/**
 * Casino Buildings module (GAME_DESIGN §16): Lucky Villager casinos near villages, Piglin Parlors
 * next to bastions, High Roller Lounges on End City towers.
 *
 * How it generates (Bedrock specifics, see docs/architecture/bedrock.md §10):
 *  - templates: `.mcstructure` shells generated from logic/layouts.ts by tools/gen-assets.mjs;
 *  - placement: script-driven (scanner.ts) — vanilla villages/bastions/End Cities cannot be
 *    extended by add-on jigsaw pools, and a script lets generation respect casino mode,
 *    `worldgen.enabled` and the per-structure chance configs at run time;
 *  - furniture: game tables (other modules' blocks), NPCs and loot are placed by script
 *    (builder.ts) right after the shell.
 */
import { ActionFormData } from '@minecraft/server-ui';
import { type Dimension, type Player, type Vector3, system, world } from '@minecraft/server';
import { type CasinoModule, type ModuleContext, type Raw, isOperator, showForm, t, worldTick } from '../core';
import { type CasinoInfo, WORLDGEN_SERVICE, type WorldgenApi } from './api';
import { HOME_PROP, NPC_TAG, buildCasino, npcHome, spawnNpc } from './builder';
import { type CasinoRecord, PRESETS, casinoBox, findCasinoAt, respawnDecision, tableSlotAt } from './logic/casinos';
import { type CasinoKind, KIND_LAYOUT, layout, villageLayoutId, villageStyleForBiome } from './logic/layouts';
import { type Site, SiteIndex } from './logic/sites';
import { Npcs } from './npcs';
import { Scanner } from './scanner';
import { casinoStore, siteStore } from './store';

const SCAN_TICKS = 100;
const STEP_TICKS = 2;
const ENTER_TICKS = 20;
const RESPAWN_CHECK_TICKS = 200;

const NAME_KEY: Readonly<Record<CasinoKind, string>> = {
  village_casino: 'gui.burmaldaholic.worldgen.village_casino',
  piglin_parlor: 'gui.burmaldaholic.worldgen.piglin_parlor',
  high_roller: 'gui.burmaldaholic.worldgen.high_roller',
};

const casinoName = (kind: CasinoKind): Raw => t(NAME_KEY[kind]);

class Worldgen implements WorldgenApi {
  private readonly sites = new SiteIndex();
  private casinos: CasinoRecord[] = [];
  private readonly enterListeners: ((p: Player, c: CasinoInfo) => void)[] = [];
  private readonly inside = new Map<string, string>();
  private readonly missingSince = new Map<string, number>();
  private readonly scanner: Scanner;

  constructor(private readonly ctx: ModuleContext) {
    for (const s of siteStore.load()) this.sites.add(s);
    this.casinos = casinoStore.load();
    this.scanner = new Scanner({
      log: ctx.log,
      sites: this.sites,
      chance: (key) => ctx.config.num(key),
      decide: (site) => this.decide(site),
      built: (rec) => this.register(rec),
    });
  }

  // ---- API ----------------------------------------------------------------------------------

  private info(r: CasinoRecord): CasinoInfo {
    const l = layout(r.layout);
    const box = l ? casinoBox(r, l) : { min: r.origin, max: r.origin };
    return { id: r.id, kind: r.kind, layout: r.layout, dimensionId: r.dim, min: box.min, max: box.max };
  }

  casinoAt(dimensionId: string, location: Vector3): CasinoInfo | undefined {
    const r = findCasinoAt(this.casinos, dimensionId, location);
    return r && this.info(r);
  }

  tablePreset(dimensionId: string, location: Vector3): (typeof PRESETS)[keyof typeof PRESETS] | undefined {
    const r = findCasinoAt(this.casinos, dimensionId, location);
    const slot = r && tableSlotAt(r, location);
    return slot && PRESETS[slot.preset];
  }

  onEnter(listener: (player: Player, casino: CasinoInfo) => void): void {
    this.enterListeners.push(listener);
  }

  list(): readonly CasinoInfo[] {
    return this.casinos.map((r) => this.info(r));
  }

  // ---- bookkeeping --------------------------------------------------------------------------

  private decide(site: Site): void {
    this.sites.add(site);
    siteStore.save(this.sites.list());
  }

  private register(rec: CasinoRecord): void {
    this.casinos.push(rec);
    casinoStore.save(this.casinos);
  }

  private active(): boolean {
    return this.ctx.isCasinoEnabled() && this.ctx.config.bool('enabled');
  }

  // ---- loops --------------------------------------------------------------------------------

  start(): void {
    const safe = (name: string, fn: () => void) => () => {
      try {
        fn();
      } catch (e) {
        this.ctx.log.warn(`${name} failed`, e);
      }
    };
    system.runInterval(
      safe('scan', () => this.active() && this.scanner.scan()),
      SCAN_TICKS,
    );
    system.runInterval(
      safe('step', () => this.active() && this.scanner.step()),
      STEP_TICKS,
    );
    system.runInterval(
      safe('enter', () => this.ctx.isCasinoEnabled() && this.checkEntering()),
      ENTER_TICKS,
    );
    system.runInterval(
      safe('respawn', () => this.ctx.isCasinoEnabled() && this.checkNpcs()),
      RESPAWN_CHECK_TICKS,
    );
  }

  private checkEntering(): void {
    if (this.casinos.length === 0) return;
    for (const p of world.getPlayers()) {
      const r = findCasinoAt(this.casinos, p.dimension.id, p.location);
      const prev = this.inside.get(p.id);
      if (!r) {
        if (prev) this.inside.delete(p.id);
        continue;
      }
      if (prev === r.id) continue;
      this.inside.set(p.id, r.id);
      this.ctx.hud.title(p, t('msg.burmaldaholic.worldgen.entered', casinoName(r.kind)), t('msg.burmaldaholic.worldgen.entered_subtitle'));
      const info = this.info(r);
      for (const l of this.enterListeners) {
        try {
          l(p, info);
        } catch (e) {
          this.ctx.log.warn('onEnter listener failed', e);
        }
      }
    }
  }

  /** Respawn NPCs that have been missing (killed, /kill, lost) for worldgen.loanSharkRespawnTicks. */
  private checkNpcs(): void {
    const now = worldTick();
    const respawnTicks = this.ctx.config.int('worldgen.loanSharkRespawnTicks');
    for (const rec of this.casinos) {
      const l = layout(rec.layout);
      if (!l || l.npcs.length === 0) continue;
      let dim: Dimension;
      try {
        dim = world.getDimension(rec.dim);
      } catch {
        continue;
      }
      const box = casinoBox(rec, l);
      const center = { x: (box.min.x + box.max.x + 1) / 2, y: (box.min.y + box.max.y + 1) / 2, z: (box.min.z + box.max.z + 1) / 2 };
      const allLoaded = [box.min, box.max, { x: box.min.x, y: 0, z: box.max.z }, { x: box.max.x, y: 0, z: box.min.z }].every((p) => {
        try {
          return dim.isChunkLoaded(p);
        } catch {
          return false;
        }
      });
      if (!allLoaded) continue;
      const homes = new Set(
        dim
          .getEntities({ location: center, maxDistance: Math.max(l.size.x, l.size.z) + 16, tags: [NPC_TAG] })
          .map((e) => e.getDynamicProperty(HOME_PROP))
          .filter((h): h is string => typeof h === 'string'),
      );
      l.npcs.forEach((_, i) => {
        const home = npcHome(rec, i);
        const d = respawnDecision(homes.has(home), this.missingSince.get(home), now, respawnTicks);
        if (d.missingSince === undefined) this.missingSince.delete(home);
        else this.missingSince.set(home, d.missingSince);
        if (d.respawn) {
          try {
            spawnNpc(dim, rec, i, this.ctx.log);
          } catch (e) {
            this.ctx.log.warn(`respawn ${home} failed`, e);
          }
        }
      });
    }
  }

  // ---- manual building (ops) -----------------------------------------------------------------

  /** Build `kind` right in front (north) of the player, entrance facing them. */
  buildHere(p: Player, kind: CasinoKind): boolean {
    const dim = p.dimension;
    const layoutId = kind === 'village_casino' ? villageLayoutId(villageStyleForBiome(dim.getBiome(p.location).id)) : KIND_LAYOUT[kind];
    const l = layout(layoutId);
    if (!l) return false;
    const origin = {
      x: Math.floor(p.location.x) - Math.floor(l.size.x / 2),
      y: Math.floor(p.location.y) - 1,
      z: Math.floor(p.location.z) - 2 - l.size.z,
    };
    try {
      const rec = buildCasino(dim, layoutId, origin, this.ctx.log);
      this.register(rec);
      this.decide({ kind, dim: dim.id, x: origin.x, z: origin.z, state: 'placed' });
      p.sendMessage(t('msg.burmaldaholic.worldgen.built', casinoName(kind)));
      return true;
    } catch (e) {
      this.ctx.log.warn('manual build failed', e);
      p.sendMessage(t('msg.burmaldaholic.worldgen.build_failed'));
      return false;
    }
  }

  async adminBuild(op: Player): Promise<void> {
    const kinds: CasinoKind[] = ['village_casino', 'piglin_parlor', 'high_roller'];
    const form = new ActionFormData().title(t('gui.burmaldaholic.worldgen.admin.title')).body(t('gui.burmaldaholic.worldgen.admin.body'));
    for (const k of kinds) form.button(casinoName(k));
    form.button(t('gui.burmaldaholic.common.close'));
    const r = await showForm(op, form);
    const kind = r?.selection === undefined ? undefined : kinds[r.selection];
    if (kind) this.buildHere(op, kind);
  }
}

export const worldgenModule: CasinoModule = {
  id: 'worldgen',
  onWorldLoad(ctx) {
    const wg = new Worldgen(ctx);
    ctx.services.provide<WorldgenApi>(WORLDGEN_SERVICE, wg);
    new Npcs(ctx).subscribe();
    wg.start();
    ctx.admin.addAction({ id: 'worldgen.build', label: t('gui.burmaldaholic.worldgen.admin.build'), run: (op) => wg.adminBuild(op) });
    // `/scriptevent burmaldaholic:worldgen build village_casino|piglin_parlor|high_roller` (ops, testing)
    system.afterEvents.scriptEventReceive.subscribe(
      (e) => {
        const src = e.sourceEntity;
        if (e.id !== 'burmaldaholic:worldgen' || src?.typeId !== 'minecraft:player') return;
        const p = src as Player;
        if (!isOperator(p)) return;
        const [cmd, arg] = e.message.trim().split(/\s+/);
        if (cmd === 'build' && (arg === 'village_casino' || arg === 'piglin_parlor' || arg === 'high_roller')) wg.buildHere(p, arg);
      },
      { namespaces: ['burmaldaholic'] },
    );
  },
};
