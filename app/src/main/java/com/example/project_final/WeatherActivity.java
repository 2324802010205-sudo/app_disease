package com.example.project_final;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Session;
import com.example.project_final.model.User;
import com.example.project_final.model.Weather;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Look;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Thời tiết theo tỉnh: hiện tại, theo giờ, 7 ngày và lời khuyên canh tác. */
public class WeatherActivity extends BaseActivity {
    private String province;

    @Override
    protected int layout() {
        return R.layout.activity_weather;
    }

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected void onReady() {
        province = Session.province();
        findViewById(R.id.location_name).setOnClickListener(v -> chooseProvince());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> load());
        if (province == null || province.isEmpty()) chooseProvince();
        else load();
    }

    private void chooseProvince() {
        withMeta(o -> Ui.chooser(this, "Chọn tỉnh/thành", o.provinces, (i, label) -> {
            province = label;
            if (isGuest()) Session.setGuestProvince(label);
            load();
        }));
    }

    private void load() {
        if (province == null) return;
        setText(R.id.location_name, province);
        busy(true);
        call(api().weather(province, null, null), new ApiCallback<Weather>() {
            @Override
            public void onSuccess(Weather w) {
                busy(false);
                bind(w);
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }

    private void bind(Weather w) {
        Weather.Current c = w.current;
        setText(R.id.location_name, w.location);
        setText(R.id.weather_sub, "Dự báo cho khu vực " + w.location);
        setText(R.id.updated_at, "Cập nhật lúc " + new SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.US).format(new Date(w.updatedAt)));
        setText(R.id.temp_n, String.valueOf(Math.round(c.tempC)));
        setText(R.id.cond_title, c.condition.label);
        setText(R.id.cond_desc, w.advice.isEmpty() ? "" : w.advice.get(0));
        ((ImageView) findViewById(R.id.cond_icon)).setImageResource(Look.weatherIcon(c.condition.icon));
        setText(R.id.humid_n, c.humidity + "%");
        setText(R.id.rain_n, c.rainProbability + "%");
        setText(R.id.feels_n, Math.round(c.feelsLikeC) + "°C");

        bindHourly(w.hourly);
        bindDaily(w.daily);
        bindDetails(c);

        setText(R.id.advice_title, w.advice.isEmpty() ? "Khuyến nghị cho nhà nông" : w.advice.get(0));
        StringBuilder rest = new StringBuilder();
        for (int i = 1; i < w.advice.size(); i++) {
            if (rest.length() > 0) rest.append("\n");
            rest.append(w.advice.get(i));
        }
        setText(R.id.advice_text, rest.length() == 0 ? "Theo dõi thời tiết hằng ngày để chọn thời điểm phun thuốc và bón phân phù hợp." : rest);
    }

    private void bindHourly(List<Weather.Hour> hours) {
        LinearLayout box = findViewById(R.id.list_hourly);
        box.removeAllViews();
        Ui.fill(box, R.layout.item_hour, hours.size(), (item, i) -> {
            Weather.Hour h = hours.get(i);
            Ui.text(item, R.id.time, h.time);
            Ui.image(item, R.id.icon, Look.weatherIcon(h.condition.icon));
            Ui.text(item, R.id.temp, Fmt.temp(h.tempC));
            Ui.text(item, R.id.rain, h.rainProbability + "%");
        });
    }

    private void bindDaily(List<Weather.Day> days) {
        LinearLayout box = findViewById(R.id.list_daily);
        box.removeAllViews();
        Ui.fill(box, R.layout.item_day, days.size(), (item, i) -> {
            Weather.Day d = days.get(i);
            Ui.text(item, R.id.day, Fmt.weekday(d.date, i == 0) + "  " + Fmt.isoDate(d.date).substring(0, 5));
            Ui.image(item, R.id.icon, Look.weatherIcon(d.condition.icon));
            Ui.text(item, R.id.rain, d.rainProbability + "%");
            Ui.text(item, R.id.range, Math.round(d.tempMinC) + "° – " + Math.round(d.tempMaxC) + "°");
        });
    }

    private void bindDetails(Weather.Current c) {
        String[] label = {"Gió", "Chỉ số UV", "Tầm nhìn"};
        String[] value = {Math.round(c.windKmh) + " km/h", String.valueOf(Math.round(c.uvIndex)), Math.round(c.visibilityKm) + " km"};
        String[] note = {c.windDirection, c.uvLabel, c.visibilityLabel};
        int[] ic = {R.drawable.ic_air, R.drawable.ic_w_sun, R.drawable.ic_visibility};
        int[] tint = {R.color.blue, 0, R.color.primary};
        LinearLayout box = findViewById(R.id.list_details);
        box.removeAllViews();
        Ui.fill(box, R.layout.item_weather_detail, 3, (item, i) -> {
            ImageView img = item.findViewById(R.id.icon);
            img.setImageResource(ic[i]);
            if (tint[i] != 0) img.setColorFilter(getColor(tint[i]));
            Ui.text(item, R.id.label, label[i]);
            Ui.text(item, R.id.value, value[i]);
            TextView n = item.findViewById(R.id.note);
            n.setText(note[i]);
            n.setTextColor(getColor(R.color.muted));
        });
    }
}
