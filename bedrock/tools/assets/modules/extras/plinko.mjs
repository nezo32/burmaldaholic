// Plinko art (extras.md §5; extras-pvp §5–§6): the pegboard (sockets pre-drilled at the §5.3 layout), pegs with
// hit/afterglow states, the glossy ball (red + underdog gold, 4 roll frames), multiplier bin caps (5 tiers × unlit / lit),
// the chute gate, foil-capped hidden bins, the mini board of Plinko Battle and the soft edge glow.
import {
  K, blend, c, disc, hstrip, image, mixHex, outline, over, put, rect, rng, sheet, stud, vstrip,
} from './kit.mjs';

/** Board geometry (GUI px, board-local). Shared with the screen code and the mockup script. */
export const BOARD = {
  w: 272,
  h: 204,
  cx: 136,
  rows: 12,
  pitchX: 20,
  pitchY: 12,
  row0: 30, // y of row 0
  binY: 176, // top of the bin caps
  binW: 18,
  binH: 14,
  chuteY: 4,
};
export const pegXY = (r, j) => [BOARD.cx + (j - r / 2) * BOARD.pitchX, BOARD.row0 + r * BOARD.pitchY];
export const binX = (k) => BOARD.cx + (k - 6) * BOARD.pitchX;

/** Multiplier tiers → cap colours (extras-pvp §5.2): < 1 red, 1 blue, 1.4–3 green, 4–33 gold, ≥ 100 purple. */
export const BIN_TIERS = [
  { id: 'loss', face: '#C83A3A', light: '#FF7A6A', dark: '#7A1A24' },
  { id: 'even', face: '#3D6AB0', light: '#7AA8F0', dark: '#1E3A6A' },
  { id: 'win', face: '#2E9A3E', light: '#70E070', dark: '#145A20' },
  { id: 'big', face: '#E8B830', light: '#FFF08A', dark: '#8A5A08' },
  { id: 'top', face: '#9C27B0', light: '#E070FF', dark: '#4A0A5A' },
];

export function plinkoBoard() {
  const { w, h } = BOARD;
  const img = image(w, h);
  const r = rng(1212);
  // field: indigo arcade cabinet with a diamond lattice and a light pool at the top
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const t = y / h;
      let hex = mixHex('#1C1448', '#0E0A26', t);
      if (((x + y) % 10 === 0 || (x - y + 1000) % 10 === 0) && y > 20 && y < BOARD.binY - 2) hex = mixHex(hex, '#3A2A7A', 0.35);
      put(img, x, y, c(hex));
      const dx = (x - BOARD.cx) / 110;
      const dy = (y - 10) / 90;
      const pool = 1 - Math.hypot(dx, dy);
      if (pool > 0) blend(img, x, y, c('#5A4AB0'), pool * 0.22);
    }
  // faint starfield dust
  for (let i = 0; i < 90; i++) blend(img, 8 + ((r() * (w - 16)) | 0), 8 + ((r() * (BOARD.binY - 16)) | 0), c(r() < 0.3 ? '#FFFFFF' : '#9A8AE8'), 0.35 + 0.3 * r());
  // guide triangle (a thin lilac line along the outer pegs)
  for (let rr = 0; rr < BOARD.rows; rr++) {
    const [lx, ly] = pegXY(rr, 0);
    const [rx] = pegXY(rr, rr);
    for (let k = 0; k < BOARD.pitchY; k += 2) {
      blend(img, Math.round(lx - 12 - (k * 10) / BOARD.pitchY), ly + k, c('#6A4AC8'), 0.55);
      blend(img, Math.round(rx + 12 + (k * 10) / BOARD.pitchY), ly + k, c('#6A4AC8'), 0.55);
    }
  }
  // peg sockets (dark rings where the peg sprites sit)
  for (let rr = 0; rr < BOARD.rows; rr++)
    for (let j = 0; j <= rr; j++) {
      const [x, y] = pegXY(rr, j);
      disc(img, x, y, 3.6, c('#07051A'), 0.85);
      disc(img, x, y + 1, 3.2, c('#3A2A7A'), 0.35);
    }
  // bin bay: dark trough, brass dividers
  rect(img, 6, BOARD.binY - 4, w - 12, h - BOARD.binY - 2, '#0A0718');
  for (let k = 0; k <= 13; k++) {
    const x = BOARD.cx + (k - 6.5) * BOARD.pitchX - 1;
    rect(img, x, BOARD.binY - 8, 3, 26, K.ink);
    rect(img, x + 1, BOARD.binY - 7, 1, 24, K.gold);
    put(img, x + 1, BOARD.binY - 8, c(K.goldLight));
  }
  // neon side strips
  for (let y = 12; y < BOARD.binY - 10; y++) {
    blend(img, 7, y, c('#40E0FF'), 0.9);
    blend(img, 8, y, c('#40E0FF'), 0.3);
    blend(img, w - 8, y, c('#FF40C0'), 0.9);
    blend(img, w - 9, y, c('#FF40C0'), 0.3);
  }
  // frame: ink, walnut, gold line
  for (let i = 0; i < 6; i++) {
    const hex = i === 0 ? K.ink : i === 5 ? K.gold : i === 1 ? '#7A4A24' : i === 4 ? '#3A2010' : '#5A3418';
    rect(img, i, i, w - 2 * i, 1, hex);
    rect(img, i, h - 1 - i, w - 2 * i, 1, i === 5 ? K.goldShade : hex);
    rect(img, i, i, 1, h - 2 * i, hex);
    rect(img, w - 1 - i, i, 1, h - 2 * i, i === 5 ? K.goldShade : hex);
  }
  for (const [x, y] of [[2, 2], [w - 3, 2], [2, h - 3], [w - 3, h - 3]]) stud(img, x, y, K.gold, K.white, 1.2);
  // chute mount
  rect(img, BOARD.cx - 18, 0, 36, 6, K.ink);
  rect(img, BOARD.cx - 17, 1, 34, 4, '#5A3418');
  rect(img, BOARD.cx - 17, 4, 34, 1, K.gold);
  return img;
}

/** Peg (7 × 7): 0 idle silver pin, 1 hit (white-hot), 2 afterglow (gold). */
export function plinkoPegs() {
  const mk = (core, ring, hi) => {
    const img = image(7, 7);
    disc(img, 3.5, 3.5, 3.4, c(K.ink));
    disc(img, 3.5, 3.5, 2.5, c(ring));
    disc(img, 3.2, 3.2, 1.4, c(core));
    put(img, 2, 2, c(hi));
    return img;
  };
  return [mk('#E8E8F0', '#8A8AA0', '#FFFFFF'), mk('#FFFFFF', '#FFF4B0', '#FFFFFF'), mk('#FFE070', K.goldShade, K.goldLight)];
}

/** Ball (9 × 9 × 4 roll frames): glossy red chip-ball; row 2 = the gold underdog ball. */
export function plinkoBalls() {
  const rows = [];
  for (const [face, dark, lt, stripe] of [['#D83440', '#8C1834', '#FF9A90', '#F4ECF8'], ['#FFD640', '#B07010', '#FFF8C8', '#FFFFFF']]) {
    const frames = [];
    for (let f = 0; f < 4; f++) {
      const img = image(9, 9);
      for (let y = 0; y < 9; y++)
        for (let x = 0; x < 9; x++) {
          const dx = x + 0.5 - 4.5;
          const dy = y + 0.5 - 4.5;
          const d = Math.hypot(dx, dy);
          if (d > 4.5) continue;
          let hex = d > 3.7 ? K.ink : dx + dy > 2.5 ? dark : face;
          // rolling stripe band: rotates with the frame
          const a = Math.atan2(dy, dx) - (f * Math.PI) / 2;
          if (d < 3.7 && d > 1.2 && Math.abs(Math.sin(a)) < 0.3 && Math.cos(a) > 0) hex = stripe;
          put(img, x, y, c(hex));
        }
      put(img, 3, 2, c(lt));
      put(img, 2, 3, c(lt));
      put(img, 3, 3, c('#FFFFFF'));
      frames.push(img);
    }
    rows.push(frames);
  }
  return rows;
}

/** Bin caps (18 × 14): tier × {unlit, lit}. Lit = brighter face, white rim, bulb glow. The multiplier is text. */
export function plinkoBins() {
  const rows = [[], []];
  for (const t of BIN_TIERS)
    for (const lit of [false, true]) {
      const img = image(18, 14);
      const face = lit ? mixHex(t.face, '#FFFFFF', 0.25) : t.face;
      rect(img, 0, 1, 18, 13, K.ink);
      rect(img, 1, 0, 16, 1, K.ink);
      rect(img, 1, 1, 16, 12, face);
      rect(img, 1, 1, 16, 1, lit ? '#FFFFFF' : t.light);
      rect(img, 1, 2, 1, 10, lit ? t.light : mixHex(t.face, t.light, 0.4));
      rect(img, 1, 11, 16, 2, t.dark);
      rect(img, 16, 2, 1, 9, t.dark);
      // label well (the multiplier text sits here)
      rect(img, 3, 3, 12, 7, lit ? mixHex(t.dark, '#000000', 0.2) : mixHex(t.dark, '#000000', 0.35));
      if (lit) {
        put(img, 3, 3, c('#FFFFFF'));
        for (let x = 0; x < 18; x++) blend(img, x, 0, c(t.light), 0.6);
      }
      rows[lit ? 1 : 0].push(img);
    }
  return rows;
}

/** Chute (28 × 16 × 2): brass funnel, gate closed / open. */
export function plinkoChute() {
  return [false, true].map((open) => {
    const img = image(28, 16);
    for (let y = 0; y < 12; y++) {
      const half = 13 - y * 0.6;
      rect(img, Math.round(14 - half), y, Math.round(half * 2), 1, K.ink);
      rect(img, Math.round(14 - half) + 1, y, Math.round(half * 2) - 2, 1, y < 2 ? K.goldLight : y % 3 === 0 ? K.goldShade : K.gold);
    }
    // throat
    rect(img, 10, 3, 8, 9, '#0A0718');
    if (open) {
      rect(img, 17, 10, 2, 6, K.ink);
      rect(img, 18, 10, 1, 5, '#C8C8D8');
    } else {
      rect(img, 9, 11, 10, 3, K.ink);
      rect(img, 10, 12, 8, 1, '#C8C8D8');
    }
    return img;
  });
}

/** Hidden bin (18 × 14 × 6, V + mcmeta): foil cap with a slow shimmer (the Final Ball, extras-pvp §6.2). */
export function plinkoBinHidden() {
  const frames = [];
  for (let f = 0; f < 6; f++) {
    const img = image(18, 14);
    rect(img, 0, 1, 18, 13, K.ink);
    rect(img, 1, 0, 16, 1, K.ink);
    for (let y = 1; y < 13; y++)
      for (let x = 1; x < 17; x++) {
        let hex = (x + y) % 4 === 0 ? '#C8C8C8' : '#B4B4B4';
        if (y > 10) hex = '#8C8C8C';
        const band = Math.abs(x + y - (f * 6 - 4));
        if (band < 2) hex = '#F0F0F0';
        else if (band < 3) hex = '#D8D8D8';
        put(img, x, y, c(hex));
      }
    rect(img, 7, 4, 4, 5, '#8C8C8C');
    rect(img, 8, 5, 2, 3, K.goldShade);
    frames.push(img);
  }
  return frames;
}

/** Plinko Battle mini board (56 × 56): 12 rows at a 4 px pitch, 13 bin lamps; a 3 × 3 ball is drawn by code. */
export function plinkoMiniBoard() {
  const img = image(56, 56);
  rect(img, 0, 0, 56, 56, K.ink);
  for (let y = 1; y < 55; y++) rect(img, 1, y, 54, 1, mixHex('#1C1448', '#0E0A26', y / 55));
  for (let rr = 0; rr < 12; rr++)
    for (let j = 0; j <= rr; j++) put(img, Math.round(28 + (j - rr / 2) * 4 - 0.5), 4 + rr * 3 + 1, c('#9A9AB8'));
  for (let k = 0; k < 13; k++) {
    const x = Math.round(28 + (k - 6) * 4 - 1.5);
    rect(img, x, 45, 3, 3, BIN_TIERS[[3, 3, 3, 2, 1, 0, 0, 0, 1, 2, 3, 3, 3][k]].face);
  }
  rect(img, 1, 50, 54, 5, '#3A2010');
  rect(img, 1, 50, 54, 1, K.goldShade);
  return img;
}

/** Soft gold edge glow nine-slice (32 × 32, border 12): flash-safe EDGE eruption frame. */
export function edgeGlow() {
  const img = image(32, 32);
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const d = Math.min(x, y, 31 - x, 31 - y);
      if (d > 10) continue;
      blend(img, x, y, c(d < 2 ? K.goldLight : K.gold), (1 - d / 11) * 0.55);
    }
  return img;
}

export const plinkoPegSheet = () => hstrip(plinkoPegs());
export const plinkoBallSheet = () => sheet(plinkoBalls());
export const plinkoBinSheet = () => sheet(plinkoBins());
export const plinkoChuteSheet = () => hstrip(plinkoChute());
export const plinkoBinHiddenStrip = () => vstrip(plinkoBinHidden());
export { outline, over };
