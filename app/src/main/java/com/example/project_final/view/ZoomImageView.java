package com.example.project_final.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/** ImageView hỗ trợ chụm để phóng to, kéo để di chuyển và chạm đúp. */
public class ZoomImageView extends AppCompatImageView {
    private static final float MAX_SCALE = 5f;

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float baseScale = 1f;
    private float lastX, lastY;
    private int activePointer = -1;

    public ZoomImageView(Context c) {
        this(c, null);
    }

    public ZoomImageView(Context c, AttributeSet a) {
        super(c, a);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float current = currentScale();
                float target = Math.max(baseScale, Math.min(MAX_SCALE * baseScale, current * d.getScaleFactor()));
                matrix.postScale(target / current, target / current, d.getFocusX(), d.getFocusY());
                clamp();
                setImageMatrix(matrix);
                return true;
            }
        });
        gestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float current = currentScale();
                float target = current > baseScale * 1.5f ? baseScale : baseScale * 3f;
                matrix.postScale(target / current, target / current, e.getX(), e.getY());
                clamp();
                setImageMatrix(matrix);
                return true;
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) fit();
    }

    @Override
    public void setImageDrawable(android.graphics.drawable.Drawable d) {
        super.setImageDrawable(d);
        fit();
    }

    private void fit() {
        if (getDrawable() == null || getWidth() == 0) return;
        float dw = getDrawable().getIntrinsicWidth(), dh = getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        baseScale = Math.min(getWidth() / dw, getHeight() / dh);
        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate((getWidth() - dw * baseScale) / 2f, (getHeight() - dh * baseScale) / 2f);
        setImageMatrix(matrix);
    }

    private float currentScale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    /** Giữ ảnh không trôi ra ngoài khung. */
    private void clamp() {
        if (getDrawable() == null) return;
        RectF r = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        matrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointer = e.getPointerId(0);
                lastX = e.getX();
                lastY = e.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && e.getPointerCount() == 1) {
                    int idx = e.findPointerIndex(activePointer);
                    if (idx >= 0) {
                        matrix.postTranslate(e.getX(idx) - lastX, e.getY(idx) - lastY);
                        lastX = e.getX(idx);
                        lastY = e.getY(idx);
                        clamp();
                        setImageMatrix(matrix);
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointer = e.getPointerId(0);
                lastX = e.getX(0);
                lastY = e.getY(0);
                break;
            default:
                break;
        }
        return true;
    }
}
