# Lumen TV – Android-TV-Launcher im Apple-TV-Look

Schlank (keine Bibliotheken, nur Android-Framework, APK ca. 50 KB) und auf flüssige Bewegung getrimmt.

## Was er kann
- **Ruhiger Startbildschirm:** dein Hintergrund, Uhr mit Datum oben rechts und unten eine einzige Leiste mit deinen Favoriten
- **Alle Apps** als letzte Kachel der Leiste (oder Pfeil nach unten): separate Liste mit Milchglas-Effekt
- **Kachelstil:** „Dezent“ (Glaskacheln, gedämpfte Icons; Standard) oder „Farbig“ (offizielle TV-Banner). Umschalten oben in der App-Liste
- **Hintergrund:** 7 Farbverlauf-Vorlagen oder eigene Bilder vom Gerät. Öffnen über „Hintergrund“ in der App-Liste oder lange OK auf „Alle Apps“
- **Lange OK** (oder Menü-Taste) auf einer Favoriten-Kachel: Verschieben, Aus Favoriten entfernen, App-Info, Deinstallieren
- **Lange OK** in der App-Liste: Zu Favoriten hinzufügen/entfernen, App-Info, Deinstallieren
- Neu installierte oder entfernte Apps erscheinen bzw. verschwinden automatisch

## Eigenes Hintergrundbild auf den TV bringen
Bild z. B. mit „Send Files to TV“ oder per USB-Stick auf den Fernseher kopieren. Danach in „Hintergrund“ → „Bilder vom Gerät anzeigen“ (einmalig Zugriff erlauben) oder „Datei auswählen…“.
Das Bild wird auf Bildschirmgröße zugeschnitten und im Launcher gespeichert. Das Original kannst du danach löschen.

## Warum er flüssig läuft
- Alle Kacheln werden einmal im Hintergrund vorgerendert. Beim Scrollen werden nur fertige Bilder gezeichnet
- Animationen laufen über die GPU (Scale, Alpha, Translation), kein Neu-Layout beim Fokuswechsel
- Unschärfe ohne teure Filter: Bild wird winzig verkleinert und gefiltert hochskaliert

## APK bauen

### Variante A: GitHub (ohne eigenen PC-Setup)
1. Neues (gerne privates) Repository auf GitHub anlegen und den Inhalt dieses Ordners hochladen.
2. Unter **Actions** startet „APK bauen“ automatisch (sonst „Run workflow“ klicken).
3. Nach ca. 3 Minuten unten beim Lauf **LumenTV-APK** herunterladen und entpacken.

### Variante B: Android Studio
Ordner öffnen → *Build → Build App Bundle(s)/APK(s) → Build APK(s)*.

## Auf den TV bringen
- Per ADB: `adb connect <TV-IP>` und `adb install app-release.apk`
- Oder ohne PC: APK z. B. mit „Send Files to TV“ übertragen und mit einem Dateimanager installieren

## Als Standard-Launcher setzen
- **Klassisches Android TV / viele Boxen:** Home-Taste drücken → „Lumen TV“ wählen → „Immer“.
- **Google TV (z. B. Chromecast, neuere Sony/TCL):** Google erlaubt dort keine Auswahl. Möglichkeiten:
  - App „Button Mapper“: Home-Taste auf Lumen TV legen (ohne Systemeingriff).
  - Oder per ADB den Google-Launcher deaktivieren (auf eigene Verantwortung):
    `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx`
    Rückgängig: `adb shell pm enable com.google.android.apps.tv.launcherx`

## Anpassen
- Spaltenzahl der App-Liste: `COLS` in `Dims.java`
- Zoomstärke: `FOCUS_SCALE` in `TileView.java`
- Hintergrund-Vorlagen: `Wallpaper.java`
- Entsättigung im dezenten Stil: `setSaturation` in `Images.java`
