// Celebration / effect sprites (global.md §2.6, §5.1 #21–26, #30): rays, coin spin, confetti, sparkle,
// flying chips, stack columns, full-screen vignettes. White art is tinted in code where the spec says so.
import { CHIPS, alpha, rgba, shade } from '../../lib/palette.mjs';
import { ellipse, image, rect, setPx } from '../../lib/grid.mjs';

/** 64² white sun rays (16 wedges, soft falloff) — tinted in code. */
export function rays() {
  const img = image(64, 64);
  for (let y = 0; y < 64; y++)
    for (let x = 0; x < 64; x++) {
      const dx = x + 0.5 - 32;
      const dy = y + 0.5 - 32;
      const r = Math.hypot(dx, dy);
      if (r > 32 || r < 3) continue;
      const a = (Math.atan2(dy, dx) / (2 * Math.PI)) * 16;
      const w = Math.abs(a - Math.round(a)); // 0 at a ray centre
      if (w > 0.22) continue;
      const k = (1 - w / 0.22) * (1 - r / 32);
      setPx(img, x, y, alpha('#FFFFFF', Math.min(1, k * 1.6)));
    }
  return img;
}

/** Coin spin frame f of 8 (16²): width follows |cos|, back half darker. */
export function coinSpin(f) {
  const img = image(16, 16);
  const ang = (f / 8) * 2 * Math.PI;
  const rx = Math.max(1, Math.abs(Math.cos(ang)) * 6.5);
  const back = Math.cos(ang) < 0;
  const face = back ? '#D8A020' : '#FFD640';
  const rim = back ? '#8A5A0C' : '#B07010';
  ellipse(img, 8, 8, rx, 6.5, (x, y, d) => (d > 0.78 ? rim : face));
  if (!back && rx > 3) {
    setPx(img, Math.round(8 - rx / 2), 5, '#FFF3A0');
    setPx(img, Math.round(8 - rx / 2), 6, '#FFF3A0');
  }
  return img;
}

const CONFETTI = ['#FFD640', '#D83440', '#80FF40', '#5CE8E0', '#D696FF', '#F4ECF8'];
/** Confetti sheet 24 × 8: 6 colours (columns of 4 px) × 2 frames (rows): flat square / edge-on sliver. */
export function confettiSheet() {
  const img = image(24, 8);
  CONFETTI.forEach((c, i) => {
    rect(img, i * 4, 0, i * 4 + 3, 3, c);
    setPx(img, i * 4 + 3, 3, shade(c, 0.7));
    rect(img, i * 4, 5, i * 4 + 3, 6, shade(c, 0.8));
  });
  return img;
}

/** 7² four-point sparkle, frame f of 4 (grow, full, twinkle, shrink); lilac core. */
export function sparkle(f) {
  const img = image(7, 7);
  const arm = [1, 3, 2, 1][f];
  const core = f === 2 ? '#FFFFFF' : 'lilac';
  for (let i = 1; i <= arm; i++) {
    for (const [dx, dy] of [[i, 0], [-i, 0], [0, i], [0, -i]]) setPx(img, 3 + dx, 3 + dy, i === arm ? alpha('lilac', 0.7) : 'lilac');
  }
  setPx(img, 3, 3, core);
  return img;
}

/** Flying chip 8² (top-down, denomination colours, ink outline). */
export function chipTop(d) {
  const { base, stripe } = CHIPS[d];
  const img = image(8, 8);
  ellipse(img, 4, 4, 4, 4, (x, y, r) => {
    if (r > 0.82) return '#180A28';
    if (r > 0.6) return (x + y) % 2 === 0 ? stripe : base;
    if (r > 0.35) return base;
    return d === 1 ? '#C8C8C8' : stripe;
  });
  return img;
}

/** Stack column slice 12 × 3 (side view: top highlight, body with edge inserts, shadow). */
export function chipSide(d) {
  const { base, stripe } = CHIPS[d];
  const img = image(12, 3);
  rect(img, 1, 0, 10, 0, shade(base, 1.15));
  rect(img, 0, 1, 11, 1, base);
  for (const x of [2, 5, 8]) rect(img, x, 1, x + 1, 1, stripe);
  rect(img, 1, 2, 10, 2, shade(base, 0.6));
  return img;
}

/** 256² radial vignette: transparent centre → colour at the edges (alpha up to `max`). */
export function vignette(c, max = 0.75) {
  const img = image(256, 256);
  const [r, g, b] = rgba(c);
  for (let y = 0; y < 256; y++)
    for (let x = 0; x < 256; x++) {
      const d = Math.hypot((x + 0.5 - 128) / 128, (y + 0.5 - 128) / 128);
      const k = Math.min(1, Math.max(0, (d - 0.55) / 0.6));
      const a = Math.round(max * k * k * 255);
      if (a) img.data.set([r, g, b, a], (y * 256 + x) * 4);
    }
  return img;
}

/** Coin spin strip (8 frames, vertical) for the Java GUI sprite; the same frames feed the particle atlas. */
export const coinSpinFrames = () => Array.from({ length: 8 }, (_, f) => coinSpin(f));
