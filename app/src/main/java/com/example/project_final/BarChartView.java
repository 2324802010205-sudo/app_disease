package com.example.project_final;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Simple bar chart with horizontal grid lines, value labels and day labels. */
public class BarChartView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] data = new int[0];
    private String[] labels = new String[0];
    private int max = 4;

    public BarChartView(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        grid.setColor(0xFFE6E8DE);
        grid.setStrokeWidth(d);
        axis.setColor(getResources().getColor(R.color.ink, null));
        axis.setTextSize(getResources().getDimension(R.dimen.text_13));
        value.setColor(getResources().getColor(R.color.ink_deep, null));
        value.setTextSize(getResources().getDimension(R.dimen.text_13));
        value.setTextAlign(Paint.Align.CENTER);
        value.setFakeBoldText(true);
    }

    public void setData(int[] data, String[] labels, int max) {
        this.data = data;
        this.labels = labels;
        this.max = max;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        if (data.length == 0) return;
        float left = 26 * d, bottom = getHeight() - 22 * d, top = 6 * d, right = getWidth() - 4 * d;
        float step = (bottom - top) / max;
        axis.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= max; i += 2) {
            float y = bottom - i * step;
            canvas.drawLine(left, y, right, y, grid);
            canvas.drawText(String.valueOf(i), left - 6 * d, y + 4 * d, axis);
        }
        float slot = (right - left) / data.length;
        float barW = slot * 0.62f;
        int peak = 0;
        for (int i = 1; i < data.length; i++) if (data[i] > data[peak]) peak = i;
        for (int i = 0; i < data.length; i++) {
            float cx = left + slot * (i + 0.5f);
            float h = data[i] * step;
            rect.set(cx - barW / 2, bottom - h, cx + barW / 2, bottom);
            int c1 = i == peak ? 0xFF3C9A4B : 0xFF8CCB94;
            int c2 = i == peak ? 0xFF1F6B3E : 0xFF56A96A;
            bar.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, c1, c2, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, 5 * d, 5 * d, bar);
            canvas.drawRect(rect.left, rect.bottom - 6 * d, rect.right, rect.bottom, bar);
            if (data[i] > 0) canvas.drawText(String.valueOf(data[i]), cx, rect.top - 4 * d, value);
            axis.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(labels[i], cx, getHeight() - 6 * d, axis);
        }
    }
}
