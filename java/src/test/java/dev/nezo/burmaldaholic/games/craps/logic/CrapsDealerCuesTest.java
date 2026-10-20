package dev.nezo.burmaldaholic.games.craps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import org.junit.jupiter.api.Test;

class CrapsDealerCuesTest {
	private static CrapsSync roll(long time, RollEvent.Kind kind) {
		return new CrapsSync(3, time, 3, 4, 9, 6, 0, 0, kind.ordinal(), 0);
	}

	@Test
	void stickmanFollowsTheThrow() {
		assertNull(CrapsDealerCues.cue(null, 10, 0));
		assertNull(CrapsDealerCues.cue(new CrapsSync(0, 0, 0, 0, 0, 0, 0, 0, -1, 0), 10, 0), "no roll yet");
		CrapsSync seven = roll(1000, RollEvent.Kind.SEVEN_OUT);
		assertNull(CrapsDealerCues.cue(seven, 1020, 0), "dice still flying");
		assertEquals(DealerGesture.STICK_PULL, CrapsDealerCues.cue(seven, 1000 + CrapsDealerCues.PULL + 1, 0).gesture());
		assertEquals(DealerGesture.SWEEP, CrapsDealerCues.cue(seven, 1000 + CrapsDealerCues.SETTLE + 1, 0).gesture(), "seven-out sweeps");
		assertEquals(DealerGesture.PAY, CrapsDealerCues.cue(roll(1000, RollEvent.Kind.POINT_MADE), 1000 + CrapsDealerCues.SETTLE + 1, 0).gesture());
		assertEquals(DealerGesture.SWEEP, CrapsDealerCues.cue(roll(1000, RollEvent.Kind.CRAPS), 1000 + CrapsDealerCues.SETTLE + 1, 0).gesture());
		assertEquals(DealerGesture.STICK_PUSH, CrapsDealerCues.cue(seven, 1000 + CrapsDealerCues.PUSH + 2, 0).gesture());
		assertNull(CrapsDealerCues.cue(seven, 1200, 0));
	}
}
