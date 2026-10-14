package dev.nezo.burmaldaholic.games.extras.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;

/**
 * The PlayArea of the v2 extras screens (REDESIGN.md §7.7, mockup {@code v2_plinko_480.png}): the game object (Plinko
 * board, wheel, coin, ticket, dice arena) centred in grid columns 1–9 and an options column in 10–12 (risk, last
 * results, top prize, legend, prize table, heads / tails). Pure; audited at every size of ui-v2.md §11.1 by
 * {@code ExtrasLayoutTest}: nothing leaves the PlayArea, the column and the object never overlap, buttons hold their
 * labels in EN and RU.
 *
 * <p>Pixel art is drawn at a whole scale; when the PlayArea is shorter than the art (1366 × 768 at GUI 3, 427 × 240)
 * the object scales down by {@link Scene#scale} (&lt; 1, the only non-integer case, rare windows).
 */
public final class ExtrasLayout {
	private ExtrasLayout() {}

	/**
	 * One scene: the object box (already scaled), its scale, and the options column with its rows.
	 *
	 * @param object  where the art is drawn (its native size × {@code scale})
	 * @param scale   art scale (1 = native, &lt; 1 small windows, 2 = doubled when the PlayArea holds it twice)
	 * @param side    the options column (cols 10–12)
	 * @param row     height of one option row (buttons, pills)
	 */
	public record Scene(Box object, float scale, Box side, int row) {
		/** The {@code i}-th option row of the column starting {@code top} px below its top. */
		public Box row(int top, int i) {
			return new Box(side.x(), side.y() + top + i * (row + 2), side.w(), row);
		}

		/** Half-width cells of the column for pill grids ({@code col} 0 / 1). */
		public Box cell(int top, int i, int col) {
			int w = (side.w() - 4) / 2;
			return new Box(side.x() + col * (w + 4), side.y() + top + i * (row + 2), w, row);
		}
	}

	/** Native sizes of the scene art (GUI px). */
	public static final int PLINKO_W = 272, PLINKO_H = 204, WHEEL = 184, TICKET_W = 212, TICKET_H = 196, COIN = 150, DICE_W = 360, DICE_H = 140;

	public static int rowH(Tier t) {
		return switch (t) {
			case T1 -> 16;
			case T2 -> 20;
			case T3 -> 24;
		};
	}

	/**
	 * The object of native size {@code w × h} centred in columns 1..{@code lastCol} of the PlayArea (inset 2 px), scaled
	 * down to fit when needed; the options column is columns {@code lastCol + 1}..12.
	 */
	public static Scene scene(TableGrid g, int w, int h, int lastCol, boolean allowDouble) {
		Box play = g.play;
		// a 2 px breath above and below the art when the PlayArea has it, else the art may touch the bars (never scale for 4 px)
		int inset = play.h() >= h + 4 ? 2 : 0;
		int top = play.y() + inset;
		int availH = play.h() - 2 * inset;
		Box left = g.columns(1, lastCol, top, availH);
		float scale = Math.min(1f, Math.min(availH / (float) h, left.w() / (float) w));
		if (allowDouble && availH >= 2 * h && left.w() >= 2 * w) scale = 2f;
		int ow = Math.round(w * scale);
		int oh = Math.round(h * scale);
		Box object = new Box(left.x() + (left.w() - ow) / 2, top + (availH - oh) / 2, ow, oh);
		Box side = lastCol >= 12 ? Box.EMPTY : g.columns(lastCol + 1, 12, play.y() + 8, play.h() - 12);
		return new Scene(object, scale, side, rowH(g.tier));
	}

	public static Scene plinko(TableGrid g) {
		return scene(g, PLINKO_W, PLINKO_H, 9, false);
	}

	public static Scene wheel(TableGrid g) {
		return scene(g, WHEEL, WHEEL, 7, false);
	}

	public static Scene scratch(TableGrid g) {
		return scene(g, TICKET_W, TICKET_H, 8, false);
	}

	public static Scene coin(TableGrid g) {
		return scene(g, COIN, COIN, 8, false);
	}

	/** Dice Duel: the arena over the full width (plates above each lane); the options column holds the challenges. */
	public static Scene dice(TableGrid g) {
		Box play = g.play;
		int row = rowH(g.tier);
		Box all = g.columns(1, 12, play.y() + 2, play.h() - 4);
		int strip = 2 * row + 8;
		int availH = all.h() - strip - 4;
		float scale = Math.min(1f, Math.min(availH / (float) DICE_H, all.w() / (float) DICE_W));
		int ow = Math.round(DICE_W * scale);
		int oh = Math.round(DICE_H * scale);
		int top = all.y() + strip + 4 + (availH - oh) / 2;
		Box object = new Box(all.x() + (all.w() - ow) / 2, top, ow, oh);
		// the PvP strip spans the arena's width above it
		Box side = new Box(object.x(), all.y() + 2, object.w(), strip);
		return new Scene(object, scale, side, row);
	}
}
