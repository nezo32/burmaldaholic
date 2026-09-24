package dev.nezo.burmaldaholic.bots.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Bot nameplates (BOTS.md §7.2): billboard and view range of a display entity (private in vanilla). */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setBillboardConstraints")
	void burmaldaholic$setBillboardConstraints(Display.BillboardConstraints constraints);

	@Invoker("setViewRange")
	void burmaldaholic$setViewRange(float range);
}
