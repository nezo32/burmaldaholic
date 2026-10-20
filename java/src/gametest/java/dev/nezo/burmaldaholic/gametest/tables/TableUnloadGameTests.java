package dev.nezo.burmaldaholic.gametest.tables;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.GameType;

/**
 * Release blocker (T-HR round 2): a BUSY table (seated player, open stake, poker stack) whose chunk unloads is played out
 * from {@code ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE}, which vanilla fires INSIDE {@code DistanceManager.runAllUpdates}.
 * Any chunk access of the play-out then re-entered the distance manager and the server crashed (vanilla NPE in
 * {@code ReferenceOpenHashSet$SetIterator}). This test leaves busy tables of every game in a far, force-loaded chunk, drops
 * the ticket so vanilla unloads it the real way, then asserts: the server is alive, every stake came back exactly once,
 * and the tables loaded again from disk carry no stakes and no seats (no double refund later). A second set is played
 * out the way {@code SERVER_STOPPING} does it.
 */
public class TableUnloadGameTests {
	private static final Transaction TEST = Transaction.of("tables", "gametest");
	private static final long START = 100_000;
	private static final List<EmbeddedChannel> CHANNELS = new ArrayList<>();

	private static ServerPlayer player(GameTestHelper helper, String name) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		CHANNELS.add(new EmbeddedChannel(connection));
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 1.5)));
		Economies.get().setBalance(server, player.getUUID(), START, TEST);
		return player;
	}

	private static long balance(MinecraftServer server, ServerPlayer p) {
		return Economies.get().balance(server, p.getUUID());
	}

	private static CompoundTag tag(String... kv) {
		CompoundTag t = new CompoundTag();
		for (int i = 0; i < kv.length; i += 2) {
			String v = kv[i + 1];
			if (v.matches("-?\\d+")) t.putLong(kv[i], Long.parseLong(v));
			else t.putString(kv[i], v);
		}
		return t;
	}

	/** One busy table per game: the block, and how its player makes it busy (sit + a stake, poker: a buy-in). */
	private record Busy(String name, Block block, BiConsumer<CasinoTableBlockEntity, ServerPlayer> make) {}

	private static List<Busy> games() {
		return List.of(
			new Busy("blackjack", BlackjackModule.TABLE.block(), (t, p) -> {
				t.onAction(p, "sit", new CompoundTag());
				t.onAction(p, "bet", tag("amount", "100"));
			}),
			new Busy("roulette", RouletteModule.TABLE.block(), (t, p) -> {
				CompoundTag b = tag("type", "straight", "amount", "50");
				b.putIntArray("nums", new int[] {17});
				t.onAction(p, "bet", b);
			}),
			new Busy("baccarat", BaccaratModule.TABLE.block(), (t, p) -> t.onAction(p, "bet", tag("box", "player", "amount", "100"))),
			new Busy("craps", CrapsModule.TABLE.block(), (t, p) -> {
				t.onAction(p, "sit", new CompoundTag());
				t.onAction(p, "bet", tag("kind", dev.nezo.burmaldaholic.games.craps.logic.BetKind.PASS.id(), "amount", "50"));
			}),
			new Busy("uth", UthModule.TABLE.block(), (t, p) -> {
				t.onAction(p, "sit", new CompoundTag());
				t.onAction(p, "bet", tag("ante", "10", "trips", "0"));
			}),
			new Busy("poker", PokerModule.TABLE.block(), (t, p) -> t.onAction(p, "buy_in", tag("level", "micro", "amount", "200"))));
	}

	private record Placed(Busy game, BlockPos pos, ServerPlayer player) {}

	/** Places every game's table in the chunk {@code (cx, cz)} (loaded) and makes it busy; asserts each one took chips or a seat. */
	private static List<Placed> placeBusy(GameTestHelper helper, int cx, int cz, String tag) {
		ServerLevel level = helper.getLevel();
		List<Placed> out = new ArrayList<>();
		int i = 0;
		for (Busy g : games()) {
			BlockPos pos = new BlockPos(cx * 16 + 1 + (i % 4) * 4, helper.absolutePos(BlockPos.ZERO).getY() + 1, cz * 16 + 1 + (i / 4) * 6);
			i++;
			level.setBlockAndUpdate(pos, g.block().defaultBlockState());
			BlockEntity be = level.getBlockEntity(pos);
			helper.assertTrue(be instanceof CasinoTableBlockEntity, g.name() + ": table block entity at " + pos);
			CasinoTableBlockEntity t = (CasinoTableBlockEntity) be;
			ServerPlayer p = player(helper, tag + "-" + g.name());
			g.make().accept(t, p);
			boolean busy = t.seats().occupied().stream().anyMatch(s -> s.player().equals(p.getUUID())) || t.stakeOf(p.getUUID()) > 0
				|| balance(level.getServer(), p) < START;
			helper.assertTrue(busy, g.name() + ": the table is busy (seat / stake / buy-in) before the unload");
			out.add(new Placed(g, pos, p));
		}
		return out;
	}

	/**
	 * Every stake came back exactly once: an undrawn bet is refunded (balance = start). Blackjack and UTH deal a solo bet at
	 * once and craps has its roll pending, so those rounds are PLAYED OUT (settled by the rules): only a legal result is allowed.
	 */
	private static void assertSettled(GameTestHelper helper, List<Placed> placed, String when) {
		MinecraftServer server = helper.getLevel().getServer();
		for (Placed pl : placed) {
			long b = balance(server, pl.player());
			List<Long> deltas = switch (pl.game().name()) {
				case "blackjack" -> List.of(-100L, 0L, 100L, 150L);
				case "craps" -> List.of(-50L, 0L, 50L); // the pending roll is played out: Pass loses or wins even money
				case "uth" -> List.of(-20L, 0L); // dealt at once; played out with default decisions = fold at the river (§21.5)
				default -> List.of(0L);
			};
			boolean ok = deltas.contains(b - START);
			helper.assertTrue(ok, pl.game().name() + " " + when + ": balance " + b + " (start " + START + "): stake not returned exactly once");
		}
	}

	private static void assertReloadedClean(GameTestHelper helper, List<Placed> placed) {
		ServerLevel level = helper.getLevel();
		for (Placed pl : placed) {
			BlockEntity be = level.getBlockEntity(pl.pos());
			helper.assertTrue(be instanceof CasinoTableBlockEntity, pl.game().name() + ": table again after the reload");
			CasinoTableBlockEntity t = (CasinoTableBlockEntity) be;
			helper.assertTrue(t.stakeOf(pl.player().getUUID()) == 0, pl.game().name() + ": no stake carried over the reload");
			helper.assertTrue(t.seats().occupied().stream().noneMatch(s -> s.player().equals(pl.player().getUUID())),
				pl.game().name() + ": the seat was released");
			long before = balance(level.getServer(), pl.player());
			t.playOutNow(CasinoTableBlockEntity.SERVER_STOPPING); // a second stop/unload must pay nothing again
			helper.assertTrue(balance(level.getServer(), pl.player()) == before, pl.game().name() + ": a second play-out paid again");
		}
	}

	private static void cleanup(GameTestHelper helper, List<Placed> placed, int cx, int cz) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		for (Placed pl : placed) {
			if (level.hasChunk(cx, cz)) level.setBlockAndUpdate(pl.pos(), Blocks.AIR.defaultBlockState());
			pl.player().closeContainer();
			server.getPlayerList().remove(pl.player());
		}
		level.setChunkForced(cx, cz, false);
	}

	@GameTest(maxTicks = 600)
	public void busyTablesSurviveChunkUnload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ChunkPos far = new ChunkPos((origin.getX() >> 4) + 256, (origin.getZ() >> 4) + 256); // 4096 blocks off: no player keeps it
		int cx = far.x(), cz = far.z();
		level.setChunkForced(cx, cz, true);
		level.getChunk(cx, cz);
		List<Placed> placed = placeBusy(helper, cx, cz, "unl");
		level.setChunkForced(cx, cz, false); // the next distance-manager update unloads it, the way vanilla does
		String[] failed = {null};
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			if (!done[0]) {
				helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "waiting for the chunk to unload");
				done[0] = true; // checked once: an assertion below must fail the test, not be retried into a pass
				try {
					assertSettled(helper, placed, "after the unload");
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz); // load again (from disk, or the not yet saved holder)
					assertReloadedClean(helper, placed);
				} catch (RuntimeException e) {
					failed[0] = e.getMessage();
				} finally {
					cleanup(helper, placed, cx, cz);
				}
			}
			helper.assertTrue(failed[0] == null, String.valueOf(failed[0]));
		});
	}

	@GameTest(maxTicks = 200)
	public void busyTablesPlayOutAtServerStop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int cx = (origin.getX() >> 4) - 256, cz = (origin.getZ() >> 4) - 256;
		level.setChunkForced(cx, cz, true);
		level.getChunk(cx, cz);
		List<Placed> placed = placeBusy(helper, cx, cz, "stop");
		try {
			for (Placed pl : placed) {
				((CasinoTableBlockEntity) level.getBlockEntity(pl.pos())).playOutNow(CasinoTableBlockEntity.SERVER_STOPPING);
			}
			assertSettled(helper, placed, "after the server-stop play-out");
			assertReloadedClean(helper, placed);
		} finally {
			cleanup(helper, placed, cx, cz);
		}
		helper.succeed();
	}
}
