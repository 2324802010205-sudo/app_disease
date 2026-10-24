package com.example.project_final;

import android.app.Application;

import com.example.project_final.api.Session;

public class RiceApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Session.init(this);
    }
}
