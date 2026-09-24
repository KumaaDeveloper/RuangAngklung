package id.ruangangklung.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A small hand-drawn bamboo and woven border for the page headers. */
public final class BambooMotifView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf = new Path();

    public BambooMotifView(Context context) {
        super(context);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float step = Ui.dp(getContext(), 52);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.4f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x88739865);
        for (float x = step * .4f; x < w; x += step) {
            canvas.drawLine(x, h * .12f, x, h * .85f, paint);
            canvas.drawLine(x - 3, h * .5f, x + 3, h * .5f, paint);
            leaf.reset();
            leaf.moveTo(x, h * .48f);
            leaf.quadTo(x + step * .22f, -h * .07f, x + step * .47f, h * .23f);
            leaf.quadTo(x + step * .28f, h * .42f, x, h * .48f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x7774A784);
            canvas.drawPath(leaf, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0x88739865);
            canvas.drawCircle(x + step * .68f, h * .62f, Ui.dp(getContext(), 1.4f), paint);
        }
        paint.setColor(0x668B704E);
        for (float x = 0; x < w; x += Ui.dp(getContext(), 13))
            canvas.drawLine(x, h * .91f, x + Ui.dp(getContext(), 6), h * .75f, paint);
    }
}
