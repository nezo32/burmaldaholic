package dev.nezo.burmaldaholic.games.baccarat.logic;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Coup;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.Map;

/**
 * Payouts of a Punto Banco coup (GAME_DESIGN §20.1, §20.6). PURE, integer arithmetic only: the Banker
 * commission is kept in basis points so {@code floor(B × (1 − c))} is exact.
 *
 * @param commissionBp Banker commission in basis points (0.05 → 500)
 * @param tiePays      Tie pays X:1
 * @param pairPays     Player/Banker Pair pays X:1
 */
public record Paytable(int commissionBp, int tiePays, int pairPays) {
	public static final Paytable DEFAULT = new Paytable(500, 8, 11);

	public Paytable {
		commissionBp = Math.max(0, Math.min(10_000, commissionBp));
	}

	public static Paytable of(double commission, int tiePays, int pairPays) {
		return new Paytable(basisPoints(commission), tiePays, pairPays);
	}

	public static int basisPoints(double fraction) {
		return (int) Math.round(fraction * 10_000);
	}

	/**
	 * The Banker step k (§20.1): the smallest integer 1…100 with {@code k × c} whole; 100 if none
	 * (then the floor applies and the edge rises slightly).
	 */
	public long bankerStep() {
		return bankerStep(commissionBp);
	}

	public static long bankerStep(int commissionBp) {
		for (int k = 1; k <= 100; k++) {
			if ((long) k * commissionBp % 10_000 == 0) {
				return k;
			}
		}
		return 100;
	}

	/** True when {@link #bankerStep()} makes the commission exact (else config logs a warning). */
	public boolean exactStep() {
		return bankerStep() * commissionBp % 10_000 == 0;
	}

	/** Banker win profit: {@code floor(stake × (1 − c))}. */
	public long bankerProfit(long stake) {
		return stake * (10_000 - commissionBp) / 10_000;
	}

	/** Commission kept on a winning Banker bet (for the result line). */
	public long commission(long stake) {
		return stake - bankerProfit(stake);
	}

	/** Total return (stake included; 0 = lost, stake = push) of one bet on a coup. */
	public long returnOf(BetKind kind, long stake, Side winner, boolean playerPair, boolean bankerPair) {
		if (stake <= 0) {
			return 0;
		}
		return switch (kind) {
			case PLAYER -> winner == Side.PLAYER ? 2 * stake : winner == Side.TIE ? stake : 0;
			case BANKER -> winner == Side.BANKER ? stake + bankerProfit(stake) : winner == Side.TIE ? stake : 0;
			case TIE -> winner == Side.TIE ? stake * (tiePays + 1L) : 0;
			case PLAYER_PAIR -> playerPair ? stake * (pairPays + 1L) : 0;
			case BANKER_PAIR -> bankerPair ? stake * (pairPays + 1L) : 0;
		};
	}

	public long returnOf(BetKind kind, long stake, Coup coup) {
		return returnOf(kind, stake, coup.winner(), coup.playerPair(), coup.bankerPair());
	}

	/** Total return of a slip on a coup. */
	public long totalReturn(Map<BetKind, Long> slip, Coup coup) {
		long sum = 0;
		for (Map.Entry<BetKind, Long> e : slip.entrySet()) {
			sum += returnOf(e.getKey(), e.getValue(), coup);
		}
		return sum;
	}

	/**
	 * House worst case for one slip (§20.6 reservation): the house's net loss maximised over the 12
	 * outcome classes {Player, Banker, Tie} × {Player Pair yes/no} × {Banker Pair yes/no}; never below 0.
	 */
	public long worstCase(Map<BetKind, Long> slip) {
		long staked = Slips.total(slip);
		long worst = 0;
		for (Side side : Side.values()) {
			for (int pp = 0; pp < 2; pp++) {
				for (int bp = 0; bp < 2; bp++) {
					long ret = 0;
					for (Map.Entry<BetKind, Long> e : slip.entrySet()) {
						ret += returnOf(e.getKey(), e.getValue(), side, pp == 1, bp == 1);
					}
					worst = Math.max(worst, ret - staked);
				}
			}
		}
		return worst;
	}
}
