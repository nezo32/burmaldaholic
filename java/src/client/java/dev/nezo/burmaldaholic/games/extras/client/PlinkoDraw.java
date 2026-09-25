package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Plinko board of visual/extras.md §5 (solo and Plinko Battle): the 272 × 204 cabinet, the 78 pegs (idle / hit /
 * afterglow), the chute gate, the 13 bin caps (5 tiers, unlit / lit, the multiplier as text), the rolling ball with its
 * ghost trail, and the 56² mini boards. Board-local positions come from {@link PlinkoAnim}.
 */
public final class PlinkoDraw {
	public static final Identifier BOARD = Kit.sheet("extras/plinko_board");
	public static final Identifier PEG = Kit.sheet("extras/plinko_peg");
	public static final Identifier BALL = Kit.sheet("extras/plinko_ball");
	public static final Identifier BINS = Kit.sheet("extras/plinko_bins");
	public static final Identifier CHUTE = Kit.sheet("extras/plinko_chute");
	public static final Identifier MINI = Kit.sheet("extras/plinko_mini_board");
	public static final int BOARD_W = 272;
	public static final int BOARD_H = 204;

	private PlinkoDraw() {}

	public static void board(GuiGraphicsExtractor g, int bx, int by) {
		Kit.region(g, BOARD, BOARD_W, BOARD_H, 0, 0, BOARD_W, BOARD_H, bx, by);
	}

	/** Pegs; {@code state(row, j)} 0 idle, 1 hit, 2 afterglow. */
	public static void pegs(GuiGraphicsExtractor g, int bx, int by, PegState state) {
		for (int r = 0; r < PlinkoAnim.ROWS; r++) {
			for (int j = 0; j <= r; j++) {
				int st = state == null ? 0 : state.at(r, j);
				int x = (int) Math.round(PlinkoAnim.pegX(r, j)) - 3;
				int y = (int) Math.round(PlinkoAnim.pegY(r)) - 3;
				Kit.region(g, PEG, 21, 7, st * 7, 0, 7, 7, bx + x, by + y);
			}
		}
	}

	@FunctionalInterface
	public interface PegState {
		int at(int row, int j);
	}

	public static void chute(GuiGraphicsExtractor g, int bx, int by, boolean open) {
		Kit.region(g, CHUTE, 56, 16, open ? 28 : 0, 0, 28, 16, bx + 122, by + 4);
	}

	/**
	 * Bin caps with their multipliers; {@code lit} = the landed bin (−1 none), pressed {@code press} px, its label
	 * popping at {@code pop} (−1 none). {@code hidden} caps are foil (Final Ball) without numbers.
	 */
	public static void bins(GuiGraphicsExtractor g, Font font, int bx, int by, double[] table, int lit, double press, double pop, boolean hidden) {
		for (int k = 0; k < 13 && k < table.length; k++) {
			int x = bx + (int) Math.round(PlinkoAnim.binX(k)) - 9;
			int y = by + PlinkoAnim.CAP_Y + (k == lit ? (int) Math.round(2 * press) : 0);
			if (hidden) {
				Kit.sprite(g, Kit.extras("plinko_bin_hidden"), x, y, 18, 14);
				continue;
			}
			cap(g, font, table[k], x, y, k == lit, k == lit ? pop : -1);
		}
	}

	/** One cap (18 × 14) with its multiplier text. */
	public static void cap(GuiGraphicsExtractor g, Font font, double mult, int x, int y, boolean lit, double pop) {
		int t = PlinkoAnim.tier(mult);
		Kit.region(g, BINS, 90, 28, t * 18, lit ? 14 : 0, 18, 14, x, y);
		Component label = Texts.decimal(Payouts.formatMultiplier(mult));
		if (pop > 0 && pop < 1 && !Kit.reduceMotion()) {
			double s = 1 + 0.35 * Math.sin(Math.PI * pop);
			g.pose().pushMatrix();
			g.pose().translate(x + 9, y + 7);
			g.pose().scale((float) s, (float) s);
			g.pose().translate(-(x + 9), -(y + 7));
			Kit.centeredFit(g, font, label, x + 9, y + 3, 16, lit ? Kit.WHITE : Kit.BONE, false);
			g.pose().popMatrix();
		} else {
			Kit.centeredFit(g, font, label, x + 9, y + 3, 16, lit ? Kit.WHITE : Kit.BONE, false);
		}
	}

	/** The ball centred at board-local (x, y), roll frame, squash, gold (underdog) row. */
	public static void ball(GuiGraphicsExtractor g, int bx, int by, double x, double y, int roll, double sx, double sy, boolean gold, double alpha) {
		g.pose().pushMatrix();
		g.pose().translate((float) (bx + x), (float) (by + y));
		g.pose().scale((float) sx, (float) sy);
		Kit.region(g, BALL, 36, 18, (roll & 3) * 9, gold ? 9 : 0, 9, 9, -4, -4, 9, 9, Kit.fade(alpha));
		g.pose().popMatrix();
	}

	/** Dotted path of the first {@code rows} rows (reduce motion, skip flash). */
	public static void dottedPath(GuiGraphicsExtractor g, int bx, int by, int path, int rows, int color) {
		for (int r = 0; r < rows && r < PlinkoAnim.ROWS; r++) {
			double x0 = PlinkoAnim.pegX(r, PlinkoAnim.rights(path, r));
			double y0 = PlinkoAnim.pegY(r) - PlinkoAnim.BALL_ABOVE;
			double x1 = r + 1 < PlinkoAnim.ROWS ? PlinkoAnim.pegX(r + 1, PlinkoAnim.rights(path, r + 1)) : PlinkoAnim.binX(PlinkoAnim.bin(path));
			double y1 = PlinkoAnim.pegY(r + 1) - PlinkoAnim.BALL_ABOVE;
			for (int k = 0; k < 4; k++) {
				double t = k / 4.0;
				int px = (int) Math.round(x0 + (x1 - x0) * t);
				int py = (int) Math.round(y0 + (y1 - y0) * t);
				g.fill(bx + px, by + py, bx + px + 2, by + py + 2, color);
			}
		}
	}

	/** A 56² mini board with its ball after {@code rowsDone} rows and the lamp of {@code lit} (−1 none). */
	public static void mini(GuiGraphicsExtractor g, int x, int y, int path, double rowsDone, int lit, boolean gold, boolean edgeFlash) {
		Kit.region(g, MINI, 56, 56, 0, 0, 56, 56, x, y);
		if (lit >= 0) {
			int lx = x + (int) Math.round(28 + (lit - 6) * 4 - 1.5);
			g.fill(lx, y + 45, lx + 3, y + 48, edgeFlash ? Kit.GOLD : Kit.WHITE);
		}
		if (rowsDone >= 0) {
			double[] u = PlinkoAnim.unit(path, rowsDone, PlinkoAnim.ROWS);
			int bxp = x + (int) Math.round(28 + u[0] * 4 - 0.5);
			int byp = rowsDone >= PlinkoAnim.ROWS ? y + 42 : y + 3 + (int) Math.round(u[1] * 3);
			g.fill(bxp - 1, byp - 1, bxp + 2, byp + 2, gold ? Kit.GOLD : 0xFFE0303A);
		}
	}
}
