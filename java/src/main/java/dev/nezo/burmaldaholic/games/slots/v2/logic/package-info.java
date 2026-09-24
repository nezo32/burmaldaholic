/**
 * Slots v2 engine (SLOTS.md): three 5×3 243-ways machines, tumbles, free spins, bonus games, progressive
 * jackpots, buy feature, max-win cap, owned-casino reservation, persisted tapes and Slot Showdown v2 scoring.
 *
 * <p>PURE (no Minecraft imports), mirrored 1:1 by {@code bedrock/src/games/slots/v2/logic/} and checked by the
 * same vectors (SLOTS.md §7.5 exact totals, {@code src/test/resources/fx/vectors/slots_engine.json},
 * {@code slots_timeline.json}). Decision (docs/architecture/animation.md §7): v2 is built NEXT TO v1
 * ({@code games.slots.logic}) and switched on in one cut-over task; v1 then only settles persisted v1 records
 * ({@code LegacySlots}) and is deleted one release later.
 *
 * <p>SKELETON: types and interfaces are final-shape; implementations marked "lane S-J…" throw
 * {@link UnsupportedOperationException} until their lane lands. Nothing here is wired into
 * {@code SlotsModule} yet.
 */
package dev.nezo.burmaldaholic.games.slots.v2.logic;
