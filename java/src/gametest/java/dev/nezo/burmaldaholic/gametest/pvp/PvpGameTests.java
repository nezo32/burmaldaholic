package dev.nezo.burmaldaholic.gametest.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpMatchData;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.core.rng.StreakTracker;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.vip.VipService;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * PvP engine in a real world (PVP.md §16.1 C5/C12, §16.7 I1–I3, §16.8 B5/B8): atomic escrow, settlement and
 * money conservation, tape persistence + play-out on restart / casino mode off / admin cancel, bots from the
 * bank, and the C-3 listener rules (no §14 streak for PvP, bot-weighted VIP credit).
 *
 * <p>Everything that restarts the engine runs inside ONE test (synchronously), so parallel tests never see
 * their matches vanish.
 */
public class PvpGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");
	private static final String MODE = "jp1test";

	/** A tiny fair test mode: one Spin! wait, one winner drawn uniformly (tape = seat order + winner). */
	static final class TestMode implements PvpMode<JsonObject, JsonObject> {
		@Override
		public String id() {
			return MODE;
		}

		@Override
		public int minPlayers() {
			return 2;
		}

		@Override
		public int maxPlayers() {
			return 6;
		}

		@Override
		public AnchorKind anchor() {
			return AnchorKind.NONE;
		}

		@Override
		public boolean equalStakes() {
			return true;
		}

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public String validate(JsonObject params) {
			return null;
		}

		@Override
		public JsonObject defaults() {
			return new JsonObject();
		}

		@Override
		public JsonObject draw(PvpRng rng, int players, JsonObject params) {
			JsonObject t = new JsonObject();
			JsonArray order = new JsonArray();
			for (int i : rng.permutation(players)) {
				order.add(i);
			}
			t.add("order", order);
			t.addProperty("w", rng.nextInt(players));
			return t;
		}

		@Override
		public Outcome score(JsonObject tape, long[] stakes, JsonObject params) {
			int n = stakes.length;
			int w = tape.get("w").getAsInt();
			int[] order = new int[n];
			for (int i = 0; i < n; i++) {
				order[i] = tape.getAsJsonArray("order").get(i).getAsInt();
			}
			long[] points = new long[n];
			points[w] = 1;
			int[] rank = new int[n];
			rank[0] = w;
			for (int i = 0, k = 1; i < n; i++) {
				if (i != w) {
					rank[k++] = i;
				}
			}
			return new Outcome(points, rank, new int[] {w}, order, List.of());
		}

		@Override
		public List<Step> timeline(JsonObject tape, Outcome outcome, JsonObject params) {
			return List.of(new Step("round_wait", 20, 0, true, new JsonObject()));
		}

		@Override
		public JsonElement encodeParams(JsonObject params) {
			return params;
		}

		@Override
		public JsonObject decodeParams(JsonElement json) {
			return json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
		}

		@Override
		public JsonElement encodeTape(JsonObject tape) {
			return tape;
		}

		@Override
		public JsonObject decodeTape(JsonElement json) {
			return json.getAsJsonObject();
		}
	}

	private static synchronized void ensureMode() {
		if (PvpModes.get(MODE).isEmpty()) {
			PvpModes.register(new TestMode());
		}
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		p.setPos(at.x, at.y, at.z);
		MinecraftServer server = helper.getLevel().getServer();
		Economies.get().setBalance(server, p.getUUID(), balance, TEST);
		StreakTracker.set(server, p.getUUID(), 0);
		return p;
	}

	private static long balance(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	private static PvpService.Anchor none(ServerPlayer p) {
		return new PvpService.Anchor(AnchorKind.NONE, (net.minecraft.server.level.ServerLevel) p.level(), p.blockPosition());
	}

	private static PvpMatch ok(GameTestHelper helper, Result<PvpMatch> r, String what) {
		helper.assertTrue(r.isOk(), what + ": " + (r.error() == null ? "" : r.error().getString()));
		return r.value();
	}

	private static long rake(long pot) {
		return PvpMath.rake(pot, CasinoConfig.pvp().rakeBasisPoints);
	}

	/** Own environment (a separate batch): its restart / casino-off steps touch every live match on the server. */
	@GameTest(maxTicks = 600, environment = "burmaldaholic:pvp_restart_engine")
	public void engineMoneyAndPlayOut(GameTestHelper helper) {
		ensureMode();
		MinecraftServer server = helper.getLevel().getServer();
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		ServerPlayer c = player(helper, 1000);
		List<ServerPlayer> all = List.of(a, b, c);
		long vipA = VipService.wagered(server, a.getUUID());

		// ---- 1. HUMANS_ONLY lobby, 3 players, admin settle-now: escrow + one settlement, chips conserved ----
		PvpMatch m = ok(helper, pvp.openLobby(a, MODE, new JsonObject(), 100, none(a), BotSettings.HUMANS_ONLY, false), "open lobby");
		helper.assertTrue(balance(a) == 900, "host entry escrowed at once");
		ok(helper, pvp.join(b, m.id, 0), "join b");
		ok(helper, pvp.join(c, m.id, 0), "join c");
		helper.assertFalse(pvp.join(c, m.id, 0).isOk(), "no double join");
		helper.assertTrue(PvpMatchData.get(server).raw().containsKey(m.id), "LOBBY is persisted");
		ok(helper, pvp.start(a, m.id), "host start");
		helper.assertTrue(m.state() == MatchState.DRAWN, "tape drawn");
		helper.assertTrue(PvpMatchData.get(server).raw().get(m.id).contains("\"state\":\"DRAWN\""), "DRAWN persisted before the reveal");
		helper.assertTrue(m.outcome() == null, "outcome hidden before the reveal ends");
		pvp.cancel(m.id); // admin: settle now
		helper.assertTrue(m.state() == MatchState.SETTLED, "settled");
		long total = all.stream().mapToLong(PvpGameTests::balance).sum();
		helper.assertTrue(total == 3000 - rake(300), "Σ balances = start − rake: " + total);
		long[] pay = m.payouts();
		helper.assertTrue(pay.length == 3 && pay[0] + pay[1] + pay[2] == 300 - rake(300), "payouts = W");
		helper.assertTrue(StreakTracker.get(server, a.getUUID()) == 0 && StreakTracker.get(server, b.getUUID()) == 0, "§14 streak untouched (C12)");
		helper.assertTrue(VipService.wagered(server, a.getUUID()) - vipA == 100, "stake counts as wagered once");
		helper.assertTrue(pvp.stats(a.getUUID()).wins() + pvp.stats(a.getUUID()).losses() == 1, "record updated");

		// ---- 2. Restart during a reveal: settled from the tape (I1); open lobby refunded (I2) ----
		long before = all.stream().mapToLong(PvpGameTests::balance).sum();
		PvpMatch drawn = ok(helper, pvp.openLobby(a, MODE, new JsonObject(), 50, none(a), BotSettings.HUMANS_ONLY, false), "lobby 2");
		ok(helper, pvp.join(b, drawn.id, 0), "join 2");
		ok(helper, pvp.start(a, drawn.id), "start 2");
		String tape = PvpMatchData.get(server).raw().get(drawn.id);
		long cBefore = balance(c);
		PvpMatch lobby = ok(helper, pvp.openLobby(c, MODE, new JsonObject(), 70, none(c), BotSettings.HUMANS_ONLY, false), "lobby 3");
		helper.assertTrue(balance(c) == cBefore - 70, "c escrowed");
		Pvp.simulateRestart(server);
		PvpMatch replayed = pvp.get(drawn.id).orElseThrow();
		helper.assertTrue(replayed.state() == MatchState.SETTLED, "DRAWN settled on load");
		helper.assertTrue(PvpMatchData.get(server).raw().get(drawn.id).contains("\"state\":\"SETTLED\""), "SETTLED persisted");
		helper.assertTrue(tape.contains("\"tape\""), "tape stored");
		helper.assertTrue(pvp.get(lobby.id).isEmpty() && !PvpMatchData.get(server).raw().containsKey(lobby.id), "lobby refunded + dropped");
		long after = all.stream().mapToLong(PvpGameTests::balance).sum();
		helper.assertTrue(after == before - rake(100), "restart conserves chips: " + before + " → " + after);
		Pvp.simulateRestart(server);
		helper.assertTrue(all.stream().mapToLong(PvpGameTests::balance).sum() == after, "idempotent: a second load pays nothing");

		// ---- 3. Casino mode off mid-match: DRAWN settled at once, lobby refunded (I3) ----
		before = after;
		PvpMatch live = ok(helper, pvp.openLobby(a, MODE, new JsonObject(), 40, none(a), BotSettings.HUMANS_ONLY, false), "lobby 4");
		ok(helper, pvp.join(b, live.id, 0), "join 4");
		ok(helper, pvp.start(a, live.id), "start 4");
		PvpMatch open = ok(helper, pvp.openLobby(c, MODE, new JsonObject(), 30, none(c), BotSettings.HUMANS_ONLY, false), "lobby 5");
		CasinoMode.set(server, false);
		CasinoMode.set(server, true);
		helper.assertTrue(live.state() == MatchState.SETTLED, "settled at casino off");
		helper.assertTrue(pvp.get(open.id).isEmpty(), "lobby cancelled at casino off");
		after = all.stream().mapToLong(PvpGameTests::balance).sum();
		helper.assertTrue(after == before - rake(80), "casino off conserves chips");

		// ---- 4. Leave before START = refund; host leaves → earliest joiner hosts (C8) ----
		PvpMatch l = ok(helper, pvp.openLobby(a, MODE, new JsonObject(), 25, none(a), BotSettings.HUMANS_ONLY, false), "lobby 6");
		ok(helper, pvp.join(b, l.id, 0), "join 6b");
		ok(helper, pvp.join(c, l.id, 0), "join 6c");
		pvp.leave(a);
		helper.assertTrue(b.getUUID().equals(l.host()), "earliest joiner is host");
		pvp.leave(b);
		pvp.leave(c);
		helper.assertTrue(pvp.get(l.id).isEmpty(), "empty lobby closed");
		helper.assertTrue(all.stream().mapToLong(PvpGameTests::balance).sum() == after, "leaves refunded exactly");

		// ---- 5. BOTS_ONLY vs 3 bank bots (B5/B8): starts at once, bots move no chips, weighted VIP credit ----
		long vipBefore = VipService.wagered(server, a.getUUID());
		long aBefore = balance(a);
		BotSettings botsOnly = new BotSettings(SeatPolicy.BOTS_ONLY, 3, BotDifficulty.MIXED, false, true, BotSpeed.INSTANT);
		PvpMatch vsBots = ok(helper, pvp.openLobby(a, MODE, new JsonObject(), 100, none(a), botsOnly, false), "bots only");
		helper.assertTrue(vsBots.participants().size() == 4, "3 bots seated: " + vsBots.participants().size());
		helper.assertTrue(vsBots.participants().stream().filter(Participant::isBot).count() == 3, "bots");
		helper.assertTrue(vsBots.state() == MatchState.DRAWN, "BOTS_ONLY starts at once");
		helper.assertTrue(vsBots.pot() == 400, "pot = 4 × entry");
		pvp.cancel(vsBots.id);
		long won = vsBots.payouts()[0];
		helper.assertTrue(balance(a) == aBefore - 100 + won, "human paid exactly its payout");
		helper.assertTrue(pvp.rivals(a.getUUID(), 50).stream().noneMatch(r -> r.name().startsWith("gui.")), "no bot rivals");
		helper.assertTrue(VipService.wagered(server, a.getUUID()) - vipBefore == 50, "VIP credit weighted ×0.5 against bots (C-3)");
		helper.assertTrue(StreakTracker.get(server, a.getUUID()) == 0, "bot round: no §14 streak");

		// ---- 6. Timed play-out: a MIXED lobby with bots and a bot duel run on server ticks ----
		PvpMatch timed = ok(helper, pvp.openLobby(b, MODE, new JsonObject(), 20,
			none(b), new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.HARD, false, true, BotSpeed.NORMAL), false), "mixed");
		ok(helper, pvp.start(b, timed.id), "start with bots");
		helper.assertTrue(timed.participants().size() == 3, "2 bots filled at start");
		PvpMatch duel = ok(helper, pvp.challenge(c, MODE, new JsonObject(), 20, new PvpService.Opponent.BotTarget(BotDifficulty.EASY)), "bot duel");
		long[] start = {balance(b), balance(c)};
		List<String> done = new ArrayList<>();
		helper.succeedWhen(() -> {
			helper.assertTrue(timed.state() == MatchState.SETTLED, "mixed match settled by the timeline");
			helper.assertTrue(duel.state() == MatchState.SETTLED, "bot duel accepted and settled");
			if (done.isEmpty()) {
				done.add("x");
				helper.assertTrue(balance(b) == start[0] + timed.payouts()[0], "b credited its payout");
				helper.assertTrue(balance(c) == start[1] - 20 + duel.payouts()[0], "c: stake escrowed at accept, payout at settle");
				for (ServerPlayer p : all) {
					pvp.leave(p);
					server.getPlayerList().remove(p);
				}
			}
		});
	}
}
