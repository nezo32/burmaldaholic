/**
 * In-world slot cabinets (animation/slots.md §6.6, task BS6): one AI-free `burmaldaholic:slot_reels` prop per slot
 * block, spawned when the block is placed, re-created by the block's own tick when missing (structure-placed
 * cabinets, `/kill`, chunk loads), de-duplicated and removed with the block. The prop is decoration only: the round
 * is always decided and persisted by the slots service before anything moves here.
 *
 * `playCabinet(...)` is the entry point for the slots service (lane B-L8, S-B5): it plans the spin with the pure
 * `planCabinet` (v2/present/cabinet-driver.ts) and schedules the property writes / land animations at the same
 * ticks as the player's form (F10); `finishCabinet(...)` jumps to the terminal window (skip / close / leave, F8).
 */
import { type Block, type Dimension, type Entity, MolangVariableMap, type StartupEvent, type Vector3, system, world } from '@minecraft/server';
import { type ModuleContext, presentation } from '../../core';
import { MACHINES, type MachineId } from './v2/logic/types';
import { CABINET, type CabinetMemory, CabinetPlayer, type CabinetSpin, type CabinetTarget, machineIndex, planCabinet } from './v2/present/cabinet-driver';

/** Block custom component (declared in packs/slots/BP/blocks/slots/*.json with `minecraft:tick`). */
export const CABINET_COMPONENT = 'burmaldaholic:slot_cabinet';

const MACHINE_OF_BLOCK: Record<string, MachineId> = Object.fromEntries(
  (Object.entries(MACHINES) as Array<[MachineId, { block: string }]>).map(([m, d]) => [`burmaldaholic:${d.block}`, m]),
);

/** Yaw that turns the prop's front (model north faces) toward the block's front (`minecraft:cardinal_direction`). */
const YAW: Record<string, number> = { south: 0, west: 90, north: 180, east: -90 };

const key = (p: Vector3): string => `${Math.floor(p.x)},${Math.floor(p.y)},${Math.floor(p.z)}`;
const tagOf = (p: Vector3): string => `${CABINET.tagPrefix}${key(p)}`;
const anchor = (b: Vector3): Vector3 => ({ x: Math.floor(b.x) + 0.5, y: Math.floor(b.y) + 1, z: Math.floor(b.z) + 0.5 });

function facingOf(block: Block): string {
  try {
    return String(block.permutation.getState('minecraft:cardinal_direction') ?? 'north');
  } catch {
    return 'north';
  }
}

/** All props linked to the block at `pos` (normally one). */
function propsAt(dim: Dimension, pos: Vector3): Entity[] {
  try {
    return dim.getEntities({ type: CABINET.entity, tags: [tagOf(pos)], location: anchor(pos), maxDistance: 1.5 });
  } catch {
    return [];
  }
}

/** The prop of the cabinet at `pos`, spawning it (and removing duplicates) when needed. */
export function ensureCabinet(block: Block): Entity | undefined {
  const machine = MACHINE_OF_BLOCK[block.typeId];
  if (!machine) return undefined;
  const found = propsAt(block.dimension, block.location);
  for (const extra of found.slice(1)) extra.remove();
  let e = found[0];
  const yaw = YAW[facingOf(block)] ?? 180;
  if (!e) {
    try {
      e = block.dimension.spawnEntity(CABINET.entity, anchor(block.location), { initialRotation: yaw, initialPersistence: true });
      e.addTag(tagOf(block.location));
    } catch {
      return undefined; // chunk not ready: the next block tick retries
    }
  } else if (Math.abs((e.getRotation().y - yaw + 540) % 360 - 180) > 1) e.setRotation({ x: 0, y: yaw });
  presentation.writeProps(e, { [CABINET.prop.machine]: machineIndex(machine) });
  return e;
}

export function removeCabinet(dim: Dimension, pos: Vector3): void {
  cancel(dim, pos);
  for (const e of propsAt(dim, pos)) e.remove();
}

/** Startup: the block component (place / tick / break). */
export function registerCabinetComponent(event: StartupEvent): void {
  event.blockComponentRegistry.registerCustomComponent(CABINET_COMPONENT, {
    onPlace: (e) => void system.run(() => ensureCabinet(e.block)),
    onTick: (e) => void ensureCabinet(e.block),
    onBreak: (e) => removeCabinet(e.dimension, e.block.location),
  });
}

/** World load: sweep props whose block is gone (explosions, pistons, /fill), every 5 s, while casino mode is on. */
export function startCabinets(ctx: ModuleContext): void {
  system.runInterval(
    ctx.guard(() => {
      for (const id of ['minecraft:overworld', 'minecraft:nether', 'minecraft:the_end']) {
        let props: Entity[];
        try {
          props = world.getDimension(id).getEntities({ type: CABINET.entity });
        } catch {
          continue;
        }
        for (const e of props) {
          const tag = e.getTags().find((t) => t.startsWith(CABINET.tagPrefix));
          const [x, y, z] = (tag?.slice(CABINET.tagPrefix.length).split(',') ?? []).map(Number);
          if (x === undefined || y === undefined || z === undefined || [x, y, z].some((v) => !Number.isFinite(v))) {
            e.remove();
            continue;
          }
          try {
            const b = e.dimension.getBlock({ x, y, z });
            if (b && !MACHINE_OF_BLOCK[b.typeId]) removeCabinet(e.dimension, { x, y, z });
          } catch {
            /* unloaded: keep */
          }
        }
      }
    }),
    100,
  );
}

// ---- playback ---------------------------------------------------------------------------------------------------
const players = new Map<string, CabinetPlayer>();
const memory = new Map<string, CabinetMemory>();
const cancel = (dim: Dimension, pos: Vector3): void => {
  const k = `${dim.id}|${key(pos)}`;
  players.get(k)?.finish();
  players.delete(k);
};

function target(e: Entity): CabinetTarget {
  return {
    write: (props) => presentation.writeProps(e, props),
    playLand: (r) => presentation.playPropAnimation(e, CABINET.landAnimation(r), CABINET.landController(r)),
    particle: (id) => {
      try {
        const vars = new MolangVariableMap();
        vars.setFloat('variable.count', id === CABINET.particles.tumble ? 12 : 8);
        const l = e.location;
        e.dimension.spawnParticle(id, { x: l.x, y: l.y + 0.45, z: l.z }, vars);
      } catch {
        /* decoration */
      }
    },
    isValid: () => e.isValid,
  };
}

/**
 * Plays one presented round on the cabinet at `pos`. `startTick` = the tick of timeline time 0 (the same tick the
 * player's form uses), so every viewer lands on the same tick. A previous round still playing is finished first.
 */
export function playCabinet(dim: Dimension, pos: Vector3, spin: CabinetSpin, startTick: number, opts: { particles?: boolean } = {}): void {
  const block = dim.getBlock(pos);
  const e = block ? ensureCabinet(block) : undefined;
  if (!e) return;
  cancel(dim, pos);
  const k = `${dim.id}|${key(pos)}`;
  const mem = memory.get(k) ?? { seq: Number(e.getProperty(CABINET.prop.seq) ?? 0) };
  memory.set(k, mem);
  const cues = planCabinet(spin, mem);
  const clock = {
    nowMs: () => (system.currentTick - startTick) * 50,
    after: (ms: number, fn: () => void) => {
      const id = system.runTimeout(fn, Math.max(1, Math.ceil(ms / 50)));
      return () => system.clearRun(id);
    },
  };
  const p = new CabinetPlayer(cues, target(e), clock, opts).play();
  players.set(k, p);
}

/** Skip / close / leave / settle: the cabinet shows the terminal window now (F8). */
export function finishCabinet(dim: Dimension, pos: Vector3): void {
  cancel(dim, pos);
}
