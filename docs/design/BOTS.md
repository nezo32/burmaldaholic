# Burmaldaholic — Seats & Bots [bots]

Status: **v1.0 draft, implementation-ready**. Owner: game design (Seats & Bots).
Audience: Java (Fabric) team, Bedrock (Script API) team, testers, localization.

This file is **normative for the `bots` module** and for the seating rules of every multi-seat table
and PvP match. It was written while other designers edit `GAME_DESIGN.md`, `CONFIG.md`, `UI.md`,
`STRINGS.md`, `LOCALIZATION.md` and `PVP.md`, so **everything about seats and bots lives here** and
is merged later (§0.3 says where each part goes). Until the merge, this file wins over the others
for anything with a `bots` key or id, and for the seating rules of §2–§3.

Conventions are those of `GAME_DESIGN.md` (integer chips, world-time ticks, 20 t = 1 s, server-side
RNG, "tier max" = VIP max bet §12, "purse" defined in §5.1) and of `LOCALIZATION.md` (key grammar,
`%1$s` placeholders, number-class plurals `.p1/.p21/.p2/.p5`).

Research input: `docs/research/bots.md` (written in parallel). §13 lists what was taken from it and
the assumptions made where it was not available.

---

## 0. Summary

### 0.1 The player's promise

> "Play with your friend only, play only with bots, or play with friends and let bots fill the empty
> seats. Pick how good the bots are."

Every table and every PvP match has a **seating policy** and **bot settings**:

| Player wants… | Policy | Private table | Result |
|---------------|--------|---------------|--------|
| Only my friend(s), nobody else | `HUMANS_ONLY` | on, friend invited | Only invited humans can sit. No bots. |
| Only bots (solo practice / chill) | `BOTS_ONLY` | implicit | The host plus N bots. Other humans can watch, not sit. |
| My friends, and bots in the free seats | `MIXED` | on, friends invited | Invited humans + bots. A bot gives its seat to an invited friend at the next safe point. |
| Anyone, bots fill up | `MIXED` | off | Default at poker. Walk-ins take free seats; bots make room. |
| Public, no bots | `HUMANS_ONLY` | off | Classic multiplayer table. |

Bot difficulty is **EASY / NORMAL / HARD / MIXED** (MIXED = random per bot). Each bot also gets a
**personality** (Rock, Calling Station, Maniac, TAG, LAG) that changes its style but never its strength
class. Where decisions cannot change EV (chemin de fer, Coin Flip Duel, Wheel Party) the three levels are
shown honestly as a **Style** (Wild / Steady / Cool-headed); where bots have no decisions at all they only
have a personality.

### 0.2 Design pillars

1. **Bots never touch the dice.** The shuffle, the dice, the wheel and every PvP tape are drawn by the
   game's RNG exactly as without bots. Bots use their **own** random stream (§4.1). Difficulty changes
   decisions only, never outcomes.
2. **Bots never cheat.** A bot decides only from what a human in its seat would see: its own cards,
   the board, the bets and the public history. Monte-Carlo samples are drawn from all cards the bot
   cannot see (other players' hole cards are **not** excluded from the sample).
3. **Clearly a bot.** Every bot name carries the translated **[BOT]** tag and the bot glyph (§7.1).
   Nobody can mistake a bot for a person.
4. **Humans first.** A human who wants a seat always gets one at the next safe point (§3). A settings
   change never unseats a human.
5. **No free money.** Bots funded by the house cannot become a chip faucet: stake gates, daily "heat"
   limits on winnings from bots, weighted VIP credit, no Golden Hour, no cashback, no streak, and bot
   chips never become an owner's rake (§5).
6. **Cheap.** Bots exist only while a human is seated. Hard limits on count, CPU, entities and chat
   (§7.5).

### 0.3 Merge plan (for the doc owners)

| This file | Goes to |
|-----------|---------|
| §1–§6 rules | `GAME_DESIGN.md` new **§23 Seats & Bots [bots]** (module table gets a `bots` row); §7.4 (poker bots) is replaced by a pointer to §23 plus the poker table of §4.3 here |
| §5 economy rules | `GAME_DESIGN.md` §23; one-line pointers in §12 (cashback), §13.3 (Golden Hour), §14 (streak), §18.2 (owned casinos), §5.8 (debt) |
| §7, §8 UI | `UI.md` new **§16 Seats & Bots**; one line per game screen section (seat plate markers) |
| §9 config | `CONFIG.md` new `## bots` section (after `multiplayer / ownership`) |
| §10 advancements | `GAME_DESIGN.md` §19 table (append) |
| §11 strings | `STRINGS.md` new `## bots` section; advancements into §advancements; config labels into §config |
| §11.0 glossary | `LOCALIZATION.md` §6 new table "6.9 Seats & Bots"; add `bots` to the module list in §1.1 |
| §12 tests | `GAME_DESIGN.md` §23 "Test method" |

**Proposed amendments to existing text** (need the owners' sign-off; implement bots as written here):

1. **GAME_DESIGN §7.4** — the bot tiers **Fish / Regular / Shark** become the poker names of
   **EASY / NORMAL / HARD**. Their decision tables stay as written (with the corrections of §4.3).
   `poker.botsEnabled` becomes a legacy alias (§9.3). The seat-fill rule moves to §2 here.
2. **LOCALIZATION §4** "Player names, bot names … never translated" → "Player names and owner names are
   never translated. **Bot names are translation keys** (`gui.burmaldaholic.bots.name.<id>`, §11.3) so a
   Russian client sees Russian names." Reason: the server cannot know a Bedrock client's language, and
   the user asked for EN/RU name pools.
3. **GAME_DESIGN §18.2** owner settings "bots on/off (poker)" → the per-table **Bots** setting of §6.2
   (Off / Atmosphere only / Allowed — bankroll-funded).
4. **GAME_DESIGN §19** `shark_hunter` description "Bust a Shark bot" → "Take every chip from a Hard
   (Shark) poker bot" (same meaning, new vocabulary).
5. **PVP.md §3.2 rule 3** (no PvP while owing) gains: "except a match whose every other participant is
   a house-funded bot, when `bots.debtorsMayPlay`" (§5.5).
6. **PVP.md §3.8** head-to-head records, PvP win streak and grudge matches ignore bots (§5.3).
7. **UI.md §2** Casino Menu → Settings gains *Bot chatter* (per player). Casino Menu → Challenges (PvP
   hub) gains *Play vs bots…* (§8.6).
8. **Code changes this spec implies** (found by the research, `docs/research/bots.md` §1.4–§1.6):
   Fish folds aces to any shove above 10 BB (fixed by the EASY table, §4.3); poker hands vs bots
   currently count 100 % for VIP and update the streak (→ §5.3); bots at owned tables are currently
   bank-funded (→ §5.1); the name list is not localized and contains "Diamond Dave" (→ §7.1).

---

## 1. Terms

| Term | Meaning |
|------|---------|
| **Bot** | A server-side virtual player that occupies a seat. Not an entity (an optional avatar may be shown, §7.2). Id = `b` + 7-char base-36, unique per table session. |
| **Money bot** | A bot whose chips are real: poker opponents, chemin de fer banker/punters, PvP participants, tournament fillers. Funded by a **purse** (§5.1). |
| **Atmosphere bot** | A bot at a house-banked table (blackjack, roulette, craps, baccarat Punto Banco, Ultimate Texas Hold'em house rounds). Its bets are **virtual**: shown, never debited, never paid, never reserved (§5.2). |
| **Seat policy** | `HUMANS_ONLY` · `BOTS_ONLY` · `MIXED` (§2.1). |
| **Bot settings** | Bot count, difficulty, keep-a-seat-free, chatter, speed (§2.2). |
| **Access** | `OPEN` or `PRIVATE` (invite list, §2.5). |
| **Session** | From the first human sitting at an empty table until the last human leaves. Bots exist only inside a session. |
| **Keeper** | The player who placed an **unowned** craftable table (UUID stored in the block). Worldgen tables and `/give` tables placed by nobody in particular have no keeper; creative placement by an op sets that op as keeper. |
| **Owner** | Charter owner of an **owned** table (§18.2). |
| **Host** | The human who controls the current session's settings (§2.4). |
| **Safe point** | The moment in a game's round cycle where seats may change, bots may leave and pending settings apply (§3.1). |
| **Claimant** | A human who asked for a seat while every seat was taken and ≥ 1 is a bot seat (§3.2). |
| **Purse** | Where a money bot's chips come from and go back to: the **bank** (unowned) or the **owner's bankroll** (owned) (§5.1). |

---

## 2. Seat policy, bot settings, access, host

### 2.1 Seat policies

| Policy | Who may sit | Bots |
|--------|-------------|------|
| `HUMANS_ONLY` | Any human allowed by Access | none |
| `BOTS_ONLY` | **Only the host.** Other humans get `…bots.error.bots_only_table` and may spectate | exactly `count` bots, `1 ≤ count ≤ seats − 1` |
| `MIXED` | Any human allowed by Access | up to `count` bots, but never more than `seats − humans − keepFree` (§2.2) |

- **BOTS_ONLY is chosen only while the host is the only human seated** (a change never unseats a
  human, pillar 4). If another human is seated, the option is disabled with
  `…bots.error.others_seated`.
- **BOTS_ONLY session end:** when the host leaves, bots leave at the next safe point and the table
  reverts to its defaults.
- **Nobody plays with bots alone:** in any policy, when the last human leaves, bots leave at the next
  safe point (bot-vs-bot rounds are never simulated unobserved, except tournament fillers §3.7).
- A human who asks to sit at someone else's BOTS_ONLY table: the host gets a chat line with a
  clickable `[Let them in]` (Java) / an entry in the table form (Bedrock) that switches the session to
  MIXED (§8.3). Nothing happens without the host.

### 2.2 Bot settings

| Setting | Values | Meaning |
|---------|--------|---------|
| **Bot count** | `0 … seats − 1` | Target number of bot seats. MIXED: upper bound; BOTS_ONLY: exact (≥ 1). |
| **Difficulty** | `EASY` · `NORMAL` · `HARD` · `MIXED` | MIXED draws each bot's level from the game's mix (§4.2). Shown as **Style** where decisions cannot change EV, hidden where bots have no decisions (§4.2). |
| **Keep a seat free** | on / off (default on) | MIXED only: leave one seat empty for walk-ins so they never wait. Off → bots fill every seat, walk-ins wait for a safe point (§3.2). |
| **Chatter** | on / off (default on) | Bot quips and emotes at this table (§7.4). Each player may also mute all bot chatter for themselves. |
| **Speed** | `NORMAL` · `FAST` · `INSTANT` | Bot think delays (§7.3). FAST and INSTANT are offered only in BOTS_ONLY (other humans need time to follow the action). |

Effective bot seats at a safe point:
```
MIXED:     bots = max(0, min(count, seats − humans − claimants − (keepFree ? 1 : 0)))
BOTS_ONLY: bots = count                         (humans = 1: the host)
HUMANS_ONLY: bots = 0
```
Then capped by the atmosphere caps (§4.7), the purse and daily buy-in budget (§5.1), the global limits
(§7.5) and the heat stages (§5.4).

### 2.3 Defaults per game

"Craftable" = a table placed by a player; "worldgen" = generated in a structure (§16). A table keeps
its **table defaults** (saved in the block); a session starts with them and reverts to them when it
ends.

| Game (table) | Bot role | Policy | Count | Difficulty | Notes |
|--------------|----------|--------|-------|------------|-------|
| Texas Hold'em (craftable) | opponents (money) | `MIXED` | 5 (fill) | `MIXED` (stake mix `poker.botMix.*`) | = the old "bots fill, 1 walk-in seat free" rule |
| Texas Hold'em (Piglin Parlor) | opponents (money) | `MIXED` | 3 | `MIXED` with the Parlor mix [20,70,10] | preset of §16.2 |
| Chemin de fer | banker / punters (money) | `MIXED` | 2 | `MIXED` style | keeps the bank moving when few humans play |
| Blackjack | fellow players (atmosphere) | `HUMANS_ONLY` craftable / `MIXED` worldgen | 0 / 2 | `NORMAL` | |
| Roulette | bettors (atmosphere) | `HUMANS_ONLY` / `MIXED` worldgen | 0 / 3 | — (personality only) | |
| Craps | bettors (atmosphere), never shooters | `HUMANS_ONLY` / `MIXED` worldgen | 0 / 2 | — | |
| Baccarat Punto Banco | bettors (atmosphere) | `HUMANS_ONLY` / `MIXED` worldgen | 0 / 2 | — | |
| Ultimate Texas Hold'em | fellow players (atmosphere) | `HUMANS_ONLY` / `MIXED` worldgen | 0 / 2 | `NORMAL` | |
| UTH player-banked | dealer-seat stand-in when no human banks; atmosphere seats in house rounds | `MIXED` | 0 seats | — | §4.6 |
| PvP duels (Coin Flip, Scratch) | opponent (money) | chosen at set-up; default "a player" | — | `NORMAL` | §3.6 |
| PvP lobbies (Slots, Wheel, Plinko, Scratch) | participants (money) | `HUMANS_ONLY` | 0 | `NORMAL` | host may pick MIXED / BOTS_ONLY at set-up |
| Tournaments | fillers (money) | off | — | — | op option at creation (§3.7) |

Poker note: at poker, "count 5" with keep-free on and one human gives 4 bots (6 − 1 − 1), exactly the
old §7.4 rule. Worldgen High-Roller tables use the worldgen column.

### 2.4 Who may change settings (roles and host)

| Role | May change | Scope |
|------|-----------|-------|
| **Operator** (Java permission 2; Bedrock op) | everything, any table, any time (applies at the safe point) | table defaults + session |
| **Owner** (owned table) | everything within the owner's **Bots** setting (§6.2) | table defaults + session; owners never sit, so they edit from the Charter or by using the table |
| **Keeper** (unowned craftable table) | everything | table defaults + session; a seated keeper is always the host |
| **Host** | policy, count, difficulty, keep-free, chatter, speed, private + invites | **session only**, within the table's limits (`Players may change seating` and `Max bots`, §6.2) |
| Other seated humans | nothing; they see the settings read-only | — |

**Host selection (deterministic, no voting):**
1. If the keeper is seated → the keeper.
2. Else the human who has been seated **longest** in the session (the first to sit at the empty table).
3. When the host leaves, the next-longest-seated human becomes host
   (`msg.burmaldaholic.bots.host_now`).
Owners and ops are not hosts; they override from outside.

**Disagreement rules** (why no vote is needed):
- A human who wants fewer bots can take a seat: in MIXED a bot yields its seat (§3.2).
- A human who wants more bots can ask the host; nobody can force bots onto others.
- The host cannot switch to BOTS_ONLY while others sit, and cannot remove a seated human (no kick).
  Removing someone from the private invite list takes effect only when that person leaves.
- A human who dislikes the settings leaves; the table never traps anyone.

**When changes apply:** a saved change is **pending** until the table's next safe point (§3.1); the
table header shows `…bots.pending` ("New settings from the next round"). Several changes before the
safe point: the last one wins. Changing Access (private on/off, invites) applies **at once** (it only
affects who may sit later).

### 2.5 Private tables (friends only)

- **Private** toggle (host / keeper / owner / op). When switched on, **every human currently seated
  is added to the invite list** automatically, so nobody is locked out mid-session.
- **Invite list:** up to `bots.private.maxInvites` (16) players, by UUID. The host invites by:
  - Java: `/casino table invite <player>`; clicking a nearby player's name in the settings screen's
    *Invite* list; or using the **Casino Card on a player** while seated at the table (prompt
    "Invite Alex to your table?").
  - Bedrock: table form → *Private table…* → *Invite a player…* (dropdown of online players within
    `bots.private.inviteRadius`, 64; 0 = any online player); or using the Casino Card on a player
    (`playerInteractWithEntity`, a MessageForm prompt).
- The invited player gets `msg.burmaldaholic.bots.invited` (game, host, coordinates) — chat, never a
  pushed form. The invite lets them sit at **that** table while it is private.
- A non-invited human who uses a private table gets `…bots.error.private_table` naming the host and
  may spectate (public state per §18.1).
- **Lifetime of invites:** invites made by the **keeper or owner** are saved with the table (a "guest
  list"); invites made by a session host expire when the session ends. Ops always bypass.
- Owners may forbid private tables at their casino (`Allow private tables`, §6.2) — a casino is a
  public business by default. When forbidden, the toggle is disabled with `…bots.error.private_forbidden`.
- PvP: private lobbies are the existing duel invite (PVP.md §3.3.1); lobbies may be made
  **invite-only** at set-up with the same invite list rules (joiners not on the list get
  `…bots.error.private_table`).

---

## 3. Seats, safe points and making room

### 3.1 Safe points per game

At a safe point: pending settings apply, bots leave or join, claimants are seated.

| Game | Safe point | A leaving money bot… |
|------|-----------|----------------------|
| Texas Hold'em | Between hands: after AWARD, before the button moves | its whole stack returns to its purse |
| Chemin de fer | After RESULT, before BANK_OFFER. A bot **banker** leaves only here (its bank returns to its purse; the bank is offered to the next seat) | bank / nothing in play |
| Blackjack, roulette, craps, baccarat, UTH (atmosphere) | At the start of BETTING; also **any time during BETTING** (atmosphere bets are virtual and simply vanish) | — |
| UTH player-banked | As UTH; the bot dealer-seat stand-in leaves at the start of BETTING (§4.6) | — |
| PvP lobby | Any time **before START** (the bot's entry is refunded to its purse). After START bots stay until SETTLE | — |
| PvP duel vs a bot | n/a (the bot is the opponent for the whole chain; Double-or-nothing links included) | — |
| Tournament | Never (fillers are registered at the end of registration and play to the end) | — |

Craps note: bots never hold the dice (shooter rotation skips bots, §4.7), so a seven-out never
depends on a bot.

### 3.2 A human wants a seat

```
human uses the table
 ├─ access denied (private, BOTS_ONLY of another host, owner closed) → error, may spectate
 ├─ a seat is free → sit now (games' own "seated but waits for next round" rules apply)
 ├─ every seat is human → gui.burmaldaholic.error.table_full
 └─ every seat taken, ≥ 1 bot seat, policy MIXED → CLAIMANT:
        reserve the bot seat chosen by §3.3; message …bots.seat_after_round
        the bot leaves at the next safe point → the claimant is seated there automatically
        (Java: the table screen opens if the claimant is within reach; Bedrock: the table form is
        shown — the player asked for it — with UserBusy retries per UI.md §0.3)
```
- A claimant stays a claimant while online and within `multiplayer.tableLeaveDistance`; otherwise the
  claim lapses silently (the bot stays).
- Claimants are seated in claim order. A claim never waits more than one round/hand.
- `keepFree` guarantees a walk-in seat in MIXED, so claims are rare by default.

### 3.3 Which bot yields

- **Poker:** the bot that would pay the big blind **last** in the coming orbit (the one that just
  posted the big blind), so the leaving bot is the one that has already paid its share; ties → the
  smallest stack. The claimant takes that seat and is dealt in next hand (existing §7.3 rule; no
  blind is owed).
- **Chemin de fer:** a bot punter first (highest seat number); the bot banker only if no punter bot
  exists.
- **Atmosphere games:** the bot with the highest seat number.
- **PvP lobby:** the last bot to join.

### 3.4 Filling seats

- Bots join at a safe point, one message for all joiners (`…bots.joined_many` "3 bots sit down:
  %1$s").
- **PvP lobbies (MIXED):** bots (at most `bots.pvp.maxPerMatch`, 3) fill free places when no human
  has joined for `bots.pvp.fillDelayTicks` (400 = 20 s), or at once when the host presses *Fill with
  bots*. With keep-free on, bots leave one place open for a walk-in, as at tables.
- Busted poker bot: leaves at the safe point and is replaced by a **new** bot (new name, level drawn
  again under MIXED) if the policy still wants one and the purse and cap allow (§5).

### 3.5 Disconnect, table break, restart, casino mode off

- Bots follow each game's existing play-out rules (§4.1 of GAME_DESIGN): "drawn rounds are played
  out". In a drawn poker hand, bots **play on** as today; the bot's resulting stack returns to its purse.
- On **table break / chunk unload / server stop / casino mode off**: after the round is settled,
  every bot leaves; stacks and banks return to their purses. Bot state is **saved with the table**
  (stacks, bank escrow, purse) so a crash can always return it: an orphaned bot stack found on load is
  returned to its purse and the bot is not restored (the next session starts fresh).
- Atmosphere bots have nothing to return.

### 3.6 PvP matches (per-match policy)

The PvP set-up (PVP.md §3.3) gains one control **Opponents**:

| Mode | Choices at set-up | Bot decisions in the match |
|------|------------------|----------------------------|
| Coin Flip Duel | *A player* (invite, as today) · *A bot* | side call; Double-or-nothing offer / Let it ride (§4.8) |
| Scratch Showdown (duel) | *A player* · *A bot* | none |
| Slot Showdown, Plinko Battle, Scratch lobby | `HUMANS_ONLY` · `MIXED` (+ max bots) · `BOTS_ONLY` (+ bot count) | none |
| Wheel Party | same three | own stake (§4.8) |

- **Duel vs a bot:** the bot "accepts" after 20–60 t (think delay) unless the purse cannot fund it or
  the player is capped (§5.4; the bot then declines with a quip).
- **BOTS_ONLY lobby:** starts as soon as the bots are seated (after the countdown) — no 90 s wait.
- Invite-only lobbies: §2.5.
- The **Style** control (Wild / Steady / Cool-headed) is shown only for Coin Flip Duel and Wheel
  Party; in the other modes it reads `…bots.luck_only` ("Luck only — bots play exactly like you").

### 3.7 Tournaments (fill)

- Operator option at creation (`/casino tournament create … [fillBots]`, Admin form toggle *Fill with
  bots*): at the end of registration, bots register until the field reaches
  `pvp.tournament.minPlayers` (knockout: also up to the next power of two instead of byes, if
  `bots.tournament.fillToBracket`), at most `bots.tournament.maxFill` (8).
- Bot entries are paid from the bank; bot prizes return to the bank. Fairness is unchanged (all
  tournament modes are symmetric chance, PVP.md §9.2): each human's EV stays −rake/players.
- Bot-vs-bot knockout matches are settled instantly from their tape (no reveal).
- Leaderboard format: each bot buys one run.
- `pvp_champion` requires ≥ 3 **human** opponents in the field; the trophy is awarded either way.

---

## 4. Difficulty and personalities

### 4.1 Randomness contract (both editions)

- Each table (and each PvP match) creates a **bot RNG** at session start, independent of the game RNG:
  Java `RandomGenerator.of("L64X128MixRandom")` seeded from `SecureRandom` (or from
  `debug.fixedSeed ^ 0xB07` in tests); Bedrock a small seeded PRNG (sfc32) seeded from
  `Date.now()`, the table key hash and a session counter (or `debug.fixedSeed ^ 0xB07`) — **never** from
  `Math.random()`, which is the Bedrock game RNG, so seeding does not shift the game's stream either.
- Bots use only the bot RNG: think delays, mixed-strategy choices, Monte-Carlo sampling, names,
  personalities, quips.
- The game RNG (shuffles, dice, wheel, tapes) is **never called by bot code**. Test §12.1 checks that,
  with a fixed seed, the shuffled deck/dice sequence is identical for EASY, NORMAL, HARD and no bots.
- Difficulty and personality change **decisions only**. They never change payouts, odds, rake or the
  streak. The streak nudge (§14) never applies to rounds with money bots anyway (§5.3).

### 4.2 Levels, MIXED and stake gates

| Level | Tag (glyph + letter + color; never color alone) | Poker name | Style name (chemmy, Coin Flip Duel, Wheel Party) | Intended feel |
|-------|-------------------------------------------------|------------|------------------------------|---------------|
| `EASY` | `E` green | Fish | Wild | Makes visible, exploitable mistakes; a new player wins over time |
| `NORMAL` | `N` yellow | Regular | Steady | Solid, "by the book"; beats careless play, loses slowly to good play |
| `HARD` | `H` red | Shark | Cool-headed | Reads the table, mixes bluffs, punishes loose humans; about break-even against a solid human |

Where the decisions cannot change anyone's EV (chemin de fer, Coin Flip Duel, Wheel Party) the same
three values are presented as **Style** ("Wild / Steady / Cool-headed") — the game never pretends
there is a skill level there. Where bots have no decisions at all (roulette, craps, baccarat, Slot
Showdown, Plinko Battle, Scratch Showdown) the control is hidden and bots only have a personality.

**MIXED** draws each new bot's level from a weight list (Easy/Normal/Hard %):

| Where | Weights | Key |
|-------|---------|-----|
| Poker Micro | 45 / 45 / 10 | `poker.botMix.micro` (new default) |
| Poker Low | 35 / 50 / 15 | `poker.botMix.low` |
| Poker Mid | 10 / 55 / 35 | `poker.botMix.mid` |
| Poker High | 0 / 45 / 55 | `poker.botMix.high` |
| Piglin Parlor poker preset (Low) | 20 / 70 / 10 | worldgen preset (existing `REGULAR_HEAVY_MIX`) |
| Every other game | 30 / 50 / 20 | `bots.difficultyMix` |

**Stake gates (poker):** `EASY` is offered only at Micro and Low by default
(`bots.poker.easyMaxStake` = LOW). At Mid/High the *Easy* option is disabled with
`…bots.error.easy_stake`, and MIXED weights for Easy there are 0 whatever the config says. Reason
(research §3.2): a competent human beats a Fish-heavy table by ≈ 40 BB/100 ≈ 24 BB/hour — 48 chips/h
at Micro (fine), 1 200–4 800 chips/h at Mid/High (a mint).

### 4.3 Texas Hold'em (money; the only game where skill decides money against bots)

This table **replaces** the decision table of GAME_DESIGN §7.4 (Fish/Regular/Shark stay as the poker
names of the three levels). Notation of §7.4: C = Chen score, E = equity, `po = toCall/(pot + toCall)`,
b = bet/pot. Positions: EP, MP, CO, BTN, SB, BB.

**Range-aware equity (NORMAL and HARD).** Each live opponent's hole cards are sampled from a range
narrowed by that opponent's **public** actions this hand (tightest seen wins):

| Opponent's latest action | Sampled from the top … % of Chen-ranked hands |
|--------------------------|-----------------------------------------------|
| none / checked / limped | 100 % (NORMAL); HARD: 100 % minus the top 8 % (they would have raised) |
| called a bet | 60 % |
| bet ≤ ½ pot, or opened preflop | 45 % |
| bet ½–1 pot, or 3-bet | 25 % |
| raised postflop, overbet, all-in | 12 % |

HARD additionally scales a human's ranges by their measured VPIP (a human with VPIP 50 % who bets gets
a range twice as wide as the table; clamped to 100 %). Samples exclude only the cards the bot can see.

| | EASY ("Fish") | NORMAL ("Regular") | HARD ("Shark") |
|---|---|---|---|
| Preflop open | limp with C ≥ 4 (65 % of such hands); raise 3 BB with C ≥ 10; ignores position | open-raise 3 BB (+1 BB per limper) with C ≥ 8 EP / 7 MP / 6 CO-BTN / 6 SB | by position: EP 9, MP 8, CO 7, BTN 5, SB 6; size 2.5 BB (+1 BB per limper); steal from CO/BTN/SB with C ≥ 4 when folded to, 40 % |
| Facing a raise | call with any pair, any Ace, suited broadways while the call ≤ 15 % of stack; **always call / shove with C ≥ 12 or TT+** (fixes the research §1.6.1 "Fish folds aces to a shove" defect) | 3-bet to 3× with C ≥ 11; call with C ≥ 9; else fold (BB checks when free) | polarized 3-bet: value C ≥ 11, bluff 10 % with suited A2–A5 / suited connectors in position; call C ≥ 8 in position, 9 out of position; vs a 4-bet or shove continue with the top 5 % (QQ+, AK) or when `E ≥ po` at that stack depth |
| Postflop reading | no simulation: made hand (uses a hole card) + outs (flush draw 9, open-ended straight draw 8) | Monte-Carlo, `poker.bot.regularSamples` (300), ranges above | Monte-Carlo, `poker.bot.sharkSamples` (700), ranges above + VPIP scaling |
| Value bet | two pair+: 50 % pot | E ≥ 0.65: 66 % pot | E ≥ 0.70 (0.58 against a calling station): 60–80 % pot; river overbet 125 % with the nuts 20 % |
| Call | any pair while the bet ≤ pot; any draw while ≤ ½ pot; **floats** 35 % with two overcards on the flop to bets ≤ ½ pot | E ≥ po + 0.05 | E ≥ po, plus **minimum defence**: facing bet b, call when this hand's E is within the top `1/(1+b)` of the bot's own sampled equity distribution on this street (same samples, no extra cost) |
| Bluff | 5 % random bet with nothing | continuation bet 50 % heads-up / 25 % multiway as preflop raiser; never bluffs the river | river bluffs at ratio `b/(1+b)` bluffs per value bet, chosen from its lowest-E hands that had a draw; flop semi-bluff 40 % with E ≥ 0.30; floats in position heads-up with E ≥ 0.25; check-raises out of position with E ≥ 0.80, 30 % |
| Position | none | preflop only | preflop and postflop |
| Opponent model | none | none | per human, last 50 hands: VPIP, PFR, aggression factor AF. Station (VPIP > 40 %, AF < 1): no bluffs, thin value. Nit (VPIP < 15 %): steal more, fold more to its raises. Maniac (AF > 3): call down wider. Bots are never modelled |
| Mistakes | 15 % of decisions take the next-lower or next-higher legal action; **tilt**: after losing a pot > 40 BB plays C − 1 looser for 5 hands | 8 % next-lower action | none (randomness only via the mixed strategies above) |
| Think time (§7.3) | 10–30 t; **tell**: +40 t when E ≥ 0.7 (a readable tell for beginners, documented in the Rules page) | 20–60 t, independent of the hand | 20–60 t + 0–40 t on decisions where to-call > 25 % of stack, independent of the hand |

Every decision ends with `legalize(view, action)` (raise → call when betting is closed, oversize →
all-in, fold → check when free), as in today's code.

Budget fallback: if the Monte-Carlo job has fewer than 50 samples at the think deadline (§7.5), the
bot decides with what it has; with < 50 samples it uses the NORMAL rule on those samples. A decision is
never later than `bots.think.maxTicks + 40` t. Bedrock's drawn-outcome play-out (GAME_DESIGN §4.1)
keeps `PLAYOUT_SAMPLES = 16` for every level.

**Personalities** (weight per level; they shift the thresholds above but never cross levels —
test §12.2):

| Personality | EASY | NORMAL | HARD | Δ open threshold (C) | Raise : call aggression | Bluff × | Style in words |
|-------------|------|--------|------|---------------------|------------------------|---------|----------------|
| `ROCK` (tight-passive) | 25 | 30 | 0 | +2 | ×0.5 | ×0.3 | "rarely bluffs" |
| `STATION` (Calling Station, loose-passive) | 35 | 0 | 0 | −3; calls 1.5× wider | ×0.5 | ×0.2 | "calls everything" |
| `MANIAC` (loose-aggressive, spewy) | 40 | 0 | 0 | −3 | ×2 | ×3 | "raises everything" |
| `TAG` (tight-aggressive) | 0 | 70 | 60 | 0 | ×1 | ×1 | "by the book" |
| `LAG` (loose-aggressive, solid) | 0 | 0 | 40 | −1.5 (round toward the looser integer at use) | ×1.4 | ×1.5 | "pressure player" |

**Target win rates** (verified by the simulation suite, §12.2), in BB/100 for the bot:

| Bot level | vs scripted ABC tight-aggressive human | vs scripted loose-passive human (VPIP 45 %) |
|-----------|----------------------------------------|---------------------------------------------|
| EASY | −40 to −80 | −10 to +5 |
| NORMAL | −5 to −15 | 0 to +10 |
| HARD | −3 to +3 | +10 to +25 |

### 4.4 Chemin de fer (money; no skill — the tableau is fixed, §20.9)

The tableau draws by fixed rules, so every chip has the same EV whoever bets it (punters −1.24 %,
banker −1.06 % after rake). The control is **Style**; it changes tempo and stakes, never EV.

| Decision | EASY (Wild) | NORMAL (Steady) | HARD (Cool-headed) |
|----------|-------------|-----------------|--------------------|
| Take the bank when offered | 60 % | 40 % | 30 %, only when its purse allows ≥ 10 coups at the table's typical bet |
| Bank amount B | 2–5 × `baccarat.chemmy.minBank` (uniform) | 10 × table min | `min(bot bank cap, 3 × median punter stake of the last 5 coups × seated humans)` — covers what is actually bet |
| After a winning coup | keeps the bank until it loses ("hot hand") | passes after 3 wins | keeps while `B ≤ bot bank cap`, else passes |
| Punter stake | 1–3 × table min, doubles after a loss (Martingale, reset at 8×) | flat 2–5 × table min | flat, sized to fill open coverage up to its cap |
| Banco | 20 % when affordable | 5 % | never |

`bot bank cap` = `bots.chemmy.bankCapMultiple` (50) × table min, ≤ the table max coverage.

Rules that keep bots from crowding humans:
- **Bot punters bet last**: only in the last 100 t of BETTING, snapped to the coverage humans left
  open; never Banco while a human has a bet on the coup.
- **A bot banks only if ≥ 1 human punter is seated.** While a bot holds the bank, other bots do not punt
  (they show *Watching*): bot-vs-bot money would be the purse against itself.
- A bot banker's winnings pay **no rake** (the house does not rake itself). A human banker's winnings
  taken from bot punters are raked into the **bank sink**, never into an owner's bankroll (§5.1).

### 4.5 Ultimate Texas Hold'em fellow players (atmosphere)

Bets are virtual; levels change only what the table sees them do.

| Street | EASY | NORMAL (= strategy R, §21.3) | HARD |
|--------|------|------------------------------|------|
| Preflop | ×4 with any pair or any Ace; 15 % of other hands ×3 ("scared money"); else check | R | R (never ×3; matches published near-optimal play) |
| Flop | ×2 with any pair (board-only pairs too — a visible mistake) or a flush draw | R | R |
| River | ×1 with any pair or better; otherwise fold 70 % / ×1 30 % | R (exact enumeration of the 990 dealer hands) | R |
| Trips | 50 % of rounds, 1 × Ante | never | never |

Tooltip line (Java) / body line (Bedrock) for the level: `…bots.uth_edge` "Costs this bot about %1$s
of the Ante" — EASY "≈ 5 %" (to be replaced by the simulated figure, §12.3), NORMAL 2.27 %, HARD 2.19 %.
The river enumeration (990 × 2 evaluations) runs inside the street's shared decision window; Bedrock
runs one bot per tick via `runJob`.

### 4.6 UTH player-banked tables

- **Human banker:** bots are **not dealt in** (they show *Watching*). Reason: bot seats are house
  money with a −2.2 % edge; a human bank would farm the house (+2.27 % of the Ante before rake).
- **No human banker, policy ≠ HUMANS_ONLY:** when `uth.pvp.houseRoundsWhenNoBanker` is true the house
  round runs as today, and the dealer plate may show a bot **stand-in** ("Dealer seat: [BOT] Madame
  Ender") for flavour. It is a house round in every respect (bank- or owner-funded, reservations,
  Golden Hour, cashback, §21), makes no decisions, offers the seat to humans at every rotation point and
  leaves at the start of BETTING when a human takes the seat. When `houseRoundsWhenNoBanker` is false
  there is **no bot banker** (research §2.3): the table waits for a human banker.
- Atmosphere bots may sit in player seats during house rounds (virtual bets, §4.5).

### 4.7 Blackjack, roulette, craps, baccarat (atmosphere)

At most `bots.atmosphere.maxPerTable.<game>` bots: blackjack 2, UTH 2, roulette 3, craps 3,
baccarat 3 (pace and screen space). Atmosphere bots bet at a random moment in the first 3–8 s of
BETTING and always count as *Ready* / *Deal pressed* — **they never delay a round**; a single human
with bots deals as fast as solo play.

Blackjack (levels):

| | EASY ("mimic the dealer") | NORMAL | HARD |
|---|------|--------|------|
| Play | hit below 17, stand on 17+, never double, split only A-A and 8-8 | basic strategy for the table's rules with 5 % errors on soft totals and doubles | perfect basic strategy for the table's rules (S17/H17, DAS, resplit to `maxHands`) |
| Insurance / even money | 50 % | never | never (quip: "Never take insurance.") |
| Virtual bet | random 1–5 × min, doubles after a loss (reset at 8×) | flat 2–5 × min | flat 5–10 × min, ≤ table max |
| Think time per action | 15–40 t | 10–30 t | 10–25 t |

- Bot hands take real cards from the shared shoe (card removal by play nobody can see changes nobody's
  expectation). Bots act in seat order but never use the 20 s timer. The dealer draws if **any** hand
  (bot included) is live — presentation only.

Roulette, craps and baccarat have **no decisions that matter** (every bet has its fixed edge); bots get
a **betting style** from their personality (research §2.5):

| Personality | Roulette | Craps | Baccarat |
|-------------|----------|-------|----------|
| `ROCK` | *Red Lover*: red (or black), flips colour after 3 losses | *Dark Side*: Don't Pass + lay odds | *Banker Only* |
| `STATION` | *Dozen Grinder*: two dozens | *Field Fan*: Field every roll | *Trend Follower*: bets the last winner |
| `MANIAC` | *Sprinkler*: 5–8 random inside chips | *Come Ladder*: Pass + every Come + odds | *Tie Hunter*: Tie + pairs every few coups |
| `TAG` | *Lucky Number*: a favourite number + its wheel neighbours | *Right-way Grinder*: Pass + full 3-4-5× odds | *Banker Only* (no side bets) |
| `LAG` | *Martingale Maria*: doubles an even-money bet after a loss, resets at 8× | Pass + Field + odds | *Chop Chaser*: bets against the last winner |

Virtual amounts `min + k × step`, k = 1–2 (ROCK, STATION), 2–5 (TAG), 5–10 (MANIAC, LAG), within the
table min/max. Craps bots **never shoot** unless `bots.craps.canShoot` (false); when allowed they roll
after 20–40 t. With bots unable to shoot, the rotation skips them, so a seven-out never depends on a bot.

### 4.8 PvP modes (money; chance only)

Every MUST PvP mode is symmetric chance with the tape drawn at START (PVP.md §3.5), so **no bot choice
changes anyone's EV**, and the tape is drawn from the same distribution for bots and humans.

| Mode | Bot choice | EASY (Wild) | NORMAL (Steady) | HARD (Cool-headed) |
|------|-----------|-------------|-----------------|--------------------|
| Coin Flip Duel | side call | random | random | random |
| | as chain loser: Double or nothing | always (up to `pvp.coin.maxDoubles`) | 50 % | walks away |
| | as chain winner: Let it ride | always | 50 % | takes the money |
| Wheel Party | stake / top-ups | `pvp.minStake` + random top-ups | the median human stake | up to the cap C, early |
| Slot Showdown, Plinko Battle, Scratch Showdown | none | — (control hidden, `…bots.luck_only`) | | |
| All | accept a rematch | 80 % | 50 % | 30 % (+20 % after a loss: "grudge") |

Wheel Party: a bigger bot slice lowers a human's win chance but enlarges the pot; each human's EV stays
`−s_i · R / P` (Lemma 2). Bots press *Spin! / Drop! / Scratch!* after 10–30 t (timing only).

### 4.9 Showcase tables (NICE)

A worldgen table with `bots.showcase.enabled` (false) and no seated human plays **virtual** bot-only
rounds while a player is within `multiplayer.spectatorRadius` (a lively casino floor; "watch the Sharks
play"). Virtual chips only, NORMAL pace, pauses when nobody watches, stops the moment a human sits (the
normal policy then applies). Counts against `bots.maxActiveTables`. Not in v0.1.0.

---

## 5. Economics and anti-abuse

### 5.1 Purses (who pays for money bots)

| Where | Purse | Rules |
|-------|-------|-------|
| Unowned table / anchor (house, worldgen, keeper tables) | **Bank** | Bot buy-ins, banks and entries are minted from the bank; everything a bot ends with returns to the bank (sink). As §7.4 today. |
| Owned table / anchor (§18.2) | **Owner's bankroll** — only if `bots.owned.funding` = `OWNER_BANKROLL` (default) **and** the owner set the table's **Bots** to *Allowed* (§6.2; default *Atmosphere only*) | A bot's buy-in / bank / entry is **reserved and debited from the bankroll** when the bot sits (`bankroll − reserved` must cover it, else the bot does not sit: `…bots.bankroll_short` on the charter); everything the bot holds returns to the bankroll when it leaves. `bots.owned.funding` = `DISABLED` → no money bots at owned casinos. |

- **The bank never funds bots at owned casinos** (research §3.3: today's code does, which lets an
  owner run an "Easy bot farm" for friends paid by the world bank — to be changed).
- **Bot chips never become rake for an owner** (and never rake at all for bots):
  - Poker: `rake = min(floor((pot − botContrib) × poker.rakePercent), poker.rakeCapBb × BB)`, and only
    when ≥ 2 humans contributed (§7.3 "no flop, no drop" unchanged). A pot where the only human faces
    bots is not raked (as today).
  - Chemin de fer: a bot banker pays no rake; a human banker's rake on chips won from bot punters goes
    to the bank sink, never to a bankroll.
  - PvP: the rake is computed on the whole pot as PVP.md §3.4 says (so every human's EV stays exactly
    −R/N), but the part `floor(rake × botStakes / pot)` goes to the bank sink instead of an owner's
    bankroll.
- **Per-table daily buy-in budget** (house-funded only): a table funds at most
  `bots.tableBuyInsPerDay` (10) new bot buy-ins/banks per MCD. When exhausted, busted bots are not
  replaced until the next MCD (`msg.burmaldaholic.bots.regulars_gone` "The regulars went home").
- Charter screen shows *Bot stacks out* (chips currently held by bankroll-funded bots) and *Bot
  results today* (§8.5).

### 5.2 Atmosphere bots are free

Atmosphere bets never touch any account, reservation, exposure check, jackpot, statistics, VIP,
contract, streak, chaos trigger or advancement. They exist only on screens and in chat. They are
therefore allowed at owned tables without the owner's bankroll (Bots = *Atmosphere only*).

### 5.3 What rounds against money bots count for

"Bot round" = a settled round in which the human's money went to or came from at least one money bot.
`botShare` = the bots' share of the money on the other side of the human in that round (poker: bot
chips matched against the human's pot contributions / all chips matched; chemmy: 1 when the bank is a
bot, else bot punter stakes / all punter stakes; PvP: bot stakes / other participants' stakes).

| Mechanic | Counts? | Rule |
|----------|---------|------|
| VIP lifetime wagered (§12) | **weighted** | `credit = floor(wagered × (1 − (1 − bots.vipWagerWeight) × botShare))`; weight 0.5 → chips matched by bots count half, chips matched by humans in full. Poker against Easy bots is +EV for good players; full credit would make it a VIP ladder that costs less than nothing. |
| `wager` contract | weighted | same credit |
| Game contracts (`play_poker`, …) | yes | `play_poker` counts hands vs bots (it is a time task, and how solo players play poker) |
| Cashback (§12) | **no** | bot rounds are not house-edge rounds (poker and chemmy never had cashback) |
| Golden Hour bonus (§13.3) | **no** | as all PvP / player-banked rounds |
| Lucky/Unlucky streak (§14) | **no** | a bot round never updates S (today's code does for poker — to be changed) |
| Chaos big-win `lucky_buff` (§13.1.3) | no | |
| Server-wide big-win broadcast | no | when every counterparty was a bot (no farm spam) |
| Advancements | yes, with exceptions | `royal_flush` counts vs bots (a royal is luck). Advancements that count **players/opponents** ignore bots: `pvp_full_house`, `pvp_rampage`, `pvp_revenge`, `pvp_champion` (§3.7); `banco`, `bank_holder`, `uth_house_seat` need ≥ 1 human on the other side. `shark_hunter` is bot-only (HARD). §10 adds bot advancements. |
| PvP rivalry, head-to-head, PvP win streak, grudge | no | bots are not rivals |
| Statistics | separate "vs bots" line (net, hands/matches) | |

House rounds with atmosphere bots are **not** bot rounds: everything counts as usual for the human.
A UTH house round shown with a bot stand-in (§4.6) is a house round.

### 5.4 Heat: daily limits on winnings from house-funded bots

Per player per MCD (`floor(worldTime / 24000)`), track `botNetToday` = net chips won from
**house-funded** money bots (losses to them count negative). Owner-funded bots are not tracked (the
owner chose to risk the bankroll).

```
cap = max(bots.dailyWinCapMin, bots.dailyWinCapTierMultiple × tierMax)       // defaults 500, 5
      Bronze 500 · Silver 1 250 · Gold 5 000 · Platinum 12 500 · Diamond 50 000 · Netherite 250 000
```
(For scale: at Micro a Bronze cap is 250 BB, at Low a Silver cap 125 BB, at Mid a Gold cap 100 BB —
about 1–5 hours of beating a soft table, research §3.2.)

**Attribution** (integer arithmetic, floor, identical in both editions):
- **Poker**, per pot p (main and side pots, after rake), for human h:
  `fromBots_h = Σ_p floor(won_h,p × botContrib_p / potSize_p)` (h's winnings from p × the bots' share
  of p) and `toBots_h = Σ_p floor(contrib_h,p × botWon_p / potSize_p)` (h's contribution to p × the
  share of p won by bots). `botNet += fromBots − toBots`. Uncalled bets returned are not counted.
- **Chemin de fer:** the human's coup result when the counterparty is a bot (bot banker), or, for a
  human banker, the bot punters' settled stakes minus the rake on them.
- **PvP match:** net `n_h`. If `n_h > 0`: `floor(n_h × botStakes / otherStakes)`. If `n_h < 0`:
  `floor(n_h × botPayouts / otherPayouts)` (the share of the winners' payouts that went to bots).

**Three stages** (checked after every settlement; the round that crosses a line is always kept — no
clawback, ever):

| Stage | When | Effect |
|-------|------|--------|
| **Adaptive heat** (optional, `bots.adaptiveHeat` true) | the player's rolling poker result vs house bots > +20 BB/100 over ≥ 200 hands | new bots at that player's poker tables are drawn one level up (EASY→NORMAL, NORMAL→HARD) |
| **Word got around** | `botNetToday ≥ cap` | at poker tables where the player sits, EASY and NORMAL house bots leave at the next safe point and only HARD bots sit (the difficulty control shows `…bots.heat_hard_only`); `msg.burmaldaholic.bots.word_got_around` to the table. Chemin de fer and PvP (no skill, −EV for humans) are unaffected. |
| **Sulking** | `botNetToday ≥ bots.sulkMultiplier (2) × cap` | house-funded bots refuse the player until the next MCD: bots at tables where the player sits leave at the next safe point (`msg.burmaldaholic.bots.sulking` + a `sulk` quip); that host's BOTS_ONLY sessions end; new duels/lobbies with bots are refused (`…bots.error.capped`, shows the reset time). Other humans at that table keep their bots once the player leaves. |

- Casino Menu → Wallet shows `…bots.wallet_line` "Winnings from bots today: 3 200 / 5 000".
- Losses are never limited (they are the house edge or the skill gap).
- Ops: `/casino bots heat <player> reset`.

### 5.5 Debt, freezes, owners

- **Asset Freeze** (Peaceful default, §5.6): no wagering at all — no bot rounds either.
- **Active loan or default** (§5.8): bots never loosen or tighten a game's own debtor rule where the
  counterparties are humans (poker allows debtors today and keeps doing so). Where a game **bars**
  debtors because it is a player-to-player transfer (chemin de fer §20.9, player-banked UTH, every PvP
  mode, PVP.md §3.2 rule 3), a debtor may still play **when every counterparty is a house-funded bot**
  and `bots.debtorsMayPlay` (true): punting against a bot bank (never banking), PvP BOTS_ONLY matches
  and duels vs a bot. Never against owner-funded bots (that would be a player-to-player transfer, §5.8
  rule 2). Garnishment applies to winnings as for any credit.
  (Research §3.4.8 suggested barring debtors from bot counterparties entirely; this design allows it
  because house bots are the house, exactly like a house game. Set the key to false for the stricter
  rule.)
- **Owners** never sit at their own tables (§18.2) — so they cannot play their own bankroll bots.
- **Bots have no accounts**: no loans, no VIP, no streak, no Last Chance, no contracts; chaos events
  never target them.

### 5.6 Abuse checklist

| Risk | Rule |
|------|------|
| Farming Easy poker bots | stake gates §4.2; heat stages §5.4; per-table buy-in budget §5.1; weighted VIP §5.3; bots at a stake level need the human's VIP for that level |
| Two humans squeezing bots together (soft-play) | limits per player; bots never collude; multi-human pots are raked |
| Laundering through bots | house bots: money comes from/returns to the bank; owner bots: the owner's own money, and debtors can't touch them |
| Owner pumping bankroll with house money | owned tables only use bankroll bots; bot chips never become rake for an owner (§5.1) |
| Human UTH banker farming bot seats | bots sit out under a human banker (§4.6) |
| Chemmy bot bank vs bot punters | never happens (§4.4) |
| Streak / Golden Hour / cashback farming | none apply to bot rounds (§5.3) |
| Advancement farming | player-counting advancements ignore bots; bot advancements need HARD bots or full tables (§10) |
| "Are the bots rigged?" | bots see only what you see; they use their own RNG; tests §12.1; Rules page line `…bots.rules.fair` |
| Stalling a table with bots | bots never time out and never extend timers |
| Crashing to avoid a loss | unchanged: drawn rounds play out, bots play on (§3.5) |

---

## 6. Table settings: storage and owner controls

### 6.1 Stored per table (block entity on Java; per-table dynamic property on Bedrock, JSON)

```
keeper:        UUID | null                       // set at placement (unowned craftable tables)
defaults:      { policy, count, difficulty, keepFree, chatter, speed, private, guests[≤16] }
limits:        { hostMayChange: bool, maxBots: int, allowPrivate: bool, botsMode }   // owner/keeper
session:       { host, pending?, invites[], claimants[], bots: [{ id, nameId, level, personality,
                 stack, bankEscrow, purse: BANK|BANKROLL }], botRngState }
```
Bedrock keeps the JSON < 2 000 chars per table (6 bots × ~120 chars). The table key is the existing
`ctx.tables` key (`x,y,z,dim` or `npc:<id>`).

### 6.2 Owner and keeper controls

| Control | Values (default) | Who |
|---------|------------------|-----|
| **Bots** | *Off* · *Atmosphere only* (default at owned tables) · *Allowed* (money bots from the bankroll) | owner (at unowned tables: always *Allowed*, bank-funded) |
| **Players may change seating** | on (default) / off | owner, keeper |
| **Max bots** | 0 … seats − 1 (default seats − 1) | owner, keeper |
| **Allow private tables** | on (keeper default) / off (owner default) | owner, keeper |
| Table defaults (policy, count, difficulty, keep-free, chatter, speed, private, guests) | §2.3 | owner, keeper, ops |

*Off* hides the whole bot section for hosts. *Atmosphere only* offers MIXED/BOTS_ONLY only on
atmosphere games; at poker/chemmy it behaves as *Off*.

---

## 7. Presentation

### 7.1 Names and the bot mark

- **Pool:** 32 names with stable ASCII ids (research §4.3), translation keys
  `gui.burmaldaholic.bots.name.<id>` (§11.3), EN and RU (Russian names are Russian jokes, not
  transliterations). The **id** is saved with the seat, so a bot keeps its name across a restart and in
  logs. Rules: no real people (celebrities, streamers, poker pros, Minecraft personalities), no slurs,
  nothing that imitates staff or operators. (The old list's "Diamond Dave" is a real musician's nickname
  and is replaced by "Diamond Dora".)
- **Theme pools:** each id has a theme tag `any`, `piglin` or `ender`. Piglin Parlor tables draw from
  `piglin` + `any` (piglin first), End City lounge tables from `ender` + `any`, others from `any`.
- A table/match never shows two bots with the same name; the id is drawn from the bot RNG without
  replacement among the ids unused at that table.
- **Display form everywhere:** code prepends the **bot glyph U+E190** (a small copper automaton face;
  font sheet `glyph_E1.png` row 9, cell 0; same code point both editions) and uses
  `gui.burmaldaholic.bots.display` = "[BOT] %1$s" / «[БОТ] %1$s». With level:
  `…bots.display_level` = "[BOT] %1$s · %2$s" — seat plates, forms, chat, action bar, lobby lists,
  results. The glyph is never inside a string (UI.md §0.1).
- Poker keeps the flavour words: "Hard (Shark)" / «Сложный (Акула)» via `…bots.level_poker` and the
  existing `gui.burmaldaholic.poker.bot.*` keys.
- Personality is shown in the tooltip / body line (`…bots.personality.<id>` + `.desc`).

### 7.2 Avatars

Seats are virtual in both editions (no chairs); bots need no entity. Config `bots.avatars.mode`:

| Mode | Java | Bedrock |
|------|------|---------|
| `NONE` | seat plates on the screen only | forms and action bar only (**Bedrock default**) |
| `NAMEPLATE` (**Java default**) | a vanilla **text display** entity per bot, non-persistent (never saved; re-created by the table), billboard, 1.6 blocks above a seat point on the table edge: glyph + translated name + level letter + stack; shown only while a player is within `multiplayer.spectatorRadius` | treated as `NONE` (entity name tags cannot be translated) |
| `ENTITY` (NICE) | custom entity `burmaldaholic:bot_avatar` (villager-sized "patron": no AI, invulnerable, no collision, no drops, not saved; reuses the dealer-NPC plumbing) + the nameplate | same entity: no AI, all damage ignored, not persistent (despawned by the table), name tag = glyph U+E190 + seat number only |

- Avatar skins follow the casino theme: a copper automaton by default, a piglin gambler in the Piglin
  Parlor, a pale end-patron in the End lounge (Bedrock: one entity type with 3 variants).
- Emotes (§7.4): `happy_villager` particles on a win, `angry_villager` on a bust, `note` on join,
  `smoke` on leave — at the avatar, or above the table centre without avatars. Java avatars turn their
  head toward the acting player.
- Limits: at most `bots.avatars.maxEntities` (16) avatar entities per world; beyond that new bots get
  none. Avatars never spawn inside blocks (2 air blocks needed at the seat point, else none).

### 7.3 Thinking delays

Per decision, uniform in `[bots.think.minTicks, bots.think.maxTicks]` (20–60 t) from the bot RNG, then
× the speed factor (NORMAL 1, FAST `bots.think.fastFactor` 0.5, INSTANT 0). Per game:

| Where | Delay |
|-------|-------|
| Poker EASY | 10–30 t, **+40 t when E ≥ 0.7** (a deliberate, documented tell for beginners) |
| Poker NORMAL | 20–60 t, never correlated with the hand |
| Poker HARD | 20–60 t + 0–40 t "tank" when to-call > 25 % of stack, never correlated with the hand |
| Blackjack | EASY 15–40 t, NORMAL 10–30 t, HARD 10–25 t per action |
| Atmosphere bets | a random moment in the first 60–160 t of BETTING; always *Ready* |
| Chemmy | bank offer 20–60 t; punter bets in the last 100 t of BETTING |
| PvP | accept a duel 20–60 t; *Spin!/Drop!/Scratch!* 10–30 t; Double-or-nothing 20–60 t |

A bot never uses more than half of the human action timer and never extends a timer. Poker keeps
`poker.botThinkMinTicks/MaxTicks` as its override of the base range (§9.3). Stale decisions are dropped
by a per-table sequence token (existing `botSeq` pattern).

### 7.4 Chatter (quips and emotes)

- **Events** (§11.4 lines, `dialog.burmaldaholic.bots.<event>.N`): `join`, `yield` (gives its seat to a
  human), `leave`, `win_big`, `bust`, `bad_beat`, `fold_to_shove`, `hero_call`, `human_wins`, `all_in`,
  `blackjack`, `seven_out`, `natural`, `bank_take`, `banco`, `pvp_win`, `pvp_loss`, `duel_accept`,
  `duel_decline`, `word_got_around`, `sulk`, `idle`.
- **Rate limits:** an event produces a line with probability `bots.chatter.chance` (0.35; HARD bots
  half of that); per bot at most one line per `bots.chatter.botCooldownTicks` (600); per table one
  line per `bots.chatter.tableCooldownTicks` (200) and at most `bots.chatter.maxPerMinute` (3).
  `yield`, `word_got_around`, `sulk`, `duel_*` always fire (they explain what happens) but queue for
  the table cooldown (≤ 40 t, else dropped).
- **Never reveal hidden information:** lines about a hand fire only after the showdown / reveal.
- **Delivery:** chat `msg.burmaldaholic.bots.say` ("%1$s: %2$s" = nested bot display name + nested
  dialog line) to
  seated humans and players within `multiplayer.spectatorRadius` who have not muted bot chatter (Casino
  Menu → Settings *Bot chatter*, default on); table toggle *Bot chatter* mutes the table. A soft "tin
  voice" at the table (`entity.villager.ambient`, pitch 1.4, volume 0.4) and the emote particles.
- Lines are fixed and translated (no free text). Lines that name the human use `%1$s`; RU lines never
  make a name the subject of a past-tense verb (STRINGS.md rule).
- `idle` fires only if no human acted for 30 s, at most once per 5 minutes per table.

### 7.5 Performance limits

| Limit | Default | Behaviour when hit |
|-------|---------|--------------------|
| `bots.maxActiveTables` (tables with bots, whole world) | 24 (Java) / 12 (Bedrock) — edition default | new sessions get no bots; the table shows `…bots.none_available` |
| `bots.maxActive` (bots, whole world) | 64 (Java) / 32 (Bedrock) | same |
| Bots per table | seats − 1; atmosphere caps §4.7; PvP `bots.pvp.maxPerMatch` | — |
| Heavy jobs (poker Monte-Carlo, UTH river enumeration) | Java inline ≤ 1 000 evaluations per job, else split across ticks; Bedrock ≤ `bots.maxConcurrentJobs` (2) `system.runJob` generators per world, yielding every 25 samples | jobs wait in a FIFO; §4.3 fallback at the deadline |
| Avatar entities | 16 | §7.2 |
| Chat lines | §7.4 | queued / dropped |
| Bedrock action-bar updates | ≥ 2 t apart per player (PVP.md §14) | coalesced |

Bots are created only inside sessions (or showcase §4.9) and have no tick of their own: the table's
state machine drives them. A session with no human within 64 blocks for 1 200 t ends (humans offline or
away count as left). An exception inside bot code → log + the game's safe default action (fold / check
/ stand / pass), as today.

---

## 8. UI (both editions)

### 8.1 Table header and seat markers (all multi-seat games)

- Header line (Java under the title; Bedrock first body line):
  `…bots.summary.<policy>` → "Humans + 3 bots · Mixed · Private" / «Люди + 3 бота · Вперемешку ·
  Закрытый».
  Pending changes add `…bots.pending`.
- **Java seat plate:** bot glyph + name + level badge `[E]`/`[N]`/`[H]` (letter on green/yellow/red) +
  stack (money bots) or "virtual" chip icon outline (atmosphere). Tooltip: level word, personality
  line, "Leaves after this round" when yielding. Reserved seats show "Reserved for Alex".
- **Bedrock:** seat lines in bodies `…bots.seat_line` = "Seat %1$s: %2$s · %3$s · %4$s"
  (seat, nested "[BOT] name", level, stack); action-bar actions keep the [BOT] tag ("[BOT] Creeper42 raises to 60").
- Atmosphere bets on Java layouts are drawn with a hatched chip (so nobody thinks those chips are
  real); Bedrock lists them under "Bots bet (for fun):" in the round summary.

### 8.2 Java: Table settings screen (256 × 200, compact same)

Opened by the `[⚙]` button at the top-right of every table screen (enabled for host/keeper/owner/op;
others get the read-only view) or `/casino table settings`.
```
 Table settings — Texas Hold'em (Low)                         Host: Alex
 ─────────────────────────────────────────────────────────────────────
 Players:     ( Humans only ) (●Humans + bots ) ( Just me and bots )
 Bots:        [-]  3  [+]          max 5
 Difficulty:  ( Easy ) ( Normal ) ( Hard ) (●Mixed )   ("Style" at chemin de fer; hidden in luck games)
 [✔] Keep a seat free for walk-ins      [✔] Bot chatter
 Bot speed:   (●Normal ) ( Fast ) ( Instant )        (only "Just me and bots")
 ─────────────────────────────────────────────────────────────────────
 [✔] Private table       Invited: Bob, Steve        [Invite…] [Manage…]
 ─────────────────────────────────────────────────────────────────────
 (keeper/owner/op only)  [Save as table defaults]
 [Save — applies from the next round]                        [Cancel]
```
- Disabled options show the reason as a tooltip (`…bots.error.others_seated`,
  `…bots.error.owner_off`, `…bots.error.owner_locked`, `…bots.error.host_locked`,
  `…bots.error.private_forbidden`, `…bots.error.easy_stake`, `…bots.heat_hard_only`).
- *Invite…* lists players within `bots.private.inviteRadius` with an [Invite] button each; *Manage…*
  lists invited players with [Remove].
- Radio groups are rows of toggle buttons (auto-width, RU at 1.45 ×, UI.md §0.1); if a row overflows it
  wraps.

### 8.3 Bedrock: forms

- Every table hub ActionForm gains **Table settings…** (icon: gear) for host/keeper/owner/op, and
  **Private table…** for the same roles. Non-hosts see **Table info** (MessageForm with the summary
  lines). RU labels ≤ 24 characters: «Настройки стола…» (16), «Закрытый стол…» (14).
- **Table settings** — ModalForm:
  1. dropdown *Players*: Humans only · Humans + bots · Just me and bots
  2. slider *Bots* 0 … max (step 1)
  3. dropdown *Bot difficulty*: Easy · Normal · Hard · Mixed (*Bot style*: Wild · Steady · Cool-headed ·
     Mixed at chemin de fer; omitted where bots have no decisions)
  4. toggle *Keep a seat free for walk-ins*
  5. toggle *Bot chatter*
  6. dropdown *Bot speed*: Normal · Fast · Instant (effective only with "Just me and bots"; the label
     says so)
  7. toggle *Save as table defaults* (keeper/owner/op only)
  submit *Save*. Invalid combinations are fixed by the server and the table form re-shows with a
  first body line explaining (e.g. `…bots.error.others_seated`).
- **Private table** — ActionForm: body "Private: on · Invited: Bob, Steve"; buttons *Make private* /
  *Make public* · *Invite a player…* (ModalForm dropdown of players within the invite radius; submit
  *Invite*) · *Remove an invite…* (dropdown of invited) · *Back*.
- **Let them in** (BOTS_ONLY, someone asked to sit): the host's next table form shows a first button
  *Let Bob in (switch to Humans + bots)*. Chat also tells the host.
- Invitees get chat only (no pushed form, PVP.md §3.11.1 rule).

### 8.4 Casino Card on a player

Java `UseEntityCallback` / Bedrock `playerInteractWithEntity` with the Casino Card while seated as host
at a table that allows private: MessageForm / Java confirm screen "Invite Bob to your Blackjack table?"
[Invite] [Cancel]. If not host: nothing (the card's normal use continues). Lucky Coin keeps its PvP
duel meaning (PVP.md §3.3.1).

### 8.5 Charter (owner)

Tables tab, per table (Java row / Bedrock ModalForm): dropdown *Bots* (Off / Atmosphere only /
Allowed), toggle *Players may change seating*, slider *Max bots*, toggle *Allow private tables*,
button *Table defaults…* (the §8.2/§8.3 form in defaults mode). Overview adds *Bot stacks out* and
*Bot results today* lines.

### 8.6 PvP set-up and hub

- Set-up forms (PVP.md §4.5, §5.5, §6.5, §7.4, §8.5) gain *Opponents* (dropdown) and, when bots are
  chosen, *Bots* (slider) and *Bot style* (only Coin Flip Duel and Wheel Party).
- PvP hub gains *Play vs bots…* → ActionForm of modes → the mode's set-up with *Opponents* preset to
  bots. Lobby views list bots with the [BOT] tag and level badge; Wheel Party slices of bots are drawn
  with a diagonal hatch on Java.

### 8.7 Commands (Java, permission 0 unless noted)

`/casino table settings` · `/casino table private on|off` · `/casino table invite <player>` ·
`/casino table uninvite <player>` · `/casino table bots <humans|mixed|bots> [count] [easy|normal|hard|mixed]`
— act on the table the player is seated at (host), or the table block they look at within 5 blocks
(keeper/owner; ops at any). Ops (level 2): `/casino bots list` (active bots, table, purse, stack),
`/casino bots clear <table|all>` (bots leave at the next safe point), `/casino bots heat <player> reset`.
Bedrock ops:
`/scriptevent burmaldaholic:bots list|clear [all]` and Casino Card → Admin → Bots.

---

## 9. Config keys (`CONFIG.md` format; section `## bots`)

Percent-like values are fractions unless the key ends in `Percent` (CONFIG.md rule). `<game>` ∈
`poker`, `chemmy`, `blackjack`, `roulette`, `craps`, `baccarat`, `uth`.

### 9.1 Core

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.enabled` | bool | true | — | Master switch. Off → every table behaves as `HUMANS_ONLY`; seated bots leave at the next safe point. |
| `bots.maxActiveTables` | int | 24 (Java) / 12 (Bedrock) | 0–256 | Tables with bots at the same time, whole world (edition default, §7.5). |
| `bots.maxActive` | int | 64 (Java) / 32 (Bedrock) | 0–512 | Bots at the same time, whole world. |
| `bots.maxConcurrentJobs` | int | 2 | 1–16 | Heavy bot jobs (Monte-Carlo, UTH river) running at once (Bedrock `runJob`; Java splits jobs over ticks above it). |
| `bots.difficultyMix` | list<int> | [30, 50, 20] | each 0–100 | Easy/Normal/Hard % for MIXED outside poker. Normalized. |
| `bots.think.minTicks` | int | 20 | 0–200 | Base think delay (§7.3). |
| `bots.think.maxTicks` | int | 60 | 0–400 | Must be ≥ min (else clamped). |
| `bots.think.tankTicks` | int | 40 | 0–200 | Extra "tank" for HARD poker bots on big decisions (+0–this). |
| `bots.think.fastFactor` | double | 0.5 | 0.0–1.0 | Delay multiplier for speed FAST. |
| `bots.personalities` | bool | true | — | Off → every bot is TAG-like at its level (no personality modifiers, plain styles). |
| `bots.keepFreeSeatDefault` | bool | true | — | Default of the per-table *Keep a seat free* toggle. |
| `bots.showcase.enabled` | bool | false | — | NICE: worldgen tables play virtual bot rounds while watched (§4.9). |

### 9.2 Seating defaults per game (family)

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.table.<game>.policy` | enum(HUMANS_ONLY, MIXED, BOTS_ONLY) | matrix below | — | Default policy of a newly placed (craftable) table. |
| `bots.table.<game>.count` | int | matrix below | 0–8 | Default bot count (clamped to seats − 1 and the atmosphere cap). |
| `bots.table.<game>.difficulty` | enum(EASY, NORMAL, HARD, MIXED) | matrix below | — | Default difficulty / style. |
| `bots.table.<game>.worldgenPolicy` | enum(HUMANS_ONLY, MIXED, BOTS_ONLY) | matrix below | — | Default for tables generated in structures (BOTS_ONLY not allowed → clamped to MIXED). |
| `bots.table.<game>.worldgenCount` | int | matrix below | 0–8 | |

| `<game>` | policy | count | difficulty | worldgenPolicy | worldgenCount |
|----------|--------|-------|------------|----------------|---------------|
| `poker` | MIXED | 5 | MIXED | MIXED | 3 (Parlor preset) |
| `chemmy` | MIXED | 2 | MIXED | MIXED | 2 |
| `blackjack` | HUMANS_ONLY | 0 | NORMAL | MIXED | 2 |
| `roulette` | HUMANS_ONLY | 0 | MIXED | MIXED | 3 |
| `craps` | HUMANS_ONLY | 0 | MIXED | MIXED | 2 |
| `baccarat` | HUMANS_ONLY | 0 | MIXED | MIXED | 2 |
| `uth` | HUMANS_ONLY | 0 | NORMAL | MIXED | 2 |

### 9.3 Games

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.atmosphere.maxPerTable.blackjack` | int | 2 | 0–4 | Atmosphere bot cap (§4.7). |
| `bots.atmosphere.maxPerTable.uth` | int | 2 | 0–5 | |
| `bots.atmosphere.maxPerTable.roulette` | int | 3 | 0–7 | |
| `bots.atmosphere.maxPerTable.craps` | int | 3 | 0–5 | |
| `bots.atmosphere.maxPerTable.baccarat` | int | 3 | 0–6 | |
| `bots.poker.easyMaxStake` | enum(MICRO, LOW, MID, HIGH) | LOW | — | Highest stake level where EASY bots may sit (§4.2). |
| `bots.chemmy.bankCapMultiple` | int | 50 | 5–1000 | A bot bank is at most this × table min (and ≤ the table max coverage). |
| `bots.craps.canShoot` | bool | false | — | Bots join the shooter rotation (roll after 20–40 t). |
| `bots.pvp.fillDelayTicks` | int | 400 | 0–1800 | MIXED lobbies: bots fill after this long without a human joiner. |
| `bots.pvp.maxPerMatch` | int | 3 | 1–7 | Max bots in one PvP match. |
| `bots.tournament.maxFill` | int | 8 | 0–31 | Max bot fillers per tournament. |
| `bots.tournament.fillToBracket` | bool | true | — | Knockout: fill to the next power of two instead of byes. |
| `poker.botsEnabled` | bool | true | — | **Legacy alias**: false forces poker tables to HUMANS_ONLY. |
| `poker.botThinkMinTicks` / `poker.botThinkMaxTicks` | int | 20 / 60 | 0–200 / 0–400 | Poker override of the base think range (EASY/HARD adjustments of §7.3 still apply). |
| `poker.bot.regularSamples` | int | **300** (was 200) | 50–5000 | NORMAL Monte-Carlo samples. |
| `poker.bot.sharkSamples` | int | **700** (was 500) | 50–5000 | HARD Monte-Carlo samples. |
| `poker.botMix.micro` / `.low` / `.mid` / `.high` | list<int> | **[45,45,10] / [35,50,15] / [10,55,35] / [0,45,55]** | each 0–100 | Easy/Normal/Hard % (§4.2). Easy is forced to 0 above `bots.poker.easyMaxStake`. |

### 9.4 Economy and abuse

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.owned.funding` | enum(OWNER_BANKROLL, DISABLED) | OWNER_BANKROLL | — | Money bots at owned casinos: paid by the bankroll, or not allowed (§5.1). |
| `bots.tableBuyInsPerDay` | int | 10 | 0–1000 | House-funded bot buy-ins/banks per table per MCD. 0 = unlimited. |
| `bots.vipWagerWeight` | double | 0.5 | 0.0–1.0 | VIP / `wager` contract credit for chips matched by bots (§5.3). |
| `bots.dailyWinCapMin` | int | 500 | 0–10⁹ | Minimum daily heat threshold (§5.4). |
| `bots.dailyWinCapTierMultiple` | int | 5 | 0–1000 | Threshold = max(min, this × tier max). 0 disables heat. |
| `bots.sulkMultiplier` | double | 2.0 | 1.0–100.0 | Bots refuse the player at this × threshold. |
| `bots.adaptiveHeat` | bool | true | — | Winning players (> +20 BB/100 over ≥ 200 hands vs house bots) get stronger bots. |
| `bots.debtorsMayPlay` | bool | true | — | Debtors may play when every counterparty is a house-funded bot (§5.5). |

### 9.5 Presentation, private tables

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.avatars.mode` | enum(NONE, NAMEPLATE, ENTITY) | NAMEPLATE (Java) / NONE (Bedrock) | — | §7.2. Bedrock treats NAMEPLATE as NONE. |
| `bots.avatars.maxEntities` | int | 16 | 0–128 | Avatar entities per world. |
| `bots.chatter.enabled` | bool | true | — | Server-wide switch for quips (tables and players can mute too). |
| `bots.chatter.chance` | double | 0.35 | 0.0–1.0 | Chance an event produces a line (HARD bots: half). |
| `bots.chatter.botCooldownTicks` | int | 600 | 0–24 000 | |
| `bots.chatter.tableCooldownTicks` | int | 200 | 0–24 000 | |
| `bots.chatter.maxPerMinute` | int | 3 | 0–60 | Per table. |
| `bots.private.enabled` | bool | true | — | Private tables and invite-only lobbies. |
| `bots.private.maxInvites` | int | 16 | 1–64 | |
| `bots.private.inviteRadius` | int | 64 | 0–1024 | Invite dropdown radius; 0 = any online player. |

---

## 10. Advancements (append to GAME_DESIGN §19)

Java: "Burmaldaholic" tab; Bedrock: Achievements page, same keys.

| Id | Parent | Condition | Frame |
|----|--------|-----------|-------|
| `man_vs_machine` | `beginners_luck` | Win a poker pot at showdown against at least one HARD bot | task |
| `clean_sweep` | `shark_hunter` | In one BOTS_ONLY poker session, take every chip from 3 bots of NORMAL or HARD level | challenge |
| `word_got_around` | `man_vs_machine` | Reach the "Word got around" heat stage (§5.4) | goal |
| `short_circuit` | `banco` | Call Banco against a bot banker at chemin de fer and win that coup | goal |
| `members_only` | `root` | Play a round at a private table together with a player you invited | task |
| `no_robots` | `members_only` | Play a round at a `HUMANS_ONLY` table with at least 4 humans seated | goal |

Existing `shark_hunter` keeps its id and trigger (bust a HARD poker bot); description string updated
(§11.6). `royal_flush` counts pots against bots (§5.3).

---

## 11. Strings (EN / RU, `STRINGS.md` format; new section `## bots`)

Rules of `STRINGS.md` apply (generator-parsed rows, no `|` or line breaks in values, positional
placeholders, `(plural)` and `(variants N)` groups, RU never makes a player name the subject of a
past-tense verb). Additional rule for this module: **RU bot lines never use a first-person past tense
verb** (bots have male and female names; Russian past tense is gendered). Arguments: `name` = player
name; `bot` = nested `gui.burmaldaholic.bots.display` component; `level` = nested
`gui.burmaldaholic.bots.level.*`; `bots` = nested plural `unit.burmaldaholic.bot`; `game` = nested
`gui.burmaldaholic.common.game.*`.

### 11.0 Glossary (LOCALIZATION.md §6.9 "Seats & Bots")

| EN | RU | Notes |
|----|----|-------|
| Bot | бот | tag «[БОТ]»; «бот-банкир», «бот-акула» |
| Seat policy / Players | состав стола / «Кто играет» | |
| Humans only | Только люди | |
| Humans + bots | Люди и боты | |
| Just me and bots | Только я и боты | |
| Host (of a table) | хозяин стола | not «хост»; «ведущий» is for shows |
| Private table | закрытый стол | "public" → открытый |
| Invite | приглашение / пригласить | |
| Difficulty (bots) | уровень | levels: Лёгкий, Нормальный, Сложный, Вперемешку (masculine, agree with «бот») |
| Style (bots) | стиль | Wild / Steady / Cool-headed → Азартный / Ровный / Хладнокровный |
| Personality | характер | Rock «Скала», Calling Station «Колл-станция», Maniac «Маньяк», TAG «Тайт-агрессор», LAG «Луз-агрессор» |
| Walk-in | новый игрок | "keep a seat free for walk-ins" → «держать место для новых игроков» |
| Tell (poker) | «тел» (подсказка) | in the Rules tip only |
| Heat / word got around | слухи расходятся | |
| Sulking | обиделись | «Боты обиделись» |

### 11.1 Units

| Key | EN | RU |
|-----|----|----|
| `unit.burmaldaholic.bot.p1` | %1$s bot | %1$s бот |
| `unit.burmaldaholic.bot.p21` | %1$s bots | %1$s бот |
| `unit.burmaldaholic.bot.p2` | %1$s bots | %1$s бота |
| `unit.burmaldaholic.bot.p5` | %1$s bots | %1$s ботов |

### 11.2 UI

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.bots.display` | [BOT] %1$s | [БОТ] %1$s |
| `gui.burmaldaholic.bots.display_level` | [BOT] %1$s · %2$s | [БОТ] %1$s · %2$s |
| `gui.burmaldaholic.bots.level_poker` | %1$s (%2$s) | %1$s (%2$s) |
| `gui.burmaldaholic.bots.level.easy` | Easy | Лёгкий |
| `gui.burmaldaholic.bots.level.normal` | Normal | Нормальный |
| `gui.burmaldaholic.bots.level.hard` | Hard | Сложный |
| `gui.burmaldaholic.bots.level.mixed` | Mixed | Вперемешку |
| `gui.burmaldaholic.bots.level.easy.short` | E | Л |
| `gui.burmaldaholic.bots.level.normal.short` | N | Н |
| `gui.burmaldaholic.bots.level.hard.short` | H | С |
| `gui.burmaldaholic.bots.style.easy` | Wild | Азартный |
| `gui.burmaldaholic.bots.style.normal` | Steady | Ровный |
| `gui.burmaldaholic.bots.style.hard` | Cool-headed | Хладнокровный |
| `gui.burmaldaholic.bots.style.mixed` | Mixed | Вперемешку |
| `gui.burmaldaholic.bots.personality.rock` | Rock | Скала |
| `gui.burmaldaholic.bots.personality.rock.desc` | rarely bluffs | блефует редко |
| `gui.burmaldaholic.bots.personality.station` | Calling Station | Колл-станция |
| `gui.burmaldaholic.bots.personality.station.desc` | calls everything | коллирует всё подряд |
| `gui.burmaldaholic.bots.personality.maniac` | Maniac | Маньяк |
| `gui.burmaldaholic.bots.personality.maniac.desc` | raises everything | рейзит всё подряд |
| `gui.burmaldaholic.bots.personality.tag` | Tight-Aggressive | Тайт-агрессор |
| `gui.burmaldaholic.bots.personality.tag.desc` | plays by the book | играет по учебнику |
| `gui.burmaldaholic.bots.personality.lag` | Loose-Aggressive | Луз-агрессор |
| `gui.burmaldaholic.bots.personality.lag.desc` | keeps up the pressure | давит без передышки |
| `gui.burmaldaholic.bots.personality_line` | %1$s — %2$s | %1$s — %2$s |
| `gui.burmaldaholic.bots.betstyle.roulette.rock` | Red Lover | Любитель красного |
| `gui.burmaldaholic.bots.betstyle.roulette.station` | Dozen Grinder | Дюжинщик |
| `gui.burmaldaholic.bots.betstyle.roulette.maniac` | Sprinkler | Разбрасыватель |
| `gui.burmaldaholic.bots.betstyle.roulette.tag` | Lucky Number | Счастливое число |
| `gui.burmaldaholic.bots.betstyle.roulette.lag` | Martingale Fan | Фанат мартингейла |
| `gui.burmaldaholic.bots.betstyle.craps.rock` | Dark Side | Тёмная сторона |
| `gui.burmaldaholic.bots.betstyle.craps.station` | Field Fan | Любитель филда |
| `gui.burmaldaholic.bots.betstyle.craps.maniac` | Come Ladder | Лестница кам-ставок |
| `gui.burmaldaholic.bots.betstyle.craps.tag` | Right-Way Grinder | Честный пасс-лайн |
| `gui.burmaldaholic.bots.betstyle.craps.lag` | Pass and Field | Пасс и филд |
| `gui.burmaldaholic.bots.betstyle.baccarat.rock` | Banker Only | Только Банкир |
| `gui.burmaldaholic.bots.betstyle.baccarat.station` | Trend Follower | Идёт за серией |
| `gui.burmaldaholic.bots.betstyle.baccarat.maniac` | Tie Hunter | Охотник за ничьей |
| `gui.burmaldaholic.bots.betstyle.baccarat.tag` | Banker, No Frills | Банкир без изысков |
| `gui.burmaldaholic.bots.betstyle.baccarat.lag` | Chop Chaser | Против серии |
| `gui.burmaldaholic.bots.settings.open` | Table settings… | Настройки стола… |
| `gui.burmaldaholic.bots.settings.info` | Table info | Об этом столе |
| `gui.burmaldaholic.bots.settings.title` | Table settings — %1$s | Настройки стола — %1$s |
| `gui.burmaldaholic.bots.settings.defaults_title` | Table defaults — %1$s | Настройки по умолчанию — %1$s |
| `gui.burmaldaholic.bots.settings.host` | Host: %1$s | Хозяин стола: %1$s |
| `gui.burmaldaholic.bots.settings.players` | Players | Кто играет |
| `gui.burmaldaholic.bots.policy.humans_only` | Humans only | Только люди |
| `gui.burmaldaholic.bots.policy.mixed` | Humans + bots | Люди и боты |
| `gui.burmaldaholic.bots.policy.bots_only` | Just me and bots | Только я и боты |
| `gui.burmaldaholic.bots.settings.count` | Bots | Боты |
| `gui.burmaldaholic.bots.settings.count_max` | max %1$s | макс. %1$s |
| `gui.burmaldaholic.bots.settings.difficulty` | Bot difficulty | Уровень ботов |
| `gui.burmaldaholic.bots.settings.style` | Bot style | Стиль ботов |
| `gui.burmaldaholic.bots.settings.keep_free` | Keep a seat free for walk-ins | Держать место для новых игроков |
| `gui.burmaldaholic.bots.settings.chatter` | Bot chatter | Болтовня ботов |
| `gui.burmaldaholic.bots.settings.speed` | Bot speed | Темп ботов |
| `gui.burmaldaholic.bots.speed.normal` | Normal | Обычный |
| `gui.burmaldaholic.bots.speed.fast` | Fast | Быстрый |
| `gui.burmaldaholic.bots.speed.instant` | Instant | Мгновенный |
| `gui.burmaldaholic.bots.settings.speed_hint` | Fast and Instant work only in Just me and bots | Быстрый и мгновенный темп — только в режиме «Только я и боты» |
| `gui.burmaldaholic.bots.settings.save_defaults` | Save as table defaults | Сохранить как настройки стола |
| `gui.burmaldaholic.bots.settings.save` | Save — applies from the next round | Сохранить — со следующего раунда |
| `gui.burmaldaholic.bots.settings.save_short` | Save | Сохранить |
| `gui.burmaldaholic.bots.summary.humans_only` | Humans only · %1$s | Только люди · %1$s |
| `gui.burmaldaholic.bots.summary.mixed` | Humans + %1$s · %2$s · %3$s | Люди + %1$s · %2$s · %3$s |
| `gui.burmaldaholic.bots.summary.bots_only` | %1$s + %2$s · %3$s · private | %1$s + %2$s · %3$s · закрытый |
| `gui.burmaldaholic.bots.summary.private` | Private | Закрытый |
| `gui.burmaldaholic.bots.summary.open` | Open to all | Открытый |
| `gui.burmaldaholic.bots.pending` | New settings from the next round | Новые настройки — со следующего раунда |
| `gui.burmaldaholic.bots.seat_line` | Seat %1$s: %2$s · %3$s · %4$s | Место %1$s: %2$s · %3$s · %4$s |
| `gui.burmaldaholic.bots.seat_reserved` | Reserved for %1$s | Занято для игрока %1$s |
| `gui.burmaldaholic.bots.seat_leaving` | Leaves after this round | Уйдёт после раунда |
| `gui.burmaldaholic.bots.thinking` | Thinking… | Думает… |
| `gui.burmaldaholic.bots.watching` | Watching | Наблюдает |
| `gui.burmaldaholic.bots.virtual_bets` | Bots bet (for fun): %1$s | Ставки ботов (понарошку): %1$s |
| `gui.burmaldaholic.bots.virtual_tooltip` | Bot chips here are not real: they never win or lose anyone's money | Фишки ботов здесь ненастоящие: они не выигрывают и не проигрывают ничьих денег |
| `gui.burmaldaholic.bots.luck_only` | Luck only — bots play exactly like you | Только удача — боты играют так же, как вы |
| `gui.burmaldaholic.bots.uth_edge` | Costs this bot about %1$s of the Ante | Обходится этому боту примерно в %1$s от анте |
| `gui.burmaldaholic.bots.heat_hard_only` | Only Hard bots will play you today | Сегодня с вами играют только сложные боты |
| `gui.burmaldaholic.bots.wallet_line` | Winnings from bots today: %1$s / %2$s | Выигрыш у ботов сегодня: %1$s / %2$s |
| `gui.burmaldaholic.bots.stats_line` | Vs bots: %1$s rounds · net %2$s | С ботами: раундов %1$s · итог %2$s |
| `gui.burmaldaholic.bots.rules.fair` | Bots see only what you see and never touch the shuffle, the dice or the wheel. | Боты видят только то, что видите вы, и никак не влияют на тасовку, кости и колесо. |
| `gui.burmaldaholic.bots.rules.tell` | Tip: Easy bots think longer when they hold a strong hand. | Подсказка: лёгкие боты думают дольше, когда у них сильная рука. |
| `gui.burmaldaholic.bots.rules.money` | Bot rounds: half VIP credit, no cashback, no Golden Hour bonus, no streak. | Игра с ботами: оборот для ВИП засчитывается наполовину, без кешбэка, без бонуса «Золотого часа» и без серий. |
| `gui.burmaldaholic.bots.private.open` | Private table… | Закрытый стол… |
| `gui.burmaldaholic.bots.private.toggle` | Private table | Закрытый стол |
| `gui.burmaldaholic.bots.private.status` | Private: %1$s · Invited: %2$s | Закрытый: %1$s · Приглашены: %2$s |
| `gui.burmaldaholic.bots.private.yes` | yes | да |
| `gui.burmaldaholic.bots.private.no` | no | нет |
| `gui.burmaldaholic.bots.private.make_private` | Make private | Сделать закрытым |
| `gui.burmaldaholic.bots.private.make_public` | Make public | Сделать открытым |
| `gui.burmaldaholic.bots.private.invite` | Invite a player… | Пригласить игрока… |
| `gui.burmaldaholic.bots.private.invite_submit` | Invite | Пригласить |
| `gui.burmaldaholic.bots.private.uninvite` | Remove an invite… | Отозвать приглашение… |
| `gui.burmaldaholic.bots.private.uninvite_submit` | Remove | Отозвать |
| `gui.burmaldaholic.bots.private.manage` | Invited… | Приглашённые… |
| `gui.burmaldaholic.bots.private.none_invited` | Nobody invited yet | Пока никто не приглашён |
| `gui.burmaldaholic.bots.private.none_nearby` | No players nearby to invite | Рядом нет игроков для приглашения |
| `gui.burmaldaholic.bots.private.confirm` | Invite %1$s to your %2$s table? | Пригласить игрока %1$s за ваш стол (%2$s)? |
| `gui.burmaldaholic.bots.let_in` | Let %1$s in (Humans + bots) | Пустить: %1$s («Люди и боты») |
| `gui.burmaldaholic.bots.pvp.opponents` | Opponents | Соперники |
| `gui.burmaldaholic.bots.pvp.a_player` | A player | Игрок |
| `gui.burmaldaholic.bots.pvp.a_bot` | A bot | Бот |
| `gui.burmaldaholic.bots.pvp.play_vs_bots` | Play vs bots… | Игра с ботами… |
| `gui.burmaldaholic.bots.pvp.fill_now` | Fill with bots | Добавить ботов |
| `gui.burmaldaholic.bots.charter.bots` | Bots | Боты |
| `gui.burmaldaholic.bots.charter.mode.off` | Off | Выкл. |
| `gui.burmaldaholic.bots.charter.mode.atmosphere` | Atmosphere only | Только для атмосферы |
| `gui.burmaldaholic.bots.charter.mode.allowed` | Allowed (paid from the bankroll) | Разрешены (за счёт кассы) |
| `gui.burmaldaholic.bots.charter.host_may_change` | Players may change seating | Игроки могут менять состав |
| `gui.burmaldaholic.bots.charter.max_bots` | Max bots | Макс. ботов |
| `gui.burmaldaholic.bots.charter.allow_private` | Allow private tables | Разрешить закрытые столы |
| `gui.burmaldaholic.bots.charter.defaults` | Table defaults… | Настройки по умолчанию… |
| `gui.burmaldaholic.bots.charter.stacks_out` | Bot stacks out: %1$s | Фишки у ботов: %1$s |
| `gui.burmaldaholic.bots.charter.results_today` | Bot results today: %1$s | Итог ботов за сегодня: %1$s |
| `gui.burmaldaholic.bots.charter.bankroll_short` | Bots can't afford a seat: the bankroll is too low | Ботам не на что сесть: в кассе мало денег |
| `gui.burmaldaholic.menu.settings.bot_chatter` | Bot chatter | Болтовня ботов |
| `gui.burmaldaholic.bots.admin.title` | Bots | Боты |
| `gui.burmaldaholic.bots.admin.line` | %1$s at %2$s · %3$s · %4$s | %1$s — %2$s · %3$s · %4$s |
| `gui.burmaldaholic.bots.admin.clear_all` | Send all bots home | Отправить ботов домой |
| `gui.burmaldaholic.bots.admin.reset_heat` | Reset a player's bot limits… | Сбросить лимиты игрока… |

### 11.3 Bot names (`gui.burmaldaholic.bots.name.<id>`; theme in the comment column is data for the code)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.bots.name.lucky_steve` | Lucky Steve | Везунчик Стив |
| `gui.burmaldaholic.bots.name.grandpa_pavel` | Grandpa Pavel | Дед Павел |
| `gui.burmaldaholic.bots.name.creeper42` | Creeper42 | Крипер42 |
| `gui.burmaldaholic.bots.name.mr_blocksworth` | Mr. Blocksworth | Мистер Блоксворт |
| `gui.burmaldaholic.bots.name.diamond_dora` | Diamond Dora | Алмазная Дора |
| `gui.burmaldaholic.bots.name.aunt_zoya` | Aunt Zoya | Тётя Зоя |
| `gui.burmaldaholic.bots.name.redstone_rick` | Redstone Rick | Редстоун Рик |
| `gui.burmaldaholic.bots.name.emerald_emma` | Emerald Emma | Изумрудная Эмма |
| `gui.burmaldaholic.bots.name.sir_oinksalot` | Sir Oinksalot | Сэр Хрюкенс |
| `gui.burmaldaholic.bots.name.baba_valya` | Baba Valya | Баба Валя |
| `gui.burmaldaholic.bots.name.uncle_grisha` | Uncle Grisha | Дядя Гриша |
| `gui.burmaldaholic.bots.name.kuzmich` | Old Kuzmich | Кузьмич |
| `gui.burmaldaholic.bots.name.cobble_carl` | Cobblestone Carl | Булыжник Карл |
| `gui.burmaldaholic.bots.name.slime_sam` | Slimy Sam | Слизняк Сэм |
| `gui.burmaldaholic.bots.name.brewing_bella` | Brewing Bella | Зельеварка Белла |
| `gui.burmaldaholic.bots.name.captain_boat` | Captain Boat | Капитан Лодка |
| `gui.burmaldaholic.bots.name.bee_bea` | Bea the Beekeeper | Пчеловод Беа |
| `gui.burmaldaholic.bots.name.torch_tanya` | Torch Tanya | Таня-Факел |
| `gui.burmaldaholic.bots.name.axolotl_al` | Axolotl Al | Аксолотль Ал |
| `gui.burmaldaholic.bots.name.lady_luckless` | Lady Luckless | Леди Невезуха |
| `gui.burmaldaholic.bots.name.iron_ivan` | Iron Ivan | Железный Иван |
| `gui.burmaldaholic.bots.name.nether_nick` | Nether Nick | Незер Ник |
| `gui.burmaldaholic.bots.name.goldie_nuggets` | Goldie Nuggets | Голди Самородок |
| `gui.burmaldaholic.bots.name.piglin_pete` | Piglin Pete | Пиглин Петя |
| `gui.burmaldaholic.bots.name.bartering_boris` | Bartering Boris | Борис-Бартер |
| `gui.burmaldaholic.bots.name.madame_crimson` | Madame Crimson | Мадам Багрянка |
| `gui.burmaldaholic.bots.name.tusk_tony` | Tony Tusks | Тони Клык |
| `gui.burmaldaholic.bots.name.enderman_ed` | Enderman Ed | Эндермен Эд |
| `gui.burmaldaholic.bots.name.madame_ender` | Madame Ender | Мадам Эндер |
| `gui.burmaldaholic.bots.name.shulker_shura` | Shulker Shura | Шалкер Шура |
| `gui.burmaldaholic.bots.name.pearl_polly` | Pearly Polly | Жемчужная Полли |
| `gui.burmaldaholic.bots.name.void_viktor` | Viktor the Void | Виктор Пустота |

Themes (code constant, not strings): `any` = lucky_steve … iron_ivan (21 ids); `piglin` = nether_nick,
goldie_nuggets, piglin_pete, bartering_boris, madame_crimson, tusk_tony (6); `ender` = enderman_ed,
madame_ender, shulker_shura, pearl_polly, void_viktor (5). Legacy saved ids from the old poker list
map to the nearest new id (`diamond_dave` → `diamond_dora`).

### 11.4 Quips (`dialog.burmaldaholic.bots.<event>.N`, variants; `%1$s` = human name where used)

| Key | EN | RU |
|-----|----|----|
| `dialog.burmaldaholic.bots.join.1` | Deal me in. I brought my lucky redstone. | Сдавайте. Я со своим счастливым редстоуном. |
| `dialog.burmaldaholic.bots.join.2` | Evening, everyone. Chips are warm, cards are cold. | Всем добрый вечер. Фишки тёплые, карты холодные. |
| `dialog.burmaldaholic.bots.join.3` | Beep boop. Just kidding. Or am I? | Бип-буп. Шучу. Или нет? |
| `dialog.burmaldaholic.bots.join.4` | Mind if I sit? The villagers won't let me into their game. | Не возражаете? Жители не пускают меня в свою игру. |
| `dialog.burmaldaholic.bots.yield.1` | Take my seat, %1$s. Humans first, house rules. | Садитесь, %1$s. Люди вперёд — правила заведения. |
| `dialog.burmaldaholic.bots.yield.2` | My shift is over. Good luck, %1$s! | Моя смена окончена. Удачи, %1$s! |
| `dialog.burmaldaholic.bots.yield.3` | The seat is warm for you, %1$s. Don't lose it all at once. | Место нагрето, %1$s. Не проиграйте всё сразу. |
| `dialog.burmaldaholic.bots.leave.1` | I'm off to mine some more chips. | Пойду накопаю ещё фишек. |
| `dialog.burmaldaholic.bots.leave.2` | That's enough excitement for one circuit board. | Для одной платы волнений достаточно. |
| `dialog.burmaldaholic.bots.leave.3` | See you at the next table. I'll be the one in copper. | Увидимся за другим столом. Ищите того, кто в меди. |
| `dialog.burmaldaholic.bots.win_big.1` | Ka-ching! Somebody call the Loan Shark. For you, not me. | Дзынь! Позовите Ростовщика. Вам, не мне. |
| `dialog.burmaldaholic.bots.win_big.2` | The house thanks you. Well, I do. | Заведение благодарит. Ну, то есть я. |
| `dialog.burmaldaholic.bots.win_big.3` | Running hot! Somebody pour water on my circuits. | Пошла карта! Полейте мне схемы водой. |
| `dialog.burmaldaholic.bots.win_big.4` | Stack it up, stack it up! | Складываем, складываем! |
| `dialog.burmaldaholic.bots.bust.1` | Out of chips. Out of pride. Out of here. | Нет фишек. Нет гордости. Нет меня. |
| `dialog.burmaldaholic.bots.bust.2` | I'll be back. With a loan. | Я вернусь. С займом. |
| `dialog.burmaldaholic.bots.bust.3` | Error 404: stack not found. | Ошибка 404: фишки не найдены. |
| `dialog.burmaldaholic.bots.bust.4` | Tell my motherboard I love her. | Передайте материнской плате: люблю. |
| `dialog.burmaldaholic.bots.bad_beat.1` | The river hates me. Personally. | Ривер меня ненавидит. Лично. |
| `dialog.burmaldaholic.bots.bad_beat.2` | Statistically, that never happens. Emotionally, it happens every day. | По статистике такого не бывает. По ощущениям — каждый день. |
| `dialog.burmaldaholic.bots.bad_beat.3` | Recalculating… nope, still lost. | Пересчитываю… нет, всё равно проигрыш. |
| `dialog.burmaldaholic.bots.fold_to_shove.1` | Too rich for my redstone. | Слишком дорого для моего редстоуна. |
| `dialog.burmaldaholic.bots.fold_to_shove.2` | I fold. Strategically. Definitely not scared. | Пас. Стратегически. Точно не от страха. |
| `dialog.burmaldaholic.bots.fold_to_shove.3` | Take it, %1$s. My chips are waiting for a better story. | Забирайте, %1$s. Мои фишки ждут истории получше. |
| `dialog.burmaldaholic.bots.hero_call.1` | I could smell that bluff from the Nether, %1$s. | Этот блеф чувствуется даже из Незера, %1$s. |
| `dialog.burmaldaholic.bots.hero_call.2` | Nice try, %1$s. My sensors are calibrated. | Хорошая попытка, %1$s. Мои датчики откалиброваны. |
| `dialog.burmaldaholic.bots.hero_call.3` | A call on a hunch. The hunch delivers. | Колл по наитию. Наитие не подводит. |
| `dialog.burmaldaholic.bots.human_wins.1` | Well played, %1$s. | Хорошо сыграно, %1$s. |
| `dialog.burmaldaholic.bots.human_wins.2` | %1$s, are you counting cards? Kidding. Mostly. | %1$s, вы считаете карты? Шучу. Почти. |
| `dialog.burmaldaholic.bots.human_wins.3` | Enjoy it, %1$s. The house wants it back. | Наслаждайтесь, %1$s. Заведение хочет это обратно. |
| `dialog.burmaldaholic.bots.human_wins.4` | Someone is on fire tonight, and it isn't the torch. | Кто-то сегодня в ударе, и это не факел. |
| `dialog.burmaldaholic.bots.all_in.1` | All in! Fortune favours the rusty. | Олл-ин! Фортуна любит ржавых. |
| `dialog.burmaldaholic.bots.all_in.2` | Everything on the table. Even my warranty. | Всё на стол. Даже гарантийный талон. |
| `dialog.burmaldaholic.bots.all_in.3` | Go big or go back to mining. | По-крупному — или назад в шахту. |
| `dialog.burmaldaholic.bots.blackjack.1` | Blackjack! I'd high-five you, but no hands. | Блэкджек! Пять бы дать, да рук нет. |
| `dialog.burmaldaholic.bots.blackjack.2` | Twenty-one! Math is beautiful. | Двадцать одно! Математика прекрасна. |
| `dialog.burmaldaholic.bots.blackjack.3` | Ace and a face, my favourite couple. | Туз и картинка — моя любимая пара. |
| `dialog.burmaldaholic.bots.seven_out.1` | Seven out! Who brought the black cat? | Семёрка! Кто принёс чёрную кошку? |
| `dialog.burmaldaholic.bots.seven_out.2` | Pass the dice. And the tissues. | Передайте кости. И платочки. |
| `dialog.burmaldaholic.bots.seven_out.3` | The dice have spoken. Rudely. | Кости сказали своё слово. Грубо. |
| `dialog.burmaldaholic.bots.natural.1` | Natural nine! Like a diamond on the first swing. | Натуральная девятка! Как алмаз с первого удара. |
| `dialog.burmaldaholic.bots.natural.2` | Eight, no third card. Elegant. | Восьмёрка — без третьей карты. Изящно. |
| `dialog.burmaldaholic.bots.natural.3` | Banker, Player, whatever: nine is nine. | Банкир, Игрок — неважно: девять есть девять. |
| `dialog.burmaldaholic.bots.bank_take.1` | I'll hold the bank. Try to take it from me. | Банк держу я. Попробуйте отнять. |
| `dialog.burmaldaholic.bots.bank_take.2` | The banker's seat: my natural habitat. | Место банкира — моя естественная среда. |
| `dialog.burmaldaholic.bots.bank_take.3` | Bets, please. The bank is open. | Ставки, пожалуйста. Банк открыт. |
| `dialog.burmaldaholic.bots.banco.1` | Banco! All of it, please. | Банко! На всё, пожалуйста. |
| `dialog.burmaldaholic.bots.banco.2` | Banco. Go big or go home. | Банко. Гулять так гулять. |
| `dialog.burmaldaholic.bots.banco.3` | I'll take the whole bank, thanks. | Беру весь банк, спасибо. |
| `dialog.burmaldaholic.bots.pvp_win.1` | Luck is a skill. Mine, today. | Удача — это навык. Сегодня мой. |
| `dialog.burmaldaholic.bots.pvp_win.2` | Good game, %1$s. The coin likes copper. | Хорошая игра, %1$s. Монетка любит медь. |
| `dialog.burmaldaholic.bots.pvp_win.3` | Victory tastes like… machine oil. Lovely. | Победа на вкус как… машинное масло. Прекрасно. |
| `dialog.burmaldaholic.bots.pvp_loss.1` | Rematch? My circuits demand it. | Реванш? Мои схемы требуют. |
| `dialog.burmaldaholic.bots.pvp_loss.2` | Well played, %1$s. Next time it's mine. | Хорошо сыграно, %1$s. В следующий раз отыграюсь. |
| `dialog.burmaldaholic.bots.pvp_loss.3` | Rigged! …Just kidding, it's perfectly fair. | Подкручено! …Шучу, всё честно. |
| `dialog.burmaldaholic.bots.duel_accept.1` | Challenge accepted. May the best dice win. | Вызов принят. Пусть победит лучший рандом. |
| `dialog.burmaldaholic.bots.duel_accept.2` | You're on, %1$s. | Идёт, %1$s. |
| `dialog.burmaldaholic.bots.duel_accept.3` | A duel? They built me for this. Well, for sorting chests, but still. | Дуэль? Меня для этого и собирали. Ну, для сортировки сундуков, но всё же. |
| `dialog.burmaldaholic.bots.duel_decline.1` | Not today, %1$s. My purse says no. | Не сегодня, %1$s. Кошелёк против. |
| `dialog.burmaldaholic.bots.duel_decline.2` | Enough of you for today, %1$s. Come back tomorrow. | На сегодня с вас хватит, %1$s. Приходите завтра. |
| `dialog.burmaldaholic.bots.word_got_around.1` | Word got around about you, %1$s. The Sharks are coming. | Слухи о вас разошлись, %1$s. Идут акулы. |
| `dialog.burmaldaholic.bots.word_got_around.2` | Easy money is closed for today. Meet the professionals. | Лёгкие деньги на сегодня закончились. Знакомьтесь с профессионалами. |
| `dialog.burmaldaholic.bots.word_got_around.3` | We've read your file, %1$s. | Мы изучили ваше досье, %1$s. |
| `dialog.burmaldaholic.bots.sulk.1` | We're not playing with you anymore, %1$s. Not today. | С вами мы больше не играем, %1$s. Сегодня — точно. |
| `dialog.burmaldaholic.bots.sulk.2` | The bots have gone on strike. Come back tomorrow. | Боты объявили забастовку. Приходите завтра. |
| `dialog.burmaldaholic.bots.sulk.3` | You've taken enough of our chips for one day. | На сегодня вы забрали достаточно наших фишек. |
| `dialog.burmaldaholic.bots.idle.1` | Anyone else hear a creeper? | Кто-нибудь ещё слышит крипера? |
| `dialog.burmaldaholic.bots.idle.2` | Fun fact: the house always wins. Almost. | Интересный факт: заведение всегда в плюсе. Почти. |
| `dialog.burmaldaholic.bots.idle.3` | Shuffle up and deal, none of us is getting any younger. | Тасуйте и сдавайте, никто из нас не молодеет. |
| `dialog.burmaldaholic.bots.idle.4` | Stare at the chips long enough and they stare back. | Если долго смотреть на фишки, фишки начинают смотреть на вас. |

Variant counts (code constant): join 4, yield 3, leave 3, win_big 4, bust 4, bad_beat 3,
fold_to_shove 3, hero_call 3, human_wins 4, all_in 3, blackjack 3, seven_out 3, natural 3, bank_take 3,
banco 3, pvp_win 3, pvp_loss 3, duel_accept 3, duel_decline 2, word_got_around 3, sulk 3, idle 4
(70 lines). Lines without `%1$s` ignore the argument.

### 11.5 Messages and errors

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.bots.say` | %1$s: %2$s | %1$s: %2$s |
| `msg.burmaldaholic.bots.joined` | %1$s sits down | За стол садится %1$s |
| `msg.burmaldaholic.bots.joined_many` | Bots sit down: %1$s | За стол садятся боты: %1$s |
| `msg.burmaldaholic.bots.left` | %1$s leaves the table | %1$s уходит из-за стола |
| `msg.burmaldaholic.bots.seat_after_round` | A bot will give you its seat after this round | Бот уступит вам место после этого раунда |
| `msg.burmaldaholic.bots.seat_ready` | Your seat is ready | Ваше место готово |
| `msg.burmaldaholic.bots.host_now` | You are now the host of this table: you choose who plays | Теперь вы хозяин стола: вы решаете, кто играет |
| `msg.burmaldaholic.bots.settings_pending` | New table settings from %1$s, from the next round: %2$s | Новые настройки от игрока %1$s — со следующего раунда: %2$s |
| `msg.burmaldaholic.bots.settings_applied` | Table settings now: %1$s | Настройки стола теперь: %1$s |
| `msg.burmaldaholic.bots.invited` | %1$s invites you to a private %2$s table at %3$s | %1$s приглашает вас за закрытый стол (%2$s): %3$s |
| `msg.burmaldaholic.bots.invite_sent` | Invite sent: %1$s | Приглашение отправлено: %1$s |
| `msg.burmaldaholic.bots.uninvited` | Invite withdrawn: %1$s | Приглашение отозвано: %1$s |
| `msg.burmaldaholic.bots.let_in_request` | %1$s wants to join your table. Open the table to let them in. | Игрок %1$s хочет сесть за ваш стол. Откройте стол, чтобы пустить. |
| `msg.burmaldaholic.bots.let_in_click` | [Let them in] | [Пустить] |
| `msg.burmaldaholic.bots.regulars_gone` | The regulars went home. No more bots at this table today. | Завсегдатаи разошлись по домам. Сегодня ботов за этим столом больше не будет. |
| `msg.burmaldaholic.bots.word_got_around` | Word got around about %1$s: only Hard bots at this table today | Слухи об игроке %1$s разошлись: сегодня за этим столом только сложные боты |
| `msg.burmaldaholic.bots.sulking` | The bots are sulking: no more games with %1$s today | Боты обиделись: сегодня с игроком %1$s они больше не играют |
| `msg.burmaldaholic.bots.none_available` | No bots are free right now: the casino floor is packed | Свободных ботов сейчас нет: в казино аншлаг |
| `msg.burmaldaholic.bots.session_ended` | The bots pack up their chips. See you next time! | Боты собирают фишки. До встречи! |
| `msg.burmaldaholic.bots.heat_reset` | Bot limits reset for %1$s | Лимиты ботов для игрока %1$s сброшены |
| `gui.burmaldaholic.bots.error.bots_only_table` | %1$s is playing against bots here. Ask them to let you in. | Здесь %1$s играет с ботами. Попросите пустить вас. |
| `gui.burmaldaholic.bots.error.others_seated` | Other players are seated, so Just me and bots is not available | За столом другие игроки — режим «Только я и боты» недоступен |
| `gui.burmaldaholic.bots.error.owner_off` | The casino owner has turned bots off at this table | Владелец казино отключил ботов за этим столом |
| `gui.burmaldaholic.bots.error.owner_locked` | The casino owner does not let players change the seating here | Владелец казино не разрешает менять состав за этим столом |
| `gui.burmaldaholic.bots.error.host_locked` | Only the host can change these settings | Менять эти настройки может только хозяин стола |
| `gui.burmaldaholic.bots.error.private_forbidden` | Private tables are not allowed in this casino | В этом казино закрытые столы запрещены |
| `gui.burmaldaholic.bots.error.private_table` | This table is private. Ask %1$s for an invite. | Это закрытый стол. Попросите приглашение у игрока %1$s. |
| `gui.burmaldaholic.bots.error.capped` | The bots won't play you until tomorrow (in %1$s) | Боты не сядут с вами до завтра (через %1$s) |
| `gui.burmaldaholic.bots.error.easy_stake` | Easy bots don't play at %1$s stakes | Лёгкие боты не играют на ставках «%1$s» |
| `gui.burmaldaholic.bots.error.invites_full` | The invite list is full (%1$s) | Список приглашённых полон (%1$s) |
| `gui.burmaldaholic.bots.error.not_host` | Sit at the table as its host first | Сначала сядьте за стол как его хозяин |
| `gui.burmaldaholic.bots.error.disabled` | Bots are turned off on this server | Боты на этом сервере отключены |
| `gui.burmaldaholic.bots.error.debt` | While you owe the Loan Shark you can only play against the house's own bots | Пока вы должны Ростовщику, можно играть только с ботами заведения |

### 11.6 Advancements

| Key | EN | RU |
|-----|----|----|
| `advancement.burmaldaholic.man_vs_machine.title` | Man vs Machine | Человек против машины |
| `advancement.burmaldaholic.man_vs_machine.description` | Win a poker pot at showdown against a Hard bot | Выиграйте банк на вскрытии против сложного бота |
| `advancement.burmaldaholic.clean_sweep.title` | Clean Sweep | Чистая работа |
| `advancement.burmaldaholic.clean_sweep.description` | At a Just me and bots poker table, take every chip from 3 Normal or Hard bots in one sitting | За покерным столом «Только я и боты» оставьте без фишек трёх нормальных или сложных ботов за одну игру |
| `advancement.burmaldaholic.word_got_around.title` | Word Got Around | Слухи расходятся |
| `advancement.burmaldaholic.word_got_around.description` | Win so much from bots in one day that only the Sharks will play you | Выиграйте у ботов за день столько, что с вами сядут играть только акулы |
| `advancement.burmaldaholic.short_circuit.title` | Short Circuit | Короткое замыкание |
| `advancement.burmaldaholic.short_circuit.description` | Call Banco against a bot banker and win the coup | Объявите банко против бота-банкира и выиграйте раздачу |
| `advancement.burmaldaholic.members_only.title` | Members Only | Только для своих |
| `advancement.burmaldaholic.members_only.description` | Play a round at a private table with a player you invited | Сыграйте раунд за закрытым столом с приглашённым вами игроком |
| `advancement.burmaldaholic.no_robots.title` | No Robots Allowed | Роботам вход воспрещён |
| `advancement.burmaldaholic.no_robots.description` | Play a round at a Humans only table with at least 4 players | Сыграйте раунд за столом «Только люди», где сидят не меньше 4 игроков |
| `advancement.burmaldaholic.shark_hunter.description` | Take every chip from a Hard (Shark) poker bot | Оставьте сложного бота-акулу без фишек |

(The last row **replaces** the existing `shark_hunter.description` value.)

### 11.7 Config labels (`## config`, new subsection "bots")

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.section.bots` | Seats and bots | Места и боты |
| `config.burmaldaholic.bots.enabled` | Bots | Боты |
| `config.burmaldaholic.bots.maxActiveTables` | Max tables with bots | Макс. столов с ботами |
| `config.burmaldaholic.bots.maxActive` | Max bots in the world | Макс. ботов в мире |
| `config.burmaldaholic.bots.maxConcurrentJobs` | Heavy bot jobs at once | Тяжёлых задач ботов одновременно |
| `config.burmaldaholic.bots.difficultyMix` | Bot level mix (Easy/Normal/Hard %%) | Состав ботов (лёгкие/нормальные/сложные, %%) |
| `config.burmaldaholic.bots.think.minTicks` | Bot think time min (ticks) | Мин. раздумье бота (тики) |
| `config.burmaldaholic.bots.think.maxTicks` | Bot think time max (ticks) | Макс. раздумье бота (тики) |
| `config.burmaldaholic.bots.think.tankTicks` | Hard bot extra think time (ticks) | Доп. раздумье сложного бота (тики) |
| `config.burmaldaholic.bots.think.fastFactor` | Fast speed multiplier | Множитель быстрого темпа |
| `config.burmaldaholic.bots.personalities` | Bot personalities | Характеры ботов |
| `config.burmaldaholic.bots.keepFreeSeatDefault` | Keep a seat free by default | Держать место свободным по умолчанию |
| `config.burmaldaholic.bots.showcase.enabled` | Showcase tables (bots play while watched) | Показательные столы (боты играют при зрителях) |
| `config.burmaldaholic.bots.table.policy` | %1$s: default players | %1$s: кто играет по умолчанию |
| `config.burmaldaholic.bots.table.count` | %1$s: default bots | %1$s: ботов по умолчанию |
| `config.burmaldaholic.bots.table.difficulty` | %1$s: default bot level | %1$s: уровень ботов по умолчанию |
| `config.burmaldaholic.bots.table.worldgenPolicy` | %1$s: players at generated tables | %1$s: кто играет за сгенерированными столами |
| `config.burmaldaholic.bots.table.worldgenCount` | %1$s: bots at generated tables | %1$s: ботов за сгенерированными столами |
| `config.burmaldaholic.bots.atmosphere.maxPerTable` | %1$s: max bots per table | %1$s: макс. ботов за столом |
| `config.burmaldaholic.bots.poker.easyMaxStake` | Highest stakes for Easy poker bots | Макс. ставки для лёгких покерных ботов |
| `config.burmaldaholic.bots.chemmy.bankCapMultiple` | Bot bank cap (× table min) | Лимит банка бота (× мин. ставка) |
| `config.burmaldaholic.bots.craps.canShoot` | Bots may shoot at craps | Боты могут бросать кости в крэпсе |
| `config.burmaldaholic.bots.pvp.fillDelayTicks` | PvP lobbies: bots fill after (ticks) | PvP-лобби: боты заходят через (тики) |
| `config.burmaldaholic.bots.pvp.maxPerMatch` | PvP: max bots per match | PvP: макс. ботов в матче |
| `config.burmaldaholic.bots.tournament.maxFill` | Tournaments: max bot fillers | Турниры: макс. ботов-заполнителей |
| `config.burmaldaholic.bots.tournament.fillToBracket` | Tournaments: fill the bracket with bots | Турниры: заполнять сетку ботами |
| `config.burmaldaholic.bots.owned.funding` | Bots at player casinos | Боты в казино игроков |
| `config.burmaldaholic.bots.owned.funding.owner_bankroll` | Paid from the bankroll | За счёт кассы заведения |
| `config.burmaldaholic.bots.owned.funding.disabled` | Not allowed | Запрещены |
| `config.burmaldaholic.bots.tableBuyInsPerDay` | Bot buy-ins per table per day | Бай-инов ботов на стол в день |
| `config.burmaldaholic.bots.vipWagerWeight` | VIP credit for chips vs bots | Зачёт ставок против ботов в ВИП |
| `config.burmaldaholic.bots.dailyWinCapMin` | Daily bot winnings limit, minimum | Дневной лимит выигрыша у ботов, минимум |
| `config.burmaldaholic.bots.dailyWinCapTierMultiple` | Daily bot winnings limit (× max bet) | Дневной лимит выигрыша у ботов (× макс. ставка) |
| `config.burmaldaholic.bots.sulkMultiplier` | Bots refuse at (× limit) | Боты отказываются играть при (× лимит) |
| `config.burmaldaholic.bots.adaptiveHeat` | Stronger bots for winning players | Сильные боты для выигрывающих игроков |
| `config.burmaldaholic.bots.debtorsMayPlay` | Debtors may play house bots | Должники могут играть с ботами заведения |
| `config.burmaldaholic.bots.avatars.mode` | Bot avatars | Аватары ботов |
| `config.burmaldaholic.bots.avatars.mode.none` | None | Нет |
| `config.burmaldaholic.bots.avatars.mode.nameplate` | Name tags | Таблички с именами |
| `config.burmaldaholic.bots.avatars.mode.entity` | Figures | Фигуры |
| `config.burmaldaholic.bots.avatars.maxEntities` | Max avatar figures | Макс. фигур ботов |
| `config.burmaldaholic.bots.chatter.enabled` | Bot chatter | Болтовня ботов |
| `config.burmaldaholic.bots.chatter.chance` | Bot chatter chance | Вероятность реплики бота |
| `config.burmaldaholic.bots.chatter.botCooldownTicks` | Bot chatter cooldown per bot (ticks) | Пауза между репликами бота (тики) |
| `config.burmaldaholic.bots.chatter.tableCooldownTicks` | Bot chatter cooldown per table (ticks) | Пауза между репликами за столом (тики) |
| `config.burmaldaholic.bots.chatter.maxPerMinute` | Bot lines per table per minute | Реплик ботов за столом в минуту |
| `config.burmaldaholic.bots.private.enabled` | Private tables | Закрытые столы |
| `config.burmaldaholic.bots.private.maxInvites` | Max invites per table | Макс. приглашений на стол |
| `config.burmaldaholic.bots.private.inviteRadius` | Invite radius (0 = anyone online) | Радиус приглашений (0 = любой онлайн) |

Family template members pass the game name (`gui.burmaldaholic.common.game.*`) as `%1$s`. Enum values
of policy/difficulty reuse `gui.burmaldaholic.bots.policy.*` / `…level.*`.

---

## 12. Test plan (both editions; pure logic tests share vectors)

### 12.1 Fairness and RNG independence

1. **Deck independence.** `debug.fixedSeed = 42`; a 6-seat poker table with 1 scripted human + 5 bots;
   run 10 000 hands four times: all EASY, all NORMAL, all HARD, all MIXED. Assert the sequence of
   shuffled decks (hash of each 52-card permutation) is **identical** in all four runs, and identical to
   a run where the 5 seats are scripted "always check/fold" humans. Same test for a chemin de fer shoe
   (1 000 coups: shoe order and burn identical), UTH (1 000 rounds: deck per round identical), blackjack
   (shoe permutation identical; card-to-seat mapping may differ after the first decision), craps (dice
   sequence identical, 1 000 rolls), and each PvP mode (tape identical for 1 000 matches whatever the
   bot levels).
2. **No peeking.** Instrument the bot `View`: assert it never contains another seat's hole cards, the
   deck/shoe order or the tape (reflection/structural test), and that the Monte-Carlo card pool =
   52 − own hole cards − visible board (size 50 preflop, 47 flop, 46 turn, 45 river).
3. **Bots use only the bot RNG.** Wrap the game RNG with a counter: over 10 000 poker hands the number of
   game-RNG calls equals the no-bot baseline (52-card shuffle per hand = 51 draws on Java).

### 12.2 Poker difficulty (seeded exploit-regression suite, 50 000 hands per cell, 6-max, 100 BB)

Scripted human strategies: *always-shove*, *always-c-bet*, *calling-station* (VPIP 45 %),
*nit* (VPIP 12 %), *ABC-TAG*. Assert, for the bot level (BB/100, 95 % CI):

| Bot level vs | always-shove | always-c-bet | calling-station | nit | ABC-TAG |
|--------------|--------------|--------------|-----------------|-----|---------|
| EASY | ≥ 0 (the shove bug is fixed) | ≥ −60 | −10 to +5 | ≥ −20 | −40 to −80 |
| NORMAL | ≥ +20 | ≥ −10 | 0 to +10 | ≥ 0 | −5 to −15 |
| HARD | ≥ +30 | ≥ +5 | +10 to +25 | ≥ +2 | −3 to +3 |

- **Level ordering:** heads-up 200 000 hands per pairing: HARD beats NORMAL by ≥ 5 BB/100, NORMAL beats
  EASY by ≥ 10 BB/100, HARD beats EASY by ≥ 15 BB/100 (CI excludes 0).
- **Personalities don't cross levels:** every personality of level L+1 beats every personality of
  level L heads-up (CI excludes 0, 100 000 hands each).
- **Stake gate:** at Mid and High, 10 000 MIXED fills never produce an EASY bot; at Micro the observed
  mix is 45/45/10 ± 1.5 %.
- **Legalize:** 10⁶ random views → every action legal.
- **Budget fallback:** with the job budget forced to 30 samples, HARD decides with the NORMAL rule and
  never later than `maxTicks + 40` t.

### 12.3 Other games

- **Blackjack:** HARD's decisions equal the basic-strategy table for 6 decks S17 DAS (all 550 cells:
  hard 5–21, soft 13–21, pairs × dealer 2–A); EASY never doubles; NORMAL deviates in 5 % ± 0.5 % of
  soft/double spots over 10⁵ decisions.
- **UTH:** 2.5 × 10⁷ rounds per level: NORMAL edge 2.27 % ± 0.2 % of the Ante (strategy R), HARD 2.19 % ±
  0.2 %, EASY measured and published into `…bots.uth_edge` (expected 4–6 %).
- **Chemin de fer EV invariance:** 10⁶ coups with one scripted human punter (flat 10) against bot
  bankers of each style: human EV −1.24 % ± 0.15 % in all three styles; bot bank never pays rake;
  no bot punter bet while a bot banks; bot punter bets never reduce the coverage a human requested.
- **Atmosphere bots:** 10 000 rounds each at blackjack, roulette, craps, baccarat, UTH with bots: the
  bank, bankroll, reservations, jackpot, streak, VIP and statistics are byte-identical to a run without
  bots (same seed, same human actions).
- **PvP:** 10⁶ Coin Flip Duels vs a bot at stake 100, 300 bp: human EV −3.00 ± 0.20 per match
  (= −R/2); Wheel Party with a HARD whale bot (human 100, bot 800): human wins 11.1 % ± 0.1 %, EV
  −3.0 ± 0.5; 10⁵ Slot Showdowns 1 human + 3 bots: human wins 25 % ± 0.3 %.

### 12.4 Seating and settings

| # | Scenario | Expected |
|---|----------|----------|
| S1 | Poker MIXED, keep-free, 1 human | 4 bots; seat 6 free |
| S2 | S1, human B uses the table | B sits at once in the free seat; next hand one bot leaves (5 − 2 − 1 = 3 bots) |
| S3 | keep-free off, 1 human + 5 bots, human B uses the table | B is claimant; the bot that just posted the BB leaves after the hand; B seated before the next deal |
| S4 | Host switches to BOTS_ONLY while B is seated | option disabled, `…error.others_seated` |
| S5 | Host changes count 4 → 2 mid-hand | `pending` shown; after AWARD 2 bots leave (the two that will pay the BB last) |
| S6 | Private on with A and B seated | A and B auto-invited; C using the table gets `…error.private_table` naming the host |
| S7 | Host leaves a MIXED table with B seated | B becomes host, `host_now` message |
| S8 | Last human leaves | all bots leave at the next safe point; stacks back to the purse; table defaults restored |
| S9 | Chemmy, bot banker, human claimant arrives | a bot punter yields first; the banker only if no punter bot |
| S10 | Craps with 2 atmosphere bots | shooter rotation never lands on a bot; seven-out passes the dice between humans only |
| S11 | UTH player-banked, human banks | bots show *Watching*; no bot seat is settled |
| S12 | Restart during a drawn poker hand with bots | hand played out (bots play on); bot stacks returned to the purse; no bots after load |
| S13 | Owned table, Bots = Allowed, bankroll 150, buy-in 200 | no bot sits; charter shows `bankroll_short` |
| S14 | Owned poker table, bankroll 10 000, 3 bots buy in 200 each | bankroll 9 400, *Bot stacks out* 600; a human wins 150 from one bot; session ends → the bots' 450 return, bankroll 9 850 |
| S15 | Worldgen blackjack, host sets HUMANS_ONLY then leaves | next session starts with the worldgen default (MIXED, 2) |
| S16 | PvP Slot Showdown (max 6), MIXED, host alone, no joiner for 400 t | 3 bots join (`maxPerMatch`), 2 places stay open; *Start* now possible |

### 12.5 Economy vectors

- **Poker rake and attribution:** BB 10, pot 300 after the flop (human h 100, bot 100, human g 100),
  h wins. Rake = `min(floor((300 − 100) × 0.05), 3 × 10) = 10` → h receives 290 (net +190).
  `fromBots_h = floor(290 × 100 / 300) = 96`. g: `toBots_g = floor(100 × 0 / 300) = 0` (the bot won
  nothing). Heads-up h vs bot, pot 200 (unraked): h wins → `botNet_h` +100; h loses → −100.
- **Heat:** Gold player (tier max 1 000) → threshold 5 000; after +5 010 net: the next poker hand has
  only HARD house bots; after +10 000: bots leave, `…error.capped`; next MCD everything resets.
- **VIP weight:** poker hand where h put in 400, matched 300 by bots and 100 by a human: botShare 0.75,
  credit `floor(400 × (1 − 0.5 × 0.75)) = 250`.
- **Buy-in budget:** 11th busted house bot at one table in one MCD is not replaced; `regulars_gone`.
- **Debt:** a debtor may punt at chemin de fer against a house bot bank (coup settles normally) but is
  refused with `…error.debt` when a human holds the bank; a debtor may start a Coin Flip Duel vs a bot
  and a BOTS_ONLY Plinko Battle, but not a MIXED lobby that already has a human joiner.
- **Rake to owner:** owned anchor, Coin Flip Duel human 100 vs a bankroll-funded bot 100, 300 bp: rake 6
  → `floor(6 × 100 / 200) = 3` to the bank sink, 3 to the bankroll; the human's EV is −3 as in any duel.

### 12.6 Presentation and limits

- Every bot shown anywhere has the glyph + `[BOT]` / `[БОТ]` tag (UI snapshot tests for each game
  screen/form). RU button labels ≤ 24 characters (`Настройки стола…` 16, `Закрытый стол…` 14,
  `Пригласить игрока…` 18, `Отозвать приглашение…` 21, `Игра с ботами…` 14).
- Name uniqueness: 10⁴ table fills of 7 bots → never a duplicate at one table.
- Chatter limits: a 30-minute bot-heavy session produces ≤ 3 lines per table-minute and ≤ 1 per bot
  per 30 s; muted players receive none.
- Performance: Bedrock, 12 tables × 5 HARD poker bots, 10 minutes: no script watchdog warning, bot
  logic ≤ 4 ms mean per tick, ≤ 2 concurrent jobs; Java, 24 tables: ≤ 2 ms mean per tick.
- Avatars: 17th avatar is not spawned; avatars are never saved (reload → recreated only for active
  sessions).

---

## 13. Research integration and assumptions

Taken from `docs/research/bots.md`: range-aware equity, minimum defence, opponent model and target win
rates for HARD poker (§4.3); the Fish shove-fold fix (research §1.6.1 — a code defect to fix now);
Easy's readable timing tell; personalities ROCK/STATION/MANIAC/TAG/LAG; stake gates and new
`poker.botMix` defaults; per-table buy-in budget; weighted VIP credit; streak exclusion; bot chips never
becoming owner rake; UTH/blackjack/chemmy/PvP level tables; betting styles for luck games; craps bots
don't shoot; PvP fill delay and cap; localized name ids (and the "Diamond Dave" replacement);
showcase mode (NICE); performance caps and the Bedrock `runJob` budget; the exploit-regression suite.

Decisions that differ from the research, with the reason:
- **Heat** keeps research's "Word got around" upgrade but adds a **sulk** stage at 2× (research had no
  hard stop; without one a player who beats HARD bots could still drain a table indefinitely), and the
  threshold is in chips by VIP tier (one number across all bot games) instead of 300 BB per table.
- **Debtors** may play house-funded bots (research suggested barring them): house bots are the house, so
  this is a house game; the key `bots.debtorsMayPlay` gives servers the stricter rule.
- **BOTS_ONLY** means "the host plus bots" (the user's "only with bots"); research's bot-only showcase is
  kept separately as NICE §4.9.
- **Worldgen tables**: hosts may change session settings (research: ops only) — the user asked for the
  choice at every table; defaults restore when the session ends.
- **Chemmy HARD** keeps research's numbers but the label is "Cool-headed" (Style), stating plainly that
  it is not stronger.

Assumptions (to confirm with the owners): the Bedrock font sheet has a free cell at U+E190; Bedrock
custom entities can be made non-persistent and fully damage-immune for avatars; `playerInteractWithEntity`
delivers Casino Card use on players (PVP.md §14 has the same risk and fallback).
