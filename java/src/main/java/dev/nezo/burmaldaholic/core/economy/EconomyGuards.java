package dev.nezo.burmaldaholic.core.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure arbitrage guards of the cashier exchange and the chip store (EC-SPEC §2). Run by {@code EconomyConfig.validate}
 * (R1, R2, R4 on rows) and on store catalog load (R3–R6); a violation disables the row's way out / the offer and is
 * logged.
 *
 * <ul>
 *   <li>R1: {@code toChips < fromChips} for every row with a way out.</li>
 *   <li>R2: every pair of rows of the same item (duplicates, per-dimension variants) and of craft equivalences ({@code A = k × B}), transitively ({@link #CHAINS}: block ↔ ingot ↔ nugget = 81):
 *   {@code in(A) ≤ k·out(B)} and {@code k·in(B) ≤ out(A)}.</li>
 *   <li>R3: store price per item ≥ 1.25 × pawn appraisal.</li>
 *   <li>R4: villager floor: for an item (or its residue, or what it uncrafts into) villagers buy {@code n} per
 *   emerald, {@code price × n ≥ V}; for store offers and for exchange rows' way out.</li>
 *   <li>R5: store price per item &gt; every exchange {@code toChips} of that item (or, through {@link #CHAINS}, what it
 *   crafts into).</li>
 *   <li>R6: no forbidden item in the catalog (incl. everything that smelts into iron / gold nuggets).</li>
 * </ul>
 */
public final class EconomyGuards {
	private EconomyGuards() {}

	/** An exchange row: {@code item} id, chips per item in, chips per item out (0 = sell-only). */
	public record Row(String id, String item, int toChips, int fromChips) {}

	/** A store offer: {@code count} items of {@code item} for {@code price} chips. */
	public record Offer(String id, String item, int count, long price) {
		public double perItem() {
			return count <= 0 ? price : price / (double) count;
		}
	}

	/** One broken rule ({@code R1}…{@code R6}) of row / offer {@code id}. */
	public record Violation(String rule, String id, String detail) {}

	/** {@code big = k × small} (crafting both ways). */
	public record Equivalence(String big, int k, String small) {}

	public static final List<Equivalence> EQUIVALENCES = List.of(
		new Equivalence("minecraft:emerald_block", 9, "minecraft:emerald"),
		new Equivalence("minecraft:diamond_block", 9, "minecraft:diamond"),
		new Equivalence("minecraft:iron_block", 9, "minecraft:iron_ingot"),
		new Equivalence("minecraft:iron_ingot", 9, "minecraft:iron_nugget"),
		new Equivalence("minecraft:gold_block", 9, "minecraft:gold_ingot"),
		new Equivalence("minecraft:gold_ingot", 9, "minecraft:gold_nugget"),
		new Equivalence("minecraft:netherite_block", 9, "minecraft:netherite_ingot"));

	/** {@link #EQUIVALENCES} closed transitively: every {@code big = k × small} reachable by crafting (k multiplied). */
	public static final List<Equivalence> CHAINS = closure(EQUIVALENCES);

	private static List<Equivalence> closure(List<Equivalence> base) {
		Map<String, Map<String, Integer>> down = new HashMap<>(); // big -> small -> k
		for (Equivalence e : base) down.computeIfAbsent(e.big(), x -> new HashMap<>()).put(e.small(), e.k());
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Map<String, Integer> smalls : down.values()) {
				for (Map.Entry<String, Integer> m : List.copyOf(smalls.entrySet())) {
					Map<String, Integer> next = down.get(m.getKey());
					if (next == null) continue;
					for (Map.Entry<String, Integer> n : next.entrySet()) {
						if (smalls.putIfAbsent(n.getKey(), m.getValue() * n.getValue()) == null) changed = true;
					}
				}
			}
		}
		List<Equivalence> out = new ArrayList<>();
		down.forEach((big, smalls) -> smalls.forEach((small, k) -> out.add(new Equivalence(big, k, small))));
		return List.copyOf(out);
	}

	/** Items villagers buy from players: items per emerald (vanilla's most generous trade). */
	public static final Map<String, Integer> VILLAGER_BUYS = Map.ofEntries(
		Map.entry("minecraft:glass_bottle", 9), Map.entry("minecraft:paper", 24), Map.entry("minecraft:book", 4),
		Map.entry("minecraft:stick", 32), Map.entry("minecraft:string", 20), Map.entry("minecraft:coal", 15),
		Map.entry("minecraft:wheat", 20), Map.entry("minecraft:pumpkin", 6), Map.entry("minecraft:melon", 4),
		Map.entry("minecraft:carrot", 22), Map.entry("minecraft:potato", 26), Map.entry("minecraft:beetroot", 15),
		Map.entry("minecraft:rotten_flesh", 32), Map.entry("minecraft:leather", 6), Map.entry("minecraft:feather", 24),
		Map.entry("minecraft:flint", 26), Map.entry("minecraft:clay_ball", 10), Map.entry("minecraft:quartz", 12),
		Map.entry("minecraft:white_wool", 18), Map.entry("minecraft:glass_pane", 11), Map.entry("minecraft:sweet_berries", 10),
		Map.entry("minecraft:dried_kelp_block", 10), Map.entry("minecraft:nether_wart", 22), Map.entry("minecraft:ink_sac", 5),
		Map.entry("minecraft:cod", 15), Map.entry("minecraft:salmon", 13), Map.entry("minecraft:chicken", 14),
		Map.entry("minecraft:porkchop", 7), Map.entry("minecraft:beef", 10), Map.entry("minecraft:mutton", 7),
		Map.entry("minecraft:rabbit", 4), Map.entry("minecraft:iron_ingot", 4), Map.entry("minecraft:diamond", 1),
		Map.entry("minecraft:gold_ingot", 3), Map.entry("minecraft:tripwire_hook", 8), Map.entry("minecraft:compass", 1),
		Map.entry("minecraft:lava_bucket", 1), Map.entry("minecraft:rabbit_foot", 2), Map.entry("minecraft:turtle_scute", 4),
		Map.entry("minecraft:armadillo_scute", 4), Map.entry("minecraft:rabbit_hide", 9));

	/** What is left of an item after use that villagers may buy (potion → glass bottle). */
	public static final Map<String, String> RESIDUE = Map.of(
		"minecraft:potion", "minecraft:glass_bottle",
		"minecraft:splash_potion", "minecraft:glass_bottle",
		"minecraft:lingering_potion", "minecraft:glass_bottle",
		"minecraft:honey_bottle", "minecraft:glass_bottle");

	/** R6: never sold in the store (exchange items and their precursors, and the rares that break progression). */
	public static final Set<String> FORBIDDEN = Set.of(
		"minecraft:emerald", "minecraft:emerald_block", "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
		"minecraft:diamond", "minecraft:diamond_block", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:iron_block", "minecraft:raw_iron", "minecraft:raw_iron_block",
		"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
		"minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:gold_block", "minecraft:raw_gold", "minecraft:raw_gold_block",
		"minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
		"minecraft:netherite_scrap", "minecraft:ancient_debris", "minecraft:netherite_ingot", "minecraft:netherite_block",
		"minecraft:netherite_upgrade_smithing_template",
		"minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:iron_shovel", "minecraft:iron_hoe", "minecraft:iron_sword",
		"minecraft:golden_pickaxe", "minecraft:golden_axe", "minecraft:golden_shovel", "minecraft:golden_hoe", "minecraft:golden_sword",
		"minecraft:spawner", "minecraft:trial_spawner", "minecraft:enchanted_golden_apple", "minecraft:nether_star", "minecraft:beacon",
		"minecraft:shulker_box", "minecraft:dragon_egg");

	/** Gear that smelts into iron / gold nuggets ({@code <material>_<piece>}, R6). */
	private static final List<String> SMELT_MATERIALS = List.of("iron_", "golden_", "chainmail_");
	private static final List<String> SMELT_PIECES = List.of("helmet", "chestplate", "leggings", "boots", "horse_armor",
		"nautilus_armor", "sword", "pickaxe", "axe", "shovel", "hoe", "spear");

	/**
	 * True when {@code item} may never be sold (R6): the list, every spawn egg, every (coloured) shulker box and every
	 * iron / gold / chainmail tool, weapon or armour piece (they smelt into nuggets).
	 */
	public static boolean forbidden(String item) {
		if (FORBIDDEN.contains(item) || item.endsWith("_spawn_egg") || item.endsWith("shulker_box")) return true;
		if (!item.startsWith("minecraft:")) return false;
		String path = item.substring("minecraft:".length());
		for (String m : SMELT_MATERIALS) {
			if (path.startsWith(m) && SMELT_PIECES.contains(path.substring(m.length()))) return true;
		}
		return false;
	}

	/**
	 * How many {@code item}s make one emerald through villagers: the item itself, what it leaves behind
	 * ({@link #RESIDUE}) or what one item uncrafts into ({@link #CHAINS}); the most generous route. {@code null} when
	 * villagers buy none of them.
	 */
	static Double itemsPerEmerald(String item) {
		Double best = null;
		Integer n = VILLAGER_BUYS.get(item);
		if (n != null) best = (double) n;
		String residue = RESIDUE.get(item);
		if (residue != null && VILLAGER_BUYS.containsKey(residue)) best = min(best, VILLAGER_BUYS.get(residue));
		for (Equivalence e : CHAINS) {
			if (e.big().equals(item) && VILLAGER_BUYS.containsKey(e.small())) best = min(best, VILLAGER_BUYS.get(e.small()) / (double) e.k());
		}
		return best;
	}

	/**
	 * How many {@code to} one {@code from} becomes by crafting for free: 1 for the same item, {@code k} when it uncrafts
	 * into {@code k} of them, {@code 1/k} when {@code k} of it craft into one ({@link #CHAINS}); {@code null} = unrelated.
	 */
	static Double itemsFrom(String from, String to) {
		if (from.equals(to)) return 1.0;
		for (Equivalence e : CHAINS) {
			if (e.big().equals(from) && e.small().equals(to)) return (double) e.k();
			if (e.small().equals(from) && e.big().equals(to)) return 1.0 / e.k();
		}
		return null;
	}

	private static Double min(Double a, double b) {
		return a == null ? b : Math.min(a, b);
	}

	/** R1 + R2 over the exchange rows, without the villager floor (see {@link #rows(Collection, int)}). */
	public static List<Violation> rows(Collection<Row> rows) {
		return rows(rows, 0);
	}

	/**
	 * R1 + R2 + R4 over the exchange rows (rows with {@code fromChips = 0} are sell-only and never broken).
	 *
	 * @param villagerValue {@code V} of R4 ({@link #villagerValue}); 0 skips R4
	 */
	public static List<Violation> rows(Collection<Row> rows, int villagerValue) {
		List<Violation> out = new ArrayList<>();
		List<Row> live = new ArrayList<>();
		for (Row r : rows) {
			if (r.toChips() <= 0 && r.fromChips() <= 0) continue;
			live.add(r);
			if (r.fromChips() > 0 && r.toChips() >= r.fromChips()) {
				out.add(new Violation("R1", r.id(), r.toChips() + " >= " + r.fromChips()));
			}
			Double per = r.fromChips() > 0 && villagerValue > 0 ? itemsPerEmerald(r.item()) : null;
			if (per != null && r.fromChips() * per < villagerValue - 1e-9) {
				out.add(new Violation("R4", r.id(), r.fromChips() + "*" + per + " < " + villagerValue));
			}
		}
		// every ordered pair of distinct rows (same item, or related by a crafting chain): buy one Y out of row b, turn it
		// into q × X for free and hand those in at row a; profitable when q·in(a) > out(b) -> b's way out is disabled
		for (Row b : live) {
			if (b.fromChips() <= 0) continue;
			for (Row a : live) {
				if (a == b || a.toChips() <= 0) continue;
				Double q = itemsFrom(b.item(), a.item());
				if (q != null && q * a.toChips() > b.fromChips() + 1e-9) {
					out.add(new Violation("R2", b.id(), q + "*in(" + a.id() + ") > out"));
					break;
				}
			}
		}
		return out;
	}

	/**
	 * R3–R6 over the store offers.
	 *
	 * @param appraisal     pawn appraisal per item id ({@code wager.appraisal})
	 * @param rows          the exchange rows (R5)
	 * @param villagerValue chips one emerald brings through a villager: {@code toChips(emerald) + perTradeBase + perEmerald}
	 */
	public static List<Violation> offers(Collection<Offer> offers, Map<String, Integer> appraisal, Collection<Row> rows, int villagerValue) {
		List<Violation> out = new ArrayList<>();
		for (Offer o : offers) {
			double unit = o.perItem();
			if (forbidden(o.item())) {
				out.add(new Violation("R6", o.id(), o.item()));
				continue;
			}
			Integer ap = appraisal.get(o.item());
			if (ap != null && ap > 0 && unit < 1.25 * ap) out.add(new Violation("R3", o.id(), unit + " < 1.25*" + ap));
			Double n = itemsPerEmerald(o.item());
			if (n != null && unit * n < villagerValue) out.add(new Violation("R4", o.id(), unit + "*" + n + " < " + villagerValue));
			for (Row r : rows) { // every row of the same item or one it crafts into / uncrafts into (T-EC1 D6)
				Double q = r.toChips() > 0 ? itemsFrom(o.item(), r.item()) : null;
				if (q != null && unit <= q * r.toChips() + 1e-9) {
					out.add(new Violation("R5", o.id(), unit + " <= " + q + "*" + r.toChips() + " (" + r.id() + ")"));
					break;
				}
			}
		}
		return out;
	}

	/** {@code V} of R4: chips one emerald brings through a villager trade. */
	public static int villagerValue(int emeraldToChips, int perTradeBase, int perEmerald) {
		return emeraldToChips + perTradeBase + perEmerald;
	}
}
