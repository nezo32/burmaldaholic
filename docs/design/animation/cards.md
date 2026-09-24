# Card-game animation spec: Blackjack, Texas Hold'em, Ultimate Texas Hold'em, Baccarat (+ Chemin de fer)

Scope: presentation only, for Blackjack (GAME_DESIGN §6, UI.md §4), Texas Hold'em (§7, UI.md §5),
Ultimate Texas Hold'em (§21, UI.md §15) and Baccarat / Chemin de fer (§20, UI.md §14), in both
editions. It covers card dealing arcs, flips, the baccarat squeeze, the poker showdown, chip stacks,
pushes and pot slides, dealer gestures, and how bot seats look. Rules, odds and payouts do not change.
Where an animation needs data or pacing that the server does not provide yet, the change is listed
under "Server data" as a dependency. Every visible outcome comes from the server.

Status: implementation-ready, 2026-09-24.

**Inputs and alignment**
- `docs/research/animation.md`, which covers the capabilities of each edition. Where this spec depends
  on it, the section number is given in brackets, for example (R§2.6).
- The sibling spec `docs/design/animation/global.md` **owns** these shared pieces, and this file calls
  them rather than redefining them:
  - `WinTier` (global §2.4)
  - the celebration kit (`CelebrationOverlay`, `fx.celebrate`, global §2.6)
  - the easing names (global §2.5)
  - the sound palette (global §2.7)
  - the FX settings (global §2.8: reduce motion, flashes, speed, celebrations, volume)
  - the bot thinking glyphs U+E191–E193 (global §2.3, §4.12)
  - the asset generator (task S1)
- `docs/design/animation/tables.md` defines the **chip visuals** (§0.4: flight, 5-disc stacks, seat
  tint, sweep, payout) and the **shared-time vs local-time** rule (§0.1). Card tables reuse both, so the
  whole casino looks like one product.
- Code read on the current branch (`claude/burmaldaholic-casino-mode-w6dejw`):
  - Java: `BlackjackScreen`, `PokerScreen`, `UthScreen`, `BaccaratScreen`, `CasinoTableScreen`, the
    three `*DealerRenderer`, and the four `*TableBlockEntity` for timers and sounds.
  - Bedrock: `games/{blackjack,poker,uth,baccarat}/**`, `core/hud.ts`, `core/logic/glyphs.ts`, and the
    dealer entity packs.
  - The newest bots code, from branch `worktree-agent-aaf0f54e81d19ae4a`: `core/logic/bots/display.ts`,
    `core/bots/table-bots.ts` and `games/*/bots.ts`.
  - Java bots, from branch `worktree-agent-afbccfe640358d8fc`.

**Contents**
- §0 Shared card-table kit: timeline and pacing, motion primitives, chips, tiers and moments,
  accessibility, faithfulness, performance, assumptions
- §1 Blackjack
- §2 Texas Hold'em
- §3 Ultimate Texas Hold'em
- §4 Baccarat and Chemin de fer, including the squeeze
- §5 Bot seats (all four games)
- §6 Assets, with counts
- §7 Sounds
- §8 Server data and config keys
- §9 Developer task breakdown in parallel lanes
- §10 MUST / NICE summary
- §11 New strings (STRINGS.md format)

---

## 0. Shared card-table kit

### 0.1 Audit summary (all four games)

| | Java today | Bedrock today |
|---|---|---|
| Cards | Flat `fill` rectangles. The rank is drawn with the font, the suit symbol in the corner, and the back is a dot pattern. There are **three different card sizes and three back colours** across the screens (BJ 18×24 blue, baccarat 18×24 burgundy, poker 13×17 / 20×28 red, UTH red). | Card glyphs U+E110–E144 in form bodies and on the action bar. |
| Dealing | None. Cards **appear** the moment a state packet arrives. The blackjack server deals the whole round and plays the dealer **in one tick**: every card is published at once, and the result follows 0 ticks later. | Same. The form body shows the finished hands. |
| Flip / reveal | None. Baccarat is the only exception: it reveals card by card from `reveal_left` (P1, B1, P2, B2 at `revealTicks/8` steps). This is the one good, server-driven pattern, and it is the model for everything below. UTH has 20 t steps per street. | Baccarat: an action-bar ticker (`revealFrames`), 10 t per card. Other games: none. |
| Chips | Text amounts only ("Bet: 100"). No chip visuals, no pushes, no pot. | Text only. |
| Showdown | Poker: the board, pots and hand names are text. No order, no best-5 highlight, no pot slide. | A showdown form body. |
| Dealer NPCs | `BlackjackDealerRenderer`, `BaccaratDealerRenderer`, `UthDealerRenderer`: a static humanoid that looks at players. No gestures. Poker has no dealer. | `geometry.humanoid.custom` + `look_at_target` only. |
| Sounds | `card_deal` (vanilla page turn), `card_shuffle`, `chip_place` (baccarat and UTH only). Blackjack and poker play **no** card sounds. | Baccarat: `random.click` on a bet. **No other card-game sound.** |
| Spectators | Nothing in the world. GAME_DESIGN §18.1 promises "Java: render over the table", but it is not built. There are no BERs. | Baccarat and UTH: an action-bar summary. |
| Bots | Poker plates show a bot tier tag. Blackjack, baccarat and UTH atmosphere bots are on the bots branch (Bedrock) with no special visuals. | `[BOT]` glyph and name in forms and on the action bar. |
| Results | A text banner line in colour. Wins, losses and pushes differ only by colour and word. No celebration, and the HUD `+N` floater can arrive **before** the dealer's cards are visible (a spoiler). | A chat line and the result form. |

**Verdict:** the card games are informative, but they have no motion at all. The biggest gains, in
order:
1. Pace the deal on the server so the cards can move.
2. Add deal arcs and flips.
3. Add chips that visibly move.
4. Add the baccarat squeeze and the poker showdown sequence.
5. Add the in-world table view (Java BER) and dealer gestures.

### 0.2 Timeline model: "server paces, client tweens"

- **Beats.** For each game, the server publishes each visible card change as its own **beat**: a card
  dealt, a card flipped, chips moved or a pot awarded. It sends one state update per beat, and beats
  are at least `beat` ticks apart (the config is in §8).
  - This is the same pattern as the existing baccarat `reveal_left`.
  - The game logic still runs at once. The round is drawn and persisted as now (GAME_DESIGN §4.1).
    Only the **publication** of each card is scheduled.
- **The client tweens between published states.** Both Java screens and the BER diff the new state
  against the old one, per card slot:

  | Change | Animation |
  |---|---|
  | empty → back | deal arc, face down |
  | empty → face | deal arc, then flip |
  | back → face | flip (baccarat: squeeze, when that slot is flagged `sq`) |
  | card → gone | gather to the discard tray, or muck |
  | bet stack changed | chip flight or sweep (tables §0.4) |

  The Bedrock ticker (§0.9) derives the same frames from the same beats.
- **Beat schedules** are **pure functions** with identical ports and shared test vectors:
  - Java `games/<g>/logic/<G>Beats.java`, Bedrock `games/<g>/logic/beats.ts`.
  - Input: the drawn round (card counts, who has a natural, who is all-in and so on) plus config.
  - Output: `[{at: tick, kind, slot}]`.
  - Test vectors live in `java/src/test/resources/fx/vectors_cards.json`. They are generated by the Java
    test and copied to `bedrock/test/fx/`, as in tables §0.1.
- **Shared time vs local time** (tables §0.1):
  - Shared time: deal, flip, squeeze, showdown order and pot award order. Every viewer sees these at
    the same moment. They ignore `anim.speed` and skip. Reduce motion changes only *how* they look,
    never *when*.
  - Local time: chip payout flights, banners and the tier celebration. These respect `anim.speed`, and
    a click or sneak skips them.
- **Catch-up:** a viewer who opens the screen or walks into range mid-round snaps to the current state.
  A tween is played only when `now − beatTick < 2 × duration`; otherwise the state is drawn settled.
- **Overrun:** if a new beat arrives while its slot is still tweening, the running tween jumps to its
  end in ≤ 120 ms, then the new tween starts.
- **Single-seat fast deal (MUST):** when exactly one human is seated and no spectator is within
  `multiplayer.spectatorRadius`, the server uses `beat × cards.soloSpeed` (0.75).
  - The Deal/Hit button **during** a beat (Java) or sneak (Bedrock) sends `skip_anim`. The server may
    then publish the remaining beats of the current deal immediately. It never skips a decision timer.
  - Shared tables never compress.

### 0.3 Motion primitives (Java GUI; the BER and Bedrock map these in their own sections)

Easing names are from global §2.5 and tables §0.2. 1 t = 50 ms. Sizes are in GUI px at GUI scale 1.

**Card sizes** (one size set for all four screens, replacing the three current ones):

| Size | Px | Use |
|---|---|---|
| L | 24×34 | the viewer's hands, the board, both baccarat hands, the dealer's hand |
| M | 16×22 | other seats' hands in blackjack and UTH, seat plates in poker |
| S | 11×15 | compact mode (UI.md §0.2) and the side-pot / muck icons |

The rank index is drawn **at runtime** with the font from `gui.burmaldaholic.card.rank.*` (RU Т/К/Д/В)
in the top-left corner and rotated 180° in the bottom-right; L size only. Faces carry pips and court art
only (§6), so nothing is baked in as text.

| Id | Primitive | Duration | Curve and geometry | Sound |
|---|---|---|---|---|
| K1 | **Deal arc** | 240 ms (poker 200 ms) | Position runs along a quadratic Bézier from the shoe S to the slot T. The control point is the midpoint − (0, 22 px) (the arc rises), with `outCubic` on t. Rotation goes −14° → 0°, with `outBack` over the last 30 %. Scale goes 0.82 → 1.0 `outQuad`. The shadow (`cards/shadow`, alpha 0.35 → 0.22) is offset (4,4) → (1,1). The card flies **face down**. A cosmetic jitter of ±2° and ±1 px, seeded by `(roundSeq, slot)`, keeps rows from looking robotic. | `card_slide` at the start (vol 0.3); `card_deal` on landing, pitch 0.95–1.05 (seeded) |
| K1b | Landing settle | 60 ms | y +1 px, then back, `outQuad`; the shadow tightens | — |
| K2 | **Flip** | 280 ms | Phase A (0–140 ms): scaleX 1 → 0 (`inSine`), y −4 px (`outSine`), shadow +1 px. The sprite **swaps back → face at scaleX = 0**. Phase B (140–280 ms): scaleX 0 → 1 (`outBack`, s = 1.3, peak 1.05), y back to 0 (`inOutQuad`). The flip axis is vertical. The face gets a white 20 % highlight that fades over phase B. | `card_flip` at 120 ms |
| K1+K2 | Deal face-up | 240 + 280 ms | The flip starts on landing (K1b is skipped), so a face-up card is readable 520 ms after its beat. Consecutive beats (300 ms) overlap, which reads as a smooth dealer rhythm. | both |
| K3 | **Squeeze** (baccarat) | `squeezeTicks` × 50 ms (default 1 000 ms) | See §4.3 | `card_squeeze` at the start, then `card_flip` at the snap |
| K4 | Slide (reposition: split, fan re-layout) | 200 ms | `outCubic` | — |
| K5 | **Gather** (end of round) | 280 ms per card, 25 ms stagger | Every card slides to the discard tray (blackjack, baccarat) or to the muck at the dealer spot (poker, UTH), `inCubic`. It tilts 0 → 8°, fades 1 → 0 over the last 80 ms, and faces flip to backs mid-flight (a 60 ms squash, not a full K2). | `card_gather` once |
| K6 | **Muck / fold** | 300 ms | Face-down cards slide toward the muck with `inCubic`, rotate 25°, and fade over the last 120 ms. The plate dims to 55 % over 200 ms. | `card_gather` (vol 0.4) |
| K7 | **Stamp** (BUST, NATURAL, BLACKJACK!, ALL-IN) | 220 ms in, then held | A nine-slice stamp sprite with runtime text scales 1.4 → 1.0 (`outBack`) and rotates −6°, alpha 0 → 1. Colour: BUST `chip.red`, NATURAL/BLACKJACK `gold`, ALL-IN `gold` on `chip.red`. | per stamp (§7) |
| K8 | **Shimmer** (blackjack, natural 9, royal, best-5 of a monster pot) | 420 ms | A diagonal white band (`fx/shimmer_band`, 8 px wide, 35 % alpha) sweeps left → right across the named cards, clipped with a scissor to the hand's bounding box, `inOutQuad`. Played once, never looped. | — |
| K9 | **Glow ring** (active hand, best-5, winning hand) | loop 1 Hz | The `cards/glow` sprite (4-frame `.mcmeta`) sits behind the card. Alpha 0.35 ↔ 0.8 with `inOutSine`. | — |
| K10 | **Total badge roll** | 150 ms | When a hand total changes, the old digits slide up 6 px and fade while the new ones slide in from below, clipped with a scissor. The badge (`tables/total_badge`, nine-slice) is `bone` on `ink`. Soft totals show "7/17". | — |
| K11 | **Desaturate** (bust, losing hand) | 300 ms | Card tint lerps to `0xFF8A8A8A` and the hand dims to 70 %. | — |
| K12 | **Action tag** (poker, UTH, bots) | 180 ms in, 1 500 ms hold, 300 ms out | A small nine-slice bubble (`tables/tag`) above the seat plate slides up 6 px with `outCubic`, then fades. Text: `gui.burmaldaholic.poker.tag.*` / `uth.tag.*`. A new tag replaces the old one at once. | — |
| K13 | **Knock** (check) | 180 ms | The plate jumps 2 px down and back twice (knuckle raps). | `table_knock` |

**Where cards come from.** The shoe is drawn as a sprite and is the start point S for K1:
- Blackjack: top-right of the dealer area.
- Baccarat: top-centre, between the two hand panels.
- Poker and UTH: a deck sprite at the dealer spot (top-centre).

### 0.4 Chips, pots and stacks

Chips follow tables §0.4, so this section lists only what that section does not cover:
- **Chip flight:** 180 ms Bézier with an apex scale of 1.25, a landing squash, and `chip_place`.
- **Stacks:** up to 5 discs, then a value label.
- **Other players' chips** use the seat tint.
- **Sweep:** 260 ms `inCubic` toward the house side.
- **Payout:** discs pop in from the house side at 40 ms each, up to 8 at most, then the stack flies to
  the balance.

Card-table additions:
- **Bet spots.** Every seat has a bet circle (`tables/bet_circle`, 22×22). The circle hovers when a
  chip is selected and the spot accepts a bet.
  - UTH has four spots per seat: Trips, Ante, Blind and Play.
  - Baccarat has five shared boxes. Every bettor's stack sits inside the box as a small seat-tinted
    stack, with at most 4 stacks per box and a "+N" label beyond that.
- **Dealer rack.** The source of payouts and the destination of sweeps (`tables/rack`, 64×12) sits at
  the top of the felt.
- **Poker bet line.** Each plate has a bet spot between the plate and the pot, placed at 35 % of the way
  from the plate to the centre.
  - **Gather:** at the end of a street every spot slides to the pot in 320 ms with `outCubic`, staggered
    50 ms clockwise from the button. The pot stack bumps (scaleY 1.1 → 1, 80 ms), and the pot label
    rolls from the old value to the new one over 300 ms with `outCubic` (numbers are monotonic).
  - **Side pots** form as separate stacks next to the main pot, one per `pots[i]`, left to right. Each
    has its own label, "Side pot 1: 120", from the existing key.
  - **Award:** see §2.2 SHOWDOWN. Pots move **side pots first**, in the order the server resolves them
    (GAME_DESIGN §7.3 "side pots outwards"), 400 ms apart. Each pot slides to its winner's plate in
    500 ms with `inOutCubic`.
  - **Split pot:** the stack divides into N stacks that fan out at the same time. The odd chip goes to
    the server-named seat.
  - The plate stack number rolls up over 300 ms. Sound: `pot_win`.
- **All-in push:** every chip in front of the seat slides to its spot at 1.2× scale with an `outBack`
  landing, and the ALL-IN stamp (K7) appears on the plate. Sound: `chip_push`.
- **Push (tie):** the stack wiggles ±1 px twice in 200 ms, stays for 400 ms, then flies back to its
  owner (tables §0.4 "push").
- **Atmosphere bots' chips** (blackjack, baccarat, UTH; BOTS.md §8.1) use the **hatched chip**
  overlay (`fx/chip_hatched`). They never fly to or from the viewer's balance. They sweep and pay
  from the rack like real chips, so the table looks alive, but they carry the hatch pattern.
- **Stack size is decoration.** Every stack carries its exact server amount as a runtime label
  (`Texts.number`). The viewer's own stacks always show the label; other players' labels appear on
  hover.

### 0.5 Win tiers and card "moments"

- The **tier** is `WinTier.of(net, stake, flags)` (global §2.4).
  - It is computed by the server at settlement, per viewer, over **all of that viewer's bets in the
    round**.
  - It is sent with the result. The client never recomputes it.
  - The celebration is the global kit (§2.6). It starts **after** the viewer's payout chips have
    reached the balance (local time), as in tables §0.3.
- **Moments** are card-specific accents: a stamp, a shimmer and a sting. They play **at the table
  beat where the event becomes public** (shared time). **They never change the tier**, and a losing
  round never gets a celebratory moment.

| Moment | When | Visual | Sound |
|---|---|---|---|
| Blackjack (natural) | after the peek, when the player's BJ is settled | K8 shimmer on both cards + K7 "BLACKJACK!" (gold) | `card_sting` |
| 21 (non-natural) | the hand reaches 21 | K9 glow flash once (alpha ≤ 30 %), "21" badge in gold | `chip_count` pitch 1.5 |
| Dealer busts | the dealer's final card | the dealer's cards K11; "Dealer busts with 24!" line in `bonus` | — (payouts follow) |
| Baccarat natural 8/9 | the natural is announced | K7 "Natural 9" stamp on that hand + K8 | `card_sting` |
| Baccarat tie | the result beat when the result is a tie | both hand panels glow green; "Tie pays!" stamp **only for viewers with a Tie bet** | `card_sting` (Tie bettors only) |
| Poker big pot (≥ `poker.fx.bigPotBb` 50 BB) / monster (≥ 100 BB) | the award beat | "Big pot!" / "Monster pot!" stamp over the pot before it slides | `win_big` layer (vol 0.5) |
| UTH Blind bonus (straight or better) / Trips bonus | settlement | the bonus circle gets K9 + K7 "Blind bonus!" / "Trips bonus!" | `card_sting` |
| Royal flush (UTH, poker) | settlement / showdown | K8 on the 5 cards, repeated 3 times 300 ms apart; all five get K9 | `card_sting` + `win_mega` layer |

- **Tier flags requested from global §2.4** (the global owner decides):
  - `topPrize` for a UTH royal flush that pays the Blind (500:1). This lifts the tier to `JACKPOT`
    and matches the existing `diamond_rain` and `royal_broadcast`.
  - Until the flag is accepted, the royal plays as `EPIC` or whatever the multiple gives.
- **Loss disguised as a win** (global `RETURN`) matters most for:
  - UTH: the Blind wins and the Play loses, or the Ante pushes and the Blind pays a little.
  - Baccarat: a Tie with only Player/Banker bets gives a push. A pair wins while the main bet loses.

  When the server tier is `RETURN`, the table shows the returned chips sliding back (tables §0.4), the
  gray label "Returned N" (global key) and `push`. There is no gold, sparkle or sting, even when a
  bonus circle paid.

### 0.6 Accessibility

The toggles are global §2.8: reduce motion, screen flashes, speed, celebrations and volume. This spec
adds **no new personal toggle** in MUST.

| Effect | Default | Reduce motion | Flashes off |
|---|---|---|---|
| K1 deal arc | arc + rotation + scale | 120 ms fade-in at the slot (no travel) | unchanged |
| K2 flip | scale flip | 100 ms cross-fade back → face | unchanged |
| K3 squeeze | peel from the edge | the card stays face down with a thin progress bar under it (`tables/progress`, 1 px, fills linearly over the same window), then a 100 ms cross-fade. The **timing is identical**. | unchanged |
| K5/K6 gather, muck | slide + tilt | 150 ms fade in place | unchanged |
| K7 stamp | scale-in 1.4 → 1 | fade-in 120 ms, no rotation | unchanged |
| K8 shimmer | sweep band | none | none |
| K9 glow ring | 1 Hz pulse | static, alpha 0.6 | static, alpha 0.5 |
| K13 knock | 2 px jumps | none (the sound only) | unchanged |
| Chip flights, sweeps, pot slides | Bézier / slides | 120 ms fade at the destination; the pot label still rolls up | unchanged |
| Dealer gestures (Java NPC, Bedrock entity) | full | arm moves shortened to 50 % amplitude | unchanged |
| BER in-world arcs and flips | full | cards appear at their slot and flip in place | unchanged |
| Bedrock ticker frames | flight → narrow → edge → face | back → face (no intermediate glyphs) | unchanged |
| Bedrock squeeze title (§4.6) | peel glyph frames | back glyph held, then face | unchanged |
| Bedrock particles | full | ×0.3 (global) | no `sparkle` twinkle |

Hard limits in every mode:
- No element flashes more than 3 times per second.
- There is no full-screen flash in card games outside the global EPIC/JACKPOT overlay.
- Every result carries **words** as well as colour (existing banner keys).
- Suits have distinct shapes, and the red suits get a 1 px outline variant in the face art so they
  still differ from black suits in greyscale.

**Narration (Java).** Each beat that changes public card state calls the narrator:
- a card lands: `…cards.narrate.dealt` ("Alex receives the ten of hearts")
- a hole card is revealed: `…cards.narrate.shows` ("Dealer shows the queen of spades")
- a result lands: the existing result keys

Beats are coalesced to at most one narration per 600 ms (a round's deal is read as "Cards dealt", then
the viewer's own cards).

**Readability first.** A card's face is fully readable at the end of K1+K2 (520 ms) and never hidden
again until the gather. Totals are shown the moment the card is readable.

### 0.7 Faithfulness contract (tested)

1. **No face before publication.**
   - Java: the server state for a viewer contains only the cards that viewer may see (as today:
     `dealer.subList(0,1)` until the reveal). The BER's public tag contains only public cards; hole
     cards appear as back placeholders with no identity.
   - Bedrock: bodies and tickers show `CARD_BACK_GLYPH` until the reveal beat.
   - Test: build the state at every beat of 1 000 random rounds and assert that the hidden cards are
     absent from it.
2. **Frames interpolate only between published states.** A deal arc shows only a back. A flip swaps
   to the real face. Burn cards stay face down (the baccarat burn indicator card is the one exception:
   it is shown face up, as the rules require).
3. **Timing never depends on hidden information.**
   - Beat schedules take only public counts: the number of cards, naturals (public at the beat where
     they show) and all-ins.
   - The squeeze window is the **same for every card**, whatever its value.
   - The run-out pause is the same for every street.
   - The UTH dealer-qualify pause is the same whether or not the dealer qualifies.
   - Test: for random rounds, the schedule is identical under any permutation of the unrevealed cards.
4. **Text and money visuals trail the cards.**
   - Result banners, per-bet lines, chat result lines, Bedrock result forms, titles and the tier
     celebration are posted at or after the **reveal gate** (`gateTick` = the last card beat + its
     animation length, §8).
   - Economy settlement stays immediate for restart safety (GAME_DESIGN §4.1), as in tables §0.6.3.
5. **HUD spoilers are suppressed.**
   - Java: while `ClientTableCache` holds a table with `gateTick > now` that the player sits at, the
     HUD balance ticker and floater (global §4.1.1) **hold** the old value and apply the delta at
     `gateTick`. This happens whether or not the screen is open.
   - Bedrock: `ctx.hud` suppresses the status line and the `+N` suffix for that player until the gate.
6. **Payout visuals use the server's per-bet returns** (`bets[i].ret` in the result). The client never
   derives a payout.
7. **Squeeze honesty.** The squeeze reveals the **real** face, progressively, from one edge. The pips
   that become visible are the real pips. There are no fake pips and no teasing reversals: the peel
   fraction is monotonic.
8. **Cosmetic randomness** (jitter, shimmer phase) is seeded by `(tableKey hash, roundSeq, slot)` only.
   The game RNG and the bot RNG are never used.
9. **Bots:** their visuals are identical to humans' (§5). Thinking indicators do not correlate with the
   hand. The only exception is the documented EASY poker tell, which is a delay only (BOTS.md §7.3).
10. **Skipping** (local-time beats) always ends on the same final state.

### 0.8 Performance budget

| Item | Budget |
|---|---|
| Java GUI per frame | ≤ 400 extra quads (tables §0.7). ≤ 24 cards tweening at once (a poker deal with 6 seats has 12 in flight at worst). Tween objects are pooled per screen, with no per-frame allocation. GUI particles come from the global pool (≤ 96). |
| Java BER per card table | ≤ 200 quads: ≤ 24 cards × 2 quads + ≤ 60 chip discs × 2 quads + 8 text runs. `getViewDistance()` 32. Tweened within 20 blocks, drawn settled from 20 to 32 blocks, not drawn beyond 32. Text (totals) only within 8 blocks. |
| Java server | One BE state update per beat. That is ≤ 4 updates per second per table and ≤ 40 per round (a 6-seat poker hand), sent only to players that track the chunk. The public tag is ≤ 1 KB. |
| Java dealer NPC | 1 synced-data change per gesture (≤ 1 per beat) |
| Bedrock script | One `system.runInterval` per game (existing), with no per-player loops. Ticker action-bar updates ≥ 2 t apart per player (PVP.md §14) and ≤ 3 frames per beat. Squeeze titles: 5 `setTitle`/`updateSubtitle` calls per squeezed card per seated player. |
| Bedrock sounds | ≤ 8 `playSound` per second per table, as one `dimension.playSound` at the table (not per player) for table sounds; per-player sounds only for private cards |
| Bedrock particles | ≤ 16 per settlement per seat, ≤ 60 per BIG+ event (global) |
| Bedrock entities (NICE §1.8) | ≤ 1 `card_hand` entity per visible hand (blackjack 6, poker 7, UTH 8, baccarat 2), AI-free, despawned 200 t after the table goes idle |

### 0.9 Bedrock ticker frames (shared by all four games)

Forms cannot animate (R§3.8). During shared-time beats **no form is open for the players involved**:
forms close at the start of the beats and re-open at the gate. The table is told through an action-bar
**ticker line** (HUD channel `<game>.table`, priority `game`). The ticker is built from glyphs, and each
card slot runs these frames:

| Transition | Frames (2 t each unless noted) |
|---|---|
| deal face down | `▭` slot U+E1D4 → flight U+E1D5 → back U+E144 |
| deal face up | slot → flight → back → narrow back U+E1D0 → edge U+E1D1 → face |
| flip | back → narrow back → edge → face |
| squeeze (baccarat) | back → peel ¼ U+E1D2 (6 t) → peel ½ U+E1D3 (6 t) → hold (4 t) → edge → face = 20 t |
| muck / gather | the card is replaced by U+E1DB (muck) for 4 t, then removed |

- Totals follow a card only after its face frame.
- The ticker is also sent to **spectators** within `multiplayer.spectatorRadius` at priority
  `ambient`.
- Sounds are played once per beat with `dimension.playSound` at the table (§7).
- Reduced motion skips the intermediate frames (§0.6).

### 0.10 Capabilities used and assumptions

Confirmed by the research:
- Java: GUI sprites with nine-slice and animated `.mcmeta` (R§2.2). The `pose()` Matrix3x2f scale and
  translate for flips, and `enableScissor` for the squeeze and shimmer (R§2.1).
- Java BER API (R§2.6): `extractRenderState` / `submit`, and `submitCustomGeometry` for card quads.
  The public state comes from `getUpdateTag`.
- Bedrock: `entity.playAnimation` for dealer gestures (R§3.1), and `onScreenDisplay.setTitle` /
  `updateSubtitle` for the squeeze title and the run-out subtitle (R§3.6). Custom particles with
  `MolangVariableMap` (R§3.4), and `dimension.playSound` (R§3.5).

Assumptions (verify during implementation):
- Bedrock `geometry.humanoid.custom` bone names are `rightArm`, `leftArm`, `head` and `body`, and
  `playAnimation` on a mob with `look_at_target` blends correctly with `blendOutTime` 0.1.
- The Bedrock title renders glyphs at about 2.5× (large enough for the squeeze card). If it does not,
  the squeeze uses the subtitle line and the title shows the side name.
- Glyph code points **U+E1D0–U+E1DF are free** and are reserved here. Already claimed: E100–E19B
  (UI.md, global), E1A0–E1AB (extras-pvp), E1B0–E1B8 (extras-pvp) and E1C0–E1CF (tables).

---

## 1. Blackjack

### 1.1 Audit

- **Java `BlackjackScreen`** (320×232):
  - Works: the layout is clear and RU-safe (flow rows), the viewer's hand is large, and the soft totals
    (`7/17`) are good.
  - Ugly or missing:
    - The cards are flat fills.
    - The dealer's whole draw appears at once with the result, so the dealer's suspense is lost
      entirely.
    - No chips.
    - The split, double and bust states are text only.
    - The insurance box is a text field.
    - No sounds except the server shuffle notice.
    - The "Dealer peeks" notice is text only.
- **Java server:** `onTick` resolves DEAL → TURNS / INSURANCE and DEALER → SETTLE in the same tick.
  RESULT lasts 60 t, which is too short for payouts.
- **Java dealer NPC:** a vanilla humanoid pose; the arms never move.
- **Bedrock:** the forms show the table with glyphs. The deal is not visible at all (the turn form
  opens with the cards already there), there are no sounds, and the dealer entity is idle.

### 1.2 Java screen storyboard

The layout keeps UI.md §4 and adds:
- the shoe (`tables/shoe`, 28×22) at the top-right;
- the discard tray (`tables/discard`, 20×14) at the top-left;
- the dealer rack under the dealer's hand;
- one bet circle per hand in front of it;
- the viewer's seat at the bottom centre, with other seats as M-size rows (existing).

Beat config: `blackjack.fx.dealBeatTicks` 6, `blackjack.fx.dealerDrawTicks` 14,
`blackjack.fx.holeFlipTicks` 10. RESULT becomes 80 t.

| Phase | t (ms, shared) | Beat |
|---|---|---|
| BETTING | local | Chip click: a chip flies to the viewer's circle (tables §0.4). Others' bets: a tinted chip flies from their plate edge (180 ms). **Deal** pressed: the circle rim flashes gold once (alpha ≤ 30 %, 200 ms). Timer: the existing ring; under 5 s it turns `chip.red`, and `wheel_tick` (p 1.6, v 0.3) plays once per second **for the viewer only while they have no bet yet**. |
| DEAL | 0, 300, 600 … (one card per beat) | Order as in GAME_DESIGN §6.3: seats 1→5, dealer up, seats 1→5, dealer hole. Each card is K1 from the shoe. Face-up cards K1+K2. The **hole card** lands face down, tucked 5 px under the up-card and 2 px lower. The Java dealer NPC plays `deal` on every beat. Totals (K10) appear after each hand's 2nd card is readable. |
| PEEK (up A or 10) | +300 after the last deal | The hole card lifts: scaleY 1 → 0.86, y −3 px (200 ms `outQuad`), hold 200 ms, back 200 ms. The NPC plays `peek`. The existing notice `…dealer_peeks` slides in. Honest: the lifted card shows only its back. **No dealer BJ:** nothing more. **Dealer BJ:** the hole card K2 flips at once, K7 "Blackjack" stamp **on the dealer's hand in `chip.red`**, then SETTLE. |
| INSURANCE | during the peek | Insurance / even money prompts open **after** the peek lift (not over it). An insurance bet slides a half stack onto the insurance line (a thin arc `tables/insurance_line` under the dealer). Settled at the peek: paid → 2:1 discs from the rack; lost → sweep. |
| Player BJ (natural) | the peek beat + 300 | K8 shimmer on both cards + K7 "BLACKJACK!" + `card_sting`. The 3:2 payout stack flies in from the rack (local time) and stays in front of the hand until SETTLE (paid immediately per the rules; shown next to the bet). |
| TURNS | per action | The **active hand** gets a K9 glow ring. The seat plate shows the existing timer bar. |
| Hit | the server beat at once | K1+K2 new card. On 21: the "21" moment. On a bust, see below. The Hit button is disabled for 520 ms while the card is in flight (prevents double taps; the server rule is unchanged). |
| Stand | at once | The hand's glow stops; a 1 px `bone.shade` underline fades in (the hand is "closed"). The NPC plays a small `wave_off` (open palm, 300 ms). |
| Double | at once | A second bet stack slides next to the first (200 ms). The double card is dealt **rotated 90°** (the classic sideways card) and lands 3 px right of the hand (K1 end rotation 90°), then K2. |
| Split | at once | The two cards slide apart (K4, 200 ms) to two hand positions. A copy of the bet flies in from the viewer's side (180 ms). Each hand then gets its second card on the next beats. Split aces: both one-card hands receive their card and close at once (1 px underline on both). |
| Bust | the card beat + 520 | K7 "BUST" (`chip.red`) over the hand + K11 on the cards + the bet stack swept to the rack at once (260 ms `inCubic`) + `chip_sweep`. The total badge shakes ±2 px 3 times (200 ms). There is no `lose` sound here (the loss is shown, not punished). |
| DEALER | 0 | K2 flip of the hole card (the NPC plays `flip`) + `…fx.dealer_reveals` line. When every player hand is already settled, the flip happens and the dealer stops. |
| DEALER draws | +700, +1 400 … | Each draw is K1+K2 from the shoe. Line `…fx.dealer_draws`. After the last card: `…fx.dealer_stands` ("Dealer stands on 19") or the "Dealer busts with 24!" moment + K11 on the dealer's cards. |
| gate | the last dealer card + 520 | Server `gateTick`. RESULT starts. |
| SETTLE | 0 (local from here) | Per hand in seat order, 150 ms stagger: **win**: payout discs pop from the rack (tables §0.4) + the existing banner "Win +100"; **push**: wiggle, and the chips return to the plate; **lose**: sweep. The viewer's chips fly to the balance last (420 ms `inCubic`), then the **global celebration** for the viewer's tier. |
| Clear | RESULT end − 600 ms | K5 gather: every card to the discard tray, dealer first, 25 ms stagger + `card_gather`. The NPC plays `sweep`. |

The whole round for 1 seat with no dealer draws: deal 1.2 s + readable at 1.7 s, and the result shows
≈ 1.4 s after the last Stand.

**Shuffle** (the existing 40 t notice): the shoe sprite does a riffle. Two half-deck sprites
(`tables/riffle`, 4 frames at 60 ms) interleave twice, then the shoe "fills" (the card-lip height
animates 2 → 6 px). The cut card (a thin red line) appears at the penetration depth. `card_shuffle`.
The NPC plays `shuffle`.

### 1.3 Interaction feedback (Java)

The global §2.2 button rules apply: hover lift, press, invalid shake with `ui_deny`, and disabled with
a tooltip reason. The table adds:
- **Chip buttons** (1/5/25/100/500) are drawn as chip sprites (`fx/chip_<d>`, 2× scale) instead of
  text buttons, with the value as a runtime label under them.
  - Hover lifts the chip 2 px and adds a `glint` rim.
  - The selected chip keeps a gold ring.
  - Invalid (over the max, or not enough balance): the chip shakes, and the limit line under the row
    flashes `chip.red` once (not a strobe).
- **Bet circle:** hover shows a ghost chip (alpha 0.4) of the selected value. Right-click removes the
  top chip (it flies back to the chip row, 160 ms).
- **Action buttons** (Hit / Stand / Double / Split) get small 9×9 icons drawn **before** the label
  (hit = card plus, stand = flat hand, double = ×2 chips, split = two cards). There is no text in the
  icons.

### 1.4 Java in-world (BER `BlackjackTableRenderer`)

Base class: `CardTableRenderer` (§9 task J-C10). The block is 1×1 and the top is at y = 1.0. The layout
is relative to the block's facing:

- The **dealer's hand** runs along the far edge, centred, at z = 0.18. The shoe is at the far-right
  corner.
- **Seat hands** sit along the near edge in an arc of 5 positions (x = 0.14 … 0.86, z = 0.78 …
  0.68 at the ends). Each shows only the seat's **first** hand; more hands add a "×2" runtime text
  badge.
- **Cards** are 0.15 × 0.21 blocks, flat at y = 1.002 + 0.001 × index (no z-fighting) and overlapped
  45 %.
- **K1 in the world:** the path is from the shoe to the slot with a lift of 0.10 blocks at the midpoint
  (`outCubic`) and a yaw of −20° → 0.
- **K2 in the world:** the card rotates 180° about its long axis over 280 ms, lifted 0.05 blocks at
  the midpoint.
- **Chip stacks:** discs (a top quad from `fx/chip_<d>` plus a side quad) in front of each seat's
  cards, with at most 5 discs.
- **Totals:** `submitText` 0.28 blocks above each hand, billboarded, scale 0.012, and only within 8
  blocks. The dealer's total appears only after the reveal.
- **Settle:** winners get 4 client `sparkle` particles at their stack; bust hands are tinted dark. At
  the gather the cards slide to the discard corner.
- **Sounds:** `card_deal` / `card_flip` are played by the BER client-side and positionally (16-block
  range), **only** when the local player is not seated at this table. Seated players hear the screen
  sounds, so nothing plays twice.

**Dealer NPC gestures (Java, MUST).**
- The `BlackjackDealer` gets synced data `GESTURE` (byte) and `GESTURE_TICK` (int), set by the table
  on its beats (the nearest dealer within 3 blocks, the same rule as the "open nearest table" lookup).
- `BlackjackDealerRenderer` extends `HumanoidRenderState` with `gesture` and `gestureAge`, and a custom
  `DealerModel extends HumanoidModel` sets the arm and head rotations in `setupAnim`. All three dealer
  renderers share the same `DealerModel` (§9 J-C11).

| Gesture | Duration | Pose curve (radians; the arm rest pose is xRot −0.35, forearms "on the table") |
|---|---|---|
| `deal` | 300 ms | right arm xRot −0.35 → −1.1 → −0.35, yRot 0 → +0.35 (toward the seat index) → 0, `outCubic` then `inQuad` |
| `flip` | 350 ms | both arms xRot −0.35 → −0.9; right-arm zRot −0.25 → +0.25 (a wrist turn) |
| `peek` | 600 ms | head xRot 0 → +0.45; right arm xRot −1.0; hold 200 ms |
| `pay` | 450 ms | right arm xRot −0.9, yRot swings toward the paid seat |
| `sweep` | 500 ms | right arm yRot +0.6 → −0.6 (a horizontal rake), xRot −0.8 |
| `shuffle` | 1 600 ms | both arms alternate xRot −0.6 / −0.9 at 5 Hz, head down 0.2 |
| `wave_off` | 300 ms | left arm zRot −0.4 → 0 |
| idle | loop | ±0.03 rad breathing sway, 4 s period (exists in vanilla walk-anim idle) |

The dealer's head turns toward the acting seat: `lookAt` the acting player (existing look-at, with the
target set by the table).

### 1.5 Bedrock storyboard

| Phase | Presentation |
|---|---|
| Bet form submit | `burmaldaholic.chip_place` at the table + `burmaldaholic:chip_pop` particle at the table top. |
| DEAL | All forms are closed. Ticker line (§0.9) for seated players and spectators: `Dealer [A♠][▭] · You [8♦][8♠] 16 · Alex [10♥][7♣] 17` (key `…blackjack.actionbar` with `…actionbar_seat` items; RU ≤ 64 chars at 5 seats uses short names with `…`). One beat per card, same ticks as Java; `burmaldaholic.card_deal` once per beat via `dimension.playSound`. Dealer entity: `playAnimation('animation.burmaldaholic.dealer.deal')` per beat when the table is NPC-hosted or a dealer is within 4 blocks. |
| PEEK | Dealer `peek` animation + ticker suffix `…dealer_peeks`. |
| Player BJ | Title `§6BLACKJACK!` (fade 3 / stay 30 / out 8 t, via `hud.holdTitle`) + `burmaldaholic.card_sting`, player-only. |
| TURN form | Opens at the gate of the deal (the last card + 6 t), body as today. |
| Hit / Double | Form closes on submit. The ticker shows the card (K-frames, 8 t), then the form re-shows **8 t later** (not instantly). On a bust: ticker `§cBUST` (key) + `burmaldaholic.chip_sweep`, and the next hand's form or the wait starts. |
| DEALER | Forms stay closed for everyone. Ticker: hole flip frames, then one draw per 14 t, then "Dealer stands on 19" / "Dealer busts with 24!". The dealer entity plays `flip` and `deal`. |
| gate → SETTLE | `fx.celebrate(player, tier, net, 'blackjack')` (global §2.6; the WIN tier is an action bar `§a+N`), `burmaldaholic.chip_stack` for winners, `chip_sweep` for losers. Particles: `chip_pop` per winning seat above the table (≤ 16). Then the Result form (global timing: 20–60 t after the celebration starts). |
| Gather | The dealer `sweep` animation + `burmaldaholic.card_gather`. |

### 1.6 Tiers and moments (blackjack)

The tier is server `WinTier`:
- A normal win returns 2× (WIN).
- A natural returns 2.5× (WIN + the BLACKJACK moment).
- Doubles or splits can reach ~4–8× (still WIN).
- BIG needs ≥ 10×, which is effectively impossible in blackjack, so the celebrations stay modest by
  design.

Moments: §0.5.

### 1.7 Server data (blackjack, both editions)

- Beat publication: `visibleCards` per hand and for the dealer. Timers (insurance, turn) start at the
  deal gate.
- The public tag for the BER (`pub`): phase, the dealer's visible cards, each seat's first-hand cards
  and total, the bet amount and outcome, plus `beatTick`.
- Result: per-hand `ret`, the viewer's `net`, `stake`, `tier` and `gateTick`.
- Dealer gesture events (Java synced data; Bedrock `playAnimation` call sites).

### 1.8 MUST / NICE (blackjack)

- **MUST:** §1.2 whole storyboard; chip visuals; peek; bust / BJ moments; the dealer draw pacing; the
  §1.4 BER; Java dealer gestures; the §1.5 Bedrock ticker, sounds, dealer animations and title; the HUD
  gate.
- **NICE:**
  - A Bedrock in-world `card_hand` entity per hand (§4.8 spec, shared with baccarat).
  - A "last card" slow-flip for the dealer's final draw when the dealer is on 12–16: the flip runs at
    1.5× duration, **always**, whatever the card, so it is honest (the trigger is the public total).
  - Java insurance chips on a painted insurance arc.

---

## 2. Texas Hold'em

### 2.1 Audit

- **Java `PokerScreen`** (400×240 oval, compact 320×220):
  - Works: plate geometry, action timer bar, raise slider with ½ / ¾ / pot buttons, and RU-safe
    ellipsis handling.
  - Missing:
    - Cards pop in.
    - Hole cards are dealt instantly.
    - The board jumps a whole street at once.
    - Pots are a text line.
    - No chips in front of the plates.
    - Folds only change a tag.
    - The showdown has no order, no highlight and no pot movement.
    - The dealer button teleports.
    - No sounds at all.
- **Java server:** bot think delays exist (20–60 t), but streets and the showdown resolve at once.
  All-in run-outs deal the rest of the board instantly, which wastes the most dramatic poker moment.
- **Bedrock:** forms plus an action-bar stream of opponent actions (good). No board reveal pacing, no
  sounds, no showdown sequence.

### 2.2 Java screen storyboard

Beats: `poker.fx.dealBeatTicks` 3, `poker.fx.gatherTicks` 8, `poker.fx.streetTicks` 12,
`poker.fx.showBeatTicks` 10, `poker.fx.awardTicks` 12, `poker.fx.runoutPauseTicks` 24.

| Phase | t (ms) | Beat |
|---|---|---|
| New hand | 0 | The previous cards are already gathered (K5 to the muck at the dealer spot). The **dealer button** travels along the table ellipse to its new seat (400 ms `inOutCubic`, parametric angle, never a straight line across the felt) + a soft `chip_place` (p 0.8). |
| Blinds | 400 | SB and BB chips fly from their plates to their bet spots (180 ms) + `chip_place`. Tags K12 "Small blind 5" / "Big blind 10" (existing `msg…action.small_blind` short form, see §11). |
| Hole cards | 600 + 150·k | Two rounds, clockwise from SB, one card per 3 t beat, K1 at 200 ms from the deck at the top centre. The viewer's cards land and flip (K2). Others stay face down, fanned 3° apart on the plate. A 6-seat deal takes 1.8 s. `card_deal` at vol 0.5, pitch seeded. |
| Acting seat | loop | K9 glow on the plate + the existing timer bar. Last 5 s: the bar pulses `chip.red` (flashes off: static red) + `wheel_tick` per second for the **acting viewer** only. Bots: the thinking dots (§5), no timer bar pulse. |
| Check | at once | K13 knock + tag "Check". |
| Call / Bet / Raise | at once | The stack slides from the plate to the bet spot (200 ms `outCubic`, stack builds); tag K12 "Call 20" / "Bet 40" / "Raise to 120"; `chip_place` (bigger bets add `chip_stack`). The plate stack number rolls down (numbers are monotonic within the tween). |
| All-in | at once | §0.4 all-in push + K7 ALL-IN stamp on the plate (pulses twice at 2 Hz, then static) + `chip_push`. |
| Fold | at once | K6 muck of the two backs (or faces, for the viewer) toward the deck spot; the plate dims to 55 %; tag "Fold". |
| Street end | 0 | Gather (§0.4): spots → pot, 50 ms stagger clockwise from the button, pot label rolls. Uncalled excess **slides back** to its owner first (200 ms, gray label "Returned 40"), because the server returns it (§7.3). |
| Burn | +400 | One back slides from the deck to the muck pile (200 ms). |
| Flop | +600 | Three backs slide to board slots 1–3 (3 beats, 150 ms apart), then flip together left → right, 80 ms apart (K2) + `card_flip` × 3 (pitch 1.0 / 1.06 / 1.12). The viewer's hand name (small label under the viewer's cards, e.g. "Pair of queens" from `…poker.hand.*`) fades in after the flip. |
| Turn / River | +600 | Burn, then 1 back slides to its slot and flips. |
| **All-in run-out** | when betting is closed and ≥ 2 live hands | See the run-out row below. |
| Showdown | 0 | Server `showOrder` (last aggressor first, else first active left of the button). For each shown hand, 500 ms apart: both hole cards K2 flip, then the hand-name badge drops in under the plate (`outBack` 200 ms). A mucked hand: tag "Mucks" + K6. |
| Best 5 | +300 after the last show | The winner's best-five cards (server `bestFive`: indices into hole + board) **lift 3 px** and get K9 glow; unused cards dim to 55 %. Ties (split pot): every winner's best five are highlighted in turn, 400 ms each. |
| Award | +600 | Pots move: side pots first (server order), 500 ms each, 400 ms apart; split pots fan (§0.4). The winner's plate rings gold (K9, 2 cycles). Big pot / monster moment (§0.5). `pot_win`. |
| Viewer result | after the award | The viewer's net → the global celebration (tier on the viewer's net vs their contributions this hand). Losing pots: no banner beyond "Alex wins 340" (existing `msg…wins_pot`). |
| Next hand | award + 1 500 ms | Then gather (K5), and the next hand starts on the existing `next_hand` timer. |

**All-in run-out** (the most dramatic poker moment):
1. Every live hand is flipped face up at once (K2, all simultaneously). This needs server config
   `poker.exposeAllIn` (true), the standard all-in exposure rule. It is a rules dependency and must be
   approved by the GAME_DESIGN owner; if it is off, the hands stay hidden until the showdown.
2. Banner `…fx.all_in_runout` slides down.
3. Each remaining street: a **sweat pause** of 1 200 ms, then the card slides in and flips at **1.6×
   K2 duration** (450 ms). The pause and the flip length are identical for every card (§0.7.3).
4. NICE: an equity bar under each exposed hand. It is computed client-side from public cards only,
   updated after each street, and animated over 300 ms. It is information-neutral because no decisions
   remain.

**Uncontested pot** (everyone else folded): no reveal. The pot slides to the winner. A winner who has
the existing Show option shows the cards with K2 and the tag "Shows".

### 2.3 Interaction feedback (Java)

- **Raise slider:** the thumb snaps to BB steps. Each step change plays `chip_count` (pitch from 0.9 to
  1.4 across the slider range, ≤ 15/s).
  - The "Raise to X" label rolls (K10).
  - Quick buttons (½ pot, ¾ pot, Pot, All-in) slide the thumb to their value over 150 ms
    (`outCubic`), never jumping.
  - An invalid amount shakes the slider and plays `ui_deny`.
- **Action buttons:** Fold is secondary, Check/Call primary, Raise primary, All-in danger (global
  button families).
- **Hover on a seat plate:** a tooltip with the full name, stack, bot level and personality (BOTS.md
  §8.1).
- **Hover on a pot stack:** its exact amount and eligible seats.

### 2.4 Java in-world (BER `PokerTableRenderer`)

On the table top:
- **Board:** 5 slots in a row in the centre (z = 0.45), L-equivalent 0.13 × 0.18 blocks.
- **Pot:** a stack plus a runtime label at the centre front.
- **Seats:** 6 positions around the rim. Each seat has two face-down cards and a bet stack; its plate
  is the bot nameplate or the player's own body.
- The dealer button is a small white disc (`tables/dealer_button` quad) that slides between seats
  (400 ms).
- The street flips, the showdown (public at the showdown), the pot slides and the run-out pause all
  match the screen timings (shared time).
- Hole cards appear face up in the world **only** at the showdown or on an all-in exposure.
- Per-seat bet stacks gather into the pot at the street end.
- No text beyond the pot amount and the hand names at the showdown (8-block range).

### 2.5 Bedrock storyboard

| Phase | Presentation |
|---|---|
| Hole cards | Ticker for the viewer: `Your cards [▭][▭]` → the frames (§0.9) → `[A♠][K♠]`; `burmaldaholic.card_deal` player-only × 2. Other seats: `…actionbar` `Board — · Pot 15` (spectators). |
| Opponent actions | The existing action-bar stream, with `[BOT]` glyph names, the thinking-dots line while a bot thinks (global §4.12), and a chip glyph before amounts. Sounds: `burmaldaholic.table_knock` for a check, `chip_place` for bets, `chip_push` for all-ins (table positional). |
| Your action form | Opens when your timer starts (unchanged). The form body adds a board line built from glyphs, a pot line with the chip glyph, and the side pots. |
| Streets | Forms of **non-acting** players are not open. Ticker `Flop [Q♠][J♠][T♠]` with the flip frames (2 t apart per card), `burmaldaholic.card_flip` × 3 at the table. |
| All-in run-out | **Title sequence** for every seated player (via `hud.holdTitle`): title `…fx.all_in_runout` (gold, fade 5 t), subtitle = the board glyphs `[Q♠][J♠][T♠] [▭] [▭]`. After a 24 t pause, `updateSubtitle` swaps the next card through the flip frames (the edge glyph for 2 t, then the face) + `card_flip`. The exposed hands are listed in the action bar: `Alex [A♠][A♦] · You [K♣][K♥]`. |
| Showdown | The ticker lists the shown hands one per 10 t: `Alex: [A♠][K♠] — Flush`. Then the winner line `…fx.wins` ("Alex wins 340"), `burmaldaholic.pot_win`; the viewer's `fx.celebrate` when they won; the big pot moment as a title (`§6Big pot!`, 3/30/8). Then the existing Showdown form, with the new body line `…fx.best_hand` listing the five cards. |

### 2.6 Tiers and moments (poker)

- The tier comes from the server `WinTier` on the viewer's net vs their chips put in this hand. A
  1 BB blind steal is `WIN`. Stacking an all-in opponent with 100 BB each: return ≈ 2×, so `WIN`, plus
  the Monster pot moment.
- Poker therefore relies on **moments** (big pot, monster pot, royal), not on tiers, which is realistic
  and honest.
- There is no celebration for a fold. A lost all-in plays no `lose` sound: the pot sliding away is
  enough.

### 2.7 Server data (poker, both editions)

- Beat publication:
  - hole cards one by one;
  - the street end split into gather, burn, cards and flip beats;
  - the showdown in `showOrder`, one per beat;
  - the award per pot per beat.
- New per-hand fields:
  - `showOrder[]`, `bestFive{seat: [indices]}`;
  - `potAwards[] {pot index, winners[], amounts[]}`;
  - `uncalledReturn {seat, amount}`;
  - `exposed[]` (all-in);
  - `lastAction {seat, kind, amount, seq}` (for K12 tags; existing log entries can feed it);
  - `bigPotBb` (the server computes the moment flag);
  - `gateTick`.
- The public tag for the BER: the board, pots, button seat, per-seat bet spot amounts, fold and all-in
  flags, and the shown cards at the showdown.
- Config: `poker.exposeAllIn` (rules owner approval) and the `poker.fx.*` beat ticks (§8).

### 2.8 MUST / NICE (poker)

- **MUST:** everything in §2.2 except the equity bars; the §2.4 BER; the §2.5 Bedrock ticker, sounds,
  run-out titles and showdown sequence.
- **NICE:**
  - Equity bars during an all-in run-out.
  - A virtual dealer NPC for poker tables (a `poker_dealer` reusing `DealerModel`).
  - Bot chatter bubbles on plates (§5).
  - A "bad beat" line in chat only (never a celebration).

---

## 3. Ultimate Texas Hold'em

### 3.1 Audit

- **Java `UthScreen`** (400×240):
  - Works: good RU-safe tags (Deciding… / Checked / Play ×4 / Folded), the at-risk line, and the
    paytable overlay. The server already paces streets (20 t flop, 10 t each for turn and river).
  - Missing:
    - The deal is instant.
    - Board cards appear instead of flipping.
    - The showdown drops all dealer and seat cards plus every per-bet line in one frame.
    - No chips on the four bet circles.
    - No qualify beat.
    - The royal flush has no special presentation beyond the chat broadcast.
- **Bedrock `uth/table.ts`:** the same street pacing (REVEAL_TICKS 20, RESULT 80). Otherwise forms and
  the action bar, with no sounds.

### 3.2 Java screen storyboard

Beats: `uth.fx.dealBeatTicks` 3, `uth.fx.boardSlideTicks` 8, existing `REVEAL_TICKS` 20, and
`uth.fx.qualifyTicks` 16.

| Phase | t (ms) | Beat |
|---|---|---|
| BETTING | local | Chips fly to **Ante**. The **Blind** circle mirrors it: a copy of the stack slides from Ante to Blind 120 ms later, which shows "Blind = Ante" in motion. Trips mode: chips go to Trips. The Play circle shows a dashed ring (empty). |
| DEAL | 0 + 150·k | Seats 1→6 card 1, dealer card 1, seats card 2, dealer card 2 (K1 from the deck at the top centre, 200 ms). The viewer's cards K2 flip on landing. Others' cards and the dealer's two cards stay face down. |
| Board | +300 after the last hole card | The 5 board backs slide out in one sweep: each 300 ms `outCubic`, 60 ms stagger, to their slots, with a single `card_slide`. |
| PREFLOP decide | shared timer | Every seat decides at the same time. Plates show "Deciding…" (humans) or the thinking dots (bots, §5). When a seat bets ×3/×4, its Play stack flies from the plate to the Play circle (220 ms) + K12 tag "Play ×4" (gold). A check is K13 knock + tag "Checked". |
| FLOP | street start | The existing 20 t step: the 3 board cards flip left → right, 100 ms apart (K2). The viewer's hand-name label updates with a 150 ms pop, e.g. "Two pair" (existing `…your_hand`). |
| TURN + RIVER | street start / +500 ms | Card 4 flips; card 5 flips 500 ms later (existing 10 t each). |
| River fold | at once | K6 muck of the viewer's cards. Ante and Blind stacks swept at once (the Trips stack stays: Trips still settles). Tag "Folded". |
| SHOWDOWN | 0 | The dealer's 2 cards flip, 200 ms apart (K2) + the dealer NPC `flip`. |
| Qualify | +600 | Banner `…uth.qualifies` / `…uth.not_qualifies` ("Dealer does not qualify — Ante pushes") slides down from the dealer area (200 ms). **Same pause either way** (§0.7.3). |
| Seat reveal | +1 100 | Every seat's hole cards flip together (public at the showdown); a best-hand label under each plate; the viewer's best five (server `bestFive`) lift + K9. |
| Settle per seat | +1 500, 150 ms stagger in seat order | Per circle in order Play → Ante → Blind → Trips, 80 ms apart: win = payout discs from the rack; push = wiggle + stays; lose = sweep. A Blind or Trips bonus plays the moment (§0.5: K9 on the circle + stamp). The player-banked table: the dealer-seat plate's bank number rolls ± per seat, with a coloured floater per seat. |
| Viewer result | after their circles | Chips to the balance → the global celebration (tier on the whole round). Royal: §0.5 royal moment, then `JACKPOT` (if the flag is accepted) or `EPIC`. |
| Gather | RESULT end − 600 ms | K5 of every card to the muck + NPC `sweep`. |

RESULT_TICKS is 80 (existing). The settle stagger for 6 seats (≈ 1.4 s) fits.

### 3.3 Interaction feedback

- The Check / Bet ×N buttons show the resulting Play stack as a **ghost stack** on the Play circle on
  hover (alpha 0.4, with the amount label). Unaffordable buttons are disabled with a tooltip
  (existing).
- A second click on a Play button after deciding: invalid shake + `ui_deny` (a Play bet is final).
- The paytable overlay opens with a 150 ms fade + 6 px rise (reduced motion: fade only). While a
  bonus pays, the matching paytable row gets a gold outline when the overlay is open.

### 3.4 Java in-world (BER `UthTableRenderer`)

- The dealer's 2 cards at the far edge; the 5-card board at the centre; 6 seat positions on the near
  and side rims with 2 face-down cards and a single combined bet stack each.
- The flop and turn/river flip timings are shared.
- At the showdown, the dealer's cards and then all seats' cards flip.
- A royal flush: the 5 cards get a gold `sparkle` burst (12 client particles) + K8 shimmer quads.

### 3.5 Bedrock storyboard

| Phase | Presentation |
|---|---|
| Bets form submit | `chip_place` + `chip_pop`. |
| DEAL | Ticker: `Dealer [▭][▭] · Board [▭][▭][▭][▭][▭] · You [A♠][K♠]` built per beat. `card_deal` (table) per beat; the dealer entity `deal`. |
| Decision form | Opens when the street's reveal beats have finished. The body is unchanged, plus the hand-name line. |
| Street reveals | Ticker flip frames per board card. The other seats' decisions stream as `…uth.actionbar` with tags. |
| SHOWDOWN | Forms closed. Ticker: the dealer's cards flip frames; after 16 t the qualify line (title-free, action bar); then the viewer's per-bet lines are revealed in 4 steps, 4 t apart (Play, Ante, Blind, Trips), with `chip_stack` / `chip_sweep`. Then `fx.celebrate` and the Result form. |
| Royal | Title `§6ROYAL FLUSH!` + `card_sting` + the global celebration (EPIC / JACKPOT) + the existing broadcast. |

### 3.6 Server data (UTH)

- Deal beats (the existing street steps stay).
- `bestFive` per seat and for the dealer at the showdown.
- Per-circle results `{play, ante, blind, trips}.ret` (existing settlement lines carry the same
  information).
- `gateTick`.
- The public BER tag: the dealer's cards (backs until the showdown), the board visible count, and per
  seat: tag, total bet and cards at the showdown.

### 3.7 MUST / NICE (UTH)

- **MUST:** §3.2, §3.4, §3.5.
- **NICE:**
  - A "Blind bonus" paytable row highlight in the Bedrock result body (a coloured row).
  - An animated banker-bank ticker on the player-banked table in the world (a runtime text display).

---

## 4. Baccarat and Chemin de fer

### 4.1 Audit

- **Java `BaccaratScreen`:**
  - Works: the best structure of the four. It is server-timed (`reveal_total` / `reveal_left`), with
    card-by-card visibility, "Player draws a third card" lines, a bead plate with letters, and snapped
    Banker amounts.
  - Missing:
    - Cards still pop instead of moving.
    - Hidden slots are backs with no deal motion.
    - No squeeze, which is the signature of baccarat.
    - Winning boxes have no motion.
    - The bead just appears.
    - Chemin de fer's bank offer and Banco have no drama.
    - The shoe does not exist visually, although passing the shoe is the heart of chemmy.
- **Bedrock:** `revealFrames` (P1, B1, P2, B2 every `revealTicks/8`, then the third-card notes) drive
  an action-bar line. This is a good base; it becomes the squeeze ticker.

### 4.2 New reveal timeline (both editions, replaces `revealFrames` / `visible()`)

A pure `revealTimeline(coup, cfg)`:
- `cfg`: `dealBeatTicks` 6, `flipTicks` 6, `squeezeTicks` 20, `announceTicks` 10 and `squeeze`
  (on/off, per-table config).
- Output: `[{at, slot, kind: DEAL|FLIP|SQUEEZE|ANNOUNCE, note}]` and `total`.

| t (ticks), squeeze on | Beat |
|---|---|
| 0, 6, 12, 18 | DEAL P1, B1, P2, B2 face down (K1 from the shoe) |
| 24 | FLIP P1 |
| 30 → 50 | SQUEEZE P2 → the Player total shows at 50 |
| 52 | FLIP B1 |
| 58 → 78 | SQUEEZE B2 → the Banker total at 78 |
| 78 → 88 | ANNOUNCE: "Natural 9" / "Player draws a third card" / "Player stands on 6" (existing keys) |
| 88 → 114 | Player third: DEAL (6 t) + SQUEEZE (20 t) |
| 114 → 120 | ANNOUNCE the Banker decision ("Banker draws" / "stands on 5") |
| 120 → 146 | Banker third: DEAL + SQUEEZE (starts at 88 if the Player stood) |
| last + 6 | `gateTick` → RESULT |

Totals: a natural coup takes 88 t (4.4 s). The longest coup takes 152 t (7.6 s). Today's reveal is 80 t.

- **Squeeze off:** each SQUEEZE becomes a FLIP (6 t). A natural takes 54 t; the longest takes 84 t.
- `reveal_total` in the state = `total`. The existing `baccarat.revealTicks` is kept as a **cap**
  (default raised to 160). If the timeline would exceed it, the squeeze windows are scaled down
  proportionally, never below 10 t.
- **Squeezed slots:** P2, B2 and both third cards. P1 and B1 only flip, because a full squeeze on all
  six cards drags.
- The schedule depends only on **how many** cards each side gets and whether there is a natural. Both
  are public at the moment their beat arrives, so this is honest (§0.7.3).

### 4.3 Squeeze (K3), Java screen

The card lies face down in its slot (L size) and is squeezed by peeling from its **bottom edge
upward**. The Banker hand is peeled from the top edge down, so the two hands mirror each other.

Revealed fraction `f(u)`, with `u` running 0 → 1 over the window:

| u | f | Beat |
|---|---|---|
| 0 → 0.20 | 0 → 0.18 (`inOutSine`) | "corner lift": the card rises 2 px and its shadow grows |
| 0.20 → 0.40 | hold at 0.18 | the **breath**: the bottom row of real pips is visible (2–10 show pip ends; courts show a sliver of the frame) |
| 0.40 → 0.80 | 0.18 → 0.60 (`inOutSine`) | the middle of the card is revealed |
| 0.80 → 0.88 | 0.60 → 0.72 | slow |
| 0.88 → 1.00 | snap to 1.0 + a 1.08 → 1.0 scale pop (`outBack`) | `card_flip` sound; the total appears |

Rendering:
- The face sprite is drawn with a scissor for the revealed band (`f × height` from the peeled edge).
- The back sprite is scissored for the rest.
- A **curl** sprite (`cards/curl_l`, 24×6: a light gradient of the paper's underside with a 1 px dark
  crease) sits at the boundary and is scaled with `1 + 0.5·sin(πf)` so the lift looks physical.

Rules:
- The same `f(u)` applies for every card (§0.7.7).
- It is monotonic (never un-peels).
- Reduced motion: §0.6 (a back with a progress bar, then a cross-fade at u = 1).

**Who squeezes** (cosmetic label only, no interaction in MUST). A line under the hand:
- `…fx.squeezing` "Alex squeezes…".
- The viewer sees `…fx.you_squeeze` (gold).
- House table: the seat with the largest stake on that side (Player: the Player bet; Banker: the
  Banker bet). A tie goes to the lowest seat. An atmosphere bot can be named.
- No bet on that side: the line is `…fx.player_card` / `…fx.banker_card`.
- Chemin de fer: the **banker** squeezes the Banker hand; the **punter with the largest bet** (or the
  Banco caller) squeezes the Player hand.

**NICE — interactive peel:**
- The named squeezer may drag the card with the mouse to peel it faster **locally**:
  `f_local = max(f(u), dragFraction)`.
- At the window end the card is fully revealed for everyone, at the same server tick.
- Others always see the scripted peel.
- This changes only that viewer's pixels, never the timing.
- Per-player toggle `…menu.settings.squeeze_peel`.

### 4.4 Java screen storyboard (house table)

| Phase | t | Beat |
|---|---|---|
| BETTING | local | Chips fly into boxes (tables §0.4). Others' chips land in boxes as tinted mini stacks. A snapped Banker amount: the chip lands, then a small chip flies back (160 ms) + the existing `…snapped` line. Hover on a box: a `glint` rim + the ratio tooltip. Ready: a ✓ pops on the plate (`outBack`). |
| NO_MORE_BETS (20 t) | 0 | Each box's stacks get a 1 px gold outline; the box rims dim 20 %; banner `…no_more_bets` slides down. |
| SHUFFLE (40 t) | 0 | Riffle (§1.2 shuffle) in the shoe; the burn card: the first card slides out **face up** (a rules burn indicator), the existing `…burned.*` line, then N backs slide to the discard. The bead plate clears with a 300 ms fade, row by row. |
| REVEAL | §4.2 | K1 deals, K2 flips, K3 squeezes; totals with K10; announce lines slide in under the hand (existing keys); a natural plays the moment (§0.5). |
| gate | | |
| RESULT (60 t) | 0 | The winning panel border glows in its colour (Player blue `#3A6BE0`, Banker red `#D03030`, Tie green `#2FA64A`) for 2 pulses; the losing panel dims 50 % (Tie: both glow green). The existing result banner slides down. |
| Bead | +200 | The new bead drops into its cell from 10 px above (250 ms `outBounce`) with its runtime letter (P/B/T; RU И/Б/Н) and the pair dots. |
| Box settle | +400, 120 ms per box in the order Player, Banker, Tie, P.Pair, B.Pair | Win: payout discs (Banker: the commission chip splits off and flies to the rack, labelled with the existing `…line.commission` amount); push (P/B on a Tie): wiggle + return; lose: sweep. |
| Viewer result | after their boxes | Chips to the balance → the global celebration. |
| Gather | end − 600 ms | K5 to the discard tray. |

### 4.5 Chemin de fer additions (Java)

- **The shoe passes.** When the bank changes hands, the shoe sprite slides from the old banker's seat
  plate to the new one (500 ms `inOutCubic`) + `card_slide` + line `…fx.shoe_passes`. The world BER
  moves its shoe quad to the matching seat edge.
- **BANK_OFFER:**
  - The candidate's modal panel scales in (0.96 → 1, 150 ms).
  - The timer ring is the existing one.
  - "Keep the bank (2 280)" shows the amount rolling up from the previous bank.
- **Banco:** the caller's plate gets K7 "Banco!" (gold) + `chip_push`, and their stack slides to the
  Player box in full (the §0.4 all-in push style).
- **Bank result:** the banker's plate shows the bank rolling ± and a coloured floater. A busted bank
  (the banker lost it all): the shoe passes at once.

### 4.6 Bedrock storyboard

| Phase | Presentation |
|---|---|
| Bet submit | `chip_place` at the table + `chip_pop`; snapped: the existing line. |
| NO_MORE_BETS | The existing action bar (`…no_more_bets`) + `burmaldaholic.chip_stack` (table). |
| REVEAL ticker | The existing `…actionbar` line (`Player [8♠][▭] 8 · Banker [▭][▭]`), now driven by `revealTimeline` with the §0.9 frames; `card_deal` / `card_flip` / `card_squeeze` at the table. |
| **Squeeze title** (MUST) | For every **seated player with a bet** (not spectators): during each SQUEEZE window, the **title** shows the single card glyph large, stepping through the peel frames (back → ¼ → ½ → hold → edge → face; 5 `setTitle`/`updateSubtitle` calls, `fadeIn 0`, stay covering the window, `fadeOut 4`). The subtitle is `…fx.squeezing` / `…fx.you_squeeze` / `…fx.player_card`. The title colour is blue for Player cards and red for Banker cards, as a § code on the frame glyph line (the glyph itself is white; the colour is on the surrounding brackets). `hud.holdTitle` covers the window. Reduced motion: the back is held, then the face. |
| Announce | Action bar `…natural` / `…player_draws` …; a natural adds `card_sting` (for bettors on that side only). |
| RESULT | `fx.celebrate` per bettor (global) → the Result form (existing). Its body gets the bead line with the new bead first, in bold (§l). Dealer entity (NPC tables): `pay` then `sweep`. |
| Chemmy | Bank offer form unchanged; when the bank passes, the action bar `…fx.shoe_passes` + `card_slide`. Banco: title `§6Banco!` 2 t / 24 t / 6 t for the table's seated players + `chip_push`. |

### 4.7 Java in-world (BER `BaccaratTableRenderer`)

- The two hands sit side by side at the centre (Player left, Banker right, relative to the facing),
  L-equivalent size 0.14 × 0.20 blocks. The shoe is at the far-right corner (in chemmy, at the banker
  seat's edge).
- **In-world squeeze:** the card's near edge lifts. It rotates about its far edge 0 → 35° following
  `f(u)`, so only the **underside of the lifted part** shows: a back-coloured quad with a light crease.
  The face stays hidden until the snap, then a 180° flip over 150 ms. It is honest and readable from
  any angle.
- The bead plate is not drawn in the world. The last result is shown as a small coloured disc with a
  runtime letter at the table front for 3 s.
- Bet boxes: one combined stack per box (the sum of all bettors) with a label within 8 blocks.

### 4.8 Bedrock in-world card entity (NICE; shared by all four games)

`burmaldaholic:card_hand`: one entity per visible hand. It is generated by
`bedrock/tools/gen-card-entity.mjs`.

- **BP** `entities/core/card_hand.json`:
  - Components: no AI, `physics` has_gravity false, `collision_box` 0.01, `damage_sensor` none,
    `pushable` false, not persistent (the table re-spawns it), `tick_world` absent.
  - Properties (`client_sync`):
    - `c0`..`c5`: int [0, 53] (0 empty, 1–52 cards, 53 back)
    - `flip`: int [0, 63], a bit per slot: "animate flip on change"
    - `sq`: int [0, 63], a bit per slot: squeeze
    - `seq`: int [0, 1023]
- **RP geometry** `models/entity/card_hand.geo.json`:
  - Bone `root` → `slot0..slot5` (offset 0.11 blocks, overlap) → 54 child bones each (one plane
    cube per face variant, 2 × 3 px model units, UV into `textures/entity/cards/faces.png`, 256×256:
    13 × 4 faces of 16×22 + backs).
  - That is 324 planes; only 6 are visible at a time.
- **Render controller:** `part_visibility` entries `slotN_cM: q.property('burmaldaholic:cN') == M`,
  generated.
- **Animations** `animations/card_hand.animation.json`:
  - `deal_N`: slot N moves from the shoe offset (−0.6, 0.1, −0.3) to 0 in 0.24 s, `easeOutCubic` via
    keyframes, yaw −20 → 0.
  - `flip_N`: rotation z 0 → 180 in 0.28 s with a lift of 0.05.
  - `squeeze_N`: rotation x 0 → 35° following the §4.3 keyframes over `squeezeTicks`, then a flip.
- **Controller** `animation_controllers/card_hand.ac.json`: one controller per slot. It watches
  `q.property('burmaldaholic:cN')` against `v.last_cN` to trigger deal or flip.
- **Script:** the table sets 1–2 properties per beat, within the §0.8 budget. Entities are despawned
  200 t after idle, or when the table breaks.

### 4.9 Server data (baccarat)

- `revealTimeline` in both editions, which replaces `revealFrames` and the Java `visible()` / `step()`.
- The state carries `reveal_total`, `reveal_left` (existing) and `sq` flags per slot.
- `squeezer {player: name|null, banker: name|null}`.
- Per-box `ret` for the viewer (the lines exist).
- `gateTick`.
- Config: `baccarat.squeeze` (bool, default true), `baccarat.fx.squeezeTicks` (20), and
  `baccarat.revealTicks` becomes the cap, default 160.
- The public BER tag: the visible cards per hand, totals and the last result.

### 4.10 MUST / NICE (baccarat)

- **MUST:** §4.2–§4.7 (the timeline, squeeze, house and chemmy storyboards, the Bedrock squeeze title
  and the BER).
- **NICE:** the interactive peel (§4.3), the §4.8 entity, and a road-map (big road) panel with
  animated bead insertion.

---

## 5. Bot seats (all four games)

The shared rules are in BOTS.md §7–§8 and global §4.12. This section covers only the in-game table
presentation.

**Java (on the bots branch; MUST):**
- **Seat plate** (all four screens, nine-slice `tables/seat_plate`):
  - Bot glyph U+E190 + name + the **level badge** (`bots/badge_easy|normal|hard`: a 7×7 coloured
    rounded square, green / yellow / red, with the level letter drawn at runtime from
    `…bots.level.<l>.short`, 1 character: E/N/H, RU Л/Н/С).
  - The stack (money bots) or the hatched-chip icon (atmosphere).
  - Tooltip: the level word, the personality line, and "Leaves after this round".
- **Thinking indicator:** while the table waits for a bot decision, its plate shows the dots glyph
  cycling U+E191 → E192 → E193 every 10 t (client-side from the state flag `thinking: seat`).
  - Humans get a timer bar instead; bots get **no timer bar** (they never use the human timer, BOTS.md
    §7.3).
  - The dots look identical for every decision (§0.7.9).
- **Action:** exactly the same motion as a human (chip slide, K12 tag, K13 knock, K6 fold). There is no
  "instant" jump even at speed FAST. At INSTANT (BOTS_ONLY tables), beats still play at the table's
  beat rate.
- **Emotes after a public result** (never before a reveal, BOTS.md §7.4): a 9×9 emote sprite
  (`bots/emote_happy`, `bots/emote_grumpy`) pops above the plate. It scales 0 → 1 (`outBack`
  200 ms), holds 1 s, then fades. At most one per bot per round. Happy on a won pot or hand; grumpy on
  a bust or a lost all-in.
- **Chatter:** the chat line as specified. NICE: a speech bubble K12 on the plate for 3 s with the
  translated quip, truncated at 1 line + "…" (full text in chat).
- **Joining or leaving a seat:** the plate slides in from the table rim (250 ms `outCubic`) with the
  `note` emote; leaving is a fade + slide out. Yielding to a human: the tag "Leaves after this round"
  (existing) pulses once, then stays.
- **Atmosphere bets:** hatched chips (§0.4). The Bedrock-style "Bots bet (for fun)" summary is a
  tooltip on the hatched stacks.
- **In-world:** the nameplate text display (global §4.12) sits above the bot's seat position on the
  BER layout, so a spectator sees the seat's cards and chips under the floating name. The BER uses the
  same seat anchor points as the nameplate (task J-C10 exports `seatAnchor(table, seat)`).

**Bedrock (MUST):** forms and the action bar only (bots are virtual; BOTS.md §7.2 default NONE).
- In ticker lines, bots appear with the glyph U+E190 and a short name.
- The acting bot's segment shows the dots glyph cycling every 10 t (global §4.12).
- Emote particles (`burmaldaholic:emote_*`, global NICE, else vanilla `villager_happy` /
  `villager_angry`) above the table centre, after the public result only.

---

## 6. Assets

All PNGs are generated by the shared generator (global task S1, `scripts/gen-fx-assets.py`) through a
card module `scripts/fx/cards.py`:
- palette from global §2.1;
- pixel art as string grids plus procedural pip layouts;
- deterministic;
- **no text in any texture** (rank indices are runtime font, §0.3).

Glyphs extend `glyph_E1.png` (Bedrock) and the matching Java `font/default.json` sheet.

### 6.1 Java GUI sprites (`textures/gui/sprites/burmaldaholic/…`)

| # | Asset | Size | Frames / files | Notes |
|---|---|---|---|---|
| 1 | `cards/faces_l.png` (atlas texture `textures/gui/cards/faces_l.png`, blit by UV) | 312×136 (13 × 4 of 24×34) | 1 | Pips in the standard layouts for A–10. Court cards J/Q/K: 12×16 portrait pixel art (3 designs tinted by suit colour; red suits get the red palette). A: one large pip with a flourish ring. The corner areas are left empty for runtime indices. Red suits have a 1 px darker outline (greyscale distinction). |
| 2 | `cards/faces_m.png` | 208×88 (16×22) | 1 | Simplified: a centre suit pip (courts: a mini portrait). Runtime index top-left. |
| 3 | `cards/faces_s.png` | 143×60 (11×15) | 1 | A single pip; the runtime index is drawn beside it |
| 4 | `cards/backs.png` | 4 designs × (L 24×34 + M 16×22 + S 11×15) = 204×34 | 1 | Designs: blackjack navy lattice, baccarat burgundy damask, poker red diamonds, UTH emerald check. One family, with a gold 1 px inner border. |
| 5 | `cards/shadow` | 28×38 | 1 | nine-slice border 4, black at 35 % with a soft edge |
| 6 | `cards/glow` | 30×40 | 4 (`.mcmeta` frametime 3) | gold rim pulse (K9) |
| 7 | `cards/curl_l`, `cards/curl_m` | 24×6, 16×4 | 2 files | squeeze curl |
| 8 | `fx/shimmer_band` | 8×40 | 1 | K8 |
| 9 | `tables/stamp` | 32×14 | 1 | nine-slice border 4, white (tinted per stamp) |
| 10 | `tables/tag` | 24×12 | 1 | nine-slice bubble with a 3 px tail (K12) |
| 11 | `tables/total_badge` | 16×10 | 1 | nine-slice |
| 12 | `tables/shoe` | 28×22 | 1 | wooden shoe with a card lip |
| 13 | `tables/riffle` | 20×12 | 4 | shuffle frames |
| 14 | `tables/discard` | 20×14 | 1 | |
| 15 | `tables/deck` | 16×20 | 1 | poker and UTH deck stack |
| 16 | `tables/rack` | 64×12 | 1 | dealer chip rack (5 coloured columns) |
| 17 | `tables/bet_circle`, `bet_circle_hover` | 22×22 | 2 | felt ring with a gold line |
| 18 | `tables/bet_box`, `_hover`, `_locked` | 32×20 | 3 | nine-slice, baccarat boxes (the Player/Banker/Tie colours are tinted by code) |
| 19 | `tables/uth_circles` | 4 × 18×18 | 4 files | Trips, Ante, Blind, Play (distinct ring shapes: dotted, solid, double, dashed; the shape carries meaning without colour) |
| 20 | `tables/insurance_line` | 96×8 | 1 | arc |
| 21 | `tables/pot_well` | 40×20 | 1 | nine-slice oval |
| 22 | `tables/dealer_button` | 11×11 | 1 | white disc with a gold star (no letter) |
| 23 | `tables/seat_plate`, `_active`, `_me`, `_folded` | 32×16 | 4 | nine-slice border 4 |
| 24 | `tables/progress` | 24×2 | 1 | reduced-motion squeeze bar |
| 25 | `tables/hand_icons` | 4 × 9×9 | 4 files | hit, stand, double, split |
| 26 | `fx/chip_hatched` | 8×8 | 1 | atmosphere overlay |
| 27 | `bots/badge_easy`, `_normal`, `_hard` | 7×7 | 3 | |
| 28 | `bots/emote_happy`, `_grumpy` | 9×9 | 2 | |
| 29 | `textures/entity/cards/faces.png` (world atlas for the BER, also copied to the Bedrock NICE entity) | 256×256 | 1 | 13 × 4 faces 16×22 + 4 backs + a blank; world faces carry a **pixel rank index** — see the note below |

Note on #29: world cards are too small for runtime text, so the atlas faces use **language-neutral**
indices:
- digits for 2–10;
- the **suit-coloured court symbols** for J/Q/K (a small crown for K, a flower for Q, a feather for J);
- a large pip for A.

No letters are baked in, so RU and EN read the same. (The research, R§8, allows digits and symbols.)

Reused from global and tables (not counted): `fx/chip_<d>`, `fx/chip_side_<d>`, `fx/sparkle`,
`panel/felt`, and the button families.

### 6.2 Java code assets

| File | Kind |
|---|---|
| `client/table/cards/CardSprites.java` | atlas UVs, back per game, size tiers |
| `client/table/cards/CardAnimator.java` | per-slot diff → tween queue (K1–K13), pooled |
| `client/table/cards/ChipStackView.java` | wraps tables' `ChipSprites` for bet spots, pots and racks |
| `client/table/cards/TableStamp.java`, `ActionTag.java` | K7, K12 |
| `core/anim/cards/CardMotion.java` (common, pure) | Bézier, flip scale, squeeze `f(u)`, jitter seed |
| `games/<g>/logic/<G>Beats.java` (pure) | beat schedules (§0.2) |
| `client/table/CardTableRenderer.java` + `games/<g>/client/<G>TableRenderer.java` ×4 | BERs |
| `client/dealer/DealerModel.java`, `DealerGesture.java` | shared dealer gestures |
| `particles`: reuse global `chip_pop`, `sparkle`; new `card_suit` (4 sprites 8×8, tumbling suits for BIG+ card wins) | 4 PNG + `particles/card_suit.json` |

### 6.3 Bedrock assets

| Asset | Path | Count |
|---|---|---|
| Glyphs U+E1D0–E1DB | `packs/core/RP/font/glyph_E1.png` via S1 | 12 cells: E1D0 narrow back, E1D1 card edge, E1D2 peel ¼, E1D3 peel ½, E1D4 empty slot, E1D5 card in flight (tilted back + motion lines), E1D6 dealer button, E1D7 pot, E1D8 small chip stack, E1D9 hatched chip, E1DA best-hand marker ▲, E1DB muck. E1DC–E1DF reserved. |
| Dealer gestures | `packs/core/RP/animations/burmaldaholic/dealer_gestures.animation.json` (deal 0.30 s, flip 0.35, peek 0.6, pay 0.45, sweep 0.5, shuffle 1.6, wave_off 0.3, squeeze_offer 0.5; bones `rightArm`, `leftArm`, `head`) + `animation_controllers/burmaldaholic/dealer.ac.json` (idle sway) | 2 files |
| Dealer entity updates | `packs/{blackjack,baccarat,uth}/RP/entity/*/dealer.entity.json`: add the animation map + controller | 3 edits |
| Particles | `packs/core/RP/particles/cards/card_suit_burst.json` (emitter, `v.count`, 4-frame suit flipbook from the global particle atlas row) | 1 file + an atlas row |
| Sounds | `packs/core/RP/sounds/sound_definitions.json` entries (§7) | 8 new ids |
| Form icons (32×32) | `packs/core/RP/textures/burmaldaholic/icons/cards/{hit, stand, double, split, insurance, fold, check, call, raise, all_in, player, banker, tie, pair, deal, rebet, clear_bets, play_bet, take_bank, banco}.png` | 20 PNGs |
| NICE `card_hand` entity | BP entity, RP entity/geo/render controller/animation/controller, `textures/entity/cards/faces.png` (copy of Java #29) | 6 files + 1 PNG, generated |

### 6.4 Asset count

- **Java:** 3 face atlases + 1 back atlas + 1 world atlas + 34 sprite PNGs (#5–#28) + 4 particle PNGs
  ≈ **43 PNGs**; 10 nine-slice / animation `.mcmeta`; 1 particle JSON; 8 new sound events.
- **Bedrock:** 12 glyph cells + 20 icons + 1 particle atlas row ≈ **21 PNG assets**; 2 animation
  files, 3 entity edits, 1 particle JSON, 8 sound definitions. NICE: 6 entity files + 1 PNG.

---

## 7. Sounds

The same ids in both editions. MUST uses vanilla compositions (global §2.7 rule).
- Java: `java/src/main/sounds/core/sounds.json`, played by code with the given pitch and volume.
- Bedrock: `burmaldaholic.<id>` in `packs/core/RP/sounds/sound_definitions.json`.

| Id | Feel | Java composition | Bedrock composition | Subtitle |
|---|---|---|---|---|
| `card_deal` (exists, re-pointed) | crisp card landing on felt | `item.book.page_turn` p1.3 v0.6 + `block.wool.hit` p1.6 v0.3 | `item.book.page_turn` p1.3 | exists |
| `card_slide` | quiet felt whoosh as a card leaves the shoe | `block.wool.step` p1.8 v0.3 | `step.cloth` p1.8 v0.3 | `card_slide` |
| `card_flip` | snappy flip | `item.book.page_turn` p1.7 v0.55 | `item.book.page_turn` p1.7 | `card_flip` |
| `card_squeeze` | slow paper bend, tense | `item.book.put` p0.6 v0.45 + `block.scaffolding.step` p1.4 v0.2 | `item.book.put` p0.6 | `card_squeeze` |
| `card_gather` | cards swept into a pile | `item.bundle.insert` p1.2 v0.5 | `bundle.insert` p1.2 | `card_gather` |
| `card_sting` | short bright flourish (BJ, natural, bonus) | code arpeggio: `block.note_block.chime` p1.0 / 1.26 / 1.5 at 0 / 80 / 160 ms + `block.amethyst_block.chime` v0.4 | `note.chime` ×3 scheduled 0 / 2 / 3 t | `card_sting` |
| `chip_push` | heavy chip pile pushed in (all-in, Banco) | `block.chain.break` p1.3 v0.6 + `item.bundle.drop_contents` v0.5 | `bundle.drop_contents` + `random.click` p0.8 | `chip_push` |
| `pot_win` | pot collected | `item.bundle.drop_contents` p1.1 v0.7 + `entity.experience_orb.pickup` p0.8 v0.4 | `bundle.drop_contents` + `random.orb` p0.8 | `pot_win` |
| `table_knock` | two knuckle raps (check) | `block.wood.hit` p0.8 v0.7, played twice 90 ms apart by code | `hit.wood` p0.8 ×2 (2 t) | `table_knock` |

- Reused: `card_shuffle`, `chip_place`, `chip_stack`, `chip_count`, `chip_sweep` (tables), `push`,
  `ui_deny`, `win_*`, `jackpot`, `wheel_tick`.
- The `card_*` and `table_knock` ids are table sounds: positional, `SoundSource.BLOCKS`, played by the
  BER for non-seated viewers and by the screen for seated ones (Java §1.4 rule). On Bedrock they use
  `dimension.playSound` at the table.
- Private-card sounds (your own hole cards) are played per player.
- The Bedrock vanilla event names above are marked **(verify)** against the 1.26.30 sound list.

---

## 8. Server data and config keys (summary)

Config (`CONFIG.md` format candidates, section per game; all are ints in ticks unless noted):

| Key | Default | Range | Meaning |
|---|---|---|---|
| `cards.soloSpeed` | 0.75 | 0.25–1.0 (double) | beat multiplier when one human is seated and there are no spectators (§0.2) |
| `blackjack.fx.dealBeatTicks` | 6 | 2–20 | one card per beat |
| `blackjack.fx.dealerDrawTicks` | 14 | 4–40 | dealer draw spacing |
| `blackjack.fx.holeFlipTicks` | 10 | 4–40 | |
| `blackjack.resultTicks` | 80 | 40–200 | was a hard-coded 60 |
| `poker.fx.dealBeatTicks` | 3 | 1–10 | |
| `poker.fx.gatherTicks` / `streetTicks` / `showBeatTicks` / `awardTicks` | 8 / 12 / 10 / 12 | 2–40 | |
| `poker.fx.runoutPauseTicks` | 24 | 0–60 | sweat pause per run-out street |
| `poker.fx.bigPotBb` / `monsterPotBb` | 50 / 100 | 10–1 000 | moment thresholds |
| `poker.exposeAllIn` | true | bool | **rules change: GAME_DESIGN §7.3 owner approval** |
| `uth.fx.dealBeatTicks` / `boardSlideTicks` / `qualifyTicks` | 3 / 8 / 16 | 1–40 | |
| `baccarat.squeeze` | true | bool | per-table (owner-settable) |
| `baccarat.fx.squeezeTicks` | 20 | 10–60 | |
| `baccarat.revealTicks` | 160 (was 80) | 40–400 | now the cap (§4.2) |

The **per-game** server data is listed in §1.7, §2.7, §3.6 and §4.9. The **common** fields:
- `beatTick` (server game time of the last beat) and `gateTick` in every table state;
- `result {net, stake, tier, bets[].ret}` per viewer;
- the public BER tag `pub` (Java `getUpdateTag`, public cards only, ≤ 1 KB);
- dealer gesture events.

---

## 9. Developer task breakdown (parallel lanes)

Dependencies are listed per task. The shared tasks come first. After that, each game's server task and
screen task can proceed in parallel per edition.

### 9.1 Shared (both editions)

| # | Task | Files | Depends on |
|---|---|---|---|
| C0 | `CardMotion` pure math (Bézier, flip, squeeze `f(u)`, jitter seed) + vectors `vectors_cards.json` | Java `core/anim/cards/CardMotion.java` (+ test); Bedrock `core/logic/anim/card-motion.ts` (+ vitest) | global S3/Ease |
| C1 | Beat schedules per game: `BlackjackBeats`, `PokerBeats`, `UthBeats`, `BaccaratRevealTimeline` + honesty tests (§0.7.3) | Java `games/<g>/logic/*Beats.java`; Bedrock `games/<g>/logic/beats.ts`; baccarat replaces `logic/round.ts#revealFrames` | — |
| C2 | Card assets in the generator (§6.1, §6.3 glyphs and icons) | `scripts/fx/cards.py` (called by S1) | global S1 |
| C3 | Strings §11 into STRINGS.md + lang files | STRINGS.md, `java/src/main/lang/*`, `bedrock/lang/*` | — |
| C4 | Sound definitions §7 (both editions) + subtitles | `java/src/main/sounds/core/sounds.json`, `CoreSounds.java`; `bedrock/packs/core/RP/sounds/sound_definitions.json` | — |
| C5 | Config keys §8 into CONFIG.md, `*Config` sections, Bedrock `config.ts` / gen-config | CONFIG.md, `core/config/sections/*`, `bedrock/tools/gen-config.mjs` | — |

### 9.2 Java lanes

| # | Task | Files | Depends on |
|---|---|---|---|
| J-C1 | Blackjack server pacing: beat publication, dealer draw pacing, peek beat, RESULT 80 t, `gateTick`, `result.tier`, `pub` tag, dealer gesture events | `games/blackjack/BlackjackTableBlockEntity.java`, `logic/BlackjackRound.java` (visible counts) | C1, global S3 |
| J-C2 | Poker server: deal / gather / burn / street / show / award beats, run-out pause, `showOrder`, `bestFive`, `potAwards`, `uncalledReturn`, `lastAction`, `exposed`, `pub` | `games/poker/PokerTableBlockEntity.java`, `logic/*` | C1 |
| J-C3 | UTH server: deal beats, board slide, qualify beat, `bestFive`, per-circle `ret`, `pub` | `games/uth/UthTableBlockEntity.java` | C1 |
| J-C4 | Baccarat server: `revealTimeline`, `sq` flags, `squeezer`, cap config, `pub`; chemmy shoe owner in state | `games/baccarat/BaccaratTableBlockEntity.java`, `logic/*` | C1 |
| J-C5 | Client card kit: `CardSprites`, `CardAnimator`, `ChipStackView`, `TableStamp`, `ActionTag`, reduced-motion paths, narration throttle | `client/table/cards/*`, `CasinoTableScreen` hooks | C0, C2, global J (Ease, FxSettings), tables' `ChipSprites` |
| J-C6 | `BlackjackScreen` integration (§1.2–§1.3) | `games/blackjack/client/BlackjackScreen.java` | J-C5, J-C1 |
| J-C7 | `PokerScreen` integration (§2.2–§2.3) | `games/poker/client/PokerScreen.java` | J-C5, J-C2 |
| J-C8 | `UthScreen` integration (§3.2–§3.3) | `games/uth/client/UthScreen.java` | J-C5, J-C3 |
| J-C9 | `BaccaratScreen` integration incl. squeeze and chemmy (§4.3–§4.5) | `games/baccarat/client/BaccaratScreen.java` | J-C5, J-C4 |
| J-C10 | `CardTableRenderer` BER base + 4 subclasses, positional sounds rule, `seatAnchor` | `client/table/CardTableRenderer.java`, `games/<g>/client/<G>TableRenderer.java`, the client module registrations | C0, C2, the matching J-C1..4 (`pub`) |
| J-C11 | Dealer gestures: synced data on 3 dealer entities, `DealerModel`, renderers | `games/{blackjack,baccarat,uth}/*Dealer.java`, `*DealerRenderer.java`, `client/dealer/*` | J-C1/3/4 events |
| J-C12 | Bot seat presentation in the 4 screens (§5) | bots branch `worktree-agent-afbccfe640358d8fc`, then the four screens | J-C5, global J17 |
| J-C13 | HUD reveal gate (hold the balance / floater until `gateTick`) | `client/ClientCasinoState.java`, `client/hud/CasinoHud.java`, `ClientTableCache` | global HUD task |

Parallelism: C0–C5 go in parallel. Then J-C1..J-C4 (4 developers) and J-C5 in parallel. Then
J-C6..J-C9 (4 developers), J-C10, J-C11 and J-C12.

### 9.3 Bedrock lanes

| # | Task | Files | Depends on |
|---|---|---|---|
| B-C1 | Blackjack pacing: beats via `system.runTimeout` chains per table, forms opened at gates, dealer pacing, `fx.celebrate` at the gate, HUD gate | `games/blackjack/table.ts`, `render.ts` | C1, global B fx |
| B-C2 | Poker pacing: deal/street/show/award beats, the run-out title sequence, the best-hand line, `hud.holdTitle` | `games/poker/index.ts`, `text.ts` | C1 |
| B-C3 | UTH pacing: deal beats, qualify beat, per-bet stepped reveal | `games/uth/table.ts`, `render.ts` | C1 |
| B-C4 | Baccarat: `revealTimeline` in the house loop and chemmy, the squeeze title, shoe-pass lines | `games/baccarat/game.ts`, `chemmy.ts`, `text.ts` | C1 |
| B-C5 | Ticker builder (pure frames from beats + glyph ids) + the renderer on HUD channels (players + spectators) | `core/logic/anim/card-ticker.ts` (+ tests), `core/cards-fx.ts` | C0, C2 glyphs |
| B-C6 | Dealer gesture animations + controller + entity edits + `playAnimation` call helper | `packs/core/RP/animations/…`, `packs/{blackjack,baccarat,uth}/RP/entity/*`, `core/cards-fx.ts#gesture` | — |
| B-C7 | Particles (`card_suit_burst`) + sound playback helpers (table vs private) | `packs/core/RP/particles/cards/*`, `core/cards-fx.ts` | C4 |
| B-C8 | Form icons + body additions (board / pot lines, best hand, bead first) | the four games' form builders | C2 |
| B-C9 | Bot presentation in tickers (glyph, dots, emotes after results) | branch `worktree-agent-aaf0f54e81d19ae4a`: `core/bots/table-bots.ts`, `games/*/bots.ts` | B-C5, global B12 |
| B-C10 (NICE) | `card_hand` entity generator + table wiring | `bedrock/tools/gen-card-entity.mjs`, `packs/core/{BP,RP}/…`, the games' table code | C2, B-C1..4 |

Parallelism: B-C1..B-C4 (4 developers) after C1. B-C5, B-C6 and B-C7 in parallel from the start.

### 9.4 Acceptance tests (per lane)

- Pure tests:
  - The schedules match the Java/Bedrock vectors.
  - Honesty permutation tests (§0.7.3).
  - The squeeze `f(u)` is monotonic and identical for all 52 cards.
  - A pot award order equals the server settlement order.
- Server tests: at every beat of 1 000 random rounds, hidden cards are absent from the viewer and
  public states (§0.7.1). Results, chat lines and forms are emitted at or after `gateTick`.
- Manual: 5-seat blackjack with 3 bots; 6-max poker with an all-in run-out; UTH royal (debug deck);
  baccarat third-card coups with squeeze on and off; chemmy bank passing. Every one runs with reduce
  motion on and off, in EN and RU at GUI scale 2 and 4, and in compact mode.

---

## 10. MUST / NICE summary

**MUST (ship now):**
- Server beat pacing and reveal gates in all four games, in both editions.
- Java:
  - card sprites and the K1–K13 primitives;
  - chips, pots and stacks;
  - the blackjack peek, dealer draw pacing, bust / BJ moments;
  - the poker button travel, street gather / burn / flip, all-in run-out (the hand exposure is subject
    to rules approval), ordered showdown with the best-5 highlight and pot slides;
  - the UTH deal, qualify beat and per-circle settle;
  - the baccarat reveal timeline and squeeze, the chemmy shoe pass and Banco;
  - BERs for all four tables;
  - dealer gestures;
  - bot plates, thinking dots and emotes;
  - the HUD gate.
- Bedrock:
  - the glyph ticker for all four games;
  - the baccarat squeeze title;
  - the poker run-out title;
  - dealer gesture animations;
  - card sounds and particles;
  - form icons and the new body lines;
  - bot ticker presentation.

**NICE:**
- The interactive baccarat peel.
- Poker equity bars during a run-out.
- A poker dealer NPC.
- Bot chatter bubbles.
- The Bedrock `card_hand` in-world entity.
- The blackjack slow final flip.
- A baccarat big-road panel.
- An in-world UTH bank ticker.

---

## 11. New strings (STRINGS.md format)

RU is checked against the 1.45 × EN budget:
- Tags ≤ 90 px, fitting on the plate.
- Stamps are nine-slice and sized to the text.
- Bedrock action-bar segments ≤ 64 RU characters per line.
- Titles ≤ 18 RU characters.

RU rule: no name is the subject of a past-tense verb.

Shared keys that global owns (`…menu.settings.reduce_motion`, `…flashes`, `…fx.tier.*`,
`…fx.returned`) are **not** repeated here.

### cards (shared by the four games)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.cards.narrate.dealt` | %1$s receives %2$s | %1$s: карта %2$s |
| `gui.burmaldaholic.cards.narrate.shows` | %1$s shows %2$s | %1$s открывает: %2$s |
| `gui.burmaldaholic.cards.narrate.dealt_all` | Cards dealt | Карты розданы |
| `gui.burmaldaholic.cards.narrate.card` | %1$s of %2$s | %1$s, %2$s |
| `gui.burmaldaholic.cards.hidden_card` | face-down card | закрытая карта |
| `gui.burmaldaholic.menu.settings.squeeze_peel` | Baccarat: peel cards myself | Баккара: вскрывать карты самому |

### blackjack

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.blackjack.fx.blackjack` | BLACKJACK! | БЛЭКДЖЕК! |
| `gui.burmaldaholic.blackjack.fx.bust` | BUST | ПЕРЕБОР |
| `gui.burmaldaholic.blackjack.fx.twenty_one` | 21! | 21! |
| `gui.burmaldaholic.blackjack.fx.dealer_reveals` | Dealer reveals the hole card | Дилер открывает закрытую карту |
| `gui.burmaldaholic.blackjack.fx.dealer_draws` | Dealer draws | Дилер берёт карту |
| `gui.burmaldaholic.blackjack.fx.dealer_stands` | Dealer stands on %1$s | Дилер останавливается на %1$s |
| `gui.burmaldaholic.blackjack.fx.dealer_busts` | Dealer busts with %1$s! | У дилера перебор: %1$s! |
| `gui.burmaldaholic.blackjack.fx.dealer_blackjack` | Dealer blackjack | Блэкджек у дилера |
| `gui.burmaldaholic.blackjack.fx.doubled` | Doubled | Удвоено |
| `gui.burmaldaholic.blackjack.actionbar` | Dealer %1$s · %2$s | Дилер %1$s · %2$s |
| `gui.burmaldaholic.blackjack.actionbar_seat` | %1$s %2$s | %1$s %2$s |

### poker

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.poker.tag.check` | Check | Чек |
| `gui.burmaldaholic.poker.tag.call` | Call %1$s | Колл %1$s |
| `gui.burmaldaholic.poker.tag.bet` | Bet %1$s | Бет %1$s |
| `gui.burmaldaholic.poker.tag.raise` | Raise to %1$s | Рейз до %1$s |
| `gui.burmaldaholic.poker.tag.fold` | Fold | Пас |
| `gui.burmaldaholic.poker.tag.small_blind` | SB %1$s | МБ %1$s |
| `gui.burmaldaholic.poker.tag.big_blind` | BB %1$s | ББ %1$s |
| `gui.burmaldaholic.poker.tag.shows` | Shows | Открывает |
| `gui.burmaldaholic.poker.tag.mucks` | Mucks | Сбрасывает |
| `gui.burmaldaholic.poker.fx.all_in_runout` | All in — running the board | Олл-ин — открываем стол |
| `gui.burmaldaholic.poker.fx.big_pot` | Big pot! | Крупный банк! |
| `gui.burmaldaholic.poker.fx.monster_pot` | Monster pot! | Огромный банк! |
| `gui.burmaldaholic.poker.fx.best_hand` | Best hand: %1$s | Лучшая рука: %1$s |
| `gui.burmaldaholic.poker.fx.wins` | %1$s wins %2$s | %1$s забирает %2$s |
| `gui.burmaldaholic.poker.fx.split` | Split pot: %1$s each | Банк делится: по %1$s |
| `gui.burmaldaholic.poker.fx.returned` | Returned %1$s | Возврат %1$s |
| `gui.burmaldaholic.poker.actionbar` | Board %1$s · Pot %2$s | Стол %1$s · Банк %2$s |

### uth

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.uth.tag.play` | Play ×%1$s | Плей ×%1$s |
| `gui.burmaldaholic.uth.fx.blind_bonus` | Blind bonus! | Бонус блайнда! |
| `gui.burmaldaholic.uth.fx.trips_bonus` | Trips bonus! | Бонус трипс! |
| `gui.burmaldaholic.uth.fx.royal` | ROYAL FLUSH! | РОЯЛ-ФЛЕШ! |

### baccarat

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.baccarat.fx.squeezing` | %1$s squeezes… | %1$s вскрывает… |
| `gui.burmaldaholic.baccarat.fx.you_squeeze` | You squeeze… | Вы вскрываете… |
| `gui.burmaldaholic.baccarat.fx.player_card` | Player's card… | Карта игрока… |
| `gui.burmaldaholic.baccarat.fx.banker_card` | Banker's card… | Карта банкира… |
| `gui.burmaldaholic.baccarat.fx.tie_pays` | Tie pays! | Ничья платит! |
| `gui.burmaldaholic.baccarat.fx.banco` | Banco! | Банко! |
| `gui.burmaldaholic.baccarat.fx.shoe_passes` | The shoe passes to %1$s | Шуз переходит к игроку %1$s |

### subtitles (new sound events)

| Key | EN | RU |
|-----|----|----|
| `subtitles.burmaldaholic.card_slide` | Card slides | Скользит карта |
| `subtitles.burmaldaholic.card_flip` | Card flips | Переворачивается карта |
| `subtitles.burmaldaholic.card_squeeze` | Card slowly bent | Карту медленно отгибают |
| `subtitles.burmaldaholic.card_gather` | Cards gathered | Карты собирают |
| `subtitles.burmaldaholic.card_sting` | Card flourish | Карточный туш |
| `subtitles.burmaldaholic.chip_push` | Chips pushed in | Фишки двигают в банк |
| `subtitles.burmaldaholic.pot_win` | Pot collected | Банк забирают |
| `subtitles.burmaldaholic.table_knock` | Knock on the table | Стук по столу |

Existing keys reused, not duplicated:
- `gui.burmaldaholic.card.rank.*`, `…card.suit.*`
- `…blackjack.dealer_peeks`, `…blackjack.shuffling`, `…blackjack.result.*`
- `…poker.all_in_tag`, `…poker.hand.*`, `msg…poker.wins_pot`
- `…uth.qualifies`, `…uth.not_qualifies`, `…uth.tag.*`
- `…baccarat.natural`, `…player_draws`, `…banker_draws`, `…player_stands`, `…banker_stands`,
  `…no_more_bets`, `…snapped`, `…burned.*`, `…line.commission`, `…actionbar`
- `…bots.thinking`, `…bots.level.*`
- global `…fx.returned`, `…fx.tier.*`
