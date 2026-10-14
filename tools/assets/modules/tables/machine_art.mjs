// MT-3 machine cabinet art (16 × 16 block textures, nearest, no baked words): slot cabinets per tier, cashier booth
// (marble counter, panelled wood, brass grille, chip shelves, sign plaque, lamp), shared bulb strip. Pure.
import { R, P, c, image, put, rng } from './draw.mjs';

/** Slot tier palettes: body light → dark, accent (lights), chrome. */
export const SLOT_TIERS = {
  copper: { body: ['#E39A62', '#C97440', '#A65A2E', '#7A3E1E'], accent: '#FFB46A', glow: '#FFD08A', chrome: ['#F2E6DA', '#B8A898'] },
  gold: { body: ['#FFE07A', '#E8B830', '#B88A18', '#7A5A0A'], accent: '#FFF2A8', glow: '#FFF6C0', chrome: ['#FFFFFF', '#C8C0A8'] },
  netherite: { body: ['#5E5468', '#463E50', '#332C3A', '#201A26'], accent: '#C070FF', glow: '#E6B8FF', chrome: ['#D8D0E4', '#8E86A0'] },
};

/** Cabinet side: chrome-edged metal panel with a recessed inner panel and a vertical sheen. */
export function slotSide(tier) {
  const t = SLOT_TIERS[tier];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, t.body[1]);
  R(img, 0, 0, 1, 16, t.chrome[0]);
  R(img, 15, 0, 1, 16, t.chrome[1]);
  R(img, 2, 2, 12, 12, t.body[2]);
  R(img, 2, 2, 12, 1, t.body[3]);
  R(img, 2, 13, 12, 1, t.body[0]);
  for (let y = 3; y < 13; y++) {
    P(img, 4 + ((y >> 1) & 1), y, t.body[1]);
    P(img, 5, y, t.body[0]);
  }
  return img;
}

/** Reel window: bezel, dark glass, three reels with symbols (7, cherry, bar) and a red pay line. */
export function slotReels(tier) {
  const t = SLOT_TIERS[tier];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, t.body[2]);
  R(img, 0, 0, 16, 1, t.chrome[0]);
  R(img, 1, 1, 14, 14, t.body[3]);
  R(img, 2, 2, 12, 12, '#141018');
  const reels = [3, 7, 11];
  for (const x of reels) {
    for (let y = 3; y < 13; y++) {
      const shade = y < 5 || y > 10 ? '#A8A098' : '#F4EEE0';
      R(img, x, y, 3, 1, shade);
    }
  }
  // symbols on the middle row: red 7, cherry, gold bar
  R(img, 3, 7, 3, 1, '#D8343C');
  P(img, 5, 8, '#D8343C');
  P(img, 4, 9, '#D8343C');
  P(img, 7, 8, '#D8343C');
  P(img, 9, 8, '#D8343C');
  P(img, 8, 7, '#3A9A3A');
  R(img, 11, 8, 3, 1, '#E8B830');
  // symbols above / below (dimmer)
  P(img, 4, 4, '#3A6AD8');
  P(img, 8, 11, '#8A3AC8');
  P(img, 12, 4, '#D8343C');
  P(img, 12, 11, '#3A9A3A');
  // pay line
  R(img, 2, 8, 1, 1, '#FF4040');
  R(img, 13, 8, 1, 1, '#FF4040');
  return img;
}

/** Lit topper sign: plaque with a star-and-coin emblem and a bulb border (the element is emissive). */
export function slotTopper(tier) {
  const t = SLOT_TIERS[tier];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, t.body[3]);
  for (let x = 0; x < 16; x += 2) {
    P(img, x, 0, t.glow);
    P(img, x + 1, 15, t.glow);
  }
  for (let y = 0; y < 16; y += 2) {
    P(img, 0, y + 1, t.glow);
    P(img, 15, y, t.glow);
  }
  R(img, 2, 2, 12, 12, '#1A1020');
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
    if (d < 4.5) put(img, x, y, c(d < 3.3 ? t.accent : t.body[1]));
  }
  // star in the coin
  R(img, 7, 5, 2, 6, t.body[3]);
  R(img, 5, 7, 6, 2, t.body[3]);
  return img;
}

/** Lower cabinet front: speaker grille, tier gem plate, coin tray with coins. */
export function slotBase(tier) {
  const t = SLOT_TIERS[tier];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, t.body[1]);
  R(img, 0, 0, 16, 1, t.chrome[0]);
  R(img, 1, 1, 14, 6, t.body[2]);
  for (let x = 2; x < 14; x += 2) for (let y = 2; y < 6; y += 2) P(img, x, y, t.body[3]);
  R(img, 6, 8, 4, 3, t.chrome[0]);
  R(img, 7, 9, 2, 1, t.accent);
  R(img, 2, 12, 12, 3, '#120C08');
  R(img, 3, 13, 2, 1, '#FFD640');
  R(img, 6, 14, 2, 1, '#E8B830');
  R(img, 10, 13, 3, 1, '#FFD640');
  R(img, 0, 15, 16, 1, t.body[3]);
  return img;
}

/** Button deck top: dark panel with lit buttons (spin + bet). */
export function slotDeck(tier) {
  const t = SLOT_TIERS[tier];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, '#1A1418');
  R(img, 0, 0, 16, 1, t.chrome[0]);
  for (const [x, col] of [[2, '#3A9AFF'], [5, '#FFD640'], [8, '#5FE07A'], [11, '#FF4848']]) R(img, x, 6, 3, 3, col);
  return img;
}

/** Bulb strip (emissive frames): warm bulbs on a brass rail. */
export function bulbs() {
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, '#7A5410');
  for (let y = 0; y < 16; y += 4) for (let x = 0; x < 16; x += 4) {
    R(img, x + 1, y + 1, 2, 2, '#FFF4B0');
    P(img, x + 1, y + 1, '#FFFFFF');
  }
  return img;
}

// ---- cashier booth -------------------------------------------------------------------------------------------------

/** Marble counter top: cream with grey-green veins (seamless). */
export function marble(dark) {
  const img = image(16, 16);
  const base = dark ? ['#3A2E34', '#4A3C44', '#2A2026'] : ['#ECE6DA', '#DAD2C4', '#B8C0B0'];
  R(img, 0, 0, 16, 16, base[0]);
  const r = rng(dark ? 77 : 41);
  for (let i = 0; i < 40; i++) P(img, Math.floor(r() * 16), Math.floor(r() * 16), base[1]);
  for (let x = 0; x < 16; x++) {
    const y = (Math.round(6 + 3 * Math.sin((x / 16) * Math.PI * 2)) + 16) % 16;
    P(img, x, y, base[2]);
    P(img, x, (y + 9) % 16, base[2]);
  }
  return img;
}

/** Raised-panel wood front (counter / wall): frame, inset panel, highlight. */
export function panel(wood) {
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, wood[1]);
  R(img, 0, 0, 16, 1, wood[0]);
  R(img, 0, 15, 16, 1, wood[3]);
  R(img, 2, 2, 12, 12, wood[2]);
  R(img, 3, 3, 10, 10, wood[1]);
  R(img, 3, 3, 10, 1, wood[0]);
  R(img, 3, 12, 10, 1, wood[3]);
  for (let y = 4; y < 12; y += 3) R(img, 4, y, 8, 1, wood[2]);
  return img;
}

/** Brass grille: vertical bars with cross rails, transparent between (cutout). */
export function grille() {
  const img = image(16, 16);
  for (let x = 1; x < 16; x += 3) {
    R(img, x, 0, 1, 16, '#E8C050');
    R(img, x + 1, 0, 1, 16, '#9A7418');
  }
  R(img, 0, 0, 16, 1, '#E8C050');
  R(img, 0, 7, 16, 1, '#C89A28');
  R(img, 0, 15, 16, 1, '#9A7418');
  return img;
}

/** Back shelves behind the grille: chip racks and stacked coins. */
export function shelves() {
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, '#2A1A12');
  const chips = ['#D23A3A', '#2E7AD8', '#2E9A48', '#1A1A1A', '#8A3AC8', '#E8B030', '#ECE6DA'];
  for (const y of [3, 9]) {
    R(img, 0, y + 4, 16, 1, '#5A3A22');
    for (let x = 1, i = 0; x < 15; x += 2, i++) R(img, x, y + 1 + (i % 2), 1, 3 - (i % 2), chips[(i + y) % chips.length]);
  }
  return img;
}

/** Sign plaque behind the localized word (drawn by the cashier sign renderer): dark board, brass frame, coin icons. */
export function signPlaque() {
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, '#B88A18');
  R(img, 1, 1, 14, 14, '#1A1020');
  R(img, 1, 1, 14, 1, '#FFE07A');
  return img;
}

/** Warm lamp glass (emissive). */
export function lamp() {
  const img = image(16, 16);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
    put(img, x, y, c(d < 4 ? '#FFFBE0' : d < 7 ? '#FFE48A' : '#F0B840'));
  }
  return img;
}
