package dev.nezo.burmaldaholic.worldgen.npc;

/** A casino NPC that wears one skin of a pool (synced LOOK byte, saved as {@code look}; DL-SPEC §3). */
public interface HasLook {
	/** Index into the skin pool, 0 … {@link #lookPool()} − 1. */
	int look();

	void setLook(int look);

	/** Size of this NPC's skin pool ({@code worldgen/logic/NpcLook}). */
	int lookPool();
}
