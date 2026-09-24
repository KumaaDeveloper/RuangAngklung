package id.ruangangklung.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Warm welcome while the app starts; Android 12+ also gets the matching system splash icon. */
public final class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable open = this::openApp;
    private boolean opened;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.CREAM);
        getWindow().setNavigationBarColor(Ui.CREAM);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.CREAM);
        LinearLayout center = Ui.column(this);
        center.setGravity(Gravity.CENTER);
        center.setPadding(Ui.dp(this, 30), Ui.dp(this, 20), Ui.dp(this, 30), Ui.dp(this, 20));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_angklung);
        logo.setContentDescription("Ikon angklung bambu Ruang Angklung");
        center.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 142), Ui.dp(this, 142)));
        center.addView(Ui.gap(this, 15));

        TextView name = Ui.text(this, "Ruang Angklung", 32, Ui.FOREST, true);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.BOLD));
        center.addView(name);
        TextView slogan = Ui.text(this, "Ayo kenali nada, lalu main bersama!", 15, Ui.MUTED, false);
        slogan.setGravity(Gravity.CENTER);
        slogan.setPadding(0, Ui.dp(this, 10), 0, 0);
        center.addView(slogan);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        root.addView(center, position);

        TextView footer = Ui.text(this, "BUNYIKAN CERITA NUSANTARA  ♪", 11, Ui.GREEN, true);
        footer.setLetterSpacing(.11f);
        footer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-1, Ui.dp(this, 50), Gravity.BOTTOM);
        bottom.bottomMargin = Ui.dp(this, 25);
        root.addView(footer, bottom);
        setContentView(root);

        logo.setAlpha(0f);
        logo.setScaleX(.78f);
        logo.setScaleY(.78f);
        logo.setRotation(-10f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(620).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        name.setAlpha(0f);
        name.setTranslationY(Ui.dp(this, 12));
        name.animate().alpha(1f).translationY(0).setStartDelay(220).setDuration(550).start();
        slogan.setAlpha(0f);
        slogan.animate().alpha(1f).setStartDelay(430).setDuration(460).start();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.postDelayed(open, 1450);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(open);
        super.onPause();
    }

    private void openApp() {
        if (opened || isFinishing()) return;
        opened = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
