package dev.flop.giveaway;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Streamlabs Socket API (socket.io v2 over a raw websocket).
 * Gets donations, subs, follows, bits and raids. Donation messages containing !<IGN> can enter.
 * Token: streamlabs.com > Settings > API Settings > API Tokens > "Your Socket API Token".
 */
public final class StreamlabsSocket extends WsClient {
    public static final StreamlabsSocket INSTANCE = new StreamlabsSocket();
    private volatile String token = "";

    private StreamlabsSocket() {
        EXEC.scheduleAtFixedRate(() -> { if (connected) send("2"); }, 20, 20, TimeUnit.SECONDS);
    }

    public void connect(String tok) {
        token = tok == null ? "" : tok.trim();
        if (token.isEmpty()) { stop(); status = "Paste your Socket API token"; return; }
        start();
    }

    @Override protected URI uri() {
        return URI.create("wss://sockets.streamlabs.com/socket.io/?EIO=3&transport=websocket&token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    @Override protected void opened() { status = "Authorizing..."; }

    @Override protected void message(String s) {
        if (s.startsWith("0")) return;            // engine.io open
        if (s.equals("3")) return;                // pong
        if (s.startsWith("40")) {                 // socket.io connected
            connected = true;
            status = "Connected";
            Giveaway.feed("Streamlabs connected", 0xFF31C3A2);
            return;
        }
        if (s.startsWith("44")) { status = "Token rejected"; stop(); status = "Token rejected"; return; }
        if (s.startsWith("41")) { reconnect(); return; }
        if (!s.startsWith("42")) return;

        JsonArray arr = JsonParser.parseString(s.substring(2)).getAsJsonArray();
        if (arr.size() < 2 || !arr.get(1).isJsonObject()) return;
        JsonObject ev = arr.get(1).getAsJsonObject();
        String type = str(ev, "type");
        JsonElement msgEl = ev.get("message");
        if (msgEl == null) return;
        if (msgEl.isJsonArray()) {
            for (JsonElement e : msgEl.getAsJsonArray()) if (e.isJsonObject()) handle(type, e.getAsJsonObject());
        } else if (msgEl.isJsonObject()) {
            handle(type, msgEl.getAsJsonObject());
        }
    }

    private void handle(String type, JsonObject m) {
        String name = str(m, "name");
        if (name.isEmpty()) name = str(m, "from");
        switch (type) {
            case "donation" -> {
                String amt = str(m, "formatted_amount");
                if (amt.isEmpty()) amt = str(m, "amount");
                Giveaway.feed("Donation  " + name + "  " + amt, 0xFF31C3A2);
                Giveaway.onDonation(name, str(m, "message"));
            }
            case "subscription", "resub" -> Giveaway.feed("Sub  " + name, 0xFF9146FF);
            case "follow" -> Giveaway.feed("Follow  " + name, 0xFF8A8F98);
            case "bits" -> Giveaway.feed("Bits  " + name + "  " + str(m, "amount"), 0xFF9146FF);
            case "raid" -> Giveaway.feed("Raid  " + name + "  (" + str(m, "raiders") + ")", 0xFFFFB454);
            default -> { }
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }
}
