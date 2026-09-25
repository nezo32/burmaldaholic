// Card-table UI sprites (visual/cards.md §6): seat plates, bot avatars and level badges, plaques and the control
// console per theme, themed buttons, stamps, tags, badges, card glow / shimmer / curl / shadow, action icons, emotes,
// and the card-suit particle frames. No words anywhere: code draws the translated text on the plates.
import { B, C, P, R, bevel, box, disc, fromRows, image, mixHex, over, ring, rrect, scaleUp } from './px.mjs';
import { SUITS_4C, SUIT_ORDER, THEMES } from './theme.mjs';
import { pip } from './suits.mjs';
import { SIZES } from './faces.mjs';

const INK = '#180A28';

// ---- seat plates (nine-slice 40 × 22, border 6) -------------------------------------------------------------------
export const PLATE_STATES = ['normal', 'active', 'me', 'folded', 'winner'];
export function seatPlate(state) {
  const img = image(40, 22);
  const fill = state === 'folded' ? '#1E1428' : '#26103C';
  const rim = { normal: '#783CBE', active: '#FFD640', me: '#FFD640', folded: '#4A3060', winner: '#FFF4B0' }[state];
  const rim2 = { normal: '#4A2474', active: '#B07010', me: '#B07010', folded: '#2A1640', winner: '#FFD640' }[state];
  rrect(img, 0, 0, 40, 22, 2, INK);
  rrect(img, 1, 1, 38, 20, 1, rim);
  R(img, 2, 2, 36, 18, rim2);
  R(img, 3, 3, 34, 16, fill);
  R(img, 3, 3, 34, 1, mixHex(fill, '#FFFFFF', 0.12));
  R(img, 3, 18, 34, 1, mixHex(fill, '#000000', 0.3));
  if (state === 'me') {
    R(img, 4, 4, 32, 1, '#FFD640');
    R(img, 4, 17, 32, 1, '#B07010');
  }
  if (state === 'winner') for (const [x, y] of [[3, 3], [36, 3], [3, 18], [36, 18]]) P(img, x, y, '#FFFFFF');
  if (state === 'active') for (let x = 6; x < 34; x += 4) P(img, x, 1, '#FFF4B0');
  // rivets
  if (state !== 'folded') for (const [x, y] of [[4, 4], [35, 4], [4, 17], [35, 17]]) if (state !== 'me') P(img, x, y, '#B07010');
  return img;
}

/** Avatar frame 20 × 20 (the player's head is drawn by code from the skin at 16 × 16 inside). */
export function avatarFrame(gold = false) {
  const img = image(20, 20);
  R(img, 0, 0, 20, 20, INK);
  R(img, 1, 1, 18, 18, gold ? '#FFD640' : '#783CBE');
  R(img, 2, 2, 16, 16, '#0A0412');
  P(img, 1, 1, gold ? '#FFF4B0' : '#BE5AFF');
  P(img, 18, 18, gold ? '#B07010' : '#4A2474');
  return img;
}

// ---- bot avatars (8 × 8 mob faces, scaled ×2 = 16 × 16, drawn inside the avatar frame) ---------------------------
const FACES = {
  villager: {
    rows: ['hhhhhhhh', 'hsssssss', 'sbbbbbbs', 'sWgssgWs', 'sssnnsss', 'sssnnsss', 'ssSnnSss', 'sSSnnSSs'],
    legend: { h: '#6A4A2A', s: '#C89A6A', S: '#A87A4A', b: '#4A2A1A', W: '#FFFFFF', g: '#2E9A4A', n: '#B07A56' },
  },
  witch: {
    rows: ['pppppppp', 'PPPPPPPP', 'sbbbbbbs', 'sWgssgWs', 'sssnnsss', 'ssswnsss', 'ssSnnSss', 'sSSnnSSs'],
    legend: { p: '#3A2450', P: '#5A3A70', s: '#A8B88A', S: '#88986A', b: '#2A3A1A', W: '#FFFFFF', g: '#8A2ABE', n: '#98A87A', w: '#5A7A3A' },
  },
  piglin: {
    rows: ['pppppppp', 'pPpppPpp', 'pwkppkwp', 'ppPPPPpp', 'pPNnnNPp', 'pPnnnnPp', 'pgPPPPgp', 'pgppppgp'],
    legend: { p: '#E8A0A0', P: '#C87878', w: '#FFFFFF', k: '#180A28', N: '#8C4A5A', n: '#F0B8B0', g: '#FFD640' },
  },
  brute: {
    rows: ['GGGGGGGG', 'GgggggGG', 'pwkppkwp', 'ppPPPPpp', 'pPNnnNPp', 'pPnnnnPp', 'pgPPPPgp', 'pgppppgp'],
    legend: { G: '#B07010', g: '#FFD640', p: '#C88080', P: '#A05A5A', w: '#FFFFFF', k: '#180A28', N: '#6A2A3A', n: '#D89898' },
  },
  enderman: {
    rows: ['kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'lPPkkPPl', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk'],
    legend: { k: '#161020', P: '#D696FF', l: '#BE5AFF' },
  },
  shulker: {
    rows: ['pppppppp', 'pPPPPPPp', 'pPPPPPPp', 'dddddddd', 'yyyyyyyy', 'ywkyykwy', 'yyyyyyyy', 'pppppppp'],
    legend: { p: '#8A5A9A', P: '#A97AA9', d: '#4E2E52', y: '#E8E4A8', w: '#FFFFFF', k: '#180A28' },
  },
};
export const BOT_AVATARS = Object.keys(FACES);
export const botAvatar = (name) => scaleUp(fromRows(FACES[name].rows, FACES[name].legend), 2);

/**
 * Bot level badges 11 × 11, shape + pip count carry the level without colour or letters:
 * EASY green disc · 1 pip, NORMAL amber rounded square · 2 pips, HARD red shield-diamond · 3 pips.
 */
export const BADGE_LEVELS = ['easy', 'normal', 'hard'];
export function botBadge(level) {
  const img = image(11, 11);
  const [c, d] = { easy: ['#5CC24A', '#2A6A20'], normal: ['#F0B830', '#8A5A10'], hard: ['#E04040', '#7A1420'] }[level];
  if (level === 'easy') {
    disc(img, 5.5, 5.5, 5.4, C(INK));
    disc(img, 5.5, 5.5, 4.5, C(d));
    disc(img, 5.5, 5.5, 3.6, C(c));
  } else if (level === 'normal') {
    rrect(img, 0, 0, 11, 11, 2, INK);
    rrect(img, 1, 1, 9, 9, 1, d);
    R(img, 2, 2, 7, 7, c);
  } else {
    for (let y = 0; y < 11; y++)
      for (let x = 0; x < 11; x++) {
        const m = Math.abs(x - 5) + Math.abs(y - 5);
        if (m <= 5) P(img, x, y, m === 5 ? INK : m === 4 ? d : c);
      }
  }
  const pips = { easy: [[5, 5]], normal: [[4, 5], [6, 5]], hard: [[3, 5], [5, 5], [7, 5]] }[level];
  for (const [x, y] of pips) {
    P(img, x, y, '#FFFFFF');
    P(img, x, y + 1, mixHex(c, '#000000', 0.5));
  }
  return img;
}

/** Thinking dots, 3 frames of 13 × 5 (strip, frametime 10). */
export function thinking(f) {
  const img = image(13, 5);
  for (let i = 0; i < 3; i++) {
    const on = i === f;
    const x = 1 + i * 4;
    R(img, x, on ? 0 : 1, 3, 3, on ? '#FFFFFF' : '#C0B0DC');
    R(img, x, on ? 3 : 4, 3, 1, INK);
  }
  return img;
}

// ---- plaques, console, stamps, tags, badges ----------------------------------------------------------------------
/** Title / celebration plaque per theme, nine-slice 48 × 20 (border 7). */
export function plaque(theme) {
  const t = THEMES[theme];
  const img = image(48, 20);
  rrect(img, 0, 0, 48, 20, 2, INK);
  rrect(img, 1, 1, 46, 18, 1, t.trim);
  R(img, 2, 2, 44, 16, t.trimShade);
  R(img, 3, 3, 42, 14, t.woodDark);
  R(img, 4, 4, 40, 12, t.wood);
  R(img, 4, 4, 40, 1, t.woodLight);
  R(img, 4, 15, 40, 1, t.woodDark);
  R(img, 2, 2, 44, 1, mixHex(t.trim, '#FFFFFF', 0.5));
  for (const [x, y] of [[3, 3], [44, 3], [3, 16], [44, 16]]) P(img, x, y, '#FFFFFF');
  return img;
}
/** Bottom control console per theme, nine-slice 48 × 28 (border 7): a rail-coloured bar with a gold top lip. */
export function consoleBar(theme) {
  const t = THEMES[theme];
  const img = image(48, 28);
  R(img, 0, 0, 48, 28, INK);
  R(img, 0, 1, 48, 1, t.trim);
  R(img, 0, 2, 48, 1, t.trimShade);
  R(img, 0, 3, 48, 24, t.padDark);
  R(img, 0, 3, 48, 1, t.pad);
  for (let x = 0; x < 48; x++) for (let y = 5; y < 26; y++) if ((x * 5 + y * 3) % 17 === 0) P(img, x, y, t.pad);
  R(img, 0, 27, 48, 1, INK);
  return img;
}
/** Stamp nine-slice 32 × 16 (border 5), baked in its colour: gold (BLACKJACK!, NATURAL, BANCO!), red (BUST), green (PUSH/TIE PAYS), ink (ALL-IN on red uses red). */
export const STAMP_KINDS = { gold: ['#FFD640', '#B07010', '#FFF4B0'], red: ['#D83440', '#8C1834', '#FF6E6A'], green: ['#2FA64A', '#14602A', '#80FF40'], violet: ['#BE5AFF', '#6A2A9A', '#D696FF'] };
export function stamp(kind) {
  const [c, d, l] = STAMP_KINDS[kind];
  const img = image(32, 16);
  rrect(img, 0, 0, 32, 16, 2, d);
  rrect(img, 1, 1, 30, 14, 1, c);
  box(img, 2, 2, 28, 12, l);
  R(img, 3, 3, 26, 10, c);
  R(img, 3, 12, 26, 1, mixHex(c, d, 0.5));
  return img;
}
/** Action tag bubble, nine-slice 24 × 11 (border 4): bone on ink. The tail is `tagTail` (5 × 3), drawn under the centre. */
export function tag() {
  const img = image(24, 11);
  rrect(img, 0, 0, 24, 11, 2, INK);
  rrect(img, 1, 1, 22, 9, 1, '#F4ECF8');
  R(img, 2, 9, 20, 1, '#C0B0DC');
  return img;
}
export function tagTail() {
  return fromRows(['iwwwi', '.iwi.', '..i..'], { i: INK, w: '#C0B0DC' });
}
/** Hand-total badge 14 × 11 (border 3): bone on ink. */
export function totalBadge(gold = false) {
  const img = image(14, 11);
  rrect(img, 0, 0, 14, 11, 1, gold ? '#B07010' : INK);
  R(img, 1, 1, 12, 9, gold ? '#FFD640' : '#26103C');
  R(img, 1, 1, 12, 1, gold ? '#FFF4B0' : '#4A2474');
  return img;
}
/** Bead-road / paytable panel nine-slice 24 × 24 (border 4): a dark inset with a gold line. */
export function roadPanel() {
  const img = image(24, 24);
  rrect(img, 0, 0, 24, 24, 1, INK);
  R(img, 1, 1, 22, 22, '#B07010');
  R(img, 2, 2, 20, 20, '#140822');
  R(img, 2, 2, 20, 1, '#0A0412');
  return img;
}

// ---- card fx --------------------------------------------------------------------------------------------------
/** Gold glow ring around an L card, 4 frames of 43 × 55 (K9). */
export function cardGlow(f, size = 'l') {
  const [w, h] = SIZES[size];
  const img = image(w + 6, h + 6);
  const k = [0.4, 0.65, 0.9, 0.65][f];
  for (let i = 0; i < 3; i++) {
    const a = k * (1 - i / 3);
    const c = C(i === 0 ? '#FFF4B0' : '#FFD640');
    for (let x = 2 - i; x < w + 4 + i; x++) {
      B(img, x + 1, 2 - i, c, a);
      B(img, x + 1, h + 3 + i, c, a);
    }
    for (let y = 3 - i; y < h + 3 + i; y++) {
      B(img, 2 - i, y, c, a);
      B(img, w + 3 + i, y, c, a);
    }
  }
  return img;
}
/** Shimmer band 10 × 49 (K8): a diagonal white band, alpha graded. */
export function shimmer() {
  const img = image(10, 49);
  for (let y = 0; y < 49; y++)
    for (let x = 0; x < 10; x++) {
      const d = Math.abs(x - 5 + (y - 24) * 0.15);
      if (d < 4.5) B(img, x, y, '#FFFFFF', d < 1.5 ? 0.55 : d < 3 ? 0.3 : 0.14);
    }
  return img;
}
/** Squeeze curl (K3): paper underside gradient with a crease, 37 × 6 (L) / 21 × 4 (M). */
export function curl(w, h) {
  const img = image(w, h);
  for (let y = 0; y < h; y++) R(img, 1, y, w - 2, 1, mixHex('#E2D8C4', '#FFFFFF', y / (h - 1)));
  R(img, 1, h - 1, w - 2, 1, '#8A7A62');
  R(img, 0, 0, 1, h, INK);
  R(img, w - 1, 0, 1, h, INK);
  return img;
}
/** Soft card shadow (drawn at +2, +3 under a card): L 41 × 53, M 25 × 33. */
export function cardShadow(size) {
  const [w, h] = SIZES[size];
  const img = image(w + 4, h + 4);
  rrect(img, 2, 2, w, h, 2, '#0A0412', 0.35);
  rrect(img, 1, 1, w + 2, h + 2, 2, '#0A0412', 0.12);
  return img;
}
/** Reduced-motion squeeze progress bar 24 × 3 (fill drawn by code: sub-UV of the lower row). */
export function progress() {
  const img = image(24, 3);
  R(img, 0, 0, 24, 3, INK);
  R(img, 1, 1, 22, 1, '#4A2474');
  return img;
}

// ---- themed secondary buttons (nine-slice 64 × 20, border 4); primary/danger reuse core families ---------------------
export function tableButton(theme, state) {
  const t = THEMES[theme];
  const img = image(64, 20);
  const dis = state === 'disabled';
  const hi = state === 'highlighted';
  const fill = dis ? mixHex(t.padDark, '#2A1640', 0.5) : hi ? t.padLight : t.pad;
  rrect(img, 0, 0, 64, 20, 2, INK);
  rrect(img, 1, 1, 62, 18, 1, dis ? '#4A3060' : hi ? '#FFF4B0' : t.trim);
  R(img, 2, 2, 60, 16, fill);
  if (!dis) {
    R(img, 2, 2, 60, 1, mixHex(fill, '#FFFFFF', 0.3));
    R(img, 2, 15, 60, 3, mixHex(fill, '#000000', 0.3));
    R(img, 2, 17, 60, 1, t.trimShade);
  }
  return img;
}

// ---- action icons (12 × 12; drawn left of the button label) ---------------------
const L = { i: INK, w: '#F4ECF8', W: '#C0B0DC', g: '#80FF40', G: '#2E9A2E', y: '#FFD640', Y: '#B07010', r: '#D83440', R: '#8C1834', b: '#3A6BE0', B: '#1E3A8A', s: '#F0C49A', S: '#C8906A', p: '#BE5AFF', t: '#2FA64A', k: '#26103C', P: '#6A2A9A' };
export const ICONS = {
  hit: ['............', 'iiiiii......', 'iwwwwi..ii..', 'iwwwwi..igi.', 'iwwwwi.iigii', 'iwwwwiiggggi', 'iwwwwi.iigii', 'iwwwwi..igi.', 'iwwwwi...i..', 'iWWWWi......', 'iiiiii......', '............'],
  stand: ['....i.i.....', '...isisi.i..', '...isisiisi.', '.i.isisisi..', 'isiisisisi..', 'ississsssi..', '.isssssssi..', '.isssssssi..', '..isssssSi..', '..issssSSi..', '...iiiiii...', '............'],
  double: ['.....i......', '....iyi.....', '...iyyyi....', '....iyi.....', '.iiiiiiiii..', 'irrwrrwrri..', 'iRRRRRRRRi..', 'irrwrrwrri..', 'iRRRRRRRRi..', 'irrwrrwrri..', '.iiiiiiiii..', '............'],
  split: ['............', 'iiiii..iiiii', 'iwwwi..iwwwi', 'iwwwi..iwwwi', 'iwwwi..iwwwi', 'iwwwi..iwwwi', 'iWWWi..iWWWi', 'iiiii..iiiii', '............', '.iy......yi.', 'iyyyyyyyyyyi', '.iy......yi.'],
  insurance: ['............', '..iiiiiiii..', '.ibbbbbbbbi.', '.ibbwbbbbbi.', '.ibbbbbbbbi.', '.ibbbbbbbbi.', '..ibbbbbbi..', '..iBbbbbBi..', '...iBbbBi...', '....iBBi....', '.....ii.....', '............'],
  surrender: ['.i..........', '.iiiiiiii...', '.iwwwwwwwi..', '.iwwwwwwwwi.', '.iwwwwwwwi..', '.iWWWWWWi...', '.iiiiiiii...', '.i..........', '.i..........', '.i..........', 'iii.........', '............'],
  fold: ['............', '....iiiiii..', '...ikBkBkBi.', '...iBkBkBki.', '..ikBkBkBi..', '..iBkBkBki..', '.ikBkBkBi...', '.iiiiiiii...', '.......ii...', '......iRri..', '.....iRrrri.', '......iiii..'],
  check: ['............', '..........ii', '.........igi', '........igi.', '.ii....igi..', 'igii..igi...', '.igii.gi....', '..igiigi....', '...iggi.....', '....ii......', '............', '............'],
  call: ['............', '..iiiiiiii..', '.irrwrrwrri.', '.iRRRRRRRRi.', '..iiiiiiii..', '............', '.iiiiiiiiii.', '.iwwwwwwwwi.', '.iiiiiiiiii.', '.iwwwwwwwwi.', '.iiiiiiiiii.', '............'],
  raise: ['.....ii.....', '....iggi....', '...iggggi...', '..iiiggiii..', '....iggi....', '.iiiiiiiiii.', 'irrwrrwrrwri', 'iRRRRRRRRRRi', 'irrwrrwrrwri', 'iRRRRRRRRRRi', '.iiiiiiiiii.', '............'],
  all_in: ['..ii....ii..', '.iyyi..iyyi.', 'iyyyyiiyyyyi', '..iyi..iyi..', '.iiiiiiiiii.', 'ippwppwppwpi', 'iPPPPPPPPPPi', 'ippwppwppwpi', 'iPPPPPPPPPPi', 'ippwppwppwpi', '.iiiiiiiiii.', '............'],
  deal: ['............', '......iiiiii', '.....iwwwwwi', 'W...iwwwwwi.', '...iwwwwwi..', 'WW.iwwwwwi..', '..iwwwwwi...', 'W.iwwwwwi...', '..iWWWWWi...', '..iiiiiii...', '............', '............'],
  rebet: ['............', '...iiiiii...', '..iyyyyyyi..', '.iyi....iyi.', '.iyi.....i..', '.iyi..iiiii.', '.iyi...iyyi.', '..iyi..iyyi.', '..iyyiiyyyi.', '...iyyyyi.i.', '....iiii....', '............'],
  clear: ['............', '.ii......ii.', 'irri....irri', '.irri..irri.', '..irriirri..', '...irrrri...', '...irrrri...', '..irriirri..', '.irri..irri.', 'irri....irri', '.ii......ii.', '............'],
  play: ['............', '.iiiiiii....', 'irrwrrwri...', 'iRRRRRRRi.i.', 'irrwrrwri.ii', '.iiiiiii.igi', '........iggi', '.iiiiiiiiggi', 'irrwrrwrigi.', 'iRRRRRRRii..', '.iiiiiiii...', '............'],
  player: ['............', '...iiiiii...', '..ibbbbbbi..', '.ibbiiiibbi.', '.ibi....ibi.', '.ibi....ibi.', '.ibi....ibi.', '.ibi....ibi.', '.ibbiiiibbi.', '..ibbbbbbi..', '...iiiiii...', '............'],
  banker: ['............', '.i...ii...i.', '.ii.iyyi.ii.', '.iyiiyyiiyi.', '.iyyyyyyyyi.', '.iyyryyryyi.', '.iyyyyyyyyi.', '.iYYYYYYYYi.', '.iiiiiiiiii.', '.irrrrrrrri.', '.iiiiiiiiii.', '............'],
  tie: ['............', '............', 'iiiiiiiiiiii', 'itttttttttti', 'iiiiiiiiiiii', '............', '............', 'iiiiiiiiiiii', 'itttttttttti', 'iiiiiiiiiiii', '............', '............'],
  pair: ['iiiiii......', 'iwwwwi......', 'iwwiiiiii...', 'iwwiwwwwi...', 'iwwiwwwwi...', 'iwwiwwwwi...', 'iiiiwwwwi...', '...iwwwwi...', '...iWWWWi...', '...iiiiii...', '............', '............'],
  banco: ['...iiiiii...', '..iyyyyyyi..', '.iyyiyyiyyi.', '.iyyyyyyyyi.', '..iiiiiiii..', '.iyyyyyyyyi.', 'iYYYYYYYYYYi', 'iyyyyyyyyyyi', 'iYYYYYYYYYYi', 'iyyyyyyyyyyi', '.iiiiiiiiii.', '............'],
  take_bank: ['............', 'iiiiiiiiiii.', 'iwwwwwwwwwi.', 'iiiiiiiiiiii', 'iYYYYYYYYYYi', 'iyyyyyyyyyyi', 'iyiyyyyyyiyi', 'iyyyyyyyyyyi', 'iYYYYYYYYYYi', 'iiiiiiiiiiii', '............', '............'],
  paytable: ['.iiiiiiiiii.', '.iwwwwwwwwi.', '.iwiiwiiiwi.', '.iwwwwwwwwi.', '.iwiiiwiiwi.', '.iwwwwwwwwi.', '.iwiiwiiiwi.', '.iwwwwwwwwi.', '.iwiiiiwiwi.', '.iwwwwwwwwi.', '.iiiiiiiiii.', '............'],
  leave: ['iiiiiii.....', 'iYYYYYi.....', 'iYyyyYi.....', 'iYyyyYi..i..', 'iYyyyYi..ii.', 'iYyyiiiiigi.', 'iYyyYiggggi.', 'iYyyyiiiigi.', 'iYyyyYi..ii.', 'iYYYYYi..i..', 'iiiiiii.....', '............'],
};
export const ICON_NAMES = Object.keys(ICONS);
export function icon(name) {
  const rows = ICONS[name].map((r) => (r.length >= 12 ? r.slice(0, 12) : r.padEnd(12, '.')));
  return fromRows(rows, L);
}

// ---- emotes (11 × 11) ---------------------------------------------------------------------------------------------
export function emote(kind) {
  const img = image(11, 11);
  disc(img, 5.5, 5.5, 5.4, C(INK));
  disc(img, 5.5, 5.5, 4.5, C(kind === 'happy' ? '#FFD640' : '#E07050'));
  P(img, 3, 3, '#FFF4B0');
  R(img, 3, 4, 1, 2, INK);
  R(img, 7, 4, 1, 2, INK);
  if (kind === 'happy') {
    P(img, 2, 6, INK);
    R(img, 3, 7, 5, 1, INK);
    P(img, 8, 6, INK);
  } else {
    R(img, 3, 8, 5, 1, INK);
    P(img, 2, 9, INK);
    P(img, 8, 9, INK);
    P(img, 2, 3, INK);
    P(img, 8, 3, INK);
  }
  return img;
}

// ---- particles: tumbling suits, 4 suits × 2 frames (8 × 8): upright, turned edge-on -------------------------------
export function suitParticle(suitIdx, frame) {
  const suit = SUIT_ORDER[suitIdx];
  const img = image(8, 8);
  const p = pip(suit, 7, SUITS_4C[suit], { outlineC: undefined });
  if (frame === 0) over(img, p, 0, 0);
  else {
    // edge-on: squash to 3 px wide
    for (let y = 0; y < 7; y++) for (let x = 0; x < 3; x++) {
      const sx = Math.min(6, Math.round(x * 3));
      const o = (y * p.w + sx) * 4;
      if (p.data[o + 3]) P(img, 2 + x, y, [p.data[o], p.data[o + 1], p.data[o + 2], 255]);
    }
  }
  ring(img, 3.5, 3.5, 0, 0, C('#FFFFFF'));
  return img;
}

export { bevel };
