package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Family;
import dev.nezo.burmaldaholic.core.config.Range;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config section `store` — the chip store catalog (EC-SPEC §3). Prices are chips per purchase of {@code count} items
 * (all defaults use {@code count = 1}). {@code limitPerDay} = purchases per player per in-game day (0 = no limit);
 * {@code limitEveryDays > 1} = after a buy day, nothing more until that many days have passed. {@code dims}: dimension
 * letters of the counter ({@code O / N / E}; empty = everywhere). Offers are checked by {@code EconomyGuards} R3–R6 when
 * the catalog is built: a violating offer is dropped and logged. Scratch cards (Games) come from {@code CashierShop}.
 */
public final class StoreConfig {
	public boolean enabled = true;

	public static final class Offer {
		public String item = "";
		@Range(min = 1, max = 64) public int count = 1;
		/** Potion id ({@code minecraft:long_night_vision}) for potion items. */
		public String potion = "";
		/** Enchantment id for enchanted books. */
		public String enchant = "";
		@Range(min = 0, max = 10) public int level = 0;
		/** Firework rocket flight duration (0 = not a rocket). */
		@Range(min = 0, max = 3) public int flight = 0;
		public String category = "food";
		@Range(min = 0, max = 100000000) public long price = 0;
		@Range(min = 0, max = 5) public int minTier = 0;
		public String dims = "";
		@Range(min = 0, max = 10000) public int limitPerDay = 0;
		@Range(min = 1, max = 365) public int limitEveryDays = 1;

		public Offer() {}

		Offer(String category, String item, long price) {
			this.category = category;
			this.item = item;
			this.price = price;
		}

		Offer tier(int t) {
			minTier = t;
			return this;
		}

		Offer limit(int perDay) {
			limitPerDay = perDay;
			return this;
		}

		Offer every(int days) {
			limitEveryDays = days;
			return this;
		}

		Offer dims(String d) {
			dims = d;
			return this;
		}

		Offer potion(String p) {
			potion = p;
			return this;
		}

		Offer book(String e, int lvl) {
			enchant = e;
			level = lvl;
			return this;
		}

		Offer flight(int f) {
			flight = f;
			return this;
		}
	}

	@Family(open = true)
	public Map<String, Offer> offers = defaults();

	static final String FOOD = "food", POTIONS = "potions", BOOKS = "books", TOOLS = "tools", DECOR = "decor", RARE = "rare";

	private static Map<String, Offer> defaults() {
		Map<String, Offer> m = new LinkedHashMap<>();
		m.put("bread", new Offer(FOOD, "minecraft:bread", 2));
		m.put("cooked_beef", new Offer(FOOD, "minecraft:cooked_beef", 3));
		m.put("cooked_porkchop", new Offer(FOOD, "minecraft:cooked_porkchop", 3));
		m.put("cooked_salmon", new Offer(FOOD, "minecraft:cooked_salmon", 3));
		m.put("pumpkin_pie", new Offer(FOOD, "minecraft:pumpkin_pie", 4));
		m.put("golden_carrot", new Offer(FOOD, "minecraft:golden_carrot", 6));
		m.put("cake", new Offer(FOOD, "minecraft:cake", 15));
		m.put("golden_apple", new Offer(FOOD, "minecraft:golden_apple", 80).limit(8));
		m.put("night_vision", new Offer(POTIONS, "minecraft:potion", 30).potion("minecraft:long_night_vision").limit(16));
		m.put("water_breathing", new Offer(POTIONS, "minecraft:potion", 35).potion("minecraft:long_water_breathing").limit(16));
		m.put("slow_falling", new Offer(POTIONS, "minecraft:potion", 40).potion("minecraft:long_slow_falling").limit(16));
		m.put("swiftness", new Offer(POTIONS, "minecraft:potion", 40).potion("minecraft:strong_swiftness").limit(16));
		m.put("healing", new Offer(POTIONS, "minecraft:potion", 40).potion("minecraft:strong_healing").limit(16));
		m.put("fire_resistance", new Offer(POTIONS, "minecraft:potion", 50).potion("minecraft:long_fire_resistance").limit(16));
		m.put("regeneration", new Offer(POTIONS, "minecraft:potion", 60).potion("minecraft:long_regeneration").limit(16).tier(1));
		m.put("strength", new Offer(POTIONS, "minecraft:potion", 70).potion("minecraft:strong_strength").limit(16).tier(2));
		m.put("book_unbreaking", new Offer(BOOKS, "minecraft:enchanted_book", 300).book("minecraft:unbreaking", 3).limit(1));
		m.put("book_feather_falling", new Offer(BOOKS, "minecraft:enchanted_book", 300).book("minecraft:feather_falling", 4).limit(1));
		m.put("book_silk_touch", new Offer(BOOKS, "minecraft:enchanted_book", 300).book("minecraft:silk_touch", 1).limit(1));
		m.put("book_protection", new Offer(BOOKS, "minecraft:enchanted_book", 400).book("minecraft:protection", 4).limit(1));
		m.put("book_sharpness", new Offer(BOOKS, "minecraft:enchanted_book", 400).book("minecraft:sharpness", 5).limit(1));
		m.put("book_efficiency", new Offer(BOOKS, "minecraft:enchanted_book", 400).book("minecraft:efficiency", 5).limit(1).tier(1));
		m.put("book_looting", new Offer(BOOKS, "minecraft:enchanted_book", 500).book("minecraft:looting", 3).limit(1).tier(2));
		m.put("book_fortune", new Offer(BOOKS, "minecraft:enchanted_book", 600).book("minecraft:fortune", 3).limit(1).tier(2));
		m.put("book_mending", new Offer(BOOKS, "minecraft:enchanted_book", 800).book("minecraft:mending", 1).limit(1).tier(2));
		m.put("fishing_rod", new Offer(TOOLS, "minecraft:fishing_rod", 10));
		m.put("spyglass", new Offer(TOOLS, "minecraft:spyglass", 25));
		m.put("saddle", new Offer(TOOLS, "minecraft:saddle", 60));
		m.put("name_tag", new Offer(TOOLS, "minecraft:name_tag", 60));
		m.put("firework_rocket", new Offer(TOOLS, "minecraft:firework_rocket", 3).flight(2));
		m.put("experience_bottle", new Offer(TOOLS, "minecraft:experience_bottle", 20).limit(64));
		m.put("ender_pearl", new Offer(TOOLS, "minecraft:ender_pearl", 25).limit(16));
		m.put("diamond_pickaxe", new Offer(TOOLS, "minecraft:diamond_pickaxe", 180).tier(1).limit(2));
		m.put("glowstone", new Offer(DECOR, "minecraft:glowstone", 4));
		m.put("sea_lantern", new Offer(DECOR, "minecraft:sea_lantern", 8));
		m.put("shulker_shell", new Offer(RARE, "minecraft:shulker_shell", 300).dims("E").tier(2).limit(4));
		m.put("totem_of_undying", new Offer(RARE, "minecraft:totem_of_undying", 750).tier(2).limit(1));
		m.put("elytra", new Offer(RARE, "minecraft:elytra", 5000).dims("E").tier(3).limit(1).every(7));
		return m;
	}
}
