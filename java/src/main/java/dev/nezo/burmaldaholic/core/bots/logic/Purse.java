package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * Where a MONEY bot's chips come from and return to (BOTS.md §5.1): the world bank (unowned table or
 * anchor) or the owner's bankroll (owned, Bots = Allowed). Atmosphere bots have {@link #NONE}.
 */
public record Purse(Kind kind, String bankrollId) {
	public enum Kind { NONE, BANK, BANKROLL }

	public static final Purse NONE = new Purse(Kind.NONE, "");
	public static final Purse BANK = new Purse(Kind.BANK, "");

	public static Purse bankroll(String id) {
		return new Purse(Kind.BANKROLL, id);
	}

	/** House-funded bots are the ones subject to the daily win cap (§5.4) and the debtor exception (§5.5). */
	public boolean houseFunded() {
		return kind == Kind.BANK;
	}
}
