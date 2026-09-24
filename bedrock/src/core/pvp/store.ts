/**
 * PvP persistence (PVP.md §3.6, pvp-bots.md §5.1): one world property per match
 * `burmaldaholic:pvp:<id>` (JSON MatchRecord) + an index `burmaldaholic:pvp_index` (id list).
 * The DRAWN record is written synchronously, in the same tick, BEFORE the first reveal title.
 * Player records: player property `burmaldaholic:pvp_record` (JSON PvpPlayerRecord).
 */
import type { Player } from '@minecraft/server';
import type { MatchRecord } from '../logic/pvp/match';
import { EMPTY_RECORD, type PvpPlayerRecord } from '../logic/pvp/rivalry';
import { readJson, worldJson, writeJson } from '../store';

export const PVP_MATCH_PROP = (id: string): string => `burmaldaholic:pvp:${id}`;
export const PVP_INDEX_PROP = 'burmaldaholic:pvp_index';
export const PVP_RECORD_PROP = 'burmaldaholic:pvp_record';

export const pvpStore = {
  ids: (): string[] => worldJson.read<string[]>(PVP_INDEX_PROP, []),
  read: (id: string): MatchRecord | undefined => worldJson.read<MatchRecord | undefined>(PVP_MATCH_PROP(id), undefined),
  write(m: MatchRecord): void {
    worldJson.write(PVP_MATCH_PROP(m.id), m);
    const ids = pvpStore.ids();
    if (!ids.includes(m.id)) worldJson.write(PVP_INDEX_PROP, [...ids, m.id]);
  },
  remove(id: string): void {
    worldJson.write(PVP_MATCH_PROP(id), undefined);
    worldJson.write(
      PVP_INDEX_PROP,
      pvpStore.ids().filter((x) => x !== id),
    );
  },
  record: (p: Player): PvpPlayerRecord => readJson(p, PVP_RECORD_PROP, EMPTY_RECORD),
  writeRecord: (p: Player, r: PvpPlayerRecord): void => writeJson(p, PVP_RECORD_PROP, r),
};
