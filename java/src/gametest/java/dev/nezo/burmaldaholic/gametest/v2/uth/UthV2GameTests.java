package dev.nezo.burmaldaholic.gametest.v2.uth;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import dev.nezo.burmaldaholic.games.uth.present.UthPub;
import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane B (UTH): the v2 bet flow through the table's action handler ({@code handleAction}, what the C2S
 * {@code table_action} receiver calls): a confirmed bet is changed by {@code clear} (refund) + a new {@code bet} — the
 * exact sequence the v2 screen's "Change bet" sends — while another player still bets; the round then deals with the
 * changed amounts.
 */
public class UthV2GameTests {
	private static final Transaction TEST = Transaction.of("uth", "gametest");
	private static final long START = 1000;

	private static CompoundTag bet(long ante, long trips) {
		CompoundTag t = new CompoundTag();
		t.putLong("ante", ante);
		t.putLong("trips", trips);
		return t;
	}

	/** The table's action entry point — what the {@code table_action} payload receiver calls (package-private there). */
	static void act(CasinoTableBlockEntity table, ServerPlayer player, String action, CompoundTag args) {
		try {
			java.lang.reflect.Method m = CasinoTableBlockEntity.class.getDeclaredMethod("handleAction", ServerPlayer.class, String.class, CompoundTag.class);
			m.setAccessible(true);
			m.invoke(table, player, action, args);
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@GameTest
	@SuppressWarnings("removal")
	public void changeBetRefundsAndReplacesTheConfirmedBet(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, UthModule.TABLE.block());
		UthTableBlockEntity table = helper.getBlockEntity(pos, UthTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		ServerPlayer b = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), START, TEST);
			Economies.get().setBalance(server, b.getUUID(), START, TEST);
			act(table, a, "sit", new CompoundTag());
			act(table, b, "sit", new CompoundTag());
			helper.assertTrue(table.seatedHumans().size() == 2, "both seated through the handler");
			act(table, a, "bet", bet(10, 5));
			helper.assertTrue(Economies.get().balance(a) == START - 25, "Ante + Blind + Trips debited");
			helper.assertTrue(table.round() == null, "b still bets: no deal");
			// Change bet: clear (refund) …
			act(table, a, "clear", new CompoundTag());
			helper.assertTrue(Economies.get().balance(a) == START, "refunded");
			helper.assertTrue(table.confirmedBet(a.getUUID()).isEmpty(), "no confirmed bet after clear");
			// … and the edited bet
			act(table, a, "bet", bet(15, 0));
			helper.assertTrue(Economies.get().balance(a) == START - 30, "the new bet debited");
			long[] c = table.confirmedBet(a.getUUID()).orElseThrow();
			helper.assertTrue(c[0] == 15 && c[1] == 0, "the server holds the changed bet");
			act(table, b, "bet", bet(10, 0));
			helper.assertTrue(table.round() != null, "dealt when everyone confirmed");
			helper.assertTrue(table.round().seat(a.getUUID()).ante == 15, "the round plays the changed Ante");
			for (int i = 0; i < 20 && table.round() != null; i++) table.fastForwardForTests(1);
		} finally {
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
		}
		helper.succeed();
	}

	/** A SURVIVAL player on a real (embedded) connection, as {@code RealFlowTableGameTests} makes them. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name, List<EmbeddedChannel> keep) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(java.util.UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		keep.add(new EmbeddedChannel(connection));
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 1.5)));
		Economies.get().setBalance(server, player.getUUID(), START, TEST);
		return player;
	}

	/** Through the real {@code table_action} receiver (menu open, in range). */
	private static void send(ServerPlayer player, UthTableBlockEntity table, String action, CompoundTag args) {
		TableNetworking.receive(player, new TableActionPayload(table.getBlockPos(), action, args));
	}

	/**
	 * R-AB m10: the public tag (in-world board, dealer NPC, everyone near the table) never carries a hole card before the
	 * SHOWDOWN segment and never a folded hand. Two players open the table by using it and play through the
	 * {@code table_action} receiver: A checks to the river and folds, B checks and plays ×1.
	 */
	@GameTest(maxTicks = 400)
	public void publicTagShowsHoleCardsOnlyAtShowdownAndNeverFolded(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, UthModule.TABLE.block());
		UthTableBlockEntity table = helper.getBlockEntity(rel, UthTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		List<EmbeddedChannel> channels = new ArrayList<>();
		ServerPlayer a = survivalPlayer(helper, "uth_folder", channels);
		ServerPlayer b = survivalPlayer(helper, "uth_player", channels);
		try {
			for (ServerPlayer p : List.of(a, b)) {
				helper.useBlock(rel, p);
				helper.assertTrue(p.containerMenu instanceof CasinoTableMenu m && m.pos().equals(table.getBlockPos()), "using the table opens its menu");
				send(p, table, "sit", new CompoundTag());
			}
			helper.assertTrue(table.seatedHumans().size() == 2, "both seated through the receiver");
			send(a, table, "bet", bet(10, 0));
			helper.assertTrue(table.buildPub().cards().length == 0, "betting: nothing public");
			send(b, table, "bet", bet(10, 0));
			helper.assertTrue(table.round() != null, "dealt when both confirmed");
			UthRound r = table.round();
			boolean sawShowdown = false;
			boolean sawB = false;
			for (int guard = 0; guard < 60 && table.round() == r; guard++) {
				UthPub pub = table.buildPub();
				List<UthRound.Seat> seats = r.seats();
				boolean showdownSegment = pub.kind() == UthPub.SHOWDOWN || pub.kind() == UthPub.RESULT;
				sawShowdown |= showdownSegment;
				for (int i = 0; i < seats.size(); i++) {
					UthRound.Seat s = seats.get(i);
					boolean shown = pub.cards()[i * 2] >= 0 || pub.cards()[i * 2 + 1] >= 0;
					if (!showdownSegment) helper.assertFalse(shown, "segment " + pub.kind() + ": " + s.name + "'s cards are public before the showdown");
					if (s.folded) helper.assertFalse(shown, "segment " + pub.kind() + ": the folded hand of " + s.name + " is public");
					if (showdownSegment && !s.folded && s.player.equals(b.getUUID())) {
						helper.assertTrue(shown, "showdown: the active hand of " + s.name + " is public");
						sawB = true;
					}
				}
				boolean acted = false;
				for (UthRound.Seat s : r.pendingSeats()) {
					ServerPlayer who = s.player.equals(a.getUUID()) ? a : s.player.equals(b.getUUID()) ? b : null;
					if (who == null) continue;
					List<String> legal = new ArrayList<>();
					for (var d : r.legal(s, true)) legal.add(d.id());
					String pick = legal.contains("check") ? "check" : who == a ? "fold" : "bet_1x";
					send(who, table, pick, new CompoundTag());
					acted = true;
				}
				if (!acted) table.fastForwardForTests(1);
			}
			helper.assertTrue(r.seat(a.getUUID()).folded, "A folded at the river");
			helper.assertTrue(sawShowdown && sawB, "the showdown segment was published with B's cards");
		} finally {
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
		}
		helper.succeed();
	}
}
