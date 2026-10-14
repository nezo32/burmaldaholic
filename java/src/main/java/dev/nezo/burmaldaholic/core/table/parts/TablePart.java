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
 */
public enum TablePart implements StringRepresentable {
	SINGLE(0, 0),
	MAIN(0, 0),
	LEFT(-1, 0),
	RIGHT(1, 0),
	BACK_LEFT(-1, -1),
	BACK(0, -1),
	BACK_RIGHT(1, -1);

	private final int r;
	private final int f;
	private final String id;

	TablePart(int r, int f) {
		this.r = r;
		this.f = f;
		this.id = name().toLowerCase(Locale.ROOT);
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
			default -> this;
		};
	}

	/** Part at local cell (r, f) of a 3×2 footprint, or null outside it. (0,0) → {@link #MAIN}. */
	public static @Nullable TablePart at(int r, int f) {
		for (TablePart p : values()) {
			if (p != SINGLE && p.r == r && p.f == f) {
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
