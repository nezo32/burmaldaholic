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
}
