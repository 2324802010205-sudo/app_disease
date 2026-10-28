package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.AuthResponse;
import com.example.project_final.util.Forms;

import java.util.Map;

public class LoginActivity extends BaseActivity {
    public static final String EXTRA_OPEN_REGISTER = "openRegister";

    private EditText email;
    private EditText password;
    private TextView loginError;

    @Override
    protected int layout() {
        return R.layout.activity_login;
    }

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    @Override
    protected void onReady() {
        email = findViewById(R.id.input_email);
        password = findViewById(R.id.input_password);
        loginError = findViewById(R.id.login_error);
        PasswordToggle.attach(password, (ImageView) findViewById(R.id.btn_toggle_password));
        go(R.id.btn_forgot, ForgotPasswordActivity.class);
        go(R.id.btn_register, RegisterActivity.class);
        findViewById(R.id.btn_login).setOnClickListener(v -> submit());
        findViewById(R.id.btn_google).setOnClickListener(v -> GoogleAuth.start(this, false));
        findViewById(R.id.btn_guest).setOnClickListener(v -> {
            Session.enterGuest();
            Intent it = new Intent(this, HomeActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(it);
        });
        if (getIntent().getBooleanExtra(EXTRA_OPEN_REGISTER, false)) startActivity(new Intent(this, RegisterActivity.class));
    }

    private void submit() {
        loginError.setVisibility(View.GONE);
        String mail = Forms.text(email);
        String pass = password.getText().toString();
        if (!Forms.require(email, "Nhập email")) return;
        if (!Forms.isEmail(mail)) {
            email.setError("Email không hợp lệ");
            email.requestFocus();
            return;
        }
        if (pass.isEmpty()) {
            password.setError("Nhập mật khẩu");
            password.requestFocus();
            return;
        }
        busy(true);
        call(api().login(new Requests.Login(mail, pass)), new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse r) {
                busy(false);
                Session.save(r);
                Intent it = new Intent(LoginActivity.this, HomeActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(it);
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (!Forms.showFieldErrors(e, Map.of("email", email, "password", password))) {
                    loginError.setText(e.message);
                    loginError.setVisibility(View.VISIBLE);
                }
            }
        });
    }
}
