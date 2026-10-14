package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The v0.2 standalone Overworld casino pieces (architecture doc §2) — since the casino redesign (CR-1) the
 * {@link GrandCasino} in its biome {@link GrandCasino.Variant variants}: three jigsaw pieces ({@code entrance},
 * {@code floor}, {@code bar}) per active variant, under the style slots {@code riviera}, {@code oasis},
 * {@code safari}, {@code alpine}, {@code ice_palace}, {@code cherry}. Built lazily once; PURE (no Minecraft types, no {@code Layouts}
 * statics, so {@code CasinoPack} can read it from {@code Layouts.all()} without an init cycle).
 *
 * <p>The pre-redesign styles ({@link OverworldStyles} × {@link OverworldCasino.Part}) are kept as {@link #legacy()} —
 * no longer in the pack — until the phase-3 cleanup deletes them.
 */
public final class OverworldPieces {
	private OverworldPieces() {}

	private static List<Piece> cache;
	private static List<Piece> legacy;

	/** The active standalone pieces: the Grand Casino of every active variant. */
	public static synchronized List<Piece> all() {
		if (cache == null) {
			List<Piece> out = new ArrayList<>();
			for (GrandCasino.Variant v : GrandCasino.Variant.active()) {
				out.addAll(GrandCasino.pieces(v));
			}
			cache = Collections.unmodifiableList(out);
		}
		return cache;
	}

	/** The retired v0.2 styles (Tudor, Oasis, Safari, Alpine, Ice Palace; six pieces each) — not in the pack. */
	public static synchronized List<Piece> legacy() {
		if (legacy == null) {
			List<Piece> out = new ArrayList<>();
			for (Style s : OverworldStyles.all()) {
				out.addAll(OverworldCasino.pieces(s));
			}
			legacy = Collections.unmodifiableList(out);
		}
		return legacy;
	}

	/** The retired pieces of one legacy style, in {@link OverworldCasino.Part} order. */
	public static List<Piece> forStyle(Style s) {
		return legacy().stream().filter(p -> p.id().startsWith("casino/" + s.id() + "/")).toList();
	}
}
