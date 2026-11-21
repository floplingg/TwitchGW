package dev.flop.giveaway;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Main menu (press O). */
public final class GiveawayScreen extends Screen {
    private static final int[] PRESETS = {60, 180, 300, 600};

    private record Btn(int x, int y, int w, int h, Runnable action) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Btn> btns = new ArrayList<>();
    private TextFieldWidget prizeField, channelField, tokenField;
    private boolean editingToken;
    private int scroll;

    // layout
    private int px, py, pw, ph, lx, lw, rx, rw, c1y, c2y;
    private static final int C1H = 118, C2H = 96;

    public GiveawayScreen() { super(Text.literal("Flop Giveaway")); }

    private void layout() {
        pw = Math.min(width - 12, 460);
        ph = Math.min(height - 12, 256);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        lx = px + 10;
        lw = Math.min(232, (pw - 30) * 55 / 100);
        rx = lx + lw + 10;
        rw = px + pw - 10 - rx;
        c1y = py + 30;
        c2y = c1y + C1H + 6;
    }

    @Override
    protected void init() {
        layout();
        Config cfg = Config.get();
        int ix = lx + 10, iw = lw - 20;

        prizeField = field(ix + 52, c1y + 20, iw - 52, 32, cfg.prize);
        prizeField.setChangedListener(s -> Config.get().prize = s);

        channelField = field(ix + 64, c2y + 20, iw - 64 - 62, 40, cfg.twitchChannel);
        channelField.setChangedListener(s -> Config.get().twitchChannel = s);

        tokenField = field(ix + 64, c2y + 48, iw - 64 - 62 - 0, 400, cfg.streamlabsToken);
        tokenField.setChangedListener(s -> Config.get().streamlabsToken = s);
        tokenField.visible = editingToken;
    }

    private TextFieldWidget field(int x, int y, int w, int max, String value) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x + 5, y + 3, w - 10, 10, Text.empty());
        f.setDrawsBackground(false);
        f.setMaxLength(max);
        f.setText(value);
        f.setEditableColor(Ui.TEXT);
        addDrawableChild(f);
        return f;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        Config.get().clamp();
        Config.save();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        btns.clear();
        c.fill(0, 0, width, height, 0x88000000);
        int acc = Ui.accent();

        Ui.card(c, px, py, pw, ph, 12, Ui.BG, Ui.LINE);

        // header
        Ui.round(c, px + 12, py + 10, 4, 10, 2, acc);
        Ui.scaled(c, "Flop", px + 21, py + 10, 1.15f, acc, false);
        Ui.scaled(c, "Giveaway", px + 21 + textRenderer.getWidth("Flop") * 1.15f + 4, py + 10, 1.15f, Ui.TEXT, false);
        int sx = px + pw - 12 - Config.ACCENTS.length * 12;
        for (int i = 0; i < Config.ACCENTS.length; i++) {
            int col = Config.ACCENTS[i];
            int x = sx + i * 12, y = py + 11;
            if (col == acc) Ui.round(c, x - 2, y - 2, 12, 12, 6, 0xFFFFFFFF);
            Ui.round(c, x, y, 8, 8, 4, col);
            btns.add(new Btn(x - 2, y - 2, 12, 12, () -> Config.get().accent = col));
        }
        String hudLbl = "HUD";
        int hx = sx - 34;
        Ui.text(c, hudLbl, hx - textRenderer.getWidth(hudLbl) + 0, py + 12, Ui.MUTED);
        Ui.toggle(c, hx + 4, py + 11, Config.get().showHud);
        btns.add(new Btn(hx - 20, py + 9, 44, 14, () -> Config.get().showHud = !Config.get().showHud));

        drawSetup(c, mx, my);
        drawConnections(c, mx, my);
        drawEntries(c, mx, my);
    }

    private void drawSetup(DrawContext c, int mx, int my) {
        Config cfg = Config.get();
        boolean locked = Giveaway.state == Giveaway.State.RUNNING || Giveaway.state == Giveaway.State.DRAWING;
        int x = lx, y = c1y, ix = lx + 10, iw = lw - 20;
        Ui.card(c, x, y, lw, C1H, 9, Ui.CARD, Ui.LINE);
        Ui.text(c, "GIVEAWAY", ix, y + 8, Ui.FAINT);
        if (locked) Ui.right(c, "locked while live", ix + iw, y + 8, Ui.FAINT);

        // prize
        Ui.text(c, "Prize", ix, y + 23, Ui.MUTED);
        fieldBox(c, prizeField, ix + 52, y + 20, iw - 52);
        prizeField.setEditable(!locked);

        // duration
        int ry = y + 39;
        Ui.text(c, "Duration", ix, ry + 3, Ui.MUTED);
        int bx = ix + 52;
        small(c, bx, ry, 14, "-", mx, my, !locked, () -> cfg.durationSec = Math.max(30, cfg.durationSec - (cfg.durationSec > 120 ? 60 : 30)));
        Ui.center(c, Ui.time(cfg.durationSec * 1000L), bx + 14 + 17, ry + 3, Ui.TEXT);
        small(c, bx + 48, ry, 14, "+", mx, my, !locked, () -> cfg.durationSec = Math.min(3600, cfg.durationSec + (cfg.durationSec >= 120 ? 60 : 30)));
        int chipX = bx + 66, chipW = (ix + iw - chipX - 6) / 4;
        for (int i = 0; i < PRESETS.length; i++) {
            int sec = PRESETS[i];
            boolean sel = cfg.durationSec == sec;
            chip(c, chipX + i * (chipW + 2), ry, chipW, (sec / 60) + "m", sel, mx, my, !locked, () -> cfg.durationSec = sec);
        }

        // winners + subs only
        ry = y + 57;
        Ui.text(c, "Winners", ix, ry + 3, Ui.MUTED);
        small(c, bx, ry, 14, "-", mx, my, !locked, () -> cfg.winners = Math.max(1, cfg.winners - 1));
        Ui.center(c, String.valueOf(cfg.winners), bx + 14 + 17, ry + 3, Ui.TEXT);
        small(c, bx + 48, ry, 14, "+", mx, my, !locked, () -> cfg.winners = Math.min(20, cfg.winners + 1));
        Ui.text(c, "Subs only", ix + iw - 18 - 6 - textRenderer.getWidth("Subs only"), ry + 3, Ui.MUTED);
        Ui.toggle(c, ix + iw - 18, ry + 2, cfg.subsOnly);
        btns.add(new Btn(ix + iw - 80, ry, 80, 14, () -> cfg.subsOnly = !cfg.subsOnly));

        // hint
        Ui.text(c, "Chat", ix, y + 78, Ui.FAINT);
        int hx = ix + textRenderer.getWidth("Chat ");
        Ui.text(c, "!<IGN>", hx, y + 78, Ui.accent());
        Ui.text(c, "to enter · 1 per viewer", hx + textRenderer.getWidth("!<IGN> "), y + 78, Ui.FAINT);

        // main action
        int by = y + 92, bh = 18;
        switch (Giveaway.state) {
            case IDLE -> big(c, ix, by, iw, bh, "Start giveaway  ·  " + Ui.time(cfg.durationSec * 1000L), true, mx, my, this::startGiveaway);
            case RUNNING -> {
                int half = (iw - 4) / 2;
                big(c, ix, by, half, bh, "End now  " + Ui.time(Giveaway.endAt - System.currentTimeMillis()), true, mx, my, Giveaway::endNow);
                big(c, ix + half + 4, by, iw - half - 4, bh, "Cancel", false, mx, my, Giveaway::cancel);
            }
            case DRAWING -> big(c, ix, by, iw, bh, "Open wheel", true, mx, my, () -> client.setScreen(new WheelScreen()));
            case DONE -> {
                int half = (iw - 4) / 2;
                big(c, ix, by, half, bh, "New giveaway", true, mx, my, this::startGiveaway);
                big(c, ix + half + 4, by, iw - half - 4, bh, "Winners", false, mx, my, () -> {
                    if (Giveaway.draw != null) client.setScreen(new WheelScreen());
                });
            }
        }
    }

    private void startGiveaway() {
        Config.get().clamp();
        Config.save();
        if (!TwitchChat.INSTANCE.connected && !TwitchChat.INSTANCE.isWanted() && !Config.get().twitchChannel.isBlank()) {
            TwitchChat.INSTANCE.connect(Config.get().twitchChannel);
        }
        scroll = 0;
        Giveaway.start();
    }

    private void drawConnections(DrawContext c, int mx, int my) {
        Config cfg = Config.get();
        int x = lx, y = c2y, ix = lx + 10, iw = lw - 20;
        int h = Math.min(C2H, py + ph - 10 - y);
        Ui.card(c, x, y, lw, h, 9, Ui.CARD, Ui.LINE);
        Ui.text(c, "CONNECTIONS", ix, y + 8, Ui.FAINT);

        // Twitch
        TwitchChat tw = TwitchChat.INSTANCE;
        int row = y + 20;
        Ui.dot(c, ix, row + 4, tw.connected ? 0xFF4BE08A : tw.isWanted() ? 0xFFFFC04D : 0xFF5A5F6A);
        Ui.text(c, "Twitch", ix + 10, row + 3, Ui.TEXT);
        fieldBox(c, channelField, ix + 64, row, iw - 64 - 62);
        boolean twOn = tw.isWanted();
        big(c, ix + iw - 58, row, 58, 14, twOn ? "Disconnect" : "Connect", !twOn, mx, my, () -> {
            if (tw.isWanted()) tw.stop(); else { tw.connect(channelField.getText()); Config.save(); }
        });
        Ui.text(c, Ui.fit(tw.status, iw - 64), ix + 64, row + 17, Ui.FAINT);

        // Streamlabs
        StreamlabsSocket sl = StreamlabsSocket.INSTANCE;
        row = y + 48;
        Ui.dot(c, ix, row + 4, sl.connected ? 0xFF31C3A2 : sl.isWanted() ? 0xFFFFC04D : 0xFF5A5F6A);
        Ui.text(c, "Streamlabs", ix + 10, row + 3, Ui.TEXT);
        int fw = iw - 64 - 62;
        if (editingToken) {
            fieldBox(c, tokenField, ix + 64, row, fw - 30);
            tokenField.setWidth(fw - 30 - 10);
            chip(c, ix + 64 + fw - 28, row, 26, "Done", false, mx, my, true, () -> setEditingToken(false));
        } else {
            Ui.round(c, ix + 64, row, fw, 14, 5, 0x14FFFFFF);
            String t = cfg.streamlabsToken.isBlank() ? "No token" : "Token  ••••••" ;
            Ui.text(c, t, ix + 69, row + 3, cfg.streamlabsToken.isBlank() ? Ui.FAINT : Ui.MUTED);
            chip(c, ix + 64 + fw - 28, row, 26, "Edit", false, mx, my, true, () -> setEditingToken(true));
        }
        boolean slOn = sl.isWanted();
        big(c, ix + iw - 58, row, 58, 14, slOn ? "Disconnect" : "Connect", !slOn, mx, my, () -> {
            if (sl.isWanted()) sl.stop(); else { setEditingToken(false); sl.connect(Config.get().streamlabsToken); Config.save(); }
        });
        Ui.text(c, Ui.fit(sl.status, iw - 64), ix + 64, row + 17, Ui.FAINT);

        // donation entries toggle
        row = y + 77;
        if (row + 12 < y + h) {
            Ui.toggle(c, ix, row, cfg.donationEntries);
            Ui.text(c, "Donation msgs with !<IGN> enter too", ix + 24, row + 1, Ui.MUTED);
            btns.add(new Btn(ix, row - 2, iw, 14, () -> cfg.donationEntries = !cfg.donationEntries));
        }
    }

    private void setEditingToken(boolean v) {
        editingToken = v;
        tokenField.visible = v;
        if (v) setFocused(tokenField); else if (getFocused() == tokenField) setFocused(null);
    }

    private void drawEntries(DrawContext c, int mx, int my) {
        int x = rx, y = c1y, h = py + ph - 10 - y, ix = rx + 10, iw = rw - 20;
        Ui.card(c, x, y, rw, h, 9, Ui.CARD, Ui.LINE);
        List<Giveaway.Entry> list = Giveaway.entries;
        Ui.text(c, "ENTRIES", ix, y + 8, Ui.FAINT);
        String n = String.valueOf(list.size());
        int nw = textRenderer.getWidth(n) + 10;
        Ui.round(c, ix + textRenderer.getWidth("ENTRIES") + 6, y + 6, nw, 11, 5, Ui.alpha(Ui.accent(), 0.25f));
        Ui.text(c, n, ix + textRenderer.getWidth("ENTRIES") + 11, y + 8, Ui.accent());

        // live status pill
        Giveaway.State st = Giveaway.state;
        String stText = switch (st) {
            case IDLE -> "Not running";
            case RUNNING -> "LIVE";
            case DRAWING -> "Drawing";
            case DONE -> "Ended";
        };
        int stCol = st == Giveaway.State.RUNNING ? 0xFFFF5A5A : Ui.MUTED;
        int stW = textRenderer.getWidth(stText) + 16;
        Ui.round(c, ix + iw - stW, y + 6, stW, 11, 5, Ui.alpha(stCol, 0.18f));
        Ui.dot(c, ix + iw - stW + 4, y + 8, st == Giveaway.State.RUNNING
                ? Ui.alpha(stCol, (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 200.0))) : stCol);
        Ui.text(c, stText, ix + iw - stW + 12, y + 8, stCol);

        // list
        int top = y + 22, bottom = y + h - 44, rowH = 13;
        int visible = Math.max(1, (bottom - top) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
        if (list.isEmpty()) {
            Ui.center(c, st == Giveaway.State.RUNNING ? "Waiting for !<IGN> in chat..." : "Start a giveaway to collect entries",
                    ix + iw / 2, top + (bottom - top) / 2 - 4, Ui.FAINT);
        }
        for (int i = 0; i < visible && i + scroll < list.size(); i++) {
            int idx = i + scroll;
            Giveaway.Entry e = list.get(idx);
            int ry = top + i * rowH;
            boolean hov = mx >= ix && mx < ix + iw && my >= ry && my < ry + rowH;
            if (hov) Ui.round(c, ix - 4, ry, iw + 8, rowH, 4, 0x10FFFFFF);
            Ui.text(c, String.valueOf(idx + 1), ix, ry + 3, Ui.FAINT);
            int nx = ix + 20;
            Ui.text(c, e.ign(), nx, ry + 3, Ui.TEXT);
            int after = nx + textRenderer.getWidth(e.ign()) + 5;
            if (!e.source().equals("Twitch")) {
                Ui.round(c, after, ry + 2, 26, 9, 4, Ui.alpha(0xFF31C3A2, 0.25f));
                Ui.text(c, "DON", after + 4, ry + 3, 0xFF31C3A2);
            } else if (e.supporter()) {
                Ui.round(c, after, ry + 2, 22, 9, 4, Ui.alpha(0xFF9146FF, 0.3f));
                Ui.text(c, "SUB", after + 3, ry + 3, 0xFFC9A8FF);
            }
            if (hov && st != Giveaway.State.DRAWING) {
                Ui.right(c, "remove", ix + iw, ry + 3, 0xFFFF6B6B);
                btns.add(new Btn(ix + iw - textRenderer.getWidth("remove"), ry, textRenderer.getWidth("remove"), rowH, () -> Giveaway.remove(e)));
            } else {
                Ui.right(c, Ui.fit(e.viewer(), iw / 3), ix + iw, ry + 3, Ui.FAINT);
            }
        }
        if (list.size() > visible) {
            int trackH = bottom - top;
            int barH = Math.max(12, trackH * visible / list.size());
            int barY = top + (trackH - barH) * scroll / Math.max(1, list.size() - visible);
            Ui.round(c, ix + iw + 5, barY, 2, barH, 1, 0x44FFFFFF);
        }

        // bottom: countdown / feed
        int by = y + h - 40;
        c.fill(ix, by, ix + iw, by + 1, Ui.LINE);
        long now = System.currentTimeMillis();
        if (st == Giveaway.State.RUNNING) {
            long left = Giveaway.endAt - now;
            float p = Math.max(0, Math.min(1, left / (float) Math.max(1, Giveaway.endAt - Giveaway.startAt)));
            Ui.text(c, Ui.fit(Giveaway.prize, iw - 60), ix, by + 7, Ui.TEXT);
            Ui.scaled(c, Ui.time(left), ix + iw - textRenderer.getWidth(Ui.time(left)) * 1.5f, by + 5, 1.5f, Ui.accent(), false);
            Ui.round(c, ix, by + 22, iw, 4, 2, 0x22FFFFFF);
            Ui.round(c, ix, by + 22, Math.max(4, (int) (iw * p)), 4, 2, Ui.accent());
            Ui.text(c, Giveaway.winnersWanted + (Giveaway.winnersWanted == 1 ? " winner" : " winners"), ix, by + 29, Ui.FAINT);
        } else {
            Giveaway.FeedItem f = Giveaway.feed.peekFirst();
            Ui.text(c, "ACTIVITY", ix, by + 7, Ui.FAINT);
            Ui.text(c, f == null ? "Nothing yet" : Ui.fit(f.text(), iw), ix, by + 20, f == null ? Ui.FAINT : f.color());
        }
    }

    // ------------------------------------------------------------------ widgets

    private void fieldBox(DrawContext c, TextFieldWidget f, int x, int y, int w) {
        boolean focused = f.isFocused();
        Ui.card(c, x, y, w, 14, 5, 0xFF0F1118, focused ? Ui.accent() : 0x26FFFFFF);
    }

    private void small(DrawContext c, int x, int y, int s, String label, int mx, int my, boolean enabled, Runnable r) {
        boolean hov = enabled && mx >= x && mx < x + s && my >= y && my < y + s;
        Ui.round(c, x, y, s, s, 5, hov ? 0x33FFFFFF : 0x1AFFFFFF);
        Ui.center(c, label, x + s / 2 + 1, y + 3, enabled ? Ui.TEXT : Ui.FAINT);
        if (enabled) btns.add(new Btn(x, y, s, s, r));
    }

    private void chip(DrawContext c, int x, int y, int w, String label, boolean sel, int mx, int my, boolean enabled, Runnable r) {
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + 14;
        int bg = sel ? Ui.alpha(Ui.accent(), 0.3f) : hov ? 0x33FFFFFF : 0x1AFFFFFF;
        Ui.round(c, x, y, w, 14, 5, bg);
        Ui.center(c, label, x + w / 2, y + 3, sel ? Ui.accent() : enabled ? Ui.TEXT : Ui.FAINT);
        if (enabled) btns.add(new Btn(x, y, w, 14, r));
    }

    private void big(DrawContext c, int x, int y, int w, int h, String label, boolean primary, int mx, int my, Runnable r) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        int acc = Ui.accent();
        int bg = primary ? (hov ? Ui.mix(acc, 0xFFFFFFFF, 0.18f) : acc) : (hov ? 0x33FFFFFF : 0x1AFFFFFF);
        Ui.round(c, x, y, w, h, Math.min(6, h / 2), bg);
        Ui.center(c, Ui.fit(label, w - 6), x + w / 2, y + (h - 8) / 2, primary ? 0xFF0B0D12 : Ui.TEXT);
        btns.add(new Btn(x, y, w, h, r));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        setFocused(null);
        if (click.button() == 0) {
            for (int i = btns.size() - 1; i >= 0; i--) {
                Btn b = btns.get(i);
                if (b.hit(click.x(), click.y())) {
                    Sounds.click();
                    b.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= rx && mx < rx + rw) {
            scroll -= (int) Math.signum(v) * 2;
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }
}
