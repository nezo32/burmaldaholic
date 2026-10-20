package dev.nezo.burmaldaholic.games.roulette.logic;

/**
 * What a roulette table publishes to spectators and the in-world wheel (block entity update tag; tables.md §1.7):
 * phase, the spin's launch / start / length / result / seed, the rotor at the launch and the last result, as an int
 * array. Sent once per phase change, never per tick. The result is only included from SPIN on (bets are closed then,
 * §0.6.7). Pure.
 *
 * <p>v2 (ROULETTE-ANIM-SPEC §2.5, 17 ints) adds the launch tick (NO_MORE_BETS start: seed and launch are known there,
 * the result is not), the rotor angle at the launch (centi-degrees, [0, 36000)), its signed speed (centi-degrees per
 * second) and the push direction. v1 (12 ints) still decodes: launch = spin start − 20, a rotor from the seed at idle
 * speed, direction −1.
 *
 * @param phase      0 betting, 1 no more bets, 2 spin, 3 result
 * @param phaseStart game time the phase began
 * @param spinStart  game time the last spin began (the draw)
 * @param spinTicks  spin length in ticks
 * @param result     result of the current / last spin, -1 = none
 * @param seed       cosmetic seed of the spin
 * @param lastResult the number the previous ball rests in (-1 = none): the idle wheel rides it
 * @param theme      0 village, 1 bastion, 2 end
 * @param seq        spin counter (a change = a new launch; 0 = the table never spun)
 * @param launchTick game time of the launch (NO_MORE_BETS start)
 * @param rotorA     rotor angle at the launch, centi-degrees
 * @param rotorW     rotor speed at the launch, signed centi-degrees per second
 * @param dir        direction of the rotor model (±1)
 * @param pushed     whether the dealer pushed the rotor at the launch (false: a table that never spun, or v1); on the wire
 *                   the direction is ±1 when pushed, ±2 when coasting
 */
public record RouletteSync(int phase, long phaseStart, long spinStart, int spinTicks, int result, int seed, int lastResult, int theme, int seq,
		long launchTick, int rotorA, int rotorW, int dir, boolean pushed) {
	public static final int VERSION = 2;
	public static final int V1 = 1;
	public static final int BETTING = 0;
	public static final int NO_MORE_BETS = 1;
	public static final int SPIN = 2;
	public static final int RESULT = 3;
	private static final int V1_LEN = 12;
	private static final int V2_LEN = 17;

	public RouletteSync {
		if (dir != 1 && dir != -1) dir = -1;
	}

	/** A v1-shaped sync (tests, previews): launch 20 ticks before the spin, a rotor from the seed. */
	public RouletteSync(int phase, long phaseStart, long spinStart, int spinTicks, int result, int seed, int lastResult, int theme, int seq) {
		this(phase, phaseStart, spinStart, spinTicks, result, seed, lastResult, theme, seq, spinStart - RouletteSpin.NMB_TICKS,
			Math.floorMod(seed, 36000), (int) Math.round(-RotorModel.OMEGA_IDLE * 100), -1, false);
	}

	/** A pushed launch (the server after NO_MORE_BETS). */
	public RouletteSync(int phase, long phaseStart, long spinStart, int spinTicks, int result, int seed, int lastResult, int theme, int seq,
			long launchTick, int rotorA, int rotorW, int dir) {
		this(phase, phaseStart, spinStart, spinTicks, result, seed, lastResult, theme, seq, launchTick, rotorA, rotorW, dir, true);
	}

	public int[] encode() {
		return new int[] {VERSION, phase, (int) (phaseStart >>> 32), (int) phaseStart, (int) (spinStart >>> 32), (int) spinStart, spinTicks, result, seed,
			lastResult, theme, seq, (int) (launchTick >>> 32), (int) launchTick, rotorA, rotorW, pushed ? dir : 2 * dir};
	}

	public static RouletteSync decode(int[] a) {
		if (a.length == V1_LEN && a[0] == V1) {
			return new RouletteSync(a[1], join(a[2], a[3]), join(a[4], a[5]), a[6], a[7], a[8], a[9], a[10], a[11]);
		}
		if (a.length != V2_LEN || a[0] != VERSION) {
			throw new IllegalArgumentException("roulette sync v" + (a.length > 0 ? a[0] : -1) + " len " + a.length);
		}
		int d = a[16];
		return new RouletteSync(a[1], join(a[2], a[3]), join(a[4], a[5]), a[6], a[7], a[8], a[9], a[10], a[11], join(a[12], a[13]), a[14], a[15],
			Integer.signum(d) == 0 ? -1 : Integer.signum(d), Math.abs(d) == 1);
	}

	private static long join(int hi, int lo) {
		return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
	}

	/** The rotor model of the last launch (sampled at ms since {@code launchTick·50}). */
	public RotorModel rotorModel() {
		return new RotorModel(rotorA / 100.0, rotorW / 100.0, dir, pushed);
	}

	/** The spin of this sync (result -1 before the draw), with the pickup of {@code previous} (-1 = none). */
	public RouletteSpin spin(int previous) {
		return RouletteSpin.build(result, seed, previous, launchTick, spinStart, spinTicks, rotorModel());
	}

	public static int phaseOf(String id) {
		return switch (id) {
			case "no_more_bets" -> NO_MORE_BETS;
			case "spin" -> SPIN;
			case "result" -> RESULT;
			default -> BETTING;
		};
	}
}
