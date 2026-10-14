package dev.nezo.burmaldaholic.gametest.uth;

import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.client.UthScreen;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * v0.1.2 hotfix, REAL player flow for "I cannot change the UTH bet", re-pointed to the table UI v2 screen (HitMap ids
 * {@code primary}, {@code chip.N}, {@code tool.*}, {@code felt.trips}, {@code action.*}): a 1920 x 1080 window at GUI
 * scale 4 (480 x 270) and 3; the player looks at an Ultimate Texas Hold'em table and presses Use, clicks Sit down, builds
 * the Ante with real clicks on the tray chips, presses Deal and plays the round to its payout by clicking the decision
 * buttons. Asserts the SERVER state after every step: the dealt Ante / Trips are exactly the draft that was built, the
 * balance moves by the round's return. Then changes the bet for the next round (R-AB M5: a chip that takes the Ante over
 * the table limit is clamped to the largest allowed Ante and the status says why, Deal stays live, the chips grey at the
 * cap; Undo takes it back, a chip on the Trips spot), confirms it while a second seat still bets (R-AB M4: a chip click
 * on the confirmed bet changes nothing on the server and says "press Change bet"; Change bet refunds and keeps the
 * amounts), deals, then Clear + Rebet. Screenshots {@code uth_flow_*}.
 */
public class UthRealFlowClientGameTests implements FabricClientGameTest {
	private static final int TIMEOUT = 600;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("B") && !RealFlow.enabled("hotfix")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
				ClientTestWorlds.Quiet q = ClientTestWorlds.quiet(context, world)) {
			RealFlow f = new RealFlow(context, world);
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("casino balance set @p 1000");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			f.size(1920, 1080, 4);
			String gui = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
			check(gui.equals("480x270"), "GUI scale 4 at 1920 x 1080 is 480 x 270, got " + gui);

			BlockPos pos = placeTable(world);
			openByUse(context, world, pos);
			context.takeScreenshot("uth_flow_s4_opened");

			// Sit down (a real click on the primary button)
			waitTarget(f, "primary");
			f.click("primary");
			waitServer(context, world, "seated", s -> table(s, pos).seats().seatOf(player(s).getUUID()).isPresent());
			context.waitTicks(5);
			context.takeScreenshot("uth_flow_s4_seated");

			// Round 1: an Ante of 10 (two 5 chips), then Deal
			f.click(chip(f, 5));
			f.click(chip(f, 5));
			context.waitTicks(3);
			check(draft(f)[0] == 10 && draft(f)[1] == 0, "two 5 chips make an Ante of 10, draft " + draft(f)[0] + "/" + draft(f)[1]);
			context.takeScreenshot("uth_flow_s4_ante10");
			long before = balance(world);
			f.click("primary");
			waitServer(context, world, "round 1 dealt", s -> mySeat(s, pos) != null);
			long[] dealt = dealt(world, pos);
			check(dealt[0] == 10 && dealt[1] == 0, "round 1: dealt Ante/Trips " + dealt[0] + "/" + dealt[1] + ", clicked 10/0");
			check(balance(world) == before - 20, "round 1: Ante + Blind debited (" + before + " -> " + balance(world) + ")");
			playToPayout(f, world, pos, before, "round1");

			// Round 2: the bet is changed. The 25 chip would take the Ante over the bronze limit (6 x Ante <= 100): it is clamped
			// to the largest allowed Ante (16) and the status says why (R-AB M5, the v0.1.2 hotfix behaviour); Deal stays live.
			// Undo takes it back (10); +5 +1 -> 16; a 1 chip on the Trips spot.
			context.waitTicks(5);
			context.takeScreenshot("uth_flow_s4_round2_betting");
			check(draft(f)[0] == 10, "round 2 starts from the last Ante (10), draft " + draft(f)[0]);
			long beforeLimit = balance(world);
			f.click(chip(f, 25));
			context.waitTicks(2);
			check(draft(f)[0] == 16, "the 25 chip clamps the Ante to the largest allowed (16), draft " + draft(f)[0]);
			check(f.status().contains("16"), "the clamp says why (max Ante 16): '" + f.status() + "'");
			check(f.enabledTarget("primary"), "Deal stays live with the clamped Ante");
			check(!f.enabledTarget(chip(f, 1)), "the chips grey out at the cap");
			check(balance(world) == beforeLimit, "nothing was debited by the clamp");
			context.takeScreenshot("uth_flow_s4_limit_clamped");
			f.click("tool.undo");
			check(draft(f)[0] == 10, "Undo takes the clamped chip back, draft " + draft(f)[0]);
			f.click(chip(f, 5));
			f.click(chip(f, 1));
			f.click("felt.trips"); // the Trips spot takes the selected chip (1)
			context.waitTicks(3);
			long[] d2 = draft(f);
			check(d2[0] == 16 && d2[1] == 1, "Ante 16 / Trips 1 built, draft " + d2[0] + "/" + d2[1]);
			context.takeScreenshot("uth_flow_s4_ante16_trips1");

			// R-AB M4: a second player (online, next to the table, never betting) keeps bets open, so the confirmed bet waits
			// for the deal. An absent UUID would be unseated by the table's online sweep within a second.
			UUID other = world.getServer().computeOnServer(s -> {
				ServerPlayer p = secondPlayer(s, pos);
				table(s, pos).seats().sit(p.getUUID(), "Alex");
				return p.getUUID();
			});
			context.waitTicks(4);
			check(world.getServer().computeOnServer(s -> table(s, pos).seatedHumans().size() == 2), "the second player is seated");
			before = balance(world);
			f.click("primary");
			waitServer(context, world, "round 2 bet confirmed", s -> table(s, pos).confirmedBet(player(s).getUUID()).isPresent());
			long[] conf = world.getServer().computeOnServer(s -> table(s, pos).confirmedBet(player(s).getUUID()).orElseThrow());
			check(conf[0] == 16 && conf[1] == 1, "the server holds 16/1, got " + conf[0] + "/" + conf[1]);
			context.waitTicks(4);
			f.click(chip(f, 5)); // locked: changes nothing, says "press Change bet"
			context.waitTicks(4);
			long[] still = world.getServer().computeOnServer(s -> table(s, pos).confirmedBet(player(s).getUUID()).orElse(new long[] {-1, -1}));
			check(still[0] == 16 && still[1] == 1, "a chip click on a confirmed bet leaves the server bet alone, got " + still[0] + "/" + still[1]);
			check(balance(world) == before - 33, "still debited once (Ante + Blind + Trips = 33): " + (before - balance(world)));
			check(draft(f)[0] == 16 && draft(f)[1] == 1, "the draft is unchanged, draft " + draft(f)[0] + "/" + draft(f)[1]);
			check(!f.status().isEmpty(), "the locked chip says why: '" + f.status() + "'");
			context.takeScreenshot("uth_flow_s4_confirmed_locked");
			f.click("primary"); // Change bet: refund, the amounts stay in the draft
			waitServer(context, world, "Change bet refunded", s -> table(s, pos).confirmedBet(player(s).getUUID()).isEmpty());
			check(balance(world) == before, "Change bet refunded the bet");
			context.waitTicks(4);
			check(draft(f)[0] == 16 && draft(f)[1] == 1, "Change bet keeps 16/1 in the draft, draft " + draft(f)[0] + "/" + draft(f)[1]);
			f.click("primary"); // Deal again
			waitServer(context, world, "round 2 bet re-confirmed", s -> table(s, pos).confirmedBet(player(s).getUUID()).isPresent());
			world.getServer().runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayer(other);
				if (p != null) s.getPlayerList().remove(p); // leaves the table (disconnect: the bet timer deals our round)
			});
			waitServer(context, world, "round 2 dealt", s -> mySeat(s, pos) != null && mySeat(s, pos).ante == d2[0]);
			dealt = dealt(world, pos);
			check(dealt[0] == d2[0] && dealt[1] == d2[1], "round 2: dealt Ante/Trips " + dealt[0] + "/" + dealt[1] + ", built " + d2[0] + "/" + d2[1]);
			playToPayout(f, world, pos, before, "round2");

			// Round 3 at GUI scale 3: Clear, then Rebet restores 16 / 1; Deal
			f.size(1920, 1080, 3);
			context.waitTicks(5);
			waitTarget(f, "tool.clear");
			f.click("tool.clear");
			check(draft(f)[0] == 0 && draft(f)[1] == 0, "Clear empties the draft, draft " + draft(f)[0]);
			f.click("tool.rebet");
			context.waitTicks(3);
			check(draft(f)[0] == d2[0] && draft(f)[1] == d2[1], "Rebet restores the last bet, draft " + draft(f)[0] + "/" + draft(f)[1]);
			context.takeScreenshot("uth_flow_s3_rebet");
			before = balance(world);
			f.click("primary");
			waitServer(context, world, "round 3 dealt", s -> mySeat(s, pos) != null && mySeat(s, pos).ante == d2[0]);
			dealt = dealt(world, pos);
			check(dealt[0] == d2[0] && dealt[1] == d2[1], "round 3 (rebet): dealt Ante/Trips " + dealt[0] + "/" + dealt[1]);
			playToPayout(f, world, pos, before, "round3");

			// 1280 x 720 at GUI scale 3 (427 x 240, tight): the bet bar is still there and clickable
			f.size(1280, 720, 3);
			context.waitTicks(5);
			context.takeScreenshot("uth_flow_720p_s3_betting");
			check(f.box("primary").isPresent() && f.box("tool.undo").isPresent(), "427 x 240: the primary and the bet tools are on screen");

			f.close();
			context.waitTicks(20);
			check(MISCLICKS.isEmpty(), "decision clicks pressed another target: " + MISCLICKS);
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
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
		// aim the camera at the table (as the mouse would), until the client sees it under the crosshair (the server may lag
		// behind under load; a stray mouse delta may turn the camera, so the aim is held)
		net.minecraft.world.phys.Vec3 target = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5);
		try {
			context.waitFor(mc -> {
				if (mc.player == null || mc.level == null) return false;
				mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
				mc.player.xRotO = mc.player.getXRot();
				mc.player.yRotO = mc.player.getYRot();
				return mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
					&& hit.getBlockPos().equals(pos) && mc.level.getBlockEntity(pos) instanceof UthTableBlockEntity;
			}, TIMEOUT);
		} catch (AssertionError e) {
			context.takeScreenshot("uth_flow_fail_not_aimed");
			String seen = context.computeOnClient(mc -> String.valueOf(mc.hitResult == null ? null : mc.hitResult.getLocation()) + " player "
				+ (mc.player == null ? null : mc.player.blockPosition()));
			throw new AssertionError("UTH real flow: the table at " + pos + " is not under the crosshair (" + seen + ")", e);
		}
		context.getInput().pressKey(o -> o.keyUse);
		context.waitFor(mc -> mc.gui.screen() instanceof UthScreen, TIMEOUT);
		context.waitTicks(10);
	}

	/** Clicks Check while it is legal, then Bet x1 at the river, until the round settles; checks the payout. */
	private static void playToPayout(RealFlow f, TestSingleplayerContext world, BlockPos pos, long before, String name) {
		ClientGameTestContext context = f.context();
		boolean shotDecision = false;
		for (int waited = 0; waited < 2400; waited += 5) {
			UthRound.Seat seat = world.getServer().computeOnServer(s -> mySeat(s, pos));
			if (seat != null && seat.result != null && world.getServer().computeOnServer(s -> !UthTableBlockEntity.SHOWDOWN.equals(phase(s, pos)))) {
				// before = balance before Deal; Ante + Blind + Play + Trips were debited, the return paid back: before + net
				long net = seat.result.net();
				long ret = seat.result.totalReturn();
				context.waitTicks(40);
				context.takeScreenshot("uth_flow_" + name + "_payout");
				long after = balance(world);
				check(after == before + net, name + ": balance " + before + " - staked " + seat.result.staked() + " + return " + ret + " = " + (before + net)
					+ ", got " + after);
				context.waitFor(mc -> mc.gui.screen() instanceof UthScreen s && s.currentView() != null && s.currentView().betting()
					&& !s.currentView().deciding(), TIMEOUT);
				context.waitTicks(5);
				return;
			}
			boolean deciding = context.computeOnClient(mc -> mc.gui.screen() instanceof UthScreen s && s.currentView() != null && s.currentView().deciding());
			String id = !deciding ? null : offered(f, "check") ? "check" : offered(f, "bet_1x") ? "bet_1x" : null;
			if (id != null && seat != null && !seat.decided) {
				if (!shotDecision) {
					context.takeScreenshot("uth_flow_" + name + "_decision");
					shotDecision = true;
				}
				String target = f.enabledTarget("action." + id) ? "action." + id : "primary";
				String layout = targets(f);
				try {
					f.click(target);
				} catch (AssertionError e) {
					// a decision click that lands on another target is a money risk: keep the evidence, finish the flow, fail at the end
					context.takeScreenshot("uth_flow_" + name + "_misclick");
					MISCLICKS.add(name + ": " + e.getMessage() + " | before: " + layout + " | after: " + targets(f) + " | vp " + f.viewport());
				}
				context.waitTicks(10);
			} else {
				context.waitTicks(5);
			}
		}
		throw new AssertionError(name + ": the round did not settle");
	}

	private static final java.util.List<io.netty.channel.embedded.EmbeddedChannel> CHANNELS = new java.util.ArrayList<>();

	/** A second server player on an embedded connection, standing next to the table (server thread). */
	private static ServerPlayer secondPlayer(MinecraftServer server, BlockPos pos) {
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Alex"), false);
		ServerPlayer p = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
		net.minecraft.network.Connection c = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		CHANNELS.add(new io.netty.channel.embedded.EmbeddedChannel(c));
		server.getPlayerList().placeNewPlayer(c, p, cookie);
		p.snapTo(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(1.5, 0, 0));
		return p;
	}

	/** Decision clicks that pressed another target (reported at the end of the run). */
	private static final java.util.List<String> MISCLICKS = new java.util.ArrayList<>();

	/** The open screen's targets: id@box (diagnostics). */
	private static String targets(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
			? h.chrome().hits().targets().stream().filter(t -> t.id().startsWith("action.") || t.id().equals("primary") || t.id().startsWith("felt."))
				.map(t -> t.id() + "@" + t.box()).collect(java.util.stream.Collectors.joining(",")) : "-");
	}

	/** Decision {@code id} is offered: as an action button, or as the primary (the flow puts one decision there). */
	private static boolean offered(RealFlow f, String id) {
		if (f.enabledTarget("action." + id)) return true;
		return f.enabledTarget("primary") && f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
			&& id.equals(h.chrome().primary.gate().action()));
	}

	// ---- input ------------------------------------------------------------------------------------------------------

	/** The tray chip of denomination {@code value} ({@code chip.<index>}; the tray offers only what fits the limits). */
	private static String chip(RealFlow f, long value) {
		int i = f.client(mc -> {
			if (!(mc.gui.screen() instanceof V2Chrome.Holder h)) return -1;
			long[] chips = h.chrome().bet.chips();
			for (int k = 0; k < chips.length; k++) if (chips[k] == value) return k;
			return -1;
		});
		check(i >= 0, "the tray offers a " + value + " chip");
		return "chip." + i;
	}

	private static void waitTarget(RealFlow f, String id) {
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
			&& h.chrome().hits().targets().stream().anyMatch(t -> t.id().equals(id) && t.enabled()), TIMEOUT);
	}

	// ---- state ------------------------------------------------------------------------------------------------------

	/** The client's bet draft {Ante, Trips}. */
	private static long[] draft(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof UthScreen s ? new long[] {s.draft().ante(), s.draft().trips()} : new long[] {-1, -1});
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
