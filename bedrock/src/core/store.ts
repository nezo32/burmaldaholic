/**
 * Dynamic-property persistence helpers (world or entity). Values are JSON strings; a corrupt
 * or missing value yields the fallback. Properties are limited to 32 767 chars, so large
 * lists use `ChunkedList`, which spreads entries over `<id>.0`, `<id>.1`, ...
 */
import { type Entity, world } from '@minecraft/server';

type Holder = Pick<Entity, 'getDynamicProperty' | 'setDynamicProperty'>;

export function readJson<T>(holder: Holder, id: string, fallback: T): T {
  const raw = holder.getDynamicProperty(id);
  if (typeof raw !== 'string') return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

/** `undefined` clears the property. */
export function writeJson(holder: Holder, id: string, value: unknown): void {
  holder.setDynamicProperty(id, value === undefined ? undefined : JSON.stringify(value));
}

export const worldJson = {
  read: <T>(id: string, fallback: T): T => readJson(world, id, fallback),
  write: (id: string, value: unknown): void => writeJson(world, id, value),
};

const CHUNK_CHARS = 30_000;

/** FIFO list of strings persisted across several world properties, capped at `cap` entries (a number or a live config read). */
export class ChunkedList {
  private items: string[] | undefined;

  constructor(
    private readonly id: string,
    private readonly cap: number | (() => number),
  ) {}

  private limit(): number {
    const c = typeof this.cap === 'function' ? this.cap() : this.cap;
    return Number.isFinite(c) ? Math.max(0, Math.floor(c)) : 0;
  }

  private load(): string[] {
    if (this.items) return this.items;
    const out: string[] = [];
    for (let i = 0; ; i++) {
      const raw = world.getDynamicProperty(`${this.id}.${i}`);
      if (typeof raw !== 'string') break;
      if (raw) out.push(...raw.split('\n'));
    }
    return (this.items = out);
  }

  has(v: string): boolean {
    return this.load().includes(v);
  }

  add(v: string): void {
    const a = this.load();
    if (a.includes(v)) return;
    a.push(v);
    const cap = this.limit();
    while (a.length > cap) a.shift();
    this.save();
  }

  delete(v: string): boolean {
    const a = this.load();
    const i = a.indexOf(v);
    if (i < 0) return false;
    a.splice(i, 1);
    this.save();
    return true;
  }

  private save(): void {
    const a = this.load();
    const chunks: string[] = [];
    let cur: string[] = [];
    let len = 0;
    for (const s of a) {
      if (len + s.length + 1 > CHUNK_CHARS && cur.length) {
        chunks.push(cur.join('\n'));
        cur = [];
        len = 0;
      }
      cur.push(s);
      len += s.length + 1;
    }
    if (cur.length) chunks.push(cur.join('\n'));
    let i = 0;
    for (; i < chunks.length; i++) world.setDynamicProperty(`${this.id}.${i}`, chunks[i]);
    // Clear stale trailing chunks.
    while (typeof world.getDynamicProperty(`${this.id}.${i}`) === 'string') world.setDynamicProperty(`${this.id}.${i++}`, undefined);
  }
}

/** Absolute world time in ticks (keeps running while players are offline; GAME_DESIGN conventions). */
export const worldTick = (): number => world.getAbsoluteTime();
