/**
 * Persistence of player-owned casinos (world dynamic properties, JSON):
 *   burmaldaholic:multiplayer.casinos          { counter, casinos: Casino[], closing: Closing[] }
 *   burmaldaholic:multiplayer.tables.<casino>  { [tableKey]: OwnedTable }   (one property per casino)
 *   burmaldaholic:multiplayer.inactive         { [tableKey]: OwnedTable }   (charter removed)
 *   burmaldaholic:multiplayer.stats.<casino>   CasinoStats
 *   burmaldaholic:multiplayer.payouts          { [playerId]: chips }        (owner offline at closing)
 * The bankroll itself is a core account (`economy.bankroll(casino.id)`).
 */
import { worldJson } from '../core';
import { type Casino, type CasinoStats, type OwnedTable, nextCasinoId } from './logic';

const P = 'burmaldaholic:multiplayer.';

export interface Closing {
  id: string;
  ownerId: string;
  ownerName: string;
}

interface Registry {
  counter: number;
  casinos: Casino[];
  closing: Closing[];
}

export class CasinoStore {
  private reg: Registry = { counter: 0, casinos: [], closing: [] };
  /** every owned or inactive table by key */
  readonly tables = new Map<string, OwnedTable>();

  load(): void {
    const r = worldJson.read<Partial<Registry>>(`${P}casinos`, {});
    this.reg = { counter: r.counter ?? 0, casinos: r.casinos ?? [], closing: r.closing ?? [] };
    this.tables.clear();
    for (const c of this.reg.casinos) {
      for (const [k, v] of Object.entries(worldJson.read<Record<string, OwnedTable>>(`${P}tables.${c.id}`, {}))) this.tables.set(k, { ...v, casinoId: c.id });
    }
    for (const [k, v] of Object.entries(worldJson.read<Record<string, OwnedTable>>(`${P}inactive`, {}))) {
      if (!this.tables.has(k)) this.tables.set(k, { ...v, casinoId: undefined });
    }
  }

  get casinos(): readonly Casino[] {
    return this.reg.casinos;
  }

  get closing(): readonly Closing[] {
    return this.reg.closing;
  }

  casino(id: string | undefined): Casino | undefined {
    return id === undefined ? undefined : this.reg.casinos.find((c) => c.id === id);
  }

  ownedBy(playerId: string): Casino[] {
    return this.reg.casinos.filter((c) => c.ownerId === playerId);
  }

  nextId(): string {
    const n = nextCasinoId(this.reg.counter);
    this.reg.counter = n.counter;
    return n.id;
  }

  saveRegistry(): void {
    worldJson.write(`${P}casinos`, this.reg);
  }

  addCasino(c: Casino): void {
    this.reg.casinos.push(c);
    this.saveRegistry();
  }

  /** Remove the casino from the active list and queue its bankroll for payout. */
  removeCasino(c: Casino): void {
    this.reg.casinos = this.reg.casinos.filter((x) => x.id !== c.id);
    this.reg.closing.push({ id: c.id, ownerId: c.ownerId, ownerName: c.ownerName });
    worldJson.write(`${P}tables.${c.id}`, undefined);
    worldJson.write(`${P}stats.${c.id}`, undefined);
    this.saveRegistry();
  }

  doneClosing(id: string): void {
    this.reg.closing = this.reg.closing.filter((x) => x.id !== id);
    this.saveRegistry();
  }

  tablesOf(casinoId: string): [string, OwnedTable][] {
    return [...this.tables].filter(([, t]) => t.casinoId === casinoId);
  }

  /** Persist the table maps touched by a change (per casino and/or the inactive map). */
  saveTables(...casinoIds: (string | undefined)[]): void {
    for (const id of new Set(casinoIds)) {
      const out: Record<string, OwnedTable> = {};
      for (const [k, t] of this.tables) if (t.casinoId === id) out[k] = t;
      const empty = Object.keys(out).length === 0;
      worldJson.write(id === undefined ? `${P}inactive` : `${P}tables.${id}`, empty ? undefined : out);
    }
  }

  stats(id: string): CasinoStats | undefined {
    return worldJson.read<CasinoStats | undefined>(`${P}stats.${id}`, undefined);
  }

  saveStats(id: string, s: CasinoStats): void {
    worldJson.write(`${P}stats.${id}`, s);
  }

  addPayout(playerId: string, amount: number): void {
    const all = worldJson.read<Record<string, number>>(`${P}payouts`, {});
    all[playerId] = (all[playerId] ?? 0) + amount;
    worldJson.write(`${P}payouts`, all);
  }

  takePayout(playerId: string): number {
    const all = worldJson.read<Record<string, number>>(`${P}payouts`, {});
    const v = all[playerId] ?? 0;
    if (v) {
      delete all[playerId];
      worldJson.write(`${P}payouts`, Object.keys(all).length ? all : undefined);
    }
    return v;
  }
}
