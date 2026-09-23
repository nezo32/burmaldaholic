/**
 * Daily contracts (GAME_DESIGN §3.4.4). PURE.
 * Generated once per world day, drawn without duplicates by weight; targets and rewards scale
 * with the VIP tier index; one reroll per slot per day.
 */
import { type Rng, weightedPick } from '../../core/logic/rng';

export const CONTRACT_IDS = [
  'mine_iron',
  'mine_coal',
  'mine_diamond',
  'kill_zombie',
  'kill_skeleton',
  'kill_creeper',
  'kill_any',
  'trade',
  'fish',
  'harvest',
  'wager',
  'win_blackjack',
  'spin_slots',
  'roulette_red',
  'play_poker',
  'explore_nether',
  'smelt',
] as const;
export type ContractId = (typeof CONTRACT_IDS)[number];

export interface ContractDef {
  id: ContractId;
  target: number;
  reward: number;
  weight: number;
}

/** §3.4.4 table (weights are defaults; config `contracts.weight.<id>` overrides). */
export const CONTRACT_DEFS: Readonly<Record<ContractId, ContractDef>> = {
  mine_iron: { id: 'mine_iron', target: 24, reward: 50, weight: 10 },
  mine_coal: { id: 'mine_coal', target: 48, reward: 30, weight: 8 },
  mine_diamond: { id: 'mine_diamond', target: 3, reward: 80, weight: 5 },
  kill_zombie: { id: 'kill_zombie', target: 12, reward: 40, weight: 10 },
  kill_skeleton: { id: 'kill_skeleton', target: 10, reward: 40, weight: 8 },
  kill_creeper: { id: 'kill_creeper', target: 5, reward: 45, weight: 6 },
  kill_any: { id: 'kill_any', target: 25, reward: 60, weight: 8 },
  trade: { id: 'trade', target: 6, reward: 40, weight: 8 },
  fish: { id: 'fish', target: 8, reward: 30, weight: 6 },
  harvest: { id: 'harvest', target: 64, reward: 30, weight: 6 },
  wager: { id: 'wager', target: 500, reward: 30, weight: 8 },
  win_blackjack: { id: 'win_blackjack', target: 3, reward: 40, weight: 5 },
  spin_slots: { id: 'spin_slots', target: 30, reward: 25, weight: 5 },
  roulette_red: { id: 'roulette_red', target: 2, reward: 35, weight: 4 },
  play_poker: { id: 'play_poker', target: 10, reward: 40, weight: 3 },
  explore_nether: { id: 'explore_nether', target: 500, reward: 60, weight: 3 },
  smelt: { id: 'smelt', target: 32, reward: 25, weight: 3 },
};

export const isContractId = (s: string): s is ContractId => (CONTRACT_IDS as readonly string[]).includes(s);

export interface Contract {
  id: ContractId;
  target: number;
  reward: number;
  progress: number;
  done: boolean;
  rerolled: boolean;
}

export interface ContractState {
  day: number;
  list: Contract[];
}

export interface ContractParams {
  /** VIP tier index t (0 = Bronze) */
  tier: number;
  /** contracts.tierScaling (0.25) */
  scaling: number;
  /** VIP contract bonus fraction (§12: Silver 0.05, Gold+ 0.10) */
  bonus: number;
  /** contracts.rewardMultiplier */
  multiplier: number;
  /** weight per id (0 disables) */
  weights: Readonly<Partial<Record<ContractId, number>>>;
}

/** target = ceil(base × (1 + s·t)) */
export function scaledTarget(base: number, tier: number, scaling: number): number {
  return Math.max(1, Math.ceil(base * (1 + scaling * tier) - 1e-9));
}

/** reward = floor(baseReward × (1 + s·t) × (1 + bonus) × multiplier) */
export function scaledReward(base: number, tier: number, scaling: number, bonus: number, multiplier: number): number {
  return Math.max(0, Math.floor(base * (1 + scaling * tier) * (1 + bonus) * multiplier + 1e-9));
}

export function makeContract(id: ContractId, p: ContractParams): Contract {
  const d = CONTRACT_DEFS[id];
  return {
    id,
    target: scaledTarget(d.target, p.tier, p.scaling),
    reward: scaledReward(d.reward, p.tier, p.scaling, p.bonus, p.multiplier),
    progress: 0,
    done: false,
    rerolled: false,
  };
}

const weightOf = (id: ContractId, p: ContractParams): number => {
  const w = p.weights[id];
  return typeof w === 'number' && Number.isFinite(w) ? Math.max(0, w) : CONTRACT_DEFS[id].weight;
};

/** Weighted draw of one id not in `exclude`; undefined when the pool is exhausted. */
export function drawId(rng: Rng, p: ContractParams, exclude: ReadonlySet<ContractId>): ContractId | undefined {
  const pool = CONTRACT_IDS.filter((id) => !exclude.has(id)).map((id) => [id, weightOf(id, p)] as const).filter(([, w]) => w > 0);
  if (!pool.length) return undefined;
  return weightedPick(rng, pool);
}

/** New day's contracts: `slots` distinct ids (fewer if the pool is smaller). */
export function generate(rng: Rng, day: number, slots: number, p: ContractParams): ContractState {
  const list: Contract[] = [];
  const used = new Set<ContractId>();
  for (let i = 0; i < slots; i++) {
    const id = drawId(rng, p, used);
    if (!id) break;
    used.add(id);
    list.push(makeContract(id, p));
  }
  return { day, list };
}

/** Add contracts until `slots` (after a VIP promotion mid-day). Mutates and returns the added ones. */
export function topUp(rng: Rng, state: ContractState, slots: number, p: ContractParams): Contract[] {
  const added: Contract[] = [];
  const used = new Set(state.list.map((c) => c.id));
  while (state.list.length < slots) {
    const id = drawId(rng, p, used);
    if (!id) break;
    used.add(id);
    const c = makeContract(id, p);
    state.list.push(c);
    added.push(c);
  }
  return added;
}

/** Needs a fresh set for `today`? */
export const isStale = (s: ContractState | undefined, today: number): boolean => !s || !Array.isArray(s.list) || s.day !== today;

/**
 * Add progress to every open contract with this id. Mutates `state`; returns the contracts
 * that completed with this call (reward them exactly once).
 */
export function addProgress(state: ContractState, id: ContractId, amount: number): Contract[] {
  const done: Contract[] = [];
  if (!(amount > 0)) return done;
  for (const c of state.list) {
    if (c.id !== id || c.done) continue;
    c.progress = Math.min(c.target, c.progress + amount);
    if (c.progress >= c.target) {
      c.done = true;
      done.push(c);
    }
  }
  return done;
}

export type RerollError = 'no_slot' | 'done' | 'rerolled' | 'pool_empty';

/** Reroll slot `index` (§3.4.4: not done, max once per slot per day). Mutates on success. */
export function reroll(rng: Rng, state: ContractState, index: number, p: ContractParams): { ok: true; contract: Contract } | { ok: false; error: RerollError } {
  const cur = state.list[index];
  if (!cur) return { ok: false, error: 'no_slot' };
  if (cur.done) return { ok: false, error: 'done' };
  if (cur.rerolled) return { ok: false, error: 'rerolled' };
  const id = drawId(rng, p, new Set(state.list.map((c) => c.id)));
  if (!id) return { ok: false, error: 'pool_empty' };
  const c = { ...makeContract(id, p), rerolled: true };
  state.list[index] = c;
  return { ok: true, contract: c };
}

export const allDone = (s: ContractState): boolean => s.list.length > 0 && s.list.every((c) => c.done);

// ---- event classification (block / entity ids) -------------------------------------------

const ORE_CONTRACT: Readonly<Record<string, ContractId>> = {
  'minecraft:iron_ore': 'mine_iron',
  'minecraft:deepslate_iron_ore': 'mine_iron',
  'minecraft:coal_ore': 'mine_coal',
  'minecraft:deepslate_coal_ore': 'mine_coal',
  'minecraft:diamond_ore': 'mine_diamond',
  'minecraft:deepslate_diamond_ore': 'mine_diamond',
};
export const oreContract = (blockId: string): ContractId | undefined => ORE_CONTRACT[blockId];

/** Crops for `harvest` and the growth value at which they are mature. */
export const CROPS: Readonly<Record<string, number>> = {
  'minecraft:wheat': 7,
  'minecraft:carrots': 7,
  'minecraft:potatoes': 7,
  'minecraft:beetroot': 7,
};
export const isMatureCrop = (blockId: string, growth: number | undefined): boolean => {
  const max = CROPS[blockId];
  return max !== undefined && typeof growth === 'number' && growth >= max;
};

const ZOMBIES = new Set(['minecraft:zombie', 'minecraft:husk', 'minecraft:drowned', 'minecraft:zombie_villager', 'minecraft:zombie_villager_v2']);
const SKELETONS = new Set(['minecraft:skeleton', 'minecraft:stray', 'minecraft:bogged']);
/** Hostile mobs for `kill_any` (§3.4.2 hostile categories). */
export const HOSTILE = new Set([
  ...ZOMBIES,
  ...SKELETONS,
  'minecraft:spider',
  'minecraft:cave_spider',
  'minecraft:silverfish',
  'minecraft:endermite',
  'minecraft:slime',
  'minecraft:magma_cube',
  'minecraft:vex',
  'minecraft:zombie_pigman',
  'minecraft:creeper',
  'minecraft:phantom',
  'minecraft:pillager',
  'minecraft:hoglin',
  'minecraft:zoglin',
  'minecraft:piglin',
  'minecraft:enderman',
  'minecraft:blaze',
  'minecraft:witch',
  'minecraft:vindicator',
  'minecraft:wither_skeleton',
  'minecraft:ghast',
  'minecraft:guardian',
  'minecraft:breeze',
  'minecraft:shulker',
  'minecraft:piglin_brute',
  'minecraft:creaking',
  'minecraft:evocation_illager',
  'minecraft:ravager',
  'minecraft:elder_guardian',
  'minecraft:warden',
  'minecraft:wither',
  'minecraft:ender_dragon',
]);

/** Contracts a mob kill advances. */
export function killContracts(typeId: string): ContractId[] {
  const out: ContractId[] = [];
  if (ZOMBIES.has(typeId)) out.push('kill_zombie');
  if (SKELETONS.has(typeId)) out.push('kill_skeleton');
  if (typeId === 'minecraft:creeper') out.push('kill_creeper');
  if (HOSTILE.has(typeId)) out.push('kill_any');
  return out;
}

export const FISH_ITEMS: readonly string[] = ['minecraft:cod', 'minecraft:salmon', 'minecraft:tropical_fish', 'minecraft:pufferfish'];
/** Furnace / blast furnace / smoker outputs counted for `smelt` (Bedrock approximation). */
export const SMELT_OUTPUTS: readonly string[] = [
  'minecraft:iron_ingot',
  'minecraft:gold_ingot',
  'minecraft:copper_ingot',
  'minecraft:netherite_scrap',
  'minecraft:cooked_beef',
  'minecraft:cooked_porkchop',
  'minecraft:cooked_chicken',
  'minecraft:cooked_mutton',
  'minecraft:cooked_rabbit',
  'minecraft:cooked_cod',
  'minecraft:cooked_salmon',
  'minecraft:baked_potato',
  'minecraft:dried_kelp',
  'minecraft:charcoal',
  'minecraft:glass',
  'minecraft:stone',
  'minecraft:smooth_stone',
  'minecraft:brick',
  'minecraft:nether_brick',
  'minecraft:hardened_clay', // Bedrock id of plain terracotta (smelted clay)
  'minecraft:deepslate',
  'minecraft:cracked_stone_bricks',
  'minecraft:sponge',
  'minecraft:lime_dye',
  'minecraft:green_dye',
  'minecraft:popped_chorus_fruit',
];

/**
 * Nether travel between two samples: horizontal distance, ignoring jumps larger than
 * `maxStep` (teleports, portals, respawns).
 */
export function travelStep(a: { x: number; z: number }, b: { x: number; z: number }, maxStep: number): number {
  const d = Math.hypot(b.x - a.x, b.z - a.z);
  return d > maxStep ? 0 : d;
}
