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
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.RotorModel;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSpin;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSync;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import dev.nezo.burmaldaholic.games.roulette.present.RoulettePub;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The roulette table in the world (docs/architecture/ui-v2.md §7.6; ROULETTE-ANIM-SPEC §4; concept
 * {@code world_roulette_concept.png}): the printed layout with every bettor's chips, the winning cell framed in gold,
 * the floating board and the seat plates — and the WHEEL as geometry sunk into the furniture's bowl (centred on
 * {@link FeltFrame#wheelCx}/{@code wheelCz} at the bowl floor, sized to {@code wheelDiameter}; VQA-INWORLD RO 1): a
 * turning rotor (the 208 head art), a stator cone, the ball track and its wall, 8 brass diamonds and a turret. The ball
 * is a camera-facing billboard with height and a contact shadow, on the SAME {@link RouletteSpin} as every screen
 * (RouletteSync v2), so the rotor never snaps and the ball rests in the drawn pocket for everybody.
 *
 * <p>LOD by camera distance: A ≤ 8 blocks (ball, shadow, streak, diamond flash, pocket glow, dolly, chip motion), B ≤ 16
 * (ball + shadow, dolly, chips appear / fade), C ≤ 32 (rotor only: the ball is hidden until it has come to rest, so a far
 * viewer never sees the result before the screen does, D12). Sounds within 24 blocks through the spin's event cursor;
 * the rolling loop for the nearest spinning table within 12 blocks only (a WORLD loop), muted while the viewer has this
 * table's screen open.
 */
public final class RouletteBoardRenderer extends BoardRenderer<RouletteTableBlockEntity, RouletteBoardRenderer.State> {
	private static final Identifier[] ROTOR = {tex("rotor_village"), tex("rotor_bastion"), tex("rotor_end")};
	private static final Identifier[] BOWL = {tex("bowl3d_village"), tex("bowl3d_bastion"), tex("bowl3d_end")};
	private static final Identifier[] PRINT = {tex("layout_w_village"), tex("layout_w_bastion"), tex("layout_w_end")};
	private static final double AUDIO_RANGE_SQ = 24 * 24;
	private static final double LOOP_RANGE = 12;
	/** Seat colours of the stacks (REDESIGN tables: per seat index). */
	private static final int[] SEAT_TINT = {0xFF4FC3F7, 0xFFFFB74D, 0xFFBA68C8, 0xFF81C784, 0xFFF06292, 0xFFFFF176, 0xFFA1887F, 0xFF90A4AE};
	private static final float Y = BoardGeometry.TOP;
	private static final int SEGMENTS = 24;

	/** Client GameTests: whether the last frame drew a ball, and its angle relative to the rotor, per table (block pos). */
	public static final Map<Long, double[]> TEST_BALL = new java.util.concurrent.ConcurrentHashMap<>();
	/** Client GameTests switch the probe on (no per-frame work in production). */
	public static volatile boolean testProbe;

	public RouletteBoardRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	private static Identifier tex(String name) {
		return Burmaldaholic.id("textures/entity/roulette/" + name + ".png");
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(RouletteModule.TABLE.blockEntityType(), RouletteBoardRenderer::new);
		BlockEntityRendererRegistry.register(RouletteModule.HIGH_ROLLER_TABLE.blockEntityType(), RouletteBoardRenderer::new);
	}

	/** Per table cache: the current spin, its sound cursor and the chip motion clocks. */
	private static final class Cache {
		@Nullable RouletteSync sync;
		@Nullable RouletteSpin spin;
		int seq = Integer.MIN_VALUE;
		int result = Integer.MIN_VALUE;
		final EventCursor cursor = new EventCursor(new MotionEvent[0]);
		double cursorAt = -1;
		final RouletteSpin.Sample sample = new RouletteSpin.Sample();
		final RouletteSpin.Sample trail = new RouletteSpin.Sample();
		/** stack key → level ms it first appeared (drop) */
		final Map<String, Double> seen = new HashMap<>();
		double resultStart = Double.NaN;
	}

	public static final class State extends BoardState {
		boolean active;
		/** wheel: frame (full = footprint-local at the bowl, else design), centre, floor, Rw */
		boolean bowlFull;
		float wx, wy, wz, rw;
		double rotor;
		boolean ball;
		float ballAlpha;
		double ballAngle, ballR, ballZ;
		/** streak billboards (LOD A, fast ball): angle / radius / height of 2 earlier samples */
		final double[] trail = new double[6];
		int trailCount;
		/** deflector flash: diamond index (-1 = none) and age 0..1 */
		int diamond = -1;
		float diamondAge;
		/** pocket glow on the rotor (-1 = none) */
		int glow = -1;
		float glowAlpha;
		/** dolly on the winning cell: number (-1 = none), drop (blocks) */
		int dolly = -1;
		float dollyDrop, dollyAlpha = 1;
		/** winning cell (-1 = none) */
		int winCell = -1;
		/** the table's own small stacks on the layout */
		final float[] sx = new float[RoulettePub.MAX_STACKS], sz = new float[RoulettePub.MAX_STACKS], sy = new float[RoulettePub.MAX_STACKS];
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
		return 32; // the rotor turns as far as the board is drawn; the ball follows the LOD rules
	}

	@Override
	protected void fill(RouletteTableBlockEntity be, State s, float partialTick) {
		RouletteSync sync = be.clientSync();
		RoulettePub pub = be.clientPub();
		s.active = sync != null;
		s.stacks = 0;
		s.winCell = -1;
		s.dolly = -1;
		s.glow = -1;
		s.diamond = -1;
		s.trailCount = 0;
		boolean fallback = s.felt == Felt.FALLBACK;
		FeltFrame f = s.frame;
		s.bowlFull = s.full && f != null && f.hasWheel();
		if (s.bowlFull) {
			s.wx = (float) f.wheelCx();
			s.wz = (float) f.wheelCz();
			s.wy = RouletteWorld.BOWL_FLOOR_Y;
			s.rw = RouletteWorld.rw(f.wheelDiameter());
		} else {
			s.wx = 0.5f + (fallback ? 0 : RouletteWorld.WHEEL_X);
			s.wz = 0.5f + (fallback ? 0 : RouletteWorld.WHEEL_Z);
			s.wy = Y + 0.001f;
			s.rw = (fallback ? 0.42f : RouletteWorld.WHEEL_R) * 0.93f;
		}
		s.content(0.5f + RouletteWorld.WHEEL_X, Y + 0.06f, 0.5f + RouletteWorld.WHEEL_Z, RouletteWorld.WHEEL_R);
		int shown = -1;
		Cache c = s.cache(Cache::new);
		if (sync != null) shown = wheel(be, s, sync, c);
		// stacks on the layout (not on the 1 × 1 fallback: no room for a layout there)
		if (!fallback) stacks(s, sync, pub, shown, c);
		lines(be, s, sync, pub, shown >= 0, shown);
		if (testProbe) TEST_BALL.put(be.getBlockPos().asLong(), new double[] {s.ball ? 1 : 0, s.ballAngle - s.rotor, s.ballR, Math.sqrt(s.distSq)});
		plates(be, s, pub);
	}

	/** The rotor / ball / effects of this frame; returns the number the board may name (the ball has come to rest), else -1. */
	private static int wheel(RouletteTableBlockEntity be, State s, RouletteSync sync, Cache c) {
		RotorModel model = sync.rotorModel();
		double t = s.levelMs - sync.launchTick() * 50.0;
		double dist = Math.sqrt(s.distSq);
		RouletteWorld.Lod lod = RouletteWorld.Lod.of(dist);
		boolean reduce = FxSettings.reduceMotion();
		boolean live = sync.phase() != RouletteSync.BETTING && sync.seq() > 0;
		c.sync = sync;
		if (!live) {
			c.spin = null;
			c.seq = Integer.MIN_VALUE;
			int last = sync.lastResult();
			s.rotor = reduce ? (last >= 0 ? -RouletteSpin.pocketAngle(last) : 0) : model.angle(Math.max(0, t));
			s.ball = last >= 0;
			s.ballAlpha = 1;
			s.ballAngle = s.rotor + (last >= 0 ? RouletteSpin.pocketAngle(last) : 0);
			s.ballR = RouletteSpin.R_POCKET;
			s.ballZ = 0;
			double sincePhase = s.levelMs - sync.phaseStart() * 50.0;
			if (last >= 0 && lod != RouletteWorld.Lod.C && sincePhase < 1500 && !reduce) {
				s.glow = last;
				s.glowAlpha = (float) (1 - sincePhase / 1500);
			}
			if (last >= 0 && sincePhase < 300) {
				s.dolly = last;
				s.dollyDrop = 0;
				s.dollyAlpha = (float) (1 - sincePhase / 300);
			}
			return -1;
		}
		int result = sync.phase() == RouletteSync.NO_MORE_BETS ? -1 : sync.result();
		if (c.spin == null || c.seq != sync.seq() || c.result != result) {
			boolean newSeq = c.seq != sync.seq();
			long spinStart = result >= 0 ? sync.spinStart() : sync.launchTick() + RouletteSpin.NMB_TICKS;
			// S1: a result that reaches this client late continues from where the drawn orbit is (no jump back)
			double join = !newSeq && c.spin != null && !c.spin.solved() ? c.cursorAt : Double.NaN;
			c.spin = RouletteSpin.build(result, sync.seed(), sync.lastResult(), sync.launchTick(), spinStart, sync.spinTicks(), model, join);
			c.seq = sync.seq();
			c.result = result;
			c.cursor.bind(c.spin.events());
			if (!newSeq) c.cursor.reset(c.cursorAt, true);
			else c.cursor.reset(Math.max(t, 0) - (t < EventCursor.LATE_MS ? 1e9 : 0), true);
		}
		RouletteSpin sp = c.spin;
		RouletteSpin.Sample smp = sp.sample(t, c.sample);
		boolean rest = sp.settled(t);
		s.rotor = reduce ? -RouletteSpin.pocketAngle(rest ? sp.result : Math.max(0, sync.lastResult())) : smp.rotor;
		// LOD C (and reduced motion): no ball before it rests in its pocket (D12: a far viewer never sees the result early)
		boolean hideMoving = lod == RouletteWorld.Lod.C || reduce;
		if (hideMoving && !rest) {
			s.ball = false;
		} else if (hideMoving) {
			s.ball = true;
			s.ballAlpha = reduce ? (float) Math.min(1, (t - sp.restMs()) / 300) : 1;
			s.ballAngle = s.rotor + RouletteSpin.pocketAngle(sp.result);
			s.ballR = RouletteSpin.R_POCKET;
			s.ballZ = 0;
		} else {
			s.ball = smp.visible;
			s.ballAlpha = (float) smp.alpha;
			s.ballAngle = smp.abs;
			s.ballR = smp.r;
			s.ballZ = smp.z;
			if (lod == RouletteWorld.Lod.A && smp.omega > 400) {
				for (int i = 0; i < 2; i++) {
					RouletteSpin.Sample q = sp.sample(t - 8 * (i + 1), c.trail);
					s.trail[3 * i] = q.abs;
					s.trail[3 * i + 1] = q.r;
					s.trail[3 * i + 2] = q.z;
				}
				s.trailCount = 2;
			}
			if (lod == RouletteWorld.Lod.A && sp.deflectorHit()) {
				double age = t - sp.deflectorMs();
				if (age >= 0 && age < 120) {
					s.diamond = sp.diamond();
					s.diamondAge = (float) (age / 120);
				}
			}
		}
		if (rest && lod != RouletteWorld.Lod.C && !reduce) {
			s.glow = sp.result;
			s.glowAlpha = 1;
		}
		// sounds: positional, within 24 blocks; never for the player at this table's screen (they hear the screen)
		boolean ownScreen = viewingOwnScreen(be);
		if (s.distSq <= AUDIO_RANGE_SQ && !ownScreen && lod != RouletteWorld.Lod.C) {
			double bx = be.getBlockPos().getX() + 0.5, by = be.getBlockPos().getY() + 1.0, bz = be.getBlockPos().getZ() + 0.5;
			c.cursor.advance(t, (e, silent) -> {
				if (!silent) voice(e, bx, by, bz, reduce);
			});
		} else {
			c.cursor.advance(t, null);
		}
		c.cursorAt = t;
		if (!ownScreen && dist <= LOOP_RANGE && t >= RouletteSpin.LAUNCH_MS && (!sp.solved() || t < sp.dropMs())) {
			double v = smp.omega / RouletteSpin.OMEGA_LAUNCH;
			double fall = Math.max(0.2, 1 - dist / LOOP_RANGE);
			WorldRoll.offer(be.getBlockPos().asLong(), dist, 1.4 + 0.6 * v, (0.05 + 0.13 * v) * fall * (reduce ? 0.5 : 1));
		}
		return rest ? sp.result : -1;
	}

	private static void voice(MotionEvent e, double x, double y, double z, boolean reduce) {
		float st = e.strength();
		switch (e.kind()) {
			case RouletteSpin.EV_LAUNCH -> FxSounds.playAt("roulette_spin", x, y, z, 0.8f, 1f);
			case RouletteSpin.EV_PUSH -> {
				if (!reduce) FxSounds.playAt("roulette_rotor_push", x, y, z, 0.7f, 1f);
			}
			case RouletteSpin.EV_DEFLECTOR -> {
				if (!reduce) FxSounds.playAt("roulette_deflector", x, y, z, 0.8f, 1f);
			}
			case RouletteSpin.EV_CONTACT -> FxSounds.playAt("roulette_ball_drop", x, y, z, 0.8f, 1f);
			case RouletteSpin.EV_FRET, RouletteSpin.EV_KICKBACK -> {
				if (!reduce) FxSounds.playAt("roulette_ball_bounce", x, y, z, 0.2f + 0.6f * st, 1.9f - 0.1f * e.aux());
			}
			case RouletteSpin.EV_FLOOR -> {
				if (!reduce) FxSounds.playAt("roulette_floor", x, y, z, 0.25f + 0.55f * st, 1f);
			}
			case RouletteSpin.EV_REST -> FxSounds.playAt("roulette_ball_settle", x, y, z, 0.8f, 1f);
			default -> { }
		}
	}

	/** The rolling-ball loop of the nearest spinning table (one WORLD loop client-wide). */
	private static final class WorldRoll {
		private static long pos;
		private static double dist = Double.MAX_VALUE, pitch = 1, volume;
		private static long at;
		private static FxSounds.Handle handle = FxSounds.Handle.NONE;

		static void offer(long p, double d, double pi, double vol) {
			long now = Util.getMillis();
			if (now - at > 120 || p == pos || d < dist) {
				pos = p;
				dist = d;
				pitch = pi;
				volume = vol;
				at = now;
			}
			if (!handle.active()) {
				handle = FxSounds.loop("roulette_roll", () -> pitch, () -> volume, () -> Util.getMillis() - at < 150, FxSounds.LoopPriority.WORLD);
			}
		}
	}

	/** The table's stacks: new ones drop (A), losers slide toward the dealer and fade (A) or fade (B, C). */
	private static void stacks(State s, @Nullable RouletteSync sync, RoulettePub pub, int shown, Cache c) {
		boolean resultPhase = sync != null && sync.phase() == RouletteSync.RESULT;
		int result = sync != null && resultPhase ? sync.result() : -1;
		if (resultPhase && result >= 0) {
			s.winCell = result;
			if (Double.isNaN(c.resultStart)) c.resultStart = s.levelMs;
		} else {
			c.resultStart = Double.NaN;
		}
		double rMs = resultPhase ? s.levelMs - c.resultStart : 0;
		RouletteWorld.Lod lod = RouletteWorld.Lod.of(Math.sqrt(s.distSq));
		boolean motion = lod == RouletteWorld.Lod.A && !FxSettings.reduceMotion();
		if (resultPhase && result >= 0 && rMs >= 400) {
			s.dolly = result;
			double u = Math.min(1, (rMs - 400) / 250);
			s.dollyDrop = motion ? (float) (0.1 * (1 - Ease.OUT_BOUNCE.apply(u))) : 0;
			s.dollyAlpha = 1;
		}
		int loser = 0;
		Map<String, Double> seen = c.seen;
		java.util.Set<String> live = new java.util.HashSet<>();
		for (RoulettePub.Stack st : pub.stacks()) {
			if (s.stacks >= RoulettePub.MAX_STACKS) break;
			Spot spot = st.spotValue();
			float[] at = RouletteWorld.stack(spot);
			int i = s.stacks++;
			String key = st.owner() + ":" + spot.key() + ":" + st.amount();
			live.add(key);
			Double first = seen.get(key);
			if (first == null) {
				first = seen.isEmpty() && s.levelMs > 0 ? s.levelMs - 1e6 : s.levelMs;
				seen.put(key, first);
			}
			double age = s.levelMs - first;
			s.sx[i] = 0.5f + at[0];
			s.sz[i] = 0.5f + at[1];
			s.sy[i] = motion && age < 200 ? (float) (0.06 * (1 - Ease.OUT_CUBIC.apply(age / 200))) : 0;
			s.sa[i] = st.amount();
			int seat = pub.players().get(st.owner()).seat();
			s.st[i] = seat >= 0 ? SEAT_TINT[seat % SEAT_TINT.length] : 0xFFB0BEC5;
			s.sAlpha[i] = 1;
			if (resultPhase && result >= 0 && !spot.covers(result)) {
				// the croupier rakes the losers toward the dealer (−z) from 650 ms, 40 ms apart, fading over 400 ms
				double u = Math.max(0, Math.min(1, (rMs - 650 - 40 * loser++) / 400));
				if (motion) s.sz[i] -= (float) (0.25 * Ease.IN_OUT_CUBIC.apply(u));
				s.sAlpha[i] = (float) Math.max(0, 1 - u);
				if (s.sAlpha[i] <= 0.02) s.stacks--;
			}
		}
		seen.keySet().retainAll(live);
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
		return mc.gui.screen() instanceof MenuAccess<?> sc && sc.getMenu() instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos());
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		super.submit(s, pose, out, camera); // felt, kit stacks (none), plates, board
		if (!s.visibleBoard() || !s.active) return;
		int light = s.lightCoords;
		boolean fallback = s.felt == Felt.FALLBACK;
		pose.pushPose();
		feltPose(pose, s); // W2: FACING + design → full 3×2 felt / compact block top (MT-DESIGN §2)
		if (!fallback) {
			float x0 = 0.5f + RouletteWorld.LAYOUT_X;
			float z0 = 0.5f + RouletteWorld.LAYOUT_Z;
			float x1 = x0 + RouletteWorld.LAYOUT_W;
			float z1 = z0 + RouletteWorld.LAYOUT_D;
			int w = RouletteWorld.PRINT.width();
			int h = RouletteWorld.PRINT.height();
			int printLight = s.full ? s.feltLight : light;
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(PRINT[s.theme]), (p, vc) -> {
				WorldQuads.topRect(p, vc, x0, z0, x1, z1, Y + 0.002f, 0, 0, w, h, w, h, 0xFFFFFFFF, printLight);
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
				out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.CHIPS), (p, vc) -> {
					int[] d = new int[RouletteWorld.MAX_DISCS];
					for (int i = 0; i < n; i++) RouletteChips.stack(p, vc, s.sx[i], s.sz[i], s.sa[i], s.st[i], s.sAlpha[i], d, light, s.sy[i]);
				});
			}
			if (s.dolly >= 0) dolly(s, pose, out, camera, light);
		}
		pose.popPose();
		// the wheel: footprint-local in the furniture's bowl (full table) or on the design felt (compact)
		pose.pushPose();
		if (s.bowlFull) pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0.5f, 0f, 0.5f);
		else feltPose(pose, s);
		pose.translate(s.wx, s.wy, s.wz);
		wheel3d(s, pose, out, camera, light);
		pose.popPose();
	}

	/** The dolly: a gold pawn billboard on the winning cell, dropping onto it. */
	private static void dolly(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera, int light) {
		float[] at = RouletteWorld.cell(s.dolly);
		Identifier bowl = BOWL[s.theme];
		int a = (int) (s.dollyAlpha * 255) << 24 | 0xFFFFFF;
		pose.pushPose();
		pose.translate(0.5f + at[0], Y + 0.04f + s.dollyDrop, 0.5f + at[1]);
		billboard(pose, s, camera);
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(bowl), (p, vc) -> sprite(p, vc, 0.035f, 0.0525f, 0, 40, 16, 24, a, light));
		pose.popPose();
	}

	/** Undoes the board's FACING rotation and turns to the camera (a camera-facing quad at the pose origin). */
	private static void billboard(PoseStack pose, State s, CameraRenderState camera) {
		pose.rotateAround(Axis.YP.rotationDegrees(s.facing.toYRot()), 0f, 0f, 0f);
		pose.rotateAround(camera.orientation, 0f, 0f, 0f);
	}

	/** A camera-facing quad of {@code w × h} centred on x, bottom-centred on y = 0... centred (sprite region u, v, uw, vh of the 64² atlas). */
	private static void sprite(PoseStack.Pose p, VertexConsumer vc, float w, float h, int u, int v, int uw, int vh, int argb, int light) {
		float x0 = -w / 2, x1 = w / 2, y0 = -h / 2, y1 = h / 2;
		float u0 = u / 64f, u1 = (u + uw) / 64f, v0 = v / 64f, v1 = (v + vh) / 64f;
		WorldQuads.vertex(p, vc, x0, y1, 0, u0, v0, argb, light, 0, 0, 1);
		WorldQuads.vertex(p, vc, x0, y0, 0, u0, v1, argb, light, 0, 0, 1);
		WorldQuads.vertex(p, vc, x1, y0, 0, u1, v1, argb, light, 0, 0, 1);
		WorldQuads.vertex(p, vc, x1, y1, 0, u1, v0, argb, light, 0, 0, 1);
	}

	/**
	 * The wheel at the pose origin (the bowl floor's centre): rotor disc + turret, stator cone (24 quads), ball track and
	 * its wall, 8 diamonds; the ball billboard with its shadow and streak; the pocket glow and the deflector flash.
	 */
	private static void wheel3d(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera, int light) {
		float rw = s.rw;
		float k = s.bowlFull ? 1f : RouletteWorld.COMPACT_HEIGHT;
		Identifier bowl = BOWL[s.theme];
		float yRotor = RouletteWorld.Y_ROTOR * k;
		float yFoot = RouletteWorld.Y_CONE_FOOT * k;
		float yTrack = RouletteWorld.Y_TRACK * k;
		float yWall = RouletteWorld.Y_WALL * k;
		float rRotor = RouletteWorld.R_ROTOR * rw;
		float rFoot = RouletteWorld.R_CONE_FOOT * rw;
		float rTrack = RouletteWorld.R_TRACK_IN * rw;
		// static bowl parts (cone, track, wall, diamonds)
		int diamond = s.diamond;
		float dAge = s.diamondAge;
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(bowl), (p, vc) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				double a0 = Math.toRadians(i * 360.0 / SEGMENTS), a1 = Math.toRadians((i + 1) * 360.0 / SEGMENTS);
				float u0 = (i % 8) * 8 / 64f, u1 = u0 + 8 / 64f;
				// the gap between the rotor and the cone (dark), the cone, the track, the wall's inner face
				ring(p, vc, a0, a1, rRotor, yRotor - 0.004f, rFoot, yFoot, u0, u1, 0 / 64f, 1 / 64f, 0xFF404040, light, 0.9f);
				ring(p, vc, a0, a1, rFoot, yFoot, rTrack, yTrack, u0, u1, 0 / 64f, 8 / 64f, 0xFFFFFFFF, light, 0.6f);
				ring(p, vc, a0, a1, rTrack, yTrack, rw, yTrack, u0, u1, 8 / 64f, 16 / 64f, 0xFFFFFFFF, light, 1f);
				wall(p, vc, a0, a1, rw, yTrack, yWall, u0, u1, 16 / 64f, 24 / 64f, light);
				// the wall's top lip, tucked under the furniture's rim (no sliver of the bowl floor shows between them)
				ring(p, vc, a0, a1, rw, yWall, rw * 1.04f, yWall, u0, u1, 16 / 64f, 17 / 64f, 0xFFFFFFFF, light, 0.8f);
			}
			for (int d = 0; d < 8; d++) {
				double a = Math.toRadians(RouletteSpin.DIAMOND0 + d * RouletteSpin.DIAMOND_PITCH);
				float r = (float) (RouletteSpin.R_DIAMOND * rw);
				float y = RouletteWorld.surfaceY(RouletteSpin.R_DIAMOND, rw, k) + 0.004f * k;
				boolean lit = d == diamond;
				int argb = lit ? (dAge < 0.5f ? 0xFFFFFFFF : 0xFFFFE080) : 0xFFFFFFFF;
				int dl = lit ? WorldQuads.FULL_BRIGHT : light;
				WorldQuads.topQuad(p, vc, (float) (r * Math.sin(a)), y, (float) (-r * Math.cos(a)), 0.011f * k, (float) Math.toDegrees(a) + 45, 32, 24, 8, 8,
					64, 64, argb, dl);
			}
		});
		// the rotor (the 208 head art with its digits) and the turret, turning
		float rot = (float) s.rotor;
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(ROTOR[s.theme]), (p, vc) -> {
			WorldQuads.topQuad(p, vc, 0, yRotor, 0, rRotor, rot, 0, 0, 152, 152, 152, 152, 0xFFFFFFFF, light);
		});
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(bowl), (p, vc) -> {
			float h = 0.012f * k;
			WorldQuads.topQuad(p, vc, 0, yRotor + h, 0, 0.022f * k, rot, 16, 40, 8, 8, 64, 64, 0xFFFFFFFF, light);
		});
		// pocket glow (translucent ring on the rotor, rides the pocket)
		if (s.glow >= 0 && s.glowAlpha > 0.02) {
			double a = Math.toRadians(s.rotor + RouletteSpin.pocketAngle(s.glow));
			float r = (float) (RouletteSpin.R_POCKET * rw);
			int argb = ((int) (s.glowAlpha * 220) << 24) | 0xFFFFFF;
			out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(bowl), (p, vc) -> WorldQuads.topQuad(p, vc, (float) (r * Math.sin(a)),
				yRotor + 0.003f * k, (float) (-r * Math.cos(a)), 0.034f * k, 0, 40, 24, 16, 16, 64, 64, argb, WorldQuads.FULL_BRIGHT));
		}
		if (!s.ball) return;
		// shadow on the surface under the ball, the streak and the ball (billboards)
		double a = Math.toRadians(s.ballAngle);
		float bx = (float) (s.ballR * rw * Math.sin(a));
		float bz = (float) (-s.ballR * rw * Math.cos(a));
		float ground = RouletteWorld.surfaceY(s.ballR, rw, k);
		float lift = (float) (s.ballZ * RouletteWorld.ART_PX * rw);
		float size = RouletteWorld.BALL_D * rw;
		int sa = (int) (Math.max(0, 0.75 * (1 - s.ballZ / 8)) * s.ballAlpha * 255);
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(bowl), (p, vc) -> WorldQuads.topQuad(p, vc, bx, ground + 0.0015f * k, bz,
			size * 0.6f * (1 + (float) s.ballZ / 16), 0, 16, 24, 16, 16, 64, 64, sa << 24 | 0xFFFFFF, light));
		for (int i = 0; i < s.trailCount; i++) {
			double ta = Math.toRadians(s.trail[3 * i]);
			float tr = (float) (s.trail[3 * i + 1] * rw);
			float tx = (float) (tr * Math.sin(ta)), tz = (float) (-tr * Math.cos(ta));
			float ty = RouletteWorld.surfaceY(s.trail[3 * i + 1], rw, k) + (float) (s.trail[3 * i + 2] * RouletteWorld.ART_PX * rw) + size / 2;
			int ta8 = (int) ((i == 0 ? 0.4 : 0.2) * s.ballAlpha * 255) << 24 | 0xFFFFFF;
			pose.pushPose();
			pose.translate(tx, ty, tz);
			billboardWheel(pose, s, camera);
			out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(bowl), (p, vc) -> sprite(p, vc, size * 0.85f, size * 0.85f, 0, 24, 16, 16, ta8, light));
			pose.popPose();
		}
		int ba = (int) (s.ballAlpha * 255) << 24 | 0xFFFFFF;
		pose.pushPose();
		pose.translate(bx, ground + lift + size / 2, bz);
		billboardWheel(pose, s, camera);
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(bowl), (p, vc) -> sprite(p, vc, size, size, 0, 24, 16, 16, ba, light));
		pose.popPose();
	}

	/** Billboard inside the wheel pose: undo the FACING rotation (full table) or the design mapping's rotation (compact). */
	private static void billboardWheel(PoseStack pose, State s, CameraRenderState camera) {
		pose.rotateAround(Axis.YP.rotationDegrees(s.facing.toYRot()), 0f, 0f, 0f);
		pose.rotateAround(camera.orientation, 0f, 0f, 0f);
	}

	/** One segment of a sloped ring between (r0, y0) and (r1, y1), facing up/inward; v from the outer edge. */
	private static void ring(PoseStack.Pose p, VertexConsumer vc, double a0, double a1, float r0, float y0, float r1, float y1, float u0, float u1, float vIn,
			float vOut, int argb, int light, float shade) {
		float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
		int col = shadeArgb(argb, shade + (1 - shade) * 0.5f);
		// counter-clockwise seen from above (normal up): inner a0, inner a1, outer a1, outer a0 — wound for the cull side
		WorldQuads.vertex(p, vc, r0 * s0, y0, r0 * c0, u0, vOut, col, light, 0, 1, 0);
		WorldQuads.vertex(p, vc, r1 * s0, y1, r1 * c0, u0, vIn, col, light, 0, 1, 0);
		WorldQuads.vertex(p, vc, r1 * s1, y1, r1 * c1, u1, vIn, col, light, 0, 1, 0);
		WorldQuads.vertex(p, vc, r0 * s1, y0, r0 * c1, u1, vOut, col, light, 0, 1, 0);
	}

	/** One segment of the track wall's inner face (vertical band at radius r from y0 to y1). */
	private static void wall(PoseStack.Pose p, VertexConsumer vc, double a0, double a1, float r, float y0, float y1, float u0, float u1, float v0, float v1,
			int light) {
		float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
		float nx = -(s0 + s1) / 2, nz = -(c0 + c1) / 2;
		WorldQuads.vertex(p, vc, r * s0, y1, r * c0, u0, v0, 0xFFE0E0E0, light, nx, 0, nz);
		WorldQuads.vertex(p, vc, r * s0, y0, r * c0, u0, v1, 0xFFE0E0E0, light, nx, 0, nz);
		WorldQuads.vertex(p, vc, r * s1, y0, r * c1, u1, v1, 0xFFE0E0E0, light, nx, 0, nz);
		WorldQuads.vertex(p, vc, r * s1, y1, r * c1, u1, v0, 0xFFE0E0E0, light, nx, 0, nz);
	}

	private static int shadeArgb(int argb, float k) {
		int r = (int) (((argb >> 16) & 255) * k), g = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
		return (argb & 0xFF000000) | r << 16 | g << 8 | b;
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
