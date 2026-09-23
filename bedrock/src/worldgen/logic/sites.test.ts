import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import {
  SITE_RADIUS,
  SiteIndex,
  candidateOrigins,
  chunkStrings,
  classifyNether,
  classifySurface,
  decodeSite,
  encodeSite,
  evaluateFootprint,
  evaluateNetherVolume,
  footprintSamples,
  looksLikeVillage,
  loungeFits,
  pickTowerTop,
  rollSite,
  volumeSamples,
} from './sites';

describe('sites', () => {
  it('encodes and decodes decided sites compactly', () => {
    const s = { kind: 'piglin_parlor', dim: 'minecraft:nether', x: -120, z: 340, state: 'no_room' } as const;
    expect(encodeSite(s)).toBe('b|n|-120|340|n');
    expect(decodeSite(encodeSite(s))).toEqual(s);
    expect(decodeSite(encodeSite({ ...s, dim: 'custom:dim' }))).toEqual({ ...s, dim: 'custom:dim' });
    expect(decodeSite('garbage')).toBeUndefined();
    expect(decodeSite('v|o|x|1|p')).toBeUndefined();
  });

  it('finds nearby decided sites per dimension, across cell borders', () => {
    const idx = new SiteIndex();
    idx.add({ kind: 'village_casino', dim: 'minecraft:overworld', x: 127, z: 0, state: 'skipped' });
    expect(idx.near('minecraft:overworld', 129, 5)?.x).toBe(127);
    expect(idx.near('minecraft:overworld', 127 + SITE_RADIUS + 1, 0)).toBeUndefined();
    expect(idx.near('minecraft:nether', 127, 0)).toBeUndefined();
    expect(idx.near('minecraft:overworld', 127 - 100, 70)).toBeDefined();
  });

  it('chunks persisted strings under the property size', () => {
    const items = Array.from({ length: 5000 }, (_, i) => `v|o|${i}|${-i}|p`);
    const chunks = chunkStrings(items, 30_000);
    expect(chunks.every((c) => c.length <= 30_000)).toBe(true);
    expect(chunks.flatMap((c) => c.split('\n'))).toEqual(items);
    expect(chunkStrings([], 10)).toEqual([]);
  });

  it('generates candidate lots north of the anchor first', () => {
    const c = candidateOrigins({ x: 100, z: 100 }, { x: 17, z: 17 }, [40, 60]);
    expect(c).toHaveLength(16);
    // first: centred straight north at distance 40 -> centre (100, 60)
    expect(c[0]).toEqual({ x: 92, z: 52 });
    // every candidate is fully clear of the anchor
    for (const o of c) expect(o.x > 100 || o.x + 17 < 100 || o.z > 100 || o.z + 17 < 100).toBe(true);
  });

  it('samples footprints and volumes including the far edges', () => {
    const f = footprintSamples({ x: 17, z: 17 }, 4);
    expect(f).toHaveLength(25);
    expect(f).toContainEqual({ dx: 16, dz: 16 });
    const v = volumeSamples({ x: 21, y: 9, z: 21 }, 4);
    expect(v).toHaveLength(6 * 3 * 6);
    expect(v).toContainEqual({ x: 20, y: 8, z: 20 });
  });

  it('classifies surface blocks', () => {
    expect(classifySurface('minecraft:grass_block')).toBe('ground');
    expect(classifySurface('minecraft:sand')).toBe('ground');
    expect(classifySurface('minecraft:orange_terracotta')).toBe('ground');
    expect(classifySurface('minecraft:short_grass')).toBe('plant');
    expect(classifySurface('minecraft:poppy')).toBe('plant');
    expect(classifySurface('minecraft:snow_layer')).toBe('plant');
    expect(classifySurface('minecraft:oak_leaves')).toBe('tree');
    expect(classifySurface('minecraft:spruce_log')).toBe('tree');
    expect(classifySurface('minecraft:water')).toBe('liquid');
    expect(classifySurface('minecraft:seagrass')).toBe('liquid');
    expect(classifySurface('minecraft:grass_path')).toBe('built');
    expect(classifySurface('minecraft:oak_planks')).toBe('built');
    expect(classifySurface('minecraft:farmland')).toBe('built');
    expect(classifySurface('minecraft:white_glazed_terracotta')).toBe('built');
    expect(classifySurface('burmaldaholic:cashier')).toBe('built');
  });

  it('accepts flat natural lots and rejects built, wet, wooded or steep ones', () => {
    const g = (y: number) => ({ y, kind: 'ground' as const });
    expect(evaluateFootprint([g(64), g(65), g(66), { y: 66, kind: 'plant' }, g(63)])).toEqual({ ok: true, floorY: 65 });
    expect(evaluateFootprint([g(64), { y: 64, kind: 'built' }]).reason).toBe('built');
    expect(evaluateFootprint([g(64), { y: 62, kind: 'liquid' }]).reason).toBe('liquid');
    expect(evaluateFootprint([g(64), g(70)]).reason).toBe('steep');
    expect(evaluateFootprint([g(64), undefined]).reason).toBe('unloaded');
    expect(evaluateFootprint([g(64), { y: 70, kind: 'tree' }, { y: 71, kind: 'tree' }]).reason).toBe('trees');
    expect(evaluateFootprint([g(64), g(64), g(64), { y: 70, kind: 'tree' }]).ok).toBe(true);
  });

  it('recognises villages by paths or a bell', () => {
    expect(looksLikeVillage(['minecraft:grass_block', 'minecraft:bell'])).toBe(true);
    expect(looksLikeVillage(['minecraft:grass_path', 'minecraft:grass_path', 'minecraft:dirt_path'])).toBe(true);
    expect(looksLikeVillage(['minecraft:grass_path', 'minecraft:stone'])).toBe(false);
  });

  it('keeps the Parlor out of the bastion and out of lava', () => {
    expect(classifyNether('minecraft:netherrack')).toBe('solid');
    expect(classifyNether('minecraft:magma')).toBe('solid');
    expect(classifyNether('minecraft:polished_blackstone_bricks')).toBe('bastion');
    expect(classifyNether('minecraft:gilded_blackstone')).toBe('bastion');
    expect(classifyNether('minecraft:lava')).toBe('lava');
    expect(classifyNether('minecraft:air')).toBe('air');
    expect(evaluateNetherVolume(['solid', 'solid', 'air'])).toEqual({ ok: true });
    expect(evaluateNetherVolume(['solid', 'bastion']).reason).toBe('bastion');
    expect(evaluateNetherVolume(['solid', 'lava']).reason).toBe('lava');
    expect(evaluateNetherVolume(['air', 'air', 'air', 'solid']).reason).toBe('open');
    expect(evaluateNetherVolume(['solid', undefined]).reason).toBe('unloaded');
  });

  it('puts the Lounge on the tallest End City roof', () => {
    const top = pickTowerTop([
      { x: 0, z: 0, y: 70, typeId: 'minecraft:end_stone' },
      { x: 4, z: 0, y: 110, typeId: 'minecraft:purpur_block' },
      { x: 8, z: 0, y: 130, typeId: 'minecraft:obsidian' },
      { x: 12, z: 0, y: 95, typeId: 'minecraft:end_bricks' },
    ]);
    expect(top).toMatchObject({ x: 4, y: 110 });
    expect(pickTowerTop([{ x: 0, z: 0, y: 60, typeId: 'minecraft:end_stone' }])).toBeUndefined();
    expect(loungeFits(110, [110, 100, undefined], 9, 256)).toBe(true);
    expect(loungeFits(110, [111], 9, 256)).toBe(false);
    expect(loungeFits(250, [250], 9, 256)).toBe(false);
  });

  it('rolls the configured chance once per site (≈ 1 per 3 villages at 0.35)', () => {
    const rng = seededRng(99);
    for (const chance of [0.35, 0.3, 0.2]) {
      let hits = 0;
      const n = 200_000;
      for (let i = 0; i < n; i++) if (rollSite(chance, rng.next())) hits++;
      expect(Math.abs(hits / n - chance)).toBeLessThan(0.005);
    }
    expect(rollSite(0, 0)).toBe(false);
    expect(rollSite(1, 0.999999)).toBe(true);
    expect(rollSite(2, 0.5)).toBe(true);
    expect(rollSite(-1, 0)).toBe(false);
  });
});
