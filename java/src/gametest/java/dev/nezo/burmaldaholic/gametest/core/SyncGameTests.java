package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.core.anim.sync.SeqGate;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * SY-SPEC §4: every table state packet carries {@code sync_seq} (+1 per write), {@code sync_epoch} (random per
 * block-entity load) and {@code game_time}; the game's own state is unchanged. Blackjack (no game_time of its own) and
 * roulette (writes its own game_time).
 */
public class SyncGameTests {
	private static final Transaction TEST = Transaction.of("sync", "gametest");

	@GameTest
	public void blackjackStateCarriesSeqEpochAndGameTime(GameTestHelper helper) {
		check(helper, BlackjackModule.TABLE.block(), BlackjackTableBlockEntity.class);
	}

	@GameTest
	public void rouletteStateCarriesSeqEpochAndGameTime(GameTestHelper helper) {
		check(helper, RouletteModule.TABLE.block(), RouletteTableBlockEntity.class);
	}

	private static <B extends CasinoTableBlockEntity> void check(GameTestHelper helper, Block block, Class<B> type) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, block);
		B table = helper.getBlockEntity(pos, type);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), 1000, TEST);
			CompoundTag plain = table.writeClientState(a);
			CompoundTag s1 = table.syncedState(a);
			helper.assertTrue(table.sit(a), "seated");
			CompoundTag s2 = table.syncedState(a);
			table.sendStateTo(a); // the real send path writes too
			CompoundTag s3 = table.syncedState(a);
			long q1 = s1.getLongOr(SeqGate.SEQ, -1);
			helper.assertTrue(q1 >= 1, "sync_seq present: " + q1);
			helper.assertTrue(s2.getLongOr(SeqGate.SEQ, -1) == q1 + 1, "sync_seq +1 per write");
			helper.assertTrue(s3.getLongOr(SeqGate.SEQ, -1) == q1 + 3, "sendStateTo is a write too");
			helper.assertTrue(s1.getIntOr(SeqGate.EPOCH, 0) == table.syncEpoch() && s3.getIntOr(SeqGate.EPOCH, 0) == table.syncEpoch(),
					"one epoch per load");
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(Math.abs(s3.getLongOr(SeqGate.GAME_TIME, -1) - now) <= 1, "game_time is the level time");
			for (String k : plain.keySet()) {
				if (!k.equals(SeqGate.GAME_TIME) && !k.equals("timers")) {
					helper.assertTrue(s1.contains(k), "the game's state key " + k + " is kept");
				}
			}
			// a replaced table is a new load: a new epoch (the client gate resets)
			int old = table.syncEpoch();
			helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
			helper.setBlock(pos, block);
			B fresh = helper.getBlockEntity(pos, type);
			helper.assertTrue(fresh != table && fresh.syncEpoch() != old, "new block entity, new epoch");
		} finally {
			server.getPlayerList().remove(a);
		}
		helper.succeed();
	}
}
