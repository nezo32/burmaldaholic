package dev.nezo.burmaldaholic.client.dealer;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Base renderer of the dealer NPCs (blackjack, baccarat, Ultimate Texas Hold'em; task J-C11): the dealer skin on the
 * {@link DealerModel}, gesturing with the nearest table that is a {@link DealerCueSource} (≤ 3 blocks, the same rule as
 * "open the nearest table") — or with its own synced gesture data when the entity is a {@link GesturingDealer}. The lookup runs at most once per second per dealer; the pose is sampled every frame on
 * the shared clock, so the arm moves on the same tick as the cards. Reduce motion halves the arm amplitude.
 */
public class DealerRenderer<T extends Mob> extends HumanoidMobRenderer<T, DealerRenderState, DealerModel> {
	private static final int RADIUS = 3;
	private static final Map<Mob, Lookup> TABLES = new WeakHashMap<>();
	private final Identifier texture;

	private static final class Lookup {
		@Nullable BlockPos table;
		long checkedAt = Long.MIN_VALUE;
	}

	public DealerRenderer(EntityRendererProvider.Context context, Identifier texture) {
		super(context, new DealerModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		this.texture = texture;
	}

	@Override
	public DealerRenderState createRenderState() {
		return new DealerRenderState();
	}

	@Override
	public Identifier getTextureLocation(DealerRenderState state) {
		return texture;
	}

	@Override
	public void extractRenderState(T entity, DealerRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		Level level = entity.level();
		DealerCueSource.Cue cue = null;
		if (entity instanceof GesturingDealer d && d.dealerGesture() != DealerGesture.NONE) {
			// synced entity data set by the table on its beats (lane J-L4's blackjack / baccarat dealers)
			double age = (level.getGameTime() - d.dealerGestureTick() + partialTicks) * 50.0;
			if (age >= 0 && age < d.dealerGesture().ms) cue = new DealerCueSource.Cue(d.dealerGesture(), age, d.dealerGestureSeat());
		}
		if (cue == null) {
			DealerCueSource source = source(entity, level);
			cue = source == null ? null : source.dealerCue(level.getGameTime(), partialTicks);
		}
		double idle = AnimClock.levelMs(partialTicks) + entity.getId() * 731.0;
		if (cue == null) {
			state.gesturing = false;
			state.pose = DealerMotion.pose(DealerGesture.NONE, 0, 0, false, idle);
		} else {
			state.gesturing = true;
			state.pose = DealerMotion.pose(cue.gesture(), cue.ageMs(), cue.towardSeat(), FxSettings.reduceMotion(), idle);
		}
	}

	private static @Nullable DealerCueSource source(Mob entity, Level level) {
		Lookup l = TABLES.computeIfAbsent(entity, k -> new Lookup());
		long now = level.getGameTime();
		if (now - l.checkedAt >= 20 || now < l.checkedAt) {
			l.checkedAt = now;
			l.table = null;
			BlockPos center = entity.blockPosition();
			double best = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -1, -RADIUS), center.offset(RADIUS, 1, RADIUS))) {
				BlockEntity be = level.getBlockEntity(pos);
				if (be instanceof DealerCueSource) {
					double d = pos.distSqr(center);
					if (d < best) {
						best = d;
						l.table = pos.immutable();
					}
				}
			}
		}
		return l.table != null && level.getBlockEntity(l.table) instanceof DealerCueSource s ? s : null;
	}
}
