package com.example.project_final.util;

import android.widget.TextView;

import androidx.annotation.DrawableRes;

import com.example.project_final.R;
import com.example.project_final.Ui;
import com.example.project_final.model.RiceField;
import com.example.project_final.model.Scan;

/** Chuyển mã trạng thái của backend thành nhãn, màu, biểu tượng dùng thống nhất trên mọi màn hình. */
public final class Look {
    private Look() {}

    /** Nhãn/màu cho mức rủi ro của một lượt quét. */
    public static void scanPill(TextView tv, Scan s) {
        if (s.needsRetake()) Ui.pill(tv, "Cần chụp lại", R.drawable.ic_photo_camera, R.color.amber, R.drawable.bg_pill_amber);
        else if (s.isPending()) Ui.pill(tv, "Chờ phân tích", R.drawable.ic_schedule, R.color.gray, R.drawable.bg_pill_gray);
        else riskPill(tv, s.riskLevel, false);
    }

    public static void riskPill(TextView tv, String risk, boolean withIcon) {
        if ("HIGH".equals(risk)) Ui.pill(tv, "Có nguy cơ", withIcon ? R.drawable.ic_warning : 0, R.color.red, R.drawable.bg_pill_red);
        else if ("WATCH".equals(risk)) Ui.pill(tv, "Cần theo dõi", withIcon ? R.drawable.ic_warning : 0, R.color.orange, R.drawable.bg_pill_orange);
        else if ("NORMAL".equals(risk)) Ui.pill(tv, "Bình thường", withIcon ? R.drawable.ic_eco : 0, R.color.green_ok, R.drawable.bg_pill_green);
        else Ui.pill(tv, "Chưa rõ", 0, R.color.gray, R.drawable.bg_pill_gray);
    }

    public static String riskLabel(String risk) {
        if ("HIGH".equals(risk)) return "Có nguy cơ";
        if ("WATCH".equals(risk)) return "Cần theo dõi";
        if ("NORMAL".equals(risk)) return "Bình thường";
        return "Chưa rõ";
    }

    /** Trạng thái ruộng: HEALTHY | WATCH | ALERT | UNKNOWN. */
    public static void fieldPill(TextView tv, RiceField f) {
        String st = f.status == null ? "UNKNOWN" : f.status;
        switch (st) {
            case "HEALTHY":
                Ui.pill(tv, "Khỏe mạnh", R.drawable.ic_eco, R.color.green_ok, R.drawable.bg_pill_green);
                break;
            case "WATCH":
                Ui.pill(tv, "Cần theo dõi", R.drawable.ic_warning, R.color.orange, R.drawable.bg_pill_orange);
                break;
            case "ALERT":
                Ui.pill(tv, "Có nguy cơ", R.drawable.ic_warning, R.color.red, R.drawable.bg_pill_red);
                break;
            default:
                Ui.pill(tv, "Chưa quét", R.drawable.ic_schedule, R.color.gray, R.drawable.bg_pill_gray);
        }
    }

    /** Mức nguy cơ khu vực: LOW | MEDIUM | HIGH | VERY_HIGH. */
    public static String areaLabel(String level) {
        if ("VERY_HIGH".equals(level)) return "Rất cao";
        if ("HIGH".equals(level)) return "Cao";
        if ("MEDIUM".equals(level)) return "Trung bình";
        return "Thấp";
    }

    public static int areaColor(String level) {
        if ("VERY_HIGH".equals(level)) return R.color.map_very_high;
        if ("HIGH".equals(level)) return R.color.map_high;
        if ("MEDIUM".equals(level)) return R.color.map_mid;
        return R.color.map_low;
    }

    @DrawableRes
    public static int areaPillBg(String level) {
        if ("VERY_HIGH".equals(level)) return R.drawable.bg_pill_red;
        if ("HIGH".equals(level)) return R.drawable.bg_pill_orange;
        if ("MEDIUM".equals(level)) return R.drawable.bg_pill_amber;
        return R.drawable.bg_pill_green;
    }

    public static int areaTextColor(String level) {
        if ("VERY_HIGH".equals(level)) return R.color.red;
        if ("HIGH".equals(level)) return R.color.orange;
        if ("MEDIUM".equals(level)) return R.color.amber;
        return R.color.green_ok;
    }

    /** Biểu tượng thời tiết theo trường icon của backend: SUN | NIGHT | PARTLY | CLOUD | RAIN | STORM. */
    @DrawableRes
    public static int weatherIcon(String icon) {
        if (icon == null) return R.drawable.ic_w_cloud;
        switch (icon) {
            case "SUN":
                return R.drawable.ic_w_sun;
            case "NIGHT":
                return R.drawable.ic_w_night;
            case "PARTLY":
                return R.drawable.ic_w_partly;
            case "RAIN":
            case "STORM":
                return R.drawable.ic_w_rain;
            default:
                return R.drawable.ic_w_cloud;
        }
    }

    /** Đoán biểu tượng từ nhãn thời tiết đã lưu cùng lượt quét (chỉ có chữ, không có mã icon). */
    @DrawableRes
    public static int weatherIconFromLabel(String label) {
        String l = label == null ? "" : label.toLowerCase();
        if (l.contains("mưa") || l.contains("dông")) return R.drawable.ic_w_rain;
        if (l.contains("ít mây")) return R.drawable.ic_w_partly;
        if (l.contains("mây") || l.contains("sương")) return R.drawable.ic_w_cloud;
        if (l.contains("quang") || l.contains("nắng")) return R.drawable.ic_w_sun;
        return R.drawable.ic_w_cloud;
    }

    public static String part(String part) {
        if ("LEAF".equals(part)) return "Trên lá";
        if ("STEM".equals(part)) return "Thân, bẹ lá";
        if ("GRAIN".equals(part)) return "Hạt, bông";
        return "";
    }

    public static String cause(String cause) {
        if ("FUNGUS".equals(cause)) return "Nấm";
        if ("BACTERIA".equals(cause)) return "Vi khuẩn";
        if ("VIRUS".equals(cause)) return "Virus";
        return "";
    }

    public static String role(String role) {
        if ("EXPERT".equals(role)) return "Chuyên gia";
        if ("FARMER".equals(role)) return "Nông dân";
        return "Người dùng";
    }

    @DrawableRes
    public static int recommendationIcon(String icon) {
        if (icon == null) return R.drawable.ic_lightbulb;
        switch (icon) {
            case "SHIELD":
                return R.drawable.ic_shield_check;
            case "LEAF":
                return R.drawable.ic_eco;
            case "WATER":
                return R.drawable.ic_water_drop;
            case "BUG":
                return R.drawable.ic_bug;
            case "CALENDAR":
                return R.drawable.ic_calendar;
            default:
                return R.drawable.ic_lightbulb;
        }
    }

    /** Nhật ký canh tác: biểu tượng, nền tròn và màu theo loại hoạt động. */
    @DrawableRes
    public static int diaryIcon(String type) {
        if (type == null) return R.drawable.ic_eco;
        switch (type) {
            case "FERTILIZE":
                return R.drawable.ic_inventory;
            case "SPRAY":
                return R.drawable.ic_spray;
            case "WATER":
                return R.drawable.ic_water_drop;
            case "WEED":
                return R.drawable.ic_sprout;
            case "HARVEST":
                return R.drawable.ic_star;
            case "SCAN":
                return R.drawable.ic_crop_free;
            default:
                return R.drawable.ic_eco;
        }
    }

    @DrawableRes
    public static int diaryBg(String type) {
        if (type == null) return R.drawable.bg_circle_tint;
        switch (type) {
            case "FERTILIZE":
                return R.drawable.bg_circle_orange;
            case "SPRAY":
                return R.drawable.bg_circle_purple;
            case "WATER":
                return R.drawable.bg_circle_blue;
            case "HARVEST":
                return R.drawable.bg_circle_amber;
            default:
                return R.drawable.bg_circle_tint;
        }
    }

    public static int diaryTint(String type) {
        if (type == null) return R.color.primary;
        switch (type) {
            case "FERTILIZE":
                return R.color.orange;
            case "SPRAY":
                return R.color.purple;
            case "WATER":
                return R.color.blue;
            case "HARVEST":
                return R.color.amber;
            default:
                return R.color.primary;
        }
    }

    /** Lời chào theo giờ trong ngày. */
    public static String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h < 11) return "Chào buổi sáng";
        if (h < 14) return "Chào buổi trưa";
        if (h < 18) return "Chào buổi chiều";
        return "Chào buổi tối";
    }

    public static boolean isNight() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 18 || h < 5;
    }
}
