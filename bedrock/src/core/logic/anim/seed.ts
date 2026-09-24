/**
 * Cosmetic seeds + mulberry32, bit-identical to Java `core.anim.SeedMix` (docs/architecture/animation.md §3.5).
 * Cosmetic randomness comes ONLY from here, seeded by public values — never from the game or bot RNG.
 */
export function fmix(h: number): number {
  h ^= h >>> 16;
  h = Math.imul(h, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return h | 0;
}

/** Order-sensitive mix of 32-bit parts. */
export function seedMix(...parts: number[]): number {
  let h = 0x811c9dc5 | 0;
  for (const p of parts) h = fmix((Math.imul(h ^ p, 0x01000193) + 0x7f4a7c15) | 0);
  return h;
}

/** Stable hash of a string (UTF-16 code units, as Java `String.charAt`). */
export function seedHash(s: string): number {
  let h = 0x811c9dc5 | 0;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 0x01000193);
  return fmix(h);
}

/** mulberry32 — the same sequence as Java `SeedMix.FxRng`. */
export class FxRng {
  private state: number;
  constructor(seed: number) {
    this.state = seed | 0;
  }
  nextU32(): number {
    this.state = (this.state + 0x6d2b79f5) | 0;
    let t = this.state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return (t ^ (t >>> 14)) >>> 0;
  }
  /** Uniform in [0, bound), bound ≤ 2^20. */
  nextInt(bound: number): number {
    if (bound <= 0 || bound > 1 << 20) throw new RangeError(`bound ${bound}`);
    return Math.floor((this.nextU32() * bound) / 4294967296);
  }
  nextDouble(): number {
    return this.nextU32() / 4294967296;
  }
}
