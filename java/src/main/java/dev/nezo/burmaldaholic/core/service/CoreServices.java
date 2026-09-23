package dev.nezo.burmaldaholic.core.service;

import java.util.Objects;

/**
 * Pluggable providers that feature modules implement for core (no module imports another module):
 *
 * <pre>
 * // VipModule.register(ctx):
 * CoreServices.setVip((server, player) -&gt; VipData.get(server).tier(player));
 * </pre>
 *
 * Every setter may be called once, by the owning module only (vip / loan / chaos / multiplayer).
 */
public final class CoreServices {
	private static VipTierProvider vip = VipTierProvider.DEFAULT;
	private static DebtProvider debt = DebtProvider.NONE;
	private static GoldenHourProvider goldenHour = GoldenHourProvider.NONE;
	private static TableOwnershipProvider tableOwnership = TableOwnershipProvider.HOUSE;

	private CoreServices() {}

	public static VipTierProvider vip() {
		return vip;
	}

	/** vip module. */
	public static void setVip(VipTierProvider provider) {
		vip = Objects.requireNonNull(provider);
	}

	public static DebtProvider debt() {
		return debt;
	}

	/** loan module. */
	public static void setDebt(DebtProvider provider) {
		debt = Objects.requireNonNull(provider);
	}

	public static GoldenHourProvider goldenHour() {
		return goldenHour;
	}

	/** chaos module. */
	public static void setGoldenHour(GoldenHourProvider provider) {
		goldenHour = Objects.requireNonNull(provider);
	}

	public static TableOwnershipProvider tableOwnership() {
		return tableOwnership;
	}

	/** multiplayer module. */
	public static void setTableOwnership(TableOwnershipProvider provider) {
		tableOwnership = Objects.requireNonNull(provider);
	}
}
