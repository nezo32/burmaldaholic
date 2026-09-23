import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import {
  MCD,
  type AdvanceConfig,
  type LoanRecord,
  adminForceDefault,
  adminSetDebt,
  advance,
  applyPayment,
  availableProducts,
  boundariesPassed,
  ceilSafe,
  difficultyBand,
  dueAmount,
  emptyRecord,
  freezeSteps,
  shiftTimers,
  garnishAmount,
  garnishableWinnings,
  isGarnishable,
  lateFeeAmount,
  loanRate,
  nextBoundary,
  normalizeRecord,
  parseDifficulty,
  parseProducts,
  ratePercent,
  seizeAmount,
  takeError,
  takeLoan,
} from './loan';

const RATE = { goodStandingDiscount: 0.02, goodStandingMaxSteps: 5, minRate: 0.1, platinumDiscount: 0.02 };
const products = parseProducts([
  [100, 3, 0],
  [500, 3, 0],
  [2000, 5, 1],
  [10000, 7, 2],
  [50000, 7, 4],
]);
const ADV: AdvanceConfig = { lateFee: 0.1, capMultiplier: 2, warningTicks: [24000, 2400] };

describe('difficulty bands', () => {
  it('maps difficulty and hardcore', () => {
    expect(difficultyBand('peaceful', false)).toBe('easy');
    expect(difficultyBand('easy', false)).toBe('easy');
    expect(difficultyBand('normal', false)).toBe('normal');
    expect(difficultyBand('hard', false)).toBe('hard');
    expect(difficultyBand('normal', true)).toBe('hard');
    expect(parseDifficulty('Peaceful')).toBe('peaceful');
    expect(parseDifficulty('Hard')).toBe('hard');
    expect(parseDifficulty('???')).toBe('normal');
  });
});

describe('products', () => {
  it('parses the default table with names by index', () => {
    expect(products.map((p) => [p.id, p.principal, p.days, p.minTier])).toEqual([
      ['pocket', 100, 3, 0],
      ['rent', 500, 3, 0],
      ['business', 2000, 5, 1],
      ['serious', 10000, 7, 2],
      ['life_changing', 50000, 7, 4],
    ]);
  });
  it('falls back to defaults on garbage and skips bad rows', () => {
    expect(parseProducts('nope')).toHaveLength(5);
    expect(parseProducts([])).toHaveLength(5);
    expect(parseProducts([[1, 2, 3], 'x', [5]])).toEqual([{ index: 0, id: 'pocket', principal: 1, days: 2, minTier: 3 }]);
  });
  it('clamps ranges', () => {
    expect(parseProducts([[0, 500, 9]])[0]).toMatchObject({ principal: 1, days: 100, minTier: 5 });
  });
  it('gates by VIP tier', () => {
    expect(availableProducts(products, 0)).toMatchObject({ locked: 3 });
    expect(availableProducts(products, 1).available.map((p) => p.principal)).toEqual([100, 500, 2000]);
    expect(availableProducts(products, 2).locked).toBe(1);
    expect(availableProducts(products, 4).locked).toBe(0);
  });
});

describe('interest', () => {
  it('base rates per band', () => {
    expect(loanRate({ ...RATE, base: 0.15 }, 0, 0)).toBe(0.15);
    expect(loanRate({ ...RATE, base: 0.2 }, 0, 0)).toBe(0.2);
    expect(loanRate({ ...RATE, base: 0.25 }, 0, 0)).toBe(0.25);
  });
  it('good standing discount capped at 5 steps and floored at minRate', () => {
    expect(loanRate({ ...RATE, base: 0.25 }, 2, 0)).toBe(0.21);
    expect(loanRate({ ...RATE, base: 0.25 }, 5, 0)).toBe(0.15);
    expect(loanRate({ ...RATE, base: 0.25 }, 50, 0)).toBe(0.15);
    expect(loanRate({ ...RATE, base: 0.15 }, 5, 3)).toBe(0.1);
    expect(loanRate({ ...RATE, base: 0.2 }, 5, 4)).toBe(0.1);
  });
  it('platinum discount from tier 3', () => {
    expect(loanRate({ ...RATE, base: 0.2 }, 0, 2)).toBe(0.2);
    expect(loanRate({ ...RATE, base: 0.2 }, 0, 3)).toBe(0.18);
  });
  it('due = ceil(principal × (1 + rate)) without float noise', () => {
    expect(dueAmount(100, 0.15)).toBe(115);
    expect(dueAmount(500, 0.2)).toBe(600);
    expect(dueAmount(2000, 0.25)).toBe(2500);
    expect(dueAmount(10000, 0.18)).toBe(11800);
    expect(dueAmount(50000, 0.1)).toBe(55000);
    expect(dueAmount(100, 0.123)).toBe(113);
    expect(dueAmount(7, 0.15)).toBe(9);
    expect(ceilSafe(600.0000000000001)).toBe(600);
    expect(ratePercent(0.18)).toBe(18);
  });
});

describe('taking a loan', () => {
  it('opens an active loan with a day-based deadline', () => {
    const r = takeLoan(emptyRecord(), products[1]!, 0.2, 1000);
    expect(r).toMatchObject({ status: 'active', principal: 500, due: 600, owed: 600, issueTick: 1000, deadlineTick: 1000 + 3 * MCD });
  });
  it('anti-abuse: one at a time, not in default, not in cooldown, VIP', () => {
    const active = takeLoan(emptyRecord(), products[0]!, 0.2, 0);
    expect(takeError(active, products[0]!, 0, 1)).toBe('one_at_a_time');
    expect(takeError({ ...active, status: 'default' }, products[0]!, 0, 1)).toBe('in_default');
    expect(takeError({ ...emptyRecord(), cooldownUntil: 500 }, products[0]!, 0, 499)).toBe('cooldown');
    expect(takeError({ ...emptyRecord(), cooldownUntil: 500 }, products[0]!, 0, 500)).toBeUndefined();
    expect(takeError(emptyRecord(), products[2]!, 0, 0)).toBe('vip');
    expect(takeError(emptyRecord(), products[2]!, 1, 0)).toBeUndefined();
  });
});

describe('repayment', () => {
  const loan = takeLoan({ ...emptyRecord(), goodStanding: 2 }, products[1]!, 0.2, 0);
  it('partial payments reduce owed', () => {
    const r = applyPayment(loan, 100, 10, 5);
    expect(r).toMatchObject({ paid: 100, closed: false });
    expect(r.rec.owed).toBe(500);
  });
  it('clamps to owed and closes on time -> goodStanding + 1', () => {
    const r = applyPayment(loan, 10_000, 10, 5);
    expect(r).toMatchObject({ paid: 600, closed: true, onTime: true, fromDefault: false });
    expect(r.rec).toMatchObject({ status: 'none', owed: 0, goodStanding: 3 });
  });
  it('closing from default resets standing and starts the cooldown', () => {
    const r = applyPayment({ ...loan, status: 'default' }, 600, 100, 5);
    expect(r).toMatchObject({ closed: true, onTime: false, fromDefault: true });
    expect(r.rec).toMatchObject({ status: 'none', goodStanding: 0, cooldownUntil: 100 + 5 * MCD });
  });
  it('ignores zero / negative / no-loan payments', () => {
    expect(applyPayment(loan, 0, 0, 5).paid).toBe(0);
    expect(applyPayment(loan, -5, 0, 5).paid).toBe(0);
    expect(applyPayment(emptyRecord(), 50, 0, 5).paid).toBe(0);
  });
});

describe('advance: warnings, default, late fees', () => {
  const loan = takeLoan(emptyRecord(), products[1]!, 0.2, 0); // due 600 at 72000
  it('sends each warning once', () => {
    let r: LoanRecord = loan;
    let e = advance(r, 2 * MCD - 1, ADV);
    expect(e.events.warnings).toEqual([]);
    e = advance(r, 2 * MCD, ADV);
    expect(e.events.warnings).toEqual([24000]);
    r = e.rec;
    expect(advance(r, 2 * MCD + 100, ADV).events.warnings).toEqual([]);
    e = advance(r, 3 * MCD - 2400, ADV);
    expect(e.events.warnings).toEqual([2400]);
    expect(advance(e.rec, 3 * MCD - 10, ADV).events.warnings).toEqual([]);
  });
  it('skips warnings as long as the whole term (1-day loan, 1-day warning)', () => {
    const oneDay = takeLoan(emptyRecord(), { index: 0, id: 'pocket', principal: 100, days: 1, minTier: 0 }, 0.2, 0);
    expect(advance(oneDay, 1, ADV).events.warnings).toEqual([]);
    expect(advance(oneDay, MCD - 2400, ADV).events.warnings).toEqual([2400]);
  });
  it('after offline time only the most urgent warning is sent', () => {
    const e = advance(loan, 3 * MCD - 100, ADV);
    expect(e.events.warnings).toEqual([2400]);
    expect(e.rec.warned.sort()).toEqual([2400, 24000]);
  });
  it('defaults at the deadline without a fee yet', () => {
    const e = advance(loan, 3 * MCD, ADV);
    expect(e.events.defaulted).toBe(true);
    expect(e.events.lateFees).toEqual([]);
    expect(e.rec).toMatchObject({ status: 'default', owed: 600, owedAtDeadline: 600, feeDays: 0 });
  });
  it('charges simple (not compound) fees per MCD boundary, capped at 2 × due', () => {
    let r = advance(loan, 3 * MCD, ADV).rec;
    let e = advance(r, 4 * MCD, ADV);
    expect(e.events.lateFees).toEqual([60]);
    r = e.rec;
    expect(r.owed).toBe(660);
    expect(advance(r, 4 * MCD + 5, ADV).events.lateFees).toEqual([]);
    e = advance(r, 13 * MCD, ADV); // 9 more boundaries: 540 more, but the cap is 1200
    expect(e.rec.owed).toBe(1200);
    expect(e.events.lateFees.reduce((a, b) => a + b, 0)).toBe(540);
    expect(advance(e.rec, 30 * MCD, ADV).rec.owed).toBe(1200);
  });
  it('offline catch-up charges several fees at once', () => {
    const e = advance(loan, 6 * MCD + 1, ADV);
    expect(e.events.defaulted).toBe(true);
    expect(e.events.lateFees).toEqual([60, 60, 60]);
    expect(e.rec.owed).toBe(780);
  });
  it('payments in default lower owed but fees stay based on owed-at-deadline', () => {
    let r = advance(loan, 3 * MCD, ADV).rec;
    r = applyPayment(r, 500, 3 * MCD, 5).rec;
    expect(r.owed).toBe(100);
    expect(advance(r, 4 * MCD, ADV).rec.owed).toBe(160);
  });
  it('boundary helpers', () => {
    expect(boundariesPassed(100, 50)).toBe(0);
    expect(boundariesPassed(100, 100 + MCD - 1)).toBe(0);
    expect(boundariesPassed(100, 100 + MCD)).toBe(1);
    expect(nextBoundary(100, 100)).toBe(100 + MCD);
    expect(nextBoundary(100, 100 + MCD)).toBe(100 + 2 * MCD);
    expect(lateFeeAmount(115, 0.05)).toBe(6);
    expect(lateFeeAmount(600, 0)).toBe(0);
  });
  it('property: owed never exceeds the cap and never drops without payments', () => {
    const rng = seededRng(7);
    for (let i = 0; i < 500; i++) {
      const p = products[Math.floor(rng.next() * products.length)]!;
      const cfg = { ...ADV, lateFee: rng.next() * 0.5, capMultiplier: 1 + rng.next() * 3 };
      let r = takeLoan(emptyRecord(), p, 0.1 + rng.next() * 0.2, 0);
      let now = 0;
      let prev = r.owed;
      for (let s = 0; s < 20; s++) {
        now += Math.floor(rng.next() * 2 * MCD);
        r = advance(r, now, cfg).rec;
        expect(r.owed).toBeGreaterThanOrEqual(prev);
        expect(r.owed).toBeLessThanOrEqual(Math.max(r.due, Math.floor(r.due * cfg.capMultiplier)));
        prev = r.owed;
      }
    }
  });
});

describe('asset freeze schedule', () => {
  const loan = takeLoan(emptyRecord(), parseProducts(undefined)[1]!, 0.2, 0);
  const cfg: AdvanceConfig = { lateFee: 0.05, capMultiplier: 2, warningTicks: [] };
  it('seizes at default and once per boundary', () => {
    const d = advance(loan, 3 * MCD, cfg);
    let f = freezeSteps(d.rec, 3 * MCD, d.events.defaulted);
    expect(f.seizures).toBe(1);
    expect(freezeSteps(f.rec, 3 * MCD + 100, false).seizures).toBe(0);
    f = freezeSteps(f.rec, 4 * MCD, false);
    expect(f.seizures).toBe(1);
    expect(freezeSteps(f.rec, 7 * MCD, false).seizures).toBe(3);
  });
  it('offline default catch-up seizes for every missed boundary', () => {
    const d = advance(loan, 5 * MCD + 3, cfg);
    expect(freezeSteps(d.rec, 5 * MCD + 3, d.events.defaulted).seizures).toBe(3);
  });
  it('freeze switched on mid-default starts at the next boundary', () => {
    const d = advance(loan, 3 * MCD, cfg).rec;
    const f = freezeSteps(d, 3 * MCD + 500, false);
    expect(f.seizures).toBe(0);
    expect(freezeSteps(f.rec, 4 * MCD, false).seizures).toBe(1);
  });
  it('nothing for active or no loan', () => {
    expect(freezeSteps(loan, 10, false).seizures).toBe(0);
    expect(freezeSteps(emptyRecord(), 10, true).seizures).toBe(0);
  });
});

describe('garnishment, seizure, admin', () => {
  it('garnish floor(credit × pct) capped at owed', () => {
    expect(garnishAmount(21, 50, 1000)).toBe(10);
    expect(garnishAmount(21, 100, 1000)).toBe(21);
    expect(garnishAmount(21, 100, 5)).toBe(5);
    expect(garnishAmount(21, 0, 5)).toBe(0);
    expect(garnishAmount(0, 50, 5)).toBe(0);
  });
  it('seize min(owed, floor(balance × pct))', () => {
    expect(seizeAmount(1001, 50, 10_000)).toBe(500);
    expect(seizeAmount(1001, 50, 300)).toBe(300);
    expect(seizeAmount(0, 50, 300)).toBe(0);
  });
  it('garnishable reasons', () => {
    expect(isGarnishable('core.earn.ore')).toBe(true);
    expect(isGarnishable('slots.payout')).toBe(false);
    expect(isGarnishable('dice_duel.pawn')).toBe(false);
    expect(isGarnishable('core.offline')).toBe(false);
    expect(isGarnishable('dice_duel.pvp')).toBe(true);
    expect(isGarnishable('vip.contract')).toBe(true);
    expect(isGarnishable('slots.refund')).toBe(false);
    expect(isGarnishable('core.cashier.deposit')).toBe(false);
    expect(isGarnishable('core.admin.give')).toBe(false);
    expect(isGarnishable('loan.take')).toBe(false);
    // poker cash-out: the returned buy-in is not income, the winnings above it are
    expect(isGarnishable('poker.stake_return')).toBe(false);
    expect(isGarnishable('poker.cashout')).toBe(true);
  });
  it('garnishes only net winnings of house-banked rounds', () => {
    // push: the stake comes back, nothing is garnished
    expect(garnishableWinnings({ houseBanked: true, staked: 100, totalReturn: 100 })).toBe(0);
    expect(garnishAmount(garnishableWinnings({ houseBanked: true, staked: 100, totalReturn: 100 }), 50, 1000)).toBe(0);
    // loss / partial return
    expect(garnishableWinnings({ houseBanked: true, staked: 100, totalReturn: 0 })).toBe(0);
    expect(garnishableWinnings({ houseBanked: true, staked: 100, totalReturn: 50 })).toBe(0);
    // 1:1 win: 200 back, 100 is winnings -> 50 % = 50 (not 100)
    expect(garnishableWinnings({ houseBanked: true, staked: 100, totalReturn: 200 })).toBe(100);
    expect(garnishAmount(garnishableWinnings({ houseBanked: true, staked: 100, totalReturn: 200 }), 50, 1000)).toBe(50);
    // blackjack 3:2
    expect(garnishableWinnings({ houseBanked: true, staked: 10, totalReturn: 25 })).toBe(15);
    // PvP is handled on the balance change
    expect(garnishableWinnings({ houseBanked: false, staked: 100, totalReturn: 200 })).toBe(0);
  });
  it('admin set/clear/force default', () => {
    const cleared = adminSetDebt({ ...takeLoan(emptyRecord(), products[0]!, 0.2, 0), goodStanding: 3 }, 0, 10);
    expect(cleared).toMatchObject({ status: 'none', owed: 0, goodStanding: 3 });
    const set = adminSetDebt(emptyRecord(), 700, 10);
    expect(set).toMatchObject({ status: 'active', owed: 700, due: 700, deadlineTick: 10 + 3 * MCD });
    const forced = adminForceDefault(set, 50);
    expect(advance(forced, 50, ADV).events.defaulted).toBe(true);
  });
  it('normalizes corrupt records', () => {
    expect(normalizeRecord(null)).toEqual(emptyRecord());
    expect(normalizeRecord({ status: 'active', owed: 0, goodStanding: 4 })).toMatchObject({ status: 'none', goodStanding: 4 });
    expect(normalizeRecord({ status: 'default', owed: 12.7, warned: [1, 'x'] })).toMatchObject({ status: 'default', owed: 12, warned: [1] });
  });
});

describe('dormant time', () => {
  it('shifts pending timers so nothing fires on re-enable', () => {
    const loan = { ...takeLoan(emptyRecord(), parseProducts(undefined)[0]!, 0.2, 0), nextWaveTick: 0, cooldownUntil: 50 };
    const s = shiftTimers(loan, 1000);
    expect(s).toMatchObject({ issueTick: 1000, deadlineTick: 3 * MCD + 1000, nextWaveTick: 0, cooldownUntil: 1050 });
    expect(shiftTimers(loan, 0)).toBe(loan);
    expect(shiftTimers(emptyRecord(), 10).deadlineTick).toBe(0);
  });
});
