package dev.nezo.burmaldaholic.games.baccarat;

import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider.TablePreset;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Generated-casino presets for baccarat tables through core's preset hook (GAME_DESIGN §16.2/16.3):
 * a baccarat table standing in a Piglin Parlor is a standard table ({@code parlor_baccarat}), one in an
 * End City High Roller Lounge is a High-Roller table ({@code high_roller_baccarat}: min 100 per coup,
 * 2 × tier max, Gold VIP).
 *
 * <p>The worldgen module's provider only knows the tables of its own layouts, so this decorator (installed
 * after worldgen's, in module order) asks it about the other casino tables around a baccarat table: a
 * neighbour with a {@code high_roller*} preset marks a lounge, {@code parlor_*} a parlor. Answers are
 * cached per position (a casino never moves).
 */
final class BaccaratPresets implements TablePresetProvider {
	static final String HIGH_ROLLER = "high_roller_baccarat";
	static final String PARLOR = "parlor_baccarat";
	/** Search box around the table (the lounge is 15 × 9 × 15, the parlor 21 × 12 × 21). */
	private static final int RADIUS_XZ = 12;
	private static final int RADIUS_Y = 4;

	private final TablePresetProvider delegate;
	private final Map<String, Optional<TablePreset>> cache = new ConcurrentHashMap<>();

	private BaccaratPresets(TablePresetProvider delegate) {
		this.delegate = delegate;
	}

	static void install() {
		CoreServices.setTablePresets(new BaccaratPresets(CoreServices.tablePresets()));
	}

	static TablePreset highRoller() {
		return new TablePreset(HIGH_ROLLER, "", -1, 100, 2, VipTiers.GOLD, List.of());
	}

	static TablePreset parlor() {
		return new TablePreset(PARLOR, "", -1, 0, 0, 0, List.of());
	}

	@Override
	public Optional<TablePreset> preset(ServerLevel level, BlockPos pos) {
		Optional<TablePreset> own = delegate.preset(level, pos);
		if (own.isPresent() || !(level.getBlockEntity(pos) instanceof BaccaratTableBlockEntity)) {
			return own;
		}
		String key = level.dimension().identifier() + "@" + pos.asLong();
		Optional<TablePreset> cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		Optional<TablePreset> found = infer(level, pos);
		if (cache.size() > 4096) {
			cache.clear();
		}
		cache.put(key, found);
		return found;
	}

	private Optional<TablePreset> infer(ServerLevel level, BlockPos pos) {
		boolean parlor = false;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-RADIUS_XZ, -RADIUS_Y, -RADIUS_XZ), pos.offset(RADIUS_XZ, RADIUS_Y, RADIUS_XZ))) {
			if (p.equals(pos) || !level.isLoaded(p) || !(level.getBlockEntity(p) instanceof CasinoTableBlockEntity)
				|| level.getBlockEntity(p) instanceof BaccaratTableBlockEntity) {
				continue;
			}
			Optional<TablePreset> other = delegate.preset(level, p.immutable());
			if (other.isPresent()) {
				if (other.get().id().startsWith("high_roller")) {
					return Optional.of(highRoller());
				}
				parlor |= other.get().id().startsWith("parlor");
			}
		}
		return parlor ? Optional.of(parlor()) : Optional.empty();
	}
}
