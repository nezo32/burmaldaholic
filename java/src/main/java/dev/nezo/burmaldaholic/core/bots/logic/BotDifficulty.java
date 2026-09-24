package dev.nezo.burmaldaholic.core.bots.logic;

import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import java.util.Locale;

/**
 * Bot strength (BOTS.md §4.2). {@link #MIXED} is only a SETTING: each new bot's level is drawn with
 * {@link #pick}; a {@link BotProfile} never holds MIXED. Poker shows EASY/NORMAL/HARD as Fish/Regular/Shark.
 */
public enum BotDifficulty implements TranslatableEnum {
	EASY, NORMAL, HARD, MIXED;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** One-letter badge shown next to bot names ("E"/"N"/"H"; MIXED never shown). */
	public String badge() {
		return name().substring(0, 1);
	}

	public boolean isMixed() {
		return this == MIXED;
	}

	@Override
	public String translationKey() {
		return "gui.burmaldaholic.bots.level." + id();
	}

	/** "Style" name where decisions cannot change EV (chemin de fer, Coin Flip Duel, Wheel Party; BOTS.md §4.2). */
	public String styleKey() {
		return "gui.burmaldaholic.bots.style." + id();
	}

	/** One-letter translated badge ({@code gui.burmaldaholic.bots.level.<id>.short}). */
	public String shortKey() {
		return translationKey() + ".short";
	}

	public static BotDifficulty byId(String id, BotDifficulty fallback) {
		for (BotDifficulty d : values()) {
			if (d.id().equalsIgnoreCase(id) || d.name().equalsIgnoreCase(id)) {
				return d;
			}
		}
		return fallback;
	}

	/** Legacy poker tier ids ({@code fish/regular/shark}) → level (saved tables, old config). */
	public static BotDifficulty fromPokerTier(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "fish" -> EASY;
			case "shark" -> HARD;
			default -> NORMAL;
		};
	}

	/**
	 * Resolves a setting to a concrete level: returns {@code this} unless MIXED, then draws from
	 * {@code mix} = [easy, normal, hard] weights (normalized; all zero → NORMAL). Uses the BOT rng.
	 */
	public BotDifficulty pick(BotRng rng, int[] mix) {
		if (!isMixed()) {
			return this;
		}
		int total = 0;
		int[] w = new int[3];
		for (int k = 0; k < 3; k++) {
			w[k] = mix != null && k < mix.length ? Math.max(0, mix[k]) : 0;
			total += w[k];
		}
		if (total <= 0) {
			return NORMAL;
		}
		int roll = rng.nextInt(total);
		for (int k = 0; k < 3; k++) {
			roll -= w[k];
			if (roll < 0) {
				return values()[k];
			}
		}
		return HARD;
	}
}
