package com.example.project_final.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.project_final.R;
import com.example.project_final.api.ApiError;

import java.util.ArrayList;
import java.util.List;

/**
 * Bao quanh nội dung một màn hình và hiển thị 4 trạng thái: đang tải, có dữ liệu, trống, lỗi (kèm thử lại).
 * Mọi view con khai báo trong XML là "nội dung"; lớp phủ trạng thái được thêm bên trên.
 */
public class StateLayout extends FrameLayout {
    private final List<View> content = new ArrayList<>();
    private View overlay;

    public StateLayout(Context c) {
        super(c);
    }

    public StateLayout(Context c, AttributeSet a) {
        super(c, a);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); i++) content.add(getChildAt(i));
        overlay = LayoutInflater.from(getContext()).inflate(R.layout.view_state, this, false);
        addView(overlay);
        overlay.setVisibility(GONE);
    }

    public void loading() {
        show(true, true, 0, null, null, null);
    }

    public void content() {
        for (View v : content) v.setVisibility(VISIBLE);
        overlay.setVisibility(GONE);
    }

    public void empty(String title, String message) {
        show(false, true, R.drawable.ic_eco, title, message, null);
    }

    public void empty(int iconRes, String title, String message) {
        show(false, true, iconRes, title, message, null);
    }

    public void error(ApiError e, Runnable retry) {
        int icon = e.isNetwork() ? R.drawable.ic_cloud : R.drawable.ic_error;
        show(false, true, icon, e.isNetwork() ? "Mất kết nối" : "Không tải được dữ liệu", e.message, retry);
    }

    private void show(boolean progress, boolean hideContent, int icon, String title, String message, Runnable retry) {
        if (hideContent) for (View v : content) v.setVisibility(INVISIBLE);
        overlay.setVisibility(VISIBLE);
        overlay.findViewById(R.id.state_progress).setVisibility(progress ? VISIBLE : GONE);
        ImageView iv = overlay.findViewById(R.id.state_icon);
        iv.setVisibility(icon != 0 ? VISIBLE : GONE);
        if (icon != 0) iv.setImageResource(icon);
        text(R.id.state_title, title);
        text(R.id.state_message, message);
        View btn = overlay.findViewById(R.id.state_retry);
        btn.setVisibility(retry != null ? VISIBLE : GONE);
        btn.setOnClickListener(retry == null ? null : v -> retry.run());
    }

    private void text(int id, String s) {
        TextView t = overlay.findViewById(id);
        t.setVisibility(s == null || s.isEmpty() ? GONE : VISIBLE);
        t.setText(s);
    }
}
