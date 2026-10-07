package dev.nezo.burmaldaholic.gametest.v2.poker;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.client.PokerScreen;
import dev.nezo.burmaldaholic.games.poker.present.PokerPub;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.client.UthScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane B — REAL-FLOW client GameTests of the v2 Hold'em and UTH screens (docs/architecture/ui-v2.md §11.2): the table
 * is placed and opened by USING the block, every step is a click on the widget the player sees (HitMap id → window
 * pixel at 1920 × 1080 GUI 4 = 480 × 270, 2560 × 1440 GUI 4 and 1920 × 1080 GUI 3 = 640 × 360, 1280 × 720 GUI 3 =
 * 427 × 240), and every step asserts the SERVER state.
 * <ul>
 *   <li>Hold'em: pick the stakes → lower the buy-in on the stepper → Buy in (server: seated with that stack) → wait for
 *   the turn → raise with the stepper / call / check by clicking → the hand ends (server: hand over, stack changed or
 *   not, no hole card of another seat in the public tag before the showdown) → Stand up from the TopBar (server: not
 *   seated, chips back).</li>
 *   <li>UTH (the "I could not change my bet" report): Sit down → chips onto the Ante, Trips armed by clicking its spot,
 *   right click takes a chip off, Undo → Deal (server: the confirmed bet equals the draft, balance debited) → Change
 *   bet (server: refunded) → a bigger bet → Deal again → decisions by clicking (Bet ×4 / Check) → settle (server:
 *   balance change = the round's net).</li>
 * </ul>
 * Screenshots {@code v2_poker_*} / {@code v2_uth_*} (EN + RU) land in {@code build/run/clientGameTest/screenshots}.
 * Failures are collected and thrown at the end.
 */
public class PokerV2ClientGameTests implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("B")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			RealFlow f = new RealFlow(context, world);
			world.getServer().runCommand("casino balance set @p 20000");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			step("uth", () -> uth(f));
			step("poker", () -> poker(f));
			step("layouts", () -> layouts(f));
			f.close();
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = true);
		} finally {
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.getLanguageManager().setSelected("en_us");
				mc.options.languageCode = "en_us";
			});
		}
		if (!failures.isEmpty()) throw new AssertionError("lane B real flow: " + String.join("; ", failures));
	}

	private void step(String name, Runnable r) {
		try {
			r.run();
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": " + e.getMessage());
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}

	private static long balance(RealFlow f) {
		return f.server(server -> Economies.get().balance(RealFlow.player(server)));
	}

	// ---- Hold'em ------------------------------------------------------------------------------------------------------

	private static CompoundTag pokerState(RealFlow f, BlockPos pos) {
		return f.server(server -> server.overworld().getBlockEntity(pos) instanceof PokerTableBlockEntity t ? t.writeClientState(RealFlow.player(server))
			: new CompoundTag());
	}

	private void poker(RealFlow f) {
		f.size(1920, 1080, 4);
		BlockPos pos = f.place(PokerModule.TABLE.block(), 0);
		f.open(pos, PokerScreen.class);
		f.shot("v2_poker_en_480_join");
		// the primary says why it is disabled until the stakes are picked
		if (f.box("action.~pick:micro").isPresent()) {
			f.click("primary");
			check(!f.status().isEmpty(), "a click on the disabled Buy in shows a reason");
			f.click("action.~pick:micro");
		}
		check(f.enabledTarget("primary"), "Buy in enabled after picking the stakes");
		long before = buyInShown(f);
		f.click(PokerScreen.STEP_MINUS);
		long after = buyInShown(f);
		check(after < before, "the stepper lowered the buy-in: " + before + " → " + after);
		f.shot("v2_poker_en_480_buyin");
		long bal0 = balance(f);
		f.click("primary");
		f.waitServer(pos, PokerTableBlockEntity.class, t -> pokerState(f, pos).getBooleanOr("seated", false), "seated after Buy in");
		long bal1 = balance(f);
		check(bal0 - bal1 == after, "the buy-in left the balance: " + (bal0 - bal1) + " vs " + after);
		// play one hand by clicking: raise on the stepper the first time, then call / check
		boolean raised = false;
		int turns = 0;
		boolean shotTurn = false;
		for (int i = 0; i < 400; i++) {
			CompoundTag st = pokerState(f, pos);
			String leak = f.server(server -> server.overworld().getBlockEntity(pos) instanceof PokerTableBlockEntity t ? leak(t.buildPub()) : null);
			if (leak != null) failures.add("poker: " + leak);
			if (turns > 0 && !st.getBooleanOr("live", false) && !st.getListOrEmpty("result").isEmpty()) break;
			boolean myTurn = f.client(mc -> mc.gui.screen() instanceof PokerScreen s && s.isMyTurn());
			if (!myTurn) {
				f.context().waitTicks(5);
				continue;
			}
			turns++;
			int seq = st.getCompoundOrEmpty("legal").getIntOr("seq", -1);
			if (!shotTurn) {
				f.context().waitTicks(10);
				f.shot("v2_poker_en_480_turn");
				shotTurn = true;
			}
			if (!raised && f.box("action.~stepper").isPresent()) {
				f.click(PokerScreen.STEP_PLUS);
				f.click("primary");
				raised = true;
			} else if (f.enabledTarget("action.call")) {
				f.click("action.call");
			} else {
				f.click("primary");
			}
			final int s0 = seq;
			f.waitServer(pos, PokerTableBlockEntity.class, t -> pokerState(f, pos).getCompoundOrEmpty("legal").getIntOr("seq", -1) != s0, "the action was taken",
				200);
		}
		check(turns > 0, "the viewer's turn never came");
		f.context().waitTicks(20);
		f.shot("v2_poker_en_480_result");
		// the hand log names our action
		f.size(2560, 1440, 4);
		f.shot("v2_poker_en_640_after");
		f.size(1920, 1080, 3);
		f.shot("v2_poker_en_640s3_after");
		f.language("ru_ru");
		f.size(1920, 1080, 4);
		f.shot("v2_poker_ru_480_after");
		f.language("en_us");
		// Stand up from the TopBar: chips back to the balance after the hand
		f.click("top.leave");
		f.waitServer(pos, PokerTableBlockEntity.class, t -> !pokerState(f, pos).getBooleanOr("seated", true) || pokerState(f, pos).getBooleanOr("leaving", false),
			"standing up");
	}

	/** The amount on the open screen's stepper. */
	private static long buyInShown(RealFlow f) {
		return f.client(mc -> {
			if (!(mc.gui.screen() instanceof PokerScreen s) || s.currentView() == null) return -1L;
			return dev.nezo.burmaldaholic.games.poker.present.PokerFlow.stepper(s.currentView()).map(x -> x.value()).orElse(-1L);
		});
	}

	/** A hole card in the public tag before the showdown would be a leak (only exposed / shown cards may be there). */
	private static String leak(PokerPub p) {
		if (!PokerPub.kindName(p.kind()).equals("finish")) {
			for (int s = 0; s < p.seatCount(); s++) {
				if (p.card(s, 0) >= 0 && !p.has(s, PokerPub.ALL_IN)) return "seat " + s + " hole card public outside the showdown";
			}
		}
		return null;
	}

	// ---- UTH ------------------------------------------------------------------------------------------------------------

	private static long[] uthConfirmed(RealFlow f, BlockPos pos) {
		return f.server(server -> server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity t
			? t.confirmedBet(RealFlow.player(server).getUUID()).orElse(null) : null);
	}

	private static String uthPhase(RealFlow f, BlockPos pos) {
		return f.server(server -> server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity t ? t.writeClientState(RealFlow.player(server))
			.getStringOr("phase", "") : "");
	}

	private void uth(RealFlow f) {
		f.size(1920, 1080, 4);
		BlockPos pos = f.place(UthModule.TABLE.block(), 3);
		f.open(pos, UthScreen.class);
		f.shot("v2_uth_en_480_open");
		// a chip before sitting down says why
		if (f.box("chip.0").isPresent()) {
			f.click("chip.0");
			check(!f.status().isEmpty(), "a chip click while standing shows a reason");
		}
		f.click("primary"); // Sit down
		f.waitServer(pos, UthTableBlockEntity.class, t -> t.seatedHumans().size() == 1, "seated after Sit down");
		// a second (absent) player keeps the betting phase open, so the bet can be changed after Deal
		UUID other = UUID.randomUUID();
		f.server(server -> server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity t && t.seats().sit(other, "Alex").isPresent());
		f.context().waitTicks(4);
		f.click("chip.1");
		f.click("chip.1");
		long ante = draft(f)[0];
		check(ante > 0, "chips went to the Ante: " + ante);
		f.click("felt.trips"); // arms Trips and adds the selected chip
		long[] d = draft(f);
		check(d[1] > 0, "the Trips spot took a chip: " + d[1]);
		f.rightClick("felt.trips");
		long afterRight = draft(f)[1];
		check(afterRight < d[1], "right click took a chip off the Trips");
		f.click("felt.trips");
		check(draft(f)[1] == d[1], "a click on the spot put it back");
		f.click("tool.undo");
		check(draft(f)[1] == afterRight, "Undo took the last chip back: " + draft(f)[1]);
		f.click("felt.ante");
		d = draft(f);
		f.shot("v2_uth_en_480_bet");
		long bal0 = balance(f);
		f.click("primary"); // Deal · cost
		f.waitServer(pos, UthTableBlockEntity.class, t -> uthConfirmed(f, pos) != null, "the bet was confirmed");
		long[] conf = uthConfirmed(f, pos);
		check(conf[0] == d[0] && conf[1] == d[1], "server bet = draft: " + conf[0] + "/" + conf[1] + " vs " + d[0] + "/" + d[1]);
		check(bal0 - balance(f) == 2 * d[0] + d[1], "debited Ante + Blind + Trips");
		f.context().waitTicks(4);
		f.shot("v2_uth_en_480_confirmed");
		// CHANGE the bet: the primary says so; a click refunds and keeps the amounts in the draft
		f.click("primary");
		f.waitServer(pos, UthTableBlockEntity.class, t -> uthConfirmed(f, pos) == null, "Change bet refunded the bet");
		check(balance(f) == bal0, "refund after Change bet");
		f.click("chip.0"); // +1: 6 × Ante + Trips stays under the VIP-0 maximum
		long[] bigger = draft(f);
		check(bigger[0] > d[0], "the changed bet is bigger: " + bigger[0]);
		f.click("primary");
		f.waitServer(pos, UthTableBlockEntity.class, t -> uthConfirmed(f, pos) != null && uthConfirmed(f, pos)[0] == bigger[0], "the changed bet was confirmed");
		// the absent player leaves: the round starts with the next confirm / the bet timer
		f.server(server -> server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity t && t.seats().leave(other).isPresent());
		long balBet = balance(f);
		f.waitServer(pos, UthTableBlockEntity.class, t -> !uthPhase(f, pos).equals("betting"), "the round started", 1200);
		boolean decided = false;
		for (int i = 0; i < 600; i++) {
			String phase = uthPhase(f, pos);
			if (decided && (phase.equals("betting") || phase.equals("idle"))) break;
			boolean legal = f.client(mc -> mc.gui.screen() instanceof UthScreen s && s.currentView() != null && s.currentView().deciding());
			if (legal && f.enabledTarget("primary")) {
				if (!decided) {
					f.context().waitTicks(6);
					f.shot("v2_uth_en_480_decision");
				}
				f.click("primary");
				decided = true;
				f.context().waitTicks(6);
				continue;
			}
			if (decided && i % 40 == 0) f.shot("v2_uth_en_480_play_" + i);
			f.context().waitTicks(5);
		}
		check(decided, "a decision was clicked");
		long net = f.server(server -> server.overworld().getBlockEntity(pos) instanceof UthTableBlockEntity t
			? t.writeClientState(RealFlow.player(server)).getCompoundOrEmpty("result").getLongOr("net", Long.MIN_VALUE) : Long.MIN_VALUE);
		f.context().waitTicks(10);
		f.shot("v2_uth_en_480_result");
		check(net != Long.MIN_VALUE, "the server reported the round's result");
		long change = balance(f) - balBet;
		check(change == net + 2 * bigger[0] + bigger[1], "balance after the round = the Ante + Blind + Trips back + the net: " + change + " vs net " + net);
		f.size(2560, 1440, 4);
		f.shot("v2_uth_en_640_result");
		f.language("ru_ru");
		f.size(1920, 1080, 4);
		f.shot("v2_uth_ru_480_result");
		f.language("en_us");
		f.close();
	}

	/** The open UTH screen's draft {ante, trips}. */
	private static long[] draft(RealFlow f) {
		return f.client(mc -> mc.gui.screen() instanceof UthScreen s ? new long[] {s.draft().ante(), s.draft().trips()} : new long[] {-1, -1});
	}

	// ---- the layouts at the other real sizes -------------------------------------------------------------------------

	private void layouts(RealFlow f) {
		BlockPos uth = f.place(UthModule.TABLE.block(), 3);
		for (int[] sz : new int[][] {{2560, 1440, 4}, {1920, 1080, 3}, {1280, 720, 3}}) {
			f.size(sz[0], sz[1], sz[2]);
			f.open(uth, UthScreen.class);
			String tag = sz[0] + "x" + sz[1] + "s" + sz[2];
			f.shot("v2_uth_en_" + tag);
			check(f.box("primary").isPresent() && f.box("felt.ante").isPresent(), tag + ": primary and the Ante spot are clickable");
			f.close();
		}
		f.size(1920, 1080, 4);
	}
}
