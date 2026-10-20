package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * "A click acts only on the control the player saw under the cursor" (the UTH Check → Bet ×4 report, R3): the Dock
 * re-lays itself out when the state or the status text changes (a new street, a longer status line pushing the action
 * row into its compact form), so a click aimed at one control can land on another that has just moved or changed under
 * the cursor. The guard keeps the identity of every target (id + what it does: the primary's action, the decision
 * window) for:
 * <ul>
 * <li>the <b>rendered</b> layout (the last frame the player saw): a click is refused when the current layout has a
 * different control at the point than that frame;</li>
 * <li>the <b>settled</b> layout (the last one that stayed on screen for {@link #SETTLE_MS}): for {@link #SETTLE_MS} after a
 * change, a click (or a key) is refused when the control at the point (or behind the key) is not the one the settled
 * layout had there — no one aims and clicks within a human reaction time, so such a click was meant for the old one.</li>
 * </ul>
 * A refused click does nothing; the status says "the buttons moved". Pure (times are passed in), unit-tested. Moved into the
 * kit from B2 (poker / UTH, R3) so every v2 screen has it: {@code V2Chrome} lays it out after every relayout and checks
 * every click and kit key.
 */
public final class ClickGuard {
	/** About one human reaction time: a control must have been on screen this long to take a click. */
	public static final long SETTLE_MS = 250;
	/**
	 * Test hook: the settle time in use ({@link #SETTLE_MS}). A client GameTest lengthens it for one step so "a click
	 * right after the change is refused" does not depend on how fast the machine ticks. Never set in production.
	 */
	public static volatile long settleMs = SETTLE_MS;

	/** A target's box and identity (id, plus what it does now). */
	public record Entry(String id, Box box, String identity) {}

	private List<Entry> current = List.of();
	private List<Entry> settled = List.of();
	private List<Entry> rendered = List.of();
	private long changedAt = Long.MIN_VALUE / 2;

	/**
	 * After every relayout: the targets (topmost last, as in {@link HitMap}) and each one's identity
	 * ({@code identity.apply(id)}; e.g. {@code "primary|bet_4x|7"}). Returns whether the layout changed (a box, a target
	 * or an identity): the chrome then drops its Tab focus.
	 */
	public boolean layout(List<HitMap.Target> targets, Function<String, String> identity, long now) {
		List<Entry> next = new ArrayList<>(targets.size());
		for (HitMap.Target t : targets) next.add(new Entry(t.id(), t.box(), identity.apply(t.id())));
		if (next.equals(current)) return false;
		if (now - changedAt >= settleMs) settled = current; // the layout being replaced stayed long enough to be seen
		current = List.copyOf(next);
		changedAt = now;
		return true;
	}

	/** A frame was drawn with the current layout. */
	public void rendered() {
		rendered = current;
	}

	/** Whether the layout has stayed unchanged for {@link #SETTLE_MS} (tests wait for it before a click). */
	public boolean settled(long now) {
		return now - changedAt >= settleMs;
	}

	/** Whether a click at layout px ({@code x}, {@code y}) may act now. */
	public boolean allowsClick(double x, double y, long now) {
		String cur = at(current, x, y);
		if (cur == null) return true; // nothing there: the click falls through to nothing
		if (!cur.equals(at(rendered, x, y))) return false;
		return settled(now) || cur.equals(at(settled, x, y));
	}

	/** Whether a key press of target {@code id} may act now (the primary's Space, decision letters, Enter on a Tab focus). */
	public boolean allowsKey(String id, long now) {
		if (settled(now)) return true;
		String cur = byId(current, id);
		return cur == null || cur.equals(byId(settled, id));
	}

	private static @Nullable String at(List<Entry> layout, double x, double y) {
		for (int i = layout.size() - 1; i >= 0; i--) {
			if (layout.get(i).box().contains(x, y)) return layout.get(i).identity();
		}
		return null;
	}

	private static @Nullable String byId(List<Entry> layout, String id) {
		for (Entry e : layout) if (Objects.equals(e.id(), id)) return e.identity();
		return null;
	}
}
