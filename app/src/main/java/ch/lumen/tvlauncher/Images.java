package ch.lumen.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Rendert Kacheln einmalig vorab, damit beim Scrollen nur noch Bitmaps gezeichnet werden. */
final class Images {
    private static final int GRAPHITE = 0xFF3A3A3C;

    private Images() {}

    /** Füllt e.tile und e.backdrop. */
    static void render(AppEntry e, Drawable banner, Drawable icon, int w, int h, float radius) {
        Bitmap raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(raw);
        if (banner != null) {
            drawBanner(c, banner, w, h);
        } else if (icon != null) {
            drawIconTile(c, icon, w, h);
        } else {
            c.drawColor(GRAPHITE);
        }
        e.backdrop = blur(raw);
        e.tile = round(raw, radius);
        raw.recycle();
    }

    private static void drawBanner(Canvas c, Drawable d, int w, int h) {
        c.drawColor(0xFF1C1C1E);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, w, h);
        } else {
            float s = Math.max(w / (float) iw, h / (float) ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            int l = (w - dw) / 2, t = (h - dh) / 2;
            d.setBounds(l, t, l + dw, t + dh);
        }
        d.draw(c);
    }

    /** Apps ohne TV-Banner: Icon mittig auf einem Verlauf in der Icon-Farbe (wie bei Apple TV). */
    private static void drawIconTile(Canvas c, Drawable icon, int w, int h) {
        int base = dominantColor(icon);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, shade(base, 1.15f), shade(base, 0.72f), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int size = Math.round(h * 0.6f);
        int l = (w - size) / 2, t = (h - size) / 2;
        icon.setBounds(l, t, l + size, t + size);
        icon.draw(c);
    }

    private static Bitmap round(Bitmap src, float r) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), r, r, p);
        return out;
    }

    /** Günstige "Unschärfe": stark verkleinern, dann gefiltert wieder vergrößern. */
    private static Bitmap blur(Bitmap src) {
        Bitmap tiny = Bitmap.createScaledBitmap(src, 16, 9, true);
        Bitmap mid = Bitmap.createScaledBitmap(tiny, 48, 27, true);
        Bitmap out = Bitmap.createScaledBitmap(mid, 160, 90, true);
        tiny.recycle();
        mid.recycle();
        return out;
    }

    private static int dominantColor(Drawable d) {
        Bitmap b = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 12, 12);
        d.draw(new Canvas(b));
        long r = 0, g = 0, bl = 0;
        int n = 0;
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                int px = b.getPixel(x, y);
                if (Color.alpha(px) < 200) continue;
                r += Color.red(px);
                g += Color.green(px);
                bl += Color.blue(px);
                n++;
            }
        }
        b.recycle();
        if (n == 0) return GRAPHITE;
        float[] hsv = new float[3];
        Color.colorToHSV(Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n)), hsv);
        if (hsv[1] < 0.15f) return GRAPHITE;       // Grau/Weiß -> edles Graphit
        hsv[1] = Math.min(hsv[1], 0.75f);
        hsv[2] = Math.max(0.35f, Math.min(hsv[2], 0.7f));
        return Color.HSVToColor(hsv);
    }

    private static int shade(int color, float f) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * f));
        return Color.HSVToColor(hsv);
    }

    static ViewOutlineProvider rounded(final float r) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        };
    }
}
