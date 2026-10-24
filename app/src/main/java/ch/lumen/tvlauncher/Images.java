package ch.lumen.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Rendert Kacheln einmalig vorab, damit beim Scrollen nur fertige Bitmaps gezeichnet werden. */
final class Images {
    static final int GRAPHITE = 0xFF2C2C2E;

    private Images() {}

    /** App-Kachel: TV-Banner, sonst Icon auf einem Verlauf in der Icon-Farbe. */
    static void render(AppEntry e, Drawable banner, Drawable icon, int w, int h, float r) {
        Bitmap raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(raw);
        if (banner != null) drawBanner(c, banner, w, h);
        else if (icon != null) drawIconTile(c, icon, w, h);
        else c.drawColor(GRAPHITE);
        e.tile = round(raw, r);
        raw.recycle();
    }

    /** Durchscheinende „Glas“-Kachel mit weißem Symbol (für „Alle Apps“). */
    static void renderGlass(AppEntry e, Drawable icon, int w, int h, float r) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x38FFFFFF);
        RectF rect = new RectF(0, 0, w, h);
        c.drawRoundRect(rect, r, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, h * 0.008f));
        p.setColor(0x33FFFFFF);
        float in = p.getStrokeWidth() / 2f;
        c.drawRoundRect(new RectF(in, in, w - in, h - in), r, r, p);
        int size = Math.round(h * 0.34f);
        int l = (w - size) / 2, t = (h - size) / 2;
        icon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        icon.setBounds(l, t, l + size, t + size);
        icon.draw(c);
        e.tile = b;
    }

    /** Dezenter Stil: Glaskachel mit leicht entsättigtem App-Icon. */
    static void renderCalm(AppEntry e, Drawable icon, int w, int h, float r) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x2EFFFFFF);
        c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, h * 0.008f));
        p.setColor(0x26FFFFFF);
        float in = p.getStrokeWidth() / 2f;
        c.drawRoundRect(new RectF(in, in, w - in, h - in), r, r, p);
        if (icon != null) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0.45f);
            icon.setColorFilter(new ColorMatrixColorFilter(cm));
            int size = Math.round(h * 0.5f);
            int l = (w - size) / 2, t = (h - size) / 2;
            icon.setBounds(l, t, l + size, t + size);
            icon.draw(c);
        }
        e.tile = b;
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

    private static void drawIconTile(Canvas c, Drawable icon, int w, int h) {
        int base = dominantColor(icon);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, shade(base, 1.12f), shade(base, 0.78f), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int size = Math.round(h * 0.56f);
        int l = (w - size) / 2, t = (h - size) / 2;
        icon.setBounds(l, t, l + size, t + size);
        icon.draw(c);
    }

    static Bitmap round(Bitmap src, float r) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), r, r, p);
        return out;
    }

    /** Bild auf w×h zuschneiden (wie centerCrop). */
    static Bitmap centerCrop(Bitmap src, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    static Bitmap roundCrop(Bitmap src, int w, int h, float r) {
        Bitmap c = centerCrop(src, w, h);
        Bitmap out = round(c, r);
        c.recycle();
        return out;
    }

    /** Günstige Unschärfe: stark verkleinern; die ImageView skaliert gefiltert wieder hoch. */
    static Bitmap blurSmall(Bitmap src) {
        Bitmap a = Bitmap.createScaledBitmap(src, 32, 18, true);
        Bitmap b = Bitmap.createScaledBitmap(a, 96, 54, true);
        a.recycle();
        return b;
    }

    static Bitmap solid(int w, int h, float r, int color) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        new Canvas(b).drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        return b;
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
        if (hsv[1] < 0.15f) return GRAPHITE;
        hsv[1] = Math.min(hsv[1], 0.7f);
        hsv[2] = Math.max(0.32f, Math.min(hsv[2], 0.62f));
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
