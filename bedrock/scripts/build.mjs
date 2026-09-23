// Build: lang merge+check -> esbuild bundle -> assemble build/BP + build/RP -> dist/*.mcaddon
// Flags: --dev (sourcemap, no minify), --no-zip
import fs from 'node:fs';
import path from 'node:path';
import * as esbuild from 'esbuild';
import { zipSync } from 'fflate';
import { buildLang, renderLang } from './lang.mjs';
import { LANGS, MODULES, PACK, ROOT, fail, readJson, rel, walk } from './lib.mjs';

const args = new Set(process.argv.slice(2));
const DEV = args.has('--dev');
const BUILD = path.join(ROOT, 'build');
const DIST = path.join(ROOT, 'dist');
const BP = path.join(BUILD, 'BP');

// Version: MOD_VERSION (set by the release workflow from the tag) > package.json "version".
// Manifest v3 takes a semver string; the pre-release/build suffix is dropped for safety.
const RAW_VERSION = process.env.MOD_VERSION || readJson('package.json').version;
const VERSION = /^v?(\d+)\.(\d+)\.(\d+)/.exec(RAW_VERSION)?.slice(1, 4).join('.');
if (!VERSION) {
  console.error(`invalid version '${RAW_VERSION}' (expected X.Y.Z)`);
  process.exit(1);
}
const RP = path.join(BUILD, 'RP');

// Shared registry files that several modules may contribute to: deep-merged instead of
// "one owner per path". Everything else must have exactly one owner module.
const MERGEABLE = new Set([
  'RP/textures/item_texture.json',
  'RP/textures/terrain_texture.json',
  'RP/textures/flipbook_textures.json',
  'RP/sounds/sound_definitions.json',
  'RP/sounds/music_definitions.json',
  'RP/sounds.json',
  'RP/blocks.json',
  'BP/item_catalog/crafting_item_catalog.json',
]);

function deepMerge(a, b, where, errors) {
  if (Array.isArray(a) && Array.isArray(b)) return [...a, ...b];
  if (a && b && typeof a === 'object' && typeof b === 'object') {
    const out = { ...a };
    for (const [k, v] of Object.entries(b)) out[k] = k in a ? deepMerge(a[k], v, `${where}.${k}`, errors) : v;
    return out;
  }
  if (JSON.stringify(a) !== JSON.stringify(b)) errors.push(`merge conflict at ${where}`);
  return a;
}

function checkVersions(errors) {
  const pkg = readJson('package.json');
  for (const [mod, ver] of Object.entries(PACK.scriptModules)) {
    const dev = pkg.devDependencies?.[mod];
    if (dev !== ver) errors.push(`pack.json uses ${mod}@${ver} but package.json devDependency is ${dev} (typings must match the manifest)`);
  }
}

function manifests() {
  const common = {
    version: VERSION,
    min_engine_version: PACK.minEngineVersion,
  };
  const metadata = { authors: PACK.authors, license: PACK.license, product_type: 'addon' };
  const bp = {
    format_version: 3,
    header: { name: 'pack.name', description: 'pack.description', uuid: PACK.uuids.bpHeader, ...common },
    modules: [
      { type: 'data', uuid: PACK.uuids.bpData, version: VERSION },
      { type: 'script', language: 'javascript', entry: 'scripts/main.js', uuid: PACK.uuids.bpScript, version: VERSION },
    ],
    dependencies: [
      { uuid: PACK.uuids.rpHeader, version: VERSION },
      ...Object.entries(PACK.scriptModules).map(([module_name, version]) => ({ module_name, version })),
    ],
    // Pack settings: shown when the pack is applied to a world (e.g. at world creation) and
    // in the pack's gear menu. Read via world.getPackSettings() (stable since @minecraft/server 2.8.0).
    settings: [
      { type: 'label', text: 'msg.burmaldaholic.core.settings.label' },
      { type: 'toggle', name: 'burmaldaholic:casino_mode', text: 'msg.burmaldaholic.core.settings.enabled', default: true },
    ],
    metadata,
  };
  const rp = {
    format_version: 3,
    header: { name: 'pack.name', description: 'pack.description', uuid: PACK.uuids.rpHeader, pack_scope: 'world', ...common },
    modules: [{ type: 'resources', uuid: PACK.uuids.rpResources, version: VERSION }],
    dependencies: [{ uuid: PACK.uuids.bpHeader, version: VERSION }],
    metadata,
  };
  if (PACK.manifestFormat === 2) return { bp: toV2(bp), rp: toV2(rp) };
  return { bp, rp };
}

// Fallback to manifest v2 (pack.json "manifestFormat": 2): version arrays, no pack settings.
// Script module dependency versions stay strings in v2.
function toV2(m) {
  const arr = (v) => v.split('.').map(Number);
  const out = JSON.parse(JSON.stringify(m));
  out.format_version = 2;
  delete out.settings;
  out.header.version = arr(out.header.version);
  out.header.min_engine_version = arr(out.header.min_engine_version);
  for (const mod of out.modules) mod.version = arr(mod.version);
  for (const d of out.dependencies ?? []) if (d.uuid) d.version = arr(d.version);
  return out;
}

function assemblePacks(errors) {
  const owners = new Map();
  const merged = new Map();
  for (const mod of MODULES) {
    const dir = path.join(ROOT, 'packs', mod.id);
    for (const file of walk(dir)) {
      if (path.basename(file) === '.gitkeep') continue;
      const relPath = path.relative(dir, file).split(path.sep).join('/'); // BP/... or RP/...
      if (MERGEABLE.has(relPath)) {
        const json = JSON.parse(fs.readFileSync(file, 'utf8'));
        merged.set(relPath, merged.has(relPath) ? deepMerge(merged.get(relPath), json, relPath, errors) : json);
        continue;
      }
      if (owners.has(relPath)) {
        errors.push(`${relPath} provided by both '${owners.get(relPath)}' and '${mod.id}'`);
        continue;
      }
      owners.set(relPath, mod.id);
      if (file.endsWith('.json')) {
        try {
          JSON.parse(fs.readFileSync(file, 'utf8'));
        } catch (e) {
          errors.push(`${rel(file)}: invalid JSON (${e.message})`);
        }
      }
      const out = path.join(BUILD, relPath);
      fs.mkdirSync(path.dirname(out), { recursive: true });
      fs.copyFileSync(file, out);
    }
  }
  for (const [relPath, json] of merged) {
    const out = path.join(BUILD, relPath);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, JSON.stringify(json, null, 2));
  }
}

function writeJson(p, v) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, JSON.stringify(v, null, 2) + '\n');
}

function zipDir(dir, prefix, into) {
  for (const f of walk(dir)) into[`${prefix}/${path.relative(dir, f).split(path.sep).join('/')}`] = fs.readFileSync(f);
}

async function main() {
  const errors = [];
  checkVersions(errors);
  const { errors: langErrors, merged } = buildLang();
  errors.push(...langErrors);
  fail(errors, 'Build failed (pre-checks)');

  fs.rmSync(BUILD, { recursive: true, force: true });
  fs.mkdirSync(BP, { recursive: true });
  fs.mkdirSync(RP, { recursive: true });

  await esbuild.build({
    entryPoints: [path.join(ROOT, 'src/main.ts')],
    outfile: path.join(BP, 'scripts/main.js'),
    bundle: true,
    format: 'esm',
    platform: 'neutral',
    target: 'es2023',
    external: ['@minecraft/server', '@minecraft/server-ui', '@minecraft/common'],
    minify: !DEV,
    sourcemap: DEV ? 'linked' : false,
    legalComments: 'none',
    logLevel: 'warning',
  });

  assemblePacks(errors);
  fail(errors, 'Build failed (packs)');

  const { bp, rp } = manifests();
  writeJson(path.join(BP, 'manifest.json'), bp);
  writeJson(path.join(RP, 'manifest.json'), rp);
  for (const pack of [BP, RP]) {
    writeJson(path.join(pack, 'texts/languages.json'), LANGS);
    writeJson(path.join(pack, 'texts/language_names.json'), PACK.languages);
    for (const lang of LANGS) fs.writeFileSync(path.join(pack, 'texts', `${lang}.lang`), renderLang(merged[lang]));
  }
  for (const icon of ['pack_icon.png']) {
    const src = path.join(ROOT, 'assets', icon);
    if (fs.existsSync(src)) for (const pack of [BP, RP]) fs.copyFileSync(src, path.join(pack, icon));
  }

  if (!args.has('--no-zip')) {
    fs.rmSync(DIST, { recursive: true, force: true });
    fs.mkdirSync(DIST, { recursive: true });
    const files = {};
    zipDir(BP, `${PACK.name}_BP`, files);
    zipDir(RP, `${PACK.name}_RP`, files);
    const out = path.join(DIST, `${PACK.name}.mcaddon`);
    fs.writeFileSync(out, zipSync(files, { level: 9, mtime: new Date('2026-01-01T00:00:00Z') }));
    console.log(`built ${rel(out)} (${Object.keys(files).length} files, v${VERSION}, engine ${PACK.minEngineVersion})`);
  } else {
    console.log(`built ${rel(BUILD)}/{BP,RP}`);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
