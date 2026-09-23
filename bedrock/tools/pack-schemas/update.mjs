// Refreshes the vendored pack-validation data used by scripts/validate-packs.mjs.
//
//   node tools/pack-schemas/update.mjs <bedrock-samples checkout> <Blockception schemas checkout>
//
// - <bedrock-samples>: https://github.com/Mojang/bedrock-samples at the tag matching
//   pack.json minEngineVersion (currently v1.26.30.5). Needs metadata/, resource_pack/ (entity,
//   models, animations, animation_controllers, render_controllers, particles, sounds,
//   sound_definitions, textures registries) and git history for `git ls-tree`.
// - <Blockception>: https://github.com/Blockception/Minecraft-bedrock-json-schemas (main).
//
// Writes tools/pack-schemas/{mojang,blockception}/*.json and vanilla-<version>.json.
// Mojang's official schemas (metadata/json_schemas) are used where they exist for our file types
// (manifest v3, items, blocks, entity components); Blockception's bundles cover the rest
// (recipes, loot tables, client entities, geometry, render controllers, texture/sound registries).
/* global process, console */
/* eslint-disable no-console */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const [samples, blockception] = process.argv.slice(2);
if (!samples || !blockception) {
  console.error('usage: node tools/pack-schemas/update.mjs <bedrock-samples> <blockception-schemas>');
  process.exit(1);
}

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
// Vanilla RP JSON sometimes carries // comments.
const readLoose = (p) => {
  const txt = fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');
  try {
    return JSON.parse(txt);
  } catch {
    return JSON.parse(txt.replace(/("(?:\\.|[^"\\])*")|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, (m, s) => s ?? ''));
  }
};
const walk = (d) => (fs.existsSync(d) ? fs.readdirSync(d, { recursive: true }).map((f) => path.join(d, f)).filter((f) => fs.statSync(f).isFile()) : []);
const write = (rel, v) => {
  const out = path.join(HERE, rel);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, JSON.stringify(v) + '\n');
};

const tag = execFileSync('git', ['-C', samples, 'describe', '--tags', '--always'], { encoding: 'utf8' }).trim();
const bcRev = execFileSync('git', ['-C', blockception, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();

// ---- official Mojang schemas -------------------------------------------------------------
const J = path.join(samples, 'metadata/json_schemas');
const MOJANG = {
  'manifest.json': 'client_server/packaging/3.0.0/Manifest.json',
  'item.json': 'server/item/1.26.30/ItemDocument.json',
  'block.json': 'server/block/1.21.110/Blocks.json',
  'block_components.json': 'server/block_components/1.26.20/Block Components.json',
  'entity_components.json': 'server/entity/1.26.30/ActorDefinitions.json',
};
for (const [name, src] of Object.entries(MOJANG)) {
  const s = readJson(path.join(J, src));
  s.$comment = `Mojang bedrock-samples ${tag}: metadata/json_schemas/${src}`;
  write(`mojang/${name}`, s);
}

// ---- Blockception bundles ----------------------------------------------------------------
const BC = {
  'recipes.json': 'behavior/recipes/recipes.json',
  'loot_tables.json': 'behavior/loot_tables/loot_tables.json',
  'spawn_rules.json': 'behavior/spawn_rules/spawn_rules.json',
  'client_entity.json': 'resource/entity/entity.json',
  'geometry.json': 'resource/models/entity/model_entity.json',
  'render_controllers.json': 'resource/render_controllers/render_controllers.json',
  'animations.json': 'resource/animations/actor_animation.json',
  'animation_controllers.json': 'resource/animation_controllers/animation_controller.json',
  'attachables.json': 'resource/attachables/attachables.json',
  'sound_definitions.json': 'resource/sounds/sound_definitions.json',
  'item_texture.json': 'resource/textures/item_texture.json',
  'terrain_texture.json': 'resource/textures/terrain_texture.json',
  'flipbook_textures.json': 'resource/textures/flipbook_textures.json',
  'blocks_rp.json': 'resource/blocks.json',
};
for (const [name, src] of Object.entries(BC)) {
  const s = readJson(path.join(blockception, src));
  s.$comment = `Blockception/Minecraft-bedrock-json-schemas@${bcRev.slice(0, 12)} ${src} (BSD-3-Clause, see LICENSE-blockception)`;
  write(`blockception/${name}`, s);
}
fs.copyFileSync(path.join(blockception, 'LICENSE'), path.join(HERE, 'blockception/LICENSE-blockception'));

// ---- vanilla identifiers -----------------------------------------------------------------
const V = path.join(samples, 'metadata/vanilladata_modules');
const names = (f) => readJson(path.join(V, f)).data_items.map((d) => d.name);
const blocksMeta = readJson(path.join(V, 'mojang-blocks.json'));
const blockStates = {};
for (const b of blocksMeta.data_items) blockStates[b.name] = (b.properties ?? []).map((p) => p.name);
const stateValues = {};
for (const p of blocksMeta.block_properties) stateValues[p.name] = p.values.map((v) => v.value);

const RPD = path.join(samples, 'resource_pack');
const geometry = new Set();
for (const f of walk(path.join(RPD, 'models'))) {
  if (!f.endsWith('.json')) continue;
  const d = readLoose(f);
  for (const k of Object.keys(d)) if (k.startsWith('geometry.')) geometry.add(k.split(':')[0]);
  for (const g of d['minecraft:geometry'] ?? []) geometry.add(g.description.identifier);
}
const keysOf = (dir, inner) => {
  const s = new Set();
  for (const f of walk(path.join(RPD, dir))) if (f.endsWith('.json')) for (const k of Object.keys(readLoose(f)[inner] ?? {})) s.add(k);
  return s;
};
const materials = new Set();
const clientEntities = new Set();
for (const f of walk(path.join(RPD, 'entity'))) {
  const d = readLoose(f)['minecraft:client_entity']?.description;
  if (!d) continue;
  clientEntities.add(d.identifier);
  for (const m of Object.values(d.materials ?? {})) materials.add(m);
}
const particles = new Set();
for (const f of walk(path.join(RPD, 'particles'))) {
  const id = readLoose(f).particle_effect?.description?.identifier;
  if (id) particles.add(id);
}
const soundDefs = readLoose(path.join(RPD, 'sounds/sound_definitions.json'));
const lsTree = (p) => execFileSync('git', ['-C', samples, 'ls-tree', '-r', '--name-only', 'HEAD', p], { encoding: 'utf8', maxBuffer: 64 << 20 }).split('\n').filter(Boolean);
const noExt = (p) => p.replace(/^resource_pack\//, '').replace(/\.[a-z0-9]+$/i, '');
const textures = lsTree('resource_pack/textures').filter((p) => /\.(png|tga|jpg)$/i.test(p) && /^resource_pack\/textures\/(entity|models|blocks|items|particle)\//.test(p)).map(noExt);
const soundFiles = lsTree('resource_pack/sounds').filter((p) => /\.(ogg|fsb|wav)$/i.test(p)).map(noExt);
const itemTexture = readLoose(path.join(RPD, 'textures/item_texture.json')).texture_data;
const terrainTexture = readLoose(path.join(RPD, 'textures/terrain_texture.json')).texture_data;

const sorted = (it) => [...new Set(it)].sort();
write(`vanilla-${tag.replace(/^v/, '').split('.').slice(0, 3).join('.')}.json`, {
  $comment: `Vanilla identifiers from Mojang bedrock-samples ${tag}. Regenerate with tools/pack-schemas/update.mjs.`,
  blocks: blockStates,
  blockStateValues: stateValues,
  items: sorted(names('mojang-items.json')),
  entities: sorted(names('mojang-entities.json')),
  effects: sorted(names('mojang-effects.json')),
  enchantments: sorted(names('mojang-enchantments.json')),
  biomes: sorted(names('mojang-biomes.json')),
  dimensions: sorted(names('mojang-dimensions.json')),
  features: sorted(names('mojang-features.json')),
  particles: sorted(particles),
  sounds: sorted(Object.keys(soundDefs.sound_definitions ?? soundDefs)),
  soundFiles: sorted(soundFiles),
  geometry: sorted(geometry),
  renderControllers: sorted(keysOf('render_controllers', 'render_controllers')),
  animations: sorted(keysOf('animations', 'animations')),
  animationControllers: sorted(keysOf('animation_controllers', 'animation_controllers')),
  materials: sorted(materials),
  clientEntities: sorted(clientEntities),
  textures: sorted(textures),
  itemTextures: sorted(Object.keys(itemTexture)),
  terrainTextures: sorted(Object.keys(terrainTexture)),
});
console.log(`pack-schemas updated from bedrock-samples ${tag} and Blockception ${bcRev.slice(0, 12)}`);
