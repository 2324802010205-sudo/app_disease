package com.riceguard.api.diagnosis;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.UrlImageSource;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.riceguard.api.config.AppProperties;
import com.riceguard.api.disease.Disease;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** Chẩn đoán bệnh lúa từ ảnh bằng Claude (vision + structured output). Cần biến môi trường ANTHROPIC_API_KEY. */
@Component
@ConditionalOnProperty(name = "riceguard.diagnosis.provider", havingValue = "claude")
public class ClaudeDiagnosisEngine implements DiagnosisEngine {

    /** Cấu trúc JSON ràng buộc đầu ra của Claude. */
    public record Output(
            @JsonPropertyDescription("true nếu ảnh chụp rõ lá, thân hoặc bông lúa; false nếu không phải cây lúa hoặc ảnh quá mờ/tối để đánh giá")
            boolean riceLeaf,
            @JsonPropertyDescription("Mã bệnh đúng như trong danh sách; 'healthy' nếu cây khỏe; 'unknown' nếu không thuộc danh sách hoặc không đủ chắc chắn")
            String diseaseCode,
            @JsonPropertyDescription("Độ chắc chắn thật sự từ 0 đến 1")
            double confidence,
            @JsonPropertyDescription("Mức độ nặng: NONE, LOW, MEDIUM hoặc HIGH")
            String severity,
            @JsonPropertyDescription("2-4 dấu hiệu nhìn thấy trong ảnh, tiếng Việt, mỗi ý một câu ngắn")
            List<String> symptoms,
            @JsonPropertyDescription("Nhận định tổng quan 1-2 câu tiếng Việt")
            String summary,
            @JsonPropertyDescription("Đúng 3 khuyến nghị xử lý phù hợp bệnh và giai đoạn lúa")
            List<Reco> recommendations,
            @JsonPropertyDescription("Nếu ảnh chưa đủ rõ: hướng dẫn chụp lại bằng tiếng Việt; nếu không cần thì để chuỗi rỗng")
            String retakeAdvice) {}

    public record Reco(
            @JsonPropertyDescription("Tiêu đề ngắn dưới 10 từ") String title,
            @JsonPropertyDescription("Mô tả 1 câu, ưu tiên nêu hoạt chất thay vì tên thương mại") String description,
            @JsonPropertyDescription("Một trong: SHIELD, LEAF, WATER, BUG, CALENDAR") String icon) {}

    private static final String SYSTEM = """
            Bạn là chuyên gia bảo vệ thực vật cho cây lúa tại Việt Nam, hỗ trợ ứng dụng RiceGuard AI chẩn đoán bệnh qua ảnh.
            Nguyên tắc:
            - Chỉ dựa vào những gì thấy rõ trong ảnh; không suy diễn bệnh khi dấu hiệu không rõ.
            - diseaseCode phải là một mã trong danh sách được cung cấp, hoặc 'healthy', hoặc 'unknown'.
            - confidence phải phản ánh đúng độ chắc chắn; ảnh mờ, thiếu sáng, cận cảnh quá mức thì giảm confidence.
            - Nếu ảnh không phải cây lúa thì riceLeaf=false.
            - Khuyến nghị phải an toàn: nêu hoạt chất, nhắc đọc kỹ nhãn và hỏi cán bộ bảo vệ thực vật khi bệnh nặng; không khuyên dùng thuốc cấm.
            - Mọi văn bản trả lời bằng tiếng Việt.
            """;

    private final AnthropicClient client;
    private final AppProperties.Diagnosis cfg;

    public ClaudeDiagnosisEngine(AppProperties props) {
        this.cfg = props.diagnosis();
        this.client = AnthropicOkHttpClient.builder().fromEnv().timeout(Duration.ofSeconds(cfg.timeoutSeconds())).build();
    }

    @Override
    public String name() {
        return "claude:" + cfg.claudeModel();
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisRequest req) {
        String catalog = req.catalog().stream()
                .map(d -> "- " + d.id() + ": " + d.name() + " — " + d.description())
                .collect(Collectors.joining("\n"));
        String ctx = "Danh sách bệnh được phép chọn:\n" + catalog
                + "\n\nBối cảnh: giai đoạn lúa = " + orUnknown(req.stage())
                + "; giống = " + orUnknown(req.variety())
                + "; thời tiết = " + orUnknown(req.weatherSummary())
                + ".\nHãy chẩn đoán ảnh đính kèm.";
        try {
            StructuredMessageCreateParams<Output> params = MessageCreateParams.builder()
                    .model(cfg.claudeModel())
                    .maxTokens(4000L)
                    .system(SYSTEM)
                    .outputConfig(Output.class)
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofImage(ImageBlockParam.builder()
                                    .source(UrlImageSource.builder().url(req.imageUrl()).build()).build()),
                            ContentBlockParam.ofText(TextBlockParam.builder().text(ctx).build())))
                    .build();
            StructuredMessage<Output> res = client.messages().create(params);
            if (res.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                throw new DiagnosisException("Dịch vụ AI từ chối phân tích ảnh này");
            }
            Output o = res.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst()
                    .orElseThrow(() -> new DiagnosisException("AI không trả về kết quả"));
            return new DiagnosisResult(o.riceLeaf(), o.diseaseCode(), o.confidence(), o.severity(), o.symptoms(), o.summary(),
                    o.recommendations() == null ? List.of() : o.recommendations().stream()
                            .map(r -> new DiagnosisResult.Recommendation(r.title(), r.description(), r.icon())).toList(),
                    o.retakeAdvice());
        } catch (AnthropicException e) {
            throw new DiagnosisException("Lỗi gọi dịch vụ AI: " + e.getMessage(), e);
        }
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() || "null".equals(s) ? "không rõ" : s;
    }
}
