package com.riceguard.api.dataset;

import com.riceguard.api.admin.AdminAuditService;
import com.riceguard.api.admin.AdminOperationsController;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.scan.Scan;
import com.riceguard.api.scan.ScanService;
import com.riceguard.api.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Quản trị kho ảnh theo nhãn AI: xem theo thư mục, duyệt đúng/sai, chẩn đoán lại. */
@RestController
@RequestMapping("/api/v1/admin/dataset")
public class AdminDatasetController {
    public record ReviewRequest(String action, String trueLabel, String note) {}

    private final DatasetService dataset;
    private final ScanService scans;
    private final AdminAuditService audit;
    private final AdminOperationsController operations;

    public AdminDatasetController(DatasetService dataset, ScanService scans, AdminAuditService audit, AdminOperationsController operations) {
        this.dataset = dataset;
        this.scans = scans;
        this.audit = audit;
        this.operations = operations;
    }

    @GetMapping("/summary")
    public DatasetService.Summary summary() {
        return dataset.summary();
    }

    @GetMapping
    public PageResult<DatasetImage> list(@RequestParam(defaultValue = "") String label,
                                         @RequestParam(defaultValue = "ALL") String review,
                                         @RequestParam(required = false) Boolean reported,
                                         @RequestParam(defaultValue = "") String confidence,
                                         @RequestParam(defaultValue = "") String q,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit) {
        return dataset.list(label, review, reported, confidence, q, cursor, limit);
    }

    @GetMapping("/{id}")
    public DatasetImage detail(@PathVariable String id) {
        return dataset.require(id);
    }

    @PutMapping("/{id}/review")
    public DatasetImage review(@AuthenticationPrincipal AuthUser admin, @PathVariable String id, @RequestBody ReviewRequest r) {
        DatasetImage d = dataset.review(admin.uid(), id, r.action(), r.trueLabel(), r.note());
        audit.record(admin.uid(), "DATASET_" + r.action(), "SCAN", id, "ai=" + d.label() + ";true=" + d.trueLabel());
        return d;
    }

    /** Chẩn đoán lại ảnh đang chờ hoặc chưa xác định, ví dụ khi dịch vụ AI vừa bật lại. */
    @PostMapping("/{id}/rediagnose")
    public DatasetImage rediagnose(@AuthenticationPrincipal AuthUser admin, @PathVariable String id) {
        DatasetImage d = dataset.require(id);
        Scan s = scans.rediagnose(d.userId(), id);
        audit.record(admin.uid(), "DATASET_REDIAGNOSE", "SCAN", id, "status=" + s.status());
        return dataset.require(id);
    }

    @PostMapping("/backfill")
    public Map<String, Integer> backfill(@AuthenticationPrincipal AuthUser admin) {
        int added = dataset.backfill();
        operations.log("DATASET_BACKFILL", added);
        audit.record(admin.uid(), "DATASET_BACKFILL", "JOB", "dataset-backfill", "count=" + added);
        return Map.of("count", added);
    }
}
