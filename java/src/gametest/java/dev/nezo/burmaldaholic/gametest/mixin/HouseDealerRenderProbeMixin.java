package dev.nezo.burmaldaholic.gametest.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.nezo.burmaldaholic.gametest.worldgen.DealerRenderProbe;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealer;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client GameTests only (DL tester): counts calls of the house dealer renderers' per-frame pose extraction. */
@Mixin(targets = "dev.nezo.burmaldaholic.worldgen.client.HouseDealerRenderers")
public abstract class HouseDealerRenderProbeMixin {
	@Inject(method = "pose", at = @At("HEAD"))
	private static void burmaldaholic$probe(CallbackInfo ci, @Local(argsOnly = true) HouseDealer dealer) {
		DealerRenderProbe.hit(BuiltInRegistries.ENTITY_TYPE.getKey(dealer.getType()).toString());
	}
}
