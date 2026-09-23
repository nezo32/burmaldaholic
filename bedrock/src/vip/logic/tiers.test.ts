import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import {
  DEFAULT_THRESHOLDS,
  type DayLedger,
  cashbackAmount,
  cashbackRate,
  contractBonus,
  contractSlots,
  loanUnlockedAt,
  maxLoanFor,
  perksAt,
  progress,
  promotions,
  recordRound,
  rollLedger,
  sanitizeThresholds,
  thresholdOf,
  tierFor,
  vipAchievement,
} from './tiers';

const TH = DEFAULT_THRESHOLDS;
const RATES = [0.02, 0.03, 0.04, 0.05];
const LOANS = [
  [100, 3, 0],
  [500, 3, 0],
  [2000, 5, 1],
  [10000, 7, 2],
  [50000, 7, 4],
];

describe('tier by lifetime wagered (§12)', () => {
  it('thresholds are inclusive', () => {
    expect(tierFor(0, TH)).toBe(0);
    expect(tierFor(4999, TH)).toBe(0);
    expect(tierFor(5000, TH)).toBe(1);
    expect(tierFor(24_999, TH)).toBe(1);
    expect(tierFor(25_000, TH)).toBe(2);
    expect(tierFor(100_000, TH)).toBe(3);
    expect(tierFor(499_999, TH)).toBe(3);
    expect(tierFor(500_000, TH)).toBe(4);
    expect(tierFor(2_500_000, TH)).toBe(5);
    expect(tierFor(1e12, TH)).toBe(5);
  });
  it('non-ascending config is repaired so a higher tier is never cheaper', () => {
    expect(sanitizeThresholds([5000, 100, 100, 200_000, 150_000])).toEqual([5000, 5001, 5002, 200_000, 200_001]);
    expect(sanitizeThresholds([Number.NaN, -5])).toEqual([5000, 25_000, 100_000, 500_000, 2_500_000]);
    expect(tierFor(5001, [5000, 100, 100, 200_000, 150_000])).toBe(2);
  });
  it('thresholdOf', () => {
    expect(thresholdOf(0, TH)).toBe(0);
    expect(thresholdOf(2, TH)).toBe(25_000);
    expect(thresholdOf(9, TH)).toBe(2_500_000);
  });
  it('progress to next tier, top tier, stored tier never lost', () => {
    expect(progress(12_400, TH)).toEqual({ tier: 1, next: 2, target: 25_000, fraction: (12_400 - 5000) / 20_000 });
    expect(progress(3_000_000, TH)).toEqual({ tier: 5, fraction: 1 });
    // admin raised the thresholds: stored Gold stays Gold
    const p = progress(10, TH, 2);
    expect(p.tier).toBe(2);
    expect(p.next).toBe(3);
    expect(p.fraction).toBe(0);
  });
  it('promotions lists every tier gained once', () => {
    expect(promotions(0, 0)).toEqual([]);
    expect(promotions(0, 1)).toEqual([1]);
    expect(promotions(1, 4)).toEqual([2, 3, 4]);
    expect(promotions(4, 9)).toEqual([5]);
    expect(promotions(3, 2)).toEqual([]);
  });
  it('milestone ids', () => {
    expect(vipAchievement(0)).toBeUndefined();
    expect(vipAchievement(1)).toBe('vip_silver');
    expect(vipAchievement(5)).toBe('vip_netherite');
    expect(vipAchievement(6)).toBeUndefined();
  });
});

describe('perks', () => {
  it('cashback rates by tier', () => {
    expect(cashbackRate(0, RATES)).toBe(0);
    expect(cashbackRate(1, RATES)).toBe(0);
    expect(cashbackRate(2, RATES)).toBe(0.02);
    expect(cashbackRate(3, RATES)).toBe(0.03);
    expect(cashbackRate(4, RATES)).toBe(0.04);
    expect(cashbackRate(5, RATES)).toBe(0.05);
    expect(cashbackRate(5, [0, 0, 0, 9])).toBe(0.5);
  });
  it('contract bonus and slots', () => {
    expect([0, 1, 2, 3, 4, 5].map((t) => contractBonus(t, 0.05, 0.1))).toEqual([0, 0.05, 0.1, 0.1, 0.1, 0.1]);
    expect([0, 1, 2, 3, 4, 5].map((t) => contractSlots(t, 3))).toEqual([3, 3, 3, 4, 5, 5]);
  });
  it('loans per tier from loan.products', () => {
    expect([0, 1, 2, 3, 4, 5].map((t) => maxLoanFor(t, LOANS))).toEqual([500, 2000, 10000, 10000, 50000, 50000]);
    expect([0, 1, 2, 3, 4, 5].map((t) => loanUnlockedAt(t, LOANS))).toEqual([500, 2000, 10000, undefined, 50000, undefined]);
    expect(maxLoanFor(3, [])).toBe(0);
  });
  it('perk table matches §12', () => {
    const params = { loanProducts: LOANS, contractBonusSilver: 0.05, contractBonusGold: 0.1, baseSlots: 3, cashback: RATES };
    const keys = (t: number) => perksAt(t, params).map((p) => p.key.replace('gui.burmaldaholic.vip.perk.', ''));
    expect(keys(0)).toEqual(['poker_micro', 'loan']);
    expect(keys(1)).toEqual(['poker_low', 'scratch_gold', 'loan', 'contract_bonus', 'cosmetic_name']);
    expect(keys(2)).toEqual(['netherite_slots', 'high_roller', 'poker_mid', 'emerald_rate', 'loan', 'contract_bonus', 'cashback', 'cosmetic_particles']);
    expect(keys(3)).toEqual(['loan_discount', 'contract_slots', 'cashback', 'cosmetic_title']);
    expect(keys(4)).toEqual(['poker_high', 'loan', 'contract_slots', 'cashback', 'cosmetic_card']);
    expect(keys(5)).toEqual(['cashback', 'cosmetic_aura']);
    expect(perksAt(2, params)).toContainEqual({ key: 'gui.burmaldaholic.vip.perk.cashback', percent: 2 });
    expect(perksAt(1, params)).toContainEqual({ key: 'gui.burmaldaholic.vip.perk.contract_bonus', percent: 5 });
    expect(perksAt(4, params)).toContainEqual({ key: 'gui.burmaldaholic.vip.perk.contract_slots', count: 5 });
    expect(perksAt(4, params)).toContainEqual({ key: 'gui.burmaldaholic.vip.perk.loan', chips: 50000 });
  });
});

describe('cashback (§12, rate × theoretical loss since 2026-09)', () => {
  it('floor(rate × theoretical loss)', () => {
    expect(cashbackAmount(600, 0.02)).toBe(12);
    expect(cashbackAmount(49, 0.02)).toBe(0);
    expect(cashbackAmount(1000, 0)).toBe(0);
    expect(cashbackAmount(0, 0.05)).toBe(0);
    expect(cashbackAmount(-5, 0.05)).toBe(0);
    expect(cashbackAmount(100, 0.03)).toBe(3);
  });
  it('ledger rolls at the MCD boundary and only eligible rounds count', () => {
    let r = recordRound(undefined, 5, 100, 0, true, 2);
    expect(r.closed).toBeUndefined();
    r = recordRound(r.ledger, 5, 50, 100, false, 1);
    expect(r.ledger).toEqual({ day: 5, staked: 150, returned: 100, cbStaked: 100, cbReturned: 0, cbTheo: 2 });
    const next = recordRound(r.ledger, 6, 10, 0, true, 0.5);
    expect(next.closed).toEqual(r.ledger);
    expect(next.ledger).toEqual({ day: 6, staked: 10, returned: 0, cbStaked: 10, cbReturned: 0, cbTheo: 0.5 });
    expect(rollLedger(next.ledger, 6).closed).toBeUndefined();
    expect(rollLedger(next.ledger, 9).closed).toEqual(next.ledger);
    expect(rollLedger({ bogus: 1 } as unknown as DayLedger, 3).ledger.day).toBe(3);
    // pre-change ledgers (no cbTheo) pay nothing
    const legacy = { day: 1, staked: 100, returned: 0, cbStaked: 100, cbReturned: 0 } as unknown as DayLedger;
    expect(rollLedger(legacy, 2).closed?.cbTheo).toBe(0);
  });

  // Low-edge, high-variance play: blackjack-like even-money game with HE 0.41 %, a Netherite
  // player (5 %), few big bets per day. The OLD formula (rate × max(0, net loss)) paid more
  // than the edge here; the new one keeps the edge at HE × (1 − rate) > 0.
  const simulate = (formula: 'old' | 'new') => {
    const rng = seededRng(4242);
    const rate = 0.05;
    const he = 0.0041;
    const pWin = (1 - he) / 2;
    let staked = 0;
    let returned = 0;
    let paid = 0;
    for (let d = 0; d < 40000; d++) {
      let ds = 0;
      let dr = 0;
      for (let i = 0; i < 5; i++) {
        ds += 1000;
        if (rng.next() < pWin) dr += 2000;
      }
      staked += ds;
      returned += dr;
      paid += formula === 'old' ? Math.floor(Math.max(0, ds - dr) * rate) : cashbackAmount(ds * he, rate);
    }
    return { edge: 1 - returned / staked, effective: 1 - (returned + paid) / staked, paidRate: paid / staked, rate, he };
  };

  it('Monte-Carlo: the old net-loss formula flips a low-edge game (+EV), the new one never does', () => {
    const old = simulate('old');
    // old cashback alone is worth more than the whole house edge -> +EV for the player
    expect(old.paidRate).toBeGreaterThan(old.he * 1.5);
    expect(old.effective).toBeLessThan(0);
    const cur = simulate('new');
    expect(cur.paidRate).toBeLessThan(cur.he);
    expect(cur.effective).toBeGreaterThan(0);
    // cashback is exactly rate × theoretical loss: effective ≈ realised edge − rate × HE
    expect(cur.effective).toBeCloseTo(cur.edge - cur.rate * cur.he, 3);
  });

  it('expected cashback never exceeds the expected loss (any edge, any rate ≤ 0.5)', () => {
    for (const he of [0.0041, 0.0136, 0.027, 0.15]) {
      for (const rate of [0.02, 0.05, 0.5]) {
        const stake = 1_000_000;
        expect(cashbackAmount(stake * he, rate)).toBeLessThan(stake * he);
      }
    }
  });
});
