package com.example.project_final.api;

import android.util.Log;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Callback chạy trên luồng chính, tách riêng nhánh thành công và nhánh lỗi đã chuẩn hóa. */
public abstract class ApiCallback<T> implements Callback<T> {
    private static final String TAG = "RiceApi";

    public abstract void onSuccess(T body);

    public abstract void onError(ApiError error);

    @Override
    public final void onResponse(Call<T> call, Response<T> response) {
        if (call.isCanceled()) return;
        if (response.isSuccessful()) {
            onSuccess(response.body());
        } else {
            ApiError e = ApiError.from(response);
            Log.w(TAG, call.request().method() + " " + call.request().url().encodedPath() + " -> " + e);
            onError(e);
        }
    }

    @Override
    public final void onFailure(Call<T> call, Throwable t) {
        if (call.isCanceled()) return;
        ApiError e = ApiError.from(t);
        Log.w(TAG, call.request().method() + " " + call.request().url().encodedPath() + " failed: " + t, t);
        onError(e);
    }
}
