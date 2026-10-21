package dev.nezo.burmaldaholic.games.slots.v2.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.FlowCheck;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.ViewportPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Owner B6 (SLT on the v2 kit): the slot cabinet lays out inside the shared {@code TableGrid} PlayArea at 427 × 240,
 * 480 × 270, 640 × 360 and 960 × 540 (and a 320 × 240 small window), nothing overlaps, the reels keep ≥ 40 px cells at
 * the four layout sizes; the Dock flow says why every disabled control is disabled.
 */
class SlotV2KitTest {
	/** {layout w, h, window w, h, GUI scale} as in {@code PlayFieldUnityTest.CELLS}, plus a small 4:3 window. */
	static final int[][] CELLS = {
		{427, 240, 1280, 720, 3},
		{480, 270, 960, 540, 2},
		{640, 360, 1280, 720, 2},
		{960, 540, 1920, 1080, 2},
		{640, 480, 640, 480, 2}, // K2 T-K2 D5: below 400 wide the scale steps down (was 320 x 240)
	};

	private static Box rect(SlotGeometry.Rect r) {
		return new Box(r.x(), r.y(), r.w(), r.h());
	}

	@Test
	void cabinetFitsThePlayAreaAtEverySize() {
		for (int[] c : CELLS) {
			TableGrid g = new TableGrid(ViewportPolicy.of(c[2], c[3], c[4]));
			String at = c[0] + "x" + c[1];
			assertEquals(c[0], g.screen.w(), at);
			Box play = g.play;
			SlotGeometry l = SlotGeometry.docked(play.x(), play.y(), play.w(), play.h());
			assertTrue(l.docked, at);
			if (c[0] >= 427) {
				assertFalse(l.compact, at + ": the four layout sizes keep the full cabinet (4 meters)");
				assertTrue(l.cell >= 40, at + ": cell " + l.cell);
			}
			Map<String, SlotGeometry.Rect> regions = l.regions();
			assertFalse(regions.containsKey("spin"), "the controls are in the Dock");
			List<String> names = new ArrayList<>(regions.keySet());
			for (int i = 0; i < names.size(); i++) {
				Box r = rect(regions.get(names.get(i)));
				assertTrue(r.inside(play), at + ": " + names.get(i) + " " + r + " outside the PlayArea " + play);
				for (int j = i + 1; j < names.size(); j++) {
					assertFalse(regions.get(names.get(i)).overlaps(regions.get(names.get(j))), at + ": " + names.get(i) + " overlaps " + names.get(j));
				}
			}
			assertTrue(rect(l.cabinet()).y() >= play.y() && rect(l.cabinet()).y() + rect(l.cabinet()).h() <= play.y() + play.h() + SlotGeometry.CABINET_PAD,
				at + ": cabinet art " + l.cabinet() + " vs " + play);
			assertFalse(rect(l.label()).intersects(g.dock), at + ": result line under the Dock");
		}
	}

	private static SlotView view(boolean busy, boolean auto, boolean locked, boolean playable, long balance, int idx, boolean buy, Gate sd) {
		return new SlotView(playable, locked, new String[] {"Gold", "Bronze"}, busy, auto, 20, idx, 5, balance, 5, buy, 416, true, true, false, sd);
	}

	@Test
	void flowAlwaysSaysWhy() {
		Gate sd = Gate.on(SlotFlow.SHOWDOWN, "gui.burmaldaholic.pvp.slots.title");
		for (boolean busy : new boolean[] {false, true}) {
			for (boolean auto : new boolean[] {false, true}) {
				for (boolean locked : new boolean[] {false, true}) {
					for (long bal : new long[] {0, 10_000}) {
						for (int idx : new int[] {0, 2, 4}) {
							SlotView v = view(busy, auto, locked, !locked, bal, idx, true, sd);
							assertEquals(List.of(), FlowCheck.problems(SlotFlow.INSTANCE, v), v.toString());
							assertTrue(SlotFlow.INSTANCE.actions(v).size() <= 5);
							for (Gate s : List.of(SlotFlow.betDown(v), SlotFlow.betUp(v))) assertTrue(s.enabled() || s.reasonKey() != null);
						}
					}
				}
			}
		}
		SlotView idle = view(false, false, false, true, 10_000, 2, false, null);
		assertEquals(SlotFlow.SPIN, SlotFlow.INSTANCE.primary(idle).action());
		assertTrue(SlotFlow.INSTANCE.primary(idle).enabled());
		assertEquals(SlotFlow.SKIP, SlotFlow.INSTANCE.primary(view(true, false, false, true, 10_000, 2, false, null)).action(), "Stop while spinning");
		Gate autoSpin = SlotFlow.INSTANCE.primary(view(true, true, false, true, 10_000, 2, false, null));
		assertEquals(SlotFlow.STOP_AUTO, autoSpin.action(), "T-SLT: during an autoplay spin Space / the primary stops the autoplay");
		assertEquals("gui.burmaldaholic.slots.stop_autoplay", autoSpin.labelKey(), "the primary says Stop Auto");
		assertEquals("gui.burmaldaholic.slots.vip_lock", SlotFlow.INSTANCE.primary(view(false, false, true, false, 10_000, 2, false, null)).reasonKey());
		assertEquals("gui.burmaldaholic.slots.bet_coin", SlotFlow.INSTANCE.hintKey(idle).orElseThrow(), "coin × multiplier hint");
		assertEquals(4L, SlotFlow.INSTANCE.hintArgs(idle)[1]);
	}
}
