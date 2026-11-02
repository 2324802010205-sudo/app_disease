package com.riceguard.api.community;

import com.google.cloud.firestore.WriteBatch;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu cộng đồng mẫu để xem giao diện bài viết/bình luận. Chỉ chạy khi SEED_DEMO_COMMUNITY=true và chỉ tạo một lần
 * (bỏ qua nếu bài mẫu đã có). Người dùng mẫu chỉ có hồ sơ trên Firestore, không có tài khoản đăng nhập.
 */
@Component
@ConditionalOnProperty(name = "riceguard.seed-demo-community", havingValue = "true")
public class DemoCommunitySeed implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoCommunitySeed.class);
    private static final long H = 3_600_000L;

    private final Fs fs;
    private final Refs refs;
    private final Times times;
    private final UserService users;

    public DemoCommunitySeed(Fs fs, Refs refs, Times times, UserService users) {
        this.fs = fs;
        this.refs = refs;
        this.times = times;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (fs.exists(refs.posts().document("demo-post-1"))) {
            log.info("Dữ liệu cộng đồng mẫu đã có, bỏ qua");
            return;
        }
        users.create("demo-expert", "khoa.bvtv@demo.riceguard.local", "Kỹ sư Trần Minh Khoa", null, "An Giang", "EXPERT");
        users.setVerifiedExpert("demo-expert", true, "seed");
        users.create("demo-hai", "hai.nongdan@demo.riceguard.local", "Nguyễn Văn Hải", null, "Đồng Tháp", "FARMER");
        users.create("demo-lan", "lan.lua@demo.riceguard.local", "Lê Thị Lan", null, "Cần Thơ", "FARMER");
        users.create("demo-tam", "tam.ruong@demo.riceguard.local", "Phạm Quốc Tâm", null, "Bình Dương", "FARMER");
        users.create("demo-nga", "nga.hatgiong@demo.riceguard.local", "Võ Thị Ngọc Nga", null, "Tiền Giang", "FARMER");

        long now = times.now();
        List<Object[]> posts = new ArrayList<>();
        posts.add(new Object[]{"demo-post-1", "demo-hai", "Đồng Tháp", "PEST",
                "Lá lúa có vết hình thoi xám trắng, có phải đạo ôn không?",
                "Ruộng nhà tôi sạ được 28 ngày, mấy hôm nay trời âm u có sương. Sáng nay ra thăm đồng thấy nhiều lá có vết hình thoi, "
                        + "giữa xám trắng, viền nâu đậm, vài chỗ liên kết thành mảng làm lá khô. Ruộng bón đạm hơi nhiều lần trước. "
                        + "Mọi người cho hỏi có phải đạo ôn lá không và nên xử lý sao cho kịp?",
                18L, 9, now - 20 * H});
        posts.add(new Object[]{"demo-post-2", "demo-lan", "Cần Thơ", "EXPERIENCE",
                "Bón thêm kali giảm đốm nâu vụ Hè Thu, chia sẻ kinh nghiệm",
                "Vụ trước ruộng tôi bị đốm nâu khá nặng, lá nhiều đốm tròn nâu, hạt lép nhiều. Vụ này tôi giảm đạm, chia nhỏ lần bón "
                        + "và bón thêm kali lúc đẻ nhánh với làm đòng, giữ nước đều, kết quả lá sạch hơn hẳn. "
                        + "Ai cũng gặp đốm nâu thì thử cân đối phân xem sao.",
                11L, 4, now - 3 * 24 * H});
        posts.add(new Object[]{"demo-post-3", "demo-tam", "Bình Dương", "WEATHER",
                "Dự báo mưa bão cuối tuần, cần chú ý bệnh bạc lá",
                "Dự báo cuối tuần có mưa to kèm gió mạnh. Sau mưa bão lá lúa dễ bị xây xát, vi khuẩn bạc lá xâm nhập rất nhanh. "
                        + "Bà con nhớ thoát nước ruộng, tạm ngưng bón đạm và kiểm tra ruộng sau mưa 3–5 ngày nhé.",
                7L, 3, now - 6 * H});

        WriteBatch b = fs.db().batch();
        for (Object[] p : posts) {
            Post post = new Post((String) p[0], (String) p[1], (String) p[2], (String) p[3], (String) p[4], (String) p[5],
                    List.of(), (Long) p[6], ((Integer) p[7]).longValue(), (Long) p[8], (Long) p[8], false);
            b.set(refs.posts().document(post.id()), fs.toMap(post));
        }
        // Bài 1: cây bình luận 2 cấp, có chuyên gia và tác giả trả lời.
        c(b, "demo-post-1", "c1", null, "demo-expert", "Theo mô tả (vết thoi, tâm xám trắng, viền nâu) thì rất có thể là đạo ôn lá. "
                + "Anh nên phun thuốc đặc trị đạo ôn có hoạt chất Tricyclazole hoặc Isoprothiolane ngay, phun ướt đều tán lá và lặp lại sau 5–7 ngày nếu trời còn ẩm. "
                + "Nhớ ngưng bón đạm trong lúc có bệnh.", 12, now - 19 * H);
        c(b, "demo-post-1", "c1r1", "c1", "demo-hai", "Cảm ơn kỹ sư. Tôi sẽ phun chiều nay. Nếu 2 ngày tới vẫn mưa phùn thì có cần phun lại sớm hơn không ạ?", 1, now - 18 * H);
        c(b, "demo-post-1", "c1r2", "c1", "demo-expert", "Nếu còn mưa phùn, ẩm cao thì anh phun lặp lại sau 5 ngày. Phun khi trời ráo, tránh lúc có sương đọng trên lá.", 4, now - 17 * H);
        c(b, "demo-post-1", "c2", null, "demo-lan", "Ruộng nhà tôi vụ trước cũng y như vậy. Sau khi giảm đạm và phun thuốc kịp thì bệnh dừng. Quan trọng là phát hiện sớm, đừng để lan thành mảng.", 5, now - 16 * H);
        c(b, "demo-post-1", "c2r1", "c2", "demo-nga", "Đúng rồi chị, tôi còn giữ nước 3–5 cm cho ruộng đỡ stress nữa.", 2, now - 15 * H);
        c(b, "demo-post-1", "c3", null, "demo-tam", "Anh thử chụp lá bệnh bằng ứng dụng RiceGuard AI xem sao, tôi quét thì ra \"Đạo ôn lá\" độ tin cậy cao, khớp với mô tả của anh.", 6, now - 12 * H);
        c(b, "demo-post-1", "c4", null, "demo-nga", "Mình hỏi thêm: giống lúa này là giống gì ạ? Có giống chống chịu đạo ôn tốt hơn thì vụ sau đổi thử.", 1, now - 8 * H);
        c(b, "demo-post-1", "c4r1", "c4", "demo-hai", "Giống OM 5451 chị ơi, vụ sau tôi sẽ thử giống khác kháng đạo ôn hơn.", 0, now - 7 * H);
        c(b, "demo-post-1", "c5", null, "demo-lan", "Có tin vui thì anh cập nhật lại nhé, để bà con khác tham khảo.", 3, now - 2 * H);
        // Bài 2
        c(b, "demo-post-2", "c1", null, "demo-expert", "Chia sẻ rất hay. Đốm nâu hay gặp ở ruộng thiếu kali, thiếu silic hoặc bị khô nước. Cân đối N-P-K và giữ nước ổn định là hướng đúng.", 8, now - 2 * 24 * H);
        c(b, "demo-post-2", "c2", null, "demo-hai", "Chị bón kali bao nhiêu lần, mỗi lần khoảng bao nhiêu vậy chị?", 2, now - 2 * 24 * H + 3 * H);
        c(b, "demo-post-2", "c2r1", "c2", "demo-lan", "Tôi chia 2 lần: đẻ nhánh và làm đòng, mỗi lần theo khuyến cáo trên bao bì cho diện tích ruộng, đất ruộng mỗi nơi mỗi khác nên anh hỏi thêm cán bộ khuyến nông nhé.", 3, now - 2 * 24 * H + 5 * H);
        c(b, "demo-post-2", "c3", null, "demo-tam", "Cảm ơn chị, vụ này tôi cũng sẽ thử.", 0, now - 24 * H);
        // Bài 3
        c(b, "demo-post-3", "c1", null, "demo-expert", "Nhắc rất đúng lúc. Sau mưa bão bà con kiểm tra ruộng trong 3–5 ngày, thấy vết úng nước ở mép lá, dịch vàng đục thì phun thuốc trừ vi khuẩn sớm, ngưng bón đạm.", 5, now - 5 * H);
        c(b, "demo-post-3", "c2", null, "demo-nga", "Nhà tôi đã tháo bớt nước rồi. Cảm ơn bạn đã nhắc.", 1, now - 4 * H);
        c(b, "demo-post-3", "c3", null, "demo-hai", "Gió lớn lúa đổ ngã thì làm sao hả kỹ sư?", 0, now - 2 * H);
        try {
            Fs.await(b.commit());
        } catch (RuntimeException e) {
            log.warn("Không ghi được dữ liệu cộng đồng mẫu: {}", e.getMessage());
            return;
        }
        log.info("Đã tạo dữ liệu cộng đồng mẫu: 3 bài viết, 16 bình luận");
    }

    private void c(WriteBatch b, String postId, String id, String parentId, String authorId, String text, long likes, long at) {
        Comment c = new Comment(id, postId, parentId, authorId, text, likes, List.of(), at);
        b.set(refs.comments(postId).document(id), fs.toMap(c));
    }
}
