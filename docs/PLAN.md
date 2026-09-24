# Burmaldaholic — delivery plan

Legend: `→` = depends on. Tasks in the same wave run in parallel.

## Wave 1 — research, design, scaffolding (parallel, no deps)
| ID | Role | Task | Output |
|----|------|------|--------|
| R1 | researcher + architect | Fabric 26.2/26.3 toolchain, buildable skeleton, Java architecture | `java/`, `docs/architecture/java.md` |
| R2 | researcher + architect | Bedrock Script API for 26.2/26.3-era, buildable skeleton, Bedrock architecture | `bedrock/`, `docs/architecture/bedrock.md` |
| D1 | designer | Game design spec: economy, all games' rules/odds/payouts, chaos, debt, VIP, config defaults, UI layouts, translation-key scheme, RU glossary | `docs/design/*` |
| C1 | developer (CI) | Reusable tag→release→CurseForge workflow (`workflow_call`), PR build workflow, docs | `.github/workflows/*`, `docs/ci/*` |

## Wave 2 — core systems (→ R1/R2/D1)
- J-core (→R1,D1): economy (chips item + balance), world-mode toggle, config, plural helper, lang merge, HUD, networking.
- B-core (→R2,D1): same for Bedrock.

## Wave 3 — features (→ J-core / B-core), one agent per feature per edition
Blackjack, Poker, Slots, Roulette, Craps, Extras (coin flip, wheel, scratch cards, plinko, dice duel),
Loan Shark + Debt Collectors, Chaos events + Golden Hour + streak, Last Chance, Worldgen casinos, VIP tiers + multiplayer ownership.

## Wave 4 — testing (→ wave 3): independent testers per edition (unit tests for game math, gametests, pack validation, lang parity).
## Wave 5 — review (→ wave 4): reviewers per edition + localization review (RU) + CI review; fixes; README; merge.

## v0.1.0 additions (user requests, in progress)
Pipeline per feature: research → design → architecture → development (self-checked) → independent testing → review → merge.

| Track | Research | Design | Architecture | Development | Test / review |
|---|---|---|---|---|---|
| Baccarat (+ Chemin de fer) | — | GAME_DESIGN §20 ✅ | existing table framework | Java + Bedrock (in progress) | per edition |
| Ultimate Texas Hold'em (+ player-banked) | — | GAME_DESIGN §21 ✅ | existing table framework | Java + Bedrock (in progress) | per edition |
| PvP versions of solo games (slots, coin flip, wheel, plinko, scratch) + entertainment layer | — | PVP.md (in progress) | shared match/escrow framework (→ design) | per game × edition | per edition |
| Seats & Bots (humans only / bots only / mixed; difficulty) | docs/research/bots.md (in progress) | BOTS.md (in progress, → research) | core seats/bots framework both editions (→ BOTS.md, PVP.md) | core framework × 2, then per-game integration (poker, baccarat, UTH, blackjack, house tables, PvP modes) | per edition |
| Per-world casino mode (Java) | Enchantaholic reference | ✅ | ✅ | ✅ | in final test/review |

Release: all tracks green in CI → squash-merge PR #1 → tag v0.1.0.
