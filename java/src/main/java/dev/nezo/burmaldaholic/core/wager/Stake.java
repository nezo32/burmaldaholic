package dev.nezo.burmaldaholic.core.wager;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * A confirmed stake (GAME_DESIGN.md §4). Created by {@link Stakes} (which already debited chips or
 * took the pawn into escrow) and consumed by {@link Stakes#settle} or {@link Stakes#refund}.
 *
 * @param value      stake value V in chips (what the game pays against, what counts as "wagered")
 * @param item       escrowed item stack (ITEM), else empty
 * @param xpLevels   levels taken (XP)
 * @param xpProgress progress points taken with them (returned on win/refund)
 * @param hearts     hearts at risk (HEARTS)
 */
public record Stake(UUID player, String gameId, Kind kind, long value, ItemStack item, int xpLevels, int xpProgress, int hearts) {
	public enum Kind {
		CHIPS, ITEM, XP, HEARTS, SOUL
	}

	public boolean isPawn() {
		return kind != Kind.CHIPS;
	}
}
