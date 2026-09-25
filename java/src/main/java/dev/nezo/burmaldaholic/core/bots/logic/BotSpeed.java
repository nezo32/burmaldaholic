package dev.nezo.burmaldaholic.core.bots.logic;

import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import java.util.Locale;

/** Bot think-delay factor (BOTS.md §2.2, §7.3). FAST/INSTANT only take effect in BOTS_ONLY sessions. */
public enum BotSpeed implements TranslatableEnum {
	NORMAL, FAST, INSTANT;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String translationKey() {
		return "gui.burmaldaholic.bots.speed." + id();
	}
}
