/**
 * Persistence of baccarat tables (GAME_DESIGN §20.5 / §20.9): the shoe (remaining order, cards
 * dealt), the bead plate and tie run survive restarts; the chemin de fer escrow (bank, punter
 * stakes, the drawn coup) is saved on every change so a restart can settle or refund it and
 * always return the bank. House-coup bets need nothing here: core persists every wager ticket
 * and its drawn result (§4.1).
 *
 * One sharded world property `burmaldaholic:baccarat.tables` = { [tableKey]: StoredTable }.
 */
import { worldSharded } from '../../core';
import type { HistoryData, ShoeData } from './logic';

const PROP = 'burmaldaholic:baccarat.tables';
const PENDING_ACH = 'burmaldaholic:baccarat.pending_achievements';

export interface StoredStake {
  /** player id */
  i: string;
  /** name */
  n: string;
  /** chips escrowed */
  a: number;
}

export interface StoredChemmy {
  /** bank: owner id, name, amount, consecutive wins */
  bank?: { o: string; n: string; a: number; w: number };
  stakes: StoredStake[];
  /** Banco caller id */
  banco?: string;
  /** the drawn coup (card ids in deal order) once dealt */
  coup?: string[];
}

export interface StoredTable {
  shoe?: ShoeData;
  hist?: HistoryData;
  chem?: StoredChemmy;
}

type Store = Record<string, StoredTable>;

function readAll(): Store {
  const v = worldSharded.read<unknown>(PROP, {});
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Store) : {};
}

export const tableStore = {
  all(): Store {
    return readAll();
  },
  get(key: string): StoredTable | undefined {
    return readAll()[key];
  },
  update(key: string, fn: (t: StoredTable) => StoredTable | undefined): void {
    const all = readAll();
    const next = fn(all[key] ?? {});
    if (next === undefined) delete all[key];
    else all[key] = next;
    worldSharded.write(PROP, all);
  },
  delete(key: string): void {
    const all = readAll();
    if (!(key in all)) return;
    delete all[key];
    worldSharded.write(PROP, all);
  },
};

/** Advancements core does not know yet (kept until a core update registers them). */
export const pendingAchievements = {
  all(): Record<string, string[]> {
    const v = worldSharded.read<unknown>(PENDING_ACH, {});
    return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, string[]>) : {};
  },
  add(playerId: string, id: string): void {
    const all = this.all();
    const list = all[playerId] ?? [];
    if (list.includes(id)) return;
    all[playerId] = [...list, id];
    worldSharded.write(PENDING_ACH, all);
  },
  write(all: Record<string, string[]>): void {
    worldSharded.write(PENDING_ACH, all);
  },
};
