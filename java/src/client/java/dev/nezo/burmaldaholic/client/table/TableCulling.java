package dev.nezo.burmaldaholic.client.table;

import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Frustum culling for table / machine block entity renderers (T-MT perf). Their renderers must render "off screen"
 * (a multi-block table's content reaches past the main block, whose chunk section may be culled while the rest is
 * visible), which skips vanilla's section culling; this test puts it back with the real extent: the whole footprint
 * (every cell, upper rows of machines included) grown by one block for content standing on it (chips, cards, holo
 * board, plates).
 */
public final class TableCulling {
	private TableCulling() {}

	/** Render box of the table or machine whose main / compact block entity is {@code be}. */
	public static AABB renderBox(BlockEntity be) {
		BlockState state = be.getBlockState();
		return TableParts.footprintBox(be.getBlockPos(), state).inflate(1.0);
	}

	/** Whether {@code be}'s render box is in the camera's culling frustum (true when no frustum is set up yet). */
	public static boolean visible(BlockEntity be) {
		Frustum f = Minecraft.getInstance().gameRenderer.mainCamera().getCullFrustum();
		return f == null || f.isVisible(renderBox(be));
	}
}
