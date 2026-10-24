package ch.lumen.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.DisplayMetrics;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Lädt installierte Apps und verwaltet Favoriten und Kachelstil. */
final class AppRepo {
    private static final String K_FAVS = "favorites";
    private static final String K_CALM = "calm_tiles";

    private final Context ctx;
    private final SharedPreferences sp;

    AppRepo(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    boolean isCalm() {
        return sp.getBoolean(K_CALM, true);
    }

    void setCalm(boolean calm) {
        sp.edit().putBoolean(K_CALM, calm).apply();
    }

    /** Hintergrund-Thread. Alle Apps alphabetisch, Einstellungen am Ende. */
    List<AppEntry> loadAll(Dims d) {
        boolean calm = isCalm();
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, AppEntry> map = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, map, d, calm);
        collect(pm, Intent.CATEGORY_LAUNCHER, false, map, d, calm);

        List<AppEntry> apps = new ArrayList<>(map.values());
        final Collator col = Collator.getInstance();
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return col.compare(a.label, b.label);
            }
        });

        AppEntry settings = new AppEntry(AppEntry.SETTINGS_KEY, ctx.getString(R.string.settings),
                new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), true);
        Images.renderGlass(settings, ctx.getResources().getDrawable(R.drawable.ic_settings, null),
                d.renderW, d.renderH, d.renderR);
        apps.add(settings);
        return apps;
    }

    private void collect(PackageManager pm, String category, boolean tv,
                         LinkedHashMap<String, AppEntry> map, Dims d, boolean calm) {
        List<ResolveInfo> list = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
        String self = ctx.getPackageName();
        for (ResolveInfo ri : list) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName) || map.containsKey(ai.packageName)) continue;
            try {
                Intent launch = new Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setClassName(ai.packageName, ai.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                AppEntry e = new AppEntry(ai.packageName, ri.loadLabel(pm).toString(), launch, tv);
                if (calm) {
                    Images.renderCalm(e, loadIcon(pm, ai), d.renderW, d.renderH, d.renderR);
                } else {
                    Drawable banner = ai.loadBanner(pm);
                    Images.render(e, banner, banner == null ? loadIcon(pm, ai) : null,
                            d.renderW, d.renderH, d.renderR);
                }
                map.put(e.key, e);
            } catch (Throwable ignored) {
                // Eine fehlerhafte App soll den Launcher nicht stoppen
            }
        }
    }

    /** Icon in hoher Auflösung, damit es auf großen Kacheln scharf bleibt. */
    private static Drawable loadIcon(PackageManager pm, ActivityInfo ai) {
        int res = ai.getIconResource();
        if (res != 0) {
            try {
                Resources r = pm.getResourcesForApplication(ai.applicationInfo);
                Drawable d = r.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) return d.mutate();
            } catch (Exception ignored) {
            }
        }
        return ai.loadIcon(pm).mutate();
    }

    /** null = noch nie gespeichert. */
    List<String> getFavoriteKeys() {
        String s = sp.getString(K_FAVS, null);
        if (s == null) return null;
        List<String> out = new ArrayList<>();
        for (String k : s.split("\n")) if (!k.isEmpty()) out.add(k);
        return out;
    }

    void saveFavorites(List<AppEntry> favs) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : favs) sb.append(e.key).append('\n');
        sp.edit().putString(K_FAVS, sb.toString()).apply();
    }
}
