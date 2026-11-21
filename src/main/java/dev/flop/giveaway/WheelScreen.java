package dev.flop.giveaway;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/** Spinning wheel that picks the winner(s) when the countdown ends. */
public final class WheelScreen extends Screen {
    private record Btn(int x, int y, int w, int h, Runnable action) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Btn> btns = new ArrayList<>();
    private int lastSlice = -1;
    private long lastTickSound;
    private long copiedAt;

    public WheelScreen() { super(Text.literal("Giveaway Wheel")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xB8070910);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        btns.clear();
        Giveaway.Draw d = Giveaway.draw;
        long now = System.currentTimeMillis();
        int acc = Ui.accent();

        // title
        Ui.scaledCenter(c, Giveaway.prize.toUpperCase(), width / 2f, 10, 1.6f, Ui.TEXT, true);
        String sub = "GIVEAWAY  ·  " + Giveaway.entries.size() + " entries";
        if (d != null) sub += "  ·  winner " + Math.min(Giveaway.winnersWanted, d.winners.size() + (d.phase == Giveaway.Draw.Phase.SPINNING ? 1 : 0))
                + " of " + Giveaway.winnersWanted;
        Ui.center(c, sub, width / 2, 27, acc);

        if (d == null || d.slices.isEmpty()) {
            Ui.center(c, "No entries", width / 2, height / 2, Ui.MUTED);
            closeButton(c, mx, my, width / 2 - 30, height / 2 + 20);
            return;
        }

        int R = Math.max(40, Math.min((height - 100) / 2, 125));
        int cx = width / 2, cy = 42 + R;
        double rot = d.rotation(now);
        boolean reveal = d.phase != Giveaway.Draw.Phase.SPINNING;

        // tick sound each time a slice passes the pointer
        int n = d.slices.size();
        double seg = Giveaway.TAU / n;
        double a = ((-Math.PI / 2 - rot) % Giveaway.TAU + Giveaway.TAU) % Giveaway.TAU;
        int under = Math.min(n - 1, (int) (a / seg));
        if (d.phase == Giveaway.Draw.Phase.SPINNING && under != lastSlice && now - lastTickSound > 45 && n > 1) {
            Sounds.wheelTick();
            lastTickSound = now;
        }
        lastSlice = under;

        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 140.0));
        drawWheel(c, cx, cy, R, d, rot, reveal ? d.winnerSlice : -1, pulse);
        drawPointer(c, cx, cy - R - 2, reveal ? Ui.mix(acc, 0xFFFFFFFF, pulse * 0.5f) : acc);

        // winners side list
        if (!d.winners.isEmpty() && width - (cx + R) > 130) {
            int lx = cx + R + 22, ly = cy - R + 4, lw = Math.min(150, width - lx - 10);
            int lh = 22 + d.winners.size() * 12;
            Ui.card(c, lx, ly, lw, lh, 8, 0xE6101218, Ui.LINE);
            Ui.text(c, "WINNERS", lx + 8, ly + 7, Ui.FAINT);
            for (int i = 0; i < d.winners.size(); i++) {
                Ui.text(c, (i + 1) + ".", lx + 8, ly + 19 + i * 12, Ui.FAINT);
                Ui.text(c, Ui.fit(d.winners.get(i).ign(), lw - 30), lx + 22, ly + 19 + i * 12, 0xFFFFD45A);
            }
        }

        // overlays go on a new layer so they cover the wheel's slice labels
        c.createNewRootLayer();

        if (d.phase == Giveaway.Draw.Phase.REVEAL && d.current != null) {
            float t = Math.min(1f, (now - d.phaseAt) / 250f);
            int w = 200, h = 66;
            int x = cx - w / 2, y = cy - h / 2;
            Ui.card(c, x - 2, y - 2, w + 4, h + 4, 12, Ui.alpha(acc, 0.35f * t), 0);
            Ui.card(c, x, y, w, h, 10, Ui.alpha(0xF20E1016, t), Ui.alpha(acc, t));
            Ui.center(c, "WINNER", cx, y + 9, Ui.alpha(acc, t));
            Ui.scaledCenter(c, Ui.fit(d.current.ign(), 95), cx, y + 22, 2f, Ui.alpha(0xFFFFD45A, t), true);
            Ui.center(c, "via " + d.current.viewer() + (d.current.source().equals("Twitch") ? "" : "  (donation)"), cx, y + 47, Ui.alpha(Ui.MUTED, t));
        }

        int by = Math.min(height - 22, cy + R + 10);
        if (d.phase == Giveaway.Draw.Phase.FINISHED) {
            int bw = 72, gap = 6, total = bw * 3 + gap * 2, bx = cx - total / 2;
            boolean canReroll = !d.pool.isEmpty() && !d.winners.isEmpty();
            button(c, bx, by, bw, "Reroll last", false, canReroll, mx, my, Giveaway::rerollLast);
            button(c, bx + bw + gap, by, bw, now - copiedAt < 1500 ? "Copied!" : "Copy names", false, true, mx, my, () -> {
                StringBuilder sb = new StringBuilder();
                for (Giveaway.Entry e : d.winners) { if (sb.length() > 0) sb.append(", "); sb.append(e.ign()); }
                client.keyboard.setClipboard(sb.toString());
                copiedAt = System.currentTimeMillis();
            });
            button(c, bx + (bw + gap) * 2, by, bw, "Done", true, true, mx, my, this::close);
        } else {
            Ui.center(c, d.phase == Giveaway.Draw.Phase.SPINNING ? "Spinning..." : "", cx, by + 4, Ui.FAINT);
        }
    }

    // ------------------------------------------------------------------ wheel

    private void drawWheel(DrawContext c, int cx, int cy, int R, Giveaway.Draw d, double rot, int highlight, float pulse) {
        List<Giveaway.Entry> slices = d.slices;
        int n = slices.size();
        double seg = Giveaway.TAU / n;
        int acc = Ui.accent();
        float[] hsv = Ui.hsv(acc);
        boolean grey = hsv[1] < 0.15f; // white accent -> use a soft multi-hue palette
        int[] cols = new int[n];
        for (int i = 0; i < n; i++) {
            float hue = grey ? i / (float) n : hsv[0] + (i / (float) n) * 0.22f - 0.11f;
            float sat = grey ? 0.45f : Math.max(0.35f, hsv[1] * 0.85f);
            float val = (i % 2 == 0) ? 0.92f : 0.62f;
            int col = Ui.fromHsv(hue, sat, val);
            if (highlight >= 0) col = i == highlight ? Ui.mix(col, 0xFFFFFFFF, 0.15f + 0.25f * pulse) : Ui.mix(col, 0xFF0A0C12, 0.6f);
            cols[i] = col;
        }
        int rim = 0xFF0B0D12, ring = Ui.alpha(acc, 0.9f), hub = 0xFF11141C;
        int hubR = Math.max(12, R / 7);

        // glow
        Ui.circle(c, cx, cy, R + 6, Ui.alpha(acc, 0.10f));
        Ui.circle(c, cx, cy, R + 3, Ui.alpha(acc, 0.18f));

        for (int y = -R; y < R; y++) {
            double fy = y + 0.5;
            int half = (int) Math.round(Math.sqrt((double) R * R - fy * fy));
            if (half <= 0) continue;
            int runStart = -half, runCol = 0;
            boolean has = false;
            for (int x = -half; x < half; x++) {
                double fx = x + 0.5;
                double dist = Math.sqrt(fx * fx + fy * fy);
                int col;
                if (dist > R - 3) col = rim;
                else if (dist < hubR) col = hub;
                else if (dist < hubR + 2) col = ring;
                else {
                    double ang = ((Math.atan2(fy, fx) - rot) % Giveaway.TAU + Giveaway.TAU) % Giveaway.TAU;
                    int idx = Math.min(n - 1, (int) (ang / seg));
                    col = cols[idx];
                }
                if (!has) { runStart = x; runCol = col; has = true; }
                else if (col != runCol) {
                    c.fill(cx + runStart, cy + y, cx + x, cy + y + 1, runCol);
                    runStart = x;
                    runCol = col;
                }
            }
            if (has) c.fill(cx + runStart, cy + y, cx + half, cy + y + 1, runCol);
        }

        // names along each slice
        if (n <= Giveaway.MAX_SLICES) {
            Matrix3x2fStack m = c.getMatrices();
            int maxW = R - hubR - 12;
            float scale = n <= 8 ? 1f : n <= 16 ? 0.85f : 0.7f;
            for (int i = 0; i < n; i++) {
                double mid = rot + (i + 0.5) * seg;
                m.pushMatrix();
                m.translate(cx, cy);
                m.rotate((float) mid);
                m.translate(hubR + 6, -4 * scale);
                m.scale(scale, scale);
                String name = Ui.fit(slices.get(i).ign(), (int) (maxW / scale));
                int col = highlight < 0 || i == highlight ? 0xFFFFFFFF : 0x88FFFFFF;
                c.drawText(textRenderer, name, 0, 0, col, true);
                m.popMatrix();
            }
        }
        Ui.center(c, "FLOP", cx, cy - 4, acc);
    }

    private void drawPointer(DrawContext c, int cx, int tipY, int color) {
        // downward triangle with tip at tipY+12
        for (int i = 0; i < 14; i++) {
            int hw = 9 - i * 9 / 14;
            c.fill(cx - hw - 1, tipY - 2 + i, cx + hw + 1, tipY - 1 + i, 0xFF0B0D12);
        }
        for (int i = 0; i < 12; i++) {
            int hw = 8 - i * 8 / 12;
            c.fill(cx - hw, tipY - 1 + i, cx + hw, tipY + i, color);
        }
    }

    // ------------------------------------------------------------------ ui

    private void button(DrawContext c, int x, int y, int w, String label, boolean primary, boolean enabled, int mx, int my, Runnable r) {
        int h = 16;
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int acc = Ui.accent();
        int bg = !enabled ? 0x0FFFFFFF : primary ? (hov ? Ui.mix(acc, 0xFFFFFFFF, 0.18f) : acc) : (hov ? 0x33FFFFFF : 0x1AFFFFFF);
        Ui.round(c, x, y, w, h, 6, bg);
        Ui.center(c, label, x + w / 2, y + 4, !enabled ? Ui.FAINT : primary ? 0xFF0B0D12 : Ui.TEXT);
        if (enabled) btns.add(new Btn(x, y, w, h, r));
    }

    private void closeButton(DrawContext c, int mx, int my, int x, int y) {
        button(c, x, y, 60, "Close", true, true, mx, my, this::close);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            for (Btn b : btns) {
                if (b.hit(click.x(), click.y())) {
                    Sounds.click();
                    b.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }
}
