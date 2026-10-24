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

## Benachrichtigungen aus Home Assistant
Oben links erscheinen Nachrichten aus Home Assistant als Glaskarten (max. 4, nach 6 Stunden automatisch weg).
Pfeil nach oben aus der Favoritenleiste wählt eine Karte, OK blendet sie aus, lange OK → „Alle löschen“.

**Einrichten (einmalig):**
1. Die IP des TVs steht in der App-Liste unter „Home Assistant“ (dort gibt es auch „Test senden“).
2. Home Assistant → Einstellungen → Geräte & Dienste → Integration hinzufügen → **Notifications for Android TV / Fire TV** → IP eingeben.
3. In Automationen die neue Aktion `notify.<name>` nutzen, z. B.:
   ```yaml
   action: notify.lumen_tv
   data:
     title: Haustür
     message: Es hat geklingelt
     data:
       image:
         url: http://homeassistant.local:8123/api/camera_proxy/camera.haustuer
   ```
   Das Bild (z. B. Kamera-Schnappschuss) wird in der Karte angezeigt.

Der Launcher empfängt auf Port 7676. Alternativ geht auch JSON per POST: `{"title": "...", "message": "..."}`.

**Auch über anderen Apps einblenden** (z. B. während Netflix): Der Launcher braucht dafür die Berechtigung „Über anderen Apps einblenden“. Auf den meisten TVs gibt es dafür kein Menü, daher einmalig per ADB:
```
adb shell appops set ch.lumen.tvlauncher SYSTEM_ALERT_WINDOW allow
```
Danach erscheint jede neue Nachricht 8 Sekunden lang oben links, egal welche App läuft.

Der Empfänger startet automatisch beim Hochfahren des TVs und läuft als stiller Hintergrunddienst – auch wenn der TV direkt ins Live-TV startet. Von Hand (per ADB) starten: `am broadcast -n ch.lumen.tvlauncher/.BootReceiver`

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
