package ch.lumen.tvlauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.DecelerateInterpolator;

/**
 * Kachel: zoomt mit Schatten bei Fokus.
 * Mit Bild: Name erscheint darunter. Ohne Bild (Stil „Nur Text“): Name auf Glas,
 * bei Fokus weiss mit dunkler Schrift.
 */
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
    private final View card;            // ImageView (Bild) oder TextView (nur Text)
    private final boolean textOnly;
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

        textOnly = e.tile == null;
        if (textOnly) {
            card = buildTextCard(c, e, d);
        } else {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            img.setImageBitmap(e.tile);
            img.setOutlineProvider(Images.rounded(d.radius));
            card = img;
        }
        addView(card, new LayoutParams(d.tileW, d.tileH));

        label = new TextView(c);
        label.setText(e.label);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, tileH * 0.15f);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setShadowLayer(tileH * 0.05f, 0, tileH * 0.015f, 0x99000000);
        label.setAlpha(labelAlways && !textOnly ? 0.6f : 0f);
        if (textOnly) label.setVisibility(GONE);   // Name steht schon auf der Kachel
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

    private static TextView buildTextCard(Context c, AppEntry e, Dims d) {
        TextView t = new TextView(c);
        t.setText(e.label);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, d.tileH * (e.label.length() > 14 ? 0.15f : 0.18f));
        t.setLineSpacing(0, 1.05f);
        int pad = Math.round(d.tileH * 0.1f);
        t.setPadding(pad, 0, pad, 0);

        GradientDrawable off = new GradientDrawable();
        off.setCornerRadius(d.radius);
        off.setColor(0x2EFFFFFF);
        off.setStroke(Math.max(1, Math.round(d.tileH * 0.008f)), 0x26FFFFFF);
        GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(d.radius);
        on.setColor(0xF2FFFFFF);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        t.setBackground(bg);
        t.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xFF111111, 0xFFFFFFFF}));
        t.setShadowLayer(d.tileH * 0.04f, 0, 0, 0x00000000);
        t.setDuplicateParentStateEnabled(true);   // Fokus der Kachel färbt Hintergrund und Schrift
        return t;
    }

    void setBitmap(Bitmap b) {
        entry.tile = b;
        if (card instanceof ImageView) ((ImageView) card).setImageBitmap(b);
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
        if (!textOnly) label.animate()
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
