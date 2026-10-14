package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.LocateRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * {@code /casino locate} (every player): the nearest casino of the player's dimension.
 *
 * <ol>
 *   <li>Refused while casino mode is off; non-operators wait {@code worldgen.locateCooldownSeconds} between searches
 *       (in memory; operators and the singleplayer host are exempt).</li>
 *   <li>Known casinos: the nearest complete {@link CasinoIndex} record of this dimension.</li>
 *   <li>Not yet explored casinos (only while generation is active): the hosts' random-spread structure grids
 *       (villages, bastions, End Cities that generate in this dimension) are walked ring by ring like vanilla
 *       {@code /locate}, up to the kind's cap ({@link LocateRules#capChunks}) or the known casino's distance,
 *       whichever is nearer. Each candidate start is pre-filtered with the generation gate ({@link LocateRules#gate}),
 *       then checked by vanilla's structure check and finally CONFIRMED in the real structure start (chunk generated to
 *       {@code STRUCTURE_STARTS}, like vanilla {@code /locate}) — a reported casino always exists.</li>
 * </ol>
 * The search runs on the server thread; it is bounded by the caps, stops one ring after the first hit and gives up
 * after {@link #BUDGET_NANOS}.
 */
public final class CasinoLocator {
	/** Hard wall-clock limit of one predicted search (the server thread waits for structure starts). */
	static final long BUDGET_NANOS = 2_000_000_000L;
	private static final Map<UUID, Long> LAST_SEARCH = new ConcurrentHashMap<>();

	private CasinoLocator() {}

	public enum Status { FOUND, NONE, WRONG_DIMENSION, MODE_OFF, COOLDOWN }

	/**
	 * What a search found: {@code hit} for {@link Status#FOUND}; {@code radiusBlocks} is how far was searched
	 * ({@link Status#NONE}; 0 = only known casinos were looked at, generation is off); {@code cooldownSeconds} the wait
	 * for {@link Status#COOLDOWN}.
	 */
	public record Outcome(Status status, LocateRules.@Nullable Candidate hit, double distance, int radiusBlocks, long cooldownSeconds) {
		static Outcome of(Status status) {
			return new Outcome(status, null, 0, 0, 0);
		}
	}

	static void register() {
		CasinoCommands.extendForPlayers(root -> root.then(Commands.literal("locate").executes(c -> execute(c.getSource()))));
	}

	/** Forgets every cooldown (server stop). Kept across reconnects on purpose: relogging must not skip the wait. */
	public static void clear() {
		LAST_SEARCH.clear();
	}

	private static int execute(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.players_only"));
			return 0;
		}
		boolean operator = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
		boolean exempt = operator || CasinoCommands.isIntegratedHost(player);
		Outcome outcome = locate(player, exempt, System.currentTimeMillis(),
			() -> source.sendSuccess(() -> Component.translatable("msg.burmaldaholic.worldgen.locate.searching").withStyle(ChatFormatting.GRAY), false));
		switch (outcome.status()) {
			case MODE_OFF -> source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.mode_off"));
			case WRONG_DIMENSION -> source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.wrong_dimension"));
			case COOLDOWN -> source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.cooldown", Texts.number(outcome.cooldownSeconds())));
			case NONE -> source.sendFailure(outcome.radiusBlocks() > 0
				? Component.translatable("msg.burmaldaholic.worldgen.locate.none", Texts.number(outcome.radiusBlocks()))
				: Component.translatable("msg.burmaldaholic.worldgen.locate.none_known"));
			case FOUND -> source.sendSuccess(() -> found(player, outcome, operator), false);
		}
		return outcome.status() == Status.FOUND ? 1 : 0;
	}

	private static Component found(ServerPlayer player, Outcome outcome, boolean operator) {
		LocateRules.Candidate hit = outcome.hit();
		String plain = hit.x() + " " + hit.y() + " " + hit.z();
		ClickEvent click = operator ? new ClickEvent.SuggestCommand("/tp @s " + plain) : new ClickEvent.CopyToClipboard(plain);
		String hover = operator ? "gui.burmaldaholic.worldgen.locate.click_tp" : "gui.burmaldaholic.worldgen.locate.click_copy";
		MutableComponent coords = Texts.raw("[" + hit.x() + ", " + hit.y() + ", " + hit.z() + "]").withStyle(st -> st
			.withColor(ChatFormatting.GREEN).withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.translatable(hover))));
		String dir = LocateRules.direction(hit.x() + 0.5 - player.getX(), hit.z() + 0.5 - player.getZ());
		String key = hit.predicted() ? "msg.burmaldaholic.worldgen.locate.found_unexplored" : "msg.burmaldaholic.worldgen.locate.found";
		return Component.translatable(key, Component.translatable(hit.kind().nameKey()), coords,
			Texts.number(Math.round(outcome.distance())), Component.translatable("gui.burmaldaholic.worldgen.dir." + dir));
	}

	/**
	 * The whole search for {@code player} (server thread). {@code exempt}: no cooldown. {@code searching} runs once
	 * right before the (slower) structure-grid search starts.
	 */
	public static Outcome locate(ServerPlayer player, boolean exempt, long nowMillis, Runnable searching) {
		if (!CasinoMode.isEnabled(player)) {
			return Outcome.of(Status.MODE_OFF);
		}
		if (!exempt) {
			long left = LocateRules.cooldownLeftSeconds(LAST_SEARCH.get(player.getUUID()), nowMillis,
				CasinoConfig.worldgen().locateCooldownSeconds);
			if (left > 0) {
				return new Outcome(Status.COOLDOWN, null, 0, 0, left);
			}
		}
		Outcome outcome = search(player.level(), player.getX(), player.getZ(), searching);
		if (!exempt && outcome.status() != Status.WRONG_DIMENSION) {
			LAST_SEARCH.put(player.getUUID(), nowMillis);
		}
		return outcome;
	}

	/**
	 * The nearest casino of {@code level} from ({@code fromX}, {@code fromZ}) without the player checks:
	 * {@link Status#FOUND}, {@link Status#NONE} or {@link Status#WRONG_DIMENSION}.
	 */
	public static Outcome search(ServerLevel level, double fromX, double fromZ, Runnable searching) {
		return search(level, fromX, fromZ, searching, level.structureManager().shouldGenerateStructures());
	}

	/**
	 * {@link #search(ServerLevel, double, double, Runnable)} with an explicit "this world generates structures" flag
	 * (vanilla's {@code generate-structures}; without it no structure start, hence no unexplored casino, can exist).
	 * GameTests pass {@code true}: the GameTest server never generates structures, so they create the starts
	 * themselves.
	 */
	public static Outcome search(ServerLevel level, double fromX, double fromZ, Runnable searching, boolean structuresGenerate) {
		String dimension = level.dimension().identifier().toString();
		List<CasinoRecord> known = new ArrayList<>();
		for (CasinoRecord r : CasinoIndex.get(level.getServer()).casinos()) {
			if (r.box() != null && r.kind() != null && r.dimension().equals(dimension)) {
				known.add(r);
			}
		}
		List<Host> hosts = hosts(level);
		if (hosts.isEmpty() && known.isEmpty()) {
			return Outcome.of(Status.WRONG_DIMENSION);
		}
		List<LocateRules.Candidate> candidates = new ArrayList<>();
		for (CasinoRecord r : known) {
			candidates.add(candidate(r.kind(), r.box(), false));
		}
		Optional<LocateRules.Candidate> best = LocateRules.nearest(candidates, fromX, fromZ);
		int searchedChunks = 0;
		if (!hosts.isEmpty() && WorldgenRuntime.active() && structuresGenerate) {
			double knownChunks = best.map(c -> distance(c, fromX, fromZ) / 16.0).orElse(Double.MAX_VALUE);
			boolean announced = false;
			long deadline = System.nanoTime() + BUDGET_NANOS;
			for (Host host : hosts) {
				int radius = (int) Math.min(LocateRules.capChunks(host.kind()), Math.ceil(knownChunks));
				searchedChunks = Math.max(searchedChunks, radius);
				if (radius <= 0) {
					continue;
				}
				if (!announced) {
					searching.run();
					announced = true;
				}
				search(level, host, fromX, fromZ, radius, known, deadline).ifPresent(candidates::add);
			}
			best = LocateRules.nearest(candidates, fromX, fromZ);
		}
		if (best.isEmpty()) {
			return new Outcome(Status.NONE, null, 0, searchedChunks * 16, 0);
		}
		return new Outcome(Status.FOUND, best.get(), distance(best.get(), fromX, fromZ), searchedChunks * 16, 0);
	}

	private static double distance(LocateRules.Candidate c, double fromX, double fromZ) {
		return LocateRules.distance(fromX, fromZ, c.x() + 0.5, c.z() + 0.5);
	}

	/** Standing point of a casino: centre column, one above the floor layer. */
	private static LocateRules.Candidate candidate(CasinoKind kind, Geometry.Box box, boolean predicted) {
		return new LocateRules.Candidate(kind, box.centerX(), box.minY() + 1, box.centerZ(), predicted);
	}

	/** A host structure family with the random-spread grids it generates on in this dimension. */
	public record Host(CasinoKind kind, double chance, Map<RandomSpreadStructurePlacement, Set<Holder<Structure>>> grids) {}

	/** Casino hosts that generate in this level (empty in a dimension without villages, bastions and End Cities). */
	public static List<Host> hosts(ServerLevel level) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		List<Host> out = new ArrayList<>();
		List<Holder<Structure>> villages = new ArrayList<>();
		structures.get(StructureTags.VILLAGE).ifPresent(set -> set.forEach(villages::add));
		addHost(out, state, CasinoKind.VILLAGE_CASINO, WorldgenRuntime.villageChance(), villages);
		structures.get(BuiltinStructures.BASTION_REMNANT).ifPresent(h ->
			addHost(out, state, CasinoKind.PIGLIN_PARLOR, WorldgenRuntime.parlorChance(), List.of(h)));
		structures.get(BuiltinStructures.END_CITY).ifPresent(h ->
			addHost(out, state, CasinoKind.HIGH_ROLLER, WorldgenRuntime.loungeChance(), List.of(h)));
		return out;
	}

	private static void addHost(List<Host> out, ChunkGeneratorStructureState state, CasinoKind kind, double chance,
			List<? extends Holder<Structure>> holders) {
		Map<RandomSpreadStructurePlacement, Set<Holder<Structure>>> grids = new LinkedHashMap<>();
		for (Holder<Structure> holder : holders) {
			for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
				// Only the class-typed RandomSpreadStructurePlacement API is used: StructurePlacement is an interface
				// on 26.3 and a class on 26.2, so calling its own methods would not link on both.
				if (placement instanceof RandomSpreadStructurePlacement spread) {
					grids.computeIfAbsent(spread, k -> new LinkedHashSet<>()).add(holder);
				}
			}
		}
		if (!grids.isEmpty()) {
			out.add(new Host(kind, chance, grids));
		}
	}

	/** Nearest confirmed casino of {@code host} within {@code radiusChunks}; stops one ring after the first hit. */
	private static Optional<LocateRules.Candidate> search(ServerLevel level, Host host, double fromX, double fromZ, int radiusChunks,
			List<CasinoRecord> known, long deadline) {
		long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
		int fromChunkX = ((int) Math.floor(fromX)) >> 4;
		int fromChunkZ = ((int) Math.floor(fromZ)) >> 4;
		List<LocateRules.Candidate> found = new ArrayList<>();
		for (Map.Entry<RandomSpreadStructurePlacement, Set<Holder<Structure>>> grid : host.grids().entrySet()) {
			RandomSpreadStructurePlacement placement = grid.getKey();
			int spacing = placement.spacing();
			int cellX = Math.floorDiv(fromChunkX, spacing);
			int cellZ = Math.floorDiv(fromChunkZ, spacing);
			int maxRing = LocateRules.maxRing(radiusChunks, spacing);
			double bestChunks = Double.MAX_VALUE;
			for (int r = 0; r <= maxRing; r++) {
				if (!LocateRules.ringMayBeat(r, spacing, bestChunks)) {
					break;
				}
				for (int[] cell : LocateRules.ringCells(r)) {
					if (System.nanoTime() > deadline) {
						Burmaldaholic.LOGGER.info("[worldgen] /casino locate stopped at its time budget ({} ring {})", host.kind().id(), r);
						return LocateRules.nearest(found, fromX, fromZ);
					}
					ChunkPos start = placement.getPotentialStructureChunk(seed, (cellX + cell[0]) * spacing, (cellZ + cell[1]) * spacing);
					double startChunks = Math.hypot(start.x() - fromChunkX, start.z() - fromChunkZ);
					if (!LocateRules.withinRadius(startChunks, radiusChunks)
						|| !LocateRules.gate(host.kind(), host.chance(), seed, start.x(), start.z())) {
						continue;
					}
					Optional<LocateRules.Candidate> hit = confirm(level, start, grid.getValue(), placement, known);
					if (hit.isPresent()) {
						found.add(hit.get());
						bestChunks = Math.min(bestChunks, distance(hit.get(), fromX, fromZ) / 16.0);
					}
				}
			}
		}
		return LocateRules.nearest(found, fromX, fromZ);
	}

	/** The casino room inside the real structure start at {@code start}, if any. */
	private static Optional<LocateRules.Candidate> confirm(ServerLevel level, ChunkPos start, Set<Holder<Structure>> holders,
			RandomSpreadStructurePlacement placement, List<CasinoRecord> known) {
		for (Holder<Structure> holder : holders) {
			Structure structure = holder.value();
			if (level.structureManager().checkStructurePresence(start, structure, placement, false) == StructureCheckResult.START_NOT_PRESENT) {
				continue;
			}
			ChunkAccess chunk = level.getChunk(start.x(), start.z(), ChunkStatus.STRUCTURE_STARTS);
			StructureStart structureStart = chunk.getStartForStructure(structure);
			if (structureStart == null || !structureStart.isValid()) {
				continue;
			}
			for (StructurePiece piece : structureStart.getPieces()) {
				if (!(piece instanceof PoolElementStructurePiece pool)) {
					continue;
				}
				Optional<Layout> layout = CasinoElements.layoutOf(pool.getElement());
				if (layout.isEmpty()) {
					continue;
				}
				BoundingBox bb = pool.getBoundingBox();
				Geometry.Box box = CasinoStructures.box(bb);
				boolean indexed = known.stream().anyMatch(r -> r.box().contains(box.centerX(), box.minY() + 1, box.centerZ()));
				return Optional.of(candidate(layout.get().kind(), box, !indexed));
			}
		}
		return Optional.empty();
	}
}
