# EC-1 handoff: cashier UX, exchange rates, chip store, merchant role

Branch `p2/ec1`, from `feature/v020-table-ui`. Spec: `.burmaldaholic-tools/findings/EC-SPEC.md` §1–3, §5, §6, plus the coordinator decisions.

## Skeleton API (for EC-2: `git merge p2/ec1`)

### Server: `core/cashier/counter`
- **`CounterSource(kind, pos, entityId)`**
  - Factories: `cashier(pos)`, `merchant(entityId)`, `command()`.
  - Has a `STREAM_CODEC`.
- **`CounterSessions`**
  - `registerTab(String id, int order, Predicate<CounterSource>)`. Built-in tabs: exchange = 0 (cashier only), store = 10.
    **EC-2:** call `CounterSessions.registerTab(CounterSessions.TAB_COMPS, 20, s -> true)` in your `register`.
  - Session calls: `tabsFor(source)`, `open(player, source)`, `openAndSend(player, source)` (sends `CounterOpenPayload`), `close(player)`.
  - `validate(player)` returns a `Session` or null:
    - Cashier: the cashier table menu is open, still valid, and within 8 blocks.
    - Merchant: a stored session; the entity is alive, in the same level, and within 8 blocks.
    - Command: the same dimension.
    - `Session.dim()` returns `O`, `N`, `E` or `?`.
  - `tryAct(player)`: the shared 4-tick cooldown for exchange, store and redeem. When it returns false, drop the action.
  - `resetCooldown(player)` is a test hook.
  - `result(player, ok, Component)` sends `CounterResultPayload`, which the screen shows as its result line.
  - Helpers: `dimCode(ResourceKey<Level>)`, `dimAllowed(dims, dim)`.
  - Typical handler: `var s = CounterSessions.validate(p); if (s == null || !CounterSessions.tryAct(p)) return;`
- **`CounterLayout`**: the pure layout for the shell, exchange and store, plus the detail panel or strip.
- **Payloads** in `core/store/net`: `CounterOpenPayload`, `CounterResultPayload`, `StoreSyncPayload`, `StoreActionPayload`.
  Core registers them in `CounterSessions.register` and `StoreService.register`, both called from `CoreModule`.

### Client: `client/cashier`
- **`CounterTab`**
  - Required: `id()`, `title()`, `layout(Box body, HitMap hits)`, `render(g, body, mouseX, mouseY, partial)`, `onTarget(id, enabled, Press, mods)`.
  - Optional: `key(keyCode, mods)`, `charTyped`, `opened()`, `tick()`, `stateChanged()`.
  - Prefix target ids with the tab id, for example `comps.redeem.snack`.
- **`CounterTabs.register(id, Function<CounterShell, CounterTab>)`**
  - **EC-2:** call `CounterTabs.register("comps", CompsTab::new)` in your client module.
  - The cashier shows `CASHIER_TABS` = exchange, store, comps. A tab with no registered factory is skipped.
  - The merchant shows the server's tab list.
- **`CounterShell`**
  - `host()` gives font, closeCounter, source, dimension, state, sendTable, balance and withdrawable.
  - `relayout()`: call it after your sync payload arrives.
  - Other methods: `result(ok, msg)`, `tab(id)`, `lastPressed()`, `pressed(id)`, `hits()`, `viewport()`, `mods(eventMods)`.
  - The static `CounterShell.current()` returns the shell of the open counter.
  - Keys:
    - 1, 2 and 3 switch tabs. The active tab sees each key first, so a focused search box can take digits.
    - Esc closes.
    - The shell tracks Shift and Ctrl from key presses, because Fabric's simulated clicks carry modifier bits = 0.
- **Hosts:** `CounterScreen` (the cashier block, a table menu; replaces `CashierScreen`) and `CounterSceneScreen` (merchant and `/casino store`).
- **`Inputs`:** added `MOD_SHIFT`, `MOD_CONTROL`, `shift(mods)` and `ctrl(mods)`, all read at runtime.
- **Gametest helper:** `gametest/core/CounterFlow` clicks targets at real pixels (optionally with Shift) and presses keys.

## Done
- **Exchange (§1)**
  - One action, `exchange {row, dir, qty, all}`. The server computes "all" with `ExchangeMath` (fitCount, countIn, countOut, countBuy, `MAX_UNITS = 36×64`).
  - Items go only into the 36 main slots. Nothing is dropped; when fewer fit, the player gets `msg…exchange.partial`.
  - `remove` returns the count it actually removed, and only that count is credited.
  - Out: withdraw first, insert, then refund whatever did not fit. Uses the shared 4-tick cooldown.
  - `MAX_BATCH` is gone, and so are buy, sell, buy_gold and sell_gold.
  - The Withdraw "Max" button uses `withdraw_fit`: the largest amount whose greedy chip split fits the inventory.
  - The cashier state gains `rows[]{id, item, to, from, have, gate, max_in, max_out}`.
- **Rates (§2)**
  - `economy.exchange` map with the 6 default rows. The v0.1 keys migrate through a new `core/config/Migrates` hook; only values that differ from their v0.1 defaults are moved.
  - `EconomyGuards` R1–R6 run in `EconomyConfig.validate`: a violating row's `fromChips` is set to 0. They also run on catalog build: a violating offer is dropped and logged once.
  - The Gold VIP emerald bonus is gone (the rate is 9 for everyone).
- **Store (§3)**
  - `StoreConfig` (`store` section): 38 offers plus 2 scratch cards. Uses potions, books, rocket flight and End dimension flags.
  - `StoreCatalog`, `StoreService` (sync, buy, limits).
  - Player attachment `burmaldaholic:store_limits`, persistent, `copyOnDeath`.
  - `/casino store` (op).
- **Merchant**
  - `MerchantRole.open` and `install(registrar)`.
  - It looks for DL's `HouseNpcRoles.register(String, BiConsumer)` reflectively, because that class has not landed yet.
- **UI**
  - `ExchangeTab`: ×1, ×64 and All/Max; Shift+click sends ×64; keyboard ↑ ↓ ← →, Enter, Shift+Enter, Ctrl+Enter, A.
  - `StoreTab`: category rail, search box, 6×4 grid (4 columns when narrow), detail panel or strip, "Confirm N?" for 3 s on a Max over 5 000.
- **Strings:** the EN and RU subsections were added to STRINGS.md, then `gen_lang` was run (LF).
- **Docs:** CONFIG.md (exchange and store), GAME_DESIGN §3.2 and §3.2.1, UI.md §3 and §3a, LOCALIZATION glossary.
- **Tests updated:** EconomyGameTests, JavaReviewGameTests m2, CoreClientGameTests and RealFlowClientGameTests (cashier and cashierShop now use the counter targets), ConfigBinderTest.

## Verified
Owner decision: 26.3 only. Every run used `-Pmc=26.3`, except the first compile and unit pass, which also ran on 26.2.
- **Compile:** `compileJava compileClientJava compileGametestJava -Pmc=26.3`. It compiled as part of the test and GameTest runs below.
  - Two standalone attempts died with JVM out-of-memory (machine-wide); the retries compiled.
  - The 26.2 compile also passed earlier.
- **Unit tests (26.3):** EconomyGuardsTest 6, ExchangeMathTest 5, StoreCatalogTest 6, CounterLayoutTest 2, ConfigBinderTest 5, ConfigSpecCoverageTest 2, InputsTest 5, RawInputGuardTest 1. All passed.
- **Server GameTests (26.3, through `TableNetworking.receive` and `StoreService.receive`):**
  - `counter_game_tests`: 8/8 passed. Covers exchange all with a near-full inventory and no drops, Max out with 3 free slots = 192, the balance cap, two "all" actions in the same tick, the state rows, golden apple ×64 → 8, elytra End-only at Platinum, merchant out of range.
  - `economy_game_tests`: 6/6 passed.
  - `java_review_game_tests_m2`: 2/2 passed.
- **Client run (26.3, `BURMALDAHOLIC_CGT=EC`):** only `CounterClientGameTests`, via a temporary entrypoint swap that was restored afterwards. BUILD SUCCESSFUL.
  - Ran at 1920×1080 GUI 4, 1280×720 GUI 3 (narrow) and RU 1920×1080.
  - Covered: Shift+click ×1 → 64, → / Enter / Shift+Enter / ← / Ctrl+Enter, the chip button + Withdraw, A, key 2, `/` search (EN "bread", RU "хлеб"), Enter buys 1, Shift+click ×16 → 64, key 1, Esc. Every check asserts server state; the chat log confirms each step in EN and RU.
- **Not run:** the full suites. `RealFlowClientGameTests` (cashier and cashierShop were rewritten for the counter) and `CoreClientGameTests` were not run; they belong to their lanes.

## Follow-up (coordinator, round 2)
- **Counting tray:** `CounterTray` replays every server move under the deposit block, in the Exchange tab's wide layout
  (`CounterLayout.Exchange.tray`). Chips fly from the inventory for deposits and credits, and from the balance readout
  for withdrawals and payments. A click skips the flights; reduce-motion shows the final stacks at once. Narrow layouts
  have no room for it; those moves are told in chat.
- **Store grid:** the mouse wheel scrolls the grid one row at a time (`CounterTab.scroll`, `CounterShell.scroll`, and
  `mouseScrolled` on both hosts). A scrollbar is drawn, and moving the selection makes the grid follow it again.
- **Pruned strings:** `gui.burmaldaholic.cashier.{buy,sell,buy_gold,sell_gold,times,emeralds_slider,shop,shop.buy_item,
  denomination,denomination.auto,exchanged,dropped}` and `config.burmaldaholic.economy.{emeraldBuyRate,
  emeraldBuyRateGoldVip,emeraldSellRate,goldBuyRate,goldSellRate}`, from STRINGS.md and the core lang JSON (LF).
  Before pruning, I grepped every `p2/*` branch and `feature/v020-table-ui`. The only references were the pre-EC-1
  copies of files this branch rewrites (CashierScreen, CashierBlockEntity, EconomyConfig and their tests).

- **Verified (26.3):**
  - `compileClientJava compileGametestJava` plus the unit tests (`core.cashier.*` including CounterLayoutTest with the tray box, and RawInputGuardTest): BUILD SUCCESSFUL.
  - EC client lane: BUILD SUCCESSFUL in 3 runs (EN 480×270, EN 427×240, RU), now also covering the tray replaying a deposit and click-to-skip, plus wheel down 1 row and wheel up back.

## Round 3: T-EC1 fixes (on top of the tester's tests, cherry-picked from `test/ec1` @d0f6565e)
- **D1:** the store spends `CashierBlockEntity.withdrawable` (balance − debt) and refuses players in default, like exchange "out" (scratch cards included). The sync's "funds" lock uses the same number.
- **D2:** the v0.1 `shop_buy` action and the cashier state's `shop` list are removed; the store tab (scratch cards too) goes through `StoreService`.
- **D3:**
  - R2 runs over `EconomyGuards.CHAINS`, the transitive closure of the crafting equivalences (block ↔ ingot ↔ nugget = 81).
  - R4 now also applies to exchange rows' way out (`EconomyGuards.rows(rows, villagerValue)`, `EconomyConfig.rowViolations()` / `villagerValue()`), including what an item uncrafts into.
  - R6 forbids every iron, golden and chainmail tool, weapon and armour piece, and horse and nautilus armour.
  - The tester's gap tests are flipped to `r2IsTransitive`, `exchangeRowsKeepVillagerFloor` and `smeltableGearIsForbidden`.
- **D4:**
  - The rate cells are sized from the widest "→N" / "←N" text of the rows (`CounterLayout.row(row, rateW)`, `ExchangeTab.rateWidth()`). The "You have" text falls back to "×N", then "×2k+", then nothing (the tooltip keeps the exact count).
  - The narrow strip's Buy button is sized from "Confirm 999,999?" (`CounterLayout.detail(store, buyW)`, `StoreTab.buyWidth`).
  - Shorter RU strings: `deposit_held` is now "Внести из руки" and `store.limit_day` is now "Сегодня ещё %1$s из %2$s" (gen_lang, LF).
  - `CounterFitCheck` checks the real rates and the real detail layout. The client test adds layout-only passes at 640×360 and 960×540 (EN and RU).
- **D5:** `CounterShell.focusChanged` clears the tracked Shift and Ctrl when the window gains or loses focus (an edge detected in `tick`). It is asserted in the client test.

**Verified (26.3 only):**
- `compileJava compileClientJava compileGametestJava` plus unit tests `core.economy.*`, `core.store.*`, `core.config.*`, `core.cashier.*`, RawInputGuardTest and InputsTest: BUILD SUCCESSFUL.
- Server GameTests:
  - `counter`: 18/18 (EC-1's 8 plus the tester's 10).
  - `economy_game_tests`: 6/6.
  - `java_review_game_tests_m2`: 2/2.
- EC client run (`BURMALDAHOLIC_CGT=EC`, only `CounterClientGameTests`, entrypoint swap restored): BUILD SUCCESSFUL, with no failures.
  - Full flows: EN and RU at 480×270 and 427×240.
  - Layout asserts and the D5 check: EN and RU at 640×360 and 960×540.

- **D6 (round 4, tester repro cherry-picked from `test/ec1` @b2681a97):** R2 now compares every ordered pair of rows for the same item (duplicates, per-dimension variants) or for items linked by a crafting chain (`EconomyGuards.itemsFrom`). R5 compares each store offer against every row of the same or a chain-linked item. `duplicateRowsOfOneItemAreCompared` and `randomizedMultiStepChainsAfterGuardsNeverProfit` pass. Unit tests on 26.3 (`core.economy.*`, `core.store.*`, `core.config.*`, `core.cashier.*`): BUILD SUCCESSFUL.

## Not done / requests
- **DL:** please expose `HouseNpcRoles.register(String, BiConsumer<ServerPlayer, Entity>)` and call `MerchantRole.install(HouseNpcRoles::register)`, or put the class at one of the names in `MerchantRole.CANDIDATES`. Also add `entity.burmaldaholic.merchant`; the string already exists.
- **EC-2:** owns the VipRules perk swap (`perk.emerald_rate` → `perk.store_gold_shelf`) and GAME_DESIGN §12.
  - I left the old `emeraldBuyRateGoldVip` text in the §12 tier table for you.
  - The `gui.burmaldaholic.cashier.tab.comps` string already exists.
- **HR:** none. `ChipMath.DENOMINATIONS` is read dynamically, so the chip buttons follow new denominations automatically.

## Risks
- A config row or offer with an unknown item, potion or enchantment id is skipped silently.
