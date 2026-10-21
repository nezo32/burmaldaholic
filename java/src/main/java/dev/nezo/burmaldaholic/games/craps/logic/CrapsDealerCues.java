package dev.nezo.burmaldaholic.games.craps.logic;

import dev.nezo.burmaldaholic.client.dealer.DealerCueSource;
import dev.nezo.burmaldaholic.client.dealer.DealerCues;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import org.jspecify.annotations.Nullable;

/**
 * Craps stickman gestures from the table's public {@link CrapsSync} (PURE; DL-SPEC §1): after a throw the stick pulls the
 * dice in (+40 ticks), the losing bets are swept or the winners paid (+50), and the dice go back to the shooter (+70).
 */
public final class CrapsDealerCues {
	public static final int PULL = 40, SETTLE = 50, PUSH = 70;

	private CrapsDealerCues() {}

	public static DealerCueSource.@Nullable Cue cue(@Nullable CrapsSync s, long gameTime, float partialTick) {
		if (s == null || s.rolls() <= 0) {
			return null;
		}
		long t = s.rollTime();
		return DealerCues.at(gameTime, partialTick)
			.add(t + PULL, DealerGesture.STICK_PULL, 0f)
			.add(t + SETTLE, loss(s.event()) ? DealerGesture.SWEEP : DealerGesture.PAY, 0f)
			.add(t + PUSH, DealerGesture.STICK_PUSH, 0f)
			.cue();
	}

	/** A roll that loses the line bets (come-out craps, seven-out). */
	public static boolean loss(int event) {
		return event == RollEvent.Kind.CRAPS.ordinal() || event == RollEvent.Kind.SEVEN_OUT.ordinal();
	}
}
