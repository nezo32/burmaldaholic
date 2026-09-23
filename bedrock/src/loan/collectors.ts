/**
 * Debt Collector squads (GAME_DESIGN §5.5, §5.7, §5.8): wave scheduling per debtor, safe spawning
 * 24–40 blocks away, the APPROACH → NEGOTIATE → HOSTILE / LEAVING state machine (pure, in
 * logic/squad.ts) driven by entity events, the negotiation form, Accountant "Audit", Enforcer
 * knockback, Repossession on the debtor's death, PAID when the debt hits 0, and cleanup of
 * orphaned squad members after restarts.
 *
 * Entities (packs/loan/BP/entities/loan): component groups switched by the events
 * `burmaldaholic:approach|negotiate|hostile|leave`. Targeting filters on the squad's per-debtor slot tag (DEBTOR_SLOT_TAG + slot, component group `burmaldaholic:target_<slot>`), which only
 * the hunted player carries while a squad is live.
 */
import {
  type Dimension,
  type Entity,
  type EntityHealthComponent,
  type EntityInventoryComponent,
  type EntityItemComponent,
  type Player,
  type Vector3,
  EntityComponentTypes,
  EntityDamageCause,
  system,
  world,
} from '@minecraft/server';
import { ActionFormData, uiManager } from '@minecraft/server-ui';
import { type Raw, HudPriority, NO_REWARD_TAG, chips, color, detach, duration, lines, mathRng, showForm, t, variant, worldTick } from '../core';
import { MULTIPLAYER_SERVICE, type MultiplayerApi } from '../multiplayer/api';
import { DEBTOR_SLOTS, DEBTOR_SLOT_TAG, DEBTOR_TAG, SQUAD_ENTITY_IDS, SQUAD_TAG } from './api';
import {
  type Cell,
  type EndReason,
  type SquadEffect,
  type SquadMachine,
  type SquadTimers,
  type UnitId,
  AUDIT,
  DEFAULT_SPAWN_RULES,
  DEFAULT_TIMERS,
  ENFORCER_KNOCKBACK,
  VARIANTS,
  answerNegotiation,
  appraisalKey,
  bestAppraised,
  classify,
  findSpawn,
  freeSlot,
  newSquad,
  onDebtorJoin,
  partialPayment,
  planRepossession,
  scaledHealth,
  squadComposition,
  squadMembers,
  stepSquad,
  waveDue,
  waveQueued,
  waveRetry,
  waveSpawned,
} from './logic';
import type { LoanService } from './service';
import { speech } from './shark';

const SQUAD_ID_TAG = 'burmaldaholic_loan_s_';
const UNIT_TYPES = new Set<string>(SQUAD_ENTITY_IDS);

interface Member {
  id: string;
  unit: UnitId;
}

interface Squad {
  id: string;
  debtorId: string;
  dim: string;
  /** leader first */
  members: Member[];
  m: SquadMachine;
  attacked: boolean;
  debtorDead: boolean;
  lastAudit: number;
  negotiating: boolean;
  /** per-debtor target slot (DEBTOR_SLOT_TAG + slot on the debtor, target_<slot> on members) */
  slot: number;
}

/** Tag / untag the hunted player: generic DEBTOR_TAG + the squad's slot tag (members target only it). */
function tagDebtor(p: Player | undefined, slot: number, on: boolean): void {
  if (!p?.isValid) return;
  if (on) {
    p.addTag(DEBTOR_TAG);
    p.addTag(DEBTOR_SLOT_TAG + slot);
  } else {
    p.removeTag(DEBTOR_TAG);
    for (const t of p.getTags()) if (t.startsWith(DEBTOR_SLOT_TAG)) p.removeTag(t);
  }
}

const say = (key: keyof typeof VARIANTS, ...args: (Raw | number)[]): Raw => variant(mathRng, key, VARIANTS[key], ...args);

export class Collectors {
  private readonly squads = new Map<string, Squad>();
  /** Debtors whose squad vanished because they logged off (one queued wave on next join). */
  private readonly queuedOffline = new Set<string>();
  private seq = 0;

  constructor(private readonly svc: LoanService) {}

  private get ctx() {
    return this.svc.ctx;
  }

  isSquadMember(e: Entity): boolean {
    return UNIT_TYPES.has(e.typeId);
  }

  squadOf(debtorId: string): Squad | undefined {
    for (const s of this.squads.values()) if (s.debtorId === debtorId && s.m.state !== 'done') return s;
    return undefined;
  }

  start(): void {
    const ctx = this.ctx;
    // Orphans from a previous session (their squad state was not persisted).
    system.run(() => this.cleanupOrphans());
    world.afterEvents.entityLoad.subscribe((e) => {
      if (e.entity.isValid && e.entity.hasTag(SQUAD_TAG) && !this.liveMember(e.entity)) e.entity.remove();
    });
    // Squad members attacked by their debtor -> hostile.
    world.afterEvents.entityHurt.subscribe((e) => {
      try {
        this.onHurt(e.hurtEntity, e.damageSource.damagingEntity);
      } catch (err) {
        ctx.log.error('collector hurt', err);
      }
    });
    world.afterEvents.entityDie.subscribe((e) => {
      try {
        if (e.deadEntity.typeId === 'minecraft:player') this.onDebtorDeath(e.deadEntity as Player, e.damageSource.damagingEntity);
        else if (UNIT_TYPES.has(e.deadEntity.typeId)) this.onMemberDeath(e.deadEntity.id);
      } catch (err) {
        ctx.log.error('collector death', err);
      }
    });
    world.afterEvents.playerSpawn.subscribe(
      ctx.guard((e) => {
        if (!e.initialSpawn) return;
        if (!this.squadOf(e.player.id)) tagDebtor(e.player, 0, false);
        let rec = this.svc.record(e.player);
        if (this.queuedOffline.delete(e.player.id)) rec = waveQueued(rec);
        const j = onDebtorJoin(rec, worldTick(), ctx.config.int('loan.offlineWaveDelayTicks'));
        if (j.missed && this.svc.collectorsMode()) {
          this.svc.save(e.player, j.rec);
          e.player.sendMessage(color('§e', t('msg.burmaldaholic.loan.wave_queued')));
        }
      }),
    );
    // Debt reached 0 by any means -> every live squad of that debtor is PAID.
    this.svc.onDebtChange((player, rec) => {
      if (rec.owed > 0) return;
      const s = this.squadOf(player.id);
      if (s && s.m.state !== 'leaving') {
        this.apply(s, [{ type: 'leave', reason: 'paid' }], { state: 'leaving', since: worldTick(), reason: 'paid' });
      }
    });
    system.runInterval(() => {
      try {
        this.tick();
      } catch (err) {
        ctx.log.error('collector tick', err);
      }
    }, 10);
  }

  // ---- scheduling -----------------------------------------------------------------------

  /** Called every second per online player (after the loan tick). */
  maybeSpawn(player: Player): void {
    if (!this.svc.collectorsMode()) return;
    const rec = this.svc.record(player);
    if (rec.status !== 'default') return;
    const now = worldTick();
    if (!waveDue({ now, nextWaveTick: rec.nextWaveTick, hasLiveSquad: !!this.squadOf(player.id) })) return;
    const ok = this.spawnWave(player, rec.owed, rec.wave + 1);
    this.svc.save(player, ok ? waveSpawned(this.svc.record(player), now) : waveRetry(this.svc.record(player), now));
  }

  private timers(): SquadTimers {
    const c = this.ctx.config;
    return { ...DEFAULT_TIMERS, approachTicks: c.int('loan.approachTicks'), negotiateTicks: c.int('loan.negotiateTicks'), hostileTicks: c.int('loan.hostileTicks') };
  }

  private healthMultiplier(): number {
    const band = this.svc.band();
    if (band === 'easy') return this.ctx.config.num('loan.collectorHealthMultiplier.easy');
    if (band === 'hard') return this.ctx.config.num('loan.collectorHealthMultiplier.hard');
    return 1;
  }

  // ---- spawning -------------------------------------------------------------------------

  /** Spawn the wave around the debtor. False = no safe spot for every member (retry later). */
  private spawnWave(player: Player, owed: number, wave: number): boolean {
    const dim = player.dimension;
    const loc = player.location;
    // Never inside a boss fight (chaos safety §13.4).
    for (const boss of ['minecraft:ender_dragon', 'minecraft:wither']) {
      if (dim.getEntities({ location: loc, maxDistance: 128, type: boss }).length) return false;
    }
    const comp = squadComposition(owed, wave, this.svc.band(), { squadMax: this.ctx.config.int('loan.squadMax'), escalationMax: this.ctx.config.int('loan.escalationMax') });
    const units = squadMembers(comp);
    const cell = cellReader(dim);
    const rules = { ...DEFAULT_SPAWN_RULES, minY: dim.heightRange.min, maxY: dim.heightRange.max };
    // Never inside a player casino claim (multiplayer); findSpawn also keeps to the world border.
    const mp = this.ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE);
    const allowed = (p: Vector3): boolean => {
      try {
        return !mp?.isInsideClaim(dim, p);
      } catch {
        return true;
      }
    };
    const spots = units.map(() => findSpawn(mathRng, loc, cell, rules, allowed));
    if (spots.some((s) => !s)) return false;

    const id = `${worldTick().toString(36)}${(++this.seq).toString(36)}`;
    const used = [...this.squads.values()].filter((x) => x.m.state !== 'done').map((x) => x.slot);
    const slot = freeSlot(used, DEBTOR_SLOTS);
    const squad: Squad = { id, debtorId: player.id, dim: dim.id, members: [], m: newSquad(worldTick()), attacked: false, debtorDead: false, lastAudit: 0, negotiating: false, slot };
    const mult = this.healthMultiplier();
    units.forEach((unit, i) => {
      try {
        const e = dim.spawnEntity(`burmaldaholic:${unit}`, spots[i]!, { spawnEvent: 'burmaldaholic:approach' });
        e.addTag(SQUAD_TAG);
        e.addTag(SQUAD_ID_TAG + id);
        e.addTag(NO_REWARD_TAG);
        e.triggerEvent(`burmaldaholic:target_${slot}`);
        (e.getComponent(EntityComponentTypes.Health) as EntityHealthComponent | undefined)?.setCurrentValue(scaledHealth(unit, mult));
        squad.members.push({ id: e.id, unit });
      } catch (err) {
        this.ctx.log.warn(`spawn ${unit} failed`, err);
      }
    });
    if (!squad.members.length) return false;
    this.squads.set(id, squad);
    tagDebtor(player, slot, true);
    this.ctx.hud.title(player, color('§c', t('msg.burmaldaholic.loan.wave_title')), t('msg.burmaldaholic.loan.wave_subtitle'), 5, 50, 10);
    player.sendMessage(color('§c', t('msg.burmaldaholic.loan.wave_incoming', chips(owed))));
    this.ctx.hud.actionbar(player, 'loan.collectors', color('§c', t('msg.burmaldaholic.loan.wave_subtitle')), HudPriority.critical, 100);
    player.playSound('random.door_close', { volume: 1, pitch: 0.6 });
    this.ctx.log.info(`wave ${wave} (${squad.members.length}) for ${player.name}, owed ${owed}`);
    return true;
  }

  // ---- runtime --------------------------------------------------------------------------

  private liveMember(e: Entity): boolean {
    const tag = e.getTags().find((x) => x.startsWith(SQUAD_ID_TAG));
    const s = tag ? this.squads.get(tag.slice(SQUAD_ID_TAG.length)) : undefined;
    return !!s && s.m.state !== 'done';
  }

  private cleanupOrphans(): void {
    for (const d of ['minecraft:overworld', 'minecraft:nether', 'minecraft:the_end']) {
      try {
        for (const e of world.getDimension(d).getEntities({ tags: [SQUAD_TAG] })) if (!this.liveMember(e)) e.remove();
      } catch {
        /* dimension not available */
      }
    }
    for (const p of world.getAllPlayers()) if (!this.squadOf(p.id)) tagDebtor(p, 0, false);
  }

  private alive(s: Squad): { member: Member; e: Entity }[] {
    const out: { member: Member; e: Entity }[] = [];
    for (const member of s.members) {
      const e = world.getEntity(member.id);
      if (e?.isValid) out.push({ member, e });
    }
    return out;
  }

  private debtor(s: Squad): Player | undefined {
    return world.getAllPlayers().find((p) => p.id === s.debtorId);
  }

  private tick(): void {
    const active = this.svc.active();
    const now = worldTick();
    for (const s of [...this.squads.values()]) {
      if (s.m.state === 'done') {
        this.squads.delete(s.id);
        continue;
      }
      const debtor = this.debtor(s);
      if (!active) {
        this.apply(s, [{ type: 'despawn', reason: 'left' }], { state: 'done', since: now, reason: 'left' });
        continue;
      }
      const alive = this.alive(s);
      const leader = alive[0]?.e;
      const same = !!debtor && debtor.dimension.id === s.dim;
      const dist = (e: Entity): number => (debtor ? Math.hypot(e.location.x - debtor.location.x, e.location.y - debtor.location.y, e.location.z - debtor.location.z) : Infinity);
      const r = stepSquad(
        s.m,
        {
          now,
          debtorOnline: !!debtor,
          sameDimension: same,
          debtorDead: s.debtorDead,
          leaderDistance: leader && same ? dist(leader) : undefined,
          nearestDistance: same && alive.length ? Math.min(...alive.map((a) => dist(a.e))) : undefined,
          // Members in unloaded chunks are not "alive" entities but still count (only deaths do).
          alive: s.members.length,
          owed: debtor ? this.svc.record(debtor).owed : 1,
          attacked: s.attacked,
        },
        this.timers(),
      );
      s.attacked = false;
      if (r.effects.length || r.m !== s.m) this.apply(s, r.effects, r.m);
      if (s.m.state === 'hostile' && debtor && same) this.hostileTick(s, debtor, alive, now);
    }
  }

  /** Apply state effects: entity events, dialogue, tags, despawn, schedule changes. */
  private apply(s: Squad, effects: SquadEffect[], m: SquadMachine): void {
    s.m = m;
    const debtor = this.debtor(s);
    const alive = this.alive(s);
    const leader = alive[0];
    const tell = (line: Raw, who = leader): void => {
      if (!debtor || !who) return;
      const out = speech(`entity.burmaldaholic.${who.member.unit}`, line, '§c');
      debtor.sendMessage(out);
      for (const p of who.e.dimension.getPlayers({ location: who.e.location, maxDistance: 24 })) if (p.id !== debtor.id) p.sendMessage(out);
    };
    for (const fx of effects) {
      switch (fx.type) {
        case 'negotiate':
          for (const a of alive) a.e.triggerEvent('burmaldaholic:negotiate');
          if (debtor) detach(this.negotiate(s, debtor), (err) => this.svc.ctx.log.error('collector negotiation form', err));
          break;
        case 'hostile': {
          for (const a of alive) a.e.triggerEvent('burmaldaholic:hostile');
          tagDebtor(debtor, s.slot, true);
          if (debtor && s.negotiating) uiManager.closeAllForms(debtor);
          tell(say('dialog.burmaldaholic.collector.hostile'));
          const big = alive.find((a) => a.member.unit === 'enforcer');
          if (big) tell(say('dialog.burmaldaholic.enforcer.line'), big);
          if (debtor) this.ctx.hud.actionbar(debtor, 'loan.collectors', color('§c', t('msg.burmaldaholic.loan.wave_subtitle')), HudPriority.critical, 100);
          break;
        }
        case 'leave':
          for (const a of alive) a.e.triggerEvent('burmaldaholic:leave');
          tagDebtor(debtor, s.slot, false);
          if (debtor) this.ctx.hud.clear(debtor, 'loan.collectors');
          if (fx.reason === 'paid') {
            tell(say('dialog.burmaldaholic.collector.paid'));
            debtor?.sendMessage(color('§a', t('msg.burmaldaholic.loan.paid_in_full_collectors')));
          } else if (fx.reason === 'partial') {
            tell(say('dialog.burmaldaholic.collector.partial'));
          }
          break;
        case 'despawn':
          this.despawn(s, alive, fx.reason, debtor);
          break;
      }
    }
  }

  private despawn(s: Squad, alive: { e: Entity }[], reason: EndReason, debtor: Player | undefined): void {
    for (const a of alive) {
      try {
        const { x, y, z } = a.e.location;
        for (let i = 0; i < 4; i++) a.e.dimension.spawnParticle('minecraft:basic_smoke_particle', { x: x + (i % 2) - 0.5, y: y + 1 + i * 0.2, z: z + (i > 1 ? 0.5 : -0.5) });
      } catch {
        /* chunk unloaded */
      }
      a.e.remove();
    }
    s.m = { state: 'done', since: worldTick(), reason };
    if (debtor) {
      tagDebtor(debtor, s.slot, false);
      this.ctx.hud.clear(debtor, 'loan.collectors');
      if (reason === 'defeated') {
        debtor.sendMessage(color('§e', t('msg.burmaldaholic.loan.squad_defeated')));
        this.ctx.achievements.unlock(debtor, 'hostile_takeover');
      }
      if (reason === 'offline') this.svc.save(debtor, waveQueued(this.svc.record(debtor)));
    } else if (reason === 'offline') {
      this.queuedOffline.add(s.debtorId);
    }
    this.squads.delete(s.id);
  }

  // ---- negotiation ----------------------------------------------------------------------

  private async negotiate(s: Squad, debtor: Player): Promise<void> {
    if (s.negotiating) return;
    s.negotiating = true;
    try {
      const svc = this.svc;
      const owed = svc.record(debtor).owed;
      const share = this.ctx.config.num('loan.partialPaymentMin');
      const part = partialPayment(owed, share);
      const balance = this.ctx.economy.balance(debtor);
      const leader = this.alive(s)[0];
      const line = say('dialog.burmaldaholic.collector.demand', chips(owed));
      if (leader) debtor.sendMessage(speech(`entity.burmaldaholic.${leader.member.unit}`, line, '§c'));
      const ticks = this.timers().negotiateTicks;
      const form = new ActionFormData()
        .title(t('gui.burmaldaholic.loan.negotiate.title'))
        .body(lines(line, color('§7', t('gui.burmaldaholic.common.balance', chips(balance))), color('§e', t('gui.burmaldaholic.loan.negotiate.timer', duration(ticks)))));
      const choices: ('pay_all' | 'pay_part' | 'refuse')[] = [];
      if (balance >= owed) {
        form.button(t('gui.burmaldaholic.loan.negotiate.pay_all', chips(owed)));
        choices.push('pay_all');
      }
      if (part < owed && balance >= part) {
        form.button(t('gui.burmaldaholic.loan.negotiate.pay_half', chips(part)));
        choices.push('pay_part');
      }
      form.button(t('gui.burmaldaholic.loan.negotiate.refuse'));
      choices.push('refuse');
      const timer = system.runTimeout(() => {
        if (debtor.isValid) uiManager.closeAllForms(debtor);
      }, ticks);
      const res = await showForm(debtor, form);
      system.clearRun(timer);
      if (s.m.state !== 'negotiate' || !debtor.isValid) return;
      const choice = !res || res.canceled || res.selection === undefined ? 'timeout' : (choices[res.selection] ?? 'refuse');
      let paidOk = false;
      if (choice === 'pay_all') paidOk = svc.pay(debtor, owed, 'loan.pay') >= owed || svc.record(debtor).owed === 0;
      if (choice === 'pay_part') {
        const before = svc.record(debtor).owed;
        const paid = svc.pay(debtor, part, 'loan.pay', false);
        paidOk = paid >= part;
        if (paidOk && svc.record(debtor).owed > 0) debtor.sendMessage(color('§e', t('msg.burmaldaholic.loan.partial_collectors', chips(paid), chips(before - paid))));
      }
      if (s.m.state !== 'negotiate') return; // paying in full already switched the squad to PAID
      const r = answerNegotiation(s.m, choice, paidOk, worldTick());
      this.apply(s, r.effects, r.m);
    } finally {
      s.negotiating = false;
    }
  }

  // ---- combat ---------------------------------------------------------------------------

  private hostileTick(s: Squad, debtor: Player, alive: { member: Member; e: Entity }[], now: number): void {
    const acc = alive.find((a) => a.member.unit === 'accountant');
    if (!acc || now - s.lastAudit < AUDIT.cooldown) return;
    const a = acc.e.location;
    const b = debtor.location;
    const d = Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
    if (d > AUDIT.range) return;
    s.lastAudit = now;
    // "Paper fangs": a line of 5 bursts from the Accountant to the debtor, then the hit.
    for (let i = 1; i <= AUDIT.fangs; i++) {
      const f = i / AUDIT.fangs;
      try {
        acc.e.dimension.spawnParticle('minecraft:critical_hit_emitter', { x: a.x + (b.x - a.x) * f, y: b.y + 0.3, z: a.z + (b.z - a.z) * f });
      } catch {
        /* unloaded */
      }
    }
    debtor.applyDamage(AUDIT.damage, { cause: EntityDamageCause.magic, damagingEntity: acc.e });
    debtor.addEffect('weakness', AUDIT.weaknessTicks, { amplifier: 0 });
    if (mathRng.next() < 0.35) debtor.sendMessage(speech('entity.burmaldaholic.accountant', say('dialog.burmaldaholic.accountant.audit'), '§c'));
  }

  private squadOfEntity(e: Entity): Squad | undefined {
    const tag = e.getTags().find((x) => x.startsWith(SQUAD_ID_TAG));
    return tag ? this.squads.get(tag.slice(SQUAD_ID_TAG.length)) : undefined;
  }

  private onHurt(hurt: Entity, by: Entity | undefined): void {
    if (!by?.isValid) return;
    // Squad member hit by its debtor -> the squad turns hostile.
    if (hurt.hasTag(SQUAD_TAG) && by.typeId === 'minecraft:player') {
      const s = this.squadOfEntity(hurt);
      if (s && s.debtorId === by.id) s.attacked = true;
      return;
    }
    // Enforcer knockback 1.5.
    if (by.typeId === 'burmaldaholic:enforcer' && hurt.typeId === 'minecraft:player') {
      const dx = hurt.location.x - by.location.x;
      const dz = hurt.location.z - by.location.z;
      const len = Math.hypot(dx, dz) || 1;
      try {
        hurt.applyKnockback({ x: (dx / len) * ENFORCER_KNOCKBACK, z: (dz / len) * ENFORCER_KNOCKBACK }, 0.45);
      } catch {
        /* knockback may be unsupported on this entity */
      }
    }
  }

  private onMemberDeath(id: string): void {
    for (const s of this.squads.values()) s.members = s.members.filter((m) => m.id !== id);
  }

  /** Repossession (§5.5) when a member of this debtor's squad killed them. */
  private onDebtorDeath(player: Player, killer: Entity | undefined): void {
    const s = this.squadOf(player.id);
    if (!s) return;
    // Any death of the debtor ends a hostile wave; only a squad kill repossesses.
    if (s.m.state === 'hostile') s.debtorDead = true;
    if (!killer?.isValid || !killer.hasTag(SQUAD_TAG) || this.squadOfEntity(killer)?.id !== s.id) return;
    const svc = this.svc;
    const rec = svc.record(player);
    if (rec.owed <= 0) return;
    const pct = this.ctx.config.int('loan.repossessBalancePercent');
    const item = this.ctx.config.bool('loan.repossessItem') ? this.findItem(player) : undefined;
    const plan = planRepossession(this.ctx.economy.balance(player), rec.owed, pct, item?.value ?? 0);
    if (plan.seized > 0 && this.ctx.economy.debit(player, plan.seized, 'loan.repossess')) svc.applyToDebt(player, plan.seized, false);
    let itemName: Raw | undefined;
    if (item && plan.itemCredit > 0) {
      itemName = t(item.nameKey);
      item.take();
      svc.applyToDebt(player, plan.itemCredit, false);
    }
    const leader = this.alive(s)[0];
    if (leader) player.sendMessage(speech(`entity.burmaldaholic.${leader.member.unit}`, say('dialog.burmaldaholic.collector.repossess'), '§c'));
    player.sendMessage(
      color('§c', itemName ? t('msg.burmaldaholic.loan.repossessed', chips(plan.seized), itemName) : t('msg.burmaldaholic.loan.repossessed_chips_only', chips(plan.seized))),
    );
  }

  /**
   * Highest-appraised stack: still in the inventory (keepInventory) or just dropped at the
   * death spot. Chip items are never appraised, so they stay where they fell.
   */
  private findItem(player: Player): { value: number; nameKey: string; take(): void } | undefined {
    const appraisal = (id: string): number => {
      const key = appraisalKey(id);
      return key && this.ctx.config.get(key) !== undefined ? this.ctx.config.int(key) : 0;
    };
    const inv = (player.getComponent(EntityComponentTypes.Inventory) as EntityInventoryComponent | undefined)?.container;
    const stacks: { slot: number; typeId: string; amount: number }[] = [];
    if (inv) {
      for (let i = 0; i < inv.size; i++) {
        const it = inv.getItem(i);
        if (it) stacks.push({ slot: i, typeId: it.typeId, amount: it.amount });
      }
    }
    const inInv = bestAppraised(stacks, safeAppraisal(appraisal));
    if (inInv && inv) {
      const key = inv.getItem(inInv.stack.slot)?.localizationKey ?? 'item.burmaldaholic.overdue_notice';
      return { value: inInv.value, nameKey: key, take: () => inv.setItem(inInv.stack.slot, undefined) };
    }
    const drops = player.dimension.getEntities({ type: 'minecraft:item', location: player.location, maxDistance: 3 });
    const dropped = drops.map((e, i) => {
      const st = (e.getComponent(EntityComponentTypes.Item) as EntityItemComponent | undefined)?.itemStack;
      return { slot: i, typeId: st?.typeId ?? '', amount: st?.amount ?? 0, key: st?.localizationKey ?? '' };
    });
    const best = bestAppraised(dropped, safeAppraisal(appraisal));
    if (!best) return undefined;
    const ent = drops[best.stack.slot]!;
    return { value: best.value, nameKey: best.stack.key, take: () => ent.isValid && ent.remove() };
  }
}

/** Config lookups throw for unknown keys; unknown items simply have no appraisal. */
const safeAppraisal =
  (f: (id: string) => number) =>
  (id: string): number => {
    try {
      return f(id);
    } catch {
      return 0;
    }
  };

/** Block classifier for the spawn search (undefined = unloaded chunk / out of world). */
function cellReader(dim: Dimension): (x: number, y: number, z: number) => Cell | undefined {
  return (x, y, z) => {
    try {
      const b = dim.getBlock({ x, y, z });
      if (!b) return undefined;
      return classify(b.typeId, b.isAir, b.isLiquid);
    } catch {
      return undefined;
    }
  };
}
