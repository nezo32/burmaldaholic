import { describe, expect, it } from 'vitest';
import { type Rng, seededRng } from '../../../core/logic/rng';
import { pc, pcs } from './cards';
import { type BotView, botView, decideBot, legalize, madeCategory, pickTier, positionOf, samplesFor, sizeTo } from './bots';
import { applyAction, startHand } from './engine';
import { TOP40_CHEN, chen, chenThreshold, equity } from './equity';

/** RNG that replays the given values (then repeats the last). */
const seq = (...v: number[]): Rng => {
  let i = 0;
  return { next: () => v[Math.min(i++, v.length - 1)]! };
};
const NEVER = seq(0.99);

function view(o: Omit<Partial<BotView>, 'hole' | 'board'> & { hole: string; board?: string }): BotView {
  const { hole, board, ...rest } = o;
  return {
    hole: pcs(hole),
    board: pcs(board ?? ''),
    street: 'preflop',
    pot: 15,
    toCall: 10,
    stack: 1000,
    bet: 0,
    currentBet: 10,
    bb: 10,
    canCheck: false,
    canRaise: true,
    minRaiseTo: 20,
    maxRaiseTo: 1000,
    position: 'middle',
    limpers: 0,
    facingRaise: false,
    preflopRaiser: false,
    opponents: 2,
    ...rest,
  };
}

describe('Chen formula', () => {
  it.each([
    ['As Ah', 20],
    ['Ks Kh', 16],
    ['2s 2h', 5],
    ['5s 5h', 5],
    ['6s 6h', 6],
    ['As Ks', 12],
    ['As Kh', 10],
    ['Ts 9s', 8],
    ['Js Ts', 9],
    ['Qs Js', 9],
    ['7s 2h', -1],
    ['As 2h', 5],
    ['5s 4s', 6],
    ['Ks Jh', 7],
  ])('%s = %i', (h, score) => {
    const [a, b] = pcs(h);
    expect(chen(a!, b!)).toBe(score);
  });

  it('top-40 % threshold is a sensible Chen score', () => {
    const thr = TOP40_CHEN();
    expect(thr).toBe(chenThreshold(0.4));
    expect(thr).toBeGreaterThanOrEqual(4);
    expect(thr).toBeLessThanOrEqual(7);
  });
});

describe('Monte-Carlo equity', () => {
  it('AA vs one random hand ≈ 85 %', () => {
    const e = equity({ hole: pcs('As Ah'), board: [], opponents: 1, samples: 6000 }, seededRng(7));
    expect(e).toBeGreaterThan(0.82);
    expect(e).toBeLessThan(0.88);
  });
  it('72o vs one random hand ≈ 35 %', () => {
    const e = equity({ hole: pcs('7s 2h'), board: [], opponents: 1, samples: 6000 }, seededRng(8));
    expect(e).toBeGreaterThan(0.31);
    expect(e).toBeLessThan(0.39);
  });
  it('equity drops with more opponents', () => {
    const one = equity({ hole: pcs('Qs Jh'), board: [], opponents: 1, samples: 3000 }, seededRng(9));
    const four = equity({ hole: pcs('Qs Jh'), board: [], opponents: 4, samples: 3000 }, seededRng(9));
    expect(four).toBeLessThan(one - 0.2);
  });
  it('the nuts on the river is 100 %', () => {
    expect(equity({ hole: pcs('As Ks'), board: pcs('Qs Js Ts 2c 3d'), opponents: 3, samples: 300 }, seededRng(1))).toBe(1);
  });
  it('board royal flush always splits', () => {
    const e = equity({ hole: pcs('2c 3d'), board: pcs('As Ks Qs Js Ts'), opponents: 1, samples: 200 }, seededRng(2));
    expect(e).toBeCloseTo(0.5, 5);
  });
  it('a top-40 % range is tougher than random hands', () => {
    const any = equity({ hole: pcs('Ks 9h'), board: [], opponents: 1, samples: 5000 }, seededRng(3));
    const tight = equity({ hole: pcs('Ks 9h'), board: [], opponents: 1, samples: 5000, ranges: [TOP40_CHEN()] }, seededRng(3));
    expect(tight).toBeLessThan(any - 0.04);
  });
});

describe('position', () => {
  it('6-max positions', () => {
    const s = startHand(
      Array.from({ length: 6 }, (_, i) => ({ id: `p${i}`, human: false, stack: 1000 })),
      seededRng(1),
      { sb: 5, bb: 10, button: 0, rake: { percent: 0, capBb: 0, noFlopNoDrop: true } },
    );
    expect([0, 1, 2, 3, 4, 5].map((i) => positionOf(s, i))).toEqual(['late', 'sb', 'bb', 'early', 'early', 'late']);
  });
  it('heads-up: button is the small blind', () => {
    const s = startHand(
      [
        { id: 'a', human: false, stack: 100 },
        { id: 'b', human: false, stack: 100 },
      ],
      seededRng(1),
      { sb: 1, bb: 2, button: 1, rake: { percent: 0, capBb: 0, noFlopNoDrop: true } },
    );
    expect([positionOf(s, 0), positionOf(s, 1)]).toEqual(['bb', 'sb']);
  });
});

describe('Fish', () => {
  it('folds junk, checks it in the big blind', () => {
    expect(decideBot('fish', view({ hole: '7s 2h' }), undefined, NEVER)).toEqual({ type: 'fold' });
    expect(decideBot('fish', view({ hole: '7s 2h', canCheck: true, toCall: 0 }), undefined, NEVER)).toEqual({ type: 'check' });
  });
  it('limps with C ≥ 4', () => expect(decideBot('fish', view({ hole: '9s 8h' }), undefined, NEVER)).toEqual({ type: 'call' }));
  it('raises ~3 BB (±10 %) only with C ≥ 12', () => {
    const a = decideBot('fish', view({ hole: 'As Ks' }), undefined, seq(0.5));
    expect(a).toEqual({ type: 'raise', to: 30 });
    const lo = decideBot('fish', view({ hole: 'As Ks' }), undefined, seq(0));
    expect(lo).toEqual({ type: 'raise', to: 27 });
  });
  it('never folds a pocket pair to a raise ≤ 10 BB', () => {
    const v = view({ hole: '2s 2h', facingRaise: true, currentBet: 100, toCall: 100, stack: 300 });
    expect(decideBot('fish', v, undefined, NEVER)).toEqual({ type: 'call' });
    expect(decideBot('fish', { ...v, currentBet: 120, toCall: 120 }, undefined, NEVER)).toEqual({ type: 'fold' });
  });
  it('calls a raise ≤ 20 % of stack with C ≥ 6', () => {
    const v = view({ hole: 'Ks Th', facingRaise: true, currentBet: 40, toCall: 40, stack: 400 });
    expect(decideBot('fish', v, undefined, NEVER)).toEqual({ type: 'call' });
    expect(decideBot('fish', { ...v, stack: 100 }, undefined, NEVER)).toEqual({ type: 'fold' });
  });
  it('postflop: calls with a pair, bets two pair, check/folds air', () => {
    const post = { street: 'flop' as const, pot: 100, currentBet: 0, toCall: 0, canCheck: true, minRaiseTo: 10 };
    expect(decideBot('fish', view({ hole: 'Ks Qh', board: 'Kd 7c 2s', ...post, toCall: 50, currentBet: 50, canCheck: false }), undefined, NEVER)).toEqual({
      type: 'call',
    });
    expect(decideBot('fish', view({ hole: 'Ks 7h', board: 'Kd 7c 2s', ...post }), undefined, seq(0.5))).toEqual({ type: 'raise', to: 50 });
    expect(decideBot('fish', view({ hole: 'As Qh', board: 'Kd 7c 2s', ...post }), undefined, NEVER)).toEqual({ type: 'check' });
    expect(decideBot('fish', view({ hole: 'As Qh', board: 'Kd 7c 2s', ...post, toCall: 50, currentBet: 50, canCheck: false }), undefined, NEVER)).toEqual({
      type: 'fold',
    });
    // 5 % bluff
    expect(decideBot('fish', view({ hole: 'As Qh', board: 'Kd 7c 2s', ...post }), undefined, seq(0.5, 0.01))).toEqual({ type: 'raise', to: 50 });
  });
  it('a board pair does not count as a made hand', () => {
    expect(madeCategory(pcs('As Qh'), pcs('7d 7c 2s'))).toBe(0);
    expect(madeCategory(pcs('As 7h'), pcs('7d 7c 2s'))).toBe(3);
  });
});

describe('Regular', () => {
  it('open-raises by position thresholds (+1 BB per limper)', () => {
    const t9s = view({ hole: 'Ts 9s', position: 'early' }); // C 8
    expect(decideBot('regular', t9s, undefined, NEVER)).toEqual({ type: 'raise', to: 30 });
    const s76 = view({ hole: '7s 6s', position: 'early' }); // C 7
    expect(decideBot('regular', s76, undefined, NEVER)).toEqual({ type: 'fold' });
    expect(decideBot('regular', { ...s76, position: 'middle', limpers: 2 }, undefined, NEVER)).toEqual({ type: 'raise', to: 50 });
    const bb = view({ hole: '7s 2h', position: 'bb', canCheck: true, toCall: 0 });
    expect(decideBot('regular', bb, undefined, NEVER)).toEqual({ type: 'check' });
  });
  it('facing a raise: 3-bet ≥ 11, call ≥ 9, else fold', () => {
    const f = { facingRaise: true, currentBet: 30, toCall: 30 };
    expect(decideBot('regular', view({ hole: 'As Ks', ...f }), undefined, NEVER)).toEqual({ type: 'raise', to: 90 });
    expect(decideBot('regular', view({ hole: 'As Js', ...f }), undefined, NEVER)).toEqual({ type: 'call' });
    expect(decideBot('regular', view({ hole: 'Ts 9s', ...f }), undefined, NEVER)).toEqual({ type: 'fold' });
  });
  it('postflop by equity and pot odds', () => {
    const facing = { street: 'turn' as const, pot: 200, currentBet: 100, toCall: 100, minRaiseTo: 200 };
    expect(decideBot('regular', view({ hole: 'As Ks', ...facing }), 0.7, NEVER)).toEqual({ type: 'raise', to: 298 });
    // pot odds 100 / 300 = 0.333: call needs E ≥ 0.383
    expect(decideBot('regular', view({ hole: 'As Ks', ...facing }), 0.4, NEVER)).toEqual({ type: 'call' });
    expect(decideBot('regular', view({ hole: 'As Ks', ...facing }), 0.36, NEVER)).toEqual({ type: 'fold' });
  });
  it('continuation-bets 50 % pot on the flop 30 % of the time as preflop raiser', () => {
    const v = view({ hole: '7s 2h', street: 'flop', pot: 60, currentBet: 0, toCall: 0, canCheck: true, minRaiseTo: 10, preflopRaiser: true });
    expect(decideBot('regular', v, 0.2, seq(0.1, 0.99))).toEqual({ type: 'raise', to: 30 });
    expect(decideBot('regular', v, 0.2, seq(0.5, 0.99))).toEqual({ type: 'check' });
  });
  it('10 % of the time takes the next-lower action', () => {
    const f = { facingRaise: true, currentBet: 30, toCall: 30 };
    expect(decideBot('regular', view({ hole: 'As Ks', ...f }), undefined, seq(0.05))).toEqual({ type: 'call' });
  });
});

describe('Shark', () => {
  it('thresholds are one lower than the regular', () => {
    expect(decideBot('shark', view({ hole: '7s 6s', position: 'early' }), undefined, NEVER)).toEqual({ type: 'raise', to: 30 });
    const f = { facingRaise: true, currentBet: 30, toCall: 30 };
    expect(decideBot('shark', view({ hole: 'Ad Qd', ...f }), undefined, NEVER)).toEqual({ type: 'raise', to: 90 }); // C 10
  });
  it('widens the calling range vs loose humans (VPIP > 40 %)', () => {
    const f = { facingRaise: true, currentBet: 30, toCall: 30 };
    const kts = view({ hole: 'Ks Jh', ...f }); // C 7 (8 - 1 gap)
    expect(decideBot('shark', kts, undefined, NEVER)).toEqual({ type: 'fold' });
    expect(decideBot('shark', { ...kts, loosestHumanVpip: 0.6 }, undefined, NEVER)).toEqual({ type: 'call' });
  });
  it('3-bet bluffs suited connectors 8 % of the time', () => {
    const f = { facingRaise: true, currentBet: 30, toCall: 30 };
    expect(decideBot('shark', view({ hole: '5s 4s', ...f }), undefined, seq(0.05))).toEqual({ type: 'raise', to: 90 });
    expect(decideBot('shark', view({ hole: '5s 4s', ...f }), undefined, NEVER)).toEqual({ type: 'fold' });
  });
  it('monsters: overbet all-in 20 %, slowplay 15 %, else raise 75 % pot', () => {
    const v = view({ hole: 'As Ks', street: 'river', pot: 200, currentBet: 0, toCall: 0, canCheck: true, minRaiseTo: 10 });
    expect(decideBot('shark', v, 0.9, seq(0.1))).toEqual({ type: 'allin' });
    expect(decideBot('shark', v, 0.9, seq(0.3))).toEqual({ type: 'check' });
    expect(decideBot('shark', v, 0.9, seq(0.5))).toEqual({ type: 'raise', to: 150 });
  });
  it('bets 50–75 % pot with E ≥ 0.6 and semi-bluffs draws on the flop', () => {
    const v = view({ hole: 'As Ks', street: 'flop', pot: 100, currentBet: 0, toCall: 0, canCheck: true, minRaiseTo: 10 });
    expect(decideBot('shark', v, 0.7, seq(0))).toEqual({ type: 'raise', to: 50 });
    expect(decideBot('shark', v, 0.7, seq(0.99))).toEqual({ type: 'raise', to: 75 });
    expect(decideBot('shark', v, 0.35, seq(0.2, 0.5))).toEqual({ type: 'raise', to: 63 });
    expect(decideBot('shark', v, 0.35, seq(0.9))).toEqual({ type: 'check' });
  });
  it('calls when E ≥ pot odds, folds otherwise', () => {
    const v = view({ hole: 'As Ks', street: 'turn', pot: 200, currentBet: 100, toCall: 100, minRaiseTo: 200 });
    expect(decideBot('shark', v, 0.34, NEVER)).toEqual({ type: 'call' });
    expect(decideBot('shark', v, 0.3, NEVER)).toEqual({ type: 'fold' });
  });
});

describe('helpers', () => {
  it('legalize clamps and downgrades', () => {
    const v = view({ hole: 'As Ks', minRaiseTo: 40, maxRaiseTo: 300 });
    expect(legalize(v, { type: 'raise', to: 25 })).toEqual({ type: 'raise', to: 40 });
    expect(legalize(v, { type: 'raise', to: 900 })).toEqual({ type: 'allin' });
    expect(legalize({ ...v, canRaise: false }, { type: 'raise', to: 100 })).toEqual({ type: 'call' });
    expect(legalize({ ...v, toCall: 0, canCheck: true }, { type: 'fold' })).toEqual({ type: 'check' });
  });
  it('sizeTo: bet is a fraction of the pot, raise adds the called amount', () => {
    expect(sizeTo(view({ hole: 'As Ks', pot: 100, currentBet: 0, toCall: 0 }), 0.5)).toBe(50);
    expect(sizeTo(view({ hole: 'As Ks', pot: 150, currentBet: 50, toCall: 50 }), 1)).toBe(250);
  });
  it('samples: fish never simulate, others only after the flop', () => {
    const cfg = { regularSamples: 200, sharkSamples: 500 };
    expect(samplesFor('fish', 'river', cfg)).toBe(0);
    expect(samplesFor('regular', 'preflop', cfg)).toBe(0);
    expect(samplesFor('regular', 'flop', cfg)).toBe(200);
    expect(samplesFor('shark', 'turn', cfg)).toBe(500);
  });
  it('pickTier follows the mix', () => {
    const rng = seededRng(5);
    const n = { fish: 0, regular: 0, shark: 0 };
    for (let i = 0; i < 10000; i++) n[pickTier(rng, [50, 40, 10])]++;
    expect(n.fish / 10000).toBeCloseTo(0.5, 1);
    expect(n.shark / 10000).toBeCloseTo(0.1, 1);
    expect(pickTier(rng, [0, 0, 0])).toBe('regular');
    expect(pickTier(rng, [0, 0, 5])).toBe('shark');
  });
  it('botView reports public info only', () => {
    const s = startHand(
      [
        { id: 'a', human: true, stack: 1000 },
        { id: 'b', human: false, stack: 1000 },
        { id: 'c', human: false, stack: 1000 },
      ],
      seededRng(3),
      { sb: 5, bb: 10, button: 0, rake: { percent: 0, capBb: 0, noFlopNoDrop: true } },
    );
    applyAction(s, { type: 'call' });
    const v = botView(s, 1, new Map([['a', 0.5]]));
    expect(v.hole).toEqual(s.players[1]!.hole);
    expect(v.limpers).toBe(1);
    expect(v.toCall).toBe(5);
    expect(v.pot).toBe(25);
    expect(v.loosestHumanVpip).toBe(0.5);
    expect(pc('As')).toBe(48);
  });
});
