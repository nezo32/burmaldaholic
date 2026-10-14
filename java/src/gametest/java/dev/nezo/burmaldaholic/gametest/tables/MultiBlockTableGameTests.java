package dev.nezo.burmaldaholic.gametest.tables;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.core.table.parts.MigrationDecision;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MT-1 (MT-DESIGN §8): multi-block casino tables in a real server world. Tables are placed with the REAL item use
 * ({@code ServerPlayerGameMode.useItemOn}, the same path as the use-item packet) by a survival player and broken with
 * {@code ServerPlayerGameMode.destroyBlock} (fires the break events: ownership, drops). Every table block × every facing.
 */
public class MultiBlockTableGameTests {
	private static final Transaction TEST = Transaction.of("tables", "gametest");
	/** Main block of the table under test (relative); the 8×8×8 test area has its own stone floor at y = 0. */
	private static final BlockPos MAIN = new BlockPos(4, 1, 4);

	// ---- helpers -------------------------------------------------------------------------------

	private static List<MultiBlockTableBlock> tableBlocks() {
		List<MultiBlockTableBlock> out = new ArrayList<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			if (b instanceof MultiBlockTableBlock m) {
				out.add(m);
			}
		}
		return out;
	}

	private static ServerPlayer player(GameTestHelper helper, String name, GameType mode) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return mode;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.gameMode.changeGameModeForPlayer(mode);
		Economies.get().setBalance(server, player.getUUID(), 5000, TEST);
		park(helper, player, Direction.SOUTH);
		return player;
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	/** Puts the player 3 blocks in front of the table (the players' side), looking at it. */
	private static void park(GameTestHelper helper, ServerPlayer p, Direction facing) {
		BlockPos at = helper.absolutePos(MAIN.relative(facing, 3));
		p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, facing.getOpposite().toYRot(), 20f);
		p.setYRot(facing.getOpposite().toYRot());
		p.setYHeadRot(facing.getOpposite().toYRot());
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static void clearArea(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 1; y < 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.killAllEntitiesOfClass(ItemEntity.class);
	}

	/** Uses the table item on the floor under MAIN like a player (real item-use path); returns the result. */
	private static InteractionResult place(GameTestHelper helper, ServerPlayer p, Block block, Direction facing) {
		park(helper, p, facing);
		ItemStack stack = new ItemStack(block.asItem(), 2);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos floor = helper.absolutePos(MAIN.below());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
		return p.gameMode.useItemOn(p, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
	}

	private static BlockPos abs(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	private static List<BlockPos> cells(GameTestHelper helper, MultiBlockTableBlock b, Direction facing) {
		List<BlockPos> out = new ArrayList<>();
		for (int[] o : b.offsets(facing)) {
			out.add(abs(helper, MAIN).offset(o[0], o[1], o[2]));
		}
		return out;
	}

	private static void assertFull(GameTestHelper helper, MultiBlockTableBlock b, Direction facing, String what) {
		ServerLevel level = helper.getLevel();
		BlockPos main = abs(helper, MAIN);
		helper.assertTrue(TableParts.intact(level, main, level.getBlockState(main)) && TableParts.partOf(level.getBlockState(main)) == TablePart.MAIN
			&& TableParts.facingOf(level.getBlockState(main)) == facing, what + ": six cells with the right parts and facing " + facing);
	}

	private static void assertGone(GameTestHelper helper, MultiBlockTableBlock b, Direction facing, String what) {
		for (BlockPos c : cells(helper, b, facing)) {
			helper.assertTrue(helper.getLevel().getBlockState(c).isAir(), what + ": cell " + c.toShortString() + " cleared (is "
				+ helper.getLevel().getBlockState(c) + ")");
		}
	}

	private static int drops(GameTestHelper helper, Block block) {
		int n = 0;
		AABB box = new AABB(abs(helper, BlockPos.ZERO)).minmax(new AABB(abs(helper, new BlockPos(7, 7, 7))));
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (e.getItem().is(block.asItem())) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static String name(Block b) {
		return BuiltInRegistries.BLOCK.getKey(b).getPath();
	}

	// ---- placement + breaking: every table × every facing ---------------------------------------

	/**
	 * Real placement of every table block in every facing: 6 cells with the right part/facing, one item consumed;
	 * then a survival player breaks a different part each time → exactly one item and six air cells.
	 */
	@GameTest(maxTicks = 400)
	public void placeAndBreakEveryTableEveryFacing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = player(helper, "mt-place", GameType.SURVIVAL);
		try {
			int k = 0;
			for (MultiBlockTableBlock b : tableBlocks()) {
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					clearArea(helper);
					InteractionResult r = place(helper, p, b, facing);
					String what = name(b) + " " + facing;
					helper.assertTrue(r.consumesAction(), what + ": placed (" + r + ")");
					helper.assertTrue(p.getMainHandItem().getCount() == 1, what + ": one item used, got " + p.getMainHandItem().getCount());
					assertFull(helper, b, facing, what);
					helper.assertTrue(helper.getLevel().getBlockEntity(abs(helper, MAIN)) instanceof CasinoTableBlockEntity, what + ": block entity on main");
					List<BlockPos> cs = cells(helper, b, facing);
					for (BlockPos c : cs.subList(1, cs.size())) {
						helper.assertTrue(helper.getLevel().getBlockEntity(c) == null, what + ": no block entity on parts");
					}
					BlockPos victim = cs.get(k++ % cs.size());
					helper.assertTrue(p.gameMode.destroyBlock(victim) || helper.getLevel().getBlockState(victim).isAir(), what + ": broke " + victim);
					assertGone(helper, b, facing, what + " broken at " + TableParts.partOf(b.defaultBlockState()));
					helper.assertTrue(drops(helper, b) == 1, what + ": exactly one item dropped, got " + drops(helper, b));
				}
			}
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** No room → refused with the message, nothing placed, the item is kept. Carpets pop (★1). Creative breaking drops nothing. */
	@GameTest(maxTicks = 200)
	public void blockedRefusedCarpetPopsCreativeNoDrop(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = player(helper, "mt-blocked", GameType.SURVIVAL);
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		try {
			Direction facing = Direction.SOUTH;
			BlockPos backLeft = TableFootprint.partPos(MAIN, facing, TablePart.BACK_LEFT);
			helper.setBlock(backLeft, Blocks.OAK_PLANKS);
			InteractionResult r = place(helper, p, b, facing);
			helper.assertFalse(r.consumesAction() && helper.getLevel().getBlockState(abs(helper, MAIN)).is(b), "blocked: refused (" + r + ")");
			helper.assertTrue(p.getMainHandItem().getCount() == 2, "blocked: the item is kept");
			helper.assertTrue(helper.getBlockState(MAIN).isAir(), "blocked: nothing placed");
			helper.assertTrue(helper.getBlockState(backLeft).is(Blocks.OAK_PLANKS), "blocked: foreign block untouched");
			// floor missing under a cell → refused too
			clearArea(helper);
			helper.setBlock(TableFootprint.partPos(MAIN, facing, TablePart.RIGHT).below(), Blocks.AIR);
			helper.assertFalse(place(helper, p, b, facing).consumesAction() && helper.getBlockState(MAIN).is(b), "no floor: refused");
			floor(helper);
			// carpet pops
			clearArea(helper);
			helper.setBlock(TableFootprint.partPos(MAIN, facing, TablePart.LEFT), Blocks.MOSS_CARPET);
			helper.assertTrue(place(helper, p, b, facing).consumesAction(), "carpet: placed");
			assertFull(helper, b, facing, "carpet");
			int carpets = 0;
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs(helper, BlockPos.ZERO)).minmax(new AABB(abs(helper, new BlockPos(7, 7, 7)))))) {
				if (e.getItem().is(Blocks.MOSS_CARPET.asItem())) carpets += e.getItem().getCount();
			}
			helper.assertTrue(carpets == 1, "carpet popped as an item, got " + carpets);
			// creative break of a part → no item, all gone
			helper.killAllEntitiesOfClass(ItemEntity.class);
			ServerPlayer c = player(helper, "mt-creative", GameType.CREATIVE);
			try {
				c.gameMode.destroyBlock(abs(helper, TableFootprint.partPos(MAIN, facing, TablePart.BACK_RIGHT)));
				assertGone(helper, b, facing, "creative");
				helper.assertTrue(drops(helper, b) == 0, "creative: no item, got " + drops(helper, b));
			} finally {
				remove(helper, c);
			}
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** TNT at a part and at the main block: the table goes, at most one item (idempotent explosion drops). */
	@GameTest(maxTicks = 200)
	public void explosionsDropAtMostOneItem(GameTestHelper helper) {
		floor(helper);
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		ServerLevel level = helper.getLevel();
		for (TablePart at : new TablePart[] {TablePart.BACK_LEFT, TablePart.MAIN, TablePart.RIGHT}) {
			for (int i = 0; i < 3; i++) {
				clearArea(helper);
				TableParts.placeFull(level, abs(helper, MAIN), b, Direction.NORTH);
				BlockPos target = abs(helper, TableFootprint.partPos(MAIN, Direction.NORTH, at));
				level.explode(null, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 2.5f, Level.ExplosionInteraction.TNT);
				int n = drops(helper, b);
				helper.assertTrue(n <= 1, "explosion at " + at + ": ≤ 1 item, got " + n);
				for (BlockPos c : cells(helper, b, Direction.NORTH)) {
					BlockState s = level.getBlockState(c);
					helper.assertTrue(s.isAir() || (s.is(b) && TableParts.intact(level, abs(helper, MAIN), level.getBlockState(abs(helper, MAIN)))),
						"explosion at " + at + ": no half table left (" + c.toShortString() + " = " + s + ")");
				}
				if (n == 1) {
					helper.assertTrue(level.getBlockState(abs(helper, MAIN)).isAir(), "an item only when the table is gone");
				}
			}
		}
		helper.succeed();
	}

	/** A piston can't push a part; water (source and flow) doesn't wash parts away; rotate/mirror keep a valid table. */
	@GameTest(maxTicks = 200)
	public void pistonsWaterRotateMirror(GameTestHelper helper) {
		floor(helper);
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		ServerLevel level = helper.getLevel();
		TableParts.placeFull(level, abs(helper, MAIN), b, Direction.NORTH);
		// facing NORTH: RIGHT is at -x of... use the cell positions: push the BACK part (z+1 of main for NORTH) from behind
		BlockPos back = TableFootprint.partPos(MAIN, Direction.NORTH, TablePart.BACK);
		BlockPos piston = back.relative(Direction.SOUTH);
		helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.NORTH));
		helper.setBlock(piston.relative(Direction.SOUTH), Blocks.REDSTONE_BLOCK);
		// water: a source next to the LEFT part (the side away from the piston)
		BlockPos left = TableFootprint.partPos(MAIN, Direction.NORTH, TablePart.LEFT);
		BlockPos water = left.relative(Direction.EAST);
		helper.setBlock(water, Blocks.WATER);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(TableParts.intact(level, abs(helper, MAIN), level.getBlockState(abs(helper, MAIN))), "piston + water: table intact");
			helper.assertTrue(!level.getBlockState(abs(helper, piston)).getValue(PistonBaseBlock.EXTENDED), "piston could not extend into the table");
			// rotate / mirror (structure placement path) keep the parts consistent with the footprint maths
			BlockState main = level.getBlockState(abs(helper, MAIN));
			for (Rotation rot : Rotation.values()) {
				BlockState r = main.rotate(rot);
				helper.assertTrue(TableParts.partOf(r) == TablePart.MAIN && TableParts.facingOf(r) == rot.rotate(Direction.NORTH), "rotate " + rot);
			}
			BlockState leftState = level.getBlockState(abs(helper, left));
			helper.assertTrue(TableParts.partOf(leftState.mirror(Mirror.LEFT_RIGHT)) == TablePart.RIGHT, "mirror swaps LEFT → RIGHT");
			helper.assertTrue(TableParts.partOf(leftState.mirror(Mirror.NONE)) == TablePart.LEFT, "no mirror keeps LEFT");
			// /setblock air on the main block → every part goes, nothing drops
			helper.killAllEntitiesOfClass(ItemEntity.class);
			helper.setBlock(MAIN, Blocks.AIR);
			assertGone(helper, b, Direction.NORTH, "setblock air on main");
			helper.assertTrue(drops(helper, b) == 0, "no item from a /setblock");
			helper.succeed();
		});
	}

	/** Using ANY part opens the main block's menu; a blackjack bet through the packet path works; stillValid from 5 blocks off the far part. */
	@GameTest(maxTicks = 400)
	public void useAnyPartOpensMainAndBets(GameTestHelper helper) {
		floor(helper);
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		ServerLevel level = helper.getLevel();
		Direction facing = Direction.SOUTH;
		TableParts.placeFull(level, abs(helper, MAIN), b, facing);
		ServerPlayer p = player(helper, "mt-use", GameType.SURVIVAL);
		try {
			BlockPos mainAbs = abs(helper, MAIN);
			for (TablePart part : List.of(TablePart.BACK_LEFT, TablePart.RIGHT, TablePart.BACK, TablePart.LEFT, TablePart.BACK_RIGHT, TablePart.MAIN)) {
				p.closeContainer();
				helper.useBlock(TableFootprint.partPos(MAIN, facing, part), p);
				helper.assertTrue(p.containerMenu instanceof CasinoTableMenu m && m.pos().equals(mainAbs),
					"use " + part + " opens the main block's table (got " + p.containerMenu + ")");
			}
			BlackjackTableBlockEntity t = (BlackjackTableBlockEntity) level.getBlockEntity(mainAbs);
			List<dev.nezo.burmaldaholic.games.blackjack.logic.Card> deck = new ArrayList<>();
			for (int v : new int[] {10, 10, 10, 7, 2, 2, 2, 2, 2, 2}) deck.add(dev.nezo.burmaldaholic.games.blackjack.logic.Card.of(v));
			t.stackCardsForTests(deck); // no natural: the stake stays open
			TableNetworking.receive(p, new TableActionPayload(mainAbs, "sit", new CompoundTag()));
			helper.assertTrue(t.isSeated(p), "sit through the packet");
			CompoundTag bet = new CompoundTag();
			bet.putLong("amount", 10L);
			TableNetworking.receive(p, new TableActionPayload(mainAbs, "bet", bet));
			helper.assertTrue(t.stakeOf(p.getUUID()) == 10, "bet through the packet, stake " + t.stakeOf(p.getUUID()));
			// stillValid: 5 blocks beyond the far (back-left) part is still in range of the footprint box
			CasinoTableMenu menu = (CasinoTableMenu) p.containerMenu;
			BlockPos far = abs(helper, TableFootprint.partPos(MAIN, facing, TablePart.BACK_LEFT));
			double max = dev.nezo.burmaldaholic.core.config.CasinoConfig.multiplayer().tableLeaveDistance;
			Vec3 at = Vec3.atBottomCenterOf(far).add(facing.getOpposite().getStepX() * (max - 0.6), 0, facing.getOpposite().getStepZ() * (max - 0.6));
			p.snapTo(at.x, at.y, at.z);
			helper.assertTrue(menu.stillValid(p), "still valid at " + (max - 0.6) + " blocks behind the far part");
			p.snapTo(at.x + facing.getOpposite().getStepX() * 3, at.y, at.z + facing.getOpposite().getStepZ() * 3);
			helper.assertFalse(menu.stillValid(p), "invalid when well beyond the footprint");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	// ---- ownership (§4) --------------------------------------------------------------------------

	/**
	 * Ownership routes every part to the main block: the owner's placed table links; a non-owner can't break any part;
	 * an explosion at a part leaves the linked table whole; the owner breaking a part removes the table and its record.
	 */
	@GameTest(maxTicks = 200)
	public void ownershipRoutesPartsToMain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = player(helper, "mt-owner", GameType.SURVIVAL);
		ServerPlayer other = player(helper, "mt-other", GameType.SURVIVAL);
		// claims keep 40 blocks from the world spawn, and the GameTest batch may sit right on it: when the test area is
		// too close, the scenario runs 300 blocks away from the spawn (absolute coordinates, own floor)
		BlockPos near = abs(helper, BlockPos.ZERO);
		boolean relocated = dev.nezo.burmaldaholic.multiplayer.Ownership.claimError(owner, level, near.above()) != null;
		BlockPos spawn = level.getServer().getRespawnData().pos();
		BlockPos base = relocated ? new BlockPos(spawn.getX() + 300, near.getY(), spawn.getZ() + 300) : near;
		if (relocated) {
			// a fully loaded (entity-ticking) chunk for the drops: force it and give the ticket a second to apply
			force(level, base, true);
			helper.runAfterDelay(20, () -> ownershipScenario(helper, level, owner, other, base, true));
		} else {
			ownershipScenario(helper, level, owner, other, base, false);
		}
	}

	private static void force(ServerLevel level, BlockPos base, boolean on) {
		for (int cx = (base.getX() - 4) >> 4; cx <= (base.getX() + 11) >> 4; cx++)
			for (int cz = (base.getZ() - 4) >> 4; cz <= (base.getZ() + 11) >> 4; cz++) level.setChunkForced(cx, cz, on);
	}

	private static void ownershipScenario(GameTestHelper helper, ServerLevel level, ServerPlayer owner, ServerPlayer other, BlockPos base,
			boolean forced) {
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		Direction facing = Direction.SOUTH;
		BlockPos mainAbs = base.offset(MAIN);
		List<BlockPos> cells = new ArrayList<>();
		for (int[] o : b.offsets(facing)) cells.add(mainAbs.offset(o[0], o[1], o[2]));
		AABB area = new AABB(base).minmax(new AABB(base.offset(7, 7, 7)));
		try {
			for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
				level.setBlock(base.offset(x, 0, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				for (int y = 1; y < 4; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			BlockPos charter = base.above();
			level.setBlock(charter, dev.nezo.burmaldaholic.multiplayer.MultiplayerModule.CHARTER.defaultBlockState(), Block.UPDATE_ALL);
			var why = dev.nezo.burmaldaholic.multiplayer.Ownership.claimError(owner, level, charter);
			helper.assertTrue(why == null, "charter allowed at " + charter.toShortString() + ": " + (why == null ? "" : why.getString()));
			dev.nezo.burmaldaholic.multiplayer.Ownership.claim(owner, level, charter);
			helper.assertTrue(dev.nezo.burmaldaholic.multiplayer.MultiplayerApi.isInsideClaim(level, mainAbs), "casino claimed");
			// real placement by the owner (item use on the floor under the main block, standing on the players' side)
			BlockPos stand = mainAbs.relative(facing, 3);
			owner.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, facing.getOpposite().toYRot(), 20f);
			owner.setYRot(facing.getOpposite().toYRot());
			ItemStack stack = new ItemStack(b.asItem(), 2);
			owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos floorPos = mainAbs.below();
			InteractionResult r = owner.gameMode.useItemOn(owner, level, stack, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(floorPos).add(0, 0.5, 0), Direction.UP, floorPos, false));
			helper.assertTrue(r.consumesAction(), "owner placed the table (" + r + ")");
			helper.assertTrue(TableParts.intact(level, mainAbs, level.getBlockState(mainAbs)), "full table placed");
			helper.assertTrue(dev.nezo.burmaldaholic.multiplayer.Ownership.owner(level, mainAbs).isPresent(), "the owner's table linked (via the main block)");
			BlockPos part = mainAbs.offset(b.offsets(facing).get(4)[0], 0, b.offsets(facing).get(4)[2]); // BACK_LEFT
			other.gameMode.destroyBlock(part);
			helper.assertTrue(TableParts.intact(level, mainAbs, level.getBlockState(mainAbs)), "non-owner break of a part refused");
			level.explode(null, part.getX() + 0.5, part.getY() + 0.5, part.getZ() + 0.5, 2.5f, Level.ExplosionInteraction.TNT);
			helper.assertTrue(TableParts.intact(level, mainAbs, level.getBlockState(mainAbs)), "explosion-proof linked table survives a blast at a part");
			level.getEntitiesOfClass(ItemEntity.class, area).forEach(net.minecraft.world.entity.Entity::discard);
			owner.gameMode.destroyBlock(part);
			for (BlockPos c : cells) helper.assertTrue(level.getBlockState(c).isAir(), "owner broke a part: " + c.toShortString() + " cleared");
			int n = 0;
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area)) if (e.getItem().is(b.asItem())) n += e.getItem().getCount();
			helper.assertTrue(n == 1, "owner: one item, got " + n);
			helper.assertTrue(dev.nezo.burmaldaholic.multiplayer.Ownership.owner(level, mainAbs).isEmpty(), "the table record is forgotten");
			level.getEntitiesOfClass(ItemEntity.class, area).forEach(net.minecraft.world.entity.Entity::discard);
			level.removeBlock(charter, false);
		} finally {
			remove(helper, owner);
			remove(helper, other);
			if (forced) force(level, base, false);
		}
		helper.succeed();
	}

	/**
	 * The CB-G house-protection hook ({@code TableProtection}) gets the MAIN position whichever part is hit: a house
	 * table refuses a break at any part, survives a blast at a part, and drops no item once breaking is allowed.
	 * (The test protection only touches this test's own table; other tests run in parallel.)
	 */
	@GameTest(maxTicks = 200)
	public void houseProtectionHookSeesTheMainBlock(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		Direction facing = Direction.SOUTH;
		BlockPos mainAbs = abs(helper, MAIN);
		boolean[] mayBreak = {false};
		List<BlockPos> seen = new ArrayList<>();
		dev.nezo.burmaldaholic.core.table.parts.TableProtection previous = dev.nezo.burmaldaholic.core.table.parts.TableProtection.get();
		dev.nezo.burmaldaholic.core.table.parts.TableProtection.install(new dev.nezo.burmaldaholic.core.table.parts.TableProtection() {
			@Override
			public boolean dropsAllowed(ServerLevel l, BlockPos main) {
				if (main.equals(mainAbs)) seen.add(main);
				return !main.equals(mainAbs) && previous.dropsAllowed(l, main);
			}

			@Override
			public boolean explosionProof(ServerLevel l, BlockPos main) {
				return main.equals(mainAbs) || previous.explosionProof(l, main);
			}

			@Override
			public boolean breakAllowed(ServerLevel l, BlockPos main, net.minecraft.world.entity.player.Player p) {
				return (!main.equals(mainAbs) || mayBreak[0]) && previous.breakAllowed(l, main, p);
			}
		});
		ServerPlayer p = player(helper, "mt-house", GameType.SURVIVAL);
		try {
			TableParts.placeFull(level, mainAbs, b, facing);
			BlockPos part = abs(helper, TableFootprint.partPos(MAIN, facing, TablePart.BACK_RIGHT));
			p.gameMode.destroyBlock(part);
			assertFull(helper, b, facing, "house table: break at a part refused");
			level.explode(null, part.getX() + 0.5, part.getY() + 0.5, part.getZ() + 0.5, 2.5f, Level.ExplosionInteraction.TNT);
			assertFull(helper, b, facing, "house table: explosion-proof as a whole");
			mayBreak[0] = true;
			helper.killAllEntitiesOfClass(ItemEntity.class);
			p.gameMode.destroyBlock(part);
			assertGone(helper, b, facing, "house table broken once allowed");
			helper.assertTrue(drops(helper, b) == 0, "house table: no item, got " + drops(helper, b));
			// the main block itself: no item either
			TableParts.placeFull(level, mainAbs, b, facing);
			p.gameMode.destroyBlock(mainAbs);
			assertGone(helper, b, facing, "house table main broken");
			helper.assertTrue(drops(helper, b) == 0, "house table main: no item, got " + drops(helper, b));
			helper.assertTrue(!seen.isEmpty(), "the hook was asked with the main position");
		} finally {
			dev.nezo.burmaldaholic.core.table.parts.TableProtection.install(previous);
			remove(helper, p);
		}
		helper.succeed();
	}

	// ---- migration -------------------------------------------------------------------------------

	/**
	 * GROW keeps the block entity: a compact table with a round in play unfolds (SINGLE → MAIN on the same block) —
	 * same block entity object, stake still open, the round not played out.
	 */
	@GameTest(maxTicks = 200)
	public void unfoldKeepsBlockEntityAndStake(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(MAIN, BlackjackModule.TABLE.block().defaultBlockState().setValue(MultiBlockTableBlock.FACING, Direction.SOUTH));
		BlackjackTableBlockEntity t = helper.getBlockEntity(MAIN, BlackjackTableBlockEntity.class);
		// player 10,10 vs dealer 10 up, 7 hole: no natural, the round waits for the player
		List<dev.nezo.burmaldaholic.games.blackjack.logic.Card> cards = new ArrayList<>(List.of(dev.nezo.burmaldaholic.games.blackjack.logic.Card.of(10), dev.nezo.burmaldaholic.games.blackjack.logic.Card.of(10),
			dev.nezo.burmaldaholic.games.blackjack.logic.Card.of(10), dev.nezo.burmaldaholic.games.blackjack.logic.Card.of(7)));
		for (int i = 0; i < 10; i++) cards.add(dev.nezo.burmaldaholic.games.blackjack.logic.Card.of(2));
		t.stackCardsForTests(cards);
		ServerPlayer p = player(helper, "mt-grow", GameType.SURVIVAL);
		try {
			t.sit(p);
			CompoundTag bet = new CompoundTag();
			bet.putLong("amount", 10L);
			t.onAction(p, "bet", bet);
			helper.assertTrue(t.stakeOf(p.getUUID()) == 10, "stake open before unfolding");
			helper.assertTrue(TableParts.partOf(level.getBlockState(abs(helper, MAIN))) == TablePart.SINGLE, "compact before");
			MigrationDecision.Outcome o = TableParts.tryUnfold(level, abs(helper, MAIN));
			helper.assertTrue(o == MigrationDecision.Outcome.GROW, "free space → GROW, got " + o);
			assertFull(helper, (MultiBlockTableBlock) BlackjackModule.TABLE.block(), Direction.SOUTH, "unfolded");
			helper.assertTrue(level.getBlockEntity(abs(helper, MAIN)) == t, "the SAME block entity object");
			helper.assertTrue(t.stakeOf(p.getUUID()) == 10 && !t.isRemoved(), "stake intact, round not played out");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/**
	 * Legacy detection: a table saved without the {@code mt} tag unfolds on its first tick when there is room; with a
	 * foreign block in a cell it stays compact; a player in a cell → RETRY; sneak-use with an empty hand after clearing
	 * the space unfolds it. A casino dealer NPC standing in a cell steps to the dealer cell.
	 */
	@GameTest(maxTicks = 300)
	public void legacyMigration(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Block block = BlackjackModule.TABLE.block();
		Direction facing = Direction.SOUTH;
		// 1. free → GROW on the first tick
		legacy(helper, block, facing);
		helper.runAfterDelay(3, () -> {
			assertFull(helper, (MultiBlockTableBlock) block, facing, "legacy table with room");
			// 2. blocked → stays compact; sneak-use after clearing grows
			clearArea(helper);
			BlockPos blocker = TableFootprint.partPos(MAIN, facing, TablePart.BACK);
			helper.setBlock(blocker, Blocks.BOOKSHELF);
			legacy(helper, block, facing);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(TableParts.partOf(helper.getBlockState(MAIN)) == TablePart.SINGLE, "blocked → COMPACT");
				helper.assertTrue(helper.getBlockState(blocker).is(Blocks.BOOKSHELF), "never touches a foreign block");
				ServerPlayer p = player(helper, "mt-sneak", GameType.SURVIVAL);
				try {
					p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					p.setShiftKeyDown(true);
					helper.useBlock(MAIN, p);
					helper.assertTrue(TableParts.partOf(helper.getBlockState(MAIN)) == TablePart.SINGLE, "sneak-use while blocked: still compact");
					helper.setBlock(blocker, Blocks.AIR);
					helper.useBlock(MAIN, p);
					assertFull(helper, (MultiBlockTableBlock) block, facing, "sneak-use after clearing");
				} finally {
					remove(helper, p);
				}
				// 3. a player in a cell → RETRY (stays compact this load)
				clearArea(helper);
				ServerPlayer stander = player(helper, "mt-stander", GameType.SURVIVAL);
				BlockPos inCell = abs(helper, TableFootprint.partPos(MAIN, facing, TablePart.LEFT));
				stander.snapTo(inCell.getX() + 0.5, inCell.getY(), inCell.getZ() + 0.5);
				helper.assertTrue(TableParts.tryUnfold(level, setSingle(helper, block, facing)) == MigrationDecision.Outcome.RETRY, "player in a cell → RETRY");
				remove(helper, stander);
				// 4. a casino dealer NPC in a cell → GROW, the dealer moved to the dealer cell
				clearArea(helper);
				BlockPos mainAbs = setSingle(helper, block, facing);
				var type = BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("burmaldaholic", "croupier"));
				net.minecraft.world.entity.Entity npc = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				BlockPos npcCell = abs(helper, TableFootprint.partPos(MAIN, facing, TablePart.BACK));
				npc.snapTo(npcCell.getX() + 0.5, npcCell.getY(), npcCell.getZ() + 0.5);
				level.addFreshEntity(npc);
				MigrationDecision.Outcome o = TableParts.tryUnfold(level, mainAbs);
				helper.assertTrue(o == MigrationDecision.Outcome.GROW, "dealer in a cell → GROW (moved), got " + o);
				BlockPos dealerCell = TableFootprint.dealerPos(mainAbs, facing, ((MultiBlockTableBlock) block).game());
				helper.assertTrue(npc.blockPosition().equals(dealerCell), "dealer stepped to the dealer cell " + dealerCell.toShortString() + ", is at "
					+ npc.blockPosition().toShortString());
				npc.discard();
				helper.succeed();
			});
		});
	}

	private static BlockPos setSingle(GameTestHelper helper, Block block, Direction facing) {
		helper.setBlock(MAIN, block.defaultBlockState().setValue(MultiBlockTableBlock.FACING, facing));
		return abs(helper, MAIN);
	}

	/** A compact table whose block entity was loaded from a v0.1 save (no {@code mt} tag). */
	private static void legacy(GameTestHelper helper, Block block, Direction facing) {
		BlockPos mainAbs = setSingle(helper, block, facing);
		CasinoTableBlockEntity be = (CasinoTableBlockEntity) helper.getLevel().getBlockEntity(mainAbs);
		CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(tag.getIntOr("mt", 0) == 1, "new tables save the mt tag");
		tag.remove("mt");
		be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
	}

	/** Chunk guard: a part whose neighbour sits in an unloaded chunk never cascades (pure check of the guard path). */
	@GameTest(maxTicks = 100)
	public void integrityRepairsOrFoldsOnFirstTick(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		// a lone MAIN (as from /setblock or a partial structure copy) regrows its parts when the cells are free
		helper.setBlock(MAIN, b.defaultBlockState().setValue(MultiBlockTableBlock.FACING, Direction.EAST).setValue(MultiBlockTableBlock.PART, TablePart.MAIN));
		helper.runAfterDelay(3, () -> {
			assertFull(helper, b, Direction.EAST, "lone main regrew its parts");
			// a lone MAIN with a foreign block in a cell folds to a compact table
			clearArea(helper);
			helper.setBlock(TableFootprint.partPos(MAIN, Direction.EAST, TablePart.RIGHT), Blocks.STONE);
			helper.setBlock(MAIN, b.defaultBlockState().setValue(MultiBlockTableBlock.FACING, Direction.EAST).setValue(MultiBlockTableBlock.PART, TablePart.MAIN));
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(TableParts.partOf(helper.getBlockState(MAIN)) == TablePart.SINGLE, "lone main without room folds to compact");
				helper.assertTrue(level.getBlockEntity(abs(helper, MAIN)) instanceof CasinoTableBlockEntity, "and keeps its block entity");
				helper.succeed();
			});
		});
	}
	// ---- worldgen (§6) ---------------------------------------------------------------------------

	/** Every MAIN in {@code box} has its whole footprint and every part has its main; returns the number of tables. */
	private static int assertTablesWhole(GameTestHelper helper, ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box, String what) {
		int mains = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (!(s.getBlock() instanceof MultiBlockTableBlock)) continue;
			TablePart part = TableParts.partOf(s);
			helper.assertTrue(part != TablePart.SINGLE, what + ": no compact table in a v0.2 template at " + p.toShortString());
			BlockPos main = TableParts.mainPos(p, s);
			helper.assertTrue(TableParts.intact(level, main, level.getBlockState(main)), what + ": whole table around " + p.toShortString() + " (" + s + ")");
			if (part == TablePart.MAIN) {
				mains++;
				helper.assertTrue(level.getBlockEntity(p) instanceof CasinoTableBlockEntity, what + ": block entity on the main block " + p.toShortString());
			}
		}
		return mains;
	}

	/** The regenerated v0.1 templates (all kinds, all rotations) place full tables that survive the first ticks. */
	@GameTest(maxTicks = 200)
	public void regeneratedTemplatesPlaceFullTables(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(190);
		List<Runnable> checks = new ArrayList<>();
		int i = 0;
		for (dev.nezo.burmaldaholic.worldgen.logic.Layout l : dev.nezo.burmaldaholic.worldgen.logic.Layouts.all().values()) {
			for (int turns = 0; turns < 4; turns++) {
				BlockPos at = origin.offset((i % 4) * 26, (i / 4) * 14, 0);
				i++;
				var rotation = dev.nezo.burmaldaholic.worldgen.CasinoStructures.rotation(turns);
				var box = dev.nezo.burmaldaholic.worldgen.CasinoBuilder.build(level, l, at, rotation).orElseThrow();
				String what = l.id() + " rotated " + turns;
				int n = assertTablesWhole(helper, level, box, what + " (placed)");
				helper.assertTrue(n >= 3, what + ": tables placed (" + n + ")");
				checks.add(() -> assertTablesWhole(helper, level, box, what + " (after 5 ticks)"));
			}
		}
		helper.runAfterDelay(5, () -> {
			checks.forEach(Runnable::run);
			helper.succeed();
		});
	}

	/**
	 * Old-template survey (§5): the committed v0.1 templates (kept as fixtures) place compact tables; for each one the
	 * migration decides GROW / COMPACT / RETRY like it would on the first load of an old world. Reported in the log;
	 * every table must at least stay a valid table (grown or compact).
	 */
	@GameTest(maxTicks = 200)
	public void oldTemplateMigrationSurvey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(150);
		var manager = dev.nezo.burmaldaholic.worldgen.CasinoBuilder.templates(level.getServer());
		String[] ids = {"village_casino_plains", "village_casino_desert", "village_casino_savanna", "village_casino_snowy", "village_casino_taiga",
			"piglin_parlor", "high_roller_lounge"};
		StringBuilder report = new StringBuilder("[MT] old v0.1 template survey:");
		int grown = 0;
		int total = 0;
		for (int i = 0; i < ids.length; i++) {
			var template = manager.get(net.minecraft.resources.Identifier.fromNamespaceAndPath("burmaldaholic-gametest", "mt_v01/" + ids[i])).orElseThrow();
			BlockPos at = origin.offset((i % 4) * 26, (i / 4) * 14, 0);
			template.placeInWorld(level, at, at, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), level.getRandom(),
				Block.UPDATE_CLIENTS);
			var size = template.getSize();
			report.append("\n  ").append(ids[i]).append(':');
			for (BlockPos p : BlockPos.betweenClosed(at, at.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				BlockState s = level.getBlockState(p);
				if (!(s.getBlock() instanceof MultiBlockTableBlock) || TableParts.partOf(s) != TablePart.SINGLE) continue;
				BlockPos table = p.immutable();
				total++;
				MigrationDecision.Outcome o = TableParts.tryUnfold(level, table);
				if (o == MigrationDecision.Outcome.GROW) grown++;
				report.append(' ').append(name(s.getBlock())).append('@').append(table.subtract(at).toShortString()).append('=').append(o);
				BlockState after = level.getBlockState(table);
				helper.assertTrue(after.getBlock() instanceof MultiBlockTableBlock && (TableParts.partOf(after) == TablePart.SINGLE
					|| TableParts.intact(level, table, after)), ids[i] + ": table at " + table.toShortString() + " valid after " + o);
			}
		}
		report.append("\n  grown ").append(grown).append(" / ").append(total);
		dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.info(report.toString());
		helper.assertTrue(total >= 20, "old templates hold the v0.1 tables (" + total + ")");
		helper.succeed();
	}
}
