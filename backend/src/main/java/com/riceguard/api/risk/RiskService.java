package com.riceguard.api.risk;

import com.google.cloud.firestore.DocumentSnapshot;
import com.riceguard.api.common.Slug;
import com.riceguard.api.common.Times;
import com.riceguard.api.config.AppProperties;
import com.riceguard.api.disease.Disease;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.weather.Provinces;
import com.riceguard.api.weather.WeatherDto;
import com.riceguard.api.weather.WeatherService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RiskService {
    public record DiseaseCount(String code, String name, long cases) {}

    public record AreaRisk(String province, String district, long cases, String level, Long lastAt,
                           List<DiseaseCount> diseases, String dominantStage) {}

    public record AreaInfo(String province, String district, String fromDate, String toDate, long cases, String level,
                           Long lastAt, List<DiseaseCount> diseases, String dominantStage, String advice,
                           WeatherDto.Current weather) {}

    private final Fs fs;
    private final Refs refs;
    private final Times times;
    private final AppProperties props;
    private final DiseaseService diseases;
    private final WeatherService weather;

    public RiskService(Fs fs, Refs refs, Times times, AppProperties props, DiseaseService diseases, WeatherService weather) {
        this.fs = fs;
        this.refs = refs;
        this.times = times;
        this.props = props;
        this.diseases = diseases;
        this.weather = weather;
    }

    /** sort: risk (mặc định) | count. disease: mã bệnh để lọc (null = mọi bệnh). */
    @Cacheable(value = "risk", key = "{#province, #disease, #days, #sort}")
    public List<AreaRisk> areas(String province, String disease, int days, String sort) {
        Map<String, Acc> acc = aggregate(days);
        List<AreaRisk> out = new ArrayList<>();
        for (Acc a : acc.values()) {
            if (province != null && !province.isBlank() && !Slug.of(province).equals(Slug.of(a.province))) continue;
            long cases = disease == null || disease.isBlank() ? a.total : a.byDisease.getOrDefault(disease, 0L);
            if (cases == 0) continue;
            out.add(new AreaRisk(a.province, a.district, cases, RiskLevels.level(cases, props.risk()), a.lastAt, top(a), dominantStage(a)));
        }
        Comparator<AreaRisk> byCases = Comparator.comparingLong(AreaRisk::cases).reversed();
        out.sort("count".equals(sort) ? byCases
                : Comparator.<AreaRisk>comparingInt(r -> -RiskLevels.rank(r.level())).thenComparing(byCases));
        return out;
    }

    @Cacheable(value = "risk", key = "{'info', #province, #district, #days}")
    public AreaInfo info(String province, String district, int days) {
        Acc a = aggregate(days).get(Slug.of(province) + "|" + Slug.of(district));
        long cases = a == null ? 0 : a.total;
        String level = RiskLevels.level(cases, props.risk());
        WeatherDto.Current w = null;
        try {
            var p = Provinces.find(province);
            if (p.isPresent()) w = weather.forecast(p.get().lat(), p.get().lng(), province).current();
        } catch (RuntimeException ignored) {
            // thời tiết chỉ là thông tin kèm theo
        }
        var today = times.today();
        return new AreaInfo(province, district, today.minusDays(days - 1L).toString(), today.toString(), cases, level,
                a == null ? null : a.lastAt, a == null ? List.of() : top(a), a == null ? null : dominantStage(a),
                RiskLevels.advice(level), w);
    }

    private Map<String, Acc> aggregate(int days) {
        String since = times.today().minusDays(days - 1L).toString();
        Map<String, Acc> out = new HashMap<>();
        for (DocumentSnapshot d : Fs.await(refs.areaStats().whereGreaterThanOrEqualTo("date", since).get()).getDocuments()) {
            String province = d.getString("province");
            String district = d.getString("district");
            if (province == null || district == null) continue;
            Acc a = out.computeIfAbsent(Slug.of(province) + "|" + Slug.of(district), k -> new Acc(province, district));
            Long total = d.getLong("total");
            a.total += total == null ? 0 : Math.max(0, total);
            Long last = d.getLong("lastAt");
            if (last != null && (a.lastAt == null || last > a.lastAt)) a.lastAt = last;
            addAll(a.byDisease, d.get("byDisease"));
            addAll(a.byStage, d.get("byStage"));
        }
        return out;
    }

    private static void addAll(Map<String, Long> target, Object raw) {
        if (raw instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (v instanceof Number n) target.merge(String.valueOf(k), Math.max(0, n.longValue()), Long::sum);
            });
        }
    }

    private List<DiseaseCount> top(Acc a) {
        return a.byDisease.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                .map(e -> {
                    Disease d = diseases.find(e.getKey());
                    return new DiseaseCount(e.getKey(), d == null ? e.getKey() : d.name(), e.getValue());
                }).toList();
    }

    private static String dominantStage(Acc a) {
        return a.byStage.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static final class Acc {
        final String province;
        final String district;
        long total;
        Long lastAt;
        final Map<String, Long> byDisease = new HashMap<>();
        final Map<String, Long> byStage = new HashMap<>();

        Acc(String province, String district) {
            this.province = province;
            this.district = district;
        }
    }
}
