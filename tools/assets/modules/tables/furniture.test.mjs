import { describe, expect, it } from 'vitest';
import generate, { FELT_TOP, PARTS, TABLES, partModel, tableBoxes } from './furniture.mjs';

const out = generate();
const byPath = new Map(out.map((o) => [o.path, JSON.parse(o.path.endsWith('.json') ? o.bytes.toString() : '{}')]));

describe('multi-block table furniture', () => {
  it('is deterministic and has unique paths', () => {
    const again = generate();
    expect(again.length).toBe(out.length);
    again.forEach((o, i) => expect(o.bytes.equals(out[i].bytes)).toBe(true));
    expect(new Set(out.map((o) => o.path)).size).toBe(out.length);
  });

  it('emits 28 variants per table block', () => {
    for (const t of TABLES) {
      const bs = byPath.get(`src/main/resources/assets/burmaldaholic/blockstates/${t.name}.json`);
      expect(Object.keys(bs.variants).length).toBe(4 * PARTS.length);
    }
  });

  it('keeps every element inside its block and the felt at 14 px', () => {
    for (const t of TABLES)
      for (const part of PARTS.slice(1)) {
        const m = partModel(t, part);
        for (const e of m.elements) {
          for (let i = 0; i < 3; i++) {
            expect(e.from[i]).toBeGreaterThanOrEqual(0);
            expect(e.to[i]).toBeLessThanOrEqual(16);
            expect(e.from[i]).toBeLessThanOrEqual(e.to[i]);
          }
          if (e.faces.up.texture === '#felt') expect(e.to[1]).toBe(FELT_TOP);
        }
      }
  });

  it('never lets a box cross a cell boundary', () => {
    for (const shape of ['crescent', 'oval', 'rect', 'roulette'])
      for (const b of tableBoxes(shape)) {
        expect(Math.floor(b.x0 / 16)).toBe(Math.floor((b.x1 - 1e-6) / 16));
        expect(Math.floor(b.z0 / 16)).toBe(Math.floor((b.z1 - 1e-6) / 16));
      }
  });
});
