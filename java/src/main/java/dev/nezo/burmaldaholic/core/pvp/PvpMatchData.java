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
	/**
	 * Match id → {@link dev.nezo.burmaldaholic.core.pvp.logic.EscrowRecord} JSON: what the bank holds for a LOBBY /
	 * DRAWN match, stored apart from the match record so a record that fails to decode can still be refunded.
	 */
	private final Map<String, String> escrow = new LinkedHashMap<>();
	/**
	 * Match id → JSON {@code {"raw":…, "why":…, "tick":…}}: records whose escrow could not be read nor refunded.
	 * Nothing is dropped; operators see them on the PvP admin page (and the server log).
	 */
	private final Map<String, String> quarantine = new LinkedHashMap<>();

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

	public Map<String, String> escrow() {
		return Map.copyOf(escrow);
	}

	public void putEscrow(String id, String json) {
		if (!json.equals(escrow.put(id, json))) {
			setDirty();
		}
	}

	public void removeEscrow(String id) {
		if (escrow.remove(id) != null) {
			setDirty();
		}
	}

	public Map<String, String> quarantine() {
		return Map.copyOf(quarantine);
	}

	public void putQuarantine(String id, String json) {
		quarantine.put(id, json);
		setDirty();
	}

	/** Operator: the quarantined record was dealt with by hand. */
	public boolean removeQuarantine(String id) {
		boolean had = quarantine.remove(id) != null;
		if (had) {
			setDirty();
		}
		return had;
	}

	private static PvpMatchData load(CompoundTag tag) {
		PvpMatchData d = new PvpMatchData();
		CompoundTag m = tag.getCompoundOrEmpty("matches");
		for (String id : m.keySet()) {
			m.getString(id).ifPresent(json -> d.matches.put(id, json));
		}
		CompoundTag e = tag.getCompoundOrEmpty("escrow");
		for (String id : e.keySet()) {
			e.getString(id).ifPresent(json -> d.escrow.put(id, json));
		}
		CompoundTag q = tag.getCompoundOrEmpty("quarantine");
		for (String id : q.keySet()) {
			q.getString(id).ifPresent(json -> d.quarantine.put(id, json));
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag m = new CompoundTag();
		matches.forEach(m::putString);
		tag.put("matches", m);
		CompoundTag e = new CompoundTag();
		escrow.forEach(e::putString);
		tag.put("escrow", e);
		CompoundTag q = new CompoundTag();
		quarantine.forEach(q::putString);
		tag.put("quarantine", q);
		tag.putInt("format", 1);
		return tag;
	}
}
