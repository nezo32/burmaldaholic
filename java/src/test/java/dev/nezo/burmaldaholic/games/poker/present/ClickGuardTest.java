package dev.nezo.burmaldaholic.games.poker.present;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The R3 money bug (a Check click registered as the Bet ×4 primary): a click acts only on the control the player saw. */
class ClickGuardTest {
	private static HitMap dock(int checkX, int checkW, int primaryX) {
		return new HitMap().add("action.check", new Box(checkX, 233, checkW, 30), true).add("action.bet_3x", new Box(checkX + checkW + 4, 233, 72, 30), true)
			.add("primary", new Box(primaryX, 233, 113, 30), true);
	}

	private static void layout(ClickGuard g, HitMap h, Map<String, String> actions, long now) {
		g.layout(h.targets(), id -> id + "|" + actions.getOrDefault(id, ""), now);
	}

	@Test
	void aControlThatMovedUnderTheCursorDoesNotTakeTheClickUntilItWasSeen() {
		ClickGuard g = new ClickGuard();
		// the pre-flop Dock as the tester saw it (Check 192 + 70), then the jump to the compact row 3 ticks later
		layout(g, dock(192, 70, 361), Map.of("primary", "bet_4x"), 0);
		g.rendered();
		assertTrue(g.allowsClick(200, 240, 1000), "a settled, rendered layout takes the click");
		// the primary grows over the old Check (the reported misclick): same point, another control
		layout(g, dock(120, 45, 180), Map.of("primary", "bet_4x"), 1000);
		assertFalse(g.allowsClick(200, 240, 1001), "not yet drawn: the player saw Check there");
		g.rendered();
		assertFalse(g.allowsClick(200, 240, 1100), "drawn 100 ms ago: no one aims that fast, the click was meant for Check");
		assertTrue(g.allowsClick(200, 240, 1000 + ClickGuard.SETTLE_MS), "after one reaction time the new control is what the player sees");
		assertTrue(g.allowsClick(10, 10, 1001), "a click on nothing is never the guard's business");
	}

	@Test
	void theSameControlStaysClickableWhileTheRowShiftsAndAChangedActionIsNot() {
		ClickGuard g = new ClickGuard();
		layout(g, dock(192, 70, 361), Map.of("primary", "check"), 0);
		g.rendered();
		// the row moves a little (a status countdown), Check still under the cursor: allowed at once
		layout(g, dock(190, 70, 361), Map.of("primary", "check"), 1000);
		g.rendered();
		assertTrue(g.allowsClick(220, 240, 1010));
		// the primary keeps its box but now does something else (Change bet → Bet ×4): refused for a reaction time
		layout(g, dock(190, 70, 361), Map.of("primary", "bet_4x"), 2000);
		g.rendered();
		assertFalse(g.allowsClick(400, 240, 2050));
		assertFalse(g.allowsKey("primary", 2050), "Space / Enter on a primary that just changed its action does nothing");
		assertTrue(g.allowsKey("action.check", 2050), "an unchanged control keeps its key");
		assertTrue(g.allowsClick(400, 240, 2000 + ClickGuard.SETTLE_MS));
		assertTrue(g.allowsKey("primary", 2000 + ClickGuard.SETTLE_MS));
	}

	@Test
	void aLayoutShownOnlyBrieflyIsNotTheOneThePlayerAimedAt() {
		ClickGuard g = new ClickGuard();
		layout(g, dock(192, 70, 361), Map.of("primary", "x"), 0);
		g.rendered();
		layout(g, dock(120, 45, 180), Map.of("primary", "x"), 1000); // A → B
		g.rendered();
		layout(g, dock(120, 45, 180), Map.of("primary", "y"), 1050); // B → C within the settle time
		g.rendered();
		// the player last saw A settled (Check at 200): neither B's nor C's primary takes the click yet
		assertFalse(g.allowsClick(200, 240, 1100));
	}
}
