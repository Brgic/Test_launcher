package ch.lumen.tvlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Startet den Empfänger nach dem Hochfahren und nach einem Update des Launchers. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        NotifyService.start(c);
    }
}
