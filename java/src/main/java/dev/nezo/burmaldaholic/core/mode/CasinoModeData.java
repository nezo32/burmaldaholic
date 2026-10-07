package dev.nezo.burmaldaholic.core.mode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The stored casino mode switch: {@code <world>/data/burmaldaholic/mode.dat} in the server-wide saved
 * data storage. Use {@link CasinoMode} (it fires the change listeners), not this class.
 *
 * <p>Since v0.1.2 the {@code enabled} key is always written, so an explicit OFF ({@code /casino mode off})
 * is stored as {@code enabled:0b}. A missing key means "never set" and reads as {@link CasinoMode#DEFAULT}
 * (ON). v0.1.1 omitted the key for OFF (the old default), so its files cannot tell "never set" from
 * "turned off": such a file is {@link #legacy()} and reads as ON (GAME_DESIGN.md §2.1).
 */
public final class CasinoModeData extends SavedData {
	public static final Codec<CasinoModeData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("enabled").forGetter(d -> Optional.of(d.enabled))
	).apply(i, CasinoModeData::decoded));

	/** null DataFixTypes: no vanilla fixer applies (Fabric skips datafixing for null). */
	public static final SavedDataType<CasinoModeData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("mode"), CasinoModeData::new, CODEC, null);

	private boolean enabled;
	/** Loaded from a file without the {@code enabled} key (never set / v0.1.1 OFF). Not saved. */
	private final boolean legacy;

	public CasinoModeData() {
		this(CasinoMode.DEFAULT, false);
	}

	private CasinoModeData(boolean enabled, boolean legacy) {
		this.enabled = enabled;
		this.legacy = legacy;
	}

	private static CasinoModeData decoded(Optional<Boolean> enabled) {
		return new CasinoModeData(enabled.orElse(CasinoMode.DEFAULT), enabled.isEmpty());
	}

	public boolean enabled() {
		return enabled;
	}

	/** True if this was read from a file that had no explicit value (it then holds the default, ON). */
	public boolean legacy() {
		return legacy;
	}

	void setEnabled(boolean value) {
		enabled = value;
		setDirty(); // always: the file must exist (with an explicit value) even when the value did not change
	}

	static CasinoModeData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}
}
