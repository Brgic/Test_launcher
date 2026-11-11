package ch.lumen.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Knöpfe für Home-Assistant-Szenen und -Skripte.
 * Home Assistant schickt die Liste über den Benachrichtigungs-Empfänger (Titel „lumen:buttons“);
 * ein Knopfdruck ruft einen Webhook in Home Assistant auf. Kein Token auf dem TV nötig.
 */
final class HaButtons {
    static final String TITLE = "lumen:buttons";
    private static final String PREFIX = "LUMENBUTTONS";
    private static final String P_JSON = "ha_buttons_json";
    private static final String P_HOST = "ha_host";

    interface Listener {
        void onButtonsChanged();
    }

    static final class Btn {
        final String id, name;

        Btn(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static Listener listener;

    private HaButtons() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** Vom Empfänger aufgerufen (Hintergrund-Thread). true = war eine Knopfliste, nicht anzeigen. */
    static boolean handle(Context c, String title, String msg, String senderIp) {
        if (!TITLE.equals(title == null ? null : title.trim())) return false;
        try {
            String json = msg == null ? "" : msg.trim();
            if (json.startsWith(PREFIX)) json = json.substring(PREFIX.length()).trim();
            new JSONObject(json);   // nur speichern, wenn gültig
            sp(c).edit().putString(P_JSON, json).putString(P_HOST, senderIp).apply();
        } catch (Exception ignored) {
        }
        return true;
    }

    static List<Btn> load(Context c) {
        List<Btn> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(sp(c).getString(P_JSON, "{}"));
            JSONArray a = o.optJSONArray("buttons");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject b = a.getJSONObject(i);
                String id = b.optString("id", "");
                if (!id.isEmpty()) out.add(new Btn(id, b.optString("name", id)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Hintergrund-Thread. Löst die Szene bzw. das Skript über den Webhook aus. */
    static boolean trigger(Context c, Btn b) {
        HttpURLConnection con = null;
        try {
            JSONObject cfg = new JSONObject(sp(c).getString(P_JSON, "{}"));
            String host = sp(c).getString(P_HOST, null);
            String hook = cfg.optString("webhook", "");
            int port = cfg.optInt("port", 8123);
            if (host == null || hook.isEmpty()) return false;
            URL url = new URL("http://" + host + ":" + port + "/api/webhook/" + hook);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(4000);
            con.setReadTimeout(6000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            byte[] body = new JSONObject().put("entity_id", b.id).toString().getBytes(Charset.forName("UTF-8"));
            try (OutputStream os = con.getOutputStream()) {
                os.write(body);
            }
            int code = con.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
