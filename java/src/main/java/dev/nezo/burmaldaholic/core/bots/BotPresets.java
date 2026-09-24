package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider.BotPreset;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Worldgen bot presets of a table block (J-G6, BOTS.md §2.3 / §7.1) through
 * {@code CoreServices.tablePresets().botDefaults}: table defaults for generated tables, the casino's name
 * theme and level mix. Games call it when they create their {@link TableBots} and from their
 * {@link BotTable#botNameTheme()} / {@link BotTable#botDifficultyMix()} hooks. Server thread.
 */
public final class BotPresets {
	private BotPresets() {}

	/** The preset of the generated table {@code be} (empty for craftable tables or before the level is known). */
	public static Optional<BotPreset> of(@Nullable BlockEntity be, String gameId) {
		if (be == null || !(be.getLevel() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		try {
			return CoreServices.tablePresets().botDefaults(level, be.getBlockPos(), gameId);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("bot preset lookup failed for {} at {}", gameId, be.getBlockPos(), e);
			return Optional.empty();
		}
	}

	/** The preset's defaults, else {@code bots.table.<game>.*} (worldgen columns when {@code generated}). */
	public static BotSettings defaults(@Nullable BlockEntity be, String gameId, boolean generated) {
		return of(be, gameId).map(BotPreset::defaults).orElseGet(() -> TableBots.defaultsFor(gameId, generated));
	}

	/** The preset's name theme, else {@code fallback}. */
	public static BotRoster.Theme theme(@Nullable BlockEntity be, String gameId, BotRoster.Theme fallback) {
		return of(be, gameId).map(BotPreset::nameTheme).orElse(fallback);
	}

	/** The preset's fixed MIXED weights, else null (the game's own mix). */
	public static int @Nullable [] mix(@Nullable BlockEntity be, String gameId) {
		return of(be, gameId).map(BotPreset::levelMix).orElse(null);
	}
}
