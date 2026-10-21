package dev.nezo.burmaldaholic.gametest.v2.blackjack;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane A in-world boards (docs/architecture/ui-v2.md §7.6, §11.4): blackjack and baccarat tables with bots mid-round,
 * seen from 3, 5 and 8 blocks. Asserts that the PUBLIC tag reaches the client (dealer cards, seats, the hole card
 * hidden until its flip) — the two tables showed nothing in the world before v0.2.0 — and screenshots each distance
 * ({@code v2a_world_*}). Called from {@link BlackjackV2ClientGameTests}.
 */
final class CardBoards {
	private CardBoards() {}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		{
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			BlockPos[] base = new BlockPos[1];
			world.getServer().runOnServer(server -> base[0] = player(server).blockPosition());
			blackjack(context, world, base[0].offset(0, 0, 24));
			baccarat(context, world, base[0].offset(14, 0, 24));
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runOnServer(server -> player(server).closeContainer());
			context.waitTicks(10);
		}
	}

	private static void blackjack(ClientGameTestContext context, TestSingleplayerContext world, BlockPos stand) {
		BlockPos pos = place(context, world, stand, BlackjackModule.TABLE, (be, p) -> {
			BlackjackTableBlockEntity t = (BlackjackTableBlockEntity) be;
			t.sit(p);
			t.bots().table().requestChange(p, new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.NORMAL, false, false, BotSpeed.FAST), false);
			t.botSafePointForTests();
			CompoundTag bet = new CompoundTag();
			bet.putLong("amount", 50);
			t.onAction(p, "bet", bet);
		});
		context.waitTicks(60);
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos) instanceof BlackjackTableBlockEntity t) t.revealAllForTests();
		});
		context.waitTicks(10);
		BlackjackPub[] pub = new BlackjackPub[1];
		context.runOnClient(mc -> {
			if (mc.level.getBlockEntity(pos) instanceof BlackjackTableBlockEntity t) pub[0] = t.clientPub();
		});
		if (pub[0] == null || pub[0].dealer().length < 2) throw new AssertionError("blackjack board: no dealer cards on the client: " + pub[0]);
		if (pub[0].seats().isEmpty() || pub[0].seats().get(0).hands().isEmpty()) throw new AssertionError("blackjack board: no seats: " + pub[0]);
		boolean turns = pub[0].phase() == BlackjackPub.TURNS || pub[0].phase() == BlackjackPub.INSURANCE;
		if (turns && pub[0].dealer()[1] != -1) throw new AssertionError("blackjack board: the hole card is public during the turns");
		shots(context, world, pos, "blackjack_turn");
		// play it out: stand, let the dealer finish, shoot the result
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos) instanceof BlackjackTableBlockEntity t) {
				t.onAction(player(server), "stand", new CompoundTag());
				for (int i = 0; i < 12; i++) t.fastForwardForTests();
			}
		});
		context.waitTicks(30);
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos) instanceof BlackjackTableBlockEntity t) t.revealAllForTests();
		});
		context.waitTicks(20);
		look(context, world, pos, 3, "blackjack_result_3m");
	}

	private static void baccarat(ClientGameTestContext context, TestSingleplayerContext world, BlockPos stand) {
		BlockPos pos = place(context, world, stand, BaccaratModule.TABLE, (be, p) -> {
			BaccaratTableBlockEntity t = (BaccaratTableBlockEntity) be;
			t.sit(p);
			CompoundTag b = new CompoundTag();
			b.putString("box", "banker");
			b.putLong("amount", 100);
			t.onAction(p, "bet", b);
			CompoundTag tie = new CompoundTag();
			tie.putString("box", "tie");
			tie.putLong("amount", 10);
			t.onAction(p, "bet", tie);
			t.onAction(p, "ready", new CompoundTag());
			for (int i = 0; i < 4 && !"reveal".equals(t.phase()); i++) t.fastForwardForTests();
		});
		context.waitTicks(140);
		BaccaratPub[] pub = new BaccaratPub[1];
		context.runOnClient(mc -> {
			if (mc.level.getBlockEntity(pos) instanceof BaccaratTableBlockEntity t) pub[0] = t.clientPub();
		});
		if (pub[0] == null || pub[0].player().length < 2 || pub[0].banker().length < 2) {
			throw new AssertionError("baccarat board: no hands on the client: " + pub[0]);
		}
		shots(context, world, pos, "baccarat");
	}

	private static void shots(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, String name) {
		for (int d : new int[] {3, 5, 8}) look(context, world, pos, d, name + "_" + d + "m");
	}

	/** Stands {@code d} blocks in front of the table (it faces north: the players stand at −z) and screenshots. */
	private static void look(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, int d, String shot) {
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5 - d;
		world.getServer().runCommand("tp @p " + x + " " + pos.getY() + " " + z + " facing " + x + " " + (pos.getY() + 1.2) + " " + (pos.getZ() + 0.5));
		context.waitTicks(8);
		context.takeScreenshot("v2a_world_" + shot);
	}

	/** Clears a floor in front of {@code stand}, places the table facing the player 4 blocks away and runs setup with the menu open. */
	private static BlockPos place(ClientGameTestContext context, TestSingleplayerContext world, BlockPos stand, TableType<?> type,
			BiConsumer<CasinoTableBlockEntity, ServerPlayer> setup) {
		BlockPos pos = stand.offset(0, 0, 4);
		world.getServer().runOnServer(server -> {
			var level = server.overworld();
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -6; dz <= 7; dz++) {
					for (int dy = 0; dy <= 5; dy++) level.setBlockAndUpdate(stand.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(stand.offset(dx, -1, dz), Blocks.POLISHED_ANDESITE.defaultBlockState());
				}
			}
			level.setBlockAndUpdate(pos, type.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH));
			ServerPlayer p = player(server);
			if (level.getBlockEntity(pos) instanceof CasinoTableBlockEntity be) {
				p.openMenu(be);
				setup.accept(be, p);
				be.sendStateTo(p);
			}
		});
		context.waitTicks(5);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runOnServer(server -> player(server).closeContainer());
		context.waitTicks(3);
		return pos;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
