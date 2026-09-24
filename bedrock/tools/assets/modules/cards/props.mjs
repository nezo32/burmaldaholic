// Table props (visual/cards.md §4, §5): chips, felt prints, spot glows, the shoe, the discard tray, the deck, the dealer
// rack, the dealer button, baccarat beads. Prints are WHITE (the screen tints them with the theme's print colour);
// everything else is fully coloured.
import { CHIPS } from '../../lib/palette.mjs';
import { B, P, R, bayer, box, disc, fromRows, image, mixHex, over, ring, C } from './px.mjs';
import { THEMES } from './theme.mjs';
import { back } from './faces.mjs';

export const DENOMS = [1, 5, 25, 100, 500];
const INK = '#180A28';

// ---- chips --------------------------------------------------------------------------------------------------------
/**
 * A chip disc seen at the table's angle: 13 × 8 (a 13 × 6 top ellipse + a 2 px edge). Stacks are drawn by code,
 * 2 px per disc. `d` a denomination, or 'tint' (greyscale, multiplied by the seat colour) .
 */
export function chipDisc(d) {
  const { base, stripe } = d === 'tint' ? { base: '#E8E8E8', stripe: '#9A9A9A' } : CHIPS[d];
  const img = image(13, 8);
  const edge = mixHex(base, '#000000', 0.35);
  // edge band (y 3..6) with inserts
  for (let x = 1; x < 12; x++) {
    const ins = (x + 1) % 4 < 2;
    R(img, x, 3, 1, 3, ins ? stripe : edge);
    P(img, x, 5, mixHex(ins ? stripe : edge, '#000000', 0.3));
  }
  // top ellipse
  for (let y = 0; y < 6; y++)
    for (let x = 0; x < 13; x++) {
      const dx = (x + 0.5 - 6.5) / 6.5;
      const dy = (y + 0.5 - 3) / 3;
      const r = Math.hypot(dx, dy);
      if (r > 1) continue;
      let c = base;
      if (r > 0.72) c = (Math.round(Math.atan2(dy, dx) * 4) & 1) === 0 ? stripe : base;
      else if (r < 0.42) c = mixHex(base, '#FFFFFF', d === 1 || d === 'tint' ? 0 : 0.15);
      if (y === 0) c = mixHex(c, '#FFFFFF', 0.3);
      P(img, x, y, c);
    }
  // outline
  const out = image(13, 8);
  for (let y = 0; y < 8; y++)
    for (let x = 0; x < 13; x++) {
      if (img.data[(y * 13 + x) * 4 + 3]) continue;
      const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([i, j]) => {
        const X = x + i;
        const Y = y + j;
        return X >= 0 && Y >= 0 && X < 13 && Y < 8 && img.data[(Y * 13 + X) * 4 + 3];
      });
      if (n) P(out, x, y, INK);
    }
  over(out, img);
  R(out, 1, 6, 11, 1, INK);
  return out;
}

/** Hatched overlay for atmosphere-bot chips (BOTS.md §8.1): diagonal bone hatching, 13 × 8. */
export function chipHatch() {
  const img = image(13, 8);
  const shape = chipDisc('tint');
  for (let y = 0; y < 8; y++)
    for (let x = 0; x < 13; x++) if (shape.data[(y * 13 + x) * 4 + 3] && (x + y) % 3 === 0) P(img, x, y, '#F4ECF8');
  return img;
}

/** Big top-view chip for the chip rack buttons, 22 × 22 (8 edge inserts, inner ring, inlay). */
export function chipBig(d) {
  const { base, stripe } = CHIPS[d];
  const img = image(22, 22);
  const dark = mixHex(base, '#000000', 0.35);
  for (let y = 0; y < 22; y++)
    for (let x = 0; x < 22; x++) {
      const dx = x + 0.5 - 11;
      const dy = y + 0.5 - 11;
      const r = Math.hypot(dx, dy);
      if (r > 10.6) continue;
      let c;
      if (r > 9.7) c = INK;
      else if (r > 7.2) {
        const a = (Math.atan2(dy, dx) / (Math.PI * 2)) * 8 + 8.25;
        c = Math.floor(a) % 2 === 0 && a % 1 < 0.55 ? stripe : base;
        if (r > 9) c = mixHex(c, '#000000', 0.2);
      } else if (r > 6.3) c = dark;
      else if (r > 5.6) c = stripe;
      else c = mixHex(base, '#FFFFFF', d === 1 ? 0 : 0.12);
      // light from the top-left
      if (r < 9.7 && dx + dy < -9 && r > 7.2) c = mixHex(c, '#FFFFFF', 0.35);
      if (r < 9.7 && dx + dy > 10) c = mixHex(c, '#000000', 0.25);
      P(img, x, y, c);
    }
  return img;
}

/** Selected-chip ring 26 × 26 (gold, with 4 glints). */
export function chipSelect() {
  const img = image(26, 26);
  ring(img, 13, 13, 12.6, 2, C('#FFD640'));
  ring(img, 13, 13, 12.6, 0.8, C('#B07010'));
  for (const [x, y] of [[13, 0], [0, 13], [25, 13], [13, 25]]) P(img, x, y, '#FFFFFF');
  return img;
}

// ---- felt prints (white; tinted by code) -------------------------------------------------------------------------
const W = '#FFFFFF';
/** Bet spot 28 × 28: a 2 px ring with a dotted inner ring (blackjack, poker bet line). */
export function printSpot() {
  const img = image(28, 28);
  ring(img, 14, 14, 13.5, 2, C(W));
  for (let a = 0; a < 20; a++) {
    const t = (a / 20) * Math.PI * 2;
    P(img, Math.round(14 + Math.cos(t) * 9.5 - 0.5), Math.round(14 + Math.sin(t) * 9.5 - 0.5), W);
  }
  return img;
}
/** UTH circles 24 × 24 — the ring STYLE carries the meaning (dotted Trips, solid Ante, double Blind, dashed Play). */
export function printUth(kind) {
  const img = image(24, 24);
  if (kind === 'trips') {
    for (let a = 0; a < 24; a++) if (a % 2 === 0) disc(img, 12 + Math.cos((a / 24) * Math.PI * 2) * 10.5, 12 + Math.sin((a / 24) * Math.PI * 2) * 10.5, 1.1, C(W));
    for (const [x, y] of [[8, 10], [12, 14], [16, 10]]) R(img, x - 1, y - 1, 2, 2, W);
  } else if (kind === 'ante') {
    ring(img, 12, 12, 11.5, 2, C(W));
    ring(img, 12, 12, 4.5, 1, C(W));
  } else if (kind === 'blind') {
    ring(img, 12, 12, 11.5, 1, C(W));
    ring(img, 12, 12, 9.5, 1, C(W));
    // a closed eye (the "blind" bet)
    for (let x = 7; x <= 16; x++) P(img, x, 12 + Math.round(((x - 11.5) / 5) ** 2 * 2 - 2) * -1 - 0, W);
    for (const x of [8, 11, 14]) P(img, x + 1, 15, W);
  } else {
    for (let a = 0; a < 16; a++) {
      if (a % 2) continue;
      for (let s = 0; s < 4; s++) {
        const t = ((a + s / 4) / 16) * Math.PI * 2;
        P(img, Math.round(12 + Math.cos(t) * 11 - 0.5), Math.round(12 + Math.sin(t) * 11 - 0.5), W);
        P(img, Math.round(12 + Math.cos(t) * 10 - 0.5), Math.round(12 + Math.sin(t) * 10 - 0.5), W);
      }
    }
    // a chevron (play)
    for (let i = 0; i < 4; i++) {
      R(img, 10 + i, 8 + i, 2, 1, W);
      R(img, 10 + i, 15 - i, 2, 1, W);
    }
  }
  return img;
}
/** Nine-slice print box 32 × 24 (border 5), rounded 2 px line — baccarat boxes, hand panels. */
export function printBox() {
  const img = image(32, 24);
  box(img, 1, 0, 30, 24, W);
  box(img, 0, 1, 32, 22, W);
  box(img, 2, 2, 28, 20, W, 0.35);
  return img;
}
/** Board / hand slot 37 × 49: a dashed card outline. */
export function printSlot(w = 37, h = 49) {
  const img = image(w, h);
  for (let x = 2; x < w - 2; x++) if ((x >> 1) % 2 === 0) {
    P(img, x, 0, W);
    P(img, x, h - 1, W);
  }
  for (let y = 2; y < h - 2; y++) if ((y >> 1) % 2 === 0) {
    P(img, 0, y, W);
    P(img, w - 1, y, W);
  }
  return img;
}
/** Pot well 48 × 24: an oval ring. */
export function printPot() {
  const img = image(48, 24);
  for (let y = 0; y < 24; y++)
    for (let x = 0; x < 48; x++) {
      const r = Math.hypot((x + 0.5 - 24) / 23.5, (y + 0.5 - 12) / 11.5);
      if (r <= 1 && r > 0.9) P(img, x, y, W);
      else if (r <= 0.8 && r > 0.74 && (x + y) % 2 === 0) P(img, x, y, W);
    }
  return img;
}
/**
 * Insurance band 232 × 30: two concentric arcs following the crescent (table 408 × 184, centre at the top edge);
 * the screen writes "INSURANCE PAYS 2 TO 1" in the band. Arc radii = 0.44 / 0.52 of the table.
 */
export function printInsurance() {
  const img = image(232, 30);
  const cx = 116;
  for (const k of [0.44, 0.52])
    for (let x = 0; x < 232; x++) {
      const u = (x + 0.5 - cx) / (204 * k);
      if (Math.abs(u) > 1) continue;
      const y = Math.round(184 * k * (1 - Math.abs(u) ** 2.6) ** (1 / 2.6) - 184 * 0.44 + 2);
      if (y >= 0 && y < 30) P(img, x, y, W);
    }
  return img;
}

/** Spot glow, 4 frames of 34 × 34 (gold pulse ring) — the active / winning spot. */
export function spotGlow(f) {
  const img = image(34, 34);
  const k = [0.45, 0.7, 1, 0.7][f];
  for (let i = 0; i < 4; i++) ring(img, 17, 17, 16.5 - i, 1, C(i === 0 ? '#FFF4B0' : '#FFD640'), k * (1 - i / 4));
  return img;
}

// ---- baccarat -----------------------------------------------------------------------------------------------------
export const BOX_COLOURS = { player: '#3A6BE0', banker: '#D03030', tie: '#2FA64A', pair: '#FFD640' };
/** Emblems (white, tinted): ring = Player, crown = Banker, equals = Tie, two cards = pair. 11 × 11. */
export const EMBLEM_ROWS = {
  player: ['...#####...', '..#.....#..', '.#.......#.', '#.........#', '#.........#', '#.........#', '#.........#', '#.........#', '.#.......#.', '..#.....#..', '...#####...'],
  banker: ['...........', '#....#....#', '##..###..##', '###.###.###', '###########', '###########', '###########', '...........', '###########', '...........', '...........'],
  tie: ['...........', '...........', '...........', '###########', '###########', '...........', '...........', '###########', '###########', '...........', '...........'],
  pair: ['######.....', '#....#.....', '#..######..', '#..#....#..', '#..#....#..', '#..#....#..', '###.....#..', '...#....#..', '...#....#..', '...######..', '...........'],
};
export const emblemPrint = (kind) => fromRows(EMBLEM_ROWS[kind], { '#': W });

/** Beads 9 × 9 for the bead road (runtime letter on top): Player circle, Banker rounded square, Tie diamond. */
export function bead(kind) {
  const img = image(9, 9);
  const c = BOX_COLOURS[kind];
  const d = mixHex(c, '#000000', 0.4);
  const l = mixHex(c, '#FFFFFF', 0.4);
  if (kind === 'player') {
    disc(img, 4.5, 4.5, 4.4, C(d));
    disc(img, 4.5, 4.5, 3.6, C(c));
    P(img, 3, 2, l);
  } else if (kind === 'banker') {
    R(img, 1, 0, 7, 9, d);
    R(img, 0, 1, 9, 7, d);
    R(img, 1, 1, 7, 7, c);
    P(img, 2, 2, l);
  } else {
    for (let y = 0; y < 9; y++)
      for (let x = 0; x < 9; x++) {
        const m = Math.abs(x - 4) + Math.abs(y - 4);
        if (m <= 4) P(img, x, y, m === 4 ? d : c);
      }
    P(img, 4, 2, l);
  }
  return img;
}
/** Pair dots 3 × 3 (Player pair = top-left corner of a bead, Banker pair = bottom-right; colours as the boxes). */
export function pairDot(kind) {
  const img = image(3, 3);
  R(img, 0, 0, 3, 3, '#180A28');
  P(img, 1, 1, kind === 'player' ? '#7AA6FF' : '#FF7A72');
  return img;
}

// ---- shoe, discard tray, deck, rack, dealer button (themed materials) --------------------------------------------
/** Dealing shoe 40 × 28, top-down at the table angle: a box with a slanted mouth and the next card's back showing. */
export function shoe(theme) {
  const t = THEMES[theme];
  const img = image(40, 28);
  // body
  R(img, 0, 6, 40, 20, INK);
  R(img, 1, 7, 38, 18, t.wood);
  R(img, 1, 7, 38, 2, t.woodLight);
  R(img, 1, 21, 38, 4, t.woodDark);
  for (let x = 3; x < 37; x += 6) R(img, x, 11, 1, 9, t.woodDark);
  // stacked cards visible on top (the shoe is open-topped): bone edges
  R(img, 4, 2, 30, 6, INK);
  R(img, 5, 3, 28, 4, '#FBF6EA');
  for (let x = 6; x < 32; x += 2) P(img, x, 5, '#C0B0DC');
  // mouth (left): the next card, face down
  R(img, 0, 12, 6, 12, INK);
  R(img, 1, 13, 5, 10, t.woodDark);
  const b = back(theme === 'bastion' ? 'bastion' : theme === 'end' ? 'end' : 'navy', 's');
  over(img, b, -6, 11);
  // brass corners + gold trim
  for (const [x, y] of [[1, 7], [37, 7], [1, 23], [37, 23]]) R(img, x, y, 2, 2, t.trim);
  R(img, 6, 24, 30, 1, t.trimShade);
  return img;
}
/** Discard tray 30 × 22 with a few backs inside (fill level is drawn by code with `trayFill`). */
export function discardTray(theme) {
  const t = THEMES[theme];
  const img = image(30, 22);
  R(img, 0, 0, 30, 22, INK);
  R(img, 1, 1, 28, 20, t.wood);
  R(img, 1, 1, 28, 1, t.woodLight);
  R(img, 3, 3, 24, 16, t.woodDark);
  R(img, 3, 3, 24, 1, '#0A0412');
  R(img, 1, 20, 28, 1, t.trimShade);
  return img;
}
/** Card stack sprite for trays and decks: 21 × 32 (M back + 3 px of edges). */
export function deck(design = 'navy') {
  const img = image(21, 32);
  for (let i = 3; i >= 1; i--) {
    R(img, 0, i, 21, 29, INK);
    R(img, 1, i + 1, 19, 27, i % 2 ? '#FBF6EA' : '#C0B0DC');
  }
  over(img, back(design, 'm'), 0, 0);
  return img;
}
/** Dealer rack 80 × 14: 6 wells of chips (500, 100, 25, 5, 1, 1) seen edge-on, in a themed frame. */
export function rack(theme) {
  const t = THEMES[theme];
  const img = image(80, 14);
  R(img, 0, 0, 80, 14, INK);
  R(img, 1, 1, 78, 12, t.wood);
  R(img, 1, 1, 78, 1, t.woodLight);
  R(img, 1, 12, 78, 1, t.trimShade);
  const cols = [500, 100, 25, 5, 1, 1];
  cols.forEach((d, i) => {
    const x = 3 + i * 13;
    R(img, x, 3, 11, 8, '#0A0412');
    const { base, stripe } = CHIPS[d];
    for (let k = 0; k < 11; k++) {
      const c = k % 2 ? base : mixHex(base, '#000000', 0.25);
      R(img, x + k, 4, 1, 6, c);
      if (k % 4 === 1) R(img, x + k, 5, 1, 4, stripe);
    }
    R(img, x, 4, 11, 1, mixHex(base, '#FFFFFF', 0.3));
  });
  return img;
}
/** Dealer button 13 × 13: bone disc, gold rim, a gold star (no letter). */
export function dealerButton() {
  const img = image(13, 13);
  disc(img, 6.5, 6.5, 6.4, C(INK));
  disc(img, 6.5, 6.5, 5.5, C('#B07010'));
  disc(img, 6.5, 6.5, 4.6, C('#F4ECF8'));
  over(img, fromRows(['..g..', '.ggg.', 'ggggg', '.g.g.', 'g...g'], { g: '#FFD640' }), 4, 4);
  B(img, 4, 3, '#FFFFFF', 0.8);
  return img;
}

/** Card-stack fill used inside the tray: a strip of card edges 24 × 12. */
export function trayFill() {
  const img = image(24, 12);
  for (let y = 0; y < 12; y++) R(img, 0, y, 24, 1, y % 2 ? '#FBF6EA' : '#C0B0DC');
  for (let y = 0; y < 12; y += 1) if (bayer(0, y) < 0.3) R(img, 0, y, 24, 1, '#E2D8C4');
  return img;
}
