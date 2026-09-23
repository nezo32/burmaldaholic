/**
 * Casino claims (GAME_DESIGN §18.2). PURE.
 * A claim is a full-height cylinder of radius `radius` around the Casino Charter block.
 */
export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

export interface Casino {
  /** stable id, also the core bankroll id */
  id: string;
  ownerId: string;
  /** last known owner name (for "%1's casino") */
  ownerName: string;
  dimension: string;
  /** charter block position (integer block coordinates) */
  x: number;
  y: number;
  z: number;
  radius: number;
  /** world tick of creation */
  created: number;
  /** true while the bankroll cannot cover the cheapest table (§18.2 insolvency) */
  broke?: boolean;
}

/** Default spawn protection kept free of claims (Bedrock has no readable spawn-protection radius). */
export const SPAWN_BUFFER = 16;

const dist2 = (ax: number, az: number, bx: number, bz: number): number => (ax - bx) ** 2 + (az - bz) ** 2;

/** Whether a point lies inside a casino's claim (same dimension, horizontal distance ≤ radius). */
export function inClaim(c: Pick<Casino, 'dimension' | 'x' | 'z' | 'radius'>, dimension: string, p: Vec3): boolean {
  if (c.dimension !== dimension) return false;
  return dist2(Math.floor(p.x), Math.floor(p.z), c.x, c.z) <= c.radius * c.radius;
}

/** The casino whose claim contains the point (claims never overlap, so at most one). */
export function casinoAt<C extends Casino>(list: readonly C[], dimension: string, p: Vec3): C | undefined {
  return list.find((c) => inClaim(c, dimension, p));
}

export type ClaimCheck = { ok: true } | { ok: false; reason: 'overlap' | 'limit' | 'disabled' };

export interface ClaimRules {
  enabled: boolean;
  radius: number;
  maxPerPlayer: number;
  /** world spawn (overworld) */
  spawn?: Vec3;
  spawnDimension?: string;
  spawnBuffer?: number;
}

/**
 * May `ownerId` claim a casino with its charter at `p`? Claims may not overlap (distance
 * between charters ≥ r1 + r2, horizontally), may not reach into spawn protection, and each
 * player owns at most `maxPerPlayer` casinos.
 */
export function checkClaim(existing: readonly Casino[], ownerId: string, dimension: string, p: Vec3, rules: ClaimRules): ClaimCheck {
  if (!rules.enabled || rules.maxPerPlayer <= 0) return { ok: false, reason: 'disabled' };
  if (existing.filter((c) => c.ownerId === ownerId).length >= rules.maxPerPlayer) return { ok: false, reason: 'limit' };
  const x = Math.floor(p.x);
  const z = Math.floor(p.z);
  for (const c of existing) {
    if (c.dimension !== dimension) continue;
    const r = c.radius + rules.radius;
    if (dist2(x, z, c.x, c.z) < r * r) return { ok: false, reason: 'overlap' };
  }
  if (rules.spawn && (rules.spawnDimension ?? 'minecraft:overworld') === dimension) {
    const r = rules.radius + (rules.spawnBuffer ?? SPAWN_BUFFER);
    if (dist2(x, z, Math.floor(rules.spawn.x), Math.floor(rules.spawn.z)) < r * r) return { ok: false, reason: 'overlap' };
  }
  return { ok: true };
}

/**
 * Break protection for charters and linked tables: only the owner and operators, and only
 * while `ownership.protectTables` is on (the charter itself is always protected).
 */
export function mayBreak(o: { isOwner: boolean; isOperator: boolean; protect: boolean; isCharter: boolean }): boolean {
  if (o.isOwner || o.isOperator) return true;
  return !o.protect && !o.isCharter;
}

/** Next free casino id (`c1`, `c2`...) given the persisted counter. */
export const nextCasinoId = (counter: number): { id: string; counter: number } => ({ id: `c${counter + 1}`, counter: counter + 1 });

/** Table key → position (inverse of core `tableKeyOf`: `<dimension>|x,y,z`). */
export function parseTableKey(key: string): { dimension: string; x: number; y: number; z: number } | undefined {
  const m = /^(.+)\|(-?\d+),(-?\d+),(-?\d+)$/.exec(key);
  if (!m) return undefined;
  return { dimension: m[1]!, x: Number(m[2]), y: Number(m[3]), z: Number(m[4]) };
}
