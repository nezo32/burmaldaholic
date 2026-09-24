/**
 * The three machines' default math, verbatim from SLOTS.md §2, §3, §5, §6, §12 and Appendix A. PURE.
 * `MachineConfig` mirrors the `slots.<m>.*` config keys (SLOTS.md §12); `config.ts` validates an override and
 * builds the engine's {@link MachineDef}. Twin of the Java defaults (lane S-J4).
 */
import type { MachineId, SymbolRole } from './types';

export interface SymbolInfo {
  code: string;
  /** lang id: `gui.burmaldaholic.slots.symbol.<id>` */
  id: string;
  role: SymbolRole;
}

const S = (code: string, id: string, role: SymbolRole = 'PAY'): SymbolInfo => ({ code, id, role });

/** Symbols in SLOTS.md §2 order (index = symbol number used in strips, windows and tapes). */
export const SYMBOLS: Readonly<Record<MachineId, readonly SymbolInfo[]>> = {
  overworld: [
    S('WD', 'totem', 'WILD'),
    S('SC', 'compass', 'SCATTER'),
    S('BN', 'chest', 'BONUS'),
    S('DI', 'diamond'),
    S('EM', 'emerald'),
    S('GO', 'gold_ingot'),
    S('IR', 'iron_ingot'),
    S('AP', 'apple'),
    S('CA', 'carrot'),
    S('WH', 'wheat'),
    S('BE', 'sweet_berries'),
  ],
  nether: [
    S('WD', 'lava_bucket', 'WILD'),
    S('SC', 'ghast_tear', 'SCATTER'),
    S('CN', 'piglin_coin', 'COIN'),
    S('SK', 'wither_skull'),
    S('BR', 'blaze_rod'),
    S('MC', 'magma_cream'),
    S('QZ', 'quartz'),
    S('NW', 'nether_wart'),
    S('CF', 'crimson_fungus'),
    S('WF', 'warped_fungus'),
    S('GD', 'glowstone'),
  ],
  end: [
    S('WD', 'dragon_egg', 'WILD'),
    S('SC', 'ender_eye', 'SCATTER'),
    S('BN', 'end_crystal', 'BONUS'),
    S('DH', 'dragon_head'),
    S('EL', 'elytra'),
    S('SS', 'shulker_shell'),
    S('CH', 'chorus_fruit'),
    S('EP', 'ender_pearl'),
    S('PU', 'purpur'),
    S('ER', 'end_rod'),
    S('ES', 'end_stone'),
  ],
};

/** Top paying symbol (advancement `top_five`, chaos rows of SLOTS.md §8.4). */
export const TOP_SYMBOL: Readonly<Record<MachineId, number>> = { overworld: 3, nether: 3, end: 3 };

export type JackpotTierName = 'mini' | 'minor' | 'major' | 'grand';
export const JACKPOT_TIERS: readonly JackpotTierName[] = ['mini', 'minor', 'major', 'grand'];

/** Mirrors `slots.<m>.*` (SLOTS.md §12). Pays are × bet (multiples of 0.2). */
export interface MachineConfig {
  enabled: boolean;
  bets: number[];
  defaultBet: number;
  minVipTier: number;
  maxWinMultiple: number;
  /** 5 strings of space-separated codes */
  strips: string[];
  /** symbol id → [3, 4, 5]-of-a-kind × bet per way */
  pays: Record<string, number[]>;
  scatterPays: number[];
  freeSpins: number[];
  retrigger: number;
  fsCap: number;
  /** Overworld only */
  fsMultiplier?: number;
  /** Nether only */
  ladder?: number[];
  ladderFree?: number[];
  /** Overworld only: `slots.overworld.pick.*` */
  pick?: { board: number; weights: Record<string, number> };
  /** Nether only: `slots.nether.hold.*` */
  hold?: { trigger: number; respins: number; coinChance: number; coinWeights: Record<string, number> };
  /** End only: `slots.end.wheel.*` */
  wheel?: { outer: string[]; middle: string[]; core: string[] };
  jackpot: {
    refBet: number;
    /** × refBet */
    seed: Record<JackpotTierName, number>;
    contribution: Record<JackpotTierName, number>;
    owned: Record<JackpotTierName, number>;
  };
  /** × bet; undefined = no buy feature (Overworld) */
  buyPrice?: number;
}

/** Keys of `slots.overworld.pick.weights` and their prize codes. */
export const PICK_KEYS: ReadonlyArray<readonly [string, number]> = [
  ['x1', 1],
  ['x2', 2],
  ['x3', 3],
  ['x5', 5],
  ['x10', 10],
  ['x25', 25],
  ['mini', -1],
  ['minor', -2],
  ['major', -3],
  ['grand', -4],
  ['creeper', 0],
];

/** Keys of `slots.nether.hold.coinWeights` and their prize codes. */
export const COIN_KEYS: ReadonlyArray<readonly [string, number]> = [
  ['x1', 1],
  ['x2', 2],
  ['x3', 3],
  ['x5', 5],
  ['x10', 10],
  ['x25', 25],
  ['mini', -1],
  ['minor', -2],
  ['major', -3],
];

const OVERWORLD_STRIPS = [
  'AP CA GO BE BE AP AP CA IR CA AP SC DI BN GO WH GO BN BE CA BE EM IR DI EM EM AP BN BE BE WH CA IR WH DI SC WH IR GO WH',
  'CA AP CA BE WH BE WH GO WH GO IR SC GO CA EM AP EM BE BE IR IR WD WD WH WH CA EM DI DI CA IR WD WD BE AP DI AP AP GO BE',
  'AP EM BE IR WH WH BN DI CA DI DI BE GO EM WD WD IR CA BE AP GO AP CA IR CA WD WD GO WH IR WH BE GO AP EM SC AP BE CA BN',
  'AP WH BE AP CA EM BE WH GO AP GO BE WD WD AP WD WD AP CA GO CA IR GO BE EM WH IR IR DI DI CA BE WH DI SC CA WH BE EM IR',
  'BE WH WH BE BE WH EM GO CA BN BE AP IR CA IR AP SC DI EM BE AP DI AP BN GO CA CA BE AP WH GO DI BN EM IR IR SC GO WH CA',
];

const NETHER_STRIPS = [
  'NW BR BR WF CF SC NW WF GD BR CF CF MC QZ NW NW MC QZ GD QZ CF WF WF MC GD CN CN NW GD SK SK CF',
  'SK SC BR MC MC WF CF NW WF NW MC NW QZ CF GD GD BR WF WD WF QZ CF GD WD NW SK QZ CN CN CF WF GD',
  'WF NW MC MC CF WF CN QZ QZ NW CF WD CF WF WD GD BR CF SK NW GD CN CN SC GD MC GD WF SK NW QZ BR',
  'CF BR NW QZ NW WF BR QZ CF MC SK CF NW SC WF GD MC SK WD CN CN WF WD WF CF GD WF GD GD QZ MC NW',
  'SK WF CF CN CN SK GD NW WF NW BR CF MC NW BR GD QZ BR CF MC GD QZ WF QZ GD MC NW CF NW CF SC WF',
];

const END_STRIPS = [
  'EL PU ES CH SS PU EP CH ES ES PU SC EP EP SS ER PU EP ER CH CH EP ER ER ES DH DH PU EL EP CH ES PU EP ER SS ER ER PU EL ER ES SS ES ES',
  'ER EP ES SS PU PU ER SS ER PU PU CH EL CH ES EP ER EL PU ES ES BN PU ES ES EP ER SS EL DH DH CH ES EP EP BN ES ER CH EP CH WD SC SS ER',
  'CH ER ES ER EP BN PU PU EP CH PU ES EP ES BN ER SS EP EL PU ES CH SS DH DH PU ER ES PU ES BN ES EP WD PU SS EL CH EL EP ER SS ER SC ER',
  'DH DH PU PU CH EL SS PU ES ES ER EP ES ER EL ES SS SS PU EL ES ER ES ER BN CH ER BN SS ER EP PU CH EP EP CH PU CH SC ER ES EP WD ES EP',
  'DH DH PU ER PU CH SC SS PU ER SS EP PU ES EP SS ER CH ER CH ER EP SS EL CH EP EL EP PU ER CH PU ES EP EP ES EL ES ES ER ES PU ES ES ER',
];

const words = (s: string): string[] => s.split(' ');

export const DEFAULT_CONFIG: Readonly<Record<MachineId, MachineConfig>> = {
  overworld: {
    enabled: true,
    bets: [5, 10, 20, 50, 100],
    defaultBet: 10,
    minVipTier: 0,
    maxWinMultiple: 500,
    strips: OVERWORLD_STRIPS,
    pays: {
      diamond: [0.8, 2, 4],
      emerald: [0.6, 1.2, 2.4],
      gold_ingot: [0.4, 0.8, 1.6],
      iron_ingot: [0.4, 0.8, 1.6],
      apple: [0.2, 0.4, 0.8],
      carrot: [0.2, 0.4, 0.8],
      wheat: [0.2, 0.4, 0.8],
      sweet_berries: [0.2, 0.4, 0.8],
    },
    scatterPays: [1, 10, 50],
    freeSpins: [8, 10, 15],
    retrigger: 8,
    fsCap: 50,
    fsMultiplier: 2,
    pick: { board: 15, weights: { x1: 30000, x2: 22000, x3: 14000, x5: 9000, x10: 3500, x25: 800, mini: 600, minor: 150, major: 20, grand: 3, creeper: 22000 } },
    jackpot: {
      refBet: 100,
      seed: { mini: 10, minor: 25, major: 100, grand: 500 },
      contribution: { mini: 0.004, minor: 0.003, major: 0.002, grand: 0.001 },
      owned: { mini: 10, minor: 25, major: 100, grand: 250 },
    },
  },
  nether: {
    enabled: true,
    bets: [10, 20, 50, 100, 250, 500],
    defaultBet: 20,
    minVipTier: 0,
    maxWinMultiple: 2000,
    strips: NETHER_STRIPS,
    pays: {
      wither_skull: [0.8, 2, 8],
      blaze_rod: [0.6, 1.2, 4],
      magma_cream: [0.4, 0.8, 2],
      quartz: [0.4, 0.8, 1.6],
      nether_wart: [0.2, 0.4, 0.6],
      crimson_fungus: [0.2, 0.4, 0.6],
      warped_fungus: [0.2, 0.2, 0.6],
      glowstone: [0.2, 0.2, 0.6],
    },
    scatterPays: [0, 0, 0],
    freeSpins: [12, 15, 20],
    retrigger: 5,
    fsCap: 60,
    ladder: [1, 2, 3, 5],
    ladderFree: [2, 4, 6, 10],
    hold: { trigger: 6, respins: 3, coinChance: 0.04, coinWeights: { x1: 4000, x2: 2500, x3: 1500, x5: 1000, x10: 500, x25: 120, mini: 80, minor: 20, major: 3 } },
    jackpot: {
      refBet: 500,
      seed: { mini: 10, minor: 30, major: 150, grand: 1000 },
      contribution: { mini: 0.005, minor: 0.004, major: 0.0035, grand: 0.0025 },
      owned: { mini: 10, minor: 30, major: 150, grand: 500 },
    },
    buyPrice: 18.4,
  },
  end: {
    enabled: true,
    bets: [50, 100, 250, 500, 1000, 2500, 5000],
    defaultBet: 100,
    minVipTier: 2,
    maxWinMultiple: 5000,
    strips: END_STRIPS,
    pays: {
      dragon_head: [2, 8, 30],
      elytra: [1, 4, 12],
      shulker_shell: [0.8, 2, 6],
      chorus_fruit: [0.6, 1.6, 5],
      ender_pearl: [0.4, 0.6, 2],
      purpur: [0.4, 0.6, 2],
      end_rod: [0.2, 0.6, 1.2],
      end_stone: [0.2, 0.6, 1.2],
    },
    scatterPays: [2, 10, 50],
    freeSpins: [9, 11, 14],
    retrigger: 4,
    fsCap: 40,
    wheel: {
      outer: words('10 UP 12 15 MINI 10 20 12 25 10 40 15 UP 12 20 MINI 15 10 75 25'),
      middle: words('30 50 MINOR 75 30 100 50 UP 30 75 MINOR 50 100 30 75 50'),
      core: words('150 MAJOR 250 150 GRAND 250 MAJOR 150 500 250 MAJOR 150'),
    },
    jackpot: {
      refBet: 5000,
      seed: { mini: 15, minor: 50, major: 250, grand: 2500 },
      contribution: { mini: 0.005, minor: 0.005, major: 0.006, grand: 0.009 },
      owned: { mini: 15, minor: 50, major: 250, grand: 1000 },
    },
    buyPrice: 109,
  },
};

/** Reference numbers of SLOTS.md §7.1 (percent; exact, uncapped) — `validateRtp` and tests compare to these. */
export const REFERENCE_RTP: Readonly<Record<MachineId, { base: number; scatter: number; freeSpins: number; bonus: number; jackpotSeed: number; contributions: number; total: number; owned: number }>> = {
  overworld: { base: 73.1594, scatter: 1.36793, freeSpins: 11.840624, bonus: 7.260196, jackpotSeed: 0.445359, contributions: 1, total: 95.073509, owned: 94.0483 },
  nether: { base: 68.222821, scatter: 0, freeSpins: 16.71077, bonus: 8.216694, jackpotSeed: 0.744099, contributions: 1.5, total: 95.394383, owned: 93.809143 },
  end: { base: 56.211147, scatter: 0.614979, freeSpins: 28.057181, bonus: 7.793704, jackpotSeed: 1.357407, contributions: 2.5, total: 96.534419, owned: 93.756641 },
};
