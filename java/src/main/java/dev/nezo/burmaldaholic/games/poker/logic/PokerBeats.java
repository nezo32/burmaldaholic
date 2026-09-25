package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;
import java.util.Set;

/**
 * Texas Hold'em beat schedules (animation/cards.md §2.2, task C1; docs/architecture/animation.md §3.6): pure
 * {@code public counts → Timeline}. The server paces publication with them (a board card is sent only when its
 * {@link #SLIDE} beat starts, a shown hand when its {@link #SHOW} beat starts, the pot awards when the first
 * {@link #AWARD} starts, the result text at the gate = {@link Timeline#sharedEndMs()}); the screen and the table
 * renderer sample the same timeline, so every viewer sees the same beat at the same tick.
 *
 * <p><b>Honesty</b> (§0.7.3): the inputs are counts that are public when the segment starts (seats dealt in, the
 * street, whether bets lie on the felt, how many hands show, how many pots). No input ever depends on a card that
 * is not yet shown, so the timing is identical under any permutation of the unrevealed cards; the run-out pause
 * and the flip length are the same for every street (lead decision L3: the slow run-out paces an already drawn
 * and persisted board, no decision remains).
 *
 * <p>Three segments:
 * <ul>
 *   <li>{@link #deal}: the button travels, the blinds fly, two rounds of hole cards clockwise from the small
 *   blind (lane = hand player index), every seat's pair "lifts" (the viewer's flips face up);</li>
 *   <li>{@link #street}: street-end gather, burn, the flop (3 cards) or turn / river (1 card) slide and flip;</li>
 *   <li>{@link #finish}: the end of a hand: optional gather, optional all-in run-out (exposure, a sweat pause
 *   before every remaining street, 1.6 × slower flips), the ordered showdown, best five, the pot awards
 *   (side pots first, in the server's pot order).</li>
 * </ul>
 */
public final class PokerBeats {
	public static final String GAME = "poker";
	public static final String BUTTON = "poker.button";
	public static final String BLIND = "poker.blind";
	public static final String DEAL = "poker.deal";
	public static final String LIFT = "poker.lift";
	public static final String GATHER = "poker.gather";
	public static final String BURN = "poker.burn";
	public static final String SLIDE = "poker.slide";
	public static final String FLIP = "poker.flip";
	public static final String EXPOSE = "poker.expose";
	public static final String SWEAT = "poker.sweat";
	public static final String SHOW = "poker.show";
	public static final String BEST = "poker.best";
	public static final String AWARD = "poker.award";

	/** Card primitive lengths (animation/cards.md §0.3): poker deal arc 200 ms, flip 280 ms, run-out flip 1.6 ×. */
	public static final int DEAL_MS = 200;
	public static final int FLIP_MS = 280;
	public static final int RUNOUT_FLIP_MS = 450;
	public static final int BUTTON_MS = 400;
	public static final int BLIND_MS = 180;
	public static final int BURN_MS = 200;
	public static final int FLOP_STAGGER_MS = 150;
	public static final int FLIP_STAGGER_MS = 80;
	/** Hand-name badge after a showdown flip (drop-in, outBack). */
	public static final int BADGE_MS = 200;
	public static final int POT_SLIDE_MS = 500;
	public static final int POT_GAP_MS = 400;
	public static final int BEST_HOLD_MS = 400;

	/**
	 * Server pacing in ticks (config {@code poker.fx.*}, animation/cards.md §8). The server appends it to every
	 * segment's args ({@link #withPacing}), so every client rebuilds the timeline with the server's pacing.
	 */
	public record Pacing(int dealBeatTicks, int gatherTicks, int streetTicks, int showBeatTicks, int awardTicks, int runoutPauseTicks) {
		public static final Pacing DEFAULT = new Pacing(3, 8, 12, 10, 12, 24);
		/** Ints {@link #withPacing} appends. */
		public static final int SIZE = 6;

		public Pacing {
			dealBeatTicks = clamp(dealBeatTicks, 1, 10);
			gatherTicks = clamp(gatherTicks, 2, 40);
			streetTicks = clamp(Math.max(streetTicks, gatherTicks + 4), 2, 40);
			showBeatTicks = clamp(showBeatTicks, 2, 40);
			awardTicks = clamp(awardTicks, 2, 40);
			runoutPauseTicks = clamp(runoutPauseTicks, 0, 60);
		}

		private static int clamp(int v, int lo, int hi) {
			return Math.max(lo, Math.min(hi, v));
		}
	}

	/**
	 * The end of a hand, from public facts only.
	 *
	 * @param gather     bets of the last betting round still lie in front of the seats
	 * @param runoutFrom first street still to show on an all-in run-out (1 flop, 2 turn, 3 river), 0 when the
	 *                   board was complete (or the pot is uncontested)
	 * @param expose     live hands are turned face up before the run-out (all-in exposure)
	 * @param shows      show beats: every live hand at the showdown, shown or mucked (0 when uncontested); never
	 *                   the number that actually show, which depends on who wins
	 * @param pots       pots awarded (main + side pots)
	 */
	public record Finish(boolean gather, int runoutFrom, boolean expose, int shows, int pots) {
		public Finish {
			runoutFrom = Math.max(0, Math.min(3, runoutFrom));
			shows = Math.max(0, shows);
			pots = Math.max(1, pots);
		}
	}

	private PokerBeats() {}

	// ---- segment args (server → clients) ------------------------------------------------------------------------

	/** Builder args of each segment kind before the pacing tail. */
	public static int argCount(String kind) {
		return switch (kind) {
			case "deal" -> 2;
			case "street" -> 1;
			case "finish" -> 5;
			default -> 0;
		};
	}

	/** {@code args} followed by the pacing (what the server publishes as the segment's args). */
	public static int[] withPacing(int[] args, Pacing p) {
		int[] out = java.util.Arrays.copyOf(args, args.length + Pacing.SIZE);
		int i = args.length;
		out[i++] = p.dealBeatTicks();
		out[i++] = p.gatherTicks();
		out[i++] = p.streetTicks();
		out[i++] = p.showBeatTicks();
		out[i++] = p.awardTicks();
		out[i] = p.runoutPauseTicks();
		return out;
	}

	/** The pacing tail of published args ({@link #withPacing}) from index {@code from}; {@link Pacing#DEFAULT} if absent. */
	public static Pacing pacingOf(int[] a, int from) {
		if (a.length < from + Pacing.SIZE) return Pacing.DEFAULT;
		return new Pacing(a[from], a[from + 1], a[from + 2], a[from + 3], a[from + 4], a[from + 5]);
	}

	/** The timeline of a published segment ({@code kind}, args with the pacing tail), or null. */
	public static Timeline segment(String kind, int[] a, int seed) {
		int n = argCount(kind);
		if (n == 0 || a.length < n) return null;
		Pacing p = pacingOf(a, n);
		return switch (kind) {
			case "deal" -> deal(a[0], a[1], p, seed);
			case "street" -> street(a[0], p, seed);
			default -> finish(new Finish(a[0] != 0, a[1], a[2] != 0, a[3], a[4]), p, seed);
		};
	}

	// ---- segments ----------------------------------------------------------------------------------------------

	/**
	 * A new hand: button (400 ms), blinds (from 400 ms), then two rounds of hole cards, one per deal beat,
	 * clockwise from the small blind; each seat's {@link #LIFT} starts when its second card has landed (the
	 * viewer's pair flips face up there; other pairs stay backs).
	 *
	 * @param players seats dealt in (hand player indices 0..players-1, clockwise)
	 * @param sbIndex hand index of the small blind (first card)
	 */
	public static Timeline deal(int players, int sbIndex, Pacing p, int seed) {
		int n = Math.max(2, players);
		TimelineBuilder b = Timeline.builder(GAME, seed).clock(Clock.SHARED);
		b.add(0, BUTTON_MS, BUTTON, -1);
		b.add(BUTTON_MS, BLIND_MS, BLIND, Math.floorMod(sbIndex, n), 0);
		b.add(BUTTON_MS, BLIND_MS, BLIND, Math.floorMod(sbIndex + 1, n), 1);
		int start = BUTTON_MS + 200;
		int step = p.dealBeatTicks() * 50;
		for (int k = 0; k < 2 * n; k++) {
			int player = Math.floorMod(sbIndex + k, n);
			b.add(start + step * k, DEAL_MS, DEAL, player, k / n);
		}
		for (int j = 0; j < n; j++) {
			int player = Math.floorMod(sbIndex + j, n);
			b.add(start + step * (n + j) + DEAL_MS, FLIP_MS, LIFT, player);
		}
		return b.build();
	}

	/** A street: gather (bets → pot), burn, then the new board cards slide in and flip. {@code street} 1..3. */
	public static Timeline street(int street, Pacing p, int seed) {
		TimelineBuilder b = Timeline.builder(GAME, seed).clock(Clock.SHARED);
		int gatherMs = p.gatherTicks() * 50;
		b.add(0, gatherMs, GATHER, -1);
		b.add(gatherMs, BURN_MS, BURN, -1);
		board(b, street, p.streetTicks() * 50, FLIP_MS);
		return b.build();
	}

	/** The end of a hand: optional gather, run-out, showdown and awards; the gate is the end of the last award. */
	public static Timeline finish(Finish f, Pacing p, int seed) {
		TimelineBuilder b = Timeline.builder(GAME, seed).clock(Clock.SHARED);
		int t = 0;
		if (f.gather()) {
			b.add(0, p.gatherTicks() * 50, GATHER, -1);
			t = p.gatherTicks() * 50;
		}
		if (f.runoutFrom() > 0) {
			if (f.expose()) {
				b.add(t, FLIP_MS, EXPOSE, -1);
				t += FLIP_MS;
			}
			int pause = p.runoutPauseTicks() * 50;
			for (int s = f.runoutFrom(); s <= 3; s++) {
				b.add(t, pause, SWEAT, s);
				b.add(t, BURN_MS, BURN, -1);
				t = board(b, s, t + Math.max(pause, BURN_MS), RUNOUT_FLIP_MS);
			}
		}
		int awardAt = t;
		if (f.shows() > 0) {
			int showStep = p.showBeatTicks() * 50;
			for (int i = 0; i < f.shows(); i++) {
				b.add(t + showStep * i, FLIP_MS + BADGE_MS, SHOW, i);
			}
			int bestAt = t + showStep * (f.shows() - 1) + FLIP_MS + BADGE_MS + 300;
			b.add(bestAt, BEST_HOLD_MS, BEST, -1);
			awardAt = bestAt + p.awardTicks() * 50;
		}
		for (int i = 0; i < f.pots(); i++) {
			b.add(awardAt + i * (POT_SLIDE_MS + POT_GAP_MS), POT_SLIDE_MS, AWARD, i);
		}
		return b.build();
	}

	/**
	 * Board cards of {@code street} (1 flop: slots 0–2, 2 turn: 3, 3 river: 4): slides from {@code at}, then flips.
	 * Returns the end of the last flip.
	 */
	private static int board(TimelineBuilder b, int street, int at, int flipMs) {
		int first = street == 1 ? 0 : street + 1;
		int count = street == 1 ? 3 : 1;
		for (int i = 0; i < count; i++) {
			b.add(at + FLOP_STAGGER_MS * i, DEAL_MS, SLIDE, first + i);
		}
		int flipAt = at + FLOP_STAGGER_MS * (count - 1) + DEAL_MS;
		int end = flipAt;
		for (int i = 0; i < count; i++) {
			b.add(flipAt + FLIP_STAGGER_MS * i, flipMs, FLIP, first + i);
			end = flipAt + FLIP_STAGGER_MS * i + flipMs;
		}
		return end;
	}

	// ---- sampling (server publication and clients) ----------------------------------------------------------

	/** Number of beats of {@code kind} that have started at {@code t} (ms). */
	public static int started(Timeline tl, String kind, double t) {
		int n = 0;
		for (Beat b : tl.beats()) {
			if (b.at() > t) break;
			if (b.kind().equals(kind)) n++;
		}
		return n;
	}

	/** Board slots (by lane) whose card has been dealt (slide started) at {@code t}: the publication rule. */
	public static int boardPublished(Timeline tl, double t) {
		int max = -1;
		for (Beat b : tl.beats()) {
			if (b.at() > t) break;
			if (b.kind().equals(SLIDE)) max = Math.max(max, b.lane());
		}
		return max + 1;
	}

	/** Publication kinds the server syncs viewers on (a new card, a shown hand, the awards, the best five). */
	public static final Set<String> PUBLISH = Set.of(SLIDE, SHOW, BEST, AWARD, EXPOSE);

	/**
	 * Start (ms) of the next publication beat strictly after {@code t}, or {@link Timeline#sharedEndMs()} when none
	 * is left (the gate), or -1 when the gate has passed.
	 */
	public static int nextPublication(Timeline tl, double t) {
		for (Beat b : tl.beats()) {
			if (b.at() > t && PUBLISH.contains(b.kind())) return b.at();
		}
		int gate = tl.sharedEndMs();
		return gate > t ? gate : -1;
	}

	/** The first beat of {@code kind} and {@code lane}, or null. */
	public static Beat find(Timeline tl, String kind, int lane) {
		for (Beat b : tl.beats()) if (b.kind().equals(kind) && b.lane() == lane) return b;
		return null;
	}
}
