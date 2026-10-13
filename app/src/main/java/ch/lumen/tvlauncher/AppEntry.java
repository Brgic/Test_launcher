package ch.lumen.tvlauncher;

import android.content.Intent;
import android.graphics.Bitmap;

/** Eine Kachel: App (oder die Einstellungen) mit vorgerenderten Bitmaps. */
final class AppEntry {
    static final String SETTINGS_KEY = "__settings";

    final String key;      // Paketname bzw. SETTINGS_KEY
    final String label;
    final Intent intent;
    Bitmap tile;           // abgerundete 16:9-Kachel
    Bitmap backdrop;       // stark verkleinert -> wirkt hochskaliert unscharf

    AppEntry(String key, String label, Intent intent) {
        this.key = key;
        this.label = label;
        this.intent = intent;
    }

    boolean isSettings() {
        return SETTINGS_KEY.equals(key);
    }
}
