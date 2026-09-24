package dev.nezo.burmaldaholic.gametest.slots;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.vip.VipService;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.slots.JackpotData;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.api.SlotsApi;
import dev.nezo.burmaldaholic.games.slots.logic.JackpotPool;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Slot machines in a real world: stake, settlement, SPIN event, VIP gate, persistent jackpot pools. */
public class SlotsGameTests {
	private static final Transaction TEST = Transaction.of("slots", "gametest");
	private static final Map<UUID, SlotsApi.Spin> SPINS = new ConcurrentHashMap<>();
	private static boolean listening;

	@SuppressWarnings("removal")
	private static void withPlayer(GameTestHelper helper, long balance, Consumer<ServerPlayer> body) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		try {
			Economies.get().setBalance(server, player.getUUID(), balance, TEST);
			body.accept(player);
		} finally {
			server.getPlayerList().remove(player);
		}
	}

	private static SlotMachineBlockEntity place(GameTestHelper helper, Tier tier) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, SlotsModule.MACHINES.get(tier).block());
		return helper.getBlockEntity(pos, SlotMachineBlockEntity.class);
	}

	private static CompoundTag lineBet(long v) {
		CompoundTag t = new CompoundTag();
		t.putLong("line_bet", v);
		return t;
	}

	@GameTest
	public void copperSpinStakesAndSettles(GameTestHelper helper) {
		if (!listening) {
			listening = true;
			SlotsApi.SPIN.register(s -> SPINS.put(s.playerId(), s));
		}
		SlotMachineBlockEntity machine = place(helper, Tier.COPPER);
		withPlayer(helper, 100, player -> {
			machine.onAction(player, "spin", lineBet(5));
			helper.assertTrue(machine.spinning(), "spin started");
			helper.assertTrue(Economies.get().balance(player) == 95, "stake of 5 taken");
			helper.assertTrue(machine.stakeOf(player.getUUID()) == 5, "open stake recorded");
			machine.onAction(player, "spin", lineBet(5));
			helper.assertTrue(Economies.get().balance(player) == 95, "no second spin while spinning");
			machine.finish(false);
			helper.assertFalse(machine.spinning(), "settled");
			helper.assertTrue(machine.stakeOf(player.getUUID()) == 0, "stake closed");
			CompoundTag result = machine.writeClientState(player).getCompoundOrEmpty("result");
			long total = result.getLongOr("total", -1);
			helper.assertTrue(total >= 0, "result synced");
			helper.assertTrue(Economies.get().balance(player) == 95 + total, "payout credited");
			SlotsApi.Spin spin = SPINS.get(player.getUUID());
			helper.assertTrue(spin != null && spin.spinBet() == 5 && spin.totalReturn() == total && "copper".equals(spin.tier()), "SPIN event");
		});
		helper.succeed();
	}

	/**
	 * Offline settlement (§4.1): a spin settled after the player disconnected pays at once (exactly once),
	 * and its side effects (streak, VIP wagered, contracts, advancements ...) are queued and applied when
	 * the player joins again — never paid a second time.
	 */
	@GameTest
	@SuppressWarnings("removal")
	public void offlineSettlementIsQueuedUntilJoin(GameTestHelper helper) {
		SlotMachineBlockEntity machine = place(helper, Tier.COPPER);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID id = player.getUUID();
		Economies.get().setBalance(server, id, 100, TEST);
		machine.onAction(player, "spin", lineBet(5));
		helper.assertTrue(machine.spinning(), "spin started");
		long wageredBefore = VipService.wagered(server, id);
		server.getPlayerList().remove(player); // disconnect mid-spin
		machine.finish(true);
		long total = machine.openStakes().isEmpty() ? Economies.get().balance(server, id) - 95 : -1;
		helper.assertTrue(total >= 0, "the spin settled for the offline player");
		helper.assertTrue(PlayResults.pending(server, id) == 1, "round queued for the next join");
		helper.assertTrue(VipService.wagered(server, id) == wageredBefore, "no side effects while offline");
		PlayResults.deliver(player); // what the JOIN handler does
		helper.assertTrue(PlayResults.pending(server, id) == 0, "queue drained");
		helper.assertTrue(VipService.wagered(server, id) == wageredBefore + 5, "VIP wagered applied on join");
		helper.assertTrue(Economies.get().balance(server, id) == 95 + total, "paid exactly once");
		PlayResults.deliver(player);
		helper.assertTrue(VipService.wagered(server, id) == wageredBefore + 5, "delivered once");
		helper.succeed();
	}

	@GameTest
	public void lineBetIsClampedToTheMachine(GameTestHelper helper) {
		SlotMachineBlockEntity machine = place(helper, Tier.GOLD);
		withPlayer(helper, 10_000, player -> {
			// Bronze max 100 -> gold line bet max floor(100 / 3) = 33
			machine.onAction(player, "spin", lineBet(500));
			helper.assertTrue(Economies.get().balance(player) == 10_000 - 99, "clamped to 33 x 3 lines");
			machine.finish(false);
		});
		helper.succeed();
	}

	@GameTest
	public void netheriteNeedsGoldVip(GameTestHelper helper) {
		SlotMachineBlockEntity machine = place(helper, Tier.NETHERITE);
		withPlayer(helper, 10_000, player -> {
			machine.onAction(player, "spin", lineBet(2));
			helper.assertFalse(machine.spinning(), "Bronze cannot play Netherite");
			helper.assertTrue(Economies.get().balance(player) == 10_000, "nothing taken");
			helper.assertFalse(machine.writeClientState(player).getBooleanOr("vip_ok", true), "screen shows the VIP lock");
		});
		helper.succeed();
	}

	@GameTest
	public void leavingMidSpinSettles(GameTestHelper helper) {
		SlotMachineBlockEntity machine = place(helper, Tier.COPPER);
		withPlayer(helper, 50, player -> {
			machine.onAction(player, "spin", lineBet(1));
			helper.assertTrue(machine.spinning(), "spin started");
			machine.leave(player.getUUID(), SlotMachineBlockEntity.LeaveReason.DISCONNECT);
			helper.assertFalse(machine.spinning(), "settled on leave");
			helper.assertTrue(machine.stakeOf(player.getUUID()) == 0, "nothing left open");
		});
		helper.succeed();
	}

	@GameTest
	public void jackpotPoolsPersistAndReset(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		JackpotData.get(server).set(Tier.GOLD, new JackpotPool(7_000, 0));
		helper.assertTrue(SlotsApi.jackpotPool(server, "gold") == 7_000, "pool read back");
		helper.assertTrue(SlotsApi.jackpotPool(server, "copper") == 0, "copper has no pool");
		SlotsApi.resetJackpots(server, "gold");
		helper.assertTrue(SlotsApi.jackpotPool(server, "gold") == 5_000, "reset to the seed");
		// a gold spin contributes 1 % (with the hidden remainder)
		SlotMachineBlockEntity machine = place(helper, Tier.GOLD);
		withPlayer(helper, 1_000, player -> {
			machine.onAction(player, "spin", lineBet(33));
			long before = SlotsApi.jackpotPool(server, "gold");
			CompoundTag pending = machine.writeClientState(player);
			helper.assertTrue(pending.contains("spin"), "spinning");
			machine.finish(false);
			long after = SlotsApi.jackpotPool(server, "gold");
			long award = machine.writeClientState(player).getCompoundOrEmpty("result").getLongOr("award", 0);
			helper.assertTrue(award > 0 || after == before, "99 x 1 % = 0.99 -> nothing yet, remainder kept");
		});
		helper.succeed();
	}

	// ---- slots v2 (SLOTS.md; lane J-L8) ------------------------------------------------------------

	private static SlotMachineBlockEntity placeV2(GameTestHelper helper, Tier tier, BlockPos pos) {
		helper.setBlock(pos, SlotsModule.MACHINES.get(tier).block());
		SlotMachineBlockEntity be = helper.getBlockEntity(pos, SlotMachineBlockEntity.class);
		be.forceV2ForTesting(true);
		return be;
	}

	private static CompoundTag bet(long v) {
		CompoundTag t = new CompoundTag();
		t.putLong("bet", v);
		return t;
	}

	/** CONFIRM → DRAW → PERSIST → SETTLE: stake, persisted tape, payout = tape, SPIN/ROUND events, stats. */
	@GameTest
	public void v2SpinStakesPersistsAndSettles(GameTestHelper helper) {
		SlotMachineBlockEntity machine = placeV2(helper, Tier.COPPER, new BlockPos(1, 1, 1));
		withPlayer(helper, 1_000, player -> {
			machine.onAction(player, "spin", bet(10));
			helper.assertTrue(machine.spinning(), "spin started");
			helper.assertTrue(Economies.get().balance(player) == 990, "stake of 10 taken");
			SpinTape tape = machine.roundTape();
			helper.assertTrue(tape != null && tape.bet() == 10 && tape.stops().length == 5, "tape drawn and kept");
			CompoundTag state = machine.writeClientState(player).getCompoundOrEmpty("v2");
			helper.assertTrue(state.getCompoundOrEmpty("spin").getLongOr("start_tick", -1) >= 0, "timeline seed sent");
			helper.assertTrue(state.getCompoundOrEmpty("spin").getIntOr("gate_ticks", 0) > 0 || tape.hunt() != null, "reveal gate known");
			machine.onAction(player, "spin", bet(10));
			helper.assertTrue(Economies.get().balance(player) == 990, "no second spin while one is in play");
			machine.finishV2(false);
			helper.assertFalse(machine.spinning(), "settled");
			helper.assertTrue(machine.stakeOf(player.getUUID()) == 0, "stake closed");
			helper.assertTrue(Economies.get().balance(player) == 990 + tape.payoutChips(), "paid exactly the tape");
			CompoundTag result = machine.writeClientState(player).getCompoundOrEmpty("v2").getCompoundOrEmpty("result");
			helper.assertTrue(result.getLongOr("total", -1) == tape.payoutChips(), "result synced");
		});
		helper.succeed();
	}

	/** SLOTS.md §15 test 13: a restart after the draw settles the round from its persisted tape (not a refund). */
	@GameTest
	public void v2RoundSettlesFromTheTapeAfterRestart(GameTestHelper helper) {
		SlotMachineBlockEntity machine = placeV2(helper, Tier.GOLD, new BlockPos(1, 1, 1));
		withPlayer(helper, 1_000, player -> {
			machine.onAction(player, "spin", bet(20));
			SpinTape tape = machine.roundTape();
			helper.assertTrue(tape != null, "drawn");
			var registries = helper.getLevel().registryAccess();
			CompoundTag saved = machine.saveCustomOnly(registries);
			// what a restart does: the block entity is loaded from the saved chunk with the open stake + tape
			machine.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
			helper.assertTrue(machine.roundTape() != null && machine.stakeOf(player.getUUID()) == 20, "round and stake restored");
			machine.tick(helper.getLevel()); // core: resume (settle from the tape) before refunding leftovers
			helper.assertFalse(machine.spinning(), "settled on load");
			helper.assertTrue(Economies.get().balance(player) == 980 + tape.payoutChips(), "settled at the persisted total, not refunded");
		});
		helper.succeed();
	}

	/** Buy feature (§6.3): the price is one wager; End needs Gold VIP (§8.5). */
	@GameTest
	public void v2BuyFeatureAndVipGate(GameTestHelper helper) {
		SlotMachineBlockEntity nether = placeV2(helper, Tier.GOLD, new BlockPos(1, 1, 1));
		SlotMachineBlockEntity end = placeV2(helper, Tier.NETHERITE, new BlockPos(3, 1, 1));
		withPlayer(helper, 10_000, player -> {
			nether.onAction(player, "buy", bet(20));
			helper.assertTrue(nether.spinning(), "bought");
			helper.assertTrue(Economies.get().balance(player) == 10_000 - 368, "18.4 x 20 = 368 taken");
			SpinTape tape = nether.roundTape();
			helper.assertTrue(tape.bought() && tape.freeSpins() != null, "free spins without a base spin");
			nether.leave(player.getUUID(), SlotMachineBlockEntity.LeaveReason.DISCONNECT);
			helper.assertFalse(nether.spinning(), "leaving = reveal: settled at once");
			long balance = Economies.get().balance(player);
			end.onAction(player, "spin", bet(50));
			helper.assertFalse(end.spinning(), "Bronze cannot play End Void");
			helper.assertTrue(Economies.get().balance(player) == balance, "nothing taken");
		});
		helper.succeed();
	}
}
