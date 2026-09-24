// Wheel of Fortune art (extras.md §4; extras-pvp §3–§4): the baked default face (GAME_DESIGN Appendix B order), the
// static rim with bulb sockets, bulbs, hub with a gem glint, the red leather flapper, the stand, segment icons at 8 /
// 16 / 40 px, the pop-out burst and the runtime-bake parts sheet. The face is 1 art px = 1 GUI px (like the Plinko
// board and the backdrops); icons on the face are hand-drawn 8 × 8 so they survive the radial rotation.
import { WHEEL_CODES, WHEEL_KIND, wheelIcon, wheelIconMini } from './icons.mjs';
import {
  K, affine, blend, c, disc, hstrip, image, mixHex, outline, over, put, rect, rng, scaleUp, shadow, stud, vstrip,
} from './kit.mjs';

/** GAME_DESIGN.md Appendix B: index 0…53 clockwise from the pointer at rest. */
export const APPENDIX_B = 'XBMBDBMBHBDBMBTBMBDBHBMBDBE' + 'BMBDBHBMBTBMBDBHBMBTBCMHDMB';

export const FACE = 160; // face texture size (radius 80)
export const RIM = 184; // rim texture size (ring 80…92)

/** Clockwise angle from the top, degrees in [0, 360). */
const angleOf = (dx, dy) => ((Math.atan2(dx, -dy) * 180) / Math.PI + 360) % 360;

/** The rotating face for a segment list (default Appendix B). */
export function wheelFace(segments = APPENDIX_B) {
  const n = segments.length;
  const s = 360 / n;
  const img = image(FACE, FACE);
  const R = FACE / 2;
  for (let y = 0; y < FACE; y++)
    for (let x = 0; x < FACE; x++) {
      const dx = x + 0.5 - R;
      const dy = y + 0.5 - R;
      const d = Math.hypot(dx, dy);
      if (d > R - 0.2) continue;
      const phi = angleOf(dx, dy);
      const shifted = (phi + s / 2) % 360;
      const i = Math.floor(shifted / s) % n;
      const kind = WHEEL_KIND[segments[i]];
      const inSeg = shifted - i * s; // 0 … s
      const edgeDist = Math.min(inSeg, s - inSeg) * (Math.PI / 180) * d; // px to the boundary
      let hex;
      if (d < 22) {
        // centre medallion: dark lacquer with a gold ring
        hex = d > 20.5 ? K.goldShade : d > 19.5 ? K.gold : (Math.floor(phi / 30) % 2 ? '#2A1238' : '#221030');
      } else if (edgeDist < 0.55 && d > 22) hex = mixHex(kind.dark, K.ink, 0.45);
      else if (d > R - 5) hex = kind.light;
      else if (d < 34) hex = kind.dark;
      else hex = kind.hex;
      put(img, x, y, c(hex));
      // wedge sheen: a soft lighter streak along each wedge's leading half, outer area
      if (d > 34 && d < R - 5 && inSeg < s * 0.5 && edgeDist > 1.2) blend(img, x, y, c('#FFFFFF'), 0.07);
    }
  // outer gold lip
  for (let y = 0; y < FACE; y++)
    for (let x = 0; x < FACE; x++) {
      const d = Math.hypot(x + 0.5 - R, y + 0.5 - R);
      if (d <= R - 0.2 && d > R - 1.4) put(img, x, y, c(K.goldShade));
    }
  // icons, upright relative to their wedge (radial), at r = 64
  for (let i = 0; i < n; i++) {
    const a = i * s;
    const icon = wheelIconMini(segments[i]);
    const rot = affine(icon, 12, 12, { rot: a, px: 4, py: 4, ox: 6, oy: 6 });
    const rad = (a * Math.PI) / 180;
    over(img, rot, Math.round(R + Math.sin(rad) * 64 - 6), Math.round(R - Math.cos(rad) * 64 - 6));
  }
  // pegs on every boundary (r = 75): 3 px brass studs
  for (let i = 0; i < n; i++) {
    const rad = ((i * s + s / 2) * Math.PI) / 180;
    const px = Math.round(R + Math.sin(rad) * 75 - 0.5);
    const py = Math.round(R - Math.cos(rad) * 75 - 0.5);
    rect(img, px - 1, py - 1, 3, 3, K.ink);
    put(img, px, py, c(K.gold));
    put(img, px - 1, py - 1, c(K.goldShade));
    put(img, px, py - 1, c(K.goldLight));
    put(img, px - 1, py, c(K.gold));
  }
  return img;
}

/** Static rim ring (184²): lacquered wood with gold lips and 24 dark bulb sockets; transparent centre. */
export function wheelRim() {
  const img = image(RIM, RIM);
  const C = RIM / 2;
  const r = rng(4040);
  for (let y = 0; y < RIM; y++)
    for (let x = 0; x < RIM; x++) {
      const dx = x + 0.5 - C;
      const dy = y + 0.5 - C;
      const d = Math.hypot(dx, dy);
      if (d > 92 || d < 79) continue;
      const lit = (-dx - dy) / d;
      let hex;
      if (d > 91) hex = K.ink;
      else if (d > 89.8) hex = lit > 0 ? K.goldLight : K.goldShade;
      else if (d > 81.2) {
        const grain = (Math.floor(d * 1.7) + Math.floor(angleOf(dx, dy) / 7)) % 3;
        hex = ['#8C1834', '#A0203C', '#7A1430'][grain];
        if (lit > 0.6 && d > 87) hex = '#C0304A';
        if (lit < -0.6 && d < 83) hex = '#5A0E24';
      } else if (d > 80) hex = lit > 0 ? K.gold : K.goldShade;
      else hex = K.ink;
      put(img, x, y, c(hex));
      if (d > 81.2 && d < 89.8 && r() < 0.05) blend(img, x, y, c('#FFFFFF'), 0.08);
    }
  for (let i = 0; i < 24; i++) {
    const a = ((i * 15 + 7.5) * Math.PI) / 180;
    const x = Math.round(C + Math.sin(a) * 85.5 - 0.5);
    const y = Math.round(C - Math.cos(a) * 85.5 - 0.5);
    disc(img, x + 0.5, y + 0.5, 2.6, c(K.ink));
    disc(img, x + 0.5, y + 0.5, 1.8, c('#3A1A10'));
  }
  // flapper mount at the top: a gold bracket
  rect(img, C - 5, 1, 10, 6, K.ink);
  rect(img, C - 4, 2, 8, 4, K.gold);
  rect(img, C - 4, 2, 8, 1, K.goldLight);
  rect(img, C - 4, 5, 8, 1, K.goldShade);
  return img;
}

/** Bulb positions on the rim (centre of each socket, rim-local coordinates). */
export const BULBS = Array.from({ length: 24 }, (_, i) => {
  const a = ((i * 15 + 7.5) * Math.PI) / 180;
  return [Math.round(RIM / 2 + Math.sin(a) * 85.5 - 0.5), Math.round(RIM / 2 - Math.cos(a) * 85.5 - 0.5)];
});

/** Bulb states (8 × 8 each): 0 off, 1 on (warm), 2 on (gold flash). Code-indexed row. */
export function wheelBulbs() {
  const mk = (on, hex) => {
    const img = image(8, 8);
    if (!on) {
      disc(img, 4, 4, 2.2, c(K.ink));
      disc(img, 4, 4, 1.6, c('#5A3A1A'));
      put(img, 3, 3, c('#8A6A3A'));
      return img;
    }
    disc(img, 4, 4, 3.9, c(hex), 0.28);
    disc(img, 4, 4, 2.4, c(hex));
    put(img, 3, 3, c(K.white));
    put(img, 4, 3, c(K.goldLight));
    return img;
  };
  return [mk(false), mk(true, '#FFB040'), mk(true, K.gold)];
}

/** Hub cap (28 × 28 × 6 frames, V + mcmeta): gold dome, ruby gem, a glint sweeping the gem on frames 1–5. */
export function wheelHub() {
  const frames = [];
  for (let f = 0; f < 6; f++) {
    const img = image(28, 28);
    for (let y = 0; y < 28; y++)
      for (let x = 0; x < 28; x++) {
        const dx = x + 0.5 - 14;
        const dy = y + 0.5 - 14;
        const d = Math.hypot(dx, dy);
        if (d > 13.5) continue;
        const lit = (-dx - dy) / (d || 1);
        let hex = d > 12.6 ? K.ink : d > 11.4 ? (lit > 0 ? K.goldLight : K.goldShade) : d > 10.4 ? K.goldDeep : d > 6 ? (lit > 0.35 ? '#FFE87A' : lit < -0.4 ? '#D8A020' : K.gold) : K.ink;
        if (d <= 5.2) hex = d > 4.2 ? '#8C1834' : dx + dy < -1 ? '#FF6E6A' : '#D83440';
        put(img, x, y, c(hex));
      }
    // bolts
    for (let i = 0; i < 6; i++) {
      const a = (i * Math.PI) / 3;
      put(img, Math.round(14 + Math.cos(a) * 8.3 - 0.5), Math.round(14 + Math.sin(a) * 8.3 - 0.5), c(K.goldShade));
    }
    put(img, 12, 12, c(K.white));
    if (f > 0) {
      const gx = 9 + f * 2;
      for (let k = -1; k <= 1; k++) blend(img, gx + k, 14 - k - (f - 3), c(K.white), k === 0 ? 0.9 : 0.5);
    }
    frames.push(img);
  }
  return frames;
}

/** Flapper (14 × 24): red leather tongue on a gold pivot; pivot at (7, 4). Code rotates it. */
export function wheelFlapper() {
  const img = image(14, 24);
  for (let y = 0; y < 24; y++)
    for (let x = 0; x < 14; x++) {
      const v = (y - 4) / 19; // 0 at pivot … 1 at tip
      if (v < 0) continue;
      const half = 5.2 * (1 - v) ** 0.9 + 0.6;
      const u = x + 0.5 - 7;
      if (Math.abs(u) > half) continue;
      const edge = Math.abs(u) > half - 1;
      put(img, x, y, c(edge ? '#5A0E1C' : u < -1 ? '#FF6E6A' : u > 1.5 ? '#A02030' : '#D83440'));
    }
  // stitching
  for (let y = 7; y < 20; y += 2) put(img, 7, y, c('#FFB0A0'));
  // pivot
  disc(img, 7, 4, 4.2, c(K.ink));
  disc(img, 7, 4, 3.2, c(K.gold));
  put(img, 6, 3, c(K.white));
  put(img, 8, 5, c(K.goldShade));
  return outline(img, c(K.ink), { k: 0.8 });
}

/** Stand (128 × 52): two lacquered legs, a cross brace and a plinth with a gold trim; drawn behind the rim. */
export function wheelStand() {
  const img = image(128, 52);
  const leg = (x0, dir) => {
    for (let y = 0; y < 40; y++) {
      const x = Math.round(x0 + dir * y * 0.55);
      rect(img, x - 4, y, 9, 1, K.ink);
      rect(img, x - 3, y, 7, 1, '#7A1430');
      rect(img, x - 3, y, 2, 1, '#A0203C');
      rect(img, x + 2, y, 1, 1, '#5A0E24');
    }
  };
  leg(52, -1);
  leg(76, 1);
  rect(img, 34, 24, 60, 5, K.ink);
  rect(img, 35, 25, 58, 3, K.goldShade);
  rect(img, 35, 25, 58, 1, K.gold);
  // plinth
  rect(img, 8, 40, 112, 12, K.ink);
  rect(img, 9, 41, 110, 10, '#3A2010');
  rect(img, 9, 41, 110, 2, '#5A3418');
  rect(img, 9, 44, 110, 1, K.gold);
  rect(img, 9, 45, 110, 1, K.goldShade);
  for (const x of [14, 38, 64, 90, 113]) stud(img, x, 48, K.gold);
  return img;
}

/** Pop-out burst (48 × 48): gold rays behind the landed icon (the §3.3 stop beat). */
export function wheelPop() {
  const img = image(48, 48);
  for (let y = 0; y < 48; y++)
    for (let x = 0; x < 48; x++) {
      const dx = x + 0.5 - 24;
      const dy = y + 0.5 - 24;
      const d = Math.hypot(dx, dy);
      const a = Math.atan2(dy, dx);
      const ray = Math.cos(a * 8) > 0.35;
      if (d < 15) put(img, x, y, c(d > 14 ? K.ink : d > 12.8 ? K.gold : '#26103C'));
      else if (ray && d < 23.5) blend(img, x, y, c(d < 19 ? K.gold : K.goldShade), 1 - (d - 15) / 11);
    }
  for (let y = 0; y < 48; y++)
    for (let x = 0; x < 48; x++) {
      const d = Math.hypot(x + 0.5 - 24, y + 0.5 - 24);
      if (d < 12.8 && d > 11.8) put(img, x, y, c(K.goldShade));
    }
  return img;
}

/** Segment icon sheets: 16 px (8 × 16 = 128 × 16), 40 px pop-out (2× outlined), 8 px (face). Order B C H M D T E X. */
export const wheelIcons16 = () => hstrip(WHEEL_CODES.map(wheelIcon));
export const wheelIcons8 = () => hstrip(WHEEL_CODES.map(wheelIconMini));
export function wheelIcons40() {
  return hstrip(
    WHEEL_CODES.map((code) => {
      const big = outline(scaleUp(wheelIcon(code), 2), c(K.ink));
      const cell = image(40, 40);
      over(cell, shadow(big, c(K.ink), 2, 2, 0.4), 4, 4);
      return cell;
    }),
  );
}

/** Runtime-bake parts (64 × 16): rim tile, gold peg, bulb off, bulb on (extras-pvp §10.1 #5). */
export function wheelParts() {
  const rimTile = image(16, 16);
  rect(rimTile, 0, 0, 16, 16, '#8C1834');
  for (let y = 0; y < 16; y += 3) rect(rimTile, 0, y, 16, 1, '#7A1430');
  rect(rimTile, 0, 0, 16, 1, K.gold);
  rect(rimTile, 0, 15, 16, 1, K.goldShade);
  const peg = image(16, 16);
  rect(peg, 6, 6, 3, 3, K.ink);
  put(peg, 7, 7, c(K.gold));
  put(peg, 6, 6, c(K.goldShade));
  put(peg, 7, 6, c(K.goldLight));
  const [off, on] = wheelBulbs();
  const b0 = image(16, 16);
  over(b0, off, 4, 4);
  const b1 = image(16, 16);
  over(b1, on, 4, 4);
  return hstrip([rimTile, peg, b0, b1]);
}

/** Wheel Party face overlay ring: 1 px white seam used when slices grow (drawn by code); here a frame of the hub. */
export const wheelHubStrip = () => vstrip(wheelHub());
