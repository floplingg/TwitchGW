package dev.flop.giveaway;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public final class Sounds {
    private Sounds() {}

    private static void play(SoundEvent e, float pitch, float volume) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.getSoundManager().play(PositionedSoundInstance.master(e, pitch, volume)));
    }

    public static void click() { play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.35f); }
    public static void tick(float pitch) { play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), pitch, 0.6f); }
    public static void wheelTick() { play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.7f, 0.35f); }
    public static void win() {
        play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.5f, 0.7f);
    }
}
