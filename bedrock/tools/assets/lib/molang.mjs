// Curves → Molang (docs/architecture/animation.md §2.9): Bedrock entity props move by Molang in generated
// animation files, from the same curves as core/logic/anim (ease.ts), so Java and Bedrock land on the same
// angle / offset. Two ways: an exact expression (`easeExpr`) or baked keyframes (`keyframes`). A tiny
// evaluator (`evalMolang`) lets tests compare the Molang against the TypeScript path (extras §14.7).
//
// Molang notes: math.sin/cos take DEGREES; `q.anim_time` is seconds; variables are `v.x` / `variable.x`.

const n = (x) => {
  const r = Math.round(x * 1e6) / 1e6;
  return Object.is(r, -0) ? '0' : String(r);
};

/** Clamps a Molang expression to 0..1. */
export const clamp01 = (e) => `math.clamp(${e}, 0, 1)`;

/**
 * Easing curves of global.md §2.5 as Molang in the variable `t` (an expression string, 0..1; clamp it first).
 * Names follow core/logic/anim/ease.ts.
 */
export function easeExpr(name, t = 't') {
  const u = `(1 - ${t})`;
  switch (name) {
    case 'linear':
      return `${t}`;
    case 'inQuad':
      return `(${t} * ${t})`;
    case 'outQuad':
      return `(1 - ${u} * ${u})`;
    case 'inOutQuad':
      return `(${t} < 0.5 ? 2 * ${t} * ${t} : 1 - math.pow(-2 * ${t} + 2, 2) / 2)`;
    case 'inCubic':
      return `(${t} * ${t} * ${t})`;
    case 'outCubic':
      return `(1 - math.pow(${u}, 3))`;
    case 'inOutCubic':
      return `(${t} < 0.5 ? 4 * ${t} * ${t} * ${t} : 1 - math.pow(-2 * ${t} + 2, 3) / 2)`;
    case 'outQuart':
      return `(1 - math.pow(${u}, 4))`;
    case 'outQuint':
      return `(1 - math.pow(${u}, 5))`;
    case 'inSine':
      return `(1 - math.cos(${t} * 90))`;
    case 'outSine':
      return `math.sin(${t} * 90)`;
    case 'inOutSine':
      return `((1 - math.cos(${t} * 180)) / 2)`;
    case 'outBack':
      return `(1 + 2.70158 * math.pow(${t} - 1, 3) + 1.70158 * math.pow(${t} - 1, 2))`;
    default:
      throw new Error(`molang: no expression for ease '${name}' (bake it with keyframes())`);
  }
}

/**
 * Bakes `fn(tSeconds) → number | number[]` into Bedrock keyframes over [0, durationS] every `stepS` seconds
 * (the last key is exactly at durationS). Values are rounded to 1e-4 (deterministic JSON).
 */
export function keyframes(fn, durationS, stepS = 0.05) {
  const out = {};
  const steps = Math.max(1, Math.round(durationS / stepS));
  const r = (v) => Math.round(v * 1e4) / 1e4 || 0;
  for (let i = 0; i <= steps; i++) {
    const t = i === steps ? durationS : i * stepS;
    const v = fn(t);
    out[n(t)] = Array.isArray(v) ? v.map(r) : [0, r(v), 0];
  }
  return out;
}

/** A piecewise-linear Molang expression through points [[x, y]…] of the variable `x` (sorted by x). */
export function piecewise(points, x = 'q.anim_time') {
  if (points.length === 1) return n(points[0][1]);
  let expr = n(points[points.length - 1][1]);
  for (let i = points.length - 2; i >= 0; i--) {
    const [x0, y0] = points[i];
    const [x1, y1] = points[i + 1];
    const k = (y1 - y0) / (x1 - x0);
    expr = `(${x} < ${n(x1)} ? ${n(y0)} + (${x} - ${n(x0)}) * ${n(k)} : ${expr})`;
  }
  return `(${x} < ${n(points[0][0])} ? ${n(points[0][1])} : ${expr.slice(1, -1)})`;
}

// ---- tiny evaluator (tests only; supports what the generator emits) ----------------------------------

const FNS = {
  'math.pow': Math.pow,
  'math.sin': (d) => Math.sin((d * Math.PI) / 180),
  'math.cos': (d) => Math.cos((d * Math.PI) / 180),
  'math.clamp': (v, a, b) => Math.min(Math.max(v, a), b),
  'math.min': Math.min,
  'math.max': Math.max,
  'math.abs': Math.abs,
  'math.floor': Math.floor,
  'math.ceil': Math.ceil,
  'math.round': Math.round,
  'math.sqrt': Math.sqrt,
  'math.exp': Math.exp,
  'math.mod': (a, b) => a % b,
  'math.lerp': (a, b, t) => a + (b - a) * t,
};

/** Evaluates a Molang expression with `vars` ({'t': 0.5, 'q.anim_time': 1.2, 'v.x': 3}). */
export function evalMolang(src, vars = {}) {
  const toks = src.match(/\s*(\d+\.?\d*|\.\d+|[A-Za-z_][\w.]*|<=|>=|==|!=|&&|\|\||[-+*/()<>?:,!])/g)?.map((s) => s.trim()) ?? [];
  let i = 0;
  const peek = () => toks[i];
  const eat = (t) => {
    if (toks[i] !== t) throw new Error(`molang: expected '${t}' at token ${i} ('${toks[i]}') in ${src}`);
    i++;
  };
  const lookup = (name) => {
    const alt = name.replace(/^variable\./, 'v.').replace(/^query\./, 'q.');
    if (name in vars) return vars[name];
    if (alt in vars) return vars[alt];
    throw new Error(`molang: unknown variable ${name}`);
  };
  function primary() {
    const t = toks[i++];
    if (t === '(') {
      const v = ternary();
      eat(')');
      return v;
    }
    if (t === '-') return -primary();
    if (t === '!') return primary() ? 0 : 1;
    if (/^[\d.]/.test(t)) return Number(t);
    if (peek() === '(') {
      const f = FNS[t];
      if (!f) throw new Error(`molang: unknown function ${t}`);
      eat('(');
      const args = [];
      if (peek() !== ')') {
        args.push(ternary());
        while (peek() === ',') {
          eat(',');
          args.push(ternary());
        }
      }
      eat(')');
      return f(...args);
    }
    return lookup(t);
  }
  const bin = (next, ops, apply) => () => {
    let v = next();
    while (ops.includes(peek())) {
      const op = toks[i++];
      v = apply(op, v, next());
    }
    return v;
  };
  const mul = bin(primary, ['*', '/'], (op, a, b) => (op === '*' ? a * b : a / b));
  const add = bin(mul, ['+', '-'], (op, a, b) => (op === '+' ? a + b : a - b));
  const cmp = bin(add, ['<', '>', '<=', '>=', '==', '!='], (op, a, b) =>
    Number({ '<': a < b, '>': a > b, '<=': a <= b, '>=': a >= b, '==': a === b, '!=': a !== b }[op]),
  );
  const and = bin(cmp, ['&&'], (_, a, b) => Number(a && b));
  const or = bin(and, ['||'], (_, a, b) => Number(a || b));
  function ternary() {
    const c = or();
    if (peek() !== '?') return c;
    eat('?');
    const a = ternary();
    eat(':');
    const b = ternary();
    return c ? a : b;
  }
  const v = ternary();
  if (i !== toks.length) throw new Error(`molang: trailing tokens in ${src}`);
  return v;
}
