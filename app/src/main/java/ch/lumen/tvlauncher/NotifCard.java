package ch.lumen.tvlauncher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/** Benachrichtigungs-Karte: dunkles Glas, bei Fokus weiß (wie die Pillen). */
@SuppressLint("ViewConstructor")
final class NotifCard extends LinearLayout {
    final Notif notif;
    private final TextView time;

    NotifCard(Context c, Notif n, Dims d, boolean focusable) {
        super(c);
        notif = n;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(d.sh * 0.018f);
        setPadding(pad, pad, Math.round(pad * 1.3f), pad);

        GradientDrawable off = new GradientDrawable();
        off.setCornerRadius(d.radius);
        off.setColor(0x73000000);
        off.setStroke(Math.max(1, Math.round(d.sh * 0.001f)), 0x26FFFFFF);
        GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(d.radius);
        on.setColor(0xF2FFFFFF);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        setBackground(bg);

        if (n.image != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(n.image);
            img.setOutlineProvider(Images.rounded(d.radius * 0.6f));
            img.setClipToOutline(true);
            int ih = Math.round(d.sh * 0.085f);
            LayoutParams lp = new LayoutParams(Math.round(ih * 16f / 9f), ih);
            lp.rightMargin = pad;
            addView(img, lp);
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setDuplicateParentStateEnabled(true);
        addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setDuplicateParentStateEnabled(true);
        col.addView(top);

        String titleText = n.title.isEmpty() ? "Home Assistant" : n.title;
        TextView title = UiKit.text(c, titleText, d.sh * 0.021f, 0xFFFFFFFF, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(states(0xFF000000, 0xFFFFFFFF));
        title.setDuplicateParentStateEnabled(true);
        top.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        time = UiKit.text(c, "", d.sh * 0.016f, 0x99FFFFFF, false);
        time.setTextColor(states(0x99000000, 0x8CFFFFFF));
        time.setDuplicateParentStateEnabled(true);
        LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = pad;
        top.addView(time, tlp);

        if (!n.message.isEmpty()) {
            TextView msg = UiKit.text(c, n.message, d.sh * 0.019f, 0xD9FFFFFF, false);
            msg.setMaxLines(3);
            msg.setEllipsize(TextUtils.TruncateAt.END);
            msg.setTextColor(states(0xCC000000, 0xD9FFFFFF));
            msg.setDuplicateParentStateEnabled(true);
            msg.setLineSpacing(0, 1.1f);
            LayoutParams mlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            mlp.topMargin = Math.round(d.sh * 0.004f);
            col.addView(msg, mlp);
        }

        updateTime();
        setFocusable(focusable);
        setClickable(focusable);
        if (focusable) {
            setOnFocusChangeListener((v, f) ->
                    v.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f).setDuration(160).start());
        }
        setPivotX(0f);
    }

    private static ColorStateList states(int focused, int normal) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, normal});
    }

    void updateTime() {
        long mins = (System.currentTimeMillis() - notif.time) / 60000;
        String s;
        if (mins < 1) s = "Jetzt";
        else if (mins < 60) s = "vor " + mins + " Min.";
        else if (mins < 180) s = "vor " + (mins / 60) + " Std.";
        else s = DateFormat.getTimeFormat(getContext()).format(new Date(notif.time));
        time.setText(s);
    }

    static int width(Dims d) {
        return Math.round(d.sw * 0.3f);
    }

}
