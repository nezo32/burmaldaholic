package dev.nezo.burmaldaholic.games.slots.v2.present;

/**
 * The beat CONTRACT between the {@code SlotTimeline} builder and every consumer of slot frames (the screen, the cabinet
 * sync, the server settle gate). Kinds are the
 * {@code SlotTimeline.*} constants; this class fixes their {@code lane} and {@code args} layout. All amounts are
 * CHIPS (already × bet); all times integer ms. Nothing here is ever revealed before its beat's {@code at}
 * (slots.md §2.1 F7), so a beat's args may carry the data its frames need.
 *
 * <pre>
 * kind            lane            dur                       args
 * SPIN_UP         -1              spin-up (120)             [spin]                      spin 0 = base, i+1 = free spin i
 * REEL_LAND       reel 0..4       landing (350 / 600 ant.)  [spin, stop, anticipated]   end = the reel's stop time
 * ANTICIPATE      reel 0..4       prev stop → this stop     [spin, reason]              reason 1 scatter, 2 bonus, 3 coins
 * SYMBOL_LAND     reel            220                       [spin, row, symbol, nth]    special symbol pop (nth of its role)
 * WILD_EXPAND     reel            300                       [spin]
 * WILD_STICK      reel            200                       [spin, stickyMaskAfter]     bit r-1 for reels 2..4
 * WIN_SHOW        -1              overview (900; 600 tumble)[spin, step, winMask, payChips, mult]
 * WAY_CYCLE       cycle index     700                       [spin, step, symbol, k, ways, payChips, cellMask]
 *                                                            scatter entry: symbol = scatter, k = count, ways = 0
 * TUMBLE_EXPLODE  -1              250                       [spin, step, explodeMask, stepPayChips, mult]
 * MULT_UP         -1              200                       [spin, step, mult, ladderIndex]
 * TUMBLE_FALL     -1              300                       [spin, step, c0 … c14]      window after the refill
 * FS_INTRO        -1              2000                      [awarded, scatterMask]
 * FS_SPIN         free spin i     whole free spin           [i, spinsTotal, featureChipsBefore, mult]
 * FS_RETRIGGER    -1              1200                      [added, spinsTotal]
 * FS_OUTRO        -1              roll-up + 1500            [featureChips]
 * BONUS_INTRO     -1              600 / 800 / 700           [feature, triggerMask, …]   2 hunt, 3 hoard [coinMask, v…], 4 wheel
 * HUNT_OPEN       pick index      400 + 300                 [entry]                     (not in a server timeline: picks are sent per pick)
 * HUNT_END        -1              80 × board + 900          []                          rest reveal after the picks; the timeline PAUSES
 *                                                                                        at the end of the hunt BONUS_INTRO until then
 * HOARD_RESPIN    respin index    900                       [newMask, respinsLeft, v…]  v = value code per new cell, cell-index order
 * HOARD_COLLECT   -1              120 × coins + 300         [totalChips, full]
 * WHEEL_SPIN      ring 0..2       4500 / 4000 / 5000        [segment]
 * WHEEL_UP        ring left       800                       []
 * BONUS_END       -1              600 glow + 400 exit       [feature]                   (wheel)
 * ROLLUP          -1              roll-up + 800 hold        [chips, tierOrdinal, bet]   LOCAL clock, first after the gate
 * WAY_CYCLE entries run next to the ROLLUP (LOCAL, base game without a feature)
 * JACKPOT         award index     by sub-tier (§4.11)       [tier, chips]               LOCAL clock, after the roll-up
 * MAX_WIN         -1              500                       []
 * END             -1              0                         []
 * </pre>
 *
 * Value codes (Hunt entries, Hoard coins): positive = × bet, {@code -1 … -4} = Mini … Grand, 0 = Creeper.
 * Skip groups: 0 = base reels, 1 + step for later steps; the builder assigns them (slots.md §2.2).
 */
public final class SlotBeats {
	/** First args of every reel-phase beat. */
	public static final int SPIN = 0;

	// REEL_LAND
	public static final int LAND_STOP = 1;
	public static final int LAND_ANTICIPATED = 2;
	// SYMBOL_LAND
	public static final int SYM_ROW = 1;
	public static final int SYM_SYMBOL = 2;
	public static final int SYM_NTH = 3;
	// WIN_SHOW
	public static final int WIN_STEP = 1;
	public static final int WIN_MASK = 2;
	public static final int WIN_PAY = 3;
	public static final int WIN_MULT = 4;
	// WAY_CYCLE
	public static final int WAY_STEP = 1;
	public static final int WAY_SYMBOL = 2;
	public static final int WAY_K = 3;
	public static final int WAY_WAYS = 4;
	public static final int WAY_PAY = 5;
	public static final int WAY_MASK = 6;
	// TUMBLE_*
	public static final int TUMBLE_STEP = 1;
	public static final int TUMBLE_MASK = 2;
	public static final int TUMBLE_PAY = 3;
	public static final int TUMBLE_MULT = 4;
	public static final int FALL_CELLS = 2;
	// MULT_UP
	public static final int MULT_VALUE = 2;
	public static final int MULT_INDEX = 3;
	// WILD_STICK
	public static final int STICK_MASK = 1;

	/** Feature codes of {@code BONUS_INTRO} (the same as {@code Showdown.Spin.featureCode}). */
	public static final int FEATURE_HUNT = 2;
	public static final int FEATURE_HOARD = 3;
	public static final int FEATURE_WHEEL = 4;

	private SlotBeats() {}
}
