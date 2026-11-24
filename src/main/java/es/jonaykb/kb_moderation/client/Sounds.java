package es.jonaykb.kb_moderation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Small UI sounds, played only for the local player. */
public final class Sounds {
    private Sounds() {
    }

    public static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void hover() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.08f);
    }

    public static void confirm() {
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.5f);
    }

    public static void danger() {
        play(SoundEvents.ANVIL_LAND, 1.4f, 0.35f);
    }

    public static void select() {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.4f);
    }

    public static void freeze() {
        play(SoundEvents.GLASS_BREAK, 0.6f, 0.6f);
        play(SoundEvents.POWDER_SNOW_STEP, 0.8f, 1.0f);
    }

    public static void unfreeze() {
        play(SoundEvents.POWDER_SNOW_BREAK, 1.2f, 0.8f);
    }

    public static void toast(int kind) {
        switch (kind) {
            case 1 -> play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.7f);
            case 2 -> play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.8f);
            case 3 -> play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.6f);
            default -> play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f, 0.5f);
        }
    }
}
