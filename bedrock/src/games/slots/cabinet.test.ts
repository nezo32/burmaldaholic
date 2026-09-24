/**
 * Cabinet prop lifecycle (task BS6) against a fake @minecraft/server: one prop per slot block, re-linked by tag
 * after a reload, duplicates removed, removed with the block (break or orphan sweep), unloaded blocks kept.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

interface V3 {
  x: number;
  y: number;
  z: number;
}

let tasks: Array<() => void> = [];
let intervals: Array<() => void> = [];

class FakeEntity {
  isValid = true;
  tags = new Set<string>();
  props = new Map<string, unknown>();
  rot = 0;
  constructor(
    readonly typeId: string,
    public location: V3,
    readonly dimension: FakeDim,
  ) {}
  addTag(t: string) {
    this.tags.add(t);
    return true;
  }
  getTags() {
    return [...this.tags];
  }
  remove() {
    this.isValid = false;
    this.dimension.entities = this.dimension.entities.filter((e) => e !== this);
  }
  getRotation() {
    return { x: 0, y: this.rot };
  }
  setRotation(r: { y: number }) {
    this.rot = r.y;
  }
  getProperty(k: string) {
    return this.props.get(k);
  }
  setProperty(k: string, v: unknown) {
    this.props.set(k, v);
  }
  playAnimation() {}
}

class FakeDim {
  entities: FakeEntity[] = [];
  blocks = new Map<string, string>();
  unloaded = new Set<string>();
  constructor(readonly id: string) {}
  getBlock(p: V3) {
    const k = `${p.x},${p.y},${p.z}`;
    if (this.unloaded.has(k)) return undefined;
    const typeId = this.blocks.get(k) ?? 'minecraft:air';
    const permutation = { getState: () => 'north' };
    return { typeId, location: { ...p }, dimension: this, permutation };
  }
  getEntities(q: { type?: string; tags?: string[]; location?: V3; maxDistance?: number }) {
    return this.entities.filter(
      (e) =>
        (!q.type || e.typeId === q.type) &&
        (!q.tags || q.tags.every((t) => e.tags.has(t))) &&
        (!q.location || Math.hypot(e.location.x - q.location.x, e.location.y - q.location.y, e.location.z - q.location.z) <= (q.maxDistance ?? Infinity)),
    );
  }
  spawnEntity(type: string, at: V3, opts?: { initialRotation?: number }) {
    const e = new FakeEntity(type, { ...at }, this);
    e.rot = opts?.initialRotation ?? 0;
    this.entities.push(e);
    return e;
  }
  spawnParticle() {}
}

const dims = new Map(['minecraft:overworld', 'minecraft:nether', 'minecraft:the_end'].map((id) => [id, new FakeDim(id)]));

vi.mock('@minecraft/server', () => ({
  world: { getDimension: (id: string) => dims.get(id) },
  system: {
    currentTick: 0,
    run: (fn: () => void) => (tasks.push(fn), tasks.length),
    runInterval: (fn: () => void) => (intervals.push(fn), intervals.length),
    runTimeout: (fn: () => void) => (tasks.push(fn), tasks.length),
    clearRun: () => {},
  },
  MolangVariableMap: class {
    setFloat() {}
  },
}));

vi.mock('../../core', () => ({
  presentation: {
    writeProps: (e: FakeEntity, values: Record<string, unknown>) => {
      for (const [k, v] of Object.entries(values)) e.setProperty(k, v);
      return Object.keys(values).length;
    },
    playPropAnimation: () => {},
  },
}));

const { ensureCabinet, registerCabinetComponent, removeCabinet, startCabinets, CABINET_COMPONENT } = await import('./cabinet');
const { CABINET } = await import('./v2/present/cabinet-driver');

const ow = dims.get('minecraft:overworld')!;
const POS = { x: 10, y: 64, z: -3 };
const place = (p: V3, type = 'burmaldaholic:slot_machine_gold') => {
  ow.blocks.set(`${p.x},${p.y},${p.z}`, type);
  return ow.getBlock(p)! as never;
};
const drain = () => {
  const t = tasks;
  tasks = [];
  t.forEach((f) => f());
};

beforeEach(() => {
  for (const d of dims.values()) {
    d.entities = [];
    d.blocks.clear();
    d.unloaded.clear();
  }
  tasks = [];
  intervals = [];
});

describe('slot cabinet prop lifecycle', () => {
  it('spawns one tagged, persistent prop above the block and reuses it on every later tick', () => {
    const b = place(POS);
    const e = ensureCabinet(b)!;
    expect(ow.entities).toHaveLength(1);
    expect(e.location).toEqual({ x: 10.5, y: 65, z: -2.5 });
    expect(e.getTags()).toEqual([`${CABINET.tagPrefix}10,64,-3`]);
    expect(e.getProperty(CABINET.prop.machine)).toBe(1); // gold = nether
    ensureCabinet(b);
    ensureCabinet(b);
    expect(ow.entities).toHaveLength(1);
  });

  it('re-links the existing prop by tag after a reload and removes duplicates (no double reels)', () => {
    const b = place(POS);
    const a = ow.spawnEntity(CABINET.entity, { x: 10.5, y: 65, z: -2.5 });
    a.addTag(`${CABINET.tagPrefix}10,64,-3`);
    const dup = ow.spawnEntity(CABINET.entity, { x: 10.5, y: 65, z: -2.5 });
    dup.addTag(`${CABINET.tagPrefix}10,64,-3`);
    expect(ensureCabinet(b)).toBe(a);
    expect(ow.entities).toEqual([a]);
  });

  it('does nothing for a block that is not a slot machine', () => {
    expect(ensureCabinet(place(POS, 'minecraft:stone'))).toBeUndefined();
    expect(ow.entities).toHaveLength(0);
  });

  it('removes the prop when the block breaks (deferred out of the break event)', () => {
    let handlers: Record<string, (e: unknown) => void> = {};
    registerCabinetComponent({ blockComponentRegistry: { registerCustomComponent: (id: string, h: typeof handlers) => id === CABINET_COMPONENT && (handlers = h) } } as never);
    const b = place(POS);
    handlers.onPlace!({ block: b });
    drain();
    expect(ow.entities).toHaveLength(1);
    ow.blocks.delete('10,64,-3');
    handlers.onBreak!({ block: b, dimension: ow });
    drain();
    expect(ow.entities).toHaveLength(0);
  });

  it('orphan sweep: removes untagged props and props whose block is gone, keeps unloaded and live ones', () => {
    startCabinets({ guard: (fn: () => void) => fn } as never);
    const live = ensureCabinet(place(POS))!;
    const gone = ensureCabinet(place({ x: 0, y: 70, z: 0 }))!;
    ow.blocks.delete('0,70,0');
    const far = ensureCabinet(place({ x: 500, y: 70, z: 500 }))!;
    ow.unloaded.add('500,70,500');
    const untagged = ow.spawnEntity(CABINET.entity, { x: 3, y: 3, z: 3 });
    intervals.forEach((f) => f());
    expect(ow.entities).toContain(live);
    expect(ow.entities).toContain(far);
    expect(ow.entities).not.toContain(gone);
    expect(ow.entities).not.toContain(untagged);
    removeCabinet(ow as never, POS);
    expect(ow.entities).toEqual([far]);
  });
});
