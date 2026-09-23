import { describe, expect, it } from 'vitest';
import { type BankrollState, bankrollAvailable, settleBankroll, tryReserve } from '../../core/logic/ledger';
import { seededRng } from '../../core/logic/rng';
import { UNKNOWN_WORST_CASE_PER_CHIP, cheapestWorstCase, isBroke, routeHouseResult, worstCaseAt, worstCasePerChip } from './solvency';

describe('worst case table', () => {
  it('matches GAME_DESIGN §18.2 examples', () => {
    expect(worstCasePerChip('roulette')).toBe(36);
    expect(worstCasePerChip('blackjack')).toBe(17.5);
    expect(worstCasePerChip('craps')).toBe(13);
    expect(worstCasePerChip('plinko')).toBe(170);
    expect(worstCasePerChip('slots', 'copper')).toBe(150);
    expect(worstCasePerChip('slots', 'netherite')).toBe(1000);
    expect(worstCasePerChip('slots', 'unknown_variant')).toBe(1000);
    expect(worstCasePerChip('poker')).toBe(0);
    expect(worstCasePerChip('mystery')).toBe(UNKNOWN_WORST_CASE_PER_CHIP);
  });
  it('game-provided overrides win', () => {
    expect(worstCasePerChip('roulette', undefined, { roulette: 40 })).toBe(40);
    expect(worstCasePerChip('slots', 'copper', { 'slots.copper': 90 })).toBe(90);
  });
  it('rounds up to whole chips', () => {
    expect(worstCaseAt(17.5, 1)).toBe(18);
    expect(worstCaseAt(17.5, 2)).toBe(35);
    expect(worstCaseAt(0, 100)).toBe(0);
  });
});

describe('insolvency', () => {
  const tables = [
    { game: 'roulette', minBet: 5, open: true }, // 180
    { game: 'blackjack', minBet: 10, open: true }, // 175
    { game: 'coin_flip', minBet: 1, open: false }, // closed: ignored
    { game: 'poker', minBet: 1, open: true }, // no exposure
  ];
  it('uses the cheapest open exposed table', () => {
    expect(cheapestWorstCase(tables)).toBe(175);
    expect(isBroke({ balance: 174, reserved: 0 }, tables)).toBe(true);
    expect(isBroke({ balance: 175, reserved: 0 }, tables)).toBe(false);
  });
  it('a casino with only poker or no tables is never broke', () => {
    expect(isBroke({ balance: 0, reserved: 0 }, [{ game: 'poker', minBet: 1, open: true }])).toBe(false);
    expect(isBroke({ balance: 0, reserved: 0 }, [])).toBe(false);
  });
  it('minimum bet below 1 counts as 1', () => {
    expect(cheapestWorstCase([{ game: 'coin_flip', minBet: 0, open: true }])).toBe(2);
  });
});

describe('fallback routing', () => {
  it('house wins go to the bankroll', () => {
    expect(routeHouseResult({ balance: 0, reserved: 0 }, 100, 0)).toEqual({ bankrollDelta: 100, bankCovered: 0 });
    expect(routeHouseResult({ balance: 0, reserved: 0 }, 100, 100)).toEqual({ bankrollDelta: 0, bankCovered: 0 });
  });
  it('house losses come out of the unreserved bankroll only', () => {
    expect(routeHouseResult({ balance: 500, reserved: 0 }, 100, 200)).toEqual({ bankrollDelta: -100, bankCovered: 0 });
    expect(routeHouseResult({ balance: 500, reserved: 450 }, 100, 200)).toEqual({ bankrollDelta: -50, bankCovered: 50 });
  });
});

/**
 * Monte-Carlo: an owner banking honest games earns the house edge on the handle, and the
 * reservation rule keeps the bankroll solvent (never negative, reservations ≤ balance).
 */
describe('Monte-Carlo: owner earns the house edge', () => {
  function simulate(game: 'roulette' | 'coin_flip', rounds: number, seed: number) {
    const rng = seededRng(seed);
    let b: BankrollState = { balance: 100_000, reserved: 0 };
    const start = b.balance;
    const bet = 25; // 25 × 1.96 = 49: whole-chip coin-flip payouts, so the edge is exact
    let handle = 0;
    let refused = 0;
    let violations = 0;
    for (let i = 0; i < rounds; i++) {
      const wc = worstCaseAt(worstCasePerChip(game), bet);
      const r = tryReserve(b, wc);
      if (!r) {
        refused++;
        continue;
      }
      b = r;
      let ret: number;
      if (game === 'roulette') ret = Math.floor(rng.next() * 37) === 17 ? bet * 36 : 0; // straight-up 35:1
      else ret = rng.next() < 0.5 ? Math.floor(bet * 1.96) : 0; // coin flip pays 0.96:1
      if (ret > wc) violations++;
      b = settleBankroll(b, { reserved: wc, stake: bet, payout: ret });
      handle += bet;
      if (b.balance < 0 || b.reserved !== 0) violations++;
    }
    return { edge: (b.balance - start) / handle, refused, violations };
  }

  it('roulette straight-up: owner edge ≈ 1/37 = 2.70 %', () => {
    const { edge, refused, violations } = simulate('roulette', 2_000_000, 7);
    expect(refused).toBe(0);
    expect(violations).toBe(0);
    // σ ≈ 5.84 / √2e6 ≈ 0.41 %; tolerance ≈ 3.6σ
    expect(Math.abs(edge - 1 / 37)).toBeLessThan(0.015);
  });

  it('coin flip: owner edge = 2 %', () => {
    const { edge, violations } = simulate('coin_flip', 1_000_000, 11);
    expect(violations).toBe(0);
    // σ ≈ 0.98 / √1e6 ≈ 0.1 %
    expect(Math.abs(edge - 0.02)).toBeLessThan(0.004);
  });

  it('a tiny bankroll refuses bets it cannot cover and never goes negative', () => {
    const rng = seededRng(3);
    let b: BankrollState = { balance: 100, reserved: 0 };
    let accepted = 0;
    let negative = 0;
    for (let i = 0; i < 10_000; i++) {
      const bet = 1 + Math.floor(rng.next() * 10);
      const wc = worstCaseAt(36, bet);
      const r = tryReserve(b, wc);
      if (!r) {
        expect(bankrollAvailable(b)).toBeLessThan(wc);
        continue;
      }
      accepted++;
      b = settleBankroll(r, { reserved: wc, stake: bet, payout: Math.floor(rng.next() * 37) === 0 ? bet * 36 : 0 });
      if (b.balance < 0) negative++;
    }
    expect(negative).toBe(0);
    expect(accepted).toBeGreaterThan(0);
  });
});
