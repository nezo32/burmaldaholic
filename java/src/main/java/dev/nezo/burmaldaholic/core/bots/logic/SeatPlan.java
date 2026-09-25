package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What happens to the bots of one table at a safe point (BOTS.md §2.2, §3, §5.1, §5.4, §7.5). Pure and
 * deterministic: given the table's seats, humans, claimants, the seated bots and every limit, it decides
 * which bots leave (in order), which claimants get a seat and how many new bots join. Same rules as
 * Bedrock {@code core/logic/bots/seating.ts}. {@code TableBots} applies the plan through the game hooks.
 *
 * <p>Order of limits: {@link SeatingMath#wantedBots} → atmosphere cap → owner Bots mode / max bots →
 * forced departures (busted, heat) → yielders for claimants and excess → new joins limited by the free
 * seats, the world budget ({@code bots.maxActive}, {@code bots.maxActiveTables}) and the purse.
 */
public final class SeatPlan {
	private SeatPlan() {}

	/**
	 * A seated bot at the safe point.
	 *
	 * @param houseFunded BANK purse (subject to heat)
	 * @param busted      a money bot with nothing left (leaves; replaced if still wanted and affordable)
	 */
	public record Bot(String key, int seat, long stack, int joinOrder, boolean banker, int handsSinceBigBlind, BotDifficulty level,
			boolean houseFunded, boolean busted) {
		SeatingMath.YieldCandidate candidate() {
			return new SeatingMath.YieldCandidate(key, seat, stack, joinOrder, banker, handsSinceBigBlind);
		}
	}

	/**
	 * @param enabled        {@code bots.enabled}
	 * @param seats          seats for occupants (no dealer seat)
	 * @param humans         humans seated now
	 * @param claimants      valid claimants in claim order
	 * @param atmosphereCap  {@code bots.atmosphere.maxPerTable.<game>} (ignored for MONEY)
	 * @param worldBudget    bots that may still join world-wide ({@code bots.maxActive} − active, this table's
	 *                       seated bots counted as active; the plan adds back the ones it makes leave)
	 * @param tableSlot      this table may host bots ({@code bots.maxActiveTables}; true if it already has some)
	 * @param affordableNew  new money bots the purse can fund ({@link BotEconomyMath#affordable}; daily buy-ins)
	 * @param hardOnly       "Word got around" at this table (poker, house purse): only HARD bots
	 * @param sulk           a seated human is sulked at and the purse is the bank: house bots leave
	 * @param relevel        a new concrete difficulty was just applied: bots of another level leave and are
	 *                       replaced (null = keep the bots as they are, e.g. MIXED or no change)
	 */
	public record Input(BotSettings settings, boolean enabled, int seats, int humans, List<UUID> claimants, List<Bot> bots,
			SeatingMath.YieldRule rule, OwnerControls owner, BotRole role, int atmosphereCap, int worldBudget, boolean tableSlot,
			int affordableNew, boolean hardOnly, boolean sulk, BotDifficulty relevel) {
		public Input {
			Objects.requireNonNull(settings);
			claimants = List.copyOf(claimants);
			bots = List.copyOf(bots);
		}
	}

	/** Why fewer bots join than wanted (drives {@code none_available} / {@code regulars_gone} / {@code bankroll_short}). */
	public enum Limit { NONE, WORLD, PURSE }

	/**
	 * @param leave           bot keys that leave, in order (forced first, then yield order)
	 * @param yielded         the subset of {@code leave} that gave its seat to a claimant (for the {@code yield} quip)
	 * @param join            new bots to seat
	 * @param forcedLevel     level of new bots (HARD under heat), null = the setting
	 * @param seatedClaimants claimants that get a seat now (the game seats them in the freed seats)
	 * @param target          bots the table should have after the safe point (before the purse/world limits)
	 */
	public record Plan(List<String> leave, List<String> yielded, int join, BotDifficulty forcedLevel, List<UUID> seatedClaimants,
			int target, Limit limit) {}

	/** Bot seats the policy and hard caps allow (before purse / world limits on NEW bots). */
	public static int target(Input in) {
		if (!in.enabled()) {
			return 0;
		}
		int t = SeatingMath.wantedBots(in.settings(), in.seats(), in.humans(), in.claimants().size());
		if (in.role() == BotRole.ATMOSPHERE) {
			t = Math.min(t, Math.max(0, in.atmosphereCap()));
		}
		if (!in.owner().botsMode().allows(in.role())) {
			return 0;
		}
		t = Math.min(t, Math.max(0, in.owner().maxBots()));
		if (in.sulk() && in.role() == BotRole.MONEY) {
			return 0;
		}
		return Math.max(0, Math.min(t, in.seats() - Math.min(in.seats(), in.humans())));
	}

	public static Plan plan(Input in) {
		int target = target(in);
		Set<String> leave = new LinkedHashSet<>();
		List<Bot> remaining = new ArrayList<>();
		for (Bot b : in.bots()) {
			boolean forced = b.busted() || (in.relevel() != null && !in.relevel().isMixed() && b.level() != in.relevel())
				|| (in.role() == BotRole.MONEY && b.houseFunded() && (in.sulk() || (in.hardOnly() && b.level() != BotDifficulty.HARD)));
			if (forced) {
				leave.add(b.key());
			} else {
				remaining.add(b);
			}
		}
		List<String> order = SeatingMath.yieldOrder(in.rule(), remaining.stream().map(Bot::candidate).toList());
		int keep = Math.min(remaining.size(), target);
		// every human + claimant needs a seat: yield more if the target (e.g. keep-free off) leaves too few
		int free = in.seats() - in.humans() - keep;
		while (keep > 0 && free < in.claimants().size()) {
			keep--;
			free++;
		}
		int excess = remaining.size() - keep;
		List<String> yielded = new ArrayList<>();
		for (int i = 0; i < excess; i++) {
			leave.add(order.get(i));
		}
		int claimantsSeated = Math.max(0, Math.min(in.claimants().size(), free));
		List<UUID> seated = in.claimants().subList(0, claimantsSeated);
		// the first leavers in yield order are the ones that made room for claimants
		int yieldCount = Math.min(excess, claimantsSeated);
		for (int i = 0; i < yieldCount; i++) {
			yielded.add(order.get(i));
		}
		int freeAfter = free - claimantsSeated;
		int join = Math.max(0, Math.min(target - keep, freeAfter));
		Limit limit = Limit.NONE;
		if (join > 0 && keep == 0 && !in.tableSlot()) {
			join = 0;
			limit = Limit.WORLD;
		}
		// the budget was taken with this table's bots still seated: the ones leaving now free their slots
		// (a relevel / busted replacement at a world-full table must not end with no bots)
		int budget = Math.max(0, in.worldBudget()) + leave.size();
		if (join > budget) {
			join = budget;
			limit = Limit.WORLD;
		}
		if (in.role() == BotRole.MONEY && join > Math.max(0, in.affordableNew())) {
			join = Math.max(0, in.affordableNew());
			limit = Limit.PURSE;
		}
		BotDifficulty forcedLevel = in.hardOnly() && in.role() == BotRole.MONEY ? BotDifficulty.HARD : null;
		return new Plan(List.copyOf(leave), List.copyOf(yielded), join, forcedLevel, List.copyOf(seated), target, limit);
	}
}
