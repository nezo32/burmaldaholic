package dev.nezo.burmaldaholic.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One HUD row: optional 8×8 item icon, left text, optional right-aligned text. Colors are ARGB
 * (the component's own style colors win). {@code pulse} blinks the row (e.g. loan in default).
 */
public record HudLine(@Nullable ItemStack icon, Component left, @Nullable Component right, int color, boolean pulse) {
	public static HudLine of(Component left) {
		return new HudLine(null, left, null, 0xFFFFFFFF, false);
	}

	public static HudLine of(Component left, int color) {
		return new HudLine(null, left, null, color, false);
	}
}
