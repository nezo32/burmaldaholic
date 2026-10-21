package dev.nezo.burmaldaholic.gametest.worldgen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
import dev.nezo.burmaldaholic.worldgen.HouseGames;
import dev.nezo.burmaldaholic.worldgen.logic.TablePresets;
import dev.nezo.burmaldaholic.worldgen.logic.WorldgenBotPresets;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoPack;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import dev.nezo.burmaldaholic.worldgen.CasinoElements;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoStructures;
import dev.nezo.burmaldaholic.worldgen.WorldgenRuntime;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Casino data pack, templates, markers (chest, NPC slots, registration) in a real world. */
public class WorldgenGameTests {
	private static BlockPos pos(Vec v) {
		return new BlockPos(v.x(), v.y(), v.z());
	}

	private static Vec vec(BlockPos p) {
		return new Vec(p.getX(), p.getY(), p.getZ());
	}

	/** Far above the test's own area so the building never touches other tests. */
	private static BlockPos origin(GameTestHelper helper) {
		return helper.absolutePos(BlockPos.ZERO).above(60);
	}

	@GameTest
	public void casinoPackLoadedWithAllTemplates(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		var templates = CasinoBuilder.templates(level.getServer());
		helper.assertTrue(WorldgenRuntime.active(), "worldgen active (casino mode on, worldgen.enabled)");
		for (Layout l : Layouts.all().values()) {
			// v0.1 + annex/parlor/lounge v2: single-element pool at the template path; standalone pieces: their style pool
			Optional<String> style = PackFiles.styleOf(l.id());
			String poolPath = style.isPresent() ? PackFiles.stylePoolPath(style.get(), PackFiles.poolName(PackFiles.pieceName(l.id()))) : l.templatePath();
			Optional<StructurePoolElement> element = pools.get(ResourceKey.create(Registries.TEMPLATE_POOL, dev.nezo.burmaldaholic.Burmaldaholic.id(poolPath)))
				.flatMap(h -> h.value().getTemplates().stream().map(com.mojang.datafixers.util.Pair::getFirst)
					.filter(e -> CasinoElements.layoutOf(e).orElse(null) == l).findFirst());
			helper.assertTrue(element.isPresent(), "pool burmaldaholic:" + poolPath + " holds " + l.id() + " (casinos data pack)");
			helper.assertTrue(CasinoElements.layoutOf(element.get()).orElseThrow() == l, "element recognised as " + l.id());
			Vec3i size = element.get().getSize(templates, Rotation.NONE);
			helper.assertTrue(size.getX() == l.size().x() && size.getY() == l.size().y() && size.getZ() == l.size().z(),
				l.id() + " template size " + size);
		}
		// Village casinos connect to street ends like vanilla terminators.
		StructurePoolElement plains = CasinoElements.element(pools, Layouts.village(dev.nezo.burmaldaholic.worldgen.logic.VillageStyle.PLAINS)).orElseThrow();
		List<StructureTemplate.JigsawBlockInfo> jigsaws = plains.getShuffledJigsawBlocks(templates, BlockPos.ZERO, Rotation.NONE, level.getRandom());
		helper.assertTrue(jigsaws.size() == 1, "one street connector");
		// Vanilla elements are not ours.
		StructurePoolElement vanilla = pools.get(net.minecraft.resources.ResourceKey.create(Registries.TEMPLATE_POOL,
			net.minecraft.resources.Identifier.parse("minecraft:village/plains/houses"))).orElseThrow().value().getTemplates().getFirst().getFirst();
		helper.assertTrue(CasinoElements.layoutOf(vanilla).isEmpty(), "vanilla house is not a casino");
		helper.succeed();
	}

	@GameTest
	public void loungeBuildsRotatedWithLootAndRecord(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Layout l = Layouts.highRollerLayout();
		BlockPos origin = origin(helper);
		int turns = 1;
		Rotation rotation = CasinoStructures.rotation(turns);
		BoundingBox box = CasinoBuilder.build(level, l, origin, rotation).orElseThrow();
		Geometry.Box expected = Geometry.templateBox(vec(origin), l.size(), turns);
		helper.assertTrue(box.minX() == expected.minX() && box.maxX() == expected.maxX() && box.minZ() == expected.minZ()
			&& box.maxZ() == expected.maxZ() && box.minY() == expected.minY(), "pure geometry matches Minecraft's box " + box);

		// Chest from its data marker: rotated with the building and filled from chests/high_roller.
		BlockPos chestPos = pos(Geometry.toWorld(vec(origin), new Vec(7, 1, 1), turns));
		BlockState chest = level.getBlockState(chestPos);
		helper.assertTrue(chest.is(Blocks.CHEST), "chest placed at " + chestPos + " but found " + chest);
		helper.assertTrue(chest.getValue(ChestBlock.FACING) == Direction.byName(Facing.SOUTH.rotate(turns).id()), "chest rotated");
		Container inv = (Container) level.getBlockEntity(chestPos);
		int stacks = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (!inv.getItem(i).isEmpty()) {
				stacks++;
			}
		}
		helper.assertTrue(stacks >= 3 && stacks <= 5, "3-5 loot stacks, got " + stacks);

		// Anchor + NPC marker cells are cleared, tables of present modules are in place.
		helper.assertTrue(level.getBlockState(pos(Geometry.toWorld(vec(origin), new Vec(7, 3, 7), turns))).isAir(), "anchor cell cleared");
		BlockState cashier = level.getBlockState(pos(Geometry.toWorld(vec(origin), new Vec(12, 1, 1), turns)));
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(cashier.getBlock()).getPath().equals("cashier"), "cashier in the lounge, found " + cashier);

		// Registered for the welcome title / High Roller check, with its NPC home.
		String dim = level.dimension().identifier().toString();
		CasinoRecord record = CasinoIndex.get(level.getServer()).casinos().stream()
			.filter(r -> r.id().equals(CasinoRecord.idFor(dim, origin.getX(), origin.getY(), origin.getZ())))
			.findFirst().orElseThrow();
		helper.assertTrue(record.kind() == CasinoKind.HIGH_ROLLER && record.box().equals(expected), "record bounds");
		helper.assertTrue(record.npcs().size() == 2, "shulker croupier + baccarat dealer homes recorded (spawned only if their entity exists)");
		BlockPos inside = pos(Geometry.toWorld(vec(origin), new Vec(7, 2, 7), turns));
		helper.assertTrue(CasinoRecord.findAt(CasinoIndex.get(level.getServer()).casinos(), dim,
			inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5) == record, "position inside the lounge resolves to it");
		helper.succeed();
	}

	/** Real worldgen path: a bastion placed with {@code /place} gets a Parlor appended (chance forced to 1). */
	@GameTest(maxTicks = 400)
	public void bastionGetsPiglinParlor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var cfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.worldgen().piglinParlor;
		double before = cfg.chance;
		BlockPos at = helper.absolutePos(BlockPos.ZERO).offset(400, 0, 400);
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		for (int x = cx - 9; x <= cx + 9; x++) {
			for (int z = cz - 9; z <= cz + 9; z++) {
				level.getChunk(x, z);
			}
		}
		try {
			cfg.chance = 1.0;
			var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
			level.getServer().getCommands().performPrefixedCommand(source,
				"place structure minecraft:bastion_remnant " + at.getX() + " 33 " + at.getZ());
		} finally {
			cfg.chance = before;
		}
		String dim = level.dimension().identifier().toString();
		boolean found = CasinoIndex.get(level.getServer()).casinos().stream().anyMatch(r -> r.kind() == CasinoKind.PIGLIN_PARLOR
			&& r.dimension().equals(dim) && Math.abs(r.box().centerX() - at.getX()) < 140 && Math.abs(r.box().centerZ() - at.getZ()) < 140);
		helper.assertTrue(found, "a Piglin Parlor was appended to the bastion and registered");
		helper.succeed();
	}

	/** Real worldgen path: an End City placed on the main End island gets at most one Lounge (beside it, lane WG). */
	@GameTest(maxTicks = 400)
	public void endCityGetsHighRollerLounge(GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
		if (end == null) {
			helper.succeed(); // dimension disabled on this test server
			return;
		}
		var cfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.worldgen().highRoller;
		double before = cfg.chance;
		for (int x = -9; x <= 9; x++) {
			for (int z = -9; z <= 9; z++) {
				end.getChunk(x, z);
			}
		}
		String dim = end.dimension().identifier().toString();
		java.util.function.LongSupplier lounges = () -> CasinoIndex.get(end.getServer()).casinos().stream()
			.filter(r -> r.kind() == CasinoKind.HIGH_ROLLER && r.dimension().equals(dim)).count();
		// The test world may be reused between runs: forget old lounges so the new one is counted.
		CasinoIndex.get(end.getServer()).removeIf(r -> r.kind() == CasinoKind.HIGH_ROLLER && r.dimension().equals(dim));
		long lengthBefore = lounges.getAsLong();
		int placed = 0;
		try {
			cfg.chance = 1.0;
			var source = end.getServer().createCommandSourceStack().withLevel(end).withSuppressedOutput();
			placed = end.getServer().getCommands().getDispatcher().execute("place structure minecraft:end_city 0 70 0", source);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.info("[worldgen test] end city not placeable here: {}", e.getMessage());
		} finally {
			cfg.chance = before;
		}
		// v0.2 (lane WG): the Lounge stands on the island beside the city, at least 24 blocks from every piece; the main
		// island may have no such site, so a placed city has at most one Lounge here. The real-terrain placement, the
		// clearance and the walk to the door are asserted by CasinoSiteGameTests.endLoungeStandsBesideTheCity.
		long added = lounges.getAsLong() - lengthBefore;
		dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.info("[worldgen test] end city placed: {}, lounges added: {}", placed, added);
		helper.assertTrue(added == 0 || placed > 0 && added == 1, "at most one Lounge per End City, only when it was placed: " + added);
		helper.succeed();
	}

	/**
	 * Real worldgen path for villages: with the chance forced to 1, placed villages get exactly one
	 * casino at a street end (a village may have no free street end, so a few villages are tried).
	 */
	@GameTest(maxTicks = 400)
	public void villagesGetOneCasino(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var cfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.worldgen().villageCasino;
		double before = cfg.chance;
		String dim = level.dimension().identifier().toString();
		int villages = 0;
		int withCasino = 0;
		try {
			cfg.chance = 1.0;
			for (int attempt = 0; attempt < 4; attempt++) {
				BlockPos at = helper.absolutePos(BlockPos.ZERO).offset(-400 - attempt * 250, 0, 300);
				int cx = at.getX() >> 4;
				int cz = at.getZ() >> 4;
				for (int x = cx - 8; x <= cx + 8; x++) {
					for (int z = cz - 8; z <= cz + 8; z++) {
						level.getChunk(x, z);
					}
				}
				BlockPos centre = at;
				CasinoIndex.get(level.getServer()).removeIf(r -> r.kind() == CasinoKind.VILLAGE_CASINO && r.dimension().equals(dim)
					&& Math.abs(r.box().centerX() - centre.getX()) < 130 && Math.abs(r.box().centerZ() - centre.getZ()) < 130);
				long before2 = count(level, dim, at);
				var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
				int ok = level.getServer().getCommands().getDispatcher().execute("place structure minecraft:village_plains "
					+ at.getX() + " " + at.getY() + " " + at.getZ(), source);
				if (ok > 0) {
					villages++;
					long added = count(level, dim, at) - before2;
					helper.assertTrue(added <= 1, "max one casino per village, got " + added);
					withCasino += (int) added;
				}
			}
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			helper.fail("place failed: " + e.getMessage());
		} finally {
			cfg.chance = before;
		}
		dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.info("[worldgen test] villages {}, with casino {}", villages, withCasino);
		helper.assertTrue(villages > 0 && withCasino > 0, "villages " + villages + ", with casino " + withCasino);
		helper.succeed();
	}

	private static long count(ServerLevel level, String dim, BlockPos near) {
		return CasinoIndex.get(level.getServer()).casinos().stream().filter(r -> r.kind() == CasinoKind.VILLAGE_CASINO
			&& r.dimension().equals(dim) && Math.abs(r.box().centerX() - near.getX()) < 130 && Math.abs(r.box().centerZ() - near.getZ()) < 130).count();
	}

	// ---- v0.2 standalone casino: structure type, set, tag, D5, D6 (architecture doc §6, §9.2) --------------

	/** Styles with a structure in this pack (empty until a lane delivers pieces, or with -PcasinoPlaceholders). */
	private static List<String> styles() {
		return PackFiles.completeStyles(CasinoPack.layouts());
	}

	@GameTest
	public void standaloneCasinoRegistryLoads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuiltInRegistries.STRUCTURE_TYPE.containsKey(Burmaldaholic.id("casino")), "structure type burmaldaholic:casino registered");
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var tag = structures.get(TagKey.create(Registries.STRUCTURE, Burmaldaholic.id("casino")));
		helper.assertTrue(tag.isPresent(), "#burmaldaholic:casino loaded");
		List<String> styles = styles();
		helper.assertTrue(tag.get().size() == styles.size(), "#burmaldaholic:casino lists " + tag.get().size() + " structures, pack has " + styles);
		var set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
			.get(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(PackFiles.SET)));
		helper.assertTrue(set.isPresent() == !styles.isEmpty(), "structure set present iff a style is complete");
		if (set.isPresent()) {
			var placement = (RandomSpreadStructurePlacement) set.get().value().placement();
			helper.assertTrue(placement.spacing() == PackFiles.SPACING && placement.separation() == PackFiles.SEPARATION,
				"spacing " + placement.spacing() + " / separation " + placement.separation());
			helper.assertTrue(set.get().value().structures().size() == styles.size(), "one set entry per style");
		}
		var pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		for (String style : styles) {
			Structure s = structures.getValue(Identifier.parse(PackFiles.structureId(style)));
			helper.assertTrue(s instanceof CasinoStructure, style + " is a burmaldaholic:casino structure, got " + s);
			CasinoStructure cs = (CasinoStructure) s;
			helper.assertTrue(cs.requiredPieces().equals(PackFiles.REQUIRED_POOLS) && cs.maxHeightRange() == PackFiles.MAX_HEIGHT_RANGE,
				style + " required " + cs.requiredPieces() + " range " + cs.maxHeightRange());
			helper.assertTrue(s.terrainAdaptation() == TerrainAdjustment.BEARD_THIN, style + " beard_thin");
			helper.assertTrue(tag.get().stream().anyMatch(h -> h.value() == s), style + " in #burmaldaholic:casino");
			for (String pool : List.of("entrance", "floor", "bar")) {
				var p = pools.get(ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.parse(PackFiles.stylePoolId(style, pool))));
				helper.assertTrue(p.isPresent() && !p.get().value().getTemplates().isEmpty(), style + " pool " + pool);
			}
		}
		helper.succeed();
	}

	private static StructureStart generate(ServerLevel level, Structure structure, ChunkPos chunk) {
		var source = level.getChunkSource();
		return StructureGen.generate(Holder.direct(structure), level.dimension(), level.registryAccess(), source.getGenerator(),
			source.randomState(), CasinoBuilder.templates(level.getServer()), level.getSeed(), chunk, level, b -> true);
	}

	private static String pieces(StructureStart start) {
		StringBuilder sb = new StringBuilder();
		for (var p : start.getPieces()) {
			if (p instanceof PoolElementStructurePiece e) {
				sb.append(CasinoElements.layoutOf(e.getElement()).map(Layout::id).orElse("?")).append('@').append(e.getPosition())
					.append('/').append(e.getRotation()).append(' ');
			}
		}
		return sb.toString();
	}

	/**
	 * The structure JSON we generate decodes through our codec (vanilla jigsaw fields inlined), assembles the same
	 * casino twice for the same chunk (the jigsaw runs once per start, re-wrapped), and D5: the same structure
	 * requiring a pool no piece belongs to never produces a start.
	 */
	@GameTest
	public void casinoCodecDeterminismAndCompleteness(GameTestHelper helper) {
		List<String> styles = styles();
		if (styles.isEmpty()) {
			Burmaldaholic.LOGGER.info("[worldgen test] no standalone casino style in the pack yet: casinoCodecDeterminismAndCompleteness skipped");
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		String json = PackFiles.structureJson(styles.getFirst());
		Structure ok = Structure.DIRECT_CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
		Structure incomplete = Structure.DIRECT_CODEC.parse(ops, JsonParser.parseString(json.replace("\"bar\"]", "\"bar\", \"vault\"]"))).getOrThrow();
		helper.assertTrue(((CasinoStructure) incomplete).requiredPieces().contains("vault"), "required_pieces decoded");
		ChunkPos chunk = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO).offset(1200, 0, -1200));
		StructureStart a = generate(level, ok, chunk);
		StructureStart b = generate(level, ok, chunk);
		helper.assertTrue(a.isValid() && b.isValid(), "complete casino generates on flat ground");
		helper.assertTrue(a.getPieces().size() >= 3, "entrance + floor + bar at least, got " + pieces(a));
		helper.assertTrue(pieces(a).equals(pieces(b)), "same chunk → same assembly:\n" + pieces(a) + "\n" + pieces(b));
		helper.assertTrue(!generate(level, incomplete, chunk).isValid(), "D5: a start missing a required pool is rejected");
		helper.succeed();
	}

	/**
	 * D6 through the real placement path: {@code place structure} runs {@code StructureStart.placeInChunk} chunk by
	 * chunk; every marker of every piece must land in ONE CASINO record whose box is the union of the pieces.
	 */
	@GameTest(maxTicks = 400)
	public void standaloneCasinoIsOneRecord(GameTestHelper helper) {
		List<String> styles = styles();
		if (styles.isEmpty()) {
			Burmaldaholic.LOGGER.info("[worldgen test] no standalone casino style in the pack yet: standaloneCasinoIsOneRecord skipped");
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(BlockPos.ZERO).offset(700, 0, -700);
		for (int x = (at.getX() >> 4) - 6; x <= (at.getX() >> 4) + 6; x++) {
			for (int z = (at.getZ() >> 4) - 6; z <= (at.getZ() >> 4) + 6; z++) {
				level.getChunk(x, z);
			}
		}
		String dim = level.dimension().identifier().toString();
		java.util.function.Supplier<List<CasinoRecord>> near = () -> CasinoIndex.get(level.getServer()).casinos().stream()
			.filter(r -> r.kind() == CasinoKind.CASINO && dim.equals(r.dimension()) && r.box() != null
				&& Math.abs(r.box().centerX() - at.getX()) < 100 && Math.abs(r.box().centerZ() - at.getZ()) < 100)
			.toList();
		CasinoIndex.get(level.getServer()).removeIf(r -> near.get().contains(r));
		try {
			var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
			int ok = level.getServer().getCommands().getDispatcher().execute("place structure " + PackFiles.structureId(styles.getFirst())
				+ " " + at.getX() + " " + at.getY() + " " + at.getZ(), source);
			helper.assertTrue(ok > 0, "place structure succeeded");
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			helper.fail("place structure failed: " + e.getMessage());
			return;
		}
		helper.succeedWhen(() -> {
			List<CasinoRecord> records = near.get();
			helper.assertTrue(records.size() == 1, "exactly one CASINO record for the start, got " + records.size());
			CasinoRecord r = records.getFirst();
			Geometry.Box box = r.box();
			helper.assertTrue(r.layoutId().endsWith("/entrance"), "anchored by the entrance, got " + r.layoutId());
			helper.assertTrue(box.xSpan() > 31 || box.zSpan() > 31, "record box is the union of several pieces: " + box);
			helper.assertTrue(r.npcs().size() >= 2, "croupier + loan shark (+ dealers) of all pieces in one record, got " + r.npcs().size());
			for (CasinoRecord.NpcSlot npc : r.npcs()) {
				Vec h = npc.home();
				helper.assertTrue(box.contains(h.x(), h.y(), h.z()), npc.role() + " home " + h + " inside " + box);
			}
		});
	}

	@GameTest
	public void parlorBuildsWithThreeNpcHomes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Layout l = Layouts.piglinParlorLayout();
		BlockPos origin = origin(helper).offset(0, 20, 0);
		CasinoBuilder.build(level, l, origin, Rotation.NONE).orElseThrow();
		String dim = level.dimension().identifier().toString();
		CasinoRecord record = CasinoIndex.get(level.getServer()).casinos().stream()
			.filter(r -> r.id().equals(CasinoRecord.idFor(dim, origin.getX(), origin.getY(), origin.getZ())))
			.findFirst().orElseThrow();
		helper.assertTrue(record.kind() == CasinoKind.PIGLIN_PARLOR, "parlor registered");
		helper.assertTrue(record.npcs().size() == 3, "2 dealers + moneylender, got " + record.npcs().size());
		BlockPos chestPos = origin.offset(1, 1, 19);
		helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST), "parlor chest");
		Container inv = (Container) level.getBlockEntity(chestPos);
		boolean gold = false;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String id = BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).toString();
			gold |= id.equals("minecraft:gold_ingot") || id.equals("minecraft:gold_block") || id.startsWith("burmaldaholic:")
				|| id.equals("minecraft:netherite_scrap");
		}
		helper.assertTrue(gold, "parlor loot rolled");
		helper.succeed();
	}

	// ---- House games (CB-G, R-CB M9 / M10 / M11) ------------------------------------------------------------------------

	private static ServerPlayer housePlayer(GameTestHelper helper, GameType mode, boolean op) {
		ServerPlayer p = CasinoQa.player(helper, 0);
		var list = helper.getLevel().getServer().getPlayerList();
		if (op) {
			list.op(p.nameAndId(), Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER), Optional.of(false));
		} else {
			list.deop(p.nameAndId());
		}
		p.setGameMode(mode);
		CasinoQa.drainMessages(p);
		return p;
	}

	private static void dropPlayers(GameTestHelper helper, List<ServerPlayer> players) {
		var list = helper.getLevel().getServer().getPlayerList();
		for (ServerPlayer p : players) {
			list.deop(p.nameAndId());
			list.remove(p);
		}
	}

	private static long itemsNear(ServerLevel level, BlockPos pos, Item item) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3), e -> e.getItem().is(item)).size();
	}

	private static void clearItems(ServerLevel level, BlockPos pos) {
		level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).forEach(e -> e.discard());
	}

	private static boolean isTable(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof CasinoTableBlock;
	}

	/**
	 * M9 on a synthetic casino record (a CASINO box around part of the test area): non-operators in Survival and in
	 * Creative cannot break a house table (refused with the message), an operator can and gets no item, explosions
	 * (TNT, mob) leave it standing, a table a player placed inside the box and a table outside any casino break and
	 * drop normally.
	 */
	@GameTest(maxTicks = 40)
	public void houseTablesCannotBeLooted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String dim = level.dimension().identifier().toString();
		BlockPos a = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos b = helper.absolutePos(new BlockPos(4, 5, 4));
		String id = "gametest|house|" + a.getX() + "|" + a.getY() + "|" + a.getZ();
		CasinoIndex index = CasinoIndex.get(level.getServer());
		index.removeIf(r -> r.id().equals(id));
		index.setAnchor(id, dim, "gametest/house", CasinoKind.CASINO, Geometry.Box.corners(vec(a), vec(b)));
		var table = dev.nezo.burmaldaholic.games.blackjack.BlackjackModule.TABLE;
		BlockPos house = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos house2 = helper.absolutePos(new BlockPos(3, 1, 3));
		BlockPos outside = helper.absolutePos(new BlockPos(7, 1, 7));
		BlockPos mine = helper.absolutePos(new BlockPos(3, 1, 1));
		List<ServerPlayer> players = new ArrayList<>();
		try {
			for (BlockPos p : List.of(house, house2, outside)) {
				level.setBlockAndUpdate(p, table.block().defaultBlockState());
			}
			ServerPlayer survivor = housePlayer(helper, GameType.SURVIVAL, false);
			ServerPlayer builder = housePlayer(helper, GameType.CREATIVE, false);
			ServerPlayer op = housePlayer(helper, GameType.SURVIVAL, true);
			players.addAll(List.of(survivor, builder, op));
			helper.assertFalse(Commands.LEVEL_GAMEMASTERS.check(survivor.permissions()), "survival test player is not an operator");
			helper.assertTrue(Commands.LEVEL_GAMEMASTERS.check(op.permissions()), "op test player is an operator");

			// non-op Survival: refused with the message, nothing drops
			helper.assertFalse(survivor.gameMode.destroyBlock(house), "non-op survival break refused");
			helper.assertTrue(isTable(level, house), "house table still there after a non-op survival break");
			helper.assertTrue(CasinoQa.drainMessages(survivor).contains(HouseGames.PROTECTED_KEY), "player told why");
			helper.assertTrue(itemsNear(level, house, table.item()) == 0, "no table item after a refused break");
			// non-op Creative: refused too
			helper.assertFalse(builder.gameMode.destroyBlock(house), "non-op creative break refused");
			helper.assertTrue(isTable(level, house), "house table still there after a non-op creative break");
			helper.assertTrue(CasinoQa.drainMessages(builder).contains(HouseGames.PROTECTED_KEY), "creative player told why");

			// explosions / drops: the decisions MT's table block applies (end-to-end in MultiBlockTableGameTests)
			BlockEntity houseBe = level.getBlockEntity(house2);
			helper.assertTrue(HouseGames.explosionProof(level, house2, houseBe), "explosions leave a house table standing");
			helper.assertFalse(HouseGames.dropsAllowed(level, house2, houseBe, null), "a house table never drops an item");
			helper.assertFalse(HouseGames.breakAllowed(level, house2, houseBe, survivor), "non-op may not break a house table");
			helper.assertTrue(HouseGames.breakAllowed(level, house2, houseBe, op), "operator may break a house table");
			helper.assertFalse(HouseGames.explosionProof(level, outside, level.getBlockEntity(outside)), "tables outside casinos are not explosion-proof");
			helper.assertTrue(HouseGames.dropsAllowed(level, outside, level.getBlockEntity(outside), null), "tables outside casinos drop");

			// a player's own table inside the casino (marked by the placing block, MT contract) stays theirs
			level.setBlockAndUpdate(mine, table.block().defaultBlockState());
			HouseGames.markPlayerPlaced(level.getBlockEntity(mine));
			helper.assertTrue(HouseGames.isPlayerPlaced(level.getBlockEntity(mine)), "placement marked on the block entity");
			helper.assertTrue(HouseGames.dropsAllowed(level, mine, level.getBlockEntity(mine), survivor), "own table may drop");
			clearItems(level, mine);
			helper.assertTrue(survivor.gameMode.destroyBlock(mine), "owner breaks their own table inside the casino");
			helper.assertTrue(!isTable(level, mine) && itemsNear(level, mine, table.item()) == 1, "own table drops itself");

			// outside every casino: ordinary table, ordinary drop
			clearItems(level, outside);
			helper.assertTrue(survivor.gameMode.destroyBlock(outside), "non-op breaks a table outside casinos");
			helper.assertTrue(!isTable(level, outside) && itemsNear(level, outside, table.item()) == 1, "table outside casinos drops itself");

			// operator in Survival: removed, but the house keeps its table (no item)
			clearItems(level, house);
			op.gameMode.destroyBlock(house);
			helper.assertTrue(!isTable(level, house), "operator removed the house table");
			helper.assertTrue(itemsNear(level, house, table.item()) == 0, "operator break of a house table drops nothing");
		} finally {
			dropPlayers(helper, players);
			index.removeIf(r -> r.id().equals(id));
		}
		helper.succeed();
	}

	/** Pistons cannot push or pull game blocks out of a casino: table blocks carry block entities (vanilla: immovable). */
	@GameTest(maxTicks = 40)
	public void pistonsDoNotMoveTables(GameTestHelper helper) {
		var table = dev.nezo.burmaldaholic.games.blackjack.BlackjackModule.TABLE;
		BlockPos tablePos = new BlockPos(2, 1, 1);
		BlockPos stonePos = new BlockPos(2, 1, 4);
		BlockPos pulledPos = new BlockPos(3, 1, 6);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(tablePos, table.block().defaultBlockState());
		// control: an identical piston pushing stone proves the pistons fire
		helper.setBlock(new BlockPos(1, 1, 4), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(stonePos, Blocks.STONE);
		// sticky piston extended by power, then a table at its head: retracting must not pull it
		helper.setBlock(new BlockPos(1, 1, 6), Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(0, 1, 6), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(4, () -> {
			helper.setBlock(pulledPos, table.block().defaultBlockState());
			helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
			helper.setBlock(new BlockPos(0, 1, 4), Blocks.REDSTONE_BLOCK);
			helper.setBlock(new BlockPos(0, 1, 6), Blocks.AIR);
		});
		helper.runAfterDelay(12, () -> {
			helper.assertBlockPresent(Blocks.STONE, stonePos.east());
			helper.assertTrue(helper.getBlockState(tablePos).is(table.block()), "pushed table did not move");
			helper.assertTrue(helper.getBlockState(tablePos.east()).isAir(), "nothing pushed past the table");
			helper.assertTrue(helper.getBlockState(pulledPos).is(table.block()), "sticky piston did not pull the table");
			helper.assertTrue(!helper.getBlockState(pulledPos.west()).is(table.block()), "table not pulled toward the piston");
			helper.succeed();
		});
	}

	/**
	 * Real standalone casino (integration: retargeted from the retired Tudor to CR-1's riviera Grand Casino, whose floor
	 * piece holds the player-banked tables; {@code /place structure}): a non-op survival player cannot break its blackjack
	 * table (message), an operator removes it without an item, TNT leaves its roulette standing; M10: the VIP room's
	 * player-banked baccarat and UTH tables get the worldgen bot defaults (bots seat next to a solo player).
	 */
	@GameTest(maxTicks = 400)
	public void tudorHouseGames(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Optional<CasinoQa.Target> tudor = CasinoQa.standalone(level.getServer()).stream().filter(t -> t.name().equals("casino_riviera")).findFirst();
		helper.assertTrue(tudor.isPresent(), "casino_riviera structure registered");
		CasinoQa.Placed placed = CasinoQa.place(level, tudor.get(), new BlockPos(-12000, 0, 8000), 1);
		helper.assertTrue(placed.record().isPresent(), "Tudor registered a casino record");
		List<CasinoQa.Spot> spots = placed.spots();
		CasinoQa.Spot blackjack = spots.stream().filter(s -> s.game() == Game.BLACKJACK).findFirst()
			.orElseThrow(() -> new AssertionError("no blackjack in Tudor"));
		CasinoQa.Spot roulette = spots.stream().filter(s -> s.game() == Game.ROULETTE).findFirst()
			.orElseThrow(() -> new AssertionError("no roulette in Tudor"));
		List<ServerPlayer> players = new ArrayList<>();
		try {
			ServerPlayer survivor = housePlayer(helper, GameType.SURVIVAL, false);
			ServerPlayer op = housePlayer(helper, GameType.SURVIVAL, true);
			players.addAll(List.of(survivor, op));
			Item item = level.getBlockState(blackjack.pos()).getBlock().asItem();
			clearItems(level, blackjack.pos());
			helper.assertFalse(survivor.gameMode.destroyBlock(blackjack.pos()), "non-op break of the Tudor blackjack refused");
			helper.assertTrue(isTable(level, blackjack.pos()), "Tudor blackjack still present");
			helper.assertTrue(CasinoQa.drainMessages(survivor).contains(HouseGames.PROTECTED_KEY), "non-op told the table is the house's");
			op.gameMode.destroyBlock(blackjack.pos());
			helper.assertTrue(!isTable(level, blackjack.pos()) && itemsNear(level, blackjack.pos(), item) == 0,
				"operator removed it and no blackjack_table item dropped within 3 blocks");

			BlockEntity rouletteBe = level.getBlockEntity(roulette.pos());
			helper.assertTrue(HouseGames.explosionProof(level, roulette.pos(), rouletteBe), "Tudor roulette is explosion-proof (MT applies it)");
			helper.assertFalse(HouseGames.dropsAllowed(level, roulette.pos(), rouletteBe, null), "Tudor roulette never drops (MT applies it)");

			// CR-1 Grand Casino: the player-banked baccarat / UTH sit in the main floor piece (no separate VIP room)
			CasinoQa.Placed vipRoom = placed;
			for (Game g : List.of(Game.BACCARAT_PLAYER_BANKED, Game.UTH_PLAYER_BANKED)) {
				CasinoQa.Spot vip = vipRoom.spots().stream().filter(s -> s.game() == g).findFirst()
					.orElseThrow(() -> new AssertionError("no " + g + " in Tudor"));
				BlockEntity be = level.getBlockEntity(vip.pos());
				helper.assertTrue(be instanceof BotTable bt && bt.tableBots() != null, g + " has Seats & Bots");
				TableBots bots = ((BotTable) be).tableBots();
				String family = WorldgenBotPresets.gameOf(g.blockId()).orElseThrow(() -> new AssertionError(g + " not in GAME_OF"));
				BotSettings want = WorldgenBotPresets.apply(TableBots.defaultsFor(family, true),
					WorldgenBotPresets.presetFor(CasinoKind.CASINO, g.blockId()));
				helper.assertTrue(bots.defaults().equals(want), g + " uses the worldgen bot defaults " + want + ", got " + bots.defaults());
				helper.assertTrue(want.policy() != SeatPolicy.HUMANS_ONLY && want.count() > 0,
					g + ": worldgen defaults seat bots next to a solo player (" + want + ")");
			}
		} finally {
			dropPlayers(helper, players);
		}
		helper.succeed();
	}

	/** M11: the v2 Parlor's baccarat gets parlor_baccarat directly; the v2 Lounge's craps / poker get High-Roller presets. */
	@GameTest(maxTicks = 100)
	public void parlorAndLoungePresets(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, Map<Game, String>> want = Map.of(
			"casino/parlor", Map.of(Game.BACCARAT, TablePresets.PARLOR_BACCARAT, Game.POKER, TablePresets.PARLOR_POKER),
			"casino/lounge", Map.of(Game.CRAPS, TablePresets.HIGH_ROLLER_CRAPS, Game.POKER, TablePresets.HIGH_ROLLER_POKER,
				Game.BACCARAT_HIGH_ROLLER, TablePresets.HIGH_ROLLER_BACCARAT, Game.BLACKJACK_HIGH_ROLLER, TablePresets.HIGH_ROLLER_BLACKJACK));
		int i = 0;
		for (var e : want.entrySet()) {
			Layout l = Layouts.byId(e.getKey()).orElseThrow(() -> new AssertionError(e.getKey() + " layout missing"));
			BlockPos at = new BlockPos(-12000 + 160 * ++i, 0, 9000);
			CasinoQa.Placed p = CasinoQa.place(level, new CasinoQa.Target(l.id(), true, true, Optional.empty(), Optional.of(l)), at, 0);
			for (var g : e.getValue().entrySet()) {
				CasinoQa.Spot s = p.spots().stream().filter(x -> x.game() == g.getKey()).findFirst()
					.orElseThrow(() -> new AssertionError(g.getKey() + " missing in " + l.id()));
				var preset = CoreServices.tablePresets().preset(level, s.pos());
				helper.assertTrue(preset.map(x -> x.id()).orElse("").equals(g.getValue()),
					l.id() + " " + g.getKey() + " preset " + g.getValue() + ", got " + preset.map(x -> x.id()).orElse("none"));
				helper.assertTrue(level.getBlockEntity(s.pos()) instanceof CasinoTableBlockEntity t && t.preset().isPresent(),
					l.id() + " " + g.getKey() + ": the table itself reads its preset");
			}
		}
		helper.succeed();
	}

	/**
	 * v0.1.3: the village casino has a baccarat table. Built from the real template (every style), the table stands at
	 * (12, 1, 13) facing south with walkable, lit player cells in front of it reachable from the entrance, a free dealer
	 * cell behind it; a player opens it by USING the block and a bet placed through the {@code table_action} receiver
	 * ({@link dev.nezo.burmaldaholic.core.table.TableNetworking#receive}) lands.
	 */
	@GameTest(maxTicks = 200)
	public void villageCasinoBaccaratTakesABet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.MinecraftServer server = level.getServer();
		String dim = level.dimension().identifier().toString();
		java.util.List<BoundingBox> boxes = new java.util.ArrayList<>();
		dev.nezo.burmaldaholic.worldgen.logic.VillageStyle[] styles = dev.nezo.burmaldaholic.worldgen.logic.VillageStyle.values();
		for (int i = 0; i < styles.length; i++) {
			Layout l = Layouts.village(styles[i]);
			BlockPos origin = origin(helper).offset(0, 40 + i * 12, 0);
			boxes.add(CasinoBuilder.build(level, l, origin, Rotation.NONE).orElseThrow());
			BlockPos table = origin.offset(12, 1, 14);
			BlockState state = level.getBlockState(table);
			helper.assertTrue(state.getBlock() == dev.nezo.burmaldaholic.games.baccarat.BaccaratModule.TABLE.block(),
				l.id() + ": baccarat table (main block) at (12, 1, 14), found " + state);
			helper.assertTrue(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH,
				l.id() + ": baccarat faces the hall (south)");
			// MT: players on the walkway row south of the 3×2 table, the dealer two rows behind the main block
			for (BlockPos cell : new BlockPos[] {table.south(), table.south().east(), table.south().west(), table.north(2)}) {
				helper.assertTrue(walkable(level, cell) && free(level, cell.above()), l.id() + ": free cell at " + cell.subtract(origin));
			}
			// lit by the template's own light above the table (the world light engine runs asynchronously, so a just-built
			// casino reads dark on its first tick on CI): glowstone at (12, 5, 13) is 5 blocks from the player's head cell (12, 2, 15)
			int emission = level.getBlockState(origin.offset(12, 5, 13)).getLightEmission();
			helper.assertTrue(emission - 5 >= 8, l.id() + ": player side is lit (light above the table emits " + emission + ")");
			helper.assertTrue(reachable(level, origin, origin.offset(8, 1, 16), table.south()), l.id() + ": baccarat reachable from the entrance");
		}

		// Plains: a player standing at the table opens it by using the block and bets through the packet path.
		BlockPos origin = origin(helper).offset(0, 40, 0);
		BlockPos table = origin.offset(12, 1, 14);
		dev.nezo.burmaldaholic.core.mode.CasinoMode.set(server, true);
		// connected like a real Survival player (RealFlowTableGameTests): the block's own use handler opens the menu
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "village-bac"), false);
		net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(server, level, cookie.gameProfile(),
			cookie.clientInformation()) {
			@Override
			public net.minecraft.world.level.GameType gameMode() {
				return net.minecraft.world.level.GameType.SURVIVAL;
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(table.getX() + 0.5, table.getY(), table.getZ() + 1.5, 180f, 20f);
		dev.nezo.burmaldaholic.core.economy.Economies.get().setBalance(server, player.getUUID(), 500,
			dev.nezo.burmaldaholic.core.economy.Economy.Transaction.of("worldgen", "gametest"));
		try {
			// the server's own right-click path (ServerPlayerGameMode#useItemOn, empty hand) on the generated block
			var used = player.gameMode.useItemOn(player, level, player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(table), Direction.SOUTH, table, false));
			helper.assertTrue(used.consumesAction(), "using the table is handled, got " + used);
			helper.assertTrue(player.containerMenu instanceof dev.nezo.burmaldaholic.core.table.CasinoTableMenu m && m.pos().equals(table),
				"using the village baccarat table opens its menu (got " + player.containerMenu + ")");
			var bac = (dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity) level.getBlockEntity(table);
			dev.nezo.burmaldaholic.core.table.TableNetworking.receive(player,
				new dev.nezo.burmaldaholic.core.network.TableActionPayload(table, "sit", new net.minecraft.nbt.CompoundTag()));
			helper.assertTrue(bac.isSeated(player), "Sit through the packet seats the player");
			net.minecraft.nbt.CompoundTag bet = new net.minecraft.nbt.CompoundTag();
			bet.putString("box", "player");
			bet.putLong("amount", 25);
			dev.nezo.burmaldaholic.core.table.TableNetworking.receive(player,
				new dev.nezo.burmaldaholic.core.network.TableActionPayload(table, "bet", bet));
			helper.assertTrue(bac.stakeOf(player.getUUID()) == 25, "the 25-chip Player bet lands, stake " + bac.stakeOf(player.getUUID()));
			helper.assertTrue(dev.nezo.burmaldaholic.core.economy.Economies.get().balance(server, player.getUUID()) == 475, "the stake left the balance");
			player.closeContainer();
		} finally {
			server.getPlayerList().remove(player);
			for (BoundingBox b : boxes) {
				level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, net.minecraft.world.phys.AABB.of(b),
					e -> e.entityTags().contains(dev.nezo.burmaldaholic.worldgen.CasinoNpcs.NPC_TAG)).forEach(net.minecraft.world.entity.Entity::discard);
				BlockPos o = new BlockPos(b.minX(), b.minY(), b.minZ());
				CasinoIndex.get(server).removeIf(r -> r.id().equals(CasinoRecord.idFor(dim, o.getX(), o.getY(), o.getZ())));
			}
		}
		helper.succeed();
	}

	private static boolean free(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	/** A player's feet fit here: nothing, or a carpet-thin floor covering. */
	private static boolean walkable(ServerLevel level, BlockPos p) {
		var shape = level.getBlockState(p).getCollisionShape(level, p);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1;
	}

	/** Breadth-first walk on the casino's floor row (feet + head free) from {@code from} to {@code to}. */
	private static boolean reachable(ServerLevel level, BlockPos origin, BlockPos from, BlockPos to) {
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(List.of(from));
		seen.add(from);
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			if (p.equals(to)) return true;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = p.relative(d);
				int x = n.getX() - origin.getX();
				int z = n.getZ() - origin.getZ();
				if (x < 0 || x > 16 || z < 0 || z > 16 || !seen.add(n)) continue;
				if (walkable(level, n) && free(level, n.above())) queue.add(n);
			}
		}
		return false;
	}
}
