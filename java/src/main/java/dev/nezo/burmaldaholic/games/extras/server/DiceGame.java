package dev.nezo.burmaldaholic.games.extras.server;

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
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.ChallengeBook;
import dev.nezo.burmaldaholic.games.extras.logic.DiceDuel;
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
import org.jspecify.annotations.Nullable;

/**
 * Dice Duel (GAME_DESIGN.md §11.5): vs the house (dice used on air; chips or pawn stake, always honest) and
 * PvP challenges (dice used on a player). PvP stakes are escrowed from both balances atomically only when the
 * challenge is accepted, so pending challenges hold nothing and simply vanish on disconnect / restart.
 */
public final class DiceGame {
	public static final String SCREEN = "dice";
	public static final String INVITE_SCREEN = "duel_invite";

	private static final ChallengeBook BOOK = new ChallengeBook();
	private static final Map<UUID, Integer> SEQ = new HashMap<>();

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

	private static String nameOf(MinecraftServer server, UUID id) {
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
		ExtrasGames.playSound(player, ExtrasModule.DICE_ROLL_SOUND, 1.0f);
		Stakes.settle(player, stake, outcome, stake.value());
		long net = DiceDuel.houseReturn(stake.value(), duel.outcome()) - stake.value();
		int seq = SEQ.merge(player.getUUID(), 1, Integer::sum);
		CompoundTag r = new CompoundTag();
		r.putInt("seq", seq);
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
		Component err = BetLimits.validate(player, stake, 1, 0);
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
			Component e1 = BetLimits.validate(challenger, c.stake(), 1, 0);
			Component e2 = BetLimits.validate(player, c.stake(), 1, 0);
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
		ExtrasGames.playSound(a, ExtrasModule.DICE_ROLL_SOUND, 1.0f);
		ExtrasGames.playSound(b, ExtrasModule.DICE_ROLL_SOUND, 1.0f);
		DiceDuel.PvpDuel duel = DiceDuel.duelPvp(OddsService.get().fair(), DiceDuel.PVP_MAX_ROLLS);
		DiceDuel.PvpRound last = duel.rounds().get(duel.rounds().size() - 1);
		for (DiceDuel.PvpRound round : duel.rounds()) {
			rollLines(a, b, round.a(), round.b());
			rollLines(b, a, round.b(), round.a());
		}
		if (duel.result() == DiceDuel.PvpResult.REFUND) {
			eco.batch(server).debit(AccountId.HOUSE, 2 * stake)
				.credit(AccountId.player(a.getUUID()), stake).credit(AccountId.player(b.getUUID()), stake)
				.commit(Transaction.refund(ExtrasGames.DICE_PVP));
			Component msg = Component.translatable("msg.burmaldaholic.extras.dice.pvp_refund").withStyle(ChatFormatting.GRAY);
			a.sendSystemMessage(msg);
			b.sendSystemMessage(msg);
			sendPvpResult(a, last.a(), last.b(), 0, b);
			sendPvpResult(b, last.b(), last.a(), 0, a);
			return;
		}
		ServerPlayer winner = duel.result() == DiceDuel.PvpResult.A ? a : b;
		ServerPlayer loser = winner == a ? b : a;
		DiceDuel.PvpPayout pay = DiceDuel.pvpPayout(stake, cfg().pvpRakePercent);
		eco.deposit(winner, pay.winnerGets(), Transaction.payout(ExtrasGames.DICE_PVP));
		Component msg = Component.translatable("msg.burmaldaholic.extras.dice.pvp_result", winner.getDisplayName(), loser.getDisplayName(), Texts.chipsAcc(pay.winnerGets()))
			.withStyle(ChatFormatting.GOLD);
		a.sendSystemMessage(msg);
		b.sendSystemMessage(msg);
		CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(winner, new CasinoEvents.PlayResult(ExtrasGames.DICE_PVP, stake, pay.winnerGets()));
		CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(loser, new CasinoEvents.PlayResult(ExtrasGames.DICE_PVP, stake, 0));
		sendPvpResult(a, last.a(), last.b(), winner == a ? pay.winnerGets() - stake : -stake, b);
		sendPvpResult(b, last.b(), last.a(), winner == b ? pay.winnerGets() - stake : -stake, a);
	}

	private static void rollLines(ServerPlayer viewer, ServerPlayer other, DiceDuel.Roll mine, DiceDuel.Roll theirs) {
		viewer.sendSystemMessage(Component.translatable("gui.burmaldaholic.extras.dice.your_roll",
			Texts.number(mine.a()), Texts.number(mine.b()), Texts.number(mine.total())));
		viewer.sendSystemMessage(Component.translatable("gui.burmaldaholic.extras.dice.their_roll", other.getDisplayName(),
			Texts.number(theirs.a()), Texts.number(theirs.b()), Texts.number(theirs.total())));
	}

	private static void sendPvpResult(ServerPlayer p, DiceDuel.Roll mine, DiceDuel.Roll theirs, long net, ServerPlayer other) {
		int seq = SEQ.merge(p.getUUID(), 1, Integer::sum);
		CompoundTag r = new CompoundTag();
		r.putInt("seq", seq);
		r.putString("mode", "pvp");
		r.putString("opponent", other.getName().getString());
		r.put("you", new IntArrayTag(new int[] {mine.a(), mine.b()}));
		r.put("them", new IntArrayTag(new int[] {theirs.a(), theirs.b()}));
		r.putString("outcome", net > 0 ? "win" : net < 0 ? "lose" : "refund");
		r.putLong("net", net);
		ExtrasGames.send(p, SCREEN, false, state(p, r));
	}

	// ---- housekeeping ---------------------------------------------------------------------------

	public static void tick(MinecraftServer server) {
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

	public static void clear() {
		BOOK.clear();
		SEQ.clear();
	}
}
