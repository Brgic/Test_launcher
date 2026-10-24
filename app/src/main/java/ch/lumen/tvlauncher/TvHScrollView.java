package ch.lumen.tvlauncher;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;

/** Horizontale Leiste mit weichem Scrollen; Kacheln bleiben immer im Rand-Abstand sichtbar. */
public final class TvHScrollView extends HorizontalScrollView {
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.8f);
    private ObjectAnimator anim;

    public TvHScrollView(Context c) {
        super(c);
    }

    public TvHScrollView(Context c, AttributeSet a) {
        super(c, a);
    }

    void ensureVisible(final View v, final int pad) {
        if (getWidth() == 0 || isLayoutRequested()) {
            post(() -> ensureVisible(v, pad));
            return;
        }
        int left = v.getLeft(), right = v.getRight();
        int s = getScrollX(), w = getWidth();
        int target = s;
        if (left - s < pad) target = left - pad;
        else if (right - s > w - pad) target = right - w + pad;
        View child = getChildAt(0);
        int max = child == null ? 0 : Math.max(0, child.getWidth() - w);
        target = Math.max(0, Math.min(max, target));
        if (anim != null) anim.cancel();
        if (target == s) return;
        anim = ObjectAnimator.ofInt(this, "scrollX", target);
        anim.setDuration(280);
        anim.setInterpolator(EASE);
        anim.start();
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }

    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        return false;
    }
}
