package dev.nezo.burmaldaholic.core.pvp.logic;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import java.util.List;

/**
 * Calls a {@link PvpMode} whose type parameters are unknown to the engine, over the persisted JSON forms
 * (params and tape). Pure. The tape is always decoded from its JSON before scoring, so a live match and a
 * match settled after a restart score exactly the same data.
 */
public final class ModeCalls {
	private ModeCalls() {}

	/** @return null = ok, else the mode's error key (also for undecodable params) */
	public static String validate(PvpMode<?, ?> mode, JsonElement params) {
		return validateT(mode, params);
	}

	private static <P, T> String validateT(PvpMode<P, T> mode, JsonElement params) {
		P p;
		try {
			p = mode.decodeParams(params);
		} catch (RuntimeException e) {
			return "gui.burmaldaholic.error.invalid_amount";
		}
		return mode.validate(p);
	}

	/** Draws the whole tape with the FAIR rng and returns its JSON form. */
	public static JsonElement draw(PvpMode<?, ?> mode, PvpRng rng, int players, JsonElement params) {
		return drawT(mode, rng, players, params);
	}

	private static <P, T> JsonElement drawT(PvpMode<P, T> mode, PvpRng rng, int players, JsonElement params) {
		return mode.encodeTape(mode.draw(rng, players, mode.decodeParams(params)));
	}

	public static Outcome score(PvpMode<?, ?> mode, JsonElement tape, long[] stakes, JsonElement params) {
		return scoreT(mode, tape, stakes, params);
	}

	private static <P, T> Outcome scoreT(PvpMode<P, T> mode, JsonElement tape, long[] stakes, JsonElement params) {
		return mode.score(mode.decodeTape(tape), stakes, mode.decodeParams(params));
	}

	public static List<Step> timeline(PvpMode<?, ?> mode, JsonElement tape, Outcome outcome, JsonElement params) {
		return timelineT(mode, tape, outcome, params);
	}

	private static <P, T> List<Step> timelineT(PvpMode<P, T> mode, JsonElement tape, Outcome outcome, JsonElement params) {
		return mode.timeline(mode.decodeTape(tape), outcome, mode.decodeParams(params));
	}

	/** {@link PvpMode#chainParams} over JSON; null when the mode keeps the default. */
	public static JsonElement chainParams(PvpMode<?, ?> mode, JsonElement params, long stake, boolean challengerHeads) {
		return chainParamsT(mode, params, stake, challengerHeads);
	}

	private static <P, T> JsonElement chainParamsT(PvpMode<P, T> mode, JsonElement params, long stake, boolean challengerHeads) {
		P next = mode.chainParams(mode.decodeParams(params), stake, challengerHeads);
		return next == null ? null : mode.encodeParams(next);
	}

	/** {@link PvpMode#stakeCap} over JSON ({@link Long#MAX_VALUE} = none or undecodable). */
	public static long stakeCap(PvpMode<?, ?> mode, JsonElement params) {
		try {
			return stakeCapT(mode, params);
		} catch (RuntimeException e) {
			return Long.MAX_VALUE;
		}
	}

	private static <P, T> long stakeCapT(PvpMode<P, T> mode, JsonElement params) {
		return mode.stakeCap(mode.decodeParams(params));
	}

	/** {@link PvpMode#botDecide}, falling back to {@link PvpBotRules#fallback} when the mode has no answer. */
	public static long botDecide(PvpMode<?, ?> mode, DecisionView view, BotDifficulty level, BotRng rng) {
		try {
			return mode.botDecide(view, level, rng);
		} catch (UnsupportedOperationException e) {
			return PvpBotRules.fallback(view, level, rng);
		}
	}
}
