package com.example.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.db.TrafficBucket;

import java.util.ArrayList;
import java.util.List;

public class TrafficChartView extends View {
    private final List<TrafficBucket> buckets = new ArrayList<>();
    private Paint gridPaint;
    private Paint textPaint;
    private Paint allowedPaint;
    private Paint blockedPaint;
    private Paint bgBarPaint;

    public TrafficChartView(Context context) {
        super(context);
        init();
    }

    public TrafficChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TrafficChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#1E293B"));
        gridPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextSize(spToPx(10));
        textPaint.setTextAlign(Paint.Align.CENTER);

        allowedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        allowedPaint.setColor(Color.parseColor("#00E676"));

        blockedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blockedPaint.setColor(Color.parseColor("#FF3366"));

        bgBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgBarPaint.setColor(Color.parseColor("#15233A"));
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setBuckets(List<TrafficBucket> newBuckets) {
        buckets.clear();
        if (newBuckets != null) {
            buckets.addAll(newBuckets);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float bottomPadding = spToPx(20);
        float topPadding = spToPx(8);
        float chartHeight = height - bottomPadding - topPadding;
        float chartBottom = height - bottomPadding;

        // Draw horizontal subtle grid lines
        canvas.drawLine(0, chartBottom, width, chartBottom, gridPaint);
        canvas.drawLine(0, chartBottom - chartHeight * 0.5f, width, chartBottom - chartHeight * 0.5f, gridPaint);
        canvas.drawLine(0, topPadding, width, topPadding, gridPaint);

        if (buckets.isEmpty()) {
            Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            emptyPaint.setColor(Color.parseColor("#64748B"));
            emptyPaint.setTextSize(spToPx(12));
            emptyPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No traffic activity recorded yet", width / 2f, chartBottom - chartHeight / 2f, emptyPaint);
            return;
        }

        // Find max total count
        int maxCount = 1;
        for (TrafficBucket b : buckets) {
            if (b.getTotal() > maxCount) {
                maxCount = b.getTotal();
            }
        }
        // Round maxCount up slightly
        maxCount = Math.max(5, (int)(maxCount * 1.15f));

        int n = buckets.size();
        float columnWidth = (float) width / n;
        float barWidth = Math.max(spToPx(12), columnWidth * 0.55f);
        float cornerRadius = spToPx(4);

        for (int i = 0; i < n; i++) {
            TrafficBucket b = buckets.get(i);
            float cx = i * columnWidth + columnWidth / 2f;
            float left = cx - barWidth / 2f;
            float right = cx + barWidth / 2f;

            // Draw full height background pillar
            RectF bgRect = new RectF(left, topPadding, right, chartBottom);
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgBarPaint);

            // Calculate heights
            float allowedRatio = (float) b.getAllowedCount() / maxCount;
            float blockedRatio = (float) b.getBlockedCount() / maxCount;

            float allowedHeight = chartHeight * allowedRatio;
            float blockedHeight = chartHeight * blockedRatio;

            // Draw allowed bar
            if (allowedHeight > 0) {
                float allowedTop = chartBottom - allowedHeight;
                RectF allowedRect = new RectF(left, allowedTop, right, chartBottom);
                canvas.drawRoundRect(allowedRect, cornerRadius, cornerRadius, allowedPaint);
            }

            // Draw blocked bar on top
            if (blockedHeight > 0) {
                float blockedTop = chartBottom - allowedHeight - blockedHeight;
                float blockedBottom = chartBottom - allowedHeight;
                RectF blockedRect = new RectF(left, blockedTop, right, blockedBottom);
                canvas.drawRoundRect(blockedRect, cornerRadius, cornerRadius, blockedPaint);
            }

            // Draw time label
            canvas.drawText(b.getLabel(), cx, height - spToPx(4), textPaint);
        }
    }
}
