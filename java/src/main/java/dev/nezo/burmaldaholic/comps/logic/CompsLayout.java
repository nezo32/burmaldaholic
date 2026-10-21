package dev.nezo.burmaldaholic.comps.logic;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout of the Comps tab (EC-SPEC §4.4), in layout px of the counter body. Left column: points, rate, today's
 * theoretical loss, cashback estimate and the daily card with its Claim button; right column: one row per reward
 * with its name, cost / limit and a Redeem button. Adapts to the body size (rows shrink to fit, never below 12 px).
 */
public final class CompsLayout {
	public static final int PAD = 8;
	public static final int LINE = 12;
	public static final int MAX_ROW = 20;
	public static final int MIN_ROW = 12;
	public static final int REDEEM_W = 56;
	/** The shell's result line takes the bottom 12 px of the body (+2 gap). */
	public static final int RESULT_H = 14;

	/**
	 * @param lines   the four readout lines (points, rate, theo, cashback)
	 * @param card    the daily card
	 * @param cardTitle  / cardText inside the card
	 * @param claim   the Claim button
	 * @param header  "Rewards" header
	 * @param rows    one box per reward row
	 * @param names   name area of each row
	 * @param costs   cost / limit area of each row
	 * @param redeem  Redeem button of each row
	 */
	public record Layout(List<Box> lines, Box card, Box cardTitle, Box cardText, Box claim, Box header, List<Box> rows, List<Box> names,
		List<Box> costs, List<Box> redeem) {}

	private CompsLayout() {}

	public static Layout compute(Box body, int rewardCount) {
		int n = Math.max(0, rewardCount);
		int lw = Math.max(120, Math.min(180, body.w() * 38 / 100));
		Box left = new Box(body.x() + PAD, body.y() + 6, lw, Math.max(0, body.h() - 6 - RESULT_H));
		List<Box> lines = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			lines.add(new Box(left.x(), left.y() + i * (LINE + 2), lw, LINE));
		}
		int cardY = left.y() + 4 * (LINE + 2) + 6;
		int cardH = Math.min(64, Math.max(48, left.bottom() - cardY));
		Box card = new Box(left.x(), cardY, lw, cardH);
		Box cardTitle = new Box(card.x() + 4, card.y() + 3, lw - 8, LINE);
		Box cardText = new Box(card.x() + 4, card.y() + 3 + LINE, lw - 8, LINE);
		Box claim = new Box(card.x() + 4, card.bottom() - 22, lw - 8, 18);

		int rx = left.right() + PAD;
		int rw = Math.max(0, body.right() - PAD - rx);
		Box header = new Box(rx, body.y() + 6, rw, LINE);
		int top = header.bottom() + 2;
		int avail = Math.max(0, body.bottom() - RESULT_H - top);
		int rowH = n == 0 ? MAX_ROW : Math.max(MIN_ROW, Math.min(MAX_ROW, avail / n));
		List<Box> rows = new ArrayList<>(), names = new ArrayList<>(), costs = new ArrayList<>(), redeem = new ArrayList<>();
		int costW = Math.min(70, Math.max(40, rw / 4));
		for (int i = 0; i < n; i++) {
			Box row = new Box(rx, top + i * rowH, rw, rowH - 2);
			Box btn = new Box(row.right() - REDEEM_W - 1, row.y() + 1, REDEEM_W, row.h() - 2);
			Box cost = new Box(btn.x() - 4 - costW, row.y(), costW, row.h());
			Box name = new Box(row.x() + 3, row.y(), Math.max(0, cost.x() - 4 - (row.x() + 3)), row.h());
			rows.add(row);
			names.add(name);
			costs.add(cost);
			redeem.add(btn);
		}
		return new Layout(List.copyOf(lines), card, cardTitle, cardText, claim, header, List.copyOf(rows), List.copyOf(names), List.copyOf(costs),
			List.copyOf(redeem));
	}
}
