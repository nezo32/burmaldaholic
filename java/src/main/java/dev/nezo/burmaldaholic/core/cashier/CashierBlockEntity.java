package dev.nezo.burmaldaholic.core.cashier;

import dev.nezo.burmaldaholic.core.chips.ChipItem;
import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cashier (GAME_DESIGN.md §3.2, UI.md §3): deposit chip items, withdraw chips (greedy denominations,
 * limited to {@code balance − debt}, frozen in default), buy/sell chips for emeralds; the Nether
 * cashier also trades gold ingots. Server-driven screen via the generic table framework.
 *
 * <p>Client actions: {@code deposit_all}, {@code deposit_held}, {@code withdraw {amount, denom}},
 * {@code buy {count}}, {@code sell {count}}, {@code buy_gold {count}}, {@code sell_gold {count}}.
 */
public class CashierBlockEntity extends CasinoTableBlockEntity {
	public static final String GAME_ID = "core";
	private static final int MAX_BATCH = 64;

	public CashierBlockEntity(TableType<CashierBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected int seatCount() {
		return 0;
	}

	@Override
	public String gameId() {
		return GAME_ID;
	}

	public boolean isNether() {
		return tableType().name().equals("nether_cashier");
	}

	private static Transaction tx(String detail) {
		return new Transaction(GAME_ID, detail, Transaction.Kind.CASHIER);
	}

	private static MinecraftServer server(ServerPlayer p) {
		return p.level().getServer();
	}

	/** {@code max(0, balance − debt)}, 0 in default (§3.2). */
	public static long withdrawable(ServerPlayer player) {
		MinecraftServer server = server(player);
		return ChipMath.withdrawable(Economies.get().balance(player),
			CoreServices.debt().owed(server, player.getUUID()), CoreServices.debt().inDefault(server, player.getUUID()));
	}

	public static int emeraldBuyRate(ServerPlayer player) {
		EconomyConfig c = CasinoConfig.economy();
		return CoreServices.vip().tier(server(player), player.getUUID()) >= VipTiers.GOLD ? c.emeraldBuyRateGoldVip : c.emeraldBuyRate;
	}

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "deposit_all" -> depositAll(player);
			case "deposit_held" -> depositHeld(player);
			case "withdraw" -> withdraw(player, args.getLongOr("amount", 0), args.getIntOr("denom", 0));
			case "buy" -> buy(player, Items.EMERALD, clampCount(args), emeraldBuyRate(player), "buy_emerald", "unit.burmaldaholic.emerald");
			case "sell" -> sell(player, Items.EMERALD, clampCount(args), CasinoConfig.economy().emeraldSellRate, "sell_emerald", "unit.burmaldaholic.emerald");
			case "buy_gold" -> {
				if (isNether()) {
					buy(player, Items.GOLD_INGOT, clampCount(args), CasinoConfig.economy().goldBuyRate, "buy_gold", "unit.burmaldaholic.gold_ingot");
				}
			}
			case "sell_gold" -> {
				if (isNether()) {
					sell(player, Items.GOLD_INGOT, clampCount(args), CasinoConfig.economy().goldSellRate, "sell_gold", "unit.burmaldaholic.gold_ingot");
				}
			}
			case "shop_buy" -> shopBuy(player, args.getStringOr("id", ""));
			default -> {
				return;
			}
		}
		syncViewers();
	}

	/** Shop tab: buys one item of offer {@code id} (VIP tier + funds checked). */
	public boolean shopBuy(ServerPlayer player, String id) {
		CashierShop.Offer offer = CashierShop.byId(id);
		if (offer == null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return false;
		}
		int tier = CoreServices.vip().tier(server(player), player.getUUID());
		if (tier < offer.minTier()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(offer.minTier())));
			return false;
		}
		long price = Math.max(0, offer.price().getAsLong());
		ItemStack item = offer.item().get();
		if (item.isEmpty()) {
			return false;
		}
		if (!Economies.get().tryWithdraw(player, price, new Transaction(GAME_ID, "shop_" + id, Transaction.Kind.OTHER))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))));
			return false;
		}
		Component name = item.getHoverName();
		if (Inventories.giveOrDrop(player, item)) {
			player.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.inventory_full"));
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.bought_chips", name, Texts.chipsAcc(price)));
		return true;
	}

	private static int clampCount(CompoundTag args) {
		return Math.max(1, Math.min(MAX_BATCH, args.getIntOr("count", 1)));
	}

	/** Deposits every chip item in the inventory. Returns chips deposited. */
	public long depositAll(ServerPlayer player) {
		Inventory inv = player.getInventory();
		long total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			total += ChipItem.valueOf(inv.getItem(i));
		}
		if (total <= 0) {
			sendError(player, Component.translatable("msg.burmaldaholic.core.no_chips_to_deposit"));
			return 0;
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() instanceof ChipItem) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		return credit(player, total);
	}

	public long depositHeld(ServerPlayer player) {
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		long value = ChipItem.valueOf(held);
		if (value <= 0) {
			sendError(player, Component.translatable("msg.burmaldaholic.core.no_chips_to_deposit"));
			return 0;
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		return credit(player, value);
	}

	private long credit(ServerPlayer player, long value) {
		Economies.get().deposit(player, value, tx("deposit"));
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.deposited", Texts.chips(value), Texts.number(Economies.get().balance(player))));
		return value;
	}

	/** Withdraws {@code amount} as chip items ({@code denom} 0 = greedy auto). Returns true on success. */
	public boolean withdraw(ServerPlayer player, long amount, int denom) {
		MinecraftServer server = server(player);
		if (CoreServices.debt().inDefault(server, player.getUUID())) {
			sendError(player, Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked"));
			return false;
		}
		if (amount <= 0) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return false;
		}
		long max = withdrawable(player);
		if (amount > max) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(max)));
			return false;
		}
		if (!Economies.get().tryWithdraw(player, amount, tx("withdraw"))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))));
			return false;
		}
		long[] counts = denom > 0 ? ChipMath.split(amount, denom) : ChipMath.split(amount);
		boolean dropped = false;
		for (int i = 0; i < counts.length; i++) {
			dropped |= give(player, Chips.item(ChipMath.DENOMINATIONS[i]), counts[i]);
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.withdrawn", Texts.chips(amount), Texts.number(Economies.get().balance(player))));
		if (dropped) {
			player.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.inventory_full"));
		}
		return true;
	}

	private void buy(ServerPlayer player, Item currency, int count, int rate, String detail, String unitKey) {
		Inventory inv = player.getInventory();
		if (inv.countItem(currency) < count) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.plural(unitKey, inv.countItem(currency))));
			return;
		}
		remove(inv, currency, count);
		long chips = (long) count * rate;
		Economies.get().deposit(player, chips, tx(detail));
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.bought_chips", Texts.chipsAcc(chips), Texts.plural(unitKey, count)));
	}

	private void sell(ServerPlayer player, Item currency, int count, int rate, String detail, String unitKey) {
		MinecraftServer server = server(player);
		if (CoreServices.debt().inDefault(server, player.getUUID())) {
			sendError(player, Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked"));
			return;
		}
		long chips = (long) count * rate;
		if (withdrawable(player) < chips || !Economies.get().tryWithdraw(player, chips, tx(detail))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(withdrawable(player))));
			return;
		}
		if (give(player, currency, count)) {
			player.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.inventory_full"));
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.sold_chips", Texts.chipsAcc(chips), Texts.plural(unitKey, count)));
	}

	private static void remove(Inventory inv, Item item, int count) {
		int left = count;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
	}

	/** Gives {@code count} items (stacks of max size); returns true if something was dropped at the feet. */
	private static boolean give(ServerPlayer player, Item item, long count) {
		boolean dropped = false;
		long left = count;
		int max = new ItemStack(item).getMaxStackSize();
		while (left > 0) {
			int n = (int) Math.min(max, left);
			dropped |= Inventories.giveOrDrop(player, new ItemStack(item, n));
			left -= n;
		}
		return dropped;
	}

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		MinecraftServer server = server(viewer);
		EconomyConfig c = CasinoConfig.economy();
		tag.putLong("withdrawable", withdrawable(viewer));
		tag.putLong("debt", CoreServices.debt().owed(server, viewer.getUUID()));
		tag.putBoolean("in_default", CoreServices.debt().inDefault(server, viewer.getUUID()));
		tag.putInt("buy_rate", emeraldBuyRate(viewer));
		tag.putInt("sell_rate", c.emeraldSellRate);
		tag.putBoolean("nether", isNether());
		tag.putInt("gold_buy_rate", c.goldBuyRate);
		tag.putInt("gold_sell_rate", c.goldSellRate);
		long carried = 0;
		Inventory inv = viewer.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			carried += ChipItem.valueOf(inv.getItem(i));
		}
		tag.putLong("carried_chips", carried);
		tag.putLong("held_chips", ChipItem.valueOf(viewer.getItemInHand(InteractionHand.MAIN_HAND)));
		int tier = CoreServices.vip().tier(server, viewer.getUUID());
		tag.putInt("vip", tier);
		net.minecraft.nbt.ListTag shop = new net.minecraft.nbt.ListTag();
		for (CashierShop.Offer o : CashierShop.offers()) {
			CompoundTag t = new CompoundTag();
			t.putString("id", o.id());
			t.putString("name", o.item().get().getItem().getDescriptionId());
			t.putLong("price", o.price().getAsLong());
			t.putInt("min_tier", o.minTier());
			shop.add(t);
		}
		tag.put("shop", shop);
		return tag;
	}

}
