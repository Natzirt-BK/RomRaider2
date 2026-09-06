/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;

/** Searchable native previews. Sample readings never enter the recording/dashboard. */
final class MobileGaugeStylePicker {
    private MobileGaugeStylePicker() { }

    static AlertDialog show(Activity activity, String title, MobileGaugeTheme selected,
            MobileGaugeTheme defaultTheme, Consumer<MobileGaugeTheme> choose) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), 0);
        root.setBackgroundColor(0xFF10171D);
        TextView hint = new TextView(activity);
        hint.setText(R.string.gauge_style_sample_hint);
        hint.setTextColor(0xFFBBC8D3);
        root.addView(hint);
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert).setTitle(title)
                .setView(root).setNegativeButton("Cancel", null).create();
        if (defaultTheme != null) {
            Button useDefault = new Button(activity);
            useDefault.setText(activity.getString(R.string.gauge_style_use_default, selected == null ? "✓ " : "", defaultTheme.displayName));
            useDefault.setSelected(selected == null);
            useDefault.setOnClickListener(view -> { choose.accept(null); dialog.dismiss(); });
            root.addView(useDefault, new LinearLayout.LayoutParams(-1, -2));
        }
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Search gauge styles");
        search.setContentDescription("Search gauge styles");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFFBBC8D3);
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        TextView empty = new TextView(activity);
        empty.setText(R.string.gauge_style_no_matches);
        empty.setTextColor(Color.WHITE);
        empty.setVisibility(View.GONE);
        root.addView(empty);
        ScrollView scroll = new ScrollView(activity);
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(2);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Runnable populate = () -> {
            grid.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int index = 0;
            for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
                if (!theme.displayName.toLowerCase(Locale.ROOT).contains(query)) continue;
                boolean current = theme == selected;
                LinearLayout tile = new LinearLayout(activity);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));
                GradientDrawable background = new GradientDrawable();
                background.setColor(0xFF182129);
                background.setCornerRadius(dp(activity, 10));
                background.setStroke(dp(activity, current ? 2 : 1), current ? 0xFFFF4F58 : 0xFF344350);
                tile.setBackground(background);
                tile.setSelected(current);
                tile.setFocusable(true);
                tile.setContentDescription(theme.displayName + (current ? ", selected" : ", tap to apply"));
                tile.setOnClickListener(view -> { choose.accept(theme); dialog.dismiss(); });
                MobileGaugeView preview = new MobileGaugeView(activity);
                preview.setTheme(theme);
                preview.setFitToViewport(true);
                preview.setValue("P-RPM", "Engine Speed", "4200", "rpm", 4200, 800, 6200);
                preview.setDataState("SAMPLE");
                preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(preview, new LinearLayout.LayoutParams(-1, dp(activity, 125)));
                TextView label = new TextView(activity);
                label.setText(activity.getString(R.string.gauge_style_label, current ? "✓ " : "", theme.displayName));
                label.setTextColor(Color.WHITE);
                label.setTextSize(14);
                label.setGravity(Gravity.CENTER);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(index / grid.getColumnCount()),
                        GridLayout.spec(index % grid.getColumnCount(), 1f));
                params.width = 0;
                params.setMargins(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
                grid.addView(tile, params);
                index++;
            }
            empty.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
            scroll.scrollTo(0, 0);
        };
        grid.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int columns = Math.max(1, Math.min(4, (right - left) / dp(activity, 155)));
            if (grid.getColumnCount() != columns) {
                grid.removeAllViews();
                grid.setColumnCount(columns);
                populate.run();
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate.run(); }
            @Override public void afterTextChanged(Editable value) { }
        });
        populate.run();
        // Keep the keyboard closed until search is explicitly focused.
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        dialog.show();
        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(Math.min(dp(activity, 900), (int) (metrics.widthPixels * .96f)),
                    (int) (metrics.heightPixels * .88f));
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        return dialog;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
