// Card faces, backs, the index font and the in-world face atlas (visual/cards.md §3).
//   L 37 × 49 — the viewer's hands, the board, the dealer, baccarat hands (standard pip layouts, double-headed courts)
//   M 21 × 29 — other seats (index + one big pip / court emblem)
//   S 13 × 17 — compact mode, side pots, the discard tray top card
// No words in any texture: the rank index is runtime text in the `burmaldaholic:core/card_index` font (drawn
// from `gui.burmaldaholic.card.rank.*`, RU Т/К/Д/В); faces reserve its corner. The world atlas (BER) uses
// language-neutral indices: digits 2–10, court emblems, a big pip for the ace.
import { B, C, P, R, box, fromRows, grid, image, mixHex, over, rrect } from './px.mjs';
import { PAPER, SUITS_2C, SUITS_4C, SUIT_ORDER } from './theme.mjs';
import { flipV, pip } from './suits.mjs';

export const RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
export const SIZES = { l: [37, 49], m: [21, 29], s: [13, 17], w: [16, 22] };

// ---- index font (runtime text; white glyphs, tinted by the draw colour) ----------------------------------------
export const INDEX_GLYPHS = {
  0: ['.##.', '#..#', '#..#', '#..#', '#..#', '.##.'],
  1: ['#', '#', '#', '#', '#', '#'],
  2: ['###.', '...#', '..#.', '.#..', '#...', '####'],
  3: ['###.', '...#', '.##.', '...#', '...#', '###.'],
  4: ['#..#', '#..#', '####', '...#', '...#', '...#'],
  5: ['####', '#...', '###.', '...#', '...#', '###.'],
  6: ['.##.', '#...', '###.', '#..#', '#..#', '.##.'],
  7: ['####', '...#', '..#.', '..#.', '.#..', '.#..'],
  8: ['.##.', '#..#', '.##.', '#..#', '#..#', '.##.'],
  9: ['.##.', '#..#', '#..#', '.###', '...#', '.##.'],
  A: ['.##.', '#..#', '#..#', '####', '#..#', '#..#'],
  J: ['..##', '...#', '...#', '...#', '#..#', '.##.'],
  Q: ['.##.', '#..#', '#..#', '#..#', '#.#.', '.#.#'],
  K: ['#..#', '#.#.', '##..', '##..', '#.#.', '#..#'],
  Т: ['###', '.#.', '.#.', '.#.', '.#.', '.#.'],
  В: ['###.', '#..#', '###.', '#..#', '#..#', '###.'],
  Д: ['.###.', '.#.#.', '.#.#.', '.#.#.', '#####', '#...#'],
  К: ['#..#', '#.#.', '##..', '##..', '#.#.', '#..#'],
  X: ['#..#', '#..#', '.##.', '.##.', '#..#', '#..#'],
};
/** Font sheet rows (8 × 8 cells, 16 per row); `chars` for the Java bitmap provider. */
export const INDEX_ROWS = ['0123456789AJQKX\u0000', 'ТВДК\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000'];
export function indexFontSheet() {
  const img = image(128, 16);
  INDEX_ROWS.forEach((row, r) =>
    [...row].forEach((ch, i) => {
      const g = INDEX_GLYPHS[ch];
      if (g) grid(img, g, { '#': '#FFFFFF' }, i * 8, r * 8 + 1);
    }),
  );
  return img;
}
export const indexFontProvider = () => ({
  providers: [{ type: 'bitmap', file: 'burmaldaholic:font/core/card_index.png', height: 8, ascent: 7, chars: INDEX_ROWS }],
});
/** Draws an index string with the glyph grids (world atlas + mockups use the same shapes as the runtime font). */
export function drawIndex(img, text, x, y, c) {
  let cx = x;
  for (const ch of text) {
    const g = INDEX_GLYPHS[ch];
    grid(img, g, { '#': c }, cx, y);
    cx += g[0].length + 1;
  }
  return cx - x - 1;
}

// ---- card body ---------------------------------------------------------------------------------------------
/** Paper body with ink outline, 2 px rounded corners, a top-left highlight and a bottom-right thickness shade. */
export function cardBody(w, h, paper = PAPER.face) {
  const img = image(w, h);
  rrect(img, 0, 0, w, h, 2, PAPER.ink);
  rrect(img, 1, 1, w - 2, h - 2, 1, paper);
  R(img, 2, 1, w - 4, 1, PAPER.hi);
  R(img, 1, 2, 1, h - 4, PAPER.hi);
  R(img, 2, h - 2, w - 4, 1, PAPER.shade);
  R(img, w - 2, 2, 1, h - 4, PAPER.shade);
  return img;
}

// ---- L pip layouts (pip 7 × 7; columns at x 8 / 15 / 22; rows by top edge; f = upside down) --------------------
const COL = { l: 8, m: 15, r: 22 };
const LAYOUT = {
  2: [['m', 5], ['m', 37, 1]],
  3: [['m', 5], ['m', 21], ['m', 37, 1]],
  4: [['l', 5], ['r', 5], ['l', 37, 1], ['r', 37, 1]],
  5: [['l', 5], ['r', 5], ['m', 21], ['l', 37, 1], ['r', 37, 1]],
  6: [['l', 5], ['r', 5], ['l', 21], ['r', 21], ['l', 37, 1], ['r', 37, 1]],
  7: [['l', 5], ['r', 5], ['m', 13], ['l', 21], ['r', 21], ['l', 37, 1], ['r', 37, 1]],
  8: [['l', 5], ['r', 5], ['m', 13], ['l', 21], ['r', 21], ['m', 29, 1], ['l', 37, 1], ['r', 37, 1]],
  9: [['l', 5], ['r', 5], ['l', 15], ['r', 15], ['m', 21], ['l', 27, 1], ['r', 27, 1], ['l', 37, 1], ['r', 37, 1]],
  10: [['l', 5], ['r', 5], ['m', 10], ['l', 15], ['r', 15], ['l', 27, 1], ['r', 27, 1], ['m', 32, 1], ['l', 37, 1], ['r', 37, 1]],
};

function suitCorner(img, suit, colours, w, h) {
  const s = pip(suit, 5, colours, { flat: true });
  over(img, s, 2, 10);
  over(img, flipV(s), w - 7, h - 15);
}

// ---- court portraits (15 × 17 bust, drawn twice: upright + mirrored = double-headed) ----------------------------
// legend: g gold, G gold shade, j jewel (suit light), s skin, S skin shade, i ink, h hair, H hair shade, w white fur /
// beard, W fur shade, r robe (suit), R robe dark, m mouth, f feather, F feather shade, t tiara pearl
const BUSTS = {
  K: [
    '...g...g...g...',
    '...gg.gjg.gg...',
    '...ggggggggg...',
    '...gGgGgGgGg...',
    '..hhhhhhhhhhh..',
    '..hsssssssssh..',
    '..hssisssissh..',
    '..hsssssSsssh..',
    '..hwssssssswh..',
    '..wwwsmmmswww..',
    '..wwwwwwwwwww..',
    '.rrwwwwwwwwwrr.',
    'rrrgwwwWwwwgrrr',
    'rRrrgwwwwwgrrRr',
    'rRrrrgwWwgrrrRr',
    'rRrrrrgjgrrrrRr',
    'RRrrrrrgrrrrrRR',
  ],
  Q: [
    '......t.t......',
    '.....gjgjg.....',
    '....ggggggg....',
    '...hhhhhhhhh...',
    '..hhsssssssHh..',
    '..hsssssssssh..',
    '..hssisssissh..',
    '.hhsssssSssshh.',
    '.hhsssssssssHh.',
    '.hHhssmmmsshHh.',
    '.hHhhsssssHhHh.',
    '.hrhhhsssHhhrh.',
    'rrrrjgwwwgjrrrr',
    'rRrrrgwwwgrrrRr',
    'rRrrrrgggrrrrRr',
    'rRrrrrrjrrrrrRr',
    'RRrrrrrrrrrrrRR',
  ],
  J: [
    '...........ff..',
    '..........fFf..',
    '...rrrrrrrfF...',
    '..rrrrrrrrRr...',
    '..gggggggggg...',
    '..hhsssssssh...',
    '..hssisssissh..',
    '..hsssssSsssh..',
    '..hssssssssh...',
    '...ssmmmmsss...',
    '....sssssss....',
    '...ggsssssgg...',
    '.rrrgwwwwwgrrr.',
    'rrRrgwwWwwgrRrr',
    'rRrrrgwwwgrrrRr',
    'rRrrrrgjgrrrrRr',
    'RRrrrrrgrrrrrRR',
  ],
};
const HAIR = { K: ['#6A4028', '#4A2A18'], Q: ['#E0A030', '#A86A18'], J: ['#2A1E28', '#140E18'] };

function bustLegend(rank, sc) {
  const [h, H] = HAIR[rank];
  return {
    g: '#FFD640', G: '#B07010', j: sc.light, s: '#F0C49A', S: '#C8906A', i: PAPER.ink, h, H, w: '#F4ECF8', W: '#C0B0DC', r: sc.base, R: sc.dark, m: '#9A3A3A',
    f: '#F4ECF8', F: '#C0B0DC', t: '#FFFFFF',
  };
}

/** A 21 × 39 double-headed portrait panel (gold frame, hatched suit-tint ground, split line in the suit colour). */
function courtPanel(rank, suit, sc) {
  const img = image(21, 39);
  const ground = mixHex(sc.light, '#FBF6EA', 0.8);
  const hatch = mixHex(sc.light, '#FBF6EA', 0.55);
  for (let y = 0; y < 39; y++) for (let x = 0; x < 21; x++) P(img, x, y, (x + y) % 4 === 0 ? hatch : ground);
  const bust = fromRows(BUSTS[rank], bustLegend(rank, sc));
  over(img, bust, 3, 2);
  over(img, flipV(bust), 3, 20);
  R(img, 1, 19, 19, 1, sc.dark);
  box(img, 0, 0, 21, 39, '#B07010');
  box(img, 1, 1, 19, 37, '#FFD640', 0.5);
  // suit pips in the free corners (upper-left and its point mirror) read the suit at a glance
  const s = pip(suit, 5, sc, { flat: true });
  over(img, s, 2, 13);
  over(img, flipV(s), 14, 21);
  return img;
}

/** Court emblems for M cards and the world atlas: crown (K), flower tiara (Q), feather (J). 9 × 9, suit coloured. */
const EMBLEMS = {
  K: ['.........', 'g...g...g', 'gg.gjg.gg', 'ggggggggg', 'gjgggggjg', 'ggggggggg', 'GGGGGGGGG', '.........', '.........'],
  Q: ['....j....', '...jjj...', '.j..j..j.', 'jjj.g.jjj', '.j.ggg.j.', '..ggggg..', '.gjgggjg.', '.GGGGGGG.', '.........'],
  J: ['......ff.', '.....fFf.', '....fFf..', '...fFf...', '..fFf....', '.fFf.....', '.Ff......', 'g........', '.........'],
};
export function emblem(rank, sc) {
  return fromRows(EMBLEMS[rank], { g: sc.base, G: sc.dark, j: sc.light, f: sc.base, F: sc.dark });
}

/** Ace: one large pip (17 px) inside a gold flourish ring. */
function acePip(suit, sc) {
  const img = image(25, 25);
  for (let a = 0; a < 24; a++) {
    const t = (a / 24) * Math.PI * 2;
    const x = Math.round(12 + Math.cos(t) * 11.5);
    const y = Math.round(12 + Math.sin(t) * 11.5);
    P(img, x, y, a % 2 ? '#FFD640' : '#B07010');
  }
  over(img, pip(suit, 17, sc), 4, 4);
  return img;
}

// ---- faces -----------------------------------------------------------------------------------------------------
export function faceL(rank, suit, colours) {
  const sc = colours[suit];
  const [w, h] = SIZES.l;
  const img = cardBody(w, h);
  suitCorner(img, suit, sc, w, h);
  if (rank === 'A') over(img, acePip(suit, sc), 4, 11);
  else if (LAYOUT[rank]) {
    const p = pip(suit, 7, sc);
    const pf = flipV(p);
    for (const [c, y, f] of LAYOUT[rank]) over(img, f ? pf : p, COL[c], y);
  } else over(img, courtPanel(rank, suit, sc), 8, 5);
  return img;
}

export function faceM(rank, suit, colours) {
  const sc = colours[suit];
  const [w, h] = SIZES.m;
  const img = cardBody(w, h);
  over(img, pip(suit, 5, sc, { flat: true }), 2, 10);
  if (rank === 'J' || rank === 'Q' || rank === 'K') {
    R(img, 8, 13, 11, 13, mixHex(sc.light, '#FBF6EA', 0.78));
    box(img, 8, 13, 11, 13, '#B07010');
    over(img, emblem(rank, sc), 9, 15);
  } else over(img, pip(suit, 11, sc), 8, 14);
  return img;
}

export function faceS(rank, suit, colours) {
  const sc = colours[suit];
  const [w, h] = SIZES.s;
  const img = cardBody(w, h);
  over(img, pip(suit, 5, sc, { flat: true }), 6, 10);
  void rank;
  return img;
}

/** World (BER) face 16 × 22 with language-neutral indices. */
export function faceW(rank, suit, colours) {
  const sc = colours[suit];
  const [w, h] = SIZES.w;
  const img = cardBody(w, h);
  if (rank === 'A') {
    over(img, pip(suit, 11, sc), 2, 5);
    return img;
  }
  if (rank === 'J' || rank === 'Q' || rank === 'K') over(img, emblem(rank, sc), 2, 2);
  else drawIndex(img, rank, 2, 2, sc.base);
  over(img, pip(suit, 5, sc, { flat: true }), 2, 10);
  over(img, pip(suit, 7, sc), 7, 12);
  return img;
}

/** Atlas: rows = suits (♠ ♥ ♦ ♣), columns = A, 2 … 10, J, Q, K. */
export function faceAtlas(size, fourColour = true) {
  const colours = fourColour ? SUITS_4C : SUITS_2C;
  const [w, h] = SIZES[size];
  const draw = { l: faceL, m: faceM, s: faceS, w: faceW }[size];
  const img = image(w * 13, h * 4);
  SUIT_ORDER.forEach((suit, r) => RANKS.forEach((rank, c) => over(img, draw(rank, suit, colours), c * w, r * h)));
  return img;
}

// ---- backs -----------------------------------------------------------------------------------------------------
export const BACK_ORDER = ['navy', 'burgundy', 'crimson', 'emerald', 'bastion', 'end'];
const BACKS = {
  navy: { base: '#1E2A6E', line: '#3A52B0', dot: '#FFD640', pattern: 'lattice', emblem: 'diamond' },
  burgundy: { base: '#5E1224', line: '#8E2A40', dot: '#FFD640', pattern: 'damask', emblem: 'diamond' },
  crimson: { base: '#A01C2A', line: '#D0404A', dot: '#FFE9A0', pattern: 'diamonds', emblem: 'diamond' },
  emerald: { base: '#0C5038', line: '#1E7A56', dot: '#FFD640', pattern: 'check', emblem: 'diamond' },
  bastion: { base: '#1C1418', line: '#3A2E34', dot: '#FFB020', pattern: 'zigzag', emblem: 'snout' },
  end: { base: '#2E1650', line: '#5A3486', dot: '#E8E4A8', pattern: 'tiles', emblem: 'eye' },
};
const EMBLEM_BACK = {
  diamond: ['...i...', '..igi..', '.igGgi.', 'igGYGgi', '.igGgi.', '..igi..', '...i...'],
  snout: ['.iiiii.', 'iPPPPPi', 'iPkPkPi', 'iPkPkPi', 'iPPPPPi', '.iiiii.', '.......'],
  eye: ['..iii..', '.iEEEi.', 'iEeKeEi', 'iEKKKEi', 'iEeKeEi', '.iEEEi.', '..iii..'],
};
function backPattern(img, x0, y0, w, h, d) {
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const X = x + x0;
      const Y = y + y0;
      let on = false;
      let dot = false;
      if (d.pattern === 'lattice') {
        on = (x + y) % 4 === 0 || (x - y + 64) % 4 === 0;
        dot = (x + y) % 8 === 0 && (x - y + 64) % 8 === 0;
      } else if (d.pattern === 'damask') {
        const u = x % 6;
        const v = y % 6;
        on = (u === 2 || u === 3) && v !== 5 ? v < 4 && Math.abs(u - 2.5) + Math.abs(v - 1.5) < 2.2 : false;
        dot = u === 5 && v === 5;
      } else if (d.pattern === 'diamonds') {
        on = (Math.abs((x % 6) - 2.5) + Math.abs((y % 8) - 3.5)) < 3;
        dot = x % 6 === 2 && y % 8 === 3;
      } else if (d.pattern === 'check') {
        on = (Math.floor(x / 3) + Math.floor(y / 3)) % 2 === 0;
        dot = x % 6 === 1 && y % 6 === 1;
      } else if (d.pattern === 'zigzag') {
        const z = (y + Math.abs((x % 8) - 4)) % 6;
        on = z === 0 || z === 1;
        dot = z === 0 && x % 8 === 4;
      } else if (d.pattern === 'tiles') {
        on = x % 5 === 0 || y % 5 === 0;
        dot = x % 10 === 7 && y % 10 === 7;
      }
      P(img, X, Y, dot ? d.dot : on ? d.line : d.base);
    }
}
/** A back of size w × h ('l' | 'm' | 's' | 'w'). */
export function back(design, size) {
  const d = BACKS[design];
  const [w, h] = SIZES[size];
  const img = cardBody(w, h);
  const m = size === 'l' ? 3 : 2;
  R(img, m, m, w - 2 * m, h - 2 * m, d.base);
  backPattern(img, m + 1, m + 1, w - 2 * m - 2, h - 2 * m - 2, d);
  box(img, m, m, w - 2 * m, h - 2 * m, '#FFD640');
  if (size === 'l' || size === 'm' || size === 'w') {
    const e = fromRows(EMBLEM_BACK[d.emblem], {
      i: PAPER.ink, g: '#FFD640', G: '#B07010', Y: '#FFF4B0', P: '#F0A0A0', k: '#8C3A4A', E: '#1E8A7A', e: '#7AE8C8', K: '#0A2A24',
    });
    const cx = (w - 7) >> 1;
    const cy = (h - 7) >> 1;
    R(img, cx - 1, cy - 1, 9, 9, d.base);
    over(img, e, cx, cy);
  }
  return img;
}
/** Backs atlas: one row per design, columns L | M | S | W. */
export function backAtlas() {
  const cols = ['l', 'm', 's', 'w'];
  const cw = cols.reduce((s, c) => s + SIZES[c][0], 0);
  const img = image(cw, SIZES.l[1] * BACK_ORDER.length);
  BACK_ORDER.forEach((d, r) => {
    let x = 0;
    for (const c of cols) {
      over(img, back(d, c), x, r * SIZES.l[1]);
      x += SIZES[c][0];
    }
  });
  return img;
}
export const BACK_COLUMNS = { l: 0, m: SIZES.l[0], s: SIZES.l[0] + SIZES.m[0], w: SIZES.l[0] + SIZES.m[0] + SIZES.s[0] };

/** World atlas 256² for the BER: 13 × 4 faces (16 × 22) at the top, then the 6 backs, a blank face, a card edge. */
export function worldAtlas() {
  const img = image(256, 256);
  over(img, faceAtlas('w', true), 0, 0);
  BACK_ORDER.forEach((d, i) => over(img, back(d, 'w'), i * 16, 88));
  over(img, cardBody(16, 22), 96, 88);
  R(img, 112, 88, 16, 1, PAPER.edge);
  B(img, 112, 89, PAPER.ink, 0.5);
  return img;
}

export { C };
