package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.List;
import java.util.Map;

/**
 * Where v0.1.x casinos generated their (1×1) game blocks, per layout, in template-local coordinates — the union over
 * every v0.1 release (v0.1.3 added the village baccarat table). Coordinator decision (T-MT D2): a legacy table (saved
 * before v0.2, so without the player-placed mark) inside a casino box is the house's only when it stands on one of these
 * positions of that casino's layout; anywhere else it is a player's. The record keeps the layout id and the rotated
 * template box, so every rotation whose box matches is tried. Pure.
 */
public final class LegacyHouseTables {
	private record Template(Vec size, List<Vec> tables) {}

	private static final Template VILLAGE = new Template(new Vec(17, 10, 17), List.of(
		new Vec(2, 1, 6), new Vec(3, 1, 6), new Vec(4, 1, 6), new Vec(11, 1, 6), new Vec(13, 1, 6), new Vec(4, 1, 10),
		new Vec(12, 1, 10), new Vec(15, 1, 13), new Vec(6, 1, 6), new Vec(12, 1, 13)));
	private static final Template PARLOR = new Template(new Vec(21, 12, 21), List.of(
		new Vec(6, 1, 6), new Vec(14, 1, 6), new Vec(1, 1, 11), new Vec(1, 1, 13), new Vec(19, 1, 11), new Vec(19, 1, 15),
		new Vec(6, 1, 12)));
	private static final Template LOUNGE = new Template(new Vec(15, 9, 15), List.of(
		new Vec(2, 1, 1), new Vec(4, 1, 1), new Vec(4, 1, 5), new Vec(10, 1, 5), new Vec(12, 1, 1), new Vec(4, 1, 10),
		new Vec(10, 1, 10)));
	private static final Map<String, Template> BY_LAYOUT = Map.of("piglin_parlor", PARLOR, "high_roller_lounge", LOUNGE);

	private LegacyHouseTables() {}

	private static Template of(String layoutId) {
		if (layoutId == null) {
			return null;
		}
		if (layoutId.startsWith("village_casino_")) {
			return VILLAGE;
		}
		return BY_LAYOUT.get(layoutId);
	}

	/**
	 * Whether world block (x, y, z) is a v0.1 generated game position of the casino with this layout and box. The
	 * template origin is read from the record id ({@link CasinoRecord#idFor}: {@code dim|x|y|z}) so only the real
	 * rotation counts; an id without an origin falls back to every rotation whose box matches.
	 */
	public static boolean generatedAt(String layoutId, Geometry.Box box, String recordId, int x, int y, int z) {
		Template t = of(layoutId);
		if (t == null || box == null) {
			return false;
		}
		Vec fromId = originOf(recordId);
		Vec at = new Vec(x, y, z);
		for (int q = 0; q < 4; q++) {
			Vec origin = fromId != null ? fromId : Geometry.originForMin(box.min(), t.size(), q);
			if (!Geometry.templateBox(origin, t.size(), q).equals(box)) {
				continue;
			}
			for (Vec local : t.tables()) {
				if (Geometry.toWorld(origin, local, q).equals(at)) {
					return true;
				}
			}
		}
		return false;
	}

	static Vec originOf(String recordId) {
		if (recordId == null) {
			return null;
		}
		String[] p = recordId.split("\\|");
		if (p.length < 4) {
			return null;
		}
		try {
			int n = p.length;
			return new Vec(Integer.parseInt(p[n - 3]), Integer.parseInt(p[n - 2]), Integer.parseInt(p[n - 1]));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
