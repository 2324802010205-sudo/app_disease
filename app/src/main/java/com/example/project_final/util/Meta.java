package com.example.project_final.util;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiClient;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.MetaOptions;

import java.util.ArrayList;
import java.util.List;

/** Danh mục lựa chọn (giống lúa, giai đoạn, tỉnh...) tải một lần từ backend và giữ trong bộ nhớ. */
public final class Meta {
    public interface Listener {
        void onLoaded(MetaOptions options);

        void onError(ApiError error);
    }

    private static MetaOptions cached;
    private static boolean loading;
    private static final List<Listener> waiting = new ArrayList<>();

    private Meta() {}

    public static MetaOptions cached() {
        return cached;
    }

    public static void load(Listener l) {
        if (cached != null) {
            l.onLoaded(cached);
            return;
        }
        waiting.add(l);
        if (loading) return;
        loading = true;
        ApiClient.api().options().enqueue(new ApiCallback<MetaOptions>() {
            @Override
            public void onSuccess(MetaOptions body) {
                loading = false;
                cached = body;
                List<Listener> copy = new ArrayList<>(waiting);
                waiting.clear();
                for (Listener w : copy) w.onLoaded(body);
            }

            @Override
            public void onError(ApiError error) {
                loading = false;
                List<Listener> copy = new ArrayList<>(waiting);
                waiting.clear();
                for (Listener w : copy) w.onError(error);
            }
        });
    }
}
