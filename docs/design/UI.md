# Burmaldaholic — UI / UX Specification

> **Java-only (2026-09-24).** Bedrock support was dropped: Bedrock sections, lanes and tasks were removed. An inline
> note that still names Bedrock (the former TypeScript twin) is historical context and does not apply.

Covers: HUD, Casino Menu, Cashier, every table/machine screen, Loan and Charter screens, and the
Bedrock form flows. All labels are translation keys from `STRINGS.md` (shown here as the English
text in quotes, with the key where it matters).

## 0. Global rules

### 0.1 Text length and Russian

- Russian strings are **20–40 % longer** than English and use wider glyphs (Ж, Ш, Щ, Ю). Every
  layout must be tested with the RU file. Design rule: **reserve 1.45 × the English pixel width**
  for any single-line label.
- Java: widths are computed at runtime: `buttonWidth = max(minWidth, textRenderer.getWidth(label) + 8)`,
  laid out in flow rows; if a row overflows the panel, the row wraps (never truncate mid-word).
  Tooltips wrap at 200 px. Numbers are right-aligned.
- Never concatenate translated fragments in code; always use a whole key with placeholders.
- Numbers: digits grouped by a regular space every 3 digits from 10 000 up (`12 500`), same in
  EN and RU (formatted in code, passed as `%s` string argument). Never use `,` or `.` as group
  separator (it is a decimal sign in RU).
- Chip icon: custom glyph **U+E100** (Java: added to `minecraft:default` font via a bitmap
  provider in `assets/burmaldaholic/font/default.json`).
  Card glyphs: U+E110–U+E14F (52 cards + back), suits U+E150–U+E153, dice faces U+E160–U+E165,
  streak flame U+E170, rain-cloud U+E171, VIP badges U+E180–U+E185. One 16×16-cell sheet
  (`glyph_e1.png`). Animation wave additions (all drawn in the same
  sheet by `tools/assets/gen-assets.mjs`, module core; Java `textures/font/core/glyph_e1.png`): HUD pips,
  sun, bell, collectors U+E172–E177; coin frames U+E186–E18B; bot, thinking dots, mini chips, emerald,
  gold ingot, sparkle U+E190–E19B; PvP U+E1A0–E1A1; extras U+E1A2–E1B8; tables U+E1C0–E1CB; cards
  U+E1D0–E1DB; dye swatches U+E1E0–E1EF. Full map and reserves: `docs/architecture/animation.md` §6. Strings never contain these glyphs; code
  prepends them as separate text components.
- Colors (§ codes): win = §a (green), loss = §c (red), push = §7 (gray),
  jackpot/golden = §6 (gold). VIP tier colors: Bronze §c, Silver §7, Gold §6, Platinum §f,
  Diamond §b, Netherite §5.
- Sounds: chip clink on bet (`burmaldaholic:chip_place`), win jingle, loss thud, jackpot fanfare,
  coin flip whoosh. Same sound event ids.
- Every screen closes with Esc. Closing mid-round never cancels a
  confirmed bet; it applies the timeout rules of the game.

### 0.2 Java screen framework

- All game screens are server-driven: the server owns state and sends `TableState` packets; the
  client screen only renders and sends `Action` packets (button id + amount). Screens re-render
  on every state packet.
- Base panel: 256 × 200 GUI px ("felt" background texture, 9-slice), centered, scales with GUI
  scale; minimum supported window at GUI scale 2 = 427 × 240 (1280 × 720 / 3 ≈ 427 × 240); if the
  scaled window is < 320 × 220, the screen switches to **compact mode** (cards 60 % size,
  history panels hidden).
- Common widgets: `BetSelector` (chip buttons 1/5/25/100/500 add to bet, "Clear", "Max",
  "Rebet"; shows current bet and limits line "Min 1 · Max 1 000"), `BalanceBar` (bottom-left:
  chip icon + balance; bottom-right: streak + VIP badge), `ResultBanner` (center, 40 ticks),
  `Timer` (top-right ring, red under 5 s).

## 1. HUD

Small panel, default top-left, 4 px margin, hidden when F1 / hud hidden, when chat is open
(Java: dims to 50 %), and when casino mode is off. Per-player toggle and corner in Casino Menu.

```
┌──────────────────────────────┐
│ ⛁ 12 500                     │  balance (gold when Golden Hour)
│ 🔥 Lucky ×4        ◆ Gold    │  streak (hidden at 0) · VIP badge + tier name
│ ⏳ Loan: 1d 04:12  owed 600  │  only with active loan (red + blinking in default)
│ ☀ Golden Hour 02:31          │  only during Golden Hour
└──────────────────────────────┘
```
- Line widths: EN ≤ 150 px, RU ≤ 200 px; panel width = widest line + 8 (Java). Background:
  black 40 % alpha.
- Balance change animation: `+120` floats up in green / `−50` in red for 30 ticks.
- Loan time format: `Xd HH:MM` in real-time minutes of remaining world ticks (1 MCD = "1d").
  Keys: `hud.burmaldaholic.loan`, `hud.burmaldaholic.loan_default`.
- Java: rendered with `HudRenderCallback` (or the 26.x HUD layer API), not overlapping the
  boss bar or status effects: when effects are shown at top-right and the HUD is TOP_RIGHT, move
  down by the effect-icon height.

Toasts: earnings (`msg.burmaldaholic.core.earned`) → action bar; big events → title/subtitle;
everything important also goes to chat.

---

## 2. Casino Menu (Casino Card / key `B`)

Java: tabbed screen 256 × 200; Bedrock: ActionForm hub.

| Tab / button | Content |
|--------------|---------|
| Wallet | Balance, lifetime wagered, today's net, VIP tier + progress bar to next tier ("12 400 / 25 000"), streak value. |
| Contracts | 3–5 contract rows: description, progress `7/24`, reward, Reroll button (cost). |
| Loan | Status (none / active / default), owed, deadline countdown, **Pay** (amount field), rules link. |
| Achievements | (Bedrock only; Java uses the advancement screen) list with ✔/✖. |
| Challenges | Pending Dice Duel challenges: Accept / Decline; "Challenge a player" (dropdown of players within range + amount). |
| My Casino | Only if the player owns a charter: shortcut to the Charter screen. |
| Rules | Current world rules: difficulty effects, Last Chance status and cooldown, Hardcore mode, chaos on/off. |
| Settings | HUD on/off, HUD corner, sounds on/off, auto-muck (poker). |
| Admin (ops) | World settings (config), give/take chips, clear debt, reset jackpot. |

---

## 3. Cashier

Java layout (256 × 180):
```
[ Cashier ]                                     [Balance ⛁ 12 500]
 Deposit:  [Deposit all chips]  [Deposit held stack]
 Withdraw: [ amount field ][500][100][25][5][1][Max]  [Withdraw]
           "Withdrawable: 11 900 (loan 600)"
 Exchange: [Buy 8 chips for 1 emerald]  [×10]   [Sell 10 chips for 1 emerald] [×10]
 Tabs: [Cashier] [Contracts] [Shop]  (Shop: scratch cards, lucky coin)
```

---

## 4. Blackjack

Java (320 × 220 when space allows, else 256 × 200 compact):
```
 Dealer            [A♠][▒]                     (11)          ⏱ 18
 ───────────────────────────────────────────────────────────────
 Seat1 Alex  [10♥][7♣] 17  bet 50     Seat2 YOU  [8♦][8♠] 16  bet 100 ◀
 Seat3 …
 ───────────────────────────────────────────────────────────────
 [Hit] [Stand] [Double] [Split] [Insurance]           Bet: [1][5][25][100][500][Clear][Rebet]
 ⛁ 12 500                                   🔥×4  ◆Gold      Min 1 · Max 1 000
```
- Buttons disabled (grayed with tooltip reason) when not legal.
- Split hands shown side by side, active hand underlined. Totals shown as `7/17` for soft hands.
- Result banners per hand: "Blackjack! +150", "Win +100", "Push", "Bust", "Dealer busts".

---

## 5. Texas Hold'em

Java (400 × 240 wide layout, compact 320 × 220): oval table with 6 seat plates (name, stack,
last action, cards), community cards center, pot(s) under them ("Pot 340 · Side pot 120"),
dealer button chip.
Action bar bottom: `[Fold] [Check/Call 20] [Raise to …]` + raise slider (min raise → all-in)
with quick buttons `[½ pot] [¾ pot] [Pot] [All-in]`, timer ring on the active seat.
Seat plate width fits a 16-char name + RU "Олл-ин" tag; long names truncated with "…" (names only).

---

## 6. Slots

Java (256 × 200): 3×3 reel window (48 px cells) with the active paylines drawn as colored lines
(1/3/5), jackpot meter on top for Gold/Netherite ("JACKPOT ⛁ 61 240"), paytable button (opens a
scrollable overlay), bet: line bet `[−] 5 [+]`, total bet, `[SPIN]` big button, `[Auto ×10]`
(stops on any win ≥ 20× or balance < bet). Winning lines flash; payout counter rolls up.

---

## 7. Roulette

Java (400 × 240): betting layout grid 3 × 12 + 0 column, outside bets below (dozens, columns on
right, even-money row). Click a cell = straight; click an edge between two numbers = split;
outer edge of a row = street; intersection of 4 = corner; outer intersection of two rows = six
line; hover highlights covered numbers and shows "Split 17:1". Chip value selector at the
bottom, `[Clear] [Rebet] [Spin]`, history strip (last 12, colored). Wheel animation panel
replaces the grid during SPIN.

---

## 8. Craps

Java (400 × 240): table layout with Pass line, Don't Pass bar, Come, Don't Come, Field, point
boxes 4/5/6/8/9/10 with the puck (ON/OFF), odds placed by clicking behind a line bet. Dice tray
with 2 dice glyphs, `[ROLL]` for the shooter only, shooter name, betting window timer.

---

## 9. Extras

- **Coin Flip** (Lucky Coin): Java small screen 200 × 140: `[Heads] [Tails]`, bet selector,
  optional `[Stake…]` (pawn: Item / XP / Hearts). Bedrock: ActionForm Heads/Tails/Stake type →
  ModalForm amount → result MessageForm "Heads! You win 96" [Again] [Close].
  Soul Wager (Hardcore + enabled): dedicated red button; first MessageForm warning, second
  MessageForm "Type-to-confirm" substitute: ModalForm text field requiring the word from
  `gui.burmaldaholic.extras.soul_confirm_word` ("DEAL" / «СДЕЛКА»); Java: hold the button 5 s.
- **Wheel of Fortune**: Java: rendered wheel (54 segments) + pointer, bet selector, Spin.
- **Scratch Card**: Java: use item → 3×3 grid of silver cells; click to scratch; "Scratch all".
- **Plinko**: Java: board 13 bins, ball animation along the server path (12 steps × 4 ticks),
  risk toggle `Low | Medium | High`, bin multipliers under the board. Bedrock: ModalForm (risk
  dropdown + amount) → action-bar path animation "◀ ▶ ▶ ◀ …" → result form.
- **Dice Duel**: challenge form (dropdown players, amount) → target gets MessageForm
  "Alex challenges you to a Dice Duel for 200 chips" [Accept] [Decline] → both see the result.

---

## 10. Loan Shark

Java (256 × 200): portrait of the shark, greeting line (random variant), table of loan products
(amount, interest, due amount, deadline, lock icon if VIP too low), `[Take loan]` → confirm
dialog with the exact due amount and deadline in days; if a loan exists: status + `[Pay]`
(amount field, `[Pay all]`).

**Collector negotiation** (§5.5): Java: small dialog screen with the leader's line and 3 buttons;

---

## 11. Casino Charter (owner)

Java (320 × 220) tabs: Overview (bankroll, reserved, available, today/total profit, status Open /
Closed-broke), Tables (list: type, position, min/max, open toggle, bots toggle), Bankroll
(Deposit / Withdraw with amount), Stats.

---

## 12. Result and error messaging

- Results: banner on Java screens + chat line `msg.burmaldaholic.<game>.result.*`; Bedrock:
  result form + chat line.
- Errors (`gui.burmaldaholic.error.*`): Java: red text under the action row for 60 ticks + deny
  sound; Bedrock: re-show the previous form with the error as the first body line in §c.
- Every limit error states the limit ("Maximum bet for your VIP tier is 1 000").

## 13. Accessibility

- Never rely on color alone: win/loss banners include words; red/black roulette numbers use
  filled vs outlined backgrounds on Java.
- Card glyphs include rank letters; suits have distinct shapes.
- All timers are at least 15 s by default for human decisions.
- Narrator (Java): screens provide narration messages for state changes (dealer card, result).

---

## 14. Baccarat (⚠ added 2026-09, GAME_DESIGN §20)

Java (400 × 240; compact 320 × 220 hides the bead plate and the side-bet row labels become icons
with tooltips):
```
 Baccarat                        Shoe: 287 cards left              ⏱ 14   [Rules] [Paytable]
 ┌───────── PLAYER ─────────┐            ┌───────── BANKER ─────────┐
 │ [8♠][K♥]   [ ]      8    │   NATURAL  │ [9♦][7♣]   [ ]      6    │
 └──────────────────────────┘            └──────────────────────────┘
 [ P.Pair 11:1 ] [ PLAYER 1:1 ] [  TIE 8:1  ] [ BANKER 1:1 −5% ] [ B.Pair 11:1 ]
      your 0         your 50        your 5          your 0             your 0
 Bead plate (6 × 10):  (P)(B)(B)(T)(P)…      Player 12 · Banker 15 · Tie 3
 Seats: Alex ✓ 120 · Steve 40 · YOU ◀ 55 · …                          Ready 2/3
 Bet: [1][5][25][100][500] [Clear] [Rebet] [Ready/Deal]        Min 1 · Max 1 000 · Banker ×20
 ⛁ 12 500                                                     🔥×4  ◆Gold
```
- Clicking a betting box adds the selected chip value; right-click removes one chip. The Banker box
  snaps down to the Banker step and flashes `…baccarat.snapped` when it did. Side boxes are hidden
  when `baccarat.pairBets` is off.
- Box labels are separate components (name + ratio) so RU fits at 1.45 × width: «Пара игрока 11:1»,
  «Банкир 1:1 −5 %». Minimum box width = max(EN, RU) label + 8 px; if the row overflows, the two pair
  boxes move to a second row.
- Bead plate: circles with a **letter** (P/B/T; RU И/Б/Н) and color (blue Player, red Banker, green
  Tie), a dot at the lower-left for a Player pair and upper-right for a Banker pair; never color
  alone (§13).
- Reveal: cards flip in deal order; third cards slide in with the line «Игрок берёт третью карту» /
  «Банкир берёт третью карту» under the hand; totals update live. Result banner "Banker wins 7 to 5"
  then per-bet lines ("Player −50", "Tie +40", "Commission 1").
- Rules overlay (button): `…baccarat.rules.*` lines and the §20.3 drawing table rendered as a grid.
- **Chemin de fer** (player-banked table): the layout keeps the two hands; the betting row is
  replaced by
  `[ Bet on Player ] [ BANCO 1 000 ]   Bank: 1 200 · covers 1 000 · open 350   Banker: Alex`.
  At BANK_OFFER the candidate sees a modal panel: amount field (default = last bank, min shown) and
  `[Take the bank] [Pass]`, or `[Keep the bank (2 280)] [Pass the bank]` for a winning banker, with
  the timer ring. The banker's own screen shows punters' bets and has no betting buttons.

---

## 15. Ultimate Texas Hold'em (⚠ added 2026-09, GAME_DESIGN §21)

Java (400 × 240; compact 320 × 220 shows other seats as one line each):
```
 Ultimate Texas Hold'em          Dealer: [▒][▒]     Flop                ⏱ 17   [Paytable]
            Board:  [Q♠][J♠][T♠] [▒] [▒]
 ┌ Seat 1 Alex  ✓ Play ×4 ┐ ┌ Seat 2 YOU ◀ ┐ ┌ Seat 3 Steve  Checked ┐ …
 Your cards [A♠][K♠]   Your hand: Straight (draw)        Still deciding: 2 players
 ( Trips 5 )  ( Ante 10 )  ( Blind 10 )  ( Play — )          At risk: up to 65
 [Check]  [Bet ×2 (20)]                                   Min 1 · Max 1 000 (6× Ante + Trips)
 ⛁ 12 500                                                          🔥×4  ◆Gold
```
- BETTING: chip buttons set the Ante (the Blind circle mirrors it), a Trips toggle + chip buttons,
  the limits line shows the resulting Ante range, `[Deal]`, `[Clear]`, `[Rebet]`.
- Decision buttons per street: preflop `[Check] [Bet ×3 (30)] [Bet ×4 (40)]`, flop
  `[Check] [Bet ×2 (20)]`, river `[Fold] [Bet ×1 (10)]`; disabled with a tooltip when unaffordable.
  After a Play bet the row shows "Play ×4 — waiting for the showdown".
- Seat plates: name, status tag (Deciding… / Checked / Play ×N / Folded) and bets; hole cards of
  other seats face down until SHOWDOWN. RU tags at 1.45×: «Думает…», «Чек», «Плей ×4», «Пас».
- SHOWDOWN: dealer cards flip, banner "Dealer qualifies" / "Dealer does not qualify — Ante pushes"
  (2 lines in RU if needed), then per-bet lines (Play +40 · Ante push · Blind 500:1 +5 000 ·
  Trips 50:1 +250) and the net.
- Paytable overlay: the Blind and Trips tables from the effective config.
- **Player-banked table**: an extra plate at the top "Dealer seat: Alex · Bank 12 000 · Reserved
  5 550" or "Dealer seat: the house" with `[Take the dealer seat]` (enabled only while free and
  before the first bet of the round; opens an amount field with the minimum bank) and, for the
  banker, `[Leave the dealer seat]` (label changes to "Leaving after this round"). The banker's
  screen has no decision buttons; it shows every seat and the running bank result.

