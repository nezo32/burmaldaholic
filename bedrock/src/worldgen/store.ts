/**
 * Persistence for decided sites and built casinos: newline-separated compact strings spread over
 * `<prefix>.0`, `<prefix>.1`, ... world dynamic properties (32 767-char limit per property).
 */
import { world } from '@minecraft/server';
import { type CasinoRecord, decodeCasino, encodeCasino } from './logic/casinos';
import { type Site, chunkStrings, decodeSite, encodeSite } from './logic/sites';

const SITES = 'burmaldaholic:worldgen.sites';
const CASINOS = 'burmaldaholic:worldgen.casinos';
const CHUNK = 30_000;

function loadList(prefix: string): string[] {
  const out: string[] = [];
  for (let i = 0; ; i++) {
    const raw = world.getDynamicProperty(`${prefix}.${i}`);
    if (typeof raw !== 'string') break;
    if (raw) out.push(...raw.split('\n'));
  }
  return out;
}

function saveList(prefix: string, items: readonly string[]): void {
  const chunks = chunkStrings(items, CHUNK);
  let i = 0;
  for (; i < chunks.length; i++) world.setDynamicProperty(`${prefix}.${i}`, chunks[i]);
  while (typeof world.getDynamicProperty(`${prefix}.${i}`) === 'string') world.setDynamicProperty(`${prefix}.${i++}`, undefined);
}

export const siteStore = {
  load: (): Site[] => loadList(SITES).flatMap((s) => decodeSite(s) ?? []),
  save: (sites: readonly Site[]): void => saveList(SITES, sites.map(encodeSite)),
};

export const casinoStore = {
  load: (): CasinoRecord[] => loadList(CASINOS).flatMap((s) => decodeCasino(s) ?? []),
  save: (casinos: readonly CasinoRecord[]): void => saveList(CASINOS, casinos.map(encodeCasino)),
};
