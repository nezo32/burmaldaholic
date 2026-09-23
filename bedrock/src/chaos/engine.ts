/**
 * Chaos engine (GAME_DESIGN §13): triggers, cooldowns, deferral, the event effects and
 * Golden Hour. Decisions live in ./logic (pure); this file talks to the world.
 */
import {
  Difficulty,
  type Dimension,
  EntityComponentTypes,
  type EntityHealthComponent,
  ItemStack,
  type Player,
  type Vector3,
  WeatherType,
  system,
  world,
} from '@minecraft/server';
import {
  HudPriority,
  type ModuleContext,
  type Raw,
  NO_REWARD_TAG,
  chance,
  chipItemId,
  chips,
  duration,
  isFormOpen,
  lit,
  mathRng,
  t,
  unit,
  worldJson,
  worldTick,
} from '../core';
import { MULTIPLAYER_SERVICE } from '../multiplayer/api';
import type { ChaosApi, ChaosSource, ChaosTriggerOptions, ChaosTriggerResult } from './api';
import {
  BUFFS,
  CHAOS_EVENTS,
  CURSES,
  type ChaosEventId,
  type DifficultyName,
  type EffectSpec,
  type Weather,
  type Weights,
  chipShowerSplit,
  cooldownReady,
  diamondRainCount,
  isBigWin,
  mobWaveComposition,
  mobWaveSize,
  nextWeather,
  pickEvent,
  resolveEvent,
  rollEffect,
  rollRange,
  splitEven,
  sunsetDue,
} from './logic/events';
import { GH_WARN_TICKS, type GoldenHourState, INITIAL_GH, canStart, goldenBonus, startState } from './logic/golden-hour';
import { type PlayerSnapshot, type Verdict, evaluate, randomOffset } from './logic/safety';
import { dimName, dropSpot, findLanding, findSpawnSpot } from './world';

const LAST_PROP = 'burmaldaholic:chaos.last';
const GH_PROP = 'burmaldaholic:chaos.golden_hour';
const WEATHER_PROP = 'burmaldaholic:chaos.weather';
const SUNSET_PROP = 'burmaldaholic:chaos.sunset';
const UNTIL_PROP = 'burmaldaholic:chaos.until';
export const WAVE_TAG = 'burmaldaholic_chaos_wave';
const BOSSES = ['minecraft:wither', 'minecraft:warden', 'minecraft:ender_dragon'];
/** Table games where a teleport would break a round (§13.4 "during a card/table round"). */
const TABLE_GAMES = ['blackjack', 'poker', 'roulette', 'craps'];
const JACKPOT_RADIUS = 16;

interface Pending {
  event: ChaosEventId;
  opts: ChaosTriggerOptions;
  since: number;
}

type Outcome = 'ok' | 'failed';

export class ChaosEngine implements ChaosApi {
  private readonly pending = new Map<string, Pending>();
  private readonly respawnAt = new Map<string, number>();
  private readonly ambientAcc = new Map<string, number>();
  private prevTimeOfDay: number | undefined;
  private readonly rng = mathRng;

  constructor(private readonly ctx: ModuleContext) {}

  // ---- config ------------------------------------------------------------------------------

  isEnabled(): boolean {
    return this.ctx.isCasinoEnabled() && this.ctx.config.bool('chaos.enabled');
  }

  private eventEnabled(e: ChaosEventId): boolean {
    if (e === 'golden_hour' && !this.ctx.config.bool('chaos.goldenHour.enabled')) return false;
    return this.ctx.config.bool(`chaos.event.${e}.enabled`);
  }

  /** Ambient weights with disabled events zeroed. */
  private weights(): Weights {
    const w: Weights = {};
    for (const e of CHAOS_EVENTS) w[e] = this.eventEnabled(e) ? this.ctx.config.int(`chaos.weight.${e}`) : 0;
    return w;
  }

  // ---- public API --------------------------------------------------------------------------

  trigger(player: Player, event: ChaosEventId, options: ChaosTriggerOptions = {}): ChaosTriggerResult {
    try {
      if (!this.isEnabled() || !player.isValid) return 'disabled';
      if (event === 'golden_hour') return this.startGoldenHour(options.source === 'slots' ? player : undefined, options.source);
      if (!this.eventEnabled(event)) return 'disabled';
      const last = player.getDynamicProperty(LAST_PROP);
      if (!options.ignoreCooldown && !cooldownReady(typeof last === 'number' ? last : undefined, worldTick(), this.ctx.config.int('chaos.playerCooldownTicks'))) {
        return 'cooldown';
      }
      return this.attempt(player, event, options);
    } catch (err) {
      this.ctx.log.error(`trigger ${event} failed`, err);
      return 'skipped';
    }
  }

  jackpot(winner: Player): void {
    if (!this.isEnabled()) return;
    this.trigger(winner, 'diamond_rain', { source: 'jackpot', ignoreCooldown: true });
    for (const p of winner.dimension.getPlayers({ location: winner.location, maxDistance: JACKPOT_RADIUS })) {
      if (p.id !== winner.id) this.trigger(p, 'chip_shower', { source: 'jackpot', ignoreCooldown: true });
    }
  }

  canStartGoldenHour(): boolean {
    return this.isEnabled() && this.eventEnabled('golden_hour') && !this.ctx.goldenHour.isActive() && canStart(this.ghState(), worldTick());
  }

  startGoldenHour(by?: Player, source: ChaosSource = 'admin'): ChaosTriggerResult {
    if (!this.isEnabled() || !this.eventEnabled('golden_hour')) return 'disabled';
    const now = worldTick();
    const st = this.ghState();
    if (this.ctx.goldenHour.isActive() || !canStart(st, now)) return 'cooldown';
    const cfg = this.ctx.config;
    const dur = cfg.int('chaos.goldenHour.durationTicks');
    const next = startState(st, now, dur, cfg.int('chaos.goldenHour.cooldownTicks'));
    this.saveGh(next);
    this.ctx.goldenHour.start(dur);
    const m = cfg.num('chaos.goldenHour.multiplier');
    for (const p of world.getAllPlayers()) {
      this.ctx.hud.title(p, t('msg.burmaldaholic.chaos.golden_hour.title'), t('msg.burmaldaholic.chaos.golden_hour.subtitle', m, duration(dur)), 10, 60, 20);
      this.sound(p, 'block.bell.hit');
    }
    if (by) world.sendMessage(t('msg.burmaldaholic.chaos.golden_hour.start_by', by.name));
    world.sendMessage(t('msg.burmaldaholic.chaos.golden_hour.start', m, duration(dur, true)));
    this.ctx.log.info(`golden hour #${next.id} started (${source})`);
    return 'started';
  }

  // ---- trigger pipeline --------------------------------------------------------------------

  private attempt(player: Player, requested: ChaosEventId, opts: ChaosTriggerOptions): ChaosTriggerResult {
    const snap = this.snapshot(player);
    let event: ChaosEventId | undefined = requested;
    let v: Verdict = evaluate(event, snap, this.safetyCfg());
    if (v.action === 'skip' && (v.reason === 'peaceful' || v.reason === 'dimension')) {
      event = resolveEvent(this.rng, requested, { peaceful: snap.peaceful, overworld: snap.dimension === 'overworld' }, this.weights());
      if (!event) return 'skipped';
      v = evaluate(event, snap, this.safetyCfg());
    }
    if (v.action === 'defer') {
      if (this.ctx.config.int('chaos.deferMaxTicks') <= 0) return 'skipped';
      if (!this.pending.has(player.id)) {
        this.pending.set(player.id, { event, opts, since: system.currentTick });
        this.ctx.hud.actionbar(player, 'chaos.deferred', t('msg.burmaldaholic.chaos.deferred'), HudPriority.alert, 60);
      }
      return 'deferred';
    }
    if (v.action === 'skip') {
      return 'skipped';
    }
    const r = this.run(player, event, opts);
    if (r !== 'ok') return 'skipped';
    player.setDynamicProperty(LAST_PROP, worldTick());
    this.ctx.log.info(`${event} for ${player.name} (${opts.source ?? 'api'})`);
    return 'started';
  }

  private safetyCfg() {
    return { respawnGraceTicks: this.ctx.config.int('chaos.respawnGraceTicks') };
  }

  private snapshot(p: Player): PlayerSnapshot {
    const health = p.getComponent(EntityComponentTypes.Health) as EntityHealthComponent | undefined;
    const respawn = this.respawnAt.get(p.id);
    let riding = false;
    try {
      riding = !!p.getComponent(EntityComponentTypes.Riding);
    } catch {
      /* not riding */
    }
    let falling = false;
    try {
      falling = p.isFalling && p.getVelocity().y < -0.5;
    } catch {
      /* ignore */
    }
    const session = this.ctx.tables.sessionOf(p);
    return {
      gameMode: String(p.getGameMode()),
      dead: !!health && health.currentValue <= 0,
      ticksSinceRespawn: respawn === undefined ? undefined : system.currentTick - respawn,
      sleeping: p.isSleeping,
      gliding: p.isGliding,
      riding,
      falling,
      formOpen: isFormOpen(p),
      inRound: this.ctx.wagers.openFor(p).length > 0 || (!!session && TABLE_GAMES.includes(session.table.game)),
      nearBoss: this.nearBoss(p),
      inClaim: this.inClaim(p),
      peaceful: world.getDifficulty() === Difficulty.Peaceful,
      dimension: dimName(p.dimension),
    };
  }

  private nearBoss(p: Player): boolean {
    const r = this.ctx.config.int('chaos.bossSafeRadius');
    if (r <= 0) return false;
    return BOSSES.some((type) => p.dimension.getEntities({ type, location: p.location, maxDistance: r }).length > 0);
  }

  private inClaim(p: Player): boolean {
    const mp = this.ctx.services.get<{ isInsideClaim?(dimension: Dimension, location: Vector3): boolean }>(MULTIPLAYER_SERVICE);
    try {
      return typeof mp?.isInsideClaim === 'function' ? mp.isInsideClaim(p.dimension, p.location) : false;
    } catch {
      return false;
    }
  }

  private run(p: Player, e: ChaosEventId, opts: ChaosTriggerOptions): Outcome {
    switch (e) {
      case 'chip_shower':
        return this.chipShower(p);
      case 'lucky_buff':
        return this.effect(p, BUFFS, 'chaos.buff', 'msg.burmaldaholic.chaos.lucky_buff', opts.source === 'big_win');
      case 'curse':
        return this.effect(p, CURSES, 'chaos.curse', 'msg.burmaldaholic.chaos.curse', false);
      case 'diamond_rain':
        return this.diamondRain(p);
      case 'xp_fountain':
        return this.xpFountain(p);
      case 'mob_wave':
        return this.mobWave(p);
      case 'random_teleport':
        return this.teleport(p);
      case 'weather_change':
        return this.weather(p);
      case 'golden_hour':
        return this.startGoldenHour(undefined, opts.source) === 'started' ? 'ok' : 'failed';
    }
  }

  // ---- events ------------------------------------------------------------------------------

  private announce(p: Player, e: ChaosEventId, title: Raw, subtitle?: Raw): void {
    this.ctx.hud.title(p, title, subtitle, 5, 50, 15);
    this.ctx.hud.actionbar(p, 'chaos.event', t(`gui.burmaldaholic.chaos.event.${e}`), HudPriority.alert, 60);
  }

  private sound(p: Player, id: string): void {
    try {
      p.playSound(id);
    } catch {
      /* unknown sound on this client */
    }
  }

  private particle(dim: Dimension, id: string, at: Vector3): void {
    try {
      dim.spawnParticle(id, at);
    } catch {
      /* chunk unloaded */
    }
  }

  private spawnItem(dim: Dimension, typeId: string, count: number, at: Vector3): void {
    let left = count;
    while (left > 0) {
      const n = Math.min(64, left);
      left -= n;
      try {
        dim.spawnItem(new ItemStack(typeId, n), at);
      } catch (err) {
        this.ctx.log.warn(`spawnItem ${typeId} failed`, err);
      }
    }
  }

  private chipShower(p: Player): Outcome {
    const cfg = this.ctx.config;
    const amount = rollRange(this.rng, cfg.int('chaos.chipShower.min'), cfg.int('chaos.chipShower.max'));
    if (amount <= 0) return 'failed';
    const { fives, ones } = chipShowerSplit(this.rng, amount);
    const dim = p.dimension;
    const origin = p.location;
    // pop out in small stacks around the player (radius 2)
    const piles: [string, number][] = [];
    for (const n of splitEven(fives, Math.min(6, Math.max(1, Math.ceil(fives / 4))))) if (n > 0) piles.push([chipItemId(5), n]);
    for (const n of splitEven(ones, Math.min(6, Math.max(1, Math.ceil(ones / 4))))) if (n > 0) piles.push([chipItemId(1), n]);
    for (const [id, n] of piles) {
      const o = randomOffset(this.rng.next(), this.rng.next(), 0.5, 2);
      this.spawnItem(dim, id, n, dropSpot(dim, origin, o.dx, o.dz, 1));
    }
    this.particle(dim, 'minecraft:totem_particle', { x: origin.x, y: origin.y + 1, z: origin.z });
    this.sound(p, 'random.levelup');
    this.announce(p, 'chip_shower', t('msg.burmaldaholic.chaos.chip_shower.title'), t('msg.burmaldaholic.chaos.chip_shower.subtitle'));
    p.sendMessage(t('msg.burmaldaholic.chaos.chip_shower.chat', chips(amount)));
    return 'ok';
  }

  private effect(p: Player, pool: readonly EffectSpec[], cfgPrefix: string, keyPrefix: string, bigWin: boolean): Outcome {
    const cfg = this.ctx.config;
    const { effect, ticks } = rollEffect(this.rng, pool, cfg.int(`${cfgPrefix}.minTicks`), cfg.int(`${cfgPrefix}.maxTicks`));
    try {
      p.addEffect(effect.id, ticks, { amplifier: effect.amplifier, showParticles: true });
    } catch (err) {
      this.ctx.log.warn(`addEffect ${effect.id} failed`, err);
      return 'failed';
    }
    const name = t(`gui.burmaldaholic.chaos.effect.${effect.name}`);
    this.announce(p, pool === BUFFS ? 'lucky_buff' : 'curse', t(`${keyPrefix}.title`), t(`${keyPrefix}.subtitle`, name, duration(ticks, true)));
    this.sound(p, pool === BUFFS ? 'random.levelup' : 'mob.witch.ambient');
    if (bigWin) p.sendMessage(t('msg.burmaldaholic.chaos.big_win_buff', name));
    return 'ok';
  }

  private diamondRain(p: Player): Outcome {
    const cfg = this.ctx.config;
    const hard = world.isHardcore || world.getDifficulty() === Difficulty.Hard;
    const count = diamondRainCount(this.rng, cfg.int('chaos.diamondRain.min'), cfg.int('chaos.diamondRain.max'), hard);
    if (count <= 0) return 'failed';
    const RAIN_TICKS = 100;
    for (let i = 0; i < count; i++) {
      const delay = Math.floor((i * RAIN_TICKS) / count);
      system.runTimeout(() => {
        if (!p.isValid) return;
        const dim = p.dimension;
        const o = randomOffset(this.rng.next(), this.rng.next(), 0, 3);
        const at = dropSpot(dim, p.location, o.dx, o.dz, 6);
        this.spawnItem(dim, 'minecraft:diamond', 1, at);
        this.particle(dim, 'minecraft:villager_happy', at);
      }, Math.max(1, delay));
    }
    // "rain" particles around the player for the duration
    for (let k = 0; k < RAIN_TICKS; k += 10) {
      system.runTimeout(() => {
        if (!p.isValid) return;
        for (let j = 0; j < 4; j++) {
          const o = randomOffset(this.rng.next(), this.rng.next(), 0, 3);
          this.particle(p.dimension, 'minecraft:villager_happy', { x: p.location.x + o.dx, y: p.location.y + 3 + this.rng.next() * 3, z: p.location.z + o.dz });
        }
      }, k + 1);
    }
    this.sound(p, 'random.orb');
    this.announce(p, 'diamond_rain', t('msg.burmaldaholic.chaos.diamond_rain.title'), t('msg.burmaldaholic.chaos.diamond_rain.subtitle'));
    return 'ok';
  }

  private xpFountain(p: Player): Outcome {
    const cfg = this.ctx.config;
    const total = rollRange(this.rng, cfg.int('chaos.xpFountain.min'), cfg.int('chaos.xpFountain.max'));
    if (total <= 0) return 'failed';
    const bursts = splitEven(total, 6);
    bursts.forEach((xp, i) => {
      system.runTimeout(() => {
        if (!p.isValid || xp <= 0) return;
        p.addExperience(xp);
        this.particle(p.dimension, 'minecraft:totem_particle', { x: p.location.x, y: p.location.y + 1.5, z: p.location.z });
        this.sound(p, 'random.orb');
      }, 1 + i * 10);
    });
    this.announce(p, 'xp_fountain', t('msg.burmaldaholic.chaos.xp_fountain.title'), t('msg.burmaldaholic.chaos.xp_fountain.subtitle'));
    return 'ok';
  }

  private mobWave(p: Player): Outcome {
    const cfg = this.ctx.config;
    const diff = String(world.getDifficulty()) as DifficultyName;
    const n = mobWaveSize(diff, world.isHardcore, { easy: cfg.int('chaos.mobWave.easy'), normal: cfg.int('chaos.mobWave.normal'), hard: cfg.int('chaos.mobWave.hard') });
    if (n <= 0) return 'failed';
    const dim = p.dimension;
    const minD = cfg.int('chaos.mobWave.minDistance');
    const maxD = cfg.int('chaos.mobWave.maxDistance');
    const spots: Vector3[] = [];
    for (let i = 0; i < n; i++) {
      let spot: Vector3 | undefined;
      for (let a = 0; a < 16 && !spot; a++) {
        const o = randomOffset(this.rng.next(), this.rng.next(), minD, maxD);
        spot = findSpawnSpot(dim, Math.floor(p.location.x) + o.dx, Math.floor(p.location.z) + o.dz, p.location.y);
      }
      if (!spot) return 'failed'; // fewer than N valid spots: skip the whole wave (§13.4)
      spots.push(spot);
    }
    const dname = dimName(dim);
    const types = mobWaveComposition(this.rng, dname === 'nether' || dname === 'the_end' ? dname : 'overworld', n);
    const until = worldTick() + cfg.int('chaos.mobWave.despawnTicks');
    types.forEach((type, i) => {
      try {
        const spawnEvent = type === 'minecraft:magma_cube' ? 'spawn_medium' : undefined;
        let mob;
        try {
          mob = dim.spawnEntity(type, spots[i] as Vector3, spawnEvent ? { spawnEvent } : undefined);
        } catch {
          mob = dim.spawnEntity(type, spots[i] as Vector3);
        }
        mob.addTag(WAVE_TAG);
        mob.addTag(NO_REWARD_TAG);
        mob.setDynamicProperty(UNTIL_PROP, until);
        this.particle(dim, 'minecraft:large_explosion', spots[i] as Vector3);
      } catch (err) {
        this.ctx.log.warn(`wave spawn ${type} failed`, err);
      }
    });
    this.sound(p, 'mob.evocation_illager.prepare_summon');
    this.announce(p, 'mob_wave', t('msg.burmaldaholic.chaos.mob_wave.title'), t('msg.burmaldaholic.chaos.mob_wave.subtitle'));
    p.sendMessage(t('msg.burmaldaholic.chaos.mob_wave.chat'));
    return 'ok';
  }

  private teleport(p: Player): Outcome {
    const cfg = this.ctx.config;
    const dim = p.dimension;
    const from = p.location;
    const attempts = cfg.int('chaos.teleport.attempts');
    for (let a = 0; a < attempts; a++) {
      const o = randomOffset(this.rng.next(), this.rng.next(), cfg.int('chaos.teleport.minDistance'), cfg.int('chaos.teleport.maxDistance'));
      const x = Math.floor(from.x) + o.dx;
      const z = Math.floor(from.z) + o.dz;
      const y = findLanding(dim, x, z, from.y);
      if (y === undefined) continue;
      const to = { x: x + 0.5, y: y + 1, z: z + 0.5 };
      try {
        p.teleport(to, { dimension: dim, keepVelocity: false });
      } catch (err) {
        this.ctx.log.warn('teleport failed', err);
        return 'failed';
      }
      p.addEffect('resistance', 60, { amplifier: 4, showParticles: false });
      this.sound(p, 'mob.endermen.portal');
      const dist = Math.round(Math.hypot(to.x - from.x, to.z - from.z));
      this.announce(p, 'random_teleport', t('msg.burmaldaholic.chaos.random_teleport.title'), t('msg.burmaldaholic.chaos.random_teleport.subtitle', unit('block', dist)));
      p.sendMessage(t('msg.burmaldaholic.chaos.random_teleport.chat', lit([x, y + 1, z].join(', '))));
      return 'ok';
    }
    return 'failed'; // no safe spot: skip silently (§13.4)
  }

  private weather(p: Player): Outcome {
    const cur = worldJson.read<Weather>(WEATHER_PROP, 'Clear');
    const next = nextWeather(cur === 'Rain' || cur === 'Thunder' ? cur : 'Clear');
    const overworld = world.getDimension('overworld');
    try {
      overworld.setWeather(next === 'Clear' ? WeatherType.Clear : next === 'Rain' ? WeatherType.Rain : WeatherType.Thunder, this.ctx.config.int('chaos.weather.durationTicks'));
    } catch (err) {
      this.ctx.log.warn('setWeather failed', err);
      return 'failed';
    }
    worldJson.write(WEATHER_PROP, next);
    const line = t(`msg.burmaldaholic.chaos.weather.${next.toLowerCase()}`);
    this.announce(p, 'weather_change', t('msg.burmaldaholic.chaos.weather.title'), line);
    for (const o of overworld.getPlayers()) if (o.id !== p.id) o.sendMessage(line);
    return 'ok';
  }

  // ---- Golden Hour -------------------------------------------------------------------------

  private ghState(): GoldenHourState {
    const s = worldJson.read<Partial<GoldenHourState>>(GH_PROP, {});
    return { ...INITIAL_GH, ...s, paid: { ...(s.paid ?? {}) } };
  }

  private saveGh(s: GoldenHourState): void {
    worldJson.write(GH_PROP, s);
  }

  /** §13.3: bonus = floor(netWin × (m − 1)) on house-banked wins, capped per player. */
  private goldenBonus(p: Player, net: number): void {
    if (!this.ctx.goldenHour.isActive()) return;
    const cfg = this.ctx.config;
    const st = this.ghState();
    const cap = cfg.int('chaos.goldenHour.bonusCap');
    const { bonus, capReached } = goldenBonus(net, cfg.num('chaos.goldenHour.multiplier'), st.paid[p.id] ?? 0, cap);
    if (bonus <= 0) return;
    const got = this.ctx.economy.earn(p, bonus, t('msg.burmaldaholic.core.source.golden_hour'), 'chaos.golden_hour');
    st.paid[p.id] = (st.paid[p.id] ?? 0) + bonus;
    this.saveGh(st);
    if (got > 0) p.sendMessage(t('msg.burmaldaholic.chaos.golden_hour.bonus', chips(got)));
    if (capReached) p.sendMessage(t('msg.burmaldaholic.chaos.golden_hour.cap', chips(cap)));
  }

  /** Every second: "ending soon" and "over" announcements. */
  private tickGoldenHour(): void {
    const st = this.ghState();
    if (st.start < 0 || st.ended) return;
    const remaining = this.ctx.goldenHour.remainingTicks();
    if (remaining <= 0) {
      st.ended = true;
      this.saveGh(st);
      world.sendMessage(t('msg.burmaldaholic.chaos.golden_hour.end'));
      for (const p of world.getAllPlayers()) this.sound(p, 'block.bell.hit');
      return;
    }
    if (!st.warned && remaining <= GH_WARN_TICKS) {
      st.warned = true;
      this.saveGh(st);
      world.sendMessage(t('msg.burmaldaholic.chaos.golden_hour.ending', duration(remaining, true)));
    }
  }

  // ---- schedulers --------------------------------------------------------------------------

  /** Wire events and intervals (called once from onWorldLoad). */
  start(): void {
    const ctx = this.ctx;
    world.afterEvents.playerSpawn.subscribe((e) => {
      if (!e.initialSpawn) this.respawnAt.set(e.player.id, system.currentTick);
    });
    world.afterEvents.playerLeave.subscribe((e) => {
      this.pending.delete(e.playerId);
      this.respawnAt.delete(e.playerId);
      this.ambientAcc.delete(e.playerId);
    });
    world.afterEvents.weatherChange.subscribe((e) => {
      if (e.dimension === 'minecraft:overworld' || e.dimension === 'overworld') worldJson.write(WEATHER_PROP, String(e.newWeather));
    });

    // Big win buff (§13.1.3) and Golden Hour bonus (§13.3).
    ctx.wagers.onSettled((e) => {
      if (!this.isEnabled() || !e.player.isValid) return;
      if (e.houseBanked && e.net > 0) {
        try {
          this.goldenBonus(e.player, e.net);
        } catch (err) {
          ctx.log.error('golden hour bonus failed', err);
        }
      }
      const cfg = ctx.config;
      if (isBigWin(e.staked, e.net, cfg.int('chaos.bigWin.multiple'), cfg.int('chaos.bigWin.minChips')) && chance(this.rng, cfg.num('chaos.bigWin.buffChance'))) {
        const p = e.player;
        // next tick: a slot special triggered right after settle takes precedence
        system.run(() => {
          if (p.isValid) this.trigger(p, 'lucky_buff', { source: 'big_win' });
        });
      }
    });

    // Deferred events: run once the casino form is closed (max chaos.deferMaxTicks).
    system.runInterval(
      ctx.guard(() => {
        const now = system.currentTick;
        for (const [id, pend] of [...this.pending]) {
          const p = world.getEntity(id) as Player | undefined;
          if (!p?.isValid || !this.isEnabled() || now - pend.since > ctx.config.int('chaos.deferMaxTicks')) {
            this.pending.delete(id);
            continue;
          }
          if (isFormOpen(p)) continue;
          this.pending.delete(id);
          try {
            this.attempt(p, pend.event, pend.opts);
          } catch (err) {
            ctx.log.error('deferred chaos failed', err);
          }
        }
      }),
      10,
    );

    // Ambient roll per player + sunset roll + Golden Hour announcements (every second).
    system.runInterval(
      ctx.guard(() => {
        this.tickGoldenHour();
        if (!this.isEnabled()) return;
        const interval = ctx.config.int('chaos.ambientIntervalTicks');
        const pAmbient = ctx.config.num('chaos.ambientChance');
        for (const p of world.getAllPlayers()) {
          const acc = (this.ambientAcc.get(p.id) ?? 0) + 20;
          if (acc < interval) {
            this.ambientAcc.set(p.id, acc);
            continue;
          }
          this.ambientAcc.set(p.id, 0);
          if (!chance(this.rng, pAmbient)) continue;
          const ev = pickEvent(this.rng, this.weights());
          if (ev) this.trigger(p, ev, { source: 'ambient' });
        }
        this.sunset();
      }),
      20,
    );

    // Mob-wave despawn (entities carry their own expiry, so reloaded chunks are handled too).
    system.runInterval(() => {
      const now = worldTick();
      for (const dim of ['overworld', 'nether', 'the_end']) {
        try {
          for (const e of world.getDimension(dim).getEntities({ tags: [WAVE_TAG] })) {
            const until = e.getDynamicProperty(UNTIL_PROP);
            if (typeof until !== 'number' || now >= until || now < until - 10 * 72000) e.remove();
          }
        } catch (err) {
          ctx.log.warn('wave despawn sweep failed', err);
        }
      }
    }, 100);
  }

  private sunset(): void {
    const tod = world.getTimeOfDay();
    const day = Math.floor(worldTick() / 24000);
    const last = worldJson.read<{ day?: number }>(SUNSET_PROP, {}).day;
    const due = sunsetDue(this.prevTimeOfDay, tod, day, last);
    this.prevTimeOfDay = tod;
    if (!due) return;
    worldJson.write(SUNSET_PROP, { day });
    if (world.getAllPlayers().length === 0) return;
    if (chance(this.rng, this.ctx.config.num('chaos.goldenHour.sunsetChance'))) this.startGoldenHour(undefined, 'sunset');
  }
}
