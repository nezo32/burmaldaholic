package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.data.OfflineMail;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.BetLimits;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.games.extras.logic.ChallengeBook;
import dev.nezo.burmaldaholic.games.extras.logic.DiceDuel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Dice Duel (GAME_DESIGN.md §11.5): vs the house (dice used on air; chips or pawn stake, always honest) and
 * PvP challenges (dice used on a player). PvP stakes are escrowed from both balances atomically only when the
 * challenge is accepted, so pending challenges hold nothing and simply vanish on disconnect / restart.
 */
public final class DiceGame {
	public static final String SCREEN = "dice";
	/** Wager gate context of a PvP duel (Asset Freeze etc. apply to both players). */
	private static final WagerVeto.Context PVP = new WagerVeto.Context(ExtrasGames.DICE_PVP, Stake.Kind.CHIPS, null, true);
	public static final String INVITE_SCREEN = "duel_invite";

	private static final ChallengeBook BOOK = new ChallengeBook();
	private static final Map<UUID, Integer> SEQ = new HashMap<>();
	/** A chat line held back until the duel's dice have landed on the screens (tables.md §3.6). */
	private record Pending(long at, UUID player, Component message) {}

	private static final List<Pending> PENDING = new ArrayList<>();
	/** GameTests / previews: forces the duel screen's location theme (-1 = by dimension). */
	public static int themeOverride = -1;

	private DiceGame() {}

	private static ExtrasConfig.DiceDuel cfg() {
		return CasinoConfig.extras().diceDuel;
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** Dice used on air: the duel screen (vs house + pending challenges + nearby opponents). */
	public static void open(ServerPlayer player, @Nullable ServerPlayer target) {
		if (!ExtrasGames.guard(player, cfg().enabled)) {
			return;
		}
		if (target != null && target != player) {
			if (!cfg().pvpEnabled) {
				player.sendOverlayMessage(ExtrasGames.error("disabled"));
				return;
			}
		}
		CompoundTag state = state(player, null);
		if (target != null && target != player) {
			state.putString("target", target.getUUID().toString());
		}
		ExtrasGames.send(player, SCREEN, true, state);
	}

	static CompoundTag state(ServerPlayer player, @Nullable CompoundTag result) {
		MinecraftServer server = player.level().getServer();
		long now = now(server);
		CompoundTag tag = new CompoundTag();
		ExtrasGames.writeLimits(tag, player, ExtrasGames.tierMax(player));
		ExtrasGames.writePawnInfo(tag, player);
		tag.putBoolean("pvp", cfg().pvpEnabled);
		tag.put("tie_on", new IntArrayTag(cfg().houseWinsTieOn.clone()));
		tag.putString("theme", new String[] {"village", "bastion", "end"}[theme(player)]);
		ListTag incoming = new ListTag();
		for (ChallengeBook.Challenge c : BOOK.incoming(player.getUUID(), now)) {
			CompoundTag ct = new CompoundTag();
			ct.putInt("id", c.id());
			ct.putString("name", nameOf(server, c.from()));
			ct.putLong("stake", c.stake());
			ct.putLong("ticks", c.expires() - now);
			incoming.add(ct);
		}
		tag.put("incoming", incoming);
		BOOK.outgoing(player.getUUID(), now).ifPresent(c -> {
			CompoundTag ot = new CompoundTag();
			ot.putString("name", nameOf(server, c.to()));
			ot.putLong("stake", c.stake());
			tag.put("outgoing", ot);
		});
		ListTag nearby = new ListTag();
		double max = cfg().maxDistance;
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player && other.level() == player.level() && other.distanceToSqr(player) <= max * max && !other.isSpectator()) {
				CompoundTag nt = new CompoundTag();
				nt.putString("uuid", other.getUUID().toString());
				nt.putString("name", other.getName().getString());
				nearby.add(nt);
			}
		}
		tag.put("nearby", nearby);
		if (result != null) {
			tag.put("result", result);
		}
		return tag;
	}

	/** Pending challenges to {@code player} (Casino Menu "Challenges" page). */
	public static List<ChallengeBook.Challenge> incoming(ServerPlayer player) {
		return BOOK.incoming(player.getUUID(), now(player.level().getServer()));
	}

	public static java.util.Optional<ChallengeBook.Challenge> outgoing(ServerPlayer player) {
		return BOOK.outgoing(player.getUUID(), now(player.level().getServer()));
	}

	static String nameOf(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		return p == null ? "?" : p.getName().getString();
	}

	public static void action(ServerPlayer player, String action, CompoundTag args) {
		if (!ExtrasGames.guard(player, cfg().enabled)) {
			return;
		}
		switch (action) {
			case "roll" -> rollHouse(player, args);
			case "challenge" -> challenge(player, args);
			case "accept" -> answer(player, args.getIntOr("id", -1), true);
			case "decline" -> answer(player, args.getIntOr("id", -1), false);
			case "refresh" -> ExtrasGames.send(player, SCREEN, false, state(player, null));
			default -> {
			}
		}
	}

	// ---- vs house -------------------------------------------------------------------------------

	private static void rollHouse(ServerPlayer player, CompoundTag args) {
		Result<Stake> taken = ExtrasGames.takeStake(player, ExtrasGames.DICE, args, 0, true);
		if (!taken.isOk()) {
			ExtrasGames.sendError(player, taken.error());
			return;
		}
		Stake stake = taken.value();
		CasinoRng rng = OddsService.get().rng(ExtrasGames.odds(player, ExtrasGames.DICE, stake.value())); // table game: fair, no streak re-draw
		DiceDuel.HouseDuel duel = DiceDuel.duelHouse(rng, cfg().houseWinsTieOn);
		Stakes.Outcome outcome = switch (duel.outcome()) {
			case WIN -> Stakes.Outcome.WIN;
			case PUSH -> Stakes.Outcome.PUSH;
			case LOSE, HOUSE_TIE -> Stakes.Outcome.LOSS;
		};
		// the cup, throw and landing sounds are played by the screen from the shared throw (tables.md §0.8)
		Stakes.settle(player, stake, outcome, stake.value());
		long net = DiceDuel.houseReturn(stake.value(), duel.outcome()) - stake.value();
		int seq = SEQ.merge(player.getUUID(), 1, Integer::sum);
		CompoundTag r = new CompoundTag();
		r.putInt("seq", seq);
		r.putInt("seed", SeedMix.mix(SeedMix.hash(player.getUUID().toString()), seq));
		r.putString("mode", "house");
		r.put("you", new IntArrayTag(new int[] {duel.player().a(), duel.player().b()}));
		r.put("them", new IntArrayTag(new int[] {duel.dealer().a(), duel.dealer().b()}));
		r.putString("outcome", duel.outcome().name().toLowerCase(java.util.Locale.ROOT));
		r.putLong("net", net);
		r.putBoolean("pawn", stake.isPawn());
		ExtrasGames.send(player, SCREEN, false, state(player, r));
	}

	// ---- PvP ------------------------------------------------------------------------------------

	private static @Nullable ServerPlayer byUuid(MinecraftServer server, String s) {
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(s));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Shared checks for both players (§11.5): enabled, casino mode, distance, debt default. */
	private static @Nullable Component pvpProblem(ServerPlayer a, @Nullable ServerPlayer b) {
		if (!cfg().pvpEnabled) {
			return ExtrasGames.error("disabled");
		}
		if (b == null || b.isRemoved() || !CasinoMode.isEnabled(b)) {
			return Component.translatable("gui.burmaldaholic.extras.dice.no_targets");
		}
		if (a.getUUID().equals(b.getUUID())) {
			return Component.translatable("msg.burmaldaholic.extras.dice.self");
		}
		double max = cfg().maxDistance;
		if (a.level() != b.level() || a.distanceToSqr(b) > max * max) {
			return ExtrasGames.error("too_far");
		}
		MinecraftServer server = a.level().getServer();
		if (CoreServices.debt().inDefault(server, a.getUUID()) || CoreServices.debt().inDefault(server, b.getUUID())) {
			return ExtrasGames.error("in_default");
		}
		return null;
	}

	private static void challenge(ServerPlayer player, CompoundTag args) {
		MinecraftServer server = player.level().getServer();
		ServerPlayer target = byUuid(server, args.getStringOr("target", ""));
		Component problem = pvpProblem(player, target);
		if (problem != null) {
			ExtrasGames.sendError(player, problem);
			return;
		}
		long stake = args.getLongOr("amount", 0);
		Component err = BetLimits.validate(player, stake, 1, 0, PVP);
		if (err != null) {
			ExtrasGames.sendError(player, err);
			return;
		}
		ChallengeBook.Created created = BOOK.create(player.getUUID(), target.getUUID(), stake, now(server), cfg().challengeTimeoutTicks);
		if (!created.ok()) {
			ExtrasGames.sendError(player, Component.translatable(created.error() == ChallengeBook.Error.SELF
				? "msg.burmaldaholic.extras.dice.self" : "msg.burmaldaholic.extras.dice.already_pending"));
			return;
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.extras.dice.challenge_sent", target.getDisplayName(), Texts.chips(stake)));
		target.sendSystemMessage(Component.translatable("msg.burmaldaholic.extras.dice.challenge_received", player.getDisplayName(), Texts.chips(stake))
			.withStyle(ChatFormatting.GOLD));
		CompoundTag invite = new CompoundTag();
		invite.putInt("id", created.challenge().id());
		invite.putString("name", player.getName().getString());
		invite.putLong("stake", stake);
		invite.putLong("ticks", cfg().challengeTimeoutTicks);
		ExtrasGames.send(target, INVITE_SCREEN, true, invite);
		ExtrasGames.send(player, SCREEN, false, state(player, null));
	}

	private static void answer(ServerPlayer player, int id, boolean accept) {
		MinecraftServer server = player.level().getServer();
		long now = now(server);
		ChallengeBook.Challenge c = BOOK.incoming(player.getUUID(), now).stream().filter(x -> x.id() == id).findFirst().orElse(null);
		if (c == null) {
			ExtrasGames.sendError(player, Component.translatable("msg.burmaldaholic.extras.dice.expired"));
			ExtrasGames.send(player, SCREEN, false, state(player, null));
			return;
		}
		BOOK.take(id, now);
		ServerPlayer challenger = server.getPlayerList().getPlayer(c.from());
		if (!accept) {
			if (challenger != null) {
				challenger.sendSystemMessage(Component.translatable("msg.burmaldaholic.extras.dice.declined", player.getDisplayName()));
			}
			ExtrasGames.send(player, SCREEN, false, state(player, null));
			return;
		}
		Component problem = pvpProblem(player, challenger);
		if (problem == null) {
			Component e1 = BetLimits.validate(challenger, c.stake(), 1, 0, PVP);
			Component e2 = BetLimits.validate(player, c.stake(), 1, 0, PVP);
			problem = e2 != null ? e2 : e1;
		}
		if (problem != null) {
			ExtrasGames.sendError(player, problem);
			if (challenger != null) {
				challenger.sendSystemMessage(problem.copy().withStyle(ChatFormatting.RED));
			}
			return;
		}
		resolvePvp(challenger, player, c.stake());
	}

	/** GameTests: a PvP duel between two players resolved at once, as if {@code b} accepted {@code a}'s challenge. */
	public static void resolvePvpForTesting(ServerPlayer a, ServerPlayer b, long stake) {
		resolvePvp(a, b, stake);
	}

	/** Escrow both stakes, roll (ties re-roll up to 3 times), pay the winner the pot minus rake or refund. */
	static void resolvePvp(ServerPlayer a, ServerPlayer b, long stake) {
		MinecraftServer server = a.level().getServer();
		Economy eco = Economies.get();
		Economy.TxResult escrow = eco.batch(server)
			.debit(AccountId.player(a.getUUID()), stake)
			.debit(AccountId.player(b.getUUID()), stake)
			.credit(AccountId.HOUSE, 2 * stake)
			.commit(Transaction.bet(ExtrasGames.DICE_PVP));
		if (!escrow.ok()) {
			Component err = ExtrasGames.error("insufficient_funds", Texts.number(eco.balance(b)));
			ExtrasGames.sendError(b, err);
			a.sendSystemMessage(err.copy().withStyle(ChatFormatting.RED));
			return;
		}
		DiceDuel.PvpDuel duel = DiceDuel.duelPvp(OddsService.get().fair(), DiceDuel.PVP_MAX_ROLLS);
		DiceDuel.PvpRound last = duel.rounds().get(duel.rounds().size() - 1);
		// text trails the dice (tables.md §3.6): each round's lines at its reveal on the screens; the money settles now
		long start = now(server);
		int seed = SeedMix.mix(SeedMix.hash(a.getUUID().toString()), SeedMix.hash(b.getUUID().toString()), (int) start);
		for (int i = 0; i < duel.rounds().size(); i++) {
			DiceDuel.PvpRound round = duel.rounds().get(i);
			long at = start + DuelTimeline.revealTick(i);
			rollLines(at, a, b, round.a(), round.b());
			rollLines(at, b, a, round.b(), round.a());
		}
		long end = start + DuelTimeline.revealTick(duel.rounds().size() - 1);
		DuelStage.start(a, b, duel, seed, start); // the dice between the two players (tables.md §3.4)
		if (duel.result() == DiceDuel.PvpResult.REFUND) {
			eco.batch(server).debit(AccountId.HOUSE, 2 * stake)
				.credit(AccountId.player(a.getUUID()), stake).credit(AccountId.player(b.getUUID()), stake)
				.commit(Transaction.refund(ExtrasGames.DICE_PVP));
			Component msg = Component.translatable("msg.burmaldaholic.extras.dice.pvp_refund").withStyle(ChatFormatting.GRAY);
			later(end, a, msg);
			later(end, b, msg);
			sendPvpResult(a, duel, true, 0, b, seed);
			sendPvpResult(b, duel, false, 0, a, seed);
			return;
		}
		ServerPlayer winner = duel.result() == DiceDuel.PvpResult.A ? a : b;
		ServerPlayer loser = winner == a ? b : a;
		DiceDuel.PvpPayout pay = DiceDuel.pvpPayout(stake, cfg().pvpRakePercent);
		eco.deposit(winner, pay.winnerGets(), Transaction.payout(ExtrasGames.DICE_PVP));
		Component msg = Component.translatable("msg.burmaldaholic.extras.dice.pvp_result", winner.getDisplayName(), loser.getDisplayName(), Texts.chipsAcc(pay.winnerGets()))
			.withStyle(ChatFormatting.GOLD);
		later(end, a, msg);
		later(end, b, msg);
		// PvP: no house edge, no Golden Hour, no cashback (§12/§13.3).
		PlayResults.fire(winner, CasinoEvents.PlayResult.of(ExtrasGames.DICE_PVP, stake, pay.winnerGets()).pvp());
		PlayResults.fire(loser, CasinoEvents.PlayResult.of(ExtrasGames.DICE_PVP, stake, 0).pvp());
		sendPvpResult(a, duel, true, winner == a ? pay.winnerGets() - stake : -stake, b, seed);
		sendPvpResult(b, duel, false, winner == b ? pay.winnerGets() - stake : -stake, a, seed);
	}

	private static void later(long at, ServerPlayer player, Component message) {
		PENDING.add(new Pending(at, player.getUUID(), message));
	}

	private static void rollLines(long at, ServerPlayer viewer, ServerPlayer other, DiceDuel.Roll mine, DiceDuel.Roll theirs) {
		later(at, viewer, Component.translatable("gui.burmaldaholic.extras.dice.your_roll",
			Texts.number(mine.a()), Texts.number(mine.b()), Texts.number(mine.total())));
		later(at, viewer, Component.translatable("gui.burmaldaholic.extras.dice.their_roll", other.getDisplayName(),
			Texts.number(theirs.a()), Texts.number(theirs.b()), Texts.number(theirs.total())));
	}

	/**
	 * The PvP result for one side: every round (tables.md §3.6 {@code rounds}), so the screen shows each tie and its
	 * re-roll; {@code you} / {@code them} keep the last round for older readers.
	 */
	private static void sendPvpResult(ServerPlayer p, DiceDuel.PvpDuel duel, boolean sideA, long net, ServerPlayer other, int seed) {
		int seq = SEQ.merge(p.getUUID(), 1, Integer::sum);
		CompoundTag r = new CompoundTag();
		r.putInt("seq", seq);
		r.putInt("seed", seed);
		r.putString("mode", "pvp");
		r.putString("opponent", other.getName().getString());
		ListTag rounds = new ListTag();
		DiceDuel.Roll mine = null;
		DiceDuel.Roll theirs = null;
		for (DiceDuel.PvpRound round : duel.rounds()) {
			mine = sideA ? round.a() : round.b();
			theirs = sideA ? round.b() : round.a();
			CompoundTag rt = new CompoundTag();
			rt.put("you", new IntArrayTag(new int[] {mine.a(), mine.b()}));
			rt.put("them", new IntArrayTag(new int[] {theirs.a(), theirs.b()}));
			rounds.add(rt);
		}
		r.put("rounds", rounds);
		r.put("you", new IntArrayTag(new int[] {mine.a(), mine.b()}));
		r.put("them", new IntArrayTag(new int[] {theirs.a(), theirs.b()}));
		r.putString("outcome", net > 0 ? "win" : net < 0 ? "lose" : "refund");
		r.putLong("net", net);
		ExtrasGames.send(p, SCREEN, false, state(p, r));
	}

	/** Location theme of the duel screen (visual/tables.md §2.1): Nether → Piglin Parlor, End → lounge, else village. */
	static int theme(ServerPlayer player) {
		if (themeOverride >= 0) {
			return themeOverride;
		}
		if (player.level().dimension() == Level.NETHER) {
			return 1;
		}
		return player.level().dimension() == Level.END ? 2 : 0;
	}

	// ---- housekeeping ---------------------------------------------------------------------------

	public static void tick(MinecraftServer server) {
		flushPending(server, false);
		DuelStage.tick(server);
		if (BOOK.size() == 0 || server.getTickCount() % 20 != 0) {
			return;
		}
		List<ChallengeBook.Challenge> expired = BOOK.expire(now(server));
		for (ChallengeBook.Challenge c : expired) {
			ServerPlayer from = server.getPlayerList().getPlayer(c.from());
			if (from != null) {
				from.sendSystemMessage(Component.translatable("msg.burmaldaholic.extras.dice.expired").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	public static void forget(UUID player) {
		BOOK.dropPlayer(player);
		SEQ.remove(player);
	}

	/**
	 * Posts the held-back duel lines that are due ({@code all}: every one, the server stops). A player who went offline
	 * meanwhile gets them on the next join ({@link OfflineMail}): a line is never lost.
	 */
	public static void flushPending(MinecraftServer server, boolean all) {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = now(server);
		List<Pending> due = new ArrayList<>();
		PENDING.removeIf(m -> {
			if (!all && m.at() > now) {
				return false;
			}
			due.add(m);
			return true;
		});
		due.forEach(m -> OfflineMail.line(server, m.player(), m.message()));
	}

	/** Held-back duel lines not posted yet (tests). */
	public static int pendingLines() {
		return PENDING.size();
	}

	public static void clear() {
		BOOK.clear();
		SEQ.clear();
		PENDING.clear();
		DuelStage.clear();
	}
}
