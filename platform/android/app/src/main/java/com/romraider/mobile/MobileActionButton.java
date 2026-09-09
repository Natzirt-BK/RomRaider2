/* RomRaider2 - GPL 2.0 or later. */
package com.romraider.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.HapticFeedbackConstants;
import android.widget.Button;

/** Visible touch, focus and disabled feedback for the app's custom button surfaces. */
final class MobileActionButton extends Button {
    MobileActionButton(Context context) {
        super(context);
        // The theme's elevation animator must not override our pressed surface.
        setStateListAnimator(null);
    }

    @Override public void setBackground(Drawable background) {
        if (background != null && !(background instanceof RippleDrawable)) {
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE); mask.setCornerRadius(8 * getResources().getDisplayMetrics().density);
            background = new RippleDrawable(ColorStateList.valueOf(0x55ffffff), background, mask);
        }
        super.setBackground(background);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        float density = getResources().getDisplayMetrics().density;
        setAlpha(isEnabled() ? 1f : .42f);
        setTranslationY(isEnabled() && isPressed() ? density : 0);
        setElevation(isEnabled() && !isPressed() ? 2 * density : 0);
    }

    @Override public boolean performClick() {
        if (isEnabled()) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        return super.performClick();
    }
}
