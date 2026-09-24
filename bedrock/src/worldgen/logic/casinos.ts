/**
 * Built casinos: persisted records, bounds, table presets, NPC respawn bookkeeping. PURE.
 */
import { type CasinoKind, type Layout, type TablePresetId, type TableSlot, type Vec3, layout as layoutById } from './layouts';

export interface CasinoRecord {
  readonly id: string;
  /** layout / template id */
  readonly layout: string;
  readonly kind: CasinoKind;
  readonly dim: string;
  /** world position of the template's min corner */
  readonly origin: Vec3;
}

export interface Box {
  readonly min: Vec3;
  readonly max: Vec3;
}

/** Inclusive block bounds of a casino. */
export function casinoBox(rec: CasinoRecord, l: Layout): Box {
  return {
    min: rec.origin,
    max: { x: rec.origin.x + l.size.x - 1, y: rec.origin.y + l.size.y - 1, z: rec.origin.z + l.size.z - 1 },
  };
}

export function inBox(b: Box, p: Vec3, margin = 0): boolean {
  return (
    p.x >= b.min.x - margin &&
    p.x < b.max.x + 1 + margin &&
    p.y >= b.min.y - margin &&
    p.y < b.max.y + 1 + margin &&
    p.z >= b.min.z - margin &&
    p.z < b.max.z + 1 + margin
  );
}

export function findCasinoAt(records: readonly CasinoRecord[], dim: string, p: Vec3, margin = 0): CasinoRecord | undefined {
  for (const r of records) {
    if (r.dim !== dim) continue;
    const l = layoutById(r.layout);
    if (l && inBox(casinoBox(r, l), p, margin)) return r;
  }
  return undefined;
}

/** The layout table slot at a world block position (tables are 1 block). */
export function tableSlotAt(rec: CasinoRecord, p: Vec3): TableSlot | undefined {
  const l = layoutById(rec.layout);
  if (!l) return undefined;
  const lx = Math.floor(p.x) - rec.origin.x;
  const ly = Math.floor(p.y) - rec.origin.y;
  const lz = Math.floor(p.z) - rec.origin.z;
  return l.tables.find((t) => t.pos.x === lx && t.pos.y === ly && t.pos.z === lz);
}

// ---- presets (GAME_DESIGN §6.4, §7.1, §16) ----------------------------------------------------

export interface PokerPreset {
  readonly stakes: 'micro' | 'low' | 'mid' | 'high';
  readonly bots: number;
  /** "Regular-heavy" bot tier mix */
  readonly botMix: 'regular_heavy';
}

export interface TablePreset {
  readonly id: TablePresetId;
  /** worldgen tables are always bank-funded house tables */
  readonly house: true;
  readonly minBet?: number;
  /** max = tierMultiplier × VIP tier max */
  readonly tierMultiplier?: number;
  /** minimum VIP tier index (0 Bronze … 5 Netherite) */
  readonly minTier?: number;
  readonly poker?: PokerPreset;
}

export const PRESETS: Readonly<Record<TablePresetId, TablePreset>> = {
  standard: { id: 'standard', house: true },
  parlor_poker: { id: 'parlor_poker', house: true, poker: { stakes: 'low', bots: 3, botMix: 'regular_heavy' } },
  high_roller_blackjack: { id: 'high_roller_blackjack', house: true, minBet: 100, tierMultiplier: 2, minTier: 2 },
  high_roller_roulette: { id: 'high_roller_roulette', house: true, minBet: 100 },
  // §16.3: min 100 per coup, max 2× tier, Gold VIP (baccarat.highRoller* defaults)
  high_roller_baccarat: { id: 'high_roller_baccarat', house: true, minBet: 100, tierMultiplier: 2, minTier: 2 },
  // §16.3: min Ante 50, W ≤ 2× tier max, Gold VIP (uth.highRoller* defaults)
  high_roller_uth: { id: 'high_roller_uth', house: true, minBet: 50, tierMultiplier: 2, minTier: 2 },
};

// ---- persistence -------------------------------------------------------------------------------

/** Compact persisted record: `id|layout|dim|x|y|z`. */
export function encodeCasino(r: CasinoRecord): string {
  return [r.id, r.layout, r.dim, r.origin.x, r.origin.y, r.origin.z].join('|');
}

export function decodeCasino(raw: string): CasinoRecord | undefined {
  const [id, lay, dim, x, y, z] = raw.split('|');
  const l = lay ? layoutById(lay) : undefined;
  const o = [x, y, z].map(Number);
  if (!id || !l || !dim || o.some((v) => !Number.isFinite(v))) return undefined;
  return { id, layout: l.id, kind: l.kind, dim, origin: { x: o[0] as number, y: o[1] as number, z: o[2] as number } };
}

export function newCasinoId(kind: CasinoKind, origin: Vec3): string {
  const k = { village_casino: 'v', piglin_parlor: 'p', high_roller: 'h' }[kind];
  return `${k}${origin.x}_${origin.y}_${origin.z}`;
}

// ---- NPC respawn ------------------------------------------------------------------------------

/** Minimum gap between two "missing" observations before a respawn (chunk/entity load race). */
export const MIN_MISSING_TICKS = 200;

/**
 * NPC slot bookkeeping: an NPC that is not found is respawned once it has been missing for
 * `respawnTicks` (worldgen.loanSharkRespawnTicks, 1 MCD by default) and was seen missing at least
 * twice. Returns the new `missingSince` and whether to respawn now.
 */
export function respawnDecision(present: boolean, missingSince: number | undefined, now: number, respawnTicks: number): { missingSince?: number; respawn: boolean } {
  if (present) return { missingSince: undefined, respawn: false };
  if (missingSince === undefined) return { missingSince: now, respawn: false };
  const due = missingSince + Math.max(respawnTicks, MIN_MISSING_TICKS);
  return now >= due ? { missingSince: undefined, respawn: true } : { missingSince, respawn: false };
}

// ---- NPC shops (GAME_DESIGN §16.1 / §16.3, prices §11.3) -------------------------------------

export interface ShopOffer {
  readonly item: string;
  readonly price: number;
  /** VIP tier needed to buy (scratch card tiers, §11.3) */
  readonly minTier: number;
}

export const CROUPIER_OFFERS: readonly ShopOffer[] = [
  { item: 'burmaldaholic:scratch_card', price: 10, minTier: 0 },
  { item: 'burmaldaholic:scratch_card_gold', price: 100, minTier: 1 },
  { item: 'burmaldaholic:lucky_coin', price: 25, minTier: 0 },
];

export const SHULKER_OFFERS: readonly ShopOffer[] = [{ item: 'burmaldaholic:scratch_card_gold', price: 100, minTier: 1 }];

export type PurchaseCheck = 'ok' | 'vip' | 'funds';

export function checkPurchase(offer: ShopOffer, balance: number, tier: number): PurchaseCheck {
  if (tier < offer.minTier) return 'vip';
  if (balance < offer.price) return 'funds';
  return 'ok';
}

/** Greeting line counts per NPC (STRINGS.md `dialog.burmaldaholic.<npc>.greeting.N`). */
export const GREETINGS = { croupier: 3, piglin_dealer: 3, shulker_croupier: 2 } as const;
