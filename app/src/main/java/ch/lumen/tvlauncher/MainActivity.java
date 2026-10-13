package ch.lumen.tvlauncher;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements TileView.Host {

    private static final int COLS = 5;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);

    private AppRepo repo;
    private List<AppEntry> all = new ArrayList<>();          // alle, in Reihenfolge, inkl. ausgeblendeter
    private final List<AppEntry> visible = new ArrayList<>();
    private final Map<String, TileView> tiles = new HashMap<>();

    private TvScrollView scroll;
    private LinearLayout grid;
    private View backdrop, shelf;
    private ImageView bgA, bgB, heroArt;
    private TextView title;
    private boolean bgAFront = true;

    private int tileW, tileH, gapH, gapV, sidePad, shelfH, screenH;
    private float radius;

    private ObjectAnimator scrollAnim;
    private AppEntry moving, shownHero, pendingHero;
    private String focusKey;
    private boolean resumed, dirty;

    private final Runnable heroRunnable = new Runnable() {
        @Override
        public void run() {
            if (pendingHero != null) showHero(pendingHero);
        }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (resumed) reload();
            else dirty = true;
        }
    };

    // ---------------------------------------------------------------- Lebenszyklus

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        scroll = findViewById(R.id.scroll);
        grid = findViewById(R.id.grid);
        backdrop = findViewById(R.id.backdrop);
        shelf = findViewById(R.id.shelf);
        bgA = findViewById(R.id.bgA);
        bgB = findViewById(R.id.bgB);
        heroArt = findViewById(R.id.heroArt);
        title = findViewById(R.id.title);
        TextView hint = findViewById(R.id.hint);
        TextView clock = findViewById(R.id.clock);

        // Maße relativ zum Bildschirm -> sieht auf 720p, 1080p und 4K gleich aus
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        screenH = h;
        sidePad = Math.round(w * 0.06f);
        gapH = Math.round(w * 0.024f);
        tileW = (w - 2 * sidePad - (COLS - 1) * gapH) / COLS;
        tileH = Math.round(tileW * 9f / 16f);
        gapV = Math.round(tileH * 0.5f);
        radius = tileH * 0.09f;
        shelfH = Math.round(h * 0.58f);

        shelf.getLayoutParams().height = shelfH;

        FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) findViewById(R.id.shelfText).getLayoutParams();
        tp.leftMargin = sidePad;
        tp.bottomMargin = Math.round(tileH * 0.55f);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.062f);
        title.setMaxWidth(Math.round(w * 0.5f));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.02f);

        int heroW = Math.round(w * 0.28f);
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) heroArt.getLayoutParams();
        hp.width = heroW;
        hp.height = heroW * 9 / 16;
        hp.rightMargin = sidePad;
        heroArt.setOutlineProvider(Images.rounded(radius * heroW / tileW));
        heroArt.setElevation(tileH * 0.2f);

        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) clock.getLayoutParams();
        cp.topMargin = Math.round(h * 0.05f);
        cp.rightMargin = sidePad;
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.032f);

        grid.setPadding(0, shelfH, 0, h / 2);
        scroll.setListener(new TvScrollView.Listener() {
            @Override
            public void onScroll(int y) {
                onScrolled(y);
            }
        });

        repo = new AppRepo(this);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (dirty) {
            dirty = false;
            reload();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (moving != null) endMove();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home-Taste, während der Launcher schon offen ist -> nach oben
        if (resumed) goTop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        io.shutdownNow();
        ui.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        if (moving != null) endMove();
        else goTop();               // Launcher wird nie verlassen
    }

    // ---------------------------------------------------------------- Laden

    private void reload() {
        final int w = Math.round(tileW * 1.35f), h = Math.round(tileH * 1.35f);   // Reserve für Fokus-Zoom
        final float r = radius * 1.35f;
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> list = repo.loadAll(w, h, r);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        applyApps(list);
                    }
                });
            }
        });
    }

    private void applyApps(List<AppEntry> list) {
        if (isFinishing()) return;
        String keep = focusKey;
        all = list;
        tiles.clear();
        shownHero = null;
        recomputeVisible();
        rebuildGrid();
        focusByKey(keep, 0);
    }

    private void recomputeVisible() {
        Set<String> hidden = repo.getHidden();
        visible.clear();
        for (AppEntry e : all) if (!hidden.contains(e.key)) visible.add(e);
    }

    private void rebuildGrid() {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < visible.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setPadding(sidePad, 0, sidePad, 0);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rp.topMargin = gapV;
                grid.addView(row, rp);
            }
            AppEntry e = visible.get(i);
            TileView t = tiles.get(e.key);
            if (t == null) {
                t = new TileView(this, e, tileW, tileH, radius, this);
                tiles.put(e.key, t);
            }
            if (t.getParent() != null) ((ViewGroup) t.getParent()).removeView(t);
            t.index = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            if (i % COLS != 0) lp.leftMargin = gapH;
            row.addView(t, lp);
        }
    }

    private void focusByKey(String key, int fallbackIndex) {
        if (visible.isEmpty()) return;
        TileView t = key != null ? tiles.get(key) : null;
        if (t == null || t.getParent() == null) {
            int i = Math.max(0, Math.min(fallbackIndex, visible.size() - 1));
            t = tiles.get(visible.get(i).key);
        }
        if (t != null) t.requestFocus();
    }

    private void goTop() {
        if (!visible.isEmpty()) focusByKey(visible.get(0).key, 0);
    }

    // ---------------------------------------------------------------- Fokus, Scrollen, Top Shelf

    @Override
    public void onTileFocused(TileView t) {
        focusKey = t.entry.key;
        int row = t.index / COLS;
        int target = row == 0 ? 0
                : Math.max(0, shelfH + row * (tileH + gapV) - Math.round(screenH * 0.32f));
        if (scroll.getHeight() == 0 || grid.isLayoutRequested()) {
            final int tg = target;
            grid.post(new Runnable() {
                @Override
                public void run() {
                    scroll.scrollTo(0, tg);
                }
            });
        } else {
            animateScrollTo(target);
        }
        pendingHero = t.entry;
        ui.removeCallbacks(heroRunnable);
        if (shownHero == null) showHero(t.entry);
        else ui.postDelayed(heroRunnable, 120);   // beim schnellen Durchklicken nicht flackern
    }

    private void animateScrollTo(int target) {
        if (scrollAnim != null) scrollAnim.cancel();
        if (scroll.getScrollY() == target) return;
        scrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", target);
        scrollAnim.setDuration(320);
        scrollAnim.setInterpolator(ease);
        scrollAnim.start();
    }

    private void onScrolled(int y) {
        float f = Math.min(1f, y / (shelfH * 0.55f));
        shelf.setAlpha(1f - f);
        shelf.setTranslationY(-y * 0.35f);
        backdrop.setAlpha(1f - 0.6f * f);
    }

    private void showHero(final AppEntry e) {
        if (e == shownHero) return;
        shownHero = e;

        ImageView in = bgAFront ? bgB : bgA;
        ImageView out = bgAFront ? bgA : bgB;
        bgAFront = !bgAFront;
        in.animate().cancel();
        out.animate().cancel();
        in.setImageBitmap(e.backdrop);
        in.animate().alpha(1f).setDuration(450).start();
        out.animate().alpha(0f).setDuration(450).start();

        heroArt.animate().cancel();
        title.animate().cancel();
        title.animate().alpha(0f).setDuration(90).start();
        heroArt.animate().alpha(0f).setDuration(90).withEndAction(new Runnable() {
            @Override
            public void run() {
                heroArt.setImageBitmap(e.tile);
                title.setText(e.label);
                heroArt.animate().alpha(1f).setDuration(240).start();
                title.animate().alpha(1f).setDuration(240).start();
            }
        }).start();
    }

    // ---------------------------------------------------------------- Öffnen & Optionen

    @Override
    public void onTileClick(TileView t) {
        if (moving != null) {
            endMove();
            return;
        }
        t.pulse();
        try {
            Bundle opts = ActivityOptions.makeScaleUpAnimation(t, 0, 0, t.getWidth(), t.getHeight()).toBundle();
            startActivity(t.entry.intent, opts);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.cant_launch, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTileLongClick(TileView t) {
        if (moving == null) showMenu(t.entry);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && moving == null) {
            View f = getCurrentFocus();
            if (f instanceof TileView) showMenu(((TileView) f).entry);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showMenu(final AppEntry e) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add(getString(R.string.move));
        actions.add(new Runnable() {
            @Override
            public void run() {
                startMove(e);
            }
        });

        if (e.isSettings()) {
            labels.add(getString(R.string.hidden_apps));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    showHiddenDialog();
                }
            });
        } else {
            labels.add(getString(R.string.hide));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    hide(e);
                }
            });
            labels.add(getString(R.string.app_info));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + e.key)));
                }
            });
            labels.add(getString(R.string.uninstall));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    safeStart(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.key)));
                }
            });
        }

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e.label)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            Toast.makeText(this, R.string.cant_launch, Toast.LENGTH_SHORT).show();
        }
    }

    private void hide(AppEntry e) {
        int idx = visible.indexOf(e);
        Set<String> h = repo.getHidden();
        h.add(e.key);
        repo.setHidden(h);
        recomputeVisible();
        rebuildGrid();
        focusByKey(null, idx);
    }

    private void showHiddenDialog() {
        Set<String> hidden = repo.getHidden();
        final List<AppEntry> list = new ArrayList<>();
        for (AppEntry e : all) if (hidden.contains(e.key)) list.add(e);
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[list.size()];
        final boolean[] checked = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).label;
            checked[i] = true;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.hidden_title)
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Set<String> h = repo.getHidden();
                        for (int i = 0; i < list.size(); i++) if (!checked[i]) h.remove(list.get(i).key);
                        repo.setHidden(h);
                        recomputeVisible();
                        rebuildGrid();
                        focusByKey(focusKey, 0);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- Verschiebe-Modus

    private void startMove(AppEntry e) {
        TileView t = tiles.get(e.key);
        if (t == null) return;
        moving = e;
        t.requestFocus();
        t.startWiggle();
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_LONG).show();
    }

    private void endMove() {
        TileView t = tiles.get(moving.key);
        if (t != null) t.stopWiggle();
        moving = null;
        List<AppEntry> merged = new ArrayList<>(visible);
        for (AppEntry e : all) if (!merged.contains(e)) merged.add(e);
        all = merged;
        repo.saveOrder(all);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (moving == null) return super.dispatchKeyEvent(ev);
        int k = ev.getKeyCode();
        int idx = visible.indexOf(moving);
        int to;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  to = idx - 1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: to = idx + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP:    to = idx - COLS; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  to = idx + COLS; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (ev.getAction() == KeyEvent.ACTION_UP) endMove();
                return true;
            default:
                return super.dispatchKeyEvent(ev);   // z. B. Lautstärke
        }
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
        to = Math.max(0, Math.min(to, visible.size() - 1));
        if (to == idx) return true;
        visible.remove(idx);
        visible.add(to, moving);
        rebuildGrid();
        TileView t = tiles.get(moving.key);
        if (t != null) t.requestFocus();
        return true;
    }
}
