package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * Who may change a table's seating settings and how a wanted change is clamped (BOTS.md §2.1, §2.4,
 * §6.2). Pure. Errors are translation keys ({@code gui.burmaldaholic.bots.error.*}); the caller adds
 * the arguments (none of these take any).
 */
public final class SettingsChange {
	private SettingsChange() {}

	/** Strongest role of the actor at this table (an operator who is also the host is OPERATOR). */
	public enum Role {
		OPERATOR, OWNER, KEEPER, HOST, NONE;

		/** Operators, owners and keepers edit the table defaults and invite guests that are saved. */
		public boolean editsDefaults() {
			return this == OPERATOR || this == OWNER || this == KEEPER;
		}

		/** May change session settings / access at all (subject to the owner's limits). */
		public boolean mayChange() {
			return this != NONE;
		}
	}

	public static final String ERR_DISABLED = "gui.burmaldaholic.bots.error.disabled";
	public static final String ERR_NOT_HOST = "gui.burmaldaholic.bots.error.not_host";
	public static final String ERR_HOST_LOCKED = "gui.burmaldaholic.bots.error.host_locked";
	public static final String ERR_OWNER_LOCKED = "gui.burmaldaholic.bots.error.owner_locked";
	public static final String ERR_OWNER_OFF = "gui.burmaldaholic.bots.error.owner_off";
	public static final String ERR_OTHERS_SEATED = "gui.burmaldaholic.bots.error.others_seated";
	public static final String ERR_PRIVATE_FORBIDDEN = "gui.burmaldaholic.bots.error.private_forbidden";

	/**
	 * @param settings   the clamped settings to store (null on error)
	 * @param error      translation key of the refusal (null on success)
	 * @param asDefaults also save them as the table defaults (only for {@link Role#editsDefaults()})
	 */
	public record Outcome(BotSettings settings, String error, boolean asDefaults) {
		public boolean ok() {
			return error == null;
		}

		static Outcome fail(String key) {
			return new Outcome(null, key, false);
		}
	}

	/**
	 * Validates and clamps a wanted change.
	 *
	 * @param seated       the actor sits at the table (to tell "not the host" from "not seated")
	 * @param humansSeated humans seated now (BOTS_ONLY only while the host is alone, pillar 4)
	 * @param seats        seats for occupants (count ≤ seats − 1)
	 */
	public static Outcome validate(Role role, boolean seated, OwnerControls limits, BotRole botRole, boolean botsEnabled,
			BotSettings wanted, boolean asDefaults, int humansSeated, int seats) {
		if (!botsEnabled && wanted.policy() != SeatPolicy.HUMANS_ONLY) {
			return Outcome.fail(ERR_DISABLED);
		}
		if (role == Role.NONE) {
			return Outcome.fail(seated ? ERR_HOST_LOCKED : ERR_NOT_HOST);
		}
		if (role == Role.HOST && !limits.hostMayChange()) {
			return Outcome.fail(ERR_OWNER_LOCKED);
		}
		if (wanted.policy() != SeatPolicy.HUMANS_ONLY && (!limits.botsMode().allows(botRole) || limits.maxBots() <= 0)) {
			return Outcome.fail(ERR_OWNER_OFF);
		}
		if (wanted.policy() == SeatPolicy.BOTS_ONLY && !SeatingMath.botsOnlyAllowed(humansSeated)) {
			return Outcome.fail(ERR_OTHERS_SEATED);
		}
		int max = Math.max(0, Math.min(limits.maxBots(), seats - 1));
		int count = Math.min(wanted.count(), max);
		if (wanted.policy() == SeatPolicy.BOTS_ONLY) {
			count = Math.max(1, count);
		}
		return new Outcome(wanted.withCount(count), null, asDefaults && role.editsDefaults());
	}

	/** May this role switch the table private / invite (§2.5)? Returns an error key or null. */
	public static String validateAccess(Role role, boolean seated, OwnerControls limits, boolean privateEnabled, boolean turningOn) {
		if (!privateEnabled) {
			return ERR_DISABLED;
		}
		if (role == Role.NONE) {
			return seated ? ERR_HOST_LOCKED : ERR_NOT_HOST;
		}
		if (turningOn && !limits.allowPrivate() && role != Role.OPERATOR) {
			return ERR_PRIVATE_FORBIDDEN;
		}
		return null;
	}
}
