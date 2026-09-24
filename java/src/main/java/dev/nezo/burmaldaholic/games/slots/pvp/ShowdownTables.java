package dev.nezo.burmaldaholic.games.slots.pvp;

import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.util.EnumMap;
import java.util.Map;

/**
 * The 3×3 machine tables Slot Showdown (PVP.md §5) still plays on. The slots v2 cut-over retired the v1 machines and
 * their {@code slots.<tier>.*} config keys, so the Showdown keeps the last v1 defaults (GAME_DESIGN.md §8) frozen here
 * until it moves to the v2 engine (SLOTS.md §9, Slot Showdown v2). No progressive star pays: a Showdown has no pool.
 */
final class ShowdownTables {
	private static final Map<Tier, SlotTable> TABLES = new EnumMap<>(Tier.class);
	private static final double[] BERRY_PARTIAL = {2, 3};

	static {
		TABLES.put(Tier.COPPER, SlotTable.of(
			Maps.of("berries", 24, "apple", 20, "golden_carrot", 16, "emerald", 12, "diamond", 8, "seven", 5, "creeper", 15),
			Maps.of("berries", 10.0, "apple", 10.0, "golden_carrot", 20.0, "emerald", 30.0, "diamond", 60.0, "seven", 150.0, "creeper", 0.0),
			BERRY_PARTIAL, Tier.COPPER.lines(), false));
		TABLES.put(Tier.GOLD, SlotTable.of(
			Maps.of("berries", 22, "apple", 19, "golden_carrot", 16, "emerald", 12, "diamond", 8, "seven", 5, "wild", 3, "creeper", 8, "pearl", 5,
				"star", 2),
			Maps.of("berries", 8.0, "apple", 7.0, "golden_carrot", 11.0, "emerald", 25.0, "diamond", 50.0, "seven", 100.0, "wild", 200.0, "creeper",
				0.0, "pearl", 10.0, "star", 0.0),
			BERRY_PARTIAL, Tier.GOLD.lines(), false));
		TABLES.put(Tier.NETHERITE, SlotTable.of(
			Maps.of("berries", 20, "apple", 19, "golden_carrot", 16, "emerald", 12,
				"diamond", 9, "seven", 6, "wild", 3, "tnt", 6, "pearl", 5,
				"clock", 2, "star", 2),
			Maps.of("berries", 8.0, "apple", 9.0, "golden_carrot", 14.0, "emerald", 25.0,
				"diamond", 50.0, "seven", 100.0, "wild", 250.0, "tnt", 0.0, "pearl", 10.0,
				"clock", 50.0, "star", 0.0),
			BERRY_PARTIAL, Tier.NETHERITE.lines(), false));
	}

	private ShowdownTables() {}

	static SlotTable table(Tier tier) {
		return TABLES.get(tier);
	}
}
