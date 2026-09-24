# Burmaldaholic — UI / UX Specification

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
- Bedrock form buttons: keep labels ≤ **24 characters RU** on one line; the form wraps longer
  labels to 2 lines (allowed, max 2). Use the button *icon* for meaning and the text for the verb.
- Never concatenate translated fragments in code; always use a whole key with placeholders.
- Numbers: digits grouped by a regular space every 3 digits from 10 000 up (`12 500`), same in
  EN and RU (formatted in code, passed as `%s` string argument). Never use `,` or `.` as group
  separator (it is a decimal sign in RU).
- Chip icon: custom glyph **U+E100** (Java: added to `minecraft:default` font via a bitmap
  provider in `assets/burmaldaholic/font/default.json`; Bedrock: `font/glyph_E1.png`, cell 0x00).
  Card glyphs: U+E110–U+E14F (52 cards + back), suits U+E150–U+E153, dice faces U+E160–U+E165,
  streak flame U+E170, rain-cloud U+E171, VIP badges U+E180–U+E185. Same code points in both
  editions (Bedrock sheet `glyph_E1.png`, 16×16 grid). Strings never contain these glyphs; code
  prepends them as separate text components.
- Colors (both editions support § codes): win = §a (green), loss = §c (red), push = §7 (gray),
  jackpot/golden = §6 (gold). VIP tier colors: Bronze §c, Silver §7, Gold §6, Platinum §f,
  Diamond §b, Netherite §5.
- Sounds: chip clink on bet (`burmaldaholic:chip_place`), win jingle, loss thud, jackpot fanfare,
  coin flip whoosh. Same sound event ids on both editions.
- Every screen closes with Esc (Java) / the form X (Bedrock). Closing mid-round never cancels a
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

### 0.3 Bedrock form framework (`@minecraft/server-ui`)

Available form types and their constraints (design must stay inside them):

| Form | Can show | Constraints |
|------|----------|-------------|
| `ActionFormData` | title, body text (rawtext, § colors, glyphs), list of buttons (text + optional icon texture), optional labels/dividers/headers (newer API) | One choice per show. No live update: to refresh state, close and re-show. Practical max ~12 buttons before scrolling hurts. |
| `ModalFormData` | title, controls: toggle, slider (min/max/step/default), dropdown, text field, (labels), custom submit label | Values only; no per-control validation in UI → server validates and re-shows with an error line. Slider max range should be ≤ ~100 steps, so use step = bet increments. |
| `MessageFormData` | title, body, 2 buttons | Confirmations only. |

Rules:
- A form can be closed by the player (`canceled`, reason `UserClosed`) or not shown at all
  (`UserBusy`, e.g. chat open) → retry every 10 ticks up to 5 s, then treat as "no action".
- Timers: forms cannot display a live countdown. Put the deadline in the body ("Auto-stand in
  20 s"), and when the server timer expires, apply the default action and call
  `uiManager.closeAllForms(player)` then show the next state.
- All text is sent as rawtext `{translate, with}` so the client resolves it in its own language.
  Button text also uses rawtext.
- Card/dice/slot rendering in body text uses glyphs (§0.1), e.g. `Dealer: [A♠][?]  (11)`.
- Between forms, the action bar shows a one-line summary (so spectators and the player see
  progress while the form is closed).

---

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
- **Bedrock**: primary implementation is a resource-pack **JSON UI** panel in `hud_screen`
  bound to the title text: the script sends `player.onScreenDisplay.setTitle("§b§m§h" + payload)`
  with a sentinel prefix and zero fade/stay timings; the JSON UI hides real titles starting with
  the sentinel and renders the payload lines in the panel. Refresh when values change (max
  every 10 ticks). Fallback when the JSON UI hack is disabled (`core.hud.enabled` still true but
  resource pack missing): action bar `⛁ 12 500 · Lucky ×4 · Gold` every 40 ticks when no other
  action-bar message was sent in the last 60 ticks.

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

Bedrock hub body: 3 lines (balance, VIP, streak). Buttons with icons in the order above.

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
Bedrock: ActionForm "Cashier" → buttons: Deposit all chips · Withdraw… (ModalForm: text field
amount + dropdown "Denomination: auto / 500 / 100 / 25 / 5 / 1") · Buy chips… (slider emeralds
1–64 showing resulting chips in label) · Sell chips… (slider) · Contracts · Shop · Close.
Errors re-show the form with a red first body line (`gui.burmaldaholic.error.*`).

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

Bedrock flow:
1. **Bet** — ModalForm: title "Blackjack — Bet", label "Balance 12 500 · Min 1 · Max 1 000",
   slider "Bet" (step chosen so ≤ 100 steps: step = max(1, ceil((max−min)/100) rounded to 1/5/25/…)),
   text field "or exact amount", submit "Deal".
2. **Insurance** (if needed) — MessageForm "Dealer shows an Ace. Insurance?" [Insure (half bet)] [No].
3. **Turn** — ActionForm: body shows dealer and your hand(s) with glyphs, totals, bet, timer
   text; buttons: Hit · Stand · Double · Split (only legal ones, with icons). Re-shown after
   each action.
4. **Result** — ActionForm: body with all hands and outcomes, net result; buttons: Play again
   (same bet) · Change bet · Leave.

---

## 5. Texas Hold'em

Java (400 × 240 wide layout, compact 320 × 220): oval table with 6 seat plates (name, stack,
last action, cards), community cards center, pot(s) under them ("Pot 340 · Side pot 120"),
dealer button chip.
Action bar bottom: `[Fold] [Check/Call 20] [Raise to …]` + raise slider (min raise → all-in)
with quick buttons `[½ pot] [¾ pot] [Pot] [All-in]`, timer ring on the active seat.
Seat plate width fits a 16-char name + RU "Олл-ин" tag; long names truncated with "…" (names only).

Bedrock flow:
- **Join** — ActionForm: stake level buttons (locked ones show lock icon + "Requires Gold VIP").
  → ModalForm buy-in slider (40–100 BB).
- **Waiting** — action bar only ("Waiting for the hand… 3 players").
- **Your action** — ActionForm: body = board, your cards, pot(s), stacks list (≤ 6 lines),
  to-call amount, timer text. Buttons: Fold · Check/Call X · Raise… · All-in X.
  Raise… → ModalForm slider from min raise to stack (step = BB), submit "Raise".
- **Showdown** — ActionForm body with each shown hand + hand name; buttons: Next hand · Stand up.
- Opponent actions stream to the action bar ("Creeper42 raises to 60").

---

## 6. Slots

Java (256 × 200): 3×3 reel window (48 px cells) with the active paylines drawn as colored lines
(1/3/5), jackpot meter on top for Gold/Netherite ("JACKPOT ⛁ 61 240"), paytable button (opens a
scrollable overlay), bet: line bet `[−] 5 [+]`, total bet, `[SPIN]` big button, `[Auto ×10]`
(stops on any win ≥ 20× or balance < bet). Winning lines flash; payout counter rolls up.

Bedrock flow:
- **Machine** — ActionForm: body = last result grid (3 rows of glyphs), paylines won, jackpot,
  line bet and total; buttons: Spin (X) · Change bet · Paytable · Leave.
- Spin → the server plays a 40-tick animation via the action bar (random glyph rows scrolling),
  then shows the machine form again with the result. "Spin ×10" option runs 10 sequential spins
  showing only a summary.
- Change bet → ModalForm slider (line bet).
- Paytable → ActionForm body listing symbols (glyph + name + pay), one button "Back".

---

## 7. Roulette

Java (400 × 240): betting layout grid 3 × 12 + 0 column, outside bets below (dozens, columns on
right, even-money row). Click a cell = straight; click an edge between two numbers = split;
outer edge of a row = street; intersection of 4 = corner; outer intersection of two rows = six
line; hover highlights covered numbers and shows "Split 17:1". Chip value selector at the
bottom, `[Clear] [Rebet] [Spin]`, history strip (last 12, colored). Wheel animation panel
replaces the grid during SPIN.

Bedrock flow:
- **Table** — ActionForm: body = your current bets (list), total, history; buttons: Add bet ·
  Clear bets · Rebet · Spin (or "Ready" in multiplayer) · Leave.
- **Add bet** — ActionForm bet type list: Straight · Split · Street · Corner · Six line · Trio ·
  First four · Dozen · Column · Red · Black · Odd · Even · 1–18 · 19–36 (15 buttons, grouped with
  headers "Inside" / "Outside").
- **Bet details** — ModalForm: dropdown for the position (e.g. Split: "17–18", "17–20" …, only
  valid combinations pre-generated; Straight: 0–36; Corner: "1-2-4-5" …), amount text field +
  slider, submit "Place bet".
- Spin → action bar animation of numbers (wheel order) slowing down, then result form:
  "17 Black — you win 180".

---

## 8. Craps

Java (400 × 240): table layout with Pass line, Don't Pass bar, Come, Don't Come, Field, point
boxes 4/5/6/8/9/10 with the puck (ON/OFF), odds placed by clicking behind a line bet. Dice tray
with 2 dice glyphs, `[ROLL]` for the shooter only, shooter name, betting window timer.

Bedrock flow:
- **Table** — ActionForm: body = puck state and point, your bets, last roll; buttons (legal only):
  Pass · Don't Pass · Come · Don't Come · Field · Odds… · Roll (shooter) · Leave.
- Bet buttons open a ModalForm amount (slider + text). Odds… → dropdown of eligible bets and the
  amount snapped to valid multiples (label explains "Must be a multiple of 5").

---

## 9. Extras

- **Coin Flip** (Lucky Coin): Java small screen 200 × 140: `[Heads] [Tails]`, bet selector,
  optional `[Stake…]` (pawn: Item / XP / Hearts). Bedrock: ActionForm Heads/Tails/Stake type →
  ModalForm amount → result MessageForm "Heads! You win 96" [Again] [Close].
  Soul Wager (Hardcore + enabled): dedicated red button; first MessageForm warning, second
  MessageForm "Type-to-confirm" substitute: ModalForm text field requiring the word from
  `gui.burmaldaholic.extras.soul_confirm_word` ("DEAL" / «СДЕЛКА»); Java: hold the button 5 s.
- **Wheel of Fortune**: Java: rendered wheel (54 segments) + pointer, bet selector, Spin.
  Bedrock: ActionForm body with segment legend; Spin → action-bar animation of segment names →
  result.
- **Scratch Card**: Java: use item → 3×3 grid of silver cells; click to scratch; "Scratch all".
  Bedrock: ActionForm body with the 3×3 grid (revealed glyphs / ▒), buttons: Scratch next ·
  Scratch all · Close (card keeps progress).
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
Bedrock: ActionForm body greeting + status; buttons: one per available product
("Borrow 500 → repay 600 in 3 days"), locked ones hidden (a line in the body says how many are
locked) · Pay… · Leave. Confirmation MessageForm before any loan.

**Collector negotiation** (§5.5): Java: small dialog screen with the leader's line and 3 buttons;
Bedrock: ActionForm with 3 buttons; 10 s timeout text; auto-closes on timeout.

---

## 11. Casino Charter (owner)

Java (320 × 220) tabs: Overview (bankroll, reserved, available, today/total profit, status Open /
Closed-broke), Tables (list: type, position, min/max, open toggle, bots toggle), Bankroll
(Deposit / Withdraw with amount), Stats.
Bedrock: ActionForm hub → sub-forms (ModalForm for table settings: toggle Open, text fields
Min/Max, toggle Bots).

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

Bedrock flow:
- **Table** — ActionForm: body = your bets (list), total, last coup (`Player [8♠][K♥] 8 · Banker
  [9♦][7♣] 6`), last 12 bead letters with § colors, limits line, "Bets close in 12 s"; buttons: Player
  (1:1) · Banker (1:1 −5 %) · Tie (8:1) · Player Pair (11:1) · Banker Pair (11:1) · Clear bets ·
  Rebet · Deal / Ready · Rules · Leave. RU labels ≤ 24 characters («Пара банкира (11:1)» = 19).
- **Bet amount** — ModalForm: label with the box and its limits (Banker: "Multiples of 20"), slider
  (step per §4 rules; Banker step 20) + text field, submit "Place bet". The server snaps the Banker
  amount and re-shows the table with `…baccarat.snapped`.
- **Coup** — action bar animation: `Player [8♠][?] · Banker [9♦][?]` → both cards → third cards,
  10 ticks per card, then the **Result** ActionForm: both hands with totals, result line, per-bet
  lines, net; buttons: Same bets again · Change bets · Leave.
- **Chemin de fer**: BANK_OFFER → ActionForm "You are offered the bank" with buttons `Take the bank
  (1 000)` · `Other amount…` (ModalForm text field) · `Pass`; a winning banker gets `Keep the bank
  (2 280)` · `Pass the bank`. Punters' table form: buttons `Bet on Player…` · `Banco (1 000)` ·
  Clear bets · Ready · Leave; body shows the bank, coverage and open coverage.
- Spectators and players between forms: action-bar summary `…baccarat.actionbar`.

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

Bedrock flow:
1. **Bets** — ModalForm: title "Ultimate Hold'em — your bets", label "Balance 12 500 · Ante 1 – 16
   (6× Ante + Trips ≤ 100)", slider "Ante" + text field, slider "Trips (optional, 0 = no bet)",
   submit "Deal". The server re-shows it with an error line when W or the balance rule fails.
2. **Decision** — ActionForm per street: body = board, your cards, your current hand name, your
   bets, "Still deciding: 2 players", timer text ("Auto-check in 20 s" / "Auto-fold in 20 s");
   buttons: only the legal options with amounts. Re-shown on each street.
3. **Waiting** — action bar only (`…uth.actionbar`) while other seats decide or after your Play bet.
4. **Result** — ActionForm: dealer hand + qualify line, your hand, per-bet lines, net; buttons:
   Play again (same bets) · Change bets · Paytable · Leave.
- **Player-banked**: the table hub adds `Take the dealer seat…` (ModalForm: bank slider/text, submit
  "Take the seat") or, for the banker, a Banker form (body: seats and their bets, bank, reserved;
  buttons: Leave the dealer seat · Close). Errors (`…uth.error.bank_cover`) name the largest Ante
  the bank covers.
