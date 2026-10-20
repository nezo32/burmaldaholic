package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import org.jspecify.annotations.Nullable;

/**
 * The local storyboard clock of ONE craps table on this client (S1, owner playtest 1: "craps is horrible on bad
 * internet"). The screen and the in-world board read the same instance (client registry by table position), so they
 * always agree.
 *
 * <ul>
 *   <li><b>Roll pressed</b> ({@link #press}): the hand rattles at dice home with NO time cap until the result arrives
 *       ({@link #onRoll}) or the server answers without a roll ({@link #cancel}).</li>
 *   <li><b>Result in</b>: the throw's t = 0 is fixed ONCE, on the local clock, at the rattle loop's next release pose
 *       ({@link CrapsHand#releaseAt}); until then the hand settles into the solver's start pose, so the plain
 *       deterministic throw starts exactly where the hand is. Later state packets for the same roll (bunched,
 *       re-synced server time) never move the origin.</li>
 *   <li><b>Spectator</b> (no press): the throw starts from dice home when its result arrives (t = 0 then), so a late
 *       packet delays the throw instead of jumping into it. Only the FIRST roll this client sees (the screen opened /
 *       the table came into view) and a roll older than {@link #STALE_MS} take the server's age: they show the throw
 *       already under way or settled.</li>
 * </ul>
 *
 * <p>The clock source is a {@link Source} so the shared SmoothClock (package SY) can replace the wall clock later
 * ({@link #source(Source)}). Pure.
 */
public final class CrapsRollClock {
	/** Local monotonic milliseconds. */
	@FunctionalInterface
	public interface Source {
		double nowMs();
	}

	/** A roll older than this on arrival is long settled: it is shown at the server's age, not replayed. */
	public static final double STALE_MS = 6000;

	private Source source;
	private double pressAt = Double.NaN;
	private int rolls = -1;
	private double origin = Double.NaN;
	private boolean firstSight;
	private @Nullable DiceRoll roll;
	private int side = CrapsThrow.RIGHT;
	// what the hand held when the result came (the previous roll at its home), when (shake ms) and the release (shake ms)
	private @Nullable DiceRoll handRoll;
	private int handSide;
	private double handReady = Double.NaN;
	private double handRelease = Double.NaN;
	private double handStart = Double.NaN;
	private final int[] fallbackFaces = {4, 2};
	private final int[] handFaces = {4, 2};
	private final DieSample base = new DieSample(), start = new DieSample(), exit = new DieSample();

	public CrapsRollClock(Source source) {
		this.source = source;
	}

	/** Swaps the clock source (SY's SmoothClock); a running throw keeps its place in local time. */
	public void source(Source s) {
		double shift = s.nowMs() - source.nowMs();
		if (!Double.isNaN(pressAt)) pressAt += shift;
		if (!Double.isNaN(origin)) origin += shift;
		if (!Double.isNaN(handStart)) handStart += shift;
		source = s;
	}

	public double now() {
		return source.nowMs();
	}

	/** Roll pressed on this client: the hand starts rattling (a second press while it rattles changes nothing). */
	public void press() {
		if (Double.isNaN(pressAt)) pressAt = now();
	}

	/** The server answered the Roll without a roll (refused / not your turn): the hand puts the dice down. */
	public void cancel() {
		pressAt = Double.NaN;
	}

	/** True from the press until the result is in. */
	public boolean waiting() {
		return !Double.isNaN(pressAt);
	}

	/**
	 * A state carrying roll counter {@code rolls} with the throw {@code r} (the plain deterministic solve) arrived.
	 * Only a NEW counter does anything: it fixes the throw's origin and keeps it. {@code serverAgeMs} = the server
	 * clock's estimate of ms since the roll (only the first sight and a stale roll use it). Returns true for a new roll.
	 */
	public boolean onRoll(int rolls, double serverAgeMs, @Nullable DiceRoll r, int side) {
		if (rolls == this.rolls) return false;
		boolean first = this.rolls < 0;
		this.rolls = rolls;
		double now = now();
		DiceRoll prev = roll;
		int prevSide = this.side;
		roll = r;
		this.side = side;
		firstSight = false;
		handRelease = Double.NaN;
		if (!Double.isNaN(pressAt) && r != null) {
			handReady = now - pressAt;
			handRelease = CrapsHand.releaseAt(handReady);
			handStart = pressAt;
			handRoll = prev;
			handSide = prevSide;
			handFaces[0] = fallbackFaces[0];
			handFaces[1] = fallbackFaces[1];
			origin = pressAt + handRelease;
			pressAt = Double.NaN;
		} else {
			pressAt = Double.NaN;
			double age = Double.isNaN(serverAgeMs) ? 0 : Math.max(0, serverAgeMs);
			if (first || age >= STALE_MS) {
				firstSight = true;
				origin = now - age;
			} else {
				origin = now;
			}
		}
		if (r != null) {
			int[] f = r.faces();
			fallbackFaces[0] = f[0];
			fallbackFaces[1] = f[1];
		}
		return true;
	}

	/** Test hook: the throw's origin pinned to {@code originMs} on this clock's timeline (no hand). */
	public void pin(double originMs) {
		origin = originMs;
		pressAt = Double.NaN;
		handRelease = Double.NaN;
	}

	public int rolls() {
		return rolls;
	}

	public @Nullable DiceRoll roll() {
		return roll;
	}

	public int side() {
		return side;
	}

	/** The current roll was first seen mid-way (screen opened / table came into view) or stale: callers may show it settled. */
	public boolean firstSight() {
		return firstSight;
	}

	/** The current throw left this client's hand (the shooter pressed Roll here; tests). */
	public boolean fromHand() {
		return !Double.isNaN(handRelease);
	}

	/** Shake ms the result came in and of the release (tests; NaN when the throw did not leave this client's hand). */
	public double[] handTimes() {
		return new double[] {handReady, handRelease};
	}

	/** ms into the current throw (negative while the hand still settles before the release; +∞ without a roll). */
	public double t() {
		return Double.isNaN(origin) ? Double.POSITIVE_INFINITY : now() - origin;
	}

	/** Shake ms of the hand now, or NaN when the hand is not rattling. */
	public double handMs() {
		if (!Double.isNaN(pressAt)) return now() - pressAt;
		if (!Double.isNaN(handRelease) && t() < 0) return now() - handStart;
		return Double.NaN;
	}

	/**
	 * The pose of die {@code die} in tray units for the hand and the throw phases: the rattling hand (settling into the
	 * throw's start once the result is in), else the solved throw at {@code t}. Returns false when there is no roll and
	 * no hand (the caller draws its own rest).
	 */
	public boolean pose(int die, double t, DieSample out) {
		double h = handMs();
		if (!Double.isNaN(h)) {
			if (!Double.isNaN(pressAt)) {
				CrapsHand.home(side, roll, fallbackFaces, die, base);
				CrapsHand.shake(base, die, h, out);
				return true;
			}
			CrapsHand.home(handSide, handRoll, handFaces, die, base);
			CrapsHand.shake(base, die, h, out);
			DiceRoll r = roll;
			if (r != null) {
				r.sample(die, 0, start);
				// spread the pair along the line between the two dice while they turn into the throw's start pose
				double[] p0 = CrapsThrow.release(side, 0), p1 = CrapsThrow.release(side, 1);
				double dx = p1[0] - p0[0], dy = p1[1] - p0[1], n = Math.max(1e-9, Math.hypot(dx, dy)), sg = die == 0 ? -1 : 1;
				CrapsHand.shake(base, die, handRelease, exit);
				CrapsHand.settle(out, exit, start, (h - handReady) / (handRelease - handReady), sg * dx / n, sg * dy / n, out);
			}
			return true;
		}
		DiceRoll r = roll;
		if (r == null) return false;
		r.sample(die, t, out);
		return true;
	}
}
