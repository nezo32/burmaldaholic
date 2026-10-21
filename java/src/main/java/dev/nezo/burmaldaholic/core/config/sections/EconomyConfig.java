package dev.nezo.burmaldaholic.core.config.sections;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.config.Family;
import dev.nezo.burmaldaholic.core.config.Migrates;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Validatable;
import dev.nezo.burmaldaholic.core.economy.EconomyGuards;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Config section `economy` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class EconomyConfig implements Validatable, Migrates {
	/** Balance cap. */
	@Range(min = 1000, max = 9007199254740991.0) public long maxBalance = 1000000000L;
	/** Credited on first join. */
	@Range(min = 0, max = 1000000) public int startingBalance = 50;
	/**
	 * Cashier exchange rows (EC-SPEC §2), by row id: {@code item} ↔ chips. {@code toChips} = chips for one item handed in,
	 * {@code fromChips} = chips for one item taken out (0 = sell-only), {@code dims} = dimension letters where the row is
	 * offered ({@code O} overworld, {@code N} nether, {@code E} end; empty = everywhere), {@code buyMinTier} = VIP tier
	 * needed to take the item out. {@code core.economy.EconomyGuards} rules R1, R2 (transitive) and R4 are enforced in {@link #validate}: a
	 * violating row loses its {@code fromChips} (no arbitrage path is left).
	 */
	public static final class ExchangeRow {
		public String item = "";
		@Range(min = 0, max = 1000000) public int toChips = 0;
		@Range(min = 0, max = 1000000) public int fromChips = 0;
		public String dims = "";
		@Range(min = 0, max = 5) public int buyMinTier = 0;

		public ExchangeRow() {}

		ExchangeRow(String item, int toChips, int fromChips, String dims, int buyMinTier) {
			this.item = item;
			this.toChips = toChips;
			this.fromChips = fromChips;
			this.dims = dims;
			this.buyMinTier = buyMinTier;
		}
	}

	@Family(open = true)
	public Map<String, ExchangeRow> exchange = exchangeDefaults();

	private static Map<String, ExchangeRow> exchangeDefaults() {
		Map<String, ExchangeRow> m = new LinkedHashMap<>();
		m.put("emerald", new ExchangeRow("minecraft:emerald", 9, 10, "ONE", 0));
		m.put("emerald_block", new ExchangeRow("minecraft:emerald_block", 81, 90, "OE", 0));
		m.put("diamond", new ExchangeRow("minecraft:diamond", 40, 55, "ONE", 0));
		m.put("iron_block", new ExchangeRow("minecraft:iron_block", 16, 27, "O", 0));
		m.put("gold_ingot", new ExchangeRow("minecraft:gold_ingot", 6, 8, "N", 0));
		m.put("netherite_scrap", new ExchangeRow("minecraft:netherite_scrap", 60, 160, "N", 4));
		return m;
	}

	public static final class Ore {
		@Range(min = 0, max = 1000) public int coal = 1;
		@Range(min = 0, max = 1000) public int copper = 1;
		@Range(min = 0, max = 1000) public int iron = 2;
		@Range(min = 0, max = 1000) public int gold = 4;
		@Range(min = 0, max = 1000) public int redstone = 1;
		@Range(min = 0, max = 1000) public int lapis = 2;
		@Range(min = 0, max = 1000) public int emerald = 15;
		@Range(min = 0, max = 1000) public int diamond = 20;
		@Range(min = 0, max = 1000) public int netherQuartz = 1;
		@Range(min = 0, max = 1000) public int netherGold = 1;
		@Range(min = 0, max = 10000) public int ancientDebris = 50;
		/** FIFO size of the placed-debris ledger. */
		@Range(min = 0, max = 65536) public int placedDebrisLedgerSize = 4096;
	}
	public Ore ore = new Ore();

	public static final class Mob {
		/** Category value (§3.4.2). */
		@Range(min = 0, max = 1000) public int common = 2;
		@Range(min = 0, max = 1000) public int creeper = 3;
		@Range(min = 0, max = 1000) public int phantom = 3;
		@Range(min = 0, max = 1000) public int pillager = 3;
		@Range(min = 0, max = 1000) public int piglin = 3;
		@Range(min = 0, max = 1000) public int enderman = 4;
		@Range(min = 0, max = 1000) public int blaze = 4;
		/** Also zoglin. */
		@Range(min = 0, max = 1000) public int hoglin = 4;
		@Range(min = 0, max = 1000) public int guardian = 4;
		@Range(min = 0, max = 1000) public int witch = 5;
		@Range(min = 0, max = 1000) public int vindicator = 5;
		@Range(min = 0, max = 1000) public int witherSkeleton = 5;
		@Range(min = 0, max = 1000) public int creaking = 5;
		@Range(min = 0, max = 1000) public int ghast = 6;
		@Range(min = 0, max = 1000) public int breeze = 8;
		@Range(min = 0, max = 1000) public int shulker = 8;
		@Range(min = 0, max = 1000) public int piglinBrute = 10;
		@Range(min = 0, max = 10000) public int evoker = 20;
		@Range(min = 0, max = 10000) public int ravager = 25;
		@Range(min = 0, max = 10000) public int elderGuardian = 100;
		@Range(min = 0, max = 100000) public int warden = 250;
		@Range(min = 0, max = 100000) public int wither = 500;
		/** First dragon kill in the world. */
		@Range(min = 0, max = 1000000) public int enderDragonFirst = 1000;
		/** Later dragon kills. */
		@Range(min = 0, max = 1000000) public int enderDragonRepeat = 200;
		/** Multiplier on Hard/Hardcore. */
		@Range(min = 0, max = 10) public double hardMultiplier = 1.25;
		/** Diminishing-returns window. */
		@Range(min = 20, max = 240000) public int windowTicks = 6000;
		/** Kills per type per window at 100 %. */
		@Range(min = 0, max = 10000) public int fullRewardKills = 20;
		/** Kills up to which 25 % is paid. */
		@Range(min = 0, max = 10000) public int reducedRewardKills = 60;
		@Range(min = 0, max = 1) public double reducedRewardFactor = 0.25;
		/** Pay for spawner-spawned mobs. */
		public boolean spawnerRewards = false;
	}
	public Mob mob = new Mob();

	public static final class Trade {
		/** Chips per trade. */
		@Range(min = 0, max = 1000) public int perTradeBase = 1;
		/** Plus chips per emerald in the trade. */
		@Range(min = 0, max = 1000) public int perEmerald = 1;
		@Range(min = 0, max = 10000) public int perTradeCap = 10;
		/** Per player per MCD. */
		@Range(min = 0, max = 1000000) public int dailyCap = 200;
	}
	public Trade trade = new Trade();


	/** v0.1 keys (and their v0.1 defaults): a value that differs from the old default moves into {@link #exchange}. */
	@Override
	public void migrate(JsonObject json) {
		move(json, "emeraldBuyRate", 8, "emerald", "toChips");
		move(json, "emeraldSellRate", 10, "emerald", "fromChips");
		move(json, "goldBuyRate", 3, "gold_ingot", "toChips");
		move(json, "goldSellRate", 12, "gold_ingot", "fromChips");
		json.remove("emeraldBuyRateGoldVip");
	}

	private static void move(JsonObject json, String legacy, int legacyDefault, String row, String field) {
		JsonElement v = json.remove(legacy);
		if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || v.getAsInt() == legacyDefault) return;
		JsonObject ex = json.has("exchange") && json.get("exchange").isJsonObject() ? json.getAsJsonObject("exchange") : new JsonObject();
		json.add("exchange", ex);
		JsonObject r = ex.has(row) && ex.get(row).isJsonObject() ? ex.getAsJsonObject(row) : new JsonObject();
		ex.add(row, r);
		if (!r.has(field)) r.addProperty(field, v.getAsInt());
	}

	@Override
	public void validate(Issues issues) {
		List<EconomyGuards.Violation> bad = rowViolations();
		for (EconomyGuards.Violation v : bad) {
			ExchangeRow r = exchange.get(v.id());
			if (r != null && r.fromChips != 0) {
				issues.invalid("exchange." + v.id() + ".fromChips", r.fromChips + " (" + v.rule() + ")", 0);
				r.fromChips = 0;
			}
		}
		if (mob.reducedRewardKills < mob.fullRewardKills) {
			issues.clamped("mob.reducedRewardKills", mob.reducedRewardKills, mob.fullRewardKills);
			mob.reducedRewardKills = mob.fullRewardKills;
		}
	}

	/** R1, R2 (transitive) and R4 (villager floor) over {@link #exchange} (T-EC1 D3). */
	public List<EconomyGuards.Violation> rowViolations() {
		return EconomyGuards.rows(guardRows(), villagerValue());
	}

	/** {@code V} of R4: chips one emerald brings through a villager trade (emerald row in + trade rewards). */
	public int villagerValue() {
		ExchangeRow em = exchange.get("emerald");
		return EconomyGuards.villagerValue(em == null ? 0 : em.toChips, trade.perTradeBase, trade.perEmerald);
	}

	/** The rows as {@link EconomyGuards} data. */
	public List<EconomyGuards.Row> guardRows() {
		List<EconomyGuards.Row> out = new ArrayList<>();
		exchange.forEach((id, r) -> out.add(new EconomyGuards.Row(id, r.item, r.toChips, r.fromChips)));
		return out;
	}
}
