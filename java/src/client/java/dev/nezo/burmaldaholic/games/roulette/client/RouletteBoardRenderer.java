package dev.nezo.burmaldaholic.games.roulette.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.board.BoardTextures;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.fx.WorldQuads;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBallPath;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSync;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import dev.nezo.burmaldaholic.games.roulette.present.RoulettePub;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteWorld;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The roulette table in the world (docs/architecture/ui-v2.md §7.6, REDESIGN.md §9; concept
 * {@code world_roulette_concept.png}): the 2.0 × 1.4 felt with the printed layout on the right and the wheel on the
 * left — the head turning and the ball running on the SAME {@link RouletteBallPath} as every screen (synced spin start /
 * result / seed), resting in the drawn pocket — every bettor's chips on the layout (coloured by player), the winning
 * cell framed in gold, the floating board ({@code ROULETTE} / {@code No more bets} → {@code 26 BLACK} / the top win or the
 * last numbers) and a plate per seat ({@code Steve} / {@code 55 on the table} → {@code 55 · +120}). Data: the block
 * entity's public {@link RouletteSync} + {@link RoulettePub}; nothing private.
 */
public final class RouletteBoardRenderer extends BoardRenderer<RouletteTableBlockEntity, RouletteBoardRenderer.State> {
	private static final Identifier[] WHEEL = {wheelTex("village"), wheelTex("bastion"), wheelTex("end")};
	private static final Identifier[] PRINT = {printTex("village"), printTex("bastion"), printTex("end")};
	private static final double AUDIO_RANGE_SQ = 24 * 24;
	/** Seat colours of the stacks (REDESIGN tables: per seat index). */
	private static final int[] SEAT_TINT = {0xFF4FC3F7, 0xFFFFB74D, 0xFFBA68C8, 0xFF81C784, 0xFFF06292, 0xFFFFF176, 0xFFA1887F, 0xFF90A4AE};
	private static final float Y = BoardGeometry.TOP;

	public RouletteBoardRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	private static Identifier wheelTex(String theme) {
		return Burmaldaholic.id("textures/entity/roulette/wheel_" + theme + ".png");
	}

	private static Identifier printTex(String theme) {
		return Burmaldaholic.id("textures/entity/roulette/layout_w_" + theme + ".png");
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(RouletteModule.TABLE.blockEntityType(), RouletteBoardRenderer::new);
		BlockEntityRendererRegistry.register(RouletteModule.HIGH_ROLLER_TABLE.blockEntityType(), RouletteBoardRenderer::new);
	}

	/** Per table cache: the ball path of the current spin and the sound cue cursor. */
	private static final class Cache {
		@Nullable RouletteSync sync;
		@Nullable RouletteBallPath path;
		double lastT = Double.NaN;
		boolean catchUp;
		int cue;
	}

	public static final class State extends BoardState {
		boolean active;
		double head;
		boolean ball;
		double ballAngle;
		float ballR;
		float ballY;
		/** winning cell (-1 = none) and its frame alpha */
		int winCell = -1;
		/** the table's own small stacks on the layout (the kit's discs are too big for a roulette cell) */
		final float[] sx = new float[RoulettePub.MAX_STACKS], sz = new float[RoulettePub.MAX_STACKS];
		final long[] sa = new long[RoulettePub.MAX_STACKS];
		final int[] st = new int[RoulettePub.MAX_STACKS];
		final float[] sAlpha = new float[RoulettePub.MAX_STACKS];
		int stacks;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected Felt felt(RouletteTableBlockEntity be) {
		return Felt.RECT;
	}

	@Override
	protected int theme(RouletteTableBlockEntity be) {
		RouletteSync sync = be.clientSync();
		return sync == null ? super.theme(be) : Math.max(0, Math.min(2, sync.theme()));
	}

	@Override
	protected int animateRadius() {
		return 32; // the ball is the show: tweened as far as the old wheel was
	}

	@Override
	protected void fill(RouletteTableBlockEntity be, State s, float partialTick) {
		RouletteSync sync = be.clientSync();
		RoulettePub pub = be.clientPub();
		s.active = sync != null;
		s.stacks = 0;
		s.winCell = -1;
		boolean fallback = s.felt == Felt.FALLBACK;
		float wx = 0.5f + (fallback ? 0 : RouletteWorld.WHEEL_X);
		float wz = 0.5f + (fallback ? 0 : RouletteWorld.WHEEL_Z);
		float wr = fallback ? 0.42f : RouletteWorld.WHEEL_R;
		s.content(wx, Y + 0.06f, wz, wr);
		boolean settled = false;
		int result = -1;
		if (sync != null) {
			result = wheel(be, s, sync, wr);
			RouletteBallPath p = s.cache(Cache::new).path;
			settled = sync.phase() == RouletteSync.RESULT
				|| (sync.phase() == RouletteSync.SPIN && p != null && p.settled(s.levelMs - sync.spinStart() * 50.0));
		}
		// stacks on the layout (not on the 1 × 1 fallback: no room for a layout there)
		if (!fallback) {
			boolean resultPhase = sync != null && sync.phase() == RouletteSync.RESULT;
			if (resultPhase && result >= 0) s.winCell = result;
			for (RoulettePub.Stack st : pub.stacks()) {
				if (s.stacks >= RoulettePub.MAX_STACKS) break;
				Spot spot = st.spotValue();
				float[] at = RouletteWorld.stack(spot);
				int i = s.stacks++;
				s.sx[i] = 0.5f + at[0];
				s.sz[i] = 0.5f + at[1];
				s.sa[i] = st.amount();
				int seat = pub.players().get(st.owner()).seat();
				s.st[i] = seat >= 0 ? SEAT_TINT[seat % SEAT_TINT.length] : 0xFFB0BEC5;
				s.sAlpha[i] = resultPhase && result >= 0 && !spot.covers(result) ? 0.35f : 1f;
			}
		}
		lines(be, s, sync, pub, settled, result);
		plates(be, s, pub);
	}

	/** The wheel's head / ball state; returns the drawn number once the ball may show it (else -1). */
	private static int wheel(RouletteTableBlockEntity be, State s, RouletteSync sync, float wr) {
		Cache c = s.cache(Cache::new);
		if (c.sync == null || c.sync.seq() != sync.seq() || c.sync.phase() != sync.phase()) {
			boolean newSpin = c.sync == null || c.sync.seq() != sync.seq();
			c.sync = sync;
			if (sync.result() >= 0 && (newSpin || c.path == null)) {
				c.path = RouletteBallPath.of(sync.result(), sync.seed(), Math.max(1, sync.spinTicks()) * 50);
				c.lastT = Double.NaN;
				c.cue = 0;
				double t0 = s.levelMs - sync.spinStart() * 50.0;
				c.catchUp = t0 > 0 && TimelineSeed.showSettled(t0, c.path.spinMs);
			} else if (sync.result() < 0) {
				c.path = null;
			}
		}
		float k = wr / RouletteWorld.WHEEL_R;
		boolean reduced = FxSettings.reduceMotion();
		boolean full = s.lod == Lod.FULL;
		int last = sync.lastResult();
		double base = RouletteWheelView.baseAngle(last);
		RouletteBallPath p = c.path;
		boolean spinning = sync.phase() == RouletteSync.SPIN || sync.phase() == RouletteSync.RESULT;
		if (p != null && spinning) {
			double t = s.levelMs - sync.spinStart() * 50.0;
			if (c.catchUp || !full) t = Math.max(t, p.spinMs);
			s.head = RouletteWheelView.headAngle(p, t, base, reduced);
			s.ball = !reduced || p.settled(t);
			s.ballAngle = s.head + p.rel(t);
			float r = (float) p.radius(t);
			float kr = (float) Math.max(0, Math.min(1, (r - RouletteBallPath.R_POCKET) / (RouletteBallPath.R_ORBIT - RouletteBallPath.R_POCKET)));
			s.ballR = k * (RouletteWorld.BALL_POCKET_R + (RouletteWorld.BALL_TRACK_R - RouletteWorld.BALL_POCKET_R) * kr);
			s.ballY = Y + 0.02f + (r > RouletteBallPath.R_POCKET_RIM && r < RouletteBallPath.R_ORBIT_END - 0.05 ? 0.01f : 0f);
			if (full && !c.catchUp && s.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) cues(be, c, p, t);
			c.lastT = t;
			return sync.phase() == RouletteSync.RESULT || p.settled(t) ? p.result : -1;
		}
		s.head = base;
		s.ball = last >= 0;
		s.ballAngle = base + (last >= 0 ? RouletteBallPath.pocketAngle(last) : 0);
		s.ballR = k * RouletteWorld.BALL_POCKET_R;
		s.ballY = Y + 0.02f;
		return -1;
	}

	private static void lines(RouletteTableBlockEntity be, State s, @Nullable RouletteSync sync, RoulettePub pub, boolean settled, int result) {
		boolean hr = be.isHighRoller();
		s.line(BoardState.TITLE, hr ? 2 : 1, () -> Component.translatable("gui.burmaldaholic.roulette.v2.board.title" + (hr ? "_high_roller" : "")),
			BoardState.GOLD);
		int phase = sync == null ? RouletteSync.BETTING : sync.phase();
		if (settled && result >= 0) {
			int col = switch (Wheel.color(result)) {
				case RED -> BoardState.RED;
				case BLACK -> BoardState.WHITE;
				case GREEN -> BoardState.GREEN;
			};
			s.line(BoardState.STATE, 1000 + result, () -> Component.translatable("gui.burmaldaholic.roulette.v2.board.result", Texts.number(result),
				Component.translatable("gui.burmaldaholic.roulette.v2.board.color." + Wheel.color(result).id())), col);
		} else if (phase == RouletteSync.BETTING) {
			s.line(BoardState.STATE, 1, () -> Component.translatable("gui.burmaldaholic.roulette.v2.board.place_bets"), BoardState.WHITE);
		} else {
			s.line(BoardState.STATE, 2, () -> Component.translatable("gui.burmaldaholic.roulette.no_more_bets"), BoardState.WHITE);
		}
		if (phase == RouletteSync.RESULT && pub.topNet() > 0) {
			String name = pub.topName();
			long net = pub.topNet();
			s.line(BoardState.RESULT, 31L * name.hashCode() + net, () -> Component.translatable("gui.burmaldaholic.roulette.v2.board.top_win", Texts.raw(name),
				Texts.number(net)), BoardState.GREEN);
		} else if (!pub.history().isEmpty()) {
			List<Integer> h = pub.history();
			// the last numbers (newest first), each in its pocket colour
			long key = 7;
			for (int n : h) key = key * 41 + n;
			s.line(BoardState.RESULT, key, () -> lastNumbers(h, 0, 6), BoardState.MUTED);
		}
	}

	/** "17 · 0 · 32 · 8" in pocket colours. */
	static Component lastNumbers(List<Integer> h, int from, int max) {
		MutableComponent c = Component.empty();
		for (int i = from; i < Math.min(h.size(), from + max); i++) {
			if (i > from) c.append(Texts.raw(" "));
			int n = h.get(i);
			c.append(Texts.number(n).withStyle(switch (Wheel.color(n)) {
				case RED -> ChatFormatting.RED;
				case BLACK -> ChatFormatting.WHITE;
				case GREEN -> ChatFormatting.GREEN;
			}));
		}
		return c;
	}

	private static void plates(RouletteTableBlockEntity be, State s, RoulettePub pub) {
		seatPlates(s, be, (seat, seats) -> {
			float[] p = RouletteWorld.plate(seat, seats);
			return new float[] {0.5f + p[0], 0.5f + RouletteWorld.FELT_D / 2 - 0.1f};
		}, (seat, seatPub, plate) -> {
			for (RoulettePub.Player p : pub.players()) {
				if (p.seat() != seat || (p.total() <= 0 && !p.settled())) continue;
				long total = p.total();
				long net = p.net();
				if (p.settled()) {
					String k = net > 0 ? "net_win" : net < 0 ? "net_lost" : "net_even";
					plate.detail(31L * total + net + 7, () -> net > 0 ? Component.translatable("gui.burmaldaholic.roulette.v2.board." + k, Texts.number(total),
						Texts.number(net)) : Component.translatable("gui.burmaldaholic.roulette.v2.board." + k, Texts.number(total)),
						net > 0 ? BoardState.GREEN : net < 0 ? BoardState.RED : BoardState.MUTED);
				} else {
					plate.detail(total, () -> Component.translatable("gui.burmaldaholic.roulette.v2.board.on_table", Texts.number(total)), BoardState.GOLD);
				}
				break;
			}
		});
	}

	/** The acting player hears the screen's sounds, not the table's. */
	private static boolean viewingOwnScreen(RouletteTableBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> sc && sc.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	private static void cues(RouletteTableBlockEntity be, Cache c, RouletteBallPath p, double t) {
		if (Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) return;
		var list = p.cues();
		while (c.cue < list.size() && list.get(c.cue).atMs() <= c.lastT) c.cue++;
		double x = be.getBlockPos().getX() + 0.5;
		double y = be.getBlockPos().getY() + 1.0;
		double z = be.getBlockPos().getZ() + 0.5;
		while (c.cue < list.size() && list.get(c.cue).atMs() <= t) {
			var cue = list.get(c.cue++);
			FxSounds.playAt(cue.sound(), x, y, z, 0.8f, cue.pitch());
		}
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		super.submit(s, pose, out, camera); // felt, kit stacks (none), plates, board
		if (!s.visibleBoard() || !s.active) return;
		int light = s.lightCoords;
		boolean fallback = s.felt == Felt.FALLBACK;
		pose.pushPose();
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0.5f, 0f, 0.5f);
		if (!fallback) {
			float x0 = 0.5f + RouletteWorld.LAYOUT_X;
			float z0 = 0.5f + RouletteWorld.LAYOUT_Z;
			float x1 = x0 + RouletteWorld.LAYOUT_W;
			float z1 = z0 + RouletteWorld.LAYOUT_D;
			int w = RouletteWorld.PRINT.width();
			int h = RouletteWorld.PRINT.height();
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(PRINT[s.theme]), (p, vc) -> {
				WorldQuads.topRect(p, vc, x0, z0, x1, z1, Y + 0.002f, 0, 0, w, h, w, h, 0xFFFFFFFF, light);
			});
			if (s.winCell >= 0) {
				var r = RouletteWorld.PRINT.cell(s.winCell);
				float cx0 = x0 + r.x() / RouletteWorld.PX_PER_BLOCK, cz0 = z0 + r.y() / RouletteWorld.PX_PER_BLOCK;
				float cx1 = cx0 + r.w() / RouletteWorld.PX_PER_BLOCK, cz1 = cz0 + r.h() / RouletteWorld.PX_PER_BLOCK;
				out.submitCustomGeometry(pose, RenderTypes.entityCutout(BoardTextures.BACKING), (p, vc) -> frame(p, vc, cx0, cz0, cx1, cz1, 0.008f,
					Y + 0.004f));
			}
			if (s.stacks > 0) {
				int n = s.stacks;
				out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(BoardTextures.CHIPS), (p, vc) -> {
					int[] d = new int[RouletteWorld.MAX_DISCS];
					for (int i = 0; i < n; i++) RouletteChips.stack(p, vc, s.sx[i], s.sz[i], s.sa[i], s.st[i], s.sAlpha[i], d, light);
				});
			}
		}
		float wx = 0.5f + (fallback ? 0 : RouletteWorld.WHEEL_X);
		float wz = 0.5f + (fallback ? 0 : RouletteWorld.WHEEL_Z);
		float wr = fallback ? 0.42f : RouletteWorld.WHEEL_R;
		float hr = wr * 21f / 32f;
		float head = (float) s.head;
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(WHEEL[s.theme]), (p, vc) -> {
			WorldQuads.topQuad(p, vc, wx, Y + 0.003f, wz, wr, 0, 64, 0, 64, 64, 128, 80, 0xFFFFFFFF, light);
			WorldQuads.topQuad(p, vc, wx, Y + 0.006f, wz, hr, head, 0, 0, 64, 64, 128, 80, 0xFFFFFFFF, light);
			WorldQuads.topQuad(p, vc, wx, Y + 0.03f, wz, wr * 0.06f, head, 16, 64, 8, 8, 128, 80, 0xFFFFFFFF, light);
			if (s.ball) {
				double a = Math.toRadians(s.ballAngle);
				float bx = wx + (float) (Math.sin(a) * s.ballR);
				float bz = wz + (float) (-Math.cos(a) * s.ballR);
				WorldQuads.topQuad(p, vc, bx, s.ballY, bz, wr * 0.045f, 0, 0, 64, 4, 4, 128, 80, 0xFFFFFFFF, light);
			}
		});
		pose.popPose();
	}

	/** A gold frame (four thin quads) around a cell, using the white backing texture tinted. */
	private static void frame(PoseStack.Pose p, VertexConsumer vc, float x0, float z0, float x1, float z1, float t, float y) {
		int gold = 0xFFFFD640;
		int l = WorldQuads.FULL_BRIGHT;
		rect(p, vc, x0 - t, z0 - t, x1 + t, z0, y, gold, l);
		rect(p, vc, x0 - t, z1, x1 + t, z1 + t, y, gold, l);
		rect(p, vc, x0 - t, z0, x0, z1, y, gold, l);
		rect(p, vc, x1, z0, x1 + t, z1, y, gold, l);
	}

	private static void rect(PoseStack.Pose p, VertexConsumer vc, float x0, float z0, float x1, float z1, float y, int argb, int light) {
		WorldQuads.topRect(p, vc, x0, z0, x1, z1, y, 0, 0, 4, 4, 4, 4, argb, light);
	}
}
