package ch.lumen.tvlauncher;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

/** Vertikale Liste, die bei Fokuswechsel weich statt ruckartig scrollt. */
public final class TvScrollView extends ScrollView {
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.8f);
    private ObjectAnimator anim;

    public TvScrollView(Context c) {
        super(c);
        init();
    }

    public TvScrollView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
        setFocusable(false);
    }

    /** Scrollt so, dass v bei anchor (0..1) der Höhe steht. */
    void smoothFocus(final View v, final float anchor) {
        if (getHeight() == 0 || isLayoutRequested()) {
            post(() -> smoothFocus(v, anchor));
            return;
        }
        int y = 0;
        View cur = v;
        while (cur != null && cur != this) {
            y += cur.getTop();
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        View child = getChildAt(0);
        int max = child == null ? 0 : Math.max(0, child.getHeight() - getHeight());
        int target = Math.max(0, Math.min(max, y - Math.round(getHeight() * anchor)));
        if (anim != null) anim.cancel();
        if (target == getScrollY()) return;
        anim = ObjectAnimator.ofInt(this, "scrollY", target);
        anim.setDuration(300);
        anim.setInterpolator(EASE);
        anim.start();
    }

    void jumpTop() {
        if (anim != null) anim.cancel();
        scrollTo(0, 0);
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
