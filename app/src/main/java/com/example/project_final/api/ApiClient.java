package com.example.project_final.api;

import android.util.Log;

import com.example.project_final.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Điểm truy cập duy nhất tới backend: ApiClient.api().xxx(). */
public final class ApiClient {
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String TAG = "RiceApi";

    private static volatile RiceApi api;

    private ApiClient() {}

    public static RiceApi api() {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) api = build();
            }
        }
        return api;
    }

    private static RiceApi build() {
        // Gson bỏ qua trường null khi gửi lên: backend coi trường vắng mặt là "không đổi".
        Gson gson = new GsonBuilder().create();

        // Client riêng, không có Authenticator, chỉ dùng để làm mới token (tránh vòng lặp 401).
        OkHttpClient bare = baseClient().build();
        RiceApi bareApi = retrofit(bare, gson).create(RiceApi.class);

        OkHttpClient.Builder client = baseClient()
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder b = chain.request().newBuilder();
                    String token = Session.idToken();
                    if (token != null && chain.request().header("Authorization") == null) b.header("Authorization", "Bearer " + token);
                    return chain.proceed(b.build());
                })
                .authenticator(new TokenAuthenticator(bareApi));
        if (BuildConfig.DEBUG) client.addInterceptor(debugLogging());
        return retrofit(client.build(), gson).create(RiceApi.class);
    }

    private static OkHttpClient.Builder baseClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)   // quét ảnh cần chờ AI nên đọc lâu hơn
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header(HEADER_REQUEST_ID, UUID.randomUUID().toString().substring(0, 8))
                        .header("Accept", "application/json")
                        .build()));
    }

    /** Ghi log yêu cầu/phản hồi (tag RiceApi). Yêu cầu tải ảnh chỉ ghi dòng đầu để không đổ dữ liệu nhị phân vào logcat. */
    private static okhttp3.Interceptor debugLogging() {
        HttpLoggingInterceptor full = new HttpLoggingInterceptor(msg -> Log.d(TAG, msg));
        full.setLevel(HttpLoggingInterceptor.Level.BODY);
        full.redactHeader("Authorization");
        HttpLoggingInterceptor basic = new HttpLoggingInterceptor(msg -> Log.d(TAG, msg));
        basic.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return chain -> {
            okhttp3.RequestBody body = chain.request().body();
            boolean multipart = body != null && body.contentType() != null && "multipart".equals(body.contentType().type());
            String path = chain.request().url().encodedPath();
            boolean credentials = path.contains("/auth/") || path.endsWith("/users/me/devices");
            return (multipart || credentials ? basic : full).intercept(chain);
        };
    }

    private static Retrofit retrofit(OkHttpClient client, Gson gson) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }
}
