package dev.nezo.burmaldaholic.gametest.pvp;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.network.MenuRequestPayload;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * B1 (owner playtest 1): after Scratch Showdown duels the result window re-opened on BOTH players every ~2 minutes,
 * even over a poker table. Cause: an older match leaving the history ({@code pvp.historyTicks}) or an expiring invite
 * pushed CLOSED, {@code PvpUi.clear} wiped the bookkeeping of the newer match, and the 10-tick scan re-opened its result.
 * These tests shorten the history / rematch / invite timers (own environment, restored after) and assert that no
 * {@code open = true} PvP sync reaches either player after they closed the result window.
 */
public class PvpHistoryGameTests {
	private static final String ENV = "burmaldaholic:pvp_history";
	private static final long STAKE = 100;
	private static final long START = 5000;

	// ---- shortened timers (ref-counted: both tests share the environment's batch) ------------------------------

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

	// ---- helpers --------------------------------------------------------------------------------------------

	private static void hub(MockClient c, String action, long amount) {
		c.send(new MenuRequestPayload("challenges", action, amount));
	}

	/** {@code a} challenges {@code b} to Scratch Showdown from the hub; {@code b} clicks the chat [Accept]. */
	private static PvpMatch duel(GameTestHelper helper, MockClient a, MockClient b) {
		hub(a, "p:new:scratch", 0);
		hub(a, "p:ch:" + b.player.getUUID(), STAKE);
		b.drain();
		String cmd = b.chatCommand("casino pvp accept");
		helper.assertTrue(cmd != null, "the target got a clickable [Accept]: " + b);
		b.chat.clear();
		b.command(cmd);
		PvpMatch m = Pvp.service().matchOf(a.player.getUUID()).orElse(null);
		helper.assertTrue(m != null && m.state().live(), "the duel started: " + (m == null ? null : m.state()));
		return m;
	}

	/** One scan's worth of the players' screens: Scratch! while the duel runs. */
	private static void play(MockClient... clients) {
		for (MockClient c : clients) {
			c.drain();
			if (c.kind.equals("match")) {
				c.send(new ExtrasActionPayload("pvp", "press", new CompoundTag()));
			}
		}
	}

	/** PvP syncs with {@code open = true} the client received from inbox index {@code from} on. */
	private static List<String> opens(MockClient c, int from) {
		List<String> out = new ArrayList<>();
		List<CustomPacketPayload> in = c.inbox;
		for (int i = from; i < in.size(); i++) {
			if (in.get(i) instanceof PvpSyncPayload p && p.open()) {
				out.add(p.kind() + " " + p.json().substring(0, Math.min(60, p.json().length())));
			}
		}
		return out;
	}

	// ---- tests ----------------------------------------------------------------------------------------------

	/**
	 * Two back-to-back duels. Both players close the second result; the first duel then leaves its rematch window,
	 * leaves the history and closes, then the second does the same. No PvP screen opens by itself on either player.
	 */
	@GameTest(maxTicks = 1600, environment = ENV)
	public void historyExpiryNeverReopensTheResult(GameTestHelper helper) {
		shorten();
		boolean[] restored = {false};
		MockClient a = MockClient.join(helper, new Vec3(2.5, 1, 3.5), START);
		MockClient b = MockClient.join(helper, new Vec3(3.5, 1, 3.5), START);
		PvpMatch first = duel(helper, a, b);
		PvpMatch[] second = {null};
		int[] mark = {-1, -1};
		long[] firstClosed = {-1};
		helper.succeedWhen(() -> {
			play(a, b);
			if (second[0] == null) {
				helper.assertTrue(first.state() == MatchState.SETTLED, "first duel settled: " + first.state());
				// right after the first result (still in its rematch window): the next duel
				a.closeScreen();
				b.closeScreen();
				second[0] = duel(helper, a, b);
				helper.assertTrue(second[0] != first, "a new match");
			}
			if (mark[0] < 0) {
				helper.assertTrue(second[0].state() == MatchState.SETTLED && "result".equals(a.screen) && "result".equals(b.screen),
					"second duel settled, result window open: a=" + a + " b=" + b);
				// both players close the result window
				a.closeScreen();
				b.closeScreen();
				mark[0] = a.inbox.size();
				mark[1] = b.inbox.size();
			}
			if (firstClosed[0] < 0 && Pvp.service().get(first.id).isEmpty()) {
				firstClosed[0] = helper.getTick();
			}
			helper.assertTrue(opens(a, mark[0]).isEmpty() && opens(b, mark[1]).isEmpty(),
				"nothing re-opened: a " + opens(a, mark[0]) + " b " + opens(b, mark[1]));
			helper.assertTrue(a.screen == null && b.screen == null, "no PvP screen open: a=" + a + " b=" + b);
			helper.assertTrue(firstClosed[0] >= 0 && helper.getTick() > firstClosed[0] + 40, "the first duel left the history (+2 scans)");
			helper.assertTrue(Pvp.service().get(second[0].id).isEmpty(), "the second duel left the history too: " + second[0].phase());
			helper.assertTrue(Pvp.service().matchOf(a.player.getUUID()).isEmpty(), "no match left");
			a.remove(helper.getLevel().getServer());
			b.remove(helper.getLevel().getServer());
			restore(restored);
		});
	}

	/** After a duel's result was closed, an invite from a third player expires: the result does not re-open. */
	@GameTest(maxTicks = 1200, environment = ENV)
	public void expiringInviteNeverReopensTheResult(GameTestHelper helper) {
		shorten();
		boolean[] restored = {false};
		MockClient a = MockClient.join(helper, new Vec3(2.5, 1, 3.5), START);
		MockClient b = MockClient.join(helper, new Vec3(3.5, 1, 3.5), START);
		PvpMatch duel = duel(helper, a, b);
		MockClient[] c = {null};
		int[] mark = {-1, -1};
		helper.succeedWhen(() -> {
			play(a, b);
			if (c[0] == null) {
				helper.assertTrue(duel.state() == MatchState.SETTLED && "result".equals(a.screen), "duel settled, result open: " + a);
				a.closeScreen();
				b.closeScreen();
				mark[0] = a.inbox.size();
				mark[1] = b.inbox.size();
				c[0] = MockClient.join(helper, new Vec3(1.5, 1, 3.5), START);
				c[0].command("casino pvp challenge " + a.player.getName().getString() + " scratch " + STAKE);
				helper.assertTrue(Pvp.service().invitesFor(a.player.getUUID()).size() == 1, "the challenge reached a");
			}
			helper.assertTrue(Pvp.service().invitesFor(a.player.getUUID()).isEmpty(), "the invite expired");
			helper.assertTrue(opens(a, mark[0]).isEmpty() && opens(b, mark[1]).isEmpty(),
				"nothing re-opened: a " + opens(a, mark[0]) + " b " + opens(b, mark[1]));
			helper.assertTrue(a.screen == null && b.screen == null, "no PvP screen open: a=" + a + " b=" + b);
			helper.assertTrue(Pvp.service().get(duel.id).isEmpty(), "the duel left the history");
			a.remove(helper.getLevel().getServer());
			b.remove(helper.getLevel().getServer());
			c[0].remove(helper.getLevel().getServer());
			restore(restored);
		});
	}
}
