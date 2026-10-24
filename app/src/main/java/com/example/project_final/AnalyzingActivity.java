package com.example.project_final;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Session;
import com.example.project_final.model.Scan;
import com.example.project_final.model.User;
import com.example.project_final.util.Uploads;
import com.google.gson.Gson;

import java.io.File;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;

/** Gửi ảnh lên backend để chẩn đoán. Tiến độ hiển thị chạy theo giai đoạn thật: nén -> tải lên -> AI phân tích. */
public class AnalyzingActivity extends BaseActivity {
    public static final String EXTRA_NOTE = "note";
    private static final String[] TITLES = {"Chuẩn bị ảnh", "Tải ảnh lên", "AI phân tích", "Tạo gợi ý"};
    private static final String[] DESCS = {"Đã tối ưu hình ảnh", "Đang gửi ảnh tới máy chủ",
            "Đang so sánh với cơ sở dữ liệu bệnh hại", "Đang tổng hợp kết quả và khuyến nghị"};

    private ValueAnimator animator;
    private Call<Scan> request;
    private boolean finished;
    private LinearLayout steps;
    private ProgressBar bar;
    private TextView percent;

    @Override
    protected int layout() {
        return R.layout.activity_analyzing;
    }

    @Override
    protected void onReady() {
        String path = getIntent().getStringExtra(ReviewPhotoActivity.EXTRA_PATH);
        if (path == null) {
            finish();
            return;
        }
        Glide.with(this).load(new File(path)).into((ImageView) findViewById(R.id.leaf));

        steps = findViewById(R.id.list_steps);
        Ui.fill(steps, R.layout.item_step, 4, (item, i) -> {
            Ui.text(item, R.id.title, TITLES[i]);
            Ui.text(item, R.id.desc, DESCS[i]);
            if (i == 3) item.findViewById(R.id.line).setVisibility(View.INVISIBLE);
        });
        bar = findViewById(R.id.progress);
        percent = findViewById(R.id.percent);

        // Thanh tiến độ chạy chậm dần tới 92% trong lúc chờ máy chủ, về 100% khi có kết quả.
        animator = ValueAnimator.ofInt(0, 92);
        animator.setDuration(25_000);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        animator.addUpdateListener(a -> show((int) a.getAnimatedValue()));
        animator.start();

        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            if (request != null) request.cancel();
            finish();
        });
        send(new File(path));
    }

    private void send(File file) {
        User u = Session.user();
        String fieldId = getIntent().getStringExtra(ScanActivity.EXTRA_FIELD_ID);
        // Không chọn ruộng thì gửi tỉnh/huyện của người dùng để bản đồ nguy cơ ghi nhận đúng khu vực.
        String province = fieldId == null && u != null ? u.province : null;
        String district = fieldId == null && u != null ? u.district : null;
        Map<String, RequestBody> parts = Uploads.text(
                "fieldId", fieldId,
                "note", getIntent().getStringExtra(EXTRA_NOTE),
                "province", province,
                "district", district);
        MultipartBody.Part image = Uploads.filePart(file, "image");
        request = api().createScan(image, parts);
        call(request, new ApiCallback<Scan>() {
            @Override
            public void onSuccess(Scan scan) {
                finished = true;
                animator.cancel();
                show(100);
                startActivity(new Intent(AnalyzingActivity.this, ResultActivity.class)
                        .putExtra(ResultActivity.EXTRA_SCAN_JSON, new Gson().toJson(scan)));
                finish();
            }

            @Override
            public void onError(ApiError e) {
                animator.cancel();
                toast(e.message);
                finish();
            }
        });
    }

    private void show(int p) {
        bar.setProgress(p);
        percent.setText(p + "%");
        for (int i = 0; i < steps.getChildCount(); i++) {
            int doneAt = (i + 1) * 25;
            int state = p >= doneAt ? 2 : (p >= doneAt - 25 ? 1 : 0);
            styleStep(steps.getChildAt(i), state);
        }
    }

    /** state: 0 waiting, 1 running, 2 done */
    private void styleStep(View item, int state) {
        FrameLayout dot = item.findViewById(R.id.dot);
        ImageView icon = item.findViewById(R.id.dot_icon);
        dot.setBackgroundResource(state == 2 ? R.drawable.bg_dot_primary : state == 1 ? R.drawable.bg_dot_current : R.drawable.bg_dot_empty);
        icon.setVisibility(state == 2 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        if (animator != null) animator.cancel();
        super.onDestroy();
    }
}
