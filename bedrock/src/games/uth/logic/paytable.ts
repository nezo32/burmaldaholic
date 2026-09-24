/**
 * Ultimate Texas Hold'em paytables (GAME_DESIGN §21.1, CONFIG.md `uth.blindPays` / `uth.tripsPays`).
 * PURE. Hands are classified with the shared poker evaluator (§7.2) - never a copy of it.
 */
import { CAT, type HandName, categoryOf, handName } from '../../poker/logic/evaluator';

/** Paytable rows, best first (CONFIG.md map keys). */
export const PAY_HANDS = ['royal', 'straightFlush', 'quads', 'fullHouse', 'flush', 'straight', 'trips'] as const;
export type PayHand = (typeof PAY_HANDS)[number];
export type Paytable = Partial<Record<PayHand, number>>;

/** Blind pays X:1 on a WIN (floored); hands not listed push. */
export const DEFAULT_BLIND_PAYS: Readonly<Paytable> = { royal: 500, straightFlush: 50, quads: 10, fullHouse: 3, flush: 1.5, straight: 1 };
/** Trips pays X:1 on the seat's own hand, win, lose or fold; lower hands lose. 50-40-30-8-6-5-3. */
export const DEFAULT_TRIPS_PAYS: Readonly<Paytable> = { royal: 50, straightFlush: 40, quads: 30, fullHouse: 8, flush: 6, straight: 5, trips: 3 };
/** The 9-7-4 variant (edge 0.90 %, §21.1 design decision). */
export const VARIANT_TRIPS_PAYS: Readonly<Paytable> = { royal: 50, straightFlush: 40, quads: 30, fullHouse: 9, flush: 7, straight: 4, trips: 3 };

/** Paytable row of an evaluated 7-card value (undefined below three of a kind). */
export function payHandOf(value: number): PayHand | undefined {
  switch (categoryOf(value)) {
    case CAT.straight_flush:
      return handName(value) === 'royal_flush' ? 'royal' : 'straightFlush';
    case CAT.four_of_a_kind:
      return 'quads';
    case CAT.full_house:
      return 'fullHouse';
    case CAT.flush:
      return 'flush';
    case CAT.straight:
      return 'straight';
    case CAT.three_of_a_kind:
      return 'trips';
    default:
      return undefined;
  }
}

/** Poker hand-name key suffix of a paytable row (`gui.burmaldaholic.poker.hand.<name>`). */
export const PAY_HAND_NAME: Readonly<Record<PayHand, HandName>> = {
  royal: 'royal_flush',
  straightFlush: 'straight_flush',
  quads: 'four_of_a_kind',
  fullHouse: 'full_house',
  flush: 'flush',
  straight: 'straight',
  trips: 'three_of_a_kind',
};

/**
 * Sanitize a configured paytable: finite numbers in [0, 1000] only; unknown rows dropped.
 * `integers` floors every value (Trips pays whole X:1).
 */
export function normalizePaytable(raw: unknown, fallback: Readonly<Paytable>, integers = false): Paytable {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { ...fallback };
  const out: Paytable = {};
  for (const h of PAY_HANDS) {
    const v = (raw as Record<string, unknown>)[h];
    if (typeof v !== 'number' || !Number.isFinite(v)) continue;
    const c = Math.min(1000, Math.max(0, v));
    out[h] = integers ? Math.floor(c) : c;
  }
  return out;
}

/** Largest multiplier of a paytable (0 when empty) - reservation / bank coverage. */
export const maxPay = (p: Readonly<Paytable>): number => Math.max(0, ...PAY_HANDS.map((h) => p[h] ?? 0));

/** Blind multiplier for a winning hand (0 = push). */
export const blindMultiplier = (p: Readonly<Paytable>, value: number): number => {
  const h = payHandOf(value);
  return h ? (p[h] ?? 0) : 0;
};

/** Trips multiplier for a hand (undefined = the Trips bet loses). */
export const tripsMultiplier = (p: Readonly<Paytable>, value: number): number | undefined => {
  const h = payHandOf(value);
  const m = h ? p[h] : undefined;
  return m === undefined || m <= 0 ? undefined : m;
};
