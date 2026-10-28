package com.example.project_final;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Session;
import com.example.project_final.model.RiceField;
import com.example.project_final.model.Page;
import com.example.project_final.model.Scan;
import com.example.project_final.model.Stats;
import com.example.project_final.model.User;
import com.example.project_final.model.Weather;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;
import com.example.project_final.view.NavBar;

import java.util.List;

public class HomeActivity extends BaseActivity {
    private SwipeRefreshLayout refresh;
    private int pendingLoads;

    @Override
    protected int layout() {
        return R.layout.activity_home;
    }

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected void onReady() {
        if (Session.isLoggedIn() && android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 3401);
        }
        setupBottomNav(NavBar.HOME);
        refresh = findViewById(R.id.refresh);
        refresh.setColorSchemeResources(R.color.primary);
        refresh.setOnRefreshListener(this::load);

        setText(R.id.stat_scans_sub, "Trong 30 ngày");
        findViewById(R.id.btn_capture).setOnClickListener(v -> {
            if (requireAccount("quét và phân tích bệnh lúa")) startActivity(new android.content.Intent(this, ScanActivity.class));
        });
        findViewById(R.id.btn_gallery).setOnClickListener(v -> {
            if (requireAccount("quét và phân tích bệnh lúa")) startActivity(new android.content.Intent(this, ScanActivity.class));
        });
        go(R.id.card_weather, WeatherActivity.class);
        go(R.id.stat_scans, HistoryActivity.class);
        go(R.id.stat_fields, FieldsActivity.class);
        go(R.id.stat_alerts, HistoryActivity.class);
        go(R.id.link_fields, FieldsActivity.class);
        go(R.id.link_history, HistoryActivity.class);
        findViewById(R.id.btn_guest_login).setOnClickListener(v -> toLoginFromGuest(false));
        findViewById(R.id.btn_guest_register).setOnClickListener(v -> toLoginFromGuest(true));
        go(R.id.tile_community, CommunityActivity.class);
        go(R.id.tile_map, RiskMapActivity.class);
        go(R.id.tile_library, LibraryActivity.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void toLoginFromGuest(boolean register) {
        Session.clear();
        android.content.Intent it = new android.content.Intent(this, LoginActivity.class).putExtra(LoginActivity.EXTRA_OPEN_REGISTER, register);
        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(it);
    }

    /** Khách: ẩn phần dữ liệu cá nhân (thống kê, ruộng, lượt quét) và hiện lời mời đăng nhập. */
    private void showGuestLayout() {
        int[] personal = {R.id.stat_row, R.id.fields_header, R.id.list_fields, R.id.scans_header, R.id.list_scans};
        for (int id : personal) findViewById(id).setVisibility(android.view.View.GONE);
        findViewById(R.id.guest_card).setVisibility(android.view.View.VISIBLE);
    }

    private void load() {
        if (isGuest()) {
            setText(R.id.greeting, Look.greeting());
            ((ImageView) findViewById(R.id.greeting_icon)).setImageResource(Look.isNight() ? R.drawable.ic_dark_mode : R.drawable.ic_sun);
            showGuestLayout();
            pendingLoads = 1;
            loadWeather(null);
            return;
        }
        User u = Session.user();
        String name = u == null ? "" : u.firstName();
        setText(R.id.greeting, Look.greeting() + (name.isEmpty() ? "" : ", " + name));
        ((ImageView) findViewById(R.id.greeting_icon)).setImageResource(Look.isNight() ? R.drawable.ic_dark_mode : R.drawable.ic_sun);

        pendingLoads = 4;
        loadWeather(u);
        loadStats();
        loadFields();
        loadScans();
    }

    private void done() {
        if (--pendingLoads <= 0) refresh.setRefreshing(false);
    }

    // ---- thời tiết

    private void loadWeather(User u) {
        Session.WeatherLocation location = Session.weatherLocation();
        if (location == null) {
            setText(R.id.weather_place, "Chọn tỉnh/thành để xem thời tiết");
            setText(R.id.weather_temp, "--°");
            setText(R.id.weather_title, "Chưa có vị trí");
            setText(R.id.weather_hint, "Chạm để chọn khu vực của bạn");
            go(R.id.card_weather, WeatherActivity.class);
            done();
            return;
        }
        call(api().weather(location.label, location.lat, location.lng), new ApiCallback<Weather>() {
            @Override
            public void onSuccess(Weather w) {
                done();
                Weather.Current c = w.current;
                setText(R.id.weather_place, w.location);
                setText(R.id.weather_temp, Math.round(c.tempC) + "°C");
                setText(R.id.weather_title, c.condition.label);
                setText(R.id.weather_hint, w.advice.isEmpty() ? "" : w.advice.get(0));
                setText(R.id.weather_humidity, c.humidity + "%");
                setText(R.id.weather_rain, c.rainProbability + "%");
                setText(R.id.weather_wind, Math.round(c.windKmh) + " km/h");
                ((ImageView) findViewById(R.id.weather_icon)).setImageResource(Look.weatherIcon(c.condition.icon));
            }

            @Override
            public void onError(ApiError e) {
                done();
                setText(R.id.weather_place, location.label);
                setText(R.id.weather_temp, "--°C");
                setText(R.id.weather_title, "Chưa có dữ liệu");
                setText(R.id.weather_hint, e.message);
                setText(R.id.weather_humidity, "--");
                setText(R.id.weather_rain, "--");
                setText(R.id.weather_wind, "--");
            }
        });
    }

    // ---- thống kê 30 ngày

    private void loadStats() {
        call(api().stats("month"), new ApiCallback<Stats>() {
            @Override
            public void onSuccess(Stats s) {
                done();
                setText(R.id.stat_scans_n, String.valueOf(s.scans));
                setText(R.id.stat_alerts_n, String.valueOf(s.alerts));
                setText(R.id.stat_alerts_sub, s.alerts > 0 ? "Cần chú ý" : "An toàn");
            }

            @Override
            public void onError(ApiError e) {
                done();
                setText(R.id.stat_scans_n, "-");
                setText(R.id.stat_alerts_n, "-");
            }
        });
    }

    // ---- ruộng của bạn

    private void loadFields() {
        call(api().fields("updated"), new ApiCallback<List<RiceField>>() {
            @Override
            public void onSuccess(List<RiceField> list) {
                done();
                double area = 0;
                for (RiceField f : list) area += f.areaHa;
                setText(R.id.stat_fields_n, String.valueOf(list.size()));
                setText(R.id.stat_fields_sub, list.isEmpty() ? "Chưa có ruộng" : "Tổng " + Fmt.area(area));
                bindFields(list);
            }

            @Override
            public void onError(ApiError e) {
                done();
                setText(R.id.stat_fields_n, "-");
                LinearLayout box = findViewById(R.id.list_fields);
                box.removeAllViews();
                Ui.emptyNote(box, e.message, null);
            }
        });
    }

    private void bindFields(List<RiceField> list) {
        LinearLayout box = findViewById(R.id.list_fields);
        box.removeAllViews();
        if (list.isEmpty()) {
            Ui.emptyNote(box, "Chưa có ruộng nào. Chạm để thêm ruộng đầu tiên", () -> startActivity(new android.content.Intent(this, AddFieldActivity.class)));
            return;
        }
        int shown = Math.min(2, list.size());
        Ui.fill(box, R.layout.item_home_field, shown, (item, i) -> {
            RiceField f = list.get(i);
            Img.load(item.findViewById(R.id.img), f.photoUrl, R.drawable.ph_field);
            Ui.text(item, R.id.name, f.name);
            Ui.text(item, R.id.meta, Fmt.area(f.areaHa) + (f.variety == null || f.variety.isEmpty() ? "" : " | " + f.variety));
            Ui.text(item, R.id.updated, Fmt.updated(f.lastScanAt));
            Look.fieldPill(item.findViewById(R.id.status), f);
            ((LinearLayout.LayoutParams) item.getLayoutParams()).setMarginStart(i == 0 ? 0 : (int) Ui.dp(this, 8));
            item.setOnClickListener(v -> open(FieldDetailActivity.class, f.id));
        });
    }

    // ---- lần quét gần đây

    private void loadScans() {
        call(api().scans("all", null, null, null, 3), new ApiCallback<Page<Scan>>() {
            @Override
            public void onSuccess(Page<Scan> page) {
                done();
                LinearLayout box = findViewById(R.id.list_scans);
                box.removeAllViews();
                if (page.items.isEmpty()) {
                    Ui.emptyNote(box, "Chưa có lần quét nào. Hãy chụp ảnh lá lúa để bắt đầu", () -> startActivity(new android.content.Intent(HomeActivity.this, ScanActivity.class)));
                    return;
                }
                Ui.fill(box, R.layout.item_home_scan, page.items.size(), (item, i) -> {
                    Scan s = page.items.get(i);
                    Img.load(item.findViewById(R.id.img), s.displayImage(), R.drawable.bg_image_placeholder);
                    Ui.text(item, R.id.title, scanTitle(s));
                    Ui.text(item, R.id.meta, (s.fieldName == null ? "" : s.fieldName + "  |  ") + Fmt.dateTime(s.createdAt));
                    Look.scanPill(item.findViewById(R.id.status), s);
                    item.setOnClickListener(v -> open(ResultDetailActivity.class, s.id));
                });
            }

            @Override
            public void onError(ApiError e) {
                done();
                LinearLayout box = findViewById(R.id.list_scans);
                box.removeAllViews();
                Ui.emptyNote(box, e.message, null);
            }
        });
    }

    static String scanTitle(Scan s) {
        if (s.isDiagnosed() && s.diseaseName != null) return s.diseaseName;
        if (s.needsRetake()) return "Ảnh chưa rõ, cần chụp lại";
        if (s.isPending()) return "Đang chờ phân tích";
        return "Lần quét";
    }
}
