/**
 * Blackjack table rules (GAME_DESIGN §6.1, CONFIG.md `blackjack.*`). PURE.
 */
export interface BlackjackRules {
  /** decks in the shoe (1–8) */
  decks: number;
  /** reshuffle before the next round once this fraction of the shoe has been dealt */
  penetration: number;
  /** H17 when true, S17 (default) when false */
  dealerHitsSoft17: boolean;
  /** blackjack profit multiplier: 1.5 = 3:2, 1.2 = 6:5 (profit floored) */
  blackjackPayout: number;
  doubleAfterSplit: boolean;
  /** max hands after splits (2–4) */
  maxHands: number;
  resplitAces: boolean;
  /** insurance / even money offered when the dealer shows an ace */
  insurance: boolean;
  /** late surrender (first two cards of an unsplit hand, after the peek) */
  lateSurrender: boolean;
}

export const DEFAULT_RULES: BlackjackRules = {
  decks: 6,
  penetration: 0.75,
  dealerHitsSoft17: false,
  blackjackPayout: 1.5,
  doubleAfterSplit: true,
  maxHands: 4,
  resplitAces: false,
  insurance: true,
  lateSurrender: false,
};

/** Clamp untrusted config values into the CONFIG.md ranges. */
export function normalizeRules(r: Partial<BlackjackRules>): BlackjackRules {
  const m = { ...DEFAULT_RULES, ...r };
  const clamp = (v: number, lo: number, hi: number, d: number) => (Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : d);
  return {
    ...m,
    decks: Math.trunc(clamp(m.decks, 1, 8, DEFAULT_RULES.decks)),
    penetration: clamp(m.penetration, 0.25, 0.9, DEFAULT_RULES.penetration),
    blackjackPayout: clamp(m.blackjackPayout, 1, 2, DEFAULT_RULES.blackjackPayout),
    maxHands: Math.trunc(clamp(m.maxHands, 2, 4, DEFAULT_RULES.maxHands)),
  };
}
