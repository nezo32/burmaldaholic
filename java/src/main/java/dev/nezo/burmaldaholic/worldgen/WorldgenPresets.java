package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider.TablePreset;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.TablePresets;
import dev.nezo.burmaldaholic.worldgen.logic.WorldgenBotPresets;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Core's table-preset hook ({@code CoreServices.setTablePresets}): tables inside a generated casino get
 * that casino's fixed settings ({@link TablePresets}); the games read them through
 * {@code CasinoTableBlockEntity#preset()}. Seats &amp; Bots: {@link #botDefaults} gives every bot table in a
 * generated casino its worldgen defaults, name theme and (Parlor poker) fixed MIXED mix.
 */
public final class WorldgenPresets implements TablePresetProvider {
	@Override
	public Optional<TablePreset> preset(ServerLevel level, BlockPos pos) {
		CasinoRecord casino = casinoAt(level, pos);
		if (casino == null) {
			return Optional.empty();
		}
		return TablePresets.presetId(casino.kind(), blockId(level, pos)).map(WorldgenPresets::of);
	}

	/**
	 * Bot table defaults of a generated casino table (J-G6): {@code bots.table.<game>.worldgen*} + the
	 * casino/table preset ({@link WorldgenBotPresets}); empty outside generated casinos or for a block
	 * without bot seats.
	 */
	@Override
	public Optional<BotPreset> botDefaults(ServerLevel level, BlockPos pos, String gameId) {
		CasinoRecord casino = casinoAt(level, pos);
		if (casino == null || gameId == null) {
			return Optional.empty();
		}
		String block = blockId(level, pos);
		if (WorldgenBotPresets.gameOf(block).isEmpty()) {
			return Optional.empty();
		}
		WorldgenBotPresets.Preset p = WorldgenBotPresets.presetFor(casino.kind(), block);
		return Optional.of(new BotPreset(WorldgenBotPresets.apply(TableBots.defaultsFor(gameId, true), p), p.nameTheme(),
			p.levelMixArray()));
	}

	private static @Nullable CasinoRecord casinoAt(ServerLevel level, BlockPos pos) {
		CasinoIndex index = CasinoIndex.get(level.getServer());
		if (index.casinos().isEmpty()) {
			return null;
		}
		return CasinoRecord.findAt(index.casinos(), level.dimension().identifier().toString(),
			pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private static String blockId(ServerLevel level, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
	}

	static TablePreset of(String id) {
		return switch (id) {
			case TablePresets.PARLOR_POKER -> TablePreset.parlorPoker();
			case TablePresets.HIGH_ROLLER_BLACKJACK -> TablePreset.highRollerBlackjack();
			case TablePresets.HIGH_ROLLER_ROULETTE -> TablePreset.highRollerRoulette();
			case TablePresets.HIGH_ROLLER_BACCARAT -> TablePreset.highRollerBaccarat();
			case TablePresets.HIGH_ROLLER_UTH -> TablePreset.highRollerUth();
			default -> throw new IllegalArgumentException(id);
		};
	}
}
