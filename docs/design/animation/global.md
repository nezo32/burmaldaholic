# Burmaldaholic — Animation Spec: Global & Meta Presentation

Scope: everything that is not one table game. HUD (balance ticker, streak meter, VIP badge, Golden
Hour, loan), Casino Menu and Cashier (chip counting / exchange), chip items and stacks, chaos events,
big-win / jackpot announcements, VIP tier-up, Last Chance coin flip, Loan Shark and Debt Collector
arrival, achievements and toasts, bot presence, idle/attract modes of world casinos, and the **shared
UI style guide** (palette, panels, buttons, typography, motion, sound) that every per-game spec in
`docs/design/animation/*.md` builds on.

Status: design only. Nothing here changes game rules or odds. Where this file defines something
shared (win tiers §2.4, easing names §2.5, sound ids §2.7, FX settings §2.8, the `fx` channel §3),
the per-game specs must use it instead of defining their own.

> **Capabilities used** (checked against `docs/research/animation.md`, 2026-09-24):
> (A1) Bedrock `player.camera.fade({fadeTime, fadeColor})` is stable in `@minecraft/server` 2.8.0;
> camera shake only through `runCommand('camerashake add @s <intensity> <seconds> positional')`.
> Every camera beat below is skipped under reduce motion and has a title-only baseline.
> (A2) Bedrock JSON UI (`hud_screen` title-sentinel panel, `anim_type` `alpha`/`offset`/`size`/
> `color`/`flip_book`/`wait`) is unofficial and fragile: every JSON UI feature here is an
> **enhancement over a working action-bar/title baseline**.
> (A3) Bedrock custom particles with `MolangVariableMap`, `flipbook_textures.json`,
> `onScreenDisplay.updateSubtitle` (roll-ups without re-fading) and server-ui DDUI `CustomForm` +
> Observables are stable.
> (A4) Java 26.2: `GuiGraphicsExtractor` (`nextStratum`, `blurBeforeThisStratum`, scissor stack),
> GUI sprites with nine-slice and `animation` `.mcmeta`, Fabric `HudElementRegistry`, `Toast` +
> `ToastManager`, `FabricParticleTypes` + `ParticleProviderRegistry`, display-entity setters only
> via accessor mixins (the bots branch already has `TextDisplayAccessor`/`DisplayAccessor`), item
> model `minecraft:range_dispatch` on `minecraft:count` **(verify)**.
> Settings names follow the research proposal (`anim.reduceMotion`, `anim.flashes`, `anim.speed`,
> `anim.volume`).

---

## 0. Audit — what exists today

Code read: Java `client/hud/CasinoHud.java`, `client/ClientCasinoState.java`,
`client/cashier/CashierScreen.java`, `client/table/CasinoTableScreen.java`,
`vip/client/CasinoMenuScreen.java`, `lastchance/client/CoinFlipOverlay.java`,
`chaos/client/ChaosClientModule.java`, `chaos/ChaosEffects.java`, `chaos/GoldenHour.java`,
`vip/VipService.java`, `loan/LoanSquads.java`, `worldgen/client/NpcRenderers.java`, bots
`BotAvatars.java` (branch `worktree-agent-afbccfe640358d8fc`); sounds under `java/src/main/sounds/*`.
Bedrock `core/hud.ts`, `core/module-core.ts`, `core/achievements.ts`, `core/wagers.ts`,
`chaos/engine.ts`, `chaos/index.ts`, `lastchance/index.ts`, `vip/vip.ts`, `loan/collectors.ts`,
`tools/gen-glyphs.mjs`, all `packs/*/RP` contents (branch `worktree-agent-aaf0f54e81d19ae4a` for the
newest bots/PvP code).

### 0.1 Java

| Area | What is there | Problems |
|---|---|---|
| HUD | Panel top-left, black 40 %, lines: chip item icon at 0.5× + balance; streak text; VIP tier name right-aligned; loan; Golden Hour timer. Floating `+120`/`−50` text appended **on the same line** for 30 ticks. | Balance jumps instantly (no count-up). Delta text is static, not floating. Pulse = **whole line disappears every 10 ticks** (streak ≥ 7, loan default): unreadable and a flashing hazard. Chip icon is a 0.5×-scaled item (blurry at odd GUI scales). No streak meter, no VIP badge art, Golden Hour is only a gold text colour. Panel is not on brand (plain black). U+E100 glyph font (UI.md §0.1) **does not exist in the Java assets** (no `font/` folder). |
| Casino Menu | Own palette `0xF01B1F2A` navy + `0xFFB8963E` brass; vanilla buttons; text tabs; progress bar lines. | Palette differs from the table screens (flat green `0xFF1E5E3A`) and from the brand (purple/gold). No tab transitions, no VIP progress animation. |
| Cashier | Flat green panel, vanilla buttons in flow rows, balance text top-right. | Deposit/withdraw/exchange give **no visual feedback** except the balance number changing. No chip art in the GUI at all. |
| Tables (shared base) | `CasinoTableScreen`: flat felt fill, vanilla `Button`, error text 60 ticks. | No shared banner, no shared celebration, no hover/press styling. Every game re-invents. |
| Chip items | 5 flat 16×16 textures (`item/core/chip_*.png`). | Same sprite for 1 or 64 chips. |
| Chaos | Server-side vanilla title + vanilla particles (`TOTEM_OF_UNDYING`, `HAPPY_VILLAGER`, `END_ROD`, `POOF`, `PORTAL`) + vanilla sounds. `ChaosClientModule` is an empty TODO. | Every event looks like the same title card. Good vs bad events are not distinguishable before reading. Diamond rain uses green "happy villager" particles. Mob wave: `POOF` only. |
| Golden Hour | Title (gold) + chat + bell (vanilla bell, pitch 0.8) + yellow boss bar `NOTCHED_10`. | No world-wide visual mood, no end beat besides chat. |
| Big wins | `core.announceBigWins` / `bigWinThreshold` exist in `CoreConfig` but **nothing in Java uses them** (Bedrock does: chat line). Slots jackpot: chat broadcast only. | No personal celebration scale, no spectator cue. |
| VIP tier-up | Vanilla title + subtitle + sound + chat, Netherite broadcast, particles by tier on wins. | Plain title; no badge reveal. |
| Last Chance | `CoinFlipOverlay`: title "Last Chance…" at 2×, coin 32×32 (2 textures) squashed by `cos` for 30 t, landing, subtitle, fade 15 t. Result is in the payload before the spin starts (good). | Coin is 2 sprites squashed (no edge frames, no shading), no heartbeat/vignette build-up, no sound sync (server plays totem sound at once, before the client coin lands). |
| Loan collectors | Title "Knock knock" (red) + one `collector_knock` (vanilla door close, pitch 0.6) + `POOF` on despawn. | Arrival is a single sound; the squad just appears. |
| Achievements | Real advancements → vanilla toasts (fine). | Other toasts (contracts, cashback) are chat-only. |
| Bot presence | Branch: text-display nameplate 1.6 blocks above a seat, 0x40 background, billboard; emote particles. | No "thinking" state, no join/leave motion. |
| World casinos | Static blocks; dealers on vanilla models; **no block-entity renderers at all**. | Casinos look dead when no one plays. |
| Sounds | 16 events, all single vanilla samples (`levelup` = win, `note_block.bass` = lose). | Thin, repetitive, no tiering. |

### 0.2 Bedrock

| Area | What is there | Problems |
|---|---|---|
| HUD | Action-bar status line only (`core/hud.ts`), refreshed every 40 t when no message was posted in 60 t; balance yellow / gold in Golden Hour; streak with flame/cloud glyph; VIP name; loan; Golden Hour clock. JSON UI panel from UI.md §1 **not implemented** (no `ui/` folder in any RP). | No count-up, no delta, line hides whenever any game posts to the action bar; nothing to animate. |
| Glyphs | `packs/core/RP/font/glyph_E1.png` generated (chip, cards, suits, dice, flame, cloud, VIP badges). | Good base; no animation frames. |
| Casino Menu / Cashier | Forms. | Forms cannot animate; no post-action feedback besides re-shown form. |
| Chaos | Title + action bar + vanilla particles (`totem_particle`, `villager_happy`, `large_explosion`) + vanilla sounds. `packs/chaos/RP` is empty. | Same as Java plus no custom particles. |
| Golden Hour | Chat + `block.bell.hit` to everyone, HUD clock. | **No title** on Bedrock start (Java has one); no end cue besides chat. |
| VIP | Title + `random.levelup` + promo particles + chat. | — |
| Last Chance | Totem particle + `burmaldaholic.last_chance` sound + action bar "Last Chance…" + title "HEADS!". Tails → chat only. | **No coin flip at all**; the result title appears at the same instant as "Last Chance…". |
| Loan collectors | Chat, `random.door_close` pitch 0.6, smoke/crit particles. | Weak arrival. |
| Achievements | Chat + action bar + `random.toast`. | Fine but plain. |
| Sounds | One custom definition (`burmaldaholic.last_chance`). Everything else vanilla ids. | Java ids like `burmaldaholic:win` have **no Bedrock counterpart**. |

### 0.3 Summary of the gap

1. No shared motion or celebration system: every module prints a title.
2. HUD values snap; pulses blink whole lines off.
3. Brand palette (docs/branding) is used nowhere in the UI.
4. Chaos events are indistinguishable at a glance (good / bad / neutral).
5. Bedrock Last Chance has no coin flip; Java's is thin and not sound-synced.
6. World casinos have no idle life.
7. Sound palette is one vanilla sample per event, different between editions.

---

## 1. Principles

1. **The server decides, the animation tells.** Every animation starts only after the server
   has fixed the outcome and sent it (payload / script state). Animations never show a
   provisional or "almost" result that differs from the final one (no near-miss teases that
   land on something else, no count-up that overshoots and comes back, no jackpot lights on a
   machine that did not pay). Timers shown are derived from server ticks.
2. **Skippable, never blocking.** Any celebration longer than 1 s can be skipped (Java: click /
   any key / Esc in the overlay; Bedrock: the title is replaced by the next form). Balance and
   results are final the moment the server sends them; the animation is decoration.
3. **Readable first.** Text is never blinked off; emphasis uses colour, scale and motion only.
4. **One vocabulary.** Same win tiers, easing names, sound ids, colours and FX settings in every
   game and both editions.
5. **Cheap.** Budgets in §2.9; everything degrades to the current behaviour when an asset or
   feature is missing.

---

## 2. Shared UI style guide (all specs use this)

### 2.1 Palette tokens

Derived from `docs/branding/branding.md` (logo) plus the functional colours of UI.md §0.1.
Java constants live in a new `dev.nezo.burmaldaholic.client.fx.CasinoPalette`; Bedrock uses the
§-code column (forms/titles) and the hex for textures.

| Token | Hex (ARGB for Java) | § fallback | Use |
|---|---|---|---|
| `bg.deep` | `#26103C` (`0xE626103C` panels, 90 %) | — | Casino Menu, Cashier, overlay panels |
| `bg.darkest` | `#140822` | — | panel border outer, HUD background (70 %) |
| `ink` | `#180A28` | §0 | text outline / 1 px sprite outline |
| `frame` | `#783CBE` | §5 | panel inner border, tab underline, button border |
| `glint` | `#BE5AFF` | §d | hover highlight, focus ring |
| `lilac` | `#D696FF` | §d | sparkles, secondary text on dark |
| `felt` | `#1E5E3A` / border `#0E2E1C` | — | table screens only (existing) |
| `chip.red` / light / dark | `#D83440` / `#FF6E6A` / `#8C1834` | §c | loss, curse, collectors, danger buttons |
| `bone` / `bone.shade` | `#F4ECF8` / `#C0B0DC` | §f / §7 | body text on dark, disabled text |
| `gold` / `gold.shade` | `#FFD640` / `#B07010` | §6 | balance, Golden Hour, primary buttons, jackpot |
| `bonus` | `#80FF40` | §a | wins, `+` deltas, good chaos |
| `curse` | `#6FA86A` on `#3A1450` | §2 | curses (sickly green on purple) |
| `cool` | `#8FA8C8` | §9 | unlucky streak (existing HUD value kept) |
| VIP tiers | Bronze `#C8763C`, Silver `#C8C8D8`, Gold `#FFD640`, Platinum `#E8F4FF`, Diamond `#5CE8E0`, Netherite `#5A4A58` + ember `#FF7A3C` | §c §7 §6 §f §b §5 (UI.md) | badges, tier-up |

Rules: text on `bg.deep` uses `bone` (contrast ≥ 7:1) or `gold` (≥ 8:1); never `frame` purple for
text. Win = `bonus` + word, loss = `chip.red` + word, push = `bone.shade` + word (UI.md §13).

### 2.2 Panels, buttons, typography

**Panels (Java).** Replace flat fills with nine-slice GUI sprites (sprite `.mcmeta` with
`"gui": {"scaling": {"type": "nine_slice", "width": W, "height": H, "border": B}}`):

| Sprite (`textures/gui/sprites/burmaldaholic/…`) | Size | Border | Use |
|---|---|---|---|
| `panel/casino.png` | 32×32 | 6 | Casino Menu, Cashier, Loan, overlays: `bg.darkest` 1 px outer, `frame` 1 px, 1 px `glint` top-left bevel, `bg.deep` fill, 4 gold rivets in corners |
| `panel/felt.png` | 32×32 | 6 | table screens (felt `#1E5E3A` with 2-tone dither, wood rim `#5A3418`/`#3A2010`, gold 1 px inner line) |
| `panel/inset.png` | 16×16 | 3 | wells: balance box, amount fields, history strips (`#140822` 80 %, 1 px `frame` bottom-right highlight) |
| `panel/hud.png` | 16×16 | 3 | HUD background (`#140822` at 70 %, 1 px `frame` at 60 %) |
| `panel/hud_golden.png` | 16×16 | 3 | HUD during Golden Hour (gold 1 px border) |
| `panel/tab.png`, `panel/tab_selected.png` | 32×16 | 4 | Casino Menu tabs |

**Buttons (Java).** A `CasinoButton extends Button` using `WidgetSprites` (normal, disabled,
highlighted) in two families; vanilla font, label centred, width rule unchanged
(`max(minWidth, textWidth + 8)`).

| Sprite set | Normal | Highlighted (hover/focus) | Disabled |
|---|---|---|---|
| `widget/casino_button*.png` (secondary) 200×20 nine-slice border 3 | `frame` border, `#3A1A5C` fill, `bone` label | border `glint`, fill `#4A2474`, label `gold` | fill `#2A1640`, border `#4A3060`, label `bone.shade` |
| `widget/casino_button_primary*.png` (Spin, Deal, Deposit, Take loan) | `gold` fill `#E8B830` with `gold.shade` bottom 2 px, `ink` label | `#FFD640`, 1 px `bone` top line | as secondary disabled |
| `widget/casino_button_danger*.png` (Soul wager, Take the bank, Fold-all-in) | `chip.red` fill, `bone` label | `chip.light` fill | as secondary disabled |

Button states and motion (all Java screens):
- **Hover**: sprite swap + label colour; 1 px lift (draw y − 1) eased over 80 ms `outQuad`; no sound.
- **Press**: y + 1 for 60 ms, `ui.button.click` vanilla (unchanged), then action is sent.
- **Invalid** (server error or disabled click): 3-cycle horizontal shake ±2 px, 240 ms `shake`
  curve; `burmaldaholic:ui_deny`; error line under the row (existing 60 t) now slides in 4 px from
  below, 150 ms `outCubic`. Reduced motion: no shake, error line only.
- **Disabled**: no hover lift; tooltip gives the reason (UI.md).
- Keyboard focus: 1 px `glint` outline (always, not motion-dependent).

**Bedrock buttons.** Forms keep vanilla styling (JSON UI form re-skins are out of scope: they break
with every vanilla UI update). Button **icons** carry the brand: new 32×32 icon textures in the
palette (§5.2 list). Errors: the re-shown form's first body line in `§c` plus `burmaldaholic.ui_deny`.

**Typography.** Vanilla font only, both editions. Allowed scales: 1× (body), 2× (titles, banners),
3× (tier words in the celebration overlay only, when it fits). No fractional scales (blurry). All
numbers right-aligned in columns; the vanilla font's digits are all 6 px advance, so tickers do not
jitter. Big text gets a 1 px `ink` outline drawn as 4 offset copies (not the default drop shadow)
via one helper `FxText.outlined(g, text, x, y, scale, color)`.
Russian: every banner/word is measured at runtime; if `width × scale > screenWidth − 32`, the scale
steps down (3→2→1) and at 1× the text wraps (max 2 lines). Budget 1.45 × EN width for any fixed box.

### 2.3 Glyphs (both editions)

Existing (UI.md §0.1): chip U+E100, cards, suits, dice, flame U+E170, cloud U+E171, VIP badges
U+E180–E185. **Java must add the same sheet** (`assets/burmaldaholic/font/default.json`, bitmap
provider over `textures/font/glyph_e1.png`, height 8, ascent 7), generated by the same
generator (task S1) so both editions stay pixel-identical. New code points for this spec:

| Code point | Glyph | Frames |
|---|---|---|
| U+E172 | streak pip empty (5×7 in the 16 cell) | — |
| U+E173 / U+E174 | streak pip lucky / unlucky | — |
| U+E175 | Golden Hour sun | — |
| U+E176 | loan alert (red bell) | — |
| U+E177 | collectors (fist) | — |
| U+E186–U+E189 | coin spin frames (face, ¾, edge, ¾ back) — Bedrock Last Chance and Coin Flip | 4 |
| U+E18A / U+E18B | coin heads / coin tails | — |
| U+E190 | bot (already reserved by BOTS.md) | — |
| U+E191–U+E193 | thinking dots 1/2/3 | 3 |
| U+E194–U+E198 | mini chips 1/5/25/100/500 (cashier breakdown lines) | — |
| U+E199 | emerald | — |
| U+E19A | gold ingot | — |
| U+E19B | sparkle (✦ in lilac) | — |

Glyphs are never in lang strings (UI.md rule): code prepends them as separate components.

### 2.4 Win tiers (shared, server-computed)

A pure function in core logic of both editions, `WinTier.of(net, stake, flags)`; the server sends
the tier with the result so clients never guess.

Multiples are **total return ÷ total stake** (research §5.7: 10/25/50×, config
`slots.bigWinTiers` generalised to `core.winTiers` = [10, 25, 50]).

| Tier | Condition (first match from the top) | Presentation budget |
|---|---|---|
| `JACKPOT` | progressive jackpot, wheel/scratch top prize, Plinko edge bin on High | full overlay 4.0 s + world FX + server-wide toast |
| `EPIC` | return ≥ 50 × stake **and** net ≥ 500, or net ≥ `core.bigWinThreshold` (5 000) | full overlay 3.0 s + world FX + server chat (existing big-win broadcast) |
| `MEGA` | return ≥ 25 × stake and net ≥ 250 | overlay 2.5 s + nearby FX |
| `BIG` | return ≥ 10 × stake and net ≥ 100 | overlay 2.0 s + chip burst |
| `WIN` | net > 0 | in-screen banner 0.8 s |
| `PUSH` | net = 0 | banner 0.6 s, gray |
| `RETURN` | 0 < return < stake (a "loss disguised as win") | muted banner "Returned N", `push` sound, **no** win colours or fanfare (research §5.11) |
| `LOSS` | return = 0 | banner 0.6 s, red word, no FX; slots stay silent, tables play the soft `lose` |

`stake` = total chips at risk in that settlement (all bets of the round). Pawn stakes use their chip
value. PvP uses the same tiers on the payout vs the player's stake. The EPIC rule matches the chaos
big-win buff rule (GAME_DESIGN §13.1.3, net ≥ 50 × stake and ≥ 500) and the broadcast threshold, so
the three always agree. Tiers are computed by the server and sent; the client never derives them.

### 2.5 Motion vocabulary

Java: `dev.nezo.burmaldaholic.client.fx.Ease` (static float functions, `t ∈ [0,1]`), `Tween`
(start value, end value, start ms, duration, easing; `value(nowMs)`), all timed on
`Util.getMillis()` + partial tick so animation is frame-rate independent.
Bedrock: script tweens are tick-quantised (50 ms); JSON UI anims use their own easing names —
map as listed.

| Name | Curve | JSON UI `easing` | Use |
|---|---|---|---|
| `outCubic` | 1 − (1 − t)³ | `out_cubic` | default enter, count-up of gains |
| `inCubic` | t³ | `in_cubic` | exits |
| `inOutQuad` | standard | `in_out_quad` | count-down of losses, panel slides |
| `outBack` | c=1.70158 overshoot | `out_back` | badges, pips, chips landing (visual only, never numbers) |
| `outElastic` | p=0.3 | `out_elastic` | tier words in EPIC/JACKPOT only |
| `shake` | sin(6πt)·(1 − t) | — (script offsets) | invalid, bad chaos |
| `linear` | t | `linear` | spins, timers |

Durations: micro 80–150 ms, small 200–350 ms, medium 400–700 ms, large 1–4 s.
Numbers never use `outBack`/`outElastic` (a count-up must be monotonic).

### 2.6 Celebration kit (Java `client/fx`, Bedrock `core/fx.ts`)

The per-game specs call these; they do not build their own.

**Java `CelebrationOverlay`** (HUD layer above chat, below screens' tooltips; also drawn on top of
open casino screens via `Screen#extractRenderState` hook in `CasinoTableScreen`):

| Beat | WIN | BIG | MEGA | EPIC | JACKPOT |
|---|---|---|---|---|---|
| 0 ms | banner slides from 8 px below, 150 ms `outCubic` | + 16 GUI chips burst from the banner, gravity 0.35 px/ms², 900 ms life; backdrop dims to 30 % over 200 ms | new stratum + `blurBeforeThisStratum()`; backdrop 40 %; rays sprite (§5.1) rotating 20°/s | backdrop 50 %; rays + 2nd layer counter-rotating; gold edge vignette (≤ 30 %, 1 Hz) | backdrop 55 %; rays; golden coin shower (40 `coin_spin` sprites, 12 fps) |
| word | "WIN" 2× `bonus` | "BIG WIN" 3× scale-in 0→1 `outBack` 300 ms | "MEGA WIN" 3× `outBack` | "EPIC WIN" 3× `outElastic` 600 ms | "JACKPOT" 3× `outElastic` + letter wave (per-char y = 2·sin(t·8 + i)) |
| amount (roll-up) | `+N` 400 ms | 1 200 ms | 1 800 ms | 2 400 ms | 3 000 ms, `×multiplier` line under it |
| sound | `win_small` | `win_big` | `win_mega` | `win_mega` + `win_big` layer | `jackpot` |
| hold → exit | 400 → fade 200 | 500 → 300 | 500 → 300 | 400 → 400 | 500 → 500 |
| total | 0.8 s | 2.0 s | 2.6 s | 3.2 s | 4.0 s |

Roll-up duration = `min(tierMax, 0.6 s + 0.9 s × log10(1 + return/stake))` (research §5.8),
`outCubic`, starting at 0 and ending **exactly** at the server's net; the final frame prints the
exact value. **Tier upgrade beat**: a MEGA/EPIC overlay starts with the BIG word and upgrades
(word swap with a 150 ms scale punch 1.2 → 1 and a `win_big` sting) at the moment the rolling
amount passes 25× / 50× of the stake; the final word always equals the server tier. Count-up tick:
`chip_count` (pitch 0.9 → 1.4 across the roll-up), at most 15/s, BIG+ only. Click, Space, Enter or
Esc skips to the final frame (hold 300 ms, then exit). `anim.speed` 1.5 (turbo) scales all
durations by 0.67.

**Bedrock `fx.celebrate(player, tier, net, game)`**: classic forms cannot animate, so the
celebration is told with title + **`updateSubtitle` roll-up** + particles + sound **after** the
result is settled and **before** the result form is shown (the form opens 20 t after WIN, 40 t
after BIG, 50 t after MEGA, 60 t after EPIC/JACKPOT, or at once if the player sneaks = skip).
Games that use a DDUI `CustomForm` (research §3.8) may show the roll-up in an Observable label
instead; the timings are the same.

| Tier | Title (fade in / stay / out, ticks) | Subtitle roll-up | Particles (`dimension.spawnParticle`, one call each) | Camera (skipped under reduce motion) |
|---|---|---|---|---|
| WIN | action bar only `§a+N` | — | `burmaldaholic:chip_pop` | — |
| BIG | `§6BIG WIN` (3/36/8) | `§a+N`, `updateSubtitle` every 2 t for 20 t, last value exact | `chip_fountain` (`variable.count` 20) | — |
| MEGA | `§6§lMEGA WIN` (3/44/10) | 24 t roll-up; title re-set from BIG→MEGA when passing 25× | `chip_fountain` 30 + `sparkle` ring | `camera.fade` gold `#FFD640` 0.1/0.1/0.3 s |
| EPIC | `§6§lEPIC WIN` (3/52/10) | 30 t roll-up, upgrades BIG→MEGA→EPIC | `chip_fountain` 40 + `gold_burst` | fade as MEGA + `camerashake add @s 0.15 0.4 positional` |
| JACKPOT | `§e§lJACKPOT` (0/60/12) | 36 t roll-up + `§7×M` suffix | `gold_burst` + `chip_fountain` 60 + vanilla firework sparks | fade 0.1/0.2/0.5 s + shake 0.2 × 0.6 s |

Titles use tier words from STRINGS (§9), never baked text. All counts ≤ 60 particles per burst.

### 2.7 Sound palette (same ids in both editions)

Java: `burmaldaholic:<id>` in `assets/burmaldaholic/sounds.json` (per-module files as today).
Bedrock: `burmaldaholic.<id>` in `packs/<module>/RP/sounds/sound_definitions.json`
(`category` "player" unless noted). **MUST phase = vanilla samples only** (layered variants,
pitch/volume); NICE phase = script-synthesised OGGs (task S4). Existing ids are kept; the Java
definitions of `win`/`lose` are re-pointed as listed.

| Id | Owner | Java composition (sounds.json variants) | Bedrock composition (`sounds/…` paths) | Subtitle key |
|---|---|---|---|---|
| `chip_place` (exists) | core | `block.chain.place` p1.4 v0.6 + `block.amethyst_block.hit` p1.8 v0.3 (2 variants) | `random/click` p1.6, `block/amethyst/hit1` p1.8 | exists |
| `chip_stack` | core | 3 variants of `block.chain.step` p1.2–1.5 v0.5 | `step/chain1-3` p1.3 | `chip_stack` |
| `chip_count` | core | `block.note_block.hat` v0.35 (pitch set by code 0.9–1.4) | `note/hat` | `chip_count` |
| `win_small` | core | `entity.experience_orb.pickup` p1.2 + `block.note_block.chime` p1.6 v0.5 | `random/orb` p1.2 + `note/chime` | `win` |
| `win` (re-pointed) | core | = `win_small` (kept for compatibility) | same | exists |
| `win_big` | core | `entity.player.levelup` p1.0 v0.8 + `block.amethyst_block.resonate` p1.2 | `random/levelup` + `block/amethyst/resonate1` | `big_win` |
| `win_mega` | core | `ui.toast.challenge_complete` v0.7 + `block.bell.use` p1.5 v0.4 | `random/toast` p0.9 + `block/bell/bell_use01` p1.5 | `mega_win` |
| `jackpot` (subtitle exists) | core | `ui.toast.challenge_complete` + `entity.firework_rocket.twinkle` ×2 + `block.bell.use` p1.0 | `random/levelup` p0.8 + `fireworks/twinkle1` + bell | exists |
| `lose` (re-pointed) | core | `block.note_block.bass` p0.7 v0.6 + `block.wool.fall` v0.4 (soft thud, not punishing) | `note/bass` p0.7 | exists |
| `push` | core | `block.note_block.hat` p0.8 v0.4 | `note/hat` p0.8 | `push` |
| `ui_deny` | core | `block.note_block.didgeridoo` p0.6 v0.4 (80 ms) | `note/didgeridoo` p0.6 | `ui_deny` |
| `toast` | core | `block.note_block.chime` p1.2 v0.5 + `block.amethyst_block.chime` | `note/chime` + `block/amethyst/chime1` | `toast` |
| `streak_up` | core | `block.note_block.pling` pitch = 0.8 + 0.08·S | `note/pling` | `streak_up` |
| `streak_break` | core | `block.fire.extinguish` v0.3 p1.6 (lucky) / `weather.rain` short v0.2 (unlucky) | `random/fizz` p1.6 | `streak_break` |
| `vip_tier_up` | vip | `ui.toast.challenge_complete` + `block.note_block.bell` arpeggio (code) | `random/toast` + `note/bell` | `vip_tier_up` |
| `golden_hour` (exists) | chaos | keep bell p0.8 + add `block.beacon.activate` p1.4 v0.5 variant layer played by code | `block/bell/bell_use01` p0.8 + `beacon/activate` p1.4 | exists |
| `golden_hour_end` | chaos | `block.bell.use` p0.6 v0.6 + `block.beacon.deactivate` p1.2 | `beacon/deactivate` p1.2 | `golden_hour_end` |
| `chaos_good` | chaos | `block.amethyst_block.chime` ×2 p1.4/1.8 + `entity.experience_orb.pickup` p1.6 | amethyst + orb | `chaos_good` |
| `chaos_bad` | chaos | `entity.evoker.prepare_wololo` p0.8 v0.6 + `ambient.cave` v0.3 (random variant) | `mob/evocation_illager/prepare_wololo` p0.8 | `chaos_bad` |
| `chaos_teleport` | chaos | `entity.enderman.teleport` p1.2 + `block.portal.trigger` v0.15 p2.0 | `mob/endermen/portal` + `portal/trigger` | `chaos_teleport` |
| `collector_knock` (exists) | loan | 3 knocks played by code at 0/280/520 ms: `block.wooden_door.close` p0.55/0.6/0.5 v0.9 | `random/door_close` p0.55 | exists |
| `collector_arrive` | loan | `entity.pillager.celebrate` p0.8 + `entity.ravager.roar` v0.25 p1.6 | `mob/pillager/celebrate` + `mob/ravager/roar` | `collector_arrive` |
| `heartbeat` | lastchance | `entity.warden.heartbeat` v0.8 (pitch 1.0→1.25 by code) | `mob/warden/heartbeat` | `heartbeat` |
| `coin_land` | lastchance/extras | `block.chain.place` p1.8 + `entity.experience_orb.pickup` p0.9 | `random/orb` p0.9 + click | `coin_land` |
| `last_chance` (exists) | lastchance | keep totem p1.2 v0.7 — **now played by the server on heads only, 1 300 ms after the flip starts** (§4.8) | keep | exists |
| `attract_chime` | worldgen | `block.note_block.chime` p1.8 v0.2 + `block.amethyst_block.chime` v0.15 | `note/chime` p1.8 | `attract` |

Rules: `SoundSource.PLAYERS` for personal, `BLOCKS` for world casinos, never `MASTER` (the current
Golden Hour bell uses `MASTER` — change to `AMBIENT`, respects the player's slider). All
celebration sounds respect the Casino Menu "Casino sounds" toggle (UI.md §2) and "Effects volume".

### 2.8 FX settings and accessibility

New per-player settings (Java: client-local file `config/burmaldaholic-client.json`, shown in Casino
Menu → Settings and a "Client effects" Mod Menu section; Bedrock: player dynamic properties
`burmaldaholic:anim.*`, Casino Menu → Settings ModalForm toggles/dropdowns):

| Setting (id) | Default | Effect |
|---|---|---|
| Reduce motion (`anim.reduceMotion`) | off | No shakes (GUI or camera), no camera fades/moves, no rays rotation, no coin showers, no blur-behind, no overshoot; floating deltas change in place; roll-ups ≤ 300 ms; overlays become a static banner. Bedrock: no `camera.*`/`camerashake`, roll-ups in 2 steps, particle `variable.count` ×0.3. Forces flashes off. |
| Screen flashes (`anim.flashes`) | on | Off: no full-screen or vignette alpha pulses; pulses become static tints; "last seconds" timers change colour once; firework sparks removed. **Even when on**: ≤ 3 flashes per second and ≤ 30 % alpha for any change faster than 250 ms (WCAG 2.3.1); no red strobe. Java: vanilla "Hide lightning flashes" or "Screen effect scale" = 0 also turns this off. |
| Animation speed (`anim.speed`) | 1 | 0.5 slow / 1 / 1.5 turbo: multiplies all durations in this file by 1/speed (never the server timers). |
| Win celebrations (`anim.celebrations`) | Everyone's | `Only mine`: other players' nearby FX/toasts suppressed (chat stays). `Off`: every tier uses the WIN banner. |
| Effects volume (`anim.volume`) | 100 % | Multiplies every sound in §2.7 (Java client side; Bedrock `playSound` volume). |
| Styled HUD panel (`anim.hudPanel`, Bedrock only) | on | Off: action-bar HUD (§4.1.2 fallback). |

Never behind a toggle: the information itself (result words, amounts, timers, chat lines).

### 2.9 Performance budget

| Item | Java | Bedrock |
|---|---|---|
| HUD per frame | ≤ 0.05 ms, no allocations in steady state (cache `Component`s, re-build only when the value changes) | HUD refresh ≤ 1 title-sentinel update / 2 t per player during a count-up, else ≤ 1 / 20 t |
| GUI particles (chips, coins, confetti) | ≤ 96 live, pooled; drop newest beyond | n/a |
| World particles per event | ≤ 60 client particles per burst, ≤ 150 for JACKPOT, ≤ 400 casino particles live in total (checked in the particle provider) | ≤ 24 `spawnParticle` calls per event per tick, ≤ 60 per event total (custom emitters do the multiplying client-side) |
| Server FX packets | ≤ 1 `fx` payload per settlement per player, ≤ 4 per second per player | script: `fx.*` ≤ 0.3 ms per tick; timeouts via `system.runTimeout`, never per-tick loops per player for decoration |
| Attract mode | client `animateTick` only, 1 roll per 40 t per block within 16 blocks | script loop every 40 t over tables with a player within 16 blocks, ≤ 8 tables per dimension per pass |
| Textures | all FX sprites in one GUI atlas page (sprites folder), total < 64 KB PNG | one particle atlas `textures/particle/burmaldaholic_fx.png` 128×128 |

---

## 3. Shared plumbing (both editions)

### 3.1 Java `fx` channel

One clientbound payload `burmaldaholic:fx` (`FxPayload`), registered in core:
`record FxPayload(Kind kind, int tier, long amount, long stake, String game, int arg, Optional<Vec3> pos, Optional<UUID> actor, Component actorName)`,
`Kind ∈ {WIN, BIG_WIN_NEARBY, BROADCAST, CHAOS, GOLDEN_HOUR_START, GOLDEN_HOUR_END, VIP_UP,
COLLECTORS, CASHIER, TOAST}`. Senders check `ServerPlayNetworking.canSend(player, FxPayload.TYPE)`;
without it the current vanilla title/particles path runs unchanged (fallback). The server keeps
spawning **item/entity effects** (diamonds, mobs); the client only adds decoration.

`PlayerStatusPayload` unchanged; the HUD derives deltas client-side (already does).

### 3.2 Bedrock `core/fx.ts`

`FxService` in core services: `celebrate(p, tier, net, game)`, `chaos(p, event, data)`,
`toast(p, titleRaw, bodyRaw, icon)`, `hudPulse(p, segmentId)`, `settings(p)`. Implements rate limits
(§2.9), the settings (§2.8), and the JSON UI sentinel protocol (§4.1.2). Modules call it instead of
`hud.title` + `playSound` directly.

---

## 4. Feature specs

Format per feature: target storyboard (ms), spectators, assets, feedback, faithfulness, accessibility,
MUST/NICE.

### 4.1 HUD

#### 4.1.1 Java HUD

Layout (panel `panel/hud.png`, padding 4, line 10 px, width = widest line + 8; RU ≤ 200 px):

```
┌───────────────────────────────┐
│ ⛁ 12 500            +120 ↑    │  balance ticker + floating delta (floats above the row)
│ ▮▮▮▮▯▯▯▯▯▯ 🔥4     ◆ Gold     │  streak meter (10 pips) + value · VIP badge 8×8 + name
│ 🔔 Loan 1d 04:12 · owed 600    │  loan (bell icon animates in default)
│ ☀ Golden Hour 02:31           │  Golden Hour (sun icon spins, gold border on panel)
└───────────────────────────────┘
```

**Balance ticker (MUST).**
- On a status packet with a new balance: `Tween(displayed → target)`, duration
  `d = clamp(250 + 150·log10(|Δ|), 250, 1100)` ms; gains `outCubic`, losses `inOutQuad` and
  capped at 600 ms (losses do not linger). New packet mid-tween → restart from the currently
  displayed value (no jump back).
- Colour during the tween: gains lerp `bonus` → normal over the last 30 %; losses `chip.red` →
  normal. Golden Hour: normal = `gold`, otherwise `bone`.
- Digit roll (NICE): each changing digit slides 4 px vertically with clipping (odometer) instead of
  just changing; implemented per character with `enableScissor`.
- Chip glyph U+E100 replaces the 0.5×-scaled item icon (crisp at every GUI scale). On gain it does a
  1-cycle 8 px → 9 px → 8 px pop (`outBack`, 200 ms); reduced motion: none.
- `chip_count` tick sound only when the HUD is visible and no screen is open and |Δ| ≥ 100, at most 6
  ticks per tween, volume 0.25.

**Floating delta (MUST).** Replaces the inline delta: `+120` (`bonus`) / `−50` (`chip.red`) spawns at
the right end of the balance row and rises 10 px over 1 400 ms `outCubic`, alpha 1 → 0 over the last
500 ms. Deltas within 400 ms merge into one label (sum; label re-pops 1.15× for 120 ms). Max 3 labels
stacked (older ones pushed up 9 px). Reduced motion: the label stays in place and fades.

**Streak meter (MUST).** 10 pips glyph U+E172/E173/E174, 1 px gap; lucky pips fill gold from the
left, unlucky fill `cool` from the left, followed by flame/cloud glyph and `×S` (existing keys).
- +1: the new pip scales 0 → 1.3 → 1 (`outBack`, 250 ms) + `streak_up` (pitch 0.8 + 0.08·S),
  volume 0.4.
- Streak broken (sign change or reset to 0): pips drain right-to-left 40 ms each, each emits a
  2-frame puff (gray), `streak_break`.
- |S| ≥ 7: **no blinking**; the flame glyph animates (3-frame sprite `hud/flame_*.png` at 8 fps),
  or the cloud drips (3 frames); pips shimmer (1 px highlight travelling left→right every 1.6 s).
  Flashes off: static glyph, shimmer off.
- |S| = 10: panel border tints gold (lucky) / `cool` (unlucky) while at 10.
- S = 0: row hidden (as today) with 150 ms height collapse (reduced motion: instant).

**VIP badge (MUST).** 8×8 badge glyph U+E180+tier before the tier name (right column). Platinum+:
a 4-frame shine sweep (`hud/badge_shine_*.png` overlay, 60 ms/frame) every 8 s. Netherite: 1 ember
pixel rising 3 px every 2 s (NICE).

**Loan (MUST).** Bell glyph U+E176; in default the **bell swings** (±15° rotation, 1 Hz `sin`) and
the text is steady `chip.red`; the current whole-line blink is removed. Flashes off or reduce motion: static
red bell. Last 10 % of the deadline: text `gold`.

**Golden Hour (MUST).** Row with sun glyph U+E175 rotating 45° steps at 4 fps (Java draws the glyph
rotated via pose; frames not needed). Panel switches to `panel/hud_golden.png` with border alpha
pulsing 60 %↔100 % at 0.5 Hz (flashes off: static 100 %). Last 30 s: timer text `chip.light` and
the row scales 1.0↔1.06 at 1 Hz (reduced motion: colour only). Start/end transitions §4.5.
The timer displays `max(0, serverTicks − ticksSincePacket)` and **stays at 00:00** until the
server's GOLDEN_HOUR_END (never ends early on the client).

**Panel show/hide (NICE).** Casino mode on / HUD toggle: slide 12 px from the anchored edge + alpha,
200 ms `outCubic`. Chat open dims to 50 % (existing) with a 120 ms fade.

**Collectors row (MUST, loan module segment order 210).** While a squad hunts the player: fist glyph
U+E177 + `hud.burmaldaholic.collectors` ("Collectors: 23 m", distance to nearest member, updated
every 10 t from a new field in the loan sync payload). Text `chip.red`; the glyph shakes ±1 px at
2 Hz when < 12 m (reduced motion: no shake).

#### 4.1.2 Bedrock HUD

**MUST — JSON UI panel (UI.md §1 primary implementation).** Files:
`packs/core/RP/ui/_ui_defs.json`, `packs/core/RP/ui/hud_screen.json` (modifications: add
`burmaldaholic_hud@burmaldaholic_hud.root` to `root_panel`, hide real titles starting with the
sentinel), `packs/core/RP/ui/burmaldaholic_hud.json` (panel, labels, anims).
Protocol: script sends `setTitle('§b§m§h' + payload, {fadeIn:0, stay: 2^31-1, fadeOut:0})`;
payload = up to 5 lines separated by `\n`, each prefixed with a one-character line code:
`b` balance, `d` delta, `s` streak, `v` VIP, `l` loan, `g` Golden Hour, `c` collectors.
JSON UI binds `#hud_title_text_string`, splits by sentinel/line codes with string-slicing bindings
(`$line_b` etc.) and shows the matching label controls.

Animations inside JSON UI (no script cost):
- **Delta**: the `d` label has `anims: [delta_rise, delta_fade]` (`offset` from `[0,0]` to
  `[0,-10]` 1.4 s `out_cubic`; `alpha` 1→0 from 0.9 s). Restarted by re-binding (the script sends a
  new delta token `d<id>+120`; a changed id retriggers the anim via `play_event`).
- **Golden border**: `image` with `color` anim gold ↔ `gold.shade`, 2 s loop (flashes off: the
  script sends line code `G` instead of `g`, which selects the static variant).
- **Streak flame**: `flip_book` anim on a 3-frame `textures/burmaldaholic/ui/flame.png` (8×24) at
  8 fps; shown only when the `s` line carries `!` (|S| ≥ 7).
- **Bell swing** for loan default: 2-frame flipbook 1 Hz.

Script side (`core/hud.ts` + `core/fx.ts`): **count-up** of the balance: on a change, send
`K = clamp(round(d/100 ms), 3, 10)` frames, one per 2 ticks, values `old + ⌊Δ·outCubic(k/K)⌋`, last
frame exact. Streak pips are rendered as glyph text (U+E172–E174) in the `s` line.

**MUST — action bar fallback** (resource pack HUD hack disabled / older client): current status line
kept, plus: `§a+120` / `§c−50` appended for 3 s after a change; Golden Hour sun glyph U+E175 before
the clock; streak pips as glyphs. No count-up (would fight other action-bar users).

Settings: the `anim.hudPanel` toggle (default on) lets a player fall back to the action bar.

### 4.2 Casino Menu (Java screen / Bedrock hub)

**Java (MUST).** Re-skin to `panel/casino.png`, tabs `panel/tab*.png`, `CasinoButton`s.
- Open: panel scales 0.96 → 1.0 and fades in, 160 ms `outCubic`; close: 100 ms fade.
- Tab switch: content cross-fades (old out 80 ms, new in 120 ms, 6 px slide in the tab's
  direction); underline slides between tabs 150 ms `inOutQuad`.
- Wallet tab: balance ticker (same component as HUD at 2×); **VIP progress bar**: fills from 0 to
  the current fraction on open, 600 ms `outCubic`, with a 1 px `bone` highlight travelling along the
  filled part every 3 s; next tier badge (8×8 → 16×16 at 2×) at the bar end, greyed until reached.
- Contracts: completed rows get a gold check stamp (sprite `menu/stamp.png` 16×16) that scales
  1.6 → 1.0 `outBack` 250 ms when the tab opens right after completion (server flag `fresh`);
  progress bars animate like the VIP bar; Reroll: row slides out left 150 ms, new row in from right.
- Errors: shake + `ui_deny` (§2.2).
- Reduced motion: no scale/slide; progress bars drawn at final value.

**Bedrock (MUST).** Forms: keep; new 32×32 hub icons (Wallet chip stack, Contracts scroll, Loan
bell, Achievements trophy, Challenges dice, My Casino key, Rules book, Settings cog, Admin crown) in
palette (§5.2). Body first line = balance with chip glyph and VIP badge glyph. After an action that
changes the balance (pay loan, reroll), close form → run the HUD count-up (§4.1.2) → re-show
the form 10 t later.

### 4.3 Cashier (chip counting and exchange)

Server additions (both editions): the cashier action result includes a **breakdown**:
`deposit {counts per denomination, total}`, `withdraw {counts per denomination, dropped}`,
`exchange {emeralds|gold ±, chips ±}`. The animation only replays this breakdown.

**Java (MUST).** Layout unchanged (flow rows), re-skinned, plus a **counting tray** (inset well,
full width × 40 px) under the title where chips animate.

Deposit storyboard (`deposit_all` with 3×500, 4×100, 2×5):
| t (ms) | Beat |
|---|---|
| 0 | tray header "Counting…" fades in; for each denomination from largest, chips fly from the player-inventory side (bottom edge of the panel, x spread) to the tray: one sprite per chip up to 10 per denomination (more → one sprite + `×N` label), 70 ms stagger, 280 ms flight on a quadratic Bézier (apex 24 px above), `outCubic` |
| on land | the sprite joins a **stack column** for its denomination (side sprite 12×3, stacked 2 px apart, max 12 visible, taller stacks show `×N`); `chip_stack` sound, pitch +0.05 per chip, max 1 sound / 60 ms |
| after last land + 150 | columns slide together to the balance well (220 ms `inCubic`), vanish with a 4-frame sparkle; balance ticker counts up (HUD rules) and the line `gui.burmaldaholic.cashier.deposited` ("Deposited 1 910") appears 1 s |
| total | ≈ 0.9–1.8 s; skip by clicking the tray |

Withdraw: reverse: the balance well emits the greedy breakdown into stack columns (largest first),
then columns drop toward the bottom edge (into the inventory), `chip_stack` per chip; if items were
dropped at the feet, the last column tips over with a red `(dropped N)` note.
Exchange: emerald (item render 16×16) slides in from left and **flips into chips** (2-frame squash
80 ms, then a burst of N mini chips into a stack column) for Buy; reverse for Sell; gold ingot for
the nether cashier. ×10 plays one flight with `×10` badge (not 10 flights).
Invalid (amount > withdrawable, inventory issues): amount field shakes + `ui_deny`; tray shows a
`chip.red` cross stamp 400 ms.
Reduced motion: tray shows the final columns instantly for 800 ms, no flights.

**Bedrock (MUST).** Forms cannot animate; sequence instead:
1. Form closes on submit.
2. `burmaldaholic.chip_stack` played once per denomination group, 3 t apart (largest first), with
   the action bar showing the growing breakdown with mini-chip glyphs:
   `⛁500 ×3 · ⛁100 ×4 · ⛁5 ×2` (glyphs U+E194–E198), one group added per step.
3. HUD balance count-up (§4.1.2).
4. Cashier form re-shown 20 t after the last step with body first line
   `gui.burmaldaholic.cashier.deposited` / `…withdrawn` in `§a`.
Particles: `burmaldaholic:chip_pop` at the cashier block top (spectators see it), 1 per group.
**NICE (after the DDUI spike, research §3.8):** a DDUI `CustomForm` cashier whose Observable label
shows the growing breakdown and balance in place (updates every 2 t), removing the close/re-open
flicker.

### 4.4 Chip items and stacks

**Java (MUST).** Item model definitions `items/chip_*.json` become `minecraft:range_dispatch` on
`minecraft:count` with thresholds 1, 2, 8, 32 → models `chip_X` (single chip, existing),
`chip_X_few` (2 chips offset), `chip_X_stack` (short stack), `chip_X_tower` (tall stack + one leaning
chip). 5 × 3 new 16×16 textures, same palette per denomination (white/red/green/black/purple,
GAME_DESIGN §3.1), 1 px `ink` outline, top ellipse + side stripes (edge inserts in `bone`).
**NICE:** dropped chip item entities glint: a client `chip_glint` particle every 60–100 t per chip
item entity within 12 blocks (≤ 8 per player view). No enchantment glint on any chip (it would read
as "enchanted").

**Bedrock (NICE).** No count-based item textures on Bedrock; keep single textures, align the 5
PNGs to the Java redraw (same generator).

### 4.5 Golden Hour start / end

Trigger: server start/end (both editions); timer and multiplier from server config.

**Java start storyboard (MUST)** (all online players; `FxPayload GOLDEN_HOUR_START`):
| t (ms) | Beat |
|---|---|
| 0 | `golden_hour` bell (existing) + `block.beacon.activate` layer; screen-edge **golden vignette** fades in 0 → 30 % over 600 ms (`textures/gui/fx/vignette_gold.png` 256×256, stretched) |
| 200 | vanilla title "GOLDEN HOUR" (existing, gold) + subtitle multiplier/duration (existing) |
| 300–2 300 | 24 `golden_mote` particles rise around each player (radius 3, client-spawned, 1.2 blocks/s) |
| 600–3 000 | HUD row slides in (§4.1.1), panel border turns gold |
| 3 000 | vignette settles to 12 % for the whole Golden Hour (NICE; flashes off: 0 %) |
Boss bar stays (visible to vanilla-HUD users, same info). Players within 32 blocks of the trigger
(jackpot winner) additionally see a `gold_burst` at the winner.

**Java end (MUST):** at `FxPayload GOLDEN_HOUR_END`: `golden_hour_end` sound, vignette fades to 0
over 1 000 ms, HUD row shrinks 200 ms, action bar `hud.burmaldaholic.golden_hour.over`; chat lines
unchanged. "Ending in" warning (existing chat) also pulses the HUD row once (scale 1.1, 300 ms).

**Bedrock start (MUST):** add the missing title (`msg.burmaldaholic.chaos.golden_hour.title` gold,
subtitle existing, 10/60/20 t — same as Java); bell (existing) + `burmaldaholic.golden_hour`;
camera fade gold `#FFD640` (fadeIn 0.3 s, hold 0.2 s, fadeOut 0.8 s) at 40 % — skipped when reduced
motion is set or flashes are off (A1); particle `burmaldaholic:golden_mote` emitter at each player (one call, the
emitter spawns 24 over 2 s). HUD panel variant `G/g` (§4.1.2). End: `burmaldaholic.golden_hour_end`,
action bar `…golden_hour.over`, HUD row removed.

Faithfulness: the start FX fires from the server event only; no client-side prediction of the end.

### 4.6 Chaos events

Common structure (both editions): the server performs the effect **first** (items spawned, effect
applied, mobs spawned, teleport done), then sends the FX with exact data (item spawn points, mob
spawn points, teleport origin/target). Every event gets a **kind colour and sound** so the player
knows good/bad/neutral before reading:

| Kind | Colour | Sound | Title style |
|---|---|---|---|
| good | `bonus` / `gold` | `chaos_good` | title in `bonus`, 5/50/15 |
| bad | `chip.red` / `curse` | `chaos_bad` | title in `chip.red`; Java GUI/world shake 3 px 300 ms; Bedrock `camerashake add @s 0.2 0.3 positional` (reduce motion: none) |
| neutral | `lilac` | `chaos_teleport` or weather thunder | title in `lilac` |

Java draws a **chaos card** under the vanilla title (NICE): 64×64 icon sprite per event (§5.1)
flipping in (y-axis squash 0 → 1, 250 ms `outBack`), held for the title duration.

Per event (Java MUST = custom particles + sounds + correct colours; Bedrock MUST = custom particle
ids + sounds + titles):

| Event | Storyboard | Spectators (within 16 blocks) |
|---|---|---|
| `chip_shower` | 0 ms `chaos_good`; at each spawned chip pile position a `chip_pop` (chip sprite pops up 0.6 blocks and falls, 4 frames spin) — exactly one per pile; 150 ms later the items are visible (they are the real items). Title. | see the pops and items |
| `lucky_buff` | `sparkle` ring (16 particles) rising around the player 1 s, tinted `bonus`; `chaos_good`; title with effect name (existing subtitle). Big-win source: the ring is gold. | ring |
| `curse` | `curse_wisp` (purple-green, 3-frame) spiral descending onto the player 1.2 s; `chaos_bad`; Java: 300 ms shake + 400 ms green-purple vignette at 30 % (flashes off: static 10 %; reduce motion: no shake) | spiral |
| `diamond_rain` | For each diamond drop point: a `diamond_glint` column (cyan sparkle, **not** a diamond sprite, so it never looks like extra loot) falls from +6 blocks to the drop point in 400 ms, the real item spawns on arrival; 8 extra ambient glints in radius 3 over 2 s; `chaos_good` + `block.amethyst_block.chime` per diamond (pitch rising). | columns + items |
| `xp_fountain` | Existing orbs (real) + `sparkle` tinted green at the fountain base per burst; `chaos_good`. | yes |
| `mob_wave` | Server first picks the spawn spots; **900 ms before** each mob appears, a `summon_rune` ground decal particle (8×8 ×4 frames, dark red circle, lies flat: Java custom particle with fixed orientation; Bedrock `minecraft:particle_appearance_billboard` with `facing_camera_mode: "direction_y"`) grows at the spot, then `POOF` + mob. Title + `chaos_bad` at 0 ms; `evoker.prepare_summon` (existing) at the rune start. Mobs spawn exactly where the runes were. | runes + mobs |
| `random_teleport` | Origin: `portal` swirl (existing) + `teleport_ring` (lilac ring expanding 0.5 → 2 blocks, 300 ms); Java: 250 ms white-lilac full-screen fade out/in around the teleport tick (flashes off / reduce motion: none; Bedrock camera fade lilac 0.1/0.1/0.3 s, A1); target: ring collapsing 2 → 0.5; title with distance (existing). | ring at both ends |
| `weather_change` | Title (existing) + `lilac` kind; no extra FX (the weather is the effect). | — |
| `golden_hour` | §4.5 | §4.5 |

Deferred events (casino screen/form open, GAME_DESIGN §13.4): FX play when the event actually runs.

### 4.7 Big-win and jackpot announcements

Server: `WinTier` (§2.4) computed at settlement (both editions). Java must **implement the big-win
broadcast** that its config already exposes (`core.announceBigWins`, `core.bigWinThreshold`) with
the existing key `msg.burmaldaholic.core.big_win` (Bedrock already does).

| Tier | Winner | Players within 32 blocks | Server-wide |
|---|---|---|---|
| BIG / MEGA | §2.6 overlay | `chip_fountain` at the winner's table/machine, `win_big` at 0.5 volume (positional) | — |
| EPIC | §2.6 | `chip_fountain` + `gold_burst`; Java NICE: a temporary text display "EPIC WIN +N" (translatable, so each client shows its language) floats 2.2 blocks above the winner, rises 0.5 blocks over 3 s, then removed | chat big-win line (existing) with chip glyph prepended; Java: casino **toast** (§4.10) for players with `Win celebrations = Everyone's` |
| JACKPOT | §2.6 | `gold_burst` + 3 firework-star particles (vanilla `firework` particle, no rockets, no damage) + `jackpot` positional | chat jackpot broadcast (existing slots key) + toast (Java) / action bar `toast.burmaldaholic.jackpot.*` 80 t (Bedrock) |

Rate limit: server-wide toasts ≤ 1 per 10 s (queued ≤ 3, then chat only). PvP results use the PvP
broadcast (PVP.md §3.11.5) with the same presentation classes.

### 4.8 Last Chance coin flip

The server decides heads/tails before any animation (both editions; Java already sends
`CoinFlipPayload(heads, highStakes)`). On heads the player is already revived and protected
(Resistance V 60 t), so the overlay must be short and must not hide the world.

**Java storyboard (MUST; rework of `CoinFlipOverlay`)**:
| t (ms) | Beat |
|---|---|
| 0 | red-black edge **vignette** 0 → 45 % (300 ms, slower than the 250 ms flash rule), world desaturates via overlay tint `#140822` 25 %; `heartbeat` ×2 (0, 350 ms; pitch 1.0, 1.12) |
| 0–150 | title "Last Chance…" fades in at 2× (top fifth, existing key) |
| 150 | coin (32×32, 12-frame sheet `gui/lastchance/coin_spin.png` 32×384: heads, 5 tilt frames, edge, 5 tilt frames back = tails side) launches up 18 px (`outCubic` 400 ms) spinning at 14 frames/s |
| 550–1 300 | coin falls back (`inCubic`), spin decelerates `outCubic` to land on the payload face at 1 300 ms; last 3 frames are the approach to **the payload face only** (no flip-past-and-back) |
| 1 300 | `coin_land`; heads: burst of 20 gold `sparkle`s from the coin (GUI particles) + server plays `last_chance` (moved from the instant of the flip to +1 300 ms via a 26 t server delay; particles `TOTEM_OF_UNDYING` also at +1 300 ms) ; vignette cross-fades to gold 20 % and out over 600 ms. Tails: coin cracks (2 frames `coin_crack_*.png`), vignette to red 45 % over 300 ms, `lose` p0.6 |
| 1 300–1 450 | result title HEADS!/TAILS… scales 1.3 → 1 `outBack`; subtitle (existing keys; high stakes red line) |
| 2 800 | fade out 500 ms |
| total | 3.3 s (fits the 3 s Resistance window + 0.3 s margin) |
Tails: the death screen opens underneath; the overlay stays on top until done (as today).
Reduced motion: no launch/vignette; coin shows face frames 0/6/11 swapping at 100 ms for 600 ms then
the result. Flashes off: vignette max 25 %, no colour change on landing.

**Bedrock (MUST — currently missing).** Sequence from the moment of the flip (server already knows):
| t (ticks) | Beat |
|---|---|
| 0 | camera fade black `#140822` fadeIn 0.1 s hold 0.2 s fadeOut 0.5 s (A1; else skip); `burmaldaholic.heartbeat`; title `§f` + coin glyph U+E186 (title = glyph only, subtitle = `msg.burmaldaholic.lastchance.flip_title`, 0/40/0) |
| 2, 4, 6 … 22 | title re-sent with the next coin frame glyph U+E186→E187→E188→E189→… (12 updates, 2 t apart; the sequence is fixed, not random, and its **last frame is the payload face**; intervals widen to 3 t for the last 4 frames = deceleration) |
| 26 | `burmaldaholic.coin_land`; title → U+E18A (heads) or U+E18B (tails) at 2× by title size + result title/subtitle (existing keys) 0/50/15; heads: `burmaldaholic.last_chance` + `minecraft:totem_particle` (moved from t=0) + `burmaldaholic:sparkle` gold ring |
| 26 | action bar `msg.burmaldaholic.lastchance.flip_title` removed |
The revive itself (health, effects) stays at t=0 exactly as today (gameplay unchanged); only the
presentation is spread over 1.3 s. Reduced motion: frames 0, 24 only. Tails on Bedrock: the player
is dead at t=0; titles are shown on the death screen (Bedrock renders titles over it; if not (A2),
chat line only, which already exists).

Spectators within 16 blocks: heads → gold `sparkle` burst + totem particles at +1 300 ms; broadcast
chat (existing).

### 4.9 Loan Shark and Debt Collector arrival

**Loan Shark screen (Java MUST).** Portrait (existing entity texture face, 32×32 crop rendered at 2×)
slides in from the left 250 ms `outCubic`; greeting line types in at 40 chars/s (click completes);
`loan_shark_idle` hum on open (subtitle exists; composition: `entity.villager.ambient` p0.7 v0.5).
Take loan: confirm dialog; on accept the loan amount runs through the HUD ticker; a `ledger` stamp
(sprite 32×16, red "seal" shape, no text) thumps onto the loan row (scale 1.5 → 1 `outBack` 200 ms,
`block.anvil.land` v0.2 p1.8).
**Bedrock:** forms; on accept: `burmaldaholic.chip_stack` + count-up.

**Collector arrival (MUST both editions)** — storyboard from the server's "wave incoming" moment:
| t (ms) | Beat |
|---|---|
| 0 | `collector_knock` ×3 (0/280/520 ms) positional at the player; title "Knock knock" (existing, red) |
| 0–1 500 | Java: red vignette pulse ×2 (0.7 Hz, max 30 %; flashes off: single static 20 % for 1 s). Bedrock: camera fade `#8C1834` 0.1/0.1/0.4 s once (A1) |
| at spawn of each member | `arrival` column at the member: 10 `collector_smoke` particles (dark purple-gray, 4 frames) rising 2 blocks + `POOF`; `collector_arrive` once per squad |
| from spawn | HUD collectors row (§4.1) with distance; leader chat line (existing) |
| squad defeated / paid | HUD row fades 300 ms; `win_small` p0.8 |
Spectators: see the smoke columns and hear the knock (positional, 16 blocks).

### 4.10 Achievements and toasts

**Java.** Advancements keep vanilla toasts (tab background already on brand). New
**`CasinoToast implements Toast`** (160×32, `toast/casino.png` background: `bg.deep`, gold 1 px
frame, 24×24 icon well) for: contract complete, cashback paid, VIP tier-up (small), big win nearby,
jackpot on the server, Golden Hour "ending in". Icon: an item or a 16×16 GUI sprite. Motion:
vanilla toast slide (the `ToastManager` animates). Sound `toast` (once, not the vanilla toast sound).
Max visible: vanilla's 5 slots; casino toasts queue.

**Bedrock (MUST).** Achievement unlock: JSON UI toast via a 2nd sentinel `§b§m§t` on the **subtitle**
(A2): 160×32 panel `textures/burmaldaholic/ui/toast.png` slides in from the top-right (`offset`
anim 250 ms `out_cubic`), holds 3 s, slides out 250 ms; icon = trophy 16×16; text = achievement
title. Fallback (current): action bar + chat + `random.toast` (keep, plus `burmaldaholic.toast`).

### 4.11 VIP tier-up

**Java (MUST).** `FxPayload VIP_UP(tier)` → overlay (replaces the vanilla title when the payload
can be sent):
| t (ms) | Beat |
|---|---|
| 0 | backdrop dim 30 %; tier-coloured rays (§5.1 `fx/rays.png` tinted) fade in, rotating 12°/s |
| 100 | large badge 48×48 (`vip/badge_large_<tier>.png`) scales 0 → 1.15 → 1 (`outBack` 350 ms) at screen centre-top; `vip_tier_up` arpeggio (bell p1.0/1.26/1.5/2.0, 90 ms apart) |
| 450 | shine sweep across the badge (8 frames 48×48 overlay, 40 ms each) |
| 500 | title line "VIP tier up!" (2×) + tier name in tier colour; line 2 "Max bet now %s" (existing `gui.burmaldaholic.vip.max_bet`) fades in |
| 2 200 | everything fades 400 ms; a small `CasinoToast` remains 5 s with the badge |
Multi-tier jumps: one overlay for the final tier; earlier badges flick past (100 ms each) before it.
Netherite: rays are ember orange with 20 rising ember GUI particles; server-wide chat (existing) + a
toast to everyone (`toast.burmaldaholic.vip_netherite`).
World (spectators 16 blocks): existing promo particles upgraded to `sparkle` tinted in tier colour,
ring of 24 rising 1.5 s.

**Bedrock (MUST).** Title `§l` + badge glyph U+E180+tier + tier name (existing key
`msg.burmaldaholic.vip.promoted_title`), subtitle max bet (as Java), 10/60/20; `burmaldaholic.vip_tier_up`;
particle `burmaldaholic:vip_ring` with `variable.tier_r/g/b` set from the tier colour.

### 4.12 Bot presence indicators

**Java (MUST, on the bots branch).** Nameplate text display (existing): add
- **Thinking**: while the bot's decision timer runs, the plate text gets the thinking-dots glyph
  cycling U+E191→E192→E193 every 10 t (server updates text; ≤ 1 text update per 10 t per bot, only
  while a player is within the spectator radius). Identical for every decision (never correlated
  with the hand; BOTS.md's EASY tell is the delay only).
- **Acted**: the plate's text display `interpolation_duration` 4 t + `transformation` scale
  1.0 → 1.15 → 1.0 (two transformation updates 4 t apart) when the bot acts.
- **Join/leave**: scale 0 → 1 over 6 t (interpolated display transformation) with `note` / `smoke`
  emote particles (existing BOTS.md rule).
- Plate background: `bg.darkest` 25 % (`0x40140822`) instead of plain black; text shadow on.
**Bedrock (MUST).** No nameplates (BOTS.md). Action bar/forms: bot names prefixed with U+E190;
"Thinking…" (`gui.burmaldaholic.bots.thinking`, exists) followed by the dots glyph cycling in the
spectator action bar line, updated each 10 t while the table's action-bar channel is live.
Emote particles above the table centre (existing rule), using `burmaldaholic:emote_*` NICE sprites
(happy chip, angry cloud) instead of villager particles.

### 4.13 Table idle / attract modes (world casinos and crafted tables)

Goal: a casino looks alive when you walk in, without fake results.

**Java (MUST).**
- **Animated block textures** (`.png.mcmeta` animation, zero code):
  slot machine fronts (copper/gold/netherite) — marquee bulbs chase: 4 frames, `frametime` 4
  (copper), 3 (gold), 2 + `interpolate: true` glow (netherite); the reel windows stay static (no
  symbols spinning, so no fake outcome). Cashier front: brass lamp flicker 2 frames, frametime 30/4
  (irregular via `frames` list). Wheel of Fortune front: rim bulbs alternate 2 frames, frametime 8.
  Plinko front: pegs twinkle 3 frames. Roulette top: none (a spinning wheel would imply a spin).
- **Ambient particles** via `Block#animateTick` (client only, free on the server): card tables 1 in
  40 per tick-roll → a `chip_glint` above the felt; slot machines 1 in 30 → `sparkle` above the
  marquee. Suppressed while the client has a table-state cache entry for that block showing a
  round in progress (`ClientTableCache`); otherwise always on. Budget §2.9.
- **Attract chime**: slot machines idle ≥ 60 s with a player within 8 blocks play `attract_chime`
  once (then not again for 120 s per machine). Client-side timer in the block's `animateTick` +
  a client map; never with a round running.
- **Dealer idle** (NICE): dealer NPCs play a "shuffle" arm swing every 8–14 s (vanilla villager model
  arm pose via the existing renderer's `setupAnim` override) and look at the nearest player within
  6 blocks.
**Bedrock (MUST).**
- Block flipbook textures: `packs/<module>/RP/textures/flipbook_textures.json` entries for the same
  fronts (`ticks_per_frame` 4/3/2, `blend_frames: true` on netherite), same PNG strips as Java (one
  generator).
- Ambient particles: script loop every 40 t over tables in loaded chunks with a player within 16
  blocks (≤ 8 tables per dimension per pass): 1 in 3 chance → `burmaldaholic:chip_glint` /
  `sparkle` above the block. `attract_chime` rule as Java via `dimension.playSound` at the machine.
- Dealer idle (NICE): `animation.burmaldaholic.dealer.shuffle` in the dealer entity RP
  (`animations/dealer.animation.json`, 1.2 s arm swing) triggered by an animation controller on a
  `q.life_time`-based random timer.

### 4.14 Casino screens entrance (shared, Java)

`CasinoTableScreen` (all games, MUST): open = panel fades + scales 0.97 → 1 in 150 ms; felt
nine-slice; result banners use the celebration kit (§2.6). Close unchanged (instant, vanilla).

---

## 5. Assets to create

All PNGs are generated by **one Python script** `scripts/gen-fx-assets.py` (Pillow; palette from
§2.1; deterministic; writes Java paths under `java/src/main/resources/assets/burmaldaholic/textures/`
and Bedrock paths under `bedrock/packs/<module>/RP/textures/`), hand-tuned pixel data in the script as
string grids like `docs/branding/gen_logo.py`. No text is baked into any texture.

### 5.1 Java textures (`textures/gui/sprites/burmaldaholic/…` unless noted)

| # | Asset | Size | Frames | Notes |
|---|---|---|---|---|
| 1–6 | `panel/casino`, `panel/felt`, `panel/inset`, `panel/hud`, `panel/hud_golden`, `panel/tab`+`tab_selected` | 32×32 / 16×16 / 32×16 | 1 | nine-slice `.mcmeta` (7 files) |
| 7–15 | `widget/casino_button`, `_highlighted`, `_disabled`; `_primary` ×3; `_danger` ×3 | 200×20 | 1 | nine-slice border 3 (9 files) |
| 16 | `hud/flame` | 8×8 | 3 (separate files `flame_0..2`) | streak ≥ 7 |
| 17 | `hud/cloud` | 8×8 | 3 | drip |
| 18 | `hud/badge_shine` | 8×8 | 4 | overlay |
| 19 | `vip/badge_large_<tier>` | 48×48 | 6 files | tier-up |
| 20 | `vip/badge_shine` | 48×48 | 8 | overlay sweep |
| 21 | `fx/rays` | 64×64 | 1 | white, tinted in code |
| 22 | `fx/coin_spin` (GUI) | 16×16 | 8 | coin shower (JACKPOT) |
| 23 | `fx/confetti` | 4×4 | 6 colours × 2 frames (12 files or one 24×8 texture blitted by UV) | |
| 24 | `fx/sparkle` | 7×7 | 4 | GUI sparkle |
| 25 | `fx/chip_<denom>` | 8×8 | 5 files | flying chips |
| 26 | `fx/chip_side_<denom>` | 12×3 | 5 files | stack columns |
| 27 | `menu/stamp` | 16×16 | 1 | contract check |
| 28 | `loan/seal` | 32×16 | 1 | loan stamp |
| 29 | `toast/casino` | 160×32 | 1 | toast background |
| 30 | `textures/gui/fx/vignette_gold.png`, `vignette_red.png`, `vignette_curse.png` | 256×256 | 1 each | radial alpha, blitted stretched |
| 31 | `textures/gui/lastchance/coin_spin.png` | 32×384 | 12 | replaces heads/tails pair (kept for fallback) |
| 32 | `textures/gui/lastchance/coin_crack_0/1.png` | 32×32 | 2 | tails |
| 33 | chaos card icons `fx/chaos_<event>` | 64×64 | 8 files | NICE |
| 34 | `textures/font/glyph_e1.png` | 256×256 | — | shared sheet incl. new code points §2.3 |
| 35 | Item stacks `textures/item/core/chip_<d>_few/_stack/_tower.png` | 16×16 | 15 files | §4.4 |
| 36 | Block anim strips: `block/slots/slot_machine_<t>_front.png` (16×64), `block/core/cashier_front.png` (16×32), `block/extras/wheel_of_fortune_front.png` (16×32), `plinko_machine_front.png` (16×48) | — | 4/2/2/3 | + `.mcmeta` |
| 37 | Particles `textures/particle/burmaldaholic/<name>_<n>.png` | 8×8 | chip_pop 4, chip_glint 4, sparkle 4, gold_burst 4, golden_mote 2, diamond_glint 4, curse_wisp 3, summon_rune 4, teleport_ring 4, collector_smoke 4 | 37 files + `particles/<name>.json` (10) |

### 5.2 Bedrock textures / files

| Asset | Path | Notes |
|---|---|---|
| Glyph sheet (extended) | `packs/core/RP/font/glyph_E1.png` | via S1 (was `gen-glyphs.mjs`; either port it or have both generators read one JSON pixel table) |
| JSON UI | `packs/core/RP/ui/_ui_defs.json`, `ui/hud_screen.json`, `ui/burmaldaholic_hud.json`, `ui/burmaldaholic_toast.json` | §4.1.2, §4.10 |
| UI textures | `packs/core/RP/textures/burmaldaholic/ui/{hud_panel, hud_panel_golden, flame(8×24), bell(8×16), toast, trophy}.png` + `.json` nine-slice (`nineslice_size`) | |
| Form icons | `packs/core/RP/textures/burmaldaholic/icons/{wallet, contracts, loan, achievements, challenges, my_casino, rules, settings, admin, deposit, withdraw, buy, sell, shop}.png` | 32×32, 14 files |
| Particle atlas | `packs/core/RP/textures/particle/burmaldaholic_fx.png` 128×128 | all 10 particle sprite sets as flipbook rows |
| Particle definitions | `packs/core/RP/particles/{chip_pop, chip_fountain, chip_glint, sparkle, gold_burst, golden_mote, diamond_glint, curse_wisp, summon_rune, teleport_ring, collector_smoke, vip_ring}.json` | 12 files; `chip_fountain`, `gold_burst`, `vip_ring`, `golden_mote` are emitters spawning many particles from one call |
| Sound definitions | `packs/core/RP/sounds/sound_definitions.json` (+ chaos, loan, vip, lastchance, worldgen files) | §2.7 ids, vanilla sound paths |
| Block flipbooks | `packs/{slots,core,extras}/RP/textures/flipbook_textures.json` + strips from S1 | §4.13 |
| Dealer idle (NICE) | `packs/{blackjack,baccarat,uth}/RP/animations/dealer.animation.json`, `animation_controllers/dealer.ac.json` | shared animation id |

### 5.3 Asset count

Java: 44 GUI/HUD/FX PNGs + 15 item PNGs + 4 animated block strips + 37 particle PNGs + 1 glyph sheet
≈ **101 PNGs**, 18 `.mcmeta`, 10 particle JSONs, 15 item model/definition JSONs, ~22 new sound
events. Bedrock: 1 glyph sheet + 6 UI + 14 icons + 1 particle atlas + 4 block strips = **26 PNGs**,
4 JSON UI files, 12 particle JSONs, 3 flipbook JSONs, 6 sound-definition files (NICE: 2 animation
files). All generated by one script (S1) except JSON UI/particle JSON (hand-authored).

---

## 6. Faithfulness checklist (review gate for every task)

1. Animation starts only after the server result/effect exists (payload or script state after the
   mutation). No client RNG decides anything visible as an outcome.
2. Final frame of every count-up = exact server number; tweens are monotonic.
3. Coin flip frames land on the payload face with no past-the-face bounce.
4. Diamond-rain glints and chip pops appear only at real spawn points and are one per real drop;
   glints never use item sprites.
5. Mob runes appear only where mobs will spawn; teleport rings only at the real origin/target.
6. Golden Hour/loan/collector timers come from server ticks; they may pause at 0 but never end early.
7. Attract mode never shows reel symbols, numbers or "win" words.
8. Bot thinking visuals are identical for every decision.
9. Skipping any animation shows the same final state.

---

## 7. Developer task breakdown

Legend: **S** shared, **J** Java, **B** Bedrock. MUST unless marked NICE. Tasks in the same
column letter can run in parallel after their dependencies.

### 7.1 Shared

| Id | Task | Files | Depends |
|---|---|---|---|
| S1 | Asset generator: palette, all PNGs of §5.1/§5.2 (incl. extended glyph sheet, chip stacks, block strips, particle sprites/atlas, icons), writes both editions | `scripts/gen-fx-assets.py` (+ port or share pixel tables with `bedrock/tools/gen-glyphs.mjs`) | — |
| S2 | Strings: add §9 keys to `docs/design/STRINGS.md` sections and lang files (both editions) | STRINGS.md, `java/src/main/lang/*`, `bedrock/lang/*` | — |
| S3 | `WinTier` pure logic + tests (both editions) | Java `core/logic/WinTier.java`; Bedrock `core/logic/win-tier.ts` | — |
| S4 (NICE) | Synthesised OGG sounds (numpy + `oggenc`) replacing vanilla composites for `win_*`, `jackpot`, `vip_tier_up`, `coin_land`, `attract_chime` | `scripts/gen-sounds.py` | S-sound ids from J2/B2 |

### 7.2 Java

| Id | Task | Files | Depends |
|---|---|---|---|
| J1 | FX core: `Ease`, `Tween`, `CasinoPalette`, `FxText`, `FxSettings` (client json + Casino Menu Settings rows + Mod Menu section), GUI particle pool | `client/fx/*` | — |
| J2 | Sounds: register §2.7 ids, re-point `win`/`lose`, Golden Hour bell to AMBIENT | `src/main/sounds/*/sounds.json`, `core/CoreSounds.java`, `chaos/GoldenHour.java` | — |
| J3 | Glyph font provider + shared sheet | `assets/burmaldaholic/font/default.json` | S1 |
| J4 | Style kit: nine-slice panels, `CasinoButton` (hover/press/invalid), apply to `CasinoTableScreen`, `CashierScreen`, `CasinoMenuScreen`, `LoanScreen`, `NegotiationScreen`, `CharterScreen` | `client/fx/CasinoButton.java`, screens | J1, S1 |
| J5 | `FxPayload` + server senders (fallback to vanilla titles) | `core/network/FxPayload.java`, `core/fx/ServerFx.java` | S3 |
| J6 | HUD rework: ticker, floating deltas, streak meter, VIP badge, loan bell, Golden Hour row/border, collectors row, show/hide | `client/hud/CasinoHud.java`, `HudLine` (add icon glyph + anim hooks), `loan` sync field | J1, J3 |
| J7 | `CelebrationOverlay` + tier presentation (§2.6) + skip | `client/fx/CelebrationOverlay.java` | J1, J2, J5 |
| J8 | Big-win broadcast (implement `announceBigWins`), nearby FX, jackpot toast, NICE text-display float | `core/…/wager settle`, `client/fx/CasinoToast.java` | J5, J7 |
| J9 | Cashier counting tray (deposit/withdraw/exchange) + server breakdown in action result | `client/cashier/CashierScreen.java`, `CashierTray.java`, core cashier handler | J1, J4, S1 |
| J10 | Casino Menu motion (open, tabs, progress bars, stamps) | `vip/client/CasinoMenuScreen.java` | J4 |
| J11 | Chaos FX: custom particle types + factories, per-event client FX, kind colours, shake/vignette, server sends spawn points | `chaos/ChaosEffects.java`, `chaos/client/ChaosClientModule.java`, `chaos/client/ChaosFx.java`, `particles/*.json` | J1, J2, J5, S1 |
| J12 | Golden Hour start/end FX + HUD tie-in | `chaos/GoldenHour.java`, `chaos/client/GoldenHourFx.java` | J6, J11 |
| J13 | Last Chance rework (storyboard §4.8, server sound/particles delayed 26 t on heads) | `lastchance/client/CoinFlipOverlay.java`, `lastchance/LastChance.java` | J1, J2, S1 |
| J14 | VIP tier-up overlay + toast + world ring | `vip/client/VipTierUpOverlay.java`, `vip/VipService.java` | J5, J7 |
| J15 | Loan Shark screen motion + collector arrival FX | `loan/client/LoanScreen.java`, `loan/LoanSquads.java`, `loan/client/CollectorFx.java` | J2, J6, J11 (particles) |
| J16 | Casino toasts (contracts, cashback, VIP, big win, jackpot) | `client/fx/CasinoToast.java`, vip/contracts senders | J5 |
| J17 | Bot nameplate thinking/act/join animation | bots branch `bots/BotAvatars.java` | S1 (glyphs) |
| J18 | Chip item stack models | `items/chip_*.json`, `models/item/*` | S1 |
| J19 | Attract mode: animated block textures + `animateTick` particles + attract chime | block classes (`animateTick`), `.mcmeta` | S1, J2, J11 |
| J20 (NICE) | Digit odometer, chaos cards, dealer idle, Netherite ember badge | — | J6, J11 |

### 7.3 Bedrock

| Id | Task | Files | Depends |
|---|---|---|---|
| B1 | `FxService` (settings props, rate limits, celebrate, toast, count-up helper) | `src/core/fx.ts`, `core/services.ts` | S3 |
| B2 | Sound definitions for §2.7 ids | `packs/*/RP/sounds/sound_definitions.json` | — |
| B3 | Particle definitions + atlas | `packs/core/RP/particles/*.json` | S1 |
| B4 | JSON UI HUD panel + sentinel protocol + fallback line upgrades | `packs/core/RP/ui/*`, `src/core/hud.ts` | S1, B1 |
| B5 | Celebrations: wire game result flows to `fx.celebrate` (per-game specs call it) + big-win broadcast presentation | `src/core/wagers.ts`, games' result forms | B1, B2, B3 |
| B6 | Cashier/Menu feedback sequences + breakdown data + form icons | `src/core/cashier.ts`, `src/core/casino.ts` | B1, S1 |
| B7 | Chaos FX per event (§4.6) + Golden Hour title/camera/particles/end | `src/chaos/engine.ts` | B1–B3 |
| B8 | Last Chance coin sequence (§4.8) | `src/lastchance/index.ts` | B1, B2, S1 (glyphs) |
| B9 | VIP tier-up title/particles/sound | `src/vip/vip.ts` | B1–B3 |
| B10 | Collector arrival FX + HUD collectors line | `src/loan/collectors.ts` | B1–B4 |
| B11 | Achievement toast (JSON UI subtitle sentinel) + fallback | `src/core/achievements.ts`, `ui/burmaldaholic_toast.json` | B4 |
| B12 | Bot presence in action bar (glyph, thinking dots) | `src/core/bots/*` (branch `worktree-agent-aaf0f54e81d19ae4a`) | S1 |
| B13 | Attract mode: flipbooks + ambient particle loop + chime | `packs/*/RP/textures/flipbook_textures.json`, `src/worldgen/*` or `core/tables.ts` | S1, B2, B3 |
| B14 | FX settings in Casino Menu → Settings | `src/core/casino.ts` | B1, S2 |
| B15 (NICE) | Dealer idle animation, emote sprites | dealer RP files | S1 |

Parallelism: S1, S2, S3, J1, J2, B1, B2 start day one. Then J3–J6, B3–B4. Everything else fans out.

---

## 8. MUST vs NICE summary

MUST: style kit and palette (§2), win tiers + celebration kit (§2.4, §2.6), sound palette with
vanilla composites (§2.7), FX settings (§2.8), HUD ticker/delta/streak meter/badge/Golden Hour/loan
without blinking (§4.1, Bedrock JSON UI panel + fallback), Casino Menu and Cashier feedback (§4.2,
§4.3), chip stack models Java (§4.4), Golden Hour start/end (§4.5), chaos kind colours + custom
particles (§4.6), Java big-win broadcast (§4.7), Last Chance rework both editions (§4.8), collector
arrival (§4.9), toasts (§4.10), VIP tier-up (§4.11), bot thinking indicator (§4.12), attract
textures/particles (§4.13).
NICE: digit odometer, HUD show/hide slide, chaos cards, EPIC text-display float, dropped-chip
glints, Bedrock chip redraw, dealer idle animations, Netherite ember, emote sprites, synthesised
sounds (S4).

---

## 9. New strings (EN / RU)

RU checked against the 1.45× budget: HUD lines ≤ 200 px, banner words at 3× fall back to 2× when
wider than the screen − 32 (§2.2), toast body 1 line ≤ 124 px else wrapped to 2.

### core — FX settings and celebrations

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.menu.settings.reduce_motion` | Reduce motion | Меньше анимации |
| `gui.burmaldaholic.menu.settings.flashes` | Screen flashes | Вспышки экрана |
| `gui.burmaldaholic.menu.settings.anim_speed` | Animation speed | Скорость анимации |
| `gui.burmaldaholic.menu.settings.anim_speed.slow` | Slow | Медленно |
| `gui.burmaldaholic.menu.settings.anim_speed.normal` | Normal | Обычно |
| `gui.burmaldaholic.menu.settings.anim_speed.turbo` | Turbo | Турбо |
| `gui.burmaldaholic.menu.settings.celebrations` | Win celebrations | Празднование выигрышей |
| `gui.burmaldaholic.menu.settings.celebrations.all` | Everyone's | Все |
| `gui.burmaldaholic.menu.settings.celebrations.mine` | Only mine | Только мои |
| `gui.burmaldaholic.menu.settings.celebrations.off` | Off | Выкл. |
| `gui.burmaldaholic.menu.settings.fx_volume` | Effects volume: %1$s%% | Громкость эффектов: %1$s %% |
| `gui.burmaldaholic.menu.settings.hud_panel` | Styled HUD panel | Оформленная панель |
| `config.burmaldaholic.section.client_fx` | Client effects | Эффекты (клиент) |
| `gui.burmaldaholic.fx.tier.win` | WIN | ВЫИГРЫШ |
| `gui.burmaldaholic.fx.tier.big` | BIG WIN | КРУПНЫЙ ВЫИГРЫШ |
| `gui.burmaldaholic.fx.tier.mega` | MEGA WIN | МЕГАВЫИГРЫШ |
| `gui.burmaldaholic.fx.tier.epic` | EPIC WIN | ЭПИЧЕСКИЙ ВЫИГРЫШ |
| `gui.burmaldaholic.fx.returned` | Returned %1$s | Возвращено: %1$s |
| `gui.burmaldaholic.fx.tier.jackpot` | JACKPOT | ДЖЕКПОТ |
| `gui.burmaldaholic.fx.tier.push` | PUSH | НИЧЬЯ |
| `gui.burmaldaholic.fx.tier.loss` | NO WIN | БЕЗ ВЫИГРЫША |
| `gui.burmaldaholic.fx.amount` | +%1$s | +%1$s |
| `gui.burmaldaholic.fx.multiplier` | ×%1$s your bet | ×%1$s от ставки |
| `gui.burmaldaholic.fx.skip` | Click to skip | Нажмите, чтобы пропустить |
| `gui.burmaldaholic.fx.skip_bedrock` | Sneak to skip | Присядьте, чтобы пропустить |
| `gui.burmaldaholic.fx.nearby_float` | %1$s +%2$s | %1$s +%2$s |

### core — HUD

| Key | EN | RU |
|-----|----|----|
| `hud.burmaldaholic.golden_hour.over` | Golden Hour is over | «Золотой час» окончен |
| `hud.burmaldaholic.collectors` | Collectors: %1$s m | Коллекторы: %1$s м |

### core — Cashier

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.cashier.counting` | Counting… | Считаем… |
| `gui.burmaldaholic.cashier.deposited` | Deposited %1$s | Внесено: %1$s |
| `gui.burmaldaholic.cashier.withdrawn` | Withdrawn %1$s | Выдано: %1$s |
| `gui.burmaldaholic.cashier.dropped` | (%1$s dropped at your feet) | (%1$s — у ваших ног) |
| `gui.burmaldaholic.cashier.exchanged` | Exchanged: %1$s | Обмен: %1$s |

### core — Toasts

| Key | EN | RU |
|-----|----|----|
| `toast.burmaldaholic.jackpot.title` | Jackpot on the server! | Джекпот на сервере! |
| `toast.burmaldaholic.jackpot.body` | %1$s · %2$s | %1$s · %2$s |
| `toast.burmaldaholic.big_win.title` | Big win | Крупный выигрыш |
| `toast.burmaldaholic.big_win.body` | %1$s · %2$s | %1$s · %2$s |
| `toast.burmaldaholic.contract.title` | Contract complete | Заказ выполнен |
| `toast.burmaldaholic.contract.body` | Reward: %1$s | Награда: %1$s |
| `toast.burmaldaholic.cashback.title` | Cashback | Кешбэк |
| `toast.burmaldaholic.cashback.body` | %1$s returned | Возвращено: %1$s |
| `toast.burmaldaholic.vip.title` | New VIP tier | Новый уровень ВИП |
| `toast.burmaldaholic.vip.body` | %1$s · max bet %2$s | %1$s · ставка до %2$s |
| `toast.burmaldaholic.vip_netherite.title` | Netherite VIP | Незеритовый ВИП |
| `toast.burmaldaholic.golden_hour.ending` | Golden Hour ends in %1$s | «Золотой час» закончится через %1$s |
| `toast.burmaldaholic.achievement.title` | Achievement unlocked | Достижение получено |

### vip

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.vip.tier_up` | VIP tier up! | Новый уровень ВИП! |

### subtitles (new sound events)

| Key | EN | RU |
|-----|----|----|
| `subtitles.burmaldaholic.chip_stack` | Chips stack | Складываются фишки |
| `subtitles.burmaldaholic.chip_count` | Chips counted | Пересчитываются фишки |
| `subtitles.burmaldaholic.big_win` | Big win fanfare | Фанфары крупного выигрыша |
| `subtitles.burmaldaholic.mega_win` | Mega win fanfare | Фанфары мегавыигрыша |
| `subtitles.burmaldaholic.push` | Tie chime | Сигнал ничьей |
| `subtitles.burmaldaholic.ui_deny` | Action denied | Действие недоступно |
| `subtitles.burmaldaholic.toast` | Casino notice | Уведомление казино |
| `subtitles.burmaldaholic.streak_up` | Streak grows | Серия растёт |
| `subtitles.burmaldaholic.streak_break` | Streak broken | Серия прервана |
| `subtitles.burmaldaholic.vip_tier_up` | VIP fanfare | Фанфары ВИП |
| `subtitles.burmaldaholic.golden_hour_end` | Golden Hour fades | «Золотой час» угасает |
| `subtitles.burmaldaholic.chaos_good` | Lucky chime | Звон удачи |
| `subtitles.burmaldaholic.chaos_bad` | Ominous chord | Зловещий аккорд |
| `subtitles.burmaldaholic.chaos_teleport` | Teleport whoosh | Свист телепорта |
| `subtitles.burmaldaholic.collector_arrive` | Collectors arrive | Прибывают коллекторы |
| `subtitles.burmaldaholic.heartbeat` | Heartbeat | Стук сердца |
| `subtitles.burmaldaholic.coin_land` | Coin lands | Монетка падает |
| `subtitles.burmaldaholic.attract` | Machine chimes | Автомат звенит |

Existing keys reused (no change): `msg.burmaldaholic.chaos.golden_hour.title/subtitle/ending/end`,
`msg.burmaldaholic.lastchance.*`, `msg.burmaldaholic.vip.promoted_title`,
`gui.burmaldaholic.vip.max_bet`, `msg.burmaldaholic.core.big_win`,
`msg.burmaldaholic.slots.jackpot_broadcast`, `msg.burmaldaholic.loan.wave_title/wave_subtitle`,
`gui.burmaldaholic.bots.thinking`, `gui.burmaldaholic.chaos.event.*`,
`subtitles.burmaldaholic.{win, lose, jackpot, golden_hour, collector_knock, loan_shark_idle, last_chance, chip_place}`.
