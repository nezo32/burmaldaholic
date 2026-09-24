/**
 * Pure slots logic, v1 (3×3 paylines; no @minecraft imports; unit-tested in *.test.ts next to it).
 * LEGACY (SLOTS.md §8.1, docs/architecture/animation.md A7): once `SLOTS_V2_ENABLED` (index.ts) is on, v1 only
 * matters for rounds drawn before the switch — core settles those from their persisted drawn tickets, and the v1
 * pools are migrated once by the v2 service (`migrateV1Pools`). Delete this folder one release after the cut-over.
 */
export * from './engine';
export * from './rtp';
export * from './jackpot';
export * from './spin';
