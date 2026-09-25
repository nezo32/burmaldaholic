// World & meta FX art (lane J-L3; docs/design/animation/global.md §4.5–§4.13, §5.1 #19–#20, #30–#33, #36; look of
// docs/design/visual/extras.md §1): the Last Chance coin, VIP tier badges + shine, chaos event cards, the Golden Hour
// countdown plaque and sun, collector card, white vignettes (tinted in code), the bot difficulty pills (font) and the
// animated attract strips of the cashier / wheel / Plinko fronts. House style: 16-px art at 2–3×, 1 px ink outline,
// ink drop shadow, light from the top left, shapes (never colour alone) tell things apart. No text in any texture.
import { CREEPER, DIAMOND, SKULL } from '../extras/icons.mjs';
import {
  K, art16, blend, c, chipTop, clamp, disc, emboss, image, mixHex, outline, over, put, rect, roundCorners, scaleUp, shadow,
} from '../extras/kit.mjs';
import { affine } from '../slots/raster.mjs';

// ---------------------------------------------------------------------------------------------------------------------
// shared

/** White radial vignette (transparent centre → opaque edges, soft), tinted by the client (gold / red / curse / lilac). */
export function vignette(size = 128) {
  const img = image(size, size);
  const h = size / 2;
  for (let y = 0; y < size; y++)
    for (let x = 0; x < size; x++) {
      const d = Math.hypot((x + 0.5 - h) / h, (y + 0.5 - h) / h);
      const k = clamp((d - 0.5) / 0.62, 0, 1);
      const a = Math.round(255 * k * k * (3 - 2 * k));
      if (a) put(img, x, y, [255, 255, 255, a]);
    }
  return img;
}

/** Grid mask helper (1 colour). */
const mask = (id, rows) => ({ id, pal: { '#': '#000000' }, grid: rows });

// ---------------------------------------------------------------------------------------------------------------------
// Last Chance coin (global §4.8, §5.1 #31–#32): 12 frames 32 × 32 — 0 heads (a heart: you live), 1–5 tilting, 6 edge,
// 7–11 tilting back to 11 tails (Death's skull). A 3 px reeded cylinder turning about the vertical axis.

export const EMBLEM_LIFE = mask('lastchance_life', [
  '................',
  '................',
  '...####..####...',
  '..######.#####..',
  '.##############.',
  '.##############.',
  '.##############.',
  '..############..',
  '...##########...',
  '....########....',
  '.....######.....',
  '......####......',
  '.......##.......',
  '................',
  '................',
  '................',
]);

export const EMBLEM_DEATH = mask('lastchance_death', [
  '................',
  '.....######.....',
  '...##########...',
  '..############..',
  '..############..',
  '..##...##...##..',
  '..##...##...##..',
  '..############..',
  '...#####.####...',
  '....########....',
  '....#.#..#.#....',
  '....########....',
  '.....######.....',
  '................',
  '................',
  '................',
]);

const R = 14.5;
const T = 3;
/** Frame angles (deg): 0 heads … 6 edge-on (90°) … 11 tails (180°). */
export const LC_ANGLES = [0, 18, 36, 54, 72, 84, 90, 108, 126, 144, 162, 180];

/** Flat 32² face: gold (life, heads) or tarnished old gold (death, tails), bevelled rim, bead ring, embossed emblem. */
export function lcFace(heads) {
  const img = image(32, 32);
  const field = heads ? ['#FFD640', '#F6C832'] : ['#D8A838', '#C89420'];
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16;
      const dy = y + 0.5 - 16;
      const d = Math.hypot(dx, dy);
      if (d > R) continue;
      const lit = (-dx - dy) / (d || 1);
      let hex;
      if (d > R - 1.1) hex = lit > 0.3 ? '#FFE87A' : lit < -0.3 ? K.goldShade : '#E0A828';
      else if (d > R - 2.2) hex = lit > 0.3 ? '#C88A18' : lit < -0.3 ? '#FFF0A0' : '#E8B830';
      else if (d > R - 3) {
        const a = Math.atan2(dy, dx);
        hex = Math.floor(((a + Math.PI) / (2 * Math.PI)) * 28) % 2 === 0 ? '#FFF4B0' : '#D8A020';
      } else hex = d > 9.5 ? field[1] : field[0];
      put(img, x, y, c(hex));
    }
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16;
      const dy = y + 0.5 - 16;
      const d = Math.hypot(dx, dy);
      if (d < R - 3 && d > 7.5 && dx + dy < -9) blend(img, x, y, c('#FFF4B0'), heads ? 0.55 : 0.3);
    }
  emboss(img, art16(heads ? EMBLEM_LIFE : EMBLEM_DEATH), 8, 8, heads
    ? { face: '#E8A020', lightHex: '#FFF8C8', darkHex: '#8C1834' }
    : { face: '#A87818', lightHex: '#F4E0A0', darkHex: '#3A2010' });
  return img;
}

function spinArt(a) {
  const heads = lcFace(true);
  const tails = lcFace(false);
  const rad = (a * Math.PI) / 180;
  const ca = Math.cos(rad);
  const sa = Math.sin(rad);
  const face = ca >= 0 ? heads : tails;
  const xc = (ca >= 0 ? 1 : -1) * (T / 2) * sa;
  const w = Math.abs(ca);
  const shade = 0.72 + 0.28 * w;
  const img = image(32, 32);
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const X = x + 0.5 - 16;
      const Y = y + 0.5 - 16;
      if (Math.abs(Y) > R) continue;
      const hw = R * Math.sqrt(Math.max(0, 1 - (Y / R) ** 2));
      if (w > 0.02 && Math.abs(X - xc) <= hw * w) {
        const u = clamp(Math.floor(16 + (X - xc) / w), 0, 31);
        const o = (y * 32 + u) * 4;
        if (!face.data[o + 3]) continue;
        put(img, x, y, [Math.round(face.data[o] * shade), Math.round(face.data[o + 1] * shade), Math.round(face.data[o + 2] * shade), 255]);
        continue;
      }
      if (Math.abs(sa) > 0.05 && Math.abs(X) <= (T / 2) * Math.abs(sa) + hw * w) {
        const groove = y % 2 === 0;
        let hex = groove ? '#D09A20' : '#8A5A08';
        if (Y < -R * 0.55) hex = groove ? '#FFE070' : '#B07010';
        if (Y > R * 0.6) hex = groove ? '#A06A10' : '#6A3E06';
        put(img, x, y, c(hex));
      }
    }
  return img;
}

/** Frame k (32 × 32): the art inset by 1 px so the ink outline fits the cell. */
export function lcFrame(k) {
  const art = affine(spinArt(LC_ANGLES[k]), 32, 32, { scale: 30 / 32, px: 16, py: 16, ox: 16, oy: 16 });
  return outline(art, c(K.ink));
}

/** Tails crack overlays (32²): 0 a hairline from the rim, 1 the full split with a chipped piece. */
export function lcCrack(stage) {
  const img = lcFrame(11);
  const path = [[9, 5], [11, 8], [10, 11], [13, 14], [12, 17], [15, 20], [14, 23], [17, 26]];
  const upto = stage === 0 ? 4 : path.length;
  for (let i = 0; i < upto; i++) {
    const [x, y] = path[i];
    put(img, x, y, c('#2A1206'));
    put(img, x + 1, y, c('#FFF4B0'));
    if (i + 1 < upto) {
      const [nx, ny] = path[i + 1];
      for (let t = 1; t < 3; t++) put(img, Math.round(x + ((nx - x) * t) / 3), Math.round(y + ((ny - y) * t) / 3), c('#2A1206'));
    }
  }
  if (stage === 1) {
    // a chipped wedge on the rim and a second branch
    for (const [x, y] of [[21, 4], [22, 4], [22, 5], [23, 5], [23, 6]]) put(img, x, y, [0, 0, 0, 0]);
    for (const [x, y] of [[21, 6], [20, 8], [19, 10], [16, 13]]) put(img, x, y, c('#2A1206'));
  }
  return img;
}

// ---------------------------------------------------------------------------------------------------------------------
// VIP tier badges (global §4.11, §5.1 #19–#20): 48 × 48, one per tier. A scalloped medallion in the tier metal, a cut
// gem in the centre and tier PIPS along the bottom arc (1 bronze … 6 netherite) — the count tells the tier, not colour.

export const VIP_TIERS = ['bronze', 'silver', 'gold', 'platinum', 'diamond', 'netherite'];
const METAL = {
  bronze: ['#F0A868', '#C8763C', '#8A4A20', '#5A2E10'],
  silver: ['#FFFFFF', '#C8C8D8', '#8A8AA0', '#50506A'],
  gold: ['#FFF4B0', '#FFD640', '#E8B830', '#B07010'],
  platinum: ['#FFFFFF', '#E8F4FF', '#A8C0D8', '#6A809A'],
  diamond: ['#E8FFFF', '#5CE8E0', '#2EA8C8', '#1A6A80'],
  netherite: ['#8A7A88', '#5A4A58', '#3A2E3A', '#1E161E'],
};
const GEM = {
  bronze: ['#FF9A7A', '#D83440', '#8C1834'],
  silver: ['#A8D8FF', '#3D8AD8', '#1A4A8C'],
  gold: ['#B8FF90', '#2E9A3E', '#145A1E'],
  platinum: ['#F0C8FF', '#9C27B0', '#5A1070'],
  diamond: ['#FFFFFF', '#A8FFF8', '#40C8C0'],
  netherite: ['#FFD080', '#FF7A3C', '#C81A08'],
};

export function vipBadge(tier) {
  const id = VIP_TIERS[tier];
  const [hi, base, mid, dark] = METAL[id];
  const img = image(48, 48);
  const cx = 24;
  const cy = 23;
  // scalloped rim (16 lobes)
  for (let y = 0; y < 48; y++)
    for (let x = 0; x < 48; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      const d = Math.hypot(dx, dy);
      const a = Math.atan2(dy, dx);
      const rr = 20 + 1.3 * Math.cos(a * 16);
      if (d > rr) continue;
      const lit = (-dx - dy) / (d || 1);
      let hex;
      if (d > rr - 1.5) hex = lit > 0.25 ? hi : lit < -0.35 ? dark : base;
      else if (d > 16.5) hex = lit > 0.4 ? base : lit < -0.4 ? mid : base;
      else if (d > 15.3) hex = lit > 0 ? dark : hi; // inner groove (inverse bevel)
      else hex = d < 9 ? base : mid;
      put(img, x, y, c(hex));
    }
  // field sheen
  for (let y = 0; y < 48; y++)
    for (let x = 0; x < 48; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      const d = Math.hypot(dx, dy);
      if (d < 15 && d > 10 && dx + dy < -10) blend(img, x, y, c(hi), 0.45);
    }
  // cut gem (octagon, table + facets)
  const [gl, gm, gd] = GEM[id];
  for (let y = -7; y <= 7; y++)
    for (let x = -7; x <= 7; x++) {
      if (Math.abs(x) + Math.abs(y) > 10) continue;
      const px = cx - 0.5 + x;
      const py = cy - 3.5 + y;
      let hex = gm;
      if (Math.abs(x) <= 3 && Math.abs(y) <= 3) hex = x + y < 0 ? gl : gm; // table
      else if (x + y < -3) hex = gl;
      else if (x + y > 3) hex = gd;
      put(img, Math.round(px), Math.round(py), c(hex));
    }
  // gem outline + sparkle
  const gemMask = image(48, 48);
  for (let y = 0; y < 48; y++) for (let x = 0; x < 48; x++) if (Math.abs(x + 0.5 - cx) + Math.abs(y + 0.5 - (cy - 3)) <= 10.6) put(gemMask, x, y, [0, 0, 0, 255]);
  for (let y = 0; y < 48; y++)
    for (let x = 0; x < 48; x++) {
      const inside = gemMask.data[(y * 48 + x) * 4 + 3];
      if (inside) continue;
      const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([i, j]) => x + i >= 0 && y + j >= 0 && x + i < 48 && y + j < 48 && gemMask.data[((y + j) * 48 + x + i) * 4 + 3]);
      if (n) put(img, x, y, c(K.ink));
    }
  put(img, cx - 3, cy - 7, c(K.white));
  put(img, cx - 4, cy - 6, c(K.white));
  // tier pips (tier + 1) on the bottom arc
  const n = tier + 1;
  for (let i = 0; i < n; i++) {
    const a = Math.PI / 2 + (i - (n - 1) / 2) * 0.33;
    const px = Math.round(cx - 0.5 + Math.cos(a) * 12.5);
    const py = Math.round(cy - 0.5 + Math.sin(a) * 12.5);
    for (const [dx, dy] of [[0, -1], [-1, 0], [0, 0], [1, 0], [0, 1]]) put(img, px + dx, py + dy, c(K.ink));
    put(img, px, py, c(hi));
  }
  // netherite embers in the rim
  if (id === 'netherite')
    for (const [x, y] of [[7, 18], [9, 31], [38, 14], [40, 28], [30, 41], [16, 5]]) {
      put(img, x, y, c('#FF7A3C'));
      put(img, x + 1, y, c('#FFD080'));
    }
  return shadow(outline(img, c(K.ink)), c(K.ink), 1, 2, 0.4);
}

/** Shine sweep frame f of 8 (48²): a white diagonal band clipped to the badge silhouette (alpha art, drawn on top). */
export function vipShine(f) {
  const silhouette = vipBadge(2);
  const out = image(48, 48);
  const pos = -6 + f * 11;
  for (let y = 0; y < 48; y++)
    for (let x = 0; x < 48; x++) {
      const o = (y * 48 + x) * 4;
      if (silhouette.data[o + 3] < 200) continue;
      const d = Math.abs(x + y * 0.6 - pos - 10);
      if (d < 2) put(out, x, y, [255, 255, 255, 200]);
      else if (d < 4) put(out, x, y, [255, 250, 230, 90]);
    }
  return out;
}

// ---------------------------------------------------------------------------------------------------------------------
// Chaos cards (global §4.6 NICE, §5.1 #33; J20): 64 × 64 — a framed card in the event's tone (good gold / bad red /
// neutral lilac) with the event icon drawn at 3× (16-px art) in the house outline + shadow.

export const CHAOS_EVENTS = [
  'chip_shower', 'lucky_buff', 'diamond_rain', 'xp_fountain', 'curse', 'mob_wave', 'random_teleport', 'weather_change', 'golden_hour',
];
const TONE = {
  good: { edge: '#FFD640', edge2: '#B07010', glow: '#80FF40', face: ['#2A1A48', '#1A0E30'] },
  bad: { edge: '#D83440', edge2: '#8C1834', glow: '#6FA86A', face: ['#2A0E24', '#160616'] },
  neutral: { edge: '#D696FF', edge2: '#783CBE', glow: '#D696FF', face: ['#22123C', '#140822'] },
};
export const EVENT_TONE = {
  chip_shower: 'good', lucky_buff: 'good', diamond_rain: 'good', xp_fountain: 'good', golden_hour: 'good',
  curse: 'bad', mob_wave: 'bad', random_teleport: 'neutral', weather_change: 'neutral',
};

const CLOVER = {
  id: 'chaos_clover', pal: { k: '#0A3A14', g: '#2E9A3E', G: '#1E7A2E', l: '#80FF40', s: '#6A4A20' }, grid: [
    '................',
    '....kkk..kkk....',
    '...kglgk.kglk...',
    '...kgggkkgggk...',
    '....kgGgggGgk...',
    '.kkk.kggGggk.kk.',
    'kglgkkgGGGgkkglk',
    'kgggGgGGlGGgGggk',
    '.kgggGGGGGGGggk.',
    '..kkgGgGGGgGkk..',
    '...kglgkkkglgk..',
    '...kgggk.kgggk..',
    '....kkk.s.kkk...',
    '........s.......',
    '.......s........',
    '................',
  ],
};
const ORB = {
  id: 'chaos_orb', pal: { k: '#0A3A08', g: '#80FF40', G: '#3FA535', d: '#1E6A18', w: '#FFFFA0', y: '#D8FF60' }, grid: [
    '................',
    '................',
    '.....kkkkkk.....',
    '....kyyggggk....',
    '...kywggGGGGk...',
    '..kywgggGGGGdk..',
    '..kygggGGGGGdk..',
    '..kggGGGGGGGdk..',
    '..kgGGGGGGGddk..',
    '..kgGGGGGGdddk..',
    '...kGGGGGdddk...',
    '....kdddddddk...',
    '.....kkkkkkk....',
    '................',
    '................',
    '................',
  ],
};
const PEARL = {
  id: 'chaos_pearl', pal: { k: '#061A18', t: '#1A6A60', T: '#2EA898', l: '#7AF0D8', w: '#E8FFF8', d: '#0C3A36', p: '#D696FF' }, grid: [
    '................',
    '.....kkkkkk.....',
    '...kkttTTttkk...',
    '..kttTllTTttdk..',
    '..ktTlwlTTttdk..',
    '.kttTllTTttttdk.',
    '.ktTTTTkkTtttdk.',
    '.kttTTkppkttddk.',
    '.kttTTkppkttddk.',
    '.ktttTTkkttdddk.',
    '.kdtttttttdddk..',
    '..kdddtttddddk..',
    '..kkdddddddkk...',
    '....kkkkkkk.....',
    '................',
    '................',
  ],
};
const STORM = {
  id: 'chaos_storm', pal: { k: '#180A28', w: '#F4ECF8', s: '#C0B0DC', d: '#8A7AA8', y: '#FFD640', Y: '#FFF4B0' }, grid: [
    '................',
    '.....kkkk.......',
    '....kwwwwk.kk...',
    '..kkwwswwwkwwk..',
    '.kwwwssswwwwwwk.',
    'kwwsssssswwsswwk',
    'kdssssssssssssdk',
    '.kddddddYdddddk.',
    '..kkkkkYYkkkkk..',
    '......kYYk......',
    '.....kYYk.......',
    '....kYYYYYk.....',
    '......kYYk......',
    '......kYk.......',
    '.....kYk........',
    '.....kk.........',
  ],
};
const SUN = {
  id: 'chaos_sun', pal: { k: '#5C3A00', y: '#FFD640', Y: '#FFF4B0', o: '#E8B830', d: '#B07010', h: '#3A2010' }, grid: [
    '.......kk.......',
    '..k....yy....k..',
    '...k...yy...k...',
    '....kkkkkkkk....',
    '...kYYYyyyyok...',
    '...kYYyyhyyok...',
    'kyykYyyyhyyokyyk',
    'kyykyyyyhyyokyyk',
    '...kyyyyhhhok...',
    '...kyyyyyyyok...',
    '...kooyyyyood...',
    '....kkkkkkkk....',
    '...k...yy...k...',
    '..k....yy....k..',
    '.......kk.......',
    '................',
  ],
};

function icon16(key) {
  switch (key) {
    case 'lucky_buff': return art16(CLOVER);
    case 'xp_fountain': return art16(ORB);
    case 'random_teleport': return art16(PEARL);
    case 'weather_change': return art16(STORM);
    case 'golden_hour': return art16(SUN);
    case 'curse': return art16(SKULL);
    case 'mob_wave': return art16(CREEPER);
    case 'diamond_rain': return art16(DIAMOND);
    default: return null;
  }
}

/** The event icon at 48 × 48 (3× art, ink outline, shadow), with a few event-specific extras. */
export function chaosIcon(key) {
  const img = image(48, 48);
  if (key === 'chip_shower') {
    // three chips tumbling down + motion ticks
    chipTop(img, 14, 14, 7.5, '#C62828', '#F5F5F5');
    chipTop(img, 32, 22, 7.5, '#2E7D32', '#F5F5F5');
    chipTop(img, 20, 34, 7.5, '#262626', '#F5F5F5');
    for (const [x, y] of [[14, 3], [32, 11], [20, 23]]) {
      put(img, x, y, c(K.gold));
      put(img, x, y - 2, c(K.goldShade));
    }
  } else {
    const src = icon16(key);
    over(img, scaleUp(src, 3), 0, 0);
    if (key === 'diamond_rain')
      for (const [x, y] of [[6, 40], [40, 36], [42, 8]]) {
        put(img, x, y, c('#5CE8E0'));
        put(img, x, y + 1, c('#E8FFFF'));
        put(img, x, y + 2, c('#2EA8C8'));
      }
    if (key === 'curse')
      for (let i = 0; i < 10; i++) {
        const a = i * 0.7;
        put(img, Math.round(24 + Math.cos(a) * (18 - i)), Math.round(40 - i * 3.2), c(i % 2 ? '#6FA86A' : '#A8E0A0'));
      }
  }
  return shadow(outline(img, c(K.ink)), c(K.ink), 2, 2, 0.4);
}

/** 64² chaos card: tone frame, dark face with a soft glow behind the icon. */
export function chaosCard(key) {
  const tone = TONE[EVENT_TONE[key]];
  const img = image(64, 64);
  rect(img, 0, 0, 64, 64, K.ink);
  rect(img, 1, 1, 62, 62, tone.edge2);
  rect(img, 2, 2, 60, 60, tone.edge);
  rect(img, 2, 2, 60, 1, mixHex(tone.edge, '#FFFFFF', 0.5));
  rect(img, 2, 2, 1, 60, mixHex(tone.edge, '#FFFFFF', 0.35));
  rect(img, 4, 4, 56, 56, K.ink);
  for (let y = 5; y < 59; y++)
    for (let x = 5; x < 59; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 38;
      put(img, x, y, c(mixHex(tone.face[0], tone.face[1], clamp(d, 0, 1))));
    }
  // glow disc behind the icon
  for (let y = 5; y < 59; y++)
    for (let x = 5; x < 59; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32);
      if (d < 22) blend(img, x, y, c(tone.glow), 0.18 * (1 - d / 22));
    }
  // corner studs
  for (const [x, y] of [[6, 6], [57, 6], [6, 57], [57, 57]]) {
    put(img, x, y, c(tone.edge));
  }
  over(img, chaosIcon(key), 8, 8);
  roundCorners(img, 0, 0, 64, 64, 2);
  return img;
}

/** Golden Hour countdown plaque (nine-slice 32 × 16, border 6): gold trim on deep purple. */
export function ghPlaque() {
  const img = image(32, 16);
  rect(img, 0, 0, 32, 16, K.ink);
  rect(img, 1, 1, 30, 14, K.goldShade);
  rect(img, 1, 1, 30, 1, K.goldLight);
  rect(img, 2, 2, 28, 12, K.gold);
  rect(img, 3, 3, 26, 10, K.ink);
  rect(img, 4, 4, 24, 8, K.deep);
  rect(img, 4, 4, 24, 1, '#3A1A5C');
  roundCorners(img, 0, 0, 32, 16, 1);
  return img;
}

/** Golden Hour sun (16 × 16 × 4 frames, rays turning; `.mcmeta` animation). */
export function ghSun(f) {
  const img = image(16, 16);
  for (let i = 0; i < 8; i++) {
    const a = (i * Math.PI) / 4 + (f * Math.PI) / 16;
    const long = i % 2 === 0;
    for (let r = 5.5; r < (long ? 7.8 : 6.9); r += 0.5) put(img, Math.round(7.5 + Math.cos(a) * r), Math.round(7.5 + Math.sin(a) * r), c(long ? K.gold : K.goldMid));
  }
  disc(img, 8, 8, 4.6, c(K.goldShade));
  disc(img, 8, 8, 3.9, c(K.gold));
  disc(img, 7.2, 7.2, 1.6, c(K.goldLight));
  return outline(img, c(K.ink));
}

// ---------------------------------------------------------------------------------------------------------------------
// Debt Collectors' arrival card (global §4.9): a 48 × 48 red-steel card with a clenched fist (shape: fist + knuckles).

const FIST = {
  id: 'loan_fist', pal: { k: '#180A28', s: '#E8B89A', S: '#F8D8C0', d: '#B07A5A', D: '#7A4A30', c: '#3A3A42', C: '#6A6A76' }, grid: [
    '................',
    '................',
    '...kkkkkkkkk....',
    '..kSSsSSsSSsk...',
    '..kSsdSsdSsdkk..',
    '..ksdDsdDsdDsSk.',
    '..kkkkkkkkkksSk.',
    '..ksssssssssssk.',
    '..kSsssssssssdk.',
    '..kssssssssssdk.',
    '...ksssssssddk..',
    '....kkdddddkk...',
    '.....kCCCCCk....',
    '.....kccccck....',
    '.....kkkkkkk....',
    '................',
  ],
};

export function collectorCard() {
  const img = image(48, 48);
  rect(img, 0, 0, 48, 48, K.ink);
  rect(img, 1, 1, 46, 46, '#5A0E24');
  rect(img, 2, 2, 44, 44, K.red);
  rect(img, 2, 2, 44, 1, K.redLight);
  rect(img, 4, 4, 40, 40, K.ink);
  for (let y = 5; y < 43; y++) for (let x = 5; x < 43; x++) put(img, x, y, c(mixHex('#22363A', '#0E1A1E', clamp((y - 5) / 38, 0, 1))));
  // hazard stripes along the bottom
  for (let x = 5; x < 43; x++) for (let y = 38; y < 43; y++) if (((x + y) >> 2) % 2 === 0) put(img, x, y, c(K.redDark));
  for (const [x, y] of [[6, 6], [41, 6], [6, 36], [41, 36]]) {
    put(img, x, y, c('#8A9AA8'));
    put(img, x + 1, y + 1, c('#3A4A58'));
  }
  const fist = shadow(outline(scaleUp(art16(FIST), 2), c(K.ink)), c(K.ink), 2, 2, 0.45);
  over(img, fist, 8, 5);
  roundCorners(img, 0, 0, 48, 48, 1);
  return img;
}

// ---------------------------------------------------------------------------------------------------------------------
// Bot difficulty pills (font `burmaldaholic:bots`, U+E500–E503; global §4.12, BOTS.md §8.1): 16² cells rendered at
// height 8. Easy one pip on green, Normal two on gold, Hard three on red, Mixed a tri-colour split — pips = shape.

const PILL = [
  { base: '#2E9A3E', light: '#80FF40', dark: '#145A1E', pips: 1 },
  { base: '#E8B830', light: '#FFF4B0', dark: '#8A5A08', pips: 2 },
  { base: '#D83440', light: '#FF6E6A', dark: '#8C1834', pips: 3 },
  { base: null, light: '#F4ECF8', dark: '#3A2A48', pips: 0 },
];

export function pill(i) {
  const p = PILL[i];
  const img = image(16, 16);
  const x0 = 0;
  const w = 15;
  const y0 = 2;
  const h = 12;
  rect(img, x0, y0, w, h, K.ink);
  for (let y = y0 + 1; y < y0 + h - 1; y++)
    for (let x = x0 + 1; x < x0 + w - 1; x++) {
      let base = p.base;
      if (!base) base = x < 5 ? '#2E9A3E' : x < 10 ? '#E8B830' : '#D83440';
      put(img, x, y, c(base));
    }
  rect(img, x0 + 1, y0 + 1, w - 2, 1, p.light);
  rect(img, x0 + 1, y0 + h - 2, w - 2, 1, p.dark);
  roundCorners(img, x0, y0, w, h, 2);
  const cx = x0 + Math.floor(w / 2);
  const pips = p.pips;
  for (let k = 0; k < pips; k++) {
    const px = cx - (pips - 1) * 2 + k * 4 - 1;
    rect(img, px, y0 + 4, 3, 4, K.ink);
    rect(img, px, y0 + 4, 2, 3, K.white);
  }
  return img;
}

// ---------------------------------------------------------------------------------------------------------------------
// Attract strips (global §4.13, §5.1 #36): the existing 16² fronts (kept pixel-for-pixel as frame 0) with lamps that
// animate through `.mcmeta` — cashier brass lamp flicker, wheel rim bulbs alternating, Plinko pegs twinkling. The reel
// windows / wheel face / board never change (no fake outcome, global §6.7).

const CASHIER = [
  'aaaaaaaaaaaaaaaa',
  'abbbbbbbbcbbbbba',
  'abddddddddddddba',
  'abedeedeedeedeba',
  'abedeedeedeedeca',
  'acedeedeedeedeba',
  'abedeedeedeedeba',
  'abedeedeedeedeba',
  'abddddddddddddba',
  'abbbcbbbbbbbbbba',
  'affffffffffffffa',
  'agggggggggggggga',
  'abbbbbbbbcbbbbba',
  'abbbbbbhhbbbbbba',
  'abbbbcbbbbbbbbba',
  'aaaaaaaaaaaaaaaa',
];
const CASHIER_PAL = { a: '#462D19', b: '#6E4828', c: '#634024', d: '#CDA03C', e: '#141418', f: '#54361E', g: '#382414', h: '#E1B042' };
const NETHER_CASHIER_PAL = { a: '#6E1419', b: '#2D1C20', c: '#28191C', d: '#F0BE32', e: '#141418', f: '#84181E', g: '#581014', h: '#FFD137' };
const WHEEL = [
  'aaaaaaabbaaaaaaa',
  'aaaaaccbbddaaaaa',
  'aaaacccbbdddaaaa',
  'aaacccccddddeaaa',
  'aaffccccdddeeeaa',
  'affffcccddeeeeea',
  'afffffcggeeeeeea',
  'afffffggggeeeeea',
  'acccccggggccccca',
  'accccccgghccccca',
  'accccciihhhcccca',
  'aaccciiihhhhccaa',
  'aaaciiiihhhhhaaa',
  'aaaaiiiihhhhaaaa',
  'aaaaaiiihhhaaaaa',
  'aaaaaaaaaaaaaaaa',
];
const WHEEL_PAL = { a: '#5A3A1A', b: '#FFFFFF', c: '#8E2A2A', d: '#18B45A', e: '#4FD8E0', f: '#2F7FC0', g: '#FFD700', h: '#7A7A7A', i: '#3A9E4A' };
const PLINKO = [
  'aaaaaaaaaaaaaaaa',
  'abbbbbbbcbbbbbba',
  'abbbbbbbdbbbbbba',
  'abbbbbbbbbbbbbba',
  'abbbbbbdbdbbbbba',
  'abbbbbbbbbbbbbba',
  'abbbbbdbdbdbbbba',
  'abbbbbbbbbbbbbba',
  'abbbbdbdbdbdbbba',
  'abbbbbbbbbbbbbba',
  'abbbdbdbdbdbdbba',
  'abbbbbbbbbbbbbba',
  'abbbbbbbbbbbbbba',
  'aeeffgghhggffeea',
  'aeeffgghhggffeea',
  'aaaaaaaaaaaaaaaa',
];
const PLINKO_PAL = { a: '#9A9A9A', b: '#1D2530', c: '#FF4040', d: '#E8E8E8', e: '#B8860B', f: '#2E7D32', g: '#3D5A80', h: '#8E2A2A' };

const fromRows = (rows, pal) => art16({ id: 'front', pal, grid: rows });

/** Cashier front, 2 frames: 0 = the original, 1 = the brass lamp (h) dimmed + counter lamps (d top row) lower. */
export function cashierFrames(nether) {
  const pal = nether ? NETHER_CASHIER_PAL : CASHIER_PAL;
  const lit = fromRows(CASHIER, pal);
  const dim = fromRows(CASHIER, { ...pal, h: mixHex(pal.h, pal.b, 0.55) });
  // the lit frame gets a 1 px glow above the lamp
  blend(lit, 7, 12, c(pal.h), 0.35);
  blend(lit, 8, 12, c(pal.h), 0.35);
  return [lit, dim];
}

/** Wheel front, 2 frames: rim bulbs on the border alternate (odd / even positions). */
export function wheelFrames() {
  const base = fromRows(WHEEL, WHEEL_PAL);
  const border = [];
  for (let x = 1; x < 15; x += 2) border.push([x, 15]);
  for (let y = 3; y < 14; y += 2) {
    border.push([0, y]);
    border.push([15, y]);
  }
  return [0, 1].map((f) => {
    const img = image(16, 16);
    over(img, base, 0, 0);
    border.forEach(([x, y], i) => put(img, x, y, c((i + f) % 2 === 0 ? '#FFE070' : '#7A4A10')));
    return img;
  });
}

/** Plinko front, 3 frames: one third of the pegs twinkles (bright white + cyan halo pixel) in turn. */
export function plinkoFrames() {
  const base = fromRows(PLINKO, PLINKO_PAL);
  const pegs = [];
  PLINKO.forEach((row, y) => [...row].forEach((ch, x) => ch === 'd' && pegs.push([x, y])));
  return [0, 1, 2].map((f) => {
    const img = image(16, 16);
    over(img, base, 0, 0);
    pegs.forEach(([x, y], i) => {
      if (i % 3 !== f) put(img, x, y, c('#A8A8B0'));
      else put(img, x, y, c('#FFFFFF'));
    });
    return img;
  });
}

// ---------------------------------------------------------------------------------------------------------------------
// World particles (lane J-L3 types; sprites generated here, providers in the client modules)

/** Mob-wave summon rune (16² × 4, drawn flat on the ground): a dark red ring with six rune ticks turning and glowing. */
export function runeFrame(f) {
  const img = image(16, 16);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      if (d > 7.5) continue;
      if (d > 6.4) put(img, x, y, c(f % 2 ? K.red : K.redDark));
      else if (d > 5.6) put(img, x, y, [140, 24, 52, 110]);
      else if (d < 2.2) put(img, x, y, [216, 52, 64, 90 + f * 30]);
    }
  for (let i = 0; i < 6; i++) {
    const a = (i * Math.PI) / 3 + (f * Math.PI) / 12;
    for (let r = 3.2; r < 5.4; r += 0.6) put(img, Math.floor(8 + Math.cos(a) * r), Math.floor(8 + Math.sin(a) * r), c(i % 2 ? K.redLight : K.red));
  }
  return img;
}

/** White glowing dot (8² × 2) for tinted bulbs and motes: hot core, soft rim. */
export function glowDot(f, soft = false) {
  const img = image(8, 8);
  for (let y = 0; y < 8; y++)
    for (let x = 0; x < 8; x++) {
      const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
      const r = soft ? 3.6 : f ? 3.2 : 2.6;
      if (d > r) continue;
      const k = 1 - d / r;
      put(img, x, y, [255, 255, 255, Math.round(255 * Math.min(1, k * (soft ? 1.2 : 1.8)))]);
    }
  return img;
}
