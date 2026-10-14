package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.table.cards.CardSprites;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;

/**
 * Card sizes of the v2 Hold'em / UTH screens (REDESIGN.md §3.1): the tier's hero / seat card box drawn with the
 * nearest atlas size ({@link CardSprites#L} 37 × 49, {@code M} 21 × 29, {@code S} 13 × 17), centred in the layout box.
 * T3's XL hero cards (49 × 65) use L until lane A's XL atlas lands (the box keeps the XL size so nothing moves then).
 */
public final class V2Cards {
	private V2Cards() {}

	public static int size(Tier t, boolean hero) {
		return switch (t) {
			case T1 -> hero ? CardSprites.M : CardSprites.S;
			case T2 -> hero ? CardSprites.L : CardSprites.M;
			case T3 -> CardSprites.L;
		};
	}

	/** Top-left where a card of {@link #size} is drawn so that it is centred in {@code box}. */
	public static int[] at(Box box, Tier t, boolean hero) {
		int s = size(t, hero);
		return new int[] {box.x() + (box.w() - CardSprites.w(s)) / 2, box.y() + (box.h() - CardSprites.h(s)) / 2};
	}
}
