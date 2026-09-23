#!/usr/bin/env node
/* global console */
// Generate the shared glyph font sheet packs/core/RP/font/glyph_E1.png (UI.md §0.1):
// 256×256 = 16×16 cells of 16×16 px; the glyph for U+E1RC sits in row R, column C.
// Code points: see src/core/logic/glyphs.ts. Deterministic.
//   node tools/gen-glyphs.mjs
import { Buffer } from 'node:buffer';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'packs/core/RP/font/glyph_E1.png');

// ---- tiny PNG encoder (RGBA8) ---------------------------------------------------------
const CRC = new Int32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});
function crc32(buf) {
  let c = -1;
  for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encodePng(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

// ---- sheet + drawing ---------------------------------------------------------------------
const SIZE = 256;
const sheet = Buffer.alloc(SIZE * SIZE * 4);
const hex = (s) => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), 255];

/** Drawing context for the cell of code point `cp` (0xE1RC). */
function cell(cp) {
  const low = cp & 0xff;
  const ox = (low & 0x0f) * 16;
  const oy = (low >> 4) * 16;
  const set = (x, y, c) => {
    if (x < 0 || y < 0 || x > 15 || y > 15) return;
    sheet.set(c, ((oy + y) * SIZE + ox + x) * 4);
  };
  const rect = (x0, y0, x1, y1, c) => {
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, c);
  };
  const bitmap = (x0, y0, rows, c) => rows.forEach((r, dy) => [...r].forEach((ch, dx) => ch === '#' && set(x0 + dx, y0 + dy, c)));
  return { set, rect, bitmap };
}

const FONT = {
  A: ['.#.', '#.#', '###', '#.#', '#.#'],
  K: ['#.#', '#.#', '##.', '#.#', '#.#'],
  Q: ['.#.', '#.#', '#.#', '#.#', '.##'],
  J: ['..#', '..#', '..#', '#.#', '.#.'],
  2: ['##.', '..#', '.#.', '#..', '###'],
  3: ['##.', '..#', '.#.', '..#', '##.'],
  4: ['#.#', '#.#', '###', '..#', '..#'],
  5: ['###', '#..', '##.', '..#', '##.'],
  6: ['.##', '#..', '###', '#.#', '###'],
  7: ['###', '..#', '.#.', '.#.', '.#.'],
  8: ['###', '#.#', '###', '#.#', '###'],
  9: ['###', '#.#', '###', '..#', '##.'],
  1: ['.#', '##', '.#', '.#', '.#'],
  0: ['###', '#.#', '#.#', '#.#', '###'],
};
const SUIT_SHAPES = {
  S: ['...#...', '..###..', '.#####.', '#######', '#######', '...#...', '..###..'],
  H: ['.##.##.', '#######', '#######', '#######', '.#####.', '..###..', '...#...'],
  D: ['...#...', '..###..', '.#####.', '#######', '.#####.', '..###..', '...#...'],
  C: ['..###..', '..###..', '#######', '#######', '##.#.##', '...#...', '..###..'],
};
const SUITS = ['S', 'H', 'D', 'C'];
const RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A'];
const RED = hex('#d32f2f');
const BLACK = hex('#1b1b1b');
const WHITE = hex('#f7f7f2');
const EDGE = hex('#9e9e9e');
const suitColor = (s) => (s === 'H' || s === 'D' ? RED : BLACK);

function cardBody(c, face = WHITE, edge = EDGE) {
  c.rect(2, 0, 13, 15, face);
  for (let x = 3; x <= 12; x++) {
    c.set(x, 0, edge);
    c.set(x, 15, edge);
  }
  for (let y = 1; y <= 14; y++) {
    c.set(2, y, edge);
    c.set(13, y, edge);
  }
  // rounded corners
  for (const [x, y] of [[2, 0], [13, 0], [2, 15], [13, 15]]) c.set(x, y, [0, 0, 0, 0]);
}

function drawRank(c, rank, col) {
  if (rank === '10') {
    c.bitmap(3, 2, FONT[1], col);
    c.bitmap(6, 2, FONT[0], col);
  } else c.bitmap(4, 2, FONT[rank], col);
}

// U+E100 chip
{
  const c = cell(0xe100);
  const base = hex('#c62828');
  const stripe = hex('#f5f5f5');
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x - 7.5, y - 7.5);
      if (d > 7.5) continue;
      const ang = Math.atan2(y - 7.5, x - 7.5);
      const onStripe = d > 5 && Math.floor(((ang + Math.PI) / (2 * Math.PI)) * 8) % 2 === 0;
      c.set(x, y, d > 6.8 ? hex('#7f1717') : onStripe ? stripe : d < 3.2 ? hex('#e57373') : base);
    }
}

// U+E110–U+E143 cards, U+E144 back
SUITS.forEach((s, si) =>
  RANKS.forEach((r, ri) => {
    const c = cell(0xe110 + si * 13 + ri);
    cardBody(c);
    drawRank(c, r, suitColor(s));
    c.bitmap(5, 8, SUIT_SHAPES[s], suitColor(s));
  }),
);
{
  const c = cell(0xe144);
  cardBody(c, hex('#8e1b1b'), hex('#5a0f0f'));
  for (let y = 2; y <= 13; y++) for (let x = 4; x <= 11; x++) if ((x + y) % 3 === 0) c.set(x, y, hex('#d9a441'));
}

// U+E150–U+E153 suits
SUITS.forEach((s, i) => cell(0xe150 + i).bitmap(4, 4, SUIT_SHAPES[s], s === 'H' || s === 'D' ? RED : hex('#e0e0e0')));

// U+E160–U+E165 dice
const PIPS = {
  1: [[7, 7]],
  2: [[4, 4], [10, 10]],
  3: [[4, 4], [7, 7], [10, 10]],
  4: [[4, 4], [10, 4], [4, 10], [10, 10]],
  5: [[4, 4], [10, 4], [7, 7], [4, 10], [10, 10]],
  6: [[4, 3], [10, 3], [4, 7], [10, 7], [4, 11], [10, 11]],
};
for (let n = 1; n <= 6; n++) {
  const c = cell(0xe160 + n - 1);
  c.rect(1, 1, 14, 14, WHITE);
  for (let i = 2; i <= 13; i++) {
    c.set(i, 1, EDGE);
    c.set(i, 14, EDGE);
    c.set(1, i, EDGE);
    c.set(14, i, EDGE);
  }
  for (const [x, y] of PIPS[n]) c.rect(x, y, x + 1, y + 1, n === 1 ? RED : BLACK);
}

// U+E170 flame, U+E171 rain cloud
cell(0xe170).bitmap(3, 1, ['....#.....', '...##.....', '...###..#.', '..####.##.', '..#######.', '.#########', '.####*####', '##########', '####**####', '###****###', '###****###', '.##****##.', '..######..', '...####...'].map((r) => r.replace(/\*/g, '.')), hex('#ff7a1a'));
cell(0xe170).bitmap(3, 1, ['..........', '..........', '..........', '..........', '..........', '..........', '......#...', '.....##...', '....####..', '...######.', '...######.', '...######.', '....####..', '..........'], hex('#ffd54f'));
cell(0xe171).bitmap(1, 2, ['....####......', '..########....', '.###########..', '##############', '##############', '.############.'], hex('#b0bec5'));
cell(0xe171).bitmap(2, 9, ['#...#...#...', '............', '..#...#...#.', '............', '#...#...#...'], hex('#4fc3f7'));

// U+E180–U+E185 VIP badges (tier colors of UI.md: §c §7 §6 §f §b §5)
['#e0714f', '#aaaaaa', '#ffaa00', '#ffffff', '#55ffff', '#aa00aa'].forEach((col, tier) => {
  const c = cell(0xe180 + tier);
  const base = hex(col);
  const dark = base.map((v, i) => (i < 3 ? Math.round(v * 0.6) : v));
  c.bitmap(2, 2, ['....####....', '...######...', '..########..', '.##########.', '############', '.##########.', '..########..', '...######...', '....####....', '.....##.....'], base);
  c.bitmap(2, 2, ['............', '............', '............', '............', '......######', '.....#####..', '......###...', '......##....', '.....##.....', '............'], dark);
});

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, encodePng(SIZE, SIZE, sheet));
console.info(`wrote ${path.relative(ROOT, OUT)}`);
