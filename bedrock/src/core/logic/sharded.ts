/**
 * Sharded JSON values (PURE over a key-value holder): a dynamic property holds at most 32 767
 * characters, so world-level stores that grow with the player count (maps keyed by player id,
 * id lists) are written as JSON split over `<id>:0`, `<id>:1`, ... A value written before
 * sharding under the plain `<id>` key is still read (migration) and removed on the next write.
 *
 * `core/store.ts` binds this to `world` as `worldSharded`.
 */

/** The part of a dynamic-property holder these helpers need (world or entity). */
export interface KvHolder {
  getDynamicProperty(id: string): unknown;
  setDynamicProperty(id: string, value?: string): void;
}

/** Characters per shard (below the 32 767 limit, with headroom). */
export const SHARD_CHARS = 30_000;

/** Upper bound on shards read (defensive against a runaway loop on a corrupt world). */
const MAX_SHARDS = 4096;

export const shardKey = (id: string, i: number): string => `${id}:${i}`;

/**
 * Split a string into pieces of at most `size` chars, never between the two halves of a
 * surrogate pair (a lone surrogate would not survive the engine's UTF-8 round trip).
 */
export function splitShards(text: string, size = SHARD_CHARS): string[] {
  const n = Math.max(2, Math.floor(size));
  const out: string[] = [];
  let i = 0;
  while (i < text.length) {
    let end = Math.min(text.length, i + n);
    if (end < text.length) {
      const c = text.charCodeAt(end - 1);
      if (c >= 0xd800 && c <= 0xdbff) end--;
    }
    out.push(text.slice(i, end));
    i = end;
  }
  return out;
}

/** Concatenated shard text, or undefined when there is no shard 0. */
function readShards(h: KvHolder, id: string): string | undefined {
  let text: string | undefined;
  for (let i = 0; i < MAX_SHARDS; i++) {
    const raw = h.getDynamicProperty(shardKey(id, i));
    if (typeof raw !== 'string') break;
    text = (text ?? '') + raw;
  }
  return text;
}

/** Read a sharded JSON value (falls back to the legacy single `<id>` property). */
export function readSharded<T>(h: KvHolder, id: string, fallback: T): T {
  const raw = readShards(h, id) ?? h.getDynamicProperty(id);
  if (typeof raw !== 'string') return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

/** Write a sharded JSON value; `undefined` clears it. Stale shards and the legacy key are removed. */
export function writeSharded(h: KvHolder, id: string, value: unknown, size = SHARD_CHARS): void {
  const shards = value === undefined ? [] : splitShards(JSON.stringify(value), size);
  let i = 0;
  for (; i < shards.length; i++) h.setDynamicProperty(shardKey(id, i), shards[i]);
  while (i < MAX_SHARDS && typeof h.getDynamicProperty(shardKey(id, i)) === 'string') h.setDynamicProperty(shardKey(id, i++), undefined);
  if (h.getDynamicProperty(id) !== undefined) h.setDynamicProperty(id, undefined);
}
