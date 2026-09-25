package dev.nezo.burmaldaholic.bots.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Bot nameplates (BOTS.md §7.2, global.md §4.12): billboard, view range and the interpolated transformation (join /
 * leave scale, "acted" pulse) of a display entity (private in vanilla).
 */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setBillboardConstraints")
	void burmaldaholic$setBillboardConstraints(Display.BillboardConstraints constraints);

	@Invoker("setViewRange")
	void burmaldaholic$setViewRange(float range);

	@Invoker("setTransformation")
	void burmaldaholic$setTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void burmaldaholic$setTransformationInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void burmaldaholic$setTransformationInterpolationDelay(int ticks);
}
