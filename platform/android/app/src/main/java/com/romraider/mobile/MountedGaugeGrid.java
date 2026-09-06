/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.content.Context;
import android.view.View;
import android.widget.GridLayout;
import com.romraider.portable.gauge.GaugeDashboardLayout;
import java.util.Collections;
import java.util.List;

/** The same gauge container in both the scrolling logger and a fitted mounted viewport. */
final class MountedGaugeGrid extends GridLayout {
    private boolean fitted;
    private int limit = 6;
    private double aspect = 320.0 / 250;
    private List<GaugeDashboardLayout.Tile> tiles = Collections.emptyList();

    MountedGaugeGrid(Context context) { super(context); }

    void configure(boolean fitted, int limit, double aspect) {
        if (limit < 1 || limit > 6) throw new IllegalArgumentException("Choose one to six gauges");
        boolean changed = this.fitted != fitted || this.limit != limit || this.aspect != aspect;
        this.fitted = fitted; this.limit = limit; this.aspect = aspect;
        updateVisibility();
        if (changed) requestLayout();
    }

    private void updateVisibility() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.setVisibility(!fitted || i < limit ? VISIBLE : GONE);
            if (child instanceof MobileGaugeView) ((MobileGaugeView) child).setFitToViewport(fitted);
        }
    }

    @Override public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateVisibility();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (!fitted) { super.onMeasure(widthSpec, heightSpec); return; }
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        tiles = GaugeDashboardLayout.fit(width, height, Math.min(limit, getChildCount()), aspect,
                Math.round(2 * getResources().getDisplayMetrics().density));
        for (int i = 0; i < tiles.size(); i++) {
            GaugeDashboardLayout.Tile tile = tiles.get(i);
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(tile.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tile.height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!fitted) { super.onLayout(changed, left, top, right, bottom); return; }
        for (int i = 0; i < tiles.size() && i < getChildCount(); i++) {
            GaugeDashboardLayout.Tile tile = tiles.get(i);
            getChildAt(i).layout(tile.left, tile.top, tile.left + tile.width, tile.top + tile.height);
        }
    }
}
