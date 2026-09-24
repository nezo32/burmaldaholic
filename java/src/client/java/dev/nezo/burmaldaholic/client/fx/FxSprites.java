package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * GUI sprites of the presentation kit ({@code textures/gui/sprites/core/…}, written by the X-L0 asset
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

	/** Sprite id {@code burmaldaholic:core/<path>} (file {@code textures/gui/sprites/core/<path>.png}). */
	public static Identifier sprite(String path) {
		return Burmaldaholic.id("core/" + path);
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
		return Compat.blitSprite(g, spriteId, x, y, w, h, argb);
	}

	/**
	 * {@code blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, argb)} bound at runtime: the pipeline type
	 * moved package between 26.2 ({@code com.mojang.blaze3d.pipeline}) and 26.3 ({@code com.mojang.renderpearl.api}),
	 * so a direct call would not link on 26.3 (docs/architecture/java.md "Multi-version strategy").
	 */
	static final class Compat {
		private static final java.lang.invoke.MethodHandle BLIT;
		private static final Object PIPELINE;

		static {
			java.lang.invoke.MethodHandle mh = null;
			Object pipeline = null;
			try {
				Class<?> pipelines = Class.forName("net.minecraft.client.renderer.RenderPipelines");
				pipeline = pipelines.getField("GUI_TEXTURED").get(null);
				for (java.lang.reflect.Method m : GuiGraphicsExtractor.class.getMethods()) {
					Class<?>[] p = m.getParameterTypes();
					if (m.getName().equals("blitSprite") && p.length == 7 && p[0].isInstance(pipeline) && p[1] == Identifier.class
						&& p[2] == int.class && p[6] == int.class) {
						mh = java.lang.invoke.MethodHandles.publicLookup().unreflect(m);
						break;
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				mh = null;
			}
			BLIT = mh;
			PIPELINE = pipeline;
		}

		static boolean blitSprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int argb) {
			if (BLIT == null) return false;
			try {
				BLIT.invoke(g, PIPELINE, id, x, y, w, h, argb);
				return true;
			} catch (Throwable t) {
				return false;
			}
		}
	}
}
