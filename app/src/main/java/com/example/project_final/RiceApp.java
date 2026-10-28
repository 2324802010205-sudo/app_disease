package com.example.project_final;

import android.app.Application;

import com.example.project_final.api.Session;
import com.example.project_final.push.PushRegistration;
import com.example.project_final.util.Appearance;

public class RiceApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Session.init(this);
        Appearance.init(this);
        PushRegistration.sync(this);
    }
}
