package dev.nezo.burmaldaholic.client.dealer;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;

/**
 * Dealer NPC gestures (animation/cards.md §1.4 table, task J-C11): pure pose curves of the kit's {@link DealerGesture} ids shared by the three dealer
 * renderers (blackjack, baccarat, Ultimate Texas Hold'em). Lives in the MAIN source set (no Minecraft imports) so it
 * is unit-tested; the client {@code DealerModel} applies a {@link Pose} to the humanoid model.
 *
 * <p>Angles are radians in the vanilla humanoid convention (xRot negative = arm forward). The rest pose has the
 * forearms "on the table" (xRot −0.35). Reduce motion halves every arm amplitude (§0.6); the breathing sway never
 * exceeds ±0.03 rad.
 */
public final class DealerMotion {
	public static final float REST_X = -0.35f;

	/** Arm and head rotations (radians). */
	public record Pose(float rightX, float rightY, float rightZ, float leftX, float leftY, float leftZ, float headX) {
		public static final Pose REST = new Pose(REST_X, 0, 0, REST_X, 0, 0, 0);
	}

	private DealerMotion() {}

	/**
	 * The pose {@code ageMs} after {@code g} started. {@code towardSeat} (−1 … 1) turns the dealing / paying arm toward
	 * the seat (left … right). {@code idleMs} drives the breathing sway (any monotonic clock).
	 */
	public static Pose pose(DealerGesture g, double ageMs, float towardSeat, boolean reduceMotion, double idleMs) {
		float amp = reduceMotion ? 0.5f : 1f;
		float sway = (float) (0.03 * Math.sin(idleMs / 4000.0 * 2 * Math.PI));
		if (g == DealerGesture.NONE || ageMs < 0 || ageMs >= g.ms) {
			return new Pose(REST_X + sway, 0, 0, REST_X - sway, 0, 0, 0);
		}
		double u = ageMs / g.ms;
		float seat = Math.max(-1f, Math.min(1f, towardSeat));
		return switch (g) {
			case DEAL -> {
				// out (outCubic) then back (inQuad)
				double k = u < 0.5 ? Ease.OUT_CUBIC.apply(u * 2) : 1 - inQuad((u - 0.5) * 2);
				yield new Pose(lerp(REST_X, REST_X + (-1.1f - REST_X) * amp, k), (float) (0.35 * seat * amp * k), 0, REST_X, 0, 0, 0);
			}
			case FLIP -> {
				double k = Math.sin(Math.PI * u);
				float x = lerp(REST_X, REST_X + (-0.9f - REST_X) * amp, k);
				float wrist = (float) ((-0.25 + 0.5 * Ease.IN_OUT_SINE.apply(u)) * amp);
				yield new Pose(x, 0, wrist, x, 0, 0, 0);
			}
			case PEEK -> {
				// lean in (200 ms), hold (200 ms), back (200 ms)
				double k = u < 1 / 3.0 ? Ease.OUT_QUAD.apply(u * 3) : u < 2 / 3.0 ? 1 : 1 - inQuad((u - 2 / 3.0) * 3);
				yield new Pose(lerp(REST_X, REST_X + (-1.0f - REST_X) * amp, k), 0, 0, REST_X, 0, 0, (float) (0.45 * amp * k));
			}
			case PAY -> {
				double k = Math.sin(Math.PI * u);
				yield new Pose(lerp(REST_X, REST_X + (-0.9f - REST_X) * amp, k), (float) (0.5 * seat * amp * k), 0, REST_X, 0, 0, 0);
			}
			case SWEEP -> {
				double k = Math.sin(Math.PI * u);
				float yaw = (float) ((0.6 - 1.2 * Ease.IN_OUT_SINE.apply(u)) * amp);
				yield new Pose(lerp(REST_X, REST_X + (-0.8f - REST_X) * amp, k), yaw, 0, REST_X, 0, 0, 0);
			}
			case SHUFFLE -> {
				double w = Math.sin(ageMs / 1000.0 * 5 * 2 * Math.PI);
				double env = Math.min(1, Math.min(u, 1 - u) * 8);
				float a = (float) (-0.75 + 0.15 * w * env);
				float b = (float) (-0.75 - 0.15 * w * env);
				float in = (float) Math.min(1, Math.min(u, 1 - u) * 8);
				yield new Pose(lerp(REST_X, REST_X + (a - REST_X) * amp, in), 0, 0, lerp(REST_X, REST_X + (b - REST_X) * amp, in), 0, 0,
					(float) (0.2 * amp * in));
			}
			case WAVE_OFF -> new Pose(REST_X, 0, 0, REST_X, 0, (float) (-0.4 * amp * (1 - Ease.OUT_QUAD.apply(u))), 0);
			default -> Pose.REST;
		};
	}

	private static double inQuad(double t) {
		double x = Math.max(0, Math.min(1, t));
		return x * x;
	}

	private static float lerp(float a, float b, double k) {
		return (float) (a + (b - a) * k);
	}
}
