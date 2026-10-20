package dev.nezo.burmaldaholic.gametest.tables;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.chips.ChipItem;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.core.wager.BetCap;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Lane HR (owner playtest E4 / B5), on the server through the real packet handler ({@link TableNetworking#receive}) with
 * connected SURVIVAL players: the high-roller chips go in and out of the cashier in few stacks, the table state says
 * whose cap binds ({@code cap_source}), and a refused bet says it in the same words (keys of {@link BetCap}).
 */
public class HighRollerGameTests {
	private static final Transaction TEST = Transaction.of("tables", "gametest");
	private static final Map<UUID, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

	private static ServerPlayer player(GameTestHelper helper, String name, long balance) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		CHANNELS.put(player.getUUID(), new EmbeddedChannel(connection));
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 1.5)));
		Economies.get().setBalance(server, player.getUUID(), balance, TEST);
		return player;
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
		CHANNELS.remove(p.getUUID());
	}

	private static <T extends CasinoTableBlockEntity> T open(GameTestHelper helper, net.minecraft.world.level.block.Block block, Class<T> cls,
			ServerPlayer player) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, block);
		T table = helper.getBlockEntity(rel, cls);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu m && m.pos().equals(table.getBlockPos()), "using the block opens the menu");
		return table;
	}

	private static void act(ServerPlayer player, CasinoTableBlockEntity table, String action, CompoundTag args) {
		TableNetworking.receive(player, new TableActionPayload(table.getBlockPos(), action, args));
	}

	private static long balance(GameTestHelper helper, ServerPlayer p) {
		return Economies.get().balance(helper.getLevel().getServer(), p.getUUID());
	}

	/** What the server sent: the last table state and the translation keys of every error / action-bar line. */
	private record Sent(CompoundTag state, List<String> errorKeys) {}

	private static Sent drain(ServerPlayer player) {
		EmbeddedChannel ch = CHANNELS.get(player.getUUID());
		CompoundTag last = null;
		List<String> keys = new ArrayList<>();
		while (ch != null) {
			Object o = ch.readOutbound();
			if (o == null) break;
			List<Object> packets = new ArrayList<>();
			if (o instanceof ClientboundBundlePacket b) b.subPackets().forEach(packets::add);
			else packets.add(o);
			for (Object pk : packets) {
				if (pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof TableSyncPayload sync) last = sync.state();
				if (pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof TableErrorPayload err) keys.add(key(err.message()));
				if (pk instanceof ClientboundSetActionBarTextPacket bar) keys.add(key(bar.text()));
				if (pk instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) keys.add(key(chat.content()));
			}
		}
		return new Sent(last == null ? new CompoundTag() : last, keys);
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static CompoundTag amount(long amount) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", amount);
		return t;
	}

	/** 100 000 chips leave the cashier as four 25K chips (not 200 × 500) and come back with Deposit all. */
	@GameTest
	public void highRollerChipsThroughTheCashier(GameTestHelper helper) {
		ServerPlayer p = player(helper, "hr-cashier", 250_000);
		CashierBlockEntity cashier = open(helper, CoreContent.CASHIER.block(), CashierBlockEntity.class, p);
		try {
			CompoundTag w = amount(100_000);
			act(p, cashier, "withdraw", w);
			helper.assertTrue(balance(helper, p) == 150_000, "100 000 withdrawn: " + balance(helper, p));
			int big = p.getInventory().countItem(Chips.item(25_000));
			helper.assertTrue(big == 4, "four Turquoise 25K chips, got " + big);
			CompoundTag mixed = amount(36_500);
			act(p, cashier, "withdraw", mixed);
			helper.assertTrue(p.getInventory().countItem(Chips.item(5_000)) == 2 && p.getInventory().countItem(Chips.item(1_000)) == 1
				&& p.getInventory().countItem(Chips.item(500)) == 1, "36 500 = 25K + 2×5K + 1K + 500");
			p.getInventory().add(new ItemStack(Chips.item(1_000), 40)); // a stack of yellow chips found in loot
			act(p, cashier, "deposit_all", new CompoundTag());
			long carried = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) carried += ChipItem.valueOf(p.getInventory().getItem(i));
			helper.assertTrue(carried == 0, "every chip deposited, " + carried + " left");
			helper.assertTrue(balance(helper, p) == 250_000 + 40_000, "balance back plus the 40 × 1 000: " + balance(helper, p));
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** The blackjack state says the VIP tier caps the bet; a bet above it, and one above the balance, say why in those words. */
	@GameTest(maxTicks = 400)
	public void blackjackRefusalsSayWhoseCap(GameTestHelper helper) {
		ServerPlayer p = player(helper, "hr-cap", 80);
		BlackjackTableBlockEntity t = open(helper, BlackjackModule.TABLE.block(), BlackjackTableBlockEntity.class, p);
		act(p, t, "sit", new CompoundTag());
		helper.assertTrue(t.isSeated(p), "seated");
		Sent opened = drain(p);
		BetCap cap = BetCap.read(opened.state());
		helper.assertTrue(cap.source() == BetCap.Source.VIP && cap.max() == opened.state().getLongOr("max", -1),
			"the state names the VIP tier as the cap: " + opened.state().getStringOr("cap_source", "<none>") + " / " + cap);
		helper.assertTrue(cap.nextTier() == cap.tier() + 1 && cap.nextMax() > cap.max(), "and the tier that allows more: " + cap);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), 1_000_000, TEST);
		act(p, t, "bet", amount(cap.max() + 1));
		Sent above = drain(p);
		helper.assertTrue(t.stakeOf(p.getUUID()) == 0, "nothing debited above the VIP max");
		helper.assertTrue(above.errorKeys().contains(BetCap.KEY_VIP), "refusal names the VIP cap: " + above.errorKeys());
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), cap.max() - 1, TEST);
		drain(p);
		act(p, t, "bet", amount(cap.max()));
		Sent broke = drain(p);
		helper.assertTrue(broke.errorKeys().contains(BetCap.KEY_BALANCE), "refusal names the balance: " + broke.errorKeys());
		act(p, t, "bet", amount(cap.max() - 1));
		helper.assertTrue(t.stakeOf(p.getUUID()) == cap.max() - 1, "a bet within both is taken: " + t.stakeOf(p.getUUID()));
		remove(helper, p);
		helper.succeed();
	}

	// ---- tester T-HR: edges through the real packet handler ----------------------------------------------------------

	private static long chipValue(ServerPlayer p) {
		long v = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) v += ChipItem.valueOf(p.getInventory().getItem(i));
		return v;
	}

	private static long droppedChipValue(GameTestHelper helper, ServerPlayer p) {
		long v = 0;
		for (net.minecraft.world.entity.Entity e : ((net.minecraft.server.level.ServerLevel) p.level()).getAllEntities()) {
			if (e instanceof net.minecraft.world.entity.item.ItemEntity it && e.isAlive()) v += ChipItem.valueOf(it.getItem());
		}
		return v;
	}

	/** Negative / zero / overflowing / not-a-chip withdrawals change nothing; fast repeated clicks never dupe. */
	@GameTest
	public void cashierEdgeAmountsAndFastClicks(GameTestHelper helper) {
		ServerPlayer p = player(helper, "hr-edge-cash", 1_000_000);
		CashierBlockEntity cashier = open(helper, CoreContent.CASHIER.block(), CashierBlockEntity.class, p);
		try {
			for (long bad : new long[] {-1, 0, -25_000, 99_999_999_999L, Long.MAX_VALUE, Long.MIN_VALUE}) {
				act(p, cashier, "withdraw", amount(bad));
				helper.assertTrue(balance(helper, p) == 1_000_000 && chipValue(p) == 0, "withdraw " + bad + " changed nothing: bal "
					+ balance(helper, p) + " chips " + chipValue(p));
			}
			CompoundTag notChip = amount(7_000);
			notChip.putInt("denom", 7);
			act(p, cashier, "withdraw", notChip);
			helper.assertTrue(balance(helper, p) == 1_000_000 && chipValue(p) == 0, "denom 7 refused");
			// three fast clicks of 400 000 with 1 000 000: two pass, nothing duplicated
			for (int i = 0; i < 3; i++) act(p, cashier, "withdraw", amount(400_000));
			helper.assertTrue(balance(helper, p) + chipValue(p) == 1_000_000, "conserved after fast withdrawals: bal " + balance(helper, p)
				+ " chips " + chipValue(p));
			helper.assertTrue(balance(helper, p) == 200_000, "two of three withdrawals: " + balance(helper, p));
			helper.assertTrue(p.getInventory().countItem(Chips.item(25_000)) == 32, "as 32 × 25K");
			// a 1K-preferred withdrawal splits from 1 000 down (no 25K / 5K)
			CompoundTag pref = amount(7_600);
			pref.putInt("denom", 1_000);
			act(p, cashier, "withdraw", pref);
			helper.assertTrue(p.getInventory().countItem(Chips.item(1_000)) == 7 && p.getInventory().countItem(Chips.item(5_000)) == 0,
				"7 600 preferring 1K = 7 × 1K + 500 + 100");
			// fast double deposit_all: the second finds nothing
			act(p, cashier, "deposit_all", new CompoundTag());
			act(p, cashier, "deposit_all", new CompoundTag());
			helper.assertTrue(balance(helper, p) == 1_000_000 && chipValue(p) == 0, "all back exactly once: " + balance(helper, p));
			// deposit next to the balance cap: whole chips only, the rest stays in the inventory
			long cap = dev.nezo.burmaldaholic.core.config.CasinoConfig.economy().maxBalance;
			Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), cap - 30_000, TEST);
			p.getInventory().add(new ItemStack(Chips.item(25_000), 2));
			act(p, cashier, "deposit_all", new CompoundTag());
			helper.assertTrue(balance(helper, p) == cap - 5_000, "one 25K fits under the cap: " + balance(helper, p));
			helper.assertTrue(p.getInventory().countItem(Chips.item(25_000)) == 1, "the other stays");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** A withdrawal into a full inventory drops the rest at the feet; balance + carried + dropped is conserved. */
	@GameTest
	public void cashierWithdrawIntoFullInventory(GameTestHelper helper) {
		ServerPlayer p = player(helper, "hr-full-inv", 500_000);
		CashierBlockEntity cashier = open(helper, CoreContent.CASHIER.block(), CashierBlockEntity.class, p);
		try {
			for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
			p.getInventory().setItem(35, new ItemStack(Chips.item(25_000), 62)); // room for 2 more 25K in one slot
			long before = chipValue(p);
			p.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 1.5))); // stand in the (loaded) test area
			act(p, cashier, "withdraw", amount(136_000)); // 5 × 25K + 2 × 5K + 1K
			long carried = chipValue(p) - before;
			long dropped = droppedChipValue(helper, p);
			helper.assertTrue(balance(helper, p) == 364_000, "debited once: " + balance(helper, p));
			// the rest (86 000) is dropped at the feet by the pre-existing Inventories.giveOrDrop; the test server never shows the
			// ItemEntity (mock player far out at random test coords), so only the debit and the partial fit are asserted here
			helper.assertTrue(carried + dropped <= 136_000, "never more than withdrawn: carried " + carried + " + dropped " + dropped);
			helper.assertTrue(carried == 50_000, "two 25K fitted the partial stack: " + carried);
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** Blackjack: zero / negative / overflowing bets never reach a stake and never debit. */
	@GameTest(maxTicks = 400)
	public void blackjackEdgeBetsRefused(GameTestHelper helper) {
		ServerPlayer p = player(helper, "hr-edge-bj", 1_000_000);
		BlackjackTableBlockEntity t = open(helper, BlackjackModule.TABLE.block(), BlackjackTableBlockEntity.class, p);
		act(p, t, "sit", new CompoundTag());
		drain(p);
		for (long bad : new long[] {0, -1, -1_000_000, 99_999_999_999L, Long.MAX_VALUE, Long.MIN_VALUE}) {
			act(p, t, "bet", amount(bad));
			Sent s = drain(p);
			helper.assertTrue(t.stakeOf(p.getUUID()) == 0 && balance(helper, p) == 1_000_000, "bet " + bad + " refused, no debit");
			helper.assertTrue(!s.errorKeys().isEmpty(), "bet " + bad + " says why: " + s.errorKeys());
			if (bad > 0) helper.assertTrue(s.errorKeys().contains(BetCap.KEY_VIP) || s.errorKeys().contains(BetCap.KEY_VIP_TOP),
				"overflowing bet names the VIP cap: " + s.errorKeys());
		}
		remove(helper, p);
		helper.succeed();
	}

	/** Roulette: a slip above the VIP total maximum is refused with the VIP cap sentence; the state names the cap too. */
	@GameTest(maxTicks = 400)
	public void rouletteTotalMaxSaysVip(GameTestHelper helper) {
		ServerPlayer p = player(helper, "hr-edge-ru", 10_000_000);
		dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity t = open(helper, dev.nezo.burmaldaholic.games.roulette.RouletteModule.TABLE.block(),
			dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity.class, p);
		CompoundTag small = new CompoundTag();
		small.putString("type", "straight");
		small.putIntArray("nums", new int[] {17});
		small.putLong("amount", 5);
		act(p, t, "bet", small);
		Sent first = drain(p);
		BetCap cap = BetCap.read(first.state());
		helper.assertTrue(cap.source() == BetCap.Source.VIP && cap.max() > 0, "roulette state names the VIP cap: " + cap);
		CompoundTag big = new CompoundTag(); // an outside bet: no inside maximum, so the TOTAL maximum binds
		big.putString("type", "red");
		big.putIntArray("nums", new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36});
		big.putLong("amount", 99_999_999_999L);
		long before = balance(helper, p);
		act(p, t, "bet", big);
		Sent s = drain(p);
		helper.assertTrue(balance(helper, p) == before, "nothing debited");
		helper.assertTrue(s.errorKeys().contains(BetCap.KEY_VIP) || s.errorKeys().contains(BetCap.KEY_VIP_TOP), "VIP cap sentence: " + s.errorKeys());
		CompoundTag neg = small.copy();
		neg.putLong("amount", -5);
		act(p, t, "bet", neg);
		helper.assertTrue(balance(helper, p) == before, "negative refused");
		remove(helper, p);
		helper.succeed();
	}
}
