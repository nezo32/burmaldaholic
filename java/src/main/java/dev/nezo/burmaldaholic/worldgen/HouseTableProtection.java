package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.table.parts.TableProtection;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.LegacyHouseTables;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Wires CB-G's {@link HouseGames} decisions into core's multi-block tables (MT {@link TableProtection}, T-CB D4/D5):
 * house tables are explosion-proof as a whole and never drop an item (any removal path); a table a player places gets
 * the persistent player-placed mark on its MAIN block entity, so it stays an ordinary table (breaks, drops) even inside
 * a casino box. Breaking permission stays with {@code HouseGames.beforeBreak} (its own break event: refusal message,
 * operator removal), so this adapter allows breaks to avoid a second refusal.
 */
public final class HouseTableProtection implements TableProtection {
	private HouseTableProtection() {}

	public static void install() {
		TableProtection.install(new HouseTableProtection());
	}

	@Override
	public boolean dropsAllowed(ServerLevel level, BlockPos main) {
		return HouseGames.dropsAllowed(level, main, level.getBlockEntity(main), null);
	}

	@Override
	public boolean dropsAllowed(ServerLevel level, BlockPos main, @Nullable BlockEntity mainBe) {
		return HouseGames.dropsAllowed(level, main, mainBe != null ? mainBe : level.getBlockEntity(main), null);
	}

	/**
	 * Coordinator decision (T-MT D2): a table saved before v0.2 carries no player-placed mark. Inside a casino it is the
	 * house's only when it stands on one of that casino's generated v0.1 table positions
	 * ({@link LegacyHouseTables}); any other legacy table there is a player's and gets the mark now.
	 */
	@Override
	public void legacyTableLoaded(ServerLevel level, BlockPos main, BlockEntity mainBe) {
		if (HouseGames.isPlayerPlaced(mainBe)) {
			return;
		}
		CasinoIndex index = CasinoIndex.get(level.getServer());
		if (index.casinos().isEmpty()) {
			return;
		}
		CasinoRecord record = index.recordAt(level.dimension().identifier().toString(), main);
		if (record != null && !LegacyHouseTables.generatedAt(record.layoutId(), record.box(), record.id(), main.getX(), main.getY(), main.getZ())) {
			HouseGames.markPlayerPlaced(mainBe);
		}
	}

	@Override
	public boolean explosionProof(ServerLevel level, BlockPos main) {
		return HouseGames.explosionProof(level, main, level.getBlockEntity(main));
	}

	@Override
	public void playerPlaced(ServerLevel level, BlockPos main, @Nullable LivingEntity placer) {
		if (placer instanceof Player) {
			HouseGames.markPlayerPlaced(level.getBlockEntity(main));
		}
	}
}
