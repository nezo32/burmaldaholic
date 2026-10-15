package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.ResultBanner;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.FxGate;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.PixelSnap;
import dev.nezo.burmaldaholic.games.roulette.logic.RotorModel;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSpin;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteFlow;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteFocus;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteFx;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * European roulette, table UI v2 (REDESIGN.md §7.5; docs/architecture/ui-v2.md; mockups {@code v2_roulette_*}). The
 * felt fills the screen: the wheel column on the left (the wheel, the ball on the shared {@link RouletteSpin},
 * "Last numbers"), the betting layout on the right with every inside bet reachable by precise hit-testing (cell =
 * straight, line = split, outer edge = street / six line, intersection = corner, zero edges = trio / first four), the
 * call bets (Tier / Orphelins / Voisins / Zero) under it; the kit's TopBar and Dock (chips, Undo / Clear / Rebet / ×2,
 * the bet readout, the status, the gold primary: Sit down / Spin / Ready / Rebet). The spin keeps the layout on screen
 * (dimmed) with every bet visible; the result frames the winning cell in gold, sweeps the losing stacks, pays the winning
 * ones with the server's per-bet returns and shows the net in the banner + the TopBar delta.
 *
 * <p>Only renders server state and sends actions ({@code bet}, {@code call}, {@code undo}, {@code clear}, {@code rebet},
 * {@code double}, {@code spin}, {@code sit}); every action goes through the kit's acknowledgement watchdog.
 */
public class RouletteScreen extends V2TableScreen<RouletteView> {
	private static final int RESULT_MS = 3000;
	/** HitMap id of the whole betting layout. */
	static final String FELT_ALL = "felt.layout";

	private final TableClock clock = new TableClock();
	private TableTheme theme = TableTheme.VILLAGE;
	private @Nullable RouletteLayout layout;
	private float partial;
	// the spin (RO: RouletteSpin, sampled at ms since the launch = the NO_MORE_BETS start)
	/** Tests: ms since the launch (overrides the shared clock; null in production). */
	public static @Nullable DoubleSupplier testClock;
	/** Tests: local RESULT ms (overrides the local result clock; null in production). */
	public static @Nullable DoubleSupplier testResultClock;
	/** Tests: records every decorative effect that passed the reduced-motion / flashes gate. */
	public static FxGate.@Nullable Recorder recorder;
	private @Nullable RouletteSpin spin;
	private int spinSeqBuilt = Integer.MIN_VALUE;
	private int spinResultBuilt = Integer.MIN_VALUE;
	private int spinLast = -1;
	private long launchTick;
	private RotorModel rotorModel = RotorModel.idle(0);
	private final RouletteSpin.Sample sample = new RouletteSpin.Sample();
	private final RouletteFx.Frame fx = new RouletteFx.Frame();
	private final RouletteWheelView.Wheel colWheel = new RouletteWheelView.Wheel();
	private final RouletteWheelView.Wheel focusWheel = new RouletteWheelView.Wheel();
	private @Nullable RouletteFocus focus;
	private final EventCursor cursor = new EventCursor(new MotionEvent[0]);
	private final EventCursor.Listener soundListener = this::onSpinEvent;
	private FxSounds.Handle roll = FxSounds.Handle.NONE;
	private double rollOmega;
	private double rollVolume;
	private boolean rollWanted;
	private long lastFrameNs;
	private double frameDt = 16.7;
	/** the last drawn head frame index of the column wheel (tests: no snap at RESULT → BETTING) */
	private int lastHeadFrame = -1;
	private long bellFor = Long.MIN_VALUE;
	private long resultFor = Long.MIN_VALUE;
	private long localStartFor = Long.MIN_VALUE;
	private long resultLocalStart;
	private boolean resultSkipped;
	private final RouletteChipFx chipFx = new RouletteChipFx();
	// the bet just placed: its value tag shows for 1.5 s (§2.2)
	private @Nullable Spot lastPlaced;
	private long lastPlacedAt;
	/** the layout point of the click being routed (precise hit-testing of the felt), null for keyboard presses */
	private double @Nullable [] clickAt;
	/** what the status hint currently describes (hover readout), "" = the flow's hint */
	private String hoverShown = "";
	/** Repeat pressed during the result (DES roulette P1): the last slip is sent the moment betting opens */
	private boolean rebetQueued;
	/** own stacks that carried a value pill in the last frame (real-flow tests) */
	private int valuePills;
	/** above this many own stacks the value pills show on hover only (DES roulette P1) */
	static final int PILLS_ALWAYS_MAX = 8;

	public RouletteScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType() == RouletteModule.HIGH_ROLLER_TABLE
			? "gui.burmaldaholic.roulette.title_high_roller" : "gui.burmaldaholic.roulette.title"));
		chrome.bet.multiSpot(true);
	}

	// ---- the pure parts ------------------------------------------------------------------------------------------------

	@Override
	protected RouletteView view(CompoundTag s) {
		clock.sync(s.getLongOr("game_time", 0));
		theme = TableKit.theme(theme());
		boolean seated = s.getIntOr("seat", -1) >= 0;
		int occupied = s.getListOrEmpty("seats").size();
		int seatCount = s.getIntOr("seat_count", 0);
		RouletteFlow.Phase phase = RouletteFlow.Phase.of(s.getStringOr("phase", "betting"));
		Map<Spot, Long> mine = mineOf(s);
		long total = s.getLongOr("total", 0);
		long rebet = rebetAmount(s);
		long ticks = s.getLongOr("ticks_left", -1);
		boolean multi = s.getIntOr("bettors", 0) > 1 || occupied > 1;
		int secs = ticks < 0 || (phase == RouletteFlow.Phase.BETTING && !multi) ? -1 : (int) Math.ceil(ticks / 20.0);
		RouletteFlow.View v = new RouletteFlow.View(seated, !seated && seatCount > 0 && occupied >= seatCount, s.getBooleanOr("enabled", true), phase,
			s.getLongOr("min", 1), s.getLongOr("max", 0), s.getLongOr("min_total", 0), s.getLongOr("balance", 0), total,
			s.getBooleanOr("can_rebet", false), rebet, s.getBooleanOr("ready", false), multi, s.getIntOr("ready_count", 0), s.getIntOr("bettors", 0), secs,
			rebetQueued && phase == RouletteFlow.Phase.RESULT);
		syncSpin(s, phase);
		chipFx.state(s, phase == RouletteFlow.Phase.BETTING, Util.getMillis());
		return RouletteView.of(v, mine);
	}

	private static Map<Spot, Long> mineOf(CompoundTag s) {
		Map<Spot, Long> out = new LinkedHashMap<>();
		ListTag list = s.getListOrEmpty("bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			Spot.parseKey(b.getStringOr("spot", "")).ifPresent(sp -> out.merge(sp, b.getLongOr("amount", 0), Long::sum));
		}
		return out;
	}

	/** The total of the server's last slip: what Rebet places. */
	private static long rebetAmount(CompoundTag s) {
		return s.getLongOr("rebet_total", 0);
	}

	@Override
	protected Flow<RouletteView> flow() {
		return RouletteFlow.FLOW;
	}

	@Override
	protected @Nullable BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				// multi-spot: a chip only arms; placing happens on the felt (onTarget)
			}

			@Override
			public void tool(String toolId) {
				sendTracked(toolId, new CompoundTag(), "", 0);
			}
		};
	}

	// ---- layout + input -----------------------------------------------------------------------------------------------

	@Override
	protected void layoutPlay(TableGrid grid, RouletteView v, HitMap hits) {
		RouletteLayout l = RouletteLayout.of(grid);
		layout = l;
		boolean open = RouletteFlow.betBlock(v.flow()).isEmpty();
		// the whole layout first (a click anywhere is hit-tested precisely), then every spot's own box on top (keyboard,
		// narration, real-flow tests: the centre of each box is that spot)
		hits.add(FELT_ALL, l.layout, open);
		for (Spot s : RouletteLayout.allSpots()) hits.add(RouletteLayout.spotId(s), l.target(s), open);
		for (Racetrack.Section s : Racetrack.Section.values()) hits.add(RouletteLayout.callId(s), l.calls.get(s.ordinal()), open);
		// the banner never covers a number cell (the winning one stays visible), the wheel or the history (R-CDE M4)
		chrome.banner.regions(l.bannerRegions(), List.of());
		chrome.banner.obstacles(l.bannerObstacles());
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		RouletteView v = view;
		RouletteLayout l = layout;
		if (v == null || l == null || !id.startsWith("felt.")) return false;
		if (button.left() && "result".equals(phase()) && !resultSkipped) {
			resultSkipped = true; // a click skips the local result beats (never the shared spin)
		}
		Optional<Gate> block = RouletteFlow.betBlock(v.flow());
		if (block.isPresent()) {
			chrome.refuse(block.get());
			return true;
		}
		if (!button.left()) return true;
		long d = chrome.bet.armOrDefault();
		if (d <= 0) {
			chrome.refuse(Gate.off("chip", RouletteFlow.K + "bet", "gui.burmaldaholic.core.v2.reason.not_enough", v.flow().min()));
			return true;
		}
		Gate chip = RouletteFlow.chip(v.flow(), d);
		if (!chip.enabled()) {
			chrome.refuse(chip);
			return true;
		}
		String rest = id.substring(5);
		if (rest.equals("layout") && (clickAt == null || l.hit(clickAt[0], clickAt[1]).isEmpty())) return true;
		if (rest.startsWith("call.")) {
			Optional<Racetrack.Section> sec = Racetrack.Section.byId(rest.substring(5));
			if (sec.isEmpty()) return false;
			CompoundTag args = new CompoundTag();
			args.putString("section", sec.get().id());
			args.putLong("amount", d);
			sendTracked("call", args, "", d);
			for (Spot cs : Racetrack.spots(sec.get())) chipFx.place(cs, d, armedChipBox(), l.anchor(cs), Util.getMillis(), FxSettings.reduceMotion());
			return true;
		}
		double[] at = clickAt;
		// a spot's own HitMap box already routed the click precisely (line bets lie on top); only the whole-layout box
		// needs the point hit-tested (in framed / offset viewports the raw event point is not in layout px)
		Optional<Spot> spot = !rest.equals("layout") ? Spot.parseKey(rest) : at != null ? l.hit(at[0], at[1]) : Optional.empty();
		if (spot.isEmpty()) return rest.equals("layout");
		CompoundTag args = new CompoundTag();
		args.putString("type", spot.get().type().id());
		args.putIntArray("nums", spot.get().numbers().stream().mapToInt(Integer::intValue).toArray());
		args.putLong("amount", d);
		sendTracked("bet", args, spot.get().key(), d);
		lastPlaced = spot.get();
		lastPlacedAt = Util.getMillis();
		// §3.6: a chip flies from the armed Dock chip to the spot; chip_place plays on landing
		chipFx.place(spot.get(), d, armedChipBox(), l.anchor(spot.get()), Util.getMillis(), FxSettings.reduceMotion());
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		clickAt = new double[] {event.x(), event.y()};
		try {
			return super.mouseClicked(event, doubleClick);
		} finally {
			clickAt = null;
		}
	}

	@Override
	public void onGate(String id, Gate gate) {
		if ("spin".equals(gate.action())) {
			sendTracked("spin");
			return;
		}
		if (BetGates.REBET.equals(gate.action()) && "result".equals(phase())) {
			// the server takes bets only while betting is open: queue the slip, skip the local result beats
			rebetQueued = true;
			resultSkipped = true;
			if (view != null) view = view(state());
			refresh();
			return;
		}
		super.onGate(id, gate);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (!rebetQueued) return;
		String ph = phase();
		if ("betting".equals(ph)) {
			rebetQueued = false;
			if (state().getLongOr("total", 0) == 0 && state().getBooleanOr("can_rebet", false)) sendTracked(BetGates.REBET);
		} else if (!"result".equals(ph)) {
			rebetQueued = false;
		}
	}

	/** The armed Dock chip's box (the start of a placement flight), null when the Dock is not laid out. */
	private @Nullable Box armedChipBox() {
		List<Box> boxes = chrome.bet.chipBoxes();
		int i = chrome.bet.selectedIndex();
		return i >= 0 && i < boxes.size() ? boxes.get(i) : boxes.isEmpty() ? null : boxes.get(0);
	}

	/** Own stacks that carried a value pill in the last frame (real-flow tests). */
	public int valuePillsDrawn() {
		return valuePills;
	}

	/** Whether a Repeat pressed during the result waits for betting to open. */
	public boolean rebetQueued() {
		return rebetQueued;
	}

	// ---- state helpers -------------------------------------------------------------------------------------------------

	private int result() {
		return state().getIntOr("result", -1);
	}

	private int[] history() {
		return state().getIntArray("history").orElse(new int[0]);
	}

	private double phaseMs() {
		return clock.msSince(state().getLongOr("phase_start", 0), partial);
	}

	/** Ms since the launch (the NO_MORE_BETS start) on the shared clock: the time every viewer samples the spin at. */
	double spinT() {
		DoubleSupplier test = testClock;
		if (test != null) return test.getAsDouble();
		return clock.msSince(launchTick, partial);
	}

	/** The newest settled number before the current launch: the ball being picked up / riding at idle. */
	private int idleLast() {
		int[] h = history();
		return h.length > 0 ? h[0] : -1;
	}

	/** Rebuilds the spin for a new launch (pre-result) and again when the result arrives (same seed, same motion so far). */
	private void syncSpin(CompoundTag s, RouletteFlow.Phase phase) {
		int seq = s.getIntOr("spin_seq", 0);
		int dir = s.getIntOr("dir", 1) < 0 ? -1 : 1;
		boolean pushed = s.getBooleanOr("pushed", seq > 0);
		rotorModel = new RotorModel(s.getIntOr("rotor_a", 0) / 100.0, s.getIntOr("rotor_w", (int) (RotorModel.OMEGA_IDLE * 100)) / 100.0, dir, pushed);
		launchTick = s.getLongOr("launch_tick", 0);
		boolean live = phase != RouletteFlow.Phase.BETTING && seq > 0;
		if (!live) return; // BETTING: the rotor model turns, the last ball rides its pocket (drawn without a spin)
		boolean drawn = phase == RouletteFlow.Phase.SPIN || phase == RouletteFlow.Phase.RESULT;
		int result = drawn ? s.getIntOr("result", -1) : -1;
		if (seq == spinSeqBuilt && result == spinResultBuilt) return;
		boolean newSeq = seq != spinSeqBuilt;
		if (newSeq) {
			int[] h = s.getIntArray("history").orElse(new int[0]);
			spinLast = phase == RouletteFlow.Phase.RESULT ? (h.length > 1 ? h[1] : -1) : (h.length > 0 ? h[0] : -1);
		}
		long spinStart = result >= 0 ? s.getLongOr("spin_start", launchTick + RouletteSpin.NMB_TICKS) : launchTick + RouletteSpin.NMB_TICKS;
		int ticks = Math.max(1, s.getIntOr("spin_ticks", 140));
		double prev = cursorAt;
		spin = RouletteSpin.build(result, s.getIntOr("seed", 0), spinLast, launchTick, spinStart, ticks, rotorModel);
		spinSeqBuilt = seq;
		spinResultBuilt = result;
		cursor.bind(spin.events());
		double t = spinT();
		if (!newSeq) cursor.reset(prev, true); // the pre-result spin already voiced these (identical before the draw)
		else if (t > EventCursor.LATE_MS) cursor.reset(t, true); // late join: the path is valid at any t; old events stay silent
		cursorAt = newSeq ? -1 : prev;
	}

	private double cursorAt = -1;

	private double resultMs() {
		DoubleSupplier test = testResultClock;
		if (test != null) return test.getAsDouble();
		long start = state().getLongOr("phase_start", 0);
		double shared = phaseMs();
		if (localStartFor != start) {
			localStartFor = start;
			resultSkipped = shared >= 0.85 * RESULT_MS;
			resultLocalStart = Util.getMillis() - (long) Math.max(0, shared);
		}
		if (resultSkipped) return RESULT_MS * 2;
		return (Util.getMillis() - resultLocalStart) * (FxSettings.speed().pct / 100.0);
	}

	private Map<Spot, Long> spots(String key) {
		Map<Spot, Long> out = new LinkedHashMap<>();
		CompoundTag t = state().getCompoundOrEmpty(key);
		for (String k : t.keySet()) Spot.parseKey(k).ifPresent(s -> out.put(s, t.getLongOr(k, 0)));
		return out;
	}

	private int lastBefore() {
		int[] h = history();
		if ("result".equals(phase())) return h.length > 1 ? h[1] : -1;
		return h.length > 0 ? h[0] : -1;
	}

	// ---- drawing -------------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, RouletteView v, int mouseX, int mouseY, float a) {
		partial = a;
		long ns = System.nanoTime();
		if (lastFrameNs != 0) frameDt = Math.max(1, Math.min(100, (ns - lastFrameNs) / 1e6));
		lastFrameNs = ns;
		RouletteLayout l = layout;
		if (l == null || l.grid.play.w() != grid.play.w() || l.grid.play.h() != grid.play.h()) {
			l = RouletteLayout.of(grid);
			layout = l;
		}
		if (focus == null || focus.colArt() != l.wheelD || focus.k() != Math.max(1, grid.viewport.k()) || focus.size() > grid.play.h() + 1) {
			focus = RouletteFocus.of(l);
		}
		String ph = phase();
		boolean betting = RouletteFlow.betBlock(v.flow()).isEmpty();
		double rMs = "result".equals(ph) ? resultMs() : RESULT_MS * 2;
		long now = Util.getMillis();
		double t = spinT();
		RouletteSpin sp = "betting".equals(ph) ? null : spin;
		boolean settled = sp != null && sp.settled(t);
		// hover: the spot / call under the mouse lights its numbers
		Spot hover = betting ? l.hit(mouseX, mouseY).orElse(null) : null;
		Racetrack.Section hoverCall = betting ? l.callAt(mouseX, mouseY).orElse(null) : null;
		Set<Integer> lit = new HashSet<>();
		Spot litOutside = null;
		if (hover != null) {
			if (hover.type().inside()) lit.addAll(hover.numbers());
			else litOutside = hover;
		} else if (hoverCall != null) {
			lit.addAll(Racetrack.covered(hoverCall));
		}
		boolean spinView = "no_more_bets".equals(ph) || "spin".equals(ph);
		// nothing names the result before the ball rests (the gold cell shows from the rest on)
		int win = "result".equals(ph) && rMs >= RouletteChipFx.RETURN_END ? result() : spinView && settled ? result() : -1;
		double dim = spinView ? 1 : "result".equals(ph) ? 1 - Math.min(1, rMs / RouletteChipFx.RETURN_END) : 0;
		RouletteFeltView.layout(g, font, l, dim, lit, litOutside, win);
		RouletteFeltView.calls(g, font, l, hoverCall, betting);
		if (dim > 0) {
			// bets are closed: the layout dims under the spin (lighter once the gold cell may show)
			double k = dim * (win >= 0 ? 0.3 : 0.5);
			Box d = l.layout;
			Box last = l.calls.get(l.calls.size() - 1);
			g.fill(d.x() - 2, d.y() - 2, d.right() + 2, Math.max(d.bottom(), last.bottom()) + 2, TableGfx.alpha(0xFF0A0614, k));

			if (win >= 0) {
				Box c = l.cell(win); // the gold cell stays bright over the dim
				int gold = 0xFFFFD640;
				g.fill(c.x() - 1, c.y() - 1, c.right() + 1, c.y() + 1, gold);
				g.fill(c.x() - 1, c.bottom() - 1, c.right() + 1, c.bottom() + 1, gold);
				g.fill(c.x() - 1, c.y() - 1, c.x() + 1, c.bottom() + 1, gold);
				g.fill(c.right() - 1, c.y() - 1, c.right() + 1, c.bottom() + 1, gold);
			}
		}
		// the wheel column
		int[] hist = history();
		boolean newShown = !"result".equals(ph) || rMs >= RouletteChipFx.HISTORY_AT;
		int[] shownHist = "result".equals(ph) && !newShown && hist.length > 0 ? java.util.Arrays.copyOfRange(hist, 1, hist.length) : hist;
		double newPill = "result".equals(ph) ? Math.max(0, Math.min(1, (rMs - RouletteChipFx.HISTORY_AT) / 250.0)) : 1;
		RouletteFeltView.history(g, font, l, shownHist, newPill);
		bell(ph);
		double focusA = focusAmount(ph, t, rMs);
		if (focusA <= 0) wheel(g, l, ph, t, rMs, 0);
		stacks(g, l, v, ph, rMs, win, t);
		chipFx.drawFlights(g, l, now, Math.max(1, grid.viewport.k()));
		if (focusA > 0) wheel(g, l, ph, t, rMs, focusA);
		// ghost chip + hover readout
		String hoverKey = "";
		if (hover != null) {
			long d = chrome.bet.selected();
			int[] p = l.anchor(hover);
			if (d > 0) ChipStack.drawPending(g, new Box(p[0] - l.chip / 2, p[1] - l.chip / 2, l.chip, l.chip), d);
			long on = v.mine().getOrDefault(hover, 0L);
			hoverKey = hover.key() + "/" + on;
			if (!hoverKey.equals(hoverShown)) {
				Component desc = describe(hover);
				chrome.status.hint(on > 0 ? Component.translatable("gui.burmaldaholic.roulette.v2.hover", desc, Texts.number(on),
					Texts.number(hover.type().payout())) : Component.translatable("gui.burmaldaholic.roulette.v2.hover_empty", desc,
					Texts.number(hover.type().payout())));
			}
		} else if (hoverCall != null) {
			long d = Math.max(0, chrome.bet.selected());
			hoverKey = "call/" + hoverCall.id() + "/" + d;
			if (!hoverKey.equals(hoverShown)) {
				long all = d * Racetrack.spots(hoverCall).size();
				chrome.status.hint(Component.translatable("gui.burmaldaholic.roulette.v2.call_hover",
					Component.translatable("gui.burmaldaholic.roulette.racetrack." + hoverCall.id()), Texts.number(d), Texts.number(all)));
			}
		}
		if (hoverKey.isEmpty() && !hoverShown.isEmpty()) refresh(); // back to the flow's own hint
		hoverShown = hoverKey;
		hoverSpot = hover;
		sounds(ph, t);
		if ("result".equals(ph) && result() >= 0) resultBeats(rMs);
	}

	private void bell(String ph) {
		if ("no_more_bets".equals(ph) && bellFor != state().getLongOr("phase_start", 0)) {
			bellFor = state().getLongOr("phase_start", 0);
			if (phaseMs() < 500) FxSounds.play("roulette_bell", 1f);
		}
	}

	/** Focus amount 0 (column) … 1 (focus wheel): grows at the launch, shrinks back in the first 400 ms of RESULT. */
	private double focusAmount(String ph, double t, double rMs) {
		RouletteFocus f = focus;
		if (f == null || !f.enabled() || FxSettings.reduceMotion() || spin == null) return 0;
		if (!RouletteFx.motion("focus", false, recorder)) return 0;
		return switch (ph) {
			case "no_more_bets", "spin" -> RouletteFocus.inAmount(t);
			case "result" -> RouletteFocus.outAmount(rMs);
			default -> 0;
		};
	}

	/**
	 * The wheel: the column wheel (idle: the rotor model turns and the last ball rides its pocket; spinning: the shared
	 * spin) or, while focused, the grown column wheel cross-fading into the native focus sprite, plus the loupe.
	 */
	private void wheel(GuiGraphicsExtractor g, RouletteLayout l, String ph, double t, double rMs, double focusA) {
		RouletteFocus f = focus;
		int k = Math.max(1, l.grid.viewport.k());
		boolean reduce = FxSettings.reduceMotion();
		RouletteSpin sp = "betting".equals(ph) ? null : spin;
		RouletteWheelView.Wheel w = colWheel;
		w.k = k;
		w.t = t;
		w.frameDt = frameDt;
		w.streakSpin = null;
		w.effects = null;
		w.glow = -1;
		w.badge = -1;
		w.alpha = 1;
		if (sp == null) {
			// idle (BETTING / before the first spin): the rotor coasts on the last launch's model; the last ball rides
			int last = idleLast();
			w.rotor = reduce ? (last >= 0 ? -RouletteSpin.pocketAngle(last) : 0) : rotorModel.angle(Math.max(0, t));
			w.ball = last >= 0;
			w.ballAlpha = 1;
			w.ballRel = last >= 0 ? RouletteSpin.pocketAngle(last) : 0;
			w.ballR = RouletteSpin.R_POCKET;
			w.ballZ = 0;
			w.ballFrame = RouletteSpin.FRAME_POCKET;
			double bettingMs = phaseMs();
			if (last >= 0 && "betting".equals(ph) && bettingMs < 1500 && !reduce) {
				w.glow = last;
				w.glowAlpha = 1 - bettingMs / 1500;
				w.glowMs = 2000;
			}
		} else {
			sp.sample(t, sample);
			RouletteFx.plan(sp, sample, t, spinLast, reduce, FxSettings.flashes(), recorder, fx);
			w.rotor = fx.rotor;
			w.ball = fx.ball;
			w.ballAlpha = fx.ballAlpha;
			w.ballRel = fx.ballAbs - fx.rotor;
			w.ballR = reduce ? RouletteSpin.R_POCKET : sample.r;
			w.ballZ = reduce ? 0 : sample.z;
			w.ballFrame = reduce ? RouletteSpin.FRAME_POCKET : sample.frame;
			w.streakSpin = fx.streak ? sp : null;
			w.effects = reduce ? null : sp;
			w.sparks = RouletteFx.spark("sparks", reduce, FxSettings.flashes(), recorder);
			w.flash = RouletteFx.diamondFlash(reduce, FxSettings.flashes(), recorder);
			if (sp.settled(t)) {
				w.glow = sp.result;
				w.glowAlpha = 1;
				w.glowMs = t - sp.restMs();
				w.badge = sp.result;
				w.badgeMs = reduce ? 1000 : t - sp.restMs() - 150;
			}
		}
		if (focusA <= 0 || f == null) {
			w.art = l.wheelD;
			w.scale = 1;
			w.cx = l.wheel.x() + l.wheelD / 2.0;
			w.cy = l.wheel.y() + l.wheelD / 2.0;
			lastHeadFrame = RouletteWheelView.headFrame(w.rotor, RouletteWheelView.HEAD_FRAMES);
			RouletteWheelView.draw(g, font, theme, w);
			return;
		}
		// focused: the column sprite grows and slides; the native focus sprite fades in over it (same rotor angle)
		boolean resultPh = "result".equals(ph);
		double swap = resultPh ? (focusA >= 0.5 ? 1 : 0) : RouletteFocus.swapAlpha(t);
		double cx = f.x(focusA);
		double cy = f.y(focusA);
		double d = f.diameter(focusA);
		if (swap < 1) {
			w.art = f.colArt();
			w.scale = d / f.colArt();
			w.cx = cx;
			w.cy = cy;
			RouletteWheelView.draw(g, font, theme, w);
		}
		if (swap > 0) {
			RouletteWheelView.Wheel fw = focusWheel;
			copy(w, fw);
			fw.art = f.art();
			fw.scale = resultPh ? d / f.art() : f.mult();
			fw.cx = cx;
			fw.cy = cy;
			fw.alpha = swap;
			RouletteWheelView.draw(g, font, theme, fw);
			if (sp != null && sp.settled(t) && fx.loupe) loupe(g, fw, sp, t, resultPh ? 1 - Math.min(1, rMs / RouletteChipFx.RETURN_END) : 1);
		}
		lastHeadFrame = RouletteWheelView.headFrame(w.rotor, RouletteWheelView.HEAD_FRAMES);
	}

	private static void copy(RouletteWheelView.Wheel a, RouletteWheelView.Wheel b) {
		b.k = a.k;
		b.t = a.t;
		b.frameDt = a.frameDt;
		b.rotor = a.rotor;
		b.ball = a.ball;
		b.ballAlpha = a.ballAlpha;
		b.ballRel = a.ballRel;
		b.ballR = a.ballR;
		b.ballZ = a.ballZ;
		b.ballFrame = a.ballFrame;
		b.streakSpin = a.streakSpin;
		b.effects = a.effects;
		b.sparks = a.sparks;
		b.flash = a.flash;
		b.glow = a.glow;
		b.glowAlpha = a.glowAlpha;
		b.glowMs = a.glowMs;
		b.badge = a.badge;
		b.badgeMs = a.badgeMs;
	}

	/**
	 * The loupe (§3.3): a brass magnifier on the felt side of the focus wheel showing the result pocket upright (the 208
	 * head turned so the pocket is on top), its number and neighbours above it and the ball in it, at the focus wheel's
	 * integer scale (1.44× / 2× of the 144 / 104 focus wheels).
	 * Pops with {@code outBack} at rest, shrinks into the badge in the first 400 ms of RESULT ({@code size} 1 → 0).
	 */
	private void loupe(GuiGraphicsExtractor g, RouletteWheelView.Wheel w, RouletteSpin sp, double t, double size) {
		double age = t - sp.restMs();
		double pop = Ease.OUT_BACK.apply(Math.max(0, Math.min(1, age / 200.0))) * size;
		if (pop <= 0.05) return;
		int k = w.k;
		double ls = Math.max(1, Math.round(w.scale)); // the ring at the focus wheel's integer scale
		double rad = w.art / 2.0 * w.scale;
		// beside the wheel, toward the layout, in its upper half; shrinks toward the badge
		double lx = w.cx + (rad + 4 + 20 * ls) * pop;
		double ly = w.cy - rad * 0.45 * pop;
		lx = PixelSnap.snap(lx, k);
		ly = PixelSnap.snap(ly, k);
		// the pocket upright under the glass, its number above it (native scale: 1.44× / 2× on the 144 / 104 focus wheel)
		double mag = ls;
		int n = sp.result;
		double rc = (RouletteSpin.R_POCKET * RouletteWheelView.RW_BIG + RouletteWheelView.DIGIT_R) / 2;
		double half = 14 * ls * pop;
		g.enableScissor((int) Math.ceil(lx - half), (int) Math.ceil(ly - half), (int) Math.floor(lx + half), (int) Math.floor(ly + half));
		g.fill((int) (lx - half), (int) (ly - half), (int) (lx + half) + 1, (int) (ly + half) + 1, 0xFF0C0A10);
		double s = mag * pop;
		g.pose().pushMatrix();
		g.pose().translate((float) lx, (float) (ly + rc * s));
		g.pose().scale((float) s, (float) s);
		g.pose().rotate((float) Math.toRadians(-RouletteSpin.pocketAngle(n)));
		TableGfx.region(g, RouletteWheelView.HEAD_208, 152, 152, 0, 0, 152, 152, -76, -76, 0xFFFFFFFF);
		g.pose().popMatrix();
		int idx = dev.nezo.burmaldaholic.games.roulette.logic.Wheel.wheelIndex(n);
		for (int j = -2; j <= 2; j++) {
			int m = dev.nezo.burmaldaholic.games.roulette.logic.Wheel.ORDER.get(Math.floorMod(idx + j, 37));
			double a = Math.toRadians(j * RouletteSpin.STEP);
			double dx = s * RouletteWheelView.DIGIT_R * Math.sin(a);
			double dy = s * (rc - RouletteWheelView.DIGIT_R * Math.cos(a));
			RouletteWheelView.digit(g, m, lx + dx, ly + dy, s, 0xFFFFFFFF, k);
		}
		double by = ly + (rc - RouletteSpin.R_POCKET * RouletteWheelView.RW_BIG) * s;
		dev.nezo.burmaldaholic.client.fx.motion.SnapDraw.frameF(g, "roulette/ball", 7, 7, 3, RouletteSpin.FRAME_POCKET, lx - 3.5 * s, by - 3.5 * s, 0xFFFFFFFF, s,
			k);
		g.disableScissor();
		dev.nezo.burmaldaholic.client.fx.motion.SnapDraw.blitF(g, TableGfx.sprite("roulette/loupe"), lx - 20 * ls * pop, ly - 20 * ls * pop, 40, 40,
			0xFFFFFFFF, ls * pop, k);
	}

	// ---- sounds (shared events, fired once at any frame rate; the hitch rule keeps late ones silent) -----------------

	private void sounds(String ph, double t) {
		RouletteSpin sp = "betting".equals(ph) ? null : spin;
		if (sp != null) {
			cursor.advance(t, soundListener);
			cursorAt = t;
		}
		// the rolling ball: one tickable loop from the launch to the drop, pitch / volume from the ball's speed
		boolean want = sp != null && t >= RouletteSpin.LAUNCH_MS && t < (sp.solved() ? sp.dropMs() + 120 : Double.MAX_VALUE) && ("spin".equals(ph) || "no_more_bets".equals(ph));
		if (want) {
			sp.sample(t, sample);
			double v = sample.omega / RouletteSpin.OMEGA_LAUNCH;
			double fadeOut = sp.solved() && t > sp.dropMs() ? Math.max(0, 1 - (t - sp.dropMs()) / 120) : 1;
			double fadeIn = Math.min(1, (t - RouletteSpin.LAUNCH_MS) / 80);
			rollOmega = 1.4 + 0.6 * v;
			rollVolume = (0.05 + 0.13 * v) * fadeOut * fadeIn * (FxSettings.reduceMotion() ? 0.5 : 1);
		}
		rollWanted = want;
		if (want && !roll.active()) {
			roll = FxSounds.loop("roulette_roll", () -> rollOmega, () -> rollVolume, () -> rollWanted, FxSounds.LoopPriority.SCREEN);
		} else if (!want && roll.active()) {
			roll.release();
		}
	}

	/** Voices a spin event (silent = late or skipped: nothing to hear; the visuals are pure in time). */
	private void onSpinEvent(MotionEvent e, boolean silent) {
		if (silent) return;
		boolean reduce = FxSettings.reduceMotion();
		float st = e.strength();
		switch (e.kind()) {
			case RouletteSpin.EV_PUSH -> {
				if (!reduce) FxSounds.play("roulette_rotor_push", 1f, 1f);
			}
			case RouletteSpin.EV_LAUNCH -> FxSounds.play("roulette_spin", 1f, 1f);
			case RouletteSpin.EV_ROLL -> {
				if (!reduce) FxSounds.play("roulette_ball_roll", 0.5f + 0.5f * st, 1.75f + 0.25f * st);
			}
			case RouletteSpin.EV_DEFLECTOR -> {
				if (!reduce) FxSounds.play("roulette_deflector", 1f, 1f);
			}
			case RouletteSpin.EV_CONTACT -> FxSounds.play("roulette_ball_drop", 1f, 1f);
			case RouletteSpin.EV_FRET, RouletteSpin.EV_KICKBACK -> {
				if (!reduce) {
					RouletteSpin sp = spin;
					int hops = sp != null && sp.rattle() != null ? sp.rattle().hops() : 6;
					float pitch = 1.9f - 0.6f * e.aux() / Math.max(1, hops - 1);
					FxSounds.play("roulette_ball_bounce", 0.25f + 0.75f * st, pitch);
				}
			}
			case RouletteSpin.EV_FLOOR -> {
				if (!reduce) FxSounds.play("roulette_floor", 0.3f + 0.7f * st, 1f);
			}
			case RouletteSpin.EV_SETTLE_CLICK -> {
				if (!reduce) FxSounds.play("roulette_ball_settle", 0.4f * st + 0.2f, 1.25f);
			}
			case RouletteSpin.EV_REST -> FxSounds.play("roulette_ball_settle", 1f, 1f);
			default -> { }
		}
	}

	@Override
	public void removed() {
		roll.release();
		RouletteTextures.releaseScreenSheets();
		super.removed();
	}

	/** Head frame index the column wheel drew last (client GameTests: the rotor never snaps). */
	public int lastHeadFrame() {
		return lastHeadFrame;
	}

	/** The current spin (client GameTests); null while betting. */
	public @Nullable RouletteSpin spin() {
		return spin;
	}

	/** Everybody's chips: bots (steel, faded), other players (their seat colour), the viewer's (with value tags). */
	private void stacks(GuiGraphicsExtractor g, RouletteLayout l, RouletteView v, String ph, double rMs, int win, double t) {
		boolean result = "result".equals(ph) && result() >= 0;
		boolean reduce = FxSettings.reduceMotion();
		int r = result();
		long now = Util.getMillis();
		if (result) chipFx.beginResult(state().getLongOr("phase_start", 0), rMs);
		if (result && !reduce && chipFx.cross(RouletteChipFx.RAKE_START)) {
			FxSounds.play("chip_sweep", 0.8f, 1f);
			FxSounds.play("roulette_rake", 1f, 1f);
		}
		for (Map.Entry<Spot, Long> e : spots("bot_chips").entrySet()) {
			RouletteFeltView.stackAt(g, l, e.getKey(), e.getValue(), 0xFF90A4AE, 0.6, 0, chipFx.dropOffset(e.getKey(), now));
		}
		// the rake (RESULT 650–1070): a bar sweeps up from the layout's bottom to the house edge; losers ride it and fade
		double rakeTop = l.layout.y() - 4;
		double rakeBottom = l.layout.bottom() + 2;
		double bar = result ? RouletteChipFx.rakeY(rMs, rakeBottom, rakeTop, reduce) : Double.NaN;
		for (Map.Entry<Spot, Long> e : spots("others").entrySet()) {
			Spot s = e.getKey();
			if (result && !s.covers(r)) {
				Box b = l.stack(s, 1);
				double[] ride = RouletteChipFx.rake(rMs, bar, b.bottom(), rakeTop, reduce);
				RouletteFeltView.stackAt(g, l, s, e.getValue(), 0xFFFFB74D, ride[1], 0, ride[0]);
			} else {
				RouletteFeltView.stackAt(g, l, s, e.getValue(), 0xFFFFB74D, 1, 0, result ? 0 : chipFx.dropOffset(s, now));
			}
		}
		if (result && !Double.isNaN(bar) && rMs < RouletteChipFx.RAKE_END + RouletteChipFx.RAKE_FADE && !reduce) {
			// the bar passes UNDER the winning stacks and the dolly (drawn before them)
			int x0 = l.layout.x() - 2;
			int w = l.layout.w() + 4;
			double a = rMs > RouletteChipFx.RAKE_END ? 1 - (rMs - RouletteChipFx.RAKE_END) / RouletteChipFx.RAKE_FADE : 1;
			dev.nezo.burmaldaholic.client.fx.motion.SnapDraw.blitF(g, TableGfx.sprite("roulette/rake"), x0, bar - 3, w, 6, TableGfx.fade(a),
				Math.max(1, l.grid.viewport.k()));
		}
		ListTag list = state().getListOrEmpty("bets");
		long[] rets = state().getLongArray("bet_returns").orElse(null);
		List<Box> placedTags = new ArrayList<>();
		var pending = chrome.pending();
		// value pills on every own stack while betting (hover / just placed only when crowded)
		boolean pillsAll = !result && list.size() <= PILLS_ALWAYS_MAX;
		int pills = 0;
		boolean spinning = "spin".equals(ph) || "no_more_bets".equals(ph);
		int k = Math.max(1, l.grid.viewport.k());
		Box balance = chrome.top.balance.box();
		double[] fly = new double[3];
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			Optional<Spot> sp = Spot.parseKey(b.getStringOr("spot", ""));
			if (sp.isEmpty()) continue;
			Spot s = sp.get();
			long amount = b.getLongOr("amount", 0);
			long ret = rets != null && i < rets.length ? rets[i] : -1;
			int n = Math.min(RouletteLayout.MAX_DISCS, ChipStack.discs(amount).size());
			Box box = l.stack(s, n);
			Box disc = new Box(box.x(), box.y(), l.chip, box.h());
			if (!result) {
				if (chipFx.flying(s, now)) continue; // its placement flight lands it (then this stack replaces it in place)
				// your chips shimmer during the spin (1 Hz, 1 px)
				double dy = spinning && !reduce && RouletteFx.motion("shimmer", false, recorder) && Math.sin(t / 1000.0 * 2 * Math.PI) > 0.7 ? -1 : 0;
				dy += chipFx.growOffset(s, now);
				drawStack(g, disc, amount, 1, 0, dy, k);
				boolean showTag = pillsAll || s.equals(lastPlaced) && Util.getMillis() - lastPlacedAt < 1500 || isHover(l, s);
				if (showTag && tag(g, l, s, n, Texts.number(amount), ValuePill.Style.YOU, placedTags)) pills++;
				continue;
			}
			boolean wins = ret > 0 || (ret < 0 && s.covers(r));
			if (!wins) {
				double[] ride = RouletteChipFx.rake(rMs, bar, disc.bottom(), rakeTop, reduce);
				if (ride[1] > 0.02) drawStack(g, disc, amount, ride[1], 0, ride[0], k);
				continue;
			}
			// winners: payout discs slide in beside the stack, then stack + payout fly to the balance
			RouletteChipFx.flyToBalance(rMs, disc, balance, reduce, fly);
			if (fly[2] <= 0.02) continue;
			drawStack(g, disc, amount, fly[2], fly[0], fly[1], k);
			long profit = ret > amount ? ret - amount : 0;
			if (profit > 0) {
				chipFx.payout(g, l, disc, profit, i, rMs, rakeTop, fly, reduce, k);
				if (rMs >= RouletteChipFx.PILL_AT && rMs < RouletteChipFx.FLY_START + 60) {
					tag(g, l, s, n, Texts.raw("+").append(Texts.number(profit)), ValuePill.Style.WIN, placedTags);
				}
			}
		}
		valuePills = pills;
		if (pending != null && !pending.spot().isEmpty()) {
			Spot.parseKey(pending.spot()).ifPresent(s -> {
				if (!chipFx.flying(s, now)) ChipStack.drawPending(g, l.stack(s, 1), pending.amount());
			});
		}
		// the dolly on the winning number (RESULT 400–650 drop, stays until betting)
		if (result) chipFx.dolly(g, l, r, rMs, reduce, k);
		else if ("betting".equals(ph) && phaseMs() < 300 && idleLast() >= 0) chipFx.dollyFade(g, l, idleLast(), phaseMs(), k);
	}

	/** An own stack (kit discs) at a float offset, snapped to the physical grid. */
	private static void drawStack(GuiGraphicsExtractor g, Box disc, long amount, double alpha, double dx, double dy, int k) {
		if (alpha <= 0.02) return;
		boolean moved = dx != 0 || dy != 0;
		if (moved) {
			g.pose().pushMatrix();
			g.pose().translate(PixelSnap.snapF(dx, k), PixelSnap.snapF(dy, k));
		}
		if (alpha >= 0.999) ChipStack.draw(g, disc, amount);
		else ChipStack.draw(g, disc, amount, (float) alpha);
		if (moved) g.pose().popMatrix();
	}

	private @Nullable Spot hoverSpot;

	private boolean isHover(RouletteLayout l, Spot s) {
		return s.equals(hoverSpot);
	}

	private boolean tag(GuiGraphicsExtractor g, RouletteLayout l, Spot s, int discs, Component text, ValuePill.Style style, List<Box> placed) {
		int w = ValuePill.width(font, text);
		Optional<Box> b = l.tag(s, discs, w, 11, placed);
		if (b.isEmpty()) return false;
		placed.add(b.get());
		ValuePill.draw(g, font, b.get(), text, style);
		return true;
	}

	/**
	 * The result beats: the banner + TopBar delta + the celebration (NICE and up) once the payout landed; a viewer with
	 * no bet on this spin sees the number ("17 RED") in the banner instead (R-CDE M4).
	 */
	private void resultBeats(double rMs) {
		long ps = state().getLongOr("phase_start", 0);
		long staked = state().getLongOr("out_staked", 0);
		long ret = state().getLongOr("out_return", 0);
		if (resultFor == ps) return;
		if (staked <= 0) {
			if (rMs < RouletteChipFx.RETURN_END) return;
			resultFor = ps;
			chrome.banner.show(numberWord(result()), 0, ResultBanner.SHOW_MS);
			return;
		}
		if (rMs < (FxSettings.reduceMotion() ? RouletteChipFx.PAY_START : RouletteChipFx.BANNER_AT)) return;
		resultFor = ps;
		long net = ret - staked;
		chrome.banner.show(net);
		chrome.top.balance.delta(net);
		WinTier tier = WinTier.valueOf(state().getStringOr("tier", net > 0 ? "WIN" : ret > 0 ? "RETURN" : "LOSS"));
		if (tier.ordinal() >= WinTier.NICE.ordinal() && FxSettings.celebrations() != FxSettings.Celebrations.OFF) {
			CelebrationOverlay.get().play(CelebrationRequest.core(tier, ret, staked, state().getIntOr("seed", 0)));
		} else if (net > 0) {
			FxSounds.play("win_small", 1f);
		} else if (ret > 0) {
			FxSounds.play("push", 1f);
		} else {
			FxSounds.play("lose", 1f);
		}
	}

	/** "17 RED", "0 ZERO": the banner word of a spin for a viewer without a bet. */
	public static Component numberWord(int n) {
		// T-C2 V7: the number and its colour word in the pocket's colour (red / white for black / green), never slate
		net.minecraft.ChatFormatting c = switch (Wheel.color(n)) {
			case RED -> net.minecraft.ChatFormatting.RED;
			case BLACK -> net.minecraft.ChatFormatting.WHITE;
			case GREEN -> net.minecraft.ChatFormatting.GREEN;
		};
		return Component.translatable("gui.burmaldaholic.roulette.v2.result", Texts.number(n).withStyle(c),
			Component.translatable("gui.burmaldaholic.roulette.v2.board.color." + Wheel.color(n).id()).withStyle(c));
	}

	/** "Split 17-20", "2nd dozen (13–24)", "Red (1:1)". */
	static Component describe(Spot s) {
		return switch (s.type()) {
			case FIRST_FOUR -> Component.translatable("gui.burmaldaholic.roulette.bet.first_four");
			case STRAIGHT, SPLIT, STREET, TRIO, CORNER, SIX_LINE ->Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id(), Texts.raw(s.label()));
			case DOZEN, COLUMN -> Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id() + "." + s.outsideIndex());
			case RED -> Component.translatable("gui.burmaldaholic.roulette.color.red");
			case BLACK -> Component.translatable("gui.burmaldaholic.roulette.color.black");
			case ODD -> Component.translatable("gui.burmaldaholic.roulette.short.odd");
			case EVEN -> Component.translatable("gui.burmaldaholic.roulette.short.even");
			case LOW -> Texts.raw("1–18");
			case HIGH -> Texts.raw("19–36");
		};
	}
}
