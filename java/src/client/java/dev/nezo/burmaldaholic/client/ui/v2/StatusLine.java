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
 */
public final class StatusLine {
	public static final int FLASH_MS = 3000, WATCHDOG_MS = 4000;

	private Box box = Box.EMPTY;
	private Box strip = Box.EMPTY;
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
	}

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
		this.box = box;
		this.strip = strip;
	}

	/** The hint strip of the last layout (empty = none). */
	public Box strip() {
		return strip;
	}

	/** The width the two lines want ({@code max(w(line1), w(line2))}, capped by the caller). */
	public int measure(Font font) {
		Component l1 = line1();
		Component l2 = line2();
		return Math.max(l1 == null ? 0 : font.width(l1), l2 == null ? 0 : font.width(l2)) + 2;
	}

	public Box box() {
		return box;
	}

	/** What line 1 shows now (tests read it). */
	public @Nullable Component line1() {
		long now = Util.getMillis();
		if (watchdog != null && now - watchdogAt < WATCHDOG_MS) return watchdog;
		if (error != null && now - errorAt < FLASH_MS) return error;
		if (flash != null && now - flashAt < FLASH_MS) return flash;
		if (reason != null) return reason;
		return line1;
	}

	public @Nullable Component line2() {
		if (line2 != null) return line2;
		return hint;
	}

	private int line1Color() {
		long now = Util.getMillis();
		if ((watchdog != null && now - watchdogAt < WATCHDOG_MS) || (error != null && now - errorAt < FLASH_MS)) return CasinoPalette.V2_LOSE;
		if (flash != null && now - flashAt < FLASH_MS) return CasinoPalette.V2_GOLD;
		if (reason != null) return CasinoPalette.V2_GOLD;
		return line1Color;
	}

	public void draw(GuiGraphicsExtractor g, Font font) {
		if (box.isEmpty()) return;
		Component l1 = line1();
		Component l2 = line2();
		if (l2 != null && !strip.isEmpty()) { // line 2 above the Dock, on a dark strip; the Dock keeps line 1
			V2Draw.fill(g, strip, V2Draw.alpha(CasinoPalette.V2_INK, 0.85f));
			V2Draw.left(g, font, l2, strip.inset(1), 3, 1, CasinoPalette.V2_MUTED, true);
			l2 = null;
		}
		int lh = 11;
		if (l1 != null && l2 == null && font.width(l1) > box.w() && box.h() >= 2 * lh + 4) {
			// one long line 1 and the second line free: wrap it onto two 1× lines (DES 3) instead of an ellipsis
			java.util.List<net.minecraft.util.FormattedCharSequence> parts = font.split(l1, Math.max(1, box.w()));
			if (parts.size() >= 2) {
				int y = box.y() + Math.max(0, (box.h() - 2 * lh - 4) / 2);
				for (int i = 0; i < 2; i++) {
					V2Draw.text(g, font, parts.get(i), box.x(), y + (lh - 8) / 2 + 1, 1, line1Color(), true);
					y += lh;
				}
				timer.draw(g, new Box(box.x(), Math.min(box.bottom() - 3, y + 1), Math.min(box.w(), 120), 3));
				return;
			}
		}
		int total = (l1 != null ? lh : 0) + (l2 != null ? lh : 0);
		int y = box.y() + Math.max(0, (box.h() - total - 4) / 2);
		if (l1 != null) {
			V2Draw.left(g, font, l1, new Box(box.x(), y, box.w(), lh), 0, 1, line1Color(), true);
			y += lh;
		}
		if (l2 != null) {
			V2Draw.left(g, font, l2, new Box(box.x(), y, box.w(), lh), 0, 1, CasinoPalette.V2_MUTED, true);
			y += lh;
		}
		timer.draw(g, new Box(box.x(), Math.min(box.bottom() - 3, y + 1), Math.min(box.w(), 120), 3));
	}
}
