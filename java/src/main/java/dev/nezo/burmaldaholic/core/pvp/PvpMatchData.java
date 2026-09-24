package dev.nezo.burmaldaholic.core.pvp;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persisted matches ({@code <world>/data/burmaldaholic/pvp.dat}, PVP.md §3.6): one JSON string per match
 * id (format: docs/architecture/pvp-bots.md §5.1). Only LOBBY / DRAWN / SETTLED are stored; invites are
 * not. The DRAWN record MUST be written (setDirty) before the first reveal packet is sent.
 */
public final class PvpMatchData extends SavedData {
	public static final SavedDataType<PvpMatchData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("pvp"), PvpMatchData::new, CompoundTag.CODEC.xmap(PvpMatchData::load, PvpMatchData::save), null);

	private final Map<String, String> matches = new LinkedHashMap<>();

	public static PvpMatchData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Map<String, String> raw() {
		return Map.copyOf(matches);
	}

	public void put(String id, String json) {
		matches.put(id, json);
		setDirty();
	}

	public void remove(String id) {
		if (matches.remove(id) != null) {
			setDirty();
		}
	}

	private static PvpMatchData load(CompoundTag tag) {
		PvpMatchData d = new PvpMatchData();
		CompoundTag m = tag.getCompoundOrEmpty("matches");
		for (String id : m.keySet()) {
			m.getString(id).ifPresent(json -> d.matches.put(id, json));
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag m = new CompoundTag();
		matches.forEach(m::putString);
		tag.put("matches", m);
		tag.putInt("format", 1);
		return tag;
	}
}
