package com.riceguard.api.scan;

import com.riceguard.api.common.Cursor;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/scans")
@Validated
@Tag(name = "Quét bệnh", description = "Gửi ảnh để chẩn đoán, lịch sử quét, so sánh")
public class ScanController {
    private final ScanService service;
    private final com.riceguard.api.scan.FeedbackService feedback;

    public ScanController(ScanService service, FeedbackService feedback) {
        this.service = service;
        this.feedback = feedback;
    }

    /**
     * Quét một ảnh. Trả 201 kèm kết quả; status = DIAGNOSED | NEEDS_RETAKE | PENDING.
     * PENDING nghĩa là ảnh đã lưu nhưng AI chưa trả lời: gọi POST /scans/{id}/diagnose để thử lại.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Scan create(@AuthenticationPrincipal AuthUser u,
                       @RequestParam("image") MultipartFile image,
                       @RequestParam(required = false) String fieldId,
                       @RequestParam(required = false) @Size(max = 40) String stage,
                       @RequestParam(required = false) @Size(max = 60) String variety,
                       @RequestParam(required = false) @Size(max = 500) String note,
                       @RequestParam(required = false) @Size(max = 60) String province,
                       @RequestParam(required = false) @Size(max = 60) String district,
                       @RequestParam(required = false) Double lat,
                       @RequestParam(required = false) Double lng) {
        return service.create(u.uid(), new ScanService.ScanInput(image, fieldId, stage, variety, note, province, district, lat, lng));
    }

    /** range: 7d | 30d | all. risk: NORMAL | WATCH | HIGH. */
    @GetMapping
    public PageResult<Scan> list(@AuthenticationPrincipal AuthUser u,
                                 @RequestParam(defaultValue = "all") String range,
                                 @RequestParam(required = false) String risk,
                                 @RequestParam(required = false) String fieldId,
                                 @RequestParam(required = false) String cursor,
                                 @RequestParam(required = false) Integer limit) {
        return service.list(u.uid(), range, risk, fieldId, cursor, Cursor.limit(limit, 20, 50));
    }

    @GetMapping("/compare")
    public ScanService.Comparison compare(@AuthenticationPrincipal AuthUser u, @RequestParam String first, @RequestParam String second) {
        return service.compare(u.uid(), first, second);
    }

    @GetMapping("/{id}")
    public Scan get(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        return service.get(u.uid(), id);
    }

    @PostMapping("/{id}/diagnose")
    public Scan rediagnose(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        return service.rediagnose(u.uid(), id);
    }

    @PutMapping("/{id}/note")
    public Scan note(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @RequestBody Map<String, @Size(max = 500) String> body) {
        return service.updateNote(u.uid(), id, body.get("note"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.delete(u.uid(), id);
    }

    /** Phản hồi kết quả chưa đúng để cải thiện mô hình. */
    @PostMapping("/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> feedback(@AuthenticationPrincipal AuthUser u, @PathVariable String id,
                                        @jakarta.validation.Valid @RequestBody FeedbackService.FeedbackRequest r) {
        return Map.of("id", feedback.submit(u.uid(), service.get(u.uid(), id), r));
    }
}
