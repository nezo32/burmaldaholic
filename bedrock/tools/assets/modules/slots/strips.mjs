// Reel strips for the art (animation/slots.md §9: "the strip textures read the strips from the config defaults so
// art never drifts from the maths"). Source order:
//   1. the v2 engine itself — `src/games/slots/v2/logic` bundled with esbuild (lane B-L8 owns it); any export that
//      yields a MachineDef ({codes, strips}) for 'overworld' | 'nether' | 'end' is used (e.g. `defaultMachine(id)`);
//   2. fallback while the engine is a skeleton: SLOTS.md Appendix A (normative), parsed from the design doc.
// Re-running the generator after the engine lands switches to (1) automatically; `source` says which one was used.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { CODES, MACHINE_ORDER } from './symbols.mjs';

/** @returns {Promise<{source: string, machines: Record<string, {codes: string[], strips: number[][]}>}>} */
export async function loadStrips(root) {
  const fromEngine = await tryEngine(root);
  if (fromEngine) return { source: 'engine', machines: fromEngine };
  return { source: 'SLOTS.md Appendix A', machines: parseAppendixA(fs.readFileSync(path.resolve(root, '../docs/design/SLOTS.md'), 'utf8')) };
}

async function tryEngine(root) {
  const entry = path.join(root, 'src/games/slots/v2/logic/index.ts');
  if (!fs.existsSync(entry)) return undefined;
  let mod;
  try {
    const esbuild = await import('esbuild');
    const out = await esbuild.build({ entryPoints: [entry], bundle: true, write: false, format: 'esm', platform: 'neutral', logLevel: 'silent' });
    const dir = fs.mkdtempSync(path.join(root, 'node_modules/.cache-slots-art-'));
    const file = path.join(dir, 'v2-logic.mjs');
    try {
      fs.writeFileSync(file, out.outputFiles[0].text);
      mod = await import(pathToFileURL(file).href);
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  } catch {
    return undefined;
  }
  const isDef = (d) => d && Array.isArray(d.strips) && d.strips.length === 5 && Array.isArray(d.codes);
  const found = {};
  for (const m of MACHINE_ORDER) {
    for (const v of Object.values(mod)) {
      let d;
      try {
        if (typeof v === 'function' && v.length <= 2) d = v(m);
        else if (v && typeof v === 'object') d = v[m];
      } catch {
        d = undefined;
      }
      if (isDef(d) && (d.machine === undefined || d.machine === m)) {
        found[m] = { codes: [...d.codes], strips: d.strips.map((s) => [...s]) };
        break;
      }
    }
  }
  return MACHINE_ORDER.every((m) => found[m]) ? found : undefined;
}

/** SLOTS.md Appendix A: "### A.1 … ``` R1: AP CA … ```" blocks in machine order. */
export function parseAppendixA(md) {
  const start = md.indexOf('## Appendix A');
  if (start < 0) throw new Error('SLOTS.md: Appendix A not found');
  const blocks = [...md.slice(start).matchAll(/```\n([\s\S]*?)```/g)].slice(0, 3).map((m) => m[1]);
  const out = {};
  MACHINE_ORDER.forEach((m, i) => {
    const codes = CODES[m];
    const strips = blocks[i]
      .trim()
      .split('\n')
      .map((line) => line.replace(/^R\d:\s*/, '').trim().split(/\s+/).map((c) => {
        const k = codes.indexOf(c);
        if (k < 0) throw new Error(`SLOTS.md Appendix A: unknown code ${c} for ${m}`);
        return k;
      }));
    if (strips.length !== 5) throw new Error(`SLOTS.md Appendix A: ${m} has ${strips.length} reels`);
    out[m] = { codes: [...codes], strips };
  });
  return out;
}
