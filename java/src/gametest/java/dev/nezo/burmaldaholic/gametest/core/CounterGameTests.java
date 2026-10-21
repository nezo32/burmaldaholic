package dev.nezo.burmaldaholic.gametest.core;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.store.MerchantRole;
import dev.nezo.burmaldaholic.core.store.StoreService;
import dev.nezo.burmaldaholic.core.store.net.StoreActionPayload;
import dev.nezo.burmaldaholic.core.store.net.StoreSyncPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.vip.VipData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
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
 * EC-1 counter (EC-SPEC §1, §3, Tests): exchange and store through the real receivers — {@code table_action}
 * ({@link TableNetworking#receive}: cashier menu opened by using the block, in range) and {@code store_action}
 * ({@link StoreService#receive}: {@code CounterSessions.validate}).
 */
public class CounterGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");
	private static final Map<UUID, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

	private static ServerPlayer player(GameTestHelper helper, long balance) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ec1_" + CHANNELS.size()), false);
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

	private static void done(GameTestHelper helper, ServerPlayer p) {
		p.closeContainer();
		CounterSessions.close(p);
		helper.getLevel().getServer().getPlayerList().remove(p);
		CHANNELS.remove(p.getUUID());
	}

	private static void withPlayer(GameTestHelper helper, long balance, Consumer<ServerPlayer> body) {
		ServerPlayer p = player(helper, balance);
		try {
			body.accept(p);
		} finally {
			done(helper, p);
		}
	}

	/** Places a cashier at (1, 1, 1) and opens it by using the block. */
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

	private static long bal(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	private static int itemEntities(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).size();
	}

	/** Fills all but {@code free} main slots with dirt. */
	private static void fillBut(ServerPlayer p, int free) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < 36 - free; i++) inv.setItem(i, new ItemStack(Items.DIRT, 64));
	}

	// ---- exchange --------------------------------------------------------------------------------------------------

	@GameTest
	public void exchangeAllNearFullInventoryNoDrops(GameTestHelper helper) {
		withPlayer(helper, 0, p -> {
			CashierBlockEntity c = open(helper, p);
			fillBut(p, 2);
			p.getInventory().setItem(34, new ItemStack(Items.EMERALD, 64));
			p.getInventory().setItem(35, new ItemStack(Items.EMERALD, 30));
			int before = itemEntities(helper);
			exchange(p, c, "emerald", "in", 0, true);
			helper.assertTrue(bal(p) == 94 * 9, "all 94 emeralds handed in for 846, got " + bal(p));
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 0, "no emerald left");
			// buy back as many as fit: 2 free slots = 128, but 846 / 10 = 84
			exchange(p, c, "emerald", "out", 0, true);
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 84 && bal(p) == 846 - 840, "84 bought, 6 chips left: " + bal(p));
			helper.assertTrue(itemEntities(helper) == before, "no item entity spawned");
		});
		helper.succeed();
	}

	@GameTest
	public void maxOutWithThreeFreeSlotsGives192(GameTestHelper helper) {
		withPlayer(helper, 1_000_000, p -> {
			CashierBlockEntity c = open(helper, p);
			fillBut(p, 3);
			int before = itemEntities(helper);
			exchange(p, c, "emerald", "out", 0, true);
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 192, "exactly 192, got " + p.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(bal(p) == 1_000_000 - 1920, "paid 1920, balance " + bal(p));
			helper.assertTrue(itemEntities(helper) == before, "nothing dropped");
			exchange(p, c, "emerald", "out", 64, false);
			helper.assertTrue(bal(p) == 1_000_000 - 1920, "a full inventory buys nothing and pays nothing");
			// Shift+click x64 path: qty 64 on a free slot
			p.getInventory().setItem(0, ItemStack.EMPTY);
			exchange(p, c, "diamond", "out", 64, false);
			helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 64 && bal(p) == 1_000_000 - 1920 - 64 * 55, "x64 diamonds for 3520");
		});
		helper.succeed();
	}

	@GameTest
	public void exchangeNearMaxBalance(GameTestHelper helper) {
		long max = CasinoConfig.economy().maxBalance;
		withPlayer(helper, max - 50, p -> {
			CashierBlockEntity c = open(helper, p);
			p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
			exchange(p, c, "emerald", "in", 0, true);
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 64 - 5, "only 5 (45 chips) fit under the cap");
			helper.assertTrue(bal(p) == max - 5, "nothing lost to the cap: " + bal(p));
		});
		helper.succeed();
	}

	@GameTest
	public void twoAllsInOneTickNoDupe(GameTestHelper helper) {
		withPlayer(helper, 0, p -> {
			CashierBlockEntity c = open(helper, p);
			p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 10));
			CompoundTag args = new CompoundTag();
			args.putString("row", "emerald");
			args.putString("dir", "in");
			args.putBoolean("all", true);
			CounterSessions.resetCooldown(p);
			TableNetworking.receive(p, new TableActionPayload(c.getBlockPos(), "exchange", args));
			TableNetworking.receive(p, new TableActionPayload(c.getBlockPos(), "exchange", args)); // same tick
			helper.assertTrue(bal(p) == 90 && p.getInventory().countItem(Items.EMERALD) == 0, "credited once: " + bal(p));
			Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), 1000, TEST);
			args.putString("dir", "out");
			CounterSessions.resetCooldown(p);
			TableNetworking.receive(p, new TableActionPayload(c.getBlockPos(), "exchange", args));
			TableNetworking.receive(p, new TableActionPayload(c.getBlockPos(), "exchange", args));
			helper.assertTrue(bal(p) == 0 && p.getInventory().countItem(Items.EMERALD) == 100, "bought once with all 1000 chips: " + bal(p));
		});
		helper.succeed();
	}

	@GameTest
	public void cashierStateListsTheRows(GameTestHelper helper) {
		withPlayer(helper, 100, p -> {
			CashierBlockEntity c = open(helper, p);
			CompoundTag s = c.writeClientState(p);
			List<String> ids = s.getListOrEmpty("rows").stream().map(t -> ((CompoundTag) t).getStringOr("id", "")).toList();
			helper.assertTrue(ids.equals(List.of("emerald", "emerald_block", "diamond", "iron_block")), "overworld rows " + ids);
			helper.assertTrue(s.getLongOr("withdraw_fit", -1) == 100, "withdraw Max fits 100");
		});
		helper.succeed();
	}

	// ---- store -----------------------------------------------------------------------------------------------------

	@GameTest
	public void goldenAppleCappedAtL8(GameTestHelper helper) {
		withPlayer(helper, 100_000, p -> {
			open(helper, p);
			StoreService.receive(p, StoreActionPayload.buy("golden_apple", 64, false));
			helper.assertTrue(p.getInventory().countItem(Items.GOLDEN_APPLE) == 8, "8 apples, got " + p.getInventory().countItem(Items.GOLDEN_APPLE));
			helper.assertTrue(bal(p) == 100_000 - 8 * 80, "paid 640");
			CounterSessions.resetCooldown(p);
			StoreService.receive(p, StoreActionPayload.buy("golden_apple", 1, false));
			helper.assertTrue(p.getInventory().countItem(Items.GOLDEN_APPLE) == 8 && bal(p) == 100_000 - 640, "limit reached for today");
			CounterSessions.resetCooldown(p);
			StoreService.receive(p, StoreActionPayload.buy("bread", 64, false));
			helper.assertTrue(p.getInventory().countItem(Items.BREAD) == 64, "unlimited bread x64 (Shift+click)");
		});
		helper.succeed();
	}

	@GameTest
	public void elytraEndOnlyAtPlatinum(GameTestHelper helper) {
		withPlayer(helper, 100_000, p -> {
			open(helper, p);
			CounterSessions.Session here = CounterSessions.validate(p);
			helper.assertTrue(here != null && here.dim().equals("O"), "overworld cashier session");
			List<StoreSyncPayload.OfferView> view = StoreService.view(p, here);
			helper.assertTrue(view.stream().noneMatch(o -> o.id().equals("elytra")), "elytra hidden at an overworld cashier");
			helper.assertTrue(view.stream().anyMatch(o -> o.id().equals("shop.scratch_card")), "scratch cards in Games");
			StoreService.receive(p, StoreActionPayload.buy("elytra", 1, false));
			helper.assertTrue(p.getInventory().countItem(Items.ELYTRA) == 0, "not buyable in the overworld");
			CounterSessions.Session end = new CounterSessions.Session(CounterSource.command(), Level.END);
			helper.assertTrue(StoreService.view(p, end).stream().anyMatch(o -> o.id().equals("elytra") && o.lockReason().equals("vip")),
				"End: shown, locked below Platinum");
			StoreService.buyAt(p, end, "elytra", 1, false);
			helper.assertTrue(p.getInventory().countItem(Items.ELYTRA) == 0, "Gold is not enough");
			VipData.get(helper.getLevel().getServer()).player(p.getUUID()).tier = 3;
			StoreService.buyAt(p, end, "elytra", 1, false);
			helper.assertTrue(p.getInventory().countItem(Items.ELYTRA) == 1 && bal(p) == 100_000 - 5000, "bought at Platinum in the End");
			StoreService.buyAt(p, end, "elytra", 1, false);
			helper.assertTrue(p.getInventory().countItem(Items.ELYTRA) == 1, "1 every 7 days");
		});
		helper.succeed();
	}

	@GameTest
	public void merchantOutOfRangeRejected(GameTestHelper helper) {
		withPlayer(helper, 1000, p -> {
			net.minecraft.world.entity.Mob merchant = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(3, 1, 1));
			MerchantRole.open(p, merchant);
			StoreService.receive(p, StoreActionPayload.buy("bread", 1, false));
			helper.assertTrue(p.getInventory().countItem(Items.BREAD) == 1, "in range: bought");
			p.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 20.5)));
			CounterSessions.resetCooldown(p);
			StoreService.receive(p, StoreActionPayload.buy("bread", 1, false));
			helper.assertTrue(p.getInventory().countItem(Items.BREAD) == 1 && bal(p) == 998, "out of range: rejected");
			helper.assertTrue(CounterSessions.validate(p) == null, "no valid session out of range");
			p.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 1.5)));
			merchant.discard();
			helper.assertTrue(CounterSessions.validate(p) == null, "a gone merchant ends the session");
		});
		helper.succeed();
	}
}
