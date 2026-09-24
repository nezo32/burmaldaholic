package dev.nezo.burmaldaholic.games.roulette.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.render.SpectatorBlockEntityRenderer;
import dev.nezo.burmaldaholic.client.render.SpectatorRenderState;
import dev.nezo.burmaldaholic.client.table.fx.WorldQuads;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBallPath;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSync;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The roulette wheel ON the table in the world (animation/tables.md §1.5; visual/tables.md §7): a static bowl and a
 * head that turns continuously in 3D, the ball on the SAME {@link RouletteBallPath} as the screens (synced
 * {@code spin_start / result / seed} from the block entity update tag, sampled at the shared clock), so everybody
 * nearby watches the same ball land in the drawn pocket. Idle: the wheel stands still with the ball in the last
 * pocket (a turning wheel would imply a spin, global §4.13). After a spin: the result badge (number on its pocket
 * colour) and the bobbing dolly for 3 s. Positional ball sounds for spectators (not for the player at the screen).
 * LOD: tweened within 32 blocks; up to 48 only the head (no ball); beyond nothing animates.
 */
public final class RouletteTableRenderer extends SpectatorBlockEntityRenderer<RouletteTableBlockEntity, RouletteTableRenderer.State> {
	private static final int RADIUS = 32;
	private static final float TOP = 14 / 16f + 0.005f;
	private static final float BOWL_HALF = 7 / 16f;
	/** Head quad half size: the bowl's inner gap (21 of 32 texels). */
	private static final float HEAD_HALF = BOWL_HALF * 21f / 32f;
	/** Ball radii: the pocket ring middle and the track middle, in blocks. */
	private static final float R_POCKET = HEAD_HALF * 22.75f / 32f;
	private static final float R_TRACK = BOWL_HALF * 25f / 32f;
	private static final double AUDIO_RANGE_SQ = 24 * 24;
	private static final Map<RouletteTableBlockEntity, Client> CACHE = new WeakHashMap<>();

	private final Font font;

	public RouletteTableRenderer(BlockEntityRendererProvider.Context ctx) {
		this.font = ctx.font();
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path (vanilla BlockEntityRenderers.register is private)
	public static void register() {
		BlockEntityRendererRegistry.register(RouletteModule.TABLE.blockEntityType(), RouletteTableRenderer::new);
		BlockEntityRendererRegistry.register(RouletteModule.HIGH_ROLLER_TABLE.blockEntityType(), RouletteTableRenderer::new);
	}

	/** Per block entity client data, rebuilt only when the sync changes. */
	private static final class Client {
		RouletteSync sync;
		@Nullable RouletteBallPath path;
		double lastT = Double.NaN;
		boolean catchUp;
		int cue;
		@Nullable FormattedCharSequence badge;
		int badgeFor = -2;
	}

	public static final class State extends SpectatorRenderState {
		boolean active;
		int theme;
		Direction facing = Direction.NORTH;
		double head;
		boolean ball;
		double ballAngle;
		float ballR;
		float ballY;
		float badgeAlpha;
		int badgeColor;
		@Nullable FormattedCharSequence badge;
		float dollyBob;
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
		return 48;
	}

	private static final Identifier[] TEX = {tex("village"), tex("bastion"), tex("end")};

	private static Identifier tex(String theme) {
		return Burmaldaholic.id("textures/entity/roulette/wheel_" + theme + ".png");
	}

	@Override
	protected void extractAnimated(RouletteTableBlockEntity be, State state, float partialTick) {
		RouletteSync sync = be.clientSync();
		state.active = sync != null && state.lod != Lod.STATIC;
		if (!state.active) {
			return;
		}
		Client c = CACHE.computeIfAbsent(be, k -> new Client());
		if (c.sync == null || c.sync.seq() != sync.seq() || c.sync.phase() != sync.phase()) {
			boolean newSpin = c.sync == null || c.sync.seq() != sync.seq();
			c.sync = sync;
			if (sync.result() >= 0 && (newSpin || c.path == null)) {
				c.path = RouletteBallPath.of(sync.result(), sync.seed(), Math.max(1, sync.spinTicks()) * 50);
				c.lastT = Double.NaN;
				c.cue = 0;
				double t0 = state.levelMs - sync.spinStart() * 50.0;
				c.catchUp = t0 > 0 && TimelineSeed.showSettled(t0, c.path.spinMs);
			} else if (sync.result() < 0) {
				c.path = null;
			}
		}
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(CasinoTableBlock.FACING) ? bs.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		state.theme = Math.max(0, Math.min(2, sync.theme()));
		boolean reduced = FxSettings.reduceMotion();
		int last = sync.lastResult();
		double base = RouletteWheelView.baseAngle(last);
		RouletteBallPath p = c.path;
		boolean spinning = sync.phase() == RouletteSync.SPIN || sync.phase() == RouletteSync.RESULT;
		if (p != null && spinning) {
			double t = state.levelMs - sync.spinStart() * 50.0;
			if (c.catchUp || state.lod != Lod.FULL) {
				t = Math.max(t, p.spinMs);
			}
			state.head = RouletteWheelView.headAngle(p, t, base, reduced);
			state.ball = state.lod == Lod.FULL && (!reduced || p.settled(t));
			state.ballAngle = state.head + p.rel(t);
			float r = (float) p.radius(t);
			float k = (float) Math.max(0, Math.min(1, (r - RouletteBallPath.R_POCKET) / (RouletteBallPath.R_ORBIT - RouletteBallPath.R_POCKET)));
			state.ballR = R_POCKET + (R_TRACK - R_POCKET) * k;
			state.ballY = TOP + 0.02f + (r > RouletteBallPath.R_POCKET_RIM && r < RouletteBallPath.R_ORBIT_END - 0.05 ? 0.01f : 0f);
			if (state.lod == Lod.FULL && !c.catchUp && state.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) {
				cues(be, c, p, t);
			}
			c.lastT = t;
			// the result badge for 3 s after the spin (RESULT)
			double since = t - p.spinMs;
			if (sync.phase() == RouletteSync.RESULT && since >= 0 && since < 3000) {
				if (c.badgeFor != p.result) {
					c.badgeFor = p.result;
					c.badge = Texts.number(p.result).getVisualOrderText();
				}
				state.badge = c.badge;
				state.badgeAlpha = (float) (reduced ? 1 : Math.min(1, Ease.OUT_BACK.apply(Math.min(1, since / 250.0))));
				state.badgeColor = RouletteWheelView.pocketColor(p.result);
				state.dollyBob = reduced ? 0 : (float) (0.03 * Math.sin(state.levelMs / 1000.0 * Math.PI * 2));
			} else {
				state.badge = null;
			}
		} else {
			// idle / no more bets: the wheel stands still, the ball rests in the last pocket
			state.head = base;
			state.ball = last >= 0 && state.lod == Lod.FULL;
			state.ballAngle = base + (last >= 0 ? RouletteBallPath.pocketAngle(last) : 0);
			state.ballR = R_POCKET;
			state.ballY = TOP + 0.02f;
			state.badge = null;
		}
	}

	/** The acting player hears the screen's sounds, not the table's. */
	private static boolean viewingOwnScreen(RouletteTableBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	private static void cues(RouletteTableBlockEntity be, Client c, RouletteBallPath p, double t) {
		if (Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) {
			return;
		}
		var list = p.cues();
		while (c.cue < list.size() && list.get(c.cue).atMs() <= c.lastT) {
			c.cue++;
		}
		double x = be.getBlockPos().getX() + 0.5;
		double y = be.getBlockPos().getY() + 1.0;
		double z = be.getBlockPos().getZ() + 0.5;
		while (c.cue < list.size() && list.get(c.cue).atMs() <= t) {
			var cue = list.get(c.cue++);
			FxSounds.playAt(cue.sound(), x, y, z, 0.8f, cue.pitch());
		}
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		if (!s.active) {
			return;
		}
		Identifier tex = TEX[s.theme];
		int light = s.lightCoords;
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0f, 0f, 0f);
		float head = (float) s.head;
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(tex), (p, vc) -> {
			WorldQuads.topQuad(p, vc, 0, TOP, 0, BOWL_HALF, 0, 64, 0, 64, 64, 128, 80, 0xFFFFFFFF, light);
			WorldQuads.topQuad(p, vc, 0, TOP + 0.004f, 0, HEAD_HALF, head, 0, 0, 64, 64, 128, 80, 0xFFFFFFFF, light);
			// turret cap
			WorldQuads.topQuad(p, vc, 0, TOP + 0.03f, 0, 1.2f / 16f, head, 16, 64, 8, 8, 128, 80, 0xFFFFFFFF, light);
			if (s.ball) {
				double a = Math.toRadians(s.ballAngle);
				float bx = (float) (Math.sin(a) * s.ballR);
				float bz = (float) (-Math.cos(a) * s.ballR);
				WorldQuads.topQuad(p, vc, bx, s.ballY, bz, 0.6f / 16f, 0, 0, 64, 4, 4, 128, 80, 0xFFFFFFFF, light);
			}
		});
		pose.popPose();
		if (s.badge != null && s.badgeAlpha > 0.05f) {
			pose.pushPose();
			pose.translate(0.5f, 1.6f, 0.5f);
			pose.rotateAround(camera.orientation, 0f, 0f, 0f);
			pose.scale(0.025f, -0.025f, 0.025f);
			int a = Math.round(Math.max(0, Math.min(1, s.badgeAlpha)) * 255);
			int bg = (Math.min(a, 0xC0) << 24) | (s.badgeColor & 0xFFFFFF);
			out.submitText(pose, -font.width(s.badge) / 2f, 0, s.badge, false, Font.DisplayMode.NORMAL, WorldQuads.FULL_BRIGHT, (a << 24) | 0xF4ECF8, bg,
				(a << 24) | 0x180A28);
			pose.popPose();
			// the pixel dolly bobbing beside the badge
			pose.pushPose();
			pose.translate(0.5f, 1.55f + s.dollyBob, 0.5f);
			pose.rotateAround(camera.orientation, 0f, 0f, 0f);
			float dx = 0.22f;
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(tex), (p, vc) -> {
				WorldQuads.vertex(p, vc, dx, 0.18f, 0, 8 / 128f, 64 / 80f, 0xFFFFFFFF, WorldQuads.FULL_BRIGHT, 0, 0, 1);
				WorldQuads.vertex(p, vc, dx, 0f, 0, 8 / 128f, 76 / 80f, 0xFFFFFFFF, WorldQuads.FULL_BRIGHT, 0, 0, 1);
				WorldQuads.vertex(p, vc, dx + 0.12f, 0f, 0, 16 / 128f, 76 / 80f, 0xFFFFFFFF, WorldQuads.FULL_BRIGHT, 0, 0, 1);
				WorldQuads.vertex(p, vc, dx + 0.12f, 0.18f, 0, 16 / 128f, 64 / 80f, 0xFFFFFFFF, WorldQuads.FULL_BRIGHT, 0, 0, 1);
			});
			pose.popPose();
		}
	}

	/** Pocket colour name for tests / narration. */
	static String colourOf(int n) {
		return Wheel.color(n).id();
	}
}
