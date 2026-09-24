// PvP presentation art (extras.md §7; extras-pvp §2, §9): player plates (you / rival / bot / winner / loser / grudge),
// head frames, bot difficulty badges, the VS emblem, pot plaque + chip piles, grudge banner halves, rivalry badges,
// record chips, taunt bubbles + pictograms, crown, medals, plaques for the Final Reveal, the podium, lobby/hub pieces,
// streak flames and the mode banner. Every plate is blank: names, numbers and words are drawn by code.
import { BOT, CHECK, CLAW, CROWN, FLAME, MODES, PADLOCK, SKULL, SWORDS, TAUNTS, TAUNT_ORDER } from './icons.mjs';
import { coinFlame } from './coin.mjs';
import {
  K, art16, bevel, blend, c, chipSide, chipTop, crop, disc, fade, hstrip, iconCell, image, mixHex, outline, over, plaque, put, rect, rng, roundCorners, shadow, stud, vstrip,
} from './kit.mjs';

/** Plate colourways. */
export const PLATES = {
  you: { edge: K.gold, edgeDark: K.goldShade, face: '#3A1A5C', light: '#5A2E86', accent: K.gold },
  rival: { edge: '#D83440', edgeDark: '#8C1834', face: '#3A0E1C', light: '#5E1A2E', accent: '#FF6E6A' },
  bot: { edge: '#8FA8C8', edgeDark: '#4A5A70', face: '#1C2438', light: '#2E3C58', accent: '#5CE8E0' },
  winner: { edge: K.goldLight, edgeDark: K.goldShade, face: '#5A3A08', light: '#8A5A10', accent: K.white },
  loser: { edge: '#5A4A6A', edgeDark: '#3A2A4A', face: '#221A2C', light: '#2E2438', accent: '#8A7A9A' },
  grudge: { edge: '#FF2A2A', edgeDark: '#8B0000', face: '#2A0608', light: '#4A0A10', accent: '#FFB0A0' },
};

/** Player plate nine-slice (48 × 32, border 10): ink, bevelled edge colour, face with a top sheen, corner rivets. */
export function plate(kind) {
  const P = PLATES[kind];
  const img = image(48, 32);
  rect(img, 0, 0, 48, 32, K.ink);
  bevel(img, 1, 1, 46, 30, P.edge, P.edgeDark);
  bevel(img, 2, 2, 44, 28, mixHex(P.edge, '#FFFFFF', 0.25), P.edge);
  rect(img, 3, 3, 42, 26, P.face);
  rect(img, 3, 3, 42, 1, P.light);
  rect(img, 3, 4, 42, 1, mixHex(P.face, P.light, 0.5));
  rect(img, 3, 28, 42, 1, mixHex(P.face, '#000000', 0.3));
  for (const [x, y] of [[4, 4], [43, 4], [4, 27], [43, 27]]) put(img, x, y, c(P.accent));
  if (kind === 'grudge') for (let i = 0; i < 3; i++) for (let k = 0; k < 5; k++) put(img, 38 + i * 2 + (k >> 1), 22 + k, c('#FF2A2A'));
  roundCorners(img, 0, 0, 48, 32, 1);
  return img;
}

/** Head frame (20 × 20) around a 16 px face: you (gold), rival (red), bot (steel with bolts). */
export function headFrame(kind) {
  const P = PLATES[kind];
  const img = image(20, 20);
  rect(img, 0, 0, 20, 20, K.ink);
  bevel(img, 1, 1, 18, 18, mixHex(P.edge, '#FFFFFF', 0.3), P.edgeDark);
  rect(img, 2, 2, 16, 16, K.ink);
  if (kind === 'bot') for (const [x, y] of [[1, 1], [18, 1], [1, 18], [18, 18]]) put(img, x, y, c('#C8D8EC'));
  roundCorners(img, 0, 0, 20, 20, 1);
  return img;
}

/** Bot difficulty badge (22 × 11): pill in the difficulty colour with 1–3 pips (MIXED = a die face). */
export function botBadge(level) {
  const col = { easy: ['#40A020', '#80FF40'], normal: ['#B07010', '#FFD640'], hard: ['#8C1834', '#FF6E6A'], mixed: ['#5A1A8A', '#D696FF'] }[level];
  const img = image(22, 11);
  rect(img, 1, 0, 20, 11, K.ink);
  rect(img, 0, 1, 22, 9, K.ink);
  rect(img, 1, 1, 20, 9, col[0]);
  rect(img, 1, 1, 20, 1, col[1]);
  // mini bot head at the left
  rect(img, 3, 3, 5, 5, '#C8D8EC');
  put(img, 4, 5, c('#5CE8E0'));
  put(img, 6, 5, c('#5CE8E0'));
  put(img, 5, 2, c('#FFD640'));
  const pips = { easy: 1, normal: 2, hard: 3, mixed: 0 }[level];
  for (let i = 0; i < pips; i++) {
    rect(img, 10 + i * 4, 4, 3, 3, K.ink);
    put(img, 11 + i * 4, 5, c(col[1]));
    put(img, 10 + i * 4, 4, c(K.white));
  }
  if (level === 'mixed') for (const [x, y] of [[11, 3], [14, 5], [17, 7], [11, 7], [17, 3]]) put(img, x, y, c(K.white));
  return img;
}

/** VS emblem (48 × 32): a split blue/red jagged shield with a gold rim; dark centre where code writes "VS". */
export function vsBadge() {
  const img = image(48, 32);
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 48; x++) {
      const dx = (x + 0.5 - 24) / 23;
      const dy = (y + 0.5 - 16) / 15;
      const a = Math.atan2(dy, dx);
      const jag = 0.68 + 0.32 * Math.abs(Math.cos(a * 5)) ** 3;
      const d = Math.hypot(dx, dy) / jag;
      if (d > 1) continue;
      const split = x + (y - 16) * 0.35 < 24;
      let hex = d > 0.9 ? K.ink : d > 0.8 ? (dy < 0 ? K.goldLight : K.goldShade) : split ? '#3D6AB0' : '#D83440';
      if (d <= 0.8 && d > 0.7) hex = split ? '#1E3A6A' : '#8C1834';
      if (Math.hypot(dx, dy) <= 0.5) hex = '#140822';
      put(img, x, y, c(hex));
    }
  // lightning seam
  const seam = [[27, 3], [25, 7], [26, 8], [23, 13]];
  for (const [x, y] of seam) put(img, x, y, c(K.goldLight));
  for (const [x, y] of [[24, 19], [22, 24], [23, 25], [20, 29]]) put(img, x, y, c(K.goldLight));
  return img;
}

/** Pot plaque nine-slice (48 × 24, border 8): gold framed dark well for the pot amount. */
export function potPlaque() {
  const img = plaque(48, 24, { edge: K.gold, face: '#140822', light: '#3A1A5C', dark: '#0A0414', studs: K.white });
  bevel(img, 1, 1, 46, 22, K.goldLight, K.goldShade);
  return img;
}

/** Chip pile for the pot (64 × 36): three stacks + scattered chips; `n` ∈ s | m | l. */
export function potChips(n = 'm') {
  const img = image(64, 36);
  const denoms = [['#C62828', '#F5F5F5'], ['#2E7D32', '#F5F5F5'], ['#262626', '#F5F5F5'], ['#6A1B9A', '#F3D34A'], ['#ECECEC', '#3A6FD8']];
  const heights = { s: [3, 2, 0], m: [6, 4, 3], l: [9, 7, 5] }[n];
  const xs = [14, 30, 44];
  heights.forEach((hgt, i) => {
    for (let k = 0; k < hgt; k++) {
      const [b, s] = denoms[(i * 2 + k) % denoms.length];
      chipSide(img, xs[i] - 7, 32 - k * 3, 15, b, s);
    }
    if (hgt) {
      const [b, s] = denoms[(i * 2 + hgt - 1) % denoms.length];
      chipTop(img, xs[i] + 0.5, 32 - hgt * 3 - 1.5, 7, b, s);
    }
  });
  if (n !== 's') {
    chipTop(img, 6, 31, 5, '#C62828', '#F5F5F5');
    chipTop(img, 57, 30, 5, '#6A1B9A', '#F3D34A');
  }
  return img;
}

/** Grudge banner halves (128 × 40 each, nine-slice border 12): torn red cloth, the torn edge on the inner side. */
export function grudgeHalf(side) {
  const img = image(128, 40);
  const r = rng(side === 'left' ? 11 : 12);
  const torn = Array.from({ length: 40 }, () => 4 + Math.floor(r() * 7));
  for (let y = 0; y < 40; y++)
    for (let x = 0; x < 128; x++) {
      const inner = side === 'left' ? 127 - x : x; // distance from the torn edge
      if (inner < torn[y] - 3) continue;
      let hex = y < 3 || y > 36 ? K.ink : y === 3 ? '#FF6A5A' : y === 36 ? '#4A0000' : (x + y) % 6 === 0 ? '#9A0A0A' : '#B01010';
      if (inner < torn[y]) hex = y % 2 ? '#4A0000' : '#FF2A2A';
      if (y > 3 && y < 36 && (y === 6 || y === 33)) hex = K.gold;
      put(img, x, y, c(hex));
    }
  // claw rip marks near the torn edge
  const x0 = side === 'left' ? 104 : 14;
  for (let i = 0; i < 3; i++)
    for (let k = 0; k < 12; k++) {
      const x = x0 + i * 4 + (k >> 2);
      put(img, x, 12 + k, c('#140000'));
      put(img, x + 1, 12 + k, c('#FF6A5A'));
    }
  return img;
}

/** Record chip nine-slice (16 × 10, border 3): lead (green) / trail (red) / even (bone). */
export function recordChip(kind) {
  const col = { lead: ['#1E6A10', '#80FF40'], trail: ['#6A0E1C', '#FF6E6A'], even: ['#3A2A4A', '#C0B0DC'] }[kind];
  const img = image(16, 10);
  rect(img, 1, 0, 14, 10, K.ink);
  rect(img, 0, 1, 16, 8, K.ink);
  rect(img, 1, 1, 14, 8, col[0]);
  rect(img, 1, 1, 14, 1, col[1]);
  return img;
}

/** Speech bubble nine-slice (24 × 24, border 8) + tail (10 × 8): friendly (bone) / cheeky (pink-red). */
export function bubble(kind) {
  const [face, edge, shade] = kind === 'cheeky' ? ['#FFE0E0', '#8C1834', '#F0B0B8'] : ['#F4ECF8', '#26103C', '#C0B0DC'];
  const img = image(24, 24);
  rect(img, 2, 0, 20, 24, edge);
  rect(img, 1, 1, 22, 22, edge);
  rect(img, 0, 2, 24, 20, edge);
  rect(img, 2, 1, 20, 22, face);
  rect(img, 1, 2, 22, 20, face);
  rect(img, 2, 21, 20, 1, shade);
  rect(img, 21, 3, 1, 18, shade);
  put(img, 3, 3, c(K.white));
  return img;
}
export function bubbleTail(kind) {
  const [face, edge] = kind === 'cheeky' ? ['#FFE0E0', '#8C1834'] : ['#F4ECF8', '#26103C'];
  const img = image(10, 8);
  const rows = ['eeeeeeeeee', 'effffffffe', '.effffffe.', '..efffffe.', '...effffe.', '....efffe.', '.....effe.', '......ee..'];
  rows.forEach((row, y) => [...row].forEach((ch, x) => ch !== '.' && put(img, x, y, c(ch === 'e' ? edge : face))));
  // the top row overlaps the bubble's bottom edge: keep it face-coloured inside
  for (let x = 1; x < 9; x++) put(img, x, 0, c(face));
  return img;
}

/** Taunt pictograms (16 × 16 × 8 and a 20 px outlined sheet), order = TAUNT_ORDER. */
export const tauntIcons16 = () => hstrip(TAUNT_ORDER.map((k) => art16(TAUNTS[k])));
export const tauntIcons20 = () => hstrip(TAUNT_ORDER.map((k) => iconCell(TAUNTS[k], 20)));

/** Crown (16 × 12) cropped from the crown art; the crown drop at the winner reveal. */
export const crown = () => crop(outline(art16(CROWN), c(K.ink)), 0, 2, 16, 12);
export const padlock = () => crop(art16(PADLOCK), 2, 2, 12, 13);

/** Place medals (12 × 16 × 6): ribbon + disc (gold, silver, bronze, then iron / copper / stone); place is text. */
export function rankMedals() {
  const mats = [
    ['#FFD640', '#FFF4B0', '#B07010'], ['#D8D8E0', '#FFFFFF', '#7A7A88'], ['#C8763C', '#F0A870', '#7A3A1C'],
    ['#8A8A96', '#B8B8C4', '#4A4A56'], ['#A86A3A', '#D09060', '#5A3418'], ['#6A6A72', '#8A8A92', '#3A3A40'],
  ];
  const ribbons = [['#D83440', '#3D6AB0'], ['#3D6AB0', '#F4ECF8'], ['#2E7D32', '#F4ECF8'], ['#5A4A6A', '#8A7A9A'], ['#5A4A6A', '#8A7A9A'], ['#5A4A6A', '#8A7A9A']];
  return mats.map(([m, l, d], i) => {
    const img = image(12, 16);
    const [ra, rb] = ribbons[i];
    for (let y = 0; y < 7; y++) {
      put(img, 3 + (y >> 1), y, c(ra));
      put(img, 4 + (y >> 1), y, c(rb));
      put(img, 8 - (y >> 1), y, c(ra));
      put(img, 7 - (y >> 1), y, c(rb));
    }
    disc(img, 6, 10.5, 5.4, c(K.ink));
    disc(img, 6, 10.5, 4.5, c(m));
    disc(img, 5.5, 10, 2.4, c(l), 0.5);
    put(img, 4, 8, c(K.white));
    for (let a = 0; a < 6; a++) put(img, Math.round(6 + Math.cos(a) * 3.9 - 0.5), Math.round(10.5 + Math.sin(a) * 3.9 - 0.5), c(d));
    return img;
  });
}

/** Reveal plaques (64 × 20 nine-slice, border 4): back (card-back lattice), face (dark), gold (winner). */
export function revealPlaque(kind) {
  const img = image(64, 20);
  rect(img, 0, 0, 64, 20, K.ink);
  if (kind === 'back') {
    for (let y = 1; y < 19; y++) for (let x = 1; x < 63; x++) put(img, x, y, c((x + y) % 4 === 0 || (x - y + 100) % 4 === 0 ? '#BE5AFF' : '#3A1A5C'));
    bevel(img, 1, 1, 62, 18, K.gold, K.goldShade);
    rect(img, 2, 2, 60, 1, K.goldLight);
  } else if (kind === 'face') {
    rect(img, 1, 1, 62, 18, '#26103C');
    bevel(img, 1, 1, 62, 18, '#783CBE', '#3A1A5C');
    rect(img, 2, 2, 60, 1, '#4A2474');
  } else {
    rect(img, 1, 1, 62, 18, '#7A5010');
    bevel(img, 1, 1, 62, 18, K.goldLight, K.goldShade);
    bevel(img, 2, 2, 60, 16, K.gold, '#B07010');
    rect(img, 3, 3, 58, 14, '#5A3A08');
    rect(img, 3, 3, 58, 1, '#8A5A10');
  }
  roundCorners(img, 0, 0, 64, 20, 1);
  return img;
}

/** Podium (200 × 60): 2nd | 1st | 3rd steps in silver / gold / bronze with a marble face; places drawn as text. */
export function podium() {
  const img = image(200, 60);
  const steps = [[0, 20, 64, '#C8C8D8', '#FFFFFF', '#7A7A88'], [64, 0, 72, '#FFD640', '#FFF4B0', '#B07010'], [136, 32, 64, '#C8763C', '#F0A870', '#7A3A1C']];
  for (const [x, y, w, m, l, d] of steps) {
    rect(img, x, y, w, 60 - y, K.ink);
    rect(img, x + 1, y + 1, w - 2, 59 - y, '#2A1840');
    for (let yy = y + 8; yy < 59; yy += 2) for (let xx = x + 2; xx < x + w - 2; xx += 5) put(img, xx + ((yy >> 1) % 2) * 2, yy, c('#34204E'));
    rect(img, x + 1, y + 1, w - 2, 6, m);
    rect(img, x + 1, y + 1, w - 2, 1, l);
    rect(img, x + 1, y + 6, w - 2, 1, d);
    // plate for the place number
    rect(img, x + w / 2 - 9, y + 14, 18, 14, K.ink);
    rect(img, x + w / 2 - 8, y + 15, 16, 12, d);
    rect(img, x + w / 2 - 8, y + 15, 16, 1, l);
  }
  return img;
}

/** Winner banner nine-slice (80 × 32, border 12): gold plaque with little wings. */
export function winnerBanner() {
  const img = image(80, 32);
  const body = plaque(64, 24, { edge: K.gold, face: '#7A1430', light: '#A8203C', dark: '#4A0A1C', studs: K.white });
  bevel(body, 1, 1, 62, 22, K.goldLight, K.goldShade);
  over(img, body, 8, 4);
  for (const side of [0, 1])
    for (let k = 0; k < 7; k++) {
      const x = side ? 72 + (k >> 1) : 7 - (k >> 1);
      rect(img, x, 8 + k * 2, 1, 2, k % 2 ? K.goldShade : K.gold);
      rect(img, side ? x + 1 : x - 1, 9 + k * 2, 1, 1, K.ink);
    }
  return img;
}

/** Mode banner nine-slice (64 × 24, border 8): No more bets / FINAL BALL / ALL SQUARE / UNDERDOG / EDGE; label is text. */
export function modeBanner() {
  const img = plaque(64, 24, { edge: K.gold, face: '#26103C', light: '#4A2474', dark: '#140822', studs: K.goldLight });
  bevel(img, 1, 1, 62, 22, K.goldLight, K.goldShade);
  for (let x = 6; x < 58; x += 4) put(img, x, 4, c('#783CBE'));
  return img;
}

/** ALL-IN tag nine-slice (24 × 12, border 4): red plaque with a flame notch on the left. */
export function allInTag() {
  const img = plaque(24, 12, { edge: '#FF6E6A', face: '#D83440', light: '#FF9A90', dark: '#8C1834' });
  put(img, 3, 5, c('#FFE070'));
  put(img, 3, 6, c('#FF7A1A'));
  put(img, 4, 7, c('#FF7A1A'));
  return img;
}

/** Empty seat (16 × 16 nine-slice, border 3): dashed lilac outline on a dark well. */
export function seatEmpty() {
  const img = image(16, 16);
  rect(img, 1, 1, 14, 14, '#140822');
  for (let i = 0; i < 16; i++) {
    const on = i % 4 < 2;
    if (!on) continue;
    for (const [x, y] of [[i, 0], [i, 15], [0, i], [15, i]]) put(img, x, y, c('#783CBE'));
  }
  return img;
}

/** Streak flames (16 × 16 × 8): red and gold (from the coin flame drawn smaller). */
export function streakFlame(kind) {
  const src = coinFlame(kind === 'gold' ? 'fire' : 'fire');
  const frames = [];
  for (let f = 0; f < 8; f++) {
    const fr = crop(src, 0, f * 24, 16, 24);
    const img = image(16, 16);
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const sy = Math.floor(y * 1.5);
        const o = (sy * 16 + x) * 4;
        if (!fr.data[o + 3]) continue;
        let px = [fr.data[o], fr.data[o + 1], fr.data[o + 2], 255];
        if (kind === 'gold') px = c(fr.data[o] > 250 && fr.data[o + 1] > 200 ? '#FFFFFF' : fr.data[o + 1] > 120 ? K.gold : K.goldShade);
        put(img, x, y, px);
      }
    frames.push(outline(img, c(K.ink), { k: 0.8 }));
  }
  return vstrip(frames);
}

/** Snuffed flame (16 × 16 × 3): flame → ember → smoke. */
export function flameSnuff() {
  const base = iconCell(FLAME, 16);
  const ember = image(16, 16);
  disc(ember, 8, 12, 2.5, c('#FF7A1A'));
  put(ember, 8, 11, c('#FFE070'));
  const smoke = image(16, 16);
  for (const [x, y, r] of [[8, 12, 2.4], [7, 8, 2], [9, 5, 1.6], [8, 2, 1.1]]) disc(smoke, x, y, r, c('#8A8A92'), 0.7);
  return [base, outline(ember, c(K.ink)), smoke];
}

/** Broken chain (32 × 16 × 2): whole → snapped (Sweet revenge). */
export function brokenChain() {
  return [false, true].map((snap) => {
    const img = image(32, 16);
    const link = (cx, cy, rx, ry, dx) => {
      for (let y = 0; y < 16; y++)
        for (let x = 0; x < 32; x++) {
          const d = Math.hypot((x + 0.5 - cx - dx) / rx, (y + 0.5 - cy) / ry);
          if (d > 1.12 || d < 0.52) continue;
          put(img, x, y, c(d > 0.98 || d < 0.62 ? K.ink : y + 0.5 < cy ? '#E8E8EE' : '#9A9AA8'));
        }
    };
    // horizontal links (flat rings) and vertical links (edge-on bars) alternate
    const xs = [5, 12, 19, 26];
    xs.forEach((x, i) => {
      const dx = snap ? (i < 2 ? -2 : 2) : 0;
      if (i % 2 === 0) link(x, 8, 5, 3.4, dx);
      else {
        rect(img, x - 1 + dx, 3, 3, 10, K.ink);
        rect(img, x + dx, 4, 1, 8, '#C8C8D4');
      }
    });
    if (snap) for (const [x, y] of [[15, 3], [16, 12], [14, 13], [17, 2], [16, 7]]) put(img, x, y, c(K.gold));
    return img;
  });
}

/** Mode icons: 16 px sheet (order coin, wheel, plinko, scratch, slots) and 40 px hub-card versions. */
export const MODE_ORDER = ['coin', 'wheel', 'plinko', 'scratch', 'slots'];
export const modeIcons16 = () => hstrip(MODE_ORDER.map((m) => art16(MODES[m])));
export const modeIcons40 = () => hstrip(MODE_ORDER.map((m) => iconCell(MODES[m], 40)));

/** Hub mode card nine-slice (48 × 48, border 12): framed velvet card with a spotlight top. */
export function modeCard(selected = false) {
  const img = image(48, 48);
  rect(img, 0, 0, 48, 48, K.ink);
  bevel(img, 1, 1, 46, 46, selected ? K.goldLight : '#BE5AFF', selected ? K.goldShade : '#3A1A5C');
  bevel(img, 2, 2, 44, 44, selected ? K.gold : '#783CBE', selected ? K.goldShade : '#26103C');
  for (let y = 3; y < 45; y++) rect(img, 3, y, 42, 1, mixHex('#3A1A5C', '#1C0C2E', y / 45));
  for (let y = 3; y < 20; y++) for (let x = 3; x < 45; x++) blend(img, x, y, c('#BE5AFF'), Math.max(0, 0.18 - Math.hypot(x - 24, y - 3) / 110));
  roundCorners(img, 0, 0, 48, 48, 1);
  return img;
}

/** Lobby seat row nine-slice (32 × 20, border 6). */
export function lobbyRow(kind = 'normal') {
  const img = plaque(32, 20, kind === 'host'
    ? { edge: K.gold, face: '#3A1A5C', light: '#5A2E86', dark: '#26103C' }
    : { edge: '#783CBE', face: '#26103C', light: '#3A1A5C', dark: '#140822' });
  return img;
}

/** Small 16 px badges sheet: rival swords, grudge claw, nemesis skull, bot, check, padlock (outlined 16). */
export const BADGE_ORDER = ['rival', 'grudge', 'nemesis', 'bot', 'check', 'padlock'];
export function badges16() {
  const defs = { rival: SWORDS, grudge: CLAW, nemesis: SKULL, bot: BOT, check: CHECK, padlock: PADLOCK };
  return hstrip(BADGE_ORDER.map((k) => art16(defs[k])));
}
export function badges20() {
  const defs = { rival: SWORDS, grudge: CLAW, nemesis: SKULL, bot: BOT, check: CHECK, padlock: PADLOCK };
  return hstrip(BADGE_ORDER.map((k) => iconCell(defs[k], 20)));
}

/** Claw marks over the underdog's head (16 × 16). */
export const claw = () => art16(CLAW);

/** Pot glow disc behind the pot pile (72 × 24): soft gold ellipse. */
export function potGlow() {
  const img = image(72, 24);
  for (let y = 0; y < 24; y++)
    for (let x = 0; x < 72; x++) {
      const d = Math.hypot((x + 0.5 - 36) / 36, (y + 0.5 - 12) / 12);
      if (d < 1) blend(img, x, y, c(K.gold), (1 - d) * 0.45);
    }
  return img;
}

export { fade, shadow, stud, vstrip };
