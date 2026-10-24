package ch.lumen.tvlauncher;

import android.app.Application;

/** Startet den Benachrichtigungs-Empfänger, sobald der Launcher-Prozess läuft. */
public final class LumenApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationCenter.init(this);
        NotifyServer.start(this);
        NotifyService.start(this);
    }
}
