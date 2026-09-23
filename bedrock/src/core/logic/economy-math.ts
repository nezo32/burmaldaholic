/**
 * Economy math (GAME_DESIGN.md §3). PURE: chip denominations, withdraw breakdown, balance
 * cap, exchange rates and the earning tables (ores, mobs, trading) with anti-farm rules.
 */

/** Chip items, highest first (§3.1). Item id `burmaldaholic:chip_<value>`. */
export const CHIP_VALUES = [500, 100, 25, 5, 1] as const;
export type ChipValue = (typeof CHIP_VALUES)[number];
export const chipItemId = (v: ChipValue): string => `burmaldaholic:chip_${v}`;

/** Value of one chip item, or 0 if `typeId` is not a chip. */
export function chipValueOf(typeId: string): number {
  const m = /^burmaldaholic:chip_(\d+)$/.exec(typeId);
  const v = m ? Number(m[1]) : 0;
  return (CHIP_VALUES as readonly number[]).includes(v) ? v : 0;
}

/**
 * Greedy withdraw breakdown into the largest denominations (§3.2). With `only` set
 * (cashier "Denomination" dropdown) uses that denomination for as much as possible and the
 * rest greedily, so the total is always exact.
 */
export function breakdown(amount: number, only?: ChipValue): { value: ChipValue; count: number }[] {
  let rest = Math.max(0, Math.floor(amount));
  const out: { value: ChipValue; count: number }[] = [];
  const order: ChipValue[] = only ? [only, ...CHIP_VALUES.filter((v) => v !== only)] : [...CHIP_VALUES];
  for (const v of order) {
    const c = Math.floor(rest / v);
    if (c > 0) {
      out.push({ value: v, count: c });
      rest -= c * v;
    }
  }
  return out;
}

/** A chip amount a debit / raise accepts: a safe integer > 0 (review m9). */
export const isChipAmount = (n: number): boolean => Number.isSafeInteger(n) && n > 0;

/** Credit with the balance cap (§3.1): excess is lost. */
export function applyCredit(balance: number, amount: number, maxBalance: number): { balance: number; credited: number; capped: boolean } {
  const room = Math.max(0, maxBalance - balance);
  const credited = Math.max(0, Math.min(Math.floor(amount), room));
  return { balance: balance + credited, credited, capped: credited < Math.floor(amount) };
}

/** withdrawable = max(0, balance − owed); 0 while a loan is in default (§3.2, §5.8). */
export function withdrawable(balance: number, owed: number, inDefault: boolean): number {
  return inDefault ? 0 : Math.max(0, balance - Math.max(0, owed));
}

/** Buying chips: `emeralds` emeralds -> chips at `rate` chips per emerald. */
export const buyChips = (emeralds: number, rate: number): number => Math.max(0, Math.floor(emeralds)) * rate;
/** Selling chips: how many whole emeralds `chipsOffered` buys at `rate`, and the chips spent. */
export function sellChips(chipsOffered: number, rate: number): { emeralds: number; cost: number } {
  const emeralds = Math.floor(Math.max(0, chipsOffered) / rate);
  return { emeralds, cost: emeralds * rate };
}

/**
 * Cashier "sell chips" at submit (review m3): the slider value re-clamped by what is
 * withdrawable NOW (a loan may have defaulted while the form was open -> 0).
 */
export function sellCount(requested: number, sliderMax: number, withdrawableNow: number, rate: number): number {
  if (!(rate > 0)) return 0;
  return Math.max(0, Math.min(Math.floor(requested), sliderMax, Math.floor(Math.max(0, withdrawableNow) / rate)));
}

/** A credit of `amount` fits under the balance cap without anything being cut (loans, review m5). */
export const fitsUnderCap = (balance: number, amount: number, maxBalance: number): boolean => balance + Math.max(0, amount) <= maxBalance;

// ---------------------------------------------------------------------------------------
// Villager trades (§3.4.3)

/** Inventory snapshot for trade detection: emeralds, emerald blocks, everything else. */
export interface TradeCounts {
  em: number;
  blocks: number;
  other: number;
}

/**
 * Emeralds moved by a villager trade between two inventory snapshots, else 0 (review m2).
 * Bedrock has no trade event, so a trade needs all of:
 *  - `tradeOpen`: the player opened a villager / wandering trader (interaction) a short while
 *    ago and is still next to that trader, and has not used a block (crafting table, chest...)
 *    since;
 *  - not `tainted`: no item dropped or picked up around the player in the window;
 *  - emeralds and other items moving in opposite directions, where emerald blocks count as
 *    9 emeralds and never as "other" (crafting 9 emeralds into a block is not a trade).
 */
export function detectTrade(prev: TradeCounts, cur: TradeCounts, o: { tradeOpen: boolean; tainted: boolean }): number {
  if (!o.tradeOpen || o.tainted) return 0;
  const dEm = cur.em + 9 * cur.blocks - (prev.em + 9 * prev.blocks);
  const dOther = cur.other - prev.other;
  if (dEm === 0 || dOther === 0 || Math.sign(dEm) === Math.sign(dOther)) return 0;
  return Math.abs(dEm);
}

// ---------------------------------------------------------------------------------------
// Ores (§3.4.1)

/** Pickaxe tiers; ores need at least `minTier` to drop loot. */
export const TOOL_TIERS = { none: 0, wooden: 1, golden: 1, stone: 2, copper: 2, iron: 3, diamond: 4, netherite: 5 } as const;

export function pickaxeTier(itemTypeId: string | undefined): number {
  const m = /^minecraft:([a-z]+)_pickaxe$/.exec(itemTypeId ?? '');
  return m ? ((TOOL_TIERS as Record<string, number>)[m[1]!] ?? 0) : 0;
}

/** Ore block id (without `minecraft:`) -> [config key, min pickaxe tier]. Deepslate = same. */
const ORES: Record<string, [string, number]> = {
  coal_ore: ['economy.ore.coal', 1],
  copper_ore: ['economy.ore.copper', 2],
  iron_ore: ['economy.ore.iron', 2],
  gold_ore: ['economy.ore.gold', 3],
  redstone_ore: ['economy.ore.redstone', 3],
  lit_redstone_ore: ['economy.ore.redstone', 3],
  lapis_ore: ['economy.ore.lapis', 2],
  emerald_ore: ['economy.ore.emerald', 3],
  diamond_ore: ['economy.ore.diamond', 3],
  quartz_ore: ['economy.ore.netherQuartz', 1],
  nether_gold_ore: ['economy.ore.netherGold', 1],
  ancient_debris: ['economy.ore.ancientDebris', 4],
};

/** Config key and min tier for an ore block type id, or undefined when it is not a paying ore. */
export function oreInfo(blockTypeId: string): { key: string; minTier: number; canonical: string } | undefined {
  const id = blockTypeId.replace(/^minecraft:/, '').replace(/^deepslate_/, '').replace(/^lit_deepslate_/, 'lit_');
  const hit = ORES[id];
  return hit ? { key: hit[0], minTier: hit[1], canonical: id === 'lit_redstone_ore' ? 'redstone_ore' : id } : undefined;
}

/** Does breaking this ore pay? (correct tool tier, no Silk Touch, not creative, not placed debris). */
export function orePays(o: { minTier: number; toolTier: number; silkTouch: boolean; creative: boolean; placedDebris: boolean }): boolean {
  return !o.creative && !o.silkTouch && !o.placedDebris && o.toolTier >= o.minTier;
}

// ---------------------------------------------------------------------------------------
// Mobs (§3.4.2)

const COMMON = ['zombie', 'husk', 'drowned', 'zombie_villager', 'zombie_villager_v2', 'skeleton', 'stray', 'bogged', 'spider', 'cave_spider', 'silverfish', 'endermite', 'slime', 'magma_cube', 'vex', 'zombie_pigman'];
const MOB_KEYS: Record<string, string> = {
  ...Object.fromEntries(COMMON.map((m) => [m, 'economy.mob.common'])),
  creeper: 'economy.mob.creeper',
  phantom: 'economy.mob.phantom',
  pillager: 'economy.mob.pillager',
  piglin: 'economy.mob.piglin',
  enderman: 'economy.mob.enderman',
  blaze: 'economy.mob.blaze',
  hoglin: 'economy.mob.hoglin',
  zoglin: 'economy.mob.hoglin',
  guardian: 'economy.mob.guardian',
  witch: 'economy.mob.witch',
  vindicator: 'economy.mob.vindicator',
  wither_skeleton: 'economy.mob.witherSkeleton',
  creaking: 'economy.mob.creaking',
  ghast: 'economy.mob.ghast',
  breeze: 'economy.mob.breeze',
  shulker: 'economy.mob.shulker',
  piglin_brute: 'economy.mob.piglinBrute',
  evocation_illager: 'economy.mob.evoker',
  ravager: 'economy.mob.ravager',
  elder_guardian: 'economy.mob.elderGuardian',
  warden: 'economy.mob.warden',
  wither: 'economy.mob.wither',
  ender_dragon: 'economy.mob.enderDragonFirst',
};

/**
 * Config key paying for a killed entity type, or undefined (passive mobs, our own entities).
 * Slimes/magma cubes pay only at size >= 2. The dragon uses the repeat key after the first kill.
 */
export function mobRewardKey(entityTypeId: string, o: { size?: number; dragonKilledBefore?: boolean } = {}): string | undefined {
  if (!entityTypeId.startsWith('minecraft:')) return undefined;
  const id = entityTypeId.slice('minecraft:'.length);
  if ((id === 'slime' || id === 'magma_cube') && (o.size ?? 2) < 2) return undefined;
  if (id === 'ender_dragon' && o.dragonKilledBefore) return 'economy.mob.enderDragonRepeat';
  return MOB_KEYS[id];
}

/** Difficulty multiplier (§2.3): Hard/Hardcore x hardMultiplier, else x1. */
export const difficultyMultiplier = (difficulty: 'peaceful' | 'easy' | 'normal' | 'hard', hardcore: boolean, hardMultiplier: number): number =>
  difficulty === 'hard' || hardcore ? hardMultiplier : 1;

/**
 * Diminishing returns: `killsInWindow` includes the current kill (1-based). Kills 1..full pay
 * 100 %, up to `reduced` pay `factor` (floored), beyond that 0.
 */
export function mobReward(base: number, killsInWindow: number, multiplier: number, cfg: { full: number; reduced: number; factor: number }): number {
  const scaled = base * multiplier;
  if (killsInWindow <= cfg.full) return Math.floor(scaled);
  if (killsInWindow <= cfg.reduced) return Math.max(0, Math.floor(scaled * cfg.factor));
  return 0;
}

/** Sliding window of kill ticks per (player, mob type). Returns the count including `now`. */
export function recordKill(ticks: number[], now: number, windowTicks: number): number {
  while (ticks.length && ticks[0]! <= now - windowTicks) ticks.shift();
  ticks.push(now);
  return ticks.length;
}

// ---------------------------------------------------------------------------------------
// Trading (§3.4.3)

/** chips = min(cap, base + perEmerald × emeralds), limited by what is left of the daily cap. */
export function tradeReward(emeralds: number, cfg: { base: number; perEmerald: number; perTradeCap: number; dailyCap: number }, earnedToday: number): number {
  const raw = Math.min(cfg.perTradeCap, cfg.base + cfg.perEmerald * Math.max(0, Math.floor(emeralds)));
  return Math.max(0, Math.min(raw, cfg.dailyCap - earnedToday));
}

/** Minecraft day index of an absolute world tick (1 MCD = 24000 ticks). */
export const mcDay = (absoluteTick: number): number => Math.floor(absoluteTick / 24000);
