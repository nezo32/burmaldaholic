/**
 * Player-owned casinos (GAME_DESIGN §18.2): Casino Charter claim, owned tables, owner bankroll
 * (core bankroll account), house P&L routing, reservation/insolvency, protection.
 *
 * Integration with the core tables framework:
 *  - Games that use `MultiplayerApi.houseFor(table.key)` bank rounds in the owner's bankroll
 *    with core's reservation rule (`wagers.place({house, worstCase})`).
 *  - Games that keep `house: BANK` are still routed: on `wagers.onSettled` the house result of
 *    a chip round played at an owned table is moved bank <-> bankroll (without an up-front
 *    reservation; a loss is capped at the unreserved bankroll, the rest is logged).
 *  - Seating is guarded for every game through `world.beforeEvents.playerInteractWithBlock`
 *    (owner can't play, table closed, house broke, inactive table) and `checkTable` for canJoin.
 */
import { type Block, type Dimension, type Player, type StartupEvent, type Vector3, ItemStack, system, world } from '@minecraft/server';
import {
  BANK,
  type HouseRef,
  type ModuleContext,
  type Raw,
  type SettledEvent,
  type TableLimits,
  GAME_IDS,
  type GameId,
  HudPriority,
  TABLE_COMPONENT,
  chips,
  gameLabel,
  giveItems,
  isCasinoEnabled,
  isOperator,
  t,
  unit,
  worldTick,
} from '../core';
import { tableKeyOf } from '../core/logic/sessions';
import type { MultiplayerApi, OwnedTableInfo } from './api';
import {
  type Casino,
  type ExposedTable,
  type OwnedTable,
  applyOwnerLimits,
  casinoAt,
  checkClaim,
  dayOf,
  effectiveMin,
  inClaim,
  isBroke,
  mayBreak,
  newOwnedTable,
  parseTableKey,
  recordRake,
  recordRound,
  routeHouseResult,
  worstCaseAt,
  worstCasePerChip,
} from './logic';
import { CasinoStore } from './store';

export const CHARTER_ID = 'burmaldaholic:casino_charter';
export const CHARTER_COMPONENT = 'burmaldaholic:casino_charter';
/** Ticks after leaving a table during which a settled bank round is still routed to it. */
const RECENT_TABLE_TICKS = 100;
/** Vertical half-height scanned by "Link unlinked tables in range". */
const LINK_SCAN_HALF_HEIGHT = 8;

type TableParams = { game?: string; variant?: string };

export class Ownership implements MultiplayerApi {
  readonly store = new CasinoStore();
  private ctx!: ModuleContext;
  private readonly overrides: Record<string, number> = {};
  private readonly recent = new Map<string, { key: string; tick: number }>();
  private readonly inside = new Map<string, string | undefined>();

  // ---- lifecycle -----------------------------------------------------------------------

  /** Startup (early execution): the charter block custom component. */
  registerComponent(event: StartupEvent): void {
    event.blockComponentRegistry.registerCustomComponent(CHARTER_COMPONENT, {
      beforeOnPlayerPlace: (e) => {
        if (!this.ctx) return;
        const player = e.player;
        const err = player ? this.claimError(player, e.block.dimension.id, e.block.location) : t('gui.burmaldaholic.error.no_permission');
        if (!err) return;
        e.cancel = true;
        if (player) system.run(() => player.isValid && player.sendMessage(err));
      },
      onPlayerInteract: (e) => {
        const player = e.player;
        const block = e.block;
        if (!player || !this.ctx) return;
        system.run(() => this.ctx.guard(() => void this.onCharterUse(player, block))());
      },
    });
  }

  start(ctx: ModuleContext, openCharter: (p: Player, c: Casino) => void | Promise<void>): void {
    this.ctx = ctx;
    this.openCharterUi = openCharter;
    this.store.load();

    world.afterEvents.playerPlaceBlock.subscribe(ctx.guard((e) => this.onPlace(e.player, e.block)));
    world.beforeEvents.playerBreakBlock.subscribe(
      ctx.guard((e) => {
        const err = this.breakError(e.player, e.block);
        if (!err) return;
        e.cancel = true;
        const p = e.player;
        system.run(() => p.isValid && p.sendMessage(err));
      }),
    );
    world.afterEvents.playerBreakBlock.subscribe(
      ctx.guard((e) => this.onBroken(e.block.dimension.id, e.block.location, e.brokenBlockPermutation.type.id, e.player)),
    );
    world.beforeEvents.explosion.subscribe(
      ctx.guard((e) => {
        if (!this.ctx.config.bool('ownership.explosionProof')) return;
        const blocks = e.getImpactedBlocks();
        const keep = blocks.filter((b) => !this.isProtectedBlock(b));
        if (keep.length !== blocks.length) e.setImpactedBlocks(keep);
      }),
    );
    world.beforeEvents.playerInteractWithBlock.subscribe(
      ctx.guard((e) => {
        const key = tableKeyOf(e.block.dimension.id, e.block.location);
        if (!this.store.tables.has(key)) return;
        const player = e.player;
        const seatedHere = this.ctx.tables.sessionOf(player)?.table.key === key;
        const err = seatedHere ? undefined : this.checkTable(player, key);
        if (err) {
          e.cancel = true;
          if (e.isFirstEvent) system.run(() => player.isValid && player.sendMessage(err));
          return;
        }
        this.recent.set(player.id, { key, tick: system.currentTick });
      }),
    );
    ctx.wagers.onSettled((e) => this.onSettled(e));
    world.afterEvents.playerSpawn.subscribe(
      ctx.guard((e) => {
        if (e.initialSpawn) this.onJoin(e.player);
      }),
    );
    world.afterEvents.playerLeave.subscribe((e) => {
      this.recent.delete(e.playerId);
      this.inside.delete(e.playerId);
    });
    system.runInterval(ctx.guard(() => this.tickPlayers()), 20);
    system.runInterval(ctx.guard(() => this.tickCasinos()), 100);
    for (const p of world.getAllPlayers()) this.onJoin(p);
  }

  private openCharterUi: (p: Player, c: Casino) => void | Promise<void> = () => {};

  private enabled(): boolean {
    return isCasinoEnabled() && this.ctx.config.bool('ownership.enabled');
  }

  // ---- MultiplayerApi --------------------------------------------------------------------

  houseFor(tableKey: string): HouseRef {
    const c = this.activeCasinoOf(tableKey);
    return c ? { kind: 'bankroll', id: c.id } : BANK;
  }

  checkTable(player: Player, tableKey: string): Raw | undefined {
    if (!this.enabled()) return undefined;
    const tbl = this.store.tables.get(tableKey);
    if (!tbl) return undefined;
    const c = this.store.casino(tbl.casinoId);
    if (!c) return t('msg.burmaldaholic.multiplayer.table_inactive');
    if (c.ownerId === player.id) return t('gui.burmaldaholic.error.owner_cannot_play');
    if (!tbl.open) return t('gui.burmaldaholic.error.table_closed');
    if (c.broke) return t('gui.burmaldaholic.error.house_broke');
    return undefined;
  }

  limitsFor(tableKey: string, base: TableLimits): TableLimits {
    const c = this.activeCasinoOf(tableKey);
    return c ? applyOwnerLimits(base, this.store.tables.get(tableKey)) : base;
  }

  tableInfo(tableKey: string): OwnedTableInfo | undefined {
    const tbl = this.store.tables.get(tableKey);
    const c = this.store.casino(tbl?.casinoId);
    if (!tbl || !c) return undefined;
    return { casinoId: c.id, ownerId: c.ownerId, ownerName: c.ownerName, open: tbl.open, min: tbl.min, max: tbl.max, bots: tbl.bots, broke: !!c.broke };
  }

  botsAllowed(tableKey: string): boolean {
    return this.tableInfo(tableKey)?.bots ?? true;
  }

  recordRake(tableKey: string, amount: number): void {
    const c = this.activeCasinoOf(tableKey);
    if (!c || !(amount > 0)) return;
    this.store.saveStats(c.id, recordRake(this.store.stats(c.id), dayOf(worldTick()), amount));
    this.checkSolvency(c);
  }

  setWorstCase(key: string, perChip: number): void {
    if (Number.isFinite(perChip) && perChip >= 0) this.overrides[key] = perChip;
  }

  worstCaseFor(game: string, bet: number, variant?: string): number {
    return worstCaseAt(worstCasePerChip(game, variant, this.overrides), bet);
  }

  isInsideClaim(dimension: string | { readonly id: string }, location: Vector3): boolean {
    return !!casinoAt(this.store.casinos, dimId(dimension), location);
  }

  ownerAt(dimension: string | { readonly id: string }, location: Vector3): { id: string; name: string } | undefined {
    const c = casinoAt(this.store.casinos, dimId(dimension), location);
    return c && { id: c.ownerId, name: c.ownerName };
  }

  casinoAt(dimensionId: string, location: Vector3): { id: string; ownerId: string; ownerName: string } | undefined {
    const c = casinoAt(this.store.casinos, dimensionId, location);
    return c && { id: c.id, ownerId: c.ownerId, ownerName: c.ownerName };
  }

  // ---- queries used by the charter UI ------------------------------------------------------

  activeCasinoOf(tableKey: string): Casino | undefined {
    if (!this.enabled()) return undefined;
    return this.store.casino(this.store.tables.get(tableKey)?.casinoId);
  }

  isOwner(p: Player, c: Casino): boolean {
    return c.ownerId === p.id;
  }

  /** Every open owned table with its effective minimum (insolvency rule input). */
  exposure(c: Casino): ExposedTable[] {
    return this.store.tablesOf(c.id).map(([, tbl]) => ({ game: tbl.game, variant: tbl.variant, minBet: effectiveMin(tbl), open: tbl.open }));
  }

  /** Global cap for owner max bets: the highest VIP tier max. */
  globalMax(): number {
    return this.ctx.config.int('vip.maxBet.netherite');
  }

  tableLabel(tbl: Pick<OwnedTable, 'game' | 'blockTypeId'>): Raw {
    if ((GAME_IDS as readonly string[]).includes(tbl.game)) return gameLabel(tbl.game as GameId);
    if (tbl.blockTypeId) {
      try {
        return { translate: new ItemStack(tbl.blockTypeId, 1).localizationKey };
      } catch {
        /* not an item */
      }
    }
    return t('gui.burmaldaholic.charter.tables');
  }

  // ---- claim / charter -----------------------------------------------------------------

  private claimError(player: Player, dimensionId: string, loc: Vector3): Raw | undefined {
    if (!isCasinoEnabled()) return t('gui.burmaldaholic.error.casino_off');
    const cfg = this.ctx.config;
    const check = checkClaim(this.store.casinos, player.id, dimensionId, loc, {
      enabled: cfg.bool('ownership.enabled'),
      radius: cfg.int('ownership.claimRadius'),
      maxPerPlayer: cfg.int('ownership.maxPerPlayer'),
      spawn: world.getDefaultSpawnLocation(),
    });
    if (!check.ok) {
      if (check.reason === 'disabled') return t('msg.burmaldaholic.multiplayer.casinos_disabled');
      return t(check.reason === 'limit' ? 'msg.burmaldaholic.multiplayer.charter_limit' : 'msg.burmaldaholic.multiplayer.charter_overlap');
    }
    const fee = cfg.int('ownership.licenseFee');
    if (this.ctx.economy.balance(player) < fee) return t('msg.burmaldaholic.multiplayer.charter_fee_missing', chips(fee));
    return undefined;
  }

  private onPlace(player: Player, block: Block): void {
    if (block.typeId === CHARTER_ID) return this.claim(player, block);
    const params = tableParams(block);
    if (!params?.game || !this.enabled()) return;
    const c = casinoAt(this.store.casinos, block.dimension.id, block.location);
    if (!c || c.ownerId !== player.id) return;
    this.link(c, block, params, player);
    this.checkSolvency(c);
  }

  private claim(player: Player, block: Block): void {
    const refuse = (err: Raw) => {
      player.sendMessage(err);
      block.setType('minecraft:air');
      giveItems(player, CHARTER_ID, 1);
    };
    const err = this.claimError(player, block.dimension.id, block.location);
    if (err) return refuse(err);
    const fee = this.ctx.config.int('ownership.licenseFee');
    if (!this.ctx.economy.debit(player, fee, 'multiplayer.license')) return refuse(t('msg.burmaldaholic.multiplayer.charter_fee_missing', chips(fee)));
    const { x, y, z } = block.location;
    const c: Casino = {
      id: this.store.nextId(),
      ownerId: player.id,
      ownerName: player.name,
      dimension: block.dimension.id,
      x,
      y,
      z,
      radius: this.ctx.config.int('ownership.claimRadius'),
      created: worldTick(),
    };
    this.store.addCasino(c);
    this.relinkInactive(c);
    player.sendMessage(t('msg.burmaldaholic.multiplayer.charter_placed', chips(fee), unit('block', c.radius)));
    this.ctx.log.info(`casino ${c.id} claimed by ${player.name} at ${c.dimension} ${x},${y},${z}`);
    this.checkSolvency(c, true);
  }

  private async onCharterUse(player: Player, block: Block): Promise<void> {
    const c = this.casinoOfCharter(block.dimension.id, block.location);
    if (!c) return;
    if (!this.isOwner(player, c) && !isOperator(player)) return player.sendMessage(t('msg.burmaldaholic.multiplayer.charter_foreign', c.ownerName));
    await this.openCharterUi(player, c);
  }

  casinoOfCharter(dimensionId: string, l: Vector3): Casino | undefined {
    return this.store.casinos.find((c) => c.dimension === dimensionId && c.x === l.x && c.y === l.y && c.z === l.z);
  }

  /** Close a casino: tables go inactive, the bankroll is paid out once open rounds settled. */
  closeCasino(c: Casino, reason: string): void {
    const keys = this.store.tablesOf(c.id).map(([k]) => k);
    for (const k of keys) {
      const tbl = this.store.tables.get(k);
      if (tbl) tbl.casinoId = undefined;
    }
    this.store.saveTables(undefined);
    this.store.removeCasino(c);
    for (const k of keys) this.ctx.tables.closeTable(k, 'broken');
    this.ctx.log.info(`casino ${c.id} of ${c.ownerName} closed (${reason})`);
    system.run(() => this.processClosing());
  }

  /** Pay out closed bankrolls whose open rounds have settled. */
  private processClosing(): void {
    for (const cl of [...this.store.closing]) {
      if (this.ctx.economy.bankroll(cl.id).reserved > 0) continue;
      const amount = this.ctx.economy.closeBankroll(cl.id);
      this.store.doneClosing(cl.id);
      const owner = world.getAllPlayers().find((p) => p.id === cl.ownerId);
      if (owner) this.payOwner(owner, amount);
      else if (amount > 0) this.store.addPayout(cl.ownerId, amount);
    }
  }

  private payOwner(owner: Player, amount: number): void {
    if (amount > 0) this.ctx.economy.credit(owner, amount, 'multiplayer.charter_removed');
    owner.sendMessage(t('msg.burmaldaholic.multiplayer.charter_removed', chips(amount)));
  }

  // ---- tables ----------------------------------------------------------------------------

  private link(c: Casino, block: Block, params: TableParams, by?: Player): boolean {
    const key = tableKeyOf(block.dimension.id, block.location);
    const cur = this.store.tables.get(key);
    if (cur?.casinoId === c.id) return false;
    if (cur?.casinoId && this.store.casino(cur.casinoId)) return false;
    const prev = cur?.casinoId;
    const tbl = cur ? { ...cur, casinoId: c.id, ownerId: c.ownerId, game: params.game ?? cur.game, variant: params.variant } : newOwnedTable(c.id, c.ownerId, params.game ?? '', params.variant, block.typeId);
    this.store.tables.set(key, tbl);
    this.store.saveTables(c.id, prev);
    by?.sendMessage(t('msg.burmaldaholic.multiplayer.table_linked', this.tableLabel(tbl)));
    return true;
  }

  /** Inactive tables of the owner inside a new claim link back automatically. */
  private relinkInactive(c: Casino): void {
    let changed = false;
    for (const [k, tbl] of this.store.tables) {
      if (tbl.casinoId || tbl.ownerId !== c.ownerId) continue;
      const pos = parseTableKey(k);
      if (!pos || !inClaim(c, pos.dimension, pos)) continue;
      tbl.casinoId = c.id;
      changed = true;
    }
    if (changed) this.store.saveTables(c.id, undefined);
  }

  /**
   * "Link unlinked tables in range": inactive tables inside the claim, plus every table block
   * found within ±8 blocks of the charter height (scanned over several ticks with runJob).
   */
  linkInRange(owner: Player, c: Casino): Promise<number> {
    this.relinkInactive(c);
    return new Promise((resolve) => system.runJob(this.scanJob(owner, c, resolve)));
  }

  private *scanJob(owner: Player, c: Casino, done: (linked: number) => void): Generator<void, void, void> {
    const dim = world.getDimension(c.dimension);
    const range = dim.heightRange;
    const r = c.radius;
    let linked = 0;
    for (let dx = -r; dx <= r; dx++) {
      for (let dz = -r; dz <= r; dz++) {
        if (dx * dx + dz * dz > r * r) continue;
        for (let dy = -LINK_SCAN_HALF_HEIGHT; dy <= LINK_SCAN_HALF_HEIGHT; dy++) {
          const y = c.y + dy;
          if (y < range.min || y >= range.max) continue;
          const b = safeBlock(dim, { x: c.x + dx, y, z: c.z + dz });
          const params = b && tableParams(b);
          if (b && params?.game && this.store.casino(c.id) && this.link(c, b, params, owner.isValid ? owner : undefined)) linked++;
        }
        yield;
      }
    }
    done(linked);
  }

  saveTable(key: string, tbl: OwnedTable): void {
    this.store.tables.set(key, tbl);
    this.store.saveTables(tbl.casinoId);
    if (!tbl.open) this.ctx.tables.closeTable(key, 'broken');
    const c = this.store.casino(tbl.casinoId);
    if (c) this.checkSolvency(c);
  }

  // ---- protection ----------------------------------------------------------------------

  private isProtectedBlock(b: Block): boolean {
    if (b.typeId === CHARTER_ID) return !!this.casinoOfCharter(b.dimension.id, b.location);
    const tbl = this.store.tables.get(tableKeyOf(b.dimension.id, b.location));
    return !!tbl && !!this.store.casino(tbl.casinoId);
  }

  private breakError(player: Player, block: Block): Raw | undefined {
    if (block.typeId === CHARTER_ID) {
      const c = this.casinoOfCharter(block.dimension.id, block.location);
      if (!c) return undefined;
      const ok = mayBreak({ isOwner: this.isOwner(player, c), isOperator: isOperator(player), protect: true, isCharter: true });
      return ok ? undefined : t('msg.burmaldaholic.multiplayer.charter_foreign', c.ownerName);
    }
    const tbl = this.store.tables.get(tableKeyOf(block.dimension.id, block.location));
    const c = this.store.casino(tbl?.casinoId);
    if (!c) return undefined;
    const ok = mayBreak({
      isOwner: this.isOwner(player, c),
      isOperator: isOperator(player),
      protect: this.ctx.config.bool('ownership.protectTables'),
      isCharter: false,
    });
    return ok ? undefined : t('msg.burmaldaholic.multiplayer.table_protected', c.ownerName);
  }

  private onBroken(dimensionId: string, loc: Vector3, typeId: string, player?: Player): void {
    if (typeId === CHARTER_ID) {
      const c = this.casinoOfCharter(dimensionId, loc);
      if (c) this.closeCasino(c, `charter broken by ${player?.name ?? '?'}`);
      return;
    }
    const key = tableKeyOf(dimensionId, loc);
    const tbl = this.store.tables.get(key);
    if (!tbl) return;
    this.store.tables.delete(key);
    this.store.saveTables(tbl.casinoId);
    const c = this.store.casino(tbl.casinoId);
    if (c) this.checkSolvency(c);
  }

  // ---- money -----------------------------------------------------------------------------

  private onSettled(e: SettledEvent): void {
    if (!this.enabled() || !e.houseBanked) return;
    const day = dayOf(worldTick());
    if (e.house.kind === 'bankroll') {
      const c = this.store.casino(e.house.id);
      if (!c) return;
      this.store.saveStats(c.id, recordRound(this.store.stats(c.id), day, e.staked, e.totalReturn));
      this.checkSolvency(c);
      return;
    }
    // Fallback: the game let the bank settle a round played at an owned table.
    if (e.stakeKind !== 'chips' || !e.player.isValid) return;
    const key = this.tableOf(e.player);
    const c = key ? this.activeCasinoOf(key) : undefined;
    if (!c) return;
    const r = routeHouseResult(this.ctx.economy.bankroll(c.id), e.staked, e.totalReturn);
    if (r.bankrollDelta !== 0) {
      const ok = this.ctx.economy.transact(
        [
          { account: { bankroll: c.id }, delta: r.bankrollDelta },
          { account: 'bank', delta: -r.bankrollDelta },
        ],
        'multiplayer.route',
      );
      if (!ok) this.ctx.log.warn(`casino ${c.id}: could not route ${r.bankrollDelta}`);
    }
    if (r.bankCovered > 0) this.ctx.log.warn(`casino ${c.id}: bank covered ${r.bankCovered} of an unreserved ${e.game} loss (game does not use houseFor)`);
    this.store.saveStats(c.id, recordRound(this.store.stats(c.id), day, e.staked, e.totalReturn));
    this.checkSolvency(c);
  }

  /** The owned table a player is (or was just) playing at. */
  private tableOf(p: Player): string | undefined {
    const s = this.ctx.tables.sessionOf(p);
    if (s) return s.table.key;
    const r = this.recent.get(p.id);
    return r && system.currentTick - r.tick <= RECENT_TABLE_TICKS ? r.key : undefined;
  }

  /** Re-evaluate the insolvency rule; notify the owner and close seats on a change. */
  checkSolvency(c: Casino, quiet = false): void {
    const broke = isBroke(this.ctx.economy.bankroll(c.id), this.exposure(c), this.overrides);
    if (broke === !!c.broke) return;
    c.broke = broke;
    this.store.saveRegistry();
    if (broke) for (const [k] of this.store.tablesOf(c.id)) system.run(() => this.ctx.tables.closeTable(k, 'broken'));
    if (quiet) return;
    const owner = world.getAllPlayers().find((p) => p.id === c.ownerId);
    owner?.sendMessage(t(broke ? 'msg.burmaldaholic.multiplayer.casino_broke' : 'msg.burmaldaholic.multiplayer.casino_reopened'));
  }

  // ---- periodic --------------------------------------------------------------------------

  private onJoin(p: Player): void {
    const owed = this.store.takePayout(p.id);
    if (owed > 0) this.payOwner(p, owed);
    let renamed = false;
    for (const c of this.store.ownedBy(p.id)) {
      if (c.ownerName !== p.name) {
        c.ownerName = p.name;
        renamed = true;
      }
      if (c.broke) p.sendMessage(t('msg.burmaldaholic.multiplayer.casino_broke'));
    }
    if (renamed) this.store.saveRegistry();
  }

  private tickPlayers(): void {
    const now = system.currentTick;
    for (const p of world.getAllPlayers()) {
      const s = this.ctx.tables.sessionOf(p);
      const r = this.recent.get(p.id);
      if (s && r && s.table.key === r.key) r.tick = now;
      if (!this.enabled()) continue;
      const c = casinoAt(this.store.casinos, p.dimension.id, p.location);
      const prev = this.inside.get(p.id);
      if (c?.id !== prev) {
        this.inside.set(p.id, c?.id);
        if (c && c.ownerId !== p.id) this.ctx.hud.actionbar(p, 'multiplayer.casino', t('msg.burmaldaholic.multiplayer.entered_casino', c.ownerName), HudPriority.ambient + 5, 60);
      }
    }
  }

  private tickCasinos(): void {
    this.processClosing();
    for (const c of [...this.store.casinos]) {
      const dim = world.getDimension(c.dimension);
      const b = safeBlock(dim, c);
      if (b && b.typeId !== CHARTER_ID) {
        this.closeCasino(c, 'charter block missing');
        continue;
      }
      this.checkSolvency(c);
    }
    // Drop records of table blocks that were removed without a player break (explosion, pistons, commands).
    const gone: [string, OwnedTable][] = [];
    for (const [k, tbl] of this.store.tables) {
      const pos = parseTableKey(k);
      if (!pos) continue;
      const b = safeBlock(world.getDimension(pos.dimension), pos);
      if (b && !tableParams(b)?.game) gone.push([k, tbl]);
    }
    for (const [k, tbl] of gone) {
      this.store.tables.delete(k);
      this.store.saveTables(tbl.casinoId);
    }
  }
}

/** The block's `burmaldaholic:table` params, if it is a table block. */
export function tableParams(block: Block): TableParams | undefined {
  try {
    const comp = block.getComponent(TABLE_COMPONENT);
    const p = comp?.customComponentParameters.params as TableParams | undefined;
    return p && typeof p === 'object' ? p : undefined;
  } catch {
    return undefined;
  }
}

const dimId = (d: string | { readonly id: string }): string => (typeof d === 'string' ? d : d.id);

/** getBlock that returns undefined for unloaded chunks or out-of-range positions. */
function safeBlock(dim: Dimension, l: Vector3): Block | undefined {
  try {
    return dim.getBlock({ x: l.x, y: l.y, z: l.z });
  } catch {
    return undefined;
  }
}
