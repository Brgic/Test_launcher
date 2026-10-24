package ch.lumen.tvlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Hält den Benachrichtigungs-Empfänger am Leben – auch wenn der TV ins Live-TV startet
 * und der Startbildschirm nie geöffnet wird. Läuft als stiller Vordergrunddienst.
 */
public final class NotifyService extends Service {
    private static final String CHANNEL = "lumen_receiver";
    private static final int ID = 7676;

    static void start(Context c) {
        try {
            Intent i = new Intent(c, NotifyService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {
            // z. B. Start im Hintergrund nicht erlaubt – der Empfänger läuft trotzdem, solange der Prozess lebt
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationCenter.init(this);
        NotifyServer.start(this);
        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(ID, n);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Benachrichtigungs-Empfänger",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
        }
        return b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Lumen TV")
                .setContentText("Empfängt Benachrichtigungen aus Home Assistant")
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
