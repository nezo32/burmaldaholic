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
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoSync;
import dev.nezo.burmaldaholic.games.extras.present.MachinePub;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The in-world Plinko machine (REDESIGN.md §9.1, ui-v2.md §7.6 lane E): an upright 1.6 × 1.2 block board standing on
 * the machine — the screen's own cabinet art (board, 78 pegs, chute, the 13 bin caps with their multipliers printed on
 * them) — so a passer-by reads the machine like the Wheel of Fortune next to it. The ball runs the server's path on the
 * SAME {@link PlinkoAnim} motion as the player's screen (from the machine's {@link PlinkoSync}, sent once per drop):
 * pegs flash as it hits them, the landed cap lights and presses only at the landing, never before. A plate above the
 * board names the player at the machine and, after the landing, the multiplier (gold / green / cyan / red as the caps).
 * The block's own face keeps its 13 lamps (landed lamp, idle chase).
 *
 * <p>LOD: within {@link #RADIUS} blocks the ball moves, pegs flash and click positionally (12 blocks, not for the
 * player at the screen), lamps chase; up to 16 blocks the plate and the cap numbers are drawn (text grows with distance
 * so the glyph stays ≥ 13 px up to 8 blocks, {@link HoloScale}); up to {@link #VIEW} blocks the board at rest with the
 * landed cap after the landing (no ball: the far viewer never sees the bin before the ball). Reduce motion: the ball
 * appears in the bin after the dotted-path time.
 */
public final class PlinkoMachineRenderer extends SpectatorBlockEntityRenderer<PlinkoBlockEntity, PlinkoMachineRenderer.State> {
	public static final int RADIUS = 16;
	public static final int VIEW = 48;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final double AUDIO_RANGE_SQ = 12 * 12;
	private static final double TEXT_RANGE_SQ = 16 * 16;
	/** Attract chase: every 20 s the lamps light once left to right (80 ms each). */
	private static final int ATTRACT_MS = 20_000;
	private static final int CHASE_STEP_MS = 80;
	private static final float T = 1f / 16f;
	private static final float FACE_Z = 0.5f + 0.002f;

	// ---- the upright board (block-local, front +z, the block's bottom at y 0) ----
	/** Board width in blocks; 1 board px (of the 272 × 204 screen art) = {@link #P} block. */
	static final float BOARD_W = 1.6f;
	static final float P = BOARD_W / PlinkoDraw.BOARD_W;
	static final float BOARD_H = PlinkoDraw.BOARD_H * P;
	/** Bottom of the board: on the machine's top. */
	static final float Y0 = 1.0f;
	static final float Z = 0.0f;
	static final float DEPTH = 0.06f;
	private static final int CAP_W = 18;

	private static final Identifier LAMP = Burmaldaholic.id("textures/entity/extras/plinko_lamp.png");
	private static final Identifier BOARD = tex("plinko_board");
	private static final Identifier PEG = tex("plinko_peg");
	private static final Identifier BALL = tex("plinko_ball");
	private static final Identifier BINS = tex("plinko_bins");
	private static final Identifier CHUTE = tex("plinko_chute");
	private static final Identifier WHITE_TEX = Identifier.withDefaultNamespace("textures/block/snow.png");
	private static final float WU = 0.5f / 16f;
	private static final float WV = 1.5f / 16f;
	private static final int INK_70 = 0xB2180A28;

	private static final Map<PlinkoBlockEntity, Client> CACHE = new WeakHashMap<>();

	private final Font font;

	private static Identifier tex(String name) {
		return Burmaldaholic.id("textures/gui/extras/" + name + ".png");
	}

	public PlinkoMachineRenderer(BlockEntityRendererProvider.Context ctx) {
		this.font = ctx.font();
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path (vanilla BlockEntityRenderers.register is private)
	public static void register() {
		BlockEntityRendererRegistry.register(ExtrasModule.PLINKO.blockEntityType(), PlinkoMachineRenderer::new);
	}

	private static final class Client {
		@Nullable PlinkoSync sync;
		int lastRow = Integer.MIN_VALUE;
		MachinePub pub = MachinePub.EMPTY;
		@Nullable FormattedCharSequence name;
		@Nullable FormattedCharSequence result;
		final FormattedCharSequence[] caps = new FormattedCharSequence[Plinko.BINS];
		final int[] capW = new int[Plinko.BINS];
		double[] table = Plinko.Risk.MEDIUM.defaults();
		String lang = "";
	}

	public static final class State extends SpectatorRenderState {
		Direction facing = Direction.NORTH;
		boolean ball;
		double bx;
		double by;
		double sx = 1;
		double sy = 1;
		int roll;
		/** The chute gate is open (the release). */
		boolean chuteOpen;
		/** Path and timing for the peg flashes (−1 = no flashes this frame). */
		int path;
		int rowMs = 200;
		double ms = -1;
		/** Lamp states per bin: 0 dark, 1 green, 2 gold. */
		final byte[] lamps = new byte[13];
		/** The landed cap (−1 none) and its press (0..1). */
		int lit = -1;
		double press;
		final double[] table = new double[Plinko.BINS];
		/** Text (null = not drawn at this distance). */
		final FormattedCharSequence[] caps = new FormattedCharSequence[Plinko.BINS];
		final int[] capW = new int[Plinko.BINS];
		@Nullable FormattedCharSequence plate;
		float plateScale = HoloScale.PLATE_BASE;
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
		return true; // the board stands above its block
	}

	@Override
	protected void extractAnimated(PlinkoBlockEntity be, State s, float partialTick) {
		BlockState bs = be.getBlockState();
		s.facing = bs.hasProperty(CasinoTableBlock.FACING) ? bs.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		java.util.Arrays.fill(s.lamps, (byte) 0);
		s.ball = false;
		s.chuteOpen = false;
		s.ms = -1;
		s.lit = -1;
		s.press = 0;
		s.plate = null;
		boolean full = s.lod == Lod.FULL;
		Client c = CACHE.computeIfAbsent(be, k -> new Client());
		refreshText(be, c);
		System.arraycopy(c.table, 0, s.table, 0, Math.min(c.table.length, s.table.length));
		boolean text = s.distSq <= TEXT_RANGE_SQ;
		for (int i = 0; i < Plinko.BINS; i++) {
			s.caps[i] = text ? c.caps[i] : null;
			s.capW[i] = c.capW[i];
		}
		PlinkoSync sync = be.plinkoSync();
		boolean busy = false;
		boolean landedShown = false;
		if (sync != null) {
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
					s.bx = b.x();
					s.by = b.y();
					s.sx = b.sx();
					s.sy = b.sy();
					s.roll = b.roll();
				}
				s.chuteOpen = !reduced && t < PlinkoAnim.RELEASE_MS + sync.rowMs();
				if (!reduced && FxSettings.flashes()) {
					s.path = sync.path();
					s.rowMs = sync.rowMs();
					s.ms = t;
				}
				if (!reduced && s.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) pegs(be, c, sync, t);
			}
			// after the landing the ball rests in its cup and the cap stays lit (the last result reads from afar)
			if (t >= sync.landMs() || t < 0) {
				landedShown = true;
				s.lit = sync.bin();
				if (full) {
					double since = t - sync.landMs();
					s.press = !reduced && since >= 0 && since < 80 ? Math.sin(Math.PI * since / 80) : 0;
					s.ball = true;
					PlinkoAnim.Ball rest = PlinkoAnim.terminal(sync.path());
					s.bx = rest.x();
					s.by = rest.y();
					s.sx = s.sy = 1;
					s.roll = 0;
				}
			}
			int lit = sync.lampLit(t, FxSettings.flashes());
			if (lit == 1) s.lamps[sync.bin()] = (byte) (sync.edge() || sync.tier() >= 3 ? 2 : 1);
		}
		if (full && !busy) {
			long cycle = (long) (s.levelMs % ATTRACT_MS);
			int k = (int) (cycle / CHASE_STEP_MS);
			if (k < 13) s.lamps[k] = 1;
		}
		if (text && c.name != null) {
			Minecraft mc = Minecraft.getInstance();
			s.plate = landedShown && c.result != null ? c.result : c.name;
			s.plateScale = HoloScale.textPx(HoloScale.BOARD_BASE, Math.sqrt(s.distSq), mc.options.fov().get(), mc.getWindow().getHeight());
		}
	}

	/** Rebuilds the cached text when the published face or the language changes (never per frame). */
	private void refreshText(PlinkoBlockEntity be, Client c) {
		MachinePub pub = be.machinePub();
		String lang = Minecraft.getInstance().options.languageCode;
		if (pub.equals(c.pub) && lang.equals(c.lang) && c.caps[0] != null && (c.sync == be.plinkoSync())) return;
		c.pub = pub;
		c.lang = lang;
		double[] t = pub.centi().length == Plinko.BINS ? pub.table() : Plinko.Risk.MEDIUM.defaults();
		c.table = t;
		for (int i = 0; i < Plinko.BINS; i++) {
			c.caps[i] = Texts.raw(MachinePub.compact(t[i])).getVisualOrderText();
			c.capW[i] = font.width(c.caps[i]);
		}
		if (pub.name().isEmpty()) {
			c.name = null;
			c.result = null;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean you = mc.player != null && mc.player.getGameProfile().name().equals(pub.name());
		Component name = Texts.raw(pub.name()).withColor((you ? 0x5CE8E0 : 0xF4ECF8));
		c.name = name.getVisualOrderText();
		PlinkoSync sync = be.plinkoSync();
		if (sync != null && sync.bin() < t.length) {
			double mult = t[sync.bin()];
			Component r = Component.translatable("gui.burmaldaholic.extras.v2.board.plinko_result", name,
				Texts.raw(MachinePub.compact(mult)).withColor(multColor(mult) & 0xFFFFFF));
			c.result = r.getVisualOrderText();
		} else {
			c.result = null;
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
		mc.level.playLocalSound(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + Y0 + BOARD_H / 2, be.getBlockPos().getZ() + 0.5,
			ExtrasModule.PLINKO_PEG_SOUND, SoundSource.BLOCKS, 0.25f * vol, PlinkoAnim.pegPitch(sync.path(), row), false);
	}

	/** Colour of a multiplier as the caps and the v2 screen print it: gold top, green win, cyan even, red loss. */
	static int multColor(double mult) {
		int tier = PlinkoAnim.tier(mult);
		return switch (tier) {
			case 0 -> 0xFFFF6E6A;
			case 1 -> 0xFF5CE8E0;
			case 2, 3 -> 0xFF80FF40;
			default -> 0xFFFFD640;
		};
	}

	// ---- submit -----------------------------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		int light = s.lightCoords;
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0f, 0f, 0f);
		boolean anyLamp = false;
		for (byte l : s.lamps) anyLamp |= l != 0;
		if (anyLamp) out.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(LAMP), (p, vc) -> lamps(p, vc, s));
		// the cabinet: a wooden box behind the art, so the board has depth and is opaque from behind
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE_TEX), (p, vc) -> cabinet(p, vc, light));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(BOARD), (p, vc) -> rect(p, vc, 0, 0, PlinkoDraw.BOARD_W, PlinkoDraw.BOARD_H, 0, 0, 0, 1,
			1, FULL_BRIGHT));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(PEG), (p, vc) -> pegs(p, vc, s));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(CHUTE), (p, vc) -> rect(p, vc, 122, 4, 28, 16, 0.001f, s.chuteOpen ? 0.5f : 0f, 0,
			s.chuteOpen ? 1f : 0.5f, 1, FULL_BRIGHT));
		if (s.ball) out.submitCustomGeometry(pose, RenderTypes.entityCutout(BALL), (p, vc) -> ball(p, vc, s));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(BINS), (p, vc) -> caps(p, vc, s));
		for (int i = 0; i < Plinko.BINS; i++) {
			FormattedCharSequence t = s.caps[i];
			if (t == null) continue;
			int w = Math.max(1, s.capW[i]);
			float f = Math.min(1.4f, (CAP_W - 2f) / w);
			float cx = (float) PlinkoAnim.binX(i);
			float cy = PlinkoAnim.CAP_Y + 7 + (i == s.lit ? (float) (2 * s.press) : 0);
			pose.pushPose();
			pose.translate(-BOARD_W / 2 + cx * P, Y0 + BOARD_H - cy * P, Z + 0.004f);
			pose.scale(f * P, -f * P, f * P);
			int col = i == s.lit ? 0xFFFFFFFF : 0xFFF4ECF8;
			out.submitText(pose, -w / 2f, -4f, t, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, col, 0, 0xFF180A28);
			pose.popPose();
		}
		if (s.plate != null) {
			int w = font.width(s.plate);
			float k = s.plateScale;
			pose.pushPose();
			pose.translate(0, Y0 + BOARD_H + 0.04f + 6 * k, Z + 0.01f);
			pose.scale(k, -k, k);
			out.submitText(pose, -w / 2f, -4f, s.plate, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, 0xFFF4ECF8, INK_70, 0);
			pose.popPose();
		}
		pose.popPose();
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
	}

	/** A front-facing quad over board px (x, y, w, h), textured (u0, v0)–(u1, v1), at the board plane + dz. */
	private static void rect(PoseStack.Pose p, VertexConsumer vc, float x, float y, float w, float h, float dz, float u0, float v0, float u1, float v1,
			int light) {
		rect(p, vc, x, y, w, h, dz, u0, v0, u1, v1, 0xFFFFFFFF, light);
	}

	private static void rect(PoseStack.Pose p, VertexConsumer vc, float x, float y, float w, float h, float dz, float u0, float v0, float u1, float v1,
			int argb, int light) {
		float x0 = -BOARD_W / 2 + x * P;
		float x1 = -BOARD_W / 2 + (x + w) * P;
		float y1 = Y0 + BOARD_H - y * P;
		float y0 = Y0 + BOARD_H - (y + h) * P;
		float z = Z + dz;
		vertex(p, vc, x0, y0, z, u0, v1, argb, light, 1);
		vertex(p, vc, x1, y0, z, u1, v1, argb, light, 1);
		vertex(p, vc, x1, y1, z, u1, v0, argb, light, 1);
		vertex(p, vc, x0, y1, z, u0, v0, argb, light, 1);
	}

	private static void pegs(PoseStack.Pose p, VertexConsumer vc, State s) {
		for (int r = 0; r < PlinkoAnim.ROWS; r++) {
			for (int j = 0; j <= r; j++) {
				int st = s.ms >= 0 ? PlinkoAnim.pegState(s.path, s.rowMs, s.ms, r, j) : 0;
				float x = (float) Math.round(PlinkoAnim.pegX(r, j)) - 3;
				float y = (float) Math.round(PlinkoAnim.pegY(r)) - 3;
				rect(p, vc, x, y, 7, 7, 0.001f, st * 7 / 21f, 0, (st * 7 + 7) / 21f, 1, FULL_BRIGHT);
			}
		}
	}

	private static void caps(PoseStack.Pose p, VertexConsumer vc, State s) {
		for (int k = 0; k < Plinko.BINS; k++) {
			int t = PlinkoAnim.tier(s.table[k]);
			boolean lit = k == s.lit;
			float x = (float) Math.round(PlinkoAnim.binX(k)) - 9;
			float y = PlinkoAnim.CAP_Y + (lit ? (float) (2 * s.press) : 0);
			rect(p, vc, x, y, 18, 14, 0.002f, t * 18 / 90f, lit ? 0.5f : 0f, (t * 18 + 18) / 90f, lit ? 1f : 0.5f, FULL_BRIGHT);
		}
	}

	private static void ball(PoseStack.Pose p, VertexConsumer vc, State s) {
		float u0 = (s.roll & 3) * 9 / 36f;
		float hw = (float) (4.5 * s.sx);
		float hh = (float) (4.5 * s.sy);
		rect(p, vc, (float) s.bx - hw, (float) s.by - hh, 2 * hw, 2 * hh, 0.003f, u0, 0, u0 + 9 / 36f, 0.5f, FULL_BRIGHT);
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

	/** The cabinet box behind the art: back, sides and top in the frame's walnut. */
	private static void cabinet(PoseStack.Pose p, VertexConsumer vc, int light) {
		float x0 = -BOARD_W / 2;
		float x1 = BOARD_W / 2;
		float y0 = Y0;
		float y1 = Y0 + BOARD_H;
		float zb = Z - DEPTH;
		int wood = 0xFF5A3418;
		int dark = 0xFF3A2010;
		flat(p, vc, x0, y0, x1, y1, zb, dark, light, -1);
		// left (−x) and right (+x) sides
		vertex(p, vc, x0, y0, zb, WU, WV, wood, light, 0);
		vertex(p, vc, x0, y0, Z, WU, WV, wood, light, 0);
		vertex(p, vc, x0, y1, Z, WU, WV, wood, light, 0);
		vertex(p, vc, x0, y1, zb, WU, WV, wood, light, 0);
		vertex(p, vc, x1, y0, Z, WU, WV, wood, light, 0);
		vertex(p, vc, x1, y0, zb, WU, WV, wood, light, 0);
		vertex(p, vc, x1, y1, zb, WU, WV, wood, light, 0);
		vertex(p, vc, x1, y1, Z, WU, WV, wood, light, 0);
		// top
		vertex(p, vc, x0, y1, Z, WU, WV, wood, light, 0);
		vertex(p, vc, x1, y1, Z, WU, WV, wood, light, 0);
		vertex(p, vc, x1, y1, zb, WU, WV, wood, light, 0);
		vertex(p, vc, x0, y1, zb, WU, WV, wood, light, 0);
		// bottom (seen from below when the machine stands on a ledge)
		vertex(p, vc, x0, y0, zb, WU, WV, dark, light, 0);
		vertex(p, vc, x1, y0, zb, WU, WV, dark, light, 0);
		vertex(p, vc, x1, y0, Z, WU, WV, dark, light, 0);
		vertex(p, vc, x0, y0, Z, WU, WV, dark, light, 0);
	}

	/** A face quad over texels (u0, v0)–(u1, v1) of the block's front face, textured (tu0, tv0)–(tu1, tv1). */
	private static void faceQuad(PoseStack.Pose p, VertexConsumer vc, float u0, float v0, float u1, float v1, float z, float tu0, float tv0, float tu1,
			float tv1, int light) {
		float x0 = -0.5f + u0 * T;
		float x1 = -0.5f + u1 * T;
		float y0 = 1f - v1 * T;
		float y1 = 1f - v0 * T;
		vertex(p, vc, x0, y0, z, tu0, tv1, 0xFFFFFFFF, light, 1);
		vertex(p, vc, x1, y0, z, tu1, tv1, 0xFFFFFFFF, light, 1);
		vertex(p, vc, x1, y1, z, tu1, tv0, 0xFFFFFFFF, light, 1);
		vertex(p, vc, x0, y1, z, tu0, tv0, 0xFFFFFFFF, light, 1);
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
}
