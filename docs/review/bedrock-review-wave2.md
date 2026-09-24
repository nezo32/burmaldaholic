# Bedrock review — wave 2 (Baccarat, UTH, PvP engine + modes + UI, Seats & Bots)

Branch `worktree-agent-a35f11d964d6bc392` @ `ff2190b` (read-only review; base `4c1efd1`, plus
Baccarat `a60e24e..c4efaad` and UTH `a60e24e..a0ada54`).

Checks run on the tip: `tsc --noEmit` clean, `eslint .` clean, `check-arch` OK, `check-strings` OK,
`lang.mjs` OK (en_US = ru_RU = 2681 keys), `gen-lang --check` OK (2583 spec keys),
`validate-packs` OK, `vitest run` 82 files / 1317 tests passed.

## Verdict

**Approve after fixing the two majors.** There are no blockers. The PvP engine's money path holds up:
one `transact` for each escrow, refund and settle; the bank is the escrow holder; the rake split sends
the bots' share to the bank; the DRAWN record is saved before `started` is emitted; a restart
refunds LOBBY records and settles DRAWN ones from the tape. Double or nothing is re-checked on every
link. Chemin de fer escrow, Banco, the bot bank and crash projection are careful. `decide(seq)` guards
stale bank offers. `onDealt` saves the bots' projected holdings, and `recover` skips bot ids. The UTH
player bank uses the core bankroll with reservations. Atmosphere bets never reach `wagers`.
Localization tooling is green and I found no literal UI strings in the diff.

Counts: **0 blockers · 2 majors · 7 minors**.

---

## Major

### M1 — Credits to a closed charter bankroll re-create it, so the chips are stranded
- **Where:** `multiplayer/ownership.ts:345-366` (`closeCasino` → `system.run(processClosing)`, which
  pays out once `reserved == 0`). The late credits come from `core/bots/service.ts:389-392` (`refund`:
  bank → bankroll), `core/pvp/service.ts:375-391` plus `core/logic/pvp/money.ts:40,93-96` (PvP bot
  refunds, BANKROLL-bot payouts, `rakeToBankroll`) and chemin de fer `payRake`. `economy.bankroll(id)`
  returns `{0,0}` for a missing id, and `saveBankroll` writes it back.
- **Scenario:** An owned casino has Bots = *Allowed*. Its poker table has 4 bots funded from the
  bankroll (their stacks are not *reserved*). The charter breaks mid-hand (block removed, or the
  owner dissolves it). `processClosing` runs the next tick, sees `reserved == 0`, pays the owner the
  bankroll and deletes it. The hand then plays out over several ticks. `endSession` → `refund` credits
  `bankroll:<id>` again, which creates a new property that nobody owns and nothing ever pays out, so
  the chips are destroyed. The same thing happens to a PvP lobby or DRAWN match at a machine of that
  casino: bot refunds, bot winnings and the rake share all go to the dead id.
- **Fix:** Tombstone closed bankrolls in the economy (`closed:<id> → ownerId`) and turn any later
  credit to a closed id into `creditById(owner)`, with a message. As an alternative, keep the bankroll
  open in `processClosing` while bots funded from it still hold chips (`TableBots.stacksOut()` across
  tables) or a live PvP match references it. Also cancel or settle PvP matches anchored to the
  casino's machines in `closeCasino`.

### M2 — Poker crash mid-hand: bots' stacks are not saved with the drawn play-out (bankroll mints or burns)
- **Where:** `games/poker/index.ts:830-841` (`saveDrawn` saves only `p.human` stacks) against `:994`
  (bot stacks are saved only at hand end). Orphan recovery (`core/bots/service.ts:165-170`) then
  returns the pre-hand stack.
- **Scenario:** An owned table has BANKROLL bots. The server stops mid-hand. On load, humans are paid
  their play-out stacks (which include chips won from bots, or leave out chips lost to them). Each
  bot's bankroll gets back its stack from **before** the hand. If a human won from a bot, the chips
  are duplicated into the bankroll; if the bot won, the human's chips vanish. With BANK purses the
  economy stays consistent, but heat for the hand is lost.
- **Fix:** In `saveDrawn`, also call `live.bots.setStack(b.id, end.players[k].stack)` for every bot
  in the hand (the same play-out), as chemin de fer `onDealt` already does.

---

## Minor

### m1 — Admin "send bots home" runs mid-round (poker)
`bots/ui.ts:505-515` → `TableBots.clear()` → `endSession` → `poker/logic/table.ts:139-147`
`unseatBot` in the middle of a hand. The bot's stack behind is refunded and its seat removed, but its
hand player stays. It then checks or folds with no seat (`scheduleBot` safe action). If it wins, its
pot share is never returned to its purse. Heat treats it as BANK (`isHouseBot` defaults to 'BANK').
Chemin de fer `onUnseat` after DEAL empties the bank, which voids a coup that was already drawn
(punters get refunds). **Fix:** make `clear()` set a flag that is applied at the next safe point, as
contract 6 says ("after the round settled").

### m2 — Coin chain decisions carry no match id (stale forms)
`core/logic/pvp/engine.ts:638-648` (`decide` matches on decision name and seat across all live
matches) and `games/extras/pvp/coin/ui.ts:218` (`stillAsked` compares only the decision name). A
Double or nothing / Let it ride form left open past its timeout, or one resolved late, answers the
**next** offer of the same kind. That can be a later chain or rematch with a larger D than the form
showed. **Fix:** pass `matchId` (and `link`) through `decide`, and compare them in `stillAsked`.

### m3 — Sulking is not re-checked when house bots fill later
`engine.ts:498-509`: `join` checks `withHouseBots` only if bots are already seated. `tickLobby`,
`fillBots` and `addBot` (`:846-857`, `:897-912`, and bot joins during the Wheel countdown) never check
the seated humans' heat. A player who is sulking can join a MIXED lobby before it fills, or start to
sulk mid-lobby, and still play house bots, which BOTS.md §5.4 forbids. The impact is low because PvP is
−EV. **Fix:** in `addBot`, refuse a house purse when any seated human is `sulking`.

### m4 — Bots-only PvP pots are broadcast server-wide
`engine.ts:1226-1232` emits `world` when `pot ≥ pvp.announceServerWidePot`, even when every
counterparty was a bot. BOTS.md §5.3 says there is no server-wide broadcast then ("no farm spam").
**Fix:** use `area` when the match has fewer than 2 humans.

### m5 — BANK-funded bots stay at a table after it becomes owned
`core/logic/bots/table-state.ts:680-700`: `forced` drops bots only when they bust or for heat.
`target` counts existing bots of every purse. If a charter links a table (keeper or worldgen) while
bots funded by the bank sit there and the owner sets Bots = *Allowed*, those bank bots keep playing at
an owned casino until they bust. BOTS.md §5.1 says the bank never funds bots at owned casinos.
**Fix:** at the safe point, drop any MONEY bot whose `purse.kind` differs from the current `purse()`.

### m6 — A failed settle leaves a live match stuck in DRAWN
`engine.ts:1099-1105`: `r.fi++` runs before `settle`. If `transact` returns false, no later tick
retries, so the match sits in DRAWN (escrow held) until a restart. This is unlikely, since credits
never fail. **Fix:** on failure, set `r.reveal = undefined` so `tickReveal` falls into its
`!r → settle` retry branch.

### m7 — A join yields a bot before the human's escrow succeeds
`engine.ts:512-518`: `removeBot` refunds and removes the last bot, and only then is the joining human
escrowed. If that escrow fails, the lobby is left one bot short. **Fix:** escrow first, then yield
the bot.

---

## Notes (not code bugs; for the doc owners)
- Heat is per player id and `bots.tableBuyInsPerDay` is per table key. Alts multiply the daily cap,
  and craftable keeper tables multiply the house buy-in budget. The daily cap still bounds each
  account. Consider a per-world daily cap on house buy-ins.
- `saveDrawn` plays out the rest of the hand with the live bot RNG (`live.bots.rng`) on every
  `drive`. This is fair (the deck is separate), but it changes the bots' real later draws. A forked
  RNG would keep bot behaviour independent of how often the hand is saved.
- Checked and OK: PvP rake split (`pvpRakeSplit`, bots' share to the bank); the debtor exception
  only against house bots; the owner veto at owned machines; `busy`/`activeOf` (no parallel escrows);
  Wheel top-ups closed at "No more bets"; the tape drawn and saved in `begin` before any reveal;
  chain `offerCheck` (tier max, balance, `maxDoubles`); chemin de fer offers guarded by `offerSeq`,
  bot punters kept out under a bot bank, human-first `makeRoom`; UTH bots watch under a human banker;
  virtual bets never touch `wagers`; craps bots never shoot; `runJob` queue (bounded, epoch cancel,
  deadline fallback); PvP record ≤ 50 rivals; pending and offline lists capped; the bots ledger
  pruned each day; custom commands run on the next tick; no `beforeEvents` mutations in the new code.
