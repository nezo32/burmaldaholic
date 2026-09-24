package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import java.util.Arrays;

/**
 * What the cabinet shows at one instant, as data (docs/architecture/animation.md §3.6 frame model). Mutable and
 * reused by the BER (no allocation per frame); {@link #equals} compares every field so fidelity tests can assert
 * {@code frame(end) == terminal}. Rows are in cell units, row 0 = top row of the reel window; a cell drawn at
 * {@code y} covers rows {@code [y, y + 1)} and is clipped to the window {@code [0, 3)}.
 */
public final class CabinetFrame {
	public static final int REELS = CabinetSync.REELS;
	/** Max cells drawn per reel (4 scrolling + 3 landed during a reduce-motion cross-fade, + 1 spare). */
	public static final int SLOTS = 8;
	/** {@link #chest} value of a closed chest. */
	public static final int CHEST_CLOSED = Integer.MIN_VALUE;

	/** Spin shown (-1 = the rest window before the first spin). */
	public int spin;
	public final int[] count = new int[REELS];
	public final int[][] sym = new int[REELS][SLOTS];
	public final float[][] y = new float[REELS][SLOTS];
	public final float[][] alpha = new float[REELS][SLOTS];
	public final float[][] scale = new float[REELS][SLOTS];
	/** Emissive burn overlay (tumble explode), 0–1. */
	public final float[][] burn = new float[REELS][SLOTS];
	/** Brightness multiplier (win-show dim of non-winning cells). */
	public final float[][] bright = new float[REELS][SLOTS];
	/** Cell uses the blur frame (reel at full speed). */
	public final boolean[][] blur = new boolean[REELS][SLOTS];
	/** Vertical squash of the reel's cells (landing, slots.md {@code SQUASH}). */
	public final float[] squash = new float[REELS];
	/** Anticipation frame glow on the reel, 0–1. */
	public final float[] anticipate = new float[REELS];

	/** Win frame cells (cell = reel × 3 + row) and the blink phase. */
	public int winMask;
	public boolean winOn;

	/** End sticky wild: expanded egg height as a fraction of the reel (0 = none, 1/3 … 1) and its row. */
	public final float[] eggGrow = new float[REELS];
	public final int[] eggRow = new int[REELS];
	/** Amethyst chain frame alpha and scale. */
	public final float[] chain = new float[REELS];
	public final float[] chainScale = new float[REELS];

	/** Piglin's Hoard grid. */
	public boolean hoard;
	public float hoardFade;
	public int coinMask;
	public final int[] coinValue = new int[CabinetSync.CELLS];
	/** Per cell: mini-reel blur phase while respinning (-1 = still). */
	public final float[] cellSpin = new float[CabinetSync.CELLS];
	/** Per cell: coin scale (land thump / lock pop). */
	public final float[] coinPop = new float[CabinetSync.CELLS];
	/** Per cell: collect highlight 0–1. */
	public final float[] coinLit = new float[CabinetSync.CELLS];
	public int pips;

	/** Treasure Hunt board: per cell {@link #CHEST_CLOSED} or the revealed entry. */
	public boolean hunt;
	public final int[] chest = new int[CabinetSync.CELLS];

	/** Dragon Wheel above the End cabinet: 0 hidden … 1 fully risen. */
	public float wheelRise;
	public int ringCount;
	public int activeRing;
	public final float[] ringAngle = new float[3];
	public final int[] ringSize = new int[3];
	public final float[] ringBright = new float[3];

	/** Floating {@code ×N} above the reels during a tumble step. */
	public float multAlpha;
	public float multRise;
	public int multValue;

	/** Floating tier word + amount after the reveal gate (Big+ / jackpot / max win). */
	public float tierAlpha;
	public float tierRise;
	public WinTier tier = WinTier.LOSS;
	public long amount;
	public int jackpotTier;
	public boolean maxWin;

	public Marquee.Pattern marquee = Marquee.Pattern.IDLE;
	/** Nothing animates any more (t ≥ {@link CabinetSync#endMs()}). */
	public boolean settled;

	void reset() {
		spin = -1;
		Arrays.fill(count, 0);
		for (int r = 0; r < REELS; r++) {
			Arrays.fill(sym[r], 0);
			Arrays.fill(y[r], 0);
			Arrays.fill(alpha[r], 0);
			Arrays.fill(scale[r], 0);
			Arrays.fill(burn[r], 0);
			Arrays.fill(bright[r], 0);
			Arrays.fill(blur[r], false);
		}
		Arrays.fill(squash, 1f);
		Arrays.fill(anticipate, 0);
		winMask = 0;
		winOn = false;
		Arrays.fill(eggGrow, 0);
		Arrays.fill(eggRow, 0);
		Arrays.fill(chain, 0);
		Arrays.fill(chainScale, 1f);
		hoard = false;
		hoardFade = 0;
		coinMask = 0;
		Arrays.fill(coinValue, 0);
		Arrays.fill(cellSpin, -1f);
		Arrays.fill(coinPop, 1f);
		Arrays.fill(coinLit, 0);
		pips = 0;
		hunt = false;
		Arrays.fill(chest, CHEST_CLOSED);
		wheelRise = 0;
		ringCount = 0;
		activeRing = 0;
		Arrays.fill(ringAngle, 0);
		Arrays.fill(ringSize, 0);
		Arrays.fill(ringBright, 0);
		multAlpha = 0;
		multRise = 0;
		multValue = 0;
		tierAlpha = 0;
		tierRise = 0;
		tier = WinTier.LOSS;
		amount = 0;
		jackpotTier = 0;
		maxWin = false;
		marquee = Marquee.Pattern.IDLE;
		settled = false;
	}

	/** Adds a cell to reel {@code r}; ignored when the reel is full or the cell is fully outside the window. */
	void cell(int r, int symbol, float row, float a, float s, boolean blurred) {
		if (count[r] >= SLOTS || row <= -1f || row >= 3f || a <= 0f) return;
		int i = count[r]++;
		sym[r][i] = symbol;
		y[r][i] = row;
		alpha[r][i] = a;
		scale[r][i] = s;
		burn[r][i] = 0;
		bright[r][i] = 1f;
		blur[r][i] = blurred;
	}

	/** Symbols of the landed rows 0–2 when the reel shows exactly three still cells, else -1 per row. */
	public int[] landedWindow() {
		int[] w = new int[CabinetSync.CELLS];
		Arrays.fill(w, -1);
		for (int r = 0; r < REELS; r++) {
			for (int i = 0; i < count[r]; i++) {
				float row = y[r][i];
				if (row == Math.round(row) && alpha[r][i] == 1f && !blur[r][i] && row >= 0 && row < 3) w[r * 3 + (int) row] = sym[r][i];
			}
		}
		return w;
	}

	private String signature() {
		StringBuilder b = new StringBuilder(512);
		b.append(spin).append('|');
		for (int r = 0; r < REELS; r++) {
			b.append('[').append(count[r]);
			for (int i = 0; i < count[r]; i++) {
				b.append(';').append(sym[r][i]).append(',').append(y[r][i]).append(',').append(alpha[r][i]).append(',').append(scale[r][i])
					.append(',').append(burn[r][i]).append(',').append(bright[r][i]).append(',').append(blur[r][i]);
			}
			b.append(']');
		}
		b.append(Arrays.toString(squash)).append(Arrays.toString(anticipate)).append(winMask).append(winOn)
			.append(Arrays.toString(eggGrow)).append(Arrays.toString(eggRow)).append(Arrays.toString(chain)).append(Arrays.toString(chainScale))
			.append(hoard).append(hoardFade).append(coinMask).append(Arrays.toString(coinValue)).append(Arrays.toString(cellSpin))
			.append(Arrays.toString(coinPop)).append(Arrays.toString(coinLit)).append(pips).append(hunt).append(Arrays.toString(chest))
			.append(wheelRise).append(ringCount).append(activeRing).append(Arrays.toString(ringAngle)).append(Arrays.toString(ringSize))
			.append(Arrays.toString(ringBright)).append(multAlpha).append(multRise).append(multValue).append(tierAlpha).append(tierRise)
			.append(tier).append(amount).append(jackpotTier).append(maxWin).append(marquee).append(settled);
		return b.toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof CabinetFrame f && f.signature().equals(signature());
	}

	@Override
	public int hashCode() {
		return signature().hashCode();
	}

	@Override
	public String toString() {
		return signature();
	}
}
