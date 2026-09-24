import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import { FULL_DECK, type PCard, pcs } from './cards';
import { type Action, type HandPlayerInit, type HandState, applyAction, coerce, legal, playOut, startHand } from './engine';

const RAKE = { percent: 0.05, capBb: 3, noFlopNoDrop: true };
const NO_RAKE = { percent: 0, capBb: 0, noFlopNoDrop: true };

/** Build a deck so player i gets holes[i] and the board comes out as given. */
function rig(n: number, button: number, holes: string[], board: string): PCard[] {
  const h = holes.map(pcs);
  const b = pcs(board);
  const used = new Set<PCard>([...h.flat(), ...b]);
  const rest = FULL_DECK.filter((c) => !used.has(c));
  const deck: PCard[] = [];
  for (let r = 0; r < 2; r++) for (let k = 1; k <= n; k++) deck.push(h[(button + k) % n]![r]!);
  deck.push(rest.shift()!, b[0]!, b[1]!, b[2]!, rest.shift()!, b[3]!, rest.shift()!, b[4]!);
  return [...deck, ...rest];
}

const players = (...stacks: number[]): HandPlayerInit[] => stacks.map((stack, i) => ({ id: `p${i}`, human: true, stack }));

function hand(stacks: number[], o: { button?: number; bb?: number; holes?: string[]; board?: string; rake?: typeof RAKE; humans?: boolean[] } = {}): HandState {
  const ps = players(...stacks).map((p, i) => ({ ...p, human: o.humans ? o.humans[i]! : true }));
  const button = o.button ?? 0;
  const bb = o.bb ?? 10;
  const deck = o.holes ? rig(stacks.length, button, o.holes, o.board ?? '2c 7d 9h Js Kd') : undefined;
  return startHand(ps, seededRng(1), { sb: bb / 2, bb, button, rake: o.rake ?? NO_RAKE, deck });
}

const act = (s: HandState, ...as: Action[]) => as.forEach((a) => applyAction(s, a));
const F: Action = { type: 'fold' };
const X: Action = { type: 'check' };
const C: Action = { type: 'call' };
const R = (to: number): Action => ({ type: 'raise', to });
const A: Action = { type: 'allin' };
const chipsIn = (s: HandState) => s.players.reduce((a, p) => a + p.stack, 0) + (s.result?.rake ?? 0);

describe('blinds and order', () => {
  it('3-handed: SB left of button, BB next, button first to act preflop', () => {
    const s = hand([1000, 1000, 1000], { button: 0 });
    expect([s.sbIndex, s.bbIndex]).toEqual([1, 2]);
    expect(s.players.map((p) => p.bet)).toEqual([0, 5, 10]);
    expect(s.toAct).toBe(0);
    expect(s.players.every((p) => p.hole.length === 2)).toBe(true);
  });

  it('6-handed: action starts left of the BB (UTG)', () => {
    const s = hand([500, 500, 500, 500, 500, 500], { button: 3 });
    expect([s.sbIndex, s.bbIndex, s.toAct]).toEqual([4, 5, 0]);
  });

  it('heads-up: button posts SB and acts first preflop, BB first after the flop', () => {
    const s = hand([1000, 1000], { button: 1 });
    expect(s.sbIndex).toBe(1);
    expect(s.bbIndex).toBe(0);
    expect(s.toAct).toBe(1);
    act(s, C, X);
    expect(s.street).toBe('flop');
    expect(s.toAct).toBe(0);
  });

  it('big blind has the option after limps', () => {
    const s = hand([1000, 1000, 1000]);
    act(s, C, C);
    expect(s.street).toBe('preflop');
    expect(s.toAct).toBe(2);
    expect(legal(s).canCheck).toBe(true);
    expect(legal(s).canRaise).toBe(true);
    act(s, X);
    expect(s.street).toBe('flop');
    expect(s.board).toHaveLength(3);
    expect(s.toAct).toBe(1); // first live player left of the button
  });

  it('a player who cannot cover the blind posts all-in', () => {
    const s = hand([1000, 3, 1000]);
    expect(s.players[1]!.bet).toBe(3);
    expect(s.players[1]!.allIn).toBe(true);
    expect(s.events[0]).toMatchObject({ type: 'blind', blind: 'sb', amount: 3, allIn: true });
  });

  it('burns one card before each street', () => {
    const s = hand([1000, 1000, 1000], { holes: ['As Ad', 'Ks Kd', 'Qs Qd'], board: '2c 7d 9h Js 3d' });
    act(s, C, C, X);
    expect(s.board).toEqual(pcs('2c 7d 9h'));
    act(s, X, X, X);
    expect(s.board).toEqual(pcs('2c 7d 9h Js'));
    act(s, X, X, X);
    expect(s.board).toEqual(pcs('2c 7d 9h Js 3d'));
  });
});

describe('no-limit betting', () => {
  it('min bet is the BB and min raise is the last full increment', () => {
    const s = hand([1000, 1000, 1000]);
    expect(legal(s).minRaiseTo).toBe(20);
    expect(() => applyAction(s, R(15))).toThrow();
    act(s, R(30)); // +20
    expect(legal(s).minRaiseTo).toBe(50);
    act(s, R(80)); // +50
    expect(legal(s).minRaiseTo).toBe(130);
    act(s, F, C);
    expect(s.street).toBe('flop');
    expect(legal(s).minRaiseTo).toBe(10);
    expect(legal(s).isBet).toBe(true);
  });

  it('a short all-in raise does not reopen betting for players who already acted', () => {
    // p0 button, p1 SB, p2 BB, p3 UTG (short)
    const s = hand([1000, 1000, 1000, 1000], { button: 0 });
    act(s, C, C, C, X); // limped preflop (UTG=3, button 0, SB 1, BB 2)
    expect(s.street).toBe('flop');
    s.players[3]!.stack = 150; // make UTG short for the test
    act(s, R(100)); // p1 bets 100
    act(s, C); // p2 calls
    act(s, A); // p3 all-in 150: +50 < 100 = not a full raise
    expect(s.currentBet).toBe(150);
    expect(s.toAct).toBe(0);
    expect(legal(s).canRaise).toBe(true); // p0 has not acted yet
    act(s, C);
    expect(s.toAct).toBe(1);
    expect(legal(s).canRaise).toBe(false); // p1 bet, facing only a short all-in
    expect(legal(s).toCall).toBe(50);
    expect(() => applyAction(s, R(300))).toThrow();
    expect(coerce(s, R(300))).toEqual(C);
    act(s, C);
    expect(legal(s).canRaise).toBe(false);
    act(s, C);
    expect(s.street).toBe('turn');
  });

  it('a full raise after a short all-in reopens the action', () => {
    const s = hand([1000, 1000, 1000, 1000], { button: 0 });
    act(s, C, C, C, X);
    s.players[3]!.stack = 150;
    act(s, R(100), C, A); // p3 short all-in to 150
    act(s, R(400)); // p0 full raise
    expect(s.toAct).toBe(1);
    expect(legal(s).canRaise).toBe(true);
  });

  it('uncalled bet goes back when everyone folds', () => {
    const s = hand([1000, 1000, 1000]);
    act(s, R(300), F, F);
    expect(s.complete).toBe(true);
    expect(s.result!.uncontested).toBe(true);
    expect(s.result!.uncalled).toEqual({ player: 0, amount: 290 });
    expect(s.players.map((p) => p.stack)).toEqual([1015, 995, 990]);
    expect(s.result!.shown).toEqual([]);
  });

  it('walk: everyone folds to the big blind', () => {
    const s = hand([1000, 1000, 1000]);
    act(s, F, F);
    expect(s.players.map((p) => p.stack)).toEqual([1000, 995, 1005]);
  });

  it('coerce turns illegal fallbacks into legal actions', () => {
    const s = hand([1000, 1000, 1000]);
    expect(coerce(s, X)).toEqual(F);
    expect(coerce(s, R(5))).toEqual(R(20));
    expect(coerce(s, R(99999))).toEqual(R(1000));
    act(s, C, C);
    expect(coerce(s, F)).toEqual(X);
  });
});

describe('showdown and pots', () => {
  it('best hand wins at showdown; losers muck, last aggressor shows first', () => {
    const s = hand([1000, 1000, 1000], { holes: ['As Ad', 'Ks Kd', '7c 2h'], board: '3c 8d 9h Js 4d' });
    act(s, R(30), C, F); // p0 raises, p1 calls, p2 folds
    act(s, X, R(50), C); // flop: p1 checks, p0 bets 50, p1 calls
    act(s, X, X, X, X);
    expect(s.complete).toBe(true);
    const r = s.result!;
    expect(r.pots).toHaveLength(1);
    expect(r.pots[0]!.winners).toEqual([0]);
    expect(r.pots[0]!.amount).toBe(30 + 30 + 10 + 50 + 50);
    expect(r.shown).toEqual([0]); // aggressor shows, loser mucks
    expect(s.players.map((p) => p.stack)).toEqual([1090, 920, 990]);
  });

  it('side pots: short all-in wins the main pot, the other the side pot', () => {
    const s = hand([100, 1000, 1000], {
      button: 2,
      holes: ['As Ad', 'Ks Kd', 'Qs Qd'],
      board: '2c 7d 9h Js 3d',
    });
    // button 2: SB 0 (100 chips), BB 1, button acts first
    act(s, R(300), A, C); // p2 raises 300, p0 all-in 100, p1 calls 300
    expect(s.street).toBe('flop');
    act(s, X, X, X, X, X, X);
    const r = s.result!;
    expect(r.pots.map((p) => p.amount)).toEqual([300, 400]);
    expect(r.pots.map((p) => p.winners)).toEqual([[0], [1]]);
    expect(s.players.map((p) => p.stack)).toEqual([300, 1100, 700]);
    expect(chipsIn(s)).toBe(2100);
  });

  it('split pot: odd chip to the first winner left of the button', () => {
    const s = hand([1000, 1000, 1000], {
      button: 0,
      holes: ['7c 2d', '3s 4d', '3h 4c'],
      board: 'Ts Js Qd Kc Ah', // broadway on board - everyone ties...
    });
    act(s, C, C, X); // p0 calls, p1 SB completes, BB checks: pot 30
    act(s, X, X, X, X, X, X, X, X, X);
    const r = s.result!;
    expect(r.pots[0]!.winners).toEqual([1, 2, 0]); // left of button first
    expect(r.pots[0]!.shares).toEqual([10, 10, 10]);
  });

  it('odd chip with two winners', () => {
    // BB 10, SB 5, pot made odd by an all-in blind
    const s = hand([1000, 1000, 7], {
      button: 0,
      holes: ['As Ks', 'Ad Kd', '2c 3h'],
      board: '5s 9h Tc Jd 4c',
    });
    // p2 BB all-in for 7; p0 calls 10, p1 completes to 10
    act(s, C, C);
    act(s, X, X, X, X, X, X);
    const r = s.result!;
    // main pot 21 (7 × 3) split p0/p1 -> first left of button (p1) gets the odd chip; side pot 6
    expect(r.pots[0]!.amount).toBe(21);
    expect(r.pots[0]!.winners).toEqual([1, 0]);
    expect(r.pots[0]!.shares).toEqual([11, 10]);
    expect(r.pots[1]!.amount).toBe(6);
    expect(chipsIn(s)).toBe(2007);
  });

  it('all-in preflop runs out the board and shows every hand', () => {
    const s = hand([500, 500], { button: 0, holes: ['As Ad', 'Kh Kd'], board: '2c 7d 9h Js 3d' });
    act(s, A, C);
    expect(s.complete).toBe(true);
    expect(s.board).toHaveLength(5);
    expect(s.result!.shown.sort()).toEqual([0, 1]);
    expect(s.players.map((p) => p.stack)).toEqual([1000, 0]);
  });

  it('rake: 2 humans after the flop, capped; none with one human vs bots', () => {
    const raked = hand([1000, 1000], { rake: RAKE, holes: ['As Ad', 'Kh Kd'], board: '2c 7d 9h Js 3d' });
    act(raked, A, C);
    expect(raked.result!.rake).toBe(30); // min(floor(2000 × 5 %), 3 × 10)
    expect(raked.players[0]!.stack).toBe(1970);

    const vsBot = hand([1000, 1000], { rake: RAKE, humans: [true, false], holes: ['As Ad', 'Kh Kd'], board: '2c 7d 9h Js 3d' });
    act(vsBot, A, C);
    expect(vsBot.result!.rake).toBe(0);

    const noFlop = hand([1000, 1000, 1000], { rake: RAKE });
    act(noFlop, R(100), F, R(300));
    expect(noFlop.complete).toBe(false);
    act(noFlop, F);
    expect(noFlop.sawFlop).toBe(false);
    expect(noFlop.result!.rake).toBe(0);
  });
});

describe('random play invariants', () => {
  it('chips are conserved and every hand completes (5 000 random hands)', () => {
    const rng = seededRng(42);
    for (let n = 0; n < 5000; n++) {
      const count = 2 + Math.floor(rng.next() * 5);
      const stacks = Array.from({ length: count }, () => 1 + Math.floor(rng.next() * 400));
      const humans = stacks.map(() => rng.next() < 0.6);
      const s = startHand(
        stacks.map((stack, i) => ({ id: `p${i}`, human: humans[i]!, stack })),
        rng,
        { sb: 1, bb: 2, button: Math.floor(rng.next() * count), rake: RAKE },
      );
      let guard = 0;
      while (!s.complete) {
        if (++guard > 500) throw new Error('hand did not finish');
        const l = legal(s);
        const r = rng.next();
        const a: Action = r < 0.15 ? F : r < 0.55 ? (l.canCheck ? X : C) : r < 0.85 ? R(l.minRaiseTo + Math.floor(rng.next() * 50)) : A;
        applyAction(s, coerce(s, a));
      }
      const total = stacks.reduce((a, b) => a + b, 0);
      expect(chipsIn(s)).toBe(total);
      expect(s.players.every((p) => p.stack >= 0)).toBe(true);
      const potSum = s.result!.pots.reduce((a, p) => a + p.amount, 0);
      expect(s.result!.pots.reduce((a, p) => a + p.shares.reduce((x, y) => x + y, 0) + p.rake, 0)).toBe(potSum);
      for (const p of s.result!.pots) expect(p.rake).toBeLessThanOrEqual(Math.floor(p.amount * 0.05));
    }
  });
});

describe('playOut: drawn outcome of a hand in progress (review M1)', () => {
  const leave = (s: HandState): Action => (legal(s).canCheck ? X : F);

  it('plays a copy to the end on the dealt deck; the hand is not changed', () => {
    const s = hand([1000, 1000], { holes: ['As Ah', '7c 2d'], board: '2c 7d 9h Js Kd' });
    act(s, C, X); // limp, check: flop comes
    const before = JSON.stringify(s);
    const end = playOut(s, leave)!;
    expect(JSON.stringify(s)).toBe(before);
    expect(end.complete).toBe(true);
    expect(end.board).toHaveLength(5); // checked down: the board already in the deck
    // p0 (button/SB heads-up) has aces, but p1's 7-2 makes two pair on 2c 7d: p1 wins
    const i1 = end.players.findIndex((p) => p.id === 'p1');
    expect(end.players[i1]!.stack).toBe(1010);
    expect(chipsIn(end)).toBe(2000);
  });

  it('a human facing a bet folds in the play-out: quitting then loses what is already in', () => {
    const s = hand([1000, 1000], { holes: ['As Ah', '7c 2d'], humans: [false, true] });
    act(s, R(300)); // bot raises, the human faces 290 more
    const end = playOut(s, (h, i) => (h.players[i]!.human ? leave(h) : C))!;
    const human = end.players.find((p) => p.human)!;
    expect(human.stack).toBe(990); // only the big blind is lost, no refund of it
  });

  it('a bluff is not rewarded: the bots play on and call it down', () => {
    const s = hand([1000, 1000], { holes: ['7c 2d', 'As Ah'], board: '3c 8d 9h Js Kd', humans: [true, false] });
    act(s, R(500)); // the human bluffs, then quits
    const end = playOut(s, (h, i) => (h.players[i]!.human ? leave(h) : C))!;
    expect(end.players.find((p) => p.human)!.stack).toBe(500);
    expect(chipsIn(end)).toBe(2000);
  });
});
