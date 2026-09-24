package dev.nezo.burmaldaholic.games.slots.v2.logic;

import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;

/**
 * Builds the {@link Timeline} of one spin from its tape (slots.md §2.2–§2.3). Consumed by the server
 * settle timer, the screen, the BER and — through the identical Bedrock port — the DDUI form and the
 * {@code slot_reels} entity. Beat kinds are the constants below ({@code slots.*}). SKELETON — lane S-J3 /
 * S-B3 (vectors {@code slots_timeline.json}).
 */
public final class SlotTimeline {
	public static final String SPIN_UP = "slots.spin_up";
	public static final String REEL_LAND = "slots.reel_land";
	public static final String ANTICIPATE = "slots.anticipate";
	public static final String SYMBOL_LAND = "slots.symbol_land";
	public static final String WIN_SHOW = "slots.win_show";
	public static final String WAY_CYCLE = "slots.way_cycle";
	public static final String TUMBLE_EXPLODE = "slots.tumble_explode";
	public static final String TUMBLE_FALL = "slots.tumble_fall";
	public static final String MULT_UP = "slots.mult_up";
	public static final String WILD_EXPAND = "slots.wild_expand";
	public static final String WILD_STICK = "slots.wild_stick";
	public static final String FS_INTRO = "slots.fs_intro";
	public static final String FS_SPIN = "slots.fs_spin";
	public static final String FS_RETRIGGER = "slots.fs_retrigger";
	public static final String FS_OUTRO = "slots.fs_outro";
	public static final String BONUS_INTRO = "slots.bonus_intro";
	public static final String HUNT_OPEN = "slots.hunt_open";
	public static final String HOARD_RESPIN = "slots.hoard_respin";
	public static final String HOARD_COLLECT = "slots.hoard_collect";
	public static final String WHEEL_SPIN = "slots.wheel_spin";
	public static final String WHEEL_UP = "slots.wheel_up";
	public static final String JACKPOT = "slots.jackpot";
	public static final String ROLLUP = "slots.rollup";
	public static final String MAX_WIN = "slots.max_win";
	public static final String END = "slots.end";

	private SlotTimeline() {}

	/**
	 * @param tape    the drawn spin
	 * @param def     machine
	 * @param shared  speed of the SPINNING player (turbo = 200) — published to spectators with the seed
	 * @param local   the viewer's local profile (roll-ups, banners)
	 * @param seed    cosmetic seed ({@code SeedMix.mix(posHash, spinSeq)})
	 */
	public static Timeline build(SpinTape tape, MachineDef def, TimingProfile shared, TimingProfile local, int seed) {
		throw new UnsupportedOperationException("slots v2 timeline: lane S-J3");
	}
}
