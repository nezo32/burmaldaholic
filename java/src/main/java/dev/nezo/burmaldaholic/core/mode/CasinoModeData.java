package dev.nezo.burmaldaholic.core.mode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The stored casino mode switch: {@code <world>/data/burmaldaholic/mode.dat} in the server-wide saved
 * data storage. Absent file = OFF. Use {@link CasinoMode} (it fires the change listeners), not this class.
 */
public final class CasinoModeData extends SavedData {
	public static final Codec<CasinoModeData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("enabled", CasinoMode.DEFAULT).forGetter(CasinoModeData::enabled)
	).apply(i, CasinoModeData::new));

	/** null DataFixTypes: no vanilla fixer applies (Fabric skips datafixing for null). */
	public static final SavedDataType<CasinoModeData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("mode"), CasinoModeData::new, CODEC, null);

	private boolean enabled;

	public CasinoModeData() {
		this(CasinoMode.DEFAULT);
	}

	private CasinoModeData(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean enabled() {
		return enabled;
	}

	void setEnabled(boolean value) {
		enabled = value;
		setDirty(); // always: the file must exist even when the value did not change
	}

	static CasinoModeData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}
}
