package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import dev.nezo.burmaldaholic.client.fx.motion.MotionClock;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.games.craps.present.CrapsRollClock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * One {@link CrapsRollClock} per craps table on this client (S1): the screen and the in-world board share it, so both
 * show the same hand shake and the same throw at the same instant. The clock source is LOCAL monotonic ms
 * ({@link MotionClock#localMs}: {@link SmoothClock#wallMs}, frozen under {@link SmoothClock.Test#freezeLevelMs}),
 * because every throw is anchored on this client (SY-SPEC §2: the roller at the press, a spectator at the arrival), so
 * a server time jump never moves a throw; only a first sight reads the shared clock's age. Tests may swap it
 * ({@link #source}).
 */
public final class CrapsRollClocks {
	/** A table on this client: its dimension and position (another world / dimension at the same pos is another table). */
	private record Key(@Nullable ResourceKey<Level> dim, BlockPos pos) {}

	private static CrapsRollClock.Source source = MotionClock::localMs;
	private static final Map<Key, CrapsRollClock> CLOCKS = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, CrapsRollClock> e) {
			return size() > 64;
		}
	};

	static {
		// a world left / a server changed: every table is gone (T-CM M4)
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	private CrapsRollClocks() {}

	/** The clock of the table at {@code pos} in {@code level}'s dimension. */
	public static CrapsRollClock of(@Nullable Level level, BlockPos pos) {
		return CLOCKS.computeIfAbsent(new Key(level == null ? null : level.dimension(), pos.immutable()), k -> new CrapsRollClock(source));
	}

	/** The clock of the table at {@code pos} in the client's current level. */
	public static CrapsRollClock of(BlockPos pos) {
		return of(Minecraft.getInstance().level, pos);
	}

	/** Drops every clock (disconnect; tests). */
	public static void clear() {
		CLOCKS.clear();
	}

	/** Clocks held (tests). */
	public static int size() {
		return CLOCKS.size();
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
		if (!Double.isNaN(AnimClock.testLevelMs())) {
			c.pin(rollTick * 50.0);
			c.finishHomeMove(); // a frozen clock never moves dice home: it is at the next shooter's end
		}
	}
}
