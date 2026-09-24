package dev.nezo.burmaldaholic.games.craps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.render.SpectatorBlockEntityRenderer;
import dev.nezo.burmaldaholic.client.render.SpectatorRenderState;
import dev.nezo.burmaldaholic.client.table.fx.WorldQuads;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.dice.DiceFaces;
import dev.nezo.burmaldaholic.core.anim.dice.DiceThrowPath;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsFelt;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsSync;
import dev.nezo.burmaldaholic.games.craps.logic.RollEvent;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Craps in the world (animation/tables.md §2.6; visual/tables.md §7): two small dice follow the SAME
 * {@link DiceThrowPath} as the screens, mapped from the felt onto the table top (the far rail opposite the shooter is
 * the back wall), land on the real faces (face-to-rotation table {@link DiceFaces}), rest 1.4 s and slide back to the
 * shooter's edge. The puck sits OFF in a corner or ON on its point spot and flips and flies with the screen's timing;
 * the total floats above the table for 2 s. Positional sounds for spectators. LOD: tweened within 24 blocks, settled
 * up to 32.
 */
public final class CrapsTableRenderer extends SpectatorBlockEntityRenderer<CrapsTableBlockEntity, CrapsTableRenderer.State> {
	private static final Identifier TEX = Burmaldaholic.id("textures/entity/craps/dice.png");
	private static final float TOP = 1.0f + 0.003f;
	/** Felt → table top: the 386 × 162 layout maps onto 14 × 10 px of the top; height: 1 block = 160 px. */
	private static final float SX = 14f / 16f / 386f;
	private static final float SZ = 10f / 16f / 162f;
	private static final float DIE = 1.5f / 16f;
	private static final double AUDIO_RANGE_SQ = 16 * 16;
	private static final Map<CrapsTableBlockEntity, Client> CACHE = new WeakHashMap<>();

	private final Font font;

	public CrapsTableRenderer(BlockEntityRendererProvider.Context ctx) {
		this.font = ctx.font();
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(CrapsModule.TABLE.blockEntityType(), CrapsTableRenderer::new);
	}

	private static final class Client {
		CrapsSync sync;
		@Nullable DiceThrowPath path;
		double lastT = Double.NaN;
		int cue;
		boolean catchUp;
		@Nullable FormattedCharSequence total;
	}

	public static final class State extends SpectatorRenderState {
		boolean active;
		int dir;
		boolean dice;
		final float[] dx = new float[2];
		final float[] dz = new float[2];
		final float[] dy = new float[2];
		final float[] spin = new float[2];
		final float[] tilt = new float[2];
		final int[] face = new int[2];
		float puckX;
		float puckZ;
		float puckY;
		boolean puckOn;
		@Nullable FormattedCharSequence total;
		float totalAlpha;
		int totalColor;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected int animateRadius() {
		return 24;
	}

	@Override
	public int getViewDistance() {
		return 32;
	}

	/** Felt pixel → table-local x (shooter side = +z before the direction rotation). */
	private static float lx(double x) {
		return (float) ((x - 193) * SX);
	}

	private static float lz(double y) {
		return (float) ((y - 81) * SZ);
	}

	private final DiceThrowPath.Sample sample = new DiceThrowPath.Sample();
	private final CrapsBeats.Puck puck = new CrapsBeats.Puck();

	@Override
	protected void extractAnimated(CrapsTableBlockEntity be, State state, float partialTick) {
		CrapsSync sync = be.clientSync();
		state.active = sync != null && state.lod != Lod.STATIC;
		if (!state.active) {
			return;
		}
		Client c = CACHE.computeIfAbsent(be, k -> new Client());
		CrapsFelt f = CrapsFelt.BIG;
		if (c.sync == null || c.sync.rolls() != sync.rolls()) {
			c.sync = sync;
			c.path = null;
			c.total = null;
			if (sync.d1() > 0 && sync.d2() > 0) {
				double[] r = f.restRegion();
				double[] st = f.throwStart();
				c.path = DiceThrowPath.of(sync.seed(), sync.d1(), sync.d2(),
					DiceThrowPath.Params.craps(st[0], st[1], f.wall(), f.dcW() + 2, f.w(), r[0], r[1], r[2], r[3]));
				c.total = Texts.number(sync.d1() + sync.d2()).getVisualOrderText();
				double t0 = state.levelMs - sync.rollTime() * 50.0;
				c.catchUp = t0 > 0.85 * CrapsBeats.IDLE;
				c.lastT = Double.NaN;
				c.cue = 0;
			}
		}
		state.dir = sync.shooterDir();
		double t = state.levelMs - sync.rollTime() * 50.0;
		if (c.catchUp || state.lod != Lod.FULL) {
			t = Math.max(t, 1_000_000);
		}
		boolean reduced = FxSettings.reduceMotion();
		DiceThrowPath p = c.path;
		state.dice = p != null;
		if (p != null) {
			// the dice rest 1.4 s, slide back to the shooter's edge (300 ms) and sit there
			double back = Math.max(0, Math.min(1, (t - p.durationMs() - 1400) / 300.0));
			double[] edge = f.throwStart();
			for (int d = 0; d < 2; d++) {
				p.sample(d, reduced ? p.durationMs() : t, sample);
				double x = sample.x;
				double y = sample.y;
				if (back > 0) {
					double e = Ease.IN_OUT_CUBIC.apply(back);
					x += (edge[0] - 12 + d * 10 - x) * e;
					y += (edge[1] - 4 - y) * e;
				}
				state.dx[d] = lx(x);
				state.dz[d] = lz(y);
				state.dy[d] = (float) (sample.z / 160.0);
				boolean rest = sample.frame < 0;
				state.spin[d] = (float) (rest ? Math.round(sample.theta / 90.0) * 90.0 : sample.theta);
				state.tilt[d] = rest ? 0f : (float) (sample.theta * 1.7);
				state.face[d] = sample.face;
			}
			if (!c.catchUp && state.lod == Lod.FULL && state.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) {
				cues(be, c, p, t);
			}
			c.lastT = t;
		}
		// the puck
		double pt = reduced ? (t >= CrapsBeats.PUCK ? CrapsBeats.PUCK_TOTAL : 0) : t - CrapsBeats.PUCK;
		CrapsBeats.puck(f.puck(sync.pointBefore()), sync.pointBefore(), f.puck(sync.point()), sync.point(), Math.max(0, pt), puck);
		state.puckX = lx(puck.x + 9);
		state.puckZ = lz(puck.y + 9);
		state.puckY = (float) (puck.lift / 160.0);
		state.puckOn = puck.frame >= 3;
		// the total above the table for 2 s after the dice rest
		double since = t - CrapsBeats.BADGE;
		if (c.total != null && since >= 0 && since < 2000 && !c.catchUp) {
			state.total = c.total;
			state.totalAlpha = (float) Math.min(1, since / 200.0);
			RollEvent.Kind[] kinds = RollEvent.Kind.values();
			RollEvent.Kind k = sync.event() >= 0 && sync.event() < kinds.length ? kinds[sync.event()] : RollEvent.Kind.ROLL;
			state.totalColor = switch (k) {
				case NATURAL, POINT_MADE -> 0x2E9A48;
				case CRAPS, SEVEN_OUT -> 0xD83440;
				case POINT_SET -> 0x783CBE;
				default -> 0x26202C;
			};
		} else {
			state.total = null;
		}
	}

	private static boolean viewingOwnScreen(CrapsTableBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	private static void cues(CrapsTableBlockEntity be, Client c, DiceThrowPath p, double t) {
		if (Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) {
			return;
		}
		var list = p.cues();
		while (c.cue < list.size() && list.get(c.cue).atMs() <= c.lastT) {
			c.cue++;
		}
		while (c.cue < list.size() && list.get(c.cue).atMs() <= t) {
			var cue = list.get(c.cue++);
			FxSounds.playAt(cue.sound(), be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1.0, be.getBlockPos().getZ() + 0.5, 0.7f, cue.pitch());
		}
	}

	private static int[][] faceUv(int top) {
		int[] faces = DiceFaces.layout(top);
		int[][] uv = new int[6][];
		for (int i = 0; i < 6; i++) {
			uv[i] = new int[] {(faces[i] - 1) * 8, 0, 8};
		}
		return uv;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		if (!s.active) {
			return;
		}
		int light = s.lightCoords;
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		// the shooter's side is +z of the felt frame; turn it toward the shooter (0 south, 1 west, 2 north, 3 east)
		pose.rotateAround(Axis.YP.rotationDegrees(-90f * s.dir), 0f, 0f, 0f);
		// the back wall: a raised pyramid-rubber strip on the far edge
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEX), (p, vc) -> {
			WorldQuads.topRect(p, vc, -7 / 16f, -5.8f / 16f, 7 / 16f, -5 / 16f, TOP + 0.02f, 0, 8, 16, 4, 64, 16, 0xFFFFFFFF, light);
			WorldQuads.topQuad(p, vc, s.puckX, TOP + 0.002f + s.puckY, s.puckZ, 0.8f / 16f, 0, s.puckOn ? 56 : 48, 0, 8, 8, 64, 16, 0xFFFFFFFF, light);
		});
		if (s.dice) {
			for (int d = 0; d < 2; d++) {
				int die = d;
				pose.pushPose();
				pose.translate(s.dx[d], TOP + DIE + s.dy[d], s.dz[d]);
				pose.rotateAround(Axis.YP.rotationDegrees(s.spin[d]), 0f, 0f, 0f);
				if (s.tilt[d] != 0) {
					pose.rotateAround(Axis.XP.rotationDegrees(s.tilt[d]), 0f, 0f, 0f);
				}
				out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEX), (p, vc) -> WorldQuads.cube(p, vc, DIE, faceUv(s.face[die]), 64, 16, light));
				pose.popPose();
			}
		}
		pose.popPose();
		if (s.total != null && s.totalAlpha > 0.05f) {
			pose.pushPose();
			pose.translate(0.5f, 1.5f, 0.5f);
			pose.rotateAround(camera.orientation, 0f, 0f, 0f);
			pose.scale(0.025f, -0.025f, 0.025f);
			int a = Math.round(s.totalAlpha * 255);
			out.submitText(pose, -font.width(s.total) / 2f, 0, s.total, false, Font.DisplayMode.NORMAL, WorldQuads.FULL_BRIGHT, (a << 24) | 0xFFD640,
				(Math.min(a, 0xC0) << 24) | s.totalColor, (a << 24) | 0x180A28);
			pose.popPose();
		}
	}
}
