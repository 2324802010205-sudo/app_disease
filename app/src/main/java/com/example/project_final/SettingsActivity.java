package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.User;
import com.example.project_final.util.Forms;
import com.example.project_final.util.Appearance;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends BaseActivity {
    private MaterialSwitch[] switches = new MaterialSwitch[3];
    private boolean binding;
    private TextView languageView;

    @Override
    protected int layout() {
        return R.layout.activity_settings;
    }

    @Override
    protected void onReady() {
        Ui.fill(findViewById(R.id.list_theme), R.layout.item_theme_option, 3, (item, i) -> {
            TextView option = (TextView) item;
            String[] values = {"SYSTEM", "LIGHT", "DARK"};
            int[] labels = {R.string.theme_system, R.string.theme_light, R.string.theme_dark};
            option.setText(labels[i]);
            option.setSelected(values[i].equals(Appearance.theme(this)));
            option.setOnClickListener(v -> saveAppearance(values[i], null));
        });
        languageView = findViewById(R.id.dd_language);
        languageView.setText("en".equals(Appearance.language(this)) ? R.string.language_english : R.string.language_vietnamese);
        languageView.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setItems(new String[]{getString(R.string.language_vietnamese), getString(R.string.language_english)},
                        (d, which) -> saveAppearance(null, which == 0 ? "vi" : "en"))
                .show());
        String[] title = {getString(R.string.notify_alert), getString(R.string.notify_reminder), getString(R.string.notify_community)};
        String[] desc = {getString(R.string.notify_alert_desc), getString(R.string.notify_reminder_desc), getString(R.string.notify_community_desc)};
        int[] icon = {R.drawable.ic_eco, R.drawable.ic_calendar, R.drawable.ic_group};
        Ui.fill(findViewById(R.id.list_switches), R.layout.item_switch_row, 3, (item, i) -> {
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(icon[i]);
            Ui.text(item, R.id.title, title[i]);
            Ui.text(item, R.id.desc, desc[i]);
            MaterialSwitch sw = item.findViewById(R.id.switcher);
            switches[i] = sw;
            sw.setOnCheckedChangeListener((b, checked) -> {
                if (!binding) saveSwitches();
            });
        });
        bindSettings(Session.user());

        String[] appLabels = {getString(R.string.version), getString(R.string.about_app), getString(R.string.help_faq), getString(R.string.privacy)};
        int[] appIcons = {R.drawable.ic_info, R.drawable.ic_description, R.drawable.ic_help, R.drawable.ic_shield_check};
        String[] infoText = {null, getString(R.string.about_app_body), getString(R.string.help_faq_body), getString(R.string.privacy_body)};
        Ui.fill(findViewById(R.id.list_app), R.layout.item_menu_row, 4, (item, i) -> {
            Ui.text(item, R.id.label, appLabels[i]);
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(appIcons[i]);
            if (i == 0) {
                item.findViewById(R.id.chevron).setVisibility(View.GONE);
                TextView value = item.findViewById(R.id.value);
                value.setVisibility(View.VISIBLE);
                value.setText(BuildConfig.VERSION_NAME);
            } else {
                item.findViewById(R.id.row).setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle(appLabels[i]).setMessage(infoText[i]).setPositiveButton(R.string.understood, null).show());
            }
        });

        findViewById(R.id.btn_password).setOnClickListener(v -> changePassword());
        findViewById(R.id.btn_reset).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_question)
                .setMessage(R.string.delete_account_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_permanently, (d, w) -> deleteAccount())
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        call(api().me(), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                Session.setUser(u);
                bindSettings(u);
            }

            @Override
            public void onError(ApiError e) {
                // dùng cài đặt đã lưu trên máy
            }
        });
    }

    private void bindSettings(User u) {
        if (u == null) return;
        User.Settings s = u.settingsOrDefault();
        binding = true;
        switches[0].setChecked(s.notifyAlert);
        switches[1].setChecked(s.notifyReminder);
        switches[2].setChecked(s.notifyCommunity);
        binding = false;
        if (s.theme != null && !s.theme.equals(Appearance.theme(this))) Appearance.applyTheme(this, s.theme);
        if (s.language != null && !s.language.equals(Appearance.language(this))) Appearance.applyLanguage(this, s.language);
    }

    private void saveSwitches() {
        Requests.Settings body = new Requests.Settings();
        body.notifyAlert = switches[0].isChecked();
        body.notifyReminder = switches[1].isChecked();
        body.notifyCommunity = switches[2].isChecked();
        call(api().updateSettings(body), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                Session.setUser(u);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                bindSettings(Session.user());
            }
        });
    }

    private void saveAppearance(String theme, String language) {
        Requests.Settings body = new Requests.Settings();
        body.theme = theme;
        body.language = language;
        call(api().updateSettings(body), new ApiCallback<User>() {
            @Override public void onSuccess(User u) {
                Session.setUser(u);
                if (theme != null) Appearance.applyTheme(SettingsActivity.this, theme);
                if (language != null) Appearance.applyLanguage(SettingsActivity.this, language);
            }
            @Override public void onError(ApiError error) { showError(error); }
        });
    }

    private void changePassword() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText current = passwordField(getString(R.string.current_password));
        EditText next = passwordField(getString(R.string.new_password_hint));
        box.addView(current);
        box.addView(next);
        new AlertDialog.Builder(this)
                .setTitle(R.string.change_password)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.change_password, (d, w) -> {
                    String cur = current.getText().toString();
                    String nw = next.getText().toString();
                    if (cur.isEmpty() || nw.length() < 8) {
                        toast(getString(R.string.password_validation));
                        return;
                    }
                    busy(true);
                    call(api().changePassword(new Requests.ChangePassword(cur, nw)), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void body) {
                            busy(false);
                            toast(getString(R.string.password_changed));
                            Session.clear();
                            toLogin();
                        }

                        @Override
                        public void onError(ApiError e) {
                            busy(false);
                            showError(e);
                        }
                    });
                })
                .show();
    }

    private EditText passwordField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void deleteAccount() {
        busy(true);
        call(api().deleteAccount(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                busy(false);
                Session.clear();
                toast(getString(R.string.account_deleted));
                toLogin();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }
}
