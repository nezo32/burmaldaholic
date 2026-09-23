# Bedrock edition: correctness review

Scope: `bedrock/src` (core, blackjack, craps, roulette, poker, slots, extras, loan, chaos,
lastchance, worldgen, vip, multiplayer). This was a read-only pass, and every finding below was
traced through the code end to end. Known issues that another developer is fixing are
excluded: the slot disconnect settle in beforeEvents, garnishment of push stakes, and the
world-store overflow.

**Verdict: approve with fixes.** Fix B1 and M1 before shipping, and M2 soon after. Everything
else is minor.

| Severity | Count |
|---|---|
| Blocker | 1 |
| Major | 2 |
| Minor | 9 |

Checked with no issues found:
- Core ticket lifecycle. `settle`/`refund` are idempotent (`open.delete` guard). Offline
  parking, restart recovery and the `resolved` list all hold up. Reservation, settle and
  release on bankrolls are correct.
- Form-response races. Blackjack uses its round/offer/snapshot checks, poker `isCurrent`+`seq`,
  roulette epochs + `isActive`, craps re-validates after the prompt, and extras check
  `s.isActive()`.
- ModalForm number parsing. `parseAmount` accepts at most 15 digits, and `validateBet` rejects
  NaN and non-positive values.
- beforeEvents handlers (except the known slots one) only cancel the event or touch in-memory
  maps.
- A static check of every `t()/plural()/variant()` call with a resolvable key found no missing
  keys and no placeholder-count mismatches against `lang/*/en_US.lang`.
- `check-strings` passes.

---

## B1 (Blocker): breaking a table block refunds rounds already in play, a free-roll exploit

- **Where:** `core/tables.ts:99-101` (`onPlayerBreak` → `closeTable(key, 'broken')`), then:
  - `games/roulette/table.ts:118` refunds every open slip, including while the wheel is
    spinning (phases `no_more_bets` and `spin`).
  - `games/poker/index.ts:335-345` calls `abortHand()`, which resets everyone to their
    start-of-hand stack (`poker/logic/table.ts:246`).
  - `games/craps/runtime.ts:535-538` refunds every working bet, including Pass/Come contract
    bets after a point is set.
- **Scenario:** tables are craftable (`packs/*/BP/recipes/*`). Worldgen casino tables and any
  table not protected by an owned casino can be broken by anyone. The block takes 2 s to mine.
  - **Roulette:** the result is drawn at `no_more_bets` (`round.ts:141`), and the action-bar
    strip decelerates onto it over `spinTicks` (5 s). A player can start mining and finish the
    break only when the strip shows a loss. They get a 100 % refund and keep any win.
  - **Poker vs bots:** facing a bot all-in with a weak hand, the player breaks the table. The
    hand is aborted and every chip they put in comes back. Chips are minted by the bank. The
    same trick lets a player cancel a pot another human is winning.
  - **Craps:** a Pass bet whose point is 4 or 10 wins only one time in three. Breaking the
    table turns it back into a full refund.
- **Rule:** GAME_DESIGN §4.1 says rounds are auto-completed with the default action and that
  there are no refunds. Only a server stop refunds.
- **Fix:** give `broken` the same handling as `leave`/`disconnect`:
  - Roulette: let the spin proceed and refund only while `canBet()`.
  - Poker: auto-fold the leaver and play the hand out. Only on `casino_off` do an abort (or a
    play-out).
  - Craps: `autoComplete`.

  Refunds for `broken` are only fair before any random draw: roulette betting phase, blackjack
  pre-deal (already correct).

  A cheaper partial fix: cancel `playerBreakBlock` (beforeEvents) for a table that has open
  tickets or a running hand.

## M1 (Major): poker pending cash-out is overwritten by a new buy-in, destroying chips

- **Where:** `games/poker/index.ts:150` (`payPending` skips players who are still seated),
  `:374` (the cash-out of a disconnected seat is saved as pending), and `:298`/`:437`
  (`saveStack(p.id, …)` keyed only by player id).
- **Scenario:**
  1. Player A disconnects mid-hand. `onLeave` marks the seat `disconnected` and keeps it until
     the hand ends.
  2. A rejoins while the hand is still running. On initial spawn `payPending` returns early
     because `isSeated`.
  3. If A re-opens the table, `onOpen` re-attaches the new Player, but `seat.disconnected`
     stays true. The seat is auto-folded, and at hand end `cashOut` takes the "offline"
     branch: `saveStack(id, X)`. Nothing is credited and the core session is not ended.
  4. A (online the whole time) buys in again at any poker table, and `joinFlow` runs
     `saveStack(A, Y)`, overwriting X. The X chips are lost for good.

  Even without step 4, X is only paid on the next relog.
- **Fix:**
  - In `cashOut`, credit whenever `livePlayer(undefined, id)` is online, whatever
    `seat.disconnected` says.
  - Clear `seat.disconnected`/`leaving` when `onOpen` re-attaches a rejoined player, or pay
    pending in `joinFlow` before the new `saveStack`.
  - Make `saveStack` for a new buy-in add to an existing pending amount instead of replacing
    it, or refuse the buy-in while pending > 0.

## M2 (Major): heart-stake penalties (and deferred soul deaths) are enforced while casino mode is off

- **Where:** `core/wagers.ts:291-297`. The `playerSpawn` handler and the 10-tick `runInterval`
  call `enforceHearts` with no `isCasinoEnabled()` check. `applyOffline` (`:626-635`) applies
  heart penalties and schedules `killBySoulWager` on join, also without the check.
- **Scenario:** a player loses a 3-heart stake (`wager.hearts.durationTicks`) and an op turns
  casino mode off. The player's health is still clamped 6 HP below max every half-second.
  Likewise, a Soul Wager lost while the player was offline kills them on join, even with the
  mode off. That is a permanent death on Hardcore.
- **Rule:** GAME_DESIGN §2.1 says the mod is dormant when casino mode is off, and §2.2 says
  vanilla behaviour is preserved.
- **Fix:** gate `enforceHearts` and the offline soul-death/hearts application on
  `isCasinoEnabled()`. Keep the parked entry, or shift the penalty expiry by the dormant time,
  as the loan module does.

---

## Minor

### m1: poker top-up by a folded player is lost if the hand is aborted
`games/poker/index.ts:736-738` adds the top-up to both `hand.players[k].stack` and `seat.stack`.
`abortHand()` (`logic/table.ts:251`) then resets `seat.stack = startStack`, so the paid top-up
disappears on `broken`/`casino_off`. **Fix:** also add the amount to `hand.players[k].startStack`,
or refuse top-ups while `inHand()`.

### m2: the trade-earning heuristic can be farmed with crafting next to a villager
`core/earning.ts:123-145` treats a trade as emerald count and other-item count changing in
opposite directions within 10 ticks with a villager within 6 blocks.
- Crafting 9 emeralds into an emerald block gives `dEm=-9, dOther=+1`, which counts as a trade.
  Uncrafting it counts again. So does dropping an emerald while picking up any item.
- Each cycle pays `tradeReward(9)` up to `economy.trade.dailyCap` per MC day. It also advances
  the VIP `trade` contract (`vip/contracts.ts:201`).

**Fix:** ignore changes whose "other" side is `emerald_block`. Better, require an open villager
trade UI, which is not detectable, so at least require that the player is not using a crafting
table and that the villager has `minecraft:trade_table`. Or accept the cap as the bound and
document it.

### m3: cashier "sell chips" does not re-check withdrawable at submit
`core/cashier.ts:150/161-168`:
- `max` comes from `withdrawable()` before the slider form.
- After the form only `debit(cost)` is checked, which uses the balance, not
  `withdrawable`/`inDefault`.
- A loan that defaults while the form is open (the loan tick runs every 20 t) still lets the
  player convert chips to emeralds.

Withdraw (`:139`) does re-check. **Fix:** recompute
`min(max, floor(withdrawable/sellRate))` after the response and bail when `inDefault`.

### m4: Soul Wager death relies on `player.kill()`
`core/wagers.ts:650-654`. GAME_DESIGN §4.4 requires the death to bypass totems. The code
applies no custom damage type and does not strip the totem, and on Bedrock `kill()` may pop a
Totem of Undying. It also broadcasts the custom death message on top of the vanilla death
message, so players see two messages. **Fix:** verify in game. If the totem saves the player,
clear the totem from the offhand and mainhand before `kill()`, and drop the manual broadcast
or suppress the vanilla one.

### m5: loan principal can be partly lost to the balance cap while the full debt is recorded
`loan/service.ts:171-173`: `credit()` may be capped at `economy.maxBalance`, but the record
stores the full principal. **Fix:** refuse the loan when `balance + principal > maxBalance`, or
record only the credited amount.

### m6: new players online when casino mode is switched on get no starting balance or card
`core/module-core.ts:81-87`: `firstJoin` only runs on the initial spawn with the mode already
on. **Fix:** also run `firstJoin` for every online player when the mode flips to on.

### m7: localization inconsistencies
- `games/blackjack/table.ts:279` passes raw numbers to `gui.burmaldaholic.common.limits`. Every
  other game passes `chips(...)`, so blackjack shows "Min 10 · Max 500" without the unit (and
  without the RU plural).
- `lang/extras/en_US.lang:30` has `…(%3 of 54)` hardcoded, but `extras.wheel.segments` is
  configurable (`wheelFromConfig` accepts any length ≥ 2). Pass the segment count as `%4`.
- `core/admin.ts:106` shows enum options without `optionLabels` as `lit(o)`, for example
  `HIGH_STAKES`.
- `lastchance/index.ts:133` formats half hearts with `toFixed(1)`, which gives "1.5" in RU
  instead of "1,5".

### m8: async flows started with `void` have no rejection handler
Examples:
- `roulette/table.ts:374,400,620`: `void this.showMain/showResult`.
- `craps/runtime.ts:339`: `void this.loop(s)`.
- `slots/index.ts:448`: `void this.showMachine(s)`.
- `extras/dice-game.ts:224`: `void answerForm`.

If the player becomes invalid mid-flow, calls such as `p.sendMessage` or
`economy.balance(p)` throw, and the error surfaces as an unhandled rejection with no context.
Money is safe, because the checks come before any mutation. **Fix:** route these through the
`safe()` pattern that blackjack uses, or add `.catch(log)`.

### m9: `Economy.debit` / `WagerService.raise` accept negative amounts
`core/economy.ts:134-139` and `core/wagers.ts:423-438`. `debit(-x)` runs `applyDelta(+x)` with
the `BALANCE_MAX` cap instead of `economy.maxBalance`. `raise(-x)` would credit the player and
lower `ticket.value`. No current caller can pass a negative value (all amounts come from
validated prompts or rule logic), so this is hardening. **Fix:** make both reject values that
are not safe integers greater than 0.

---

## Notes (no action required)
- Roulette tables whose last bettor walked away stay in `RouletteGame.tables` forever. They are
  skipped cheaply each tick. The `lastBets`/`lastPos`/`toasts` maps also grow by one small
  entry per player. The cost is negligible.
- Wager fallback routing in `multiplayer/ownership.ts:482-500` (`recent` table) is effectively
  dead code. Every `place()` gets a table key from the player's session, virtual tables
  included.
- Poker `recordPvp` skips humans who were offline at hand end, so they get no streak or VIP
  wagered credit for that hand.

---

## Resolution

All findings are fixed in `bedrock/`. `npm run build`, `npm test` (802 tests), `npm run lint`
and `npm run check:spec` pass.

| Finding | Fix | Regression test |
|---|---|---|
| **B1** broken table refunds rounds in play | One rule for every game in core: `leavePolicy(reason)` (`core/logic/sessions.ts`, re-exported by `core/tables.ts`) is passed to every `onLeave` as a third argument. GAME_DESIGN §4.1 applies: `broken` gets the same handling as `leave`/`disconnect` (`play_out`), and only `casino_off` refunds. Roulette keeps the slip on the wheel (an abandoned slip spins right away, with no refund mid-spin). Craps runs `autoComplete`. Poker auto-folds the seat and plays the hand out, and it aborts only on `casino_off`. Blackjack, slots and extras already played out on any reason. This also covers a dealer NPC being removed and an owner closing a table. | `core/logic/sessions.test.ts`: the policy table, plus a scan that fails if any game special-cases `'broken'` |
| **M1** poker pending cash-out overwritten or stranded | New `poker/logic/stacks.ts`. Stacks are keyed by player+table while a seat is live, and by player+table+hand once parked. Parking merges into an existing entry, and a new buy-in or top-up adds to the store and never replaces. Legacy `{id: amount}` stores are still read. `payPending` pays parked entries even while the player is seated (only that seat's live stack stays) and runs before every new buy-in. `cashOut` credits whenever the player is online (`livePlayer`), whatever `seat.disconnected` says. `onOpen` clears `disconnected`/`leaving` when a player re-attaches. | `games/poker/logic/stacks.test.ts` |
| **M2** heart penalties and offline soul deaths while the casino is off | `WagerService` no longer enforces hearts while the mode is off (`activeNow()`). **Choice: pause, not refund.** A world dormancy counter (`core.dormant_ticks`, `advanceDormancy`) grows by the time the mode was off, and each penalty's expiry is rebased by it (`rebasePenalties`, field `d`). This works for offline players too, with no per-player sweep. A Soul Wager lost while offline is kept as a player flag and kills only once the player is online and the mode is on. The flag is re-parked if the mode turns off during the 2-second delay. | `core/wagers-dormant.test.ts` (fake server), `core-logic.test.ts` |
| m1 poker top-up lost on abort | New `TableModel.topUp()` adds to the hand's `stack` **and** `startStack`, so the chips survive `abortHand` and are not counted as hand net. | `poker/logic/table.test.ts` |
| m2 trade farming with emerald blocks | A trade now needs a trade session: the player interacted with a villager or wandering trader in the last 60 s, is still within 6 blocks of it, and has not interacted with a block since. It must also not be tainted by an item drop or pickup near the player in the last second. Emerald blocks count as 9 emeralds and never as "other" (`detectTrade`), so crafting or uncrafting yields 0. | `economy-math.test.ts` |
| m3 cashier sell not re-checked at submit | After the slider, sell bails when `inDefault` and re-clamps to `sellCount(n, max, withdrawable now, rate)`. | `economy-math.test.ts` |
| m4 Soul Wager death via `kill()` | Before `kill()`, totems are moved out of the main hand and offhand into free inventory slots, or dropped at the player's feet if none is free. They then drop or are kept like the rest of the inventory, so no totem can pop. The custom death message respects `showDeathMessages`. Edition limit: the engine's generic death line cannot be suppressed without changing a vanilla game rule, which §2.2 forbids. This needs an in-game check. | none: runtime only, so this needs manual verification |
| m5 loan principal lost to the balance cap | `LoanService.take` refuses the loan (`msg.burmaldaholic.loan.over_cap`) unless `fitsUnderCap(balance, principal, maxBalance)`. | `economy-math.test.ts` |
| m6 no starting balance when the mode is switched on | Core watches the mode every second (`modeFlip`). On an off-to-on flip it runs the idempotent `firstJoin` for every online player, whatever flipped the mode. | `core-logic.test.ts` |
| m7 text inconsistencies | Blackjack limits now use `chips()`. The wheel legend uses the new key `gui.burmaldaholic.extras.wheel.legend_of` with the segment count as `%4` (`wheelLegend().total`); it is a Bedrock manual lang line, so STRINGS.md and Java are untouched. The admin enum dropdown uses `enumOptionLabel()`, which falls back to the conventional key instead of `lit(option)`. Half hearts use `decimal()`, which gets its separator from the new key `unit.burmaldaholic.decimal_separator` (`.` in EN, `,` in RU). | `games.test.ts`, `config-schema.test.ts` (every catalog enum label exists), `core-logic.test.ts` |
| m8 `void` async flows without a rejection handler | New `detach(promise, onError)` (`core/logic/async.ts`) is used for roulette, craps, slots, poker, dice duel, scratch shop, loan shark, collectors, cashier, menu, admin and charter flows. | `core/logic/async.test.ts` |
| m9 negative `debit` / `raise` | `Economy.debit` refuses anything that is not a safe integer > 0 (0 stays a no-op). `WagerService.raise` requires `isChipAmount(amount)`. | `wagers-dormant.test.ts`, `economy-math.test.ts` |
| Extra: poker cash-out garnished in full while in default | A cash-out is split by `splitCashOut(amount, buyIn)`. The returned buy-in (buy-in plus top-ups, tracked per seat in the stack store) is credited as `poker.stake_return`, which `isGarnishable` now exempts. Only the winnings above it (`poker.cashout`) are garnished. | `stacks.test.ts`, `loan/logic/loan.test.ts` |

`docs/architecture/bedrock.md` is updated too: the `onLeave` signature and leave policy, `detach`,
and the edition notes on hearts and trades.

---

## Re-review (fixes 2713023, 3e82eb5)

**Verdict: APPROVE.** `npm run build`, `npm test` (48 files, 802 tests) and `npm run lint`
(tsc, eslint, arch, strings, packs) pass.

Verified end to end:
- **B1:** `Tables.end` passes `leavePolicy(reason)` to every `onLeave`, and only `casino_off`
  refunds. Roulette keeps the slip, and an abandoned slip still spins (`round.update`
  treats unseated slips as ready). Craps `autoComplete`s and settles. Poker marks the seat
  `leaving`, auto-folds it within 10 ticks (`drive`), and cashes it out at hand end.
  Blackjack, slots and extras never refunded. Owner close, casino broke and a removed dealer
  NPC now play out too. That is correct, because bankroll exposure was reserved when the bet
  was placed. No chips are minted or lost on these paths.
- **M1:** the live entry (player+table) and the parked entry (player+table+hand) never
  overwrite each other. `addBuyIn` and `park` add to what is there. `payPending` never pays
  a live entry while its seat exists, and `cashOut` either clears (online) or parks
  (offline) exactly once. Rejoin, rebuy at another table and top-up all trace clean. A
  legacy `{id: n}` entry is paid once.
- **M2:** the dormancy counter is only advanced while the mode is on, and penalties are
  rebased lazily. Offline hearts and soul deaths wait until the mode is on.
- **m1, m3, m5, m6, m7, m8, m9:** confirmed in code.

Remaining (minor, not blocking):
- **r1:** `core/wagers.ts:682` `applyPendingSoul` clears the flag before the 40-tick delay. A
  player who logs out within 2 s of joining escapes a Soul Wager lost while offline. The old
  code had the same gap. Fix: clear the flag inside `killBySoulWager`.
- **r2:** `games/poker/index.ts:363-374`. Casino mode turning off only reaches seats with a
  session. A disconnected or leaving seat mid-hand stays seated after `abortHand`, and no hand
  is scheduled for it. Its chips stay in the live entry and are not paid until the player
  stands up from the table, or the server restarts. No chips are lost. This was already the
  case before the fixes.
- **r3:** m2 trade detection is still heuristic, with `dailyCap` as the bound. That is
  acceptable.
