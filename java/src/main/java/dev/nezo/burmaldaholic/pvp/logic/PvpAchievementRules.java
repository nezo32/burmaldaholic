package dev.nezo.burmaldaholic.pvp.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The PvP advancements owned by the pvp module (PVP.md §11): {@code pvp_first_win}, {@code pvp_all_in},
 * {@code pvp_full_house}, {@code pvp_revenge}, {@code pvp_rampage}. Pure; the module gathers the {@link Facts}
 * per human participant at settlement. Bots never count as opponents for full house / revenge / rampage
 * (§3.15.2) and a bot-only match neither extends nor breaks the rampage streak; first win and all-in may be
 * earned against bots.
 */
public final class PvpAchievementRules {
	public static final String FIRST_WIN = "pvp_first_win";
	public static final String ALL_IN = "pvp_all_in";
	public static final String FULL_HOUSE = "pvp_full_house";
	public static final String REVENGE = "pvp_revenge";
	public static final String RAMPAGE = "pvp_rampage";
	public static final List<String> IDS = List.of(FIRST_WIN, ALL_IN, FULL_HOUSE, REVENGE, RAMPAGE);

	/** Distinct human opponents needed for {@code pvp_full_house}. */
	public static final int FULL_HOUSE_OPPONENTS = 5;
	/** Win streak and distinct opponents for {@code pvp_rampage}. */
	public static final int RAMPAGE_STREAK = 5;
	public static final int RAMPAGE_OPPONENTS = 2;

	private PvpAchievementRules() {}

	/**
	 * Rampage streak of one player: wins in a row, counted only in matches with ≥ 1 human opponent, and the
	 * distinct human opponents met during the current streak.
	 */
	public record Streak(int wins, Set<String> opponents) {
		public static final Streak NONE = new Streak(0, Set.of());

		public Streak {
			opponents = Set.copyOf(opponents);
		}

		/** Streak after a match; {@code humanOpponents} empty = bot-only match (no change). A split win counts as a win. */
		public Streak after(boolean won, Collection<String> humanOpponents) {
			if (humanOpponents.isEmpty()) {
				return this;
			}
			if (!won) {
				return NONE;
			}
			Set<String> all = new LinkedHashSet<>(opponents);
			all.addAll(humanOpponents);
			return new Streak(wins + 1, all);
		}

		public boolean rampage() {
			return wins >= RAMPAGE_STREAK && opponents.size() >= RAMPAGE_OPPONENTS;
		}
	}

	/**
	 * One human participant's view of a settled match.
	 *
	 * @param won            in the outcome's winners (a split win counts)
	 * @param allIn          the participant's escrow left them at balance 0 (§3.11.5)
	 * @param humanOpponents distinct human opponents in the match
	 * @param grudge         the match was a grudge match (2 humans)
	 * @param underdog       this player was the side on the losing run of that grudge match
	 * @param streakAfter    rampage streak after this match
	 */
	public record Facts(boolean won, boolean allIn, int humanOpponents, boolean grudge, boolean underdog, Streak streakAfter) {}

	/** Advancement ids earned by this match (possibly already owned; granting is idempotent). */
	public static List<String> earned(Facts f) {
		List<String> out = new ArrayList<>();
		if (f.won()) {
			out.add(FIRST_WIN);
			if (f.allIn()) {
				out.add(ALL_IN);
			}
			if (f.humanOpponents() >= FULL_HOUSE_OPPONENTS) {
				out.add(FULL_HOUSE);
			}
			if (f.grudge() && f.underdog() && f.humanOpponents() >= 1) {
				out.add(REVENGE);
			}
		}
		if (f.streakAfter() != null && f.streakAfter().rampage()) {
			out.add(RAMPAGE);
		}
		return out;
	}

	/**
	 * Was the winner of a grudge match the underdog? Works whether the engine updated the head-to-head record
	 * before or after the settle event: a run still ≤ −grudgeLosses (not updated yet) or exactly +1 (a losing
	 * run just ended; the favoured side would be at +grudgeLosses or more) both mean yes.
	 *
	 * @param runVsLoser the winner's run against the loser as read at settlement
	 */
	public static boolean underdogWon(int runVsLoser, int grudgeLosses) {
		return runVsLoser <= -grudgeLosses || runVsLoser == 1;
	}
}
