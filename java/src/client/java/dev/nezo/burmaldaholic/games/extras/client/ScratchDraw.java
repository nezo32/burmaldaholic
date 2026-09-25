package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.GuiParticlePool;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.games.extras.logic.anim.ScratchMask;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Scratch foil and symbols (visual/extras.md §6; solo tickets and Scratch Showdown cards): the foil drawn only where
 * the {@link ScratchMask} still covers, the per-theme scratch-edge autotile on torn sub-tiles (§6.4; untouched rows are
 * merged into one blit), the shimmer on untouched cells, the symbol sheets and the foil flakes (GUI particles).
 */
public final class ScratchDraw {
	public static final Identifier FOIL = Kit.sheet("extras/scratch_foil");
	public static final Identifier FOIL_SMALL = Kit.sheet("extras/scratch_foil_showdown");
	public static final Identifier EDGES = Kit.sheet("extras/scratch_edges");
	public static final Identifier SCUFF = Kit.sheet("extras/scratch_scuff");
	public static final Identifier SYM_20 = Kit.sheet("extras/scratch_symbols_20");
	public static final Identifier SYM_40 = Kit.sheet("extras/scratch_symbols_40");
	public static final Identifier SYM_40_WIN = Kit.sheet("extras/scratch_symbols_40_win");
	public static final Identifier FLAKES = Kit.sheet("extras/flakes");
	public static final Identifier TORN = Kit.sheet("extras/torn_corner");
	public static final Identifier PUFF = Kit.sheet("extras/explosion_puff");
	/** Symbol order of the sheets: coal, iron, gold, emerald, diamond, nether star, creeper, rabbit's foot, charred. */
	public static final int CREEPER = 6;
	public static final int FOOT = 7;
	/** Edge rows: basic, gold, showdown. */
	public static final int ROW_BASIC = 0;
	public static final int ROW_GOLD = 1;
	public static final int ROW_SHOWDOWN = 2;

	private ScratchDraw() {}

	/**
	 * Foil over a cell at (x, y). {@code foilV} selects the theme's foil (solo: 0 basic, 44 gold); {@code small} = the
	 * 24² Showdown foil.
	 */
	public static void foil(GuiGraphicsExtractor g, ScratchMask m, int x, int y, boolean small, int foilV, int edgeRow, boolean shimmer) {
		int w = m.nx() * ScratchMask.TILE;
		int h = m.ny() * ScratchMask.TILE;
		Identifier tex = small ? FOIL_SMALL : FOIL;
		int texH = small ? 24 : 88;
		if (m.untouched()) {
			Kit.region(g, tex, w, texH, 0, foilV, w, h, x, y);
			if (shimmer) Kit.sprite(g, Kit.extras(small ? "scratch_foil_shimmer_small" : "scratch_foil_shimmer"), x, y, w, h);
			return;
		}
		if (m.isClear()) return;
		for (int b = 0; b < m.ny(); b++) {
			int a = 0;
			while (a < m.nx()) {
				if (!m.covered(a, b)) {
					a++;
					continue;
				}
				// a run of covered tiles without torn neighbours: one blit
				int start = a;
				while (a < m.nx() && m.covered(a, b) && m.edge(a, b) == 0) a++;
				if (a > start) {
					Kit.region(g, tex, w, texH, start * 4, foilV + b * 4, (a - start) * 4, 4, x + start * 4, y + b * 4);
					continue;
				}
				int e = m.edge(a, b);
				Kit.region(g, tex, w, texH, a * 4, foilV + b * 4, 4, 4, x + a * 4, y + b * 4);
				Kit.region(g, EDGES, 64, 12, e * 4, edgeRow * 4, 4, 4, x + a * 4, y + b * 4);
				a++;
			}
		}
	}

	/** Symbol {@code s} at 40 px (solo cells), lit for the winning trio. */
	public static void symbol40(GuiGraphicsExtractor g, int s, int x, int y, boolean lit) {
		Kit.region(g, lit ? SYM_40_WIN : SYM_40, 360, 40, Math.max(0, Math.min(8, s)) * 40, 0, 40, 40, x, y);
	}

	public static void symbol20(GuiGraphicsExtractor g, int s, int x, int y) {
		Kit.region(g, SYM_20, 180, 20, Math.max(0, Math.min(8, s)) * 20, 0, 20, 20, x, y);
	}

	/** Flake sprite ids of the particle pool: 0–3 foil, 4–5 embers, 6–7 sparks. */
	public static void flakes(GuiGraphicsExtractor g, GuiParticlePool pool, long now) {
		for (int i = 0; i < pool.count(); i++) {
			float age = pool.age(i, now);
			double a = Math.max(0, 1 - age / 400.0);
			int s = pool.sprite(i);
			Kit.region(g, FLAKES, 32, 4, (s & 7) * 4, 0, 4, 4, Math.round(pool.x(i)), Math.round(pool.y(i)), 4, 4, Kit.fade(a));
		}
	}

	/** Sprays {@code n} flakes from (x, y) with a seeded spread. */
	public static void spray(GuiParticlePool pool, float x, float y, int n, int seed, long now, boolean embers) {
		dev.nezo.burmaldaholic.core.anim.SeedMix.FxRng rng = new dev.nezo.burmaldaholic.core.anim.SeedMix.FxRng(seed);
		for (int i = 0; i < n; i++) {
			float vx = (float) ((rng.nextDouble() - 0.5) * 0.12);
			float vy = (float) (-0.05 - rng.nextDouble() * 0.08);
			int sprite = embers ? 4 + rng.nextInt(4) : rng.nextInt(4);
			pool.spawn(sprite, x + (float) (rng.nextDouble() * 6 - 3), y + (float) (rng.nextDouble() * 6 - 3), vx, vy, 0.0004f, now, 400);
		}
	}
}
