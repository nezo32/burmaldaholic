package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `ownership` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class OwnershipConfig {
	public boolean enabled = true;
	@Range(min = 0, max = 1000000000) public int licenseFee = 1000;
	@Range(min = 4, max = 128) public int claimRadius = 24;
	@Range(min = 0, max = 16) public int maxPerPlayer = 1;
	public boolean protectTables = true;
	public boolean explosionProof = true;
}
