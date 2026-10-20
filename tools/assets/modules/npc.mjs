// Casino NPC skins (DL-SPEC §3, owner playtest N1/N2) → Java only, worldgen-owned (`textures/entity/worldgen/…`,
// gradle checkAssetOwnership):
//   dealer/human_0..5     player model, wide arms: house dealers of the Overworld
//   dealer/piglin_0..3    adult piglin: Nether house dealers and the nether cashier / merchant
//   dealer/pit_boss       piglin (rendered as a brute): the Parlor's pit boss
//   dealer/ender_0..2     enderman (64 × 32): End house dealers, croupier and merchant
//   croupier/villager_0..3 villager model: croupiers and Overworld merchants
// and the house dealer's spawn egg (`item/worldgen/house_dealer_spawn_egg`). Helpers in ./npc/ (uv, palette, paint).
// The existing worldgen/{croupier,piglin_dealer,shulker_croupier}.png are not touched.
import { JAVA_ASSETS, png } from '../lib/emit.mjs';
import { ellipse, image, setPx } from '../lib/grid.mjs';
import { shade } from '../lib/palette.mjs';
import { canvas } from './npc/paint.mjs';
import {
  CLOTH, ENDER, GOLD, GOLD_DARK, HAIR, PIGLIN_SKIN, PIGLIN_SNOUT, SHOE, SKIN, VILLAGER_NOSE, VILLAGER_SKIN,
} from './npc/palette.mjs';
import { ENDERMAN, PIGLIN, PLAYER, VILLAGER } from './npc/uv.mjs';

const DIR = `${JAVA_ASSETS}/textures/entity/worldgen`;
const EGG = `${JAVA_ASSETS}/textures/item/worldgen/house_dealer_spawn_egg.png`;
const SIDES4 = ['right', 'front', 'left', 'back'];

// ---- shared layers ------------------------------------------------------------------------------------------------

/** Shirt front with a vest / jacket either side (cols 0..1 and w-2..w-1), buttons, and the jacket round the back. */
function torso(c, box, { shirt, coat, tie, tieKind = 'bow', buttons = coat, open = 2 }) {
  const { w, h } = c.size(box, 'front');
  c.fill(box, coat);
  c.face(box, 'front', (x, y) => {
    const centre = x >= open && x < w - open;
    if (!centre) return coat;
    if (tieKind === 'bow' && y <= 1 && x >= w / 2 - 2 && x < w / 2 + 2) return y === 0 && (x === w / 2 - 2 || x === w / 2 + 1) ? shirt : tie;
    if (tieKind === 'long' && x >= w / 2 - 1 && x < w / 2 + 1 && y < h - 3) return y === 0 ? shade(tie, 0.8) : tie;
    if (buttons && (x === w / 2 - 1 || x === w / 2) && y >= 4 && y % 3 === 1) return buttons;
    return shirt;
  });
  // the shirt collar shows over the coat at the shoulders
  c.face(box, 'top', (x) => (x >= open && x < w - open ? shirt : null));
}

/** Arm: sleeve colour, a cuff, the hand (bottom rows + bottom face) in skin. */
function arm(c, box, { sleeve, cuff, skin, band }) {
  const { h } = c.size(box, 'front');
  c.fill(box, sleeve);
  c.band(box, h - 3, h - 1, skin);
  c.face(box, 'bottom', skin);
  if (cuff) c.band(box, h - 4, h - 4, cuff);
  if (band) c.band(box, 3, 3, band);
}

/** Leg: trousers with a darker crease on the front, shoes on the bottom rows. */
function leg(c, box, { trousers, shoe = SHOE }) {
  const { w, h } = c.size(box, 'front');
  c.fill(box, trousers);
  c.face(box, 'front', (x, y) => (x === Math.floor(w / 2) && y < h - 2 ? shade(trousers, 0.85) : null));
  c.band(box, h - 2, h - 1, shoe);
  c.face(box, 'bottom', shoe);
}

/** Two eyes (white + iris), brows and a mouth on a head's front face. */
function face(c, box, { eyeY, skin, iris = '#3A2A1E', brow, mouth, lashes, xs }) {
  const { w } = c.size(box, 'front');
  const [l, r] = xs ?? [Math.floor(w / 2) - 3, Math.floor(w / 2) + 1];
  for (const x0 of [l, r]) {
    c.put(box, 'front', x0, eyeY, x0 === l ? '#F4F4F0' : iris, true);
    c.put(box, 'front', x0 + 1, eyeY, x0 === l ? iris : '#F4F4F0', true);
    if (brow) {
      c.put(box, 'front', x0, eyeY - 1, brow);
      c.put(box, 'front', x0 + 1, eyeY - 1, brow);
    }
    if (lashes) c.put(box, 'front', x0 === l ? x0 - 1 : x0 + 2, eyeY, lashes, true);
  }
  if (mouth) {
    c.put(box, 'front', Math.floor(w / 2) - 1, eyeY + 2, mouth);
    c.put(box, 'front', Math.floor(w / 2), eyeY + 2, mouth);
  }
  c.put(box, 'front', Math.floor(w / 2) - 1, eyeY + 1, shade(skin, 0.86));
  c.put(box, 'front', Math.floor(w / 2), eyeY + 1, shade(skin, 0.86));
}

// ---- human dealers (player model) ---------------------------------------------------------------------------------

export const HUMANS = [
  { name: 'tux', skin: SKIN.light, hair: HAIR.black, coat: CLOTH.black, sleeves: 'coat', tie: CLOTH.black, trousers: CLOTH.black },
  { name: 'red_vest', skin: SKIN.medium, hair: HAIR.brown, coat: CLOTH.red, sleeves: 'shirt', band: CLOTH.red, tie: CLOTH.black, trousers: CLOTH.black },
  { name: 'green_vest', skin: SKIN.dark, hair: HAIR.black, coat: CLOTH.green, sleeves: 'shirt', band: CLOTH.black, tie: GOLD_DARK, trousers: CLOTH.charcoal },
  { name: 'burgundy_suit', skin: SKIN.light, hair: HAIR.auburn, coat: CLOTH.burgundy, sleeves: 'coat', tie: CLOTH.black, tieKind: 'long', trousers: shade(CLOTH.burgundy, 0.7) },
  { name: 'bun', skin: SKIN.medium, hair: HAIR.brown, coat: CLOTH.black, sleeves: 'shirt', band: CLOTH.red, tie: CLOTH.red, trousers: CLOTH.black, woman: true },
  { name: 'veteran', skin: SKIN.dark, hair: HAIR.grey, coat: CLOTH.charcoal, sleeves: 'coat', tie: CLOTH.navy, tieKind: 'long', trousers: CLOTH.charcoal, moustache: true },
];

export function human(look) {
  const c = canvas(PLAYER);
  const shirt = CLOTH.white;
  // base skin -> hair
  c.fill('head', look.skin);
  c.face('head', 'top', look.hair);
  c.face('head', 'back', (x, y) => (y <= (look.woman ? 6 : 4) ? look.hair : null));
  for (const s of ['right', 'left']) c.face('head', s, (x, y) => (y <= (look.woman ? 4 : 1) || (s === 'right' ? x <= 1 : x >= 6) && y <= 3 ? look.hair : null));
  c.face('head', 'front', (x, y) => (y === 0 || y === 1 && (x === 0 || x === 7 || look.woman) ? look.hair : null));
  face(c, 'head', {
    eyeY: 4, skin: look.skin, brow: shade(look.hair, 0.9), mouth: look.woman ? '#B0303A' : shade(look.skin, 0.7),
    lashes: look.woman ? HAIR.black : null,
  });
  if (look.moustache) for (let x = 2; x <= 5; x++) c.put('head', 'front', x, 6, look.hair);
  // shirt -> vest / jacket -> tie
  torso(c, 'body', { shirt, coat: look.coat, tie: look.tie, tieKind: look.tieKind ?? 'bow', open: look.sleeves === 'coat' ? 2 : 2 });
  for (const a of ['rightArm', 'leftArm']) {
    arm(c, a, look.sleeves === 'coat'
      ? { sleeve: look.coat, cuff: shirt, skin: look.skin }
      : { sleeve: shirt, skin: look.skin, band: look.band });
  }
  for (const l of ['rightLeg', 'leftLeg']) leg(c, l, { trousers: look.trousers });
  // overlay only where allowed: lapels on the jacket layer, the bun on the hat layer
  if (look.sleeves === 'coat') {
    for (let y = 0; y <= 4; y++) {
      c.put('jacket', 'front', 2, y, shade(look.coat, 1.25));
      c.put('jacket', 'front', 5, y, shade(look.coat, 1.25));
    }
  }
  if (look.woman) {
    c.face('hat', 'top', (x, y) => (x >= 2 && x <= 5 && y >= 4 ? look.hair : null));
    c.face('hat', 'back', (x, y) => (x >= 2 && x <= 5 && y <= 3 ? (y === 3 ? shade(look.hair, 0.8) : look.hair) : null));
    c.put('hat', 'back', 3, 2, '#C9A055');
  }
  return c.img;
}

// ---- piglins ------------------------------------------------------------------------------------------------------

export const PIGLINS = [
  { name: 'crimson', vest: CLOTH.crimson, tie: CLOTH.black },
  { name: 'gold', vest: CLOTH.gold, tie: CLOTH.black },
  { name: 'black', vest: CLOTH.black, tie: CLOTH.red },
  { name: 'teal', vest: CLOTH.teal, tie: GOLD_DARK },
];

function piglinHead(c, { hair = '#5A2E1E' } = {}) {
  c.fill('head', PIGLIN_SKIN);
  c.face('head', 'top', (x, y) => ((x + y) % 4 === 0 ? hair : null));
  face(c, 'head', { eyeY: 3, skin: PIGLIN_SKIN, iris: '#2A1A10', brow: shade(PIGLIN_SKIN, 0.7), xs: [2, 6] });
  c.fill('snout', PIGLIN_SNOUT);
  c.put('snout', 'front', 1, 1, '#5E2E2A', true);
  c.put('snout', 'front', 2, 1, '#5E2E2A', true);
  c.fill('tuskRight', '#EFE6C8');
  c.fill('tuskLeft', '#EFE6C8');
  c.fill('rightEar', PIGLIN_SKIN);
  c.fill('leftEar', PIGLIN_SKIN);
  // gold earrings low on the ears
  c.put('rightEar', 'right', 2, 4, GOLD, true);
  c.put('leftEar', 'left', 1, 4, GOLD, true);
  c.put('rightEar', 'front', 0, 4, GOLD_DARK, true);
  c.put('leftEar', 'front', 0, 4, GOLD_DARK, true);
}

export function piglin(look) {
  const c = canvas(PIGLIN);
  piglinHead(c);
  torso(c, 'body', { shirt: CLOTH.white, coat: look.vest, tie: look.tie, buttons: GOLD });
  for (const a of ['rightArm', 'leftArm']) arm(c, a, { sleeve: CLOTH.white, skin: PIGLIN_SKIN, band: look.vest });
  for (const l of ['rightLeg', 'leftLeg']) leg(c, l, { trousers: CLOTH.charcoal, shoe: '#3A2416' });
  return c.img;
}

export function pitBoss() {
  const c = canvas(PIGLIN);
  piglinHead(c, { hair: '#2A1A12' });
  torso(c, 'body', { shirt: CLOTH.white, coat: CLOTH.black, tie: CLOTH.black, tieKind: 'long', buttons: null, open: 3 });
  // the gold chain: a V from both shoulders, a medallion at the bottom
  for (let i = 0; i < 5; i++) {
    c.put('body', 'front', 1 + Math.floor(i / 2), i, GOLD, true);
    c.put('body', 'front', 6 - Math.floor(i / 2), i, GOLD, true);
  }
  c.put('body', 'front', 3, 5, GOLD_DARK, true);
  c.put('body', 'front', 4, 5, GOLD_DARK, true);
  c.put('body', 'front', 3, 6, GOLD, true);
  c.put('body', 'front', 4, 6, GOLD, true);
  for (const a of ['rightArm', 'leftArm']) arm(c, a, { sleeve: CLOTH.black, cuff: CLOTH.white, skin: PIGLIN_SKIN });
  c.put('rightArm', 'front', 1, 10, GOLD, true); // a signet ring
  for (const l of ['rightLeg', 'leftLeg']) leg(c, l, { trousers: CLOTH.black, shoe: '#1A1008' });
  return c.img;
}

// ---- endermen -----------------------------------------------------------------------------------------------------

export const ENDERS = [
  { name: 'tux', coat: '#24242C', shirt: CLOTH.white, tie: CLOTH.black, vest: null },
  { name: 'white_tie', coat: '#1C1C24', shirt: CLOTH.white, tie: CLOTH.white, vest: CLOTH.white },
  { name: 'velvet', coat: CLOTH.plum, shirt: '#E6DDF0', tie: CLOTH.black, vest: CLOTH.purple },
];

export function ender(look) {
  const c = canvas(ENDERMAN);
  c.fill('head', ENDER);
  // a little highlight on the crown so the head is not a void
  c.face('head', 'top', (x, y) => ((x * 3 + y * 5) % 11 === 0 ? '#22222A' : null));
  c.fill('body', look.coat);
  c.face('body', 'front', (x, y) => {
    if (x === 0 || x === 7) return look.coat;
    if (y <= 1 && x >= 2 && x <= 5) return y === 0 && (x === 2 || x === 5) ? look.shirt : look.tie;
    if (look.vest && y >= 3 && y <= 8 && x >= 2 && x <= 5) return (x === 3 || x === 4) && y % 2 === 0 ? GOLD_DARK : look.vest;
    if (x >= 2 && x <= 5) return look.shirt;
    return y >= 9 ? look.coat : shade(look.coat, 1.2); // lapels
  });
  c.face('body', 'back', (x, y) => (y >= 8 && (x === 3 || x === 4) ? shade(look.coat, 0.7) : null)); // the tails' split
  c.fill('limb', look.coat);
  c.band('limb', 26, 27, look.shirt);
  c.band('limb', 28, 29, ENDER);
  c.face('limb', 'bottom', ENDER);
  c.face('limb', 'top', look.coat);
  return c.img;
}

// ---- villager croupiers ---------------------------------------------------------------------------------------------

export const VILLAGERS = [
  { name: 'top_hat', suit: CLOTH.black, tie: CLOTH.red, hat: { colour: CLOTH.black, rows: 4, band: CLOTH.red, rim: 2 } },
  { name: 'bowler', suit: CLOTH.brown, tie: CLOTH.green, hat: { colour: '#3A2618', rows: 2, band: CLOTH.black, rim: 1 } },
  { name: 'fedora', suit: CLOTH.grey, tie: CLOTH.navy, hat: { colour: '#4A4A52', rows: 3, band: CLOTH.black, rim: 2 } },
  { name: 'bareheaded', suit: CLOTH.navy, tie: GOLD_DARK, hat: null },
];

export function villager(look) {
  const c = canvas(VILLAGER);
  c.fill('head', VILLAGER_SKIN);
  c.face('head', 'top', '#3A2A1E'); // slicked hair
  c.face('head', 'back', (x, y) => (y <= 2 ? '#3A2A1E' : null));
  for (let x = 1; x <= 6; x++) c.put('head', 'front', x, 3, '#3A2A1E'); // the unibrow
  face(c, 'head', { eyeY: 4, skin: VILLAGER_SKIN, iris: '#2E5A2A', xs: [1, 5] });
  c.fill('nose', VILLAGER_NOSE);
  torso(c, 'body', { shirt: CLOTH.white, coat: CLOTH.white, tie: look.tie, tieKind: 'long', buttons: null, open: 0 });
  c.fill('body', CLOTH.white, ['right', 'left', 'back']);
  c.fill('arms', look.suit);
  c.band('arms', 7, 7, CLOTH.white);
  c.fill('armsMid', look.suit);
  c.face('armsMid', 'front', (x) => (x >= 2 && x <= 5 ? VILLAGER_SKIN : null));
  c.face('armsMid', 'bottom', (x) => (x >= 2 && x <= 5 ? VILLAGER_SKIN : null));
  leg(c, 'leg', { trousers: shade(look.suit, 0.85) });
  // overlay: the suit coat (open at the front, tails at the back), the hat and its rim
  c.face('jacket', 'front', (x, y) => (y <= 13 && (x <= 1 || x >= 6) ? look.suit : null));
  for (const s of ['right', 'left']) c.face('jacket', s, (x, y) => (y <= 13 ? look.suit : null));
  c.face('jacket', 'back', (x, y) => (y <= 15 && !(y >= 11 && (x === 3 || x === 4)) ? look.suit : null));
  c.face('jacket', 'top', look.suit);
  if (look.hat) {
    const { colour, rows, band, rim } = look.hat;
    c.face('hat', 'top', colour);
    c.band('hat', 0, rows - 1, colour);
    c.band('hat', rows - 1, rows - 1, band);
    c.face('hatRim', 'front', (x, y) => {
      const d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
      return d > 3.5 && d <= 3.5 + rim ? shade(colour, 0.9) : null;
    });
  }
  return c.img;
}

// ---- spawn egg ----------------------------------------------------------------------------------------------------

export function egg() {
  const img = image(16, 16);
  ellipse(img, 8, 8.6, 5.2, 6.6, (x, y, d) => (d > 0.86 ? '#1A1A20' : '#ECE8DC'));
  for (const [x, y] of [[6, 5], [9, 7], [7, 10], [10, 11], [5, 8]]) setPx(img, x, y, '#1A1A20');
  for (const [x, y] of [[7, 13], [8, 13], [9, 13]]) setPx(img, x, y, '#A3202A'); // the bow tie
  setPx(img, 8, 12, '#A3202A');
  return img;
}

export const OUTPUTS = () => [
  ...HUMANS.map((l, i) => [`${DIR}/dealer/human_${i}.png`, human(l), PLAYER]),
  ...PIGLINS.map((l, i) => [`${DIR}/dealer/piglin_${i}.png`, piglin(l), PIGLIN]),
  [`${DIR}/dealer/pit_boss.png`, pitBoss(), PIGLIN],
  ...ENDERS.map((l, i) => [`${DIR}/dealer/ender_${i}.png`, ender(l), ENDERMAN]),
  ...VILLAGERS.map((l, i) => [`${DIR}/croupier/villager_${i}.png`, villager(l), VILLAGER]),
];

export default function generate() {
  return [...OUTPUTS().map(([path, img]) => png(path, img)), png(EGG, egg())];
}
