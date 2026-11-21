package dev.flop.giveaway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;

/** Small auto-reconnecting websocket wrapper (java.net.http, no extra libraries). */
public abstract class WsClient {
    static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "flop-giveaway-net");
        t.setDaemon(true);
        return t;
    });
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public volatile String status = "Offline";
    public volatile boolean connected;
    private volatile boolean wanted;
    private volatile int gen;
    private volatile WebSocket ws;
    private CompletableFuture<?> chain = CompletableFuture.completedFuture(null);

    protected abstract URI uri() throws Exception;
    protected abstract void opened();
    protected abstract void message(String text);

    public boolean isWanted() { return wanted; }

    public synchronized void start() {
        stop();
        wanted = true;
        open();
    }

    public synchronized void stop() {
        wanted = false;
        gen++;
        connected = false;
        status = "Offline";
        WebSocket w = ws;
        ws = null;
        if (w != null) try { w.abort(); } catch (Exception ignored) { }
    }

    private synchronized void open() {
        final int my = ++gen;
        connected = false;
        status = "Connecting...";
        URI u;
        try { u = uri(); } catch (Exception e) { status = e.getMessage(); wanted = false; return; }
        HTTP.newWebSocketBuilder().buildAsync(u, new L(my)).whenComplete((w, err) -> {
            if (err != null && my == gen) {
                status = "Can't connect, retrying...";
                retry(my);
            }
        });
    }

    private void retry(int my) {
        if (!wanted) return;
        EXEC.schedule(() -> {
            synchronized (WsClient.this) {
                if (wanted && my == gen) open();
            }
        }, 5, TimeUnit.SECONDS);
    }

    protected synchronized void send(String s) {
        WebSocket w = ws;
        if (w == null) return;
        chain = chain.handle((a, b) -> null).thenCompose(v -> w.sendText(s, true));
    }

    protected void reconnect() {
        WebSocket w = ws;
        if (w != null) try { w.abort(); } catch (Exception ignored) { }
    }

    private final class L implements WebSocket.Listener {
        private final int my;
        private final StringBuilder buf = new StringBuilder();
        L(int my) { this.my = my; }

        @Override public void onOpen(WebSocket w) {
            synchronized (WsClient.this) {
                if (my != gen) { w.abort(); return; }
                ws = w;
                chain = CompletableFuture.completedFuture(null);
            }
            try { opened(); } catch (Exception e) { FlopGiveaway.LOG.warn("ws open", e); }
            w.request(1);
        }

        @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String s = buf.toString();
                buf.setLength(0);
                if (my == gen) {
                    try { message(s); } catch (Exception e) { FlopGiveaway.LOG.warn("ws message", e); }
                }
            }
            w.request(1);
            return null;
        }

        @Override public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            lost();
            return null;
        }

        @Override public void onError(WebSocket w, Throwable error) { lost(); }

        private void lost() {
            if (my != gen) return;
            connected = false;
            ws = null;
            status = "Disconnected, retrying...";
            retry(my);
        }
    }
}
