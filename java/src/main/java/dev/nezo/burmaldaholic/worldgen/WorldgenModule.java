package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoStructureTypes;
import dev.nezo.burmaldaholic.worldgen.npc.NpcContent;
import dev.nezo.burmaldaholic.worldgen.npc.NpcInteractions;
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
 *   <li>Casino staff: Croupier, Piglin Dealer, Shulker Croupier ({@code npc/}); their shop / table opening
 *       in {@code NpcInteractions}.</li>
 *   <li>Table presets (Parlor poker, High Roller tables) through core's hook ({@link WorldgenPresets}).</li>
 *   <li>v0.2.0 standalone casinos: structure type {@code burmaldaholic:casino} ({@code structure/CasinoStructure}),
 *       set {@code burmaldaholic:casinos}, tag {@code #burmaldaholic:casino}; pack generated at build time by
 *       {@code logic/PackGen} (docs/architecture/casino-buildings.md).</li>
 *   <li>Ops: {@code /casino worldgen build <village_casino|piglin_parlor|high_roller|casino [style]>}.</li>
 *   <li>Every player: {@code /casino locate} — the nearest casino of the dimension, standalone casinos included
 *       ({@link CasinoLocator}; it replaces the v0.2 draft's op-only {@code /casino worldgen locate}).</li>
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

		NpcContent.register(ctx);
		NpcInteractions.register();
		// INT-X: DL's merchant opens EC-1's Store/Comps counter (EC-SPEC §3) through EC-1's adapter
		dev.nezo.burmaldaholic.core.store.MerchantRole.install(dev.nezo.burmaldaholic.worldgen.npc.HouseNpcRoles::register);
		// EC-2 (comps suite night): "is this block inside a generated casino?" for other modules
		FabricLoader.getInstance().getObjectShare().put("burmaldaholic:worldgen/casino_at",
			(java.util.function.BiFunction<net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos, Boolean>) (level, pos) ->
				dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord.findAt(CasinoIndex.get(level.getServer()).casinos(),
					level.dimension().identifier().toString(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) != null);
		HouseGames.register(); // CB-G house games (+ MT TableProtection install)
		CasinoAttract.register(ctx);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CasinoAttract.forget(handler.getPlayer().getUUID()));
		CoreServices.setTablePresets(new WorldgenPresets());

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			WorldgenRuntime.setServer(server);
			// prewarm (R-CB M6f): the piece registry and the layout index are built here, not on a worldgen thread
			dev.nezo.burmaldaholic.worldgen.logic.CasinoPack.pieces();
			dev.nezo.burmaldaholic.worldgen.logic.Layouts.all();
		});
		// worldgen gate snapshot (R-CB M5): recomputed on the server thread only, read by worldgen threads
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
				WorldgenRuntime.refresh();
			}
		});
		dev.nezo.burmaldaholic.core.mode.CasinoMode.onChange((server, enabled) -> WorldgenRuntime.refresh());
		dev.nezo.burmaldaholic.core.config.ConfigManager.get().addListener(WorldgenRuntime::configChanged);
		ServerTickEvents.END_SERVER_TICK.register(WorldgenRuntime::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WorldgenRuntime.setServer(null);
			dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure.clearCaches();
			CasinoTracker.clear();
			CasinoLocator.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(CasinoTracker::tick);
		CasinoEvents.PLAY_RESOLVED.register((player, result) -> CasinoTracker.onPlayResolved(player));

		CasinoStructureTypes.init();

		CasinoLocator.register();
		CasinoCommands.extend(root -> {
			var build = Commands.literal("build");
			for (CasinoKind kind : CasinoKind.values()) {
				if (kind == CasinoKind.CASINO) {
					var casino = Commands.literal(kind.id()).executes(c -> buildCasino(c.getSource(), null));
					for (String style : PackFiles.STYLE_BIOMES.keySet()) {
						casino.then(Commands.literal(style).executes(c -> buildCasino(c.getSource(), style)));
					}
					build.then(casino);
				} else {
					build.then(Commands.literal(kind.id()).executes(c -> build(c.getSource(), kind)));
				}
			}
			root.then(Commands.literal(ID).then(build));
		});
	}

	/** Style matching the biome at a position (plains → riviera, desert → oasis, cherry grove → cherry, …). */
	static String styleFor(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos at) {
		String biome = level.getBiome(at).unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
		if (biome.contains("cherry")) {
			return "cherry"; // before the village style: cherry_grove would match "grove" -> TAIGA -> alpine
		}
		return switch (VillageStyle.forBiome(biome)) {
			case PLAINS -> "riviera";
			case DESERT -> "oasis";
			case SAVANNA -> "safari";
			case TAIGA -> "alpine";
			case SNOWY -> "ice_palace";
		};
	}

	/**
	 * {@code /casino worldgen build casino [style]}: the standalone casino is a jigsaw structure, so it is built by
	 * dispatching vanilla {@code place structure} (version-proof; same {@code placeInChunk} path as world generation,
	 * so markers and the one-record-per-start context behave as in a generated world). Entrance faces a random way,
	 * start chunk 2 chunks ahead of the player.
	 */
	private static int buildCasino(CommandSourceStack source, String requested) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.build_failed"));
			return 0;
		}
		var level = player.level();
		var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		String style = requested != null ? requested : styleFor(level, player.blockPosition());
		if (registry.getValue(net.minecraft.resources.Identifier.parse(PackFiles.structureId(style))) == null) {
			// the biome's style is not in this pack yet: any available one
			style = PackFiles.STYLE_BIOMES.keySet().stream()
				.filter(s -> registry.getValue(net.minecraft.resources.Identifier.parse(PackFiles.structureId(s))) != null)
				.findFirst().orElse(null);
			if (style == null || requested != null) {
				source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.build_failed"));
				return 0;
			}
		}
		var dir = player.getDirection();
		var at = player.blockPosition().relative(dir, 32);
		String cmd = "place structure " + PackFiles.structureId(style) + " " + at.getX() + " " + at.getY() + " " + at.getZ();
		try {
			int ok = level.getServer().getCommands().getDispatcher().execute(cmd, source.withSuppressedOutput());
			if (ok > 0) {
				source.sendSuccess(() -> Component.translatable("msg.burmaldaholic.worldgen.built",
					Component.translatable(CasinoKind.CASINO.nameKey())), true);
				return 1;
			}
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			Burmaldaholic.LOGGER.info("[worldgen] {} failed: {}", cmd, e.getMessage());
		}
		source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.build_failed"));
		return 0;
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
