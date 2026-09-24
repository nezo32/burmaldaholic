package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
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
			Optional<StructurePoolElement> element = CasinoElements.element(pools, l);
			helper.assertTrue(element.isPresent(), "pool burmaldaholic:" + l.templatePath() + " loaded from the casinos data pack");
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
		BlockPos chestPos = pos(Geometry.toWorld(vec(origin), new Vec(6, 1, 1), turns));
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
		helper.assertTrue(level.getBlockState(pos(Geometry.toWorld(vec(origin), new Vec(6, 3, 6), turns))).isAir(), "anchor cell cleared");
		BlockState cashier = level.getBlockState(pos(Geometry.toWorld(vec(origin), new Vec(10, 1, 1), turns)));
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(cashier.getBlock()).getPath().equals("cashier"), "cashier in the lounge, found " + cashier);

		// Registered for the welcome title / High Roller check, with its NPC home.
		String dim = level.dimension().identifier().toString();
		CasinoRecord record = CasinoIndex.get(level.getServer()).casinos().stream()
			.filter(r -> r.id().equals(CasinoRecord.idFor(dim, origin.getX(), origin.getY(), origin.getZ())))
			.findFirst().orElseThrow();
		helper.assertTrue(record.kind() == CasinoKind.HIGH_ROLLER && record.box().equals(expected), "record bounds");
		helper.assertTrue(record.npcs().size() == 1, "shulker croupier home recorded (spawned only if its entity exists)");
		BlockPos inside = pos(Geometry.toWorld(vec(origin), new Vec(6, 2, 6), turns));
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
}
