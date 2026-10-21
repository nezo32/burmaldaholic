# DL — house dealers and NPC variety (phase 2, R3: owner playtest N1/N2)

Branch `p2/dl` (from p2/cr-2; merged p2/mt (twice, last @616f71ea) and p2/cr-2 f53e1095). 26.3 only from the owner's 2026-10-20 decision on. Spec: `.burmaldaholic-tools/findings/DL-SPEC.md`.

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

- **MT dealer API / lecterns:** the binding is validated with `TableParts.dealerSpot`, and house tables come from
  `HouseGames.isHouseTable` plus "no owner". `Kits.legacyPod` never places a lectern (the dealer cell is 2-high air
  with a house dealer), `TableClearance` R4 rejects anything but air (`DealerCellClearanceTest`), and old
  BACCARAT/PIGLIN dealer markers in the CR-2 pods map to one house dealer (`NpcRoleDlTest` plus the GameTests).
- **Commits:** ba47aa5e (DL), 05312ae7 (tests), 72c099f0 **DL-hooks** (the game block entities only), ecbaf507 (MT merge + lectern/dealer API), then this handoff commit (GameTest chunk-force fix).

## Verified
- `npm run gen:assets && npm run check:assets && npm test` (tools): 11 files / 64 tests pass, 19 outputs, no other module changed.
- `-Pmc=26.3 compileJava compileClientJava compileGametestJava`: OK. (Before the owner's 26.3-only decision, 26.2 compiled too.)
- `-Pmc=26.3 test --tests 'dev.nezo.burmaldaholic.worldgen.*' '*DealerCuesTest' '*DealerMotionTest' '*CardMotionTest' '*CardKitReviewTest' '*RawInputGuardTest'`:
  343 run; the only failure is MultiBlockLayoutsTest (see Risks), and DealerCellClearanceTest was fixed and re-run green.
- `-Pmc=26.3 runGameTest -Dfabric-api.gametest.filter=house_dealer` on a wiped `build/run/gameTest`, after merging p2/cr-2 @a2d77bf5 and p2/mt @89c15f71: **All 11 passed**; MultiBlockLayoutsTest + worldgen.* unit tests green.
- `-Pmc=26.3 runGameTest -Dfabric-api.gametest.filter=worldgen_npc`: **All 5 passed**.
- Fixed a test-harness bug: the casino tests used to un-force chunks the GameTest framework had forced, which froze neighbouring tests. Now they only un-force their own chunks. Covered:
  annex, grand (riviera via /place), Nether parlor, End lounge (one dealer per table, bound, species, End head room, no
  old dealer NPC, one merchant, ensure ×3 idempotent), owned table, /kill respawn with the same look ≤ 340 t,
  save/load plus adopt, legacy replaced, damage/TNT/push, click/break/re-place, blackjack gesture.

## Fix round after T-DL (findings/T-DL.md, D0–D7)
Cherry-picked the tester's `test/dl` @2c17351e (HouseDealerTesterGameTests, client lane `DL`, render probe).
- **D0** `HouseDealerRenderers`: the craps stick `ItemStack` is created on first use (no static ItemStack at client init).
- **D1** `HouseDealers.standable` accepts a low block (carpet ≤ 3/16) in the cell; the dealer stands on its top
  (`floorHeight`). The legacy baccarat / piglin dealer is discarded only after its replacement spawned, and its slot is
  dropped by position and by entity UUID (it may have been moved by an unfold). v0.1.3 village casino 4/4 and Lounge 4/4 dealers.
- **D2** `HouseDealer.followTable` (each rebind and every 20 t before the home snap): re-resolves the bound table's
  `TableParts.dealerSpot`; when the cell moved (unfold f−1→f−2, fold back) and the home was the old cell or is now
  covered, the dealer re-homes there (if standable). Egg-placed dealers elsewhere keep their spot.
- **D3** a dealer click runs `level.mayInteract` (spawn protection) then `player.gameMode.useItemOn(…, ItemStack.EMPTY,
  MAIN_HAND, hit on the main block's top)`: the same chain as a real empty-hand block use (UseBlockCallback → Ownership
  refusals, bot ask-to-join, then the block).
- **D4** `HouseDealer.bindable`: binding (candidates and the 3-block fallback) skips player-placed (`HouseGames.isPlayerPlaced`) and owned tables.
- **D5** `DealerGestureSink.boundTable()` + static `gestureAt(level, table, radius, g)`: the dealer bound to the table
  wins; a dealer bound elsewhere never answers; unbound legacy dealers by distance. Blackjack/Baccarat BEs call it (hook shrank).
- **D6** EOL restored to the base (a2d77bf5) style in NpcRenderers, Assembly, CasinoIndex, WorldgenModule,
  MultiBlockLayoutsTest, OverworldCasinoTest, DealerMotionTest, gametest mixins.json, and the tester/NpcLook tests (LF).
  STRINGS.md, Kits.java, GrandCasinoTest already matched. (TableCulling is CRLF from p2/mt itself.)
- **D7** renderer: no `boolean[]` / `Cue` per frame; the pose is written straight into the render state (`Posed`).
- Tester test adjustments: `dealerClickHasTheTableClicksChecks` resets its counter before the table click (the earlier
  dealer clicks now correctly fire the callback too); `HouseDealerRenderProbeMixin` takes the dealer via MixinExtras
  `@Local(argsOnly)` (the pose signature changed).

### Verified (26.3 only, `build/run/gameTest` wiped before each GameTest run)
- `compileJava compileClientJava compileGametestJava -Pmc=26.3`: OK.
- `runGameTest -Dfabric-api.gametest.filter=house_dealer`: **All 24 passed** (DL 11 + tester 13).
- `runGameTest -Dfabric-api.gametest.filter=worldgen_npc`: **All 5 passed**.
- `test --tests '*MultiBlockLayoutsTest' 'dev.nezo.burmaldaholic.worldgen.*' '*DealerMotionTest' '*DealerCuesTest' '*RawInputGuardTest'`: 331 run, 0 failed.
- Client lane DL (`BURMALDAHOLIC_CGT=DL`, only `HouseDealerClientGameTests` entrypoint, swap reverted): **PASS**, no client
  crash, no render exceptions; 11 server dealers placed (riviera), client sees 13 bound; probe: house_dealer 667,
  piglin 325, ender 325 pose extractions.

## Not verified
- No screenshots (per brief). Poses, the stick and the ender eyes are proven only by the client run above (extraction, no exceptions).
- Real chunk unload/reload is covered by the tester test `realChunkUnloadReloadKeepsOneDealer`.

## Requests to other packages
- (resolved) the annex dealer-cell reach issue: CR-2 fixed it in a2d77bf5.
  with "casino/annex/plains blackjack @3,3 R4 dealer Cell[3,1] not reachable from the walkway". That is MT's new reachability rule
  against CR-2's annex; DL changed no blocks there. CR-2 or MT should fix it.
- **SY:** if RouletteSync, CrapsSync or PokerPub timing changes, update the three `*DealerCues` mappers and their tests.
- **EC:** register `"merchant"` in `HouseNpcRoles` at init.

## Risks
- The DL-hooks commit touches Blackjack/Baccarat (dealer lookup) and the Roulette/Craps/Poker BEs (`implements DealerCueSource`). It merges after A2, B2, RO and CM; re-apply if those rewrites move the code.
- The legacy v0.1 templates are frozen: their old dealers are replaced at runtime by `HouseDealers.ensure`.
