package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.Locale;

/**
 * Bot personality (BOTS.md §4.3, §4.7): changes style, never strength class (test BOTS.md §12.2).
 * Poker: threshold / aggression / bluff modifiers on top of the level. Roulette / craps / baccarat: the
 * betting style ({@code gui.burmaldaholic.bots.betstyle.<game>.<id>}). PvP: taunt flavour only.
 * {@code bots.personalities} = false → every bot is {@link #TAG}.
 *
 * @param weights     appearance weight at EASY / NORMAL / HARD
 * @param openDelta   Δ Chen open threshold (LAG −1.5: round toward the looser integer at use)
 * @param aggression  raise : call aggression factor
 * @param bluffFactor × bluff frequency
 * @param callWider   × calling range width (STATION 1.5)
 */
public enum Personality {
	ROCK(new int[] {25, 30, 0}, 2, 0.5, 0.3, 1.0),
	STATION(new int[] {35, 0, 0}, -3, 0.5, 0.2, 1.5),
	MANIAC(new int[] {40, 0, 0}, -3, 2.0, 3.0, 1.0),
	TAG(new int[] {0, 70, 60}, 0, 1.0, 1.0, 1.0),
	LAG(new int[] {0, 0, 40}, -1.5, 1.4, 1.5, 1.0);

	private final int[] weights;
	public final double openDelta;
	public final double aggression;
	public final double bluffFactor;
	public final double callWider;

	Personality(int[] weights, double openDelta, double aggression, double bluffFactor, double callWider) {
		this.weights = weights;
		this.openDelta = openDelta;
		this.aggression = aggression;
		this.bluffFactor = bluffFactor;
		this.callWider = callWider;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code gui.burmaldaholic.bots.personality.<id>} (+ {@code .desc}). */
	public String translationKey() {
		return "gui.burmaldaholic.bots.personality." + id();
	}

	/** Betting style name at an atmosphere game ({@code roulette | craps | baccarat}). */
	public String betStyleKey(String game) {
		return "gui.burmaldaholic.bots.betstyle." + game + "." + id();
	}

	public int weight(BotDifficulty level) {
		return switch (level) {
			case EASY -> weights[0];
			case NORMAL, MIXED -> weights[1];
			case HARD -> weights[2];
		};
	}

	/** Draws a personality for a concrete level from the BOT rng ({@code enabled} = {@code bots.personalities}). */
	public static Personality pick(BotRng rng, BotDifficulty level, boolean enabled) {
		if (!enabled) {
			return TAG;
		}
		int total = 0;
		for (Personality p : values()) {
			total += p.weight(level);
		}
		if (total <= 0) {
			return TAG;
		}
		int roll = rng.nextInt(total);
		for (Personality p : values()) {
			roll -= p.weight(level);
			if (roll < 0) {
				return p;
			}
		}
		return TAG;
	}

	public static Personality byId(String id) {
		for (Personality p : values()) {
			if (p.id().equalsIgnoreCase(id)) {
				return p;
			}
		}
		return TAG;
	}
}
