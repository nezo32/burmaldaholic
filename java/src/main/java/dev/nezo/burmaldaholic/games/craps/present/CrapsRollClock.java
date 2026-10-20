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
	/** Dice home's move from one end of the Pass line to the other (smoothstep: peak about 0.07 u/ms, under a throw's own). */
	public static final double HOME_MOVE_MS = 1100;
	/** Fastest average speed of a settle into a throw that starts away from the hand (desync fallback), u/ms. */
	public static final double SETTLE_SPEED = 0.045;
	/** The fixed swap path of dice home between the two ends (one die passes behind the other). */
	private static final CrapsPair.Push HOME_PUSH;

	static {
		double[] r0 = CrapsThrow.release(CrapsThrow.RIGHT, 0), r1 = CrapsThrow.release(CrapsThrow.RIGHT, 1);
		double[] l0 = CrapsThrow.release(CrapsThrow.LEFT, 0), l1 = CrapsThrow.release(CrapsThrow.LEFT, 1);
		HOME_PUSH = CrapsPair.plan(r0[0], r0[1], r1[0], r1[1], l0[0], l0[1], l1[0], l1[1], 2.2, new CrapsPair.Push());
	}

	private Source source;
	private double pressAt = Double.NaN;
	private int rolls = -1;
	private double origin = Double.NaN;
	private boolean firstSight;
	private @Nullable DiceRoll roll;
	private int side = CrapsThrow.RIGHT;
	// what the hand held when the result came (the previous roll at its home), when (shake ms) and the release (shake ms)
	private @Nullable DiceRoll handRoll;
	private double handReady = Double.NaN;
	private double handRelease = Double.NaN;
	private double handStart = Double.NaN;
	private final int[] fallbackFaces = {4, 2};
	private final int[] handFaces = {4, 2};
	private final DieSample base = new DieSample(), start = new DieSample(), exit = new DieSample();
	// the settle's spread: the APART unit axis, or (a long crossing settle) the CrapsPair push
	private double spreadX, spreadY;
	private final CrapsPair.Push handPush = new CrapsPair.Push();
	// T-CM M1: dice home is the NEXT shooter's end of the Pass line (synced by the server); it moves along a fixed
	// non-crossing path S in [0 RIGHT, 1 LEFT] when the next shooter changes sides
	private boolean homeKnown;
	private double homeS0, homeS1, homeAt = Double.NaN;
	private final double[] homeTmp = new double[4], relTmp = new double[2], handHome = new double[4];
	private final double[] px2 = new double[2], py2 = new double[2];

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
		// a counter that went backwards is another table at this place (broken and replaced): a first sight
		boolean first = this.rolls < 0 || rolls < this.rolls;
		this.rolls = rolls;
		double now = now();
		DiceRoll prev = roll;
		roll = r;
		this.side = side;
		firstSight = false;
		handRelease = Double.NaN;
		if (!Double.isNaN(pressAt) && r != null) {
			handReady = now - pressAt;
			handStart = pressAt;
			handRoll = prev;
			handFaces[0] = fallbackFaces[0];
			handFaces[1] = fallbackFaces[1];
			// the settle runs from dice home to the throw's start; when they are apart (the throw comes from the other
			// end: a side this client did not know yet) it takes longer and passes one die behind the other
			// (the hand's home is frozen here: a later move of dice home never drags the settling hand)
			home(handHome);
			double far = 0;
			for (int d = 0; d < 2; d++) {
				CrapsThrow.release(side, d, relTmp);
				far = Math.max(far, Math.hypot(relTmp[0] - handHome[2 * d], relTmp[1] - handHome[2 * d + 1]));
			}
			double[] a0 = CrapsThrow.release(side, 0, px2), a1 = CrapsThrow.release(side, 1, py2);
			if (far > 1.0) {
				CrapsPair.plan(handHome[0], handHome[1], handHome[2], handHome[3], a0[0], a0[1], a1[0], a1[1], 2.2, handPush);
				spreadX = spreadY = 0;
			} else {
				handPush.p = 0;
				double dx = a1[0] - a0[0], dy = a1[1] - a0[1], n = Math.max(1e-9, Math.hypot(dx, dy));
				spreadX = dx / n;
				spreadY = dy / n;
			}
			handRelease = CrapsHand.releaseAt(handReady, far * 1.5 / SETTLE_SPEED);
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
			if (!homeKnown) nextSide(side);
		}
		if (r != null) {
			int[] f = r.faces();
			fallbackFaces[0] = f[0];
			fallbackFaces[1] = f[1];
		}
		return true;
	}

	/**
	 * The next shooter's end of the Pass line (server state {@code next_side}): dice home, where the stick drags the dice
	 * and where the hand picks them up. The first value is taken as is; a change moves dice home along the fixed swap
	 * path over {@link #HOME_MOVE_MS} (never a jump; a reversal continues from where it is).
	 */
	public void nextSide(int side) {
		double target = side == CrapsThrow.LEFT ? 1 : 0;
		if (!homeKnown) {
			homeKnown = true;
			homeS0 = homeS1 = target;
			homeAt = Double.NaN;
			return;
		}
		if (target == homeS1) return;
		homeS0 = homeS();
		homeS1 = target;
		homeAt = now();
	}

	/** The next shooter's side as known here (dice home's destination). */
	public int nextSide() {
		return homeS1 >= 0.5 ? CrapsThrow.LEFT : CrapsThrow.RIGHT;
	}

	/** Ends a running move of dice home now (test hook: under a frozen clock a move would never progress). */
	public void finishHomeMove() {
		homeS0 = homeS1;
		homeAt = Double.NaN;
	}

	/** True while dice home moves to the other end. */
	public boolean homeMoving() {
		return homeS() != homeS1;
	}

	private double homeS() {
		if (Double.isNaN(homeAt)) return homeS1;
		double span = HOME_MOVE_MS * Math.abs(homeS1 - homeS0);
		double p = span <= 0 ? 1 : (now() - homeAt) / span;
		if (p >= 1) return homeS1;
		p = Math.max(0, p);
		return homeS0 + (homeS1 - homeS0) * p * p * (3 - 2 * p);
	}

	/** Dice home now: the die centres {x0, y0, x1, y1} in tray units (moving when the next shooter changed sides). */
	public double[] home(double[] out) {
		double s = homeS();
		for (int d = 0; d < 2; d++) {
			CrapsThrow.release(CrapsThrow.RIGHT, d, px2);
			CrapsThrow.release(CrapsThrow.LEFT, d, py2);
			out[2 * d] = px2[0] + (py2[0] - px2[0]) * s;
			out[2 * d + 1] = px2[1] + (py2[1] - px2[1]) * s;
		}
		if (HOME_PUSH.p > 0 && s > 0 && s < 1) {
			double k = HOME_PUSH.p * StrictMath.sin(Math.PI * s);
			out[2 * HOME_PUSH.die] += HOME_PUSH.nx * k;
			out[2 * HOME_PUSH.die + 1] += HOME_PUSH.ny * k;
		}
		return out;
	}

	/** The resting pose of die {@code die} at dice home now (the last roll's faces squared up, else 4 and 2). */
	public void homePose(int die, DieSample out) {
		home(homeTmp);
		CrapsHand.home(homeTmp[2 * die], homeTmp[2 * die + 1], roll, fallbackFaces, die, out);
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
				home(homeTmp);
				CrapsHand.home(homeTmp[2 * die], homeTmp[2 * die + 1], roll, fallbackFaces, die, base);
				CrapsHand.shake(base, die, h, out);
				return true;
			}
			CrapsHand.home(handHome[2 * die], handHome[2 * die + 1], handRoll, handFaces, die, base);
			CrapsHand.shake(base, die, h, out);
			DiceRoll r = roll;
			if (r != null) {
				r.sample(die, 0, start);
				// spread the pair along the line between the two dice while they turn into the throw's start pose (or,
				// on a long crossing settle, pass one die behind the other)
				double sg = die == 0 ? -1 : 1;
				double px = 0, py = 0;
				if (handPush.p > 0 && handPush.die == die) {
					px = handPush.nx * handPush.p;
					py = handPush.ny * handPush.p;
				}
				CrapsHand.shake(base, die, handRelease, exit);
				CrapsHand.settle(out, exit, start, (h - handReady) / (handRelease - handReady), sg * spreadX, sg * spreadY, px, py, out);
			}
			return true;
		}
		DiceRoll r = roll;
		if (r == null) return false;
		r.sample(die, t, out);
		return true;
	}
}
