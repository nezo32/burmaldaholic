package dev.nezo.burmaldaholic.client.table;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * MT-3: the localized word on the sign board of a full (3 × 2) cashier booth — "Cashier" / "Касса" in gold, full
 * bright (the board itself is a baked model; no word is ever baked into a texture). Compact v0.1 cashiers draw nothing.
 */
public final class CashierSignRenderer implements BlockEntityRenderer<CashierBlockEntity, CashierSignRenderer.State> {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final double RANGE_SQ = 48 * 48;
	/** Sign board centre in footprint-local coordinates (machines.mjs: x −8..24 px, y 29.25..31.75 px, front z 14.5 px). */
	private static final double SIGN_X = 0.5;
	private static final double SIGN_Y = 30.5 / 16;
	private static final double SIGN_Z = 14.5 / 16 + 0.004;
	private static final double MAX_W = 1.8;
	private static final double MAX_H = 2.0 / 16;

	public static final class State extends BlockEntityRenderState {
		boolean show;
		Direction facing = Direction.NORTH;
		@Nullable FormattedCharSequence text;
	}

	private final Font font;

	public CashierSignRenderer(BlockEntityRendererProvider.Context ctx) {
		this.font = ctx.font();
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path (vanilla BlockEntityRenderers.register is private)
	public static void register() {
		BlockEntityRendererRegistry.register(CoreContent.CASHIER.blockEntityType(), CashierSignRenderer::new);
		BlockEntityRendererRegistry.register(CoreContent.NETHER_CASHIER.blockEntityType(), CashierSignRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CashierBlockEntity be, State state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(be, state, crumbling);
		BlockState bs = be.getBlockState();
		state.show = TableParts.partOf(bs) == TablePart.MAIN && cameraPos.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) <= RANGE_SQ;
		state.facing = bs.hasProperty(CasinoTableBlock.FACING) ? bs.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		state.text = state.show ? Component.translatable("block.burmaldaholic.cashier").getVisualOrderText() : null;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		if (!s.show || s.text == null) {
			return;
		}
		int w = Math.max(1, font.width(s.text));
		float scale = (float) Math.min(MAX_H / 7.0, MAX_W / w);
		double[] at = TableFootprint.toWorld(SIGN_X, SIGN_Z, s.facing.getStepX(), s.facing.getStepZ());
		pose.pushPose();
		pose.translate((float) at[0], (float) SIGN_Y, (float) at[1]);
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0f, 0f, 0f);
		pose.scale(scale, -scale, scale);
		out.submitText(pose, -w / 2f, -3.5f, s.text, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, 0xFFFFD640, 0, 0xFF3A2208);
		pose.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
