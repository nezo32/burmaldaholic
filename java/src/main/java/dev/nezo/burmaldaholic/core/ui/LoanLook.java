package dev.nezo.burmaldaholic.core.ui;

/**
 * Presentation choices of the Loan Shark's dark look (extras.md §8.4), pure: which cheeky line the shark says and how
 * full the debt meter is. Display only — the loan rules live in the loan module.
 */
public final class LoanLook {
	/** The shark's bubble lines ({@code gui.burmaldaholic.loan.shark.<id>}). */
	public enum Mood {
		NONE("none", "gui.burmaldaholic.loan.shark.none"),
		ACTIVE("active", "gui.burmaldaholic.loan.shark.active"),
		DEFAULT("default", "gui.burmaldaholic.loan.shark.default"),
		COOLDOWN("cooldown", "gui.burmaldaholic.loan.shark.cooldown");

		public final String id;
		/** The full lang key, spelled out (ResourceIntegrityTest checks every key literal exists in EN + RU). */
		private final String key;

		Mood(String id, String key) {
			this.id = id;
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	private LoanLook() {}

	/** Mood from the loan screen's {@code status} string ({@code none | active | default | cooldown}). */
	public static Mood mood(String status) {
		return switch (status == null ? "" : status) {
			case "active" -> Mood.ACTIVE;
			case "default" -> Mood.DEFAULT;
			case "cooldown" -> Mood.COOLDOWN;
			default -> Mood.NONE;
		};
	}

	/** Mood from the synced HUD status (Casino Menu tab): owed > 0 → active / default. */
	public static Mood mood(long debt, boolean inDefault) {
		if (debt <= 0) return Mood.NONE;
		return inDefault ? Mood.DEFAULT : Mood.ACTIVE;
	}

	/** Debt meter fill: {@code owed / (principal × 1.5)}, capped at 1; 0 without a loan, full when the principal is unknown. */
	public static double debtFill(long owed, long principal) {
		if (owed <= 0) return 0;
		if (principal <= 0) return 1;
		return Math.min(1, owed / (principal * 1.5));
	}

	/**
	 * A readable "due in" for {@code ticks} before the deadline: {@code {days, hours}} in in-game days (24 000 ticks) and
	 * hours (1 000 ticks), rounded down; {@code {0, 0}} = less than an hour (or past due).
	 */
	public static long[] dueIn(long ticks) {
		long hours = Math.max(0, ticks) / 1000;
		return new long[] {hours / 24, hours % 24};
	}

	/** Whole in-game days (24 000 ticks) overdue, at least 1 once the deadline has passed. */
	public static long overdueDays(long ticksPastDeadline) {
		if (ticksPastDeadline <= 0) return 0;
		return Math.max(1, ticksPastDeadline / 24000);
	}
}
