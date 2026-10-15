package dev.nezo.burmaldaholic.gametest.v2.baccarat;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane A (baccarat): server GameTests of the in-world board's PUBLIC tag (docs/architecture/ui-v2.md §7.2): the boxes
 * show the bets, the coup's cards follow the reveal (no face before its beat, no winner before the result). Phase 2 (A2,
 * review R-AB m8): {@code unbet_chip} through the real {@code table_action} receiver takes back exactly one chip.
 */
public class BaccaratV2GameTests {
	private static final Transaction TEST = Transaction.of("baccarat", "gametest");

	@GameTest
	@SuppressWarnings("removal")
	public void publicTagFollowsTheReveal(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, BaccaratModule.TABLE.block());
		BaccaratTableBlockEntity table = helper.getBlockEntity(pos, BaccaratTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), 10_000, TEST);
			helper.assertTrue(table.sit(a), "seated");
			CompoundTag bet = new CompoundTag();
			bet.putString("box", "player");
			bet.putLong("amount", 100);
			table.onAction(a, "bet", bet);
			BaccaratPub betting = table.buildPub();
			helper.assertTrue(betting.boxes()[BaccaratPub.P] >= 100, "the Player box shows the bet");
			BaccaratPub.SeatPub mine = betting.seat(table.seats().seatOf(a.getUUID()).orElse(-1));
			helper.assertTrue(mine != null && mine.mainBox() == BaccaratPub.P, "the seat's main box is Player");
			table.onAction(a, "ready", new CompoundTag());
			for (int i = 0; i < 4 && !"reveal".equals(table.phase()); i++) table.fastForwardForTests();
			helper.assertTrue("reveal".equals(table.phase()), "the reveal runs");
			BaccaratPub reveal = table.buildPub();
			helper.assertTrue(reveal.winner() == 0, "no winner during the reveal");
			long now = helper.getLevel().getGameTime();
			for (int i = 0; i < reveal.player().length; i++) {
				boolean shown = reveal.playerRev()[i] >= 0 && reveal.revStart() + reveal.playerRev()[i] <= now;
				helper.assertTrue(reveal.player()[i] < 0 || shown, "no Player face before its reveal beat");
			}
			helper.assertTrue(table.getUpdateTag(helper.getLevel().registryAccess()).contains(BaccaratTableBlockEntity.PUB_KEY), "update tag");
			for (int i = 0; i < 6 && !"result".equals(table.phase()); i++) table.fastForwardForTests();
			BaccaratPub result = table.buildPub();
			helper.assertTrue("result".equals(table.phase()) && result.winner() > 0, "the winner is public on the result");
			helper.assertTrue(BaccaratPub.allShown(result.player()) && BaccaratPub.allShown(result.banker()), "every card face up on the result");
		} finally {
			server.getPlayerList().remove(a);
		}
		helper.succeed();
	}

	// ---- unbet_chip (A2, review R-AB m8) ------------------------------------------------------------------------------

	/** Resolved plays per player (CasinoEvents.PLAY_RESOLVED): a chip taken back must never count as a play. */
	private static final Map<UUID, Integer> PLAYS = new ConcurrentHashMap<>();
	private static boolean listening;

	private static synchronized void listen() {
		if (listening) return;
		listening = true;
		CasinoEvents.PLAY_RESOLVED.register((player, result) -> PLAYS.merge(player.getUUID(), 1, Integer::sum));
	}

	/** A SURVIVAL player connected like a real one, with the table menu opened by using the block (the receiver checks it). */
	private static ServerPlayer opened(GameTestHelper helper, BlockPos rel) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "bac-undo"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 1.5)));
		Economies.get().setBalance(server, player.getUUID(), 10_000, TEST);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu, "using the block opens the table menu");
		return player;
	}

	private static void act(ServerPlayer p, BaccaratTableBlockEntity t, String action, String box, long amount) {
		CompoundTag args = new CompoundTag();
		if (!box.isEmpty()) args.putString("box", box);
		if (amount > 0) args.putLong("amount", amount);
		TableNetworking.receive(p, new TableActionPayload(t.getBlockPos(), action, args));
	}

	private static long box(BaccaratTableBlockEntity t, ServerPlayer p, String box) {
		return t.writeClientState(p).getCompoundOrEmpty("slip").getLongOr(box, 0);
	}

	private static long balance(GameTestHelper helper, ServerPlayer p) {
		return Economies.get().balance(helper.getLevel().getServer(), p.getUUID());
	}

	@GameTest
	public void unbetChipTakesBackExactlyOneChip(GameTestHelper helper) {
		listen();
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, BaccaratModule.TABLE.block());
		BaccaratTableBlockEntity t = helper.getBlockEntity(rel, BaccaratTableBlockEntity.class);
		ServerPlayer p = opened(helper, rel);
		try {
			act(p, t, "sit", "", 0);
			helper.assertTrue(t.isSeated(p), "seated through the packet");
			act(p, t, "bet", "player", 25);
			act(p, t, "bet", "player", 25);
			act(p, t, "bet", "tie", 10);
			helper.assertTrue(box(t, p, "player") == 50 && box(t, p, "tie") == 10, "PLAYER 25 + 25, TIE 10 placed (slip " + t.writeClientState(p)
				.getCompoundOrEmpty("slip") + ")");
			long before = balance(helper, p);
			int plays = PLAYS.getOrDefault(p.getUUID(), 0);
			// more than the box holds: refused, nothing moves
			act(p, t, "unbet_chip", "tie", 25);
			helper.assertTrue(balance(helper, p) == before && box(t, p, "tie") == 10, "unbet_chip of more than the box holds is refused");
			// Undo of one 25 chip: exactly 25 back, PLAYER keeps 25, TIE untouched, the stake shrinks by 25
			act(p, t, "unbet_chip", "player", 25);
			helper.assertTrue(balance(helper, p) - before == 25, "exactly the chip comes back (got " + (balance(helper, p) - before) + ")");
			helper.assertTrue(box(t, p, "player") == 25, "PLAYER keeps the other chip (" + box(t, p, "player") + ")");
			helper.assertTrue(box(t, p, "tie") == 10, "the other box is untouched");
			helper.assertTrue(t.stakeOf(p.getUUID()) == 35, "the open stake is 35 (" + t.stakeOf(p.getUUID()) + ")");
			// the last chip of a box empties it
			act(p, t, "unbet_chip", "tie", 10);
			helper.assertTrue(box(t, p, "tie") == 0 && balance(helper, p) - before == 35 && t.stakeOf(p.getUUID()) == 25, "TIE emptied, 10 back");
			helper.assertTrue(PLAYS.getOrDefault(p.getUUID(), 0) == plays, "no PLAY_RESOLVED for a chip taken back");
			// betting closed: refused
			act(p, t, "ready", "", 0);
			for (int i = 0; i < 4 && "betting".equals(t.phase()); i++) t.fastForwardForTests();
			helper.assertTrue(!"betting".equals(t.phase()), "the coup runs (" + t.phase() + ")");
			long closed = balance(helper, p);
			long stake = t.stakeOf(p.getUUID());
			act(p, t, "unbet_chip", "player", 25);
			helper.assertTrue(balance(helper, p) == closed && t.stakeOf(p.getUUID()) == stake, "unbet_chip after no more bets is refused");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}

	/** T-D2 item 6: a baccarat coup in play is played out on removal without a single preset world read. */
	@GameTest
	@SuppressWarnings("removal")
	public void removalPlayOutReadsNoPreset(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, BaccaratModule.TABLE.block());
		BaccaratTableBlockEntity t = helper.getBlockEntity(rel, BaccaratTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		try (dev.nezo.burmaldaholic.gametest.v2.blackjack.PresetReadCounter counter =
				new dev.nezo.burmaldaholic.gametest.v2.blackjack.PresetReadCounter(helper.absolutePos(rel))) {
			Economies.get().setBalance(server, a.getUUID(), 10_000, TEST);
			helper.assertTrue(t.sit(a), "seated");
			CompoundTag bet = new CompoundTag();
			bet.putString("box", "player");
			bet.putLong("amount", 100);
			t.onAction(a, "bet", bet);
			t.onAction(a, "ready", new CompoundTag());
			for (int i = 0; i < 4 && "betting".equals(t.phase()); i++) t.fastForwardForTests();
			helper.assertTrue(t.stakeOf(a.getUUID()) > 0 && !"betting".equals(t.phase()), "a coup in play (" + t.phase() + ")");
			t.buildPub();
			counter.reset();
			helper.setBlock(rel, net.minecraft.world.level.block.Blocks.AIR);
			helper.assertTrue(counter.reads() == 0, "preset world reads during the removal play-out: " + counter.reads());
			helper.assertTrue(t.stakeOf(a.getUUID()) == 0, "the coup was played out on removal");
		} finally {
			server.getPlayerList().remove(a);
		}
		helper.succeed();
	}
}
