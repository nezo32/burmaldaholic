/**
 * Shared easing vocabulary (global.md §2.5, tables.md §0.2, extras-pvp.md §0.2). PURE. Twin of Java
 * `core.anim.Ease`; both are checked against `test/fx/vectors/core_anim.json` (docs/architecture/animation.md §3.4).
 * Numbers (roll-ups) never use outBack / outElastic / outBounce.
 */
export type EaseId =
  | 'linear'
  | 'outQuad'
  | 'outCubic'
  | 'inCubic'
  | 'inOutQuad'
  | 'inOutCubic'
  | 'inSine'
  | 'outSine'
  | 'inOutSine'
  | 'outQuint'
  | 'outBack'
  | 'outElastic'
  | 'outBounce'
  | 'shake';

/** Same order as the Java enum (the vector file lists them in this order). */
export const EASE_IDS: readonly EaseId[] = [
  'linear',
  'outQuad',
  'outCubic',
  'inCubic',
  'inOutQuad',
  'inOutCubic',
  'inSine',
  'outSine',
  'inOutSine',
  'outQuint',
  'outBack',
  'outElastic',
  'outBounce',
  'shake',
];

const clamp01 = (t: number): number => (t <= 0 ? 0 : t >= 1 ? 1 : t);

export function outBack(s: number, t: number): number {
  const x = clamp01(t);
  if (x === 1) return 1;
  const u = x - 1;
  return 1 + (s + 1) * u * u * u + s * u * u;
}

export function expDecay(k: number, t: number): number {
  const x = clamp01(t);
  if (x === 1) return 1;
  return (1 - Math.exp(-k * x)) / (1 - Math.exp(-k));
}

function outBounce(x: number): number {
  const n1 = 7.5625;
  const d1 = 2.75;
  if (x < 1 / d1) return n1 * x * x;
  if (x < 2 / d1) {
    const u = x - 1.5 / d1;
    return n1 * u * u + 0.75;
  }
  if (x < 2.5 / d1) {
    const u = x - 2.25 / d1;
    return n1 * u * u + 0.9375;
  }
  const u = x - 2.625 / d1;
  return n1 * u * u + 0.984375;
}

export function ease(id: EaseId, t: number): number {
  const x = clamp01(t);
  switch (id) {
    case 'linear':
      return x;
    case 'outQuad': {
      const u = 1 - x;
      return 1 - u * u;
    }
    case 'outCubic': {
      const u = 1 - x;
      return 1 - u * u * u;
    }
    case 'inCubic':
      return x * x * x;
    case 'inOutQuad': {
      if (x < 0.5) return 2 * x * x;
      const u = -2 * x + 2;
      return 1 - (u * u) / 2;
    }
    case 'inOutCubic': {
      if (x < 0.5) return 4 * x * x * x;
      const u = -2 * x + 2;
      return 1 - (u * u * u) / 2;
    }
    case 'inSine':
      return x === 1 ? 1 : 1 - Math.cos((x * Math.PI) / 2);
    case 'outSine':
      return x === 1 ? 1 : Math.sin((x * Math.PI) / 2);
    case 'inOutSine':
      return x === 1 ? 1 : -(Math.cos(Math.PI * x) - 1) / 2;
    case 'outQuint': {
      const u = 1 - x;
      return 1 - u * u * u * u * u;
    }
    case 'outBack':
      return outBack(1.70158, x);
    case 'outElastic':
      if (x === 0 || x === 1) return x;
      return Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * ((2 * Math.PI) / 3)) + 1;
    case 'outBounce':
      return outBounce(x);
    case 'shake':
      return Math.sin(6 * Math.PI * x) * (1 - x);
  }
}
