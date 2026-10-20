package dev.nezo.burmaldaholic.worldgen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.logic.NpcLook;
import dev.nezo.burmaldaholic.worldgen.npc.CasinoNpcEntity;
import dev.nezo.burmaldaholic.worldgen.npc.NpcContent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.model.monster.shulker.ShulkerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.client.renderer.entity.state.ShulkerRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;

/**
 * Casino staff on vanilla models with generated skins ({@code textures/entity/worldgen/*.png}): the
 * Croupier on the villager model (tuxedo, bow tie), the Piglin Dealer on the piglin model (crimson vest),
 * the Shulker Croupier on the shulker model (purple shell with gold trim, shell slightly open).
 */
final class NpcRenderers {
	private NpcRenderers() {}

	static Identifier texture(String name) {
		return Burmaldaholic.id("textures/entity/worldgen/" + name + ".png");
	}

	static void register() {
		EntityRenderers.register(NpcContent.CROUPIER, CroupierRenderer::new);
		EntityRenderers.register(NpcContent.PIGLIN_DEALER, DealerRenderer::new);
		EntityRenderers.register(NpcContent.SHULKER_CROUPIER, ShulkerCroupierRenderer::new);
		// DL: new staff, every one wearing a skin of its pool (worldgen/logic/NpcLook)
		EntityRenderers.register(NpcContent.MERCHANT, CroupierRenderer::new);
		EntityRenderers.register(NpcContent.PIGLIN_MERCHANT, DealerRenderer::new);
		EntityRenderers.register(NpcContent.PIT_BOSS, DealerRenderer::new);
		EntityRenderers.register(NpcContent.ENDER_CROUPIER, EnderStaffRenderer::new);
		EntityRenderers.register(NpcContent.ENDER_MERCHANT, EnderStaffRenderer::new);
		HouseDealerRenderers.register();
	}

	static final Identifier[] VILLAGERS = pool("croupier/villager_", NpcLook.VILLAGER);
	static final Identifier PIT_BOSS = texture("dealer/pit_boss");

	private static Identifier[] pool(String prefix, int n) {
		Identifier[] out = new Identifier[n];
		for (int i = 0; i < n; i++) {
			out[i] = texture(prefix + i);
		}
		return out;
	}

	/** Villager-model staff: the croupier and the overworld merchant. */
	static final class StaffVillagerState extends VillagerRenderState {
		int look;
	}

	/** Piglin-model staff: the nether cashier, the nether merchant and the pit boss (a brute). */
	static final class StaffPiglinState extends PiglinRenderState {
		int look;
		boolean pitBoss;
	}

	/** Ender croupier / ender merchant: the enderman in casino dress at 0.9 scale, vanilla eyes, no carried block. */
	static final class EnderStaffRenderer extends MobRenderer<CasinoNpcEntity, EndermanRenderState, EndermanModel<EndermanRenderState>> {
		EnderStaffRenderer(EntityRendererProvider.Context context) {
			super(context, new EndermanModel<>(context.bakeLayer(ModelLayers.ENDERMAN)), 0.45F);
			addLayer(new EnderEyesLayer(this));
		}

		@Override
		public EndermanRenderState createRenderState() {
			return new StaffEnderState();
		}

		@Override
		public Identifier getTextureLocation(EndermanRenderState state) {
			return HouseDealerRenderers.pick(HouseDealerRenderers.ENDER, state instanceof StaffEnderState s ? s.look : 0);
		}

		@Override
		protected void scale(EndermanRenderState state, PoseStack poseStack) {
			poseStack.scale(0.9F, 0.9F, 0.9F);
		}

		@Override
		public void extractRenderState(CasinoNpcEntity entity, EndermanRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, itemModelResolver);
			state.isCreepy = false;
			if (state instanceof StaffEnderState s) {
				s.look = entity.look();
			}
		}
	}

	static final class StaffEnderState extends EndermanRenderState {
		int look;
	}

	static final class CroupierRenderer extends MobRenderer<CasinoNpcEntity, VillagerRenderState, VillagerModel> {
		CroupierRenderer(EntityRendererProvider.Context context) {
			super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
		}

		@Override
		public Identifier getTextureLocation(VillagerRenderState state) {
			return VILLAGERS[Math.floorMod(state instanceof StaffVillagerState s ? s.look : 0, VILLAGERS.length)];
		}

		@Override
		public VillagerRenderState createRenderState() {
			return new StaffVillagerState();
		}

		@Override
		public void extractRenderState(CasinoNpcEntity entity, VillagerRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			if (state instanceof StaffVillagerState s) {
				s.look = entity.look();
			}
		}
	}

	static final class DealerRenderer extends HumanoidMobRenderer<CasinoNpcEntity, PiglinRenderState, PiglinModel> {
		DealerRenderer(EntityRendererProvider.Context context) {
			super(context, new AdultPiglinModel(context.bakeLayer(ModelLayers.PIGLIN)), new AdultPiglinModel(context.bakeLayer(ModelLayers.PIGLIN)),
				0.5F, PiglinRenderer.PIGLIN_CUSTOM_HEAD_TRANSFORMS);
		}

		@Override
		public Identifier getTextureLocation(PiglinRenderState state) {
			if (state instanceof StaffPiglinState s) {
				return s.pitBoss ? PIT_BOSS : HouseDealerRenderers.pick(HouseDealerRenderers.PIGLIN, s.look);
			}
			return HouseDealerRenderers.PIGLIN[0];
		}

		@Override
		public PiglinRenderState createRenderState() {
			return new StaffPiglinState();
		}

		@Override
		public void extractRenderState(CasinoNpcEntity entity, PiglinRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.armPose = PiglinArmPose.DEFAULT;
			boolean pitBoss = entity.getType() == NpcContent.PIT_BOSS;
			state.isBrute = pitBoss;
			if (state instanceof StaffPiglinState s) {
				s.look = entity.look();
				s.pitBoss = pitBoss;
			}
		}
	}

	static final class ShulkerCroupierRenderer extends MobRenderer<CasinoNpcEntity, ShulkerRenderState, ShulkerModel> {
		private static final Identifier TEXTURE = texture("shulker_croupier");
		/** How far the shell stays open (0 closed … 1 fully open). */
		private static final float PEEK = 0.35F;

		ShulkerCroupierRenderer(EntityRendererProvider.Context context) {
			super(context, new ShulkerModel(context.bakeLayer(ModelLayers.SHULKER)), 0.0F);
		}

		@Override
		public Identifier getTextureLocation(ShulkerRenderState state) {
			return TEXTURE;
		}

		@Override
		public ShulkerRenderState createRenderState() {
			return new ShulkerRenderState();
		}

		@Override
		public void extractRenderState(CasinoNpcEntity entity, ShulkerRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.peekAmount = PEEK;
			state.attachFace = Direction.DOWN;
			// ShulkerModel turns the head by (yHeadRot − 180 − yBodyRot); the body is already turned by bodyRot
			state.yHeadRot = state.yRot + 180.0F;
			state.yBodyRot = 0.0F;
		}
	}
}
