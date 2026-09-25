package dev.nezo.burmaldaholic.gametest.pvp;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpMatchData;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.Settlement;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelMode;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownMode;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyMode;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode;
import java.util.ArrayList;
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
 * End-to-end flows of the five PvP modes on the real engine and the real mode implementations (PVP.md §16,
 * BOTS.md §5, Bedrock wave-2 review fixes): Coin Flip Duel Double-or-nothing chains (stale answers dropped),
 * Wheel Party / Slot Showdown / Plinko Battle / Scratch Showdown with house bots, restart and casino-off
 * play-outs, bots filling and yielding seats, a charter breaking mid-match (late chips follow the tombstone
 * to an OFFLINE owner) — with money conservation checked in every flow.
 */
public class PvpIntegrationGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");

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

	private static long bal(MinecraftServer s, UUID id) {
		return Economies.get().balance(s, id);
	}

	private static PvpService.Anchor none(ServerPlayer p) {
		return new PvpService.Anchor(AnchorKind.NONE, (ServerLevel) p.level(), p.blockPosition());
	}

	private static PvpMatch ok(GameTestHelper helper, Result<PvpMatch> r, String what) {
		helper.assertTrue(r.isOk(), what + ": " + (r.error() == null ? "" : r.error().getString()));
		return r.value();
	}

	private static BotSettings bots(SeatPolicy policy, int count) {
		return new BotSettings(policy, count, BotDifficulty.NORMAL, false, true, BotSpeed.INSTANT);
	}

	/** The human's net of a settled match: payout − stake. */
	private static long net(PvpMatch m, ServerPlayer p) {
		for (Participant x : m.participants()) {
			if (x.occupant instanceof SeatOccupant.Human h && h.id().equals(p.getUUID())) {
				return m.payouts()[x.index] - x.stake();
			}
		}
		throw new IllegalStateException("not in the match");
	}

	private static void done(GameTestHelper helper, ServerPlayer... players) {
		MinecraftServer s = helper.getLevel().getServer();
		for (ServerPlayer p : players) {
			Pvp.service().leave(p);
			s.getPlayerList().remove(p);
		}
	}

	private static JsonElement plinko(String risk, int balls) {
		PlinkoBattleMode m = new PlinkoBattleMode();
		return m.encodeParams(new PlinkoBattleMode.Params(risk, balls));
	}

	private static JsonElement scratch() {
		ScratchShowdownMode m = new ScratchShowdownMode();
		return m.encodeParams(m.defaults());
	}

	private static JsonElement slots(Tier tier, int spins) {
		SlotShowdownMode m = new SlotShowdownMode();
		SlotShowdownMode.Params d = m.defaults(tier);
		return m.encodeParams(new SlotShowdownMode.Params(d.tier(), spins, d.rules()));
	}

	// ---- Coin Flip Duel: Double-or-nothing chain between two humans ----------------------------------------

	/** C-chain: flip, the loser calls Double or nothing (a stale answer is dropped), the winner lets it ride, the next link settles. */
	@GameTest(maxTicks = 2400)
	public void coinDoubleOrNothingChain(GameTestHelper helper) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 5000);
		ServerPlayer b = player(helper, 5000);
		CoinDuelMode mode = new CoinDuelMode();
		long start = bal(a) + bal(b);
		PvpMatch first = ok(helper, pvp.challenge(a, "coin", mode.encodeParams(new CoinDuelMode.Params(100, true)), 100,
			new PvpService.Opponent.PlayerTarget(b.getUUID())), "challenge");
		helper.assertTrue(first.state() == MatchState.INVITED && bal(a) == 5000, "an invite escrows nothing");
		helper.assertTrue(pvp.invitesFor(b.getUUID()).contains(first), "b sees the invite");
		ok(helper, pvp.accept(b, first.id), "accept");
		helper.assertTrue(bal(a) == 4900 && bal(b) == 4900, "both stakes escrowed at accept");
		List<PvpMatch> links = new ArrayList<>(List.of(first));
		boolean[] staleTried = {false};
		helper.succeedWhen(() -> {
			for (ServerPlayer p : links.size() > 1 ? List.<ServerPlayer>of() : List.of(a, b)) {
				Optional<DecisionView> d = pvp.decisionFor(p.getUUID());
				if (d.isEmpty()) {
					continue;
				}
				PvpMatch m = pvp.matchOf(p.getUUID()).orElseThrow();
				if ("coin.don_offer".equals(d.get().decision())) {
					if (!staleTried[0]) {
						staleTried[0] = true;
						pvp.decide(p, "coin.don_offer", 0, m.id, m.decisionSeq() + 7); // a stale form: dropped (m2)
						helper.assertTrue(pvp.decisionFor(p.getUUID()).isPresent(), "a stale answer changes nothing");
					}
					pvp.decide(p, "coin.don_offer", 1, m.id, m.decisionSeq());
				} else if ("coin.let_it_ride".equals(d.get().decision())) {
					pvp.decide(p, "coin.let_it_ride", 1, m.id, m.decisionSeq());
				}
			}
			for (PvpMatch m : pvp.all()) {
				if (!links.contains(m) && first.id.equals(m.chainOf)) {
					links.add(m);
				}
			}
			helper.assertTrue(links.size() >= 2, "a second link was played");
			PvpMatch second = links.get(1);
			helper.assertTrue(second.link == 2 && second.state() == MatchState.SETTLED, "second link settled");
			helper.assertTrue(second.participants().get(0).stake() == second.participants().get(1).stake() && second.pot() > 0, "equal D stakes");
			long rakes = 0;
			for (PvpMatch m : links) {
				if (m.state() == MatchState.SETTLED) {
					rakes += m.rake();
				} else {
					throw new AssertionError("link " + m.link + " not settled");
				}
			}
			helper.assertTrue(bal(a) + bal(b) == start - rakes, "A + B = start − Σ rake over the chain: " + (bal(a) + bal(b)) + " vs " + (start - rakes));
			helper.assertTrue(staleTried[0], "the stale-answer check ran");
			done(helper, a, b);
		});
	}

	// ---- Wheel Party with bots (host Start seats MIXED bots, No more bets, spin, settle) ---------------------

	@GameTest(maxTicks = 1200)
	public void wheelPartyWithBots(GameTestHelper helper) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 5000);
		PvpMatch m = ok(helper, pvp.openLobby(a, "wheel", new WheelPartyMode().encodeParams(new WheelPartyMode.Params(500)), 60, none(a),
			bots(SeatPolicy.MIXED, 3), false), "open wheel");
		helper.assertTrue(bal(a) == 4940, "host stake escrowed");
		ok(helper, pvp.start(a, m.id), "host spins now (bots fill first)");
		helper.assertTrue(m.participants().stream().filter(Participant::isBot).count() == 3, "3 bots seated");
		helper.assertTrue(m.participants().stream().filter(Participant::isBot).allMatch(p -> p.stake() >= CasinoConfig.pvp().minStake && p.stake() <= 500),
			"bot stakes within [min, cap]");
		helper.succeedWhen(() -> {
			helper.assertTrue(m.state() == MatchState.SETTLED, "spun and settled");
			helper.assertTrue(bal(a) == 5000 + net(m, a), "human: −stake + payout, bots move no chips (bank)");
			Outcome o = m.outcome();
			helper.assertTrue(o != null && o.winners().length == 1, "one slice wins");
			done(helper, a);
		});
	}

	// ---- Slot Showdown / Plinko Battle / Scratch Showdown vs house bots --------------------------------------

	@GameTest(maxTicks = 3000)
	public void slotShowdownBotsOnly(GameTestHelper helper) {
		runBotsOnly(helper, "slots", slots(Tier.GOLD, 3), 3);
	}

	@GameTest(maxTicks = 3000)
	public void plinkoBattleBotsOnly(GameTestHelper helper) {
		runBotsOnly(helper, "plinko", plinko("medium", 3), 3);
	}

	@GameTest(maxTicks = 3000)
	public void scratchShowdownVsBot(GameTestHelper helper) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 2000);
		PvpMatch m = ok(helper, pvp.challenge(a, "scratch", scratch(), 50, new PvpService.Opponent.BotTarget(BotDifficulty.HARD)), "duel a bot");
		helper.succeedWhen(() -> {
			pvp.press(a); // Scratch! only speeds the reveal up
			helper.assertTrue(m.state() == MatchState.SETTLED, "bot accepted, card revealed, settled");
			helper.assertTrue(bal(a) == 2000 + net(m, a), "stake at accept, payout at settle");
			done(helper, a);
		});
	}

	private static void runBotsOnly(GameTestHelper helper, String mode, JsonElement params, int botCount) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 3000);
		PvpMatch m = ok(helper, pvp.openLobby(a, mode, params, 40, none(a), bots(SeatPolicy.BOTS_ONLY, botCount), false), mode + " bots only");
		helper.assertTrue(m.state() == MatchState.DRAWN, "BOTS_ONLY starts at once");
		helper.assertTrue(m.participants().size() == botCount + 1, "bots seated: " + m.participants().size());
		helper.assertTrue(m.outcome() == null, "outcome hidden during the reveal");
		helper.succeedWhen(() -> {
			pvp.press(a); // Spin! / Drop! (bots press after their think time)
			helper.assertTrue(m.state() == MatchState.SETTLED, mode + " settled by the timeline");
			helper.assertTrue(bal(a) == 3000 + net(m, a), mode + ": the human pays its stake and gets its payout, nothing else");
			long paid = 0;
			for (long x : m.payouts()) {
				paid += x;
			}
			helper.assertTrue(paid + m.rake() == m.pot(), "payouts + rake = pot");
			done(helper, a);
		});
	}

	// ---- restart / casino off play-outs with real modes ------------------------------------------------------

	/** Own environment (a separate batch): a simulated restart and casino mode off touch every live match on the server. */
	@GameTest(maxTicks = 400, environment = "burmaldaholic:pvp_restart_modes")
	public void restartAndCasinoOffPlayOut(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 4000);
		ServerPlayer b = player(helper, 4000);
		long start = bal(a) + bal(b);
		// a DRAWN Plinko Battle and an open Slot Showdown lobby, then a "crash": DRAWN settles from the tape, LOBBY refunds
		PvpMatch drawn = ok(helper, pvp.openLobby(a, "plinko", plinko("high", 5), 100, none(a), BotSettings.HUMANS_ONLY, false), "plinko");
		ok(helper, pvp.join(b, drawn.id, 0), "join plinko");
		ok(helper, pvp.start(a, drawn.id), "start plinko");
		helper.assertTrue(drawn.state() == MatchState.DRAWN && PvpMatchData.get(server).raw().get(drawn.id).contains("\"row\""),
			"DRAWN persisted with the params snapshot (points row)");
		Pvp.simulateRestart(server);
		PvpMatch replay = pvp.get(drawn.id).orElseThrow();
		helper.assertTrue(replay.state() == MatchState.SETTLED, "settled from the tape on load");
		long afterRestart = bal(a) + bal(b);
		helper.assertTrue(afterRestart == start - replay.rake(), "restart conserves chips");
		// casino mode off mid-match: a Scratch Showdown in its reveal settles at once, a Wheel lobby refunds
		PvpMatch live = ok(helper, pvp.openLobby(a, "scratch", scratch(), 30, none(a), BotSettings.HUMANS_ONLY, false), "scratch lobby");
		ok(helper, pvp.join(b, live.id, 0), "join scratch");
		ok(helper, pvp.start(a, live.id), "start scratch");
		ServerPlayer c = player(helper, 1000);
		PvpMatch wheel = ok(helper, pvp.openLobby(c, "wheel", new WheelPartyMode().encodeParams(new WheelPartyMode.Params(200)), 70, none(c),
			BotSettings.HUMANS_ONLY, false), "wheel lobby");
		helper.assertTrue(bal(c) == 930, "wheel stake escrowed");
		CasinoMode.set(server, false);
		CasinoMode.set(server, true);
		helper.assertTrue(live.state() == MatchState.SETTLED, "reveal settled at casino off");
		helper.assertTrue(pvp.get(wheel.id).isEmpty() && bal(c) == 1000, "lobby refunded at casino off");
		helper.assertTrue(bal(a) + bal(b) == afterRestart - live.rake(), "casino off conserves chips");
		done(helper, a, b, c);
		helper.succeed();
	}

	// ---- bots fill, then yield to a joining human (escrow first, review m7) ----------------------------------

	/** A full Wheel Party (host + bots) during its countdown: a joining human takes the last bot's seat after paying. */
	@GameTest(maxTicks = 1500)
	public void botsFillThenYieldToHuman(GameTestHelper helper) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 2000);
		ServerPlayer b = player(helper, 2000);
		ServerPlayer poor = player(helper, 5);
		var wheelCfg = CasinoConfig.pvp().wheel;
		int maxBefore = wheelCfg.maxPlayers;
		PvpMatch m;
		try {
			wheelCfg.maxPlayers = 3; // synchronous section: a 3-seat wheel the bots can fill
			m = ok(helper, pvp.openLobby(a, "wheel", new WheelPartyMode().encodeParams(new WheelPartyMode.Params(200)), 60, none(a),
				new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, false, true, BotSpeed.INSTANT), false), "mixed wheel");
			pvp.fillWithBots(a, m.id);
			helper.assertTrue(m.participants().size() == 3 && m.participants().stream().filter(Participant::isBot).count() == 2,
				"2 bots fill the wheel: " + m.participants().size());
			helper.assertTrue(m.noMoreBetsTick() > 0, "the countdown runs");
			helper.assertFalse(pvp.join(poor, m.id, 50).isOk(), "5 chips can't pay 50");
			helper.assertTrue(m.participants().stream().filter(Participant::isBot).count() == 2 && bal(poor) == 5, "a failed join yields no bot, takes nothing");
			ok(helper, pvp.join(b, m.id, 50), "b joins the full wheel");
			helper.assertTrue(m.participants().size() == 3 && m.participants().stream().filter(Participant::isBot).count() == 1, "the last bot yielded its seat");
			helper.assertTrue(bal(b) == 1950, "b escrowed its stake");
		} finally {
			wheelCfg.maxPlayers = maxBefore;
		}
		PvpMatch match = m;
		helper.succeedWhen(() -> {
			helper.assertTrue(match.state() == MatchState.SETTLED, "spun and settled");
			helper.assertTrue(bal(a) + bal(b) == 4000 + net(match, a) + net(match, b), "humans: stakes out, payouts in");
			done(helper, a, b, poor);
		});
	}

	// ---- charter break mid-match: late chips follow the tombstone to the (offline) owner --------------------

	@GameTest(maxTicks = 3000)
	public void charterBreakMidMatchPaysTheOfflineOwner(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		PvpService pvp = Pvp.service();
		Economy eco = Economies.get();
		BlockPos machine = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos machine2 = helper.absolutePos(new BlockPos(3, 1, 1));
		level.setBlockAndUpdate(machine, Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(machine2, Blocks.STONE.defaultBlockState());
		UUID owner = UUID.randomUUID(); // never online
		String bankroll = "jint2_charter_" + UUID.randomUUID();
		eco.bankrolls(server).open(bankroll, owner);
		eco.transfer(server, AccountId.HOUSE, AccountId.bankroll(bankroll), 10_000, TEST);
		TableOwnershipProvider prev = CoreServices.tableOwnership();
		OwnedTable table = new OwnedTable(owner, bankroll, 0, 0, true);
		CoreServices.setTableOwnership((l, p) -> p.equals(machine) || p.equals(machine2) ? Optional.of(table) : prev.owner(l, p));
		ServerPlayer a = player(helper, 3000);
		ServerPlayer b = player(helper, 3000);
		PvpMatch m;
		PvpMatch waiting;
		try {
			// a drawn Plinko Battle with 2 owner-funded bots, and a waiting lobby holding 2 owner-funded bot entries
			m = ok(helper, pvp.openLobby(a, "plinko", plinko("low", 3), 100, new PvpService.Anchor(AnchorKind.PLINKO_MACHINE, level, machine),
				bots(SeatPolicy.BOTS_ONLY, 2), false), "owned bots-only");
			waiting = ok(helper, pvp.openLobby(b, "scratch", scratch(), 80, new PvpService.Anchor(AnchorKind.PLINKO_MACHINE, level, machine2),
				new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, false, true, BotSpeed.INSTANT), false), "owned mixed lobby");
			pvp.fillWithBots(b, waiting.id);
		} finally {
			// the charter breaks: the table is no longer owned, the bankroll is paid out and closed (multiplayer processClosing)
			CoreServices.setTableOwnership(prev);
		}
		helper.assertTrue(m.state() == MatchState.DRAWN, "drawn before the break");
		helper.assertTrue(m.participants().stream().filter(Participant::isBot).allMatch(p -> ((SeatOccupant.Bot) p.occupant).purse().kind() == Purse.Kind.BANKROLL),
			"bots funded by the owner's bankroll");
		long waitingBots = waiting.participants().stream().filter(Participant::isBot).mapToLong(Participant::stake).sum();
		long left = eco.bankrolls(server).get(bankroll).orElseThrow().balance();
		helper.assertTrue(left == 10_000 - 200 - waitingBots, "bot entries moved from the bankroll to the bank escrow");
		eco.transfer(server, AccountId.bankroll(bankroll), AccountId.player(owner), left, TEST);
		eco.bankrolls(server).close(bankroll);
		long ownerAtClose = bal(server, owner);
		helper.assertTrue(ownerAtClose == left, "owner paid the rest at close");
		helper.assertTrue(eco.bankrolls(server).closedOwner(bankroll).orElseThrow().equals(owner), "tombstoned to the owner");
		long bStart = 3000;
		helper.succeedWhen(() -> {
			pvp.press(a);
			helper.assertTrue(m.state() == MatchState.SETTLED, "the match played out after the break");
			helper.assertTrue(pvp.get(waiting.id).isEmpty(), "the lobby at the closed casino was cancelled (anchor watch)");
			helper.assertTrue(eco.bankrolls(server).get(bankroll).isEmpty(), "the bankroll is never re-created by late chips");
			// every chip the bots and the rake owed the closed bankroll reached the offline owner
			List<Settlement.Seat> seats = new ArrayList<>();
			long botPayouts = 0;
			for (Participant p : m.participants()) {
				seats.add(p.isBot() ? Settlement.Seat.bankrollBot(p.stake()) : Settlement.Seat.human(p.stake()));
				if (p.isBot()) {
					botPayouts += m.payouts()[p.index];
				}
			}
			Outcome o = m.outcome();
			Settlement.Result r = Settlement.settle(seats, o.winners(), o.seatOrder(), CasinoConfig.pvp().rakeBasisPoints, true);
			long expected = ownerAtClose + botPayouts + r.rakeToBankroll() + waitingBots;
			helper.assertTrue(bal(server, owner) == expected, "owner = paid at close + bot payouts + rake share + refunded bot entries: "
				+ bal(server, owner) + " vs " + expected);
			helper.assertTrue(bal(a) == 3000 + net(m, a), "the human settled normally");
			helper.assertTrue(bal(b) == bStart, "the lobby host was refunded");
			done(helper, a, b);
		});
	}
}
