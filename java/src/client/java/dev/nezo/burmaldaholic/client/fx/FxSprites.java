package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * GUI sprites of the presentation kit ({@code textures/gui/sprites/burmaldaholic/…}, written by the X-L0 asset
 * generator). The kit must work before the art lands (global.md §1.5: "degrades when an asset is missing"), so
 * every draw goes through {@link #has}: a missing sprite is drawn with the caller's fill fallback instead of the
 * magenta "missing" texture. Presence is cached per resource reload ({@link #invalidate}).
 */
public final class FxSprites {
	public static final Identifier RAYS = sprite("fx/rays");
	public static final Identifier COIN_SPIN = sprite("fx/coin_spin");
	public static final Identifier SPARKLE = sprite("fx/sparkle");
	public static final Identifier CONFETTI = sprite("fx/confetti");
	public static final Identifier TOAST = sprite("toast/casino");
	public static final Identifier PANEL = sprite("panel/casino");

	private static final Map<Identifier, Boolean> PRESENT = new ConcurrentHashMap<>();

	private FxSprites() {}

	/** Sprite id {@code burmaldaholic:burmaldaholic/<path>} (file {@code textures/gui/sprites/burmaldaholic/<path>.png}). */
	public static Identifier sprite(String path) {
		return Burmaldaholic.id("burmaldaholic/" + path);
	}

	/** Chip sprite of a denomination (1, 5, 25, 100, 500). */
	public static Identifier chip(int denom) {
		return sprite("fx/chip_" + denom);
	}

	public static boolean has(Identifier spriteId) {
		return PRESENT.computeIfAbsent(spriteId, id -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.getResourceManager() == null) return false;
			Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/gui/sprites/" + id.getPath() + ".png");
			return mc.getResourceManager().getResource(file).isPresent();
		});
	}

	/** Forget the presence cache (resource reload). */
	public static void invalidate() {
		PRESENT.clear();
	}

	/** Blits a GUI sprite tinted with {@code argb}; returns false (nothing drawn) when the sprite is missing. */
	public static boolean blit(GuiGraphicsExtractor g, Identifier spriteId, int x, int y, int w, int h, int argb) {
		if (!has(spriteId)) return false;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, spriteId, x, y, w, h, argb);
		return true;
	}
}
