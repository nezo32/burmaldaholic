package dev.nezo.burmaldaholic.games.roulette.logic;

import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The racetrack (docs/design/visual/tables.md §3.3): a stadium of the 37 pockets in wheel order and the four French
 * call-bet sections. Geometry = the generated art ({@code racetrackCells()} of
 * {@code tools/assets/modules/tables/roulette.mjs}, 278 × 44), so the screen hit-tests the shapes the art draws.
 *
 * <p>Call bets are a NICE extension of GAME_DESIGN §9: every one is split into STANDARD bets before it reaches the
 * round ({@link #bets}), so rules, limits and payouts are unchanged. A number is a "neighbours" bet: five straight-ups
 * (the number and two pockets on each side). Pure.
 */
public final class Racetrack {
	public static final int W = 278;
	public static final int H = 44;
	/** Cell ring thickness. */
	public static final int T = 13;
	private static final double CAP = H / 2.0;
	private static final double STRAIGHT = W - 2 * CAP;
	/** Inner section dividers (Tier | Orphelins | Voisins | Zero). */
	public static final int[] DIVIDERS = {84, 150, 222};

	public enum Section {
		TIER, ORPHELINS, VOISINS, ZERO;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Optional<Section> byId(String id) {
			for (Section s : values()) {
				if (s.id().equals(id)) {
					return Optional.of(s);
				}
			}
			return Optional.empty();
		}

		/** Centre x of the section's label inside the stadium. */
		public int labelX() {
			return switch (this) {
				case TIER -> 50;
				case ORPHELINS -> 117;
				case VOISINS -> 186;
				case ZERO -> 243;
			};
		}
	}

	/** One number cell (image-local, fractional like the art). */
	public record Cell(int n, double x, double y, double w, double h, String part) {
		public double cx() {
			return part.equals("right") ? W - CAP + 6.5 : part.equals("left") ? CAP - 6 : x + w / 2;
		}

		public double cy() {
			return y + h / 2;
		}
	}

	private static final List<Cell> CELLS = buildCells();

	private Racetrack() {}

	private static List<Cell> buildCells() {
		List<Cell> cells = new ArrayList<>();
		List<Integer> order = Wheel.ORDER;
		int i24 = order.indexOf(24);
		int i26 = order.indexOf(26);
		for (int i = 0; i <= i26 - i24; i++) {
			cells.add(new Cell(order.get(i24 + i), CAP + STRAIGHT * i / 17, 0, STRAIGHT / 17, T, "top"));
		}
		cells.add(new Cell(0, W - CAP, 0, CAP, H / 2.0, "right"));
		cells.add(new Cell(32, W - CAP, H / 2.0, CAP, H / 2.0, "right"));
		int i15 = order.indexOf(15);
		int i23 = order.indexOf(23);
		for (int i = 0; i <= i23 - i15; i++) {
			cells.add(new Cell(order.get(i15 + i), W - CAP - STRAIGHT * (i + 1) / 16, H - T, STRAIGHT / 16, T, "bottom"));
		}
		cells.add(new Cell(10, 0, H / 2.0, CAP, H / 2.0, "left"));
		cells.add(new Cell(5, 0, 0, CAP, H / 2.0, "left"));
		return List.copyOf(cells);
	}

	public static List<Cell> cells() {
		return CELLS;
	}

	public static Cell cell(int n) {
		for (Cell c : CELLS) {
			if (c.n() == n) {
				return c;
			}
		}
		throw new IllegalArgumentException("not a pocket: " + n);
	}

	private static boolean inStadium(double px, double py, double inset) {
		double cx = Math.min(Math.max(px, CAP), W - CAP);
		return Math.hypot(px - cx, py - CAP) <= CAP - inset;
	}

	/** Number under image-local (x, y), if on the ring. */
	public static Optional<Integer> numberAt(double x, double y) {
		if (!inStadium(x, y, 0) || inStadium(x, y, T)) {
			return Optional.empty();
		}
		for (Cell c : CELLS) {
			boolean in = switch (c.part()) {
				case "top", "bottom" -> x >= c.x() && x < c.x() + c.w() && y >= c.y() && y < c.y() + c.h();
				case "right" -> x >= W - CAP && y >= c.y() && y < c.y() + c.h();
				default -> x < CAP && y >= c.y() && y < c.y() + c.h();
			};
			if (in) {
				return Optional.of(c.n());
			}
		}
		return Optional.empty();
	}

	/** Section under image-local (x, y), if inside the inner field. */
	public static Optional<Section> sectionAt(double x, double y) {
		if (!inStadium(x, y, T)) {
			return Optional.empty();
		}
		if (x < DIVIDERS[0]) {
			return Optional.of(Section.TIER);
		}
		if (x < DIVIDERS[1]) {
			return Optional.of(Section.ORPHELINS);
		}
		if (x < DIVIDERS[2]) {
			return Optional.of(Section.VOISINS);
		}
		return Optional.of(Section.ZERO);
	}

	/** {@code n} and {@code k} pockets on each side of it, in wheel order. */
	public static List<Integer> neighbours(int n, int k) {
		int i = Wheel.wheelIndex(n);
		List<Integer> out = new ArrayList<>();
		for (int d = -k; d <= k; d++) {
			out.add(Wheel.ORDER.get(Math.floorMod(i + d, Wheel.POCKETS)));
		}
		return out;
	}

	/** The standard spots of a call bet (repeated spots carry two chips). */
	public static List<Spot> spots(Section s) {
		return switch (s) {
			case TIER -> List.of(split(5, 8), split(10, 11), split(13, 16), split(23, 24), split(27, 30), split(33, 36));
			case ORPHELINS -> List.of(Spot.of(BetType.STRAIGHT, 1), split(6, 9), split(14, 17), split(17, 20), split(31, 34));
			case VOISINS -> List.of(Spot.of(BetType.TRIO, 0, 2, 3), Spot.of(BetType.TRIO, 0, 2, 3), split(4, 7), split(12, 15), split(18, 21),
				split(19, 22), split(32, 35), Spot.of(BetType.CORNER, 25, 26, 28, 29), Spot.of(BetType.CORNER, 25, 26, 28, 29));
			case ZERO -> List.of(split(0, 3), split(12, 15), Spot.of(BetType.STRAIGHT, 26), split(32, 35));
		};
	}

	private static Spot split(int a, int b) {
		return Spot.of(BetType.SPLIT, a, b);
	}

	/** Standard bets of a call bet: {@code unit} chips per spot (merged; Voisins' double spots carry 2 units). */
	public static List<Bet> bets(Section s, long unit) {
		return Bets.mergeAll(List.of(), spots(s).stream().map(sp -> new Bet(sp, unit)).toList());
	}

	/** Neighbours bet: five straight-ups of {@code unit}. */
	public static List<Bet> neighbourBets(int n, long unit) {
		return neighbours(n, 2).stream().map(x -> new Bet(Spot.of(BetType.STRAIGHT, x), unit)).toList();
	}

	/** Every number a call bet covers (hover highlight). */
	public static Set<Integer> covered(Section s) {
		Set<Integer> out = new LinkedHashSet<>();
		for (Spot sp : spots(s)) {
			out.addAll(sp.numbers());
		}
		return out;
	}
}
