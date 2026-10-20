package dev.nezo.burmaldaholic.core.store;

import com.mojang.brigadier.Command;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.cashier.ExchangeMath;
import dev.nezo.burmaldaholic.core.cashier.ExchangeRows;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.store.net.StoreActionPayload;
import dev.nezo.burmaldaholic.core.store.net.StoreSyncPayload;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Chip store server (EC-SPEC §3): {@code StoreActionPayload} sync / buy, the per-player daily limits
 * ({@code burmaldaholic:store_limits}), {@code /casino store} (op) and the merchant NPC role ({@link MerchantRole}).
 * Buys follow the exchange rule: {@code n = min(qty | ∞, balance / price, fit, dailyLeft)}, items only into free
 * main-inventory room, the shared 4-tick counter cooldown.
 */
public final class StoreService {
	private StoreService() {}

	public static AttachmentType<StoreLimits.Data> LIMITS;

	public static void register(ModuleContext core) {
		LIMITS = AttachmentRegistry.create(Burmaldaholic.id("store_limits"),
			b -> b.persistent(StoreLimits.Data.CODEC).copyOnDeath().initializer(() -> StoreLimits.Data.EMPTY));
		StoreSyncPayload.TYPE = core.payloads().clientbound("store_sync", StoreSyncPayload.CODEC);
		StoreActionPayload.TYPE = core.payloads().serverbound("store_action", StoreActionPayload.CODEC,
			(payload, context) -> receive(context.player(), payload));
		CasinoCommands.extend(root -> root.then(Commands.literal("store").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			CounterSessions.openAndSend(p, CounterSource.command());
			return Command.SINGLE_SUCCESS;
		})));
		MerchantRole.register();
	}

	/** In-game day (the contracts' day: overworld game time / 24000). */
	public static long today(MinecraftServer server) {
		return server.overworld().getGameTime() / 24000L;
	}

	/** The {@code store_action} receiver; public so server GameTests drive the packet path. */
	public static void receive(ServerPlayer player, StoreActionPayload payload) {
		try {
			switch (payload.action()) {
				case "sync" -> {
					CounterSessions.Session s = CounterSessions.validate(player);
					if (s != null) sync(player, s);
				}
				case "buy" -> buy(player, payload.id(), payload.qty(), payload.all());
				default -> {}
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Store action '{}' failed", payload.action(), e);
		}
	}

	/** The offers as {@code player} sees them at session {@code s}. */
	public static List<StoreSyncPayload.OfferView> view(ServerPlayer player, CounterSessions.Session s) {
		MinecraftServer server = player.level().getServer();
		int tier = CoreServices.vip().tier(server, player.getUUID());
		long day = today(server);
		long balance = Economies.get().balance(player);
		StoreLimits.Data data = limits(player);
		List<StoreSyncPayload.OfferView> out = new ArrayList<>();
		for (StoreCatalog.Entry e : StoreCatalog.entries(server.registryAccess(), s.dim())) {
			long left = data.left(e.id(), day, e.limitPerDay(), e.limitEveryDays());
			String lock = tier < e.minTier() ? "vip" : left == 0 ? "limit" : balance < e.price() ? "funds" : "";
			out.add(new StoreSyncPayload.OfferView(e.id(), e.category(), e.stack(), e.price(), e.minTier(), left, e.limitPerDay(), e.limitEveryDays(), lock));
		}
		return out;
	}

	public static void sync(ServerPlayer player, CounterSessions.Session s) {
		if (StoreSyncPayload.TYPE == null || !ServerPlayNetworking.canSend(player, StoreSyncPayload.TYPE)) return;
		int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
		ServerPlayNetworking.send(player, new StoreSyncPayload(view(player, s), Economies.get().balance(player), tier));
	}

	public static StoreLimits.Data limits(ServerPlayer player) {
		StoreLimits.Data d = player.getAttached(LIMITS);
		return d == null ? StoreLimits.Data.EMPTY : d;
	}

	/**
	 * Buys offer {@code id} × {@code qty} (or as many as allowed when {@code all}). Returns the purchases made (0 = refused,
	 * the reason is in the result line).
	 */
	public static long buy(ServerPlayer player, String id, long qty, boolean all) {
		CounterSessions.Session s = CounterSessions.validate(player);
		if (s == null) {
			CounterSessions.result(player, false, Component.translatable("gui.burmaldaholic.cashier.store.away"));
			return 0;
		}
		if (!CounterSessions.tryAct(player)) return 0;
		return buyAt(player, s, id, qty, all);
	}

	/** {@link #buy} for an already validated session (and cooldown); GameTests use it for another dimension's counter. */
	public static long buyAt(ServerPlayer player, CounterSessions.Session s, String id, long qty, boolean all) {
		MinecraftServer server = player.level().getServer();
		StoreCatalog.Entry e = StoreCatalog.entry(server.registryAccess(), s.dim(), id).orElse(null);
		if (e == null) {
			fail(player, s, Component.translatable("gui.burmaldaholic.error.disabled"));
			return 0;
		}
		int tier = CoreServices.vip().tier(server, player.getUUID());
		if (tier < e.minTier()) {
			fail(player, s, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(e.minTier())));
			return 0;
		}
		long day = today(server);
		StoreLimits.Data data = limits(player);
		long left = data.left(e.id(), day, e.limitPerDay(), e.limitEveryDays());
		ItemStack one = e.stack().copyWithCount(1);
		int per = e.stack().getCount();
		long fit = ExchangeRows.fit(player.getInventory(), one);
		long balance = Economies.get().balance(player);
		long n = ExchangeMath.countBuy(qty, all, balance, e.price(), fit, per, left);
		if (n <= 0) {
			Component why = left == 0 ? Component.translatable("gui.burmaldaholic.cashier.store.limit_reached")
				: balance < e.price() ? Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance))
				: Component.translatable("gui.burmaldaholic.error.inventory_full");
			fail(player, s, why);
			return 0;
		}
		long cost = n * e.price();
		if (!Economies.get().tryWithdraw(player, cost, new Transaction("core", "store_" + e.id(), Transaction.Kind.OTHER))) {
			fail(player, s, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
			return 0;
		}
		long items = n * per;
		long inserted = ExchangeRows.insert(player.getInventory(), one, items);
		long bought = n;
		if (inserted < items) { // cannot happen (fit checked), but never keep chips for items not handed out
			long missing = items - inserted;
			long refundUnits = (missing + per - 1) / per;
			Economies.get().deposit(player, refundUnits * e.price(), new Transaction("core", "store_refund_" + e.id(), Transaction.Kind.REFUND));
			bought = n - refundUnits;
		}
		if (e.limitPerDay() > 0 && bought > 0) player.setAttached(LIMITS, data.record(e.id(), day, (int) bought));
		Component line = Component.translatable("msg.burmaldaholic.core.store.bought", Texts.number(bought * per), e.stack().getHoverName(),
			Texts.chips(bought * e.price()));
		boolean partial = !all && bought < qty;
		CounterSessions.result(player, true, partial
			? Component.translatable("msg.burmaldaholic.core.exchange.partial", Texts.number(bought), Texts.number(qty)) : line);
		player.sendSystemMessage(line);
		sync(player, s);
		return bought;
	}

	private static void fail(ServerPlayer player, CounterSessions.Session s, Component why) {
		CounterSessions.result(player, false, why);
		sync(player, s);
	}
}
