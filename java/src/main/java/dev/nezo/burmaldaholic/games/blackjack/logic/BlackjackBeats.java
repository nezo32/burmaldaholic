package dev.nezo.burmaldaholic.games.blackjack.logic;

import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import java.util.ArrayList;
import java.util.List;

/**
 * Blackjack beat schedule (C1; docs/design/animation/cards.md §0.2, §1.2): the round is drawn and resolved at once by
 * {@link BlackjackRound} (unchanged), and this PURE publisher decides WHEN each card becomes public — one card per beat
 * ({@code dealBeatTicks} 6), the hole card tucked face down, the peek beat, the hole flip, the dealer's draws
 * ({@code dealerDrawTicks} 14 apart) — and the busy gate ({@link #busyUntil}: the last card is readable) before which the
 * table shows no decision, timer or result.
 *
 * <p>The publisher is synced after every round change ({@link #sync}); new cards are scheduled after the ones already
 * scheduled. Every card gets a stable {@code id} (a split card keeps its id, so the screen slides it). The schedule
 * depends only on PUBLIC counts and kinds (how many cards, a peek, the dealer playing), never on card values
 * (faithfulness §0.7.3, tested by permutation). {@link #timeline} renders the schedule as a shared {@link Timeline} for
 * the golden vectors ({@code fx/vectors/cards.json}).
 */
public final class BlackjackBeats {
	public static final String GAME = "blackjack";
	public static final String DEAL = "blackjack.deal";
	public static final String HOLE = "blackjack.hole";
	public static final String PEEK = "blackjack.peek";
	public static final String HIT = "blackjack.hit";
	public static final String DOUBLE = "blackjack.double";
	public static final String SPLIT = "blackjack.split_card";
	public static final String FLIP = "blackjack.hole_flip";
	public static final String DRAW = "blackjack.dealer_draw";
	/** A face-up card is readable K1 + K2 after its beat (520 ms). */
	public static final int READABLE_TICKS = Timeline.ceilTicks(CardMotion.READABLE_MS);
	/** A face-down card / the flip is settled after its tween. */
	public static final int FLIP_TICKS = Timeline.ceilTicks(CardMotion.FLIP_MS);

	/** Beat config (ticks): {@code blackjack.fx.*} defaults of cards.md §8. */
	public record Config(int dealBeatTicks, int dealerDrawTicks, int holeFlipTicks, int peekTicks) {
		public static final Config DEFAULT = new Config(6, 14, 10, 12);

		/** The single-seat fast deal ({@code cards.soloSpeed} 0.75): beats × factor, never below 2 ticks. */
		public Config scaled(double factor) {
			return new Config(Math.max(2, (int) Math.round(dealBeatTicks * factor)), Math.max(4, (int) Math.round(dealerDrawTicks * factor)),
				Math.max(4, (int) Math.round(holeFlipTicks * factor)), Math.max(6, (int) Math.round(peekTicks * factor)));
		}
	}

	/** A published card: stable id, code, the tick it leaves the shoe and its beat kind. */
	public record Ev(int id, int code, long tick, String kind) {}

	private final Config cfg;
	private final long start;
	private final List<Ev> dealer = new ArrayList<>();
	private final List<Integer> seatNos = new ArrayList<>();
	private final List<List<List<Ev>>> hands = new ArrayList<>();
	private long last;
	private long peekTick = -1;
	private long holeFlipTick = -1;
	private int nextId;
	private long busyUntil;

	/**
	 * Schedules the initial deal of {@code round} from {@code startTick}: first card to each seat (seat order), the
	 * dealer's up card, second card to each seat, the dealer's hole card (face down), then whatever the round already did
	 * (peek, naturals, a dealer blackjack).
	 */
	public BlackjackBeats(BlackjackRound round, long startTick, Config cfg) {
		this.cfg = cfg;
		this.start = startTick;
		this.last = startTick - cfg.dealBeatTicks;
		List<BlackjackRound.Seat> seats = round.seats();
		for (BlackjackRound.Seat s : seats) {
			seatNos.add(s.seat);
			List<List<Ev>> hs = new ArrayList<>();
			hs.add(new ArrayList<>());
			hands.add(hs);
		}
		// the round constructor deals in this order; later hands (none yet) are synced below
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < seats.size(); i++) {
				List<Card> cards = seats.get(i).hands.getFirst().cards;
				// a seat's first hand may already be longer (never at construction) — only the first two here
				if (cards.size() > pass) hands.get(i).getFirst().add(next(cards.get(pass).code(), DEAL));
			}
			dealer.add(next(round.dealerCards().get(pass).code(), pass == 0 ? DEAL : HOLE));
		}
		busyUntil = last + READABLE_TICKS;
		sync(round, startTick);
	}

	private Ev next(int code, String kind) {
		last = last + cfg.dealBeatTicks;
		return new Ev(nextId++, code, last, kind);
	}

	private Ev nextAt(int code, String kind, long tick) {
		last = Math.max(last, tick);
		return new Ev(nextId++, code, last, kind);
	}

	/** Re-reads the round after a change at {@code now}; new cards are scheduled one beat after the last one. */
	public void sync(BlackjackRound round, long now) {
		// peek (after the deal, or after the insurance decisions)
		if (round.peeked() && peekTick < 0) {
			peekTick = Math.max(now, last + cfg.dealBeatTicks);
			last = peekTick + cfg.peekTicks - cfg.dealBeatTicks;
			busyUntil = Math.max(busyUntil, peekTick + cfg.peekTicks);
		}
		// seats: reconcile hands by card identity (a split card keeps its id), new cards one per beat
		for (int i = 0; i < seatNos.size(); i++) {
			BlackjackRound.Seat s = round.seat(seatNos.get(i));
			if (s == null) continue;
			List<List<Ev>> old = hands.get(i);
			List<Ev> pool = new ArrayList<>();
			for (List<Ev> h : old) pool.addAll(h);
			List<List<Ev>> fresh = new ArrayList<>();
			for (BlackjackRound.Hand h : s.hands) {
				List<Ev> list = new ArrayList<>();
				for (int c = 0; c < h.cards.size(); c++) {
					int code = h.cards.get(c).code();
					Ev found = null;
					for (Ev e : pool) {
						if (e.code == code) {
							found = e;
							break;
						}
					}
					if (found != null) {
						pool.remove(found);
					} else {
						String kind = h.doubled && c == 2 ? DOUBLE : h.split && c == 1 ? SPLIT : HIT;
						found = nextAt(code, kind, Math.max(now, last + cfg.dealBeatTicks));
						busyUntil = Math.max(busyUntil, found.tick + READABLE_TICKS);
					}
					list.add(found);
				}
				fresh.add(list);
			}
			hands.set(i, fresh);
		}
		// dealer: the hole flip, then the draws
		List<Card> d = round.dealerCards();
		if (round.holeRevealed() && holeFlipTick < 0) {
			long at = Math.max(now, last + cfg.holeFlipTicks);
			if (peekTick >= 0) at = Math.max(at, peekTick + cfg.peekTicks);
			holeFlipTick = at;
			last = at;
			busyUntil = Math.max(busyUntil, at + FLIP_TICKS);
		}
		for (int i = dealer.size(); i < d.size(); i++) {
			dealer.add(nextAt(d.get(i).code(), DRAW, Math.max(now, last + cfg.dealerDrawTicks)));
			busyUntil = Math.max(busyUntil, last + READABLE_TICKS);
		}
	}

	/** Moves the whole schedule {@code ticks} earlier (tests / screenshots: "everything is already public"). */
	public void shiftEarlier(long ticks) {
		if (ticks <= 0) return;
		dealer.replaceAll(e -> new Ev(e.id, e.code, e.tick - ticks, e.kind));
		for (List<List<Ev>> hs : hands) for (List<Ev> h : hs) h.replaceAll(e -> new Ev(e.id, e.code, e.tick - ticks, e.kind));
		if (peekTick >= 0) peekTick -= ticks;
		if (holeFlipTick >= 0) holeFlipTick -= ticks;
		last -= ticks;
		busyUntil -= ticks;
		shift += ticks;
	}

	private long shift;

	// ---- queries --------------------------------------------------------------------------------------------------

	public long startTick() {
		return start - shift;
	}

	/** Until this tick a card is still moving: no decision, timer or result is shown (the reveal gate at the end). */
	public long busyUntil() {
		return busyUntil;
	}

	public boolean busy(long now) {
		return now < busyUntil;
	}

	/** Ticks left until the gate (0 when open). */
	public int delay(long now) {
		return (int) Math.max(0, busyUntil - now);
	}

	public long peekTick() {
		return peekTick;
	}

	public long holeFlipTick() {
		return holeFlipTick;
	}

	/** Hole card public (flip beat reached). */
	public boolean holeShown(long now) {
		return holeFlipTick >= 0 && now >= holeFlipTick;
	}

	/** The next tick after {@code now} at which something becomes public (a card, the flip, the gate), or -1. */
	public long nextChange(long now) {
		long best = Long.MAX_VALUE;
		for (Ev e : dealer) if (e.tick > now) best = Math.min(best, e.tick);
		for (List<List<Ev>> hs : hands) for (List<Ev> h : hs) for (Ev e : h) if (e.tick > now) best = Math.min(best, e.tick);
		if (holeFlipTick > now) best = Math.min(best, holeFlipTick);
		if (peekTick > now) best = Math.min(best, peekTick);
		if (busyUntil > now) best = Math.min(best, busyUntil);
		return best == Long.MAX_VALUE ? -1 : best;
	}

	/** Beat kinds that became public in ({@code from}, {@code to}] (dealer gestures, sounds for spectators). */
	public List<String> kindsBetween(long from, long to) {
		List<String> out = new ArrayList<>();
		for (Ev e : dealer) if (e.tick > from && e.tick <= to) out.add(e.kind);
		for (List<List<Ev>> hs : hands) for (List<Ev> h : hs) for (Ev e : h) if (e.tick > from && e.tick <= to) out.add(e.kind);
		if (peekTick > from && peekTick <= to) out.add(PEEK);
		if (holeFlipTick > from && holeFlipTick <= to) out.add(FLIP);
		return out;
	}

	/** Dealer cards public at {@code now} (the hole card is a back, id kept, until the flip). */
	public List<Ev> dealerAt(long now) {
		List<Ev> out = new ArrayList<>();
		for (int i = 0; i < dealer.size(); i++) {
			Ev e = dealer.get(i);
			if (e.tick > now) break;
			out.add(i == 1 && !holeShown(now) ? new Ev(e.id, -1, e.tick, e.kind) : e);
		}
		return out;
	}

	/** Hands of seat {@code seatNo} public at {@code now} (empty hands are dropped at the end). */
	public List<List<Ev>> handsAt(int seatNo, long now) {
		int i = seatNos.indexOf(seatNo);
		List<List<Ev>> out = new ArrayList<>();
		if (i < 0) return out;
		for (List<Ev> h : hands.get(i)) {
			List<Ev> v = new ArrayList<>();
			for (Ev e : h) if (e.tick <= now) v.add(e);
			if (!v.isEmpty() || out.isEmpty()) out.add(v);
		}
		return out;
	}

	/** The whole schedule as a shared timeline (vectors; lane = seat, −1 dealer; args = [hand, index]; no card values). */
	public Timeline timeline() {
		TimelineBuilder b = Timeline.builder(GAME, 0).clock(Clock.SHARED);
		for (int i = 0; i < dealer.size(); i++) {
			Ev e = dealer.get(i);
			b.add(ms(e.tick), i == 1 ? CardMotion.DEAL_MS : CardMotion.READABLE_MS, e.kind, -1, 0, i);
		}
		if (peekTick >= 0) b.add(ms(peekTick), cfg.peekTicks * 50, PEEK, -1);
		if (holeFlipTick >= 0) b.add(ms(holeFlipTick), CardMotion.FLIP_MS, FLIP, -1, 0, 1);
		for (int s = 0; s < seatNos.size(); s++) {
			List<List<Ev>> hs = hands.get(s);
			for (int h = 0; h < hs.size(); h++) {
				for (int c = 0; c < hs.get(h).size(); c++) {
					Ev e = hs.get(h).get(c);
					b.add(ms(e.tick), CardMotion.READABLE_MS, e.kind, seatNos.get(s), h, c);
				}
			}
		}
		return b.build();
	}

	private int ms(long tick) {
		return (int) Math.max(0, (tick - startTick()) * 50);
	}
}
