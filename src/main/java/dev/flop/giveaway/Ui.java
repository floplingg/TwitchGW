package dev.flop.giveaway;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

/** Rounded, flat drawing helpers in the Flop Visuals style. */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xEE0E1016;
    public static final int CARD = 0xFF161922;
    public static final int CARD_HI = 0xFF1C2030;
    public static final int LINE = 0x22FFFFFF;
    public static final int TEXT = 0xFFF2F4F8;
    public static final int MUTED = 0xFF8A8F98;
    public static final int FAINT = 0xFF5A5F6A;

    public static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }
    public static int accent() { return Config.get().accent; }

    /** Filled rounded rectangle, scanline corners. */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Rounded rect with a 1px border. */
    public static void card(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        round(c, x, y, w, h, r, border);
        round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void circle(DrawContext c, int cx, int cy, int r, int color) {
        for (int y = -r; y < r; y++) {
            double fy = y + 0.5;
            int half = (int) Math.round(Math.sqrt(r * r - fy * fy));
            if (half > 0) c.fill(cx - half, cy + y, cx + half, cy + y + 1, color);
        }
    }

    public static void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(tr(), s, x, y, color, false);
    }

    public static void shadow(DrawContext c, String s, int x, int y, int color) {
        c.drawText(tr(), s, x, y, color, true);
    }

    public static void center(DrawContext c, String s, int cx, int y, int color) {
        c.drawText(tr(), s, cx - tr().getWidth(s) / 2, y, color, false);
    }

    public static void right(DrawContext c, String s, int rx, int y, int color) {
        c.drawText(tr(), s, rx - tr().getWidth(s), y, color, false);
    }

    public static void scaled(DrawContext c, String s, float x, float y, float scale, int color, boolean shadow) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(tr(), s, 0, 0, color, shadow);
        m.popMatrix();
    }

    public static void scaledCenter(DrawContext c, String s, float cx, float y, float scale, int color, boolean shadow) {
        scaled(c, s, cx - tr().getWidth(s) * scale / 2f, y, scale, color, shadow);
    }

    public static String fit(String s, int w) {
        if (tr().getWidth(s) <= w) return s;
        return tr().trimToWidth(s, Math.max(0, w - tr().getWidth("..."))) + "...";
    }

    public static void toggle(DrawContext c, int x, int y, boolean on) {
        round(c, x, y, 18, 10, 5, on ? accent() : 0x33FFFFFF);
        round(c, on ? x + 9 : x + 1, y + 1, 8, 8, 4, 0xFFFFFFFF);
    }

    public static void dot(DrawContext c, int x, int y, int color) {
        round(c, x, y, 6, 6, 3, color);
    }

    // ------------------------------------------------------------ color

    public static int alpha(int color, float a) {
        int al = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static float[] hsv(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static int fromHsv(float h, float s, float v) {
        h = ((h % 1) + 1) % 1;
        float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
        float r, g, b;
        int seg = (int) (h * 6);
        switch (seg) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    public static String time(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
