package dev.nezo.burmaldaholic.gametest.tables;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Tester probes for package MT (T-MT): money / loot safety on every removal path. */
public class MultiBlockTesterGameTests {
	private static final Transaction TEST = Transaction.of("tables", "gametest");
	private static final BlockPos MAIN = new BlockPos(4, 1, 4);

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
		BlockPos at = helper.absolutePos(MAIN.relative(Direction.SOUTH, 3));
		player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Direction.NORTH.toYRot(), 20f);
		return player;
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static void floorAndClear(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y < 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.killAllEntitiesOfClass(ItemEntity.class);
	}

	private static int drops(GameTestHelper helper, Block block) {
		int n = 0;
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).minmax(new AABB(helper.absolutePos(new BlockPos(7, 7, 7))));
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box.inflate(2))) {
			if (e.getItem().is(block.asItem())) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static List<BlockPos> cells(GameTestHelper helper, MultiBlockTableBlock b, Direction facing) {
		List<BlockPos> out = new ArrayList<>();
		for (dev.nezo.burmaldaholic.core.table.parts.LocalCell c : b.cells()) {
			int[] o = c.offset(facing.getStepX(), facing.getStepZ());
			out.add(helper.absolutePos(MAIN).offset(o[0], o[1], o[2]));
		}
		return out;
	}

	private static void allAir(GameTestHelper helper, MultiBlockTableBlock b, Direction facing, String what) {
		for (BlockPos c : cells(helper, b, facing)) {
			helper.assertTrue(helper.getLevel().getBlockState(c).isAir(), what + ": " + c.toShortString() + " is " + helper.getLevel().getBlockState(c));
		}
	}

	/** Blackjack table with a seated player and an open 10-chip stake (no natural). */
	private static BlackjackTableBlockEntity tableWithStake(GameTestHelper helper, ServerPlayer p, Direction facing) {
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		TableParts.placeFull(helper.getLevel(), helper.absolutePos(MAIN), b, facing);
		BlackjackTableBlockEntity t = (BlackjackTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(MAIN));
		List<dev.nezo.burmaldaholic.games.blackjack.logic.Card> deck = new ArrayList<>();
		for (int v : new int[] {10, 10, 10, 7, 2, 2, 2, 2, 2, 2}) deck.add(dev.nezo.burmaldaholic.games.blackjack.logic.Card.of(v));
		t.stackCardsForTests(deck);
		t.sit(p);
		CompoundTag bet = new CompoundTag();
		bet.putLong("amount", 10L);
		t.onAction(p, "bet", bet);
		helper.assertTrue(t.stakeOf(p.getUUID()) == 10, "stake open");
		return t;
	}

	/**
	 * A survival player with an open stake breaks a PART (every facing): one item, the block entity gone and the stake
	 * settled (balance back in the legal range, nothing lost): player 20 vs dealer 17 → a 10-chip win (5010) or a refund (5000).
	 */
	@GameTest(maxTicks = 200)
	public void partBreakWithOpenStakeSettles(GameTestHelper helper) {
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			floorAndClear(helper);
			ServerPlayer p = player(helper, "tmt-stake", GameType.SURVIVAL);
			try {
				BlackjackTableBlockEntity t = tableWithStake(helper, p, facing);
				long before = Economies.get().balance(p);
				p.gameMode.destroyBlock(helper.absolutePos(TableFootprint.partPos(MAIN, facing, TablePart.BACK_LEFT)));
				allAir(helper, b, facing, "part break " + facing);
				helper.assertTrue(drops(helper, b) == 1, facing + ": one item, got " + drops(helper, b));
				helper.assertTrue(t.isRemoved() && t.stakeOf(p.getUUID()) == 0, facing + ": BE removed, stake settled");
				long after = Economies.get().balance(p);
				helper.assertTrue(after == before + 10 || after == before + 20, facing + ": stake refunded or won (before " + before + ", after " + after + ")");
			} finally {
				remove(helper, p);
			}
		}
		helper.succeed();
	}

	/** TNT at a part of a table with an open stake: ≤1 item, no half table, stake settled. */
	@GameTest(maxTicks = 200)
	public void explosionWithOpenStakeSettles(GameTestHelper helper) {
		floorAndClear(helper);
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		ServerPlayer p = player(helper, "tmt-boom", GameType.SURVIVAL);
		try {
			BlackjackTableBlockEntity t = tableWithStake(helper, p, Direction.SOUTH);
			long before = Economies.get().balance(p);
			BlockPos part = helper.absolutePos(TableFootprint.partPos(MAIN, Direction.SOUTH, TablePart.BACK_RIGHT));
			helper.getLevel().explode(null, part.getX() + 0.5, part.getY() + 0.5, part.getZ() + 0.5, 4f, Level.ExplosionInteraction.TNT);
			allAir(helper, b, Direction.SOUTH, "explosion");
			helper.assertTrue(drops(helper, b) <= 1, "≤1 item, got " + drops(helper, b));
			long after = Economies.get().balance(p);
			helper.assertTrue(t.isRemoved() && (after == before + 10 || after == before + 20), "stake settled (before " + before + ", after " + after + ")");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/**
	 * HouseGames contract (dropsAllowed: "every removal path: ops, /setblock … destroy, withers"): a house table whose
	 * part is destroyed by a non-player (wither body break = level.destroyBlock(pos, true, wither)) must not drop an item.
	 */
	@GameTest(maxTicks = 100)
	public void houseTableNonPlayerRemovalDropsNothing(GameTestHelper helper) {
		floorAndClear(helper);
		ServerLevel level = helper.getLevel();
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BlockPos z = helper.absolutePos(new BlockPos(7, 7, 7));
		String id = "gametest|tmt_house|" + a.getX() + "|" + a.getY() + "|" + a.getZ();
		var index = dev.nezo.burmaldaholic.worldgen.CasinoIndex.get(level.getServer());
		index.removeIf(r -> r.id().equals(id));
		index.setAnchor(id, level.dimension().identifier().toString(), "gametest/tmt_house", dev.nezo.burmaldaholic.worldgen.logic.CasinoKind.VILLAGE_CASINO,
			dev.nezo.burmaldaholic.worldgen.logic.Geometry.Box.corners(new dev.nezo.burmaldaholic.worldgen.logic.Vec(a.getX(), a.getY(), a.getZ()),
				new dev.nezo.burmaldaholic.worldgen.logic.Vec(z.getX(), z.getY(), z.getZ())));
		try {
			StringBuilder fails = new StringBuilder();
			for (TablePart hit : new TablePart[] {TablePart.BACK_LEFT, TablePart.MAIN}) {
				floorAndClear(helper);
				BlockPos main = helper.absolutePos(MAIN);
				TableParts.placeFull(level, main, b, Direction.NORTH);
				helper.assertTrue(dev.nezo.burmaldaholic.worldgen.HouseGames.isHouseTable(level, main, level.getBlockEntity(main)), "house table");
				level.destroyBlock(helper.absolutePos(TableFootprint.partPos(MAIN, Direction.NORTH, hit)), true, null);
				allAir(helper, b, Direction.NORTH, "non-player removal at " + hit);
				int n = drops(helper, b);
				if (n != 0) fails.append(hit).append(" → ").append(n).append(" item(s); ");
			}
			helper.assertTrue(fails.isEmpty(), "house table looted by a non-player removal: " + fails);
		} finally {
			index.removeIf(r -> r.id().equals(id));
		}
		helper.succeed();
	}

	/** A second table whose footprint overlaps the first one's is refused, the item kept, the first table untouched. */
	@GameTest(maxTicks = 100)
	public void overlappingTableRefused(GameTestHelper helper) {
		floorAndClear(helper);
		ServerLevel level = helper.getLevel();
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		TableParts.placeFull(level, helper.absolutePos(MAIN), b, Direction.SOUTH);
		ServerPlayer p = player(helper, "tmt-overlap", GameType.SURVIVAL);
		try {
			// second main two cells to the right: its LEFT cell = first table's RIGHT cell
			BlockPos main2 = TableFootprint.cellPos(MAIN, Direction.SOUTH, 2, 0);
			ItemStack stack = new ItemStack(b.asItem(), 2);
			p.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos floor = helper.absolutePos(main2.below());
			BlockPos stand = helper.absolutePos(main2.relative(Direction.SOUTH, 3));
			p.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Direction.NORTH.toYRot(), 20f);
			p.gameMode.useItemOn(p, level, stack, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
			helper.assertTrue(p.getMainHandItem().getCount() == 2, "item kept");
			helper.assertTrue(helper.getBlockState(main2).isAir(), "nothing placed");
			helper.assertTrue(TableParts.intact(level, helper.absolutePos(MAIN), level.getBlockState(helper.absolutePos(MAIN))), "first table intact");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** Fire and lava next to the parts, a hopper under one: after 100 ticks the table stands, nothing dropped. */
	@GameTest(maxTicks = 200)
	public void fireLavaHopperLeaveTableAlone(GameTestHelper helper) {
		floorAndClear(helper);
		ServerLevel level = helper.getLevel();
		MultiBlockTableBlock b = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		helper.setBlock(TableFootprint.partPos(MAIN, Direction.SOUTH, TablePart.RIGHT).below(), Blocks.HOPPER);
		TableParts.placeFull(level, helper.absolutePos(MAIN), b, Direction.SOUTH);
		BlockPos left = TableFootprint.partPos(MAIN, Direction.SOUTH, TablePart.LEFT);
		helper.setBlock(left.relative(Direction.WEST).below(), Blocks.NETHERRACK);
		helper.setBlock(left.relative(Direction.WEST), Blocks.FIRE);
		helper.setBlock(TableFootprint.partPos(MAIN, Direction.SOUTH, TablePart.BACK).relative(Direction.NORTH), Blocks.LAVA);
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(TableParts.intact(level, helper.absolutePos(MAIN), level.getBlockState(helper.absolutePos(MAIN))), "table intact");
			helper.assertTrue(drops(helper, b) == 0, "nothing dropped");
			helper.succeed();
		});
	}
}
