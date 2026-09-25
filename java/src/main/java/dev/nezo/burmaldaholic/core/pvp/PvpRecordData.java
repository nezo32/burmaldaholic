package dev.nezo.burmaldaholic.core.pvp;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-player PvP records ({@code <world>/data/burmaldaholic/pvp_records.dat}, PVP.md §3.8): one JSON
 * string per player UUID (format: pvp-bots.md §5.2 — totals, win streak, the 50 most recent rivals LRU,
 * "accept challenges" setting). Same JSON shape as the Bedrock player property {@code burmaldaholic:pvp_record}.
 */
public final class PvpRecordData extends SavedData {
	public static final SavedDataType<PvpRecordData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("pvp_records"), PvpRecordData::new, CompoundTag.CODEC.xmap(PvpRecordData::load, PvpRecordData::save), null);

	private final Map<UUID, String> records = new HashMap<>();

	public static PvpRecordData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public String json(UUID player) {
		return records.getOrDefault(player, "{}");
	}

	public void put(UUID player, String json) {
		records.put(player, json);
		setDirty();
	}

	private static PvpRecordData load(CompoundTag tag) {
		PvpRecordData d = new PvpRecordData();
		CompoundTag r = tag.getCompoundOrEmpty("records");
		for (String k : r.keySet()) {
			try {
				UUID id = UUID.fromString(k);
				r.getString(k).ifPresent(json -> d.records.put(id, json));
			} catch (IllegalArgumentException ignored) {
				// skip a corrupt key
			}
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag r = new CompoundTag();
		records.forEach((k, v) -> r.putString(k.toString(), v));
		tag.put("records", r);
		tag.putInt("format", 1);
		return tag;
	}
}
