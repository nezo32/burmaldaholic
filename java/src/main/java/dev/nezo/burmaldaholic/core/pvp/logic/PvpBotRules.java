package dev.nezo.burmaldaholic.core.pvp.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;

/**
 * Engine-side bot timing and the fallback decisions of PvP bots (BOTS.md §4.8, §7.3, PVP.md §3.15.4).
 * Pure; every draw uses the BOT rng. Modes normally answer decisions with {@link PvpMode#botDecide};
 * {@link #fallback} is used when a mode has none. Nothing here touches the tape or scoring.
 */
public final class PvpBotRules {
	private PvpBotRules() {}

	/** Ticks until a bot presses Spin! / Drop! / Scratch! (EASY 30–40, NORMAL 15–30, HARD 10–15; ≤ half the step). */
	public static int pressTicks(BotRng rng, BotDifficulty level, BotSpeed speed, int stepTicks) {
		int t = switch (level) {
			case EASY -> rng.between(30, 40);
			case HARD -> rng.between(10, 15);
			default -> rng.between(15, 30);
		};
		return scale(t, speed, stepTicks);
	}

	/** Duel acceptance delay (20–60 t). */
	public static int acceptTicks(BotRng rng, BotSpeed speed) {
		return scale(rng.between(20, 60), speed, 0);
	}

	/** Double-or-nothing / let-it-ride / rematch think (20–60 t, never more than half the human timer). */
	public static int decisionTicks(BotRng rng, BotSpeed speed, int humanTimer) {
		return scale(rng.between(20, 60), speed, humanTimer);
	}

	private static int scale(int t, BotSpeed speed, int limit) {
		if (speed == BotSpeed.INSTANT) {
			return 0;
		}
		if (speed == BotSpeed.FAST) {
			t = t / 2;
		}
		if (limit > 0) {
			t = Math.min(t, limit / 2);
		}
		return Math.max(0, t);
	}

	/** Chance that a bot presses Rematch (80 / 50 / 30 %, HARD +20 % after a loss). */
	public static double rematchChance(BotDifficulty level, boolean lost) {
		return switch (level) {
			case EASY -> 0.8;
			case HARD -> lost ? 0.5 : 0.3;
			default -> 0.5;
		};
	}

	/** BOTS.md §4.8 defaults for the decisions of {@link DecisionView}. */
	public static long fallback(DecisionView v, BotDifficulty level, BotRng rng) {
		return switch (v.decision()) {
			case "coin.side" -> rng.nextInt(2);
			case "coin.don_offer", "coin.let_it_ride" -> switch (level) {
				case EASY -> 1;
				case HARD -> 0;
				default -> rng.chance(0.5) ? 1 : 0;
			};
			case "wheel.stake" -> {
				long cap = Math.max(v.minStake(), v.cap());
				long s = switch (level) {
					case EASY -> v.minStake();
					case HARD -> cap;
					default -> v.medianHumanStake() > 0 ? v.medianHumanStake() : v.minStake();
				};
				yield Math.max(v.minStake(), Math.min(cap, s));
			}
			case "wheel.top_up" -> {
				long room = v.cap() - v.currentStake();
				if (level == BotDifficulty.EASY && room >= v.minStake() && rng.chance(0.3)) {
					yield v.minStake();
				}
				yield 0;
			}
			default -> 0;
		};
	}
}
