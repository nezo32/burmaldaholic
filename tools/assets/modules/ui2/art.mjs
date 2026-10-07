// Table UI v2 kit art (lane K; docs/architecture/ui-v2.md §9, docs/design/visual/v2/REDESIGN.md §5, §10): chips with
// language-neutral value digits, felt discs, pills, plates, stamps, buttons, key plates, icon buttons and glyphs, the
// TopBar / Dock bars, banner, toast, felt tiles and vignette. Tokens of REDESIGN §5. Pure functions → images.
import { ellipse, image, rect, setPx } from '../../lib/grid.mjs';
import { alpha, mix, shade } from '../../lib/palette.mjs';

export const T = {
  ink: '#180A28',
  panel: '#22122F',
  panelHi: '#341E48',
  edge: '#783CBE',
  text: '#F4ECF8',
  muted: '#C0B0DC',
  gold: '#FFD640',
  goldShade: '#B07010',
  goldLight: '#FFF4B0',
  win: '#80FF40',
  lose: '#FF6E6A',
  push: '#8FA8C8',
  winFill: '#2E8C28',
  loseFill: '#B0242E',
  pushFill: '#4A5A78',
  goldFill: '#C88A1C',
  you: '#5CE8E0',
  felt: '#1E5E3A',
  feltPrint: '#E8D9A0',
  rail: '#5A3418',
  railDark: '#3A2010',
  railHi: '#8A5628',
  btn: '#4E287C',
  btnHi: '#7046A4',
  danger: '#9C2430',
  dangerHi: '#C03440',
};

/** Chip denominations: base, stripe (edge spots), digit colour. */
export const CHIP = {
  1: { base: '#E4E4EC', stripe: '#3C78D8', digit: '#2A2A3A' },
  5: { base: '#C62828', stripe: '#F5F5F5', digit: '#FFFFFF' },
  25: { base: '#2E7D32', stripe: '#F5F5F5', digit: '#FFFFFF' },
  100: { base: '#262626', stripe: '#F5F5F5', digit: '#FFFFFF' },
  500: { base: '#6A1B9A', stripe: '#F3D34A', digit: '#FFFFFF' },
  1000: { base: '#E8C030', stripe: '#3A2010', digit: '#3A2010' },
  5000: { base: '#E07A1C', stripe: '#FFF4B0', digit: '#2A1408' },
};
export const DENOMS = [1, 5, 25, 100, 500, 1000, 5000];

// ---- digits (language-neutral numerals and the K suffix; no words) ---------------------------------------------------

const D35 = {
  0: ['###', '#.#', '#.#', '#.#', '###'],
  1: ['.#.', '##.', '.#.', '.#.', '###'],
  2: ['###', '..#', '###', '#..', '###'],
  5: ['###', '#..', '###', '..#', '###'],
  K: ['#.#', '#.#', '##.', '#.#', '#.#'],
};
const D57 = {
  0: ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'],
  1: ['..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'],
  2: ['.###.', '#...#', '....#', '..##.', '.#...', '#....', '#####'],
  5: ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'],
  K: ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'],
};
export const chipLabel = (d) => (d >= 1000 ? `${d / 1000}K` : String(d));

function glyphs(img, text, cx, cy, font, colour, shadow) {
  const gw = font[0][0].length;
  const gh = font[0].length;
  const w = text.length * (gw + 1) - 1;
  const x0 = Math.round(cx - w / 2);
  const y0 = Math.round(cy - gh / 2);
  for (const pass of shadow ? [1, 0] : [0]) {
    let x = x0;
    for (const ch of text) {
      const g = font[ch];
      g.forEach((row, j) => [...row].forEach((p, i) => p === '#' && setPx(img, x + i + pass, y0 + j + pass, pass ? shadow : colour)));
      x += gw + 1;
    }
  }
}

// ---- chips -------------------------------------------------------------------------------------------------------------

/** A tray chip `size`² (20 / 24 / 32): ink rim, base body, 8 edge spots, inner ring, face, centred digits. */
export function chip(denom, size) {
  const s = CHIP[denom];
  const img = image(size, size);
  const c = size / 2;
  const r = size / 2 - 0.5;
  ellipse(img, c, c, r, r, T.ink);
  ellipse(img, c, c, r - 1, r - 1, (x, y, d) => {
    // 8 edge spots: angular bands on the outer ring
    const a = Math.atan2(y + 0.5 - c, x + 0.5 - c);
    const seg = ((a / (2 * Math.PI)) * 8 + 8.25) % 1;
    const rim = d > 0.72;
    if (rim && seg < 0.5) return s.stripe;
    if (rim) return d > 0.93 ? shade(s.base, 0.75) : s.base;
    return d > 0.64 ? shade(s.base, 0.62) : mix(s.base, '#FFFFFF', d < 0.25 ? 0.12 : 0.05);
  });
  // lower-right shade, upper-left light (3D)
  ellipse(img, c, c, r - 1, r - 1, (x, y, d) => (d > 0.9 && x + y > size ? shade(s.base, 0.55) : undefined));
  const label = chipLabel(denom);
  const font = size >= 32 ? D57 : D35;
  glyphs(img, label, c, c, font, s.digit, s.digit === '#FFFFFF' ? shade(s.base, 0.45) : undefined);
  return img;
}

/** The gold selection ring around a tray chip (chip size + 4). */
export function chipRing(size) {
  const n = size + 4;
  const img = image(n, n);
  const c = n / 2;
  ellipse(img, c, c, c - 0.5, c - 0.5, (x, y, d) => (d > 0.84 ? (d > 0.95 ? T.goldShade : T.gold) : undefined));
  return img;
}

/** A top-down felt disc (10 / 12 / 16). */
export function disc(denom, size) {
  const s = CHIP[denom];
  const img = image(size, size);
  const c = size / 2;
  ellipse(img, c, c, c - 0.5, c - 0.5, T.ink);
  ellipse(img, c, c, c - 1.5, c - 1.5, (x, y, d) => {
    const a = Math.atan2(y + 0.5 - c, x + 0.5 - c);
    const seg = ((a / (2 * Math.PI)) * 6 + 6.25) % 1;
    if (d > 0.62) return seg < 0.45 ? s.stripe : s.base;
    return d > 0.45 ? shade(s.base, 0.7) : mix(s.base, '#FFFFFF', 0.1);
  });
  // the edge under the disc (stack thickness)
  for (let x = 2; x < size - 2; x++) setPx(img, x, size - 1, shade(s.base, 0.45));
  return img;
}

/** The TopBar balance chip icon (10²). */
export function chipIcon() {
  const img = image(10, 10);
  ellipse(img, 5, 5, 4.5, 4.5, T.ink);
  ellipse(img, 5, 5, 3.5, 3.5, (x, y, d) => (d > 0.55 ? ((x + y) % 2 ? '#FFFFFF' : '#D83440') : '#F4ECF8'));
  return img;
}

// ---- nine-slices -------------------------------------------------------------------------------------------------------

/** A rounded pill: ink outline (corners cut), fill, light top row, shade bottom row. */
export function pill(w, h, fill, rim = T.ink, { light = 0.18, dark = 0.72, edgeTop = null, edgeBottom = null } = {}) {
  const img = image(w, h);
  rect(img, 1, 0, w - 2, h - 1, rim);
  rect(img, 0, 1, w - 1, h - 2, rim);
  rect(img, 1, 1, w - 2, h - 2, fill);
  rect(img, 2, 1, w - 3, 1, mix(fill, '#FFFFFF', light));
  rect(img, 1, h - 2, w - 2, h - 2, shade(fill, dark));
  if (edgeTop) rect(img, 1, 0, w - 2, 0, edgeTop);
  if (edgeBottom) {
    rect(img, 0, h - 2, w - 1, h - 1, edgeBottom);
    rect(img, 1, h - 3, w - 2, h - 3, shade(edgeBottom, 0.55));
  }
  return img;
}

/** Value pills (12 × 11). */
export const VALUE_PILLS = {
  value_pill: [T.panel, T.ink],
  value_pill_you: ['#1C4A52', T.you],
  value_pill_win: [T.winFill, T.ink],
  value_pill_lose: [T.loseFill, T.ink],
  value_pill_push: [T.pushFill, T.ink],
};

/** Seat plates 24 × 18: panel, 1 px edge colour on top, 2 px at the bottom (REDESIGN §4). */
export function plate(edge) {
  const img = image(24, 18);
  rect(img, 0, 0, 23, 17, T.ink);
  rect(img, 1, 1, 22, 15, T.panel);
  rect(img, 1, 1, 22, 1, mix(T.panel, '#FFFFFF', 0.08));
  rect(img, 0, 0, 23, 0, edge);
  rect(img, 0, 16, 23, 17, edge);
  rect(img, 1, 15, 22, 15, shade(edge, 0.5));
  return img;
}

/** Stamps 24 × 16 (fill colour, a lighter top bevel, darker bottom; ink rim). */
export function stamp(fill) {
  const img = image(24, 16);
  rect(img, 0, 0, 23, 15, T.ink);
  rect(img, 1, 1, 22, 14, fill);
  rect(img, 1, 1, 22, 2, mix(fill, '#FFFFFF', 0.3));
  rect(img, 1, 13, 22, 14, shade(fill, 0.68));
  rect(img, 2, 3, 2, 12, mix(fill, '#FFFFFF', 0.12));
  return img;
}

export function stampDot(fill) {
  const img = image(9, 9);
  ellipse(img, 4.5, 4.5, 4.5, 4.5, T.ink);
  ellipse(img, 4.5, 4.5, 3.5, 3.5, (x, y, d) => (d < 0.5 && x + y < 8 ? mix(fill, '#FFFFFF', 0.35) : fill));
  return img;
}

/** Buttons 32 × 30 per style and state. */
const BUTTON = {
  button_primary: { base: '#FFD640', hover: '#FFE478', pressed: '#E8B830', disabled: '#5A4E2A', rim: T.ink, bottom: T.goldShade },
  button: { base: T.btn, hover: T.btnHi, pressed: '#40206A', disabled: '#2C2038', rim: T.ink, bottom: '#2E1650' },
  button_danger: { base: T.danger, hover: T.dangerHi, pressed: '#7C1A24', disabled: '#3A1A22', rim: T.ink, bottom: '#5A1018' },
};
export const BUTTON_STATES = ['', '_hover', '_pressed', '_disabled'];
export const BUTTON_STYLES = Object.keys(BUTTON);

export function button(style, state, w = 32, h = 30) {
  const s = BUTTON[style];
  const fill = state === '_hover' ? s.hover : state === '_pressed' ? s.pressed : state === '_disabled' ? s.disabled : s.base;
  const img = image(w, h);
  const pressed = state === '_pressed';
  rect(img, 1, 0, w - 2, h - 1, s.rim);
  rect(img, 0, 1, w - 1, h - 2, s.rim);
  rect(img, 1, 1, w - 2, h - 2, fill);
  if (state !== '_disabled') {
    rect(img, 2, 1, w - 3, 1, mix(fill, '#FFFFFF', pressed ? 0.08 : 0.35));
    rect(img, 1, 2, 1, h - 4, mix(fill, '#FFFFFF', 0.15));
  }
  // the 3D lip at the bottom (gone when pressed)
  if (!pressed) rect(img, 1, h - 4, w - 2, h - 2, state === '_disabled' ? shade(fill, 0.75) : s.bottom);
  else rect(img, 1, h - 2, w - 2, h - 2, shade(fill, 0.7));
  return img;
}

/** Icon buttons 20 × 20 (tools and TopBar icons; nine-slice for 14–24). */
export function iconButton(state) {
  return button('button', state, 20, 20);
}

/** Key hint plate 10 × 10 (drawn under the key name). */
export function keyHint() {
  const img = image(10, 10);
  rect(img, 0, 0, 9, 9, alpha(T.ink, 0.55));
  rect(img, 1, 1, 8, 8, alpha('#000000', 0.25));
  rect(img, 1, 8, 8, 8, alpha('#FFFFFF', 0.18));
  return img;
}

/** Bars 32 × h: ink fill, a gold-shade trim line and a rail-dark line at the edge facing the felt. */
export function bar(h, trimAtBottom) {
  const img = image(32, h);
  rect(img, 0, 0, 31, h - 1, T.ink);
  for (let x = 0; x < 32; x += 4) setPx(img, x + (trimAtBottom ? 1 : 2), trimAtBottom ? 3 : h - 4, mix(T.ink, '#FFFFFF', 0.04));
  const a = trimAtBottom ? h - 2 : 1;
  const b = trimAtBottom ? h - 1 : 0;
  rect(img, 0, a, 31, a, T.goldShade);
  rect(img, 0, b, 31, b, T.railDark);
  return img;
}

/** The result banner 48 × 28: gold rim, ink face. */
export function banner() {
  const img = image(48, 28);
  rect(img, 0, 0, 47, 27, T.goldShade);
  rect(img, 1, 1, 46, 26, T.gold);
  rect(img, 1, 1, 46, 1, T.goldLight);
  rect(img, 3, 3, 44, 24, T.ink);
  rect(img, 3, 3, 44, 3, T.panelHi);
  return img;
}

/** The toast on the felt 32 × 15. */
export function toast() {
  const img = image(32, 15);
  rect(img, 0, 0, 31, 14, alpha(T.ink, 0.94));
  rect(img, 0, 0, 31, 0, T.panelHi);
  rect(img, 0, 14, 31, 14, shade(T.ink, 0.6));
  return img;
}

/** Title / info / balance / total pills 16 × 14. */
export function smallPill(fill, rim) {
  return pill(16, 14, fill, rim, { light: 0.1 });
}

/** The focus ring 16² (gold, 1 px, corners cut). */
export function focusRing() {
  const img = image(16, 16);
  rect(img, 1, 0, 14, 0, T.gold);
  rect(img, 1, 15, 14, 15, T.gold);
  rect(img, 0, 1, 0, 14, T.gold);
  rect(img, 15, 1, 15, 14, T.gold);
  return img;
}

/** The felt edge vignette 64² (nine-slice border 16, transparent centre). */
export function vignette() {
  const img = image(64, 64);
  for (let y = 0; y < 64; y++)
    for (let x = 0; x < 64; x++) {
      const d = Math.min(x, y, 63 - x, 63 - y);
      if (d >= 16) continue;
      const a = 0.32 * (1 - d / 16) ** 2;
      if (a > 0.01) setPx(img, x, y, alpha(T.ink, a));
    }
  return img;
}

/** A tiling felt 64² per theme: base + deterministic two-tone speckle (no visible repeat at 1×). */
export const FELTS = { village: '#1E5E3A', bastion: '#6A1E22', end: '#2E2050' };
export function felt(base, seed) {
  const img = image(64, 64);
  let s = seed >>> 0;
  const r = () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
  rect(img, 0, 0, 63, 63, base);
  for (let i = 0; i < 520; i++) {
    const x = (r() * 64) | 0;
    const y = (r() * 64) | 0;
    const v = r();
    setPx(img, x, y, v < 0.6 ? shade(base, 0.86) : v < 0.9 ? mix(base, '#FFFFFF', 0.06) : shade(base, 0.76));
  }
  return img;
}

// ---- glyphs (12² and 16²; no letters except the universal '?') -------------------------------------------------------

const G12 = {
  undo: ['............', '...#........', '..##........', '.#########..', '##########..', '.##......##.', '..#.......#.', '..........#.', '.........##.', '...######...', '...#####....', '............'],
  clear: ['............', '.##......##.', '.###....###.', '..###..###..', '...######...', '....####....', '....####....', '...######...', '..###..###..', '.###....###.', '.##......##.', '............'],
  rebet: ['............', '...#####.#..', '..#.....##..', '.#.....###..', '.#..........', '.#..........', '.#........#.', '.#........#.', '..#......#..', '...######...', '............', '............'],
  double: ['............', '..######....', '.##....##...', '.......##...', '......##....', '.....##.....', '....##..#.#.', '...##....#..', '..##....#.#.', '.########...', '............', '............'],
  rules: ['............', '....####....', '...##..##...', '.......##...', '......##....', '.....##.....', '.....##.....', '............', '.....##.....', '.....##.....', '............', '............'],
  settings: ['............', '.....##.....', '..#.####.#..', '..########..', '..###..###..', '.####..####.', '.####..####.', '..###..###..', '..########..', '..#.####.#..', '.....##.....', '............'],
  leave: ['............', '.#######....', '.#.....#....', '.#.....#.#..', '.#.....#.##.', '.#..########', '.#..########', '.#.....#.##.', '.#.....#.#..', '.#.....#....', '.#######....', '............'],
};

/** An icon glyph at 12² or 16² (the 12-px art, nearest-neighbour), white with an ink shadow. */
export function glyph(name, size) {
  const g = G12[name];
  const img = image(size, size);
  const k = size / 12;
  for (let y = 0; y < size; y++)
    for (let x = 0; x < size; x++) {
      const gx = Math.min(11, Math.floor(x / k));
      const gy = Math.min(11, Math.floor(y / k));
      if (g[gy][gx] === '#') setPx(img, x, y, T.text);
    }
  // 1 px ink shadow down-right
  const out = image(size, size);
  for (let y = 0; y < size; y++)
    for (let x = 0; x < size; x++) {
      const o = (y * size + x) * 4;
      if (img.data[o + 3]) setPx(out, x, y, T.text);
      else if (x > 0 && y > 0 && img.data[((y - 1) * size + (x - 1)) * 4 + 3]) setPx(out, x, y, alpha(T.ink, 0.8));
    }
  return out;
}
export const GLYPHS = Object.keys(G12);
