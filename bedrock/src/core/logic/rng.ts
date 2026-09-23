/**
 * Random number source. PURE. Games take an `Rng` parameter so tests can inject a
 * seeded generator; production uses `mathRng` (Math.random is available in Bedrock QuickJS).
 */
export interface Rng {
  /** Uniform float in [0, 1). */
  next(): number;
}

export const mathRng: Rng = { next: () => Math.random() };

/** Deterministic seeded RNG (mulberry32) for tests / replays. */
export function seededRng(seed: number): Rng {
  let s = seed >>> 0;
  return {
    next() {
      s = (s + 0x6d2b79f5) >>> 0;
      let r = s;
      r = Math.imul(r ^ (r >>> 15), r | 1);
      r ^= r + Math.imul(r ^ (r >>> 7), r | 61);
      return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    },
  };
}

/** Integer in [min, max] inclusive. */
export function randInt(rng: Rng, min: number, max: number): number {
  return min + Math.floor(rng.next() * (max - min + 1));
}

export function chance(rng: Rng, p: number): boolean {
  return rng.next() < p;
}

export function pick<T>(rng: Rng, items: readonly T[]): T {
  if (items.length === 0) throw new Error('pick from empty list');
  return items[Math.floor(rng.next() * items.length)] as T;
}

/** Weighted pick; weights must be >= 0 and not all zero. */
export function weightedPick<T>(rng: Rng, entries: readonly (readonly [T, number])[]): T {
  const total = entries.reduce((s, [, w]) => s + w, 0);
  if (!(total > 0)) throw new Error('weightedPick: total weight must be > 0');
  let r = rng.next() * total;
  for (const [v, w] of entries) {
    r -= w;
    if (r < 0) return v;
  }
  return entries[entries.length - 1]![0];
}

/** Fisher-Yates, returns a new array. */
export function shuffle<T>(rng: Rng, items: readonly T[]): T[] {
  const a = items.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1));
    [a[i], a[j]] = [a[j] as T, a[i] as T];
  }
  return a;
}
