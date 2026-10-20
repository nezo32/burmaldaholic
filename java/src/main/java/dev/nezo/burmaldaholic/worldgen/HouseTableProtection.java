package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.table.parts.TableProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
