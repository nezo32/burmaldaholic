package dev.nezo.burmaldaholic.gametest.v2.uth;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.client.ui.v2.V2Input;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.client.UthScreen;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * TESTER (T-B2) probes, not product code. UTH solo at 480 x 270:
 * <ol>
 * <li>Tab focus + Enter: focus the Deal primary with Tab, press Enter, keep tapping Enter (impatient / auto-repeat) while
 * the decision Dock opens: does the focused index land on a decision and act (bypassing the ClickGuard)?</li>
 * <li>"Click again": a refused click flashes "The buttons moved, click again"; does the Dock re-lay out around that flash
 * (status width) so the same pixel now hits another control?</li>
 * <li>GUI-scale change at a decision, then a click at the pixel aimed before the change in the same tick.</li>
 * </ol>
 * Facts are logged with the prefix {@code TB2:}; money effects fail the test at the end.
 *
 * <p>Cherry-picked into the kit (p2/k2) from the T-B2 tester's branch for M1 / m1: runs in lane K. The B2-only server
 * part of probe 2 (a stale-epoch Bet ×4 refused by the UTH server) is replaced by the kit's own transient lines (a server
 * error and the watchdog line shown on the decision Dock), which must not move it either; the M1 click at the old Check
 * edge is always made and must not debit.
 */
public class TesterB2ClientGameTests implements FabricClientGameTest {
	private static final int TIMEOUT = 600;
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("K") && !RealFlow.enabled("TB2")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet q = ClientTestWorlds.quiet(context, world)) {
			RealFlow f = new RealFlow(context, world);
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runOnServer(s -> CasinoConfig.chaos().enabled = false);
			world.getServer().runCommand("casino balance set @p 5000");
			f.size(1920, 1080, 4);
			BlockPos pos = f.place(UthModule.TABLE.block(), 0);
			f.open(pos, UthScreen.class);
			waitTarget(f, "primary");
			settledClick(f, "primary"); // sit
			f.waitServer(pos, UthTableBlockEntity.class, t -> t.seatedHumans().size() == 1, "seated");
			context.waitTicks(5);

			// ---- 1. Tab + Enter ---------------------------------------------------------------------------------------
			settledClick(f, chip(f, 5));
			settledClick(f, chip(f, 5));
			context.waitTicks(5);
			long before = balance(world);
			int tabs = 0;
			while (!focused(f).equals("primary") && tabs < 80) {
				f.key(KeyMap.TAB);
				tabs++;
			}
			log("tab: " + tabs + " tabs to focus '" + focused(f) + "' (gate " + primaryAction(f) + ")");
			if (focused(f).equals("primary")) {
				List<String> seen = new ArrayList<>();
				for (int i = 0; i < 30; i++) {
					String fid = focused(f);
					String act = primaryAction(f);
					context.getInput().pressKey(V2Input.keyCode(KeyMap.ENTER));
					context.waitTicks(2);
					UthRound.Seat s = mySeat(world, pos);
					seen.add(i + ":" + fid + "/" + act + "->" + lastPressed(f) + (s == null ? "" : " play=" + s.playMultiple + (s.folded ? " folded" : "")));
					if (s != null && (s.playMultiple > 0 || s.folded)) {
						fail("Enter on the Tab-focused Deal kept acting after the decision Dock opened: " + (s.folded ? "folded" : "Bet x" + s.playMultiple)
							+ " placed (balance " + before + " -> " + balance(world) + "); presses " + seen);
						break;
					}
				}
				log("tab+enter presses: " + seen);
			} else {
				log("tab: never focused the primary; skipped");
				settledClick(f, "primary");
			}
			playOut(f, world, pos, "r1");

			// ---- 2. "click again" after a refusal -------------------------------------------------------------------
			waitBetting(f);
			settledClick(f, "primary"); // Deal (rebet amounts kept in the draft)
			waitDecision(f, world, pos);
			watchDock(f, world, pos, "480", 120);
			String dock0 = dock(f);
			// the right edge of Check as the player saw it (3 px inside)
			dev.nezo.burmaldaholic.core.ui.v2.Box cb = f.box("action.check").orElseThrow();
			dev.nezo.burmaldaholic.core.ui.v2.Viewport vp0 = f.viewport();
			double[] checkPx = {vp0.windowX(cb.x() + cb.w() - 3), vp0.windowY(cb.y() + cb.h() / 2.0)};
			long beforeAgain = balance(world);
			// the screen's own refusal flash ("buttons moved, click again"), then a server error and the watchdog line: the
			// next relayout (UthScreen refreshes every 10 ticks) must leave every control where it was (T-B2 M1)
			String[][] transients = {{"flash", V2Keys.REASON_MOVED}, {"error", "gui.burmaldaholic.error.not_your_turn"}, {"watchdog", V2Keys.REASON_NO_ANSWER}};
			for (String[] tr : transients) {
				context.runOnClient(mc -> {
					if (mc.gui.screen() instanceof V2Chrome.Holder h) {
						Component c = Component.translatable(tr[1]);
						switch (tr[0]) {
							case "flash" -> h.chrome().status.flash(c);
							case "error" -> h.chrome().error(c);
							default -> h.chrome().status.watchdog(c);
						}
						h.chrome().relayout();
					}
				});
				context.waitTicks(12); // past UthScreen's own 10-tick refresh, with the line still showing
				String dock1 = dock(f);
				log(tr[0] + " relayout: status '" + f.status() + "'; before " + dock0 + " after " + dock1);
				if (!dock0.equals(dock1)) fail("the " + tr[0] + " line re-lays the decision Dock out: " + dock0 + " -> " + dock1);
			}
			// the M1 repro: a refusal flash, then 400 ms later the player clicks again where Check was (its right edge)
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof V2Chrome.Holder h) {
					h.chrome().status.flash(Component.translatable(V2Keys.REASON_MOVED));
					h.chrome().relayout();
				}
			});
			context.waitTicks(8); // > SETTLE_MS
			context.getInput().setCursorPos(checkPx[0], checkPx[1]);
			context.waitTick();
			context.getInput().pressMouse(Inputs.MOUSE_LEFT);
			context.waitTicks(10);
			UthRound.Seat sa = mySeat(world, pos);
			log("click again at the old Check right edge pressed '" + lastPressed(f) + "' play=" + (sa == null ? -1 : sa.playMultiple)
				+ " balance " + beforeAgain + " -> " + balance(world));
			if (!"action.check".equals(lastPressed(f))) fail("click again at the old Check edge pressed '" + lastPressed(f) + "', not Check");
			if (sa != null && (sa.playMultiple > 0 || sa.folded)) fail("click again (400 ms after the refusal flash) at the right edge of Check placed Bet x"
				+ sa.playMultiple + (sa.folded ? " / folded" : "") + ", debit " + (beforeAgain - balance(world)));
			if (balance(world) != beforeAgain) fail("click again at Check changed the balance " + beforeAgain + " -> " + balance(world));
			playOut(f, world, pos, "r2");

			// ---- 3. GUI scale change, then a click at the pre-change pixel in the same tick -------------------------
			waitBetting(f);
			settledClick(f, "primary");
			waitDecision(f, world, pos);
			context.waitTicks(25);
			double[] px = f.windowPoint("action.check");
			String dockS0 = dock(f);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(3);
				mc.resizeGui();
			});
			context.getInput().setCursorPos(px[0], px[1]);
			context.getInput().pressMouse(Inputs.MOUSE_LEFT);
			context.waitTicks(3);
			UthRound.Seat s3 = mySeat(world, pos);
			log("resize: " + dockS0 + " -> " + dock(f) + "; stale-pixel click pressed '" + lastPressed(f) + "' play=" + (s3 == null ? -1 : s3.playMultiple)
				+ " decided=" + (s3 != null && s3.decided) + " status '" + f.status() + "'");
			if (s3 != null && (s3.playMultiple > 0 || s3.folded)) fail("a click at the pre-resize Check pixel placed a bet / fold");
			playOut(f, world, pos, "r3");

			// ---- 4. 427 x 240: does the decision Dock move by itself while the timer counts down? -------------------
			f.size(1280, 720, 3);
			waitBetting(f);
			settledClick(f, "primary");
			waitDecision(f, world, pos);
			watchDock(f, world, pos, "427", 600);
			playOut(f, world, pos, "r4");

			f.close();
			context.waitTicks(20);
		} finally {
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
		if (!failures.isEmpty()) throw new AssertionError("T-B2 probes: " + String.join(" || ", failures));
	}

	private void fail(String m) {
		failures.add(m);
		log("FAIL " + m);
	}

	private static void log(String m) {
		System.out.println("TB2: " + m);
	}

	// ---- flow -------------------------------------------------------------------------------------------------------

	private static void settledClick(RealFlow f, String id) {
		f.context().waitFor(mc -> !(mc.gui.screen() instanceof V2Chrome.Holder h) || h.chrome().clickSettled(), TIMEOUT);
		f.click(id);
	}

	private static void waitTarget(RealFlow f, String id) {
		f.context().waitFor(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
			&& h.chrome().hits().targets().stream().anyMatch(t -> t.id().equals(id) && t.enabled()), TIMEOUT);
	}

	private static void waitBetting(RealFlow f) {
		f.context().waitFor(mc -> mc.gui.screen() instanceof UthScreen s && s.currentView() != null && s.currentView().betting()
			&& !s.currentView().deciding(), TIMEOUT * 2);
		f.context().waitTicks(10);
		waitTarget(f, "primary");
	}

	private static void waitDecision(RealFlow f, TestSingleplayerContext world, BlockPos pos) {
		f.context().waitFor(mc -> mc.gui.screen() instanceof UthScreen s && s.currentView() != null && s.currentView().deciding()
			&& "bet_4x".equals(s.chrome().primary.gate().action()), TIMEOUT);
		waitTarget(f, "action.check");
	}

	/** Checks / plays x1 / folds through until the round is over (settled clicks; a misclick is only logged here). */
	private void playOut(RealFlow f, TestSingleplayerContext world, BlockPos pos, String name) {
		ClientGameTestContext c = f.context();
		for (int w = 0; w < 3000; w += 5) {
			boolean over = world.getServer().computeOnServer(s -> {
				UthTableBlockEntity t = (UthTableBlockEntity) s.overworld().getBlockEntity(pos);
				return t.round() == null;
			});
			if (over) return;
			UthRound.Seat seat = mySeat(world, pos);
			boolean deciding = c.computeOnClient(mc -> mc.gui.screen() instanceof UthScreen s && s.currentView() != null && s.currentView().deciding());
			if (deciding && seat != null && !seat.decided) {
				String id = f.enabledTarget("action.check") ? "action.check" : "primary";
				try {
					settledClick(f, id);
				} catch (AssertionError e) {
					log(name + ": playOut click: " + e.getMessage());
				}
				c.waitTicks(10);
			} else {
				c.waitTicks(5);
			}
		}
		throw new AssertionError(name + ": round never ended");
	}

	/** Samples the decision Dock every 2 ticks (no input) until the decision closes or {@code ticks} pass; any change is a defect. */
	private void watchDock(RealFlow f, TestSingleplayerContext world, BlockPos pos, String label, int ticks) {
		String first = dock(f);
		String last = first;
		int changes = 0;
		for (int t = 0; t < ticks; t += 2) {
			UthRound.Seat s = mySeat(world, pos);
			if (s == null || s.decided) break;
			f.context().waitTicks(2);
			String now = dock(f);
			if (!ids(now).equals(ids(last))) break; // the street changed (new controls): a legitimate relayout
			if (!now.equals(last)) {
				changes++;
				log("dock " + label + " moved with no input at t+" + t + " (status '" + f.status() + "' / '" + line2(f) + "'): " + last + " -> " + now);
				last = now;
			}
		}
		log("dock " + label + ": " + changes + " changes while deciding; " + first);
		if (changes > 0) fail("the decision Dock at " + label + " moved " + changes + " times with no input (timer / status width)");
	}

	private static String ids(String dock) {
		return dock.replaceAll("@Box\\[[^\\]]*\\]", "");
	}

	private static String line2(RealFlow f) {
		return f.client(mc -> {
			if (!(mc.gui.screen() instanceof V2Chrome.Holder h)) return "";
			var c = h.chrome().status.line2();
			return c == null ? "" : c.getString();
		});
	}

	private static String focused(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h ? h.chrome().focusedId() : "-");
	}

	private static String lastPressed(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h ? h.chrome().lastPressed() : "-");
	}

	private static String primaryAction(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h ? h.chrome().primary.gate().action() : "-");
	}

	private static String dock(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h
			? h.chrome().hits().targets().stream().filter(t -> t.id().startsWith("action.") || t.id().equals("primary"))
				.map(t -> t.id() + "@" + t.box()).collect(Collectors.joining(",")) : "-");
	}

	private static String chip(RealFlow f, long value) {
		int i = f.client(mc -> {
			if (!(mc.gui.screen() instanceof V2Chrome.Holder h)) return -1;
			long[] chips = h.chrome().bet.chips();
			for (int k = 0; k < chips.length; k++) if (chips[k] == value) return k;
			return -1;
		});
		if (i < 0) throw new AssertionError("no " + value + " chip");
		return "chip." + i;
	}

	private static UthRound.@Nullable Seat mySeat(TestSingleplayerContext world, BlockPos pos) {
		return world.getServer().computeOnServer(s -> {
			UthRound r = ((UthTableBlockEntity) s.overworld().getBlockEntity(pos)).round();
			return r == null ? null : r.seat(s.getPlayerList().getPlayers().getFirst().getUUID());
		});
	}

	private static long balance(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(s -> Economies.get().balance(s.getPlayerList().getPlayers().getFirst()));
	}
}
