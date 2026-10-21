package dev.nezo.burmaldaholic.core.cashier;

import dev.nezo.burmaldaholic.core.chips.ChipItem;
import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 * Cashier (GAME_DESIGN.md §3.2, UI.md §3, EC-SPEC §1): deposit chip items, withdraw chips (greedy denominations,
 * limited to {@code balance − debt}, frozen in default) and the exchange rows of {@code economy.exchange} for the
 * dimension of the cashier (emerald, diamond, gold ingot, …). Server-driven screen via the generic table framework.
 *
 * <p>Client actions: {@code deposit_all}, {@code deposit_held}, {@code withdraw {amount, denom}},
 * {@code exchange {row, dir: "in" | "out", qty, all}} (the server computes "all"; never drops items at the feet).
 * The store tab (scratch cards included) goes through {@code StoreService} / {@code StoreActionPayload}; the v0.1
 * {@code shop_buy} action was removed (T-EC1 D2).
 */
public class CashierBlockEntity extends CasinoTableBlockEntity {
	public static final String GAME_ID = "core";
	/**
	 * Review M3: one withdrawal hands out at most this many item stacks (one inventory's worth), inventory first;
	 * only the rest drops at the player's feet. Larger amounts take several clicks.
	 */
	public static final int MAX_WITHDRAW_STACKS = 36;

	/** Counting tray (lane J-L2, extras.md §8.3): what the last move of a viewer was, for the client's chip columns. */
	public enum MoveKind {
		/** Chip items taken from the inventory into the balance. */
		DEPOSIT,
		/** Chip items handed out from the balance. */
		WITHDRAW,
		/** Chips credited for emeralds / gold (no chip items moved; the columns show the greedy breakdown). */
		CREDIT,
		/** Chips paid for emeralds / gold or a shop item (greedy breakdown). */
		PAID
	}

	/**
	 * One move for the tray: {@code counts} per {@link ChipMath#DENOMINATIONS} (largest first) — for deposits and
	 * withdrawals the chip items actually moved; {@code seq} increases per move so the client replays each once.
	 */
	public record Move(int seq, MoveKind kind, long amount, long[] counts) {}

	/** Last move per player (transient: a reloaded cashier shows an empty tray). */
	private final Map<UUID, Move> lastMoves = new HashMap<>();
	private int moveSeq;

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

	/** Dimension code of this cashier's level ({@code O/N/E/?}): filters the exchange rows. */
	public String dim() {
		return level == null ? "O" : CounterSessions.dimCode(level.dimension());
	}

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "deposit_all" -> depositAll(player);
			case "deposit_held" -> depositHeld(player);
			case "withdraw" -> withdraw(player, args.getLongOr("amount", 0), args.getIntOr("denom", 0));
			case "exchange" -> exchange(player, args.getStringOr("row", ""), "out".equals(args.getStringOr("dir", "in")),
				args.getLongOr("qty", 1), args.getBooleanOr("all", false));
			default -> {
				return;
			}
		}
		syncViewers();
	}

	/** Room left under {@code economy.maxBalance} (review m2: never take items whose value would be capped away). */
	private static long room(ServerPlayer player) {
		return Math.max(0, CasinoConfig.economy().maxBalance - Economies.get().balance(player));
	}

	/**
	 * Takes up to {@code room} chips' worth from one stack (whole chips only) and returns their value; the
	 * rest stays in the slot.
	 */
	private static long take(ItemStack stack, long room, long[] counts) {
		if (!(stack.getItem() instanceof ChipItem chip) || chip.value() <= 0 || stack.isEmpty()) {
			return 0;
		}
		int n = (int) Math.min(stack.getCount(), room / chip.value());
		if (n <= 0) {
			return 0;
		}
		stack.shrink(n);
		int index = denomIndex(chip.value());
		if (index >= 0) {
			counts[index] += n;
		}
		return (long) n * chip.value();
	}

	private static int denomIndex(long value) {
		for (int i = 0; i < ChipMath.DENOMINATIONS.length; i++) {
			if (ChipMath.DENOMINATIONS[i] == value) {
				return i;
			}
		}
		return -1;
	}

	/** Records a move for the counting tray; {@code counts == null} = the greedy breakdown of {@code amount}. */
	private void recordMove(ServerPlayer player, MoveKind kind, long amount, long[] counts) {
		if (amount <= 0) {
			return;
		}
		lastMoves.put(player.getUUID(), new Move(++moveSeq, kind, amount, counts != null ? counts : ChipMath.split(amount)));
	}

	/** The last recorded move of {@code player} at this cashier (null = none since it was loaded). */
	public Move lastMove(ServerPlayer player) {
		return lastMoves.get(player.getUUID());
	}

	/** Deposits every chip item in the inventory (as far as the balance cap allows). Returns chips deposited. */
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
		long room = room(player);
		long taken = 0;
		long[] counts = new long[ChipMath.DENOMINATIONS.length];
		for (int i = 0; i < inv.getContainerSize() && room - taken > 0; i++) {
			taken += take(inv.getItem(i), room - taken, counts);
		}
		inv.setChanged();
		if (taken <= 0) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.balance_full"));
			return 0;
		}
		if (taken < total) {
			player.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.balance_full"));
		}
		recordMove(player, MoveKind.DEPOSIT, taken, counts);
		return credit(player, taken);
	}

	public long depositHeld(ServerPlayer player) {
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		long value = ChipItem.valueOf(held);
		if (value <= 0) {
			sendError(player, Component.translatable("msg.burmaldaholic.core.no_chips_to_deposit"));
			return 0;
		}
		long[] counts = new long[ChipMath.DENOMINATIONS.length];
		long taken = take(held, room(player), counts);
		if (taken <= 0) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.balance_full"));
			return 0;
		}
		if (taken < value) {
			player.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.balance_full"));
		}
		recordMove(player, MoveKind.DEPOSIT, taken, counts);
		return credit(player, taken);
	}

	private long credit(ServerPlayer player, long value) {
		Economies.get().deposit(player, value, tx("deposit"));
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.deposited", Texts.chips(value), Texts.number(Economies.get().balance(player))));
		return value;
	}

	/**
	 * Withdraws {@code amount} as chip items ({@code denom} 0 = greedy auto, else one of
	 * {@link ChipMath#DENOMINATIONS}). At most {@link #MAX_WITHDRAW_STACKS} stacks per call (review M3): a larger
	 * amount is reduced and the player is told the limit. Returns true on success.
	 */
	public boolean withdraw(ServerPlayer player, long amount, int denom) {
		MinecraftServer server = server(player);
		if (CoreServices.debt().inDefault(server, player.getUUID())) {
			sendError(player, Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked"));
			return false;
		}
		if (amount <= 0 || (denom != 0 && !ChipMath.isDenomination(denom))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return false;
		}
		long max = withdrawable(player);
		if (amount > max) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(max)));
			return false;
		}
		int stackSize = new ItemStack(Chips.item(ChipMath.DENOMINATIONS[0])).getMaxStackSize();
		long capped = ChipMath.capToStacks(amount, denom, MAX_WITHDRAW_STACKS, stackSize);
		if (capped < amount) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.withdraw_capped", Texts.number(capped)));
			amount = capped;
		}
		if (amount <= 0 || !Economies.get().tryWithdraw(player, amount, tx("withdraw"))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))));
			return false;
		}
		long[] counts = denom > 0 ? ChipMath.split(amount, denom) : ChipMath.split(amount);
		recordMove(player, MoveKind.WITHDRAW, amount, counts.clone());
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

	/**
	 * One exchange (EC-SPEC §1): {@code out = false} hands items in for chips, {@code out = true} buys items with chips.
	 * The count is computed here ({@link ExchangeMath}); items only go into free main-inventory room (a smaller count +
	 * {@code msg…exchange.partial} instead of drops). Gated by the shared 4-tick counter cooldown. Returns the item
	 * count moved.
	 */
	public long exchange(ServerPlayer player, String rowId, boolean out, long qty, boolean all) {
		if (!CounterSessions.tryAct(player)) return 0;
		ExchangeRows.Row row = ExchangeRows.row(dim(), rowId).orElse(null);
		if (row == null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return 0;
		}
		Inventory inv = player.getInventory();
		ItemStack proto = new ItemStack(row.item());
		Component name = proto.getHoverName();
		if (!out) {
			long have = ExchangeRows.count(inv, row.item());
			if (row.toChips() <= 0 || have <= 0) {
				sendError(player, Component.translatable("gui.burmaldaholic.cashier.exchange.none", name));
				return 0;
			}
			long n = ExchangeMath.countIn(qty, all, have, room(player), row.toChips());
			if (n <= 0) {
				sendError(player, Component.translatable("gui.burmaldaholic.error.balance_full"));
				return 0;
			}
			long removed = ExchangeRows.remove(inv, row.item(), n); // credit only what was really taken (stale-count dupe)
			long chips = removed * row.toChips();
			Economies.get().deposit(player, chips, tx("exchange_in_" + row.id()));
			recordMove(player, MoveKind.CREDIT, chips, null);
			long wanted = all ? have : Math.min(qty, have);
			report(player, Component.translatable("msg.burmaldaholic.core.exchange.in", Texts.number(removed), name, Texts.chips(chips)), removed, wanted);
			return removed;
		}
		MinecraftServer server = server(player);
		if (CoreServices.debt().inDefault(server, player.getUUID())) {
			sendError(player, Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked"));
			return 0;
		}
		if (row.fromChips() <= 0) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return 0;
		}
		if (CoreServices.vip().tier(server, player.getUUID()) < row.buyMinTier()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(row.buyMinTier())));
			return 0;
		}
		long fit = ExchangeRows.fit(inv, proto);
		long wd = withdrawable(player);
		long n = ExchangeMath.countOut(qty, all, wd, row.fromChips(), fit);
		if (n <= 0) {
			sendError(player, fit <= 0 ? Component.translatable("gui.burmaldaholic.error.inventory_full")
				: Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(wd)));
			return 0;
		}
		long cost = n * row.fromChips();
		if (!Economies.get().tryWithdraw(player, cost, tx("exchange_out_" + row.id()))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))));
			return 0;
		}
		long inserted = ExchangeRows.insert(inv, proto, n);
		if (inserted < n) {
			Economies.get().deposit(player, (n - inserted) * row.fromChips(), tx("exchange_refund_" + row.id()));
		}
		long paid = inserted * row.fromChips();
		recordMove(player, MoveKind.PAID, paid, null);
		long wanted = all ? Math.min(ExchangeMath.MAX_UNITS, wd / row.fromChips()) : qty;
		report(player, Component.translatable("msg.burmaldaholic.core.exchange.out", Texts.number(inserted), name, Texts.chips(paid)), inserted, wanted);
		return inserted;
	}

	/** Chat + the counter's result line; adds the "only N fit" line when fewer than wanted moved. */
	private static void report(ServerPlayer player, Component line, long moved, long wanted) {
		player.sendSystemMessage(line);
		if (moved < wanted) {
			Component partial = Component.translatable("msg.burmaldaholic.core.exchange.partial", Texts.number(moved), Texts.number(wanted));
			player.sendSystemMessage(partial);
			CounterSessions.result(player, true, partial);
		} else {
			CounterSessions.result(player, true, line);
		}
	}

	/** Largest amount ≤ withdrawable whose greedy chip split fits the main inventory (the Withdraw "Max" button). */
	public static long withdrawFit(ServerPlayer player) {
		long max = withdrawable(player);
		int stackSize = new ItemStack(Chips.item(ChipMath.DENOMINATIONS[0])).getMaxStackSize();
		max = Math.min(max, ChipMath.capToStacks(max, 0, MAX_WITHDRAW_STACKS, stackSize));
		Inventory inv = player.getInventory();
		long lo = 0, hi = max;
		while (lo < hi) {
			long mid = lo + (hi - lo + 1) / 2;
			if (splitFits(inv, mid)) lo = mid;
			else hi = mid - 1;
		}
		return lo;
	}

	private static boolean splitFits(Inventory inv, long amount) {
		long[] counts = ChipMath.split(amount);
		int empty = 0;
		for (int i = 0; i < ExchangeRows.MAIN_SLOTS; i++) if (inv.getItem(i).isEmpty()) empty++;
		long slotsNeeded = 0;
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] <= 0) continue;
			ItemStack proto = new ItemStack(Chips.item(ChipMath.DENOMINATIONS[i]));
			long partial = 0;
			for (int j = 0; j < ExchangeRows.MAIN_SLOTS; j++) {
				ItemStack s = inv.getItem(j);
				if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, proto)) partial += Math.max(0, s.getMaxStackSize() - s.getCount());
			}
			long rest = Math.max(0, counts[i] - partial);
			slotsNeeded += (rest + proto.getMaxStackSize() - 1) / proto.getMaxStackSize();
		}
		return slotsNeeded <= empty;
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
		tag.putLong("withdrawable", withdrawable(viewer));
		tag.putLong("debt", CoreServices.debt().owed(server, viewer.getUUID()));
		tag.putBoolean("in_default", CoreServices.debt().inDefault(server, viewer.getUUID()));
		tag.putLong("withdraw_fit", withdrawFit(viewer));
		tag.putString("dim", dim());
		net.minecraft.nbt.ListTag rows = new net.minecraft.nbt.ListTag();
		Inventory vinv = viewer.getInventory();
		long wd = withdrawable(viewer);
		long room = room(viewer);
		for (ExchangeRows.Row r : ExchangeRows.rows(dim())) {
			CompoundTag t = new CompoundTag();
			t.putString("id", r.id());
			t.putString("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.item()).toString());
			t.putInt("to", r.toChips());
			t.putInt("from", r.fromChips());
			long have = ExchangeRows.count(vinv, r.item());
			t.putLong("have", have);
			t.putInt("gate", r.buyMinTier());
			t.putLong("max_in", ExchangeMath.countIn(0, true, have, room, r.toChips()));
			t.putLong("max_out", ExchangeMath.countOut(0, true, wd, r.fromChips(), ExchangeRows.fit(vinv, new ItemStack(r.item()))));
			rows.add(t);
		}
		tag.put("rows", rows);
		long carried = 0;
		Inventory inv = viewer.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			carried += ChipItem.valueOf(inv.getItem(i));
		}
		tag.putLong("carried_chips", carried);
		tag.putLong("held_chips", ChipItem.valueOf(viewer.getItemInHand(InteractionHand.MAIN_HAND)));
		int tier = CoreServices.vip().tier(server, viewer.getUUID());
		tag.putInt("vip", tier);
		Move move = lastMove(viewer);
		if (move != null) {
			CompoundTag tray = new CompoundTag();
			tray.putInt("seq", move.seq());
			tray.putString("kind", move.kind().name().toLowerCase(java.util.Locale.ROOT));
			tray.putLong("amount", move.amount());
			for (int i = 0; i < move.counts().length && i < ChipMath.DENOMINATIONS.length; i++) {
				tray.putLong(Integer.toString(ChipMath.DENOMINATIONS[i]), move.counts()[i]);
			}
			tag.put("tray", tray);
		}
		return tag;
	}

}
