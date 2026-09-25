package dev.nezo.burmaldaholic.client.pvp.kit;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The themed scenes of visual/extras.md §2.3: a 400 × 240 backdrop (1 art px = 1 GUI px) under a 64² nine-slice
 * frame (border 12) in the game's material, the marquee strip (Coin Flip) and the title banner stretched to the
 * translated title. Rendering order §1.1: backdrop → frame → marquee → title banner; the play area is the backdrop.
 */
public enum Scene {
	COIN("extras/coin_backdrop", Kit.extras("coin_frame"), Kit.extras("coin_marquee"), Kit.extras("coin_banner")),
	WHEEL("extras/wheel_backdrop", Kit.extras("wheel_frame"), null, Kit.extras("wheel_banner")),
	PLINKO("extras/plinko_backdrop", Kit.extras("plinko_frame"), null, Kit.extras("plinko_banner")),
	SCRATCH("extras/scratch_backdrop", Kit.extras("scratch_frame"), null, Kit.extras("scratch_banner")),
	PVP("pvp/arena_backdrop", Kit.pvp("frame"), null, Kit.pvp("banner")),
	PVP_GRUDGE("pvp/arena_backdrop_grudge", Kit.pvp("grudge_frame"), null, Kit.pvp("grudge_banner"));

	public static final int W = 400;
	public static final int H = 240;

	private final Identifier backdrop;
	private final Identifier frame;
	private final @Nullable Identifier marquee;
	private final Identifier banner;

	Scene(String backdrop, Identifier frame, @Nullable Identifier marquee, Identifier banner) {
		this.backdrop = Kit.sheet(backdrop);
		this.frame = frame;
		this.marquee = marquee;
		this.banner = banner;
	}

	/** Panel origin for a screen of {@code width × height} (centred; clamped to the top when the screen is short). */
	public static int left(int width) {
		return (width - W) / 2;
	}

	public static int top(int height) {
		return Math.max(0, (height - H) / 2);
	}

	/** Backdrop (the play area), drawn first. */
	public void backdrop(GuiGraphicsExtractor g, int x, int y) {
		Kit.region(g, backdrop, W, H, 0, 0, W, H, x, y);
	}

	/** Frame, marquee and banner with the title; call after the backdrop. {@code title} null = no banner. */
	public void frame(GuiGraphicsExtractor g, Font font, int x, int y, @Nullable Component title) {
		Kit.sprite(g, frame, x, y, W, H);
		if (marquee != null) {
			for (int mx = x + 12; mx < x + W - 12; mx += 128) {
				int w = Math.min(128, x + W - 12 - mx);
				g.enableScissor(mx, y + 12, mx + w, y + 24);
				Kit.sprite(g, marquee, mx, y + 12, 128, 12);
				g.disableScissor();
			}
		}
		if (title != null) {
			int tw = Math.max(110, font.width(title) + 32);
			int bx = x + W / 2 - tw / 2;
			Kit.sprite(g, banner, bx, y + 3, tw, 22);
			g.text(font, title, x + W / 2 - font.width(title) / 2, y + 10, Kit.GOLD, true);
		}
	}

	/** Full scene: backdrop + frame + marquee + banner. */
	public void draw(GuiGraphicsExtractor g, Font font, int x, int y, @Nullable Component title) {
		backdrop(g, x, y);
		frame(g, font, x, y, title);
	}

	/** HUD chip counter at (x, y): {@code core/hud/chip_counter} sized to the number + 26, the animated chip icon. */
	public static int chipCounter(GuiGraphicsExtractor g, Font font, Component amount, int x, int y, boolean golden) {
		int w = font.width(amount) + 26;
		Kit.sprite(g, Kit.core(golden ? "hud/chip_counter_golden" : "hud/chip_counter"), x, y, w, 16);
		Kit.sprite(g, Kit.core("hud/chip_icon"), x + 3, y + 2, 12, 12);
		g.text(font, amount, x + 18, y + 4, Kit.GOLD, true);
		return w;
	}

	/** An inset well ({@code core/panel/inset}). */
	public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		if (!Kit.sprite(g, Kit.core("panel/inset"), x, y, w, h)) {
			g.fill(x, y, x + w, y + h, 0xC0140822);
			Kit.frameRect(g, x, y, w, h, 0xFF783CBE);
		}
	}

	/** A solid casino card ({@code core/panel/casino}). */
	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		if (!Kit.sprite(g, Kit.core("panel/casino"), x, y, w, h)) {
			g.fill(x, y, x + w, y + h, 0xF026103C);
			Kit.frameRect(g, x, y, w, h, 0xFFFFD640);
		}
	}
}
