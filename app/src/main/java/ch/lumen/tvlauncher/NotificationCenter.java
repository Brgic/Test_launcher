package ch.lumen.tvlauncher;

import android.content.Context;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Hält die aktuellen Benachrichtigungen. Nur vom Haupt-Thread benutzen. */
final class NotificationCenter {
    interface Listener {
        void onNotificationsChanged(Notif added);
    }

    static final int MAX = 4;
    private static final long MAX_AGE_MS = 6L * 60 * 60 * 1000;   // nach 6 Stunden ausblenden

    private static final List<Notif> items = new ArrayList<>();
    private static Listener listener;
    private static boolean launcherVisible;
    private static Context app;

    private NotificationCenter() {}

    static void init(Context c) {
        app = c.getApplicationContext();
    }

    static void setListener(Listener l) {
        listener = l;
    }

    static void setLauncherVisible(boolean v) {
        launcherVisible = v;
    }

    /** Neueste zuerst. */
    static List<Notif> list() {
        return new ArrayList<>(items);
    }

    static void add(Notif n) {
        items.add(0, n);
        while (items.size() > MAX) items.remove(items.size() - 1);
        if (!launcherVisible && app != null) OverlayPopup.show(app, n);
        if (listener != null) listener.onNotificationsChanged(n);
    }

    static void remove(int id) {
        for (Iterator<Notif> it = items.iterator(); it.hasNext(); ) {
            if (it.next().id == id) it.remove();
        }
        if (listener != null) listener.onNotificationsChanged(null);
    }

    static void clear() {
        items.clear();
        if (listener != null) listener.onNotificationsChanged(null);
    }

    /** true, wenn etwas entfernt wurde. */
    static boolean expire() {
        long limit = System.currentTimeMillis() - MAX_AGE_MS;
        boolean changed = false;
        for (Iterator<Notif> it = items.iterator(); it.hasNext(); ) {
            if (it.next().time < limit) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }
}
