package ch.lumen.tvlauncher;

import android.util.DisplayMetrics;

/** Alle Maße relativ zur Bildschirmgröße -> gleiches Aussehen auf 720p, 1080p und 4K. */
final class Dims {
    static final int COLS = 6;

    final int sw, sh, side, gap, gapV, tileW, tileH, renderW, renderH;
    final float radius, renderR;

    Dims(DisplayMetrics dm) {
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);
        side = Math.round(sw * 0.06f);
        gap = Math.round(sw * 0.02f);
        tileW = (sw - 2 * side - (COLS - 1) * gap) / COLS;
        tileH = Math.round(tileW * 9f / 16f);
        gapV = Math.round(tileH * 0.62f);
        radius = tileH * 0.1f;
        // Kacheln etwas größer rendern, damit sie beim Fokus-Zoom scharf bleiben
        renderW = Math.round(tileW * 1.2f);
        renderH = Math.round(tileH * 1.2f);
        renderR = radius * 1.2f;
    }
}
