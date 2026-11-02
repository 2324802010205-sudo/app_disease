package com.riceguard.api.dataset;

import com.google.cloud.firestore.FieldValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thay đổi bộ đếm thống kê trong meta/dataset. Gom các phép cộng/trừ rồi chuyển thành FieldValue.increment,
 * nhờ đó trang quản trị chỉ tốn 1 lượt đọc thay vì đọc toàn bộ ảnh.
 */
final class DatasetCounters {
    private final Map<String, Long> deltas = new LinkedHashMap<>();

    void add(long by, String... path) {
        deltas.merge(String.join("\u0000", path), by, Long::sum);
    }

    void image(String label, long sign) {
        add(sign, "total");
        add(sign, "byLabel", label);
    }

    /** status: CONFIRMED | CORRECTED | REJECTED; PENDING không được đếm. */
    void review(String label, String status, String trueLabel, long sign) {
        if (status == null || "PENDING".equals(status)) return;
        add(sign, "reviewed", label, status.toLowerCase(java.util.Locale.ROOT));
        if ("CORRECTED".equals(status) && trueLabel != null) add(sign, "confusion", label, trueLabel);
    }

    Map<String, Long> raw() {
        return deltas;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> toIncrements() {
        Map<String, Object> root = new LinkedHashMap<>();
        deltas.forEach((path, by) -> {
            if (by == 0) return;
            List<String> keys = List.of(path.split("\u0000"));
            Map<String, Object> node = root;
            for (String k : keys.subList(0, keys.size() - 1)) node = (Map<String, Object>) node.computeIfAbsent(k, x -> new LinkedHashMap<String, Object>());
            node.put(keys.get(keys.size() - 1), FieldValue.increment(by));
        });
        return root;
    }
}
