/**
 * Daily contracts runtime (GAME_DESIGN §3.4.4; the spec lists them under core, which ships
 * none on Bedrock, so the vip module runs them - slots, targets and rewards depend on VIP).
 *
 * Edition notes (no stable events for these on Bedrock 1.26.30):
 *  - fish: counted as fish items gained while holding a fishing rod.
 *  - smelt: counted as smelting outputs gained within 5 blocks of a furnace/blast furnace/smoker.
 *  - trade: counted from core's trade earnings (economy reason `core.earn.trade`).
 *  - roulette_red: reported by the roulette module through VipApi.reportContract; left out of
 *    the pool until a game registers it.
 */
import {
  BlockVolume,
  type Entity,
  EntityComponentTypes,
  type EntityInventoryComponent,
  GameMode,
  ItemComponentTypes,
  type ItemEnchantableComponent,
  type Player,
  system,
  world,
} from '@minecraft/server';
import { type ModuleContext, NO_REWARD_TAG, chips, color, mathRng, readJson, t, worldTick, writeJson } from '../core';
import { mcDay } from '../core/logic/economy-math';
import type { VipContractId } from './api';
import {
  CONTRACT_IDS,
  type Contract,
  type ContractId,
  type ContractParams,
  type ContractState,
  FISH_ITEMS,
  SMELT_OUTPUTS,
  addProgress,
  allDone,
  contractBonus,
  contractSlots,
  generate,
  isContractId,
  isMatureCrop,
  isStale,
  killContracts,
  oreContract,
  reroll,
  topUp,
  travelStep,
} from './logic';
import type { VipService } from './vip';

const STATE_PROP = 'burmaldaholic:vip.contracts';
/** Contracts only a game module can observe (added to the pool once a game registers them). */
const EXTERNAL: readonly ContractId[] = ['roulette_red'];
const FURNACES = ['minecraft:furnace', 'minecraft:lit_furnace', 'minecraft:blast_furnace', 'minecraft:lit_blast_furnace', 'minecraft:smoker', 'minecraft:lit_smoker'];

export type RerollOutcome = { ok: true; contract: Contract; cost: number } | { ok: false; reason: 'rerolled' | 'done' | 'funds' | 'other' };

export class ContractsService {
  private readonly cache = new Map<string, ContractState>();
  private readonly sources = new Set<ContractId>();
  private readonly counts = new Map<string, { fish: number; smelt: number }>();
  private readonly lastPos = new Map<string, { x: number; z: number }>();
  private readonly travel = new Map<string, number>();

  constructor(
    private readonly ctx: ModuleContext,
    private readonly vip: VipService,
  ) {}

  enabled(): boolean {
    return this.ctx.config.bool('contracts.enabled');
  }

  registerSource(id: VipContractId): void {
    if (isContractId(id)) this.sources.add(id);
  }

  // ---- state -------------------------------------------------------------------------------

  params(p: Player): ContractParams {
    const c = this.ctx.config;
    const tier = this.vip.tier(p);
    const weights: Partial<Record<ContractId, number>> = {};
    for (const id of CONTRACT_IDS) weights[id] = EXTERNAL.includes(id) && !this.sources.has(id) ? 0 : c.int(`contracts.weight.${id}`);
    return {
      tier,
      scaling: c.num('contracts.tierScaling'),
      bonus: contractBonus(tier, c.num('vip.contractBonus.silver'), c.num('vip.contractBonus.gold')),
      multiplier: c.num('contracts.rewardMultiplier'),
      weights,
    };
  }

  slots(p: Player): number {
    return contractSlots(this.vip.tier(p), this.ctx.config.int('contracts.slots'));
  }

  private save(p: Player, s: ContractState): void {
    this.cache.set(p.id, s);
    writeJson(p, STATE_PROP, s);
  }

  /** Today's contracts, generated on the first call of a new world day (announced if `announce`). */
  state(p: Player, announce = true): ContractState {
    const today = mcDay(worldTick());
    let s = this.cache.get(p.id) ?? readJson<ContractState | undefined>(p, STATE_PROP, undefined);
    if (!s || isStale(s, today)) {
      s = generate(mathRng, today, this.slots(p), this.params(p));
      this.save(p, s);
      if (announce && s.list.length) p.sendMessage(color('§e', t('msg.burmaldaholic.contracts.new')));
    } else {
      this.cache.set(p.id, s);
    }
    return s;
  }

  /** After a VIP promotion: extra slots appear immediately. */
  topUp(p: Player): void {
    if (!this.enabled()) return;
    const s = this.state(p, false);
    if (topUp(mathRng, s, this.slots(p), this.params(p)).length) this.save(p, s);
  }

  /** Is a contract with this id open today (cheap check for polling)? */
  private wants(p: Player, id: ContractId): boolean {
    return this.state(p).list.some((c) => c.id === id && !c.done);
  }

  progress(p: Player, id: ContractId, amount: number): void {
    if (!this.enabled() || !this.ctx.isCasinoEnabled() || !p.isValid || !(amount > 0)) return;
    const s = this.state(p);
    if (!s.list.some((c) => c.id === id && !c.done)) return;
    const done = addProgress(s, id, amount);
    this.save(p, s);
    for (const c of done) this.complete(p, c);
    if (done.length && allDone(s)) p.sendMessage(color('§6', t('msg.burmaldaholic.contracts.all_done')));
  }

  private complete(p: Player, c: Contract): void {
    const got = c.reward > 0 ? this.ctx.economy.earn(p, c.reward, t('msg.burmaldaholic.core.source.contract'), 'vip.contract') : 0;
    p.sendMessage(color('§a', t('msg.burmaldaholic.contracts.completed', t(`gui.burmaldaholic.contracts.task.${c.id}`, c.target), chips(got))));
    p.playSound('random.orb', { volume: 1, pitch: 1.2 });
  }

  rerollCost(): number {
    return Math.max(0, this.ctx.config.int('contracts.rerollCost'));
  }

  doReroll(p: Player, index: number): RerollOutcome {
    const s = this.state(p);
    const cur = s.list[index];
    if (!cur) return { ok: false, reason: 'other' };
    if (cur.done) return { ok: false, reason: 'done' };
    if (cur.rerolled) return { ok: false, reason: 'rerolled' };
    const cost = this.rerollCost();
    if (cost > 0 && !this.ctx.economy.debit(p, cost, 'vip.contract_reroll')) return { ok: false, reason: 'funds' };
    const r = reroll(mathRng, s, index, this.params(p));
    if (!r.ok) {
      if (cost > 0) this.ctx.economy.credit(p, cost, 'vip.contract_reroll_refund');
      return { ok: false, reason: 'other' };
    }
    this.save(p, s);
    return { ok: true, contract: r.contract, cost };
  }

  // ---- event sources -----------------------------------------------------------------------

  start(): void {
    const { ctx } = this;
    world.afterEvents.playerBreakBlock.subscribe(
      ctx.guard((e) => {
        const p = e.player;
        if (p.getGameMode() === GameMode.Creative) return;
        const perm = e.brokenBlockPermutation;
        const ore = oreContract(perm.type.id);
        if (ore) {
          const ench = e.itemStackBeforeBreak?.getComponent(ItemComponentTypes.Enchantable) as ItemEnchantableComponent | undefined;
          if (!ench?.hasEnchantment('silk_touch')) this.progress(p, ore, 1);
          return;
        }
        let growth: number | undefined;
        try {
          const g = perm.getState('growth');
          growth = typeof g === 'number' ? g : undefined;
        } catch {
          growth = undefined;
        }
        if (isMatureCrop(perm.type.id, growth)) this.progress(p, 'harvest', 1);
      }),
    );
    world.afterEvents.entityDie.subscribe(
      ctx.guard((e) => {
        const p = killerPlayer(e.damageSource.damagingEntity);
        const dead = e.deadEntity;
        if (!p || p.getGameMode() === GameMode.Creative) return;
        if (dead.typeId.startsWith('burmaldaholic:') || dead.hasTag(NO_REWARD_TAG) || dead.getTags().some((x) => x.startsWith('burmaldaholic'))) return;
        for (const id of killContracts(dead.typeId)) this.progress(p, id, 1);
      }),
    );
    ctx.economy.onChange((p, _bal, delta, reason) => {
      if (reason === 'core.earn.trade' && delta > 0) this.progress(p, 'trade', 1);
    });
    world.afterEvents.playerSpawn.subscribe(
      ctx.guard((e) => {
        if (!e.initialSpawn || !this.enabled()) return;
        const p = e.player;
        system.runTimeout(ctx.guard(() => p.isValid && void this.state(p)), 80);
      }),
    );
    world.afterEvents.playerLeave.subscribe((e) => {
      this.cache.delete(e.playerId);
      this.counts.delete(e.playerId);
      this.lastPos.delete(e.playerId);
      this.travel.delete(e.playerId);
    });
    // Inventory-based fish / smelt detection.
    system.runInterval(
      ctx.guard(() => {
        if (!this.enabled()) return;
        for (const p of world.getAllPlayers()) this.pollInventory(p);
      }),
      10,
    );
    // Nether travel + day rollover for online players.
    system.runInterval(
      ctx.guard(() => {
        if (!this.enabled()) return;
        for (const p of world.getAllPlayers()) this.pollTravel(p);
      }),
      20,
    );
  }

  private pollInventory(p: Player): void {
    const wantFish = this.wants(p, 'fish');
    const wantSmelt = this.wants(p, 'smelt');
    if (!wantFish && !wantSmelt) {
      this.counts.delete(p.id);
      return;
    }
    const inv = (p.getComponent(EntityComponentTypes.Inventory) as EntityInventoryComponent | undefined)?.container;
    if (!inv) return;
    let fish = 0;
    let smelt = 0;
    for (let i = 0; i < inv.size; i++) {
      const it = inv.getItem(i);
      if (!it) continue;
      if (FISH_ITEMS.includes(it.typeId)) fish += it.amount;
      else if (SMELT_OUTPUTS.includes(it.typeId)) smelt += it.amount;
    }
    const prev = this.counts.get(p.id);
    this.counts.set(p.id, { fish, smelt });
    if (!prev) return;
    if (wantFish && fish > prev.fish && inv.getItem(p.selectedSlotIndex)?.typeId === 'minecraft:fishing_rod') this.progress(p, 'fish', fish - prev.fish);
    if (wantSmelt && smelt > prev.smelt && nearFurnace(p)) this.progress(p, 'smelt', smelt - prev.smelt);
  }

  private pollTravel(p: Player): void {
    if (p.dimension.id !== 'minecraft:nether' || !this.wants(p, 'explore_nether')) {
      this.lastPos.delete(p.id);
      return;
    }
    const pos = { x: p.location.x, z: p.location.z };
    const prev = this.lastPos.get(p.id);
    this.lastPos.set(p.id, pos);
    if (!prev) return;
    const acc = (this.travel.get(p.id) ?? 0) + travelStep(prev, pos, 80);
    const whole = Math.floor(acc);
    this.travel.set(p.id, acc - whole);
    if (whole > 0) this.progress(p, 'explore_nether', whole);
  }
}

function killerPlayer(e: Entity | undefined): Player | undefined {
  if (!e?.isValid) return undefined;
  if (e.typeId === 'minecraft:player') return e as Player;
  const owner = (e.getComponent(EntityComponentTypes.Projectile) as { owner?: Entity } | undefined)?.owner;
  return owner?.typeId === 'minecraft:player' ? (owner as Player) : undefined;
}

function nearFurnace(p: Player): boolean {
  const { x, y, z } = p.location;
  try {
    return p.dimension.containsBlock(new BlockVolume({ x: x - 5, y: y - 3, z: z - 5 }, { x: x + 5, y: y + 3, z: z + 5 }), { includeTypes: FURNACES });
  } catch {
    return false;
  }
}
