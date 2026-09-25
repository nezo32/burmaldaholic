package dev.nezo.burmaldaholic.core.ui;

/**
 * Presentation choices of the Loan Shark's dark look (extras.md §8.4), pure: which cheeky line the shark says and how
 * full the debt meter is. Display only — the loan rules live in the loan module.
 */
public final class LoanLook {
	/** The shark's bubble lines ({@code gui.burmaldaholic.loan.shark.<id>}). */
	public enum Mood {
		NONE("none"),
		ACTIVE("active"),
		DEFAULT("default"),
		COOLDOWN("cooldown");

		public final String id;

		Mood(String id) {
			this.id = id;
		}

		public String key() {
			return "gui.burmaldaholic.loan.shark." + id;
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

	/** Whole in-game days (24 000 ticks) overdue, at least 1 once the deadline has passed. */
	public static long overdueDays(long ticksPastDeadline) {
		if (ticksPastDeadline <= 0) return 0;
		return Math.max(1, ticksPastDeadline / 24000);
	}
}
