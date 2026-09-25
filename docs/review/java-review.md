# Java edition: correctness review

Scope: `java/src/main` and `java/src/client` (core, blackjack, poker, roulette, craps, slots,
extras, loan, vip, chaos, lastchance, multiplayer). This was a read-only pass, and every finding
below was traced end to end through the code. The following areas were excluded because another
developer is editing them: the `worldgen` package (NPCs, presets), the wheel legend, Mod Menu enum
labels, and roulette's casino-off handling. The worldgen mixin targets were still checked for
26.2/26.3 compatibility (see below).

**Verdict: not ready to ship.** B1 is a repeatable chip mint. Fix it and M1–M3 first. The minors
can follow.

| Severity | Count |
|---|---|
| Blocker | 1 |
| Major | 3 |
| Minor | 8 |

Checked with no issues found:
- **C2S payloads.** All six serverbound payloads go through `Payloads.serverbound`, which drops them
  while casino mode is off. Fabric runs play-payload handlers on the server thread.
  - `table_action` requires `containerMenu` to be the `CasinoTableMenu` at that `pos` and
    `stillValid` (block type + `tableLeaveDistance`). Action strings are capped at 64 characters.
    Exceptions are caught per action.
  - Every game re-validates the amount (`BetLimits`, `> 0`, VIP max, funds), the phase and the
    turn:
    - blackjack: `round.current()`, `legal()`, and the insurance cap;
    - poker: `toAct` + `seq`, and `coerce`;
    - roulette: `canBet`, `SlipLimits`;
    - craps: `placeError`/`oddsError`, the shooter check and the bet window;
    - slots: `range.clamp`.
  - Menu pages are re-checked with `visible()`. Charter actions check owner/op, and bankroll
    withdrawals are limited to `balance − reserved`.
  - The loan shark checks distance and liveness, and the loan cap (Bedrock m5) is fixed.
- **Double settle / refund+settle.** `settle`/`settleBet`/`refund` remove the open stake before
  paying, so a second call is a no-op. `pay()` and `refundStake()` release the reservation exactly
  once. Blackjack `paid`, slots `pending = null` and poker `cashOut` via `removeSeat` are all
  idempotent.
- **Bedrock B1 (breaking a table refunds rounds in play) is fixed.** `preRemoveSideEffects` →
  `leave(REMOVED)` → `playOutForRemoval`:
  - blackjack stands everyone;
  - roulette spins a drawn round and refunds only during betting;
  - craps runs `autoComplete`;
  - slots run `finish(true)`;
  - poker plays the hand out and does not abort.

  Only stakes that were never drawn are refunded.
- **Bedrock M1 (poker rejoin chip loss) is fixed.** `droppedSeats` re-attaches a rejoining player.
  `cashOut` pays offline-safe through the ledger, and the returned buy-in is a non-garnishable
  `TRANSFER`.
- **Bedrock m1 (top-up lost on abort) is fixed.** `canTopUp` refuses while the player is dealt into
  the hand.
- **Bedrock M2 (heart penalties while casino mode is off) is fixed.** `HeartPenalties.refresh`
  removes the modifiers and pauses expiry while the mode is off (`dormantTicks`). The Soul Wager is
  instant and online-only. The `soul_wager` damage type bypasses invulnerability, armour, effects
  and enchantments, and Last Chance skips it.
- **Bedrock m2 (trade farming).** The trade reward hooks `AbstractVillager.notifyTrade`, so only a
  real completed trade pays, and the daily cap still applies. Emerald-block crafting cannot farm it.
- **Bedrock m3 (sell chips not re-checked).** Cashier `sell`/`withdraw` re-check `inDefault` and
  `withdrawable` on the server for every action.
- **Casino mode off:** every mixin is a no-op while the mode is off:
  - `Earnings.onTrade` and the VIP stat, smelt and cosmetic hooks;
  - `isExplosionProof`;
  - the debris ledger (it only records data).

  Loan timers shift by the time the mode was off, and collectors despawn.
- **Information leaks:**
  - blackjack sends only the dealer's up card until `holeRevealed`;
  - poker sends hole cards only to their owner or on showdown, and the board holds only dealt cards;
  - no shoe, deck or RNG state is ever sent.
- **Mixin targets on 26.2 and 26.3** were checked with `javap` against both jars:
  - `AbstractVillager.notifyTrade`
  - `BlockItem.place`
  - `Mob.finalizeSpawn`
  - `ServerExplosion.calculateExplodedPositions` + `level`
  - `FurnaceResultSlot.checkTakeAchievements` + `player`/`removeCount`
  - `Player.getDisplayName`
  - `ServerPlayer.awardStat`
  - `CreateWorldScreen$GameTab.<init>` → `GridLayout.createRowHelper`
  - worldgen: `JigsawPlacement$Placer.tryPlacingChildren`, the `MinecraftServer.structureTemplateManager`,
    `SinglePoolElement.template`/`place`, `TemplateStructurePiece.templateName` accessors, and
    `Structure.generate`. 26.3 added a `Climate$Sampler` parameter to `Structure.generate`; the
    mixin captures its arguments by type with `@Local(argsOnly)`, so it still matches.

  No runtime reflection is used.
- **Localization.** A static scan of every `Component.translatable("…")` found no missing keys in
  either `en_us` or `ru_ru`, and no placeholder/argument count mismatches.

---

## B1 (Blocker): poker re-buy while still dealt into the running hand pays the old hand stack again, a repeatable mint

- **Where:**
  - `games/poker/PokerTableBlockEntity.java:228` (`buyIn` only checks `table.seatOf(id)`).
  - `:335-339` (`standUp` of a folded player cashes out immediately).
  - `:668` (`cashOut` pays `table.liveStack(id)`).
  - `games/poker/logic/PokerTable.java:152-170`: `handIndexOf`/`liveStack` look the player up by
    **id in the hand**, not by seat.
  - `:173-180` (`refundableStack`), `:361-367` (`settleHand`) and `:386-391` (`abortHand`) also
    match by id and seat index only.
- **Scenario:** at a table with a second human, so the hand keeps running after A leaves:
  1. A buys in for 100 bb and folds preflop. The hand stack is X ≈ 100 bb.
  2. A presses Stand up. `liveInHand` is false (folded), so `cashOut` pays X and removes the seat.
  3. While the hand is still running, A buys in again for the minimum Y = 40 bb. `seatOf(A)` is
     null, so the buy-in is accepted and a new seat is added.
  4. A presses Stand up again. `handIndexOf(A)` still finds A's folded entry in the running hand,
     so `liveStack` returns **X**, not Y. A pays Y and receives X.
  5. Repeat for as long as the other players keep acting (each decision has an action timer).
     Every cycle mints X − Y chips from the bank.
- **Variant:** if A stays and re-seats at the same index, `settleHand` writes X over Y
  (`s.id.equals(p.id)`), and `abortHand`/`refundableStack` restore the old start stack. Chips are
  created or destroyed either way.
- **Fix:**
  - Refuse `buy_in` while `table.inHand() && table.handIndexOf(id) >= 0` (error
    `round_in_progress`).
  - Also make `liveStack`, `refundableStack`, `settleHand` and `abortHand` match the dealt **Seat
    object**: keep the dealt `Seat` references beside `handSeats`, and compare by identity.
  - Add a GameTest: fold, stand up, buy in, stand up, and assert the balance never exceeds the
    starting total.

## M1 (Major): rounds whose outcome is already drawn are refunded on reload (server stop or chunk unload), a free-roll

- **Where:**
  - `core/table/CasinoTableBlockEntity.java:745-750` (`loadAdditional` sets `refundPending` for any
    open stake) and `:599-609` (the refund).
  - `games/poker/PokerTableBlockEntity.java:857-883` (saves `refundableStack` = the start-of-hand
    stack) and `:736-756` (repays it).
  - Round state is never persisted.
- **Scenario:** the outcome is drawn, and usually visible to the player, before settlement:
  - Blackjack: own cards and the dealer's up card are known at the decision.
  - Roulette: the result is drawn at `NO_MORE_BETS` and sent as `result` during SPIN
    (`RouletteTableBlockEntity.java:389`).
  - Slots: the grid is drawn at `startSpin` and sent in `spin.grid` before the `spinTicks` timer
    settles it (`SlotMachineBlockEntity.java:547`).
  - Poker: facing a bet with a weak hand.
  - Craps: Pass after a point of 4 or 10 is set.

  In singleplayer, Esc pauses the integrated server, so the timer never fires. "Save and Quit"
  then refunds the losing round in full. On a server, the same happens when the table's chunk
  unloads mid-round, for example when the only nearby player steps through a portal while
  seated. Seated players with a stake are never removed for distance.
- **Rule:** GAME_DESIGN §4.1 says a server stop refunds. Review B1's principle says refunds are only
  fair before any random draw. The Java edition already has the code to play rounds out
  (`playOutForRemoval`).
- **Fix:** run the play-out on BE unload and server stop too. Call it from `setRemoved()` when
  `!removing` and the level is still loaded, or from
  `ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD` / `SERVER_STOPPING`. Refund only stakes that were
  never drawn. Poker should play the hand out instead of saving start stacks. Update §4.1
  accordingly.

## M2 (Major): an open stake keeps its old bankroll when the table's ownership changes, so money moves between the wrong accounts and reservations leak

- **Where:**
  - `core/table/CasinoTableBlockEntity.java:342-363`. When raising an existing stake key, the new
    amount is transferred to, and reserved on, the **current** bank (`ownership()`). The merged
    `OpenStake` keeps `prev.bankroll()`, and `pay`/`refundStake` use that bankroll.
  - `multiplayer/Ownership.java:323-353` (`linkInRange`, the charter "link" action) links tables
    that have open stakes. Charter removal makes tables inactive, with the same effect.
- **Scenario:**
  - A player bets at an unowned roulette table (house).
  - The colluding owner presses "link".
  - The player adds a large bet. The stake and reservation go to bankroll B, but the record says
    house.
  - If the bet wins, the house pays; if it loses, B keeps it. That is a free-roll for colluders.
  - B's reservation is never released (it is released against `""`), so
    `processClosing` (`Ownership.java:272`) waits for `reserved == 0` forever, and the casino can
    never be closed.
  - The reverse, unlinking, makes B pay for chips that went to the house.

  Blackjack doubles, splits and insurance raise the same key.
- **Fix:**
  - In `placeBet`, when `prev != null`, use `prev.bankroll()` for the transfer and the reservation,
    or refuse the raise when the bank changed.
  - Skip linking or unlinking tables that have open stakes (`!table.openStakes().isEmpty()`),
    deferring them to their next idle moment.

## M3 (Major): cashier withdraw spawns an unbounded number of item entities (server DoS, chips lost on despawn)

- **Where:** `core/cashier/CashierBlockEntity.java:174-203` and `give` at `:247-257`.
  `ChipMath.split(amount, denom)` accepts a client-supplied `denom`.
- **Scenario:**
  - A vanilla client can type 1 000 000 000 (the default `maxBalance`). That gives 2 000 000 chips
    of 500, or 31 250 stacks.
  - A crafted packet with `denom=1` and a 1 M balance gives 15 625 stacks.
  - Everything that does not fit is dropped as separate `ItemEntity`s in one tick, which stalls or
    crashes the server. The drops despawn after 5 minutes, destroying the chips.
- **Fix:** clamp the withdrawal to what fits in the free inventory space (or to a fixed cap, such
  as 36 stacks per click). Reject a `denom` that is not in `DENOMINATIONS`.

---

## Minor

### m1: scratch card outcome leaks to the client through item `custom_data`
`games/extras/server/ScratchGame.java:201-213,222-224` stores the whole face (`cells`), `prize`,
`creeper` and `top` in the stack's `CUSTOM_DATA`, and item components are synced to the client.
A client mod or `/data get` reads the unrevealed cells, which contradicts the class doc ("only
revealed cells are ever sent"). This has no money impact, because the outcome is fixed at the first
scratch, but it spoils the reveal and exposes creeper cards.
**Fix:** keep only `id` + `mask` on the item, and the face in world data keyed by `id`.

### m2: cashier deposit/buy destroys items at the balance cap
`CashierBlockEntity.java:138-171` (deposit) and `:205-215` (buy) remove chip items or emeralds and
then `deposit()`. `Ledger.commit` drops everything above `maxBalance` (`lostToCap`), and only a
chat line is shown. **Fix:** clamp to `maxBalance − balance` first (the same pattern as the loan
m5 fix): deposit only the chips or emeralds that fit and leave the rest in the inventory.

### m3: XP pawn wipes partial level progress
`core/wager/Stakes.java:160-163` calls `setExperiencePoints(0)`. The progress is returned on WIN or
PUSH but lost on LOSS, and it is not part of V. **Fix:** leave the progress untouched and remove
only the whole levels.

### m4: pawn stakes at an owned Wheel settle against the house
`games/extras/block/WheelBlockEntity.java:102-128`: `Stakes.settle`/`deposit` always use
`AccountId.HOUSE`, so the owner's bankroll neither pays winnings nor receives the pawn value, and
the owned-casino statistics miss the round. **Fix:** refuse pawns at owned tables (a veto), or
route the chip part through the bankroll.

### m5: the Last Chance scar stays applied while casino mode is off
`lastchance/LastChance.java:390`: `tickSecond` calls `refreshScar` for every player regardless of
the mode. Heart-wager penalties pause while the mode is off (review M2), so the scar is
inconsistent with the §2.1 "dormant" rule. **Fix:** remove the modifier while the mode is off, as
`HeartPenalties.refresh` does, or document that the permanent Hardcore scar is exempt.

### m6: decimal multipliers are not localized
`games/extras/logic/Payouts.java:19-23` (`Locale.ROOT`, `.`) is used in the Plinko chat result
(`PlinkoBlockEntity.java:123`) and on the Wheel and Plinko screens. RU shows "0.5×" instead of
"0,5×" (compare Bedrock m7). **Fix:** use a translated decimal separator.

### m7: Coin Flip and Dice payloads do not require the item
`CoinFlipGame.java:71-92` and `DiceGame.java:134-147` accept `extras_action` from any player.
Owning a Lucky Coin or Dice is only checked client-side, when the screen is opened with the item.
The odds are fair, so money is safe. **Fix:** require the item in either hand (or an open-screen
token) on the server.

### m8: the returned stake is garnished in default
`core/economy/Economy.java:139-141`: `PAYOUT` is garnishable, and house-game settlements pay stake
plus winnings as one `PAYOUT`. A bet placed before default and settled after it has its **stake**
garnished too. Poker already splits `stake_return`. **Fix:** in `CasinoTableBlockEntity.pay`, pay
`min(payout, amount)` as a non-garnishable `TRANSFER` and only the winnings as `PAYOUT`.

---

## Notes (no action required)
- Poker's `SEATED` is a static map. It is guarded by `isRemoved()` and cleaned up in `setRemoved`,
  so stale entries from an earlier integrated server are harmless.
- Creative players can already mint through the creative inventory (chip items, forged scratch-card
  `custom_data`). That is accepted, because creative mode is trusted.
- A reservation whose table never reloads, or is removed without `preRemoveSideEffects` (for
  example by an external editor), blocks charter closing forever. Consider an admin release command.
- Roulette `result` is sent during SPIN, when no more bets are accepted. It matters only through M1.

---

## Resolution (2026-09-24)

Every finding is fixed. Each fix has a regression test: JUnit tests are under `java/src/test`, and GameTests
are in `gametest/core/JavaReviewGameTests` unless another class is named.

| # | Fix | Test |
|---|---|---|
| B1 | `buy_in` is refused with `round_in_progress` while the player still has an entry in the running hand (`PokerTable.dealtInto`, which matches by id and so also covers a folded player who stood up). `handIndexOf`/`liveStack`/`refundableStack`/`settleHand`/`abortHand` now match the dealt `Seat` object (`handSeatRefs`, identity), not the id. | `PokerTableTest.reseatedPlayerIsNotMatchedToTheOldHandEntry`; GameTest `PokerGameTests.foldStandRebuyCyclesConserveChips` (fold → stand → re-buy ×3, never above the starting total) |
| M1 | New `CasinoTableBlockEntity#playOutNow(why)` (the former removal path: everybody leaves `REMOVED`, `playOutForRemoval`, only undrawn stakes refunded; idempotent). `core.table.TableLifecycle` runs it on **chunk unload** (`FULL_CHUNK_STATUS_CHANGE` → `INACCESSIBLE`, which vanilla fires *before* the chunk is saved; the chunk is then marked unsaved) and on **`SERVER_STOPPING`** (before players are removed and the world is saved; online players get `msg.burmaldaholic.core.round_played_out`). Poker reports its seats via `hasRoundInPlay()`, so the hand is played out and paid and no start-of-hand refund is saved. Casino mode off: craps and poker play out instead of refunding (roulette is handled by its own owner). Only a crash can still leave stakes to refund on load. GAME_DESIGN §4.1 (already ⚠ CHANGED by the Bedrock fix) now notes Java's settle-at-stop variant and the casino-off rule. | `m1DrawnRoundIsSettledWhenTheTableStops` (20 vs 17 pays, no stake in the saved NBT, second call is a no-op); `PokerGameTests.stoppedTableSettlesTheHandInsteadOfSavingARefund` |
| M2 | `placeBet` routes every bet of a player's open round (a raise or a new bet key) to the bankroll that round started with (`roundBankroll`), for both the transfer and the reservation. `settle` never releases a reservation against another bankroll (legacy data). Linking/unlinking therefore needs no deferral. | `m2RaiseAfterOwnershipChangeStaysWithTheRoundsBank` (house → linked, and owned → unlinked; balances and `reserved == 0`) |
| M3 | `withdraw` rejects a `denom` that is not in `DENOMINATIONS`, and caps each call at `MAX_WITHDRAW_STACKS` = 36 item stacks (`ChipMath.capToStacks`). The player sees `msg.burmaldaholic.core.withdraw_capped`. Items go to the inventory first, and at most the rest of 36 stacks can drop. | `ChipMathTest.withdrawalCappedToStacks`; `m3WithdrawIsCappedAndDenominationValidated` |
| m1 | The scratch card item holds only `id`/`kind`/`mask`. The face (cells, prize, creeper, top, price) lives in world data `ScratchData` (`scratch_cards.dat`) and is removed when the card is finished. Old cards migrate on first use. | `m1ScratchFaceNeverOnTheItem` |
| m2 | Cashier deposit (all/held) and buy/buy_gold only take the chips or currency that fit under `maxBalance`, and leave the rest in the inventory (`gui.burmaldaholic.error.balance_full`). | `m2DepositAndBuyStopAtTheBalanceCap` |
| m3 | `Stakes.xp` removes whole levels only. The progress stays with the player (`xpProgress` is 0). GAME_DESIGN §4.3.2 ⚠ CHANGED. | `m3XpStakeKeepsPartialProgress` |
| m4 | Wager gate: pawn stakes are refused at owned tables (`gui.burmaldaholic.error.pawn_owned_table`). GAME_DESIGN §4.3 ⚠ CHANGED (both editions). | `m4PawnRefusedAtOwnedTables` |
| m5 | `LastChance.refreshScar` removes the modifier while casino mode is off. The stored scar is kept and is re-applied when the mode is on. | `m5ScarDormantWhileCasinoModeOff` |
| m6 | `Texts.decimal` uses the translated separator `unit.burmaldaholic.decimal_separator` (`.`/`,`, the same key as Bedrock m7). It is applied to Plinko chat and screen, the Wheel legend, the Golden Hour multiplier and the loan discount. | `m6DecimalSeparatorIsTranslated` |
| m7 | Coin Flip and Dice `extras_action` payloads are accepted only if the player holds the item in either hand, or if the server opened that screen for them (item use or the Casino Menu "vs house"). This is checked by `ExtrasGames.mayAct`. | `m7CoinFlipNeedsTheItemServerSide` |
| m8 | Table `pay` and `Stakes.settle` return the stake as a non-garnishable `TRANSFER` (`stake_return`), and pay only the winnings as `PAYOUT`. | `m8OnlyWinningsAreGarnishable` |

Found while testing: `PokerTableBlockEntity.standUp` threw an NPE when the last seated human stood up on their own
turn and that fold ended the hand, which closed the table. This is fixed with a null check and covered by
`foldStandRebuyCyclesConserveChips`.

New keys that are only in the Java lang files (`java/tools/lang_java_only.json`) are
`unit.burmaldaholic.decimal_separator`, `gui.burmaldaholic.error.pawn_owned_table`,
`gui.burmaldaholic.error.balance_full` and `msg.burmaldaholic.core.withdraw_capped`. Bedrock needs
`pawn_owned_table` as a manual lang line when it follows the §4.3 change. `msg.burmaldaholic.core.round_played_out`
comes from STRINGS.md.
