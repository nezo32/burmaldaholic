/**
 * Generic table framework. A game block (or item / NPC) is a "table"; using it seats the
 * player and hands a TableSession to the game, which then drives its own form flow.
 *
 *  - Block side: any block with the custom component `burmaldaholic:table` and params
 *    `{ "game": "<handler id>", "variant": "<optional>" }` (see docs/architecture/bedrock.md).
 *    Core registers the component; games only `ctx.tables.register({...})`.
 *  - Session side: one session per player; seats per table; per-session tick timers; leaves
 *    on "Leave", walking away (> maxDistance), disconnect, the block being broken or casino
 *    mode turning off. The game's `onLeave` runs in every case (auto-complete the round there).
 */
import { type Block, type Dimension, type Player, type StartupEvent, type Vector3, system, world } from '@minecraft/server';
import { uiManager } from '@minecraft/server-ui';
import { isCasinoEnabled } from './casino';
import type { ConfigService } from './config';
import { type Raw, t } from './logic/rawtext';
import { SeatRegistry, tableKeyOf, within } from './logic/sessions';
import { createLogger } from './log';

const log = createLogger('core.tables');
export const TABLE_COMPONENT = 'burmaldaholic:table';

export type LeaveReason = 'leave' | 'distance' | 'disconnect' | 'broken' | 'casino_off' | 'replaced';

export interface TableRef {
  /** stable key `<dimension>|x,y,z` (use it as the game-state key) */
  readonly key: string;
  readonly game: string;
  /** block param `variant` (e.g. 'high_roller') */
  readonly variant?: string;
  readonly dimension: Dimension;
  readonly location: Vector3;
  /** the block type id, when opened from a block */
  readonly blockTypeId?: string;
}

export interface TableSession {
  readonly player: Player;
  readonly playerId: string;
  readonly table: TableRef;
  /** 1-based seat number */
  readonly seat: number;
  /** free-form per-session game state */
  data: Record<string, unknown>;
  /** Run `fn` after `ticks` unless the session ends first; same id replaces the timer. */
  setTimer(id: string, ticks: number, fn: () => void): void;
  clearTimer(id: string): void;
  /** Leave the table (runs the game's onLeave with reason 'leave'). */
  leave(): void;
  isActive(): boolean;
  /** Close any open form of this player (timer expiry: apply the default action, then re-show). */
  closeForms(): void;
}

export interface TableHandler {
  /** handler id == block param `game` */
  id: string;
  /** seats per table (default 1) */
  seats?: number | ((table: TableRef) => number);
  /** walk-away distance; default config multiplayer.tableLeaveDistance */
  maxDistance?: number;
  /** Player used the table (first time or again while seated): show the game's forms. */
  onOpen(session: TableSession, rejoined: boolean): void | Promise<void>;
  /** Session ended for any reason. Auto-complete / refund open rounds here. */
  onLeave?(session: TableSession, reason: LeaveReason): void;
  /** Optional extra check before seating (VIP tier, owner-can't-play...). Return an error to refuse. */
  canJoin?(player: Player, table: TableRef): Raw | undefined;
}

interface SessionImpl extends TableSession {
  timers: Map<string, number>;
  active: boolean;
  handler: TableHandler;
}

export class Tables {
  private readonly handlers = new Map<string, TableHandler>();
  private readonly seats = new SeatRegistry();
  private readonly sessions = new Map<string, SessionImpl>();

  constructor(private readonly config: ConfigService) {}

  /** Register a game's table handler (onStartup or onWorldLoad). */
  register(h: TableHandler): void {
    if (this.handlers.has(h.id)) throw new Error(`table handler '${h.id}' already registered`);
    this.handlers.set(h.id, h);
  }

  /** Core: register the block custom component during startup. */
  registerComponent(event: StartupEvent): void {
    event.blockComponentRegistry.registerCustomComponent(TABLE_COMPONENT, {
      onPlayerInteract: (e, p) => {
        if (!e.player) return;
        const params = (p.params ?? {}) as { game?: string; variant?: string };
        const player = e.player;
        const block = e.block;
        system.run(() => this.fromBlock(player, block, params));
      },
      onPlayerBreak: (e) => {
        const key = tableKeyOf(e.dimension.id, e.block.location);
        system.run(() => this.closeTable(key, 'broken'));
      },
    });
  }

  private fromBlock(player: Player, block: Block, params: { game?: string; variant?: string }): void {
    if (!params.game) return log.warn(`${block.typeId} has ${TABLE_COMPONENT} without a "game" param`);
    this.open(player, {
      key: tableKeyOf(block.dimension.id, block.location),
      game: params.game,
      variant: params.variant,
      dimension: block.dimension,
      location: block.center(),
      blockTypeId: block.typeId,
    });
  }

  /**
   * Seat `player` at a table and call the handler's onOpen. Also usable from items and NPCs:
   * build a TableRef (e.g. key `npc:<entity id>`) and call `ctx.tables.open(player, ref)`.
   */
  open(player: Player, table: TableRef): void {
    if (!isCasinoEnabled()) return player.sendMessage(t('gui.burmaldaholic.error.casino_off'));
    const h = this.handlers.get(table.game);
    if (!h) return player.sendMessage(t('gui.burmaldaholic.error.disabled'));
    const existing = this.sessions.get(player.id);
    if (!existing || existing.table.key !== table.key) {
      const refuse = h.canJoin?.(player, table);
      if (refuse) return player.sendMessage(refuse);
    }
    const seats = typeof h.seats === 'function' ? h.seats(table) : (h.seats ?? 1);
    const j = this.seats.join(player.id, table.key, seats);
    if (!j.ok) return player.sendMessage(t(j.reason === 'busy' ? 'gui.burmaldaholic.error.busy' : 'gui.burmaldaholic.error.table_full'));
    let s = this.sessions.get(player.id);
    if (!s) {
      s = this.makeSession(player, table, j.seat, h);
      this.sessions.set(player.id, s);
    }
    this.safe(() => h.onOpen(s!, j.rejoined), `${h.id}.onOpen`);
  }

  private makeSession(player: Player, table: TableRef, seat: number, handler: TableHandler): SessionImpl {
    const end = (x: SessionImpl, r: LeaveReason) => this.end(x, r);
    const safe = (fn: () => void, what: string) => this.safe(fn, what);
    const s: SessionImpl = {
      player,
      playerId: player.id,
      table,
      seat,
      data: {},
      timers: new Map(),
      active: true,
      handler,
      setTimer(id, ticks, fn) {
        this.clearTimer(id);
        const h = system.runTimeout(() => {
          s.timers.delete(id);
          if (s.active) safe(fn, `${handler.id} timer ${id}`);
        }, Math.max(1, Math.floor(ticks)));
        s.timers.set(id, h);
      },
      clearTimer(id) {
        const h = s.timers.get(id);
        if (h !== undefined) system.clearRun(h);
        s.timers.delete(id);
      },
      leave() {
        end(s, 'leave');
      },
      isActive: () => s.active,
      closeForms() {
        if (player.isValid) uiManager.closeAllForms(player);
      },
    };
    return s;
  }

  private end(s: SessionImpl, reason: LeaveReason): void {
    if (!s.active) return;
    s.active = false;
    for (const h of s.timers.values()) system.clearRun(h);
    s.timers.clear();
    this.sessions.delete(s.playerId);
    this.seats.leave(s.playerId);
    this.safe(() => s.handler.onLeave?.(s, reason), `${s.handler.id}.onLeave`);
  }

  /** The player's current session, if seated. */
  sessionOf(player: Player): TableSession | undefined {
    return this.sessions.get(player.id);
  }

  /** Everyone seated at a table, by seat order. */
  sessionsAt(tableKey: string): TableSession[] {
    return this.seats.at(tableKey).map((x) => this.sessions.get(x.playerId)).filter((x): x is SessionImpl => !!x);
  }

  /** End every session at a table (e.g. owner closed it). */
  closeTable(tableKey: string, reason: LeaveReason = 'broken'): void {
    for (const s of this.sessionsAt(tableKey)) this.end(s as SessionImpl, reason);
  }

  /** Core: disconnect / distance / casino-off handling. */
  start(): void {
    world.beforeEvents.playerLeave.subscribe((e) => {
      const s = this.sessions.get(e.player.id);
      if (s) system.run(() => this.end(s, 'disconnect'));
    });
    system.runInterval(() => {
      const casinoOn = isCasinoEnabled();
      for (const s of [...this.sessions.values()]) {
        if (!s.player.isValid) {
          this.end(s, 'disconnect');
          continue;
        }
        if (!casinoOn) {
          this.end(s, 'casino_off');
          continue;
        }
        const r = s.handler.maxDistance ?? this.config.int('multiplayer.tableLeaveDistance');
        if (s.player.dimension.id !== s.table.dimension.id || !within(s.player.location, s.table.location, r)) {
          s.player.sendMessage(t('gui.burmaldaholic.error.too_far'));
          this.end(s, 'distance');
        }
      }
    }, 20);
  }

  private safe(fn: () => void | Promise<void>, what: string): void {
    try {
      const r = fn();
      if (r instanceof Promise) r.catch((e: unknown) => log.error(`${what} failed`, e));
    } catch (e) {
      log.error(`${what} failed`, e);
    }
  }
}
