// Checks of the core asset module (lane X-L0): glyph sheet E1 covers every range of the glyph map
// (docs/architecture/animation.md §6) and nothing else, both editions get identical sheet bytes, outputs are
// deterministic, inside core's namespace and within the sprite budget (global.md §2.9).
import { describe, expect, it } from 'vitest';
import { GLYPH_RANGES } from '../../src/core/logic/anim/glyph-map';
import { buildGlyphSheet } from './modules/core/glyphs.mjs';
import generate, { atlasRows } from './modules/core.mjs';

const hex = (cp) => `U+${cp.toString(16).toUpperCase()}`;

describe('glyph sheet E1', () => {
  const { used } = buildGlyphSheet();
  const e1 = GLYPH_RANGES.filter((r) => r.from >> 8 === 0xe1);
  // the card range reserves E145–E14F; everything else in a non-reserve range is drawn
  const expectedGap = (cp) => cp >= 0xe145 && cp <= 0xe14f;

  it('draws every code point of every E1 range of the glyph map', () => {
    const missing = [];
    for (const r of e1.filter((x) => x.spec !== 'reserved'))
      for (let cp = r.from; cp <= r.to; cp++) if (!used.has(cp) && !expectedGap(cp)) missing.push(`${hex(cp)} (${r.what})`);
    expect(missing).toEqual([]);
  });

  it('leaves reserves empty and draws nothing outside the map', () => {
    const stray = [...used].filter((cp) => {
      const r = GLYPH_RANGES.find((x) => cp >= x.from && cp <= x.to);
      return !r || r.spec === 'reserved' || expectedGap(cp);
    });
    expect(stray.map(hex)).toEqual([]);
  });
});

describe('core asset module', () => {
  const out = generate();
  const byPath = new Map(out.map((o) => [`${o.edition}:${o.path}`, o]));

  it('is deterministic and writes each file once', () => {
    expect(byPath.size).toBe(out.length);
    const again = generate();
    expect(again.every((o, i) => o.path === out[i].path && o.bytes.equals(out[i].bytes))).toBe(true);
  });

  it('ships the same glyph sheet bytes to both editions', () => {
    const b = byPath.get('bedrock:packs/core/RP/font/glyph_E1.png');
    const j = byPath.get('java:src/main/resources/assets/burmaldaholic/textures/font/core/glyph_e1.png');
    expect(b && j && b.bytes.equals(j.bytes)).toBe(true);
  });

  it('stays inside core ownership (Java `core/` folder, Bedrock core pack)', () => {
    for (const o of out) {
      if (o.edition === 'java') expect(o.path.split('/')).toContain('core');
      else expect(o.path.startsWith('packs/core/')).toBe(true);
    }
  });

  it('keeps the GUI fx sprites under 64 KB (global.md §2.9)', () => {
    const fx = out.filter((o) => o.path.includes('/gui/sprites/core/fx/')).reduce((s, o) => s + o.bytes.length, 0);
    expect(fx).toBeLessThan(64 * 1024);
  });

  it('lays the particle atlas out exactly as ATLAS_ROWS of core/logic/anim/particle-atlas.ts (lane B-L1)', () => {
    // row, sprite, frames — mirror of B-L1's ATLAS_ROWS (8 × 8 cells); replace with an import once merged
    const expected = [
      ['chip', 4], ['chip_glint', 4], ['sparkle', 4], ['gold_burst', 4], ['golden_mote', 2], ['diamond_glint', 4],
      ['curse_wisp', 3], ['summon_rune', 4], ['teleport_ring', 4], ['collector_smoke', 4], ['coin', 8], ['confetti', 8],
      ['foil_flake', 4],
    ];
    const rows = atlasRows();
    expect(Object.entries(rows).map(([name, r]) => [name, r.frames, r.y, r.w, r.h])).toEqual(expected.map(([n, f], i) => [n, f, i * 8, 8, 8]));
  });
});
