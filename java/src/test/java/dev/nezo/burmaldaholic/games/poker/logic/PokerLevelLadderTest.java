package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reviewer B (adversarial): the levels must play noticeably differently where it counts — at the same table,
 * on the same cards, HARD takes chips from EASY. The exploit suite measures each level against scripted
 * humans; this pits the levels against each other (bot-vs-bot chips are zero-sum, so the ladder is direct).
 */
class PokerLevelLadderTest {
	private static final long BB = 10;

	/** Folds everything it can (check when free): a table needs a human to deal, this one stays out of the way. */
	private static final PokerSim.Strategy FOLDER = (h, i, rng) -> h.legal(i).canCheck() ? Hand.Action.check() : Hand.Action.fold();

	/** Seat 0 = the folder, then 2 × strong and 2 × weak alternating; returns {strong net, weak net, hands}. */
	private static long[] ladder(BotDifficulty strong, BotDifficulty weak, int hands, long seed) {
		BotRng pr = BotRng.seeded(seed);
		List<PokerSim.Seat> seats = new ArrayList<>();
		seats.add(PokerSim.Seat.human(FOLDER));
		for (int k = 1; k <= 4; k++) {
			BotDifficulty level = k % 2 == 1 ? strong : weak; // alternate, so position is shared evenly
			seats.add(PokerSim.Seat.bot(new BotProfile("b" + k, "creeper42", level, Personality.pick(pr, level, true))));
		}
		PokerSim.Result r = PokerSim.simulate(seats, hands, BB, 100, PokerRng.of(new java.util.SplittableRandom(seed)), seed ^ 0xB07, 1,
			new PokerBotPolicy.Config(60, 60), null);
		long s = r.net()[1] + r.net()[3];
		long w = r.net()[2] + r.net()[4];
		assertEquals(0, s + w + r.net()[0], "no rake: zero-sum, no chips created or destroyed");
		return new long[] {s, w, r.perHand().size()};
	}

	@Test
	void hardBeatsEasyAtTheSameTable() {
		long[] r = ladder(BotDifficulty.HARD, BotDifficulty.EASY, 2000, 11);
		assertEquals(2000, r[2], "every hand dealt");
		double hard = r[0] / 2.0 / BB / r[2] * 100;
		double easy = r[1] / 2.0 / BB / r[2] * 100;
		assertTrue(hard - easy > 10, "HARD should clearly beat EASY: " + String.format("%.1f vs %.1f", hard, easy) + " BB/100 per seat");
	}

	@Test
	void normalBeatsEasyAtTheSameTable() {
		long[] r = ladder(BotDifficulty.NORMAL, BotDifficulty.EASY, 2000, 23);
		assertTrue(r[0] > r[1], "NORMAL should beat EASY: " + r[0] + " vs " + r[1]);
	}
}
