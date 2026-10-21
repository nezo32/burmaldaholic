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
	private static final long[] EXTRA_SEEDS = {1L, 2024L, -77L, 31415926L};

	private static BlockPos region(int test, int index) {
		return new BlockPos(8000 + test * 2400, 0, 8000 + index * 224);
	}

	/** 1. Registry: the five structures, the set, the tag and every pool load (both MC versions run this). */
	@GameTest(environment = CasinoQa.ENV)
	public void casinoRegistryLoads(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		List<Target> standalone = CasinoQa.standalone(server);
		if (standalone.isEmpty()) {
			helper.assertFalse(CasinoQa.requireV2(), "v0.2 casino structures missing (burmaldaholic:casino_<style>)");
			CasinoQa.log("casinoRegistryLoads: no v0.2 structures yet (pre-merge) — only the v0.1 pools are checked");
		} else {
			// all five once every style lane merged (integration gate); partial merges check what is there
			helper.assertTrue(standalone.size() == CasinoQa.STYLES.size() || !CasinoQa.requireV2(), "all five styles registered, got " + standalone);
			CasinoQa.log("casinoRegistryLoads: {} of {} styles registered", standalone.size(), CasinoQa.STYLES.size());
			helper.assertTrue(reg.lookupOrThrow(Registries.STRUCTURE_SET).get(ResourceKey.create(Registries.STRUCTURE_SET, CasinoQa.SET)).isPresent(),
				"structure_set " + CasinoQa.SET);
			var tag = CasinoQa.casinoTag(server);
			helper.assertTrue(tag.isPresent() && tag.get().size() == standalone.size(), "#" + CasinoQa.TAG + " lists every casino structure");
			HolderLookup.RegistryLookup<StructureTemplatePool> pools = reg.lookupOrThrow(Registries.TEMPLATE_POOL);
			for (String s : standalone.stream().map(t -> t.name().substring("casino_".length())).toList()) {
				for (String pool : dev.nezo.burmaldaholic.worldgen.logic.PackFiles.REQUIRED_POOLS) { // the Grand Casino has no rear pool
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
	@GameTest(environment = CasinoQa.ENV)
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
	@GameTest(maxTicks = 600, environment = CasinoQa.ENV)
	public void placesEveryCasinoInEveryRotation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, List<String>> problems = CasinoQa.problems();
		List<Placed> lit = new ArrayList<>();
		List<ChunkPos> forced = new ArrayList<>();
		int i = 0;
		int placements = 0;
		for (Target t : CasinoQa.all(level.getServer())) {
			int base = i++;
			for (int turns = 0; turns < 4; turns++) {
				// v0.2 buildings stay up for the light poll (own region each); others reuse one region (fewer chunks)
				BlockPos at = t.v2() && turns > 0 ? region(0, i++) : region(0, base);
				Placed p = CasinoQa.place(level, t, at, turns);
				placements++;
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
			CasinoQa.log("placesEveryCasinoInEveryRotation: {} placements ok (no v0.2 casino yet: light bar not checked)", placements);
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
	@GameTest(maxTicks = 200, environment = CasinoQa.ENV)
	public void everyGameOpensFromTheBuilding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, List<String>> problems = CasinoQa.problems();
		List<ServerPlayer> players = new ArrayList<>();
		List<Map.Entry<Target, BlockPos>> built = new ArrayList<>();
		int i = 0;
		try {
			for (Target t : CasinoQa.all(level.getServer())) {
				BlockPos at = region(1, i++);
				built.add(Map.entry(t, at));
				Placed p = CasinoQa.place(level, t, at, 1);
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
			// Betting seated atmosphere / house bots at every table: wipe the buildings so those tables leave the
			// server-wide bot budget (bots.maxActiveTables) — otherwise later bot GameTests find no free table slot.
			built.forEach(e -> CasinoQa.reset(level, e.getValue(), e.getKey().structure().isPresent() ? 56 : 36, 40));
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
	@GameTest(maxTicks = 100, environment = CasinoQa.ENV)
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
				// DL-SPEC §2: a table pod's baccarat dealer is the dimension's house dealer (CR-1's Grand Casino seats it via tablePod)
				if (roles.contains(NpcRole.HOUSE_DEALER)) {
					roles.add(NpcRole.BACCARAT_DEALER);
				}
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
	@GameTest(maxTicks = 40, environment = CasinoQa.ENV)
	public void incompleteAssemblyRejected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		List<Target> standalone = CasinoQa.standalone(server);
		RegistryOps<com.google.gson.JsonElement> ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
		if (standalone.isEmpty()) {
			helper.assertFalse(CasinoQa.requireV2(), "v0.2 casino structures missing");
			// harness self-check on a vanilla jigsaw structure: codec round trip + reflective context + a start on flat ground
			Holder<Structure> village = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.parse("minecraft:village_plains")));
			var json = Structure.DIRECT_CODEC.encodeStart(ops, village.value()).getOrThrow().getAsJsonObject();
			json.addProperty("size", 1);
			Structure.DIRECT_CODEC.parse(ops, json).getOrThrow();
			int starts = 0;
			for (int k = 0; k < 4; k++) {
				starts += village.value().findValidGenerationPoint(flatContext(level, region(3, k))).isPresent() ? 1 : 0;
			}
			helper.assertTrue(starts > 0, "harness: village_plains found no start on flat ground");
			CasinoQa.log("incompleteAssemblyRejected: no v0.2 structure yet — harness self-check on village_plains ok ({} starts)", starts);
			helper.succeed();
			return;
		}
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
				Structure.GenerationContext ctx = flatContext(level, region(3, tries++));
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

	/** A generation context for the test level's (flat) chunk at {@code at}, every biome accepted. */
	private static Structure.GenerationContext flatContext(ServerLevel level, BlockPos at) {
		MinecraftServer server = level.getServer();
		CasinoQa.loadChunks(level, at, 1);
		return CasinoQa.context(level.getSeed(), server.registryAccess(), level.getChunkSource().getGenerator(),
			level.getChunkSource().getGenerator().getBiomeSource(), level.getChunkSource().randomState(),
			CasinoQa.climateSampler(level.getChunkSource().randomState()), dev.nezo.burmaldaholic.worldgen.CasinoBuilder.templates(server), CasinoQa.chunk(at), (LevelHeightAccessor) level,
			(java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>>) b -> true);
	}

	/**
	 * 7. Fit rate and generation time on the default world preset (§9.4 / §9.5): over 4 seeds, within 2000 blocks,
	 * ≥ 8 casinos, median nearest ≤ 700, ≤ 40 % of biome-valid cells rejected by flatness / completeness, median
	 * start generation ≤ 5 ms and p99 ≤ 25 ms. No chunk is generated (like WorldgenSpawnSurvey). Before the v0.2
	 * structures exist the same survey runs on {@code minecraft:villages} (one seed) as a harness self-check.
	 */
	@GameTest(maxTicks = 40, environment = CasinoQa.ENV)
	public void casinoFitRateAndGenerationTime(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		if (CasinoQa.standalone(server).isEmpty()) {
			helper.assertFalse(CasinoQa.requireV2(), "v0.2 casino structures missing");
			Survey s = survey(server, Identifier.parse("minecraft:villages"), new long[] {SEEDS[0]}, 2000);
			CasinoQa.log("casinoFitRateAndGenerationTime: no v0.2 structure yet — harness self-check on villages: {}", s);
			helper.assertTrue(s.placed() > 0 && s.biomeValid() >= s.placed(), "harness: the villages survey found nothing: " + s);
			helper.succeed();
			return;
		}
		var stats = dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure.STATS;
		boolean stats0 = stats.enabled;
		stats.enabled = true; // reject reasons of the cells near spawn (cheap counters; timings are measured here, not by STATS)
		Survey s;
		try {
			s = survey(server, CasinoQa.SET, SEEDS, 2000);
		} finally {
			stats.enabled = stats0;
		}
		CasinoQa.log("fit: {}", s);
		// the spread numbers are for the full set of five styles (fewer styles = fewer biomes); fit and cost always count
		boolean allStyles = CasinoQa.standalone(server).size() == CasinoQa.STYLES.size() || CasinoQa.requireV2();
		helper.assertTrue(s.placed() >= 8 || !allStyles, "≥ 8 casinos within 2000 blocks over 4 seeds: " + s);
		// median nearest from spawn: 4 seeds give an upper median of 2 samples (one coastal seed swings it by 500 blocks),
		// so the spread is judged over 8 seeds (the 4 above + 4 more; placement only, their timings are not gated)
		Survey extra = survey(server, CasinoQa.SET, EXTRA_SEEDS, 2000);
		List<Integer> all8 = new ArrayList<>(s.nearest());
		all8.addAll(extra.nearest());
		all8.sort(Integer::compare);
		int median8 = all8.isEmpty() ? Integer.MAX_VALUE : (all8.get((all8.size() - 1) / 2) + all8.get(all8.size() / 2)) / 2;
		CasinoQa.log("fit: spread over 8 seeds: nearest {} -> median {} (4-seed upper median {}); extra seeds {}", all8, median8, s.medianNearest(), extra);
		helper.assertTrue(median8 <= 700 || !allStyles, "median nearest casino ≤ 700 blocks over 8 seeds: " + all8 + " -> " + median8);
		helper.assertTrue(s.rejected() <= 0.40, "≤ 40 % of biome-valid cells rejected: " + s);
		// contract §9.5: median ≤ 5 ms, p99 ≤ 25 ms (cold noise, warm JIT). The shared CI box is ~10× slower under load,
		// so the hard gate is 30× that budget; -Dburmaldaholic.requireCasinoV2=true applies the contract numbers.
		double k = CasinoQa.requireV2() ? 1 : 30;
		helper.assertTrue(s.medianMs() <= 5 * k && s.p99Ms() <= 25 * k, "start generation median ≤ " + 5 * k + " ms, p99 ≤ " + 25 * k + " ms: " + s);
		helper.succeed();
	}

	/**
	 * 9. The worldgen gate is a snapshot (R-CB M5): worker threads read volatile fields, recomputed on the server
	 * thread. Casino mode is NOT toggled here: its change listeners reset the bot tables of tests running in the same
	 * batch (a full build lost 5 bot tests to that); the mode path is {@code CasinoMode.onChange → refresh}. The config
	 * path is exercised in one tick and restored.
	 */
	@GameTest(environment = CasinoQa.ENV)
	public void worldgenRuntimeSnapshotTracksMode(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var cfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.worldgen();
		boolean standalone0 = cfg.standalone.enabled;
		try {
			dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.refresh();
			boolean truth = cfg.enabled && dev.nezo.burmaldaholic.core.mode.CasinoMode.isEnabled(server);
			helper.assertTrue(WorldgenRuntimeView.active() == truth, "snapshot == casino mode && worldgen.enabled");
			helper.assertTrue(WorldgenRuntimeView.standalone() == (truth && cfg.standalone.enabled), "standalone snapshot");
			// a worker thread sees the same answer without touching the server's saved data
			boolean[] seen = new boolean[] {!truth};
			Thread t = new Thread(() -> seen[0] = WorldgenRuntimeView.active());
			t.start();
			t.join();
			helper.assertTrue(seen[0] == truth, "worker thread reads the snapshot");
			cfg.standalone.enabled = false;
			dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.refresh();
			helper.assertTrue(WorldgenRuntimeView.active() == truth && !WorldgenRuntimeView.standalone(), "standalone off: annexes keep their gate");
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		} finally {
			cfg.standalone.enabled = standalone0;
			dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.refresh();
		}
		helper.succeed();
	}

	/**
	 * 10. {@code worldgen.standalone.enabled = false}: the standalone set makes no start anywhere (default preset, real
	 * structure-start generation, no chunk generated), village annexes still attach. One tick, restored after.
	 */
	@GameTest(maxTicks = 40, environment = CasinoQa.ENV)
	public void standaloneDisabledKeepsAnnexes(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var cfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.worldgen();
		boolean standalone0 = cfg.standalone.enabled;
		double village0 = cfg.villageCasino.chance;
		Survey off;
		WorldgenSpawnSurvey.Count villages;
		try {
			cfg.standalone.enabled = false;
			cfg.villageCasino.chance = 1;
			dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.refresh();
			dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure.clearCaches();
			off = CasinoQa.standalone(server).isEmpty() ? null : survey(server, CasinoQa.SET, new long[] {SEEDS[0]}, 1500);
			villages = WorldgenSpawnSurvey.run(server.registryAccess(), dev.nezo.burmaldaholic.worldgen.CasinoBuilder.templates(server), SEEDS[0], true,
				LevelStem.OVERWORLD, Level.OVERWORLD, "minecraft:villages", 1500);
		} finally {
			cfg.standalone.enabled = standalone0;
			cfg.villageCasino.chance = village0;
			dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.refresh();
			dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure.clearCaches();
		}
		CasinoQa.log("standalone off: {} | villages {}", off, villages);
		helper.assertTrue(off == null || off.placed() == 0, "standalone casinos started while disabled: " + off);
		helper.assertTrue(off == null || off.biomeValid() > 0, "harness: no biome-valid cell surveyed");
		helper.assertTrue(villages.casinos() > 0 && villages.casinos() >= 0.5 * villages.starts(), "village annexes must still attach (chance 1): " + villages);
		helper.succeed();
	}

	/** 11. The test world is written without DSYNC ({@code GameTestServerMixin}): the shutdown save no longer takes ~30 min. */
	@GameTest(environment = CasinoQa.ENV)
	public void gameTestServerSkipsSyncWrites(GameTestHelper helper) {
		helper.assertFalse(helper.getLevel().getServer().forceSynchronousWrites(), "GameTest server still forces synchronous chunk writes");
		helper.succeed();
	}

	/** Read-only view of the worldgen gate for the tests above. */
	private static final class WorldgenRuntimeView {
		static boolean active() {
			return dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.active();
		}

		static boolean standalone() {
			return dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.standaloneActive();
		}
	}

	record Survey(int placed, int biomeValid, double medianMs, double p99Ms, int medianNearest, String perSeed, List<Integer> nearest) {
		double rejected() {
			return biomeValid == 0 ? 1 : 1 - placed / (double) biomeValid;
		}

		@Override
		public String toString() {
			return String.format("placed %d of %d biome-valid cells (%d%% rejected) · start gen median %.2f ms p99 %.2f ms · median nearest %d · per seed %s",
				placed, biomeValid, Math.round(rejected() * 100), medianMs, p99Ms, medianNearest, perSeed);
		}
	}

	private static Survey survey(MinecraftServer server, Identifier setId, long[] seeds, int radius) {
		RegistryAccess reg = server.registryAccess();
		HolderLookup.RegistryLookup<StructureSet> sets = reg.lookupOrThrow(Registries.STRUCTURE_SET);
		StructureSet set = sets.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, setId)).value();
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
		LevelStem stem = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
			.createWorldDimensions().dimensions().get(LevelStem.OVERWORLD);
		ChunkGenerator gen = stem.generator();
		DimensionType type = stem.type().value();
		LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());
		int biomeValid = 0;
		int placed = 0;
		List<Double> millis = new ArrayList<>();
		List<Integer> nearest = new ArrayList<>();
		StringBuilder perSeed = new StringBuilder();
		StringBuilder near = new StringBuilder(); // rejected biome-valid cells near spawn (tuning)
		for (long seed : seeds) {
			RandomState rs = CasinoQa.randomState(((NoiseBasedChunkGenerator) gen).generatorSettings().value(), reg.lookupOrThrow(Registries.NOISE), seed);
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
					// biome where the jigsaw checks it: at the start projected onto the surface (WORLD_SURFACE_WG)
					int sy = gen.getBaseHeight(bx, bz, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, height, rs);
					for (StructureSet.StructureSelectionEntry e : set.structures()) {
						anyBiome |= CasinoQa.biomeMatches(gen, rs, bx, sy, bz, e.structure().value().biomes()::contains);
					}
					if (!anyBiome) {
						continue;
					}
					biomeValid++;
					var st = dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure.STATS;
					long f0 = st.steep.get(), w0 = st.flooded.get(), b0 = st.biome.get(), i0 = st.incomplete.get();
					long t0 = System.nanoTime();
					boolean ok = startAt(set, seed, chunk, reg, gen, rs, server, height);
					millis.add((System.nanoTime() - t0) / 1e6);
					if (ok) {
						placed++;
						seedPlaced++;
						best = Math.min(best, (int) Math.hypot(bx, bz));
					} else if (Math.hypot(bx, bz) < 1000) {
						near.append(seed).append("@").append(bx).append(",").append(bz).append(" flat ").append(st.steep.get() - f0).append(" flood ")
							.append(st.flooded.get() - w0).append(" biome ").append(st.biome.get() - b0).append(" incomplete ").append(st.incomplete.get() - i0).append("; ");
					}
				}
			}
			if (best != Integer.MAX_VALUE) {
				nearest.add(best);
			}
			perSeed.append(seed).append(": ").append(seedPlaced).append(" (nearest ").append(best == Integer.MAX_VALUE ? "-" : best).append(") ");
		}
		nearest.sort(Integer::compare);
		if (!near.isEmpty()) {
			CasinoQa.log("fit: rejected biome-valid cells within 1000 of spawn: {}", near);
		}
		return new Survey(placed, biomeValid, CasinoQa.quantile(millis, 0.5), CasinoQa.quantile(millis, 0.99),
			nearest.isEmpty() ? Integer.MAX_VALUE : nearest.get(nearest.size() / 2), perSeed.toString().trim(), List.copyOf(nearest));
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
			StructureStart start = CasinoQa.generate(holder, seed, Level.OVERWORLD, reg, gen, gen.getBiomeSource(), rs, CasinoQa.climateSampler(rs),
				dev.nezo.burmaldaholic.worldgen.CasinoBuilder.templates(server), chunk, height, (java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>>) holder.value().biomes()::contains);
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
	@GameTest(maxTicks = 100, environment = CasinoQa.ENV)
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

	/** O3: natural leaves under a roof inside a casino box are cleared; persistent (template) and open-air leaves stay. */
	@net.fabricmc.fabric.api.gametest.v1.GameTest
	public void foliageSweepClearsTreeLeavesIndoors(net.minecraft.gametest.framework.GameTestHelper helper) {
		var level = helper.getLevel();
		var natural = net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState();
		var persistent = natural.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		for (int x = 0; x <= 2; x++) {
			helper.setBlock(new net.minecraft.core.BlockPos(x, 5, 0), net.minecraft.world.level.block.Blocks.STONE_BRICKS); // roof
		}
		helper.setBlock(new net.minecraft.core.BlockPos(0, 2, 0), natural);
		helper.setBlock(new net.minecraft.core.BlockPos(1, 2, 0), persistent);
		helper.setBlock(new net.minecraft.core.BlockPos(4, 2, 0), natural); // no roof above
		net.minecraft.core.BlockPos a = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
		net.minecraft.core.BlockPos b = helper.absolutePos(new net.minecraft.core.BlockPos(4, 6, 0));
		var box = new dev.nezo.burmaldaholic.worldgen.logic.Geometry.Box(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
			Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
		int removed = dev.nezo.burmaldaholic.worldgen.CasinoFoliage.sweep(level, box);
		helper.assertTrue(removed == 1, "one indoor tree leaf removed, got " + removed);
		helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new net.minecraft.core.BlockPos(0, 2, 0));
		helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.OAK_LEAVES, new net.minecraft.core.BlockPos(1, 2, 0));
		helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.OAK_LEAVES, new net.minecraft.core.BlockPos(4, 2, 0));
		helper.succeed();
	}
}
