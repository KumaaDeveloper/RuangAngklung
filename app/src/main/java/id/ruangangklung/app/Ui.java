package id.ruangangklung.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int CREAM = Color.rgb(255, 248, 233);
    public static final int PAPER = Color.rgb(255, 253, 246);
    public static final int FOREST = Color.rgb(35, 89, 71);
    public static final int GREEN = Color.rgb(36, 105, 82);
    public static final int GOLD = Color.rgb(232, 178, 79);
    public static final int LIGHT_GOLD = Color.rgb(255, 237, 196);
    public static final int MINT = Color.rgb(226, 244, 222);
    public static final int PEACH = Color.rgb(255, 225, 195);
    public static final int INK = Color.rgb(44, 64, 52);
    public static final int MUTED = Color.rgb(87, 102, 88);
    public static final int BORDER = Color.rgb(224, 214, 191);
    private Ui() { }

    public static int dp(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + .5f);
    }

    public static GradientDrawable rounded(Context context, int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static GradientDrawable bordered(Context context, int color, int stroke, float radius) {
        GradientDrawable drawable = rounded(context, color, radius);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static RippleDrawable ripple(Context context, GradientDrawable background) {
        return new RippleDrawable(ColorStateList.valueOf(0x22888888), background, null);
    }

    public static TextView text(Context context, String copy, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(copy);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    public static TextView button(Context context, String copy, boolean primary) {
        TextView view = text(context, copy, 16, primary ? PAPER : GREEN, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 56));
        view.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        view.setBackground(ripple(context, bordered(context, primary ? GREEN : PAPER, primary ? GREEN : BORDER, 20)));
        view.setClickable(true);
        view.setFocusable(true);
        touchMotion(view);
        return view;
    }

    /** A subtle response for touch targets, without intercepting their click listeners. */
    public static void touchMotion(View view) {
        view.setOnTouchListener((target, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                target.animate().scaleX(.97f).scaleY(.97f).setDuration(90).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
            return false;
        });
    }

    public static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    public static View gap(Context context, int height) {
        View space = new View(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, height)));
        return space;
    }

    public static LinearLayout.LayoutParams margins(Context context, int width, int height,
                                                      int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }
}
