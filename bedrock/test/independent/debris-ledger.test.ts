/// <reference types="node" />
/**
 * Regression (tester): the placed-debris ledger (GAME_DESIGN §3.4.1) must honour
 * CONFIG.md `economy.ore.placedDebrisLedgerSize` (FIFO, default 4096) instead of a hard-coded
 * 65 536, and keep every chunk under the 32 767-char dynamic property limit.
 */
import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it, vi } from 'vitest';

const props = new Map<string, unknown>();
vi.mock('@minecraft/server', () => ({
  world: {
    getDynamicProperty: (k: string) => props.get(k),
    setDynamicProperty: (k: string, v: unknown) => (v === undefined ? props.delete(k) : props.set(k, v)),
  },
}));
const { ChunkedList } = await import('../../src/core/store');

describe('ChunkedList (placed-debris ledger)', () => {
  it('evicts the oldest entries beyond a live cap', () => {
    props.clear();
    let cap = 3;
    const l = new ChunkedList('t:debris', () => cap);
    for (const k of ['a', 'b', 'c', 'd', 'e']) l.add(k);
    expect(['a', 'b', 'c', 'd', 'e'].map((k) => l.has(k))).toEqual([false, false, true, true, true]);
    cap = 1;
    l.add('f');
    expect(['d', 'e', 'f'].map((k) => l.has(k))).toEqual([false, false, true]);
    // reload from the persisted chunks
    const again = new ChunkedList('t:debris', () => cap);
    expect(again.has('f')).toBe(true);
    expect(again.has('e')).toBe(false);
  });
  it('splits large ledgers into chunks below the dynamic property limit', () => {
    props.clear();
    const l = new ChunkedList('t:big', 5000);
    for (let i = 0; i < 5000; i++) l.add(`minecraft:overworld:${i},${-i},${i * 7}`);
    const chunks = [...props.entries()].filter(([k]) => k.startsWith('t:big.'));
    expect(chunks.length).toBeGreaterThan(1);
    for (const [, v] of chunks) expect((v as string).length).toBeLessThanOrEqual(32_767);
    expect(new ChunkedList('t:big', 5000).has('minecraft:overworld:4999,-4999,34993')).toBe(true);
  });
  it('core earning wires the ledger size to the CONFIG.md key', () => {
    const src = fs.readFileSync(path.resolve(__dirname, '../../src/core/earning.ts'), 'utf8');
    expect(src).toMatch(/new ChunkedList\([^;]*economy\.ore\.placedDebrisLedgerSize/);
  });
});
