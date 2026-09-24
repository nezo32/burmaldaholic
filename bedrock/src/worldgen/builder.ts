/**
 * Builds one casino at a world position: places the `.mcstructure` shell, fills the foundation,
 * then furnishes it by script (game tables of other modules, NPCs, loot).
 */
import { BlockPermutation, type Dimension, type Entity, EnchantmentTypes, EntityTypes, ItemStack, ItemTypes, type Vector3, world } from '@minecraft/server';
import { type Logger, NO_REWARD_TAG, mathRng, pick, randInt } from '../core';
import { type CasinoRecord, newCasinoId } from './logic/casinos';
import type { LoanApi } from '../loan/api';
import { type Facing, type Layout, NPC_ENTITY, type NpcRole, facingYaw, layout, structureId } from './logic/layouts';
import { BOOK_ENCHANTMENTS, LOOT, rollLoot, scatterSlots } from './logic/loot';
import { classifySurface } from './logic/sites';

/** Tag + dynamic property carried by every NPC worldgen spawns (respawn bookkeeping). */
export const NPC_TAG = 'burmaldaholic_worldgen_npc';
export const HOME_PROP = 'burmaldaholic:worldgen.home';

export const npcHome = (rec: CasinoRecord, index: number): string => `${rec.id}#${index}`;

/** Missing block/entity/item types are logged once, not on every casino. */
const reported = new Set<string>();
function reportMissing(log: Logger, what: string): void {
  if (reported.has(what)) return;
  reported.add(what);
  log.warn(`${what} is not registered; skipped in generated casinos`);
}

function tablePermutation(id: string, facing: Facing): BlockPermutation | undefined {
  try {
    return BlockPermutation.resolve(id, { 'minecraft:cardinal_direction': facing } as Record<string, string>);
  } catch {
    // table block without a direction trait
  }
  try {
    return BlockPermutation.resolve(id);
  } catch {
    return undefined;
  }
}

function fillFoundation(dim: Dimension, l: Layout, o: Vector3): void {
  if (l.foundation.depth <= 0) return;
  for (let x = 0; x < l.size.x; x++)
    for (let z = 0; z < l.size.z; z++)
      for (let d = 1; d <= l.foundation.depth; d++) {
        const b = dim.getBlock({ x: o.x + x, y: o.y - d, z: o.z + z });
        if (!b) break;
        const kind = classifySurface(b.typeId);
        if (!(b.isAir || b.isLiquid || kind === 'plant' || kind === 'tree' || kind === 'liquid')) break;
        b.setType(l.foundation.block);
      }
}

function placeTables(dim: Dimension, l: Layout, o: Vector3, log: Logger): void {
  for (const t of l.tables) {
    const perm = tablePermutation(t.block, t.facing);
    if (!perm) {
      reportMissing(log, `block ${t.block}`);
      continue;
    }
    dim.getBlock({ x: o.x + t.pos.x, y: o.y + t.pos.y, z: o.z + t.pos.z })?.setPermutation(perm);
  }
}

/** The loan module's API (Loan Shark / Piglin Moneylender belong to it); set by the worldgen module. */
let loanApi: () => LoanApi | undefined = () => undefined;
export function setLoanApi(get: () => LoanApi | undefined): void {
  loanApi = get;
}

/** NPC roles owned by the loan module (spawned through LoanApi.spawnShark, respawned by loan). */
export const isLoanRole = (role: NpcRole): boolean => role === 'loan_shark' || role === 'piglin_moneylender';

/** Spawn NPC slot `index` of a casino. Returns false if no entity type for it is registered. */
export function spawnNpc(dim: Dimension, rec: CasinoRecord, index: number, log: Logger): boolean {
  const l = layout(rec.layout);
  const slot = l?.npcs[index];
  if (!slot) return false;
  const at = { x: rec.origin.x + slot.pos.x + 0.5, y: rec.origin.y + slot.pos.y, z: rec.origin.z + slot.pos.z + 0.5 };
  // Loan Shark (village casino) / Piglin Moneylender (Piglin Parlor): the loan module spawns
  // them, so they get its home/respawn bookkeeping and interaction.
  const loan = isLoanRole(slot.role) ? loanApi() : undefined;
  let e: Entity | undefined = loan?.spawnShark(dim, at, slot.role === 'piglin_moneylender' ? 'piglin' : 'shark');
  if (!e) {
    const ids = NPC_ENTITY[slot.role];
    const id = ids.find((i) => EntityTypes.get(i));
    if (!id) {
      reportMissing(log, `entity ${ids[0]}`);
      return false;
    }
    e = dim.spawnEntity(id, at);
  }
  e.setRotation({ x: 0, y: facingYaw(slot.facing) });
  e.addTag(NPC_TAG);
  e.addTag(NO_REWARD_TAG);
  for (const tag of slot.tags ?? []) e.addTag(tag);
  e.setDynamicProperty(HOME_PROP, npcHome(rec, index));
  return true;
}

function fillChests(dim: Dimension, l: Layout, o: Vector3, log: Logger): void {
  for (const c of l.chests) {
    const container = dim.getBlock({ x: o.x + c.pos.x, y: o.y + c.pos.y, z: o.z + c.pos.z })?.getComponent('minecraft:inventory')?.container;
    if (!container) continue;
    const stacks = rollLoot(LOOT[c.loot], mathRng).filter((s) => {
      if (ItemTypes.get(s.item)) return true;
      reportMissing(log, `item ${s.item}`);
      return false;
    });
    const slots = scatterSlots(stacks.length, container.size, mathRng);
    stacks.forEach((s, i) => {
      const slot = slots[i];
      if (slot === undefined) return;
      const stack = new ItemStack(s.item, 1);
      stack.amount = Math.max(1, Math.min(s.count, stack.maxAmount));
      if (s.enchant) {
        try {
          const [id, max] = pick(mathRng, BOOK_ENCHANTMENTS);
          const type = EnchantmentTypes.get(id);
          if (type) stack.getComponent('minecraft:enchantable')?.addEnchantment({ type, level: randInt(mathRng, 1, max) });
        } catch {
          // plain book is fine
        }
      }
      container.setItem(slot, stack);
    });
  }
}

/** Piglin Parlor: dig a corridor from the door (+z) until it opens into a cave (max 24 blocks). */
function carveTunnel(dim: Dimension, l: Layout, o: Vector3): void {
  for (let step = 0; step < 24; step++) {
    const z = o.z + l.size.z + step;
    let open = true;
    const cells: Vector3[] = [];
    for (let x = o.x + l.door.x0; x <= o.x + l.door.x1; x++) for (let y = o.y + 1; y <= o.y + l.door.height; y++) cells.push({ x, y, z });
    for (const p of cells) {
      const b = dim.getBlock(p);
      if (!b) return;
      if (/chest|spawner|lava/.test(b.typeId)) return;
      if (!b.isAir) open = false;
    }
    if (open) return;
    for (const p of cells) dim.getBlock(p)?.setType('minecraft:air');
    for (let x = o.x + l.door.x0; x <= o.x + l.door.x1; x++) {
      const floor = dim.getBlock({ x, y: o.y, z });
      if (floor && (floor.isAir || floor.isLiquid)) floor.setType('minecraft:blackstone');
    }
  }
}

/**
 * Build casino `layoutId` with its min corner at `origin` (floor layer at origin.y).
 * Throws if the template cannot be placed (unloaded chunks, out of world bounds).
 */
export function buildCasino(dim: Dimension, layoutId: string, origin: Vector3, log: Logger): CasinoRecord {
  const l = layout(layoutId);
  if (!l) throw new Error(`unknown casino layout ${layoutId}`);
  const o = { x: Math.floor(origin.x), y: Math.floor(origin.y), z: Math.floor(origin.z) };
  world.structureManager.place(structureId(l.id), dim, o, { includeEntities: false });
  const rec: CasinoRecord = { id: newCasinoId(l.kind, o), layout: l.id, kind: l.kind, dim: dim.id, origin: o };
  const steps: [string, () => void][] = [
    ['foundation', () => fillFoundation(dim, l, o)],
    ['tables', () => placeTables(dim, l, o, log)],
    ['npcs', () => l.npcs.forEach((_, i) => spawnNpc(dim, rec, i, log))],
    ['loot', () => fillChests(dim, l, o, log)],
    ['tunnel', () => l.kind === 'piglin_parlor' && carveTunnel(dim, l, o)],
  ];
  for (const [name, run] of steps) {
    try {
      run();
    } catch (e) {
      log.warn(`casino ${rec.id}: ${name} failed`, e);
    }
  }
  log.info(`built ${l.id} at ${dim.id} ${o.x} ${o.y} ${o.z}`);
  return rec;
}
