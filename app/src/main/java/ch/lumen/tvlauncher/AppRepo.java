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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Lädt installierte Apps und merkt sich Reihenfolge & ausgeblendete Apps. */
final class AppRepo {
    private static final String K_ORDER = "order";
    private static final String K_HIDDEN = "hidden";

    private final Context ctx;
    private final SharedPreferences sp;

    AppRepo(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** Läuft im Hintergrund-Thread. Liefert alle Kacheln in gespeicherter Reihenfolge (inkl. ausgeblendeter). */
    List<AppEntry> loadAll(int w, int h, float radius) {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, AppEntry> map = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, map, w, h, radius);
        collect(pm, Intent.CATEGORY_LAUNCHER, map, w, h, radius);

        List<AppEntry> apps = new ArrayList<>(map.values());
        final Collator col = Collator.getInstance();
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return col.compare(a.label, b.label);
            }
        });

        AppEntry settings = new AppEntry(AppEntry.SETTINGS_KEY, ctx.getString(R.string.settings),
                new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Images.render(settings, null, ctx.getResources().getDrawable(R.drawable.ic_settings, null), w, h, radius);
        apps.add(settings);
        map.put(settings.key, settings);

        List<AppEntry> result = new ArrayList<>(apps.size());
        Set<String> used = new HashSet<>();
        for (String k : getOrder()) {
            AppEntry e = map.get(k);
            if (e != null && used.add(k)) result.add(e);
        }
        for (AppEntry e : apps) {           // neue Apps hinten anhängen
            if (used.add(e.key)) result.add(e);
        }
        return result;
    }

    private void collect(PackageManager pm, String category, LinkedHashMap<String, AppEntry> map,
                         int w, int h, float radius) {
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
                AppEntry e = new AppEntry(ai.packageName, ri.loadLabel(pm).toString(), launch);
                Drawable banner = ai.loadBanner(pm);   // fällt intern auf das App-Banner zurück
                Images.render(e, banner, banner == null ? loadIcon(pm, ai) : null, w, h, radius);
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
                if (d != null) return d;
            } catch (Exception ignored) {
            }
        }
        return ai.loadIcon(pm);
    }

    private List<String> getOrder() {
        List<String> out = new ArrayList<>();
        String s = sp.getString(K_ORDER, "");
        for (String k : s.split("\n")) if (!k.isEmpty()) out.add(k);
        return out;
    }

    void saveOrder(List<AppEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : entries) sb.append(e.key).append('\n');
        sp.edit().putString(K_ORDER, sb.toString()).apply();
    }

    Set<String> getHidden() {
        return new HashSet<>(sp.getStringSet(K_HIDDEN, Collections.<String>emptySet()));
    }

    void setHidden(Set<String> hidden) {
        sp.edit().putStringSet(K_HIDDEN, new HashSet<>(hidden)).apply();
    }
}
