package com.example.project_final.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.project_final.api.ApiClient;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.ImageRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Response;

/** Tải lần lượt nhiều ảnh lên /media/upload (nén trước) trên luồng nền, kết quả trả về luồng chính. */
public final class ImageUploader {
    public interface Callback {
        void onDone(List<ImageRef> refs);

        void onError(String message);
    }

    private ImageUploader() {}

    /** kind: avatar | field | scan | diary | post | feedback. */
    public static void upload(Context ctx, List<Uri> uris, String kind, Callback cb) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<ImageRef> out = new ArrayList<>();
            try {
                for (Uri u : uris) {
                    MultipartBody.Part part = Uploads.imagePart(app, u, "file");
                    Response<ImageRef> r = ApiClient.api().uploadImage(part, kind).execute();
                    if (!r.isSuccessful() || r.body() == null) {
                        ApiError e = ApiError.from(r);
                        main.post(() -> cb.onError(e.message));
                        return;
                    }
                    out.add(r.body());
                }
                main.post(() -> cb.onDone(out));
            } catch (IOException e) {
                ApiError err = e instanceof java.net.SocketTimeoutException || e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                        ? ApiError.from(e) : null;
                String msg = err != null ? err.message : "Không đọc được ảnh: " + e.getMessage();
                main.post(() -> cb.onError(msg));
            }
        }).start();
    }
}
