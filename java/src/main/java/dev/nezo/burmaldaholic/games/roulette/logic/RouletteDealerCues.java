package dev.nezo.burmaldaholic.games.roulette.logic;

import dev.nezo.burmaldaholic.client.dealer.DealerCueSource;
import dev.nezo.burmaldaholic.client.dealer.DealerCues;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import org.jspecify.annotations.Nullable;

/**
 * Roulette house dealer gestures from the table's public {@link RouletteSync} (PURE; DL-SPEC §1): "no more bets" waves
 * off, the spin flicks the wheel, the result sweeps the losing chips and pays 30 ticks later.
 */
public final class RouletteDealerCues {
	/** Ticks after the result before the payout gesture. */
	public static final int PAY_DELAY = 30;

	private RouletteDealerCues() {}

	public static DealerCueSource.@Nullable Cue cue(@Nullable RouletteSync s, long gameTime, float partialTick) {
		if (s == null) {
			return null;
		}
		DealerCues c = DealerCues.at(gameTime, partialTick);
		if (s.phase() == RouletteSync.NO_MORE_BETS) {
			c.add(s.phaseStart(), DealerGesture.WAVE_OFF, 0f);
		}
		if (s.phase() >= RouletteSync.SPIN && s.seq() > 0) {
			c.add(s.spinStart(), DealerGesture.SPIN, 0f);
		}
		if (s.phase() == RouletteSync.RESULT) {
			c.add(s.phaseStart(), DealerGesture.SWEEP, 0f);
			c.add(s.phaseStart() + PAY_DELAY, DealerGesture.PAY, 0.5f);
		}
		return c.cue();
	}
}
