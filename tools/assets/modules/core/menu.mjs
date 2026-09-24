// Casino Menu shell art (docs/design/visual/extras.md §8–§9; global.md §4.2, §4.10): the "casino ledger" — lobby and
// Loan Shark backdrops, the leather-and-gold shell frame, ruled ledger pages, bookmark tabs + tab icons, header and
// balance plaques, ledger rows, progress bars, the Loan Shark's portrait and dark kit (debt meter, overdue stamp,
// contract paper, offer cards), achievement plates + medals, and the HUD chip counter + toast variants.
// Pure drawing; core.mjs names the files. The drawing kit is shared with the extras module (../extras/kit.mjs).
import {
  K, art16, bevel, blend, bayer, bulb, c, chipSide, chipTop, disc, hstrip, iconCell, image, line, mixHex, outline, over, plaque, put, rect, rng,
  roundCorners, scaleUp, shadow, speckle, stud, vgrad, vignette, vstrip,
} from '../extras/kit.mjs';
import { SWORDS } from '../extras/icons.mjs';

const W = 400;
const H = 240;
const I = (id, pal, grid) => ({ id: `menu_${id}`, pal, grid: grid.map((r) => r.padEnd(16, '.').slice(0, 16)) });

// ---- tab icons (16 px) ------------------------------------------------------------------------------------------------
const P = {
  k: '#180A28', y: '#FFD640', Y: '#B07010', h: '#FFF4B0', b: '#8A5220', B: '#5A3418', n: '#C8763C', w: '#F4ECF8', W: '#C0B0DC',
  r: '#D83440', R: '#8C1834', g: '#40D060', G: '#1E7A30', c: '#5CE8E0', C: '#1A9A94', p: '#BE5AFF', P: '#783CBE', s: '#8FA8C8', S: '#4A5A70',
  e: '#E8E8EE', a: '#9A9AA8',
};
export const TAB_ICONS = {
  wallet: I('wallet', P, [
    '................', '................', '...kkkkkkkkk....', '..kBbbbbbbbbk...', '.kbbbbbbbbbbbk..', '.kkkkkkkkkkkkkk.', '.kbbbbbbbbkkkkk.',
    '.kbbbbbbbkyyyyk.', '.kbbbbbbbkyhYyk.', '.kbbbbbbbkyYYyk.', '.kbbbbbbbbkkkkk.', '.kBbbbbbbbbbbBk.', '.kBBBBBBBBBBBBk.', '..kkkkkkkkkkkk..', '................', '................',
  ]),
  vip: I('vip', P, [
    '................', '....kkkkkkkk....', '...kccwcccCCk...', '..kcwwccccCCCk..', '.kccccccccCCCCk.', '.kkkkkkkkkkkkkk.', '..kCcccccccCCk..', '...kCcccccCCk...',
    '....kCcccCCk....', '.....kCcCCk.....', '......kCCk......', '.......kk.......', '................', '................', '................', '................',
  ]),
  contracts: I('contracts', P, [
    '................', '..kkkkkkkkkkk...', '.kwwwwwwwwwwwk..', '.kWkkkkkkkkkWk..', '..kwwwwwwwwwk...', '..kwWWWWWWwwk...', '..kwwwwwwwwwk...', '..kwWWWWWwwwk...',
    '..kwwwwwwwwwk...', '..kwWWWWWWwkk...', '..kwwwwwwwkrrk..', '.kWkkkkkkkrRRrk.', '.kwwwwwwwwkrrk..', '..kkkkkkkkk.kRk.', '...........kRk..', '................',
  ]),
  loan: I('loan', P, [
    '................', '................', '.......k........', '......ksk.......', '......kssk......', '.....ksssk......', '.....ksSssk.....', '....ksSSsssk....',
    '....ksSSSsssk...', '...ksSSSSssssk..', 'kkkkkkkkkkkkkkkk', 'kSSkSSSkkSSSkSSk', '.kkSSkkSSSkkSSk.', '...kk..kkk..kk..', '................', '................',
  ]),
  achievements: I('achievements', P, [
    '................', '...kkkkkkkkkk...', '.kkyyyyyyyyyykk.', 'kykyhyyyyyyykyk.', 'kykyhyyyyyyykyk.', '.kkyhyyyyyyYkk..', '...kyyyyyyYk....',
    '....kyyyyYk.....', '.....kyyYk......', '......kyk.......', '......kYk.......', '.....kyyYk......', '....kkkkkkk.....', '....kBBBBBk.....', '....kkkkkkk.....', '................',
  ]),
  challenges: I('challenges', P, [
    '................', '.....kkkkkk.....', '...kkrrrrrrkk...', '..krrwwwwwwrrk..', '..krwwrrrrwwrk..', '.krrwrrwwrrwrrk.', '.krwwrwwwwrwwrk.', '.krwwrwrrwrwwrk.',
    '.krwwrwrrwrwwrk.', '.krwwrwwwwrwwrk.', '.krrwrrwwrrwrrk.', '..krwwrrrrwwrk..', '..krrwwwwwwrrk..', '...kkrrrrrrkk...', '.....kkkkkk.....', '................',
  ]),
  my_casino: I('my_casino', P, [
    '.......kk.......', '......kyyk......', '.....kPPPPk.....', '...kkPppPPPkk...', '..kPPPPPPPPPPk..', '.kkkkkkkkkkkkkk.', '.kwWkwWkkwWkwWk.', '.kwWkwWkkwWkwWk.',
    '.kwWkwWkkwWkwWk.', '.kwWkwWkkwWkwWk.', '.kwWkwWkkwWkwWk.', '.kkkkkkyykkkkkk.', '.kwwwwkyykwwwwk.', 'kkkkkkkkkkkkkkkk', 'kYYYYYYYYYYYYYYk', 'kkkkkkkkkkkkkkkk',
  ]),
  rules: I('rules', P, [
    '................', '................', '.kkkkkk.kkkkkk..', 'kwwwwwwkwwwwwwk.', 'kwWWWWwkwWWWWwk.', 'kwwwwwwkwwwwwwk.', 'kwWWWwwkwWWWWwk.', 'kwwwwwwkwwwwwwk.',
    'kwWWWWwkwWWWwwk.', 'kwwwwwwkwwwwwwk.', 'kwWWWwwkwWWWWwk.', 'kwwwwwwkwwwwwwk.', 'kPPPPPPkPPPPPPk.', '.kkkkkkkkkkkkk..', '.......rR.......', '.......r........',
  ]),
  cashier: I('cashier', P, [
    '................', '................', '......kkkkk.....', '....kkrwrwrkk...', '...krwrrrrrwrk..', '...kRrrrrrrrRk..', '...kkRRRRRRRkk..', '...kgwgwgwgwgk..',
    '...kGGGGGGGGGk..', '...kkkkkkkkkkk..', '...kpypypypypk..', '...kPPPPPPPPPk..', '...kkkkkkkkkkk..', '...kwkwkwkwkwk..', '...kWWWWWWWWWk..', '...kkkkkkkkkkk..',
  ]),
};
function gearIcon() {
  const img = image(16, 16);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8;
      const dy = y + 0.5 - 8;
      const d = Math.hypot(dx, dy);
      const a = Math.atan2(dy, dx);
      const tooth = Math.cos(a * 8) > 0.25;
      if (d > (tooth ? 7.5 : 5.8) || d < 2.2) continue;
      put(img, x, y, c(d > (tooth ? 6.6 : 4.9) ? K.ink : d < 3.2 ? K.ink : dx + dy < 0 ? '#E8E8EE' : '#9A9AA8'));
    }
  return img;
}
/** Tab order (Casino Menu): icons are code-indexed in this order in `tab_icons.png`. */
export const TAB_ORDER = ['wallet', 'vip', 'contracts', 'loan', 'achievements', 'challenges', 'pvp', 'my_casino', 'rules', 'cashier', 'settings'];
function tabIcon16(name) {
  if (name === 'settings') return gearIcon();
  if (name === 'pvp') return art16(SWORDS);
  return art16(TAB_ICONS[name]);
}
export const tabIcons16 = () => hstrip(TAB_ORDER.map(tabIcon16));
export const tabIcons20 = () => hstrip(TAB_ORDER.map((n) => iconCell(tabIcon16(n), 20)));
export const tabIcons40 = () => hstrip(TAB_ORDER.map((n) => iconCell(tabIcon16(n), 40)));

// ---- shell -------------------------------------------------------------------------------------------------------------
/** Shell frame nine-slice (64², border 16): burgundy leather ledger binding, gold filigree corners, stitching. */
export function shellFrame(loan = false) {
  const img = image(64, 64);
  const leather = loan ? ['#1A2A2E', '#22363A', '#142024', '#0C1618'] : ['#5A0E24', '#6A1430', '#4A0A1C', '#3A0814'];
  const trim = loan ? ['#8A9AA8', '#5A6A78', '#3A4650'] : [K.goldLight, K.gold, K.goldShade];
  const r = rng(loan ? 71 : 70);
  for (let y = 0; y < 64; y++)
    for (let x = 0; x < 64; x++) {
      if (x >= 16 && x < 48 && y >= 16 && y < 48) continue;
      const n = r();
      put(img, x, y, c(n < 0.08 ? leather[1] : n > 0.93 ? leather[2] : leather[0]));
    }
  const band = (i, lt, dk) => {
    rect(img, i, i, 64 - 2 * i, 1, lt);
    rect(img, i, i, 1, 64 - 2 * i, lt);
    rect(img, i, 63 - i, 64 - 2 * i, 1, dk);
    rect(img, 63 - i, i, 1, 64 - 2 * i, dk);
  };
  band(0, K.ink, K.ink);
  band(1, trim[0], trim[2]);
  band(2, trim[1], trim[2]);
  band(13, trim[2], trim[0]);
  band(14, trim[1], trim[1]);
  band(15, K.ink, K.ink);
  // stitching (dashed) at 6 px
  for (let i = 6; i < 58; i += 3) for (const [x, y] of [[i, 6], [i, 57], [6, i], [57, i]]) put(img, x, y, c(loan ? '#5A6A78' : '#C8903C'));
  // corner filigree (loan: rivets + hazard red)
  for (const [sx, sy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
    const ox = sx ? 63 : 0;
    const oy = sy ? 63 : 0;
    const f = (x, y, hex) => put(img, sx ? ox - x : ox + x, sy ? oy - y : oy + y, c(hex));
    if (loan) {
      for (const [x, y] of [[8, 8], [10, 4], [4, 10]]) stud(img, sx ? 63 - x : x, sy ? 63 - y : y, '#8A9AA8', '#E8F0F8', 1.4);
      for (let k = 0; k < 6; k++) f(3 + k, 12 - k, '#D83440');
    } else {
      for (let k = 3; k < 12; k++) f(k, 3, trim[1]);
      for (let k = 3; k < 12; k++) f(3, k, trim[1]);
      for (const [x, y] of [[5, 5], [6, 6], [7, 7], [8, 6], [6, 8], [9, 5], [5, 9], [10, 7], [7, 10]]) f(x, y, trim[0]);
      f(8, 8, K.white);
    }
  }
  return img;
}

/** Ledger page nine-slice (32², border 6; the centre tiles): dark page with ruled lines every 10 px and a margin rule. */
export function page(loan = false) {
  const img = image(32, 32);
  const [bg, rule, margin, edge] = loan ? ['#0E1A1E', '#16282C', '#5A1018', '#22363A'] : ['#1E0C30', '#2A1640', '#5A1A3A', '#3A1A5C'];
  rect(img, 0, 0, 32, 32, bg);
  for (let y = 6; y < 26; y += 10) rect(img, 0, y + 9, 32, 1, rule);
  rect(img, 0, 0, 32, 1, K.ink);
  rect(img, 0, 31, 32, 1, K.ink);
  rect(img, 0, 0, 1, 32, K.ink);
  rect(img, 31, 0, 1, 32, K.ink);
  bevel(img, 1, 1, 30, 30, mixHex(edge, '#000000', 0.4), edge);
  rect(img, 4, 1, 1, 30, margin);
  return img;
}

/** Bookmark tab nine-slice (32 × 24, border 6): normal / hover / selected; `loan` = the dark Loan Shark tab. */
export function tab(state, loan = false) {
  const img = image(32, 24);
  const face = loan
    ? { normal: '#1A2A2E', hover: '#22363A', selected: '#0E1A1E' }[state]
    : { normal: '#3A1A5C', hover: '#4A2474', selected: '#1E0C30' }[state];
  const edge = loan ? (state === 'selected' ? '#D83440' : '#5A6A78') : state === 'selected' ? K.gold : state === 'hover' ? '#BE5AFF' : '#783CBE';
  rect(img, 1, 0, 30, 24, K.ink);
  rect(img, 0, 1, 32, 23, K.ink);
  rect(img, 1, 1, 30, 23, edge);
  rect(img, 2, 2, 28, 22, face);
  rect(img, 2, 2, 28, 1, mixHex(face, '#FFFFFF', 0.18));
  if (state === 'selected') {
    rect(img, 2, 1, 28, 1, loan ? '#FF6E6A' : K.goldLight);
    rect(img, 1, 23, 30, 1, face); // opens into the page
    rect(img, 0, 23, 1, 1, K.ink);
  } else rect(img, 0, 23, 32, 1, K.ink);
  return img;
}

/** Header plate nine-slice (64 × 20, border 8): brass nameplate with bulbs at the ends; title drawn by code. */
export function headerPlate() {
  const img = image(64, 20);
  rect(img, 0, 0, 64, 20, K.ink);
  bevel(img, 1, 1, 62, 18, K.goldLight, K.goldShade);
  rect(img, 2, 2, 60, 16, K.gold);
  rect(img, 2, 2, 60, 1, K.goldLight);
  rect(img, 2, 17, 60, 1, K.goldShade);
  rect(img, 8, 4, 48, 12, '#26103C');
  rect(img, 8, 4, 48, 1, '#140822');
  bulb(img, 4, 9, true);
  bulb(img, 59, 9, true);
  roundCorners(img, 0, 0, 64, 20, 1);
  return img;
}

/** Balance plaque nine-slice (32 × 20, border 8): gold framed dark well with a chip socket on the left. */
export function balancePlaque() {
  const img = plaque(32, 20, { edge: K.gold, face: '#140822', light: '#26103C', dark: '#0A0414' });
  bevel(img, 1, 1, 30, 18, K.goldLight, K.goldShade);
  return img;
}

/** Ledger row nine-slice (32 × 14, border 4): normal / alt / highlight (hover). */
export function ledgerRow(kind = 'normal') {
  const face = { normal: '#241036', alt: '#2C143F', highlight: '#3A1A5C', loan: '#12242A' }[kind];
  const img = image(32, 14);
  rect(img, 0, 0, 32, 14, face);
  rect(img, 0, 13, 32, 1, kind === 'loan' ? '#0A1418' : '#1A0A28');
  rect(img, 0, 0, 32, 1, mixHex(face, '#FFFFFF', 0.08));
  if (kind === 'highlight') {
    rect(img, 0, 0, 1, 14, K.gold);
    rect(img, 31, 0, 1, 14, K.goldShade);
  }
  return img;
}

/** Progress bar frame nine-slice (32 × 10, border 4) and fill tiles (8 × 6): gold / green / red / vip-tier tint. */
export function progressFrame() {
  const img = image(32, 10);
  rect(img, 0, 0, 32, 10, K.ink);
  bevel(img, 1, 1, 30, 8, '#3A1A5C', '#783CBE');
  rect(img, 2, 2, 28, 6, '#0A0414');
  roundCorners(img, 0, 0, 32, 10, 1);
  return img;
}
export function progressFill(kind) {
  const [a, b, lt] = { gold: [K.gold, K.goldShade, K.goldLight], green: ['#40D060', '#1E7A30', '#A0FFA0'], red: ['#D83440', '#8C1834', '#FF9A90'], lilac: ['#BE5AFF', '#783CBE', '#E8C0FF'] }[kind];
  const img = image(8, 6);
  rect(img, 0, 0, 8, 6, a);
  rect(img, 0, 0, 8, 1, lt);
  rect(img, 0, 5, 8, 1, b);
  put(img, 2, 2, c(lt));
  put(img, 6, 3, c(mixHex(a, lt, 0.5)));
  return img;
}

// ---- achievements -------------------------------------------------------------------------------------------------------
/** Achievement plate nine-slice (48 × 24, border 8): locked (slate) / unlocked (purple + gold) / gold (rare). */
export function achPlate(kind) {
  const S = {
    locked: { edge: '#4A3A5A', face: '#1A1224', light: '#241A30', dark: '#120C1A' },
    unlocked: { edge: '#783CBE', face: '#2A1440', light: '#3A1A5C', dark: '#1A0A28' },
    gold: { edge: K.gold, face: '#3A2408', light: '#5A3A08', dark: '#2A1804' },
  }[kind];
  const img = plaque(48, 24, { ...S, studs: kind === 'locked' ? '#4A3A5A' : kind === 'gold' ? K.white : K.gold });
  if (kind === 'gold') bevel(img, 1, 1, 46, 22, K.goldLight, K.goldShade);
  // medallion well at the left (24 px medal sits at x 2)
  rect(img, 3, 3, 18, 18, mixHex(S.face, '#000000', 0.3));
  return img;
}

/** Achievement medals (24 × 24 × 4): locked (grey + padlock), bronze, silver, gold (with a star emboss). */
export function achMedals() {
  const mats = [['#4A4A56', '#6A6A76', '#2E2E36'], ['#C8763C', '#F0A870', '#7A3A1C'], ['#C8C8D8', '#FFFFFF', '#6A6A7A'], [K.gold, K.goldLight, K.goldShade]];
  return mats.map(([m, l, d], i) => {
    const img = image(24, 24);
    for (let y = 0; y < 24; y++)
      for (let x = 0; x < 24; x++) {
        const dx = x + 0.5 - 12;
        const dy = y + 0.5 - 12;
        const dd = Math.hypot(dx, dy);
        if (dd > 11) continue;
        const a = Math.atan2(dy, dx);
        let hex = dd > 10.2 ? K.ink : dd > 9 ? (Math.cos(a * 12) > 0 ? l : d) : dd > 8 ? d : dx + dy < -4 ? l : m;
        const star = dd < 2.4 + 4.2 * ((Math.cos(5 * (a + Math.PI / 2)) + 1) / 2) ** 3;
        if (star && i > 0) hex = dx + dy < 0 ? l : d;
        put(img, x, y, c(hex));
      }
    if (i === 0) {
      rect(img, 9, 11, 7, 6, K.ink);
      rect(img, 10, 12, 5, 4, '#9A9AA8');
      rect(img, 10, 8, 1, 4, K.ink);
      rect(img, 14, 8, 1, 4, K.ink);
      rect(img, 10, 7, 5, 1, K.ink);
      put(img, 12, 13, c(K.ink));
    }
    return img;
  });
}

// ---- Loan Shark ------------------------------------------------------------------------------------------------------------
const SHARK_PAL = {
  k: '#0A0A12', h: '#26262E', H: '#8C1834', j: '#3A3A46', S: '#7A9AB8', s: '#5A7A9A', d: '#3A5070', w: '#E8F0F8', m: '#2A0A14', t: '#FFFFFF',
  g: '#FFD640', r: '#D83440', e: '#FFE070', L: '#22222E', l: '#3A3A4A', W: '#F4ECF8', R: '#8C1834',
};
const SHARK_GRID = [
  '',
  '...........kkkkkkkk',
  '..........khhhhhhhhk',
  '.........khhjhhhhhhhk',
  '.........khjhhhhhhhhhk',
  '.........kHHHHHHHHHHHk',
  '.....kkkkkkkkkkkkkkkkkkkkk',
  '....khhhhhhhhhhhhhhhhhhhhhhk',
  '.....kkkkkkkkkkkkkkkkkkkkkk',
  '......kSSSSSSSSSSSSSSSSk',
  '.....kSSSSSSSSSSSSSSSSSSSk',
  '....kSSSSSSSSSSSSSkkkSSSSSk',
  '....kSSSSSSSSSSSSkwekSSSSSSk',
  '...ksSSSSSSSSSSSSkkkSSSSSSSSk',
  '...kssSSSSSSSSSSSSSSSSSSSSSSSk',
  '...ksssSSSSSSSSSSSSSSSSSSSSSSSk',
  '..ksdsssSSSSSSSSSSSSSSSSSSSSSSk',
  '..ksdssssmmmmmmmmmmmmmmmmmmmmmk',
  '..ksdsssmtmtmtmtmtgtmtmtmtmtmk',
  '..ksssssmmmmmmmmmmmmmmmmmmmmk',
  '..kssssswtmtmtmtmtmtmtmtmwk',
  '..ksssswwwwwwwwwwwwwwwwwwk',
  '...kssswwwwwwwwwwwwwwwwk',
  '....kkswwwwwwwwwwwwwwk',
  '...kLLkkkkkkkkkkkkkkLLk',
  '..kLLLLLkWWkrrkWWkLLLLLk',
  '.kLLlLLLLkWkrrkWkLLLlLLLk',
  '.kLLlLLLLLkWkrrkWkLLlLLLLk',
  'kLLLlLLLLLLkWrrWkLLLlLLLLLk',
  'kLLLlLLLLLLLkrrkLLLLlLLLLLk',
  'kLLLlLLLLLLLLkkLLLLLlLLLLLk',
  'kkkkkkkkkkkkkkkkkkkkkkkkkkk',
];
/** Loan Shark portrait (72 × 72): a 32 px shark in a fedora and pinstripes, drawn at 2× with the house outline. */
export function sharkPortrait() {
  const grid = SHARK_GRID.map((r) => r.padEnd(32, '.').slice(0, 32));
  const art = image(32, 32);
  grid.forEach((row, y) => [...row].forEach((ch, x) => ch !== '.' && put(art, x, y, c(SHARK_PAL[ch]))));
  // shading: top of the head catches the lamp
  for (let x = 7; x < 24; x++) if (art.data[(9 * 32 + x) * 4 + 3]) put(art, x, 9, c('#9ABAD8'));
  const big = outline(scaleUp(art, 2), c(K.ink));
  const img = image(72, 72);
  over(img, shadow(big, c(K.ink), 2, 2, 0.45), 3, 3);
  // cigar with an ember at the mouth corner
  rect(img, 62, 40, 8, 3, '#8A5220');
  rect(img, 62, 40, 8, 1, '#B07038');
  rect(img, 69, 40, 2, 3, '#FF7A1A');
  put(img, 70, 40, c('#FFE070'));
  for (const [x, y] of [[69, 36], [68, 33], [70, 30]]) blend(img, x, y, c('#C0B0DC'), 0.6);
  return img;
}

/** Debt meter frame nine-slice (32 × 12, border 5) + skull end cap (12 × 12). Fill uses progressFill('red'). */
export function debtMeter() {
  const img = image(32, 12);
  rect(img, 0, 0, 32, 12, K.ink);
  bevel(img, 1, 1, 30, 10, '#5A6A78', '#22363A');
  rect(img, 2, 2, 28, 8, '#0A0406');
  for (let x = 2; x < 30; x += 4) rect(img, x, 9, 2, 1, '#3A0A10');
  return img;
}
export function debtSkull() {
  const img = image(12, 12);
  disc(img, 6, 5.5, 5.2, c(K.ink));
  disc(img, 6, 5.5, 4.3, c('#E8E0D8'));
  rect(img, 3, 8, 6, 3, K.ink);
  rect(img, 4, 8, 4, 2, '#E8E0D8');
  put(img, 4, 5, c(K.ink));
  put(img, 7, 5, c(K.ink));
  put(img, 4, 4, c('#D83440'));
  put(img, 7, 4, c('#D83440'));
  put(img, 5, 9, c(K.ink));
  return img;
}

/** OVERDUE stamp (72 × 28): a red double-ring rubber stamp, slightly rotated; the word is drawn by code. */
export function overdueStamp() {
  const img = image(72, 28);
  const r = rng(404);
  for (let y = 0; y < 28; y++)
    for (let x = 0; x < 72; x++) {
      const u = x - 36 + (y - 14) * 0.12;
      const v = y - 14 - (x - 36) * 0.06;
      const inner = Math.abs(u) < 31 && Math.abs(v) < 9;
      const outer = Math.abs(u) < 34 && Math.abs(v) < 12;
      const ring = outer && !(Math.abs(u) < 32.5 && Math.abs(v) < 10.5);
      const ring2 = inner && !(Math.abs(u) < 30 && Math.abs(v) < 8);
      if ((ring || ring2) && r() > 0.12) put(img, x, y, c(r() < 0.2 ? '#FF4A50' : '#D83440'));
    }
  return img;
}

/** Contract paper nine-slice (32 × 32, border 8): parchment with a torn top edge and a signature rule. */
export function contractPaper() {
  const img = image(32, 32);
  const r = rng(505);
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const n = r();
      put(img, x, y, c(n < 0.1 ? '#E0D0A8' : n > 0.95 ? '#FFF8E0' : '#F0E4C0'));
    }
  for (let x = 0; x < 32; x++) {
    const t = x % 4 === 1 ? 1 : 0;
    for (let y = 0; y < t; y++) put(img, x, y, [0, 0, 0, 0]);
    put(img, x, t, c('#C8B488'));
  }
  rect(img, 0, 31, 32, 1, '#A89060');
  rect(img, 0, 0, 1, 32, '#C8B488');
  rect(img, 31, 0, 1, 32, '#A89060');
  return img;
}

/** Loan offer card nine-slice (32 × 24, border 8): dark steel with a red side stripe; locked variant is chained. */
export function offerCard(locked = false) {
  const img = plaque(32, 24, locked
    ? { edge: '#2E3A42', face: '#0E1418', light: '#18222A', dark: '#080C10' }
    : { edge: '#5A6A78', face: '#12242A', light: '#1E3840', dark: '#0A1418', studs: '#8A9AA8' });
  rect(img, 2, 2, 2, 20, locked ? '#3A1418' : '#D83440');
  if (locked) for (let x = 6; x < 28; x += 4) put(img, x, 12, c('#5A6A78'));
  return img;
}

// ---- HUD chip counter + toasts -----------------------------------------------------------------------------------------------
/** HUD chip counter pill nine-slice (32 × 16, border 6): dark glass, gold (or golden-hour) edge, chip socket. */
export function chipCounter(golden = false) {
  const img = image(32, 16);
  rect(img, 2, 0, 28, 16, K.ink);
  rect(img, 1, 1, 30, 14, K.ink);
  rect(img, 0, 2, 32, 12, K.ink);
  rect(img, 2, 1, 28, 14, golden ? K.goldShade : '#783CBE');
  rect(img, 1, 2, 30, 12, golden ? K.goldShade : '#783CBE');
  rect(img, 2, 2, 28, 12, golden ? '#3A2408' : '#140822');
  rect(img, 2, 2, 28, 1, golden ? K.gold : '#3A1A5C');
  for (const [x, y] of [[0, 0], [31, 0], [0, 15], [31, 15], [1, 0], [0, 1], [30, 0], [31, 1], [0, 14], [1, 15], [30, 15], [31, 14]]) put(img, x, y, [0, 0, 0, 0]);
  if (golden) for (const [x, y] of [[3, 2], [28, 2]]) put(img, x, y, c(K.white));
  return img;
}

/** HUD chip icon (12 × 12 × 4, V + mcmeta frametime 4): a red chip with a shine sweeping over it. */
export function chipIcon() {
  const frames = [];
  for (let f = 0; f < 4; f++) {
    const img = image(12, 12);
    chipTop(img, 6, 6, 5.5, '#D83440', '#F4ECF8');
    for (let y = 0; y < 12; y++)
      for (let x = 0; x < 12; x++) {
        const d = Math.abs(x + y - (f * 7 - 3));
        if (d < 1.2 && img.data[(y * 12 + x) * 4 + 3]) blend(img, x, y, c(K.white), 0.55);
      }
    frames.push(img);
  }
  return vstrip(frames);
}

/** Delta pill nine-slice (16 × 10, border 4): up (bonus green) / down (chip red). */
export function deltaPill(up) {
  const img = image(16, 10);
  const [edge, face, lt] = up ? ['#1E6A10', '#0E2A08', '#80FF40'] : ['#6A0E1C', '#2A0610', '#FF6E6A'];
  rect(img, 1, 0, 14, 10, K.ink);
  rect(img, 0, 1, 16, 8, K.ink);
  rect(img, 1, 1, 14, 8, edge);
  rect(img, 2, 2, 12, 6, face);
  rect(img, 2, 2, 12, 1, lt);
  return img;
}

/** Toast variants (160 × 32): achievement (gold ribbon), pvp (red/blue split), loan (dark steel + red). */
export function toastVariant(kind) {
  const img = image(160, 32);
  const face = { achievement: '#2A1440', pvp: '#1C1030', loan: '#0E1A1E' }[kind];
  const edge = { achievement: K.gold, pvp: '#BE5AFF', loan: '#D83440' }[kind];
  rect(img, 0, 0, 160, 32, K.ink);
  rect(img, 1, 1, 158, 30, edge);
  rect(img, 2, 2, 156, 28, face);
  rect(img, 2, 2, 156, 1, mixHex(face, '#FFFFFF', 0.15));
  if (kind === 'pvp') {
    rect(img, 1, 1, 79, 1, '#3D6AB0');
    rect(img, 80, 1, 79, 1, '#D83440');
  }
  if (kind === 'achievement')
    for (let x = 30; x < 158; x += 6) put(img, x, 28, c(K.goldShade));
  // icon well
  rect(img, 4, 4, 24, 24, K.ink);
  rect(img, 5, 5, 22, 22, mixHex(face, '#000000', 0.35));
  bevel(img, 4, 4, 24, 24, mixHex(edge, '#000000', 0.3), edge);
  roundCorners(img, 0, 0, 160, 32, 1);
  return img;
}

// ---- backdrops -----------------------------------------------------------------------------------------------------------------
/** Casino lobby: art-deco wall (deep purple, gold sunburst arches), pillars, a chandelier, a carpet edge. */
export function lobbyBackdrop() {
  const img = image(W, H);
  vgrad(img, 0, 0, W, H, c('#1E0C30'), c('#2E1448'), 10);
  // deco fans
  for (let fx = 0; fx < W + 40; fx += 80)
    for (let y = 40; y < 200; y++)
      for (let x = fx - 40; x < fx + 40; x++) {
        const dx = x - fx;
        const dy = 200 - y;
        const d = Math.hypot(dx, dy);
        const a = Math.atan2(dx, dy);
        if (d > 150) continue;
        const ray = Math.abs(Math.sin(a * 9)) < 0.08;
        const arc = Math.abs(d % 30 - 15) < 0.6;
        if ((ray || arc) && x >= 0 && x < W) blend(img, x, y, c(K.goldShade), 0.35);
      }
  // pillars
  for (const x of [30, 350]) {
    rect(img, x, 20, 22, 190, '#2A1440');
    rect(img, x, 20, 3, 190, '#3A1A5C');
    rect(img, x + 19, 20, 3, 190, '#1A0A28');
    for (let y = 30; y < 200; y += 12) rect(img, x + 6, y, 10, 1, '#3A1A5C');
    rect(img, x - 4, 16, 30, 6, K.goldShade);
    rect(img, x - 4, 16, 30, 1, K.gold);
    rect(img, x - 4, 206, 30, 6, K.goldShade);
  }
  // chandelier
  line(img, 200, 0, 200, 14, c(K.goldShade));
  for (let k = -3; k <= 3; k++) {
    const x = 200 + k * 12;
    const y = 24 + Math.abs(k) * -1;
    line(img, 200, 16, x, y, c(K.goldShade));
    bulb(img, x, y + 2, true, '#FFE8A0');
  }
  rect(img, 186, 14, 28, 4, K.gold);
  for (let y = 26; y < 140; y++)
    for (let x = 0; x < W; x++) {
      const d = Math.hypot((x - 200) / 160, (y - 30) / 110);
      if (d < 1 && bayer(x, y) < (1 - d) * 0.25) blend(img, x, y, c('#FFE8A0'), 0.2);
    }
  // carpet
  rect(img, 0, 212, W, 28, '#5A0E24');
  for (let x = 0; x < W; x += 12) for (let y = 216; y < 240; y += 8) put(img, x + ((y / 8) % 2) * 6, y, c(K.goldShade));
  rect(img, 0, 212, W, 2, K.gold);
  return vignette(img, 0.55);
}

/** Loan Shark harbour: black water, fog, a sick moon, pier posts, circling fins, one red lamp. */
export function loanBackdrop() {
  const img = image(W, H);
  vgrad(img, 0, 0, W, 150, c('#04080A'), c('#0E1E22'), 10);
  speckle(img, 0, 0, W, 100, 40, ['#5A7A80', '#2A3A40'], 616, 0.7);
  disc(img, 300, 50, 20, c('#8AA890'), 0.5);
  disc(img, 300, 50, 17, c('#B8C8A0'), 0.6);
  disc(img, 306, 46, 15, c('#0E1E22'), 0.55);
  // water
  for (let y = 150; y < H; y++)
    for (let x = 0; x < W; x++) {
      const wave = Math.sin(x * 0.08 + y * 0.9) + Math.sin(x * 0.031 - y * 0.4);
      put(img, x, y, c(wave > 1.2 ? '#1E3A40' : wave > 0.2 ? '#12262C' : '#0A181C'));
    }
  // moon reflection
  for (let y = 152; y < 230; y += 3) {
    const w = 10 - (y - 150) / 12;
    rect(img, Math.round(300 - w), y, Math.round(w * 2), 1, '#4A6A60');
  }
  // pier
  rect(img, 0, 128, 160, 8, '#2A2016');
  rect(img, 0, 128, 160, 1, '#4A3A28');
  for (const x of [10, 60, 110, 150]) {
    rect(img, x, 128, 6, 70, '#1A140E');
    rect(img, x, 128, 1, 70, '#3A2A1C');
  }
  // lamp post with a red lamp
  rect(img, 120, 70, 3, 58, '#1A1A20');
  rect(img, 114, 64, 15, 6, '#1A1A20');
  disc(img, 121.5, 72, 3, c('#FF3A3A'));
  for (let y = 72; y < 150; y++)
    for (let x = 90; x < 160; x++) {
      const d = Math.hypot((x - 121) / 40, (y - 72) / 80);
      if (d < 1 && bayer(x, y) < (1 - d) * 0.35) blend(img, x, y, c('#FF2A2A'), 0.25);
    }
  // fins
  for (const [fx, fy, s] of [[230, 170, 1], [340, 196, 0.7], [180, 214, 0.55]]) {
    for (let k = 0; k < 14 * s; k++) rect(img, Math.round(fx + k * 0.6), Math.round(fy - k), Math.max(1, Math.round((14 * s - k) * 0.7)), 1, '#3A5070');
    for (let x = -8; x < 22 * s; x += 3) put(img, Math.round(fx + x), fy + 1, c('#5A8A90'));
  }
  // fog bands
  for (const [y0, k] of [[110, 0.18], [140, 0.25]])
    for (let y = y0; y < y0 + 10; y++) for (let x = 0; x < W; x++) if (bayer(x, y) < k) blend(img, x, y, c('#7A9A98'), 0.3);
  return vignette(img, 0.75, 0.5);
}

export { chipSide, iconCell };
