package dev.nezo.burmaldaholic.core.table.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where a table's dealer NPC stands (R3: a real dealer at every house table; package DL spawns it).
 *
 * @param main    the table's main block (block entity, menus, networking)
 * @param cell    the block the dealer's feet occupy ({@link TableFootprint#dealerCell}; for a compact 1×1 table the
 *                block directly behind it)
 * @param facing  the direction the dealer faces: toward the table and the players (= the table's FACING)
 * @param game    footprint kind (roulette's dealer stands behind the wheel column, r −1)
 * @param compact true for a compact (SINGLE, legacy) table
 */
public record DealerSpot(BlockPos main, BlockPos cell, Direction facing, TableGame game, boolean compact) {
	/** Feet position: centre of the cell's floor. */
	public Vec3 feet() {
		return Vec3.atBottomCenterOf(cell);
	}

	/** Entity yaw in degrees (body and head) so the dealer looks at the table. */
	public float yaw() {
		return TableFootprint.dealerYaw(facing);
	}
}
