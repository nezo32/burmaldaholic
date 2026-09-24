package dev.nezo.burmaldaholic.games.slots.v2.present.preview;

import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;

/**
 * PREVIEW FIXTURE (lane J-L9): the three default machines of SLOTS.md §3 / Appendix A ({@link SlotDefaults}) for the
 * preview gallery and the fidelity tests. The real screen reads the machine definition from the server state.
 */
public final class PreviewMachines {
	/** Treasure Hunt contents and weights (SLOTS.md §3.1): value codes 1,2,3,5,10,25, -1..-4, 0 = Creeper. */
	public static final int[] HUNT_VALUES = SlotDefaults.PICK_VALUES;
	public static final int[] HUNT_WEIGHTS = SlotDefaults.PICK_WEIGHTS;
	/** Piglin coin values and weights ×10 (SLOTS.md §3.2). */
	public static final int[] COIN_VALUES = SlotDefaults.HOLD_VALUES;
	public static final int[] COIN_WEIGHTS = SlotDefaults.HOLD_WEIGHTS;

	private PreviewMachines() {}

	public static MachineDef def(Machine m) {
		return SlotDefaults.def(m);
	}

	/** Default Dragon Wheel wedges by ring index 0 outer, 1 middle, 2 core (value codes; 0 = UP). */
	public static int[] wheelRing(int ring) {
		return SlotDefaults.def(Machine.END).features().wheelRings()[Math.max(0, Math.min(2, ring))];
	}
}
