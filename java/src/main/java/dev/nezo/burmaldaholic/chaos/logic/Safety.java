package dev.nezo.burmaldaholic.chaos.logic;

import java.util.List;
import java.util.Set;

/**
 * Chaos safety rules (GAME_DESIGN.md §13.4). PURE: the world layer takes a snapshot of the player
 * and the blocks; these functions decide.
 */
public final class Safety {
	private Safety() {}

	public enum SkipReason {
		GAME_MODE, DEAD, RESPAWN, SLEEPING, PEACEFUL, BOSS, CLAIM, GLIDING, RIDING, FALLING, IN_ROUND, DIMENSION
	}

	public enum Action {
		RUN, DEFER, SKIP
	}

	public record Verdict(Action action, SkipReason reason) {
		public static final Verdict RUN = new Verdict(Action.RUN, null);
		public static final Verdict DEFER = new Verdict(Action.DEFER, null);

		static Verdict skip(SkipReason r) {
			return new Verdict(Action.SKIP, r);
		}
	}

	/**
	 * @param ticksSinceRespawn world ticks since the last respawn, -1 = none recorded
	 * @param casinoScreenOpen  a casino menu/screen is open (event is deferred)
	 * @param inRound           seated at a table with an open stake (card/table round)
	 * @param nearBoss          within {@code chaos.bossSafeRadius} of a Wither, Warden or Ender Dragon
	 * @param inClaim           inside a claimed casino interior (§18)
	 */
	public record PlayerSnapshot(boolean creativeOrSpectator, boolean dead, long ticksSinceRespawn, boolean sleeping, boolean gliding,
			boolean riding, double fallDistance, boolean casinoScreenOpen, boolean inRound, boolean nearBoss, boolean inClaim, boolean peaceful,
			boolean overworld) {}

	/**
	 * Per-player checks for one event. Golden Hour is server-wide and not checked here.
	 * {@code mob_wave} in Peaceful → skip(PEACEFUL) and {@code weather_change} outside the Overworld →
	 * skip(DIMENSION): the caller rerolls ({@link ChaosRules#resolve}).
	 */
	public static Verdict evaluate(ChaosEvent event, PlayerSnapshot s, int respawnGraceTicks) {
		if (event == ChaosEvent.GOLDEN_HOUR) {
			return Verdict.RUN;
		}
		if (s.creativeOrSpectator()) {
			return Verdict.skip(SkipReason.GAME_MODE);
		}
		if (s.dead()) {
			return Verdict.skip(SkipReason.DEAD);
		}
		if (s.ticksSinceRespawn() >= 0 && s.ticksSinceRespawn() < respawnGraceTicks) {
			return Verdict.skip(SkipReason.RESPAWN);
		}
		if (s.sleeping()) {
			return Verdict.skip(SkipReason.SLEEPING);
		}
		if (s.casinoScreenOpen()) {
			return Verdict.DEFER;
		}
		return switch (event) {
			case MOB_WAVE -> s.peaceful() ? Verdict.skip(SkipReason.PEACEFUL)
				: s.nearBoss() ? Verdict.skip(SkipReason.BOSS)
				: s.inClaim() ? Verdict.skip(SkipReason.CLAIM)
				: Verdict.RUN;
			case RANDOM_TELEPORT -> s.gliding() ? Verdict.skip(SkipReason.GLIDING)
				: s.riding() ? Verdict.skip(SkipReason.RIDING)
				: s.fallDistance() > 3 ? Verdict.skip(SkipReason.FALLING)
				: s.nearBoss() ? Verdict.skip(SkipReason.BOSS)
				: s.inRound() ? Verdict.skip(SkipReason.IN_ROUND)
				: Verdict.RUN;
			case WEATHER_CHANGE -> s.overworld() ? Verdict.RUN : Verdict.skip(SkipReason.DIMENSION);
			default -> Verdict.RUN;
		};
	}

	// ---- teleport landing ----------------------------------------------------------------------

	/** Blocks a player must never land on (§13.4), by vanilla registry path. */
	public static final Set<String> UNSAFE_GROUND = Set.of("lava", "magma_block", "fire", "soul_fire", "campfire", "soul_campfire", "cactus",
		"sweet_berry_bush", "powder_snow", "pointed_dripstone", "water", "bedrock", "barrier", "structure_void", "light", "end_portal",
		"nether_portal", "end_gateway", "wither_rose", "scaffolding", "end_portal_frame");

	/** Hazards the item drops of {@code diamond_rain}/{@code chip_shower} must not fall into. */
	public static final Set<String> UNSAFE_DROP_SURFACE = Set.of("lava", "fire", "soul_fire", "cactus", "magma_block");

	/**
	 * One block as seen by the landing rules.
	 *
	 * @param id        vanilla registry path ("stone")
	 * @param air       an air block
	 * @param fluid     contains any fluid (water, lava, waterlogged)
	 * @param sturdyTop its top face is a full sturdy face (can be stood on)
	 * @param collides  has any collision shape (a player could not stand inside it)
	 */
	public record BlockInfo(String id, boolean air, boolean fluid, boolean sturdyTop, boolean collides) {}

	/** Solid, full, non-hazardous ground. */
	public static boolean isSolidGround(BlockInfo b) {
		return !b.air() && !b.fluid() && b.sturdyTop() && !UNSAFE_GROUND.contains(b.id());
	}

	/** Free space for the player's feet/head: no collision, no fluid, no hazard. */
	public static boolean isFreeSpace(BlockInfo b) {
		return (b.air() || !b.collides()) && !b.fluid() && !UNSAFE_GROUND.contains(b.id());
	}

	/**
	 * @param dimension "overworld", "the_nether", "the_end"
	 * @param y         y of the ground block
	 * @param below     blocks under the ground, top first (End rule needs 3)
	 */
	public record LandingColumn(String dimension, int y, int minY, BlockInfo ground, BlockInfo feet, BlockInfo head, List<BlockInfo> below) {}

	/** §13.4 teleport target rules for one column (world border / chunk checks are done by the caller). */
	public static boolean isSafeLanding(LandingColumn c) {
		if (!isSolidGround(c.ground()) || !isFreeSpace(c.feet()) || !isFreeSpace(c.head())) {
			return false;
		}
		// the player stands at y + 1: must be above dimension min Y + 5
		if (c.y() + 1 <= c.minY() + 5) {
			return false;
		}
		if (c.dimension().equals("the_nether") && c.y() + 2 >= 120) {
			return false;
		}
		if (c.dimension().equals("the_end")) {
			if (!c.ground().id().equals("end_stone") || c.below().size() < 3) {
				return false;
			}
			for (int i = 0; i < 3; i++) {
				if (!isSolidGround(c.below().get(i))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Items may not be dropped into lava/void: the first non-air block below must exist and be safe. */
	public static boolean safeDropSurface(BlockInfo firstNonAirBelow) {
		return firstNonAirBelow != null && !UNSAFE_DROP_SURFACE.contains(firstNonAirBelow.id()) && !"lava".equals(firstNonAirBelow.id());
	}
}
