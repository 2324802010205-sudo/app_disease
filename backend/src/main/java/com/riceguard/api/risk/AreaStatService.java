package com.riceguard.api.risk;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.SetOptions;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Slug;
import com.riceguard.api.common.Times;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Bộ đếm ẩn danh theo (tỉnh, huyện, ngày): mỗi lượt quét phát hiện bệnh cộng 1.
 * Bản đồ nguy cơ chỉ đọc các bộ đếm này nên rẻ và không lộ dữ liệu cá nhân.
 */
@Service
public class AreaStatService {
    private final Fs fs;
    private final Refs refs;
    private final Times times;

    public AreaStatService(Fs fs, Refs refs, Times times) {
        this.fs = fs;
        this.refs = refs;
        this.times = times;
    }

    @CacheEvict(value = "risk", allEntries = true)
    public void record(String province, String district, String diseaseCode, String stage, long at, int delta) {
        if (province == null || province.isBlank() || district == null || district.isBlank() || diseaseCode == null) return;
        String date = times.dateOf(at).toString();
        String id = Slug.of(province) + "__" + Slug.of(district) + "__" + date.replace("-", "");
        Map<String, Object> data = new HashMap<>();
        data.put("province", province.trim());
        data.put("district", district.trim());
        data.put("date", date);
        data.put("total", FieldValue.increment(delta));
        data.put("byDisease", Map.of(key(diseaseCode), FieldValue.increment(delta)));
        if (stage != null && !stage.isBlank()) data.put("byStage", Map.of(key(stage), FieldValue.increment(delta)));
        if (delta > 0) data.put("lastAt", at);
        Fs.await(refs.areaStats().document(id).set(data, SetOptions.merge()));
    }

    private static String key(String s) {
        return s.trim().replace('.', ' ').replace('/', ' ');
    }
}
