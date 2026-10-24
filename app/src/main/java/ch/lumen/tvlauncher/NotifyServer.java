package ch.lumen.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Winziger HTTP-Empfänger auf Port 7676 – kompatibel mit der Home-Assistant-Integration
 * „Notifications for Android TV / Fire TV“ (Formular-POST mit msg, title und optionalem Bild).
 * Zusätzlich wird JSON {"title": "...", "message": "..."} angenommen.
 */
final class NotifyServer implements Runnable {
    static final int PORT = 7676;

    private static final Charset ISO = Charset.forName("ISO-8859-1");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private static final Pattern NAME = Pattern.compile("(?:^|[;\\s])name=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOUNDARY = Pattern.compile("boundary=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);
    private static boolean started;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private NotifyServer() {}

    static synchronized void start(Context c) {
        if (started) return;
        started = true;
        Thread t = new Thread(new NotifyServer(), "lumen-notify");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        while (true) {
            try (ServerSocket ss = new ServerSocket()) {
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(PORT));
                while (true) {
                    final Socket s = ss.accept();
                    pool.execute(() -> handle(s));
                }
            } catch (Throwable t) {
                try {
                    Thread.sleep(5000);   // Port belegt oder Netz weg -> später erneut
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(15000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String method = requestLine.split(" ")[0].toUpperCase(Locale.ROOT);

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int c = line.indexOf(':');
                if (c > 0) headers.put(line.substring(0, c).trim().toLowerCase(Locale.ROOT), line.substring(c + 1).trim());
            }

            if ("POST".equals(method)) {
                byte[] body = readBody(in, headers);
                if (body != null) handlePost(headers.get("content-type"), body);
            }
            respond(sock.getOutputStream());
        } catch (Throwable ignored) {
        }
    }

    private static void respond(OutputStream out) throws IOException {
        byte[] body = "OK".getBytes(ISO);
        String head = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(ISO));
        out.write(body);
        out.flush();
    }

    private void handlePost(String contentType, byte[] body) throws Exception {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String title = null, msg = null;
        byte[] image = null;

        if (ct.startsWith("multipart/form-data")) {
            Matcher m = BOUNDARY.matcher(contentType);
            if (!m.find()) return;
            Map<String, byte[]> parts = parseMultipart(body, m.group(1));
            title = str(parts.get("title"));
            msg = str(parts.get("msg"));
            if (msg == null) msg = str(parts.get("message"));
            image = parts.get("filename");
            if (image == null) image = parts.get("image");
        } else if (ct.contains("json")) {
            JSONObject o = new JSONObject(new String(body, UTF8));
            title = o.optString("title", null);
            msg = o.optString("message", o.optString("msg", null));
        } else {
            Map<String, String> f = parseUrlEncoded(new String(body, UTF8));
            title = f.get("title");
            msg = f.containsKey("msg") ? f.get("msg") : f.get("message");
        }

        if ((title == null || title.isEmpty()) && (msg == null || msg.isEmpty())) return;
        final Notif n = new Notif(title, msg, decodeImage(image));
        ui.post(() -> NotificationCenter.add(n));
    }

    private static String str(byte[] b) {
        return b == null ? null : new String(b, UTF8);
    }

    /** Bild verkleinert dekodieren; der 1×1-Platzhalter von Home Assistant wird ignoriert. */
    private static Bitmap decodeImage(byte[] b) {
        if (b == null || b.length < 100) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        if (o.outWidth < 8 || o.outHeight < 8) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 480) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(b, 0, b.length, o2);
    }

    // ------------------------------------------------------------ HTTP-Helfer

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') buf.write(c);
            if (buf.size() > 16384) throw new IOException("line too long");
        }
        if (c == -1 && buf.size() == 0) return null;
        return new String(buf.toByteArray(), ISO);
    }

    private static byte[] readBody(InputStream in, Map<String, String> h) throws IOException {
        if ("chunked".equalsIgnoreCase(h.get("transfer-encoding"))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (true) {
                String sizeLine = readLine(in);
                if (sizeLine == null) break;
                int semi = sizeLine.indexOf(';');
                int size = Integer.parseInt((semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim(), 16);
                if (size == 0) {
                    String l;
                    while ((l = readLine(in)) != null && !l.isEmpty()) { /* Trailer */ }
                    break;
                }
                if (out.size() + size > MAX_BODY) return null;
                out.write(readFully(in, size));
                readLine(in);
            }
            return out.toByteArray();
        }
        String cl = h.get("content-length");
        if (cl == null) return new byte[0];
        int len = Integer.parseInt(cl.trim());
        if (len < 0 || len > MAX_BODY) return null;
        return readFully(in, len);
    }

    private static byte[] readFully(InputStream in, int len) throws IOException {
        byte[] b = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(b, off, len - off);
            if (r < 0) throw new IOException("unexpected end");
            off += r;
        }
        return b;
    }

    static Map<String, byte[]> parseMultipart(byte[] body, String boundary) {
        Map<String, byte[]> out = new HashMap<>();
        byte[] delim = ("--" + boundary).getBytes(ISO);
        byte[] crlf2 = {'\r', '\n', '\r', '\n'};
        int pos = indexOf(body, delim, 0);
        while (pos >= 0) {
            int start = pos + delim.length;
            if (start + 1 < body.length && body[start] == '-' && body[start + 1] == '-') break;
            start += 2;   // CRLF nach dem Trenner
            int next = indexOf(body, delim, start);
            if (next < 0) break;
            int end = Math.max(start, next - 2);
            int hdrEnd = indexOf(body, crlf2, start);
            if (hdrEnd >= 0 && hdrEnd < end) {
                String headers = new String(body, start, hdrEnd - start, ISO);
                Matcher m = NAME.matcher(headers);
                if (m.find()) out.put(m.group(1), Arrays.copyOfRange(body, hdrEnd + 4, Math.max(hdrEnd + 4, end)));
            }
            pos = next;
        }
        return out;
    }

    private static int indexOf(byte[] data, byte[] pat, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) if (data[i + j] != pat[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static Map<String, String> parseUrlEncoded(String s) throws Exception {
        Map<String, String> out = new HashMap<>();
        for (String pair : s.split("&")) {
            int e = pair.indexOf('=');
            if (e <= 0) continue;
            out.put(URLDecoder.decode(pair.substring(0, e), "UTF-8"), URLDecoder.decode(pair.substring(e + 1), "UTF-8"));
        }
        return out;
    }

    /** Lokale IPv4-Adresse des TVs (für die Einrichtung in Home Assistant). */
    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
