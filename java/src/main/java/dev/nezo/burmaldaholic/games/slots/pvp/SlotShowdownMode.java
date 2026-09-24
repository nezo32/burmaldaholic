package dev.nezo.burmaldaholic.games.slots.pvp;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.List;

/**
 * SKELETON — PVP.md §5 (mode id {@code slots}). Owner: dev C (slots), task J-M3 (docs/architecture/pvp-bots.md §7).
 * Pure: no Minecraft types. Test vectors: PVP.md §16. {@link #enabled()} stays false until implemented, so the
 * engine and the hub never offer the mode.
 */
public final class SlotShowdownMode implements PvpMode<SlotShowdownMode.Params, SlotShowdownMode.Tape> {
	/** Host set-up (validate ranges in {@link #validate}). */
	public record Params(String tier, int spins) {}

	/** Everything random, drawn at START (seat order included). */
	public record Tape(int[] seatOrder, int[] hot, int[][][] grids) {}

	@Override
	public String id() {
		return "slots";
	}

	@Override
	public int minPlayers() {
		return 2;
	}

	@Override
	public int maxPlayers() {
		return CasinoConfig.pvp().slots.maxPlayers;
	}

	@Override
	public AnchorKind anchor() {
		return AnchorKind.SLOT_MACHINE;
	}

	@Override
	public boolean equalStakes() {
		return true;
	}

	@Override
	public boolean enabled() {
		return false; // TODO(J-M3): CasinoConfig.pvp().enabled && CasinoConfig.pvp().slots.enabled
	}

	@Override
	public String validate(Params params) {
		return null; // TODO(J-M3)
	}

	@Override
	public Params defaults() {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}

	@Override
	public Tape draw(PvpRng rng, int players, Params params) {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}

	@Override
	public Outcome score(Tape tape, long[] stakes, Params params) {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}

	@Override
	public List<Step> timeline(Tape tape, Outcome outcome, Params params) {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}

	@Override
	public JsonElement encodeParams(Params params) {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}

	@Override
	public Params decodeParams(JsonElement json) {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}

	@Override
	public JsonElement encodeTape(Tape tape) {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}

	@Override
	public Tape decodeTape(JsonElement json) {
		throw new UnsupportedOperationException("TODO(J-M3)");
	}
}
