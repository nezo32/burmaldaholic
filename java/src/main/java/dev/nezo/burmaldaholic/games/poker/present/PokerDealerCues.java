package dev.nezo.burmaldaholic.games.poker.present;

import dev.nezo.burmaldaholic.client.dealer.DealerCueSource;
import dev.nezo.burmaldaholic.client.dealer.DealerCues;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import org.jspecify.annotations.Nullable;

/**
 * Poker house dealer gestures from the table's public {@link PokerPub} (PURE; DL-SPEC §1): the deal pitches one card per
 * beat round the table, every street (flop / turn / river) is dealt, the finish pushes the pot to the winner and the
 * dealer shuffles for the next hand.
 */
public final class PokerDealerCues {
	/** Ticks between two pitched cards of the deal. */
	public static final int DEAL_STEP = 8;
	/** Ticks after the finish before the shuffle. */
	public static final int SHUFFLE_DELAY = 50;

	private PokerDealerCues() {}

	public static DealerCueSource.@Nullable Cue cue(@Nullable PokerPub p, long gameTime, float partialTick) {
		if (p == null || p.seq() <= 0) {
			return null;
		}
		DealerCues c = DealerCues.at(gameTime, partialTick);
		int n = p.seatCount();
		switch (p.kind()) {
			case 1 -> {
				int dealt = 0;
				for (int s = 0; s < n; s++) {
					dealt += p.has(s, PokerPub.DEALT) ? 1 : 0;
				}
				int k = 0;
				for (int round = 0; round < 2; round++) {
					for (int s = 0; s < n && k < 2 * Math.max(1, dealt); s++) {
						if (dealt == 0 || p.has(s, PokerPub.DEALT)) {
							c.add(p.startTick() + (long) k++ * DEAL_STEP, DealerGesture.DEAL, DealerCues.seat(s, n));
						}
					}
				}
			}
			case 2 -> c.add(p.startTick(), DealerGesture.DEAL, 0f);
			case 3 -> {
				int winner = -1;
				for (int s = 0; s < n && winner < 0; s++) {
					winner = p.has(s, PokerPub.WINNER) ? s : -1;
				}
				c.add(p.startTick(), DealerGesture.POT_PUSH, DealerCues.seat(winner, n));
				c.add(p.startTick() + SHUFFLE_DELAY, DealerGesture.SHUFFLE, 0f);
			}
			default -> {
			}
		}
		return c.cue();
	}
}
