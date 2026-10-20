package dev.nezo.burmaldaholic.core.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure arbitrage guards of the cashier exchange and the chip store (EC-SPEC §2). Run by {@code EconomyConfig.validate}
 * (R1, R2) and on store catalog load (R3–R6); a violation disables the row's way out / the offer and is logged.
 *
 * <ul>
 *   <li>R1: {@code toChips < fromChips} for every row with a way out.</li>
 *   <li>R2: craft equivalences ({@code A = k × B}): {@code in(A) ≤ k·out(B)} and {@code k·in(B) ≤ out(A)}.</li>
 *   <li>R3: store price per item ≥ 1.25 × pawn appraisal.</li>
 *   <li>R4: villager floor: for an item (or its residue) villagers buy {@code n} per emerald, {@code price × n ≥ V}.</li>
 *   <li>R5: store price per item &gt; that item's exchange {@code toChips}.</li>
 *   <li>R6: no forbidden item in the catalog.</li>
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

	/** True when {@code item} may never be sold (R6): the list, every spawn egg and every (coloured) shulker box. */
	public static boolean forbidden(String item) {
		return FORBIDDEN.contains(item) || item.endsWith("_spawn_egg") || item.endsWith("shulker_box");
	}

	/** R1 + R2 over the exchange rows (rows with {@code fromChips = 0} are sell-only and never broken by R1). */
	public static List<Violation> rows(Collection<Row> rows) {
		List<Violation> out = new ArrayList<>();
		Map<String, Row> byItem = new HashMap<>();
		for (Row r : rows) {
			if (r.toChips() <= 0 && r.fromChips() <= 0) continue;
			byItem.put(r.item(), r);
			if (r.fromChips() > 0 && r.toChips() >= r.fromChips()) {
				out.add(new Violation("R1", r.id(), r.toChips() + " >= " + r.fromChips()));
			}
		}
		for (Equivalence e : EQUIVALENCES) {
			Row a = byItem.get(e.big());
			Row b = byItem.get(e.small());
			if (a == null || b == null) continue;
			// craft k × B into A: buy B out, hand A in
			if (b.fromChips() > 0 && a.toChips() > (long) e.k() * b.fromChips()) out.add(new Violation("R2", b.id(), "in(" + a.id() + ") > k*out"));
			// uncraft A into k × B: buy A out, hand B in
			if (a.fromChips() > 0 && (long) e.k() * b.toChips() > a.fromChips()) out.add(new Violation("R2", a.id(), "k*in(" + b.id() + ") > out"));
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
		Map<String, Row> byItem = new HashMap<>();
		for (Row r : rows) byItem.put(r.item(), r);
		for (Offer o : offers) {
			double unit = o.perItem();
			if (forbidden(o.item())) {
				out.add(new Violation("R6", o.id(), o.item()));
				continue;
			}
			Integer ap = appraisal.get(o.item());
			if (ap != null && ap > 0 && unit < 1.25 * ap) out.add(new Violation("R3", o.id(), unit + " < 1.25*" + ap));
			String buyer = VILLAGER_BUYS.containsKey(o.item()) ? o.item() : RESIDUE.get(o.item());
			Integer n = buyer == null ? null : VILLAGER_BUYS.get(buyer);
			if (n != null && unit * n < villagerValue) out.add(new Violation("R4", o.id(), unit + "*" + n + " < " + villagerValue));
			Row r = byItem.get(o.item());
			if (r != null && r.toChips() > 0 && unit <= r.toChips()) out.add(new Violation("R5", o.id(), unit + " <= " + r.toChips()));
		}
		return out;
	}

	/** {@code V} of R4: chips one emerald brings through a villager trade. */
	public static int villagerValue(int emeraldToChips, int perTradeBase, int perEmerald) {
		return emeraldToChips + perTradeBase + perEmerald;
	}
}
