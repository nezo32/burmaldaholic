# EC-1 handoff — cashier UX, exchange rates, chip store, merchant role

Branch `p2/ec1` (from `feature/v020-table-ui`). Spec: `.burmaldaholic-tools/findings/EC-SPEC.md` §1–3, 5, 6.

## Skeleton API (for EC-2: `git merge p2/ec1`)

### Server — `core/cashier/counter`
- `CounterSource(kind, pos, entityId)`: `cashier(pos)`, `merchant(entityId)`, `command()`; `STREAM_CODEC`.
- `CounterSessions`
  - `registerTab(String id, int order, Predicate<CounterSource>)`: exchange = 0 (cashier only), store = 10.
    **EC-2:** `CounterSessions.registerTab(CounterSessions.TAB_COMPS, 20, s -> true)` in the comps module's `register`.
  - `tabsFor(source)`, `open(player, source)`, `openAndSend(player, source)` (sends `CounterOpenPayload`), `close(player)`.
  - `validate(player) -> Session | null`. Cashier = the open cashier table menu (still valid, within 8 blocks). Merchant =
    stored session, entity alive, same level, within 8 blocks. Command = same dimension. `Session.dim()` = `O/N/E/?`.
  - `tryAct(player)`: the shared 4-tick cooldown for exchange, store and redeem (false = drop the action).
    `resetCooldown(player)` is a test hook.
  - `result(player, ok, Component)` sends `CounterResultPayload` (the screen's result line).
  - `dimCode(ResourceKey<Level>)`, `dimAllowed(Collection<String> dims, String dim)`.
  - Typical handler: `var s = CounterSessions.validate(p); if (s == null || !CounterSessions.tryAct(p)) return;`
- `CounterLayout`: the pure shell layout (`shell(guiW, guiH, tabWidths)` -> panel, top, tabs, readout, close, body, result).
- Payloads in `core/store/net`: `CounterOpenPayload(source, tabs, dimension)` (S2C), `CounterResultPayload(ok, message)` (S2C).
  They are registered by core (`CounterSessions.register`, called from `CoreModule`).

### Client — `client/cashier`
- `CounterTab`: `id()`, `title()`, `layout(Box body, HitMap hits)`, `render(g, body, mouseX, mouseY, partial)`,
  `onTarget(id, enabled, Press, mods)`, then the optional `key(keyCode, mods)`, `charTyped`, `opened()`, `tick()`, `stateChanged()`.
  Prefix target ids with the tab id (`comps.redeem.snack`).
- `CounterTabs.register(String id, Function<CounterShell, CounterTab>)`. **EC-2:** `CounterTabs.register("comps", CompsTab::new)`
  in the client module. The cashier block shows `CASHIER_TABS = exchange, store, comps`. The merchant gets the server's list.
- `CounterShell`: `host()` (font, closeCounter, source, dimension, state, sendTable, balance, withdrawable), `relayout()`
  (call it after your sync payload arrives), `result(ok, msg)`, `tab(id)`, `lastPressed()`, `pressed(id)`, `hits()`,
  `viewport()`, and the static `CounterShell.current()`, which returns the open counter's shell (use it from your payload receivers).
  Shell keys: 1/2/3 switch tabs and Esc closes. The shell offers each key to the active tab first.
- Hosts: `CounterScreen` (the cashier block, a table menu) and `CounterSceneScreen` (merchant, `/casino store`). Both implement `CounterShell.Holder`.
- `Inputs.shift(mods)`, `Inputs.ctrl(mods)`, `Inputs.MOD_SHIFT/MOD_CONTROL` (runtime values).

## Status
- [x] Skeleton (shell, tab interface, sessions, open and result payloads).
- [ ] Exchange rows, ExchangeMath, EconomyGuards, StoreConfig/catalog, store payloads, tabs, strings, tests (in progress).
