package com.riceguard.api.risk;

import com.riceguard.api.config.AppProperties;

/** Quy đổi số ca bệnh ghi nhận trong kỳ thành mức nguy cơ của khu vực. */
public final class RiskLevels {
    private RiskLevels() {}

    public static String level(long cases, AppProperties.Risk t) {
        if (cases >= t.veryHighFrom()) return "VERY_HIGH";
        if (cases >= t.highFrom()) return "HIGH";
        if (cases >= t.mediumFrom()) return "MEDIUM";
        return "LOW";
    }

    public static int rank(String level) {
        return switch (level) {
            case "VERY_HIGH" -> 3;
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
    }

    public static String advice(String level) {
        return switch (level) {
            case "VERY_HIGH" -> "Nguy cơ rất cao. Kiểm tra đồng ruộng hằng ngày, phun phòng trừ kịp thời và báo cán bộ bảo vệ thực vật địa phương.";
            case "HIGH" -> "Nguy cơ cao. Cần tăng cường kiểm tra đồng ruộng và theo dõi sát diễn biến.";
            case "MEDIUM" -> "Nguy cơ trung bình. Thường xuyên kiểm tra đồng ruộng, phát hiện sớm triệu chứng và áp dụng biện pháp phòng trừ phù hợp.";
            default -> "Nguy cơ thấp. Tiếp tục kiểm tra ruộng định kỳ và giữ vệ sinh đồng ruộng.";
        };
    }
}
