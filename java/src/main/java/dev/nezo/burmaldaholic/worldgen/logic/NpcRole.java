package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.List;
import java.util.Locale;

/**
 * NPCs placed in generated casinos. The entity types belong to other modules (ids from STRINGS.md);
 * worldgen spawns the first candidate id that is registered at placement time and skips the NPC
 * when none is.
 */
public enum NpcRole {
	CROUPIER("burmaldaholic:croupier"),
	LOAN_SHARK("burmaldaholic:loan_shark"),
	/** Loan Shark variant; falls back to the plain Loan Shark when the variant is not registered. */
	PIGLIN_MONEYLENDER("burmaldaholic:piglin_moneylender", "burmaldaholic:loan_shark"),
	PIGLIN_DEALER("burmaldaholic:piglin_dealer"),
	SHULKER_CROUPIER("burmaldaholic:shulker_croupier"),
	/** Owned by the baccarat module; cosmetic, opens the nearest baccarat table (§20.6). */
	BACCARAT_DEALER("burmaldaholic:baccarat_dealer"),
	/** DL: the house dealer standing on a table's dealer cell; its type follows the dimension ({@link #entityIds(String)}). */
	HOUSE_DEALER("burmaldaholic:house_dealer"),
	/** DL: the End's croupier (enderman in a tux; sells like the Shulker Croupier did). */
	ENDER_CROUPIER("burmaldaholic:ender_croupier"),
	/** DL: the Parlor's pit boss (piglin brute in a black suit; greeting + the croupier's shop). */
	PIT_BOSS("burmaldaholic:pit_boss"),
	/** DL/EC: the merchant beside every cashier (opens the store EC registers); type follows the dimension. */
	MERCHANT("burmaldaholic:merchant");

	private final List<String> entityIds;

	NpcRole(String... entityIds) {
		this.entityIds = List.of(entityIds);
	}

	/** Candidate entity type ids, most specific first. */
	public List<String> entityIds() {
		return entityIds;
	}

	/** Candidate entity type ids in {@code dimension} (house dealers and merchants have a species per dimension). */
	public List<String> entityIds(String dimension) {
		if (this != HOUSE_DEALER && this != MERCHANT) {
			return entityIds;
		}
		String base = this == HOUSE_DEALER ? "house_dealer" : "merchant";
		return switch (NpcLook.Species.of(dimension)) {
			case PIGLIN -> List.of("burmaldaholic:piglin_" + base);
			case ENDER -> List.of("burmaldaholic:ender_" + base);
			case HUMAN -> entityIds;
		};
	}

	/**
	 * How long this NPC stays missing before it respawns: the configured {@code worldgen.loanSharkRespawnTicks}, except
	 * house dealers and merchants, which come back after {@link RespawnRule#MIN_MISSING_TICKS} (a table without its
	 * dealer, a cashier without its store, is broken).
	 */
	public long respawnTicks(long configured) {
		return this == HOUSE_DEALER || this == MERCHANT ? RespawnRule.MIN_MISSING_TICKS : configured;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static NpcRole byId(String id) {
		for (NpcRole r : values()) {
			if (r.id().equals(id)) {
				return r;
			}
		}
		throw new IllegalArgumentException("unknown npc role '" + id + "'");
	}
}
