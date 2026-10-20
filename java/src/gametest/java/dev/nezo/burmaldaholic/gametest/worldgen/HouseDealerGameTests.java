package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoNpcs;
import dev.nezo.burmaldaholic.worldgen.CasinoStructures;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.NpcLook;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.npc.CasinoNpcEntity;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealer;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealers;
import dev.nezo.burmaldaholic.worldgen.npc.NpcContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DL house dealers in a real world (DL-SPEC §5): one dealer per house table in every casino kind with the dimension's
 * species, bound to its table; none at player-owned tables; respawn with the same look; idempotent migration that
 * replaces legacy dealers; indestructible and immovable; a click uses the table; idle without one; blackjack gestures.
 */
public class HouseDealerGameTests {
	private static final Transaction TEST = Transaction.of("worldgen", "gametest");

	// ---- helpers ------------------------------------------------------------------------------------------------

	/** A fake generated casino over the 8 × 8 test area (removed again by {@link #forget}). */
	private static CasinoRecord casinoAround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		String dim = level.dimension().identifier().toString();
		String id = CasinoRecord.idFor(dim, o.getX(), o.getY(), o.getZ()) + "|dl";
		CasinoIndex index = CasinoIndex.get(level.getServer());
		index.setAnchor(id, dim, "test", CasinoKind.CASINO, new Geometry.Box(o.getX(), o.getY(), o.getZ(), o.getX() + 7, o.getY() + 5, o.getZ() + 7));
		return index.recordFor(id, dim);
	}

	private static void forget(GameTestHelper helper, CasinoRecord r) {
		CasinoIndex.get(helper.getLevel().getServer()).removeIf(x -> x.id().equals(r.id()));
	}

	/** A full table of {@code block} with its main block at test-relative {@code rel}, players to the south. */
	private static BlockPos table(GameTestHelper helper, Object block, BlockPos rel) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE); // a casino floor under the dealer cell
			}
		}
		BlockPos main = helper.absolutePos(rel);
		TableParts.placeFull(helper.getLevel(), main, (MultiBlockTableBlock) block, Direction.SOUTH);
		return main;
	}

	private static List<HouseDealer> dealersAt(ServerLevel level, BlockPos cell) {
		return level.getEntitiesOfClass(HouseDealer.class, new AABB(cell), Entity::isAlive);
	}

	private static List<HouseDealer> dealersIn(ServerLevel level, BoundingBox box) {
		return level.getEntitiesOfClass(HouseDealer.class, AABB.of(box).inflate(1), Entity::isAlive);
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 6));
		p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	private static String typeId(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
	}

	// ---- every casino kind ----------------------------------------------------------------------------------------

	/**
	 * Builds a casino like worldgen does in {@code level} and checks it: every house table has exactly one dealer on its
	 * cell (none anywhere else), bound to its main block, of the dimension's species, with head room for endermen; no
	 * table keeps an old dealer marker NPC; exactly one merchant; running the migration twice changes nothing.
	 */
	private static void checkCasino(GameTestHelper helper, ServerLevel level, Layout layout, BlockPos origin) {
		List<long[]> forced = force(level, origin, 3);
		BoundingBox box = CasinoBuilder.build(level, layout, origin, CasinoStructures.rotation(0))
			.orElseThrow(() -> new AssertionError(layout.id() + ": placement failed"));
		String dim = level.dimension().identifier().toString();
		CasinoRecord record = CasinoIndex.get(level.getServer()).casinos().stream()
			.filter(r -> r.dimension().equals(dim) && r.box() != null && r.box().contains(origin.getX() + 3, origin.getY() + 3, origin.getZ() + 3, 0))
			.reduce((a, b) -> b).orElseThrow(() -> new AssertionError(layout.id() + ": no casino record"));
		verify(helper, level, layout.id(), record, box, forced);
	}

	private static List<long[]> force(ServerLevel level, BlockPos origin, int span) {
		int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
		List<long[]> forced = new ArrayList<>();
		for (int x = cx - 1; x <= cx + span; x++) {
			for (int z = cz - 1; z <= cz + span; z++) {
				// only chunks nobody forces yet: the GameTest framework forces the test areas, and un-forcing one of
				// those later would freeze every test standing in it
				if (!level.getForceLoadedChunks().contains(net.minecraft.world.level.ChunkPos.pack(x, z))) {
					level.setChunkForced(x, z, true);
					forced.add(new long[] {x, z});
				}
			}
		}
		return forced;
	}

	/**
	 * Checks a built casino once its entities are loaded (polled): every house table has exactly one dealer on its cell
	 * (none anywhere else), bound to its main block, of the dimension's species, with head room for endermen; no old
	 * dealer NPC (baccarat / piglin dealer marker) is left at any table; exactly one merchant; running the migration
	 * twice more changes nothing.
	 */
	private static void verify(GameTestHelper helper, ServerLevel level, String name, CasinoRecord record, BoundingBox box, List<long[]> forced) {
		helper.succeedWhen(() -> {
			List<BlockPos> tables = HouseDealers.houseTables(level, record);
			helper.assertTrue(tables != null, name + ": casino chunks loaded");
			helper.assertTrue(!tables.isEmpty(), name + ": house tables found");
			NpcLook.Species species = NpcLook.Species.of(level.dimension().identifier().toString());
			for (int pass = 0; pass < 3; pass++) {
				for (BlockPos main : tables) {
					BlockState state = level.getBlockState(main);
					BlockPos cell = HouseDealers.dealerCell(main, state);
					List<HouseDealer> here = dealersAt(level, cell);
					helper.assertTrue(here.size() == 1, name + " pass " + pass + ": one dealer at " + cell + " for " + main + ", got " + here.size());
					HouseDealer d = here.getFirst();
					d.rebind(level);
					helper.assertTrue(main.equals(d.table()), name + ": dealer bound to its table " + main + ", got " + d.table());
					helper.assertTrue(d.species() == species, name + ": " + species + " dealer, got " + typeId(d));
					if (species == NpcLook.Species.ENDER) {
						helper.assertTrue(level.getBlockState(cell.above(2)).isAir(), name + ": ender head room over " + cell);
					}
					for (BlockPos at : new BlockPos[] {cell, cell.relative(TableParts.facingOf(state))}) {
						List<Entity> old = level.getEntities((Entity) null, new AABB(at),
							e -> typeId(e).equals("burmaldaholic:baccarat_dealer") || typeId(e).equals("burmaldaholic:piglin_dealer"));
						helper.assertTrue(old.isEmpty(), name + ": no old dealer NPC at " + at + ": " + old);
					}
				}
				int all = dealersIn(level, box).size();
				helper.assertTrue(all == tables.size(), name + ": " + tables.size() + " tables, " + all + " dealers");
				helper.assertTrue(HouseDealers.ensure(level, record), name + ": migration pass complete");
			}
			long merchants = level.getEntitiesOfClass(CasinoNpcEntity.class, AABB.of(box).inflate(1), e -> e.role() == NpcRole.MERCHANT).size();
			helper.assertTrue(merchants == 1, name + ": one merchant beside the cashier, got " + merchants);
			// clean up: the test world persists
			for (Entity e : level.getEntities((Entity) null, AABB.of(box).inflate(2), e -> e.entityTags().contains(CasinoNpcs.NPC_TAG))) {
				e.discard();
			}
			CasinoIndex.get(level.getServer()).removeIf(r -> r.id().equals(record.id()));
			for (long[] c : forced) {
				level.setChunkForced((int) c[0], (int) c[1], false);
			}
		});
	}

	/** A site no earlier run used (the test world persists between runs). */
	private static BlockPos fresh(GameTestHelper helper, int y) {
		var r = helper.getLevel().getRandom();
		return new BlockPos(20000 + r.nextInt(400) * 96, y, 20000 + r.nextInt(400) * 96);
	}

	private static Layout layout(String id) {
		return Layouts.byId(id).orElseThrow(() -> new AssertionError("layout " + id));
	}

	private static ServerLevel dimension(GameTestHelper helper, ResourceKey<Level> key) {
		ServerLevel l = helper.getLevel().getServer().getLevel(key);
		return l != null ? l : helper.getLevel();
	}

	@GameTest(maxTicks = 200)
	public void annexHasOneHumanDealerPerTable(GameTestHelper helper) {
		checkCasino(helper, helper.getLevel(), layout("casino/annex/plains"), helper.absolutePos(BlockPos.ZERO).above(70));
	}

	@GameTest(maxTicks = 400)
	public void grandCasinoHasOneHumanDealerPerTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CasinoQa.Target riviera = CasinoQa.standalone(level.getServer()).stream().filter(t -> t.name().equals("casino_riviera")).findFirst()
			.orElseThrow(() -> new AssertionError("casino_riviera structure"));
		BlockPos at = fresh(helper, 0);
		List<long[]> forced = force(level, at, 4);
		CasinoQa.Placed placed = CasinoQa.place(level, riviera, at, 0);
		CasinoRecord record = placed.record().orElseThrow(() -> new AssertionError("grand casino record"));
		verify(helper, level, "casino_riviera", record, placed.box(), forced);
	}

	@GameTest(maxTicks = 200)
	public void parlorHasOnePiglinDealerPerTable(GameTestHelper helper) {
		checkCasino(helper, dimension(helper, Level.NETHER), layout("casino/parlor"), fresh(helper, 40));
	}

	@GameTest(maxTicks = 200)
	public void loungeHasOneEnderDealerPerTable(GameTestHelper helper) {
		checkCasino(helper, dimension(helper, Level.END), layout("casino/lounge"), fresh(helper, 80));
	}

	// ---- house tables only -----------------------------------------------------------------------------------------

	@GameTest
	public void playerOwnedTableGetsNoDealer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = table(helper, PokerModule.TABLE.block(), new BlockPos(3, 1, 4));
		CasinoRecord casino = casinoAround(helper);
		TableOwnershipProvider prev = CoreServices.tableOwnership();
		var owned = new TableOwnershipProvider.OwnedTable(UUID.randomUUID(), "dl_test", 0, 0, true);
		CoreServices.setTableOwnership((l, p) -> p.equals(main) ? Optional.of(owned) : prev.owner(l, p));
		try {
			helper.assertTrue(HouseDealers.ensure(level, casino), "pass complete");
			helper.assertTrue(dealersIn(level, new BoundingBox(main).inflatedBy(3)).isEmpty(), "no dealer at a player-owned table");
		} finally {
			CoreServices.setTableOwnership(prev);
			forget(helper, casino);
		}
		helper.succeed();
	}

	// ---- respawn, idempotence, legacy --------------------------------------------------------------------------

	@GameTest(maxTicks = 340)
	public void killedDealerRespawnsWithTheSameLook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = table(helper, BlackjackModule.TABLE.block(), new BlockPos(3, 1, 4));
		CasinoRecord casino = casinoAround(helper);
		BlockPos cell = TableFootprint.dealerPos(main, Direction.SOUTH, dev.nezo.burmaldaholic.core.table.parts.TableGame.BLACKJACK);
		// like the worldgen marker: spawn on the cell and record the NPC slot
		HouseDealer first = (HouseDealer) CasinoNpcs.spawn(level, NpcRole.HOUSE_DEALER, cell, Direction.SOUTH.toYRot(), casino.id());
		casino.addNpc(NpcRole.HOUSE_DEALER, new Vec(cell.getX(), cell.getY(), cell.getZ()), Direction.SOUTH.toYRot(), first.getUUID());
		int look = first.look();
		UUID old = first.getUUID();
		first.kill(level); // /kill: bypasses the invulnerability
		helper.succeedWhen(() -> {
			List<HouseDealer> now = dealersAt(level, cell);
			List<HouseDealer> near = level.getEntitiesOfClass(HouseDealer.class, new AABB(cell).inflate(4), e -> true);
			helper.assertTrue(now.size() == 1 && !now.getFirst().getUUID().equals(old), "respawned on its cell: " + now.size() + " here, near "
				+ near.stream().map(e -> e.blockPosition().toShortString() + (e.isAlive() ? "" : " dead")).toList() + ", slots "
				+ casino.npcs().stream().map(sl -> sl.role() + "@" + sl.home() + " missing " + sl.missingSince + " " + sl.entity).toList()
				+ ", indexed " + CasinoIndex.get(level.getServer()).casinos().contains(casino) + " v" + casino.dealersVersion);
			helper.assertTrue(now.getFirst().look() == look, "same look after respawn");
			now.getFirst().discard();
			forget(helper, casino);
		});
	}

	@GameTest
	public void migrationIsIdempotentAndAdoptsReloadedDealers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = table(helper, BlackjackModule.TABLE.block(), new BlockPos(3, 1, 4));
		CasinoRecord casino = casinoAround(helper);
		try {
			HouseDealers.ensure(level, casino);
			HouseDealers.ensure(level, casino);
			BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
			helper.assertTrue(dealersAt(level, cell).size() == 1, "ensure twice: one dealer");
			// "unload / reload": the entity comes back from its save data under a new UUID-less lookup; the slot adopts it
			HouseDealer d = dealersAt(level, cell).getFirst();
			d.rebind(level); // the binding is lazy (first tick); this dealer has not ticked yet
			CompoundTag saved = new CompoundTag();
			var out = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
			d.saveWithoutId(out);
			saved = out.buildResult();
			d.discard();
			HouseDealer reloaded = NpcContent.HOUSE_DEALER.create(level, EntitySpawnReason.LOAD);
			reloaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved));
			level.addFreshEntity(reloaded);
			helper.assertTrue(main.equals(reloaded.table()) && cell.equals(reloaded.home()), "binding and home survive a reload");
			HouseDealers.ensure(level, casino);
			CasinoRecord.NpcSlot slot = casino.npcs().stream().filter(s -> s.role() == NpcRole.HOUSE_DEALER).findFirst().orElseThrow();
			helper.assertTrue(CasinoNpcs.respawn(level, slot, casino.id()) == reloaded, "respawn adopts the reloaded dealer");
			helper.assertTrue(dealersAt(level, cell).size() == 1, "still one dealer");
			helper.assertTrue(casino.npcs().stream().filter(s -> s.role() == NpcRole.HOUSE_DEALER).count() == 1, "one slot");
			reloaded.discard();
		} finally {
			forget(helper, casino);
		}
		helper.succeed();
	}

	@GameTest
	public void legacyDealerIsReplaced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = table(helper, BaccaratModule.TABLE.block(), new BlockPos(3, 1, 4));
		CasinoRecord casino = casinoAround(helper);
		try {
			BlockPos legacyAt = main.relative(Direction.NORTH); // v0.1: the baccarat dealer stood at f − 1
			Entity legacy = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("burmaldaholic:baccarat_dealer")).create(level, EntitySpawnReason.STRUCTURE);
			legacy.snapTo(legacyAt.getX() + 0.5, legacyAt.getY(), legacyAt.getZ() + 0.5, 0f, 0f);
			legacy.addTag(CasinoNpcs.NPC_TAG);
			level.addFreshEntity(legacy);
			casino.addNpc(NpcRole.BACCARAT_DEALER, new Vec(legacyAt.getX(), legacyAt.getY(), legacyAt.getZ()), 0f, legacy.getUUID());
			helper.assertTrue(HouseDealers.ensure(level, casino), "pass complete");
			helper.assertTrue(legacy.isRemoved(), "legacy dealer discarded");
			helper.assertTrue(casino.npcs().stream().noneMatch(s -> s.role() == NpcRole.BACCARAT_DEALER), "its slot dropped");
			BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
			helper.assertTrue(dealersAt(level, cell).size() == 1, "house dealer on the dealer cell");
			dealersAt(level, cell).forEach(Entity::discard);
		} finally {
			forget(helper, casino);
		}
		helper.succeed();
	}

	// ---- protection --------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 80)
	public void dealerTakesNoDamageAndCannotBeMoved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos cell = helper.absolutePos(new BlockPos(3, 1, 3));
		HouseDealer d = (HouseDealer) CasinoNpcs.spawn(level, NpcRole.HOUSE_DEALER, cell, 0f, "dl-test");
		Entity zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		List<DamageSource> sources = List.of(level.damageSources().mobAttack((net.minecraft.world.entity.LivingEntity) zombie),
			level.damageSources().source(DamageTypes.ARROW), level.damageSources().lava(), level.damageSources().fall(),
			level.damageSources().source(DamageTypes.EXPLOSION), level.damageSources().inFire());
		for (DamageSource s : sources) {
			helper.assertTrue(!d.hurtServer(level, s, 50f) && d.getHealth() == d.getMaxHealth(), "no damage from " + s.getMsgId());
		}
		zombie.discard();
		level.explode(null, cell.getX() + 1.5, cell.getY(), cell.getZ() + 0.5, 4f, Level.ExplosionInteraction.TNT);
		d.push(2, 0.5, 2);
		d.setDeltaMovement(new Vec3(1, 0, 1));
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(d.isAlive() && d.getHealth() == d.getMaxHealth(), "survived TNT");
			helper.assertTrue(d.position().distanceTo(Vec3.atBottomCenterOf(cell)) <= 0.3, "pushes did not move it: " + d.position());
			helper.assertTrue(!d.isPushable() && !d.canBeLeashed(), "not pushable, no leash");
			d.discard();
			helper.succeed();
		});
	}

	// ---- interaction and binding -------------------------------------------------------------------------------

	@GameTest
	public void clickUsesTheTableAndBreakingIdlesTheDealer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = table(helper, BlackjackModule.TABLE.block(), new BlockPos(3, 1, 4));
		BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
		HouseDealer d = (HouseDealer) CasinoNpcs.spawn(level, NpcRole.HOUSE_DEALER, cell, Direction.SOUTH.toYRot(), "dl-test");
		ServerPlayer p = player(helper, 100);
		MinecraftServer server = level.getServer();
		try {
			d.use(p, level);
			helper.assertTrue(p.containerMenu instanceof CasinoTableMenu m && m.pos().equals(main), "click opened the table: " + p.containerMenu);
			p.closeContainer();
			for (BlockPos c : TableParts.cellPositions(main, level.getBlockState(main))) {
				level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			}
			d.use(p, level);
			helper.assertTrue(d.table() == null && !(p.containerMenu instanceof CasinoTableMenu), "no table: idle (no_table message)");
			TableParts.placeFull(level, main, (MultiBlockTableBlock) BlackjackModule.TABLE.block(), Direction.SOUTH);
			d.rebind(level);
			helper.assertTrue(main.equals(d.table()), "re-placed table: bound again");
		} finally {
			server.getPlayerList().remove(p);
			d.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void blackjackRoundMakesTheDealerGesture(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos main = table(helper, BlackjackModule.TABLE.block(), new BlockPos(3, 1, 4));
		BlockPos cell = HouseDealers.dealerCell(main, level.getBlockState(main));
		HouseDealer d = (HouseDealer) CasinoNpcs.spawn(level, NpcRole.HOUSE_DEALER, cell, Direction.SOUTH.toYRot(), "dl-test");
		BlackjackTableBlockEntity table = (BlackjackTableBlockEntity) level.getBlockEntity(main);
		ServerPlayer p = player(helper, 1000);
		try {
			helper.assertTrue(d.dealerGesture() == DealerGesture.NONE, "idle before the round");
			helper.assertTrue(table.sit(p), "seated");
			CompoundTag amount = new CompoundTag();
			amount.putLong("amount", 10);
			table.onAction(p, "bet", amount);
			helper.assertTrue(table.round() != null, "round dealt");
			helper.assertTrue(d.dealerGesture() != DealerGesture.NONE, "the house dealer gestures with the deal");
		} finally {
			level.getServer().getPlayerList().remove(p);
			d.discard();
		}
		helper.succeed();
	}
}
