/**
 * Dice Duel (GAME_DESIGN §11.5). PURE.
 *
 * Vs house: player and dealer each roll 2d6; higher total wins 1:1; ties push, except ties on
 * a total in `houseWinsTieOn` (default [7]) which the house wins. HE = (6/36)² = 2.78 %.
 * Always honest (no streak re-draw).
 *
 * PvP: both roll 2d6, higher total takes the pot minus the rake; ties re-roll, and after
 * `maxRolls` ties in a row both stakes are refunded. Challenges are tracked by ChallengeBook.
 */
import { type Rng, randInt } from '../../../core/logic/rng';

export type Roll = readonly [number, number];
export const rollTotal = (r: Roll): number => r[0] + r[1];
export const roll2d6 = (rng: Rng): Roll => [randInt(rng, 1, 6), randInt(rng, 1, 6)];

export type HouseOutcome = 'win' | 'lose' | 'push' | 'house_tie';

export interface HouseDuel {
  player: Roll;
  dealer: Roll;
  outcome: HouseOutcome;
}

export function judgeHouse(player: Roll, dealer: Roll, houseWinsTieOn: readonly number[]): HouseOutcome {
  const a = rollTotal(player);
  const b = rollTotal(dealer);
  if (a > b) return 'win';
  if (a < b) return 'lose';
  return houseWinsTieOn.includes(a) ? 'house_tie' : 'push';
}

export function duelHouse(rng: Rng, houseWinsTieOn: readonly number[] = [7]): HouseDuel {
  const player = roll2d6(rng);
  const dealer = roll2d6(rng);
  return { player, dealer, outcome: judgeHouse(player, dealer, houseWinsTieOn) };
}

/** Total return (stake included): win 2×, push 1×, loss / house tie 0. */
export function houseReturn(stake: number, o: HouseOutcome): number {
  return o === 'win' ? 2 * stake : o === 'push' ? stake : 0;
}

/** Config `extras.diceDuel.houseWinsTieOn` → valid totals 2…12. */
export function tieTotals(value: unknown): number[] {
  if (!Array.isArray(value)) return [7];
  return [...new Set(value.filter((v): v is number => Number.isInteger(v) && v >= 2 && v <= 12))];
}

/** Probability that two 2d6 totals are equal and equal to t. */
const P2D6 = [0, 0, 1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1].map((n) => n / 36);

/** Exact RTP of the house duel (win prob × 2 + push prob). */
export function houseRtp(houseWinsTieOn: readonly number[] = [7]): number {
  let tie = 0;
  let houseTie = 0;
  for (let s = 2; s <= 12; s++) {
    const p = (P2D6[s] ?? 0) ** 2;
    tie += p;
    if (houseWinsTieOn.includes(s)) houseTie += p;
  }
  const win = (1 - tie) / 2;
  return 2 * win + (tie - houseTie);
}

// ---- PvP ---------------------------------------------------------------------------------

export interface PvpRound {
  a: Roll;
  b: Roll;
}

export interface PvpDuel {
  rounds: PvpRound[];
  /** 'a' or 'b' won; 'refund' after maxRolls ties in a row */
  result: 'a' | 'b' | 'refund';
}

export function duelPvp(rng: Rng, maxRolls = 3): PvpDuel {
  const rounds: PvpRound[] = [];
  for (let i = 0; i < Math.max(1, maxRolls); i++) {
    const a = roll2d6(rng);
    const b = roll2d6(rng);
    rounds.push({ a, b });
    const d = rollTotal(a) - rollTotal(b);
    if (d !== 0) return { rounds, result: d > 0 ? 'a' : 'b' };
  }
  return { rounds, result: 'refund' };
}

/** Pot split: winner receives pot − rake; rake = floor(pot × percent / 100). */
export function pvpPayout(stake: number, rakePercent: number): { pot: number; rake: number; winnerGets: number } {
  const pot = 2 * stake;
  const rake = Math.floor((pot * Math.max(0, rakePercent)) / 100);
  return { pot, rake, winnerGets: pot - rake };
}

// ---- challenges --------------------------------------------------------------------------

export interface Challenge {
  id: number;
  from: string;
  to: string;
  stake: number;
  /** absolute tick when it expires */
  expires: number;
}

export type ChallengeError = 'self' | 'already_pending';

/**
 * Pending PvP challenges (in memory: nothing is escrowed until accepted, so a restart only
 * forgets pending invitations). Max 1 pending OUTGOING challenge per player.
 */
export class ChallengeBook {
  private readonly list: Challenge[] = [];
  private seq = 0;

  create(from: string, to: string, stake: number, now: number, timeoutTicks: number): { ok: true; challenge: Challenge } | { ok: false; error: ChallengeError } {
    if (from === to) return { ok: false, error: 'self' };
    if (this.outgoing(from, now)) return { ok: false, error: 'already_pending' };
    const challenge: Challenge = { id: ++this.seq, from, to, stake, expires: now + timeoutTicks };
    this.list.push(challenge);
    return { ok: true, challenge };
  }

  /** The live outgoing challenge of a player. */
  outgoing(from: string, now: number): Challenge | undefined {
    return this.list.find((c) => c.from === from && c.expires > now);
  }

  /** Live challenges addressed to a player, oldest first. */
  incoming(to: string, now: number): Challenge[] {
    return this.list.filter((c) => c.to === to && c.expires > now);
  }

  get(id: number, now: number): Challenge | undefined {
    return this.list.find((c) => c.id === id && c.expires > now);
  }

  /** Remove and return a live challenge (accept / decline). */
  take(id: number, now: number): Challenge | undefined {
    const i = this.list.findIndex((c) => c.id === id);
    if (i < 0) return undefined;
    const [c] = this.list.splice(i, 1);
    return c && c.expires > now ? c : undefined;
  }

  /** Remove and return expired challenges. */
  expire(now: number): Challenge[] {
    const out: Challenge[] = [];
    for (let i = this.list.length - 1; i >= 0; i--) {
      const c = this.list[i] as Challenge;
      if (c.expires <= now) out.unshift(...this.list.splice(i, 1));
    }
    return out;
  }

  /** Remove every challenge involving a player (disconnect). */
  dropPlayer(id: string): Challenge[] {
    const out: Challenge[] = [];
    for (let i = this.list.length - 1; i >= 0; i--) {
      const c = this.list[i] as Challenge;
      if (c.from === id || c.to === id) out.unshift(...this.list.splice(i, 1));
    }
    return out;
  }

  size(): number {
    return this.list.length;
  }
}
