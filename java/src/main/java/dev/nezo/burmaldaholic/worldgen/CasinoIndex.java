package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every generated casino of the world ({@code <world>/data/burmaldaholic/worldgen_casinos.dat}):
 * bounds for the "Welcome to…" title and the High Roller check, NPC homes for respawning.
 * Server thread only — worldgen threads hand records over with {@code server.execute}.
 */
public final class CasinoIndex extends SavedData {
	public static final SavedDataType<CasinoIndex> TYPE = new SavedDataType<>(
		Burmaldaholic.id("worldgen_casinos"), CasinoIndex::new, CompoundTag.CODEC.xmap(CasinoIndex::load, CasinoIndex::save), null);

	private final Map<String, CasinoRecord> casinos = new LinkedHashMap<>();

	public static CasinoIndex get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Collection<CasinoRecord> casinos() {
		return casinos.values();
	}

	public CasinoRecord recordFor(String id, String dimension) {
		return casinos.computeIfAbsent(id, k -> new CasinoRecord(id, dimension));
	}

	public void setAnchor(String id, String dimension, String layoutId, CasinoKind kind, Geometry.Box box) {
		recordFor(id, dimension).setAnchor(layoutId, kind, box);
		setDirty();
	}

	public void addNpc(String id, String dimension, NpcRole role, Vec home, float yaw, UUID entity) {
		recordFor(id, dimension).addNpc(role, home, yaw, entity);
		setDirty();
	}

	/** Forgets matching casinos (the buildings stay); returns whether anything was removed. */
	public boolean removeIf(java.util.function.Predicate<CasinoRecord> filter) {
		boolean removed = casinos.values().removeIf(filter);
		if (removed) {
			setDirty();
		}
		return removed;
	}

	/** Marks the data dirty after an NPC slot changed. */
	public void changed() {
		setDirty();
	}

	private CompoundTag save() {
		ListTag list = new ListTag();
		for (CasinoRecord r : casinos.values()) {
			CompoundTag t = new CompoundTag();
			t.putString("id", r.id());
			t.putString("dimension", r.dimension());
			if (r.box() != null && r.kind() != null) {
				t.putString("layout", r.layoutId());
				t.putString("kind", r.kind().id());
				Geometry.Box b = r.box();
				t.putIntArray("box", new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
			}
			ListTag npcs = new ListTag();
			for (CasinoRecord.NpcSlot s : r.npcs()) {
				CompoundTag n = new CompoundTag();
				n.putString("role", s.role().id());
				n.putIntArray("home", new int[] {s.home().x(), s.home().y(), s.home().z()});
				n.putFloat("yaw", s.yaw());
				if (s.entity != null) {
					n.putString("entity", s.entity.toString());
				}
				n.putLong("missing_since", s.missingSince);
				npcs.add(n);
			}
			t.put("npcs", npcs);
			list.add(t);
		}
		CompoundTag root = new CompoundTag();
		root.put("casinos", list);
		return root;
	}

	private static CasinoIndex load(CompoundTag root) {
		CasinoIndex data = new CasinoIndex();
		ListTag list = root.getListOrEmpty("casinos");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			String id = t.getStringOr("id", "");
			if (id.isEmpty()) {
				continue;
			}
			CasinoRecord r = data.recordFor(id, t.getStringOr("dimension", ""));
			int[] b = t.getIntArray("box").orElse(new int[0]);
			try {
				if (b.length == 6) {
					r.setAnchor(t.getStringOr("layout", ""), CasinoKind.byId(t.getStringOr("kind", "")),
						new Geometry.Box(b[0], b[1], b[2], b[3], b[4], b[5]));
				}
				ListTag npcs = t.getListOrEmpty("npcs");
				for (int j = 0; j < npcs.size(); j++) {
					CompoundTag n = npcs.getCompoundOrEmpty(j);
					int[] h = n.getIntArray("home").orElse(new int[0]);
					if (h.length != 3) {
						continue;
					}
					String entity = n.getStringOr("entity", "");
					CasinoRecord.NpcSlot slot = r.addNpc(NpcRole.byId(n.getStringOr("role", "")), new Vec(h[0], h[1], h[2]),
						n.getFloatOr("yaw", 0f), entity.isEmpty() ? null : UUID.fromString(entity));
					slot.missingSince = n.getLongOr("missing_since", slot.missingSince);
				}
			} catch (IllegalArgumentException e) {
				Burmaldaholic.LOGGER.warn("[worldgen] skipping unreadable casino record {}", id, e);
			}
		}
		return data;
	}
}
