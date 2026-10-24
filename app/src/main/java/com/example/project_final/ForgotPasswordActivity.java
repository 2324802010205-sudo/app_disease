package com.example.project_final;

import android.widget.EditText;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.util.Forms;

import java.util.Map;

public class ForgotPasswordActivity extends BaseActivity {
    private EditText email;

    @Override
    protected int layout() {
        return R.layout.activity_forgot;
    }

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    @Override
    protected void onReady() {
        email = findViewById(R.id.input_email);
        done(R.id.btn_back_login);
        findViewById(R.id.btn_send).setOnClickListener(v -> submit());
    }

    private void submit() {
        if (!Forms.require(email, "Nhập email để nhận hướng dẫn")) return;
        String mail = Forms.text(email);
        if (!Forms.isEmail(mail)) {
            email.setError("Email không hợp lệ");
            return;
        }
        busy(true);
        call(api().forgotPassword(new Requests.Forgot(mail)), new ApiCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> body) {
                busy(false);
                toast(body != null && body.get("message") != null ? body.get("message") : "Đã gửi hướng dẫn tới " + mail);
                finish();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (!Forms.showFieldErrors(e, Map.of("email", email))) showError(e);
            }
        });
    }
}
