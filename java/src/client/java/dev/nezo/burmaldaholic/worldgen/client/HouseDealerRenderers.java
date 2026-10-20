package dev.nezo.burmaldaholic.worldgen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.dealer.DealerCueSource;
import dev.nezo.burmaldaholic.client.dealer.DealerModel;
import dev.nezo.burmaldaholic.client.dealer.DealerMotion;
import dev.nezo.burmaldaholic.client.dealer.DealerRenderState;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealer;
import dev.nezo.burmaldaholic.worldgen.npc.NpcContent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Renderers of the three house dealer types (DL-SPEC §1/§3): human on the player model, piglin on the adult piglin
 * model, enderman (0.9 scale, vanilla eyes layer, no carried block) on the enderman model, each wearing its
 * {@code LOOK} skin. The pose is the dealer's synced gesture (blackjack / baccarat push it) or, else, the cue of its
 * bound table ({@link DealerCueSource}, read from the synced TABLE: no block-entity scans). Bound to a craps table the
 * dealer holds a stick (render-only, never equipment).
 */
final class HouseDealerRenderers {
	private static final ItemStack STICK = new ItemStack(Items.STICK);

	private HouseDealerRenderers() {}

	static void register() {
		EntityRenderers.register(NpcContent.HOUSE_DEALER, HumanRenderer::new);
		EntityRenderers.register(NpcContent.PIGLIN_HOUSE_DEALER, PiglinDealerRenderer::new);
		EntityRenderers.register(NpcContent.ENDER_HOUSE_DEALER, EnderDealerRenderer::new);
	}

	static Identifier texture(String name) {
		return Burmaldaholic.id("textures/entity/worldgen/" + name + ".png");
	}

	private static Identifier[] pool(String prefix, int n) {
		Identifier[] out = new Identifier[n];
		for (int i = 0; i < n; i++) {
			out[i] = texture(prefix + i);
		}
		return out;
	}

	static final Identifier[] HUMAN = pool("dealer/human_", dev.nezo.burmaldaholic.worldgen.logic.NpcLook.HUMAN);
	static final Identifier[] PIGLIN = pool("dealer/piglin_", dev.nezo.burmaldaholic.worldgen.logic.NpcLook.PIGLIN);
	static final Identifier[] ENDER = pool("dealer/ender_", dev.nezo.burmaldaholic.worldgen.logic.NpcLook.ENDER);

	static Identifier pick(Identifier[] pool, int look) {
		return pool[Math.floorMod(look, pool.length)];
	}

	/** This frame's pose of {@code dealer}; {@code out[0]} = a gesture is playing. */
	static DealerMotion.Pose pose(HouseDealer dealer, float partialTicks, boolean[] gesturing) {
		Level level = dealer.level();
		DealerCueSource.Cue cue = null;
		DealerGesture g = dealer.dealerGesture();
		if (g != DealerGesture.NONE) {
			double age = (level.getGameTime() - dealer.dealerGestureTick() + partialTicks) * 50.0;
			if (age >= 0 && age < g.ms) {
				cue = new DealerCueSource.Cue(g, age, dealer.dealerGestureSeat());
			}
		}
		BlockPos table = dealer.table();
		if (cue == null && table != null && level.getBlockEntity(table) instanceof DealerCueSource source) {
			cue = source.dealerCue(level.getGameTime(), partialTicks);
		}
		double idle = AnimClock.levelMs(partialTicks) + dealer.getId() * 731.0;
		gesturing[0] = cue != null;
		return cue == null ? DealerMotion.pose(DealerGesture.NONE, 0, 0, false, idle)
			: DealerMotion.pose(cue.gesture(), cue.ageMs(), cue.towardSeat(), FxSettings.reduceMotion(), idle);
	}

	/** True when the dealer works a craps table (holds the stick). */
	static boolean stickman(HouseDealer dealer) {
		BlockPos table = dealer.table();
		return table != null && TableParts.gameOf(dealer.level().getBlockState(table)) == TableGame.CRAPS;
	}

	static void stick(HouseDealer dealer, ArmedEntityRenderState state, ItemModelResolver items) {
		if (stickman(dealer)) {
			items.updateForLiving(state.rightHandItemState, STICK, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, dealer);
			state.rightHandItemStack = STICK;
		}
	}

	// ---- human -------------------------------------------------------------------------------------------------

	static final class HumanRenderer extends HumanoidMobRenderer<HouseDealer, DealerRenderState, DealerModel> {
		HumanRenderer(EntityRendererProvider.Context context) {
			super(context, new DealerModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		}

		@Override
		public DealerRenderState createRenderState() {
			return new HumanState();
		}

		@Override
		public Identifier getTextureLocation(DealerRenderState state) {
			return pick(HUMAN, state instanceof HumanState s ? s.look : 0);
		}

		@Override
		public void extractRenderState(HouseDealer entity, DealerRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			boolean[] g = new boolean[1];
			state.pose = pose(entity, partialTicks, g);
			state.gesturing = g[0];
			if (state instanceof HumanState s) {
				s.look = entity.look();
			}
			stick(entity, state, itemModelResolver);
		}
	}

	static final class HumanState extends DealerRenderState {
		int look;
	}

	// ---- piglin ------------------------------------------------------------------------------------------------

	static final class PiglinState extends PiglinRenderState {
		DealerMotion.Pose pose = DealerMotion.Pose.REST;
		boolean gesturing;
		int look;
	}

	/** The adult piglin with the dealer's arm pose. */
	static final class PiglinDealerModel extends AdultPiglinModel {
		PiglinDealerModel(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(PiglinRenderState state) {
			super.setupAnim(state);
			if (state instanceof PiglinState s) {
				apply(this, s.pose, s.gesturing);
			}
		}
	}

	static void apply(net.minecraft.client.model.HumanoidModel<?> m, DealerMotion.Pose p, boolean gesturing) {
		m.rightArm.xRot = p.rightX();
		m.rightArm.yRot = p.rightY();
		m.rightArm.zRot = p.rightZ();
		m.leftArm.xRot = p.leftX();
		m.leftArm.yRot = p.leftY();
		m.leftArm.zRot = p.leftZ();
		if (gesturing) {
			m.head.xRot += p.headX();
		}
	}

	static final class PiglinDealerRenderer extends HumanoidMobRenderer<HouseDealer, PiglinRenderState, PiglinModel> {
		PiglinDealerRenderer(EntityRendererProvider.Context context) {
			super(context, new PiglinDealerModel(context.bakeLayer(ModelLayers.PIGLIN)), new PiglinDealerModel(context.bakeLayer(ModelLayers.PIGLIN)),
				0.5F, PiglinRenderer.PIGLIN_CUSTOM_HEAD_TRANSFORMS);
		}

		@Override
		public PiglinRenderState createRenderState() {
			return new PiglinState();
		}

		@Override
		public Identifier getTextureLocation(PiglinRenderState state) {
			return pick(PIGLIN, state instanceof PiglinState s ? s.look : 0);
		}

		@Override
		public void extractRenderState(HouseDealer entity, PiglinRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.armPose = PiglinArmPose.DEFAULT;
			state.isBrute = false;
			if (state instanceof PiglinState s) {
				boolean[] g = new boolean[1];
				s.pose = pose(entity, partialTicks, g);
				s.gesturing = g[0];
				s.look = entity.look();
			}
			stick(entity, state, itemModelResolver);
		}
	}

	// ---- ender -------------------------------------------------------------------------------------------------

	static final class EnderState extends EndermanRenderState {
		DealerMotion.Pose pose = DealerMotion.Pose.REST;
		boolean gesturing;
		int look;
	}

	/** The enderman with the dealer's arm pose at 0.7× amplitude, tilted so its hands rest on the felt. */
	static final class EnderDealerModel extends EndermanModel<EndermanRenderState> {
		EnderDealerModel(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(EndermanRenderState state) {
			super.setupAnim(state);
			if (state instanceof EnderState s) {
				apply(this, DealerMotion.ender(s.pose), s.gesturing);
			}
		}
	}

	static final class EnderDealerRenderer extends MobRenderer<HouseDealer, EndermanRenderState, EndermanModel<EndermanRenderState>> {
		/** Render scale: an enderman is 2.9 tall, the dealer 2.6. */
		static final float SCALE = 0.9f;

		EnderDealerRenderer(EntityRendererProvider.Context context) {
			super(context, new EnderDealerModel(context.bakeLayer(ModelLayers.ENDERMAN)), 0.5F * SCALE);
			addLayer(new EnderEyesLayer(this));
			addLayer(new ItemInHandLayer<>(this));
		}

		@Override
		public EndermanRenderState createRenderState() {
			return new EnderState();
		}

		@Override
		public Identifier getTextureLocation(EndermanRenderState state) {
			return pick(ENDER, state instanceof EnderState s ? s.look : 0);
		}

		@Override
		protected void scale(EndermanRenderState state, PoseStack poseStack) {
			poseStack.scale(SCALE, SCALE, SCALE);
		}

		@Override
		public void extractRenderState(HouseDealer entity, EndermanRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			net.minecraft.client.renderer.entity.HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, itemModelResolver);
			state.isCreepy = false;
			if (state instanceof EnderState s) {
				boolean[] g = new boolean[1];
				s.pose = pose(entity, partialTicks, g);
				s.gesturing = g[0];
				s.look = entity.look();
			}
			stick(entity, state, itemModelResolver);
		}
	}
}
