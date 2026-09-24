# Burmaldaholic — Player-vs-Player Modes [pvp]

> **Java-only (2026-09-24).** Bedrock support was dropped: Bedrock sections, lanes and tasks were removed. An inline
> note that still names Bedrock (the former TypeScript twin) is historical context and does not apply.

Status: **v1.0 draft, implementation-ready for MUST items**. Owner: game design (PvP).
Audience: Java (Fabric) team, testers, localization.

This file is **normative for the `pvp` module**, like `GAME_DESIGN.md` is for the other modules. It was
written while another designer edits `GAME_DESIGN.md`, `CONFIG.md`, `UI.md`, `STRINGS.md` and
`LOCALIZATION.md`, so **everything PvP lives here** and is merged later (§0.2 says where each part
goes). Until the merge, this file wins over the others for anything with a `pvp` key or id.

Conventions are the ones of `GAME_DESIGN.md` (chips are integers, world-time ticks, 20 t = 1 s,
server-side RNG, "tier max" = VIP max bet §12) and of `LOCALIZATION.md` (key grammar, `%1$s`
placeholders, number-class plurals `.p1/.p21/.p2/.p5`).

---

## 0. Summary

### 0.1 What PvP adds

Players bet **against each other** instead of the house. Their stakes form a **pot**; the pot minus
a small **house cut** (rake, default 3 %) goes to the winner(s). The house never takes the other side,
never shifts odds, and never pays anything it did not collect — so every PvP mode is **fair: each
player's expected value is exactly "minus their share of the rake"** (§3.5).

| Mode | Players | One-liner | Priority |
|------|---------|-----------|----------|
| **Coin Flip Duel** (§4) | 2 | Call it, flip it, and the loser may shout **"Double or nothing!"** to get back to all square | **MUST** |
| **Slot Showdown** (§5) | 2–6 | Everyone spins the same machine type N times; line wins are points; a **HOT symbol** each spin, **KABOOM** halves you, **Ender Pearl swaps** you with the leader, the last place gets an **Underdog Boost** | **MUST** |
| **Wheel Party** (§6) | 2–8 | Buy a slice of the wheel — bigger stake, bigger slice. One shared spin. The pot goes to whoever's slice the pointer lands on | **MUST** |
| **Plinko Battle** (§7) | 2–6 | Everyone drops at once at the same risk; bins are points; underdog boost on the last ball | **MUST** |
| **Scratch Showdown** (§8) | 2–6 | Everyone gets a card; nine cells scratched **together, one at a time**; creepers blow up your best cell, Rabbit's Feet double the card | **MUST** |
| PvP core (§3): challenges, lobbies, escrow, rake, tape persistence, rivalry, win streaks, grudge matches, taunts, rematch, reveal choreography, **bots & seating policy** (§3.15, shared rules in `BOTS.md`) | — | the shared frame all modes use | **MUST** |
| Spectator side bets (§9.1) | spectators | Pari-mutuel "tote" on any chance-only match | NICE |
| Tournaments (§9.2) | 4–32 | Knockout brackets or timed leaderboards, prize pool from entries, trophies | NICE |
| Floating scoreboard (§9.3) | — | In-world hologram above the anchor machine | NICE |
| Coin Series (§10.1) | 2 | Best of 3 / 5 | NICE |
| Jackpot Race (§10.2) | 2–6 | First to hit the target line takes the pot | NICE |
| Wheel Heist (§10.3) | 2–6 | Loot / Steal / Bankrupt / Double / Shield wheel, three rounds | NICE |
| Plinko Bumpers (§10.4) | 2–6 | One bumper per player to push an opponent's ball to the centre | NICE |
| Scratch Poker (§10.5) | 2–4 | Hidden cards, two betting rounds | NICE |
| Dice Duel on the PvP core (§10.6) | 2 | Migrate the existing PvP Dice Duel (§11.5) to this frame | NICE |

### 0.2 Merge plan (for the doc owner, after the Baccarat/UTH edit lands)

| This file | Goes to |
|-----------|---------|
| §1–§10 rules, math, state machines | `GAME_DESIGN.md` new **§22 PvP [pvp]** (module table gets a `pvp` row) |
| §11 advancements | `GAME_DESIGN.md` §19 table (append) |
| §12 sounds and particles | `GAME_DESIGN.md` §22 + `UI.md` §0.1 sounds line |
| §13 config | `CONFIG.md` new `## pvp` section (after `extras`) |
| §4.x/§5.x… "UI" subsections and §3.11 | `UI.md` new **§14 PvP** |
| §15 strings | `STRINGS.md` new `## pvp` section; units into §core Units; advancements into §advancements; config labels into §config; subtitles into §sounds |
| §15.0 glossary | `LOCALIZATION.md` §6 new table "6.7 PvP"; add `pvp` to the module list in §1.1 |
| §16 tests | `GAME_DESIGN.md` §22 "Test method" |

**Proposed amendments to existing text** (need the owner's sign-off; implement PvP as written here):

1. **§14 streak** — "Update on each settled house-banked **or PvP** wager" becomes "on each settled
   house-banked wager, and on PvP wagers only when `pvp.affectsStreak` = true (default **false**)".
   Reason: two accounts ping-ponging 10-chip PvP matches could pump a +10 streak for almost nothing
   and then enjoy the lucky re-draw on house games (§3.12). Dice Duel PvP follows the same key.
2. **§4.1** play-out list gains: "PvP modes: the whole match (the *tape*) is drawn at START and
   persisted before any reveal; STARTED matches are settled from the tape, LOBBY entries are
   refunded" (§3.6).
3. **§13.3 Golden Hour** "all except PvP poker and PvP dice" → "all except PvP (poker, dice and every
   `pvp` mode)".
4. **UI.md §2** Casino Menu "Challenges" tab content becomes the PvP hub (§3.11.2); the key
   `gui.burmaldaholic.menu.challenges` is unchanged.
5. **§18.2** owned casinos: "Owner **cannot** play at their own tables" also covers PvP matches
   anchored at their machines; PvP rake at owned machines goes to the bankroll (§3.4).

---

## 1. Design pillars

1. **Everybody watches the same moment.** Every mode ends in one shared reveal that the whole room
   sees (titles, sounds, particles). PvP is a *social event*, not a private form.
2. **Short.** A match lasts 5–40 s. Rematch is one button.
3. **Symmetric chaos.** Swingy mechanics (KABOOM, SWAP, Underdog Boost, creepers, Rabbit's Feet,
   near-misses) make comebacks common and stories memorable — and because each rule treats all
   players identically, none of them changes anyone's expected value (§3.5).
4. **No decisions inside a MUST match.** All MUST modes are pure chance after START: the full outcome
   is drawn at once, so leaving, disconnecting or a server stop can never change a result. The only
   decision is *between* matches (Double or nothing, Rematch). Decision-heavy modes are NICE.
5. **Rivalry gives meaning.** Head-to-head records, win-streak call-outs, grudge matches and taunts
   turn 50/50 coin flips into feuds.
6. **Drama on screen.** Custom screens carry the reveals, with titles, action bar, sounds and particles
   around them (§3.11).

---

## 2. Scope, priorities and team split

### 2.1 MUST for v0.1.0

- PvP core (§3) complete, including rivalry records, win-streak announcements, grudge banner,
  taunts, rematch, the "Final Reveal" choreography, the PvP hub in the Casino Menu, and the seating
  policy with bot opponents (`HUMANS_ONLY` / `BOTS_ONLY` / `MIXED`, difficulties per `BOTS.md`, §3.15).
- The five MUST modes (§4–§8) with their MUST advancements (§11) and vanilla sounds (§12).
- Everything is chips only: **no pawn stakes, no Soul Wager in PvP** (pawn stakes are house-only,
  §4.3 m4).

### 2.2 NICE (later)

Side bets, tournaments, floating scoreboard, custom PvP sound events, owner per-machine PvP
settings, and the NICE modes of §10. They are specified far enough that their keys and strings are
reserved and nothing in MUST has to change to add them.

### 2.3 Four developers

The core contract (§3.14) is written first — it is small (interfaces + pure functions) — so all four
can start at once against stubs.

| Dev | Owns | Depends on | Estimate |
|-----|------|------------|----------------------|
| **A — core** | §3: match registry, invite + lobby flows, escrow/settle/rake (one atomic transaction each), tape persistence and load-time settle, eligibility check, rivalry + win streak + grudge, rematch, taunts, Final Reveal kit, PvP hub, Java lobby screen | economy `batch`/`transact`, `ctx.tables`, HUD, BOTS.md registry | 5–6 h |
| **B — coin + wheel** | §4 Coin Flip Duel (Lucky Coin on player, Double or nothing chain), §6 Wheel Party (wheel block buttons, arcs, spin), their `botDecide` (§3.15.4) | A's contract | 3–4 h |
| **C — slots** | §5 Slot Showdown (reuses the solo `slots` line evaluator; adds scoring rules and the multi-player reel view) | A's contract, slots evaluator | 3–4 h |
| **D — plinko + scratch** | §7 Plinko Battle (reuses the solo path generator), §8 Scratch Showdown (new card logic) | A's contract | 3–4 h |

Advancements: each dev adds the triggers of their mode (criteria);
dev A adds `pvp_first_win`, `pvp_rampage`, `pvp_revenge`, `pvp_full_house`, `pvp_all_in`.

---

## 3. PvP core [pvp]

### 3.1 Terms

| Term | Meaning |
|------|---------|
| **Match** | One PvP game instance: mode, parameters, participants, stakes, tape, result. Id = 8-char base-36 string. |
| **Duel** | A 2-player match created by a **challenge** (invite) to one named player. |
| **Lobby** | A 2–N player match that others join; anchored at a machine block (slots, wheel, plinko) or at the host's position (scratch). |
| **Host** | The player who created the match (always a participant). |
| **Anchor** | The block (or, for anchor-less lobbies, the host's position at creation) that defines distance checks, the owned-casino link and where effects play. |
| **Stake / entry** | Chips a participant puts in. Equal for everyone except Wheel Party. |
| **Pot** | Σ stakes. |
| **Rake / house cut** | The house's share of the pot (§3.4). |
| **Tape** | Every random value of the match, drawn once at START (§3.6). |
| **Seat order** | A uniformly random permutation of the participants drawn into the tape; used for every tie-break that needs an order. |

### 3.2 Eligibility (checked at invite, accept, lobby create, join, top-up, rematch and Double or nothing)

A player may take part only if **all** hold (first failing rule gives the error shown):

| # | Rule | Error key |
|---|------|-----------|
| 1 | Casino mode on, `pvp.enabled`, the mode's `pvp.<mode>.enabled` | `gui.burmaldaholic.error.casino_off` / `gui.burmaldaholic.error.disabled` |
| 2 | Not in Spectator game mode | `gui.burmaldaholic.pvp.error.spectator` |
| 3 | No outstanding debt at all (owed = 0), not in default, no Asset Freeze (§5.6, §5.8 rule 2) | `gui.burmaldaholic.pvp.error.debt` (for the other player: `…error.target_unavailable`, which does not reveal why) |
| 4 | Not busy: not seated at any table, not in another PvP match or lobby, not in a solo round | `gui.burmaldaholic.error.busy` / `…pvp.error.busy_target` |
| 5 | Same dimension as the anchor and within `pvp.joinRadius` (16) blocks of it (duel: of the challenger) | `…pvp.error.other_dimension` / `…pvp.error.too_far` |
| 6 | Stake ≥ `pvp.minStake` (10) and ≤ the player's **tier max** (and ≤ the lobby cap for Wheel Party) | `…pvp.error.stake_min` / `gui.burmaldaholic.error.bet_too_high` / `…pvp.error.over_target_max` |
| 7 | Balance ≥ stake | `gui.burmaldaholic.error.insufficient_funds` / `…pvp.error.cant_afford_target` |
| 8 | Not the same player as another participant | `…pvp.error.self` |
| 9 | Machine rules of the anchor: machine VIP requirement (Netherite slots: Gold VIP), owned machine is linked and open (not "inactive" or owner-closed; an **insolvent** casino still allows PvP because the bankroll pays nothing), and the player is **not the owner** | `gui.burmaldaholic.error.vip_required` / `gui.burmaldaholic.error.table_closed` / `…pvp.error.owner` |
| 10 | Target accepts PvP challenges (per-player setting `Accept PvP challenges`, default on) | `…pvp.error.no_invites` |
| 11 | Target has not declined a challenge from this challenger in the last `pvp.declineCooldownTicks` (600) | `…pvp.error.cooldown_target` |
| 12 | Challenger has fewer than `pvp.maxPendingInvites` (1) outgoing invites | `…pvp.error.pending` |

Rules 3–7 are re-checked at the moment money moves (accept / join / rematch / Double or nothing).
Distance is **not** checked after START: walking away never forfeits a match (§3.6).

### 3.3 Matchmaking

#### 3.3.1 Duels (Coin Flip Duel; Scratch Showdown against one named player)

- **Start points:** use the **Lucky Coin on a player** (Java `UseEntityCallback`; Bedrock
  `world.beforeEvents.playerInteractWithEntity` with the coin in hand; fallback: coin used on air
  while `getEntitiesFromViewDirection` finds a player within 6 blocks) → Coin Flip Duel set-up;
  Casino Menu → Challenges → *New match…* (any duel mode, pick an opponent from players within
  `pvp.joinRadius`); Java command `/casino pvp challenge <player> <mode> <amount> [heads|tails]`.
- The challenger fills the set-up (stake, side) → eligibility for both → **invite** sent. Nothing is
  debited yet.
- Target is told by chat + action bar + sound (`pvp_challenge`, §12). Java chat line carries clickable
  `[Accept]` / `[Decline]` components (`ClickEvent.RunCommand("/casino pvp accept <id>")`). Bedrock:
  opening the Casino Menu while an invite is pending shows the invite form first (no surprise form
  is pushed onto a player who may be fighting); the chat line says so.
- Invite states: `PENDING` → `ACCEPTED` | `DECLINED` | `EXPIRED` (after `pvp.inviteTimeoutTicks`, 600 =
  30 s) | `WITHDRAWN` (challenger cancels, leaves the radius before the answer, disconnects, or casino
  mode turns off).
- **Accept** = one atomic transaction debiting both stakes (§3.4). If it fails (someone spent the
  chips), both are told and the invite dies.

#### 3.3.2 Lobbies (Slot Showdown, Plinko Battle, Wheel Party; Scratch Showdown with "anyone nearby")

- **Host:** uses the machine → the machine's normal form/screen has an extra entry *Start a …* →
  set-up (entry, spins/balls/risk, cap) → host's entry is escrowed immediately → lobby opens.
  Scratch Showdown lobbies are created from the hub and anchored at the host's position.
- Announcement to every player within `pvp.joinRadius` of the anchor (chat + sound).
- **Join:** use a machine of the same kind within `pvp.<mode>.linkRadius` (8) of the anchor — or the
  anchor itself — and pick *Join …*; or hub → *Open lobbies nearby*. The entry is escrowed at join.
  Several players may join through the same machine (the match is virtual).
- **Leave** before START: the entry is refunded at once.
- **Start** when any of: host presses *Start* with ≥ 2 participants; the lobby is full; the lobby timer
  `pvp.lobbyTimeoutTicks` (1800 = 90 s from creation) ends with ≥ 2 participants. With < 2 at the
  timer, or if the host leaves before anyone joined, the lobby is **cancelled** and refunded. If the
  host leaves a lobby with others in it, the earliest joiner becomes host.
- Anchor broken, casino mode off, owned machine closed/unlinked before START → cancelled, refunded.

### 3.4 Money: escrow, settlement, rake

**Escrow.** Stakes move from balances in one atomic call — Java
`eco.batch(server).debit(player(a), s)…credit(HOUSE, pot).commit(Transaction.bet("pvp"))`; Bedrock
`economy.transact([{account: a, delta: -s}, …, {account: 'bank', delta: pot}], reason)`. The bank is
used as the escrow holder (it is unbounded, so parking the pot there is equivalent to a separate
account); the match record stores what is owed back. Nothing else may touch it.

**Rake** (integer only, no floating point, same):
```
rake = floor((pot × pvp.rakeBasisPoints + 5000) / 10000)       // round half up; 300 bp = 3 %
W    = pot − rake                                              // what the players get back
```
Owned casino: if the anchor is linked to a charter at START (recorded in the match), the rake is
credited to that **bankroll** in the settlement transaction (stats line "PvP house cut collected");
otherwise it stays in the bank (sink, §3.5 of GAME_DESIGN). The bankroll is never debited and never
reserved for PvP. The owner can't take part (§3.2 rule 9).

**Payout.** Winners are determined by the mode's pure `score()` (§3.14). With k tied winners each gets
`floor(W / k)`; the `W mod k` odd chips go one each to the tied winners earliest in **seat order**
(random, from the tape). Settlement = one atomic transaction: `bank −W−rake`, winners `+…`, bankroll
`+rake` (if owned). Winners who are offline are credited through the offline deposit path (loan
garnishment and the balance cap apply exactly as for any credit).

**Not affected by house mechanics:** no streak re-draw (§14 is never consulted in `pvp`), no Golden
Hour bonus, no VIP cashback (cashback is on house theoretical loss only), no progressive jackpot
contribution or award, no chaos events triggered by PvP results (specials only score; a KABOOM
spawns nothing), no big-win `lucky_buff` roll.

**Counted:** each participant's stake counts as *wagered* once per match for VIP lifetime wagered
(`pvp.countsTowardVip`, default true) and for the `wager` contract; other contracts (`spin_slots`, …)
do not count PvP spins. Statistics: lifetime PvP wins/losses/net per player. The big-win broadcast
(`core.announceBigWins`) uses the PvP broadcast message instead (§3.11.5).

### 3.5 Fairness (the proof every mode relies on)

Let the participants be 1…N with stakes s_i, pot P = Σ s_i, rake R, W = P − R.

**Lemma 1 (exchangeable modes, equal stakes s).** If the joint distribution of the tape is invariant
under permuting the participants (all draws i.i.d. per participant plus a uniformly random seat
order) and `score()` treats participants only through their tape data and seat position, then every
participant has the same distribution of payout. Since Σ payouts = W exactly (ties and odd chips
included), each participant's expected payout is W/N and

```
EV_i = W/N − s = (Ns − R)/N − s = −R/N.
```
Every rule in this file that looks "unfair in the moment" — Underdog Boost, KABOOM, Pearl SWAP,
Time Warp, creepers, Rabbit's Feet, odd-chip order — is a symmetric function of the state, so
Lemma 1 holds for Coin Flip Duel, Slot Showdown, Plinko Battle and Scratch Showdown.

**Lemma 2 (proportional mode, Wheel Party).** u is uniform on the integers `[0, P)`; participant i owns
`[C_{i−1}, C_i)` with `C_i − C_{i−1} = s_i`. So `Pr(i wins) = s_i / P` exactly, and
```
EV_i = (s_i / P) · W − s_i = −s_i · R / P        (the rake is shared pro rata)
```
**Lemma 3 (Double or nothing).** Each flip of the chain is a fresh fair 50/50 flip with equal stakes,
so each flip has EV −R_k/2 for both players whatever the history; decisions to continue or stop do
not change any flip's EV (a sum of fair bets is fair: optional stopping cannot create an edge).

**House take.** The house's take is exactly R per match, ≈ `pvp.rakeBasisPoints` of every pot, and
nothing else. With the default 300 bp a player pays on average 3 % of what they stake, which is more
than the cheapest house game (coin flip, 2 %) — so PvP is never the cheapest way to farm VIP
wagered (§3.12).

Examples (defaults): Coin Flip Duel 100 v 100 → P 200, R 6, winner +94, EV −3 each. Slot Showdown
4 × 100 → P 400, R 12, winner +288, EV −3 each. Wheel Party 50/150/800 → P 1000, R 30, W 970;
win chances 5 % / 15 % / 80 %; EV −1.5 / −4.5 / −24 (sum −30 ✓).

### 3.6 Match state machine, persistence and play-out

```
          ┌──────────── duel ────────────┐
INVITED ──accept (escrow both)──────────▶ STARTING
   │ decline / timeout / withdraw → CLOSED (nothing was escrowed)
LOBBY (entries escrowed) ──start rule §3.3.2──▶ STARTING
   │ cancel (<2 at timeout, anchor gone, casino off, host alone leaves) → CANCELLED (refund all)
STARTING: re-check rules 1, 3, 7 for everyone whose stake is not yet escrowed; draw the TAPE
          (seat order + every random value of the mode); persist {state: DRAWN, tape}
          ─────────────────────────────▶ REVEAL
REVEAL:   the mode's timeline plays (§3.11.4); no money moves; leaving, disconnecting,
          walking away and anchor breakage change nothing
          ── timeline ends │ casino mode off │ server stop │ load after crash ──▶ SETTLE
SETTLE:   one atomic transaction (payouts + rake); rivalry, win streak, statistics,
          advancements; persist {state: SETTLED}  ─▶ RESULT (rematch window §3.10) ─▶ CLOSED
```

- **Persistence.** Java: `SavedData` `burmaldaholic_pvp` (matches, invites are not saved). Bedrock:
  world dynamic property `burmaldaholic:pvp:<id>` (JSON) + an index property `burmaldaholic:pvp_index`.
  The largest tape (Slot Showdown, 6 players × 10 spins × 9 cells + 10 hot symbols) is < 700 chars.
  The DRAWN record is written **before the first reveal packet/title** (Bedrock: synchronously in
  the same tick; Java: mark dirty and, for a `DRAWN` write, call the level's save-data flush if the
  API allows, else accept the §4.1 crash caveat).
- **Tape confidentiality.** Java never sends unrevealed parts of the tape to clients (the screen
  receives each step as it is revealed). Bedrock is server-only anyway.
- **Play-out on load / stop.** On world load: `LOBBY` → refund every entry (offline-safe, message on
  join `msg.burmaldaholic.pvp.lobby.refunded`); `DRAWN` → settle from the tape (offline-safe, message on
  join `msg.burmaldaholic.pvp.result.offline`); `SETTLED` → nothing. Java may instead settle `DRAWN`
  matches at the stop, before the world saves (same result).
- **Casino mode off mid-match:** invites withdrawn; lobbies refunded; `DRAWN` matches settle at once
  (animation skipped, `msg.burmaldaholic.pvp.result.casino_off`). This follows §4.1: drawn rounds are
  played out, undrawn bets refunded.
- **Disconnect / leave / walk away during REVEAL:** the match keeps playing; the leaver gets
  `msg.burmaldaholic.pvp.result.away` (if still online) and the result later.
- **Per mode play-out:**

| Mode | Drawn at | Choices after START | Disconnect / stop / casino off |
|------|----------|---------------------|--------------------------------|
| Coin Flip Duel | accept (one flip per link) | none inside a flip; Double or nothing is a **new** match | settle the flip; a pending Double-or-nothing offer lapses (= walk away) |
| Slot Showdown | START (all spins, hot symbols, seat order) | *Spin!* only speeds up the timeline | settle from tape |
| Wheel Party | START (= "No more bets") | none | settle from tape |
| Plinko Battle | START (all paths, seat order) | *Drop!* only speeds up | settle from tape |
| Scratch Showdown | START (all cells, seat order) | *Scratch!* only speeds up | settle from tape |

### 3.7 Timers (defaults)

| Timer | Key | Default |
|-------|-----|---------|
| Invite answer | `pvp.inviteTimeoutTicks` | 600 (30 s) |
| Lobby fill | `pvp.lobbyTimeoutTicks` | 1800 (90 s) |
| Human decision (Double or nothing offer/answer, rematch) | `pvp.decisionTimeoutTicks` | 300 (15 s — UI.md §13 minimum) |
| Pre-match countdown ("3… 2… 1…") | `pvp.countdownTicks` | 60 |
| Settled-match history kept (for rematch/records) | `pvp.historyTicks` | 6000 |

Pacing timers (auto-spin, auto-drop, auto-scratch) are per mode; they are not decisions, so they
may be shorter than 15 s.

### 3.8 Rivalry, win streaks, grudge matches

**Head-to-head record** per ordered pair (A→B): wins, losses, net chips, and the current *run*
(+n = A won the last n against B, −n = lost the last n). Stored per player (Java attachment;
Bedrock player property `burmaldaholic:pvp_record`, JSON, the 50 most recent rivals kept, LRU).
Updated at SETTLE: in a match with a unique winner, the winner gets a win against each loser and
each loser a loss against the winner (losers do not score against each other). Split winners score
nothing against each other and a win against each loser. Records are shown in invites, lobbies and
the hub (**Nemesis** = the rival with the worst net for you, if ≤ −3 matches).

**PvP win streak** per player (separate from §14 Lucky streak, which PvP never touches): +1 on a
match win (a split win counts), reset to 0 on a loss. Announced at the thresholds
`pvp.streakAnnounce` = [3, 5, 10] (three message tiers: heating / rampage / legendary) to players
within `pvp.announceRadius` (32), and server-wide for the top tier. A streak ≥ the first threshold
that is broken gives the "streak broken" call-out naming the breaker.

**Grudge match.** In a 2-player match, if one side's run against the other is ≤ −`pvp.grudgeLosses`
(3), the match is a **GRUDGE MATCH**: banner title for both, ravager roar, the lobby/invite shows it.
Winning it as the underdog gives `pvp_revenge`. It changes no money (a rake waiver would let
colluding accounts dodge the rake; see §3.12).

### 3.9 Taunts

- 8 fixed, translated lines (§15.4): *GG, well played · Good luck. You'll need it · No way! · It's
  rigged! · Again. Right now. · Nerves of steel · Say bye to your chips · Respect*.
- Only participants, from invite acceptance until the rematch window ends. Delivered to participants
  and players within `pvp.announceRadius` as chat `msg.burmaldaholic.pvp.taunt.say` plus a villager
  "yes" (friendly lines) or "no" (cheeky lines) sound at the sender.
- Cooldown `pvp.taunts.cooldownTicks` (100) per player, max `pvp.taunts.maxPerMatch` (5). Muted
  entirely by `pvp.taunts.enabled` = false. No free text (no moderation burden, fully translated).

### 3.10 Rematch

After SETTLE every participant sees **Rematch** for `pvp.decisionTimeoutTicks`. Duel: both must press.
Lobby: the rematch starts when all have answered or the timer ends, with every participant who
pressed (≥ 2 needed), same mode, same parameters, a fresh tape. Stakes are escrowed at the moment the
rematch starts (eligibility re-checked; anyone who can't cover it is dropped with the error). The
grudge flag is recomputed. Wheel Party rematch re-opens the lobby with everyone's previous stake
pre-filled (they may change it).

### 3.11 UI and drama — shared parts

#### 3.11.1 Edition split

| Need | Java (custom screens, server-driven like UI.md §0.2) |
|------|------------------------------------------------------|
| Set-up, join, invite answer | Screens (below) |
| Live match | Mode screen; when closed, a one-line **match ticker** HUD overlay (top-centre, under the boss bar) |
| Final reveal | Screen banner sequence + titles |
| Result | Result panel on the screen: ranking, pot, rake, payout, [Rematch] [Taunt] [Close] |
| Spectators | Players within `pvp.announceRadius` get the ticker overlay line and titles for the Final Reveal winner only |

**Russian length.** All Java widths use `max(minWidth, textWidth + 8)` and wrap (UI.md §0.1). Bedrock
buttons were checked to be ≤ 24 RU characters (§15 notes the longest). Action-bar lines are ≤ 44 EN
characters so RU (×1.45) stays ≤ 64.

#### 3.11.2 PvP hub (Casino Menu → Challenges)

Java: the existing *Challenges* tab becomes a 3-part panel (256 × 200):
```
 Your record: 12 wins · 9 losses · net +340        Nemesis: Alex (1–5)
 ─ Pending ───────────────────────────────────────────────────────────
  Alex · Coin Flip Duel · 200         [Accept] [Decline]      ⏱ 22
 ─ Open lobbies nearby ───────────────────────────────────────────────
  Slot Showdown · Golden Reels · 100 · 3/6                     [Join]
 ─────────────────────────────────────────────────────────────────────
 [New match…]  [Head-to-head]  [Dice Duel]  (NICE: [Tournaments] [Side bets])
```
Bedrock ActionForm "Challenges": body = record line (+ nemesis line); buttons: one *Accept: <name> ·
<game>* and one *Decline: <name>* per pending invite (max 2 invites shown, newest first) · *Open
lobbies nearby (n)* · *New match…* · *Head-to-head* · *Dice Duel* (existing flow) · Back.
- *New match…* → ActionForm of duel-capable modes (Coin Flip Duel, Scratch Showdown) + a body line
  explaining that machine modes start at their machines → mode set-up ModalForm (§4.4, §8.4).
- *Head-to-head* → ActionForm body listing up to 10 rivals `Name: W–L · net N`, button Back.
- Settings page gains toggle *Accept PvP challenges* (`gui.burmaldaholic.pvp.settings.invites`).

#### 3.11.3 Lobby view

Java `PvpLobbyScreen` 256 × 200 (compact: same, rows 10 px):
```
 Slot Showdown — Golden Reels                                   ⏱ 58
 Entry 100 · 5 spins each · Pot 300 · House cut 3%
 ─────────────────────────────────────────────────────────────────────
 1 ◆ Alex (host)    ALL-IN       Head-to-head 3–5
 2 ◆ You                          —
 3 ◆ Bob                          Head-to-head 1–0
 4   Empty seat
 ─────────────────────────────────────────────────────────────────────
 [Start] (host only, ≥2)   [Leave lobby]   [Taunt…]           ⛁ 12 500
```

#### 3.11.4 The Final Reveal (shared choreography, all multi-player modes)

Each mode's last step (last spin / ball / cell) is **not** revealed per player as it lands. Instead:

| t (ticks) | Everyone in the match | Spectators in radius |
|-----------|----------------------|----------------------|
| 0 | Title "Final results…", drumroll: `note.basedrum` / `block.note_block.basedrum` 8 hits accelerating over 40 t | action bar "Final results…" |
| 40, 60, … | For each place from **last to 2nd**: subtitle `#k: Name — points` + bell note (pitch 0.8 → 1.6 rising); `angry_villager` particles at that player | — |
| +30 (pause) | silence | — |
| end | Title **"NAME WINS!"** + subtitle payout; win jingle + level-up sound; totem particles + firework twinkle at the winner; losers get `burmaldaholic:lose` | title "NAME WINS!" |

Two players: skip straight from the drumroll to the winner. Max length 40 + 5 × 20 + 30 + 40 =
210 t (10.5 s) at 6 players. Ties: the title is "Dead heat!" with the split.

#### 3.11.5 Announcements

- Match start and result: chat to participants; result also to players within
  `pvp.announceRadius`.
- Server-wide chat if the pot ≥ `pvp.announceServerWidePot` (5000) or the winner is on a top-tier
  streak (`msg.burmaldaholic.pvp.result.broadcast`).
- ALL-IN: if a participant's escrow leaves them with balance 0, their name gets the tag **ALL-IN** /
  «ВА-БАНК» everywhere in that match and the countdown adds one extra drum hit per all-in player.

### 3.12 Anti-abuse and collusion

| Risk | Rule |
|------|------|
| Chip laundering between accounts | PvP only moves chips players could already hand over as chip items; the extra route it opens is closed for debtors (rule 3: no PvP while owing — you can't win your loan money off an alt, nor dump it). Every transfer costs ≥ the rake. |
| Streak farming (tiny PvP matches to reach +10 and get lucky re-draws at the house) | PvP never updates the §14 streak (`pvp.affectsStreak` false). |
| VIP wagered farming | Counted (it is real risk), but the rake makes it cost 3 % per chip, more than house coin flip (2 %). Config validation: if `pvp.countsTowardVip` and `pvp.rakeBasisPoints` < 200, log a warning and show it on the admin page (`gui.burmaldaholic.menu.admin.rtp_warning` style line, `…pvp.admin.rake_warning`). |
| Rake dodging via grudge/tournament bonuses | Nothing waives the rake in MUST. Tournament "added prize" (NICE) can only be added by operators. |
| Throwing a match | MUST modes have no choices after START → nothing to throw. Side bets (NICE) only on chance-only modes and never by participants or the anchor's owner. |
| Owner self-dealing | Owner cannot join PvP at own machines; rake to bankroll is visible on the charter stats. |
| Leaving/crashing to avoid a loss | Tape drawn and persisted before any reveal; settle from tape. |
| Invite spam | 1 outgoing invite at a time; 30 s decline cooldown per pair; per-player "accept challenges" toggle. |
| Taunt spam | Fixed lines, cooldown, per-match cap, off switch. |
| Advancement farming with alts | Cosmetic only; `pvp_rampage` needs ≥ 2 distinct opponents, `pvp_full_house` needs 5 distinct opponents in one match, `pvp_revenge` needs a real 3-loss run. |
| Timeouts / stalling | Every wait has a timer; defaults are the safe option (decline, walk away, auto-spin). |

### 3.13 Commands

Java (`/casino pvp …`, permission 0 unless noted): `challenge <player> <mode> <amount> [heads|tails]`,
`accept [id]`, `decline [id]`, `leave`, `record [player]`, `taunt <id>`; ops (level 2): `list`,
`cancel <matchId>` (LOBBY → refund; DRAWN → settle now). Bedrock: players use items/menus; ops use
`/scriptevent burmaldaholic:pvp list|cancel <id>` and Casino Card → Admin → PvP matches.

### 3.14 Mode contract (write this first; all devs code against it)

Pure, engine-free, shared test vectors:
```
interface PvpMode<P, T> {
  id: 'coin' | 'slots' | 'wheel' | 'plinko' | 'scratch';
  minPlayers: number; maxPlayers: number;
  anchor: 'none' | 'slot_machine' | 'wheel_of_fortune' | 'plinko_machine';
  equalStakes: boolean;                                  // false only for wheel
  validate(params: P): ErrorKey | undefined;             // ranges from §13
  draw(rng: Rng, n: number, params: P): T;               // ALL randomness, incl. seatOrder
  score(tape: T, stakes: number[], params: P): Outcome;  // pure; deterministic
  timeline(tape: T, outcome: Outcome, params: P): Step[];// what to reveal at which tick
}
Outcome { points: number[]; rankOrder: number[]; winners: number[]; events: Event[] }  // events: kaboom, swap, …
```
Core owns everything else (§3.1–§3.13, §3.15) and exposes to modes: `revealStep(match, step)` (fans out to
screens / action bar / titles), `sound(match, id, who)`, `particles(match, id, who)`.
Each mode also implements `botDecide(decision, view, difficulty, rng) → choice` for the decisions
listed in §3.15.4 (MUST modes: Coin Flip Duel and Wheel Party only; the others have no decisions).

### 3.15 Bots and seating policy (MUST)

The shared rules — the three **seating policies** (`HUMANS_ONLY`, `BOTS_ONLY`, `MIXED`), who sets
them, the **difficulty** levels (Easy / Normal / Hard, and Mixed = random per bot), bot names and
skins, bot economics and anti-farming — are defined in **`BOTS.md`** (cross-game bot & seating
spec; background research in `docs/research/bots.md` §2.6 and §3). This section only says how PvP
applies them; where the two disagree, BOTS.md wins.

#### 3.15.1 Policy per match

| Match kind | Who sets the policy | How it is chosen | Default |
|------------|--------------------|------------------|---------|
| Duel (Coin Flip Duel, Scratch Showdown vs one player) | the challenger | the *Opponent* dropdown lists nearby players **and** "Bot — Easy / Normal / Hard / Mixed"; a player = `HUMANS_ONLY`, a bot = `BOTS_ONLY` | player (if any nearby) |
| Lobby (Slot Showdown, Plinko Battle, Wheel Party, Scratch Showdown open lobby) | the host, at set-up | dropdown *Seats* (policy, BOTS.md names) + dropdown *Bot difficulty* + slider *Table size* (2 … mode max) | `pvp.bots.defaultPolicy` (`MIXED`), `pvp.bots.defaultDifficulty` (Mixed) |
| Tournament (NICE, knockout) | the operator | `MIXED` = empty bracket slots up to the next power of two are filled with bots at the start; `HUMANS_ONLY` = byes instead | `HUMANS_ONLY` |

- `HUMANS_ONLY`: exactly the current behaviour of §3.3.
- `BOTS_ONLY`: the host plays alone against `Table size − 1` bots; the match starts **immediately**
  after set-up (no lobby, no timer). Nobody else can join.
- `MIXED`: a normal lobby; when it starts (host *Start*, lobby full, or the lobby timer), every empty
  seat up to *Table size* is filled with a bot. The host may press *Start* alone ("fill with bots");
  the < 2 players cancel rule of §3.3.2 then never triggers. Wheel Party: bots join during the
  countdown (§3.15.4), not all at the end; with `BOTS_ONLY` the bots place their stakes at once
  and the countdown is shortened to 100 t so the slices can be seen before the spin.
- Owned machines: bots are only seated if BOTS.md's bankroll rules allow it (bot stakes are funded
  and reserved by the bankroll there); otherwise the policy falls back to `HUMANS_ONLY` with
  BOTS.md's "no bots here" message.
- `pvp.bots.enabled` = false or `pvp.bots.maxPerMatch` reached → the bot options are hidden.

#### 3.15.2 How a bot takes part

- A bot is a **participant** like any player: it has a seat, a stake equal to the entry (Wheel Party:
  its chosen stake), its own tape data drawn **by exactly the same `draw()` call** as the humans', and
  it can win, tie and split.
- Bot money follows BOTS.md (house-funded; the house — bank, or the bankroll at owned machines —
  pays bot stakes into escrow and receives bot payouts). The rake is computed on the full pot and,
  in any match with a bot, always goes to the **bank**, never to an owner's bankroll (an owner + alt
  must not be able to farm owner-side rake off bots; see BOTS.md economics).
  Consequence at defaults: a human's EV against bots is the same −rake/N as against humans (Lemma 1
  / 2), so playing vs bots is a house game with a ≈ 3 % edge — never positive for the human in any
  MUST mode, whatever the difficulty.
- Bots never trigger or receive: rivalry records, win-streak call-outs, grudge matches, side bets.
  Humans' records ignore bot opponents, and a human's PvP win streak (§3.8) only counts matches
  with at least one human opponent (a bot-only match neither extends nor breaks it).
- Advancements: `pvp_revenge`, `pvp_rampage`, `pvp_full_house` and `pvp_champion` require human
  opponents; all other PvP advancements may be earned against bots.
- Bots press *Spin! / Drop! / Scratch!* after a think time (BOTS.md; default 10–40 t) so humans never
  wait on them, and use taunts per their personality (Easy: friendly lines, Hard: cheeky lines;
  same cooldown and cap as players).
- Bots are shown with their BOTS.md name, skin/head and a difficulty tag in every list, ranking and
  title (e.g. "Creeper42 [Hard]").

#### 3.15.3 Fairness invariant (difficulty never touches RNG)

- Difficulty only selects among **decisions** (§3.15.4). It never changes weights, draws, the tape,
  seat order, tie-breaks or scoring. The tape is drawn before any bot decision and without knowing
  which seats are bots.
- In every MUST mode the only bot decisions are EV-neutral by Lemma 2/3 (Double-or-nothing continue
  / stop, Wheel Party stake size and timing), so **every MUST mode has the same EV for a human
  against Easy, Normal or Hard bots**. Tests §16.8.
- Bots never see hidden information (they read the same `view` a human in their seat would get; no
  tape look-ahead).

#### 3.15.4 What difficulty changes, per mode

| Mode | Bot decisions | Easy | Normal | Hard |
|------|---------------|------|--------|------|
| Coin Flip Duel | side (cosmetic); as chain loser: call Double or nothing?; as chain winner: let it ride? | calls DoN 80 %, lets it ride 80 % (loves drama) | calls 50 %, rides 50 % | calls 30 %; lets it ride only up to link 3 (stake ≤ 2S), then takes the money |
| Slot Showdown | none (pure chance); pacing and taunts only | slow (30–40 t) | 15–30 t | fast (10–15 t) |
| Wheel Party | stake size; top-up timing | stakes the minimum, joins early, never tops up | stakes a random 20–60 % of the cap, may top up once | joins mid-countdown with 50–100 % of the cap and tops up in the last 3 s before *No more bets* ("snipe") to become the biggest slice when affordable |
| Plinko Battle | none (MUST); NICE bumpers: whom to hit | random target | the current leader | the leader, and only when the leader's risk makes the push-to-centre most costly (High risk, final ball) |
| Scratch Showdown | none (pure chance) | — | — | — |
| NICE Scratch Poker | check / bet / call / raise / fold, bluffs | calls down with any card, bluffs 15 % at random | acts on exact current-score percentile vs the known distribution (§8.4): bet ≥ 65th, fold < 25th facing a bet | Normal + conditional equity from the revealed cells (DP of §16.6 over the remaining cells), bluffs 10 % with blockers (Rabbit's Foot showing) |
| NICE Wheel Heist | Steal target | random opponent | the leader | the opponent whose loss most improves its own win chance (leader, or the runner-up if the leader has a Shield) |
| NICE Coin Series / Jackpot Race | none | — | — | — |
| NICE Tournaments | bots fill bracket slots (`MIXED`); they play their matches like any bot | per the tournament's difficulty setting | | |

EV note for the NICE decision modes: Scratch Poker and Wheel Heist decisions change EV between
players; against house-funded bots that could turn a skilled human +EV. They therefore ship only
with BOTS.md's anti-farming limits for skill games (the same ones poker bots use) and the side-bet
ban; this is part of why they are NICE.

#### 3.15.5 UI additions

- Set-up forms/screens gain *Seats* (policy) and *Bot difficulty* dropdowns and a *Table size*
  slider (lobbies) — labels and policy/difficulty names come from BOTS.md's keys; PvP adds only the
  strings in §15.15.
- Lobby list rows show bots with their tag; `MIXED` lobbies show "n bots will fill the empty seats".
- Hub *New match…* opponent dropdown: nearby players first, then the four bot entries.

---

## 4. Coin Flip Duel [pvp.coin] — MUST

### 4.1 Rules

- 2 players. The **challenger** picks the stake S (`pvp.minStake` … own tier max) and a side (Heads
  / Tails); the **target** gets the other side. The invite shows S, the side and the head-to-head.
- On accept both S are escrowed, the flip is drawn (`heads = rng.nextBoolean()`) and persisted, then
  the countdown plays (`pvp.coin.countdownTicks`, 60 t; +20 t per ALL-IN player), then the coin lands.
- Winner receives `W = 2S − rake`. No ties.

### 4.2 Double or nothing (the chain)

After a flip the **loser** (only) sees *Double or nothing — Heads* / *— Tails* / *Walk away* for
`pvp.decisionTimeoutTicks`. The loser calls the side. Then the **winner** sees *Let it ride* / *Take
the money* (same timer; timeout = take the money). If both agree, a new linked flip starts at once.

Chain arithmetic (this is what makes it "double or nothing"): let D = the loser's total deficit in
the chain *before rake* (after the first flip D = S). **The next flip's stake is D for each player.**

| Flip | Stake each | If the chain loser loses again | If the chain loser wins |
|------|-----------|--------------------------------|--------------------------|
| 1 | S | D = S | (roles swap: the other player is the loser, D = S) |
| 2 | S | D = 2S | **ALL SQUARE** — both back to 0 before rake; chain ends |
| 3 | 2S | D = 4S | all square |
| 4 | 4S | D = 8S | all square |
| 5 | 8S | D = 16S (chain over) | all square |

- The chain ends at "all square", when anyone walks away / takes the money, or after
  `pvp.coin.maxDoubles` (4) doubles (so at most 5 flips; the largest stake is 8S).
- A Double or nothing offer is only shown when D ≤ both players' tier max and both balances ≥ D;
  otherwise the button is replaced by a disabled line (tooltip) with the
  reason (`gui.burmaldaholic.pvp.coin.don_limit` / `…don_unaffordable`).
- Each flip is its own match record (id, tape, rake). The chain is only a link (`chainOf`, `link#`).
  A server stop between flips ends the chain; a stop during a flip settles that flip.

### 4.2a Bots

A bot can be the opponent (`BOTS_ONLY` duel). It picks a side at random and makes the two
Double-or-nothing decisions per §3.15.4 (Easy loves the chain, Hard walks away early). Each flip
stays a fair coin, so difficulty changes the story, not the odds.

### 4.3 State machine

```
INVITED ─accept→ ESCROW(S,S) → DRAWN(flip) → COUNTDOWN(60t) → LANDED → SETTLE
                → OFFER_LOSER (15 s: DoN Heads | DoN Tails | Walk away | timeout = walk away)
                   → OFFER_WINNER (15 s: Let it ride | Take the money | timeout = take the money)
                      → ESCROW(D,D) → DRAWN … (link+1)          │ else → RESULT (Rematch window) → CLOSED
```

### 4.4 Payout math

Fair coin, equal stakes: EV = −rake/2 per flip per player (Lemma 1, 3). Worked chain (S = 100,
300 bp): flip 1 B wins (pot 200, rake 6: B +94, A −100). Flip 2 (stake 100) B wins (B +94, A −100).
Flip 3 (stake 200, rake 12) B wins (B +188, A −200). Flip 4 (stake 400, rake 24) **A wins**: A +376.
Totals: A = −100 −100 −200 +376 = **−24**; B = 94 + 94 + 188 − 400 = **−24**; rake 6+6+12+24 = 48 ✓.
"All square (minus the house's cut)".

### 4.5 UI

**Java** `CoinDuelScreen` 256 × 180, opens for both duelists on accept:
```
        Alex            vs            You
       [HEADS]                      [TAILS]
        200 ⛁          Pot 400        200 ⛁         Head-to-head 3–5
                   ( coin sprite, 12-frame spin,
                     slows down over the countdown )
 ──────────────────────────────────────────────────────────────────
  "TAILS!  You take 388"                                (ResultBanner)
 [Double or nothing — Heads] [Double or nothing — Tails] [Walk away]      (loser)
 [Let it ride] [Take the money]                                            (winner, on offer)
 [Rematch] [Taunt…] [Close]                                                (after the chain ends)
```
Set-up: the coin used on a player opens a 200 × 140 panel: `BetSelector` (UI.md §0.2), side toggle
Heads | Tails, head-to-head line, [Throw down the gauntlet]. Chain status line under the pot:
"Chain: Alex down 400".

Spectators within `pvp.announceRadius`: action bar "Alex vs Bob — 200 on the line" at start and the
landing title's text as an action-bar line.

### 4.6 Advancements, sounds, particles

Advancements: `pvp_all_square` (win a Double-or-nothing flip as the chain loser). Sounds: invite
horn, countdown hats, `burmaldaholic:coin_flip` at launch, win/lose, `random.levelup` for "ALL
SQUARE". Particles: `crit` burst at the coin's landing (in front of the winner), totem at the
winner. See §12.

---

## 5. Slot Showdown [pvp.slots] — MUST

### 5.1 Rules

- 2 … `pvp.slots.maxPlayers` (6). Anchor: a slot machine; the **tier** of the anchor is the match
  tier (Copper Bandit / Golden Reels / Netherite High Roller). Joiners use a machine of the same
  tier within `pvp.slots.linkRadius` (8) blocks of the anchor, or the anchor. Netherite: Gold VIP.
- Host sets: entry E (≥ `pvp.minStake`, ≤ host tier max) and spins N ∈ `pvp.slots.spinChoices`
  ([3, 5, 10]; default 5).
- Every participant gets N spins of that tier's machine, **drawn exactly like a solo spin** (9 cells
  i.i.d. from the tier's symbol weights, §8.1), evaluated on the tier's paylines (1 / 3 / 5) with the
  solo evaluator. No money moves per spin; line wins become **points**.
- Spins happen in **rounds**: round r = everyone's r-th spin, revealed together.

### 5.2 Scoring

Per line, `linePoints` = the solo line multiplier (e.g. Diamond ×50 → 50; 1 leading berry → 2; 2 → 3;
three Wilds → 200/250), with these PvP-specific values for specials:

| Line result | Points | Effect |
|-------------|--------|--------|
| Three Creepers (Copper, Gold) / three TNT (Netherite) | 0 | **KABOOM**: your total *before this spin* is halved (floor). Several KABOOM lines in one spin halve once. |
| Three Ender Pearls | 10 | **SWAP**: after the round's points are added, if you are not the leader, swap totals with the leader. |
| Three Clocks (Netherite) | 50 | **TIME WARP**: your next spin's points ×2 (no effect on the last spin). No Golden Hour. |
| Three Nether Stars (Gold, Netherite) | `pvp.slots.starPoints` (500) | none (no jackpot in PvP) |

**HOT symbol.** Each round the tape holds one regular paying symbol drawn uniformly from {Sweet
Berries, Apple, Golden Carrot, Emerald, Diamond, Redstone Seven}. Every line whose paying combination
is that symbol (including Wild substitutions and berry partials) scores ×2 that round
(`pvp.slots.hotSymbol`).

**Underdog Boost.** Before the final round, every player whose total is **strictly the lowest** (not
all tied) scores ×2 on the final spin (`pvp.slots.underdogBoost`).

**Round resolution order** (all players at once):
1. `spinPoints_i = Σ_lines linePoints × (2 if the line's symbol is HOT)`, then × 2 for TIME WARP pending,
   × 2 for Underdog Boost (multipliers stack; max ×8).
2. For every player with a KABOOM line this spin: `total_i = floor(total_i / 2)`.
3. `total_i += spinPoints_i`.
4. SWAPs in seat order: for each player with a Pearl line who is not *the* leader, swap totals with
   the leader (leader = highest total; among tied leaders the earliest in seat order, excluding the
   swapper).
5. Set TIME WARP pending for players with a Clock line (cleared after use).

**Winner:** highest total; tie-break 1 = more paying lines over the match; tie-break 2 = higher best
single-spin points; still tied → split (§3.4).

### 5.2a Bots

Bots fill seats per the lobby policy (§3.15.1) and spin their own tape like anyone; they make no
scoring decisions (the mode has none). Difficulty only sets how fast they press *Spin!* and how they
taunt. KABOOM/SWAP hit and help bots exactly like players.

### 5.3 State machine and timeline

```
LOBBY → START (tape: seatOrder, hot[1..N], grid[player][1..N]) → for r in 1..N:
   ROUND_WAIT (announce HOT; up to pvp.slots.spinIntervalTicks = 100 t, or until every online
               participant pressed Spin!) → ROUND_SPIN (40 t reels) → ROUND_SCORE (show points,
               KABOOM / SWAP / TIME WARP events 20 t each) 
   before round N: UNDERDOG announcement (20 t)
   round N: reels spin, points hidden → FINAL REVEAL (§3.11.4) → SETTLE → RESULT
```
Typical length at N = 5: 5 × (5 + 2 + 1) s + 6 s ≈ 46 s max; ≈ 25 s when everyone presses Spin!.

### 5.4 Payout math

Equal entries, i.i.d. spins per player, symmetric scoring → Lemma 1: EV = −rake/N. Reference numbers
(tests, §16.3): mean points per spin without HOT/boosts: Copper 0.897598, Golden Reels 2.793399,
Netherite 4.746735; P(KABOOM on a spin) 0.003375 / 0.001535 / 0.001080. Copper matches often end low
(P(a player scores 0 in 5 spins) = 0.7455⁵ ≈ 0.23): that is why HOT and the tie-breaks exist.

### 5.5 UI

**Java** `SlotShowdownScreen` 400 × 240 (compact < 400 px wide: 320 × 220):
```
 SLOT SHOWDOWN · Golden Reels · Spin 3/5 · HOT: Diamond ×2 🔥         Pot 600  ⏱ 4
 ┌ Alex ───────┐ ┌ You ────────┐ ┌ Bob ────────┐
 │ ▣ ▣ ▣  #1   │ │ ▣ ▣ ▣  #2   │ │ ▣ ▣ ▣  #3   │    each panel 124 × 76: name (16 chars),
 │ ▣ ▣ ▣  57   │ │ ▣ ▣ ▣  42   │ │ ▣ ▣ ▣  12   │    3×3 reels at 16 px, total, rank,
 │ ▣ ▣ ▣ +20   │ │ ▣ ▣ ▣  +0   │ │ ▣ ▣ ▣ +50   │    this spin's points, ALL-IN tag
 └─────────────┘ └─────────────┘ └─────────────┘
 ┌ Cid …        (row 2 for players 4–6)
 [SPIN!]  [Taunt…]  [Rules]                                          ⛁ 12 500
```
Compact mode: your panel at 2× size on the left, a ranking list (name · total · last spin) on the
right. KABOOM: panel shakes 10 t, red flash, score counts down. SWAP: two panels' totals fly across
(20 t). HOT symbol cells glow. Final round: totals show "???" until the Final Reveal.
Host set-up: panel on the machine screen: entry `BetSelector`, spins toggle 3 | 5 | 10, [Open lobby].

### 5.6 Advancements, sounds, particles

`pvp_phoenix` (win after a KABOOM hit you). Sounds: `slot_spin` per round, HOT = firecharge,
KABOOM = explosion at 0.5 volume, SWAP = enderman teleport, TIME WARP = bell. Particles: `flame` on
HOT cells' machine, `explosion` puff at the victim's machine, `portal` between swapped players'
machines.

---

## 6. Wheel Party [pvp.wheel] — MUST

### 6.1 Rules

- 2 … `pvp.wheel.maxPlayers` (8). Anchor: a Wheel of Fortune block. Players join by using the anchor
  (only the anchor — it is one wheel).
- Host sets the **cap** C (max total stake per player, `pvp.minStake` … host tier max) and their own
  stake. Each participant stakes `s_i ∈ [pvp.minStake, min(C, own tier max)]` and may **top up** (total
  ≤ that bound) until *No more bets*. Stakes are escrowed at join / top-up.
- The wheel is repainted: each participant owns one coloured **slice** with arc = `s_i / P`
  (colours = the 16 dye colours in seat-join order; slices in join order).
- As soon as 2 players are in, the **spin countdown** `pvp.wheel.countdownTicks` (600 = 30 s) starts
  (it does not reset on joins). Host may *Spin the wheel!* any time with ≥ 2 players. At
  `pvp.wheel.noMoreBetsTicks` (60) before the spin: "No more bets!" — joins and top-ups close. The
  lobby timer of §3.3.2 does not apply (the countdown replaces it); with < 2 players after
  `pvp.lobbyTimeoutTicks` the party is cancelled and refunded.
- START = No more bets: draw `u = uniform integer in [0, P)` (Java `rng.nextLong(P)`, Bedrock
  `Math.floor(Math.random() * P)`; P ≤ 8 × tier max < 2⁵³). The owner of the slice containing u wins
  W = P − rake.

### 6.2 Spin presentation

- Pointer angle at rest: `360° × (u + 0.5) / P` measured from slice 0's start; the wheel turns 3 full
  turns + that angle, ease-out over 100 t.
- **By a hair**: with u in slice `[C_{i−1}, C_i)`, if `min(u − C_{i−1}, C_i − 1 − u) ≤ ceil(P × 0.02)`
  the message *By a hair!* names the owner of the slice across the nearer boundary (the circle
  wraps: the last slice borders the first), and Java slows the last 20 t.
- **Underdog**: if the winner's share `s_i / P ≤ 0.10` → "UNDERDOG!" broadcast to the radius + firework
  twinkle + `pvp_underdog`.

### 6.2a Bots

In `MIXED`/`BOTS_ONLY` parties bots join during the countdown and choose their slice size and
top-up timing by difficulty (§3.15.4) — a Hard bot's last-second snipe is part of the show. Any
stake choice is fair (Lemma 2: every slice pays −R/P per chip), so a human's EV per chip is
identical whatever the bots do.

### 6.3 State machine

```
LOBBY(host staked) ─2nd player→ COUNTDOWN(600 t; joins/top-ups allowed)
   ─ host "Spin" or countdown hits noMoreBets → NO_MORE_BETS (60 t) → START (draw u, persist)
   → SPIN (100 t) → RESULT_TITLE (40 t) → SETTLE → RESULT (rematch window, stakes pre-filled)
```

### 6.4 Payout math

Lemma 2: `Pr(i) = s_i / P`, `EV_i = −s_i × R / P`. Example (300 bp): stakes 50 / 150 / 800 → P 1000,
R 30; slices [0,50) [50,200) [200,1000); u = 49 → first player, u = 50 → second, u = 199 → second,
u = 200 → third. Each player loses 3 % of their stake on average — the same rate for everyone,
however big or small their slice.

### 6.5 UI

**Java** `WheelPartyScreen` 256 × 220:
```
      ◢■■■■◣           Slice                     Stake    Share
    ◢  wheel ◣         ■ Alex (host)              800     80 %
    ■  140 px  ■  ▼    ■ Bob                      150     15 %
    ◥  slices ◤        ■ You                       50      5 %   UNDERDOG
      ◥■■■■◤           Pot 1 000 · House cut 3 %
 Stake: [BetSelector ≤ cap 1 000]  [Add to my slice]       Spins in 0:18
 [Spin the wheel!] (host)   [Taunt…]   [Leave] (before No more bets)
```
Slices are drawn as arcs of the player's colour with their head icon at the arc centre (arcs < 6°
get no icon). Legend width fits a 16-char name + RU "ТЁМНАЯ ЛОШАДКА" tag in a second line.

### 6.6 Advancements, sounds, particles

`pvp_underdog`. Sounds: `wheel_tick` per slice passed, bell on stop, firework twinkle on underdog.
Particles: `happy_villager` ring at the wheel on the win, totem at the winner.

---

## 7. Plinko Battle [pvp.plinko] — MUST

### 7.1 Rules

- 2 … `pvp.plinko.maxPlayers` (6). Anchor: a Plinko machine; joiners use any Plinko machine within
  `pvp.plinko.linkRadius` (8) of it.
- Host sets entry E, risk (Low / Medium / High — one risk for everyone) and balls B ∈
  `pvp.plinko.ballChoices` ([1, 3, 5], default 3).
- Each ball is exactly a solo Plinko drop (12 independent left/right flips, bin k ~ Binomial(12, ½)).
- **Points per ball = `round(multiplier × 10)`** of the risk row:

| Bin | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|-----|---|---|---|---|---|---|---|---|---|---|----|----|----|
| Low | 100 | 30 | 16 | 14 | 10 | 10 | 5 | 10 | 10 | 14 | 16 | 30 | 100 |
| Medium | 330 | 110 | 40 | 20 | 10 | 6 | 3 | 6 | 10 | 20 | 40 | 110 | 330 |
| High | 1700 | 240 | 81 | 20 | 6 | 2 | 2 | 2 | 6 | 20 | 81 | 240 | 1700 |

(the rows are derived from the live config `extras.plinko.low/medium/high`, so they follow any change.)
- Rounds: round b = everyone's b-th ball, dropped simultaneously.
- **Underdog Boost** (`pvp.plinko.underdogBoost`): strictly-last before the final ball scores ×2 on it.
- **EDGE!** Bin 0 or 12 → announcement to the radius (fun only).
- Winner: highest total; tie-break = best single ball; then split.

### 7.1a Bots

Bots fill seats per the lobby policy and drop their own tape balls; no decisions in MUST (NICE
bumpers: §3.15.4). Difficulty = pacing and taunts only.

### 7.2 State machine and timeline

```
LOBBY → START (tape: seatOrder, path[player][1..B] as 12-bit masks) → for b in 1..B:
   BALL_WAIT (≤ pvp.plinko.roundIntervalTicks = 80 t or all pressed Drop!) → DROP (48 t: 12 rows × 4 t)
   → BALL_SCORE (40 t, standings) ; before ball B: UNDERDOG (20 t)
   ball B: drops, bins hidden → FINAL REVEAL → SETTLE → RESULT
```

### 7.3 Payout math

Lemma 1 (equal entries, i.i.d. paths, symmetric rules): EV = −rake/N. Mean points per ball (exact,
4096 paths): Low 9.65625, Medium 9.6572265625, High 9.669921875 (= 10 × solo RTP). On High, one edge
ball (1 in 2048 per ball) nearly always wins, which is the point: everyone holds their breath on the
last row.

### 7.4 UI

**Java** `PlinkoBattleScreen` 400 × 240: your board in the centre (180 × 180, balls animated along the
tape path), left column ranking (6 rows: name, total, last bin ×m), right column the other players'
last ball as a small bin strip (13 cells, the landed bin lit). Top bar "PLINKO BATTLE · High · Ball
2/3 · Pot 600". Final ball: all boards drop together, the last row runs at half speed (slow-mo 8 t).
Buttons [Drop!] [Taunt…] [Rules]. Set-up on the machine screen: entry `BetSelector`, risk
Low | Medium | High, balls 1 | 3 | 5.

### 7.5 Advancements, sounds, particles

`pvp_full_house` can be earned here (any mode). Sounds: `plinko_peg` per row, EDGE = firework
twinkle + bell. Particles: `end_rod` at the machine when a ball lands in an edge bin.

---

## 8. Scratch Showdown [pvp.scratch] — MUST

### 8.1 The showdown card

A dedicated PvP card (no item needed; it is virtual). 3 × 3 cells, **each cell i.i.d.** from:

| Symbol | Weight | Value | Effect |
|--------|--------|-------|--------|
| Coal | 30 | 1 | |
| Iron | 25 | 2 | |
| Gold | 18 | 3 | |
| Emerald | 12 | 5 | |
| Diamond | 6 | 10 | |
| Nether Star | 1 | 25 | |
| Creeper | 5 | — | **burns** the highest-value surviving cell revealed so far (it becomes charred, worth 0, no longer counts for trios); if none, fizzles |
| Rabbit's Foot | 3 | — | card multiplier ×2 (two feet ×4; a third does nothing) |
| **Total** | 100 | | |

**Score** = `(Σ surviving cell values, where every symbol with ≥ 3 surviving copies counts double)
× 2^min(feet, 2)`. Tie-break: highest single surviving cell value; then split.

Cells are revealed in a fixed order 1…9 (row-major) — the order matters for creepers, and it is the
same for everyone.

### 8.2 Rules

- 2 … `pvp.scratch.maxPlayers` (6). Created from the hub: *vs one player* (duel invite, §3.3.1) or
  *open lobby* (anchored at the host's position; players within `pvp.joinRadius` join from the hub).
- Entry E (≥ `pvp.minStake`, ≤ tier max). Everyone gets one card from the tape.
- The 9 cells are scratched **for everybody at the same time**, one cell per step: each step waits up
  to `pvp.scratch.revealIntervalTicks` (40 t) or until every online participant pressed *Scratch!*.
  After each step everyone sees everyone's cells and running scores.
- Step 9 is hidden and resolved by the Final Reveal (§3.11.4).

### 8.2a Bots

Bots can be the duel opponent or fill lobby seats; their card comes from the same draw. No
decisions in MUST (the fold/bluff layer is NICE Scratch Poker, §10.5 and §3.15.4).

### 8.3 State machine

```
INVITED│LOBBY → START (tape: seatOrder, cells[player][1..9]) → for c in 1..8: STEP_WAIT → REVEAL_CELL (20 t)
   → events (creeper burn / golden foot, 20 t) → STEP 9 hidden → FINAL REVEAL → SETTLE → RESULT
```

### 8.4 Payout math

Lemma 1: EV = −rake/N. Exact distribution (DP over the 8^9 sequences, §16.6): mean score 38.816,
s.d. 21.85, median 34, P(score = 0) = 3.2 × 10⁻⁵, max 1400 (7 stars + 2 feet), P(2 feet) = 0.02816,
P(two players tie before the tie-break) = 0.01997.

Worked card: Coal, Coal, Coal, Diamond, Creeper, Iron, Rabbit's Foot, Emerald, Gold → the Creeper
burns the Diamond; survivors Coal×3 (trio → 6), Iron 2, Emerald 5, Gold 3 = 16; one foot → **32**.

### 8.5 UI

**Java** `ScratchShowdownScreen` 400 × 240: up to 6 mini-cards (3 × 3 cells at 20 px, silver until
scratched) in a 3 × 2 grid, each with name, running score, feet multiplier badge "×2", ALL-IN tag.
Two players: two big cards (40 px cells). The current cell pulses on every card; scratching plays a
per-card silver-dust particle sprite (GUI only). Creeper: the burned cell cracks to black; foot: card
border turns gold. [Scratch!] [Taunt…] [Rules]. Top bar "SCRATCH SHOWDOWN · Cell 5/9 · Pot 400".

Glyphs: reuse the scratch-card glyph sheet for Coal/Iron/Gold/Emerald/Diamond/Star/Creeper; add one
glyph for Rabbit's Foot (U+E1A0) and one "charred" glyph (U+E1A1); unscratched = ▒.

### 8.6 Advancements, sounds, particles

`pvp_lucky_feet` (win with two Rabbit's Feet on your card). Sounds: `scratch` per step, creeper cell
= `random.fuse` / `entity.creeper.primed` then a soft explosion, foot = amethyst chime. Particles:
`wax_on` on a foot, `smoke` on a burn at the player.

---

## 9. Entertainment layer — NICE

### 9.1 Spectator side bets ("the tote")

- Available on **chance-only** matches (Coin Flip Duel single flips, Slot Showdown, Wheel Party,
  Plinko Battle, Scratch Showdown; never on §10 modes with decisions) when `pvp.side.enabled`.
- Who: any eligible player (§3.2 rules 1–3, 6–7) within `pvp.announceRadius` of the anchor who is
  **not a participant and not the owner of the anchor's casino**. Stake 1 … tier max per match.
- When: from lobby open / invite accept until START (the tape does not exist yet). For duels the
  window is the 3 s countdown extended by `pvp.side.windowTicks` (200) before the draw.
- Pari-mutuel: each bettor backs one participant. At SETTLE, pool Q, side rake
  `Rs = floor((Q × pvp.side.rakeBasisPoints + 5000) / 10000)` (default 500 bp), `Qw = Q − Rs`, divided
  among the winners' backers pro rata to their bets (split match: Qw split equally between the tied
  winners' backer groups; floor, odd chips to the largest backers first). If nobody backed a winner,
  every side bet is refunded and no side rake is taken. Side rake → same destination as the match
  rake.
- Displayed odds: "pays about ×(Qw / backing of that player)" updated live (Java) / at form open
  (Bedrock). For Wheel Party the lobby also shows each slice's true win chance, so sharp bettors can
  find value — that is the fun.
- Fairness: bettors play against each other; the house takes Rs only. Participants can't bet, and all
  eligible modes have no choices after START, so nobody can throw a match.
- UI: hub button *Side bets* (list of open matches in range) → Java: picker panel with odds; Bedrock:
  ActionForm of matches → ActionForm of participants with odds → ModalForm amount.

### 9.2 Tournaments

**Creation.** Operators: Java `/casino tournament create <mode> <knockout|leaderboard> <entry>
[maxPlayers] [name]` or Admin → PvP → Create tournament; optional **added prize**
(minted by the bank, operators only). Scheduled: `pvp.tournament.autoEveryDays` (0 = off) at
time-of-day `pvp.tournament.autoTimeOfDay`, with `autoMode` / `autoFormat` / `autoEntry`.
Announcement server-wide; registration lasts `pvp.tournament.registrationTicks` (2400 = 2 min).
Entry is escrowed at registration; withdrawing before the start refunds it. Fewer than
`pvp.tournament.minPlayers` (4) at the start → cancelled, refunded.

**Knockout** (modes: Coin Flip Duel single flip, Slot Showdown, Plinko Battle, Scratch Showdown, all
heads-up; up to `pvp.tournament.maxPlayers` 32). Bracket seeded by a random permutation; byes (to
the next power of two) go to random players. Each match is a normal PvP match with stake 0 (only the
bracket's pot matters) played at the anchor's tier/risk chosen at creation; matches of a round run in
parallel; the next round starts `pvp.tournament.roundGapTicks` (200) after the last match of the
previous one. Absent or offline players' matches play out from their tape (all chance).

**Leaderboard** (modes: Slot Showdown, Plinko Battle, Scratch Showdown). A window of
`pvp.tournament.windowTicks` (12000 = 10 min). Each entry buys one **run** (a solo scoring run: N
spins / B balls / one card, same scoring as the mode, Underdog Boost off, SWAP off); a player may buy
up to `pvp.tournament.maxRunsPerPlayer` (3) runs; best run counts; ties broken by the earlier run.

**Prizes.** Pool = Σ entries − rake + added prize. Split `pvp.tournament.prizeSplit` ([60, 30, 10]);
places paid = min(len(split), floor(players / 2)), shares renormalised; floor, odd chips to 1st.
Knockout 3rd place = both losing semi-finalists share the 3rd share.

**Fairness.** Knockout: symmetric chance matches + random seeding → every player has the same
distribution of finishing place → EV per player = pool/players − entry = −rake/players (+ added
prize/players). Leaderboard: runs are i.i.d. and each costs the same entry → every run is equally
likely to hold any rank → EV per run = −rake/runs. Buying more runs buys more chances at exactly fair
price.

**Rewards.** Champion gets a **Champion's Trophy** item (`pvp_trophy`, decorative, rarity epic, name
tooltip: tournament name · mode · world day) and `pvp_champion`. Results broadcast server-wide.

**UI.** Hub *Tournaments* → list (name · mode · format · entry · players/max · status) → details:
Java bracket view (up to 32 slots, zoomable 2 columns per page) / Bedrock ActionForm body "Round 2:
Alex vs Bob ✔ …" (paged 12 lines). Leaderboard: top 10 + your best and runs left.

### 9.3 Floating scoreboard

Above the anchor while a match runs: Java a vanilla **text display** entity (billboard, 3 lines:
mode · pot, leader · points, round); Bedrock a dummy entity `burmaldaholic:hologram` (invisible, no
collision, `nameTag` with `\n` avoided — three stacked entities). Removed at CLOSED and on load.

---

## 10. More PvP modes — NICE

### 10.1 Coin Series (best of 3 / 5)

Duel variant: stake S each, one escrow, flips until someone has `ceil(n/2)` wins; winner takes
2S − rake. "Match point for …" callout; each flip revealed with the 3 s countdown. Drawn: the whole
sequence of 5 flips at START (only the needed prefix is shown). Fair by Lemma 1. Double or nothing is
not offered after a series.

### 10.2 Jackpot Race

Slots, 2–6, same tier. Each round every remaining player pays a per-spin fee f into the pot
(escrowed up front for `pvp.race.maxSpins` (30) spins; unused fees refunded) and spins
automatically every `pvp.race.intervalTicks` (60). First player(s) whose spin shows the **target**
(`pvp.race.target`, default "three Diamonds or better on any line": Diamond, Seven, Wild, Star) win;
simultaneous hits split. If nobody hits in maxSpins, the highest Showdown total wins. Equal fees per
round, symmetric → fair. Drama: a spinning "heat meter" of near misses (two target symbols on a
line).

### 10.3 Wheel Heist

Equal entries, 2–6, anchored at a Wheel of Fortune, a special 24-segment heist wheel:
Loot +1 ×8, Loot +2 ×5, Loot +3 ×3, Loot +5 ×1, **Steal** ×3 (take up to 3 loot from a player you
choose; timeout = the current leader other than you, ties → earliest seat), **Bankrupt** ×2 (lose all
your loot), **Double** ×1 (double your loot), **Shield** ×1 (the next Steal or Bankrupt against you is
blocked). Three rounds; in each round players spin once in seat order (random). Most loot wins; tie →
more Shields left, then split. Symmetric choices + random seat order → fair ex ante between
equally-skilled players (zero-sum among players; house takes only the rake). No side bets.

### 10.4 Plinko Bumpers

Plinko Battle option: every player gets one **bumper** per match. During BALL_WAIT they may place it on
an opponent's next ball: that ball's first 3 rows ignore any step that moves it away from the centre
(bin 6). Applied as a deterministic transform of the pre-drawn path. Symmetric resources → fair; the
fun is choosing whom to hit (usually the High-risk leader). No side bets.

### 10.5 Scratch Poker

2–4 players, ante A. Each gets a hidden Showdown card (§8.1). Cells 1–4 are revealed privately →
fixed-limit betting round (bet/raise size A, max 3 raises; fold = lose what you put in) → cells 5–8 →
betting round (size 2A) → showdown reveals cell 9 and all cards; best score takes the pot minus rake
(rake only if ≥ 2 players reach the showdown, "no showdown, no drop"). Timeouts: check, else fold.
Disconnect: check/fold. Poker buttons reuse the `gui.burmaldaholic.poker.*` keys.

### 10.6 Dice Duel on the PvP core

Move §11.5 PvP Dice Duel onto this frame (invite, escrow, rivalry, taunts, rematch, Final Reveal),
keeping its own `extras.diceDuel.*` keys (rake 0 %, tie re-rolls). Its results then feed rivalry and
win streaks.

---

## 11. Advancements [advancements]

Java: new entries in the "Burmaldaholic" tab; Bedrock: Achievements page, same keys.

| Id | Parent | Condition | Frame | Priority |
|----|--------|-----------|-------|----------|
| `pvp_first_win` | `beginners_luck` | Win (or split-win) any PvP match | task | MUST |
| `pvp_all_in` | `pvp_first_win` | Win a PvP match in which you were ALL-IN (§3.11.5) | goal | MUST |
| `pvp_all_square` | `pvp_first_win` | Win a Double-or-nothing flip as the chain's loser (get back to all square) | task | MUST |
| `pvp_underdog` | `pvp_first_win` | Win a Wheel Party holding ≤ 10 % of the wheel | goal | MUST |
| `pvp_phoenix` | `pvp_first_win` | Win a Slot Showdown after a KABOOM halved your score in that match | goal | MUST |
| `pvp_lucky_feet` | `pvp_first_win` | Win a Scratch Showdown with two Rabbit's Feet on your card | goal | MUST |
| `pvp_full_house` | `pvp_first_win` | Win a match against 5 distinct human opponents | goal | MUST |
| `pvp_revenge` | `pvp_first_win` | Win a grudge match as the side on the losing run | goal | MUST |
| `pvp_rampage` | `pvp_first_win` | Reach a PvP win streak of 5 with ≥ 2 distinct opponents in it | challenge | MUST |
| `pvp_champion` | `pvp_rampage` | Win a tournament | challenge | NICE |
| `pvp_bookie` | `pvp_first_win` | Win a side bet whose final payout was ≥ ×5 | goal | NICE |

`pvp_revenge`, `pvp_rampage`, `pvp_full_house` and `pvp_champion` need human opponents; the others can be earned
against bots (§3.15.2). `pvp_full_house` needs 5 distinct **human** opponents (bots never count as
distinct opponents).

---

## 12. Sounds and particles

MUST uses **vanilla sound events** (no new audio; subtitles are vanilla's). Played to participants
(and to spectators where noted) at the player's position; all respect the per-player "Casino
sounds" setting.

| Moment | Java sound event | Volume / pitch |
|--------|------------------|----------------|
| Invite received / lobby opened | `item.goat_horn.sound.0` ("Ponder") | 0.6 / 1.0 |
| Countdown tick (3-2-1) | `block.note_block.hat` | 1.0 / 1.0, 1.2, 1.4 |
| Coin launch | `burmaldaholic:coin_flip` | 1.0 |
| Reels / balls / wheel / scratch | existing `slot_spin`, `plinko_peg`, `wheel_tick`, `scratch` | as solo |
| Drumroll (Final Reveal) | `block.note_block.basedrum` × 8 accelerating | 0.8 |
| Place reveal | `block.note_block.bell` | 0.7 / 0.8 → 1.6 |
| Winner | `burmaldaholic:win` + `entity.player.levelup` | 1.0 |
| Loser | `burmaldaholic:lose` | 0.8 |
| HOT symbol | `item.firecharge.use` | 0.5 |
| KABOOM | `entity.generic.explode` | 0.5 (sound only, no explosion) |
| SWAP | `entity.enderman.teleport` | 0.8 |
| TIME WARP | `block.bell.use` | 0.7 |
| Creeper cell | `entity.creeper.primed` | 0.6 |
| Rabbit's Foot | `block.amethyst_block.chime` | 1.0 |
| EDGE! / UNDERDOG! | `entity.firework_rocket.twinkle` | 1.0 (spectators too) |
| GRUDGE MATCH banner | `entity.ravager.roar` | 0.5 |
| Win-streak legendary | `item.totem.use` | 0.6 (radius) |
| Taunt friendly / cheeky | `entity.villager.yes` / `entity.villager.no` | 0.8 |

Particles (spawned server-side at world positions; ids to be verified in game on as
in architecture "open risks"):

| Moment | Java |
|--------|------|
| Winner burst | `minecraft:totem_of_undying` (30) |
| Winner ring | `minecraft:happy_villager` |
| Losers on place reveal | `minecraft:angry_villager` |
| Coin landing | `minecraft:crit` |
| HOT | `minecraft:flame` |
| KABOOM | `minecraft:explosion` (1) |
| SWAP | `minecraft:portal` |
| EDGE bin | `minecraft:end_rod` |
| Rabbit's Foot | `minecraft:wax_on` |
| Creeper burn | `minecraft:smoke` |

NICE: custom events `burmaldaholic:pvp_challenge`, `pvp_drumroll`, `pvp_victory` (aliases of vanilla
files at first, own audio later) with subtitles §15.16.

---

## 13. Config keys (`CONFIG.md` format; section `## pvp`)

Percent-like values are integers in **basis points** (1 bp = 0.01 %) to keep the rake integer-exact.

### 13.1 Core

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `pvp.enabled` | bool | true | — | Master switch for every `pvp` mode (Dice Duel PvP keeps `extras.diceDuel.pvpEnabled`). |
| `pvp.rakeBasisPoints` | int | 300 | 0–1000 | House cut of every pot, round half up (§3.4). 300 = 3 %. |
| `pvp.minStake` | int | 10 | 1–1 000 000 | Minimum stake/entry per player. |
| `pvp.joinRadius` | int | 16 | 2–128 | Max distance to the anchor/challenger to invite or join. |
| `pvp.announceRadius` | int | 32 | 0–256 | Spectator feed, taunts and result chat radius. 0 = participants only. |
| `pvp.announceServerWidePot` | long | 5000 | 0–10¹² | Pots ≥ this are announced server-wide. 0 = never. |
| `pvp.inviteTimeoutTicks` | int | 600 | 200–6000 | Time to answer a challenge. |
| `pvp.lobbyTimeoutTicks` | int | 1800 | 400–12 000 | Lobby auto-start (≥ 2 players) or cancel. |
| `pvp.decisionTimeoutTicks` | int | 300 | 300–2400 | Double-or-nothing offers/answers, rematch window. Min 15 s (UI.md §13). |
| `pvp.countdownTicks` | int | 60 | 0–200 | Pre-reveal countdown ("3… 2… 1…"). |
| `pvp.maxPendingInvites` | int | 1 | 1–5 | Outgoing invites per player. |
| `pvp.declineCooldownTicks` | int | 600 | 0–12 000 | After a decline, the same challenger can't re-invite the same target. |
| `pvp.historyTicks` | int | 6000 | 600–72 000 | How long settled matches are kept (rematch, admin list). |
| `pvp.affectsStreak` | bool | false | — | PvP results update the §14 Lucky/Unlucky streak. Keep off (§3.12). |
| `pvp.countsTowardVip` | bool | true | — | Stakes count as lifetime wagered (and the `wager` contract). |
| `pvp.streakAnnounce` | list<int> | [3, 5, 10] | 3 ascending values, 2–100 | PvP win-streak call-out thresholds (heating / rampage / legendary). |
| `pvp.grudgeLosses` | int | 3 | 2–20 | Losing run that makes the next 2-player meeting a grudge match. |
| `pvp.taunts.enabled` | bool | true | — | |
| `pvp.taunts.cooldownTicks` | int | 100 | 20–1200 | |
| `pvp.taunts.maxPerMatch` | int | 5 | 1–50 | |
| `pvp.allowOwnedMachines` | bool | true | — | PvP at machines linked to player casinos (rake → bankroll). |
| `pvp.bots.enabled` | bool | true | — | Bots may take PvP seats (policy semantics and bot economics: BOTS.md). |
| `pvp.bots.defaultPolicy` | enum(HUMANS_ONLY, BOTS_ONLY, MIXED) | MIXED | — | Pre-selected seating policy in lobby set-up (§3.15.1). |
| `pvp.bots.defaultDifficulty` | enum(EASY, NORMAL, HARD, MIXED) | MIXED | — | Pre-selected bot difficulty. |
| `pvp.bots.maxPerMatch` | int | 7 | 0–15 | Most bots in one match (0 = bots off in PvP). |

### 13.2 Modes

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `pvp.coin.enabled` | bool | true | — | Coin Flip Duel. |
| `pvp.coin.maxDoubles` | int | 4 | 0–10 | Double-or-nothing links after the first flip (0 = off). |
| `pvp.slots.enabled` | bool | true | — | Slot Showdown. |
| `pvp.slots.maxPlayers` | int | 6 | 2–6 | |
| `pvp.slots.spinChoices` | list<int> | [3, 5, 10] | 1–20 each, 1–4 entries | Spins-per-player options offered to the host; the middle one is the default. |
| `pvp.slots.linkRadius` | int | 8 | 0–32 | Same-tier machines within this radius of the anchor can join. 0 = anchor only. |
| `pvp.slots.spinIntervalTicks` | int | 100 | 40–400 | Max wait per round before auto-spin. |
| `pvp.slots.hotSymbol` | bool | true | — | |
| `pvp.slots.underdogBoost` | bool | true | — | |
| `pvp.slots.kaboom` | bool | true | — | Off: three Creepers/TNT score 0 without halving. |
| `pvp.slots.pearlSwap` | bool | true | — | Off: three Pearls score 10 only. |
| `pvp.slots.starPoints` | int | 500 | 0–100 000 | Points for three Nether Stars. |
| `pvp.wheel.enabled` | bool | true | — | Wheel Party. |
| `pvp.wheel.maxPlayers` | int | 8 | 2–16 | |
| `pvp.wheel.countdownTicks` | int | 600 | 300–2400 | Starts when the 2nd player joins. |
| `pvp.wheel.noMoreBetsTicks` | int | 60 | 20–200 | |
| `pvp.wheel.underdogShareBasisPoints` | int | 1000 | 1–5000 | Winner share ≤ this = UNDERDOG (10 %). |
| `pvp.plinko.enabled` | bool | true | — | Plinko Battle. |
| `pvp.plinko.maxPlayers` | int | 6 | 2–6 | |
| `pvp.plinko.ballChoices` | list<int> | [1, 3, 5] | 1–10 each, 1–4 entries | Middle = default. |
| `pvp.plinko.linkRadius` | int | 8 | 0–32 | |
| `pvp.plinko.roundIntervalTicks` | int | 80 | 40–400 | |
| `pvp.plinko.underdogBoost` | bool | true | — | |
| `pvp.scratch.enabled` | bool | true | — | Scratch Showdown. |
| `pvp.scratch.maxPlayers` | int | 6 | 2–6 | |
| `pvp.scratch.revealIntervalTicks` | int | 40 | 20–200 | Max wait per cell. |
| `pvp.scratch.weights` | map<symbol,int> | coal 30, iron 25, gold 18, emerald 12, diamond 6, star 1, creeper 5, foot 3 | 0–1000 each, sum ≥ 1 | Cell weights (§8.1). |
| `pvp.scratch.values` | map<symbol,int> | coal 1, iron 2, gold 3, emerald 5, diamond 10, star 25 | 0–1000 | |

### 13.3 NICE

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `pvp.side.enabled` | bool | false | — | Spectator side bets (§9.1). |
| `pvp.side.rakeBasisPoints` | int | 500 | 0–2000 | |
| `pvp.side.windowTicks` | int | 200 | 60–1200 | Extra betting window before a duel's draw. |
| `pvp.tournament.enabled` | bool | false | — | |
| `pvp.tournament.minPlayers` | int | 4 | 2–64 | |
| `pvp.tournament.maxPlayers` | int | 32 | 4–64 | |
| `pvp.tournament.registrationTicks` | int | 2400 | 600–72 000 | |
| `pvp.tournament.roundGapTicks` | int | 200 | 60–2400 | |
| `pvp.tournament.windowTicks` | int | 12000 | 1200–240 000 | Leaderboard duration. |
| `pvp.tournament.maxRunsPerPlayer` | int | 3 | 1–20 | |
| `pvp.tournament.prizeSplit` | list<int> | [60, 30, 10] | 1–8 entries, sum 100 | Percent of the pool per place. |
| `pvp.tournament.autoEveryDays` | int | 0 | 0–30 | 0 = no scheduled tournaments. |
| `pvp.tournament.autoTimeOfDay` | int | 13000 | 0–23 999 | |
| `pvp.tournament.autoMode` | enum(coin, slots, plinko, scratch) | slots | — | |
| `pvp.tournament.autoFormat` | enum(knockout, leaderboard) | leaderboard | — | |
| `pvp.tournament.autoEntry` | int | 100 | 1–10⁶ | |
| `pvp.race.maxSpins` | int | 30 | 5–200 | |
| `pvp.race.intervalTicks` | int | 60 | 20–400 | |
| `pvp.heist.rounds` | int | 3 | 1–10 | |
| `pvp.plinko.bumpers` | bool | false | — | |
| `pvp.scratchPoker.enabled` | bool | false | — | |
| `pvp.series.enabled` | bool | false | — | Coin Series best of 3/5. |

Validation: `pvp.slots.spinChoices` / `ballChoices` sorted and de-duplicated on load; if
`pvp.countsTowardVip` and `pvp.rakeBasisPoints` < 200 → warning (§3.12).

---

## 14. Engine notes and open risks

- **Java offline credit** for settle-on-load uses the same offline deposit path as §4.1 play-outs.
- **Java text display** (NICE hologram) is vanilla since 1.19.4; the Bedrock hologram needs a new
  dummy entity (NICE, not in MUST).
- **Chunk unloads** never matter after START (the tape does not need the anchor).
- **Particle ids** in §12 need an in-game check (ids drift between versions).

---

## 15. Strings (`STRINGS.md` format — merge as `## pvp`)

Format rules are those of `STRINGS.md` (rows `| \`key\` | EN | RU |`, no `|` or line breaks in values,
positional placeholders, `(plural)` groups of four). Argument notes use the STRINGS.md notation:
`chips` = nested `unit.burmaldaholic.chip` (nominative), `chips_acc` = `unit.burmaldaholic.chip_acc`,
`points` = nested `unit.burmaldaholic.point`, `wins` = `unit.burmaldaholic.win`, `matches` =
`unit.burmaldaholic.match`, `blocks` = `unit.burmaldaholic.block`, `sec_acc` =
`unit.burmaldaholic.second_acc`, `num` = plain formatted number, `pct` = nested
`gui.burmaldaholic.pvp.percent`, `name` = player name, `game` = nested `gui.burmaldaholic.pvp.game.*`,
`symbol` = nested symbol name, `side` = nested `gui.burmaldaholic.extras.coin.heads/tails`.

Russian rules applied throughout (LOCALIZATION.md §7): no player name as the subject of a past-tense
verb (present tense or «игрок %1$s»), «вы» in UI, «ты» only in taunts (player-to-player banter), «ё»
everywhere, « » for game/mode names in running text. Button labels are ≤ 24 RU characters (longest:
«Начать «Колесо на всех»» 23, «Начать битву автоматов» 22, «Всё или ничего — Решка» 22,
«Отказаться от участия» 21); labels with names/numbers may wrap to 2 lines (UI.md §0.1).

### 15.0 Glossary additions (→ LOCALIZATION.md §6.7)

| EN | RU | Notes |
|----|----|-------|
| PvP / player vs player | PvP / игрок против игрока | keep "PvP" in Latin in UI |
| Match | матч | |
| Duel | дуэль | |
| Challenge (noun / verb) | вызов / бросить вызов | |
| Lobby | лобби | |
| Host | организатор | |
| Entry (fee) | взнос | |
| Pot | банк | as in poker |
| House cut (rake) | комиссия заведения | UI; «рейк» stays poker-only |
| Rematch | реванш | |
| Head-to-head | личные встречи | sports term |
| Nemesis | заклятый соперник | |
| Grudge match | «дело принципа» | |
| Taunt | подколка / подколоть | |
| Double or nothing | «всё или ничего» | the Russian idiom; not «удвоить или ничего» |
| All square | квиты | |
| All-in | ва-банк | not «олл-ин» here (that is the poker button) |
| Dead heat (tie) | ноздря в ноздрю | only in results; rules say «ничья» |
| Underdog | тёмная лошадка / аутсайдер | «Фора аутсайдеру» for the boost |
| Showdown (slots) | битва | «Битва автоматов» |
| Hot symbol | горячий символ | |
| KABOOM | БАБАХ | |
| Swap (Ender Pearl) | рокировка | |
| Time Warp | — | shown as the effect («двойные очки»), no name |
| Rabbit's Foot | кроличья лапка | vanilla item name |
| Slice (wheel) | кусок | |
| Side bets | ставки зрителей | |
| Tournament / bracket / knockout / leaderboard | турнир / сетка / на вылет / таблица лидеров | |
| Trophy | кубок | |
| Loot / Steal / Bankrupt / Shield | добыча / кража / банкрот / щит | NICE Wheel Heist |
| Bumper | отбойник | NICE Plinko Bumpers |

### 15.1 Units (plural) — add to §core Units

| Key | EN | RU |
|-----|----|----|
| `unit.burmaldaholic.point.p1` | %1$s point | %1$s очко |
| `unit.burmaldaholic.point.p21` | %1$s points | %1$s очко |
| `unit.burmaldaholic.point.p2` | %1$s points | %1$s очка |
| `unit.burmaldaholic.point.p5` | %1$s points | %1$s очков |
| `unit.burmaldaholic.win.p1` | %1$s win | %1$s победа |
| `unit.burmaldaholic.win.p21` | %1$s wins | %1$s победа |
| `unit.burmaldaholic.win.p2` | %1$s wins | %1$s победы |
| `unit.burmaldaholic.win.p5` | %1$s wins | %1$s побед |
| `unit.burmaldaholic.match.p1` | %1$s match | %1$s матч |
| `unit.burmaldaholic.match.p21` | %1$s matches | %1$s матч |
| `unit.burmaldaholic.match.p2` | %1$s matches | %1$s матча |
| `unit.burmaldaholic.match.p5` | %1$s matches | %1$s матчей |
| `unit.burmaldaholic.ball.p1` | %1$s ball | %1$s шарик |
| `unit.burmaldaholic.ball.p21` | %1$s balls | %1$s шарик |
| `unit.burmaldaholic.ball.p2` | %1$s balls | %1$s шарика |
| `unit.burmaldaholic.ball.p5` | %1$s balls | %1$s шариков |

### 15.2 Hub, new match, invites, lobby, match, results, rematch, errors

Args: `hub.record` %1$s num wins, %2$s num losses, %3$s signed num · `hub.nemesis` %1$s name, %2$s–%3$s
num · `hub.accept` %1$s name, %2$s game · `hub.lobby_row` %1$s game, %2$s chips, %3$s/%4$s num ·
`invite.sent` %1$s name, %2$s game, %3$s chips_acc · `invite.received` %1$s name, %2$s game, %3$s
chips_acc · `invite.body` %1$s name, %2$s game, %3$s chips · `invite.side` %1$s side ·
`invite.expires` %1$s sec_acc · `lobby.*` chips / num / sec_acc as named · `lobby.opened` %1$s name,
%2$s game, %3$s block name, %4$s chips · `lobby.join_confirm` %1$s chips, %2$s nested
`slots.spins_value`/`plinko.balls_value`/empty, %3$s chips · `match.bar` %1$s/%2$s round num, %3$s
points (num), %4$s rank num, %5$s leader name, %6$s leader points (num) · `match.bar_leader` %4$s num
lead · `match.row`/`match.place` %1$s num, %2$s name, %3$s points · `result.*` chips · 
`result.broadcast` %1$s game, %2$s name, %3$s chips_acc, %4$s num · `result.broadcast_split` %2$s
joined names, %3$s chips_acc · `result.offline`/`casino_off` %1$s game, %2$s nested result line.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.hub.title` | PvP Arena | Арена PvP |
| `gui.burmaldaholic.pvp.hub.record` | Your record: %1$s–%2$s · net %3$s | Ваш счёт: %1$s–%2$s · итог %3$s |
| `gui.burmaldaholic.pvp.hub.nemesis` | Nemesis: %1$s (%2$s–%3$s) | Заклятый соперник: %1$s (%2$s–%3$s) |
| `gui.burmaldaholic.pvp.hub.no_record` | No PvP matches yet | PvP-матчей пока не было |
| `gui.burmaldaholic.pvp.hub.pending` | Pending | Ждут ответа |
| `gui.burmaldaholic.pvp.hub.accept` | Accept: %1$s · %2$s | Принять: %1$s · %2$s |
| `gui.burmaldaholic.pvp.hub.decline` | Decline: %1$s | Отказать: %1$s |
| `gui.burmaldaholic.pvp.hub.nearby` | Open lobbies nearby (%1$s) | Лобби поблизости (%1$s) |
| `gui.burmaldaholic.pvp.hub.no_lobbies` | No open lobbies nearby | Поблизости нет открытых лобби |
| `gui.burmaldaholic.pvp.hub.lobby_row` | %1$s · %2$s · %3$s/%4$s | %1$s · %2$s · %3$s/%4$s |
| `gui.burmaldaholic.pvp.hub.join` | Join | Войти |
| `gui.burmaldaholic.pvp.hub.new_match` | New match… | Новый матч… |
| `gui.burmaldaholic.pvp.hub.rivals` | Head-to-head | Личные встречи |
| `gui.burmaldaholic.pvp.hub.machine_hint` | Slot Showdown, Plinko Battle and Wheel Party start at their machines | Битва автоматов, битва Плинко и «Колесо на всех» начинаются у самих автоматов |
| `gui.burmaldaholic.pvp.new.title` | New match | Новый матч |
| `gui.burmaldaholic.pvp.new.opponent` | Opponent | Соперник |
| `gui.burmaldaholic.pvp.new.open_lobby` | Open lobby (anyone nearby) | Открытое лобби (все, кто рядом) |
| `gui.burmaldaholic.pvp.new.stake` | Stake per player | Ставка с каждого |
| `gui.burmaldaholic.pvp.new.side` | Your side | Ваша сторона |
| `gui.burmaldaholic.pvp.new.submit` | Throw down the gauntlet | Бросить вызов |
| `gui.burmaldaholic.pvp.new.no_players` | Nobody is close enough — open a lobby instead | Рядом никого — лучше откройте лобби |
| `gui.burmaldaholic.pvp.game.coin` | Coin Flip Duel | Дуэль на монетке |
| `gui.burmaldaholic.pvp.game.slots` | Slot Showdown | Битва автоматов |
| `gui.burmaldaholic.pvp.game.wheel` | Wheel Party | Колесо на всех |
| `gui.burmaldaholic.pvp.game.plinko` | Plinko Battle | Битва Плинко |
| `gui.burmaldaholic.pvp.game.scratch` | Scratch Showdown | Лотерейная битва |
| `msg.burmaldaholic.pvp.invite.sent` | Challenge sent to %1$s: %2$s for %3$s | Вызов отправлен игроку %1$s: %2$s на %3$s |
| `msg.burmaldaholic.pvp.invite.received` | %1$s challenges you: %2$s for %3$s! | %1$s бросает вам вызов: %2$s на %3$s! |
| `gui.burmaldaholic.pvp.invite.accept_button` | [Accept] | [Принять] |
| `gui.burmaldaholic.pvp.invite.decline_button` | [Decline] | [Отказать] |
| `gui.burmaldaholic.pvp.invite.title` | Challenge! | Вызов! |
| `gui.burmaldaholic.pvp.invite.body` | %1$s challenges you: %2$s. Stake: %3$s each. | %1$s бросает вам вызов: %2$s. Ставка: %3$s с каждого. |
| `gui.burmaldaholic.pvp.invite.side` | You'd play %1$s | Ваша сторона: %1$s |
| `gui.burmaldaholic.pvp.invite.record` | Head-to-head vs %1$s: %2$s–%3$s | Личные встречи (%1$s): %2$s–%3$s |
| `gui.burmaldaholic.pvp.invite.record_none` | You've never played %1$s | С игроком %1$s вы ещё не играли |
| `gui.burmaldaholic.pvp.invite.expires` | Expires in %1$s | Истекает через %1$s |
| `gui.burmaldaholic.pvp.invite.accept` | Accept | Принять |
| `gui.burmaldaholic.pvp.invite.decline` | Decline | Отказаться |
| `msg.burmaldaholic.pvp.invite.accepted` | %1$s accepts! Stakes are in. | %1$s принимает вызов! Ставки сделаны. |
| `msg.burmaldaholic.pvp.invite.declined` | %1$s declines. Maybe next time. | %1$s отказывается. Может, в другой раз. |
| `msg.burmaldaholic.pvp.invite.expired` | No answer from %1$s — the challenge expired | %1$s не отвечает — вызов сгорел |
| `msg.burmaldaholic.pvp.invite.withdrawn` | The challenge from %1$s was withdrawn | Вызов от игрока %1$s отозван |
| `msg.burmaldaholic.pvp.invite.failed` | The stakes couldn't be collected — challenge cancelled | Собрать ставки не удалось — вызов отменён |
| `gui.burmaldaholic.pvp.lobby.title` | %1$s — lobby | %1$s — лобби |
| `gui.burmaldaholic.pvp.lobby.entry` | Entry: %1$s | Взнос: %1$s |
| `gui.burmaldaholic.pvp.lobby.pot` | Pot: %1$s | Банк: %1$s |
| `gui.burmaldaholic.pvp.lobby.rake` | House cut: %1$s | Комиссия заведения: %1$s |
| `gui.burmaldaholic.pvp.percent` | %1$s%% | %1$s %% |
| `gui.burmaldaholic.pvp.lobby.players` | Players: %1$s/%2$s | Игроки: %1$s/%2$s |
| `gui.burmaldaholic.pvp.lobby.host` | %1$s (host) | %1$s (организатор) |
| `gui.burmaldaholic.pvp.lobby.record` | Head-to-head %1$s–%2$s | Личные встречи %1$s–%2$s |
| `gui.burmaldaholic.pvp.lobby.starts_in` | Starts in %1$s | Старт через %1$s |
| `gui.burmaldaholic.pvp.lobby.need_more` | Waiting for at least one more player | Ждём ещё хотя бы одного игрока |
| `gui.burmaldaholic.pvp.lobby.start` | Start now | Начать |
| `gui.burmaldaholic.pvp.lobby.join` | Join (%1$s) | Войти (%1$s) |
| `gui.burmaldaholic.pvp.lobby.join_confirm` | Entry %1$s · %2$s · Pot %3$s | Взнос %1$s · %2$s · банк %3$s |
| `gui.burmaldaholic.pvp.lobby.leave` | Leave lobby | Выйти из лобби |
| `gui.burmaldaholic.pvp.lobby.open` | Open lobby | Открыть лобби |
| `gui.burmaldaholic.pvp.lobby.waiting_bar` | %1$s · %2$s/%3$s · starts in %4$s | %1$s · %2$s/%3$s · старт через %4$s |
| `msg.burmaldaholic.pvp.lobby.opened` | %1$s opens %2$s at a %3$s — entry %4$s. Use a machine nearby to join! | %1$s открывает «%2$s» (%3$s), взнос %4$s. Подходите к автомату рядом! |
| `msg.burmaldaholic.pvp.lobby.opened_here` | %1$s opens %2$s nearby — entry %3$s. Join from the Casino Menu! | %1$s открывает «%2$s» неподалёку, взнос %3$s. Вход — через меню казино! |
| `msg.burmaldaholic.pvp.lobby.joined` | %1$s joins (%2$s/%3$s) | %1$s в игре (%2$s/%3$s) |
| `msg.burmaldaholic.pvp.lobby.left` | %1$s leaves the lobby | %1$s выходит из лобби |
| `msg.burmaldaholic.pvp.lobby.new_host` | %1$s is the host now | Теперь организатор — %1$s |
| `msg.burmaldaholic.pvp.lobby.cancelled` | Not enough players — lobby closed | Не набралось игроков — лобби закрыто |
| `msg.burmaldaholic.pvp.lobby.refunded` | Your entry of %1$s was returned | Ваш взнос возвращён: %1$s |
| `msg.burmaldaholic.pvp.match.start` | %1$s begins! Pot: %2$s | «%1$s» начинается! Банк: %2$s |
| `msg.burmaldaholic.pvp.match.all_in` | %1$s goes ALL-IN! | %1$s идёт ва-банк! |
| `gui.burmaldaholic.pvp.all_in_tag` | ALL-IN | ВА-БАНК |
| `gui.burmaldaholic.pvp.match.go` | Go! | Поехали! |
| `gui.burmaldaholic.pvp.match.bar` | %1$s/%2$s · You %3$s (#%4$s) · %5$s %6$s | %1$s/%2$s · Вы %3$s (№%4$s) · %5$s %6$s |
| `gui.burmaldaholic.pvp.match.bar_leader` | %1$s/%2$s · You %3$s (#1) · lead %4$s | %1$s/%2$s · Вы %3$s (№1) · отрыв %4$s |
| `gui.burmaldaholic.pvp.match.standings` | Standings | Таблица |
| `gui.burmaldaholic.pvp.match.row` | #%1$s %2$s — %3$s | №%1$s %2$s — %3$s |
| `gui.burmaldaholic.pvp.match.hidden` | ??? | ??? |
| `gui.burmaldaholic.pvp.match.final` | Final results… | Итоги… |
| `gui.burmaldaholic.pvp.match.place` | #%1$s: %2$s — %3$s | №%1$s: %2$s — %3$s |
| `gui.burmaldaholic.pvp.result.title` | Result | Итог |
| `gui.burmaldaholic.pvp.result.winner_title` | %1$s WINS! | ПОБЕДА: %1$s! |
| `gui.burmaldaholic.pvp.result.dead_heat_title` | DEAD HEAT! | НОЗДРЯ В НОЗДРЮ! |
| `gui.burmaldaholic.pvp.result.you_win` | You take the pot: +%1$s | Банк ваш: +%1$s |
| `gui.burmaldaholic.pvp.result.you_lose` | Not this time: −%1$s | Не в этот раз: −%1$s |
| `gui.burmaldaholic.pvp.result.split` | The pot is split: +%1$s for you | Банк поделён: вам +%1$s |
| `gui.burmaldaholic.pvp.result.pot_line` | Pot %1$s · house cut %2$s · paid out %3$s | Банк %1$s · комиссия %2$s · выплачено %3$s |
| `msg.burmaldaholic.pvp.result.broadcast` | %1$s: %2$s takes %3$s! Opponents: %4$s | %1$s: %2$s забирает %3$s! Соперников: %4$s |
| `msg.burmaldaholic.pvp.result.broadcast_split` | %1$s: dead heat — %2$s split %3$s | %1$s: ноздря в ноздрю — %2$s делят %3$s |
| `msg.burmaldaholic.pvp.result.offline` | While you were away, your %1$s match was played out: %2$s | Пока вас не было, матч «%1$s» доигран: %2$s |
| `msg.burmaldaholic.pvp.result.away` | You left, but the match plays on — the result is already decided | Вы ушли, но матч продолжается — исход уже решён |
| `msg.burmaldaholic.pvp.result.casino_off` | Casino mode was switched off — your %1$s match was settled at once: %2$s | Режим казино выключен — матч «%1$s» рассчитан сразу: %2$s |
| `gui.burmaldaholic.pvp.rematch` | Rematch | Реванш |
| `gui.burmaldaholic.pvp.rematch.waiting` | Rematch? Ready: %1$s/%2$s | Реванш? Готовы: %1$s/%2$s |
| `msg.burmaldaholic.pvp.rematch.requested` | %1$s wants a rematch! | %1$s требует реванша! |
| `msg.burmaldaholic.pvp.rematch.start` | Rematch! Same stakes, fresh luck. | Реванш! Ставки те же, удача новая. |
| `msg.burmaldaholic.pvp.rematch.expired` | No rematch this time | Реванша не будет |
| `msg.burmaldaholic.pvp.rematch.dropped` | %1$s can't cover the rematch and sits this one out | %1$s не тянет реванш и пропускает эту партию |
| `gui.burmaldaholic.pvp.error.debt` | No PvP while you owe the Loan Shark | Пока вы должны Ростовщику, PvP недоступно |
| `gui.burmaldaholic.pvp.error.target_unavailable` | %1$s can't play PvP right now | %1$s сейчас не может играть в PvP |
| `gui.burmaldaholic.pvp.error.spectator` | Not in Spectator mode | В режиме наблюдателя нельзя |
| `gui.burmaldaholic.pvp.error.self` | You can't challenge yourself | Вызвать самого себя нельзя |
| `gui.burmaldaholic.pvp.error.busy_target` | %1$s is in another game right now | %1$s сейчас в другой игре |
| `gui.burmaldaholic.pvp.error.pending` | You already have a challenge waiting for an answer | У вас уже есть вызов без ответа |
| `gui.burmaldaholic.pvp.error.too_far` | %1$s is too far away (max %2$s) | %1$s слишком далеко (макс. %2$s) |
| `gui.burmaldaholic.pvp.error.too_far_anchor` | You're too far from the host's machine (max %1$s) | Вы слишком далеко от автомата организатора (макс. %1$s) |
| `gui.burmaldaholic.pvp.error.other_dimension` | %1$s is in another dimension | %1$s в другом измерении |
| `gui.burmaldaholic.pvp.error.no_invites` | %1$s isn't taking challenges right now | %1$s сейчас не принимает вызовы |
| `gui.burmaldaholic.pvp.error.cooldown_target` | %1$s just said no. Give them a moment. | Игрок %1$s только что отказал. Дайте ему минутку. |
| `gui.burmaldaholic.pvp.error.cant_afford_target` | %1$s can't cover that stake | Игроку %1$s не хватает на такую ставку |
| `gui.burmaldaholic.pvp.error.over_target_max` | That's over %1$s's VIP limit (%2$s) | Это выше ВИП-лимита игрока %1$s (%2$s) |
| `gui.burmaldaholic.pvp.error.stake_min` | The minimum PvP stake is %1$s | Минимальная ставка в PvP — %1$s |
| `gui.burmaldaholic.pvp.error.lobby_full` | The lobby is full | В лобби нет мест |
| `gui.burmaldaholic.pvp.error.lobby_started` | The match has already started | Матч уже начался |
| `gui.burmaldaholic.pvp.error.lobby_gone` | That lobby is closed | Это лобби уже закрыто |
| `gui.burmaldaholic.pvp.error.wrong_machine` | Join from a %1$s near the host's machine (radius: %2$s) | Войти можно только с автомата «%1$s» рядом с организатором (радиус: %2$s) |
| `gui.burmaldaholic.pvp.error.owner` | Owners can't play PvP at their own machines | Владелец не может играть в PvP на своих автоматах |
| `gui.burmaldaholic.pvp.error.needs_opponent` | A duel needs an opponent — pick a player | Для дуэли нужен соперник — выберите игрока |
| `gui.burmaldaholic.pvp.error.over_cap` | The cap here is %1$s per player | Здесь не больше %1$s с игрока |
| `gui.burmaldaholic.pvp.error.no_more_bets` | No more bets — the wheel is about to spin | Ставок больше нет — колесо вот-вот закрутится |
| `gui.burmaldaholic.pvp.settings.invites` | Accept PvP challenges | Принимать вызовы игроков |
| `gui.burmaldaholic.pvp.admin.matches` | PvP matches | PvP-матчи |
| `gui.burmaldaholic.pvp.admin.match_row` | %1$s · %2$s · %3$s · pot %4$s | %1$s · %2$s · %3$s · банк %4$s |
| `gui.burmaldaholic.pvp.admin.cancel` | Cancel / settle now | Отменить / рассчитать |
| `gui.burmaldaholic.pvp.admin.state.lobby` | lobby | лобби |
| `gui.burmaldaholic.pvp.admin.state.drawn` | in play | идёт игра |
| `gui.burmaldaholic.pvp.admin.state.settled` | settled | рассчитан |
| `gui.burmaldaholic.pvp.admin.rake_warning` | Warning: the PvP house cut (%1$s) is below the cheapest house game — VIP can be farmed cheaply | Внимание: комиссия PvP (%1$s) ниже преимущества самой дешёвой игры заведения — ВИП можно накрутить задёшево |
| `gui.burmaldaholic.pvp.charter.rake` | PvP house cut collected: %1$s | Комиссия с PvP-матчей: %1$s |
| `tooltip.burmaldaholic.lucky_coin.pvp` | Use on a player: challenge them to a Coin Flip Duel | На игрока — вызов на дуэль на монетке |

### 15.3 Rivalry, win streaks, grudge matches

Args: `rivals.row` %1$s name, %2$s–%3$s num, %4$s signed num · `streak.*` %1$s name, %2$s wins ·
`streak.broken` %1$s breaker name, %2$s name, %3$s wins · `grudge.subtitle` %1$s name, %2$s matches ·
`grudge.revenge` %1$s winner name, %2$s loser name.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.rivals.title` | Head-to-head | Личные встречи |
| `gui.burmaldaholic.pvp.rivals.row` | %1$s: %2$s–%3$s · net %4$s | %1$s: %2$s–%3$s · итог %4$s |
| `gui.burmaldaholic.pvp.rivals.none` | No rivals yet. Time to make some. | Соперников пока нет. Самое время завести. |
| `msg.burmaldaholic.pvp.streak.heating` | %1$s is heating up: %2$s in a row! | %1$s разогревается: %2$s подряд! |
| `msg.burmaldaholic.pvp.streak.rampage` | RAMPAGE! %1$s — %2$s in a row. Somebody stop them! | РАЗНОС! %1$s — %2$s подряд. Остановите это кто-нибудь! |
| `msg.burmaldaholic.pvp.streak.legendary` | LEGENDARY! %1$s — %2$s in a row! | ЛЕГЕНДА! %1$s — %2$s подряд! |
| `msg.burmaldaholic.pvp.streak.broken` | %1$s ends %2$s's run of %3$s! | %1$s прерывает серию игрока %2$s: %3$s подряд! |
| `gui.burmaldaholic.pvp.grudge.title` | GRUDGE MATCH | ДЕЛО ПРИНЦИПА |
| `gui.burmaldaholic.pvp.grudge.subtitle` | %1$s won the last %2$s | Последние %2$s — за игроком %1$s |
| `msg.burmaldaholic.pvp.grudge.revenge` | Sweet revenge! %1$s finally beats %2$s | Сладкая месть! %1$s наконец обыгрывает соперника — %2$s |

### 15.4 Taunts

Args: `taunt.say` %1$s name, %2$s nested `gui.burmaldaholic.pvp.taunt.<id>`. Friendly (villager
"yes" sound): gg, luck, respect, steel; cheeky ("no" sound): wow, rigged, again, bye.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.taunt.button` | Taunt… | Подколоть… |
| `gui.burmaldaholic.pvp.taunt.title` | Say something | Сказать пару слов |
| `msg.burmaldaholic.pvp.taunt.say` | %1$s: “%2$s” | %1$s: «%2$s» |
| `gui.burmaldaholic.pvp.taunt.limit` | That's enough talking for one match | Для одного матча слов достаточно |
| `gui.burmaldaholic.pvp.taunt.gg` | GG, well played | GG, красиво сыграно |
| `gui.burmaldaholic.pvp.taunt.luck` | Good luck. You'll need it | Удачи. Пригодится |
| `gui.burmaldaholic.pvp.taunt.wow` | No way! | Да ладно?! |
| `gui.burmaldaholic.pvp.taunt.rigged` | It's rigged! | Да тут подкрутка! |
| `gui.burmaldaholic.pvp.taunt.again` | Again. Right now. | Ещё раз. Сейчас же. |
| `gui.burmaldaholic.pvp.taunt.steel` | Nerves of steel | Нервы — стальные |
| `gui.burmaldaholic.pvp.taunt.bye` | Say bye to your chips | Прощайся с фишками |
| `gui.burmaldaholic.pvp.taunt.respect` | Respect | Моё почтение |

### 15.5 Coin Flip Duel

Args: `coin.vs` names · `coin.sides` %1$s name, %2$s side, %3$s name, %4$s side · `coin.landed` %1$s
side · `coin.takes` %1$s name, %2$s chips_acc · `coin.chain` %1$s name, %2$s chips ·
`coin.don_explain` %1$s chips, %2$s chips · `coin.don_request` %1$s name, %2$s chips, %3$s side,
%4$s chips · `coin.don_unaffordable` %1$s chips · `coin.don_called` %1$s num, %2$s chips ·
`coin.setup_title` %1$s name · `coin.spectate` %1$s name, %2$s name, %3$s chips.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.coin.title` | Coin Flip Duel | Дуэль на монетке |
| `gui.burmaldaholic.pvp.coin.setup_title` | Coin Flip Duel vs %1$s | Дуэль на монетке: %1$s |
| `gui.burmaldaholic.pvp.coin.vs` | %1$s vs %2$s | %1$s против %2$s |
| `gui.burmaldaholic.pvp.coin.sides` | %1$s: %2$s · %3$s: %4$s | %1$s: %2$s · %3$s: %4$s |
| `gui.burmaldaholic.pvp.coin.landed` | %1$s! | %1$s! |
| `gui.burmaldaholic.pvp.coin.takes` | %1$s takes %2$s | %1$s забирает %2$s |
| `gui.burmaldaholic.pvp.coin.chain` | Chain: %1$s down %2$s | Цепочка: %1$s в минусе на %2$s |
| `gui.burmaldaholic.pvp.coin.don_heads` | Double or nothing — Heads | Всё или ничего — Орёл |
| `gui.burmaldaholic.pvp.coin.don_tails` | Double or nothing — Tails | Всё или ничего — Решка |
| `gui.burmaldaholic.pvp.coin.walk_away` | Walk away | Уйти |
| `gui.burmaldaholic.pvp.coin.don_explain` | Stake %1$s each. Win it and you're all square; lose it and you're down %2$s. | Ставка с каждого: %1$s. Выиграете — будете квиты, проиграете — минус %2$s. |
| `gui.burmaldaholic.pvp.coin.don_request_title` | Double or nothing? | Всё или ничего? |
| `gui.burmaldaholic.pvp.coin.don_request` | %1$s wants double or nothing: %2$s each, calling %3$s. You're up %4$s. | %1$s предлагает «всё или ничего»: с каждого %2$s, выбор — %3$s. Вы в плюсе на %4$s. |
| `gui.burmaldaholic.pvp.coin.let_it_ride` | Let it ride | Рискнуть |
| `gui.burmaldaholic.pvp.coin.take_money` | Take the money | Забрать выигрыш |
| `gui.burmaldaholic.pvp.coin.don_limit` | The chain is at its limit — no more doubling | Цепочка на пределе — удваивать больше нельзя |
| `gui.burmaldaholic.pvp.coin.don_unaffordable` | Double or nothing needs %1$s from each of you | Для «всё или ничего» нужно %1$s с каждого |
| `msg.burmaldaholic.pvp.coin.don_called` | Double or nothing #%1$s: %2$s on the line! | «Всё или ничего» №%1$s: на кону %2$s! |
| `msg.burmaldaholic.pvp.coin.all_square` | ALL SQUARE! %1$s and %2$s are even again (minus the house's cut) | КВИТЫ! %1$s и %2$s снова при своих (за вычетом комиссии) |
| `msg.burmaldaholic.pvp.coin.cashed_out` | %1$s takes the money and walks | %1$s забирает выигрыш и уходит |
| `msg.burmaldaholic.pvp.coin.walked_away` | %1$s walks away. Wise, or just broke? | %1$s уходит. Мудро — или просто на мели? |
| `gui.burmaldaholic.pvp.coin.spectate` | %1$s vs %2$s — %3$s on the line | %1$s против %2$s — на кону %3$s |

### 15.6 Slot Showdown

Args: `slots.join` %1$s chips, %2$s/%3$s num · `slots.spins_value` %1$s num · `slots.round` num ·
`slots.auto_in` sec_acc · `slots.hot` / `msg…slots.hot` %1$s symbol (`gui.burmaldaholic.slots.symbol.*`)
· `slots.spin_points` %1$s points · `slots.kaboom` %1$s name, %2$s num, %3$s num · `slots.swap` %1$s
name, %2$s leader name, %3$s num, %4$s num · `slots.time_warp` %1$s name · `slots.star` %1$s name,
%2$s points · `slots.underdog` %1$s name · `slots.rules.5` %1$s points.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.slots.title` | Slot Showdown | Битва автоматов |
| `gui.burmaldaholic.pvp.slots.host` | Start a Slot Showdown | Начать битву автоматов |
| `gui.burmaldaholic.pvp.slots.join` | Join Showdown: %1$s · %2$s/%3$s | В битву: %1$s · %2$s/%3$s |
| `gui.burmaldaholic.pvp.slots.spins` | Spins per player | Вращений у каждого |
| `gui.burmaldaholic.pvp.slots.spins_value` | Spins each: %1$s | Вращений у каждого: %1$s |
| `gui.burmaldaholic.pvp.slots.round` | Spin %1$s/%2$s | Вращение %1$s/%2$s |
| `gui.burmaldaholic.pvp.slots.final_spin` | FINAL SPIN | ФИНАЛЬНОЕ ВРАЩЕНИЕ |
| `gui.burmaldaholic.pvp.slots.spin_now` | Spin! | Крутить! |
| `gui.burmaldaholic.pvp.slots.auto_in` | Auto-spin in %1$s | Автовращение через %1$s |
| `gui.burmaldaholic.pvp.slots.hot` | HOT: %1$s ×2 | ГОРЯЧО: %1$s ×2 |
| `msg.burmaldaholic.pvp.slots.hot` | Hot symbol this spin: %1$s scores double! | Горячий символ вращения: %1$s — очки ×2! |
| `gui.burmaldaholic.pvp.slots.spin_points` | +%1$s this spin | +%1$s за вращение |
| `gui.burmaldaholic.pvp.slots.kaboom_title` | KABOOM! | БАБАХ! |
| `msg.burmaldaholic.pvp.slots.kaboom` | KABOOM! %1$s's score is blown in half: %2$s → %3$s | БАБАХ! Очки игрока %1$s — пополам: %2$s → %3$s |
| `gui.burmaldaholic.pvp.slots.swap_title` | SWAP! | РОКИРОВКА! |
| `msg.burmaldaholic.pvp.slots.swap` | Ender Pearls! %1$s swaps scores with the leader %2$s: %3$s ⇄ %4$s | Жемчуг Края! %1$s меняется очками с лидером (%2$s): %3$s ⇄ %4$s |
| `msg.burmaldaholic.pvp.slots.time_warp` | Three clocks! %1$s's next spin counts double | Трое часов! Следующее вращение игрока %1$s — с двойными очками |
| `msg.burmaldaholic.pvp.slots.star` | THREE STARS! %1$s scores %2$s | ТРИ ЗВЕЗДЫ! %1$s получает %2$s |
| `gui.burmaldaholic.pvp.slots.underdog` | UNDERDOG BOOST | ФОРА АУТСАЙДЕРУ |
| `msg.burmaldaholic.pvp.slots.underdog` | Underdog boost: %1$s scores double on the final spin! | Фора аутсайдеру: %1$s получает двойные очки в финальном вращении! |
| `msg.burmaldaholic.pvp.slots.underdog_you` | You're in last place — your final spin counts double! | Вы на последнем месте — финальное вращение даст двойные очки! |
| `gui.burmaldaholic.pvp.slots.rules.1` | Everyone spins the same machine type. Line wins become points; the most points takes the pot. | Все крутят автоматы одного типа. Выигрыши на линиях превращаются в очки; у кого больше очков, тот забирает банк. |
| `gui.burmaldaholic.pvp.slots.rules.2` | Each spin one fruit or gem is HOT and scores double. | В каждом вращении один символ — горячий: он даёт двойные очки. |
| `gui.burmaldaholic.pvp.slots.rules.3` | Three Creepers or TNT on a line: KABOOM — your score is halved. | Три крипера или динамита на линии: БАБАХ — ваши очки делятся пополам. |
| `gui.burmaldaholic.pvp.slots.rules.4` | Three Ender Pearls: you swap scores with the leader. | Три жемчужины Края: вы меняетесь очками с лидером. |
| `gui.burmaldaholic.pvp.slots.rules.5` | Three Clocks: your next spin counts double. Three Nether Stars: %1$s. | Трое часов: следующее вращение — с двойными очками. Три звезды Незера: %1$s. |
| `gui.burmaldaholic.pvp.slots.rules.6` | Last place before the final spin scores double on it. | Последнее место перед финальным вращением получает в нём двойные очки. |
| `gui.burmaldaholic.pvp.slots.tiebreak` | Tie-break: more winning lines, then the best single spin | При равенстве: больше выигрышных линий, затем лучшее вращение |

### 15.7 Wheel Party

Args: `wheel.join` num/num · `wheel.slice` %1$s name, %2$s chips, %3$s pct · `wheel.spins_in` sec_acc
· `wheel.bar` %1$s chips, %2$s pct, %3$s sec_acc · `wheel.top_up` %1$s name, %2$s chips_acc, %3$s pct
· `wheel.lands`/`by_a_hair` %1$s name · `wheel.underdog` %1$s name, %2$s pct · `wheel.chance` pct.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.wheel.title` | Wheel Party | Колесо на всех |
| `gui.burmaldaholic.pvp.wheel.host` | Start a Wheel Party | Начать «Колесо на всех» |
| `gui.burmaldaholic.pvp.wheel.join` | Join the party: %1$s/%2$s | Присоединиться: %1$s/%2$s |
| `gui.burmaldaholic.pvp.wheel.add` | Add to my slice | Увеличить свой кусок |
| `gui.burmaldaholic.pvp.wheel.cap` | Max stake per player | Макс. ставка с игрока |
| `gui.burmaldaholic.pvp.wheel.your_stake` | Your stake | Ваша ставка |
| `gui.burmaldaholic.pvp.wheel.rules` | Buy a slice of the wheel: the bigger your stake, the bigger your slice. One spin — the winner takes the pot. | Купите кусок колеса: чем больше ставка, тем больше кусок. Одно вращение — победитель забирает банк. |
| `gui.burmaldaholic.pvp.wheel.slice` | %1$s — %2$s (%3$s) | %1$s — %2$s (%3$s) |
| `gui.burmaldaholic.pvp.wheel.chance` | Win chance: %1$s | Шанс на победу: %1$s |
| `gui.burmaldaholic.pvp.wheel.spin_now` | Spin the wheel! | Крутить колесо! |
| `gui.burmaldaholic.pvp.wheel.spins_in` | Spins in %1$s | Вращение через %1$s |
| `gui.burmaldaholic.pvp.wheel.bar` | Pot %1$s · your slice %2$s · spins in %3$s | Банк %1$s · ваш кусок %2$s · вращение через %3$s |
| `gui.burmaldaholic.pvp.wheel.underdog_tag` | UNDERDOG | ТЁМНАЯ ЛОШАДКА |
| `msg.burmaldaholic.pvp.wheel.top_up` | %1$s adds %2$s — now %3$s of the wheel | %1$s добавляет %2$s — теперь это %3$s колеса |
| `msg.burmaldaholic.pvp.wheel.no_more_bets` | No more bets! | Ставок больше нет! |
| `msg.burmaldaholic.pvp.wheel.lands` | The wheel stops on %1$s's slice! | Колесо останавливается на куске игрока %1$s! |
| `msg.burmaldaholic.pvp.wheel.by_a_hair` | By a hair! Just past %1$s's slice… | На волоске! Чуть-чуть мимо куска игрока %1$s… |
| `msg.burmaldaholic.pvp.wheel.underdog` | UNDERDOG! %1$s wins with just %2$s of the wheel! | ТЁМНАЯ ЛОШАДКА! %1$s побеждает всего с %2$s колеса! |

### 15.8 Plinko Battle

Args: `plinko.join` %1$s chips, num/num · `plinko.balls_value` num · `plinko.round` num/num ·
`plinko.auto_in` sec_acc · `plinko.ball_result` %1$s multiplier text, %2$s points · `plinko.edge`
%1$s name, %2$s multiplier text · `plinko.underdog` %1$s name.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.plinko.title` | Plinko Battle | Битва Плинко |
| `gui.burmaldaholic.pvp.plinko.host` | Start a Plinko Battle | Начать битву Плинко |
| `gui.burmaldaholic.pvp.plinko.join` | Join the battle: %1$s · %2$s/%3$s | В битву: %1$s · %2$s/%3$s |
| `gui.burmaldaholic.pvp.plinko.balls` | Balls per player | Шариков у каждого |
| `gui.burmaldaholic.pvp.plinko.balls_value` | Balls each: %1$s | Шариков у каждого: %1$s |
| `gui.burmaldaholic.pvp.plinko.round` | Ball %1$s/%2$s | Шарик %1$s/%2$s |
| `gui.burmaldaholic.pvp.plinko.final_ball` | FINAL BALL | ФИНАЛЬНЫЙ ШАРИК |
| `gui.burmaldaholic.pvp.plinko.drop_now` | Drop! | Бросить! |
| `gui.burmaldaholic.pvp.plinko.auto_in` | Auto-drop in %1$s | Автоброс через %1$s |
| `gui.burmaldaholic.pvp.plinko.ball_result` | Bin ×%1$s — %2$s | Лунка ×%1$s — %2$s |
| `gui.burmaldaholic.pvp.plinko.rules.1` | Everyone drops at the same time, at the same risk. Bin ×1 = 10 points. The most points takes the pot. | Все бросают одновременно и с одним риском. Лунка ×1 = 10 очков. У кого больше очков, тот забирает банк. |
| `gui.burmaldaholic.pvp.plinko.rules.2` | Last place before the final ball scores double on it. | Последнее место перед финальным шариком получает за него двойные очки. |
| `gui.burmaldaholic.pvp.plinko.tiebreak` | Tie-break: the best single ball | При равенстве решает лучший шарик |
| `msg.burmaldaholic.pvp.plinko.edge` | EDGE! %1$s hits the ×%2$s bin! | КРАЙ! %1$s попадает в лунку ×%2$s! |
| `msg.burmaldaholic.pvp.plinko.underdog` | Underdog boost: %1$s's final ball counts double! | Фора аутсайдеру: финальный шарик игрока %1$s — с двойными очками! |

### 15.9 Scratch Showdown

Args: `scratch.cell` num · `scratch.auto_in` sec_acc · `scratch.multiplier` num · `scratch.score`
points · `scratch.creeper` %1$s name, %2$s nested `scratch.symbol.*` · `scratch.fizzle` %1$s name ·
`scratch.foot` %1$s name, %2$s num.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.scratch.title` | Scratch Showdown | Лотерейная битва |
| `gui.burmaldaholic.pvp.scratch.cell` | Cell %1$s/9 | Клетка %1$s/9 |
| `gui.burmaldaholic.pvp.scratch.scratch_now` | Scratch! | Стереть! |
| `gui.burmaldaholic.pvp.scratch.auto_in` | Auto-scratch in %1$s | Автостирание через %1$s |
| `gui.burmaldaholic.pvp.scratch.multiplier` | ×%1$s | ×%1$s |
| `gui.burmaldaholic.pvp.scratch.score` | Score: %1$s | Счёт: %1$s |
| `gui.burmaldaholic.pvp.scratch.rules.1` | Everyone gets a card. Nine cells are scratched for all at once, one at a time. The highest score takes the pot. | Каждому — по билету. Девять клеток стираются у всех одновременно, по одной. У кого больше очков, тот забирает банк. |
| `gui.burmaldaholic.pvp.scratch.rules.2` | Coal 1 · Iron 2 · Gold 3 · Emerald 5 · Diamond 10 · Nether Star 25 | Уголь 1 · Железо 2 · Золото 3 · Изумруд 5 · Алмаз 10 · Звезда Незера 25 |
| `gui.burmaldaholic.pvp.scratch.rules.3` | Three or more of a kind score double. Rabbit's Foot: the whole card ×2 (max ×4). | Три одинаковых и больше — очки ×2. Кроличья лапка: весь билет ×2 (максимум ×4). |
| `gui.burmaldaholic.pvp.scratch.rules.4` | Creeper: blows up your best cell so far. | Крипер: взрывает вашу лучшую открытую клетку. |
| `gui.burmaldaholic.pvp.scratch.tiebreak` | Tie-break: the best single cell | При равенстве решает лучшая клетка |
| `gui.burmaldaholic.pvp.scratch.symbol.coal` | Coal | Уголь |
| `gui.burmaldaholic.pvp.scratch.symbol.iron` | Iron | Железо |
| `gui.burmaldaholic.pvp.scratch.symbol.gold` | Gold | Золото |
| `gui.burmaldaholic.pvp.scratch.symbol.emerald` | Emerald | Изумруд |
| `gui.burmaldaholic.pvp.scratch.symbol.diamond` | Diamond | Алмаз |
| `gui.burmaldaholic.pvp.scratch.symbol.star` | Nether Star | Звезда Незера |
| `gui.burmaldaholic.pvp.scratch.symbol.creeper` | Creeper | Крипер |
| `gui.burmaldaholic.pvp.scratch.symbol.foot` | Rabbit's Foot | Кроличья лапка |
| `gui.burmaldaholic.pvp.scratch.symbol.burned` | Charred | Сгорело |
| `msg.burmaldaholic.pvp.scratch.creeper` | Creeper! %1$s loses a cell: %2$s | Крипер! У игрока %1$s сгорает клетка: %2$s |
| `msg.burmaldaholic.pvp.scratch.fizzle` | Creeper! …but %1$s has nothing to blow up | Крипер! …но у игрока %1$s взрывать нечего |
| `msg.burmaldaholic.pvp.scratch.foot` | Rabbit's Foot! %1$s's card is now worth ×%2$s | Кроличья лапка! Билет игрока %1$s теперь ×%2$s |

### 15.10 NICE — spectator side bets

Args: `side.odds` %1$s name, %2$s decimal text · `side.pool` chips · `side.placed` %1$s name, %2$s
chips_acc · `side.won`/`lost` chips · `side.announce` %1$s game.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.side.title` | Side bets | Ставки зрителей |
| `gui.burmaldaholic.pvp.side.open` | Bet on this match | Поставить на матч |
| `gui.burmaldaholic.pvp.side.pick` | Back a player | На кого ставим |
| `gui.burmaldaholic.pvp.side.odds` | %1$s — pays about ×%2$s | %1$s — выплата около ×%2$s |
| `gui.burmaldaholic.pvp.side.pool` | Side pool: %1$s | Банк зрителей: %1$s |
| `gui.burmaldaholic.pvp.side.closed` | Side bets are closed | Ставки зрителей закрыты |
| `gui.burmaldaholic.pvp.side.none` | No matches to bet on nearby | Поблизости нет матчей для ставок |
| `gui.burmaldaholic.pvp.side.error_player` | Players can't bet on their own match | Участники не ставят на свой матч |
| `gui.burmaldaholic.pvp.side.error_mode` | Side bets aren't available for this game | Для этой игры ставки зрителей недоступны |
| `msg.burmaldaholic.pvp.side.placed` | You back %1$s with %2$s | Вы ставите на игрока %1$s: %2$s |
| `msg.burmaldaholic.pvp.side.won` | Your pick won! +%1$s | Ваш игрок победил! +%1$s |
| `msg.burmaldaholic.pvp.side.lost` | Your pick lost: −%1$s | Ваш игрок проиграл: −%1$s |
| `msg.burmaldaholic.pvp.side.refunded` | Nobody backed the winner — side bets returned | На победителя никто не ставил — ставки зрителей возвращены |
| `msg.burmaldaholic.pvp.side.announce` | Side bets open: %1$s. Casino Menu → Challenges | Открыты ставки зрителей: %1$s. «Меню казино → Вызовы» |

### 15.11 NICE — tournaments

Args: `tournament.row` %1$s name, %2$s game, %3$s chips, %4$s/%5$s num · `register` chips ·
`prize_pool`/`prize` chips · `prizes` joined text · `starts_in`/`ends_in` minute_acc or sec_acc ·
`round` num · `match_row` names · `bye` name · `your_best` points, num · `enter_run` chips ·
`runs_left` num · `announce` %1$s name, %2$s game, %3$s chips · `starting` %1$s name, %2$s players ·
`next_match` %1$s name, %2$s sec_acc · `eliminated` name · `champion` %1$s name, %2$s tournament
name, %3$s chips_acc · `prize` %1$s num, %2$s chips · `leader` %1$s name, %2$s points · trophy tooltip
%1$s tournament name, %2$s game, %3$s num.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.tournament.title` | Tournaments | Турниры |
| `gui.burmaldaholic.pvp.tournament.none` | No tournaments right now | Сейчас турниров нет |
| `gui.burmaldaholic.pvp.tournament.row` | %1$s · %2$s · entry %3$s · %4$s/%5$s | %1$s · %2$s · взнос %3$s · %4$s/%5$s |
| `gui.burmaldaholic.pvp.tournament.format.knockout` | Knockout | На вылет |
| `gui.burmaldaholic.pvp.tournament.format.leaderboard` | Leaderboard | Таблица лидеров |
| `gui.burmaldaholic.pvp.tournament.register` | Register (%1$s) | Записаться (%1$s) |
| `gui.burmaldaholic.pvp.tournament.unregister` | Withdraw | Отказаться от участия |
| `gui.burmaldaholic.pvp.tournament.prize_pool` | Prize pool: %1$s | Призовой фонд: %1$s |
| `gui.burmaldaholic.pvp.tournament.prizes` | Prizes: %1$s | Призы: %1$s |
| `gui.burmaldaholic.pvp.tournament.starts_in` | Starts in %1$s | Старт через %1$s |
| `gui.burmaldaholic.pvp.tournament.ends_in` | Ends in %1$s | Финиш через %1$s |
| `gui.burmaldaholic.pvp.tournament.bracket` | Bracket | Сетка |
| `gui.burmaldaholic.pvp.tournament.round` | Round %1$s | Раунд %1$s |
| `gui.burmaldaholic.pvp.tournament.semifinal` | Semi-final | Полуфинал |
| `gui.burmaldaholic.pvp.tournament.final` | Final | Финал |
| `gui.burmaldaholic.pvp.tournament.match_row` | %1$s vs %2$s | %1$s против %2$s |
| `gui.burmaldaholic.pvp.tournament.bye` | %1$s goes through without a match | %1$s проходит дальше без игры |
| `gui.burmaldaholic.pvp.tournament.your_best` | Your best: %1$s (#%2$s) | Ваш лучший результат: %1$s (№%2$s) |
| `gui.burmaldaholic.pvp.tournament.enter_run` | Play a run (%1$s) | Сыграть попытку (%1$s) |
| `gui.burmaldaholic.pvp.tournament.runs_left` | Runs left: %1$s | Осталось попыток: %1$s |
| `gui.burmaldaholic.pvp.tournament.create` | Create tournament | Создать турнир |
| `gui.burmaldaholic.pvp.tournament.name` | Name | Название |
| `gui.burmaldaholic.pvp.tournament.added_prize` | Added prize (paid by the bank) | Добавка к призу (за счёт банка) |
| `gui.burmaldaholic.pvp.tournament.max_players` | Max players | Макс. участников |
| `gui.burmaldaholic.pvp.tournament.duration` | Duration | Длительность |
| `msg.burmaldaholic.pvp.tournament.announce` | Tournament! %1$s — %2$s. Entry %3$s. Register in Casino Menu → Challenges! | Турнир! %1$s — %2$s. Взнос %3$s. Запись — «Меню казино → Вызовы»! |
| `msg.burmaldaholic.pvp.tournament.starting` | The %1$s tournament begins! Players: %2$s | Турнир «%1$s» начинается! Участников: %2$s |
| `msg.burmaldaholic.pvp.tournament.cancelled` | Tournament cancelled — not enough players. Entries returned. | Турнир отменён — мало участников. Взносы возвращены. |
| `msg.burmaldaholic.pvp.tournament.next_match` | Your next match: vs %1$s, in %2$s | Ваш следующий матч — против игрока %1$s, через %2$s |
| `msg.burmaldaholic.pvp.tournament.eliminated` | Knocked out by %1$s. Thanks for playing! | Вы выбываете — сильнее оказался %1$s. Спасибо за игру! |
| `msg.burmaldaholic.pvp.tournament.champion` | CHAMPION! %1$s wins the %2$s tournament and %3$s! | ЧЕМПИОН! %1$s выигрывает турнир «%2$s» и %3$s! |
| `msg.burmaldaholic.pvp.tournament.prize` | Prize for #%1$s: %2$s | Приз за №%1$s: %2$s |
| `msg.burmaldaholic.pvp.tournament.leader` | New leader: %1$s with %2$s | Новый лидер: %1$s — %2$s |
| `item.burmaldaholic.pvp_trophy` | Champion's Trophy | Кубок чемпиона |
| `tooltip.burmaldaholic.pvp_trophy` | %1$s · %2$s · day %3$s | %1$s · %2$s · день %3$s |

### 15.12 NICE — more modes, owner settings

Args: `series.format` num · `series.score` num–num · `series.match_point` name · `race.rules` %1$s
target text · `race.fee` chips · `race.hit` name · `race.timeout` %1$s spins ·
`heist.loot`/`loot_line` num · `heist.stole` %1$s name, %2$s num, %3$s name · `heist.bankrupt` /
`shielded` name · `bumper_placed` %1$s name, %2$s name · `scratch_poker.folds` name.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.game.series` | Coin Series | Серия на монетке |
| `gui.burmaldaholic.pvp.game.race` | Jackpot Race | Гонка за джекпотом |
| `gui.burmaldaholic.pvp.game.heist` | Wheel Heist | Налёт на колесо |
| `gui.burmaldaholic.pvp.game.scratch_poker` | Scratch Poker | Лотерейный покер |
| `gui.burmaldaholic.pvp.series.format` | Series: first to %1$s wins | Серия: нужно побед — %1$s |
| `gui.burmaldaholic.pvp.series.score` | Series %1$s–%2$s | Счёт серии %1$s–%2$s |
| `msg.burmaldaholic.pvp.series.match_point` | Match point for %1$s! | Матчбол у игрока %1$s! |
| `gui.burmaldaholic.pvp.race.rules` | Everyone spins together. The first to hit %1$s on a line takes the pot. | Все крутят одновременно. Кто первым соберёт на линии %1$s, тот забирает банк. |
| `gui.burmaldaholic.pvp.race.target` | three Diamonds or better | три алмаза или лучше |
| `gui.burmaldaholic.pvp.race.fee` | Per spin: %1$s from each | За вращение: %1$s с каждого |
| `msg.burmaldaholic.pvp.race.hit` | %1$s hits it! | %1$s срывает куш! |
| `msg.burmaldaholic.pvp.race.timeout` | Nobody hit it in %1$s — the most points wins | За %1$s никто не собрал — побеждает больше очков |
| `gui.burmaldaholic.pvp.heist.rules` | Three rounds, everyone spins. Grab loot, steal it or lose it all. The most loot takes the pot. | Три круга, крутят все. Хватайте добычу, крадите её или теряйте всё. У кого больше добычи, тот забирает банк. |
| `gui.burmaldaholic.pvp.heist.loot` | Loot +%1$s | Добыча +%1$s |
| `gui.burmaldaholic.pvp.heist.steal` | Steal | Кража |
| `gui.burmaldaholic.pvp.heist.bankrupt` | Bankrupt | Банкрот |
| `gui.burmaldaholic.pvp.heist.double` | Double | Удвоение |
| `gui.burmaldaholic.pvp.heist.shield` | Shield | Щит |
| `gui.burmaldaholic.pvp.heist.pick_victim` | Steal from whom? | У кого крадём? |
| `gui.burmaldaholic.pvp.heist.loot_line` | %1$s — loot %2$s | %1$s — добыча %2$s |
| `msg.burmaldaholic.pvp.heist.stole` | %1$s steals %2$s loot from %3$s! | %1$s крадёт у игрока %3$s добычу: %2$s! |
| `msg.burmaldaholic.pvp.heist.bankrupt` | BANKRUPT! %1$s loses all the loot | БАНКРОТ! %1$s теряет всю добычу |
| `msg.burmaldaholic.pvp.heist.shielded` | %1$s's shield blocks it! | Щит игрока %1$s отражает удар! |
| `gui.burmaldaholic.pvp.plinko.bumper` | Place bumper on… | Поставить отбойник… |
| `msg.burmaldaholic.pvp.plinko.bumper_placed` | %1$s puts a bumper on %2$s's next ball! | %1$s ставит отбойник на следующий шарик игрока %2$s! |
| `gui.burmaldaholic.pvp.scratch_poker.rules` | Hidden cards. Four cells, then bet; four more, then bet; the last cell at the showdown. | Билеты скрыты. Четыре клетки — торговля, ещё четыре — торговля, последняя — на вскрытии. |
| `msg.burmaldaholic.pvp.scratch_poker.folds` | %1$s folds | %1$s пасует |
| `gui.burmaldaholic.pvp.charter.allow` | Allow PvP matches | Разрешить PvP-матчи |

### 15.13 Advancements (→ STRINGS.md §advancements)

| Key | EN | RU |
|-----|----|----|
| `advancement.burmaldaholic.pvp_first_win.title` | Mano a Mano | Один на один |
| `advancement.burmaldaholic.pvp_first_win.description` | Win a match against another player | Выиграйте матч против другого игрока |
| `advancement.burmaldaholic.pvp_all_in.title` | Fortune Favours the Bold | Смелость города берёт |
| `advancement.burmaldaholic.pvp_all_in.description` | Go all-in in a PvP match and win | Сыграйте ва-банк в PvP-матче и победите |
| `advancement.burmaldaholic.pvp_all_square.title` | Even Steven | Квиты |
| `advancement.burmaldaholic.pvp_all_square.description` | Lose a coin duel, call double or nothing and get back to all square | Проиграйте дуэль на монетке, крикните «всё или ничего» и отыграйтесь |
| `advancement.burmaldaholic.pvp_underdog.title` | Dark Horse | Тёмная лошадка |
| `advancement.burmaldaholic.pvp_underdog.description` | Win a Wheel Party holding a tenth of the wheel or less | Выиграйте «Колесо на всех», владея десятой частью колеса или меньше |
| `advancement.burmaldaholic.pvp_phoenix.title` | Rise From the Ashes | Восстать из пепла |
| `advancement.burmaldaholic.pvp_phoenix.description` | Win a Slot Showdown after a KABOOM halved your score | Выиграйте битву автоматов после того, как «БАБАХ» ополовинил ваши очки |
| `advancement.burmaldaholic.pvp_lucky_feet.title` | Lucky Feet | Две лапки на удачу |
| `advancement.burmaldaholic.pvp_lucky_feet.description` | Win a Scratch Showdown with two Rabbit's Feet on your card | Выиграйте лотерейную битву с двумя кроличьими лапками на билете |
| `advancement.burmaldaholic.pvp_full_house.title` | Last One Standing | Один против всех |
| `advancement.burmaldaholic.pvp_full_house.description` | Beat five opponents in a single match | Обыграйте пятерых соперников в одном матче |
| `advancement.burmaldaholic.pvp_revenge.title` | Sweet Revenge | Сладкая месть |
| `advancement.burmaldaholic.pvp_revenge.description` | Win a grudge match against the player who kept beating you | Выиграйте «дело принципа» у того, кто раз за разом вас обыгрывал |
| `advancement.burmaldaholic.pvp_rampage.title` | Rampage | Разнос |
| `advancement.burmaldaholic.pvp_rampage.description` | Win five PvP matches in a row against at least two different players | Выиграйте пять PvP-матчей подряд минимум у двух разных соперников |
| `advancement.burmaldaholic.pvp_champion.title` | Champion | Чемпион |
| `advancement.burmaldaholic.pvp_champion.description` | Win a tournament | Выиграйте турнир |
| `advancement.burmaldaholic.pvp_bookie.title` | Called It | Чуйка |
| `advancement.burmaldaholic.pvp_bookie.description` | Win a side bet that paid ×5 or more | Выиграйте ставку зрителя с выплатой от ×5 |

### 15.14 Config labels (→ STRINGS.md §config)

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.section.pvp` | PvP | PvP |
| `config.burmaldaholic.pvp.enabled` | PvP modes | PvP-режимы |
| `config.burmaldaholic.pvp.rakeBasisPoints` | House cut (basis points, 100 = 1%%) | Комиссия заведения (базисные пункты, 100 = 1 %%) |
| `config.burmaldaholic.pvp.minStake` | Minimum stake | Минимальная ставка |
| `config.burmaldaholic.pvp.joinRadius` | Invite and join distance | Дистанция вызова и входа |
| `config.burmaldaholic.pvp.announceRadius` | Spectator radius | Радиус зрителей |
| `config.burmaldaholic.pvp.announceServerWidePot` | Pot announced to everyone | Банк для объявления всем |
| `config.burmaldaholic.pvp.inviteTimeoutTicks` | Time to answer a challenge (ticks) | Время на ответ на вызов (тики) |
| `config.burmaldaholic.pvp.lobbyTimeoutTicks` | Lobby time (ticks) | Время лобби (тики) |
| `config.burmaldaholic.pvp.decisionTimeoutTicks` | Decision time (ticks) | Время на решение (тики) |
| `config.burmaldaholic.pvp.countdownTicks` | Countdown (ticks) | Обратный отсчёт (тики) |
| `config.burmaldaholic.pvp.maxPendingInvites` | Open challenges per player | Открытых вызовов на игрока |
| `config.burmaldaholic.pvp.declineCooldownTicks` | Re-challenge cooldown after a decline (ticks) | Пауза после отказа (тики) |
| `config.burmaldaholic.pvp.historyTicks` | Keep finished matches (ticks) | Хранить сыгранные матчи (тики) |
| `config.burmaldaholic.pvp.affectsStreak` | PvP changes the luck streak | PvP влияет на серию удачи |
| `config.burmaldaholic.pvp.countsTowardVip` | PvP stakes count for VIP | PvP-ставки идут в зачёт ВИП |
| `config.burmaldaholic.pvp.streakAnnounce` | Win streak announcements | Объявления о сериях побед |
| `config.burmaldaholic.pvp.grudgeLosses` | Losses in a row for a grudge match | Поражений подряд для «дела принципа» |
| `config.burmaldaholic.pvp.taunts.enabled` | Taunts | Подколки |
| `config.burmaldaholic.pvp.taunts.cooldownTicks` | Taunt cooldown (ticks) | Пауза между подколками (тики) |
| `config.burmaldaholic.pvp.taunts.maxPerMatch` | Taunts per match | Подколок за матч |
| `config.burmaldaholic.pvp.allowOwnedMachines` | PvP at player casinos | PvP в казино игроков |
| `config.burmaldaholic.pvp.bots.enabled` | Bots in PvP | Боты в PvP |
| `config.burmaldaholic.pvp.bots.defaultPolicy` | Default seating | Рассадка по умолчанию |
| `config.burmaldaholic.pvp.bots.defaultDifficulty` | Default bot difficulty | Сложность ботов по умолчанию |
| `config.burmaldaholic.pvp.bots.maxPerMatch` | Max bots per match | Макс. ботов в матче |
| `config.burmaldaholic.pvp.coin.enabled` | Coin Flip Duel | Дуэль на монетке |
| `config.burmaldaholic.pvp.coin.maxDoubles` | Double or nothing: max doublings | «Всё или ничего»: макс. удвоений |
| `config.burmaldaholic.pvp.slots.enabled` | Slot Showdown | Битва автоматов |
| `config.burmaldaholic.pvp.slots.maxPlayers` | Slot Showdown: max players | Битва автоматов: макс. игроков |
| `config.burmaldaholic.pvp.slots.spinChoices` | Slot Showdown: spin options | Битва автоматов: варианты числа вращений |
| `config.burmaldaholic.pvp.slots.linkRadius` | Slot Showdown: machine link radius | Битва автоматов: радиус связи автоматов |
| `config.burmaldaholic.pvp.slots.spinIntervalTicks` | Slot Showdown: auto-spin after (ticks) | Битва автоматов: автовращение через (тики) |
| `config.burmaldaholic.pvp.slots.hotSymbol` | Slot Showdown: hot symbol | Битва автоматов: горячий символ |
| `config.burmaldaholic.pvp.slots.underdogBoost` | Slot Showdown: underdog boost | Битва автоматов: фора аутсайдеру |
| `config.burmaldaholic.pvp.slots.kaboom` | Slot Showdown: KABOOM | Битва автоматов: «БАБАХ» |
| `config.burmaldaholic.pvp.slots.pearlSwap` | Slot Showdown: Ender Pearl swap | Битва автоматов: рокировка |
| `config.burmaldaholic.pvp.slots.starPoints` | Slot Showdown: points for three stars | Битва автоматов: очки за три звезды |
| `config.burmaldaholic.pvp.wheel.enabled` | Wheel Party | Колесо на всех |
| `config.burmaldaholic.pvp.wheel.maxPlayers` | Wheel Party: max players | Колесо на всех: макс. игроков |
| `config.burmaldaholic.pvp.wheel.countdownTicks` | Wheel Party: countdown (ticks) | Колесо на всех: отсчёт (тики) |
| `config.burmaldaholic.pvp.wheel.noMoreBetsTicks` | Wheel Party: no more bets (ticks) | Колесо на всех: «ставок больше нет» (тики) |
| `config.burmaldaholic.pvp.wheel.underdogShareBasisPoints` | Wheel Party: underdog share (basis points) | Колесо на всех: доля тёмной лошадки (б. п.) |
| `config.burmaldaholic.pvp.plinko.enabled` | Plinko Battle | Битва Плинко |
| `config.burmaldaholic.pvp.plinko.maxPlayers` | Plinko Battle: max players | Битва Плинко: макс. игроков |
| `config.burmaldaholic.pvp.plinko.ballChoices` | Plinko Battle: ball options | Битва Плинко: варианты числа шариков |
| `config.burmaldaholic.pvp.plinko.linkRadius` | Plinko Battle: machine link radius | Битва Плинко: радиус связи автоматов |
| `config.burmaldaholic.pvp.plinko.roundIntervalTicks` | Plinko Battle: auto-drop after (ticks) | Битва Плинко: автоброс через (тики) |
| `config.burmaldaholic.pvp.plinko.underdogBoost` | Plinko Battle: underdog boost | Битва Плинко: фора аутсайдеру |
| `config.burmaldaholic.pvp.scratch.enabled` | Scratch Showdown | Лотерейная битва |
| `config.burmaldaholic.pvp.scratch.maxPlayers` | Scratch Showdown: max players | Лотерейная битва: макс. игроков |
| `config.burmaldaholic.pvp.scratch.revealIntervalTicks` | Scratch Showdown: auto-scratch after (ticks) | Лотерейная битва: автостирание через (тики) |
| `config.burmaldaholic.pvp.scratch.weights` | Scratch Showdown: cell weights | Лотерейная битва: веса клеток |
| `config.burmaldaholic.pvp.scratch.values` | Scratch Showdown: cell values | Лотерейная битва: очки клеток |
| `config.burmaldaholic.pvp.side.enabled` | Spectator side bets | Ставки зрителей |
| `config.burmaldaholic.pvp.side.rakeBasisPoints` | Side bets: house cut (basis points) | Ставки зрителей: комиссия (б. п.) |
| `config.burmaldaholic.pvp.side.windowTicks` | Side bets: extra window (ticks) | Ставки зрителей: доп. время (тики) |
| `config.burmaldaholic.pvp.tournament.enabled` | Tournaments | Турниры |
| `config.burmaldaholic.pvp.tournament.minPlayers` | Tournaments: min players | Турниры: мин. участников |
| `config.burmaldaholic.pvp.tournament.maxPlayers` | Tournaments: max players | Турниры: макс. участников |
| `config.burmaldaholic.pvp.tournament.registrationTicks` | Tournaments: registration (ticks) | Турниры: запись (тики) |
| `config.burmaldaholic.pvp.tournament.roundGapTicks` | Tournaments: pause between rounds (ticks) | Турниры: пауза между раундами (тики) |
| `config.burmaldaholic.pvp.tournament.windowTicks` | Tournaments: leaderboard duration (ticks) | Турниры: длительность таблицы лидеров (тики) |
| `config.burmaldaholic.pvp.tournament.maxRunsPerPlayer` | Tournaments: runs per player | Турниры: попыток на игрока |
| `config.burmaldaholic.pvp.tournament.prizeSplit` | Tournaments: prize split (%%) | Турниры: деление призов (%%) |
| `config.burmaldaholic.pvp.tournament.autoEveryDays` | Scheduled tournament every N days | Турнир по расписанию раз в N дней |
| `config.burmaldaholic.pvp.tournament.autoTimeOfDay` | Scheduled tournament: time of day | Турнир по расписанию: время суток |
| `config.burmaldaholic.pvp.tournament.autoMode` | Scheduled tournament: game | Турнир по расписанию: игра |
| `config.burmaldaholic.pvp.tournament.autoFormat` | Scheduled tournament: format | Турнир по расписанию: формат |
| `config.burmaldaholic.pvp.tournament.autoEntry` | Scheduled tournament: entry | Турнир по расписанию: взнос |
| `config.burmaldaholic.pvp.race.maxSpins` | Jackpot Race: max spins | Гонка за джекпотом: макс. вращений |
| `config.burmaldaholic.pvp.race.intervalTicks` | Jackpot Race: spin interval (ticks) | Гонка за джекпотом: интервал (тики) |
| `config.burmaldaholic.pvp.heist.rounds` | Wheel Heist: rounds | Налёт на колесо: круги |
| `config.burmaldaholic.pvp.plinko.bumpers` | Plinko Battle: bumpers | Битва Плинко: отбойники |
| `config.burmaldaholic.pvp.scratchPoker.enabled` | Scratch Poker | Лотерейный покер |
| `config.burmaldaholic.pvp.series.enabled` | Coin Series (best of 3/5) | Серия на монетке (до 2/3 побед) |

### 15.15 Bots in PvP (policy and difficulty names come from BOTS.md)

Args: `bots.opponent` %1$s nested BOTS.md difficulty name · `bots.will_fill` %1$s num ·
`bots.tagged` %1$s bot name, %2$s nested difficulty name · `bots.table_size_value` num.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.bots.opponent` | Bot — %1$s | Бот — %1$s |
| `gui.burmaldaholic.pvp.bots.seats` | Seats | Кто играет |
| `gui.burmaldaholic.pvp.bots.difficulty` | Bot difficulty | Сложность ботов |
| `gui.burmaldaholic.pvp.bots.table_size` | Table size | Мест за столом |
| `gui.burmaldaholic.pvp.bots.table_size_value` | Seats: %1$s | Мест: %1$s |
| `gui.burmaldaholic.pvp.bots.will_fill` | Empty seats at the start: bots fill %1$s | Свободные места при старте займут боты: %1$s |
| `gui.burmaldaholic.pvp.bots.start_with_bots` | Start with bots | Начать с ботами |
| `gui.burmaldaholic.pvp.bots.tagged` | %1$s [%2$s] | %1$s [%2$s] |
| `msg.burmaldaholic.pvp.bots.joined` | %1$s joins the game | В игру вступает %1$s |
| `msg.burmaldaholic.pvp.bots.no_bots_here` | Bots can't play at this machine — humans only | На этом автомате боты не играют — только люди |

### 15.16 Subtitles (NICE custom sound events → STRINGS.md §sounds)

| Key | EN | RU |
|-----|----|----|
| `subtitles.burmaldaholic.pvp_challenge` | Challenge horn | Рог вызова |
| `subtitles.burmaldaholic.pvp_drumroll` | Drumroll | Барабанная дробь |
| `subtitles.burmaldaholic.pvp_victory` | Victory fanfare | Победные фанфары |

---

## 16. Tests (identical vectors)

All `draw`/`score` functions are pure (§3.14), so the vectors below run as plain unit tests (Java
JUnit, Bedrock `npm test`) with `debug.fixedSeed`-style seeded RNGs. Monte-Carlo tolerances are
absolute unless stated.

### 16.1 Core

| # | Test | Expected |
|---|------|----------|
| C1 | Rake, 300 bp: pot 16, 20, 34, 50, 200, 400, 999, 1000, 10 000 | 0, 1, 1, 2, 6, 12, 30, 30, 300 |
| C2 | Rake, 0 bp (any pot) / 500 bp pot 30 / 1000 bp pot 25 | 0 / 2 / 3 |
| C3 | Split W = 97 between 2 tied winners, seat order [B, A] | B 49, A 48 |
| C4 | Split W = 100 among 3, seat order [C, A, B] | C 34, A 33, B 33 |
| C5 | Conservation: 10⁵ random matches (all modes, 2–8 players, random stakes, ties forced in 10 %) | Σ debits = Σ payouts + Σ rake exactly; no balance < 0 |
| C6 | Eligibility matrix §3.2: one case per row (12 rows) incl. owed = 1, default, Asset Freeze, owner at own machine, Netherite machine at Silver VIP, other dimension, 17 blocks away, stake 9, stake > tier max, balance < stake, second outgoing invite, decline cooldown | the listed error key |
| C7 | Owned anchor: rake goes to the bankroll; bankroll reservation unchanged; insolvent casino still accepts PvP | bankroll += rake |
| C8 | Lobby: host alone at timeout → cancelled, entry refunded; host leaves with 2 others → earliest joiner is host | refunds exact; host id |
| C9 | Rivalry: 3-player match A wins → A +1 W vs B and vs C; B, C +1 L vs A; B–C unchanged; split A=B → no A–B change | records as stated |
| C10 | Win streak thresholds [3, 5, 10]: messages fired exactly at 3, 5, 10; loss at 4 → "broken" message | message keys |
| C11 | Grudge: B lost last 3 to A → next A–B 2-player match flagged; a 3-player match with A and B → not flagged | flags |
| C12 | Streak isolation: 1000 PvP matches → §14 streak S unchanged (affectsStreak=false) | S = 0 |
| C13 | Taunts: 6th taunt in a match refused; 2 taunts within 100 t → 2nd refused | error keys |

### 16.2 Coin Flip Duel

| # | Test | Expected |
|---|------|----------|
| K1 | 10⁶ flips | heads 0.5 ± 0.0015 |
| K2 | Chain stakes, S = 100, loser keeps losing | 100, 100, 200, 400, 800; 6th offer disabled (`maxDoubles` 4) |
| K3 | Worked chain §4.4 (B, B, B, A) | A −24, B −24, rake 48 |
| K4 | 2 flips (B, then A wins the DoN), S = 100 | A −6, B −6 |
| K5 | DoN offer when D > a tier max or a balance < D | button replaced by `don_limit` / `don_unaffordable` |
| K6 | Timeouts: loser silent → walk away; winner silent → take the money | chain ends, no escrow |
| K7 | Disconnect during the countdown | flip settled from the tape; offer lapses |

### 16.3 Slot Showdown

| # | Test | Expected |
|---|------|----------|
| S1 | Mean points per spin (HOT off, no boosts), 10⁷ spins | Copper 0.897598, Golden Reels 2.793399, Netherite 4.746735 (±0.3 % relative) |
| S2 | P(KABOOM in a spin), 10⁷ spins | 0.003375 / 0.001535 / 0.001080 (±5 % relative) |
| S3 | Copper middle row [Berry, Berry, Apple] | 3 points; HOT = Berry → 6 |
| S4 | Golden Reels row [Wild, Diamond, Diamond] | 50; HOT = Diamond → 100; HOT = Apple → 50 |
| S5 | KABOOM: total 57, spin has a Creeper line + a 20-point line | 57 → 28 → 48 |
| S6 | SWAP: totals A 10, B 90, C 40; this round A scores only a Pearl line (10), B and C score 0 | step 3: A 20; step 4: A ⇄ leader B → A 90, B 20, C 40 |
| S7 | Two SWAPs in one round (A then C in seat order) | applied sequentially against the current leader |
| S8 | TIME WARP on spin 2 → spin 3 points ×2; on the last spin → no effect | as stated |
| S9 | Underdog: totals before final [12, 12, 30] → both 12s get ×2; [20, 20, 20] → nobody | as stated |
| S10 | Tie-break: equal totals, lines 7 vs 5 → the 7 wins; equal lines, best spin 60 vs 50 → 60 wins; all equal → split | as stated |
| S11 | Fairness MC: 2, 3, 6 players, each tier, 10⁶ matches | each seat's win share 1/N ± 0.003 |
| S12 | Tape size 6 players × 10 spins | serialized ≤ 700 chars; round trip equal |

### 16.4 Wheel Party

| # | Test | Expected |
|---|------|----------|
| W1 | Stakes [50, 150, 800]: u = 0, 49, 50, 199, 200, 999 | P1, P1, P2, P2, P3, P3 |
| W2 | MC 10⁶ spins, stakes [10, 30, 60] | 0.1 / 0.3 / 0.6 ± 0.002 |
| W3 | EV with 300 bp, stakes [50, 150, 800] (exact) | −1.5, −4.5, −24 |
| W4 | "By a hair" (stakes [50, 150, 800], P 1000, window 20) | u = 219 flagged (19 from 200, names P2), u = 221 not, u = 995 flagged (wraps to P1), u = 30 flagged (19 from 50, names P2) |
| W5 | Underdog: winner share 100/1000 → flagged; 101/1000 → not | as stated |
| W6 | Top-up beyond min(cap, tier max) / after No more bets | `error.over_cap` / `error.no_more_bets` |

### 16.5 Plinko Battle

| # | Test | Expected |
|---|------|----------|
| P1 | Points rows from `extras.plinko.*` (rounding) | the §7.1 table exactly |
| P2 | Exact mean points per ball (4096 paths) | Low 9.65625, Medium 9.6572265625, High 9.669921875 |
| P3 | Path mask → bin: 0b000000000000 → 0, 0b111111111111 → 12, 0b101010101010 → 6 | as stated |
| P4 | Underdog, tie-break, split | as S9/S10 with "best single ball" |
| P5 | Fairness MC 2/6 players, High, 3 balls, 10⁶ matches | 1/N ± 0.003 |

### 16.6 Scratch Showdown

| # | Test | Expected |
|---|------|----------|
| R1 | Worked card §8.4 (Coal, Coal, Coal, Diamond, Creeper, Iron, Foot, Emerald, Gold) | 32 |
| R2 | Star, Creeper, Creeper, then 6 × Coal | Star burned; 2nd creeper fizzles; Coal × 6 trio → 12 |
| R3 | 3 feet + 6 Diamonds | (6 × 10 × 2) × 4 = 480 (third foot ignored) |
| R4 | 7 Stars + 2 feet | 1400 (max) |
| R5 | Exact DP (defaults) | mean 38.8164 ± 0.0001, P(0) = 3.2458 × 10⁻⁵, P(2 feet) = 0.028158, P(2-player tie before tie-break) = 0.019972 |
| R6 | MC 10⁷ cards vs R5 | mean within 0.3 % |
| R7 | Fairness MC 2/6 players, 10⁶ matches | 1/N ± 0.003 |

### 16.7 Persistence, play-out and integration (manual + gametest)

| # | Scenario | Expected |
|---|----------|----------|
| I1 | Stop the server during a Slot Showdown reveal | on restart: settled from the tape; offline players get `result.offline` on join |
| I2 | Stop with an open lobby | entries refunded on load; `lobby.refunded` on join |
| I3 | Casino mode off mid-match / in lobby | settle at once / refund |
| I4 | Break the anchor during REVEAL / during LOBBY | no effect / cancelled + refund |
| I5 | Walk 100 blocks away mid-match | match continues; result chat arrives |
| I6 | Kill (crash) the Bedrock process right after the countdown title | tape exists in the dynamic property → settle on load |
| I7 | Bedrock: HUD title refresh during the Final Reveal | drama titles not cut (holdTitle) |
| I8 | Java: client packet sniff during a showdown | no unrevealed tape data sent |

### 16.8 Bots and seating (§3.15)

| # | Test | Expected |
|---|------|----------|

### 16.9 UI and localization

- Every key in §15 exists in all four language files (parity test of LOCALIZATION.md §1).
- Plural vectors of LOCALIZATION.md §3.2 for the new units `point`, `win`, `match`, `ball`.
- Action-bar lines with 16-char names ≤ 64 RU chars.
