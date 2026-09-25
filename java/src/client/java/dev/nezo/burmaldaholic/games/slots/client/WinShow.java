package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import dev.nezo.burmaldaholic.games.slots.v2.present.WinShowPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Win show (slots.md §4.4; JS3): non-winning cells dim to 40 %, winning cells get a glinting gold frame and their
 * win loop, the all-wins overview draws every way path at 50 %, then the symbol cycle shows one winning symbol at a
 * time (highest amount first) with its paths at 100 %, flow dashes travelling left → right and the label
 * "Diamond ×5 · 12 ways · 48". The cycle loops locally until the next spin, a feature, or 12 s.
 */
public final class WinShow {
	private static final String[] ENDS_SHOW = {SlotTimeline.SPIN_UP, SlotTimeline.TUMBLE_EXPLODE, SlotTimeline.FS_INTRO, SlotTimeline.BONUS_INTRO,
		SlotTimeline.FS_OUTRO};

	private Beat show;
	private final List<Beat> cycle = new ArrayList<>();
	private final int[] segs = new int[80];
	private int lastCycleSound = -1;

	public void reset() {
		show = null;
		cycle.clear();
		lastCycleSound = -1;
	}

	public void cue(SlotStage s, Beat b) {
		if (b.kind().equals(SlotTimeline.WIN_SHOW)) {
			show = b;
			cycle.clear();
			for (Beat c : s.beats(SlotTimeline.WAY_CYCLE)) {
				if (c.arg(SlotBeats.SPIN) == b.arg(SlotBeats.SPIN) && c.arg(SlotBeats.WAY_STEP) == b.arg(SlotBeats.WIN_STEP)) cycle.add(c);
			}
			lastCycleSound = -1;
		} else if (b.kind().equals(SlotTimeline.WAY_CYCLE)) {
			int i = b.lane();
			if (i != lastCycleSound) {
				lastCycleSound = i;
				SlotSounds.winSmall(WinShowPlan.cyclePitch(i), 0.4f);
				s.host().narrate(label(s, b));
			}
		}
	}

	/** The running show, or null when a later beat ended it (next spin, tumble, feature). */
	public Beat current(SlotStage s) {
		if (show == null || s.script() == null) return null;
		double t = s.t();
		if (t < show.at()) return null;
		for (String k : ENDS_SHOW) {
			for (Beat b : s.beats(k)) {
				if (b.at() > show.at() && b.at() <= t) return null;
			}
		}
		return show;
	}

	private double since(SlotStage s) {
		return s.t() - show.at();
	}

	public boolean winning(SlotStage s, int cell) {
		Beat cur = current(s);
		return cur != null && (cur.arg(SlotBeats.WIN_MASK) & (1 << cell)) != 0 && since(s) < WinShowPlan.LOOP_LIMIT_MS;
	}

	/** Pulse of a winning cell (the current cycle symbol's cells, or every winning cell in the overview). */
	public double pulse(SlotStage s, int cell) {
		Beat cur = current(s);
		if (cur == null || !winning(s, cell)) return 1;
		Beat entry = entry(s);
		if (entry != null && (entry.arg(SlotBeats.WAY_MASK) & (1 << cell)) == 0) return 1;
		return WinShowPlan.pulse(since(s), s.reduceMotion());
	}

	/** Current cycle entry (a WAY_CYCLE beat) or null during the overview. */
	public Beat entry(SlotStage s) {
		Beat cur = current(s);
		if (cur == null || cycle.isEmpty()) return null;
		double t = s.t();
		double start = cycle.get(0).at();
		if (t < start) return null;
		int i = WinShowPlan.cycleIndex(t - start, cycle.size(), since(s));
		return i < 0 ? null : cycle.get(i);
	}

	/** Label under the reels for the current cycle entry, or null. */
	public Component label(SlotStage s) {
		Beat e = entry(s);
		return e == null ? null : label(s, e);
	}

	private static Component label(SlotStage s, Beat e) {
		int sym = e.arg(SlotBeats.WAY_SYMBOL);
		int ways = e.arg(SlotBeats.WAY_WAYS);
		if (ways == 0) {
			return Component.translatable("gui.burmaldaholic.slots.scatter_win", Texts.number(e.arg(SlotBeats.WAY_K)), Texts.chips(e.arg(SlotBeats.WAY_PAY)));
		}
		return Component.translatable("gui.burmaldaholic.slots.symbol_win", Component.translatable(SymbolStyle.nameKey(s.machine(), sym)),
			Texts.number(e.arg(SlotBeats.WAY_K)), Texts.number(ways), Texts.chips(e.arg(SlotBeats.WAY_PAY)));
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g) {
		Beat cur = current(s);
		if (cur == null) return;
		double ms = since(s);
		int mask = cur.arg(SlotBeats.WIN_MASK);
		Beat entry = entry(s);
		int focus = entry == null ? mask : entry.arg(SlotBeats.WAY_MASK);
		int cell = s.cell();
		// dim everything that does not win (during the cycle: everything outside the current symbol)
		float dim = WinShowPlan.dim(ms);
		if (dim < 1) {
			for (int c = 0; c < 15; c++) {
				if ((focus & (1 << c)) != 0) continue;
				int x = s.cellX(c / 3);
				int y = s.cellY(c % 3);
				g.fill(x, y, x + cell, y + cell, SlotDraw.withAlpha(0xFF000000, 1 - dim));
			}
		}
		// paths: all at 50 % in the overview, the current symbol at 100 %
		if (!cycle.isEmpty()) {
			if (entry == null) {
				for (Beat e : cycle) drawPaths(s, g, e, WinShowPlan.OVERVIEW_ALPHA, ms, cycle.indexOf(e));
			} else {
				drawPaths(s, g, entry, WinShowPlan.CYCLE_ALPHA, ms, entry.lane());
			}
		}
		// gold frames with a travelling glint on every winning cell
		int glint = WinShowPlan.frameGlint(ms);
		for (int c = 0; c < 15; c++) {
			if ((mask & (1 << c)) == 0) continue;
			int x = s.cellX(c / 3);
			int y = s.cellY(c % 3);
			boolean lit = (focus & (1 << c)) != 0;
			if (CabinetArt.ART) {
				SlotSprites.blit(g, SlotSprites.WIN_FRAME, x, y, cell, cell,
					lit ? 0xFFFFFFFF : 0xA0FFFFFF);
				continue;
			}
			int gold = lit ? 0xFFFFD640 : 0xB0B07010;
			SlotDraw.frame(g, x + 1, y + 1, cell - 2, cell - 2, 2, gold);
			if (lit && !s.reduceMotion()) {
				int side = glint;
				int gx = side == 0 ? x + 2 + (int) ((ms / 3) % (cell - 6)) : side == 2 ? x + cell - 4 - (int) ((ms / 3) % (cell - 6)) : x + 1;
				int gy = side == 1 ? y + 2 + (int) ((ms / 3) % (cell - 6)) : side == 3 ? y + cell - 4 - (int) ((ms / 3) % (cell - 6)) : y + 1;
				g.fill(gx, gy, gx + 3, gy + 3, 0xFFFFFFFF);
			}
		}
	}

	private void drawPaths(SlotStage s, GuiGraphicsExtractor g, Beat e, float alpha, double ms, int slot) {
		int mask = e.arg(SlotBeats.WAY_MASK);
		if (e.arg(SlotBeats.WAY_WAYS) == 0) return; // scatters pay anywhere: no path
		int k = e.arg(SlotBeats.WAY_K);
		int color = SymbolStyle.pathColor(s.machine(), e.arg(SlotBeats.WAY_SYMBOL));
		int flow = SymbolStyle.flowColor(color);
		int n = WinShowPlan.segments(mask, k, segs);
		int half = s.cell() / 2;
		int shape = WinShowPlan.dashShape(slot);
		for (int i = 0; i < n; i++) {
			int a = segs[2 * i];
			int b = segs[2 * i + 1];
			double x0 = s.cellX(a / 3) + half;
			double y0 = s.cellY(a % 3) + half;
			double x1 = s.cellX(b / 3) + half;
			double y1 = s.cellY(b % 3) + half;
			SlotDraw.line(g, x0, y0, x1, y1, 3, SlotDraw.withAlpha(0xFF180A28, alpha * 0.55));
			SlotDraw.line(g, x0, y0, x1, y1, 2, SlotDraw.withAlpha(color, alpha * 0.9));
			if (!s.reduceMotion()) {
				double len = Math.hypot(x1 - x0, y1 - y0);
				double off = WinShowPlan.flowOffset(ms, len);
				double from = off / len;
				double to = (off + (shape == 1 ? 2 : WinShowPlan.FLOW_LEN)) / len;
				SlotDraw.lineSegment(g, x0, y0, x1, y1, from, to, shape == 2 ? 3 : 2, SlotDraw.withAlpha(flow, alpha));
			}
		}
		// path nodes on every winning cell (2-frame blink)
		int node = (int) ((s.now() / 250) % 2);
		for (int c = 0; c < 15; c++) {
			if ((mask & (1 << c)) == 0 || c / 3 >= k) continue;
			int cx = s.cellX(c / 3) + half;
			int cy = s.cellY(c % 3) + half;
			int r = node == 0 ? 3 : 4;
			g.fill(cx - r, cy - r, cx + r, cy + r, SlotDraw.withAlpha(0xFF180A28, alpha * 0.8));
			g.fill(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, SlotDraw.withAlpha(color, alpha));
			if (CabinetArt.ART) {
				SlotSprites.blit(g, SlotSprites.PATH_NODE, cx - 4, cy - 4, 8, 8,
					SlotDraw.withAlpha(0xFFFFFFFF, alpha));
			}
		}
	}
}
