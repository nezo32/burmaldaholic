# Burmaldaholic — Presentation architecture: animation, FX and the slots v2 engine (both editions)

Owner: animation-wave architect. Status: **skeleton in the tree, compiling, behaviour unchanged**
(2026-09-24). Audience: every developer of the animation and slots-redesign wave, both editions.

Inputs: `docs/research/animation.md` (capabilities), `docs/design/animation/{global,cards,tables,slots,extras-pvp}.md`
(storyboards, per-game task lists), `docs/design/SLOTS.md` (+ `slots-math/`), `docs/architecture/{java,bedrock,pvp-bots}.md`
(module contracts, ownership, PvP/Bots layering). The design files say **what** moves and when; this file says
**where the code lives, which shared APIs everybody uses, how both editions stay identical, and who owns which files**.

Contents
0. The shape in one page
1. Decisions (lead decisions + architect decisions, with the spec edits applied)
2. Per-edition runtimes (Java client runtime, assets, particles, sounds, BER framework, server FX and sync; Bedrock
   scheduler, live forms, entity props, HUD/titles/camera; FX settings; screen base classes)
3. The shared presentation timeline ("PTL"): format, clocks, sync protocol, vectors, seeds, per-game builders
4. Win tiers and the celebration API
5. Asset pipeline (one generator)
6. Glyph map (conflict-free, all specs)
7. Slots v2 engine architecture
8. Fit with the PvP + Seats & Bots code (not yet merged)
9. Work breakdown: lanes, file ownership, dependencies
10. Skeleton inventory and verification

---

## 0. The shape in one page

```
             server (authoritative)                               clients / viewers
 ┌───────────────────────────────────────────┐       ┌────────────────────────────────────────────────────┐
 │ game logic (pure)  → outcome (tape, pocket, │       │ Java screen      Java BER        Bedrock form/entity│
 │ dice, card beats) — drawn & persisted first │       │     │               │                 │           │
 │        │                                    │ seed  │     ▼               ▼                 ▼           │
 │        ▼                                    │+outcome│  SAME pure builder: Timeline = build(outcome, seed,│
 │ Timeline = build(outcome, seed, SHARED)  ───┼──────►│  profile) → sampled at (serverTime − start)        │
 │ reveal gate = startTick + sharedEndTicks    │       │  FrameModel.frame(o, tl, t) → draw                 │
 │ (text/chat/forms/celebration after the gate)│       │  final frame == terminal(outcome)  (tested)        │
 └───────────────────────────────────────────┘       └────────────────────────────────────────────────────┘
       Java: core.anim (pure, common set)  ≡  Bedrock: core/logic/anim (pure)   ← shared vectors, byte-identical JSON
```

- **One timeline format for both editions** (§3). Every game has a pure builder `outcome → Timeline` with an
  identical Java and TypeScript port; shared JSON vectors prove both build byte-identical timelines.
- **Server decides, clients animate to it** (global.md §1, SLOTS.md §1.4). Clients never draw or predict outcomes;
  cosmetic variation comes only from `SeedMix` of public values.
- **Shared clock vs local clock** (§3.2). Server-paced beats are the same tick for every viewer (screens, BERs,
  Bedrock entities, spectators); decoration after the reveal gate is per viewer (speed, skip).
- **One shared kit per edition**: Java `core.anim` (pure) + `client.anim` / `client.fx` / `client.render` +
  `core.fx` / `core.sound`; Bedrock `core/logic/anim` (pure) + `core/presentation`. Games never build their own
  tween library, celebration, settings, sound helper or BER time base.
- **One asset generator** for both editions (§5), **one glyph map** (§6), **one sound catalog** (§2.4).
- **Slots v2** is a new pure engine package in each edition, built next to v1 and switched in one cut-over (§7).

---

## 1. Decisions

### 1.1 Lead decisions (2026-09-24) — recorded and applied

| # | Decision | Where applied |
|---|---|---|
| L1 | The six presentation-only SLOTS.md changes of `animation/slots.md` §0.3 are **accepted**: D1 symbol sprites 40 × 40 drawn 1:1 in 44 px cells + a 32 × 32 compact sheet; D2 Showdown mini-cells 16 px; D3 Bedrock in-world tumbles keep the first landed window (MUST) with the `g0…g2` final-window overlay as NICE; D4 slot tier words passed to the shared celebration API; D5 Hoard filler is a neutral ember blur that never scrolls a coin; D6 Treasure Hunt chest rattles until the server confirms the reveal. No rule, number or RTP changes. | `SLOTS.md` §9.5, §10 (header note), §10.1, §10.4, §10.5, §10.6 — each marked **⚠ CHANGED**; `animation/slots.md` §0.3 marked ACCEPTED, §12.3 Q1/Q5 resolved |
| L2 | **Celebration overlay API change**: games pass their own tier words. **Win-tier thresholds are per game**: one shared `WinTier` API with per-game threshold tables — global default WIN / BIG / MEGA / EPIC at 10 / 25 / 50 × for table and extras games (and for broadcasts and the chaos big-win rule); slots NICE / BIG / MEGA / EPIC at 5 / 15 / 40 / 100 ×. | `global.md` §2.4, §2.6 (⚠ CHANGED notes), §2.7 (`win_nice`), §9 (`fx.tier.nice`, `nice_win` subtitle); code `core.anim.WinTier/WinTierTable/TierWords`, `core/logic/anim/win-tier.ts`; §4 here |
| L3 | **Hold'em slow all-in run-out approved** (presentation only: pacing of an already drawn and persisted board; no decision remains). | `animation/cards.md` §2.2 marked APPROVED; lane J-L5 / B-L5 |

### 1.2 Architect decisions (binding for this wave)

| # | Decision | Why |
|---|---|---|
| A1 | Pure presentation maths lives in the **common** source set: Java `dev.nezo.burmaldaholic.core.anim` (not `client.fx` as global.md §2.5 said; global.md patched). Bedrock twin `src/core/logic/anim/`. | JUnit only sees `main`; the server needs timeline lengths for reveal gates and settle timers; Bedrock `check-arch` requires pure code under `logic/`. |
| A2 | Every spec's per-game path/beat module (`CardMotion`, `*Beats`, `RouletteBallPath`, `DiceThrowPath`, `CoinAnim/WheelAnim/PlinkoAnim`, `SlotTimeline`) **outputs the shared `Timeline`** (§3) or is a pure path function sampled by a `FrameModel` (§3.6). Their spec paths are kept; shared paths go to `core/anim/<topic>` (Java) and `core/logic/anim/<topic>` (Bedrock) instead of `core/logic/fx`, `core/anim/cards` mixed spellings. | One vocabulary, one vector format. |
| A3 | **Fidelity vectors** live in `java/src/test/resources/fx/vectors/<topic>.json` (generated by the Java test with `FX_DUMP_VECTORS=1`) and are mirrored to `bedrock/test/fx/vectors/<topic>.json`; `npm run sync:vectors` checks/copies. Replaces `docs/design/animation/slots-timeline-vectors.json` and the `vectors_tables.json` / `vectors_cards.json` single files (now `tables.json`, `cards.json`, `extras.json`, `slots_timeline.json`, `slots_engine.json`, `core_anim.json`). | CI path filters build each edition only from its own folder. |
| A4 | **One asset generator in Node**: `bedrock/tools/gen-assets.mjs` + `bedrock/tools/assets/{lib,modules}/**`, writing both editions (replaces the planned Python `scripts/gen-fx-assets.py`, `scripts/fx_assets/*.py`, `scripts/fx/cards.py`, `tools/gen-table-anim.mjs`, `tools/gen-card-entity.mjs`; absorbs `gen-glyphs.mjs` / `gen-textures.mjs` over time). The worldgen generator (`src/worldgen/tools/gen-assets.mjs`, structures + NPC skins) stays module-owned. | Node is already the Bedrock toolchain; `--check` runs in CI; can bundle pure TS (e.g. the slot strips) with esbuild like the worldgen generator does. |
| A5 | **Glyph clash resolved**: `extras-pvp.md` dye swatches move from U+E1C0–E1CF (also claimed by `tables.md`) to **U+E1E0–E1EF**. The full map is §6 and `core/logic/anim/glyph-map.ts` (overlap test). The research's "cards U+E5xx, dice U+E6xx" idea is superseded. | Two specs claimed the same 16 cells. |
| A6 | **Sound ownership**: ids used by more than one module are owned (registered) by **core**: `dice_throw`, `dice_bounce`, `dice_cup`, `dice_wall` (craps + extras), `coin_land`, `coin_whoosh`, `wheel_stop`, `burn`, `collector_arrive`, `heartbeat`, `attract_chime` (names that no module namespace owns). Catalog: §2.4. | Java `namespaces.properties` (e.g. `dice_*` belongs to extras, which craps may not register). |
| A7 | Slots v2 is built **next to v1** (Java `games.slots.v2.logic`, Bedrock `games/slots/v2/logic`); a single cut-over task switches the module, v1 becomes settle-only `LegacySlots`, deleted one release later (SLOTS.md §8.1). | Parallel lanes can land engine, screen and entity work without breaking the playable v1. |
| A8 | Win tier enum includes **NICE** for every game (`LOSS, RETURN, PUSH, WIN, NICE, BIG, MEGA, EPIC, JACKPOT`; MAX WIN is a flag). The default table has no NICE threshold; table-game floors (tables.md §0.3) reach it; it presents as the in-panel banner pop with the new core key `gui.burmaldaholic.fx.tier.nice` and sound `win_nice`. | tables.md floors used NICE, global.md had no NICE. |
| A9 | Bedrock live UI goes through **one `LiveForm` abstraction** (DDUI `CustomForm` + Observables, classic fallback always kept), after the **DDUI spike B-S0** (lane B-L1, day 1). Slots, scratch and duel live forms all use it. | Research open question 1; three specs planned separate DDUI code. |

---

## 2. Per-edition runtimes

### 2.1 Java client animation runtime

| Package (source set) | Class | Role | Status |
|---|---|---|---|
| `core.anim` (main, pure) | `Ease` | all easing curves of global §2.5 + tables §0.2 + extras §0.2 (`outBack(s,t)`, `expDecay(k,t)`) | **done**, vectors |
| | `Beat`, `Clock`, `Timeline`, `TimelineBuilder`, `TimingProfile` | the PTL format (§3) | **done**, vectors |
| | `TimelineSeed` | what the server publishes (§3.3), elapsed ms, catch-up rule | done |
| | `SeedMix` (+ `FxRng`) | cosmetic seeds, mulberry32 | **done**, vectors |
| | `WinTier`, `WinTierTable`, `TierWords`, `RollUp` | §4 | **done**, vectors |
| | `TextFit` | RU width rules (§2.12) | done |
| | `FrameModel<O,F>` | pure frame sampler contract (§3.6) | interface |
| `client.anim` (client) | `AnimClock` | shared ms = (level game time + partialTick) × 50; local ms = `Util.getMillis()` | done |
| | `Tween` | reusable float tween (no per-frame allocation) | done |
| | `TimelinePlayer` | plays a `Timeline`: shared part from server time, local part from wall time; catch-up (85 %), skip (local groups only), overrun (`finishNow`) | done, unused |
| `client.fx` (client) | `FxSettings` | §2.11 | skeleton (not loaded yet) |
| | `CasinoPalette` | global §2.1 tokens (ARGB) | done |
| | `CelebrationOverlay`, `CelebrationRequest` | §4; state machine + roll-up maths done, drawing = lane J-L1 | skeleton |
| | `GuiParticlePool` | ≤ 96 pooled GUI particles (SoA) | simulation done, drawing = J-L1 |
| | `FxText`, `CasinoToast`, `FxSounds` | outlined text (4-offset ink outline), toasts, playback × `anim.volume` | lane J-L1 |
| `client.render` (client) | `SpectatorBlockEntityRenderer<T,S>`, `SpectatorRenderState` | §2.5 | base done, no subclass yet |

Rules: every animated value is a function of **time**, never of frame count; the shared clock is always
`AnimClock.sharedMs(seed, partialTick)` / `levelMs(partialTick)`; allocation-free in steady state (reuse tweens,
pools, cached `Component`s); reduce motion and flashes are applied by the **builders** (they emit the reduced beat
set) and by `FxSettings.flashes()` at draw time.

### 2.2 Java sprites, atlas, fonts (generated)

- **GUI sprites** under `assets/burmaldaholic/textures/gui/sprites/burmaldaholic/<module>/…` go into the vanilla GUI
  atlas; nine-slice and looping animation via `.mcmeta` (`gui.scaling.nine_slice`, `animation.frametime`), all
  written by the generator (§5). One atlas page; total FX sprites < 64 KB (global §2.9).
- **Code-indexed sheets** (not atlas): `textures/gui/<module>/*.png` blitted by UV (slot symbol sheets 40 px /
  32 px / 16 px per D1–D2, card faces, wheel faces). Horizontal frame strips; the generator writes a JSON index
  next to each sheet only when a sheet is irregular.
- **Fonts**: `assets/burmaldaholic/font/default.json` (lane J-L1, task J3) with bitmap providers: E1 sheet
  `textures/font/glyph_e1.png` (256², 16 px cells, height 8, ascent 7 — the same PNG as Bedrock's `glyph_E1.png`) and
  the slot planes E2/E3/E4 (512², 32 px cells) for Java fallback text; `burmaldaholic:banner` bitmap font
  (Latin + Cyrillic, 2× pixel style) for tier words and banners (SLOTS.md §10.5). Glyphs are never inside lang
  strings; code prepends them as separate components (UI.md rule).
- **Block/item textures**: animated block strips (`textures/block/<module>/*_front.png` + `.mcmeta`), chip stack
  item models (`range_dispatch` on count — **verify**), all generated.
- Ownership: file names/folders follow `checkAssetOwnership` (`slots/…`, `roulette/…`); core assets under
  `burmaldaholic/` folders or unprefixed names are core's.

### 2.3 Java particles

- Registered with `FabricParticleTypes.simple()` in the **owning module** (`ctx.registry()`), providers with
  `ParticleProviderRegistry` in the client module; JSON `assets/burmaldaholic/particles/<id>.json` + sprites
  `textures/particle/burmaldaholic/<id>_<n>.png` (generated).
- Core owns the shared set: `chip_pop, chip_glint, sparkle, gold_burst, golden_mote, diamond_glint, curse_wisp,
  summon_rune, teleport_ring, collector_smoke` (global §5.1) + `coin_burst, confetti, jackpot_burst` (used by slots and
  extras). Module-owned: `slots` → `ember_burst`, `void_motes`; `extras` → `foil_flake`; `craps` → `dice_dust`.
- Budgets (global §2.9): ≤ 60 per burst, ≤ 150 for JACKPOT, ≤ 400 casino particles alive (checked in the shared
  provider base `client.fx.CasinoParticle`, lane J-L1); server sends ≤ 3 `sendParticles` per event.
- The same ids exist on Bedrock (`burmaldaholic:<id>` particle JSON); Bedrock emitters multiply one
  `spawnParticle` call client-side (`variable.count`).

### 2.4 Sound event registry (both editions)

`core.sound.SoundCatalog` (Java) and `core/logic/anim/sound-ids.ts` (Bedrock) list **every** sound id of the wave
(global §2.7, cards §7, tables §0.8, extras §10.3, slots §8) with owner module, source (`player` / `block` /
`ambient`), subtitle and whether it already exists; the list (id@owner, in order) is part of `core_anim.json`, so the
catalogs cannot drift. Registration: `CasinoSounds.registerOwned(ctx, "<module>")` in the owning module's
`register` + the module's `src/main/sounds/<module>/sounds.json` entries (vanilla composites in the MUST phase; NICE
`.ogg` files later under the same ids). Bedrock: `packs/<owner>/RP/sounds/sound_definitions.json` (deep-merged by the
build), played through `core/presentation` helpers with `volume × anim.volume`.
Rules: never `MASTER`; personal = `PLAYERS`, tables/cabinets = `BLOCKS` played positionally by the BER (Java) or
`dimension.playSound` (Bedrock); "+"-composites are two events in the same tick; ≤ 20 sounds/s per player.

### 2.5 Java BlockEntityRenderer framework (spectator views)

`client.render.SpectatorBlockEntityRenderer<T,S extends SpectatorRenderState>` implements the 26.x split
`extractRenderState` / `submit` API (verified against the 26.2 and 26.3 jars). The base fills `levelMs` (shared clock),
`distSq` and a LOD (`FULL` tweened ≤ `animateRadius()`, `SETTLED` ≤ `getViewDistance()`, `STATIC` beyond); a subclass
copies its block entity's last `TimelineSeed` + outcome in `extractAnimated` and draws only from the state in
`submit`. Timelines are rebuilt **only when `seq` changes** (cached on the BE client object), never per frame.

| Renderer | Lane | Notes |
|---|---|---|
| `SlotCabinetRenderer` | J-L10 | reels on the cabinet face from the same `SlotTimeline`; tumbles, sticky, Hoard, wheel, tier text (`submitText`) |
| `RouletteTableRenderer`, `CrapsTableRenderer` | J-L6 | ball path / dice path; view distance 48 / 32 |
| `CardTableRenderer` (+ 4 subclasses) | J-L5 | public card state only (`pub` tag) |
| `WheelOfFortuneRenderer`, `PlinkoMachineRenderer` | J-L7 | `SpinSync` / `DropSync` |

Sync data travels in the BE update tag (`getUpdatePacket` / `getUpdateTag`), ≤ 1 update per beat (cards) or per
spin (+1 per free spin / bonus step, coalesced ≥ 10 t) — never per tick.

### 2.6 Java server FX and the sync protocol

- `core.fx.ServerFx` (skeleton; default = vanilla fallback = today's behaviour): `celebrate(player, Celebration)`,
  `event(player, Kind, game, arg)`. Lane J-L1 installs the implementation that sends the clientbound
  `burmaldaholic:fx` payload (global §3.1) when `ServerPlayNetworking.canSend(player, FxPayload.TYPE)`, else vanilla
  titles/particles.
- **Screens** receive the outcome with their existing state payloads (tables: `TableSyncPayload`; slots:
  the v2 spin state); **spectators** through the BE update tag. Both carry a `TimelineSeed`
  (`game, seq, startTick, seed, speedPct`) next to the game outcome (§3.3).
- The server computes the **reveal gate** from the same builder: `gateTick = startTick + timeline.sharedEndTicks()`.
  Result chat lines, titles, result forms, HUD balance floaters (`holdBalanceDelta`) and the celebration wait for
  the gate; money settles immediately where GAME_DESIGN §4.1 requires restart safety (tables.md §0.6.3).
- Early outcome publication is fine only after bets close (tables §0.6.7); PvP steps are revealed step by step
  (PVP.md §3.6, extras §0.3.7).

### 2.7 Bedrock presentation runtime — scheduler with budgets

`core/presentation/scheduler.ts` `playTimeline(timeline, sink, opts)`: **one `system.runInterval` per session**
(a solo player's machine, a table round), period 2 t by default; each frame computes `t = (tick − t0) × 50 + skipOffset`,
fires `sink.beat(b)` for beats that started (beats passed by a skip or a late start are not replayed), calls
`sink.frame(t)` for continuous beats, and ends with `sink.end(t, interrupted)` (fidelity F8: interrupt = reveal).
`alive()` stops it when the form closes / player leaves. Frames over `budgetMs` (0.3 ms) are logged.
One-shot beats that need exact ticks without a session use `system.runTimeout` chains (tables `soundTimeline`).
Budgets (global §2.9, slots §2.6, cards §0.8): ≤ 1 Observable write per 2 t per label, ≥ 2 t between action-bar
frames, ≤ 24 `spawnParticle` calls per event, ≤ N `setProperty` per event as each spec lists.

### 2.8 Bedrock live forms (DDUI with classic fallback)

`core/presentation/live-form.ts` `createLiveForm(player, title, {preferDdui, actionbar})` returns a `LiveForm`
(`label`, `button`, `set(id, text)`, `show`, `close`, `isShowing`). **DDUI**: `CustomForm` with `ObservableString` /
`ObservableUIRawMessage` per label/button, `set` writes only changes. **Classic**: an `ActionFormData` between steps,
live text routed to the game's HUD action-bar channel while the form is closed. Construction failure → classic.
**Spike B-S0 first** (lane B-L1, 1 day): update-rate limits, glyph size in a label, `§` tinting of glyphs, title
glyph scale, per-render-controller `uv_anim`, `q.distance_from_camera`, entity property count; the findings go into
the [V] items of `animation/slots.md` and `extras-pvp.md` §0.7 and may flip defaults (`slots.bedrock.ddui`).

### 2.9 Bedrock entity-based in-world props

Props = AI-free entities with `client_sync` properties: `slot_reels` (slots), `wheel_fx`, `plinko_fx`, `coin_fx`
(extras), roulette wheel, `table_die` + puck (craps / Dice Duel), NICE `card_hand`. Pattern (research §3.2):
- the script writes **properties at beat ticks** (`writeProps` writes only changes and returns the write count for
  budget accounting) and calls `playPropAnimation(anim, controller, nextState)` for one-shot motion;
- per-frame motion is **Molang** in generated animation files (curves from `core/logic/anim` baked as constants or
  expressions by the generator, so Java and Bedrock land on the same angle / reel offset — extras §14.7 tests a tiny
  Molang evaluator against the TS path);
- geometry (`*.geo.json`), animations, controllers and render controllers are **generated JSON** (no Blockbench):
  asset modules emit them from code (bone lists, per-reel quads, UV strips from the real reel strips).
- Lifecycle: spawn on block place / chunk load (re-link by tag), remove with the block; one entity per machine,
  never per spectator; LOD via `q.distance_from_camera` [V].

### 2.10 Bedrock HUD, titles, camera, particles, sounds

- Titles and roll-ups: `rollup-hud.ts` `titleRollUp` (`setTitle` once, `updateSubtitle` every 2 t, exact last value;
  reduce motion = 2 steps) — requires `hud.holdTitle` from the PvP branch (§8) so the status line does not clobber it.
- Action-bar tickers: game HUD channels via `ctx.hud.actionbar(p, '<game>.<channel>', raw, priority)` (existing
  queue), ≥ 2 t apart per player.
- Camera: `camera.ts` `fade` (stable API; skipped with reduce motion or flashes off) and `shake` (command; skipped with
  reduce motion). Every camera beat has a title-only baseline.
- Particles and sounds: definitions in `packs/<owner>/RP/particles|sounds`; one particle atlas per owner
  (`burmaldaholic_fx.png` core, `burmaldaholic_slots.png` slots), generated.
- `FxService` (global §3.2) = `core/presentation/fx.ts` (lane B-L1): `celebrate(player, CelebrationRequest)`,
  `chaos`, `toast`, `hudPulse`, rate limits, settings; games call it instead of `hud.title` + `playSound`.

### 2.11 FX settings (both editions)

Ids of global §2.8: `anim.reduceMotion`, `anim.flashes`, `anim.speed` (50 / 100 / 150), `anim.celebrations`
(all / mine / off), `anim.volume` (0–100), Bedrock-only `anim.hudPanel`.
Java: `client.fx.FxSettings` ↔ `config/burmaldaholic-client.json` (client-local; Casino Menu → Settings rows and the Mod
Menu "Client effects" section; vanilla "Hide lightning flashes" or "Screen effect scale = 0" force flashes off).
Bedrock: player dynamic properties `burmaldaholic:anim.*` read by `core/presentation/settings.ts`, written by the Casino
Menu → Settings ModalForm. Both expose `localProfile()` → `TimingProfile` for LOCAL beats; shared beats always use
`TimingProfile.SHARED` (only the acting player's machine turbo, SLOTS.md §6.4, changes shared speed and is published
in `TimelineSeed.speedPct`). Reduce motion forces flashes off.

### 2.12 Screen base classes and RU layout helpers (Java)

- `client.ui.CasinoScreen` (lane J-L2): common base for every casino screen: nine-slice panel, entrance
  (fade + scale 0.97 → 1, 150 ms), `CelebrationOverlay` drawn on top (stratum above widgets), error line slide,
  compact mode switch (< 400 × 240), key handling (Space/Enter skip, Esc never cancels a confirmed action), narrator
  throttle. `CasinoTableScreen` extends it (hooks only; games keep their subclasses).
- `client.ui.CasinoButton` (primary / secondary / danger sprite families, hover lift, press, invalid shake).
- Layout maths is pure and tested in `core.anim.TextFit`: `buttonWidth = max(min, textWidth + 8)`,
  `bannerScale` 3 → 2 → 1 (never fractional), `flowRows` for button rows, `enBudget(box) = box / 1.45`. Every fixed box
  is checked with the RU strings at GUI scale 2 and 4 in client gametests (lane J-L2 adds a helper that opens each
  screen with `ru_ru` and asserts no text exceeds its box).

---

## 3. The shared presentation timeline ("PTL")

### 3.1 Data model

```json
{"v":1,"game":"slots.nether","seed":-420153623,"beats":[
  {"at":0,"dur":120,"kind":"slots.spin_up","lane":-1,"group":0,"clock":"S","args":[]},
  {"at":250,"dur":350,"kind":"slots.reel_land","lane":0,"group":0,"clock":"S","args":[17,3]}, …]}
```

| Field | Meaning |
|---|---|
| `at`, `dur` | integer ms from beat 0 (never ticks; Bedrock rounds up with `ceilTicks` when scheduling) |
| `kind` | `<game>.<beat>` (constants per game: `SlotTimeline.*`, `SLOT_BEAT`, `BlackjackBeats.*` …) |
| `lane` | reel / seat / card slot / ring, −1 none |
| `group` | skip group (skip = jump to the group end) |
| `clock` | `S` shared / `L` local (§3.2) |
| `args` | small ints (symbol ids, amounts, masks) — never information that is not yet public at `at` |

Beats are sorted by `at`, then insertion order. Canonical JSON (fixed key order, no spaces) is the vector format.
Durations derived from transcendental maths (roll-ups) are `floor(x + 1e-9)` in both editions. Local durations are
scaled by `TimingProfile.scale(ms) = floor(ms × 100 / speedPct)`.

### 3.2 Clocks

**Shared** (tables §0.1, cards §0.2): spins, throws, deals, flips, squeezes, run-outs, reel stops, feature steps that
every viewer must see together; ignore viewer speed/skip; reduce motion changes only how they look. **Local**:
payout flights, banners, celebrations, roll-ups after the gate; respect `anim.speed`, skippable. The gate between
them is `timeline.sharedEndMs()`.

### 3.3 Sync protocol

1. Server resolves and **persists** the outcome (tape / round), then publishes `TimelineSeed{game, seq, startTick,
   seed, speedPct}` + outcome (state payload for the actor, BE update tag for spectators; Bedrock: the script itself).
2. Every consumer builds `Timeline = Builder.build(outcome, seed, sharedProfile, localProfile)` with the **same pure
   builder**, samples `t = (gameTime − startTick + partialTick) × 50` (Java) or `(currentTick − t0) × 50` (Bedrock).
3. Late joiners: `t ≥ 0.85 × sharedEnd` → draw the terminal state (no tweens). Overrun: a new `seq` finishes the old
   player first (≤ 120–250 ms per spec).
4. Server posts text/forms/celebration at the gate; settle timers (slots "settled after the timeline ends",
   SLOTS.md §10.2) use `sharedEndTicks()`; skip / close / disconnect settle at once from the persisted outcome.
5. Optional client **outcome-free spin-up** (research §2.11): a screen may start its spin-up on click; it lands only
   on the received outcome; a rejected action decelerates onto the previous state (slots F1).

### 3.4 Shared test tapes (fidelity)

- Files: `java/src/test/resources/fx/vectors/<topic>.json` (generated by the Java test on `FX_DUMP_VECTORS=1`), mirrored
  to `bedrock/test/fx/vectors/` (`npm run sync:vectors` checks, `--write` copies). Each file holds **inputs and
  outputs**; each edition recomputes outputs from the inputs (so Bedrock tests never depend on Java at runtime).
- Topics: `core_anim` (**exists**: easing samples, seeds, RNG, tiers, roll-ups, speed scaling, a sample timeline,
  the sound catalog), `cards` (beat schedules per game + `CardMotion`), `tables` (ball path 37 pockets × 64 seeds,
  dice path 36 pairs × 64 seeds × 8 paths), `extras` (coin / wheel / plinko / scratch), `slots_engine` (windows, way
  wins, tumble chains, draws from seeded RNG, tape strings), `slots_timeline` (20 tapes per machine → beat lists).
- Every game adds: (a) **final frame == server result** for every vector (`FrameModel.terminal`), (b) the same after
  skip, reduce motion and catch-up, (c) honesty properties from its spec (cards §0.7.3 permutation invariance, slots
  §10.3 anticipation iff visible condition, extras §14 no boundary crossing), (d) no unrevealed data in any sync
  payload (gametests).

### 3.5 Cosmetic seeds

`SeedMix.mix(parts…)` / `seedMix(...)` (FNV fold through murmur3 `fmix`), `SeedMix.hash(string)`, `FxRng` (mulberry32)
— bit-identical in both editions (vectors). Seeds use **public** values only: `mix(hash(tableKey), roundSeq, slot)`,
`mix(posHash, spinSeq)`. Never the game RNG, the bot RNG, `Math.random`, `ThreadLocalRandom`.

### 3.6 Per-game builders and frame models (who writes what)

| Game | Java (main, pure) | Bedrock (pure) | Lane |
|---|---|---|---|
| core celebration / roll-up | `core.anim.RollUp`, `WinTierTable.upgradePoints` | `core/logic/anim/{rollup,win-tier}.ts` | done |
| cards kit | `core.anim.cards.CardMotion` | `core/logic/anim/cards/card-motion.ts`, `card-ticker.ts` | J-L4 / B-L4 |
| blackjack, baccarat | `games.<g>.logic.<G>Beats` | `games/<g>/logic/beats.ts` | J-L4 / B-L4 |
| poker, UTH | `games.<g>.logic.<G>Beats` (incl. slow run-out, L3) | `games/<g>/logic/beats.ts` | J-L5 / B-L5 |
| roulette | `games.roulette.logic.RouletteBallPath` | `games/roulette/logic/ball-path.ts` | J-L6 / B-L6 |
| craps, Dice Duel | `core.anim.dice.DiceThrowPath` | `core/logic/anim/dice/dice-path.ts` | J-L6 / B-L6 |
| extras | `games.extras.logic.anim.{CoinAnim,WheelAnim,PlinkoAnim,ScratchWipe}` | `games/extras/logic/anim.ts` | J-L7 / B-L7 |
| slots | `games.slots.v2.logic.SlotTimeline` (+ `SlotFrames`) | `games/slots/v2/logic/timeline.ts` (+ `frames.ts`) | J-L8 / B-L8 (builder), J-L9 / B-L9 (frames) |
| PvP shared (countdown, Final Reveal) | server-paced by the PvP engine cues; presenter maps cues to beats | same | J-L7 / B-L7 |

---

## 4. Win tiers and the celebration API

- Server: `WinTier.of(ret, stake, table, jackpot, floor)` → sent with the result. Tables: `WinTierTable.DEFAULT`
  (10 / 25 / 50 ×, net floors 100 / 250 / 500, EPIC also at net ≥ `core.bigWinThreshold`), `WinTierTable.SLOTS`
  (5 / 15 / 40 / 100 ×; 1× is a WIN, not a PUSH); games may define their own via `WinTierTable.of`. Config:
  `core.winTiers` / `slots.bigWinTiers` feed `withMultiples`.
- Broadcasts (global §4.7) and the chaos big-win rule (GAME_DESIGN §13.1.3) always use the **default** table on the
  spin's net, whatever the game's on-screen table.
- Client: `CelebrationOverlay.play(CelebrationRequest{tier, ret, stake, table, words, stems, subTier, maxWin, seed})`
  (Java) / `fx.celebrate(player, request)` (Bedrock). `words` = `TierWords.CORE` or the game's own
  (`SlotTiers.WORDS` / `SLOT_TIER_WORDS`); `stems` = `TierStems.CORE` or `slots.*`. Upgrade beats come from
  `table.upgradePoints(stake, start, final)`; the word always ends on the server tier; roll-up
  `RollUp.durationMs/valueAt` (monotonic, exact last frame). Skippable; `anim.celebrations` = off → WIN banner only.
- Jackpots: JACKPOT tier + sub-tier (1 Mini … 4 Grand) with slots.md §4.11 lengths; several jackpots play in tape order.

---

## 5. Asset pipeline (one generator)

`bedrock/tools/gen-assets.mjs` (skeleton, runs) loads every `tools/assets/modules/<module>.mjs`; each default-exports
`generate(ctx)` returning `{edition: 'java'|'bedrock', path, bytes}` outputs; the driver writes them (`npm run
gen:assets`) or verifies them (`npm run check:assets`, add to `lint` when the first module produces files).
Library (`tools/assets/lib/`): `png.mjs` (deterministic encoder), `palette.mjs` (global §2.1 tokens), `grid.mjs`
(string-grid pixel art, frame strips, blits), `emit.mjs` (`png`, `json`, Java `mcmeta` for animation / nine-slice).
To add (lane X-L0): `font.mjs` (glyph sheet writer + Java font JSON), `molang.mjs` (curve → Molang expression / baked
keyframes), `geo.mjs` (bone/quad builders for entity geometry), `atlas.mjs` (particle atlas + Bedrock particle JSON),
`transforms.mjs` (blur, shine band, palette swap, squash frames of slots.md §3.2).

| Module file | Owner lane | Produces (both editions unless noted) |
|---|---|---|
| `core.mjs` | X-L0 | panels, buttons, HUD sprites, fx sprites (rays, coins, confetti, sparkle, chips), vignettes, toasts, glyph sheet E1 (all ranges of §6), core particle sprites + Bedrock atlas, form icons, chip stack items |
| `meta.mjs` | B-L3 | VIP badges, Last Chance coin, chaos cards (NICE), loan seal, attract block strips + flipbooks |
| `cards.mjs` | B-L4 | card faces/backs, glow, stamps, tags, shoe, pot/chip stacks, `card_hand` entity (NICE) |
| `tables.mjs` | B-L6 | roulette wheel, dice, puck, layouts, dolly, ball path Molang, die/puck/wheel entities |
| `extras.mjs` | B-L7 | coin, wheel faces, Plinko, scratch tickets, `coin_fx`/`wheel_fx`/`plinko_fx` entities + Molang |
| `slots.mjs` | B-L10 | symbol sheets 40/32/16 px (D1, D2), glyph planes E2/E3/E4, cabinets, marquees, backdrops, feature sprites, strip textures **read from the v2 strips** (bundled from `games/slots/v2/logic` with esbuild), `slot_reels` geo/animations/controllers, particles |

No text in textures (digits only where specs allow). Outputs must respect ownership (Java `checkAssetOwnership`,
Bedrock `packs/<module>/`). Generated files are committed; `--check` keeps them honest.

---

## 6. Glyph map (conflict-free across all specs)

Authority: `bedrock/src/core/logic/anim/glyph-map.ts` (overlap test in `vectors.test.ts`). Sheets: **E1** = core
`glyph_E1.png` (256², 16 px cells; Java `glyph_e1.png`, same file); **E2/E3/E4** = slots base / win / blur planes
(512², 32 px cells, same offsets).

| Range | Owner | Spec | Content |
|---|---|---|---|
| E100 | core | UI.md | chip |
| E110–E14F | core | UI.md | cards (E144 back) |
| E150–E153, E160–E165 | core | UI.md | suits, dice faces |
| E170–E171 / E172–E177 | core | UI.md / global | flame, cloud / streak pips, sun, loan bell, collectors |
| E180–E185 | vip | UI.md | VIP badges |
| E186–E18B | core | global | coin spin frames, heads, tails |
| E190 / E191–E19B | bots / core | BOTS / global | bot / thinking dots, mini chips, emerald, gold ingot, sparkle |
| E19C–E19F | core | — | global reserve |
| E1A0–E1A1 | pvp | PVP.md | rabbit's foot, charred |
| E1A2–E1AF, E1B0–E1B8 | extras | extras-pvp | scratch symbols, foil, chain pips, Plinko; wheel icons |
| E1B9–E1BF | extras | — | reserve |
| E1C0–E1CB (E1CC–E1CF reserve) | core | tables | tumbling die, pockets, ball, dolly, push, tick/cross |
| E1D0–E1DB (E1DC–E1DF reserve) | core | cards | card flip / peel / slot / flight, button, pot, stacks, muck |
| **E1E0–E1EF** | extras | extras-pvp (**moved**, A5) | Wheel Party dye swatches |
| E1F0–E1FF | core | — | presentation reserve (skip hint, spinners) |
| E200–E20A, E210–E21A, E220–E22A | slots | SLOTS.md | Overworld, Nether, End symbols |
| E230–E23A, E23B–E244 | slots | SLOTS.md, slots.md | shared slot glyphs; respin pips, anticipation arrow, ember blur, way node, hazards, wedge, ready |
| E245–E2FF | slots | — | reserve |
| E300–E3FF, E400–E4FF | slots | SLOTS.md | win-glow plane, blur plane |
| E500–EFFF | — | — | free (the research's E5xx/E6xx proposal is superseded) |

New ranges need a one-line PR to `glyph-map.ts` (the test fails on overlap) and UI.md §0.1.

---

## 7. Slots v2 engine architecture

### 7.1 Packages (mirrored, pure)

| Concern | Java `games.slots.v2.logic` | Bedrock `games/slots/v2/logic` | Status |
|---|---|---|---|
| machines, symbols, config def | `Machine`, `SymbolRole`, `MachineDef` (fifths), `Window` | `types.ts` (`MachineId`, `MachineDef`, `windowFromStops`) | types done |
| 243-ways evaluator | `Ways.evaluate(def, window, stickyMask)` → `WayWin`, `Result` | `engine.ts evaluateWays` | stub |
| tumbles (Nether) | `Tumble.run(def, stops, ladder)` → `Step`, `Chain` | `engine.ts runTumbles` | stub |
| tape (all random values of a spin) | `SpinTape` (+ `FreeSpins`, `FreeSpin`, `Hunt`, `Hoard`, `Wheel`, `JackpotAward`) | `types.ts SpinTape` | types done |
| draw (CONFIRM → TAPE) incl. free spins, retriggers, sticky masks, Hunt i.i.d. entries, Hoard respins, Wheel rings, cap | `SlotDraw.draw(Request, SlotRng)` | `engine.ts drawSpin` | stub |
| persistence codec (`{v:2,…}`, < 1 200 chars) | `TapeCodec` | `tape-codec.ts` | stub |
| progressive jackpots | `Jackpots.award / incrementAfter` (+ pool store in the module) | `jackpots.ts` | maths done |
| max-win cap, owned reservation | inside the draw; `SlotDraw.reservation = cap × bet` | `engine.ts reservation` | done |
| honest anticipation | `Anticipation.stopTimes` | `anticipation.ts` | stub (+ base schedule done) |
| tiers / words | `SlotTiers` (`WinTierTable.SLOTS`, `WORDS`) | `tiers.ts` | done |
| timeline | `SlotTimeline.build` + beat constants | `timeline.ts` | stub + constants |
| Slot Showdown v2 | `Showdown` (`Spin`, `Hazard`, `points`) | `showdown.ts` | stub + points |
| RTP validation (`slots.validateRtp`) | `SlotRtpV2` (enumeration < 30 s) | `rtp.ts` (factorised form + Nether defaults) | lane S-x4 |

Units: all pays in **fifths of the bet** (integers; SLOTS.md §7.5), chips = fifths × bet / 5 (exact because bets are
multiples of 5). `SlotRng` adapts `OddsService.play` (Java) / `ctx.odds.draw` (Bedrock) so the §14 streak re-draw
applies to the whole spin, never to bought features (SLOTS.md §8.2).

### 7.2 Module layer (non-pure, per edition)

- **Round lifecycle** (SLOTS.md §1.2, §8.1): CONFIRM (bet/price debit through wagers, owned reservation
  `reserved += cap × bet`) → `SlotDraw.draw` → streak re-draw → jackpot award at draw time (pools debited; others'
  meters show `pool + pending` until the reveal) → **PERSIST** `{v:2, machine, bet, price?, tape, total,
  jackpotAwards[], startTick, opened?}` (Java: `SlotMachineBlockEntity` + `SavedData burmaldaholic_slots` for pools;
  Bedrock: sharded world JSON per machine key + `burmaldaholic:slots:jp`) → PRESENT (timeline) → SETTLE at the gate
  or at once on skip / close / disconnect / restart. Treasure Hunt progress (`opened`) is persisted per pick; the
  i-th pick reveals entry i (D6: the client learns entry i only on pick i).
- **Protocol (Java)**: `spin` state = `TimelineSeed` + the tape section the client may see now (base stops, then per
  free spin / per bonus step, F7), skip action; spectators: `SpinSync` in the BE update tag.
- **Integration**: streak, Golden Hour (net excluding pool awards), chaos triggers (§8.4 priority list), VIP gate,
  contracts (`spin_slots`, new `slots_feature`), statistics, advancements, autoplay stop rules, buy feature limits.
- **Migration**: v1 pools → v2 Grand increments once (§5.3); v1 persisted rounds settle via `LegacySlots`.
- **Cut-over task** (S-x5): switch `SlotsModule` / `slots/index.ts` to v2, keep v1 settle-only, update config keys
  (CONFIG.md `slots` section from SLOTS.md §12), strings (SLOTS.md §13), advancements (§14).

### 7.3 Shared engine vectors

`slots_engine.json`: per machine, fixed stops → window, way wins (symbol, k, ways, pay), tumble chains, scatter/bonus
counts; seeded draws (a test `SlotRng` = `FxRng`-backed sequence) → tape strings; codec round trips. Exact totals of
SLOTS.md §7.5 are checked by a **slow tagged** Java test (full enumeration, CI nightly) and by the Bedrock factorised
form for non-tumbling machines. Monte-Carlo tolerance tests (§7.5) nightly in both editions.

---

## 8. Fit with the PvP + Seats & Bots code (not yet merged)

Read via `git show worktree-agent-aaf0f54e81d19ae4a:<path>` (Bedrock final) and the Java integration branch
(pvp-bots.md §2.1 file map).
- **PvP presenters** (`core/pvp/presenter.ts` `PvpPresenter`, Java `Pvp.setPresenter`) stay the entry point; the
  animation lanes implement presenter callbacks (`countdown`, `revealStep`, `finalReveal`, `sound`, `particles`) by
  building **local** timelines from the step data (the engine already paces shared time with cue ticks). No change
  to the engine contract.
- **`hud.holdTitle`** (branch) is required by `titleRollUp`, card run-out titles and PvP reveals — lanes use it after
  the merge; before it, they call `ctx.hud.title` (identical behaviour minus the hold).
- **Slot Showdown v2** replaces `games/slots/pvp/logic/showdown.ts` (branch, 3×3) with a mode whose `draw` uses
  `SlotDraw` per spin and stores `Showdown.Spin` records (SLOTS.md §9.4; < 3 000 chars fits one dynamic property —
  the branch's `test/independent/slot-record-size.test.ts` must be updated).
- **Bots**: thinking/act visuals (global §4.12, cards §5) hook `BotTableHooks` / `TableBots` presentation callbacks
  and `core/logic/bots/display.ts`; identical visuals for every decision (global §6.8).
- Files touched only **after the merge** are marked ⏸ in §9.

---

## 9. Work breakdown — lanes, file ownership, dependencies

Conventions: task ids are those of the design files (global `S*/J*/B*`, cards `C*/J-C*/B-C*`, tables `J-A*/J-R*/J-C*/J-D*/
B-*`, extras `SX*/JX*/BX*`, slots `SX*/JS*/BS*`) plus the slots engine tasks `S-J*` / `S-B*` below. **▶ now** = can start on
the current skeleton; **◆ after <x>** = waits for a milestone; **⏸** = after the PvP-bots merge. A lane owns its
files exclusively; shared core files have one owner lane each (below). Other lanes that need a core change open a
small PR to the owner lane. Everyone may add rows to STRINGS.md only through lane X-L0 (single editor).

### 9.0 Shared lane (one developer, Node, both editions)

**X-L0 Assets, strings, vectors** — owns `bedrock/tools/gen-assets.mjs`, `bedrock/tools/assets/lib/**`,
`bedrock/tools/assets/modules/core.mjs`, `bedrock/tools/sync-fx-vectors.mjs`, `docs/design/STRINGS.md` animation sections,
generated lang (`java/src/main/lang/*`, `bedrock/lang/*` via the generators), `packs/core/RP/font/**`,
Java `assets/burmaldaholic/textures/font/**`, core sprites/particle sprites (output paths of `core.mjs`).
Tasks: S1 (framework + core art + glyph sheet E1 incl. every range of §6, folding in `gen-glyphs.mjs`), S2 / C3 / SX3 /
SX4(slots) / tables §6 strings (all five specs' string tables into STRINGS.md in one pass), lib additions of §5,
vector sync in CI (`sync:vectors` + `check:assets` into `npm run lint`). ▶ now. Milestone **X-M1** (day 3): framework +
lib + glyph sheet; **X-M2** (week 1): all core art.

### 9.1 Java lanes (10)

| Lane | Owns (paths under `java/src/…/dev/nezo/burmaldaholic/` unless noted) | Tasks | Start |
|---|---|---|---|
| **J-L1 Presentation core** | `core/anim/**` (extensions), `client/anim/**`, `client/fx/**`, `core/fx/**` (+ `FxPayload` in `core/network/FxPayload.java`), `core/sound/**`, `core/CoreSounds.java`, `src/main/sounds/core/sounds.json`, `assets/burmaldaholic/font/*.json`, core particle types/providers, `CoreClientModule` wiring lines | J1 (rest), J2, J3, J5, J7 (+ L2 API), J8, J16, JX1, core sound ids of cards §7 / tables §0.8 (C4), `CasinoParticle` budget base | ▶ now. **J-M1** (week 1): overlay draws + `ServerFx` payload + sounds registered |
| **J-L2 Style kit, HUD, meta screens** | `client/ui/**` (new: `CasinoScreen`, `CasinoButton`, panels), `client/hud/**`, `client/ClientCasinoState.java`, `client/table/CasinoTableScreen.java` + `ClientTableCache.java` (hooks), `client/cashier/**`, `vip/client/CasinoMenuScreen.java`, `loan/client/LoanScreen.java`, `multiplayer/client/*Screen.java` styling, chip item models | J4, J6 (+ `holdBalanceDelta`, cards J-C13 reveal gate), J9, J10, J18, J15 (screen part), RU layout gametest helper | ▶ now (sprites: placeholders until X-M2) |
| **J-L3 World & meta FX** | `chaos/**`, `lastchance/**`, `vip/**` (except CasinoMenuScreen), `loan/**` (except LoanScreen), `worldgen/**` attract hooks, block `.mcmeta` for attract strips | J11, J12, J13, J14, J15 (arrival), J19, J20; ⏸ J17 bot nameplates (`bots/**` on the branch) | ▶ now (◆ J-M1 for overlay-based beats) |
| **J-L4 Cards A: kit + blackjack + baccarat** | `core/anim/cards/**` (`CardMotion`), `client/table/cards/**` (`CardSprites`, `CardAnimator`, `ChipStackView`, `TableStamp`, `ActionTag`), `games/blackjack/**`, `games/baccarat/**` | C0, C1 (blackjack/baccarat beats), J-C1, J-C4, J-C5, J-C6, J-C9 (+ chemmy), dealer gesture events for these two | ▶ now (C0/C1/J-C1/J-C4 pure + server) |
| **J-L5 Cards B: poker + UTH + BER + dealers** | `games/poker/**`, `games/uth/**`, `client/table/CardTableRenderer.java`, `client/dealer/**`, dealer renderers | C1 (poker/UTH beats incl. **slow run-out**, L3), J-C2, J-C3, J-C7, J-C8, J-C10, J-C11; ⏸ J-C12 bot seats | ▶ now (◆ J-L4 `CardAnimator` interface, published day 2) |
| **J-L6 Tables: roulette, craps, Dice Duel** | `games/roulette/**`, `games/craps/**`, `core/anim/dice/**`, `client/table/fx/**` (table kit, was `client/fx/table`), Dice Duel files in extras: `games/extras/server/{DiceGame,DuelStage}.java`, `games/extras/client/{DiceScreen,DuelInviteScreen}.java` | J-A0, J-R1…R4, J-C1…C4 (craps), J-D1, J-D2, J-T; ⏸ craps bot shooter delay | ▶ now (pure paths + server data) |
| **J-L7 Extras & PvP presentation** | `games/extras/**` except the Dice Duel files, `pvp/**`, `client/pvp/**`, `core/mixin/DisplayAccessor*` (with core review) | JX2, JX3, JX4, JX5, SX2(extras anim); ⏸ JX6, JX7, JX8, JX9, JX10 | ▶ now (solo games); ⏸ PvP |
| **J-L8 Slots engine & server** | `games/slots/v2/logic/**`, `games/slots/*.java` (server: module, BE, jackpot data, chaos bridge, api), `games/slots/pvp/**` (server), `core/config/sections/SlotsConfig*` (with core), slots tests | S-J1 ways + tumbles, S-J2 draw + tape + codec + pools + persistence, S-J3 anticipation + `SlotTimeline` (SX2), S-J4 config v2 + `validateRtp`, S-J5 protocol (JS13) + settle at gate + skip + `SpinSync` data + cut-over, S-J6 migration + `LegacySlots`, S-J7 integrations (§7.2), ⏸ S-J8 Showdown v2 mode | ▶ now. **S-M1** (week 1): evaluator + tumbles match §7.5 + vectors; **S-M2**: draw/tape/codec; **S-M3**: timeline vectors |
| **J-L9 Slots screen** | `games/slots/client/**` except the renderer / particles / Showdown screen; `src/main/sounds/slots/sounds.json` | JS1–JS12, JS17, SX3 (Java sounds) | ▶ now against `SlotTimeline` stubs + fake tapes; ◆ S-M3 for real timelines |
| **J-L10 Slots in-world & Showdown screen** | `games/slots/client/{SlotCabinetRenderer,SlotsParticles}.java`, `games/slots/SlotsFx.java`, slots particles JSON, cabinet models | JS14, JS15; ⏸ JS16 | ◆ S-M1 (window data) — renderer shell ▶ now |

### 9.2 Bedrock lanes (10)

| Lane | Owns (paths under `bedrock/` unless noted) | Tasks | Start |
|---|---|---|---|
| **B-L1 Presentation core** | `src/core/presentation/**`, `src/core/logic/anim/**` (extensions), `src/core/fx*.ts`, `packs/core/RP/{particles,sounds}/**` | B-S0/BS0 DDUI spike (**day 1**), B1 (`FxService.celebrate` with L2 API), B2, B3, BX1, B-A0 (table helpers `soundTimeline`, `revealAfter`), core sound ids of cards/tables | ▶ now. **B-M1** (week 1): celebrate + scheduler adopted + spike report |
| **B-L2 HUD & meta** | `src/core/hud.ts`, `packs/core/RP/ui/**`, `src/core/{cashier,casino,achievements,wagers}.ts` (presentation parts), core form icons wiring | B4, B5, B6, B11, B14 (settings form), ⏸ B12 bot presence | ▶ now |
| **B-L3 World & meta FX** | `src/{chaos,lastchance,vip,loan}/**`, `src/worldgen/**` attract loop, `packs/{chaos,lastchance,vip,loan,worldgen}/RP/**`, `tools/assets/modules/meta.mjs` | B7, B8, B9, B10, B13, B15 | ▶ now (◆ B-M1 for celebrate) |
| **B-L4 Cards A** | `src/games/{blackjack,baccarat}/**`, `src/core/logic/anim/cards/**`, `src/core/cards-fx.ts`, `packs/{blackjack,baccarat}/**`, `tools/assets/modules/cards.mjs` | C0 (TS), C1 (bj/bac), B-C1, B-C4, B-C5 (ticker), B-C7, C2 art | ▶ now |
| **B-L5 Cards B** | `src/games/{poker,uth}/**`, `packs/{poker,uth}/**`, dealer animation files `packs/core/RP/{animations,animation_controllers}/dealer*` | C1 (poker/UTH incl. run-out titles), B-C2, B-C3, B-C6, B-C8; ⏸ B-C9; NICE B-C10 | ▶ now (◆ B-L4 ticker API day 2) |
| **B-L6 Tables** | `src/games/{roulette,craps}/**`, `src/core/logic/anim/dice/**`, `src/games/extras/{dice-game,duel-stage}.ts`, `packs/{roulette,craps}/**`, `tools/assets/modules/tables.mjs` | B-A1, B-R1…R3, B-C1, B-C2 (⏸ bot delay part), B-D1, B-N | ▶ now |
| **B-L7 Extras & PvP** | `src/games/extras/**` except the Dice Duel files, `src/pvp/**` ⏸, `packs/extras/**`, `tools/assets/modules/extras.mjs` | BX2, BX3, BX5, BX7, BX9, SX2 (TS), SX4; ⏸ BX4, BX6, BX8, BX10; NICE BX11 (uses `LiveForm`) | ▶ now (solo); ⏸ PvP |
| **B-L8 Slots engine & service** | `src/games/slots/v2/logic/**`, `src/games/slots/{index,api}.ts` + new `service.ts`, `src/games/slots/logic/**` (v1 → legacy), `src/games/slots/pvp/**` ⏸ | S-B1 ways + tumbles, S-B2 draw/tape/codec/pools/persistence, S-B3 anticipation + timeline, S-B4 config + `validateRtp`, S-B5 service (round lifecycle, gate, skip, reservation) + cut-over, S-B6 migration, S-B7 integrations, ⏸ S-B8 Showdown v2 mode | ▶ now (mirror of J-L8; vectors from Java or both from SLOTS.md) |
| **B-L9 Slots form presentation** | `src/games/slots/v2/present/{frames,ddui-form,classic,features,celebrate,settings}.ts` | BS1, BS2 (on `LiveForm`), BS3, BS4, BS5, BS10 | ▶ now (frames on stub timelines); ◆ B-S0 for DDUI |
| **B-L10 Slots cabinet & Showdown** | `src/games/slots/v2/present/cabinet-driver.ts`, `src/games/slots/cabinet.ts`, `packs/slots/**`, `tools/assets/modules/slots.mjs` (**both editions' slot art**) | SX1 (slot art incl. Java sheets), BS6, BS7, BS8, SX3 (Bedrock sounds); ⏸ BS9 | ▶ now (art + entity files); ◆ S-M1 for real strips |

### 9.3 Dependency graph and schedule

```
day 1 ─┬─ X-L0 framework ──► X-M1 (glyph sheet, lib) ──► X-M2 (core art) ──► all screens' final sprites
       ├─ J-L1 / B-L1 core ──► J-M1 / B-M1 (overlay, ServerFx, FxService, sounds) ──► celebrations in every game
       ├─ B-S0 DDUI spike (B-L1) ──► BS2, BX11, [V] items resolved
       ├─ pure logic in every game lane (beats, paths, anim, slots engine) ──► vectors ──► screens/BER/entities
       ├─ server-data lanes (J-C1..4, J-R2, J-C2, J-D1, S-J5 data) ─ independent
       └─ PvP-bots merge ──► ⏸ tasks (JX6–10, BX4/6/8/10, J17/B12, J-C12/B-C9, JS16/BS9, S-x8, craps bot delay)
```

Critical path: **slots** (S-M1 → S-M3 → JS1/BS1 → features → Showdown) and **J-M1/B-M1** (every celebration). Lanes
never wait for art: placeholders from the generator's "debug" palette are acceptable until X-M2 and the per-game
art modules land. Merge order inside the wave: core lanes (X-L0, J-L1, B-L1) first, then game lanes in any order;
the two slot cut-overs (S-J5, S-B5) last, together with strings/config/advancements.

### 9.4 Review gates (every task)

global.md §6 checklist; the spec's fidelity section (cards §0.7, tables §0.6, extras §0.3/§14, slots §2.1); vectors
green in both editions; reduce motion / flashes off / RU at GUI scale 2 and 4 / compact mode checked; budgets of
§2.7 and the specs; no literal strings (`checkNoLiterals`, `check-strings`); ownership checks green.

---

## 10. Skeleton inventory and verification

Added in this wave (compiling; nothing is wired into a module, so behaviour is unchanged):

- Java main: `core/anim/{Ease,Clock,Beat,Timeline,TimelineBuilder,TimingProfile,TimelineSeed,SeedMix,WinTier,
  WinTierTable,TierWords,RollUp,TextFit,FrameModel}.java`, `core/sound/{SoundCatalog,CasinoSounds}.java`,
  `core/fx/ServerFx.java`, `games/slots/v2/logic/{Machine,SymbolRole,MachineDef,Window,Ways,Tumble,SpinTape,TapeCodec,
  SlotRng,SlotDraw,Jackpots,Anticipation,SlotTiers,SlotTimeline,Showdown}.java`.
- Java client: `client/anim/{AnimClock,Tween,TimelinePlayer}.java`, `client/fx/{FxSettings,CasinoPalette,
  CelebrationRequest,CelebrationOverlay,GuiParticlePool}.java`, `client/render/{SpectatorBlockEntityRenderer,
  SpectatorRenderState}.java`.
- Java tests: `core/anim/CoreAnimVectorsTest` (+ generated `src/test/resources/fx/vectors/core_anim.json`),
  `games/slots/v2/logic/SlotsV2SkeletonTest`.
- Bedrock: `src/core/logic/anim/{ease,seed,timeline,win-tier,rollup,sound-ids,glyph-map,index}.ts` + `vectors.test.ts`,
  `src/core/presentation/{settings,scheduler,live-form,camera,rollup-hud,props,index}.ts` (exported from
  `core/index.ts` as `anim` and `presentation`), `src/games/slots/v2/logic/{types,engine,jackpots,tape-codec,
  anticipation,tiers,timeline,showdown,index}.ts` + `skeleton.test.ts`, `tools/gen-assets.mjs`,
  `tools/assets/{lib/{png,palette,grid,emit}.mjs,lib/grid.test.mjs,modules/core.mjs}`, `tools/sync-fx-vectors.mjs`,
  `test/fx/vectors/core_anim.json`; npm scripts `gen:assets`, `check:assets`, `sync:vectors`; two reviewed entries in
  `test/independent/casino-guard.test.ts` for the presentation intervals.
- Strings: `STRINGS.md` gained `### Win tiers and celebrations` (core, the 9 `gui.burmaldaholic.fx.tier.*` /
  `fx.returned` keys of global §9) and `### Slots v2 tier words` (early import of the 8 SLOTS.md §13 keys that
  `SlotTiers.WORDS` references); lang fragments regenerated in both editions (additions only). The rest of the
  animation strings is lane X-L0.
- The Java and TypeScript implementations produce **byte-identical** canonical timelines, seeds and RNG sequences and
  equal easing/tier/roll-up values (`core_anim.json`).

Verified: Java `./gradlew build` (compile 26.2, unit tests, GameTests, `checkLinkage` vs 26.3); Bedrock
`npm run build && npm test && npm run lint`.
