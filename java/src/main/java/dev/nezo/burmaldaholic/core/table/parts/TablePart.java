package dev.nezo.burmaldaholic.core.table.parts;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * Which cell of a multi-block casino table a block is (MT-DESIGN §1/§2). Offsets are in the table's
 * local frame: {@link #r()} = cells to the players' right, {@link #f()} = cells toward the players
 * (+FACING). The main block (block entity, menus, sync) sits at the FRONT CENTRE (0,0).
 * {@link #SINGLE} is the legacy / compact 1×1 table and the block's DEFAULT state, so old saves and
 * structure palettes without the property decode as compact tables.
 *
 * <p>MT-3: upright machines (slots, wheel, Plinko, cashier) use the same enum with {@link #u()} = cells up;
 * each block's {@code part} property only lists the values of its own footprint (tables: the first seven).
 */
public enum TablePart implements StringRepresentable {
	SINGLE(0, 0),
	MAIN(0, 0),
	LEFT(-1, 0),
	RIGHT(1, 0),
	BACK_LEFT(-1, -1),
	BACK(0, -1),
	BACK_RIGHT(1, -1),
	/** Upright machines: the row above the main block. */
	UPPER_LEFT(-1, 1, 0),
	UPPER(0, 1, 0),
	UPPER_RIGHT(1, 1, 0),
	/** Upright machines: two rows above the main block. */
	TOP_LEFT(-1, 2, 0),
	TOP(0, 2, 0),
	TOP_RIGHT(1, 2, 0);

	private final int r;
	private final int u;
	private final int f;
	private final String id;

	TablePart(int r, int f) {
		this(r, 0, f);
	}

	TablePart(int r, int u, int f) {
		this.r = r;
		this.u = u;
		this.f = f;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	/** Cells up from the main block (0 for tables). */
	public int u() {
		return u;
	}

	/** Cells to the players' right of the main block. */
	public int r() {
		return r;
	}

	/** Cells toward the players (+FACING) from the main block (0 or -1). */
	public int f() {
		return f;
	}

	/** Holds the block entity: {@link #MAIN} or {@link #SINGLE}. */
	public boolean isController() {
		return this == MAIN || this == SINGLE;
	}

	/** Part of a full (unfolded) table — everything except {@link #SINGLE}. */
	public boolean isMultiBlock() {
		return this != SINGLE;
	}

	/** Mirror across the table's front-back axis: LEFT↔RIGHT, BACK_LEFT↔BACK_RIGHT. */
	public TablePart mirrored() {
		return switch (this) {
			case LEFT -> RIGHT;
			case RIGHT -> LEFT;
			case BACK_LEFT -> BACK_RIGHT;
			case BACK_RIGHT -> BACK_LEFT;
			case UPPER_LEFT -> UPPER_RIGHT;
			case UPPER_RIGHT -> UPPER_LEFT;
			case TOP_LEFT -> TOP_RIGHT;
			case TOP_RIGHT -> TOP_LEFT;
			default -> this;
		};
	}

	/** Part at local cell (r, f) of a 3×2 footprint, or null outside it. (0,0) → {@link #MAIN}. */
	public static @Nullable TablePart at(int r, int f) {
		return at(r, 0, f);
	}

	/** Part at local cell (r, u, f), or null. (0,0,0) → {@link #MAIN}. */
	public static @Nullable TablePart at(int r, int u, int f) {
		for (TablePart p : values()) {
			if (p != SINGLE && p.r == r && p.u == u && p.f == f) {
				return p;
			}
		}
		return null;
	}

	public static @Nullable TablePart byId(String id) {
		for (TablePart p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	@Override
	public String toString() {
		return id;
	}
}
