package dev.nezo.burmaldaholic.core.table.parts;

/**
 * Vertical render extent of table / machine renderers above their block (the client's frustum box,
 * {@code client/table/TableCulling}). Pure, so a unit test checks the box against every renderer element.
 *
 * <p>Slot cabinets (compact and full): the highest elements of {@code SlotCabinetRenderer} are the floating tier word
 * (baseline 2.35 + tier rise ≤ 0.3 + 0.95 while the bonus wheel is up, glyphs 9 px × 0.024) and the bonus wheel
 * (marquee top 1 + 13/16 + 4/16, + 0.06, centre + radius 0.42 each side). The values below mirror those constants.
 */
public final class RenderExtent {
	/** Top of the slot render box above the slot block's y (both forms). */
	public static final double SLOT_TOP = 4.0;
	/** Top of the default table / machine box above the highest footprint cell (chips, cards, plates, holo board). */
	public static final double CONTENT_ABOVE = 1.0;

	/** SlotCabinetRenderer: tier word baseline 2.35, rise ≤ 0.3, +0.95 over the wheel, glyph height 9 × 0.024. */
	public static double slotTierTextTop() {
		return 2.35 + 0.3 + 0.95 + 9 * 0.024;
	}

	/** SlotCabinetRenderer: bonus wheel centre (marquee top + 0.06 + radius, rise ≤ 1) plus its radius. */
	public static double slotBonusWheelTop() {
		double marqueeTop = 1.0 + 13 / 16.0 + 4 / 16.0;
		double radius = 0.42;
		return marqueeTop + 0.06 + radius + radius;
	}

	private RenderExtent() {}
}
