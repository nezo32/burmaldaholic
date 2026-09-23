/**
 * Bedrock `.mcstructure` encoder (little-endian NBT). PURE.
 *
 * Layout of the root compound (name ""):
 *   format_version: Int 1
 *   size: List<Int>[3]
 *   structure: {
 *     block_indices: List<List<Int>> [primary layer, waterlog layer]   (-1 = structure void)
 *     entities: List<Compound> []
 *     palette: { default: { block_palette: List<{name, states, version}>, block_position_data: {} } }
 *   }
 *   structure_world_origin: List<Int>[0, 0, 0]
 * Cell order: z fastest, then y, then x (see Grid.index).
 */
import { type BlockSpec, type Grid, type Layout } from './layouts';
import { type Tag, TAG, nbt, writeNbt } from './nbt';

/**
 * Block data version stamped on every palette entry: 1.21.110.0 ((1<<24)|(21<<16)|(110<<8)|0).
 * Palette names/states use that version's (current, flattened) block ids; the game upgrades
 * older data on load but never downgrades, so this must stay <= the min engine (1.26.30).
 */
export const BLOCK_DATA_VERSION = (1 << 24) | (21 << 16) | (110 << 8);

const stateTag = (v: string | number | boolean): Tag =>
  typeof v === 'boolean' ? nbt.byte(v ? 1 : 0) : typeof v === 'number' ? nbt.int(v) : nbt.string(v);

export function paletteKey(b: BlockSpec): string {
  const states = Object.entries(b.states ?? {}).sort(([a], [c]) => (a < c ? -1 : 1));
  return `${b.name}${JSON.stringify(states)}`;
}

export interface EncodedPalette {
  readonly indices: number[];
  readonly palette: BlockSpec[];
}

/** Palette in first-use order + per-cell indices (-1 for void). */
export function buildPalette(grid: Grid): EncodedPalette {
  const palette: BlockSpec[] = [];
  const byKey = new Map<string, number>();
  const indices = new Array<number>(grid.cellCount()).fill(-1);
  for (let x = 0; x < grid.sx; x++)
    for (let y = 0; y < grid.sy; y++)
      for (let z = 0; z < grid.sz; z++) {
        const b = grid.get(x, y, z);
        if (!b) continue;
        const key = paletteKey(b);
        let i = byKey.get(key);
        if (i === undefined) {
          i = palette.length;
          palette.push(b);
          byKey.set(key, i);
        }
        indices[grid.index(x, y, z)] = i;
      }
  return { indices, palette };
}

export function structureTag(grid: Grid): Tag {
  const { indices, palette } = buildPalette(grid);
  const blockPalette = palette.map((b) =>
    nbt.compound([
      ['name', nbt.string(b.name)],
      ['states', nbt.compound(Object.entries(b.states ?? {}).map(([k, v]) => [k, stateTag(v)] as [string, Tag]))],
      ['version', nbt.int(BLOCK_DATA_VERSION)],
    ]),
  );
  const ints = (a: number[]): Tag => nbt.list(TAG.int, a.map(nbt.int));
  return nbt.compound([
    ['format_version', nbt.int(1)],
    ['size', ints([grid.sx, grid.sy, grid.sz])],
    [
      'structure',
      nbt.compound([
        ['block_indices', nbt.list(TAG.list, [ints(indices), ints(new Array<number>(indices.length).fill(-1))])],
        ['entities', nbt.list(TAG.compound, [])],
        [
          'palette',
          nbt.compound([
            [
              'default',
              nbt.compound([
                ['block_palette', nbt.list(TAG.compound, blockPalette)],
                ['block_position_data', nbt.compound([])],
              ]),
            ],
          ]),
        ],
      ]),
    ],
    ['structure_world_origin', ints([0, 0, 0])],
  ]);
}

export function encodeMcstructure(grid: Grid): Uint8Array {
  return writeNbt(structureTag(grid), '');
}

/** Pack-relative output path of a layout's template: identifier `burmaldaholic:worldgen_<id>`. */
export function structurePath(l: Layout): string {
  return `BP/structures/burmaldaholic/worldgen_${l.id}.mcstructure`;
}
