/**
 * Last Chance (GAME_DESIGN §15): a coin flip with Death on a lethal hit.
 *
 * Edition approach (Bedrock, stable @minecraft/server 2.8.0): `world.beforeEvents.entityHurt`
 * is stable and cancellable, so we intercept the lethal hit like a totem does instead of the
 * snapshot/respawn fallback described in §15.1. The player never dies on success, so vanilla
 * death, drops, keepInventory and Hardcore are never touched:
 *  - hit with damage >= current health -> rules decide (logic/rules.ts) -> flip;
 *  - heads: cancel the hit, grace-cancel further hits for 2 ticks, next tick revive at
 *    ceil(max/2) with Resistance V 60 t + Regeneration I 100 t, fire out, fee taken;
 *  - tails / not applicable: nothing is changed and the vanilla death proceeds. The failure
 *    messages and the cooldown are applied from `entityDie` only if the player really died
 *    (armor/absorption can make a "lethal-looking" hit survivable; then nothing happened).
 * The before-event damage is the incoming amount; if the engine reports it before armor, a
 * save can trigger on a hit that armor would have survived (the player still pays). Verify in game.
 *
 * Hardcore High Stakes scar: script cannot change max_health (see core heart wagers), so the
 * permanent -N HP is enforced by capping heals (`beforeEvents.entityHeal`) and clamping health.
 */
import {
  Difficulty,
  EntityComponentTypes,
  type EntityEquippableComponent,
  type EntityHealthComponent,
  EquipmentSlot,
  type Player,
  system,
  world,
} from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import {
  type CasinoModule,
  type ConfigStore,
  HudPriority,
  type ModuleContext,
  type Raw,
  SOUL_WAGER_TAG,
  chipValueInInventory,
  chips,
  duration,
  lines,
  mathRng,
  pluralKey,
  readJson,
  showForm,
  t,
  takeAllChips,
  unit,
  worldTick,
  writeJson,
} from '../core';
import type { LastchanceApi, LastchanceSavedEvent, LastchanceStatus } from './api';
import { LASTCHANCE_SERVICE } from './api';
import {
  type HardcoreMode,
  type LcConfig,
  type LcDifficulty,
  type LcMode,
  type LcRecord,
  cappedHealing,
  chanceFor,
  cooldownFor,
  cooldownRemaining,
  decide,
  flip,
  isLethal,
  modeFor,
  parseRecord,
  planSuccess,
  readyDue,
  scarredMax,
} from './logic';

const STATE_PROP = 'burmaldaholic:lastchance.state';
const TOTEM_ID = 'minecraft:totem_of_undying';
const SOUND_ID = 'burmaldaholic.last_chance';
const PARTICLE_ID = 'minecraft:totem_particle';
const PLAYER_ID = 'minecraft:player';
/** Ticks a pending tails result waits for the matching death event. */
const PENDING_TTL = 2;

function readConfig(c: ConfigStore): LcConfig {
  return {
    enabled: c.bool('enabled'),
    chance: { easy: c.num('chance.easy'), normal: c.num('chance.normal'), hard: c.num('chance.hard') },
    cooldownTicks: c.int('cooldownTicks'),
    costPercent: c.int('costPercent'),
    hardcoreMode: (c.str('hardcoreMode') === 'HIGH_STAKES' ? 'HIGH_STAKES' : 'DISABLED') as HardcoreMode,
    hardcore: {
      chance: c.num('hardcore.chance'),
      cooldownTicks: c.int('hardcore.cooldownTicks'),
      minStake: c.int('hardcore.minStake'),
      heartCost: c.int('hardcore.heartCost'),
      minMaxHealth: c.int('hardcore.minMaxHealth'),
    },
  };
}

function difficulty(): LcDifficulty {
  switch (world.getDifficulty()) {
    case Difficulty.Peaceful:
      return 'peaceful';
    case Difficulty.Easy:
      return 'easy';
    case Difficulty.Hard:
      return 'hard';
    default:
      return 'normal';
  }
}

const healthOf = (p: Player): EntityHealthComponent | undefined => p.getComponent(EntityComponentTypes.Health) as EntityHealthComponent | undefined;

function holdsTotem(p: Player): boolean {
  const eq = p.getComponent(EntityComponentTypes.Equippable) as EntityEquippableComponent | undefined;
  if (!eq) return false;
  return eq.getEquipment(EquipmentSlot.Mainhand)?.typeId === TOTEM_ID || eq.getEquipment(EquipmentSlot.Offhand)?.typeId === TOTEM_ID;
}

function typeIdOf(e: { typeId: string } | undefined): string | undefined {
  try {
    return e?.typeId;
  } catch {
    return undefined;
  }
}

const readRecord = (p: Player): LcRecord => parseRecord(readJson<unknown>(p, STATE_PROP, undefined));
const writeRecord = (p: Player, rec: LcRecord): void => writeJson(p, STATE_PROP, Object.keys(rec).length ? rec : undefined);

/** "-N HP" as hearts: whole hearts use the plural unit, half hearts the "few" form (1.5 hearts / 1,5 сердца). */
function heartsText(hp: number): Raw {
  return hp % 2 === 0 ? unit('heart', hp / 2) : t(pluralKey('unit.burmaldaholic.heart', 2), (hp / 2).toFixed(1));
}

interface Pending {
  kind: 'fail' | 'not_eligible';
  tick: number;
  mode: LcMode;
}

export const lastchanceModule: CasinoModule = {
  id: 'lastchance',

  onWorldLoad(ctx: ModuleContext) {
    const cfg = (): LcConfig => readConfig(ctx.config);
    /** player id -> last tick in which every hit is cancelled (between heads and the revive). */
    const saving = new Map<string, number>();
    /** player id -> a lost flip / refused High Stakes waiting for the death event. */
    const pending = new Map<string, Pending>();
    /** player id -> scar HP (cache of the persisted record, read in restricted mode). */
    const scars = new Map<string, number>();
    const listeners: ((e: LastchanceSavedEvent) => void)[] = [];

    const scarOf = (p: Player): number => {
      let s = scars.get(p.id);
      if (s === undefined) scars.set(p.id, (s = readRecord(p).scarHp ?? 0));
      return s;
    };

    const clampScar = (p: Player): void => {
      const scar = scarOf(p);
      if (!scar) return;
      const h = healthOf(p);
      if (!h || h.currentValue <= 0) return;
      const cap = scarredMax(h.effectiveMax, scar);
      if (h.currentValue > cap) h.setCurrentValue(cap);
    };

    const startCooldown = (p: Player, mode: LcMode): void => {
      const rec = readRecord(p);
      rec.usedAt = worldTick();
      delete rec.notified;
      writeRecord(p, rec);
      const cd = cooldownFor(mode, cfg());
      // A Hardcore death is final (spectator): no point announcing a recharge.
      if (cd > 0 && !world.isHardcore) p.sendMessage(t('msg.burmaldaholic.lastchance.cooldown_started', duration(cd)));
    };

    // ---- the lethal hit ------------------------------------------------------------------
    world.beforeEvents.entityHurt.subscribe(
      ctx.guard((e) => {
        if (e.hurtEntity.typeId !== PLAYER_ID) return;
        const p = e.hurtEntity as Player;
        const now = system.currentTick;
        if ((saving.get(p.id) ?? -1) >= now) {
          e.cancel = true; // heads already called this tick: nothing else may kill them
          return;
        }
        const h = healthOf(p);
        if (!h || !isLethal(e.damage, h.currentValue)) return;
        const pend = pending.get(p.id);
        if (pend && now - pend.tick <= 1) return; // already flipped (and lost) this tick
        const c = cfg();
        const rec = readRecord(p);
        const src = e.damageSource;
        const d = decide(
          {
            damage: e.damage,
            health: h.currentValue,
            cause: src.cause,
            attackerTypes: [typeIdOf(src.damagingEntity), typeIdOf(src.damagingProjectile)].filter((x): x is string => !!x),
          },
          {
            balance: ctx.economy.balance(p),
            chipsCarried: chipValueInInventory(p),
            baseMaxHealth: h.effectiveMax,
            scarHp: rec.scarHp ?? 0,
            holdsTotem: holdsTotem(p),
            soulWager: p.hasTag(SOUL_WAGER_TAG),
            lastUsedAt: rec.usedAt,
          },
          { casinoOn: ctx.isCasinoEnabled(), hardcore: world.isHardcore, difficulty: difficulty(), now: worldTick() },
          c,
        );
        if (d.kind === 'skip') {
          if (d.reason === 'not_eligible') pending.set(p.id, { kind: 'not_eligible', tick: now, mode: 'high_stakes' });
          return;
        }
        // Chaos / VIP may tweak the standard coin through core odds; High Stakes stays fixed (§15.3).
        const p0 = d.chance;
        let chance = p0;
        if (d.mode === 'standard' && p0 > 0 && p0 < 1) {
          try {
            chance = ctx.odds.probability(p.id, 'last_chance', p0);
          } catch (err) {
            ctx.log.warn(`odds modifier failed, using base chance: ${String(err)}`);
          }
        }
        if (flip(mathRng, chance)) {
          e.cancel = true;
          saving.set(p.id, now + PENDING_TTL);
          system.run(() => revive(p, d.mode));
        } else {
          pending.set(p.id, { kind: 'fail', tick: now, mode: d.mode });
        }
      }),
    );

    function revive(p: Player, mode: LcMode): void {
      if (!p.isValid) return;
      const c = cfg();
      const h = healthOf(p);
      const rec = readRecord(p);
      const balance = ctx.economy.balance(p);
      const plan = planSuccess(mode, c, balance, h?.effectiveMax ?? 20, rec.scarHp ?? 0);
      let paid = 0;
      if (plan.destroyChips) paid += takeAllChips(p);
      const fee = Math.min(plan.fee, Math.max(0, balance));
      if (fee > 0 && ctx.economy.debit(p, fee, `lastchance.${mode}`)) paid += fee;

      if (plan.addScarHp > 0) rec.scarHp = (rec.scarHp ?? 0) + plan.addScarHp;
      rec.usedAt = worldTick();
      delete rec.notified;
      writeRecord(p, rec);
      scars.set(p.id, rec.scarHp ?? 0);

      h?.setCurrentValue(plan.reviveHealth);
      p.extinguishFire(false);
      p.addEffect('resistance', 60, { amplifier: 4, showParticles: false });
      p.addEffect('regeneration', 100, { amplifier: 0 });
      try {
        const at = { x: p.location.x, y: p.location.y + 1, z: p.location.z };
        p.dimension.spawnParticle(PARTICLE_ID, at);
        p.dimension.playSound(SOUND_ID, p.location);
      } catch (err) {
        ctx.log.warn(`effects failed: ${String(err)}`);
      }

      ctx.hud.actionbar(p, 'lastchance.flip', t('msg.burmaldaholic.lastchance.flip_title'), HudPriority.critical, 60);
      if (mode === 'high_stakes') {
        ctx.hud.title(p, t('msg.burmaldaholic.lastchance.heads_title'), t('msg.burmaldaholic.lastchance.hardcore.title'), 5, 50, 15);
        p.sendMessage(t('msg.burmaldaholic.lastchance.hardcore.success'));
        p.sendMessage(t('msg.burmaldaholic.lastchance.hardcore.scar', heartsText(plan.addScarHp)));
        world.sendMessage(t('msg.burmaldaholic.lastchance.hardcore.broadcast', p.name));
      } else {
        ctx.hud.title(p, t('msg.burmaldaholic.lastchance.heads_title'), t('msg.burmaldaholic.lastchance.heads_subtitle'), 5, 50, 15);
        p.sendMessage(t('msg.burmaldaholic.lastchance.success', chips(fee)));
        world.sendMessage(t('msg.burmaldaholic.lastchance.broadcast_success', p.name));
      }
      const cd = cooldownFor(mode, c);
      if (cd > 0) p.sendMessage(t('msg.burmaldaholic.lastchance.cooldown_started', duration(cd)));
      for (const l of listeners) {
        try {
          l({ player: p, mode, paid });
        } catch (err) {
          ctx.log.error('onSaved listener failed', err);
        }
      }
    }

    // ---- tails: the vanilla death went through -------------------------------------------
    world.afterEvents.entityDie.subscribe(
      ctx.guard((e) => {
        const p = e.deadEntity as Player;
        const pend = pending.get(p.id);
        pending.delete(p.id);
        saving.delete(p.id);
        if (!pend || system.currentTick - pend.tick > PENDING_TTL) return;
        if (pend.kind === 'not_eligible') {
          p.sendMessage(t('msg.burmaldaholic.lastchance.hardcore.not_eligible'));
          return;
        }
        ctx.hud.title(p, t('msg.burmaldaholic.lastchance.tails_title'), t('msg.burmaldaholic.lastchance.tails_subtitle'));
        p.sendMessage(t('msg.burmaldaholic.lastchance.failure'));
        world.sendMessage(t('msg.burmaldaholic.lastchance.broadcast_failure', p.name));
        startCooldown(p, pend.mode); // §15.1: the cooldown starts on either outcome
      }),
      { entityTypes: [PLAYER_ID] },
    );

    // ---- High Stakes scar: heals never pass the scarred maximum ---------------------------
    world.beforeEvents.entityHeal.subscribe(
      ctx.guard((e) => {
        if (e.healedEntity.typeId !== PLAYER_ID) return;
        const p = e.healedEntity as Player;
        const scar = scarOf(p);
        if (!scar) return;
        const h = healthOf(p);
        if (!h) return;
        const allowed = cappedHealing(h.currentValue, e.healing, scarredMax(h.effectiveMax, scar));
        if (allowed <= 0) e.cancel = true;
        else e.healing = allowed;
      }),
    );

    world.afterEvents.playerSpawn.subscribe(
      ctx.guard((e) => {
        scars.delete(e.player.id);
        system.run(() => e.player.isValid && ctx.isCasinoEnabled() && clampScar(e.player));
      }),
    );
    world.afterEvents.playerLeave.subscribe((e) => {
      scars.delete(e.playerId);
      pending.delete(e.playerId);
      saving.delete(e.playerId);
    });

    // ---- once a second: scar clamp (regen from effects/saturation), "recharged" notices ----
    system.runInterval(
      ctx.guard(() => {
        const now = system.currentTick;
        for (const [id, pend] of pending) if (now - pend.tick > PENDING_TTL) pending.delete(id);
        for (const [id, until] of saving) if (until < now) saving.delete(id);
        const c = cfg();
        const mode = modeFor(world.isHardcore, c);
        const wt = worldTick();
        for (const p of world.getPlayers()) {
          clampScar(p);
          if (!mode) continue;
          const rec = readRecord(p);
          if (readyDue(rec, wt, cooldownFor(mode, c))) {
            rec.notified = true;
            writeRecord(p, rec);
            p.sendMessage(t('msg.burmaldaholic.lastchance.ready'));
          }
        }
      }),
      20,
    );

    // ---- API + Casino Menu "Rules" page ----------------------------------------------------
    const status = (p: Player): LastchanceStatus => {
      const c = cfg();
      const mode = ctx.isCasinoEnabled() ? modeFor(world.isHardcore, c) : undefined;
      const rec = readRecord(p);
      const cd = mode ? cooldownFor(mode, c) : 0;
      return {
        mode,
        chance: mode === 'high_stakes' ? c.hardcore.chance : mode ? chanceFor(difficulty(), c) : 0,
        cooldownTicks: cd,
        remainingTicks: mode ? cooldownRemaining(worldTick(), rec.usedAt, cd) : 0,
        scarHp: rec.scarHp ?? 0,
      };
    };
    const api: LastchanceApi = { status, onSaved: (l) => void listeners.push(l) };
    ctx.services.provide(LASTCHANCE_SERVICE, api);

    ctx.menu.add({
      id: 'rules',
      order: 70,
      label: t('gui.burmaldaholic.menu.rules'),
      icon: 'textures/items/totem',
      open: async (p) => {
        const s = status(p);
        const diffKey = world.isHardcore ? 'hardcore' : difficulty();
        const lc: (Raw | undefined)[] = [];
        if (!s.mode) lc.push(t('gui.burmaldaholic.menu.rules.last_chance_off'));
        else {
          if (s.mode === 'high_stakes') lc.push(t('gui.burmaldaholic.menu.rules.hardcore_high_stakes'));
          lc.push(t('gui.burmaldaholic.menu.rules.last_chance', `${Math.round(s.chance * 100)}%`, duration(s.cooldownTicks)));
          lc.push(
            s.remainingTicks > 0
              ? t('gui.burmaldaholic.menu.rules.last_chance_cooldown', duration(s.remainingTicks))
              : t('gui.burmaldaholic.menu.rules.last_chance_ready'),
          );
        }
        if (s.scarHp > 0) lc.push(t('msg.burmaldaholic.lastchance.hardcore.scar', heartsText(s.scarHp)));
        const body = lines(
          t('gui.burmaldaholic.menu.rules.difficulty', t(`gui.burmaldaholic.common.difficulty.${diffKey}`)),
          ...lc,
          t('gui.burmaldaholic.menu.rules.chaos', t(ctx.config.bool('chaos.enabled') ? 'gui.burmaldaholic.common.on' : 'gui.burmaldaholic.common.off')),
          t('gui.burmaldaholic.menu.rules.disclaimer'),
        );
        const form = new ActionFormData().title(t('gui.burmaldaholic.menu.rules')).body(body).button(t('gui.burmaldaholic.common.back'));
        const res = await showForm(p, form);
        if (res && !res.canceled && res.selection === 0) await ctx.menu.open(p);
      },
    });
  },
};
