package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.MenuRequestPayload;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoStructures;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.npc.CasinoNpcEntity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Casino QA toolkit (lane CB-5, docs/architecture/casino-buildings.md §9): what casinos exist, placing them
 * the way worldgen does, reading back their game blocks from the WORLD (never from layout code), and the
 * real-player flow (use-item-on packet → menu, C2S payload → bet).
 *
 * <p>Contract-driven and version-tolerant: v0.2 content (the {@code burmaldaholic:casino_<style>} structures,
 * the {@code casino/…} layouts, {@code CasinoKind.CASINO}) is discovered at runtime by id, so these tests run
 * against the frozen v0.1 templates today and switch to the new buildings the moment they are merged.
 * {@code -Dburmaldaholic.requireCasinoV2=true} turns "v0.2 content missing" into a failure (integration gate).
 */
public final class CasinoQa {
	/**
	 * GameTest environment of the long casino / survey tests (CasinoBuildingGameTests, WorldgenSpawnSurvey,
	 * OtherworldFitSurvey): their own batch, so seconds of synchronous survey work on the server thread never run in
	 * the same batch as the tick-timed bot tests (a full build lost 3-5 bot tests to that).
	 */
	public static final String ENV = "burmaldaholic:casino_worldgen";

	public static final List<String> STYLES = List.of("tudor", "oasis", "safari", "alpine", "ice_palace");
	public static final Identifier SET = Identifier.parse("burmaldaholic:casinos");
	public static final Identifier TAG = Identifier.parse("burmaldaholic:casino");
	/** Air kept above every table for the in-world board (mirrors {@code Kits.BOARD_CLEARANCE}). */
	public static final int BOARD_CLEARANCE = 3;
	private static final Transaction QA = Transaction.of("casino_qa", "gametest");
	private static final long[] LADDER = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000};

	private CasinoQa() {}

	public static boolean requireV2() {
		return Boolean.getBoolean("burmaldaholic.requireCasinoV2");
	}

	public static void log(String fmt, Object... args) {
		Burmaldaholic.LOGGER.info("[casino-qa] " + fmt, args);
	}

	// ---- what exists ------------------------------------------------------------------------------------------------

	/**
	 * One kind of casino to test. {@code structure} = a v0.2 standalone casino (placed with {@code /place structure});
	 * {@code layout} = a single-template casino (annex / Parlor / Lounge / v0.1) placed through its pool element.
	 * {@code full}: must hold every game family (architecture §4); {@code v2}: the v0.2 quality bar applies.
	 */
	public record Target(String name, boolean full, boolean v2, Optional<ResourceKey<Structure>> structure, Optional<Layout> layout) {
		@Override
		public String toString() {
			return name;
		}
	}

	public static Optional<Holder.Reference<Structure>> structure(MinecraftServer server, String style) {
		return server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(ResourceKey.create(Registries.STRUCTURE, Identifier.parse("burmaldaholic:casino_" + style)));
	}

	/** The v0.2 standalone casino structures present in the registry (empty before CB-1/CB-2 merge). */
	public static List<Target> standalone(MinecraftServer server) {
		List<Target> out = new ArrayList<>();
		for (String s : STYLES) {
			structure(server, s).ifPresent(h -> out.add(new Target("casino_" + s, true, true, Optional.of(h.key()), Optional.empty())));
		}
		return out;
	}

	/** v0.2 single-template casinos (village annex per village style, Parlor, Lounge) known to {@link Layouts}. */
	public static List<Target> v2Singles() {
		List<Target> out = new ArrayList<>();
		for (VillageStyle v : VillageStyle.values()) {
			Layouts.byId("casino/annex/" + v.name().toLowerCase(java.util.Locale.ROOT))
				.ifPresent(l -> out.add(new Target(l.id(), true, true, Optional.empty(), Optional.of(l))));
		}
		for (String id : List.of("casino/parlor", "casino/lounge")) {
			Layouts.byId(id).ifPresent(l -> out.add(new Target(l.id(), true, true, Optional.empty(), Optional.of(l))));
		}
		return out;
	}

	/** The frozen v0.1 templates (always present: old saves reference them). */
	public static List<Target> legacy() {
		List<Target> out = new ArrayList<>();
		for (Layout l : Layouts.all().values()) {
			if (!l.id().startsWith("casino/")) {
				out.add(new Target(l.id(), false, false, Optional.empty(), Optional.of(l)));
			}
		}
		return out;
	}

	public static List<Target> all(MinecraftServer server) {
		List<Target> out = new ArrayList<>(standalone(server));
		out.addAll(v2Singles());
		out.addAll(legacy());
		return out;
	}

	public static boolean hasV2(MinecraftServer server) {
		return !standalone(server).isEmpty() || !v2Singles().isEmpty();
	}

	public static Optional<? extends net.minecraft.core.HolderSet.Named<Structure>> casinoTag(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(TagKey.create(Registries.STRUCTURE, TAG));
	}

	// ---- placing ----------------------------------------------------------------------------------------------------

	/** A game block found in the world: {@code facing} = the side players stand on. */
	public record Spot(BlockPos pos, Game game, Direction facing) {
		public BlockPos playerCell() {
			return pos.relative(facing);
		}

		@Override
		public String toString() {
			return game + "@" + pos.toShortString() + " facing " + facing.getSerializedName();
		}
	}

	public record Placed(Target target, BoundingBox box, List<Spot> spots, Optional<CasinoRecord> record, int rotation) {
		public Set<Game.Family> families() {
			Set<Game.Family> f = EnumSet.noneOf(Game.Family.class);
			spots.forEach(s -> f.add(s.game().family()));
			return f;
		}

		public Map<Game.Family, Spot> firstBaseSpotPerFamily() {
			Map<Game.Family, Spot> m = new EnumMap<>(Game.Family.class);
			for (Spot s : spots) {
				Game.Family fam = s.game().family();
				boolean base = s.game() == baseGame(fam);
				if (!m.containsKey(fam) || base && m.get(fam).game() != baseGame(fam)) {
					m.put(fam, s);
				}
			}
			return m;
		}
	}

	/** The plain (non-VIP, house-banked) block of a family: the one any player can bet at. */
	public static Game baseGame(Game.Family f) {
		return switch (f) {
			case CASHIER -> Game.CASHIER;
			case BLACKJACK -> Game.BLACKJACK;
			case ROULETTE -> Game.ROULETTE;
			case CRAPS -> Game.CRAPS;
			case BACCARAT -> Game.BACCARAT;
			case UTH -> Game.UTH;
			case POKER -> Game.POKER;
			case SLOTS -> Game.SLOTS_COPPER;
			case WHEEL -> Game.WHEEL;
			case PLINKO -> Game.PLINKO;
		};
	}

	/** Loads (generates) every chunk within {@code radius} chunks of {@code at}: {@code /place} needs them loaded. */
	public static void loadChunks(ServerLevel level, BlockPos at, int radius) {
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				level.getChunk(x, z);
			}
		}
	}

	/** Top block y of the generated terrain at (x, z) — ignores whatever an earlier run built there. */
	public static int groundY(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
			level.getChunkSource().randomState()) - 1;
	}

	/**
	 * The test world persists between runs: wipe what an earlier run built around {@code at} (blocks above the
	 * generated ground, casino NPCs, records) so every run starts on clean terrain.
	 */
	public static void reset(ServerLevel level, BlockPos at, int radius, int height) {
		int ground = groundY(level, at.getX(), at.getZ());
		BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		for (BlockPos p : BlockPos.betweenClosed(at.getX() - radius, ground + 1, at.getZ() - radius, at.getX() + radius, ground + height,
			at.getZ() + radius)) {
			if (!level.getBlockState(p).isAir()) {
				level.setBlock(p, air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
			}
		}
		AABB area = new AABB(at.getX() - radius, ground - 8, at.getZ() - radius, at.getX() + radius + 1, ground + height, at.getZ() + radius + 1);
		level.getEntitiesOfClass(CasinoNpcEntity.class, area).forEach(e -> e.discard());
		forgetNear(level, at, radius + 16);
	}

	/** Forgets casino records near {@code at} so a re-run of the test world starts clean. */
	public static void forgetNear(ServerLevel level, BlockPos at, int radius) {
		String dim = level.dimension().identifier().toString();
		CasinoIndex.get(level.getServer()).removeIf(r -> r.dimension().equals(dim) && r.box() != null
			&& Math.abs(r.box().centerX() - at.getX()) < radius && Math.abs(r.box().centerZ() - at.getZ()) < radius);
	}

	/**
	 * Places a target at {@code at} (x/z; y is the ground there) like worldgen does and reads it back. Structures go
	 * through {@code /place structure} (same {@code placeInChunk} path as worldgen, rotation from the position);
	 * layouts through their pool element with {@code turns} quarter turns, floor row on the ground.
	 */
	public static Placed place(ServerLevel level, Target t, BlockPos at, int turns) {
		loadChunks(level, at, t.structure().isPresent() ? 5 : 2);
		reset(level, at, t.structure().isPresent() ? 56 : 36, 40);
		BoundingBox box;
		if (t.structure().isPresent()) {
			int y = groundY(level, at.getX(), at.getZ());
			var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
			int ok;
			try {
				ok = level.getServer().getCommands().getDispatcher().execute(
					"place structure " + t.structure().get().identifier() + " " + at.getX() + " " + y + " " + at.getZ(), source);
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
				throw new AssertionError(t + ": /place structure failed at " + at.toShortString() + ": " + e.getMessage());
			}
			if (ok <= 0) {
				throw new AssertionError(t + ": /place structure placed nothing at " + at.toShortString());
			}
			Optional<CasinoRecord> rec = recordNear(level, at, 64);
			box = rec.map(r -> new BoundingBox(r.box().minX(), r.box().minY(), r.box().minZ(), r.box().maxX(), r.box().maxY(), r.box().maxZ()))
				.orElseGet(() -> scanBox(level, new BlockPos(at.getX(), y, at.getZ()), 52, 24));
			return new Placed(t, box, spots(level, box), rec, -1);
		}
		Layout l = t.layout().orElseThrow();
		int floorBlocks = t.v2() ? 2 : 0; // v0.2 pieces: foundation rows 0..2 sink in, floor row y = 3 (§4.1)
		BlockPos origin = new BlockPos(at.getX(), groundY(level, at.getX(), at.getZ()) - floorBlocks, at.getZ());
		box = CasinoBuilder.build(level, l, origin, CasinoStructures.rotation(turns))
			.orElseThrow(() -> new AssertionError(t + ": pool element missing or placement failed"));
		return new Placed(t, box, spots(level, box), recordNear(level, at, 64), turns);
	}

	/** The newest casino record whose box is around {@code at}. */
	public static Optional<CasinoRecord> recordNear(ServerLevel level, BlockPos at, int radius) {
		String dim = level.dimension().identifier().toString();
		CasinoRecord found = null;
		for (CasinoRecord r : CasinoIndex.get(level.getServer()).casinos()) {
			if (r.dimension().equals(dim) && r.box() != null && Math.abs(r.box().centerX() - at.getX()) < radius
				&& Math.abs(r.box().centerZ() - at.getZ()) < radius) {
				found = r;
			}
		}
		return Optional.ofNullable(found);
	}

	public static List<CasinoRecord> recordsIntersecting(ServerLevel level, BoundingBox box) {
		String dim = level.dimension().identifier().toString();
		List<CasinoRecord> out = new ArrayList<>();
		for (CasinoRecord r : CasinoIndex.get(level.getServer()).casinos()) {
			if (r.dimension().equals(dim) && r.box() != null && box.intersects(new BoundingBox(r.box().minX(), r.box().minY(),
				r.box().minZ(), r.box().maxX(), r.box().maxY(), r.box().maxZ()))) {
				out.add(r);
			}
		}
		return out;
	}

	/** Without a casino record (pre-D6 platform): the box around every game block near {@code at}. */
	private static BoundingBox scanBox(ServerLevel level, BlockPos at, int r, int h) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				for (int y = -6; y <= h; y++) {
					p.set(at.getX() + x, at.getY() + y, at.getZ() + z);
					if (gameOf(level.getBlockState(p)) != null) {
						found.add(p.immutable());
					}
				}
			}
		}
		return BoundingBox.encapsulatingPositions(found).map(b -> b.inflatedBy(6))
			.orElse(new BoundingBox(at.getX() - r, at.getY() - 6, at.getZ() - r, at.getX() + r, at.getY() + h, at.getZ() + r));
	}

	public static Game gameOf(BlockState state) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals(Burmaldaholic.MOD_ID) ? Game.byBlockId(id.toString()) : null;
	}

	/** Every game block inside {@code box}, read from the world. */
	public static List<Spot> spots(ServerLevel level, BoundingBox box) {
		List<Spot> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			Game g = gameOf(s);
			if (g != null) {
				Direction f = s.hasProperty(CasinoTableBlock.FACING) ? s.getValue(CasinoTableBlock.FACING) : Direction.UP;
				out.add(new Spot(p.immutable(), g, f));
			}
		}
		return out;
	}

	// ---- static checks on placed buildings ------------------------------------------------------------------------

	/** Highest feet-cell collision a player stands on (3 snow layers 0.25, a slab 0.5): walking steps up 0.6. */
	static final double MAX_FEET_SHAPE = 0.5;
	/** Above this the head reaches into the second cell up (1.8-block player). */
	static final double HEAD_IN_SECOND_CELL = 0.2;
	/** Walking climbs 0.6 without jumping; a jump reaches 1.25. */
	static final double STEP_UP = 0.6;
	static final double JUMP_UP = 1.25;

	/**
	 * A player fits with the feet in this cell: its collision top ≤ {@link #MAX_FEET_SHAPE} (carpet, snow layers, a
	 * bottom slab), the cell above free, and — for shapes higher than {@link #HEAD_IN_SECOND_CELL} — the next one too.
	 */
	static boolean walkable(ServerLevel level, BlockPos feet) {
		double h = feetHeight(level, feet);
		if (h > MAX_FEET_SHAPE || !free(level, feet.above())) {
			return false;
		}
		return h <= HEAD_IN_SECOND_CELL || free(level, feet.above(2));
	}

	private static boolean free(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	/** Collision top of the feet cell (0 = empty). */
	static double feetHeight(ServerLevel level, BlockPos p) {
		VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
		return s.isEmpty() ? 0 : s.max(Direction.Axis.Y);
	}

	/** Placement problems of one building (empty = fine): facings, support, player cells, families, clearance. */
	public static List<String> structuralProblems(ServerLevel level, Placed placed) {
		List<String> bad = new ArrayList<>();
		Target t = placed.target();
		if (placed.spots().isEmpty()) {
			bad.add("no game block at all (absent module ids load as air)");
		}
		for (Spot s : placed.spots()) {
			if (s.facing().getAxis() == Direction.Axis.Y) {
				bad.add(s + ": no horizontal facing");
				continue;
			}
			BlockPos below = s.pos().below();
			if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				bad.add(s + ": floats (nothing solid below)");
			}
			if (!walkable(level, s.playerCell())) {
				bad.add(s + ": player cell " + s.playerCell().toShortString() + " blocked by " + level.getBlockState(s.playerCell()));
			}
			if (t.v2() && s.game().isTable()) {
				for (BlockPos c : BlockPos.betweenClosed(s.pos().offset(-1, 1, -1), s.pos().offset(1, BOARD_CLEARANCE, 1))) {
					if (!level.getBlockState(c).getCollisionShape(level, c).isEmpty() && !level.getBlockState(c).isAir()) {
						bad.add(s + ": board clearance blocked at " + c.toShortString() + " by " + level.getBlockState(c));
						break;
					}
				}
			}
		}
		Set<BlockPos> reach = reachableFromOutside(level, placed.box());
		for (Spot s : placed.spots()) {
			if (s.facing().getAxis() != Direction.Axis.Y && walkable(level, s.playerCell()) && !reach.contains(s.playerCell())) {
				bad.add(s + ": player cell not reachable on foot from outside");
			}
		}
		if (t.full()) {
			Set<Game.Family> missing = EnumSet.copyOf(Game.Family.REQUIRED);
			missing.removeAll(placed.families());
			if (!missing.isEmpty()) {
				bad.add("missing game families " + missing + " (every game in every full casino)");
			}
		}
		return bad;
	}

	/** Light problems (v0.2 bar §3.4 rule 4): ≥ 10 above every game block, ≥ 1 on every enclosed floor cell. */
	public static List<String> lightProblems(ServerLevel level, Placed placed) {
		List<String> bad = new ArrayList<>();
		for (Spot s : placed.spots()) {
			int l = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, s.pos().above());
			if (l < 10) {
				bad.add(s + ": block light " + l + " < 10 above");
			}
		}
		int dark = 0;
		String first = null;
		BoundingBox b = placed.box();
		// rooms players walk into: enclosed AND reachable on foot from outside (sealed attics / roof voids are not floors)
		Set<BlockPos> reach = reachableFromOutside(level, b);
		for (BlockPos p : reach) {
			if (!b.isInside(p) || !enclosedFloorCell(level, p, b)) {
				continue;
			}
			if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) < 1) {
				dark++;
				first = first == null ? p.toShortString() : first;
			}
		}
		if (dark > 0) {
			bad.add(dark + " walkable interior cells at block light 0 (mob spawns), e.g. " + first);
		}
		return bad;
	}

	/** Air (or carpet) over a solid floor, roofed, with walls in all four directions inside the box. */
	static boolean enclosedFloorCell(ServerLevel level, BlockPos p, BoundingBox box) {
		if (!walkable(level, p) || level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
			return false;
		}
		if (!blockedWithin(level, p, Direction.UP, box)) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!blockedWithin(level, p, d, box)) {
				return false;
			}
		}
		return true;
	}

	private static boolean blockedWithin(ServerLevel level, BlockPos p, Direction d, BoundingBox box) {
		BlockPos.MutableBlockPos c = p.mutable();
		for (int i = 1; i < 48; i++) {
			c.move(d);
			if (!box.isInside(c)) {
				return false;
			}
			if (!level.getBlockState(c).getCollisionShape(level, c).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	// ---- terrain (real worldgen, §9.4) ---------------------------------------------------------------------------------

	/** Feet cell passable, head free, something solid to stand on. */
	public static boolean stand(ServerLevel level, BlockPos p) {
		BlockState below = level.getBlockState(p.below());
		return walkable(level, p) && (feetHeight(level, p) > 0 || !below.getCollisionShape(level, p.below()).isEmpty())
			&& gameOf(below) == null; // nobody walks across the tables
	}

	/** Natural terrain that must never stand inside a building (cut terrain) — stone, dirt, sand, leaves, logs, water. */
	public static boolean terrain(BlockState s) {
		return s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(net.minecraft.tags.BlockTags.DIRT)
			|| s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)
			|| s.is(net.minecraft.world.level.block.Blocks.GRAVEL) || !s.getFluidState().isEmpty() && !s.hasProperty(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
	}

	/** Natural ground (no vegetation): {@link #terrain} without logs and leaves. */
	public static boolean ground(BlockState s) {
		return terrain(s) && !s.is(net.minecraft.tags.BlockTags.LOGS) && !s.is(net.minecraft.tags.BlockTags.LEAVES);
	}

	/** Every walkable cell reachable on foot from outside the building (multi-source walk from the ring around it). */
	public static Set<BlockPos> reachableFromOutside(ServerLevel level, BoundingBox box) {
		BoundingBox area = box.inflatedBy(6, 2, 6);
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		Set<BlockPos> seen = new java.util.HashSet<>();
		for (int x = area.minX(); x <= area.maxX(); x++) {
			for (int z = area.minZ(); z <= area.maxZ(); z++) {
				if (box.inflatedBy(2, 0, 2).isInside(x, box.minY(), z)) {
					continue;
				}
				for (int y = area.minY(); y <= area.maxY(); y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (stand(level, p) && seen.add(p)) {
						queue.add(p);
					}
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos c = queue.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = c.relative(d).above(dy);
					if (!area.isInside(n) || seen.contains(n) || !stand(level, n)) {
						continue;
					}
					double rise = dy + feetHeight(level, n) - feetHeight(level, c);
					if (rise > JUMP_UP || rise > STEP_UP && !free(level, c.above(2))) {
						continue; // too high, or a jump under a low ceiling
					}
					seen.add(n);
					queue.add(n);
				}
			}
		}
		return seen;
	}

	/**
	 * A casino generated by worldgen in real terrain: (a) no floating foundation column, (b) no terrain inside the
	 * building (player cells, board clearance, enclosed rooms), (c) every game reachable on foot from outside.
	 */
	public static List<String> terrainProblems(ServerLevel level, Placed placed) {
		List<String> bad = new ArrayList<>();
		BoundingBox b = placed.box();
		int floating = 0;
		String firstFloat = null;
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				if (x != b.minX() && x != b.maxX() && z != b.minZ() && z != b.maxZ()) {
					continue;
				}
				BlockPos base = new BlockPos(x, b.minY(), z);
				// natural ground is not a foundation: the union box of an L-shaped assembly has empty corners, where a
				// cave under the hillside read as "floating" (tudor@seed 42: stone over a 3-high cave, outside every piece)
				if (level.getBlockState(base).getCollisionShape(level, base).isEmpty() || ground(level.getBlockState(base))) {
					continue;
				}
				boolean grounded = false;
				for (int d = 1; d <= 3 && !grounded; d++) {
					BlockPos q = base.below(d);
					grounded = !level.getBlockState(q).getCollisionShape(level, q).isEmpty();
				}
				if (!grounded) {
					floating++;
					firstFloat = firstFloat == null ? base.toShortString() + " " + level.getBlockState(base).getBlock() : firstFloat;
				}
			}
		}
		if (floating > 0) {
			bad.add(floating + " foundation columns float (> 3 blocks of air below), e.g. " + firstFloat);
		}
		for (Spot s : placed.spots()) {
			List<BlockPos> cells = new ArrayList<>(List.of(s.playerCell(), s.playerCell().above()));
			if (s.game().isTable()) {
				BlockPos.betweenClosed(s.pos().offset(-1, 1, -1), s.pos().offset(1, BOARD_CLEARANCE, 1)).forEach(c -> cells.add(c.immutable()));
			}
			for (BlockPos c : cells) {
				if (terrain(level.getBlockState(c))) {
					bad.add(s + ": terrain " + level.getBlockState(c).getBlock() + " inside at " + c.toShortString());
					break;
				}
			}
		}
		int cut = 0;
		String firstCut = null;
		for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
			if (!enclosedFloorCell(level, p, b)) {
				continue;
			}
			BlockPos.MutableBlockPos c = p.mutable();
			while (c.getY() < b.maxY() && level.getBlockState(c).getCollisionShape(level, c).isEmpty()) {
				c.move(Direction.UP);
			}
			// ground over a room: stone, dirt, sand, gravel, water — not logs / leaves, which the templates use as beams
			// and hedges (Tudor's dark-oak ceiling beams were counted as 107 "cut" cells)
			if (ground(level.getBlockState(c)) || ground(level.getBlockState(p))) {
				cut++;
				firstCut = firstCut == null ? c.toShortString() + " " + level.getBlockState(c).getBlock() : firstCut;
			}
		}
		if (cut > 0) {
			bad.add(cut + " interior cells with terrain over them (cut terrain), e.g. " + firstCut);
		}
		Set<BlockPos> reach = reachableFromOutside(level, b);
		for (Spot s : placed.spots()) {
			if (!reach.contains(s.playerCell())) {
				bad.add(s + ": not reachable on foot from outside the building");
			}
		}
		return bad;
	}

	// ---- the real player flow -----------------------------------------------------------------------------------------

	/** A mock player whose client has "loaded" (the server ignores use packets before that), funded for the tests. */
	@SuppressWarnings("removal")
	public static ServerPlayer player(net.minecraft.gametest.framework.GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		acceptTeleport(p);
		p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		moneyMoves(p);
		Economies.get().setBalance(p.level().getServer(), p.getUUID(), balance, QA);
		return p;
	}

	/**
	 * Joining teleports the player and the server ignores block use until the client confirms that teleport; a real
	 * client echoes the id of the position packet it received — the mock has no client, so the id is read back.
	 */
	public static void acceptTeleport(ServerPlayer p) {
		try {
			java.lang.reflect.Field f = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
			f.setAccessible(true);
			java.lang.reflect.Field pending = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
			pending.setAccessible(true);
			if (pending.get(p.connection) != null) {
				// 26.2: (id); 26.3: (id, x, y, z, yRot, xRot) — the client echoes where it was put
				Constructor<?> c = net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket.class.getConstructors()[0];
				Object[] args = c.getParameterCount() == 1 ? new Object[] {f.getInt(p.connection)}
					: new Object[] {f.getInt(p.connection), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()};
				p.connection.handleAcceptTeleportPacket((net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket) c.newInstance(args));
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot confirm the join teleport of the mock player", e);
		}
	}

	public static long balance(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	/** Stands on the spot's player cell looking at the block, the way a player walks up to a table. */
	public static void standAt(ServerPlayer p, Spot s) {
		BlockPos cell = s.playerCell();
		Vec3 feet = new Vec3(cell.getX() + 0.5, cell.getY() + (lowShapeTop(p.level(), cell)), cell.getZ() + 0.5);
		Vec3 target = Vec3.atCenterOf(s.pos());
		double dx = target.x - feet.x;
		double dz = target.z - feet.z;
		double dy = target.y - (feet.y + p.getEyeHeight());
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		p.snapTo(feet.x, feet.y, feet.z, yaw, pitch);
	}

	private static double lowShapeTop(ServerLevel level, BlockPos p) {
		VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
		return s.isEmpty() ? 0 : s.max(Direction.Axis.Y);
	}

	private static int sequence;

	/** Right-click on the block face the player looks at, through the real packet handler (reach, spawn protection…). */
	public static void use(ServerPlayer p, BlockPos pos, Direction face) {
		Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		p.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false), ++sequence));
	}

	/** Walks up to the spot and right-clicks it; true when that table's menu is open on the server. */
	public static boolean open(ServerPlayer p, Spot s) {
		standAt(p, s);
		use(p, s.pos(), s.facing());
		return p.containerMenu instanceof CasinoTableMenu m && m.pos().equals(s.pos());
	}

	/** Sends a C2S payload through the registered receiver, exactly as it arrives from a client. */
	public static void send(ServerPlayer p, CustomPacketPayload payload) {
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(payload));
	}

	public static void action(ServerPlayer p, BlockPos pos, String action, CompoundTag args) {
		send(p, new TableActionPayload(pos, action, args));
	}

	/** What a client sends for one bet of {@code amount} at a family's table (same actions as the game screens). */
	static List<Map.Entry<String, CompoundTag>> betActions(Game.Family family, long amount) {
		List<Map.Entry<String, CompoundTag>> out = new ArrayList<>();
		CompoundTag a = new CompoundTag();
		switch (family) {
			case BLACKJACK -> {
				out.add(Map.entry("sit", new CompoundTag()));
				a.putLong("amount", amount);
				out.add(Map.entry("bet", a));
			}
			case ROULETTE -> {
				a.putString("type", "straight");
				a.putIntArray("nums", new int[] {17});
				a.putLong("amount", amount);
				out.add(Map.entry("bet", a));
			}
			case CRAPS -> {
				out.add(Map.entry("sit", new CompoundTag()));
				a.putString("kind", "pass");
				a.putLong("amount", amount);
				out.add(Map.entry("bet", a));
			}
			case BACCARAT -> {
				out.add(Map.entry("sit", new CompoundTag()));
				a.putString("box", "player");
				a.putLong("amount", amount);
				out.add(Map.entry("bet", a));
			}
			case UTH -> {
				out.add(Map.entry("sit", new CompoundTag()));
				a.putLong("ante", amount);
				a.putLong("trips", 0);
				out.add(Map.entry("bet", a));
			}
			case POKER -> {
				a.putString("level", "micro");
				a.putLong("amount", amount * 10);
				out.add(Map.entry("buy_in", a));
			}
			case SLOTS -> {
				a.putLong("bet", amount);
				out.add(Map.entry("spin", a));
			}
			case WHEEL -> {
				a.putString("stake", "chips");
				a.putLong("amount", amount);
				out.add(Map.entry("spin", a));
			}
			case PLINKO -> {
				a.putString("risk", "low");
				a.putLong("amount", amount);
				out.add(Map.entry("drop", a));
			}
			case CASHIER -> {
				a.putInt("count", 1);
				out.add(Map.entry("buy", a));
			}
		}
		return out;
	}

	/**
	 * Places one bet at the spot through the payload receiver, trying the usual chip amounts from small to large
	 * (stakes/limits differ per table and preset). Returns {@code null} on success, else what went wrong.
	 */
	public static String bet(ServerPlayer p, Spot s) {
		ServerLevel level = p.level();
		if (!(level.getBlockEntity(s.pos()) instanceof CasinoTableBlockEntity table)) {
			return "no table block entity";
		}
		Game.Family fam = s.game().family();
		if (fam == Game.Family.CASHIER) {
			p.getInventory().add(new ItemStack(Items.EMERALD, 4));
		}
		drainMessages(p);
		long before = balance(p);
		int moves = moneyMoves(p);
		List<String> said = new ArrayList<>();
		for (long amount : LADDER) {
			for (Map.Entry<String, CompoundTag> e : betActions(fam, amount)) {
				action(p, s.pos(), e.getKey(), e.getValue());
			}
			// instant games (wheel, plinko, slots) may settle within the same call: watch the economy, not just the total
			if (moneyMoves(p) != moves || balance(p) != before || table.hasStake(p.getUUID())) {
				return null;
			}
			for (String m : drainMessages(p)) {
				if (!said.contains(m)) {
					said.add(m);
				}
			}
			if (fam == Game.Family.CASHIER) {
				break;
			}
		}
		return "no balance change / open stake after " + betActions(fam, LADDER[0]).stream().map(Map.Entry::getKey).toList()
			+ " for amounts " + java.util.Arrays.toString(LADDER) + " (balance " + before + "); server said " + said;
	}

	private static final Map<java.util.UUID, Integer> MOVES = new java.util.concurrent.ConcurrentHashMap<>();
	private static volatile boolean listening;

	/** How many balance changes the economy reported for this player (CasinoEvents.BALANCE_CHANGED). */
	public static int moneyMoves(ServerPlayer p) {
		if (!listening) {
			synchronized (MOVES) {
				if (!listening) {
					dev.nezo.burmaldaholic.core.events.CasinoEvents.BALANCE_CHANGED.register((pl, b, a, r) -> MOVES.merge(pl.getUUID(), 1, Integer::sum));
					listening = true;
				}
			}
		}
		return MOVES.getOrDefault(p.getUUID(), 0);
	}

	/** Gives the player the top VIP tier (some generated tables are VIP-gated by design, e.g. Parlor poker = Low stakes). */
	public static void makeVip(ServerPlayer p) {
		long[] t = dev.nezo.burmaldaholic.vip.VipService.thresholds();
		dev.nezo.burmaldaholic.vip.VipService.setWagered(p, t[t.length - 1] + 1);
	}

	/**
	 * What the server told this (mock) client since the last call: table errors, action-bar and chat lines, read from
	 * the player's in-memory connection (the packets a real client would display).
	 */
	public static List<String> drainMessages(ServerPlayer p) {
		List<String> out = new ArrayList<>();
		try {
			java.lang.reflect.Field cf = net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
			cf.setAccessible(true);
			Object conn = cf.get(p.connection);
			java.lang.reflect.Field ch = net.minecraft.network.Connection.class.getDeclaredField("channel");
			ch.setAccessible(true);
			if (!(ch.get(conn) instanceof io.netty.channel.embedded.EmbeddedChannel channel)) {
				return out;
			}
			Object msg;
			while ((msg = channel.readOutbound()) != null) {
				collect(msg, out);
			}
		} catch (ReflectiveOperationException e) {
			out.add("(cannot read the mock connection: " + e + ")");
		}
		return out;
	}

	private static void collect(Object msg, List<String> out) {
		if (msg instanceof net.minecraft.network.protocol.BundlePacket<?> bundle) {
			bundle.subPackets().forEach(sub -> collect(sub, out));
		} else if (msg instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
			&& packet.payload() instanceof dev.nezo.burmaldaholic.core.network.TableErrorPayload err) {
			out.add(text(err.message()));
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket bar) {
			out.add(text(bar.text()));
		} else if (msg instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
			out.add(text(chat.content()));
		}
	}

	private static String text(net.minecraft.network.chat.Component c) {
		return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : c.getString();
	}

	/** Right-clicks the nearest Croupier of the building and buys a scratch card from the shop page. */
	public static String croupierSellsScratchCard(ServerPlayer p, Placed placed) {
		ServerLevel level = p.level();
		AABB area = AABB.of(placed.box()).inflate(2);
		List<CasinoNpcEntity> npcs = level.getEntitiesOfClass(CasinoNpcEntity.class, area, n -> n.role() == NpcRole.CROUPIER);
		if (npcs.isEmpty()) {
			return "no Croupier entity in the building";
		}
		CasinoNpcEntity npc = npcs.getFirst();
		Vec3 at = npc.position().add(1.2, 0, 0);
		p.snapTo(at.x, at.y, at.z, 90f, 0f);
		p.connection.handleInteract(new ServerboundInteractPacket(npc.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
		long before = balance(p);
		int cards = count(p, "scratch_card");
		send(p, new MenuRequestPayload("worldgen_shop", "buy:scratch_card", 0));
		if (count(p, "scratch_card") <= cards || balance(p) >= before) {
			return "shop buy did not hand out a scratch card (balance " + before + " -> " + balance(p) + ")";
		}
		return null;
	}

	private static int count(ServerPlayer p, String path) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (BuiltInRegistries.ITEM.getKey(s.getItem()).equals(Burmaldaholic.id(path))) {
				n += s.getCount();
			}
		}
		return n;
	}

	// ---- version-tolerant structure generation (26.2 and 26.3 differ in signatures) ----------------------------------

	/**
	 * {@code Structure.generate(...)} by reflection: 26.3 added a {@code Climate.Sampler} parameter. Every parameter is
	 * filled by type from {@code pool}; {@code int} = 0 (references), {@code long} = seed.
	 */
	public static net.minecraft.world.level.levelgen.structure.StructureStart generate(Holder<Structure> holder, long seed, Object... pool) {
		List<Object> values = new ArrayList<>(List.of(pool));
		values.add(holder);
		for (Method m : Structure.class.getMethods()) {
			if (!m.getName().equals("generate") || m.getReturnType() != net.minecraft.world.level.levelgen.structure.StructureStart.class) {
				continue;
			}
			Object[] args = fill(m.getParameterTypes(), values, seed);
			if (args != null) {
				try {
					return (net.minecraft.world.level.levelgen.structure.StructureStart) m.invoke(holder.value(), args);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError("Structure.generate failed", e.getCause() != null ? e.getCause() : e);
				}
			}
		}
		throw new AssertionError("no Structure.generate overload matches " + values);
	}

	/** {@code new Structure.GenerationContext(...)} (shortest constructor) by reflection, same filling rules. */
	public static Structure.GenerationContext context(long seed, Object... pool) {
		Constructor<?> best = null;
		Object[] bestArgs = null;
		for (Constructor<?> c : Structure.GenerationContext.class.getConstructors()) {
			Object[] args = fill(c.getParameterTypes(), List.of(pool), seed);
			if (args != null && (best == null || c.getParameterCount() < best.getParameterCount())) {
				best = c;
				bestArgs = args;
			}
		}
		if (best == null) {
			throw new AssertionError("no GenerationContext constructor matches");
		}
		try {
			return (Structure.GenerationContext) best.newInstance(bestArgs);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("GenerationContext failed", e);
		}
	}

	private static Object[] fill(Class<?>[] types, List<Object> values, long seed) {
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++) {
			Class<?> t = types[i];
			if (t == int.class) {
				args[i] = 0;
			} else if (t == long.class) {
				args[i] = seed;
			} else {
				Object v = null;
				for (Object o : values) {
					if (o != null && t.isInstance(o)) {
						v = o;
						break;
					}
				}
				if (v == null) {
					return null;
				}
				args[i] = v;
			}
		}
		return args;
	}

	/** Biome-tag check at a block column, version-tolerant ({@code findBiomeHorizontal} radius 0). */
	public static boolean biomeMatches(net.minecraft.world.level.chunk.ChunkGenerator gen, net.minecraft.world.level.levelgen.RandomState rs,
			int x, int y, int z, Predicate<Holder<net.minecraft.world.level.biome.Biome>> test) {
		for (Method m : net.minecraft.world.level.biome.BiomeSource.class.getMethods()) {
			if (!m.getName().equals("findBiomeHorizontal") || m.getParameterCount() != 7) {
				continue;
			}
			Class<?> last = m.getParameterTypes()[6];
			Object ctx = last.isInstance(rs) ? rs : climateSampler(rs);
			try {
				return m.invoke(gen.getBiomeSource(), x, y, z, 0, test, net.minecraft.util.RandomSource.create(0), ctx) != null;
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("findBiomeHorizontal failed", e);
			}
		}
		throw new AssertionError("no findBiomeHorizontal(…7) overload");
	}

	/** The climate sampler of a random state: {@code sampler()} on 26.2, {@code createClimateSampler(EMPTY_UNCACHED)} on 26.3. */
	public static Object climateSampler(net.minecraft.world.level.levelgen.RandomState rs) {
		try {
			for (Method m : rs.getClass().getMethods()) {
				if (m.getName().equals("sampler") && m.getParameterCount() == 0) {
					return m.invoke(rs);
				}
			}
			for (Method m : rs.getClass().getMethods()) {
				if (m.getName().equals("createClimateSampler") && m.getParameterCount() == 1) {
					Class<?> ctx = m.getParameterTypes()[0];
					return m.invoke(rs, ctx.getField("EMPTY_UNCACHED").get(null));
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("climate sampler", e);
		}
		throw new AssertionError("no climate sampler accessor on RandomState");
	}

	/** {@code RandomState.create(settings, noises, seed)} — the argument order differs between 26.2 and 26.3. */
	public static net.minecraft.world.level.levelgen.RandomState randomState(net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings,
			net.minecraft.core.HolderGetter<?> noises, long seed) {
		for (Method m : net.minecraft.world.level.levelgen.RandomState.class.getMethods()) {
			if (m.getName().equals("create") && m.getParameterCount() == 3) {
				Object[] args = fill(m.getParameterTypes(), List.of(settings, noises), seed);
				if (args != null) {
					try {
						return (net.minecraft.world.level.levelgen.RandomState) m.invoke(null, args);
					} catch (ReflectiveOperationException e) {
						throw new AssertionError("RandomState.create", e);
					}
				}
			}
		}
		throw new AssertionError("no RandomState.create(3) overload");
	}

	/** Quantile of a sorted copy. */
	public static double quantile(List<Double> xs, double q) {
		if (xs.isEmpty()) {
			return 0;
		}
		List<Double> s = new ArrayList<>(xs);
		s.sort(Double::compare);
		return s.get(Math.min(s.size() - 1, (int) Math.floor(q * (s.size() - 1) + 0.5)));
	}

	/** Groups problems per target for a readable failure message. */
	public static String report(Map<String, List<String>> problems) {
		StringBuilder sb = new StringBuilder();
		problems.forEach((k, v) -> {
			if (!v.isEmpty()) {
				sb.append("\n  ").append(k).append(": ").append(String.join("; ", v.subList(0, Math.min(8, v.size()))));
				if (v.size() > 8) {
					sb.append(" … (+").append(v.size() - 8).append(")");
				}
			}
		});
		return sb.toString();
	}

	public static Map<String, List<String>> problems() {
		return new LinkedHashMap<>();
	}

	public static ChunkPos chunk(BlockPos p) {
		return new ChunkPos(p.getX() >> 4, p.getZ() >> 4);
	}
}
