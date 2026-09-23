package dev.nezo.burmaldaholic.core.wager;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
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
		MinecraftServer server = player.level().getServer();
		int tier = CoreServices.vip().tier(server, player.getUUID());
		long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
		long balance = Economies.get().balance(player);
		return message(check(amount, min, tableMax, tierMax, balance), min, tableMax, tierMax, tier, balance);
	}

	public static @Nullable Component message(Violation v, long min, long tableMax, long tierMax, int tier, long balance) {
		return switch (v) {
			case NONE -> null;
			case INVALID -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
			case TOO_LOW -> Component.translatable("gui.burmaldaholic.error.bet_too_low", Texts.number(min));
			case TABLE_MAX -> Component.translatable("gui.burmaldaholic.error.table_max", Texts.number(tableMax));
			case TIER_MAX -> Component.translatable("gui.burmaldaholic.error.bet_too_high", Texts.number(tierMax), VipTiers.name(tier));
			case INSUFFICIENT_FUNDS -> Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance));
		};
	}
}
