package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Member;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Size;

/**
 * Config section `slots` — keys, defaults and ranges from docs/design/CONFIG.md (slots v2, SLOTS.md §12). The v1 3×3 keys
 * ({@code slots.copper.*}, {@code .gold.*}, {@code .netherite.*}, {@code .jackpot.seed / .contribution},
 * {@code .ownedStarPays}, {@code .spinTicks}) are gone: an old config keeps them as unknown keys, ignored with a warning.
 */
public final class SlotsConfig {
	public boolean enabled = true;
	public static final class Jackpot {
		/** Server-wide chat from this jackpot tier up (SLOTS.md §12). */
		public SlotsV2Config.AnnounceTier announceMinTier = SlotsV2Config.AnnounceTier.MAJOR;
	}
	public Jackpot jackpot = new Jackpot();

	/** On load, compute each machine's RTP (SLOTS.md §7.5) from the config; a machine above 0.99, or a buy feature above its machine, logs a loud warning and shows it on the admin page (never auto-fix). */
	public boolean validateRtp = true;

	@Member("gui.burmaldaholic.slots.machine.overworld")
	public SlotsV2Config.Overworld overworld = new SlotsV2Config.Overworld();
	@Member("gui.burmaldaholic.slots.machine.nether")
	public SlotsV2Config.Nether nether = new SlotsV2Config.Nether();
	@Member("gui.burmaldaholic.slots.machine.end")
	public SlotsV2Config.End end = new SlotsV2Config.End();
	public SlotsV2Config.BuyFeature buyFeature = new SlotsV2Config.BuyFeature();
	public SlotsV2Config.Autoplay autoplay = new SlotsV2Config.Autoplay();
	public boolean turboAllowed = true;
	/** Off: reels always stop on the base schedule. */
	public boolean anticipation = true;
	/** Nice / Big / Mega / Epic thresholds (× bet). */
	@Range(min = 1, max = 10000) @Size(min = 4, max = 4) public int[] bigWinTiers = {5, 15, 40, 100};
	public SlotsV2Config.InWorld inWorld = new SlotsV2Config.InWorld();
}
