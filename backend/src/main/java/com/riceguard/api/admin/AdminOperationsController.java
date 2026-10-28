package com.riceguard.api.admin;

import com.google.cloud.firestore.DocumentSnapshot;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Slug;
import com.riceguard.api.common.Times;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.scan.Scan;
import com.riceguard.api.security.AuthUser;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
    private final Fs fs;
    private final Refs refs;
    private final Times times;
    private final DiseaseService diseases;
    private final AdminAuditService audit;

    public AdminOperationsController(Fs fs, Refs refs, Times times, DiseaseService diseases, AdminAuditService audit) {
        this.fs = fs; this.refs = refs; this.times = times; this.diseases = diseases; this.audit = audit;
    }

    public record Recipient(String userId, String fieldId, String fieldName, String province, String district) {}

    @GetMapping("/jobs/reminders/preview")
    public Map<String, Object> reminderPreview() {
        List<Recipient> recipients = new ArrayList<>();
        for (DocumentSnapshot doc : Fs.await(fs.db().collectionGroup("fields")
                .whereEqualTo("nextCheckDate", times.today().toString()).get()).getDocuments()) {
            recipients.add(new Recipient(doc.getReference().getParent().getParent().getId(), doc.getId(),
                    doc.getString("name"), doc.getString("province"), doc.getString("district")));
        }
        return Map.of("count", recipients.size(), "recipients", recipients);
    }

    @GetMapping("/operations")
    public Map<String, Object> operations() {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (DocumentSnapshot doc : Fs.await(fs.db().collection("adminJobLogs")
                .orderBy("createdAt", com.google.cloud.firestore.Query.Direction.DESCENDING).limit(30).get()).getDocuments()) {
            Map<String, Object> row = new HashMap<>(doc.getData() == null ? Map.of() : doc.getData());
            row.put("id", doc.getId()); logs.add(row);
        }
        return Map.of("backend", "ONLINE", "firestore", "ONLINE", "jobs", logs,
                "diseases", fs.count(refs.diseases()), "riskAreas", fs.count(refs.areaStats()), "reports", fs.count(refs.reports()));
    }

    @PostMapping("/jobs/disease-sync")
    public Map<String, Object> syncDiseases(@AuthenticationPrincipal AuthUser admin) throws IOException {
        diseases.seed();
        long count = fs.count(refs.diseases());
        log("DISEASE_SYNC", count);
        audit.record(admin.uid(), "DISEASE_SYNC", "JOB", "disease-sync", "count=" + count);
        return Map.of("count", count);
    }

    @PostMapping("/jobs/risk-refresh")
    @CacheEvict(value = "risk", allEntries = true)
    public Map<String, Object> refreshRisk(@AuthenticationPrincipal AuthUser admin) {
        Map<String, Map<String, Object>> aggregate = new HashMap<>();
        for (Scan scan : fs.query(fs.db().collectionGroup("scans"), Scan.class)) {
            if (!scan.countedInArea() || scan.diseaseCode() == null || scan.province() == null || scan.district() == null) continue;
            String date = times.dateOf(scan.createdAt()).toString();
            String id = Slug.of(scan.province()) + "__" + Slug.of(scan.district()) + "__" + date.replace("-", "");
            Map<String, Object> data = aggregate.computeIfAbsent(id, key -> {
                Map<String, Object> row = new HashMap<>();
                row.put("province", scan.province()); row.put("district", scan.district()); row.put("date", date);
                row.put("total", 0L); row.put("byDisease", new HashMap<String, Long>());
                row.put("byStage", new HashMap<String, Long>()); row.put("lastAt", 0L);
                return row;
            });
            data.put("total", (long) data.get("total") + 1);
            data.put("lastAt", Math.max((long) data.get("lastAt"), scan.createdAt()));
            @SuppressWarnings("unchecked") Map<String, Long> byDisease = (Map<String, Long>) data.get("byDisease");
            byDisease.merge(scan.diseaseCode(), 1L, Long::sum);
            if (scan.stage() != null && !scan.stage().isBlank()) {
                @SuppressWarnings("unchecked") Map<String, Long> byStage = (Map<String, Long>) data.get("byStage");
                byStage.merge(scan.stage(), 1L, Long::sum);
            }
        }
        for (DocumentSnapshot old : Fs.await(refs.areaStats().get()).getDocuments()) {
            if (!aggregate.containsKey(old.getId())) Fs.await(old.getReference().delete());
        }
        aggregate.forEach((id, data) -> Fs.await(refs.areaStats().document(id).set(data)));
        log("RISK_REFRESH", aggregate.size());
        audit.record(admin.uid(), "RISK_REFRESH", "JOB", "risk-refresh", "count=" + aggregate.size());
        return Map.of("count", aggregate.size());
    }

    public void log(String kind, long count) {
        Fs.await(fs.db().collection("adminJobLogs").document().set(Map.of("kind", kind, "count", count, "createdAt", times.now())));
    }
}
