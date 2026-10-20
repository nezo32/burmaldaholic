package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.LocateRules;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
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
import net.minecraft.resources.Identifier;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * {@code /casino locate} (every player): the nearest casino of the player's dimension.
 *
 * <ol>
 *   <li>Refused while casino mode is off; non-operators wait {@code worldgen.locateCooldownSeconds} between searches
 *       (in memory, monotonic time; operators and the singleplayer host are exempt).</li>
 *   <li>Known casinos: the nearest complete {@link CasinoIndex} record of this dimension.</li>
 *   <li>Not yet explored casinos (only while generation is active): the hosts' random-spread structure grids
 *       (villages — v0.1 casino or v0.2 annex —, the v0.2 standalone casinos of {@code #burmaldaholic:casino}, bastions
 *       and End Cities — Parlor / Lounge v1 or v2 — that generate in this dimension) are walked ring by ring like vanilla
 *       {@code /locate}, up to the kind's cap ({@link LocateRules#capChunks}) or the known casino's distance,
 *       whichever is nearer. Each candidate start inside the world border is pre-filtered with the generation gate
 *       ({@link LocateRules#gate}), then checked by vanilla's structure check and finally CONFIRMED in the real
 *       structure start (chunk generated to {@code STRUCTURE_STARTS}, like vanilla {@code /locate}) — a reported
 *       casino always exists.</li>
 * </ol>
 * The grid search runs on the server thread. It is bounded by the caps, stops at the first ring that can no longer
 * beat the best hit ({@link LocateRules#ringMayBeat}), gives up after {@link #BUDGET_NANOS} (checked before every
 * structure check and chunk load) and runs at most once per {@link LocateRules#GRID_SEARCH_GAP_NANOS} server-wide;
 * an answer that may miss a closer casino says so ({@link Outcome#truncated()}).
 */
public final class CasinoLocator {
	/** Hard wall-clock limit of one grid search (the server thread waits for structure starts). */
	public static final long BUDGET_NANOS = 2_000_000_000L;
	/** Last search per player, monotonic nanos ({@link System#nanoTime()}). */
	private static final Map<UUID, Long> LAST_SEARCH = new ConcurrentHashMap<>();
	/** Start of the last grid search on this server (monotonic nanos), {@code null} = none yet. */
	private static @Nullable Long lastGridSearch;

	private CasinoLocator() {}

	public enum Status { FOUND, NONE, WRONG_DIMENSION, MODE_OFF, COOLDOWN, BUSY }

	/**
	 * What a search found: {@code hit} for {@link Status#FOUND}; {@code radiusBlocks} is how far was searched
	 * ({@link Status#NONE}; 0 = only known casinos were looked at); {@code truncated}: the grid search was cut short
	 * (time budget) or skipped (server busy), so a closer casino may exist; {@code cooldownSeconds} the wait for
	 * {@link Status#COOLDOWN}.
	 */
	public record Outcome(Status status, LocateRules.@Nullable Candidate hit, double distance, int radiusBlocks, boolean truncated,
			long cooldownSeconds) {
		static Outcome of(Status status) {
			return new Outcome(status, null, 0, 0, false, 0);
		}
	}

	/**
	 * Limits of one search: {@code structuresGenerate} (vanilla's {@code generate-structures}; without it no structure
	 * start, hence no unexplored casino, can exist), {@code mayRunGrid} (asked once, right before the grid search would
	 * start; {@code false} = skip it and answer from the index only) and the time budget.
	 */
	public record Limits(boolean structuresGenerate, BooleanSupplier mayRunGrid, long budgetNanos) {
		/** The level's own structure setting, no server-wide limit, the normal budget. */
		public static Limits of(ServerLevel level) {
			return new Limits(level.structureManager().shouldGenerateStructures(), () -> true, BUDGET_NANOS);
		}
	}

	static void register() {
		CasinoCommands.extendForPlayers(root -> root.then(Commands.literal("locate").executes(c -> execute(c.getSource()))));
	}

	/**
	 * Forgets every cooldown and the server-wide limit (server stop). Cooldowns are kept across reconnects on
	 * purpose: relogging must not skip the wait.
	 */
	public static synchronized void clear() {
		LAST_SEARCH.clear();
		lastGridSearch = null;
	}

	/** Server-wide limit: claims the grid-search slot if the last grid search started long enough ago. */
	private static synchronized boolean tryStartGridSearch(long nowNanos) {
		if (!LocateRules.gridSearchAllowed(lastGridSearch, nowNanos)) {
			return false;
		}
		lastGridSearch = nowNanos;
		return true;
	}

	private static int execute(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.players_only"));
			return 0;
		}
		boolean operator = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
		boolean exempt = operator || CasinoCommands.isIntegratedHost(player);
		Outcome outcome = locate(player, exempt, System.nanoTime(),
			() -> source.sendSuccess(() -> Component.translatable("msg.burmaldaholic.worldgen.locate.searching").withStyle(ChatFormatting.GRAY), false));
		switch (outcome.status()) {
			case MODE_OFF -> source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.mode_off"));
			case WRONG_DIMENSION -> source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.wrong_dimension"));
			case COOLDOWN -> source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.cooldown", Texts.number(outcome.cooldownSeconds())));
			case BUSY -> source.sendFailure(Component.translatable("msg.burmaldaholic.worldgen.locate.busy"));
			case NONE -> source.sendFailure(none(outcome));
			case FOUND -> source.sendSuccess(() -> found(player, outcome, operator), false);
		}
		return outcome.status() == Status.FOUND ? 1 : 0;
	}

	private static Component none(Outcome outcome) {
		if (outcome.radiusBlocks() <= 0) {
			return Component.translatable("msg.burmaldaholic.worldgen.locate.none_known");
		}
		String key = outcome.truncated() ? "msg.burmaldaholic.worldgen.locate.none_partial" : "msg.burmaldaholic.worldgen.locate.none";
		return Component.translatable(key, Texts.number(outcome.radiusBlocks()));
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
		MutableComponent line = Component.translatable(key, Component.translatable(hit.kind().nameKey()), coords,
			Texts.number(Math.round(outcome.distance())), Component.translatable("gui.burmaldaholic.worldgen.dir." + dir));
		if (outcome.truncated()) {
			line.append(Texts.raw(" ")).append(Component.translatable("msg.burmaldaholic.worldgen.locate.cut_short").withStyle(ChatFormatting.GRAY));
		}
		return line;
	}

	/**
	 * The whole search for {@code player} (server thread). {@code exempt}: no cooldown; {@code nowNanos}: monotonic
	 * time ({@link System#nanoTime()}); {@code searching} runs once right before the (slower) grid search starts.
	 * The cooldown is charged BEFORE searching (a failing search still costs it) and refunded only for a dimension
	 * without casinos. The grid search also obeys the server-wide limit.
	 */
	public static Outcome locate(ServerPlayer player, boolean exempt, long nowNanos, Runnable searching) {
		return locate(player, exempt, nowNanos, searching, player.level().structureManager().shouldGenerateStructures());
	}

	/**
	 * {@link #locate(ServerPlayer, boolean, long, Runnable)} with an explicit "this world generates structures" flag
	 * (GameTests: the GameTest server never does).
	 */
	public static Outcome locate(ServerPlayer player, boolean exempt, long nowNanos, Runnable searching, boolean structuresGenerate) {
		if (!CasinoMode.isEnabled(player)) {
			return Outcome.of(Status.MODE_OFF);
		}
		UUID id = player.getUUID();
		Long previous = null;
		if (!exempt) {
			int cooldown = CasinoConfig.worldgen().locateCooldownSeconds;
			previous = LAST_SEARCH.get(id);
			long left = LocateRules.cooldownLeftSeconds(previous, nowNanos, cooldown);
			if (left > 0) {
				return new Outcome(Status.COOLDOWN, null, 0, 0, false, left);
			}
			// Drop expired entries so the map only ever holds players inside their cooldown.
			LAST_SEARCH.values().removeIf(t -> LocateRules.cooldownLeftSeconds(t, nowNanos, cooldown) == 0);
			LAST_SEARCH.put(id, nowNanos);
		}
		ServerLevel level = player.level();
		Limits limits = new Limits(structuresGenerate, () -> tryStartGridSearch(nowNanos), BUDGET_NANOS);
		Outcome outcome = search(level, player.getX(), player.getZ(), searching, limits);
		if (!exempt && outcome.status() == Status.WRONG_DIMENSION) {
			if (previous == null) {
				LAST_SEARCH.remove(id);
			} else {
				LAST_SEARCH.put(id, previous);
			}
		}
		return outcome;
	}

	/**
	 * The nearest casino of {@code level} from ({@code fromX}, {@code fromZ}) without the player checks and without
	 * the server-wide limit: {@link Status#FOUND}, {@link Status#NONE} or {@link Status#WRONG_DIMENSION}.
	 */
	public static Outcome search(ServerLevel level, double fromX, double fromZ, Runnable searching) {
		return search(level, fromX, fromZ, searching, Limits.of(level));
	}

	/**
	 * {@link #search(ServerLevel, double, double, Runnable)} with explicit {@link Limits}; may also answer
	 * {@link Status#BUSY} (grid search refused, no known casino). GameTests pass {@code structuresGenerate = true}:
	 * the GameTest server never generates structures, so they create the starts themselves.
	 */
	public static Outcome search(ServerLevel level, double fromX, double fromZ, Runnable searching, Limits limits) {
		return search(level, fromX, fromZ, searching, limits, hosts(level));
	}

	/**
	 * {@link #search(ServerLevel, double, double, Runnable, Limits)} over explicit {@code hosts} (GameTests: the
	 * GameTest Overworld is superflat, whose generator only places villages, so the standalone casino host is built
	 * from its structure set by hand — {@link #standaloneHost}).
	 */
	public static Outcome search(ServerLevel level, double fromX, double fromZ, Runnable searching, Limits limits, List<Host> hosts) {
		String dimension = level.dimension().identifier().toString();
		List<CasinoRecord> known = new ArrayList<>();
		for (CasinoRecord r : CasinoIndex.get(level.getServer()).casinos()) {
			if (r.box() != null && r.kind() != null && r.dimension().equals(dimension)) {
				known.add(r);
			}
		}
		if (hosts.isEmpty() && known.isEmpty()) {
			return Outcome.of(Status.WRONG_DIMENSION);
		}
		List<LocateRules.Candidate> candidates = new ArrayList<>();
		for (CasinoRecord r : known) {
			candidates.add(candidate(r.kind(), r.box(), false));
		}
		Optional<LocateRules.Candidate> best = LocateRules.nearest(candidates, fromX, fromZ);
		int searchedChunks = 0;
		boolean busy = false;
		Budget budget = null;
		if (!hosts.isEmpty() && WorldgenRuntime.active() && limits.structuresGenerate()) {
			double knownChunks = best.map(c -> distance(c, fromX, fromZ) / 16.0).orElse(Double.MAX_VALUE);
			for (Host host : hosts) {
				int radius = (int) Math.min(LocateRules.capChunks(host.kind()), Math.ceil(knownChunks));
				if (radius <= 0) {
					continue;
				}
				if (budget == null) {
					if (!limits.mayRunGrid().getAsBoolean()) {
						busy = true;
						break;
					}
					searching.run();
					budget = new Budget(System.nanoTime() + limits.budgetNanos());
				}
				searchedChunks = Math.max(searchedChunks, radius);
				search(level, host, fromX, fromZ, radius, known, budget).ifPresent(candidates::add);
			}
			best = LocateRules.nearest(candidates, fromX, fromZ);
		}
		boolean truncated = busy || (budget != null && budget.expired);
		if (best.isPresent()) {
			return new Outcome(Status.FOUND, best.get(), distance(best.get(), fromX, fromZ), searchedChunks * 16, truncated, 0);
		}
		if (busy) {
			return Outcome.of(Status.BUSY);
		}
		return new Outcome(Status.NONE, null, 0, searchedChunks * 16, truncated, 0);
	}

	/** Time budget of one grid search; remembers whether it ran out. */
	private static final class Budget {
		private final long deadline;
		boolean expired;

		Budget(long deadline) {
			this.deadline = deadline;
		}

		/** True (and remembered) once the deadline has passed. */
		boolean out() {
			if (!expired && System.nanoTime() - deadline >= 0) {
				expired = true;
			}
			return expired;
		}
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

	/**
	 * Casino hosts that generate in this level (empty in a dimension without villages, standalone casinos, bastions and
	 * End Cities).
	 */
	public static List<Host> hosts(ServerLevel level) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		List<Host> out = new ArrayList<>();
		List<Holder<Structure>> villages = new ArrayList<>();
		structures.get(StructureTags.VILLAGE).ifPresent(set -> set.forEach(villages::add));
		addHost(out, state, CasinoKind.VILLAGE_CASINO, WorldgenRuntime.villageChance(), villages);
		// v0.2 standalone casinos: their own set (burmaldaholic:casinos), every style listed in the structure tag
		List<Holder<Structure>> standalone = new ArrayList<>();
		structures.get(TagKey.create(Registries.STRUCTURE, Identifier.parse(PackFiles.TAG))).ifPresent(set -> set.forEach(standalone::add));
		if (WorldgenRuntime.standaloneActive()) { // worldgen.standalone.enabled off: no new standalone casino can exist
			addHost(out, state, CasinoKind.CASINO, 1.0, standalone);
		}
		structures.get(BuiltinStructures.BASTION_REMNANT).ifPresent(h ->
			addHost(out, state, CasinoKind.PIGLIN_PARLOR, WorldgenRuntime.parlorChance(), List.of(h)));
		structures.get(BuiltinStructures.END_CITY).ifPresent(h ->
			addHost(out, state, CasinoKind.HIGH_ROLLER, WorldgenRuntime.loungeChance(), List.of(h)));
		return out;
	}

	/**
	 * The standalone casino host straight from the {@code burmaldaholic:casinos} structure set, whether or not this
	 * level's generator places it (empty when the pack has no such set, or while {@code worldgen.standalone.enabled}
	 * is off).
	 */
	public static Optional<Host> standaloneHost(ServerLevel level) {
		if (!WorldgenRuntime.standaloneActive()) {
			return Optional.empty(); // worldgen.standalone.enabled off: nothing unexplored to predict
		}
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
			.getValue(Identifier.parse(PackFiles.SET)) instanceof net.minecraft.world.level.levelgen.structure.StructureSet set
			&& set.placement() instanceof RandomSpreadStructurePlacement spread
			? Optional.of(new Host(CasinoKind.CASINO, 1.0, Map.of(spread, set.structures().stream()
				.map(net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry::structure)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))))
			: Optional.empty();
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

	/**
	 * Nearest confirmed casino of {@code host} within {@code radiusChunks}: rings outward until a ring can no longer
	 * beat the best hit ({@link LocateRules#ringMayBeat}) or the budget runs out (then the best so far).
	 */
	private static Optional<LocateRules.Candidate> search(ServerLevel level, Host host, double fromX, double fromZ, int radiusChunks,
			List<CasinoRecord> known, Budget budget) {
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
					if (budget.out()) {
						Burmaldaholic.LOGGER.info("[worldgen] /casino locate stopped at its time budget ({} ring {})", host.kind().id(), r);
						return LocateRules.nearest(found, fromX, fromZ);
					}
					ChunkPos start = placement.getPotentialStructureChunk(seed, (cellX + cell[0]) * spacing, (cellZ + cell[1]) * spacing);
					double startChunks = Math.hypot(start.x() - fromChunkX, start.z() - fromChunkZ);
					if (!LocateRules.withinRadius(startChunks, radiusChunks)
						|| !level.getWorldBorder().isWithinBounds(start)
						|| !LocateRules.gate(host.kind(), host.chance(), seed, start.x(), start.z())) {
						continue;
					}
					Optional<LocateRules.Candidate> hit = confirm(level, start, grid.getValue(), placement, known, budget);
					if (hit.isPresent()) {
						found.add(hit.get());
						bestChunks = Math.min(bestChunks, distance(hit.get(), fromX, fromZ) / 16.0);
					}
				}
			}
		}
		return LocateRules.nearest(found, fromX, fromZ);
	}

	/** The casino room inside the real structure start at {@code start}, if any (empty once the budget is out). */
	private static Optional<LocateRules.Candidate> confirm(ServerLevel level, ChunkPos start, Set<Holder<Structure>> holders,
			RandomSpreadStructurePlacement placement, List<CasinoRecord> known, Budget budget) {
		for (Holder<Structure> holder : holders) {
			Structure structure = holder.value();
			if (budget.out()) {
				return Optional.empty();
			}
			if (level.structureManager().checkStructurePresence(start, structure, placement, false) == StructureCheckResult.START_NOT_PRESENT) {
				continue;
			}
			if (budget.out()) {
				return Optional.empty();
			}
			ChunkAccess chunk = level.getChunk(start.x(), start.z(), ChunkStatus.STRUCTURE_STARTS);
			StructureStart structureStart = chunk.getStartForStructure(structure);
			if (structureStart == null || !structureStart.isValid()) {
				continue;
			}
			// the start's casino (v0.1 room, v0.2 annex / Parlor / Lounge, or every piece of a standalone casino): the
			// same kind + union box the markers register (CasinoPlacement), so "indexed" matches the CasinoIndex record
			CasinoPlacement.Context casino = CasinoPlacement.of(level.dimension().identifier().toString(), structureStart.getPieces());
			if (casino == null) {
				continue;
			}
			Geometry.Box box = casino.box();
			boolean indexed = known.stream().anyMatch(r -> r.box().contains(box.centerX(), box.minY() + 1, box.centerZ()));
			return Optional.of(candidate(casino.kind(), box, !indexed));
		}
		return Optional.empty();
	}
}
