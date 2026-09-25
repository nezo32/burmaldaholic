package dev.nezo.burmaldaholic.games.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.render.SpectatorBlockEntityRenderer;
import dev.nezo.burmaldaholic.client.render.SpectatorRenderState;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import dev.nezo.burmaldaholic.games.extras.logic.anim.WheelAnim;
import dev.nezo.burmaldaholic.games.extras.logic.anim.WheelSync;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The in-world Wheel of Fortune (extras-pvp.md §3.4): the machine's wheel standing on its block — the rotating face
 * (the generated {@code wheel_face.png} for the Appendix B list, flat wedges for a configured list), the lacquer rim
 * with 24 bulbs, the hub, the leather flapper and a post — turning on the SAME {@link WheelAnim} curve as the player's
 * screen from the machine's {@link WheelSync} (sent once per spin), so spectators watch the spin and the stop on the
 * drawn segment.
 *
 * <p>LOD (culled by distance): within {@link #RADIUS} blocks the spin animates, bulbs chase and the flapper ticks
 * (positional ticks within 16 blocks unless the viewer is the player at the screen); up to {@link #VIEW} blocks the
 * wheel is drawn at rest — the previous segment until the spin has ended, then the new one (never the outcome ahead
 * of the stop); beyond, nothing. Reduce motion: the straight 600 ms turn. Players without the mod see the static block
 * plus the server's vanilla stop sound and particles.
 */
public final class WheelOfFortuneRenderer extends SpectatorBlockEntityRenderer<WheelBlockEntity, WheelOfFortuneRenderer.State> {
	/** Full animation radius (blocks). */
	public static final int RADIUS = 16;
	/** Draw distance (blocks). */
	public static final int VIEW = 48;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final double AUDIO_RANGE_SQ = 16 * 16;
	private static final int MAX_TICKS_PER_SECOND = 12;

	// ---- geometry (block-local: front at +z, the block's bottom at y 0; 1 face px = R / 80) ----
	private static final float R = 0.8f;
	private static final float PX = R / 80f;
	private static final float CY = 1.0f + 92 * PX + 0.06f;
	private static final float FACE_Z = 0.06f;

	private static final Identifier FACE = tex("gui/extras/wheel_face");
	private static final Identifier RIM = tex("gui/extras/wheel_rim");
	private static final Identifier HUB = tex("gui/sprites/burmaldaholic/extras/wheel_hub");
	private static final Identifier FLAPPER = tex("gui/sprites/burmaldaholic/extras/wheel_flapper");
	private static final Identifier BULBS = tex("entity/extras/wheel_bulbs");
	private static final Identifier WHITE_TEX = Identifier.withDefaultNamespace("textures/block/snow.png");
	private static final float WU = 0.5f / 16f;
	private static final float WV = 1.5f / 16f;

	private static final Map<WheelBlockEntity, Client> CACHE = new WeakHashMap<>();

	private static Identifier tex(String path) {
		return Burmaldaholic.id("textures/" + path + ".png");
	}

	public WheelOfFortuneRenderer(BlockEntityRendererProvider.Context ctx) {}

	/** Registers the renderer (client module hook). */
	@SuppressWarnings("deprecation") // the only public 26.x registration path (vanilla BlockEntityRenderers.register is private)
	public static void register() {
		BlockEntityRendererRegistry.register(ExtrasModule.WHEEL.blockEntityType(), WheelOfFortuneRenderer::new);
	}

	/** Per block entity client data (tick sound bookkeeping). */
	private static final class Client {
		@Nullable WheelSync sync;
		long lastPeg = Long.MIN_VALUE;
		double lastTickMs = -1e9;
		double lastT = Double.NaN;
	}

	public static final class State extends SpectatorRenderState {
		Direction facing = Direction.NORTH;
		double angle;
		double flapper;
		String segments = String.join("", Wheel.DEFAULT_SEGMENTS);
		boolean defaultFace = true;
		/** Bulb states: 0 off, 1 on. */
		final byte[] bulbs = new byte[24];
		/** Stop beat: the landed segment (−1 none); every other wedge is veiled. */
		int landed = -1;
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
	public boolean shouldRenderOffScreen() {
		return true; // the wheel stands above its block
	}

	@Override
	protected void extractAnimated(WheelBlockEntity be, State s, float partialTick) {
		BlockState bs = be.getBlockState();
		s.facing = bs.hasProperty(CasinoTableBlock.FACING) ? bs.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		WheelSync sync = be.wheelSync();
		java.util.Arrays.fill(s.bulbs, (byte) 0);
		s.landed = -1;
		s.flapper = 0;
		boolean full = s.lod == Lod.FULL;
		if (sync == null) {
			s.angle = 0;
			s.segments = String.join("", Wheel.DEFAULT_SEGMENTS);
			s.defaultFace = true;
			idleBulbs(s, full);
			return;
		}
		Client c = CACHE.computeIfAbsent(be, k -> new Client());
		if (c.sync != sync) {
			c.sync = sync;
			c.lastPeg = Long.MIN_VALUE;
			c.lastT = Double.NaN;
		}
		s.segments = sync.segments();
		s.defaultFace = s.segments.equals(String.join("", Wheel.DEFAULT_SEGMENTS));
		double t = s.levelMs - sync.startTick() * 50.0;
		boolean reduced = FxSettings.reduceMotion();
		boolean spinning = t >= 0 && !sync.stopped(t, reduced);
		s.angle = sync.shownAngle(t, full, reduced);
		int n = sync.segmentCount();
		double pitch = 360.0 / n;
		if (full && spinning) {
			s.flapper = reduced ? 0 : WheelAnim.flapper(s.angle, pitch);
			int shift = (int) Math.floor(s.angle / 15.0);
			for (int i = 0; i < 24; i++) s.bulbs[i] = (byte) (Math.floorMod(i + shift, 3) == 0 ? 1 : 0);
			if (s.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) ticks(be, c, s.angle, pitch, t, sync);
		} else if (full && t >= 0 && t < sync.totalMs() + 600) {
			// the stop beat: the landed wedge brightens, the bulbs blink twice (steady with flashes off)
			double since = t - (reduced ? WheelAnim.REDUCED_MS : sync.totalMs());
			s.landed = sync.index();
			boolean on = !FxSettings.flashes() || ((int) (Math.max(0, since) / 150)) % 2 == 0;
			for (int i = 0; i < 24; i++) s.bulbs[i] = (byte) (on ? 1 : 0);
		} else {
			idleBulbs(s, full);
		}
		c.lastT = t;
	}

	/** Idle: the bulbs breathe slowly (a 3 s cycle, every other bulb). Far away: all off. */
	private static void idleBulbs(State s, boolean full) {
		if (!full) return;
		int phase = (int) (s.levelMs / 1500.0) % 2;
		for (int i = 0; i < 24; i++) s.bulbs[i] = (byte) (i % 2 == phase ? 1 : 0);
	}

	/** The acting player hears the screen's ticks, not the world's. */
	private static boolean viewingOwnScreen(WheelBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> scr && scr.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	/** A positional tick per peg passed (≤ 12 per second), pitch rising as the wheel slows. */
	private static void ticks(WheelBlockEntity be, Client c, double angle, double pitch, double t, WheelSync sync) {
		long peg = WheelAnim.pegCount(angle, pitch);
		if (c.lastPeg == Long.MIN_VALUE || Double.isNaN(c.lastT) || t - c.lastT > 500) {
			c.lastPeg = peg;
			return;
		}
		if (peg == c.lastPeg) return;
		c.lastPeg = peg;
		if (t - c.lastTickMs < 1000.0 / MAX_TICKS_PER_SECOND) return;
		c.lastTickMs = t;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		float vol = FxSettings.volume();
		if (vol <= 0) return;
		SoundEvent e = CasinoSounds.get("wheel_tick");
		float p = 0.9f + 0.5f * (float) Math.min(1, Math.max(0, t / sync.totalMs()));
		mc.level.playLocalSound(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + CY, be.getBlockPos().getZ() + 0.5,
			e != null ? e : SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.35f * vol, p, false);
	}

	// ---- submit -----------------------------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		int light = s.lightCoords;
		int lit = FULL_BRIGHT; // the wheel is lit by its own bulbs: the face reads at dusk and in dark casinos
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0f, 0f, 0f); // mulPose(Quaternionfc) is gone in 26.3
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE_TEX), (p, vc) -> stand(p, vc, light));
		if (s.defaultFace) {
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(FACE), (p, vc) -> face(p, vc, s.angle, lit));
		} else {
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE_TEX), (p, vc) -> wedges(p, vc, s.segments, s.angle, lit));
		}
		if (s.landed >= 0) {
			out.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE_TEX), (p, vc) -> stopBeat(p, vc, s));
		}
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(RIM), (p, vc) -> quad(p, vc, 0, CY, 92 * PX, FACE_Z + 0.004f, 0, 0, 1, 1, 0xFFFFFFFF, light));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(BULBS), (p, vc) -> bulbs(p, vc, s, light));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(HUB), (p, vc) -> quad(p, vc, 0, CY, 14 * PX, FACE_Z + 0.008f, 0, 0, 1, 28f / 168f,
			0xFFFFFFFF, light));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(FLAPPER), (p, vc) -> flapper(p, vc, s.flapper, light));
		pose.popPose();
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
	}

	/** A square quad facing +z centred at (cx, cy), half-size {@code h}; (u0, v0) = top-left. */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, float cx, float cy, float h, float z, float u0, float v0, float u1, float v1,
			int argb, int light) {
		vertex(p, vc, cx - h, cy - h, z, u0, v1, argb, light, 1);
		vertex(p, vc, cx + h, cy - h, z, u1, v1, argb, light, 1);
		vertex(p, vc, cx + h, cy + h, z, u1, v0, argb, light, 1);
		vertex(p, vc, cx - h, cy + h, z, u0, v0, argb, light, 1);
	}

	private static void flat(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float x1, float y1, float z, int argb, int light, float nz) {
		if (nz > 0) {
			vertex(p, vc, x0, y0, z, WU, WV, argb, light, nz);
			vertex(p, vc, x1, y0, z, WU, WV, argb, light, nz);
			vertex(p, vc, x1, y1, z, WU, WV, argb, light, nz);
			vertex(p, vc, x0, y1, z, WU, WV, argb, light, nz);
		} else {
			vertex(p, vc, x1, y0, z, WU, WV, argb, light, nz);
			vertex(p, vc, x0, y0, z, WU, WV, argb, light, nz);
			vertex(p, vc, x0, y1, z, WU, WV, argb, light, nz);
			vertex(p, vc, x1, y1, z, WU, WV, argb, light, nz);
		}
	}

	/** The post from the block top to the hub and the dark back of the wheel (so it is not see-through from behind). */
	private static void stand(PoseStack.Pose p, VertexConsumer vc, int light) {
		float w = 0.07f;
		flat(p, vc, -w, 1.0f, w, CY, 0.0f, 0xFF5A0E24, light, 1);
		flat(p, vc, -w, 1.0f, w, CY, -0.03f, 0xFF3A0816, light, -1);
		int segs = 24;
		float r = 92 * PX;
		for (int i = 0; i < segs; i++) {
			double a0 = Math.PI * 2 * i / segs;
			double a1 = Math.PI * 2 * (i + 1) / segs;
			float x0 = (float) Math.sin(a0) * r;
			float y0 = CY + (float) Math.cos(a0) * r;
			float x1 = (float) Math.sin(a1) * r;
			float y1 = CY + (float) Math.cos(a1) * r;
			// back fan (facing −z)
			vertex(p, vc, 0, CY, FACE_Z - 0.03f, WU, WV, 0xFF3A0816, light, -1);
			vertex(p, vc, x0, y0, FACE_Z - 0.03f, WU, WV, 0xFF3A0816, light, -1);
			vertex(p, vc, x1, y1, FACE_Z - 0.03f, WU, WV, 0xFF3A0816, light, -1);
			vertex(p, vc, x1, y1, FACE_Z - 0.03f, WU, WV, 0xFF3A0816, light, -1);
		}
	}

	/** The baked Appendix B face rotated clockwise (seen from the front) by {@code angle} degrees. */
	private static void face(PoseStack.Pose p, VertexConsumer vc, double angle, int light) {
		double a = Math.toRadians(angle);
		float cos = (float) Math.cos(a);
		float sin = (float) Math.sin(a);
		float[] cx = {-1, 1, 1, -1};
		float[] cy = {-1, -1, 1, 1};
		float[] cu = {0, 1, 1, 0};
		float[] cv = {1, 1, 0, 0};
		for (int i = 0; i < 4; i++) {
			float dx = cx[i] * R;
			float dy = cy[i] * R;
			vertex(p, vc, dx * cos + dy * sin, CY - dx * sin + dy * cos, FACE_Z, cu[i], cv[i], 0xFFFFFFFF, light, 1);
		}
	}

	/** Flat wedges for a configured segment list (the face texture shows Appendix B only). */
	private static void wedges(PoseStack.Pose p, VertexConsumer vc, String segments, double angle, int light) {
		int n = segments.length();
		double s = 360.0 / n;
		for (int i = 0; i < n; i++) {
			int col = Wheel.color(String.valueOf(segments.charAt(i))) | 0xFF000000;
			double c = i * s + angle;
			double a0 = Math.toRadians(c - s / 2);
			double a1 = Math.toRadians(c + s / 2);
			float x0 = (float) Math.sin(a0) * R;
			float y0 = CY + (float) Math.cos(a0) * R;
			float x1 = (float) Math.sin(a1) * R;
			float y1 = CY + (float) Math.cos(a1) * R;
			vertex(p, vc, 0, CY, FACE_Z, WU, WV, col, light, 1);
			vertex(p, vc, x1, y1, FACE_Z, WU, WV, col, light, 1);
			vertex(p, vc, x0, y0, FACE_Z, WU, WV, col, light, 1);
			vertex(p, vc, x0, y0, FACE_Z, WU, WV, col, light, 1);
		}
	}

	/** Stop beat: every other wedge dims (the landed one stays bright): a dark veil fan skipping the landed wedge. */
	private static void stopBeat(PoseStack.Pose p, VertexConsumer vc, State s) {
		int n = s.segments.length();
		double seg = 360.0 / n;
		int veil = 0x60000000;
		for (int i = 0; i < n; i++) {
			if (i == s.landed) continue;
			double c = i * seg + s.angle;
			double a0 = Math.toRadians(c - seg / 2);
			double a1 = Math.toRadians(c + seg / 2);
			float x0 = (float) Math.sin(a0) * R;
			float y0 = CY + (float) Math.cos(a0) * R;
			float x1 = (float) Math.sin(a1) * R;
			float y1 = CY + (float) Math.cos(a1) * R;
			vertex(p, vc, 0, CY, FACE_Z + 0.002f, WU, WV, veil, FULL_BRIGHT, 1);
			vertex(p, vc, x1, y1, FACE_Z + 0.002f, WU, WV, veil, FULL_BRIGHT, 1);
			vertex(p, vc, x0, y0, FACE_Z + 0.002f, WU, WV, veil, FULL_BRIGHT, 1);
			vertex(p, vc, x0, y0, FACE_Z + 0.002f, WU, WV, veil, FULL_BRIGHT, 1);
		}
	}

	/** 24 bulbs on the rim (visual/extras.md §4.1 positions): lit ones full bright. */
	private static void bulbs(PoseStack.Pose p, VertexConsumer vc, State s, int light) {
		float r = 85.5f * PX;
		float h = 3.5f * PX;
		for (int i = 0; i < 24; i++) {
			double a = Math.toRadians(i * 15 + 7.5);
			float bx = (float) Math.sin(a) * r;
			float by = CY + (float) Math.cos(a) * r;
			boolean on = s.bulbs[i] != 0;
			quad(p, vc, bx, by, h, FACE_Z + 0.006f, on ? 0.5f : 0f, 0, on ? 1f : 0.5f, 1, 0xFFFFFFFF, on ? FULL_BRIGHT : light);
		}
	}

	/** The flapper on its pivot above the face, deflected by {@code deg} (negative = pushed left). */
	private static void flapper(PoseStack.Pose p, VertexConsumer vc, double deg, int light) {
		float px = 0;
		float py = CY + 87 * PX;
		double a = Math.toRadians(deg);
		float cos = (float) Math.cos(a);
		float sin = (float) Math.sin(a);
		// sprite 14 × 24 px drawn from (−7, −4) relative to the pivot (GUI y down → world y up)
		float[][] corners = {{-7, 20}, {7, 20}, {7, -4}, {-7, -4}};
		float[] us = {0, 1, 1, 0};
		float[] vs = {1, 1, 0, 0};
		for (int i = 0; i < 4; i++) {
			float dx = corners[i][0] * PX;
			float dy = -corners[i][1] * PX;
			vertex(p, vc, px + dx * cos + dy * sin, py - dx * sin + dy * cos, FACE_Z + 0.01f, us[i], vs[i], 0xFFFFFFFF, light, 1);
		}
	}
}
