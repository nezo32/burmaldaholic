package dev.nezo.burmaldaholic.gametest.tables;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.core.table.TableType;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;

/**
 * v0.1.2 hotfix lane A10: what real players do differently from the solo tests — TWO HUMANS at one house table (friends
 * on a server), a starting balance of 50 chips (economy.startingBalance) and the screen's default chips (roulette and
 * blackjack 25, craps 5). Every player connects like a real one, opens the table by USING the block and acts only
 * through the {@code table_action} receiver ({@link TableNetworking#receive}). Asserts the bets land and every refusal
 * reached the player's screen as an error line (never a silent no).
 */
public class RealFlowMultiplayerGameTests {
	private static final Transaction TEST = Transaction.of("tables", "gametest");
	/** economy.startingBalance: a fresh Survival player. */
	private static final long FRESH = 50;
	private static final Map<UUID, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

	private static ServerPlayer player(GameTestHelper helper, String name, double x, double z, long balance) {
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
		player.snapTo(helper.absoluteVec(new Vec3(x, 1, z)));
		Economies.get().setBalance(server, player.getUUID(), balance, TEST);
		dev.nezo.burmaldaholic.core.mode.CasinoMode.set(server, true); // a filtered run can start before GameTestCasinoMode
		return player;
	}

	private static <T extends CasinoTableBlockEntity> T place(GameTestHelper helper, TableType<?> type, Class<T> cls) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, type.block());
		return helper.getBlockEntity(rel, cls);
	}

	private static void use(GameTestHelper helper, CasinoTableBlockEntity table, ServerPlayer player) {
		BlockPos rel = null;
		for (BlockPos c : BlockPos.betweenClosed(0, 0, 0, 7, 3, 7)) {
			if (helper.absolutePos(c).equals(table.getBlockPos())) {
				rel = c.immutable();
			}
		}
		helper.assertTrue(rel != null, "table inside the test area");
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu m && m.pos().equals(table.getBlockPos()),
			player.getName().getString() + ": using the block opens the table menu (got " + player.containerMenu + ")");
	}

	/** Error lines the server sent to the player's table screen since the last call. */
	private static List<String> errors(ServerPlayer player) {
		EmbeddedChannel ch = CHANNELS.get(player.getUUID());
		List<String> out = new ArrayList<>();
		if (ch != null) {
			ch.runPendingTasks(); // packets sent off the channel's event loop are queued there
			ch.flush();
		}
		while (ch != null) {
			Object o = ch.readOutbound();
			if (o == null) break;
			List<Object> packets = new ArrayList<>();
			if (o instanceof ClientboundBundlePacket b) b.subPackets().forEach(packets::add);
			else packets.add(o);
			for (Object pk : packets) {
				if (pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof TableErrorPayload e) {
					out.add(key(e.message()));
				} else if (pk instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat && chat.overlay()) {
					// sendError's action-bar copy (a test connection never negotiated the table_error channel)
					String k = key(chat.content());
					if (!out.contains(k)) out.add(k);
				}
			}
		}
		return out;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static void act(ServerPlayer player, CasinoTableBlockEntity table, String action, CompoundTag args) {
		TableNetworking.receive(player, new TableActionPayload(table.getBlockPos(), action, args));
	}

	private static long balance(GameTestHelper helper, ServerPlayer p) {
		return Economies.get().balance(helper.getLevel().getServer(), p.getUUID());
	}

	private static String phase(CasinoTableBlockEntity t, ServerPlayer p) {
		return t.writeClientState(p).getStringOr("phase", "");
	}

	private static void finish(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			p.closeContainer();
			helper.getLevel().getServer().getPlayerList().remove(p);
			CHANNELS.remove(p.getUUID());
		}
		helper.succeed();
	}

	private static CompoundTag redBet(long amount, boolean red) {
		CompoundTag bet = new CompoundTag();
		bet.putString("type", red ? "red" : "black");
		bet.putIntArray("nums", red ? new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
			: new int[] {2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35});
		bet.putLong("amount", amount);
		return bet;
	}

	// ---- roulette: two friends share the wheel --------------------------------------------------------------------

	/**
	 * Alice bets 25 on red and presses Ready; Bob opens the same table, bets 25 on black (the screen's default chip) and
	 * presses Ready; both slips are spun and settled together; then both bet again on the next spin.
	 */
	@GameTest(maxTicks = 3600)
	public void rouletteTwoHumansShareTheSpin(GameTestHelper helper) {
		RouletteTableBlockEntity t = place(helper, RouletteModule.TABLE, RouletteTableBlockEntity.class);
		ServerPlayer a = player(helper, "ru-alice", 2.5, 1.5, FRESH);
		ServerPlayer b = player(helper, "ru-bob", 0.5, 2.5, FRESH);
		use(helper, t, a);
		use(helper, t, b);
		int[] spins = {0};
		boolean[] staked = {false};
		List<String> trouble = new ArrayList<>();
		helper.onEachTick(() -> {
			if (spins[0] >= 2) return;
			boolean betting = "betting".equals(phase(t, a));
			if (betting && t.stakeOf(a.getUUID()) == 0 && t.stakeOf(b.getUUID()) == 0) {
				if (staked[0]) {
					staked[0] = false;
					spins[0]++;
					if (spins[0] >= 2) return;
				}
				act(a, t, "bet", redBet(25, true));
				act(a, t, "spin", new CompoundTag());
				act(b, t, "bet", redBet(25, false));
				act(b, t, "spin", new CompoundTag());
				List<String> ea = errors(a);
				List<String> eb = errors(b);
				if (t.stakeOf(a.getUUID()) != 25 || t.stakeOf(b.getUUID()) != 25) {
					trouble.add("spin " + spins[0] + ": alice " + t.stakeOf(a.getUUID()) + " " + ea + ", bob " + t.stakeOf(b.getUUID()) + " " + eb);
				} else {
					staked[0] = true;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(trouble.isEmpty(), "both friends' bets land: " + trouble);
			helper.assertTrue(spins[0] >= 2, "two shared spins settled (phase " + phase(t, a) + ", stakes " + t.stakeOf(a.getUUID()) + "/"
				+ t.stakeOf(b.getUUID()) + ")");
			finish(helper, a, b);
		});
	}

	/** Bob opens the table while Alice's spin is already in the air: told "no more bets", then bets on the next spin. */
	@GameTest(maxTicks = 3600)
	public void rouletteLateJoinerBetsNextSpin(GameTestHelper helper) {
		RouletteTableBlockEntity t = place(helper, RouletteModule.TABLE, RouletteTableBlockEntity.class);
		ServerPlayer a = player(helper, "ru-alice2", 2.5, 1.5, FRESH);
		ServerPlayer b = player(helper, "ru-bob2", 0.5, 2.5, FRESH);
		use(helper, t, a);
		act(a, t, "bet", redBet(25, true));
		act(a, t, "spin", new CompoundTag());
		helper.assertTrue(t.stakeOf(a.getUUID()) == 25, "alice's bet lands: " + errors(a));
		int[] step = {0};
		List<String> trouble = new ArrayList<>();
		helper.onEachTick(() -> {
			String ph = phase(t, a);
			if (step[0] == 0 && "spin".equals(ph)) {
				use(helper, t, b);
				errors(b);
				act(b, t, "bet", redBet(25, false));
				List<String> eb = errors(b);
				if (t.stakeOf(b.getUUID()) != 0 || !eb.contains("gui.burmaldaholic.roulette.no_more_bets")) {
					trouble.add("mid-spin bet: stake " + t.stakeOf(b.getUUID()) + ", errors " + eb);
				}
				step[0] = 1;
			} else if (step[0] == 1 && "betting".equals(ph) && t.stakeOf(a.getUUID()) == 0) {
				act(b, t, "bet", redBet(25, false));
				if (t.stakeOf(b.getUUID()) != 25) {
					trouble.add("next spin: bob's bet refused " + errors(b));
				}
				step[0] = 2;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] == 2, "bob joined mid-spin and bet on the next one (step " + step[0] + ", phase " + phase(t, a) + ")");
			helper.assertTrue(trouble.isEmpty(), String.valueOf(trouble));
			finish(helper, a, b);
		});
	}

	// ---- craps: two friends, one shooter --------------------------------------------------------------------------

	/**
	 * Alice and Bob both put 5 on the Pass Line at the come-out; Alice shoots until the line resolves. A point is set
	 * while Bob watches: then Bob's Pass Line click is refused WITH the reason and his Field bet goes through.
	 */
	@GameTest(maxTicks = 7200)
	public void crapsTwoHumansPassLineAndPointOn(GameTestHelper helper) {
		CrapsTableBlockEntity t = place(helper, CrapsModule.TABLE, CrapsTableBlockEntity.class);
		ServerPlayer a = player(helper, "cr-alice", 2.5, 1.5, FRESH);
		ServerPlayer b = player(helper, "cr-bob", 0.5, 2.5, FRESH);
		use(helper, t, a);
		use(helper, t, b);
		CompoundTag pass = new CompoundTag();
		pass.putString("kind", "pass");
		pass.putLong("amount", 5);
		act(a, t, "bet", pass);
		act(b, t, "bet", pass);
		helper.assertTrue(t.stakeOf(a.getUUID()) == 5, "alice's Pass Line lands: " + errors(a));
		helper.assertTrue(t.stakeOf(b.getUUID()) == 5, "bob's Pass Line lands: " + errors(b));
		boolean[] pointSeen = {false};
		boolean[] checked = {false};
		List<String> trouble = new ArrayList<>();
		helper.onEachTick(() -> {
			if (checked[0]) return;
			if (t.table().point() != 0 && !pointSeen[0]) {
				pointSeen[0] = true;
				errors(b);
				CompoundTag again = pass.copy();
				act(b, t, "bet", again);
				List<String> eb = errors(b);
				if (!eb.contains("gui.burmaldaholic.craps.line_only_come_out")) {
					trouble.add("Pass Line with a point on: errors " + eb);
				}
				CompoundTag field = new CompoundTag();
				field.putString("kind", "field");
				field.putLong("amount", 5);
				long before = t.stakeOf(b.getUUID());
				act(b, t, "bet", field);
				if (t.stakeOf(b.getUUID()) != before + 5) {
					trouble.add("Field with a point on refused: " + errors(b));
				}
				checked[0] = true;
				return;
			}
			if (t.stakeOf(a.getUUID()) > 0) {
				act(a, t, "roll", new CompoundTag());
			} else if (!pointSeen[0]) {
				// the line resolved on the come-out (7 / 11 / craps): both bet again for the next come-out
				act(a, t, "bet", pass);
				if (t.stakeOf(b.getUUID()) == 0) act(b, t, "bet", pass);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "a point was set and bob bet into it (point " + t.table().point() + ")");
			helper.assertTrue(trouble.isEmpty(), String.valueOf(trouble));
			finish(helper, a, b);
		});
	}

	// ---- blackjack: two friends sit and bet -----------------------------------------------------------------------

	/** Alice and Bob sit, each bets 25 (the screen's default chip) and stands; the next round both bet again. */
	@GameTest(maxTicks = 3600)
	public void blackjackTwoHumansBetAndPlay(GameTestHelper helper) {
		BlackjackTableBlockEntity t = place(helper, BlackjackModule.TABLE, BlackjackTableBlockEntity.class);
		ServerPlayer a = player(helper, "bj-alice", 2.5, 1.5, FRESH * 4);
		ServerPlayer b = player(helper, "bj-bob", 0.5, 2.5, FRESH * 4);
		use(helper, t, a);
		use(helper, t, b);
		act(a, t, "sit", new CompoundTag());
		act(b, t, "sit", new CompoundTag());
		helper.assertTrue(t.isSeated(a) && t.isSeated(b), "both friends sit (" + errors(a) + " / " + errors(b) + ")");
		int[] rounds = {0};
		boolean[] inRound = {false};
		List<String> trouble = new ArrayList<>();
		CompoundTag bet = new CompoundTag();
		bet.putLong("amount", 25);
		helper.onEachTick(() -> {
			if (rounds[0] >= 2) return;
			boolean betting = "betting".equals(phase(t, a));
			if (betting && t.stakeOf(a.getUUID()) == 0 && t.stakeOf(b.getUUID()) == 0) {
				if (inRound[0]) {
					inRound[0] = false;
					rounds[0]++;
					if (rounds[0] >= 2) return;
				}
				act(a, t, "bet", bet);
				act(b, t, "bet", bet);
				boolean aIn = t.stakeOf(a.getUUID()) > 0 || !"betting".equals(phase(t, a));
				boolean bIn = t.stakeOf(b.getUUID()) > 0 || !"betting".equals(phase(t, a));
				if (!aIn || !bIn) {
					trouble.add("round " + rounds[0] + ": alice " + errors(a) + ", bob " + errors(b));
				}
				inRound[0] = true;
			} else if (!betting) {
				inRound[0] = true;
				for (ServerPlayer p : new ServerPlayer[] {a, b}) {
					CompoundTag none = new CompoundTag();
					none.putLong("amount", 0);
					act(p, t, "insurance", none);
					CompoundTag no = new CompoundTag();
					no.putBoolean("take", false);
					act(p, t, "even_money", no);
					act(p, t, "stand", new CompoundTag());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(trouble.isEmpty(), String.valueOf(trouble));
			helper.assertTrue(rounds[0] >= 2, "two shared rounds (phase " + phase(t, a) + ")");
			helper.assertTrue(balance(helper, a) >= 0 && balance(helper, b) >= 0, "balances stay valid");
			finish(helper, a, b);
		});
	}

	// ---- a generated village casino: MIXED atmosphere bots ----------------------------------------------------------

	/**
	 * Tables inside a generated casino get the worldgen bot defaults (MIXED, 2–3 atmosphere bots). A fresh Survival
	 * player (150 chips) walks up to the roulette, craps and blackjack tables there and puts the screen's default chip
	 * down on each; every bet lands.
	 */
	@GameTest(maxTicks = 2400)
	public void generatedCasinoTablesTakeAFreshPlayersBet(GameTestHelper helper) {
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		String dim = helper.getLevel().dimension().identifier().toString();
		String id = CasinoRecord.idFor(dim, o.getX(), o.getY(), o.getZ()) + "|a10";
		CasinoIndex.get(helper.getLevel().getServer()).setAnchor(id, dim, "test", CasinoKind.VILLAGE_CASINO,
			new Geometry.Box(o.getX(), o.getY(), o.getZ(), o.getX() + 7, o.getY() + 5, o.getZ() + 7));
		helper.setBlock(new BlockPos(1, 1, 1), RouletteModule.TABLE.block());
		helper.setBlock(new BlockPos(5, 1, 1), CrapsModule.TABLE.block());
		helper.setBlock(new BlockPos(1, 1, 5), BlackjackModule.TABLE.block());
		RouletteTableBlockEntity ru = helper.getBlockEntity(new BlockPos(1, 1, 1), RouletteTableBlockEntity.class);
		CrapsTableBlockEntity cr = helper.getBlockEntity(new BlockPos(5, 1, 1), CrapsTableBlockEntity.class);
		BlackjackTableBlockEntity bj = helper.getBlockEntity(new BlockPos(1, 1, 5), BlackjackTableBlockEntity.class);
		ServerPlayer p = player(helper, "gen-fresh", 3.5, 3.5, FRESH * 3);
		List<String> trouble = new ArrayList<>();
		helper.assertTrue(ru.bots().table().settings().policy() == SeatPolicy.MIXED,
			"the roulette table counts as generated (MIXED bots), policy " + ru.bots().table().settings().policy());
		use(helper, ru, p);
		act(p, ru, "bet", redBet(25, true));
		if (ru.stakeOf(p.getUUID()) != 25) trouble.add("roulette: " + errors(p));
		act(p, ru, "spin", new CompoundTag());
		use(helper, cr, p);
		CompoundTag pass = new CompoundTag();
		pass.putString("kind", "pass");
		pass.putLong("amount", 5);
		act(p, cr, "bet", pass);
		if (cr.stakeOf(p.getUUID()) != 5) trouble.add("craps: point " + cr.table().point() + " " + errors(p));
		use(helper, bj, p);
		act(p, bj, "sit", new CompoundTag());
		CompoundTag bet = new CompoundTag();
		bet.putLong("amount", 25);
		act(p, bj, "bet", bet);
		if (bj.stakeOf(p.getUUID()) != 25 && "betting".equals(phase(bj, p))) trouble.add("blackjack: seated " + bj.isSeated(p) + " " + errors(p));
		helper.runAfterDelay(200, () -> {
			CasinoIndex.get(helper.getLevel().getServer()).removeIf(r -> r.id().equals(id));
			helper.assertTrue(trouble.isEmpty(), String.valueOf(trouble));
			finish(helper, p);
		});
	}
}
