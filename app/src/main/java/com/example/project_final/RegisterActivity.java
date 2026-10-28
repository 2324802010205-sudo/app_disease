package com.example.project_final;

import android.content.Intent;
import android.widget.CheckBox;
import android.widget.EditText;
import android.view.View;
import android.widget.ImageView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.AuthResponse;
import com.example.project_final.util.Forms;

import java.util.Map;

public class RegisterActivity extends BaseActivity {
    /** Có khi người dùng đăng nhập bằng Google nhưng tài khoản chưa có: màn này hoàn tất đăng ký bằng chính tài khoản Google đó. */
    public static final String EXTRA_GOOGLE_TOKEN = "googleToken";
    public static final String EXTRA_GOOGLE_NAME = "googleName";
    public static final String EXTRA_GOOGLE_EMAIL = "googleEmail";

    private EditText name, email, password, confirm;
    private String googleToken;

    @Override
    protected int layout() {
        return R.layout.activity_register;
    }

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    @Override
    protected void onReady() {
        name = findViewById(R.id.input_name);
        email = findViewById(R.id.input_email);
        password = findViewById(R.id.input_password);
        confirm = findViewById(R.id.input_confirm);
        PasswordToggle.attach(password, (ImageView) findViewById(R.id.btn_toggle_password));
        PasswordToggle.attach(confirm, (ImageView) findViewById(R.id.btn_toggle_confirm));
        done(R.id.btn_login);
        findViewById(R.id.btn_create).setOnClickListener(v -> submit());
        findViewById(R.id.btn_google).setOnClickListener(v -> {
            if (!termsAccepted()) return;
            GoogleAuth.start(this, true);
        });
        View layoutTerms = findViewById(R.id.layout_terms);
        if (layoutTerms != null) {
            layoutTerms.setOnClickListener(v -> {
                CheckBox cb = findViewById(R.id.check_terms);
                if (cb != null) cb.setChecked(!cb.isChecked());
            });
        }
        View txtTerms = findViewById(R.id.txt_terms_of_use);
        if (txtTerms != null) {
            txtTerms.setOnClickListener(v -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.terms_of_use)
                        .setMessage(R.string.terms_content)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }

        googleToken = getIntent().getStringExtra(EXTRA_GOOGLE_TOKEN);
        if (googleToken != null) enterGoogleMode();
    }

    private boolean termsAccepted() {
        if (((CheckBox) findViewById(R.id.check_terms)).isChecked()) return true;
        toast("Hãy đồng ý với điều khoản sử dụng để tiếp tục");
        return false;
    }

    /** Hoàn tất đăng ký bằng Google: điền sẵn tên và email, không cần mật khẩu. */
    private void enterGoogleMode() {
        String gName = getIntent().getStringExtra(EXTRA_GOOGLE_NAME);
        String gEmail = getIntent().getStringExtra(EXTRA_GOOGLE_EMAIL);
        setText(R.id.screen_title, "Hoàn tất đăng ký");
        setText(R.id.screen_hint, "Tài khoản Google này chưa có trong hệ thống. Xác nhận thông tin để tạo tài khoản.");
        name.setText(gName);
        name.setEnabled(false);
        email.setText(gEmail);
        email.setEnabled(false);
        findViewById(R.id.row_password).setVisibility(View.GONE);
        findViewById(R.id.row_confirm).setVisibility(View.GONE);
        findViewById(R.id.group_google).setVisibility(View.GONE);
    }

    private void submitGoogle() {
        if (!termsAccepted()) return;
        GoogleAuth.exchange(this, googleToken, Forms.text(name), Forms.text(email), true);
    }

    private void submit() {
        if (googleToken != null) {
            submitGoogle();
            return;
        }
        String pass = password.getText().toString();
        if (!Forms.require(name, "Nhập họ và tên")) return;
        if (!Forms.require(email, "Nhập email")) return;
        if (!Forms.isEmail(Forms.text(email))) {
            email.setError("Email không hợp lệ");
            email.requestFocus();
            return;
        }
        if (pass.length() < 8) {
            password.setError("Mật khẩu cần ít nhất 8 ký tự");
            password.requestFocus();
            return;
        }
        if (!pass.equals(confirm.getText().toString())) {
            confirm.setError("Mật khẩu xác nhận chưa khớp");
            confirm.requestFocus();
            return;
        }
        if (!((CheckBox) findViewById(R.id.check_terms)).isChecked()) {
            toast("Hãy đồng ý với điều khoản sử dụng để tiếp tục");
            return;
        }
        busy(true);
        Requests.Register body = new Requests.Register(Forms.text(name), Forms.text(email), pass, null, null, "FARMER");
        call(api().register(body), new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse r) {
                busy(false);
                Session.save(r);
                Intent it = new Intent(RegisterActivity.this, HomeActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(it);
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (!Forms.showFieldErrors(e, Map.of("fullName", name, "email", email, "password", password))) showError(e);
            }
        });
    }
}
