package dev.nezo.burmaldaholic.games.extras.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The in-world Lucky Coin toss (extras-pvp.md §1.3): keyframes of a display entity (setters private in vanilla). */
@Mixin(Display.class)
public interface TossDisplayAccessor {
	@Invoker("setTransformation")
	void burmaldaholic$extrasTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void burmaldaholic$extrasInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void burmaldaholic$extrasInterpolationDelay(int ticks);

	@Invoker("setViewRange")
	void burmaldaholic$extrasViewRange(float range);
}
