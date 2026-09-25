package dev.nezo.burmaldaholic.core.config.sections;

import com.google.gson.annotations.SerializedName;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.Family;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Size;
import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import dev.nezo.burmaldaholic.core.config.Validatable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Config section `bots` — keys, defaults and ranges from docs/design/CONFIG.md (BOTS.md §9). Java edition defaults. */
public final class BotsConfig implements Validatable {
	public boolean enabled = true;
	/** Edition default: Java 24 (Bedrock 12). */
	@Range(min = 0, max = 256) public int maxActiveTables = 24;
	/** Edition default: Java 64 (Bedrock 32). */
	@Range(min = 0, max = 512) public int maxActive = 64;
	@Range(min = 1, max = 16) public int maxConcurrentJobs = 2;
	/** Easy/Normal/Hard % for MIXED outside poker. */
	@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] difficultyMix = {30, 50, 20};

	public static final class Think {
		@Range(min = 0, max = 200) public int minTicks = 20;
		@Range(min = 0, max = 400) public int maxTicks = 60;
		@Range(min = 0, max = 200) public int tankTicks = 40;
		@Range(min = 0, max = 1) public double fastFactor = 0.5;
	}
	public Think think = new Think();

	public boolean personalities = true;
	public boolean keepFreeSeatDefault = true;

	public static final class Showcase {
		public boolean enabled = false;
	}
	public Showcase showcase = new Showcase();

	/** Seating defaults of newly placed / generated tables per game (§9.2). */
	public static final class TableDefaults {
		public SeatPolicy policy = SeatPolicy.HUMANS_ONLY;
		@Range(min = 0, max = 8) public int count = 0;
		public BotDifficulty difficulty = BotDifficulty.NORMAL;
		public SeatPolicy worldgenPolicy = SeatPolicy.MIXED;
		@Range(min = 0, max = 8) public int worldgenCount = 2;

		TableDefaults with(SeatPolicy p, int c, BotDifficulty d, int wc) {
			policy = p;
			count = c;
			difficulty = d;
			worldgenCount = wc;
			return this;
		}
	}

	@Family(member = "gui.burmaldaholic.common.game.%s")
	public Map<String, TableDefaults> table = tableDefaults();

	private static Map<String, TableDefaults> tableDefaults() {
		Map<String, TableDefaults> m = new LinkedHashMap<>();
		m.put("poker", new TableDefaults().with(SeatPolicy.MIXED, 5, BotDifficulty.MIXED, 3));
		m.put("chemmy", new TableDefaults().with(SeatPolicy.MIXED, 2, BotDifficulty.MIXED, 2));
		m.put("blackjack", new TableDefaults().with(SeatPolicy.HUMANS_ONLY, 0, BotDifficulty.NORMAL, 2));
		m.put("roulette", new TableDefaults().with(SeatPolicy.HUMANS_ONLY, 0, BotDifficulty.MIXED, 3));
		m.put("craps", new TableDefaults().with(SeatPolicy.HUMANS_ONLY, 0, BotDifficulty.MIXED, 2));
		m.put("baccarat", new TableDefaults().with(SeatPolicy.HUMANS_ONLY, 0, BotDifficulty.MIXED, 2));
		m.put("uth", new TableDefaults().with(SeatPolicy.HUMANS_ONLY, 0, BotDifficulty.NORMAL, 2));
		return m;
	}

	public static final class Atmosphere {
		public static final class MaxPerTable {
			@Range(min = 0, max = 4) public int blackjack = 2;
			@Range(min = 0, max = 5) public int uth = 2;
			@Range(min = 0, max = 7) public int roulette = 3;
			@Range(min = 0, max = 5) public int craps = 3;
			@Range(min = 0, max = 6) public int baccarat = 3;
		}
		public MaxPerTable maxPerTable = new MaxPerTable();
	}
	public Atmosphere atmosphere = new Atmosphere();

	public static final class Poker {
		public StakeCap easyMaxStake = StakeCap.LOW;
	}
	public Poker poker = new Poker();

	public static final class Chemmy {
		@Range(min = 5, max = 1000) public int bankCapMultiple = 50;
	}
	public Chemmy chemmy = new Chemmy();

	public static final class Pvp {
		@Range(min = 0, max = 1800) public int fillDelayTicks = 400;
		@Range(min = 1, max = 7) public int maxPerMatch = 3;
	}
	public Pvp pvp = new Pvp();

	public static final class Tournament {
		@Range(min = 0, max = 31) public int maxFill = 8;
		public boolean fillToBracket = true;
	}
	public Tournament tournament = new Tournament();

	public static final class Owned {
		public Funding funding = Funding.OWNER_BANKROLL;
	}
	public Owned owned = new Owned();

	@Range(min = 0, max = 1000) public int tableBuyInsPerDay = 10;
	@Range(min = 0, max = 1) public double vipWagerWeight = 0.5;
	@Range(min = 0, max = 1000000000) public int dailyWinCapMin = 500;
	@Range(min = 0, max = 1000) public int dailyWinCapTierMultiple = 5;
	@Range(min = 1, max = 100) public double sulkMultiplier = 2.0;
	public boolean adaptiveHeat = true;
	public boolean debtorsMayPlay = true;

	public static final class Avatars {
		/** Edition default: Java NAMEPLATE (Bedrock NONE). */
		public AvatarMode mode = AvatarMode.NAMEPLATE;
		@Range(min = 0, max = 128) public int maxEntities = 16;
	}
	public Avatars avatars = new Avatars();

	public static final class Chatter {
		public boolean enabled = true;
		@Range(min = 0, max = 1) public double chance = 0.35;
		@Range(min = 0, max = 24000) public int botCooldownTicks = 600;
		@Range(min = 0, max = 24000) public int tableCooldownTicks = 200;
		@Range(min = 0, max = 60) public int maxPerMinute = 3;
	}
	public Chatter chatter = new Chatter();

	/** {@code bots.private.*} ({@code private} is a Java keyword). */
	public static final class PrivateTables {
		public boolean enabled = true;
		@Range(min = 1, max = 64) public int maxInvites = 16;
		@Range(min = 0, max = 1024) public int inviteRadius = 64;
	}
	@SerializedName("private")
	public PrivateTables privateTables = new PrivateTables();

	public enum StakeCap implements TranslatableEnum {
		MICRO, LOW, MID, HIGH;

		@Override
		public String translationKey() {
			return "config.burmaldaholic.bots.poker.easyMaxStake." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Funding implements TranslatableEnum {
		OWNER_BANKROLL, DISABLED;

		@Override
		public String translationKey() {
			return "config.burmaldaholic.bots.owned.funding." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum AvatarMode implements TranslatableEnum {
		NONE, NAMEPLATE, ENTITY;

		@Override
		public String translationKey() {
			return "config.burmaldaholic.bots.avatars.mode." + name().toLowerCase(Locale.ROOT);
		}
	}

	@Override
	public void validate(Issues issues) {
		if (think.maxTicks < think.minTicks) {
			issues.clamped("think.maxTicks", think.maxTicks, think.minTicks);
			think.maxTicks = think.minTicks;
		}
		table.values().forEach(t -> {
			if (t.worldgenPolicy == SeatPolicy.BOTS_ONLY) {
				t.worldgenPolicy = SeatPolicy.MIXED; // §9.2: not allowed for generated tables
			}
		});
	}
}
