package dev.nezo.burmaldaholic.core.economy;

import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Default economy: balance stored as a persistent player attachment (survives death/relog).
 * J-core decides whether physical chip items mirror this balance (see docs/design).
 */
public final class AttachmentEconomy implements Economy {
	private static AttachmentType<Long> balanceType;

	public static void register() {
		balanceType = AttachmentRegistry.create(Burmaldaholic.id("balance"), builder -> builder
			.persistent(Codec.LONG)
			.copyOnDeath()
			.initializer(() -> 0L));
	}

	@Override
	public long balance(ServerPlayer player) {
		Long value = player.getAttached(balanceType);
		return value == null ? 0L : value;
	}

	@Override
	public boolean tryWithdraw(ServerPlayer player, long amount, Transaction reason) {
		requireNonNegative(amount);
		long before = balance(player);
		if (before < amount) {
			return false;
		}
		player.setAttached(balanceType, before - amount);
		CasinoEvents.BALANCE_CHANGED.invoker().onBalanceChanged(player, before, before - amount, reason);
		return true;
	}

	@Override
	public void deposit(ServerPlayer player, long amount, Transaction reason) {
		requireNonNegative(amount);
		long before = balance(player);
		long after = Math.addExact(before, amount);
		player.setAttached(balanceType, after);
		CasinoEvents.BALANCE_CHANGED.invoker().onBalanceChanged(player, before, after, reason);
	}

	private static void requireNonNegative(long amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("amount must be >= 0: " + amount);
		}
	}
}
