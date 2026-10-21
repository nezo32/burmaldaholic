# HR — high-roller chips, one denomination source, bet entry, whose-cap reasons (R3; owner playtest E4, B5)

Branch `p2/hr` (from `feature/v020-table-ui`, `p2/k2` merged first). HR now owns `core/ui/v2/BetGates` reasons and the
bet-entry controls (`BetAmount`, client `BetEntry`).

## Done
1. **Chips 1000 / 5000 / 25000** (Yellow / Orange / Turquoise; EN+RU in STRINGS.md `core › High-roller chips … (lane HR)`).
   - **One source**: `ChipMath.DENOMINATIONS` = {25 000 … 1}; `ascending()`, `indexOf`, `largestAtMost`, `discs`, `label` ("25K").
     Replaced the copies in ChipColumns, CardLayout, ChipStacks, BoardGeometry, BetView, BaccaratView, BlackjackView,
     ChipStack, ChipStackView, CelebrationOverlay, BetControl, BlackjackScreen rack, KitGalleryScreen, Chips (registration loop).
   - JS: `CHIP_DENOMS` + `CHIPS` (with `digit`) + `chipLabel` in `tools/assets/lib/palette.mjs`; core/items, core/glyphs, cards/props,
     tables/kit, board/art, ui2/art read it. New `chipItem()` (port of the python chip) emits `item/core/chip_<d>.png` for all chips;
     `gen_core_assets.py` no longer writes chips (it also used to clobber the range_dispatch item JSONs).
   - `DenominationSourceTest` (source scan of java main/client/gametest + tools/assets .mjs) fails on any 3 consecutive chip values.
   - Cashier: withdraw/deposit are generic (100 000 = four 25K chips); tray columns `min(40, 240/n)` with "1K" labels; quick-add chips on
     their own row. Casino Menu pocket shows only denominations you carry.
2. **Bet entry** (`client/ui/v2/BetEntry`, pure `core/ui/v2/BetAmount`): readout click or **E** opens `[½][amount][×2][Max][OK]` above
   the Dock; digits / numpad / `k` (×1000) / `m` type via `V2Input` key names (26.3-safe; RawInputGuardTest green), Backspace, Enter = OK,
   Esc; mouse wheel over readout/field steps by a chip. Clamped to `min(table, VIP, balance)`; every clamp and Max flash whose cap.
   OK → `BetPort.amount()` (single spot) or `BetBar.unit()` (the amount a felt click places: roulette, craps, baccarat spots, any port
   without `amount`).
3. **Whose cap** (`core/wager/BetCap`): `cap_source`/`cap_max`/`cap_tier`/`cap_next`/`cap_next_max`/`cap_min`/`cap_owner_min` in every
   table state (`CasinoTableBlockEntity.clientState`, after the game's own `max`). Keys `gui.burmaldaholic.core.v2.reason.cap.{vip,vip_top,
   table,owner,owner_min,balance}`. `BetGates.aboveMax/belowMin/overBalance`; `V2Chrome.explain` rewrites any flow's generic
   above-max / not-enough reason (so RouletteFlow/CrapsFlow need no change). Server refusals (`placeBet`, `BetLimits.message/refusal`,
   blackjack HR, baccarat TOTAL_MAX / chemmy OVER_MAX, roulette TOTAL_MAX) use the same keys.

## Edits in files owned by other packages (minimal; please keep when merging)
- K2: `CasinoTableBlockEntity` (capFor, clientState, placeBet limit message), `V2Chrome`, `BetBar` (unit), `BetPort` (setsAmount/amount),
  `V2Input` (numpad digits), `KeyMap` (E = `entry.open`), `ChipStack` colours, `KitContractTest:33` (8-chip tray window).
- A2: BlackjackScreen (port `amount`, rack list), BaccaratScreen (chemmy `amount`), Blackjack/BaccaratTableBlockEntity refusal lines, views' DENOMS.
- B2: UthScreen port `amount` (sets the armed spot). C2: RouletteTableBlockEntity `message(v, player)`; `tables/draw.mjs` small-font `K`.
- E2/EC: Plinko/Wheel/CoinFlip/Dice ports + `LocalBet.set`; CashierScreen rows; BetControl.
- Integrator: gametest `fabric.mod.json` + `HighRollerGameTests`, `HighRollerClientGameTests`; `config/core-ids.txt` chip ids.

## Verified (all on 26.3; 26.2 dropped by the owner mid-package, the earlier 26.2 compile was green too)
- `./gradlew -Pmc=26.3 compileJava compileClientJava compileGametestJava test --tests core.chips.* core.ui.v2.* core.wager.*
  *DenominationSourceTest *RawInputGuardTest *LocalBet*` → green (ChipMathTest, BetAmountTest, BetCapTest, BetGatesTest,
  KitContractTest, DenominationSourceTest, RawInputGuardTest).
- `runGameTest -Pmc=26.3 -Dfabric-api.gametest.filter=high_roller` → "All 4 required tests passed": `HighRollerGameTests`
  (cashier withdraw 100 000 = four 25K chips, 36 500 split, deposit_all of 40×1K; blackjack state `cap_source=vip` + next tier,
  above-VIP bet refused with `reason.cap.vip`, above-balance bet refused with `reason.cap.balance`, within both accepted), all through
  `TableNetworking.receive`.
- Client real flow on 26.3 (`BURMALDAHOLIC_CGT=HR`, only `HighRollerClientGameTests` in a temporary, restored entrypoint swap):
  blackjack opened by use → Sit → E → digit keys "2500" → Enter (draft 2 500, nothing sent) → E → click Max at its pixel
  (VIP max 20 000, status names VIP) → OK → Deal → SERVER stake 20 000. BUILD SUCCESSFUL.
- `cd tools && npm run gen:assets && npm run check:assets && npm test` → assets OK, 59 tests passed. `gen_lang.py` run (no --prune).
- Not run: full build, other games' client lanes (tester), RU fit of the new strings in a live screen (strings are short; the status
  line ellipsises).

## Not done / risks
- Poker (stepper buy-in/raise) has no bet entry; tooltip for the readout opener is narration only.
- Board `chips_w.png` 1000/5000 colours changed to the UI's (yellow/orange); item textures of 1–500 now use palette colours (100's stripe white).
- Plinko/Wheel publish a fraction of the VIP max: reported as VIP (the next tier scales it).

## Fix round after T-HR (FAIL @e7845a94), all 26.3
- Cherry-picked the tester's tests (`test/hr` @92f9d442: BetAmountEdgeTest, 4 server tests, HighRollerEntryClientGameTests, lane THR).
- D1: `BetPort.entryCost()` / `BetAmount.Cost` (UTH: field = Ante, stake = 2 × Ante + Trips; Trips spot: Trips + 2 × Ante). The strip opens
  with the Ante, Max = largest Ante whose whole stake fits min(table, VIP, balance), the status shows "Total stake: N".
- D2: DenominationSourceTest scans comment-stripped, line-joined text: any-order lists of ≥3 chips (a 0 may join), high pairs
  (≥1000), ordered triples, ternary ladders; self-test covers the tester's misses.
- D3: `BetCap.withMax` scales the next tier's max. Roulette inside max still refused with its own key (not a cap; noted).
- D4: "0k" ignored; cashier tray columns ≤ 208 px → head 126 px; client assert: EN/RU withdrawn/deposited/credited/paid 57 600 000 fit.
- Full-inventory withdraw: new GameTest counts the dropped ItemEntities (3 stacks = 56 000). The tester's "no ItemEntity" was a test
  artefact: the mock player was creative under the `gameMode()` override, and a creative `Inventory.add` voids the stack; the test sets
  real survival (`setGameMode`). Real survival players drop correctly.
- Verified: unit (chips, ui.v2, wager, Denomination, RawInputGuard) green; `runGameTest high_roller` "All 9 required tests passed";
  client HR + THR (blackjack typed/Max, roulette, craps, punto banco, Plinko, **UTH** Ante/cost/Max, cashier head fit) BUILD SUCCESSFUL.

## Fix round 2 after T-HR round 2 (FAIL @21055623, D5), all 26.3
- **D5:** UTH Max/clamp now uses the server's own rule. `UthLimits.maxAnte(maxW, trips, balance)` = min((maxW − Trips) / 6,
  (balance − Trips) / 3), `maxTrips(maxW, ante, balance)`, `balanceNeeded` (3 × Ante + Trips; `check` uses it). `BetAmount.Cost`
  now carries separate multiples for the stake shown (2 × Ante + Trips), the maximum (6 × Ante + Trips) and the balance
  (3 × Ante + Trips); `UthFlow.entryCost(draft)` builds it from the `UthLimits` constants and `UthScreen` uses it (Ante and Trips spots).
  `BetAmountTest`: corrected numbers ((300 − 10) / 6 = 48, (1000 − 10) / 3 = 330, Trips 60 / 80) and a 20 000-case randomized test
  (random VIP max, table max, balance, min, Trips): client Max == `UthLimits.maxAnte/maxTrips`, `check(Max) == NONE`, `check(Max + 1)`
  refused, and Max's reason names the balance exactly when the server would refuse for funds.
- **Shutdown NPE:** cause = a busy table (seated player / open stake) left in the test area was played out by
  `TableLifecycle`'s `FULL_CHUNK_STATUS_CHANGE` hook while the area's chunk unloaded at shutdown, i.e. inside
  `DistanceManager.runAllUpdates` (the set it iterates was modified → NPE). `HighRollerGameTests.remove` now breaks the table/cashier
  at (1,1,1) while the chunk is loaded (normal `preRemoveSideEffects` play-out), then removes the player (finally). The drop test counts
  and discards the drops and removes the player in the same tick in a finally (no polled lambda).
- **Roulette inside max:** `RouletteTableBlockEntity.message(v, player)` answers `INSIDE_MAX` with the bet-cap sentence
  (`capFor(total max).withMax(inside max)`); `rouletteTotalMaxSaysVip` asserts a straight bet of inside_max + 1 is refused with `reason.cap.vip*`.
- **Client (THR):** the UTH step now checks Max with the VIP max binding (== server `maxAnte`, accepted, +1 = WORST_CASE_MAX), then sets
  the balance to 3 000 server-side + `syncViewers` (the tester's `casino balance set` never resynced the screen), Max = 1 000, Deal →
  server stake 2 000, 1 000 left.

### Verified (26.3)
- `compileJava compileClientJava compileGametestJava` + `test --tests core.chips.* core.ui.v2.* core.wager.* *DenominationSourceTest
  *RawInputGuardTest *LocalBet* *UthRoundTest *Roulette*` → green.
- `runGameTest -Dfabric-api.gametest.filter=high_roller` twice → "All 9 required tests passed", **BUILD SUCCESSFUL** both times (before
  the fix the same run crashed after the passes). `high_roller_game_tests` alone and `end_city_gets_high_roller` alone also green.
- Client `runClientGameTest -Pmc=26.3`, `BURMALDAHOLIC_CGT=THR`, only `HighRollerEntryClientGameTests` (temporary entrypoint swap,
  restored): roulette, craps, baccarat, **UTH (typed Ante, VIP-bound Max == server, balance-bound Max 1 000 → Deal stake 2 000)**,
  Plinko → BUILD SUCCESSFUL (log `.burmaldaholic-tools/hr-r3-client263.log`). Not run: full build, other lanes.

### Core fix: busy tables at chunk unload / server stop (coordinator request, release blocker)
- Root cause, reproduced in-process: vanilla fires `FULL_CHUNK_STATUS_CHANGE` inside `DistanceManager.runAllUpdates`; the play-out of
  a busy table re-entered chunk loading there, which modified the set being iterated (vanilla NPE, server crash). Real servers too.
- Fix (core, minimal): `core/mixin/ServerChunkCacheMixin` (HEAD/RETURN of `ServerChunkCache.runDistanceManagerUpdates`, registered
  in `burmaldaholic.core.mixins.json`) brackets the update; `TableLifecycle.onChunkUnloading` only queues the chunk while inside it
  and plays it out when the outermost update returns (still before `ChunkMap.processUnloads` saves the chunk, so the saved table
  carries no stakes). Outside an update it plays out at once, as before. Queue/depth reset on SERVER_STOPPED.
- `TableUnloadGameTests` (new, registered in gametest `fabric.mod.json`): busy blackjack, roulette, baccarat, craps, UTH and poker
  tables (seat + stake / buy-in) in a far force-loaded chunk; the ticket is dropped so vanilla unloads it for real. Asserts the
  server lives, every balance is the start (refund) or a legal played-out result (blackjack −100/0/+100/+150, craps ±50, UTH fold −20),
  the tables reloaded carry no stake and no seat, and a second play-out pays nothing. A second test does the SERVER_STOPPING play-out.
  Before the fix the unload test crashed the server with the exact NPE; after: green twice.
- Full `runGameTest -Pmc=26.3`: "All 240 required tests passed", BUILD SUCCESSFUL.
- Hardening (after T-HR PASS @e82e7858): the queue is `core/table/DeferredUnloads` (pure); `START_SERVER_TICK` resets a depth left
  by an update that threw and plays out what waited (warn logged). `DeferredUnloadsTest` (nesting, re-entrant play-out, stuck depth,
  clear) green; `TableUnloadGameTests` green on 26.3.
