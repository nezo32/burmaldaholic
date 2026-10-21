# Root cause: "I explored and found no casinos" (v0.1.1)

Measured 2026-10-07 with `WorldgenSpawnSurvey` (gametest; real structure-start generation of the
default world preset, our mixins included, 4 seeds). Full numbers and the fix plan:
[docs/design/CASINO_BUILDINGS.md](../design/CASINO_BUILDINGS.md) §1–§2.

1. **Casino worldgen is gated by casino mode, which defaults to OFF** (`WorldgenRuntime.active()` =
   `worldgen.enabled && CasinoMode.isEnabled`). A world created without pressing the "Casino Mode"
   button, and *every dedicated server* until an op runs `/casino mode on`, generates its spawn area
   and everything explored before that with no casinos — and there is no retro-generation. This is
   the most likely reason for "none at all" (the CurseForge reports are from a multiplayer server).
2. **Even with the mode on, casinos are rare by construction.** There is no casino structure: casinos
   are only annexes of vanilla structures — 35 % of villages (× 89 % street-end fit), 30 % of
   bastions, 20 % of End Cities. Overworld: **11 casinos in 4 × π·2000² blocks² = 1 per 4.6 km²**,
   the nearest one **820–1216 blocks from spawn** (4 seeds). A player must first find a village
   (~1 per 1.1 km²) and then hope it is one of the ~1 in 4 with a casino.
3. **They are not findable**: no structure id, so `/locate structure`, explorer maps and structure
   compasses cannot point at them; and the 17×17 flat-roofed box at the end of a village street reads
   as "just another house" from a distance.

Ruled out (verified): data pack not loading (pack `DEFAULT_ENABLED`, `casinoPackLoadedWithAllTemplates`
passes), biome tags (none are used — the host structure decides), dimension mismatch, terrain
adaptation (the piece is rigid; 39/44 villages accept it, failures are narrow snowy/taiga/desert
street ends; bastions 30/30 and End Cities 117/117 accept their room).
