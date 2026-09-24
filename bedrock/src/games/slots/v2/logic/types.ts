/**
 * Slots v2 engine types (SLOTS.md). PURE; twin of Java `games.slots.v2.logic` — same field names and
 * units (money in FIFTHS of the bet, SLOTS.md §7.5) so one tape string decodes in both editions.
 * Decision (docs/architecture/animation.md §7): v2 lives next to v1 (`games/slots/logic`) until the cut-over
 * task switches `slots/index.ts`; v1 then only settles persisted v1 records and is deleted one release later.
 */
export type MachineId = 'overworld' | 'nether' | 'end';

export const MACHINES: Readonly<Record<MachineId, { block: string; defaultStripLength: number; defaultCap: number }>> = {
  overworld: { block: 'slot_machine_copper', defaultStripLength: 40, defaultCap: 500 },
  nether: { block: 'slot_machine_gold', defaultStripLength: 32, defaultCap: 2000 },
  end: { block: 'slot_machine_netherite', defaultStripLength: 45, defaultCap: 5000 },
};

export type SymbolRole = 'PAY' | 'WILD' | 'SCATTER' | 'BONUS' | 'COIN';

export const REELS = 5;
export const ROWS = 3;

export interface MachineDef {
  machine: MachineId;
  codes: string[];
  roles: SymbolRole[];
  strips: number[][];
  /** per symbol: [3, 4, 5]-of-a-kind pay per way, fifths of the bet */
  paysFifths: number[][];
  scatterFifths: [number, number, number];
  bonusReelsMask: number;
  freeSpins: [number, number, number];
  retrigger: number;
  fsCap: number;
  fsMultiplier: number;
  ladder: number[];
  ladderFree: number[];
  capMultiple: number;
  buyPriceFifths: number;
}

export const symbolAt = (def: MachineDef, reel: number, stop: number, row: number): number => {
  const s = def.strips[reel]!;
  return s[(((stop + row) % s.length) + s.length) % s.length]!;
};

/** 15 cells, index = reel × 3 + row. */
export type Window = readonly number[];

export function windowFromStops(def: MachineDef, stops: readonly number[]): Window {
  const c: number[] = [];
  for (let r = 0; r < REELS; r++) for (let y = 0; y < ROWS; y++) c.push(symbolAt(def, r, stops[r]!, y));
  return c;
}

export interface WayWin {
  symbol: number;
  k: number;
  ways: number;
  payFifths: number;
  cellMask: number;
}

export interface WaysResult {
  wins: WayWin[];
  payFifths: number;
  winMask: number;
  scatters: number;
  bonusCount: number;
  coins: number;
}

export interface TumbleStep {
  step: number;
  window: Window;
  result: WaysResult;
  multiplier: number;
  payFifths: number;
  tops: number[];
}

export interface TumbleChain {
  steps: TumbleStep[];
  finalWindow: Window;
  payFifths: number;
}

export interface FreeSpin {
  stops: number[];
  stickyMaskAfter: number;
  retrigger: boolean;
  payFifths: number;
}

/** The drawn tape of one spin (SLOTS.md §1.2, §8.1 `{v:2,…}`). */
export interface SpinTape {
  machine: MachineId;
  bet: number;
  bought: boolean;
  stops: number[];
  freeSpins?: { awarded: number; spins: FreeSpin[]; payFifths: number };
  /** reveal-order entries: 1,2,3,5,10,25 = ×bet; -1..-4 = Mini..Grand; 0 = Creeper */
  hunt?: { entries: number[]; opened: number };
  hoard?: { initialCells: number[]; initialValues: number[]; respinCells: number[][]; respinValues: number[][] };
  wheel?: { segments: number[] };
  jackpots: Array<{ tier: 1 | 2 | 3 | 4; chips: number; owned: boolean }>;
  totalFifths: number;
  capHit: boolean;
}

export const TAPE_VERSION = 2;

export const tapeTotalChips = (t: SpinTape): number => (t.totalFifths * t.bet) / 5;

/** Randomness for the draw: adapts `ctx.odds.draw` (streak re-draw of the whole spin, SLOTS.md §8.2). */
export interface SlotRng {
  nextInt(bound: number): number;
}
