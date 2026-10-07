// Table UI v2 scene fills (docs/architecture/ui-v2.md §9, lane E; REDESIGN.md §7.7 "the backdrop art becomes the
// PlayArea fill"): calm 64 × 64 seamless tiles behind the game object of each v2 extras / PvP screen, so the object and
// the numbers read (the old 400 × 240 backdrops are busy scenes). Dark, low-contrast lattices in each game's colours;
// deterministic sparkle. The screens tile them over the whole PlayArea.
import { c, image, put, rng } from './kit.mjs';

const S = 64;

function base(hexA, hexB) {
  const img = image(S, S);
  for (let y = 0; y < S; y++) {
    for (let x = 0; x < S; x++) {
      // 2 × 2 checker dither of two close tones: texture without pattern noise
      put(img, x, y, c(((x >> 1) + (y >> 1)) % 2 ? hexA : hexB));
    }
  }
  return img;
}

/** Diamond lattice lines every `step` px (seamless when step divides 64). */
function lattice(img, step, hex) {
  for (let y = 0; y < S; y++) {
    for (let x = 0; x < S; x++) {
      if ((x + y) % step === 0 || (x - y + S) % step === 0) put(img, x, y, c(hex));
    }
  }
  return img;
}

function sparkle(img, n, hexes, seed) {
  const r = rng(seed);
  for (let i = 0; i < n; i++) {
    const x = Math.floor(r() * S);
    const y = Math.floor(r() * S);
    put(img, x, y, c(hexes[i % hexes.length]));
  }
  return img;
}

/** Plinko: the mockup's night navy with the faint diamond net and a few stars. */
function plinko() {
  return sparkle(lattice(base('#1A1648', '#1C184C'), 16, '#221D58'), 5, ['#4A44A0', '#6A64C0'], 4401);
}

/** Wheel of Fortune: carnival night purple, dotted lattice, warm bulb specks. */
function wheel() {
  return sparkle(lattice(base('#22103A', '#24123E'), 32, '#2E1A4C'), 6, ['#FFD640', '#7A4AB0', '#FF6E6A'], 4402);
}

/** Coin Flip: velvet green with a fine diamond (the parlour of the old backdrop, calmed down). */
function coin() {
  return sparkle(lattice(base('#103A26', '#123E28'), 16, '#174A31'), 3, ['#2E7A50'], 4403);
}

/** Scratch: the kiosk's teal, darkened so the cream ticket pops. */
function scratch() {
  return sparkle(lattice(base('#0E3236', '#10363A'), 32, '#154448'), 4, ['#2E8A80', '#E8D9A0'], 4404);
}

/** Dice Duel: tavern brown with a plank rhythm. */
function dice() {
  const img = base('#2A1A10', '#2C1C12');
  for (let x = 0; x < S; x++) {
    put(img, x, 15, c('#22140C'));
    put(img, x, 47, c('#22140C'));
  }
  for (let y = 0; y < S; y++) {
    put(img, (y < 16 || y >= 48) ? 40 : 8, y, c('#22140C'));
  }
  return sparkle(img, 4, ['#3A2616'], 4405);
}

/** PvP arena: gunmetal violet, wide lattice, cyan / red specks (the two sides). */
function arena() {
  return sparkle(lattice(base('#1A1428', '#1C162C'), 32, '#262040'), 6, ['#5CE8E0', '#FF6E6A'], 4406);
}

export const FILLS = { plinko, wheel, coin, scratch, dice, arena };
