package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `multiplayer` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class MultiplayerConfig {
	@Range(min = 3, max = 32) public int tableLeaveDistance = 8;
	@Range(min = 0, max = 32) public int spectatorRadius = 8;
}
