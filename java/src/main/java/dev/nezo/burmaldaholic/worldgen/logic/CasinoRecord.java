package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A generated casino (PURE, mutable; owned by the server thread). Created by whichever data marker
 * of the casino is placed first — the anchor fills in layout and bounds, NPC markers add their slots.
 */
public final class CasinoRecord {
	private final String id;
	private final String dimension;
	private String layoutId;
	private CasinoKind kind;
	private Geometry.Box box;
	private final List<NpcSlot> npcs = new ArrayList<>();

	public CasinoRecord(String id, String dimension) {
		this.id = id;
		this.dimension = dimension;
	}

	/** Stable id: dimension + template origin of the piece ({@code minecraft:overworld|12|64|-40}). */
	public static String idFor(String dimension, int x, int y, int z) {
		return dimension + "|" + x + "|" + y + "|" + z;
	}

	public String id() {
		return id;
	}

	public String dimension() {
		return dimension;
	}

	public String layoutId() {
		return layoutId;
	}

	public CasinoKind kind() {
		return kind;
	}

	/** Bounds, or {@code null} until the anchor marker was placed. */
	public Geometry.Box box() {
		return box;
	}

	public List<NpcSlot> npcs() {
		return npcs;
	}

	public void setAnchor(String layoutId, CasinoKind kind, Geometry.Box box) {
		this.layoutId = layoutId;
		this.kind = kind;
		this.box = box;
	}

	/** Adds an NPC home; a second marker for the same home (re-placement) only updates the entity. */
	public NpcSlot addNpc(NpcRole role, Vec home, float yaw, UUID entity) {
		for (NpcSlot s : npcs) {
			if (s.home().equals(home)) {
				s.entity = entity;
				return s;
			}
		}
		NpcSlot slot = new NpcSlot(role, home, yaw);
		slot.entity = entity;
		npcs.add(slot);
		return slot;
	}

	/** True if the casino is complete and contains the (entity) position in this dimension. */
	public boolean contains(String dim, double x, double y, double z) {
		return box != null && kind != null && dimension.equals(dim) && box.contains(x, y, z, 0);
	}

	/** First complete casino containing the position. */
	public static CasinoRecord findAt(Iterable<CasinoRecord> records, String dim, double x, double y, double z) {
		for (CasinoRecord r : records) {
			if (r.contains(dim, x, y, z)) {
				return r;
			}
		}
		return null;
	}

	/** An NPC's home in a casino. */
	public static final class NpcSlot {
		private final NpcRole role;
		private final Vec home;
		private final float yaw;
		/** Last spawned entity, {@code null} if none could be spawned (type absent). */
		public UUID entity;
		public long missingSince = RespawnRule.NONE;

		public NpcSlot(NpcRole role, Vec home, float yaw) {
			this.role = role;
			this.home = home;
			this.yaw = yaw;
		}

		public NpcRole role() {
			return role;
		}

		public Vec home() {
			return home;
		}

		public float yaw() {
			return yaw;
		}
	}
}
