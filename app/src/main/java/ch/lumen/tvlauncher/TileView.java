package ch.lumen.tvlauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** App-Kachel: vergrößert sich mit Schatten bei Fokus, Name erscheint darunter. */
@SuppressLint("ViewConstructor")
final class TileView extends FrameLayout {

    interface Host {
        void onTileFocused(TileView t);
        void onTileClick(TileView t);
        void onTileLongClick(TileView t);
    }

    private static final float FOCUS_SCALE = 1.12f;
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    final AppEntry entry;
    int index;
    private final ImageView card;
    private final TextView label;
    private final int tileH;
    private ObjectAnimator wiggle;

    TileView(Context c, AppEntry e, int w, int h, float radius, final Host host) {
        super(c);
        entry = e;
        tileH = h;
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);

        card = new ImageView(c);
        card.setScaleType(ImageView.ScaleType.FIT_XY);
        card.setImageBitmap(e.tile);
        card.setOutlineProvider(Images.rounded(radius));
        addView(card, new LayoutParams(w, h));

        label = new TextView(c);
        label.setText(e.label);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.16f);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setShadowLayer(h * 0.05f, 0, h * 0.02f, 0xCC000000);
        label.setAlpha(0f);
        label.setTranslationY(h * 1.06f);
        LayoutParams lp = new LayoutParams(w, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        addView(label, lp);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onTileClick(TileView.this);
            }
        });
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                host.onTileLongClick(TileView.this);
                return true;
            }
        });
        this.host = host;
    }

    private final Host host;

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        float s = focused ? FOCUS_SCALE : 1f;
        card.animate()
                .scaleX(s).scaleY(s)
                .translationZ(focused ? tileH * 0.14f : 0f)
                .setDuration(focused ? 200 : 160)
                .setInterpolator(EASE)
                .start();
        label.animate()
                .alpha(focused ? 1f : 0f)
                .translationY(focused ? tileH * 1.12f : tileH * 1.06f)
                .setDuration(focused ? 220 : 120)
                .setInterpolator(EASE)
                .start();
        if (focused) host.onTileFocused(this);
    }

    /** Kurzes "Eindrücken" beim Öffnen. */
    void pulse() {
        card.animate().scaleX(1.04f).scaleY(1.04f).setDuration(90).withEndAction(new Runnable() {
            @Override
            public void run() {
                float s = isFocused() ? FOCUS_SCALE : 1f;
                card.animate().scaleX(s).scaleY(s).setDuration(160).start();
            }
        }).start();
    }

    /** Wackeln im Verschiebe-Modus (wie auf dem Apple TV). */
    void startWiggle() {
        stopWiggle();
        wiggle = ObjectAnimator.ofFloat(card, "rotation", -1.4f, 1.4f);
        wiggle.setDuration(130);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.start();
    }

    void stopWiggle() {
        if (wiggle != null) {
            wiggle.cancel();
            wiggle = null;
        }
        card.setRotation(0f);
    }
}
