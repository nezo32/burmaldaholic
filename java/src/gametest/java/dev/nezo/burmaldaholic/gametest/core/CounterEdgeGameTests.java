package dev.nezo.burmaldaholic.gametest.core;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.store.StoreLimits;
import dev.nezo.burmaldaholic.core.store.StoreService;
import dev.nezo.burmaldaholic.core.store.net.StoreActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.loan.LoanService;
import dev.nezo.burmaldaholic.vip.VipData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * T-EC1 (tester): money / item edge cases of the EC-1 counter through the real receivers ({@link TableNetworking#receive},
 * {@link StoreService#receive}).
 */
public class CounterEdgeGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");
	private static final Map<UUID, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

	private static ServerPlayer player(GameTestHelper helper, long balance) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "tec1_" + CHANNELS.size()), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		CHANNELS.put(player.getUUID(), new EmbeddedChannel(connection));
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 1.5)));
		player.getInventory().clearContent();
		Economies.get().setBalance(server, player.getUUID(), balance, TEST);
		CounterSessions.resetCooldown(player);
		return player;
	}

	private static void withPlayer(GameTestHelper helper, long balance, Consumer<ServerPlayer> body) {
		ServerPlayer p = player(helper, balance);
		try {
			body.accept(p);
		} finally {
			p.closeContainer();
			CounterSessions.close(p);
			helper.getLevel().getServer().getPlayerList().remove(p);
			CHANNELS.remove(p.getUUID());
		}
	}

	private static CashierBlockEntity open(GameTestHelper helper, ServerPlayer player) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, CoreContent.CASHIER.block());
		CashierBlockEntity cashier = helper.getBlockEntity(rel, CashierBlockEntity.class);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu m && m.pos().equals(cashier.getBlockPos()), "the cashier menu is open");
		return cashier;
	}

	private static void exchange(ServerPlayer p, CashierBlockEntity c, String row, String dir, long qty, boolean all) {
		CounterSessions.resetCooldown(p);
		CompoundTag args = new CompoundTag();
		args.putString("row", row);
		args.putString("dir", dir);
		args.putLong("qty", qty);
		args.putBoolean("all", all);
		TableNetworking.receive(p, new TableActionPayload(c.getBlockPos(), "exchange", args));
	}

	private static void buy(ServerPlayer p, String id, int qty, boolean all) {
		CounterSessions.resetCooldown(p);
		StoreService.receive(p, StoreActionPayload.buy(id, qty, all));
	}

	private static long bal(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	private static int itemEntities(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).size();
	}

	private static void fillBut(ServerPlayer p, int free) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < 36 - free; i++) inv.setItem(i, new ItemStack(Items.DIRT, 64));
	}

	private static long total(ServerPlayer p, net.minecraft.world.item.Item item) {
		return p.getInventory().countItem(item);
	}

	// ---- exchange -------------------------------------------------------------------------------------------------

	@GameTest
	public void staleCountsNegativeAndOverflowQuantities(GameTestHelper helper) {
		withPlayer(helper, 0, p -> {
			CashierBlockEntity c = open(helper, p);
			p.getInventory().setItem(3, new ItemStack(Items.EMERALD, 10));
			exchange(p, c, "emerald", "in", 64, false); // the client believed it had 64
			helper.assertTrue(bal(p) == 90 && total(p, Items.EMERALD) == 0, "stale 64: only the 10 held are credited, got " + bal(p));
			exchange(p, c, "emerald", "in", -5, false);
			exchange(p, c, "emerald", "out", -5, false);
			exchange(p, c, "emerald", "out", Long.MIN_VALUE, false);
			helper.assertTrue(bal(p) == 90 && total(p, Items.EMERALD) == 0, "negative qty moves nothing");
			exchange(p, c, "emerald", "out", Long.MAX_VALUE, false);
			helper.assertTrue(total(p, Items.EMERALD) == 9 && bal(p) == 0, "Long.MAX out = what the balance pays (9), bal " + bal(p));
			exchange(p, c, "nope", "out", 1, false);
			exchange(p, c, "netherite_scrap", "in", 1, true); // nether row at an overworld cashier
			exchange(p, c, "gold_ingot", "out", 1, false);
			helper.assertTrue(bal(p) == 0 && total(p, Items.GOLD_INGOT) == 0, "unknown / other-dimension rows do nothing");
		});
		helper.succeed();
	}

	@GameTest
	public void renamedStacksAreNotMoneyAndPartialStacksFillFirst(GameTestHelper helper) {
		withPlayer(helper, 10_000, p -> {
			CashierBlockEntity c = open(helper, p);
			ItemStack named = new ItemStack(Items.EMERALD, 20);
			named.set(DataComponents.CUSTOM_NAME, Component.literal("keepsake"));
			p.getInventory().setItem(0, named);
			p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 5));
			exchange(p, c, "emerald", "in", 0, true);
			helper.assertTrue(bal(p) == 10_000 + 45 && p.getInventory().getItem(0).getCount() == 20, "only plain emeralds: " + bal(p));
			// out: 2 free... fill all but slot 1 (empty) + slot 0 named; buy 70 -> 64 fit into slot 1 only
			fillBut(p, 0);
			p.getInventory().setItem(0, named.copy());
			p.getInventory().setItem(1, new ItemStack(Items.EMERALD, 60));
			int before = itemEntities(helper);
			exchange(p, c, "emerald", "out", 64, false);
			helper.assertTrue(p.getInventory().getItem(1).getCount() == 64 && p.getInventory().getItem(0).getCount() == 20,
				"only 4 fit on the partial plain stack; the named stack untouched");
			helper.assertTrue(bal(p) == 10_045 - 40, "paid for 4 only: " + bal(p));
			helper.assertTrue(itemEntities(helper) == before, "nothing dropped");
		});
		helper.succeed();
	}

	@GameTest
	public void offhandAndArmourAreNeverTouched(GameTestHelper helper) {
		withPlayer(helper, 0, p -> {
			CashierBlockEntity c = open(helper, p);
			p.getInventory().setItem(40, new ItemStack(Items.EMERALD, 64)); // off-hand slot
			exchange(p, c, "emerald", "in", 0, true);
			helper.assertTrue(bal(p) == 0 && p.getInventory().getItem(40).getCount() == 64, "off-hand emeralds are not counted");
		});
		helper.succeed();
	}

	@GameTest
	public void netheriteVipGateAndDefaultBlocksExchangeOut(GameTestHelper helper) {
		EconomyConfig.ExchangeRow scrap = CasinoConfig.economy().exchange.get("netherite_scrap");
		String dims = scrap.dims;
		scrap.dims = ""; // the test level is the overworld
		try {
			withPlayer(helper, 10_000, p -> {
				CashierBlockEntity c = open(helper, p);
				MinecraftServer server = helper.getLevel().getServer();
				VipData.get(server).player(p.getUUID()).tier = 3;
				exchange(p, c, "netherite_scrap", "out", 1, false);
				helper.assertTrue(total(p, Items.NETHERITE_SCRAP) == 0 && bal(p) == 10_000, "Platinum < Diamond: refused");
				VipData.get(server).player(p.getUUID()).tier = 4;
				exchange(p, c, "netherite_scrap", "out", 1, false);
				helper.assertTrue(total(p, Items.NETHERITE_SCRAP) == 1 && bal(p) == 10_000 - 160, "Diamond VIP buys");
				exchange(p, c, "netherite_scrap", "in", 1, false);
				helper.assertTrue(bal(p) == 10_000 - 100, "sold back at 60");
			});
		} finally {
			scrap.dims = dims;
		}
		helper.succeed();
	}

	// ---- store -----------------------------------------------------------------------------------------------------

	@GameTest
	public void storeSameTickDoubleBuyAndNegatives(GameTestHelper helper) {
		withPlayer(helper, 1000, p -> {
			open(helper, p);
			CounterSessions.resetCooldown(p);
			StoreService.receive(p, StoreActionPayload.buy("bread", 10, false));
			StoreService.receive(p, StoreActionPayload.buy("bread", 10, false)); // same tick
			helper.assertTrue(total(p, Items.BREAD) == 10 && bal(p) == 980, "bought once: " + bal(p));
			buy(p, "bread", -3, false);
			buy(p, "bread", Integer.MIN_VALUE, false);
			helper.assertTrue(total(p, Items.BREAD) == 10 && bal(p) == 980, "negative qty buys nothing");
			buy(p, "bread", Integer.MAX_VALUE, false);
			helper.assertTrue(total(p, Items.BREAD) == 10 + 490 && bal(p) == 0, "MAX qty = what the balance pays: " + bal(p));
			buy(p, "no_such_offer", 1, false);
			buy(p, "emerald", 1, false);
		});
		helper.succeed();
	}

	@GameTest
	public void storeMaxUnstackableFitsFreeSlots(GameTestHelper helper) {
		withPlayer(helper, 10_000, p -> {
			open(helper, p);
			fillBut(p, 3);
			int before = itemEntities(helper);
			buy(p, "fishing_rod", 0, true);
			helper.assertTrue(total(p, Items.FISHING_ROD) == 3 && bal(p) == 10_000 - 30, "3 rods for 3 free slots, bal " + bal(p));
			buy(p, "fishing_rod", 1, false);
			helper.assertTrue(total(p, Items.FISHING_ROD) == 3 && bal(p) == 10_000 - 30, "full: nothing charged");
			helper.assertTrue(itemEntities(helper) == before, "nothing dropped");
		});
		helper.succeed();
	}

	@GameTest
	public void dailyLimitRollsOverAndElytraEverySevenDays(GameTestHelper helper) {
		withPlayer(helper, 100_000, p -> {
			open(helper, p);
			MinecraftServer server = helper.getLevel().getServer();
			long today = StoreService.today(server);
			// 8 apples bought "yesterday"
			p.setAttached(StoreService.LIMITS, StoreLimits.Data.EMPTY.record("golden_apple", today - 1, 8));
			buy(p, "golden_apple", 64, false);
			helper.assertTrue(total(p, Items.GOLDEN_APPLE) == 8, "a new day: 8 again, got " + total(p, Items.GOLDEN_APPLE));
			buy(p, "golden_apple", 1, false);
			helper.assertTrue(total(p, Items.GOLDEN_APPLE) == 8, "then capped");
			CounterSessions.Session end = new CounterSessions.Session(CounterSource.command(), Level.END);
			VipData.get(server).player(p.getUUID()).tier = 3;
			StoreLimits.Data d = StoreService.limits(p);
			java.util.Map<String, Long> last = new java.util.HashMap<>(d.lastBuyDay());
			last.put("elytra", today - 6);
			p.setAttached(StoreService.LIMITS, new StoreLimits.Data(d.day(), d.boughtToday(), last));
			StoreService.buyAt(p, end, "elytra", 1, false);
			helper.assertTrue(total(p, Items.ELYTRA) == 0, "6 days after an elytra: locked");
			last.put("elytra", today - 7);
			p.setAttached(StoreService.LIMITS, new StoreLimits.Data(d.day(), d.boughtToday(), last));
			StoreService.buyAt(p, end, "elytra", 64, false);
			helper.assertTrue(total(p, Items.ELYTRA) == 1, "7 days later: exactly one");
			helper.assertTrue(StoreService.limits(p).left("golden_apple", today, 8, 1) == 0, "the elytra buy keeps today's apple count");
			StoreService.buyAt(p, end, "elytra", 1, false);
			helper.assertTrue(total(p, Items.ELYTRA) == 1, "and the next elytra is locked again");
		});
		helper.succeed();
	}

	@GameTest
	public void endOnlyOffersRefusedInOverworldAndNether(GameTestHelper helper) {
		withPlayer(helper, 100_000, p -> {
			open(helper, p);
			MinecraftServer server = helper.getLevel().getServer();
			VipData.get(server).player(p.getUUID()).tier = 5;
			for (var dim : java.util.List.of(Level.OVERWORLD, Level.NETHER)) {
				CounterSessions.Session s = new CounterSessions.Session(CounterSource.command(), dim);
				StoreService.buyAt(p, s, "elytra", 1, false);
				StoreService.buyAt(p, s, "shulker_shell", 1, false);
			}
			buy(p, "elytra", 1, false);
			buy(p, "shulker_shell", 1, false);
			helper.assertTrue(total(p, Items.ELYTRA) == 0 && total(p, Items.SHULKER_SHELL) == 0 && bal(p) == 100_000, "End-only stays End-only");
			buy(p, "strength", 1, false);
			helper.assertTrue(total(p, Items.POTION) == 1, "tier 5 buys a Gold potion");
			VipData.get(server).player(p.getUUID()).tier = 1;
			buy(p, "strength", 1, false);
			buy(p, "book_mending", 1, false);
			helper.assertTrue(total(p, Items.POTION) == 1 && total(p, Items.ENCHANTED_BOOK) == 0, "Silver: Gold offers refused");
		});
		helper.succeed();
	}

	/**
	 * Exchange "out" refuses a player in default (withdrawable = balance − debt). The store uses the raw balance: borrowed
	 * chips become items. Asserted as the SAFE behaviour; a failure here is defect T-EC1 D1.
	 */
	@GameTest
	public void storeRespectsDebtLikeExchangeOut(GameTestHelper helper) {
		withPlayer(helper, 1000, p -> {
			open(helper, p);
			MinecraftServer server = helper.getLevel().getServer();
			LoanService.adminSet(server, p.getUUID(), 5000);
			LoanService.adminDefault(server, p.getUUID());
			helper.assertTrue(CoreServices.debt().inDefault(server, p.getUUID()), "in default");
			long b0 = bal(p);
			buy(p, "ender_pearl", 16, false);
			helper.assertTrue(total(p, Items.ENDER_PEARL) == 0 && bal(p) == b0,
				"D1: a defaulted player turned " + (b0 - bal(p)) + " owed chips into " + total(p, Items.ENDER_PEARL) + " ender pearls");
		});
		helper.succeed();
	}

	/** The v0.1 {@code shop_buy} action is still live on the cashier: no cooldown, drops at the feet (T-EC1 D2). */
	@GameTest
	public void legacyShopBuyDoesNotDrop(GameTestHelper helper) {
		withPlayer(helper, 100_000, p -> {
			CashierBlockEntity c = open(helper, p);
			fillBut(p, 0);
			int before = itemEntities(helper);
			CompoundTag args = new CompoundTag();
			args.putString("id", "scratch_card");
			TableNetworking.receive(p, new TableActionPayload(c.getBlockPos(), "shop_buy", args));
			helper.assertTrue(itemEntities(helper) == before && bal(p) == 100_000,
				"D2: legacy shop_buy is still live (no cooldown, no fit check): charged " + (100_000 - bal(p)) + ", drops " + (itemEntities(helper) - before));
		});
		helper.succeed();
	}
}
