package ch.lumen.tvlauncher;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.ScrollView;

/**
 * ScrollView, die bei Fokuswechseln NICHT selbst springt.
 * Das Scrollen übernimmt die Activity mit einer eigenen, weichen Animation.
 */
public final class TvScrollView extends ScrollView {

    interface Listener {
        void onScroll(int y);
    }

    private Listener listener;

    public TvScrollView(Context context) {
        super(context);
    }

    public TvScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (listener != null) listener.onScroll(y);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }

    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        // Steuerkreuz normal an die Fokussuche weitergeben
        return false;
    }
}
