/**
 * The BOT random stream (BOTS.md §4.1). PURE. Bots use ONLY this stream (think delays, mixed
 * strategies, Monte-Carlo samples, names, personalities, quips) — never `mathRng`, which is the Bedrock
 * GAME rng (shuffles, dice, tapes). Seeded sfc32 from `Date.now()`, the table key hash and a session
 * counter (or `debug.fixedSeed ^ 0xB07`) — never from Math.random().
 */
import type { Rng } from '../rng';

/** sfc32 PRNG. */
export function sfc32(a: number, b: number, c: number, d: number): Rng {
  let s0 = a >>> 0;
  let s1 = b >>> 0;
  let s2 = c >>> 0;
  let s3 = d >>> 0;
  const next = (): number => {
    const t = (((s0 + s1) >>> 0) + s3) >>> 0;
    s3 = (s3 + 1) >>> 0;
    s0 = s1 ^ (s1 >>> 9);
    s1 = (s2 + (s2 << 3)) >>> 0;
    s2 = (s2 << 21) | (s2 >>> 11);
    s2 = (s2 + t) >>> 0;
    return t / 4294967296;
  };
  for (let i = 0; i < 12; i++) next(); // warm-up
  return { next };
}

/** 32-bit FNV-1a of a string (table key). */
export function hash32(s: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

/** New bot stream for a table session / match. `fixedSeed` ≠ 0 → deterministic (tests). */
export function botRng(tableKey: string, sessionCounter: number, nowMs: number, fixedSeed = 0): Rng {
  if (fixedSeed !== 0) return sfc32(fixedSeed ^ 0xb07, hash32(tableKey), sessionCounter, 0x9e3779b9);
  return sfc32(nowMs & 0xffffffff, Math.floor(nowMs / 4294967296), hash32(tableKey), sessionCounter);
}
