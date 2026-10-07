package dev.nezo.burmaldaholic.gametest.v2.uth;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
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
			act(table, a, "bet", bet(25, 0));
			helper.assertTrue(Economies.get().balance(a) == START - 50, "the new bet debited");
			long[] c = table.confirmedBet(a.getUUID()).orElseThrow();
			helper.assertTrue(c[0] == 25 && c[1] == 0, "the server holds the changed bet");
			act(table, b, "bet", bet(10, 0));
			helper.assertTrue(table.round() != null, "dealt when everyone confirmed");
			helper.assertTrue(table.round().seat(a.getUUID()).ante == 25, "the round plays the changed Ante");
			for (int i = 0; i < 20 && table.round() != null; i++) table.fastForwardForTests(1);
		} finally {
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
		}
		helper.succeed();
	}
}
