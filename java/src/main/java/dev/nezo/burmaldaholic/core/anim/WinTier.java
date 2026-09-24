package dev.nezo.burmaldaholic.core.anim;

/**
 * The shared win-tier ladder (global.md §2.4, ⚠ CHANGED by the lead decision recorded in
 * docs/architecture/animation.md §1: one API, per-game {@link WinTierTable}). Computed by the SERVER and
 * sent with the result; clients never derive it. Bedrock: {@code core/logic/anim/win-tier.ts}.
 */
public enum WinTier {
	/** return = 0 (slots: silent; tables: soft {@code lose}). */
	LOSS,
	/** 0 &lt; return &lt; stake: "Returned N", muted, never win colours (no loss disguised as a win). */
	RETURN,
	/** return = stake on tables with a push (slots have none: 1× is a WIN). */
	PUSH,
	WIN,
	/** slots 5×; table games only via a floor (tables.md §0.3). In-panel banner, no overlay. */
	NICE,
	BIG,
	MEGA,
	EPIC,
	/** progressive / top prize; a sub-tier (Mini…Grand) travels separately. */
	JACKPOT;

	public boolean isWin() {
		return ordinal() >= WIN.ordinal();
	}

	/** Tiers that use the full-screen {@code CelebrationOverlay} (BIG and above). */
	public boolean isOverlay() {
		return ordinal() >= BIG.ordinal();
	}

	public WinTier atLeast(WinTier floor) {
		return floor != null && floor.ordinal() > ordinal() ? floor : this;
	}

	/**
	 * Classifies one settlement.
	 *
	 * @param ret     total returned to the player (stake included), chips
	 * @param stake   total at risk in this settlement (all bets of the round; buy-feature: the underlying bet
	 *                for slots multiples, SLOTS.md §6.3)
	 * @param table   the game's thresholds
	 * @param jackpot a progressive / top prize was won in this settlement
	 * @param floor   game-event floor (tables.md §0.3) applied to wins only, or {@code null}
	 */
	public static WinTier of(long ret, long stake, WinTierTable table, boolean jackpot, WinTier floor) {
		if (jackpot) return JACKPOT;
		if (ret <= 0) return LOSS;
		if (ret < stake) return RETURN;
		if (ret == stake && table.evenIsPush()) return PUSH;
		WinTier tier = WIN;
		for (WinTier t : new WinTier[] {EPIC, MEGA, BIG, NICE}) {
			if (table.reaches(t, ret, stake)) {
				tier = t;
				break;
			}
		}
		return tier.atLeast(floor == null || !floor.isWin() ? null : floor);
	}

	public static WinTier of(long ret, long stake, WinTierTable table) {
		return of(ret, stake, table, false, null);
	}
}
