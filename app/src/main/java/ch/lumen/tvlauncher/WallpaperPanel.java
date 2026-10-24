package ch.lumen.tvlauncher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Hintergrund wählen: Vorlagen und Bilder vom Gerät. */
@SuppressLint("ViewConstructor")
final class WallpaperPanel extends Overlay implements TileView.Host {

    interface Callback {
        void onPresetChosen(int index);
        void onImageChosen(Uri uri);
        void onNeedImagePermission();
        void onSystemPicker();
    }

    private static final int MAX_IMAGES = 60;
    private static final String PRESET = "preset:";

    private final Callback cb;
    private final ExecutorService io;
    private final Handler ui;
    private final TvScrollView scroll;
    private final LinearLayout presetsBox, imagesBox;
    private final TextView devicePill;
    private Bitmap placeholder;
    private TileView firstPreset;
    private boolean built, imagesLoaded;
    private int generation;

    WallpaperPanel(Context c, Dims d, ExecutorService io, Handler ui, Callback cb) {
        super(c, d);
        this.cb = cb;
        this.io = io;
        this.ui = ui;

        scroll = new TvScrollView(c);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(0, Math.round(d.sh * 0.08f), 0, Math.round(d.sh * 0.4f));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(padded(UiKit.text(c, c.getString(R.string.wallpaper), d.sh * 0.05f, 0xFFFFFFFF, true)));
        content.addView(section(c.getString(R.string.presets)));
        presetsBox = grid();
        content.addView(presetsBox);

        content.addView(section(c.getString(R.string.own_images)));
        LinearLayout pills = new LinearLayout(c);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        pills.setClipChildren(false);
        pills.setPadding(d.side, 0, d.side, 0);
        devicePill = UiKit.pill(c, "", d);
        devicePill.setOnClickListener(v -> {
            if (hasPermission()) loadImages();
            else cb.onNeedImagePermission();
        });
        pills.addView(devicePill);
        Intent pick = pickIntent();
        if (pick.resolveActivity(c.getPackageManager()) != null) {
            TextView other = UiKit.pill(c, c.getString(R.string.pick_file), d);
            other.setOnClickListener(v -> cb.onSystemPicker());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Math.round(d.sh * 0.02f);
            pills.addView(other, lp);
        }
        content.addView(pills);

        imagesBox = grid();
        LinearLayout.LayoutParams ilp = (LinearLayout.LayoutParams) imagesBox.getLayoutParams();
        ilp.topMargin = Math.round(d.sh * 0.05f);
        content.addView(imagesBox);
    }

    static Intent pickIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
    }

    private View padded(View v) {
        v.setPadding(d.side, 0, d.side, 0);
        return v;
    }

    private View section(String s) {
        TextView t = UiKit.text(getContext(), s, d.sh * 0.026f, 0x99FFFFFF, true);
        t.setPadding(d.side, Math.round(d.sh * 0.06f), d.side, Math.round(d.sh * 0.03f));
        return t;
    }

    private LinearLayout grid() {
        LinearLayout g = new LinearLayout(getContext());
        g.setOrientation(LinearLayout.VERTICAL);
        g.setClipChildren(false);
        g.setClipToPadding(false);
        g.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    /** Fügt eine Kachel in ein Raster mit COLS Spalten ein. */
    private TileView addTile(LinearLayout g, AppEntry e) {
        int n = 0;
        for (int i = 0; i < g.getChildCount(); i++) n += ((ViewGroup) g.getChildAt(i)).getChildCount();
        LinearLayout row;
        if (n % Dims.COLS == 0) {
            row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(d.side, 0, d.side, 0);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, d.tileH);
            if (n > 0) rp.topMargin = e.label.isEmpty() ? d.gap : d.gapV;
            g.addView(row, rp);
        } else {
            row = (LinearLayout) g.getChildAt(g.getChildCount() - 1);
        }
        TileView t = new TileView(getContext(), e, d, !e.label.isEmpty(), this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d.tileW, d.tileH);
        if (n % Dims.COLS != 0) lp.leftMargin = d.gap;
        row.addView(t, lp);
        return t;
    }

    boolean hasPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return getContext().checkSelfPermission(imagePermission()) == PackageManager.PERMISSION_GRANTED;
    }

    static String imagePermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    protected void onOpened() {
        scroll.jumpTop();
        if (!built) {
            built = true;
            placeholder = Images.solid(d.renderW, d.renderH, d.renderR, Images.GRAPHITE);
            for (int i = 0; i < Wallpaper.NAMES.length; i++) {
                AppEntry e = new AppEntry(PRESET + i, Wallpaper.NAMES[i], null, false);
                Bitmap b = Wallpaper.preset(i, d.renderW, d.renderH);
                e.tile = Images.round(b, d.renderR);
                b.recycle();
                TileView t = addTile(presetsBox, e);
                if (i == 0) firstPreset = t;
            }
        }
        updatePill();
        if (!imagesLoaded && hasPermission()) loadImages();
        if (firstPreset != null) firstPreset.requestFocus();
    }

    void onPermissionResult(boolean granted) {
        updatePill();
        if (granted) loadImages();
        else showInfo(getContext().getString(R.string.no_permission));
    }

    private void updatePill() {
        devicePill.setText(hasPermission() ? R.string.refresh : R.string.show_device_images);
    }

    private void showInfo(String s) {
        imagesBox.removeAllViews();
        TextView t = UiKit.text(getContext(), s, d.sh * 0.022f, 0x99FFFFFF, false);
        t.setPadding(d.side, 0, d.side, 0);
        imagesBox.addView(t);
    }

    private void loadImages() {
        imagesLoaded = true;
        final int gen = ++generation;
        imagesBox.removeAllViews();
        final ContentResolver cr = getContext().getContentResolver();
        io.execute(() -> {
            final List<Long> ids = new ArrayList<>();
            try (Cursor cur = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID}, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (cur != null) {
                    while (cur.moveToNext() && ids.size() < MAX_IMAGES) ids.add(cur.getLong(0));
                }
            } catch (Throwable ignored) {
            }
            ui.post(() -> {
                if (gen != generation) return;
                if (ids.isEmpty()) {
                    showInfo(getContext().getString(R.string.no_images));
                    return;
                }
                for (final long id : ids) {
                    final Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    AppEntry e = new AppEntry(uri.toString(), "", null, false);
                    e.tile = placeholder;
                    final TileView t = addTile(imagesBox, e);
                    io.execute(() -> {
                        final Bitmap thumb = thumbnail(cr, uri, id);
                        if (thumb != null) ui.post(() -> {
                            if (gen == generation) t.setBitmap(thumb);
                        });
                    });
                }
            });
        });
    }

    @SuppressWarnings("deprecation")
    private Bitmap thumbnail(ContentResolver cr, Uri uri, long id) {
        try {
            Bitmap src;
            if (Build.VERSION.SDK_INT >= 29) {
                src = cr.loadThumbnail(uri, new Size(d.renderW, d.renderH), null);
            } else {
                src = MediaStore.Images.Thumbnails.getThumbnail(cr, id, MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
            if (src == null) return null;
            Bitmap out = Images.roundCrop(src, d.renderW, d.renderH, d.renderR);
            src.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onTileFocused(TileView t) {
        scroll.smoothFocus(t, 0.36f);
    }

    @Override
    public void onTileClick(TileView t) {
        t.pulse();
        String k = t.entry.key;
        if (k.startsWith(PRESET)) cb.onPresetChosen(Integer.parseInt(k.substring(PRESET.length())));
        else cb.onImageChosen(Uri.parse(k));
    }

    @Override
    public void onTileLongClick(TileView t) {
        onTileClick(t);
    }
}
