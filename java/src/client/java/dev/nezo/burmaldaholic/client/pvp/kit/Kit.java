package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing kit of the extras and PvP screens (lane J-L7; visual/extras.md §1, §10, §12). Two kinds of art, both written
 * by {@code tools/assets/modules/extras.mjs}:
 * <ul>
 *   <li><b>atlas sprites</b> ({@code textures/gui/sprites/burmaldaholic/{extras,pvp}/…}, {@code core/…}): drawn with
 *   {@link FxSprites#blit}, so nine-slices, tiles and {@code .mcmeta} animations come from the atlas and a tint works;</li>
 *   <li><b>code-indexed sheets</b> ({@code textures/gui/{extras,pvp}/…}): regions by UV; tinted through the pipeline
 *   {@code blit} bound at runtime (26.2 and 26.3 moved {@code RenderPipeline}), else the plain overload.</li>
 * </ul>
 * Everything is drawn at integer GUI positions; text is the vanilla font at 1×/2× (no text in textures).
 */
public final class Kit {
	public static final int INK = CasinoPalette.INK;
	public static final int GOLD = CasinoPalette.GOLD;
	public static final int BONE = CasinoPalette.BONE;
	public static final int BONE_SHADE = CasinoPalette.BONE_SHADE;
	public static final int BONUS = CasinoPalette.BONUS;
	public static final int LILAC = CasinoPalette.LILAC;
	public static final int RED = CasinoPalette.CHIP_RED;
	public static final int RED_LIGHT = CasinoPalette.CHIP_RED_LIGHT;
	public static final int CURSE = CasinoPalette.CURSE;
	public static final int COOL = CasinoPalette.COOL;
	public static final int WHITE = 0xFFFFFFFF;

	private Kit() {}

	// ---- ids ------------------------------------------------------------------------------------------------------

	/** Atlas sprite {@code burmaldaholic:burmaldaholic/extras/<name>}. */
	public static Identifier extras(String name) {
		return Burmaldaholic.id("burmaldaholic/extras/" + name);
	}

	/** Atlas sprite {@code burmaldaholic:burmaldaholic/pvp/<name>}. */
	public static Identifier pvp(String name) {
		return Burmaldaholic.id("burmaldaholic/pvp/" + name);
	}

	/** Atlas sprite {@code burmaldaholic:core/<path>}. */
	public static Identifier core(String path) {
		return FxSprites.sprite(path);
	}

	/** A code-indexed sheet {@code textures/gui/<path>.png}. */
	public static Identifier sheet(String path) {
		return Burmaldaholic.id("textures/gui/" + path + ".png");
	}

	// ---- sprites --------------------------------------------------------------------------------------------------

	public static boolean sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
		return FxSprites.blit(g, id, x, y, w, h, 0xFFFFFFFF);
	}

	public static boolean sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int argb) {
		if ((argb >>> 24) < 3) return false;
		return FxSprites.blit(g, id, x, y, w, h, argb);
	}

	/** A region of a sheet ({@code texW × texH}), drawn 1:1 at (x, y). */
	public static void region(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, int x, int y) {
		region(g, tex, texW, texH, u, v, w, h, x, y, w, h, 0xFFFFFFFF);
	}

	/** A region of a sheet drawn into (x, y, dw, dh) with a tint (ARGB). */
	public static void region(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, int x, int y, int dw, int dh,
			int argb) {
		if (dw <= 0 || dh <= 0 || (argb >>> 24) < 3) return;
		if (argb != 0xFFFFFFFF && Tinted.blit(g, tex, x, y, u, v, dw, dh, w, h, texW, texH, argb)) return;
		g.blit(tex, x, y, x + dw, y + dh, u / (float) texW, (u + w) / (float) texW, v / (float) texH, (v + h) / (float) texH);
	}

	/** Frame {@code index} of a horizontal strip of {@code fw × fh} frames. */
	public static void frame(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int fw, int fh, int index, int x, int y, int argb) {
		region(g, tex, texW, texH, index * fw, 0, fw, fh, x, y, fw, fh, argb);
	}

	/** {@code blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, uw, vh, texW, texH, argb)} bound at runtime. */
	static final class Tinted {
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
					if (m.getName().equals("blit") && p.length == 13 && p[0].isInstance(pipeline) && p[1] == Identifier.class && p[4] == float.class
						&& p[12] == int.class) {
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

		static boolean blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int u, int v, int w, int h, int uw, int vh, int texW, int texH,
				int argb) {
			if (BLIT == null) return false;
			try {
				BLIT.invoke(g, PIPELINE, id, x, y, (float) u, (float) v, w, h, uw, vh, texW, texH, argb);
				return true;
			} catch (Throwable t) {
				return false;
			}
		}
	}

	// ---- colours --------------------------------------------------------------------------------------------------

	public static int alpha(int argb, double a) {
		int na = (int) Math.round(Math.max(0, Math.min(1, a)) * (argb >>> 24));
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	/** White tint with an alpha (sprites fading in / out). */
	public static int fade(double a) {
		return alpha(0xFFFFFFFF, a);
	}

	/** Grey tint of brightness {@code f} (0–1), opaque. */
	public static int shade(double f) {
		int c = (int) Math.round(Math.max(0, Math.min(1, f)) * 255);
		return 0xFF000000 | c << 16 | c << 8 | c;
	}

	public static int lerp(int c0, int c1, double t) {
		double u = Math.max(0, Math.min(1, t));
		int a = (int) Math.round((c0 >>> 24) * (1 - u) + (c1 >>> 24) * u);
		int r = (int) Math.round(((c0 >> 16) & 0xFF) * (1 - u) + ((c1 >> 16) & 0xFF) * u);
		int gg = (int) Math.round(((c0 >> 8) & 0xFF) * (1 - u) + ((c1 >> 8) & 0xFF) * u);
		int b = (int) Math.round((c0 & 0xFF) * (1 - u) + (c1 & 0xFF) * u);
		return a << 24 | r << 16 | gg << 8 | b;
	}

	// ---- shapes ---------------------------------------------------------------------------------------------------

	public static void frameRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** A {@code t}-px line from (x0, y0) to (x1, y1) (rotated fill). */
	public static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int t, int color) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		float len = (float) Math.hypot(dx, dy);
		if (len < 0.5f) return;
		g.pose().pushMatrix();
		g.pose().translate((float) x0, (float) y0);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.fill(0, -t / 2, Math.round(len), -t / 2 + Math.max(1, t), color);
		g.pose().popMatrix();
	}

	/** Filled disc from horizontal spans. */
	public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}

	/** A ring of {@code segments} short arcs filled clockwise from the top up to {@code progress} (timers). */
	public static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, double progress, int color, int back) {
		int n = 48;
		for (int i = 0; i < n; i++) {
			double a = i / (double) n * Math.PI * 2;
			int x = cx + (int) Math.round(Math.sin(a) * r);
			int y = cy - (int) Math.round(Math.cos(a) * r);
			g.fill(x - 1, y - 1, x + 1, y + 1, i < progress * n ? color : back);
		}
	}

	// ---- text -----------------------------------------------------------------------------------------------------

	public static void text(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int color) {
		g.text(font, c, x, y, color, true);
	}

	public static void textFlat(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int color) {
		g.text(font, c, x, y, color, false);
	}

	public static void right(GuiGraphicsExtractor g, Font font, Component c, int rx, int y, int color) {
		g.text(font, c, rx - font.width(c), y, color, true);
	}

	public static void centered(GuiGraphicsExtractor g, Font font, Component c, int cx, int y, int color) {
		g.text(font, c, cx - font.width(c) / 2, y, color, true);
	}

	/** Left-aligned text that shrinks (never below ½) to fit {@code maxWidth}. */
	public static void fit(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int maxWidth, int color, boolean shadow) {
		int w = font.width(c);
		if (w <= maxWidth) {
			g.text(font, c, x, y, color, shadow);
			return;
		}
		float s = Math.max(0.5f, maxWidth / (float) w);
		g.pose().pushMatrix();
		g.pose().translate(x, y + (1 - s) * 4);
		g.pose().scale(s, s);
		g.text(font, c, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** Centred text that shrinks (never below ½) to fit. */
	public static void centeredFit(GuiGraphicsExtractor g, Font font, Component c, int cx, int y, int maxWidth, int color, boolean shadow) {
		int w = font.width(c);
		if (w <= maxWidth) {
			g.text(font, c, cx - w / 2, y, color, shadow);
			return;
		}
		float s = Math.max(0.5f, maxWidth / (float) w);
		g.pose().pushMatrix();
		g.pose().translate(cx, y + (1 - s) * 4);
		g.pose().scale(s, s);
		g.text(font, c, -w / 2, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** Right-aligned text that shrinks to fit. */
	public static void rightFit(GuiGraphicsExtractor g, Font font, Component c, int rx, int y, int maxWidth, int color) {
		int w = Math.min(maxWidth, font.width(c));
		fit(g, font, c, rx - w, y, maxWidth, color, true);
	}

	/** Word-wrapped text; returns the y below it (10 px lines). */
	public static int wrap(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(c, Math.max(10, width))) {
			g.text(font, line, x, y, color, true);
			y += 10;
		}
		return y;
	}

	/** Word-wrapped, centred lines (at most {@code maxLines}); returns the y below. */
	public static int wrapCentered(GuiGraphicsExtractor g, Font font, Component c, int cx, int y, int width, int maxLines, int color, boolean shadow) {
		int n = 0;
		for (FormattedCharSequence line : font.split(c, Math.max(10, width))) {
			if (n++ >= maxLines) break;
			g.text(font, line, cx - font.width(line) / 2, y, color, shadow);
			y += 10;
		}
		return y;
	}

	/** Text at an integer scale with a 1 px ink outline (hero numbers, banner words), centred on cx. */
	public static void big(GuiGraphicsExtractor g, Font font, Component c, float cx, float y, float scale, int color, int ink) {
		FormattedCharSequence seq = c.getVisualOrderText();
		int w = font.width(seq);
		int inkA = alpha(ink, (color >>> 24) / 255.0);
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		int x = -w / 2;
		g.text(font, seq, x - 1, 0, inkA, false);
		g.text(font, seq, x + 1, 0, inkA, false);
		g.text(font, seq, x, -1, inkA, false);
		g.text(font, seq, x, 1, inkA, false);
		g.text(font, seq, x, 0, color, false);
		g.pose().popMatrix();
	}

	/** Left-aligned outlined text at a scale. */
	public static void bigLeft(GuiGraphicsExtractor g, Font font, Component c, float x, float y, float scale, int color, int ink) {
		FormattedCharSequence seq = c.getVisualOrderText();
		int inkA = alpha(ink, (color >>> 24) / 255.0);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, seq, -1, 0, inkA, false);
		g.text(font, seq, 1, 0, inkA, false);
		g.text(font, seq, 0, -1, inkA, false);
		g.text(font, seq, 0, 1, inkA, false);
		g.text(font, seq, 0, 0, color, false);
		g.pose().popMatrix();
	}

	// ---- sounds ---------------------------------------------------------------------------------------------------

	/** A vanilla sound event (UI, non-positional) × {@code anim.volume}; silently skipped when unknown. */
	public static void vanilla(String id, float volume, float pitch) {
		play(Identifier.withDefaultNamespace(id), volume, pitch);
	}

	/** A registered {@code burmaldaholic:} sound that is not in the catalog (legacy module sounds), UI. */
	public static void mod(net.minecraft.sounds.@org.jspecify.annotations.Nullable SoundEvent event, float volume, float pitch) {
		if (event != null) play(event.location(), volume, pitch);
	}

	private static void play(Identifier id, float volume, float pitch) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		float v = volume * FxSettings.volume();
		if (mc == null || mc.getSoundManager() == null || v <= 0.001f) return;
		var e = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(id);
		if (e == null) return;
		mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(e, pitch, v));
	}

	// ---- settings -------------------------------------------------------------------------------------------------

	public static boolean reduceMotion() {
		return FxSettings.reduceMotion();
	}

	public static boolean flashes() {
		return FxSettings.flashes();
	}

	/** Viewer speed for client-paced solo motion (50 / 100 / 150 %). */
	public static int speedPct() {
		return FxSettings.speed().pct;
	}
}
