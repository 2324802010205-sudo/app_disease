package com.example.project_final.util;

import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;

import com.example.project_final.api.ApiError;

import java.util.Map;

/** Đọc và kiểm tra dữ liệu nhập, gắn lỗi theo từng ô khi backend trả về `fields`. */
public final class Forms {
    private Forms() {}

    public static String text(TextView v) {
        return v.getText() == null ? "" : v.getText().toString().trim();
    }

    /** Chuỗi rỗng thành null để không gửi lên backend. */
    public static String orNull(TextView v) {
        String s = text(v);
        return s.isEmpty() ? null : s;
    }

    public static boolean isEmail(String s) {
        return Patterns.EMAIL_ADDRESS.matcher(s).matches();
    }

    /** Gắn lỗi vào ô nhập và đưa con trỏ về ô đầu tiên có lỗi. Trả về false nếu không có gì để hiển thị. */
    public static boolean require(EditText field, String message) {
        if (!text(field).isEmpty()) return true;
        field.setError(message);
        field.requestFocus();
        return false;
    }

    /** Hiển thị lỗi theo trường (ví dụ {"email": "Email không hợp lệ"}). Trả về true nếu đã gắn ít nhất một lỗi. */
    public static boolean showFieldErrors(ApiError e, Map<String, EditText> inputs) {
        if (e.fields == null) return false;
        boolean shown = false;
        for (Map.Entry<String, String> f : e.fields.entrySet()) {
            EditText input = inputs.get(f.getKey());
            if (input == null) continue;
            input.setError(f.getValue());
            if (!shown) input.requestFocus();
            shown = true;
        }
        return shown;
    }
}
