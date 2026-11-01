package ch.lumen.tvlauncher;

import android.content.Intent;
import android.graphics.Bitmap;

/** Eine Kachel: App, Einstellungen, „Alle Apps“ oder ein Hintergrundbild. */
final class AppEntry {
    static final String SETTINGS_KEY = "__settings";
    static final String ALL_KEY = "__all";

    final String key;
    final String originalLabel;   // Name, wie ihn die App liefert
    String label;                 // angezeigter Name (evtl. vom Benutzer umbenannt)
    final Intent intent;
    final boolean tv;      // hat einen TV-Launcher-Eintrag
    Bitmap tile;

    AppEntry(String key, String label, Intent intent, boolean tv) {
        this.key = key;
        this.originalLabel = label;
        this.label = label;
        this.intent = intent;
        this.tv = tv;
    }

    boolean isApp() {
        return intent != null && !SETTINGS_KEY.equals(key);
    }
}
