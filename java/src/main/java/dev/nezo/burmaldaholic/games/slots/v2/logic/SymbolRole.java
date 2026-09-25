package dev.nezo.burmaldaholic.games.slots.v2.logic;

/** What a symbol does in evaluation (SLOTS.md §1.1, §2). */
public enum SymbolRole {
	/** Pays by ways. */
	PAY,
	/** Substitutes for PAY symbols; only on reels 2–4. */
	WILD,
	/** Counts anywhere; pays (Overworld, End) and triggers free spins. */
	SCATTER,
	/** Chest / crystal: counts only on its machine's bonus reels. */
	BONUS,
	/** Nether Piglin coin: counts anywhere, carries a drawn value. */
	COIN
}
