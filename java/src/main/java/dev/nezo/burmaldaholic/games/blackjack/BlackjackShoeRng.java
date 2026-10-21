package dev.nezo.burmaldaholic.games.blackjack;

import dev.nezo.burmaldaholic.core.rng.OddsService;
import java.util.function.IntUnaryOperator;

/**
 * The blackjack shoe's RNG (E5): the FAIR {@link OddsService} generator, used only to shuffle. No player context, no odds
 * modifier and no streak re-draw ever reaches it, and a round only takes the next card of the shuffled order.
 */
public final class BlackjackShoeRng {
	private BlackjackShoeRng() {}

	public static IntUnaryOperator fair() {
		return bound -> OddsService.get().fair().nextInt(bound);
	}
}
