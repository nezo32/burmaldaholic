package dev.nezo.burmaldaholic.games.extras.pvp.coin;

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
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.List;
import java.util.Map;

/**
 * Coin Flip Duel — PVP.md §4 (mode id {@code coin}), task J-M1. Pure: no Minecraft types.
 *
 * <p>Two participants: index 0 = the challenger (or, in a Double-or-nothing link, the chain loser who called
 * the side), index 1 = the other player. {@link Params#challengerHeads} is participant 0's side; participant 1
 * has the other. One flip per match: {@code heads = rng.nextBoolean()}; no ties; winner takes
 * {@code W = 2S − rake}. Chains (Double or nothing / let it ride) are separate linked matches — arithmetic,
 * offer limits and bot choices in {@link CoinChain}.
 *
 * <p>Timeline (the engine appends nothing for two players except the winner title): {@code countdown}
 * ({@code pvp.countdownTicks}; "3… 2… 1…"), {@code spin} (20 t; coin launch, action-bar spin), {@code land}
 * (40 t; reveals the face and the winner). Step data: countdown {@code {heads0}} (participant 0's side),
 * spin {}, land {@code {heads, winner}}.
 */
public final class CoinDuelMode implements PvpMode<CoinDuelMode.Params, CoinDuelMode.Tape> {
	public static final String ID = "coin";
	public static final int SPIN_TICKS = 20;
	public static final int LAND_TICKS = 40;

	/** Set-up: stake per player and participant 0's side. */
	public record Params(long stake, boolean challengerHeads) {}

	/** Everything random, drawn at START: seat order (odd chips; unused with no ties) and the face. */
	public record Tape(int[] seatOrder, boolean heads) {}

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
		return 2;
	}

	@Override
	public AnchorKind anchor() {
		return AnchorKind.NONE;
	}

	@Override
	public boolean equalStakes() {
		return true;
	}

	/**
	 * Flip to true when the core engine (task J-P1) is merged: until then {@code PvpService} is a skeleton that
	 * refuses every call, so the mode must not be offered.
	 */
	public static final boolean ENGINE_READY = false;

	@Override
	public boolean enabled() {
		return ENGINE_READY && CasinoConfig.pvp().enabled && CasinoConfig.pvp().coin.enabled;
	}

	@Override
	public String validate(Params params) {
		return validate(params, CasinoConfig.pvp().minStake);
	}

	/** Range check with an explicit {@code pvp.minStake} (tests). Tier max / balance are eligibility (§3.2). */
	public static String validate(Params params, long minStake) {
		if (params == null || params.stake() < minStake) {
			return "gui.burmaldaholic.pvp.error.stake_min";
		}
		return null;
	}

	@Override
	public Params defaults() {
		return new Params(Math.max(1, CasinoConfig.pvp().minStake), true);
	}

	@Override
	public Tape draw(PvpRng rng, int players, Params params) {
		if (players != 2) {
			throw new IllegalArgumentException("Coin Flip Duel needs exactly 2 players");
		}
		boolean heads = rng.nextBoolean();
		return new Tape(rng.permutation(2), heads);
	}

	/** Participant index of the winner: 0 if the coin shows participant 0's side. */
	public static int winner(Tape tape, Params params) {
		return tape.heads() == params.challengerHeads() ? 0 : 1;
	}

	@Override
	public Outcome score(Tape tape, long[] stakes, Params params) {
		int w = winner(tape, params);
		int l = 1 - w;
		long[] points = new long[2];
		points[w] = 1;
		PvpEvent landed = new PvpEvent("landed", w, 0, Map.of("heads", tape.heads() ? 1L : 0L));
		return new Outcome(points, new int[] {w, l}, new int[] {w}, tape.seatOrder().clone(), List.of(landed));
	}

	@Override
	public List<Step> timeline(Tape tape, Outcome outcome, Params params) {
		return timeline(tape, outcome, params, CasinoConfig.pvp().countdownTicks);
	}

	public static List<Step> timeline(Tape tape, Outcome outcome, Params params, int countdownTicks) {
		JsonObject countdown = new JsonObject();
		countdown.addProperty("heads0", params.challengerHeads());
		JsonObject land = new JsonObject();
		land.addProperty("heads", tape.heads());
		land.addProperty("winner", outcome.winners()[0]);
		return List.of(
			new Step("countdown", Math.max(0, countdownTicks), -1, false, countdown),
			new Step("spin", SPIN_TICKS, 0, false, new JsonObject()),
			new Step("land", LAND_TICKS, 0, false, land));
	}

	@Override
	public boolean hasDecisions() {
		return true;
	}

	/** {@link CoinChain#SIDE}, {@link CoinChain#DON_OFFER}, {@link CoinChain#LET_IT_RIDE}; bot rng only. */
	@Override
	public long botDecide(DecisionView view, BotDifficulty level, BotRng rng) {
		return switch (view.decision()) {
			case CoinChain.SIDE -> CoinChain.botSide(rng);
			case CoinChain.DON_OFFER -> CoinChain.botDoubleOrNothing(level, rng);
			case CoinChain.LET_IT_RIDE -> CoinChain.botLetItRide(level, rng);
			default -> throw new IllegalArgumentException("unknown coin decision " + view.decision());
		};
	}

	// ---- JSON ----------------------------------------------------------------------------------------

	@Override
	public JsonElement encodeParams(Params params) {
		JsonObject o = new JsonObject();
		o.addProperty("stake", params.stake());
		o.addProperty("heads", params.challengerHeads());
		return o;
	}

	@Override
	public Params decodeParams(JsonElement json) {
		JsonObject o = json.getAsJsonObject();
		return new Params(o.get("stake").getAsLong(), !o.has("heads") || o.get("heads").getAsBoolean());
	}

	@Override
	public JsonElement encodeTape(Tape tape) {
		JsonObject o = new JsonObject();
		JsonArray seat = new JsonArray();
		for (int i : tape.seatOrder()) {
			seat.add(i);
		}
		o.add("seat", seat);
		o.addProperty("heads", tape.heads());
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
		return new Tape(order, o.get("heads").getAsBoolean());
	}
}
