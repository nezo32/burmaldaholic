package dev.nezo.burmaldaholic.gametest.realflow;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBeats;
import dev.nezo.burmaldaholic.gametest.poker.FastPokerBots;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * REAL-FLOW poker with two humans (lane A4): two players open the table menu the way the block does
 * ({@code openMenu}) and every action goes in as a {@code ServerboundCustomPayloadPacket} through the player's packet
 * listener — the same path a client's click takes (the Fabric receiver, the menu / position check, then the table).
 * They buy in, play hands checking / calling small bets (folding to big ones), stand up; the chips come back.
 *
 * <p>Deterministic in ticks: the humans never risk more than half their buy-in, so neither can bust (a busted human
 * leaves; with both gone the table closes and its hand counter restarts — the old "call everything" version then
 * waited for a third hand that could never come and timed out at random, depending on the bots' cards). The tick
 * budget comes from the table's own pacing ({@link #budgetTicks}) and must fit {@link #MAX_TICKS}.
 */
public class RealFlowPokerGameTests {
	private static final Transaction TEST = Transaction.of("poker", "gametest");
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);
	private static final int HANDS = 2;
	/** The test's timeout; {@link #budgetTicks} (from the game's timing) must fit in it. */
	private static final int MAX_TICKS = 8000;

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance, double dx) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5 + dx, 1, 3.5));
		player.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), player.getUUID(), balance, TEST);
		return player;
	}

	private static void send(ServerPlayer p, BlockPos pos, String action, CompoundTag args) {
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new TableActionPayload(pos, action, args)));
	}

	/**
	 * Upper bound of {@code hands} hands at a full table, in ticks, from the game's own timing: the first hand's
	 * start delay (plus one waiting retry), and per hand the presentation segments (deal, three streets, the slowest
	 * finish), the next-hand delay, and every bot acting twice per street (an orbit and a re-open) at the bot think
	 * cap; the humans answer within two ticks of their turn opening.
	 */
	static int budgetTicks(int hands) {
		int seats = CasinoConfig.poker().maxSeats;
		int bots = Math.max(0, seats - 2);
		PokerBeats.Pacing pacing = PokerTableBlockEntity.pacing();
		int stages = PokerBeats.segment("deal", PokerBeats.withPacing(new int[] {seats, 0}, pacing), 0).sharedEndTicks();
		for (int street = 1; street <= 3; street++) {
			stages += PokerBeats.segment("street", PokerBeats.withPacing(new int[] {street}, pacing), 0).sharedEndTicks();
		}
		stages += PokerBeats.segment("finish", PokerBeats.withPacing(new int[] {1, 1, 1, seats, seats}, pacing), 0).sharedEndTicks();
		int actions = 4 * 2 * (bots * Math.max(1, PokerTableBlockEntity.botThinkCapTicks()) + 2 * 2);
		int hand = stages + actions + PokerTableBlockEntity.NEXT_HAND_SHOWDOWN_TICKS;
		return PokerTableBlockEntity.HAND_START_DELAY_TICKS + PokerTableBlockEntity.WAITING_RETRY_TICKS + hands * hand;
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void realflowTwoHumansPlayHandsThroughPackets(GameTestHelper helper) {
		int budget = budgetTicks(HANDS);
		helper.assertTrue(budget <= MAX_TICKS, "the game's pacing needs " + budget + " ticks for " + HANDS + " hands: raise MAX_TICKS (" + MAX_TICKS + ")");
		helper.setBlock(TABLE, PokerModule.TABLE.block());
		BlockPos abs = helper.absolutePos(TABLE);
		PokerTableBlockEntity table = helper.getBlockEntity(TABLE, PokerTableBlockEntity.class);
		ServerPlayer a = player(helper, 2000, 0);
		ServerPlayer b = player(helper, 2000, 1);
		ServerPlayer[] humans = {a, b};
		var server = helper.getLevel().getServer();
		FastPokerBots.Handle fast = FastPokerBots.acquire();
		long bb = CasinoConfig.poker().stakes.micro.bb;
		long amount = (long) CasinoConfig.poker().minBuyInBb * bb;
		a.openMenu(table);
		b.openMenu(table);
		for (ServerPlayer p : humans) {
			CompoundTag t = new CompoundTag();
			t.putString("level", "micro");
			t.putLong("amount", amount);
			send(p, abs, "buy_in", t);
		}
		for (ServerPlayer p : humans) {
			helper.assertTrue(table.writeClientState(p).getBooleanOr("seated", false), "seated through the packet path");
			helper.assertTrue(Economies.get().balance(p) == 2000 - amount, "buy-in escrowed through the packet path: " + Economies.get().balance(p));
		}
		int startHand = table.writeClientState(a).getIntOr("hand_no", 0);
		List<String> trace = new ArrayList<>();
		boolean[] done = {false};
		// Neither human can bust (see the class doc), so a lost seat is a bug: fail now with the trace, not at the timeout.
		helper.failIfEver(() -> {
			if (done[0]) {
				return;
			}
			for (ServerPlayer p : humans) {
				if (!table.writeClientState(p).getBooleanOr("seated", false)) {
					fast.release();
					throw helper.assertionException("a human lost the seat on tick %s: %s", helper.getTick(), String.join(" | ", trace));
				}
			}
		});
		helper.succeedWhen(() -> {
			CompoundTag sa = table.writeClientState(a);
			String now = "h" + sa.getIntOr("hand_no", 0) + (sa.getBooleanOr("live", false) ? " live" : " idle") + " street=" + sa.getStringOr("street", "")
				+ " to_act=" + sa.getIntOr("to_act", -1);
			if (trace.isEmpty() || !trace.getLast().endsWith(": " + now)) {
				trace.add(helper.getTick() + ": " + now);
			}
			for (ServerPlayer p : humans) {
				CompoundTag legal = table.writeClientState(p).getCompoundOrEmpty("legal");
				if (!legal.isEmpty()) {
					// check; call a small bet while at least half the buy-in stays behind; else fold
					long toCall = legal.getLongOr("to_call", 0);
					long stack = legal.getLongOr("my_stack", 0);
					boolean call = toCall <= 2 * bb && stack - toCall >= amount / 2;
					CompoundTag act = new CompoundTag();
					act.putString("kind", legal.getBooleanOr("can_check", false) ? "check" : call ? "call" : "fold");
					act.putInt("seq", legal.getIntOr("seq", 0));
					act.putLong("to", 0);
					send(p, abs, "act", act);
				}
			}
			helper.assertTrue(sa.getIntOr("hand_no", 0) >= startHand + HANDS && !sa.getBooleanOr("live", true),
				HANDS + " hands played (budget " + budget + " ticks): " + String.join(" | ", trace));
			done[0] = true;
			send(a, abs, "stand_up", new CompoundTag());
			send(b, abs, "stand_up", new CompoundTag());
			long total = Economies.get().balance(a) + Economies.get().balance(b);
			fast.release();
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
			// each human keeps at least half the buy-in behind (minus the blinds) and gets the stack back on standing up
			helper.assertTrue(total >= 2 * (2000 - amount) + 2 * (amount / 2 - 2 * bb), "stacks paid back: " + total);
			helper.assertTrue(!table.writeClientState(a).getBooleanOr("seated", true), "a stood up");
			helper.assertTrue(!table.writeClientState(b).getBooleanOr("seated", true), "b stood up");
		});
	}
}
