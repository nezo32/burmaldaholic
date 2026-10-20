package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.client.dealer.DealerCueSource;
import dev.nezo.burmaldaholic.client.dealer.DealerGestureSink;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackDealer;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsDealerCues;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.present.PokerDealerCues;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteDealerCues;
import dev.nezo.burmaldaholic.gametest.poker.FastPokerBots;
import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoNpcs;
import dev.nezo.burmaldaholic.worldgen.CasinoStructures;
import dev.nezo.burmaldaholic.worldgen.HouseGames;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealer;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealers;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tester GameTests for package DL (house dealers): v0.1.3 casinos migrated exactly once, no dealer inside blocks, budget,
 * gesture routing, real chunk unload / reload, respawn races, click parity with the table, unfolding a compact house
 * table, player tables near an idle dealer, server-side gesture cues of roulette / craps / poker / baccarat rounds.
 */
public class HouseDealerTesterGameTests {
	private static final Transaction TEST = Transaction.of("worldgen", "gametest_dl_tester");

	// ---- helpers ------------------------------------------------------------------------------------------------

	private static CasinoRecord casinoAround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		String dim = level.dimension().identifier().toString();
		String id = CasinoRecord.idFor(dim, o.getX(), o.getY(), o.getZ()) + "|dlt";
		CasinoIndex index = CasinoIndex.get(level.getServer());
		index.setAnchor(id, dim, "test", CasinoKind.CASINO, new Geometry.Box(o.getX(), o.getY(), o.getZ(), o.getX() + 7, o.getY() + 5, o.getZ() + 7));
		return index.recordFor(id, dim);
	}

	private static void forget(GameTestHelper helper, CasinoRecord r) {
		CasinoIndex.get(helper.getLevel().getServer()).removeIf(x -> x.id().equals(r.id()));
	}

	private static void floor(GameTestHelper helper) {
		for (int x = -2; x < 10; x++) {
			for (int z = -2; z < 10; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
			}
		}
	}

	private static BlockPos fullTable(GameTestHelper helper, Object block, BlockPos rel) {
		floor(helper);
		BlockPos main = helper.absolutePos(rel);
		TableParts.placeFull(helper.getLevel(), main, (MultiBlockTableBlock) block, Direction.SOUTH);
		return main;
	}

	private static HouseDealer dealerFor(ServerLevel level, BlockPos main) {
		BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
		HouseDealer d = (HouseDealer) CasinoNpcs.spawn(level, NpcRole.HOUSE_DEALER, cell, TableParts.facingOf(level.getBlockState(main)).toYRot(), "dl-tester");
		d.rebind(level);
		return d;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance, BlockPos rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(rel);
		p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	@SuppressWarnings("removal")
	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static String typeId(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
	}

	private static boolean legacyDealer(Entity e) {
		String t = typeId(e);
		return t.equals("burmaldaholic:baccarat_dealer") || t.equals("burmaldaholic:piglin_dealer");
	}

	/** Blocks whose collision shape intersects the entity's box (a dealer standing inside a table / wall). */
	private static List<String> insideBlocks(ServerLevel level, Entity e) {
		List<String> out = new ArrayList<>();
		AABB bb = e.getBoundingBox().deflate(0.05);
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bb.minX, bb.minY, bb.minZ), BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ))) {
			BlockState s = level.getBlockState(p);
			VoxelShape shape = s.getCollisionShape(level, p);
			if (!shape.isEmpty() && shape.move(p.getX(), p.getY(), p.getZ()).toAabbs().stream().anyMatch(b -> b.intersects(bb))) {
				out.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + "@" + p.toShortString());
			}
		}
		return out;
	}

	private static BlockPos fresh(GameTestHelper helper, int y) {
		var r = helper.getLevel().getRandom();
		return new BlockPos(-30000 - r.nextInt(400) * 96, y, 30000 + r.nextInt(400) * 96);
	}

	private static List<long[]> force(ServerLevel level, BlockPos origin, int span) {
		int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
		List<long[]> forced = new ArrayList<>();
		for (int x = cx - 1; x <= cx + span; x++) {
			for (int z = cz - 1; z <= cz + span; z++) {
				if (!level.getForceLoadedChunks().contains(ChunkPos.pack(x, z))) {
					level.setChunkForced(x, z, true);
					forced.add(new long[] {x, z});
				}
			}
		}
		return forced;
	}

	private static void unforce(ServerLevel level, List<long[]> forced) {
		for (long[] c : forced) {
			level.setChunkForced((int) c[0], (int) c[1], false);
		}
	}

	private static ServerLevel dimension(GameTestHelper helper, net.minecraft.resources.ResourceKey<Level> key) {
		ServerLevel l = helper.getLevel().getServer().getLevel(key);
		return l != null ? l : helper.getLevel();
	}

	// ---- 1. v0.1.3 casinos (frozen templates) and v0.2 casinos: migration, inside-blocks, budget, routing --------

	/**
	 * Builds the casino from its real template, lets tables do their first tick (legacy SINGLE tables unfold) and dealers
	 * snap home, then runs the migration twice (+ once more after resetting the version: "migration run twice") and
	 * checks: exactly one house dealer per house table with a standable cell, none inside blocks, no legacy dealer left at
	 * a table, ≤ 12 per casino, the same dealers after every pass, and a blackjack / baccarat table's gesture lookup (the
	 * nearest DealerGestureSink within 3 blocks) reaches its OWN dealer.
	 */
	private static void migrateAndCheck(GameTestHelper helper, ServerLevel level, String layoutId, BlockPos origin) {
		List<long[]> forced = force(level, origin, 3);
		BoundingBox box = CasinoBuilder.build(level, Layouts.byId(layoutId).orElseThrow(), origin, CasinoStructures.rotation(0))
			.orElseThrow(() -> new AssertionError(layoutId + ": placement failed"));
		String dim = level.dimension().identifier().toString();
		int[] tick = {0};
		CasinoRecord[] rec = {null};
		Set<UUID>[] first = new Set[] {null};
		List<String> report = new ArrayList<>();
		String[] result = {null};
		helper.succeedWhen(() -> {
			if (result[0] != null) {
				helper.assertTrue(result[0].isEmpty(), result[0]);
				return;
			}
			tick[0]++;
			if (rec[0] == null) {
				rec[0] = CasinoIndex.get(level.getServer()).casinos().stream()
					.filter(r -> r.dimension().equals(dim) && r.box() != null && r.box().contains(box.minX() + 2, box.minY() + 2, box.minZ() + 2, 0))
					.reduce((a, b) -> b).orElse(null);
			}
			helper.assertTrue(rec[0] != null, layoutId + ": waiting for the casino record");
			CasinoRecord record = rec[0];
			helper.assertTrue(HouseDealers.houseTables(level, record) != null, layoutId + ": waiting for chunks");
			helper.assertTrue(tick[0] > 40, "waiting for first ticks (unfold)");
			if (first[0] == null) {
				record.dealersVersion = 0; // a v0.1.3 save: not migrated yet
				helper.assertTrue(HouseDealers.ensure(level, record), layoutId + ": pass 1 complete");
				helper.assertTrue(HouseDealers.ensure(level, record), layoutId + ": pass 2 complete");
				first[0] = new HashSet<>();
				for (HouseDealer d : level.getEntitiesOfClass(HouseDealer.class, AABB.of(box).inflate(2), Entity::isAlive)) {
					first[0].add(d.getUUID());
				}
			}
			helper.assertTrue(tick[0] > 120, "waiting for dealers to settle (home snap every 20 t)");
			List<String> issues = new ArrayList<>();
			List<BlockPos> tables = HouseDealers.houseTables(level, record);
			List<HouseDealer> all = level.getEntitiesOfClass(HouseDealer.class, AABB.of(box).inflate(2), Entity::isAlive);
			int standableCells = 0;
			for (BlockPos main : tables) {
				BlockState st = level.getBlockState(main);
				BlockPos cell = HouseDealers.dealerCell(main, st);
				boolean standable = level.getBlockState(cell).isAir() && level.getBlockState(cell.above()).isAir()
					&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP);
				standableCells += standable ? 1 : 0;
				List<HouseDealer> here = level.getEntitiesOfClass(HouseDealer.class, new AABB(cell), Entity::isAlive);
				String desc = TableParts.gameOf(st) + "/" + TableParts.partOf(st) + "@" + main.toShortString() + " cell " + cell.toShortString()
					+ " (" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(cell).getBlock()).getPath() + ")";
				report.add(desc + ": " + here.size() + " dealer(s)");
				if (here.size() != 1) {
					issues.add(desc + ": " + here.size() + " dealers on the cell, standable=" + standable);
				}
				for (Entity e : level.getEntities((Entity) null, new AABB(cell).inflate(1.5), HouseDealerTesterGameTests::legacyDealer)) {
					issues.add(desc + ": legacy " + typeId(e) + " left at " + e.blockPosition().toShortString());
				}
				TableGame g = TableParts.gameOf(st);
				if (g == TableGame.BLACKJACK || g == TableGame.BACCARAT) {
					Vec3 c = Vec3.atCenterOf(main);
					Mob best = null;
					double bestD = Double.MAX_VALUE;
					for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(main).inflate(BlackjackDealer.TABLE_RADIUS), m -> m instanceof DealerGestureSink)) {
						if (m.distanceToSqr(c) < bestD) {
							bestD = m.distanceToSqr(c);
							best = m;
						}
					}
					if (here.size() == 1 && best != here.getFirst()) {
						issues.add(desc + ": gestures go to " + (best == null ? "nobody" : typeId(best) + "@" + best.blockPosition().toShortString())
							+ " instead of its dealer");
					}
				}
			}
			for (HouseDealer d : all) {
				List<String> in = insideBlocks(level, d);
				if (!in.isEmpty()) {
					issues.add("dealer " + d.blockPosition().toShortString() + " (home " + d.home() + ", table " + d.table() + ") inside " + in);
				}
				if (!first[0].contains(d.getUUID())) {
					issues.add("dealer " + d.blockPosition().toShortString() + " appeared after the migration");
				}
			}
			if (all.size() > HouseDealers.MAX_PER_CASINO) {
				issues.add(all.size() + " house dealers > " + HouseDealers.MAX_PER_CASINO);
			}
			if (all.size() < Math.min(standableCells, HouseDealers.MAX_PER_CASINO)) {
				issues.add("only " + all.size() + " dealers for " + standableCells + " standable dealer cells");
			}
			// "migration run twice": a reset version re-runs the pass, nothing changes
			record.dealersVersion = 0;
			HouseDealers.ensure(level, record);
			Set<UUID> after = new HashSet<>();
			level.getEntitiesOfClass(HouseDealer.class, AABB.of(box).inflate(2), Entity::isAlive).forEach(d -> after.add(d.getUUID()));
			if (!after.equals(first[0])) {
				issues.add("re-running the migration changed the dealers: " + first[0].size() + " -> " + after.size());
			}
			long slots = record.npcs().stream().filter(s -> s.role() == NpcRole.HOUSE_DEALER).count();
			if (slots != all.size()) {
				issues.add(slots + " HOUSE_DEALER slots for " + all.size() + " dealers");
			}
			long legacySlots = record.npcs().stream().filter(s -> s.role() == NpcRole.BACCARAT_DEALER).count();
			int npcs = level.getEntities((Entity) null, AABB.of(box).inflate(2), e -> e.entityTags().contains(CasinoNpcs.NPC_TAG)).size();
			report.add("tables " + tables.size() + ", standable cells " + standableCells + ", dealers " + all.size() + ", npcs " + npcs
				+ ", BACCARAT_DEALER slots " + legacySlots);
			// clean up before asserting (the test world persists)
			for (Entity e : level.getEntities((Entity) null, AABB.of(box).inflate(2), e -> e.entityTags().contains(CasinoNpcs.NPC_TAG) || e instanceof HouseDealer)) {
				e.discard();
			}
			CasinoIndex.get(level.getServer()).removeIf(r -> r.id().equals(record.id()));
			unforce(level, forced);
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.info("[DL-T] {}: {}", layoutId, String.join(" | ", report));
			result[0] = issues.isEmpty() ? "" : layoutId + ": " + String.join("; ", issues);
			helper.assertTrue(result[0].isEmpty(), result[0]);
		});
	}

	@GameTest(maxTicks = 400)
	public void legacyVillageCasinoMigratesOnce(GameTestHelper helper) {
		migrateAndCheck(helper, helper.getLevel(), "village_casino_plains", fresh(helper, 100));
	}

	@GameTest(maxTicks = 400)
	public void legacyPiglinParlorMigratesOnce(GameTestHelper helper) {
		migrateAndCheck(helper, dimension(helper, Level.NETHER), "piglin_parlor", fresh(helper, 40));
	}

	@GameTest(maxTicks = 400)
	public void legacyHighRollerLoungeMigratesOnce(GameTestHelper helper) {
		migrateAndCheck(helper, dimension(helper, Level.END), "high_roller_lounge", fresh(helper, 80));
	}

	@GameTest(maxTicks = 400)
	public void v2AnnexDealersSettleOutsideBlocks(GameTestHelper helper) {
		migrateAndCheck(helper, helper.getLevel(), "casino/annex/plains", fresh(helper, 100));
	}

	// ---- 2. a compact house table unfolded by a player (sneak-click) -----------------------------------------------

	@GameTest(maxTicks = 120)
	public void unfoldingACompactHouseTableKeepsTheDealerOutOfIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos main = helper.absolutePos(new BlockPos(3, 1, 4));
		level.setBlock(main, BlackjackModule.TABLE.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.SOUTH), 3);
		CasinoRecord casino = casinoAround(helper);
		helper.assertTrue(TableParts.partOf(level.getBlockState(main)) == TablePart.SINGLE, "compact table");
		helper.assertTrue(HouseDealers.ensure(level, casino), "pass");
		BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
		List<HouseDealer> ds = level.getEntitiesOfClass(HouseDealer.class, new AABB(cell), Entity::isAlive);
		helper.assertTrue(ds.size() == 1, "a dealer at the compact table's f-1 cell");
		HouseDealer d = ds.getFirst();
		var outcome = TableParts.tryUnfold(level, main);
		helper.runAfterDelay(60, () -> {
			try {
				helper.assertTrue(outcome == dev.nezo.burmaldaholic.core.table.parts.MigrationDecision.Outcome.GROW, "unfolded: " + outcome);
				List<String> in = insideBlocks(level, d);
				helper.assertTrue(in.isEmpty(), "after unfolding, the house dealer (home " + d.home() + ") stands inside the table: " + in
					+ " at " + d.blockPosition().toShortString());
				helper.assertTrue(main.equals(d.table()), "still bound");
			} finally {
				d.discard();
				forget(helper, casino);
			}
			helper.succeed();
		});
	}

	// ---- 3. real chunk unload / reload ---------------------------------------------------------------------------

	@GameTest(maxTicks = 900)
	public void realChunkUnloadReloadKeepsOneDealer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = fresh(helper, 120);
		ChunkPos chunk = ChunkPos.containing(at);
		long key = ChunkPos.pack(chunk.x(), chunk.z());
		List<long[]> forced = force(level, at, 0);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				level.setBlock(at.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
			}
		}
		BlockPos main = at.offset(0, 0, 1);
		TableParts.placeFull(level, main, (MultiBlockTableBlock) BlackjackModule.TABLE.block(), Direction.SOUTH);
		HouseDealer d = dealerFor(level, main);
		UUID id = d.getUUID();
		int look = d.look();
		BlockPos home = d.home();
		BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
		int[] phase = {0};
		int[] t = {0};
		helper.succeedWhen(() -> {
			t[0]++;
			if (phase[0] == 0) {
				helper.assertTrue(t[0] > 10, "settle");
				unforce(level, forced);
				phase[0] = 1;
			}
			if (phase[0] == 1) {
				helper.assertTrue(level.getEntity(id) == null && !level.areEntitiesLoaded(key), "waiting for the entity chunk to unload");
				force(level, at, 0);
				phase[0] = 2;
			}
			List<HouseDealer> now = level.getEntitiesOfClass(HouseDealer.class, new AABB(cell).inflate(3), Entity::isAlive);
			helper.assertTrue(level.areEntitiesLoaded(key) && !now.isEmpty(), "waiting for the reload");
			helper.assertTrue(now.size() == 1, "one dealer after a real unload/reload, got " + now.size());
			HouseDealer r = now.getFirst();
			helper.assertTrue(r.getUUID().equals(id) && r.look() == look && home.equals(r.home()), "same entity, look and home");
			helper.assertTrue(main.equals(r.table()), "bound after reload: " + r.table());
			r.discard();
			level.setChunkForced(chunk.x(), chunk.z(), false);
		});
	}

	// ---- 4. respawn races ------------------------------------------------------------------------------------------

	@GameTest
	public void respawnTwiceAndMigrationAfterKillMakeOneDealer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = fullTable(helper, BlackjackModule.TABLE.block(), new BlockPos(3, 1, 4));
		CasinoRecord casino = casinoAround(helper);
		try {
			HouseDealers.ensure(level, casino);
			BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
			HouseDealer d = level.getEntitiesOfClass(HouseDealer.class, new AABB(cell), Entity::isAlive).getFirst();
			int look = d.look();
			d.kill(level);
			CasinoRecord.NpcSlot slot = casino.npcs().stream().filter(s -> s.role() == NpcRole.HOUSE_DEALER).findFirst().orElseThrow();
			Entity a = CasinoNpcs.respawn(level, slot, casino.id());
			Entity b = CasinoNpcs.respawn(level, slot, casino.id()); // a second tracker pass / second player loading it
			HouseDealers.ensure(level, casino);
			List<HouseDealer> now = level.getEntitiesOfClass(HouseDealer.class, new AABB(cell).inflate(2), Entity::isAlive);
			helper.assertTrue(a == b && now.size() == 1, "one dealer after kill + 2 respawns + migration, got " + now.size());
			helper.assertTrue(now.getFirst().look() == look, "same look");
			now.forEach(Entity::discard);
		} finally {
			forget(helper, casino);
		}
		helper.succeed();
	}

	// ---- 5. click parity with the table -----------------------------------------------------------------------------

	private static final Map<BlockPos, AtomicInteger> USE_BLOCK_SEEN = new HashMap<>();
	private static boolean listening;

	private static synchronized void listen() {
		if (!listening) {
			listening = true;
			UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
				synchronized (USE_BLOCK_SEEN) {
					AtomicInteger n = USE_BLOCK_SEEN.get(hit.getBlockPos());
					if (n != null) {
						n.incrementAndGet();
					}
				}
				return InteractionResult.PASS;
			});
		}
	}

	@GameTest
	public void dealerClickHasTheTableClicksChecks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = fullTable(helper, BlackjackModule.TABLE.block(), new BlockPos(3, 1, 4));
		HouseDealer d = dealerFor(level, main);
		ServerPlayer p = player(helper, 100, new BlockPos(3, 1, 6));
		listen();
		AtomicInteger seen = new AtomicInteger();
		synchronized (USE_BLOCK_SEEN) {
			USE_BLOCK_SEEN.put(main.immutable(), seen);
		}
		try {
			// the real entity click (Player.interactOn → mobInteract) opens the table
			p.interactOn(d, InteractionHand.MAIN_HAND, d.position().add(0, 1, 0));
			helper.assertTrue(p.containerMenu instanceof CasinoTableMenu m && m.pos().equals(main), "entity click opened the table");
			p.closeContainer();
			// casino mode off: neither path opens
			CasinoMode.set(level.getServer(), false);
			try {
				p.interactOn(d, InteractionHand.MAIN_HAND, d.position().add(0, 1, 0));
				helper.assertTrue(!(p.containerMenu instanceof CasinoTableMenu), "casino off: dealer click refused");
			} finally {
				CasinoMode.set(level.getServer(), true);
			}
			// the table click fires UseBlockCallback (Ownership.onUseBlock refusals, BotInteractions); the dealer click must too
			BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(main).add(0, 1, 0), Direction.UP, main, false);
			seen.set(0); // the dealer clicks above already went through the callback (DL D3 fix)
			p.gameMode.useItemOn(p, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			int viaTable = seen.get();
			p.closeContainer();
			p.interactOn(d, InteractionHand.MAIN_HAND, d.position().add(0, 1, 0));
			int viaDealer = seen.get() - viaTable;
			p.closeContainer();
			helper.assertTrue(viaTable == 1, "table click fires UseBlockCallback: " + viaTable);
			helper.assertTrue(viaDealer == 1, "dealer click skips UseBlockCallback (owned closed / broke / inactive table refusals"
				+ " of Ownership.onUseBlock and BotInteractions are bypassed): fired " + viaDealer + "x");
		} finally {
			synchronized (USE_BLOCK_SEEN) {
				USE_BLOCK_SEEN.remove(main);
			}
			remove(helper, p);
			d.discard();
		}
		helper.succeed();
	}

	// ---- 6. player-placed table next to an idle house dealer -----------------------------------------------------

	@GameTest
	public void idleHouseDealerDoesNotAdoptAPlayersTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = fullTable(helper, BlackjackModule.TABLE.block(), new BlockPos(3, 1, 4));
		HouseDealer d = dealerFor(level, main);
		try {
			for (BlockPos c : TableParts.cellPositions(main, level.getBlockState(main))) {
				level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			}
			d.rebind(level);
			helper.assertTrue(d.table() == null, "idle");
			BlockPos own = d.home().relative(Direction.EAST, 2);
			level.setBlock(own, CrapsModule.TABLE.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH), 3);
			HouseGames.markPlayerPlaced(level.getBlockEntity(own));
			d.rebind(level);
			helper.assertTrue(d.table() == null, "a house dealer adopted a player-placed table at " + own.toShortString() + " (bound " + d.table() + ")");
		} finally {
			d.discard();
		}
		helper.succeed();
	}

	// ---- 7. gestures during rounds (server side: the public sync the client maps to cues) ------------------------

	private static void sample(Set<DealerGesture> seen, DealerCueSource.Cue cue) {
		if (cue != null) {
			seen.add(cue.gesture());
		}
	}

	@GameTest(maxTicks = 600)
	public void rouletteRoundCuesWaveSpinSweepPay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = fullTable(helper, RouletteModule.TABLE.block(), new BlockPos(3, 1, 4));
		HouseDealer d = dealerFor(level, main);
		helper.assertTrue(main.equals(d.table()), "roulette dealer bound: " + d.table());
		RouletteTableBlockEntity t = (RouletteTableBlockEntity) level.getBlockEntity(main);
		ServerPlayer p = player(helper, 1000, new BlockPos(3, 1, 6));
		CompoundTag bet = new CompoundTag();
		bet.putString("type", BetType.RED.id());
		bet.putIntArray("nums", dev.nezo.burmaldaholic.games.roulette.logic.Spot.all(BetType.RED).getFirst().numbers().stream().mapToInt(Integer::intValue).toArray());
		bet.putLong("amount", 10);
		t.onAction(p, "bet", bet);
		t.onAction(p, "spin", new CompoundTag());
		Set<DealerGesture> seen = EnumSet.noneOf(DealerGesture.class);
		helper.onEachTick(() -> sample(seen, RouletteDealerCues.cue(t.sync(), level.getGameTime(), 0f)));
		helper.succeedWhen(() -> {
			helper.assertTrue(seen.containsAll(EnumSet.of(DealerGesture.WAVE_OFF, DealerGesture.SPIN, DealerGesture.SWEEP, DealerGesture.PAY)),
				"roulette gestures so far " + seen + " (phase " + t.phase() + ")");
			remove(helper, p);
			d.discard();
		});
	}

	@GameTest(maxTicks = 300)
	public void crapsRollCuesStickPullSettlePush(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = fullTable(helper, CrapsModule.TABLE.block(), new BlockPos(3, 1, 4));
		HouseDealer d = dealerFor(level, main);
		helper.assertTrue(main.equals(d.table()), "craps dealer bound: " + d.table());
		CrapsTableBlockEntity t = (CrapsTableBlockEntity) level.getBlockEntity(main);
		ServerPlayer p = player(helper, 1000, new BlockPos(3, 1, 6));
		CompoundTag bet = new CompoundTag();
		bet.putString("kind", dev.nezo.burmaldaholic.games.craps.logic.BetKind.PASS.id());
		bet.putLong("amount", 10);
		t.onAction(p, "bet", bet);
		t.roll(5, 6);
		Set<DealerGesture> seen = EnumSet.noneOf(DealerGesture.class);
		helper.onEachTick(() -> sample(seen, CrapsDealerCues.cue(t.sync(), level.getGameTime(), 0f)));
		helper.succeedWhen(() -> {
			helper.assertTrue(seen.containsAll(EnumSet.of(DealerGesture.STICK_PULL, DealerGesture.PAY, DealerGesture.STICK_PUSH)),
				"craps gestures so far " + seen);
			remove(helper, p);
			d.discard();
		});
	}

	@GameTest(maxTicks = 2400)
	public void pokerHandCuesDealAndPotPush(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = fullTable(helper, PokerModule.TABLE.block(), new BlockPos(3, 1, 4));
		HouseDealer d = dealerFor(level, main);
		helper.assertTrue(main.equals(d.table()), "poker dealer bound: " + d.table());
		PokerTableBlockEntity t = (PokerTableBlockEntity) level.getBlockEntity(main);
		ServerPlayer p = player(helper, 100000, new BlockPos(3, 1, 6));
		FastPokerBots.Handle fast = FastPokerBots.acquire();
		long bb = CasinoConfig.poker().stakes.micro.bb;
		CompoundTag buy = new CompoundTag();
		buy.putString("level", "micro");
		buy.putLong("amount", (long) CasinoConfig.poker().maxBuyInBb * bb);
		t.onAction(p, "buy_in", buy);
		Set<DealerGesture> seen = EnumSet.noneOf(DealerGesture.class);
		helper.onEachTick(() -> {
			sample(seen, PokerDealerCues.cue(t.buildPub(), level.getGameTime(), 0f));
			CompoundTag legal = t.writeClientState(p).getCompoundOrEmpty("legal");
			if (!legal.isEmpty()) {
				CompoundTag act = new CompoundTag();
				act.putString("kind", legal.getBooleanOr("can_check", false) ? "check" : "call");
				act.putInt("seq", legal.getIntOr("seq", 0));
				t.onAction(p, "act", act);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(seen.containsAll(EnumSet.of(DealerGesture.DEAL, DealerGesture.POT_PUSH, DealerGesture.SHUFFLE)),
				"poker gestures so far " + seen);
			t.onAction(p, "stand_up", new CompoundTag());
			fast.release();
			remove(helper, p);
			d.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void baccaratCoupMakesTheHouseDealerGesture(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = fullTable(helper, BaccaratModule.TABLE.block(), new BlockPos(3, 1, 4));
		HouseDealer d = dealerFor(level, main);
		helper.assertTrue(main.equals(d.table()), "baccarat dealer bound: " + d.table());
		BaccaratTableBlockEntity t = (BaccaratTableBlockEntity) level.getBlockEntity(main);
		ServerPlayer p = player(helper, 1000, new BlockPos(3, 1, 6));
		try {
			helper.assertTrue(d.dealerGesture() == DealerGesture.NONE, "idle before the coup");
			CompoundTag bet = new CompoundTag();
			bet.putString("box", dev.nezo.burmaldaholic.games.baccarat.logic.BetKind.PLAYER.id());
			bet.putLong("amount", 10);
			t.onAction(p, "bet", bet);
			t.onAction(p, "ready", new CompoundTag());
			Set<DealerGesture> seen = EnumSet.noneOf(DealerGesture.class);
			for (int i = 0; i < 12 && !"result".equals(t.phase()); i++) {
				t.fastForwardForTests();
				seen.add(d.dealerGesture());
			}
			seen.remove(DealerGesture.NONE);
			helper.assertTrue(!seen.isEmpty(), "the baccarat house dealer gestured during the coup (phase " + t.phase() + ")");
		} finally {
			remove(helper, p);
			d.discard();
		}
		helper.succeed();
	}
}
