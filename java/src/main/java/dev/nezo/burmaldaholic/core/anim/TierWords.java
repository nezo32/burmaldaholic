package dev.nezo.burmaldaholic.core.anim;

/**
 * The words (lang keys) a celebration shows per tier — supplied by the calling game (lead decision
 * 2026-09-24, slots.md §0.3 D4). {@link #CORE} is the default; slots pass their own
 * ({@code games.slots.v2.logic.SlotTiers#WORDS}). Keys only: code never bakes words into textures.
 *
 * @param win      WIN word key
 * @param nice     NICE word key
 * @param big      BIG word key
 * @param mega     MEGA word key
 * @param epic     EPIC word key
 * @param jackpot  JACKPOT word key (sub-tier word passed as %1$s by games that have one)
 * @param returned "Returned %1$s" key
 * @param push     PUSH word key
 * @param loss     LOSS word key
 * @param maxWin   MAX WIN plate key (or {@code null} when the game has no cap)
 */
public record TierWords(String win, String nice, String big, String mega, String epic, String jackpot, String returned,
		String push, String loss, String maxWin) {
	public static final TierWords CORE = new TierWords(
		"gui.burmaldaholic.fx.tier.win",
		"gui.burmaldaholic.fx.tier.nice",
		"gui.burmaldaholic.fx.tier.big",
		"gui.burmaldaholic.fx.tier.mega",
		"gui.burmaldaholic.fx.tier.epic",
		"gui.burmaldaholic.fx.tier.jackpot",
		"gui.burmaldaholic.fx.returned",
		"gui.burmaldaholic.fx.tier.push",
		"gui.burmaldaholic.fx.tier.loss",
		null);

	public String key(WinTier tier) {
		return switch (tier) {
			case LOSS -> loss;
			case RETURN -> returned;
			case PUSH -> push;
			case WIN -> win;
			case NICE -> nice;
			case BIG -> big;
			case MEGA -> mega;
			case EPIC -> epic;
			case JACKPOT -> jackpot;
		};
	}
}
