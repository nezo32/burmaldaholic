# Burmaldaholic — Game Design Specification

> **Java-only (2026-09-24).** Bedrock support was dropped: Bedrock sections, lanes and tasks were removed. An inline
> note that still names Bedrock (the former TypeScript twin) is historical context and does not apply.

Status: **v1.0, implementation-ready**. Owner: lead game design (D1).
Audience: Java (Fabric) team, testers, localization.

This document is **normative**: the mod implements exactly these rules, numbers and state machines.
(Java-only since 2026-09-24; the former Bedrock "edition notes" were removed.)

Companion files:

| File | Contents |
|------|----------|
| `CONFIG.md` | Every tunable value (key, type, default, range). Numbers in this file are the **defaults** of those keys. |
| `UI.md` | Screens, forms, HUD, layout rules. |
| `LOCALIZATION.md` | Key scheme, placeholders, plural helper, glossary, tone. |
| `STRINGS.md` | Every player-facing string, EN + RU, grouped by module. |

Conventions used below:

- **Chips** are integers. There are no fractional chips anywhere. Every payout formula ends with
  `floor()` unless stated otherwise; the fractional remainder stays with the house (the bank).
- "Payout X:1" means the player gets the stake back **plus** X × stake. "Multiplier X×" means the
  player receives X × stake in total (stake included).
- **RTP** = expected total return / total staked. **House edge (HE)** = 1 − RTP.
- Time is measured in **game ticks of world time** (`world.getTime()` Java / `system.currentTick` is
  NOT acceptable because it resets; Bedrock must use `world.getAbsoluteTime()`), so timers keep
  running while a player is offline. 20 ticks = 1 s; **1 Minecraft day (MCD) = 24000 ticks = 20 min**.
- "Tier max" means the VIP tier maximum bet (§12).
- Module names in brackets, e.g. **[loan]**, are the ownership units used in `STRINGS.md` and in
  the code package layout.
- All randomness is server-side. Clients only animate results the server already decided.
  Shuffles are Fisher–Yates with a uniform RNG. Java: one `java.util.random.RandomGenerator`
  (`L64X128MixRandom` or `SecureRandom`) per game instance; Bedrock: `Math.random()`.

---

## 1. Overview

Burmaldaholic turns a survival world into a casino economy. Players earn **chips** by playing
normal Minecraft (mining, fighting, trading, daily contracts), then gamble them at tables and
machines found in generated casinos or built themselves. Gambling feeds back into survival: a
**Loan Shark** lends chips and sends **Debt Collectors** when you default, **chaos events** rain
diamonds or mobs on you, **Last Chance** flips a coin when you die, and a **Lucky/Unlucky streak**
nudges RNG games. Everything is fair-feeling but the house wins slightly (§17 summary table).

Modules (each maps to one Wave-3 feature team):

| Module | Section |
|--------|---------|
| core (mode, economy, HUD, cashier, contracts, wagers) | §2–§4 |
| blackjack | §6 |
| poker | §7 |
| slots | §8 |
| roulette | §9 |
| craps | §10 |
| baccarat (Punto Banco) | §20 |
| uth (Ultimate Texas Hold'em) | §21 |
| extras (coin flip, wheel, scratch cards, plinko, dice duel) | §11 |
| loan (Loan Shark + Debt Collectors) | §5 |
| chaos (events, Golden Hour) | §13 |
| streak | §14 |
| lastchance | §15 |
| worldgen | §16 |
| vip | §12 |
| multiplayer (hosted tables, owned casinos) | §18 |
| advancements | §19 |

**Multiplayer per game** (⚠ ADDED 2026-09): blackjack — up to 5 seats vs the dealer; poker — PvP
(6-max, bots fill); roulette — shared spins (8 bettors); craps — shooter rotation (6 seats);
baccarat — shared coup for up to 7 seats vs the house, plus the player-banked **Chemin de fer**
variant (PvP, §20.9); Ultimate Texas Hold'em — up to 6 seats sharing board and dealer hand vs the
house, plus the **player-banked** variant (a player takes the dealer seat, §21.9); dice duel — PvP
or vs the house; slots, wheel, plinko, scratch cards, coin flip — solo.

---

## 2. Mode activation and difficulty

### 2.1 Casino mode switch

Casino mode is a per-world flag `core.casinoMode` (bool). When **false** the mod is *dormant*: no
HUD, no earning, no chaos, no Last Chance, no debt, tables show "Casino mode is off" and do
nothing; all saved data (balances, loans, ownership) is preserved untouched. When **true**
everything in this document is active.

**Java (Fabric):**
- **Not a game rule.** The value is saved with the world in its own file
  `<world>/data/burmaldaholic/mode.dat` (world saved data, like Enchantaholic's per-world mode).
  It defaults to **OFF**: installing the mod changes nothing until a world opts in. A world without
  the file (dedicated server, other launcher) starts OFF; an unreadable file logs a warning and
  resets to OFF.
- The mod adds a toggle button **"Casino Mode: ON/OFF"** (default **OFF**, full width, with a
  tooltip) to the *Game* tab of the Create World screen, directly **below "Difficulty"** and above
  "Allow Commands". The choice is kept with the screen's creation settings (Cancel and Re-Create
  start OFF again) and written to `mode.dat` as soon as the world is created (saved immediately,
  not at the first autosave).
- Operators (permission level 2) switch it later with `/casino mode on|off` and check it with
  `/casino mode status` (bare `/casino mode` = status; alias `/burmaldaholic mode …`). In
  single-player this needs cheats (Allow Commands or Open to LAN). Every online player (and
  later, every player on first join while the mode is on) then gets the first-join welcome: starting
  balance and Casino Card (§3.3).
- Worldgen structures ship in a built-in data pack `burmaldaholic:casinos` that is enabled by
  default in the Data Packs list of world creation. If the pack is disabled, casinos do not
  generate but gameplay still works (players craft their own tables).
- The saved `mode.dat` value is the source of truth; `config/burmaldaholic.json` never overrides it.

### 2.2 Vanilla difficulty is never altered

The mod **never** changes the world difficulty, the Hardcore flag, `keepInventory`, natural
regeneration or any vanilla game rule. It only *reads* difficulty to scale its own mechanics.
"Hardcore" below means the world's hardcore flag is set (Java `LevelProperties.isHardcore()`,
Bedrock `world.isHardcore`). A Hardcore world always runs at Hard difficulty, so every "Hard" value
applies to it too, plus the Hardcore overrides.

### 2.3 Difficulty interaction table (defaults)

| Mechanic | Peaceful | Easy | Normal | Hard | Hardcore |
|----------|----------|------|--------|------|----------|
| Mob-kill chip rewards | n/a (few mobs) | ×1.0 | ×1.0 | ×1.25 | ×1.25 |
| Chaos `mob_wave` | never (rerolled) | 3 mobs | 4 mobs | 6 mobs | 6 mobs |
| Last Chance success chance | 0.60 | 0.60 | 0.50 | 0.40 | disabled by default; High-Stakes variant 0.50 (§15.3) |
| Last Chance cost (on success) | 10 % balance | 10 % | 10 % | 10 % | all chips + 1 permanent heart |
| Loan interest (base) | 15 % | 15 % | 20 % | 25 % | 25 % |
| Loan late fee per overdue MCD | 5 % | 5 % | 10 % | 15 % | 15 % |
| Debt Collectors | never; **Asset Freeze** instead (§5.6) | squad −1 Collector, HP ×0.75 | base | squad +1 Collector, HP ×1.25 | as Hard; plus a collector kill that ends the debtor is final (no Last Chance vs. collectors) |
| Heart wager | allowed | allowed | allowed | allowed | allowed (temporary only) |
| Soul Wager (§4.6) | — | — | — | — | only if `wager.hardcoreSoulWager=true` (default false) |

---

## 3. Economy

### 3.1 Two forms of money

1. **Balance** (account): an integer per player, stored server-side (Java: player persistent
   data attachment; Bedrock: player dynamic property `burmaldaholic:balance`). Shown on the HUD.
   Survives death. All wagers are debited from the balance; all winnings are credited to it.
   Range `0 … economy.maxBalance` (default 1 000 000 000). Credits beyond the cap are lost with
   message `msg.burmaldaholic.core.balance_capped`. Balance can never go negative; debt is a
   separate number (§5).
2. **Chip items**: physical, tradable, droppable, stackable (64) items. They drop on death like
   any item and can be stolen, stored in chests, traded between players.

| Item id | Value | Color | Rarity (name color) |
|---------|-------|-------|---------------------|
| `chip_1` | 1 | white | common |
| `chip_5` | 5 | red | common |
| `chip_25` | 25 | green | uncommon (yellow) |
| `chip_100` | 100 | black | rare (aqua) |
| `chip_500` | 500 | purple | epic (light purple) |

Chip items do nothing on their own. Right-click with a chip item: shows value tooltip only.

### 3.2 Cashier (block `cashier`)

A workstation block (1×1×1, directional, model: counter with a brass grille). Right-click opens
the Cashier screen (UI.md §4). Functions:

| Action | Rule |
|--------|------|
| **Deposit** | Takes all chip items from the player's inventory (or only the selected stack, player's choice) and adds their value to the balance. |
| **Withdraw** | Player enters an amount A ≤ withdrawable (below). Converted greedily into the largest denominations (500, 100, 25, 5, 1). If inventory is full, the rest drops at the player's feet. |
| **Buy chips** | 1 emerald → `economy.emeraldBuyRate` chips (default 8; Gold VIP+ 9). |
| **Sell chips** | `economy.emeraldSellRate` chips (default 10) → 1 emerald. Only whole emeralds. |
| **Contracts** | Opens the contracts tab (§3.5). |
| **Loan status** | Read-only; paying is done at the Loan Shark or via the Casino Card. |

`withdrawable = max(0, balance − outstandingDebt)` — you cannot turn borrowed money into items
(anti-abuse, §5.8). While a loan is **in default**, withdraw is completely disabled.
Nether cashiers (Piglin Parlor, §16.2) additionally trade gold ingots: buy 1 gold ingot → 3 chips,
sell 12 chips → 1 gold ingot.

Crafting (shaped):
```
G E G      G = gold ingot, E = emerald, C = chest
I C I      I = iron ingot
I I I
```

### 3.3 Casino Card (item `casino_card`)

Every player receives one Casino Card on first join while casino mode is on (once per player;
re-craftable: paper + gold nugget + emerald, shapeless). Using it opens the **Casino Menu**
(UI.md §3): balance, streak, VIP progress, contracts, loan (pay/view), achievements (Bedrock),
dice-duel challenges, owned-casino management, admin page (ops only), settings (HUD position).
Java also binds the menu to a key (default `B`, "Open Casino Menu"); the card works in both.

### 3.4 Earning chips (credited straight to balance)

Every credit shows an action-bar toast `msg.burmaldaholic.core.earned` ("+20 chips (Diamond ore)").
Toasts within 20 ticks are merged. All rates are config keys `economy.ore.*`, `economy.mob.*`.

#### 3.4.1 Ores (per ore block broken by a player)

Rewarded when a player breaks the ore **and it drops its normal loot** (tool is correct tier; no
Silk Touch). Fortune does **not** multiply. Deepslate variants give the same as normal variants.
Creative mode: no reward.

| Ore | Chips | | Ore | Chips |
|-----|-------|-|-----|-------|
| Coal | 1 | | Lapis | 2 |
| Copper | 1 | | Emerald | 15 |
| Iron | 2 | | Diamond | 20 |
| Gold (overworld) | 4 | | Nether quartz | 1 |
| Redstone | 1 | | Nether gold | 1 |
| Ancient debris | 50 | | Gilded blackstone | 0 |

Anti-exploit: ancient debris drops itself even without Silk Touch, so player-placed debris is
tracked in a **placed-debris ledger** (per dimension, set of block positions, FIFO cap 4096). Breaking
a ledgered position gives 0 and removes the entry. All other ores need Silk Touch to be moved, and
Silk Touch mining gives 0, so each ore block pays at most once.

#### 3.4.2 Mob kills

Rewarded when a mob dies and the vanilla "killed by player" condition holds (the same condition
that makes it drop XP: player dealt damage within the last 100 ticks). Multiplied by the
difficulty factor in §2.3.

| Category | Mobs | Chips |
|----------|------|-------|
| Passive / ambient / tamed / villagers | cow, sheep, bat, villager, iron golem, … | 0 |
| Common hostile | zombie (all variants incl. husk, drowned, zombie villager), skeleton, stray, bogged, spider, cave spider, silverfish, endermite, slime & magma cube (size ≥ 2 only), vex, zombified piglin | 2 |
| Uncommon hostile | creeper, phantom, pillager, hoglin, zoglin, piglin, drowned with trident, enderman, blaze | 3–4 (creeper 3, phantom 3, pillager 3, hoglin 4, zoglin 4, piglin 3, enderman 4, blaze 4) |
| Tough hostile | witch 5, vindicator 5, wither skeleton 5, ghast 6, guardian 4, breeze 8, shulker 8, piglin brute 10, creaking 5 (when its heart is broken by a player) | as listed |
| Mini-boss | evoker 20, ravager 25, elder guardian 100 | as listed |
| Boss | warden 250, wither 500, ender dragon 1000 (first kill in the world) / 200 (later kills) | as listed |
| Debt Collector squad, chaos-wave mobs, mobs spawned by spawners/trial spawners | — | 0 |

Anti-farm diminishing returns (per player, per mob type): count kills of that type in the last
`economy.mob.windowTicks` (6000). Kills 1–20 pay 100 %, 21–60 pay 25 % (floor, min 0), 61+ pay 0.
Spawner detection — Java: `SpawnReason.SPAWNER`/`TRIAL_SPAWNER` stored on the entity; Bedrock:
`entitySpawn` cause `Spawned` within 5 blocks of a `mob_spawner`/`trial_spawner` block is tagged
`burmaldaholic:spawner` (best-effort; the diminishing window is the main guard).

#### 3.4.3 Villager trading

Each completed villager or wandering-trader trade: `chips = 1 + (emeralds given or received in
that trade)`, capped at 10 per trade and `economy.trade.dailyCap` = 200 chips per player per MCD.
(`TradeOfferUsed` callback.)

#### 3.4.4 Contracts (daily tasks)

- Each player has **3 contract slots** (Platinum 4, Diamond+ 5). Contracts are generated at the
  first moment the player is online on a new world day (`floor(worldTime / 24000)` changed).
  Unfinished contracts expire at day rollover without penalty.
- Contracts are drawn without duplicates from the pool below with weights. Target amounts scale
  with VIP tier index t (0 = Bronze): `target = base × (1 + 0.25 t)` rounded up;
  `reward = baseReward × (1 + 0.25 t)` × VIP contract bonus (§12).
- **Reroll** one contract: costs `contracts.rerollCost` (10) chips, max 1 reroll per slot per day.
- Progress counts only while casino mode is on. Completion credits immediately with a toast and
  sound.

| Id | Task (target base) | Reward base | Weight |
|----|--------------------|-------------|--------|
| `mine_iron` | Mine 24 iron ore | 50 | 10 |
| `mine_coal` | Mine 48 coal ore | 30 | 8 |
| `mine_diamond` | Mine 3 diamond ore | 80 | 5 |
| `kill_zombie` | Kill 12 zombies | 40 | 10 |
| `kill_skeleton` | Kill 10 skeletons | 40 | 8 |
| `kill_creeper` | Kill 5 creepers | 45 | 6 |
| `kill_any` | Kill 25 hostile mobs | 60 | 8 |
| `trade` | Complete 6 villager trades | 40 | 8 |
| `fish` | Catch 8 fish | 30 | 6 |
| `harvest` | Harvest 64 crops (wheat, carrots, potatoes, beetroot) | 30 | 6 |
| `wager` | Wager 500 chips in total | 30 | 8 |
| `win_blackjack` | Win 3 blackjack hands | 40 | 5 |
| `spin_slots` | Spin slot machines 30 times | 25 | 5 |
| `roulette_red` | Win 2 bets on red at roulette | 35 | 4 |
| `play_poker` | Play 10 poker hands to showdown or fold | 40 | 3 |
| `explore_nether` | Travel 500 blocks in the Nether | 60 | 3 |
| `smelt` | Smelt 32 items | 25 | 3 |
| `slots_feature` | Trigger 2 slot features (free spins or a bonus game; a bought feature does not count; SLOTS.md §8.7) | 45 | 4 |

### 3.5 Sinks (where chips leave the world)

House edge on house-banked games; loan interest and late fees; cashier spread (8 vs 10 per
emerald); contract rerolls; casino license fee (§18); poker rake at non-owned tables; Last Chance
cost. Mints: earnings (§3.4), loans, Golden Hour bonuses, jackpot seeds, loot chests.

---

## 4. Wagers and non-chip stakes

### 4.1 Generic wager lifecycle (all house-banked games)

```
IDLE → STAKED (balance debited, bet locked) → RESOLVING (server RNG) → SETTLED (credit payout)
```
- Debit happens when the bet is **confirmed**, never before. If the balance is insufficient the
  bet is rejected with `gui.burmaldaholic.error.insufficient_funds`.
- If the player disconnects between STAKED and SETTLED: the round is auto-completed by the
  server with the game's default action (blackjack: stand; craps: bets stay working until
  resolved; roulette: spin proceeds; baccarat: the coup proceeds; Ultimate Texas Hold'em: check,
  and on the river fold unless §21.4 auto-plays) and the payout is credited to the balance. No
  refunds.
- If the server stops mid-round (⚠ **CHANGED 2026-09 — review M1 free-roll fix**):
  **drawn rounds are played out, undrawn bets refunded.** Once any random outcome of the round
  is drawn, the result is persisted with the stake *before* it is shown or animated, and after
  the restart the round is **settled** at that result (offline-safe, logged, the player is told
  on join); quitting while a losing result animates gains nothing. Only rounds with no draw yet
  are refunded. "Drawn" per game:
  - roulette: the winning number (drawn at the spin); slots: the grid (drawn at the spin);
  - blackjack: the deal — the stored result is "every open hand stands now, the dealer plays
    the next cards of the shoe", re-drawn after every decision;
  - craps: contract bets with an established point (Pass/Don't Pass during a point, a moved
    Come/Don't Come) are played out with honest dice like a leave; bets waiting for their first
    roll (come-out line bets, a new Come, Field) are refunded;
  - poker: the hand is played out as if every human left now (humans check/fold, bots play on,
    on the deck already dealt); the resulting stacks are paid instead of the start stacks;
  - coin flip, dice duel, wheel, plinko: drawn and settled in the same tick (the animation only
    replays the credited result); scratch cards keep the drawn card and are never refunded;
  - baccarat: the whole coup (4–6 cards), drawn at DEAL before the reveal animation (§20.5);
  - Ultimate Texas Hold'em: the deck (all hole, dealer and board cards), drawn at DEAL; the stored
    result is "every pending decision takes its default action now" (§21.4, §21.5).
  Java may instead play the round out *at* the stop, before the world is saved (same result rules,
  same `msg.burmaldaholic.core.round_played_out` notice); it does the same when a table's chunk
  unloads mid-round. Only a crash (no clean stop) can still leave bets to refund on load.
  Casino mode turning off mid-round (⚠ **CHANGED 2026-09**): the same rule — drawn
  rounds are played out and settled, only undrawn bets are refunded (§2.1 dormancy never cancels a
  decided round).
- Every settled wager updates: lifetime wagered (VIP), streak (§14), contracts, statistics.
  Wagered amount = total chips put at risk in that round (including doubles, splits, odds).

### 4.2 Bet limits

Min bet is per table/machine (below). Max bet = min(table max, VIP tier max §12). The UI never
offers amounts outside `[min, max]`; the server re-validates.

### 4.3 Non-chip stakes ("Pawn" wagers)

Allowed only on the one-bet games: **Coin Flip, Dice Duel (vs house), Wheel of Fortune**, and
**roulette even-money bets** (red/black, odd/even, low/high). One pawn stake per round, cannot
be mixed with chips in the same bet. The stake is converted to a **stake value V** in chips:

- **Win**: the stake is returned untouched **and** the player receives the chip winnings as if V
  chips had been bet (e.g. coin flip pays `floor(V × 0.96)`).
- **Loss**: the stake is forfeited (item destroyed, levels removed, hearts lost for a duration).
- V must be ≤ tier max bet; otherwise the stake is refused.
- ⚠ **CHANGED 2026-09 — review m4**: pawn stakes are **house-only**. At a table or
  machine linked to a player-owned casino (§18.2) only chips are accepted
  (`gui.burmaldaholic.error.pawn_owned_table`), because owned-table stakes and payouts go through the
  owner's bankroll, which cannot hold items, levels or hearts.

#### 4.3.1 Items

Only items in the **appraisal table** are accepted, undamaged, unenchanted, unnamed, one stack
(the held stack). V = appraisal × count.

| Item | Value | Item | Value |
|------|-------|------|-------|
| Iron ingot | 2 | Diamond | 20 |
| Gold ingot | 4 | Diamond block | 180 |
| Emerald | 8 | Netherite scrap | 40 |
| Emerald block | 72 | Netherite ingot | 150 |
| Lapis block | 15 | Ancient debris | 45 |
| Golden apple | 30 | Enchanted golden apple | 300 |
| Totem of Undying | 150 | Heart of the Sea | 200 |
| Nether star | 400 | Elytra (full durability) | 500 |
| Trident (full durability) | 250 | Echo shard | 25 |

#### 4.3.2 XP levels

Stake L levels, 1 ≤ L ≤ current level, L ≤ 30. V = `floor(points(L) / 4)` where `points(L)` is
the vanilla XP-point total between level (current − L) and current. On loss, remove exactly those
levels (⚠ **CHANGED 2026-09 — review m3**: the player ends at level current − L and
**keeps** the partial progress towards the next level; the progress is not part of V and is never
staked).

#### 4.3.3 Temporary max hearts

- Stake h hearts, 1 ≤ h ≤ `wager.hearts.maxPerBet` (3). V = h × `wager.hearts.valuePerHeart` (100).
- On loss: add an attribute modifier to `max_health`: id `burmaldaholic:heart_wager_<n>`,
  amount −2h, operation add, expiring after `wager.hearts.durationTicks` (24000 = 1 MCD) of world
  time (counts while offline; persists through death/respawn; re-applied on join).
- Cap: the sum of active heart-wager penalties ≤ `wager.hearts.maxTotal` (5 hearts), and the
  resulting max health is never below 10 HP. Stakes that would break the cap are refused.
- If current health > new max health, health is clamped (no damage event, no death).

### 4.4 Hardcore Soul Wager

Only if the world is Hardcore **and** `wager.hardcoreSoulWager = true` (default **false**).
- Available on **Coin Flip only**. Requires two confirmations (UI.md §11.1) and a 5-second hold.
- V = max(`wager.soul.minValue` 1000, current balance). Win pays 1:1 → +V chips.
- Loss: player dies immediately (damage type `burmaldaholic:soul_wager`, bypasses armor, totems
  and Last Chance). Death message `death.attack.burmaldaholic.soul_wager`.
- Cooldown `wager.soul.cooldownTicks` (72000 = 3 MCD) per player.

---

## 5. Loan Shark and Debt Collectors [loan]

### 5.1 Loan Shark NPC

Entity `burmaldaholic:loan_shark`: humanoid (villager-sized, suit + gold chain texture), 40 HP,
invulnerable to players while a loan screen is open, neutral (never attacks), does not despawn,
cannot be leashed or traded with normally. Spawns 1 per village casino (§16.1) and in the Piglin
Parlor as a piglin-skinned variant ("Piglin Moneylender"); spawn egg in creative. Killing him
does **not** erase debt (debt belongs to the world bank); he drops 1 emerald and respawns in
his casino after 1 MCD. Right-click opens the Loan screen with a random greeting line.

### 5.2 Loan products

Only **one active loan** per player. Amounts fixed; availability by VIP tier.

| Loan | Principal | Deadline | Min VIP |
|------|-----------|----------|---------|
| Pocket money | 100 | 3 MCD | Bronze |
| Rent | 500 | 3 MCD | Bronze |
| Business | 2 000 | 5 MCD | Silver |
| Serious | 10 000 | 7 MCD | Gold |
| Life-changing | 50 000 | 7 MCD | Diamond |

**Interest (flat, charged once):** `due = ceil(principal × (1 + rate))`, where
`rate = baseRate(difficulty) − 0.02 × min(goodStanding, 5) − (VIP ≥ Platinum ? 0.02 : 0)`,
floored at 0.10.
`baseRate`: Peaceful/Easy 0.15, Normal 0.20, Hard/Hardcore 0.25. `goodStanding` is the count of
loans repaid on time (never decreases except on default, which resets it to 0).

**Deadline:** `deadlineTick = issueTick + days × 24000`. The HUD shows the remaining time when a
loan is active (UI.md §2).

**Cooldown:** after a default is fully repaid, no new loan for `loan.defaultCooldownDays` (5) MCD.

### 5.3 Repayment

- Pay any amount 1…min(balance, owed) at the Loan Shark or from the Casino Card.
- Paying the full owed amount closes the loan. On time → `goodStanding += 1`.
- Early repayment does not reduce interest.

### 5.4 Loan state machine

```
NONE ──take──▶ ACTIVE ──paid in full──▶ NONE (goodStanding+1)
                 │
        tick ≥ deadline
                 ▼
             DEFAULT ──paid in full──▶ NONE (goodStanding=0, cooldown 5 MCD)
                 │ each MCD boundary after deadline: owed += ceil(owedAtDeadline × lateFee)
                 │   (simple, not compound), capped at 2 × dueAtIssue
                 │ collection waves (§5.5) / Asset Freeze in Peaceful (§5.6)
```
Warnings: chat + sound at 1 MCD and at 2400 ticks (2 min) before the deadline.
While in DEFAULT: **garnishment** — 50 % (`loan.garnishPercent`) of every chip credit (ores, mobs,
trades, contracts, game winnings) goes to the debt first; withdraw/transfer disabled; new loans
disabled; the player cannot start a Dice Duel or own-casino deposit.

### 5.5 Debt Collectors (Easy/Normal/Hard/Hardcore)

**Wave schedule:** first wave spawns `loan.firstWaveDelayTicks` (600) after entering DEFAULT; then
one wave at every subsequent MCD boundary while in DEFAULT. If the debtor is offline, the wave is
queued and spawns 1200 ticks after they next join (max one queued wave). Only one live wave per
debtor at a time. Wave number w = 1, 2, 3, … (per default episode).

**Squad composition** by owed amount D at spawn time (then difficulty modifier, then escalation
`+ min(w − 1, 3)` extra Collectors):

| Owed D | Collectors | Repo Men | Accountant | Enforcer |
|--------|-----------|----------|------------|----------|
| < 500 | 2 | 0 | 0 | 0 |
| 500 – 2 999 | 2 | 1 | 0 | 0 |
| 3 000 – 14 999 | 3 | 2 | 1 | 0 |
| ≥ 15 000 | 4 | 2 | 1 | 1 |

Difficulty: Easy −1 Collector (min 1) and HP ×0.75; Hard/Hardcore +1 Collector and HP ×1.25.
Hard cap: 10 squad members.

**Units** (all are custom illager entities, team "collectors", immune to their own friendly fire,
not raid members, never join raids, don't pick up items):

| Unit | Id | Base HP | Weapon / attack | Speed | Notes |
|------|----|---------|-----------------|-------|-------|
| Collector | `debt_collector` | 24 | melee 5 (iron-axe look, "baseball bat" model) | 0.33 | vindicator AI, may open wooden doors |
| Repo Man | `repo_man` | 24 | crossbow 4–6 | 0.30 | keeps 8–12 blocks distance |
| Accountant | `accountant` | 28 | "Audit": 5 evoker-fang-like paper fangs, 6 dmg, cooldown 100 t; applies Weakness I 200 t on hit | 0.28 | squad leader, speaks the dialogue, no vexes |
| Enforcer | `enforcer` | 80 | melee 12, knockback 1.5 | 0.30 | knockback resistance 0.75, "Big guy" model |

**Spawn:** 24–40 blocks horizontally from the debtor, on a valid surface in the same dimension
(solid top face, 2 air blocks, light ignored, not in water/lava, inside world border). Up to 16
attempts per member; if no spot, retry the whole wave after 600 ticks. Not inside a boss-fight
zone (see chaos safety §13.4).

**Behavior state machine:**
```
APPROACH (non-hostile, 600 t max): walk to debtor.
   Leader within 6 blocks → NEGOTIATE: leader stops, says a demand line; debtor gets a form/screen
      "Pay in full" | "Pay part (≥ 50 %)" | "Refuse". 200 t to answer (timeout = Refuse).
      Pay in full  → loan closes → PAID.
      Pay ≥ 50 %   → owed reduced → LEAVING (this wave ends; next wave next MCD).
      Refuse       → HOSTILE.
   APPROACH timeout → HOSTILE.
HOSTILE: target only the debtor, and any entity that damages a squad member.
   Despawn after 6000 t, if debtor dies (after Repossession) or changes dimension (wave re-queued
   for next MCD), or if > 96 blocks away from debtor for 200 t.
PAID / LEAVING: stop attacking, say line, walk away 100 t, despawn with poof particles.
```
Any time the debt reaches 0 (by any means — payment, garnishment, admin), every live squad of
that debtor switches to PAID immediately.

**No griefing (regardless of `mobGriefing`):** never break or place blocks, never pick up items,
never trample farmland, never ignite anything; Repo Man bolts don't break anything. They may open
wooden doors (like vindicators). They never target other players, pets or villagers unless hit.

**Repossession (debtor killed by a squad member):**
`seized = min(owed, floor(balance × 0.5))` is moved from balance to the debt. Additionally the
single highest-appraised item (§4.3.1 table, by stack value) in the debtor's inventory is
removed and credited to the debt at appraisal value (message names the item). Chip items the
player dropped on death stay on the ground (collectors don't touch them). The wave then ends.

**Drops when killed:** Collector/Repo Man: 0–1 emerald, 10 % "Overdue Notice" (flavor paper item);
Accountant: 1–2 emeralds + "Ledger Page" (flavor) ; Enforcer: 2–4 emeralds, 1 iron block 25 %.
No chips, no XP beyond vanilla illager XP (5). Killing collectors does not reduce the debt.

### 5.6 Peaceful: Asset Freeze

No collectors spawn in Peaceful. Instead, at DEFAULT and at every MCD boundary while in DEFAULT:
`seize = min(owed, floor(balance × 0.5))` is taken from the balance, garnishment is 100 %, and the
player cannot wager at all until the debt is repaid. Switching difficulty mid-default applies the
new rule at the next wave/boundary.

### 5.7 Hardcore

Loans work as on Hard. Collectors use Hard scaling. Death to a collector is final (Hardcore
death); Last Chance never triggers against damage from a squad member in Hardcore.

### 5.8 Anti-grief / abuse rules

1. One loan at a time; no new loan while in default or during the post-default cooldown.
2. `withdrawable = balance − owed` (can't convert borrowed chips to items); no player-to-player
   balance transfers while owing (Dice Duel challenges also blocked).
3. Debt is world-bound and survives death, relog, dimension change and difficulty change.
4. Collectors never grief (above), don't spawn in other players' claimed casinos' *interior*
   (they spawn outside the claim and walk in), don't count toward mob-kill rewards, and cap one
   wave per MCD, so they cannot be farmed.
5. Operators: `/casino debt <player> clear|set <n>` (command).

---

## 6. Blackjack [blackjack]

### 6.1 Rules (defaults)

| Rule | Value | Config key |
|------|-------|-----------|
| Decks | 6 | `blackjack.decks` (1–8) |
| Shuffle | Reshuffle when ≥ 75 % of the shoe has been dealt, before the next round | `blackjack.penetration` |
| Dealer on soft 17 | **Stands (S17)** | `blackjack.dealerHitsSoft17` = false |
| Hole card / peek | Dealer takes a hole card; peeks for blackjack when the up-card is A or 10-value | — |
| Blackjack pays | 3:2 | `blackjack.blackjackPayout` = 1.5 |
| Double down | On any first two cards, for an amount equal to the original bet; exactly one more card | — |
| Double after split | Yes | `blackjack.doubleAfterSplit` |
| Split | Two cards of the **same rank** (K+K yes, K+Q no). Re-split up to **4 hands** total | `blackjack.maxHands` = 4 |
| Split aces | Once; each ace gets exactly one card; no re-split, no hit, no double; A+10 after split = 21, **not** blackjack (pays 1:1) | `blackjack.resplitAces` = false |
| Insurance | Offered when up-card is A; up to half the main bet; pays 2:1 | — |
| Even money | Offered instead of insurance when the player has blackjack vs A; pays 1:1 immediately | — |
| Surrender | Off | `blackjack.lateSurrender` = false |
| Push | Stake returned | — |

**House edge (6 decks, S17, DAS, resplit to 4, no RSA, no surrender, peek): ≈ 0.41 %**
with perfect basic strategy (typical players ~1.5–2 %). With H17: +0.22 %. Insurance bet itself:
HE 7.40 % (6 decks). Payout rounding: 3:2 on odd bets is floored (e.g. bet 5 → +7).

### 6.2 Hand values

A = 1 or 11, 2–10 face value, J/Q/K = 10. A hand is **soft** if it contains an ace counted as 11.
Blackjack = exactly two cards, A + 10-value, on an unsplit hand. Bust > 21.

### 6.3 Round state machine (one table, up to 5 seats)

```
BETTING   : seated players place bets (min..max). Ends when every seated player has pressed
            "Deal" or after blackjack.betTimerTicks (300 = 15 s) from the FIRST bet. Seats with
            no bet sit this round out.
DEAL      : shuffle if needed; one card up to each seat (seat order 1→5), dealer up-card,
            second card to each seat, dealer hole card (face down).
INSURANCE : if dealer up = A: each seat without BJ gets Insurance [0..bet/2] / No; seat with
            BJ gets Even money Yes/No. Timer 200 t → "No".
PEEK      : if dealer up ∈ {A,10-value}: check hole. Dealer BJ → reveal, settle all
            (player BJ pushes, insurance pays 2:1, others lose main bet) → SETTLE.
PLAYER_TURNS : seats in order; hands in order (split hands left→right).
            Actions: Hit, Stand, Double (2 cards only, balance ≥ bet), Split (pair, balance ≥ bet,
            hands < 4). Hand auto-stands at 21. Player BJ is settled immediately at 3:2 after peek.
            Turn timer blackjack.turnTimerTicks (400 = 20 s) → auto Stand. Disconnect → auto Stand.
DEALER    : reveal hole; if every player hand is bust or already settled, skip drawing.
            Else draw until total ≥ 17 (S17: stand on all 17; H17: hit soft 17).
SETTLE    : win 1:1, BJ 3:2, push returns, lose. Credit balances, update streak/VIP/contracts.
            Show result 60 t → BETTING.
```
Single player: the table starts DEAL immediately on "Deal" (no wait).

### 6.4 Limits (per main bet)

Standard table min 1, max = tier max. High-Roller table (End City, or configured by owner): min 100,
max = 2 × tier max, requires Gold VIP. Doubles/splits may exceed the max (they equal the bet).

---

## 7. Texas Hold'em [poker]

### 7.1 Format

No-Limit Texas Hold'em, 2–6 seats (6-max), cash game. One standard 52-card deck, shuffled every hand.

| Stake level | SB / BB | Buy-in (40–100 BB) | Min VIP |
|-------------|---------|--------------------|---------|
| Micro | 1 / 2 | 80 – 200 | Bronze |
| Low | 5 / 10 | 400 – 1 000 | Silver |
| Mid | 25 / 50 | 2 000 – 5 000 | Gold |
| High | 100 / 200 | 8 000 – 20 000 | Diamond |

The table block is configured by its placer (stake level, bots on/off, bot tier mix) — worldgen
tables have fixed configs (§16). Buy-in moves chips from balance to the table stack; leaving the
table (between hands) returns the stack to the balance. Top-up allowed between hands up to 100 BB.

### 7.2 Hand ranking (high to low)

Royal flush (A-K-Q-J-10 same suit, is a straight flush) > Straight flush > Four of a kind > Full
house > Flush > Straight > Three of a kind > Two pair > One pair > High card. Ace is high, or low
only in A-2-3-4-5 ("wheel", 5-high straight). Best 5 of 7 cards. Ties broken by kickers in rank
order; suits never break ties. Identical best-5 → split pot.

Evaluator contract (unit-tested with the same vectors):
`evaluate(cards[7]) -> int` where higher is better; `category << 20 | ranks packed 4 bits × 5`.

### 7.3 Hand flow

```
SEATING  : ≥ 2 players with chips (humans + bots) → start. Button moves 1 seat clockwise each hand
           (dead-button not used; if the next seat is empty it simply advances).
BLINDS   : SB = seat left of button, BB = next. Heads-up: button posts SB and acts first preflop.
           Players who can't cover the blind post all-in.
PREFLOP  : 2 hole cards each. Action starts left of BB. BB has the option.
FLOP     : burn 1, 3 community cards. Action starts left of button.
TURN     : burn 1, 1 card.  RIVER: burn 1, 1 card.
SHOWDOWN : last aggressor shows first (else first active left of button); others may muck when
           losing (auto-muck option; bots always show when winning).
AWARD    : pots from side pots outwards; odd chip to the first winner left of the button.
```
**Betting (No-Limit):** actions Fold, Check (if nothing to call), Call, Bet/Raise, All-in.
Min bet = BB. Min raise increment = the previous bet/raise increment in this round (≥ BB). An
all-in that is less than a full raise does **not** reopen betting for players who already acted.
Round ends when all active players have acted and matched the highest bet (or are all-in).

**Side pots:** at the end of each betting round, sort all-in contribution levels ascending;
for each level L build a pot = Σ over all players of min(contribution, L) − previous levels,
eligible = non-folded players with contribution ≥ L. Folded players' chips go into pots but they
are never eligible. Uncalled excess of the largest bet is returned to its owner before showdown.

**Timeouts:** each human action has `poker.actionTimerTicks` (600 = 30 s). Timeout → Check if
possible, else Fold. 2 consecutive timeouts → "sitting out" (auto-fold, blinds still posted
when due). Sitting out for 3 hands or disconnected at hand end → removed, stack returned to
balance. Being > 8 blocks from the table at hand start → sitting out.

**Rake (house edge on PvP pots):** `rake = min(floor(pot × 0.05), 3 × BB)`, taken from each pot
that saw a flop ("no flop, no drop") **and** had ≥ 2 human contributors. Rake goes to the table
owner's bankroll in an owned casino (§18), otherwise it is removed (bank sink). Pots where the
only human faces bots are not raked (the bots are the house).

### 7.4 Bots

Bots fill empty seats when `poker.botsEnabled` and ≥ 1 human is seated (max seats − humans − 1
free seat kept for walk-ins). Bots are **funded by the house**: each bot buys in for 100 BB from
nowhere; bot winnings disappear into the bank; a busted bot leaves and is replaced next hand.
Bot names come from a fixed list (not localized, e.g. "Lucky Steve", "Grandpa Pavel",
"Creeper42", "Mr. Blocksworth"); the tier is shown as a colored tag (Fish/Regular/Shark).
Think time: random 20–60 ticks per action.

Definitions: **Chen score** C (Bill Chen formula, integer-rounded up). **Position**: early =
first 2 to act preflop, late = cutoff/button, middle otherwise. **Equity E**: Monte-Carlo share of
pot won vs N random opponent hands (N = active opponents), with `samples` iterations, board
completed randomly. **Pot odds** `po = toCall / (pot + toCall)`.

| Tier | Preflop | Postflop | Randomness |
|------|---------|----------|------------|
| **Fish** | Calls with C ≥ 4; raises 3 BB only with C ≥ 12; calls any raise ≤ 20 % of stack if C ≥ 6; never folds a pocket pair preflop to a raise ≤ 10 BB | No simulation. Made hand ≥ one pair: call any bet ≤ pot; two pair+: bet 50 % pot; nothing: check/fold; bluff-bet 5 % | ±10 % sizing |
| **Regular** | Open-raise to 3 BB (+1 BB per limper) if C ≥ 8 early / 7 middle / 6 late or SB; call a raise if C ≥ 9; 3-bet to 3× if C ≥ 11; else fold (BB checks when possible) | `samples = 200`. E ≥ 0.65 → bet/raise 66 % pot; E ≥ po + 0.05 → call; no bet facing → check, except continuation-bet 50 % pot on flop 30 % of the time if preflop raiser; else fold | 10 % chance to take the next-lower action |
| **Shark** | Regular thresholds − 1; 3-bet bluff 8 % with suited connectors and suited aces; tracks each human's VPIP over the last 20 hands and widens calling range by 1 C when VPIP > 40 % | `samples = 500`, opponents who raised preflop sample only from their top 40 % Chen hands. E ≥ 0.85 → 20 % overbet all-in, 15 % slowplay (check/call), else raise 75 % pot; E ≥ 0.6 → bet 60 % pot; draws (flop E ≥ 0.30) semi-bluff 40 %; call if E ≥ po; fold otherwise | Sizing 50–75 % pot uniform |

Default seat mix when bots fill: Micro/Low: Fish 50 %, Regular 40 %, Shark 10 %; Mid: 30/50/20; High:
10/50/40 (config `poker.botMix.*`). Bots never collude (each decides only from its own cards and
public info). Performance note: Monte-Carlo may be spread over ticks (Bedrock `system.runJob`).

**House edge**: PvP: rake ≈ 3–5 % of raked pots. Vs bots: depends on skill; with the default mix
an average survival player loses ≈ 5 BB/100 hands; a skilled player can beat Fish/Regular tables
(intended — poker is the "skill" game).

---

## 8. Slots [slots]

### 8.1 Common mechanics

- Screen shows a 3×3 window (3 reels × 3 rows). **Each of the 9 cells is drawn independently**
  from the machine's symbol weights (no reel strips). This makes every payline's distribution
  identical, so per-line RTP = machine RTP.
- Bet = line bet × number of lines (fixed per machine; all lines always played).
- A line pays the **single best** combination on it. Evaluation per line (cells a, b, c left→right):
  1. If a = b = c and it is a special symbol (Creeper, TNT, Ender Pearl, Clock, Nether Star) →
     special result (table).
  2. If a = b = c = Wild → Wild payout.
  3. Else for every regular symbol S: if each cell is S or Wild → candidate payout(S). Take max.
     (Wild never substitutes for specials; three Wilds use rule 2.)
  4. Else "Berry" partial: count leading berries from the left (Wild does not count here):
     1 → pays 2×, 2 → pays 3×. (3 berries is covered by rule 3.)
- Payout per line = floor(multiplier × line bet). Spin result = sum of all lines.
- Special triggers are applied **after** crediting: at most one chaos event per spin (priority
  Star > Clock > Pearl > TNT/Creeper). If chaos is disabled, specials still pay their payout
  and no event fires.
- Spin animation: 40–60 ticks; server result is final before animation starts.

### 8.2 Tier 1 — "Copper Bandit" (`slot_machine_copper`)

1 payline (middle row). Line bet 1–min(tier max, 50). No jackpot.

| Symbol | Weight | 3 of a kind pays |
|--------|--------|------------------|
| Sweet Berries | 24 | 10× (1 lead → 2×, 2 lead → 3×) |
| Apple | 20 | 10× |
| Golden Carrot | 16 | 20× |
| Emerald | 12 | 30× |
| Diamond | 8 | 60× |
| Redstone Seven | 5 | 150× |
| Creeper | 15 | 0 → chaos `mob_wave` |
| **Total** | 100 | |

**RTP 89.76 %, HE 10.24 %.** Hit frequency 25.4 %. 3 Creepers 1 in 296 spins.

### 8.3 Tier 2 — "Golden Reels" (`slot_machine_gold`)

3 paylines (top, middle, bottom rows). Line bet 1–min(tier max/3, 100) → spin bet 3–300.
Progressive jackpot contribution 1 % of every spin bet.

| Symbol | Weight | 3 of a kind pays |
|--------|--------|------------------|
| Sweet Berries | 22 | 8× (1 lead → 2×, 2 lead → 3×) |
| Apple | 19 | 7× |
| Golden Carrot | 16 | 11× |
| Emerald | 12 | 25× |
| Diamond | 8 | 50× |
| Redstone Seven | 5 | 100× |
| Totem (Wild) | 3 | 200× (three Wilds) |
| Creeper | 8 | 0 → chaos `mob_wave` |
| Ender Pearl | 5 | 10× → chaos `random_teleport` |
| Nether Star | 2 | **Progressive jackpot** (§8.5) |
| **Total** | 100 | |

**Base RTP 92.71 % + jackpot 1.00 % = 93.71 %, HE 6.29 %.** Per-line hit 24.5 %.
Jackpot 1 in 125 000 lines (≈ 1 in 41 667 spins).

### 8.4 Tier 3 — "Netherite High Roller" (`slot_machine_netherite`)

5 paylines: 3 rows + 2 diagonals (top-left→bottom-right, bottom-left→top-right). Requires Gold VIP.
Line bet 2–min(tier max/5, 500) → spin bet 10–2 500. Jackpot contribution 1.5 %.

| Symbol | Weight | 3 of a kind pays |
|--------|--------|------------------|
| Sweet Berries | 20 | 8× (1 lead → 2×, 2 lead → 3×) |
| Apple | 19 | 9× |
| Golden Carrot | 16 | 14× |
| Emerald | 12 | 25× |
| Diamond | 9 | 50× |
| Redstone Seven | 6 | 100× |
| Totem (Wild) | 3 | 250× |
| TNT | 6 | 0 → chaos `mob_wave` |
| Ender Pearl | 5 | 10× → chaos `random_teleport` |
| Clock | 2 | 50× → **Golden Hour** starts server-wide (§13.3, cooldown respected; if on cooldown: pays 50× only) |
| Nether Star | 2 | **Progressive jackpot** |
| **Total** | 100 | |

**Base RTP 94.535 % + jackpot 1.50 % = 96.035 %, HE 3.965 %.** Per-line hit 22.5 %.

Contribution breakdown (per line, for tests): see appendix A.

### 8.5 Progressive jackpot

- One pool per tier per world (Gold, Netherite), stored in world data. Seeds: Gold 5 000,
  Netherite 50 000 (`slots.jackpot.seed.*`), minted by the bank.
- Every spin adds `floor(spinBet × contribution)`; fractional parts accumulate in a hidden
  remainder so the long-run rate is exact.
- Win (3 Nether Stars on any line): `award = floor(pool × min(1, spinBet / machineMaxSpinBet))`.
  Pool −= award; if pool < seed, pool = seed (bank tops up). Multiple star lines in one spin
  still award once. Server-wide announcement + chaos `diamond_rain` for the winner +
  `chip_shower` for every player within 16 blocks.
- Owned-casino machines (§18) have **no** progressive: 3 stars pay a fixed 1000× line bet
  (RTP then 93.51 % / 95.33 %).

---

## 9. European Roulette [roulette]

Single-zero wheel 0–36. Red: 1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36; the rest of 1–36 black;
0 green. Wheel order (clockwise, for animation): 0-32-15-19-4-21-2-25-17-34-6-27-13-36-11-30-8-23-10-5-24-16-33-1-20-14-31-9-22-18-29-7-28-12-35-3-26.

| Bet | Covers | Payout | HE |
|-----|--------|--------|----|
| Straight | 1 number (incl. 0) | 35:1 | 2.70 % |
| Split | 2 adjacent numbers on the layout (incl. 0-1, 0-2, 0-3) | 17:1 | 2.70 % |
| Street | 3 numbers in a row (1-2-3 … 34-35-36) | 11:1 | 2.70 % |
| Trio | 0-1-2 or 0-2-3 | 11:1 | 2.70 % |
| Corner | 4 numbers in a square (e.g. 1-2-4-5) | 8:1 | 2.70 % |
| First Four | 0-1-2-3 | 8:1 | 2.70 % |
| Six Line | 2 adjacent streets (e.g. 1–6) | 5:1 | 2.70 % |
| Dozen | 1–12, 13–24, 25–36 | 2:1 | 2.70 % |
| Column | 1st (1,4,…,34), 2nd (2,…,35), 3rd (3,…,36) | 2:1 | 2.70 % |
| Red / Black | 18 numbers | 1:1 | 2.70 % |
| Odd / Even | 18 numbers (0 is neither) | 1:1 | 2.70 % |
| Low (1–18) / High (19–36) | 18 numbers | 1:1 | 2.70 % |

Zero rule: all outside bets lose on 0 (`roulette.laPartage` = false; if true, even-money bets
lose only half on 0 → HE 1.35 % on those bets).

Limits: table min per spin 1; each individual bet ≥ 1; **total per spin ≤ tier max**; each
inside bet ≤ tier max / 4. High-roller table: min total 100, max 2 × tier max.

State machine: `BETTING (roulette.betTimerTicks 500 = 25 s from the first bet in multiplayer;
single player can press Spin) → NO_MORE_BETS (20 t) → SPIN (100 t animation) → RESULT (60 t,
settle) → BETTING`. Players may repeat last bets ("Rebet") or clear before NO_MORE_BETS. History of
the last 12 results is shown.

---

## 10. Craps [craps]

Two six-sided dice. One **shooter** at a time; any seated player (up to 6) may bet.

### 10.1 Bets

| Bet | Rules | Payout | HE |
|-----|-------|--------|----|
| Pass Line | Come-out: 7/11 win, 2/3/12 lose, else that number becomes the **point**. Then: point before 7 wins, 7 before point loses. Contract bet (cannot be removed after point is set). | 1:1 | 1.41 % |
| Don't Pass | Come-out: 2/3 win, **12 push (bar 12)**, 7/11 lose. Then: 7 before point wins, point before 7 loses. | 1:1 | 1.36 % |
| Come | Placed when a point is on; next roll acts as its own come-out for this bet (7/11 win, 2/3/12 lose, else moves to its come point). | 1:1 | 1.41 % |
| Don't Come | Mirror of Don't Pass for come bets (bar 12). | 1:1 | 1.36 % |
| Field | One roll: 3,4,9,10,11 pay 1:1; **2 pays 2:1; 12 pays 3:1**; 5,6,7,8 lose. | see rule | 2.78 % |
| Odds (take) | Behind Pass/Come once a point exists. Max **3-4-5×**: 3× on 4/10, 4× on 5/9, 5× on 6/8. | true odds 2:1 (4/10), 3:2 (5/9), 6:5 (6/8) | 0 % |
| Odds (lay) | Behind Don't Pass/Don't Come. Max = the lay that wins 3-4-5× the flat bet (3× on 4/10, 4× on 5/9, 5× on 6/8), i.e. a lay of up to 6× the flat bet. | 1:2 (4/10), 2:3 (5/9), 5:6 (6/8) | 0 % |

Odds-bet amounts are restricted so payouts are whole: take 5/9 → even amounts; take 6/8 →
multiples of 5; lay 4/10 → multiples of 2; lay 5/9 → multiples of 3; lay 6/8 → multiples of 6.
The UI snaps down to the nearest valid amount. Come-bet odds are **off** on the come-out roll
(returned if 7 on come-out). Combined Pass + full 3-4-5× odds HE ≈ 0.37 %.

### 10.2 Point logic / state machine

```
COME_OUT (puck OFF): bets allowed: Pass, Don't Pass, Field (and come-bet odds adjustments).
   roll → 7/11: pass wins, DP loses. 2/3: pass loses, DP wins. 12: pass loses, DP push.
          4,5,6,8,9,10: point = roll, puck ON → POINT.
POINT: bets allowed: Come, Don't Come, Field, Odds. Pass/Don't Pass can only be placed on the
   come-out roll (v1 rule).
   roll = point → pass wins (+odds), DP loses; puck OFF → COME_OUT (same shooter).
   roll = 7     → "seven-out": pass loses, DP wins, all come bets with points lose, don't-come
                  with points win; field loses; puck OFF → COME_OUT, **next shooter**.
   other rolls  → resolve field and come/don't-come moves.
```
Shooter must have a Pass or Don't Pass bet on come-out. Roll button available to the shooter
only; timer `craps.rollTimerTicks` (400 = 20 s) → auto roll. Betting window before each roll:
`craps.betWindowTicks` (160 = 8 s) in multiplayer. Shooter rotation: clockwise among seated
players with a line bet; single player is always the shooter.

Limits: each flat bet (Pass/DP/Come/DC/Field) 1…tier max; odds not counted in the max.

---

## 11. Extras [extras]

### 11.1 Coin Flip (Lucky Coin item `lucky_coin`, usable anywhere, vs the house)

Choose Heads/Tails, stake 1…tier max. Win pays **0.96:1** (floor) i.e. 1.96× total.
**RTP 98.0 %, HE 2.0 %.** Streak adjustment applies (§14). Pawn stakes and Soul Wager allowed.
Lucky Coin: crafted from 1 gold ingot + 1 chip_5 (shapeless), not consumed.

### 11.2 Wheel of Fortune (`wheel_of_fortune` block, 3×3 face multiblock-looking model, 1 block hitbox)

Single bet 1…tier max/2; spin; the segment's multiplier × stake is paid (floor). 54 segments:

| Segment | Count | Multiplier |
|---------|-------|-----------|
| Bust | 25 | 0× |
| Creeper | 1 | 0× → chaos `mob_wave` |
| Half back | 5 | 0.5× |
| Money back | 11 | 1× |
| Double | 7 | 2× |
| Triple | 3 | 3× |
| Emerald | 1 | 5× |
| Diamond | 1 | 10× |

**RTP = 51.5/54 = 95.37 %, HE 4.63 %.** Segment order around the wheel (index 0–53) is fixed in
appendix B so the wheel animates identically for every viewer.

### 11.3 Scratch Cards (items)

Bought from a Croupier NPC, the Cashier "Shop" tab or loot. The outcome is decided on **first
scratch** (server), stored in the item's data, so unscratched cards are fungible and streak applies.

| Card | Price | Min VIP | Prizes (chips : probability) | RTP |
|------|-------|---------|------------------------------|-----|
| `scratch_card` (Basic) | 10 | Bronze | 10 : 0.22, 20 : 0.10, 50 : 0.03, 100 : 0.01, 500 : 0.002, 2 500 : 0.0001; else 0 | **79.5 %** (HE 20.5 %) |
| `scratch_card_gold` | 100 | Silver | 100 : 0.20, 200 : 0.10, 500 : 0.05, 1 000 : 0.01, 5 000 : 0.001, 25 000 : 0.0002; else 0 | **85.0 %** (HE 15 %) |

Card face: 3×3 cells with prize symbols. Winning card: exactly 3 cells show the prize symbol;
the other 6 show other symbols, each at most twice. Losing card: no symbol appears 3+ times.
1 % of losing cards are **Creeper cards** (3 creeper cells, no prize) → chaos `mob_wave`.
Player scratches cell by cell (Java: click cells).
Unscratched card stack 16; scratched card becomes `scratch_card_used` (junk, stack 64).

### 11.4 Plinko (`plinko_machine` block)

12 rows of pegs, ball ends in bin k ~ Binomial(12, ½) (12 independent left/right coin flips;
animate that exact path). Choose risk Low/Medium/High; bet 1…tier max/5. Payout = floor(bet × mult).

| Bin | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|-----|---|---|---|---|---|---|---|---|---|---|----|----|----|
| P (×4096) | 1 | 12 | 66 | 220 | 495 | 792 | 924 | 792 | 495 | 220 | 66 | 12 | 1 |
| Low | 10 | 3 | 1.6 | 1.4 | 1.0 | 1.0 | 0.5 | 1.0 | 1.0 | 1.4 | 1.6 | 3 | 10 |
| Medium | 33 | 11 | 4 | 2 | 1.0 | 0.6 | 0.3 | 0.6 | 1.0 | 2 | 4 | 11 | 33 |
| High | 170 | 24 | 8.1 | 2 | 0.6 | 0.2 | 0.2 | 0.2 | 0.6 | 2 | 8.1 | 24 | 170 |

**RTP: Low 96.56 %, Medium 96.57 %, High 96.70 %** (HE ≈ 3.4 %).

### 11.5 Dice Duel

**Vs house** (at any Craps table's side menu or `dice` item used on air): player and dealer each roll
2d6; higher total wins 1:1; ties **push**, except a tie on **7** which the house wins.
**HE = (6/36)² = 2.78 %.** Bet 1…tier max. Pawn stakes allowed.

**PvP**: use the `dice` item on another player (or Casino Card → Challenges) → the target gets a
challenge (stake S, 30 s to accept). Both stakes are escrowed from balances; each rolls 2d6,
higher wins the pot minus `extras.diceDuel.pvpRakePercent` (0 %); ties re-roll up to 3 times,
then both stakes are refunded. Both players must be within 16 blocks, not in debt default, and
not the same player. Max 1 pending outgoing challenge per player.

---

## 12. VIP tiers [vip]

Tier by **lifetime chips wagered** W (never decreases; tiers are never lost).

| # | Tier | W ≥ | Max bet | Perks |
|---|------|-----|---------|-------|
| 0 | Bronze | 0 | 100 | Micro poker, Basic scratch cards, loans 100/500 |
| 1 | Silver | 5 000 | 250 | Low poker, Gold scratch cards, loan 2 000, +5 % contract rewards, silver name tag on tables |
| 2 | Gold | 25 000 | 1 000 | Netherite slots, High-Roller tables, Mid poker, loan 10 000, emerald buy rate 9, +10 % contracts, 2 % cashback, gold win particles |
| 3 | Platinum | 100 000 | 2 500 | 4 contract slots, loan interest −2 %, 3 % cashback, "Platinum" chat title |
| 4 | Diamond | 500 000 | 10 000 | High poker, loan 50 000, 5 contract slots, 4 % cashback, diamond Casino Card skin |
| 5 | Netherite | 2 500 000 | 50 000 | 5 % cashback, Netherite aura particles on wins, server-wide announcement on promotion to Netherite |

**Cashback** (⚠ **CHANGED 2026-09 — balance fix; Java must follow**): at each MCD boundary,
`cashback = floor(rate × theoreticalLossToday)` where
`theoreticalLossToday = Σ (stake × houseEdge(game, bet))` over the day's house-banked chip rounds
(bank-banked only: never PvP poker, never owned casinos, never pawn stakes). It is paid whatever
the day's actual result was.

- `houseEdge` = the §17 edge of the bet. Where a game has several bets/variants, use the bet's
  own edge when the game knows it (slots per tier, plinko per risk, scratch per card, craps
  Odds = 0 %), otherwise the **lowest** edge of that game (blackjack 0.41 % incl. doubles,
  splits and insurance; craps flat bets 1.36 %; roulette 2.70 %; coin flip 2 %; wheel 4.63 %;
  dice duel 2.78 %). Baccarat (§20) knows each bet's edge (Banker 1.06 %, Player 1.24 %, Tie
  14.36 %, pairs 10.36 %). Ultimate Texas Hold'em (§21): Ante + Blind + Play chips use the element
  of risk **0.53 %**, Trips its paytable's computed edge (1.90 % at defaults).
- Expected cashback = `rate × HE × wagered` < `HE × wagered` (rate ≤ 0.5), so the effective edge
  is `HE × (1 − rate) > 0` for every game and tier.
- Why: the old formula `floor(max(0, netLossToday) × rate)` was **+EV** for Gold+ players on
  low-edge games — `E[max(0, dayLoss)]` of a high-variance day is far larger than the edge
  (e.g. 5 × 1 000-chip blackjack hands a day at Netherite: ≈ 0.9 % back vs a 0.41 % edge).
- Ledgers stored before the change (no theoretical loss) pay no cashback for that day.

Promotion: title + sound + chat; cosmetics are client-side particle/name-color only.

---

## 13. Chaos layer [chaos]

Toggle `chaos.enabled` (default true). Chaos never runs in dormant mode.

### 13.1 Triggers

1. **Ambient roll**: every `chaos.ambientIntervalTicks` (6000 = 5 min) per eligible online player,
   with probability `chaos.ambientChance` (0.08) choose an event from the ambient weights.
2. **Slot/wheel/scratch specials** (§8, §11) → the named event (bypasses the ambient chance, but
   still respects safety and per-player cooldown — if blocked, the payout still happens).
3. **Big win**: a single settlement with net profit ≥ 50 × stake and ≥ 500 chips → 30 % chance of
   `lucky_buff`.
4. **Jackpot** → `diamond_rain` for winner + `chip_shower` for players within 16 blocks.
5. **Sunset roll** (Golden Hour): once per MCD at time-of-day 12000, probability
   `chaos.goldenHour.sunsetChance` (0.10).

Per-player cooldown between events: `chaos.playerCooldownTicks` (3000). Golden Hour has its own
cooldown (below).

### 13.2 Events

| Id | Kind | Ambient weight | Effect | Duration |
|----|------|----------------|--------|----------|
| `chip_shower` | good | 18 | 20–100 chips as chip items (chip_1 and chip_5 mix) pop out around the player (radius 2) | instant |
| `lucky_buff` | good | 20 | One of: Speed II, Haste II, Regeneration I, Strength I, Luck I, Jump Boost II, Fire Resistance | 1200–3600 t |
| `diamond_rain` | good | 4 | 3–6 diamonds (Hard/Hardcore: 2–5) drop from 6 blocks above within radius 3, or at feet if no headroom; particle "rain" | 100 t |
| `xp_fountain` | good | 12 | 50–150 XP in orbs | 60 t |
| `curse` | bad | 16 | One of: Slowness I, Mining Fatigue I, Hunger I, Weakness I, Bad Luck (Unluck) I, Glowing | 600–1800 t |
| `mob_wave` | bad | 12 | N hostile mobs (§2.3 counts) at 8–16 blocks; mobs tagged `burmaldaholic:chaos` | mobs despawn after 6000 t |
| `random_teleport` | neutral | 8 | Teleport 32–256 blocks horizontally to a safe spot | instant |
| `weather_change` | neutral | 8 | Overworld only: clear → rain, rain → thunder, thunder → clear | 6000 t |
| `golden_hour` | good, **server-wide** | 2 | §13.3 | 3600 t |

Mob-wave composition by dimension: Overworld zombies/skeletons/spiders (equal), Nether
skeletons/magma cubes (size 2), End endermites/skeletons. No creepers (no block damage ever).

### 13.3 Golden Hour

- Server-wide. While active, **net winnings** of house-banked games (all except PvP poker, PvP
  dice, chemin de fer coups §20.9 and player-banked hold'em rounds §21.9) are multiplied by `chaos.goldenHour.multiplier` (2.0): bonus = floor(netWin × (m − 1)),
  paid by the bank (also at owned casinos — owners never pay it).
- Bonus cap per player per Golden Hour: `chaos.goldenHour.bonusCap` (5 000).
- Duration 3600 t (3 min). Cooldown between Golden Hours: `chaos.goldenHour.cooldownTicks`
  (24000). Start/end announced to all players (title + chat + bell sound); HUD shows a timer.

### 13.4 Safety rules (all events)

An event is **skipped** (and not rerolled, except `mob_wave` in Peaceful which is rerolled from the
remaining events) if any applies:
- Player is in Creative or Spectator, dead, or within 200 t after respawn, or sleeping.
- Player has a casino screen/form open → event is **deferred** until closed (max 600 t, then skip).
- `mob_wave`: difficulty Peaceful; player within 64 blocks of a Wither, Warden or Ender Dragon;
  inside a claimed casino (§18) interior; fewer than N valid spawn spots found in 16 attempts each.
- `random_teleport`: player gliding, riding, falling (fall distance > 3), in a boss zone (as above),
  or during a card/table round. Target must be: inside world border, same dimension, chunk
  already generated **or** loadable (max 1 synchronous chunk load), a solid full top block that
  is not lava/magma/fire/campfire/cactus/sweet berry bush/powder snow/pointed dripstone/water,
  2 air (non-liquid) blocks above, Y > dimension min Y + 5, Nether Y < 120 (never onto the roof),
  End: only above end stone with ≥ 3 solid blocks below. 16 attempts; failure → skip silently.
  Pets that are sitting stay; leashed mobs are not teleported. After teleport: 60 t Resistance V.
- `diamond_rain`/`chip_shower` never spawn items into lava/void (spawn at feet instead).
- `weather_change` only in the Overworld; elsewhere reroll once from good events.
- Buff/curse effects are never lethal (no Poison, Wither, Instant Damage, Levitation).

---

## 14. Lucky / Unlucky streak [streak]

Per player integer **S ∈ [−10, +10]**, persisted.

**Update on each settled house-banked or PvP wager** with stake ≥ 1:
- Win (net > 0): `S = max(S, 0) + 1`, capped at +10.
- Loss (net < 0): `S = min(S, 0) − 1`, capped at −10.
- Push / net 0: unchanged.
- Decay: for every `streak.decayTicks` (12000) of world time without a settled wager, S moves 1
  toward 0.

**Effect** — only on **RNG games** (slots, wheel, plinko, scratch cards, coin flip). Table games
(blackjack, poker, roulette, craps, dice duel, baccarat §20, Ultimate Texas Hold'em §21) are always
honest; the streak there is cosmetic.
After the outcome is drawn, if it is a **losing outcome** (total return < stake), with probability
`r` the whole outcome is re-drawn once and the second draw is final:
```
r_raw = S > 0 ? 0.005 × S          (lucky, max 0.05)
      : S < 0 ? 0.003 × |S|        (pity, max 0.03)
      : 0
r_cap = max(0, (1 − streak.minHouseEdge) / RTP_game − 1)   // minHouseEdge = 0.01
r     = min(r_raw, r_cap)
```
Proof of the cap: a re-draw replaces a return < stake with an expected return RTP, so
`RTP' ≤ RTP × (1 + r) ≤ 1 − minHouseEdge`. The house edge therefore never drops below 1 % on
any game, whatever the streak. `RTP_game` values are the constants in §17 (Plinko uses the chosen
risk's value; slots use the tier's total RTP incl. jackpot).

Examples: coin flip r_cap = 0.0102; Copper slots r_cap = 0.103 (so full 5 % applies).

**HUD:** S > 0 shows flame icon(s) and "Lucky ×S" in gold; S < 0 shows a rain-cloud icon and
"Unlucky ×|S|" in gray-blue; |S| ≥ 7 pulses. S = 0 hides the streak line.
Additionally |S| = 10 grants the advancements "On Fire" / "Black Cat".

---

## 15. Last Chance [lastchance]

### 15.1 Trigger

When a player would die (Java: Fabric `ServerLivingEntityEvents.ALLOW_DEATH`; after vanilla
totem check), and all conditions hold:
- casino mode on, `lastChance.enabled`, not Hardcore (see §15.3 for Hardcore),
- the player holds no Totem of Undying (vanilla totem takes priority),
- damage type is not `out_of_world`/void, `/kill` (`generic_kill`), `soul_wager`,
- cooldown ready: `worldTime ≥ lastChanceUsedAt + lastChance.cooldownTicks` (24000),
then flip: success with probability p(difficulty) (§2.3: 0.60 / 0.60 / 0.50 / 0.40).

**Success:** death is cancelled; health = ceil(maxHealth / 2); fire extinguished; Resistance V for
60 t and Regeneration I for 100 t; cost `floor(balance × lastChance.costPercent/100)` (10 %) taken
from balance; title "HEADS — Last Chance!"; totem-like particles + coin sound.
**Failure:** normal death proceeds; title "TAILS…" shown on the death screen chat.
The cooldown starts on **either** outcome. Cooldown remaining is shown in the Casino Menu.

### 15.2 Multiplayer

Last Chance announcement goes to all players ("%1$s flipped a coin with Death and won").

### 15.3 Hardcore

`lastChance.hardcoreMode` enum: **`DISABLED` (default)** | `HIGH_STAKES`.
HIGH_STAKES: success chance 0.50 (fixed); eligible only if balance + chip items in inventory ≥ 100
and max health ≥ 8 HP; cooldown 120000 t (5 MCD).
- Success: balance set to 0 **and** all chip items in the player's inventory destroyed, **and** a
  permanent `max_health` modifier −2 HP (`burmaldaholic:last_chance_scar`, stacks) is applied;
  revive at half (new) max health.
- Failure: Hardcore death (vanilla spectator). The stake is not taken (you're dead anyway).
The setting is chosen in the setup (Bedrock) / config + world-creation (Java) and shown in
the world's Casino Menu "Rules" page so players know.

---

## 16. Worldgen [worldgen]

All structures only generate in newly generated chunks while `worldgen.enabled` (and the Java data
pack) is on. Tables in structures are ordinary blocks (breakable; drop themselves) flagged as
**house tables** (bank-funded). Structure NBT/`.mcstructure` files are authored once and exported
for sizes below are bounding boxes (X × Y × Z).

### 16.1 Village Casino ("Lucky Villager")

- Size 17 × 10 × 17, one palette variant per village type (plains, desert, savanna, taiga, snowy)
  via block replacement processors.
- Frequency target: **≈ 1 per 3 villages** (`worldgen.villageCasino.chance` 0.35).
  Java: added as a jigsaw element to each village's `houses` pool with a weight calibrated to that
  frequency, max 1 per village. Bedrock: custom jigsaw structure with the village structure-set
  spacing (34/8) in village biomes, frequency 0.35 — so it may appear next to or near villages
  (accepted edition difference).
- Contents: Cashier ×1; Blackjack table ×1 (standard); Roulette table ×1; Copper Bandit ×3;
  Golden Reels ×1; Wheel of Fortune ×1; Loan Shark ×1; Croupier (villager-like NPC, sells scratch
  cards 10/100 chips and Lucky Coins 25 chips) ×1; neon-ish glowstone/redstone-lamp sign "CASINO";
  back room with 1 loot chest `burmaldaholic:chests/village_casino`.
- ⚠ **CHANGED 2026-09 — new games §20/§21 re-export the structure files**: plus an
  **Ultimate Texas Hold'em table ×1** (standard, `uth_table`) along the back wall (bounding box
  unchanged). Chunks generated before the update keep the old layout.
- Loot (4–7 rolls): chip_1 ×5–20 (w 30), chip_5 ×2–8 (w 25), chip_25 ×1–3 (w 12), scratch_card
  ×1–3 (w 15), emerald ×2–6 (w 12), golden carrot ×2–5 (w 8), lucky_coin ×1 (w 5), casino_card
  (w 3).

### 16.2 Nether casino — "Piglin Parlor"

- Inside bastion remnants: `worldgen.piglinParlor.chance` 0.30 of bastions, placed as an extra room
  appended to the bastion (Java: added to bastion jigsaw pools).
- Size 21 × 12 × 21, blackstone/gold/crimson palette.
- Contents: Craps table ×1; Poker table ×1 (Low stakes, 3 bots, mix Regular-heavy); Golden Reels ×2;
  Plinko ×1; Nether Cashier ×1 (gold ingot exchange); **Piglin Dealers ×2** (piglin model with vest,
  neutral, never zombify, admire gold but never take items); Piglin Moneylender (Loan Shark variant) ×1.
- ⚠ **CHANGED 2026-09 — new games §20/§21**: plus a **Baccarat table ×1** (standard,
  `baccarat_table`) — gold for gold, the piglins' favourite. One of the two Piglin Dealers stands
  behind it (cosmetic; the table works without a dealer). Bounding box unchanged.
- Piglin Dealers are **not** bastion piglins: normal bastion piglins still behave vanilla; entering
  the Parlor does not anger them unless blocks/chests are broken (vanilla rules).
- Loot `chests/piglin_parlor` (5–8 rolls): gold ingot ×4–12, gold block ×1 (w 8), chip_25 ×2–6,
  chip_100 ×1–2 (w 10), scratch_card_gold ×1 (w 8), netherite scrap ×1 (w 3), lucky_coin (w 5).

### 16.3 End City High Roller Lounge

- `worldgen.highRoller.chance` 0.20 of End Cities, placed as a top-floor room on a tower.
- Size 15 × 9 × 15 (13 × 9 × 13 before the 2026-09 games, see below), purpur/obsidian/end-rod palette.
- Contents: Netherite High Roller slots ×2; High-Roller Blackjack table ×1 (min 100, max 2× tier,
  Gold VIP); High-Roller Roulette ×1 (min 100); Cashier ×1; **Shulker Croupier** (cosmetic
  shulker-skinned NPC, sells gold scratch cards).
- ⚠ **CHANGED 2026-09 — new games §20/§21; structure re-exported, size now 15 × 9 × 15**: plus a
  **High-Roller Baccarat table ×1** (`baccarat_table_high_roller`, min 100 per coup, Gold VIP) with a
  Baccarat Dealer NPC, and a **High-Roller Ultimate Texas Hold'em table ×1**
  (`uth_table_high_roller`, min Ante 50, Gold VIP). These worldgen tables (and creative / `/give`)
  are the only source of the High-Roller variants, like the other High-Roller tables.
- Loot `chests/high_roller` (3–5 rolls): chip_100 ×2–5, chip_500 ×1–2 (w 10), diamond ×2–6,
  enchanted book (random, w 10), scratch_card_gold ×1–2, `golden_chip` trophy (w 4, decorative,
  "Worth nothing. Priceless.").

### 16.4 Craftable blocks (so players can build casinos)

| Block | Recipe (shaped) |
|-------|-----------------|
| `blackjack_table` | green wool ×3 / planks ×3 / fence, chip_25, fence |
| `poker_table` | green wool ×3 / dark oak planks ×3 / fence, chip_100, fence |
| `roulette_table` | green wool ×3 / planks ×3 / fence, compass, fence |
| `craps_table` | green wool ×3 / planks ×3 / fence, dice, fence |
| `baccarat_table` | green wool ×3 / planks ×3 / fence, gold ingot, fence |
| `uth_table` | green wool ×3 / dark oak planks ×3 / chip_25, fence, chip_25 |
| `baccarat_table_player_banked` | shapeless: `baccarat_table` + chip_100 (Chemin de fer, §20.9) |
| `uth_table_player_banked` | shapeless: `uth_table` + chip_100 (§21.9) |
| `slot_machine_copper` | copper ingot ×7, redstone, chip_5 |
| `slot_machine_gold` | gold ingot ×7, redstone block, chip_25 |
| `slot_machine_netherite` | netherite ingot, gold block ×6, redstone block, chip_100 |
| `wheel_of_fortune` | planks ×4, stick ×3, clock, chip_25 |
| `plinko_machine` | glass ×3, iron bars ×4, iron ingot, chip_5 |
| `cashier` | §3.2 |
| `casino_charter` | §18.2 |
| `dice` (item ×2) | bone block + black dye |

Blocks require no power; the machine *is* the dealer.

---

## 17. House edge summary (defaults)

| Game | RTP | House edge | Streak-adjustable |
|------|-----|-----------|-------------------|
| Blackjack (basic strategy) | 99.59 % | 0.41 % | no |
| Blackjack insurance | 92.60 % | 7.40 % | no |
| Poker vs players | rake ≤ 5 % of raked pots | — | no |
| Slots — Copper Bandit | 89.76 % | 10.24 % | yes |
| Slots — Golden Reels | 93.71 % | 6.29 % | yes |
| Slots — Netherite High Roller | 96.035 % | 3.965 % | yes |
| Roulette (any bet) | 97.30 % | 2.70 % | no |
| Craps Pass / Come | 98.59 % | 1.41 % | no |
| Craps Don't Pass / Don't Come | 98.64 % | 1.36 % | no |
| Craps Field | 97.22 % | 2.78 % | no |
| Craps Odds | 100 % | 0 % | no |
| Coin Flip | 98.00 % | 2.00 % | yes |
| Wheel of Fortune | 95.37 % | 4.63 % | yes |
| Scratch Basic / Gold | 79.5 % / 85.0 % | 20.5 % / 15.0 % | yes |
| Plinko L / M / H | 96.56 / 96.57 / 96.70 % | ≈ 3.4 % | yes |
| Dice Duel vs house | 97.22 % | 2.78 % | no |
| Baccarat Banker (5 % commission) | 98.94 % | 1.06 % | no |
| Baccarat Player | 98.76 % | 1.24 % | no |
| Baccarat Tie (8:1) | 85.64 % | 14.36 % | no |
| Baccarat Player Pair / Banker Pair (11:1) | 89.64 % | 10.36 % | no |
| Ultimate Texas Hold'em (optimal play) | 99.47 % of all chips wagered | 2.19 % of the Ante (element of risk 0.53 %) | no |
| Ultimate Texas Hold'em Trips (50-40-30-8-6-5-3) | 98.10 % | 1.90 % | no |
| Chemin de fer (PvP) | banker 98.94 % / punters 98.76 % | rake 5 % of banker wins ≈ 2.29 % of covered chips | no |
| Player-banked Ultimate Texas Hold'em (PvP) | seats as above | rake 1 % of the banker's positive net per round | no |

Testers: every RNG game gets a 10⁷-round Monte-Carlo test asserting |RTP − expected| < 0.3 %
(Plinko High and Netherite slots: 10⁸ or exact enumeration, due to variance). Baccarat and UTH
Trips are tested by **exact enumeration** (§20.8, §21.8); the UTH base game by a Monte-Carlo of the
reference strategy R (§21.8).

---

## 18. Multiplayer [multiplayer]

### 18.1 Player-hosted tables

Any placed table/machine outside a claim is a **house table** (bank-funded). Multiplayer tables:
Blackjack (5 seats), Poker (6 seats), Roulette (8 bettors), Craps (6 seats), Baccarat (7 seats,
§20; player-banked Chemin de fer variant §20.9), Ultimate Texas Hold'em (6 seats + dealer seat,
§21; player-banked variant §21.9). Seat by using the table;
leave with the "Leave" button or by walking > 8 blocks away (between rounds; mid-round the
disconnect rules of each game apply). Spectators within 8 blocks see public table state (Java:
render over the table).

### 18.2 Player-owned casinos

- **Casino Charter** block (`casino_charter`), recipe: gold block, emerald block, gold block /
  chip_500, lodestone, chip_500 / gold block ×3. Placing it costs the license fee
  `ownership.licenseFee` (1 000 chips, from balance; refused if unaffordable).
- Claim: cylinder of radius `ownership.claimRadius` (24) blocks around the charter, full height.
  Must not overlap another claim or spawn protection. Max `ownership.maxPerPlayer` (1) charters.
- Every casino block placed **by the owner** inside the claim links to it (becomes an *owned
  table*). Blocks placed before the claim can be linked from the charter screen.
- **Owner bankroll**: separate account on the charter. Owner deposits/withdraws from/to balance.
  All owned-table stakes go into the bankroll; all payouts come from it. The owner therefore
  earns the house edge. Poker rake at owned tables → bankroll.
- Owner settings per table: min bet, max bet (≤ global table max; tier max still applies to the
  player), open/closed, bots on/off (poker). Owner **cannot** play at their own tables.
- **Solvency (reservation rule):** before accepting any stake, compute the round's worst-case
  payout (max total the house could pay for all bets currently placed in that round, e.g.
  roulette: max over the 37 outcomes; blackjack: 8 × bet (4 hands doubled) + insurance; slots:
  highest line pay × lines × line bet; plinko: max mult × bet; craps: sum of max wins incl. odds;
  baccarat: max over its 12 outcome classes, §20.6; Ultimate Texas Hold'em: `505 × Ante + 50 ×
  Trips` per seat, §21.6).
  `reserved += worstCase`. Accept only if `reserved ≤ bankroll`. On settlement, bankroll changes by
  the real result and the reservation is released. Owner withdrawals are limited to
  `bankroll − reserved`.
- **Insolvency:** if bankroll < the smallest worst-case of any table at its minimum bet, all owned
  tables show **"Closed — the house is broke"** and refuse bets; the owner is notified. They
  reopen automatically when the bankroll is topped up. The bank never pays for an owner.
- Protection: only the owner (and ops) can break linked tables and the charter; explosions don't
  destroy them. Other blocks in the claim are **not** protected (this is not a land-claim mod).
- Breaking the charter: bankroll (minus reservations, after open rounds settle) is returned to
  the owner's balance; linked tables become **inactive** (not house tables) until re-linked.
- Golden Hour bonuses and cashback at owned tables are paid by the bank, not the owner.
- Statistics on the charter screen: today's/total handle, payouts, rake, profit.

---

## 19. Advancements [advancements]

Java: real advancements (tab "Burmaldaholic", background: green felt). Bedrock: no custom
advancements → **Achievements** page in the Casino Menu + toast (title bar) using the same keys;
progress stored in player dynamic property. Keys: `advancement.burmaldaholic.<id>.title` /
`.description`.

| Id | Parent | Condition | Frame |
|----|--------|-----------|-------|
| `root` | — | Casino mode on and player holds any chip or Casino Card | task |
| `first_bet` | root | Settle any wager | task |
| `beginners_luck` | first_bet | Win a wager | task |
| `natural` | beginners_luck | Get a blackjack | task |
| `split_personality` | natural | Play 4 hands from splits in one round | goal |
| `royal_flush` | beginners_luck | Win a poker pot with a royal flush | challenge |
| `shark_hunter` | beginners_luck | Bust a Shark bot | goal |
| `three_sevens` | beginners_luck | Hit 3 Redstone Sevens on a slot line | goal |
| `jackpot` | three_sevens | Win a progressive jackpot | challenge |
| `zero_hero` | beginners_luck | Win a straight bet on 0 | goal |
| `hot_shooter` | beginners_luck | Make 3 points in a row as craps shooter | goal |
| `plinko_edge` | beginners_luck | Land in bin 0 or 12 on Plinko High | challenge |
| `scratch_top` | first_bet | Win the top prize on any scratch card | challenge |
| `on_fire` | beginners_luck | Reach streak +10 | goal |
| `black_cat` | first_bet | Reach streak −10 | goal |
| `loan_taken` | root | Take a loan | task |
| `knock_knock` | loan_taken | Default on a loan | task |
| `hostile_takeover` | knock_knock | Kill an entire Debt Collector squad | goal |
| `clean_slate` | loan_taken | Repay a loan on time | task |
| `not_today` | root | Survive via Last Chance | goal |
| `scarred` | not_today | Survive a High-Stakes Last Chance in Hardcore | challenge |
| `heart_on_the_line` | first_bet | Win a heart wager | task |
| `devils_deal` | heart_on_the_line | Win a Soul Wager | challenge |
| `golden_hour` | root | Win during Golden Hour | task |
| `beam_me_up` | root | Be teleported by chaos | task |
| `vip_silver` … `vip_netherite` | chain from root | Reach each VIP tier (5 entries: silver, gold, platinum, diamond, netherite) | task/goal/challenge for netherite |
| `the_house` | root | Place a Casino Charter | goal |
| `house_always_wins` | the_house | Owned casino earns 10 000 net profit | challenge |
| `bankrupt` | the_house | Your casino closes for insolvency | task |
| `piglin_parlor` | root | Enter a Piglin Parlor | task |
| `high_roller` | piglin_parlor | Place a bet in the End City High Roller Lounge | goal |
| `baccarat_natural` | beginners_luck | Win a Player or Banker bet whose hand is a **natural 9** (two cards totalling 9) | task |
| `tie_streak` | baccarat_natural | Win Tie bets on **two consecutive coups** at the same baccarat table | goal |
| `uth_four_x` | beginners_luck | Bet 4× preflop at Ultimate Texas Hold'em and win that Play bet | task |
| `banco` | baccarat_natural | Call **Banco** at a chemin de fer table and win that coup | goal |
| `bank_holder` | banco | Keep one chemin de fer bank through **5 winning coups in a row** | challenge |
| `uth_house_seat` | uth_four_x | In the Ultimate Texas Hold'em dealer seat, finish a round with a net profit against at least 2 seated players | goal |
| `uth_royal` | uth_four_x | Make a royal flush at Ultimate Texas Hold'em that pays the Blind or the Trips (separate from poker's `royal_flush`, which needs a PvP pot) | challenge |

---

## 20. Baccarat — Punto Banco [baccarat]

⚠ **ADDED 2026-09 — new game.** Pure "no decisions" card game: players bet, the
table deals by fixed rules. Blocks `baccarat_table` (standard) and `baccarat_table_high_roller`.

### 20.1 Rules (defaults)

| Rule | Value | Config key |
|------|-------|-----------|
| Decks | 8 × 52 = 416 cards, one shoe per table, shared by every bettor | `baccarat.decks` (1–8) |
| Shuffle | Reshuffle **before** a coup when ≥ 80 % of the shoe has been dealt | `baccarat.penetration` |
| Burn | After each shuffle the first card is shown and burned, then as many more cards face down as its value (A = 1, 2–9 face, 10/J/Q/K = 10) → 2–11 cards burned | `baccarat.burnCards` |
| Card values | A = 1, 2–9 = face value, 10/J/Q/K = 0. Hand total = sum **mod 10** (7 + 8 = 5) | — |
| Deal order | Player card 1, Banker card 1, Player card 2, Banker card 2 (all face up) | — |
| Natural | Either two-card hand totals 8 or 9 → nobody draws | — |
| Third cards | Drawing table §20.3 (fixed; no player decisions) | — |
| Player bet | Wins 1:1 if Player's total is higher; **push on Tie** | — |
| Banker bet | Wins **0.95:1** (1:1 minus 5 % commission) if Banker's total is higher; **push on Tie** | `baccarat.bankerCommission` = 0.05 |
| Tie bet | 8:1 when the totals are equal; loses otherwise | `baccarat.tiePays` = 8 |
| Player Pair / Banker Pair | 11:1 when that hand's **first two cards have the same rank** (K♠K♥ yes, K+Q no — as the blackjack split rule); independent of who wins | `baccarat.pairBets` = true, `baccarat.pairPays` = 11 |

A bettor may combine any bets in one coup (Player and Banker together are allowed; the
commission makes that a small sure loss, never a gain).

**Commission and chip rounding (the "Banker step" rule).** Every payout is `floor()`ed (§ conventions),
so a Banker win pays `floor(B × (1 − c))` profit, c = `baccarat.bankerCommission`. Flooring an
arbitrary amount would silently raise the edge (B = 5 → +4, edge 7.9 %), so **Banker bets must be a
multiple of the Banker step** `k` = the smallest integer 1…100 with `k × c` a whole number (c = 0.05 →
k = **20**, so B = 20 n wins exactly 19 n; c = 0.04 → k = 25; c = 0 → k = 1). If no such k ≤ 100
exists, k = 100 and the floor applies (config load logs a warning with the resulting edge). The
UI snaps a Banker amount **down** to the nearest multiple (message `…baccarat.snapped`), exactly like
craps odds (§10.1); the server rejects any other amount. The minimum Banker bet is
`max(k, table min)`.

### 20.2 Exact odds and house edge (8 decks, off the top)

Weights are ordered card sequences of a full coup extended to 6 cards, so every count is an
integer over `416 × 415 × 414 × 413 × 412 × 411 = 4 998 398 275 503 360`:

| Result | Count | Probability |
|--------|-------|-------------|
| Banker wins | 2 292 252 566 437 888 | 0.458 597 |
| Player wins | 2 230 518 282 592 256 | 0.446 247 |
| Tie | 475 627 426 473 216 | 0.095 156 |
| Player Pair (either hand, each) | 31 / 415 | 0.074 699 |

| Bet | EV per chip | House edge | Note |
|-----|-------------|-----------|------|
| Banker 0.95:1 | 0.95 × 0.458597 − 0.446247 = −0.010579 | **1.06 %** | 1.17 % of resolved (non-tie) bets |
| Player 1:1 | 0.446247 − 0.458597 = −0.012351 | **1.24 %** | |
| Tie 8:1 | 9 × 0.095156 − 1 = −0.143596 | **14.36 %** | `tiePays` 9 → 4.84 % |
| Player Pair / Banker Pair 11:1 | 12 × 31/415 − 1 = −43/415 | **10.36 %** | `pairPays` ≥ 13 would be player-favourable → range capped at 12 |

Penetration does not change these figures in a way players can exploit (card counting in
baccarat is worthless), so the shoe is dealt deep. Other deck counts change the edges slightly;
tests recompute them with the same method (§20.8).

### 20.3 Third-card (drawing) rules

1. If either hand is a **natural** (two-card 8 or 9): both stand; compare.
2. **Player**: total 0–5 → draws one card; 6–7 → stands.
3. **Banker**, if Player **stood** (Player had 6–7): 0–5 draws, 6–7 stands.
4. **Banker**, if Player **drew**: by Banker's two-card total and the **value** (0–9) of Player's
   third card (D = draw, S = stand):

| Banker total \ Player's 3rd card | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|------|---|---|---|---|---|---|---|---|---|---|
| 0, 1, 2 | D | D | D | D | D | D | D | D | D | D |
| 3 | D | D | D | D | D | D | D | D | **S** | D |
| 4 | S | S | D | D | D | D | D | D | S | S |
| 5 | S | S | S | S | D | D | D | D | S | S |
| 6 | S | S | S | S | S | S | D | D | S | S |
| 7 | S | S | S | S | S | S | S | S | S | S |

Each hand has at most 3 cards. Higher final total wins; equal totals are a Tie.

Test vectors (cards in deal order P1, B1, P2, B2, then P3, B3):
1. 8♠ 9♦ K♥ 7♣ → Player 8 natural, Banker 6; no draws; **Player wins 8–6**.
2. 2♣ K♦ 3♥ 3♠, P3 = 8♦ → Player 5 draws → 3; Banker 3 vs P3 = 8 **stands** → **Tie 3–3**.
3. A♠ 5♥ 4♦ K♣, P3 = 4♥, B3 = 3♣ → Player 5 → 9; Banker 5 vs P3 = 4 draws → 8; **Player wins 9–8**.
4. 7♦ 2♠ Q♥ 3♥, B3 = 4♠ → Player 7 stands; Banker 5 draws (Player stood) → 9; **Banker wins 9–7**;
   a Banker bet of 40 wins +38.
5. Q♠ 6♦ Q♦ A♣ → Player Pair (Q, Q), Player 0 draws; Banker 7 — Pair pays whatever follows.

### 20.4 Limits

- Standard table: every bet ≥ `baccarat.minBet` (1); Banker ≥ `max(k, minBet)` and a multiple of k;
  Tie and **each** Pair ≤ `floor(max × baccarat.sideMaxFraction)` (0.25); **total per coup ≤ max**,
  max = min(table max, tier max) (§4.2).
- High-Roller table: requires **Gold VIP**; total per coup ≥ `baccarat.highRollerMinTotal` (100),
  ≤ 2 × tier max (`baccarat.highRollerMaxMultiplier`); the same Banker step and side-bet fraction.
- Chips only (no pawn stakes, §4.3). Wagered (VIP, contracts) = the sum of the coup's bets.
- Streak: updated once per coup from the bettor's **net** over all bets; never alters the cards.

### 20.5 Table state machine (one shoe, up to 7 seats)

```
BETTING      : players take a seat by using the table (or its dealer NPC). Each bet is debited when it is
               placed (STAKED, §4.1); "Clear" refunds all own bets, "Rebet" repeats the last coup's
               bets (snapped to the current limits). Ends baccarat.betTimerTicks (400 = 20 s) after
               the FIRST bet at the table, or earlier when every bettor with a bet pressed "Ready".
               Single bettor: "Deal" starts the coup at once.
NO_MORE_BETS : 20 t. Bets locked.
SHUFFLE      : only if the penetration is reached (or the shoe is new): shuffle, burn (§20.1),
               40 t animation, bead plate cleared.
DEAL         : the server draws the complete coup (4–6 cards, rules are mechanical) and persists the
               cards and all stakes BEFORE any animation → the coup is "drawn" (§4.1).
REVEAL       : baccarat.revealTicks (80 t): P1, B1, P2, B2 10 t apart, then Player's third card,
               then Banker's third card (each announced: "Player draws a third card").
RESULT       : 60 t. Settle every bettor, credit, update bead plate/streak/VIP/contracts → BETTING.
```
No dealer NPC is needed: the table deals itself.

**Shared table**: up to `baccarat.seats` (7) seated players bet on the **same coup**
from the same shoe — one Player hand and one Banker hand per round for everyone; each seat settles
its own bets. Readiness works like roulette (§9): the window closes on the timer or when all seats
with bets are Ready. A player who sits down after BETTING has closed is **seated but waits** for the
next round (`gui.burmaldaholic.baccarat.waiting_next`). Everyone within `multiplayer.spectatorRadius`
(seated or not) sees the public state (§18.1: Java renders the cards and bead plate over the
table; Bedrock gets the action-bar summary `gui.burmaldaholic.baccarat.actionbar`).

**Leave, disconnect, table break, restart** (per §4.1, "drawn rounds are played out"):
- *Leave button or walking away* (> `multiplayer.tableLeaveDistance`) during BETTING: own bets are
  cleared and refunded (= Clear; `msg.burmaldaholic.baccarat.left_refunded`).
- *Disconnect* during BETTING: the bets stay and play in the coming coup (no refunds, like
  roulette); the result is credited offline and reported on join
  (`msg.burmaldaholic.core.auto_completed`).
- From NO_MORE_BETS on, leaving or disconnecting changes nothing: the coup is dealt and credited.
- *Table break* (block broken, dealer NPC removed on Bedrock, casino mode off, chunk unload,
  server stop, crash): BETTING / NO_MORE_BETS → every bet is refunded
  (`msg.burmaldaholic.baccarat.bets_refunded`); DEAL / REVEAL / RESULT → settled at the stored coup
  (`msg.burmaldaholic.core.round_played_out`). Java may settle at the stop instead (§4.1).
- The shoe (remaining order, cards dealt, bead plate, tie run) is saved with the table and survives
  restarts; breaking the table discards it.

### 20.6 House tables, owned casinos, dealer NPC

- House tables are bank-funded. At an owned table the owner sets min (per coup) and max (per coup).
- **Reservation** (§18.2): for each bettor and each of the **12 outcome classes** {Player, Banker,
  Tie} × {Player Pair yes/no} × {Banker Pair yes/no}, the house's net loss is
  `main + pairs`, where main = `P − B − T` (Player wins), `floor(B × (1 − c)) − P − T` (Banker wins),
  `tiePays × T` (Tie; P and B push), and each pair adds `pairPays × stake` if it hits, else
  `−stake`. The table reserves `max(0, max over the 12 classes of Σ over bettors)` and re-checks it
  on every bet placement (`gui.burmaldaholic.error.exposure` when it does not fit). Smallest
  worst-case at the minimum bet (insolvency test): 1 (Player 1).
- **Baccarat Dealer** NPC `baccarat_dealer` (optional, cosmetic, like the blackjack dealer):
  stationary, invulnerable, persistent, looks at players. Java: using it opens the nearest baccarat
  table (normal or High Roller) within 3 blocks, else `msg.burmaldaholic.baccarat.no_table`. Spawn egg
  `baccarat_dealer_spawn_egg`.

### 20.7 Chaos and advancement hooks

- **Tie run**: when a table deals `baccarat.tieStreakChaos` (3; 0 = off) Ties in a row, every bettor
  who **won a Tie bet** on the coup that completes the run gets `chip_shower` (as §13.1 trigger 2:
  bypasses the ambient chance, respects §13.4 safety and the per-player cooldown; deferred while
  the screen is open). Every player within `multiplayer.spectatorRadius` sees
  `msg.burmaldaholic.baccarat.tie_run` (plural). The run counter continues (4th tie → again).
- **Natural 9** on the winning side: chat flourish `msg.burmaldaholic.baccarat.natural_nine` and
  advancement `baccarat_natural` for bettors whose Player/Banker bet won with it.
- `tie_streak` advancement: the same player wins a Tie bet on two consecutive coups of one table.
- The §13.1 big-win rule can never fire here (max pay 11:1). Golden Hour doubles the coup's net
  win as for every house game. `random_teleport` is blocked while a coup runs (§13.4).

### 20.8 Test method

Exact enumeration by **value classes** (10 classes: value 0 with `16 × decks` cards, A…9 with
`4 × decks` each): loop P1, B1, P2, B2 (weight = product of remaining class counts, decrementing),
apply §20.3 for the third cards, and extend each k-card sequence weight by
`(N − k)(N − k − 1)…(N − 5)` so all leaves share the denominator `N!/(N − 6)!`. About 10⁶ leaves,
milliseconds. Assert for 8 decks the three counts of §20.2 exactly and their sum; assert the pair
probability `(4 × decks − 1)/(N − 1)` = 31/415 (pairs are by **rank**, 13 ranks × 32 cards). Also:
the 5 vectors of §20.3, the Banker step for c ∈ {0.05, 0.04, 0.0, 0.03}, and a 10⁶-coup shoe
simulation (with burn and penetration) within 0.3 % of the exact probabilities.

### 20.9 Chemin de fer — player-banked variant (PvP)

⚠ **ADDED 2026-09.** Block `baccarat_table_player_banked` (shapeless: `baccarat_table` + `chip_100`);
works only while `baccarat.chemmy.enabled`. Up to 7 seats. Players bank for each other; the house
only takes a commission (rake).

- **Roles**: the **Banker** (one seated player holding the bank) owns the Banker hand; every other
  seated player is a **punter** and bets on the Player hand **against the bank**. There are no Tie,
  pair or Banker-side bets at a chemin de fer coup.
- **Fixed tableau**: both hands draw by §20.3 exactly as in Punto Banco (deliberate simplification of
  classic chemin de fer, where the punter chooses on 5 and the banker in a few spots): no extra
  timers, and the exact odds of §20.2 apply. Same 8-deck shoe, burn and penetration.
- **Bank**: taking the bank escrows `B` chips from the banker's balance
  (`baccarat.chemmy.minBank` 20 ≤ B ≤ balance), held on the table like a poker stack.
  **Coverage** `C = min(B, banker's max)` (max = min(table max, the banker's tier max), §4.2) is what
  punters may bet this coup; the rest of the bank is not at risk.
- **Punter bets**: each ≥ table min and ≤ the punter's own max; accepted in placement order while
  `Σ ≤ C` (the bet that crosses C is snapped down to the open coverage). **Banco**: a punter whose
  balance and max both reach C presses "Banco" and matches the whole coverage alone — every other
  punter bet is refunded and betting closes at once. First press wins; one Banco per coup.
- **Settlement**: Banker hand wins → the banker wins `W = Σ punter stakes`,
  `rake = floor(W × baccarat.chemmy.rakePercent)` (0.05) goes to the owner's bankroll at an owned
  casino (else it leaves the economy, §3.5), `B += W − rake`. Player hand wins → each punter is paid
  1:1 from the bank, `B −= Σ stakes`. Tie → every stake pushes.
- **Edges** (per chip covered / bet): banker −1.06 % (0.95 × 0.458597 − 0.446247), punters −1.24 %,
  house rake +2.29 % (0.05 × 0.458597). Nobody but the rake gains on average; the house never pays
  at a chemin de fer coup, so no reservation is made.
- **Rotation**: a banker who **wins** is offered to keep the whole bank or pass it. A banker who
  **loses**, passes, drops below `minBank` or leaves gets the remaining bank back to the balance, and
  the bank is offered to the **next seat clockwise**; each candidate may take it (amount of their
  choice) or pass. If every seat passes, the round is a normal **house coup** (§20.5 bets against
  the house on the same shoe) when `baccarat.chemmy.houseCoupWhenNoBanker` (true), else the table
  waits.
- **Who may play**: nobody who owes the Loan Shark (active loan or default) may bank or punt at a
  chemin de fer coup — it is a player-to-player transfer (§5.8; `…baccarat.error.pvp_owing`); they
  may still play house coups. The owner of an owned table never plays at it (§18.2).

```
BANK_OFFER   : candidate = the winning banker, else the next seat clockwise (first round: seat 1).
               Buttons: Take the bank (amount) · Pass — or Keep the bank · Pass the bank.
               baccarat.chemmy.bankOfferTicks (200 = 10 s); timeout = Pass (nothing escrowed / the
               bank goes back to the balance). All seats pass → HOUSE coup (§20.5) or wait.
BETTING      : punters bet up to C or call Banco; Ready / betTimerTicks from the first bet, as §20.5.
               No punter bet within baccarat.chemmy.idleTicks (600 = 30 s) → the bank is passed.
NO_MORE_BETS → SHUFFLE (if due) → DEAL (coup drawn and persisted with stakes and bank, §4.1)
             → REVEAL → RESULT (settle, rake) → BANK_OFFER.
```

**Leave, disconnect, table break, restart**:
- Banker leaves / walks away / disconnects in BANK_OFFER or BETTING: all punter bets are refunded,
  the bank is returned, the next seat is offered the bank.
- Banker leaves after DEAL: the coup **plays out** against the escrowed bank and settles; the bank
  then returns to the banker's balance (offline credit, notice `msg.burmaldaholic.baccarat.bank_returned`
  on join) and rotation continues.
- Punters: as §20.5 (bets placed stay in play on disconnect; Leave in BETTING refunds).
- Table break / restart: undrawn → punter stakes refunded and bank returned; drawn → settled, then
  the bank returned. The escrow is saved with the table; an orphaned bank found on load is always
  returned to its owner.
- Streak / VIP / contracts: PvP wagers count (§14); the banker's wagered = the stakes matched that
  coup. No cashback and no Golden Hour bonus on chemin de fer coups (PvP, §12, §13.3); house coups
  at this table get both as usual. The natural-9 flourish applies; the tie-run hook needs Tie bets,
  so only house coups can trigger it.
- Advancements: `banco` (call Banco and win that coup), `bank_holder` (keep one bank through 5
  winning coups in a row).

---

## 21. Ultimate Texas Hold'em [uth]

⚠ **ADDED 2026-09 — new game.** House-banked hold'em: each seat plays only
against the dealer. Blocks `uth_table` (standard) and `uth_table_high_roller`. Reuses the poker
hand evaluator (§7.2) and hand names.

### 21.1 Rules (defaults)

| Rule | Value | Config key |
|------|-------|-----------|
| Deck | 1 × 52, shuffled every round (Fisher–Yates), no burn cards | — |
| Seats | up to 6; every seat plays against the dealer's hand, never against other seats | `uth.seats` (1–6) |
| Mandatory bets | **Ante** and **Blind**, always equal (the Blind is placed automatically) | — |
| Optional bet | **Trips** (0 = none); needs an Ante | `uth.tripsEnabled` |
| Preflop decision | **Check**, **Bet ×4** or **Bet ×3** the Ante (the Play bet) | `uth.allow3x` = true |
| Flop decision (3 board cards shown) | If no Play bet yet: **Check** or **Bet ×2** | — |
| River decision (turn and river shown together) | If no Play bet yet: **Bet ×1** or **Fold** | — |
| One Play bet | A seat makes at most one Play bet per round; after it the seat only watches | — |
| Hands | Best 5 of 7 (2 hole + 5 board), §7.2 ranking, `evaluate(cards[7])` | — |
| Dealer qualifies | Dealer's best hand is **one pair or better** (board pairs count) | — |
| Blind paytable (only when the seat **wins**) | Royal flush 500:1 · Straight flush 50:1 · Four of a kind 10:1 · Full house 3:1 · Flush 3:2 · Straight 1:1 · anything lower: push | `uth.blindPays` |
| Trips paytable (on the seat's own hand, win or lose, **even after a fold**) | Royal flush 50:1 · Straight flush 40:1 · Four of a kind 30:1 · Full house 8:1 · Flush 6:1 · Straight 5:1 · Three of a kind 3:1 · lower: loses | `uth.tripsPays` |

**Settlement** (A = Ante = Blind, P = Play bet, T = Trips; "wins" compares the full `evaluate`
values, equal values = tie):

| Seat result | Play | Ante | Blind |
|-------------|------|------|-------|
| Wins, dealer qualifies | +P (1:1) | +A (1:1) | Blind paytable, else push |
| Wins, dealer does **not** qualify | +P | **push** | Blind paytable, else push |
| Loses, dealer qualifies | −P | −A | −A |
| Loses, dealer does **not** qualify | −P | **push** | −A |
| Tie | push | push | push |
| Folded (river) | — | −A | −A |

Trips: `+T × tripsPays[category]` for three of a kind or better, else `−T`, always. Royal flush =
straight flush whose top card is an Ace. **Rounding**: the only fractional pay is the Blind's 3:2
flush → `floor(1.5 × A)` (odd Antes lose half a chip, as blackjack's 3:2, §6.1).

⚠ **Design decision (Trips paytable)**: the default is the standard casino table
50-40-30-**8-6-5**-3, whose exact edge is **1.90 %** (§21.3). The variant 50-40-30-**9-7-4**-3 has an
edge of only **0.90 %** (not 1.90 %); it remains available through `uth.tripsPays`.

Test vectors (A = 10 unless stated; cards: player hole · dealer hole · board):
1. A♠K♠ · 7♦2♣ · Q♠J♠T♠3♥4♦, Bet ×4 preflop, T = 5 → royal flush; dealer Q-high does not qualify:
   Play +40, Ante push, Blind +5 000, Trips +250 → **net +5 290**.
2. 9♥9♣ · K♦K♣ · 9♦5♠2♥J♣3♦, Bet ×4, T = 10 → trips beat kings (dealer qualifies): Play +40,
   Ante +10, Blind push, Trips +30 → **+80**.
3. Q♣7♦ · any · 2♠5♥9♦J♠3♣, check, check, fold, T = 10 → Ante −10, Blind −10, Trips −10 → **−30**.
4. 2♣3♦ · 4♠5♦ · A♠A♥K♦K♣Q♥, Bet ×1 at the river, T = 5 → both play the board: Play/Ante/Blind push,
   Trips (two pair) −5 → **−5**.
5. 7♣2♦ · A♣8♦ · K♠Q♥9♣5♦3♠, Bet ×1 → dealer wins with A-high, does not qualify: Play −10, Ante push,
   Blind −10 → **−20**.
6. A = 5, the seat wins with a flush → Blind +7 (floor 7.5).

### 21.2 Limits and balance

- **Worst-case total** `W = 6 × Ante + Trips` (Ante + Blind + a ×4 Play bet + Trips). The VIP /
  table maximum applies to W, not to the Ante: `W ≤ max`, max = min(table max, tier max) (§4.2).
  Bronze (100): Ante ≤ 16 without Trips. UI shows the resulting Ante range.
- Ante ≥ `uth.minAnte` (1); Trips 0 or ≥ `uth.minAnte`.
- At confirmation the balance must cover `2 × Ante + Trips` (debited now) **and** leave ≥ 1 × Ante
  (`gui.burmaldaholic.uth.error.keep_for_river`), so the ×1 river bet is affordable when the round
  starts. Play buttons whose amount exceeds the current balance are disabled with a tooltip; if no
  Play bet is affordable at the river, only Fold is offered.
- High-Roller table: requires **Gold VIP**, Ante ≥ `uth.highRollerMinAnte` (50),
  W ≤ 2 × tier max (`uth.highRollerMaxMultiplier`).
- Chips only (no pawn stakes). Wagered (VIP, contracts, streak) = Ante + Blind + Play + Trips
  actually placed; one settlement (net of all four bets) per seat per round.
- Streak: cosmetic only; the shuffle and the dealer are never altered.

### 21.3 House edge and method

| Bet | Figure | Method |
|-----|--------|--------|
| Ante + Blind + Play, **optimal** strategy | **2.185 % of the Ante** (≈ 2.19 %); average total wagered 4.15 Antes → **element of risk 0.527 %** (≈ 0.53 %) | Full combinatorial analysis with the §7.2 evaluator: every hole-card pair (1 326, 169 classes) × flop × turn/river × the dealer's 990 remaining hole pairs, choosing the max-EV action backwards (river → flop → preflop). Offline tool, not part of CI. Optimal play never uses ×3. |
| Same, **reference strategy R** (below) | **2.27 % ± 0.06 %** of the Ante, element of risk 0.55 %, average total wagered 4.15 Antes; fold 19.1 %, ×4 37.7 %, ×2 21.4 %, ×1 21.7 % | Monte-Carlo, 6 × 10⁷ rounds (design-time measurement, SE 0.064 %). Re-derived 2026-09 with the Bedrock board sampler (`uth/logic/sim.ts`, exact over the 1 081 × 990 hole/dealer pairs of each board): 5.4 × 10⁵ boards → 2.25 % ± 0.08 %, same bet frequencies — the figure stands implement R identically. |
| Trips 50-40-30-8-6-5-3 | EV = −2 547 324 / 133 784 560 = **−1.9040 %** | Exact over all C(52,7) = 133 784 560 hands: royal 4 324, straight flush 37 260, quads 224 848, full house 3 473 184, flush 4 047 644, straight 6 180 020, trips 6 461 620 (hit rate 15.27 %). |
| Trips 50-40-30-9-7-4-3 (variant) | −1 206 516 / 133 784 560 = −0.9018 % | Same counts. |

**Reference strategy R** (tests only; never shown as advice):
- Preflop Bet ×4 with: any pair 3-3 or better; any Ace; K-x suited, K-5+ offsuit; Q-6+ suited,
  Q-8+ offsuit; J-8+ suited, J-T+ offsuit. Otherwise check.
- Flop Bet ×2 with: a pair that uses at least one hole card (except pocket 2s); any made hand of
  two pair or better on the 5 known cards (incl. straights, flushes, board trips); four to a flush
  including a hole card of that suit of rank 10 or higher. Otherwise check.
- River: enumerate the dealer's 990 possible hole pairs from the 45 unseen cards; Bet ×1 iff the
  mean result of betting (win: `1 + (dealer qualifies ? 1 : 0) + blindPay`; loss:
  `−(2 + (dealer qualifies ? 1 : 0))`; tie: 0, in Antes) is greater than −2 (the fold). Otherwise fold.

### 21.4 Round state machine (one table, up to 6 seats)

```
BETTING  : seated players set the Ante (the Blind follows) and optional Trips and press "Deal"
           (the bets are debited = STAKED). "Clear" takes them back until DEAL. Ends when every
           seated player with a bet pressed Deal, or uth.betTimerTicks (300 = 15 s) after the FIRST
           confirmed bet. Seats without a bet sit this round out. Single player: DEAL at once.
DEAL     : shuffle; one card to each active seat (seat order 1→6), one to the dealer, again; then
           the 5 board cards face down. The deck and stakes are persisted → the round is "drawn".
PREFLOP  : every active seat decides at the same time (private buttons, public result tags):
           Check · Bet ×3 · Bet ×4.
FLOP     : reveal 3 board cards (20 t). Seats without a Play bet: Check · Bet ×2.
RIVER    : reveal turn and river (2 × 10 t). Seats without a Play bet: Bet ×1 · Fold.
SHOWDOWN : reveal the dealer's cards (20 t), announce "Dealer qualifies" or not, settle every seat
           (Play, Ante, Blind, Trips), credit. Show 80 t → BETTING.
```
- **Shared table**: all seats share the same board and the same dealer hand; each seat has its own
  hole cards and plays only against the dealer. A player who sits down after DEAL is **seated but
  waits** for the next round (`gui.burmaldaholic.uth.waiting_next`); spectators within
  `multiplayer.spectatorRadius` see the public state (board, seat tags and bets; hole cards only at
  SHOWDOWN) as in §18.1.
- Each decision street has one shared timer `uth.decisionTimerTicks` (400 = 20 s). A street ends as
  soon as every seat that still has a decision has decided; a street with no pending decision is
  only revealed (no wait). Pressing a Play button is final.
- **Timeout / safe default action**: PREFLOP and FLOP → **Check** (no chips put at risk). RIVER →
  **Fold**, except when `uth.autoPlayMadeHands` (true) and the seat's best hand is a **straight or
  better** and the balance covers 1 × Ante: then **Bet ×1** (a timeout must never throw away a Blind
  bonus). Messages `msg.burmaldaholic.uth.auto_check` / `auto_fold` / `auto_play`.

### 21.5 Leave, disconnect, table break, restart

- *Leave / walk away / disconnect* after DEAL: the seat's pending decisions are applied **at once**
  with the default rule above; the round plays out and is credited
  (`msg.burmaldaholic.core.auto_completed`; offline credits are reported on join). During BETTING:
  Leave / walking away takes the confirmed bets back (undrawn); a disconnect leaves them in play and
  the round is played with default actions (no refunds, §4.1).
- *Table break / server stop / casino mode off / chunk unload* (per §4.1): BETTING → every confirmed
  bet refunded (`msg.burmaldaholic.uth.bets_refunded`). DEAL onward → the round is **played out** on
  the persisted deck with every pending decision set to its default action, then settled
  (`msg.burmaldaholic.core.round_played_out`). Decisions already taken stay.

### 21.6 House tables, owned casinos, dealer NPC

- House tables are bank-funded. At an owned table the owner's min bet is the minimum **Ante** and
  the max bet is the maximum **W** (§21.2).
- **Reservation per seat** (§18.2), taken at confirmation assuming the largest Play bet:
  `maxLoss = Ante × (4 + 1 + blindPays.royal) + Trips × tripsPays.royal` = **505 × Ante + 50 × Trips**
  at defaults (Play ×4 + Ante 1:1 + Blind 500:1 + Trips 50:1 on a royal flush). Released at
  settlement. Example: Ante 10 + Trips 10 → 5 550 reserved. The insolvency test uses 505 (Ante 1).
  UTH is the most bankroll-hungry table; the charter screen shows its reservation per seat.
- **Hold'em Dealer** NPC `uth_dealer` (optional, cosmetic): same behaviour as the Baccarat Dealer
  (§20.6); radius 3 blocks
  (`msg.burmaldaholic.uth.no_table`). Spawn egg `uth_dealer_spawn_egg`.

### 21.7 Chaos and advancement hooks

- **Royal flush paying the Blind** → `diamond_rain` for that player (as the jackpot trigger,
  §13.1.4: bypasses the ambient chance, respects safety) and the server-wide
  `msg.burmaldaholic.uth.royal_broadcast`; this replaces the big-win `lucky_buff` roll for that
  settlement. The §13.1 big-win rule applies normally otherwise (e.g. a large Trips hit).
- Advancements (§19): `uth_four_x` (the seat bet ×4 preflop and its Play bet won), `uth_royal`
  (the seat's hand is a royal flush and the Blind **or** the Trips paid on it). Poker's
  `royal_flush` stays PvP-only.
- Golden Hour doubles the seat's net win as for every house game; `random_teleport` is blocked
  while the seat is in a round (§13.4).

### 21.8 Test method

- **Settlement**: the 6 vectors of §21.1, plus every row of the settlement table.
- **Trips**: exact — either enumerate all 133 784 560 seven-card hands with `evaluate`, or assert
  the category counts of §21.3 (the poker evaluator tests already cover them) and compute the EV
  with integer arithmetic; assert −2 547 324 exactly for the default paytable.
- **Base game**: 2.5 × 10⁷ rounds of strategy R, Ante 2 (so the 3:2 flush is exact); assert
  1.95 % ≤ HE ≤ 2.60 % of the Ante (sd per round ≈ 4.95 Antes, SE ≈ 0.099 % → ± 3.3 σ band) and
  the ×4 frequency 37.7 % ± 0.3 %.
- **Timeouts**: preflop/flop timeout checks, river timeout folds, a straight at the river with
  `autoPlayMadeHands` bets ×1; restart after DEAL settles with those defaults.

### 21.9 Player-banked Ultimate Texas Hold'em (PvP)

⚠ **ADDED 2026-09.** Block `uth_table_player_banked` (shapeless: `uth_table` + `chip_100`); works only
while `uth.pvp.enabled`. 6 player seats plus the **dealer seat**, which a player may take.

- **Taking the dealer seat**: a seated player with VIP ≥ `uth.pvp.minBankerVip` (2 = Gold), who
  does not owe the Loan Shark (§5.8, `…uth.error.pvp_owing`) and is not the table owner, presses
  "Take the dealer seat" while the seat is free, **before the first bet of a round is confirmed**
  (later requests wait for the next round). They escrow a bank `B` (`uth.pvp.minBank` 1 000 ≤ B ≤
  balance) and give up their player seat. The banker makes **no decisions**: the dealer hand plays
  and qualifies exactly as in §21.1; the bank replaces the house as the payer and the payee.
- **Coverage check** (the owned-casino rule, §18.2, against the bank): each seat's confirmation
  reserves its worst case `505 × Ante + 50 × Trips` (§21.6 formula); it is accepted only if
  `reserved + seatWorstCase ≤ B`, else `gui.burmaldaholic.uth.error.bank_cover` names the largest
  Ante the bank still covers. The banker's own tier max does not apply (the bank is the limit); the
  seats' limits (§21.2) do.
- **Settlement**: seats settle as §21.1 (Trips included) against the bank. `bankerNet = −Σ seatNet`.
  If `bankerNet > 0`: `rake = floor(bankerNet × uth.pvp.rakePercent)` (0.01) → owner's bankroll at an
  owned casino, else removed; `B += bankerNet − rake`. Otherwise `B += bankerNet` (the bank pays).
- **Figures** (heads-up, opponent playing strategy R): the dealer seat gains +2.27 % of each Ante
  before rake; the rake costs `rakePercent × 1.81` Antes per round (1.81 = mean seat loss per round,
  simulated) → at 1 % the banker keeps ≈ +0.46 % of the Ante, at 2 % ≈ −1.35 %. Several seats net
  against each other, lowering the rake. The seats keep their −2.2 % to −2.3 %. No Golden Hour bonus
  and no cashback on player-banked rounds; house rounds at this table get both.
- **Rotation**: after `uth.pvp.bankerRounds` (10; 0 = unlimited) rounds, the dealer seat is offered to
  the next seat clockwise (the current banker keeps it if nobody accepts). The banker may press
  "Leave the dealer seat" any time — it takes effect **after the current round**. A bank below
  `minBank`, or unable to cover one seat at the minimum Ante, ends the banking after the round.
  The rest of the bank always returns to the banker's balance.
- **No banker** → the house deals (a normal §21 round, bank-funded or owner-funded) when
  `uth.pvp.houseRoundsWhenNoBanker` (true); else the table waits.

State machine: §21.4 unchanged, plus: at the start of BETTING the table fixes who banks this round
(banker or house). If the banker leaves before DEAL, the confirmed seat bets stay and the round
becomes a house round (the bank's reservations are released, the house re-checks its own).

**Leave, disconnect, table break, restart**:
- Banker disconnects / walks away after DEAL: the round **plays out** against the escrowed bank
  (seats keep deciding; the dealer has no decisions) and settles; then the bank returns to the
  banker's balance (offline credit, `msg.burmaldaholic.uth.bank_returned` on join); the next round
  is dealt by the house or a new banker.
- Seats: §21.5.
- Table break / restart: undrawn → seat bets refunded and bank returned; drawn → played out against
  the bank with default actions, settled, then the bank returned. The escrow is saved with the
  table; an orphaned bank found on load is always returned.
- Streak / VIP: seats as usual; the banker's settlement counts as one PvP wager whose wagered amount
  is the seats' total stakes that round.
- Advancement: `uth_house_seat` (in the dealer seat, finish a round with a net profit against at
  least 2 seated players).

---

## Appendix A — Slot per-line probability breakdown (test vectors)

Copper Bandit (RTP 0.89760): 1 berry p=0.182400 (×2), 2 berry 0.043776 (×3), 3 berry 0.013824,
3 apple 0.008000, 3 carrot 0.004096, 3 emerald 0.001728, 3 diamond 0.000512, 3 seven 0.000125,
3 creeper 0.003375. P(no win) = 0.74554.

Golden Reels (base 0.92713): 1 berry 0.169950, 2 berry 0.036300, 3 berry* 0.015598, 3 apple*
0.010621, 3 carrot* 0.006832, 3 emerald* 0.003348, 3 diamond* 0.001304, 3 seven* 0.000485,
3 wild 0.000027, 3 pearl 0.000125, 3 creeper 0.000512, 3 star 0.000008. (* includes wild
substitutions.) P(no win) = 0.75541.

Netherite (base 0.94535): 1 berry 0.158620, 2 berry 0.030800, 3 berry* 0.012140, 3 apple* 0.010621,
3 carrot* 0.006832, 3 emerald* 0.003348, 3 diamond* 0.001701, 3 seven* 0.000702, 3 wild 0.000027,
3 pearl 0.000125, 3 clock 0.000008, 3 tnt 0.000216, 3 star 0.000008. P(no win) = 0.77508.

## Appendix B — Wheel of Fortune segment order

Index 0…53 clockwise from the pointer at rest. Codes: B=Bust, C=Creeper, H=0.5×, M=1×, D=2×,
T=3×, E=5×, X=10×.

```
X B M B D B M B H B D B M B T B M B D B H B M B D B E
B M B D B H B M B T B M B D B H B M B T B C M H D M B
```
(27 + 27 = 54 entries; counts: B 25, C 1, H 5, M 11, D 7, T 3, E 1, X 1 — tests must assert.)
