package dev.nezo.burmaldaholic.gametest.v2.blackjack;

import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * T-D2 item 6: counts the table-preset world lookups for ONE position (the real provider is delegated to and put back on
 * {@link #close}). A table played out while it is removed (chunk unload) must read its preset 0 times: a lookup there
 * re-enters chunk loading mid-unload and crashes the server tick.
 */
public final class PresetReadCounter implements TablePresetProvider, AutoCloseable {
	private final TablePresetProvider inner = CoreServices.tablePresets();
	private final BlockPos watch;
	private final AtomicInteger reads = new AtomicInteger();

	public PresetReadCounter(BlockPos absolutePos) {
		this.watch = absolutePos.immutable();
		CoreServices.setTablePresets(this);
	}

	public int reads() {
		return reads.get();
	}

	public void reset() {
		reads.set(0);
	}

	@Override
	public Optional<TablePreset> preset(ServerLevel level, BlockPos pos) {
		if (watch.equals(pos)) reads.incrementAndGet();
		return inner.preset(level, pos);
	}

	@Override
	public Optional<BotPreset> botDefaults(ServerLevel level, BlockPos pos, String gameId) {
		if (watch.equals(pos)) reads.incrementAndGet();
		return inner.botDefaults(level, pos, gameId);
	}

	@Override
	public void close() {
		if (CoreServices.tablePresets() == this) CoreServices.setTablePresets(inner);
	}
}
