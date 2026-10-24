package com.example.project_final;

import android.content.Intent;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.AuthResponse;
import com.example.project_final.util.GoogleSignIn;

/**
 * Luồng đăng nhập/đăng ký bằng Google dùng chung cho màn đăng nhập và đăng ký.
 * Đăng nhập mà tài khoản chưa có trên hệ thống thì chuyển sang màn đăng ký (điền sẵn tên, email).
 */
final class GoogleAuth {
    private GoogleAuth() {}

    /** register=true: tạo tài khoản nếu chưa có; false: chỉ đăng nhập, chưa có thì chuyển sang đăng ký. */
    static void start(BaseActivity a, boolean register) {
        GoogleSignIn.start(a, new GoogleSignIn.Callback() {
            @Override
            public void onSuccess(String idToken, String name, String email) {
                exchange(a, idToken, name, email, register);
            }

            @Override
            public void onFailure(String message) {
                a.toast(message);
            }

            @Override
            public void onCancelled() {
                // người dùng tự đóng hộp chọn tài khoản
            }
        });
    }

    /** Đổi Google ID token lấy phiên đăng nhập từ backend. */
    static void exchange(BaseActivity a, String idToken, String name, String email, boolean register) {
        a.busy(true);
        a.call(a.api().google(new Requests.Google(idToken, register ? "REGISTER" : "LOGIN")), new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse r) {
                a.busy(false);
                Session.save(r);
                Intent it = new Intent(a, HomeActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                a.startActivity(it);
            }

            @Override
            public void onError(ApiError e) {
                a.busy(false);
                if (ApiError.GOOGLE_NOT_REGISTERED.equals(e.code)) {
                    a.toast("Tài khoản Google này chưa đăng ký. Hãy hoàn tất đăng ký");
                    a.startActivity(new Intent(a, RegisterActivity.class)
                            .putExtra(RegisterActivity.EXTRA_GOOGLE_TOKEN, idToken)
                            .putExtra(RegisterActivity.EXTRA_GOOGLE_NAME, name)
                            .putExtra(RegisterActivity.EXTRA_GOOGLE_EMAIL, email));
                } else {
                    a.showError(e);
                }
            }
        });
    }
}
