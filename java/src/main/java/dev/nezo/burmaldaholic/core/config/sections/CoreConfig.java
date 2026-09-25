package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import java.util.Locale;

/** Config section `core` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class CoreConfig {
	/** Give a Casino Card on a player's first join. */
	public boolean giveCasinoCardOnJoin = true;
	public static final class Hud {
		/** Show the HUD panel (players can hide it individually too). */
		public boolean enabled = true;
		/** Default HUD corner (per-player override in Casino Menu). */
		public HudPosition position = HudPosition.TOP_LEFT;
	}
	public Hud hud = new Hud();

	/** Server-wide chat for wins ≥ `core.bigWinThreshold`. */
	public boolean announceBigWins = true;
	/** Net win that is announced. */
	@Range(min = 100, max = 10000000) public int bigWinThreshold = 5000;
	/** Refund STAKED rounds after a server restart (§4.1). */
	public boolean roundTimeoutRefund = true;

	/** HUD corner; labels reuse the Casino Menu settings keys. */
	public enum HudPosition implements TranslatableEnum {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		public boolean bottom() {
			return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
		}

		@Override
		public String translationKey() {
			return "gui.burmaldaholic.menu.settings.corner." + name().toLowerCase(Locale.ROOT);
		}
	}
}
