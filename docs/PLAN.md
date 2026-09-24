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
