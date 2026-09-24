package dev.nezo.burmaldaholic.gametest.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpMatchData;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.EscrowRecord;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelMode;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Adversarial PvP engine checks (Java wave-2 re-review, area A): a mode that returns an unsettleable outcome is
 * refunded instead of burning chips or retrying forever, a bankroll that vanished without a tombstone does not
 * block the humans' payouts, private-lobby invites can't be spammed or pushed past the opt-out, and a duel is
 * only accepted within reach of the challenger (withdrawn when the challenger walks off).
 */
public class PvpAdversarialGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");
	private static final String BAD = "jp1bad";

	/** Fair 1-winner mode whose score() can be switched to a repeated winner (a buggy mode). */
	static final class FlakyMode implements PvpMode<JsonObject, JsonObject> {
		static volatile boolean broken;

		@Override
		public String id() {
			return BAD;
		}

		@Override
		public int minPlayers() {
			return 2;
		}

		@Override
		public int maxPlayers() {
			return 4;
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
			return t;
		}

		@Override
		public Outcome score(JsonObject tape, long[] stakes, JsonObject params) {
			int n = stakes.length;
			int[] order = new int[n];
			for (int i = 0; i < n; i++) {
				order[i] = tape.getAsJsonArray("order").get(i).getAsInt();
			}
			int[] rank = order.clone();
			long[] points = new long[n];
			return new Outcome(points, rank, broken ? new int[] {0, 0} : new int[] {order[0]}, order, List.of());
		}

		@Override
		public List<Step> timeline(JsonObject tape, Outcome outcome, JsonObject params) {
			return List.of(new Step("round_wait", 200, 0, false, new JsonObject()));
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
		if (PvpModes.get(BAD).isEmpty()) {
			PvpModes.register(new FlakyMode());
		}
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	private static long bal(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	private static PvpService.Anchor none(ServerPlayer p) {
		return new PvpService.Anchor(AnchorKind.NONE, (ServerLevel) p.level(), p.blockPosition());
	}

	private static PvpMatch ok(GameTestHelper helper, Result<PvpMatch> r, String what) {
		helper.assertTrue(r.isOk(), what + ": " + (r.error() == null ? "" : r.error().getString()));
		return r.value();
	}

	private static void done(GameTestHelper helper, ServerPlayer... players) {
		MinecraftServer s = helper.getLevel().getServer();
		for (ServerPlayer p : players) {
			Pvp.service().leave(p);
			s.getPlayerList().remove(p);
		}
	}

	/** A repeated winner would pay one share of W/2 and burn the other: the drawn match is refunded instead. */
	@GameTest(maxTicks = 100)
	public void invalidOutcomeIsRefundedNotBurnt(GameTestHelper helper) {
		ensureMode();
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		PvpMatch m;
		synchronized (FlakyMode.class) {
			FlakyMode.broken = false;
			m = ok(helper, pvp.openLobby(a, BAD, new JsonObject(), 100, none(a), BotSettings.HUMANS_ONLY, false), "open");
			ok(helper, pvp.join(b, m.id, 0), "join");
			ok(helper, pvp.start(a, m.id), "start");
			helper.assertTrue(m.state() == MatchState.DRAWN && bal(a) == 900 && bal(b) == 900, "drawn, both escrowed");
			FlakyMode.broken = true; // e.g. a config / code change between the draw and the settlement
			try {
				pvp.cancel(m.id); // admin: settle now
			} finally {
				FlakyMode.broken = false;
			}
		}
		helper.assertTrue(m.state() == MatchState.CANCELLED, "an unsettleable outcome is not paid: " + m.state());
		helper.assertTrue(bal(a) == 1000 && bal(b) == 1000, "both stakes back exactly: " + bal(a) + " / " + bal(b));
		helper.assertTrue(pvp.get(m.id).isEmpty(), "no stuck match left retrying");
		done(helper, a, b);
		helper.succeed();
	}

	/** A bankroll gone without a tombstone must not block the humans' payouts (the settlement would fail forever). */
	@GameTest(maxTicks = 100)
	public void vanishedBankrollDoesNotBlockTheHumans(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		PvpService pvp = Pvp.service();
		Economy eco = Economies.get();
		BlockPos machine = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(machine, Blocks.STONE.defaultBlockState());
		UUID owner = UUID.randomUUID();
		String bankroll = "jadv_gone_" + UUID.randomUUID();
		eco.bankrolls(server).open(bankroll, owner);
		eco.transfer(server, AccountId.HOUSE, AccountId.bankroll(bankroll), 5_000, TEST);
		ServerPlayer a = player(helper, 3000);
		TableOwnershipProvider prev = CoreServices.tableOwnership();
		OwnedTable table = new OwnedTable(owner, bankroll, 0, 0, true);
		CoreServices.setTableOwnership((l, p) -> p.equals(machine) ? Optional.of(table) : prev.owner(l, p));
		PvpMatch m;
		try {
			m = ok(helper, pvp.openLobby(a, "plinko", new PlinkoBattleMode().encodeParams(new PlinkoBattleMode.Params("low", 3)), 100,
				new PvpService.Anchor(AnchorKind.PLINKO_MACHINE, level, machine),
				new BotSettings(SeatPolicy.BOTS_ONLY, 2, BotDifficulty.NORMAL, false, true, BotSpeed.INSTANT), false), "owned bots-only");
		} finally {
			CoreServices.setTableOwnership(prev);
		}
		helper.assertTrue(m.state() == MatchState.DRAWN && m.participants().stream().filter(Participant::isBot).count() == 2, "drawn with 2 bankroll bots");
		// the bankroll disappears with no tombstone (closed before tombstones existed / pruned by mistake)
		long left = eco.bankrolls(server).get(bankroll).orElseThrow().balance();
		eco.transfer(server, AccountId.bankroll(bankroll), AccountId.player(owner), left, TEST);
		eco.bankrolls(server).close(bankroll);
		CasinoWorldData.get(server).ledger().tombstones().remove(bankroll);
		pvp.cancel(m.id); // settle now
		helper.assertTrue(m.state() == MatchState.SETTLED, "settled once, not stuck retrying: " + m.state());
		long net = m.payouts()[0] - m.participants().get(0).stake();
		helper.assertTrue(bal(a) == 3000 + net, "the human is paid exactly: " + bal(a) + " vs " + (3000 + net));
		helper.assertTrue(eco.bankrolls(server).get(bankroll).isEmpty(), "the bankroll is not re-created");
		done(helper, a);
		helper.succeed();
	}

	/** A private-lobby host can't spam a guest with repeated invites, nor invite a player who opted out. */
	@GameTest(maxTicks = 100)
	public void lobbyInvitesCannotBeSpammed(GameTestHelper helper) {
		ensureMode();
		PvpService pvp = Pvp.service();
		ServerPlayer host = player(helper, 1000);
		ServerPlayer guest = player(helper, 1000);
		ServerPlayer shy = player(helper, 1000);
		pvp.setAcceptInvites(shy.getUUID(), false);
		PvpMatch m = ok(helper, pvp.openLobby(host, BAD, new JsonObject(), 50, none(host), BotSettings.HUMANS_ONLY, true), "private lobby");
		Result<Boolean> first = pvp.inviteToLobby(host, guest.getUUID());
		helper.assertTrue(first.isOk() && Boolean.TRUE.equals(first.value()), "first invite sent");
		Result<Boolean> again = pvp.inviteToLobby(host, guest.getUUID());
		helper.assertTrue(again.isOk() && Boolean.FALSE.equals(again.value()), "a repeated invite sends nothing");
		helper.assertFalse(pvp.inviteToLobby(host, shy.getUUID()).isOk(), "a player who takes no challenges can't be invited");
		helper.assertFalse(pvp.inviteToLobby(host, host.getUUID()).isOk(), "no self invite");
		helper.assertTrue(m.guests().size() == 1, "one guest");
		pvp.setAcceptInvites(shy.getUUID(), true);
		done(helper, host, guest, shy);
		helper.assertTrue(bal(host) == 1000, "lobby refunded");
		helper.succeed();
	}

	/** Rule 5 re-checked when money moves: a target out of reach can't accept; a challenger who walks off withdraws. */
	@GameTest(maxTicks = 200)
	public void duelNeedsReach(GameTestHelper helper) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		JsonElement params = new CoinDuelMode().encodeParams(new CoinDuelMode.Params(100, true));
		PvpMatch invite = ok(helper, pvp.challenge(a, "coin", params, 100, new PvpService.Opponent.PlayerTarget(b.getUUID())), "challenge");
		b.setPos(b.getX() + 64, b.getY(), b.getZ());
		helper.assertFalse(pvp.accept(b, invite.id).isOk(), "accepting from 64 blocks away is refused");
		helper.assertTrue(bal(a) == 1000 && bal(b) == 1000, "nothing escrowed");
		b.setPos(a.getX(), a.getY(), a.getZ());
		PvpMatch second = ok(helper, pvp.challenge(a, "coin", params, 100, new PvpService.Opponent.PlayerTarget(b.getUUID())), "challenge 2");
		a.setPos(a.getX() + 64, a.getY(), a.getZ()); // the challenger walks off before the answer
		helper.succeedWhen(() -> {
			helper.assertTrue(pvp.get(second.id).isEmpty() && second.state() == MatchState.CANCELLED, "invite withdrawn");
			helper.assertTrue(bal(a) == 1000 && bal(b) == 1000, "nothing moved");
			done(helper, a, b);
		});
	}
	/** challenge → withdraw → challenge can't spam a target: the withdraw starts the per-pair cooldown too. */
	@GameTest(maxTicks = 100)
	public void withdrawStartsTheChallengeCooldown(GameTestHelper helper) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		ServerPlayer c = player(helper, 1000);
		JsonElement params = new CoinDuelMode().encodeParams(new CoinDuelMode.Params(100, true));
		PvpMatch first = ok(helper, pvp.challenge(a, "coin", params, 100, new PvpService.Opponent.PlayerTarget(b.getUUID())), "challenge");
		pvp.withdraw(a, first.id);
		helper.assertTrue(pvp.get(first.id).isEmpty(), "withdrawn");
		Result<PvpMatch> again = pvp.challenge(a, "coin", params, 100, new PvpService.Opponent.PlayerTarget(b.getUUID()));
		helper.assertFalse(again.isOk(), "re-challenging the same target right after a withdraw is refused");
		helper.assertTrue(again.error().getString().length() > 0, "with a reason");
		PvpMatch other = ok(helper, pvp.challenge(a, "coin", params, 100, new PvpService.Opponent.PlayerTarget(c.getUUID())), "another target is fine");
		pvp.leave(a); // leaving withdraws too
		helper.assertFalse(pvp.challenge(a, "coin", params, 100, new PvpService.Opponent.PlayerTarget(c.getUUID())).isOk(),
			"a withdraw through leave starts the cooldown as well");
		helper.assertTrue(pvp.get(other.id).isEmpty(), "second invite withdrawn");
		PvpMatch back = ok(helper, pvp.challenge(b, "coin", params, 100, new PvpService.Opponent.PlayerTarget(a.getUUID())),
			"the other direction is not blocked");
		pvp.decline(a, back.id);
		helper.assertTrue(bal(a) == 1000 && bal(b) == 1000 && bal(c) == 1000, "nothing moved");
		done(helper, a, b, c);
		helper.succeed();
	}

	/** A Double-or-nothing answer that names no question (seq -1) goes through the stale guard: dropped. */
	@GameTest(maxTicks = 1200)
	public void answerWithoutSeqIsDropped(GameTestHelper helper) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 5000);
		ServerPlayer b = player(helper, 5000);
		PvpMatch first = ok(helper, pvp.challenge(a, "coin", new CoinDuelMode().encodeParams(new CoinDuelMode.Params(100, true)), 100,
			new PvpService.Opponent.PlayerTarget(b.getUUID())), "challenge");
		ok(helper, pvp.accept(b, first.id), "accept");
		boolean[] checked = {false};
		helper.succeedWhen(() -> {
			if (!checked[0]) {
				for (ServerPlayer p : List.of(a, b)) {
					Optional<DecisionView> d = pvp.decisionFor(p.getUUID());
					if (d.isPresent() && "coin.don_offer".equals(d.get().decision())) {
						long seq = first.decisionSeq();
						pvp.decide(p, "coin.don_offer", 1, first.id, -1);
						pvp.decide(p, "coin.don_offer", 1, null, -1);
						helper.assertTrue(first.decisionSeq() == seq && pvp.decisionFor(p.getUUID()).map(DecisionView::decision).orElse("")
							.equals("coin.don_offer"), "an answer without seq changes nothing");
						pvp.decide(p, "coin.don_offer", 0, first.id, first.decisionSeq()); // walk away: the chain ends
						checked[0] = true;
					}
				}
			}
			helper.assertTrue(checked[0], "the offer was asked");
			helper.assertTrue(pvp.all().stream().noneMatch(m -> first.id.equals(m.chainOf)), "no second link was started");
			helper.assertTrue(bal(a) + bal(b) == 10_000 - first.rake(), "one flip settled: " + (bal(a) + bal(b)));
			done(helper, a, b);
		});
	}

	/**
	 * Unreadable saved match records never take their escrow with them (own environment: the simulated restart
	 * touches every live match): refunded from the escrow record, else from what can be read of the record, else
	 * quarantined for an operator; an escrow record without its match is refunded too.
	 */
	@GameTest(maxTicks = 100, environment = "burmaldaholic:pvp_restart_salvage")
	public void unreadableRecordsKeepTheirEscrow(GameTestHelper helper) {
		ensureMode();
		MinecraftServer server = helper.getLevel().getServer();
		PvpService pvp = Pvp.service();
		PvpMatchData data = PvpMatchData.get(server);
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		ServerPlayer c = player(helper, 1000);
		ServerPlayer d = player(helper, 1000);
		ServerPlayer e = player(helper, 1000);
		ServerPlayer f = player(helper, 1000);
		// 0. a healthy lobby: refunded once (its escrow record is not paid a second time as an "orphan")
		ok(helper, pvp.openLobby(f, BAD, new JsonObject(), 40, none(f), BotSettings.HUMANS_ONLY, false), "healthy lobby");
		// 1. a DRAWN match whose record is garbage: the escrow record pays both back
		PvpMatch drawn = ok(helper, pvp.openLobby(a, BAD, new JsonObject(), 100, none(a), BotSettings.HUMANS_ONLY, false), "lobby 1");
		ok(helper, pvp.join(b, drawn.id, 0), "join 1");
		ok(helper, pvp.start(a, drawn.id), "start 1");
		helper.assertTrue(drawn.state() == MatchState.DRAWN && data.escrow().containsKey(drawn.id), "escrow stored next to the DRAWN record");
		data.put(drawn.id, "{broken");
		// 2. a LOBBY record that fails to decode and lost its escrow record: salvaged from the participants
		PvpMatch lobby = ok(helper, pvp.openLobby(c, BAD, new JsonObject(), 70, none(c), BotSettings.HUMANS_ONLY, false), "lobby 2");
		JsonObject rec = JsonParser.parseString(data.raw().get(lobby.id)).getAsJsonObject();
		rec.remove("anchor");
		data.put(lobby.id, rec.toString());
		data.removeEscrow(lobby.id);
		// 3. an unreadable record with no escrow record: quarantined, chips stay in the bank (none minted)
		PvpMatch lost = ok(helper, pvp.openLobby(d, BAD, new JsonObject(), 50, none(d), BotSettings.HUMANS_ONLY, false), "lobby 3");
		data.put(lost.id, "garbage");
		data.removeEscrow(lost.id);
		// 4. an escrow record whose match record is gone
		data.putEscrow("jadvorph", EscrowRecord.encode(BAD, List.of(EscrowRecord.Entry.player(e.getUUID(), 30))));
		Pvp.simulateRestart(server);
		helper.assertTrue(bal(a) == 1000 && bal(b) == 1000, "DRAWN escrow refunded from the escrow record: " + bal(a) + "/" + bal(b));
		helper.assertTrue(bal(c) == 1000, "salvaged lobby entry refunded: " + bal(c));
		helper.assertTrue(bal(d) == 950, "an unreadable entry is not guessed");
		helper.assertTrue(data.quarantine().containsKey(lost.id) && data.quarantine().get(lost.id).contains("garbage"),
			"the record is quarantined with its raw text for an operator");
		helper.assertTrue(bal(e) == 1030, "orphan escrow refunded");
		helper.assertTrue(bal(f) == 1000, "a healthy lobby is refunded exactly once: " + bal(f));
		for (String id : List.of(drawn.id, lobby.id, lost.id, "jadvorph")) {
			helper.assertFalse(data.raw().containsKey(id) || data.escrow().containsKey(id), "no stale record left: " + id);
		}
		helper.assertFalse(data.quarantine().containsKey(drawn.id) || data.quarantine().containsKey(lobby.id), "refunded ones not quarantined");
		helper.assertTrue(data.removeQuarantine(lost.id), "an operator can mark it resolved");
		done(helper, a, b, c, d, e, f);
		helper.succeed();
	}
}

