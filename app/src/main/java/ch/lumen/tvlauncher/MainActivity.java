package ch.lumen.tvlauncher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity
        implements TileView.Host, AppsPanel.Callback, WallpaperPanel.Callback,
        NotificationCenter.Listener {

    private static final int REQ_PERM = 1;
    private static final int REQ_PICK = 2;
    private static final int DEFAULT_FAVS = 5;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Dims d;
    private AppRepo repo;
    private List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> favs = new ArrayList<>();
    private AppEntry allTile;

    private LinearLayout row, notifBox;
    private TvHScrollView hscroll;
    private ImageView wpA, wpB;
    private boolean aFront = true;
    private AppsPanel apps;
    private WallpaperPanel wp;

    private AppEntry moving;
    private String focusKey;
    private boolean resumed, dirty;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (NotificationCenter.expire()) renderNotifs(null);
            else for (int i = 0; i < notifBox.getChildCount(); i++) ((NotifCard) notifBox.getChildAt(i)).updateTime();
            ui.postDelayed(this, 30000);
        }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (resumed) reload();
            else dirty = true;
        }
    };

    // ------------------------------------------------------------ Lebenszyklus

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        d = new Dims(getResources().getDisplayMetrics());
        repo = new AppRepo(this);

        FrameLayout root = findViewById(R.id.root);
        wpA = findViewById(R.id.wpA);
        wpB = findViewById(R.id.wpB);
        row = findViewById(R.id.row);
        hscroll = findViewById(R.id.hscroll);
        notifBox = findViewById(R.id.notifBox);
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) notifBox.getLayoutParams();
        np.topMargin = Math.round(d.sh * 0.06f);
        np.leftMargin = d.side;

        View clockBox = findViewById(R.id.clockBox);
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) clockBox.getLayoutParams();
        cp.topMargin = Math.round(d.sh * 0.06f);
        cp.rightMargin = d.side;
        ((TextView) findViewById(R.id.clock)).setTextSize(TypedValue.COMPLEX_UNIT_PX, d.sh * 0.075f);
        ((TextView) findViewById(R.id.date)).setTextSize(TypedValue.COMPLEX_UNIT_PX, d.sh * 0.024f);

        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) hscroll.getLayoutParams();
        hp.bottomMargin = Math.round(d.sh * 0.06f);
        row.setPadding(d.side, Math.round(d.tileH * 0.2f), d.side, Math.round(d.tileH * 0.5f));

        allTile = new AppEntry(AppEntry.ALL_KEY, getString(R.string.all_apps), null, false);
        Images.renderGlass(allTile, getResources().getDrawable(R.drawable.ic_apps, null),
                d.renderW, d.renderH, d.renderR);

        apps = new AppsPanel(this, d, this);
        apps.setTileStyleLabel(repo.isCalm());
        wp = new WallpaperPanel(this, d, io, ui, this);
        root.addView(apps, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wp, new FrameLayout.LayoutParams(-1, -1));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);

        loadWallpaper();
        reload();
        NotificationCenter.setListener(this);
        renderNotifs(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        NotificationCenter.setLauncherVisible(true);
        ui.removeCallbacks(ticker);
        ticker.run();
        if (dirty) {
            dirty = false;
            reload();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        NotificationCenter.setLauncherVisible(false);
        ui.removeCallbacks(ticker);
        if (moving != null) endMove();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Nach dem Start einer App zurück auf den ruhigen Startbildschirm
        if (apps.isOpen() || wp.isOpen()) {
            wp.close(false);
            apps.close(false);
            updateFocusBlocking();
            focusRow(focusKey, 0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (resumed) goHome();   // Home-Taste im Launcher
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        NotificationCenter.setListener(null);
        NotificationCenter.setLauncherVisible(false);
        io.shutdownNow();
        ui.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        if (moving != null) endMove();
        else if (wp.isOpen()) closeWallpaper();
        else if (apps.isOpen()) closeApps();
        else focusRow(null, 0);   // Launcher wird nie verlassen
    }

    private void goHome() {
        if (moving != null) endMove();
        wp.close(true);
        apps.close(true);
        updateFocusBlocking();
        focusRow(null, 0);
    }

    // ------------------------------------------------------------ Laden

    private void reload() {
        io.execute(() -> {
            final List<AppEntry> list = repo.loadAll(d);
            ui.post(() -> applyApps(list));
        });
    }

    private void applyApps(List<AppEntry> list) {
        if (isFinishing()) return;
        all = list;
        Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry e : all) byKey.put(e.key, e);

        favs.clear();
        List<String> keys = repo.getFavoriteKeys();
        if (keys == null) {
            for (AppEntry e : all) if (e.tv && e.isApp() && favs.size() < DEFAULT_FAVS) favs.add(e);
            for (AppEntry e : all) if (!favs.contains(e) && e.isApp() && favs.size() < DEFAULT_FAVS) favs.add(e);
            repo.saveFavorites(favs);
        } else {
            for (String k : keys) {
                AppEntry e = byKey.get(k);
                if (e != null) favs.add(e);
            }
        }
        rebuildRow();
        apps.setApps(all);
        if (!apps.isOpen() && !wp.isOpen()) focusRow(focusKey, 0);
    }

    private void loadWallpaper() {
        final int w = Math.min(d.sw, 1920), h = Math.min(d.sh, 1080);
        io.execute(() -> {
            final Bitmap b = Wallpaper.load(this, w, h);
            final Bitmap tiny = Images.blurSmall(b);
            ui.post(() -> {
                final ImageView in = aFront ? wpB : wpA;
                final ImageView out = aFront ? wpA : wpB;
                aFront = !aFront;
                in.animate().cancel();
                out.animate().cancel();
                in.setImageBitmap(b);
                in.animate().alpha(1f).setDuration(500).start();
                out.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                    if (out.getAlpha() == 0f) out.setImageDrawable(null);
                }).start();
                apps.setBackdrop(tiny);
                wp.setBackdrop(tiny);
            });
        });
    }

    // ------------------------------------------------------------ Favoritenleiste

    private void rebuildRow() {
        row.removeAllViews();
        List<AppEntry> items = new ArrayList<>(favs);
        items.add(allTile);
        for (int i = 0; i < items.size(); i++) {
            TileView t = new TileView(this, items.get(i), d, false, this);
            t.index = i;
            row.addView(t, rowParams(i));
        }
    }

    private LinearLayout.LayoutParams rowParams(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d.tileW, d.tileH);
        if (i > 0) lp.leftMargin = d.gap;
        return lp;
    }

    private void focusRow(String key, int fallback) {
        int n = row.getChildCount();
        if (n == 0) return;
        for (int i = 0; i < n; i++) {
            TileView t = (TileView) row.getChildAt(i);
            if (key != null && key.equals(t.entry.key)) {
                t.requestFocus();
                return;
            }
        }
        row.getChildAt(Math.max(0, Math.min(fallback, n - 1))).requestFocus();
    }

    @Override
    public void onTileFocused(TileView t) {
        focusKey = t.entry.key;
        hscroll.ensureVisible(t, d.side);
    }

    @Override
    public void onTileClick(TileView t) {
        if (moving != null) {
            endMove();
        } else if (t.entry == allTile) {
            openApps();
        } else {
            launch(t);
        }
    }

    @Override
    public void onTileLongClick(TileView t) {
        if (moving != null) return;
        if (t.entry == allTile) {
            menu(allTile.label, new String[]{getString(R.string.change_wallpaper)},
                    new Runnable[]{this::openWallpaper});
        } else {
            favMenu(t.entry);
        }
    }

    private void launch(TileView t) {
        t.pulse();
        try {
            Bundle opts = ActivityOptions.makeScaleUpAnimation(t, 0, 0, t.getWidth(), t.getHeight()).toBundle();
            startActivity(t.entry.intent, opts);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.cant_launch, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ Menüs

    private void favMenu(final AppEntry e) {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        l.add(getString(R.string.move));
        a.add(() -> startMove(e));
        l.add(getString(R.string.fav_remove));
        a.add(() -> toggleFavorite(e));
        addAppActions(e, l, a);
        menu(e.label, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }

    @Override
    public void onAppMenu(final AppEntry e) {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        l.add(getString(favs.contains(e) ? R.string.fav_remove : R.string.fav_add));
        a.add(() -> toggleFavorite(e));
        addAppActions(e, l, a);
        menu(e.label, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }

    private void addAppActions(final AppEntry e, List<String> l, List<Runnable> a) {
        if (!e.isApp()) return;
        l.add(getString(R.string.app_info));
        a.add(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + e.key))));
        l.add(getString(R.string.uninstall));
        a.add(() -> safeStart(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.key))));
    }

    private void menu(String title, String[] labels, final Runnable[] actions) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setItems(labels, (dlg, which) -> actions[which].run())
                .show();
    }

    private void toggleFavorite(AppEntry e) {
        int idx = favs.indexOf(e);
        if (idx >= 0) {
            favs.remove(idx);
            Toast.makeText(this, R.string.fav_removed, Toast.LENGTH_SHORT).show();
        } else {
            favs.add(e);
            Toast.makeText(this, R.string.fav_added, Toast.LENGTH_SHORT).show();
        }
        repo.saveFavorites(favs);
        rebuildRow();
        if (!apps.isOpen()) focusRow(null, Math.max(0, idx));
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            Toast.makeText(this, R.string.cant_launch, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ App-Liste

    private void openApps() {
        apps.open();
        updateFocusBlocking();
    }

    private void closeApps() {
        apps.close(true);
        updateFocusBlocking();
        focusRow(focusKey, 0);
    }

    @Override
    public void onAppOpen(TileView t) {
        launch(t);
    }

    @Override
    public void onWallpaperRequest() {
        openWallpaper();
    }

    @Override
    public void onToggleTileStyle() {
        boolean calm = !repo.isCalm();
        repo.setCalm(calm);
        apps.setTileStyleLabel(calm);
        reload();
    }

    // ------------------------------------------------------------ Hintergrund

    private void openWallpaper() {
        wp.open();
        updateFocusBlocking();
    }

    private void closeWallpaper() {
        wp.close(true);
        updateFocusBlocking();
        if (apps.isOpen()) apps.focusLast();
        else focusRow(focusKey, 0);
    }

    @Override
    public void onPresetChosen(int index) {
        Wallpaper.setPreset(this, index);
        loadWallpaper();
        goHome();
    }

    @Override
    public void onImageChosen(final Uri uri) {
        final int w = Math.min(d.sw, 1920), h = Math.min(d.sh, 1080);
        io.execute(() -> {
            final boolean ok = Wallpaper.setCustom(this, uri, w, h);
            ui.post(() -> {
                if (ok) {
                    loadWallpaper();
                    goHome();
                } else {
                    Toast.makeText(this, R.string.wallpaper_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public void onNeedImagePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{WallpaperPanel.imagePermission()}, REQ_PERM);
        }
    }

    @Override
    public void onSystemPicker() {
        try {
            startActivityForResult(WallpaperPanel.pickIntent(), REQ_PICK);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.cant_launch, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERM) {
            wp.onPermissionResult(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_PICK && result == RESULT_OK && data != null && data.getData() != null) {
            onImageChosen(data.getData());
        }
    }

    // ------------------------------------------------------------ Benachrichtigungen

    @Override
    public void onNotificationsChanged(Notif added) {
        renderNotifs(added);
    }

    private void renderNotifs(Notif added) {
        View f = getCurrentFocus();
        int focusedId = -1, focusedIndex = -1;
        if (f instanceof NotifCard) {
            focusedId = ((NotifCard) f).notif.id;
            focusedIndex = notifBox.indexOfChild(f);
        }
        notifBox.removeAllViews();
        int gap = Math.round(d.sh * 0.015f);
        View refocus = null;
        for (final Notif n : NotificationCenter.list()) {
            final NotifCard card = new NotifCard(this, n, d, true);
            card.setOnClickListener(v -> dismiss(card));
            card.setOnLongClickListener(v -> {
                menu(getString(R.string.notifications), new String[]{getString(R.string.clear_all)},
                        new Runnable[]{NotificationCenter::clear});
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(NotifCard.width(d),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (notifBox.getChildCount() > 0) lp.topMargin = gap;
            notifBox.addView(card, lp);
            if (n.id == focusedId) refocus = card;
            if (added != null && n.id == added.id) {
                card.setAlpha(0f);
                card.setTranslationX(-d.sh * 0.03f);
                card.animate().alpha(1f).translationX(0f).setDuration(260).start();
            }
        }
        if (refocus != null) {
            refocus.requestFocus();
        } else if (focusedIndex >= 0) {
            int n = notifBox.getChildCount();
            if (n > 0) notifBox.getChildAt(Math.min(focusedIndex, n - 1)).requestFocus();
            else focusRow(focusKey, 0);
        }
    }

    private void dismiss(final NotifCard card) {
        card.animate().alpha(0f).translationX(-d.sh * 0.04f).setDuration(160)
                .withEndAction(() -> NotificationCenter.remove(card.notif.id)).start();
    }

    @Override
    public void onHomeAssistantInfo() {
        String ip = NotifyServer.localIp();
        StringBuilder sb = new StringBuilder();
        sb.append("IP-Adresse dieses TVs: ").append(ip == null ? "unbekannt (kein Netzwerk?)" : ip).append("\n\n");
        sb.append("So richtest du es ein:\n");
        sb.append("1. Home Assistant → Einstellungen → Geräte & Dienste → Integration hinzufügen\n");
        sb.append("2. „Notifications for Android TV / Fire TV“ wählen und diese IP eingeben\n");
        sb.append("3. In Automationen die neue Aktion notify.… verwenden (Titel, Nachricht, optional Bild)\n\n");
        sb.append("Über anderen Apps einblenden: ")
                .append(OverlayPopup.allowed(this) ? "aktiv" : "aus (siehe README, per ADB aktivierbar)");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Home Assistant")
                .setMessage(sb.toString())
                .setPositiveButton(R.string.send_test, (dlg, w) -> {
                    goHome();
                    NotificationCenter.add(new Notif("Lumen TV", "Benachrichtigungen funktionieren.", null));
                })
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    // ------------------------------------------------------------ Fokus & Tasten

    /** Verhindert, dass der Fokus aus einer offenen Ebene in die darunterliegende springt. */
    private void updateFocusBlocking() {
        boolean anyOpen = apps.isOpen() || wp.isOpen();
        int home = anyOpen ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        hscroll.setDescendantFocusability(home);
        notifBox.setDescendantFocusability(home);
        apps.setDescendantFocusability(wp.isOpen() ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && moving == null) {
            View f = getCurrentFocus();
            if (f instanceof TileView) f.performLongClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void startMove(AppEntry e) {
        int i = favs.indexOf(e);
        if (i < 0) return;
        moving = e;
        TileView t = (TileView) row.getChildAt(i);
        t.requestFocus();
        t.startWiggle();
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_LONG).show();
    }

    private void endMove() {
        int i = favs.indexOf(moving);
        if (i >= 0) ((TileView) row.getChildAt(i)).stopWiggle();
        moving = null;
        repo.saveFavorites(favs);
    }

    private void moveFav(int from, int to) {
        AppEntry e = favs.remove(from);
        favs.add(to, e);
        View v = row.getChildAt(from);
        row.removeViewAt(from);
        row.addView(v, to);
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            c.setLayoutParams(rowParams(i));
            ((TileView) c).index = i;
        }
        v.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int k = ev.getKeyCode();
        if (moving != null) {
            int idx = favs.indexOf(moving);
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                        int to = idx + (k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                        if (to >= 0 && to < favs.size()) moveFav(idx, to);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (ev.getAction() == KeyEvent.ACTION_UP) endMove();
                    return true;
                default:
                    return super.dispatchKeyEvent(ev);
            }
        }
        // Auf dem Startbildschirm: Pfeil nach unten öffnet die App-Liste
        if (k == KeyEvent.KEYCODE_DPAD_DOWN && ev.getAction() == KeyEvent.ACTION_DOWN
                && !apps.isOpen() && !wp.isOpen()) {
            View f = getCurrentFocus();
            if (f != null && f.getParent() == row) {
                openApps();
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }
}
