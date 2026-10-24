package ch.lumen.tvlauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** Kleine UI-Bausteine: Text und Pillen-Buttons im Apple-TV-Stil. */
final class UiKit {
    private UiKit() {}

    static TextView text(Context c, CharSequence s, float px, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.create(medium ? "sans-serif-medium" : "sans-serif", android.graphics.Typeface.NORMAL));
        return t;
    }

    /** Pille: halbtransparent, bei Fokus weiß mit schwarzer Schrift. */
    static TextView pill(Context c, CharSequence s, Dims d) {
        TextView t = text(c, s, d.sh * 0.022f, 0xFFFFFFFF, true);
        float r = d.sh;
        GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(r);
        on.setColor(0xFFFFFFFF);
        GradientDrawable off = new GradientDrawable();
        off.setCornerRadius(r);
        off.setColor(0x26FFFFFF);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        t.setBackground(bg);
        t.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xFF000000, 0xFFFFFFFF}));
        int ph = Math.round(d.sh * 0.03f), pv = Math.round(d.sh * 0.012f);
        t.setPadding(ph, pv, ph, pv);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnFocusChangeListener((v, f) ->
                v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(160).start());
        return t;
    }
}
