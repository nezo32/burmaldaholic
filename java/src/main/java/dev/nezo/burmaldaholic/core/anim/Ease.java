package dev.nezo.burmaldaholic.core.anim;

/**
 * The shared easing vocabulary (global.md §2.5, tables.md §0.2, extras-pvp.md §0.2). PURE: used by the
 * server (timeline lengths), the client (GUI, BER) and JUnit. The Bedrock port is
 * {@code bedrock/src/core/logic/anim/ease.ts}; both are checked against
 * {@code src/test/resources/fx/vectors/core_anim.json} (docs/architecture/animation.md §3.4).
 *
 * <p>Every curve clamps {@code t} to [0, 1], returns exactly 0 at 0 and 1 at 1 (except {@link #SHAKE},
 * which returns to 0). Numbers (roll-ups, counters) must never use {@link #OUT_BACK}, {@link #OUT_ELASTIC}
 * or {@link #OUT_BOUNCE}: a count-up is monotonic.
 */
public enum Ease {
	LINEAR("linear"),
	OUT_QUAD("outQuad"),
	OUT_CUBIC("outCubic"),
	IN_CUBIC("inCubic"),
	IN_OUT_QUAD("inOutQuad"),
	IN_OUT_CUBIC("inOutCubic"),
	IN_SINE("inSine"),
	OUT_SINE("outSine"),
	IN_OUT_SINE("inOutSine"),
	OUT_QUINT("outQuint"),
	OUT_BACK("outBack"),
	OUT_ELASTIC("outElastic"),
	OUT_BOUNCE("outBounce"),
	SHAKE("shake");

	private final String id;

	Ease(String id) {
		this.id = id;
	}

	/** Cross-edition name (the same string in Bedrock and in the timeline JSON). */
	public String id() {
		return id;
	}

	public static Ease byId(String id) {
		for (Ease e : values()) if (e.id.equals(id)) return e;
		throw new IllegalArgumentException("unknown easing " + id);
	}

	public double apply(double t) {
		double x = t <= 0 ? 0 : t >= 1 ? 1 : t;
		return switch (this) {
			case LINEAR -> x;
			case OUT_QUAD -> {
				double u = 1 - x;
				yield 1 - u * u;
			}
			case OUT_CUBIC -> {
				double u = 1 - x;
				yield 1 - u * u * u;
			}
			case IN_CUBIC -> x * x * x;
			case IN_OUT_QUAD -> {
				if (x < 0.5) yield 2 * x * x;
				double u = -2 * x + 2;
				yield 1 - u * u / 2;
			}
			case IN_OUT_CUBIC -> {
				if (x < 0.5) yield 4 * x * x * x;
				double u = -2 * x + 2;
				yield 1 - u * u * u / 2;
			}
			case IN_SINE -> x == 1 ? 1 : 1 - Math.cos(x * Math.PI / 2);
			case OUT_SINE -> x == 1 ? 1 : Math.sin(x * Math.PI / 2);
			case IN_OUT_SINE -> x == 1 ? 1 : -(Math.cos(Math.PI * x) - 1) / 2;
			case OUT_QUINT -> {
				double u = 1 - x;
				yield 1 - u * u * u * u * u;
			}
			case OUT_BACK -> outBack(1.70158, x);
			case OUT_ELASTIC -> {
				if (x == 0 || x == 1) yield x;
				yield Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
			}
			case OUT_BOUNCE -> outBounce(x);
			case SHAKE -> Math.sin(6 * Math.PI * x) * (1 - x);
		};
	}

	/** {@code outBack} with a custom overshoot {@code s} (slots.md §2.3 uses 1.2 for the reel landing). */
	public static double outBack(double s, double t) {
		double x = t <= 0 ? 0 : t >= 1 ? 1 : t;
		if (x == 1) return 1;
		double u = x - 1;
		return 1 + (s + 1) * u * u * u + s * u * u;
	}

	/** Normalised exponential decay (tables.md §0.2, ball orbit k = 3.2). */
	public static double expDecay(double k, double t) {
		double x = t <= 0 ? 0 : t >= 1 ? 1 : t;
		if (x == 1) return 1;
		return (1 - Math.exp(-k * x)) / (1 - Math.exp(-k));
	}

	private static double outBounce(double x) {
		final double n1 = 7.5625;
		final double d1 = 2.75;
		if (x < 1 / d1) return n1 * x * x;
		if (x < 2 / d1) {
			double u = x - 1.5 / d1;
			return n1 * u * u + 0.75;
		}
		if (x < 2.5 / d1) {
			double u = x - 2.25 / d1;
			return n1 * u * u + 0.9375;
		}
		double u = x - 2.625 / d1;
		return n1 * u * u + 0.984375;
	}
}
