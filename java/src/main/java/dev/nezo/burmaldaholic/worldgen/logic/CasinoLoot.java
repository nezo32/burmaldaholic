package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Casino loot chests {@code burmaldaholic:chests/<table>} (GAME_DESIGN §16.1–16.3). PURE.
 *
 * <p>Chests are filled in code when the structure is placed instead of by a data-driven loot table:
 * items owned by other modules may be missing in a build (a JSON loot table with one unknown item
 * fails to load completely) and the loot-table JSON/API format differs between Minecraft 26.2 and
 * 26.3. Entries whose item is not registered are left out before rolling, so the roll count stays
 * as specified.
 *
 * <p>Weights the spec leaves open (entries listed without "w") use {@link #DEFAULT_WEIGHT}
 * (same as Bedrock).
 */
public final class CasinoLoot {
	public static final int DEFAULT_WEIGHT = 20;

	private CasinoLoot() {}

	/** One weighted entry; {@code enchant} = enchanted book with one random enchantment. */
	public record Entry(String item, int min, int max, int weight, boolean enchant) {
		public Entry {
			if (min < 1 || max < min || weight < 1) {
				throw new IllegalArgumentException("bad loot entry " + item);
			}
		}
	}

	public enum Table {
		// §16.1 (4–7 rolls)
		VILLAGE_CASINO(4, 7, List.of(
			e("burmaldaholic:chip_1", 5, 20, 30),
			e("burmaldaholic:chip_5", 2, 8, 25),
			e("burmaldaholic:chip_25", 1, 3, 12),
			e("burmaldaholic:scratch_card", 1, 3, 15),
			e("minecraft:emerald", 2, 6, 12),
			e("minecraft:golden_carrot", 2, 5, 8),
			e("burmaldaholic:lucky_coin", 1, 1, 5),
			e("burmaldaholic:casino_card", 1, 1, 3))),
		// §16.2 (5–8 rolls)
		PIGLIN_PARLOR(5, 8, List.of(
			e("minecraft:gold_ingot", 4, 12, DEFAULT_WEIGHT),
			e("minecraft:gold_block", 1, 1, 8),
			e("burmaldaholic:chip_25", 2, 6, DEFAULT_WEIGHT),
			e("burmaldaholic:chip_100", 1, 2, 10),
			e("burmaldaholic:scratch_card_gold", 1, 1, 8),
			e("minecraft:netherite_scrap", 1, 1, 3),
			e("burmaldaholic:lucky_coin", 1, 1, 5))),
		// §16.3 (3–5 rolls)
		HIGH_ROLLER(3, 5, List.of(
			e("burmaldaholic:chip_100", 2, 5, DEFAULT_WEIGHT),
			e("burmaldaholic:chip_500", 1, 2, 10),
			e("minecraft:diamond", 2, 6, DEFAULT_WEIGHT),
			new Entry("minecraft:enchanted_book", 1, 1, 10, true),
			e("burmaldaholic:scratch_card_gold", 1, 2, DEFAULT_WEIGHT),
			e("burmaldaholic:golden_chip", 1, 1, 4)));

		private final int minRolls;
		private final int maxRolls;
		private final List<Entry> entries;

		Table(int minRolls, int maxRolls, List<Entry> entries) {
			this.minRolls = minRolls;
			this.maxRolls = maxRolls;
			this.entries = entries;
		}

		public int minRolls() {
			return minRolls;
		}

		public int maxRolls() {
			return maxRolls;
		}

		public List<Entry> entries() {
			return entries;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The loot table id this table stands for ({@code burmaldaholic:chests/village_casino}). */
		public String lootTableId() {
			return "burmaldaholic:chests/" + id();
		}

		public static Table byId(String id) {
			for (Table t : values()) {
				if (t.id().equals(id)) {
					return t;
				}
			}
			throw new IllegalArgumentException("unknown loot table '" + id + "'");
		}
	}

	private static Entry e(String item, int min, int max, int weight) {
		return new Entry(item, min, max, weight, false);
	}

	/** One rolled stack. */
	public record Stack(String item, int count, boolean enchant) {}

	/**
	 * Rolls a table: one stack per roll (like vanilla, stacks are not merged).
	 *
	 * @param nextInt   {@code bound -> [0, bound)} random source
	 * @param available which item ids exist in this game (others are left out before rolling)
	 */
	public static List<Stack> roll(Table table, IntUnaryOperator nextInt, Predicate<String> available) {
		List<Entry> pool = new ArrayList<>();
		int total = 0;
		for (Entry en : table.entries()) {
			if (available.test(en.item())) {
				pool.add(en);
				total += en.weight();
			}
		}
		List<Stack> out = new ArrayList<>();
		if (pool.isEmpty()) {
			return out;
		}
		int rolls = between(nextInt, table.minRolls(), table.maxRolls());
		for (int i = 0; i < rolls; i++) {
			int pick = nextInt.applyAsInt(total);
			Entry chosen = pool.getLast();
			for (Entry en : pool) {
				pick -= en.weight();
				if (pick < 0) {
					chosen = en;
					break;
				}
			}
			out.add(new Stack(chosen.item(), between(nextInt, chosen.min(), chosen.max()), chosen.enchant()));
		}
		return out;
	}

	/** Distinct random container slots for {@code stacks} stacks (vanilla-like scatter). */
	public static List<Integer> scatterSlots(int stacks, int containerSize, IntUnaryOperator nextInt) {
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < containerSize; i++) {
			free.add(i);
		}
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < Math.min(stacks, containerSize); i++) {
			out.add(free.remove(nextInt.applyAsInt(free.size())));
		}
		return out;
	}

	/** Enchantments a High Roller enchanted book can carry: {id, max level} (same list as Bedrock). */
	public static final List<Enchant> BOOK_ENCHANTMENTS = List.of(
		new Enchant("minecraft:sharpness", 5), new Enchant("minecraft:protection", 4),
		new Enchant("minecraft:efficiency", 5), new Enchant("minecraft:unbreaking", 3),
		new Enchant("minecraft:fortune", 3), new Enchant("minecraft:looting", 3),
		new Enchant("minecraft:feather_falling", 4), new Enchant("minecraft:power", 5),
		new Enchant("minecraft:mending", 1), new Enchant("minecraft:silk_touch", 1));

	public record Enchant(String id, int maxLevel) {}

	public record EnchantPick(String id, int level) {}

	/** Picks the enchantment and its level (1..max) for a book. */
	public static EnchantPick pickEnchant(IntUnaryOperator nextInt) {
		Enchant e = BOOK_ENCHANTMENTS.get(nextInt.applyAsInt(BOOK_ENCHANTMENTS.size()));
		return new EnchantPick(e.id(), 1 + nextInt.applyAsInt(e.maxLevel()));
	}

	static int between(IntUnaryOperator nextInt, int min, int max) {
		return min + nextInt.applyAsInt(max - min + 1);
	}
}
