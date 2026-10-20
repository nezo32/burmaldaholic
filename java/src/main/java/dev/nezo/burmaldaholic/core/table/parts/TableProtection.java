package dev.nezo.burmaldaholic.core.table.parts;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Hook for house (casino-owned) game protection, so core multi-block code doesn't depend on worldgen (CB-G's
 * {@code worldgen/HouseGames} lives on the CB branch). Every call gets the MAIN (controller) position of the table or
 * machine — never a part. The default allows everything; CB-G installs its implementation with {@link #install}:
 *
 * <pre>
 *   TableProtection.install(new TableProtection() {
 *       public boolean dropsAllowed(ServerLevel l, BlockPos main) { return HouseGames.dropsAllowed(l, main); }
 *       public boolean explosionProof(ServerLevel l, BlockPos main) { return HouseGames.explosionProof(l, main); }
 *       public boolean breakAllowed(ServerLevel l, BlockPos main, Player p) { return HouseGames.breakAllowed(l, main, p); }
 *       public void playerPlaced(ServerLevel l, BlockPos main, LivingEntity placer) { HouseGames.markPlayerPlaced(l, main, placer); }
 *   });
 * </pre>
 *
 * Wired in: breaking any part / the main block (no item when {@code dropsAllowed} is false, also in creative-free
 * survival breaks and explosions), the explosion filter (whole table stays when {@code explosionProof}), the break
 * event (refused when {@code breakAllowed} is false) and {@code setPlacedBy} ({@code playerPlaced}).
 */
public interface TableProtection {
	TableProtection ALLOW_ALL = new TableProtection() {};

	/** Whether breaking / blowing up the table at {@code main} may drop its item. */
	default boolean dropsAllowed(ServerLevel level, BlockPos main) {
		return true;
	}

	/**
	 * Whether removing the table at {@code main} may drop its item, given its (possibly already detached) main block
	 * entity — the loot-time check every removal path reaches ({@code getDrops}: player, explosion, wither,
	 * {@code /setblock … destroy}, other mods). Default: {@link #dropsAllowed(ServerLevel, BlockPos)}.
	 */
	default boolean dropsAllowed(ServerLevel level, BlockPos main, @Nullable BlockEntity mainBe) {
		return dropsAllowed(level, main);
	}

	/**
	 * A table saved before v0.2 (no {@code mt} tag) loaded for the first time: lets the house protection decide once
	 * whether it is a generated table or a player's (and mark it), before the migration rewrites the save.
	 */
	default void legacyTableLoaded(ServerLevel level, BlockPos main, BlockEntity mainBe) {}

	/** Whether the table at {@code main} (every part with it) survives explosions. */
	default boolean explosionProof(ServerLevel level, BlockPos main) {
		return false;
	}

	/** Whether {@code player} may break the table at {@code main} (any part of it). */
	default boolean breakAllowed(ServerLevel level, BlockPos main, Player player) {
		return true;
	}

	/** A player (or other entity) placed the table whose main block is {@code main}. */
	default void playerPlaced(ServerLevel level, BlockPos main, @Nullable LivingEntity placer) {}

	/** The installed protection (default {@link #ALLOW_ALL}). */
	static TableProtection get() {
		return Holder.current;
	}

	/** Installs the protection (CB-G at module init). */
	static void install(TableProtection protection) {
		Holder.current = Objects.requireNonNull(protection);
	}

	/** Internal holder of the installed protection. */
	final class Holder {
		private static volatile TableProtection current = ALLOW_ALL;

		private Holder() {}
	}
}
