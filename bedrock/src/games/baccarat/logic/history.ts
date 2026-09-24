/**
 * Bead plate (results of the current shoe), running stats and the tie run (GAME_DESIGN §20.5,
 * §20.7). PURE and serializable.
 *
 * Stored bead = winner letter P / B / T + optional 'p' (Player pair) + optional 'b' (Banker
 * pair), beads joined by spaces.
 */
import type { Coup, Winner } from './rules';

export interface Bead {
  readonly winner: Winner;
  readonly playerPair: boolean;
  readonly bankerPair: boolean;
}

export interface HistoryData {
  /** beads, oldest first ("P", "Bp", "Tpb" …) */
  b: string;
  /** coups dealt from this shoe (bead plate may be capped shorter) */
  n: number;
  /** stats of this shoe: player, banker, tie */
  s: [number, number, number];
  /** ties in a row (continues across shuffles) */
  t: number;
  /** coups dealt at this table ever (coup #) */
  c: number;
}

const LETTER: Record<Winner, string> = { player: 'P', banker: 'B', tie: 'T' };
const WINNER: Record<string, Winner> = { P: 'player', B: 'banker', T: 'tie' };

export const encodeBead = (b: Bead): string => LETTER[b.winner] + (b.playerPair ? 'p' : '') + (b.bankerPair ? 'b' : '');

export function decodeBead(s: string): Bead | undefined {
  const winner = WINNER[s[0] ?? ''];
  if (!winner) return undefined;
  return { winner, playerPair: s.includes('p'), bankerPair: s.includes('b') };
}

export class BeadPlate {
  beads: Bead[] = [];
  /** coups of this shoe */
  shoeCoups = 0;
  stats: [number, number, number] = [0, 0, 0];
  tieRun = 0;
  coupNo = 0;

  constructor(private readonly cap: number) {}

  /** Record a coup; returns the tie run after it. */
  record(c: Coup): number {
    this.coupNo++;
    this.shoeCoups++;
    const i = c.winner === 'player' ? 0 : c.winner === 'banker' ? 1 : 2;
    this.stats[i]++;
    this.tieRun = c.winner === 'tie' ? this.tieRun + 1 : 0;
    if (this.cap > 0) {
      this.beads.push({ winner: c.winner, playerPair: c.playerPair, bankerPair: c.bankerPair });
      while (this.beads.length > this.cap) this.beads.shift();
    }
    return this.tieRun;
  }

  /** New shoe: the plate and the shoe stats are cleared (the tie run and coup # continue). */
  clear(): void {
    this.beads = [];
    this.shoeCoups = 0;
    this.stats = [0, 0, 0];
  }

  /** Newest `n` beads, oldest first. */
  last(n: number): Bead[] {
    return this.beads.slice(Math.max(0, this.beads.length - n));
  }

  toData(): HistoryData {
    return { b: this.beads.map(encodeBead).join(' '), n: this.shoeCoups, s: [...this.stats], t: this.tieRun, c: this.coupNo };
  }

  static fromData(x: unknown, cap: number): BeadPlate {
    const h = new BeadPlate(cap);
    const d = x as Partial<HistoryData> | undefined;
    if (!d || typeof d !== 'object') return h;
    if (typeof d.b === 'string' && d.b) h.beads = d.b.split(' ').map(decodeBead).filter((b): b is Bead => !!b).slice(-Math.max(0, cap));
    if (typeof d.n === 'number') h.shoeCoups = Math.max(0, Math.floor(d.n));
    if (Array.isArray(d.s) && d.s.length === 3 && d.s.every((v) => typeof v === 'number')) h.stats = [d.s[0], d.s[1], d.s[2]];
    if (typeof d.t === 'number') h.tieRun = Math.max(0, Math.floor(d.t));
    if (typeof d.c === 'number') h.coupNo = Math.max(0, Math.floor(d.c));
    return h;
  }
}

/**
 * §20.7: the tie run fires on the coup that completes `threshold` ties in a row and again on
 * every further tie (the counter continues). 0 = off.
 */
export const tieRunFires = (run: number, threshold: number): boolean => threshold > 0 && run >= threshold;
