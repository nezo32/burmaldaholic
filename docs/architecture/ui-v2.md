# Burmaldaholic — Table UI v2 and the in-world board: architecture (v0.2.0)

Owner: architect B2 (UI redesign + in-world board). Status: **contract + work breakdown; the pure layout skeleton is
in the tree and tested** (`core/ui/v2/**`, `TableLayoutV2Test`, `BoardMathTest`). Audience: the kit lanes and the
per-game lanes of v0.2.0.

Inputs: `docs/design/visual/v2/REDESIGN.md` (the design; mockups `v2/*.png`, before shots `v2/before/`), the
orchestrator's review of the mockups (§2 here), the v0.1.2 real-player reports (bets impossible at blackjack /
roulette / craps; UTH bet stuck; non-slot UIs "horrendous"; the game on the in-world table is loved and must become
readable), `docs/architecture/animation.md` (PTL timelines, FX kit, BER framework, asset generator), `java.md`.

Scope of v0.2.0: **perfect the EXISTING games** — blackjack, baccarat / chemmy, Hold'em, UTH, roulette, craps, Plinko,
Wheel of Fortune, scratch cards, Dice Duel, Coin Flip, the PvP invite / duel screens, and the in-world view of every
table. No new games, no rule / money / RNG-order change. Slots v2 (the quality bar) is untouched.

Contents
0. The shape in one page
1. Decisions
2. Amendments to REDESIGN.md (review fixes + what the prototype found)
3. Layout system (pure, `core.ui.v2`)
4. Client kit (`client.ui.v2`): components and their APIs
5. The bet flow contract: impossible to get stuck
6. Input, hit-testing and GUI scale
7. The in-world board kit (`client.board`, `core.ui.v2.board`)
8. Animation integration
9. Sprites (`tools/assets`): modules, files, who regenerates
10. Strings
11. Test plan
12. Lanes, file ownership, merge order
13. Risks
14. Skeleton inventory

---

## 0. The shape in one page

```
 main (pure, JUnit)                                   client                                         gametest
 ┌──────────────────────────────────────┐   ┌───────────────────────────────────────────────┐   ┌───────────────────────────┐
 │ core.ui.v2                           │   │ client.ui.v2   (the kit: widgets, no game code)│   │ gametest.v2.RealFlow      │
 │  Viewport  Tier  TableGrid  Box      │──►│  V2Chrome ── TopBar BetBar Dock PrimaryButton  │   │  open by USING the block, │
 │  DockLayout SeatColumn TagPlacer     │   │             ActionRow SeatPlate ChipStack      │   │  click HitMap ids at      │
 │  HitMap LayoutAudit TextFit Gate     │   │             Stamp ResultBanner BalancePill ... │   │  480×270 / 640×360 / …,   │
 │  BetView (pure bet model)            │   │  V2TableScreen (menu tables)  V2SceneScreen    │   │  assert SERVER state      │
 │ core.ui.v2.board                     │   │ client.board  (in-world kit)                   │   │ gametest.v2.<game>.*      │
 │  HoloScale (LOD, text px)            │──►│  BoardRenderer<T,S> ─ FeltPainter CardPainter  │   └───────────────────────────┘
 │  PlateLayout (plate collision)       │   │    ChipPainter HoloBoard SeatPlates Projector  │
 │ core.table.TablePublic (seat names)  │   └───────────────────────────────────────────────┘
 │ games.<g>.present.<G>Flow  (gates)   │      games.<g>.client.<G>Screen (v2)  <G>BoardRenderer
 │ games.<g>.present.<G>Pub   (public)  │
 └──────────────────────────────────────┘
```

- **One frame, one bet bar, one primary button** for every non-slot game. Layout is pure maths in `main`, unit-tested
  at every GUI size; the client only draws the boxes the maths produced and routes clicks through the same boxes.
- **Every primary/action state is a `Gate`** produced by a pure per-game `Flow`: a disabled gate cannot exist without
  a reason; the status line shows it. The bet flow has an acknowledgement watchdog, so a click is never silently lost.
- **The in-world board** is one renderer base + painters; every table (blackjack and baccarat included, which draw
  nothing today) shows felt, cards, chips, an upright readable board and collision-free seat plates.
- **Kit first, as a thin contract**; then one lane per game group owns its screen AND its in-world renderer AND its
  real-flow tests. Hotfix v0.1.2 is merged before any of this.

---

## 1. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | Pure layout lives in **`main`**: `dev.nezo.burmaldaholic.core.ui.v2` (+ `.board`). Widgets live in **`client`**: `dev.nezo.burmaldaholic.client.ui.v2`; in-world rendering in `dev.nezo.burmaldaholic.client.board`. REDESIGN's `client/table/v2/` path is replaced. | JUnit sees only `main` (animation.md A1); layout must be testable without MC. |
| D2 | A screen works in a **virtual GUI** (`Viewport`, `ViewportPolicy`; phase 2b §3.7): the player's GUI scale is kept for 240–720 px high GUIs, stepped **down** below 320 × 240 and stepped **up** above 720 px by the smallest whole `m` that brings it to ≤ 720; a centred **frame** caps the working area at 600 high and 16:9. Always an integer framebuffer scale. One factor (and one frame offset) for drawing and the mouse. | 1080p@2 used to be a 2× "zoomed in" 480 × 270; it is now a ROOMY 960 × 540 at the player's scale. |
| D3 | **v2 screens fill the GUI** (their frame): `imageWidth = width`, `imageHeight = height`, `leftPos = topPos = 0`; width × height = the frame. Layout px = container px = GUI px (÷ factor, − frame offset). No centred canvas, no offset maths in game code (the frame offset lives in the entrance pose and the mouse mixin only). | Removes the whole class of "click lands 40 px off" bugs the hotfix is fixing one by one. |
| D4 | **Tier = min(tier by height, tier by width)** (T2 needs ≥ 400 px wide, T3 ≥ 560). | 4:3 windows at T2 heights overflowed the Dock (prototype finding). |
| D5 | **Composition over inheritance**: the kit's `V2Chrome` holds grid, dock, hit map, top bar, bet bar, toasts. Two thin bases host it: `V2TableScreen extends CasinoTableScreen` (menu tables) and `V2SceneScreen extends CasinoScreen` (Coin Flip, Scratch, Dice, PvP). | Two screen families exist today (`AbstractContainerScreen` vs `Screen`); the same kit must serve both. |
| D6 | **`Gate` + per-game pure `Flow`** (`games/<g>/present/<G>Flow.java`) decide label / enabled / reason of the primary and action buttons from a small view record built from the state tag. Disabled ⇒ reason (enforced in the constructor). | "The main button always says why it's disabled"; exhaustively unit-testable per phase. |
| D7 | **Bets stay server-authoritative** (today's actions `bet`, `undo`, `clear`, `rebet`, `double`, …). The client `BetBar` sends, shows an optimistic "pending" chip, and a **watchdog** turns a missing echo (40 ticks) into a visible reason + a `resync` request. | No duplicated money logic on the client; no silent stuck state. |
| D8 | Result **stamps show the word only** and sit in the seat's outcome slot **above the cards**; the **net goes into the seat's value pill** under the cards (where the chips land). Collisions are resolved by `SeatColumn.table` (levels 2× → 1× → dot). | Review: stamps covered cards; prototype: "BLACKJACK +75" at 2× overlaps the hero's neighbours at 480 × 270. |
| D9 | In-world text scale **grows with distance** up to 8 blocks (`HoloScale`): glyph ≥ 13 screen px from 3 to 8 blocks at 1080p, FOV 70–90. | Fixed 1/80 block gives 9.6 px at 8 blocks — below readable. |
| D10 | In-world plates are laid out in the **camera plane** (`PlateLayout`), always **above the projected felt content**, collision-free, leader line when displaced. Board is a **yaw-only** billboard (upright, never swims). | Review: plates overlapped cards and each other. |
| D11 | Kit sprites go to **`textures/gui/sprites/core/v2/**`** (ids `burmaldaholic:core/v2/<name>`), generated by a new module `tools/assets/modules/ui2.mjs`. REDESIGN's `tables/v2/…` path would fail `checkAssetOwnership` (no module owns `tables`/`v2`). | Ownership check. |
| D12 | Seat names for the board travel in a small **public seat header** (`core.table.TablePublic`) in each table's update tag, written by the base BE. No new packets. | Plates need names for every passer-by; hole cards stay hidden. |

---

## 2. Amendments to REDESIGN.md

These override the design where they differ. The design owner should mark REDESIGN.md §4/§7/§9/§10 accordingly
(the kit lane does it in its first PR).

### 2.1 Result stamps never cover cards (review)
- Seat column order (top → bottom): **outcome slot · cards · stack + value pill · plate** (`SeatColumn`). The outcome
  slot shows the TotalPill while the hand plays and the **Stamp word** on settle (`BUST`, `BLACKJACK`, `PUSH`,
  `WIN`, `SURRENDER`). The **net** (`+75` green / `−50` red / `50` slate) replaces the bet value in the value pill to the
  right of the stack. Hero: 2× stamp; others 1×. If a level collides, it degrades (2× → 1× → coloured dot); the
  dot still says it with colour + the value pill, and the TopBar delta + status line always carry the net.
- The hero's total no longer floats left of the fan (it collided with the neighbour's 5-card hand); it is the hero's
  outcome slot, 2×.
- The value pill is **beside** the stack, never above it (in the mockup the pills covered the bottom of the hero's
  cards). When the seat pitch cannot hold discs + pill (7 seats on a 320-wide GUI) the discs are dropped.
- `RoundBanner` → **`ResultBanner`**, placed only in a declared free region (`FreeSlot` search over regions the game
  layout registers: card tables = between dealer row and seat row; roulette = over the wheel column; craps = over the
  FIELD row's empty band). It never covers cards, bets or layout numbers; when no region fits it is skipped (status +
  delta pill carry the result). Banner = net of the whole round ("YOU WIN +125"); stamps are per hand — they must
  agree (`+125` = sum of hand nets; the mockup's +125 vs +75 is a copy error).
- Felt prints (`BLACKJACK PAYS 3 TO 2`, `Dealer stands on 17`) are **decor**: drawn only where they intersect no
  content box (at T1/T2 the second line drops).

### 2.2 Roulette chip value tags (review)
- No permanent value tag per stack. A stack is denominations-coloured discs (≤ 4 visible) on the cell / line / corner.
- A value tag appears for **the hovered spot** and **the bet just placed (1.5 s)**, placed by `TagPlacer`
  (generalised from `games/poker/present/LabelPlacer`, moved to `core.ui.v2`) with obstacles = every **number glyph
  box** of the layout + every stack + the other tag. If nothing is free, no tag — the status line shows the hover
  readout instead: `Split 8 – 11 · 5 · pays 17 : 1`.
- Holding **Alt** shows all tags that can be placed without collision (T3 mostly). The Dock readout always shows
  `Bet 55` and `6 spots`.
- Stack positions are snapped off the number glyph: inside-bets on a cell sit in the cell's lower third, line bets on
  the line, so the number stays readable (asserted by a layout test over every bet spot of the layout).

### 2.3 The "Open" empty seat (review)
- Gone. An empty seat is just the **printed betting box** of the felt (35 % print alpha), like a real table.
- When the viewer is not seated: the primary is **Sit down** (enabled; or the reason: `Table full`, `Not enough
  chips for the minimum 10`), the status says `Pick a seat or press Space`, and hovering an empty box lights it with a
  gold ring and the felt word `Sit`. Click = sit (seat index sent when the server supports it; else plain `sit`).
- Others' plates show the name only; an empty slot has no plate.

### 2.4 In-world plates and board (review)
- Plate anchor = the seat's **rail edge, 0.12 block outside the felt** (outside the overhang), 0.35 block above the
  felt; then pushed above the projected felt content and resolved (`PlateLayout`, §7.4). Never over a card.
- The table board: upright, yaw-only billboard, centred over the **dealer side** 1.25 blocks above the felt, also
  pushed above the content and above the plates.
- Legibility 3–8 blocks: `HoloScale` (D9); backing `ink` at 70 %, no shadow, full-bright, ≤ 3 board lines, ≤ 2
  plate lines; LOD with 0.5-block hysteresis.

### 2.5 Other prototype findings
- Hero seat anchor fraction 0.86 (not 0.80), clamped so the hero plate ends ≥ 4 px above the felt bottom; outer seats
  pulled in so full plates stay on the felt; even seat counts use `seats + 1` slots so the viewer is centred.
- Fan step shrinks (≥ 5 px, the rank index stays visible) so 5-card hands never touch the neighbours.
- Dock degradation order (never covers the primary): status → min · tools → Undo + Clear · secondary button dropped
  (Leave stays as the TopBar icon) · chips → 3 (selected stays) · readout 2× → 1× · key-hint plates dropped.
- Sprite path `core/v2` (D11).

---

## 3. Layout system (pure, `core.ui.v2`)

All in `java/src/main/java/dev/nezo/burmaldaholic/core/ui/v2/`. No Minecraft imports. Every method deterministic.

| Class | Status | API (essentials) |
|---|---|---|
| `Box` | **in tree** | record `(x, y, w, h)`; `contains` (half-open), `intersects`, `inside`, `inset`, `offset`, `centred` |
| `Viewport` | **in tree** | `of(fbW, fbH, guiScale)` → `(w, h, k, factor)`; `toLayout(gui)`, `toWindow(layout)` |
| `Tier` | **in tree** | T1/T2/T3 metrics (cards, chips, bars, margin, buttons, tools, plate); `of(w, h)` |
| `TableGrid` | **in tree** | `topBar`, `play`, `dock`, `felt()`, `fx(f)`, `fy(f)`, `columns(from, to, y, h)` (12-col grid) |
| `DockLayout` | **in tree** | `layout(grid, Spec, selectedChip)` → `Dock(chips, firstChip, tools, readout, status, actions, secondary, primary, overflow, compact flags, stepper, hintStrip)`; `Spec.betting(...)`, `Spec.actions(actionW, compactW[, stepperW], primaryW, primaryCompactW)`, `spec.withStatus(statusW)` (phase 2: `statusW = min(cap, max(w(line1), w(line2)))`; short ⇒ compact actions, still short ⇒ an 11-px `hintStrip` above the Dock carries line 2 and is a banner obstacle) |
| `SeatColumn` | **in tree** | `layout(...)` one seat; `crescent(grid, seats)`, `pitch`, `plateW`; `table(grid, seats, cards[], heroSlot, outcomeW[][], valueW[], levelOut[])` resolves collisions |
| `Gate` | **in tree** | `on(action, labelKey, args…)`, `off(action, labelKey, reasonKey, args…)` |
| `HitMap` | **in tree** | `add(id, box, enabled)`, `at(x, y)`, `box(id)`, `targets()` |
| `LayoutAudit` | **in tree** | `add(name, box)`, `allow(prefixA, prefixB)`, `fits(name, textW, pad)`, `problems()` |
| `TextFit` | **in tree** | `fit(text, maxW, widthFn)` ellipsis, code-point safe |
| `TagPlacer` | K lane | move of `LabelPlacer` (poker keeps a deprecated forwarding alias until lane B switches) |
| `FreeSlot` | K lane | `first(regions[], w, h, obstacles[])` → box or empty (ResultBanner, toasts on the felt) |
| `BetView` | K lane | pure bet model, §5.1 |
| `KeyMap` | K lane | key → HitMap id table (`Space` primary, `1–5` chips, `Backspace` undo, `Delete` clear, `R` rebet, `X` ×2, `Esc` leave, game keys) |
| `board.HoloScale` | **in tree** | `lod(d)`, `textPx(base, d, fov, screenH)`, `glyphPx(...)` |
| `board.PlateLayout` | **in tree** | `place(au, av, w, h, order, contentTop, gap, outU, outV, leader)` |

Per-game pure layouts (owned by the game lanes, same package rules): `games/<g>/present/<G>Layout.java` — e.g.
`BlackjackLayout.of(TableGrid, BlackjackView)` → seat columns, dealer fan, shoe, banner regions, HitMap targets.
Roulette: `RouletteLayout` (wheel column, every bet-spot box, number glyph boxes as obstacles). Craps: `CrapsLayout`.
Each returns a record of `Box`es and is audited in a unit test at every size of §11.1.

### 3.7 Resolution policy (phase 2b, RS)
`ViewportPolicy.of(fbW, fbH, guiScale)` → `Viewport(w, h, k, factor, ox, oy, frameW, frameH)`:
1. **Scale**: the player's GUI scale is kept while the GUI is 240–720 px high (and ≥ 320 wide). Below 320 × 240 step
   down; above 720 step up by the smallest whole `m` with `ceil(H / (gs·m)) ≤ 720` (never below 320 wide). Text is
   always one layout px per font px; banner / stamp words keep their own 2× at T3+.
2. **Frame** (`Frame.of(w, h)`): `frameH = min(h, 600)`, `frameW = min(w, ceil(frameH · 16 / 9))`, centred (offsets in
   whole layout px; GUI offset = `ox · factor`, whole GUI px). 1280 × 720 → 1067 × 600; ultrawide 640 × 270 → 480 × 270.
   The screen's `width × height` is the frame; `ScreenEntrance` translates the pose by the GUI offset before scaling;
   `MouseHandlerFitMixin` returns `(gui − offset) / factor`. Outside the frame: the dimmed menu background and the
   generated `core/v2/frame_edge` nine-slice (8-px shadow, rail and gold rims).
3. **Room** (`grid.room()`): `ROOMY` when the frame is ≥ 800 × 450 (1080p@2, 1440p@2/3, 4K@2–4). Tier stays T1/T2/T3
   (ROOMY is always T3). The kit at ROOMY keeps the action key plates; `SeatPlate.draw(..., name, line2, kind, bot)`
   draws a two-line plate (name + stack / last action) when the box is ≥ 26 high. Games opt in for content (histories,
   card sizes; X-RS); nothing is pose-scaled except through ArtScale (MK).
4. Tests click with `Viewport.windowX/windowY` (frame-relative → window px); `toWindow` is scale only.

| Window | Auto | GUI2 | GUI3 | GUI4 | GUI6 |
|---|---|---|---|---|---|
| 1280×720 | 427×240 k3 T1 | 640×360 k2 T3 | =Auto | clamp | clamp |
| 1920×1080 | 480×270 k4 T2 | 960×540 k2 ROOMY | 640×360 k3 | =Auto | clamp |
| 2560×1440 | 427×240 k6 T1 | 1280×720 k2 frame 1067×600 ROOMY | 854×480 k3 ROOMY | 640×360 k4 | =Auto |
| 3840×2160 | 427×240 k9 T1 | 960×540 k4 ROOMY | 1280×720 k3 frame ROOMY | 960×540 k4 ROOMY | 640×360 k6 |

`ViewportPolicyTest` holds this matrix plus odd windows (1366×768, 1280×1024, 854×480, 2560×1080, 3440×1440).

**ROOMY adoption checklist per game (X-RS / motion packages)**: read `grid.room()` in the game's `<G>Layout`; at ROOMY give
the extra space to content (seat plates with `line2`, longer histories: roulette 12→20, Plinko 6→12, craps 8→14;
larger native card sprites via ArtScale); keep every box inside `grid.felt()`; add the 960×540 and 1067×600 frames to
the game's layout test (EN/RU); never pose-scale a region to fill space.

---

## 4. Client kit (`client.ui.v2`)

`java/src/client/java/dev/nezo/burmaldaholic/client/ui/v2/`. Widgets are **not** vanilla `AbstractWidget`s: the
chrome draws and hit-tests by `Box` (D3), so there is exactly one coordinate system. Narration still works: each
target registers a narration supplier with the chrome (`V2Chrome.narrate(id)`), and `Tab` focus walks the HitMap
in order (focused target gets the gold ring; `Enter` presses it).

### 4.1 Hosts
| Class | Does |
|---|---|
| `V2Chrome` | owns `Viewport`, `TableGrid`, `HitMap`, the components below and the watchdog; `layout()` (on init / resize / state change), `render(g, mouse, partial)`, `click(x, y, button)`, `key(code)`, `scroll(x, y, dy)`; per-frame work = drawing only. Phase 2: `Host.layoutOverlay(Dock, HitMap)` (default no-op) is called LAST in `relayout()`; `flash(targetId, reasonKey, args…)` = reason in the status line + 3-px shake of that target (`shakeDx(id)` for host-drawn felt targets). |
| `V2TableScreen<V>` | extends `CasinoTableScreen`; sets image = full GUI (D3); `protected abstract V view(CompoundTag)`, `Flow<V> flow()`, `void layoutPlay(TableGrid, V, HitMap)`, `void renderPlay(...)`; routes `mouseClicked/keyPressed/mouseScrolled` to the chrome; keeps `sendAction`, `state()`, error line → toast. Phase 2: `static registerSettings(Predicate<BlockEntity> applies, Consumer<BlockPos> open)` — the TopBar gear shows only at a table a provider applies to and opens it (bots, R-AB M6; replaces the integrator's `topSettingsHook`, kept deprecated as a bot-table provider until W2 moves). |
| `V2SceneScreen<V>` | same over `CasinoScreen` (Coin Flip, Scratch, Dice Duel, PvP invite / duel). |
| `V2Gallery` (gametest) | `gametest/v2/KitGalleryV2Screen`: every component in every state at the current size; audit overlay. |

### 4.2 Components
| Component | API sketch | Notes |
|---|---|---|
| `TopBar` | `set(title, limits, seats, theme)`, icons `rules / settings / leave` (ids `top.rules`, `top.settings`, `top.leave`), `BalancePill` | title pill gold; limits / seats pills muted; icons 14² pitch 16; Leave always present when seated |
| `BalancePill` | `target(balance)`, `delta(net)` | uses `core.ui.BalanceTicker` (0.6 s), delta pill 2 s via `DeltaFloaters`; chip icon; 2× at T3 |
| `BetBar` | `bind(BetView, BetPort)`; ids `chip.0..4`, `tool.undo/clear/rebet/double`, `readout` | chips filtered by limits; single-spot games: chip click = add to bet; multi-spot: chip arms, felt spots light; right-click chip = remove one; keys 1–5; shows the pending chip (§5.2) |
| `PrimaryButton` | `set(Gate, keyHint)`; id `primary` | gold 92×30 / 132×36; disabled = dim + the status line shows the gate's reason; a click on a disabled primary shakes it and re-shows the reason (never nothing) |
| `ActionRow` | `set(List<Gate>, keys)`; ids `action.<action>` | equal widths, danger style for fold / surrender, ≤ 5 + primary, compact (no key plates) when narrow |
| `Stepper` (phase 2) | `chrome.stepper.set(Gate minus, Gate plus, Component value, int keyMinus, int keyPlus)`, `clear()`; ids `stepper.minus`, `stepper.value`, `stepper.plus` | ACTIONS Dock slot left of the actions (laid out by `DockLayout`, so it survives every relayout — R-AB M3); − / + presses reach `Host.onGate("stepper.minus" \| "stepper.plus", gate)`; disabled ⇒ reason + shake; wheel over the value = − / + |
| `StatusLine` | `set(line1, line2)`, `reason(Gate)`, `timer(TimerBar)` | two lines, ellipsised (`TextFit`); priority: watchdog > error > gate reason > game hint > timer |
| `TimerBar` | `set(secondsLeft, total)` | 3 px, gold → red under 5 s |
| `SeatPlate` | `draw(box, name, kind)` kind = YOU / ACTING / OTHER | name only, avatar 14², ellipsised; no numbers |
| `ChipStack` | `draw(box, amount, scale)` | ≤ 4 discs (12 / 16 px) by greedy denominations; value pill drawn by the column, not by the stack |
| `ValuePill` | `draw(box, text, style)` style = YOU / OTHER / WIN / LOSE / PUSH | 1×, 11 high |
| `Stamp` | `draw(box, word, kind, scale, ageMs)` kind = WIN / LOSE / PUSH / GOLD; `dot(box, kind)` | pop-in 120 ms (reduce motion: none) |
| `TotalPill` | `draw(box, text, scale)` | the outcome slot before settle |
| `ResultBanner` | `show(net)`, `show(Component word, long net, int ms)` (net 0 ⇒ the word only, e.g. `17 RED` for a spectator); `regions(regions, obstacles)`, `obstacles(List<Box>)` (more obstacles for this layout pass); placed by `FreeSlot` | full 2× look (T2 232×28, T3 300×36) or the compact one (200×20, 1× bold: always at T1, and at T2/T3 when the full one finds no place); 1.5 s then folds into the status line; ≥ 10× bet hands over to `CelebrationOverlay` |
| `Toast` | `V2Toast.show(kind, text)` | errors and server notices **on the felt** (top-right of PlayArea via `FreeSlot`), not vanilla toasts over the TopBar; replaces the 60-tick error line |
| `FeltPrint` | `draw(box, text, alpha)` | decor layer, drawn last-checked against content boxes |

Colour tokens: extend `client/fx/CasinoPalette.java` with the REDESIGN §5 tokens as `V2_*` constants (K lane only).
Art: nine-slices from `core/v2/*` sprites (§9); every component has a flat-colour fallback when a sprite is missing
(the gallery test asserts both).

---

## 5. The bet flow contract: impossible to get stuck

### 5.1 Pure model
`core.ui.v2.BetView` (record, built by each game's `view()` from the state tag):
`seated`, `canSit`, `phase` (BETTING / LOCKED / ACTING / SETTLING / IDLE), `bettingOpen`, `min`, `max`,
`balance`, `myTotal`, `spots` (id → amount, for multi-spot), `canUndo`, `canClear`, `canRebet`, `rebetAmount`,
`canDouble`, `waitingFor` (name or ""), `secondsLeft`. `BetView.chips(denoms)` → offered chips (≤ 5, filtered by
`max` and `balance`; the smallest affordable is always offered when `balance ≥ min`).

`games/<g>/present/<G>Flow.java` (pure) maps the game's view to gates:
`Gate primary(V)`, `List<Gate> actions(V)`, `Gate tool(String id, V)`, `Optional<String> hintKey(V)`.

### 5.2 Client rules (BetBar + watchdog, in the kit)
1. Every interactive element is **always visible** in its phase; a disabled one is drawn dim and **clicking it shows
   its reason** (status line + 3-px shake). No hidden-because-unavailable bet UI.
2. Not seated ⇒ BetBar dimmed with reason `Sit down to bet`, primary `Sit down` (§2.3). Seated + betting closed ⇒
   reason `Bets open in 4 s` / `Round in progress`.
3. Chip click / spot click ⇒ send the action, draw a **pending** chip (60 % alpha) on the spot, start the watchdog.
   State echo with a higher `seq` or a changed spot amount ⇒ pending cleared. Server error ⇒ pending cleared, error toast
   with the server's reason. **No echo in 40 ticks** ⇒ status `The table did not answer — try again`, a `resync`
   action is sent (core action handled by the base BE: re-sends the state), pending cleared.
4. A multi-spot click **without an armed chip** arms the last-used (or smallest affordable) chip and places it; it never
   does nothing.
5. Every reason is a translation key from the core v2 set (§10) or the game's own; a test asserts every reason key
   exists in EN and RU.
6. Keyboard parity: every pointer action has a key; the key hint plate shows it.
7. Phase 2 (K2b). **Acks**: the server keeps `{epoch, count}` per player (`ack_epoch`, `ack` in `baseState`; the epoch
   is a random non-zero id of the entry, so a reloaded / replaced table or a pruned and re-created entry is a NEW
   counter). The client waits for `ack ≥ expected`, `expected = max(expected, lastAck) + 1` per send (two clicks wait for
   two acks); a changed epoch — or, without an epoch, a counter that went back — re-bases and drops the pending action.
   Entries of players who neither sit nor watch are pruned (leave, and a 30 s sweep). **One state per action**: while
   `handleAction` runs, states for the sender are deferred to one at its end (other viewers are synced at once; an
   error message goes out before that state). **Held keys**: a key held down presses its chip / primary / action /
   stepper once; the auto-repeat is swallowed until `keyReleased`. **Key plates**: named keys use vanilla's
   `key.keyboard.<name>`; letters and digits show the key cap (`H`, `1`); vanilla has no `key.keyboard.h`. The primary's
   plate is dark ink with gold text; the disabled primary is slate (one disabled style everywhere).
   **Status width**: the Dock gives the status `min(40 % of the Dock, max(w(line1), w(line2)))` when it can (actions go
   compact first); otherwise line 2 moves to an 11-px strip above the Dock (`Dock.hintStrip`, a banner obstacle).
   **Vanilla toasts** neither render nor update while a v2 screen is open (`ToastManagerV2Mixin`).

### 5.3 Exhaustive tests
Per game: `<G>FlowTest` (unit) enumerates the view's phases × seated × balance (0, < min, ≥ min, ≥ max) × bet (0, > 0)
× betting open/closed and asserts: (a) at least one enabled gate **or** a waiting reason with `secondsLeft ≥ 0` or a
`waitingFor` name; (b) no disabled gate without a reason (by type); (c) when `bettingOpen && seated && balance ≥ min`
a chip is enabled. This is the test that would have caught v0.1.1's "can't place a bet".

### 5.4 Hotfix compatibility
The v0.1.2 hotfix (bet bugs, UTH bet UX, canvas click transform, PvP modes) merges **first**. v2 screens reuse its
server-side fixes unchanged; its client-side click-transform fix becomes dead code once a screen switches to v2 (D3)
and is deleted with the old screen. Its real-flow tests are kept and re-pointed at v2 widgets by id
(`RealFlow.click("primary")` instead of coordinates). The only base-class edits of the kit (§12) are additive hooks
in `CasinoTableScreen` / `CasinoScreen` / `FitScaled` / `MouseHandlerFitMixin`, rebased on the hotfix.

---

## 6. Input, hit-testing and GUI scale

- **Transform**: `Viewport.factor = k / guiScale`. Drawing: the host pushes `pose().scale(factor)` around the whole
  screen extract (generalising `FitScaled.push`, which today only handles `< 1`). Mouse: `MouseHandlerFitMixin`
  divides by the factor when `!= 1` (today `< 1` only). One property, `FitScaled.fitScale()`, feeds both. Tooltips and
  vanilla widgets inside a v2 screen inherit the transform.
- **Hit-testing**: `V2Chrome.click` converts once (`toLayout`) and asks `HitMap.at` (topmost wins). Felt spots,
  chips, tools, actions, primary, plates (hover only), TopBar icons all live in the same map; z-order = registration
  order (felt first, Dock last). Disabled targets stay in the map (§5.2-1).
- **Scroll**: raise stepper (poker), Plinko risk, roulette chip cycling — routed by HitMap target under the mouse.
- **Resize**: `init()` rebuilds layout; the chrome keeps armed chip / focus / pending across re-layouts by id.
- **Unit-testable**: all of it is pure except the final draw; `RealFlow` (gametest) uses `HitMap.box(id)` and
  `Viewport.toWindow` to click real pixels, so tests click exactly where players do at each GUI scale.

---

## 7. The in-world board kit

### 7.1 Packages and classes
`java/src/client/java/dev/nezo/burmaldaholic/client/board/`:

| Class | Does |
|---|---|
| `BoardRenderer<T, S extends BoardState>` | extends `SpectatorBlockEntityRenderer`; replaces the generic parts of `client/table/CardTableRenderer` (which becomes a thin subclass kept for poker/UTH until lane B ports them). Computes facing, overhang fallback flag, distance, `HoloScale.Lod` with hysteresis; calls `fill(be, state)`; submits felt → cards → chips → holo (board + plates) in that order. |
| `BoardState` | pooled arrays: cards (≤ 32), stacks (≤ 12), texts (board ≤ 3 lines, plates ≤ 8 × 2 lines), plate anchors, content top; `FormattedCharSequence` cache keyed by `(seq, lang)` — rebuilt only when the public data changes. |
| `FeltPainter` | felt top 2.25 × 1.25 (crescent) / 2.0 × 1.4 (roulette, craps) / 2.0 × 2.0 (poker oval) + rail lip, render-only overhang along `FACING`; 1 × 1 fallback when blocked (flag cached in the BE on neighbour update). |
| `CardPainter` | cards 1.5 × real (0.22 × 0.30 block) from `faces_w2.png`, lift 1/32, tilt 20° toward their owner; flip via `dealt(...)` (moved from `CardTableRenderer`). |
| `ChipPainter` | disc stacks (≤ 6 discs, top quad + 8-quad band) from `entity/core/board/chips_w.png`. |
| `HoloBoard` | the table board: yaw-only billboard, text px from `HoloScale`, backing quad (`holo_backing`), lines title / state / turn / result. |
| `SeatPlates` | projects plate anchors into the camera plane (camera `left/up` from `CameraRenderState`), runs `PlateLayout`, draws backing + 2 lines + optional 1-px leader. |
| `BoardStats` | debug counters (quads, lines, ns per submit) shown by `/casino debug board` and read by the perf GameTest. |

Pure: `core.ui.v2.board.HoloScale` and `PlateLayout` (in tree, tested), `BoardGeometry` (W lane: felt sizes,
anchors per table type, card / chip sizes in blocks).

### 7.2 Public data
- `core/table/TablePublic.java` (W lane): `writeSeats(CompoundTag, TableSeats)` / `readSeats(CompoundTag)` →
  `SeatPub[] (index, uuid, name ≤ 16, bot)`. The base `CasinoTableBlockEntity` gets one hook: `protected void
  writePublic(CompoundTag)` called from a new non-final default `getUpdateTag` and from overrides via
  `super.writePublic(t)`, and `sendBlockUpdated` (throttled ≤ 4/s) when seats change.
- Per game `games/<g>/present/<G>Pub.java` (pure, encode/decode unit-tested like `PokerPub`):
  **new** `BlackjackPub` (dealer up card + revealed cards, per seat hand(s) cards, bet, total, outcome, acting seat,
  seq / startTick for the deal timeline) and `BaccaratPub` (player / banker cards, totals, bets per box, winner);
  existing poker / UTH / roulette / craps / Plinko / Wheel syncs gain the fields the board shows (result line,
  per-seat net) — lane-owned, no new packets.

### 7.3 Size and legibility
- Board text px = `HoloScale.textPx(1/80, d, fov, screenH)`; plates `1/100` base. Reads `options.fov()` and the
  window height; glyph ≥ 13 px from 3 to 8 blocks (tested). Full-bright, depth-tested, polygon offset, no shadow,
  backing `ink` 70 %.
- LOD (`HoloScale.lod` + 0.5 block hysteresis): ≤ 8 everything + tweens; 8–16 board state/result lines + cards +
  chips, settled; 16–32 felt + cards + chips, no text; > 32 block model.

### 7.4 Plates
Anchor per seat at the rail edge outside the felt, 0.35 block up. Each frame (≤ 8 blocks): project anchors and the
content's top (max over cards / stacks / wheel of their projected top) into the camera plane; `PlateLayout.place`
with priority viewer → acting → nearest; draw at the resolved positions; leader line when displaced > 0.18 block.
Plates replace the `[BOT] name` tags of seated bots (bots keep tags away from tables — `bots/client` gets a one-line
"seated at a table with a board" check, W lane).

### 7.5 Budget (per table, per frame)
≤ 32 card quads, ≤ 96 chip quads, ≤ 2 backings + 8 plates, ≤ 19 text lines, 1 wheel head + 1 ball; text sequences
cached per `seq`; ≤ 0.15 ms per table on the render thread, 20 tables ≤ 3 ms. Asserted on **counts** in the perf
GameTest (CI is software-rendered; times are reported, not asserted).

### 7.6 Per-table board lanes
| Table | Lane | Felt / objects | Board lines | Plate lines |
|---|---|---|---|---|
| Blackjack | A | dealer fan (hole card face-down), hands + stacks, shoe | `Blackjack 1–100` / `Dealer 10` / `Steve – 18 s` → `Steve +350` | `Steve` / `50 · 15` → `+75 BLACKJACK` |
| Baccarat / chemmy | A | Player / Banker hands, stacks on P / B / T | `Player 4 · Banker 7` / `BANKER WINS` | name / bet + box |
| Hold'em | B | board, pot, backs, button | `Pot 120 · Turn` / acting / winner + hand | name / stack · last action |
| UTH | B | dealer + board, bet circles | street / result | name / ante · outcome |
| Roulette | C | bowl + head + **ball**, layout with stacks coloured by player | `No more bets` / `26 BLACK` / top win | name / on table · net |
| Craps | D | puck, dice 0.12 block, pass / field / odds stacks | `Point 8` / `3 + 5 = 8` | name / on table · net |
| Plinko, Wheel | E | upright boards (Plinko gains pegs + ball + bins) | last multiplier | the player only |

### 7.7 As built (lane W) — how a game plugs in
```java
public final class BlackjackBoardRenderer extends BoardRenderer<BlackjackTableBlockEntity, BoardState> {
  public BlackjackBoardRenderer(BlockEntityRendererProvider.Context c) { super(c); }
  @Override public BoardState createRenderState() { return new BoardState(); }
  @Override protected Felt felt(BlackjackTableBlockEntity be) { return Felt.CRESCENT; }
  @Override protected void fill(BlackjackTableBlockEntity be, BoardState s, float pt) {
    BlackjackPub p = be.clientPub();                       // PUBLIC data only
    s.line(BoardState.TITLE, key, () -> Component…, BoardState.GOLD);   // built only when key/lang change
    float[] d = BoardGeometry.dealerSlot(s.felt, 0, 2); s.card(d[0], d[1], 0, cardId);   // −1 = back
    float[] seat = BoardGeometry.crescentSeat(i, 7);  s.card(…); s.stack(x, z, amount);
    BoardRenderer.seatPlates(s, be, BoardGeometry::crescentSeat, (i, pub, plate) -> plate.detail(k, () -> …, BoardState.GOLD).acting(i == acting));
  }
}
// register (game client module, after core): BlockEntityRendererRegistry.register(type, BlackjackBoardRenderer::new)
```
- Coordinates are table-local (`BoardGeometry`: x right, z toward the players, felt at `TOP`); the kit rotates by
  FACING, shrinks everything onto the 1 × 1 fallback felt when the overhang is blocked (`contentScale`), and computes
  LOD / text px. `s.content(x, y, z, r)` declares a wheel / dice the plates must clear; `s.cache(Cache::new)` keeps a
  per-table game cache (timelines). Interim boards for blackjack / baccarat (`SeatsBoardRenderer`, names only) are
  registered by `BoardKit` from the core client module; a game's own registration (later) overrides them.
- Seat names: `CasinoTableBlockEntity#writePublic` writes `TablePublic` into the default update tag. **A game that
  overrides `getUpdateTag` adds one line `writePublic(t);`** (poker, UTH, roulette, craps today), else its plates have no
  names. Bot avatars (`[BOT] …` text displays) are hidden client-side near a table with a board.
- Sprites: `textures/entity/core/board/` from `tools/assets/modules/board.mjs` — `faces_w2.png` cells are 37 × 49
  (a dedicated in-world face: 2× index, big pip), not 32 × 44.
- Spacing (worldgen / casino buildings): the overhang reaches 0.625 block left/right and 0.125 front/back
  (crescent), 0.5 / 0.2 (rect), 0.5 all round (oval). A table needs the blocks beside it at table level (left / right, and
  front / back: even 0.125 counts) free of colliding blocks — one free block on each long side is the rule; two tables side by side need **2 free blocks between them**
  (centre pitch ≥ 3), else both fall back to the 1 × 1 top.

---

## 8. Animation integration

- Positions come from the layout: animators ask the game layout for target boxes (`layout.seat(i).cards()` card k,
  `betBar.chipBox(d)`, `layout.spot(id)`). `CardAnimator`, `client.anim.Tween`, `TimelinePlayer`, `AnimClock` and the
  PTL builders (`core.anim`, per-game `*Beats`) are reused unchanged — no new tween library.
- Chip flights: BetBar chip box → felt spot (place), spot → rack (lose), rack → spot (win); same timings as today
  (`client/table/fx`). The value pill switches to the net when the win flight lands.
- Stamps pop on the settle beat of the shared timeline (not on packet arrival), so the screen and the in-world board
  stamp on the same tick (fidelity F10). The ResultBanner starts after the last stamp.
- Balance: `BalancePill` uses `BalanceTicker`; the delta pill uses `DeltaFloaters`. Celebrations: wins ≥ 10× bet
  call `ClientFx.celebrate(...)` (existing tiers) — the banner yields to it.
- Reduce motion (`FxSettings.reduceMotion()`): no pop / shake / slide; everything snaps to the terminal frame.
- In-world: `BoardRenderer` samples the same timelines (`SpectatorBlockEntityRenderer.levelMs`) — deal arcs, flips,
  ball path (`RouletteBallPath`), dice path — at LOD FULL; terminal frame otherwise.

---

## 9. Sprites (`tools/assets`)

Rule unchanged: generated only (`cd tools && npm ci && npm run gen:assets`, then `npm run check:assets && npm test`);
never hand-edited; one owner per module file.

| Module (file) | Owner lane | Outputs (paths under `java/src/main/resources/assets/burmaldaholic/textures/`) |
|---|---|---|
| **new** `modules/ui2.mjs` + `modules/ui2/*.mjs` | K | `gui/sprites/core/v2/`: `chip_<d>` 20²/24²/32² (value digits in the centre, language-neutral), `chip_selected_ring`, `felt_disc_<d>` 12²/16², `value_pill{,_you,_win,_lose,_push}` 9-slice, `plate{,_you,_acting}`, `stamp_{win,lose,push,gold}` + `stamp_dot_*`, `button{,_primary,_danger}` × {normal, hover, pressed, disabled}, `key_hint`, `icon_button` + `icon/{undo,clear,rebet,double,rules,settings,leave}`, `topbar`, `dock`, `banner`, `toast`, `felt_vignette`, `focus_ring` |
| **new** `modules/board.mjs` + `modules/board/*.mjs` | W | `entity/core/board/`: `faces_w2.png` (32 × 44 per card + backs), `chips_w.png`, `holo_backing.png`, `rail_w_<θ>.png`, `felt_w_<θ>.png` |
| `modules/cards.mjs` (+ `cards/*`) | A | `gui/core/cards/faces_xl*.png`, `backs_xl.png`, `rail_arc_<θ>_<tier>.png`; deletes `backdrop_<θ>`, `table_*_compact` when A and B switched (cleanup lane Q runs the deletion) |
| `modules/tables.mjs` (+ `tables/roulette.mjs`, `tables/wheel.mjs`) | C | `gui/tables/roulette/wheel_bowl_m_<θ>`, `wheel_head_m` (T2 104², T3 144²), `gui/sprites/tables/roulette/history_pill_{red,black,green}` |
| `modules/tables/craps_v2.mjs` (new helper, + 1 import line in `tables.mjs`) | D | `gui/tables/craps/layout_v2_<θ>_<tier>.png` if the generated layout replaces the drawn one |
| `modules/extras.mjs` (+ `extras/*`) | E | scene fills for the PlayArea (Plinko board 272 wide, wheel legend art, scratch ticket), deletes the frames / marquees / banners after switch |
| `modules/core.mjs` | K (only `menu` icons touched if needed) | — |

Who regenerates: each lane runs `node assets/gen-assets.mjs --module <its module>` while iterating, the **full**
`npm run gen:assets && npm run check:assets && npm test` before every commit that touches `tools/assets`. On a
merge conflict in a generated PNG: take either side, re-run the generator, commit the result in the merge. Hand
edits of PNGs are rejected by `check:assets`. Deprecated outputs are deleted by removing their generator code (never
by deleting the PNG only).

---

## 10. Strings

Source of truth `docs/design/STRINGS.md` (EN + RU, RU plurals `.p1/.p21/.p2/.p5`), language fragments generated per
module (`java/src/main/lang/<module>/`) — per-module files, so lanes don't conflict in generated JSON.

| Section in STRINGS.md | Keys | Owner |
|---|---|---|
| `## core` → new `### Table UI v2 kit (ui-v2.md §10)` | `gui.burmaldaholic.core.v2.*`: `bet`, `on_table`, `spots.p*`, `tool.{undo,clear,rebet,double}`, `key.{space,backspace,…}`, `primary.{sit,deal,rebet,leave}`, `reason.{place_bet,sit_to_bet,below_min,above_max,not_enough,table_full,bets_closed,bets_open_in,round_in_progress,waiting_for,no_answer,spinning,rolling,dealing}`, `status.{pick_seat,click_layout,chips_add,next_round_in}`, `stamp.{win,lose,push,bust,blackjack,natural,surrender}`, `banner.{you_win,you_lost,push}`, `sit` | K |
| `## core` → new `### In-world board (ui-v2.md §7)` | `gui.burmaldaholic.core.board.*`: `turn`, `seconds`, `no_more_bets`, `result_line`, `dealer_draws`, … | W |
| each game's section → new `### Table UI v2 (lane X)` | game-specific labels, reasons, felt prints, board lines | the game's lane |

Rules: buttons are sized by the measured current-language label (layout takes widths); every new string has a RU
row that passes the RU layout test (§11.1 runs the real `en_us` / `ru_ru` widths through the stub font = 6 px/char,
and the client gallery test measures the real font). No `Component.literal` (checkNoLiterals); digits on chip sprites
are art, not text. Removed keys: delete rows only in the cleanup lane Q (other branches may still reference them).

---

## 11. Test plan

### 11.1 Pure layout tests (unit, `src/test/.../core/ui/v2/` and per game `games/<g>/present/`)
- Sizes: 320×240, 427×240, 455×256, **480×270**, 512×288, 569×320, 600×338, **640×360**, 683×384, 854×480, 630×270
  (21:9), 960×539, plus a sweep of every 13 px height × 37 px width between 4:3 and 21:9 (in tree).
- Viewport cases: 1920×1080 @1–4, 2560×1440 @2–4, 3840×2160 @4, 1280×720 @4, 1366×768 @2 (in tree).
- Per screen: no box outside its region, no unexpected overlaps (`LayoutAudit`), primary present at the right edge,
  every label ≤ its box with EN and RU widths, selected chip visible, Dock `overflow == 0`.
- Per game: `<G>FlowTest` (§5.3); roulette: every spot × number glyph boxes; craps: every bet box; poker: 2–6 seats
  oval; baccarat: 7 plates; seat collision with 1–7 seats × 2–5 cards (in tree for the crescent).
- Board: `HoloScale` legibility, `PlateLayout` random 2000-run property test (in tree); `<G>Pub` encode/decode.

### 11.2 REAL-FLOW client GameTests (one class per lane, `gametest/v2/<game>/`)
Shared helper `gametest/v2/RealFlow.java` (K): `place(type)` puts the table next to the player and **uses the block**
(`useItemOn` from the player, as a right click), `size(fbW, fbH, guiScale)` resizes, `click(id)` reads the open
screen's `HitMap.box(id)` and clicks its centre in window px (`Viewport.toWindow`), `key(code)`, `waitState(pred,
timeout)` on the SERVER block entity, `shot(name)`. Every wait has a timeout; `close()` closes screens and waits
before the world closes. Opt-in filter `BURMALDAHOLIC_CGT=<lane>` so a lane can run only its class (one client run
machine-wide).

Each game plays **a full round by clicking** at **480 × 270 (1920×1080 @4)** and **640 × 360 (2560×1440 @4 and
1920×1080 @3)**, plus one run at 427 × 240 @3: sit (click `primary`) → assert seated on the server → bet (click
`chip.k`, then the felt spot or nothing for single-spot) → assert stake on the server → primary (Deal / Spin / Roll)
→ assert phase → decisions (`action.stand`, …) → settle → assert balance change = the server's settle → Rebet →
assert rebet. Negative paths: click `primary` with no bet → assert status shows `reason.place_bet` and nothing sent;
balance below min → reason; a dropped packet (test hook drops one `bet` echo) → watchdog reason + resync. Baccarat
also chemmy; UTH also Trips + Play 4×/2×/1×; poker: buy-in → fold / call / raise via stepper; extras: each game;
PvP: invite accept + one duel per mode (wheel, plinko, scratch, slot showdown, coin).

### 11.3 Screenshots, before / after
`RedesignCaptureClientGameTests` (Q lane updates its flows to v2 ids) captures every screen at 1920×1080 and
2560×1440 GUI 4 and the world at 3 / 5 / 8 blocks. A contact-sheet script (`docs/design/visual/v2/compare.py`, Q)
puts mockup | before | after side by side per screen into `docs/design/visual/v2/after/`. Review gate: the design
owner signs off each pair; the orchestrator's four review points (stamps, roulette tags, empty seats, plates) are
explicit checklist items.

### 11.4 In-world capture and perf
`gametest/v2/board/BoardClientGameTests` (W): blackjack + roulette + poker tables with bots mid-round, camera at
3 / 5 / 8 / 12 blocks, 0° and 30° to the side; asserts `BoardStats` counts per table (≤ budget), plate rectangles
disjoint in screen space (read back from the renderer's last layout), board visible at 8 blocks; 20-table scene
reports ms.

---

## 12. Lanes, file ownership, merge order

Paths relative to `java/src/` unless noted. "Owns" = only that lane edits; others ask.

### K — Kit (FIRST; thin contract in the first commit, then the components)
Owns: `main/.../core/ui/v2/**` (skeleton in tree), `client/.../client/ui/v2/**`, `gametest/.../gametest/v2/RealFlow.java`,
`gametest/.../gametest/v2/KitGalleryV2Screen.java`, `gametest/.../gametest/v2/KitV2ClientGameTests.java`,
`test/.../core/ui/v2/**`, `tools/assets/modules/ui2.mjs` + `ui2/**`, STRINGS `core › Table UI v2 kit`,
additive hooks in `client/.../client/table/CasinoTableScreen.java`, `client/.../client/ui/CasinoScreen.java`,
`client/.../client/ui/FitScaled.java`, `client/.../core/mixin/client/MouseHandlerFitMixin.java`,
`client/.../client/fx/CasinoPalette.java` (V2 tokens), the `resync` core action in
`main/.../core/table/CasinoTableBlockEntity.java` (5 lines), and **`gametest/resources/fabric.mod.json`**: K registers
every lane's test class up front (stubs that return immediately) so no lane edits it.
Contract commit (K0) = all public signatures of §3–§5 compiling with stub drawing + RealFlow + stubs; merge K0 as soon
as it compiles and its unit tests pass. K1 = the drawn components + gallery (lanes may start against K0).

### W — Board kit (parallel with K, merges second)
Owns: `client/.../client/board/**`, `main/.../core/ui/v2/board/**` (skeleton in tree), `main/.../core/table/TablePublic.java`,
the `writePublic` hook in `CasinoTableBlockEntity`, refactor of `client/.../client/table/CardTableRenderer.java` onto
`BoardRenderer`, `client/.../bots/client/*` tag suppression at tables, `tools/assets/modules/board.mjs` + `board/**`,
`gametest/.../gametest/v2/board/**`, STRINGS `core › In-world board`.

### A — Blackjack + Baccarat
Owns: `client/.../games/blackjack/client/**`, `client/.../games/baccarat/client/**` (screens rebuilt on
`V2TableScreen`; **new** `BlackjackBoardRenderer`, `BaccaratBoardRenderer`), `main/.../games/{blackjack,baccarat}/present/**`
(Flow, Layout, Pub), the public-tag lines in `BlackjackTableBlockEntity` / `BaccaratTableBlockEntity` (getUpdateTag +
sync calls only — no rules), `client/.../client/table/cards/**` (shared card-table pieces; B consumes), `tools/assets/modules/cards.mjs`
+ `cards/**`, `gametest/.../gametest/v2/{blackjack,baccarat}/**`, `test/.../games/{blackjack,baccarat}/present/**`,
STRINGS `blackjack`, `baccarat` v2 subsections.

### B — Hold'em + UTH
Owns: `client/.../games/{poker,uth}/client/**`, `main/.../games/{poker,uth}/present/**` (incl. `LabelPlacer` → alias
of `TagPlacer`, `TableWorld` sizes via `BoardGeometry`), public-tag lines in their BEs, gametests + tests of both,
STRINGS `poker`, `uth` v2 subsections. Consumes A's XL card atlas (A merges first or B waits for it).

### C — Roulette
Owns: `client/.../games/roulette/client/**`, `main/.../games/roulette/present/**` (new `RouletteLayout`, `RouletteFlow`),
roulette public sync fields, `tools/assets/modules/tables.mjs` + `tables/{roulette,wheel,kit,theme,draw}.mjs`,
gametests + tests, STRINGS `roulette` v2.

### D — Craps
Owns: `client/.../games/craps/client/**`, `main/.../games/craps/present/**`, craps public sync fields,
`tools/assets/modules/tables/craps_v2.mjs` (+ one import line in `tables.mjs`, rebased after C), `tables/craps.mjs`,
`tables/dice.mjs`, gametests + tests, STRINGS `craps` v2.

### E — Extras + PvP
Owns: `client/.../games/extras/client/**` (incl. `pvp/**`), `client/.../pvp/client/**`, `client/.../client/pvp/kit/**`,
`main/.../games/extras/present/**` (new Flows/Layouts), Plinko upright board in `PlinkoMachineRenderer`, Wheel plates,
`tools/assets/modules/extras.mjs` + `extras/**`, gametests + tests, STRINGS `extras`, `pvp` v2.

### Q — Capture, comparison, cleanup (LAST)
Owns: `gametest/.../gametest/design/RedesignCaptureClientGameTests.java`, `docs/design/visual/v2/after/**`,
`compare.py`, deletion of deprecated classes (`client/table/fx/TableChrome`, old `ChipButton` / `TableButton` primaries,
`FitScaled` users that no longer exist, `CardTableScreen` canvas maths), deprecated sprites (by removing generator
code in the owning modules, coordinated), removed STRINGS rows; final `build`, `build -Pmc=26.3`, `runClientGameTest`.

### Merge order
1. **v0.1.2 hotfix** (bet bugs, UTH, click transform, PvP) → `v012-base` successor.
2. **K0** (contract) → 3. **W0** (board contract) → 4. **K1**, **W1** (drawn kit, board base + blackjack-agnostic painters).
5. Game lanes in parallel, merged as they pass review: **A** (reference implementation; also fixes the "can't bet"
   class for blackjack/baccarat end-to-end) → **C** → **D** → **B** → **E**. Each rebases on the previous merge;
   shared-file contacts are only STRINGS sections (own subsections), `tables.mjs` (C before D) and generated PNGs.
6. **Q**.

---

## 13. Risks

| Risk | Mitigation |
|---|---|
| Hotfix and v2 both touch `CasinoTableScreen`, the click transform and the four bet screens. | Hotfix merges first; v2 replaces those screens wholesale (no line-level merge); K's base edits are additive and rebased; the hotfix's real-flow tests are re-pointed, not dropped. |
| Bets still impossible for a server-side reason v2 cannot see. | `FlowTest` + real-flow tests assert the **server** stake after clicking; watchdog + `resync` turn silent failures into a visible reason. |
| Step-up scaling (D2) changes mouse maths for every `FitScaled` screen. | Same single factor for draw and mouse; `Viewport` unit-tested; K's gallery GameTest clicks every component at 1920×1080 @2 and 3840×2160 @4. |
| In-world overhang clips into neighbouring blocks / other tables. | Fallback to 1 × 1 top when blocked (cached flag); coordinate with the worldgen lane: casino layouts keep one free block on both long sides. |
| Text-heavy board costs frame time with 20 tables. | Cached sequences per `seq`, LOD with hysteresis, counts asserted, ms reported by `BoardStats`. |
| Plates jitter when the camera moves (re-resolved each frame). | Priority order stable; resolution in the camera plane depends only on yaw/pitch; small moves keep the same candidate (ties prefer the previous frame's shift — W keeps a 1-frame memory per plate). |
| RU strings overflow fixed buttons. | Widths are measured and fed to the pure layout; RU widths in unit tests; compact fallbacks defined (§2.5). |
| Generated PNG merge conflicts across lanes. | One module file per lane, regenerate on conflict, `check:assets` in CI. |
| One client GameTest at a time machine-wide slows 6 lanes. | `BURMALDAHOLIC_CGT=<lane>` filter; pure tests carry most of the layout coverage. |
| Blackjack/baccarat BEs gain a public tag (new sync) while hotfix devs touch them. | Lane A adds only `getUpdateTag`/`writePublic` + `sendBlockUpdated` calls after the hotfix merged; no rule code touched; `BlackjackPub` encode/decode unit-tested and never carries the hole card face-down. |

---

## 14. Skeleton inventory (in this commit)

| File | What |
|---|---|
| `java/src/main/java/dev/nezo/burmaldaholic/core/ui/v2/Box.java` | geometry |
| `.../core/ui/v2/Viewport.java` | virtual GUI, step up / down, single factor |
| `.../core/ui/v2/Tier.java` | T1–T3 metrics, tier by width and height |
| `.../core/ui/v2/TableGrid.java` | TopBar / PlayArea / Dock, 12-col grid, fractions |
| `.../core/ui/v2/DockLayout.java` | Dock in betting and actions modes with the degradation order |
| `.../core/ui/v2/SeatColumn.java` | seat column, crescent anchors, collision-resolving `table(...)` |
| `.../core/ui/v2/Gate.java` | primary/action state with mandatory reason |
| `.../core/ui/v2/HitMap.java`, `LayoutAudit.java`, `TextFit.java` | hit-testing, audit, ellipsis |
| `.../core/ui/v2/board/HoloScale.java`, `PlateLayout.java` | in-world legibility and plate collision avoidance |
| `java/src/test/java/dev/nezo/burmaldaholic/core/ui/v2/TableLayoutV2Test.java` | viewport, tiers, Dock EN/RU at all sizes, crescent 1–7 seats × 2–5 cards, hit map, gates, ellipsis |
| `.../core/ui/v2/BoardMathTest.java` | legibility 3–8 blocks at 1080p/1440p FOV 70/90; plates above felt and disjoint (2000 random tables) |

Run: `./gradlew test --tests 'dev.nezo.burmaldaholic.core.ui.v2.*'` (11 tests, green).

---

## 15. Kit as built (K0 contract; lane K)

How a game lane uses the kit. Signatures are stable; later K commits only add.

**Pure (`core/ui/v2`)** — `BetView` (record + `builder()`; `chips()` = the tray: ≤ 5 consecutive denominations from the
largest one ≤ min, filtered by max and balance), `BetGates` (shared gates: `chip(d, v)`, `tool(id, v)`, `sit(v)`,
`primary(action, labelKey, v)`, `betOrRebet(...)`, `waiting(...)`), `Flow<V>` (`primary`, `actions`, `bet`, `tool`,
`chip`, `hintKey`/`hintArgs`; defaults use `BetGates`), `FlowCheck.problems(flow, view)` (§5.3 — call it from every
`<G>FlowTest` over the enumerated views), `V2Keys` (all kit translation keys), `KeyMap` (`standard()` + `bind(keyName, id)`, logical key names resolved by the client's `V2Input` — never key codes, v0.1.3;
ids in `KeyMap.Ids`: `primary`, `secondary`, `chip.N`, `tool.<undo|clear|rebet|double>`, `action.<a>`, `felt.<id>`,
`seat.<i>`, `top.<rules|settings|leave>`, `readout`), `Stamps` (`Outcome` → word key + colour kind; `levels(wordW, hero)`
for `SeatColumn.table`, `scaleOf(level, hero)` back), `FreeSlot`, `TagPlacer` (Box version of `LabelPlacer`; STRICT =
no tag when nothing is free), `AckWatchdog` (40-tick echo watchdog; `state(epoch, ack)`, §5.2-7),
`BetGates.armDefault(chips, min, balance, lastUsed)` (the chip of an unarmed felt click; never below the minimum when one
fits).

**Server** — `CasinoTableBlockEntity`: every handled action bumps a per-player counter written as `ack` (with
`ack_epoch`) in `baseState`, and the acting player gets exactly ONE state back at the end of the action (§5.2-7);
`resync` is a core action (answer = that state).

**Client (`client/ui/v2`)** — subclass `V2TableScreen<V>` (menu tables) or `V2SceneScreen<V>` (scene screens; implement
`sendRaw`, call `state(view, ack)`):

```java
public final class BlackjackScreen extends V2TableScreen<BlackjackView> {
	protected BlackjackView view(CompoundTag s) { return BlackjackView.of(s); }          // pure
	protected Flow<BlackjackView> flow() { return BlackjackFlow.INSTANCE; }              // pure
	protected void layoutPlay(TableGrid g, BlackjackView v, HitMap hits) {               // felt targets + banner regions
		hits.add(KeyMap.Ids.seat(i), box, true);
		chrome.banner.regions(List.of(gapBetweenDealerAndSeats), cardBoxes);
	}
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, BlackjackView v, int mx, int my, float pt) {
		// cards (lane A), SeatPlate.draw, ChipStack.draw, ValuePill.draw, Stamp.drawResolved / TotalPill.draw, FeltPrint
	}
	@Override protected void init() { super.init(); chrome.keys(KeyMap.standard().bind(KeyMap.H, "action.hit")); }
	@Override public boolean onTarget(String id, boolean enabled, int button) { /* seat.i → sendTracked("sit") */ }
}
```

- Gates → actions: an enabled primary / action sends `gate.action()` through `sendTracked` (override `onGate` for args).
  Single-spot bets go through `betPort()` (default: `bet {amount}` and the tool ids as actions); multi-spot games call
  `chrome.bet.multiSpot(true)` and, on a felt click, `long d = chrome.bet.armOrDefault(); sendTracked("bet", args, spot, d)`.
- Results: `chrome.banner.show(net)` (after the last stamp), `chrome.top.balance.delta(net)`, `chrome.status.set(l1, l2)`.
- Errors: `showError` → toast on the felt + status line. The watchdog reason / resync are automatic.
- Tests: `gametest/v2/RealFlow` (`size`, `place`, `open`, `click(id)`, `key`, `waitServer`, `waitChrome`, `status()`,
  `shot`, `close`); the dropped-packet hook is `V2Chrome.dropNextSends = 1`. Lane classes are pre-registered:
  `gametest/v2/<game>/<Game>V2ClientGameTests` and `<Game>V2GameTests` (board: `BoardClientGameTests`, `BoardGameTests`).
- Sprites: `client/ui/v2/V2Sprites` ids under `core/v2/`; every component has a flat fallback (`V2Draw.forceFallback`).
