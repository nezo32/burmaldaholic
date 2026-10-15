package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.motion.SnapDraw;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.motion.PixelSnap;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jspecify.annotations.Nullable;

/**
 * Chips on the roulette felt in motion (ROULETTE-ANIM-SPEC §3.6–3.7; tables.md §0.4, §1.4). LOCAL time only: nothing
 * here touches the shared spin or money; it animates what the server state already says.
 *
 * <ul>
 *   <li><b>Place:</b> a chip flies from the armed Dock chip to the spot on a quadratic Bézier (control 18 px above),
 *       180 ms {@code outCubic}, drops its last 2 px in 40 ms and puffs 3 dust px; {@code chip_place} plays on landing.
 *       The confirmed stack then replaces it in place. Rebet / ×2 / server-added chips fly in staggered 40 ms (≤ 400).</li>
 *   <li><b>Undo / Clear:</b> the removed chips fly back to the Dock (160 ms {@code inCubic}).</li>
 *   <li><b>Others and bots:</b> new stacks drop 8 px with {@code outBounce} (150 ms), silently.</li>
 *   <li><b>Result beats:</b> dolly 400–650 ms, rake 650–1070 (+80 fade), payout discs 1100–1500, flight to the balance
 *       1500–1920, banner at 1920, history pill 2600.</li>
 * </ul>
 * Reduced motion: no flights or drops; the dolly appears, the rake is a 120 ms fade in place, payout shows only pills.
 */
final class RouletteChipFx {
	static final double RETURN_END = 400;
	static final double DOLLY_START = 400, DOLLY_MS = 250;
	static final double RAKE_START = 650, RAKE_MS = 420, RAKE_END = RAKE_START + RAKE_MS, RAKE_FADE = 80;
	static final double PAY_START = 1100, PAY_STAGGER = 40, PAY_MS = 260, PILL_AT = 1380;
	static final double FLY_START = 1500, FLY_MS = 420, BANNER_AT = 1920;
	static final double HISTORY_AT = 2600;
	private static final long PUFF = -1;
	private static final double PLACE_MS = 180, DROP_MS = 40, PUFF_MS = 140, BACK_MS = 160, OTHER_DROP_MS = 150, GROW_MS = 60;

	private record Flight(Spot spot, long denom, double x0, double y0, double x1, double y1, long start, boolean back) {}

	private final List<Flight> flights = new ArrayList<>();
	/** spot → time its own placement flight ends (the stack replaces it then) */
	private final Map<Spot, Long> landing = new HashMap<>();
	private final Map<Spot, Long> mine = new LinkedHashMap<>();
	private final Map<String, Long> othersSeen = new HashMap<>();
	private final Map<Spot, Long> dropStart = new HashMap<>();
	private final Map<Spot, Long> growStart = new HashMap<>();
	private boolean first = true;
	private @Nullable Box lastDock;
	// result beat sound crossings
	private long beatKey = Long.MIN_VALUE;
	private double prevR = -1, curR = -1;
	private int paySounds;

	// ---- placement ------------------------------------------------------------------------------------------------------

	/** A chip of {@code denom} flies from the Dock chip box {@code from} (null: from 18 px above) to {@code to}. */
	void place(Spot s, long denom, @Nullable Box from, int[] to, long now, boolean reduce) {
		if (reduce) {
			FxSounds.play("chip_place", 1f, pitch(denom));
			return;
		}
		if (from != null) lastDock = from;
		double x0 = from != null ? from.cx() : to[0];
		double y0 = from != null ? from.cy() : to[1] - 18;
		flights.add(new Flight(s, denom, x0, y0, to[0], to[1], now, false));
		landing.put(s, now + (long) (PLACE_MS + DROP_MS));
	}

	private static float pitch(long d) {
		return (float) (0.9 + 0.1 * Math.log(Math.max(1, d)) / Math.log(5));
	}

	/** True while the own placement flight onto {@code s} is in the air (the stack and the pending ghost wait for it). */
	boolean flying(Spot s, long now) {
		Long end = landing.get(s);
		return end != null && now < end;
	}

	/** Diffs the server state (own slip, others', bots') against the last one and starts the motions it implies. */
	void state(CompoundTag s, boolean betting, long now) {
		Map<Spot, Long> cur = new LinkedHashMap<>();
		ListTag list = s.getListOrEmpty("bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			Spot.parseKey(b.getStringOr("spot", "")).ifPresent(sp -> cur.merge(sp, b.getLongOr("amount", 0), Long::sum));
		}
		Map<String, Long> others = new HashMap<>();
		for (String key : new String[] {"others", "bot_chips"}) {
			CompoundTag t = s.getCompoundOrEmpty(key);
			for (String k : t.keySet()) others.merge(k, t.getLongOr(k, 0), Long::sum);
		}
		if (!first && betting) {
			int stagger = 0;
			for (Map.Entry<Spot, Long> e : cur.entrySet()) {
				long before = mine.getOrDefault(e.getKey(), 0L);
				if (e.getValue() <= before) continue;
				if (flying(e.getKey(), now + 1) || landing.containsKey(e.getKey()) && now - landing.get(e.getKey()) < 400) {
					growStart.put(e.getKey(), Math.max(now, landing.get(e.getKey())));
					continue;
				}
				// rebet / double / a server-side add: fly in from the Dock, staggered (≤ 400 ms in all)
				long denom = ChipStack.discs(e.getValue() - before).isEmpty() ? 1 : ChipStack.discs(e.getValue() - before).get(0);
				long start = now + Math.min(400, 40L * stagger++);
				pendingIn.add(new long[] {start, denom});
				pendingSpot.add(e.getKey());
				landing.put(e.getKey(), start + (long) (PLACE_MS + DROP_MS));
			}
			for (Map.Entry<Spot, Long> e : mine.entrySet()) {
				long after = cur.getOrDefault(e.getKey(), 0L);
				if (after >= e.getValue()) continue;
				long denom = ChipStack.discs(e.getValue() - after).isEmpty() ? 1 : ChipStack.discs(e.getValue() - after).get(0);
				backSpot.add(e.getKey());
				backDenom.add(denom);
			}
			for (Map.Entry<String, Long> e : others.entrySet()) {
				if (e.getValue() > othersSeen.getOrDefault(e.getKey(), 0L)) Spot.parseKey(e.getKey()).ifPresent(sp -> dropStart.put(sp, now));
			}
		}
		first = false;
		mine.clear();
		mine.putAll(cur);
		othersSeen.clear();
		othersSeen.putAll(others);
	}

	// flights queued from state() (they need the layout's anchors, resolved when drawn)
	private final List<long[]> pendingIn = new ArrayList<>();
	private final List<Spot> pendingSpot = new ArrayList<>();
	private final List<Spot> backSpot = new ArrayList<>();
	private final List<Long> backDenom = new ArrayList<>();

	/** Vertical offset of a just-placed other / bot stack (drops from 8 px, {@code outBounce}). */
	double dropOffset(Spot s, long now) {
		Long st = dropStart.get(s);
		if (st == null) return 0;
		double u = (now - st) / OTHER_DROP_MS;
		if (u >= 1) {
			dropStart.remove(s);
			return 0;
		}
		return -8 * (1 - Ease.OUT_BOUNCE.apply(Math.max(0, u)));
	}

	/** Vertical offset of an own stack that just grew: the new disc drops 3 px (60 ms). */
	double growOffset(Spot s, long now) {
		Long st = growStart.get(s);
		if (st == null || now < st) return 0;
		double u = (now - st) / GROW_MS;
		if (u >= 1) {
			growStart.remove(s);
			return 0;
		}
		return -3 * (1 - u * u);
	}

	/** Draws the chips in the air (above the felt, below the wheel) and voices their landings. */
	void drawFlights(GuiGraphicsExtractor g, RouletteLayout l, long now, int k) {
		Box dock = lastDock;
		for (int i = 0; i < pendingSpot.size(); i++) {
			Spot s = pendingSpot.get(i);
			int[] to = l.anchor(s);
			long[] p = pendingIn.get(i);
			flights.add(new Flight(s, p[1], dock != null ? dock.cx() : to[0], dock != null ? dock.cy() : to[1] - 18, to[0], to[1], p[0], false));
		}
		pendingSpot.clear();
		pendingIn.clear();
		for (int i = 0; i < backSpot.size(); i++) {
			int[] from = l.anchor(backSpot.get(i));
			double tx = dock != null ? dock.cx() : from[0];
			double ty = dock != null ? dock.cy() : from[1] + 40;
			flights.add(new Flight(backSpot.get(i), backDenom.get(i), from[0], from[1], tx, ty, now, true));
		}
		backSpot.clear();
		backDenom.clear();
		List<Flight> puffs = null;
		Iterator<Flight> it = flights.iterator();
		while (it.hasNext()) {
			Flight f = it.next();
			double age = now - f.start();
			if (age < 0) continue;
			int chip = l.chip;
			if (f.denom() == PUFF) {
				// 3 dust px kicked up where the chip landed
				if (age >= PUFF_MS) {
					it.remove();
					continue;
				}
				double pu = age / PUFF_MS;
				for (int q = 0; q < 3; q++) {
					double a = Math.toRadians(-150 + q * 60);
					double d = 2 + 4 * Ease.OUT_CUBIC.apply(pu);
					RouletteWheelView.dot(g, f.x1() + d * Math.cos(a) - 0.5, f.y1() + chip / 2.0 - 1 + d * Math.sin(a) * 0.5, 1,
						TableGfx.alpha(0xFFE8DCC8, 0.7 * (1 - pu)), k);
				}
				continue;
			}
			if (f.back()) {
				double u = age / BACK_MS;
				if (u >= 1) {
					it.remove();
					continue;
				}
				double e = Ease.IN_CUBIC.apply(u);
				double x = f.x0() + (f.x1() - f.x0()) * e;
				double y = f.y0() + (f.y1() - f.y0()) * e - 14 * Math.sin(Math.PI * u);
				disc(g, f.denom(), x, y, chip, 1 - 0.3 * u, k);
				continue;
			}
			if (age < PLACE_MS) {
				double u = Ease.OUT_CUBIC.apply(age / PLACE_MS);
				double cx = (f.x0() + f.x1()) / 2;
				double cy = Math.min(f.y0(), f.y1()) - 18;
				double tx = f.x1();
				double ty = f.y1() - 2;
				double x = (1 - u) * (1 - u) * f.x0() + 2 * (1 - u) * u * cx + u * u * tx;
				double y = (1 - u) * (1 - u) * f.y0() + 2 * (1 - u) * u * cy + u * u * ty;
				disc(g, f.denom(), x, y, chip, 1, k);
			} else if (age < PLACE_MS + DROP_MS) {
				double u = (age - PLACE_MS) / DROP_MS;
				disc(g, f.denom(), f.x1(), f.y1() - 2 * (1 - u * u), chip, 1, k);
			} else {
				FxSounds.play("chip_place", 1f, pitch(f.denom()));
				it.remove();
				if (puffs == null) puffs = new ArrayList<>();
				puffs.add(new Flight(f.spot(), PUFF, f.x1(), f.y1(), f.x1(), f.y1(), now, false));
			}
		}
		if (puffs != null) flights.addAll(puffs);
		landing.entrySet().removeIf(e -> now - e.getValue() > 2000);
	}

	private static void disc(GuiGraphicsExtractor g, long denom, double cx, double cy, int chip, double alpha, int k) {
		String sprite = "core/stack/chip_" + denomSprite(denom);
		SnapDraw.blitF(g, TableGfx.sprite(sprite), cx - chip / 2.0, cy - chip / 2.0, chip, chip - 1, TableGfx.fade(alpha), k);
	}

	/** The chip sprite of a denomination (the kit's set; unknown → the nearest lower one). */
	private static long denomSprite(long denom) {
		long[] set = {1, 5, 25, 100, 500, 1000, 5000};
		long best = 1;
		for (long d : set) if (d <= denom) best = d;
		return best;
	}

	// ---- result beats ------------------------------------------------------------------------------------------------

	/** Starts a RESULT frame at local {@code rMs} (crossings voice the beats once; a skip is silent). */
	void beginResult(long key, double rMs) {
		if (key != beatKey) {
			beatKey = key;
			prevR = -1;
			curR = -1;
			paySounds = 0;
		}
		prevR = curR;
		curR = rMs;
	}

	/** True once when the local result time crosses {@code at} (never after a skip jump). */
	boolean cross(double at) {
		return prevR >= 0 && prevR < at && curR >= at && curR - prevR < 500;
	}

	/** Rake bar y at {@code rMs}: from the layout's bottom to the house edge, {@code inOutCubic}. NaN = no bar. */
	static double rakeY(double rMs, double bottom, double top, boolean reduce) {
		if (reduce) return Double.NaN;
		if (rMs < RAKE_START) return bottom;
		double u = Ease.IN_OUT_CUBIC.apply(Math.min(1, (rMs - RAKE_START) / RAKE_MS));
		return bottom + (top - bottom) * u;
	}

	/** A losing stack under the rake: {dy, alpha}. It is caught when the bar reaches it, rides up and fades past the top. */
	static double[] rake(double rMs, double bar, double stackBottom, double top, boolean reduce) {
		if (rMs < RAKE_START) return new double[] {0, 1};
		if (reduce) return new double[] {0, Math.max(0, 1 - (rMs - RAKE_START) / 120)};
		double dy = Double.isNaN(bar) || bar > stackBottom ? 0 : bar - stackBottom;
		double a = rMs <= RAKE_END ? 1 : Math.max(0, 1 - (rMs - RAKE_END) / RAKE_FADE);
		if (dy < 0) a *= Math.max(0.25, 1 - 0.5 * (-dy) / Math.max(1, stackBottom - top)); // squeezed into the pile
		return new double[] {dy, a};
	}

	/** A winning stack's flight to the TopBar balance: out = {dx, dy, alpha}. */
	static void flyToBalance(double rMs, Box disc, @Nullable Box balance, boolean reduce, double[] out) {
		out[0] = 0;
		out[1] = 0;
		out[2] = 1;
		if (rMs < FLY_START) return;
		if (reduce || balance == null) {
			out[2] = 0;
			return;
		}
		double u = Math.min(1, (rMs - FLY_START) / FLY_MS);
		double e = Ease.IN_CUBIC.apply(u);
		out[0] = (balance.cx() - disc.cx()) * e;
		out[1] = (balance.cy() - disc.cy()) * e;
		out[2] = u >= 1 ? 0 : 1 - 0.4 * e;
	}

	/** Payout discs sliding in from the house edge beside a winning stack (and riding its flight to the balance). */
	void payout(GuiGraphicsExtractor g, RouletteLayout l, Box disc, long profit, int bet, double rMs, double top, double[] fly, boolean reduce, int k) {
		if (reduce || fly[2] <= 0.02) return;
		List<Long> ds = ChipStack.discs(profit);
		int n = Math.min(8, ds.size());
		double x1 = disc.right() + 1;
		for (int j = 0; j < n; j++) {
			double start = PAY_START + PAY_STAGGER * (j + 2 * bet);
			if (rMs < start) continue;
			double u = Ease.OUT_CUBIC.apply(Math.min(1, (rMs - start) / PAY_MS));
			double y1 = disc.bottom() - l.chip - j * Math.max(1, l.rise);
			double x = x1 + l.chip / 2.0;
			double y = top + (y1 + l.chip / 2.0 - top) * u;
			if (cross(start + PAY_MS) && paySounds < 4) {
				paySounds++;
				FxSounds.play("chip_stack", 0.8f, 1f + 0.05f * j);
			}
			disc(g, ds.get(j), x + fly[0], y + fly[1], l.chip, fly[2], k);
		}
	}

	/** The dolly on the winning number: drops 24 px ({@code outBounce}), its shadow darkens; stays until betting. */
	void dolly(GuiGraphicsExtractor g, RouletteLayout l, int result, double rMs, boolean reduce, int k) {
		if (rMs < DOLLY_START) return;
		Box c = l.cell(result);
		double u = reduce ? 1 : Math.min(1, (rMs - DOLLY_START) / DOLLY_MS);
		double drop = reduce ? 0 : 24 * (1 - Ease.OUT_BOUNCE.apply(u));
		if (cross(DOLLY_START + DOLLY_MS * 0.364)) FxSounds.play("roulette_dolly", 1f, 1f);
		if (reduce && cross(DOLLY_START)) FxSounds.play("roulette_dolly", 1f, 1f);
		double x = c.cx() - 6;
		double y = c.cy() - 10;
		SnapDraw.blitF(g, TableGfx.sprite("roulette/dolly_shadow"), x, y + 11, 12, 4, TableGfx.fade(0.3 + 0.7 * u), k);
		SnapDraw.blitF(g, TableGfx.sprite("roulette/dolly"), x, PixelSnap.snap(y - drop, k), 12, 14, 0xFFFFFFFF, k);
	}

	/** The dolly fading away as betting opens. */
	void dollyFade(GuiGraphicsExtractor g, RouletteLayout l, int last, double ms, int k) {
		Box c = l.cell(last);
		double a = 1 - ms / 300.0;
		if (a <= 0.02) return;
		SnapDraw.blitF(g, TableGfx.sprite("roulette/dolly_shadow"), c.cx() - 6, c.cy() + 1, 12, 4, TableGfx.fade(a), k);
		SnapDraw.blitF(g, TableGfx.sprite("roulette/dolly"), c.cx() - 6, c.cy() - 10, 12, 14, TableGfx.fade(a), k);
	}
}
