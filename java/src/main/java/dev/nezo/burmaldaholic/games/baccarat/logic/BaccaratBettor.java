package dev.nezo.burmaldaholic.games.baccarat.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.EnumMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Punto Banco ATMOSPHERE bots (BOTS.md §4.7, §5.2). PURE; same styles and numbers as Bedrock
 * {@code BACCARAT_BETTOR}. The bet is chosen by the bot's personality (its betting style); the chips are
 * VIRTUAL — shown on screens and in chat only, never escrowed, settled, reserved or counted anywhere.
 *
 * <ul>
 *   <li>ROCK <i>Banker Only</i>, TAG <i>Banker, no frills</i>;</li>
 *   <li>STATION <i>Trend Follower</i>: the last Player/Banker winner;</li>
 *   <li>LAG <i>Chop Chaser</i>: against the last winner;</li>
 *   <li>MANIAC <i>Tie Hunter</i>: a random side + Tie every coup, both pairs every third coup.</li>
 * </ul>
 * Amounts: {@code box min + k × Banker step}, k = 1–2 (ROCK, STATION), 2–5 (TAG), 5–10 (MANIAC, LAG), then
 * fitted to the table limits.
 */
public final class BaccaratBettor implements BotPolicy<BaccaratBettor.View, EnumMap<BetKind, Long>> {
	public static final BaccaratBettor INSTANCE = new BaccaratBettor();

	/** Pairs every this many coups for the Tie Hunter. */
	public static final int TIE_HUNTER_PAIRS_EVERY = 3;
	/** Atmosphere bets land 60–160 t into BETTING (BOTS.md §7.3), × the speed factor. */
	public static final int DELAY_MIN = 60;
	public static final int DELAY_MAX = 160;

	/**
	 * What a bettor sees (public).
	 *
	 * @param lastSide last Player / Banker winner at this table (ties skipped), null when none
	 * @param coupNo   number of the coup about to be dealt
	 */
	public record View(Slips.Limits limits, @Nullable Side lastSide, long coupNo) {}

	private BaccaratBettor() {}

	/** k range {@code [lo, hi]} by personality. */
	public static int[] kRange(Personality p) {
		return switch (p) {
			case ROCK, STATION -> new int[] {1, 2};
			case TAG -> new int[] {2, 5};
			case MANIAC, LAG -> new int[] {5, 10};
		};
	}

	/** Smallest legal bet on a box (Banker: the step-aligned minimum). */
	public static long boxMin(BetKind box, Slips.Limits l) {
		return box == BetKind.BANKER ? l.bankerMin() : l.minBet();
	}

	/** Virtual amount {@code min + k × step} for a box (step = the table's Banker unit). */
	public static long virtualAmount(BetKind box, int k, Slips.Limits l) {
		return boxMin(box, l) + Math.max(0, k) * boxMin(BetKind.BANKER, l);
	}

	/** The last side that won (ties skipped); bead codes as the table stores them (winner ordinal | pair flags). */
	public static @Nullable Side lastSide(List<Integer> beads) {
		for (int i = beads.size() - 1; i >= 0; i--) {
			Side s = Side.byOrdinal(beads.get(i) & 3);
			if (s != Side.TIE) {
				return s;
			}
		}
		return null;
	}

	@Override
	public EnumMap<BetKind, Long> decide(BotProfile bot, View v, Object work, BotRng rng) {
		int[] r = kRange(bot.personality());
		int k = rng.between(r[0], r[1]);
		Slips.Limits l = v.limits();
		EnumMap<BetKind, Long> slip = new EnumMap<>(BetKind.class);
		switch (bot.personality()) {
			case ROCK, TAG -> slip.put(BetKind.BANKER, virtualAmount(BetKind.BANKER, k, l));
			case STATION -> {
				BetKind side = v.lastSide() == Side.PLAYER ? BetKind.PLAYER : BetKind.BANKER;
				slip.put(side, virtualAmount(side, k, l));
			}
			case LAG -> {
				BetKind side = v.lastSide() == Side.BANKER ? BetKind.PLAYER : v.lastSide() == Side.PLAYER ? BetKind.BANKER : BetKind.PLAYER;
				slip.put(side, virtualAmount(side, k, l));
			}
			case MANIAC -> {
				BetKind side = rng.chance(0.5) ? BetKind.PLAYER : BetKind.BANKER;
				slip.put(side, virtualAmount(side, k, l));
				slip.put(BetKind.TIE, virtualAmount(BetKind.TIE, 0, l));
				if (l.pairs() && v.coupNo() % TIE_HUNTER_PAIRS_EVERY == 0) {
					slip.put(BetKind.PLAYER_PAIR, virtualAmount(BetKind.PLAYER_PAIR, 0, l));
					slip.put(BetKind.BANKER_PAIR, virtualAmount(BetKind.BANKER_PAIR, 0, l));
				}
			}
		}
		return slip;
	}

	/** Fits the slip to the table limits (Banker snapped to the step, side max, total max). */
	@Override
	public EnumMap<BetKind, Long> legalize(View v, EnumMap<BetKind, Long> slip) {
		return slip == null ? new EnumMap<>(BetKind.class) : v.limits().fit(slip);
	}
}
