package dev.nezo.burmaldaholic.core.bots.logic;

import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import java.util.Locale;

/** Who may sit at a table / take part in a match (BOTS.md §2.1). Pure. */
public enum SeatPolicy implements TranslatableEnum {
	/** Humans allowed by Access only; no bots. */
	HUMANS_ONLY,
	/** Only the host plus exactly {@code count} bots. */
	BOTS_ONLY,
	/** Humans + up to {@code count} bots in the free seats (bots yield to humans at the next safe point). */
	MIXED;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** BOTS.md §11 policy name (also used as the config option label). */
	@Override
	public String translationKey() {
		return "gui.burmaldaholic.bots.policy." + id();
	}

	public static SeatPolicy byId(String id, SeatPolicy fallback) {
		for (SeatPolicy p : values()) {
			if (p.id().equalsIgnoreCase(id) || p.name().equalsIgnoreCase(id)) {
				return p;
			}
		}
		return fallback;
	}
}
