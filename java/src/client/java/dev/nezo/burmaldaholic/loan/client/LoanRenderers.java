package dev.nezo.burmaldaholic.loan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.loan.LoanContent;
import dev.nezo.burmaldaholic.loan.entity.LoanSharkEntity;
import dev.nezo.burmaldaholic.loan.entity.SquadMob;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;

/**
 * Vanilla models with the loan module's own skins ({@code textures/entity/loan/*.png}, generated): the
 * Loan Shark on the villager model (fedora on the hat + rim), the Piglin Moneylender on the piglin
 * model, and the four squad units on the illager model (the Enforcer is bigger through its SCALE attribute).
 */
final class LoanRenderers {
	private LoanRenderers() {}

	static Identifier texture(String name) {
		return Burmaldaholic.id("textures/entity/loan/" + name + ".png");
	}

	static void register() {
		EntityRenderers.register(LoanContent.LOAN_SHARK, SharkRenderer::new);
		EntityRenderers.register(LoanContent.PIGLIN_MONEYLENDER, PiglinLenderRenderer::new);
		EntityRenderers.register(LoanContent.DEBT_COLLECTOR, c -> new SquadRenderer<>(c, "debt_collector"));
		EntityRenderers.register(LoanContent.REPO_MAN, c -> new SquadRenderer<>(c, "repo_man"));
		EntityRenderers.register(LoanContent.ACCOUNTANT, c -> new SquadRenderer<>(c, "accountant"));
		EntityRenderers.register(LoanContent.ENFORCER, c -> new SquadRenderer<>(c, "enforcer"));
	}

	static final class SharkRenderer extends MobRenderer<LoanSharkEntity, VillagerRenderState, VillagerModel> {
		private static final Identifier TEXTURE = texture("loan_shark");

		SharkRenderer(EntityRendererProvider.Context context) {
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

	static final class PiglinLenderRenderer extends HumanoidMobRenderer<LoanSharkEntity, PiglinRenderState, PiglinModel> {
		private static final Identifier TEXTURE = texture("piglin_moneylender");

		PiglinLenderRenderer(EntityRendererProvider.Context context) {
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
		public void extractRenderState(LoanSharkEntity entity, PiglinRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.armPose = PiglinArmPose.DEFAULT;
			state.isBrute = false;
		}
	}

	static final class SquadRenderer<T extends SquadMob> extends IllagerRenderer<T, IllagerRenderState> {
		private final Identifier texture;

		SquadRenderer(EntityRendererProvider.Context context, String name) {
			super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.VINDICATOR)), 0.5F);
			this.texture = texture(name);
			this.model.getHat().visible = true; // caps / visors are painted on the hat layer
			this.addLayer(new ItemInHandLayer<IllagerRenderState, IllagerModel<IllagerRenderState>>(this) {
				@Override
				public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, IllagerRenderState state, float yRot, float xRot) {
					if (state.armPose != AbstractIllager.IllagerArmPose.CROSSED) { // no floating weapon with crossed arms
						super.submit(poseStack, collector, light, state, yRot, xRot);
					}
				}
			});
		}

		@Override
		public Identifier getTextureLocation(IllagerRenderState state) {
			return texture;
		}

		@Override
		public IllagerRenderState createRenderState() {
			return new IllagerRenderState();
		}
	}
}
