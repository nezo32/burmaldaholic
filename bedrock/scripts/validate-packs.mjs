// Pack validation (npm run validate:packs, also part of npm run lint).
//
// Builds build/{BP,RP} (no zip) and checks the assembled packs against:
//  - Mojang's official JSON schemas for the pinned engine (manifest v3, items, blocks, entity
//    components) and Blockception's bundles for the other file types; see tools/pack-schemas/.
//  - format_version <= pack.json minEngineVersion for every data file.
//  - cross references: textures, geometry, materials, render controllers, animations, sounds,
//    loot tables, recipe ingredients, lang keys, custom components, entity events,
//    .mcstructure block palettes (little-endian NBT), and ids used as literals in src/.
// Vanilla identifiers come from tools/pack-schemas/vanilla-<minEngineVersion>.json.
//
// Flags: --no-build (validate the existing build/ as is).
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import Ajv from 'ajv';
import { PACK, ROOT, fail, rel, walk } from './lib.mjs';

const args = new Set(process.argv.slice(2));
if (!args.has('--no-build')) execFileSync(process.execPath, [path.join(ROOT, 'scripts/build.mjs'), '--no-zip'], { stdio: ['ignore', 'ignore', 'inherit'] });

const BUILD = path.join(ROOT, 'build');
const BP = path.join(BUILD, 'BP');
const RP = path.join(BUILD, 'RP');
const SCHEMAS = path.join(ROOT, 'tools/pack-schemas');
const ENGINE = PACK.minEngineVersion;
const V = JSON.parse(fs.readFileSync(path.join(SCHEMAS, `vanilla-${ENGINE}.json`), 'utf8'));
const errors = [];
const err = (file, msg) => errors.push(`${typeof file === 'string' && path.isAbsolute(file) ? rel(file) : file}: ${msg}`);

// ---------------------------------------------------------------------------------------------
// helpers
const readJson = (f) => {
  try {
    return JSON.parse(fs.readFileSync(f, 'utf8'));
  } catch (e) {
    err(f, `invalid JSON (${e.message})`);
    return undefined;
  }
};
const filesIn = (dir, ext = '.json') => walk(dir).filter((f) => f.endsWith(ext));
const semver = (v) => (typeof v === 'string' ? v.split('.').map(Number) : v);
const cmpVer = (a, b) => {
  const x = semver(a);
  const y = semver(b);
  for (let i = 0; i < 3; i++) if ((x[i] ?? 0) !== (y[i] ?? 0)) return (x[i] ?? 0) - (y[i] ?? 0);
  return 0;
};
const set = (a) => new Set(a);
const vanilla = {
  blocks: set(Object.keys(V.blocks)),
  items: set(V.items),
  entities: set(V.entities),
  geometry: set(V.geometry),
  renderControllers: set(V.renderControllers),
  animations: set([...V.animations, ...V.animationControllers]),
  materials: set(V.materials),
  textures: set(V.textures),
  soundFiles: set(V.soundFiles),
  sounds: set(V.sounds),
  particles: set(V.particles),
  effects: set(V.effects),
  itemTextures: set(V.itemTextures),
  terrainTextures: set(V.terrainTextures),
};

const ajv = new Ajv({ strict: false, allErrors: true, validateSchema: false, unicodeRegExp: false, validateFormats: false });
const schema = (name) => {
  const s = loadSchema(name);
  if (name === 'mojang/manifest.json') {
    // Mojang's settings[] oneOf has no discriminator (a toggle also matches LabelSetting), so tie
    // each branch to its "type" value before validating.
    for (const d of Object.values(s.definitions)) {
      const kind = { LabelSetting: 'label', SliderSetting: 'slider', ToggleSetting: 'toggle', DropdownSetting: 'dropdown' }[d.title];
      if (kind) d.properties.type = { const: kind };
    }
  }
  return ajv.compile(s);
};
// Mojang's generated schemas use oneOf as a plain union (e.g. a filter test object also matches
// the filter-group map), which makes valid documents fail "exactly one"; read them as anyOf.
function loadSchema(name) {
  const txt = fs.readFileSync(path.join(SCHEMAS, name), 'utf8');
  return JSON.parse(name.startsWith('mojang/') ? txt.replace(/"oneOf":/g, '"anyOf":') : txt);
}
// Report the most specific schema errors (drop the generic oneOf/anyOf wrappers when a deeper one exists).
function check(validate, data, file, what = '') {
  if (validate(data)) return;
  const es = validate.errors;
  const deep = es.filter((e) => !['oneOf', 'anyOf', 'if', 'else', 'then'].includes(e.keyword));
  for (const e of (deep.length ? deep : es).slice(0, 6)) err(file, `schema${what}: ${e.instancePath || '/'} ${e.message}${e.params?.additionalProperty ? ` '${e.params.additionalProperty}'` : ''}${e.params?.allowedValues ? ` ${JSON.stringify(e.params.allowedValues)}` : ''}`);
}
const fileExists = (pack, p, exts) => exts.some((x) => fs.existsSync(path.join(pack, p + x)));
const checkFormatVersion = (f, d) => {
  const fv = d?.format_version;
  if (fv === undefined) return;
  if (typeof fv !== 'string' || !/^\d+\.\d+\.\d+$/.test(fv)) err(f, `format_version '${fv}' is not a x.y.z string`);
  else if (cmpVer(fv, ENGINE) > 0) err(f, `format_version ${fv} is newer than min_engine_version ${ENGINE}`);
};

// lang keys (en_US is the reference; lang.mjs checks the other languages match)
const lang = (pack) => {
  const out = new Set();
  const f = path.join(pack, 'texts/en_US.lang');
  if (fs.existsSync(f)) for (const line of fs.readFileSync(f, 'utf8').split('\n')) if (/^[^#\s][^=]*=/.test(line)) out.add(line.slice(0, line.indexOf('=')));
  return out;
};
const rpLang = lang(RP);
const bpLang = lang(BP);

// script sources (non-test), for custom components / literal ids
const SRC = walk(path.join(ROOT, 'src')).filter((f) => f.endsWith('.ts') && !f.endsWith('.test.ts'));
// Comments are blanked (keeping line numbers) so ids mentioned in docs are not checked.
const stripComments = (t) => t.replace(/("(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, (m, str) => str ?? m.replace(/[^\n]/g, ' '));
const srcText = new Map(SRC.map((f) => [f, stripComments(fs.readFileSync(f, 'utf8'))]));
const allSrc = [...srcText.values()].join('\n');

// ---------------------------------------------------------------------------------------------
// 0. every JSON parses and respects the engine version
const ALL_JSON = [...filesIn(BP), ...filesIn(RP)];
const docs = new Map();
for (const f of ALL_JSON) {
  const d = readJson(f);
  if (d === undefined) continue;
  docs.set(f, d);
  if (!f.endsWith('manifest.json')) checkFormatVersion(f, d);
}
const docsIn = (dir) => [...docs.entries()].filter(([f]) => f.startsWith(dir + path.sep));

// ---------------------------------------------------------------------------------------------
// 1. manifests
{
  const v = schema('mojang/manifest.json');
  const bp = docs.get(path.join(BP, 'manifest.json'));
  const rp = docs.get(path.join(RP, 'manifest.json'));
  if (!bp || !rp) err('build', 'manifest.json missing');
  else {
    check(v, bp, path.join(BP, 'manifest.json'));
    check(v, rp, path.join(RP, 'manifest.json'));
    for (const [m, f, texts] of [
      [bp, 'BP/manifest.json', bpLang],
      [rp, 'RP/manifest.json', rpLang],
    ]) {
      if (m.format_version !== PACK.manifestFormat) err(f, `format_version ${m.format_version} != pack.json manifestFormat ${PACK.manifestFormat}`);
      if (cmpVer(m.header.min_engine_version, ENGINE) !== 0) err(f, `min_engine_version != ${ENGINE}`);
      for (const k of [m.header.name, m.header.description]) if (!texts.has(k)) err(f, `header text key '${k}' missing from texts/en_US.lang`);
      if (!m.metadata?.authors?.length) err(f, 'metadata.authors is required by manifest v3');
    }
    const uuids = [bp.header.uuid, rp.header.uuid, ...bp.modules.map((x) => x.uuid), ...rp.modules.map((x) => x.uuid)];
    if (new Set(uuids).size !== uuids.length) err('manifest', 'duplicate UUIDs across BP/RP header and modules');
    if (!bp.dependencies.some((d) => d.uuid === rp.header.uuid)) err('BP/manifest.json', 'BP does not depend on the RP');
    if (!rp.dependencies.some((d) => d.uuid === bp.header.uuid)) err('RP/manifest.json', 'RP does not depend on the BP');
    for (const [mod, ver] of Object.entries(PACK.scriptModules)) if (!bp.dependencies.some((d) => d.module_name === mod && d.version === ver)) err('BP/manifest.json', `missing dependency ${mod}@${ver}`);
    const script = bp.modules.find((x) => x.type === 'script');
    if (script && !fs.existsSync(path.join(BP, script.entry))) err('BP/manifest.json', `script entry ${script.entry} missing`);
    for (const s of bp.settings ?? []) if (s.text && !bpLang.has(s.text)) err('BP/manifest.json', `setting text key '${s.text}' missing from BP texts`);
  }
}

// ---------------------------------------------------------------------------------------------
// registries collected from the packs
const custom = { items: new Set(), blocks: new Set(), entities: new Set(), clientEntities: new Set(), geometry: new Set(), renderControllers: new Set(), animations: new Set(), sounds: new Set(), structures: new Set() };
const entityDocs = new Map(); // id -> BP entity doc
for (const [f, d] of docsIn(path.join(BP, 'items'))) addId(custom.items, d?.['minecraft:item']?.description?.identifier, f);
for (const [f, d] of docsIn(path.join(BP, 'blocks'))) addId(custom.blocks, d?.['minecraft:block']?.description?.identifier, f);
for (const [f, d] of docsIn(path.join(BP, 'entities'))) {
  const id = d?.['minecraft:entity']?.description?.identifier;
  addId(custom.entities, id, f);
  if (id) entityDocs.set(id, d['minecraft:entity']);
}
for (const [f, d] of docsIn(path.join(RP, 'entity'))) addId(custom.clientEntities, d?.['minecraft:client_entity']?.description?.identifier, f);
for (const [, d] of docsIn(path.join(RP, 'models'))) {
  for (const g of d?.['minecraft:geometry'] ?? []) custom.geometry.add(g.description?.identifier);
  for (const k of Object.keys(d ?? {})) if (k.startsWith('geometry.')) custom.geometry.add(k.split(':')[0]);
}
for (const [, d] of docsIn(path.join(RP, 'render_controllers'))) for (const k of Object.keys(d?.render_controllers ?? {})) custom.renderControllers.add(k);
for (const [, d] of docsIn(path.join(RP, 'animations'))) for (const k of Object.keys(d?.animations ?? {})) custom.animations.add(k);
for (const [, d] of docsIn(path.join(RP, 'animation_controllers'))) for (const k of Object.keys(d?.animation_controllers ?? {})) custom.animations.add(k);
const soundDefs = docs.get(path.join(RP, 'sounds/sound_definitions.json'));
for (const k of Object.keys(soundDefs?.sound_definitions ?? {})) custom.sounds.add(k);
for (const f of filesIn(path.join(BP, 'structures'), '.mcstructure')) {
  const r = path.relative(path.join(BP, 'structures'), f).split(path.sep);
  custom.structures.add(r.length > 1 ? `${r[0]}:${r.slice(1).join('/').replace(/\.mcstructure$/, '')}` : `mystructure:${r[0].replace(/\.mcstructure$/, '')}`);
}
function addId(bucket, id, f) {
  if (typeof id !== 'string') return err(f, 'missing description.identifier');
  if (!id.startsWith('burmaldaholic:')) err(f, `identifier '${id}' is not in the burmaldaholic: namespace`);
  if (bucket.has(id)) err(f, `duplicate identifier '${id}'`);
  bucket.add(id);
}
const itemTexture = docs.get(path.join(RP, 'textures/item_texture.json'));
const terrainTexture = docs.get(path.join(RP, 'textures/terrain_texture.json'));
const itemTextureKeys = new Set([...Object.keys(itemTexture?.texture_data ?? {}), ...vanilla.itemTextures]);
const terrainTextureKeys = new Set([...Object.keys(terrainTexture?.texture_data ?? {}), ...vanilla.terrainTextures]);
const anyItem = (id) => {
  const full = id.includes(':') ? id : `minecraft:${id}`;
  return custom.items.has(full) || custom.blocks.has(full) || vanilla.items.has(full) || vanilla.blocks.has(full);
};
// custom component names defined as string constants in src
const srcLiterals = new Set([...allSrc.matchAll(/['"`]([a-z0-9_]+:[a-z0-9_./]+)['"`]/g)].map((m) => m[1]));
const componentRegistrations = /registerCustomComponent\(/.test(allSrc);

// ---------------------------------------------------------------------------------------------
// 2. items
{
  const doc = schema('mojang/item.json');
  const known = new Set(Object.keys(JSON.parse(fs.readFileSync(path.join(SCHEMAS, 'mojang/item.json'), 'utf8')).definitions[itemComponentsRef()]?.properties ?? {}));
  for (const [f, d] of docsIn(path.join(BP, 'items'))) {
    const item = d?.['minecraft:item'];
    if (!item) {
      err(f, 'missing minecraft:item');
      continue;
    }
    check(doc, item, f);
    const comps = item.components ?? {};
    for (const k of Object.keys(comps)) {
      if (k.startsWith('minecraft:') && known.size && !known.has(k)) err(f, `unknown item component ${k}`);
      if (!k.startsWith('minecraft:') && (!srcLiterals.has(k) || !componentRegistrations)) err(f, `custom component ${k} is not registered in src/`);
    }
    const icon = comps['minecraft:icon'];
    const iconKeys = typeof icon === 'string' ? [icon] : Object.values(icon?.textures ?? (icon?.texture ? { d: icon.texture } : {}));
    if (!icon && !comps['minecraft:block_placer']) err(f, 'no minecraft:icon');
    for (const t of iconKeys) if (!itemTextureKeys.has(t)) err(f, `icon texture '${t}' not in item_texture.json`);
    const dn = comps['minecraft:display_name']?.value;
    if (dn && !rpLang.has(dn)) err(f, `display_name key '${dn}' missing from RP texts`);
    if (!dn && !rpLang.has(`item.${item.description.identifier}.name`)) err(f, `no display_name and no 'item.${item.description.identifier}.name' lang key`);
  }
  function itemComponentsRef() {
    const s = JSON.parse(fs.readFileSync(path.join(SCHEMAS, 'mojang/item.json'), 'utf8'));
    return s.properties.components.$ref.split('/').pop();
  }
}

// ---------------------------------------------------------------------------------------------
// 3. blocks
{
  const doc = schema('mojang/block.json');
  const compsSchemaRaw = loadSchema('mojang/block_components.json');
  const comps = ajv.compile(compsSchemaRaw);
  const known = new Set(Object.keys(compsSchemaRaw.properties));
  const rpBlocks = docs.get(path.join(RP, 'blocks.json')) ?? {};
  for (const [f, d] of docsIn(path.join(BP, 'blocks'))) {
    const block = d?.['minecraft:block'];
    if (!block) {
      err(f, 'missing minecraft:block');
      continue;
    }
    check(doc, block, f);
    const id = block.description.identifier;
    const states = new Set([...Object.keys(block.description.states ?? {}), ...Object.values(block.description.traits ?? {}).flatMap((t) => t.enabled_states ?? [])]);
    const sets = [['components', block.components ?? {}], ...(block.permutations ?? []).map((p, i) => [`permutations[${i}]`, p.components ?? {}, p.condition])];
    for (const [where, c, cond] of sets) {
      check(comps, c, f, ` (${where})`);
      for (const k of Object.keys(c)) {
        if (k.startsWith('minecraft:') && !known.has(k)) err(f, `unknown block component ${k} (${where})`);
        if (!k.startsWith('minecraft:') && (!srcLiterals.has(k) || !componentRegistrations)) err(f, `custom component ${k} is not registered in src/`);
      }
      for (const inst of Object.values(c['minecraft:material_instances'] ?? {})) if (typeof inst === 'object' && inst.texture && !terrainTextureKeys.has(inst.texture)) err(f, `texture '${inst.texture}' not in terrain_texture.json (${where})`);
      const geo = c['minecraft:geometry'];
      const geoId = typeof geo === 'string' ? geo : geo?.identifier;
      if (geoId && !geoId.startsWith('minecraft:geometry.') && !custom.geometry.has(geoId)) err(f, `geometry '${geoId}' not found in RP/models`);
      if (typeof cond === 'string') for (const m of cond.matchAll(/block_state\(\s*'([^']+)'\s*\)/g)) if (!states.has(m[1])) err(f, `permutation condition uses undeclared state '${m[1]}'`);
    }
    const dn = block.components?.['minecraft:display_name'];
    const dnKey = typeof dn === 'string' ? dn : dn?.value;
    if (dnKey && !rpLang.has(dnKey)) err(f, `display_name key '${dnKey}' missing from RP texts`);
    if (!dnKey && !rpLang.has(`tile.${id}.name`)) err(f, `no display_name and no 'tile.${id}.name' lang key`);
    if (!rpBlocks[id]) err('RP/blocks.json', `no entry for ${id} (block sound/culling defaults)`);
  }
  for (const k of Object.keys(rpBlocks)) if (k !== 'format_version' && !custom.blocks.has(k) && !vanilla.blocks.has(k)) err('RP/blocks.json', `entry '${k}' has no block definition`);
  check(schema('blockception/blocks_rp.json'), rpBlocks, path.join(RP, 'blocks.json'));
}

// ---------------------------------------------------------------------------------------------
// 4. server entities
{
  const raw = loadSchema('mojang/entity_components.json');
  const comps = ajv.compile(raw);
  const known = new Set(Object.keys(raw.properties));
  const knownPattern = Object.keys(raw.patternProperties ?? {}).map((p) => new RegExp(p));
  for (const [f, d] of docsIn(path.join(BP, 'entities'))) {
    const e = d?.['minecraft:entity'];
    if (!e) {
      err(f, 'missing minecraft:entity');
      continue;
    }
    const groups = e.component_groups ?? {};
    for (const [where, c] of [['components', e.components ?? {}], ...Object.entries(groups).map(([g, c]) => [`component_groups.${g}`, c])]) {
      check(comps, c, f, ` (${where})`);
      for (const k of Object.keys(c)) if (!known.has(k) && !knownPattern.some((r) => r.test(k))) err(f, `unknown entity component ${k} (${where})`);
      walkJson(c, (k, v) => {
        if (k === 'table' && typeof v === 'string' && !fs.existsSync(path.join(BP, v))) err(f, `loot table ${v} missing (${where})`);
        if (k === 'event' && typeof v === 'string' && !v.startsWith('minecraft:') && !(e.events ?? {})[v]) err(f, `event '${v}' not defined (${where})`);
      });
    }
    walkJson(e.events ?? {}, (k, v) => {
      if ((k === 'component_groups') && Array.isArray(v)) for (const g of v) if (!groups[g]) err(f, `event references missing component group '${g}'`);
    });
    const rid = e.description.runtime_identifier;
    if (rid && !vanilla.entities.has(rid)) err(f, `runtime_identifier '${rid}' is not a vanilla entity`);
    const id = e.description.identifier;
    if (e.description.is_summonable !== false && !custom.clientEntities.has(id)) err(f, `no client entity (RP/entity) for ${id}`);
    const egg = e.description.is_spawnable;
    if (egg && !rpLang.has(`item.spawn_egg.entity.${id}.name`)) err(f, `spawn egg lang key 'item.spawn_egg.entity.${id}.name' missing`);
    if (!rpLang.has(`entity.${id}.name`)) err(f, `lang key 'entity.${id}.name' missing`);
  }
}
function walkJson(o, fn) {
  if (Array.isArray(o)) o.forEach((x) => walkJson(x, fn));
  else if (o && typeof o === 'object')
    for (const [k, v] of Object.entries(o)) {
      fn(k, v);
      walkJson(v, fn);
    }
}

// ---------------------------------------------------------------------------------------------
// 5. client entities, geometry, render controllers, animations
{
  const v = schema('blockception/client_entity.json');
  for (const [f, d] of docsIn(path.join(RP, 'entity'))) {
    check(v, d, f);
    const desc = d?.['minecraft:client_entity']?.description;
    if (!desc) continue;
    if (!custom.entities.has(desc.identifier) && !vanilla.entities.has(desc.identifier)) err(f, `client entity ${desc.identifier} has no BP entity`);
    for (const g of Object.values(desc.geometry ?? {})) if (!custom.geometry.has(g) && !vanilla.geometry.has(g)) err(f, `geometry '${g}' not found (custom or vanilla ${ENGINE})`);
    for (const t of Object.values(desc.textures ?? {})) if (!fileExists(RP, t, ['.png', '.tga', '.jpg']) && !vanilla.textures.has(t)) err(f, `texture '${t}' not found`);
    for (const m of Object.values(desc.materials ?? {})) if (!vanilla.materials.has(m)) err(f, `material '${m}' is not a known vanilla entity material`);
    for (const a of Object.values(desc.animations ?? {})) if (!custom.animations.has(a) && !vanilla.animations.has(a)) err(f, `animation '${a}' not found`);
    for (const rc of desc.render_controllers ?? []) {
      const name = typeof rc === 'string' ? rc : Object.keys(rc)[0];
      if (!custom.renderControllers.has(name) && !vanilla.renderControllers.has(name)) err(f, `render controller '${name}' not found`);
      const def = [...docsIn(path.join(RP, 'render_controllers'))].map(([, x]) => x?.render_controllers?.[name]).find(Boolean);
      if (def) {
        // Geometry.x / Texture.x / Material.x used by the controller must exist in this entity
        const txt = JSON.stringify(def);
        for (const [, kind, key] of txt.matchAll(/\b(Geometry|Texture|Material)\.([a-z0-9_]+)/gi)) {
          const map = { geometry: desc.geometry, texture: desc.textures, material: desc.materials }[kind.toLowerCase()] ?? {};
          if (!(key in map) && !(def.arrays && JSON.stringify(def.arrays).includes(`${kind}.${key}`))) err(f, `render controller ${name} uses ${kind}.${key}, not defined by the entity`);
        }
      }
    }
    const animKeys = new Set(Object.keys(desc.animations ?? {}));
    for (const a of desc.scripts?.animate ?? []) {
      const name = typeof a === 'string' ? a : Object.keys(a)[0];
      if (!animKeys.has(name)) err(f, `scripts.animate '${name}' is not in description.animations`);
    }
    if (desc.animation_controllers && cmpVer(d.format_version, '1.10.0') >= 0) err(f, `description.animation_controllers is the 1.8.0 format and is ignored in format ${d.format_version}; list the controllers in animations + scripts.animate`);
    if (desc.spawn_egg && !desc.spawn_egg.texture && !desc.spawn_egg.base_color) err(f, 'spawn_egg without texture or colors');
  }
  const geo = schema('blockception/geometry.json');
  for (const [f, d] of docsIn(path.join(RP, 'models'))) check(geo, d, f);
  const rc = schema('blockception/render_controllers.json');
  for (const [f, d] of docsIn(path.join(RP, 'render_controllers'))) check(rc, d, f);
  const an = schema('blockception/animations.json');
  for (const [f, d] of docsIn(path.join(RP, 'animations'))) check(an, d, f);
  const ac = schema('blockception/animation_controllers.json');
  for (const [f, d] of docsIn(path.join(RP, 'animation_controllers'))) check(ac, d, f);
  const at = schema('blockception/attachables.json');
  for (const [f, d] of docsIn(path.join(RP, 'attachables'))) {
    check(at, d, f);
    const desc = d?.['minecraft:attachable']?.description;
    if (desc && !anyItem(desc.identifier)) err(f, `attachable for unknown item ${desc.identifier}`);
  }
}

// ---------------------------------------------------------------------------------------------
// 6. loot tables, recipes, spawn rules
{
  const lt = schema('blockception/loot_tables.json');
  for (const [f, d] of docsIn(path.join(BP, 'loot_tables'))) {
    check(lt, d, f);
    walkJson(d, (k, v) => {
      if (k === 'type' && v === 'item') return;
      if (k === 'name' && typeof v === 'string' && !anyItem(v) && !v.startsWith('loot_tables/')) err(f, `loot entry item '${v}' does not exist`);
    });
    for (const p of d?.pools ?? []) for (const e of p.entries ?? []) if (e.type === 'loot_table' && !fs.existsSync(path.join(BP, e.name))) err(f, `nested loot table ${e.name} missing`);
  }
  const rv = schema('blockception/recipes.json');
  const recipeIds = new Set();
  for (const [f, d] of docsIn(path.join(BP, 'recipes'))) {
    check(rv, d, f);
    const [kind, r] = Object.entries(d ?? {}).find(([k]) => k.startsWith('minecraft:recipe_')) ?? [];
    if (!r) {
      err(f, 'no minecraft:recipe_* body');
      continue;
    }
    const id = r.description?.identifier;
    if (recipeIds.has(id)) err(f, `duplicate recipe identifier ${id}`);
    recipeIds.add(id);
    const itemRefs = [];
    const collect = (x) => {
      if (typeof x === 'string') itemRefs.push(x.split(':').length > 2 ? x.split(':').slice(0, 2).join(':') : x);
      else if (x?.item) itemRefs.push(x.item);
      else if (x?.tag) return;
      else if (Array.isArray(x)) x.forEach(collect);
    };
    if (kind === 'minecraft:recipe_shaped') {
      const pat = r.pattern ?? [];
      if (pat.length < 1 || pat.length > 3 || pat.some((row) => row.length < 1 || row.length > 3)) err(f, 'shaped pattern must be 1–3 rows of 1–3 chars');
      if (new Set(pat.map((row) => row.length)).size > 1) err(f, 'shaped pattern rows have different lengths');
      const used = new Set(pat.join('').replace(/ /g, ''));
      for (const ch of used) if (!(ch in (r.key ?? {}))) err(f, `pattern symbol '${ch}' has no key`);
      for (const ch of Object.keys(r.key ?? {})) if (!used.has(ch)) err(f, `key '${ch}' unused in pattern`);
      Object.values(r.key ?? {}).forEach(collect);
    }
    if (kind === 'minecraft:recipe_shapeless') {
      collect(r.ingredients);
      if ((r.ingredients ?? []).reduce((n, i) => n + (i.count ?? 1), 0) > 9) err(f, 'shapeless recipe with more than 9 ingredients');
    }
    collect(r.result);
    for (const it of itemRefs) if (!anyItem(it)) err(f, `recipe item '${it}' does not exist`);
    if (!r.unlock && cmpVer(d.format_version, '1.20.30') >= 0) err(f, 'recipe has no "unlock" (required from format 1.20.30 to show in the recipe book)');
    for (const t of r.tags ?? []) if (!['crafting_table', 'stonecutter', 'furnace', 'smoker', 'blast_furnace', 'campfire', 'soul_campfire', 'brewing_stand', 'smithing_table'].includes(t)) err(f, `unknown recipe tag '${t}'`);
  }
  const sr = schema('blockception/spawn_rules.json');
  for (const [f, d] of docsIn(path.join(BP, 'spawn_rules'))) {
    check(sr, d, f);
    const id = d?.['minecraft:spawn_rules']?.description?.identifier;
    if (id && !custom.entities.has(id)) err(f, `spawn rules for unknown entity ${id}`);
  }
}

// ---------------------------------------------------------------------------------------------
// 7. texture and sound registries, fonts
{
  const it = itemTexture;
  if (it) {
    check(schema('blockception/item_texture.json'), it, path.join(RP, 'textures/item_texture.json'));
    if (it.texture_name !== 'atlas.items') err('RP/textures/item_texture.json', 'texture_name must be atlas.items');
  }
  if (terrainTexture) {
    check(schema('blockception/terrain_texture.json'), terrainTexture, path.join(RP, 'textures/terrain_texture.json'));
    if (terrainTexture.texture_name !== 'atlas.terrain') err('RP/textures/terrain_texture.json', 'texture_name must be atlas.terrain');
  }
  for (const [name, reg] of [
    ['item_texture.json', it],
    ['terrain_texture.json', terrainTexture],
  ]) {
    for (const [key, entry] of Object.entries(reg?.texture_data ?? {})) {
      const paths = [];
      const add = (t) => (typeof t === 'string' ? paths.push(t) : t?.path ? paths.push(t.path) : Array.isArray(t) ? t.forEach(add) : t?.variations ? t.variations.forEach(add) : undefined);
      add(entry.textures);
      for (const p of paths) if (!fileExists(RP, p, ['.png', '.tga', '.jpg']) && !vanilla.textures.has(p)) err(`RP/textures/${name}`, `${key}: texture file '${p}' not found`);
    }
  }
  const fb = docs.get(path.join(RP, 'textures/flipbook_textures.json'));
  if (fb) {
    check(schema('blockception/flipbook_textures.json'), fb, path.join(RP, 'textures/flipbook_textures.json'));
    for (const e of fb) if (!fileExists(RP, e.flipbook_texture, ['.png', '.tga'])) err('RP/textures/flipbook_textures.json', `missing ${e.flipbook_texture}`);
  }
  if (soundDefs) {
    check(schema('blockception/sound_definitions.json'), soundDefs, path.join(RP, 'sounds/sound_definitions.json'));
    for (const [key, def] of Object.entries(soundDefs.sound_definitions ?? {})) {
      if (!key.startsWith('burmaldaholic.') && vanilla.sounds.has(key)) err('RP/sounds/sound_definitions.json', `'${key}' overrides a vanilla sound definition`);
      for (const s of def.sounds ?? []) {
        const n = typeof s === 'string' ? s : s.name;
        if (n && !fileExists(RP, n, ['.ogg', '.fsb', '.wav']) && !vanilla.soundFiles.has(n)) err('RP/sounds/sound_definitions.json', `${key}: sound file '${n}' not found`);
      }
    }
  }
  for (const f of walk(path.join(RP, 'font'))) {
    const b = path.basename(f);
    if (!/^glyph_[0-9A-F]{2}\.png$/.test(b)) {
      err(f, 'font files must be named glyph_XX.png (upper-case hex)');
      continue;
    }
    const buf = fs.readFileSync(f);
    const w = buf.readUInt32BE(16);
    const h = buf.readUInt32BE(20);
    if (buf.toString('latin1', 1, 4) !== 'PNG') err(f, 'not a PNG');
    else if (w !== h || w % 16 !== 0) err(f, `glyph page must be square with a size divisible by 16 (got ${w}x${h})`);
  }
  for (const f of walk(RP).filter((x) => x.endsWith('.png'))) {
    const buf = fs.readFileSync(f);
    if (buf.length < 24 || buf.toString('latin1', 1, 4) !== 'PNG') err(f, 'invalid PNG');
  }
}

// ---------------------------------------------------------------------------------------------
// 8. .mcstructure files (little-endian NBT)
const STRUCTURE_SIZES = {
  // GAME_DESIGN §16 bounding boxes (X × Y × Z)
  village_casino: [17, 10, 17],
  piglin_parlor: [21, 12, 21],
  high_roller_lounge: [15, 9, 15], // §16.3 re-export for baccarat/UTH (2026-09)
};
for (const f of filesIn(path.join(BP, 'structures'), '.mcstructure')) {
  let root;
  try {
    root = readNbt(fs.readFileSync(f));
  } catch (e) {
    err(f, `NBT parse failed: ${e.message}`);
    continue;
  }
  const size = root.size;
  if (root.format_version !== 1) err(f, `format_version ${root.format_version} (expected 1)`);
  if (!Array.isArray(size) || size.length !== 3 || size.some((n) => !Number.isInteger(n) || n <= 0 || n > 64)) {
    err(f, `bad size ${JSON.stringify(size)} (1..64 per axis)`);
    continue;
  }
  const kind = Object.keys(STRUCTURE_SIZES).find((k) => path.basename(f).includes(k));
  if (kind && STRUCTURE_SIZES[kind].join() !== size.join()) err(f, `size ${size.join('×')} differs from GAME_DESIGN §16 ${STRUCTURE_SIZES[kind].join('×')}`);
  const vol = size[0] * size[1] * size[2];
  const s = root.structure;
  const layers = s?.block_indices;
  if (!Array.isArray(layers) || layers.length !== 2) err(f, 'structure.block_indices must have 2 layers');
  const palette = s?.palette?.default?.block_palette ?? [];
  for (const [li, layer] of (layers ?? []).entries()) {
    if (layer.length !== vol) err(f, `block_indices[${li}] has ${layer.length} entries, expected ${vol}`);
    const bad = layer.find((i) => i < -1 || i >= palette.length);
    if (bad !== undefined) err(f, `block_indices[${li}] references palette index ${bad} of ${palette.length}`);
  }
  for (const b of palette) {
    const name = b.name;
    if (!vanilla.blocks.has(name) && !custom.blocks.has(name)) {
      err(f, `palette block '${name}' does not exist in ${ENGINE}`);
      continue;
    }
    if (!Number.isInteger(b.version)) err(f, `palette block '${name}' has no version`);
    if (vanilla.blocks.has(name)) {
      const allowed = new Set(V.blocks[name]);
      for (const [st, val] of Object.entries(b.states ?? {})) {
        if (!allowed.has(st)) err(f, `palette block '${name}' has unknown state '${st}'`);
        else if (V.blockStateValues[st] && !V.blockStateValues[st].some((x) => x === val || (typeof x === 'boolean' && Number(x) === val))) err(f, `palette block '${name}' state ${st}=${JSON.stringify(val)} is not a valid value`);
      }
    }
  }
  for (const e of s?.entities ?? []) if (e.identifier && !vanilla.entities.has(e.identifier) && !custom.entities.has(e.identifier)) err(f, `structure entity '${e.identifier}' does not exist`);
}

function readNbt(buf) {
  let o = 0;
  const u8 = () => buf.readUInt8(o++);
  const i16 = () => ((o += 2), buf.readInt16LE(o - 2));
  const u16 = () => ((o += 2), buf.readUInt16LE(o - 2));
  const i32 = () => ((o += 4), buf.readInt32LE(o - 4));
  const str = () => {
    const n = u16();
    o += n;
    return buf.toString('utf8', o - n, o);
  };
  const payload = (t) => {
    switch (t) {
      case 1:
        return buf.readInt8(o++);
      case 2:
        return i16();
      case 3:
        return i32();
      case 4:
        return ((o += 8), buf.readBigInt64LE(o - 8));
      case 5:
        return ((o += 4), buf.readFloatLE(o - 4));
      case 6:
        return ((o += 8), buf.readDoubleLE(o - 8));
      case 7: {
        const n = i32();
        o += n;
        return [...buf.subarray(o - n, o)];
      }
      case 8:
        return str();
      case 9: {
        const et = u8();
        const n = i32();
        if (n < 0 || n > 1e7) throw new Error(`bad list length ${n}`);
        return Array.from({ length: n }, () => payload(et));
      }
      case 10: {
        const out = {};
        for (;;) {
          const tt = u8();
          if (tt === 0) return out;
          const name = str();
          out[name] = payload(tt);
        }
      }
      case 11: {
        const n = i32();
        return Array.from({ length: n }, () => i32());
      }
      case 12: {
        const n = i32();
        return Array.from({ length: n }, () => ((o += 8), buf.readBigInt64LE(o - 8)));
      }
      default:
        throw new Error(`unknown tag ${t} at ${o}`);
    }
  };
  if (u8() !== 10) throw new Error('root is not a compound');
  str();
  const root = payload(10);
  if (o !== buf.length) throw new Error(`${buf.length - o} trailing bytes`);
  return root;
}

// ---------------------------------------------------------------------------------------------
// 9. identifiers used as literals in src/
{
  // Entity events that scripts trigger (spawnEvent / triggerEvent) must exist on some entity.
  const events = new Set([...entityDocs.values()].flatMap((e) => Object.keys(e.events ?? {})));
  const blockComponents = new Set(docsIn(path.join(BP, 'blocks')).flatMap(([, d]) => Object.keys(d?.['minecraft:block']?.components ?? {})));
  const itemComponents = new Set(docsIn(path.join(BP, 'items')).flatMap(([, d]) => Object.keys(d?.['minecraft:item']?.components ?? {})));
  const settings = new Set((docs.get(path.join(BP, 'manifest.json'))?.settings ?? []).map((s) => s.name).filter(Boolean));
  // Real ids missing from Mojang's vanilla data, and Java/legacy aliases that code only compares
  // against defensively (never places or spawns them).
  const TOLERATED = ['minecraft:end_gateway', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:dirt_path'];
  const vanillaAny = new Set([...TOLERATED, ...vanilla.blocks, ...vanilla.items, ...vanilla.entities, ...vanilla.particles, ...V.effects, ...V.enchantments, ...V.biomes, ...V.dimensions, ...V.features, ...Object.keys(V.blockStateValues).map((s) => s), ...scriptComponentIds()]);
  for (const [f, txt] of srcText) {
    for (const m of txt.matchAll(/['"`](burmaldaholic|minecraft):([a-z0-9_./]*)['"`]/g)) {
      const id = `${m[1]}:${m[2]}`;
      const line = txt.slice(0, m.index).split('\n').length;
      const lineText = txt.split('\n')[line - 1];
      if (/i18n-ignore|validate-packs-ignore/.test(lineText)) continue;
      if (m[2] === '') continue; // prefix used for string building
      if (m[1] === 'minecraft') {
        if (!vanillaAny.has(id)) err(`${rel(f)}:${line}`, `'${id}' is not a vanilla ${ENGINE} block/item/entity/particle/effect/enchantment/biome/dimension/component id`);
        continue;
      }
      const name = m[2];
      // dynamic properties (burmaldaholic:<module>.<key>, or constants named *_PROP / *PROPERTY)
      if (name.includes('.') || name.endsWith('/') || /PROP|DynamicProperty/.test(lineText)) continue;
      const known =
        custom.items.has(id) || custom.blocks.has(id) || custom.entities.has(id) || custom.structures.has(id) || events.has(id) || blockComponents.has(id) || itemComponents.has(id) || settings.has(id) || custom.sounds.has(id) || isCommandOrScriptEvent(txt, id);
      if (!known) err(`${rel(f)}:${line}`, `'${id}' is not an item, block, entity, structure, entity event, custom component or command defined by the packs`);
    }
    for (const m of txt.matchAll(/\.playSound\(\s*'([^']+)'/g)) if (!vanilla.sounds.has(m[1]) && !custom.sounds.has(m[1])) err(`${rel(f)}:${txt.slice(0, m.index).split('\n').length}`, `sound '${m[1]}' is not defined`);
    for (const m of txt.matchAll(/\.addEffect\(\s*'([^']+)'/g)) if (!V.effects.includes(m[1].includes(':') ? m[1] : `minecraft:${m[1]}`)) err(`${rel(f)}:${txt.slice(0, m.index).split('\n').length}`, `effect '${m[1]}' does not exist`);
    for (const m of txt.matchAll(/(?:SOUND[A-Z_]*|sound)\s*[:=]\s*'([a-z0-9_.]+)'/g)) if (!vanilla.sounds.has(m[1]) && !custom.sounds.has(m[1])) err(`${rel(f)}:${txt.slice(0, m.index).split('\n').length}`, `sound '${m[1]}' is not defined`);
    for (const m of txt.matchAll(/(?:PARTICLE[A-Z_]*|particle)\s*[:=]\s*'([a-z0-9_:.]+)'/g)) if (!vanilla.particles.has(m[1])) err(`${rel(f)}:${txt.slice(0, m.index).split('\n').length}`, `particle '${m[1]}' is not a vanilla particle`);
  }
  function isCommandOrScriptEvent(txt, id) {
    const short = id.split(':')[1];
    // registerCommand({ name: 'burmaldaholic:x' ...}) / scriptEventReceive ids / tag names
    return new RegExp(`name:\\s*['"\`]${id}['"\`]|(?:scriptevent|ScriptEvent|event\\.id|\\.id\\s*!?==)[^\\n]*${id}|${short}['"\`]\\s*,\\s*\\(`).test(txt) || new RegExp(`(?:COMMAND|EVENT|SCRIPT_EVENT|CMD)[A-Z_]*\\s*=\\s*['"\`]${id}['"\`]`).test(txt);
  }
  function scriptComponentIds() {
    const d = fs.readFileSync(path.join(ROOT, 'node_modules/@minecraft/server/index.d.ts'), 'utf8');
    return [...d.matchAll(/'(minecraft:[a-z0-9_.]+)'/g)].map((m) => m[1]);
  }
}

fail(errors, 'Pack validation failed');
console.log(`packs OK (${docs.size} JSON files, schemas for ${ENGINE})`);
