package dev.nezo.burmaldaholic.games.craps.logic;

/**
 * Configurable craps numbers (CONFIG.md {@code craps.*}).
 *
 * @param fieldPays2  Field pays X:1 on 2 (default 2)
 * @param fieldPays12 Field pays X:1 on 12 (default 3)
 * @param maxOdds4_10 max odds as a multiple of the flat bet on 4/10 (3-4-5×)
 */
public record CrapsRules(int fieldPays2, int fieldPays12, int maxOdds4_10, int maxOdds5_9, int maxOdds6_8) {
	public static final CrapsRules DEFAULT = new CrapsRules(2, 3, 3, 4, 5);

	public int maxOddsMultiple(int point) {
		return switch (point) {
			case 4, 10 -> maxOdds4_10;
			case 5, 9 -> maxOdds5_9;
			default -> maxOdds6_8;
		};
	}
}
