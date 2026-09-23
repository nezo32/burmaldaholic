/**
 * Exact per-line statistics by enumerating every (a, b, c) of the machine's symbols. PURE.
 * Cells are independent, so every payline has the same distribution and per-line RTP is the
 * machine RTP (§8.1). The progressive jackpot adds exactly its contribution rate in the long
 * run (the pool pays out what the spins put in), so total RTP = base + contribution.
 */
import { type LineKind, type SlotTable, type Sym, SYMBOLS, evaluateLine } from './engine';

export interface LineStats {
  /** expected line return / line bet, jackpot excluded */
  baseRtp: number;
  /** P(line pays > 0) — "hit frequency" */
  hitRate: number;
  /** P(line pays nothing) — zero-pay specials (Creeper, TNT, progressive Star) count as no win */
  pNoWin: number;
  /** P(three stars) — the progressive jackpot chance per line */
  pJackpot: number;
  /** probability per outcome label: `berry1`, `berry2`, `three:<sym>`, `wild`, `special:<sym>` */
  outcomes: Map<string, number>;
  /** E[(line multiplier)²] (for Monte-Carlo tolerances) */
  secondMoment: number;
}

export const outcomeLabel = (kind: LineKind, symbol: Sym): string => (kind === 'berry1' || kind === 'berry2' || kind === 'wild' ? kind : `${kind}:${symbol}`);

export function lineStats(table: SlotTable): LineStats {
  const total = SYMBOLS.reduce((s, x) => s + table.weights[x], 0);
  const outcomes = new Map<string, number>();
  let baseRtp = 0;
  let hit = 0;
  let second = 0;
  if (total <= 0) return { baseRtp: 0, hitRate: 0, pNoWin: 1, pJackpot: 0, outcomes, secondMoment: 0 };
  const syms = SYMBOLS.filter((s) => table.weights[s] > 0);
  for (const a of syms)
    for (const b of syms)
      for (const c of syms) {
        const p = (table.weights[a] * table.weights[b] * table.weights[c]) / (total * total * total);
        const r = evaluateLine(a, b, c, table);
        if (!r) continue;
        if (r.multiplier > 0) hit += p;
        baseRtp += p * r.multiplier;
        second += p * r.multiplier * r.multiplier;
        const k = outcomeLabel(r.kind, r.symbol);
        outcomes.set(k, (outcomes.get(k) ?? 0) + p);
      }
  const pJackpot = table.progressive ? (outcomes.get('special:star') ?? 0) : 0;
  return { baseRtp, hitRate: hit, pNoWin: 1 - hit, pJackpot, outcomes, secondMoment: second };
}

/** Total RTP including the jackpot contribution (progressive machines only). */
export function totalRtp(table: SlotTable, contribution: number): number {
  return lineStats(table).baseRtp + (table.progressive ? Math.max(0, contribution) : 0);
}
