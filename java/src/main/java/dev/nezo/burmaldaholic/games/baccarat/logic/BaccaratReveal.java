package dev.nezo.burmaldaholic.games.baccarat.logic;

import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * The baccarat reveal timeline (C1; docs/design/animation/cards.md §4.2), replacing the old {@code revealFrames}: the
 * four cards are dealt face down one per beat (P1, B1, P2, B2), P1 flips, P2 is squeezed (the Player total shows), B1
 * flips, B2 is squeezed (the Banker total), the announcement (natural / draws / stands), then each third card is dealt
 * SIDEWAYS and squeezed, with the Banker decision announced in between. PURE and in ticks.
 *
 * <p>The schedule depends only on how many cards each side gets and whether there is a natural — both public at the
 * moment their beat arrives — so it is honest (§0.7.3); the squeeze window is the same for every card (§0.7.7). The
 * server publishes a card's face only from its FLIP / SQUEEZE start ({@link #revealAt}), a card at all only from its
 * DEAL ({@link #dealAt}); the total length is never sent before the end. {@code capTicks}: longer schedules shrink the
 * squeeze windows proportionally, never below 10 ticks.
 */
public final class BaccaratReveal {
	public enum Kind {
		DEAL,
		FLIP,
		SQUEEZE,
		ANNOUNCE
	}

	/** Beat config (ticks): {@code baccarat.fx.*} defaults of cards.md §8. */
	public record Config(int dealBeatTicks, int flipTicks, int squeezeTicks, int announceTicks, boolean squeeze, int capTicks) {
		public static final Config DEFAULT = new Config(6, 6, 20, 10, true, 160);

		public Config withCap(int cap) {
			return new Config(dealBeatTicks, flipTicks, squeezeTicks, announceTicks, squeeze, cap);
		}
	}

	/** One step: start / length in ticks, side 0 Player / 1 Banker / −1 none, card index (0..2), −1 none. */
	public record Step(int at, int dur, Kind kind, int side, int index) {
		public int end() {
			return at + dur;
		}
	}

	private final List<Step> steps;
	private final int total;

	private BaccaratReveal(List<Step> steps, int total) {
		this.steps = List.copyOf(steps);
		this.total = total;
	}

	/**
	 * The reveal of a coup with {@code playerCards} / {@code bankerCards} (2 or 3) and whether either side has a
	 * natural (then no third card is dealt).
	 */
	public static BaccaratReveal build(int playerCards, int bankerCards, boolean natural, Config cfg) {
		// the squeeze window is sized on the LONGEST coup (both sides draw), never on this one: P2's squeeze starts
		// before the third cards are public, so its length must not depend on them (§0.7.3, §0.7.7)
		int sq = cfg.squeezeTicks;
		BaccaratReveal worst = build(3, 3, false, cfg, sq);
		if (cfg.squeeze && worst.total > cfg.capTicks) {
			int squeezes = 4;
			int over = worst.total - cfg.capTicks;
			sq = Math.max(10, cfg.squeezeTicks - (over + squeezes - 1) / squeezes);
		}
		return build(playerCards, bankerCards, natural, cfg, sq);
	}

	private static BaccaratReveal build(int pCards, int bCards, boolean natural, Config cfg, int sq) {
		List<Step> s = new ArrayList<>();
		int b = cfg.dealBeatTicks;
		int t = 0;
		for (int i = 0; i < 4; i++) {
			s.add(new Step(t, b, Kind.DEAL, i % 2, i / 2));
			t += b;
		}
		// P1 flip, P2 squeeze, B1 flip, B2 squeeze
		s.add(new Step(t, cfg.flipTicks, Kind.FLIP, 0, 0));
		t += cfg.flipTicks;
		t = reveal(s, t, 0, 1, cfg, sq);
		s.add(new Step(t, cfg.flipTicks, Kind.FLIP, 1, 0));
		t += cfg.flipTicks;
		t = reveal(s, t, 1, 1, cfg, sq);
		s.add(new Step(t, cfg.announceTicks, Kind.ANNOUNCE, -1, 0));
		t += cfg.announceTicks;
		if (natural) return new BaccaratReveal(s, t);
		boolean playerDraws = pCards > 2;
		if (playerDraws) {
			s.add(new Step(t, b, Kind.DEAL, 0, 2));
			t += b;
			t = reveal(s, t, 0, 2, cfg, sq);
			s.add(new Step(t, b, Kind.ANNOUNCE, -1, 1));
			t += b;
		}
		if (bCards > 2) {
			s.add(new Step(t, b, Kind.DEAL, 1, 2));
			t += b;
			t = reveal(s, t, 1, 2, cfg, sq);
		}
		return new BaccaratReveal(s, t + b);
	}

	/** A squeezed slot (P2, B2, both third cards) or, squeeze off, a flip; returns the end (+2 t after a squeeze). */
	private static int reveal(List<Step> s, int t, int side, int index, Config cfg, int sq) {
		if (cfg.squeeze) {
			s.add(new Step(t, sq, Kind.SQUEEZE, side, index));
			return t + sq + 2;
		}
		s.add(new Step(t, cfg.flipTicks, Kind.FLIP, side, index));
		return t + cfg.flipTicks;
	}

	public List<Step> steps() {
		return steps;
	}

	/** Length of the reveal in ticks (the reveal gate: result text and the settle come after it). */
	public int total() {
		return total;
	}

	/** Tick the card leaves the shoe, or −1 (no such card). */
	public int dealAt(int side, int index) {
		for (Step st : steps) if (st.kind == Kind.DEAL && st.side == side && st.index == index) return st.at;
		return -1;
	}

	/** The FLIP / SQUEEZE step that turns this card face up, or null. */
	public Step revealStep(int side, int index) {
		for (Step st : steps) if ((st.kind == Kind.FLIP || st.kind == Kind.SQUEEZE) && st.side == side && st.index == index) return st;
		return null;
	}

	/** Tick the card's face becomes public (its flip / squeeze starts), or −1. */
	public int revealAt(int side, int index) {
		Step st = revealStep(side, index);
		return st == null ? -1 : st.at;
	}

	/** The announcement step {@code n} (0: after the four cards, 1: the Banker decision), or null. */
	public Step announce(int n) {
		for (Step st : steps) if (st.kind == Kind.ANNOUNCE && st.index == n) return st;
		return null;
	}

	/** As a shared timeline (vectors): kind {@code baccarat.<kind>}, lane = side, args = [index]. */
	public Timeline timeline() {
		TimelineBuilder b = Timeline.builder("baccarat", 0).clock(Clock.SHARED);
		for (Step st : steps) b.add(st.at * 50, st.dur * 50, "baccarat." + st.kind.name().toLowerCase(java.util.Locale.ROOT), st.side, st.index);
		b.add(total * 50, 0, "baccarat.gate", -1);
		return b.build();
	}
}
