package dev.nezo.burmaldaholic.games.craps.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Craps atmosphere bettors (BOTS.md §4.7, pvp-bots.md §4.6). PURE; mirrors Bedrock
 * {@code games/craps/logic/bots.ts}.
 *
 * <p>Every craps bet has its fixed edge, so a bot only has a BETTING STYLE from its personality
 * (difficulty hidden). Bots NEVER shoot (BOTS.md §4.7, no config switch): the shooter rotation only sees
 * human seats, so a seven-out never depends on a bot. Their bets are VIRTUAL and live in a shadow
 * {@link CrapsTable} ({@link CrapsBotTable}) that mirrors the real puck; each real roll resolves them with
 * the same dice — no extra dice are thrown, nothing touches a stake. Every choice uses the BOT rng.
 * <pre>
 *   ROCK    Dark Side          Don't Pass + lay odds
 *   STATION Field Fan          Field every roll
 *   MANIAC  Come Ladder        Pass + every Come + odds
 *   TAG     Right-Way Grinder  Pass + full 3-4-5× odds
 *   LAG     Pass and Field     Pass + Field + odds
 * </pre>
 * Flat amounts {@code min + k × min} with k = 1–2 (ROCK, STATION), 2–5 (TAG), 5–10 (MANIAC, LAG).
 */
public final class CrapsBettor implements BotPolicy<CrapsBettor.View, List<CrapsBettor.Action>> {
	public static final CrapsBettor INSTANCE = new CrapsBettor();

	/** A bot's move: a new flat bet, or odds behind one of its bets. */
	public sealed interface Action {
		record Flat(BetKind kind, long amount) implements Action {}

		record Odds(int betId, long amount) implements Action {}
	}

	/** Odds room behind a bet: how much more may go on ({@code room}, snapped) in multiples of {@code unit}. */
	public record Room(long room, int unit) {}

	/**
	 * @param point   the real table's puck (0 = OFF, come-out)
	 * @param bets    this bot's working bets
	 * @param oddsRoom odds room per bet id, from the shadow table
	 */
	public record View(int point, List<Bet> bets, long minBet, CrapsRules rules, Map<Integer, Room> oddsRoom) {
		boolean comeOut() {
			return point == 0;
		}
	}

	private CrapsBettor() {}

	public static int[] kRange(Personality p) {
		return switch (p) {
			case ROCK, STATION -> new int[] {1, 2};
			case TAG -> new int[] {2, 5};
			case MANIAC, LAG -> new int[] {5, 10};
		};
	}

	private static long flat(BotRng rng, Personality p, long min) {
		int[] k = kRange(p);
		return min + rng.between(k[0], k[1]) * min;
	}

	/** Has a bet of {@code kind} ({@code pending}: only one that has not travelled to a point yet). */
	private static boolean has(List<Bet> bets, BetKind kind, boolean pending) {
		return bets.stream().anyMatch(b -> b.kind() == kind && (!pending || b.point() == 0));
	}

	/** Full odds behind every eligible bet of {@code kinds}. */
	private static List<Action> fullOdds(View view, Set<BetKind> kinds) {
		List<Action> out = new ArrayList<>();
		for (Bet b : view.bets()) {
			if (!kinds.contains(b.kind())) {
				continue;
			}
			Room o = view.oddsRoom().get(b.id());
			if (o != null && o.room() >= o.unit()) {
				out.add(new Action.Odds(b.id(), o.room()));
			}
		}
		return out;
	}

	@Override
	public List<Action> decide(BotProfile bot, View view, Object work, BotRng rng) {
		boolean comeOut = view.comeOut();
		long min = Math.max(1, view.minBet());
		Personality p = bot.personality();
		List<Action> out = new ArrayList<>();
		switch (p) {
			case ROCK -> {
				if (comeOut && !has(view.bets(), BetKind.DONT_PASS, false)) {
					out.add(new Action.Flat(BetKind.DONT_PASS, flat(rng, p, min)));
				}
				out.addAll(fullOdds(view, EnumSet.of(BetKind.DONT_PASS)));
			}
			case STATION -> {
				if (!has(view.bets(), BetKind.FIELD, false)) {
					out.add(new Action.Flat(BetKind.FIELD, flat(rng, p, min)));
				}
			}
			case MANIAC -> {
				if (comeOut && !has(view.bets(), BetKind.PASS, false)) {
					out.add(new Action.Flat(BetKind.PASS, flat(rng, p, min)));
				}
				if (!comeOut && !has(view.bets(), BetKind.COME, true)) {
					out.add(new Action.Flat(BetKind.COME, flat(rng, p, min)));
				}
				out.addAll(fullOdds(view, EnumSet.of(BetKind.PASS, BetKind.COME)));
			}
			case TAG -> {
				if (comeOut && !has(view.bets(), BetKind.PASS, false)) {
					out.add(new Action.Flat(BetKind.PASS, flat(rng, p, min)));
				}
				out.addAll(fullOdds(view, EnumSet.of(BetKind.PASS)));
			}
			case LAG -> {
				if (comeOut && !has(view.bets(), BetKind.PASS, false)) {
					out.add(new Action.Flat(BetKind.PASS, flat(rng, p, min)));
				}
				if (!has(view.bets(), BetKind.FIELD, false)) {
					out.add(new Action.Flat(BetKind.FIELD, flat(rng, p, min)));
				}
				out.addAll(fullOdds(view, EnumSet.of(BetKind.PASS)));
			}
		}
		return out;
	}

	/** Line bets only on the come-out, Come only with a point, one of each; odds snapped within the max. */
	@Override
	public List<Action> legalize(View view, List<Action> actions) {
		boolean comeOut = view.comeOut();
		long min = Math.max(1, view.minBet());
		Set<BetKind> placed = EnumSet.noneOf(BetKind.class);
		List<Action> out = new ArrayList<>();
		for (Action a : actions) {
			switch (a) {
				case Action.Flat f -> {
					boolean line = f.kind().isLine();
					boolean come = f.kind().isCome();
					if ((line && !comeOut) || (come && comeOut) || placed.contains(f.kind())) {
						continue;
					}
					if ((line || f.kind() == BetKind.FIELD) && has(view.bets(), f.kind(), false)) {
						continue;
					}
					if (come && has(view.bets(), f.kind(), true)) {
						continue;
					}
					placed.add(f.kind());
					out.add(new Action.Flat(f.kind(), Math.max(min, f.amount())));
				}
				case Action.Odds o -> {
					Room r = view.oddsRoom().get(o.betId());
					long amount = r == null ? 0 : CrapsMath.snapOdds(Math.min(o.amount(), r.room()), r.unit());
					if (r != null && amount >= r.unit()) {
						out.add(new Action.Odds(o.betId(), amount));
					}
				}
			}
		}
		return out;
	}
}
