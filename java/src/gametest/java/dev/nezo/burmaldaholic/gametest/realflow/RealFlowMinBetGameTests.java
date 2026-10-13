package dev.nezo.burmaldaholic.gametest.realflow;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.logic.CoinFlip;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * REAL-FLOW minimum bets of the extras with fractional payouts (hotfix A9): a winning bet never pays less than +1.
 * Every action goes in as a {@code ServerboundCustomPayloadPacket} through the player's packet listener (the Fabric
 * receiver, the item / menu check, then the game), like a client's click.
 */
public class RealFlowMinBetGameTests {
	private static final Transaction TEST = Transaction.of("extras", "gametest");
	private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 3.5));
		player.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), player.getUUID(), balance, TEST);
		return player;
	}

	private static void flip(ServerPlayer p, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("side", CoinFlip.Side.HEADS.id());
		t.putString("stake", "chips");
		t.putLong("amount", amount);
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new ExtrasActionPayload(CoinFlipGame.SCREEN, "flip", t)));
	}

	/** Coin flip: 1 chip is refused (floor(1 × 0.96) = 0 would be a win paying nothing); 2 chips plays and a win pays ≥ +1. */
	@GameTest(maxTicks = 200)
	public void realflowCoinFlipMinimumBetWinsAtLeastOne(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExtrasModule.LUCKY_COIN));
		long min = CoinFlip.minBet(CasinoConfig.extras().coinFlip.payout);
		helper.assertTrue(min == 2, "coin flip minimum at the default payout: " + min);
		flip(p, 1);
		helper.assertTrue(Economies.get().balance(p) == 1000, "a 1-chip flip is refused: " + Economies.get().balance(p));
		long before = 1000;
		for (int i = 0; i < 20; i++) {
			flip(p, 2);
			long now = Economies.get().balance(p);
			helper.assertTrue(now == before - 2 || now == before + 1, "a 2-chip flip loses 2 or wins +1: " + before + " -> " + now);
			before = now;
		}
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	/** Plinko: 2 chips are refused (floor(2 × 1.4) = 2 would be a 1.4× "win" paying nothing); 3 chips drop. */
	@GameTest(maxTicks = 400)
	public void realflowPlinkoMinimumBetWinsAtLeastOne(GameTestHelper helper) {
		helper.setBlock(MACHINE, ExtrasModule.PLINKO.block());
		BlockPos abs = helper.absolutePos(MACHINE);
		PlinkoBlockEntity machine = helper.getBlockEntity(MACHINE, PlinkoBlockEntity.class);
		ServerPlayer p = player(helper, 1000);
		p.openMenu(machine);
		helper.assertTrue(tableMin(machine.writeClientState(p), "low") == 3, "plinko Low minimum in the state: " + machine.writeClientState(p));
		CompoundTag t = new CompoundTag();
		t.putString("risk", "low");
		t.putLong("amount", 2);
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new TableActionPayload(abs, "drop", t)));
		helper.assertTrue(Economies.get().balance(p) == 1000, "a 2-chip drop is refused: " + Economies.get().balance(p));
		helper.assertTrue(!machine.writeClientState(p).contains("result"), "no drop played at 2 chips");
		t.putLong("amount", 3);
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new TableActionPayload(abs, "drop", t)));
		CompoundTag r = machine.writeClientState(p).getCompoundOrEmpty("result");
		helper.assertTrue(r.getLongOr("stake", 0) == 3, "the 3-chip drop played: " + r);
		double mult = r.getDoubleOr("mult", 0);
		long net = r.getLongOr("net", 0);
		helper.assertTrue(mult <= 1 || net >= 1, "a winning landing (" + mult + "×) pays at least +1: net " + net);
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	private static long tableMin(CompoundTag state, String risk) {
		net.minecraft.nbt.ListTag tables = state.getListOrEmpty("tables");
		for (int i = 0; i < tables.size(); i++) {
			if (risk.equals(tables.getCompoundOrEmpty(i).getStringOr("risk", ""))) {
				return tables.getCompoundOrEmpty(i).getLongOr("min", -1);
			}
		}
		return -1;
	}

	/**
	 * v0.1.2 review: the minimum is per risk — Medium / High have no win near ×1, so 1 chip drops there (every win pays
	 * ≥ +1 at 1 chip) while Low still needs 3. The state carries each risk's minimum for the screen.
	 */
	@GameTest(maxTicks = 400)
	public void realflowPlinkoMinimumBetPerRisk(GameTestHelper helper) {
		helper.setBlock(MACHINE, ExtrasModule.PLINKO.block());
		BlockPos abs = helper.absolutePos(MACHINE);
		PlinkoBlockEntity machine = helper.getBlockEntity(MACHINE, PlinkoBlockEntity.class);
		for (String risk : new String[] {"medium", "high"}) {
			ServerPlayer p = player(helper, 1000);
			p.openMenu(machine);
			CompoundTag state = machine.writeClientState(p);
			helper.assertTrue(tableMin(state, risk) == 1 && state.getLongOr("min", 0) == 1, risk + " minimum in the state: " + state);
			CompoundTag t = new CompoundTag();
			t.putString("risk", risk);
			t.putLong("amount", 1);
			p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new TableActionPayload(abs, "drop", t)));
			CompoundTag r = machine.writeClientState(p).getCompoundOrEmpty("result");
			helper.assertTrue(r.getLongOr("stake", 0) == 1 && risk.equals(r.getStringOr("risk", "")), "a 1-chip " + risk + " drop played: " + r);
			double mult = r.getDoubleOr("mult", 0);
			helper.assertTrue(mult <= 1 || r.getLongOr("net", 0) >= 1, "a winning " + risk + " landing pays at least +1: " + r);
			p.closeContainer();
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		ServerPlayer p = player(helper, 1000);
		p.openMenu(machine);
		CompoundTag t = new CompoundTag();
		t.putString("risk", "low");
		t.putLong("amount", 1);
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new TableActionPayload(abs, "drop", t)));
		helper.assertTrue(Economies.get().balance(p) == 1000, "a 1-chip Low drop is still refused: " + Economies.get().balance(p));
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}
}
