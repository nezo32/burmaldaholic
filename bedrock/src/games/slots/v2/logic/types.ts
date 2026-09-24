/**
 * Slots v2 engine types (SLOTS.md). PURE; twin of Java `games.slots.v2.logic` — same field names and
 * units (money in FIFTHS of the bet, SLOTS.md §7.5) so one tape string decodes in both editions.
 * Decision (docs/architecture/animation.md §7): v2 lives next to v1 (`games/slots/logic`) until the cut-over
 * task switches `slots/index.ts`; v1 then only settles persisted v1 records and is deleted one release later.
 */
export type MachineId = 'overworld' | 'nether' | 'end';

export const MACHINE_IDS: readonly MachineId[] = ['overworld', 'nether', 'end'];

export const MACHINES: Readonly<Record<MachineId, { block: string; defaultStripLength: number; defaultCap: number }>> = {
  overworld: { block: 'slot_machine_copper', defaultStripLength: 40, defaultCap: 500 },
  nether: { block: 'slot_machine_gold', defaultStripLength: 32, defaultCap: 2000 },
  end: { block: 'slot_machine_netherite', defaultStripLength: 45, defaultCap: 5000 },
};

export const isMachineId = (v: unknown): v is MachineId => v === 'overworld' || v === 'nether' || v === 'end';

export type SymbolRole = 'PAY' | 'WILD' | 'SCATTER' | 'BONUS' | 'COIN';

export const REELS = 5;
export const ROWS = 3;
export const CELLS = REELS * ROWS;

/**
 * Prize codes shared by Treasure Hunt entries, Hoard coins and wheel wedges: a positive integer = that many
 * × bet; −1…−4 = Mini…Grand jackpot; 0 = Creeper (hunt) / UP (wheel).
 */
export type PrizeCode = number;

/** Treasure Hunt (SLOTS.md §3.1; `slots.overworld.pick.*`). */
export interface HuntDef {
  board: number;
  /** content codes, aligned with `weights` */
  values: number[];
  weights: number[];
}

/** Piglin's Hoard (SLOTS.md §3.2; `slots.nether.hold.*`). Weights are the ×10 integers. */
export interface HoardDef {
  trigger: number;
  respins: number;
  /** per empty cell per respin, in millionths (0.04 → 40 000) */
  chanceMicro: number;
  values: number[];
  weights: number[];
}

/** Dragon Wheel rings (outer, middle, core), wedges clockwise from the pointer; codes as {@link PrizeCode}. */
export interface WheelDef {
  rings: number[][];
}

/** Progressive jackpots per tier 1…4 (index 0 unused), SLOTS.md §5. */
export interface JackpotDef {
  /** `slots.<m>.jackpot.refBet` */
  ref: number;
  /** seed chips per tier (seed multiple × ref) */
  seeds: number[];
  /** fraction of every stake per tier */
  contribution: number[];
  /** owned-casino fixed prize, × bet */
  owned: number[];
}

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
  /** Bonus game of the machine (exactly one of hunt / hoard / wheel). */
  hunt?: HuntDef;
  hoard?: HoardDef;
  wheel?: WheelDef;
  jackpot: JackpotDef;
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

/** 15-bit mask helper: bit (reel × 3 + row). */
export const cellBit = (reel: number, row: number): number => 1 << (reel * ROWS + row);

export interface WayWin {
  symbol: number;
  k: number;
  ways: number;
  payFifths: number;
  cellMask: number;
}

export interface WaysResult {
  /** winning symbols in symbol order */
  wins: WayWin[];
  /** Σ way pays (no scatter pay, no multiplier) */
  payFifths: number;
  /** union of the wins' cell masks (cells that explode in a tumble) */
  winMask: number;
  /** scatter cells on the window (sticky reels excluded) */
  scatters: number;
  /** bonus reels (of `bonusReelsMask`) showing at least one bonus symbol */
  bonusCount: number;
  /** coin cells on the window */
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
  /** every evaluation, the last one has no win (so tumbles = steps.length − 1) */
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

export interface JackpotAward {
  tier: 1 | 2 | 3 | 4;
  chips: number;
  owned: boolean;
}

/** The drawn tape of one spin (SLOTS.md §1.2, §8.1 `{v:2,…}`). */
export interface SpinTape {
  machine: MachineId;
  bet: number;
  bought: boolean;
  /** 5 base stops (empty when bought) */
  stops: number[];
  freeSpins?: { awarded: number; spins: FreeSpin[]; payFifths: number };
  /**
   * Treasure Hunt: `board` i.i.d. entries in REVEAL order (codes: 1,2,3,5,10,25 = ×bet; −1…−4 = Mini…Grand;
   * 0 = Creeper) and `opened` = picks made so far (0 at draw; the round record persists it per pick, SLOTS.md
   * §8.1). How many chests the hunt opens is `huntOpens(def, tape)`, a pure function of the tape.
   */
  hunt?: { entries: number[]; opened: number };
  /** Piglin's Hoard: initial coin cells/values, then per respin the new coins (cell, value). */
  hoard?: { initialCells: number[]; initialValues: number[]; respinCells: number[][]; respinValues: number[][] };
  /** Dragon Wheel: segment index per ring reached (outer, middle, core); length 1–3. */
  wheel?: { segments: number[] };
  /** in tape order; amounts fixed at draw time */
  jackpots: JackpotAward[];
  /** spin total EXCLUDING progressive awards (owned fixed jackpots are included), capped, fifths of the bet */
  totalFifths: number;
  capHit: boolean;
}

export const TAPE_VERSION = 2;

export const tapeTotalChips = (t: SpinTape): number => (t.totalFifths * t.bet) / 5;

/** Chips paid from the progressive pools (not part of the wager settlement, SLOTS.md §8.3). */
export const tapePoolChips = (t: SpinTape): number => t.jackpots.reduce((s, j) => s + (j.owned ? 0 : j.chips), 0);

/** Randomness for the draw: adapts `ctx.odds.draw` (streak re-draw of the whole spin, SLOTS.md §8.2). */
export interface SlotRng {
  nextInt(bound: number): number;
}
