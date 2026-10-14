package dev.nezo.burmaldaholic.worldgen;

import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * House games (R-CB M9): the game blocks a generated casino placed are the house's, not loot.
 *
 * <ul>
 *   <li>A <b>house table</b> is a {@link CasinoTableBlock} (tables, slots, wheel, Plinko, cashiers) inside the box of
 *       any complete {@link CasinoIndex} record of its dimension — standalone casinos, v0.2 and v0.1 annexes, Piglin
 *       Parlors, High Roller Lounges — that no player placed. Tables a player places carry the persistent
 *       block-entity attachment {@code burmaldaholic:player_placed_table} and stay ordinary tables everywhere.</li>
 *   <li>Players without operator permission (level 2, any game mode) cannot break a house table:
 *       {@code PlayerBlockBreakEvents.BEFORE} cancels with {@code msg.burmaldaholic.worldgen.house_table_protected}.
 *       Runs alongside {@code Ownership.beforeBreak} (multiplayer-owned tables); either one may cancel.</li>
 *   <li>A house table drops nothing however it is removed (operators, {@code /setblock … destroy}, withers), and
 *       explosions leave it standing ({@link CasinoTableBlock} hooks). Pistons never move it: table blocks have
 *       block entities, which vanilla pistons refuse to push or pull.</li>
 * </ul>
 * Always on (no config key), independent of casino mode. Preset lookups never load chunks; this check only runs on a
 * break, a drop or an explosion at a table, i.e. in a loaded chunk.
 */
public final class HouseGames implements CasinoTableBlock.HouseTables {
	public static final String PROTECTED_KEY = "msg.burmaldaholic.worldgen.house_table_protected";
	private static @Nullable AttachmentType<Boolean> playerPlaced;

	private HouseGames() {}

	public static void register() {
		playerPlaced = AttachmentRegistry.createPersistent(Burmaldaholic.id("player_placed_table"), Codec.BOOL);
		CasinoTableBlock.setHouseTables(new HouseGames());
		PlayerBlockBreakEvents.BEFORE.register(HouseGames::beforeBreak);
	}

	/** PlayerBlockBreakEvents.BEFORE: non-operators may not break a house table. */
	static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(state.getBlock() instanceof CasinoTableBlock) || !(level instanceof ServerLevel serverLevel)
			|| !(player instanceof ServerPlayer sp) || isOperator(sp) || !isHouse(serverLevel, pos, be)) {
			return true;
		}
		sp.sendOverlayMessage(Component.translatable(PROTECTED_KEY));
		return false;
	}

	static boolean isOperator(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	/** True if a player placed the table whose block entity this is. */
	public static boolean isPlayerPlaced(@Nullable BlockEntity be) {
		return be != null && playerPlaced != null && Boolean.TRUE.equals(be.getAttached(playerPlaced));
	}

	static boolean isHouse(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
		if (isPlayerPlaced(be)) {
			return false;
		}
		CasinoIndex index = CasinoIndex.get(level.getServer());
		return !index.casinos().isEmpty() && index.recordAt(level.dimension().identifier().toString(), pos) != null;
	}

	@Override
	public boolean isHouseTable(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
		return isHouse(level, pos, be);
	}

	@Override
	public void placedByPlayer(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
		if (be != null && playerPlaced != null) {
			be.setAttached(playerPlaced, true);
			be.setChanged();
		}
	}
}
