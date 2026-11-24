package com.rotarypumpers.meraki;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import com.rotarypumpers.meraki.core.ShotInsights;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Recorded scale samples aligned to estimated brew start. Each path breaks at gaps. */
final class RecentShotChart extends View {
    private static final int[] COLORS = {
        0xff6e42aa, 0xff12776f, 0xffa75417, 0xff245caa, 0xffa43858,
        0xff42702e, 0xff786019, 0xff894185, 0xff117193, 0xff7d8981
    };
    private final List<List<double[]>> traces = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double timeMax = 10, weightMin = 0, weightMax = 10;
    private final float density;
    private int selected = -1;

    static int color(int index) { return COLORS[index]; }

    RecentShotChart(Context context, JSONArray shots) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setContentDescription("Overlaid cup weight during brew, aligned to estimated brew start");
        for (int i = 0; i < shots.length(); i++) {
            JSONObject shot = shots.optJSONObject(i);
            List<double[]> trace = new ArrayList<>();
            traces.add(trace);
            JSONArray rows = shot.optJSONArray("samples");
            double start = ShotInsights.bound(shot, "start_bounds");
            double stop = ShotInsights.bound(shot, "stop_bounds");
            if (rows == null || !Double.isFinite(start)) continue;
            double prior = Double.NaN;
            double gap = ShotInsights.gapLimit(shot);
            for (int j = 0; j < rows.length(); j++) {
                JSONObject row = rows.optJSONObject(j);
                if (row == null) { prior = Double.NaN; continue; }
                double at = row.optDouble("uptime"), weight = row.optDouble("weight_g");
                if (!Double.isFinite(at) || !Double.isFinite(weight)) {
                    prior = Double.NaN;
                    continue;
                }
                if (at < start || Double.isFinite(stop) && at > stop) continue;
                boolean join = Double.isFinite(prior) && at > prior && at - prior <= gap;
                trace.add(new double[]{at - start, weight, join ? 1 : 0});
                prior = at;
                timeMax = Math.max(timeMax, at - start);
                weightMin = Math.min(weightMin, weight);
                weightMax = Math.max(weightMax, weight);
            }
        }
        timeMax = Math.ceil(timeMax / 5) * 5;
        weightMin = Math.floor(weightMin);
        weightMax = Math.ceil(weightMax / 5) * 5;
    }

    boolean hasCurve(int index) {
        for (double[] point : traces.get(index)) if (point[2] != 0) return true;
        return false;
    }

    void toggleTrace(int index) {
        selected = selected == index ? -1 : index;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = 34 * density, top = 22 * density;
        float right = getWidth() - 18 * density, bottom = getHeight() - 34 * density;
        if (right <= left || bottom <= top) return;
        paint.setTextSize(11 * density);
        paint.setColor(0xff59635d);
        canvas.drawText("Cup weight (g)", left, 14 * density, paint);
        for (int tick = 0; tick <= 4; tick++) {
            float y = bottom - (bottom - top) * tick / 4;
            paint.setColor(0xffe2e6e1);
            canvas.drawLine(left, y, right, y, paint);
            paint.setColor(0xff59635d);
            canvas.drawText(String.format(java.util.Locale.US, "%.0f",
                weightMin + (weightMax - weightMin) * tick / 4), 2 * density, y + 4 * density, paint);
        }
        for (int tick = 0; tick <= 4; tick++) {
            float x = left + (right - left) * tick / 4;
            String label = String.format(java.util.Locale.US, "%.0f", timeMax * tick / 4);
            canvas.drawText(label, x - paint.measureText(label) / 2, bottom + 15 * density, paint);
        }
        String caption = "Seconds from brew start";
        canvas.drawText(caption, (left + right - paint.measureText(caption)) / 2,
            bottom + 30 * density, paint);
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < traces.size(); i++) {
            if (selected >= 0 && i != selected) continue;
            Path path = new Path();
            for (double[] point : traces.get(i)) {
                float x = left + (float)(point[0] / timeMax) * (right - left);
                float y = bottom - (float)((point[1] - weightMin) / (weightMax - weightMin)) * (bottom - top);
                if (point[2] == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            paint.setColor(color(i));
            paint.setStrokeWidth((selected < 0 ? 1.5f : 2.5f) * density);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
}
