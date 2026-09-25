package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Size;
import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import dev.nezo.burmaldaholic.core.config.Validatable;
import java.util.Arrays;
import java.util.Map;

/**
 * Config section `pvp` — keys, defaults and ranges from docs/design/CONFIG.md (PVP.md §13). Bot keys of
 * PvP live in the `bots` section ({@code bots.pvp.*}, BOTS.md §9.3 supersedes PVP.md's {@code pvp.bots.*}).
 */
public final class PvpConfig implements Validatable {
	public boolean enabled = true;
	@Range(min = 0, max = 1000) public int rakeBasisPoints = 300;
	@Range(min = 1, max = 1000000) public int minStake = 10;
	@Range(min = 2, max = 128) public int joinRadius = 16;
	@Range(min = 0, max = 256) public int announceRadius = 32;
	@Range(min = 0, max = 1000000000000L) public long announceServerWidePot = 5000L;
	@Range(min = 200, max = 6000) public int inviteTimeoutTicks = 600;
	@Range(min = 400, max = 12000) public int lobbyTimeoutTicks = 1800;
	@Range(min = 300, max = 2400) public int decisionTimeoutTicks = 300;
	@Range(min = 0, max = 200) public int countdownTicks = 60;
	@Range(min = 1, max = 5) public int maxPendingInvites = 1;
	@Range(min = 0, max = 12000) public int declineCooldownTicks = 600;
	@Range(min = 600, max = 72000) public int historyTicks = 6000;
	public boolean affectsStreak = false;
	public boolean countsTowardVip = true;
	/** Heating / rampage / legendary. */
	@Range(min = 2, max = 100) @Size(min = 3, max = 3) public int[] streakAnnounce = {3, 5, 10};
	@Range(min = 2, max = 20) public int grudgeLosses = 3;

	public static final class Taunts {
		public boolean enabled = true;
		@Range(min = 20, max = 1200) public int cooldownTicks = 100;
		@Range(min = 1, max = 50) public int maxPerMatch = 5;
	}
	public Taunts taunts = new Taunts();

	public boolean allowOwnedMachines = true;

	public static final class Coin {
		public boolean enabled = true;
		@Range(min = 0, max = 10) public int maxDoubles = 4;
	}
	public Coin coin = new Coin();

	public static final class Slots {
		public boolean enabled = true;
		@Range(min = 2, max = 6) public int maxPlayers = 6;
		@Range(min = 1, max = 20) @Size(min = 1, max = 4) public int[] spinChoices = {3, 5, 10};
		@Range(min = 0, max = 32) public int linkRadius = 8;
		@Range(min = 40, max = 400) public int spinIntervalTicks = 100;
		public boolean hotSymbol = true;
		public boolean underdogBoost = true;
		public boolean kaboom = true;
		public boolean pearlSwap = true;
		@Range(min = 0, max = 100000) public int starPoints = 500;
	}
	public Slots slots = new Slots();

	public static final class Wheel {
		public boolean enabled = true;
		@Range(min = 2, max = 16) public int maxPlayers = 8;
		@Range(min = 300, max = 2400) public int countdownTicks = 600;
		@Range(min = 20, max = 200) public int noMoreBetsTicks = 60;
		@Range(min = 1, max = 5000) public int underdogShareBasisPoints = 1000;
	}
	public Wheel wheel = new Wheel();

	public static final class Plinko {
		public boolean enabled = true;
		@Range(min = 2, max = 6) public int maxPlayers = 6;
		@Range(min = 1, max = 10) @Size(min = 1, max = 4) public int[] ballChoices = {1, 3, 5};
		@Range(min = 0, max = 32) public int linkRadius = 8;
		@Range(min = 40, max = 400) public int roundIntervalTicks = 80;
		public boolean underdogBoost = true;
		/** NICE (§10.4). */
		public boolean bumpers = false;
	}
	public Plinko plinko = new Plinko();

	public static final class Scratch {
		public boolean enabled = true;
		@Range(min = 2, max = 6) public int maxPlayers = 6;
		@Range(min = 20, max = 200) public int revealIntervalTicks = 40;
		/** Cell weights (§8.1). */
		@Range(min = 0, max = 1000) public Map<String, Integer> weights = Maps.of("coal", 30, "iron", 25, "gold", 18, "emerald", 12,
			"diamond", 6, "star", 1, "creeper", 5, "foot", 3);
		@Range(min = 0, max = 1000) public Map<String, Integer> values = Maps.of("coal", 1, "iron", 2, "gold", 3, "emerald", 5,
			"diamond", 10, "star", 25);
	}
	public Scratch scratch = new Scratch();

	// ---- NICE (§13.3) ----

	public static final class Side {
		public boolean enabled = false;
		@Range(min = 0, max = 2000) public int rakeBasisPoints = 500;
		@Range(min = 60, max = 1200) public int windowTicks = 200;
	}
	public Side side = new Side();

	public static final class Tournament {
		public boolean enabled = false;
		@Range(min = 2, max = 64) public int minPlayers = 4;
		@Range(min = 4, max = 64) public int maxPlayers = 32;
		@Range(min = 600, max = 72000) public int registrationTicks = 2400;
		@Range(min = 60, max = 2400) public int roundGapTicks = 200;
		@Range(min = 1200, max = 240000) public int windowTicks = 12000;
		@Range(min = 1, max = 20) public int maxRunsPerPlayer = 3;
		@Range(min = 0, max = 100) @Size(min = 1, max = 8) public int[] prizeSplit = {60, 30, 10};
		@Range(min = 0, max = 30) public int autoEveryDays = 0;
		@Range(min = 0, max = 23999) public int autoTimeOfDay = 13000;
		public AutoMode autoMode = AutoMode.slots;
		public AutoFormat autoFormat = AutoFormat.leaderboard;
		@Range(min = 1, max = 1000000) public int autoEntry = 100;
	}
	public Tournament tournament = new Tournament();

	public static final class Race {
		@Range(min = 5, max = 200) public int maxSpins = 30;
		@Range(min = 20, max = 400) public int intervalTicks = 60;
	}
	public Race race = new Race();

	public static final class Heist {
		@Range(min = 1, max = 10) public int rounds = 3;
	}
	public Heist heist = new Heist();

	public static final class ScratchPoker {
		public boolean enabled = false;
	}
	public ScratchPoker scratchPoker = new ScratchPoker();

	public static final class Series {
		public boolean enabled = false;
	}
	public Series series = new Series();

	/** Lower-case constants so the file values equal CONFIG.md (coin, slots, …). */
	public enum AutoMode implements TranslatableEnum {
		coin, slots, plinko, scratch;

		@Override
		public String translationKey() {
			return "gui.burmaldaholic.pvp.game." + name();
		}
	}

	public enum AutoFormat implements TranslatableEnum {
		knockout, leaderboard;

		@Override
		public String translationKey() {
			return "gui.burmaldaholic.pvp.tournament.format." + name();
		}
	}

	@Override
	public void validate(Issues issues) {
		slots.spinChoices = sortedUnique(slots.spinChoices);
		plinko.ballChoices = sortedUnique(plinko.ballChoices);
		// PVP.md §3.12: warn when PvP becomes the cheapest VIP ladder (shown on the admin page by the engine).
	}

	private static int[] sortedUnique(int[] values) {
		return Arrays.stream(values).distinct().sorted().toArray();
	}
}
