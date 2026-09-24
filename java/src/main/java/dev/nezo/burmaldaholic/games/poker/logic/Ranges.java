package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Public ranges and the human opponent model for the poker bots (BOTS.md §4.3). Pure; same rules and
 * numbers as Bedrock {@code games/poker/logic/ranges.ts}.
 *
 * <p>Every opponent's hole cards are sampled from a range narrowed by that opponent's PUBLIC actions this
 * hand (tightest seen wins): none / checked / limped → 100 % (HARD: minus the top 8 %), called → 60 %,
 * bet ≤ ½ pot or opened preflop → 45 %, bet ½–1 pot or 3-bet → 25 %, raised postflop / overbet / all-in →
 * 12 % of the Chen-ranked hands. HARD scales a human's range by their measured VPIP. The opponent model
 * (last 50 hands: VPIP, PFR, aggression factor) is kept for humans only — bots are never modelled.
 */
public final class Ranges {
	/** Range tags, loosest first ("tightest seen wins" = the highest ordinal). */
	public enum Tag {
		ANY(1), CALL(0.6), SMALL(0.45), MID(0.25), STRONG(0.12);

		/** Top share of the Chen-ranked hands. */
		public final double top;

		Tag(double top) {
			this.top = top;
		}

		public Tag tighter(Tag o) {
			return ordinal() >= o.ordinal() ? this : o;
		}

		/** An aggressive tag (bet / raise), i.e. not ANY and not CALL. */
		public boolean aggressive() {
			return this != ANY && this != CALL;
		}
	}

	/** HARD: an opponent who only checked / limped would have raised the top 8 %. */
	public static final double HARD_LIMP_EXCLUDE = 0.08;
	/** VPIP of a "normal" table: a human with VPIP 50 % gets a range twice as wide. */
	public static final double TABLE_VPIP = 0.25;
	/** Stats need at least this many hands before the model trusts them. */
	public static final int MODEL_MIN_HANDS = 10;
	public static final int MODEL_WINDOW = 50;
	/** PFR below this = a passive player (limps its big hands). */
	public static final double PASSIVE_PFR = 0.08;

	/** A slice [lo, hi) of the ranked combos, as fractions of all 1326. */
	public record Spec(double lo, double hi) {
		public static final Spec FULL = new Spec(0, 1);
	}

	private static int[] ranked;

	private Ranges() {}

	/**
	 * Range of an opponent. {@code hard} = the HARD model (limp exclusion, VPIP scaling); {@code vpip} = the
	 * human's measured VPIP (negative = unknown / a bot); {@code passive} = a measured human who almost never
	 * raises preflop.
	 */
	public static Spec rangeOf(Tag tag, boolean hard, double vpip, boolean passive) {
		// A measured passive human (PFR < 8 %) limps / checks its big hands too: nothing to exclude.
		if (tag == Tag.ANY) {
			return hard && !passive ? new Spec(HARD_LIMP_EXCLUDE, 1) : Spec.FULL;
		}
		double hi = tag.top;
		// "A human with VPIP 50 % who BETS gets a range twice as wide": aggressive tags only (a call stays 60 %).
		// A passive human's bets are not loosened by its VPIP (it only bets strong hands).
		if (hard && !passive && tag != Tag.CALL && vpip > 0) {
			hi = Math.min(1, hi * Math.max(0.4, vpip / TABLE_VPIP));
		}
		return new Spec(0, hi);
	}

	public static Spec rangeOf(Tag tag, boolean hard) {
		return rangeOf(tag, hard, -1, false);
	}

	// ---- ranked combos -------------------------------------------------------------------------------

	/** All 1326 two-card combos (encoded {@code a * 52 + b}, a &lt; b), best Chen score first (ties: higher cards, suited). */
	public static synchronized int[] rankedCombos() {
		if (ranked == null) {
			long[] keyed = new long[1326];
			int k = 0;
			for (int a = 0; a < 52; a++) {
				for (int b = a + 1; b < 52; b++) {
					int hi = Math.max(Cards.rank(a), Cards.rank(b));
					int lo = Math.min(Cards.rank(a), Cards.rank(b));
					long key = Equity.chen(a, b) * 10000L + hi * 100L + lo * 2L + (Cards.suit(a) == Cards.suit(b) ? 1 : 0);
					// sort descending by key, then ascending by (a, b): encode as (MAX − key) then code
					keyed[k++] = ((1_000_000L - key) << 12) | (a * 52L + b);
				}
			}
			Arrays.sort(keyed);
			ranked = new int[1326];
			for (int i = 0; i < 1326; i++) {
				ranked[i] = (int) (keyed[i] & 0xFFF);
			}
		}
		return ranked;
	}

	/** Share (0..1) of all combos ranked at or above this hand (0 = best). */
	public static double handRankShare(int[] hole) {
		int[] all = rankedCombos();
		int a = Math.min(hole[0], hole[1]);
		int b = Math.max(hole[0], hole[1]);
		int code = a * 52 + b;
		for (int i = 0; i < all.length; i++) {
			if (all[i] == code) {
				return (double) i / all.length;
			}
		}
		return 1;
	}

	// ---- public actions → tags ------------------------------------------------------------------------

	/**
	 * The tightest range tag each player's public actions this hand imply (index = hand player index).
	 * Replays the hand's events: blinds are not actions; a preflop call of the big blind is a limp.
	 */
	public static Tag[] publicTags(Hand h) {
		int n = h.players().size();
		Tag[] tags = new Tag[n];
		Arrays.fill(tags, Tag.ANY);
		long[] streetBet = new long[n];
		long pot = 0;
		long current = 0;
		int preRaises = 0;
		Hand.Street street = Hand.Street.PREFLOP;
		for (Hand.Event e : h.events()) {
			if (e instanceof Hand.Blind b) {
				streetBet[b.player()] += b.amount();
				pot += b.amount();
				current = Math.max(current, streetBet[b.player()]);
				continue;
			}
			if (e instanceof Hand.Dealt d) {
				Arrays.fill(streetBet, 0);
				current = 0;
				street = d.street();
				continue;
			}
			Hand.Acted a = (Hand.Acted) e;
			int p = a.player();
			Tag tag = Tag.ANY;
			switch (a.type()) {
				case FOLD, CHECK -> {
				}
				case CALL -> {
					tag = street == Hand.Street.PREFLOP && current <= h.bb() ? Tag.ANY : Tag.CALL;
					streetBet[p] += a.amount();
					pot += a.amount();
				}
				case BET, RAISE -> {
					long added = a.amount() - streetBet[p];
					if (street == Hand.Street.PREFLOP) {
						preRaises++;
						tag = preRaises == 1 ? Tag.SMALL : preRaises == 2 ? Tag.MID : Tag.STRONG;
					} else if (a.type() == Hand.ActionType.RAISE) {
						tag = Tag.STRONG;
					} else {
						double b = pot > 0 ? (double) a.amount() / pot : 1;
						tag = b <= 0.5 ? Tag.SMALL : b <= 1 ? Tag.MID : Tag.STRONG;
					}
					if (a.allIn()) {
						tag = Tag.STRONG;
					}
					streetBet[p] = a.amount();
					pot += Math.max(0, added);
					current = Math.max(current, a.amount());
				}
			}
			tags[p] = tags[p].tighter(tag);
		}
		return tags;
	}

	/** Bets / raises so far on the current street (preflop: raises above the big blind). */
	public static int streetRaises(Hand h) {
		List<Hand.Event> ev = h.events();
		int k = 0;
		for (int i = ev.size() - 1; i >= 0; i--) {
			Hand.Event e = ev.get(i);
			if (e instanceof Hand.Dealt) {
				break;
			}
			if (e instanceof Hand.Acted a && (a.type() == Hand.ActionType.BET || a.type() == Hand.ActionType.RAISE)) {
				k++;
			}
		}
		return k;
	}

	// ---- opponent model (HARD) -------------------------------------------------------------------------

	/** One recorded hand of a human: VPIP, PFR, postflop bets + raises, postflop calls. */
	public record HandStat(boolean vpip, boolean pfr, int aggr, int calls) {}

	/** @param af aggression factor (bets + raises) / calls postflop */
	public record HumanStats(int hands, double vpip, double pfr, double af) {}

	public enum OpponentType { STATION, NIT, MANIAC, REGULAR }

	/** Stats of the last {@link #MODEL_WINDOW} hands; null below {@link #MODEL_MIN_HANDS}. */
	public static HumanStats statsOf(List<HandStat> history) {
		if (history == null || history.size() < MODEL_MIN_HANDS) {
			return null;
		}
		List<HandStat> h = history.subList(Math.max(0, history.size() - MODEL_WINDOW), history.size());
		int n = h.size();
		int aggr = 0;
		int calls = 0;
		int vpip = 0;
		int pfr = 0;
		for (HandStat x : h) {
			aggr += x.aggr();
			calls += x.calls();
			vpip += x.vpip() ? 1 : 0;
			pfr += x.pfr() ? 1 : 0;
		}
		double af = calls > 0 ? (double) aggr / calls : aggr > 0 ? 4 : 1;
		return new HumanStats(n, (double) vpip / n, (double) pfr / n, af);
	}

	/** Station (VPIP &gt; 40 %, AF &lt; 1), nit (VPIP &lt; 15 %), maniac (AF &gt; 3), else regular. */
	public static OpponentType opponentType(HumanStats st) {
		if (st == null) {
			return OpponentType.REGULAR;
		}
		if (st.vpip() > 0.4 && st.af() < 1) {
			return OpponentType.STATION;
		}
		if (st.vpip() < 0.15) {
			return OpponentType.NIT;
		}
		if (st.af() > 3) {
			return OpponentType.MANIAC;
		}
		return OpponentType.REGULAR;
	}

	/** The finished hand's contribution to player i's stats. */
	public static HandStat handStat(Hand h, int i) {
		int aggr = 0;
		int calls = 0;
		boolean post = false;
		for (Hand.Event e : h.events()) {
			if (e instanceof Hand.Dealt) {
				post = true;
			} else if (post && e instanceof Hand.Acted a && a.player() == i) {
				if (a.type() == Hand.ActionType.BET || a.type() == Hand.ActionType.RAISE) {
					aggr++;
				} else if (a.type() == Hand.ActionType.CALL) {
					calls++;
				}
			}
		}
		Hand.Player p = h.player(i);
		return new HandStat(p.vpip(), p.pfr(), aggr, calls);
	}

	/** Keeps the last {@link #MODEL_WINDOW} entries. */
	public static void record(List<HandStat> history, HandStat s) {
		history.add(s);
		while (history.size() > MODEL_WINDOW) {
			history.removeFirst();
		}
	}

	/** Mutable history list for a seat. */
	public static List<HandStat> newHistory() {
		return new ArrayList<>();
	}
}
