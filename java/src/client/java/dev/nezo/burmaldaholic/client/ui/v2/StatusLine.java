package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The two status lines of the Dock (1× gold / 1× muted, ellipsised). Line 1 priority (docs/architecture/ui-v2.md §4.2):
 * watchdog > error > flashed reason (a click on a disabled target) > the primary's reason > the game's line 1. Line 2:
 * the game's line 2 > the hint > the timer text. A {@link TimerBar} sits under the lines.
 *
 * <p>Layout (money bug T-B2 M1): the Dock is laid out ONLY from the <b>stable</b> lines ({@link #stableLine1()} /
 * {@link #stableLine2()}: the game's lines, its hint and the primary's reason, which change with the state). The
 * <b>transient</b> lines (watchdog, error, flash; "the buttons moved, click again" is one) never feed {@link #measure}:
 * they are drawn into the status box the stable lines got, and what does not fit there goes into an overlay band just
 * above the Dock ({@link #place(Box, Box, Box)}), drawn over the felt without moving any control. A refused click's own
 * message therefore can never move the button the player is told to click again.
 */
public final class StatusLine {
	public static final int FLASH_MS = 3000, WATCHDOG_MS = 4000;

	private Box box = Box.EMPTY;
	private Box strip = Box.EMPTY;
	private Box overlay = Box.EMPTY;
	private @Nullable Component line1, line2, hint;
	private @Nullable Component watchdog, error, flash, reason;
	private long watchdogAt, errorAt, flashAt;
	private int line1Color = CasinoPalette.V2_GOLD;
	public final TimerBar timer = new TimerBar();

	/** The game's lines (null = none); line 1 is gold unless {@link #color}. */
	public void set(@Nullable Component line1, @Nullable Component line2) {
		this.line1 = line1;
		this.line2 = line2;
	}

	/** Colour of the game's line 1 (e.g. win green for a result summary). */
	public void color(int argb) {
		line1Color = argb;
	}

	public void hint(@Nullable Component hint) {
		this.hint = hint;
	}

	/** The primary's state: its reason shows on line 1 while it is disabled. */
	public void reason(Gate primary) {
		this.reason = V2Draw.reason(primary);
		this.phaseReason = !primary.enabled() && PHASE_REASONS.contains(primary.reasonKey());
	}

	/**
	 * Reasons that only name the phase ("Spinning…", "Round in progress", "Bets closed"): when the game has its own hint
	 * for that phase ("The ball is spinning…"), the hint replaces it on line 1 instead of saying the same thing twice
	 * (review T-C2 V8).
	 */
	private static final java.util.Set<String> PHASE_REASONS = java.util.Set.of(dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_SPINNING,
		dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_ROUND_IN_PROGRESS, dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_BETS_CLOSED,
		dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_ROLLING, dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_DEALING);
	private boolean phaseReason;

	/** A click on a disabled target: its reason, for {@link #FLASH_MS}. */
	public void flash(Component reason) {
		this.flash = reason;
		this.flashAt = Util.getMillis();
	}

	/** Drops a flashed reason (the player did something that worked since). */
	public void clearFlash() {
		flash = null;
	}

	public void error(Component message) {
		this.error = message;
		this.errorAt = Util.getMillis();
	}

	public void watchdog(Component message) {
		this.watchdog = message;
		this.watchdogAt = Util.getMillis();
	}

	public void timer(int secondsLeft, int total) {
		timer.set(secondsLeft, total);
	}

	void place(Box box) {
		place(box, Box.EMPTY);
	}

	/** The Dock status box and the hint strip above the Dock (empty = both lines in the Dock; review R-KW B2). */
	void place(Box box, Box strip) {
		place(box, strip, Box.EMPTY);
	}

	/**
	 * As {@link #place(Box, Box)}, plus {@code overlay}: where a transient line that does not fit the box is drawn when the
	 * layout opened no strip (the Dock-wide band just above the Dock, over the felt; never a hit target).
	 */
	void place(Box box, Box strip, Box overlay) {
		this.box = box;
		this.strip = strip;
		this.overlay = overlay;
	}

	/** Drops every transient line (watchdog, error, flash); tests use it between probes. */
	public void clearTransient() {
		watchdog = null;
		error = null;
		flash = null;
	}

	/** The transient line showing now (watchdog > error > flash), or null. */
	public @Nullable Component transientLine() {
		long now = Util.getMillis();
		if (watchdog != null && now - watchdogAt < WATCHDOG_MS) return watchdog;
		if (error != null && now - errorAt < FLASH_MS) return error;
		if (flash != null && now - flashAt < FLASH_MS) return flash;
		return null;
	}

	/** The hint strip of the last layout (empty = none). */
	public Box strip() {
		return strip;
	}

	/**
	 * The column width at which both STABLE lines fit the Dock ({@link dev.nezo.burmaldaholic.core.ui.v2.StatusPlan#want},
	 * uncapped). Transient lines are never measured (T-B2 M1): they must not move the controls.
	 */
	public int measure(Font font) {
		return measure(font::width);
	}

	/** {@link #measure(Font)} with a width function (unit tests). */
	public int measure(java.util.function.ToIntFunction<String> width) {
		Component l1 = stableLine1();
		Component l2 = stableLine2();
		if (l1 != measuredL1 || l2 != measuredL2 || measured < 0) { // cached by line identity (the chrome asks every frame)
			measuredL1 = l1;
			measuredL2 = l2;
			measured = dev.nezo.burmaldaholic.core.ui.v2.StatusPlan.want(l1 == null ? null : l1.getString(), l2 == null ? null : l2.getString(), width);
		}
		return measured;
	}

	private @Nullable Component measuredL1, measuredL2;
	private int measured = -1;

	public Box box() {
		return box;
	}

	/** What line 1 shows now (tests read it): a transient line, else the stable one. */
	public @Nullable Component line1() {
		Component t = transientLine();
		return t != null ? t : stableLine1();
	}

	/** Line 1 without transient lines: the primary's reason (or the phase hint) or the game's line 1. Lays the Dock out. */
	public @Nullable Component stableLine1() {
		if (reason != null && phaseReason && line2 == null && hint != null) return hint;
		if (reason != null) return reason;
		return line1;
	}

	public @Nullable Component line2() {
		return line2After(line1());
	}

	/** Line 2 beside {@link #stableLine1()} (lays the Dock out). */
	public @Nullable Component stableLine2() {
		return line2After(stableLine1());
	}

	private @Nullable Component line2After(@Nullable Component l1) {
		Component l2 = line2 != null ? line2 : hint;
		if (l2 == null) return null;
		if (l1 == l2 || (l1 != null && l1.getString().equals(l2.getString()))) return null; // said once (a phase reason; T-K2 D2)
		return l2;
	}

	/**
	 * The strip the plan may use now: the layout's strip, or (while a transient line shows and the layout opened none) the
	 * overlay band above the Dock.
	 */
	private Box drawStrip() {
		return strip.isEmpty() && transientLine() != null ? overlay : strip;
	}

	private int line1Color() {
		long now = Util.getMillis();
		if ((watchdog != null && now - watchdogAt < WATCHDOG_MS) || (error != null && now - errorAt < FLASH_MS)) return CasinoPalette.V2_LOSE;
		if (flash != null && now - flashAt < FLASH_MS) return CasinoPalette.V2_GOLD;
		if (reason != null) return CasinoPalette.V2_GOLD;
		return line1Color;
	}

	private dev.nezo.burmaldaholic.core.ui.v2.StatusPlan plan = dev.nezo.burmaldaholic.core.ui.v2.StatusPlan.EMPTY;
	private @Nullable Component planL1, planL2;
	private Box planBox = Box.EMPTY, planStrip = Box.EMPTY;

	/**
	 * Where the lines go now ({@link dev.nezo.burmaldaholic.core.ui.v2.StatusPlan}); recomputed only when a line or a box
	 * changes (no per-frame wrapping, T-K2 D7). Tests read it to prove no word is lost.
	 */
	public dev.nezo.burmaldaholic.core.ui.v2.StatusPlan plan(Font font) {
		return plan(font::width);
	}

	/** {@link #plan(Font)} with a width function (unit tests). */
	public dev.nezo.burmaldaholic.core.ui.v2.StatusPlan plan(java.util.function.ToIntFunction<String> width) {
		Component l1 = line1();
		Component l2 = line2();
		Box strip = drawStrip();
		if (l1 != planL1 || l2 != planL2 || !box.equals(planBox) || !strip.equals(planStrip)) {
			planL1 = l1;
			planL2 = l2;
			planBox = box;
			planStrip = strip;
			plan = dev.nezo.burmaldaholic.core.ui.v2.StatusPlan.of(l1 == null ? null : l1.getString(), l2 == null ? null : l2.getString(), box.w(), box.h(),
				strip.isEmpty() ? 0 : strip.w(), width);
		}
		return plan;
	}

	/**
	 * The strip area the STABLE lines use (one or two rows; empty = none): the banner keeps clear of it. A transient line's
	 * overlay is no obstacle (it must not move anything either).
	 */
	public Box stripUsed(Font font) {
		if (strip.isEmpty()) return Box.EMPTY;
		Component l1 = stableLine1();
		Component l2 = stableLine2();
		dev.nezo.burmaldaholic.core.ui.v2.StatusPlan p = dev.nezo.burmaldaholic.core.ui.v2.StatusPlan.of(l1 == null ? null : l1.getString(),
			l2 == null ? null : l2.getString(), box.w(), box.h(), strip.w(), font::width);
		if (p.stripText() == null) return Box.EMPTY;
		return p.stripRows() == 2 ? new Box(strip.x(), strip.y() - strip.h(), strip.w(), 2 * strip.h()) : strip;
	}

	/** What the plan would cut ("" = every word readable). */
	public String lost(Font font) {
		return lost(font::width);
	}

	/** {@link #lost(Font)} with a width function (unit tests). */
	public String lost(java.util.function.ToIntFunction<String> width) {
		Component l1 = line1();
		Component l2 = line2();
		Box strip = drawStrip();
		return plan(width).lost(l1 == null ? null : l1.getString(), l2 == null ? null : l2.getString(), box.w(), strip.isEmpty() ? 0 : strip.w(), width);
	}

	public void draw(GuiGraphicsExtractor g, Font font) {
		Box strip = drawStrip();
		if (box.isEmpty() && strip.isEmpty()) return;
		dev.nezo.burmaldaholic.core.ui.v2.StatusPlan p = plan(font);
		String st = p.stripText();
		if (st != null && !strip.isEmpty() && p.stripRows() == 2) { // two long lines: line 1 on a row above line 2
			int w = Math.min(strip.w(), Math.max(font.width(p.stripL1()), font.width(p.stripL2())) + 8);
			Box band = new Box(strip.x(), strip.y() - strip.h(), w, 2 * strip.h());
			V2Draw.fill(g, band, V2Draw.alpha(CasinoPalette.V2_INK, 0.85f));
			V2Draw.text(g, font, raw(p.stripL1()), band.x() + 4, band.y() + (strip.h() - 8) / 2 + 1, 1, line1Color(), true);
			V2Draw.text(g, font, raw(p.stripL2()), band.x() + 4, strip.y() + (strip.h() - 8) / 2 + 1, 1, CasinoPalette.V2_MUTED, true);
		} else if (st != null && !strip.isEmpty()) { // the hint strip above the Dock, on a dark band behind its text only
			Box band = new Box(strip.x(), strip.y(), Math.min(strip.w(), font.width(st) + 8), strip.h());
			V2Draw.fill(g, band, V2Draw.alpha(CasinoPalette.V2_INK, 0.85f));
			int x = band.x() + 4, ty = band.y() + (band.h() - 8) / 2 + 1;
			if (p.stripL1() != null) {
				V2Draw.text(g, font, raw(p.stripL1()), x, ty, 1, line1Color(), true);
				x += font.width(p.stripL1());
				if (p.stripL2() != null) x += font.width(dev.nezo.burmaldaholic.core.ui.v2.StatusPlan.JOIN);
			}
			if (p.stripL2() != null) V2Draw.text(g, font, raw(p.stripL2()), x, ty, 1, CasinoPalette.V2_MUTED, true);
		}
		int lh = dev.nezo.burmaldaholic.core.ui.v2.StatusPlan.ROW_H;
		int n = p.dock().size();
		if (n == 0) return; // no orphan timer underline in an empty status column (T-C2 V8)
		int y = box.y() + Math.max(0, (box.h() - n * lh - 4) / 2);
		for (int i = 0; i < n; i++) {
			V2Draw.text(g, font, raw(p.dock().get(i)), box.x(), y + (lh - 8) / 2 + 1, 1, p.dockGold().get(i) ? line1Color() : CasinoPalette.V2_MUTED, true);
			y += lh;
		}
		timer.draw(g, new Box(box.x(), Math.min(box.bottom() - 3, y + 1), Math.min(box.w(), 120), 3));
	}

	private final java.util.Map<String, Component> raws = new java.util.HashMap<>();

	/** A literal component per drawn string, kept while the plan lives (no per-frame allocation). */
	private Component raw(String s) {
		if (raws.size() > 16) raws.clear();
		return raws.computeIfAbsent(s, dev.nezo.burmaldaholic.core.text.Texts::raw);
	}
}
