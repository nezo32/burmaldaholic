# DL — house dealers and NPC variety (phase 2, R3: owner playtest N1/N2)

Branch `p2/dl` (from p2/cr-2; merged p2/mt 715868eb and p2/cr-2 f53e1095). Spec: `.burmaldaholic-tools/findings/DL-SPEC.md`.

## Done
- **HouseDealer** (`worldgen/npc/HouseDealer`), three types: `house_dealer` (human, player model), `piglin_house_dealer`,
  `ender_house_dealer` (0.6×2.6, rendered at 0.9). They are fire-immune, take no damage (except creative and `/kill`), are
  not pushable, have no physics or gravity, no leash, riding or effects, and ignore explosions. They snap home every
  20 t, run one goal (LookAtPlayer 8, head ±75°), lock their body yaw to the table facing, and use
  `clientTrackingRange(8)`. The synced `TABLE` is bound lazily and re-checked every 100 t (staggered), using
  `worldgen/logic/DealerBinding` candidates, else the nearest table within 3 blocks. A click calls
  `mainState.useWithoutItem` at the main block's top; with no table bound the player gets
  `msg…worldgen.dealer.no_table`. The NBT keeps `table`, `home`, `home_yaw` and `look`.
- **Gestures:** appended `DealerGesture` SPIN/STICK_PUSH/STICK_PULL/POT_PUSH (ids are stable) and added DealerMotion
  cases plus `DealerMotion.ender()` (−0.55 rad, 0.7×). New `client/dealer/DealerGestureSink` and the `DealerCues`
  helper. Pure mappers: `RouletteDealerCues`, `CrapsDealerCues`, `PokerDealerCues`. The craps dealer holds a stick on
  the render side only.
- **Spawning:** `Kits.tablePod`/`legacyPod` map null, BACCARAT_DEALER and PIGLIN_DEALER to HOUSE_DEALER, so the old
  markers in Parlor, Lounge and annex pods become exactly one house dealer per cell. `CasinoNpcs.spawn(..., casinoId)`
  picks the type per dimension and sets the look. `CasinoNpcs.respawn` adds the duplicate guard. `HouseDealers.ensure`
  migrates and keeps one dealer per house table (≤12 per casino, idempotent, `CasinoRecord.dealersVersion`).
  `NpcRole.respawnTicks` is 200 t for dealers and merchants, and `CasinoTracker` checks NPCs every 100 t.
- **Variety:** `worldgen/logic/NpcLook` pools (human 6, piglin 4, ender 3, villager 4), a synced LOOK byte on
  HouseDealer and CasinoNpcEntity, the Ender Croupier (Lounge, replaces the shulker; sells the shulker's list), and
  the Pit Boss (Parlor; greeting plus the croupier shop).
- **EC:** `worldgen/npc/HouseNpcRoles.register(String role, BiConsumer<ServerPlayer, Entity>)` and the MERCHANT role
  (`merchant`/`piglin_merchant`/`ender_merchant` by dimension). There is one merchant per casino beside the cashier:
  `Kits.cashierCage` (r+2, f+1), Lounge (3, FR, 12), Parlor (CX+2, FR, 4). Without a handler the merchant greets and
  shows `msg…worldgen.merchant.closed`.
- **Art:** `tools/assets/modules/npc.mjs` plus `npc/{uv,palette,paint}.mjs` produce 18 skins and the house-dealer
  spawn egg. Tests are in `npc/npc-art.test.mjs`.

## Verified
(see the bottom section)

## Not verified
- No client run and no screenshots (per brief). Renderer poses, the stick and the ender eyes layer are compile-checked only.
- "Unload/reload" is simulated by a save/load round trip of the entity, not a real chunk unload.

## Requests to other packages
- **CB-G:** when `HouseGames.isHouseTable` lands, call it from `HouseDealers.isHouseTable` (today the fallback is "inside the box and no owner").
- **SY:** if RouletteSync, CrapsSync or PokerPub timing changes, update the three `*DealerCues` mappers and their tests.
- **EC:** register `"merchant"` in `HouseNpcRoles` at init.

## Risks
- The DL-hooks commit touches Blackjack/Baccarat (dealer lookup) and the Roulette/Craps/Poker BEs (`implements DealerCueSource`). It merges after A2, B2, RO and CM; re-apply if those rewrites move the code.
- The legacy v0.1 templates are frozen: their old dealers are replaced at runtime by `HouseDealers.ensure`.
