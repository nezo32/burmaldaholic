package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import org.junit.jupiter.api.Test;

class RouletteDealerCuesTest {
	private static RouletteSync sync(int phase, long phaseStart, long spinStart) {
		return new RouletteSync(phase, phaseStart, spinStart, 160, phase >= RouletteSync.SPIN ? 17 : -1, 3, 5, 0, 4);
	}

	@Test
	void phasesMapToGestures() {
		assertNull(RouletteDealerCues.cue(null, 100, 0));
		assertNull(RouletteDealerCues.cue(sync(RouletteSync.BETTING, 100, 50), 105, 0), "betting: idle");
		assertEquals(DealerGesture.WAVE_OFF, RouletteDealerCues.cue(sync(RouletteSync.NO_MORE_BETS, 100, 50), 102, 0).gesture());
		var spin = RouletteDealerCues.cue(sync(RouletteSync.SPIN, 200, 200), 205, 0.5f);
		assertEquals(DealerGesture.SPIN, spin.gesture());
		assertEquals(5.5 * 50, spin.ageMs(), 1e-9);
		assertNull(RouletteDealerCues.cue(sync(RouletteSync.SPIN, 200, 200), 260, 0), "the flick is over long before the ball drops");
		assertEquals(DealerGesture.SWEEP, RouletteDealerCues.cue(sync(RouletteSync.RESULT, 400, 200), 403, 0).gesture());
		assertEquals(DealerGesture.PAY, RouletteDealerCues.cue(sync(RouletteSync.RESULT, 400, 200), 400 + RouletteDealerCues.PAY_DELAY + 2, 0).gesture());
		assertNull(RouletteDealerCues.cue(sync(RouletteSync.RESULT, 400, 200), 480, 0));
	}
}
