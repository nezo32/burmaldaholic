package dev.nezo.burmaldaholic.games.craps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.fx.WorldQuads;
import dev.nezo.burmaldaholic.core.anim.dice.DiceFaces;
import dev.nezo.burmaldaholic.core.anim.dice.DiceThrowPath;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsSync;
import dev.nezo.burmaldaholic.games.craps.logic.RollEvent;
import dev.nezo.burmaldaholic.games.craps.present.CrapsBoardGeometry;
import dev.nezo.burmaldaholic.games.craps.present.CrapsFlow;
import dev.nezo.burmaldaholic.games.craps.present.CrapsPub;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Craps in the world, v2 board (ui-v2.md §7.6, REDESIGN.md §9.3; lane D): the 2.0 × 1.4 felt with the craps print
 * (point boxes, COME, FIELD numbers, DON'T PASS BAR, the gold PASS LINE), every player's stacks from the public
 * {@link CrapsPub} (pass / field / come / odds, travelled come bets on their number), the puck ON on the point or OFF
 * in Don't Come, two 0.12-block dice that tumble along the SAME {@link DiceThrowPath} as the screen and rest on the real
 * faces, the upright board ({@code Craps} / {@code Point 8} / {@code 3 + 5 = 8}) and a plate per seat
 * ({@code name} / {@code 125 on table · +20}).
 */
public final class CrapsBoardRenderer extends BoardRenderer<CrapsTableBlockEntity, CrapsBoardRenderer.State> {
	private static final Identifier PRINT = Burmaldaholic.id("textures/entity/craps/board_print.png");
	private static final Identifier DICE = Burmaldaholic.id("textures/entity/craps/dice.png");
	/** Half edge of a die (0.12-block cubes, REDESIGN §9.1). */
	private static final float DIE = 0.06f;
	private static final double AUDIO_RANGE_SQ = 16 * 16;

	public CrapsBoardRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(CrapsModule.TABLE.blockEntityType(), CrapsBoardRenderer::new);
	}

	/** Per table: the throw being shown. */
	private static final class Client {
		int rolls = -1;
		@Nullable DiceThrowPath path;
		boolean catchUp;
		double lastT = Double.NaN;
		int cue;
	}

	public static final class State extends BoardState {
		boolean print;
		boolean dice;
		final float[] dx = new float[2];
		final float[] dz = new float[2];
		final float[] dy = new float[2];
		final float[] spin = new float[2];
		final float[] tilt = new float[2];
		final int[] face = new int[2];
		float puckX, puckZ, puckY;
		boolean puckOn;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected Felt felt(CrapsTableBlockEntity be) {
		return Felt.RECT;
	}

	@Override
	protected int theme(CrapsTableBlockEntity be) {
		CrapsSync sync = be.clientSync();
		return sync != null ? sync.theme() : super.theme(be);
	}

	private final DiceThrowPath.Sample sample = new DiceThrowPath.Sample();
	private final CrapsBeats.Puck puck = new CrapsBeats.Puck();

	@Override
	protected void fill(CrapsTableBlockEntity be, State s, float partialTick) {
		CrapsSync sync = be.clientSync();
		CrapsPub pub = be.clientPub();
		// the print only fits the full felt (a blocked overhang keeps the block's own top)
		s.print = s.felt == Felt.RECT;
		if (!s.print) {
			s.dice = false;
			return;
		}
		// stacks: every bet on the layout, by seat
		for (CrapsPub.Stack st : pub.stacks()) {
			double[] p = CrapsBoardGeometry.stack(st.kind(), st.point(), false, st.seat());
			s.stack(CrapsBoardGeometry.x(p[0]), CrapsBoardGeometry.z(p[1]), st.flat());
			if (st.odds() > 0) {
				double[] o = CrapsBoardGeometry.stack(st.kind(), st.point(), true, st.seat());
				s.stack(CrapsBoardGeometry.x(o[0]), CrapsBoardGeometry.z(o[1]), st.odds());
			}
		}
		// board lines
		int point = sync == null ? 0 : sync.point();
		s.line(BoardState.TITLE, 1, () -> Component.translatable("gui.burmaldaholic.craps.title"), BoardState.GOLD);
		s.line(BoardState.STATE, 31L * point + 7, () -> point == 0 ? Component.translatable(CrapsFlow.K + "board.come_out")
			: Component.translatable(CrapsFlow.K + "board.point", Texts.number(point)), BoardState.WHITE);
		if (sync != null && sync.d1() > 0) {
			double since = s.levelMs - sync.rollTime() * 50.0;
			boolean shown = since >= CrapsBeats.BADGE || since < 0 || since > 60_000;
			int color = switch (eventOf(sync)) {
				case NATURAL, POINT_MADE -> BoardState.GREEN;
				case CRAPS, SEVEN_OUT -> BoardState.RED;
				case POINT_SET -> BoardState.GOLD;
				default -> BoardState.WHITE;
			};
			int d1 = sync.d1();
			int d2 = sync.d2();
			s.line(BoardState.RESULT, shown ? 1000L * sync.rolls() + 1 : -1, () -> shown
				? Component.translatable(CrapsFlow.K + "board.roll", Texts.number(d1), Texts.number(d2), Texts.number(d1 + d2)) : null, color);
		}
		// plates: name / "125 on table · +20"
		long pubSeq = pub.seq();
		seatPlates(s, be, CrapsBoardRenderer::seatSpot, (seat, sp, plate) -> {
			long on = pub.onTable(seat);
			Long net = pub.net(seat);
			int color = net == null || net == 0 ? BoardState.MUTED : net > 0 ? BoardState.GREEN : BoardState.RED;
			plate.detail(pubSeq * 17 + seat, () -> detail(on, net), color);
		});
		dicePuck(be, s, sync);
	}

	private static Component detail(long onTable, @Nullable Long net) {
		Component on = Component.translatable(CrapsFlow.K + "board.plate", Texts.number(onTable));
		if (net == null || net == 0) return on;
		Component n = net > 0 ? Component.translatable("gui.burmaldaholic.core.v2.net.plus", Texts.number(net))
			: Component.translatable("gui.burmaldaholic.core.v2.net.minus", Texts.number(-net));
		return Component.translatable(CrapsFlow.K + "board.net", on, n);
	}

	private static RollEvent.Kind eventOf(CrapsSync sync) {
		RollEvent.Kind[] kinds = RollEvent.Kind.values();
		return sync.event() >= 0 && sync.event() < kinds.length ? kinds[sync.event()] : RollEvent.Kind.ROLL;
	}

	/** Seats around the front (+z) half of the rect felt, left to right. */
	private static float[] seatSpot(int seat, int seats) {
		int n = Math.max(1, seats);
		float f = (seat + 0.5f) / n;
		return new float[] {0.5f - 0.8f + 1.6f * f, 0.5f + 0.55f};
	}

	private void dicePuck(CrapsTableBlockEntity be, State s, @Nullable CrapsSync sync) {
		s.dice = false;
		if (sync == null) {
			puckAt(s, 0, 0, 0);
			return;
		}
		Client c = s.cache(Client::new);
		if (c.rolls != sync.rolls()) {
			c.rolls = sync.rolls();
			c.path = null;
			if (sync.d1() > 0 && sync.d2() > 0) {
				Box come = CrapsBoardGeometry.COME;
				double[] rest = CrapsBoardGeometry.diceRest();
				c.path = DiceThrowPath.of(sync.seed(), sync.d1(), sync.d2(), DiceThrowPath.Params.craps(CrapsBoardGeometry.PRINT_W - 20,
					CrapsBoardGeometry.PRINT_H - 8, come.y() + 2, come.x(), come.right(), rest[0] - 12, come.y() + 9, rest[0] + 6, come.bottom() - 9));
				double t0 = s.levelMs - sync.rollTime() * 50.0;
				c.catchUp = t0 > 0.85 * CrapsBeats.IDLE;
				c.lastT = Double.NaN;
				c.cue = 0;
			}
		}
		double t = s.levelMs - sync.rollTime() * 50.0;
		boolean full = s.hlod == HoloScale.Lod.FULL;
		if (c.catchUp || !full) t = Math.max(t, 1_000_000);
		boolean reduced = FxSettings.reduceMotion();
		DiceThrowPath p = c.path;
		if (p != null) {
			s.dice = true;
			for (int d = 0; d < 2; d++) {
				p.sample(d, reduced ? p.durationMs() : t, sample);
				s.dx[d] = CrapsBoardGeometry.x(sample.x);
				s.dz[d] = CrapsBoardGeometry.z(sample.y);
				s.dy[d] = (float) (sample.z * CrapsBoardGeometry.SCALE);
				boolean rest = sample.frame < 0;
				s.spin[d] = (float) (rest ? Math.round(sample.theta / 90.0) * 90.0 : sample.theta);
				s.tilt[d] = rest ? 0f : (float) (sample.theta * 1.7);
				s.face[d] = sample.face;
			}
			s.content(CrapsBoardGeometry.x(CrapsBoardGeometry.diceRest()[0]), BoardGeometry.TOP + 2 * DIE,
				CrapsBoardGeometry.z(CrapsBoardGeometry.diceRest()[1]), 0.12f);
			if (!c.catchUp && full && s.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) cues(be, c, p, t);
			c.lastT = t;
		}
		double pt = reduced ? (t >= CrapsBeats.PUCK ? CrapsBeats.PUCK_TOTAL : 0) : t - CrapsBeats.PUCK;
		double[] from = CrapsBoardGeometry.puck(sync.pointBefore());
		double[] to = CrapsBoardGeometry.puck(sync.point());
		CrapsBeats.puck(new int[] {(int) from[0], (int) from[1]}, sync.pointBefore(), new int[] {(int) to[0], (int) to[1]}, sync.point(),
			Math.max(0, pt), puck);
		puckAt(s, puck.x, puck.y, puck.lift);
		s.puckOn = puck.frame >= 3;
	}

	private static void puckAt(State s, double px, double py, double lift) {
		if (px == 0 && py == 0) {
			double[] off = CrapsBoardGeometry.puck(0);
			px = off[0];
			py = off[1];
		}
		s.puckX = CrapsBoardGeometry.x(px);
		s.puckZ = CrapsBoardGeometry.z(py);
		s.puckY = (float) (lift * CrapsBoardGeometry.SCALE);
		s.puckOn = false;
	}

	private static boolean viewingOwnScreen(CrapsTableBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> sc && sc.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	private static void cues(CrapsTableBlockEntity be, Client c, DiceThrowPath p, double t) {
		if (Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) return;
		var list = p.cues();
		while (c.cue < list.size() && list.get(c.cue).atMs() <= c.lastT) c.cue++;
		while (c.cue < list.size() && list.get(c.cue).atMs() <= t) {
			var cue = list.get(c.cue++);
			FxSounds.playAt(cue.sound(), be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1.0, be.getBlockPos().getZ() + 0.5, 0.7f, cue.pitch());
		}
	}

	private static int[][] faceUv(int top) {
		int[] faces = DiceFaces.layout(top);
		int[][] uv = new int[6][];
		for (int i = 0; i < 6; i++) uv[i] = new int[] {(faces[i] - 1) * 8, 0, 8};
		return uv;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		super.submit(s, pose, out, camera);
		if (!s.print || !s.visibleBoard()) return;
		int light = s.lightCoords;
		pose.pushPose();
		feltPose(pose, s); // W2: FACING + design → full 3×2 felt / compact block top (MT-DESIGN §2)
		float y = BoardGeometry.TOP + BoardGeometry.LAYER;
		float x0 = CrapsBoardGeometry.x(0), z0 = CrapsBoardGeometry.z(0);
		float x1 = CrapsBoardGeometry.x(CrapsBoardGeometry.PRINT_W), z1 = CrapsBoardGeometry.z(CrapsBoardGeometry.PRINT_H);
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(PRINT), (p, vc) -> WorldQuads.topRect(p, vc, x0, z0, x1, z1, y, 0, 0,
			CrapsBoardGeometry.PRINT_W, CrapsBoardGeometry.PRINT_H, CrapsBoardGeometry.PRINT_W, CrapsBoardGeometry.PRINT_H, 0xFFFFFFFF, light));
		// the puck: a disc on the felt, ON (white) / OFF (black)
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(DICE), (p, vc) -> WorldQuads.topQuad(p, vc, s.puckX, y + 0.004f + s.puckY, s.puckZ,
			0.055f, 0, s.puckOn ? 56 : 48, 0, 8, 8, 64, 16, 0xFFFFFFFF, light));
		if (s.dice) {
			for (int d = 0; d < 2; d++) {
				int die = d;
				pose.pushPose();
				pose.translate(s.dx[d], BoardGeometry.TOP + DIE + s.dy[d], s.dz[d]);
				pose.rotateAround(Axis.YP.rotationDegrees(s.spin[d]), 0f, 0f, 0f);
				if (s.tilt[d] != 0) pose.rotateAround(Axis.XP.rotationDegrees(s.tilt[d]), 0f, 0f, 0f);
				out.submitCustomGeometry(pose, RenderTypes.entityCutout(DICE), (p, vc) -> WorldQuads.cube(p, vc, DIE, faceUv(s.face[die]), 64, 16, light));
				pose.popPose();
			}
		}
		pose.popPose();
	}
}
