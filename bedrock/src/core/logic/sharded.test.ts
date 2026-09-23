import { describe, expect, it } from 'vitest';
import { type KvHolder, SHARD_CHARS, readSharded, shardKey, splitShards, writeSharded } from './sharded';

class Fake implements KvHolder {
  readonly m = new Map<string, string>();
  getDynamicProperty(id: string): unknown {
    return this.m.get(id);
  }
  setDynamicProperty(id: string, value?: string): void {
    if (value === undefined) this.m.delete(id);
    else {
      if (value.length > 32_767) throw new Error('too long');
      this.m.set(id, value);
    }
  }
}

describe('sharded world stores', () => {
  it('splits into pieces of at most size chars and joins back', () => {
    expect(splitShards('')).toEqual([]);
    expect(splitShards('abcdefg', 3)).toEqual(['abc', 'def', 'g']);
    const s = 'x'.repeat(SHARD_CHARS * 2 + 5);
    const parts = splitShards(s);
    expect(parts.map((p) => p.length)).toEqual([SHARD_CHARS, SHARD_CHARS, 5]);
    expect(parts.join('')).toBe(s);
  });

  it('never splits a surrogate pair', () => {
    const s = 'ab😀cd'; // 😀 = 2 UTF-16 units at index 2..3
    const parts = splitShards(s, 3);
    expect(parts.join('')).toBe(s);
    for (const p of parts) expect(p).not.toMatch(/^[\uDC00-\uDFFF]|[\uD800-\uDBFF]$/);
  });

  it('round-trips a map of a few thousand players (> 32 KB) over several keys', () => {
    const h = new Fake();
    const big: Record<string, number> = {};
    for (let i = 0; i < 3000; i++) big[`-42949672${String(i).padStart(4, '0')}`] = i * 7;
    writeSharded(h, 'burmaldaholic:test.map', big);
    expect(h.m.size).toBeGreaterThan(1);
    expect([...h.m.keys()].every((k) => k.startsWith('burmaldaholic:test.map:'))).toBe(true);
    expect(readSharded(h, 'burmaldaholic:test.map', {})).toEqual(big);
  });

  it('removes stale shards when the value shrinks and clears on undefined', () => {
    const h = new Fake();
    writeSharded(h, 'k', 'y'.repeat(100), 10);
    expect(h.m.has(shardKey('k', 10))).toBe(true);
    writeSharded(h, 'k', 'z', 10);
    expect([...h.m.keys()]).toEqual([shardKey('k', 0)]);
    expect(readSharded(h, 'k', '')).toBe('z');
    writeSharded(h, 'k', undefined);
    expect(h.m.size).toBe(0);
    expect(readSharded(h, 'k', 'fallback')).toBe('fallback');
  });

  it('migrates the legacy single-key value and drops it on the next write', () => {
    const h = new Fake();
    h.m.set('legacy', JSON.stringify({ a: 1 }));
    expect(readSharded(h, 'legacy', {})).toEqual({ a: 1 });
    writeSharded(h, 'legacy', { a: 1, b: 2 });
    expect(h.m.has('legacy')).toBe(false);
    expect(readSharded(h, 'legacy', {})).toEqual({ a: 1, b: 2 });
  });

  it('corrupt data yields the fallback', () => {
    const h = new Fake();
    h.m.set(shardKey('bad', 0), '{"a":');
    expect(readSharded(h, 'bad', 7)).toBe(7);
  });
});
