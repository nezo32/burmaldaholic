package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.motion.MotionClock;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.games.craps.present.CrapsRollClock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * One {@link CrapsRollClock} per craps table on this client (S1): the screen and the in-world board share it, so both
 * show the same hand shake and the same throw at the same instant. The clock source is local
 * monotonic ms; package SY swaps in its SmoothClock with {@link #source}).
 */
public final class CrapsRollClocks {
	private static CrapsRollClock.Source source = () -> MotionClock.localMs();
	private static final Map<BlockPos, CrapsRollClock> CLOCKS = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<BlockPos, CrapsRollClock> e) {
			return size() > 64;
		}
	};

	private CrapsRollClocks() {}

	public static CrapsRollClock of(BlockPos pos) {
		return CLOCKS.computeIfAbsent(pos.immutable(), p -> new CrapsRollClock(source));
	}

	/** Replaces the clock source of every table (SY SmoothClock); running throws keep their place. */
	public static void source(CrapsRollClock.Source s) {
		source = s;
		for (CrapsRollClock c : CLOCKS.values()) c.source(s);
	}

	/**
	 * Feeds a state's roll to the table's clock ({@code rollTick} = the server tick of the roll, {@code serverNowTicks}
	 * = the viewer's estimate of server time now; only a NEW roll counter does anything). Under a client GameTest's
	 * frozen clock the origin is pinned to the server tick, so storyboard frames stay deterministic.
	 */
	public static boolean onRoll(CrapsRollClock c, int rolls, long rollTick, double serverNowTicks, @Nullable DiceRoll r, int side) {
		boolean fresh = c.onRoll(rolls, (serverNowTicks - rollTick) * 50.0, r, side);
		pinUnderTest(c, rollTick);
		return fresh;
	}

	/** Test hook: with a frozen test clock the throw's t = test level ms − roll tick · 50. */
	public static void pinUnderTest(CrapsRollClock c, long rollTick) {
		if (!Double.isNaN(AnimClock.testLevelMs())) c.pin(rollTick * 50.0);
	}
}
