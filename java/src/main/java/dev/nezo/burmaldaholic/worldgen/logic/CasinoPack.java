package dev.nezo.burmaldaholic.worldgen.logic;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.AnnexPieces;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.OverworldPieces;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of the v0.2 casino pieces (PURE; architecture doc casino-buildings.md §2, §5): the standalone
 * Overworld casinos ({@link OverworldPieces}, lane CB-2/CB-3), the village annexes ({@link AnnexPieces},
 * CB-3) and the Piglin Parlor / High Roller Lounge v2 ({@link OtherworldPieces}, CB-4). Each lane fills its
 * own list; this class only merges them. It must never read {@link Layouts}' registry ({@code Layouts.all()}
 * merges this one — no static-init cycle).
 *
 * <p><b>Placeholders</b> (platform testing only): with {@code -Dburmaldaholic.casinoPlaceholders=true} (Gradle
 * {@code -PcasinoPlaceholders}, set for {@code generateCasinoPack} and every run task) every style without real
 * pieces gets {@link PlaceholderPieces} — plain boxes with the contract's sizes, connectors and all game
 * families — so the structure type, spawn survey and {@code /locate} can be proven before the real buildings
 * land. Never set for a release build.
 */
public final class CasinoPack {
	public static final String PLACEHOLDERS_PROPERTY = "burmaldaholic.casinoPlaceholders";

	private static List<Piece> pieces;
	private static Map<String, Piece> byId;

	private CasinoPack() {}

	public static boolean placeholders() {
		return Boolean.getBoolean(PLACEHOLDERS_PROPERTY);
	}

	/** Every v0.2 piece, in a fixed order (Overworld, annexes, Nether/End, then placeholders). */
	public static synchronized List<Piece> pieces() {
		if (pieces == null) {
			List<Piece> all = new ArrayList<>();
			all.addAll(OverworldPieces.all());
			all.addAll(AnnexPieces.all());
			all.addAll(OtherworldPieces.all());
			if (placeholders()) {
				Set<String> real = new java.util.HashSet<>();
				all.forEach(p -> PackFiles.styleOf(p.id()).ifPresent(real::add));
				for (String style : PackFiles.STYLE_BIOMES.keySet()) {
					if (!real.contains(style)) {
						all.addAll(PlaceholderPieces.style(style));
					}
				}
			}
			Map<String, Piece> m = new LinkedHashMap<>();
			for (Piece p : all) {
				if (!p.id().startsWith("casino/")) {
					throw new IllegalStateException("v0.2 piece id " + p.id() + " must start with casino/ (v0.1 ids are frozen)");
				}
				if (m.put(p.id(), p) != null) {
					throw new IllegalStateException("casino piece " + p.id() + " is defined twice");
				}
			}
			byId = Collections.unmodifiableMap(m);
			pieces = List.copyOf(all);
		}
		return pieces;
	}

	public static List<Layout> layouts() {
		return pieces().stream().map(Piece::layout).toList();
	}

	public static Optional<Piece> piece(String id) {
		pieces();
		return Optional.ofNullable(byId.get(id));
	}

	/** The "Lucky Villager" v2 annex of a village style ({@code casino/annex/<style>}), once lane CB-3 provides it. */
	public static Optional<Layout> villageAnnex(VillageStyle style) {
		return piece("casino/annex/" + style.id()).map(Piece::layout);
	}

	/** Piglin Parlor v2 ({@code casino/parlor}), once lane CB-4 provides it. */
	public static Optional<Layout> parlor() {
		return piece("casino/parlor").map(Piece::layout);
	}

	/** High Roller Lounge v2 ({@code casino/lounge}), once lane CB-4 provides it. */
	public static Optional<Layout> lounge() {
		return piece("casino/lounge").map(Piece::layout);
	}
}
