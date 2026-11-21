package dev.flop.giveaway;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Twitch chat anonymously over Twitch's IRC websocket (read-only "justinfan" login),
 * so no OAuth token is needed. This is the same chat Streamlabs shows in its chat box.
 */
public final class TwitchChat extends WsClient {
    public static final TwitchChat INSTANCE = new TwitchChat();
    private volatile String channel = "";

    public String channel() { return channel; }

    public static String clean(String raw) {
        String c = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int i = c.lastIndexOf("twitch.tv/");
        if (i >= 0) c = c.substring(i + 10);
        c = c.replace("#", "").replace("@", "");
        int slash = c.indexOf('/');
        if (slash >= 0) c = c.substring(0, slash);
        return c.replaceAll("[^a-z0-9_]", "");
    }

    public void connect(String raw) {
        channel = clean(raw);
        if (channel.isEmpty()) { stop(); status = "Enter your channel name"; return; }
        start();
    }

    @Override protected URI uri() { return URI.create("wss://irc-ws.chat.twitch.tv:443"); }

    @Override protected void opened() {
        send("CAP REQ :twitch.tv/tags twitch.tv/commands");
        send("PASS SCHMOOPIIE");
        send("NICK justinfan" + (10000 + Giveaway.RNG.nextInt(89999)));
        send("JOIN #" + channel);
        status = "Joining #" + channel + "...";
    }

    @Override protected void message(String text) {
        for (String line : text.split("\r\n")) if (!line.isEmpty()) line(line);
    }

    private void line(String line) {
        if (line.startsWith("PING")) { send("PONG" + line.substring(4)); return; }

        Map<String, String> tags = Map.of();
        String rest = line;
        if (rest.startsWith("@")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) return;
            tags = tags(rest.substring(1, sp));
            rest = rest.substring(sp + 1);
        }
        String prefix = "";
        if (rest.startsWith(":")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) return;
            prefix = rest.substring(1, sp);
            rest = rest.substring(sp + 1);
        }
        int sp = rest.indexOf(' ');
        String cmd = sp < 0 ? rest : rest.substring(0, sp);
        String params = sp < 0 ? "" : rest.substring(sp + 1);

        switch (cmd) {
            case "PRIVMSG" -> {
                int c = params.indexOf(" :");
                if (c < 0) return;
                String msg = params.substring(c + 2);
                if (msg.startsWith("\u0001")) return; // /me actions
                int bang = prefix.indexOf('!');
                String login = bang > 0 ? prefix.substring(0, bang) : prefix;
                String display = tags.getOrDefault("display-name", "");
                if (display.isEmpty()) display = login;
                String uid = tags.getOrDefault("user-id", login);
                String badges = tags.getOrDefault("badges", "");
                boolean supporter = "1".equals(tags.get("subscriber")) || "1".equals(tags.get("mod"))
                        || badges.contains("subscriber/") || badges.contains("founder/")
                        || badges.contains("broadcaster/") || badges.contains("moderator/")
                        || badges.contains("vip/");
                Giveaway.onChat("tw:" + uid, display, msg, supporter);
            }
            case "ROOMSTATE", "366" -> {
                if (!connected) Giveaway.feed("Twitch chat connected: #" + channel, 0xFF9146FF);
                connected = true;
                status = "Live in #" + channel;
            }
            case "NOTICE" -> {
                int c = params.indexOf(" :");
                if (c >= 0) status = params.substring(c + 2);
            }
            case "RECONNECT" -> reconnect();
            default -> { }
        }
    }

    private static Map<String, String> tags(String raw) {
        Map<String, String> m = new HashMap<>();
        for (String kv : raw.split(";")) {
            int eq = kv.indexOf('=');
            if (eq > 0) m.put(kv.substring(0, eq), kv.substring(eq + 1).replace("\\s", " "));
        }
        return m;
    }
}
