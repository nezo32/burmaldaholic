package dev.nezo.burmaldaholic.gametest.uth;

import dev.nezo.burmaldaholic.client.table.cards.CardButton;
import dev.nezo.burmaldaholic.core.anim.cards.CardLayout;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.client.UthScreen;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * v0.1.2 hotfix, REAL player flow for "I cannot change the UTH bet": a 1920 × 1080 window at GUI scale 4 (Auto at 1080p,
 * a 480 × 270 GUI) and 3; the player looks at an Ultimate Texas Hold'em table and presses Use, clicks Sit down, sets the
 * Ante with the chip rack (real mouse clicks at the drawn chips), presses Deal and plays the round to its payout by
 * clicking the decision buttons. Asserts the SERVER state after every step: the dealt Ante / Trips are exactly what was
 * clicked, the balance moves by the round's return. Then changes the bet for the next round (a chip above the table
 * limit is refused with an error and the Ante clamps, − undoes it, the Trips target, Clear + Rebet) and deals again.
 * Screenshots {@code uth_flow_*}.
 */
public class UthRealFlowClientGameTests implements FabricClientGameTest {
	private static final int TIMEOUT = 600;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
				ClientTestWorlds.Quiet q = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("casino balance set @p 1000");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(3);
			guiScale(context, 4);
			String gui = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
			check(gui.equals("480x270"), "GUI scale 4 at 1920 × 1080 is 480 × 270, got " + gui);

			BlockPos pos = placeTable(world);
			openByUse(context, world, pos);
			context.takeScreenshot("uth_flow_s4_opened");

			// Sit down (a real click on the console button)
			clickButton(context, "gui.burmaldaholic.poker.sit_down");
			waitServer(context, world, "seated", s -> table(s, pos).seats().seatOf(player(s).getUUID()).isPresent());
			context.waitTicks(5);
			context.takeScreenshot("uth_flow_s4_seated");

			// Round 1: an Ante of 10 — two 5 chips — then Deal
			clickChip(context, 1);
			clickChip(context, 1);
			context.waitTicks(3);
			context.takeScreenshot("uth_flow_s4_ante10");
			long before = balance(world);
			clickButton(context, "gui.burmaldaholic.common.deal");
			waitServer(context, world, "round 1 dealt", s -> mySeat(s, pos) != null);
			long[] dealt = dealt(world, pos);
			check(dealt[0] == 10 && dealt[1] == 0, "round 1: dealt Ante/Trips " + dealt[0] + "/" + dealt[1] + ", clicked 10/0");
			check(balance(world) == before - 20, "round 1: Ante + Blind debited (" + before + " → " + balance(world) + ")");
			playToPayout(context, world, pos, before, "round1");

			// Round 2: the bet is changed. The 25 chip is above the bronze limit (6 × Ante ≤ 100): refused with a message, the
			// Ante clamps to the largest allowed (16); − undoes it (10); +5 → 15; the Trips target gets one 1 chip.
			context.waitTicks(5);
			context.takeScreenshot("uth_flow_s4_round2_betting");
			check(pendingAnte(context) == 10, "round 2 starts from the last Ante (10), pending " + pendingAnte(context));
			clickChip(context, 2);
			context.waitTicks(2);
			check(pendingAnte(context) == 16, "a chip above the limit clamps the Ante to 16, pending " + pendingAnte(context));
			check(errorShown(context), "a chip above the limit tells the player why");
			context.takeScreenshot("uth_flow_s4_limit_error");
			clickButton(context, "gui.burmaldaholic.uth.undo");
			check(pendingAnte(context) == 10, "− undoes the last chip, pending " + pendingAnte(context));
			clickChip(context, 1);
			clickButton(context, "gui.burmaldaholic.uth.target.ante");
			clickChip(context, 0);
			context.waitTicks(3);
			context.takeScreenshot("uth_flow_s4_ante15_trips1");
			before = balance(world);
			clickButton(context, "gui.burmaldaholic.common.deal");
			waitServer(context, world, "round 2 dealt", s -> mySeat(s, pos) != null && mySeat(s, pos).ante == 15);
			dealt = dealt(world, pos);
			check(dealt[0] == 15 && dealt[1] == 1, "round 2: dealt Ante/Trips " + dealt[0] + "/" + dealt[1] + ", clicked 15/1");
			playToPayout(context, world, pos, before, "round2");

			// Round 3 at GUI scale 3: Clear, then Rebet restores 15 / 1; Deal
			guiScale(context, 3);
			context.waitTicks(5);
			clickButton(context, "gui.burmaldaholic.common.clear");
			check(pendingAnte(context) == 0, "Clear empties the Ante, pending " + pendingAnte(context));
			clickButton(context, "gui.burmaldaholic.common.rebet");
			context.waitTicks(3);
			context.takeScreenshot("uth_flow_s3_rebet");
			before = balance(world);
			clickButton(context, "gui.burmaldaholic.common.deal");
			waitServer(context, world, "round 3 dealt", s -> mySeat(s, pos) != null && mySeat(s, pos).ante == 15);
			dealt = dealt(world, pos);
			check(dealt[0] == 15 && dealt[1] == 1, "round 3 (rebet): dealt Ante/Trips " + dealt[0] + "/" + dealt[1]);
			playToPayout(context, world, pos, before, "round3");

			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(20);
		} finally {
			context.getInput().resizeWindow(854, 480);
			guiScale(context, 0);
		}
	}

	// ---- flow -----------------------------------------------------------------------------------------------------

	private static BlockPos placeTable(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			BlockPos at = p.blockPosition().offset(0, 0, 2);
			server.overworld().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(at.above(), Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(at, UthModule.TABLE.block().defaultBlockState());
			return at;
		});
	}

	/** Looks at the table and presses the Use key, as a player does. */
	private static void openByUse(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
		world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ facing " + (pos.getX() + 0.5) + " " + (pos.getY() + 0.5) + " " + (pos.getZ() + 0.5));
		context.waitTicks(5);
		context.getInput().pressKey(o -> o.keyUse);
		context.waitFor(mc -> mc.gui.screen() instanceof UthScreen, TIMEOUT);
		context.waitTicks(10);
	}

	/** Clicks Check while it is legal, then Bet ×1 at the river, until the round settles; checks the payout. */
	private static void playToPayout(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, long before, String name) {
		boolean shotDecision = false;
		for (int waited = 0; waited < 2400; waited += 5) {
			UthRound.Seat seat = world.getServer().computeOnServer(s -> mySeat(s, pos));
			if (seat != null && seat.result != null && world.getServer().computeOnServer(s -> !UthTableBlockEntity.SHOWDOWN.equals(phase(s, pos)))) {
				long ret = seat.result.totalReturn();
				context.waitTicks(40);
				context.takeScreenshot("uth_flow_s4_" + name + "_payout");
				long after = balance(world);
				check(after == before + ret, name + ": balance " + before + " + return " + ret + " = " + (before + ret) + ", got " + after);
				context.waitFor(mc -> buttonCenter(mc, "gui.burmaldaholic.common.deal") != null, TIMEOUT);
				return;
			}
			String id = context.computeOnClient(mc -> buttonCenter(mc, "gui.burmaldaholic.uth.check") != null ? "gui.burmaldaholic.uth.check"
				: buttonCenter(mc, "gui.burmaldaholic.uth.button.bet_1x") != null ? "gui.burmaldaholic.uth.button.bet_1x" : null);
			if (id != null && seat != null && !seat.decided) {
				if (!shotDecision) {
					context.takeScreenshot("uth_flow_s4_" + name + "_decision");
					shotDecision = true;
				}
				clickButton(context, id);
				context.waitTicks(10);
			} else {
				context.waitTicks(5);
			}
		}
		throw new AssertionError(name + ": the round did not settle");
	}

	// ---- input ------------------------------------------------------------------------------------------------------

	/** Canvas → window pixels (the card canvas: integer scale k, centred; CardTableScreen#init). */
	private static double[] toWindow(Minecraft mc, double cx, double cy) {
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		int k = CardLayout.scale(w, h);
		check(k >= 1, "the full card canvas fits " + w + "x" + h);
		int ox = (int) Math.floor((w - CardLayout.CANVAS_W * k) / 2f);
		int oy = (int) Math.floor((h - CardLayout.CANVAS_H * k) / 2f);
		double s = mc.getWindow().getGuiScale();
		return new double[] {(ox + cx * k) * s, (oy + cy * k) * s};
	}

	private static double @Nullable [] buttonCenter(Minecraft mc, String key) {
		if (!(mc.gui.screen() instanceof UthScreen screen)) return null;
		for (GuiEventListener c : screen.children()) {
			if (c instanceof CardButton b && b.visible && b.active && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
				return toWindow(mc, b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0);
			}
		}
		return null;
	}

	private static void clickButton(ClientGameTestContext context, String key) {
		context.waitFor(mc -> buttonCenter(mc, key) != null, TIMEOUT);
		double[] at = context.computeOnClient(mc -> buttonCenter(mc, key));
		click(context, at);
	}

	/** The chip rack as drawn: chips 1, 5, 25, 100, 500 at canvas (8 + 25 i, 211), 22 × 22. */
	private static void clickChip(ClientGameTestContext context, int i) {
		click(context, context.computeOnClient(mc -> toWindow(mc, 8 + 25 * i + 11, 211 + 11)));
	}

	private static void click(ClientGameTestContext context, double[] at) {
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(0);
		context.waitTicks(2);
	}

	// ---- state ------------------------------------------------------------------------------------------------------

	private static long pendingAnte(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof UthScreen s ? s.pendingForTests()[0] : -1L);
	}

	private static boolean errorShown(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof UthScreen s && s.errorForTests() != null);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static UthTableBlockEntity table(MinecraftServer server, BlockPos pos) {
		return (UthTableBlockEntity) server.overworld().getBlockEntity(pos);
	}

	private static String phase(MinecraftServer server, BlockPos pos) {
		return table(server, pos).writeClientState(player(server)).getStringOr("phase", "");
	}

	private static UthRound.@Nullable Seat mySeat(MinecraftServer server, BlockPos pos) {
		UthRound r = table(server, pos).round();
		return r == null ? null : r.seat(player(server).getUUID());
	}

	private static long[] dealt(TestSingleplayerContext world, BlockPos pos) {
		return world.getServer().computeOnServer(s -> {
			UthRound.Seat seat = mySeat(s, pos);
			return seat == null ? new long[] {-1, -1} : new long[] {seat.ante, seat.trips};
		});
	}

	private static long balance(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(s -> Economies.get().balance(player(s)));
	}

	private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world, String what,
			java.util.function.Predicate<MinecraftServer> cond) {
		for (int waited = 0; waited < TIMEOUT; waited += 2) {
			if (world.getServer().computeOnServer(cond::test)) return;
			context.waitTicks(2);
		}
		throw new AssertionError("timed out waiting for: " + what);
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError("UTH real flow: " + message);
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}
}
