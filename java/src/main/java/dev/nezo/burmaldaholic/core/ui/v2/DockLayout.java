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
	 */
	public record Spec(Mode mode, int chips, int tools, int readoutW, int readoutCompactW, int[] actionW, int[] actionCompactW, int secondaryW,
			int primaryW, int primaryCompactW) {
		public static Spec betting(int chips, int tools, int readoutW, int readoutCompactW, int secondaryW, int primaryW, int primaryCompactW) {
			return new Spec(Mode.BETTING, chips, tools, readoutW, readoutCompactW, new int[0], new int[0], secondaryW, primaryW, primaryCompactW);
		}

		public static Spec actions(int[] actionW, int[] actionCompactW, int primaryW, int primaryCompactW) {
			return new Spec(Mode.ACTIONS, 0, 0, 0, 0, actionW, actionCompactW, 0, primaryW, primaryCompactW);
		}

		Spec compactPrimary() {
			return new Spec(mode, chips, tools, readoutW, readoutCompactW, actionW, actionCompactW, secondaryW, primaryCompactW, primaryCompactW);
		}
	}

	/**
	 * @param chips      visible chip boxes (index = position in the visible window)
	 * @param firstChip  index of the first visible denomination
	 * @param tools      visible tool boxes (Undo, Clear, Rebet, ×2 order)
	 * @param overflow   px that did not fit (0 = OK)
	 */
	public record Dock(List<Box> chips, int firstChip, List<Box> tools, Box readout, boolean compactReadout, Box status, List<Box> actions,
			boolean compactActions, Box secondary, Box primary, int overflow, boolean compactPrimary) {}

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
			Dock c = layoutOnce(g, s.compactPrimary(), selectedChip);
			return new Dock(c.chips(), c.firstChip(), c.tools(), c.readout(), c.compactReadout(), c.status(), c.actions(), c.compactActions(), c.secondary(),
				c.primary(), c.overflow(), true);
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
		if (s.mode() == Mode.ACTIONS) {
			int n = s.actionW().length;
			boolean compact = false;
			int total = sum(s.actionW(), t.actionW, 4);
			if (m + minStatus + 6 + total > right) {
				compact = true;
				total = sum(s.actionCompactW(), 0, 4);
			}
			int x = right - total;
			int overflow = Math.max(0, m + minStatus + 6 - x);
			List<Box> actions = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				int w = compact ? s.actionCompactW()[i] : Math.max(t.actionW, s.actionW()[i]);
				actions.add(new Box(x, by, w, bh));
				x += w + 4;
			}
			int statusRight = actions.isEmpty() ? right : actions.get(0).x() - 6;
			Box status = new Box(m, d.y() + 4, Math.max(0, statusRight - m), d.h() - 8);
			return new Dock(List.of(), 0, List.of(), Box.EMPTY, false, status, actions, compact, secondary, primary, overflow, false);
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
		return new Dock(chipBoxes, first, toolBoxes, readout, readoutW < s.readoutW(), status, List.of(), false, secondary, primary, overflow, false);
	}

	private static int sum(int[] ws, int min, int gap) {
		int total = 0;
		for (int w : ws) total += Math.max(min, w) + gap;
		return ws.length == 0 ? 0 : total - gap;
	}
}
