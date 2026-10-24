package com.example.project_final.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * TextView có icon (drawableStart/End) mà cả cụm "icon + chữ" luôn nằm sát nhau ở giữa nút.
 * TextView thường dính icon vào mép nút nên khi nút rộng, icon và chữ cách nhau rất xa.
 * Ở đây phần trống được chia đều vào hai bên padding; chỉ áp dụng khi chữ căn giữa theo chiều ngang và chữ nằm trên một dòng.
 */
public class IconTextView extends AppCompatTextView {
    private final int basePaddingStart;
    private final int basePaddingEnd;

    public IconTextView(Context c) {
        this(c, null);
    }

    public IconTextView(Context c, AttributeSet a) {
        this(c, a, android.R.attr.textViewStyle);
    }

    public IconTextView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        basePaddingStart = getPaddingStart();
        basePaddingEnd = getPaddingEnd();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerContent();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        centerContent();
    }

    @Override
    public void setCompoundDrawablesRelative(Drawable s, Drawable t, Drawable e, Drawable b) {
        super.setCompoundDrawablesRelative(s, t, e, b);
        centerContent();
    }

    @Override
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(int s, int t, int e, int b) {
        super.setCompoundDrawablesRelativeWithIntrinsicBounds(s, t, e, b);
        centerContent();
    }

    private void centerContent() {
        int w = getWidth();
        if (w <= 0 || getText() == null) return;
        if ((getGravity() & android.view.Gravity.HORIZONTAL_GRAVITY_MASK) != android.view.Gravity.CENTER_HORIZONTAL) {
            apply(basePaddingStart, basePaddingEnd);
            return;
        }
        Drawable[] d = getCompoundDrawablesRelative();
        float icons = 0;
        for (Drawable x : new Drawable[]{d[0], d[2]}) {
            if (x == null) continue;
            int iw = x.getBounds().width() > 0 ? x.getBounds().width() : x.getIntrinsicWidth();
            icons += iw + getCompoundDrawablePadding();
        }
        float text = getPaint().measureText(getText(), 0, getText().length());
        float free = w - basePaddingStart - basePaddingEnd - icons - text;
        if (icons == 0 || free <= 2 || getLineCount() > 1) {
            apply(basePaddingStart, basePaddingEnd);
            return;
        }
        int extra = (int) (free / 2f);
        apply(basePaddingStart + extra, basePaddingEnd + extra);
    }

    private void apply(int start, int end) {
        if (getPaddingStart() != start || getPaddingEnd() != end) setPaddingRelative(start, getPaddingTop(), end, getPaddingBottom());
    }
}
