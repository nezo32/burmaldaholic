import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import generate from '../slots.mjs';
import { art } from './art.mjs';
import { SHARED_ICONS } from './icons.mjs';
import { LEGACY_BASE, LEGACY_V1 } from './legacy-v1.mjs';
import { alphaAt } from './raster.mjs';
import { planeCells } from './sheets.mjs';
import { parseAppendixA } from './strips.mjs';
import { CODES, MACHINE_ORDER, MACHINE_SYMBOLS, validateArt } from './symbols.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const SLOTS_MD = fs.readFileSync(path.resolve(ROOT, '../docs/design/SLOTS.md'), 'utf8');

describe('slots art: sources', () => {
  it('has 11 valid 16 × 16 symbols per machine in SLOTS.md §2 code order, and valid shared icons', () => {
    for (const m of MACHINE_ORDER) {
      validateArt(MACHINE_SYMBOLS[m]);
      expect(MACHINE_SYMBOLS[m]).toHaveLength(11);
    }
    validateArt(SHARED_ICONS);
    expect(SHARED_ICONS).toHaveLength(0x244 - 0x230 + 1);
    // SLOTS.md §2 tables list the codes in glyph order
    const tableCodes = [...SLOTS_MD.matchAll(/^\| ([A-Z]{2}) \| `[a-z_]+` \|/gm)].map((x) => x[1]);
    expect(tableCodes).toEqual(MACHINE_ORDER.flatMap((m) => CODES[m]));
  });

  it('parses SLOTS.md Appendix A strips (fallback until the engine exports MachineDef defaults)', () => {
    const s = parseAppendixA(SLOTS_MD);
    expect(s.overworld.strips.map((r) => r.length)).toEqual([40, 40, 40, 40, 40]);
    expect(s.nether.strips.map((r) => r.length)).toEqual([32, 32, 32, 32, 32]);
    expect(s.end.strips.map((r) => r.length)).toEqual([45, 45, 45, 45, 45]);
    // SLOTS.md §3.1: Sweet Berries ×6 and Totems ×0 on Overworld reel 1
    const count = (m, r, code) => s[m].strips[r].filter((i) => CODES[m][i] === code).length;
    expect(count('overworld', 0, 'BE')).toBe(6);
    expect(count('overworld', 0, 'WD')).toBe(0);
    expect(count('nether', 2, 'CN')).toBe(3);
    expect(count('end', 1, 'BN')).toBe(2);
  });

  it('fills every slots glyph of the map (E200–E20A, E210–E21A, E220–E22A, E230–E244) and the v1 legacy cells', () => {
    const cells = planeCells();
    const offsets = [...range(0x00, 0x0a), ...range(0x10, 0x1a), ...range(0x20, 0x2a), ...range(0x30, 0x44), ...range(LEGACY_BASE & 0xff, (LEGACY_BASE & 0xff) + LEGACY_V1.length - 1)];
    for (const o of offsets) {
      const c = cells.get(o);
      expect(c, `U+E2${o.toString(16)}`).toBeDefined();
      for (const k of ['base', 'win', 'blur']) expect(opaque(c[k]), `U+E2${o.toString(16)} ${k}`).toBeGreaterThan(20);
    }
    // no cell outside the reserved ranges
    for (const o of cells.keys()) expect(offsets).toContain(o);
  });

  it('keeps the art readable: every symbol covers a good part of its 16 px cell', () => {
    for (const m of MACHINE_ORDER) for (const s of MACHINE_SYMBOLS[m]) expect(opaque(art(s)), s.id).toBeGreaterThan(40);
  });
});

describe('slots art: generator outputs', () => {
  it('is deterministic, respects ownership and ships the prop files', async () => {
    const a = await generate({ root: ROOT });
    const b = await generate({ root: ROOT });
    expect(a.map((o) => o.path)).toEqual(b.map((o) => o.path));
    a.forEach((o, i) => expect(o.bytes.equals(b[i].bytes), o.path).toBe(true));
    const paths = new Set();
    for (const o of a) {
      expect(paths.has(`${o.edition}:${o.path}`), `duplicate ${o.path}`).toBe(false);
      paths.add(`${o.edition}:${o.path}`);
      if (o.edition === 'bedrock') expect(o.path.startsWith('packs/slots/')).toBe(true);
      // Java checkAssetOwnership: a path segment owned by slots (or pvp for Showdown sprites)
      else expect(o.path.split('/').slice(4).some((seg) => seg === 'slots' || seg === 'pvp' || seg.startsWith('slot_machine'))).toBe(true);
    }
    const get = (p) => JSON.parse(a.find((o) => o.path === p).bytes.toString());
    const be = get('packs/slots/BP/entities/slots/slot_reels.json')['minecraft:entity'].description.properties;
    expect(Object.keys(be).length).toBeLessThanOrEqual(32);
    // every texture the client entity references is generated
    const ce = get('packs/slots/RP/entity/slots/slot_reels.entity.json')['minecraft:client_entity'].description;
    for (const t of Object.values(ce.textures)) expect(paths.has(`bedrock:packs/slots/RP/${t}.png`), t).toBe(true);
    // Java sheets have the D1/D2 sizes
    const size = (p) => {
      const buf = a.find((o) => o.path.endsWith(p)).bytes;
      return [buf.readUInt32BE(16), buf.readUInt32BE(20)];
    };
    expect(size('textures/gui/slots/overworld_symbols.png')).toEqual([640, 440]);
    expect(size('textures/gui/slots/nether_symbols_32.png')).toEqual([512, 352]);
    expect(size('textures/gui/slots/end_symbols_16.png')).toEqual([32, 176]);
    expect(size('packs/slots/RP/font/glyph_E3.png')).toEqual([512, 512]);
  }, 30_000);
});

function range(a, b) {
  return Array.from({ length: b - a + 1 }, (_, i) => a + i);
}
function opaque(img) {
  let n = 0;
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) if (alphaAt(img, x, y)) n++;
  return n;
}
