package dev.nezo.burmaldaholic.games.craps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.fx.WorldQuads;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceAudio;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceWorldDraw;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceEvent;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRollCache;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsSync;
import dev.nezo.burmaldaholic.games.craps.logic.RollEvent;
import dev.nezo.burmaldaholic.games.craps.present.CrapsBoardGeometry;
import dev.nezo.burmaldaholic.games.craps.present.CrapsFlow;
import dev.nezo.burmaldaholic.games.craps.present.CrapsPub;
import dev.nezo.burmaldaholic.games.craps.present.CrapsStick;
import dev.nezo.burmaldaholic.games.craps.present.CrapsThrow;
import java.util.ArrayList;
import java.util.List;
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
 * Craps in the world (ui-v2.md §7.6, REDESIGN.md §9.3; lane D, motion CM = CRAPS-ANIM-SPEC §6): the print with its
 * rubber-pyramid back wall (a raised box whose inner face carries the pyramids, the hit diamond flashing), a chip groove
 * on the players' rail, every player's stacks from the public {@link CrapsPub} — taken, paid, travelled and collected in
 * casino order after the roll — the puck turning over to its spot, the SAME {@link CrapsThrow} solve as every screen
 * (quaternion cubes centred at their support height: faces real all the time, no corner in the felt), the stickman's
 * dowel returning the dice, the upright board ({@code Craps} / {@code Point 8} / {@code 3 + 5 = 8}) and the seat plates.
 *
 * <p>LOD (CS §6, D12): ≤ 8 blocks full motion with shadows, flashes, the turning puck and the stick; 8–16 the dice
 * move without shadows, the puck glides flat; 16–32 NO dice until the throw rests (no result spoiler at distance), then
 * they appear at rest, the puck sits at its final spot and the stacks are static; beyond, the block model only.
 */
public final class CrapsBoardRenderer extends BoardRenderer<CrapsTableBlockEntity, CrapsBoardRenderer.State> {
	private static final Identifier PRINT = Burmaldaholic.id("textures/entity/craps/board_print.png");
	private static final Identifier WALL = Burmaldaholic.id("textures/entity/craps/wall.png");
	private static final Identifier PUCK = Burmaldaholic.id("textures/entity/craps/puck.png");
	private static final double AUDIO_RANGE_SQ = 16 * 16;
	/** Half edge of a world die (design blocks) = one tray unit. */
	private static final float H = CrapsBoardGeometry.DIE / 2;
	/** Puck radius (design blocks). */
	private static final float PUCK_R = 0.066f;
	/** LOD distances (blocks): A full, B moving, C rest only. */
	public static final double LOD_A = 8, LOD_B = 16;

	public CrapsBoardRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(CrapsModule.TABLE.blockEntityType(), CrapsBoardRenderer::new);
	}

	/** Per table: the throw being shown (solved once per roll) and the stacks as they were before it. */
	private static final class Client {
		final DiceRollCache cache = new DiceRollCache(2);
		int rolls = -1;
		@Nullable DiceRoll roll;
		CrapsBeats beats = CrapsBeats.of(1700);
		int side;
		boolean catchUp;
		@Nullable EventCursor cursor;
		final DiceAudio audio = DiceAudio.world();
		@Nullable CrapsPub before;
		CrapsPub lastPub = CrapsPub.EMPTY;
		boolean stickCue;
		// wall hits (arena x) and their times
		final double[] wallT = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		final double[] wallX = new double[2];
		int wallNext;
		final EventCursor.Listener onEvent = (MotionEvent e, boolean silent) -> {
			if (e.kind() == DiceEvent.WALL) {
				int i = wallNext;
				wallNext = (i + 1) % 2;
				wallT[i] = e.tMs();
				wallX[i] = e.x();
			}
		};
	}

	public static final class State extends BoardState {
		boolean print;
		boolean dice;
		boolean shadows;
		final DieSample[] die = {new DieSample(), new DieSample()};
		float puckX, puckZ, puckY;
		boolean puckFromOn, puckToOn;
		float puckFlip;
		float puckDirX = 1, puckDirZ;
		boolean stick;
		float stickX, stickZ;
		final float[] flashX = new float[2];
		final float[] flashA = new float[2];
		final boolean[] flashHit = new boolean[2];
		boolean groove;
		float grooveX0, grooveX1, grooveZ0, grooveZ1, grooveY;
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

	private final CrapsBeats.Puck puck = new CrapsBeats.Puck();
	private final CrapsStick.Out stick = new CrapsStick.Out();

	@Override
	protected void fill(CrapsTableBlockEntity be, State s, float partialTick) {
		CrapsSync sync = be.clientSync();
		CrapsPub pub = be.clientPub();
		// the print only fits the full felt (a blocked overhang keeps the block's own top)
		s.print = s.felt == Felt.RECT;
		s.dice = false;
		s.stick = false;
		s.flashA[0] = s.flashA[1] = 0;
		groove(s);
		if (!s.print) return;
		Client c = s.cache(Client::new);
		double t = sync == null ? 1e9 : s.levelMs - sync.rollTime() * 50.0;
		if (sync != null) newRoll(c, sync, t);
		boolean lodA = s.dist <= LOD_A;
		boolean lodB = s.dist <= LOD_B;
		boolean reduced = FxSettings.reduceMotion();
		stacks(s, c, sync, pub, c.catchUp || !lodB || reduced ? 1e9 : t);
		// board lines
		int point = sync == null ? 0 : sync.point();
		s.line(BoardState.TITLE, 1, () -> Component.translatable("gui.burmaldaholic.craps.title"), BoardState.GOLD);
		// the state line follows the puck: the point before the throw until the puck lands (no spoiler while the dice fly)
		int shownPoint = sync == null ? 0 : c.catchUp || t >= c.beats.PUCK + CrapsBeats.puckTouchdown() || t < 0 ? point : sync.pointBefore();
		s.line(BoardState.STATE, 31L * shownPoint + 7, () -> shownPoint == 0 ? Component.translatable(CrapsFlow.K + "board.come_out")
			: Component.translatable(CrapsFlow.K + "board.point", Texts.number(shownPoint)), BoardState.WHITE);
		if (sync != null && sync.d1() > 0) {
			boolean shown = t >= c.beats.BADGE || t < 0 || t > 60_000 || c.catchUp;
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
		// plates: name / "125 on table · +20" (the nets once the chips have moved)
		long pubSeq = pub.seq();
		boolean netsShown = sync == null || t >= c.beats.CHIPS || c.catchUp;
		seatPlates(s, be, CrapsBoardRenderer::seatSpot, (seat, sp, plate) -> {
			long on = pub.onTable(seat);
			Long net = netsShown ? pub.net(seat) : null;
			int color = net == null || net == 0 ? BoardState.MUTED : net > 0 ? BoardState.GREEN : BoardState.RED;
			plate.detail(pubSeq * 17 + seat * 2 + (netsShown ? 1 : 0), () -> detail(on, net), color);
		});
		dice(be, s, c, sync, t, lodA, lodB, reduced);
		puck(s, c, sync, t, lodA, lodB, reduced);
		c.lastPub = pub;
		if (probePos != null) probe(be, s);
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

	/** Solves a new throw once (the same solve as every screen) and snapshots the stacks as they were before it. */
	private static void newRoll(Client c, CrapsSync sync, double t) {
		if (c.rolls == sync.rolls()) return;
		boolean first = c.rolls < 0;
		c.rolls = sync.rolls();
		c.roll = null;
		c.before = first ? null : c.lastPub;
		c.stickCue = false;
		c.wallT[0] = c.wallT[1] = Double.NEGATIVE_INFINITY;
		if (sync.d1() <= 0 || sync.d2() <= 0) return;
		int side = sync.shooterSide();
		c.side = side;
		DiceRoll r = c.cache.get(DiceRollCache.key(sync.rolls(), side, sync.seed()), () -> CrapsThrow.solve(side, sync.seed(), sync.d1(), sync.d2(), sync.pointBefore()));
		c.roll = r;
		c.beats = CrapsBeats.of(sync.restMs() > 0 ? sync.restMs() : r.restMs());
		c.catchUp = first && t > c.beats.CHIPS;
		c.cursor = DiceAudio.cursor(r.events());
		if (t > 0) c.cursor.reset(t, true);
	}

	// ---- dice -------------------------------------------------------------------------------------------------------

	private static void toDesign(DieSample in, DieSample out) {
		out.set(in);
		out.x = CrapsBoardGeometry.x(CrapsBoardGeometry.px(in.x)) / H;
		out.y = CrapsBoardGeometry.z(CrapsBoardGeometry.py(in.y)) / H;
	}

	private final DieSample tmp = new DieSample();
	private final double[] rest = new double[4];
	private final double[] home = new double[4];
	private final double[] qq = new double[4];

	private void dice(CrapsTableBlockEntity be, State s, Client c, @Nullable CrapsSync sync, double t, boolean lodA, boolean lodB, boolean reduced) {
		DiceRoll r = c.roll;
		if (sync == null || r == null) return;
		// D12 / CS §6 C: beyond the animate range no dice are drawn before the throw rests (no spoiler at distance)
		boolean settled = c.catchUp || reduced || t >= c.beats.rest;
		if (!lodB && !settled) return;
		double tt = c.catchUp ? 1e9 : t;
		s.dice = true;
		s.shadows = lodA;
		if (tt >= c.beats.STICK && !reduced) {
			// the stickman's return (print px), the dice keeping their faces
			for (int d = 0; d < 2; d++) {
				r.sample(d, 1e9, tmp);
				rest[2 * d] = CrapsBoardGeometry.px(tmp.x);
				rest[2 * d + 1] = CrapsBoardGeometry.py(tmp.y);
				double[] h = CrapsThrow.release(c.side, d);
				home[2 * d] = CrapsBoardGeometry.px(h[0]);
				home[2 * d + 1] = CrapsBoardGeometry.py(h[1]);
			}
			CrapsStick.sample(tt, c.beats, rest, home, -24, CrapsBoardGeometry.dieHalfPx() * 2, stick);
			for (int d = 0; d < 2; d++) {
				r.sample(d, 1e9, tmp);
				DieSample o = s.die[d];
				o.set(tmp);
				if (stick.drag >= 0.5) {
					CubeGroup.element(CubeGroup.nearest(o.q), qq);
					Quat.copy(qq, o.q);
				}
				o.x = CrapsBoardGeometry.x(stick.x[d]) / H;
				o.y = CrapsBoardGeometry.z(stick.y[d]) / H;
			}
			s.stick = lodA && stick.visible;
			s.stickX = CrapsBoardGeometry.x(stick.hookX);
			s.stickZ = CrapsBoardGeometry.z(stick.hookY);
		} else {
			double st = reduced || !lodB ? 1e9 : tt;
			for (int d = 0; d < 2; d++) {
				r.sample(d, st, tmp);
				toDesign(tmp, s.die[d]);
			}
		}
		float cx = (float) ((s.die[0].x + s.die[1].x) / 2 * H);
		float cz = (float) ((s.die[0].y + s.die[1].y) / 2 * H);
		s.content(cx, BoardGeometry.TOP + 2 * H, cz, 0.12f);
		// wall flashes and sounds from the events: within 8 blocks all contacts, 8–16 only the wall and the rest
		EventCursor cur = c.cursor;
		if (cur != null && !c.catchUp && lodB) {
			boolean audible = s.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be) && lodA;
			c.audio.muted(!audible);
			c.audio.at(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1.0, be.getBlockPos().getZ() + 0.5);
			c.audio.advance(cur, Math.min(t, c.beats.IDLE), 0, c.onEvent);
		}
		if (lodA && !reduced) {
			for (int i = 0; i < 2; i++) {
				double since = t - c.wallT[i];
				if (since < 0 || since > 340) continue;
				s.flashX[i] = CrapsBoardGeometry.x(CrapsBoardGeometry.px(c.wallX[i]));
				boolean hit = since < 40 && FxSettings.flashes();
				s.flashHit[i] = hit;
				s.flashA[i] = (float) (hit ? 1 : Math.max(0, 1 - (since - 40) / 300.0) * (FxSettings.flashes() ? 1 : 0.5));
			}
		}
		if (lodA && !c.stickCue && t >= c.beats.stickHook() && t < c.beats.stickHook() + 150 && s.distSq <= AUDIO_RANGE_SQ && !viewingOwnScreen(be)) {
			c.stickCue = true;
			FxSounds.playAt("craps_stick", be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1.0, be.getBlockPos().getZ() + 0.5, 0.7f, 1f);
		}
	}

	private static boolean viewingOwnScreen(CrapsTableBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> sc && sc.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	// ---- puck -------------------------------------------------------------------------------------------------------

	private void puck(State s, Client c, @Nullable CrapsSync sync, double t, boolean lodA, boolean lodB, boolean reduced) {
		int from = sync == null ? 0 : sync.pointBefore();
		int to = sync == null ? 0 : sync.point();
		double[] f = CrapsBoardGeometry.puck(from);
		double[] e = CrapsBoardGeometry.puck(to);
		double pt = c.catchUp || reduced || !lodB ? 1e9 : t - c.beats.PUCK;
		CrapsBeats.puck(f, from, e, to, Math.max(0, pt), 0.35, puck);
		s.puckX = CrapsBoardGeometry.x(puck.x);
		s.puckZ = CrapsBoardGeometry.z(puck.y);
		s.puckY = (float) (puck.lift * CrapsBoardGeometry.SCALE);
		// the disc turns over about the axis across its travel (A); B shows the end face at once
		s.puckFromOn = from != 0;
		s.puckToOn = to != 0;
		s.puckFlip = lodA ? (float) puck.flip : (puck.flip > 0 ? 1 : 0);
		double dx = e[0] - f[0];
		double dz = e[1] - f[1];
		double len = Math.hypot(dx, dz);
		s.puckDirX = len < 1e-6 ? 1 : (float) (dx / len);
		s.puckDirZ = len < 1e-6 ? 0 : (float) (dz / len);
	}

	// ---- stacks -----------------------------------------------------------------------------------------------------

	/**
	 * What a roll did to a public stack, from the public dice and the bet kind (cosmetic only: the money and the plates'
	 * nets are the server's): 1 win, -1 lose, 2 push, 3 the come bet travels, 0 nothing.
	 */
	static int outcome(String kind, int point, int pointBefore, int total) {
		boolean comeOut = pointBefore == 0;
		switch (kind) {
			case "field" -> {
				return total == 5 || total == 6 || total == 7 || total == 8 ? -1 : 1;
			}
			case "pass", "come" -> {
				boolean fresh = "pass".equals(kind) ? comeOut : point == 0;
				int p = "pass".equals(kind) ? pointBefore : point;
				if (fresh) {
					if (total == 7 || total == 11) return 1;
					if (total == 2 || total == 3 || total == 12) return -1;
					return "come".equals(kind) ? 3 : 0;
				}
				if (total == p) return 1;
				return total == 7 ? -1 : 0;
			}
			case "dont_pass", "dont_come" -> {
				boolean fresh = "dont_pass".equals(kind) ? comeOut : point == 0;
				int p = "dont_pass".equals(kind) ? pointBefore : point;
				if (fresh) {
					if (total == 2 || total == 3) return 1;
					if (total == 12) return 2;
					if (total == 7 || total == 11) return -1;
					return "dont_come".equals(kind) ? 3 : 0;
				}
				if (total == 7) return 1;
				return total == p ? -1 : 0;
			}
			default -> {
				return 0;
			}
		}
	}

	private record Spot(int seat, String kind, int point) {}

	/**
	 * The stacks: before the chips move, the layout as it was before the roll; then the losers slide to the bank (one
	 * wave), come bets travel to their number, winners get a payout stack from the bank and both slide to the player's
	 * rail; pushes slide home. Static at C and with reduced motion.
	 */
	private static void stacks(State s, Client c, @Nullable CrapsSync sync, CrapsPub pub, double t) {
		CrapsBeats b = c.beats;
		CrapsPub before = c.before;
		if (sync == null || before == null || c.roll == null || t >= b.IDLE) {
			drawStatic(s, pub.stacks(), null);
			return;
		}
		if (t < b.CHIPS) {
			drawStatic(s, before.stacks(), null);
			return;
		}
		int total = sync.d1() + sync.d2();
		List<Spot> arriving = new ArrayList<>();
		double[] bank = CrapsBoardGeometry.bank();
		int wave = 0;
		for (CrapsPub.Stack st : before.stacks()) {
			if (pub.stacks().contains(st)) continue;
			int o = outcome(st.kind(), st.point(), sync.pointBefore(), total);
			double[] at = CrapsBoardGeometry.stack(st.kind(), st.point(), false, st.seat());
			float[] seat = seatSpot(st.seat(), 8);
			double[] railPx = {(seat[0] - (0.5 - CrapsBoardGeometry.W / 2)) / CrapsBoardGeometry.SCALE, CrapsBoardGeometry.PRINT_H + 8};
			long amount = st.flat() + st.odds();
			switch (o) {
				case -1 -> slide(s, at, bank, amount, t, b.CHIPS + (wave++) * 40 * b.f, 260 * b.f, true);
				case 3 -> {
					// travels to the box of the rolled number (the new come stack there waits for it)
					double[] to = CrapsBoardGeometry.stack(st.kind(), total, false, st.seat());
					double p = clamp01((t - b.TRAVEL) / (400 * b.f));
					if (p < 1) {
						double e = Ease.IN_OUT_CUBIC.apply(p);
						s.stack(CrapsBoardGeometry.x(at[0] + (to[0] - at[0]) * e), CrapsBoardGeometry.z(at[1] + (to[1] - at[1]) * e), amount);
					}
					arriving.add(new Spot(st.seat(), st.kind(), total));
				}
				case 1 -> {
					// a payout stack from the bank beside the bet, then both go to the player's rail
					double[] col = {at[0] + 6, at[1]};
					if (t < b.COLLECT) {
						s.stack(CrapsBoardGeometry.x(at[0]), CrapsBoardGeometry.z(at[1]), amount);
						if (t >= b.TRAVEL) slide(s, bank, col, st.flat(), t, b.TRAVEL, 220 * b.f, false);
					} else {
						slide(s, at, railPx, amount, t, b.COLLECT, 380 * b.f, true);
						slide(s, col, railPx, st.flat(), t, b.COLLECT + 30 * b.f, 380 * b.f, true);
					}
				}
				default -> slide(s, at, railPx, amount, t, b.TRAVEL, 300 * b.f, true);
			}
		}
		drawStatic(s, pub.stacks(), arriving.isEmpty() || t >= b.TRAVEL + 400 * b.f ? null : arriving);
	}

	private static void drawStatic(State s, List<CrapsPub.Stack> stacks, @Nullable List<Spot> skip) {
		for (CrapsPub.Stack st : stacks) {
			if (skip != null && skip.contains(new Spot(st.seat(), st.kind(), st.point()))) continue;
			double[] p = CrapsBoardGeometry.stack(st.kind(), st.point(), false, st.seat());
			s.stack(CrapsBoardGeometry.x(p[0]), CrapsBoardGeometry.z(p[1]), st.flat());
			if (st.odds() > 0) {
				double[] o = CrapsBoardGeometry.stack(st.kind(), st.point(), true, st.seat());
				s.stack(CrapsBoardGeometry.x(o[0]), CrapsBoardGeometry.z(o[1]), st.odds());
			}
		}
	}

	/** A stack sliding flat from {@code a} to {@code b} (print px); it rests at a before {@code start}, gone after when asked. */
	private static void slide(State s, double[] a, double[] b, long amount, double t, double start, double dur, boolean goneAfter) {
		double p = clamp01((t - start) / dur);
		if (p >= 1 && goneAfter) return;
		double e = Ease.IN_OUT_CUBIC.apply(p);
		s.stack(CrapsBoardGeometry.x(a[0] + (b[0] - a[0]) * e), CrapsBoardGeometry.z(a[1] + (b[1] - a[1]) * e), amount, p > 0.85 && goneAfter ? 0.6f : 1f, 0);
	}

	private static double clamp01(double v) {
		return Math.max(0, Math.min(1, v));
	}

	// ---- the rail groove (full tables) --------------------------------------------------------------------------------

	/** A chip groove along the top of the players' rail of a full table (design coordinates of the footprint rail). */
	private static void groove(State s) {
		FeltFrame f = s.frame;
		s.groove = s.full && f != null && !f.compact();
		if (!s.groove) return;
		float x0 = (float) (f.cx() - f.w() / 2 + 0.25);
		float x1 = (float) (f.cx() + f.w() / 2 - 0.25);
		float zc = (float) (f.cz() + f.d() / 2 + f.rail() / 2);
		float hw = (float) (f.rail() * 0.28);
		float y = (float) (FeltFrame.RAIL_TOP + 0.0015);
		s.grooveX0 = s.designX(x0);
		s.grooveX1 = s.designX(x1);
		s.grooveZ0 = s.designZ(zc - hw);
		s.grooveZ1 = s.designZ(zc + hw);
		s.grooveY = s.designY(y);
	}

	// ---- submit -----------------------------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		super.submit(s, pose, out, camera);
		if (!s.print || !s.visibleBoard()) return;
		int light = s.lightCoords;
		int felt = s.feltLight;
		pose.pushPose();
		feltPose(pose, s); // W2: FACING + design → full 3×2 felt / compact block top (MT-DESIGN §2)
		float y = BoardGeometry.TOP + BoardGeometry.LAYER;
		float x0 = CrapsBoardGeometry.x(0), z0 = CrapsBoardGeometry.z(0);
		float x1 = CrapsBoardGeometry.x(CrapsBoardGeometry.PRINT_W), z1 = CrapsBoardGeometry.z(CrapsBoardGeometry.PRINT_H);
		// translucent: the print has soft shadows and a tinted Don't Pass band (cutout would turn them black)
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(PRINT), (p, vc) -> WorldQuads.topRect(p, vc, x0, z0, x1, z1, y, 0, 0,
			CrapsBoardGeometry.PRINT_W, CrapsBoardGeometry.PRINT_H, CrapsBoardGeometry.PRINT_W, CrapsBoardGeometry.PRINT_H, 0xFFFFFFFF, felt));
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(WALL), (p, vc) -> wall(p, vc, y, light));
		if (s.flashA[0] > 0 || s.flashA[1] > 0) {
			out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WALL), (p, vc) -> flashes(p, vc, s, y));
		}
		if (s.groove) {
			float gx0 = s.grooveX0, gx1 = s.grooveX1, gz0 = s.grooveZ0, gz1 = s.grooveZ1, gy = s.grooveY;
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(WALL), (p, vc) -> WorldQuads.topRect(p, vc, gx0, gz0, gx1, gz1, gy, 32, 12, 16, 4, 64,
				16, 0xFFFFFFFF, light));
		}
		// the puck: turns over about the horizontal axis across its travel
		pose.pushPose();
		pose.translate(s.puckX, y + 0.006f + s.puckY, s.puckZ);
		if (s.puckFlip > 0 && s.puckFlip < 1) {
			float ang = (float) Math.toRadians(180 * s.puckFlip);
			pose.rotateAround(new org.joml.Quaternionf().rotateAxis(ang, -s.puckDirZ, 0, s.puckDirX), 0f, 0f, 0f);
		}
		// before the turn the start face is up (the end face under it); once turned the end face is up
		boolean turned = s.puckFlip >= 1;
		boolean topOn = turned ? s.puckToOn : s.puckFromOn;
		boolean botOn = !topOn;
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(PUCK), (p, vc) -> puckDisc(p, vc, topOn, botOn, light));
		pose.popPose();
		if (s.dice) {
			for (int d = 0; d < 2; d++) {
				if (s.shadows) DiceWorldDraw.shadow(pose, out, s.die[d], H, y, light);
				DiceWorldDraw.die(pose, out, s.die[d], H, y, light); // DCa's 16-px face cube (the atlas pips)
			}
		}
		if (s.stick) {
			float sx = s.stickX, sz = s.stickZ;
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(WALL), (p, vc) -> dowel(p, vc, 0.5f, y + 0.32f, z0 - 0.35f, sx, y + 0.012f, sz, 0.007f, light));
		}
		pose.popPose();
	}

	/** The raised back wall: the pyramid face toward the players (tiled every 16 print px), the padded top, end caps. */
	private static void wall(PoseStack.Pose p, VertexConsumer vc, float y, int light) {
		Box w = CrapsBoardGeometry.WALL;
		float zf = CrapsBoardGeometry.z(w.bottom());
		float zb = CrapsBoardGeometry.z(w.y()) - 0.01f;
		float top = y + CrapsBoardGeometry.WALL_HEIGHT;
		for (int px = w.x(); px < w.right(); px += 16) {
			int tw = Math.min(16, w.right() - px);
			float a = CrapsBoardGeometry.x(px);
			float b = CrapsBoardGeometry.x(px + tw);
			// inner face (+z)
			WorldQuads.vertex(p, vc, a, top, zf, 0, 0, 0xFFFFFFFF, light, 0, 0, 1);
			WorldQuads.vertex(p, vc, a, y, zf, 0, 8 / 16f, 0xFFFFFFFF, light, 0, 0, 1);
			WorldQuads.vertex(p, vc, b, y, zf, tw / 64f, 8 / 16f, 0xFFFFFFFF, light, 0, 0, 1);
			WorldQuads.vertex(p, vc, b, top, zf, tw / 64f, 0, 0xFFFFFFFF, light, 0, 0, 1);
			// padded top
			WorldQuads.topRect(p, vc, a, zb, b, zf, top, 0, 8, tw, 4, 64, 16, 0xFFFFFFFF, light);
		}
		float xa = CrapsBoardGeometry.x(w.x());
		float xb = CrapsBoardGeometry.x(w.right());
		// end caps
		WorldQuads.vertex(p, vc, xa, top, zb, 0, 8 / 16f, 0xFFB0B0B0, light, -1, 0, 0);
		WorldQuads.vertex(p, vc, xa, y, zb, 0, 12 / 16f, 0xFFB0B0B0, light, -1, 0, 0);
		WorldQuads.vertex(p, vc, xa, y, zf, 6 / 64f, 12 / 16f, 0xFFB0B0B0, light, -1, 0, 0);
		WorldQuads.vertex(p, vc, xa, top, zf, 6 / 64f, 8 / 16f, 0xFFB0B0B0, light, -1, 0, 0);
		WorldQuads.vertex(p, vc, xb, top, zf, 0, 8 / 16f, 0xFFB0B0B0, light, 1, 0, 0);
		WorldQuads.vertex(p, vc, xb, y, zf, 0, 12 / 16f, 0xFFB0B0B0, light, 1, 0, 0);
		WorldQuads.vertex(p, vc, xb, y, zb, 6 / 64f, 12 / 16f, 0xFFB0B0B0, light, 1, 0, 0);
		WorldQuads.vertex(p, vc, xb, top, zb, 6 / 64f, 8 / 16f, 0xFFB0B0B0, light, 1, 0, 0);
	}

	/** The hit diamond: a white cell for one frame, then gold fading (emissive). */
	private static void flashes(PoseStack.Pose p, VertexConsumer vc, State s, float y) {
		float zf = CrapsBoardGeometry.z(CrapsBoardGeometry.WALL.bottom()) + 0.0015f;
		float top = y + CrapsBoardGeometry.WALL_HEIGHT;
		float half = 2 * CrapsBoardGeometry.SCALE;
		for (int i = 0; i < 2; i++) {
			if (s.flashA[i] <= 0) continue;
			int a = Math.round(255 * s.flashA[i]);
			int u = s.flashHit[i] ? 0 : 16;
			float x = s.flashX[i];
			int argb = (a << 24) | 0xFFFFFF;
			WorldQuads.vertex(p, vc, x - half, top, zf, u / 64f, 12 / 16f, argb, WorldQuads.FULL_BRIGHT, 0, 0, 1);
			WorldQuads.vertex(p, vc, x - half, y, zf, u / 64f, 1, argb, WorldQuads.FULL_BRIGHT, 0, 0, 1);
			WorldQuads.vertex(p, vc, x + half, y, zf, (u + 16) / 64f, 1, argb, WorldQuads.FULL_BRIGHT, 0, 0, 1);
			WorldQuads.vertex(p, vc, x + half, top, zf, (u + 16) / 64f, 12 / 16f, argb, WorldQuads.FULL_BRIGHT, 0, 0, 1);
		}
	}

	/** The puck: two faces back to back (top / bottom), ON white, OFF black. */
	private static void puckDisc(PoseStack.Pose p, VertexConsumer vc, boolean topOn, boolean bottomOn, int light) {
		float r = PUCK_R;
		float tu = topOn ? 0 : 16;
		float bu = bottomOn ? 0 : 16;
		WorldQuads.vertex(p, vc, -r, 0.0015f, -r, tu / 32f, 0, 0xFFFFFFFF, light, 0, 1, 0);
		WorldQuads.vertex(p, vc, -r, 0.0015f, r, tu / 32f, 1, 0xFFFFFFFF, light, 0, 1, 0);
		WorldQuads.vertex(p, vc, r, 0.0015f, r, (tu + 16) / 32f, 1, 0xFFFFFFFF, light, 0, 1, 0);
		WorldQuads.vertex(p, vc, r, 0.0015f, -r, (tu + 16) / 32f, 0, 0xFFFFFFFF, light, 0, 1, 0);
		WorldQuads.vertex(p, vc, -r, -0.0015f, r, bu / 32f, 0, 0xFFFFFFFF, light, 0, -1, 0);
		WorldQuads.vertex(p, vc, -r, -0.0015f, -r, bu / 32f, 1, 0xFFFFFFFF, light, 0, -1, 0);
		WorldQuads.vertex(p, vc, r, -0.0015f, -r, (bu + 16) / 32f, 1, 0xFFFFFFFF, light, 0, -1, 0);
		WorldQuads.vertex(p, vc, r, -0.0015f, r, (bu + 16) / 32f, 0, 0xFFFFFFFF, light, 0, -1, 0);
	}

	/** The stick: a thin dowel from the stickman's hand to the hook, two crossed double-sided quads. */
	private static void dowel(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float r, int light) {
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-4f) return;
		dx /= len;
		dy /= len;
		dz /= len;
		float hx = -dz, hz = dx;
		float hl = (float) Math.sqrt(hx * hx + hz * hz);
		if (hl < 1e-4f) {
			hx = 1;
			hz = 0;
			hl = 1;
		}
		hx = hx / hl * r;
		hz = hz / hl * r;
		float vx = dy * hz, vy = dz * hx - dx * hz, vz = -dy * hx;
		float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (vl < 1e-6f) return;
		vx = vx / vl * r;
		vy = vy / vl * r;
		vz = vz / vl * r;
		float[][] sides = {{hx, 0, hz}, {vx, vy, vz}};
		for (float[] o : sides) {
			for (int w = 0; w < 2; w++) {
				float sg = w == 0 ? 1 : -1;
				WorldQuads.vertex(p, vc, ax - o[0] * sg, ay - o[1] * sg, az - o[2] * sg, 48 / 64f, 12 / 16f, 0xFFFFFFFF, light, 0, 1, 0);
				WorldQuads.vertex(p, vc, bx - o[0] * sg, by - o[1] * sg, bz - o[2] * sg, 48 / 64f, 1, 0xFFFFFFFF, light, 0, 1, 0);
				WorldQuads.vertex(p, vc, bx + o[0] * sg, by + o[1] * sg, bz + o[2] * sg, 1, 1, 0xFFFFFFFF, light, 0, 1, 0);
				WorldQuads.vertex(p, vc, ax + o[0] * sg, ay + o[1] * sg, az + o[2] * sg, 1, 12 / 16f, 0xFFFFFFFF, light, 0, 1, 0);
			}
		}
	}

	/** Test probe: the table whose last filled state is recorded ({@link #probeDice}, {@link #probeFaces}). */
	public static volatile net.minecraft.core.@Nullable BlockPos probePos;
	/** Test probe: 1 = dice drawn in the last frame of {@link #probePos}, 0 = none, -1 = not seen. */
	public static volatile int probeDice = -1;
	/** Test probe: the drawn top faces and whether each cube's lowest point is above the felt. */
	public static final int[] probeFaces = new int[2];
	public static volatile boolean probeAboveFelt;

	private static void probe(CrapsTableBlockEntity be, State s) {
		if (!be.getBlockPos().equals(probePos)) return;
		probeDice = s.dice ? 1 : 0;
		boolean above = true;
		for (int d = 0; d < 2; d++) {
			probeFaces[d] = CubeGroup.faceUp(s.die[d].q);
			above &= s.die[d].zc + 1e-9 >= Quat.support(s.die[d].q) && s.die[d].zLow >= -0.02;
		}
		probeAboveFelt = above;
	}

	/** Test hook: the dice of a filled state are drawn (LOD C spoiler check). */
	public static boolean diceDrawn(State s) {
		return s.dice;
	}

	/** Test hook: die {@code d} of a filled state (design coordinates ÷ the die half-edge, MODEL orientation). */
	public static DieSample die(State s, int d) {
		return s.die[d];
	}
}
