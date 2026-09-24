# Burmaldaholic — PvP modes and Seats & Bots: shared architecture (both editions)

Owner: architect (this wave). Normative game rules: `docs/design/PVP.md` (PvP modes) and
`docs/design/BOTS.md` (seats, bots, economy). Where they differ BOTS.md wins; `docs/research/bots.md`
is background only. Edition basics: `java.md`, `bedrock.md`. This file says **how** the two features
are built, which files exist already (skeleton, compiling, behaviour unchanged) and who builds what.

---

## 0. Decisions in one page

| Topic | Decision |
|-------|----------|
| Where the engines live | **Core** in both editions: `core.pvp` / `core.bots` (Java), `src/core/pvp`, `src/core/bots` + pure `src/core/logic/{pvp,bots}` (Bedrock). Every game module can use them without importing another module. |
| New modules | `pvp` and `bots` (both editions, registered, empty stubs). They own the lang fragments (`gui.burmaldaholic.pvp.*`, `…bots.*`), the config sections' UI labels and all **UI / commands** of the two features (hub, lobby/result screens, table-settings screen, invites, admin pages). |
| Where PvP modes live | In the module that owns the solo game (reuses its pure logic, blocks and machine UI): `extras` → Coin Flip Duel, Wheel Party, Plinko Battle, Scratch Showdown; `slots` → Slot Showdown. Each mode is its own sub-package / folder, registered through one pre-wired helper, so the mode developers never edit the module entry file. |
| Mode contract | PVP.md §3.14 `PvpMode<P,T>` — pure `validate/draw/score/timeline/botDecide` + JSON codecs. Identical in both editions. |
| Seat model | Games keep seats abstract over **`SeatOccupant` = Human \| Bot** and route every bot decision through the game's pure **`BotPolicy<V,A>`** (`decide` + mandatory `legalize`). Per-table bot state is a composable **`TableBots`** object owned by the game's table (core table classes stay unchanged). |
| Randomness | Two streams, never mixed: the **game RNG** (Java `OddsService.fair()`, Bedrock `mathRng`) draws shuffles / dice / tapes; the **bot RNG** (Java L64X128MixRandom, Bedrock sfc32 — never seeded from `Math.random`) drives every bot choice. |
| Money | PvP escrow and settlement = **one atomic transaction each** (Java `Economy.batch`, Bedrock `economy.transact`), the bank is the escrow holder. Money bots are funded by a **purse** (bank, or the owner's bankroll at owned tables). Rake: bank or the anchor's bankroll, **bot share of the rake always to the bank**. |
| Conflicts resolved | PVP.md `pvp.bots.*` config (4 keys) and its §3.15 defaults are **superseded** by BOTS.md (`bots.enabled`, `bots.pvp.fillDelayTicks`, `bots.pvp.maxPerMatch`, lobbies default `HUMANS_ONLY`/`NORMAL`, bot rake share to the bank only, Style labels, level tables §4.8). Those 4 rows/labels were not merged. |

---

## 1. Layering

```
 pure logic (unit-tested, no engine types) ─────────────────────────────────────────────────────────
   core/…/pvp/logic   PvpMode, Outcome, Step, PvpRng, DecisionView, PvpMath, HeadToHead, WinStreaks,
                      Eligibility, MatchState (+ Bedrock MatchRecord / PvpPlayerRecord JSON types)
   core/…/bots/logic  SeatPolicy, BotDifficulty, BotSpeed, BotRole, BotsMode, Personality, Purse,
                      BotProfile, SeatOccupant, BotSettings, OwnerControls, TableAccess(Java),
                      SeatingMath, BotRoster, BotPolicy, BotWork, ThinkTime, BotEconomyMath, ChatterLimiter
   <game>/logic       the game's BotPolicy (poker, baccarat/chemmy, uth, blackjack, roulette, craps)
   extras|slots/pvp/<mode>/…   the PvpMode implementations
 core services (engine state, money, persistence, timers) ────────────────────────────────────────
   PvP engine  Java Pvp.service() (PvpEngine) / Bedrock ctx.pvp (PvpEngine)
   Bots        Java Bots + TableBots + BotJobs + BotLedger + BotPurses / Bedrock ctx.bots (Bots, TableBots, BotJobs)
 feature modules (UI, commands, advancements) ────────────────────────────────────────────────────
   pvp   hub, invites, lobby / result screens, presenter, /casino pvp, PvP advancements
   bots  table settings, private tables, invites, chatter delivery, avatars, /casino table|bots, admin
   extras, slots   machine-form entries + per-mode screens (Java) / forms & action-bar lines (Bedrock)
   poker, baccarat, uth, blackjack, roulette, craps   own their TableBots wiring + BotPolicy
```

Dependency rules (unchanged edition rules apply): modules talk to core only; pure logic imports only
pure logic (Bedrock `check-arch` enforces it: a mode's `logic/` imports `core/logic/pvp/*` only).

---

## 2. File map (skeleton already in the tree)

### 2.1 Java (`java/src/…/dev/nezo/burmaldaholic/`)

| Path | Contents | Owner task |
|------|----------|------------|
| `core/pvp/logic/` `PvpMode, AnchorKind, PvpRng, Outcome, PvpEvent, Step, DecisionView, MatchState, PvpMath, HeadToHead, WinStreaks, Eligibility` | contract + pure math (PvpMath done, tested) | J-P1 extends |
| `core/pvp/` `PvpService, Pvp, PvpEngine (skeleton), PvpMatch, Participant, PvpModes, PvpPresenter, PvpEvents, PvpMatchData, PvpRecordData` | engine API, registry, persistence types, events | J-P1 |
| `core/bots/logic/` `SeatPolicy, BotDifficulty, BotSpeed, BotRole, BotsMode, Personality, Purse, BotProfile, SeatOccupant, BotSettings, OwnerControls, TableAccess, SeatingMath, BotRoster, BotRng, BotPolicy, BotWork, ThinkTime, BotEconomyMath, ChatterLimiter` | seat model + pure rules (SeatingMath/BotEconomyMath tested) | J-B1 extends |
| `core/bots/` `Bots, BotTable, TableBots (skeleton), BotPurses, BotJobs, BotLedger, BotLedgerData, BotRounds` | services | J-B1 |
| `core/config/sections/PvpConfig, BotsConfig` + `CasinoConfig.pvp()/bots()` | every new CONFIG.md key (done) | — |
| `core/config/ConfigBinder.keyOf` | `@SerializedName` keys (`bots.private.*`) | done |
| `pvp/PvpModule`, `client: pvp/client/PvpClientModule` | module stubs | J-P2 |
| `bots/BotsModule`, `client: bots/client/BotsClientModule` | module stubs | J-B2 |
| `client/pvp/PvpScreens` (core client) | per-mode screen registry | J-P2 uses, mode tasks register |
| `games/extras/pvp/ExtrasPvpModes` (called from `ExtrasModule.register`) | registers the 4 extras modes | pre-wired |
| `games/extras/pvp/{coin,wheel,plinko,scratch}/…Mode` | stub modes (`enabled()` = false) | J-M1, J-M2, J-M4, J-M5 |
| `games/slots/pvp/SlotsPvp` (called from `SlotsModule.register`), `SlotShowdownMode` | stub mode | J-M3 |
| `src/main/lang/{pvp,bots}/`, `burmaldaholic.{pvp,bots}.mixins.json`, `config/namespaces.properties` (`pvp=`, `bots=`), `ModuleList`, `ClientModuleList`, `fabric.mod.json` | registration | done |
| `CoreModule.register` → `Pvp.register(); Bots.register();` | lifecycle hooks (no-op / job ticker only) | done |

### 2.2 Bedrock (`bedrock/src/…`)

| Path | Contents | Owner task |
|------|----------|------------|
| `core/logic/pvp/mode.ts, math.ts (+test), rivalry.ts, eligibility.ts, match.ts` | contract, math, record JSON types | B-P1 extends |
| `core/pvp/service.ts` (`PvpService`, `PvpEngine` skeleton), `presenter.ts` (`PvpPresenter`, `PvpModeUi`), `store.ts` (`pvpStore`) | engine | B-P1 |
| `core/logic/bots/types.ts, rng.ts (sfc32), personality.ts, roster.ts, seating.ts (+test), policy.ts, think.ts, economy.ts, chatter.ts` | seat model + pure rules | B-B1 extends |
| `core/bots/service.ts` (`BotsService`, `Bots`), `table-bots.ts` (`TableBots`, `BotTableHooks`, `TableBotsState`), `purses.ts`, `jobs.ts` (`runJob` limiter) | services | B-B1 |
| `core/module.ts` `ctx.pvp`, `ctx.bots`; `core/registry.ts` wiring; `core/index.ts` exports | done | — |
| `pvp/index.ts, api.ts`, `bots/index.ts, api.ts`, `lang/{pvp,bots}`, `modules.json`, `modules.ts`, `core/logic/ids.ts` | module stubs + registration | B-P2, B-B2 |
| `games/extras/pvp/index.ts` (`registerExtrasPvp`, called from extras `onWorldLoad`) | registers 4 modes | pre-wired |
| `games/extras/pvp/{coin,wheel,plinko,scratch}/logic/mode.ts` | stub modes (`enabled()` false) | B-M1, B-M2, B-M4, B-M5 |
| `games/slots/pvp/index.ts`, `games/slots/pvp/logic/mode.ts` | stub mode | B-M3 |
| `tools/lib/strings.mjs`, `tools/lib/config-md.mjs`, `tools/gen-*.mjs` | new sections; edition defaults; `bots.table.<game>` family; sections of not-yet-registered modules (baccarat, uth) are skipped with one warning instead of failing | done |

---

## 3. PvP core

### 3.1 Match lifecycle (PVP.md §3.3, §3.6)

```
challenge ──▶ INVITED ──accept──▶ [escrow] ─┐                       (not persisted)
openLobby ──▶ LOBBY (host escrowed; joins escrow at once; leave = refund; persisted)
              │ start rule / host Start / BOTS_ONLY immediately / MIXED fill (bots.pvp.fillDelayTicks)
              ▼                              │
           STARTING: re-check rules 1,3,7 for non-escrowed; seat bots; draw tape with the FAIR rng
              ▼
           DRAWN  ── persist {tape} BEFORE the first reveal packet/title (Bedrock: same tick, sync)
              │ timeline (mode steps + engine Final Reveal); Spin!/Drop!/Scratch! only speed it up
              ▼  (also on: casino off, server stop (Java), world load after crash)
           SETTLED ── one transaction; rivalry / streak / stats / advancements / announcements
              ▼  rematch window pvp.decisionTimeoutTicks, kept pvp.historyTicks
           CLOSED
```
- **Load:** LOBBY → refund each entry (offline-safe, `msg.burmaldaholic.pvp.lobby.refunded` on join);
  DRAWN → settle from the tape (`…pvp.result.offline`); SETTLED → keep until `historyTicks`, then drop.
- **Casino mode off:** invites withdrawn, lobbies refunded, DRAWN settled at once (`…result.casino_off`).
- **Coin Flip Duel chains:** every flip is its own match (`chainOf`, `link`); Double-or-nothing /
  let-it-ride are `decide()` calls between matches; a stop between links ends the chain.
- Timers run on the engine tick (Java `END_SERVER_TICK`, Bedrock `system.runInterval(…, 1)`), in world
  ticks stored in the record, so a reload never extends a wait.

### 3.2 Escrow, settlement, rake routing (PVP.md §3.4, BOTS.md §5.1)

Escrow (join / accept / top-up), Java:
```java
eco.batch(server).debit(AccountId.player(p), s)            // each human stake
   .debit(AccountId.bankroll(id), sBot)                    // BANKROLL-purse bot stakes (owned anchor, Bots=Allowed)
   .credit(AccountId.HOUSE, Σ human + Σ bankroll-bot)      // BANK-purse bots: nothing moves (the bank mints)
   .commit(Transaction.bet("pvp"));
```
Bedrock: the same legs in one `economy.transact([...], 'pvp.escrow')`; bot legs from
`fundLegs(purse, amount)` (core/bots/purses.ts). Settlement:
```
pot = Σ stakes (bots included) ; rake = PvpMath.rake(pot, pvp.rakeBasisPoints) ; W = pot − rake
payouts = PvpMath.split(W, outcome.winners, outcome.seatOrder, n)
rakeToBankroll = anchorBankroll ? rake − floor(rake × botStakes / pot) : 0      // BotEconomyMath.pvpRakeToBank
legs: HOUSE −(Σ human payouts + Σ bankroll-bot payouts + rakeToBankroll)
      human i +payout_i (offline: Java deposit(server, uuid, …) / Bedrock economy.creditById)
      BANKROLL-purse bot +payout ; bankroll +rakeToBankroll       (BANK-purse bot payouts stay in the bank)
```
- The engine fires `PlayResults.fire(player, PlayResult.of("pvp", stake, payout).pvp().withTable(…).withTags(mode))`
  (+ `BotRounds.tag` when bots took part) for every human — Bedrock `wagers.recordPvp` (see task C-1).
- Owned anchor: the rake destination is the bankroll recorded **at creation** (`PvpMatch.bankroll`); the
  owner can never participate (eligibility rule 9). `RAKE_COLLECTED` fires for the charter stats.
- House mechanics that never apply to PvP: streak re-draw, Golden Hour, cashback, jackpots, chaos
  triggers, big-win lucky buff; `pvp.affectsStreak` (default false) gates the streak update and
  `pvp.countsTowardVip` the VIP credit (× the bot weighting of BOTS.md §5.3).

### 3.3 Tape pre-draw and persistence (§4.1 play-out)

`PvpMode.draw(fairRng, n, params)` returns the whole tape (seat order included) **before** bots make
any decision and without knowing which seats are bots. The engine stores `encodeTape(tape)` in the
match record, marks it DRAWN and only then reveals. Clients (Java) receive only revealed `Step.data`.
`score()` is recomputed from the tape at settle (deterministic), so the outcome is never stored
before it is shown.

### 3.4 Rivalry, win streaks, grudge, taunts, announcements

- `HeadToHead` per ordered human pair, `WinStreaks.announceTier/brokenCallout`, grudge via
  `HeadToHead.grudge(pvp.grudgeLosses)` — pure (both editions); stored per player (§5.2).
- Bots are never rivals: no records, no streak change for bot-only matches (a match with ≥ 1 human
  opponent counts), no grudge, excluded from `pvp_full_house` / `pvp_rampage` / `pvp_revenge`.
- Taunts: 8 fixed lines, `pvp.taunts.*`; bots taunt through `ChatterLimiter` + their personality.
- Announcements: participants + `pvp.announceRadius`; server-wide at `announceServerWidePot` or a
  legendary streak (`PvpEvents.STREAK`); ALL-IN tag when an escrow leaves balance 0.

### 3.5 How a mode plugs in

```java
public final class WheelPartyMode implements PvpMode<WheelPartyMode.Params, WheelPartyMode.Tape> { … }
// games/extras/pvp/ExtrasPvpModes.register():  PvpModes.register(new WheelPartyMode());
// machine UI (the mode owner's files):  Pvp.service().openLobby(player, "wheel", mode.encodeParams(p), stake,
//        new PvpService.Anchor(AnchorKind.WHEEL_OF_FORTUNE, level, pos), seating, inviteOnly);
```
```ts
// games/extras/pvp/wheel/logic/mode.ts: export function createWheelPartyMode(cfg): PvpMode<Params, Tape>
// games/extras/pvp/index.ts: ctx.pvp.registerMode(createWheelPartyMode(ctx.config), wheelUi)
// wheel machine form: ctx.pvp.openLobby(player, 'wheel', params, stake, anchor, seating, inviteOnly)
```
A mode owns: the pure contract + tests (PVP.md §16 vectors), its machine-form entries (Start / Join /
Spin! / top-up), its per-mode presentation (Java screen registered with `client.pvp.PvpScreens`,
Bedrock `PvpModeUi.describe(step)` action-bar lines), its advancements (via `PvpEvents.MATCH_SETTLED`
+ `Outcome.events`), and `botDecide` for its decisions (coin, wheel). Everything else is the engine.

### 3.6 Per-edition UI hooks

| Hook | Java | Bedrock |
|------|------|---------|
| Presenter | `Pvp.setPresenter(...)` by the pvp module: `PvpMatchSyncPayload` → `client.pvp.PvpScreens` mode screen, fallback titles/ticker HUD segment | `ctx.pvp.setPresenter(...)`: titles (needs `hud.holdTitle`, task C-2), action bar `ctx.hud.actionbar(p, 'pvp.<id>', …, HudPriority.game)` every ≥ 2 t, chat log, result ActionForm |
| Hub | `CasinoMenu.register(PvpHubPage)` (page id `challenges`, takes over extras' `ChallengesPage`; Dice Duel becomes a hub button) | `ctx.menu.add({id:'challenges'…})` (same takeover) |
| Invites | clickable chat `[Accept]/[Decline]` → `/casino pvp accept <id>` | chat + action bar; the invite form appears when the player opens the Casino Menu |
| Machine entries | mode owners' screens/buttons | mode owners' forms (`pvp/<mode>/ui.ts`) |

---

## 4. Seats & Bots core

### 4.1 Seat model and decisions

- `SeatOccupant` = `Human(id, name)` | `Bot(profile, role, purse)`; key = UUID string / `bot:<id>`.
- `BotProfile(id, nameId, level ∈ EASY|NORMAL|HARD, personality ∈ ROCK|STATION|MANIAC|TAG|LAG)`.
- Every game implements **one pure `BotPolicy<View, Action>`** in its `logic` package: the view is
  built only from what a human in that seat sees; `act = legalize(decide(...))`; heavy work via
  `BotWork` (step-able), scheduled by `BotJobs` (Java server-tick budget, 2 000 units/tick) /
  `ctx.bots.submitWork` (Bedrock `system.runJob`, ≤ `bots.maxConcurrentJobs`, yield every 25 units).
  At the think deadline the partial result is used (poker: < 50 samples → NORMAL rule).
- Humans and bots share the game's action type, so the round engine never branches on who acts;
  the table driver only chooses the source: human input / timeout default vs `BotPolicy.act` after
  `TableBots.thinkTicks(...)`. Stale decisions are dropped by a per-table sequence token.
- An exception in bot code → log + the game's safe default action (fold / check / stand / pass).

### 4.2 Settings, policy, difficulty, who may change, private tables

- `BotSettings(policy, count, difficulty, keepFree, chatter, speed)`; `OwnerControls(botsMode,
  hostMayChange, maxBots, allowPrivate)`; access = private flag + saved guests + session invites.
- Effective bots at a safe point = `SeatingMath.wantedBots` → `SeatingMath.capped` (owner mode/max,
  world `bots.maxActive` budget, purse affordability, atmosphere caps, heat) — BOTS.md §2.2.
- Host = keeper if seated, else longest-seated human (`SeatingMath.host`); ops / owner / keeper edit
  defaults; host edits the session; changes are **pending until the next safe point**; access changes
  apply at once. BOTS_ONLY only while the host is the only human.
- Claimants (all seats taken, ≥ 1 bot): the game's `YieldRule` picks the bot that leaves at the safe
  point (poker: just posted BB; chemmy: punter first; atmosphere: highest seat; PvP: last joined).
- Table defaults come from `bots.table.<game>.*` (craftable) / `worldgen*` (generated tables,
  `TablePresetProvider` may override), restored when the session ends.

### 4.3 Identity, names, presentation

Name ids (32, three theme pools) → `gui.burmaldaholic.bots.name.<id>`, drawn without replacement per
table/match from the bot RNG, saved in the profile (legacy `diamond_dave` → `diamond_dora`). Display:
glyph U+E190 (never inside a string) + `gui.burmaldaholic.bots.display[_level]`; level badge
`…level.<l>.short`; Style names `…style.<l>` where decisions can't change EV (chemmy, coin, wheel);
difficulty hidden (`…bots.luck_only`) where bots have no decisions.

### 4.4 Stake source and anti-farming

| Rule | Where implemented |
|------|-------------------|
| Purse: BANK (unowned) / BANKROLL (owned, `bots.owned.funding`=OWNER_BANKROLL and table Bots=Allowed) | `BotPurses` / `purses.ts`; `TableBots.safePoint` checks `bankroll − reserved` |
| Per-table daily house buy-ins `bots.tableBuyInsPerDay` | `BotLedgerData.countBuyIn` (Java) / ledger (Bedrock) |
| Heat: `botNetToday` ≥ cap → HARD-only at poker; ≥ `sulkMultiplier` × cap → sulk | `BotLedger` / `ctx.bots.recordNet/sulking`; attribution `BotEconomyMath.pokerPot/pvp` |
| Weighted VIP / `wager` contract credit, no streak / cashback / Golden Hour / broadcast | games tag results with `BotRounds.VS_BOTS` / `ONLY_BOTS` (Java; Bedrock task C-1 adds the same to `SettledEvent`); listeners in vip / core streak / chaos read the tags (task C-3) |
| Poker rake excludes bot chips, only with ≥ 2 human contributors | `pokerRake` (Bedrock economy.ts) / poker migration |
| Stake gates (no EASY above `bots.poker.easyMaxStake`) | poker migration |
| Debtors vs house bots only (`bots.debtorsMayPlay`) | `Eligibility.Facts.onlyHouseBots` (PvP), chemmy/uth eligibility |
| Atmosphere bots never touch money | games: virtual bets are presentation only |

### 4.5 Persistence across restarts

- Java: `TableBots.save/load(CompoundTag)` inside the table BE's save/load; Bedrock: world property
  `burmaldaholic:bots:<tableKey>` (JSON `TableBotsState`, < 2 000 chars).
- On load an orphaned bot stack / bank escrow (a crash) is **returned to its purse** and the bot is
  not restored; a clean stop plays the round out first and every bot leaves (stacks back to purses).
- The bot RNG is not persisted (a new session re-seeds).

### 4.6 How each game plugs in

| Game | Role | Policy (pure, game `logic/`) | Safe point | Notes |
|------|------|------------------------------|-----------|-------|
| Poker | MONEY | `PokerBotPolicy` wraps today's `Bots.decide` (EASY/NORMAL/HARD = Fish/Regular/Shark) + BOTS.md §4.3 table | between hands | migration §4.7 |
| Chemin de fer | MONEY (bank / punt) | `ChemmyBotPolicy` (take bank, bank size, keep/pass, punt, banco — Style) | after RESULT, before BANK_OFFER | bot banks only with ≥ 1 human punter; no bot-vs-bot; bot bank pays no rake |
| Baccarat PB | ATMOSPHERE | `BaccaratBettor` (personality betting styles) | start of / during BETTING | virtual bets, ≤ `bots.atmosphere.maxPerTable.baccarat` |
| UTH | ATMOSPHERE | `UthBotPolicy` (EASY hunch, NORMAL/HARD = strategy R) | start of BETTING | river enumeration as `BotWork` |
| UTH player-banked | — | stand-in dealer plate only | rotation point | bots *Watching* under a human banker; no bot banker when `houseRoundsWhenNoBanker` = false |
| Blackjack | ATMOSPHERE | `BlackjackBotPolicy` (mimic dealer / basic ±5 % / perfect basic) | start of BETTING | bots take real cards, never use the human timer |
| Roulette, craps | ATMOSPHERE | `RouletteBettor`, `CrapsBettor` (styles) | start of BETTING | craps bots never shoot (no config switch) |
| PvP | MONEY | `PvpMode.botDecide` (coin, wheel) | before START | seats filled by the engine; think 10–30 t for presses |

The Baccarat/UTH developers were told to keep seats abstract and route decisions through a decision
interface: their interface is adapted to (or replaced by) `BotPolicy<View, Action>` in task G-2/G-3;
their seat type is mapped to `SeatOccupant`.

### 4.7 Poker migration (and research flaws)

1. Replace `Bots.Tier` with `BotDifficulty` (legacy ids `fish/regular/shark` still load:
   `BotDifficulty.fromPokerTier`); names → `BotRoster` ids; seats → `SeatOccupant`.
2. `PokerTable.botTarget/fillBots/makeRoom` → `TableBots.safePoint` + `YieldRule.POKER_BIG_BLIND`.
3. **Separate the bot RNG**: today `PokerTableBlockEntity.rng()` feeds both the shuffle and
   `Bots.decide` (and Bedrock uses `mathRng` for both) → the bot stream must be `TableBots.rng()`.
4. Fix Fish folding premium hands to a shove (research §1.6.1: `C ≥ 12 or TT+` always call/shove),
   Fish floats 35 %, range-aware equity for NORMAL/HARD, HARD opponent model (BOTS.md §4.3).
5. Owned tables: bots funded by the bankroll (today: bank) or not at all; rake excludes bot chips.
6. Poker results vs bots: tag `BotRounds.VS_BOTS`, no streak, weighted VIP (today: full + streak).
7. Replace "Diamond Dave"; update `poker.bot.*Samples` / `poker.botMix.*` defaults in CONFIG.md +
   `PokerConfig` + Bedrock catalog (the rows were intentionally not touched by the pre-merge).
8. Add the seeded exploit-regression suite (BOTS.md §12.2) in both editions.

Java (J-G1, done): `PokerBotPolicy` (+ `Ranges`, range-aware `Equity.Work`, `PokerMoney`) mirror Bedrock's
`bots.ts` / `ranges.ts` / `equity.ts` / `money.ts`; `PokerTableBlockEntity` implements `BotTable` (stake gate
via `BotTable.botLevelAllowed` → `TableBots.levelAllowed`, a gated fixed level applies as NORMAL), escrows a
claimant's buy-in until the safe point (`TableBots.withdrawClaim` when it lapses), tags results with
`BotRounds.tag` + `withShare`, records heat, keeps the drawn outcome current for humans (saved refunds) and
bots (`TableBots.setStack`), and reaches the bots UI through `PokerBotsUi` (no-op until J-B2 installs it).
`Pvp.addBusyCheck(PokerTableBlockEntity::isSeatedAnywhere)` is left for the PvP integration (TODO in `PokerModule`).

---

## 5. Data formats

### 5.1 Match record (`MatchRecord`, JSON; Java `PvpMatchData` value, Bedrock `burmaldaholic:pvp:<id>`)
```json
{"v":1,"id":"k3j9x0aa","mode":"slots","state":"DRAWN","params":{…mode…},
 "anchor":{"kind":"slot_machine","dim":"minecraft:overworld","x":10,"y":64,"z":-3},
 "bankroll":"","seating":{"policy":"MIXED","count":3,"difficulty":"NORMAL","keepFree":true,"chatter":true,"speed":"NORMAL"},
 "inviteOnly":false,"host":"<uuid>","createdTick":123456,"chainOf":"","link":0,
 "participants":[{"index":0,"occupant":{"kind":"human","id":"<uuid>","name":"Alex"},"stake":100,"allIn":false},
                 {"index":1,"occupant":{"kind":"bot","profile":{"id":"b0k2…","nameId":"creeper42","level":"NORMAL","personality":"TAG"},"role":"MONEY","purse":{"kind":"BANK"}},"stake":100,"allIn":false}],
 "tape":{…mode…},"drawnTick":123500,"payouts":[194,0],"rake":6,"grudge":false}
```
Bedrock index `burmaldaholic:pvp_index` = JSON id list. Largest record (6 × Slot Showdown, 10 spins)
< 2 000 chars. Java stores the same JSON string per id (NBT `matches.<id>`, `format` = 1).

### 5.2 Player PvP record (`PvpPlayerRecord`; Bedrock player property `burmaldaholic:pvp_record`, Java `PvpRecordData`)
`{"v":1,"wins":12,"losses":9,"net":340,"streak":2,"acceptInvites":true,"rivals":[{"id":"<uuid>","name":"Alex","r":{"wins":1,"losses":5,"net":-420,"run":-3}}],"botRounds":40,"botNet":-120}` — rivals ≤ 50, most recent first.

### 5.3 Table bots state (`TableBotsState`; Java NBT with the same fields in the table BE, Bedrock `burmaldaholic:bots:<tableKey>`)
`{"v":1,"keeper":"<uuid>","defaults":{policy,count,difficulty,keepFree,chatter,speed,"private":false,"guests":[]},"limits":{botsMode,hostMayChange,maxBots,allowPrivate},"session":{"host":"<uuid>","pending":{…}?,"invites":[],"claimants":[],"bots":[{"profile":{…},"purse":{"kind":"BANKROLL","id":"…"},"stack":180,"bankEscrow":0}]}}`

### 5.4 Bot ledger
Java `data/burmaldaholic/bots.dat`: `net.<uuid> = {day, value}`, `buyIns.<tableKey> = {day, value}`.
Bedrock: sharded world JSON `burmaldaholic:bots_ledger` = `{ "<playerId>": [day, net], "t:<tableKey>": [day, buyIns] }`.

---

## 6. Public API reference

### 6.1 Java
- `core.pvp.Pvp.service(): PvpService` — `challenge/accept/decline/withdraw`, `openLobby/join/topUp/leave/start/fillWithBots`,
  `press/decide/taunt/rematch`, `matchOf/get/lobbiesNear/invitesFor/record/rivals/stats`, `all/cancel`
  (signatures in `PvpService.java`; nested `Anchor`, `Opponent.{PlayerTarget,BotTarget}`, `Stats`, `Rival`).
- `core.pvp.PvpModes.register(PvpMode)`, `Pvp.setPresenter(PvpPresenter)`.
- Events `core.pvp.PvpEvents.MATCH_STARTED / MATCH_SETTLED / STREAK`.
- `core.bots.TableBots` (+ `BotTable` hooks), `Bots.newRng/activeBudgetLeft/thinkTicks/enabled`, `BotJobs.submit`,
  `BotLedger.record/netToday/threshold/sulking`, `BotPurses.account/fund/settle`, `BotRounds.tag/vsBots/onlyBots`.
- Config: `CasinoConfig.pvp()` (`PvpConfig`), `CasinoConfig.bots()` (`BotsConfig`, `table.get("poker")`, `privateTables` = `bots.private.*`).
- Client: `client.pvp.PvpScreens.register(modeId, factory)`.

### 6.2 Bedrock
- `ctx.pvp: PvpService` (`core/pvp/service.ts`): `registerMode(mode, ui?)`, `mode/modes`, `setPresenter`, and the same
  verbs as Java returning `PvpResult<T>` (`{ok, value} | {ok:false, error: Raw}`).
- `ctx.bots: BotsService` (`core/bots/service.ts`): `enabled`, `activeBudgetLeft`, `newRng(tableKey)`, `think(...)`,
  `submitWork(...)`, `table(key, hooks, defaults, limits?) → TableBots`, `netToday/recordNet/sulking`.
- Pure imports for game/mode logic: `core/logic/pvp/{mode,math,rivalry,eligibility,match}`,
  `core/logic/bots/{types,rng,personality,roster,seating,policy,think,economy,chatter}`.
- Module services: `PVP_UI_SERVICE` (`pvp/api.ts`: `openHub`), `BOTS_UI_SERVICE` (`bots/api.ts`: `openSettings`, `openPrivate`).

### 6.3 Config (CONFIG.md `## pvp`, `## bots`, pre-merged)
All PVP.md §13 keys except `pvp.bots.*`; all BOTS.md §9 keys except the existing `poker.*` rows.
Edition defaults are written `X (Java) / Y (Bedrock)` (both generators/tests understand it).
`bots.table.<game>.*` is a family (Java `@Family Map<String, TableDefaults>`, Bedrock `familyMembers`).

### 6.4 Strings (STRINGS.md, pre-merged)
`## pvp` (after extras) and `## bots` (after multiplayer) sections; units into core Units; advancements
into `## advancements`; config labels as `### pvp` / `### bots` in `## config`; subtitles in `## sounds`.
Not merged: the 4 `config…pvp.bots.*` labels (keys superseded) and the **replacement** of
`advancement.burmaldaholic.shark_hunter.description` (existing row; doc owner / task J-G1). Added by
the architect: the 4 option labels of `bots.poker.easyMaxStake` (the admin-form test requires them).

---

## 7. Work breakdown

IDs: J = Java, B = Bedrock, C = core glue (either edition, small), G = game integration, M = PvP mode.
"core first" items unblock the others; everything else can start **now** against the skeleton (the
contracts are fixed; stubs compile; pure logic is testable without the engines).

### 7.1 Core (start now; each edition one developer, or one per engine)

| Task | Scope | Files (owned) | Depends |
|------|-------|---------------|---------|
| J-P1 / B-P1 **PvP engine** | lifecycle, eligibility, escrow/settle/rake, tape persistence + load/stop/casino-off play-out, timers, rematch, taunts, rivalry/streak/grudge, announcements, bot seat filling + pacing + `botDecide` driving, `PvpEvents` | `core/pvp/**` (Java), `core/pvp/**`, `core/logic/pvp/**` (Bedrock) | skeleton only |
| J-B1 / B-B1 **Bots core** | `TableBots` (admit, pending, safe point, claimants, host, private access, persistence, orphan return), purses, per-table buy-ins, heat ledger, world limits, job scheduler hardening, chatter queue | `core/bots/**` (Java), `core/bots/**`, `core/logic/bots/**` (Bedrock) | skeleton only |
| C-1 | Bedrock: `GameId` + label for `pvp`, `SettledEvent.botShare/vsBots`, `recordPvp` accepting bot tags | `core/wagers.ts`, `core/logic/house-edge.ts` | — |
| C-2 | Bedrock `ctx.hud.holdTitle(p, ticks)` (PVP.md §14) | `core/hud.ts` | — |
| C-3 | Listeners honour bot/PvP tags: streak skip (`pvp.affectsStreak`, bot rounds), VIP weighted credit, no cashback / Golden Hour / broadcast for only-bot rounds | Java `core/rng/StreakTracker`, `vip/…`, `chaos/…`; Bedrock `core/streak.ts`, `vip`, `chaos` | J-B1/B-B1 API (tags already defined) |

### 7.2 UI modules (start now against the service interfaces; wire to the real engines when P1/B1 land)

| Task | Scope | Files | Depends |
|------|-------|-------|---------|
| J-P2 / B-P2 **PvP UI** | hub page (takes over extras' Challenges page, Dice Duel button kept), invites, lobby view, result window, presenter, match ticker, `/casino pvp` / `scriptevent`, admin PvP list, PvP advancements (first_win, all_in, full_house, revenge, rampage) | Java `pvp/**`, `client pvp/**`, `client/pvp/PvpScreens`; Bedrock `src/pvp/**` | P1 for live testing; extras' `ChallengesPage` hand-over (coordinate with extras owner) |
| J-B2 / B-B2 **Bots UI** | table settings screen/forms, private table + invites (Casino Card on a player), `/casino table …`, `/casino bots …`, charter controls (with multiplayer owner), Wallet heat line, avatars (Java nameplates; Bedrock entity NICE), chatter delivery + per-player mute, bot advancements (members_only, no_robots, word_got_around) | Java `bots/**`, `client bots/**`; Bedrock `src/bots/**`, `packs/bots/**` | B1 for live testing |

### 7.3 PvP modes (start now; pure part first, machine UI after P1)

| Task | Mode | Files (each edition) |
|------|------|---------------------|
| J-M1 / B-M1 (dev B) | Coin Flip Duel + Lucky Coin on a player + DoN chain decisions + `botDecide` | `games/extras/pvp/coin/**` (+ Java client screen under `games/extras/client/pvp/coin/`) |
| J-M2 / B-M2 (dev B) | Wheel Party + wheel machine entries + top-ups + `botDecide` | `games/extras/pvp/wheel/**` |
| J-M3 / B-M3 (dev C) | Slot Showdown (reuses `slots` line evaluator) | `games/slots/pvp/**` |
| J-M4 / B-M4 (dev D) | Plinko Battle (reuses the solo path generator) | `games/extras/pvp/plinko/**` |
| J-M5 / B-M5 (dev D) | Scratch Showdown (new card logic, duel + lobby) | `games/extras/pvp/scratch/**` |

Each mode task: pure mode + PVP.md §16 vectors first (no engine needed), then machine entries,
presentation, mode advancements (`pvp_all_square`, `pvp_underdog`, `pvp_phoenix`, `pvp_lucky_feet`),
then flip `enabled()` to the config switch. Machine entries touch the owner module's machine files
(extras wheel/plinko UI, slots machine form): one small hook per mode, coordinated with that module's owner.

### 7.4 Game integration (after J-B1 / B-B1 `TableBots` is usable; independent of each other)

| Task | Scope | Files | Depends |
|------|-------|-------|---------|
| J-G1 / B-G1 **Poker migration** | §4.7 items 1–8 | `games/poker/**` (+ CONFIG.md/PokerConfig poker rows, `shark_hunter` string) | B1 |
| J-G2 / B-G2 **Baccarat + chemmy bots** | `ChemmyBotPolicy` (money), `BaccaratBettor` (atmosphere), TableBots wiring, claimant/yield rules | `games/baccarat/**` | B1 + Baccarat module merged |
| J-G3 / B-G3 **UTH bots** | `UthBotPolicy` (R, river as BotWork), atmosphere seats, player-banked Watching + stand-in | `games/uth/**` | B1 + UTH module merged |
| J-G4 / B-G4 **Blackjack atmosphere** | `BlackjackBotPolicy`, virtual bets, real cards | `games/blackjack/**` | B1 |
| J-G5 / B-G5 **Roulette + craps atmosphere** | bettors by personality, craps never shoots | `games/roulette/**`, `games/craps/**` | B1 |
| J-G6 / B-G6 **Worldgen presets** | table defaults `worldgen*`, Parlor mix [20,70,10], themed name pools | `worldgen/**` | B1 |

Parallelism: with 4 developers per edition — dev A: P1 then P2; dev B: M1, M2; dev C: M3 then G1;
dev D: M4, M5; a fifth / the core owner: B1 then B2; G2–G6 go to the Baccarat/UTH/game owners once B1 lands.

---

## 8. Merge notes and open items

- **Generated files** (`java/src/main/lang/**`, `bedrock/lang/**`, `bedrock/src/core/logic/config-catalog.ts`)
  will conflict with the Baccarat/UTH branches: take either side and re-run `python3 java/tools/gen_lang.py`,
  `npm run gen:lang`, `npm run gen:config`. Java core's fragment currently carries the baccarat/uth keys
  (no module owns them yet); they move to their modules automatically once `baccarat=` / `uth=` are in
  `namespaces.properties`. Bedrock skips the two sections until the modules are in `modules.json`.
- Registration lists (`ModuleList`, `ClientModuleList`, `fabric.mod.json`, `namespaces.properties`,
  `modules.json`, `modules.ts`, `ids.ts`, `strings.mjs SECTION_OWNERS`): additive one-line conflicts;
  keep both sides (`strings.mjs` already lists `baccarat`/`uth`).
- Java `ConfigSpecCoverageTest` fails only for the `baccarat.*` / `uth.*` rows until those modules
  register their config sections (expected).
- Open design items: LOCALIZATION.md glossary merges (PVP.md §15.0, BOTS.md §11.0), GAME_DESIGN/UI.md
  merges and the amendments listed in PVP.md §0.2 and BOTS.md §0.3; `bots.atmosphere.maxPerTable.*`
  Java config labels (Bedrock uses the template label); U+E190 bot glyph and U+E1A0/E1A1 PvP glyphs in
  both font sheets; Bedrock `playerInteractWithEntity` for Casino Card / Lucky Coin on a player (verify in game).
