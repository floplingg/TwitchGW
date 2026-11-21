package dev.flop.giveaway;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlopGiveaway implements ClientModInitializer {
    public static final String MOD_ID = "flopgiveaway";
    public static final Logger LOG = LoggerFactory.getLogger("Flop Giveaway");
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.flopgiveaway.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, category));

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of(MOD_ID, "countdown"), Hud::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(Giveaway.state == Giveaway.State.DRAWING
                            ? new WheelScreen() : new GiveawayScreen());
                }
            }
            Giveaway.tick(client);
        });

        Config c = Config.get();
        if (!c.twitchChannel.isBlank()) TwitchChat.INSTANCE.connect(c.twitchChannel);
        if (!c.streamlabsToken.isBlank()) StreamlabsSocket.INSTANCE.connect(c.streamlabsToken);
        LOG.info("Flop Giveaway ready (press O)");
    }
}
