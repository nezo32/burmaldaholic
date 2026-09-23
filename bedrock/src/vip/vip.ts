/**
 * VIP tiers runtime (GAME_DESIGN §12): lifetime-wagered tracking from `wagers.onSettled`, the
 * VipProvider for core limits, promotions (title + sound + chat, Netherite broadcast, vip_*
 * milestones), daily cashback, cosmetics (name color at tables, Platinum+ name badge, win
 * particles, Diamond Casino Card) and the HUD segment.
 */
import {
  EntityComponentTypes,
  type EntityInventoryComponent,
  ItemStack,
  type Player,
  type Vector3,
  system,
  world,
} from '@minecraft/server';
import {
  CASINO_CARD_ID,
  HudPriority,
  type ModuleContext,
  type SettledEvent,
  VIP_COLORS,
  chips,
  color,
  lit,
  readJson,
  t,
  worldTick,
  writeJson,
} from '../core';
import { mcDay } from '../core/logic/economy-math';
import type { VipApi, VipContractId, VipPromotion } from './api';
import {
  DIAMOND,
  type DayLedger,
  GOLD,
  MAX_TIER,
  NETHERITE,
  type PerkParams,
  PLATINUM,
  SILVER,
  cashbackAmount,
  cashbackRate,
  progress,
  promotions,
  recordRound,
  rollLedger,
  tierFor,
  vipAchievement,
} from './logic';

export const DIAMOND_CARD_ID = 'burmaldaholic:vip_diamond_casino_card';
const WAGERED_PROP = 'burmaldaholic:vip.wagered';
const TIER_PROP = 'burmaldaholic:vip.tier';
const LEDGER_PROP = 'burmaldaholic:vip.ledger';
const MILESTONES_PROP = 'burmaldaholic:vip.milestones';
const TIER_KEYS = ['bronze', 'silver', 'gold', 'platinum', 'diamond', 'netherite'] as const;

export class VipService {
  private readonly promoted: ((e: VipPromotion) => void)[] = [];
  private readonly nameTags = new Map<string, string>();
  /** set by index.ts: contract hooks for settled rounds */
  contractHook?: (p: Player, id: VipContractId, n: number) => void;

  constructor(private readonly ctx: ModuleContext) {}

  // ---- config ------------------------------------------------------------------------------

  private enabled(): boolean {
    return this.ctx.config.bool('enabled');
  }

  thresholds(): number[] {
    return TIER_KEYS.slice(1).map((k) => this.ctx.config.num(`vip.threshold.${k}`));
  }

  maxBetOf(tier: number): number {
    return this.ctx.config.int(`vip.maxBet.${TIER_KEYS[Math.max(0, Math.min(MAX_TIER, tier))]}`);
  }

  private cashbackRates(): number[] {
    return TIER_KEYS.slice(GOLD).map((k) => this.ctx.config.num(`vip.cashback.${k}`));
  }

  perkParams(): PerkParams {
    let loanProducts: unknown;
    try {
      loanProducts = this.ctx.config.json<number[][]>('loan.products');
    } catch {
      loanProducts = undefined;
    }
    return {
      loanProducts: Array.isArray(loanProducts) ? loanProducts.filter(Array.isArray) : [],
      contractBonusSilver: this.ctx.config.num('vip.contractBonus.silver'),
      contractBonusGold: this.ctx.config.num('vip.contractBonus.gold'),
      baseSlots: this.ctx.config.int('contracts.slots'),
      cashback: this.cashbackRates(),
    };
  }

  // ---- state -------------------------------------------------------------------------------

  wagered(p: Player): number {
    const v = p.getDynamicProperty(WAGERED_PROP);
    return typeof v === 'number' && Number.isFinite(v) ? v : 0;
  }

  private storedTier(p: Player): number {
    const v = p.getDynamicProperty(TIER_PROP);
    return typeof v === 'number' ? Math.max(0, Math.min(MAX_TIER, Math.floor(v))) : 0;
  }

  /** Earned tier (never lost). Bronze for everyone while `vip.enabled` is false. */
  tier(p: Player): number {
    if (!this.enabled()) return 0;
    return Math.max(this.storedTier(p), tierFor(this.wagered(p), this.thresholds()));
  }

  milestones(p: Player): string[] {
    const v = readJson<unknown>(p, MILESTONES_PROP, []);
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [];
  }

  ledger(p: Player): DayLedger {
    return rollLedger(readJson<DayLedger | undefined>(p, LEDGER_PROP, undefined), mcDay(worldTick())).ledger;
  }

  // ---- wiring ------------------------------------------------------------------------------

  start(): void {
    const { ctx } = this;
    ctx.limits.setVipProvider({ tier: (p) => this.tier(p), maxBet: (p) => this.maxBetOf(this.tier(p)) });
    ctx.wagers.onSettled((e) => this.onSettled(e));

    world.afterEvents.playerSpawn.subscribe(
      ctx.guard((e) => {
        if (!e.initialSpawn) return;
        const p = e.player;
        system.runTimeout(
          ctx.guard(() => {
            if (!p.isValid) return;
            this.checkPromotion(p);
            this.checkCashback(p);
            this.swapCards(p);
          }),
          60,
        );
      }),
    );
    world.afterEvents.playerLeave.subscribe((e) => this.nameTags.delete(e.playerId));

    // Day rollover (cashback), threshold edits (promotion), cards: every 5 s.
    system.runInterval(
      ctx.guard(() => {
        for (const p of world.getAllPlayers()) {
          this.checkPromotion(p);
          this.checkCashback(p);
          if (this.tier(p) >= DIAMOND) this.swapCards(p);
        }
      }),
      100,
    );
    // Name tags (colored at tables / badge) every second; restored when casino mode is off.
    system.runInterval(() => {
      for (const p of world.getAllPlayers()) {
        try {
          this.updateNameTag(p);
        } catch (err) {
          ctx.log.warn('name tag update failed', err);
        }
      }
    }, 20);

    // HUD: replace core's plain tier segment with tier + progress to the next tier.
    ctx.hud.addSegment({
      id: 'core.vip',
      order: 20,
      render: (p) => {
        const tier = this.tier(p);
        const name = ctx.limits.tierName(tier);
        const pr = progress(this.wagered(p), this.thresholds(), tier);
        if (pr.next === undefined) return t('hud.burmaldaholic.vip', name);
        return t('hud.burmaldaholic.vip.progress', name, Math.floor(pr.fraction * 100));
      },
    });
  }

  onPromoted(l: (e: VipPromotion) => void): void {
    this.promoted.push(l);
  }

  api(extra: Pick<VipApi, 'reportContract' | 'registerContractSource'>): VipApi {
    return {
      lifetimeWagered: (p) => this.wagered(p),
      tier: (p) => this.tier(p),
      cashbackRate: (p) => cashbackRate(this.tier(p), this.cashbackRates()),
      onPromoted: (l) => this.onPromoted(l),
      milestones: (p) => this.milestones(p),
      ...extra,
    };
  }

  // ---- settled rounds ----------------------------------------------------------------------

  private onSettled(e: SettledEvent): void {
    const p = e.player;
    if (!p.isValid) return;
    const staked = Math.max(0, e.staked);
    if (staked > 0) p.setDynamicProperty(WAGERED_PROP, this.wagered(p) + staked);

    // Cashback: house-banked chip rounds paid by the bank (never PvP / owned casinos).
    const eligible = e.houseBanked && e.house.kind === 'bank' && e.stakeKind === 'chips';
    const r = recordRound(readJson<DayLedger | undefined>(p, LEDGER_PROP, undefined), mcDay(worldTick()), staked, Math.max(0, e.totalReturn), eligible);
    writeJson(p, LEDGER_PROP, r.ledger);
    if (r.closed) this.payCashback(p, r.closed);

    this.checkPromotion(p);
    if (e.net > 0) this.winParticles(p);

    const hook = this.contractHook;
    if (hook) {
      if (staked > 0) hook(p, 'wager', staked);
      if (e.game === 'blackjack' && e.houseBanked && e.net > 0) hook(p, 'win_blackjack', 1);
      if (e.game === 'slots' && e.houseBanked) hook(p, 'spin_slots', 1);
      if (e.game === 'poker' && !e.houseBanked) hook(p, 'play_poker', 1);
    }
  }

  // ---- promotion ---------------------------------------------------------------------------

  /** Announce each newly reached tier once (also after an admin lowers the thresholds). */
  checkPromotion(p: Player): void {
    if (!this.enabled()) return;
    const stored = this.storedTier(p);
    const now = this.tier(p);
    if (now <= stored) return;
    p.setDynamicProperty(TIER_PROP, now);
    const gained = promotions(stored, now);
    gained.forEach((tier, i) => this.announce(p, tier, i === gained.length - 1));
    if (now >= DIAMOND) this.swapCards(p);
  }

  /** `loud` = the last tier of this promotion (title/sound once on multi-tier jumps). */
  private announce(p: Player, tier: number, loud: boolean): void {
    const { ctx } = this;
    const name = ctx.limits.tierName(tier);
    if (loud) {
      ctx.hud.title(p, t('msg.burmaldaholic.vip.promoted_title', name), undefined, 10, 60, 20);
      p.playSound('random.levelup', { volume: 1, pitch: 1 });
      this.promoParticles(p, tier);
    }
    p.sendMessage(t('msg.burmaldaholic.vip.promoted', name, chips(this.maxBetOf(tier))));
    if (tier === NETHERITE && ctx.config.bool('vip.announceNetherite')) {
      for (const other of world.getAllPlayers()) if (other.id !== p.id) other.sendMessage(t('msg.burmaldaholic.vip.netherite_broadcast', lit(p.name)));
    }
    const ach = vipAchievement(tier);
    if (ach) {
      const list = this.milestones(p);
      if (!list.includes(ach)) {
        list.push(ach);
        writeJson(p, MILESTONES_PROP, list);
        const msg = t('gui.burmaldaholic.achievements.unlocked', color('§e', t(`advancement.burmaldaholic.${ach}.title`)));
        p.sendMessage(msg);
        ctx.hud.actionbar(p, 'vip.milestone', msg, HudPriority.alert, 80);
      }
    }
    for (const l of this.promoted) {
      try {
        l({ player: p, from: tier - 1, to: tier });
      } catch (err) {
        ctx.log.error('promotion listener failed', err);
      }
    }
  }

  // ---- cashback ----------------------------------------------------------------------------

  checkCashback(p: Player): void {
    const stored = readJson<DayLedger | undefined>(p, LEDGER_PROP, undefined);
    const r = rollLedger(stored, mcDay(worldTick()));
    if (!r.closed) return;
    writeJson(p, LEDGER_PROP, r.ledger);
    this.payCashback(p, r.closed);
  }

  private payCashback(p: Player, day: DayLedger): void {
    const rate = cashbackRate(this.tier(p), this.cashbackRates());
    const amount = cashbackAmount(day.cbStaked, day.cbReturned, rate);
    if (amount <= 0) return;
    const got = this.ctx.economy.credit(p, amount, 'vip.cashback');
    if (got > 0) p.sendMessage(color('§a', t('msg.burmaldaholic.vip.cashback', chips(got))));
  }

  // ---- cosmetics ---------------------------------------------------------------------------

  /** Silver+: tier-colored name while seated at a table; Platinum+: colored badge after the name. */
  private updateNameTag(p: Player): void {
    let desired = p.name;
    if (this.ctx.isCasinoEnabled()) {
      const tier = this.tier(p);
      const c = VIP_COLORS[tier] ?? '§f';
      const atTable = tier >= SILVER && this.ctx.tables.sessionOf(p) !== undefined;
      desired = `${atTable ? c : ''}${p.name}${atTable ? '§r' : ''}${tier >= PLATINUM ? ` ${c}◆` : ''}`;
    }
    const managed = this.nameTags.get(p.id);
    if (managed === undefined && desired === p.name) return;
    if (p.nameTag !== desired) p.nameTag = desired;
    if (desired === p.name) this.nameTags.delete(p.id);
    else this.nameTags.set(p.id, desired);
  }

  private spawn(p: Player, id: string, at: Vector3): void {
    try {
      p.dimension.spawnParticle(id, at);
    } catch {
      /* chunk unloaded / unknown particle: cosmetic only */
    }
  }

  private ring(p: Player, id: string, radius: number, y: number, n: number): void {
    const { x, z } = p.location;
    for (let i = 0; i < n; i++) {
      const a = (i / n) * Math.PI * 2;
      this.spawn(p, id, { x: x + Math.cos(a) * radius, y: p.location.y + y, z: z + Math.sin(a) * radius });
    }
  }

  /** Gold+: gold sparkle on wins; Netherite: soul-fire aura. */
  private winParticles(p: Player): void {
    const tier = this.tier(p);
    if (tier >= NETHERITE) {
      this.ring(p, 'minecraft:soul_particle', 0.9, 0.2, 12);
      this.ring(p, 'minecraft:soul_particle', 0.7, 1.2, 8);
      this.spawn(p, 'minecraft:totem_particle', { x: p.location.x, y: p.location.y + 1, z: p.location.z });
    } else if (tier >= GOLD) {
      this.ring(p, 'minecraft:totem_particle', 0.6, 1.0, 6);
    }
  }

  private promoParticles(p: Player, tier: number): void {
    this.ring(p, 'minecraft:totem_particle', 1, 0.5, 10);
    if (tier >= NETHERITE) this.ring(p, 'minecraft:soul_particle', 1.2, 0.2, 16);
  }

  /** Diamond+: every Casino Card in the inventory becomes the Diamond Casino Card (same menu). */
  swapCards(p: Player): void {
    if (this.tier(p) < DIAMOND) return;
    const inv = (p.getComponent(EntityComponentTypes.Inventory) as EntityInventoryComponent | undefined)?.container;
    if (!inv) return;
    for (let i = 0; i < inv.size; i++) {
      const it = inv.getItem(i);
      if (it?.typeId !== CASINO_CARD_ID) continue;
      try {
        inv.setItem(i, new ItemStack(DIAMOND_CARD_ID, it.amount));
      } catch (err) {
        this.ctx.log.warn('card swap failed', err);
        return;
      }
    }
  }
}

