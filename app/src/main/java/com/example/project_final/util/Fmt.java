package com.example.project_final.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Định dạng ngày giờ, diện tích, nhiệt độ theo thói quen người Việt. */
public final class Fmt {
    private static final Locale VI = new Locale("vi", "VN");

    private Fmt() {}

    /** "12 thg 5, 2025   09:24" */
    public static String dateTime(long millis) {
        Date d = new Date(millis);
        return new SimpleDateFormat("d 'thg' M, yyyy   HH:mm", VI).format(d);
    }

    /** "12 thg 5, 2025" */
    public static String dateLong(long millis) {
        return new SimpleDateFormat("d 'thg' M, yyyy", VI).format(new Date(millis));
    }

    /** "dd/MM/yyyy" từ chuỗi yyyy-MM-dd; chuỗi rỗng trả về "". */
    public static String isoDate(String iso) {
        if (iso == null || iso.length() < 10) return "";
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    /** yyyy-MM-dd từ năm, tháng (0-11), ngày. */
    public static String toIso(int year, int month0, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month0 + 1, day);
    }

    public static String todayIso() {
        Calendar c = Calendar.getInstance();
        return toIso(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    /** "Vừa xong", "5 phút trước", "2 giờ trước", "Hôm qua", "3 ngày trước", sau đó là ngày đầy đủ. */
    public static String relative(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < 0) diff = 0;
        long min = diff / 60_000;
        if (min < 1) return "Vừa xong";
        if (min < 60) return min + " phút trước";
        long hours = min / 60;
        if (hours < 24) return hours + " giờ trước";
        long days = hours / 24;
        if (days == 1) return "Hôm qua";
        if (days < 7) return days + " ngày trước";
        return dateLong(millis);
    }

    /** "Cập nhật 2 ngày trước"; chưa có dữ liệu thì "Chưa quét". */
    public static String updated(Long millis) {
        return millis == null || millis == 0 ? "Chưa có lượt quét" : "Quét " + relative(millis).toLowerCase(VI);
    }

    /** 0.8 -> "0,8 ha"; 2 -> "2 ha". */
    public static String area(double ha) {
        String s = ha == Math.rint(ha) ? String.valueOf((long) ha) : String.format(VI, "%.2f", ha).replaceAll("0+$", "").replaceAll("[,]$", "");
        return s + " ha";
    }

    /** Số diện tích không kèm đơn vị: 2.8 -> "2,8". */
    public static String areaNumber(double ha) {
        return area(ha).replace(" ha", "");
    }

    public static String temp(double c) {
        return Math.round(c) + "°";
    }

    public static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    /** Hôm nay / ngày mai / số ngày còn lại tới ngày kiểm tra: "26/04/2025 (2 ngày nữa)". */
    public static String nextCheck(String iso, Integer daysUntil) {
        if (iso == null || iso.isEmpty()) return "Chưa đặt lịch";
        String d = isoDate(iso);
        if (daysUntil == null) return d;
        if (daysUntil < 0) return d + " (quá hạn " + (-daysUntil) + " ngày)";
        if (daysUntil == 0) return d + " (hôm nay)";
        return d + " (" + daysUntil + " ngày nữa)";
    }

    /** "08:00" -> giữ nguyên; "2025-05-12" -> "T2" (thứ trong tuần). */
    public static String weekday(String iso, boolean first) {
        if (first) return "Hôm nay";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            int w = c.get(Calendar.DAY_OF_WEEK);
            return w == Calendar.SUNDAY ? "CN" : "T" + w;
        } catch (java.text.ParseException e) {
            return iso;
        }
    }
}
