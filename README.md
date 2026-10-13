# Lumen TV – Android-TV-Launcher im Apple-TV-Look

Schlank (keine Bibliotheken, nur Android-Framework, APK ca. 50 KB) und auf flüssige Bewegung getrimmt.

## Was er kann
- **Top Shelf** wie beim Apple TV: großes Banner und Name der fokussierten App, dahinter ein weich verschwommener Farbhintergrund, der beim Wechsel überblendet
- **Kachelraster** mit 5 Spalten, abgerundete 16:9-Kacheln; bei Fokus Zoom mit Schatten, der App-Name erscheint darunter
- Beim Runterscrollen gleitet der Top Shelf mit leichtem Parallax-Effekt weg
- Uhr oben rechts
- TV-Apps zeigen ihr offizielles Banner; Handy-Apps bekommen eine Kachel mit Icon auf einem Verlauf in der Icon-Farbe
- **Lange OK drücken** (oder Menü-Taste) auf einer Kachel: Verschieben (Kachel wackelt, mit Pfeilen bewegen, OK beendet), Ausblenden, App-Info, Deinstallieren
- Auf der Kachel **Einstellungen**: lange drücken → ausgeblendete Apps wieder einblenden
- Neu installierte oder entfernte Apps erscheinen bzw. verschwinden automatisch
- Home- oder Zurück-Taste springt an den Anfang

## Warum er flüssig läuft
- Alle Kacheln werden einmal im Hintergrund vorgerendert (inkl. Rundung) – beim Scrollen werden nur fertige Bilder gezeichnet
- Animationen laufen über die GPU (Scale, Alpha, Translation), kein Neu-Layout beim Fokuswechsel
- Unschärfe ohne teure Filter: Bild wird winzig verkleinert und gefiltert hochskaliert
- Hintergrundwechsel wird beim schnellen Durchklicken entprellt

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
- Spaltenzahl: `COLS` in `MainActivity.java`
- Zoomstärke: `FOCUS_SCALE` in `TileView.java`
- Farben des Verlaufs: `res/drawable/scrim.xml`
