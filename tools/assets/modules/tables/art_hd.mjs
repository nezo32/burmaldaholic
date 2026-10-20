// MT visual pass 3 (VQA round 2): high-detail art for faces that need more than 1 texel per model pixel — slot reels and
// topper, cashier sign plate and back wall, table-limit placards — drawn at 32 or 64 px per block (block textures may be
// larger than 16 px; model UVs stay in 0..16 units). Also the finer leather grain and the alternating bulb strip.
// No words are baked (digits/glyphs only). Pure + deterministic.
import { R, P, c, image, put, rng, disc, ring } from './draw.mjs';

const SYM = 8; // reel symbol size (texels) in the 64-px reel texture

/** 8×8 reel symbols (cherry, BAR, 7, bell) at (x, y). */
function symbol(img, kind, x, y) {
  if (kind === 'cherry') {
    for (const [cx, cy] of [[2, 5], [5, 6]]) for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      if (Math.abs(dx) + Math.abs(dy) < 2) P(img, x + cx + dx, y + cy + dy, '#D8202C');
    }
    P(img, x + 1, y + 4, '#FF8A90');
    P(img, x + 2, y + 2, '#2E8A2E');
    P(img, x + 3, y + 1, '#2E8A2E');
    P(img, x + 4, y + 2, '#2E8A2E');
    P(img, x + 5, y + 3, '#2E8A2E');
    P(img, x + 4, y + 0, '#3AB83A');
  } else if (kind === 'bar') {
    R(img, x, y + 2, SYM, 4, '#1A1418');
    R(img, x + 1, y + 3, SYM - 2, 2, '#F4EEE0');
    R(img, x + 2, y + 3, 1, 2, '#1A1418');
    R(img, x + 5, y + 3, 1, 2, '#1A1418');
  } else if (kind === 'seven') {
    R(img, x + 1, y + 1, 6, 2, '#D8202C');
    R(img, x + 4, y + 3, 2, 2, '#D8202C');
    R(img, x + 3, y + 5, 2, 2, '#D8202C');
    R(img, x + 1, y + 1, 6, 1, '#FF6A70');
  } else if (kind === 'bell') {
    R(img, x + 3, y + 1, 2, 1, '#B88A18');
    R(img, x + 2, y + 2, 4, 3, '#F0C040');
    R(img, x + 1, y + 5, 6, 1, '#F0C040');
    P(img, x + 3, y + 3, '#FFF4B0');
    R(img, x + 3, y + 6, 2, 1, '#8A6420');
  }
}

/**
 * Reel window (64 × 64; the model maps uv 2..14 × 4..13 = texels 8..56 × 16..52 onto the 12 × 9 px glass): three reels,
 * three symbols each, a dark pay line across the middle and a curvature gradient (darker top and bottom rows).
 */
export function slotReelsHd(tier, accent) {
  const img = image(64, 64);
  R(img, 0, 0, 64, 64, '#141018');
  const order = [['seven', 'bar', 'cherry'], ['bell', 'seven', 'bar'], ['cherry', 'bell', 'seven']];
  for (let i = 0; i < 3; i++) {
    const x0 = 9 + i * 16;
    for (let y = 16; y < 52; y++) {
      const d = Math.abs(y + 0.5 - 34) / 18; // 0 centre → 1 edge
      const shade = d < 0.45 ? '#F8F2E4' : d < 0.7 ? '#E2DACA' : d < 0.88 ? '#C2B8A6' : '#8E8676';
      R(img, x0, y, 14, 1, shade);
    }
    order[i].forEach((k, j) => symbol(img, k, x0 + 3, 18 + j * 11));
    R(img, x0 - 1, 16, 1, 36, '#2A2430');
  }
  R(img, 8, 33, 48, 2, '#2A0A10');
  R(img, 8, 33, 2, 2, accent);
  R(img, 54, 33, 2, 2, accent);
  return img;
}

/** 5-px-tall 7 glyph (3 wide) at (x, y). */
function seven5(img, x, y, col) {
  R(img, x, y, 3, 1, col);
  P(img, x + 2, y + 1, col);
  P(img, x + 1, y + 2, col);
  P(img, x + 1, y + 3, col);
  P(img, x + 1, y + 4, col);
}

/**
 * Topper sign (64 × 64; the model maps uv 1.5..14.5 × 5..10 onto the 13 × 5 px front): bulb border alternating warm
 * white / tier accent, tier-coloured field and a bold 7-7-7 (5-texel pixel font, scaled ×2) with a dark outline.
 */
export function slotTopperHd(tier, t) {
  const img = image(64, 64);
  const x0 = 6;
  const y0 = 20;
  const w = 52;
  const h = 20;
  R(img, 0, 0, 64, 64, t.body[3]);
  R(img, x0, y0, w, h, t.body[3]);
  for (let x = x0; x < x0 + w; x += 2) {
    R(img, x, y0, 1, 1, (x >> 1) % 2 ? '#FFF4C8' : t.accent);
    R(img, x + 1, y0 + h - 1, 1, 1, (x >> 1) % 2 ? t.accent : '#FFF4C8');
  }
  for (let y = y0; y < y0 + h; y += 2) {
    P(img, x0, y + 1, '#FFF4C8');
    P(img, x0 + w - 1, y, t.accent);
  }
  R(img, x0 + 2, y0 + 2, w - 4, h - 4, t.body[1]);
  R(img, x0 + 2, y0 + 2, w - 4, 2, t.body[0]);
  // 7-7-7, each glyph 3×5 scaled ×2 (6×10) with a dark outline
  for (let i = 0; i < 3; i++) {
    const gx = x0 + 9 + i * 13;
    const gy = y0 + 5;
    const glyph = image(3, 5);
    seven5(glyph, 0, 0, '#D8202C');
    for (let y = 0; y < 5; y++) for (let x = 0; x < 3; x++) {
      const o = (y * 3 + x) * 4;
      if (!glyph.data[o + 3]) continue;
      for (let dy = -1; dy <= 2; dy++) for (let dx = -1; dx <= 2; dx++) {
        const px = gx + x * 2 + dx;
        const py = gy + y * 2 + dy;
        P(img, px, py, '#1A0408');
      }
    }
    for (let y = 0; y < 5; y++) for (let x = 0; x < 3; x++) {
      const o = (y * 3 + x) * 4;
      if (glyph.data[o + 3]) R(img, gx + x * 2, gy + y * 2, 2, 2, y === 0 ? '#FF6A70' : '#E8242E');
    }
  }
  return img;
}

/** Cashier sign plate (64 × 64; the model maps the whole texture onto the plate): solid dark red, gold double border. */
export function cashierPlateHd() {
  const img = image(64, 64);
  R(img, 0, 0, 64, 64, '#E8B830');
  R(img, 2, 2, 60, 60, '#4A0E14');
  R(img, 4, 4, 56, 56, '#E8B830');
  R(img, 5, 5, 54, 54, '#4A0E14');
  R(img, 2, 2, 60, 1, '#FFE07A');
  R(img, 4, 4, 56, 1, '#FFE07A');
  return img;
}

/**
 * Back wall behind the cashier glass (64 × 64 per block): cash pigeonholes with green / cream banknote bundles and
 * chip columns; dark wood dividers. Tiles per block along the booth.
 */
export function cashWallHd() {
  const img = image(64, 64);
  R(img, 0, 0, 64, 64, '#2E1C12');
  const r = rng(91);
  const chips = ['#D23A3A', '#2E7AD8', '#2E9A48', '#1A1A1A', '#8A3AC8', '#E8B030'];
  for (let row = 0; row < 4; row++) {
    for (let col = 0; col < 4; col++) {
      const x = col * 16 + 1;
      const y = row * 16 + 1;
      R(img, x, y, 14, 14, '#160C08');
      R(img, x, y + 13, 14, 1, '#4A3020');
      const kind = (row * 5 + col * 3) % 3;
      if (kind === 0) {
        // banknote bundles: green with a cream band
        for (let k = 0; k < 3; k++) {
          R(img, x + 1, y + 9 - k * 3, 12, 3, k % 2 ? '#6AA86A' : '#4E8A4E');
          R(img, x + 5, y + 9 - k * 3, 3, 3, '#ECE6DA');
        }
      } else if (kind === 1) {
        // chip columns
        for (let k = 0; k < 4; k++) {
          const h = 6 + Math.floor(r() * 6);
          R(img, x + 1 + k * 3, y + 13 - h, 2, h, chips[(row + col + k) % chips.length]);
          for (let s = y + 13 - h; s < y + 13; s += 2) P(img, x + 1 + k * 3, s, '#F4EEE0');
        }
      } else {
        // coin bags
        disc(img, x + 7, y + 9, 4, c('#B8945A'));
        R(img, x + 6, y + 4, 3, 2, '#8A6A3A');
        P(img, x + 6, y + 8, '#FFD640');
      }
    }
  }
  return img;
}

/** Small safe front (32 × 32): steel door, brass dial and handle. */
export function safeHd() {
  const img = image(32, 32);
  R(img, 0, 0, 32, 32, '#3A3E46');
  R(img, 2, 2, 28, 28, '#5A606A');
  R(img, 3, 3, 26, 1, '#8A909A');
  disc(img, 12, 16, 5, c('#B88A18'));
  disc(img, 12, 16, 3, c('#F0C040'));
  P(img, 12, 12, '#2A2A2A');
  R(img, 22, 13, 3, 7, '#C8C8D0');
  return img;
}

/**
 * Table-limit placard (32 × 32; the model maps uv 0..16 × 2.67..13.33 onto the 6 × 4 px plate): white enamel with a gold
 * frame and a clean game icon with a 1-texel black outline — spade (card tables), red ball (roulette), die (craps).
 */
export function placardHd(icon) {
  const img = image(32, 32);
  R(img, 0, 0, 32, 32, '#F4EEE0');
  R(img, 0, 5, 32, 1, '#E8B830');
  R(img, 0, 26, 32, 1, '#B88A18');
  R(img, 0, 5, 1, 22, '#E8B830');
  R(img, 31, 5, 1, 22, '#B88A18');
  R(img, 1, 6, 30, 20, '#FAF6EE');
  const cx = 16;
  const cy = 16;
  if (icon === 'spade') {
    const pts = [];
    for (let y = -6; y <= 4; y++) for (let x = -6; x <= 6; x++) {
      const inTop = y <= 1 && Math.abs(x) <= (y + 6) * 0.8;
      const inLobes = y >= -1 && y <= 2 && (Math.hypot(x + 3, y - 0.5) <= 3 || Math.hypot(x - 3, y - 0.5) <= 3);
      if (inTop || inLobes) pts.push([x, y]);
    }
    for (let y = 3; y <= 6; y++) for (let x = -Math.floor((y - 2) / 1.5); x <= Math.floor((y - 2) / 1.5); x++) pts.push([x, y]);
    const set = new Set(pts.map(([x, y]) => `${x},${y}`));
    for (const [x, y] of pts) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      if (!set.has(`${x + dx},${y + dy}`)) P(img, cx + x + dx, cy + y + dy, '#000000');
    }
    for (const [x, y] of pts) P(img, cx + x, cy + y, '#16121A');
  } else if (icon === 'ball') {
    disc(img, cx, cy, 7, c('#000000'));
    disc(img, cx, cy, 6, c('#D8202C'));
    disc(img, cx - 2, cy - 2, 2, c('#FF8A90'));
  } else {
    R(img, cx - 7, cy - 7, 14, 14, '#000000');
    R(img, cx - 6, cy - 6, 12, 12, '#D8202C');
    for (const [x, y] of [[-3, -3], [3, -3], [-3, 3], [3, 3]]) R(img, cx + x - 1, cy + y - 1, 2, 2, '#FFFFFF');
  }
  return img;
}

/**
 * Finer padded leather (16 px): 2–3-texel grain within ±6 % lightness of the base, 1-texel tufts every 8 texels,
 * stitch dots every 4 texels on all edges.
 */
export function leatherFine(base, stitch, seed) {
  const img = image(16, 16);
  const [r0, g0, b0] = c(base);
  const r = rng(seed);
  for (let y = 0; y < 16; y += 2) for (let x = 0; x < 16; x += 2) {
    const k = 1 + (r() - 0.5) * 0.12;
    for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
      const kk = k + ((dx + dy) % 2 ? 0.015 : -0.015);
      put(img, x + dx, y + dy, [Math.min(255, r0 * kk), Math.min(255, g0 * kk), Math.min(255, b0 * kk), 255].map(Math.round));
    }
  }
  for (const [x, y] of [[4, 4], [12, 12], [4, 12], [12, 4]]) {
    put(img, x, y, [Math.round(r0 * 0.82), Math.round(g0 * 0.82), Math.round(b0 * 0.82), 255]);
  }
  for (let i = 1; i < 16; i += 4) {
    P(img, i, 0, stitch);
    P(img, i, 15, stitch);
    P(img, 0, i, stitch);
    P(img, 15, i, stitch);
  }
  return img;
}

/** Bulb strip in two alternating colours (warm white / red) on a brass rail — fully emissive in the models. */
export function bulbs2() {
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, '#6A4810');
  for (let y = 0; y < 16; y += 4) for (let x = 0; x < 16; x += 4) {
    const warm = ((x + y) >> 2) % 2 === 0;
    R(img, x + 1, y + 1, 2, 2, warm ? '#FFF6C8' : '#FF4A4A');
    P(img, x + 1, y + 1, '#FFFFFF');
  }
  return img;
}

/** Panelled cabinet side (Plinko): vertical brass strip + two inset panels. */
export function cabinetSide(wood) {
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, wood[1]);
  R(img, 7, 0, 2, 16, '#D0A040');
  R(img, 7, 0, 1, 16, '#F0C860');
  for (const y of [1, 9]) {
    R(img, 1, y, 5, 6, wood[2]);
    R(img, 10, y, 5, 6, wood[2]);
    R(img, 1, y, 5, 1, wood[0]);
    R(img, 10, y, 5, 1, wood[0]);
  }
  return img;
}

export { ring };
