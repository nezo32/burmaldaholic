package dev.nezo.burmaldaholic.gametest.bots;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.bots.BotTables;
import dev.nezo.burmaldaholic.core.bots.BotPresets;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * BOT (phase 2): server bot avatars (nameplates) at multi-block tables. For every bot game (blackjack, baccarat, Hold'em,
 * UTH, roulette, craps) and every facing a full 3×2 table gets two bots and a viewer in front of it; the real server tick
 * ({@code BotAvatars}) must spawn one plate per bot exactly at MT's seat point ({@link TableFootprint#seatOffset},
 * {@link TableFootprint#SEAT_HEIGHT}), never above a cell of the table and never inside a block. Plus: a spectator-mode
 * viewer on a carpeted floor (the two root causes), the legacy compact table, and unload safety of the bot lookups.
 */
public class BotAvatarGameTests {
	private static final String TAG = "burmaldaholic_bot_plate";
	private static final Transaction FUNDS = Transaction.of("bots", "gametest_avatars");
	/** Main block of the table (relative); the 8×8 test area gets a stone floor at y = 0. */
	private static final BlockPos MAIN = new BlockPos(4, 1, 4);
	private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	// ---- one test per game: all 4 facings ------------------------------------------------------------------------

	@GameTest(maxTicks = 400)
	public void blackjackPlatesAtSeatPoints(GameTestHelper helper) {
		everyFacing(helper, BlackjackModule.TABLE);
	}

	@GameTest(maxTicks = 400)
	public void baccaratPlatesAtSeatPoints(GameTestHelper helper) {
		everyFacing(helper, BaccaratModule.TABLE);
	}

	@GameTest(maxTicks = 400)
	public void pokerPlatesAtSeatPoints(GameTestHelper helper) {
		everyFacing(helper, PokerModule.TABLE);
	}

	@GameTest(maxTicks = 400)
	public void uthPlatesAtSeatPoints(GameTestHelper helper) {
		everyFacing(helper, UthModule.TABLE);
	}

	@GameTest(maxTicks = 400)
	public void roulettePlatesAtSeatPoints(GameTestHelper helper) {
		everyFacing(helper, RouletteModule.TABLE);
	}

	@GameTest(maxTicks = 400)
	public void crapsPlatesAtSeatPoints(GameTestHelper helper) {
		everyFacing(helper, CrapsModule.TABLE);
	}

	/** Root causes 1 + 2: the only viewer is in spectator mode and the floor round the table is carpeted (generated casinos). */
	@GameTest(maxTicks = 200)
	public void spectatorViewerOnCarpetSeesPlates(GameTestHelper helper) {
		budget();
		ServerPlayer viewer = player(helper, "bot_spectator", GameType.SPECTATOR);
		reset(helper);
		BlockPos main = helper.absolutePos(MAIN);
		TableParts.placeFull(helper.getLevel(), main, (MultiBlockTableBlock) BlackjackModule.TABLE.block(), Direction.SOUTH);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				BlockPos p = helper.absolutePos(new BlockPos(x, 1, z));
				if (helper.getLevel().getBlockState(p).isAir()) {
					helper.getLevel().setBlockAndUpdate(p, Blocks.MOSS_CARPET.defaultBlockState());
				}
			}
		}
		park(helper, viewer, Direction.SOUTH);
		seatTwoBots(helper, main, viewer);
		helper.assertTrue(viewer.isSpectator(), "the viewer is a spectator");
		helper.startSequence().thenWaitUntil(() -> check(helper, main, false, "spectator + carpet"))
			.thenExecute(() -> remove(helper, viewer)).thenSucceed();
	}

	/** Legacy compact (part = single) tables keep the v0.1 half circle round the block. */
	@GameTest(maxTicks = 200)
	public void legacyCompactTablePlates(GameTestHelper helper) {
		budget();
		ServerPlayer viewer = player(helper, "bot_compact", GameType.CREATIVE);
		reset(helper);
		BlockPos main = helper.absolutePos(MAIN);
		MultiBlockTableBlock block = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
		helper.getLevel().setBlockAndUpdate(main, block.defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.EAST)
			.setValue(block.partProperty(), TablePart.SINGLE));
		park(helper, viewer, Direction.EAST);
		seatTwoBots(helper, main, viewer);
		helper.startSequence().thenWaitUntil(() -> check(helper, main, true, "compact"))
			.thenExecute(() -> remove(helper, viewer)).thenSucceed();
	}

	/**
	 * A table block entity in an unloaded chunk: the bot lookups (BotTables, AtmosphereBots.tableBots/table, presets)
	 * neither load the chunk nor reach the preset provider (which reads the block state), nor throw.
	 */
	@GameTest(maxTicks = 100)
	public void unloadedTableLookupsNeverLoadTheChunk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = helper.absolutePos(MAIN).offset(30_000, 0, 30_000);
		int cx = far.getX() >> 4;
		int cz = far.getZ() >> 4;
		helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "the far chunk starts unloaded");
		TablePresetProvider original = CoreServices.tablePresets();
		AtomicInteger probed = new AtomicInteger();
		CoreServices.setTablePresets(new TablePresetProvider() {
			@Override
			public Optional<TablePreset> preset(ServerLevel l, BlockPos pos) {
				if (pos.equals(far)) {
					probed.incrementAndGet();
					l.getBlockState(pos); // what worldgen's provider does: would load the chunk
				}
				return original.preset(l, pos);
			}

			@Override
			public Optional<BotPreset> botDefaults(ServerLevel l, BlockPos pos, String gameId) {
				if (pos.equals(far)) {
					probed.incrementAndGet();
					l.getBlockState(pos);
				}
				return original.botDefaults(l, pos, gameId);
			}
		});
		try {
			MultiBlockTableBlock block = (MultiBlockTableBlock) BlackjackModule.TABLE.block();
			BlockState state = block.defaultBlockState().setValue(block.partProperty(), TablePart.MAIN);
			BlockEntity be = block.newBlockEntity(far, state);
			helper.assertTrue(be instanceof BotTable, "blackjack block entity is a bot table");
			be.setLevel(level);
			BotTable table = (BotTable) be;
			helper.assertTrue(table.tableBots() == null, "no bot state is created for a table in an unloaded chunk");
			helper.assertTrue(BotTables.at(level, far).isEmpty(), "BotTables.at: empty for an unloaded chunk");
			helper.assertTrue(BotPresets.of(be, "blackjack").isEmpty(), "no preset lookup in an unloaded chunk");
			table.botNameTheme();
			table.botDifficultyMix();
			// the helper's own entry point (gameplay never calls it unloaded, but it must not load either)
			((dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity) be).bots().table();
			helper.assertTrue(probed.get() == 0, "the preset provider was reached " + probed.get() + " times for the unloaded table");
			helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "a bot lookup loaded the far chunk");
		} finally {
			CoreServices.setTablePresets(original);
		}
		helper.succeed();
	}

	// ---- steps -----------------------------------------------------------------------------------------------------

	/** One sequence: per facing, place the table + bots + viewer, then wait (each tick) until the plates are right. */
	private static void everyFacing(GameTestHelper helper, TableType<?> type) {
		budget();
		ServerPlayer viewer = player(helper, "bv" + Math.floorMod(type.name().hashCode(), 100000), GameType.CREATIVE);
		BlockPos main = helper.absolutePos(MAIN);
		var seq = helper.startSequence();
		for (Direction facing : FACINGS) {
			String what = type.name() + " " + facing;
			seq = seq.thenExecute(() -> {
				reset(helper);
				TableParts.placeFull(helper.getLevel(), main, (MultiBlockTableBlock) type.block(), facing);
				helper.assertTrue(TableParts.partOf(helper.getLevel().getBlockState(main)) == TablePart.MAIN, what + ": full table placed");
				park(helper, viewer, facing);
				seatTwoBots(helper, main, viewer);
			}).thenWaitUntil(() -> check(helper, main, false, what));
		}
		seq.thenExecute(() -> remove(helper, viewer)).thenSucceed();
	}

	/** Throws (retried each tick by the sequence) until the plates are right. */
	private static void check(GameTestHelper helper, BlockPos main, boolean compact, String what) {
		String why = platesOk(helper, main, compact);
		helper.assertTrue(why == null, what + ": " + why);
	}

	/**
	 * null when every seated bot has exactly one plate at its seat point, the plate's block is free and its column at
	 * table height is not a cell of this table; else the reason.
	 */
	private static String platesOk(GameTestHelper helper, BlockPos main, boolean compact) {
		ServerLevel level = helper.getLevel();
		BlockState state = level.getBlockState(main);
		if (!(level.getBlockEntity(main) instanceof BotTable table)) {
			return "no bot table at " + main.toShortString();
		}
		TablePart part = TableParts.partOf(state);
		if (compact != (part == TablePart.SINGLE)) {
			return "unexpected part " + part;
		}
		TableGame game = TableParts.gameOf(state);
		Direction facing = TableParts.facingOf(state);
		List<SeatOccupant> occupants = table.occupants();
		int seats = Math.max(occupants.size(), table.botSeatCount());
		List<BlockPos> cells = TableParts.cellPositions(main, state); // MT 89c15f71: positions() moved to TableParts
		List<Display.TextDisplay> plates = level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(main).inflate(4),
			d -> d.entityTags().contains(TAG));
		int bots = 0;
		for (int i = 0; i < occupants.size(); i++) {
			if (!(occupants.get(i) instanceof SeatOccupant.Bot)) {
				continue;
			}
			bots++;
			double[] off = TableFootprint.seatOffset(game, part, i, seats, facing.getStepX(), facing.getStepZ());
			Vec3 want = new Vec3(main.getX() + off[0], main.getY() + TableFootprint.SEAT_HEIGHT, main.getZ() + off[1]);
			long at = plates.stream().filter(d -> d.position().distanceTo(want) < 0.01).count();
			if (at != 1) {
				return "seat " + i + "/" + seats + ": " + at + " plates at " + want + " (plates near: " + positions(plates) + ")";
			}
			BlockPos column = BlockPos.containing(want.x, main.getY(), want.z);
			if (cells.contains(column)) {
				return "seat " + i + " plate stands above table cell " + column.toShortString();
			}
			BlockPos plateBlock = BlockPos.containing(want);
			if (!level.getBlockState(plateBlock).getCollisionShape(level, plateBlock).isEmpty()) {
				return "seat " + i + " plate inside a block";
			}
		}
		if (bots < 2) {
			return "only " + bots + " bots seated";
		}
		if (plates.size() != bots) {
			return plates.size() + " plates for " + bots + " bots: " + positions(plates);
		}
		return null;
	}

	private static String positions(List<Display.TextDisplay> plates) {
		List<String> out = new ArrayList<>();
		for (Display.TextDisplay d : plates) {
			out.add(String.format(java.util.Locale.ROOT, "(%.2f,%.2f,%.2f)", d.getX(), d.getY(), d.getZ()));
		}
		return String.join(" ", out);
	}

	// ---- world helpers ---------------------------------------------------------------------------------------------

	/** Room for every test's plates when the bot tests run side by side (default 16 per server). */
	private static void budget() {
		var avatars = CasinoConfig.bots().avatars;
		avatars.maxEntities = Math.max(avatars.maxEntities, 64);
	}

	/** Hold'em has no seat model before a stake level is chosen: the viewer buys in first (as in W2's lane). */
	private static void seatTwoBots(GameTestHelper helper, BlockPos main, ServerPlayer viewer) {
		helper.assertTrue(helper.getLevel().getBlockEntity(main) instanceof BotTable, "a bot table at the main block");
		BotTable table = (BotTable) helper.getLevel().getBlockEntity(main);
		if (table instanceof dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity poker) {
			Economies.get().setBalance(helper.getLevel().getServer(), viewer.getUUID(), 5000, FUNDS);
			net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
			args.putString("level", "micro");
			args.putLong("amount", 200);
			poker.onAction(viewer, "buy_in", args);
		}
		for (int i = 0; i < 2; i++) {
			BotProfile prof = new BotProfile("bot" + i, i == 0 ? "creeper42" : "axolotl_al", BotDifficulty.NORMAL, Personality.TAG);
			boolean money = table.botRole() == BotRole.MONEY;
			table.seatBot(new SeatOccupant.Bot(prof, table.botRole(), money ? Purse.BANK : Purse.NONE), money ? Math.max(200, table.botBuyIn()) : 0);
		}
	}

	/** Stone floor at y = 0, air above (removes the previous table and its plates' blocks). */
	private static void reset(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y < 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.killAllEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class);
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
		return player;
	}

	/** The viewer stands 3 blocks in front of the table (players' side). */
	private static void park(GameTestHelper helper, ServerPlayer p, Direction facing) {
		BlockPos at = helper.absolutePos(MAIN.relative(facing, 3));
		p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, facing.getOpposite().toYRot(), 20f);
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}
}
