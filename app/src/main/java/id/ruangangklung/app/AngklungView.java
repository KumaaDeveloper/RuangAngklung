package id.ruangangklung.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Scalable canvas illustration, inspired by the supplied angklung photos. */
public final class AngklungView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float sway;
    private String note = "C4";
    private boolean showNoteLabel = true;
    private ValueAnimator animation;
    private final RectF rect = new RectF();

    public AngklungView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setNote(note);
    }

    public void setNote(String note) {
        this.note = note;
        setContentDescription("Angklung bambu, nada aktif " + NoteCatalog.label(note));
        invalidate();
    }

    /** The home page renders its note in a separate caption below the illustration. */
    public void setShowNoteLabel(boolean show) {
        showNoteLabel = show;
        invalidate();
    }

    public void ring() {
        if (animation != null) animation.cancel();
        animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(650);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            sway = (float) (Math.sin(progress * Math.PI * 4) * (1 - progress) * 12);
            invalidate();
        });
        animation.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animation != null) animation.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        rect.set(0, 0, w, h);
        paint.setShader(new LinearGradient(0, 0, w, h, 0xFFFFF3D5, 0xFFF6D6AC, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, Ui.dp(getContext(), 26), Ui.dp(getContext(), 26), paint);
        paint.setShader(null);

        // Light curves and confetti make the instrument inviting without hiding the bamboo.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        paint.setColor(0x444FA079);
        for (int i = 0; i < 4; i++) {
            float radius = w * (.22f + i * .14f);
            canvas.drawCircle(w * .50f, h * .48f, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 7; i++) {
            paint.setColor(i % 2 == 0 ? 0xFF74A78A : 0xFFE7AE69);
            canvas.drawCircle(w * .09f + (i % 2) * 7, h * .12f + i * h * .12f, 2.3f, paint);
            canvas.drawCircle(w * .91f - (i % 2) * 7, h * .12f + i * h * .12f, 2.3f, paint);
        }
        drawBambooFlank(canvas, w * .09f, h, 1f);
        drawBambooFlank(canvas, w * .91f, h, -1f);

        float size = Math.min(w * .66f, h * .91f);
        float left = (w - size) / 2f;
        float top = h * .045f;
        canvas.save();
        canvas.rotate(sway, w / 2f, top + size * .15f);
        drawInstrument(canvas, left, top, size);
        canvas.restore();

        if (showNoteLabel) {
            paint.setColor(Ui.FOREST);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(Math.min(Ui.dp(getContext(), 15), w * .047f));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("NADA  " + NoteCatalog.label(note), w / 2f, h - Ui.dp(getContext(), 17), paint);
        }
    }

    private void drawBambooFlank(Canvas canvas, float x, float h, float direction) {
        paint.setColor(0xAA65936B);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 2));
        canvas.drawLine(x, h * .28f, x, h * .80f, paint);
        for (int i = 0; i < 3; i++) {
            float y = h * (.39f + .16f * i);
            canvas.drawLine(x - Ui.dp(getContext(), 3), y,
                    x + Ui.dp(getContext(), 3), y, paint);
            Path leaf = new Path();
            leaf.moveTo(x, y);
            leaf.quadTo(x + direction * Ui.dp(getContext(), 19), y - Ui.dp(getContext(), 30),
                    x + direction * Ui.dp(getContext(), 31), y - Ui.dp(getContext(), 19));
            leaf.quadTo(x + direction * Ui.dp(getContext(), 15), y - Ui.dp(getContext(), 10), x, y);
            paint.setColor(0x9967986A);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(leaf, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xAA65936B);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawInstrument(Canvas canvas, float x, float y, float s) {
        float railY = y + s * .08f, footY = y + s * .78f;
        paint.setColor(0x77000000);
        rect.set(x + s * .10f, footY + s * .03f, x + s * .91f, footY + s * .10f);
        canvas.drawOval(rect, paint);
        for (int i = 0; i < 4; i++) {
            float tubeX = x + s * (.24f + .16f * i);
            float tubeTop = y + s * (.38f - i * .065f);
            float tubeBottom = footY - s * .035f;
            paint.setShader(new LinearGradient(tubeX, 0, tubeX + s * .105f, 0,
                    new int[]{0xFF755039, 0xFFE9BE7F, 0xFFA86A40, 0xFF5A3B2C},
                    null, Shader.TileMode.CLAMP));
            rect.set(tubeX, tubeTop, tubeX + s * .105f, tubeBottom);
            canvas.drawRoundRect(rect, s * .025f, s * .025f, paint);
            paint.setShader(null);
            paint.setColor(0xB3F9D49D);
            paint.setStrokeWidth(s * .008f);
            canvas.drawLine(tubeX + s * .02f, tubeTop + s * .023f,
                    tubeX + s * .02f, tubeBottom - s * .02f, paint);
            paint.setColor(0xFF4D362B);
            canvas.drawOval(tubeX + s * .012f, tubeTop - s * .004f,
                    tubeX + s * .094f, tubeTop + s * .027f, paint);
            paint.setColor(0xFFBE8B58);
            canvas.drawOval(tubeX + s * .024f, tubeTop + s * .005f,
                    tubeX + s * .08f, tubeTop + s * .022f, paint);
        }

        // Uprights and ropes sit in front of the suspended tubes.
        paint.setColor(0xFFDEAE70);
        paint.setStrokeWidth(s * .014f);
        canvas.drawLine(x + s * .15f, railY, x + s * .15f, footY, paint);
        canvas.drawLine(x + s * .84f, railY, x + s * .84f, footY, paint);
        for (int i = 0; i < 4; i++) {
            float cord = x + s * (.29f + i * .16f);
            canvas.drawLine(cord, railY, cord, y + s * (.37f - i * .065f), paint);
        }
        paint.setColor(0xFFE3BE84);
        canvas.drawRoundRect(x + s * .12f, railY - s * .027f,
                x + s * .87f, railY + s * .025f, s * .015f, s * .015f, paint);
        paint.setColor(0xFFBE8B58);
        canvas.drawRoundRect(x + s * .11f, footY - s * .015f,
                x + s * .88f, footY + s * .06f, s * .015f, s * .015f, paint);
        paint.setColor(0xFFF1CB8D);
        canvas.drawRoundRect(x + s * .13f, footY - s * .015f,
                x + s * .86f, footY + s * .007f, s * .008f, s * .008f, paint);
        paint.setColor(0xFFA97147);
        paint.setStrokeWidth(s * .018f);
        canvas.drawLine(x + s * .15f, y + s * .47f, x + s * .84f, y + s * .47f, paint);
        paint.setColor(0xFFE8C08B);
        for (int i = 0; i < 4; i++) {
            float knot = x + s * (.29f + i * .16f);
            canvas.drawCircle(knot, y + s * .47f, s * .014f, paint);
        }
    }
}
