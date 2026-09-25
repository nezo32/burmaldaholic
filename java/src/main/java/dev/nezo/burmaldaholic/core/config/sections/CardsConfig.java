package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import java.util.Locale;

/** Config section `cards` (the four card tables' presentation) — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class CardsConfig {
	/**
	 * Look of every card table (visual/cards.md §7): AUTO = by the table's dimension; otherwise forced. Sent with each
	 * card table's state ({@code theme}); a client-side forced theme (tests) still wins.
	 */
	public Theme theme = Theme.AUTO;
	/** Beat multiplier when one human is seated (animation/cards.md §0.2). */
	@Range(min = 0.25, max = 1.0) public double soloSpeed = 0.75;

	public enum Theme implements TranslatableEnum {
		AUTO, VILLAGE, BASTION, END;

		/** The theme id the clients read ({@code village | bastion | end}), or "" for AUTO. */
		public String id() {
			return this == AUTO ? "" : name().toLowerCase(Locale.ROOT);
		}

		@Override
		public String translationKey() {
			return "config.burmaldaholic.cards.theme." + name().toLowerCase(Locale.ROOT);
		}
	}
}
