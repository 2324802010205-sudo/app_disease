package com.example.project_final.util;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.project_final.BuildConfig;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * Đăng nhập Google bằng Credential Manager. Chỉ lấy Google ID token; việc xác thực và tạo phiên do backend (Firebase) làm.
 * Cần riceguard.googleWebClientId trong gradle.properties; thiếu thì báo lỗi rõ ràng thay vì im lặng.
 */
public final class GoogleSignIn {
    public interface Callback {
        void onSuccess(String idToken, String displayName, String email);

        void onFailure(String message);

        /** Người dùng đóng hộp chọn tài khoản: không cần báo lỗi. */
        void onCancelled();
    }

    private GoogleSignIn() {}

    public static boolean isConfigured() {
        return !BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty();
    }

    public static void start(Activity activity, Callback cb) {
        if (!isConfigured()) {
            cb.onFailure("Chưa cấu hình đăng nhập Google. Thêm riceguard.googleWebClientId vào gradle.properties");
            return;
        }
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential c = result.getCredential();
                        if (c instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(((CustomCredential) c).getData());
                            cb.onSuccess(g.getIdToken(), g.getDisplayName(), g.getId());
                        } else {
                            cb.onFailure("Không nhận được tài khoản Google, vui lòng thử lại");
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) cb.onCancelled();
                        else cb.onFailure("Không đăng nhập được bằng Google: " + e.getMessage());
                    }
                });
    }
}
