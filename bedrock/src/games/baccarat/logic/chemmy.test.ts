import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import {
  BaccaratShoe,
  type PunterStake,
  acceptBet,
  applyBanco,
  bancoCheck,
  chemmyEdges,
  clockwise,
  coverage,
  dealCoup,
  exactEdges,
  nextCandidate,
  normalizeBankDecision,
  openCoverage,
  settleChemmy,
} from '.';

describe('decision interface', () => {
  it('bank decisions are validated against the offer (invalid → Pass)', () => {
    const offer = { keep: false, bank: 100, minBank: 20, balance: 500 };
    expect(normalizeBankDecision({ kind: 'take', amount: 200 }, offer)).toEqual({ kind: 'take', amount: 200 });
    expect(normalizeBankDecision({ kind: 'take', amount: 10 }, offer)).toEqual({ kind: 'pass' });
    expect(normalizeBankDecision({ kind: 'take', amount: 600 }, offer)).toEqual({ kind: 'pass' });
    expect(normalizeBankDecision({ kind: 'keep' }, offer)).toEqual({ kind: 'pass' });
    expect(normalizeBankDecision({ kind: 'keep' }, { ...offer, keep: true })).toEqual({ kind: 'keep' });
    expect(normalizeBankDecision({ kind: 'keep' }, { ...offer, keep: true, bank: 5 })).toEqual({ kind: 'pass' });
    expect(normalizeBankDecision(undefined, offer)).toEqual({ kind: 'pass' });
    expect(
      clockwise([
        { id: 'b', name: 'B', kind: 'human', seat: 3 },
        { id: 'a', name: 'A', kind: 'bot', seat: 1 },
      ]),
    ).toEqual(['a', 'b']);
  });
});

describe('chemin de fer (§20.9)', () => {
  it('coverage = min(bank, banker max)', () => {
    expect(coverage(1200, 1000)).toBe(1000);
    expect(coverage(300, 1000)).toBe(300);
  });

  it('bets are accepted in order while Σ ≤ C; the crossing bet is snapped to the open coverage', () => {
    const stakes: PunterStake[] = [{ id: 'a', name: 'A', amount: 650 }];
    const r = acceptBet(stakes, 1000, 'b', 500, 1, 5000, false);
    expect(r).toEqual({ ok: true, amount: 350, snapped: true });
    expect(openCoverage([...stakes, { id: 'b', name: 'B', amount: 350 }], 1000)).toBe(0);
    expect(acceptBet([...stakes, { id: 'b', name: 'B', amount: 350 }], 1000, 'c', 10, 1, 5000, false)).toEqual({ ok: false, error: { code: 'coverage', open: 0 } });
  });

  it("the punter's own max and the table min are hard limits", () => {
    expect(acceptBet([], 1000, 'a', 600, 1, 500, false)).toEqual({ ok: false, error: { code: 'total_max', max: 500 } });
    expect(acceptBet([], 1000, 'a', 4, 5, 500, false)).toEqual({ ok: false, error: { code: 'bet_too_low', min: 5 } });
    expect(acceptBet([{ id: 'x', name: 'X', amount: 998 }], 1000, 'a', 10, 5, 500, false)).toEqual({ ok: false, error: { code: 'coverage', open: 2 } });
    expect(acceptBet([], 1000, 'a', 0, 1, 500, false)).toEqual({ ok: false, error: { code: 'invalid_amount' } });
    expect(acceptBet([], 1000, 'a', 10, 1, 500, true)).toEqual({ ok: false, error: { code: 'banco_taken' } });
  });

  it('Banco: balance and max must reach C; other bets are refunded; one per coup', () => {
    expect(bancoCheck(1000, 999, 5000, false)).toEqual({ code: 'banco_funds', need: 1000 });
    expect(bancoCheck(1000, 5000, 999, false)).toEqual({ code: 'banco_funds', need: 1000 });
    expect(bancoCheck(1000, 1000, 1000, false)).toBeUndefined();
    expect(bancoCheck(1000, 1000, 1000, true)).toEqual({ code: 'banco_taken' });
    const b = applyBanco(
      [
        { id: 'a', name: 'A', amount: 100 },
        { id: 'b', name: 'B', amount: 200 },
      ],
      'b',
      'B',
      1000,
    );
    expect(b.stakes).toEqual([{ id: 'b', name: 'B', amount: 1000 }]);
    expect(b.refunds).toEqual([{ id: 'a', name: 'A', amount: 100 }]);
    expect(b.extra).toBe(800);
  });

  it('settlement: bank wins minus rake, pays 1:1, ties push', () => {
    const stakes: PunterStake[] = [
      { id: 'a', name: 'A', amount: 300 },
      { id: 'b', name: 'B', amount: 99 },
    ];
    const w = settleChemmy('banker', stakes, 0.05);
    expect(w).toMatchObject({ matched: 399, rake: 19, bankDelta: 380 });
    expect([...w.punterReturns.values()]).toEqual([0, 0]);
    const l = settleChemmy('player', stakes, 0.05);
    expect(l).toMatchObject({ rake: 0, bankDelta: -399 });
    expect(l.punterReturns.get('a')).toBe(600);
    const t = settleChemmy('tie', stakes, 0.05);
    expect(t.bankDelta).toBe(0);
    expect(t.punterReturns.get('b')).toBe(99);
    // chips are conserved: bank delta + punter nets + rake = 0
    for (const r of [w, l, t]) {
      const punterNet = stakes.reduce((a, s) => a + (r.punterReturns.get(s.id)! - s.amount), 0);
      expect(r.bankDelta + punterNet + r.rake).toBe(0);
    }
  });

  it('rotation: next seat clockwise, skipping those who passed', () => {
    const order = ['s1', 's2', 's3', 's4'];
    expect(nextCandidate(order, undefined, new Set())).toBe('s1');
    expect(nextCandidate(order, 's2', new Set())).toBe('s3');
    expect(nextCandidate(order, 's4', new Set(['s1']))).toBe('s2');
    expect(nextCandidate(order, 's2', new Set(order))).toBeUndefined();
    expect(nextCandidate([], undefined, new Set())).toBeUndefined();
    // the previous banker left: start from the beginning
    expect(nextCandidate(order, 'gone', new Set())).toBe('s1');
  });

  it('edges per chip: banker −1.06 %, punters −1.24 %, house +2.29 %', () => {
    const e = exactEdges(8, 0.05, 8, 11);
    const c = chemmyEdges(e.pBanker, e.pPlayer, 0.05);
    expect(c.banker).toBeCloseTo(-0.010579, 5);
    expect(c.punter).toBeCloseTo(-0.012351, 5);
    expect(c.house).toBeCloseTo(0.02293, 4);
  });

  it('Monte-Carlo: the rake is the only systematic gain (10⁵ coups, bank covers 100)', () => {
    const rng = seededRng(99);
    const shoe = new BaccaratShoe(8);
    let bank = 0;
    let punters = 0;
    let rake = 0;
    const n = 100_000;
    for (let i = 0; i < n; i++) {
      if (shoe.needsShuffle(0.8)) shoe.shuffle(rng, true);
      const c = dealCoup(() => shoe.draw());
      const r = settleChemmy(c.winner, [{ id: 'p', name: 'P', amount: 100 }], 0.05);
      bank += r.bankDelta;
      punters += r.punterReturns.get('p')! - 100;
      rake += r.rake;
    }
    expect(bank + punters + rake).toBe(0);
    expect(rake / (100 * n)).toBeCloseTo(0.05 * 0.4586, 2);
    expect(punters / (100 * n)).toBeLessThan(0.005);
  });
});
