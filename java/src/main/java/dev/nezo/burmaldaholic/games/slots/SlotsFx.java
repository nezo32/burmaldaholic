package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetFxPlan;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * World FX of the slot cabinets (slots.md §5.3, JS15): the module-owned particle types ({@code ember_burst},
 * {@code void_motes}; Java ids {@code burmaldaholic:slots/…} because the asset ownership check requires the module
 * folder) and the hook through which every CLIENT plays a published {@link CabinetSync}'s {@link CabinetFxPlan} at
 * {@code startTick + ceil(atMs / 50)}: the particles burst when the cabinet reels every spectator sees reach the beat.
 *
 * <p>The events are played client-side (review J-L1/J-L10): each viewer's Effects volume, reduce-motion and flashes-off
 * settings apply to the positional sounds, the particle counts and the firework flashes. The client module installs
 * {@link #clientSync}; the block entity calls it when a new sync arrives in its update tag.
 */
public final class SlotsFx {
	/** Registry paths (particle JSON at {@code particles/slots/<name>.json}). */
	public static final String EMBER_BURST = "slots/ember_burst";
	public static final String VOID_MOTES = "slots/void_motes";

	private static @Nullable SimpleParticleType emberBurst;
	private static @Nullable SimpleParticleType voidMotes;

	/** Client hook: a cabinet received a new sync (client thread). No-op on a dedicated server. */
	public static volatile Consumer<SlotMachineBlockEntity> clientSync = be -> {};

	private SlotsFx() {}

	/** Called once from {@code SlotsModule.register}. */
	public static void register(ModuleContext ctx) {
		emberBurst = Registry.register(BuiltInRegistries.PARTICLE_TYPE, ctx.id(EMBER_BURST), FabricParticleTypes.simple());
		voidMotes = Registry.register(BuiltInRegistries.PARTICLE_TYPE, ctx.id(VOID_MOTES), FabricParticleTypes.simple());
	}

	public static @Nullable SimpleParticleType emberBurst() {
		return emberBurst;
	}

	public static @Nullable SimpleParticleType voidMotes() {
		return voidMotes;
	}

	/** Point on the cabinet front at height {@code y} (the reel face is the upper block, slots.md §5.1). */
	public static Vec3 face(BlockPos pos, Direction facing, double y) {
		return new Vec3(pos.getX() + 0.5 + facing.getStepX() * 0.55, pos.getY() + y, pos.getZ() + 0.5 + facing.getStepZ() * 0.55);
	}

	/** Centre of reel {@code r} on the face (reel 0 on the viewer's left). */
	public static Vec3 reel(BlockPos pos, Direction facing, int r) {
		Direction left = facing.getClockWise();
		double off = (r - 2) * 0.16;
		Vec3 f = face(pos, facing, 1.45);
		return f.add(left.getStepX() * -off, 0, left.getStepZ() * -off);
	}
}
