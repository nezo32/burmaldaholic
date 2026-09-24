package dev.nezo.burmaldaholic.games.uth.logic;

import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;

/**
 * Ultimate Texas Hold'em beat schedules (animation/cards.md §3.2, task C1): pure {@code public counts → Timeline},
 * sampled by the screen, the table renderer and the dealer NPC, and used by the server for its timers (the decision
 * window opens after the deal; the showdown settles at the gate).
 *
 * <p>Honesty (§0.7.3): the only input is the number of seats dealt in. The qualify pause is the same whether the
 * dealer qualifies or not, and every seat's settle beat has the same length whatever it wins.
 *
 * <p>Segments: {@link #deal} (hole cards to every seat and the dealer, then the five board backs sweep out),
 * {@link #street} (the flop flips left → right; turn then river 500 ms later), {@link #showdown} (the dealer's two
 * cards flip, the qualify banner, every seat's hand turns over, then per-seat settlement circle by circle) and
 * {@link #result} (the gather before the next round).
 */
public final class UthBeats {
	public static final String GAME = "uth";
	public static final String DEAL = "uth.deal";
	public static final String DEALER_DEAL = "uth.dealer_deal";
	public static final String LIFT = "uth.lift";
	public static final String BOARD = "uth.board";
	public static final String FLIP = "uth.flip";
	public static final String DEALER_FLIP = "uth.dealer_flip";
	public static final String QUALIFY = "uth.qualify";
	public static final String REVEAL = "uth.reveal";
	public static final String SETTLE = "uth.settle";
	public static final String GATHER = "uth.gather";

	public static final int DEAL_STEP_MS = 150;
	public static final int DEAL_MS = 200;
	public static final int FLIP_MS = 280;
	public static final int BOARD_MS = 300;
	public static final int BOARD_STAGGER_MS = 60;
	/** Settle order per seat: Play, Ante, Blind, Trips (circle ids of {@link #CIRCLES}). */
	public static final int[] SETTLE_ORDER = {3, 1, 2, 0};
	/** Circle ids in their row order on the felt: Trips, Ante, Blind, Play (visual/cards.md §6.5). */
	public static final String[] CIRCLES = {"trips", "ante", "blind", "play"};
	public static final int CIRCLE_STEP_MS = 80;
	public static final int SEAT_STAGGER_MS = 150;
	public static final int SETTLE_MS = 260;
	/** Existing server result phase (UthTableBlockEntity.RESULT_TICKS). */
	public static final int RESULT_MS = 80 * 50;

	private UthBeats() {}

	/**
	 * The deal: one card to each seat (lanes = seat order 0..seats-1), one to the dealer, again, then the five board
	 * backs sweep to their slots 300 ms after the last hole card.
	 */
	public static Timeline deal(int seats, int seed) {
		int n = Math.max(1, seats);
		TimelineBuilder b = Timeline.builder(GAME, seed).clock(Clock.SHARED);
		int k = 0;
		for (int pass = 0; pass < 2; pass++) {
			for (int s = 0; s < n; s++) {
				b.add(DEAL_STEP_MS * k++, DEAL_MS, DEAL, s, pass);
			}
			b.add(DEAL_STEP_MS * k++, DEAL_MS, DEALER_DEAL, pass, pass);
		}
		for (int s = 0; s < n; s++) {
			b.add(DEAL_STEP_MS * (n + 1 + s) + DEAL_MS, FLIP_MS, LIFT, s);
		}
		int boardAt = DEAL_STEP_MS * (k - 1) + DEAL_MS + 300;
		for (int i = 0; i < 5; i++) {
			b.add(boardAt + BOARD_STAGGER_MS * i, BOARD_MS, BOARD, i);
		}
		return b.build();
	}

	/** Street reveal: {@code street} 1 = the flop (slots 0–2, 100 ms apart), 2 = turn + river (slot 4 500 ms later). */
	public static Timeline street(int street, int seed) {
		TimelineBuilder b = Timeline.builder(GAME, seed).clock(Clock.SHARED);
		if (street <= 1) {
			for (int i = 0; i < 3; i++) b.add(100 * i, FLIP_MS, FLIP, i);
		} else {
			b.add(0, FLIP_MS, FLIP, 3);
			b.add(500, FLIP_MS, FLIP, 4);
		}
		return b.build();
	}

	/** The showdown for {@code seats} seats (lane = seat order); the gate is the end of the last seat's settle. */
	public static Timeline showdown(int seats, int seed) {
		int n = Math.max(1, seats);
		TimelineBuilder b = Timeline.builder(GAME, seed).clock(Clock.SHARED);
		b.add(0, FLIP_MS, DEALER_FLIP, 0);
		b.add(200, FLIP_MS, DEALER_FLIP, 1);
		b.add(600, 200, QUALIFY, -1);
		b.add(1100, FLIP_MS, REVEAL, -1);
		for (int s = 0; s < n; s++) {
			for (int c = 0; c < SETTLE_ORDER.length; c++) {
				b.add(1500 + SEAT_STAGGER_MS * s + CIRCLE_STEP_MS * c, SETTLE_MS, SETTLE, s, SETTLE_ORDER[c]);
			}
		}
		return b.build();
	}

	/** The result phase: every card is gathered 600 ms before the next betting round. */
	public static Timeline result(int seed) {
		TimelineBuilder b = Timeline.builder(GAME, seed).clock(Clock.SHARED);
		b.add(RESULT_MS - 600, 280, GATHER, -1);
		return b.build();
	}

	/** Server ticks the deal needs before the decision window is fair (the last card readable). */
	public static int dealTicks(int seats) {
		return deal(seats, 0).sharedEndTicks();
	}

	/** Server ticks from the showdown start to the gate (settlement, result text). */
	public static int showdownTicks(int seats) {
		return showdown(seats, 0).sharedEndTicks();
	}
}
