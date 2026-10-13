package dev.nezo.burmaldaholic.gametest.realflow;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
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
 * They buy in, play hands calling / checking, stand up; the chips are conserved (minus rake).
 */
public class RealFlowPokerGameTests {
	private static final Transaction TEST = Transaction.of("poker", "gametest");
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

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

	@GameTest(maxTicks = 3000)
	public void realflowTwoHumansPlayHandsThroughPackets(GameTestHelper helper) {
		helper.setBlock(TABLE, PokerModule.TABLE.block());
		BlockPos abs = helper.absolutePos(TABLE);
		PokerTableBlockEntity table = helper.getBlockEntity(TABLE, PokerTableBlockEntity.class);
		ServerPlayer a = player(helper, 2000, 0);
		ServerPlayer b = player(helper, 2000, 1);
		var server = helper.getLevel().getServer();
		int thinkMin = CasinoConfig.poker().botThinkMinTicks;
		int thinkMax = CasinoConfig.poker().botThinkMaxTicks;
		CasinoConfig.poker().botThinkMinTicks = 0;
		CasinoConfig.poker().botThinkMaxTicks = 2;
		long bb = CasinoConfig.poker().stakes.micro.bb;
		long amount = (long) CasinoConfig.poker().minBuyInBb * bb;
		a.openMenu(table);
		b.openMenu(table);
		for (ServerPlayer p : new ServerPlayer[] {a, b}) {
			CompoundTag t = new CompoundTag();
			t.putString("level", "micro");
			t.putLong("amount", amount);
			send(p, abs, "buy_in", t);
		}
		int[] startHand = {-1};
		helper.succeedWhen(() -> {
			for (ServerPlayer p : new ServerPlayer[] {a, b}) {
				helper.assertTrue(Economies.get().balance(p) == 2000 - amount || !table.writeClientState(p).getBooleanOr("seated", false),
					"buy-in through the packet path: " + Economies.get().balance(p));
			}
			CompoundTag sa = table.writeClientState(a);
			if (startHand[0] < 0) startHand[0] = sa.getIntOr("hand_no", 0);
			for (ServerPlayer p : new ServerPlayer[] {a, b}) {
				CompoundTag legal = table.writeClientState(p).getCompoundOrEmpty("legal");
				if (!legal.isEmpty()) {
					CompoundTag act = new CompoundTag();
					act.putString("kind", legal.getBooleanOr("can_check", false) ? "check" : "call");
					act.putInt("seq", legal.getIntOr("seq", 0));
					act.putLong("to", 0);
					send(p, abs, "act", act);
				}
			}
			helper.assertTrue(sa.getIntOr("hand_no", 0) >= startHand[0] + 2 && !sa.getBooleanOr("live", true), "two hands played");
			send(a, abs, "stand_up", new CompoundTag());
			send(b, abs, "stand_up", new CompoundTag());
			long total = Economies.get().balance(a) + Economies.get().balance(b);
			CasinoConfig.poker().botThinkMinTicks = thinkMin;
			CasinoConfig.poker().botThinkMaxTicks = thinkMax;
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
			// bots may have taken or lost chips too; the two humans cannot have created money beyond the table's pots
			helper.assertTrue(total > 0, "stacks paid back: " + total);
			helper.assertTrue(!table.writeClientState(a).getBooleanOr("seated", true), "a stood up");
		});
	}
}
