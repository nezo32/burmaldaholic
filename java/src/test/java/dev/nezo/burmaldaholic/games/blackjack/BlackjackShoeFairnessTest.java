package dev.nezo.burmaldaholic.games.blackjack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.rng.StreakRules;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRules;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Shoe;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * E5 / owner Q&amp;A 7: StreakConfig nudges and odds modifiers never touch blackjack draws. The table's shoe RNG
 * ({@link BlackjackShoeRng#fair}, what {@code BlackjackTableBlockEntity.shoeRng()} uses) is the FAIR OddsService generator; with a maximal modifier and a huge
 * streak the shoe and the played rounds are card for card the same as without them.
 */
class BlackjackShoeFairnessTest {
	private static volatile boolean nudge;

	@Test
	void streakAndModifiersDoNotChangeTheCards() {
		OddsService before = OddsService.get();
		try {
			OddsService.set(new OddsService(new SplittableRandom(77)));
			int[][] plain = play();
			OddsService.get().addModifier("test-e5", 1, (ctx, p) -> nudge ? 1.0 : p);
			OddsService.get().setStreakSource(id -> nudge ? 1_000 : 0, () -> StreakRules.Settings.DEFAULTS);
			nudge = true;
			OddsService.set(new OddsService(new SplittableRandom(77)));
			int[][] nudged = play();
			assertEquals(plain.length, nudged.length);
			for (int i = 0; i < plain.length; i++) assertArrayEquals(plain[i], nudged[i], "row " + i);
		} finally {
			nudge = false;
			OddsService.set(before);
			OddsService.get().setStreakSource(id -> 0, () -> StreakRules.Settings.DEFAULTS);
		}
	}

	/** The shoe order, then the first cards of 20 rounds for one player, then the shoe position. */
	private static int[][] play() {
		Shoe shoe = new Shoe(BlackjackShoeRng.fair(), 6);
		int[] order = shoe.codes();
		UUID p = new UUID(1, 2);
		int[] dealt = new int[20 * 2];
		for (int r = 0; r < 20; r++) {
			shoe.beginRound();
			BlackjackRound round = new BlackjackRound(BlackjackRules.DEFAULT, shoe, List.of(new BlackjackRound.SeatBet(0, p, 10)));
			List<Card> d = round.dealerCards();
			dealt[2 * r] = d.get(0).code();
			dealt[2 * r + 1] = round.seats().getFirst().hands.getFirst().cards.getFirst().code();
			shoe.endRound();
		}
		return new int[][] {order, dealt, {shoe.dealt()}};
	}
}
