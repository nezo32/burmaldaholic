# Burmaldaholic — Configuration Reference

Every tunable value of the game (Java / Fabric). The defaults are
the numbers used throughout `GAME_DESIGN.md` (section references in the last column).

## Storage and editing

| | |
|---|---|
| Storage | `config/burmaldaholic.json` (server side). Keys are dotted paths; the file is **nested JSON objects** following the dots (`economy.ore.diamond` → `{"economy":{"ore":{"diamond":20}}}`). Unknown keys are ignored with a log warning; missing keys use defaults. Per-world override: `<world>/data/burmaldaholic_config.json` (same format), which wins over the global file. |
| Casino mode flag | World saved data `data/burmaldaholic/mode.dat` (source of truth for `core.casinoMode`; not a game rule; default OFF; `/casino mode on\|off\|status`) |
| Editing | Config screen (Mod Menu integration, client) for single-player; `/casino config get/set/reset <key> [value]` (permission level 2) on servers; reload with `/casino config reload`. |
| Validation | Out-of-range values are **clamped** to the range and a warning is logged / shown to the editor. Wrong type → default. |
| Sync | Server sends the effective config subset needed for UI (limits, payouts tables) to clients on join and on change. |
| Labels | `config.burmaldaholic.<key>`; `.tooltip` only where STRINGS.md §config lists one; keys with `<…>` use the family template key (STRINGS.md §config "Family templates"). |

Types: `bool`, `int` (32-bit, except where `long`), `double`, `enum(...)`, `list<…>`.
Percent values are stored as **fractions** (`0.05` = 5 %) unless the key ends in `Percent`.

---

## core

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `core.casinoMode` | bool | false (on world creation) | — | Master switch (§2.1). Stored in world saved data `mode.dat`, not in the config file. |
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
| `poker.botsEnabled` | bool | true | — | **Legacy alias** of `bots.enabled` for poker: false forces poker tables to `HUMANS_ONLY` (BOTS.md §9.3). |
| `poker.botBuyInBb` | int | 100 | 20–1000 | |
| `poker.botThinkMinTicks` | int | 20 | 0–200 | |
| `poker.botThinkMaxTicks` | int | 60 | 0–400 | |
| `poker.bot.regularSamples` | int | 300 | 50–5000 | Monte-Carlo samples of a NORMAL (Regular) bot decision (range-aware equity, BOTS.md §4.3). |
| `poker.bot.sharkSamples` | int | 700 | 50–5000 | Monte-Carlo samples of a HARD (Shark) bot decision. |
| `poker.botMix.micro` | list<int> | [45,45,10] | each 0–100 | Easy/Normal/Hard (Fish/Regular/Shark) % for MIXED difficulty. Normalized; Easy is forced to 0 above `bots.poker.easyMaxStake`. |
| `poker.botMix.low` | list<int> | [35,50,15] | each 0–100 | |
| `poker.botMix.mid` | list<int> | [10,55,35] | each 0–100 | |
| `poker.botMix.high` | list<int> | [0,45,55] | each 0–100 | |
| `poker.maxDistance` | int | 8 | 3–32 | Blocks from table before sitting out. |

## slots

Slots v2 (`SLOTS.md` §12; the v1 3×3 keys are gone, see "Removed" below). `<m>` ∈ `overworld`, `nether`, `end`.

Registration state: every row with a back-quoted key is registered in Java (`SlotsConfig` / `SlotsV2Config`,
checked by `ConfigSpecCoverageTest`). The per-machine families and the table-valued keys of the second table are registered too (`slots.<m>.*`,
defaults per `SLOTS.md`); JSON cannot hold `slots.<m>.freeSpins` as both a list and an object, so the Java config keeps
the spin awards in `slots.<m>.freeSpins.awards`.

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `slots.enabled` | bool | true | — | All slot machines. |
| `slots.validateRtp` | bool | true | — | On load, compute each machine's RTP (`SLOTS.md` §7.5) from the config; a machine above 0.99, or a buy feature above its machine, logs a loud warning and shows it on the admin page (never auto-fix). |
| `slots.jackpot.announceMinTier` | enum(MINI, MINOR, MAJOR, GRAND) | MAJOR | — | Server-wide chat from this tier up. |
| `slots.buyFeature.enabled` | bool | true | — | |
| `slots.buyFeature.tierMaxMultiple` | int | 25 | 1–1000 | Price ≤ tier max × this. |
| `slots.autoplay.enabled` | bool | true | — | |
| `slots.autoplay.counts` | list<int> | [10,25,50,100] | each 1–1000 | |
| `slots.autoplay.lossLimits` | list<int> | [10,25,50,100] | each 1–10000 | × bet; one is mandatory. |
| `slots.turboAllowed` | bool | true | — | |
| `slots.anticipation` | bool | true | — | Off: reels always stop on the base schedule. |
| `slots.bigWinTiers` | list<int> | [5,15,40,100] | each 1–10000 | Nice/Big/Mega/Epic thresholds (× bet); 4 increasing entries. |
| `slots.inWorld.enabled` | bool | true | — | In-world reels (block entity renderer). |
| `slots.inWorld.radius` | int | 24 | 0–64 | Spectator range. |
| `slots.overworld.minVipTier` | int | 0 | 0–5 | |
| `slots.nether.minVipTier` | int | 0 | 0–5 | |
| `slots.end.minVipTier` | int | 2 | 0–5 | 2 = Gold. |
| `slots.overworld.freeSpins.multiplier` | int | 2 | 1–10 | |
| `slots.nether.tumble.ladder` | list<int> | [1,2,3,5] | each 1–100 | Base game; 4 entries. |
| `slots.nether.tumble.ladderFree` | list<int> | [2,4,6,10] | each 1–100 | Free spins; 4 entries. |
| `slots.overworld.pick.board` | int | 15 | 3–30 | Chests on the board. |
| `slots.overworld.pick.weights` | map<string,int> | x1 30000, x2 22000, x3 14000, x5 9000, x10 3500, x25 800, mini 600, minor 150, major 20, grand 3, creeper 22000 | each 0–10000000 | Treasure Hunt chest contents (`SLOTS.md` §3.1). |
| `slots.nether.hold.trigger` | int | 6 | 3–15 | |
| `slots.nether.hold.respins` | int | 3 | 1–10 | |
| `slots.nether.hold.coinChance` | double | 0.04 | 0.0–0.5 | Per empty cell per respin. |
| `slots.nether.hold.coinWeights` | map<string,int> | x1 4000, x2 2500, x3 1500, x5 1000, x10 500, x25 120, mini 80, minor 20, major 3 | each 0–10000000 | ×10 of `SLOTS.md` §3.2. |
| `slots.nether.buy.price` | double | 18.4 | 1–10000 | × bet, multiples of 0.2. |
| `slots.end.buy.price` | double | 109 | 1–10000 | × bet, multiples of 0.2. |

Per-machine families and table-valued keys (defaults per `SLOTS.md`):

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| slots.<m>.enabled | bool | true | — | One machine type (disabled cabinets show "Out of order"). |
| slots.<m>.bets | list<int> | §6.1 | 1–8 entries, each 5–10⁶, multiples of 5, sorted | Bet ladder. Not a multiple of 5 → the list is rejected (default kept). |
| slots.<m>.defaultBet | int | 10 / 20 / 100 | on the ladder | Clamped to the nearest ladder value. |
| slots.<m>.maxWinMultiple | int | 500 / 2000 / 5000 | 50–100 000 | Max-win cap and owned reservation (§1.3, §8.6). |
| slots.<m>.strips | list<string> | Appendix A | exactly 5 strings of codes | Advanced. Validated: known codes, Wild only on reels 2–4, bonus only on its reels, scatter spacing ≥ 3. |
| slots.<m>.pays | map<symbol,list<double>> | §3 tables | each 0–10 000, multiples of 0.2 | 3/4/5-of-a-kind × bet per way. |
| slots.<m>.scatterPays | list<double> | [1,10,50] / [0,0,0] / [2,10,50] | each 0–10 000, integers | × bet for 3/4/5 scatters. |
| slots.<m>.freeSpins | list<int> | [8,10,15] / [12,15,20] / [9,11,14] | each 0–100 | Spins for 3/4/5 scatters. |
| slots.<m>.freeSpins.retrigger | int | 8 / 5 / 4 | 0–100 | |
| slots.<m>.freeSpins.cap | int | 50 / 60 / 40 | 1–500 | Max spins awarded per feature. |
| slots.end.wheel.outer / .middle / .core | list<string> | §3.3 wedge orders | 4–32 entries each | Tokens: an integer multiple, `MINI MINOR MAJOR GRAND`, `UP` (not in core). |
| slots.<m>.jackpot.refBet | int | 100 / 500 / 5000 | 5–10⁶ | Full jackpot at this bet or above. |
| slots.<m>.jackpot.seed | map<tier,int> | §5.1 | each 0–100 000 | Multiples of `refBet`. |
| slots.<m>.jackpot.contribution | map<tier,double> | §5.1 | each 0.0–0.05 | Fraction of every stake. |
| slots.<m>.jackpot.owned | map<tier,int> | §5.2 | each 0–100 000 | Fixed × bet at owned machines. |
| pvp.slots.hazardWeights | list<int> | [94,2,2,2] | 4 entries, each 0–1 000 | Slot Showdown v2 (S-B8 / S-J8): none / KABOOM / SWAP / TIME WARP. |
| pvp.race.target | enum(feature, jackpot, five_top) | feature | — | Jackpot Race (NICE). |

**Removed** (ignored with one warning listing them, `SLOTS.md` §11): `slots.copper.maxLineBet`,
`slots.gold.maxLineBet`, `slots.netherite.minLineBet`, `slots.netherite.maxLineBet`, `slots.netherite.minVipTier`,
`slots.<tier>.weights`, `slots.<tier>.pays`, `slots.<tier>.berryPartial`, `slots.jackpot.contribution.*`,
`slots.jackpot.seed.*`, `slots.ownedStarPays`, `slots.spinTicks` (`pvp.slots.starPoints` goes with Slot Showdown v2).

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
| `baccarat.revealTicks` | int | 160 | 20–300 | Cap of the card reveal timeline (deal, flips, squeezes; `animation/cards.md` §4.2). |
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

## pvp

⚠ Added 2026-09 (PVP.md §13, pre-merged by the architect). Percent-like values are integer basis points.
PVP.md's `pvp.bots.*` keys are superseded by the `bots` section (`bots.enabled`, `bots.pvp.*`; BOTS.md §2.3 defaults).

### Core

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

### Modes

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

### NICE

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

## bots

⚠ Added 2026-09 (BOTS.md §9, pre-merged by the architect).
BOTS.md §9.3 also changes existing poker keys (`poker.bot.regularSamples` 300, `poker.bot.sharkSamples` 700, `poker.botMix.*` [45,45,10] / [35,50,15] / [10,55,35] / [0,45,55]; `poker.botsEnabled` becomes a legacy alias); those rows above are updated by the poker bot migration task (docs/architecture/pvp-bots.md §7).

### Core

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.enabled` | bool | true | — | Master switch. Off → every table behaves as `HUMANS_ONLY`; seated bots leave at the next safe point. |
| `bots.maxActiveTables` | int | 24 | 0–256 | Tables with bots at the same time, whole world (§7.5). |
| `bots.maxActive` | int | 64 | 0–512 | Bots at the same time, whole world. |
| `bots.maxConcurrentJobs` | int | 2 | 1–16 | Heavy bot jobs (Monte-Carlo, UTH river) running at once (jobs are split over ticks above it). |
| `bots.difficultyMix` | list<int> | [30, 50, 20] | each 0–100 | Easy/Normal/Hard % for MIXED outside poker. Normalized. |
| `bots.think.minTicks` | int | 20 | 0–200 | Base think delay (§7.3). |
| `bots.think.maxTicks` | int | 60 | 0–400 | Must be ≥ min (else clamped). |
| `bots.think.tankTicks` | int | 40 | 0–200 | Extra "tank" for HARD poker bots on big decisions (+0–this). |
| `bots.think.fastFactor` | double | 0.5 | 0.0–1.0 | Delay multiplier for speed FAST. |
| `bots.personalities` | bool | true | — | Off → every bot is TAG-like at its level (no personality modifiers, plain styles). |
| `bots.keepFreeSeatDefault` | bool | true | — | Default of the per-table *Keep a seat free* toggle. |
| `bots.showcase.enabled` | bool | false | — | NICE: worldgen tables play virtual bot rounds while watched (§4.9). |

### Seating defaults per game (family)

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.table.<game>.policy` | enum(HUMANS_ONLY, MIXED, BOTS_ONLY) | matrix below | — | Default policy of a newly placed (craftable) table. |
| `bots.table.<game>.count` | int | matrix below | 0–8 | Default bot count (clamped to seats − 1 and the atmosphere cap). |
| `bots.table.<game>.difficulty` | enum(EASY, NORMAL, HARD, MIXED) | matrix below | — | Default difficulty / style. |
| `bots.table.<game>.worldgenPolicy` | enum(HUMANS_ONLY, MIXED, BOTS_ONLY) | matrix below | — | Default for tables generated in structures (BOTS_ONLY not allowed → clamped to MIXED). |
| `bots.table.<game>.worldgenCount` | int | matrix below | 0–8 | |

| game | policy | count | difficulty | worldgenPolicy | worldgenCount |
|----------|--------|-------|------------|----------------|---------------|
| poker | MIXED | 5 | MIXED | MIXED | 3 (Parlor preset) |
| chemmy | MIXED | 2 | MIXED | MIXED | 2 |
| blackjack | HUMANS_ONLY | 0 | NORMAL | MIXED | 2 |
| roulette | HUMANS_ONLY | 0 | MIXED | MIXED | 3 |
| craps | HUMANS_ONLY | 0 | MIXED | MIXED | 2 |
| baccarat | HUMANS_ONLY | 0 | MIXED | MIXED | 2 |
| uth | HUMANS_ONLY | 0 | NORMAL | MIXED | 2 |

### Games

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.atmosphere.maxPerTable.blackjack` | int | 2 | 0–4 | Atmosphere bot cap (§4.7). |
| `bots.atmosphere.maxPerTable.uth` | int | 2 | 0–5 | |
| `bots.atmosphere.maxPerTable.roulette` | int | 3 | 0–7 | |
| `bots.atmosphere.maxPerTable.craps` | int | 3 | 0–5 | |
| `bots.atmosphere.maxPerTable.baccarat` | int | 3 | 0–6 | |
| `bots.poker.easyMaxStake` | enum(MICRO, LOW, MID, HIGH) | LOW | — | Highest stake level where EASY bots may sit (§4.2). |
| `bots.chemmy.bankCapMultiple` | int | 50 | 5–1000 | A bot bank is at most this × table min (and ≤ the table max coverage). |
| `bots.pvp.fillDelayTicks` | int | 400 | 0–1800 | MIXED lobbies: bots fill after this long without a human joiner. |
| `bots.pvp.maxPerMatch` | int | 3 | 1–7 | Max bots in one PvP match. |
| `bots.tournament.maxFill` | int | 8 | 0–31 | Max bot fillers per tournament. |
| `bots.tournament.fillToBracket` | bool | true | — | Knockout: fill to the next power of two instead of byes. |

### Economy and abuse

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

### Presentation, private tables

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `bots.avatars.mode` | enum(NONE, NAMEPLATE, ENTITY) | NAMEPLATE | — | §7.2. |
| `bots.avatars.maxEntities` | int | 16 | 0–128 | Avatar entities per world. |
| `bots.chatter.enabled` | bool | true | — | Server-wide switch for quips (tables and players can mute too). |
| `bots.chatter.chance` | double | 0.35 | 0.0–1.0 | Chance an event produces a line (HARD bots: half). |
| `bots.chatter.botCooldownTicks` | int | 600 | 0–24 000 | |
| `bots.chatter.tableCooldownTicks` | int | 200 | 0–24 000 | |
| `bots.chatter.maxPerMinute` | int | 3 | 0–60 | Per table. |
| `bots.private.enabled` | bool | true | — | Private tables and invite-only lobbies. |
| `bots.private.maxInvites` | int | 16 | 1–64 | |
| `bots.private.inviteRadius` | int | 64 | 0–1024 | Invite dropdown radius; 0 = any online player. |

## admin / debug

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `debug.logRounds` | bool | false | — | Log every settled round (for testers). |
| `debug.fixedSeed` | long | 0 | any | Non-zero → deterministic RNG (tests only). |
| `debug.showOdds` | bool | false | — | Show computed RTP on machine screens (ops). |
