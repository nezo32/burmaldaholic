/// <reference types="node" />
import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { CEL_PARTICLES, CORE_TIER_STEMS } from './celebration';
import { ATLAS_ROWS, CORE_PARTICLES, PARTICLE_ATLAS, atlasRow } from './particle-atlas';
import { MAX_FLIP_FRAMES, SOUND_LAYERS, definitionIds, planFlipbook, planSoundTimeline } from './sound-plan';
import { SOUND_DEFS } from './sound-ids';

const ROOT = path.resolve(__dirname, '../../../..');
const RP = path.join(ROOT, 'packs/core/RP');
const readJson = (f: string) => JSON.parse(fs.readFileSync(f, 'utf8'));
const vanilla = readJson(path.join(ROOT, 'tools/pack-schemas/vanilla-1.26.30.json')) as { soundFiles: string[]; particles: string[] };
const defs = readJson(path.join(RP, 'sounds/sound_definitions.json')).sound_definitions as Record<string, { category: string; sounds: Array<{ name: string }> }>;

describe('core sound definitions (packs/core/RP/sounds)', () => {
  const coreIds = SOUND_DEFS.filter((d) => d.owner === 'core').map((d) => d.id);

  it('define every core-owned catalog id and every layer, and nothing else', () => {
    const want = coreIds.flatMap(definitionIds).map((id) => `burmaldaholic.${id}`).sort();
    expect(Object.keys(defs).sort()).toEqual(want);
  });

  it('layers belong to core ids; stems of the default kit are core ids', () => {
    for (const id of Object.keys(SOUND_LAYERS)) expect(coreIds).toContain(id);
    for (const [k, v] of Object.entries(CORE_TIER_STEMS)) if (v) expect(coreIds, k).toContain(v);
  });

  it('use vanilla 1.26.30 sound files only (MUST phase) and table sounds are block sounds', () => {
    const files = new Set(vanilla.soundFiles);
    for (const [k, d] of Object.entries(defs)) for (const s of d.sounds) expect(files.has(s.name), `${k}: ${s.name}`).toBe(true);
    for (const d of SOUND_DEFS.filter((x) => x.owner === 'core' && x.source === 'block')) expect(defs[`burmaldaholic.${d.id}`]!.category).toBe('block');
  });
});

describe('core particles (packs/core/RP/particles)', () => {
  const dir = path.join(RP, 'particles');
  const files = fs.readdirSync(dir).filter((f) => f.endsWith('.json'));

  it('one file per core particle, identifier = file name, on the core atlas rows', () => {
    expect(files.map((f) => f.replace(/\.json$/, '')).sort()).toEqual(Object.keys(CORE_PARTICLES).sort());
    for (const f of files) {
      const id = f.replace(/\.json$/, '');
      const e = readJson(path.join(dir, f)).particle_effect;
      expect(e.description.identifier).toBe(`burmaldaholic:${id}`);
      expect(e.description.basic_render_parameters.texture).toBe(PARTICLE_ATLAS.path);
      const uv = e.components['minecraft:particle_appearance_billboard'].uv;
      expect([uv.texture_width, uv.texture_height]).toEqual([PARTICLE_ATLAS.size, PARTICLE_ATLAS.size]);
      const row = atlasRow(CORE_PARTICLES[id]!)!;
      expect(uv.flipbook.base_UV).toEqual([0, row.row * PARTICLE_ATLAS.cell]);
      expect(uv.flipbook.max_frame).toBe(row.frames);
      // one call per burst, count from script, ≤ 60 (global.md §2.9)
      expect(e.components['minecraft:emitter_rate_instant'].num_particles).toMatch(/math\.clamp\(v\.count \?\? \d+, 1, 60\)/);
    }
  });

  it('atlas rows fit the 128 px sheet and do not overlap', () => {
    const rows = ATLAS_ROWS.map((r) => r.row);
    expect(new Set(rows).size).toBe(rows.length);
    for (const r of ATLAS_ROWS) {
      expect((r.row + 1) * PARTICLE_ATLAS.cell).toBeLessThanOrEqual(PARTICLE_ATLAS.size);
      expect(r.frames * PARTICLE_ATLAS.cell).toBeLessThanOrEqual(PARTICLE_ATLAS.size);
    }
  });

  it('the celebration kit only uses defined core particles or vanilla ones', () => {
    for (const p of CEL_PARTICLES) {
      if (p.startsWith('minecraft:')) expect(vanilla.particles).toContain(p);
      else expect(Object.keys(CORE_PARTICLES)).toContain(p.slice('burmaldaholic:'.length));
    }
  });
});

describe('sound timeline plan (tables.md §0.7: ≤ 12 calls, one runTimeout chain)', () => {
  it('sorts, converts to ticks (ceil), dedupes same id in a tick', () => {
    const s = planSoundTimeline([
      { at: 120, id: 'b' },
      { at: 0, id: 'a' },
      { at: 101, id: 'b' },
      { at: 50, id: 'c', pitch: 1.4 },
    ]);
    expect(s.map((x) => [x.tick, x.id])).toEqual([
      [0, 'a'],
      [1, 'c'],
      [3, 'b'],
    ]);
    expect(s[1]!.pitch).toBe(1.4);
  });

  it('caps the call count and always keeps the last (landing) event', () => {
    const ev = Array.from({ length: 40 }, (_, i) => ({ at: i * 100, id: i === 39 ? 'settle' : 'roll' }));
    const s = planSoundTimeline(ev);
    expect(s.length).toBeLessThanOrEqual(12);
    expect(s[s.length - 1]!.id).toBe('settle');
    for (let i = 1; i < s.length; i++) expect(s[i]!.tick).toBeGreaterThan(s[i - 1]!.tick);
  });

  it('flipbook frames are ≥ 2 t apart, ≤ 18, reduce motion = last frame only', () => {
    expect(planFlipbook(4, 1, false).map((f) => f.tick)).toEqual([0, 2, 4, 6]);
    expect(planFlipbook(30, 3, false)).toHaveLength(MAX_FLIP_FRAMES);
    expect(planFlipbook(6, 3, true)).toEqual([{ tick: 0, frame: 5 }]);
    expect(planFlipbook(0, 3, false)).toEqual([]);
  });
});
