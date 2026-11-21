package dev.flop.giveaway;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** On-screen countdown card + live entry toasts (visible on stream). */
public final class Hud {
    private Hud() {}

    public static void render(DrawContext c, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Config.get().showHud || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof WheelScreen) return;

        Giveaway.State st = Giveaway.state;
        int sw = c.getScaledWindowWidth();
        long now = System.currentTimeMillis();
        int acc = Ui.accent();

        if (st == Giveaway.State.RUNNING) {
            int w = 176, h = 62, x = (sw - w) / 2, y = 6;
            long left = Giveaway.endAt - now;
            long total = Math.max(1, Giveaway.endAt - Giveaway.startAt);
            boolean hurry = left <= 10_000;
            float pulse = hurry ? (float) (0.5 + 0.5 * Math.sin(now / 120.0)) : 0f;

            Ui.card(c, x, y, w, h, 9, 0xE60E1016, hurry ? Ui.mix(Ui.LINE, 0xFFFF6B6B, pulse) : Ui.alpha(acc, 0.55f));
            Ui.text(c, "GIVEAWAY", x + 10, y + 8, acc);
            Ui.right(c, Ui.fit(Giveaway.prize, 100), x + w - 10, y + 8, Ui.TEXT);

            int timeCol = hurry ? Ui.mix(Ui.TEXT, 0xFFFF6B6B, pulse) : Ui.TEXT;
            Ui.scaledCenter(c, Ui.time(left), x + w / 2f, y + 19, 2f, timeCol, true);

            float p = Math.max(0, Math.min(1, left / (float) total));
            Ui.round(c, x + 10, y + 39, w - 20, 4, 2, 0x22FFFFFF);
            Ui.round(c, x + 10, y + 39, Math.max(4, (int) ((w - 20) * p)), 4, 2, acc);

            String bottom;
            int bc;
            if (now - Giveaway.lastJoinAt < 2500) {
                bottom = "+ " + Giveaway.lastJoin + " entered";
                bc = acc;
            } else {
                bottom = "Type !<IGN> in chat";
                bc = Ui.MUTED;
            }
            Ui.text(c, Ui.fit(bottom, w - 70), x + 10, y + 48, bc);
            Ui.right(c, Giveaway.entries.size() + " entries", x + w - 10, y + 48, Ui.TEXT);
        } else if (st == Giveaway.State.DRAWING && !(mc.currentScreen instanceof GiveawayScreen)) {
            String s = "Drawing winner...  press " + FlopGiveaway.openKey.getBoundKeyLocalizedText().getString();
            int w = Ui.tr().getWidth(s) + 20, x = (sw - w) / 2;
            Ui.card(c, x, 6, w, 20, 8, 0xE60E1016, Ui.alpha(acc, 0.6f));
            Ui.center(c, s, sw / 2, 12, Ui.TEXT);
        }
    }
}
