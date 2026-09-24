package dev.nezo.burmaldaholic.games.slots.client.features;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.client.reels.SymbolSheet;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotScript;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * End expanding sticky Dragon Eggs (slots.md §4.6; JS6): the egg cracks with light, grows over its whole reel
 * (scale-Y 1/3 → 1, outBack 1.4, anchored on its row), amethyst chains clamp around the reel, and the reel stays
 * wild for the rest of the feature: it does not spin, shivers once when the others spin up, its chains glint and
 * motes rise slowly. A scatter under a sticky reel is simply covered (no pop). Reduce motion: the column switches at
 * once.
 */
public final class WildView {
	private static final int AMETHYST = 0xFFB040FF;
	private long lastMote;

	public void reset() {
		lastMote = 0;
	}

	public void cue(SlotStage s, Beat b) {
		if (b.kind().equals(SlotTimeline.WILD_EXPAND)) {
			SlotSounds.play("slots.wild_expand", 1f, 1f);
			int r = b.lane();
			s.particles().burst(ScreenParticles.MOTE, s.cellX(r) + s.cell() / 2f, s.wy() + s.windowH() / 2f, 12, 0.08f, 0, 900, s.now());
		} else if (b.kind().equals(SlotTimeline.WILD_STICK)) {
			SlotSounds.wildStick();
		}
	}

	private static int eggRow(SlotStage s, int spin, int reel) {
		for (Beat b : s.beats(SlotTimeline.SYMBOL_LAND)) {
			if (b.lane() == reel && b.arg(SlotBeats.SPIN) == spin && b.arg(SlotBeats.SYM_SYMBOL) == 0) return b.arg(SlotBeats.SYM_ROW);
		}
		return 1;
	}

	/** ±1 px shiver of a sticky reel during the other reels' spin-up. */
	public int shiver(SlotStage s, int reel) {
		if (s.reduceMotion() || reel < 1 || reel > 3 || s.frames() == null || s.frames().phase < 0) return 0;
		if ((s.frames().stickyMask & (1 << (reel - 1))) == 0) return 0;
		SlotScript.Phase p = s.script().phase(s.frames().phase);
		return FeatureMotion.shiver(s.t() - p.spinUpAt);
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g) {
		if (s.script() == null) return;
		double t = s.t();
		int sticky = s.frames().stickyMask;
		for (int r = 1; r <= 3; r++) {
			boolean settled = (sticky & (1 << (r - 1))) != 0;
			Beat expand = null;
			Beat stick = null;
			for (Beat b : s.beats(SlotTimeline.WILD_EXPAND)) if (b.lane() == r && b.at() <= t && t < b.end() + 400) expand = b;
			for (Beat b : s.beats(SlotTimeline.WILD_STICK)) if (b.lane() == r && b.at() <= t && t < b.end()) stick = b;
			if (!settled && expand == null && stick == null) {
				drawCrack(s, g, r, t);
				continue;
			}
			double scaleY = 1;
			int anchorRow = 1;
			if (expand != null && t < expand.end()) {
				scaleY = FeatureMotion.expandScale(t - expand.at(), expand.dur(), s.reduceMotion());
				anchorRow = eggRow(s, expand.arg(SlotBeats.SPIN), r);
			}
			double chainA = settled ? 1 : stick != null ? FeatureMotion.stickAlpha(t - stick.at(), stick.dur()) : 0;
			double chainS = settled || stick == null ? 1 : FeatureMotion.stickScale(t - stick.at(), stick.dur());
			g.enableScissor(s.wx(), s.wy(), s.wx() + s.windowW(), s.wy() + s.windowH());
			drawTallEgg(s, g, r, scaleY, anchorRow, chainA, chainS, sticky == 7);
			g.disableScissor();
		}
		// slow motes on sticky reels (1 per 400 ms)
		if (sticky != 0 && s.now() - lastMote > 400 && !s.reduceMotion()) {
			lastMote = s.now();
			for (int r = 1; r <= 3; r++) {
				if ((sticky & (1 << (r - 1))) != 0)
					s.particles().rise(ScreenParticles.MOTE, s.cellX(r) + s.cell() / 2f, s.wy() + s.windowH() - 6, s.cell() * 0.7f, 1, s.now());
			}
		}
	}

	/** The egg's 3-frame light crack between the stop and the expand (150 → 270 ms after the stop). */
	private static void drawCrack(SlotStage s, GuiGraphicsExtractor g, int r, double t) {
		for (Beat b : s.beats(SlotTimeline.WILD_EXPAND)) {
			if (b.lane() != r) continue;
			double sinceStop = t - (b.at() - 270);
			int frame = FeatureMotion.crackFrame(sinceStop);
			if (frame < 0 || s.reduceMotion()) continue;
			int row = eggRow(s, b.arg(SlotBeats.SPIN), r);
			int cx = s.cellX(r) + s.cell() / 2;
			int cy = s.cellY(row) + s.cell() / 2;
			int len = 4 + frame * 5;
			SlotDraw.line(g, cx - len, cy - len / 2.0, cx + len, cy + len / 2.0, 1, 0xFFFFF4FF);
			SlotDraw.line(g, cx, cy - len, cx - len / 2.0, cy + len, 1, 0xDDFFE0FF);
		}
	}

	private static void drawTallEgg(SlotStage s, GuiGraphicsExtractor g, int r, double scaleY, int anchorRow, double chainA, double chainS,
			boolean allThree) {
		int cell = s.cell();
		int x = s.cellX(r);
		int h = s.windowH();
		float anchorY = s.cellY(anchorRow) + cell / 2f;
		g.pose().pushMatrix();
		g.pose().translate(0, anchorY);
		g.pose().scale(1, (float) scaleY);
		g.pose().translate(0, -anchorY);
		// the tall egg column: violet glow, a big egg in the middle and motes of light
		g.fillGradient(x + 1, s.wy(), x + cell - 1, s.wy() + h, 0xF02A0A44, 0xF0120622);
		double shimmer = 0.5 + 0.5 * Math.sin(s.now() / 260.0);
		if (CabinetArt.ART) {
			// generated tall egg: 8 shimmer frames (12.5 fps) + 3 grow keys; drawn 1:1 (40 × 132) or scaled in compact
			int frame = scaleY < 0.99 ? 8 + Math.min(2, (int) Math.floor((scaleY - 1 / 3.0) / (2 / 3.0) * 3)) : (int) ((s.now() / 80) % 8);
			int w = cell < 40 ? 29 : 40;
			SlotSprites.sheet(g, SlotSprites.EGG_TALL, 440, 132, frame * 40, 0, 40,
				132, x + (cell - w) / 2, s.wy(), w, h, 0xFFFFFFFF);
			g.pose().popMatrix();
			if (chainA > 0) {
				float ccx = x + cell / 2f;
				float ccy = s.wy() + h / 2f;
				g.pose().pushMatrix();
				g.pose().translate(ccx, ccy);
				g.pose().scale((float) chainS, (float) chainS);
				g.pose().translate(-ccx, -ccy);
				int tint = allThree ? SlotDraw.lerp(0xFFFFFFFF, 0xFFFFE0FF, 0.5 + 0.5 * Math.sin(s.now() / 150.0)) : 0xFFFFFFFF;
				if (CabinetArt.ANIMATED_STRIPS) {
					SlotSprites.blit(g, SlotSprites.STICKY, x, s.wy() - 2, cell, h + 4, SlotDraw.withAlpha(tint, chainA));
				} else {
					int chain = SlotDraw.withAlpha(allThree ? tint : AMETHYST, chainA);
					SlotDraw.frame(g, x + 1, s.wy() + 1, cell - 2, h - 2, 2, chain);
					for (int i = 0; i < h / 8; i++) {
						int ly = s.wy() + 4 + i * 8;
						int c = (i + s.now() / 120) % 9 == 0 ? SlotDraw.withAlpha(0xFFFFFFFF, chainA) : SlotDraw.withAlpha(0xFFD696FF, chainA * 0.9);
						g.fill(x + 1, ly, x + 4, ly + 4, c);
						g.fill(x + cell - 4, ly + 4, x + cell - 1, ly + 8, c);
					}
				}
				g.pose().popMatrix();
			}
			return;
		}
		g.fillGradient(x + 4, s.wy() + 4, x + cell - 4, s.wy() + h - 4, SlotDraw.withAlpha(AMETHYST, 0.18 + 0.12 * shimmer), 0x00000000);
		SymbolSheet.draw(g, s.machine(), 0, SymbolSheet.COL_WIN + (int) ((s.now() / 80) % 8), x, s.cellY(1), cell, 1.35, 1.35, 1);
		int crackX = x + cell / 2;
		SlotDraw.line(g, crackX, s.wy() + cell - 4, crackX + 3, s.wy() + h - cell + 4, 1, SlotDraw.withAlpha(0xFFFFF4FF, 0.35 + 0.35 * shimmer));
		g.pose().popMatrix();
		if (chainA > 0) {
			float cx = x + cell / 2f;
			float cy = s.wy() + h / 2f;
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().scale((float) chainS, (float) chainS);
			g.pose().translate(-cx, -cy);
			int chain = SlotDraw.withAlpha(allThree ? SlotDraw.lerp(AMETHYST, 0xFFFFFFFF, 0.3 + 0.3 * Math.sin(s.now() / 150.0)) : AMETHYST, chainA);
			SlotDraw.frame(g, x + 1, s.wy() + 1, cell - 2, h - 2, 2, chain);
			// chain links along the sides with a travelling glint
			for (int i = 0; i < h / 8; i++) {
				int ly = s.wy() + 4 + i * 8;
				boolean glint = (i + s.now() / 120) % 9 == 0;
				int c = glint ? SlotDraw.withAlpha(0xFFFFFFFF, chainA) : SlotDraw.withAlpha(0xFFD696FF, chainA * 0.9);
				g.fill(x + 1, ly, x + 4, ly + 4, c);
				g.fill(x + cell - 4, ly + 4, x + cell - 1, ly + 8, c);
			}
			g.pose().popMatrix();
		}
	}
}
