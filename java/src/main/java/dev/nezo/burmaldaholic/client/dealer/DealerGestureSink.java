package dev.nezo.burmaldaholic.client.dealer;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A dealer entity a table can make gesture (server side, one synced-data change per beat): the blackjack and baccarat
 * tables call {@link #gestureAt} instead of looking up their own dealer classes, so the worldgen house dealers
 * ({@code worldgen/npc/HouseDealer}) and the legacy game dealers both follow the cards (DL-SPEC §1 "Gestures").
 */
public interface DealerGestureSink {
	/** Plays {@code gesture} now. */
	void gesture(DealerGesture gesture);

	/** Main block of the table this dealer is bound to, or null when it has no binding (legacy dealers: by distance). */
	default @Nullable BlockPos boundTable() {
		return null;
	}

	/**
	 * The dealer of the table at {@code table} plays {@code gesture}: the sink bound to that table, else the nearest
	 * unbound sink within {@code radius} blocks (legacy dealers). A dealer bound to another table never answers.
	 */
	static void gestureAt(ServerLevel level, BlockPos table, double radius, DealerGesture gesture) {
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		Vec3 c = Vec3.atCenterOf(table);
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(table).inflate(radius), m -> m instanceof DealerGestureSink)) {
			BlockPos bound = ((DealerGestureSink) m).boundTable();
			if (bound != null && !bound.equals(table)) {
				continue;
			}
			double d = bound != null ? -1 : m.distanceToSqr(c); // the bound dealer wins over any unbound one
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		if (best != null) {
			((DealerGestureSink) best).gesture(gesture);
		}
	}
}
