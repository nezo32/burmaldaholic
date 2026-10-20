// VQA round 2 (findings/VQA-INWORLD.md "MT r2 defects and fix specs" 1–7) proven by geometry and pixel asserts — there
// are no more screenshot rounds (owner 2026-10-20), so every spec item is a deterministic contract here.
import { describe, expect, it } from 'vitest';
import { TABLES, partModel, tableBoxes, tableSegs, railTexture, THEMES } from './furniture.mjs';
import { BOWL, RAIL_TOP, RIM } from './table_geom.mjs';
import { machineGeometry, MACHINES, machinePartModel, partsOf } from './machines.mjs';
import * as H from './art_hd.mjs';
import * as A from './machine_art.mjs';
import { get } from '../slots/raster.mjs';
import { c } from './draw.mjs';

const hex = (img, x, y) => `#${get(img, x, y).slice(0, 3).map((v) => v.toString(16).padStart(2, '0').toUpperCase()).join('')}`;
const lum = ([r, g, b]) => 0.2126 * r + 0.7152 * g + 0.0722 * b;
const SHAPES = ['crescent', 'oval', 'rect', 'roulette'];
const isRed = (p) => p[0] > 180 && p[1] < 80 && p[2] < 80;

describe('r2-1 table-limit placard', () => {
  it('is a lit 6 × 4 px enamel plate on a 1 × 7 px brass stanchion, dealer side, facing the players', () => {
    for (const shape of SHAPES) {
      const boxes = tableBoxes(shape);
      const plates = boxes.filter((b) => b.mat === 'placard');
      expect(plates.length).toBe(1);
      const p = plates[0];
      expect([p.x1 - p.x0, p.y1 - p.y0]).toEqual([6, 4]);
      expect(p.emit).toBeGreaterThanOrEqual(10);
      expect(p.z1).toBeLessThan(0); // dealer half, its +Z face toward the players
      const cx = (p.x0 + p.x1) / 2;
      const post = boxes.find((b) => b.mat === 'trim' && b.x1 - b.x0 === 1 && (b.x0 + b.x1) / 2 === cx && b.y1 - b.y0 === 7);
      expect(post).toBeTruthy();
      expect(post.y0).toBe(14); // stands on the felt
      expect(post.y1).toBeGreaterThan(p.y0); // carries the plate
    }
  });

  it('shows the game icon on white enamel in a gold frame, never a mixed blob', () => {
    const icon = { blackjack: 'spade', baccarat: 'spade', uth: 'spade', poker: 'spade', roulette: 'ball', craps: 'die' };
    for (const t of TABLES) expect(partModel(t, 'back_left').textures.placard).toBe(`burmaldaholic:block/core/tables/placard_${icon[t.game]}`);
    for (const kind of ['spade', 'ball', 'die']) {
      const img = H.placardHd(kind);
      expect(hex(img, 0, 5)).toBe('#E8B830'); // gold frame
      expect(hex(img, 2, 8)).toBe('#FAF6EE'); // enamel
      expect(hex(img, 28, 23)).toBe('#FAF6EE');
      const centre = get(img, 16, 16);
      if (kind === 'spade') expect(lum(centre)).toBeLessThan(40);
      else expect(isRed(centre)).toBe(true);
      const colours = new Set();
      for (let y = 6; y < 26; y++) for (let x = 1; x < 31; x++) colours.add(hex(img, x, y));
      expect(colours.size).toBeLessThanOrEqual(5); // enamel + outline + icon (+ highlight / pips)
    }
  });
});

describe('r2-2 roulette bowl rim', () => {
  const rim = tableSegs('roulette').filter((s) => Math.hypot(s.cx - BOWL.cx, s.cz - BOWL.cz) < BOWL.r + 1);
  const inner = (s) => Math.hypot(s.cx - BOWL.cx, s.cz - BOWL.cz) - s.thick / 2;
  const outer = (s) => Math.hypot(s.cx - BOWL.cx, s.cz - BOWL.cz) + s.thick / 2;

  it('is low (≤ 3 px above the rail) yet tops the RO rotor wall (y 17)', () => {
    const top = Math.max(...rim.map((s) => s.y1));
    expect(top).toBe(RIM.top);
    expect(top - RAIL_TOP).toBeGreaterThan(0);
    expect(top - RAIL_TOP).toBeLessThanOrEqual(3);
    expect(top).toBeGreaterThanOrEqual(17);
  });

  it('keeps out of the RO wheel: nothing inside r 6.5, nothing inside r 6.75 below y 17', () => {
    for (const s of rim) {
      expect(inner(s)).toBeGreaterThanOrEqual(RIM.open - 1e-9);
      if (s.y0 < RIM.lipY) expect(inner(s)).toBeGreaterThanOrEqual(RIM.wallR - 1e-9);
    }
    // the bowl floor stays inside the wall and at 13.5 (RouletteWorld.BOWL_FLOOR_Y)
    const floor = tableBoxes('roulette').filter((b) => b.mat === 'bowl');
    expect(floor.length).toBeGreaterThan(0);
    for (const b of floor) {
      expect(b.y1).toBe(13.5);
      const far = Math.max(...[[b.x0, b.z0], [b.x1, b.z0], [b.x0, b.z1], [b.x1, b.z1]].map(([x, z]) => Math.hypot(x - BOWL.cx, z - BOWL.cz)));
      expect(far).toBeLessThanOrEqual(RIM.wallR + 1.2); // 1-px columns: corners may reach past the chord
    }
  });

  it('steps its outer face in three rings, brass top band lit, dark inner wall, cream ball-track lip', () => {
    const outerAt = (y) => Math.max(...rim.filter((s) => s.y0 <= y && s.y1 > y).map(outer));
    const steps = [14, 16, 17].map(outerAt);
    expect(steps[0]).toBeGreaterThan(steps[1]);
    expect(steps[1]).toBeGreaterThan(steps[2]);
    const band = rim.filter((s) => s.mat === 'trim');
    expect(band.length).toBe(24);
    for (const s of band) {
      expect(s.y1).toBe(RIM.top);
      expect(s.emit).toBeGreaterThan(0);
    }
    const wall = rim.filter((s) => s.mat === 'rail');
    expect(Math.min(...wall.map(inner))).toBeCloseTo(RIM.wallR, 9);
    const lip = rim.filter((s) => s.mat === 'track');
    expect(lip.length).toBe(24);
    for (const s of lip) {
      expect(s.y1).toBe(RIM.top);
      expect(inner(s)).toBeCloseTo(RIM.open, 9);
    }
    expect(rim.every((s) => s.mat !== 'wood' || s.y1 <= 16.5)).toBe(true); // no plain wooden bucket above the band
  });
});

describe('r2-3 slot topper, reels, lever', () => {
  const g = machineGeometry('slot').boxes;
  it('has a 1 × 7 px chrome stick angled 15° with a 2 × 2 red ball on a 2 × 3 chrome hub', () => {
    const stick = g.find((b) => b.mat === 'chrome' && b.rot);
    const ball = g.find((b) => b.mat === 'ball');
    expect([stick.x1 - stick.x0, stick.y1 - stick.y0, stick.z1 - stick.z0]).toEqual([1, 7, 1]);
    expect(Math.abs(stick.rot.angle)).toBe(15);
    expect([ball.x1 - ball.x0, ball.y1 - ball.y0]).toEqual([2, 2]);
    expect(ball.rot).toEqual(stick.rot);
    expect(ball.y0).toBe(stick.y1);
    const hub = g.find((b) => b.mat === 'chrome' && b.x1 - b.x0 === 2 && b.y1 - b.y0 === 3);
    expect(hub.y1).toBe(stick.y0);
    expect(stick.rot.origin[1]).toBe(hub.y1);
  });

  it('lights the topper and reel glass', () => {
    expect(g.find((b) => b.mat === 'topper').emit).toBeGreaterThanOrEqual(14);
    expect(g.find((b) => b.mat === 'reels').emit).toBeGreaterThan(0);
  });

  it('draws three reels of three symbols, a dark pay line and curvature shading', () => {
    for (const [tier, t] of Object.entries(A.SLOT_TIERS)) {
      const img = H.slotReelsHd(tier, t.accent);
      for (let i = 0; i < 3; i++) {
        const x0 = 9 + i * 16;
        expect(lum(get(img, x0 + 1, 16))).toBeLessThan(lum(get(img, x0 + 1, 30))); // curvature: edge darker
        for (let j = 0; j < 3; j++) {
          let ink = 0;
          for (let y = 18 + j * 11; y < 26 + j * 11; y++) for (let x = x0 + 3; x < x0 + 11; x++) if (lum(get(img, x, y)) < 200) ink++;
          expect(ink).toBeGreaterThan(4); // a symbol, not a blank strip
        }
      }
      for (let x = 10; x < 54; x++) expect(lum(get(img, x, 33))).toBeLessThan(30);
      // topper: three red 7 glyphs inside the mapped face (uv 1.5..14.5 × 5..10 → texels 6..58 × 20..40)
      const top = H.slotTopperHd(tier, t);
      for (let i = 0; i < 3; i++) {
        let red = 0;
        for (let y = 25; y < 35; y++) for (let x = 15 + i * 13; x < 21 + i * 13; x++) if (isRed(get(top, x, y))) red++;
        expect(red).toBeGreaterThanOrEqual(14);
      }
    }
  });
});

describe('r2-4 cashier sign plate and cash wall', () => {
  it('is a solid dark-red plate with a gold double border (the word is the sign renderer\'s)', () => {
    const img = H.cashierPlateHd();
    expect(hex(img, 32, 32)).toBe('#4A0E14');
    expect(hex(img, 0, 32)).toBe('#E8B830');
    expect(hex(img, 3, 32)).toBe('#4A0E14');
    expect(hex(img, 4, 32)).toBe('#E8B830');
    expect(hex(img, 5, 32)).toBe('#4A0E14');
    const colours = new Set();
    for (let y = 6; y < 58; y++) for (let x = 6; x < 58; x++) colours.add(hex(img, x, y));
    expect([...colours]).toEqual(['#4A0E14']);
  });

  it('shows cash pigeonholes (banknotes, chips) and a safe behind the glass, no bookshelves', () => {
    for (const m of MACHINES.filter((k) => k.kind === 'cashier')) {
      for (const part of partsOf('cashier')) {
        const tex = Object.values(machinePartModel(m, part).textures ?? {});
        expect(tex.some((t) => /bookshelf/.test(t))).toBe(false);
      }
      const upper = machinePartModel(m, 'upper');
      expect(upper.textures.shelves).toBe('burmaldaholic:block/core/machines/cashier_wall');
      expect(upper.textures.safe).toBe('burmaldaholic:block/core/machines/cashier_safe');
    }
    const g = machineGeometry('cashier').boxes;
    expect(g.find((b) => b.mat === 'sign').emit).toBeGreaterThanOrEqual(12);
    expect(g.some((b) => b.mat === 'safe')).toBe(true);
    const wall = H.cashWallHd();
    let green = 0;
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const [r, gg, b] = get(wall, x, y);
      if (gg > r + 25 && gg > b + 10) green++;
    }
    expect(green).toBeGreaterThan(40); // banknote bundles
  });
});

describe('r2-5/6 Plinko cabinet, night lights', () => {
  const g = machineGeometry('plinko').boxes;
  it('has a lit bulb marquee with no centre emblem (PL draws the localized title there) and panelled sides', () => {
    const img = A.plinkoMarquee();
    for (let y = 2; y < 6; y++) for (let x = 0; x < 16; x++) expect(hex(img, x, y)).toBe('#2A0E3A');
    const marquee = g.find((b) => b.frontTex === 'marquee');
    expect(marquee.emit).toBe(15);
    expect(marquee.sideTex).toBe('ppanel');
    const side = H.cabinetSide(['#5A3A7A', '#3A1E50', '#2A1440']);
    expect(hex(side, 7, 8)).toBe('#F0C860'); // vertical brass strip
    expect(hex(side, 3, 4)).toBe('#2A1440'); // inset panel
  });

  it('lights the bin row and both bulb pillars fully, two colours alternating', () => {
    expect(g.find((b) => b.mat === 'pbase').emit).toBeGreaterThanOrEqual(11);
    const pillars = g.filter((b) => b.mat === 'bulbs');
    expect(pillars.length).toBe(2);
    for (const b of pillars) expect(b.emit).toBe(15);
    const bulbs = H.bulbs2();
    expect(hex(bulbs, 2, 2)).toBe('#FFF6C8');
    expect(hex(bulbs, 6, 2)).toBe('#FF4A4A');
    expect(hex(bulbs, 2, 6)).toBe('#FF4A4A');
  });

  it('lights the Wheel of Fortune rim bulbs and plinth bulbs fully', () => {
    const w = machineGeometry('wheel');
    const rim = w.segs.filter((s) => s.thick === 1.5 && s.axis === 'z');
    expect(rim.length).toBe(24);
    for (const s of rim) expect([s.mat, s.emit]).toEqual(['bulbs', 15]);
    expect(w.boxes.find((b) => b.mat === 'bulbs').emit).toBe(15);
  });

  it('lights the brass band under the rail on the high-roller theme only, and every placard plate', () => {
    for (const t of TABLES) {
      let band = 0;
      let lit = 0;
      let plate = 0;
      for (const part of ['main', 'left', 'right', 'back_left', 'back', 'back_right']) {
        for (const e of partModel(t, part).elements) {
          if (e.faces.north?.texture === '#placard' || e.faces.south?.texture === '#placard') plate += e.light_emission ? 1 : 0;
          if (e.from[1] !== 11.5 || e.to[1] !== 12.5) continue;
          band++;
          if (e.light_emission) lit++;
        }
      }
      expect(band).toBeGreaterThan(0);
      expect(lit).toBe(t.theme === 'high_roller' ? band : 0);
      expect(plate).toBe(1);
    }
  });
});

describe('r2-7 rail leather grain', () => {
  it('stays within ±6 % (+ the 1.5 % weave) of the base in 2-px features, apart from tufts and stitches', () => {
    for (const theme of Object.keys(THEMES)) {
      const img = railTexture(theme);
      const pix = [];
      for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) if (!((x === 4 || x === 12) && (y === 4 || y === 12))) pix.push(lum(get(img, x, y)));
      const mean = lum(c(THEMES[theme].rail[1])); // the base tone
      for (const l of pix) expect(Math.abs(l - mean) / mean).toBeLessThanOrEqual(0.06 + 0.015 + 0.01);
      // 2-px features: the two pixels of a grain cell differ only by the weave
      for (let y = 2; y < 14; y += 2) for (let x = 2; x < 14; x += 2) {
        if ((x === 4 || x === 12) && (y === 4 || y === 12)) continue;
        expect(Math.abs(lum(get(img, x, y)) - lum(get(img, x + 1, y + 1))) / mean).toBeLessThanOrEqual(0.005);
      }
    }
  });
});
