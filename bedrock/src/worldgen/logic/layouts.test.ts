import { describe, expect, it } from 'vitest';
import { generatedFiles } from './generate';
import { BLOCK, type Layout, NPC_ENTITY, type Vec3, allLayouts, facingYaw, signPixels, structureId, villageStyleForBiome } from './layouts';
import { BLOCK_DATA_VERSION, buildPalette, encodeMcstructure, structurePath } from './mcstructure';
import { TAG, type Tag, child, readNbt } from './nbt';

// node:fs without pulling @types/node into the (Bedrock) type program
interface Fs {
  readFileSync(p: string, enc?: string): Uint8Array & { toString(): string };
  existsSync(p: string): boolean;
  readdirSync(p: string): string[];
}
const fs = (await import(/* @vite-ignore */ ['node', 'fs'].join(':'))) as Fs;
const PACK = 'packs/worldgen';

function langKeys(): Set<string> {
  const keys = new Set<string>();
  for (const dir of fs.readdirSync('lang')) {
    const f = `lang/${dir}/en_US.lang`;
    if (!fs.existsSync(f)) continue;
    for (const line of String(fs.readFileSync(f, 'utf8')).split('\n')) {
      const m = /^([^#=][^=]*)=/.exec(line);
      if (m) keys.add(m[1] as string);
    }
  }
  return keys;
}

const layouts = [...allLayouts().values()];
const byId = (id: string): Layout => allLayouts().get(id) as Layout;

function count<T>(items: readonly T[], pred: (t: T) => boolean): number {
  return items.filter(pred).length;
}

describe('casino layouts (GAME_DESIGN §16)', () => {
  it('has one village palette per village type plus the Parlor and the Lounge', () => {
    expect(layouts.map((l) => l.id).sort()).toEqual(
      [
        'high_roller_lounge',
        'piglin_parlor',
        'village_casino_desert',
        'village_casino_plains',
        'village_casino_savanna',
        'village_casino_snowy',
        'village_casino_taiga',
      ].sort(),
    );
  });

  it('uses the spec bounding boxes', () => {
    for (const l of layouts.filter((l) => l.kind === 'village_casino')) expect(l.size).toEqual({ x: 17, y: 10, z: 17 });
    expect(byId('piglin_parlor').size).toEqual({ x: 21, y: 12, z: 21 });
    expect(byId('high_roller_lounge').size).toEqual({ x: 15, y: 9, z: 15 });
  });

  it('village casino contents: cashier, blackjack, roulette, 3 copper, 1 gold, wheel, loan shark, croupier, 1 chest', () => {
    for (const l of layouts.filter((l) => l.kind === 'village_casino')) {
      const blocks = l.tables.map((t) => t.block);
      expect(count(blocks, (b) => b === BLOCK.cashier)).toBe(1);
      expect(count(blocks, (b) => b === BLOCK.blackjack)).toBe(1);
      expect(count(blocks, (b) => b === BLOCK.roulette)).toBe(1);
      expect(count(blocks, (b) => b === BLOCK.slotsCopper)).toBe(3);
      expect(count(blocks, (b) => b === BLOCK.slotsGold)).toBe(1);
      expect(count(blocks, (b) => b === BLOCK.wheel)).toBe(1);
      expect(count(blocks, (b) => b === BLOCK.uth)).toBe(1);
      expect(l.npcs.map((n) => n.role).sort()).toEqual(['croupier', 'loan_shark']);
      expect(l.chests).toEqual([expect.objectContaining({ loot: 'village_casino' })]);
    }
  });

  it('Piglin Parlor contents: craps, low-stakes poker, baccarat, 2 gold slots, plinko, nether cashier, 2 dealers, moneylender', () => {
    const l = byId('piglin_parlor');
    const blocks = l.tables.map((t) => t.block);
    expect(count(blocks, (b) => b === BLOCK.craps)).toBe(1);
    expect(l.tables.filter((t) => t.block === BLOCK.poker).map((t) => t.preset)).toEqual(['parlor_poker']);
    expect(count(blocks, (b) => b === BLOCK.slotsGold)).toBe(2);
    expect(count(blocks, (b) => b === BLOCK.plinko)).toBe(1);
    expect(count(blocks, (b) => b === BLOCK.netherCashier)).toBe(1);
    expect(count(blocks, (b) => b === BLOCK.baccarat)).toBe(1);
    expect(l.npcs.map((n) => n.role).sort()).toEqual(['piglin_dealer', 'piglin_dealer', 'piglin_moneylender']);
    expect(l.chests.map((c) => c.loot)).toEqual(['piglin_parlor']);
  });

  it('High Roller Lounge contents: 2 netherite slots, HR blackjack + roulette + baccarat + UTH, cashier, shulker croupier, baccarat dealer', () => {
    const l = byId('high_roller_lounge');
    const blocks = l.tables.map((t) => t.block);
    expect(count(blocks, (b) => b === BLOCK.slotsNetherite)).toBe(2);
    expect(l.tables.filter((t) => t.block === BLOCK.blackjackHighRoller).map((t) => t.preset)).toEqual(['high_roller_blackjack']);
    expect(l.tables.filter((t) => t.block === BLOCK.rouletteHighRoller).map((t) => t.preset)).toEqual(['high_roller_roulette']);
    expect(count(blocks, (b) => b === BLOCK.cashier)).toBe(1);
    expect(l.tables.filter((t) => t.block === BLOCK.baccaratHighRoller).map((t) => t.preset)).toEqual(['high_roller_baccarat']);
    expect(l.tables.filter((t) => t.block === BLOCK.uthHighRoller).map((t) => t.preset)).toEqual(['high_roller_uth']);
    expect(l.size).toEqual({ x: 15, y: 9, z: 15 });
    expect(l.npcs.map((n) => n.role)).toEqual(['shulker_croupier', 'baccarat_dealer']);
    expect(l.chests.map((c) => c.loot)).toEqual(['high_roller']);
  });

  it('references table blocks and NPC entities by their STRINGS.md ids', () => {
    const keys = langKeys();
    for (const id of Object.values(BLOCK)) expect(keys.has(`block.burmaldaholic.${id.slice('burmaldaholic:'.length)}`), id).toBe(true);
    for (const ids of Object.values(NPC_ENTITY))
      for (const id of ids) expect(keys.has(`entity.burmaldaholic.${id.slice('burmaldaholic:'.length)}`), id).toBe(true);
  });

  const walkable = (l: Layout, occupied: Set<string>, x: number, z: number): boolean => {
    if (x < 0 || z < 0 || x >= l.size.x || z >= l.size.z) return false;
    if (occupied.has(`${x},${z}`)) return false;
    const feet = l.grid.get(x, 1, z)?.name;
    const head = l.grid.get(x, 2, z)?.name;
    return (feet === 'minecraft:air' || !!feet?.endsWith('_carpet')) && head === 'minecraft:air';
  };

  for (const l of layouts) {
    it(`${l.id}: furniture sits inside, on free floor cells, and is reachable from the door`, () => {
      const occupied = new Set<string>();
      const check = (p: Vec3, what: string) => {
        expect(p.y, what).toBe(1);
        expect(p.x > 0 && p.z > 0 && p.x < l.size.x - 1 && p.z < l.size.z - 1, `${what} inside walls`).toBe(true);
        const floor = l.grid.get(p.x, 0, p.z)?.name;
        expect(floor && floor !== 'minecraft:air', `${what} has a floor`).toBe(true);
        const key = `${p.x},${p.z}`;
        expect(occupied.has(key), `${what} overlaps`).toBe(false);
        occupied.add(key);
      };
      for (const t of l.tables) {
        check(t.pos, t.block);
        const cell = l.grid.get(t.pos.x, 1, t.pos.z)?.name ?? '';
        expect(cell === 'minecraft:air' || cell.endsWith('_carpet'), `${t.block} replaces air/carpet, not ${cell}`).toBe(true);
      }
      for (const n of l.npcs) {
        check(n.pos, n.role);
        expect(l.grid.get(n.pos.x, 2, n.pos.z)?.name, `${n.role} headroom`).toBe('minecraft:air');
      }
      for (const c of l.chests) {
        check(c.pos, 'chest');
        expect(l.grid.get(c.pos.x, 1, c.pos.z)?.name).toBe('minecraft:chest');
      }
      // flood fill from the door
      const start: [number, number][] = [];
      for (let x = l.door.x0; x <= l.door.x1; x++) {
        expect(l.grid.get(x, 1, l.size.z - 1)?.name, 'door is open').toBe('minecraft:air');
        start.push([x, l.size.z - 1]);
      }
      const seen = new Set(start.map(([x, z]) => `${x},${z}`));
      const queue = [...start];
      while (queue.length) {
        const [x, z] = queue.shift() as [number, number];
        for (const [dx, dz] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ] as const) {
          const k = `${x + dx},${z + dz}`;
          if (!seen.has(k) && walkable(l, occupied, x + dx, z + dz)) {
            seen.add(k);
            queue.push([x + dx, z + dz]);
          }
        }
      }
      const reachable = (p: Vec3) =>
        [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ].some(([dx, dz]) => seen.has(`${p.x + (dx as number)},${p.z + (dz as number)}`));
      for (const t of l.tables) expect(reachable(t.pos), `${t.block} at ${t.pos.x},${t.pos.z} reachable`).toBe(true);
      for (const n of l.npcs) expect(reachable(n.pos), `${n.role} reachable`).toBe(true);
      for (const c of l.chests) expect(reachable(c.pos), 'chest reachable').toBe(true);
    });
  }

  it('spells CASINO on the village sign (3×5 font, 16 columns)', () => {
    const s = signPixels('CASINO');
    expect(s.width).toBe(16);
    expect(new Set(s.pixels.map((p) => p.letter))).toEqual(new Set([0, 1, 2, 3, 4, 5]));
    expect(s.pixels.every((p) => p.row >= 0 && p.row < 5 && p.col >= 0 && p.col < 16)).toBe(true);
    const v = byId('village_casino_plains');
    const lit = (x: number, y: number) => ['minecraft:glowstone', 'minecraft:lit_redstone_lamp'].includes(v.grid.get(x, y, 16)?.name ?? '');
    for (const p of s.pixels) expect(lit(p.col, 9 - p.row)).toBe(true);
    // every lamp is powered from behind
    for (let x = 0; x < 17; x++)
      for (let y = 5; y < 10; y++) if (v.grid.get(x, y, 16)?.name === 'minecraft:lit_redstone_lamp') expect(v.grid.get(x, y, 15)?.name).toBe('minecraft:redstone_block');
  });

  it('maps biomes to village styles', () => {
    expect(villageStyleForBiome('minecraft:desert')).toBe('desert');
    expect(villageStyleForBiome('minecraft:savanna_plateau')).toBe('savanna');
    expect(villageStyleForBiome('minecraft:taiga')).toBe('taiga');
    expect(villageStyleForBiome('minecraft:snowy_plains')).toBe('snowy');
    expect(villageStyleForBiome('minecraft:ice_plains')).toBe('snowy');
    expect(villageStyleForBiome('minecraft:plains')).toBe('plains');
    expect(villageStyleForBiome('minecraft:meadow')).toBe('plains');
  });

  it('yaw faces the given direction', () => {
    expect(facingYaw('south')).toBe(0);
    expect(facingYaw('north')).toBe(180);
    expect(facingYaw('west')).toBe(90);
    expect(facingYaw('east')).toBe(-90);
    expect(structureId('piglin_parlor')).toBe('burmaldaholic:worldgen_piglin_parlor');
  });
});

describe('.mcstructure templates', () => {
  const tagList = (t: Tag | undefined): Tag[] => (t?.type === TAG.list ? t.value : []);
  const ints = (t: Tag | undefined): number[] => tagList(t).map((x) => (x.type === TAG.int ? x.value : NaN));

  for (const l of layouts) {
    it(`${l.id}: valid little-endian NBT with the Bedrock structure schema`, () => {
      const { name, tag } = readNbt(encodeMcstructure(l.grid));
      expect(name).toBe('');
      expect(child(tag, 'format_version')).toEqual({ type: TAG.int, value: 1 });
      expect(ints(child(tag, 'size'))).toEqual([l.size.x, l.size.y, l.size.z]);
      expect(ints(child(tag, 'structure_world_origin'))).toEqual([0, 0, 0]);
      const structure = child(tag, 'structure') as Tag;
      const layers = tagList(child(structure, 'block_indices'));
      expect(layers).toHaveLength(2);
      const n = l.size.x * l.size.y * l.size.z;
      const primary = ints(layers[0]);
      expect(primary).toHaveLength(n);
      expect(ints(layers[1])).toEqual(new Array(n).fill(-1));
      expect(tagList(child(structure, 'entities'))).toEqual([]);
      const palette = tagList(child(child(child(structure, 'palette') as Tag, 'default') as Tag, 'block_palette'));
      expect(palette.length).toBe(buildPalette(l.grid).palette.length);
      for (const p of palette) {
        const nm = child(p, 'name');
        expect(nm?.type === TAG.string && nm.value.startsWith('minecraft:')).toBe(true);
        expect(child(p, 'version')).toEqual({ type: TAG.int, value: BLOCK_DATA_VERSION });
        expect(child(p, 'states')?.type).toBe(TAG.compound);
      }
      expect(Math.max(...primary)).toBe(palette.length - 1);
      expect(Math.min(...primary)).toBeGreaterThanOrEqual(-1);
      // never contains another module's blocks: those are placed by script
      expect(palette.some((p) => JSON.stringify(child(p, 'name')).includes('burmaldaholic'))).toBe(false);
    });
  }

  it('committed templates match the generator (run: node src/worldgen/tools/gen-assets.mjs)', () => {
    for (const f of generatedFiles()) {
      const file = `${PACK}/${f.path}`;
      expect(fs.existsSync(file), file).toBe(true);
      expect(bytesEqual(fs.readFileSync(file), f.bytes), `${file} is stale`).toBe(true);
    }
    expect(generatedFiles().map((f) => f.path)).toEqual(layouts.map(structurePath));
  });
});

function bytesEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}
