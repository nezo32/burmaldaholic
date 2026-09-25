// Reel strips for the art (animation/slots.md §9: "the strip textures read the strips from the config defaults so
// art never drifts from the maths"). Source: SLOTS.md Appendix A (normative; the Java v2 engine's MachineDef
// defaults are the same strips), parsed from the design doc.
import fs from 'node:fs';
import path from 'node:path';
import { CODES, MACHINE_ORDER } from './symbols.mjs';

/** @returns {{source: string, machines: Record<string, {codes: string[], strips: number[][]}>}} */
export function loadStrips(repoRoot) {
  return { source: 'SLOTS.md Appendix A', machines: parseAppendixA(fs.readFileSync(path.resolve(repoRoot, 'docs/design/SLOTS.md'), 'utf8')) };
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
