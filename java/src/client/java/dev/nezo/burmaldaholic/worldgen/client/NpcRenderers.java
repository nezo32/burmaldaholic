package dev.nezo.burmaldaholic.worldgen.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.npc.CasinoNpcEntity;
import dev.nezo.burmaldaholic.worldgen.npc.NpcContent;
import net.minecraft.client.model.geom.ModelLayers;
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
	}

	static final class CroupierRenderer extends MobRenderer<CasinoNpcEntity, VillagerRenderState, VillagerModel> {
		private static final Identifier TEXTURE = texture("croupier");

		CroupierRenderer(EntityRendererProvider.Context context) {
			super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
		}

		@Override
		public Identifier getTextureLocation(VillagerRenderState state) {
			return TEXTURE;
		}

		@Override
		public VillagerRenderState createRenderState() {
			return new VillagerRenderState();
		}
	}

	static final class DealerRenderer extends HumanoidMobRenderer<CasinoNpcEntity, PiglinRenderState, PiglinModel> {
		private static final Identifier TEXTURE = texture("piglin_dealer");

		DealerRenderer(EntityRendererProvider.Context context) {
			super(context, new AdultPiglinModel(context.bakeLayer(ModelLayers.PIGLIN)), new AdultPiglinModel(context.bakeLayer(ModelLayers.PIGLIN)),
				0.5F, PiglinRenderer.PIGLIN_CUSTOM_HEAD_TRANSFORMS);
		}

		@Override
		public Identifier getTextureLocation(PiglinRenderState state) {
			return TEXTURE;
		}

		@Override
		public PiglinRenderState createRenderState() {
			return new PiglinRenderState();
		}

		@Override
		public void extractRenderState(CasinoNpcEntity entity, PiglinRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.armPose = PiglinArmPose.DEFAULT;
			state.isBrute = false;
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
