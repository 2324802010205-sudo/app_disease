package com.example.project_final;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/** Small helpers for filling lists of item layouts. */
public final class Ui {
    private Ui() {}

    public interface Binder {
        void bind(View item, int index);
    }

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static void fill(ViewGroup parent, int layout, int count, Binder binder) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        for (int i = 0; i < count; i++) {
            View item = inf.inflate(layout, parent, false);
            binder.bind(item, i);
            parent.addView(item);
        }
    }

    public static void text(View root, int id, CharSequence s) {
        ((TextView) root.findViewById(id)).setText(s);
    }

    public static void image(View root, int id, int res) {
        ((ImageView) root.findViewById(id)).setImageResource(res);
    }

    /** Dòng thông báo trong danh sách trống (kèm hành động tùy chọn). */
    public static void emptyNote(ViewGroup parent, String message, Runnable action) {
        TextView t = new TextView(parent.getContext());
        t.setText(message);
        t.setGravity(android.view.Gravity.CENTER);
        t.setTextColor(parent.getResources().getColor(action == null ? R.color.muted : R.color.primary, null));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, parent.getResources().getDimension(R.dimen.text_16));
        if (action != null) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            t.setOnClickListener(v -> action.run());
        }
        int p = (int) dp(parent.getContext(), 18);
        t.setPadding(p, p, p, p);
        parent.addView(t, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Applies a pill look (text colour + background) to a TextView. */
    public static void pill(TextView tv, String label, int iconRes, int textColor, int bgRes) {
        tv.setText(label);
        tv.setTextColor(tv.getResources().getColor(textColor, null));
        tv.setBackgroundResource(bgRes);
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(tv.getResources().getColor(textColor, null)));
    }

    /** Turns a TextView into a simple dropdown backed by a popup menu. */
    public static void dropdown(TextView field, String[] options) {
        dropdown(field, options, null);
    }

    public interface Index {
        void on(int index);
    }

    public static void dropdown(TextView field, String[] options, Index onPick) {
        field.setOnClickListener(v -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(v.getContext(), v);
            for (String o : options) menu.getMenu().add(o);
            menu.setOnMenuItemClickListener(item -> {
                field.setText(item.getTitle());
                field.setTextColor(field.getResources().getColor(R.color.ink_deep, null));
                if (onPick != null) {
                    for (int i = 0; i < options.length; i++) if (options[i].contentEquals(item.getTitle())) onPick.on(i);
                }
                return true;
            });
            menu.show();
        });
    }

    public interface Pick {
        void onPick(int index, String label);
    }

    /** Hộp chọn một mục trong danh sách dài (ví dụ 63 tỉnh), có cuộn. */
    public static void chooser(Context c, String title, java.util.List<String> items, Pick pick) {
        new androidx.appcompat.app.AlertDialog.Builder(c)
                .setTitle(title)
                .setItems(items.toArray(new String[0]), (d, which) -> pick.onPick(which, items.get(which)))
                .show();
    }

    /** Biến TextView thành ô chọn mở hộp thoại danh sách; đổi chữ và màu khi đã chọn. */
    public static void picker(TextView field, String title, java.util.List<String> items, Pick pick) {
        field.setOnClickListener(v -> chooser(v.getContext(), title, items, (i, label) -> {
            field.setText(label);
            field.setTextColor(field.getResources().getColor(R.color.ink_deep, null));
            if (pick != null) pick.onPick(i, label);
        }));
    }

    /** Sizes compound icons relative to the text so 24dp vectors do not dwarf small labels. */
    public static void scaleIcons(View root) {
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            android.graphics.drawable.Drawable[] d = tv.getCompoundDrawablesRelative();
            boolean any = false;
            for (android.graphics.drawable.Drawable x : d) any |= x != null;
            if (any) {
                float dp = tv.getResources().getDisplayMetrics().density;
                int size = Math.round(Math.max(13 * dp, Math.min(22 * dp, tv.getTextSize() * 1.3f)));
                boolean changed = false;
                for (android.graphics.drawable.Drawable x : d) {
                    if (x != null && (x.getBounds().width() != size || x.getBounds().height() != size)) {
                        x.setBounds(0, 0, size, size);
                        changed = true;
                    }
                }
                if (changed) tv.setCompoundDrawablesRelative(d[0], d[1], d[2], d[3]);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) scaleIcons(g.getChildAt(i));
        }
    }
}
