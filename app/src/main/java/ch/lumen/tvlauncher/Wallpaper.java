package ch.lumen.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Hintergrund: eingebaute Farbverläufe oder ein eigenes Bild (wird verkleinert lokal gespeichert). */
final class Wallpaper {
    static final String[] NAMES = {"Mitternacht", "Polarlicht", "Dämmerung", "Glut", "Wald", "Graphit", "Schwarz"};

    // Grundfarbe, Farbfleck 1, Farbfleck 2
    private static final int[][] COLORS = {
            {0xFF04060D, 0xFF1A2A5E, 0xFF35204F},
            {0xFF02090B, 0xFF0E534F, 0xFF262C70},
            {0xFF07050F, 0xFF4B3470, 0xFF7A4A3C},
            {0xFF0B0404, 0xFF6E2410, 0xFF420D28},
            {0xFF030705, 0xFF1C4528, 0xFF0D2830},
            {0xFF101012, 0xFF2A2A2E, 0xFF17171A},
            {0xFF000000, 0xFF000000, 0xFF000000},
    };
    // x1, y1, Radius1, x2, y2, Radius2 (relativ zur Breite)
    private static final float[][] SPOTS = {
            {0.15f, 0.05f, 0.85f, 0.95f, 0.95f, 0.75f},
            {0.10f, 0.15f, 0.80f, 0.90f, 0.65f, 0.75f},
            {0.75f, 0.05f, 0.80f, 0.10f, 1.00f, 0.70f},
            {0.85f, 0.10f, 0.80f, 0.05f, 0.95f, 0.70f},
            {0.30f, 0.00f, 0.85f, 0.95f, 0.95f, 0.70f},
            {0.50f, 0.00f, 0.90f, 0.50f, 1.10f, 0.80f},
            {0.50f, 0.50f, 0.10f, 0.50f, 0.50f, 0.10f},
    };

    private static final String K_WP = "wallpaper";
    private static final String CUSTOM = "custom";

    private Wallpaper() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "wallpaper.jpg");
    }

    static Bitmap preset(int i, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int[] col = COLORS[i];
        float[] s = SPOTS[i];
        c.drawColor(col[0]);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setDither(true);
        for (int k = 0; k < 2; k++) {
            int color = col[k + 1];
            p.setShader(new RadialGradient(s[k * 3] * w, s[k * 3 + 1] * h, Math.max(1f, s[k * 3 + 2] * w),
                    color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        return b;
    }

    /** Hintergrund-Thread. */
    static Bitmap load(Context c, int w, int h) {
        String v = sp(c).getString(K_WP, "0");
        if (CUSTOM.equals(v)) {
            Bitmap b = BitmapFactory.decodeFile(file(c).getAbsolutePath());
            if (b != null) return b;
            v = "0";
        }
        int i;
        try {
            i = Math.max(0, Math.min(NAMES.length - 1, Integer.parseInt(v)));
        } catch (NumberFormatException e) {
            i = 0;
        }
        return preset(i, w / 2, h / 2);   // Verläufe dürfen weich hochskaliert werden
    }

    static void setPreset(Context c, int i) {
        sp(c).edit().putString(K_WP, String.valueOf(i)).apply();
    }

    /** Hintergrund-Thread. Bild laden, drehen, auf Bildschirmgröße zuschneiden und speichern. */
    static boolean setCustom(Context c, Uri uri, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap src;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                src = BitmapFactory.decodeStream(in, null, o2);
            }
            if (src == null) return false;

            int rot = rotation(c, uri);
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
                if (r != src) src.recycle();
                src = r;
            }
            Bitmap out = Images.centerCrop(src, w, h);
            src.recycle();
            try (FileOutputStream fo = new FileOutputStream(file(c))) {
                out.compress(Bitmap.CompressFormat.JPEG, 92, fo);
            }
            out.recycle();
            sp(c).edit().putString(K_WP, CUSTOM).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int rotation(Context c, Uri uri) {
        if (Build.VERSION.SDK_INT < 24) return 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }
}
