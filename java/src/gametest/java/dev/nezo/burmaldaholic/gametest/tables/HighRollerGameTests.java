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
}
