package dev.nezo.burmaldaholic.core.pvp.logic;

import com.google.gson.JsonObject;

/**
 * One reveal step of a mode's timeline (PVP.md §3.11.4). The engine sends only revealed steps to
 * clients (tape confidentiality). {@code waitForAll}: the engine waits up to {@code ticks} or until
 * every online human participant pressed the mode's advance button (Spin! / Drop! / Scratch!); bots
 * "press" after their think time.
 *
 * @param kind  mode-specific id ({@code round_wait}, {@code spin}, {@code score}, {@code cell}, …)
 * @param ticks duration of the step
 * @param round 0-based round / ball / cell (-1 = n/a)
 * @param data  public data revealed by this step (never unrevealed tape parts)
 */
public record Step(String kind, int ticks, int round, boolean waitForAll, JsonObject data) {}
