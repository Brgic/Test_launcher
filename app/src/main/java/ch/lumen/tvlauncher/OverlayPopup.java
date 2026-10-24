package ch.lumen.tvlauncher;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Zeigt eine Benachrichtigung kurz oben links über anderen Apps (z. B. während Netflix läuft).
 * Benötigt die Berechtigung „Über anderen Apps einblenden“ – ohne sie passiert einfach nichts.
 */
final class OverlayPopup {
    private static final long SHOW_MS = 8000;
    private static View current;

    private OverlayPopup() {}

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    @SuppressWarnings("deprecation")
    static void show(Context c, Notif n) {
        if (!allowed(c)) return;
        try {
            final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            Dims d = new Dims(c.getResources().getDisplayMetrics());
            hide(wm);

            final NotifCard card = new NotifCard(c, n, d, false);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    NotifCard.width(d),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = d.side;
            lp.y = Math.round(d.sh * 0.06f);
            card.setAlpha(0f);
            card.setTranslationX(-d.sh * 0.03f);
            wm.addView(card, lp);
            current = card;
            card.animate().alpha(1f).translationX(0f).setDuration(250).start();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (current != card) return;
                card.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    if (current == card) hide(wm);
                }).start();
            }, SHOW_MS);
        } catch (Throwable ignored) {
        }
    }

    private static void hide(WindowManager wm) {
        if (current == null) return;
        try {
            wm.removeView(current);
        } catch (Throwable ignored) {
        }
        current = null;
    }
}
