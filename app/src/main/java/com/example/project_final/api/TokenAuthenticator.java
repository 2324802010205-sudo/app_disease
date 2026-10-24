package com.example.project_final.api;

import android.util.Log;

import com.example.project_final.model.AuthResponse;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;

/** Khi gặp 401, đổi refresh token lấy ID token mới rồi gửi lại yêu cầu một lần. Hết hạn hẳn thì đăng xuất. */
final class TokenAuthenticator implements Authenticator {
    private static final String TAG = "RiceApi";
    private final RiceApi bareApi;

    TokenAuthenticator(RiceApi bareApi) {
        this.bareApi = bareApi;
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        if (response.request().url().encodedPath().contains("/auth/")) return null;
        if (responseCount(response) >= 2) return null;

        synchronized (Session.class) {
            String sent = bearer(response.request());
            String current = Session.idToken();
            // Luồng khác vừa làm mới token trong lúc yêu cầu này đang chờ: dùng luôn token mới.
            if (current != null && !current.equals(sent)) return withToken(response.request(), current);

            String refresh = Session.refreshToken();
            if (refresh == null) return null;
            try {
                retrofit2.Response<AuthResponse> r = bareApi.refresh(new Requests.Refresh(refresh)).execute();
                if (r.isSuccessful() && r.body() != null && r.body().idToken != null) {
                    Session.save(r.body());
                    return withToken(response.request(), r.body().idToken);
                }
                if (r.code() == 400 || r.code() == 401) {
                    Log.w(TAG, "Refresh token bị từ chối (" + r.code() + "), đăng xuất");
                    Session.expire();
                }
            } catch (IOException e) {
                Log.w(TAG, "Không làm mới được token do lỗi mạng: " + e);
                throw e;
            }
            return null;
        }
    }

    private static String bearer(Request req) {
        String h = req.header("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : null;
    }

    private static Request withToken(Request req, String token) {
        return req.newBuilder().header("Authorization", "Bearer " + token).build();
    }

    private static int responseCount(Response r) {
        int n = 1;
        while ((r = r.priorResponse()) != null) n++;
        return n;
    }
}
