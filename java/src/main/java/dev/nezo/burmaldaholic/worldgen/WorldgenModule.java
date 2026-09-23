package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Casino structures in world generation (GAME_DESIGN §16).
 *
 * <ul>
 *   <li>Templates + pools ship in the built-in data pack {@code burmaldaholic:casinos}
 *       ({@code resources/resourcepacks/casinos}, enabled by default for new worlds); files are
 *       generated from {@code logic/Layouts} by {@code TemplateExportTest}.</li>
 *   <li>Village casinos: extra element of the five vanilla village street-end ({@code terminators}) pools
 *       ({@link VillageCasinos}); Piglin Parlor appended to bastions and High Roller Lounge on End City
 *       towers ({@link CasinoStructures}).</li>
 *   <li>NPCs, loot chests and casino registration run from data markers ({@link CasinoMarkers});
 *       upkeep (welcome title, advancements, NPC respawn) in {@link CasinoTracker}.</li>
 *   <li>Ops: {@code /casino worldgen build <village_casino|piglin_parlor|high_roller>}.</li>
 * </ul>
 * New casinos generate only while casino mode and {@code worldgen.enabled} are on; the tables inside
 * are ordinary (house) tables of their modules and follow casino mode themselves.
 */
public final class WorldgenModule implements CasinoModule {
	public static final String ID = "worldgen";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		FabricLoader.getInstance().getModContainer(Burmaldaholic.MOD_ID).ifPresent(mod ->
			ResourceLoader.registerBuiltinPack(ctx.id("casinos"), mod, Component.translatable("pack.burmaldaholic.casinos"),
				PackActivationType.DEFAULT_ENABLED));

		ServerLifecycleEvents.SERVER_STARTING.register(WorldgenRuntime::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WorldgenRuntime.setServer(null);
			CasinoTracker.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(CasinoTracker::tick);
		CasinoEvents.PLAY_RESOLVED.register((player, result) -> CasinoTracker.onPlayResolved(player));

		CasinoCommands.extend(root -> {
			var build = Commands.literal("build");
			for (CasinoKind kind : CasinoKind.values()) {
				build.then(Commands.literal(kind.id()).executes(c -> build(c.getSource(), kind)));
			}
			root.then(Commands.literal(ID).then(build));
		});
	}

	private static int build(CommandSourceStack source, CasinoKind kind) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.build_failed"));
			return 0;
		}
		var level = player.level();
		Layout layout = CasinoBuilder.layoutFor(level, kind, player.blockPosition());
		Facing look = Facing.byId(player.getDirection().getName());
		Optional<BoundingBox> built = CasinoBuilder.buildInFront(level, layout, player.blockPosition(), look);
		if (built.isEmpty()) {
			source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.build_failed"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("msg.burmaldaholic.worldgen.built", Component.translatable(kind.nameKey())), true);
		return 1;
	}
}
