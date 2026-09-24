/**
 * Slots module (SLOTS.md, slots v2: three 243-ways video slots; docs/architecture/animation.md §7).
 *
 * Wiring (cut-over S-B5, done):
 *  - `./service.ts` (lane B-L8): round lifecycle — stake, whole-tape draw with the §14 streak re-draw, jackpots at
 *    draw time, persistence, settlement at the reveal gate / at once on skip, leave, disconnect or restart;
 *    integrations (chaos, statistics, advancements, contracts, owned casinos). v1 pools are migrated once
 *    (SLOTS.md §5.3); v1 rounds still open are settled by core from their drawn tickets.
 *  - `./form.ts` + `v2/present/**` (lane B-L9): the machine form — DDUI live form, classic ActionForm/action-bar
 *    fallback (`slots.bedrock.ddui`), frames, features, celebrations (through the lane B-L1 FxService).
 *  - `./cabinet.ts` + `v2/present/cabinet-driver.ts` (lane B-L10): the in-world `slot_reels` prop, landing on the
 *    same ticks as the player's form.
 *
 * The v1 machines (3×3 paylines) are gone from the live path; `./logic` (v1 pure maths) stays one release for
 * reference (SLOTS.md §8.1) and is deleted then.
 */
import type { CasinoModule } from '../../core';
import { SLOTS_SERVICE, type SlotsApi } from './api';
import { finishCabinet, playCabinet, registerCabinetComponent, startCabinets } from './cabinet';
import { closeMachineForm, openMachineForm, roundOf } from './form';
import { registerSlotsPvp } from './pvp';
import { type SlotsV2Service, startSlotsV2 } from './service';

/** Slots v2 is live (cut-over S-B5). Kept as a constant for tools and docs that still refer to the flag. */
export const SLOTS_V2_ENABLED = true;

export const slotsModule: CasinoModule = {
  id: 'slots',
  onStartup(ctx) {
    registerCabinetComponent(ctx.event); // in-world reels prop (lane B-L10, animation/slots.md §6.6)
  },
  onWorldLoad(ctx) {
    startCabinets(ctx); // lane B-L10
    registerSlotsPvp(ctx); // Slot Showdown (docs/architecture/pvp-bots.md)
    const ref: { svc?: SlotsV2Service } = {};
    const svc = startSlotsV2(ctx, undefined, {
      roundFromTape: roundOf,
      playCabinet,
      finishCabinet,
      openMachine: (s) => ref.svc && openMachineForm(ctx, ref.svc, s),
      closeMachine: closeMachineForm,
    });
    ref.svc = svc;
    ctx.services.provide<SlotsApi>(SLOTS_SERVICE, svc);
  },
};
