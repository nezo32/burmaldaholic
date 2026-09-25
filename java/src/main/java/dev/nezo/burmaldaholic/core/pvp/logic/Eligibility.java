package dev.nezo.burmaldaholic.core.pvp.logic;

/**
 * PvP eligibility (PVP.md §3.2, amended by BOTS.md §5.5): the first failing rule's error key. Pure over
 * {@link Facts}, which the engine gathers per player. Both editions evaluate the same order.
 */
public final class Eligibility {
	private Eligibility() {}

	/**
	 * @param casinoOn        casino mode on
	 * @param modeEnabled     {@code pvp.enabled} and the mode's {@code pvp.<mode>.enabled}
	 * @param spectator       in Spectator mode
	 * @param owesDebt        owed &gt; 0, in default (Asset Freeze is reported by {@code frozen})
	 * @param frozen          Asset Freeze
	 * @param onlyHouseBots   every other participant is a house-funded bot (debtor exception, BOTS.md §5.5)
	 * @param debtorsMayPlay  {@code bots.debtorsMayPlay}
	 * @param busy            seated at a table / in another match / in a solo round
	 * @param sameDimension   same dimension as the anchor
	 * @param distance        blocks to the anchor (or challenger)
	 * @param joinRadius      {@code pvp.joinRadius}
	 * @param stake           stake to check (0 = not yet chosen)
	 * @param minStake        {@code pvp.minStake}
	 * @param tierMax         the player's VIP max bet
	 * @param balance         the player's balance
	 * @param self            already a participant
	 * @param vipOk           machine VIP requirement met
	 * @param machineOpen     owned machine linked and open
	 * @param owner           the player owns the anchor's casino
	 * @param acceptsInvites  target's "Accept PvP challenges" (duels only; true otherwise)
	 * @param declineCooldown target declined this challenger recently
	 * @param pendingFull     challenger has {@code pvp.maxPendingInvites} outgoing invites
	 * @param botsCapped      house bots refuse this player today (BOTS.md §5.4; only when bots take part)
	 */
	public record Facts(boolean casinoOn, boolean modeEnabled, boolean spectator, boolean owesDebt, boolean frozen, boolean onlyHouseBots,
			boolean debtorsMayPlay, boolean busy, boolean sameDimension, double distance, int joinRadius, long stake,
			long minStake, long tierMax, long balance, boolean self, boolean vipOk, boolean machineOpen, boolean owner,
			boolean acceptsInvites, boolean declineCooldown, boolean pendingFull, boolean botsCapped) {}

	/** @return null when eligible, else the error translation key (for the player themself). */
	public static String check(Facts f) {
		if (!f.casinoOn()) {
			return "gui.burmaldaholic.error.casino_off";
		}
		if (!f.modeEnabled()) {
			return "gui.burmaldaholic.error.disabled";
		}
		if (f.spectator()) {
			return "gui.burmaldaholic.pvp.error.spectator";
		}
		if (f.frozen() || (f.owesDebt() && !(f.onlyHouseBots() && f.debtorsMayPlay()))) {
			return "gui.burmaldaholic.pvp.error.debt";
		}
		if (f.busy()) {
			return "gui.burmaldaholic.error.busy";
		}
		if (!f.sameDimension()) {
			return "gui.burmaldaholic.pvp.error.other_dimension";
		}
		if (f.distance() > f.joinRadius()) {
			return "gui.burmaldaholic.pvp.error.too_far";
		}
		if (f.stake() > 0 && f.stake() < f.minStake()) {
			return "gui.burmaldaholic.pvp.error.stake_min";
		}
		if (f.stake() > f.tierMax()) {
			return "gui.burmaldaholic.error.bet_too_high";
		}
		if (f.stake() > f.balance()) {
			return "gui.burmaldaholic.error.insufficient_funds";
		}
		if (f.self()) {
			return "gui.burmaldaholic.pvp.error.self";
		}
		if (!f.vipOk()) {
			return "gui.burmaldaholic.error.vip_required";
		}
		if (!f.machineOpen()) {
			return "gui.burmaldaholic.error.table_closed";
		}
		if (f.owner()) {
			return "gui.burmaldaholic.pvp.error.owner";
		}
		if (!f.acceptsInvites()) {
			return "gui.burmaldaholic.pvp.error.no_invites";
		}
		if (f.declineCooldown()) {
			return "gui.burmaldaholic.pvp.error.cooldown_target";
		}
		if (f.pendingFull()) {
			return "gui.burmaldaholic.pvp.error.pending";
		}
		if (f.botsCapped()) {
			return "gui.burmaldaholic.bots.error.capped";
		}
		return null;
	}
}
