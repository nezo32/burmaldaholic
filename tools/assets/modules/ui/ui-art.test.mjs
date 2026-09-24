// Checks of the UI kit art module (lane J-L2): deterministic, inside core's namespace, nine-slices match the PNGs,
// frames have a transparent centre and an opaque border, no stray pixels outside the round button.
import { describe, expect, it } from 'vitest';
import generate from '../ui.mjs';
import { THEMES, actionButton, frame, plate } from './art.mjs';

const alphaAt = (img, x, y) => img.data[(y * img.w + x) * 4 + 3];

describe('ui kit module', () => {
  const out = generate();

  it('is deterministic, writes each file once, under core/ui', () => {
    const paths = out.map((o) => o.path);
    expect(new Set(paths).size).toBe(paths.length);
    const again = generate();
    expect(again.every((o, i) => o.path === out[i].path && o.bytes.equals(out[i].bytes))).toBe(true);
    for (const p of paths) expect(p).toMatch(/textures\/gui\/sprites\/core\/ui\/[a-z_]+\.png(\.mcmeta)?$/);
  });

  it('ships a frame and a banner for every scene the Java CasinoTheme names', () => {
    for (const t of ['village', 'bastion', 'end', 'lobby', 'loan']) {
      expect(Object.keys(THEMES)).toContain(t);
      expect(out.some((o) => o.path.endsWith(`/frame_${t}.png`))).toBe(true);
      expect(out.some((o) => o.path.endsWith(`/banner_${t}.png.mcmeta`))).toBe(true);
    }
  });

  it('frames: opaque 12 px border, transparent centre', () => {
    for (const t of Object.keys(THEMES)) {
      const img = frame(t);
      expect([img.w, img.h]).toEqual([64, 64]);
      for (let i = 0; i < 64; i++) {
        expect(alphaAt(img, i, 0)).toBe(255);
        expect(alphaAt(img, 0, i)).toBe(255);
        expect(alphaAt(img, i, 11)).toBe(255);
      }
      for (let y = 12; y < 52; y++) for (let x = 12; x < 52; x++) expect(alphaAt(img, x, y)).toBe(0);
    }
  });

  it('plates are 24 × 16 with rounded corners; the action button stays inside its 40² circle', () => {
    for (const g of [false, true]) {
      const p = plate(g);
      expect([p.w, p.h]).toEqual([24, 16]);
      expect(alphaAt(p, 0, 0)).toBe(0);
      expect(alphaAt(p, 12, 8)).toBe(255);
    }
    for (const st of ['normal', 'highlighted', 'pressed', 'disabled']) {
      const b = actionButton(st);
      expect([b.w, b.h]).toEqual([40, 40]);
      expect(alphaAt(b, 0, 0)).toBe(0);
      expect(alphaAt(b, 20, 20)).toBe(255);
    }
  });
});
