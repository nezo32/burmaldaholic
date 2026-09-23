/**
 * Offline mailbox: results for players who are not online (see logic/offline.ts). Stored in the
 * world property `burmaldaholic:core.offline.<playerId>`; applied by the wager service when the
 * player joins (before stale rounds are refunded).
 */
import { type Player, world } from '@minecraft/server';
import { type OfflineEntry, isEmptyOffline, normalizeOffline } from './logic/offline';
import { worldJson } from './store';

const PREFIX = 'burmaldaholic:core.offline.';

/** The online Player with this id (a stale Player object of a rejoined player resolves to the new one). */
export function onlinePlayer(playerId: string): Player | undefined {
  return world.getAllPlayers().find((p) => p.id === playerId && p.isValid);
}

/** `player` itself when valid, else the online player with the same id, else undefined (offline). */
export function livePlayer(player: Player | undefined, playerId: string): Player | undefined {
  if (player?.isValid) return player;
  return onlinePlayer(playerId);
}

export const offlineStore = {
  read(playerId: string): OfflineEntry {
    return normalizeOffline(worldJson.read<unknown>(PREFIX + playerId, undefined));
  },
  update(playerId: string, fn: (e: OfflineEntry) => OfflineEntry): void {
    const next = fn(this.read(playerId));
    worldJson.write(PREFIX + playerId, isEmptyOffline(next) ? undefined : next);
  },
  /** Read and clear. */
  take(playerId: string): OfflineEntry {
    const e = this.read(playerId);
    worldJson.write(PREFIX + playerId, undefined);
    return e;
  },
};
