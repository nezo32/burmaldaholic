package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every v0.2 Overworld casino piece: {@link OverworldStyles#all()} × {@link OverworldCasino.Part} (architecture
 * doc §2). Built lazily once; PURE (no Minecraft types, no {@code Layouts} statics, so {@code CasinoPack} can
 * read it from {@code Layouts.all()} without an init cycle).
 */
public final class OverworldPieces {
	private OverworldPieces() {}

	private static List<Piece> cache;

	public static synchronized List<Piece> all() {
		if (cache == null) {
			List<Piece> out = new ArrayList<>();
			for (Style s : OverworldStyles.all()) {
				out.addAll(OverworldCasino.pieces(s));
			}
			cache = Collections.unmodifiableList(out);
		}
		return cache;
	}

	/** The pieces of one style, in {@link OverworldCasino.Part} order. */
	public static List<Piece> forStyle(Style s) {
		return all().stream().filter(p -> p.id().startsWith("casino/" + s.id() + "/")).toList();
	}
}
