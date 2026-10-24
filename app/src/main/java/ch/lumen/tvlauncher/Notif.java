package ch.lumen.tvlauncher;

import android.graphics.Bitmap;

/** Eine empfangene Benachrichtigung. */
final class Notif {
    private static int nextId = 1;

    final int id;
    final String title;
    final String message;
    final Bitmap image;   // optional, z. B. Kamera-Schnappschuss
    final long time;

    Notif(String title, String message, Bitmap image) {
        synchronized (Notif.class) {
            id = nextId++;
        }
        this.title = title == null ? "" : title.trim();
        this.message = message == null ? "" : message.trim();
        this.image = image;
        this.time = System.currentTimeMillis();
    }
}
