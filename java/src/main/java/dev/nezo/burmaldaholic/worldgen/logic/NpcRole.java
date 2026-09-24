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
	BACCARAT_DEALER("burmaldaholic:baccarat_dealer");

	private final List<String> entityIds;

	NpcRole(String... entityIds) {
		this.entityIds = List.of(entityIds);
	}

	/** Candidate entity type ids, most specific first. */
	public List<String> entityIds() {
		return entityIds;
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
