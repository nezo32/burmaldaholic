package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.present.MeterModel;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The machine's world behind the reels (slots.md §3.1, §3.3, §4.1): the theme backdrop with its drifting layers (sky
 * and clouds / basalt and a pulsing lava glow with embers / the void with two star layers), cross-faded into the
 * free-spin theme; the cabinet frame in the machine materials with rivets; the marquee with chasing bulbs (idle
 * chase, fast chase while spinning, 2 Hz blink on a win, alternating halves on big wins, rainbow on a jackpot); and the
 * jackpot meters (badge colours, rolling values, "WON!" flash). Drawn with fills until the SX1 sprites exist.
 */
public final class CabinetArt {
	private static final String[] TIER_KEYS = {"mini", "minor", "major", "grand"};
	/** Use the generated art (SX1); false draws the procedural placeholders. */
	public static boolean ART = true;
	/**
	 * Draw the animated strips (marquee, lit ladder plate, anticipation, sticky frame, stack bracket) from their PNGs
	 * with the frame picked in code ({@link SlotSprites}); false = the code-drawn versions.
	 */
	public static boolean ANIMATED_STRIPS = true;

	private CabinetArt() {}

	public static void backdrop(GuiGraphicsExtractor g, SlotLayout l, SlotStage s) {
		Machine m = s.machine();
		SymbolStyle.Theme th = SymbolStyle.theme(m);
		double fs = s.freeSpins().themeAmount(s);
		int x0 = l.left;
		int y0 = l.top;
		int x1 = l.left + l.width;
		int y1 = l.top + l.height;
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF140822);
		long now = s.now();
		boolean still = s.reduceMotion();
		g.enableScissor(x0, y0, x1, y1);
		if (ART) {
			// generated backdrops (400 × 240), cross-faded into the free-spin theme, + a drifting parallax tile layer
			SlotSprites.sheet(g, SlotSprites.backdrop(m, false), 400, 240, 0, 0, 400, 240, x0, y0, l.width, l.height, 0xFFFFFFFF);
			if (fs > 0) SlotSprites.sheet(g, SlotSprites.backdrop(m, true), 400, 240, 0, 0, 400, 240, x0, y0, l.width, l.height, SlotDraw.withAlpha(0xFFFFFFFF, fs));
			int speed = m == Machine.NETHER ? 1 : 2;
			int off = still ? 0 : (int) ((now / (1000 / (speed * 4))) % 256);
			for (int tx = -off; tx < l.width; tx += 256) {
				SlotSprites.sheet(g, SlotSprites.parallax(m), 256, 128, 0, 0, 256, 128, x0 + tx, y1 - 128, 256, 128, SlotDraw.withAlpha(0xFFFFFFFF, 0.8));
			}
			if (m == Machine.NETHER) {
				double pulse = still ? 0.5 : 0.5 + 0.5 * Math.sin(now / 4000.0 * Math.PI * 2);
				g.fillGradient(x0, y1 - 30, x1, y1, 0x00FF5A00, SlotDraw.withAlpha(0xFFFF6A00, 0.25 + 0.2 * pulse + 0.15 * fs));
			}
			g.disableScissor();
			return;
		}
		g.fillGradient(x0, y0, x1, y1, SlotDraw.lerp(th.skyTop(), th.fsSkyTop(), fs), SlotDraw.lerp(th.skyBottom(), th.fsSkyBottom(), fs));
		switch (m) {
			case OVERWORLD -> {
				// hills silhouette + three clouds drifting 2 px/s; free spins: moon and twinkling stars
				int hill = SlotDraw.lerp(0xFF4F8F3A, 0xFF18284A, fs);
				for (int i = 0; i < l.width; i += 2) {
					int h = (int) (18 + 10 * Math.sin(i / 37.0) + 6 * Math.sin(i / 13.0 + 1));
					g.fill(x0 + i, y1 - h, x0 + i + 2, y1, hill);
				}
				for (int c = 0; c < 3; c++) {
					int cx = x0 + (int) ((c * 150 + (still ? 0 : now / 500)) % (l.width + 60)) - 30;
					int cy = y0 + 30 + c * 18;
					int cloud = SlotDraw.withAlpha(0xFFFFFFFF, 0.55 * (1 - fs));
					g.fill(cx, cy, cx + 36, cy + 8, cloud);
					g.fill(cx + 8, cy - 5, cx + 26, cy, cloud);
				}
				if (fs > 0) {
					SlotDraw.disc(g, x1 - 40, y0 + 34, 10, SlotDraw.withAlpha(0xFFF4ECD8, fs));
					for (int i = 0; i < 24; i++) {
						int sx = x0 + (i * 97) % l.width;
						int sy = y0 + 20 + (i * 53) % 90;
						double tw = still ? 1 : 0.5 + 0.5 * Math.sin(now / 300.0 + i);
						g.fill(sx, sy, sx + 1, sy + 1, SlotDraw.withAlpha(0xFFFFFFFF, fs * tw));
					}
				}
			}
			case NETHER -> {
				// basalt pillars and a lava lake glow pulsing at 0.25 Hz
				for (int p = 0; p < 6; p++) {
					int px = x0 + 10 + p * (l.width / 6);
					int ph = 60 + (p * 37) % 50;
					g.fill(px, y1 - ph, px + 14, y1, 0xFF2A2428);
					g.fill(px, y1 - ph, px + 2, y1, 0xFF3E3438);
				}
				double pulse = still ? 0.5 : 0.5 + 0.5 * Math.sin(now / 4000.0 * Math.PI * 2);
				g.fillGradient(x0, y1 - 30, x1, y1, 0x00FF5A00, SlotDraw.withAlpha(0xFFFF6A00, 0.45 + 0.25 * pulse + 0.2 * fs));
			}
			case END -> {
				// void, an end-stone island silhouette, two star layers drifting 1 / 2 px/s (parallax)
				for (int layer = 0; layer < 2; layer++) {
					double drift = still ? 0 : now / (layer == 0 ? 1000.0 : 500.0);
					for (int i = 0; i < 30; i++) {
						int sx = x0 + (int) (((i * 131 + layer * 57) + drift) % l.width);
						int sy = y0 + (i * 71 + layer * 23) % l.height;
						g.fill(sx, sy, sx + 1 + layer, sy + 1 + layer, SlotDraw.withAlpha(layer == 0 ? 0xFFB8A8D0 : 0xFFF4ECF8, 0.6));
					}
				}
				for (int i = 0; i < l.width; i += 2) {
					int h = (int) Math.max(0, 16 - Math.abs(i - l.width / 2.0) / 8);
					g.fill(x0 + i, y1 - h, x0 + i + 2, y1, 0xFF3A3828);
				}
				if (fs > 0) g.fillGradient(x0, y0 + 20, x1, y0 + 60, 0x00000000, SlotDraw.withAlpha(0xFF6A2A8A, 0.4 * fs));
			}
		}
		g.disableScissor();
	}

	/** Cabinet frame around the reel window + marquee with chasing bulbs. */
	public static void cabinet(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, Component name, WinTier tier, boolean jackpot) {
		Machine m = s.machine();
		SymbolStyle.Theme th = SymbolStyle.theme(m);
		double fs = s.freeSpins().themeAmount(s);
		int b = l.border;
		int marquee = l.marquee;
		int x0 = l.wx - b;
		int y0 = l.wy - b - marquee;
		int x1 = l.wx + l.windowW() + b;
		int y1 = l.wy + l.windowH() + b;
		int body = switch (m) {
			case OVERWORLD -> 0xFF7A5230;
			case NETHER -> 0xFF26202A;
			case END -> 0xFF140C1C;
		};
		int trim = SlotDraw.lerp(th.trim(), th.glow(), fs * 0.5);
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF0C0610);
		if (ART) {
			// the frame overhangs 8 px at the sides and bottom, 2 px at the top (clear of the jackpot meters)
			var cab = l.cabinet();
			SlotSprites.blit(g, SlotSprites.machine(m, "cabinet"), cab.x(), cab.y(), cab.w(), cab.h());
			if (fs > 0) SlotSprites.blit(g, SlotSprites.machine(m, "cabinet_fs"), cab.x(), cab.y(), cab.w(), cab.h(),
				SlotDraw.withAlpha(0xFFFFFFFF, fs));
		} else {
			g.fillGradient(x0, y0, x1, y1, SlotDraw.shade(body, 1.2), SlotDraw.shade(body, 0.8));
			SlotDraw.frame(g, x0, y0, x1 - x0, y1 - y0, 2, trim);
		}
		SlotDraw.frame(g, l.wx - 2, l.wy - 2, l.windowW() + 4, l.windowH() + 4, 2, SlotDraw.shade(trim, 0.7));
		if (m == Machine.NETHER && !s.reduceMotion()) {
			// glowing lava seams
			double p = 0.5 + 0.5 * Math.sin(s.now() / 700.0);
			g.fill(x0 + 3, y1 - 3, x1 - 3, y1 - 2, SlotDraw.withAlpha(0xFFFF7A1A, 0.5 + 0.4 * p));
		}
		// rivets
		int rivet = m == Machine.OVERWORLD ? th.accent() : SlotDraw.shade(trim, 1.2);
		for (int[] pt : new int[][] {{x0 + 3, y0 + 3}, {x1 - 5, y0 + 3}, {x0 + 3, y1 - 5}, {x1 - 5, y1 - 5}}) g.fill(pt[0], pt[1], pt[0] + 2, pt[1] + 2, rivet);
		// marquee
		int my = y0 + 2;
		int mh = marquee - 2;
		g.fill(x0 + 4, my, x1 - 4, my + mh, 0xFF1A0E22);
		if (ART && ANIMATED_STRIPS && !jackpot && !tier.isWin()) {
			SlotSprites.blit(g, SlotSprites.machine(m, fs > 0.5 ? "marquee_fs" : "marquee"), x0 + 4, my, x1 - x0 - 8, mh);
			titlePlate(g, l, s, name);
			return;
		}
		int bulbs = (x1 - x0 - 12) / 6;
		long now = s.now();
		for (int i = 0; i < bulbs; i++) {
			int bx = x0 + 6 + i * 6;
			boolean lit;
			int color = SlotDraw.lerp(th.glow(), th.fsSkyBottom(), fs * 0.3);
			if (jackpot) {
				lit = true;
				color = SlotDraw.rainbow(now / 900.0 + i / (double) bulbs);
			} else if (tier.isOverlay()) {
				lit = (i % 2 == 0) == ((now / 125) % 2 == 0);
			} else if (tier.isWin()) {
				lit = (now / 250) % 2 == 0;
			} else if (s.spinning()) {
				lit = (i + now / 50) % 4 == 0;
			} else {
				lit = (i + now / 150) % 4 == 0;
			}
			if (s.reduceMotion()) lit = i % 2 == 0;
			g.fill(bx, my + 1, bx + 2, my + 3, lit ? color : 0xFF3A2A40);
			g.fill(bx, my + mh - 3, bx + 2, my + mh - 1, lit ? color : 0xFF3A2A40);
		}
		titlePlate(g, l, s, name);
	}

	/**
	 * The machine's title on its own plate over the middle of the marquee (the bulbs show on both sides of it, never
	 * through the letters); a Nice win's tier plate covers it for the win show.
	 */
	private static void titlePlate(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, Component name) {
		Font font = s.font();
		var r = l.titlePlate(font.width(name));
		if (ART) SlotSprites.blit(g, SlotSprites.machine(s.machine(), "title_plate"), r.x(), r.y(), r.w(), r.h());
		else SlotDraw.plate(g, r.x(), r.y(), r.w(), r.h(), 0xFF2A1A3A, 0xFF120818, 0xFFFFD640);
		SlotDraw.centeredFit(g, font, name, r.x() + r.w() / 2, r.y() + (r.h() - 8) / 2 + (l.compact ? 0 : 1), r.w() - 12, 0xFFFFE680);
	}

	/** Jackpot meters: badge, tier word and rolling amount; "WON!" + flash when another player's jackpot drops a pool. */
	public static void meters(GuiGraphicsExtractor g, SlotLayout l, SlotStage s, MeterModel meters, long[] pools) {
		Font font = s.font();
		long now = s.now();
		for (int i = 0; i < l.meterTiers.length; i++) {
			int tier = l.meterTiers[i];
			if (pools == null || pools[tier - 1] <= 0) continue;
			int x = l.meterX(i);
			int y = l.meterY;
			int color = SymbolStyle.JACKPOT_COLORS[tier - 1];
			int rim = tier == 4 && !s.reduceMotion() ? SlotDraw.rainbow(now / 4000.0) : color;
			if (ART) {
				SlotSprites.blit(g, SlotSprites.jackpotPlate(tier), x, y - 1, l.meterW, 18);
				if (tier == 4 && !s.reduceMotion()) SlotDraw.frame(g, x, y - 1, l.meterW, 18, 1, SlotDraw.withAlpha(rim, 0.8));
			} else {
				SlotDraw.plate(g, x, y, l.meterW, 16, SlotDraw.shade(color, 0.45), SlotDraw.shade(color, 0.2), rim);
			}
			double flash = meters.flash(tier - 1, now, s.flashes());
			if (flash > 0) g.fill(x, y, x + l.meterW, y + 16, SlotDraw.withAlpha(color, flash));
			SlotDraw.gem(g, x + 8, y + 8, 5, color);
			Component word = Component.translatable("gui.burmaldaholic.slots.jackpot.tier." + TIER_KEYS[tier - 1]);
			Component value = meters.showWon(tier - 1, now) ? Component.translatable("gui.burmaldaholic.slots.fx.meter_won")
				: Texts.number(meters.shown(tier - 1, now));
			Component line = Component.translatable("gui.burmaldaholic.slots.jackpot.meter", word, value);
			SlotDraw.centeredFit(g, font, line, x + 8 + (l.meterW - 8) / 2, y + 4, l.meterW - 18, 0xFFFFFFFF);
		}
	}

	/** The reel-window glass sprite (a diagonal sheen; drawn over the reels). */
	public static void glass(GuiGraphicsExtractor g, SlotLayout l) {
		if (ART) SlotSprites.blit(g, SlotSprites.GLASS, l.wx, l.wy, l.windowW(), l.windowH());
	}
}
