package dev.nezo.burmaldaholic.games.slots.v2.logic;

/** The three v2 machines (SLOTS.md §0). Block ids are unchanged. */
public enum Machine {
	OVERWORLD("overworld", "slot_machine_copper", 40, 500),
	NETHER("nether", "slot_machine_gold", 32, 2000),
	END("end", "slot_machine_netherite", 45, 5000);

	/** Config / tape id ({@code slots.<m>.*}). */
	public final String id;
	/** Block id path (unchanged from v1). */
	public final String block;
	/** Default strip length (Appendix A); configured strips may differ. */
	public final int defaultStripLength;
	/** Default max-win multiple ({@code slots.<m>.maxWinMultiple}). */
	public final int defaultCap;

	Machine(String id, String block, int defaultStripLength, int defaultCap) {
		this.id = id;
		this.block = block;
		this.defaultStripLength = defaultStripLength;
		this.defaultCap = defaultCap;
	}

	public static Machine byId(String id) {
		for (Machine m : values()) if (m.id.equals(id)) return m;
		throw new IllegalArgumentException("unknown machine " + id);
	}
}
