package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the v0.2 village annexes (lane CB-3): one {@link VillageAnnex} piece per {@link VillageStyle},
 * built lazily once (pure, deterministic). {@code CasinoPack} (CB-1) lists {@link #all()} and resolves the
 * annex of a village with {@link #forStyle}.
 */
public final class AnnexPieces {
	private AnnexPieces() {}

	private static Map<VillageStyle, Piece> pieces;

	private static synchronized Map<VillageStyle, Piece> pieces() {
		if (pieces == null) {
			Map<VillageStyle, Piece> m = new EnumMap<>(VillageStyle.class);
			for (VillageStyle v : VillageStyle.values()) {
				m.put(v, VillageAnnex.build(v));
			}
			pieces = Collections.unmodifiableMap(m);
		}
		return pieces;
	}

	/** Every annex, in {@link VillageStyle} order. */
	public static List<Piece> all() {
		return new ArrayList<>(pieces().values());
	}

	public static Piece forStyle(VillageStyle v) {
		return pieces().get(v);
	}
}
