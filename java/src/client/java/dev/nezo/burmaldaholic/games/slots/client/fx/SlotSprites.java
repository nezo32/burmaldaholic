package dev.nezo.burmaldaholic.games.slots.client.fx;

import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The generated slot art (SX1, lane B-L10; slots.md §9.1), drawn as plain textures by UV with the ONE blit overload
 * that links on every supported version ({@code blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)}): 26.3 moved
 * {@code RenderPipeline} to another package, so no pipeline-taking call may be used (checkLinkage). Consequences:
 * sprites are addressed by their PNG path under {@code textures/gui/sprites/…}, animated strips pick their frame
 * here from the frame time, nine-slices are drawn as nine blits, and colour tints are not available (a "colour" whose
 * alpha is below ½ skips the draw; callers fade with overlays instead).
 */
public final class SlotSprites {
	private SlotSprites() {}

	/**
	 * A texture: size, frame height (vertical strip) and animation frame time in ms (0 = code picks the frame), and a
	 * nine-slice border (0 = stretch).
	 */
	public record Tex(Identifier id, int w, int h, int frameH, int frameMs, int border) {
		public int frames() {
			return Math.max(1, h / frameH);
		}
	}

	private static Tex sprite(String path, int w, int h, int frameH, int frameMs, int border) {
		return new Tex(Identifier.fromNamespaceAndPath("burmaldaholic", "textures/gui/sprites/burmaldaholic/slots/" + path + ".png"), w, h, frameH, frameMs,
			border);
	}

	private static Tex sprite(String path, int w, int h) {
		return sprite(path, w, h, h, 0, 0);
	}

	public static Identifier texture(String path) {
		return Identifier.fromNamespaceAndPath("burmaldaholic", "textures/gui/slots/" + path);
	}

	// shared sprites
	public static final Tex GLASS = sprite("glass", 220, 132);
	public static final Tex WIN_FRAME = sprite("win_frame", 44, 176, 44, 100, 0);
	public static final Tex PATH_NODE = sprite("path_node", 8, 16, 8, 200, 0);
	public static final Tex PIP_ON = sprite("pip_on", 8, 8);
	public static final Tex PIP_OFF = sprite("pip_off", 8, 8);
	public static final Tex TURBO_ON = sprite("turbo_on", 16, 16);
	public static final Tex TURBO_OFF = sprite("turbo_off", 16, 16);
	public static final Tex MAXWIN_PLATE = sprite("maxwin_plate", 128, 32);
	public static final Tex LEVER = sprite("lever", 12, 60);
	public static final Tex SPIN = sprite("spin_button", 56, 40);
	public static final Tex SPIN_HOVER = sprite("spin_button_hover", 56, 40);
	public static final Tex SPIN_PRESSED = sprite("spin_button_pressed", 56, 40);
	public static final Tex SPIN_DISABLED = sprite("spin_button_disabled", 56, 40);
	public static final Tex SPIN_STOP = sprite("spin_button_stop", 56, 40);
	// casino controls (lane J-L9b): nine-slice button faces, 12 px icons, the inset value plate
	public static final Tex BUTTON = sprite("button", 32, 20, 20, 0, 6);
	public static final Tex BUTTON_HOVER = sprite("button_hover", 32, 20, 20, 0, 6);
	public static final Tex BUTTON_PRESSED = sprite("button_pressed", 32, 20, 20, 0, 6);
	public static final Tex BUTTON_DISABLED = sprite("button_disabled", 32, 20, 20, 0, 6);
	public static final Tex BUTTON_ON = sprite("button_on", 32, 20, 20, 0, 6);
	public static final Tex BUTTON_GOLD = sprite("button_gold", 32, 20, 20, 0, 6);
	/** Nice tier plate (thin rim so a 2× word fits a 20 px plate). */
	public static final Tex TIER_PLATE = sprite("tier_plate", 16, 16, 16, 0, 3);
	public static final Tex VALUE_PLATE = sprite("value_plate", 32, 18, 18, 0, 5);
	public static final Tex ICON_AUTO = sprite("icon_auto", 12, 12);
	public static final Tex ICON_PAYTABLE = sprite("icon_paytable", 12, 12);
	public static final Tex ICON_MINUS = sprite("icon_minus", 12, 12);
	public static final Tex ICON_PLUS = sprite("icon_plus", 12, 12);
	public static final Tex ICON_BONUS = sprite("icon_bonus", 12, 12);
	/** Atlas ids of the control icons (for the J-L2 {@code CasinoButton} kit, which draws atlas sprites). */
	public static Identifier atlas(String path) {
		return Identifier.fromNamespaceAndPath("burmaldaholic", "burmaldaholic/slots/" + path);
	}

	public static final Identifier ICON_AUTO_ID = atlas("icon_auto");
	public static final Identifier ICON_PAYTABLE_ID = atlas("icon_paytable");
	public static final Identifier ICON_MINUS_ID = atlas("icon_minus");
	public static final Identifier ICON_PLUS_ID = atlas("icon_plus");
	public static final Identifier ICON_BONUS_ID = atlas("icon_bonus");
	public static final Identifier TURBO_ON_ID = atlas("turbo_on");
	public static final Identifier TURBO_OFF_ID = atlas("turbo_off");
	private static final String[] PLATES = {"mini", "minor", "major", "grand"};

	public static Tex jackpotPlate(int tier) {
		return sprite("jackpot_plate_" + PLATES[Math.max(1, Math.min(4, tier)) - 1], 94, 18);
	}

	// feature strips
	public static final Tex CHEST = sprite("overworld/chest", 40, 240, 40, 0, 0);
	public static final Tex CHEST_DIM = sprite("overworld/chest_dim", 40, 40);
	public static final Tex CREEPER = sprite("overworld/creeper", 40, 160, 40, 0, 0);
	public static final Tex STACK_BRACKET = sprite("overworld/stack_bracket", 44, 368, 92, 150, 0);
	public static final Tex BURN = sprite("nether/burn", 44, 220, 44, 0, 0);
	public static final Tex COIN_LOCK = sprite("nether/coin_lock", 44, 132, 44, 0, 0);
	public static final Tex EMPTY_CELL = sprite("nether/empty_cell", 44, 44);
	public static final Tex EMBER_BLUR = sprite("nether/ember_blur", 44, 44);
	public static final Tex LADDER = sprite("nether/ladder_plate", 72, 16);
	public static final Tex LADDER_LIT = sprite("nether/ladder_plate_lit", 72, 32, 16, 200, 0);
	public static final Tex STICKY = sprite("end/sticky_frame", 44, 544, 136, 150, 0);
	public static final Tex POINTER = sprite("end/pointer", 16, 48, 24, 0, 0);
	public static final Tex UP_ARROW = sprite("end/up_arrow", 16, 16);

	/** Per-machine sprites: cabinet(_fs) 64 nine-slice 12, banner 48/12, banner_small 32/8, title_plate 32×16/5, side_panel 32/8, marquee(_fs) 4 × 12, anticipation 8 × 140. */
	public static Tex machine(Machine m, String name) {
		String p = m.id + "/" + name;
		return switch (name) {
			case "cabinet", "cabinet_fs" -> sprite(p, 64, 64, 64, 0, 12);
			case "banner" -> sprite(p, 48, 48, 48, 0, 12);
			case "banner_small" -> sprite(p, 32, 32, 32, 0, 8);
			case "title_plate" -> sprite(p, 32, 16, 16, 0, 5);
			case "side_panel" -> sprite(p, 32, 32, 32, 0, 8);
			case "marquee", "marquee_fs" -> sprite(p, 128, 48, 12, 150, 0);
			case "anticipation" -> sprite(p, 48, 1120, 140, 50, 0);
			default -> sprite(p, 16, 16);
		};
	}

	public static Tex coinPile(int size) {
		return sprite("overworld/coin_pile_" + (size >= 2 ? "l" : size == 1 ? "m" : "s"), 40, 40);
	}

	public static Identifier backdrop(Machine m, boolean fs) {
		return texture(m.id + (fs ? "_backdrop_fs.png" : "_backdrop.png"));
	}

	public static Identifier parallax(Machine m) {
		return texture(m.id + "_parallax.png");
	}

	/** 11 × (40 × 132): 8 shimmer + 3 grow keys. */
	public static final Identifier EGG_TALL = texture("end_egg_tall.png");

	private static boolean visible(int color) {
		return (color >>> 24) >= 0x80;
	}

	/** Region (u, v, uw, vh in texels) of a texture of {@code tw × th} drawn into (x, y, w, h). */
	private static void region(GuiGraphicsExtractor g, Identifier id, int tw, int th, int u, int v, int uw, int vh, int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) return;
		g.blit(id, x, y, x + w, y + h, u / (float) tw, (u + uw) / (float) tw, v / (float) th, (v + vh) / (float) th);
	}

	/** Draws a sprite (current animation frame for animated strips; nine-slice when it has a border). */
	public static void blit(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h, int color) {
		if (!visible(color)) return;
		blit(g, t, x, y, w, h);
	}

	public static void blit(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h) {
		int frame = t.frameMs() > 0 ? (int) ((Util.getMillis() / t.frameMs()) % t.frames()) : 0;
		if (t.border() > 0) {
			nineSlice(g, t, x, y, w, h);
			return;
		}
		region(g, t.id(), t.w(), t.h(), 0, frame * t.frameH(), t.w(), t.frameH(), x, y, w, h);
	}

	private static void nineSlice(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h) {
		int b = t.border();
		int sw = t.w();
		int sh = t.frameH();
		int bx = Math.min(b, w / 2);
		int by = Math.min(b, h / 2);
		int[] xs = {x, x + bx, x + w - bx, x + w};
		int[] ys = {y, y + by, y + h - by, y + h};
		int[] us = {0, b, sw - b, sw};
		int[] vs = {0, b, sh - b, sh};
		for (int j = 0; j < 3; j++) {
			for (int i = 0; i < 3; i++) {
				region(g, t.id(), t.w(), t.h(), us[i], vs[j], us[i + 1] - us[i], vs[j + 1] - vs[j], xs[i], ys[j], xs[i + 1] - xs[i], ys[j + 1] - ys[j]);
			}
		}
	}

	/** Frame {@code index} of a code-driven strip, drawn at (x, y) with size (w, h). */
	public static void frame(GuiGraphicsExtractor g, Tex t, int fw, int fh, int frames, int index, int x, int y, int w, int h, int color) {
		if (!visible(color)) return;
		int i = Math.max(0, Math.min(frames - 1, index));
		region(g, t.id(), t.w(), t.h(), 0, i * fh, fw, fh, x, y, w, h);
	}

	/** A region of a plain texture sheet. */
	public static void sheet(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int fw, int fh, int x, int y, int w, int h, int color) {
		if (!visible(color)) return;
		region(g, tex, texW, texH, u, v, fw, fh, x, y, w, h);
	}
}
