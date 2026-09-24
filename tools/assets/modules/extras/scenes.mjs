// Themed scenes for the extras / PvP screens (extras.md §2.3): 400 × 240 backdrops (1 art px = 1 GUI px, like the
// slots backdrops), nine-slice frames (64², border 12) with the house gold trim in each theme's material, marquee
// bulb strips (128 × 12 × 4) and blank title banners (48², border 12). Menu-shell scenes live in core/menu.mjs.
import {
  K, bayer, blend, bulb, c, disc, image, line, mixHex, put, rect, rng, speckle, stud, vgrad, vignette, vstrip,
} from './kit.mjs';

const W = 400;
const H = 240;

/** Theme materials for frames, marquees and banners. */
export const SCENE = {
  coin: { mat: ['#5A3418', '#6E4020', '#48280E', '#3A2010'], trim: 'gold', inner: '#1E5E3A', lamp: '#FFD640', panel: '#1A0E08' },
  wheel: { mat: ['#A0203C', '#B82A48', '#8C1834', '#6A1028'], trim: 'gold', inner: '#FFD640', lamp: '#FFE070', panel: '#2A0610' },
  plinko: { mat: ['#241A5A', '#2E2270', '#1C1448', '#140E34'], trim: 'gold', inner: '#40E0FF', lamp: '#40E0FF', panel: '#0A0720' },
  scratch: { mat: ['#1E7A6A', '#27907C', '#16604F', '#0E4A3C'], trim: 'gold', inner: '#F4ECD8', lamp: '#FFE070', panel: '#08241E' },
  pvp: { mat: ['#3A3A48', '#4A4A5A', '#2E2E3A', '#22222C'], trim: 'gold', inner: '#BE5AFF', lamp: '#FFD640', panel: '#12121A' },
  grudge: { mat: ['#4A0A10', '#5E1018', '#3A060C', '#2A0408'], trim: 'blood', inner: '#FF2A2A', lamp: '#FF4A30', panel: '#1A0204' },
};

const TRIM = {
  gold: ['#FFF4B0', '#FFD640', '#E8B830', '#B07010'],
  blood: ['#FF9A8A', '#D83440', '#A8202C', '#5A0610'],
};

/** Nine-slice frame 64 × 64 (border 12), transparent centre (the backdrop shows through). */
export function sceneFrame(theme) {
  const S = SCENE[theme];
  const [tl, t, tm, td] = TRIM[S.trim];
  const img = image(64, 64);
  const r = rng(theme.length * 131);
  for (let y = 0; y < 64; y++)
    for (let x = 0; x < 64; x++) {
      if (x >= 12 && x < 52 && y >= 12 && y < 52) continue;
      let hex;
      if (theme === 'coin') hex = S.mat[(y + (x > 31 ? 3 : 0)) % 6 === 5 ? 3 : ((x * 3 + y) >> 3) % 2];
      else if (theme === 'pvp' || theme === 'grudge') hex = (x + y) % 7 === 0 ? S.mat[1] : S.mat[(r() * 3) | 0 ? 0 : 2];
      else if (theme === 'plinko') hex = (x + y) % 8 < 1 ? S.mat[1] : S.mat[0];
      else if (theme === 'scratch') hex = y % 4 === 0 ? S.mat[1] : S.mat[0];
      else hex = (x * 2 + y) % 9 === 0 ? S.mat[1] : S.mat[0];
      put(img, x, y, c(hex));
    }
  const band = (i, lt, dk) => {
    rect(img, i, i, 64 - 2 * i, 1, lt);
    rect(img, i, i, 1, 64 - 2 * i, lt);
    rect(img, i, 63 - i, 64 - 2 * i, 1, dk);
    rect(img, 63 - i, i, 1, 64 - 2 * i, dk);
  };
  band(0, K.ink, K.ink);
  band(1, tl, td);
  band(2, t, tm);
  band(3, tm, td);
  band(8, td, tl);
  band(9, t, tm);
  band(10, tl, td);
  band(11, K.ink, K.ink);
  // inner accent line just outside the window
  band(10, S.inner, mixHex(S.inner, '#000000', 0.4));
  // corner studs and edge lamps (symmetric so the tiled edges repeat cleanly)
  for (const [x, y] of [[5, 5], [58, 5], [5, 58], [58, 58]]) stud(img, x, y, t, K.white, 1.8);
  for (let k = 20; k <= 44; k += 8) for (const [x, y] of [[k, 5], [k, 58], [5, k], [58, k]]) put(img, x, y, c(S.lamp));
  if (theme === 'grudge')
    for (const [x0, y0] of [[48, 2], [2, 48]])
      for (let i = 0; i < 3; i++) for (let k = 0; k < 7; k++) put(img, x0 + i * 2 + (k >> 2), y0 + k, c('#140000'));
  return img;
}

/** Marquee strip 128 × 12 × 4 frames (V + mcmeta frametime 3): chasing bulbs in the theme's lamp colour. */
export function marquee(theme) {
  const S = SCENE[theme];
  const frames = [];
  for (let f = 0; f < 4; f++) {
    const img = image(128, 12);
    rect(img, 0, 0, 128, 12, K.ink);
    rect(img, 1, 1, 126, 10, S.panel);
    rect(img, 1, 1, 126, 1, TRIM[S.trim][0]);
    rect(img, 1, 10, 126, 1, TRIM[S.trim][3]);
    for (let i = 0; i < 20; i++) bulb(img, 4 + Math.round(i * 6.25), 5, (i + f) % 4 === 0 || (i + f) % 4 === 2 && theme === 'wheel', S.lamp, mixHex(S.panel, S.lamp, 0.25));
    frames.push(img);
  }
  return vstrip(frames);
}

/** Title banner nine-slice (48 × 24, border 10): theme panel with the house trim; the title is drawn by code. */
export function banner(theme) {
  const S = SCENE[theme];
  const [tl, t, , td] = TRIM[S.trim];
  const img = image(48, 24);
  rect(img, 0, 0, 48, 24, K.ink);
  rect(img, 1, 1, 46, 22, td);
  rect(img, 2, 2, 44, 20, t);
  rect(img, 2, 2, 44, 1, tl);
  rect(img, 4, 4, 40, 16, S.panel);
  rect(img, 4, 4, 40, 1, mixHex(S.panel, '#FFFFFF', 0.12));
  for (const [x, y] of [[3, 3], [44, 3], [3, 20], [44, 20]]) put(img, x, y, c(K.white));
  return img;
}

// ---- backdrops ------------------------------------------------------------------------------------------------------
function curtain(img, x0, w, flip, hexes = ['#8C1834', '#B02440', '#6A1028', '#4A0A1C']) {
  for (let y = 0; y < H; y++)
    for (let x = 0; x < w; x++) {
      const u = flip ? w - 1 - x : x;
      const fold = Math.sin(u * 0.55 + y * 0.01);
      const hex = fold > 0.6 ? hexes[1] : fold > -0.2 ? hexes[0] : fold > -0.7 ? hexes[2] : hexes[3];
      // swag: the curtain is tied back — its inner edge curves out lower down
      const edge = w - 1 - Math.round(Math.max(0, Math.sin((y / H) * Math.PI) * w * 0.45));
      if (u > edge) continue;
      put(img, x0 + x, y, c(hex));
    }
  // tassel
  const tx = flip ? x0 + 6 : x0 + w - 10;
  rect(img, tx, 120, 4, 3, K.gold);
  for (let k = 0; k < 10; k++) put(img, tx + (k % 4), 123 + (k >> 1), c(k % 2 ? K.goldShade : K.gold));
}

/** Lucky Coin back room: green damask wall, brass lamp with a warm light cone, velvet curtains, a baize table edge. */
function coinBackdrop() {
  const img = image(W, H);
  for (let y = 0; y < H; y++)
    for (let x = 0; x < W; x++) {
      const u = x % 20;
      const v = y % 24;
      const dam = Math.abs(u - 10) + Math.abs(v - 12) * 0.8;
      let hex = dam < 3 || (dam > 7 && dam < 8.2) ? '#1E4A30' : '#143622';
      if ((x + y) % 2 === 0 && dam < 1.5) hex = '#2A5A3C';
      put(img, x, y, c(hex));
    }
  // wainscot
  rect(img, 0, 150, W, 90, '#3A2010');
  for (let x = 8; x < W; x += 56) {
    rect(img, x, 160, 44, 40, '#48280E');
    rect(img, x, 160, 44, 1, '#6E4020');
    rect(img, x, 199, 44, 1, '#2A160A');
  }
  rect(img, 0, 148, W, 3, K.goldShade);
  rect(img, 0, 148, W, 1, K.gold);
  // light cone from the lamp
  for (let y = 18; y < 210; y++)
    for (let x = 0; x < W; x++) {
      const half = 14 + (y - 18) * 0.62;
      const d = Math.abs(x - 200) / half;
      if (d > 1) continue;
      const a = (1 - d) * (1 - (y - 18) / 260) * 0.5;
      if (bayer(x, y) < a) blend(img, x, y, c('#FFE8A0'), 0.35);
    }
  // lamp
  line(img, 200, 0, 200, 8, c('#3A2A1A'));
  for (let y = 8; y < 20; y++) {
    const half = 3 + (y - 8) * 1.4;
    rect(img, 200 - half, y, half * 2, 1, y === 19 ? K.goldShade : y < 11 ? K.gold : '#1E5E3A');
  }
  disc(img, 200, 20, 4, c('#FFF4B0'));
  // table edge
  rect(img, 0, 210, W, 30, '#1E5E3A');
  for (let x = 0; x < W; x++) for (let y = 210; y < 240; y++) if ((x * 7 + y * 3) % 13 === 0) put(img, x, y, c('#2A7A4A'));
  rect(img, 0, 206, W, 5, '#5A3418');
  rect(img, 0, 206, W, 1, '#7A4A24');
  rect(img, 0, 210, W, 1, K.gold);
  curtain(img, 0, 70, false);
  curtain(img, W - 70, 70, true);
  rect(img, 0, 0, W, 10, '#4A0A1C');
  for (let x = 0; x < W; x += 10) {
    disc(img, x + 5, 10, 5, c('#8C1834'));
    put(img, x + 5, 14, c(K.gold));
  }
  return vignette(img, 0.55);
}

/** Carnival night: tent canopy, string lights, a far ferris wheel, boardwalk. */
function wheelBackdrop() {
  const img = image(W, H);
  vgrad(img, 0, 0, W, H, c('#140822'), c('#3A1450'), 10);
  speckle(img, 0, 0, W, 150, 110, ['#FFFFFF', '#D696FF', '#8A7AB8'], 707, 0.9);
  // far ferris wheel silhouette
  const fx = 320;
  const fy = 118;
  for (let a = 0; a < 360; a += 1) {
    const rad = (a * Math.PI) / 180;
    put(img, Math.round(fx + Math.cos(rad) * 52), Math.round(fy + Math.sin(rad) * 52), c('#5A2A80'));
    if (a % 30 === 0) {
      line(img, fx, fy, Math.round(fx + Math.cos(rad) * 52), Math.round(fy + Math.sin(rad) * 52), c('#5A2A80'));
      rect(img, Math.round(fx + Math.cos(rad) * 52) - 3, Math.round(fy + Math.sin(rad) * 52) + 1, 7, 5, '#3A1A5C');
      put(img, Math.round(fx + Math.cos(rad) * 52), Math.round(fy + Math.sin(rad) * 52) + 3, c(a % 60 ? '#FFB040' : '#FF6E6A'));
    }
  }
  line(img, fx - 30, 200, fx, fy, c('#5A2A80'));
  line(img, fx + 30, 200, fx, fy, c('#5A2A80'));
  // tents silhouettes
  for (const [x, w, h] of [[20, 80, 60], [110, 60, 44]]) {
    for (let y = 0; y < h; y++) {
      const half = (y / h) * (w / 2);
      rect(img, Math.round(x + w / 2 - half), 200 - h + y, Math.round(half * 2), 1, '#3A1856');
    }
    put(img, x + w / 2, 200 - h - 2, c('#FFD640'));
  }
  // boardwalk
  rect(img, 0, 200, W, 40, '#3A2010');
  for (let y = 200; y < 240; y += 5) rect(img, 0, y, W, 1, '#2A160A');
  for (let x = 0; x < W; x += 23) rect(img, x + ((x / 23) % 2) * 9, 200, 1, 40, '#2A160A');
  rect(img, 0, 200, W, 1, '#5A3418');
  // canopy with scalloped stripes
  for (let y = 0; y < 26; y++)
    for (let x = 0; x < W; x++) {
      const stripe = Math.floor(x / 16) % 2;
      const scallop = 20 + Math.round(5 * Math.sqrt(Math.max(0, 1 - ((x % 16) - 7.5) ** 2 / 64)));
      if (y > scallop) continue;
      put(img, x, y, c(stripe ? '#F4ECD8' : '#D83440'));
      if (y === scallop) put(img, x, y, c(stripe ? '#C0B0A0' : '#8C1834'));
    }
  // string lights (catenaries)
  for (const [x0, x1, sag, y0] of [[0, 200, 22, 40], [200, 400, 22, 40], [0, 400, 40, 70]]) {
    for (let x = x0; x < x1; x++) {
      const t = (x - x0) / (x1 - x0);
      const y = Math.round(y0 + sag * 4 * t * (1 - t));
      put(img, x, y, c('#1A0E10'));
      if ((x - x0) % 16 === 8) bulb(img, x, y + 3, true, ['#FFD640', '#FF6E6A', '#80FF40', '#5CE8E0'][((x - x0) / 16) % 4 | 0]);
    }
  }
  return vignette(img, 0.5);
}

/** Arcade: indigo room, neon perspective grid floor, cabinet silhouettes with glowing screens. */
function plinkoBackdrop() {
  const img = image(W, H);
  vgrad(img, 0, 0, W, 150, c('#0A0720'), c('#1C1448'), 10);
  speckle(img, 0, 0, W, 140, 60, ['#6A5AB8', '#FFFFFF'], 313, 0.6);
  // cabinets
  for (let i = 0; i < 7; i++) {
    const x = 6 + i * 58;
    rect(img, x, 70, 44, 80, '#140E34');
    rect(img, x, 70, 44, 1, '#2E2270');
    rect(img, x + 6, 80, 32, 24, ['#40E0FF', '#FF40C0', '#80FF40', '#FFD640'][i % 4]);
    rect(img, x + 7, 81, 30, 22, '#0A0720');
    for (let k = 0; k < 6; k++) put(img, x + 10 + k * 4, 88 + (k * 5) % 9, c(['#40E0FF', '#FF40C0', '#FFD640'][k % 3]));
    rect(img, x + 4, 112, 36, 6, '#2E2270');
    disc(img, x + 14, 115, 2, c('#D83440'));
    disc(img, x + 26, 115, 2, c('#3D6AB0'));
  }
  // neon wall strip
  rect(img, 0, 60, W, 1, '#FF40C0');
  rect(img, 0, 61, W, 1, '#6A1A5A');
  // floor grid in perspective
  rect(img, 0, 150, W, 90, '#0A0616');
  for (let k = 0; k < 9; k++) {
    const y = 150 + Math.round(90 * (k / 8) ** 1.8);
    rect(img, 0, y, W, 1, k % 2 ? '#40E0FF' : '#2A80A0');
  }
  for (let k = -12; k <= 12; k++) line(img, 200 + k * 10, 150, 200 + k * 60, 240, c('#6A1A8A'));
  rect(img, 0, 150, W, 1, '#40E0FF');
  return vignette(img, 0.5);
}

/** Corner kiosk: striped wallpaper, ticket-roll shelf, a horseshoe, a glass counter. */
function scratchBackdrop() {
  const img = image(W, H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) put(img, x, y, c(Math.floor(x / 10) % 2 ? '#F0E4C8' : '#E4D4B0'));
  for (let y = 0; y < H; y += 20) for (let x = 5; x < W; x += 20) put(img, x + ((y / 20) % 2) * 10, y + 10, c('#1E7A6A'));
  // shelf with ticket rolls
  rect(img, 0, 58, W, 6, '#5A3418');
  rect(img, 0, 58, W, 1, '#7A4A24');
  const rolls = ['#D83440', '#FFD640', '#40D060', '#3D6AB0', '#BE5AFF', '#FF7A1A'];
  for (let i = 0; i < 16; i++) {
    const x = 8 + i * 24;
    const hex = rolls[i % rolls.length];
    rect(img, x, 36, 18, 22, K.ink);
    rect(img, x + 1, 37, 16, 20, hex);
    rect(img, x + 1, 37, 16, 2, mixHex(hex, '#FFFFFF', 0.4));
    for (let y = 41; y < 56; y += 3) rect(img, x + 3, y, 12, 1, mixHex(hex, '#000000', 0.25));
  }
  // horseshoe (lucky) centre top
  for (let a = 200; a <= 340; a += 4) {
    const rad = (a * Math.PI) / 180;
    disc(img, 200 + Math.cos(rad) * 12, 22 - Math.sin(rad) * 12, 2.2, c('#8A8A96'));
  }
  // posters without text: coloured frames with a coin / star motif
  for (const [x, hex] of [[40, '#D83440'], [330, '#1E7A6A']]) {
    rect(img, x, 80, 34, 44, K.ink);
    rect(img, x + 1, 81, 32, 42, hex);
    disc(img, x + 17, 100, 9, c(K.gold));
    disc(img, x + 17, 100, 6, c(K.goldShade));
    for (let k = 0; k < 4; k++) rect(img, x + 5, 114 + k * 2, 24, 1, mixHex(hex, '#FFFFFF', 0.3));
  }
  // counter
  rect(img, 0, 196, W, 44, '#5A3418');
  for (let x = 0; x < W; x += 40) rect(img, x, 202, 1, 38, '#3A2010');
  rect(img, 0, 190, W, 7, '#A8D8E0');
  rect(img, 0, 190, W, 1, '#FFFFFF');
  rect(img, 0, 196, W, 1, '#6A9AA8');
  return vignette(img, 0.6, 0.72);
}

/** Arena: dark stage with two spotlights (blue left, red right), crowd silhouettes, pennants. */
function arenaBackdrop(grudge = false) {
  const img = image(W, H);
  vgrad(img, 0, 0, W, H, c(grudge ? '#1A0206' : '#0E0818'), c(grudge ? '#3A0A10' : '#26103C'), 10);
  // spotlights
  for (const [sx, hex] of [[60, grudge ? '#FF4A30' : '#4A8AFF'], [340, '#FF3A4A']])
    for (let y = 0; y < 200; y++)
      for (let x = 0; x < W; x++) {
        const cx = sx + (200 - sx) * (y / 200) * 0.9;
        const half = 6 + y * 0.35;
        const d = Math.abs(x - cx) / half;
        if (d > 1) continue;
        if (bayer(x, y) < (1 - d) * 0.45) blend(img, x, y, c(hex), 0.28);
      }
  // pennants
  for (let x = 0; x < W; x++) {
    const y = Math.round(18 + 10 * Math.sin((x / W) * Math.PI));
    put(img, x, y, c('#1A0E10'));
    if (x % 18 === 0)
      for (let k = 0; k < 8; k++) rect(img, x + 1 + (k >> 1), y + 1 + k, 8 - k, 1, (x / 18) % 2 ? (grudge ? '#8B0000' : '#3D6AB0') : '#D83440');
  }
  // floor ring
  for (let y = 186; y < 212; y++)
    for (let x = 60; x < 340; x++) {
      const d = Math.hypot((x - 200) / 140, (y - 199) / 13);
      if (d < 1) put(img, x, y, c(d > 0.9 ? K.goldShade : d > 0.85 ? K.gold : grudge ? '#2A0608' : '#1C1030'));
    }
  // crowd rows
  const r = rng(grudge ? 99 : 98);
  for (let row = 0; row < 3; row++) {
    const y0 = 206 + row * 11;
    for (let x = -6; x < W; x += 9 + ((r() * 4) | 0)) {
      const hx = x + ((r() * 3) | 0);
      const col = row === 0 ? '#1A1020' : row === 1 ? '#140C18' : '#0E0812';
      disc(img, hx + 4, y0, 3.6, c(col));
      rect(img, hx, y0 + 3, 9, 16, col);
      if (r() < 0.12) rect(img, hx + 7, y0 - 8, 1, 8, col);
      if (r() < 0.1) put(img, hx + 7, y0 - 9, c(grudge ? '#FF7A1A' : '#FFD640'));
    }
  }
  if (grudge)
    for (let i = 0; i < 3; i++)
      for (let k = 0; k < 40; k++) {
        const x = 300 + i * 14 + (k >> 2);
        blend(img, x, 30 + k, c('#000000'), 0.7);
        blend(img, x + 1, 30 + k, c('#FF2A2A'), 0.5);
      }
  return vignette(img, 0.6);
}

export const BACKDROPS = {
  coin: coinBackdrop,
  wheel: wheelBackdrop,
  plinko: plinkoBackdrop,
  scratch: scratchBackdrop,
  arena: () => arenaBackdrop(false),
  arena_grudge: () => arenaBackdrop(true),
};
