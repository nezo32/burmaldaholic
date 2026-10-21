package dev.nezo.burmaldaholic.client.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * Review T-C2 V8: during the spin the Dock said "Spinning…" while the strip said "The ball is spinning…". A phase reason
 * gives line 1 to the game's own hint for that phase (said once); any other reason keeps line 1 and the hint stays on
 * line 2.
 */
class StatusLineTest {
	@Test
	void phaseReasonYieldsToTheGamesHint() {
		StatusLine s = new StatusLine();
		Component hint = Component.literal("The ball is spinning…");
		s.hint(hint);
		s.reason(Gate.off("spin", "x", V2Keys.REASON_SPINNING));
		assertSame(hint, s.line1(), "the hint takes line 1");
		assertNull(s.line2(), "and is not repeated on line 2");
	}

	@Test
	void otherReasonsKeepLineOneAndTheHint() {
		StatusLine s = new StatusLine();
		Component hint = Component.literal("Chips add to your bet");
		s.hint(hint);
		s.reason(Gate.off("deal", "x", V2Keys.REASON_PLACE_BET));
		assertEquals(V2Keys.REASON_PLACE_BET, ((net.minecraft.network.chat.contents.TranslatableContents) s.line1().getContents()).getKey());
		assertSame(hint, s.line2());
	}

	@Test
	void phaseReasonWithoutAHintStillShows() {
		StatusLine s = new StatusLine();
		s.reason(Gate.off("spin", "x", V2Keys.REASON_SPINNING));
		assertEquals(V2Keys.REASON_SPINNING, ((net.minecraft.network.chat.contents.TranslatableContents) s.line1().getContents()).getKey());
		assertNull(s.line2());
	}
}
