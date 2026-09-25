package dev.nezo.burmaldaholic.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Base render state of every in-world spectator view (slot cabinets, roulette wheel, card tables, wheel of
 * fortune, Plinko). Filled on the extract pass; {@code submit} must only read it.
 */
public class SpectatorRenderState extends BlockEntityRenderState {
	/** Level time in ms including the partial tick (shared clock). */
	public double levelMs;
	/** Squared distance camera → block centre. */
	public double distSq;
	/** Level of detail for this frame. */
	public SpectatorBlockEntityRenderer.Lod lod = SpectatorBlockEntityRenderer.Lod.STATIC;
}
