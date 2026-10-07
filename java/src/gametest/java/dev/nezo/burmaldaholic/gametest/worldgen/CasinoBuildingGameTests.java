package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Placed;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Spot;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Target;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.npc.CasinoNpcEntity;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Casino buildings v0.2.0 — server QA (lane CB-5, docs/architecture/casino-buildings.md §9.2, §9.5):
 * registry, templates, placement in every rotation, every game opened and bet on by a player standing in the
 * building (real packet path), one casino record per start, complete-or-absent (D5), fit rate + generation time,
 * idle tick budget. Runs against whatever casinos exist: the v0.1 templates today, the v0.2 structures after merge.
 *
 * <p>Each test works in its own far-away region of the test level so buildings never touch other tests.
 */
public class CasinoBuildingGameTests {
	private static final long[] SEEDS = {12345L, 42L, -4172144997902289642L, 8675309L};

	private static BlockPos region(int test, int index) {
		return new BlockPos(8000 + test * 2400, 0, 8000 + index * 224);
	}

	/** 1. Registry: the five structures, the set, the tag and every pool load (both MC versions run this). */
	@GameTest
	public void casinoRegistryLoads(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		List<Target> standalone = CasinoQa.standalone(server);
		if (standalone.isEmpty()) {
			helper.assertFalse(CasinoQa.requireV2(), "v0.2 casino structures missing (burmaldaholic:casino_<style>)");
			CasinoQa.log("casinoRegistryLoads: no v0.2 structures yet (pre-merge) — only the v0.1 pools are checked");
		} else {
			helper.assertTrue(standalone.size() == CasinoQa.STYLES.size(), "all five styles registered, got " + standalone);
			helper.assertTrue(reg.lookupOrThrow(Registries.STRUCTURE_SET).get(ResourceKey.create(Registries.STRUCTURE_SET, CasinoQa.SET)).isPresent(),
				"structure_set " + CasinoQa.SET);
			var tag = CasinoQa.casinoTag(server);
			helper.assertTrue(tag.isPresent() && tag.get().size() == CasinoQa.STYLES.size(), "#" + CasinoQa.TAG + " lists the five casinos");
			HolderLookup.RegistryLookup<StructureTemplatePool> pools = reg.lookupOrThrow(Registries.TEMPLATE_POOL);
			for (String s : CasinoQa.STYLES) {
				for (String pool : List.of("entrance", "floor", "bar", "rear")) {
					Identifier id = Identifier.parse("burmaldaholic:worldgen/casino/" + s + "/" + pool);
					var p = pools.get(ResourceKey.create(Registries.TEMPLATE_POOL, id));
					helper.assertTrue(p.isPresent() && !p.get().value().getTemplates().isEmpty(), "pool " + id + " present and non-empty");
				}
			}
		}
		for (Target t : CasinoQa.legacy()) {
			helper.assertTrue(dev.nezo.burmaldaholic.worldgen.CasinoElements.element(reg.lookupOrThrow(Registries.TEMPLATE_POOL),
				t.layout().orElseThrow()).isPresent(), "v0.1 pool kept for old saves: " + t);
		}
		helper.succeed();
	}

	/**
	 * 2. Every block id and property in every casino template resolves: an unknown id loads as AIR silently
	 * (a typo = a missing table), an unknown property is dropped (a wrong facing = an unusable table).
	 */
	@GameTest
	public void templatePalettesResolve(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Map<Identifier, Resource> files = server.getResourceManager().listResources("structure",
			id -> id.getNamespace().equals("burmaldaholic") && id.getPath().startsWith("structure/worldgen/") && id.getPath().endsWith(".nbt"));
		helper.assertTrue(files.size() >= CasinoQa.legacy().size(), "casino templates found: " + files.size());
		List<String> bad = new ArrayList<>();
		for (var e : files.entrySet()) {
			CompoundTag root;
			try (InputStream in = e.getValue().open()) {
				root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			} catch (java.io.IOException ex) {
				bad.add(e.getKey() + ": unreadable " + ex.getMessage());
				continue;
			}
			helper.assertTrue(root.getIntOr("DataVersion", 0) <= 4903 || !e.getKey().getPath().contains("/casino/"),
				e.getKey() + ": DataVersion must stay 26.2's (4903) so 26.2 can load it");
			ListTag palette = root.getListOrEmpty("palette");
			for (int i = 0; i < palette.size(); i++) {
				CompoundTag entry = palette.getCompoundOrEmpty(i);
				Identifier block = Identifier.tryParse(entry.getStringOr("Name", ""));
				if (block == null || !BuiltInRegistries.BLOCK.containsKey(block)) {
					bad.add(e.getKey().getPath() + ": unknown block " + entry.getStringOr("Name", "?"));
					continue;
				}
				Block b = BuiltInRegistries.BLOCK.getValue(block);
				CompoundTag props = entry.getCompoundOrEmpty("Properties");
				for (String key : props.keySet()) {
					Property<?> p = b.getStateDefinition().getProperty(key);
					String value = props.getStringOr(key, "");
					if (p == null) {
						bad.add(e.getKey().getPath() + ": " + block + " has no property " + key);
					} else if (p.getValue(value).isEmpty()) {
						bad.add(e.getKey().getPath() + ": " + block + "[" + key + "=" + value + "] invalid value");
					}
				}
			}
			if (!root.getListOrEmpty("entities").isEmpty() && e.getKey().getPath().contains("/casino/")) {
				bad.add(e.getKey().getPath() + ": entities in a v0.2 template (D8 forbids them)");
			}
		}
		helper.assertTrue(bad.isEmpty(), "template problems:\n  " + String.join("\n  ", bad));
		CasinoQa.log("templatePalettesResolve: {} templates ok", files.size());
		helper.succeed();
	}

	/**
	 * 3. Every casino in four placements (layouts: the four rotations; structures: four positions = the
	 * rotations worldgen picks): game blocks present (all ten families in a full casino), facing = a free player
	 * cell, standing on a floor; v0.2 also keeps the board clearance free and is lit (≥ 10 over games, no dark floor).
	 */
	@GameTest(maxTicks = 600)
	public void placesEveryCasinoInEveryRotation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, List<String>> problems = CasinoQa.problems();
		List<Placed> lit = new ArrayList<>();
		List<ChunkPos> forced = new ArrayList<>();
		int i = 0;
		for (Target t : CasinoQa.all(level.getServer())) {
			for (int turns = 0; turns < 4; turns++) {
				BlockPos at = region(0, i++);
				Placed p = CasinoQa.place(level, t, at, turns);
				problems.computeIfAbsent(t + " r" + turns, k -> new ArrayList<>()).addAll(CasinoQa.structuralProblems(level, p));
				if (t.v2()) {
					lit.add(p);
					force(level, p.box(), forced);
				}
			}
		}
		String report = CasinoQa.report(problems);
		helper.assertTrue(report.isEmpty(), "placement problems:" + report);
		if (lit.isEmpty()) {
			CasinoQa.log("placesEveryCasinoInEveryRotation: {} placements ok (no v0.2 casino yet: light bar not checked)", i);
			helper.succeed();
			return;
		}
		// light is computed off-thread: poll until it settles (or report the last result at the timeout)
		helper.succeedWhen(() -> {
			Map<String, List<String>> dark = CasinoQa.problems();
			for (Placed p : lit) {
				dark.computeIfAbsent(p.target() + " r" + p.rotation(), k -> new ArrayList<>()).addAll(CasinoQa.lightProblems(level, p));
			}
			String r = CasinoQa.report(dark);
			helper.assertTrue(r.isEmpty(), "lighting below the v0.2 bar:" + r);
			unforce(level, forced);
		});
	}

	/**
	 * 4. REAL FLOW: a player stands on each game's player cell, right-clicks it through the use-item packet handler
	 * (reach, facing and menu checks included) and the table's menu opens; for every family one bet goes through the
	 * C2S {@code table_action} receiver and moves money. The cashier sells chips for an emerald, the Croupier (if the
	 * building has one) sells a scratch card through its shop page.
	 */
	@GameTest(maxTicks = 200)
	public void everyGameOpensFromTheBuilding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, List<String>> problems = CasinoQa.problems();
		List<ServerPlayer> players = new ArrayList<>();
		int i = 0;
		try {
			for (Target t : CasinoQa.all(level.getServer())) {
				Placed p = CasinoQa.place(level, t, region(1, i++), 1);
				List<String> bad = problems.computeIfAbsent(t.toString(), k -> new ArrayList<>());
				ServerPlayer player = CasinoQa.player(helper, 1_000_000);
				players.add(player);
				for (Spot s : p.spots()) {
					if (!CasinoQa.open(player, s)) {
						bad.add(s + ": right-click from the player cell did not open its menu (open: "
							+ player.containerMenu.getClass().getSimpleName() + ")");
					}
				}
				for (var e : p.firstBaseSpotPerFamily().entrySet()) {
					Spot s = e.getValue();
					if (s.game() != CasinoQa.baseGame(e.getKey())) {
						continue; // VIP / player-banked only: opening is checked above, betting needs a VIP tier or a banker
					}
					if (!CasinoQa.open(player, s)) {
						continue;
					}
					String err = CasinoQa.bet(player, s);
					if (err != null && err.contains("vip_required")) {
						CasinoQa.log("{}: {} is VIP-gated by its preset; retrying as a VIP", t, s);
						CasinoQa.makeVip(player);
						err = CasinoQa.bet(player, s);
					}
					if (err != null) {
						bad.add(e.getKey() + " bet at " + s + ": " + err);
					}
				}
				if (p.record().map(r -> r.npcs().stream().anyMatch(n -> n.role() == NpcRole.CROUPIER)).orElse(false)) {
					String err = CasinoQa.croupierSellsScratchCard(player, p);
					if (err != null && !err.startsWith("no Croupier entity")) {
						bad.add("Croupier: " + err);
					}
					CasinoQa.log("{}: Croupier shop {}", t, err == null ? "sold a scratch card" : err);
				}
				player.closeContainer();
			}
		} finally {
			players.forEach(pl -> level.getServer().getPlayerList().remove(pl));
		}
		String report = CasinoQa.report(problems);
		helper.assertTrue(report.isEmpty(), "real-flow problems:" + report);
		CasinoQa.log("everyGameOpensFromTheBuilding: {} casinos, every game opened and bet on", i);
		helper.succeed();
	}

	/**
	 * 5. One casino per placement (D6): exactly one record intersects the building and its box holds every game
	 * block; a full casino records the Croupier home, v0.2 standalone casinos also the Loan Shark and Baccarat Dealer.
	 */
	@GameTest(maxTicks = 100)
	public void oneCasinoRecordPerStart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, List<String>> problems = CasinoQa.problems();
		int i = 0;
		for (Target t : CasinoQa.all(level.getServer())) {
			Placed p = CasinoQa.place(level, t, region(2, i++), 0);
			List<String> bad = problems.computeIfAbsent(t.toString(), k -> new ArrayList<>());
			List<CasinoRecord> recs = CasinoQa.recordsIntersecting(level, p.box());
			if (recs.size() != 1) {
				bad.add(recs.size() + " casino records over the building (want exactly 1): " + recs.stream().map(CasinoRecord::id).toList());
				continue;
			}
			CasinoRecord r = recs.getFirst();
			for (Spot s : p.spots()) {
				if (!r.box().contains(s.pos().getX(), s.pos().getY(), s.pos().getZ())) {
					bad.add(s + " outside the record box " + r.box());
				}
			}
			Set<NpcRole> roles = EnumSet.noneOf(NpcRole.class);
			r.npcs().forEach(n -> roles.add(n.role()));
			if (t.structure().isPresent()) {
				helper.assertTrue(r.kind() != null && r.kind().id().equals("casino"), t + ": record kind CASINO, got " + r.kind());
				for (NpcRole need : List.of(NpcRole.CROUPIER, NpcRole.LOAN_SHARK, NpcRole.BACCARAT_DEALER)) {
					if (!roles.contains(need)) {
						bad.add("NPC home missing: " + need);
					}
				}
			}
		}
		String report = CasinoQa.report(problems);
		helper.assertTrue(report.isEmpty(), "casino records:" + report);
		helper.succeed();
	}

	/**
	 * 6. Complete or absent (D5): the casino structure re-decoded with a jigsaw depth of 1 (entrance + floor only, the
	 * bar never attaches) must never produce a start, while the real one does on the same flat chunks.
	 */
	@GameTest(maxTicks = 40)
	public void incompleteAssemblyRejected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		List<Target> standalone = CasinoQa.standalone(server);
		if (standalone.isEmpty()) {
			helper.assertFalse(CasinoQa.requireV2(), "v0.2 casino structures missing");
			CasinoQa.log("incompleteAssemblyRejected: skipped (no v0.2 structure yet)");
			helper.succeed();
			return;
		}
		RegistryOps<com.google.gson.JsonElement> ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
		int realStarts = 0;
		int incompleteStarts = 0;
		int tries = 0;
		for (Target t : standalone) {
			Holder<Structure> real = CasinoQa.structure(server, t.name().substring("casino_".length())).orElseThrow();
			var json = Structure.DIRECT_CODEC.encodeStart(ops, real.value()).getOrThrow().getAsJsonObject();
			helper.assertTrue(json.has("required_pieces") && json.getAsJsonArray("required_pieces").size() >= 3,
				t + ": required_pieces lists entrance, floor, bar: " + json.get("required_pieces"));
			json.addProperty("size", 1);
			Structure incomplete = Structure.DIRECT_CODEC.parse(ops, json).getOrThrow();
			for (int k = 0; k < 6; k++) {
				BlockPos at = region(3, tries++);
				CasinoQa.loadChunks(level, at, 1);
				Structure.GenerationContext ctx = CasinoQa.context(level.getSeed(), server.registryAccess(),
					level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(),
					level.getChunkSource().randomState(), level.getChunkSource().randomState().sampler(),
					server.getStructureManager(), CasinoQa.chunk(at), (LevelHeightAccessor) level,
					(java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>>) b -> true);
				if (real.value().findValidGenerationPoint(ctx).isPresent()) {
					realStarts++;
				}
				if (incomplete.findValidGenerationPoint(ctx).isPresent()) {
					incompleteStarts++;
				}
			}
		}
		helper.assertTrue(incompleteStarts == 0, "an assembly without the bar produced " + incompleteStarts + " starts (D5: complete or absent)");
		helper.assertTrue(realStarts > 0, "control: the real casino structure produced no start on flat ground in " + tries + " tries");
		helper.succeed();
	}

	/**
	 * 7. Fit rate and generation time on the default world preset (§9.4 / §9.5): over 4 seeds, within 2000 blocks,
	 * ≥ 8 casinos, median nearest ≤ 700, ≤ 40 % of biome-valid cells rejected by flatness / completeness, median
	 * start generation ≤ 5 ms and p99 ≤ 25 ms. No chunk is generated (like WorldgenSpawnSurvey).
	 */
	@GameTest(maxTicks = 40)
	public void casinoFitRateAndGenerationTime(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		if (CasinoQa.standalone(server).isEmpty()) {
			helper.assertFalse(CasinoQa.requireV2(), "v0.2 casino structures missing");
			CasinoQa.log("casinoFitRateAndGenerationTime: skipped (no v0.2 structure yet)");
			helper.succeed();
			return;
		}
		HolderLookup.RegistryLookup<StructureSet> sets = reg.lookupOrThrow(Registries.STRUCTURE_SET);
		StructureSet set = sets.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, CasinoQa.SET)).value();
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
		LevelStem stem = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
			.createWorldDimensions().dimensions().get(LevelStem.OVERWORLD);
		ChunkGenerator gen = stem.generator();
		DimensionType type = stem.type().value();
		LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());
		int radius = 2000;
		int biomeValid = 0;
		int placed = 0;
		List<Double> millis = new ArrayList<>();
		List<Integer> nearest = new ArrayList<>();
		StringBuilder perSeed = new StringBuilder();
		for (long seed : SEEDS) {
			RandomState rs = RandomState.create(((NoiseBasedChunkGenerator) gen).generatorSettings().value(), reg.lookupOrThrow(Registries.NOISE), seed);
			ChunkGeneratorStructureState state = gen.createState(sets, rs, seed);
			int spacing = placement.spacing();
			int rc = radius / 16;
			int seedPlaced = 0;
			int best = Integer.MAX_VALUE;
			for (int cx = Math.floorDiv(-rc, spacing) - 1; cx <= Math.floorDiv(rc, spacing) + 1; cx++) {
				for (int cz = Math.floorDiv(-rc, spacing) - 1; cz <= Math.floorDiv(rc, spacing) + 1; cz++) {
					ChunkPos chunk = placement.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
					int bx = chunk.getMiddleBlockX();
					int bz = chunk.getMiddleBlockZ();
					if ((long) bx * bx + (long) bz * bz > (long) radius * radius || !placement.isStructureChunk(state, chunk.x(), chunk.z())) {
						continue;
					}
					boolean anyBiome = false;
					for (StructureSet.StructureSelectionEntry e : set.structures()) {
						anyBiome |= CasinoQa.biomeMatches(gen, rs, bx, 64, bz, e.structure().value().biomes()::contains);
					}
					if (!anyBiome) {
						continue;
					}
					biomeValid++;
					long t0 = System.nanoTime();
					boolean ok = startAt(set, seed, chunk, reg, gen, rs, server, height);
					millis.add((System.nanoTime() - t0) / 1e6);
					if (ok) {
						placed++;
						seedPlaced++;
						best = Math.min(best, (int) Math.hypot(bx, bz));
					}
				}
			}
			if (best != Integer.MAX_VALUE) {
				nearest.add(best);
			}
			perSeed.append(seed).append(": ").append(seedPlaced).append(" (nearest ").append(best == Integer.MAX_VALUE ? "-" : best).append(") ");
		}
		nearest.sort(Integer::compare);
		double rejected = biomeValid == 0 ? 1 : 1 - placed / (double) biomeValid;
		double median = CasinoQa.quantile(millis, 0.5);
		double p99 = CasinoQa.quantile(millis, 0.99);
		int medianNearest = nearest.isEmpty() ? Integer.MAX_VALUE : nearest.get(nearest.size() / 2);
		CasinoQa.log("fit: placed {} of {} biome-valid cells ({}% rejected) · per seed {}· start gen median {} ms p99 {} ms · median nearest {}",
			placed, biomeValid, Math.round(rejected * 100), perSeed, String.format("%.2f", median), String.format("%.2f", p99), medianNearest);
		helper.assertTrue(placed >= 8, "≥ 8 casinos within 2000 blocks over 4 seeds, got " + placed + " (" + perSeed + ")");
		helper.assertTrue(medianNearest <= 700, "median nearest casino ≤ 700 blocks, got " + medianNearest);
		helper.assertTrue(rejected <= 0.40, "≤ 40 % of biome-valid cells rejected, got " + Math.round(rejected * 100) + " %");
		helper.assertTrue(median <= 5 && p99 <= 25, "start generation median ≤ 5 ms, p99 ≤ 25 ms; got " + median + " / " + p99);
		helper.succeed();
	}

	/** Same weighted pick as ChunkGenerator.createStructures; true when one of the set's casinos starts here. */
	private static boolean startAt(StructureSet set, long seed, ChunkPos chunk, RegistryAccess reg, ChunkGenerator gen, RandomState rs,
			MinecraftServer server, LevelHeightAccessor height) {
		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(set.structures());
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(seed, chunk.x(), chunk.z());
		int total = options.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
		while (!options.isEmpty()) {
			int choice = random.nextInt(total);
			int index = 0;
			for (StructureSet.StructureSelectionEntry o : options) {
				choice -= o.weight();
				if (choice < 0) {
					break;
				}
				index++;
			}
			StructureSet.StructureSelectionEntry sel = options.get(index);
			Holder<Structure> holder = sel.structure();
			StructureStart start = CasinoQa.generate(holder, seed, Level.OVERWORLD, reg, gen, gen.getBiomeSource(), rs, rs.sampler(),
				server.getStructureManager(), chunk, height, (java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>>) holder.value().biomes()::contains);
			if (start.isValid()) {
				return true;
			}
			options.remove(index);
			total -= sel.weight();
		}
		return false;
	}

	/**
	 * 8. Idle tick budget (§9.5): the casino's own server cost per tick — every game block entity's ticker plus every
	 * NPC's tick — stays ≤ 1 ms (measured directly, so other tests running in parallel do not add noise).
	 */
	@GameTest(maxTicks = 100)
	public void idleTickBudget(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Target> targets = new ArrayList<>(CasinoQa.standalone(level.getServer()));
		if (targets.isEmpty()) {
			targets.addAll(CasinoQa.v2Singles());
		}
		if (targets.isEmpty()) {
			targets.addAll(CasinoQa.legacy()); // pre-merge: measure the v0.1 buildings so the harness is exercised
		}
		Map<String, List<String>> problems = CasinoQa.problems();
		int i = 0;
		StringBuilder log = new StringBuilder();
		for (Target t : targets) {
			Placed p = CasinoQa.place(level, t, region(4, i++), 0);
			List<CasinoTableBlockEntity> tables = new ArrayList<>();
			for (Spot s : p.spots()) {
				if (level.getBlockEntity(s.pos()) instanceof CasinoTableBlockEntity be) {
					tables.add(be);
				}
			}
			List<CasinoNpcEntity> npcs = level.getEntitiesOfClass(CasinoNpcEntity.class, net.minecraft.world.phys.AABB.of(p.box()).inflate(2));
			for (int warm = 0; warm < 60; warm++) {
				tables.forEach(be -> be.tick(level));
				npcs.forEach(CasinoNpcEntity::tick);
			}
			int n = 200;
			long t0 = System.nanoTime();
			for (int k = 0; k < n; k++) {
				tables.forEach(be -> be.tick(level));
				npcs.forEach(CasinoNpcEntity::tick);
			}
			double perTickMs = (System.nanoTime() - t0) / 1e6 / n;
			log.append(t).append('=').append(String.format("%.3f", perTickMs)).append("ms(").append(tables.size()).append(" tables, ")
				.append(npcs.size()).append(" npcs) ");
			List<String> bad = problems.computeIfAbsent(t.toString(), k -> new ArrayList<>());
			if (perTickMs > 1.0) {
				bad.add(String.format("casino costs %.3f ms per tick (> 1 ms)", perTickMs));
			}
			if (t.v2() && (tables.size() > 40 || npcs.size() > 6)) {
				bad.add("content budget: " + tables.size() + " game blocks (≤ 40), " + npcs.size() + " NPCs (≤ 6)");
			}
		}
		CasinoQa.log("idle tick: {}", log);
		String report = CasinoQa.report(problems);
		helper.assertTrue(report.isEmpty(), "idle tick budget:" + report);
		helper.succeed();
	}

	private static void force(ServerLevel level, BoundingBox box, List<ChunkPos> forced) {
		for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
			for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
				level.setChunkForced(x, z, true);
				forced.add(new ChunkPos(x, z));
			}
		}
	}

	private static void unforce(ServerLevel level, List<ChunkPos> forced) {
		Set<ChunkPos> done = new HashSet<>();
		for (ChunkPos c : forced) {
			if (done.add(c)) {
				level.setChunkForced(c.x(), c.z(), false);
			}
		}
		forced.clear();
	}
}
