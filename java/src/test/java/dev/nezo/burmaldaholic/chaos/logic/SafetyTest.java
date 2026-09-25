package dev.nezo.burmaldaholic.chaos.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.chaos.logic.Safety.Action;
import dev.nezo.burmaldaholic.chaos.logic.Safety.BlockInfo;
import dev.nezo.burmaldaholic.chaos.logic.Safety.LandingColumn;
import dev.nezo.burmaldaholic.chaos.logic.Safety.PlayerSnapshot;
import dev.nezo.burmaldaholic.chaos.logic.Safety.SkipReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafetyTest {
	private static final int GRACE = 200;

	/** A player that nothing blocks. */
	private static PlayerSnapshot ok() {
		return new PlayerSnapshot(false, false, -1, false, false, false, 0, false, false, false, false, false, true);
	}

	private static PlayerSnapshot with(java.util.function.UnaryOperator<Object[]> f) {
		PlayerSnapshot s = ok();
		Object[] a = {s.creativeOrSpectator(), s.dead(), s.ticksSinceRespawn(), s.sleeping(), s.gliding(), s.riding(), s.fallDistance(),
			s.casinoScreenOpen(), s.inRound(), s.nearBoss(), s.inClaim(), s.peaceful(), s.overworld()};
		a = f.apply(a);
		return new PlayerSnapshot((boolean) a[0], (boolean) a[1], (long) a[2], (boolean) a[3], (boolean) a[4], (boolean) a[5], (double) a[6],
			(boolean) a[7], (boolean) a[8], (boolean) a[9], (boolean) a[10], (boolean) a[11], (boolean) a[12]);
	}

	private static PlayerSnapshot set(int index, Object value) {
		return with(a -> {
			a[index] = value;
			return a;
		});
	}

	private static SkipReason skip(ChaosEvent e, PlayerSnapshot s) {
		Safety.Verdict v = Safety.evaluate(e, s, GRACE);
		assertEquals(Action.SKIP, v.action(), e + " should skip");
		return v.reason();
	}

	@Test
	void everythingRunsForAPlainPlayer() {
		for (ChaosEvent e : ChaosEvent.values()) {
			assertEquals(Action.RUN, Safety.evaluate(e, ok(), GRACE).action(), e.id());
		}
	}

	@Test
	void generalRules() {
		for (ChaosEvent e : ChaosEvent.values()) {
			if (e == ChaosEvent.GOLDEN_HOUR) {
				assertEquals(Action.RUN, Safety.evaluate(e, set(0, true), GRACE).action(), "server-wide, never per-player blocked");
				continue;
			}
			assertEquals(SkipReason.GAME_MODE, skip(e, set(0, true)));
			assertEquals(SkipReason.DEAD, skip(e, set(1, true)));
			assertEquals(SkipReason.RESPAWN, skip(e, set(2, 199L)));
			assertEquals(SkipReason.SLEEPING, skip(e, set(3, true)));
			assertEquals(Action.DEFER, Safety.evaluate(e, set(7, true), GRACE).action());
		}
		assertEquals(Action.RUN, Safety.evaluate(ChaosEvent.CURSE, set(2, 200L), GRACE).action(), "grace over");
	}

	@Test
	void mobWaveRules() {
		assertEquals(SkipReason.PEACEFUL, skip(ChaosEvent.MOB_WAVE, set(11, true)));
		assertEquals(SkipReason.BOSS, skip(ChaosEvent.MOB_WAVE, set(9, true)));
		assertEquals(SkipReason.CLAIM, skip(ChaosEvent.MOB_WAVE, set(10, true)));
		assertEquals(Action.RUN, Safety.evaluate(ChaosEvent.MOB_WAVE, set(8, true), GRACE).action(), "a table round does not stop a wave");
	}

	@Test
	void teleportRules() {
		assertEquals(SkipReason.GLIDING, skip(ChaosEvent.RANDOM_TELEPORT, set(4, true)));
		assertEquals(SkipReason.RIDING, skip(ChaosEvent.RANDOM_TELEPORT, set(5, true)));
		assertEquals(SkipReason.FALLING, skip(ChaosEvent.RANDOM_TELEPORT, set(6, 3.5)));
		assertEquals(Action.RUN, Safety.evaluate(ChaosEvent.RANDOM_TELEPORT, set(6, 3.0), GRACE).action());
		assertEquals(SkipReason.BOSS, skip(ChaosEvent.RANDOM_TELEPORT, set(9, true)));
		assertEquals(SkipReason.IN_ROUND, skip(ChaosEvent.RANDOM_TELEPORT, set(8, true)));
		assertEquals(Action.RUN, Safety.evaluate(ChaosEvent.CHIP_SHOWER, set(4, true), GRACE).action(), "gliding only blocks teleport");
	}

	@Test
	void weatherOnlyInOverworld() {
		assertEquals(SkipReason.DIMENSION, skip(ChaosEvent.WEATHER_CHANGE, set(12, false)));
	}

	// ---- landing -------------------------------------------------------------------------------

	private static BlockInfo solid(String id) {
		return new BlockInfo(id, false, false, true, true);
	}

	private static final BlockInfo AIR = new BlockInfo("air", true, false, false, false);
	private static final BlockInfo GRASS = new BlockInfo("short_grass", false, false, false, false);
	private static final BlockInfo WATER = new BlockInfo("water", false, true, false, false);

	private static LandingColumn col(String dim, int y, BlockInfo ground, BlockInfo feet, BlockInfo head, List<BlockInfo> below) {
		return new LandingColumn(dim, y, -64, ground, feet, head, below);
	}

	private static LandingColumn col(String dim, int y, BlockInfo ground) {
		return col(dim, y, ground, AIR, AIR, List.of(solid("stone"), solid("stone"), solid("stone")));
	}

	@Test
	void landingOnGrassIsSafe() {
		assertTrue(Safety.isSafeLanding(col("overworld", 64, solid("grass_block"))));
		assertTrue(Safety.isSafeLanding(col("overworld", 64, solid("grass_block"), GRASS, AIR, List.of())), "plants at feet are fine");
	}

	@Test
	void hazardsAreRejected() {
		for (String bad : List.of("lava", "magma_block", "fire", "campfire", "soul_campfire", "cactus", "sweet_berry_bush", "powder_snow",
				"pointed_dripstone", "water")) {
			assertFalse(Safety.isSafeLanding(col("overworld", 64, solid(bad))), bad);
		}
		assertFalse(Safety.isSafeLanding(col("overworld", 64, new BlockInfo("oak_slab", false, false, false, true))), "not a full top");
		assertFalse(Safety.isSafeLanding(col("overworld", 64, WATER)));
		assertFalse(Safety.isSafeLanding(col("overworld", 64, solid("stone"), WATER, AIR, List.of())), "water at feet");
		assertFalse(Safety.isSafeLanding(col("overworld", 64, solid("stone"), AIR, solid("stone"), List.of())), "no headroom");
		assertFalse(Safety.isSafeLanding(col("overworld", 64, solid("stone"), new BlockInfo("fire", false, false, false, false), AIR, List.of())),
			"fire at feet");
	}

	@Test
	void minYAndNetherRoof() {
		assertFalse(Safety.isSafeLanding(col("overworld", -60, solid("deepslate"))), "y+1 must be > minY + 5");
		assertTrue(Safety.isSafeLanding(col("overworld", -58, solid("deepslate"))));
		assertTrue(Safety.isSafeLanding(col("the_nether", 100, solid("netherrack"))));
		assertFalse(Safety.isSafeLanding(col("the_nether", 118, solid("netherrack"))), "head would be at 120");
		assertFalse(Safety.isSafeLanding(col("the_nether", 127, solid("bedrock"))), "roof");
	}

	@Test
	void endNeedsEndStoneWithThreeSolidBelow() {
		assertTrue(Safety.isSafeLanding(col("the_end", 60, solid("end_stone"), AIR, AIR, List.of(solid("end_stone"), solid("end_stone"), solid("end_stone")))));
		assertFalse(Safety.isSafeLanding(col("the_end", 60, solid("obsidian"))), "only end stone");
		assertFalse(Safety.isSafeLanding(col("the_end", 60, solid("end_stone"), AIR, AIR, List.of(solid("end_stone"), AIR, solid("end_stone")))),
			"thin crust");
		assertFalse(Safety.isSafeLanding(col("the_end", 60, solid("end_stone"), AIR, AIR, List.of(solid("end_stone")))));
	}

	@Test
	void dropSurface() {
		assertFalse(Safety.safeDropSurface(null), "void");
		assertFalse(Safety.safeDropSurface(new BlockInfo("lava", false, true, false, false)));
		assertFalse(Safety.safeDropSurface(solid("cactus")));
		assertTrue(Safety.safeDropSurface(solid("stone")));
		assertTrue(Safety.safeDropSurface(WATER), "items float, they are not lost");
	}
}
