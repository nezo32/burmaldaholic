package dev.nezo.burmaldaholic.core;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import net.minecraft.sounds.SoundEvent;

/**
 * Core sound events shared by all modules ({@code subtitles.burmaldaholic.<name>}, STRINGS.md "sounds").
 * They map to vanilla sound events in {@code src/main/sounds/core/sounds.json} (no .ogg files needed; a
 * resource pack can replace them).
 */
public final class CoreSounds {
	public static SoundEvent CHIP_PLACE;
	public static SoundEvent CARD_DEAL;
	public static SoundEvent CARD_SHUFFLE;
	public static SoundEvent SLOT_SPIN;
	public static SoundEvent WIN;
	public static SoundEvent LOSE;
	public static SoundEvent WHEEL_TICK;
	public static SoundEvent SCRATCH;
	public static SoundEvent COLLECTOR_KNOCK;

	private CoreSounds() {}

	static void register(ModuleContext ctx) {
		CHIP_PLACE = ctx.registry().sound("chip_place");
		CARD_DEAL = ctx.registry().sound("card_deal");
		CARD_SHUFFLE = ctx.registry().sound("card_shuffle");
		SLOT_SPIN = ctx.registry().sound("slot_spin");
		WIN = ctx.registry().sound("win");
		LOSE = ctx.registry().sound("lose");
		WHEEL_TICK = ctx.registry().sound("wheel_tick");
		SCRATCH = ctx.registry().sound("scratch");
		COLLECTOR_KNOCK = ctx.registry().sound("collector_knock");
	}
}
