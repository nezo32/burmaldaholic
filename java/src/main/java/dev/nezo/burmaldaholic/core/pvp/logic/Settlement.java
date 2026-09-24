package dev.nezo.burmaldaholic.core.pvp.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import java.util.List;

/**
 * Pure money flow of one PvP match (PVP.md §3.4, BOTS.md §5.1, pvp-bots.md §3.2). The bank is the escrow
 * holder: human stakes and BANKROLL-purse bot stakes are moved into it at escrow; BANK-purse bot stakes are
 * minted (nothing moves) and their payouts are sunk (nothing moves). Integer only, identical in both editions.
 */
public final class Settlement {
	private Settlement() {}

	/**
	 * One participant.
	 *
	 * @param stake        escrowed stake
	 * @param bot          a money bot
	 * @param bankrollBot  a bot funded by the anchor owner's bankroll (else a bank bot)
	 */
	public record Seat(long stake, boolean bot, boolean bankrollBot) {
		public static Seat human(long stake) {
			return new Seat(stake, false, false);
		}

		public static Seat bankBot(long stake) {
			return new Seat(stake, true, false);
		}

		public static Seat bankrollBot(long stake) {
			return new Seat(stake, true, true);
		}

		/** Does this seat's money really move through the bank (humans and bankroll bots)? */
		public boolean moves() {
			return !bot || bankrollBot;
		}
	}

	/**
	 * @param pot            Σ stakes (bots included)
	 * @param rake           {@link PvpMath#rake}
	 * @param rakeToBankroll the part of the rake credited to the anchor's bankroll (0 = unowned anchor)
	 * @param payouts        per participant
	 * @param houseDelta     the bank's leg of the settlement transaction (≤ 0)
	 * @param bankrollDelta  the bankroll's leg (bankroll-bot payouts + rakeToBankroll, ≥ 0)
	 */
	public record Result(long pot, long rake, long rakeToBankroll, long[] payouts, long houseDelta, long bankrollDelta) {
		public long botStakes(List<Seat> seats) {
			long s = 0;
			for (Seat x : seats) {
				if (x.bot()) {
					s += x.stake();
				}
			}
			return s;
		}
	}

	/** Chips that enter the bank at escrow (Σ human + Σ bankroll-bot stakes). */
	public static long escrowToHouse(List<Seat> seats) {
		long s = 0;
		for (Seat x : seats) {
			if (x.moves()) {
				s = Math.addExact(s, x.stake());
			}
		}
		return s;
	}

	/** Chips taken from the bankroll at escrow (Σ bankroll-bot stakes). */
	public static long escrowFromBankroll(List<Seat> seats) {
		long s = 0;
		for (Seat x : seats) {
			if (x.bot() && x.bankrollBot()) {
				s = Math.addExact(s, x.stake());
			}
		}
		return s;
	}

	/**
	 * Settles a match.
	 *
	 * @param winners        {@link Outcome#winners()}
	 * @param seatOrder      {@link Outcome#seatOrder()} (odd chips)
	 * @param rakeBasisPoints {@code pvp.rakeBasisPoints}
	 * @param anchorBankroll the anchor was linked to an owned casino at creation
	 */
	public static Result settle(List<Seat> seats, int[] winners, int[] seatOrder, int rakeBasisPoints, boolean anchorBankroll) {
		int n = seats.size();
		long pot = 0;
		long botStakes = 0;
		for (Seat s : seats) {
			pot = Math.addExact(pot, s.stake());
			if (s.bot()) {
				botStakes += s.stake();
			}
		}
		long rake = PvpMath.rake(pot, rakeBasisPoints);
		long[] payouts = PvpMath.split(pot - rake, winners, seatOrder, n);
		long toBankroll = anchorBankroll ? rake - BotEconomyMath.pvpRakeToBank(rake, botStakes, pot) : 0;
		long house = 0;
		long bankroll = toBankroll;
		for (int i = 0; i < n; i++) {
			Seat s = seats.get(i);
			if (s.moves()) {
				house = Math.addExact(house, payouts[i]);
			}
			if (s.bot() && s.bankrollBot()) {
				bankroll = Math.addExact(bankroll, payouts[i]);
			}
		}
		house = Math.addExact(house, toBankroll);
		return new Result(pot, rake, toBankroll, payouts, -house, bankroll);
	}

	/**
	 * The bots' share of the money on the other side of human {@code self} (BOTS.md §5.3): bot stakes /
	 * the other participants' stakes. 0 when nobody else staked.
	 */
	public static double botShare(List<Seat> seats, int self) {
		long bots = 0;
		long others = 0;
		for (int i = 0; i < seats.size(); i++) {
			if (i == self) {
				continue;
			}
			others += seats.get(i).stake();
			if (seats.get(i).bot()) {
				bots += seats.get(i).stake();
			}
		}
		return others <= 0 ? 0 : (double) bots / others;
	}

	/**
	 * Net of human {@code self} attributed to bots for the daily heat ledger (BOTS.md §5.4,
	 * {@link BotEconomyMath#pvp}).
	 */
	public static long botAttributedNet(List<Seat> seats, long[] payouts, int self) {
		long net = payouts[self] - seats.get(self).stake();
		long botStakes = 0;
		long otherStakes = 0;
		long botPayouts = 0;
		long otherPayouts = 0;
		for (int i = 0; i < seats.size(); i++) {
			if (i == self) {
				continue;
			}
			otherStakes += seats.get(i).stake();
			otherPayouts += payouts[i];
			if (seats.get(i).bot()) {
				botStakes += seats.get(i).stake();
				botPayouts += payouts[i];
			}
		}
		return BotEconomyMath.pvp(net, botStakes, otherStakes, botPayouts, otherPayouts);
	}
}
