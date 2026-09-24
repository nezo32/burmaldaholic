// Glyph sheet E1 (docs/architecture/animation.md §6; UI.md §0.1; global.md §2.3; cards.md §4; tables.md §4.2;
// extras-pvp.md §10.5; PVP.md; BOTS.md §7.1). One 256² PNG, 16 px cells, shared by both editions:
// Bedrock `packs/core/RP/font/glyph_E1.png`, Java `textures/font/core/glyph_e1.png` (bitmap provider, lane J-L1).
// Cells keep a 1 px transparent margin (no bleed at title scale). Icons are drawn as 7 × 7 grids at 2× so they
// stay crisp when Java renders the sheet at height 8 (one texel per GUI pixel).
// The pre-wave glyphs (chip, cards, suits, dice, flame, cloud, VIP badges) are ported unchanged from the old
// tools/gen-glyphs.mjs.
import { DYES, CHIPS } from '../../lib/palette.mjs';
import { glyphPage } from '../../lib/font.mjs';
import { drawGrid, ellipse, frame, image, line, rect, setPx } from '../../lib/grid.mjs';
import { outline, rotate } from '../../lib/transforms.mjs';

const RED = '#D32F2F';
const BLACK = '#1B1B1B';
const WHITE = '#F7F7F2';
const EDGE = '#9E9E9E';

/** Old-API painter on a cell canvas (set / rect / bitmap with '#' pixels). */
function painter(page, cp) {
  const cv = page.canvas(cp);
  const set = (x, y, c) => setPx(cv.img, x, y, c);
  return {
    set,
    clear: (x, y) => cv.img.data.fill(0, (y * 16 + x) * 4, (y * 16 + x) * 4 + 4),
    rect: (x0, y0, x1, y1, c) => rect(cv.img, x0, y0, x1, y1, c),
    bitmap: (x0, y0, rows, c) => rows.forEach((r, dy) => [...r].forEach((ch, dx) => ch === '#' && set(x0 + dx, y0 + dy, c))),
    img: cv.img,
    commit: cv.commit,
  };
}

// ---- pre-wave glyphs (ported from tools/gen-glyphs.mjs) -----------------------------------------------
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
const suitColor = (s) => (s === 'H' || s === 'D' ? RED : BLACK);

function cardBody(c, face = WHITE, edge = EDGE, x0 = 2, x1 = 13) {
  c.rect(x0, 0, x1, 15, face);
  for (let x = x0 + 1; x <= x1 - 1; x++) {
    c.set(x, 0, edge);
    c.set(x, 15, edge);
  }
  for (let y = 1; y <= 14; y++) {
    c.set(x0, y, edge);
    c.set(x1, y, edge);
  }
  for (const [x, y] of [[x0, 0], [x1, 0], [x0, 15], [x1, 15]]) c.clear(x, y);
}
const BACK = '#8E1B1B';
const BACK_EDGE = '#5A0F0F';
const BACK_PATTERN = '#D9A441';
function backPattern(c, x0 = 4, x1 = 11, y0 = 2, y1 = 13) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) if ((x + y) % 3 === 0) c.set(x, y, BACK_PATTERN);
}

function drawRank(c, rank, col) {
  if (rank === '10') {
    c.bitmap(3, 2, FONT[1], col);
    c.bitmap(6, 2, FONT[0], col);
  } else c.bitmap(4, 2, FONT[rank], col);
}

function preWave(page) {
  {
    const c = painter(page, 0xe100);
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const d = Math.hypot(x - 7.5, y - 7.5);
        if (d > 7.5) continue;
        const ang = Math.atan2(y - 7.5, x - 7.5);
        const onStripe = d > 5 && Math.floor(((ang + Math.PI) / (2 * Math.PI)) * 8) % 2 === 0;
        c.set(x, y, d > 6.8 ? '#7F1717' : onStripe ? '#F5F5F5' : d < 3.2 ? '#E57373' : '#C62828');
      }
    c.commit();
  }
  SUITS.forEach((s, si) =>
    RANKS.forEach((r, ri) => {
      const c = painter(page, 0xe110 + si * 13 + ri);
      cardBody(c);
      drawRank(c, r, suitColor(s));
      c.bitmap(5, 8, SUIT_SHAPES[s], suitColor(s));
      c.commit();
    }),
  );
  {
    const c = painter(page, 0xe144);
    cardBody(c, BACK, BACK_EDGE);
    backPattern(c);
    c.commit();
  }
  SUITS.forEach((s, i) => {
    const c = painter(page, 0xe150 + i);
    c.bitmap(4, 4, SUIT_SHAPES[s], s === 'H' || s === 'D' ? RED : '#E0E0E0');
    c.commit();
  });
  const PIPS = {
    1: [[7, 7]],
    2: [[4, 4], [10, 10]],
    3: [[4, 4], [7, 7], [10, 10]],
    4: [[4, 4], [10, 4], [4, 10], [10, 10]],
    5: [[4, 4], [10, 4], [7, 7], [4, 10], [10, 10]],
    6: [[4, 3], [10, 3], [4, 7], [10, 7], [4, 11], [10, 11]],
  };
  for (let n = 1; n <= 6; n++) {
    const c = painter(page, 0xe160 + n - 1);
    c.rect(1, 1, 14, 14, WHITE);
    for (let i = 2; i <= 13; i++) {
      c.set(i, 1, EDGE);
      c.set(i, 14, EDGE);
      c.set(1, i, EDGE);
      c.set(14, i, EDGE);
    }
    for (const [x, y] of PIPS[n]) c.rect(x, y, x + 1, y + 1, n === 1 ? RED : BLACK);
    c.commit();
  }
  {
    const c = painter(page, 0xe170);
    c.bitmap(3, 1, ['....#.....', '...##.....', '...###..#.', '..####.##.', '..#######.', '.#########', '.####.####', '##########', '####..####', '###....###', '###....###', '.##....##.', '..######..', '...####...'], '#FF7A1A');
    c.bitmap(3, 1, ['..........', '..........', '..........', '..........', '..........', '..........', '......#...', '.....##...', '....####..', '...######.', '...######.', '...######.', '....####..', '..........'], '#FFD54F');
    c.commit();
  }
  {
    const c = painter(page, 0xe171);
    c.bitmap(1, 2, ['....####......', '..########....', '.###########..', '##############', '##############', '.############.'], '#B0BEC5');
    c.bitmap(2, 9, ['#...#...#...', '............', '..#...#...#.', '............', '#...#...#...'], '#4FC3F7');
    c.commit();
  }
  ['#E0714F', '#AAAAAA', '#FFAA00', '#FFFFFF', '#55FFFF', '#AA00AA'].forEach((col, tier) => {
    const c = painter(page, 0xe180 + tier);
    const dark = '#' + [1, 3, 5].map((i) => Math.round(parseInt(col.slice(i, i + 2), 16) * 0.6).toString(16).padStart(2, '0')).join('');
    c.bitmap(2, 2, ['....####....', '...######...', '..########..', '.##########.', '############', '.##########.', '..########..', '...######...', '....####....', '.....##.....'], col);
    c.bitmap(2, 2, ['............', '............', '............', '............', '......######', '.....#####..', '......###...', '......##....', '.....##.....', '............'], dark);
    c.commit();
  });
}

// ---- animation-wave glyphs -----------------------------------------------------------------------------

/** 7 × 7 icon grids (drawn at 2× with a 1 px margin). Shared legends below. */
const L = {
  // gold / ingots
  Y: 'gold', o: 'gold.shade', w: '#FFF3A0',
  // iron
  I: '#D8D8D8', i: '#8C8C8C', W: '#FFFFFF',
  // emerald
  d: '#0B7A3A', G: '#17DD62', g: '#9CFFC0',
  // diamond
  c: '#1AA3A3', C: '#4AEDD9', l: '#D1FFF8',
  // bone / ink
  b: 'bone', B: 'bone.shade', k: 'ink',
  // reds
  R: 'chip.red', r: 'chip.light', D: 'chip.dark',
  // lilac
  p: 'lilac', P: 'glint',
};

const G7 = {
  sun: ['Y..Y..Y', '.oYYYo.', '.YYwYY.', 'YYwwYYY', '.YYYYY.', '.oYYYo.', 'Y..Y..Y'],
  bell: ['...R...', '..RRR..', '.RRrRR.', '.RRrRR.', '.RRRRR.', 'RRRRRRR', '...D...'],
  fist: ['.ssss..', 'ssssss.', 'sksksks', 'sssssss', 'ssssssk', '.sssss.', '..RRR..'],
  bot: ['.ccccc.', 'cCCCCCc', 'cCeCeCc', 'cCCCCCc', 'cCgggCc', 'cCCCCCc', '.c...c.'],
  emerald: ['..ddd..', '.dGgGd.', 'dGGgGGd', 'dGGGGGd', 'dGGGGGd', '.dGGGd.', '..ddd..'],
  ingot: ['.......', '..wwww.', '.wYYYYo', 'wYYYYoo', 'ooooooo', '.......', '.......'],
  ironIngot: ['.......', '..WWWW.', '.WIIIIi', 'WIIIIii', 'iiiiiii', '.......', '.......'],
  sparkle: ['...p...', '...p...', '..pbp..', 'ppbbbpp', '..pbp..', '...p...', '...p...'],
  rabbitFoot: ['..tt...', '.tttt..', '.tTtt..', '..ttt..', '..ttt..', '..tTt..', '..YYY..'],
  charred: ['.hhhhh.', 'hHeHHHh', 'hHHHeHh', 'hhHHHhh', 'hHeHHHh', 'hHHHHHh', '.hhhhh.'],
  coal: ['..kkk..', '.kKkKk.', 'kKkkkKk', 'kkKkkkk', 'kKkkKkk', '.kkKkk.', '..kkk..'],
  diamond: ['.cCCCc.', 'cClllCc', 'CCCCCCC', '.cCCCc.', '..cCc..', '...c...', '.......'],
  netherStar: ['...W...', '..WnW..', 'nnWWWnn', '.nWWWn.', '.nW.Wn.', '.n...n.', '.......'],
  creeper: ['GGgGGGG', 'GkkGkkg', 'gkkGkkG', 'GGGkGGG', 'GgkkkGG', 'GGkGkGg', 'GGGGgGG'],
  dolly: ['..bbb..', '.bYYYb.', '..bbb..', '...b...', '..bbb..', '.bbbbb.', '.......'],
  pushRing: ['..BBB..', '.B...B.', 'B.....B', 'BkkkkkB', 'B.....B', '.B...B.', '..BBB..'],
  tick: ['.......', '......v', '.....vv', 'v...vv.', 'vv.vv..', '.vvv...', '..v....'],
  cross: ['R.....R', 'RR...RR', '.RR.RR.', '..RRR..', '.RR.RR.', 'RR...RR', 'R.....R'],
  bestHand: ['...Y...', '...Y...', '..YYY..', '..YYY..', '.YYYYY.', '.YYYYY.', 'ooooooo'],
};
const L7 = {
  ...L,
  s: '#E0A070', // skin
  e: '#5CE8E0',
  t: '#E8D8C0',
  T: '#B8A080',
  h: 'char',
  H: '#3A2A24',
  e2: 'ember',
  K: '#5A5A5A',
  n: '#C8C8E0',
  v: 'bonus',
};
// the copper bot and charred use their own letters
const BOT = { c: '#8A4B2A', C: '#C87A4A', e: '#5CE8E0', g: '#5A3020' };
const CHARRED = { h: 'char', H: '#3A2A24', e: 'ember' };
const COAL = { k: '#2A2A2A', K: '#5A5A5A' };
const STAR = { W: '#FFFFFF', n: '#C8C8E0' };
const CREEPER = { G: '#3FA535', g: '#5CC24C', k: '#0A2A0A' };

/** Top-down mini chip in denomination colours (7 × 7). */
const miniChip = (d) => ({
  rows: ['..sbs..', '.bbbbb.', 'sbSSSbs', 'bbSbSbb', 'sbSSSbs', '.bbbbb.', '..sbs..'],
  legend: { b: CHIPS[d].base, s: CHIPS[d].stripe, S: d === 1 ? '#C8C8C8' : CHIPS[d].stripe },
});

function waveGlyphs(page) {
  const g = (cp, rows, legend) => page.grid(cp, rows, { ...L7, ...legend });

  // U+E172–E174 streak pips (6 × 8, even-aligned so Java's half-size render stays crisp)
  const pip = (cp, border, fill, top) => {
    const c = page.canvas(cp);
    rect(c.img, 5, 4, 10, 11, fill);
    frame(c.img, 5, 4, 10, 11, border);
    if (top) rect(c.img, 6, 5, 9, 5, top);
    c.commit();
  };
  pip(0xe172, '#783CBE', '#2A1640');
  pip(0xe173, '#B07010', '#FFD640', '#FFF0A0');
  pip(0xe174, '#4A5A78', '#8FA8C8', '#C8D8F0');
  g(0xe175, G7.sun, {});
  g(0xe176, G7.bell, {});
  g(0xe177, G7.fist, {});

  // U+E186–E189 coin spin (face, ¾, edge, ¾ back), U+E18A heads, U+E18B tails
  const coin = (cp, rx, faceCol, rimCol, hi, emblem) => {
    const c = page.canvas(cp);
    ellipse(c.img, 8, 8, rx, 7, (x, y, d) => (d > 0.8 ? rimCol : faceCol));
    if (hi && rx > 3) {
      setPx(c.img, 8 - Math.round(rx / 2), 4, hi);
      setPx(c.img, 8 - Math.round(rx / 2), 5, hi);
    }
    if (emblem) drawGrid(c.img, emblem.rows, emblem.legend, 5, 5);
    c.commit();
  };
  coin(0xe186, 7, '#FFD640', '#B07010', '#FFF3A0');
  coin(0xe187, 5, '#F0C030', '#B07010', '#FFF3A0');
  coin(0xe188, 1.5, '#B07010', '#7A4A08');
  coin(0xe189, 5, '#D8A020', '#8A5A0C');
  const crown = { rows: ['o.o.o.', 'oooooo', 'oYoYoo', 'oooooo', '......', '......'], legend: { o: '#B07010', Y: '#FFF3A0' } };
  const skull = { rows: ['.oooo.', 'oYooYo', 'oooooo', '.o..o.', '.oooo.', '......'], legend: { o: '#8A5A0C', Y: '#1A0C00' } };
  coin(0xe18a, 7, '#FFD640', '#B07010', '#FFF3A0', crown);
  coin(0xe18b, 7, '#E8B830', '#8A5A0C', null, skull);

  g(0xe190, G7.bot, BOT);
  // U+E191–E193 thinking dots 1/2/3 (identical for every decision, global.md §6.8)
  for (let n = 1; n <= 3; n++) {
    const c = page.canvas(0xe190 + n);
    for (let i = 0; i < 3; i++) rect(c.img, 2 + i * 5, 10, 3 + i * 5, 11, i < n ? '#F4ECF8' : '#6A5A80');
    c.commit();
  }
  [1, 5, 25, 100, 500].forEach((d, i) => {
    const m = miniChip(d);
    page.put(0xe194 + i, outlineGrid(m.rows, m.legend));
  });
  g(0xe199, G7.emerald, {});
  g(0xe19a, G7.ingot, {});
  g(0xe19b, G7.sparkle, {});

  // PvP (PVP.md): rabbit's foot, charred
  g(0xe1a0, G7.rabbitFoot, {});
  g(0xe1a1, G7.charred, CHARRED);
  // extras (extras-pvp.md §10.5): scratch symbols, foil, chain pips, Plinko
  g(0xe1a2, G7.coal, COAL);
  g(0xe1a3, G7.ironIngot, {});
  g(0xe1a4, G7.diamond, {});
  g(0xe1a5, G7.netherStar, STAR);
  g(0xe1a6, G7.creeper, CREEPER);
  foil(page, 0xe1a7, false);
  foil(page, 0xe1a8, true);
  {
    const c = page.canvas(0xe1a9);
    ellipse(c.img, 8, 8, 4, 4, (x, y, d) => (d > 0.7 ? '#C0B0DC' : null));
    c.commit();
  }
  {
    const c = page.canvas(0xe1aa);
    ellipse(c.img, 8, 8, 5, 5, (x, y, d) => (d > 0.75 ? '#B07010' : d < 0.35 && x < 8 && y < 8 ? '#FFF3A0' : '#FFD640'));
    c.commit();
  }
  {
    const c = page.canvas(0xe1ab); // Plinko ball
    ellipse(c.img, 8, 8, 5, 5, (x, y, d) => (d > 0.8 ? '#C0B0DC' : x < 7 && y < 7 ? '#FFFFFF' : '#F4ECF8'));
    c.commit();
  }
  {
    const c = page.canvas(0xe1ac); // peg dot
    rect(c.img, 7, 7, 8, 8, '#C0B0DC');
    c.commit();
  }
  const lamp = (cp, rim, core, glow) => {
    const c = page.canvas(cp);
    rect(c.img, 3, 5, 12, 10, core);
    frame(c.img, 3, 5, 12, 10, rim);
    if (glow) rect(c.img, 5, 6, 7, 7, glow);
    c.commit();
  };
  lamp(0xe1ad, '#4A3060', '#2A1640');
  lamp(0xe1ae, '#D696FF', '#F4ECF8', '#FFFFFF');
  lamp(0xe1af, '#B07010', '#FFD640', '#FFF3A0');
  wheelIcon(page, 0xe1b0);
  // wheel segment icons B C H M D T E X (extras-pvp.md §3.2)
  const chip = { r: 'chip.red', w: 'bone', L: 'chip.light', k: 'ink' };
  const grey = { r: '#6A6A6A', w: '#B8B8B8', L: '#8A8A8A', k: '#1A1A1A' };
  g(0xe1b1, ['..rwr..', '.rrkrr.', 'wrLkLrw', 'rrkLLrr', 'wrLkLrw', '.rrrkr.', '..rwr..'], grey);
  g(0xe1b2, G7.creeper, CREEPER);
  g(0xe1b3, ['..rw...', '.rrr...', 'wrLL...', 'rrLL...', 'wrLL...', '.rrr...', '..rw...'], chip);
  g(0xe1b4, ['..rwr..', '.rrrrr.', 'wrLLLrw', 'rrLLLrr', 'wrLLLrw', '.rrrrr.', '..rwr..'], chip);
  g(0xe1b5, ['.......', '..rrr..', '.rLLLr.', '.wrwrw.', '.rLLLr.', '.wrwrw.', '.......'], chip);
  g(0xe1b6, ['..rrr..', '.rLLLr.', '.wrwrw.', '.rLLLr.', '.wrwrw.', '.rLLLr.', '.wrwrw.'], chip);
  g(0xe1b7, G7.emerald, {});
  g(0xe1b8, G7.diamond, {});

  // tables (tables.md §4.2): tumbling die ×4, pockets, ball, dolly, push, tick, cross
  for (let f = 0; f < 4; f++) {
    const die = image(16, 16);
    rect(die, 4, 4, 11, 11, WHITE);
    frame(die, 4, 4, 11, 11, EDGE);
    const c = page.canvas(0xe1c0 + f);
    const rot = rotate(die, f * 22.5);
    for (let i = 0; i < rot.data.length; i++) c.img.data[i] = rot.data[i];
    // motion lines trail to the left (blank faces: no false numbers, tables.md §3)
    line(c.img, 0, 6 + (f % 2), 2, 6 + (f % 2), '#C0B0DC');
    line(c.img, 0, 10 - (f % 2), 1, 10 - (f % 2), '#C0B0DC');
    c.commit();
  }
  {
    const c = page.canvas(0xe1c4);
    ellipse(c.img, 8, 8, 5, 5, '#D83440');
    c.commit();
  }
  {
    const c = page.canvas(0xe1c5);
    ellipse(c.img, 8, 8, 5, 5, (x, y, d) => (d > 0.8 ? '#C0B0DC' : d > 0.45 ? '#1B1B1B' : null));
    c.commit();
  }
  g(0xe1c6, ['...v...', '..vvv..', '.vvvvv.', 'vvvvvvv', '.vvvvv.', '..vvv..', '...v...'], { v: '#1E9E4A' });
  {
    const c = page.canvas(0xe1c7); // roulette ball (smaller than the Plinko ball)
    ellipse(c.img, 8, 8, 3.5, 3.5, (x, y) => (x < 8 && y < 8 ? '#FFFFFF' : '#E0D8E8'));
    c.commit();
  }
  g(0xe1c8, G7.dolly, {});
  g(0xe1c9, G7.pushRing, { k: '#8C8C8C', B: '#C0B0DC' });
  g(0xe1ca, G7.tick, {});
  g(0xe1cb, G7.cross, {});

  // cards (cards.md §4.1): flip / peel / slot / flight frames, button, pot, stacks, hatch, best hand, muck
  cardGlyphs(page);

  // E1E0–E1EF Wheel Party dye swatches (A5), rounded 12 × 12 with an ink border
  DYES.forEach((dye, i) => {
    const c = page.canvas(0xe1e0 + i);
    rect(c.img, 2, 2, 13, 13, dye);
    frame(c.img, 2, 2, 13, 13, '#180A28');
    for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) c.img.data.fill(0, (y * 16 + x) * 4, (y * 16 + x) * 4 + 4);
    c.commit();
  });
}

/** A 7 × 7 grid at 2× with a 1 px ink outline (mini chips must read on light and dark text). */
function outlineGrid(rows, legend) {
  const img = image(16, 16);
  drawGrid(img, rows, legend, 1, 1, 2);
  return outline(img, '#180A28');
}

function foil(page, cp, half) {
  const c = page.canvas(cp);
  rect(c.img, 2, 2, 13, 13, '#B4B4B4');
  frame(c.img, 2, 2, 13, 13, '#8C8C8C');
  for (let i = 0; i < 8; i++) setPx(c.img, 4 + i, 11 - i, '#D8D8D8');
  if (half) {
    // the top-left triangle is scratched away: bone paper with a few flakes on the edge
    for (let y = 2; y <= 13; y++) for (let x = 2; x <= 13; x++) if (x + y < 15) setPx(c.img, x, y, '#F4ECF8');
    for (const [x, y] of [[8, 6], [6, 8], [10, 4], [4, 10]]) setPx(c.img, x, y, '#8C8C8C');
  }
  c.commit();
}

function wheelIcon(page, cp) {
  const c = page.canvas(cp);
  const segs = ['#3A3A3A', '#3FA535', '#6C8EBF', '#3D5A80', '#2E7D32', '#9C27B0', '#00C853', '#4FC3F7'];
  ellipse(c.img, 8, 8, 7, 7, (x, y, d) => {
    if (d > 0.85) return '#5A3418';
    if (d < 0.25) return '#FFD640';
    const a = (Math.atan2(y + 0.5 - 8, x + 0.5 - 8) + Math.PI) / (2 * Math.PI);
    return segs[Math.floor(a * 8) % 8];
  });
  setPx(c.img, 7, 0, '#F4ECF8');
  setPx(c.img, 8, 0, '#F4ECF8');
  setPx(c.img, 7, 1, '#F4ECF8');
  setPx(c.img, 8, 1, '#F4ECF8');
  c.commit();
}

function cardGlyphs(page) {
  const card = (cp, draw) => {
    const c = painter(page, cp);
    draw(c);
    c.commit();
  };
  // E1D0 narrow back (flip mid-frame), E1D1 card edge
  card(0xe1d0, (c) => {
    cardBody(c, BACK, BACK_EDGE, 5, 10);
    backPattern(c, 6, 9);
  });
  card(0xe1d1, (c) => c.rect(7, 0, 8, 15, EDGE));
  // E1D2 / E1D3 peel ¼ / ½: the back with its lower-right corner bent up, white face showing
  const peel = (k) => (c) => {
    cardBody(c, BACK, BACK_EDGE);
    backPattern(c);
    for (let y = 0; y < 16; y++)
      for (let x = 2; x <= 13; x++) {
        const s = x - 2 + y; // distance along the diagonal from the top-left
        if (s > 26 - k) c.set(x, y, s === 27 - k ? EDGE : WHITE);
      }
  };
  card(0xe1d2, peel(4));
  card(0xe1d3, peel(9));
  // E1D4 empty slot: dashed outline
  card(0xe1d4, (c) => {
    for (let y = 1; y <= 14; y++) if (y % 3 !== 0) {
      c.set(2, y, '#C0B0DC');
      c.set(13, y, '#C0B0DC');
    }
    for (let x = 3; x <= 12; x++) if (x % 3 !== 0) {
      c.set(x, 1, '#C0B0DC');
      c.set(x, 14, '#C0B0DC');
    }
  });
  // E1D5 card in flight: tilted back + motion lines
  {
    const cv = page.canvas(0xe1d5);
    const back = image(16, 16);
    const p = { set: (x, y, col) => setPx(back, x, y, col), clear: () => {}, rect: (a, b, cc, d, col) => rect(back, a, b, cc, d, col) };
    cardBody(p, BACK, BACK_EDGE, 5, 12);
    for (let y = 2; y <= 13; y++) for (let x = 7; x <= 10; x++) if ((x + y) % 3 === 0) setPx(back, x, y, BACK_PATTERN);
    const r = rotate(back, 20);
    for (let i = 0; i < r.data.length; i++) cv.img.data[i] = r.data[i];
    line(cv.img, 0, 5, 2, 5, '#C0B0DC');
    line(cv.img, 0, 9, 3, 9, '#C0B0DC');
    line(cv.img, 0, 13, 1, 13, '#C0B0DC');
    cv.commit();
  }
  // E1D6 dealer button: white disc, gold ring, centre dot (no letters in textures)
  {
    const c = page.canvas(0xe1d6);
    ellipse(c.img, 8, 8, 6, 6, (x, y, d) => (d > 0.82 ? '#B07010' : d < 0.3 ? '#180A28' : '#F4ECF8'));
    c.commit();
  }
  // E1D7 pot: a heap of chips
  page.grid(0xe1d7, ['.......', '..rgr..', '.gbrgb.', 'rbgbrgr', 'brrgbrb', 'ggbrrgg', '.......'], { r: '#C62828', g: '#2E7D32', b: '#262626' });
  // E1D8 small chip stack (side view)
  page.grid(0xe1d8, ['.......', '..rrr..', '.rLLLr.', '.wrwrw.', '.rrrrr.', '.wrwrw.', '.......'], { r: 'chip.red', w: 'bone', L: 'chip.light' });
  // E1D9 hatched chip (a bet not yet confirmed)
  page.grid(0xe1d9, ['..BbB..', '.b.B.b.', 'B.b.B.b', 'b.B.b.B', 'B.b.B.b', '.b.B.b.', '..BbB..'], { b: 'bone', B: 'bone.shade' });
  page.grid(0xe1da, G7.bestHand, L7);
  // E1DB muck: two crossed face-down cards
  {
    const cv = page.canvas(0xe1db);
    for (const deg of [-25, 20]) {
      const back = image(16, 16);
      const p = { set: (x, y, col) => setPx(back, x, y, col), clear: () => {}, rect: (a, b, cc, d, col) => rect(back, a, b, cc, d, col) };
      cardBody(p, BACK, BACK_EDGE, 5, 10);
      const r = rotate(back, deg);
      for (let i = 0; i < r.data.length; i += 4) if (r.data[i + 3]) cv.img.data.set(r.data.subarray(i, i + 4), i);
    }
    cv.commit();
  }
}

/** Builds the whole E1 page. Returns the glyph page (img + used set). */
export function buildGlyphSheet() {
  const page = glyphPage(0xe1, 16);
  preWave(page);
  waveGlyphs(page);
  return page;
}
