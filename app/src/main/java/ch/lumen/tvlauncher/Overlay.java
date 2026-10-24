package ch.lumen.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Vollbild-Ebene mit unscharfem, abgedunkeltem Hintergrund (Milchglas-Effekt). */
abstract class Overlay extends FrameLayout {
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    protected final Dims d;
    private final ImageView bg;
    private boolean open;

    Overlay(Context c, Dims d) {
        super(c);
        this.d = d;
        setVisibility(GONE);
        setClickable(true);
        bg = new ImageView(c);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(bg, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        View dim = new View(c);
        dim.setBackgroundColor(0xB0000000);
        addView(dim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    void setBackdrop(Bitmap tiny) {
        bg.setImageBitmap(tiny);
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        if (open) return;
        open = true;
        animate().cancel();
        setVisibility(VISIBLE);
        setAlpha(0f);
        setScaleX(1.04f);
        setScaleY(1.04f);
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(230).setInterpolator(EASE).withLayer().start();
        onOpened();
    }

    void close(boolean animated) {
        if (!open) return;
        open = false;
        animate().cancel();
        if (!animated) {
            setVisibility(GONE);
            return;
        }
        animate().alpha(0f).setDuration(150).withLayer().withEndAction(() -> {
            if (!open) setVisibility(GONE);
        }).start();
    }

    protected abstract void onOpened();
}
