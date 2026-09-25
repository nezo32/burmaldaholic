#!/usr/bin/env node
/* global console, process */
// ONE generator for all presentation art of the mod (docs/architecture/animation.md §5): PNGs (pixel art from
// string grids, palette of global.md §2.1) and Java `.mcmeta` sidecars, written into the java/ tree. No text is
// ever baked into a texture.
//
//   npm run gen:assets                                 # (in tools/) write every module's outputs
//   npm run check:assets                               # exit 1 if any committed output is stale
//   node assets/gen-assets.mjs --module slots          # one module
//
// Each module in assets/modules/<module>.mjs default-exports `generate(ctx)` returning outputs `{path, bytes}`
// (paths relative to java/, see assets/lib/emit.mjs). Modules are owned by their game lanes; the framework
// (lib/, this driver) by lane X-L0. Output paths must be inside the owning module's namespace (Java
// checkAssetOwnership).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '../..');
const JAVA_ROOT = path.join(REPO_ROOT, 'java');
const args = process.argv.slice(2);
const flag = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? (args[i + 1] ?? '') : undefined;
};
const check = args.includes('--check');
const onlyModule = flag('--module');

const modDir = path.join(HERE, 'modules');
const modules = fs
  .readdirSync(modDir)
  .filter((f) => f.endsWith('.mjs'))
  .map((f) => f.replace(/\.mjs$/, ''))
  .filter((m) => !onlyModule || m === onlyModule)
  .sort();

let stale = 0;
let written = 0;
for (const m of modules) {
  const { default: generate } = await import(pathToFileURL(path.join(modDir, `${m}.mjs`)).href);
  const outputs = await generate({ root: REPO_ROOT, javaRoot: JAVA_ROOT, module: m });
  for (const o of outputs) {
    const file = path.join(JAVA_ROOT, o.path);
    const current = fs.existsSync(file) ? fs.readFileSync(file) : undefined;
    if (current && current.equals(o.bytes)) continue;
    if (check) {
      console.error(`stale: java/${o.path} (module ${m})`);
      stale++;
      continue;
    }
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, o.bytes);
    written++;
  }
}
if (check && stale > 0) {
  console.error(`${stale} generated asset(s) are stale: run (cd tools && npm run gen:assets)`);
  process.exit(1);
}
console.info(check ? `assets OK (${modules.length} module(s))` : `wrote ${written} file(s) from ${modules.length} module(s)`);
