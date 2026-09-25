// Java GUI sprites (animation/slots.md §3.3, §9.1), backdrops and Showdown sprites.
// Looping sprites get a `.mcmeta` animation; code-driven frame sets (chest lid, creeper swell, burn, coin lock,
// pointer, crack) are vertical strips WITHOUT `.mcmeta` — the screen blits the frame it needs (sub-UV).
// No words anywhere: plates and banners are blank; code draws the translated text on them.
import { JAVA_ASSETS, mcmeta, png } from '../../lib/emit.mjs';
import { art } from './art.mjs';
import { ICON } from './icons.mjs';
import {
  affine, blend, brighten, col, crop, desaturate, disc, fade, fill, halo, image, line, outline, over, put, ring, rng, scaleUp, shadow, star, vgrad, vstrip,
} from './raster.mjs';
import { MACHINE_ORDER, MACHINE_SYMBOLS } from './symbols.mjs';
import { THEME } from './theme.mjs';

const J = JAVA_ASSETS;
const SPR = `${J}/textures/gui/sprites/burmaldaholic/slots`;
const INK = '#180A28';

const c = col;
function rect(img, x, y, w, h, hex, k = 1) {
  fill(img, x, y, w, h, c(hex), k);
}
function frameRect(img, x, y, w, h, light, dark) {
  rect(img, x, y, w, 1, light);
  rect(img, x, y, 1, h, light);
  rect(img, x, y + h - 1, w, 1, dark);
  rect(img, x + w - 1, y, 1, h, dark);
}

// ---- cabinet nine-slice (64 × 64, border 12) -------------------------------------------------------------------
function cabinetFrame(m, fs) {
  const t = THEME[m];
  const img = image(64, 64);
  const r = rng(m.length * 97 + (fs ? 7 : 0));
  // outer ink
  rect(img, 0, 0, 64, 64, INK);
  // material band (texture noise by machine)
  for (let y = 1; y < 63; y++)
    for (let x = 1; x < 63; x++) {
      if (x >= 12 && x < 52 && y >= 12 && y < 52) continue;
      const mat = t.material.map(c);
      let px;
      if (m === 'overworld') px = (y + (x > 31 ? 2 : 0)) % 5 === 4 ? mat[3] : mat[((y + (x > 31 ? 2 : 0)) / 5) % 2 < 1 ? 0 : 1];
      else if (m === 'nether') px = mat[(r() * 3) | 0];
      else px = x % 6 === 0 || y % 6 === 0 ? mat[0] : x % 6 === 5 || y % 6 === 5 ? mat[2] : mat[1];
      put(img, x, y, px);
    }
  // trim bands: outer (2 px) and inner bevel (2 px) in the trim colour
  frameRect(img, 1, 1, 62, 62, t.trimLight, t.trimDark);
  frameRect(img, 2, 2, 60, 60, t.trim, t.trimDark);
  frameRect(img, 9, 9, 46, 46, t.trimLight, t.trimDark);
  frameRect(img, 10, 10, 44, 44, t.trim, t.trim);
  frameRect(img, 11, 11, 42, 42, t.trimDark, t.trimLight);
  // centre: the window (behind the reels)
  rect(img, 12, 12, 40, 40, t.window);
  // corner studs / lamps
  for (const [x, y] of [[5, 5], [58, 5], [5, 58], [58, 58]]) {
    disc(img, x + 0.5, y + 0.5, 2.2, c(t.trimDark));
    disc(img, x + 0.5, y + 0.5, 1.6, c(fs ? t.fsBulb : t.rivet));
    put(img, x, y, c(fs ? '#FFFFFF' : t.rivetLight));
  }
  // edge studs (tile along the stretched edges: kept symmetric so nine-slice repeats look regular)
  for (let k = 20; k <= 44; k += 8) for (const [x, y] of [[k, 5], [k, 58], [5, k], [58, k]]) put(img, x, y, c(fs ? t.fsBulb : t.rivet));
  if (m === 'nether') for (let k = 3; k < 61; k += 7) put(img, k, 7, c(fs ? '#FF4A20' : '#FF7A1A'));
  if (m === 'end' && fs) for (let k = 3; k < 61; k += 4) put(img, 7, k, c('#C070FF'));
  if (m === 'overworld') for (let k = 14; k < 50; k += 3) put(img, k, 1, c(fs ? '#9CC8FF' : '#5FC23A'));
  return img;
}

// ---- marquee strip: 128 × 12 frames (4), bulbs chase ---------------------------------------------------------------
function marquee(m, fs) {
  const t = THEME[m];
  const frames = [];
  for (let f = 0; f < 4; f++) {
    const img = image(128, 12);
    rect(img, 0, 0, 128, 12, INK);
    rect(img, 1, 1, 126, 10, fs ? t.fsPanel : t.panel);
    frameRect(img, 1, 1, 126, 10, t.trimLight, t.trimDark);
    for (let i = 0; i < 20; i++) {
      const x = 5 + i * 6.1;
      const on = fs ? (i + f) % 2 === 0 : (i + f) % 4 === 0;
      const bc = fs ? t.fsBulb : t.bulbOn;
      if (on) {
        disc(img, x + 0.5, 6, 2.2, c(bc));
        put(img, Math.round(x), 5, c(t.bulbHot));
        blend(img, Math.round(x) - 3, 6, c(bc), 0.3);
        blend(img, Math.round(x) + 3, 6, c(bc), 0.3);
      } else disc(img, x + 0.5, 6, 1.6, c(t.bulbOff));
    }
    frames.push(img);
  }
  return vstrip(frames);
}

// ---- banners (nine-slice plaques for tier words) ------------------------------------------------------------------
function banner(m, size, border) {
  const t = THEME[m];
  const img = image(size, size);
  rect(img, 0, 0, size, size, INK);
  rect(img, 1, 1, size - 2, size - 2, t.trimDark);
  frameRect(img, 1, 1, size - 2, size - 2, t.trimLight, t.trimDark);
  rect(img, 3, 3, size - 6, size - 6, t.trim);
  frameRect(img, 3, 3, size - 6, size - 6, t.trimLight, t.trimDark);
  rect(img, border - 2, border - 2, size - 2 * border + 4, size - 2 * border + 4, t.panel);
  frameRect(img, border - 2, border - 2, size - 2 * border + 4, size - 2 * border + 4, t.trimDark, t.trimLight);
  for (const [x, y] of [[4, 4], [size - 5, 4], [4, size - 5], [size - 5, size - 5]]) {
    put(img, x, y, c('#FFFFFF'));
    blend(img, x + 1, y, c(t.bulbOn), 0.6);
    blend(img, x, y + 1, c(t.bulbOn), 0.6);
  }
  return img;
}

// ---- anticipation frame: 48 × 140 × 8 frames (pulsing gold glow around one reel column) ------------------------------
function anticipation(m) {
  const t = THEME[m];
  const frames = [];
  for (let f = 0; f < 8; f++) {
    const img = image(48, 140);
    const k = 0.55 + 0.45 * Math.sin((f / 8) * Math.PI * 2);
    for (let d = 0; d < 4; d++) {
      const a = k * (1 - d / 4);
      const colr = c(d === 0 ? '#FFF4B0' : '#FFD640');
      for (let x = d; x < 48 - d; x++) {
        blend(img, x, d, colr, a);
        blend(img, x, 139 - d, colr, a);
      }
      for (let y = d; y < 140 - d; y++) {
        blend(img, d, y, colr, a);
        blend(img, 47 - d, y, colr, a);
      }
    }
    // travelling sparkles along the rim
    for (let s = 0; s < 3; s++) {
      const p = ((f / 8 + s / 3) % 1) * (2 * 48 + 2 * 140);
      let x;
      let y;
      if (p < 48) [x, y] = [p, 1];
      else if (p < 188) [x, y] = [46, p - 48];
      else if (p < 236) [x, y] = [236 - p, 138];
      else [x, y] = [1, 376 - p];
      star(img, Math.round(x), Math.round(y), 2, c(t.bulbOn), 1);
    }
    frames.push(img);
  }
  return vstrip(frames);
}

// ---- shared sprites -----------------------------------------------------------------------------------------------
function glass() {
  const img = image(220, 132);
  for (let y = 0; y < 132; y++)
    for (let x = 0; x < 220; x++) {
      const d = x + y * 0.6 - 150;
      if (Math.abs(d) < 14) blend(img, x, y, [255, 255, 255, 255], 0.08);
      if (Math.abs(d) < 5) blend(img, x, y, [255, 255, 255, 255], 0.05);
    }
  return img;
}

function winFrame() {
  const frames = [];
  for (let f = 0; f < 4; f++) {
    const img = image(44, 44);
    for (let i = 0; i < 44; i++)
      for (const [x, y] of [[i, 0], [i, 43], [0, i], [43, i], [i, 1], [i, 42], [1, i], [42, i]]) put(img, x, y, c(x === 0 || y === 0 || x === 43 || y === 43 ? '#B07010' : '#FFD640'));
    for (let i = 2; i < 42; i++) for (const [x, y] of [[i, 2], [i, 41], [2, i], [41, i]]) blend(img, x, y, c('#FFF4B0'), 0.5);
    // glint travelling around the frame
    const p = (f / 4) * 172;
    const pos = p < 43 ? [p, 1] : p < 86 ? [42, p - 43] : p < 129 ? [129 - p, 42] : [1, 172 - p];
    star(img, Math.round(pos[0]), Math.round(pos[1]), 3, c('#FFFFFF'), 1);
    for (const [x, y] of [[0, 0], [43, 0], [0, 43], [43, 43]]) put(img, x, y, c('#FFFFFF'));
    frames.push(img);
  }
  return vstrip(frames);
}

function pathNode() {
  const a = image(8, 8);
  disc(a, 4, 4, 3, c('#B07010'));
  disc(a, 4, 4, 2.2, c('#FFD640'));
  put(a, 3, 3, c('#FFFFFF'));
  const b = brighten(a, 1.35);
  return vstrip([a, b]);
}

function spinButton(state) {
  const img = image(56, 40);
  const base = { idle: '#D83440', hover: '#FF5A5A', pressed: '#A82030', disabled: '#6A5A6A', stop: '#C07010' }[state];
  const light = { idle: '#FF6E6A', hover: '#FF9A90', pressed: '#D83440', disabled: '#8A7A8A', stop: '#FFD640' }[state];
  const dark = { idle: '#8C1834', hover: '#B02030', pressed: '#6A1020', disabled: '#3A2A3A', stop: '#7A4000' }[state];
  const dy = state === 'pressed' ? 2 : 0;
  // pill with a gold rim
  for (let y = 0; y < 40; y++)
    for (let x = 0; x < 56; x++) {
      const rx = Math.max(0, Math.abs(x + 0.5 - 28) - 8);
      const ry = Math.abs(y + 0.5 - 20);
      const d = Math.hypot(rx, ry);
      if (d > 19.5) continue;
      let colr = d > 18 ? INK : d > 16.5 ? (state === 'disabled' ? '#6A6A6A' : '#FFD640') : y + dy < 16 ? light : base;
      if (d <= 16.5 && y > 30 - dy) colr = dark;
      put(img, x, y, c(colr));
    }
  // icon: circular arrow (spin) or square (stop)
  const cx = 28;
  const cy = 19 + dy;
  if (state === 'stop') rect(img, cx - 5, cy - 5, 10, 10, '#FFFFFF');
  else {
    ring(img, cx, cy, 7, 2, c(state === 'disabled' ? '#A8A0A8' : '#FFFFFF'));
    for (let y = cy - 10; y <= cy; y++) for (let x = cx; x < cx + 9; x++) if (x - cx < cy - y) put(img, x, y, c(light));
    for (let y = -9; y <= -3; y++) for (let x = 3; x <= 3 + (3 - Math.abs(y + 6)); x++) put(img, cx + x, cy + y, c(state === 'disabled' ? '#A8A0A8' : '#FFFFFF'));
  }
  return img;
}

function plate(w, h, hexes) {
  const [edge, face, light, dark] = hexes;
  const img = image(w, h);
  rect(img, 1, 0, w - 2, h, INK);
  rect(img, 0, 1, w, h - 2, INK);
  rect(img, 1, 1, w - 2, h - 2, edge);
  rect(img, 2, 2, w - 4, h - 4, face);
  rect(img, 2, 2, w - 4, 1, light);
  rect(img, 2, h - 3, w - 4, 1, dark);
  return img;
}

function jackpotPlate(tier) {
  const hex = { mini: ['#40A020', '#1E4A10', '#80FF40', '#0E2A08'], minor: ['#2050C0', '#0E1E4A', '#4080FF', '#08102A'], major: ['#B07010', '#4A2E08', '#FFD640', '#2A1804'], grand: ['#B01080', '#4A0A3A', '#FF40C0', '#2A0420'] }[tier];
  const img = plate(94, 18, hex);
  const icon = art(ICON[`jp_${tier}`]);
  over(img, icon, 2, 1);
  return img;
}

function turbo(on) {
  const img = image(16, 16);
  disc(img, 8, 8, 7.5, c(INK));
  disc(img, 8, 8, 6.5, c(on ? '#B07010' : '#3A2A4A'));
  const bolt = ['...##', '..##.', '.####', '...#.', '..#..', '.#...'];
  bolt.forEach((row, j) => [...row].forEach((ch, i) => ch === '#' && put(img, 5 + i, 5 + j, c(on ? '#FFF4B0' : '#8A7A8A'))));
  return img;
}

function lever() {
  const img = image(12, 60);
  rect(img, 4, 10, 4, 48, '#7A7A86');
  rect(img, 4, 10, 1, 48, '#B4B4C2');
  rect(img, 7, 10, 1, 48, '#4A4A56');
  disc(img, 6, 6, 5.5, c(INK));
  disc(img, 6, 6, 4.5, c('#D83440'));
  put(img, 4, 4, c('#FF9A90'));
  rect(img, 1, 55, 10, 5, INK);
  rect(img, 2, 56, 8, 3, '#B07010');
  return img;
}

// ---- screen polish (lane J-L9b): title plate, casino buttons, icons, value plate, side panel ------------------------
/** Per-machine rim colours for plates: the trim (copper / gold), purpur on the End (its trim is near-black). */
function rim(m) {
  const t = THEME[m];
  return m === 'end' ? { rim: '#A77BA7', light: '#D8B4D8', dark: '#5A3A5A' } : { rim: t.trim, light: t.trimLight, dark: t.trimDark };
}

/**
 * Title plate over the marquee (nine-slice 32 × 16, border 2): a 1 px outline and a 1 px bevelled rim around an
 * engraved dark face. The rim is thin on purpose: the plate is only 16 px (14 px compact) tall, and a thicker rim
 * crossed the title's lowest pixel row and its shadow (Cyrillic descenders, J-L9b review).
 */
function titlePlate(m) {
  const t = THEME[m];
  const r = rim(m);
  const img = image(32, 16);
  rect(img, 1, 0, 30, 16, INK);
  rect(img, 0, 1, 32, 14, INK);
  frameRect(img, 1, 1, 30, 14, r.light, r.dark);
  put(img, 1, 1, c(t.rivetLight));
  vgrad(img, 2, 2, 28, 12, c(t.panel), c(INK), 4);
  return img;
}

/** Casino button face (nine-slice 32 × 20, border 6): pill-ish corners, bevel, gold rim. */
function casinoButton(state) {
  const pal = {
    idle: ['#5A2A7A', '#2A0E44', '#FFD640', '#B07010'],
    hover: ['#7A3AA0', '#3A1458', '#FFF4A0', '#FFD640'],
    pressed: ['#2A0E44', '#4A1A6A', '#B07010', '#7A4000'],
    disabled: ['#4A4A52', '#2A2A30', '#7A7A82', '#4A4A52'],
    on: ['#FFD640', '#C07010', '#FFF4B0', '#7A4000'],
    gold: ['#FFC400', '#B07010', '#FFF0A0', '#6A3A00'],
  }[state];
  const [top, bottom, rimLight, rimDark] = pal;
  const img = image(32, 20);
  rect(img, 2, 0, 28, 20, INK);
  rect(img, 1, 1, 30, 18, INK);
  rect(img, 0, 2, 32, 16, INK);
  rect(img, 2, 1, 28, 18, rimDark);
  rect(img, 1, 2, 30, 16, rimDark);
  rect(img, 2, 1, 28, 1, rimLight);
  rect(img, 1, 2, 1, 15, rimLight);
  vgrad(img, 2, 2, 28, 16, c(top), c(bottom), 6);
  if (state !== 'pressed') rect(img, 3, 3, 26, 1, brighter(top));
  else rect(img, 2, 2, 28, 1, INK);
  return img;
}
const brighter = (hex) => {
  const [r, g, b] = c(hex);
  const h = (v) => Math.min(255, Math.round(v + (255 - v) * 0.35)).toString(16).padStart(2, '0');
  return `#${h(r)}${h(g)}${h(b)}`;
};

/** Inset value plate (nine-slice 32 × 18, border 5): dark LCD face with a gold rim (bet value, win meter). */
function valuePlate() {
  const img = image(32, 18);
  rect(img, 1, 0, 30, 18, INK);
  rect(img, 0, 1, 32, 16, INK);
  rect(img, 1, 1, 30, 16, '#B07010');
  frameRect(img, 1, 1, 30, 16, '#FFE680', '#6A3A00');
  rect(img, 3, 3, 26, 12, '#0C0616');
  frameRect(img, 3, 3, 26, 12, '#05020A', '#3A2A4A');
  return img;
}

/** Nice tier plate (nine-slice 16 × 16, border 3): thin gold rim around a deep purple face, so a 2× word fits 20 px. */
function tierPlate() {
  const img = image(16, 16);
  rect(img, 1, 0, 14, 16, INK);
  rect(img, 0, 1, 16, 14, INK);
  rect(img, 1, 1, 14, 14, '#FFD640');
  frameRect(img, 1, 1, 14, 14, '#FFF4B0', '#B07010');
  vgrad(img, 3, 3, 10, 10, c('#5A1A7A'), c('#1E0830'), 4);
  put(img, 1, 1, c('#FFFFFF'));
  return img;
}

/** Side panel (nine-slice 32 × 32, border 8): translucent dark face inside a machine-coloured rim. */
function sidePanel(m) {
  const t = THEME[m];
  const r = rim(m);
  const img = image(32, 32);
  rect(img, 1, 0, 30, 32, INK);
  rect(img, 0, 1, 32, 30, INK);
  rect(img, 1, 1, 30, 30, r.rim);
  frameRect(img, 1, 1, 30, 30, r.light, r.dark);
  rect(img, 3, 3, 26, 26, INK);
  for (let y = 4; y < 28; y++) for (let x = 4; x < 28; x++) put(img, x, y, [...c(t.panel).slice(0, 3), 214]);
  frameRect(img, 3, 3, 26, 26, r.dark, r.light);
  for (const [x, y] of [[2, 2], [29, 2], [2, 29], [29, 29]]) put(img, x, y, c(t.rivetLight));
  return img;
}

/** 12 × 12 control icons: white glyph with an ink outline (drawn on the casino buttons). */
const GLYPHS = {
  auto: [
    '............',
    '....####....',
    '..##....#.#.',
    '.#.......##.',
    '.#......###.',
    '#...........',
    '...........#',
    '.###......#.',
    '.##.......#.',
    '.#.#....##..',
    '....####....',
    '............',
  ],
  paytable: [
    '............',
    '.##########.',
    '.#........#.',
    '.#.##.###.#.',
    '.#........#.',
    '.#.##.###.#.',
    '.#........#.',
    '.#.##.###.#.',
    '.#........#.',
    '.##########.',
    '............',
    '............',
  ],
  minus: [
    '............',
    '............',
    '............',
    '............',
    '............',
    '..########..',
    '..########..',
    '............',
    '............',
    '............',
    '............',
    '............',
  ],
  plus: [
    '............',
    '............',
    '.....##.....',
    '.....##.....',
    '.....##.....',
    '..########..',
    '..########..',
    '.....##.....',
    '.....##.....',
    '.....##.....',
    '............',
    '............',
  ],
  bonus: [
    '............',
    '...##..##...',
    '..#..##..#..',
    '.##########.',
    '.#....#...#.',
    '.##########.',
    '..#...#..#..',
    '..#...#..#..',
    '..#...#..#..',
    '..########..',
    '............',
    '............',
  ],
};
function icon(name) {
  const img = image(12, 12);
  GLYPHS[name].forEach((row, y) => [...row].forEach((ch, x) => ch === '#' && put(img, x, y, c('#FFFFFF'))));
  return outline(img, c(INK), { k: 1 });
}

// ---- feature sprites -------------------------------------------------------------------------------------------------
const cell40 = (def, opts = {}) => {
  const img = affine(art(def, { lit: opts.lit }), 40, 40, { scale: 2, sx: opts.sx ?? 1, sy: opts.sy ?? 1, rot: opts.rot ?? 0, px: 8, py: 8, ox: 20, oy: 20 + (opts.dy ?? 0) });
  return shadow(outline(img, c(INK), { k: 0.9 }), c(INK), 2, 2, 0.4);
};

function chestFrames() {
  // closed → rattle-ready key → lid opening (3) → open with glow
  const frames = [cell40(ICON.chest_closed)];
  frames.push(cell40(ICON.chest_closed, { rot: 4 }));
  frames.push(cell40(ICON.chest_closed, { sy: 1.04, dy: -1 }));
  frames.push(cell40(ICON.chest_open, { sy: 0.96 }));
  frames.push(cell40(ICON.chest_open));
  frames.push(halo(cell40(ICON.chest_open, { lit: true }), c('#FFD640'), 3, 0.8));
  return vstrip(frames);
}

function coinPile(n) {
  const img = image(40, 40);
  const coin = art(MACHINE_SYMBOLS.nether[2]);
  const r = rng(n * 31);
  const count = { s: 3, m: 6, l: 10 }[n];
  for (let i = 0; i < count; i++) {
    const x = 6 + ((r() * 16) | 0);
    const y = 20 - Math.floor(i / 3) * 5 + ((r() * 3) | 0);
    over(img, affine(coin, 16, 12, { scale: 1, sy: 0.6, px: 8, py: 8, ox: 8, oy: 6 }), x, y);
  }
  return shadow(outline(img, c(INK)), c(INK), 1, 2, 0.4);
}

function creeperFrames() {
  return vstrip([0, 1, 2, 3].map((f) => {
    const s = 1 + f * 0.06;
    let img = cell40(ICON.creeper, { sx: s, sy: s });
    if (f === 3) img = brighten(img, 1.5);
    return img;
  }));
}

function stackBracket() {
  const frames = [];
  for (let f = 0; f < 4; f++) {
    const img = image(44, 92);
    const glow = 0.4 + 0.6 * Math.abs(Math.sin((f / 4) * Math.PI));
    for (let y = 0; y < 92; y++)
      for (const x of [0, 1, 42, 43]) put(img, x, y, c(x === 0 || x === 43 ? '#B07010' : '#FFD640'));
    for (let x = 0; x < 44; x++) for (const y of [0, 1, 90, 91]) put(img, x, y, c(y === 0 || y === 91 ? '#B07010' : '#FFD640'));
    for (let y = 2; y < 90; y++) {
      blend(img, 2, y, c('#FFF4B0'), glow * 0.7);
      blend(img, 41, y, c('#FFF4B0'), glow * 0.7);
    }
    // central clasp linking the two cells
    rect(img, 16, 43, 12, 6, '#B07010');
    rect(img, 17, 44, 10, 4, '#FFD640');
    put(img, 21, 45, c('#FFFFFF'));
    frames.push(img);
  }
  return vstrip(frames);
}

function burnFrames(m) {
  const t = THEME[m];
  return vstrip([0, 1, 2, 3, 4].map((f) => {
    const img = image(44, 44);
    const r = rng(f * 13 + 5);
    const front = 46 - f * 11;
    for (let y = 0; y < 44; y++)
      for (let x = 0; x < 44; x++) {
        const edge = front + Math.sin(x * 0.7 + f * 1.7) * 2.5;
        const d = y - edge;
        if (d < -7 || d > 5) continue;
        const colr = d > 2 ? '#C83A08' : d > -2 ? '#FF7A1A' : '#FFE070';
        const k = d < -4 ? 0.45 : 1;
        if (r() < (d < -4 ? 0.5 : 0.95)) blend(img, x, y, c(colr), k);
      }
    // ash specks below the front, embers rising above it
    for (let k = 0; k < 10; k++) {
      const x = (r() * 44) | 0;
      put(img, x, Math.min(43, front + 6 + ((r() * 10) | 0)), c('#3A2A2A'));
      blend(img, (r() * 44) | 0, Math.max(0, front - 10 - ((r() * 12) | 0)), c(t.accent), 0.9);
    }
    return img;
  }));
}

function ladderPlate(lit) {
  const hex = lit ? ['#FFD640', '#FF9A1A', '#FFF4B0', '#B07010'] : ['#6A4A10', '#2A1A08', '#8A6A30', '#140A04'];
  return plate(72, 16, hex);
}

function coinLock() {
  return vstrip([0, 1, 2].map((f) => {
    const img = image(44, 44);
    over(img, cell40(MACHINE_SYMBOLS.nether[2]), 2, 2);
    const off = (2 - f) * 3; // the four clasps slide in from the corners
    for (const [sx, sy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      const x = sx < 0 ? -off : 44 - 9 + off;
      const y = sy < 0 ? -off : 44 - 9 + off;
      const hx = sx < 0 ? x : x + 6;
      const vy = sy < 0 ? y : y + 6;
      rect(img, x, vy, 9, 3, '#FFD640');
      rect(img, hx, y, 3, 9, '#FFD640');
      rect(img, x, vy + (sy < 0 ? 2 : 0), 9, 1, '#B07010');
      rect(img, hx + (sx < 0 ? 2 : 0), y, 1, 9, '#B07010');
    }
    if (f === 2) frameRect(img, 0, 0, 44, 44, '#FFF4B0', '#B07010');
    return f === 2 ? brighten(img, 1.12) : img;
  }));
}

function stickyFrame() {
  const frames = [];
  for (let f = 0; f < 4; f++) {
    const img = image(44, 136);
    for (let y = 0; y < 136; y++)
      for (const x of [0, 1, 2, 41, 42, 43]) {
        const link = (y + (x > 20 ? 2 : 0)) % 6;
        put(img, x, y, c(link < 2 ? '#D0A0FF' : link < 4 ? '#9A50E0' : '#5A2A8A'));
      }
    for (let x = 0; x < 44; x++)
      for (const y of [0, 1, 2, 133, 134, 135]) {
        const link = x % 6;
        put(img, x, y, c(link < 2 ? '#D0A0FF' : link < 4 ? '#9A50E0' : '#5A2A8A'));
      }
    const gy = Math.round((f / 4) * 136);
    star(img, 1, gy, 3, c('#FFFFFF'), 1);
    star(img, 42, 135 - gy, 3, c('#FFFFFF'), 1);
    frames.push(img);
  }
  return vstrip(frames);
}

/** One wedge (apex at the bottom centre) of a ring with `n` segments, in the ring's material; code rotates + tints. */
function wedge(n, size, fillHex, edgeHex) {
  const img = image(size, size);
  const cx = size / 2;
  const cy = size;
  const half = Math.PI / n;
  for (let y = 0; y < size; y++)
    for (let x = 0; x < size; x++) {
      const dx = x + 0.5 - cx;
      const dy = cy - (y + 0.5);
      const d = Math.hypot(dx, dy);
      const a = Math.atan2(dx, dy);
      if (d > size - 0.5 || d < size * 0.25 || Math.abs(a) > half) continue;
      const edge = Math.abs(a) > half - 0.05 || d > size - 2;
      put(img, x, y, c(edge ? edgeHex : fillHex));
    }
  return img;
}

function pointerFrames() {
  const a = cell40(ICON.pointer);
  const b = affine(a, 40, 40, { rot: 12, px: 20, py: 6, ox: 20, oy: 6 });
  return vstrip([crop(a, 12, 4, 16, 24), crop(b, 12, 4, 16, 24)]);
}

function upArrow() {
  const img = image(16, 16);
  const g = ['.......k........', '......kPk.......', '.....kPPPk......', '....kPPwPPk.....', '...kPPPwPPPk....', '..kkkkPwPkkkk...', '.....kPwPk......', '.....kPPPk......', '.....kPPPk......', '.....kPPPk......', '.....kkkkk......'];
  g.forEach((row, j) => [...row].forEach((ch, i) => ch !== '.' && put(img, i, j + 3, c({ k: '#2A0A40', P: '#B040FF', w: '#F0C0FF' }[ch]))));
  return img;
}

// ---- Showdown sprites -------------------------------------------------------------------------------------------------
function crackFrames() {
  return vstrip([0, 1, 2, 3].map((f) => {
    const img = image(124, 76);
    const r = rng(4242);
    const branches = 3 + f * 2;
    for (let b = 0; b < branches; b++) {
      let x = 62;
      let y = 38;
      const ang = (b / branches) * Math.PI * 2 + r() * 0.5;
      const len = 14 + f * 9 + r() * 10;
      for (let s = 0; s < len; s += 3) {
        const nx = x + Math.cos(ang + (r() - 0.5) * 0.8) * 3;
        const ny = y + Math.sin(ang + (r() - 0.5) * 0.8) * 3;
        line(img, x, y, nx, ny, c('#FFFFFF'), 0.85);
        line(img, x + 1, y + 1, nx + 1, ny + 1, c(INK), 0.6);
        x = nx;
        y = ny;
      }
    }
    return img;
  }));
}

function flameUnderline() {
  return vstrip([0, 1, 2].map((f) => {
    const img = image(80, 6);
    for (let x = 0; x < 80; x++) {
      const h = 2 + Math.round(2 * Math.abs(Math.sin(x * 0.45 + f * 1.3)));
      for (let y = 6 - h; y < 6; y++) put(img, x, y, c(y === 6 - h ? '#FFE070' : y < 5 ? '#FF7A1A' : '#C83A08'));
    }
    return img;
  }));
}

function chipX2() {
  const img = image(16, 16);
  for (const [dx, dy] of [[2, 4], [0, 0]]) {
    disc(img, 7 + dx, 7 + dy, 5.5, c(INK));
    disc(img, 7 + dx, 7 + dy, 4.8, c('#D83440'));
    ring(img, 7 + dx, 7 + dy, 3.5, 1, c('#F4ECF8'));
    put(img, 6 + dx, 6 + dy, c('#FF6E6A'));
  }
  return img;
}

// ---- backdrops (400 × 240), free-spin backdrops, parallax tiles (256 × 128) ------------------------------------------
function hills(img, y0, amp, freq, phase, hex, k = 1) {
  for (let x = 0; x < img.w; x++) {
    const h = Math.round(y0 - amp * (0.5 + 0.5 * Math.sin(x * freq + phase)) - amp * 0.3 * Math.sin(x * freq * 2.7 + phase * 2));
    for (let y = h; y < img.h; y++) blend(img, x, y, c(hex), k);
  }
}
function cloud(img, x, y, s, hex = '#FFFFFF', k = 1) {
  for (const [dx, dy, r] of [[0, 0, 6], [8, -3, 7], [16, 0, 6], [7, 3, 6]]) disc(img, x + dx * s, y + dy * s, r * s, c(hex), k);
}

function backdrop(m, fs) {
  const t = THEME[m];
  const img = image(400, 240);
  const [top, bottom] = fs ? t.fsSky : t.sky;
  vgrad(img, 0, 0, 400, 240, c(top), c(bottom), 12);
  const r = rng(m.length * 1000 + (fs ? 1 : 0));
  if (m === 'overworld') {
    if (!fs) {
      disc(img, 330, 50, 18, c('#FFF4B0'));
      disc(img, 330, 50, 14, c('#FFE070'));
      cloud(img, 40, 40, 1.2);
      cloud(img, 170, 70, 0.9);
      cloud(img, 250, 30, 1);
      hills(img, 200, 30, 0.02, 1, '#7CC468');
      hills(img, 225, 20, 0.035, 3, '#4FA83A');
      for (let k = 0; k < 40; k++) put(img, (r() * 400) | 0, 225 + ((r() * 14) | 0), c(['#FF5A5A', '#FFE070', '#FFFFFF', '#B07AFF'][k % 4]));
    } else {
      for (let k = 0; k < 120; k++) put(img, (r() * 400) | 0, (r() * 170) | 0, c(r() < 0.2 ? '#FFFFFF' : '#8AA0D8'));
      disc(img, 320, 50, 16, c('#F4ECD8'));
      disc(img, 327, 45, 14, c(top));
      hills(img, 200, 30, 0.02, 1, '#1A3A3A');
      hills(img, 225, 20, 0.035, 3, '#10282A');
      for (const x of [60, 150, 260, 350]) {
        rect(img, x, 205, 2, 14, '#3A2A1A');
        disc(img, x + 1, 203, 4, c('#FFB040'), 0.5);
        rect(img, x - 1, 200, 4, 5, '#FFD080');
      }
    }
  } else if (m === 'nether') {
    // basalt pillars
    for (let k = 0; k < 9; k++) {
      const x = 10 + k * 45 + ((r() * 20) | 0);
      const w = 14 + ((r() * 12) | 0);
      const h = 80 + ((r() * 90) | 0);
      rect(img, x, 240 - h, w, h, fs ? '#2A0606' : '#241E28');
      rect(img, x, 240 - h, 2, h, fs ? '#4A0A0A' : '#3A3440');
    }
    // lava lake glow
    for (let y = 210; y < 240; y++) for (let x = 0; x < 400; x++) blend(img, x, y, c(y > 222 ? '#FF7A1A' : '#C83A08'), y > 222 ? 1 : (y - 210) / 14);
    for (let k = 0; k < 60; k++) put(img, (r() * 400) | 0, 224 + ((r() * 16) | 0), c('#FFE070'));
    for (let k = 0; k < 50; k++) put(img, (r() * 400) | 0, (r() * 200) | 0, c(r() < 0.5 ? '#FF9A30' : '#FF5A10'));
    if (fs) for (let x = 0; x < 400; x++) for (let y = 0; y < 14; y++) blend(img, x, y + Math.round(3 * Math.sin(x * 0.1)), c('#FF3A10'), 0.5 - y / 30);
  } else {
    for (let k = 0; k < 160; k++) put(img, (r() * 400) | 0, (r() * 200) | 0, c(r() < 0.15 ? '#FFFFFF' : r() < 0.5 ? '#6A5A8A' : '#3A2A5A'));
    if (fs)
      for (let x = 0; x < 400; x++) {
        const top = 40 + Math.round(18 * Math.sin(x * 0.021) + 6 * Math.sin(x * 0.07));
        const len = 34 + Math.round(10 * Math.sin(x * 0.05 + 1));
        for (let d = 0; d < len; d++) blend(img, x, top + d, c(d < len * 0.4 ? '#60F0B0' : '#B040FF'), 0.34 * (1 - d / len));
      }
    // floating end-stone island: shaded top, jagged underside, a far End City tower
    const island = (cx, cy, w, depth) => {
      for (let x = cx - w; x <= cx + w; x++) {
        const u = (x - cx) / w;
        const top = cy - Math.round(6 * Math.cos(u * Math.PI * 0.5) + (x % 7 === 0 ? 1 : 0));
        const bottom = cy + Math.round(depth * (1 - u * u) * (0.75 + 0.25 * Math.sin(x * 1.3)));
        for (let y = top; y <= bottom; y++) {
          const shade = y === top ? '#FAF6CE' : y < top + 3 ? '#E8E4A8' : (x + y) % 3 === 0 && y > cy + 2 ? '#8E8A5A' : y > cy + 4 ? '#A8A060' : '#C8C080';
          put(img, x, y, c(shade));
        }
      }
    };
    island(200, 190, 140, 40);
    island(60, 120, 24, 10);
    island(352, 96, 18, 8);
    rect(img, 270, 128, 16, 58, '#8E648E');
    rect(img, 270, 128, 2, 58, '#A77BA7');
    rect(img, 266, 122, 24, 6, '#A77BA7');
    rect(img, 272, 104, 12, 18, '#8E648E');
    rect(img, 268, 100, 20, 4, '#A77BA7');
    for (const [x, y] of [[274, 140], [280, 140], [274, 156], [280, 156], [277, 110]]) put(img, x, y, c('#F4ECF8'));
    put(img, 278, 96, c('#F4ECF8'));
  }
  return img;
}

function parallax(m) {
  const img = image(256, 128);
  const r = rng(m.length * 77);
  if (m === 'overworld') {
    cloud(img, 20, 30, 1, '#FFFFFF', 0.85);
    cloud(img, 140, 70, 0.8, '#FFFFFF', 0.8);
    cloud(img, 200, 20, 0.7, '#FFFFFF', 0.75);
  } else if (m === 'nether') for (let k = 0; k < 40; k++) blend(img, (r() * 256) | 0, (r() * 128) | 0, c(r() < 0.5 ? '#FF9A30' : '#FFE070'), 0.9);
  else for (let k = 0; k < 60; k++) blend(img, (r() * 256) | 0, (r() * 128) | 0, c(r() < 0.3 ? '#FFFFFF' : '#9A8AC8'), 0.9);
  return img;
}

// ---- outputs ---------------------------------------------------------------------------------------------------------------
export function guiOutputs() {
  const out = [];
  const sprite = (rel, img, meta) => {
    out.push(png(`${SPR}/${rel}.png`, img));
    if (meta) out.push(mcmeta(`${SPR}/${rel}.png`, meta));
  };
  for (const m of MACHINE_ORDER) {
    sprite(`${m}/cabinet`, cabinetFrame(m, false), { nineSlice: { width: 64, height: 64, border: 12 } });
    sprite(`${m}/cabinet_fs`, cabinetFrame(m, true), { nineSlice: { width: 64, height: 64, border: 12 } });
    sprite(`${m}/marquee`, marquee(m, false), { frametime: 3, frame: [128, 12] });
    sprite(`${m}/marquee_fs`, marquee(m, true), { frametime: 3, frame: [128, 12] });
    sprite(`${m}/banner`, banner(m, 48, 12), { nineSlice: { width: 48, height: 48, border: 12 } });
    sprite(`${m}/banner_small`, banner(m, 32, 8), { nineSlice: { width: 32, height: 32, border: 8 } });
    sprite(`${m}/anticipation`, anticipation(m), { frametime: 1, frame: [48, 140] });
    sprite(`${m}/title_plate`, titlePlate(m), { nineSlice: { width: 32, height: 16, border: 2 } });
    sprite(`${m}/side_panel`, sidePanel(m), { nineSlice: { width: 32, height: 32, border: 8 } });
    out.push(png(`${J}/textures/gui/slots/${m}_backdrop.png`, backdrop(m, false)));
    out.push(png(`${J}/textures/gui/slots/${m}_backdrop_fs.png`, backdrop(m, true)));
    out.push(png(`${J}/textures/gui/slots/${m}_parallax.png`, parallax(m)));
  }
  sprite('glass', glass());
  sprite('win_frame', winFrame(), { frametime: 2 });
  sprite('path_node', pathNode(), { frametime: 4 });
  for (const s of ['idle', 'hover', 'pressed', 'disabled', 'stop']) sprite(s === 'idle' ? 'spin_button' : `spin_button_${s}`, spinButton(s));
  sprite('maxwin_plate', plate(128, 32, ['#B07010', '#4A0A3A', '#FF40C0', '#2A0420']));
  for (const tier of ['mini', 'minor', 'major', 'grand']) sprite(`jackpot_plate_${tier}`, jackpotPlate(tier));
  sprite('pip_on', crop(scaleUp(art(ICON.pip_on), 1), 4, 4, 8, 8));
  sprite('pip_off', crop(art(ICON.pip_off), 4, 4, 8, 8));
  sprite('turbo_on', turbo(true));
  sprite('turbo_off', turbo(false));
  sprite('lever', lever());
  // screen polish (J-L9b): casino buttons, icons, value plate
  for (const st of ['idle', 'hover', 'pressed', 'disabled', 'on', 'gold']) {
    sprite(st === 'idle' ? 'button' : `button_${st}`, casinoButton(st), { nineSlice: { width: 32, height: 20, border: 6 } });
  }
  for (const n of Object.keys(GLYPHS)) sprite(`icon_${n}`, icon(n));
  sprite('tier_plate', tierPlate(), { nineSlice: { width: 16, height: 16, border: 3 } });
  sprite('value_plate', valuePlate(), { nineSlice: { width: 32, height: 18, border: 5 } });
  // Overworld features
  sprite('overworld/chest', chestFrames());
  sprite('overworld/chest_dim', fade(desaturate(cell40(ICON.chest_open), 0.6), 0.6));
  for (const n of ['s', 'm', 'l']) sprite(`overworld/coin_pile_${n}`, coinPile(n));
  sprite('overworld/creeper', creeperFrames());
  sprite('overworld/stack_bracket', stackBracket(), { frametime: 3, frame: [44, 92] });
  // Nether features
  sprite('nether/burn', burnFrames('nether'));
  sprite('nether/ladder_plate', ladderPlate(false));
  sprite('nether/ladder_plate_lit', vstrip([ladderPlate(true), brighten(ladderPlate(true), 1.2)]), { frametime: 4, frame: [72, 16] });
  sprite('nether/coin_lock', coinLock());
  sprite('nether/empty_cell', affine(art(ICON.empty_cell), 44, 44, { scale: 2.75, px: 8, py: 8, ox: 22, oy: 22 }));
  sprite('nether/ember_blur', affine(art(ICON.ember_blur), 44, 44, { scale: 2.75, px: 8, py: 8, ox: 22, oy: 22 }));
  // End features
  sprite('end/sticky_frame', stickyFrame(), { frametime: 3, frame: [44, 136] });
  sprite('end/wedge_outer', wedge(20, 64, '#E8E4A8', '#B07010'));
  sprite('end/wedge_middle', wedge(16, 48, '#A77BA7', '#B07010'));
  sprite('end/wedge_core', wedge(12, 32, '#5A2A6A', '#FFD640'));
  sprite('end/pointer', pointerFrames());
  sprite('end/up_arrow', upArrow());
  // Showdown (pvp/slots; presentation only, BS9/JS16 wire them later)
  const PVP = `${J}/textures/gui/sprites/burmaldaholic/pvp/slots`;
  const pvp = (rel, img, meta) => {
    out.push(png(`${PVP}/${rel}.png`, img));
    if (meta) out.push(mcmeta(`${PVP}/${rel}.png`, meta));
  };
  pvp('hazard_kaboom', art(ICON.hazard_kaboom));
  pvp('hazard_swap', art(ICON.hazard_swap));
  pvp('hazard_warp', art(ICON.hazard_warp));
  pvp('crack', crackFrames());
  pvp('flame_underline', flameUnderline(), { frametime: 3, frame: [80, 6] });
  pvp('chip_x2', chipX2());
  pvp('check', art(ICON.ready_check));
  return out;
}
