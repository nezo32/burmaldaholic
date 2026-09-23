package dev.nezo.burmaldaholic.multiplayer;

import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.Casino;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Claim lookups for other features (chaos: no mob waves inside casinos; loan: collectors, etc.).
 *
 * <p>Feature modules must not import this package directly (java.md §4); until core exposes a
 * {@code CoreServices} claim provider these are the functions such a provider should delegate to.
 * Server thread only.
 */
public final class MultiplayerApi {
	private MultiplayerApi() {}

	/** Whether the block column lies inside any player casino claim (full height). */
	public static boolean isInsideClaim(ServerLevel level, BlockPos pos) {
		return Ownership.casinoAt(level, pos).isPresent();
	}

	/** Owner of the casino claim containing the position. */
	public static Optional<UUID> ownerAt(ServerLevel level, BlockPos pos) {
		return Ownership.casinoAt(level, pos).map(c -> c.owner);
	}

	/** Last known owner name of the claim containing the position. */
	public static Optional<String> ownerNameAt(ServerLevel level, BlockPos pos) {
		return Ownership.casinoAt(level, pos).map(c -> c.ownerName);
	}

	/** Whether poker bots may sit at the table (owner setting; true for house tables). */
	public static boolean botsAllowed(ServerLevel level, BlockPos pos) {
		return Ownership.book(level.getServer()).table(Ownership.key(level, pos)).map(t -> t.bots).orElse(true);
	}

	/** Records poker rake that the game already paid into the owner's bankroll (statistics only). */
	public static void recordRake(ServerLevel level, BlockPos pos, long rake) {
		if (rake > 0) {
			Ownership.recordRake(level, pos, rake);
		}
	}

	/** Casino id at a position (for logging / debugging). */
	public static Optional<String> casinoIdAt(ServerLevel level, BlockPos pos) {
		return Ownership.casinoAt(level, pos).map((Casino c) -> c.id);
	}
}
