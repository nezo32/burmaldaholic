package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.multiplayer.Ownership;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * A table test starts from its own state, not from what an earlier test at the same spot left. Vanilla lays every batch
 * out on the same grid, so test N of a later batch places its table at the absolute position test N of an earlier batch
 * used, and all server state keyed by position carries over: the bot ledger's per-table house buy-ins (10 a day; spent,
 * no bot can sit and the chemmy bank never reaches a bot: {@code playerPuntsAgainstABotBanker} timed out in about half
 * of the full runs), owned-table records (owner limits, "table closed"), casino records (generated-casino presets,
 * worldgen bots) and the baccarat preset cache. Call {@link #fresh} before placing the table.
 */
public final class TableIsolation {
	private TableIsolation() {}

	/** Forgets every position-keyed record at test-relative {@code rel} (call before {@code setBlock}). */
	public static void fresh(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(rel);
		String dim = level.dimension().identifier().toString();
		BotLedger.forgetTableForTests(level.getServer(), dim + "@" + abs.getX() + "," + abs.getY() + "," + abs.getZ());
		Ownership.forgetTableForTests(level, abs);
		CasinoIndex.get(level.getServer()).removeIf(r -> r.contains(dim, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5));
		BaccaratModule.forgetPresetForTests(level, abs);
	}
}
