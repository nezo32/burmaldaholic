package dev.nezo.burmaldaholic.core.wager;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Bet validation (GAME_DESIGN.md §4.2): {@code min ≤ amount ≤ min(tableMax, VIP tier max)} and the
 * player can afford it. The VIP max comes from {@link CoreServices#vip()} (tier 0 until the vip module
 * installs its provider).
 */
public final class BetLimits {
	public enum Violation {
		NONE, INVALID, TOO_LOW, TABLE_MAX, TIER_MAX, INSUFFICIENT_FUNDS
	}

	private BetLimits() {}

	/** Pure check (unit-tested). {@code tableMax ≤ 0} = no table limit. */
	public static Violation check(long amount, long min, long tableMax, long tierMax, long balance) {
		if (amount <= 0) {
			return Violation.INVALID;
		}
		if (amount < min) {
			return Violation.TOO_LOW;
		}
		if (tableMax > 0 && tableMax < tierMax && amount > tableMax) {
			return Violation.TABLE_MAX;
		}
		if (amount > tierMax) {
			return Violation.TIER_MAX;
		}
		if (tableMax > 0 && amount > tableMax) {
			return Violation.TABLE_MAX;
		}
		if (amount > balance) {
			return Violation.INSUFFICIENT_FUNDS;
		}
		return Violation.NONE;
	}

	/** Effective max bet for a player at a table ({@code min(tableMax, tier max)}). */
	public static long maxBet(ServerPlayer player, long tableMax) {
		MinecraftServer server = player.level().getServer();
		long tier = CoreServices.vip().maxBet(server, player.getUUID());
		return tableMax > 0 ? Math.min(tableMax, tier) : tier;
	}

	/** @return null if the bet is allowed, else the translated reason ({@code gui.burmaldaholic.error.*}). */
	public static @Nullable Component validate(ServerPlayer player, long amount, long min, long tableMax) {
		return validate(player, amount, min, tableMax, WagerVeto.Context.chips(""));
	}

	/**
	 * Same, with the wager gate ({@link Wagers#check}: casino mode, owned-table rules, module vetoes such
	 * as the loan Asset Freeze) evaluated for {@code ctx} first.
	 */
	public static @Nullable Component validate(ServerPlayer player, long amount, long min, long tableMax, WagerVeto.Context ctx) {
		Component veto = Wagers.check(player, ctx);
		if (veto != null) {
			return veto;
		}
		MinecraftServer server = player.level().getServer();
		int tier = CoreServices.vip().tier(server, player.getUUID());
		long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
		long balance = Economies.get().balance(player);
		return message(check(amount, min, tableMax, tierMax, balance), min, tableMax, tierMax, tier, balance);
	}

	/**
	 * The refusal of a violation, in the same words as the client's bet gates ({@link BetCap}, owner playtest B5): the
	 * minimum, the table's maximum, the VIP tier's maximum with the tier that allows more, or the balance.
	 */
	public static @Nullable Component message(Violation v, long min, long tableMax, long tierMax, int tier, long balance) {
		return switch (v) {
			case TABLE_MAX -> BetCap.infer(tableMax, min, tableMax, 0, 0, tierMax, tier, 0).reason();
			case TIER_MAX -> BetCap.infer(tierMax, min, 0, 0, 0, tierMax, tier, nextTierMax(tier)).reason();
			default -> refusal(v, BetCap.infer(tableMax > 0 ? Math.min(tableMax, tierMax) : tierMax, min, tableMax, 0, 0, tierMax, tier,
				nextTierMax(tier)), balance);
		};
	}

	/** The refusal of {@code v} when the viewer's {@link BetCap} is known (min, whose max, balance). */
	public static @Nullable Component refusal(Violation v, BetCap cap, long balance) {
		return switch (v) {
			case NONE -> null;
			case INVALID -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
			case TOO_LOW -> cap.minReason();
			case TABLE_MAX, TIER_MAX -> cap.reason();
			case INSUFFICIENT_FUNDS -> BetCap.balanceReason(balance);
		};
	}

	/** The VIP maximum of the tier above {@code tier}, or 0 at the top tier. */
	public static long nextTierMax(int tier) {
		return tier + 1 < VipTiers.IDS.length ? VipTiers.maxBet(tier + 1) : 0;
	}

	/**
	 * The cap of a bet at a table: {@code effectiveMax} is what the game allows the player (after the table, owner and
	 * VIP limits); {@code declaredTableMax} the table's own config maximum (0 = none), {@code ownerMax} / {@code ownerMin}
	 * the owner's settings (0 = none).
	 */
	public static BetCap cap(ServerPlayer player, long effectiveMax, long min, long declaredTableMax, long ownerMax, long ownerMin) {
		MinecraftServer server = player.level().getServer();
		int tier = CoreServices.vip().tier(server, player.getUUID());
		long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
		long next = tier + 1 < VipTiers.IDS.length ? Math.max(tierMax, VipTiers.maxBet(tier + 1)) : 0;
		return BetCap.infer(effectiveMax, min, declaredTableMax, ownerMax, ownerMin, tierMax, tier, next);
	}
}
