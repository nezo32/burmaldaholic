package dev.nezo.burmaldaholic.games.extras.pvp.wheel;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.List;

/**
 * SKELETON — PVP.md §6 (mode id {@code wheel}). Owner: dev B (coin + wheel), task J-M2 (docs/architecture/pvp-bots.md §7).
 * Pure: no Minecraft types. Test vectors: PVP.md §16. {@link #enabled()} stays false until implemented, so the
 * engine and the hub never offer the mode.
 */
public final class WheelPartyMode implements PvpMode<WheelPartyMode.Params, WheelPartyMode.Tape> {
	/** Host set-up (validate ranges in {@link #validate}). */
	public record Params(long cap) {}

	/** Everything random, drawn at START (seat order included). */
	public record Tape(int[] seatOrder, long u) {}

	@Override
	public String id() {
		return "wheel";
	}

	@Override
	public int minPlayers() {
		return 2;
	}

	@Override
	public int maxPlayers() {
		return CasinoConfig.pvp().wheel.maxPlayers;
	}

	@Override
	public AnchorKind anchor() {
		return AnchorKind.WHEEL_OF_FORTUNE;
	}

	@Override
	public boolean equalStakes() {
		return false;
	}

	@Override
	public boolean enabled() {
		return false; // TODO(J-M2): CasinoConfig.pvp().enabled && CasinoConfig.pvp().wheel.enabled
	}

	@Override
	public String validate(Params params) {
		return null; // TODO(J-M2)
	}

	@Override
	public Params defaults() {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}

	@Override
	public Tape draw(PvpRng rng, int players, Params params) {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}

	@Override
	public Outcome score(Tape tape, long[] stakes, Params params) {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}

	@Override
	public List<Step> timeline(Tape tape, Outcome outcome, Params params) {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}

	@Override
	public boolean hasDecisions() {
		return true;
	}

	@Override
	public long botDecide(DecisionView view, BotDifficulty level, BotRng rng) {
		throw new UnsupportedOperationException("TODO(J-M2) BOTS.md §4.8 / PVP.md §3.15.4");
	}

	@Override
	public JsonElement encodeParams(Params params) {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}

	@Override
	public Params decodeParams(JsonElement json) {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}

	@Override
	public JsonElement encodeTape(Tape tape) {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}

	@Override
	public Tape decodeTape(JsonElement json) {
		throw new UnsupportedOperationException("TODO(J-M2)");
	}
}
