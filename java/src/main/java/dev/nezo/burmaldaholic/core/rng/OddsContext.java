package dev.nezo.burmaldaholic.core.rng;

import java.util.UUID;

/** Who is playing what — passed to every {@link OddsModifier}. Pure data, no Minecraft types. */
public record OddsContext(UUID playerId, String gameId, long bet) {}
