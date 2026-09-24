#!/usr/bin/env node
/* global console, process */
// ONE generator for all presentation art of both editions (docs/architecture/animation.md §5):
// PNGs (pixel art from string grids, palette of global.md §2.1), Java `.mcmeta`, Bedrock flipbook /
// particle / geometry / animation JSON (generated Molang constants). No text is ever baked into a texture.
//
//   node tools/gen-assets.mjs                     # write every module's outputs (both editions)
//   node tools/gen-assets.mjs --check             # exit 1 if any committed output is stale
//   node tools/gen-assets.mjs --module slots      # one module
//   node tools/gen-assets.mjs --edition bedrock   # skip the Java tree (e.g. no ../java checkout)
//
// Each module in tools/assets/modules/<module>.mjs default-exports `generate(ctx)` returning outputs
// `{edition, path, bytes}` (see tools/assets/lib/emit.mjs). Modules are owned by their game lanes; the
// framework (lib/, this driver) by lane X-L0. Output paths must be inside the owning module's namespace
// (Java checkAssetOwnership, Bedrock packs/<module>/).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const JAVA_ROOT = path.resolve(ROOT, '../java');
const args = process.argv.slice(2);
const flag = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? (args[i + 1] ?? '') : undefined;
};
const check = args.includes('--check');
const onlyModule = flag('--module');
const edition = flag('--edition') ?? 'all';

const modDir = path.join(ROOT, 'tools/assets/modules');
const modules = fs
  .readdirSync(modDir)
  .filter((f) => f.endsWith('.mjs'))
  .map((f) => f.replace(/\.mjs$/, ''))
  .filter((m) => !onlyModule || m === onlyModule)
  .sort();

const rootOf = (ed) => (ed === 'java' ? JAVA_ROOT : ROOT);
let stale = 0;
let written = 0;
for (const m of modules) {
  const { default: generate } = await import(pathToFileURL(path.join(modDir, `${m}.mjs`)).href);
  const outputs = await generate({ root: ROOT, javaRoot: JAVA_ROOT, module: m });
  for (const o of outputs) {
    if (edition !== 'all' && o.edition !== edition) continue;
    if (o.edition === 'java' && !fs.existsSync(JAVA_ROOT)) continue;
    const file = path.join(rootOf(o.edition), o.path);
    const current = fs.existsSync(file) ? fs.readFileSync(file) : undefined;
    if (current && current.equals(o.bytes)) continue;
    if (check) {
      console.error(`stale: ${o.edition}/${o.path} (module ${m})`);
      stale++;
      continue;
    }
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, o.bytes);
    written++;
  }
}
if (check && stale > 0) {
  console.error(`${stale} generated asset(s) are stale: run node tools/gen-assets.mjs`);
  process.exit(1);
}
console.info(check ? `assets OK (${modules.length} module(s))` : `wrote ${written} file(s) from ${modules.length} module(s)`);
