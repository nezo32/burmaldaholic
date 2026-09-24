package dev.nezo.burmaldaholic.games.baccarat.logic;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Chemin de fer bank of one table (GAME_DESIGN §20.9). PURE bookkeeping — who holds the bank, how much
 * is escrowed, the punters' bets against it and the settlement of a coup. Money itself moves in the
 * block entity.
 *
 * @param <K> player key (UUID in game, strings in tests)
 */
public final class ChemmyBank<K> {
	private K banker;
	/** Escrowed bank B. */
	private long bank;
	/** Chips the banker put in (the returned part up to this is a transfer, the rest a payout). */
	private long invested;
	/** Winning coups in a row with this bank (bank_holder advancement). */
	private int wins;
	/** Punter bets in placement order. */
	private final LinkedHashMap<K, Long> punts = new LinkedHashMap<>();
	private K banco;

	public K banker() {
		return banker;
	}

	public boolean held() {
		return banker != null;
	}

	public long bank() {
		return bank;
	}

	public long invested() {
		return invested;
	}

	public int wins() {
		return wins;
	}

	public Map<K, Long> punts() {
		return punts;
	}

	public long punted() {
		long s = 0;
		for (long v : punts.values()) {
			s += v;
		}
		return s;
	}

	public K banco() {
		return banco;
	}

	/** A player posts a bank of {@code amount} (already escrowed by the caller). */
	public void take(K who, long amount) {
		banker = who;
		bank = amount;
		invested = amount;
		wins = 0;
		punts.clear();
		banco = null;
	}

	/** Restores a saved bank. */
	public void restore(K who, long amount, long investedAmount, int winsInRow) {
		banker = who;
		bank = Math.max(0, amount);
		invested = Math.max(0, investedAmount);
		wins = Math.max(0, winsInRow);
	}

	/** Ends the bank; returns the amount to give back to the banker (the punts must be settled or refunded first). */
	public long close() {
		long back = bank;
		banker = null;
		bank = 0;
		invested = 0;
		wins = 0;
		punts.clear();
		banco = null;
		return back;
	}

	/** {@code C = min(B, banker's max)} — what punters may bet this coup. */
	public long coverage(long bankerMax) {
		return Math.max(0, Math.min(bank, bankerMax));
	}

	public long open(long bankerMax) {
		return Math.max(0, coverage(bankerMax) - punted());
	}

	public enum PuntError {
		INVALID, TOO_LOW, COVERAGE, OVER_MAX, BANCO_CALLED, IS_BANKER
	}

	public record Punt(long accepted, Optional<PuntError> error, long limit) {
		static Punt ok(long amount) {
			return new Punt(amount, Optional.empty(), 0);
		}

		static Punt fail(PuntError e, long limit) {
			return new Punt(0, Optional.of(e), limit);
		}
	}

	/**
	 * A punter adds {@code amount} against the bank: ≥ {@code min}, own total ≤ {@code punterMax}; the bet
	 * that crosses the coverage is snapped down to the open coverage (§20.9).
	 * Nothing is changed on error; on success the caller escrows {@link Punt#accepted()}.
	 */
	public Punt checkPunt(K who, long amount, long min, long punterMax, long bankerMax) {
		if (who.equals(banker)) {
			return Punt.fail(PuntError.IS_BANKER, 0);
		}
		if (banco != null) {
			return Punt.fail(PuntError.BANCO_CALLED, 0);
		}
		if (amount <= 0) {
			return Punt.fail(PuntError.INVALID, 0);
		}
		long mine = punts.getOrDefault(who, 0L);
		long roomMax = punterMax - mine;
		if (roomMax <= 0) {
			return Punt.fail(PuntError.OVER_MAX, punterMax);
		}
		long roomCoverage = open(bankerMax);
		if (roomCoverage <= 0) {
			return Punt.fail(PuntError.COVERAGE, 0);
		}
		if (amount > roomMax && roomMax < roomCoverage) {
			return Punt.fail(PuntError.OVER_MAX, punterMax);
		}
		long accepted = Math.min(amount, roomCoverage);
		if (mine + accepted < min) {
			return accepted < amount ? Punt.fail(PuntError.COVERAGE, open(bankerMax)) : Punt.fail(PuntError.TOO_LOW, min);
		}
		return Punt.ok(accepted);
	}

	/** Undoes a Banco call whose escrow failed (the refunded punts stay refunded). */
	public void cancelBanco() {
		banco = null;
	}

	public void addPunt(K who, long amount) {
		punts.merge(who, amount, Long::sum);
	}

	public long removePunt(K who) {
		Long v = punts.remove(who);
		return v == null ? 0 : v;
	}

	/** Banco: {@code who} plays the whole coverage alone; returns the other punts to refund (caller refunds them). */
	public Map<K, Long> banco(K who) {
		Map<K, Long> others = new LinkedHashMap<>(punts);
		punts.clear();
		banco = who;
		return others;
	}

	/** Result of a settled coup. */
	public record Settlement<K>(Map<K, Long> punterReturns, long bankerWin, long rake, long bankLoss) {
		/** Net change of the bank. */
		public long bankDelta() {
			return bankerWin - rake - bankLoss;
		}
	}

	/**
	 * Settles the punts (§20.9): Banker hand wins → the bank wins {@code W = Σ stakes}, the house keeps
	 * {@code floor(W × rake)}, {@code B += W − rake}; Player hand wins → every punter is paid 1:1 from the
	 * bank; Tie → every stake pushes. Clears the punts and the Banco call; counts the bank's winning run.
	 */
	public Settlement<K> settle(Side winner, int rakeBp) {
		Map<K, Long> returns = new LinkedHashMap<>();
		long w = punted();
		long rake = 0;
		long win = 0;
		long loss = 0;
		switch (winner) {
			case BANKER -> {
				win = w;
				rake = w * Math.max(0, Math.min(10_000, rakeBp)) / 10_000;
				punts.keySet().forEach(k -> returns.put(k, 0L));
				wins++;
			}
			case PLAYER -> {
				loss = w;
				punts.forEach((k, v) -> returns.put(k, 2 * v));
				wins = 0;
			}
			case TIE -> punts.forEach(returns::put);
		}
		bank += win - rake - loss;
		punts.clear();
		banco = null;
		return new Settlement<>(returns, win, rake, loss);
	}
}
