package dev.nezo.burmaldaholic.core.bots.logic;

import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import java.util.Locale;

/**
 * The owner's per-table "Bots" control (BOTS.md §6.2). Unowned tables are always {@link #ALLOWED}
 * (bank-funded); owned tables default to {@link #ATMOSPHERE} (money bots would need the bankroll).
 */
public enum BotsMode implements TranslatableEnum {
	OFF, ATMOSPHERE, ALLOWED;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** May a bot of this role sit? */
	public boolean allows(BotRole role) {
		return switch (this) {
			case OFF -> false;
			case ATMOSPHERE -> role == BotRole.ATMOSPHERE;
			case ALLOWED -> true;
		};
	}

	@Override
	public String translationKey() {
		return "gui.burmaldaholic.bots.charter.mode." + id();
	}
}
