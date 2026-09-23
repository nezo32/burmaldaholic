/**
 * Earning chips by playing (GAME_DESIGN §3.4): ores, mob kills, villager trades. Credited to
 * the balance with a merged action-bar toast. Only while casino mode is on.
 *
 * Edition notes (Bedrock):
 *  - Mobs: "killed by player" = the damaging entity (or projectile owner) of the death is a
 *    player. Spawner mobs are tagged on spawn when a (trial) spawner is within 5 blocks.
 *    Modules exclude their own mobs (chaos waves, collectors) with NO_REWARD_TAG or because
 *    their type id starts with `burmaldaholic:`.
 *  - Trades: there is no trade event; a trade is detected as an emerald-count change in the
 *    same tick as another item change while a villager / wandering trader is within 6 blocks.
 */
import {
  BlockVolume,
  type Entity,
  EntityInitializationCause,
  EntityComponentTypes,
  type EntityInventoryComponent,
  GameMode,
  ItemComponentTypes,
  type ItemEnchantableComponent,
  type Player,
  system,
  world,
} from '@minecraft/server';
import type { ConfigService } from './config';
import type { Economy } from './economy';
import { difficultyMultiplier, mcDay, mobReward, mobRewardKey, oreInfo, orePays, pickaxeTier, recordKill, tradeReward } from './logic/economy-math';
import { t } from './logic/rawtext';
import { ChunkedList, readJson, worldJson, worldTick, writeJson } from './store';

/** Tag a spawned mob with this to make it pay nothing when killed. */
export const NO_REWARD_TAG = 'burmaldaholic_core_no_reward';
const SPAWNER_TAG = 'burmaldaholic_core_spawner';
const DRAGON_PROP = 'burmaldaholic:core.dragon_killed';
const TRADE_PROP = 'burmaldaholic:core.trade_day';

export class Earning {
  private readonly kills = new Map<string, number[]>();
  private readonly debris: ChunkedList;
  private readonly emeralds = new Map<string, number>();

  constructor(
    private readonly economy: Economy,
    private readonly config: ConfigService,
    private readonly active: () => boolean,
  ) {
    this.debris = new ChunkedList('burmaldaholic:core.placed_debris', 65_536);
  }

  start(): void {
    world.afterEvents.playerBreakBlock.subscribe((e) => this.guard(() => this.onBreak(e.player, e.brokenBlockPermutation.type.id, e.itemStackBeforeBreak, e.dimension.id, e.block.location)));
    world.afterEvents.playerPlaceBlock.subscribe((e) => this.guard(() => {
      if (e.block.typeId === 'minecraft:ancient_debris') this.debris.add(posKey(e.dimension.id, e.block.location));
    }));
    world.afterEvents.entityDie.subscribe((e) => this.guard(() => this.onDie(e.deadEntity, e.damageSource.damagingEntity)));
    world.afterEvents.entitySpawn.subscribe((e) => this.guard(() => this.tagSpawner(e.entity, e.cause)));
    system.runInterval(() => this.guard(() => this.pollTrades()), 10);
  }

  private guard(fn: () => void): void {
    if (!this.active()) return;
    try {
      fn();
    } catch (err) {
      console.warn('[burmaldaholic:core.earning]', err);
    }
  }

  // ---- ores ---------------------------------------------------------------------------

  private onBreak(player: Player, blockId: string, tool: { typeId: string; getComponent(id: string): unknown } | undefined, dim: string, loc: { x: number; y: number; z: number }): void {
    const ore = oreInfo(blockId);
    if (!ore) return;
    const placed = ore.canonical === 'ancient_debris' && this.debris.delete(posKey(dim, loc));
    const ench = tool?.getComponent(ItemComponentTypes.Enchantable) as ItemEnchantableComponent | undefined;
    const silk = !!ench?.hasEnchantment('silk_touch');
    const pays = orePays({ minTier: ore.minTier, toolTier: pickaxeTier(tool?.typeId), silkTouch: silk, creative: player.getGameMode() === GameMode.Creative, placedDebris: placed });
    const amount = pays ? this.config.int(ore.key) : 0;
    if (amount > 0) this.economy.earn(player, amount, t(`tile.${ore.canonical}.name`), 'core.earn.ore');
  }

  // ---- mobs ---------------------------------------------------------------------------

  private onDie(dead: Entity, killer: Entity | undefined): void {
    const player = killerPlayer(killer);
    if (!player || dead.typeId === 'minecraft:player') return;
    if (dead.hasTag(NO_REWARD_TAG) || dead.getTags().some((x) => x.startsWith('burmaldaholic') && x !== SPAWNER_TAG)) return;
    if (dead.hasTag(SPAWNER_TAG) && !this.config.bool('economy.mob.spawnerRewards')) return;
    if (player.getGameMode() === GameMode.Creative) return;
    let size: number | undefined;
    if (dead.typeId === 'minecraft:slime' || dead.typeId === 'minecraft:magma_cube') {
      size = (dead.getComponent(EntityComponentTypes.Variant) as { value: number } | undefined)?.value;
    }
    const dragonBefore = dead.typeId === 'minecraft:ender_dragon' && worldJson.read<boolean>(DRAGON_PROP, false);
    const key = mobRewardKey(dead.typeId, { size, dragonKilledBefore: dragonBefore });
    if (dead.typeId === 'minecraft:ender_dragon') worldJson.write(DRAGON_PROP, true);
    if (!key) return;
    const now = worldTick();
    const id = `${player.id}|${dead.typeId}`;
    let list = this.kills.get(id);
    if (!list) this.kills.set(id, (list = []));
    const n = recordKill(list, now, this.config.int('economy.mob.windowTicks'));
    const diff = String(world.getDifficulty()).toLowerCase() as 'peaceful' | 'easy' | 'normal' | 'hard';
    const amount = mobReward(this.config.int(key), n, difficultyMultiplier(diff, world.isHardcore, this.config.num('economy.mob.hardMultiplier')), {
      full: this.config.int('economy.mob.fullRewardKills'),
      reduced: this.config.int('economy.mob.reducedRewardKills'),
      factor: this.config.num('economy.mob.reducedRewardFactor'),
    });
    if (amount > 0) this.economy.earn(player, amount, t(`entity.${dead.typeId.replace(/^minecraft:/, '')}.name`), 'core.earn.mob');
  }

  private tagSpawner(e: Entity, cause: EntityInitializationCause): void {
    if (cause !== EntityInitializationCause.Spawned || !e.isValid || e.typeId === 'minecraft:player' || e.typeId === 'minecraft:item') return;
    const { x, y, z } = e.location;
    const volume = new BlockVolume({ x: x - 5, y: y - 3, z: z - 5 }, { x: x + 5, y: y + 3, z: z + 5 });
    if (e.dimension.containsBlock(volume, { includeTypes: ['minecraft:mob_spawner', 'minecraft:trial_spawner'] })) e.addTag(SPAWNER_TAG);
  }

  // ---- trades -------------------------------------------------------------------------

  private pollTrades(): void {
    for (const p of world.getAllPlayers()) {
      const inv = (p.getComponent(EntityComponentTypes.Inventory) as EntityInventoryComponent | undefined)?.container;
      if (!inv) continue;
      let em = 0;
      let other = 0;
      for (let i = 0; i < inv.size; i++) {
        const it = inv.getItem(i);
        if (!it) continue;
        if (it.typeId === 'minecraft:emerald') em += it.amount;
        else other += it.amount;
      }
      const sig = em * 100_000 + other;
      const prev = this.emeralds.get(p.id);
      this.emeralds.set(p.id, sig);
      if (prev === undefined) continue;
      const dEm = em - Math.floor(prev / 100_000);
      const dOther = other - (prev % 100_000);
      if (dEm === 0 || dOther === 0 || Math.sign(dEm) === Math.sign(dOther)) continue;
      const traders = p.dimension.getEntities({ location: p.location, maxDistance: 6, families: ['villager', 'wandering_trader'] });
      if (!traders.length) continue;
      this.creditTrade(p, Math.abs(dEm));
    }
  }

  private creditTrade(p: Player, emeralds: number): void {
    const day = mcDay(worldTick());
    const st = readJson<{ day: number; earned: number }>(p, TRADE_PROP, { day, earned: 0 });
    const earnedToday = st.day === day ? st.earned : 0;
    const c = this.config;
    const amount = tradeReward(emeralds, { base: c.int('economy.trade.perTradeBase'), perEmerald: c.int('economy.trade.perEmerald'), perTradeCap: c.int('economy.trade.perTradeCap'), dailyCap: c.int('economy.trade.dailyCap') }, earnedToday);
    if (amount <= 0) return;
    const got = this.economy.earn(p, amount, t('msg.burmaldaholic.core.source.trade'), 'core.earn.trade');
    writeJson(p, TRADE_PROP, { day, earned: earnedToday + got });
  }
}

const posKey = (dim: string, l: { x: number; y: number; z: number }): string => `${dim}|${l.x},${l.y},${l.z}`;

function killerPlayer(e: Entity | undefined): Player | undefined {
  if (!e?.isValid) return undefined;
  if (e.typeId === 'minecraft:player') return e as Player;
  const owner = (e.getComponent(EntityComponentTypes.Projectile) as { owner?: Entity } | undefined)?.owner;
  return owner?.typeId === 'minecraft:player' ? (owner as Player) : undefined;
}
