import { describe, expect, it } from 'vitest';
import generate from '../tables.mjs';
import { PRINT_H, PRINT_W, PUCK_FRAMES, PUCK_SIZES, ROWS_Y, STICK_WIDTHS, WALL, WALL_HEIGHTS, backWallStrip, printGeometry, puckSheet, stickHook } from './craps_v2.mjs';

// CM craps motion art (CRAPS-ANIM-SPEC §8): sizes, frame counts and the geometry contracts the Java side codes against.
const out = generate();
const byPath = new Map(out.map((o) => [o.path, o]));
const size = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
const T = 'src/main/resources/assets/burmaldaholic/textures';
const at = (img, x, y) => img.data[(y * img.w + x) * 4 + 3];

describe('craps v2 motion art (CM)', () => {
  it('back wall strips: 16 × 3H, IDLE opaque and seamless, HIT / HOT overlays only on diamond 0', () => {
    for (const h of WALL_HEIGHTS) {
      expect(size(byPath.get(`${T}/gui/tables/craps/v2/back_wall_${h}.png`).bytes)).toEqual([16, 3 * h]);
      const img = backWallStrip(h);
      for (let y = 0; y < h; y++) for (let x = 0; x < 16; x++) expect(at(img, x, y)).toBe(255);
      // pitch 4: every diamond column of the IDLE row is the same pixels
      for (let y = 0; y < h; y++)
        for (let x = 0; x < 4; x++)
          for (const i of [1, 2, 3]) expect(img.data.slice(((y * 16) + x) * 4, ((y * 16) + x) * 4 + 4)).toEqual(img.data.slice(((y * 16) + 4 * i + x) * 4, ((y * 16) + 4 * i + x) * 4 + 4));
      for (const row of [1, 2]) {
        let lit = 0;
        for (let y = row * h; y < (row + 1) * h; y++)
          for (let x = 0; x < 16; x++) {
            if (x >= 4) expect(at(img, x, y)).toBe(0);
            else if (at(img, x, y)) lit++;
          }
        expect(lit).toBeGreaterThan(0);
      }
    }
  });

  it('stick: n × 8 shaft tiles and 5n × 3n hooks whose shaft columns match the tile', () => {
    for (const n of STICK_WIDTHS) {
      expect(size(byPath.get(`${T}/gui/tables/craps/v2/stick_shaft_${n}.png`).bytes)).toEqual([n, 8]);
      expect(size(byPath.get(`${T}/gui/tables/craps/v2/stick_hook_${n}.png`).bytes)).toEqual([5 * n, 3 * n]);
      const hook = stickHook(n);
      for (let x = 0; x < 5 * n; x++) expect(at(hook, x, 0) > 0).toBe(x >= 2 * n && x < 3 * n);
      // the blade reaches the bottom centre (the hook point)
      expect(at(hook, Math.floor(2.5 * n), 3 * n - 1) + at(hook, Math.floor(2.5 * n), 3 * n - 2)).toBeGreaterThan(0);
    }
  });

  it('puck: S × 7S strips of 7 frames for 12 / 16 / 20 / 26', () => {
    expect(PUCK_FRAMES).toBe(7);
    for (const s of PUCK_SIZES) {
      expect(size(byPath.get(`${T}/gui/tables/craps/v2/puck_${s}.png`).bytes)).toEqual([s, 7 * s]);
      const img = puckSheet(s);
      // the OFF face is dark and the ON face light at their centres
      const lum = (fy) => {
        const o = ((fy * s + (s >> 1)) * s + (s >> 1)) * 4;
        return img.data[o] + img.data[o + 1] + img.data[o + 2];
      };
      expect(lum(0)).toBeLessThan(200);
      expect(lum(6)).toBeGreaterThan(600);
    }
  });

  it('in-world print is 256 × 176 with the wall strip on top and rows below it', () => {
    expect(size(byPath.get(`${T}/entity/craps/board_print.png`).bytes)).toEqual([PRINT_W, PRINT_H]);
    expect([PRINT_W, PRINT_H]).toEqual([256, 176]);
    expect(WALL).toEqual({ x: 4, y: 4, w: 248, h: 6 });
    const g = printGeometry();
    expect(g.dontCome.y).toBe(ROWS_Y);
    expect(g.points[0].y).toBe(ROWS_Y);
    expect(g.wall.y + g.wall.h).toBeLessThan(ROWS_Y);
    expect(g.pass.y + g.pass.h).toBe(PRINT_H - 4);
    expect(g.come.y).toBe(g.points[0].y + g.points[0].h + 3);
    expect(g.field.y).toBe(g.come.y + g.come.h + 3);
  });

  it('in-world wall and puck textures', () => {
    expect(size(byPath.get(`${T}/entity/craps/wall.png`).bytes)).toEqual([64, 16]);
    expect(size(byPath.get(`${T}/entity/craps/puck.png`).bytes)).toEqual([32, 16]);
  });
});
