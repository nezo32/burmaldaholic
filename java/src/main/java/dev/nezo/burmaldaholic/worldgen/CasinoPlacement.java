package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.jspecify.annotations.Nullable;

/**
 * "One casino per StructureStart" (architecture doc §6.3, D6). While a structure start places its pieces in a
 * chunk ({@code StructureStartMixin} around {@code StructureStart.placeInChunk}), the casino pieces of that start
 * share one context: the casino id of the FIRST casino piece (its template origin — the start piece for the
 * standalone casino, the only piece for an annex, so v0.1 ids are unchanged) and the union box of all casino
 * pieces. {@link CasinoMarkers} uses it so every marker of a multi-piece casino registers into the same record.
 * Every chunk computes the same context from the same saved pieces, so ids are stable across chunks and restarts.
 * Without a context (direct template placement by {@link CasinoBuilder}) markers keep the per-piece behaviour.
 */
public final class CasinoPlacement {
	/** The casino a placement belongs to. */
	public record Context(String casinoId, String layoutId, CasinoKind kind, Geometry.Box box) {}

	private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

	private CasinoPlacement() {}

	public static @Nullable Context current() {
		return CURRENT.get();
	}

	/** The casino context of a start's pieces; null when none of them is a casino piece. */
	public static @Nullable Context of(String dimension, List<StructurePiece> pieces) {
		Context ctx = null;
		BoundingBox union = null;
		for (StructurePiece piece : pieces) {
			if (!(piece instanceof PoolElementStructurePiece p)) {
				continue;
			}
			Optional<Layout> layout = CasinoElements.layoutOf(p.getElement());
			if (layout.isEmpty()) {
				continue;
			}
			BoundingBox b = p.getBoundingBox();
			if (ctx == null) {
				BlockPos o = p.getPosition();
				ctx = new Context(CasinoRecord.idFor(dimension, o.getX(), o.getY(), o.getZ()), layout.get().id(), layout.get().kind(), null);
				union = b;
			} else {
				union = new BoundingBox(Math.min(union.minX(), b.minX()), Math.min(union.minY(), b.minY()), Math.min(union.minZ(), b.minZ()),
					Math.max(union.maxX(), b.maxX()), Math.max(union.maxY(), b.maxY()), Math.max(union.maxZ(), b.maxZ()));
			}
		}
		if (ctx == null) {
			return null;
		}
		return new Context(ctx.casinoId(), ctx.layoutId(), ctx.kind(),
			new Geometry.Box(union.minX(), union.minY(), union.minZ(), union.maxX(), union.maxY(), union.maxZ()));
	}

	/** Runs {@code body} with {@code ctx} as the current context (restores the previous one afterwards). */
	public static void with(@Nullable Context ctx, Runnable body) {
		if (ctx == null) {
			body.run();
			return;
		}
		Context previous = CURRENT.get();
		CURRENT.set(ctx);
		try {
			body.run();
		} finally {
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}
}
