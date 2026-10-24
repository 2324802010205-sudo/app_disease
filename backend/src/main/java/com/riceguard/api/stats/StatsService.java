package com.riceguard.api.stats;

import com.google.cloud.firestore.Query;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.community.Post;
import com.riceguard.api.diary.DiaryEntry;
import com.riceguard.api.scan.Scan;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class StatsService {
    public record Bucket(String riskLevel, long count, double percent) {}
    public record Day(String date, long count) {}
    public record TopDisease(String code, String name, long count) {}
    public record Activity(String type, String title, String description, long at, String refId) {}
    public record Stats(String range, long scans, long alerts, List<Bucket> distribution, List<Day> daily,
                        List<TopDisease> topDiseases, List<Activity> recentActivities) {}

    private final Fs fs;
    private final Refs refs;
    private final Times times;

    public StatsService(Fs fs, Refs refs, Times times) {
        this.fs = fs;
        this.refs = refs;
        this.times = times;
    }

    /** range: week (7 ngày) | month (30 ngày). */
    public Stats stats(String uid, String range) {
        int days = "month".equals(range) ? 30 : 7;
        long since = times.sinceDays(days);
        List<Scan> scans = fs.query(refs.scans(uid).whereGreaterThanOrEqualTo("createdAt", since)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(2000), Scan.class);
        List<Scan> judged = scans.stream().filter(s -> "DIAGNOSED".equals(s.status())).toList();

        long normal = judged.stream().filter(s -> "NORMAL".equals(s.riskLevel())).count();
        long high = judged.stream().filter(s -> "HIGH".equals(s.riskLevel())).count();
        long watch = judged.stream().filter(s -> "WATCH".equals(s.riskLevel())).count();
        long total = Math.max(1, judged.size());
        List<Bucket> dist = List.of(
                new Bucket("NORMAL", normal, pct(normal, total)),
                new Bucket("HIGH", high, pct(high, total)),
                new Bucket("WATCH", watch, pct(watch, total)));

        Map<LocalDate, Long> perDay = new TreeMap<>();
        LocalDate today = times.today();
        for (int i = days - 1; i >= 0; i--) perDay.put(today.minusDays(i), 0L);
        scans.forEach(s -> perDay.merge(times.dateOf(s.createdAt()), 1L, Long::sum));
        List<Day> daily = perDay.entrySet().stream().filter(e -> !e.getKey().isBefore(today.minusDays(days - 1L)))
                .map(e -> new Day(e.getKey().toString(), e.getValue())).toList();

        Map<String, Long> byDisease = judged.stream().filter(s -> s.diseaseCode() != null && !"healthy".equals(s.diseaseCode()))
                .collect(Collectors.groupingBy(Scan::diseaseCode, LinkedHashMap::new, Collectors.counting()));
        Map<String, String> names = judged.stream().filter(s -> s.diseaseCode() != null)
                .collect(Collectors.toMap(Scan::diseaseCode, s -> String.valueOf(s.diseaseName()), (a, b) -> a));
        List<TopDisease> top = byDisease.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                .map(e -> new TopDisease(e.getKey(), names.get(e.getKey()), e.getValue())).toList();

        return new Stats(days == 30 ? "month" : "week", scans.size(), high, dist, daily, top, recent(uid));
    }

    private List<Activity> recent(String uid) {
        List<Activity> out = new ArrayList<>();
        fs.query(refs.scans(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(3), Scan.class).forEach(s ->
                out.add(new Activity("SCAN", s.diseaseName() == null ? "Quét ảnh lúa" : "Quét phát hiện " + s.diseaseName(),
                        s.summary(), s.createdAt(), s.id())));
        fs.query(refs.diaries(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(3), DiaryEntry.class).forEach(d ->
                out.add(new Activity("DIARY", d.title(), d.content(), d.createdAt(), d.id())));
        fs.query(refs.posts().whereEqualTo("authorId", uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(3), Post.class).forEach(p ->
                out.add(new Activity("POST", p.title(), p.content(), p.createdAt(), p.id())));
        out.sort(Comparator.comparingLong(Activity::at).reversed());
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    private static double pct(long n, long total) {
        return Math.round(n * 1000.0 / total) / 10.0;
    }
}
