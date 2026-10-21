package dev.nezo.burmaldaholic.core.anim.sync;

/**
 * Server lead (SY-SPEC §4): a result's animation starts {@code lead} ticks after the server decided it
 * ({@code startTick = gameTime() + lead}), so the packet usually arrives before the shared start and every viewer can
 * hand off on time. Overridable by the system property {@code burmaldaholic.syncLead.<game>} (ticks, 0 to 40) or at
 * runtime (tests, config).
 */
public final class SyncLead {
	public static volatile int SLOTS = prop("slots", 4);
	public static volatile int ROULETTE = prop("roulette", 6);
	public static volatile int CRAPS = prop("craps", 5);
	public static volatile int CARDS = prop("cards", 4);
	public static volatile int WHEEL = prop("wheel", 4);
	public static volatile int PLINKO = prop("plinko", 4);
	/** PvP dice: on top of the existing SHAKE_LEAD. */
	public static volatile int PVP_DICE = prop("pvp_dice", 4);

	private SyncLead() {}

	/** {@code gameTime + lead}, the lead clamped to 0 to 40 ticks. */
	public static long startTick(long gameTime, int lead) {
		return gameTime + clamp(lead);
	}

	static int clamp(int lead) {
		return Math.max(0, Math.min(40, lead));
	}

	private static int prop(String game, int def) {
		String v = System.getProperty("burmaldaholic.syncLead." + game);
		if (v == null) return def;
		try {
			return clamp(Integer.parseInt(v.trim()));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
