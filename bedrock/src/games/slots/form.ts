/**
 * Slots v2 machine form wiring (cut-over S-B5; docs/architecture/animation.md §7.2): the B-L9 presenter
 * (`v2/present/ddui-form.ts` DDUI form, `classic.ts` fallback) on top of the B-L8 service, with the B-L1
 * FxService for Big+ celebrations and the cabinet as the anchor of sounds, particles and the jackpot camera.
 *
 * `SlotsFormHost` is the service's `SlotsV2Host` plus what only the form needs (anchor, config flags, the fx
 * adapter, the last settled screen). One open machine form per player; leaving the table closes it.
 */
import type { Block, Player } from '@minecraft/server';
import type { ModuleContext, TableSession } from '../../core';
import { type SlotsV2Deps, type SlotsV2Service, SlotsV2Host } from './service';
import { slotTier } from './v2/logic/tiers';
import type { SlotFx } from './v2/present/celebrate';
import { openSlotMachine } from './v2/present/classic';
import type { SlotAnchor, SlotHost, SlotPresenter } from './v2/present/ddui-form';
import { type SlotScreen, terminalScreen } from './v2/present/features';
import { type SlotRound, roundFromTape } from './v2/present/frames';
import { type SlotConfigFlags, frameOptions, slotSettings } from './v2/present/settings';

/** Outward direction of a cabinet face per `minecraft:cardinal_direction` (the prop faces the block's front). */
const FACING: Record<string, { x: number; z: number }> = { north: { x: 0, z: -1 }, south: { x: 0, z: 1 }, east: { x: 1, z: 0 }, west: { x: -1, z: 0 } };

/** Centre of the reel face of the cabinet at `block` (the prop stands on the block, reels at +1). */
export function cabinetAnchor(block: Block | undefined): SlotAnchor | undefined {
  if (!block) return undefined;
  let dir = 'north';
  try {
    dir = String(block.permutation.getState('minecraft:cardinal_direction') ?? 'north');
  } catch {
    /* no state: default front */
  }
  const f = FACING[dir] ?? FACING.north!;
  const l = block.location;
  return { dimension: block.dimension, location: { x: Math.floor(l.x) + 0.5 + f.x * 0.5, y: Math.floor(l.y) + 1, z: Math.floor(l.z) + 0.5 + f.z * 0.5 }, facing: f };
}

/** B-L9 `SlotFx` on the core FxService (lane B-L1): the presenter's slot cues play the stems, so none here. */
export function slotFx(ctx: ModuleContext, anchor: SlotAnchor | undefined): SlotFx {
  return {
    celebrate: (p, r) =>
      ctx.fx.celebrate(
        p,
        { tier: r.tier, ret: r.net, stake: r.stake, game: r.game, seed: r.seed, table: r.table, words: r.words, stems: {}, subTier: r.jackpotSubTier, maxWin: r.maxWin },
        anchor ? { at: anchor.location, dimension: anchor.dimension } : {},
      ),
  };
}

export const roundOf: NonNullable<SlotsV2Deps<SlotRound>['roundFromTape']> = (def, tape, o) => roundFromTape(def, tape, { tier: o.tier, rest: o.rest });

export class SlotsFormHost extends SlotsV2Host<SlotRound> implements SlotHost {
  readonly anchor: SlotAnchor | undefined;
  readonly flags: SlotConfigFlags;
  readonly fx: SlotFx | undefined;

  constructor(svc: SlotsV2Service, session: TableSession, ctx?: ModuleContext) {
    super(svc, session, roundOf);
    let block: Block | undefined;
    try {
      block = session.table.dimension.getBlock(session.table.location);
    } catch {
      block = undefined;
    }
    this.anchor = cabinetAnchor(block);
    this.flags = svc.flags();
    this.fx = ctx ? slotFx(ctx, this.anchor) : undefined;
  }

  /** The settled screen of the previous round (shown when the form opens). */
  lastScreen(): SlotScreen | undefined {
    const last = this.st.last;
    if (!last) return undefined;
    const def = this.svc.def(last.machine);
    const round = roundOf(def, last, { tier: slotTier(last.totalFifths, this.svc.bigWinTiers()) });
    return terminalScreen(round, frameOptions(slotSettings(this.session.player, this.flags)));
  }
}

/** Open machine forms (one per player). */
const open = new Map<string, { presenter: SlotPresenter }>();

/** `SlotsV2Deps.openMachine`: DDUI first, the classic form when DDUI is off / unavailable (`openSlotMachine`). */
export function openMachineForm(ctx: ModuleContext, svc: SlotsV2Service, s: TableSession): void {
  const p: Player = s.player;
  if (!p.isValid || !s.isActive()) return;
  const prev = open.get(s.playerId);
  if (prev?.presenter.running()) return;
  const host = new SlotsFormHost(svc, s, ctx);
  const presenter = openSlotMachine(p, host, { hud: ctx.hud });
  open.set(s.playerId, { presenter });
}

/** `SlotsV2Deps.closeMachine`: the table session ended (leave, disconnect, casino off). */
export function closeMachineForm(s: TableSession): void {
  const f = open.get(s.playerId);
  open.delete(s.playerId);
  // the service has already settled the round; ending the presentation shows the terminal state (F8)
  f?.presenter.finish(true);
}
