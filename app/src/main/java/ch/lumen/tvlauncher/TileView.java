package ch.lumen.tvlauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.DecelerateInterpolator;

/** Kachel: zoomt mit Schatten bei Fokus, Name darunter. */
@SuppressLint("ViewConstructor")
final class TileView extends FrameLayout {

    interface Host {
        void onTileFocused(TileView t);
        void onTileClick(TileView t);
        void onTileLongClick(TileView t);
    }

    private static final float FOCUS_SCALE = 1.1f;
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    final AppEntry entry;
    int index;
    private final ImageView card;
    private final TextView label;
    private final int tileH;
    private final boolean labelAlways;
    private final Host host;
    private ObjectAnimator wiggle;

    TileView(Context c, AppEntry e, Dims d, boolean labelAlways, Host host) {
        super(c);
        entry = e;
        this.host = host;
        this.labelAlways = labelAlways;
        tileH = d.tileH;
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);

        card = new ImageView(c);
        card.setScaleType(ImageView.ScaleType.FIT_XY);
        card.setImageBitmap(e.tile);
        card.setOutlineProvider(Images.rounded(d.radius));
        addView(card, new LayoutParams(d.tileW, d.tileH));

        label = new TextView(c);
        label.setText(e.label);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, tileH * 0.15f);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setShadowLayer(tileH * 0.05f, 0, tileH * 0.015f, 0x99000000);
        label.setAlpha(labelAlways ? 0.6f : 0f);
        label.setTranslationY(tileH * 1.06f);
        LayoutParams lp = new LayoutParams(d.tileW, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        addView(label, lp);

        setOnClickListener(v -> host.onTileClick(this));
        setOnLongClickListener(v -> {
            host.onTileLongClick(this);
            return true;
        });
    }

    void setBitmap(Bitmap b) {
        entry.tile = b;
        card.setImageBitmap(b);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        float s = focused ? FOCUS_SCALE : 1f;
        card.animate()
                .scaleX(s).scaleY(s)
                .translationZ(focused ? tileH * 0.12f : 0f)
                .setDuration(focused ? 200 : 150)
                .setInterpolator(EASE)
                .start();
        label.animate()
                .alpha(focused ? 1f : (labelAlways ? 0.6f : 0f))
                .translationY(focused ? tileH * 1.11f : tileH * 1.06f)
                .setDuration(focused ? 200 : 120)
                .setInterpolator(EASE)
                .start();
        if (focused) host.onTileFocused(this);
    }

    void pulse() {
        card.animate().scaleX(1.03f).scaleY(1.03f).setDuration(90).withEndAction(() -> {
            float s = isFocused() ? FOCUS_SCALE : 1f;
            card.animate().scaleX(s).scaleY(s).setDuration(160).start();
        }).start();
    }

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
