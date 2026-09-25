package dev.nezo.burmaldaholic.client.render;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Framework for in-world spectator views (docs/architecture/animation.md §2.5; research §2.6). Subclasses
 * (e.g. {@code SlotCabinetRenderer}, {@code RouletteTableRenderer}, {@code CardTableRenderer}) only:
 * <ol>
 *   <li>copy the block entity's last {@code TimelineSeed} + outcome into their state in
 *       {@link #extractAnimated} (rebuilding the game's pure timeline when {@code seq} changes, cached on
 *       the state owner, never per frame);</li>
 *   <li>draw in {@code submit} from the state only, using the LOD the base computed.</li>
 * </ol>
 * The base computes the shared clock ({@link AnimClock#levelMs}) and the LOD tiers, so every BER lands on the
 * same tick as the acting player's screen (fidelity F10). SKELETON: no renderer extends it yet.
 */
public abstract class SpectatorBlockEntityRenderer<T extends BlockEntity, S extends SpectatorRenderState>
		implements BlockEntityRenderer<T, S> {
	/** Detail levels (tables.md §0.7, cards.md §0.8, slots.md §2.6). */
	public enum Lod {
		/** Tweened motion, text, particles. */
		FULL,
		/** Settled state, no tweens (animations snap to their current beat's terminal frame). */
		SETTLED,
		/** Static face / idle pose only. */
		STATIC
	}

	/** Blocks within which motion is tweened (default 20; slots 24, roulette 32). */
	protected int animateRadius() {
		return 20;
	}

	@Override
	public int getViewDistance() {
		return 32;
	}

	@Override
	public void extractRenderState(T be, S state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(be, state, crumbling);
		state.levelMs = AnimClock.levelMs(partialTick);
		state.distSq = cameraPos.distanceToSqr(Vec3.atCenterOf(be.getBlockPos()));
		int r = animateRadius();
		int view = getViewDistance();
		state.lod = state.distSq <= (double) r * r ? Lod.FULL : state.distSq <= (double) view * view ? Lod.SETTLED : Lod.STATIC;
		extractAnimated(be, state, partialTick);
	}

	/** Copies the game-specific sync data into {@code state}. */
	protected abstract void extractAnimated(T be, S state, float partialTick);
}
