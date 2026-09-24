# Burmaldaholic — Branding

**Tagline:** *Mine. Bet. Regret nothing.*
Alt: *The house always wins. Slightly.*
Russian display name: **«Бурмалдоголик»**.

## Logo

`curseforge_logo.png` (400×400) is 40×40 pixel art in the same frame and palette as Enchantaholic's logo:
a casino chip with a gold diamond-suit emblem, a die, gold "winnings" chevrons, a green plus and
lilac sparkles. `gen_logo.py` draws it pixel by pixel and also writes the Fabric/Mod Menu `icon.png`
(128×128). Re-run after changing it:

```bash
python3 docs/branding/gen_logo.py   # needs Pillow
```

## Color palette

| Role | Hex |
|---|---|
| Background, deep purple | `#26103C` |
| Background, darkest / border | `#140822` |
| Ink / outline | `#180A28` |
| Frame purple | `#783CBE` |
| Glint purple | `#BE5AFF` |
| Sparkle lilac | `#D696FF` |
| Chip red / light / dark | `#D83440` / `#FF6E6A` / `#8C1834` |
| Chip inserts (bone / shade) | `#F4ECF8` / `#C0B0DC` |
| Gold / gold shade | `#FFD640` / `#B07010` |
| Bonus green (+) | `#80FF40` |

The frame, sparkle, gold and green colors are shared with Enchantaholic so the two projects read as a family.

## CurseForge — Java (Fabric) project

**Summary (short description):**
A casino game mode for survival: earn chips, play blackjack, poker, baccarat, slots, roulette and craps, borrow from a loan shark and survive the chaos.

**Description:**

> **Burmaldaholic** (Russian: «Бурмалдоголик») turns survival into a casino. Mining ores, killing mobs, trading and finishing daily contracts earn you **chips**, and every village might hide a small casino. Sit down at **blackjack, Texas Hold'em, Ultimate Texas Hold'em, baccarat, slots, roulette or craps**, try your luck at **Coin Flip, Wheel of Fortune, Scratch Cards, Plinko or a Dice Duel**, and bet chips, items, XP levels, even your hearts. Run dry? The **Loan Shark** will help, and his **Debt Collectors** will find you if you miss the deadline.
>
> The mode is a **Casino Mode** button on the Create World screen, right below Difficulty. Easy, Normal, Hard and Hardcore work exactly as in vanilla.

**Features**
- 🎰 **Real casino games:** Blackjack (6 decks, 3:2, split, double, insurance), Texas Hold'em against bots and other players, Ultimate Texas Hold'em against the dealer, Punto Banco baccarat (Player, Banker, Tie and pair bets), three tiers of slot machines with progressive jackpots, European roulette with every standard bet, and full craps (pass/don't pass, come, field, odds).
- 🎲 **Side games:** Coin Flip, Wheel of Fortune, Scratch Cards, Plinko and Dice Duels, including player-vs-player.
- 🪙 **Chips economy:** chips are earned in survival and exist only in your world. No real money and nothing to buy, ever.
- 🦈 **Loan Shark & Debt Collectors:** borrow with interest. Miss the deadline and an illager squad comes to collect.
- 🌩️ **Chaos events:** jackpots can set off diamond rain, mob waves, random teleports, buffs, curses and **Golden Hour**, when payouts double for the whole server.
- 🍀 **Lucky and unlucky streaks:** shown on the HUD. They nudge the odds a little, and the house always keeps an edge.
- ☠️ **Last Chance:** on death, a coin flip may bring you back at half health. It is off in Hardcore unless you opt into the high-stakes version.
- 🏛️ **Casinos in the world:** village casinos, a Piglin Parlor next to bastions and a High Roller Lounge on End City towers.
- 💎 **VIP tiers:** from Bronze to Netherite, with cosmetics, better tables, higher limits and cashback.
- 👥 **Multiplayer:** player-hosted tables and player-owned casinos, where the owner earns the house edge.
- 🏆 **35 advancements** in their own tab.
- ⚖️ **Fair and configurable:** every game follows real rules with a small house edge (blackjack about 0.4 %, baccarat Banker 1.06 %, roulette 2.7 %). You can change odds, payouts, event frequency and debt rules in the config file or through Mod Menu.
- 🇬🇧🇷🇺 **English and Russian:** follows your game language automatically.

**Requirements:** Minecraft Java **26.2–26.3**, Fabric Loader 0.19.5+, Fabric API, Java 25. Mod Menu is optional (for the config screen). Install it on both the server and the clients.
