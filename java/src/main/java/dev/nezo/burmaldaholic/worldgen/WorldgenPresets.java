package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider.TablePreset;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.TablePresets;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/**
 * Core's table-preset hook ({@code CoreServices.setTablePresets}): tables inside a generated casino get
 * that casino's fixed settings ({@link TablePresets}); the games read them through
 * {@code CasinoTableBlockEntity#preset()}.
 */
public final class WorldgenPresets implements TablePresetProvider {
	@Override
	public Optional<TablePreset> preset(ServerLevel level, BlockPos pos) {
		CasinoIndex index = CasinoIndex.get(level.getServer());
		if (index.casinos().isEmpty()) {
			return Optional.empty();
		}
		CasinoRecord casino = CasinoRecord.findAt(index.casinos(), level.dimension().identifier().toString(),
			pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		if (casino == null) {
			return Optional.empty();
		}
		String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
		return TablePresets.presetId(casino.kind(), block).map(WorldgenPresets::of);
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
