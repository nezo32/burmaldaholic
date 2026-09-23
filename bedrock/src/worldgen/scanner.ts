/**
 * Script-driven casino generation (docs/architecture/bedrock.md §10 "Fallback 2").
 *
 * Every few seconds the scanner looks around each player for the residents of the structures a
 * casino belongs to (villagers -> village, piglin brutes -> bastion, shulkers -> End City). A new
 * structure becomes a job that is worked on a little per tick: confirm it, roll the configured
 * chance once, find a lot, build. The decision is persisted per site so a structure is judged
 * exactly once. Runs only while casino mode and `worldgen.enabled` are on.
 */
import { type Dimension, type Vector3, world } from '@minecraft/server';
import { type Logger, mathRng, worldTick } from '../core';
import { buildCasino } from './builder';
import type { CasinoRecord } from './logic/casinos';
import { type CasinoKind, KIND_LAYOUT, layout, villageLayoutId, villageStyleForBiome } from './logic/layouts';
import {
  type NetherKind,
  type Site,
  type SiteIndex,
  type SiteState,
  type SurfaceSample,
  type TopSample,
  candidateOrigins,
  classifyNether,
  classifySurface,
  evaluateFootprint,
  evaluateNetherVolume,
  footprintSamples,
  isEndCityBlock,
  looksLikeVillage,
  loungeFits,
  rollSite,
  volumeSamples,
} from './logic/sites';

interface Target {
  readonly kind: CasinoKind;
  readonly types: readonly string[];
  readonly chanceKey: string;
}

const TARGETS: Readonly<Record<string, Target>> = {
  'minecraft:overworld': { kind: 'village_casino', types: ['minecraft:villager_v2', 'minecraft:villager'], chanceKey: 'worldgen.villageCasino.chance' },
  'minecraft:nether': { kind: 'piglin_parlor', types: ['minecraft:piglin_brute'], chanceKey: 'worldgen.piglinParlor.chance' },
  'minecraft:the_end': { kind: 'high_roller', types: ['minecraft:shulker'], chanceKey: 'worldgen.highRoller.chance' },
};

const SCAN_RADIUS = 96;
const MAX_ATTEMPTS = 5;
const RETRY_TICKS = 400;

interface Candidate {
  readonly x: number;
  readonly y: number;
  readonly z: number;
  /** End: roof height the lounge stands on */
  readonly roofY?: number;
}

interface Job {
  readonly kind: CasinoKind;
  readonly dim: Dimension;
  readonly anchor: Vector3;
  phase: 'survey' | 'candidates';
  layoutId: string;
  candidates: Candidate[];
  i: number;
  sawUnloaded: boolean;
  attempts: number;
  notBefore: number;
}

export interface ScannerHost {
  readonly log: Logger;
  readonly sites: SiteIndex;
  chance(key: string): number;
  decide(site: Site): void;
  built(rec: CasinoRecord): void;
}

function loaded(dim: Dimension, x: number, z: number): boolean {
  try {
    return dim.isChunkLoaded({ x, y: 0, z });
  } catch {
    return false;
  }
}

function top(dim: Dimension, x: number, z: number): { y: number; typeId: string } | undefined {
  if (!loaded(dim, x, z)) return undefined;
  try {
    const b = dim.getTopmostBlock({ x, z });
    return b ? { y: b.location.y, typeId: b.typeId } : undefined;
  } catch {
    return undefined;
  }
}

export class Scanner {
  private readonly jobs: Job[] = [];

  constructor(private readonly host: ScannerHost) {}

  pending(): number {
    return this.jobs.length;
  }

  /** Look around every player for new structures (call every ~100 ticks). */
  scan(): void {
    for (const player of world.getPlayers()) {
      const dim = player.dimension;
      const target = TARGETS[dim.id];
      if (!target) continue;
      for (const type of target.types) {
        let found = false;
        let residents;
        try {
          residents = dim.getEntities({ type, location: player.location, maxDistance: SCAN_RADIUS });
        } catch {
          continue; // e.g. a legacy entity type this version no longer knows
        }
        for (const e of residents) {
          const { x, z } = e.location;
          if (this.host.sites.near(dim.id, x, z) || this.jobs.some((j) => j.dim.id === dim.id && (j.anchor.x - x) ** 2 + (j.anchor.z - z) ** 2 < 128 ** 2)) continue;
          this.jobs.push({ kind: target.kind, dim, anchor: { ...e.location }, phase: 'survey', layoutId: '', candidates: [], i: 0, sawUnloaded: false, attempts: 0, notBefore: 0 });
          found = true;
          break;
        }
        if (found) break;
      }
    }
  }

  /** Do a small slice of work on the first ready job (call every couple of ticks). */
  step(): void {
    const now = worldTick();
    const job = this.jobs.find((j) => j.notBefore <= now);
    if (!job) return;
    try {
      if (job.phase === 'survey') this.survey(job, now);
      else this.tryCandidate(job, now);
    } catch (e) {
      this.host.log.warn('worldgen job failed', e);
      this.finish(job, 'no_room');
    }
  }

  private finish(job: Job, state: SiteState): void {
    const i = this.jobs.indexOf(job);
    if (i >= 0) this.jobs.splice(i, 1);
    this.host.decide({ kind: job.kind, dim: job.dim.id, x: Math.round(job.anchor.x), z: Math.round(job.anchor.z), state });
  }

  private retryLater(job: Job, now: number): void {
    if (++job.attempts >= MAX_ATTEMPTS) return this.finish(job, job.phase === 'survey' ? 'skipped' : 'no_room');
    job.notBefore = now + RETRY_TICKS;
    job.i = 0;
    job.sawUnloaded = false;
  }

  private roll(job: Job): boolean {
    const target = TARGETS[job.dim.id];
    if (!target || !rollSite(this.host.chance(target.chanceKey), mathRng.next())) {
      this.finish(job, 'skipped');
      return false;
    }
    return true;
  }

  private survey(job: Job, now: number): void {
    const { anchor, dim } = job;
    if (job.kind === 'village_casino') {
      const ids: string[] = [];
      let total = 0;
      for (let dx = -48; dx <= 48; dx += 8)
        for (let dz = -48; dz <= 48; dz += 8) {
          total++;
          const t = top(dim, Math.floor(anchor.x + dx), Math.floor(anchor.z + dz));
          if (t) ids.push(t.typeId);
        }
      if (!looksLikeVillage(ids)) {
        // a lone villager (trading hall, zombie cure...) is not a village
        if (ids.length < total * 0.6) this.retryLater(job, now);
        else this.finish(job, 'skipped');
        return;
      }
      if (!this.roll(job)) return;
      job.layoutId = villageLayoutId(villageStyleForBiome(dim.getBiome(anchor).id));
      const l = layout(job.layoutId);
      if (!l) return this.finish(job, 'no_room');
      job.candidates = candidateOrigins(anchor, l.size, [36, 48, 60, 72]).map((c) => ({ ...c, y: 0 }));
    } else if (job.kind === 'piglin_parlor') {
      if (!this.roll(job)) return;
      job.layoutId = KIND_LAYOUT.piglin_parlor;
      const l = layout(job.layoutId);
      if (!l) return this.finish(job, 'no_room');
      const y = Math.floor(anchor.y) - 1;
      if (y < dim.heightRange.min + 1 || y + l.size.y > 126) return this.finish(job, 'no_room');
      job.candidates = candidateOrigins(anchor, l.size, [34, 44, 54]).map((c) => ({ ...c, y }));
    } else {
      const samples: TopSample[] = [];
      let total = 0;
      for (let dx = -24; dx <= 24; dx += 4)
        for (let dz = -24; dz <= 24; dz += 4) {
          total++;
          const x = Math.floor(anchor.x + dx);
          const z = Math.floor(anchor.z + dz);
          const t = top(dim, x, z);
          if (t) samples.push({ x, z, y: t.y, typeId: t.typeId });
        }
      const roofs = samples.filter((s) => isEndCityBlock(s.typeId)).sort((a, b) => b.y - a.y);
      if (roofs.length === 0) {
        if (samples.length < total * 0.6) this.retryLater(job, now);
        else this.finish(job, 'skipped');
        return;
      }
      if (!this.roll(job)) return;
      job.layoutId = KIND_LAYOUT.high_roller;
      const l = layout(job.layoutId);
      if (!l) return this.finish(job, 'no_room');
      const half = Math.floor(l.size.x / 2);
      job.candidates = roofs.slice(0, 6).map((r) => ({ x: r.x - half, y: r.y + 1, z: r.z - half, roofY: r.y }));
    }
    job.phase = 'candidates';
    job.i = 0;
  }

  private tryCandidate(job: Job, now: number): void {
    const l = layout(job.layoutId);
    const c = job.candidates[job.i];
    if (!l || !c) {
      if (job.sawUnloaded) this.retryLater(job, now);
      else this.finish(job, 'no_room');
      return;
    }
    job.i++;
    const { dim } = job;
    const corners = [
      [c.x, c.z],
      [c.x + l.size.x - 1, c.z],
      [c.x, c.z + l.size.z - 1],
      [c.x + l.size.x - 1, c.z + l.size.z - 1],
    ] as const;
    if (!corners.every(([x, z]) => loaded(dim, x, z))) {
      job.sawUnloaded = true;
      return;
    }
    // never build on top of someone: try this lot again later
    const occupied = dim.getPlayers().some((p) => {
      const { x, z } = p.location;
      return x >= c.x - 3 && x <= c.x + l.size.x + 3 && z >= c.z - 3 && z <= c.z + l.size.z + 3;
    });
    if (occupied) {
      job.sawUnloaded = true;
      return;
    }
    let origin: Vector3 | undefined;
    if (job.kind === 'village_casino') {
      const samples: (SurfaceSample | undefined)[] = footprintSamples(l.size, 4).map(({ dx, dz }) => {
        const t = top(dim, c.x + dx, c.z + dz);
        return t ? { y: t.y, kind: classifySurface(t.typeId) } : undefined;
      });
      const v = evaluateFootprint(samples);
      if (v.ok && v.floorY + l.size.y < dim.heightRange.max) origin = { x: c.x, y: v.floorY, z: c.z };
    } else if (job.kind === 'piglin_parlor') {
      const samples: (NetherKind | undefined)[] = volumeSamples({ x: l.size.x, y: 9, z: l.size.z }, 4).map((p) => {
        try {
          const b = dim.getBlock({ x: c.x + p.x, y: c.y + p.y, z: c.z + p.z });
          return b ? classifyNether(b.typeId) : undefined;
        } catch {
          return undefined;
        }
      });
      if (evaluateNetherVolume(samples).ok) origin = { x: c.x, y: c.y, z: c.z };
    } else {
      const tops = footprintSamples(l.size, 3).map(({ dx, dz }) => top(dim, c.x + dx, c.z + dz)?.y);
      if (c.roofY !== undefined && loungeFits(c.roofY, tops, l.size.y, dim.heightRange.max)) origin = { x: c.x, y: c.y, z: c.z };
    }
    if (!origin) return;
    const rec = buildCasino(dim, job.layoutId, origin, this.host.log);
    this.host.built(rec);
    this.finish(job, 'placed');
  }
}
