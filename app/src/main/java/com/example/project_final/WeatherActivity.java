package com.example.project_final;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;

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

/** Thời tiết theo tọa độ hiện tại hoặc tỉnh đã chọn. */
public class WeatherActivity extends BaseActivity {
    private Session.WeatherLocation selected;
    private CancellationSignal locationRequest;
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) locate();
                else toast("Cần quyền vị trí để xem thời tiết nơi bạn đang ở. Bạn vẫn có thể chọn tỉnh/thành.");
            });

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
        selected = Session.weatherLocation();
        findViewById(R.id.location_name).setOnClickListener(v -> chooseLocation());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            if (selected != null && selected.lat != null && hasLocationPermission()) locate();
            else load();
        });
        if (selected == null) chooseLocation();
        else if (selected.lat != null && hasLocationPermission()) locate();
        else load();
    }

    private void chooseLocation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chọn vị trí thời tiết")
                .setItems(new String[]{"Vị trí hiện tại", "Chọn tỉnh/thành"}, (dialog, which) -> {
                    if (which == 0) {
                        if (hasLocationPermission()) locate();
                        else locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION});
                    } else chooseProvince();
                }).show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void locate() {
        if (!hasLocationPermission()) return;
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean precise = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        String provider = precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                : manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
        if (provider == null) {
            toast("Hãy bật dịch vụ vị trí hoặc chọn tỉnh/thành.");
            if (selected != null) load();
            return;
        }
        if (locationRequest != null) locationRequest.cancel();
        locationRequest = new CancellationSignal();
        busy(true);
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, locationRequest,
                    ContextCompat.getMainExecutor(this), location -> {
                        locationRequest = null;
                        busy(false);
                        if (location == null) {
                            toast("Không xác định được vị trí. Hãy thử lại hoặc chọn tỉnh/thành.");
                            if (selected != null) load();
                            return;
                        }
                        selected = new Session.WeatherLocation("Vị trí hiện tại", location.getLatitude(), location.getLongitude());
                        Session.setWeatherLocation(selected.label, selected.lat, selected.lng);
                        load();
                    });
        } catch (SecurityException | IllegalArgumentException e) {
            locationRequest = null;
            busy(false);
            toast("Không lấy được vị trí. Hãy chọn tỉnh/thành.");
            if (selected != null) load();
        }
    }

    private void chooseProvince() {
        withMeta(o -> Ui.chooser(this, "Chọn tỉnh/thành", o.provinces, (i, label) -> {
            selected = new Session.WeatherLocation(label, null, null);
            Session.setWeatherLocation(label, null, null);
            load();
        }));
    }

    private void load() {
        if (selected == null) return;
        Session.WeatherLocation requested = selected;
        setText(R.id.location_name, requested.label);
        busy(true);
        call(api().weather(requested.label, requested.lat, requested.lng), new ApiCallback<Weather>() {
            @Override
            public void onSuccess(Weather w) {
                busy(false);
                if (requested == selected) bind(w);
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (requested == selected) {
                    setText(R.id.weather_sub, "Không tải được dữ liệu thời tiết cho " + requested.label);
                    setText(R.id.updated_at, "");
                    setText(R.id.temp_n, "--");
                    setText(R.id.cond_title, "Chưa có dữ liệu");
                    setText(R.id.cond_desc, e.message);
                    ((LinearLayout) findViewById(R.id.list_hourly)).removeAllViews();
                    ((LinearLayout) findViewById(R.id.list_daily)).removeAllViews();
                    ((LinearLayout) findViewById(R.id.list_details)).removeAllViews();
                    showError(e);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (locationRequest != null) locationRequest.cancel();
        super.onDestroy();
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
        String windDir = c.windDirection != null ? c.windDirection.replace("Hướng ", "") : "";
        String[] note = {windDir, c.uvLabel, c.visibilityLabel};
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
