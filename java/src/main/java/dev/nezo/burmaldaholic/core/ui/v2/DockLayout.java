package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dock of every v2 table screen (docs/architecture/ui-v2.md §3.3): left to right the BetBar (chip tray, tools,
 * bet readout) or nothing, then the status (two lines, ellipsised), then the ActionRow and the secondary button, and
 * the PrimaryButton, always at the right edge. Pure: the widths of translated labels are measured by the caller (font
 * width + paddings) and passed in, so the same math runs in unit tests with EN and RU widths.
 *
 * <p>When the Dock is too narrow it degrades in a fixed order and NEVER overlaps or hides the primary: (1) the status
 * shrinks to its minimum, (2) the tools drop from the end (×2, Rebet; Undo and Clear stay), (3) the secondary button
 * goes (Leave stays reachable as the TopBar icon), (4) the tray shows fewer chips (≥ 3; the selected one stays
 * visible, keys 1–5 still work), (5) the bet readout drops its 2× value to 1× ({@code readoutCompactW}; 320-wide
 * windows only), (6) the primary loses its key-hint plate; action buttons lose theirs ({@code compactW}) first. Whatever is still too wide is reported
 * in {@link Dock#overflow} (a test failure, never drawn over a neighbour).
 */
public final class DockLayout {
	private DockLayout() {}

	public enum Mode {
		/** Chip tray + tools + readout + status + primary. */
		BETTING,
		/** Status + action buttons + primary. */
		ACTIONS
	}

	/**
	 * @param chips        chip denominations offered (≤ 5)
	 * @param tools        bet tools offered, in the fixed order Undo, Clear, Rebet, ×2 (0..4)
	 * @param readoutW     width of the bet readout ("Bet" caption / 2× value), measured
	 * @param readoutCompactW the same with a 1× value (last resort)
	 * @param actionW      per action button: full width (label + key hint + padding)
	 * @param actionCompactW per action button: width without the key hint
	 * @param secondaryW   secondary normal button left of the primary (e.g. Leave on the result), 0 = none
	 * @param primaryW     measured primary content (label + key hint + padding); the tier minimum applies
	 * @param primaryCompactW the primary without its key-hint plate (last resort, 320-wide windows)
	 * @param stepperW     ACTIONS: the {@code − value +} stepper left of the action buttons (0 = none)
	 * @param statusW      the width the status wants: {@code min(cap, max(w(line1), w(line2)))} (0 = only the minimum); when
	 *                     the status cannot get it, actions go compact first, then line 2 moves to {@link Dock#hintStrip}
	 */
	public record Spec(Mode mode, int chips, int tools, int readoutW, int readoutCompactW, int[] actionW, int[] actionCompactW, int secondaryW,
			int primaryW, int primaryCompactW, int stepperW, int statusW) {
		public static Spec betting(int chips, int tools, int readoutW, int readoutCompactW, int secondaryW, int primaryW, int primaryCompactW) {
			return new Spec(Mode.BETTING, chips, tools, readoutW, readoutCompactW, new int[0], new int[0], secondaryW, primaryW, primaryCompactW, 0, 0);
		}

		public static Spec actions(int[] actionW, int[] actionCompactW, int primaryW, int primaryCompactW) {
			return actions(actionW, actionCompactW, 0, primaryW, primaryCompactW);
		}

		/** Actions with a stepper slot ({@code stepperW} 0 = none) between the status and the action buttons. */
		public static Spec actions(int[] actionW, int[] actionCompactW, int stepperW, int primaryW, int primaryCompactW) {
			return new Spec(Mode.ACTIONS, 0, 0, 0, 0, actionW, actionCompactW, 0, primaryW, primaryCompactW, stepperW, 0);
		}

		/** The same spec with the status's wanted width ({@link #statusW}). */
		public Spec withStatus(int statusW) {
			return new Spec(mode, chips, tools, readoutW, readoutCompactW, actionW, actionCompactW, secondaryW, primaryW, primaryCompactW, stepperW,
				Math.max(0, statusW));
		}

		Spec compactPrimary() {
			return new Spec(mode, chips, tools, readoutW, readoutCompactW, actionW, actionCompactW, secondaryW, primaryCompactW, primaryCompactW, stepperW,
				statusW);
		}
	}

	/**
	 * @param chips      visible chip boxes (index = position in the visible window)
	 * @param firstChip  index of the first visible denomination
	 * @param tools      visible tool boxes (Undo, Clear, Rebet, ×2 order)
	 * @param overflow   px that did not fit (0 = OK)
	 * @param stepper    the stepper slot (ACTIONS with {@link Spec#stepperW}; empty otherwise)
	 * @param hintStrip  an 11-px strip just above the Dock, inside the PlayArea, that carries status line 2 when the Dock
	 *                   cannot give the status {@link Spec#statusW} (empty = line 2 stays in the Dock); hosts treat it as a
	 *                   FreeSlot obstacle
	 */
	public record Dock(List<Box> chips, int firstChip, List<Box> tools, Box readout, boolean compactReadout, Box status, List<Box> actions,
			boolean compactActions, Box secondary, Box primary, int overflow, boolean compactPrimary, Box stepper, Box hintStrip) {
		Dock withCompactPrimary() {
			return new Dock(chips, firstChip, tools, readout, compactReadout, status, actions, compactActions, secondary, primary, overflow, true, stepper,
				hintStrip);
		}
	}

	/** Height of {@link Dock#hintStrip}. */
	public static final int HINT_STRIP_H = 11;

	public static int minStatus(Tier t) {
		return t == Tier.T1 ? 44 : 64;
	}

	public static Dock layout(TableGrid g, Spec s) {
		return layout(g, s, 0);
	}

	/** @param selectedChip index of the selected denomination (kept visible when the tray is shortened) */
	public static Dock layout(TableGrid g, Spec s, int selectedChip) {
		Dock d = layoutOnce(g, s, selectedChip);
		if (d.overflow() > 0 && s.primaryCompactW() > 0 && s.primaryCompactW() < s.primaryW()) {
			return layoutOnce(g, s.compactPrimary(), selectedChip).withCompactPrimary();
		}
		return d;
	}

	private static Dock layoutOnce(TableGrid g, Spec s, int selectedChip) {
		Tier t = g.tier;
		Box d = g.dock;
		int m = t.margin;
		int bh = t.buttonH;
		int by = d.y() + (d.h() - bh) / 2;
		int primaryW = Math.max(t.primaryW, s.primaryW());
		Box primary = new Box(d.right() - m - primaryW, by, primaryW, bh);
		int right = primary.x() - 6;
		Box secondary = Box.EMPTY;
		if (s.secondaryW() > 0) {
			int sw = Math.max(t.actionW, s.secondaryW());
			secondary = new Box(right - sw, by, sw, bh);
			right = secondary.x() - 4;
		}
		int minStatus = minStatus(t);
		int wantStatus = Math.max(minStatus, s.statusW());
		if (s.mode() == Mode.ACTIONS) {
			int n = s.actionW().length;
			int stepperW = Math.max(0, s.stepperW());
			int stepperGap = stepperW > 0 && n > 0 ? 6 : 0;
			boolean compact = false;
			int total = sum(s.actionW(), t.actionW, 4) + stepperW + stepperGap;
			if (m + wantStatus + 6 + total > right && !(g.roomy() && m + minStatus + 6 + total <= right)) { // ROOMY keeps the key plates
				compact = true;
				total = sum(s.actionCompactW(), 0, 4) + stepperW + stepperGap;
			}
			int x = right - total;
			int overflow = Math.max(0, m + minStatus + 6 - x);
			Box stepper = Box.EMPTY;
			if (stepperW > 0) {
				stepper = new Box(x, by, stepperW, bh);
				x += stepperW + stepperGap;
			}
			List<Box> actions = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				int w = compact ? s.actionCompactW()[i] : Math.max(t.actionW, s.actionW()[i]);
				actions.add(new Box(x, by, w, bh));
				x += w + 4;
			}
			int statusRight = !stepper.isEmpty() ? stepper.x() - 6 : actions.isEmpty() ? right : actions.get(0).x() - 6;
			Box status = new Box(m, d.y() + 4, Math.max(0, statusRight - m), d.h() - 8);
			return new Dock(List.of(), 0, List.of(), Box.EMPTY, false, status, actions, compact, secondary, primary, overflow, false, stepper,
				hintStrip(g, status, s.statusW()));
		}
		// BETTING: chips · tools · readout · status
		int chip = t.trayChip;
		int chipPitch = chip + (t == Tier.T3 ? 4 : 2);
		int tools = s.tools();
		int chips = s.chips();
		int readoutW = s.readoutW();
		int overflow;
		while (true) {
			int need = m + 2 + chips * chipPitch - (chipPitch - chip) + (tools > 0 ? 6 + tools * t.toolPitch - (t.toolPitch - t.tool) : 0) + 8 + readoutW
				+ 8 + minStatus;
			overflow = Math.max(0, need - right);
			if (overflow == 0) break;
			if (tools > 2) tools--;
			else if (!secondary.isEmpty()) {
				secondary = Box.EMPTY; // Leave stays reachable as the TopBar icon
				right = primary.x() - 6;
			} else if (chips > 3) chips--;
			else if (readoutW > s.readoutCompactW() && s.readoutCompactW() > 0) readoutW = s.readoutCompactW();
			else break;
		}
		int first = Math.max(0, Math.min(selectedChip - chips / 2, s.chips() - chips));
		List<Box> chipBoxes = new ArrayList<>();
		int x = m + 2;
		int cy = d.y() + (d.h() - chip) / 2;
		for (int i = 0; i < chips; i++) {
			chipBoxes.add(new Box(x, cy, chip, chip));
			x += chipPitch;
		}
		x += 6 - (chipPitch - chip);
		List<Box> toolBoxes = new ArrayList<>();
		int ty = d.y() + (d.h() - t.tool) / 2;
		for (int i = 0; i < tools; i++) {
			toolBoxes.add(new Box(x, ty, t.tool, t.tool));
			x += t.toolPitch;
		}
		if (tools > 0) x += 8 - (t.toolPitch - t.tool);
		Box readout = new Box(x, d.y() + 4, readoutW, d.h() - 8);
		x = readout.right() + 8;
		Box status = new Box(x, d.y() + 4, Math.max(0, right - x), d.h() - 8);
		return new Dock(chipBoxes, first, toolBoxes, readout, readoutW < s.readoutW(), status, List.of(), false, secondary, primary, overflow, false,
			Box.EMPTY, hintStrip(g, status, s.statusW()));
	}

	/** The hint strip when the status box is narrower than the status wants (else empty). */
	private static Box hintStrip(TableGrid g, Box status, int statusW) {
		if (statusW <= 0 || status.w() >= statusW) return Box.EMPTY;
		int m = g.tier.margin;
		// the whole Dock width: it may carry line 2, or a lone line 1 too long for two lines of the column (the status
		// fills only behind its text)
		return new Box(m, g.dock.y() - HINT_STRIP_H, g.dock.w() - 2 * m, HINT_STRIP_H);
	}

	private static int sum(int[] ws, int min, int gap) {
		int total = 0;
		for (int w : ws) total += Math.max(min, w) + gap;
		return ws.length == 0 ? 0 : total - gap;
	}
}
