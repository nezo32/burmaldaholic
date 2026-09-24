/**
 * House edge per game (GAME_DESIGN §17) for the *theoretical loss* of a round:
 * theoreticalLoss = Σ stake × houseEdge. PURE.
 *
 * Used by VIP cashback (§12, changed 2026-09: cashback = rate × theoretical loss, so it can
 * never exceed the expected loss and the edge stays positive for every game and tier).
 *
 * Where a game has several bets/variants, the table holds the LOWEST edge of that game
 * (conservative: cashback never exceeds the real edge). Games that know better pass their
 * own edge (`wagers.place({ houseEdge })`, `wagers.raise(..., edge)`), e.g. slots per tier or
 * craps Odds (0 %).
 */
export const HOUSE_EDGE: Readonly<Record<string, number>> = {
  blackjack: 0.0041,
  poker: 0,
  slots: 0.0396,
  roulette: 0.027,
  craps: 0.0136,
  coin_flip: 0.02,
  wheel: 1 - 51.5 / 54,
  scratch: 0.15,
  plinko: 0.033,
  dice_duel: 0.0278,
  /** Banker bet, 5 % commission (the lowest baccarat edge). */
  baccarat: 0.0106,
  /** Element of risk: 2.19 % of the Ante over ~4.1 Antes wagered per round. */
  uth: 0.0053,
};

export const houseEdgeOf = (game: string): number => {
  const e = HOUSE_EDGE[game];
  return typeof e === 'number' && e > 0 ? e : 0;
};

/** Theoretical loss of `stake` chips at `edge` (never negative). */
export const theoreticalLoss = (stake: number, edge: number): number => (stake > 0 && edge > 0 ? stake * edge : 0);
