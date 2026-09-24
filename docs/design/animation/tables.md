# Table-game animation spec: European Roulette, Craps, Dice Duel

Scope: presentation only, for European Roulette (GAME_DESIGN §9, UI.md §7), Craps (§10, UI.md §8) and
Dice Duel against the house and PvP (§11.5, UI.md §9, PVP.md §10.6), in both editions. Rules, odds and
payouts stay as they are. Where the animation needs data the server does not send yet, the data change
is listed under "Server data" as a dependency. Every visible outcome comes from the server's result.

Status: implementation-ready, 2026-09-24.

Inputs and alignment:
- `docs/research/animation.md` arrived after the first draft of this spec, and the spec was then
  aligned with it (§0.9 lists what the research confirms).
- The sibling spec `docs/design/animation/global.md` **owns** the shared pieces, and this file calls
  them rather than redefining them:
  - the win tiers (`WinTier`, global §2.4)
  - the celebration kit (`CelebrationOverlay` / `fx.celebrate`, global §2.6)
  - the motion vocabulary (`client/fx/Ease`, global §2.5)
  - the sound palette (global §2.7)
  - the FX settings (global §2.8)
  - the asset generator (global task S1)
- This file adds only the table-specific motion, the assets and sounds that go with it, and the server
  data it needs.

Contents
- §0 Shared rules for all three games: timeline model, easing, tiers, accessibility, faithfulness, performance, assumptions
- §1 European Roulette: audit, storyboards, assets, feedback, tiers, tasks
- §2 Craps
- §3 Dice Duel: against the house and PvP
- §4 Asset inventory with counts
- §5 Developer task breakdown, in parallel lanes per edition
- §6 New strings in STRINGS.md table format

---

## 0. Shared rules

### 0.1 Timeline model

- Every animation is a **pure function** `frame = f(outcome, seed, t)`. Input values:
  - `outcome`: the result the server already decided. For roulette this is the pocket. For craps it is
    d1, d2 and the per-bet resolutions. For the dice duel it is the dice of every round.
  - `seed`: sent by the server and derived from the table position and the round or roll counter. Every
    viewer gets the same path: the player, the other seated players and bystanders in the world.
  - `t`: milliseconds since the server event. On Java it is computed from game ticks plus `partialTick`.
    On Bedrock it is computed from `q.anim_time` or from `system.currentTick`.
- The path math lives in pure classes with identical ports and shared test vectors:
  - Java: `…/logic/*Path.java`, with tests in `src/test`.
  - Bedrock: `…/logic/*-path.ts`, with vitest tests.
  - The test vectors are a JSON file checked into each edition:
    - Java: `java/src/test/resources/fx/vectors_tables.json`
    - Bedrock: `bedrock/test/fx/vectors_tables.json`
    - The file is generated once by the Java test (`-Dfx.dumpVectors=true`) and copied to Bedrock, so
      both editions draw the same curve.
- **Late join or catch-up:** anyone who opens the screen or comes into range mid-animation jumps to the
  correct `t`, using the server's `ticks_left` or `anim_start`. If more than 85 % of the animation has
  already played, show the settled state straight away.
- **Overrun:** if a new server event arrives before the current animation has finished (for example a
  bot shooter rolling fast), play the rest of the current timeline in 250 ms, then start the next one.
- One tick is 50 ms. All timings in this document are ms at the default config. When a timing scales with
  a config value, the formula is given.
- **Shared time versus local time:**
  - The spin, the throw and the puck are **shared time**: every viewer sees them at the same moment.
    They ignore the per-player animation speed and skip settings (global §2.8, extras-pvp §0), except
    for reduced motion (§0.5).
  - The beats after the reveal (sweep, payout, banner, celebration) are **local time**. They respect
    the speed setting, and a click or sneak skips them to the final state (global §2.6).

### 0.2 Easing vocabulary (same names in both editions)

| Name | f(x), x∈[0,1] | Used for |
|------|---------------|----------|
| `linear` | x | tumble frame stepping |
| `outQuad` | 1−(1−x)² | wheel head deceleration, Bedrock molang |
| `outCubic` | 1−(1−x)³ | chip flights, panel slides |
| `inCubic` | x³ | chips flying into the balance |
| `inOutCubic` | x<.5 ? 4x³ : 1−(−2x+2)³/2 | puck travel, layout ↔ wheel swap |
| `outBack` | 1+2.70158(x−1)³+1.70158(x−1)² | banners, total badges ("pop") |
| `outBounce` | standard Penner bounce | dolly drop, puck landing |
| `expDecay(k)` | 1−e^(−k·x) / (1−e^(−k)) | ball orbit (k = 3.2) |

The shared names come from global §2.5: Java `dev.nezo.burmaldaholic.client.fx.Ease`, Bedrock
`core/fx.ts`. This file adds `outBounce` and `expDecay(k)` to the same class. Bedrock molang writes the
formulas out inline, and the generator in §5 emits them. As in global §2.5, **numbers never use
`outBack`/`outBounce`**: only badges, chips and pucks do.

### 0.3 Win tiers: global `WinTier` plus table floors

The tier is **computed by the server** with the shared pure `WinTier.of(net, stake, flags)` (global §2.4:
LOSS, PUSH, WIN, NICE, BIG, MEGA, JACKPOT) and is sent with the result. The celebration itself (banner,
count-up, overlay, Bedrock title and particles) is the shared kit (global §2.6). The table games add
two things:

1. **Floors** (`flags`): a game event can raise the tier to at least a given level. It never lowers a
   tier.

| Game event | Floor |
|------------|-------|
| Roulette: a winning straight-up bet (any number) | NICE |
| Roulette: a winning straight-up bet on 0 ("zero hero") | NICE, and the confetti uses green only |
| Craps: point made with odds behind it, or a field 12 hit at 3:1 | NICE |
| Craps: hot shooter, 3 or more points in a row (the shooter only) | NICE |
| Dice Duel PvP: a win | NICE (the opponent's chips fly to you) |

2. **Partial returns** (roulette, craps): when `0 < ret < staked`, the global tier is LOSS. The table
   motion still slides the returned chips back (§0.4) and plays `push` once, so the player sees what
   came back.

Where the kit starts: the celebration begins **after** the table's own payout beat has flown the chips
to the balance. The shared count-up then starts from that point. On Bedrock, `fx.celebrate` is called at
the reveal tick given in each storyboard.

### 0.4 Chip visuals (shared by all three games)

- Denomination colors are the same as the existing `chipColor`: 1 white, 5 red, 25 green, 100 black,
  500 purple.
- The same 5 chip sprites are used in the GUI and in world particles.
- **Chip flight (Java GUI):**
  - The chip starts at the selected chip button's center and lands at the drop point.
  - It flies along a quadratic Bézier with its control point 18 px above the midpoint.
  - The flight takes 180 ms with `outCubic`.
  - The chip's scale rises 1.0 → 1.25 at the arc's apex and returns to 1.0.
  - On landing it squashes (scale Y 0.8, X 1.1) for 60 ms, then returns to 1.0.
  - `chip_place` plays with pitch 0.9 + 0.1·log5(denomination). This is the global `chip_place`
    composite.
- **Stacks:**
  - A stack is drawn as up to 5 discs, each 1 px higher than the one below. Above 5 discs, a
    value label appears (runtime font, 0.5 scale). Stacks never show more than 5 discs.
  - Other players' chips use the **seat tint**: a grayscale chip sprite times the seat color. Seat
    colors, seats 1–8: #4FC3F7, #FFB74D, #BA68C8, #81C784, #F06292, #FFF176, #A1887F, #90A4AE.
  - Other players' stacks are drawn at 70 % size, with a 1 px dark outline, under your own stacks.
  - Accessibility: a number is always written on your own stack.
- **Sweep (lose):**
  - Chips slide to the top edge of the panel (the "house side") over 260 ms with `inCubic`, fading
    out over the last 80 ms.
  - Chips are staggered 30 ms apart; at most 12 are animated individually, and any more fade out
    together.
  - `chip_sweep` plays once.
- **Payout (win):**
  - The payout discs pop in beside the bet, from the house side: 1 disc every 40 ms, at most 8.
  - `chip_stack` (global) plays once per 2 discs, at most 4 times.
  - After 400 ms, the bet stack and the payout stack fly together to the balance counter (top right),
    taking 420 ms with `inCubic`.
  - The balance then rolls up over 600 ms with `outQuad`, and the digits flash gold for 300 ms.
    With reduced flashing (§0.5) the digits stay gold with no flash.
- **Stack size rule:** stack size and payout disc count are only visual. The number labels show the
  exact server amounts (`Texts.chips`).

### 0.5 Accessibility toggles

The settings belong to global §2.8: **Reduce motion**, **Reduce flashing**, **Win celebrations** and
**Effects volume**. Their keys are `gui.burmaldaholic.menu.settings.*`. This spec adds one per-player
setting, **Spin camera** (Bedrock only, NICE, off by default), and specifies how the table motion reacts
to the shared toggles:

| Effect | Default | Reduced motion | Reduce flashing |
|--------|---------|----------------|-----------------|
| Chip flights and sweeps | Bézier flights | 120 ms fade in place | unchanged |
| Roulette wheel | full ball physics | the wheel turns at ≤ 0.4 rev/s, the ball is hidden, and at the end the ball fades in inside the result pocket (300 ms) | unchanged |
| Dice throws | arc, wall hit, tumble | the dice fade in on the final faces (200 ms), then a 1 px outline pulse | unchanged |
| Puck | flip and fly | a 150 ms crossfade to the new position | unchanged |
| Panel shake (MEGA and above, from the shared kit) | ±2 px | off | off |
| Glow and border pulses | ≤ 2 Hz, alpha ≤ 35 % | static | **static**, alpha 25 % |
| Coin showers | 40 / 80 sprites | 10 static coins that fade | sprites fall, no twinkle frames |
| Seven-out vignette | red pulse, 1× 400 ms, alpha ≤ 25 % | static tint | static tint, alpha 15 % |
| Bedrock titles | pop | shown, and the stay does not change | unchanged (Bedrock titles do not flash) |
| Bedrock spin camera (NICE) | opt-in | never | unchanged |
| Bedrock particles | full | ×0.3 count (global §2.8) | no `sparkle` twinkle, coins only |
| Bedrock `camerashake` (NICE, craps wall hit) | 0.15 intensity, 0.25 s | never | unchanged |

Hard limits that apply even with both toggles off:
- No element may flash more than **3 times per second**.
- Nothing that fills the full screen may change luminance by more than 35 %.
- Every result has **words** as well as color: banners use the existing result keys, and pockets have a
  shape marker (§1.4).

Narration (Java): the result banner calls `NarratorChatListener`-style narration with the same
component as the banner (the existing requirement in UI.md §13).

### 0.6 Faithfulness contract (tested)

1. The **final frame equals the server outcome**:
   - Roulette: the ball rests in pocket `result`.
   - Dice: the top faces equal d1 and d2.
   - Puck: its position equals `point`.
   - Unit tests cover all 37 results × 64 seeds, and all 36 dice pairs × 64 seeds × 8 paths.
2. **No false readouts before the reveal:**
   - Tumbling dice use the blurred `die_tumble` frames. Readable pips appear only in the last tumble
     (≥ 82 % of the throw), and they are the real faces. The current Java code, which cycles random
     readable faces, is removed.
   - The roulette pocket highlight, the number readout and the dolly appear only once the ball has
     settled (`u ≥ 0.90`, §1.3).
   - During the bounces the ball never stays in a non-result pocket for longer than **120 ms**. This
     is covered by a test.
3. **Text trails the animation:**
   - Result banners, chat lines, action-bar results and Bedrock result forms are posted **after** the
     animation lands.
   - Craps: the server holds the chat, action-bar and title lines for `REVEAL_DELAY = 32 t`.
   - Dice Duel: 36 t per round, as described in §3.
   - Economy settlement stays **immediate** (restart safety, GAME_DESIGN §4.1). Only the messages wait.
4. **The HUD balance floater** (`+120`) is delayed on the client until the screen's payout flight
   reaches the balance counter. On Bedrock the status line is suppressed for the reveal delay.
5. **Payout visuals use the server's per-bet returns.** The client never derives a payout from the
   layout. This needs the per-bet resolution data listed in §1.7, §2.7 and §3.6.
6. Cosmetic randomness (hops, throw paths, sparkles) comes **only from `seed`**, never from client
   `Random`. As a result, two players standing side by side see the same bounce.
7. Revealing the result early to modded clients is acceptable. It happens only after bets have closed:
   the roulette result is drawn at SPIN, after NO_MORE_BETS. Craps and duel results are rolled at once.

### 0.7 Performance budget

| Item | Budget |
|------|--------|
| Java GUI per frame (one table screen) | ≤ 400 extra quads; GUI particles from the shared pool (global §2.9: ≤ 96 live); no allocations per frame in the path functions (primitive math only) |
| Java BER per table | ≤ 220 vertices (wheel 24-segment bowl + 37-pocket head strip + ball + dolly), ≤ 96 for craps (2 dice + puck); `getViewDistance()` 48 for roulette (research §7), 32 for craps; animated only within 32 blocks, static pose beyond |
| Java server | 0 per-tick packets for animation: one state/update packet per event (spin start, roll, result) |
| Bedrock script | ≤ 3 entity property writes per event; sound timeline ≤ 12 `playSound` calls per spin, scheduled with one `system.runTimeout` chain; no per-tick loops for animation |
| Bedrock entities | roulette 1 wheel entity per table; craps 2 dice + 1 puck per table; duel ≤ 4 dice per duel, despawned 60 t after the end |
| Bedrock particles | table motion ≤ 12 per event (chip pops, dice dust); celebrations follow the global budget (§2.9, ≤ 60 per event) |
| Sounds per viewer | ≤ 6 per second during a spin, never more than 2 overlapping of the same id |

### 0.8 Sounds (new ids; the same ids on both editions)

Java maps them to vanilla events in `src/main/sounds/<module>/sounds.json`. Bedrock maps them to vanilla
sound files in `RP/sounds/sound_definitions.json`. No `.ogg` files ship; a resource pack can replace
them. Subtitles are in §6.

| Id | Module | Description (the feel to aim for) | Java vanilla map (pitch / vol) | Bedrock vanilla map |
|----|--------|--------------------------------|---------------------------------|---------------------|
| `roulette_ball_roll` | roulette | Short rolling tick; repeated every 250→600 ms with falling pitch while the ball orbits | `block.stone_button.click_off` 1.9/0.25 | `random.click` p 1.9 v 0.25 |
| `roulette_ball_drop` | roulette | Hard clack as the ball hits a deflector diamond | `block.bamboo_wood.hit` 1.6/0.7 | `hit.bamboo_wood` |
| `roulette_ball_bounce` | roulette | Lighter clatter as the ball hops across the frets | `block.bamboo_wood_button.click_on` 1.4/0.6 | `random.wood_click` p 1.4 |
| `roulette_ball_settle` | roulette | Soft click as the ball sits in its pocket | `block.wooden_button.click_off` 1.1/0.6 | `random.wood_click` p 1.0 |
| `roulette_bell` | roulette | Dealer's "no more bets" bell (one strike) | `block.bell.use` 1.6/0.4 | `block.bell.hit` p 1.6 |
| `roulette_dolly` | roulette | Glassy tink as the win marker lands | `block.amethyst_block.hit` 1.3/0.8 | `hit.amethyst_block` |
| `chip_sweep` | core | Chips raked across felt | `item.bundle.remove_one` 0.8/0.7 | `bundle.remove_one` |
| `dice_throw` | extras | Whoosh plus rattle as the dice leave the hand | `entity.snowball.throw` 1.3/0.5 | `random.bow` p 1.6 v 0.4 |
| `dice_bounce` | extras | Dice bouncing on felt (per floor contact) | `block.wool.hit` 1.2/0.9 | `hit.cloth` |
| `dice_wall` | craps | Dice hitting the rubber back wall | `block.wood.hit` 1.5/0.8 | `hit.wood` p 1.5 |
| `dice_cup` | extras | Dice rattling in the cup (loop of 3 hits) | `block.bamboo_wood.step` 1.8/0.6 | `step.bamboo_wood` p 1.8 |
| `craps_puck` | craps | Heavy plastic puck set down | `block.stone_button.click_on` 0.6/0.8 | `random.click` p 0.6 |

That is **12 new ids**. The following are reused rather than duplicated:
- existing: `roulette_spin` (launch whoosh), `dice_roll`, `pvp_drumroll`, `pvp_victory`, `wheel_tick`
- from global §2.7: `chip_place`, `chip_stack`, `push`, `ui_deny`, `lose`, `win_small`, `win_nice`,
  `win_big`, `win_mega`, `jackpot`

The tier sounds are played by the celebration kit, not by this spec's timelines. The Bedrock ids use
the `burmaldaholic.<id>` form (global §2.7).

Rule: the in-world sounds are played **client-side and positional** by the Java BER, or by the Bedrock
script at the table position, from the timeline. The Java server stops playing the per-event
`SPIN_SOUND` / `DICE_ROLL_SOUND` for players whose client has the mod, which is all of them on Java.
It keeps playing them only in the headless or fallback path, so nothing is heard twice.

### 0.9 Capabilities (confirmed by `docs/research/animation.md`) and remaining assumptions

Confirmed by the research:
1. **Java GUI** (research §2.1): `g.pose()` is a `Matrix3x2fStack` with rotation, and `WheelScreen`
   already rotates. `blitSprite(RenderPipelines.GUI_TEXTURED, …)` draws GUI sprites, and
   `nextStratum()` layers the result banner above the widgets. As a result, the rotated wheel
   (§1.4) needs no pre-rendered frames.
2. **Java BER** (research §2.6): the `createRenderState` / `extractRenderState` / `submit` API, with
   `submitCustomGeometry` for the wheel disc and `submitText` for the badges. Sync uses one
   `getUpdateTag` per event, and `getViewDistance()` can be overridden.
3. **Java Display entities** (research §2.7):
   - Interpolation works, but the transformation setters are **private**, so an accessor mixin is
     needed.
   - The rotation slerp takes the **shortest path**, so the tumble keyframes are ≤ 90° apart (§3.4).
   - `TextDisplay` is used for the duel total billboards.
4. **Bedrock entities** (research §3.2):
   - `client_sync` int/bool properties can be read with `q.property` in animations and controllers.
   - `playAnimation` can drive controller states.
   - `setPropertyOverrideForEntity` exists. It is **not** used here, because every viewer must see the
     same wheel.
5. **Bedrock camera and shake** (research §3.7): `player.camera.setCamera` with ease, and
   `camerashake` through `runCommand`. Both are opt-in or NICE, are never used while a form is open, and
   are never used with reduced motion.

Remaining assumptions:
6. **Bedrock JSON UI form styling** is possible but fragile (research §3.8). Everything that needs it is
   marked NICE.
7. **Glyphs:**
   - Code points **U+E1C0–U+E1CF are free**. They are reserved here for table FX. In use or claimed so
     far: E100, E110–E14F, E150–E153, E160–E165, E170–E177, E180–E19B (global §2.3, BOTS.md) and
     E1A0–E1A1.
   - Language-neutral pixel **numerals** are allowed on world textures (research §8: "digits as glyph
     sheets"). Words never are.

---

## 1. European Roulette

### 1.1 Audit: what exists today

**Java** (`games/roulette/client/RouletteScreen.java`, 380 × 236 panel):

Layout and chips:
- ✔ The layout grid is correct and clickable (splits, corners and so on through `Layout.hit`).
- ✔ Hovering highlights the covered numbers. ✔ Tooltips show the bet and the payout.
- ✘ **Chips are flat 10 × 10 squares** that appear instantly. There is no flight, no stacking and no
  denomination read-out beyond a text scaled to 0.5.
- ✘ Other players' chips are 5 × 5 gray squares with no seat identity.
- ✘ There is no ghost preview of **where** a split or corner chip will land, so edge bets are guesswork.
- ✘ An invalid click (over the limit, not in the betting phase) does nothing visible on the layout;
  only the error line changes.

Phases:
- ✘ NO_MORE_BETS changes only the status text. There is no bell, no dimming and no lock.

The spin:
- ✘ **It is a horizontal strip of pockets** that scrolls under a marker with cubic ease-out
  (`SpinAnimation`, 3 laps).
- It has no wheel, no ball, no bounce and no tension, and the result is readable in the strip's final
  frames.
- It is correct, because the strip ends on the result, but it reads like a slot reel, not a roulette.

Result:
- ✘ The winning number gets a gold outline and nothing more. There is **no dolly**, no chip sweep and no
  payout motion.
- There is no banner and no tier: a 35:1 straight-up hit looks the same as a lost Red bet, apart from
  the status text.
- ~ The history strip is static. There is no insert animation, and the newest entry has a gold outline.

In the world:
- ✘ Nothing is rendered. The table is a 16 × 16 block with a flat texture.
- Bystanders hear a single `roulette_spin` sound (wood click) and see nothing.

Sound: a single click at spin start. There are no ball, drop or settle sounds.

**Bedrock** (`bedrock/src/games/roulette/table.ts`, `logic/animation.ts`):
- ✔ An action-bar strip of 7 pockets in wheel order, stepped with a quadratic ease-out (`spinFrames`)
  and ending on the result. It is correct but plain: color codes only, no ball, and no shape markers
  for color-blind players.
- ✘ There is nothing in the world: no wheel and no marker. The chat line "The ball is spinning…" is all
  that bystanders get.
- ✘ The result is shown in the action bar and a form. There is no title and no tier, and a big win
  shows no particles.
- ✘ Chip placement is silent apart from forms. There is no `chip_place` sound on Bedrock, and the core
  UI rule requires one.

### 1.2 Target experience (the short version)

A **real top-down wheel** replaces the strip:
- The wheel head spins clockwise while the ball runs counter-clockwise on the track.
- The ball slows, drops, hits a deflector, hops twice across the frets and settles in the drawn pocket,
  while the wheel keeps turning.
- The dolly drops onto the winning number on the layout.
- Losing chips are raked away and winning chips are paid out and fly to the balance.

On Java, the same wheel spins **on the table in the world** (BER), so everyone nearby watches the same
ball. On Bedrock, a **wheel entity** on the table plays the same curve from molang, and the result number
floats above it.

### 1.3 Ball path, shared with both editions (`RouletteBallPath`)

The inputs are `result`, `seed`, `S = spinTicks × 50 ms` (default 5000) and `u = t / S ∈ [0,1]`.

Derived from `seed`, a `SplitMix64` so the edition ports can reproduce it:
- `h1 ∈ {−3,−2,−1,1,2,3}`, `h2 ∈ {−2,−1,1,2}`, with `h1+h2 ≠ 0`, so the ball never lands on the
  result pocket before the final hop.
- `laps ∈ {5,6}` of the ball relative to the head.
- `H0` = the wheel head's start angle.
- `B0` = the ball's release angle.

Angles are in degrees, clockwise positive, and 0 is the top of the screen. The pocket angle is
`P(n) = wheelIndex(n) × 360/37`.

Wheel head:
- `Head(u) = H0 + 540 × outQuad(u)`, which is 1.5 turns clockwise, still moving slowly at u = 1.
- After u = 1 it coasts to a stop over the RESULT phase (`outQuad` from its final speed). The ball stays
  locked to the head.

Ball angle relative to the head, `Rel(u)`:

| Phase | u range | Rel(u) | Ball radius r (fraction of the wheel radius) |
|-------|---------|--------|---------------------------------------------|
| Launch | 0 – 0.04 | `B0 − H0 + ...` blended into the orbit (C¹-continuous) | 0.96 (outer track) |
| Orbit | 0.04 – 0.60 | `P(res+h1+h2) − 360·laps·(1 − expDecay(3.2)(v))`, where `v` is the phase-normalized u | 0.96 → 0.93 (a slow spiral inward) |
| Drop | 0.60 – 0.68 | continues on its angular velocity, capped at 70 % | `0.93 → 0.74` with `inQuad`; at 0.64 a **deflector kick**: r += 0.03 for 80 ms, then `roulette_ball_drop` |
| Hop 1 | 0.68 – 0.78 | from `P(res+h1+h2)` to `P(res+h2)` with `inOutCubic` | 0.62 + 0.08·sin(π·v) (an arc over the frets), then `roulette_ball_bounce` at the end |
| Hop 2 | 0.78 – 0.86 | from `P(res+h2)` to `P(res)` with `inOutCubic` | 0.62 + 0.04·sin(π·v), then `roulette_ball_bounce` (pitch 1.2) |
| Settle | 0.86 – 0.90 | `P(res) + 1.5·sin(6π·v)·(1−v)` (a wobble) | 0.62, then `roulette_ball_settle` at 0.88 |
| Settled | 0.90 – 1 and beyond | exactly `P(res)`, and the ball rides the head | 0.62 |

Here `P(res+k)` means the pocket k steps along the wheel order, not the number `res + k`.

Tested invariants:
- `Rel(1) == P(result)` exactly.
- Between 0.68 and 0.90, the time spent over any pocket other than the result is ≤ 120 ms at
  S = 2000 ms, the shortest config.
- r is continuous.

Scaling: the phase boundaries are fractions of S, so `roulette.spinTicks` values from 40 to 300 all work.
Below S = 3000 ms, hop 2 is dropped (`h2 = 0`, `h1 ≠ 0`) and hop 1 covers 0.68–0.86.

Ball-roll sound schedule during the orbit: at `u` where `Rel` crosses each multiple of 360°, at most
once per 250 ms. The pitch runs 1.9 → 1.3 across the orbit.

### 1.4 Java screen storyboard

The panel stays 380 × 236, and the layout keeps its coordinates. The wheel panel is **160 × 160**,
centered in the upper area (wheel radius 72 px).

**BETTING**

| Beat | Timing | What happens |
|------|--------|--------------|
| Hover a spot | continuous | The covered cells brighten (+25 % white, as today). A **ghost chip** (the selected denomination at 55 % alpha) sits at the exact drop point: an edge for a split, an intersection for a corner. The tooltip is unchanged. |
| Click (valid) | 0–180 ms | A chip flies from the selected chip button to the drop point (§0.4); the local stack grows on the next state packet. If the server's state packet has not arrived within 400 ms, the flown chip stays as a ghost at 55 % until it does. A rejected bet runs the invalid beat. |
| Click (invalid, sent to the server and rejected, or locally known) | 0–220 ms | The ghost chip turns red with a 1 px outline and shakes on x (+3, −3, +2, 0 px at 55 ms steps). The deny sound plays (the existing `UI.md §12` error sound) and the error line appears. There is no flight. |
| Chip button select | 120 ms | The selected chip rises 2 px and gets a gold 1 px ring. The previous one drops back. |
| Another player bets | 150 ms | Their chip drops from 8 px above its spot with `outBounce`, in their seat tint (§0.4). It is silent unless it is within 1 s of your own action. |
| Rebet | 60 ms stagger | All rebet chips fly from the chip tray in sequence, capped at 400 ms in total. |
| Clear | 200 ms | Your stacks slide back to the chip tray with `inCubic` and fade out. |
| Timer | last 5 s | The timer ring pulses once per second (scale 1.0→1.08, 150 ms). In the last 3 s, `wheel_tick` plays each second. |

**NO_MORE_BETS** (20 t = 1000 ms)

| t | Beat |
|---|------|
| 0 | `roulette_bell` plays. The banner "No more bets" slides down from the top of the panel (250 ms, `outBack`). |
| 0–400 | A dim wipe runs left → right over the layout: an alpha-35 % black overlay, whose edge is a 12 px gradient. The chips desaturate by 30 %. The hover ghost is disabled. |
| 600–1000 | The layout slides down 40 px and scales to 0.62, parking in the lower-left corner as a mini layout that keeps the chips visible. The wheel panel fades and scales 0.85→1.0 into place (`inOutCubic`, 400 ms). |

**SPIN** (S = 5000 ms, §1.3). Wheel layers, bottom to top:
1. The bowl (static wood and track).
2. 8 deflector diamonds (static).
3. The head, which rotates: pockets, frets, and the **pocket numbers drawn at runtime** with the font,
   rotated with the head at 0.5 scale, white.
4. The turret (it rotates with the head, plus a specular highlight that does not rotate).
5. The ball shadow (offset +1, +1).
6. The ball (8 × 8, 3 highlight frames chosen by the ball's world angle so the highlight stays top-left).

Beats by u:
- `u = 0`: `roulette_spin` whoosh. The ball appears at `B0` on the track with a 4-frame motion streak
  (a 3-sprite trail at 60/40/20 % alpha) while its angular speed is above 360°/s.
- `u ≈ 0.64`: the deflector kick. A **4-pixel spark** appears at the diamond, and the diamond flashes
  white for 80 ms (a static highlight with reduced flashing).
- `u = 0.68`, `0.78`: the hop landings. The fret the ball crosses lights for 100 ms.
- `u = 0.90`: settled. The result pocket's **glow ring** fades in (`pocket_glow`, 250 ms), and one pulse
  plays at 2 Hz (static with reduced flashing).
  - The big number badge pops above the turret: the number in a disc of the pocket color, sized
    1.6× font scale, with `outBack` over 250 ms.
  - Shape marker: a red pocket is a **filled disc**, a black pocket is a **ring**, and 0 is a
    **diamond**. The same shapes are used in the history strip and the Bedrock glyphs.
- Mini layout (lower left): nothing moves on it during the spin, apart from a slow 1 Hz shimmer on your
  chips, and none with reduced motion.

**RESULT** (60 t = 3000 ms)

| t | Beat |
|---|------|
| 0–400 | The wheel panel shrinks to 0.5 and slides to the upper-right corner, where it stays visible and still turning slowly. The layout returns to full size (`inOutCubic`). |
| 400–650 | The **dolly** drops onto the winning number cell from 24 px above, with `outBounce`. Its shadow grows from 30 % to 100 %, and `roulette_dolly` plays. The winning cell's outline turns gold, and every covering outside box (dozen, column, red and so on) gets a gold outline with a 60 ms stagger in reading order. |
| 650–1000 | **Sweep:** losing stacks slide to the top edge (§0.4), and other players' losing stacks go too. `chip_sweep` plays. |
| 1000–1500 | **Payout:** winning stacks receive their payout discs (§0.4). The amount label above each winning stack shows `+N`, from the server's per-bet return, and pops in with `outBack`. |
| 1500–2000 | The winning stacks fly to the balance, and the balance rolls. |
| 1000 | The **result banner** comes in: the existing `result_win` / `result_lose` text, colored by tier (§0.3), 160 px wide, wrapped. It slides in with `outBack` and stays until the next BETTING. At NICE and above, the shared `CelebrationOverlay` (global §2.6) plays over it once the payout has reached the balance, at about 2000 ms. **Zero hero** (a straight-up bet on 0 hit) shows a green-edged banner with the existing `msg.burmaldaholic.roulette.zero_hero` key as its second line. |
| 2600–3000 | The dolly fades out after the new BETTING packet arrives. The **history strip** gets the new pill: it slides in from the left edge (250 ms), the others shift right by 17 px, and the pill that falls off fades out. The newest pill keeps its gold outline and draws a shape marker. |

Multiplayer:
- Other players' winning stacks get their payout discs in their seat tint. They fly to their seat
  name tag, which is drawn at the panel edge, not to your balance.
- Their banners are not shown to you. A line such as "Alex +360" (`gui.burmaldaholic.fx.nearby_float`)
  floats up from their stack for 1.2 s.

### 1.5 Java in-world (BER `RouletteTableRenderer`)

- **Model** (on the block top, y = 16/16 + 0.01):
  - The bowl is a 24-segment ring of outer radius 7/16 block, rising 1 px at the rim.
  - The head is a 37-quad fan with the `wheel_world` texture strip. The turret is a 2 × 2 px cross.
  - The ball is a 1 px cube with a white emissive-free cutout.
- Motion is **identical to §1.3**, driven by synced `spin_start` (game time), `spin_ticks`, `result` and
  `seed` from the block entity update tag (§1.7).
- The sound timeline is played positionally from the BER, with a 24-block audible range.
- **Idle:**
  - The wheel is **static** and the ball rests in the last result pocket. A turning wheel would imply
    a spin (global §4.13).
  - During BETTING, only the dolly of the last result stays visible, until the next NO_MORE_BETS.
- **Result badge:** for 3000 ms after RESULT, a billboard sits 0.6 block above the table.
  - It shows the result number (runtime font, scale 0.025) inside a pocket-color disc with a shape
    marker (§1.4).
  - It fades in with `outBack` over 250 ms.
  - A pixel **dolly** (8 × 8 billboard) bobs next to it (±0.03 block, 1 Hz).
  - The badge can be read from 16 blocks.
- **Wins in the world:** particles appear at the winning player's position, not at the table. They follow
  the tier (§0.3) and use the shared kit's world FX (global §2.6).
- **LOD:** at 32–64 blocks, only the head rotation is drawn, with no ball. Beyond 64 blocks, nothing
  animates.

### 1.6 Bedrock storyboard

**Placing bets** (forms)
- After each confirmed bet:
  - `chip_place` plays at the table (pitch by denomination, as in §0.4), audible to anyone nearby.
  - Action bar for 40 t: `⛁ +25 · Split 17–20`, using existing bet labels and the chip glyph.
  - A **2-particle `chip_pop`** (global particle, the denomination color via a molang variable) pops on the table top so bystanders see
    activity.
- If the bet is invalid: the existing error line is the first body line (UI.md §12), plus the vanilla
  `note.bass` deny sound.

**NO_MORE_BETS**
- `roulette_bell` plays.
- Title channel: nothing, so titles stay free for the result. The existing action bar
  "No more bets" is kept.

**SPIN** (the wheel entity `burmaldaholic:roulette_wheel`, one per table, sitting on the table top)
- The server writes these properties in **one tick**:
  - `spin_seq` (+1 mod 256)
  - `pocket` = `wheelIndex(result)` (0–36)
  - `hop1`, `hop2`
  - `laps`
  - `head0` (0–36, the head's start index)
  - `spin_len` (40–300)
- The client animation controller moves `idle → spin` when `spin_seq` changes. The `spin` animation
  evaluates §1.3 in molang: `v.u = math.min(q.anim_time * 20 / q.property('burmaldaholic:spin_len'), 1)`.
  - Bone `head`: rotation y = `-(head0·9.7297 + 540·(1-(1-v.u)²))`.
  - Bone `ball_pivot`: rotation y = head + `Rel(u)`.
  - Bone `ball`: position x = `r(u)·7`.
  - The generator script emits the piecewise ternaries (§5, B-R2).
- At `v.u ≥ 1` the controller moves to `settled`. The head coasts to a stop over 3 s (`outQuad`), and
  the ball stays locked to the head. When the head is idle, it is static (global §4.13).
- **Sounds:** the script schedules the §1.3 sound events with `system.runTimeout` from the same pure
  path (`ball-path.ts`) and plays them at the table position. That is ≤ 12 calls.
- **Action-bar strip (kept and improved):**
  - It shows 7 pockets in wheel order. Each pocket carries a **shape glyph** before its number: U+E1C4
    red filled disc, U+E1C5 black ring, U+E1C6 green diamond. The center pocket is framed as
    `▸ ◂`.
  - The strip follows the ball's pocket relative to the head, from the same path, sampled every
    2 ticks. That is the same curve as the entity, so the strip and the wheel agree.
  - From u = 0.60 the strip shows only the pocket under the ball, 3 each side, and from u = 0.90 only
    `▸ ● 17 ◂`.
  - Reduced motion: the strip updates every 10 ticks.
- **Spectators:** they see the wheel entity and hear the sounds. They do not get the action bar,
  which only seated players receive, as today.
- **NICE, spin camera (opt-in setting "Spin camera", solo table only, never with reduced motion):**
  - Camera: `minecraft:free` at table + (0, 2.2, −1.4) facing the table center, `ease 0.8 in_out_sine`.
  - It stays in place for the spin and is cleared at RESULT + 20 t with `ease 0.6`.
  - Any input (movement or sneak) cancels it at once.

**RESULT**
- The wheel entity's `result_on` = true shows the **dolly bone** (it pops up with a 0.35 s squash
  animation) and makes the nameTag visible: `§c● 17`, where the color and glyph follow the pocket color.
  The name tag holds only digits and a glyph, never words, so it needs no key.
  - It is hidden 60 t later, when the next BETTING starts.
- **Title**, through `hud.title`, which respects PvP title holds:
  - The title is the colored number with its shape glyph.
  - The subtitle is the existing `result_win` / `result_lose` text.
  - Fade 5/40/10 t.
  - At NICE and above, the title is handed to `fx.celebrate` (global §2.6), which shows the tier word and
    count-up. The number title shows first, for 20 t.
- **Particles** at the player come from the shared `fx.celebrate` (global §2.6: `chip_pop`, `chip_fountain`,
  `sparkle`, `gold_burst`).
- The result form (existing) opens **after** the title has shown for 20 t, so the title is not hidden.
  - Its body now starts with the shape glyph and number, then one line per bet:
    `✔ Split 17–20 +180` / `✘ Red −10`, from the server's per-bet returns.
  - Win lines are §a, lose lines are §c. ✔ and ✘ are glyphs, and the words come from existing keys.
- **NICE:** a JSON UI-styled result form with a big-number header panel, keyed on a title prefix
  (see assumption 6).

### 1.7 Server data (dependencies)

Java `RouletteTableBlockEntity`:
- Client state gains:
  - `seed` (int)
  - `spin_start` (the game time when SPIN began)
  - `bets[].ret` during RESULT: the per-bet total return, taken from the settled slip
    (`settleSlip.returns`)
  - `others_res`: spot key → net, for other players' result motion
- **Block entity update tag** (`getUpdateTag` / `ClientboundBlockEntityDataPacket`) for the BER
  carries `phase`, `spin_start`, `spin_ticks`, `result`, `seed` and `last_result`. It is sent at each
  phase change.
- The seed is `mix(pos.asLong(), roundCounter)`.
- The per-event `SPIN_SOUND` is removed from the server (§0.8).

Bedrock `roulette/table.ts`:
- `rt.seed` is created the same way.
- Spawn and repair the wheel entity: exactly one per table, tagged `bh_table:<key>`. It is despawned
  when the table breaks and respawned on the chunk load of a table without one, checked each time the
  table ticks.
- Property writes happen at SPIN, RESULT and BETTING only.

### 1.8 Tiers specific to roulette

- The tier comes from the server's `WinTier` (§0.3), using the floors in that table: a straight-up bet
  gives at least NICE, and zero hero gives at least NICE with green-only confetti.
- The table beats (dolly, sweep, payout) are the same for every tier. Only the banner color and the
  shared celebration change.
- When everything is lost, the banner is the existing `result_lose` text ("17 Black — not this time") in
  muted red. There are no particles, and the sound is the global `lose`.

### 1.9 MUST / NICE

- **MUST, Java:**
  - Wheel panel with ball physics (§1.3–1.4).
  - Chip flights, ghost preview, invalid shake.
  - NO_MORE_BETS wipe and bell.
  - Dolly, sweep, payout and flying balance.
  - Result banner with the shared celebration hook.
  - History insert animation.
  - BER wheel with ball and result badge.
  - Sounds.
  - Accessibility toggles.
- **MUST, Bedrock:**
  - Wheel entity with the synced spin.
  - Sound timeline.
  - Glyph strip with shape markers.
  - Result title and tier particles.
  - `chip_place` on bets.
  - Per-bet result lines in the result form.
  - Dolly and name tag.
- **NICE:**
  - Spin camera (Bedrock).
  - JSON UI result form.
  - In-world chip stacks per bettor: Java BER stacks at the table edge, Bedrock `chip_stack` entity.
  - Hot/cold numbers overlay on the history strip.
  - Fallback F1: 8 pre-rendered rotation frames if 2D pose rotation is unavailable.

---

## 2. Craps

### 2.1 Audit

**Java** (`CrapsScreen.java`, 400 × 240):

Layout:
- ✔ Clear areas, wrapped labels (RU-safe), hover outline, tooltips with odds hints.
- ✘ Bets are **text amounts only** (gold numbers). There are no chips on the layout, so you cannot see
  "your money on Pass" at a glance.

The roll:
- ✘ The dice tray is a 144 × 30 brown strip on the right.
- The **roll animation is 16 ticks of random readable faces** (`cosmetic.nextInt(6)`) with ±2 px
  jitter. It shows wrong numbers (breaking §0.6.2) and has no throw, no wall and no bounce.

Puck:
- ~ The puck is drawn from fills, with ON/OFF text at 0.5 scale.
- ✘ It teleports between the Don't Come bar and the point box. There is no flip and no travel.

Resolution:
- ✘ Bets resolve instantly on the state packet. Amounts vanish or change with no sweep or payout, and
  come bets jump to their point box.
- ✘ The last roll text appears after the animation, but the **chat lines arrive at once** (server), so
  chat spoils the tray animation.

Other:
- ✘ No in-world presentation. The single `dice_roll` sound comes from the extras module, and craps has
  nothing.

**Bedrock** (`craps/runtime.ts`):
- ✘ Text only: the chat headline "Alex rolled ⚂⚄ = 8", personal lines, and a 100-tick action bar.
- ✘ No sound, no in-world dice and no puck.
- ✘ Bots (branch `worktree-agent-aaf0f54e81d19ae4a`, `craps/bots.ts`) roll 20–40 t after the window.
  Nothing marks that a bot is throwing.

### 2.2 Target experience

The shooter throws the dice **across the whole layout**:
1. They arc from the shooter's side.
2. They smack the **pyramid back wall** at the top edge.
3. They tumble back with two floor bounces and come to rest on the real faces.
4. The stickman's call pops the total.
5. The **puck flips and flies** to the point, or back OFF.
6. Chips are swept or paid. Come bets **travel** to their point box.
7. The stick drags the dice back to the shooter.

In the world, two small dice tumble across the table top and hit its far rail, and the puck sits on
the table.

### 2.3 Throw path, shared (`DiceThrowPath`, also used by Dice Duel)

The inputs are `seed`, `faces (d1,d2)`, the `start` point, the `wall` line (or none, for the duel),
the `rest` region, and `T` (the throw duration, default 1350 ms).

Output per die: 2D position `(x, y)`, height `z ≥ 0`, spin angle `θ`, and tumble frame index or final
face.

| Segment | Time | Motion |
|---------|------|--------|
| Flight | 0–450 ms | A parabola from `start` (z = 0) to the wall contact point `W` (x from seed, within 20–80 % of the wall length), apex z = 22 px. `θ` rotates at 720°/s. Tumble frames advance every 50 ms. The two dice are offset 6 px sideways with a phase lag of 40 ms. |
| Wall hit | 450–530 ms | The die squashes on the wall's axis (0.8 / 1.15) for 80 ms. The wall strip gets a highlight at `W` (80 ms). `dice_wall` plays, and 4 dust pixels appear. |
| Rebound | 530–1100 ms | Velocity is reflected with a restitution of 0.55 and an angle jitter of ±15° (from seed). **Two floor bounces** of apex 9 px and 4 px, with `dice_bounce` at each contact (pitch 1.2 → 1.0). Tumble frames slow from 50 to 90 ms per frame. |
| Final tumble | 1100–1350 ms | A skid, decelerating with `outCubic` to the rest point inside `rest`. `θ` eases to the nearest multiple of 90°. At **1110 ms (82 %)** the frame switches to the **real face** and stays there. The dice never overlap: the rest points are ≥ 18 px apart (a solver shifts the second die). |

The final state equals `faces`, which is tested. The rest region is the felt area between the Come and
Field rows (x 40–200, y 52–100 in panel coordinates), which avoids the point boxes. The dice sit over
the Come/Field labels only until the stick-back beat, 2.4 s later.

### 2.4 Java screen storyboard

Layout changes (MUST):
- A **back wall strip**, 232 × 6 px, sits above the point boxes. The point boxes move down 6 px, and
  `BOX_Y` goes from 18 to 24. That space is taken from the gap above CONTROLS_Y, which is already
  there.
- **Chips on the layout:**
  - Your flat bet sits as a stack at the right end of its area, and your odds stack is offset 6 px
    up and to the right, "behind the line".
  - Other players' chips are tinted by seat (§0.4). The text amounts stay for accuracy.

**Before the roll** (shooter)
- The Roll button pulses gently (scale 1.0→1.04 at 1 Hz) while `can_roll` is true. It is static with
  reduced motion.
- Pressing Roll:
  - The button depresses 1 px.
  - Both dice in the tray **rattle** (±1 px, alternating at 60 ms steps) and `dice_cup` plays. The
    rattle lasts until the state packet arrives, at most 600 ms. It shows **blank** tumble frames, so no
    numbers appear before the result.
- Other viewers see the shooter's name plate glow gold during the betting window's final 3 s. With a bot
  shooter, the bot glyph U+E190 goes before the name, and it gets the same glow.

**The roll** (the state packet with `rolls` incremented). `t = 0` is packet receipt, or the synced
`roll_time` on a late join.

| t (ms) | Beat |
|--------|------|
| 0 | `dice_throw` plays. The dice leave the tray (start = the tray center, or the shooter's seat edge for other viewers, `shooter_dir`) and fly over the layout toward the back wall (§2.3). |
| 450 | Wall hit: squash, spark and `dice_wall`. |
| 530–1100 | Rebound with two bounces. |
| 1100–1350 | Skid to rest; the real faces appear at 1110. |
| 1350 | The **total badge** pops 14 px above the dice. It is a dark disc with the runtime number `8` at 1.5× scale, `outBack`, 250 ms. For special totals (2, 3, 7, 11, 12), the disc border takes the event color: green natural, red craps or seven out. |
| 1400 | The **event banner** (the existing `msg.burmaldaholic.craps.*` event keys) slides from the right edge into the status panel, in the event color. |
| 1500–1850 | **Puck** (§2.5). |
| 1600–2400 | **Chip resolution** in this order: (1) losing bets swept, (2) come and don't-come bets **travel** to their point box (a 400 ms arc, `inOutCubic`), (3) winning bets get their payout discs, (4) winnings fly to the balance. This uses the per-bet `last_res` list (§2.7), and each row has a 60 ms stagger. Odds returned as "off" slide back to the chip tray. |
| 2400–2700 | **Stick-back:** a hooked stick sprite slides in from the right edge and drags both dice back to the tray (`inOutCubic`, 300 ms). The badge fades out. |
| 2700 | The idle state is ready for the next window. The window timer is visible throughout. |

Special beats:
- **Point set:**
  - The point box gets a pulsing ring: 2 pulses at 2 Hz, then a steady 1 px gold outline while the
    point is on.
  - The number in the box scales 1.0→1.2→1.0.
- **Point made:**
  - The ring flashes 3× at 2.5 Hz (static with reduced flashing).
  - Confetti (24 small pieces) bursts from the box.
  - The Pass line area's outline glows green for 600 ms.
- **Seven out:**
  - A red vignette appears once: alpha 0 → 25 % → 0 over 400 ms.
  - The dice badge shakes (±2 px, 3 steps).
  - **Every** losing come-point stack is swept in one wave, left → right.
  - The shooter plate slides out to the left, and the next shooter's plate slides in from the right,
    with the translation key from §6.
- **Craps on the come-out (2, 3, 12):** the Pass stacks tip over (they rotate 20° and fall 3 px) and are
  swept. For 12 on Don't Pass, a **"bar" push** glyph (a small gray ring U+E1C9) blinks once over the
  Don't Pass stack, and the stack slides back to the tray unchanged.
- **Hot shooter** (the server's `points_in_row` ≥ 3, as in the existing `pointListeners`):
  - A flame outline sits on the shooter plate: the existing flame glyph U+E170 plus a 3-frame
    `flame` sprite at 6 fps, static with reduced motion.
  - The banner reads "Hot shooter! %1$s points in a row".

### 2.5 Puck animation

- **OFF → ON:**
  1. The puck rises from the Don't Come bar: its shadow grows and its scale goes 1.0→1.15 over 120 ms.
  2. It **flips** as its X scale goes 1→0→1 over 200 ms. At the midpoint the face swaps from black
     (OFF) to white (ON), and the edge sprite shows at the thin moment.
  3. It flies to the point box's top-right corner (`inOutCubic`, 350 ms).
  4. It lands with `outBounce` (4 px), and `craps_puck` plays.
- **ON → OFF:** the reverse. It flies back to the Don't Come bar and flips to black.
- The ON/OFF label is drawn at runtime from `gui.burmaldaholic.common.on/off`, 0.5 scale. It is not
  baked into the sprite.
- Faithfulness: the puck's destination is always `state.point`. If the point changed twice while an
  animation was pending, it goes straight to the latest value.

### 2.6 Java in-world (BER `CrapsTableRenderer`)

- The table top gets a **rail**, a 1 px raised strip, on the edge opposite the shooter (from
  `shooter_dir`). It is textured with the pyramid pattern.
- Two dice, each a 3 px cube (textured faces 4 × 4 from `dice_world`), follow the **same
  `DiceThrowPath`**, mapped from the panel region onto the block top:
  - The layout's 232 × 128 px maps to 14 × 10 px of the top.
  - The height z is scaled to 1 block = 160 px.
  - The final orientation shows d1 and d2 on the top faces, which is tested with the face-to-rotation
    table.
- The dice rest for 1400 ms, then slide back to the shooter's edge (300 ms) and disappear. When idle,
  the dice sit at the shooter's edge.
- **Puck:** a 3 × 3 px disc on the table top.
  - OFF puck: black, at a corner.
  - ON puck: white. The top texture has **6 pixel-numeral point spots** (§4). When ON, the puck sits on
    the spot for `point`.
  - It moves with the same flip and fly as §2.5, scaled down.
- **Total badge:** a billboard 0.5 block above the table for 2 s. It shows the runtime number and a
  disc in the event color.
- **Sounds:** the throw, wall, bounce and puck sounds play positionally from the timeline.
- Particles at a winning player come from the shared kit (§0.3). A point made shows 6 `happy_villager` particles above
  the table, and a seven out shows 4 `smoke` puffs.

### 2.7 Server data (dependencies)

Java `CrapsTableBlockEntity`:
- Client state gains:
  - `seed`
  - `roll_time` (game time)
  - `shooter_dir` (0–3, the direction from the table to the shooter)
  - `points_in_row`
  - `last_res`: a list of `{id, kind, point, mine, outcome: win|lose|push|move|stay|odds_off, flat,
    odds, ret, moved_to}` for the last roll. It is kept until the next roll.
  - `prev_bets`: the bets snapshot from before the roll, so the client can draw the chips in their
    pre-roll places while the dice fly.
- **Reveal delay:** chat headlines, personal result lines, the "point made" listeners' titles and
  advancements are posted `REVEAL_DELAY = 32 t` after the roll (`schedule(level, 32, …)`). The economy
  settles at once.
- **The bet window starts after the reveal:** `betWindowTicks` counts from `roll + REVEAL_DELAY`, so a
  player never has to bet while the dice are still moving.
- The BE update tag carries `d1`, `d2`, `rolls`, `roll_time`, `seed`, `point`, `shooter_dir` and
  `event`.
- **Bots** (`bots.craps.canShoot`) wait at least `REVEAL_DELAY + 20 t` after the previous roll. This is
  also a change on the Bedrock bots branch.

Bedrock `craps/runtime.ts`: the same data and delays. Dice and puck entities are managed per table.

### 2.8 Bedrock storyboard

The entities are `burmaldaholic:table_die` ×2 and `burmaldaholic:craps_puck` ×1 per table. They have no
AI, no collision, are invulnerable and are not saved (they are rebuilt on load).

- **Die properties:** `seq` (0–255), `face` (1–6), `path` (0–7), `side` (0–3, the shooter's direction),
  `slot` (0/1).
- `table_die.animation.json` holds **8 throw-path animations**. The generator samples them from
  `DiceThrowPath` with 8 fixed seeds, so they match the Java paths for those seeds, keyframed every 2
  ticks.
  - Bone `pos`: translation.
  - Bone `tumble`: rotation, which ends at a multiple of 360° on every axis.
  - Bone `face`: a static rotation from `face` by molang lookup, where 6 ternaries map face → (x, z)
    rotation.
  - Because the tumble ends at a whole number of turns, the final orientation is exactly the face
    rotation, which makes it **faithful by construction**.
  - The server picks `path = seed mod 8`.
  - `side` rotates the root bone by 90° steps so the dice fly away from the shooter.
- **Puck:** properties `on` (bool) and `point` (0, 4, 5, 6, 8, 9, 10).
  - Controller: `off ↔ on`, with the flip animation (0.35 s) on the transition. The position comes
    from a molang lookup of the 6 spots on the table top.
- **Timeline** (the script schedules it with `system.runTimeout`):

| Tick | Beat |
|------|------|
| 0 | The dice properties are written (`seq++`). `dice_throw` plays at the table. The action bar shows the shooter "throws": `⚀⚀ → …` using the **tumble glyph frames** U+E1C0–E1C3 animated every 2 t. They are blank dice, so no false numbers appear. |
| 9 | `dice_wall`. |
| 13, 18 | `dice_bounce`. |
| 22 | The dice land (the entity animation ends). The action bar now shows the real faces: `⚂⚄ = 8` (the existing `last_roll` key). |
| 24 | **Title** for special events only: point set "8" + subtitle `point_set`; point made; seven out; natural or craps. Fade 3/30/8. Normal rolls use only the action bar. |
| 26 | Puck properties are written. The flip plays, then `craps_puck`. |
| 32 | The delayed chat lines are posted (§2.7). The action bar carries the personal result line: `✔ Pass +20` / `✘ Field −5`. Tier particles and sounds play at each winning player. |

- **Bystanders** see the dice and the puck in the world and hear everything. Titles and action bars go
  only to seated players.
- **Hot shooter:** the title "Hot shooter! …" goes to the seated players, and 8 `flame` particles
  appear at the shooter.

### 2.9 Tiers (craps)

- The tier is computed over the bets that **resolved on this roll** (`last_res` with `mine`), using the
  server's `WinTier` and the craps floors (§0.3).
- Bets that were only returned (odds off, bar-12 push) play the `push` beat.
- The shooter's hot-shooter floor applies to the shooter only.

### 2.10 MUST / NICE

- **MUST, Java:**
  - Throw across the layout with the wall and faithful faces.
  - Total badge and event banner.
  - Puck flip and fly.
  - Chips on the layout; sweep, payout and come travel.
  - Stick-back.
  - Seven-out and point-made beats.
  - BER dice and puck.
  - Reveal delay and `last_res`.
  - Sounds.
- **MUST, Bedrock:**
  - Die and puck entities with 8 paths.
  - Timeline sounds.
  - Tumble-glyph action bar.
  - Special-event titles.
  - Delayed messages.
  - Tier particles.
- **NICE:**
  - Hot-shooter flame sprite.
  - Shooter-plate slide.
  - Bedrock in-world chip stacks.
  - A "dice off the table" re-roll gag. **Not allowed:** it would imply a different roll.

---

## 3. Dice Duel: against the house and PvP

### 3.1 Audit

**Java** (`DiceScreen.java` 260 × 200, `DuelInviteScreen.java` 220 × 90):
- ✘ A 20-tick animation of **random readable faces** from `new Random(ticks*31)`. It breaks §0.6.2, and
  the dealer's dice cycle through numbers they never rolled.
- ✘ There is no throw, no reveal order and no drama. The outcome line simply appears.
- ✘ PvP: the server sends **only the last round**, while tie re-rolls go to chat (`rollLines`). The
  screen cannot show "Tie! Re-roll".
- ✘ The invite screen is plain text plus a timer. There is no urgency cue, even though the challenge
  holds your money for 30 s.
- ✘ Nothing in the world. Two players duelling face to face see nothing between them, and bystanders
  see nothing at all.

**Bedrock** (`extras/dice-game.ts`):
- ✘ Against the house: an action bar with your roll, a 15-tick sleep, then a form. The **dealer's roll
  first appears in the result form**, which has no reveal moment.
- ✘ PvP: all rolls are dumped into chat at once, with no staging.

### 3.2 Target experience

A **two-lane duel**: you on the left, the dealer or the opponent on the right.
1. You shake the cup.
2. Both pairs are thrown.
3. Your dice land first, then theirs 350 ms later.
4. The totals pop, and the higher total's badge **crushes** the other one (it scales up while the
   other cracks and dims).
5. The chips move across the table.

PvP plays the same way for both players, mirrored, with every tie round shown ("Tie! Re-roll 2 of 3"),
and **real dice fly between the two players in the world**.

### 3.3 Java screen storyboard

Panel changes:
- The panel grows to 260 × 216.
- The dice area grows to 244 × 70 and becomes a felt **lane** with a center divider and a "vs" badge
  (pixel art, with no letters: two crossed dice).
- Each lane has a dice cup (24 × 24) at its outer edge.

**Against the house:**

| t (ms) | Beat |
|--------|------|
| 0 | Roll is pressed. Your cup **shakes** (4 frames, 60 ms each, ±2 px), and `dice_cup` plays. The dealer's cup shakes in the opposite phase. This lasts until the result packet arrives, and at least 300 ms. |
| 300 | Both cups tip (they rotate 35° toward the center over 100 ms). `dice_throw` plays. Both pairs leave via `DiceThrowPath` with **no wall**: a short arc that bounces once on the felt (apex 14 px, one bounce), T = 900 ms. The real faces appear at 82 %. |
| 1200 | **Your** dice rest. Your total badge pops (`outBack`, 250 ms). |
| 1200–1550 | **The dealer's dice are delayed by 350 ms** (a suspense offset built into their path). A soft `pvp_drumroll` roll plays at 0.3 volume under the delay, NICE. |
| 1550 | The dealer's dice rest, and their total badge pops. |
| 1750 | **Compare:** |
|  | Win: your badge scales to 1.3 and turns gold. The dealer's badge cracks (a 2-frame crack sprite) and dims to 50 %. The "vs" badge tilts toward the loser. |
|  | Lose: the mirror image, so your badge cracks. |
|  | Push: both badges shake ±1 px twice and turn gray. |
|  | **House tie on 7:** both show 7. A **red house stamp** (pixel art: a red seal holding two small dice showing 3 and 4) slams onto the dealer's side with scale 1.6→1.0, `outBounce`, 250 ms. The text is the existing `tie_seven` key. |
| 1900–2600 | Chips: your stake stack sits in your lane. On a win, the dealer pushes a matching stack across the divider (400 ms) and both stacks fly to the balance. On a loss, your stack slides to the dealer's side and fades out. On a push, the stack returns to the tray. |
| 1900 | The outcome line (existing keys) appears with its tier color. `win` or `lose` plays. |

`ROLL_TICKS` goes from 20 to 52. The Roll button is re-enabled at 2600 ms. Pressing Roll again during
beats after 1900 ms skips straight to the new roll.

**PvP (both screens):** the server sends `rounds: [{you:[a,b], them:[a,b]}…]` and `outcome`.
- Every round plays the throw above. After a tied round:
  - Both totals shake.
  - The banner "Tie! Re-roll %1$s of %2$s" appears for 600 ms.
  - The dice are **swept back into the cups** (300 ms), and the next round starts, 1400 ms per round.
- After the final round:
  - **Win:** the opponent's stake stack flies across the divider into yours, and then everything flies
    to the balance. The tier floor is NICE (§0.3), and `pvp_victory` plays.
  - **Refund after 3 ties:** both stacks slide back to their owners, with a gray banner (the existing
    `pvp_refund` key).
- The opponent's name label comes from their name at runtime, in a name plate. It is not a translation
  key.

**Invite screen:**
- The dice cup icon rattles once per second (2 frames), and is static with reduced motion.
- The timer is a ring around the icon that empties clockwise. It turns red and pulses at 1 Hz in the
  last 5 s.
- The Accept button gets a 1 px green glow. Decline stays neutral.
- The stake is shown as a chip stack plus the existing text.

### 3.4 In-world duel (Java: display entities; Bedrock: `table_die`)

**PvP (MUST):**
- On acceptance, the server spawns **4 dice**, one pair per player, as Java `ItemDisplay` entities
  using the item model `burmaldaholic:dice_display` (a 6-face cube, §4) or as the Bedrock `table_die`
  with `mode = duel` (scale ×2.5).
- Each pair starts at its owner's chest, 0.4 block toward the opponent.
- It is thrown to the **midpoint between the players**, on the ground found by a downward raycast. If
  no ground is found within 3 blocks, it lands at the midpoint's height and is shown floating, with a
  `cloud` puff on landing.
- Java motion comes from the server:
  - Every 3 ticks: `teleport_duration = 3` with the path sampled from `DiceThrowPath`, in world units
    where 1 path px = 1/40 block.
  - Tumble: `transformation.left_rotation` is interpolated in 90° steps
    (`interpolation_duration = 3`).
  - The final rotation is the face-up quaternion for the real face.
- Bedrock: `path`, `face`, `seq` and `side` (the yaw toward the opponent) are written once.
- Timing per round matches the screen timeline (§3.3), and the opponent's pair lands 350 ms later.
- **Result:**
  - A total billboard over each pair: Java uses a `TextDisplay` whose text is only the number, and
    Bedrock uses the entity nameTag with digits.
  - The winner gets the NICE-tier world burst from the shared kit.
  - Bystanders within 16 blocks see and hear everything.
- The dice despawn 60 t after the final round.
- A tied round: the dice are "picked up", fading with scale → 0 over 5 t, then rethrown.

**Against the house (NICE on Java, MUST on Bedrock, because Bedrock has no screen animation):**
- Your pair lands 1.5 blocks in front of you, on the ground from a raycast.
- The dealer's pair is thrown from the Craps table if you are at one, otherwise from 2.5 blocks ahead,
  toward you.
- The same timeline applies.

### 3.5 Bedrock storyboard

**Against the house** (`houseRound`):

| Tick | Beat |
|------|------|
| 0 | The stake is confirmed. `dice_cup` plays. The action bar shows the cup-shake glyphs (tumble glyphs E1C0–E1C3 alternating every 2 t) for 6 t. |
| 6 | The in-world throw (§3.4), and `dice_throw` plays. |
| 24 | Your dice land. Action bar: `⚂⚄ = 8` (the existing `your_roll` key). |
| 31 | The dealer's dice land. Action bar: both rolls (`your_roll` · `their_roll`). |
| 36 | **Title:** `8 : 6` (runtime numbers, colored green or red, and the colon is a literal glyph). Subtitle: the outcome key (`win` / `lose` / `tie` / `tie_seven`). `fx.celebrate` plays the tier (normally WIN, because the bet pays 1:1). |
| 56 | The result form opens, as it does today, after the title. |

`sleep(s, 15)` becomes `sleep(s, 56)`.

**PvP:**
- Both players get the same timeline, one round every 30 t.
- Between rounds: the title "Tie! Re-roll %1$s of %2$s" (fade 2/16/4).
- The final result:
  - The winner's title is "Victory!" (the existing PvP key if present, otherwise §6) with `pvp_victory`
    and the shared `chip_fountain` particle.
  - The loser gets a gray title with their roll and `lose`.
  - The chat lines (`rollLines`, `pvp_result`) are posted **after** each round's reveal, not all at
    once.
- Economy: the escrow and payout stay atomic and immediate (as today). Only the messages and titles
  follow the timeline.
- PvP-core migration (PVP.md §10.6, NICE): when Dice Duel moves to the PvP frame, this timeline becomes
  its **Final Reveal** presenter segment. It must call `hud.holdTitle(player, rounds×30+40)` so no other
  title interrupts the reveal.

### 3.6 Server data (dependencies)

Java `DiceGame`:
- The result gains `rounds` (a list of pairs) and `seed`.
- The house result gains `seed`.
- The `rollLines` chat and the `pvp_result` chat are scheduled at each round's reveal tick
  (36 t + 30 t × round index).
- A `DuelStage` server helper spawns and animates the display entities and is ticked from the server
  tick event. At most 8 duels are staged at once; beyond that, a duel gets no in-world dice.

Bedrock `dice-game.ts`: the same fields, plus a `duel-stage.ts` helper.

### 3.7 MUST / NICE

- **MUST:**
  - Java two-lane screen with cups, a faithful throw, staggered reveal, crush compare, house stamp and
    chip moves.
  - PvP rounds shown.
  - Invite ring timer.
  - PvP in-world dice on both editions.
  - Bedrock in-world house dice.
  - Bedrock timeline titles and delayed chat.
- **NICE:**
  - Java in-world house dice.
  - Drumroll under the dealer delay.
  - PvP rivalry flair (PVP.md, after migration).
  - Taunt emotes during the re-roll pause.

---

## 4. Asset inventory

All textures are **generated by scripts** (deterministic, with no text baked in). The one exception is
the dolly, which may be refined as hand-authored pixel art at the same size.
- The generator is the shared **`scripts/gen-fx-assets.py`** (global task S1). It writes both editions
  from one palette.
- This spec adds a `tables` section to that generator:
  - Java GUI sprites go under `textures/gui/sprites/burmaldaholic/<game>/…` (the global convention).
  - Entity and block textures go under `textures/entity|block/<game>/…`.
  - Bedrock files are mirrored under `bedrock/packs/<module>/RP/textures/…`.
- In the table below, the "gui/…" paths are relative to `textures/gui/sprites/burmaldaholic/`.

### 4.1 Textures

| # | Path (Java `textures/`; Bedrock RP mirror) | Size | Frames | Content |
|---|------------------------------------------|------|--------|---------|
| T1 | `gui/roulette/wheel_bowl.png` | 160×160 | 1 | Wood bowl, ball track (lighter ring), 8 brass deflector diamonds |
| T2 | `gui/roulette/wheel_head.png` | 128×128 | 1 | 37 pockets (red, black, green) in wheel order, brass frets and inner cone. **No numerals** (drawn at runtime) |
| T3 | `gui/roulette/wheel_turret.png` | 32×32 | 1 | Brass cross turret |
| T4 | `gui/roulette/ball.png` | 24×8 | 3 | Ivory ball, highlight in 3 positions |
| T5 | `gui/roulette/pocket_glow.png` | 32×32 | 1 | Radial gold glow (alpha) |
| T6 | `gui/roulette/dolly.png` | 24×16 | 2 | Dolly (12×16) + its shadow (12×4, padded) |
| T7 | `gui/tables/chips.png` | 72×16 | 6 | Top views (12×12) and stack discs (12×4) for 5 denominations + 1 grayscale for the seat tint. If global §4.4 ships chip-stack sprites, reuse them and keep only the grayscale cell |
| T8 | `gui/tables/confetti.png` | 24×4 | 6 | Confetti 4×4 × 6 colors (sparkles and coins come from the global kit) |
| T9 | `gui/tables/felt.png` | 64×64 | 1 | Tileable felt noise (replaces the flat `FELT` fill; use the global 9-slice felt if it lands first) |
| T10 | `gui/craps/die_tumble.png` | 128×16 | 8 | Motion-blurred rotating die, **no readable pips** |
| T11 | `gui/craps/die_faces.png` | 96×16 | 6 | Crisp faces 1–6 (the 1-pip is red, as today) |
| T12 | `gui/craps/die_shadow.png` | 16×8 | 1 | Soft ellipse |
| T13 | `gui/craps/back_wall.png` | 16×8 | 1 | Tileable rubber pyramids |
| T14 | `gui/craps/puck.png` | 48×16 | 3 | ON white, OFF black, edge (flip frame). The labels are drawn at runtime |
| T15 | `gui/craps/stick.png` | 48×8 | 1 | Stickman's hooked stick |
| T16 | `gui/craps/flame.png` | 24×8 | 3 | Hot-shooter flame (NICE) |
| T17 | `gui/extras/dice_cup.png` | 96×24 | 4 | Leather cup, shake frames |
| T18 | `gui/extras/duel_badges.png` | 96×24 | 4 | "vs" crossed dice, crack overlay ×2 frames, house stamp (red seal + 3 and 4 pips) |
| T19 | `entity/roulette/wheel_world.png` | 64×64 | 1 | Atlas: head strip (37×4 px pockets), bowl, ball, dolly, for the Java BER and the Bedrock wheel geo |
| T20 | `entity/craps/dice_world.png` | 32×16 | 1 | 6 faces 4×4 + puck ON/OFF 4×4 + rail pyramid |
| T21 | `block/craps/craps_table_top.png` (**update**) | 16×16 | 1 | Adds the 6 point spots with pixel numerals 4 5 6 8 9 10 on the far half |
| T22 | `item/extras/dice_display.png` | 16×16 | 1 | 6 faces 4×4 (+ 4 pad) for the Java `dice_display` cube model |
| T23 | dust cell added to the global particle atlas `particle/burmaldaholic_fx.png` | 8×8 | 1 | Felt dust puff for dice landings |

In total that is **23 texture entries**: 21 new sheets, 1 updated block top, and 1 cell in the global
particle atlas. Together they hold **63 frames/cells**.

### 4.2 Glyphs (both editions: Java `font/default.json` bitmap provider; Bedrock `glyph_E1.png`)

| Code point | Glyph |
|-----------|-------|
| U+E1C0–U+E1C3 | Tumbling die, 4 frames (blank faces with motion lines) |
| U+E1C4 | Red pocket: filled disc |
| U+E1C5 | Black pocket: ring |
| U+E1C6 | Zero pocket: green diamond |
| U+E1C7 | Roulette ball |
| U+E1C8 | Dolly |
| U+E1C9 | Push or bar marker (gray ring with a bar) |
| U+E1CA / U+E1CB | ✔ win tick / ✘ lose cross (if there is no existing glyph) |

That is **12 glyph cells**, and U+E1CC–E1CF are reserved. (extras-pvp.md also claimed E1C0–E1CF for dye
swatches; that clash is resolved by moving the swatches to E1E0–E1EF — `docs/architecture/animation.md` §6.)

### 4.3 Java models and renderers

| File | Kind |
|------|------|
| `client/…/games/roulette/client/RouletteTableRenderer.java` | BER (wheel, ball, badge, dolly) |
| `client/…/games/craps/client/CrapsTableRenderer.java` | BER (rail, dice, puck, badge) |
| `models/item/dice_display.json` + items def `items/dice_display.json` | 6-face cube item model, a hidden item that is not in the creative tab |
| `client/fx/*` (global J1, J7) | Used as is: `Ease` (+ `outBounce`, `expDecay` added here), `Tween`, `FxSettings`, GUI particle pool, `CelebrationOverlay` |
| `client/fx/table/*` (this spec) | `Timeline` (tick + partial), `ChipSprites` (flight, stack, sweep, payout), `BalanceRoller` hook, `ResultBanner` |

### 4.4 Bedrock entity files

| Pack | Files |
|------|-------|
| roulette BP | `entities/roulette/wheel.json` (properties §1.6, `minecraft:physics` off, `damage_sensor` all, `collision_box` 0, `pushable` false) |
| roulette RP | `entity/roulette/wheel.entity.json`, `models/entity/roulette/wheel.geo.json` (bones: root, bowl, head, turret, ball_pivot, ball, dolly), `animations/roulette/wheel.animation.json` (idle_turn, spin, settled_turn, dolly_pop), `animation_controllers/roulette/wheel.ac.json`, `render_controllers/roulette/wheel.rc.json` |
| craps BP | `entities/craps/table_die.json`, `entities/craps/puck.json` |
| craps RP | `entity/craps/table_die.entity.json`, `models/entity/craps/table_die.geo.json` (root, pos, tumble, face), `animations/craps/table_die.animation.json` (**8 throw paths** + duel_pickup + idle), `animation_controllers/craps/table_die.ac.json`; `puck.entity.json`, `puck.geo.json`, `puck.animation.json` (flip_on, flip_off), `puck.ac.json` |
| core RP | `particles/tables/dice_dust.json` (new); `chip_pop`, `chip_fountain`, `sparkle` and `gold_burst` come from global §5.2; `sounds/sound_definitions.json` entries (§0.8) |

That is **16 Bedrock entity files** and **1 new particle definition**, all generated by
`tools/gen-table-anim.mjs` from the pure paths.

### 4.5 Sounds

There are **12 new sound events** (§0.8), plus subtitle keys, in both editions.

---

## 5. Developer task breakdown

The lanes run in parallel. "→" marks a dependency. Each lane owns its files and does not touch the
others. Shared lanes (A*) land first, or behind stub interfaces.

### Java

| Lane | Task | Files | Depends on |
|------|------|-------|------------|
| J-A0 | **Table FX kit:** `client/fx/table/Timeline`, `ChipSprites` (flight, stack, sweep, payout), `ResultBanner`; add `outBounce` / `expDecay` to `Ease`; hook the payout into `CelebrationOverlay` | `java/src/client/java/dev/nezo/burmaldaholic/client/fx/table/*.java` | global J1 (`client/fx`), J7 (`CelebrationOverlay`), S3 (`WinTier`); stub interfaces allow a parallel start |
| J-A1 | **Assets:** a `tables` section in `scripts/gen-fx-assets.py` (T1–T22, T23 cell, glyphs E1C0–E1CB) that writes both editions; `sounds.json` entries (§0.8); lang keys (§6) | `scripts/gen-fx-assets.py`, `src/main/sounds/*/sounds.json`, STRINGS.md + `gen_lang.py` | global S1 (generator skeleton), J3 (Java glyph font) |
| J-R1 | Pure `RouletteBallPath` + `SeedMix` + tests + vector dump | `main/…/games/roulette/logic/RouletteBallPath.java`, `test/…/RouletteBallPathTest.java`, `test/resources/fx/vectors_tables.json` | none |
| J-R2 | Roulette server: `seed`, `spin_start`, `bets[].ret`, `others_res`, BE update tag, remove the server spin sound | `games/roulette/RouletteTableBlockEntity.java` | none |
| J-R3 | `RouletteScreen`: ghost chip, flights, invalid shake, NO_MORE_BETS wipe, wheel panel, dolly, sweep and payout, banners, history insert, reduced motion | `games/roulette/client/RouletteScreen.java` (+ `RouletteWheelWidget.java`) | J-A0, J-A1, J-R1, J-R2 |
| J-R4 | `RouletteTableRenderer` BER + registration in `RouletteClientModule` | `games/roulette/client/RouletteTableRenderer.java` | J-R1, J-R2, J-A1 (BER pattern → J-C4 reuses it) |
| J-C1 | Pure `DiceThrowPath` (wall and no-wall modes, face → rotation table) + tests | `main/…/core/logic/fx/DiceThrowPath.java`, tests | none |
| J-C2 | Craps server: `seed`, `roll_time`, `shooter_dir`, `points_in_row`, `last_res`, `prev_bets`, REVEAL_DELAY on messages, window-after-reveal, BE update tag, bot delay | `games/craps/CrapsTableBlockEntity.java`, `core/bots/…` (craps shooter delay) | none |
| J-C3 | `CrapsScreen`: back wall, chips on the layout, throw overlay, badge, event banner, puck flip and fly, chip resolution, stick-back, seven-out and point-made beats | `games/craps/client/CrapsScreen.java` (+ `CrapsPuck.java`, `CrapsDiceLayer.java`) | J-A0, J-A1, J-C1, J-C2 |
| J-C4 | `CrapsTableRenderer` BER | `games/craps/client/CrapsTableRenderer.java` | J-C1, J-C2, J-R4 pattern |
| J-D1 | Dice Duel server: `rounds`, `seed`, delayed chat per round, `DuelStage` (ItemDisplay dice) | `games/extras/server/DiceGame.java`, `games/extras/server/DuelStage.java`, `models/item/dice_display.json` | J-C1 |
| J-D2 | `DiceScreen` two-lane redesign + `DuelInviteScreen` ring timer | `games/extras/client/DiceScreen.java`, `DuelInviteScreen.java`, `Art.java` (remove random faces) | J-A0, J-A1, J-C1, J-D1 |
| J-T | GameTests: reveal delay (chat after 32 t), BE sync tags, duel rounds payload; the faithfulness unit tests from §0.6 | `src/gametest/…/craps`, `…/independent` | J-R2, J-C2, J-D1 |

### Bedrock

| Lane | Task | Files | Depends on |
|------|------|-------|------------|
| B-A0 | **Table FX helpers:** `soundTimeline(dim, pos, events)`, `tumbleStrip()`, `revealAfter(ticks, fn)`, the spin-camera setting row (NICE) | `bedrock/src/core/fx-table.ts`, `core/logic/fx/*` | global B1 (`FxService`, settings, `celebrate`) |
| B-A1 | **Assets:** Bedrock output of J-A1's generator section (T19–T21, T23, glyphs), `sound_definitions.json` entries, `dice_dust` particle, lang via `gen-lang.mjs` | `packs/core/RP/**`, `packs/{roulette,craps,extras}/RP/**`, `bedrock/lang/**` | J-A1 (the same script) |
| B-R1 | Pure `roulette/logic/ball-path.ts` + `core/logic/fx/seed.ts` + vitest against the shared vectors | `bedrock/src/games/roulette/logic/ball-path.ts`, `*.test.ts`, `bedrock/test/fx/vectors_tables.json` | J-R1 vectors (or a stub) |
| B-R2 | Wheel entity: generator `tools/gen-table-anim.mjs` (emits the molang for §1.3) + BP/RP files (§4.4) | `bedrock/tools/gen-table-anim.mjs`, `packs/roulette/**` | B-R1, B-A1 |
| B-R3 | `roulette/table.ts`: wheel lifecycle, property writes, sound timeline, glyph strip, result title and tiers, per-bet form lines, `chip_place` on bets | `bedrock/src/games/roulette/table.ts`, `text.ts`, `logic/animation.ts` (use ball-path) | B-A0, B-R1, B-R2 |
| B-C1 | Pure `core/logic/fx/dice-path.ts` (port of J-C1) + the die and puck entity files generated by `gen-table-anim.mjs` (8 paths) | `bedrock/src/core/logic/fx/dice-path.ts`, `packs/craps/**` | J-C1 vectors, B-A1 |
| B-C2 | `craps/runtime.ts`: dice and puck lifecycle, timeline, delayed messages, titles, tiers; bot shooter delay on the bots branch | `bedrock/src/games/craps/runtime.ts`, `craps/bots.ts` (rebase onto `worktree-agent-aaf0f54e81d19ae4a`) | B-A0, B-C1 |
| B-D1 | `extras/dice-game.ts` + `extras/duel-stage.ts`: in-world duel dice (house + PvP), round titles, delayed chat and form | `bedrock/src/games/extras/dice-game.ts`, `duel-stage.ts` | B-A0, B-C1 |
| B-N | NICE: spin camera (B-N1), JSON UI result panel (B-N2), chip stack entity (B-N3) | `roulette/table.ts`, `packs/core/RP/ui/**` | the MUST lanes |

Critical path: global J1/J7/B1, then J-A0/B-A0, together with the pure paths (J-R1, J-C1), then the screens
and entities. The pure paths, the server-data lanes (J-R2, J-C2, J-D1) and the asset section can start
on day 1.

---

## 6. New strings (STRINGS.md format)

Russian lengths were checked against the 1.45 × EN budget. The banners wrap at 160 px (Java). On
Bedrock, the titles are ≤ 18 RU characters, and the subtitles reuse existing keys.

### core: setting (the rest of the FX settings and the tier words belong to global.md §9)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.menu.settings.spin_camera` | Spin camera | Камера на колесо |
| `gui.burmaldaholic.menu.settings.spin_camera.tooltip` | Roulette: the camera looks at the wheel during your spin. Any movement cancels it. | Рулетка: во время вашего вращения камера смотрит на колесо. Любое движение отменяет. |

Reused from global.md §9: `gui.burmaldaholic.menu.settings.reduce_motion` and `flashes`,
`gui.burmaldaholic.fx.tier.*`, and `gui.burmaldaholic.fx.nearby_float` (for "Alex +360" over another
player's winning stack).

### roulette

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.roulette.fx.straight_up` | Straight up! | Прямое попадание! |
| `gui.burmaldaholic.roulette.fx.last_bets` | Last bets! | Последние ставки! |
| `gui.burmaldaholic.roulette.fx.bet_placed` | %1$s on %2$s | %1$s на «%2$s» |

### craps

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.craps.fx.hot_shooter` | Hot shooter! %1$s points in a row | Горячая рука! Поинтов подряд: %1$s |
| `gui.burmaldaholic.craps.fx.new_shooter` | New shooter: %1$s | Новый бросающий: %1$s |
| `gui.burmaldaholic.craps.fx.bar_push` | Bar 12 — push | Бар на 12 — возврат |
| `gui.burmaldaholic.craps.fx.throwing` | %1$s throws… | Бросок: %1$s… |

### extras (Dice Duel)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.extras.dice.fx.tie_reroll` | Tie! Re-roll %1$s of %2$s | Ничья! Переброс %1$s из %2$s |
| `gui.burmaldaholic.extras.dice.fx.round` | Round %1$s | Раунд %1$s |
| `gui.burmaldaholic.extras.dice.fx.victory` | Victory! | Победа! |
| `gui.burmaldaholic.extras.dice.fx.defeat` | Defeat | Поражение |
| `gui.burmaldaholic.extras.dice.fx.shake` | Shake the cup… | Трясём стакан… |

### sounds (subtitles)

| Key | EN | RU |
|-----|----|----|
| `subtitles.burmaldaholic.roulette_ball_roll` | Roulette ball rolls | Катится шарик рулетки |
| `subtitles.burmaldaholic.roulette_ball_drop` | Ball hits a deflector | Шарик ударяется о ромб |
| `subtitles.burmaldaholic.roulette_ball_bounce` | Ball clatters | Шарик стучит по лункам |
| `subtitles.burmaldaholic.roulette_ball_settle` | Ball settles | Шарик останавливается |
| `subtitles.burmaldaholic.roulette_bell` | Croupier's bell | Звонок крупье |
| `subtitles.burmaldaholic.roulette_dolly` | Marker placed | Ставится маркер |
| `subtitles.burmaldaholic.chip_sweep` | Chips raked in | Фишки сгребаются |
| `subtitles.burmaldaholic.dice_throw` | Dice thrown | Бросаются кости |
| `subtitles.burmaldaholic.dice_bounce` | Dice bounce | Кости подпрыгивают |
| `subtitles.burmaldaholic.dice_wall` | Dice hit the wall | Кости бьются о борт |
| `subtitles.burmaldaholic.dice_cup` | Dice rattle | Гремят кости в стакане |
| `subtitles.burmaldaholic.craps_puck` | Puck placed | Ставится шайба |

That is 26 new keys: 2 core, 3 roulette, 4 craps, 5 dice and 12 subtitles. Existing keys that are
reused are cited inline in §1–§3. For the Bedrock `.lang` files, `%N$s` becomes `%N` (LOCALIZATION.md
§2).
