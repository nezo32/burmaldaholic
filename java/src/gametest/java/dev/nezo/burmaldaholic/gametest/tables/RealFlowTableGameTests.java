package dev.nezo.burmaldaholic.gametest.tables;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
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
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * v0.1.2 hotfix, real player flow on the server: a SURVIVAL player (connected like a real one) opens a fresh table by
 * USING the block ({@code GameTestHelper.useBlock}: the block's own use handler opens the menu) and every action goes
 * through the real {@code table_action} receiver ({@link TableNetworking#receive}: menu open, in range) — never the
 * game's {@code onAction} directly. Each table plays a full cycle solo and with bots: sit → bet → deal / spin / roll →
 * resolve → payout → the next round accepts a bet again.
 */
public class RealFlowTableGameTests {
	private static final Transaction TEST = Transaction.of("tables", "gametest");
	private static final long START = 5000;
	/** The test players' connections: what the server SENT them (the table screen only knows the synced state). */
	private static final Map<UUID, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
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
		Economies.get().setBalance(server, player.getUUID(), START, TEST);
		return player;
	}

	/** Places the table at (1, 1, 1) and opens it by using the block; fails the test when no table menu opened. */
	private static <T extends CasinoTableBlockEntity> T open(GameTestHelper helper, TableType<?> type, Class<T> cls, ServerPlayer player) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, type.block());
		T table = helper.getBlockEntity(rel, cls);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu m && m.pos().equals(table.getBlockPos()),
			"using the block opens the table menu (got " + player.containerMenu + ")");
		assertScreenInSync(helper, player, table, "opened");
		return table;
	}

	/** The newest table state the server sent this player for {@code table} (drains the connection), or null. */
	private static @Nullable CompoundTag lastSync(ServerPlayer player, CasinoTableBlockEntity table) {
		EmbeddedChannel ch = CHANNELS.get(player.getUUID());
		CompoundTag last = null;
		while (ch != null) {
			Object o = ch.readOutbound();
			if (o == null) break;
			List<Object> packets = new ArrayList<>();
			if (o instanceof ClientboundBundlePacket b) b.subPackets().forEach(packets::add);
			else packets.add(o);
			for (Object pk : packets) {
				if (pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof TableSyncPayload sync && sync.pos().equals(table.getBlockPos())) {
					last = sync.state();
				}
			}
		}
		return last;
	}

	/** What the player's screen shows (the last sync) agrees with the server on the phase and the player's seat. */
	private static void assertScreenInSync(GameTestHelper helper, ServerPlayer player, CasinoTableBlockEntity table, String when) {
		CompoundTag shown = lastSync(player, table);
		CompoundTag now = table.writeClientState(player);
		helper.assertTrue(shown != null, when + ": the server sent the screen a state");
		helper.assertTrue(shown.getStringOr("phase", "").equals(now.getStringOr("phase", "")) && shown.getIntOr("seat", -1) == now.getIntOr("seat", -1),
			when + ": the screen shows phase " + shown.getStringOr("phase", "") + " / seat " + shown.getIntOr("seat", -1) + ", the table is in "
				+ now.getStringOr("phase", "") + " / seat " + now.getIntOr("seat", -1));
	}

	private static void act(ServerPlayer player, CasinoTableBlockEntity table, String action, CompoundTag args) {
		TableNetworking.receive(player, new TableActionPayload(table.getBlockPos(), action, args));
	}

	private static void act(ServerPlayer player, CasinoTableBlockEntity table, String action) {
		act(player, table, action, new CompoundTag());
	}

	private static CompoundTag tag(String key, Object value) {
		CompoundTag t = new CompoundTag();
		if (value instanceof Long l) t.putLong(key, l);
		else if (value instanceof Integer i) t.putInt(key, i);
		else if (value instanceof Boolean b) t.putBoolean(key, b);
		else t.putString(key, String.valueOf(value));
		return t;
	}

	/** The phase the table's screen shows (roulette and craps run their own round model, not core's phase). */
	private static String phase(CasinoTableBlockEntity t, ServerPlayer p) {
		return t.writeClientState(p).getStringOr("phase", "");
	}

	private static long balance(GameTestHelper helper, ServerPlayer p) {
		return Economies.get().balance(helper.getLevel().getServer(), p.getUUID());
	}

	private static void bots(ServerPlayer player, CasinoTableBlockEntity table, boolean on) {
		if (!on) return;
		BotSettings two = new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, false, false, BotSpeed.FAST);
		if (table instanceof BlackjackTableBlockEntity t) t.bots().table().requestChange(player, two, false);
		if (table instanceof RouletteTableBlockEntity t) t.bots().table().requestChange(player, two, false);
		if (table instanceof CrapsTableBlockEntity t) t.bots().table().requestChange(player, two, false);
		if (table instanceof BaccaratTableBlockEntity t) t.bots().setDefaults(two);
	}

	private static void finish(GameTestHelper helper, ServerPlayer player) {
		long b = balance(helper, player);
		helper.assertTrue(b > START - 200 && b < START + 200, "the payouts landed on the balance (" + START + " → " + b + ")");
		player.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(player);
		CHANNELS.remove(player.getUUID());
		helper.succeed();
	}

	// ---- blackjack ------------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 2400)
	public void blackjackRealFlowSolo(GameTestHelper helper) {
		blackjack(helper, false);
	}

	@GameTest(maxTicks = 3600)
	public void blackjackRealFlowWithBots(GameTestHelper helper) {
		blackjack(helper, true);
	}

	private static void blackjack(GameTestHelper helper, boolean withBots) {
		ServerPlayer p = survivalPlayer(helper, "bj-real");
		BlackjackTableBlockEntity t = open(helper, BlackjackModule.TABLE, BlackjackTableBlockEntity.class, p);
		act(p, t, "sit");
		helper.assertTrue(t.isSeated(p), "Sit through the packet seats the player");
		assertScreenInSync(helper, p, t, "after Sit");
		bots(p, t, withBots);
		int[] rounds = {0};
		boolean[] staked = {false};
		helper.onEachTick(() -> {
			if (rounds[0] >= 2) return;
			if ("betting".equals(phase(t, p)) && t.stakeOf(p.getUUID()) == 0) {
				if (staked[0]) {
					staked[0] = false;
					rounds[0]++;
					if (rounds[0] >= 2) return;
				}
				act(p, t, "bet", tag("amount", 10L));
				if (t.stakeOf(p.getUUID()) == 10) staked[0] = true;
			} else if (t.stakeOf(p.getUUID()) > 0) {
				staked[0] = true;
				act(p, t, "insurance", tag("amount", 0L));
				act(p, t, "even_money", tag("take", false));
				act(p, t, "stand");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(rounds[0] >= 2, "two full blackjack rounds bet → deal → settle (phase " + phase(t, p) + ", stake "
				+ t.stakeOf(p.getUUID()) + ")");
			finish(helper, p);
		});
	}

	// ---- roulette -------------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 3600)
	public void rouletteRealFlowSolo(GameTestHelper helper) {
		roulette(helper, false);
	}

	@GameTest(maxTicks = 4800)
	public void rouletteRealFlowWithBots(GameTestHelper helper) {
		roulette(helper, true);
	}

	private static void roulette(GameTestHelper helper, boolean withBots) {
		ServerPlayer p = survivalPlayer(helper, "ru-real");
		RouletteTableBlockEntity t = open(helper, RouletteModule.TABLE, RouletteTableBlockEntity.class, p);
		bots(p, t, false);
		int[] spins = {0};
		boolean[] staked = {false};
		boolean[] botsAsked = {false};
		helper.onEachTick(() -> {
			if (spins[0] >= 2) return;
			if ("betting".equals(phase(t, p)) && t.stakeOf(p.getUUID()) == 0) {
				if (staked[0]) {
					staked[0] = false;
					spins[0]++;
					if (spins[0] >= 2) return;
				}
				CompoundTag bet = tag("type", "red");
				bet.putIntArray("nums", new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36});
				bet.putLong("amount", 5);
				act(p, t, "bet", bet);
				if (t.stakeOf(p.getUUID()) == 5) {
					staked[0] = true;
					if (withBots && !botsAsked[0]) {
						botsAsked[0] = true;
						bots(p, t, true);
					}
					act(p, t, "spin");
				}
			} else if (t.stakeOf(p.getUUID()) > 0) {
				staked[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(spins[0] >= 2, "two full roulette spins bet → spin → settle (phase " + phase(t, p) + ", stake "
				+ t.stakeOf(p.getUUID()) + ")");
			finish(helper, p);
		});
	}

	// ---- craps ----------------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 6000)
	public void crapsRealFlowSolo(GameTestHelper helper) {
		craps(helper, false);
	}

	@GameTest(maxTicks = 7200)
	public void crapsRealFlowWithBots(GameTestHelper helper) {
		craps(helper, true);
	}

	private static void craps(GameTestHelper helper, boolean withBots) {
		ServerPlayer p = survivalPlayer(helper, "cr-real");
		CrapsTableBlockEntity t = open(helper, CrapsModule.TABLE, CrapsTableBlockEntity.class, p);
		int[] decisions = {0};
		boolean[] staked = {false};
		boolean[] botsAsked = {false};
		helper.onEachTick(() -> {
			if (decisions[0] >= 2) return;
			long stake = t.stakeOf(p.getUUID());
			if (stake == 0) {
				if (staked[0]) {
					staked[0] = false;
					decisions[0]++;
					if (decisions[0] >= 2) return;
				}
				act(p, t, "bet", tag("kind", "pass").merge(tag("amount", 5L)));
				if (t.stakeOf(p.getUUID()) > 0) {
					staked[0] = true;
					if (withBots && !botsAsked[0]) {
						botsAsked[0] = true;
						bots(p, t, true);
					}
				}
			} else {
				staked[0] = true;
				act(p, t, "roll"); // only the shooter's roll counts; refused otherwise (the bots / timer roll)
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(decisions[0] >= 2, "two Pass Line decisions bet → roll → settle (phase " + phase(t, p) + ", stake "
				+ t.stakeOf(p.getUUID()) + ")");
			finish(helper, p);
		});
	}

	// ---- baccarat -------------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 3600)
	public void baccaratRealFlowSolo(GameTestHelper helper) {
		baccarat(helper, false);
	}

	@GameTest(maxTicks = 4800)
	public void baccaratRealFlowWithBots(GameTestHelper helper) {
		baccarat(helper, true);
	}

	private static void baccarat(GameTestHelper helper, boolean withBots) {
		ServerPlayer p = survivalPlayer(helper, "bac-real");
		BaccaratTableBlockEntity t = open(helper, BaccaratModule.TABLE, BaccaratTableBlockEntity.class, p);
		bots(p, t, withBots);
		act(p, t, "sit");
		helper.assertTrue(t.isSeated(p), "Sit through the packet seats the player");
		assertScreenInSync(helper, p, t, "after Sit");
		int[] coups = {0};
		boolean[] staked = {false};
		helper.onEachTick(() -> {
			if (coups[0] >= 2) return;
			if (t.stakeOf(p.getUUID()) == 0) {
				if (staked[0]) {
					staked[0] = false;
					coups[0]++;
					if (coups[0] >= 2) return;
				}
				if ("betting".equals(phase(t, p)) || "idle".equals(phase(t, p))) {
					CompoundTag bet = tag("box", "player");
					bet.putLong("amount", 25);
					act(p, t, "bet", bet);
					if (t.stakeOf(p.getUUID()) > 0) {
						staked[0] = true;
						act(p, t, "ready");
					}
				}
			} else {
				staked[0] = true;
				if ("betting".equals(phase(t, p))) act(p, t, "ready");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(coups[0] >= 2, "two full baccarat coups bet → deal → settle (phase " + phase(t, p) + ", stake "
				+ t.stakeOf(p.getUUID()) + ")");
			finish(helper, p);
		});
	}

	// ---- kit K2: one state per action, ack epochs, ack pruning (reviews R-KW B4, M8, R-CDE m6) -------------------------

	/** Every table state the server sent {@code player} for {@code table} since the last drain. */
	private static List<CompoundTag> syncs(ServerPlayer player, CasinoTableBlockEntity table) {
		EmbeddedChannel ch = CHANNELS.get(player.getUUID());
		List<CompoundTag> out = new ArrayList<>();
		while (ch != null) {
			Object o = ch.readOutbound();
			if (o == null) break;
			List<Object> packets = new ArrayList<>();
			if (o instanceof ClientboundBundlePacket b) b.subPackets().forEach(packets::add);
			else packets.add(o);
			for (Object pk : packets) {
				if (pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof TableSyncPayload sync && sync.pos().equals(table.getBlockPos())) {
					out.add(sync.state());
				}
			}
		}
		return out;
	}

	/** {@code action} answers its sender with exactly one state whose ack is one higher; returns how many the other viewer got. */
	private static int oneState(GameTestHelper helper, ServerPlayer p, @Nullable ServerPlayer other, CasinoTableBlockEntity t, String action,
			CompoundTag args) {
		syncs(p, t);
		if (other != null) syncs(other, t);
		long before = t.writeClientState(p).getLongOr("ack", -1);
		act(p, t, action, args);
		List<CompoundTag> got = syncs(p, t);
		helper.assertTrue(got.size() == 1, action + " at " + t.gameId() + ": the sender got " + got.size() + " states, want exactly 1");
		helper.assertTrue(got.get(0).getLongOr("ack", -1) == before + 1, action + ": ack " + before + " → " + got.get(0).getLongOr("ack", -1));
		helper.assertTrue(got.get(0).getIntOr("ack_epoch", 0) != 0, action + ": the state carries a non-zero ack_epoch");
		return other == null ? 0 : syncs(other, t).size();
	}

	@GameTest(maxTicks = 200)
	public void one_state_per_action(GameTestHelper helper) {
		ServerPlayer p = survivalPlayer(helper, "k2-one");
		ServerPlayer q = survivalPlayer(helper, "k2-watch");
		BlackjackTableBlockEntity bj = open(helper, BlackjackModule.TABLE, BlackjackTableBlockEntity.class, p);
		helper.useBlock(new BlockPos(1, 1, 1), q);
		int seen = oneState(helper, p, q, bj, "sit", new CompoundTag());
		helper.assertTrue(seen >= 1, "the other viewer is synced after a sit (got " + seen + ")");
		oneState(helper, p, q, bj, "bet", tag("amount", 10L));
		oneState(helper, p, q, bj, "resync", new CompoundTag());
		oneState(helper, p, q, bj, "leave", new CompoundTag());
		// baccarat sit (its sit opens betting after core's sync) and a roulette bet, on fresh tables
		helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.AIR);
		BaccaratTableBlockEntity bac = open(helper, BaccaratModule.TABLE, BaccaratTableBlockEntity.class, p);
		oneState(helper, p, null, bac, "sit", new CompoundTag());
		helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.AIR);
		RouletteTableBlockEntity ru = open(helper, RouletteModule.TABLE, RouletteTableBlockEntity.class, p);
		CompoundTag bet = tag("type", "red");
		bet.putIntArray("nums", new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36});
		bet.putLong("amount", 5);
		oneState(helper, p, null, ru, "bet", bet);
		oneState(helper, p, null, ru, "resync", new CompoundTag());
		quit(helper, q);
		quit(helper, p);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void ack_epoch_changes_on_reload(GameTestHelper helper) {
		ServerPlayer p = survivalPlayer(helper, "k2-epoch");
		BlackjackTableBlockEntity t = open(helper, BlackjackModule.TABLE, BlackjackTableBlockEntity.class, p);
		act(p, t, "resync");
		act(p, t, "resync");
		CompoundTag before = t.writeClientState(p);
		helper.assertTrue(before.getLongOr("ack", -1) == 2, "two actions counted (ack " + before.getLongOr("ack", -1) + ")");
		helper.assertTrue(before.getIntOr("ack_epoch", 0) == t.writeClientState(p).getIntOr("ack_epoch", 0), "the epoch is stable");
		// the table goes and comes back (broken and placed again; a chunk reload is the same: a new block entity)
		helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.AIR);
		BlackjackTableBlockEntity again = open(helper, BlackjackModule.TABLE, BlackjackTableBlockEntity.class, p);
		CompoundTag after = again.writeClientState(p);
		helper.assertTrue(after.getIntOr("ack_epoch", 0) != 0 && after.getIntOr("ack_epoch", 0) != before.getIntOr("ack_epoch", 0),
			"a new table has a new ack epoch (" + before.getIntOr("ack_epoch", 0) + " → " + after.getIntOr("ack_epoch", 0) + ")");
		helper.assertTrue(after.getLongOr("ack", -1) == 0, "and a new counter");
		quit(helper, p);
		helper.succeed();
	}

	@GameTest(maxTicks = 900)
	public void acks_pruned_after_leave(GameTestHelper helper) {
		ServerPlayer seated = survivalPlayer(helper, "k2-seated");
		ServerPlayer watcher = survivalPlayer(helper, "k2-watcher");
		BlackjackTableBlockEntity t = open(helper, BlackjackModule.TABLE, BlackjackTableBlockEntity.class, seated);
		helper.useBlock(new BlockPos(1, 1, 1), watcher);
		act(seated, t, "sit");
		act(watcher, t, "resync");
		helper.assertTrue(t.ackEntries() == 2, "an entry per player who acted (" + t.ackEntries() + ")");
		// the seated player walks away from the screen, then leaves the seat: the entry goes with the seat
		seated.closeContainer();
		t.leave(seated.getUUID(), CasinoTableBlockEntity.LeaveReason.LEFT);
		helper.assertTrue(t.ackEntries() == 1, "the leaver's entry is pruned on leave (" + t.ackEntries() + ")");
		// a watcher closes the screen: the sweep drops the entry
		watcher.closeContainer();
		helper.succeedWhen(() -> {
			helper.assertTrue(t.ackEntries() == 0, "the sweep prunes a watcher who closed the screen (" + t.ackEntries() + ")");
			quit(helper, seated);
			quit(helper, watcher);
		});
	}

	private static void quit(GameTestHelper helper, ServerPlayer p) {
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
		CHANNELS.remove(p.getUUID());
	}
}
