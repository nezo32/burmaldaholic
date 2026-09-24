import { describe, expect, it } from 'vitest';
import { type Card, parseCard } from '../../../core/logic/cards';
import { seededRng } from '../../../core/logic/rng';
import {
  BaccaratShoe,
  BeadPlate,
  CoupClock,
  DEFAULT_RULES,
  OUTCOME_CLASSES,
  type Slip,
  bankerDraws,
  bankerProfit,
  bankerStep,
  boxEdge,
  boxMin,
  burnValue,
  checkAdd,
  checkSlip,
  coupFromCards,
  dealCoup,
  dealOrder,
  exactCounts,
  exactEdges,
  fitSlip,
  handTotal,
  maxAddable,
  playerDraws,
  rankValue,
  revealFrames,
  settleSlip,
  slipLimits,
  snapToStep,
  tableExposure,
  tieRunFires,
  winningNatural,
  worstCaseReturn,
} from '.';

const cards = (s: string): Card[] => s.split(' ').map(parseCard);

describe('card values and totals (§20.1)', () => {
  it('A = 1, 2–9 face, 10/J/Q/K = 0', () => {
    expect(rankValue('A')).toBe(1);
    expect(rankValue('7')).toBe(7);
    for (const r of ['10', 'J', 'Q', 'K'] as const) expect(rankValue(r)).toBe(0);
  });
  it('totals are mod 10 (7 + 8 = 5)', () => {
    expect(handTotal(cards('7S 8H'))).toBe(5);
    expect(handTotal(cards('KS QH'))).toBe(0);
    expect(handTotal(cards('9S 9H 9D'))).toBe(7);
  });
});

describe('drawing rules (§20.3)', () => {
  it('Player draws on 0–5, stands on 6–7', () => {
    for (let t = 0; t <= 5; t++) expect(playerDraws(t)).toBe(true);
    expect(playerDraws(6)).toBe(false);
    expect(playerDraws(7)).toBe(false);
  });
  it('Banker after Player stood: 0–5 draws, 6–7 stands', () => {
    for (let t = 0; t <= 5; t++) expect(bankerDraws(t, undefined)).toBe(true);
    expect(bankerDraws(6, undefined)).toBe(false);
    expect(bankerDraws(7, undefined)).toBe(false);
  });
  it('Banker after Player drew: the full §20.3 grid', () => {
    const grid: Record<number, string> = {
      0: 'DDDDDDDDDD',
      1: 'DDDDDDDDDD',
      2: 'DDDDDDDDDD',
      3: 'DDDDDDDDSD',
      4: 'SSDDDDDDSS',
      5: 'SSSSDDDDSS',
      6: 'SSSSSSDDSS',
      7: 'SSSSSSSSSS',
    };
    for (const [bt, row] of Object.entries(grid)) for (let v = 0; v < 10; v++) expect(bankerDraws(Number(bt), v), `B${bt} vs ${v}`).toBe(row[v] === 'D');
  });

  it('vector 1: 8♠ 9♦ K♥ 7♣ → Player 8 natural beats Banker 6', () => {
    const c = coupFromCards(cards('8S 9D KH 7C'));
    expect(c).toMatchObject({ playerTotal: 8, bankerTotal: 6, winner: 'player', natural: true });
    expect(c.player).toHaveLength(2);
    expect(c.banker).toHaveLength(2);
  });
  it('vector 2: 2♣ K♦ 3♥ 3♠, P3 8♦ → Banker 3 stands vs 8 → Tie 3–3', () => {
    const c = coupFromCards(cards('2C KD 3H 3S 8D'));
    expect(c).toMatchObject({ playerTotal: 3, bankerTotal: 3, winner: 'tie' });
    expect(c.banker).toHaveLength(2);
  });
  it('vector 3: A♠ 5♥ 4♦ K♣, P3 4♥, B3 3♣ → Player 9 beats Banker 8', () => {
    const c = coupFromCards(cards('AS 5H 4D KC 4H 3C'));
    expect(c).toMatchObject({ playerTotal: 9, bankerTotal: 8, winner: 'player' });
    expect(c.player).toHaveLength(3);
    expect(c.banker).toHaveLength(3);
  });
  it('vector 4: 7♦ 2♠ Q♥ 3♥, B3 4♠ → Banker 9 beats 7; Banker 40 wins +38', () => {
    const c = coupFromCards(cards('7D 2S QH 3H 4S'));
    expect(c).toMatchObject({ playerTotal: 7, bankerTotal: 9, winner: 'banker' });
    expect(c.player).toHaveLength(2);
    expect(settleSlip({ banker: 40 }, c, DEFAULT_RULES).totalReturn - 40).toBe(38);
  });
  it('vector 5: Q♠ 6♦ Q♦ A♣ → Player Pair, Player 0 draws, Banker 7', () => {
    const c = coupFromCards(cards('QS 6D QD AC 5H'));
    expect(c.playerPair).toBe(true);
    expect(c.bankerPair).toBe(false);
    expect(c.player).toHaveLength(3);
    expect(handTotal(c.banker)).toBe(7);
    expect(settleSlip({ player_pair: 10 }, c, DEFAULT_RULES).totalReturn).toBe(120);
  });
  it('pairs are by rank (K+Q is no pair)', () => {
    expect(coupFromCards(cards('KS 2H QD 3C 5H 5D')).playerPair).toBe(false);
    expect(coupFromCards(cards('KS 2H KH 2C 5H 5D')).bankerPair).toBe(true);
  });
  it('replay by deal order reproduces the coup', () => {
    const shoe = new BaccaratShoe(8);
    const rng = seededRng(7);
    shoe.shuffle(rng, true);
    for (let i = 0; i < 60; i++) {
      const c = dealCoup(() => shoe.draw());
      expect(coupFromCards(dealOrder(c))).toEqual(c);
    }
  });
  it('winning natural', () => {
    expect(winningNatural(coupFromCards(cards('4S 9D 5H 7C')))).toBe(9);
    expect(winningNatural(coupFromCards(cards('8S 9D KH 7C')))).toBe(8);
    expect(winningNatural(coupFromCards(cards('7D 2S QH 3H 4S')))).toBeUndefined();
  });
});

describe('Banker step and commission (§20.1)', () => {
  it('step for c ∈ {0.05, 0.04, 0, 0.03}', () => {
    expect(bankerStep(0.05)).toBe(20);
    expect(bankerStep(0.04)).toBe(25);
    expect(bankerStep(0)).toBe(1);
    expect(bankerStep(0.03)).toBe(100);
    expect(bankerStep(0.1)).toBe(10);
    expect(bankerStep(0.013)).toBe(100); // none ≤ 100: floor applies
  });
  it('multiples of the step win exactly (1 − c)', () => {
    for (let n = 1; n <= 500; n++) expect(bankerProfit(20 * n, 0.05)).toBe(19 * n);
    for (let n = 1; n <= 200; n++) expect(bankerProfit(25 * n, 0.04)).toBe(24 * n);
    expect(bankerProfit(37, 0)).toBe(37);
  });
  it('an off-step amount floors (B = 5 → +4)', () => {
    expect(bankerProfit(5, 0.05)).toBe(4);
    expect(bankerProfit(150, 0.03)).toBe(145);
  });
  it('snap down', () => {
    expect(snapToStep(59, 20)).toBe(40);
    expect(snapToStep(19, 20)).toBe(0);
  });
});

describe('payouts', () => {
  const r = DEFAULT_RULES;
  const P = { winner: 'player', playerPair: false, bankerPair: false } as const;
  const B = { winner: 'banker', playerPair: false, bankerPair: false } as const;
  const T = { winner: 'tie', playerPair: true, bankerPair: true } as const;
  it('Player 1:1, push on tie', () => {
    expect(settleSlip({ player: 50 }, P, r).totalReturn).toBe(100);
    expect(settleSlip({ player: 50 }, B, r).totalReturn).toBe(0);
    expect(settleSlip({ player: 50 }, T, r).totalReturn).toBe(50);
  });
  it('Banker 0.95:1 with commission, push on tie', () => {
    const s = settleSlip({ banker: 100 }, B, r);
    expect(s.totalReturn).toBe(195);
    expect(s.commission).toBe(5);
    expect(settleSlip({ banker: 100 }, T, r).totalReturn).toBe(100);
    expect(settleSlip({ banker: 100 }, P, r).commission).toBe(0);
  });
  it('Tie 8:1 (9:1 configurable), pairs 11:1', () => {
    expect(settleSlip({ tie: 5 }, T, r).totalReturn).toBe(45);
    expect(settleSlip({ tie: 5 }, T, { ...r, tiePays: 9 }).totalReturn).toBe(50);
    expect(settleSlip({ tie: 5 }, P, r).totalReturn).toBe(0);
    expect(settleSlip({ player_pair: 3, banker_pair: 2 }, T, r).totalReturn).toBe(36 + 24);
  });
  it('Player + Banker together is a small sure loss, never a gain', () => {
    for (const o of OUTCOME_CLASSES) expect(settleSlip({ player: 100, banker: 100 }, o, r).totalReturn).toBeLessThanOrEqual(200);
  });
});

describe('exact enumeration (§20.2, §20.8)', () => {
  it('8 decks: the three counts, their sum and the pair probability', () => {
    const c = exactCounts(8);
    expect(c.banker).toBe(2_292_252_566_437_888);
    expect(c.player).toBe(2_230_518_282_592_256);
    expect(c.tie).toBe(475_627_426_473_216);
    expect(c.total).toBe(416 * 415 * 414 * 413 * 412 * 411);
    expect(c.banker + c.player + c.tie).toBe(c.total);
    expect(c.pair).toBeCloseTo(31 / 415, 15);
  });
  it('8 decks: house edges 1.06 / 1.24 / 14.36 / 10.36 %', () => {
    const e = exactEdges(8, 0.05, 8, 11);
    expect(e.banker).toBeCloseTo(0.010579, 5);
    expect(e.player).toBeCloseTo(0.012351, 5);
    expect(e.tie).toBeCloseTo(0.143596, 5);
    expect(e.pair).toBeCloseTo(43 / 415, 10);
    expect(exactEdges(8, 0.05, 9, 11).tie).toBeCloseTo(0.0484, 3);
    // pairPays 12 is the highest that keeps a house edge (13 would favour the player)
    expect(exactEdges(8, 0.05, 8, 12).pair).toBeGreaterThan(0);
    expect(exactEdges(8, 0.05, 8, 13).pair).toBeLessThan(0);
  });
  it('every deck count 1–8 sums to its denominator and keeps a positive edge', () => {
    for (let d = 1; d <= 8; d++) {
      const c = exactCounts(d);
      expect(c.banker + c.player + c.tie).toBe(c.total);
      const e = exactEdges(d, 0.05, 8, 11);
      expect(e.banker).toBeGreaterThan(0);
      expect(e.player).toBeGreaterThan(0);
    }
  });
  it('boxEdge matches the exact edges', () => {
    const e = exactEdges(8, 0.05, 8, 11);
    expect(boxEdge('banker', DEFAULT_RULES)).toBeCloseTo(e.banker, 5);
    expect(boxEdge('player', DEFAULT_RULES)).toBeCloseTo(e.player, 5);
    expect(boxEdge('tie', DEFAULT_RULES)).toBeCloseTo(e.tie, 5);
    expect(boxEdge('player_pair', DEFAULT_RULES)).toBeCloseTo(e.pair, 5);
  });
});

describe('shoe simulation (§20.8: 10⁶ coups with burn and penetration)', () => {
  it('matches the exact probabilities within 0.3 % and the Banker bet returns ≈ 98.94 %', () => {
    const rng = seededRng(20260924);
    const shoe = new BaccaratShoe(8);
    const n = 1_000_000;
    let b = 0;
    let p = 0;
    let t = 0;
    let pp = 0;
    let bankerReturn = 0;
    let burns = 0;
    for (let i = 0; i < n; i++) {
      if (shoe.needsShuffle(0.8)) {
        const s = shoe.shuffle(rng, true);
        expect(s.burned).toBeGreaterThanOrEqual(2);
        expect(s.burned).toBeLessThanOrEqual(11);
        burns++;
      }
      const c = dealCoup(() => shoe.draw());
      if (c.winner === 'banker') b++;
      else if (c.winner === 'player') p++;
      else t++;
      if (c.playerPair) pp++;
      bankerReturn += settleSlip({ banker: 20 }, c, DEFAULT_RULES).totalReturn;
    }
    const e = exactEdges(8, 0.05, 8, 11);
    expect(Math.abs(b / n - e.pBanker)).toBeLessThan(0.003);
    expect(Math.abs(p / n - e.pPlayer)).toBeLessThan(0.003);
    expect(Math.abs(t / n - e.pTie)).toBeLessThan(0.003);
    expect(Math.abs(pp / n - 31 / 415)).toBeLessThan(0.003);
    expect(Math.abs(bankerReturn / (20 * n) - (1 - e.banker))).toBeLessThan(0.003);
    expect(burns).toBeGreaterThan(10_000);
  });
});

describe('limits (§20.4)', () => {
  const l = slipLimits({ max: 1000, minBet: 1, commission: 0.05, sideMaxFraction: 0.25 });
  it('derived limits', () => {
    expect(l).toMatchObject({ step: 20, sideMax: 250, max: 1000, minTotal: 0, pairs: true });
    expect(boxMin('banker', l)).toBe(20);
    expect(boxMin('banker', slipLimits({ max: 1000, minBet: 30, commission: 0.05, sideMaxFraction: 0.25 }))).toBe(40);
    expect(boxMin('player', l)).toBe(1);
  });
  it('Banker must be a multiple of the step', () => {
    expect(checkAdd({}, 'banker', 30, l)).toEqual({ code: 'banker_step', step: 20 });
    expect(checkAdd({}, 'banker', 40, l)).toBeUndefined();
  });
  it('side bets ≤ max × fraction each, total ≤ max', () => {
    expect(checkAdd({ tie: 200 }, 'tie', 51, l)).toEqual({ code: 'side_max', max: 250 });
    expect(checkAdd({ tie: 250 }, 'player_pair', 250, l)).toBeUndefined();
    expect(checkAdd({ player: 900 }, 'tie', 101, l)).toEqual({ code: 'total_max', max: 1000 });
    expect(checkAdd({}, 'player', 0, l)).toEqual({ code: 'invalid_amount' });
    expect(checkAdd({}, 'player', 1.5, l)).toEqual({ code: 'invalid_amount' });
  });
  it('pairs off', () => {
    const off = slipLimits({ max: 1000, minBet: 1, commission: 0.05, sideMaxFraction: 0.25, pairs: false });
    expect(checkAdd({}, 'banker_pair', 5, off)).toEqual({ code: 'pairs_off' });
    expect(maxAddable({}, 'player_pair', off)).toBe(0);
  });
  it('maxAddable snaps the Banker room', () => {
    expect(maxAddable({ player: 950 }, 'banker', l)).toBe(40);
    expect(maxAddable({ player: 990 }, 'banker', l)).toBe(0);
    expect(maxAddable({ tie: 100 }, 'tie', l)).toBe(150);
  });
  it('High Roller minimum per coup applies at Deal', () => {
    const hr = slipLimits({ max: 2000, minBet: 1, commission: 0.05, sideMaxFraction: 0.25, minTotal: 100 });
    expect(checkSlip({ player: 50 }, hr, true)).toEqual({ code: 'min_total', min: 100 });
    expect(checkSlip({ player: 60, banker: 40 }, hr, true)).toBeUndefined();
  });
  it('Rebet is fitted into the current limits', () => {
    const small = slipLimits({ max: 100, minBet: 1, commission: 0.05, sideMaxFraction: 0.25 });
    expect(fitSlip({ banker: 200, tie: 50 }, small)).toEqual({ banker: 100 });
    expect(fitSlip({ player: 30, banker: 59, tie: 50 }, small)).toEqual({ player: 30, banker: 40, tie: 25 });
  });
});

describe('reservation (§20.6)', () => {
  it('smallest worst case: Player 1 → the house can lose 1', () => {
    expect(tableExposure([{ player: 1 }], DEFAULT_RULES)).toBe(1);
    expect(worstCaseReturn({ player: 1 }, DEFAULT_RULES)).toBe(2);
  });
  it('max over the 12 classes of the SUM over bettors', () => {
    const slips: Slip[] = [{ player: 100 }, { banker: 100 }];
    // Player wins: +100 −100 = 0; Banker wins: −100 + 95 = −5 → house max loss 0... ties push.
    expect(tableExposure(slips, DEFAULT_RULES)).toBe(0);
    expect(tableExposure([{ tie: 10, player_pair: 10, banker_pair: 10 }], DEFAULT_RULES)).toBe(80 + 110 + 110 - 0);
  });
  it('per-ticket worst case covers the tie + both pairs class', () => {
    expect(worstCaseReturn({ player: 100, tie: 10, player_pair: 5, banker_pair: 5 }, DEFAULT_RULES)).toBe(200 + 60 + 60);
  });
});

describe('shoe and burn (§20.1)', () => {
  it('burn value: A 1, 2–9 face, tens and faces 10', () => {
    expect(burnValue(parseCard('AS'))).toBe(1);
    expect(burnValue(parseCard('9S'))).toBe(9);
    expect(burnValue(parseCard('QS'))).toBe(10);
  });
  it('shuffle burns 1 + value; needsShuffle at penetration', () => {
    const shoe = new BaccaratShoe(8);
    expect(shoe.needsShuffle(0.8)).toBe(true);
    const r = shoe.shuffle(seededRng(3), true);
    expect(r.burned).toBe(1 + burnValue(r.shown!));
    expect(shoe.dealt).toBe(r.burned);
    expect(shoe.size).toBe(416);
    expect(shoe.needsShuffle(0.8)).toBe(false);
    while (shoe.dealt < 333) shoe.draw();
    expect(shoe.needsShuffle(0.8)).toBe(true);
    const off = new BaccaratShoe(1);
    expect(off.shuffle(seededRng(1), false).burned).toBe(0);
  });
  it('serializes and restores exactly', () => {
    const shoe = new BaccaratShoe(8);
    shoe.shuffle(seededRng(9), true);
    for (let i = 0; i < 30; i++) shoe.draw();
    const back = BaccaratShoe.fromData(JSON.parse(JSON.stringify(shoe.toData())), 8);
    expect(back.dealt).toBe(shoe.dealt);
    for (let i = 0; i < 50; i++) expect(back.draw()).toEqual(shoe.draw());
    expect(BaccaratShoe.fromData(shoe.toData(), 6).isNew).toBe(true);
    expect(BaccaratShoe.fromData({ d: 8, c: 'XX', p: 0 }, 8).isNew).toBe(true);
    expect(BaccaratShoe.fromData(undefined, 8).isNew).toBe(true);
  });
});

describe('bead plate and tie run (§20.7)', () => {
  const tie = coupFromCards(cards('2C KD 3H 3S 8D'));
  const pl = coupFromCards(cards('8S 9D KH 7C'));
  it('tie run fires at 3 and continues (4th tie → again), resets on a non-tie', () => {
    const h = new BeadPlate(60);
    expect(tieRunFires(h.record(tie), 3)).toBe(false);
    expect(tieRunFires(h.record(tie), 3)).toBe(false);
    expect(tieRunFires(h.record(tie), 3)).toBe(true);
    expect(tieRunFires(h.record(tie), 3)).toBe(true);
    expect(h.record(pl)).toBe(0);
    expect(tieRunFires(5, 0)).toBe(false);
  });
  it('caps the plate, clears on a new shoe, round-trips', () => {
    const h = new BeadPlate(3);
    for (let i = 0; i < 5; i++) h.record(i % 2 ? tie : pl);
    expect(h.beads).toHaveLength(3);
    expect(h.stats).toEqual([3, 0, 2]);
    expect(h.coupNo).toBe(5);
    const back = BeadPlate.fromData(JSON.parse(JSON.stringify(h.toData())), 3);
    expect(back.beads).toEqual(h.beads);
    expect(back.tieRun).toBe(h.tieRun);
    h.clear();
    expect(h.beads).toHaveLength(0);
    expect(h.coupNo).toBe(5);
  });
});

describe('table clock (§20.5)', () => {
  it('window from the first bet; all seated bettors Ready closes it early', () => {
    const c = new CoupClock({ betTicks: 400, noMoreBetsTicks: 20, shuffleTicks: 40, revealTicks: 80, resultTicks: 60 });
    expect(c.update(0, [], ['a', 'b'], () => false)).toBeUndefined();
    c.onBet(10);
    expect(c.remaining(10)).toBe(400);
    expect(c.update(11, ['a', 'b'], ['a', 'b'], () => false)).toBeUndefined();
    c.setReady('a');
    expect(c.update(12, ['a', 'b'], ['a', 'b'], () => false)).toBeUndefined();
    // b disconnected: their bets ride along, a alone decides
    expect(c.update(13, ['a', 'b'], ['a'], () => false)).toEqual({ to: 'no_more_bets' });
    expect(c.canBet()).toBe(false);
    expect(c.update(33, ['a', 'b'], ['a'], () => true)).toEqual({ to: 'shuffle' });
    expect(c.update(73, ['a', 'b'], ['a'], () => true)).toEqual({ to: 'reveal' });
    expect(c.update(153, ['a', 'b'], ['a'], () => true)).toEqual({ to: 'result' });
    expect(c.update(213, ['a', 'b'], ['a'], () => true)).toEqual({ to: 'betting' });
    expect(c.isReady('a')).toBe(false);
  });
  it('timer expiry', () => {
    const c = new CoupClock({ betTicks: 400, noMoreBetsTicks: 20, shuffleTicks: 40, revealTicks: 80, resultTicks: 60 });
    c.onBet(0);
    expect(c.update(399, ['a'], ['a'], () => false)).toBeUndefined();
    expect(c.update(400, ['a'], ['a'], () => false)).toEqual({ to: 'no_more_bets' });
    expect(c.update(420, ['a'], ['a'], () => false)).toEqual({ to: 'reveal' });
  });
  it('reveal frames: 4 cards 10 t apart, then the third cards', () => {
    expect(revealFrames(3, 3, false, 80).map((f) => [f.at, f.player, f.banker, f.note])).toEqual([
      [0, 1, 0, undefined],
      [10, 1, 1, undefined],
      [20, 2, 1, undefined],
      [30, 2, 2, undefined],
      [50, 3, 2, 'player_draws'],
      [70, 3, 3, 'banker_draws'],
    ]);
    expect(revealFrames(2, 2, true, 80).at(-1)).toMatchObject({ note: 'natural', player: 2, banker: 2 });
    expect(revealFrames(2, 3, false, 80).slice(4).map((f) => f.note)).toEqual(['player_stands', 'banker_draws']);
  });
});
