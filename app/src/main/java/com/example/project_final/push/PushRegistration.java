package com.example.project_final.push;

import android.content.Context;
import android.util.Log;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiClient;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

/** Keeps the current user's FCM token registered with the backend. */
public final class PushRegistration {
    private static final String TAG = "RicePush";
    private static final String PREFS = "riceguard_push";
    private static final String TOKEN = "token";

    private PushRegistration() {}

    public static void sync(Context context) {
        if (!Session.isLoggedIn() || FirebaseApp.getApps(context).isEmpty()) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> register(context, token))
                .addOnFailureListener(error -> Log.w(TAG, "FCM token unavailable", error));
    }

    public static void register(Context context, String token) {
        if (token == null || token.isEmpty() || !Session.isLoggedIn()) return;
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN, token).apply();
        ApiClient.api().addDevice(new Requests.Device(token)).enqueue(new ApiCallback<Void>() {
            @Override public void onSuccess(Void ignored) { }
            @Override public void onError(ApiError error) { Log.w(TAG, "Device registration failed: " + error.message); }
        });
    }

    public static void unregister(Context context, Runnable done) {
        String token = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TOKEN, null);
        if (token == null || !Session.isLoggedIn()) { done.run(); return; }
        ApiClient.api().removeDevice(new Requests.Device(token)).enqueue(new ApiCallback<Void>() {
            @Override public void onSuccess(Void ignored) { done.run(); }
            @Override public void onError(ApiError error) {
                Log.w(TAG, "Device removal failed: " + error.message);
                done.run();
            }
        });
    }
}
