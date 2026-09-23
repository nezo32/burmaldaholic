/**
 * Everything tools/gen-structures.mjs writes into packs/worldgen/. PURE (returns bytes only).
 * Regenerate with `node src/worldgen/tools/gen-structures.mjs`; worldgen.test.ts fails when the
 * committed templates drift from this output.
 */
import { allLayouts } from './layouts';
import { encodeMcstructure, structurePath } from './mcstructure';

export interface GeneratedFile {
  /** path relative to packs/worldgen/ */
  readonly path: string;
  readonly bytes: Uint8Array;
}

export function generatedFiles(): GeneratedFile[] {
  return [...allLayouts().values()].map((l) => ({ path: structurePath(l), bytes: encodeMcstructure(l.grid) }));
}
