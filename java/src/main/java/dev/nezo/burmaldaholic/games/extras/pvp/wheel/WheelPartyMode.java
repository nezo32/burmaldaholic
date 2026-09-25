package dev.nezo.burmaldaholic.games.extras.pvp.wheel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wheel Party — PVP.md §6 (mode id {@code wheel}), task J-M2. Pure: no Minecraft types.
 *
 * <p>Stakes differ ({@link #equalStakes()} false): participant i's slice is proportional to its escrowed stake,
 * slices in join order. At START ("No more bets") the tape draws one 53-bit value r; {@link #score} maps it onto
 * the final pot, {@code u = floor(r × P / 2⁵³)} (see {@link WheelMath#spinPoint}), and the owner of the slice
 * containing u takes {@code W = P − rake}.
 *
 * <p>Events of {@link #score}: {@code spin} (seat = winner, data {@code u}, {@code pot}), {@code by_a_hair} (seat =
 * the owner across the nearer boundary), {@code underdog} (seat = winner, data {@code shareBp}). Timeline:
 * {@code spin} (100 t; data {@code u, pot, winner, hair, angle1000}, the client eases 3 turns + the angle) and
 * {@code result} (40 t; data {@code winner, underdog, shareBp}).
 */
public final class WheelPartyMode implements PvpMode<WheelPartyMode.Params, WheelPartyMode.Tape> {
	public static final String ID = "wheel";
	public static final int SPIN_TICKS = 100;
	public static final int RESULT_TICKS = 40;
	/** BOTS_ONLY parties: the countdown is shortened so the slices can be seen before the spin (PVP.md §3.15.1). */
	public static final int BOTS_ONLY_COUNTDOWN_TICKS = 100;

	/** Host set-up: C, the max total stake per player. */
	public record Params(long cap) {}

	/**
	 * Everything random, drawn at START: the seat order (unused for payouts — a wheel has no ties) and
	 * {@code r ∈ [0, 2⁵³)}, the spin (see class doc).
	 */
	public record Tape(int[] seatOrder, long r) {}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int minPlayers() {
		return 2;
	}

	@Override
	public int maxPlayers() {
		return maxPlayersNow();
	}

	/** {@code pvp.wheel.maxPlayers}. */
	public static int maxPlayersNow() {
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

	/**
	 * Flip to true when the core engine (task J-P1) is merged: until then {@code PvpService} is a skeleton that
	 * refuses every call, so the mode must not be offered.
	 */
	public static final boolean ENGINE_READY = true;

	@Override
	public boolean enabled() {
		return ENGINE_READY && CasinoConfig.pvp().enabled && CasinoConfig.pvp().wheel.enabled;
	}

	@Override
	public String validate(Params params) {
		return validate(params, CasinoConfig.pvp().minStake);
	}

	/** The cap must allow the minimum stake. Host tier max / balance are eligibility (§3.2). */
	public static String validate(Params params, long minStake) {
		if (params == null || params.cap() < minStake) {
			return WheelMath.ERROR_STAKE_MIN;
		}
		return null;
	}

	@Override
	public Params defaults() {
		return new Params(Math.max(1, CasinoConfig.pvp().minStake) * 10L);
	}

	@Override
	public Tape draw(PvpRng rng, int players, Params params) {
		if (players < 2) {
			throw new IllegalArgumentException("Wheel Party needs at least 2 players");
		}
		// seat order first, then the spin (the same draw order as Bedrock: same fair stream → same tape)
		int[] seatOrder = rng.permutation(players);
		return new Tape(seatOrder, rng.nextLong(WheelMath.SPIN_RESOLUTION));
	}

	@Override
	public Outcome score(Tape tape, long[] stakes, Params params) {
		return score(tape, stakes, CasinoConfig.pvp().wheel.underdogShareBasisPoints);
	}

	public static Outcome score(Tape tape, long[] stakes, int underdogBasisPoints) {
		long pot = PvpMath.pot(stakes);
		return scoreAt(WheelMath.spinPoint(tape.r(), pot), stakes, tape.seatOrder(), underdogBasisPoints);
	}

	/** Scoring for a known point u (test vectors W1, W4, W5). */
	public static Outcome scoreAt(long u, long[] stakes, int[] seatOrder, int underdogBasisPoints) {
		int n = stakes.length;
		long pot = PvpMath.pot(stakes);
		int w = WheelMath.winner(stakes, u);
		// the "score" of a Wheel Party is the slice (same Outcome as Bedrock): losers by slice, ties by seat order
		long[] points = stakes.clone();
		int[] seatPos = new int[n];
		for (int k = 0; k < seatOrder.length; k++) {
			seatPos[seatOrder[k]] = k;
		}
		List<Integer> others = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (i != w) {
				others.add(i);
			}
		}
		others.sort((a, b) -> stakes[a] != stakes[b] ? Long.compare(stakes[b], stakes[a]) : Integer.compare(seatPos[a], seatPos[b]));
		int[] rank = new int[n];
		rank[0] = w;
		for (int k = 0; k < others.size(); k++) {
			rank[k + 1] = others.get(k);
		}
		List<PvpEvent> events = new ArrayList<>();
		events.add(new PvpEvent("spin", w, -1, Map.of("u", u, "pot", pot)));
		int hair = WheelMath.byAHair(stakes, u);
		if (hair >= 0) {
			events.add(PvpEvent.of("by_a_hair", hair, -1));
		}
		if (WheelMath.underdog(stakes[w], pot, underdogBasisPoints)) {
			events.add(new PvpEvent("underdog", w, -1, Map.of("shareBp", WheelMath.shareBasisPoints(stakes[w], pot))));
		}
		return new Outcome(points, rank, new int[] {w}, seatOrder.clone(), events);
	}

	@Override
	public List<Step> timeline(Tape tape, Outcome outcome, Params params) {
		PvpEvent spin = event(outcome, "spin");
		long u = spin == null ? 0 : spin.data().getOrDefault("u", 0L);
		long pot = spin == null ? 1 : Math.max(1, spin.data().getOrDefault("pot", 1L));
		int winner = outcome.winners()[0];
		PvpEvent hair = event(outcome, "by_a_hair");
		PvpEvent under = event(outcome, "underdog");

		JsonObject spinData = new JsonObject();
		spinData.addProperty("u", u);
		spinData.addProperty("pot", pot);
		spinData.addProperty("winner", winner);
		spinData.addProperty("hair", hair == null ? -1 : hair.seat());
		spinData.addProperty("angle1000", Math.round(WheelMath.pointerDegrees(u, pot) * 1000));
		JsonObject result = new JsonObject();
		result.addProperty("winner", winner);
		result.addProperty("underdog", under != null);
		result.addProperty("shareBp", under == null ? -1 : under.data().getOrDefault("shareBp", 0L));
		return List.of(
			new Step("spin", SPIN_TICKS, 0, false, spinData),
			new Step("result", RESULT_TICKS, 0, false, result));
	}

	private static PvpEvent event(Outcome outcome, String kind) {
		for (PvpEvent e : outcome.events()) {
			if (e.kind().equals(kind)) {
				return e;
			}
		}
		return null;
	}

	@Override
	public boolean hasDecisions() {
		return true;
	}

	/** {@code wheel.stake} (the stake to place) and {@code wheel.top_up} (extra chips, 0 = none); bot rng only. */
	@Override
	public long botDecide(DecisionView view, BotDifficulty level, BotRng rng) {
		return switch (view.decision()) {
			case "wheel.stake" -> WheelMath.botStake(level, view.minStake(), view.cap(), view.medianHumanStake(), rng);
			case "wheel.top_up" -> WheelMath.botTopUp(level, view.minStake(), view.cap(), view.currentStake(), rng);
			default -> throw new IllegalArgumentException("unknown wheel decision " + view.decision());
		};
	}

	// ---- JSON ----------------------------------------------------------------------------------------

	@Override
	public JsonElement encodeParams(Params params) {
		JsonObject o = new JsonObject();
		o.addProperty("cap", params.cap());
		return o;
	}

	@Override
	public Params decodeParams(JsonElement json) {
		return new Params(json.getAsJsonObject().get("cap").getAsLong());
	}

	@Override
	public JsonElement encodeTape(Tape tape) {
		JsonObject o = new JsonObject();
		JsonArray seat = new JsonArray();
		for (int i : tape.seatOrder()) {
			seat.add(i);
		}
		o.add("seat", seat);
		o.addProperty("r", tape.r());
		return o;
	}

	@Override
	public Tape decodeTape(JsonElement json) {
		JsonObject o = json.getAsJsonObject();
		JsonArray seat = o.getAsJsonArray("seat");
		int[] order = new int[seat.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = seat.get(i).getAsInt();
		}
		return new Tape(order, o.get("r").getAsLong());
	}
}
