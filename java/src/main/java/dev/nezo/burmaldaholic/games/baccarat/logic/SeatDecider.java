package dev.nezo.burmaldaholic.games.baccarat.logic;

import java.util.Map;
import java.util.Optional;

/**
 * Every in-round decision of a baccarat seat goes through this interface (PURE), so a future bot policy
 * (docs/design/BOTS.md, core bot framework) plugs in without touching the table: the table asks the
 * seat's decider when a decision opens; a decider that answers at once (a bot) is applied immediately
 * through the same code path as a human's screen action, an empty answer (a human, {@link #HUMAN}) waits
 * for input and the table's timer applies the default (pass / no bet).
 */
public interface SeatDecider {
	/** Humans: always wait for the screen. */
	SeatDecider HUMAN = new SeatDecider() {};

	enum BankChoice { TAKE, KEEP, PASS }

	/**
	 * @param choice TAKE a new bank of {@code amount}, KEEP the current bank (winning banker), or PASS
	 */
	record BankDecision(BankChoice choice, long amount) {
		public static BankDecision pass() {
			return new BankDecision(BankChoice.PASS, 0);
		}
	}

	/**
	 * Chemin de fer bank offer (§20.9 BANK_OFFER).
	 *
	 * @param keep        true: the seat holds a winning bank ({@code currentBank}) and may keep or pass it
	 * @param minBank     smallest bank
	 * @param defaultBank suggested amount (last bank, clamped)
	 * @param balance     the seat's chips
	 */
	record BankOffer(boolean keep, long currentBank, long minBank, long defaultBank, long balance) {}

	/**
	 * Chemin de fer punter window (§20.9 BETTING).
	 *
	 * @param coverage C of this coup; {@code open} what is still uncovered
	 * @param bancoAllowed the seat's balance and max both reach C and nobody called Banco
	 */
	record PuntWindow(long coverage, long open, long minBet, long maxBet, long balance, boolean bancoAllowed) {}

	/** {@code banco} = call Banco (amount ignored); else punt {@code amount} (0 = no bet). */
	record PuntDecision(long amount, boolean banco, boolean ready) {}

	/** House coup betting window (§20.5). */
	record HouseWindow(Slips.Limits limits, long balance, Map<BetKind, Long> lastSlip, Paytable paytable) {}

	/** {@code bets} to place (may be empty) and whether the seat is Ready afterwards. */
	record HouseDecision(Map<BetKind, Long> bets, boolean ready) {}

	default Optional<BankDecision> bankOffer(BankOffer offer) {
		return Optional.empty();
	}

	default Optional<PuntDecision> punt(PuntWindow window) {
		return Optional.empty();
	}

	default Optional<HouseDecision> houseBets(HouseWindow window) {
		return Optional.empty();
	}
}
