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
 * Related hooks elsewhere in core: {@code Wagers.addVeto} (may this player wager),
 * {@code CasinoAdvancements.grant} (advancements), {@code CasinoMenu.register} (Casino Menu pages).
 */
public final class CoreServices {
	private static VipTierProvider vip = VipTierProvider.DEFAULT;
	private static DebtProvider debt = DebtProvider.NONE;
	private static GoldenHourProvider goldenHour = GoldenHourProvider.NONE;
	private static TableOwnershipProvider tableOwnership = TableOwnershipProvider.HOUSE;
	private static ClaimProvider claims = ClaimProvider.NONE;
	private static TablePresetProvider tablePresets = TablePresetProvider.NONE;

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

	public static TablePresetProvider tablePresets() {
		return tablePresets;
	}

	/** worldgen module (generated casino tables). */
	public static void setTablePresets(TablePresetProvider provider) {
		tablePresets = Objects.requireNonNull(provider);
	}

	public static ClaimProvider claims() {
		return claims;
	}

	/** multiplayer module. */
	public static void setClaims(ClaimProvider provider) {
		claims = Objects.requireNonNull(provider);
	}
}
