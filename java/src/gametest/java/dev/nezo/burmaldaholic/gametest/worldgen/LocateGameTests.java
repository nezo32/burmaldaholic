package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoLocator;
import dev.nezo.burmaldaholic.worldgen.CasinoPlacement;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.LocateRules;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * {@code /casino locate}: index path as a plain player, cooldown, server-wide limit, mode off, hosts per dimension,
 * predicted path per host type (v0.1 / v0.2 village casinos, v0.2 standalone casinos, Parlor, Lounge), cut-short /
 * busy answers, gate parity.
 */
public class LocateGameTests {
	/** A known casino right next to a non-operator mock player is found, by the API and by the real command. */
	@GameTest
	@SuppressWarnings("removal")
	public void locateFindsIndexedCasinoForPlainPlayerThenCoolsDown(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var cfg = CasinoConfig.worldgen();
		double chance = cfg.villageCasino.chance;
		int cooldown = cfg.locateCooldownSeconds;
		String dim = level.dimension().identifier().toString();
		int cx = (int) Math.floor(player.getX()) + 3;
		int cz = (int) Math.floor(player.getZ()) + 3;
		int y = (int) Math.floor(player.getY());
		String id = CasinoRecord.idFor(dim, cx, y, cz) + "|locate-test";
		CasinoIndex index = CasinoIndex.get(server);
		try {
			cfg.villageCasino.chance = 0; // no predicted village can beat the fake record
			cfg.locateCooldownSeconds = 30;
			CasinoLocator.clear();
			index.setAnchor(id, dim, Layouts.highRollerLayout().id(), CasinoKind.HIGH_ROLLER,
				new Geometry.Box(cx - 2, y - 1, cz - 2, cx + 2, y + 4, cz + 2));
			long t0 = System.nanoTime();

			CasinoLocator.Outcome direct = CasinoLocator.locate(player, true, t0, () -> {}, true);
			helper.assertTrue(direct.status() == CasinoLocator.Status.FOUND, "found: " + direct);
			LocateRules.Candidate hit = direct.hit();
			helper.assertTrue(hit.kind() == CasinoKind.HIGH_ROLLER && hit.x() == cx && hit.z() == cz && hit.y() == y && !hit.predicted(),
				"the indexed record: " + hit);
			helper.assertFalse(direct.truncated(), "first grid search ran in full: " + direct);
			// Server-wide limit: a second grid search within a second only gets the index answer, marked as such.
			CasinoLocator.Outcome soon = CasinoLocator.locate(player, true, t0 + 500_000_000L, () -> {}, true);
			helper.assertTrue(soon.status() == CasinoLocator.Status.FOUND && soon.truncated(), "second grid search within 1 s: " + soon);
			CasinoLocator.Outcome later1 = CasinoLocator.locate(player, true, t0 + 1_100_000_000L, () -> {}, true);
			helper.assertTrue(later1.status() == CasinoLocator.Status.FOUND && !later1.truncated(), "after 1 s: " + later1);
			CasinoLocator.clear();

			helper.assertFalse(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()), "mock player is not an operator");
			int result = server.getCommands().getDispatcher().execute("casino locate", player.createCommandSourceStack());
			helper.assertTrue(result == 1, "/casino locate as a plain player found it (result " + result + ")");

			CasinoLocator.Outcome again = CasinoLocator.locate(player, false, System.nanoTime(), () -> {});
			helper.assertTrue(again.status() == CasinoLocator.Status.COOLDOWN && again.cooldownSeconds() > 0 && again.cooldownSeconds() <= 30,
				"second search within the cooldown is refused: " + again);
			int refused = server.getCommands().getDispatcher().execute("casino locate", player.createCommandSourceStack());
			helper.assertTrue(refused == 0, "command refused during the cooldown");
			CasinoLocator.Outcome exempt = CasinoLocator.locate(player, true, System.nanoTime(), () -> {});
			helper.assertTrue(exempt.status() == CasinoLocator.Status.FOUND, "operators are exempt: " + exempt);
			CasinoLocator.Outcome later = CasinoLocator.locate(player, false, System.nanoTime() + 31_000_000_000L, () -> {});
			helper.assertTrue(later.status() == CasinoLocator.Status.FOUND, "after the cooldown: " + later);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			helper.fail("/casino locate not usable by a plain player: " + e.getMessage());
		} finally {
			cfg.villageCasino.chance = chance;
			cfg.locateCooldownSeconds = cooldown;
			index.removeIf(r -> r.id().equals(id));
			CasinoLocator.clear();
			server.getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void locateRefusedWhileModeOff(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean before = CasinoMode.isEnabled(server);
		try {
			CasinoLocator.clear();
			CasinoMode.set(server, false);
			CasinoLocator.Outcome off = CasinoLocator.locate(player, false, System.nanoTime(), () -> {});
			helper.assertTrue(off.status() == CasinoLocator.Status.MODE_OFF, "refused while casino mode is off: " + off);
			int result = server.getCommands().getDispatcher().execute("casino locate", player.createCommandSourceStack());
			helper.assertTrue(result == 0, "command refused while casino mode is off");
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			helper.fail("/casino locate not usable: " + e.getMessage());
		} finally {
			CasinoMode.set(server, before);
			CasinoLocator.clear();
			server.getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * Villages and the v0.2 standalone casinos ({@code #burmaldaholic:casino}) host casinos in the Overworld, bastions
	 * in the Nether, End Cities in the End — nothing else. A dimension whose biomes cannot hold its host has none
	 * (26.3's GameTest Nether is a flat level without bastion biomes).
	 */
	@GameTest
	public void hostsFollowTheDimension(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel overworldLevel = server.getLevel(Level.OVERWORLD);
		List<CasinoKind> overworld = kinds(overworldLevel);
		// standalone casinos are a host wherever the level's generator places the burmaldaholic:casinos set: always on
		// a noise Overworld; the GameTest superflat Overworld lists only villages on 26.2 but every set on 26.3
		var tag = overworldLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(TagKey.create(Registries.STRUCTURE, Identifier.parse(PackFiles.TAG)));
		var genState = overworldLevel.getChunkSource().getGeneratorState();
		boolean standalone = tag.isPresent() && tag.get().stream().anyMatch(h -> !genState.getPlacementsForStructure(h).isEmpty());
		if (overworldLevel.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
				&& tag.isPresent() && tag.get().size() > 0) {
			helper.assertTrue(standalone, "a noise Overworld places the standalone casino set");
		}
		List<CasinoKind> expected = standalone ? List.of(CasinoKind.VILLAGE_CASINO, CasinoKind.CASINO) : List.of(CasinoKind.VILLAGE_CASINO);
		helper.assertTrue(overworld.equals(expected), "overworld hosts " + overworld + ", expected " + expected);
		check(helper, server.getLevel(Level.NETHER), CasinoKind.PIGLIN_PARLOR);
		check(helper, server.getLevel(Level.END), CasinoKind.HIGH_ROLLER);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, ServerLevel level, CasinoKind only) {
		if (level == null) {
			return; // dimension disabled on this test server
		}
		List<CasinoKind> kinds = kinds(level);
		helper.assertTrue(kinds.isEmpty() || kinds.equals(List.of(only)), level.dimension().identifier() + " hosts " + kinds);
		if (level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) {
			helper.assertTrue(kinds.equals(List.of(only)), level.dimension().identifier() + " (noise generator) hosts " + kinds);
		}
	}

	private static List<CasinoKind> kinds(ServerLevel level) {
		return CasinoLocator.hosts(level).stream().map(CasinoLocator.Host::kind).toList();
	}

	/**
	 * Predicted path, for EVERY host type the test server can generate (26.2: Overworld villages + Nether bastions;
	 * 26.3, whose GameTest Nether is flat without bastions: villages; End Cities wherever the GameTest End has them).
	 * In an unexplored spot with the casino chance forced to 1, the host starts of the nine grid cells around it are
	 * created exactly like chunk generation does ({@code createStructures} + {@code onStructureStartsAvailable}; the
	 * GameTest server itself never generates structures). The locator must then report the nearest of those casinos as
	 * unexplored: found through the grid, the gate, the vanilla structure check and the real structure start.
	 */
	@GameTest(maxTicks = 600)
	public void predictsUnexploredCasinoForEveryHost(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<CasinoKind> verified = new ArrayList<>();
		int spot = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (CasinoLocator.Host host : CasinoLocator.hosts(level)) {
				// standalone casinos skip unfit cells (biome, terrain): a few fresh spots until one holds casinos
				int tries = host.kind() == CasinoKind.CASINO ? 3 : 1;
				for (int t = 0; t < tries; t++) {
					if (predict(helper, level, host, spot++)) {
						verified.add(host.kind());
						break;
					}
				}
			}
		}
		Burmaldaholic.LOGGER.info("[locate test] predicted path verified for {}", verified);
		helper.assertTrue(verified.contains(CasinoKind.VILLAGE_CASINO), "village casinos predicted: " + verified);
		if (CasinoLocator.hosts(server.getLevel(Level.OVERWORLD)).stream().anyMatch(h -> h.kind() == CasinoKind.CASINO)) {
			helper.assertTrue(verified.contains(CasinoKind.CASINO), "standalone casinos predicted: " + verified);
		}
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether != null && !CasinoLocator.hosts(nether).isEmpty()) {
			helper.assertTrue(verified.contains(CasinoKind.PIGLIN_PARLOR), "Piglin Parlors predicted: " + verified);
		}
		helper.succeed();
	}

	/**
	 * One host: prepares the nine cells (25 for the sparser standalone casinos), searches from 100 blocks east of a
	 * prepared casino, checks the answer. Each prepared casino is the union box of its start's casino pieces
	 * ({@link CasinoPlacement#of}, what the index records; a standalone casino has several pieces). Returns false
	 * (nothing asserted) only for End Cities when none of the cells got a lounge (it needs a tower top) and for
	 * standalone casinos when none of the cells fits one (biome / terrain).
	 */
	private static boolean predict(GameTestHelper helper, ServerLevel level, CasinoLocator.Host host, int spot) {
		double before = chance(host.kind());
		// Somewhere new on every run (the test world may be reused), far from spawn and other tests.
		long salt = System.nanoTime();
		double x = 40_000 + Math.floorMod(salt, 20_000L) + spot * 50_000;
		double z = -40_000 - Math.floorMod(salt / 7, 20_000L);
		List<Geometry.Box> prepared = new ArrayList<>();
		String dim = level.dimension().identifier().toString();
		int rings = host.kind() == CasinoKind.CASINO ? 2 : 1;
		CasinoLocator.Outcome outcome;
		double fromX;
		double fromZ;
		try {
			setChance(host.kind(), 1.0);
			RandomSpreadStructurePlacement placement = host.grids().keySet().iterator().next();
			ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
			StructureTemplateManager templates = CasinoBuilder.templates(level.getServer());
			int spacing = placement.spacing();
			int cellX = Math.floorDiv(((int) Math.floor(x)) >> 4, spacing);
			int cellZ = Math.floorDiv(((int) Math.floor(z)) >> 4, spacing);
			for (int r = 0; r <= rings; r++) {
				for (int[] cell : LocateRules.ringCells(r)) {
					ChunkPos p = placement.getPotentialStructureChunk(state.getLevelSeed(), (cellX + cell[0]) * spacing, (cellZ + cell[1]) * spacing);
					ChunkAccess chunk = level.getChunk(p.x(), p.z(), ChunkStatus.STRUCTURE_STARTS);
					level.getChunkSource().getGenerator().createStructures(level.registryAccess(), state, level.structureManager(), chunk,
						templates, level.dimension());
					level.onStructureStartsAvailable(chunk);
					for (var holder : host.grids().get(placement)) {
						StructureStart start = chunk.getStartForStructure(holder.value());
						if (start == null || !start.isValid()) {
							continue;
						}
						CasinoPlacement.Context casino = CasinoPlacement.of(dim, start.getPieces());
						if (casino != null) {
							helper.assertTrue(casino.kind() == host.kind(), host.kind().id() + " start holds a " + casino.kind() + " casino");
							prepared.add(casino.box());
						}
					}
				}
			}
			Burmaldaholic.LOGGER.info("[locate test] {} casinos created around {}, {}: {}", host.kind().id(), x, z, prepared.size());
			if (prepared.isEmpty() && (host.kind() == CasinoKind.HIGH_ROLLER || host.kind() == CasinoKind.CASINO)) {
				return false;
			}
			helper.assertTrue(!prepared.isEmpty(), "no " + host.kind().id() + " host with a casino in the nine cells around " + x + ", " + z);
			// Search from 100 blocks east of the first one: well inside every cap.
			Geometry.Box first = prepared.getFirst();
			fromX = first.centerX() + 100;
			fromZ = first.centerZ();
			outcome = CasinoLocator.search(level, fromX, fromZ, () -> {},
				new CasinoLocator.Limits(true, () -> true, CasinoLocator.BUDGET_NANOS));
		} finally {
			setChance(host.kind(), before);
		}
		Burmaldaholic.LOGGER.info("[locate test] {} search from {}, {}: {}", host.kind().id(), fromX, fromZ, outcome);
		helper.assertTrue(outcome.status() == CasinoLocator.Status.FOUND && !outcome.truncated(),
			host.kind().id() + " predicted within the cap, not cut short: " + outcome);
		LocateRules.Candidate hit = outcome.hit();
		helper.assertTrue(hit.kind() == host.kind() && hit.predicted(), "predicted " + host.kind().id() + ": " + hit);
		helper.assertTrue(prepared.stream().anyMatch(b -> b.contains(hit.x(), hit.y(), hit.z())),
			"the reported point lies in a real casino: " + hit + " in " + prepared);
		// Nearest of the prepared ones (none may be skipped).
		double sx = fromX;
		double sz = fromZ;
		double best = prepared.stream().mapToDouble(b -> Math.hypot(b.centerX() + 0.5 - sx, b.centerZ() + 0.5 - sz))
			.min().orElseThrow();
		helper.assertTrue(outcome.distance() <= best + 2, "nearest prepared casino: " + outcome.distance() + " vs " + best);
		return true;
	}

	/**
	 * Predicted path for the v0.2 standalone casino (structure set {@code burmaldaholic:casinos}). The GameTest
	 * Overworld is superflat and never places that set, so the host comes from the set itself
	 * ({@link CasinoLocator#standaloneHost}) and the starts of 25 grid cells are generated with the real
	 * {@code CasinoStructure} (jigsaw + completeness + terrain fit) and stored like chunk generation does. The locator
	 * must report the nearest one as an unexplored CASINO, confirmed in the real (multi-piece) start, with its standing
	 * point inside the start's casino box.
	 */
	@GameTest(maxTicks = 600)
	public void predictsUnexploredStandaloneCasino(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().getLevel(Level.OVERWORLD);
		var hostOpt = CasinoLocator.standaloneHost(level);
		if (hostOpt.isEmpty()) {
			Burmaldaholic.LOGGER.info("[locate test] no {} structure set in the pack: standalone path skipped", PackFiles.SET);
			helper.succeed();
			return;
		}
		CasinoLocator.Host host = hostOpt.get();
		var entry = host.grids().entrySet().iterator().next();
		RandomSpreadStructurePlacement placement = entry.getKey();
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		long seed = state.getLevelSeed();
		String dim = level.dimension().identifier().toString();
		long salt = System.nanoTime();
		double x = 240_000 + Math.floorMod(salt, 20_000L);
		double z = 240_000 + Math.floorMod(salt / 7, 20_000L);
		int spacing = placement.spacing();
		int cellX = Math.floorDiv(((int) Math.floor(x)) >> 4, spacing);
		int cellZ = Math.floorDiv(((int) Math.floor(z)) >> 4, spacing);
		List<Geometry.Box> prepared = new ArrayList<>();
		for (int r = 0; r <= 2; r++) {
			for (int[] cell : LocateRules.ringCells(r)) {
				ChunkPos p = placement.getPotentialStructureChunk(seed, (cellX + cell[0]) * spacing, (cellZ + cell[1]) * spacing);
				ChunkAccess chunk = level.getChunk(p.x(), p.z(), ChunkStatus.STRUCTURE_STARTS);
				for (var holder : entry.getValue()) {
					var source = level.getChunkSource();
					StructureStart start = StructureGen.generate(holder, level.dimension(), level.registryAccess(), source.getGenerator(),
						source.randomState(), CasinoBuilder.templates(level.getServer()), seed, p, level, b -> true);
					if (start == null || !start.isValid()) {
						continue;
					}
					chunk.setStartForStructure(holder.value(), start);
					CasinoPlacement.Context casino = CasinoPlacement.of(dim, start.getPieces());
					helper.assertTrue(casino != null && casino.kind() == CasinoKind.CASINO, "standalone start holds a CASINO: " + casino);
					prepared.add(casino.box());
					break; // one structure per cell, like the set
				}
				level.onStructureStartsAvailable(chunk);
			}
		}
		Burmaldaholic.LOGGER.info("[locate test] casino (standalone) starts created around {}, {}: {}", x, z, prepared.size());
		helper.assertTrue(!prepared.isEmpty(), "no standalone casino fits any of the 25 superflat cells around " + x + ", " + z);
		Geometry.Box first = prepared.getFirst();
		double fromX = first.centerX() + 100;
		double fromZ = first.centerZ();
		CasinoLocator.Outcome outcome = CasinoLocator.search(level, fromX, fromZ, () -> {},
			new CasinoLocator.Limits(true, () -> true, CasinoLocator.BUDGET_NANOS), List.of(host));
		Burmaldaholic.LOGGER.info("[locate test] casino search from {}, {}: {}", fromX, fromZ, outcome);
		helper.assertTrue(outcome.status() == CasinoLocator.Status.FOUND && !outcome.truncated(), "standalone predicted, not cut short: " + outcome);
		LocateRules.Candidate hit = outcome.hit();
		helper.assertTrue(hit.kind() == CasinoKind.CASINO && hit.predicted(), "predicted standalone casino: " + hit);
		helper.assertTrue(prepared.stream().anyMatch(b -> b.contains(hit.x(), hit.y(), hit.z())), "the reported point lies in a real casino: " + hit);
		double best = prepared.stream().mapToDouble(b -> Math.hypot(b.centerX() + 0.5 - fromX, b.centerZ() + 0.5 - fromZ)).min().orElseThrow();
		helper.assertTrue(outcome.distance() <= best + 2, "nearest prepared standalone casino: " + outcome.distance() + " vs " + best);
		helper.succeed();
	}

	/**
	 * Truncation honesty: a grid search that runs out of time (budget 0) or is refused by the server-wide limit says
	 * so: FOUND / NONE with {@code truncated}, or BUSY when nothing is known.
	 */
	@GameTest
	public void cutShortAndBusyAnswersSaySo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().getLevel(Level.OVERWORLD);
		var cfg = CasinoConfig.worldgen();
		double before = cfg.villageCasino.chance;
		String dim = level.dimension().identifier().toString();
		long salt = System.nanoTime();
		double x = -140_000 - Math.floorMod(salt, 20_000L);
		double z = 140_000;
		String id = CasinoRecord.idFor(dim, (int) x + 100, 70, (int) z) + "|locate-cut-test";
		CasinoIndex index = CasinoIndex.get(level.getServer());
		CasinoLocator.Limits outOfTime = new CasinoLocator.Limits(true, () -> true, 0);
		CasinoLocator.Limits refused = new CasinoLocator.Limits(true, () -> false, CasinoLocator.BUDGET_NANOS);
		// A reused test world may hold casinos registered by earlier runs anywhere in the dimension: then the answer
		// is that (far) known casino, still marked as cut short.
		boolean anyKnown = index.casinos().stream().anyMatch(r -> r.box() != null && r.kind() != null && r.dimension().equals(dim));
		try {
			cfg.villageCasino.chance = 1.0; // every cell passes the gate, so only the budget stops the search
			CasinoLocator.Outcome none = CasinoLocator.search(level, x, z, () -> {}, outOfTime);
			helper.assertTrue(none.truncated() && none.radiusBlocks() > 0
				&& none.status() == (anyKnown ? CasinoLocator.Status.FOUND : CasinoLocator.Status.NONE),
				"out of time, nothing near: NONE (or the far known casino) + truncated: " + none);
			CasinoLocator.Outcome busy = CasinoLocator.search(level, x, z, () -> helper.fail("no grid search when refused"), refused);
			helper.assertTrue(anyKnown ? busy.status() == CasinoLocator.Status.FOUND && busy.truncated() : busy.status() == CasinoLocator.Status.BUSY,
				"refused, nothing near: BUSY (or the far known casino + truncated): " + busy);

			index.setAnchor(id, dim, Layouts.highRollerLayout().id(), CasinoKind.HIGH_ROLLER,
				new Geometry.Box((int) x + 98, 69, (int) z - 2, (int) x + 102, 74, (int) z + 2));
			CasinoLocator.Outcome known = CasinoLocator.search(level, x, z, () -> {}, outOfTime);
			helper.assertTrue(known.status() == CasinoLocator.Status.FOUND && known.truncated() && !known.hit().predicted(),
				"out of time, known casino: FOUND + truncated: " + known);
			CasinoLocator.Outcome knownBusy = CasinoLocator.search(level, x, z, () -> {}, refused);
			helper.assertTrue(knownBusy.status() == CasinoLocator.Status.FOUND && knownBusy.truncated(),
				"refused, known casino: index answer + truncated: " + knownBusy);
		} finally {
			cfg.villageCasino.chance = before;
			index.removeIf(r -> r.id().equals(id));
		}
		helper.succeed();
	}

	/**
	 * Gate parity in real structure starts (the locator's pre-filter must never hide a casino): with the chances at
	 * 0.5, starts created like chunk generation does in fresh Overworld / Nether cells only ever hold a casino where
	 * {@link LocateRules#gate} passes for that start chunk, and the casino is of the host's kind (village starts: v0.1
	 * room or v0.2 annex; {@code burmaldaholic:casinos} starts: a standalone casino; bastions: Parlor v1 / v2).
	 */
	@GameTest(maxTicks = 600)
	public void gatePreFilterMatchesRealGeneration(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var cfg = CasinoConfig.worldgen();
		double village = cfg.villageCasino.chance;
		double parlor = cfg.piglinParlor.chance;
		int[] counts = new int[3]; // starts, casinos, gate passes
		try {
			cfg.villageCasino.chance = 0.5;
			cfg.piglinParlor.chance = 0.5;
			long salt = System.nanoTime();
			parity(helper, server.getLevel(Level.OVERWORLD), -60_000 - (int) Math.floorMod(salt, 20_000L), 60_000, 3, counts);
			ServerLevel nether = server.getLevel(Level.NETHER);
			if (nether != null) {
				parity(helper, nether, 60_000 + (int) Math.floorMod(salt, 20_000L), 60_000, 2, counts);
			}
		} finally {
			cfg.villageCasino.chance = village;
			cfg.piglinParlor.chance = parlor;
		}
		Burmaldaholic.LOGGER.info("[locate test] gate parity: {} host starts, {} with a casino, {} passed the gate", counts[0], counts[1], counts[2]);
		helper.assertTrue(counts[0] > 0, "no host structure start created");
		helper.succeed();
	}

	private static void parity(GameTestHelper helper, ServerLevel level, int blockX, int blockZ, int rings, int[] counts) {
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		StructureTemplateManager templates = CasinoBuilder.templates(level.getServer());
		for (CasinoLocator.Host host : CasinoLocator.hosts(level)) {
			for (var grid : host.grids().entrySet()) {
				RandomSpreadStructurePlacement placement = grid.getKey();
				int spacing = placement.spacing();
				int cellX = Math.floorDiv(blockX >> 4, spacing);
				int cellZ = Math.floorDiv(blockZ >> 4, spacing);
				for (int r = 0; r <= rings; r++) {
					for (int[] cell : LocateRules.ringCells(r)) {
						ChunkPos p = placement.getPotentialStructureChunk(state.getLevelSeed(), (cellX + cell[0]) * spacing, (cellZ + cell[1]) * spacing);
						ChunkAccess chunk = level.getChunk(p.x(), p.z(), ChunkStatus.STRUCTURE_STARTS);
						if (chunk.getAllStarts().isEmpty()) {
							level.getChunkSource().getGenerator().createStructures(level.registryAccess(), state, level.structureManager(), chunk,
								templates, level.dimension());
						}
						for (var holder : grid.getValue()) {
							StructureStart start = chunk.getStartForStructure(holder.value());
							if (start == null || !start.isValid()) {
								continue;
							}
							counts[0]++;
							boolean gate = LocateRules.gate(host.kind(), host.chance(), state.getLevelSeed(), p.x(), p.z());
							counts[2] += gate ? 1 : 0;
							CasinoPlacement.Context casino = CasinoPlacement.of(level.dimension().identifier().toString(), start.getPieces());
							if (casino != null) {
								counts[1]++;
								helper.assertTrue(gate, host.kind().id() + " casino at start chunk " + p + " but the locator's gate says no");
								helper.assertTrue(casino.kind() == host.kind(), host.kind().id() + " start at " + p + " holds a " + casino.kind());
							}
						}
					}
				}
			}
		}
	}

	private static double chance(CasinoKind kind) {
		var cfg = CasinoConfig.worldgen();
		return switch (kind) {
			case VILLAGE_CASINO -> cfg.villageCasino.chance;
			case PIGLIN_PARLOR -> cfg.piglinParlor.chance;
			case HIGH_ROLLER -> cfg.highRoller.chance;
			case CASINO -> 1.0; // no chance setting: its own structure set decides
		};
	}

	private static void setChance(CasinoKind kind, double value) {
		var cfg = CasinoConfig.worldgen();
		switch (kind) {
			case VILLAGE_CASINO -> cfg.villageCasino.chance = value;
			case PIGLIN_PARLOR -> cfg.piglinParlor.chance = value;
			case HIGH_ROLLER -> cfg.highRoller.chance = value;
			case CASINO -> { }
		}
	}
}
