package dev.nezo.burmaldaholic.games.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.render.SpectatorBlockEntityRenderer;
import dev.nezo.burmaldaholic.client.render.SpectatorRenderState;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoSync;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The in-world Plinko machine (extras-pvp.md §5.4): a 2 × 2 texel ball travels over the machine's front face along the
 * server's path (the SAME {@link PlinkoAnim} motion as the player's screen, from the machine's {@link PlinkoSync} sent
 * once per drop), scaled onto the face's 12 × 12 texel peg area; a strip of 13 lamps over the bins lights the landed
 * bin at the landing (blinking twice at 2 Hz, then steady for 3 s) and chases once every 20 s when idle.
 *
 * <p>LOD (culled by distance): within {@link #RADIUS} blocks the ball moves, the pegs click positionally (within 12
 * blocks, not for the player at the screen) and the lamps chase; up to {@link #VIEW} blocks only the landed lamp after
 * the landing — no ball, so a far viewer never sees the bin before the ball arrives; beyond, nothing. Reduce motion: the
 * ball appears in the bin after the dotted-path time. Players without the mod see the static block plus the server's
 * vanilla particles and sounds at the landing.
 */
public final class PlinkoMachineRenderer extends SpectatorBlockEntityRenderer<PlinkoBlockEntity, PlinkoMachineRenderer.State> {
	public static final int RADIUS = 16;
	public static final int VIEW = 48;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final double AUDIO_RANGE_SQ = 12 * 12;
	/** Attract chase: every 20 s the lamps light once left to right (80 ms each). */
	private static final int ATTRACT_MS = 20_000;
	private static final int CHASE_STEP_MS = 80;
	private static final float T = 1f / 16f;
	private static final float FACE_Z = 0.5f + 0.002f;

	private static final Identifier LAMP = Burmaldaholic.id("textures/entity/extras/plinko_lamp.png");
	private static final Identifier BALL = Burmaldaholic.id("textures/entity/extras/plinko_ball.png");

	private static final Map<PlinkoBlockEntity, Client> CACHE = new WeakHashMap<>();

	public PlinkoMachineRenderer(BlockEntityRendererProvider.Context ctx) {}

	@SuppressWarnings("deprecation") // the only public 26.x registration path (vanilla BlockEntityRenderers.register is private)
	public static void register() {
		BlockEntityRendererRegistry.register(ExtrasModule.PLINKO.blockEntityType(), PlinkoMachineRenderer::new);
	}

	private static final class Client {
		@Nullable PlinkoSync sync;
		int lastRow = Integer.MIN_VALUE;
	}

	public static final class State extends SpectatorRenderState {
		Direction facing = Direction.NORTH;
		boolean ball;
		double u;
		double v;
		/** Lamp states per bin: 0 dark, 1 green, 2 gold. */
		final byte[] lamps = new byte[13];
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected int animateRadius() {
		return RADIUS;
	}

	@Override
	public int getViewDistance() {
		return VIEW;
	}

	@Override
	protected void extractAnimated(PlinkoBlockEntity be, State s, float partialTick) {
		BlockState bs = be.getBlockState();
		s.facing = bs.hasProperty(CasinoTableBlock.FACING) ? bs.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		java.util.Arrays.fill(s.lamps, (byte) 0);
		s.ball = false;
		boolean full = s.lod == Lod.FULL;
		PlinkoSync sync = be.plinkoSync();
		boolean busy = false;
		if (sync != null) {
			Client c = CACHE.computeIfAbsent(be, k -> new Client());
			if (c.sync != sync) {
				c.sync = sync;
				c.lastRow = Integer.MIN_VALUE;
			}
			double t = s.levelMs - sync.startTick() * 50.0;
			boolean reduced = FxSettings.reduceMotion();
			busy = t >= 0 && t < sync.landMs() + PlinkoSync.LAMP_MS;
			if (full && t >= 0 && t < sync.landMs()) {
				PlinkoAnim.Ball b = sync.ball(t, reduced);
				if (b != null) {
					s.ball = true;
					s.u = PlinkoSync.faceU(b.x());
					s.v = PlinkoSync.faceV(b.y());
				}
				if (!reduced && s.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) pegs(be, c, sync, t);
			}
			int lit = sync.lampLit(t, FxSettings.flashes());
			if (lit == 1) s.lamps[sync.bin()] = (byte) (sync.edge() || sync.tier() >= 3 ? 2 : 1);
		}
		if (full && !busy) {
			long cycle = (long) (s.levelMs % ATTRACT_MS);
			int k = (int) (cycle / CHASE_STEP_MS);
			if (k < 13) s.lamps[k] = 1;
		}
	}

	private static boolean viewingOwnScreen(PlinkoBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> scr && scr.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	/** One positional click per row contact, the column pitch of the screen (left low, right high). */
	private static void pegs(PlinkoBlockEntity be, Client c, PlinkoSync sync, double t) {
		int row = -1;
		for (int r = 0; r < PlinkoAnim.ROWS; r++) if (t >= PlinkoAnim.contactMs(sync.rowMs(), r)) row = r;
		if (c.lastRow == Integer.MIN_VALUE) {
			c.lastRow = row;
			return;
		}
		if (row <= c.lastRow) return;
		c.lastRow = row;
		Minecraft mc = Minecraft.getInstance();
		float vol = FxSettings.volume();
		if (mc.level == null || vol <= 0 || ExtrasModule.PLINKO_PEG_SOUND == null) return;
		mc.level.playLocalSound(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5, ExtrasModule.PLINKO_PEG_SOUND,
			SoundSource.BLOCKS, 0.25f * vol, PlinkoAnim.pegPitch(sync.path(), row), false);
	}

	// ---- submit -----------------------------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		boolean anyLamp = false;
		for (byte l : s.lamps) anyLamp |= l != 0;
		if (!s.ball && !anyLamp) return;
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0f, 0f, 0f);
		if (anyLamp) out.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(LAMP), (p, vc) -> lamps(p, vc, s));
		if (s.ball) out.submitCustomGeometry(pose, RenderTypes.entityCutout(BALL), (p, vc) -> ball(p, vc, s));
		pose.popPose();
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int light) {
		vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
	}

	/** A face quad over texels (u0, v0)–(u1, v1) of the front face, textured (tu0, tv0)–(tu1, tv1). */
	private static void faceQuad(PoseStack.Pose p, VertexConsumer vc, float u0, float v0, float u1, float v1, float z, float tu0, float tv0, float tu1,
			float tv1, int light) {
		float x0 = -0.5f + u0 * T;
		float x1 = -0.5f + u1 * T;
		float y0 = 1f - v1 * T;
		float y1 = 1f - v0 * T;
		vertex(p, vc, x0, y0, z, tu0, tv1, light);
		vertex(p, vc, x1, y0, z, tu1, tv1, light);
		vertex(p, vc, x1, y1, z, tu1, tv0, light);
		vertex(p, vc, x0, y1, z, tu0, tv0, light);
	}

	private static void lamps(PoseStack.Pose p, VertexConsumer vc, State s) {
		for (int bin = 0; bin < 13; bin++) {
			int l = s.lamps[bin];
			if (l == 0) continue;
			float cu = (float) PlinkoSync.lampU(bin);
			float tu0 = l == 2 ? 8f / 12f : 4f / 12f;
			faceQuad(p, vc, cu - 0.5f, 13f, cu + 0.5f, 14.6f, FACE_Z, tu0, 0, tu0 + 4f / 12f, 1, FULL_BRIGHT);
		}
	}

	private static void ball(PoseStack.Pose p, VertexConsumer vc, State s) {
		float u = (float) s.u;
		float v = (float) s.v;
		faceQuad(p, vc, u - 1f, v - 1f, u + 1f, v + 1f, FACE_Z + 0.001f, 0, 0, 0.5f, 1, FULL_BRIGHT);
	}
}
