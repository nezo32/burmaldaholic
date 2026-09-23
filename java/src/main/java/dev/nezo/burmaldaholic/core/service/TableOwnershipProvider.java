package dev.nezo.burmaldaholic.core.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Tells the table framework whether a table belongs to a player-owned casino (§18.2). The
 * multiplayer module implements it; the default says every table is a house table (bank-funded).
 */
@FunctionalInterface
public interface TableOwnershipProvider {
	TableOwnershipProvider HOUSE = (level, pos) -> Optional.empty();

	Optional<OwnedTable> owner(ServerLevel level, BlockPos pos);

	/**
	 * @param owner      owning player (cannot play at own tables)
	 * @param bankrollId {@code Economy.bankrolls()} account paying out and receiving stakes
	 * @param minBet     owner's min bet (0 = table default)
	 * @param maxBet     owner's max bet (0 = table default; tier max still applies)
	 * @param open       owner's open/closed switch
	 */
	record OwnedTable(UUID owner, String bankrollId, long minBet, long maxBet, boolean open) {}
}
