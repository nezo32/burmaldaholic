# Burmaldaholic — Configuration Reference

Every tunable value of the game. **Both editions use exactly these key names.** The defaults are
the numbers used throughout `GAME_DESIGN.md` (section references in the last column).

## Storage and editing

| | Java (Fabric) | Bedrock (Script API) |
|---|---|---|
| Storage | `config/burmaldaholic.json` (server side). Keys are dotted paths; the file is **nested JSON objects** following the dots (`economy.ore.diamond` → `{"economy":{"ore":{"diamond":20}}}`). Unknown keys are ignored with a log warning; missing keys use defaults. Per-world override: `<world>/data/burmaldaholic_config.json` (same format), which wins over the global file. | World dynamic property `burmaldaholic:config` holding a **flat JSON object of overrides only** (`{"economy.ore.diamond":25}`), to stay under the 32 767-char property limit. Missing keys = defaults. |
| Casino mode flag | World saved data `data/burmaldaholic/mode.dat` (source of truth for `core.casinoMode`; not a game rule; default OFF; `/casino mode on\|off\|status`) | Dynamic property `burmaldaholic:casino_mode` |
| Editing | Config screen (Mod Menu integration, client) for single-player; `/casino config get/set/reset <key> [value]` (permission level 2) on servers; reload with `/casino config reload`. | Casino Card → Admin → World settings (ops only): one ActionForm per module listing keys → ModalForm per group (toggle for bool, slider for small int ranges, text field otherwise, dropdown for enums). `/scriptevent burmaldaholic:config set <key> <value>`. |
| Validation | Out-of-range values are **clamped** to the range and a warning is logged / shown to the editor. Wrong type → default. | Same. |
| Sync | Server sends the effective config subset needed for UI (limits, payouts tables) to clients on join and on change. | Not needed (UI is server-built forms). |
| Labels | `config.burmaldaholic.<key>`; `.tooltip` only where STRINGS.md §config lists one; keys with `<…>` use the family template key (STRINGS.md §config "Family templates"). | Same keys. |

Types: `bool`, `int` (32-bit, except where `long`), `double`, `enum(...)`, `list<…>`.
Percent values are stored as **fractions** (`0.05` = 5 %) unless the key ends in `Percent`.

---

## core

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `core.casinoMode` | bool | true (on world creation) | — | Master switch (§2.1). Stored in world saved data `mode.dat` (Java, default OFF) / dynamic property (Bedrock), not in the config file. |
| `core.giveCasinoCardOnJoin` | bool | true | — | Give a Casino Card on a player's first join. |
| `core.hud.enabled` | bool | true | — | Show the HUD panel (players can hide it individually too). |
| `core.hud.position` | enum(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT) | TOP_LEFT | — | Default HUD corner (per-player override in Casino Menu). |
| `core.announceBigWins` | bool | true | — | Server-wide chat for wins ≥ `core.bigWinThreshold`. |
| `core.bigWinThreshold` | int | 5000 | 100–10 000 000 | Net win that is announced. |
| `core.roundTimeoutRefund` | bool | true | — | Refund STAKED rounds after a server restart (§4.1). |

## economy

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `economy.maxBalance` | long | 1000000000 | 1 000–9 007 199 254 740 991 | Balance cap. |
| `economy.startingBalance` | int | 50 | 0–1 000 000 | Credited on first join. |
| `economy.emeraldBuyRate` | int | 8 | 1–1000 | Chips per emerald when buying chips. |
| `economy.emeraldBuyRateGoldVip` | int | 9 | 1–1000 | Same for Gold VIP+. |
| `economy.emeraldSellRate` | int | 10 | 1–1000 | Chips per emerald when selling chips. Must be ≥ buy rate (else clamped). |
| `economy.goldBuyRate` | int | 3 | 1–1000 | Nether cashier: chips per gold ingot. |
| `economy.goldSellRate` | int | 12 | 1–1000 | Nether cashier: chips per gold ingot sold. |
| `economy.ore.coal` | int | 1 | 0–1000 | |
| `economy.ore.copper` | int | 1 | 0–1000 | |
| `economy.ore.iron` | int | 2 | 0–1000 | |
| `economy.ore.gold` | int | 4 | 0–1000 | |
| `economy.ore.redstone` | int | 1 | 0–1000 | |
| `economy.ore.lapis` | int | 2 | 0–1000 | |
| `economy.ore.emerald` | int | 15 | 0–1000 | |
| `economy.ore.diamond` | int | 20 | 0–1000 | |
| `economy.ore.netherQuartz` | int | 1 | 0–1000 | |
| `economy.ore.netherGold` | int | 1 | 0–1000 | |
| `economy.ore.ancientDebris` | int | 50 | 0–10 000 | |
| `economy.ore.placedDebrisLedgerSize` | int | 4096 | 0–65 536 | FIFO size of the placed-debris ledger. |
| `economy.mob.common` | int | 2 | 0–1000 | Category value (§3.4.2). |
| `economy.mob.creeper` | int | 3 | 0–1000 | |
| `economy.mob.phantom` | int | 3 | 0–1000 | |
| `economy.mob.pillager` | int | 3 | 0–1000 | |
| `economy.mob.piglin` | int | 3 | 0–1000 | |
| `economy.mob.enderman` | int | 4 | 0–1000 | |
| `economy.mob.blaze` | int | 4 | 0–1000 | |
| `economy.mob.hoglin` | int | 4 | 0–1000 | Also zoglin. |
| `economy.mob.guardian` | int | 4 | 0–1000 | |
| `economy.mob.witch` | int | 5 | 0–1000 | |
| `economy.mob.vindicator` | int | 5 | 0–1000 | |
| `economy.mob.witherSkeleton` | int | 5 | 0–1000 | |
| `economy.mob.creaking` | int | 5 | 0–1000 | |
| `economy.mob.ghast` | int | 6 | 0–1000 | |
| `economy.mob.breeze` | int | 8 | 0–1000 | |
| `economy.mob.shulker` | int | 8 | 0–1000 | |
| `economy.mob.piglinBrute` | int | 10 | 0–1000 | |
| `economy.mob.evoker` | int | 20 | 0–10 000 | |
| `economy.mob.ravager` | int | 25 | 0–10 000 | |
| `economy.mob.elderGuardian` | int | 100 | 0–10 000 | |
| `economy.mob.warden` | int | 250 | 0–100 000 | |
| `economy.mob.wither` | int | 500 | 0–100 000 | |
| `economy.mob.enderDragonFirst` | int | 1000 | 0–1 000 000 | First dragon kill in the world. |
| `economy.mob.enderDragonRepeat` | int | 200 | 0–1 000 000 | Later dragon kills. |
| `economy.mob.hardMultiplier` | double | 1.25 | 0.0–10.0 | Multiplier on Hard/Hardcore. |
| `economy.mob.windowTicks` | int | 6000 | 20–240 000 | Diminishing-returns window. |
| `economy.mob.fullRewardKills` | int | 20 | 0–10 000 | Kills per type per window at 100 %. |
| `economy.mob.reducedRewardKills` | int | 60 | 0–10 000 | Kills up to which 25 % is paid. |
| `economy.mob.reducedRewardFactor` | double | 0.25 | 0.0–1.0 | |
| `economy.mob.spawnerRewards` | bool | false | — | Pay for spawner-spawned mobs. |
| `economy.trade.perTradeBase` | int | 1 | 0–1000 | Chips per trade. |
| `economy.trade.perEmerald` | int | 1 | 0–1000 | Plus chips per emerald in the trade. |
| `economy.trade.perTradeCap` | int | 10 | 0–10 000 | |
| `economy.trade.dailyCap` | int | 200 | 0–1 000 000 | Per player per MCD. |

## contracts

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `contracts.enabled` | bool | true | — | |
| `contracts.slots` | int | 3 | 1–8 | Base slots (VIP adds, §12). |
| `contracts.rerollCost` | int | 10 | 0–100 000 | |
| `contracts.tierScaling` | double | 0.25 | 0.0–2.0 | Target and reward scaling per VIP tier index. |
| `contracts.rewardMultiplier` | double | 1.0 | 0.0–100.0 | Global multiplier on contract rewards. |
| `contracts.weight.<id>` | int | per §3.4.4 | 0–1000 | Pool weight per contract id; 0 disables it. |

## wager (non-chip stakes)

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `wager.pawnEnabled` | bool | true | — | Item/XP/heart stakes. |
| `wager.items.enabled` | bool | true | — | |
| `wager.xp.enabled` | bool | true | — | |
| `wager.xp.maxLevels` | int | 30 | 1–100 | |
| `wager.xp.pointsPerChip` | int | 4 | 1–1000 | V = floor(points / this). |
| `wager.hearts.enabled` | bool | true | — | |
| `wager.hearts.valuePerHeart` | int | 100 | 1–100 000 | |
| `wager.hearts.maxPerBet` | int | 3 | 1–5 | |
| `wager.hearts.maxTotal` | int | 5 | 1–9 | Max simultaneously lost hearts. |
| `wager.hearts.durationTicks` | int | 24000 | 1 200–240 000 | |
| `wager.hardcoreSoulWager` | bool | false | — | Enables Soul Wager in Hardcore. |
| `wager.soul.minValue` | int | 1000 | 1–1 000 000 | |
| `wager.soul.cooldownTicks` | int | 72000 | 0–2 400 000 | |
| `wager.appraisal.<item_id>` | int | per §4.3.1 | 0–100 000 | Item appraisal values (0 removes the item from the list). |

## vip

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `vip.threshold.silver` | long | 5000 | 1–10¹² | Lifetime wagered. Must be ascending across tiers (validated). |
| `vip.threshold.gold` | long | 25000 | | |
| `vip.threshold.platinum` | long | 100000 | | |
| `vip.threshold.diamond` | long | 500000 | | |
| `vip.threshold.netherite` | long | 2500000 | | |
| `vip.maxBet.bronze` | int | 100 | 1–10⁹ | Tier max bet. |
| `vip.maxBet.silver` | int | 250 | | |
| `vip.maxBet.gold` | int | 1000 | | |
| `vip.maxBet.platinum` | int | 2500 | | |
| `vip.maxBet.diamond` | int | 10000 | | |
| `vip.maxBet.netherite` | int | 50000 | | |
| `vip.cashback.gold` | double | 0.02 | 0.0–0.5 | Fraction of the day's **theoretical loss** (Σ stake × house edge, GAME_DESIGN §12). ⚠ Changed 2026-09: was a fraction of daily net loss (+EV on low-edge games). |
| `vip.cashback.platinum` | double | 0.03 | 0.0–0.5 | |
| `vip.cashback.diamond` | double | 0.04 | 0.0–0.5 | |
| `vip.cashback.netherite` | double | 0.05 | 0.0–0.5 | |
| `vip.contractBonus.silver` | double | 0.05 | 0.0–5.0 | |
| `vip.contractBonus.gold` | double | 0.10 | 0.0–5.0 | Applies to Gold and above. |
| `vip.announceNetherite` | bool | true | — | |

## blackjack

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `blackjack.enabled` | bool | true | — | |
| `blackjack.decks` | int | 6 | 1–8 | |
| `blackjack.penetration` | double | 0.75 | 0.25–0.90 | Reshuffle point. |
| `blackjack.dealerHitsSoft17` | bool | false | — | |
| `blackjack.blackjackPayout` | double | 1.5 | 1.0–2.0 | 1.5 = 3:2, 1.2 = 6:5. |
| `blackjack.doubleAfterSplit` | bool | true | — | |
| `blackjack.maxHands` | int | 4 | 2–4 | Hands after splits. |
| `blackjack.resplitAces` | bool | false | — | |
| `blackjack.insurance` | bool | true | — | |
| `blackjack.lateSurrender` | bool | false | — | Adds a Surrender button (lose half). |
| `blackjack.minBet` | int | 1 | 1–10⁶ | Standard table. |
| `blackjack.highRollerMinBet` | int | 100 | 1–10⁶ | |
| `blackjack.highRollerMaxMultiplier` | double | 2.0 | 1.0–10.0 | × tier max. |
| `blackjack.seats` | int | 5 | 1–7 | |
| `blackjack.betTimerTicks` | int | 300 | 100–2400 | |
| `blackjack.insuranceTimerTicks` | int | 200 | 100–1200 | |
| `blackjack.turnTimerTicks` | int | 400 | 100–2400 | |

## poker

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `poker.enabled` | bool | true | — | |
| `poker.maxSeats` | int | 6 | 2–9 | |
| `poker.stakes.micro.bb` | int | 2 | 2–10⁶ | SB = BB/2 (floor, min 1). |
| `poker.stakes.low.bb` | int | 10 | | |
| `poker.stakes.mid.bb` | int | 50 | | |
| `poker.stakes.high.bb` | int | 200 | | |
| `poker.minBuyInBb` | int | 40 | 10–1000 | |
| `poker.maxBuyInBb` | int | 100 | 10–1000 | |
| `poker.actionTimerTicks` | int | 600 | 200–2400 | |
| `poker.timeoutsToSitOut` | int | 2 | 1–10 | |
| `poker.sitOutHandsToRemove` | int | 3 | 1–100 | |
| `poker.rakePercent` | double | 0.05 | 0.0–0.10 | |
| `poker.rakeCapBb` | int | 3 | 0–100 | |
| `poker.rakeNoFlopNoDrop` | bool | true | — | |
| `poker.botsEnabled` | bool | true | — | |
| `poker.botBuyInBb` | int | 100 | 20–1000 | |
| `poker.botThinkMinTicks` | int | 20 | 0–200 | |
| `poker.botThinkMaxTicks` | int | 60 | 0–400 | |
| `poker.bot.regularSamples` | int | 200 | 50–5000 | Monte-Carlo iterations. |
| `poker.bot.sharkSamples` | int | 500 | 50–5000 | |
| `poker.botMix.micro` | list<int> | [50,40,10] | each 0–100 | Fish/Regular/Shark %. Normalized. |
| `poker.botMix.low` | list<int> | [50,40,10] | | |
| `poker.botMix.mid` | list<int> | [30,50,20] | | |
| `poker.botMix.high` | list<int> | [10,50,40] | | |
| `poker.maxDistance` | int | 8 | 3–32 | Blocks from table before sitting out. |

## slots

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `slots.enabled` | bool | true | — | |
| `slots.copper.maxLineBet` | int | 50 | 1–10⁶ | Also capped by tier max. |
| `slots.gold.maxLineBet` | int | 100 | 1–10⁶ | Also ≤ tier max / 3. |
| `slots.netherite.minLineBet` | int | 2 | 1–10⁶ | |
| `slots.netherite.maxLineBet` | int | 500 | 1–10⁶ | Also ≤ tier max / 5. |
| `slots.netherite.minVipTier` | int | 2 | 0–5 | 2 = Gold. |
| `slots.<tier>.weights` | map<symbol,int> | §8 tables | each 0–10 000 | Symbol weights per tier (`copper`, `gold`, `netherite`). |
| `slots.<tier>.pays` | map<symbol,double> | §8 tables | each 0–100 000 | 3-of-a-kind multipliers. |
| `slots.<tier>.berryPartial` | list<double> | [2, 3] | each 0–100 | Pays for 1 and 2 leading berries. |
| `slots.jackpot.contribution.gold` | double | 0.01 | 0.0–0.10 | |
| `slots.jackpot.contribution.netherite` | double | 0.015 | 0.0–0.10 | |
| `slots.jackpot.seed.gold` | int | 5000 | 0–10⁹ | |
| `slots.jackpot.seed.netherite` | int | 50000 | 0–10⁹ | |
| `slots.ownedStarPays` | double | 1000 | 0–100 000 | Fixed 3-star pay at owned machines. |
| `slots.spinTicks` | int | 50 | 10–200 | Animation length. |
| `slots.validateRtp` | bool | true | — | On load, compute RTP from weights/pays; if a tier > 0.99 (incl. contribution) log a loud warning and show it on the admin page (never auto-fix). |

## roulette

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `roulette.enabled` | bool | true | — | |
| `roulette.laPartage` | bool | false | — | |
| `roulette.minBet` | int | 1 | 1–10⁶ | Per individual bet. |
| `roulette.insideMaxFraction` | double | 0.25 | 0.01–1.0 | Inside bet ≤ tier max × this. |
| `roulette.highRollerMinTotal` | int | 100 | 1–10⁶ | |
| `roulette.highRollerMaxMultiplier` | double | 2.0 | 1.0–10.0 | |
| `roulette.betTimerTicks` | int | 500 | 100–2400 | |
| `roulette.spinTicks` | int | 100 | 40–300 | |
| `roulette.maxBettors` | int | 8 | 1–16 | |
| `roulette.historyLength` | int | 12 | 0–50 | |

## craps

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `craps.enabled` | bool | true | — | |
| `craps.minBet` | int | 1 | 1–10⁶ | |
| `craps.fieldPays2` | int | 2 | 1–10 | X:1 on 2. |
| `craps.fieldPays12` | int | 3 | 1–10 | X:1 on 12 (2 → HE 5.56 %). |
| `craps.maxOdds4_10` | int | 3 | 0–100 | × flat bet. |
| `craps.maxOdds5_9` | int | 4 | 0–100 | |
| `craps.maxOdds6_8` | int | 5 | 0–100 | |
| `craps.betWindowTicks` | int | 160 | 40–1200 | |
| `craps.rollTimerTicks` | int | 400 | 100–2400 | |
| `craps.seats` | int | 6 | 1–8 | |

## baccarat

⚠ Added 2026-09 (GAME_DESIGN §20).

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `baccarat.enabled` | bool | true | — | All baccarat tables (house and chemin de fer). |
| `baccarat.decks` | int | 8 | 1–8 | Decks in the shoe. Edges in §20.2 are for 8. |
| `baccarat.penetration` | double | 0.80 | 0.25–0.90 | Reshuffle before a coup once this share of the shoe is dealt. |
| `baccarat.burnCards` | bool | true | — | Burn procedure after each shuffle (§20.1). |
| `baccarat.bankerCommission` | double | 0.05 | 0.0–0.10 | Banker wins pay (1 − this):1, floored. Also sets the Banker step (§20.1; 0.05 → 20). |
| `baccarat.tiePays` | int | 8 | 8–9 | Tie pays X:1 (8 → HE 14.36 %, 9 → 4.84 %). |
| `baccarat.pairBets` | bool | true | — | Offer Player Pair / Banker Pair. |
| `baccarat.pairPays` | int | 11 | 1–12 | Pair pays X:1 (11 → HE 10.36 %; 13+ would favour the player, hence the cap). |
| `baccarat.minBet` | int | 1 | 1–10⁶ | Per individual bet (Banker also ≥ the Banker step). |
| `baccarat.sideMaxFraction` | double | 0.25 | 0.01–1.0 | Tie and each Pair ≤ max × this. |
| `baccarat.highRollerMinTotal` | int | 100 | 1–10⁶ | High-Roller table: minimum total per coup. |
| `baccarat.highRollerMaxMultiplier` | double | 2.0 | 1.0–10.0 | High-Roller max = tier max × this. |
| `baccarat.highRollerMinVipTier` | int | 2 | 0–5 | 2 = Gold. |
| `baccarat.seats` | int | 7 | 1–7 | Seats per table (house and chemin de fer). |
| `baccarat.betTimerTicks` | int | 400 | 100–2400 | Betting window after the first bet. |
| `baccarat.revealTicks` | int | 80 | 20–300 | Card reveal animation. |
| `baccarat.historyLength` | int | 60 | 0–120 | Bead plate size (coups of the current shoe). |
| `baccarat.tieStreakChaos` | int | 3 | 0–10 | Ties in a row that trigger `chip_shower` for Tie winners; 0 = off. |
| `baccarat.chemmy.enabled` | bool | true | — | Chemin de fer (player-banked) tables work (§20.9). |
| `baccarat.chemmy.minBank` | int | 20 | 1–10⁹ | Smallest bank a player can post. |
| `baccarat.chemmy.rakePercent` | double | 0.05 | 0.0–0.10 | House commission on the banker's winning coups. |
| `baccarat.chemmy.bankOfferTicks` | int | 200 | 100–1200 | Time to take / keep / pass the bank. |
| `baccarat.chemmy.idleTicks` | int | 600 | 100–6000 | No punter bet for this long → the bank passes. |
| `baccarat.chemmy.houseCoupWhenNoBanker` | bool | true | — | Nobody banks → play a house coup instead of waiting. |

## uth

⚠ Added 2026-09 (GAME_DESIGN §21).

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `uth.enabled` | bool | true | — | All Ultimate Texas Hold'em tables. |
| `uth.seats` | int | 6 | 1–6 | Player seats (the dealer seat is extra). |
| `uth.allow3x` | bool | true | — | Offer the ×3 preflop bet (optimal play never uses it). |
| `uth.minAnte` | int | 1 | 1–10⁶ | Standard table minimum Ante (and minimum Trips). |
| `uth.highRollerMinAnte` | int | 50 | 1–10⁶ | |
| `uth.highRollerMaxMultiplier` | double | 2.0 | 1.0–10.0 | High-Roller: W = 6 × Ante + Trips ≤ tier max × this. |
| `uth.highRollerMinVipTier` | int | 2 | 0–5 | 2 = Gold. |
| `uth.tripsEnabled` | bool | true | — | Offer the Trips side bet. |
| `uth.blindPays` | map<hand,double> | royal 500, straightFlush 50, quads 10, fullHouse 3, flush 1.5, straight 1 | each 0–1000 | Blind pays X:1 on a win (floored); hands not listed push. |
| `uth.tripsPays` | map<hand,int> | royal 50, straightFlush 40, quads 30, fullHouse 8, flush 6, straight 5, trips 3 | each 0–1000 | Trips pays X:1. Variant 9/7/4 for FH/flush/straight → HE 0.90 %. |
| `uth.validateEdge` | bool | true | — | On load compute the Trips edge exactly (§21.3); if ≤ 1 % log a loud warning and show it on the admin page (never auto-fix). |
| `uth.betTimerTicks` | int | 300 | 100–2400 | Betting window after the first confirmed bet. |
| `uth.decisionTimerTicks` | int | 400 | 200–2400 | Per street (preflop, flop, river). |
| `uth.autoPlayMadeHands` | bool | true | — | River timeout with a straight or better bets ×1 instead of folding. |
| `uth.pvp.enabled` | bool | true | — | Player-banked tables allow a player dealer (§21.9). |
| `uth.pvp.minBank` | int | 1000 | 505–10⁹ | Smallest bank for the dealer seat. |
| `uth.pvp.minBankerVip` | int | 2 | 0–5 | VIP tier needed to take the dealer seat (2 = Gold). |
| `uth.pvp.rakePercent` | double | 0.01 | 0.0–0.10 | House rake on the banker's positive net per round. |
| `uth.pvp.bankerRounds` | int | 10 | 0–1000 | Rounds before the dealer seat is offered on; 0 = unlimited. |
| `uth.pvp.houseRoundsWhenNoBanker` | bool | true | — | Nobody banks → the house deals instead of waiting. |

## extras

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `extras.coinFlip.enabled` | bool | true | — | |
| `extras.coinFlip.payout` | double | 0.96 | 0.5–1.0 | Win pays X:1. |
| `extras.wheel.enabled` | bool | true | — | |
| `extras.wheel.maxBetFraction` | double | 0.5 | 0.01–1.0 | × tier max. |
| `extras.wheel.segments` | list<string> | Appendix B | 2–100 entries | Segment codes in order. |
| `extras.wheel.multipliers` | map<code,double> | B 0, C 0, H 0.5, M 1, D 2, T 3, E 5, X 10 | 0–1000 | |
| `extras.scratch.enabled` | bool | true | — | |
| `extras.scratch.basic.price` | int | 10 | 1–10⁶ | |
| `extras.scratch.basic.prizes` | list<[int,double]> | §11.3 | | (prize, probability) pairs; Σp ≤ 1. |
| `extras.scratch.gold.price` | int | 100 | 1–10⁶ | |
| `extras.scratch.gold.prizes` | list<[int,double]> | §11.3 | | |
| `extras.scratch.creeperChance` | double | 0.01 | 0.0–0.5 | Share of losing cards that are Creeper cards. |
| `extras.plinko.enabled` | bool | true | — | |
| `extras.plinko.maxBetFraction` | double | 0.2 | 0.01–1.0 | |
| `extras.plinko.low` | list<double> | §11.4 | 13 entries | |
| `extras.plinko.medium` | list<double> | §11.4 | 13 entries | |
| `extras.plinko.high` | list<double> | §11.4 | 13 entries | |
| `extras.diceDuel.enabled` | bool | true | — | |
| `extras.diceDuel.houseWinsTieOn` | list<int> | [7] | totals 2–12 | Tie totals the house wins (others push). |
| `extras.diceDuel.pvpEnabled` | bool | true | — | |
| `extras.diceDuel.pvpRakePercent` | int | 0 | 0–20 | |
| `extras.diceDuel.challengeTimeoutTicks` | int | 600 | 100–6000 | |
| `extras.diceDuel.maxDistance` | int | 16 | 2–128 | |

## loan

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `loan.enabled` | bool | true | — | |
| `loan.products` | list<[principal,days,minTier]> | [[100,3,0],[500,3,0],[2000,5,1],[10000,7,2],[50000,7,4]] | principal 1–10⁹, days 1–100, tier 0–5 | |
| `loan.rate.easy` | double | 0.15 | 0.0–5.0 | Also Peaceful. |
| `loan.rate.normal` | double | 0.20 | 0.0–5.0 | |
| `loan.rate.hard` | double | 0.25 | 0.0–5.0 | Also Hardcore. |
| `loan.goodStandingDiscount` | double | 0.02 | 0.0–0.5 | Per on-time loan. |
| `loan.goodStandingMaxSteps` | int | 5 | 0–50 | |
| `loan.minRate` | double | 0.10 | 0.0–5.0 | |
| `loan.platinumDiscount` | double | 0.02 | 0.0–0.5 | Extra VIP discount (Platinum+). |
| `loan.lateFee.easy` | double | 0.05 | 0.0–1.0 | Per overdue MCD, of owed-at-deadline. |
| `loan.lateFee.normal` | double | 0.10 | 0.0–1.0 | |
| `loan.lateFee.hard` | double | 0.15 | 0.0–1.0 | |
| `loan.lateFeeCapMultiplier` | double | 2.0 | 1.0–10.0 | Owed ≤ due × this. |
| `loan.garnishPercent` | int | 50 | 0–100 | In default. |
| `loan.defaultCooldownDays` | int | 5 | 0–100 | |
| `loan.warningTicks` | list<int> | [24000, 2400] | each 0–240 000 | Warnings before deadline. |
| `loan.collectors.enabled` | bool | true | — | False → Asset Freeze on all difficulties. |
| `loan.firstWaveDelayTicks` | int | 600 | 0–24 000 | |
| `loan.offlineWaveDelayTicks` | int | 1200 | 0–24 000 | After join. |
| `loan.squadMax` | int | 10 | 1–20 | |
| `loan.escalationMax` | int | 3 | 0–10 | Extra Collectors from later waves. |
| `loan.approachTicks` | int | 600 | 100–6000 | |
| `loan.negotiateTicks` | int | 200 | 100–2400 | |
| `loan.hostileTicks` | int | 6000 | 600–48 000 | |
| `loan.partialPaymentMin` | double | 0.5 | 0.0–1.0 | Share of owed to send a wave away. |
| `loan.repossessBalancePercent` | int | 50 | 0–100 | |
| `loan.repossessItem` | bool | true | — | Take the most valuable appraised item on death. |
| `loan.collectorHealthMultiplier.easy` | double | 0.75 | 0.1–10 | |
| `loan.collectorHealthMultiplier.hard` | double | 1.25 | 0.1–10 | |
| `loan.peacefulSeizePercent` | int | 50 | 0–100 | Asset Freeze seizure per MCD. |

## chaos

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `chaos.enabled` | bool | true | — | |
| `chaos.ambientIntervalTicks` | int | 6000 | 600–240 000 | |
| `chaos.ambientChance` | double | 0.08 | 0.0–1.0 | |
| `chaos.playerCooldownTicks` | int | 3000 | 0–240 000 | |
| `chaos.weight.<event>` | int | §13.2 | 0–1000 | `chip_shower`, `lucky_buff`, `diamond_rain`, `xp_fountain`, `curse`, `mob_wave`, `random_teleport`, `weather_change`, `golden_hour`. 0 disables. |
| `chaos.event.<event>.enabled` | bool | true | — | Also disables the event for slot/wheel triggers. |
| `chaos.bigWin.multiple` | int | 50 | 2–10 000 | |
| `chaos.bigWin.minChips` | int | 500 | 1–10⁹ | |
| `chaos.bigWin.buffChance` | double | 0.30 | 0.0–1.0 | |
| `chaos.chipShower.min` | int | 20 | 0–100 000 | |
| `chaos.chipShower.max` | int | 100 | 0–100 000 | |
| `chaos.diamondRain.min` | int | 3 | 0–64 | Hard/Hardcore −1. |
| `chaos.diamondRain.max` | int | 6 | 0–64 | |
| `chaos.xpFountain.min` | int | 50 | 0–10 000 | |
| `chaos.xpFountain.max` | int | 150 | 0–10 000 | |
| `chaos.buff.minTicks` | int | 1200 | 20–72 000 | |
| `chaos.buff.maxTicks` | int | 3600 | 20–72 000 | |
| `chaos.curse.minTicks` | int | 600 | 20–72 000 | |
| `chaos.curse.maxTicks` | int | 1800 | 20–72 000 | |
| `chaos.mobWave.easy` | int | 3 | 0–20 | |
| `chaos.mobWave.normal` | int | 4 | 0–20 | |
| `chaos.mobWave.hard` | int | 6 | 0–20 | |
| `chaos.mobWave.minDistance` | int | 8 | 4–64 | |
| `chaos.mobWave.maxDistance` | int | 16 | 4–64 | |
| `chaos.mobWave.despawnTicks` | int | 6000 | 200–72 000 | |
| `chaos.teleport.minDistance` | int | 32 | 8–10 000 | |
| `chaos.teleport.maxDistance` | int | 256 | 8–10 000 | |
| `chaos.teleport.attempts` | int | 16 | 1–64 | |
| `chaos.weather.durationTicks` | int | 6000 | 600–72 000 | |
| `chaos.goldenHour.enabled` | bool | true | — | |
| `chaos.goldenHour.multiplier` | double | 2.0 | 1.0–10.0 | Applied to net winnings. |
| `chaos.goldenHour.durationTicks` | int | 3600 | 600–24 000 | |
| `chaos.goldenHour.cooldownTicks` | int | 24000 | 0–240 000 | |
| `chaos.goldenHour.sunsetChance` | double | 0.10 | 0.0–1.0 | |
| `chaos.goldenHour.bonusCap` | int | 5000 | 0–10⁹ | Per player per event. |
| `chaos.respawnGraceTicks` | int | 200 | 0–2400 | |
| `chaos.deferMaxTicks` | int | 600 | 0–6000 | Defer while a casino UI is open. |
| `chaos.bossSafeRadius` | int | 64 | 0–256 | |

## streak

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `streak.enabled` | bool | true | — | False: streak still tracked for HUD/advancements but no odds effect. |
| `streak.max` | int | 10 | 1–100 | |
| `streak.luckyPerStep` | double | 0.005 | 0.0–0.1 | |
| `streak.pityPerStep` | double | 0.003 | 0.0–0.1 | |
| `streak.minHouseEdge` | double | 0.01 | 0.0–0.5 | Hard floor (§14). Values < 0.005 are clamped to 0.005. |
| `streak.decayTicks` | int | 12000 | 0–240 000 | 0 = no decay. |

## lastchance

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `lastChance.enabled` | bool | true | — | |
| `lastChance.chance.easy` | double | 0.60 | 0.0–1.0 | Also Peaceful. |
| `lastChance.chance.normal` | double | 0.50 | 0.0–1.0 | |
| `lastChance.chance.hard` | double | 0.40 | 0.0–1.0 | |
| `lastChance.cooldownTicks` | int | 24000 | 0–2 400 000 | |
| `lastChance.costPercent` | int | 10 | 0–100 | Of balance, on success. |
| `lastChance.hardcoreMode` | enum(DISABLED, HIGH_STAKES) | DISABLED | — | |
| `lastChance.hardcore.chance` | double | 0.50 | 0.0–1.0 | |
| `lastChance.hardcore.cooldownTicks` | int | 120000 | 0–2 400 000 | |
| `lastChance.hardcore.minStake` | int | 100 | 0–10⁹ | Balance + carried chips needed. |
| `lastChance.hardcore.heartCost` | int | 2 | 1–10 | Permanent max-health HP removed. |
| `lastChance.hardcore.minMaxHealth` | int | 8 | 2–20 | Not eligible below this max HP. |

## worldgen

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `worldgen.enabled` | bool | true | — | New chunks only. |
| `worldgen.villageCasino.chance` | double | 0.35 | 0.0–1.0 | |
| `worldgen.piglinParlor.chance` | double | 0.30 | 0.0–1.0 | |
| `worldgen.highRoller.chance` | double | 0.20 | 0.0–1.0 | |
| `worldgen.loanSharkRespawnTicks` | int | 24000 | 0–240 000 | |

## multiplayer / ownership

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `ownership.enabled` | bool | true | — | |
| `ownership.licenseFee` | int | 1000 | 0–10⁹ | |
| `ownership.claimRadius` | int | 24 | 4–128 | |
| `ownership.maxPerPlayer` | int | 1 | 0–16 | |
| `ownership.protectTables` | bool | true | — | |
| `ownership.explosionProof` | bool | true | — | |
| `multiplayer.tableLeaveDistance` | int | 8 | 3–32 | |
| `multiplayer.spectatorRadius` | int | 8 | 0–32 | |

## admin / debug

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `debug.logRounds` | bool | false | — | Log every settled round (for testers). |
| `debug.fixedSeed` | long | 0 | any | Non-zero → deterministic RNG (tests only). |
| `debug.showOdds` | bool | false | — | Show computed RTP on machine screens (ops). |
