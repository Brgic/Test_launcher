package ch.lumen.tvlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Startet den Empfänger nach dem Hochfahren (und holt Lumen TV nach vorn) sowie nach einem Update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // Nur nach echtem Hochfahren den Startbildschirm nach vorn holen, nicht nach einem Update
        NotifyService.start(c, Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()));
    }
}
