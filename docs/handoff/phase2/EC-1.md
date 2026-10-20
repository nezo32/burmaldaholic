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
See the report for the exact results.

## Not done / requests
- **The counting tray animation of the old CashierScreen was dropped.** The moves are still recorded (`tray` in the state), but nothing draws them yet.
- **DL:** please expose `HouseNpcRoles.register(String, BiConsumer<ServerPlayer, Entity>)` and call `MerchantRole.install(HouseNpcRoles::register)`, or put the class at one of the names in `MerchantRole.CANDIDATES`. Also add `entity.burmaldaholic.merchant`; the string already exists.
- **EC-2:** owns the VipRules perk swap (`perk.emerald_rate` → `perk.store_gold_shelf`) and GAME_DESIGN §12.
  - I left the old `emeraldBuyRateGoldVip` text in the §12 tier table for you.
  - The `gui.burmaldaholic.cashier.tab.comps` string already exists.
- **HR:** none. `ChipMath.DENOMINATIONS` is read dynamically, so the chip buttons follow new denominations automatically.
- **Unused strings:** the old `gui.burmaldaholic.cashier.{buy,sell,buy_gold,sell_gold,shop.buy_item,times}` rows and the five `config.burmaldaholic.economy.*Rate*` rows are still in STRINGS.md but nothing uses them. Prune them at integration.

## Risks
- The store tab has no mouse-wheel scroll; arrow keys scroll the grid. With "All" selected only 24 of 40 tiles are visible, so use the categories or the search.
- A config row or offer with an unknown item, potion or enchantment id is skipped silently.
