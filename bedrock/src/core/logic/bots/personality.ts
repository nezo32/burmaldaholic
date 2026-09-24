/** Bot personalities (BOTS.md §4.3, §4.7). PURE. Never crosses levels (test BOTS.md §12.2). */
import type { Rng } from '../rng';
import type { BotLevel, Personality } from './types';

export interface PersonalityDef {
  /** appearance weight at EASY / NORMAL / HARD */
  readonly weights: readonly [number, number, number];
  /** Δ Chen open threshold (LAG −1.5: round toward the looser integer at use) */
  readonly openDelta: number;
  /** raise : call aggression factor */
  readonly aggression: number;
  readonly bluffFactor: number;
  /** × calling range width */
  readonly callWider: number;
}

export const PERSONALITY_DEFS: Readonly<Record<Personality, PersonalityDef>> = {
  ROCK: { weights: [25, 30, 0], openDelta: 2, aggression: 0.5, bluffFactor: 0.3, callWider: 1 },
  STATION: { weights: [35, 0, 0], openDelta: -3, aggression: 0.5, bluffFactor: 0.2, callWider: 1.5 },
  MANIAC: { weights: [40, 0, 0], openDelta: -3, aggression: 2, bluffFactor: 3, callWider: 1 },
  TAG: { weights: [0, 70, 60], openDelta: 0, aggression: 1, bluffFactor: 1, callWider: 1 },
  LAG: { weights: [0, 0, 40], openDelta: -1.5, aggression: 1.4, bluffFactor: 1.5, callWider: 1 },
};

const LEVEL_INDEX: Record<BotLevel, 0 | 1 | 2> = { EASY: 0, NORMAL: 1, HARD: 2 };

/** Draw a personality for a level from the BOT rng (`enabled` = `bots.personalities`; off → TAG). */
export function pickPersonality(rng: Rng, level: BotLevel, enabled = true): Personality {
  if (!enabled) return 'TAG';
  const entries = (Object.keys(PERSONALITY_DEFS) as Personality[]).map((p) => [p, PERSONALITY_DEFS[p].weights[LEVEL_INDEX[level]]] as const);
  const total = entries.reduce((s, [, w]) => s + w, 0);
  if (total <= 0) return 'TAG';
  let r = rng.next() * total;
  for (const [p, w] of entries) {
    r -= w;
    if (r < 0) return p;
  }
  return 'TAG';
}

/** Betting style name at an atmosphere game (BOTS.md §4.7). */
export const betStyleKey = (game: 'roulette' | 'craps' | 'baccarat', p: Personality): string => `gui.burmaldaholic.bots.betstyle.${game}.${p.toLowerCase()}`;
