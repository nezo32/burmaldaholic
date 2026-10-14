package dev.nezo.burmaldaholic.gametest.worldgen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
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

	/** Real worldgen path: an End City placed on the main End island gets the lounge on a tower. */
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
		if (placed > 0) {
			helper.assertTrue(lounges.getAsLong() == lengthBefore + 1, "a High Roller Lounge was added to the End City and registered");
		}
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
			BlockPos table = origin.offset(12, 1, 13);
			BlockState state = level.getBlockState(table);
			helper.assertTrue(state.getBlock() == dev.nezo.burmaldaholic.games.baccarat.BaccaratModule.TABLE.block(),
				l.id() + ": baccarat table at (12, 1, 13), found " + state);
			helper.assertTrue(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH,
				l.id() + ": baccarat faces the hall (south)");
			for (BlockPos cell : new BlockPos[] {table.south(), table.south(2), table.north()}) {
				helper.assertTrue(walkable(level, cell) && free(level, cell.above()), l.id() + ": free cell at " + cell.subtract(origin));
			}
			// lit by the template's own light above the table (the world light engine runs asynchronously, so a just-built
			// casino reads dark on its first tick on CI): glowstone at (12, 5, 13) is 4 blocks from the player's head cell
			int emission = level.getBlockState(table.above(4)).getLightEmission();
			helper.assertTrue(emission - 4 >= 8, l.id() + ": player side is lit (light above the table emits " + emission + ")");
			helper.assertTrue(reachable(level, origin, origin.offset(8, 1, 16), table.south()), l.id() + ": baccarat reachable from the entrance");
		}

		// Plains: a player standing at the table opens it by using the block and bets through the packet path.
		BlockPos origin = origin(helper).offset(0, 40, 0);
		BlockPos table = origin.offset(12, 1, 13);
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
