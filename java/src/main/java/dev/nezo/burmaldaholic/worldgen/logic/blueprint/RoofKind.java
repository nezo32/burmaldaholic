package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

/**
 * Roof silhouettes a style can ask for (architecture doc §7.2). {@link Roofs#roof} builds one over a
 * footprint; the per-shape verbs ({@link Roofs#gable}, {@link Roofs#dome}, ...) stay public for pieces
 * that compose their own roofline.
 */
public enum RoofKind {
	/** Tudor: stair hip roof with a ridge cap and dark eaves. */
	HIP,
	/** Oasis: flat roof behind a stepped art-deco parapet with a glass dome in the middle. */
	FLAT_DOME,
	/** Safari: stepped hay-bale thatch hip with a wooden ridge. */
	THATCH,
	/** Alpine: steep gable (ridge front-to-back), copper ridge, stone chimney with a campfire. */
	STEEP_GABLE,
	/** Ice palace: low mansard drum, blue-ice dome and quartz spires on the corners. */
	ICE_DOME,
	/** Spare shape: steep mansard with a flat cap. */
	MANSARD
}
