package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure post-settlement mappings of a spin: the chaos trigger (SLOTS.md §8.4, at most one per spin, by priority),
 * the advancements it earns (§14) and the facts other modules need (features, tumbles, top-symbol 5-kinds).
 * Bedrock twin: {@code games/slots/v2/logic/outcomes.ts}.
 */
public final class SlotOutcomes {
	private SlotOutcomes() {}

	/**
	 * Facts of a spin derived from its tape.
	 *
	 * @param scatters       base-window scatters (0 for a bought feature)
	 * @param maxTumbles     most tumbles in one reel spin (base or free)
	 * @param baseTumbles    tumbles of the base spin
	 * @param fiveTopBase    5 of the top symbol on the base spin (any tumble step)
	 * @param fiveTopAny     … on the base spin or any free spin
	 * @param finalSticky    End: sticky mask at the end of Void Walker
	 */
	public record Facts(int scatters, int maxTumbles, int baseTumbles, boolean fiveTopBase, boolean fiveTopAny, int finalSticky) {}

	public static Facts facts(MachineDef def, SpinTape t) {
		int scat = 0;
		int maxT = 0;
		int baseT = 0;
		boolean fiveBase = false;
		boolean fiveAny = false;
		if (!t.bought()) {
			SpinEval b = SpinEval.of(def, t.stops(), false, 0);
			scat = b.scatters();
			baseT = b.tumbles();
			maxT = baseT;
			fiveBase = b.fiveTop();
			fiveAny = fiveBase;
		}
		int mask = 0;
		if (t.freeSpins() != null) {
			for (SpinTape.FreeSpin f : t.freeSpins().spins()) {
				SpinEval e = SpinEval.of(def, f.stops(), true, mask);
				mask = e.stickyAfter();
				maxT = Math.max(maxT, e.tumbles());
				fiveAny |= e.fiveTop();
			}
		}
		return new Facts(scat, maxT, baseT, fiveBase, fiveAny, mask);
	}

	/**
	 * The chaos event of a spin.
	 *
	 * @param priority   1 highest (SLOTS.md §8.4 table)
	 * @param event      chaos event id; {@code "jackpot"} = the jackpot celebration (diamond rain + chip shower + announce)
	 * @param messageKey flavour line sent to the winner when the event runs (null = none / decided by the caller)
	 * @param arg        message argument (tumble count) or 0
	 */
	public record ChaosPick(int priority, String event, String messageKey, int arg) {}

	/**
	 * Highest-priority chaos trigger of a settled spin, or null.
	 *
	 * @param stake       chips staked (bet, or the buy price)
	 * @param bigWinRoll  the 30 % draw of the big-win rule (§13.1-3) came up (drawn by the caller, fair)
	 */
	public static ChaosPick chaos(MachineDef def, SpinTape t, long stake, boolean bigWinRoll) {
		Facts f = facts(def, t);
		if (t.hasJackpot(Jackpots.MAJOR)) return new ChaosPick(1, "jackpot", null, 0);
		if (!t.jackpots().isEmpty()) return new ChaosPick(2, "chip_shower", null, 0);
		if (!t.bought() && t.freeSpins() != null && f.scatters() >= 5) return new ChaosPick(3, "golden_hour", null, 0);
		switch (def.machine()) {
			case OVERWORLD -> {
				if (t.hunt() != null && t.hunt().creeperFirst()) return new ChaosPick(4, "mob_wave", "msg.burmaldaholic.slots.creeper_friends", 0);
			}
			case NETHER -> {
				if (f.fiveTopBase()) return new ChaosPick(4, "mob_wave", "msg.burmaldaholic.slots.wither_skulls", 0);
				if (f.baseTumbles() >= 6) return new ChaosPick(4, "lucky_buff", "msg.burmaldaholic.slots.tumble_chain", f.baseTumbles());
			}
			case END -> {
				if (f.fiveTopBase()) return new ChaosPick(4, "random_teleport", "msg.burmaldaholic.slots.dragon_fling", 0);
				if (t.freeSpins() != null && f.finalSticky() == 0b111) return new ChaosPick(4, "xp_fountain", "msg.burmaldaholic.slots.void_walker", 0);
			}
		}
		long net = t.payoutChips() - stake;
		if (bigWinRoll && net >= 50 * stake && net >= 500) return new ChaosPick(5, "lucky_buff", null, 0);
		return null;
	}

	/** Advancement ids of SLOTS.md §14 earned by the spin (PvP spins never call this). */
	public static List<String> advancements(MachineDef def, SpinTape t) {
		Facts f = facts(def, t);
		List<String> out = new ArrayList<>();
		if (f.fiveTopAny()) out.add("top_five");
		if (t.hasJackpot(Jackpots.MAJOR)) out.add("jackpot");
		if (!t.jackpots().isEmpty()) out.add("mini_jackpot");
		if (t.freeSpins() != null && !t.bought()) out.add("free_spins");
		if (t.hunt() != null) {
			int opens = SlotDraw.huntOpens(def, t);
			int prizes = opens > 0 && t.hunt().entries()[opens - 1] == 0 ? opens - 1 : opens;
			if (prizes >= 10) out.add("treasure_hunter");
		}
		if (def.machine() == Machine.NETHER && f.maxTumbles() >= 6) out.add("tumble_six");
		if (t.hoard() != null && t.hoard().full()) out.add("hoard_full");
		if (t.freeSpins() != null && def.stickyWilds() && f.finalSticky() == 0b111) out.add("void_walker");
		if (t.wheel() != null && t.wheel().segments().length >= 3) out.add("dragon_core");
		if (t.totalFifths() >= 500) out.add("epic_win");
		if (t.capHit()) out.add("max_win");
		return out;
	}
}
