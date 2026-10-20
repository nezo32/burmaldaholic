package dev.nezo.burmaldaholic.gametest.pvp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet;
import dev.nezo.burmaldaholic.pvp.net.PvpActionPayload;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Tester (T-E2) GameTests for B1 (owner playtest 1: the duel result window re-opened every ~2 min). Shortened timers
 * (history 100 / rematch 60 / invite 60), restored after. Each test counts every {@code open = true} PvP sync a player
 * receives, per (match id, kind): a view may open at most once, and after the players walked away (opened another
 * screen: poker in the owner's case) nothing opens by itself while the old matches leave the history.
 */
public class TesterE2PvpGameTests {
	private static final String ENV = "burmaldaholic:pvp_e2t";
	private static final String RESTART_ENV = "burmaldaholic:pvp_e2t_restart";
	private static final long STAKE = 100;
	private static final long START = 5000;

	private static int users;
	private static int[] saved;

	private static void shorten() {
		PvpConfig c = CasinoConfig.pvp();
		if (users++ == 0) {
			saved = new int[] {c.historyTicks, c.decisionTimeoutTicks, c.inviteTimeoutTicks};
		}
		c.historyTicks = 100;
		c.decisionTimeoutTicks = 60;
		c.inviteTimeoutTicks = 60;
	}

	private static void restore(boolean[] done) {
		if (done[0]) {
			return;
		}
		done[0] = true;
		if (--users == 0) {
			PvpConfig c = CasinoConfig.pvp();
			c.historyTicks = saved[0];
			c.decisionTimeoutTicks = saved[1];
			c.inviteTimeoutTicks = saved[2];
		}
	}

	// ---- helpers ----------------------------------------------------------------------------------------------

	private static final Consumer<MockClient> NO_PRESS = c -> {
	};
	private static final Consumer<MockClient> EXTRAS_PRESS = c -> c.send(new ExtrasActionPayload("pvp", "press", new CompoundTag()));
	private static final Consumer<MockClient> PVP_PRESS = c -> c.send(new PvpActionPayload("press", c.matchId(), "", 0));

	private static Consumer<MockClient> pressOf(String mode) {
		return switch (mode) {
			case "scratch", "plinko" -> EXTRAS_PRESS;
			case "slots" -> PVP_PRESS;
			default -> NO_PRESS;
		};
	}

	/** One tick of the two screens: answer a decision (Coin), press the mode's advance button while in the match. */
	private static void drive(Consumer<MockClient> press, MockClient... clients) {
		for (MockClient c : clients) {
			c.drain();
			JsonObject s = c.state;
			if (s != null && s.has("decision") && s.get("decision").isJsonObject()) {
				JsonObject d = s.getAsJsonObject("decision");
				c.send(new CoinDuelNet.Action("decide", MockClient.args("decision", d.get("id").getAsString(), "option", 0L, "match",
					d.get("match").getAsString(), "seq", d.get("seq").getAsLong())));
			} else if (c.kind.equals("match") && c.screen != null && c.screen.startsWith("match:")) {
				press.accept(c);
			}
		}
	}

	/** {@code a} challenges {@code b} to {@code mode}; {@code b} clicks the chat [Accept]. Returns the live match. */
	private static PvpMatch challenge(GameTestHelper helper, String mode, MockClient a, MockClient b) {
		if (mode.equals("coin")) {
			a.send(new CoinDuelNet.Action("challenge", MockClient.args("stake", STAKE, "heads", true, "target", b.player.getUUID().toString())));
		} else {
			a.command("casino pvp challenge " + b.player.getName().getString() + " " + mode + " " + STAKE);
		}
		b.drain();
		String cmd = b.chatCommand("casino pvp accept");
		helper.assertTrue(cmd != null, mode + ": the target got a clickable [Accept]: " + b);
		b.chat.clear();
		b.command(cmd);
		PvpMatch m = Pvp.service().matchOf(a.player.getUUID()).orElse(null);
		helper.assertTrue(m != null && m.state().live(), mode + ": the duel started: " + (m == null ? null : m.state()));
		return m;
	}

	/** Every open = true PvP sync from inbox index {@code from}: "kind id". */
	private static List<String> opens(MockClient c, int from) {
		List<String> out = new ArrayList<>();
		for (int i = from; i < c.inbox.size(); i++) {
			CustomPacketPayload x = c.inbox.get(i);
			if (x instanceof PvpSyncPayload p && p.open()) {
				String id = "?";
				try {
					JsonObject o = JsonParser.parseString(p.json()).getAsJsonObject();
					id = (o.has("id") ? o.get("id").getAsString() : "?") + "/" + (o.has("state") ? o.get("state").getAsString() : "?");
				} catch (RuntimeException ignored) {
				}
				out.add(p.kind() + " " + id);
			}
		}
		return out;
	}

	/**
	 * A RESULT view (kind id/state) opened more than once: the B1 symptom. (A match view is sent open twice in the same
	 * tick at the accept: the accept command and MATCH_STARTED; the client only updates the open screen. Not counted.)
	 */
	private static List<String> duplicates(List<String> opens) {
		Map<String, Integer> n = new HashMap<>();
		opens.stream().filter(o -> o.startsWith("result ")).forEach(o -> n.merge(o, 1, Integer::sum));
		return n.entrySet().stream().filter(e -> e.getValue() > 1).map(e -> e.getKey() + " x" + e.getValue()).toList();
	}

	private static boolean gone(String... ids) {
		for (String id : ids) {
			if (id == null || Pvp.service().get(id).isPresent()) {
				return false;
			}
		}
		return true;
	}

	// ---- B1: the owner's exact case ---------------------------------------------------------------------------

	/**
	 * The owner's case: duel 1 (Scratch Showdown) → both press Rematch → duel 2 → another challenge right after → duel 3;
	 * after each result the players walk away to another screen (the poker table) without pressing Close. A third
	 * player's invite expires on the way. While all three matches leave the rematch window and the history, no PvP
	 * window opens by itself, and no view of any match ever opens twice.
	 */
	@GameTest(maxTicks = 3000, environment = ENV)
	public void ownerCaseThreeScratchDuelsThenPoker(GameTestHelper helper) {
		shorten();
		boolean[] restored = {false};
		MockClient a = MockClient.join(helper, new Vec3(2.5, 1, 3.5), START);
		MockClient b = MockClient.join(helper, new Vec3(3.5, 1, 3.5), START);
		String[] ids = new String[3];
		ids[0] = challenge(helper, "scratch", a, b).id;
		int[] stage = {0};
		int[] mark = {-1, -1};
		MockClient[] c = {null};
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			drive(EXTRAS_PRESS, a, b);
			if (c[0] != null) {
				c[0].drain();
			}
			// duels 1 → 2 → 3: each one's result window → both press Rematch (the owner's back-to-back duels)
			for (int i = stage[0]; i < 2; i++) {
				PvpMatch m = Pvp.service().get(ids[i]).orElse(null);
				helper.assertTrue(m != null, "duel " + (i + 1) + " vanished: a=" + a + " b=" + b);
				helper.assertTrue("rematch".equals(m.phase()) && "result".equals(a.screen) && "result".equals(b.screen),
					"duel " + (i + 1) + " in its rematch window, result open: " + m.phase() + " a=" + a + " b=" + b);
				a.send(new PvpActionPayload("rematch", ids[i], "", 0));
				b.send(new PvpActionPayload("rematch", ids[i], "", 0));
				String prev = ids[i];
				PvpMatch next = Pvp.service().matchOf(a.player.getUUID()).filter(x -> !x.id.equals(prev) && x.state().live()).orElse(null);
				helper.assertTrue(next != null, "rematch " + (i + 1) + " started: a=" + a);
				ids[i + 1] = next.id;
				stage[0] = i + 1;
			}
			if (stage[0] == 2) {
				PvpMatch m3 = Pvp.service().get(ids[2]).orElse(null);
				helper.assertTrue(m3 != null, "duel 3 vanished: a=" + a + " b=" + b);
				helper.assertTrue(m3.state() == MatchState.SETTLED && "result".equals(a.screen) && "result".equals(b.screen),
					"duel 3 settled, result open: a=" + a + " b=" + b);
				// walk away to the poker table: the poker screen replaces the result (no Close pressed)
				a.closeScreen();
				b.closeScreen();
				mark[0] = a.inbox.size();
				mark[1] = b.inbox.size();
				c[0] = MockClient.join(helper, new Vec3(1.5, 1, 3.5), START);
				c[0].command("casino pvp challenge " + a.player.getName().getString() + " scratch " + STAKE);
				stage[0] = 3;
			}
			helper.assertTrue(stage[0] == 3, "stage " + stage[0]);
			List<String> la = opens(a, mark[0]);
			List<String> lb = opens(b, mark[1]);
			helper.assertTrue(la.isEmpty() && lb.isEmpty(), "B1: a PvP window re-opened by itself: a " + la + " b " + lb);
			helper.assertTrue(a.screen == null && b.screen == null, "no PvP screen open: a=" + a + " b=" + b);
			helper.assertTrue(duplicates(opens(a, 0)).isEmpty() && duplicates(opens(b, 0)).isEmpty(),
				"a view opened twice: a " + duplicates(opens(a, 0)) + " b " + duplicates(opens(b, 0)));
			helper.assertTrue(Pvp.service().invitesFor(a.player.getUUID()).isEmpty(), "the third player's invite expired");
			helper.assertTrue(gone(ids), "all three duels left the history");
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			helper.assertTrue(helper.getTick() > doneAt[0] + 60, "three more scans after the last expiry");
			long total = a.balance() + b.balance();
			helper.assertTrue(total <= 2 * START && total > 2 * START - 3 * 2 * STAKE, "chips conserved less the rake: " + total);
			a.remove(helper);
			b.remove(helper);
			c[0].remove(helper);
			restore(restored);
		});
	}

	// ---- B1: every mode ---------------------------------------------------------------------------------------

	/** One duel in {@code mode}; the players walk away from the result; nothing re-opens until the match is gone. */
	private static void modeNeverReopens(GameTestHelper helper, String mode) {
		shorten();
		boolean[] restored = {false};
		MockClient a = MockClient.join(helper, new Vec3(2.5, 1, 3.5), START);
		MockClient b = MockClient.join(helper, new Vec3(3.5, 1, 3.5), START);
		String id = challenge(helper, mode, a, b).id;
		Consumer<MockClient> press = pressOf(mode);
		int[] mark = {-1, -1};
		long[] goneAt = {-1};
		helper.succeedWhen(() -> {
			drive(press, a, b);
			if (mark[0] < 0) {
				PvpMatch m = Pvp.service().get(id).orElse(null);
				helper.assertTrue(m != null, "match vanished: a=" + a + " b=" + b);
				helper.assertTrue(m.state() == MatchState.SETTLED && "result".equals(a.screen) && "result".equals(b.screen),
					mode + ": settled, result open: a=" + a + " b=" + b);
				a.closeScreen();
				b.closeScreen();
				mark[0] = a.inbox.size();
				mark[1] = b.inbox.size();
			}
			List<String> la = opens(a, mark[0]);
			List<String> lb = opens(b, mark[1]);
			helper.assertTrue(la.isEmpty() && lb.isEmpty(), mode + ": B1 re-open: a " + la + " b " + lb);
			helper.assertTrue(duplicates(opens(a, 0)).isEmpty() && duplicates(opens(b, 0)).isEmpty(),
				mode + ": a view opened twice: a " + duplicates(opens(a, 0)) + " b " + duplicates(opens(b, 0)));
			helper.assertTrue(gone(id), mode + ": the match left the history");
			if (goneAt[0] < 0) {
				goneAt[0] = helper.getTick();
			}
			helper.assertTrue(helper.getTick() > goneAt[0] + 40, mode + ": two more scans");
			a.remove(helper);
			b.remove(helper);
			restore(restored);
		});
	}

	@GameTest(maxTicks = 1500, environment = ENV)
	public void b1NeverReopensCoin(GameTestHelper helper) {
		modeNeverReopens(helper, "coin");
	}

	@GameTest(maxTicks = 1500, environment = ENV)
	public void b1NeverReopensScratch(GameTestHelper helper) {
		modeNeverReopens(helper, "scratch");
	}

	@GameTest(maxTicks = 2400, environment = ENV)
	public void b1NeverReopensPlinko(GameTestHelper helper) {
		modeNeverReopens(helper, "plinko");
	}

	@GameTest(maxTicks = 2400, environment = ENV)
	public void b1NeverReopensWheel(GameTestHelper helper) {
		modeNeverReopens(helper, "wheel");
	}

	@GameTest(maxTicks = 3000, environment = ENV)
	public void b1NeverReopensSlots(GameTestHelper helper) {
		modeNeverReopens(helper, "slots");
	}

	// ---- disconnect / rejoin mid-match ------------------------------------------------------------------------

	/**
	 * {@code a} disconnects while the duel runs and rejoins (same UUID) after it settled: the result may open once for
	 * the rejoined player; after it is closed nothing re-opens; chips are conserved (stakes once, payouts once).
	 */
	@GameTest(maxTicks = 1500, environment = ENV)
	public void disconnectRejoinMidMatch(GameTestHelper helper) {
		shorten();
		boolean[] restored = {false};
		MockClient a0 = MockClient.join(helper, new Vec3(2.5, 1, 3.5), START);
		MockClient b = MockClient.join(helper, new Vec3(3.5, 1, 3.5), START);
		UUID aId = a0.player.getUUID();
		String id = challenge(helper, "scratch", a0, b).id;
		a0.remove(helper); // disconnect mid-match
		MockClient[] a = {null};
		int[] mark = {-1, -1};
		long[] goneAt = {-1};
		helper.succeedWhen(() -> {
			drive(EXTRAS_PRESS, b);
			PvpMatch m = Pvp.service().get(id).orElse(null);
			if (a[0] == null) {
				helper.assertTrue(m != null && m.state() == MatchState.SETTLED, "settled while a is offline: " + (m == null ? null : m.state()));
				a[0] = MockClient.rejoin(helper, aId, new Vec3(2.5, 1, 3.5));
			}
			drive(NO_PRESS, a[0]);
			if (mark[0] < 0) {
				helper.assertTrue(b.screen != null && b.screen.equals("result"), "b sees the result: " + b);
				helper.assertTrue("result".equals(a[0].screen), "the rejoined player is shown the result once: " + a[0]);
				// a's result: shown at most once after the rejoin (asserted below); close both
				a[0].closeScreen();
				b.closeScreen();
				mark[0] = a[0].inbox.size();
				mark[1] = b.inbox.size();
			}
			List<String> la = opens(a[0], mark[0]);
			List<String> lb = opens(b, mark[1]);
			helper.assertTrue(la.isEmpty() && lb.isEmpty(), "re-open after rejoin: a " + la + " b " + lb);
			helper.assertTrue(duplicates(opens(a[0], 0)).isEmpty() && duplicates(opens(b, 0)).isEmpty(), "a view opened twice: a " + duplicates(opens(a[0], 0)) + " b " + duplicates(opens(b, 0)));
			helper.assertTrue(gone(id), "the match left the history");
			if (goneAt[0] < 0) {
				goneAt[0] = helper.getTick();
			}
			helper.assertTrue(helper.getTick() > goneAt[0] + 40, "two more scans");
			long total = Economies.get().balance(helper.getLevel().getServer(), aId) + b.balance();
			helper.assertTrue(total <= 2 * START && total >= 2 * START - 2 * STAKE, "chips conserved less the rake: " + total);
			a[0].remove(helper);
			b.remove(helper);
			restore(restored);
		});
	}

	// ---- server restart with history --------------------------------------------------------------------------

	/**
	 * A duel settles (result shown and walked away from), then the server restarts while the match is in its rematch
	 * window (PvpUi's memory is gone, as in a new JVM / world reload). After the restart nothing opens by itself.
	 */
	@GameTest(maxTicks = 1500, environment = RESTART_ENV)
	public void restartWithHistoryNeverReopens(GameTestHelper helper) {
		shorten();
		boolean[] restored = {false};
		MockClient a = MockClient.join(helper, new Vec3(2.5, 1, 3.5), START);
		MockClient b = MockClient.join(helper, new Vec3(3.5, 1, 3.5), START);
		String id = challenge(helper, "scratch", a, b).id;
		int[] mark = {-1, -1};
		long[] goneAt = {-1};
		helper.succeedWhen(() -> {
			drive(EXTRAS_PRESS, a, b);
			if (mark[0] < 0) {
				PvpMatch m = Pvp.service().get(id).orElse(null);
				helper.assertTrue(m != null, "match vanished: a=" + a + " b=" + b);
				helper.assertTrue(m.state() == MatchState.SETTLED && "result".equals(a.screen) && "result".equals(b.screen),
					"settled, result open: a=" + a + " b=" + b);
				a.closeScreen();
				b.closeScreen();
				mark[0] = a.inbox.size();
				mark[1] = b.inbox.size();
				try {
					java.lang.reflect.Method clear = dev.nezo.burmaldaholic.pvp.PvpUi.class.getDeclaredMethod("clearAll");
					clear.setAccessible(true);
					clear.invoke(null);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				Pvp.simulateRestart(helper.getLevel().getServer());
			}
			List<String> la = opens(a, mark[0]);
			List<String> lb = opens(b, mark[1]);
			helper.assertTrue(la.isEmpty() && lb.isEmpty(), "re-open after the restart: a " + la + " b " + lb);
			helper.assertTrue(gone(id), "the match left the history");
			if (goneAt[0] < 0) {
				goneAt[0] = helper.getTick();
			}
			helper.assertTrue(helper.getTick() > goneAt[0] + 40, "two more scans");
			a.remove(helper);
			b.remove(helper);
			restore(restored);
		});
	}
}
