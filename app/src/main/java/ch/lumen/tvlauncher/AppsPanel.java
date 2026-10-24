package ch.lumen.tvlauncher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Separate Liste mit allen Apps. */
@SuppressLint("ViewConstructor")
final class AppsPanel extends Overlay implements TileView.Host {

    interface Callback {
        void onAppOpen(TileView t);
        void onAppMenu(AppEntry e);
        void onWallpaperRequest();
        void onToggleTileStyle();
    }

    private final Callback cb;
    private final TvScrollView scroll;
    private final LinearLayout grid;
    private final TextView stylePill;
    private final Map<String, TileView> tiles = new HashMap<>();
    private String lastKey;
    private TileView first;

    AppsPanel(Context c, Dims d, Callback cb) {
        super(c, d);
        this.cb = cb;

        scroll = new TvScrollView(c);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(0, Math.round(d.sh * 0.08f), 0, Math.round(d.sh * 0.4f));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setPadding(d.side, 0, d.side, 0);
        header.addView(UiKit.text(c, c.getString(R.string.apps), d.sh * 0.05f, 0xFFFFFFFF, true));
        View spacer = new View(c);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        stylePill = UiKit.pill(c, "", d);
        stylePill.setOnClickListener(v -> cb.onToggleTileStyle());
        header.addView(stylePill);

        TextView wpPill = UiKit.pill(c, c.getString(R.string.wallpaper), d);
        wpPill.setOnClickListener(v -> cb.onWallpaperRequest());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.leftMargin = Math.round(d.sh * 0.02f);
        header.addView(wpPill, wlp);
        content.addView(header);

        grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Math.round(d.sh * 0.06f);
        content.addView(grid, glp);
    }

    void setTileStyleLabel(boolean calm) {
        stylePill.setText(calm ? "Kacheln: Dezent" : "Kacheln: Farbig");
    }

    void setApps(List<AppEntry> apps) {
        boolean hadFocus = isOpen() && findFocus() instanceof TileView;
        grid.removeAllViews();
        tiles.clear();
        first = null;
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % Dims.COLS == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setPadding(d.side, 0, d.side, 0);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, d.tileH);
                if (i > 0) rp.topMargin = d.gapV;
                grid.addView(row, rp);
            }
            AppEntry e = apps.get(i);
            TileView t = new TileView(getContext(), e, d, true, this);
            t.index = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d.tileW, d.tileH);
            if (i % Dims.COLS != 0) lp.leftMargin = d.gap;
            row.addView(t, lp);
            tiles.put(e.key, t);
            if (first == null) first = t;
        }
        if (hadFocus) focusLast();
    }

    @Override
    protected void onOpened() {
        scroll.jumpTop();
        lastKey = null;
        if (first != null) first.requestFocus();
    }

    void focusLast() {
        TileView t = lastKey != null ? tiles.get(lastKey) : null;
        if (t == null) t = first;
        if (t != null) t.requestFocus();
    }

    @Override
    public void onTileFocused(TileView t) {
        lastKey = t.entry.key;
        scroll.smoothFocus(t, 0.34f);
    }

    @Override
    public void onTileClick(TileView t) {
        cb.onAppOpen(t);
    }

    @Override
    public void onTileLongClick(TileView t) {
        cb.onAppMenu(t.entry);
    }
}
