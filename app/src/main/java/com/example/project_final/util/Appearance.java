package com.example.project_final.util;

import android.content.Context;
import android.app.LocaleManager;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Local preferences apply before a screen is drawn and are also synced to the user profile. */
public final class Appearance {
    private static final String PREFS = "riceguard_appearance";
    private Appearance() { }

    public static String theme(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("theme", "SYSTEM");
    }

    public static String language(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("language", "vi");
    }

    public static void init(Context context) {
        applyTheme(context, theme(context));
        applyLanguage(context, language(context));
    }

    public static void applyTheme(Context context, String value) {
        if (!"DARK".equals(value) && !"LIGHT".equals(value)) value = "SYSTEM";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("theme", value).apply();
        AppCompatDelegate.setDefaultNightMode("DARK".equals(value)
                ? AppCompatDelegate.MODE_NIGHT_YES : "LIGHT".equals(value)
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void applyLanguage(Context context, String value) {
        if (!"en".equals(value)) value = "vi";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("language", value).apply();
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null && !value.equals(manager.getApplicationLocales().toLanguageTags()))
                manager.setApplicationLocales(LocaleList.forLanguageTags(value));
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value));
        }
    }
}
