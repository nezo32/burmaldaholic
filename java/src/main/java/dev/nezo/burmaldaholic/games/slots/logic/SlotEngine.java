package dev.nezo.burmaldaholic.games.slots.logic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Slot machine rules (GAME_DESIGN.md §8.1). PURE: no Minecraft imports.
 *
 * <ul>
 *   <li>3×3 window, every cell drawn independently from the machine's weights (no reel strips), so every
 *       payline has the same distribution and per-line RTP = machine RTP.</li>
 *   <li>A line pays its single best combination; payout = floor(multiplier × line bet).</li>
 *   <li>At most one chaos trigger per spin: Star &gt; Clock &gt; Pearl &gt; TNT/Creeper.</li>
 * </ul>
 */
public final class SlotEngine {
	/** Chaos priority (§8.1). */
	public static final List<Symbol> SPECIAL_PRIORITY = List.of(Symbol.STAR, Symbol.CLOCK, Symbol.PEARL, Symbol.TNT, Symbol.CREEPER);

	private SlotEngine() {}

	public enum LineKind {
		/** Three of a regular symbol (Wild substitutions included). */
		THREE,
		/** Three Wilds. */
		WILD,
		/** One leading Sweet Berry. */
		BERRY1,
		/** Two leading Sweet Berries. */
		BERRY2,
		/** Three natural specials (Creeper, TNT, Pearl, Clock, Star). */
		SPECIAL
	}

	/** Best result of one payline. {@code multiplier} is 0 for zero-pay specials and the progressive star. */
	public record LineResult(LineKind kind, Symbol symbol, double multiplier) {}

	/** A paying (or special) line of a spin. */
	public record LineWin(int line, LineKind kind, Symbol symbol, double multiplier, long payout) {}

	/**
	 * Evaluation of a drawn window.
	 *
	 * @param basePayout sum of line payouts (jackpot award NOT included)
	 * @param jackpotHit three natural stars on a progressive machine (awarded once per spin, §8.5)
	 * @param special    chaos trigger of this spin (highest priority), or null
	 */
	public record SpinEval(Symbol[][] grid, List<LineWin> wins, long basePayout, boolean jackpotHit, @Nullable Symbol special) {
		/** Any payline won with (wild-substituted) Redstone Sevens. */
		public boolean threeSevens() {
			return wins.stream().anyMatch(w -> w.kind() == LineKind.THREE && w.symbol() == Symbol.SEVEN);
		}
	}

	/**
	 * One payline, cells left→right (§8.1):
	 * <ol>
	 *   <li>three identical specials → special result;</li>
	 *   <li>three Wilds → Wild pay;</li>
	 *   <li>best regular S where every cell is S or Wild (Wild never substitutes for specials);</li>
	 *   <li>leading Sweet Berries (Wild does not count): 1 → partial[0], 2 → partial[1].</li>
	 * </ol>
	 *
	 * @return null when the line has no result
	 */
	public static @Nullable LineResult evaluateLine(Symbol a, Symbol b, Symbol c, SlotTable table) {
		if (a == b && b == c && a.isSpecial()) {
			double m = a == Symbol.STAR && table.progressive() ? 0 : table.pay(a);
			return new LineResult(LineKind.SPECIAL, a, m);
		}
		if (a == Symbol.WILD && b == Symbol.WILD && c == Symbol.WILD) {
			return new LineResult(LineKind.WILD, Symbol.WILD, table.pay(Symbol.WILD));
		}
		LineResult best = null;
		for (Symbol s : Symbol.values()) {
			if (!s.isRegular()) {
				continue;
			}
			if (matches(a, s) && matches(b, s) && matches(c, s)) {
				double m = table.pay(s);
				if (best == null || m > best.multiplier()) {
					best = new LineResult(LineKind.THREE, s, m);
				}
			}
		}
		if (best != null) {
			return best;
		}
		if (a == Symbol.BERRIES) {
			return b == Symbol.BERRIES
				? new LineResult(LineKind.BERRY2, Symbol.BERRIES, table.berryPartial(2))
				: new LineResult(LineKind.BERRY1, Symbol.BERRIES, table.berryPartial(1));
		}
		return null;
	}

	private static boolean matches(Symbol cell, Symbol s) {
		return cell == s || cell == Symbol.WILD;
	}

	/** floor(multiplier × lineBet), tolerant to float noise (e.g. 2.3 × 10 = 22.999999). */
	public static long linePayout(double multiplier, long lineBet) {
		return (long) Math.floor(multiplier * lineBet + 1e-9);
	}

	/** Evaluates the machine's paylines of a {@code grid[row][col]} window. */
	public static SpinEval evaluate(Symbol[][] grid, SlotTable table, long lineBet) {
		List<LineWin> wins = new ArrayList<>();
		Set<Symbol> specials = EnumSet.noneOf(Symbol.class);
		long base = 0;
		boolean jackpot = false;
		for (int line = 1; line <= table.lines(); line++) {
			LineResult r = evaluateLine(grid[Paylines.row(line, 0)][0], grid[Paylines.row(line, 1)][1], grid[Paylines.row(line, 2)][2], table);
			if (r == null) {
				continue;
			}
			long payout = linePayout(r.multiplier(), lineBet);
			base += payout;
			wins.add(new LineWin(line, r.kind(), r.symbol(), r.multiplier(), payout));
			if (r.kind() == LineKind.SPECIAL) {
				specials.add(r.symbol());
				if (r.symbol() == Symbol.STAR && table.progressive()) {
					jackpot = true;
				}
			}
		}
		Symbol special = null;
		for (Symbol s : SPECIAL_PRIORITY) {
			if (specials.contains(s)) {
				special = s;
				break;
			}
		}
		return new SpinEval(grid, List.copyOf(wins), base, jackpot, special);
	}

	/** One cell: weighted pick with {@code nextInt(totalWeight)}. */
	public static Symbol drawSymbol(SlotTable table, IntUnaryOperator nextInt) {
		int roll = nextInt.applyAsInt(table.totalWeight());
		Symbol last = null;
		for (Symbol s : table.present()) {
			last = s;
			roll -= table.weight(s);
			if (roll < 0) {
				return s;
			}
		}
		return last;
	}

	/**
	 * Draws a 3×3 window, every cell independent.
	 *
	 * @param nextInt fair uniform int in [0, bound) (e.g. {@code CasinoRng::nextInt})
	 */
	public static Symbol[][] drawGrid(SlotTable table, IntUnaryOperator nextInt) {
		if (table.empty()) {
			throw new IllegalStateException("slot machine has no symbols");
		}
		Symbol[][] g = new Symbol[3][3];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				g[r][c] = drawSymbol(table, nextInt);
			}
		}
		return g;
	}

	/** Draw + evaluate. */
	public static SpinEval spin(SlotTable table, long lineBet, IntUnaryOperator nextInt) {
		return evaluate(drawGrid(table, nextInt), table, lineBet);
	}

	/** Losing outcome for the §14 streak re-draw: returns less than the spin bet (a jackpot never counts as losing). */
	public static boolean losing(SpinEval e, long spinBet) {
		return !e.jackpotHit() && e.basePayout() < spinBet;
	}

	/** Largest possible total return of one spin (bankroll reservation, §18.2), jackpot excluded. */
	public static long worstCaseReturn(SlotTable table, long lineBet) {
		return (long) Math.ceil(table.bestLineMultiplier() * lineBet - 1e-9) * table.lines();
	}
}
