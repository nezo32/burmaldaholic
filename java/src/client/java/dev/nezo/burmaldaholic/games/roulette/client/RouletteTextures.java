package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * PHASE2B D8: the big roulette head sheets (222 frames: 5.5 MB at 104, 10.6 MB at 144 in VRAM) are plain sheet
 * textures, loaded lazily by the texture manager the first time a wheel of that size draws, and released when the
 * roulette screen closes; the in-world board uses the small entity textures only.
 */
final class RouletteTextures {
	private static final Identifier[] SHEETS = {TableGfx.sheet("roulette/wheel_head_m104"), TableGfx.sheet("roulette/wheel_head_m144")};

	private RouletteTextures() {}

	/** Frees the head sheets (they reload on the next draw). */
	static void releaseScreenSheets() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		var tm = mc.getTextureManager();
		for (Identifier id : SHEETS) tm.release(id);
	}
}
