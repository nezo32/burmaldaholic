/**
 * Independent tester suite: plural helper, casino-mode resolution, economy atomicity/cap,
 * offline settlement (no double pay), VIP cashback, loans (§3, §5, §12, architecture §4/§7).
 */
import { describe, expect, it } from 'vitest';
import { applyCredit, withdrawable } from '../../src/core/logic/economy-math';
import { applyDelta, planTransaction, settleBankroll, tryReserve, type AccountView } from '../../src/core/logic/ledger';
import { resolveCasinoEnabled } from '../../src/core/logic/mode';
import { emptyOffline, isEmptyOffline, normalizeOffline, planRecovery, withChips, withResolved } from '../../src/core/logic/offline';
import { pluralSuffix } from '../../src/core/logic/plural';
import { HOUSE_EDGE } from '../../src/core/logic/house-edge';
import { advance, applyPayment, dueAmount, emptyRecord, garnishAmount, loanRate, MCD, seizeAmount, takeLoan, takeError, type Product } from '../../src/loan/logic/loan';
import { cashbackAmount, cashbackRate, contractSlots, recordRound, rollLedger, tierFor } from '../../src/vip/logic/tiers';
import { CONFIG_CATALOG } from '../../src/core/logic/config-catalog';
import { decay, record } from '../../src/core/logic/streak';
import { checkAdd, slipLimits } from '../../src/games/roulette/logic/limits';
import { allSpots } from '../../src/games/roulette/logic/bets';

describe('plural suffix (architecture §7): RU one/few/many split, EN singular only for 1', () => {
  const ru = (n: number) => {
    const a = Math.abs(Math.trunc(n));
    if (a % 10 === 1 && a % 100 !== 11) return a === 1 ? 'p1' : 'p21';
    if (a % 10 >= 2 && a % 10 <= 4 && (a % 100 < 10 || a % 100 >= 20)) return 'p2';
    return 'p5';
  };
  it('matches the CLDR Russian rule for 0..2000 and negatives', () => {
    for (let n = -30; n <= 2000; n++) expect(pluralSuffix(n)).toBe(ru(n));
  });
  it('spot values', () => {
    expect([1, 2, 5, 11, 21, 111, 0, 12, 22, 101, 1001, -1, 1.9].map(pluralSuffix)).toEqual(['p1', 'p2', 'p5', 'p5', 'p21', 'p5', 'p5', 'p5', 'p2', 'p21', 'p21', 'p1', 'p1']);
  });
});

describe('casino mode resolution (architecture §4)', () => {
  it('world override > pack setting > default on', () => {
    expect(resolveCasinoEnabled(false, { 'burmaldaholic:casino_mode': true })).toBe(false);
    expect(resolveCasinoEnabled(undefined, { 'burmaldaholic:casino_mode': false })).toBe(false);
    expect(resolveCasinoEnabled(undefined, undefined)).toBe(true);
    expect(resolveCasinoEnabled('garbage', {})).toBe(true);
  });
});

describe('economy (§3.1): never negative, capped, atomic transactions', () => {
  it('credits are capped at maxBalance, excess lost', () => {
    expect(applyCredit(999_999_990, 50, 1_000_000_000)).toEqual({ balance: 1_000_000_000, credited: 10, capped: true });
    expect(applyCredit(5, 2.9, 100)).toEqual({ balance: 7, credited: 2, capped: false });
  });
  it('balances never go negative; non-integer deltas are rejected', () => {
    expect(applyDelta(10, -11)).toEqual({ ok: false, reason: 'insufficient' });
    expect(applyDelta(10, 0.5).ok).toBe(false);
  });
  it('withdrawable = balance − owed, 0 in default', () => {
    expect(withdrawable(500, 200, false)).toBe(300);
    expect(withdrawable(100, 200, false)).toBe(0);
    expect(withdrawable(500, 200, true)).toBe(0);
  });
  it('a transaction with one unaffordable leg changes nothing', () => {
    const view = (a: string): AccountView => (a === 'bank' ? { balance: Infinity } : a === 'p1' ? { balance: 40 } : { balance: 100, locked: 80 });
    const r = planTransaction(
      [
        { account: 'p1', delta: -50 },
        { account: 'bank', delta: 50 },
      ],
      view,
    );
    expect(r.ok).toBe(false);
    // bankroll can't spend reserved chips
    expect(planTransaction([{ account: 'br', delta: -30 }], view).ok).toBe(false);
    const ok = planTransaction(
      [
        { account: 'p1', delta: -40 },
        { account: 'br', delta: 4 },
        { account: 'bank', delta: 36 },
      ],
      view,
    );
    expect(ok.ok).toBe(true);
    if (ok.ok) expect(ok.balances.get('p1')).toBe(0);
  });
  it('legs on the same account are netted before the check', () => {
    const r = planTransaction(
      [
        { account: 'p', delta: -30 },
        { account: 'p', delta: 25 },
      ],
      () => ({ balance: 10 }),
    );
    expect(r.ok && r.balances.get('p')).toBe(5);
  });
  it('owned-casino reservation: accept only if reserved + worst case ≤ bankroll', () => {
    expect(tryReserve({ balance: 100, reserved: 60 }, 40)).toEqual({ balance: 100, reserved: 100 });
    expect(tryReserve({ balance: 100, reserved: 60 }, 41)).toBeUndefined();
    expect(settleBankroll({ balance: 100, reserved: 40 }, { reserved: 40, stake: 20, payout: 40 })).toEqual({ balance: 80, reserved: 0 });
  });
});

describe('offline settlement (§4.1): no refund of a round already settled offline', () => {
  it('resolved tickets are dropped, stale ones from an earlier boot refunded, current kept', () => {
    const stored = [
      { id: 'a', boot: 1 },
      { id: 'b', boot: 1 },
      { id: 'c', boot: 2 },
    ];
    const e = withResolved(withChips(emptyOffline(), 50), 'a');
    const plan = planRecovery(stored, e.resolved, 2);
    expect(plan.dropped.map((t) => t.id)).toEqual(['a']);
    expect(plan.refund.map((t) => t.id)).toEqual(['b']);
    expect(plan.keep.map((t) => t.id)).toEqual(['c']);
  });
  it('settling the same ticket twice offline records it once', () => {
    const e = withResolved(withResolved(emptyOffline(), 'x'), 'x');
    expect(e.resolved).toEqual(['x']);
  });
  it('corrupt stored entries normalize to empty', () => {
    expect(isEmptyOffline(normalizeOffline('{bad'))).toBe(true);
    expect(isEmptyOffline(normalizeOffline(undefined))).toBe(true);
  });
});

describe('VIP cashback (§12, changed 2026-09): never +EV', () => {
  it('rates 2/3/4/5 % from Gold, 0 below Gold', () => {
    const rates = [0.02, 0.03, 0.04, 0.05];
    expect([0, 1, 2, 3, 4, 5].map((t) => cashbackRate(t, rates))).toEqual([0, 0, 0.02, 0.03, 0.04, 0.05]);
    expect(cashbackRate(5, [9, 9, 9, 9])).toBeLessThanOrEqual(0.5); // clamped
  });
  it('cashback = floor(rate × Σ stake × edge) < expected loss for every game and tier', () => {
    for (const [game, edge] of Object.entries(HOUSE_EDGE)) {
      if (edge <= 0) continue;
      for (const rate of [0.02, 0.03, 0.04, 0.05, 0.5]) {
        const staked = 5 * 1000;
        const cb = cashbackAmount(staked * edge, rate);
        expect(cb, game).toBeLessThan(staked * edge);
        expect(edge * staked - cb, game).toBeGreaterThan(0);
      }
    }
  });
  it('a winning day still gets its (small) cashback; PvP/pawn rounds contribute nothing', () => {
    let r = recordRound(undefined, 10, 1000, 3000, true, 1000 * 0.0041);
    r = recordRound(r.ledger, 10, 500, 0, false, 500 * 0.02);
    const closed = rollLedger(r.ledger, 11).closed!;
    expect(closed.cbTheo).toBeCloseTo(4.1, 9);
    expect(cashbackAmount(closed.cbTheo, 0.05)).toBe(0);
    expect(rollLedger({ day: 3, staked: 1, returned: 0, cbStaked: 1, cbReturned: 0 } as never, 4).closed?.cbTheo).toBe(0);
  });
});

describe('loans §5', () => {
  const cfg = (base: number) => ({ base, goodStandingDiscount: 0.02, goodStandingMaxSteps: 5, minRate: 0.1, platinumDiscount: 0.02 });
  it('rate = base − 0.02·min(goodStanding,5) − 0.02 (Platinum+), floor 0.10', () => {
    expect(loanRate(cfg(0.2), 0, 0)).toBeCloseTo(0.2, 9);
    expect(loanRate(cfg(0.25), 2, 3)).toBeCloseTo(0.19, 9);
    expect(loanRate(cfg(0.15), 9, 5)).toBeCloseTo(0.1, 9);
  });
  it('due = ceil(principal × (1 + rate)) without float noise', () => {
    expect(dueAmount(500, 0.2)).toBe(600);
    expect(dueAmount(100, 0.15)).toBe(115);
    expect(dueAmount(2000, 0.19)).toBe(2380);
    expect(dueAmount(101, 0.15)).toBe(117); // 116.15 -> 117
  });
  const product: Product = { index: 1, id: 'rent', principal: 500, days: 3, minTier: 0 };
  it('deadline = issue + days × MCD; late fee is simple (on owedAtDeadline), capped at 2 × due', () => {
    let rec = takeLoan(emptyRecord(), product, 0.2, 1000);
    expect(rec.deadlineTick).toBe(1000 + 3 * MCD);
    const adv = { lateFee: 0.1, capMultiplier: 2, warningTicks: [24000, 2400] };
    rec = advance(rec, rec.deadlineTick, adv).rec;
    expect(rec.status).toBe('default');
    expect(rec.owed).toBe(600);
    rec = advance(rec, rec.deadlineTick + 3 * MCD, adv).rec;
    expect(rec.owed).toBe(600 + 3 * 60); // not compounded
    rec = advance(rec, rec.deadlineTick + 50 * MCD, adv).rec;
    expect(rec.owed).toBe(1200); // cap 2 × due
    // re-running advance for the same time charges nothing more (idempotent)
    expect(advance(rec, rec.deadlineTick + 50 * MCD, adv).rec.owed).toBe(1200);
  });
  it('offline catch-up charges every missed boundary once', () => {
    const adv = { lateFee: 0.15, capMultiplier: 2, warningTicks: [] };
    const rec = takeLoan(emptyRecord(), product, 0.2, 0);
    const late = advance(rec, rec.deadlineTick + 2 * MCD + 5, adv);
    expect(late.events.lateFees).toEqual([90, 90]);
    expect(advance(late.rec, rec.deadlineTick + 2 * MCD + 10, adv).events.lateFees).toEqual([]);
  });
  it('repaying: on time -> goodStanding+1; from default -> goodStanding 0 + cooldown; one loan at a time', () => {
    const rec = takeLoan({ ...emptyRecord(), goodStanding: 2 }, product, 0.2, 0);
    expect(takeError(rec, product, 0, 10)).toBe('one_at_a_time');
    const paid = applyPayment(rec, 10_000, 100, 5);
    expect(paid.paid).toBe(600);
    expect(paid.rec.goodStanding).toBe(3);
    const def = advance(rec, rec.deadlineTick, { lateFee: 0.1, capMultiplier: 2, warningTicks: [] }).rec;
    expect(takeError(def, product, 0, def.deadlineTick)).toBe('in_default');
    const closed = applyPayment(def, 600, def.deadlineTick + 1, 5);
    expect(closed.rec.goodStanding).toBe(0);
    expect(takeError(closed.rec, product, 0, def.deadlineTick + 2)).toBe('cooldown');
    expect(takeError(closed.rec, product, 0, def.deadlineTick + 1 + 5 * MCD)).toBeUndefined();
  });
  it('garnishment 50 % of a credit and seizure min(owed, ⌊balance/2⌋)', () => {
    expect(garnishAmount(101, 50, 1000)).toBe(50);
    expect(garnishAmount(100, 50, 20)).toBe(20);
    expect(garnishAmount(100, 100, 1000)).toBe(100);
    expect(seizeAmount(999, 50, 10_000)).toBe(499);
    expect(seizeAmount(999, 50, 100)).toBe(100);
  });
});

describe('VIP tiers §12, streak §14, roulette limits §9 (config defaults vs spec)', () => {
  const def = (k: string) => CONFIG_CATALOG.find((d) => d.key === k)?.default;
  it('tier thresholds 5k/25k/100k/500k/2.5M and max bets 100…50 000', () => {
    const th = [5_000, 25_000, 100_000, 500_000, 2_500_000];
    expect([0, 4_999, 5_000, 24_999, 25_000, 100_000, 499_999, 500_000, 2_500_000].map((w) => tierFor(w, th))).toEqual([0, 0, 1, 1, 2, 3, 3, 4, 5]);
    expect(['bronze', 'silver', 'gold', 'platinum', 'diamond', 'netherite'].map((t) => def(`vip.maxBet.${t}`))).toEqual([100, 250, 1000, 2500, 10000, 50000]);
    expect([0, 3, 4].map((t) => contractSlots(t, 3))).toEqual([3, 4, 5]);
  });
  it('streak: win = max(S,0)+1, loss = min(S,0)−1, capped ±10, decays 1 per 12000 t', () => {
    const cfg = { max: 10, decayTicks: 12000 };
    expect(record({ s: -4, t: 0 }, 'win', 10, cfg).s).toBe(1);
    expect(record({ s: 3, t: 0 }, 'loss', 10, cfg).s).toBe(-1);
    expect(record({ s: 10, t: 0 }, 'win', 10, cfg).s).toBe(10);
    expect(record({ s: 5, t: 0 }, 'push', 10, cfg).s).toBe(5);
    expect(decay({ s: 5, t: 0 }, 36_000, cfg).s).toBe(2);
    expect(decay({ s: -2, t: 0 }, 1_000_000, cfg).s).toBe(0);
  });
  it('roulette: each inside bet ≤ tier max / 4, total per spin ≤ tier max', () => {
    const l = slipLimits({ tierMax: 100, minBet: 1, insideMaxFraction: 0.25 });
    const straight = allSpots('straight')[17]!;
    expect(checkAdd([], [{ ...straight, amount: 25 }], l)).toBeUndefined();
    expect(checkAdd([{ ...straight, amount: 25 }], [{ ...straight, amount: 1 }], l)).toEqual({ code: 'inside_max', max: 25 });
    expect(checkAdd([], [{ ...allSpots('red')[0]!, amount: 101 }], l)).toEqual({ code: 'total_max', max: 100 });
  });
});
