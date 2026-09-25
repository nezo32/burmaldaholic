package dev.nezo.burmaldaholic.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One HUD row under the chip counter: an optional 8 × 8 icon (a GUI sprite {@code sprite}, else an item {@code icon}),
 * left text, optional right text (drawn after a gap in {@code bone.shade}). Colors are ARGB (the component's own style
 * colors win). {@code pulse} marks an urgent row (the loan in default): its icon animates, the text stays steady
 * (global.md §4.1.1: no blinking).
 */
public record HudLine(@Nullable ItemStack icon, Component left, @Nullable Component right, int color, boolean pulse, @Nullable Identifier sprite) {
	public HudLine(@Nullable ItemStack icon, Component left, @Nullable Component right, int color, boolean pulse) {
		this(icon, left, right, color, pulse, null);
	}

	public static HudLine of(Component left) {
		return new HudLine(null, left, null, 0xFFFFFFFF, false);
	}

	public static HudLine of(Component left, int color) {
		return new HudLine(null, left, null, color, false);
	}

	/** A row with an 8 × 8 GUI sprite icon. */
	public static HudLine sprite(Identifier sprite, Component left, int color) {
		return new HudLine(null, left, null, color, false, sprite);
	}
}
