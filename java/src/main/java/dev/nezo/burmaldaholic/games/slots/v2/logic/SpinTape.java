package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.Arrays;
import java.util.List;

/**
 * The drawn "tape" of one spin (SLOTS.md §1.2): every random value of the spin, fixed at CONFIRM, persisted
 * before anything is shown ({@code {v:2, machine, bet, price?, tape, total, jackpotAwards[], startTick}},
 * SLOTS.md §8.1) and replayed by the presentation. Settlement uses only {@link #payoutChips()}; everything else
 * drives the timeline. Tumble chains, expansions and win evaluations are NOT stored: they are pure functions of
 * the stops ({@link SpinEval}).
 *
 * @param machine     machine
 * @param bet         total bet (chips; multiple of 5); for a bought feature the underlying bet
 * @param bought      bought feature (no base spin; price = bet × buy price)
 * @param stops       5 base stops (empty when bought)
 * @param freeSpins   free-spin feature or {@code null}
 * @param hunt        Treasure Hunt or {@code null}
 * @param hoard       Piglin's Hoard or {@code null}
 * @param wheel       Dragon Wheel or {@code null}
 * @param jackpots    jackpot awards in tape order (amount fixed at draw, SLOTS.md §5.2)
 * @param totalFifths spin total EXCLUDING progressive awards, capped (fifths of the bet); owned-casino fixed
 *                    jackpots are INSIDE it (and inside the cap)
 * @param capHit      the max-win cap ended the spin (SLOTS.md §1.3)
 */
public record SpinTape(Machine machine, long bet, boolean bought, int[] stops, FreeSpins freeSpins, Hunt hunt, Hoard hoard,
		Wheel wheel, List<JackpotAward> jackpots, long totalFifths, boolean capHit) {
	/** Tape format version (SLOTS.md §8.1 {@code v:2}). */
	public static final int VERSION = 2;

	public SpinTape {
		jackpots = jackpots == null ? List.of() : List.copyOf(jackpots);
		stops = stops == null ? new int[0] : stops.clone();
	}

	@Override
	public int[] stops() {
		return stops.clone();
	}

	/** Chips credited for the spin excluding progressive awards: {@code totalFifths × bet / 5} (exact). */
	public long totalChips() {
		return totalFifths * bet / 5;
	}

	/** Chips paid from the progressive pools (0 at owned machines: their fixed jackpots are in the total). */
	public long progressiveChips() {
		long s = 0;
		for (JackpotAward j : jackpots) if (!j.owned()) s += j.chips();
		return s;
	}

	/** Everything the player receives for the spin. */
	public long payoutChips() {
		return totalChips() + progressiveChips();
	}

	/** Any jackpot of tier ≥ {@code tier}. */
	public boolean hasJackpot(int tier) {
		for (JackpotAward j : jackpots) if (j.tier() >= tier) return true;
		return false;
	}

	/** Free spins or a bonus game started (a bought feature counts, but see {@link #bought()}). */
	public boolean featureTriggered() {
		return freeSpins != null || hunt != null || hoard != null || wheel != null;
	}

	/** Same tape with the Treasure Hunt progress changed (persisted per pick, SLOTS.md §7.2 of the architecture). */
	public SpinTape withHuntOpened(int opened) {
		if (hunt == null) return this;
		return new SpinTape(machine, bet, bought, stops, freeSpins, new Hunt(hunt.entries(), Math.max(0, Math.min(opened, hunt.entries().length))), hoard,
			wheel, jackpots, totalFifths, capHit);
	}

	/** One free spin: its stops, the sticky mask AFTER it (End), whether it added spins, its pay (multiplier applied). */
	public record FreeSpin(int[] stops, int stickyMaskAfter, boolean retrigger, long payFifths) {
		public FreeSpin {
			stops = stops.clone();
		}

		@Override
		public int[] stops() {
			return stops.clone();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof FreeSpin f && Arrays.equals(f.stops, stops) && f.stickyMaskAfter == stickyMaskAfter && f.retrigger == retrigger
				&& f.payFifths == payFifths;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(stops) * 31 + (int) payFifths;
		}
	}

	/** Free-spin feature: spins awarded at trigger and every spin played (retriggers included). */
	public record FreeSpins(int awarded, List<FreeSpin> spins, long payFifths) {
		public FreeSpins {
			spins = List.copyOf(spins);
		}
	}

	/**
	 * Treasure Hunt: i.i.d. contents in REVEAL order; the i-th opened chest shows entry i whichever chest is
	 * clicked (SLOTS.md §1.2). Entries: 1,2,3,5,10,25 = coins ×bet; -1..-4 = Mini..Grand; 0 = Creeper. The board
	 * holds {@code entries.length} chests; {@code opened} = chests the player has opened so far (0 at draw).
	 */
	public record Hunt(int[] entries, int opened) {
		public Hunt {
			entries = entries.clone();
		}

		@Override
		public int[] entries() {
			return entries.clone();
		}

		/** Chests up to and including the first Creeper, or all of them (ignores the max-win cap: see {@code SlotDraw.huntOpens}). */
		public int picks() {
			for (int i = 0; i < entries.length; i++) if (entries[i] == 0) return i + 1;
			return entries.length;
		}

		/** The hunt ended on its first chest (chaos {@code mob_wave}, SLOTS.md §8.4). */
		public boolean creeperFirst() {
			return entries.length > 0 && entries[0] == 0;
		}

		/** Chests with a prize (the Creeper excluded). */
		public int prizes() {
			int p = picks();
			return p > 0 && entries[p - 1] == 0 ? p - 1 : p;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Hunt h && Arrays.equals(h.entries, entries) && h.opened == opened;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(entries) * 31 + opened;
		}
	}

	/**
	 * Piglin's Hoard: initial coin cells/values, then per respin the new coins (cell, value). Value codes as
	 * {@link Hunt#entries()} (positive = ×bet, negative = jackpot tier); 15 filled adds the Grand. Cells are window
	 * indices (reel × 3 + row), ascending within a group.
	 */
	public record Hoard(int[] initialCells, int[] initialValues, List<int[]> respinCells, List<int[]> respinValues) {
		public Hoard {
			initialCells = initialCells.clone();
			initialValues = initialValues.clone();
			respinCells = respinCells.stream().map(int[]::clone).toList();
			respinValues = respinValues.stream().map(int[]::clone).toList();
		}

		/** Coins at the end. */
		public int coins() {
			int n = initialCells.length;
			for (int[] c : respinCells) n += c.length;
			return n;
		}

		public boolean full() {
			return coins() >= 15;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Hoard h) || h.respinCells.size() != respinCells.size()) return false;
			for (int i = 0; i < respinCells.size(); i++) {
				if (!Arrays.equals(h.respinCells.get(i), respinCells.get(i)) || !Arrays.equals(h.respinValues.get(i), respinValues.get(i))) return false;
			}
			return Arrays.equals(h.initialCells, initialCells) && Arrays.equals(h.initialValues, initialValues);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(initialCells) * 31 + respinCells.size();
		}
	}

	/** Dragon Wheel: segment index per ring reached (outer, middle, core); length 1–3. */
	public record Wheel(int[] segments) {
		public Wheel {
			segments = segments.clone();
		}

		@Override
		public int[] segments() {
			return segments.clone();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Wheel w && Arrays.equals(w.segments, segments);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(segments);
		}
	}

	/**
	 * A jackpot won in this spin (tier 1 Mini … 4 Grand), amount fixed at draw time (pool debited then, so
	 * two players can never win the same money). Owned machines: fixed multiple, inside the cap.
	 */
	public record JackpotAward(int tier, long chips, boolean owned) {}

	@Override
	public boolean equals(Object o) {
		return o instanceof SpinTape t && t.machine == machine && t.bet == bet && t.bought == bought && Arrays.equals(t.stops, stops)
			&& java.util.Objects.equals(t.freeSpins, freeSpins) && java.util.Objects.equals(t.hunt, hunt) && java.util.Objects.equals(t.hoard, hoard)
			&& java.util.Objects.equals(t.wheel, wheel) && t.jackpots.equals(jackpots) && t.totalFifths == totalFifths && t.capHit == capHit;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(stops) * 31 + Long.hashCode(totalFifths);
	}
}
