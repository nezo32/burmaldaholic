package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.motion.MotionFx;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceAudio;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceDraw;
import dev.nezo.burmaldaholic.client.table.fx.dice.DieAtlas;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Felt;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceEvent;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRollCache;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.StageMap;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.present.CrapsChipBeats;
import dev.nezo.burmaldaholic.games.craps.present.CrapsFlow;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout.Layer;
import dev.nezo.burmaldaholic.games.craps.present.CrapsStick;
import dev.nezo.burmaldaholic.games.craps.present.CrapsThrow;
import dev.nezo.burmaldaholic.games.craps.present.CrapsView;
import dev.nezo.burmaldaholic.games.craps.present.CrapsView.BetPub;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Craps, table UI v2 (REDESIGN.md §7.6; docs/architecture/ui-v2.md, lane D; motion CM = CRAPS-ANIM-SPEC). The felt
 * fills the PlayArea ({@link CrapsLayout}): the rubber-pyramid back wall, the place row with the puck, the COME row with
 * the dice zone and the total, the FIELD, the DON'T PASS BAR and the PASS LINE with its odds lane and dice home at both
 * ends. The Dock is the kit's (chip tray, Undo / Clear, status, {@code Sit down} → {@code Roll}).
 *
 * <p>The roll (CS §2–§5) is the shared dice kit: the server's faces, the shooter's side and the cosmetic seed give one
 * {@link CrapsThrow} solve (cached per roll) — the dice leave the shooter's hand at dice home, land on the felt (or fly
 * on), smack the back wall's pyramids (the hit diamond flashes), bounce, roll over their edges, rock and rest anywhere
 * in the COME row's zone, the top faces produced by the motion itself (orientation atlas, physical-pixel snapped). The
 * storyboard {@link CrapsBeats#of(int, double)} hangs off the server's rest time: total pill, puck flip and glide, the
 * chips in casino order ({@link CrapsChipBeats}: take, travel and pay, collect), the stickman's return
 * ({@link CrapsStick}), idle. The throw and the puck are shared time; chips / stick / idle follow the speed setting.
 * Reduced motion has its own path (CS §4.8). Late joiners see the settled table; Space / a click skips the chip beats.
 */
public class CrapsScreen extends V2TableScreen<CrapsView> {
	private static final String K = "gui.burmaldaholic.craps.";
	private static final String K2 = CrapsFlow.K;
	/** One-shot cues of a roll (bit index). */
	private static final int CUE_PUCK_LIFT = 0, CUE_PUCK = 1, CUE_SWEEP = 2, CUE_STICK = 3, CUE_BANNER = 4, CUE_COLLECT = 5, CUE_REDUCED_FELT = 6,
		CUE_TRAVEL = 7;
	/** Felt / wall effects of the throw (closed form in the time since their event). */
	private static final int MAX_FX = 32;
	private static final double DUST_MS = 160, SPARK_MS = 220, HIT_MS = 40, HOT_MS = 300, JOLT_MS = 50;

	private final TableClock clock = new TableClock();
	private CrapsLayout layout = CrapsLayout.of(TableGrid.of(480, 270));
	private int k = 1;
	private float partial;
	private boolean showRules;
	private @Nullable String hoverSpot;
	/** spots a click would place on now (lit while a chip is armed) */
	private final Set<String> live = new HashSet<>();
	// ---- the roll ----
	private final DiceRollCache cache = new DiceRollCache(4);
	private int shownRolls = -1;
	private @Nullable DiceRoll roll;
	private int side = CrapsThrow.RIGHT;
	private int[] faces = {4, 2};
	private CrapsBeats beats = CrapsBeats.of(1700);
	private boolean lateRoll;
	private boolean skipped;
	private long rollPressedAt = -1;
	private int settledFor = -1;
	private boolean rollingShown;
	private boolean settlingShown;
	private long lastSecond = -1;
	private long cues;
	private int lastPuckFrom, lastPuckTo;
	/** The previous roll's total (fades out at the next RELEASE). */
	private int prevTotal;
	private int prevTotalColor;
	private @Nullable EventCursor cursor;
	private final DiceAudio audio = DiceAudio.gui();
	private final EventCursor.Listener visual = this::onDiceEvent;
	private CrapsChipBeats.@Nullable Plan chipPlan;
	private final CrapsChipBeats.Pos pos = new CrapsChipBeats.Pos();
	private final CrapsBeats.Puck puck = new CrapsBeats.Puck();
	private final CrapsStick.Out stick = new CrapsStick.Out();
	private final DieSample[] ds = {new DieSample(), new DieSample()};
	private final DieSample after = new DieSample();
	private final DieSample tmp = new DieSample();
	private final DiceDraw.Scratch scratch = new DiceDraw.Scratch();
	private final double[] qTmp = new double[4];
	private final double[] qTmp2 = new double[4];
	private final double[] restXY = new double[4];
	private final double[] homeXY = new double[4];
	// fx ring: kind (0 dust, 1 spark, 2 wall hit), time, x, y (arena units), aux
	private final int[] fxKind = new int[MAX_FX];
	private final double[] fxT = new double[MAX_FX], fxX = new double[MAX_FX], fxY = new double[MAX_FX];
	private int fxNext;
	// ---- local chip flights (placing) ----
	private record Placing(String spot, long amount, long denom, Box from, Box to, long start, long myTotalAtSend, boolean odds) {}

	private @Nullable Placing placing;
	private @Nullable Placing refused;
	private long refusedAt;
	private boolean placeLanded;

	public CrapsScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(K + "title"));
		chrome.bet.multiSpot(true);
		chrome.bet.tools(BetGates.UNDO, BetGates.CLEAR);
	}

	// ---- the game's pure parts ----------------------------------------------------------------------------------------

	@Override
	protected CrapsView view(CompoundTag s) {
		CrapsView v = CrapsView.read(s, secondsLeft(s, "window"), secondsLeft(s, "roll"), rolling(), settling());
		// T-D2 V6: while the throw is shown the felt (and the Dock's "on table") show the bets as they were before it
		return rolling() ? v.withBets(prevBets()) : v;
	}

	@Override
	protected Flow<CrapsView> flow() {
		return CrapsFlow.FLOW;
	}

	@Override
	protected void onStateChanged(CompoundTag s) {
		clock.sync(s.getLongOr("game_time", 0));
		int rolls = s.getIntOr("rolls", 0);
		if (rolls != shownRolls) {
			boolean first = shownRolls < 0;
			if (!first && roll != null) {
				prevTotal = faces[0] + faces[1];
				prevTotalColor = totalColor(lastEvent);
			}
			shownRolls = rolls;
			rollPressedAt = -1;
			roll = null;
			chipPlan = null;
			cues = 0;
			int d1 = s.getIntOr("d1", 0);
			int d2 = s.getIntOr("d2", 0);
			if (d1 > 0 && d2 > 0) {
				newThrow(s, d1, d2);
				lateRoll = first || rollMs(s) >= beats.CHIPS + 0.85 * (beats.IDLE - beats.CHIPS);
			}
			skipped = false;
			if (lateRoll) settledFor = rolls;
			lastEvent = s.getStringOr("event", "");
			lastPuckFrom = s.getIntOr("point_before", 0);
			lastPuckTo = s.getIntOr("point", 0);
		}
		super.onStateChanged(s);
		rollingShown = rolling();
		settlingShown = settling();
		statusLines();
	}

	private String lastEvent = "";

	/** Solves the throw once (cache by roll counter, side and seed) and rewinds the event cursor. */
	private void newThrow(CompoundTag s, int d1, int d2) {
		int seed = s.getIntOr("seed", 0);
		side = s.contains("shooter_side") ? s.getIntOr("shooter_side", 0) : (s.getIntOr("shooter_dir", 0) == 1 ? CrapsThrow.LEFT : CrapsThrow.RIGHT);
		int sd = side;
		int pb = s.getIntOr("point_before", 0);
		faces = new int[] {d1, d2};
		roll = cache.get(DiceRollCache.key(s.getIntOr("rolls", 0), sd, seed), () -> CrapsThrow.solve(sd, seed, d1, d2, pb));
		int serverRest = s.getIntOr("rest_ms", 0);
		int rest = roll.restMs();
		if (serverRest > 0 && serverRest != rest) {
			// the server's rest time paces the beats; a client solve that differs is logged once (dev check, CS §3.1)
			if (!restMismatchLogged) {
				restMismatchLogged = true;
				Burmaldaholic.LOGGER.warn("craps: client throw rests at {} ms, server at {} ms (roll {})", rest, serverRest, s.getIntOr("rolls", 0));
			}
			rest = serverRest;
		}
		beats = CrapsBeats.of(rest, FxSettings.speed().pct / 100.0);
		cursor = DiceAudio.cursor(roll.events());
		double t = rollMs(s);
		if (t > 0) cursor.reset(t, true);
		fxNext = 0;
		java.util.Arrays.fill(fxT, Double.NEGATIVE_INFINITY);
	}

	private static boolean restMismatchLogged;
	private int layoutStrip = -1;

	@Override
	protected void layoutPlay(TableGrid grid, CrapsView v, HitMap hits) {
		var dock = chrome.dock();
		int strip = dock == null || dock.hintStrip().isEmpty() ? 0 : dock.hintStrip().h();
		layoutStrip = strip;
		CrapsLayout l = CrapsLayout.of(grid, strip);
		if (!l.felt.equals(layout.felt)) chipPlan = null;
		layout = l;
		live.clear();
		long chip = Math.max(1, chrome.bet.selected());
		for (Map.Entry<String, Box> e : l.spots().entrySet()) {
			boolean sends = CrapsFlow.click(v, e.getKey(), chip).sends();
			if (sends) live.add(e.getKey());
			hits.add(e.getKey(), e.getValue(), true);
		}
		List<Box> obstacles = new ArrayList<>(l.glyphs());
		obstacles.add(l.diceZone.inset(-l.die / 2));
		obstacles.add(l.total);
		chrome.banner.regions(List.of(l.bannerRegion()), obstacles.stream().filter(b -> b != l.comeWord).toList());
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		if (!id.startsWith("felt.") || view == null) return false;
		if (button.right()) {
			// right click = Undo, through the same gate as the tool (R-CDE M5: closed while the dice fly)
			takeBack(BetGates.UNDO);
			return true;
		}
		if (!button.left()) return true; // scroll over the felt: nothing
		long chip = chrome.bet.armOrDefault();
		if (chip <= 0) {
			chrome.status.flash(V2Draw.tr(K2 + "reason.not_enough", view.min()));
			return true;
		}
		CrapsFlow.Click c = CrapsFlow.click(view, id, chip);
		if (!c.sends()) {
			chrome.status.flash(V2Draw.tr(c.errorKey(), c.errorArgs()));
			FxSounds.play("ui_deny", 1f);
			return true;
		}
		CompoundTag args = new CompoundTag();
		if (c.action().equals("bet")) {
			args.putString("kind", c.kind());
		} else {
			args.putInt("bet", c.betId());
		}
		args.putLong("amount", c.amount());
		sendTracked(c.action(), args, id, c.amount());
		startPlacing(id, c.action(), c.amount(), chip);
		// T-D2 P-1: a chip under the table minimum bets the minimum: say so
		if (c.action().equals("bet") && chip < view.min()) chrome.status.flash(V2Draw.tr(K2 + "reason.min_raised", Texts.number(view.min())));
		return true;
	}

	/** CS §4.6.1: one disc of the armed denomination flies from its Dock tray button to the stack (sound on landing). */
	private void startPlacing(String spot, String action, long amount, long denom) {
		boolean odds = "odds".equals(action) || CrapsLayout.kindOf(spot).equals("pass_odds");
		Box to = pendingBox(spot, action);
		Box from = odds ? flatBoxOf(spot) : trayBox(denom);
		placing = new Placing(spot, amount, denom, from, to, Util.getMillis(), view == null ? 0 : view.myTotal(), odds);
		placeLanded = false;
	}

	@Override
	protected @Nullable BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				// multi-spot: chips arm (the chrome never calls add); a stray call bets the Pass line on the come-out
				if (view != null) onTarget(CrapsLayout.PASS, true, Press.LEFT);
			}

			@Override
			public void remove(long denom) {
				takeBack(BetGates.UNDO);
			}

			@Override
			public void tool(String toolId) {
				takeBack(toolId);
			}
		};
	}

	/** Undo / Clear: sent only when {@link CrapsFlow#tool} allows it, else its reason is flashed (never a silent packet). */
	private void takeBack(String toolId) {
		if (view == null) return;
		Gate g = CrapsFlow.tool(toolId, view(state()));
		if (!g.enabled()) {
			chrome.status.flash(V2Draw.tr(g.reasonKey(), g.reasonArgs()));
			FxSounds.play("ui_deny", 1f);
			return;
		}
		sendTracked(toolId);
	}

	@Override
	public void onGate(String id, Gate gate) {
		if ("roll".equals(gate.action())) {
			rollPressedAt = Util.getMillis();
			if (!FxSettings.reduceMotion()) FxSounds.play("dice_cup", 0.8f, 1.8f);
		}
		super.onGate(id, gate);
	}

	@Override
	protected void openRules() {
		showRules = !showRules;
	}

	// ---- state helpers ------------------------------------------------------------------------------------------------

	private long secondsLeft(CompoundTag s, String timer) {
		long ticks = s.getCompoundOrEmpty("timers").getLongOr(timer, -1);
		if (ticks < 0) return -1;
		double elapsed = clock.msSince(s.getLongOr("game_time", 0), partial) / 50.0;
		return (long) Math.max(0, Math.ceil((ticks - elapsed) / 20.0));
	}

	private double rollMs(CompoundTag s) {
		return clock.msSince(s.getLongOr("roll_time", 0), partial);
	}

	/** ms into the roll storyboard (IDLE and beyond = settled). */
	private double t() {
		if (roll == null || lateRoll) return beats.IDLE + 1;
		double t = rollMs(state());
		if (skipped && t >= beats.BADGE) return Math.max(t, beats.IDLE + 1);
		return t;
	}

	/** The throw is still being shown: the felt and Undo are closed until the chips move (D5). */
	private boolean rolling() {
		return roll != null && !lateRoll && t() < beats.CHIPS;
	}

	/** The roll is still being settled on the felt: Roll waits for the stick and the idle beat (D5). */
	private boolean settling() {
		return roll != null && !lateRoll && t() < beats.IDLE;
	}

	private List<BetPub> prevBets() {
		List<BetPub> out = new ArrayList<>();
		ListTag list = state().getListOrEmpty("prev_bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			out.add(new BetPub(-1, t.getStringOr("kind", ""), t.getLongOr("flat", 0), t.getLongOr("odds", 0), t.getIntOr("point", 0),
				t.getBooleanOr("mine", false), 1, 0, 0, false));
		}
		return out;
	}

	private List<CrapsChipBeats.Res> lastRes() {
		List<CrapsChipBeats.Res> out = new ArrayList<>();
		ListTag list = state().getListOrEmpty("last_res");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			out.add(new CrapsChipBeats.Res(t.getStringOr("kind", ""), t.getIntOr("point", 0), t.getBooleanOr("mine", false), t.getStringOr("outcome", ""),
				t.getLongOr("flat", 0), t.getLongOr("odds", 0), t.getLongOr("ret", 0), t.getIntOr("moved_to", 0)));
		}
		return out;
	}

	private void statusLines() {
		if (view == null) return;
		CrapsFlow.Status st = CrapsFlow.status(view);
		Gate p = CrapsFlow.primary(view);
		Component l1 = V2Draw.tr(st.line1().key(), st.line1().args());
		Component l2 = V2Draw.tr(st.line2().key(), st.line2().args());
		// a disabled primary shows its reason on line 1: then line 2 carries the point
		chrome.status.set(l1, p.enabled() ? l2 : l1);
		if (hoverSpot != null) {
			CrapsFlow.Line h = CrapsFlow.hover(hoverSpot, view);
			chrome.status.set(l1, V2Draw.tr(h.key(), h.args()));
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		boolean r = rolling();
		boolean st = settling();
		long sec = Util.getMillis() / 1000;
		if (r != rollingShown || st != settlingShown || sec != lastSecond) {
			rollingShown = r;
			settlingShown = st;
			lastSecond = sec;
			view = view(state());
			refresh();
			statusLines();
		}
	}

	// ---- input --------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (showRules) {
			showRules = false;
			return true;
		}
		if (skipBeats()) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == Inputs.KEY_SPACE && !chrome.primary.gate().enabled() && skipBeats()) return true;
		if (event.isEscape() && showRules) {
			showRules = false;
			return true;
		}
		return super.keyPressed(event);
	}

	/** Space / a click while the chips resolve: jump to the settled table (dice at home). */
	private boolean skipBeats() {
		if (roll == null || skipped || lateRoll) return false;
		double t = rollMs(state());
		if (t >= beats.BADGE && t < beats.IDLE) {
			skipped = true;
			view = view(state());
			refresh();
			statusLines();
			return true;
		}
		return false;
	}

	// ---- drawing ------------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, CrapsView v, int mx, int my, float a) {
		partial = a;
		k = Math.max(1, grid.viewport.k());
		var dock = chrome.dock();
		int strip = dock == null || dock.hintStrip().isEmpty() ? 0 : dock.hintStrip().h();
		if (strip != layoutStrip) refresh(); // the Dock opened / closed its hint strip after the felt was laid out
		CrapsLayout l = layout;
		CasinoTheme theme = theme();
		int print = V2Felt.print(theme);
		String hover = null;
		if (!showRules) {
			Optional<HitMap.Target> tgt = chrome.hits().at(mx, my);
			if (tgt.isPresent() && tgt.get().id().startsWith("felt.")) hover = tgt.get().id();
		}
		if (!java.util.Objects.equals(hover, hoverSpot)) {
			hoverSpot = hover;
			statusLines();
		}
		double t = t();
		CompoundTag s = state();
		int point = v.point();
		int pointBefore = s.getIntOr("point_before", point);
		boolean reduced = FxSettings.reduceMotion();
		// the event cursor fires dice sounds and the felt / wall effects exactly once at any fps
		if (cursor != null && roll != null) {
			if (lateRoll || skipped) cursor.reset(t, true);
			audio.advance(cursor, Math.min(t, beats.IDLE), 0, visual);
		}
		int ringPoint = t >= beats.PUCK + CrapsBeats.puckTouchdown() ? point : pointBefore;
		feltLines(g, l, print, ringPoint, chrome.bet.armed(), t);
		labels(g, l, print);
		backWall(g, l, t, reduced);
		chips(g, l, v, t, reduced);
		DiceState d = dice(g, l, s, t, reduced);
		puck(g, l, t, pointBefore, point, reduced);
		airborne(g, l, d, t);
		stick(g, l, t, reduced);
		effects(g, l, t);
		totalPill(g, l, s, t);
		collectFlights(g, t);
		placingFlight(g);
		cues(t);
		settle(t);
	}

	private void feltLines(GuiGraphicsExtractor g, CrapsLayout l, int print, int point, boolean armed, double t) {
		int line = V2Draw.alpha(print, 0.85f);
		for (Map.Entry<String, Box> e : l.spots().entrySet()) {
			String id = e.getKey();
			Box b = e.getValue();
			if (id.equals(CrapsLayout.PASS_ODDS)) continue;
			boolean lit = armed && live.contains(id);
			if (id.equals(hoverSpot)) {
				V2Draw.fill(g, b, live.contains(id) ? 0x24FFFFFF : 0x10FFFFFF);
			} else if (lit) {
				V2Draw.fill(g, b, 0x10FFD640);
			}
			V2Draw.outline(g, b, line);
			if (id.equals(CrapsLayout.PASS)) {
				V2Draw.outline(g, b, CasinoPalette.V2_GOLD);
				V2Draw.outline(g, b.inset(1), V2Draw.alpha(CasinoPalette.V2_GOLD, 0.45f));
			}
			if (id.equals(hoverSpot) && live.contains(id)) V2Draw.outline(g, b.inset(1), CasinoPalette.V2_GOLD);
		}
		if (CrapsLayout.PASS_ODDS.equals(hoverSpot)) {
			V2Draw.fill(g, l.passOdds.inset(2), live.contains(CrapsLayout.PASS_ODDS) ? 0x24FFFFFF : 0x10FFFFFF);
		}
		if (point != 0) {
			Box b = l.point(point);
			// CS §4.5: on point set the ring pulses twice at 2 Hz, on point made it flashes 3 times at 2.5 Hz, then holds
			double since = t - (beats.PUCK + CrapsBeats.puckTouchdown());
			float a = 1f;
			if (!FxSettings.reduceMotion() && since >= 0 && since < 1000 && "point_set".equals(lastEvent)) {
				a = (float) (0.55 + 0.45 * Math.cos(2 * Math.PI * 2 * since / 1000.0));
			}
			V2Draw.outline(g, b, V2Draw.alpha(CasinoPalette.V2_GOLD, a));
			V2Draw.outline(g, b.inset(1), V2Draw.alpha(CasinoPalette.V2_GOLD, a));
		}
		if ("point_made".equals(lastEvent) && !FxSettings.reduceMotion()) {
			int made = state().getIntOr("point_before", 0);
			double since = t - beats.BADGE;
			if (made != 0 && since >= 0 && since < 1200) {
				Box b = l.point(made);
				boolean on = ((int) (since / 200)) % 2 == 0;
				if (on && MotionFx.flash("craps_point_made")) {
					V2Draw.fill(g, b.inset(1), 0x30FFD640);
					V2Draw.outline(g, b, CasinoPalette.V2_WIN);
					V2Draw.outline(g, b.inset(1), CasinoPalette.V2_WIN);
				}
				confetti(g, b, since);
			}
		}
	}

	/** 24 confetti px from a made point's box (closed form in the time since the badge). */
	private void confetti(GuiGraphicsExtractor g, Box b, double since) {
		if (!MotionFx.particles("craps_confetti", 24)) return;
		int[] cols = {CasinoPalette.V2_GOLD, CasinoPalette.V2_WIN, 0xFFFFFFFF, 0xFFE05AA0};
		double s = since / 1000.0;
		if (s > 1.0) return;
		for (int i = 0; i < 24; i++) {
			double ang = -Math.PI / 2 + (i * 2.399963) % (Math.PI * 1.6) - Math.PI * 0.8;
			double sp = 40 + (i * 37 % 50);
			double x = b.cx() + Math.cos(ang) * sp * s;
			double y = b.cy() + Math.sin(ang) * sp * s + 90 * s * s;
			int a = (int) (255 * Math.max(0, 1 - s));
			pixel(g, x, y, (a << 24) | (cols[i % cols.length] & 0xFFFFFF));
		}
	}

	private void pixel(GuiGraphicsExtractor g, double x, double y, int argb) {
		g.pose().pushMatrix();
		snap(g, x, y);
		g.fill(0, 0, 1, 1, argb);
		g.pose().popMatrix();
	}

	/** Translates the pose to (x, y) snapped to physical pixels (moving things, CS §4.2). */
	private void snap(GuiGraphicsExtractor g, double x, double y) {
		g.pose().translate((float) (Math.round(x * k) / (double) k), (float) (Math.round(y * k) / (double) k));
	}

	private void labels(GuiGraphicsExtractor g, CrapsLayout l, int print) {
		Font f = font;
		// DON'T COME: two lines centred in its box
		Box dc = l.dontCome;
		List<FormattedCharSequence> dcl = f.split(Component.translatable(K + "layout.dont_come"), dc.w() - 6);
		int dy = dc.y() + (dc.h() - dcl.size() * 10) / 2 + 1;
		for (FormattedCharSequence seq : dcl) {
			g.text(f, seq, dc.x() + (dc.w() - f.width(seq)) / 2, dy, print, true);
			dy += 10;
		}
		for (int i = 0; i < CrapsLayout.POINTS.length; i++) {
			Box gl = l.pointGlyphs[i];
			Component n = Texts.number(CrapsLayout.POINTS[i]);
			int w = f.width(n) * l.pointScale;
			V2Draw.text(g, f, n, gl.cx() - w / 2, gl.y(), l.pointScale, print, true);
		}
		// T-D2 V3: the result banner may sit on the COME word: then the word is not printed under it
		if (!chrome.banner.active()) big(g, Component.translatable(K + "layout.come"), l.comeWord, print);
		big(g, Component.translatable(K + "layout.field"), l.fieldWord, print);
		int numScale = l.tier == Tier.T3 ? 2 : 1;
		for (int i = 0; i < l.fieldNumbers.length; i++) {
			int n = l.fieldNumbers[i];
			Box gl = l.fieldGlyphs[i];
			boolean gold = n == 2 || n == 12;
			int c = gold ? CasinoPalette.V2_GOLD : print;
			Component num = Texts.number(n);
			int w = f.width(num) * numScale;
			V2Draw.text(g, f, num, gl.cx() - w / 2, gl.y(), numScale, c, true);
			if (gold) {
				int mult = n == 2 ? state().getIntOr("field2", 2) : state().getIntOr("field12", 3);
				Component m = Component.translatable(K2 + "field_mult", Texts.number(mult));
				V2Draw.text(g, f, m, gl.cx() - f.width(m) / 2, gl.y() + 8 * numScale + 2, 1, c, true);
			}
		}
		small(g, Component.translatable(K + "layout.dont_pass"), l.dontPass, l.dontPassWord.cx(), print);
		small(g, Component.translatable(K + "layout.pass_line"), l.pass, l.passWord.cx(), print);
		Component odds = Component.translatable(K2 + "caption.odds");
		V2Draw.text(g, f, odds, l.oddsWord.x(), l.pass.cy() - 4, 1, V2Draw.alpha(print, 0.8f), true);
	}

	/** A felt word at 2× when it fits its box, else 1×, centred. */
	private void big(GuiGraphicsExtractor g, Component c, Box b, int color) {
		int w = font.width(c);
		int scale = w * 2 <= b.w() + 8 ? 2 : 1;
		V2Draw.text(g, font, c, b.cx() - w * scale / 2, b.cy() - 4 * scale + 1, scale, color, true);
	}

	private void small(GuiGraphicsExtractor g, Component c, Box row, int cx, int color) {
		int max = row.w() / 3;
		FormattedCharSequence seq = font.width(c) <= max ? c.getVisualOrderText() : dev.nezo.burmaldaholic.client.ui.CasinoUi.fit(font, c, max);
		g.text(font, seq, cx - font.width(seq) / 2, row.cy() - 3, color, true);
	}

	// ---- the back wall ----------------------------------------------------------------------------------------------

	private static final int TILE = 16;

	private Identifier wallSheet(int h) {
		return TableGfx.sheet("craps/v2/back_wall_" + h);
	}

	/** The rubber-pyramid strip and the diamond each die hit (white for one frame, then gold fading; CS §4.3). */
	private void backWall(GuiGraphicsExtractor g, CrapsLayout l, double t, boolean reduced) {
		Box w = l.backWall;
		int h = w.h();
		Identifier tex = wallSheet(h);
		for (int x = w.x(); x < w.right(); x += TILE) {
			int tw = Math.min(TILE, w.right() - x);
			int jolt = 0;
			if (!reduced) {
				for (int i = 0; i < MAX_FX; i++) {
					if (fxKind[i] == 2 && t - fxT[i] >= 0 && t - fxT[i] < JOLT_MS) {
						double gx = l.arenaMap().x(fxX[i]);
						if (gx >= x && gx < x + TILE) jolt = -1;
					}
				}
			}
			TableGfx.region(g, tex, TILE, 3 * h, 0, 0, tw, h + jolt, x, w.y(), 0xFFFFFFFF);
		}
		// hit diamonds
		for (int i = 0; i < MAX_FX; i++) {
			if (fxKind[i] != 2) continue;
			double since = t - fxT[i];
			double life = reduced ? 600 : HIT_MS + HOT_MS;
			if (since < 0 || since >= life) continue;
			double gx = l.arenaMap().x(fxX[i]);
			int di = (int) Math.floor((gx - w.x()) / 4.0);
			int dx = w.x() + Math.max(0, Math.min((w.w() - 4) / 4, di)) * 4;
			boolean flashes = FxSettings.flashes();
			if (!reduced && since < HIT_MS && flashes) {
				TableGfx.region(g, tex, TILE, 3 * h, 0, h, 4, h, dx, w.y(), 0xFFFFFFFF);
			} else {
				double a = reduced ? 1 : 1 - (since - (flashes ? HIT_MS : 0)) / HOT_MS;
				if (!flashes) a *= 0.5;
				a = Math.max(0, Math.min(1, a));
				TableGfx.region(g, tex, TILE, 3 * h, 0, 2 * h, 4, h, dx, w.y(), TableGfx.alpha(0xFFFFFFFF, a));
			}
		}
	}

	// ---- dice -------------------------------------------------------------------------------------------------------

	/** What {@link #dice} drew and what is left for the airborne layer. */
	private record DiceState(boolean[] air) {}

	private final boolean[] airFlags = new boolean[2];
	private final DiceState diceState = new DiceState(airFlags);

	private StageMap map() {
		return layout.arenaMap();
	}

	/**
	 * The dice on the felt (shadows + dice with z &lt; 0.3) and the airborne ones' flags: the rattle in the shooter's hand,
	 * the throw, the rest in the zone, the stickman's drag and dice home (CS §4.2 layers 4–5).
	 */
	private DiceState dice(GuiGraphicsExtractor g, CrapsLayout l, CompoundTag s, double t, boolean reduced) {
		airFlags[0] = airFlags[1] = false;
		StageMap map = map();
		int cell = l.die;
		long now = Util.getMillis();
		DiceRoll r = roll;
		boolean rattling = rollPressedAt >= 0 && now - rollPressedAt < 900 && (r == null || rollMs(s) < 0 || stale());
		if (r == null || rattling) {
			// no throw shown: the dice wait at home (the last faces), rattling in the hand while Roll is pressed
			homeSamples(side, r);
			for (int d = 0; d < 2; d++) {
				DieSample x = ds[d];
				if (rattling && !reduced) {
					double p = Math.min(1, (now - rollPressedAt) / 100.0);
					x.zc += 0.4 * Ease.OUT_CUBIC.apply(p) / map.sy() * map.sy();
					int step = (int) ((now - rollPressedAt) / 60);
					double yaw = Math.toRadians(((step + d) % 2 == 0 ? 8 : -8));
					Quat.axisAngle(0, 0, 1, yaw, qTmp);
					Quat.mul(qTmp, x.q, x.q);
					double jit = ((step * 7 + d * 3) % 3 - 1) / (double) k;
					x.x += jit / map.sx();
				}
				DiceDraw.shadow(g, x, map, cell, k, 1);
			}
			for (int d = 0; d < 2; d++) DiceDraw.die(g, ds[d], map, cell, k, 0xFFFFFFFF, scratch);
			return diceState;
		}
		if (reduced) {
			// CS §4.8: no arcs — the dice fade in at their rest points over 150 ms, then the stick is a cross-fade
			double a = Math.min(1, Math.max(0, t / 150.0));
			for (int d = 0; d < 2; d++) r.sample(d, 1e9, ds[d]);
			if (t >= beats.STICK) {
				double k2 = Math.min(1, (t - beats.STICK) / 150.0);
				drawPair(g, map, cell, 1 - k2, false);
				homeSamples(side, r);
				drawPair(g, map, cell, k2, true);
			} else {
				drawPair(g, map, cell, a, false);
			}
			return diceState;
		}
		if (t >= beats.STICK) {
			// the stickman's return: the dice slide and keep their top faces; yaw squares up during the drag
			double tR = r.restTime(0) > r.restTime(1) ? r.restTime(0) : r.restTime(1);
			for (int d = 0; d < 2; d++) {
				r.sample(d, tR + 1e6, ds[d]);
				restXY[2 * d] = map.x(ds[d].x);
				restXY[2 * d + 1] = map.y(ds[d].y);
			}
			homeXY(side);
			CrapsStick.sample(t, beats, restXY, homeXY, l.felt.y() - 2, cell, stick);
			for (int d = 0; d < 2; d++) {
				DieSample x = ds[d];
				x.x = map.u(stick.x[d]);
				x.y = map.v(stick.y[d]);
				if (stick.drag >= 0.5) {
					CubeGroup.element(CubeGroup.nearest(x.q), qTmp);
					Quat.copy(qTmp, x.q);
				}
				DiceDraw.shadow(g, x, map, cell, k, 1);
			}
			for (int d = 0; d < 2; d++) DiceDraw.die(g, ds[d], map, cell, k, 0xFFFFFFFF, scratch);
			return diceState;
		}
		// the throw
		for (int d = 0; d < 2; d++) {
			r.sample(d, t, ds[d]);
			airFlags[d] = ds[d].zLow >= 0.3;
		}
		for (int d = 0; d < 2; d++) if (!airFlags[d]) DiceDraw.shadow(g, ds[d], map, cell, k, 1);
		for (int d = 0; d < 2; d++) if (!airFlags[d]) drawDie(g, r, d, t, map, cell);
		// the rest glint: one frame (≥ 33 ms) when the later die rests
		double restAt = beats.rest;
		if (t >= restAt && t < restAt + 50 && FxSettings.flashes()) {
			for (int d = 0; d < 2; d++) DiceDraw.restGlint(g, ds[d], map, cell, k, 0xFFFFFFFF);
		}
		return diceState;
	}

	/** One die of the throw with its after-image (|ω| &gt; 9 rad/s: the frame 14 ms earlier at α 0.35). */
	private void drawDie(GuiGraphicsExtractor g, DiceRoll r, int d, double t, StageMap map, int cell) {
		DieSample x = ds[d];
		if (x.omega > DiceDraw.AFTER_OMEGA && MotionFx.streak("dice_after_image")) {
			r.sample(d, t - DiceDraw.AFTER_MS, after);
			DiceDraw.die(g, after, map, cell, k, ((int) Math.round(255 * DiceDraw.AFTER_ALPHA) << 24) | 0xFFFFFF, scratch);
		}
		DiceDraw.die(g, x, map, cell, k, 0xFFFFFFFF, scratch);
	}

	/** Airborne dice (z ≥ 0.3) over chips and the puck, with their shadows (CS §4.2 layers 7–8). */
	private void airborne(GuiGraphicsExtractor g, CrapsLayout l, DiceState st, double t) {
		DiceRoll r = roll;
		if (r == null || (!st.air()[0] && !st.air()[1])) return;
		StageMap map = map();
		for (int d = 0; d < 2; d++) if (st.air()[d]) DiceDraw.shadow(g, ds[d], map, l.die, k, 1);
		for (int d = 0; d < 2; d++) if (st.air()[d]) drawDie(g, r, d, t, map, l.die);
	}

	private void drawPair(GuiGraphicsExtractor g, StageMap map, int cell, double alpha, boolean shadow) {
		if (alpha <= 0.01) return;
		int a = (int) Math.round(255 * Math.min(1, alpha));
		for (int d = 0; d < 2; d++) DiceDraw.shadow(g, ds[d], map, cell, k, alpha);
		for (int d = 0; d < 2; d++) DiceDraw.die(g, ds[d], map, cell, k, (a << 24) | 0xFFFFFF, scratch);
	}

	/** The dice resting at the shooter's dice home: the last roll's faces (squared up), else 4 and 2. */
	private void homeSamples(int sd, @Nullable DiceRoll r) {
		for (int d = 0; d < 2; d++) {
			DieSample x = ds[d];
			if (r != null) {
				r.sample(d, 1e9, x);
				CubeGroup.element(CubeGroup.nearest(x.q), qTmp);
				Quat.copy(qTmp, x.q);
			} else {
				CubeGroup.element(CubeGroup.index(faces[d], d), qTmp);
				Quat.copy(qTmp, x.q);
				x.zLow = 0;
				x.zc = 1;
				x.phase = DieSample.REST;
				x.omega = 0;
			}
			double[] p = CrapsThrow.release(sd, d);
			x.x = p[0];
			x.y = p[1];
		}
	}

	private void homeXY(int sd) {
		StageMap map = map();
		for (int d = 0; d < 2; d++) {
			double[] p = CrapsThrow.release(sd, d);
			homeXY[2 * d] = map.x(p[0]);
			homeXY[2 * d + 1] = map.y(p[1]);
		}
	}

	private boolean stale() {
		return state().getIntOr("rolls", 0) == shownRolls && rollMs(state()) > beats.IDLE;
	}

	// ---- the stick ---------------------------------------------------------------------------------------------------

	/** The stickman's stick (CS §4.7): a dowel from beyond the wall to the hook behind the dice, drawn as pixel rows. */
	private void stick(GuiGraphicsExtractor g, CrapsLayout l, double t, boolean reduced) {
		if (roll == null || reduced || t < beats.STICK || stick.phase == CrapsStick.DONE || stick.phase == CrapsStick.BEFORE || !stick.visible) return;
		int n = l.stick;
		// the stickman's hand: above the felt, at the top centre (across the table from the players)
		double px = l.felt.cx();
		double py = l.felt.y() - 40;
		double hx = stick.hookX;
		double hy = stick.hookY;
		Identifier shaft = TableGfx.sheet("craps/v2/stick_shaft_" + n);
		Identifier hook = TableGfx.sheet("craps/v2/stick_hook_" + n);
		int hw = 5 * n;
		int hh = 3 * n;
		double topY = Math.max(py, l.grid.play.y());
		double y0 = topY;
		double y1 = hy - hh;
		g.enableScissor(l.grid.play.x(), l.grid.play.y(), l.grid.play.right(), l.grid.play.bottom());
		if (y1 > y0) {
			for (int y = (int) Math.floor(y0); y < y1; y++) {
				double f = (y - py) / Math.max(1, hy - hh - py);
				double cx = px + (hx - px) * f;
				g.pose().pushMatrix();
				snap(g, cx - n / 2.0, y);
				TableGfx.region(g, shaft, n, 8, 0, Math.floorMod(y, 8), n, 1, 0, 0, 0xFFFFFFFF);
				g.pose().popMatrix();
			}
		}
		g.pose().pushMatrix();
		snap(g, hx - hw / 2.0, hy - hh);
		TableGfx.region(g, hook, hw, hh, 0, 0, hw, hh, 0, 0, 0xFFFFFFFF);
		g.pose().popMatrix();
		g.disableScissor();
	}

	// ---- puck ---------------------------------------------------------------------------------------------------------

	private void puck(GuiGraphicsExtractor g, CrapsLayout l, double t, int from, int to, boolean reduced) {
		Box fb = l.puckBox(from);
		Box tb = l.puckBox(to);
		double pt = t - beats.PUCK;
		int size = l.puck;
		double kk = size / 16.0;
		if (reduced) {
			// a 120 ms cross-fade between its two spots
			double a = Math.max(0, Math.min(1, pt / 120.0));
			if (a < 1) puckAt(g, fb.x(), fb.y(), 0, from != 0 ? CrapsBeats.FRAME_ON : CrapsBeats.FRAME_OFF, size, 1 - a);
			if (a > 0) puckAt(g, tb.x(), tb.y(), 0, to != 0 ? CrapsBeats.FRAME_ON : CrapsBeats.FRAME_OFF, size, a);
			return;
		}
		CrapsBeats.puck(new double[] {fb.x(), fb.y()}, from, new double[] {tb.x(), tb.y()}, to, Math.max(0, pt), kk, puck);
		puckAt(g, puck.x, puck.y, puck.lift, puck.frame, size, 1);
	}

	private void puckAt(GuiGraphicsExtractor g, double x, double y, double lift, int frame, int size, double alpha) {
		int sheet = size <= 12 ? 12 : size <= 16 ? 16 : size <= 20 ? 20 : 26;
		Identifier tex = TableGfx.sheet("craps/v2/puck_" + sheet);
		int a = (int) Math.round(255 * alpha);
		if (lift > 0.2) {
			// the shadow stays on the felt while the puck rises
			g.pose().pushMatrix();
			snap(g, x + lift * 0.3, y + lift * 0.2);
			int sa = (int) Math.round(0x50 * alpha * Math.max(0.4, 1 - lift / 12));
			g.fill(1, 2, sheet - 1, sheet - 1, sa << 24);
			g.pose().popMatrix();
		}
		g.pose().pushMatrix();
		snap(g, x, y - lift);
		TableGfx.region(g, tex, sheet, sheet * CrapsBeats.FRAMES, 0, frame * sheet, sheet, sheet, 0, 0, (a << 24) | 0xFFFFFF);
		if (frame == CrapsBeats.FRAME_ON || frame == CrapsBeats.FRAME_OFF) {
			Component word = Component.translatable(frame == CrapsBeats.FRAME_ON ? "gui.burmaldaholic.common.on" : "gui.burmaldaholic.common.off");
			int w = font.width(word);
			int c = V2Draw.alpha(frame == CrapsBeats.FRAME_ON ? 0xFF26202C : CasinoPalette.V2_TEXT, (float) alpha);
			if (w <= sheet - 4) {
				g.text(font, word, (sheet - w) / 2, (sheet - 7) / 2, c, false);
			} else {
				// T-D2 V4: the word always fits inside the disc (the face is ~60 % of the puck); the scale keeps whole
				// physical px per glyph px
				double room = sheet * 0.62;
				double sc = Math.floor(room / w * k) / k;
				if (sc <= 0) sc = 1.0 / k;
				g.pose().translate(sheet / 2f, sheet / 2f);
				g.pose().scale((float) sc, (float) sc);
				g.text(font, word, -w / 2, -4, c, false);
			}
		}
		g.pose().popMatrix();
	}

	// ---- chips ------------------------------------------------------------------------------------------------------

	private Box stackBox(String kind, int point, boolean odds, boolean mine) {
		return layout.stack(kind, point, mine ? (odds ? Layer.ODDS : Layer.MINE) : Layer.OTHERS);
	}

	private final CrapsChipBeats.Boxes boxes = new CrapsChipBeats.Boxes() {
		@Override
		public Box stack(String kind, int point, Layer layer) {
			return layout.stack(kind, point, layer);
		}

		@Override
		public Box bank() {
			Box w = layout.backWall;
			return new Box(w.cx() - layout.chip / 2, w.y(), layout.chip, layout.chip);
		}

		@Override
		public Box tray(long amount) {
			return trayBox(amount);
		}

		@Override
		public Box balance() {
			Box b = chrome.top.balance.box();
			return b.isEmpty() ? new Box(layout.felt.x(), layout.grid.topBar.y(), layout.chip, layout.chip) : b;
		}

		@Override
		public Box exit(boolean left) {
			Box f = layout.felt;
			return new Box(left ? f.x() + f.w() / 5 : f.right() - f.w() / 5, f.bottom() + layout.chip, layout.chip, layout.chip);
		}
	};

	/** The Dock chip button of the denomination nearest {@code amount} (the tray), else the Dock's left end. */
	private Box trayBox(long amount) {
		List<Box> boxes = chrome.bet.chipBoxes();
		long[] chips = chrome.bet.chips();
		Box best = null;
		long bestD = Long.MAX_VALUE;
		for (int i = 0; i < boxes.size(); i++) {
			int idx = chrome.bet.firstChip() + i;
			if (idx < 0 || idx >= chips.length) continue;
			long d = Math.abs(chips[idx] - amount);
			if (chips[idx] <= amount && d < bestD || best == null) {
				best = boxes.get(i);
				bestD = d;
			}
		}
		if (best != null) return best;
		Box dock = layout.grid.dock;
		return new Box(dock.x() + 4, dock.y() + 2, layout.chip, layout.chip);
	}

	private Box flatBoxOf(String spot) {
		String kind = CrapsLayout.kindOf(spot);
		return switch (kind) {
			case "pass_odds" -> stackBox("pass", 0, false, true);
			case "point" -> stackBox("come", CrapsLayout.pointOf(spot), false, true);
			default -> stackBox(kind, 0, false, true);
		};
	}

	private Box pendingBox(String spot, String action) {
		String kind = CrapsLayout.kindOf(spot);
		return switch (kind) {
			case "point" -> stackBox("come", CrapsLayout.pointOf(spot), true, true);
			case "pass_odds" -> stackBox("pass", 0, true, true);
			default -> stackBox(kind, 0, "odds".equals(action), true);
		};
	}

	private CrapsChipBeats.Plan plan(boolean sevenOut) {
		if (chipPlan == null) chipPlan = CrapsChipBeats.plan(lastRes(), boxes, beats, sevenOut);
		return chipPlan;
	}

	/**
	 * Bets on the felt: before the chips move, the bets as they were (the throw is in the air); from {@link CrapsBeats#CHIPS}
	 * the current bets (travelled come bets once they land) plus the take / pay / push flights and your net pills.
	 */
	private void chips(GuiGraphicsExtractor g, CrapsLayout l, CrapsView v, double t, boolean reduced) {
		List<Box> tags = new ArrayList<>();
		if (roll != null && t < beats.CHIPS) {
			drawBets(g, prevBets(), 1f, null, t, tags);
		} else if (roll != null && t < beats.IDLE) {
			boolean sevenOut = "seven_out".equals(lastEvent);
			CrapsChipBeats.Plan p = plan(sevenOut);
			drawBets(g, v.bets(), 1f, reduced ? null : p, t, tags);
			if (reduced) {
				for (CrapsChipBeats.Pill pill : p.pills()) {
					if (t < beats.CHIPS + 1200) netPill(g, pill.at(), pill.net(), 1f);
				}
			} else {
				for (CrapsChipBeats.Flight f : p.flights()) {
					if (f.kind() == CrapsChipBeats.Kind.COLLECT) continue; // drawn over everything
					drawFlight(g, f, t);
				}
				for (CrapsChipBeats.Pill pill : p.pills()) {
					if (t < pill.from() || t >= pill.to()) continue;
					float fade = (float) Math.max(0, Math.min(1, (pill.to() - t) / 200.0));
					netPill(g, pill.at(), pill.net(), fade);
				}
			}
		} else {
			drawBets(g, v.bets(), 1f, null, t, tags);
		}
		// the pending chip of a bet / odds not yet answered (after its flight landed)
		var pend = chrome.pending();
		if (pend != null && pend.spot().startsWith("felt.") && (placing == null || placeLanded)) {
			ChipStack.drawPending(g, pendingBox(pend.spot(), pend.action()).offset(0, -3), pend.amount());
		}
	}

	private void drawFlight(GuiGraphicsExtractor g, CrapsChipBeats.Flight f, double t) {
		CrapsChipBeats.sample(f, t, pos);
		if (!pos.visible || pos.alpha <= 0.02f) return;
		if (f.denom() > 0) {
			g.pose().pushMatrix();
			snap(g, pos.x, pos.y);
			ChipStack.disc(g, 0, 0, f.size(), f.denom(), pos.alpha);
			g.pose().popMatrix();
			return;
		}
		List<Long> discs = ChipStack.discs(f.amount());
		int size = f.size();
		int h = size + (discs.size() - 1);
		// the stack box's top-left is (x, y); the discs sit at its bottom (a rake spreads them along the drag)
		Box b = layout.stack("pass", 0, Layer.MINE);
		double bottom = pos.y + b.h() - size;
		for (int i = 0; i < discs.size(); i++) {
			g.pose().pushMatrix();
			snap(g, pos.x, bottom - i + pos.spread * i * 0.9 * (f.kind() == CrapsChipBeats.Kind.TAKE ? 1 : 0));
			ChipStack.disc(g, 0, 0, size, discs.get(i), pos.alpha);
			g.pose().popMatrix();
		}
	}

	/** Your collect flights to the balance (over the Dock and the TopBar). */
	private void collectFlights(GuiGraphicsExtractor g, double t) {
		if (roll == null || t < beats.COLLECT || t >= beats.IDLE + 400 || FxSettings.reduceMotion() || chipPlan == null) return;
		for (CrapsChipBeats.Flight f : chipPlan.flights()) {
			if (f.kind() == CrapsChipBeats.Kind.COLLECT) drawFlight(g, f, t);
		}
	}

	private void drawBets(GuiGraphicsExtractor g, List<BetPub> bets, float alpha, CrapsChipBeats.@Nullable Plan plan, double t, List<Box> tags) {
		Map<String, Long> others = new LinkedHashMap<>();
		for (BetPub b : bets) {
			if (b.mine()) continue;
			if (plan != null && t < plan.travelLanding(b.kind(), b.point(), false)) continue;
			String k2 = (b.point() != 0 && (b.kind().equals("come") || b.kind().equals("dont_come")) ? "come" : b.kind()) + "@" + b.point();
			others.merge(k2, b.staked(), Long::sum);
		}
		for (Map.Entry<String, Long> e : others.entrySet()) {
			String[] kp = e.getKey().split("@");
			ChipStack.draw(g, stackBox(kp[0], Integer.parseInt(kp[1]), false, false), e.getValue(), alpha * 0.85f);
		}
		for (BetPub b : bets) {
			if (!b.mine()) continue;
			if (plan != null && t < plan.travelLanding(b.kind(), b.point(), true)) continue;
			Box flat = stackBox(b.kind(), b.point(), false, true);
			ChipStack.draw(g, flat, b.flat(), alpha);
			valueTag(g, flat, b.flat(), tags);
			if (b.odds() > 0) {
				Box odds = stackBox(b.kind(), b.point(), true, true);
				ChipStack.draw(g, odds, b.odds(), alpha);
				valueTag(g, odds, b.odds(), tags);
			}
		}
	}

	/**
	 * The value pill of one of your stacks: above-right of its top disc, kept inside the felt, off other pills and off
	 * every printed number (D2 open item: the Field pill covered "11" at 427 / 640) — left of the stack, then higher.
	 */
	private void valueTag(GuiGraphicsExtractor g, Box stack, long amount, List<Box> placed) {
		Component n = Texts.number(amount);
		Box tag = layout.valueTag(stack, ChipStack.discs(amount).size(), ValuePill.width(font, n), placed);
		ValuePill.draw(g, font, tag, n, ValuePill.Style.YOU);
	}

	private void netPill(GuiGraphicsExtractor g, Box stack, long net, float alpha) {
		if (alpha <= 0.05f) return;
		Component n = V2Draw.net(net);
		int w = ValuePill.width(font, n);
		Box tag = new Box(stack.cx() - w / 2, stack.y() - 12, w, 11);
		if (tag.y() < layout.felt.y() + 1) tag = tag.offset(0, layout.felt.y() + 1 - tag.y());
		ValuePill.draw(g, font, tag, n, ValuePill.ofNet(net));
	}

	/** Your chip on its way from the tray (or the flat stack, for odds) to the felt; back to the tray when refused. */
	private void placingFlight(GuiGraphicsExtractor g) {
		long now = Util.getMillis();
		Placing p = placing;
		if (p != null) {
			double ms = FxSettings.reduceMotion() ? 1e9 : now - p.start();
			double dur = p.odds() ? 140 : 200;
			if (ms < dur) {
				double e = Ease.OUT_CUBIC.apply(ms / dur);
				double x = p.from().x() + (p.to().x() - p.from().x()) * e;
				double y = p.from().bottom() - p.to().w() + (p.to().bottom() - p.to().w() - (p.from().bottom() - p.to().w())) * e - (p.odds() ? 0 : 8 * Math.sin(Math.PI * ms / dur));
				g.pose().pushMatrix();
				snap(g, x, y);
				ChipStack.disc(g, 0, 0, layout.chip, p.denom(), 1f);
				g.pose().popMatrix();
			} else if (!placeLanded) {
				placeLanded = true;
				FxSounds.play("chip_place", 1f, (float) (0.9 + 0.1 * Math.log(Math.max(1, p.amount())) / Math.log(5)));
			}
			var pend = chrome.pending();
			if (placeLanded && pend == null) {
				// answered: refused when your chips on the felt did not grow
				CrapsView v = view;
				if (v != null && v.myTotal() <= p.myTotalAtSend()) {
					refused = p;
					refusedAt = now;
					FxSounds.play("ui_deny", 1f);
				}
				placing = null;
			}
		}
		Placing r = refused;
		if (r != null) {
			double ms = now - refusedAt;
			if (ms >= 200 || FxSettings.reduceMotion()) {
				refused = null;
				return;
			}
			double e = Ease.OUT_CUBIC.apply(ms / 200.0);
			double x = r.to().x() + (r.from().x() - r.to().x()) * e;
			double y = r.to().bottom() - layout.chip + (r.from().y() - (r.to().bottom() - layout.chip)) * e;
			g.pose().pushMatrix();
			snap(g, x, y);
			ChipStack.disc(g, 0, 0, layout.chip, r.denom(), (float) (1 - 0.5 * e));
			g.pose().popMatrix();
		}
	}

	// ---- total pill -----------------------------------------------------------------------------------------------

	private int totalColor(String ev) {
		return switch (ev) {
			case "natural", "point_made" -> CasinoPalette.V2_WIN;
			case "craps", "seven_out" -> CasinoPalette.V2_LOSE;
			default -> CasinoPalette.V2_GOLD;
		};
	}

	/** The total pill in its fixed box: pops at BADGE, shakes on a seven-out; the previous total fades at RELEASE. */
	private void totalPill(GuiGraphicsExtractor g, CrapsLayout l, CompoundTag s, double t) {
		boolean reduced = FxSettings.reduceMotion();
		int badge = reduced ? CrapsBeats.REDUCED_BADGE : beats.BADGE;
		if (roll == null) return;
		if (t < badge && !lateRoll) {
			if (prevTotal > 0 && t < 120) pill(g, l, prevTotal, prevTotalColor, 1 - t / 120.0, 0, 0, false);
			return;
		}
		int total = faces[0] + faces[1];
		String ev = s.getStringOr("event", "");
		double since = t - badge;
		double pop = reduced || lateRoll ? 1 : Math.min(1, since / 220.0);
		int dx = 0;
		if (!reduced && "seven_out".equals(ev) && since >= 0 && since < 300 && MotionFx.shake("craps_seven_out")) {
			dx = (int) Math.round(2 * Math.sin(2 * Math.PI * 3 * since / 300.0) * (1 - since / 300.0));
		}
		pill(g, l, total, totalColor(ev), 1, pop, dx, true);
		if (!reduced && "seven_out".equals(ev) && since >= 0 && since < 400 && FxSettings.flashes() && MotionFx.flash("craps_vignette")) {
			int a = (int) (0x40 * (1 - since / 400.0));
			Box p = l.grid.play;
			for (int i = 0; i < 6; i++) {
				int c = (a * (6 - i) / 6) << 24 | 0x8C1834;
				g.fill(p.x(), p.y() + i, p.right(), p.y() + i + 1, c);
				g.fill(p.x(), p.bottom() - i - 1, p.right(), p.bottom() - i, c);
				g.fill(p.x() + i, p.y(), p.x() + i + 1, p.bottom(), c);
				g.fill(p.right() - i - 1, p.y(), p.right() - i, p.bottom(), c);
			}
		}
		if (ev.equals("point_set") || ev.equals("point_made")) {
			V2Draw.text(g, font, Component.translatable(K2 + "caption.point"), l.pointCaption.x(), l.pointCaption.y(), 1, V2Felt.print(theme()), true);
		}
	}

	private void pill(GuiGraphicsExtractor g, CrapsLayout l, int total, int color, double alpha, double pop, int dx, boolean popFx) {
		Box tb = l.total.offset(dx, 0);
		float a = (float) alpha;
		V2Draw.fill(g, tb, V2Draw.alpha(CasinoPalette.V2_INK, a));
		V2Draw.outline(g, tb, V2Draw.alpha(CasinoPalette.V2_GOLD_SHADE, a));
		if (popFx && pop < 1) {
			// the pop: a white flash and a ring one pixel out, easing back in (OUT_BACK, 220 ms)
			double e = Ease.OUT_BACK.apply(pop);
			float fl = (float) Math.max(0, 0.6 * (1 - pop));
			if (FxSettings.flashes()) V2Draw.fill(g, tb.inset(1), V2Draw.alpha(0xFFFFFFFF, fl));
			int grow = e > 1.02 ? 1 : 0;
			if (grow > 0 || pop < 0.5) V2Draw.outline(g, tb.inset(-1), V2Draw.alpha(color, (float) (1 - pop)));
		}
		Component n = Texts.number(total);
		int scale = l.tier == Tier.T1 ? 1 : 2;
		int w = font.width(n) * scale;
		V2Draw.text(g, font, n, tb.cx() - w / 2 + 1, tb.cy() - 4 * scale + 1, scale, V2Draw.alpha(color, a), true);
	}

	// ---- effects and sounds ---------------------------------------------------------------------------------------

	/** Dice events from the cursor: dust at felt landings, sparks and the diamond flash at the wall (CS §4.3). */
	private void onDiceEvent(MotionEvent e, boolean silent) {
		int kind = switch (e.kind()) {
			case DiceEvent.FLOOR, DiceEvent.TIP -> e.kind() == DiceEvent.FLOOR ? 0 : -1;
			case DiceEvent.WALL -> 2;
			case DiceEvent.CLACK -> 1;
			default -> -1;
		};
		if (kind < 0) return;
		int i = fxNext;
		fxNext = (fxNext + 1) % MAX_FX;
		fxKind[i] = kind;
		fxT[i] = e.tMs();
		fxX[i] = e.x();
		fxY[i] = e.y();
		if (kind == 2) {
			// sparks kick off the wall along its normal
			int j = fxNext;
			fxNext = (fxNext + 1) % MAX_FX;
			fxKind[j] = 3;
			fxT[j] = e.tMs();
			fxX[j] = e.x();
			fxY[j] = e.y();
		}
	}

	private void effects(GuiGraphicsExtractor g, CrapsLayout l, double t) {
		if (roll == null || FxSettings.reduceMotion()) return;
		StageMap map = map();
		int dust = TableGfx.lerp(V2Felt.felt(theme()), 0xFFFFFFFF, 0.3);
		for (int i = 0; i < MAX_FX; i++) {
			double since = t - fxT[i];
			if (since < 0) continue;
			double x = map.x(fxX[i]);
			double y = map.y(fxY[i]);
			switch (fxKind[i]) {
				case 0 -> {
					if (since >= DUST_MS || !MotionFx.particles("craps_dust", 3)) continue;
					double p = since / DUST_MS;
					int a = (int) (200 * (1 - p));
					for (int q = 0; q < 3; q++) {
						double ang = Math.PI * (0.15 + 0.35 * q) + (i % 3) * 0.4;
						double r = 2 + 5 * Ease.OUT_CUBIC.apply(p);
						pixel(g, x + Math.cos(ang) * r * (q == 1 ? -1 : 1), y + l.die * 0.3 - Math.sin(ang) * r * 0.5, (a << 24) | (dust & 0xFFFFFF));
					}
				}
				case 1 -> {
					if (since >= SPARK_MS || !MotionFx.particles("craps_clack", 2)) continue;
					double p = since / SPARK_MS;
					int a = (int) (255 * (1 - p));
					pixel(g, x - 1 - 3 * p, y - l.die * 0.4 - 4 * p, (a << 24) | 0xF4ECF8);
					pixel(g, x + 1 + 3 * p, y - l.die * 0.4 - 4 * p, (a << 24) | 0xF4ECF8);
				}
				case 3 -> {
					if (since >= SPARK_MS || !FxSettings.flashes() || !MotionFx.particles("craps_wall_spark", 4)) continue;
					double p = since / SPARK_MS;
					int a = (int) (255 * (1 - p));
					double wy = l.backWall.bottom();
					for (int q = 0; q < 4; q++) {
						double ang = Math.PI * (0.2 + 0.2 * q);
						double r = 3 + 9 * Ease.OUT_CUBIC.apply(p);
						int col = q % 2 == 0 ? 0xFFF4C0 : 0xFFD640;
						pixel(g, x + Math.cos(ang) * r, wy + Math.sin(ang) * r * 0.6, (a << 24) | col);
					}
				}
				default -> {}
			}
		}
	}

	private boolean cue(int bit, double at, double t) {
		if (t < at || (cues & (1L << bit)) != 0) return false;
		cues |= 1L << bit;
		return t - at < 150; // a hitch / late join past the cue: silent
	}

	/** One-shot sounds of the storyboard (own screen; the dice contacts go through {@link DiceAudio}). */
	private void cues(double t) {
		if (roll == null || lateRoll) return;
		boolean reduced = FxSettings.reduceMotion();
		if (reduced && cue(CUE_REDUCED_FELT, 150, t)) FxSounds.play("dice_bounce", 0.8f, 1.1f);
		int pf = lastPuckFrom;
		int pt = lastPuckTo;
		if (pf != pt || (pf == 0) != (pt == 0)) {
			if (!reduced && cue(CUE_PUCK_LIFT, beats.PUCK, t)) FxSounds.play("craps_puck_lift", 1f);
			if (cue(CUE_PUCK, beats.PUCK + (reduced ? 0 : CrapsBeats.puckTouchdown()), t)) FxSounds.play("craps_puck", 1f);
		}
		if (cue(CUE_SWEEP, beats.CHIPS, t)) {
			for (CrapsChipBeats.Res r : lastRes()) {
				if ("lose".equals(r.outcome())) {
					FxSounds.play("chip_sweep", 1f);
					break;
				}
			}
		}
		if (chipPlan != null && !reduced) {
			int i = 0;
			for (CrapsChipBeats.Flight f : chipPlan.flights()) {
				if (f.kind() == CrapsChipBeats.Kind.PAY && f.mine()) {
					// each payout disc lands with a count tick, pitch rising
					int bit = 16 + Math.min(i, 40);
					if (cue(bit, f.end(), t)) FxSounds.play("chip_count", 0.7f, (float) (1.0 + 0.08 * i));
					i++;
				}
			}
			for (CrapsChipBeats.Flight f : chipPlan.flights()) {
				if (f.kind() == CrapsChipBeats.Kind.TRAVEL && cue(CUE_TRAVEL, f.end(), t)) FxSounds.play("chip_stack", 0.8f);
			}
		}
		if (!reduced && cue(CUE_STICK, beats.stickHook(), t)) FxSounds.play("craps_stick", 1f);
	}

	/** The result of the roll for the viewer: banner at BANNER, balance / win sound / celebration at COLLECT (once). */
	private void settle(double t) {
		if (roll == null) return;
		CompoundTag s = state();
		long staked = s.getLongOr("res_staked", 0);
		long ret = s.getLongOr("res_return", 0);
		if (staked <= 0) return;
		long net = ret - staked;
		if (!lateRoll && cue(CUE_BANNER, beats.CHIPS, t)) chrome.banner.show(net);
		if (settledFor == shownRolls || t < beats.COLLECT) return;
		settledFor = shownRolls;
		chrome.top.balance.delta(net);
		WinTier tier = WinTier.valueOf(s.getStringOr("tier", "LOSS"));
		if (tier.ordinal() >= WinTier.NICE.ordinal() && FxSettings.celebrations() != FxSettings.Celebrations.OFF) {
			CelebrationOverlay.get().play(CelebrationRequest.core(tier, ret, staked, s.getIntOr("seed", 0)));
		} else if (ret > staked) {
			FxSounds.play("win_small", 1f);
		} else if (ret > 0) {
			FxSounds.play("push", 1f);
		}
	}

	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, CrapsView v, int mx, int my, float a) {
		if (!showRules) return;
		Box b = grid.play.inset(grid.m() + 10);
		V2Draw.fill(g, grid.play, 0x90000000);
		V2Draw.pill(g, b, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD_SHADE);
		int y = b.y() + 8;
		for (int i = 1; i <= 3; i++) {
			for (FormattedCharSequence seq : font.split(Component.translatable(K + "rules." + i), b.w() - 16)) {
				g.text(font, seq, b.x() + 8, y, CasinoPalette.V2_TEXT, true);
				y += 10;
			}
			y += 4;
		}
		g.text(font, dev.nezo.burmaldaholic.client.ui.CasinoUi.fit(font, limitsLine(), b.w() - 16), b.x() + 8, y, CasinoPalette.V2_GOLD, true);
	}

	// ---- tests ------------------------------------------------------------------------------------------------------

	/** The throw is still being shown: the felt and Undo are closed (tests). */
	public boolean throwShown() {
		return rolling();
	}

	/** The current layout (tests). */
	public CrapsLayout layout() {
		return layout;
	}

	/** The cached throw of the shown roll (tests; null before the first roll). */
	public @Nullable DiceRoll shownRoll() {
		return roll;
	}

	/** The storyboard of the shown roll (tests). */
	public CrapsBeats beats() {
		return beats;
	}

	/** Shooter side of the shown roll (tests). */
	public int shownSide() {
		return side;
	}

	/** Roll counter of the shown state (tests). */
	public int shownRolls() {
		return shownRolls;
	}

	/** Storyboard time now (tests). */
	public double storyMs() {
		return t();
	}

	/** Atlas frame of die {@code d} at story time {@code t} before the stick (tests: the pip probe at rest). */
	public int dieFrame(int d, double t) {
		DiceRoll r = roll;
		if (r == null) return -1;
		r.sample(d, t, tmp);
		return dev.nezo.burmaldaholic.core.anim.dice.DieAtlasTable.frame(tmp.q);
	}

	/** GUI x of the back wall diamond die {@code d} hits first (tests: the flash probe), NaN when none. */
	public double wallHitX(int d) {
		DiceRoll r = roll;
		if (r == null) return Double.NaN;
		for (MotionEvent e : r.events()) if (e.kind() == DiceEvent.WALL && e.obj() == d) return map().x(e.x());
		return Double.NaN;
	}

	/** Die centre {x, y} (layout px, lifted) of die {@code d} at story time {@code t} as drawn (tests: pixel probes). */
	public double[] dieCentre(int d, double t) {
		DiceRoll r = roll;
		double[] out = new double[2];
		if (r == null) return out;
		if (t >= beats.STICK) {
			homeXY(side);
			for (int i = 0; i < 2; i++) {
				r.sample(i, 1e9, tmp);
				restXY[2 * i] = map().x(tmp.x);
				restXY[2 * i + 1] = map().y(tmp.y);
			}
			CrapsStick.Out o = new CrapsStick.Out();
			CrapsStick.sample(t, beats, restXY, homeXY, layout.felt.y() - 2, layout.die, o);
			out[0] = o.x[d];
			out[1] = o.y[d] - map().lift(1);
			return out;
		}
		r.sample(d, t, tmp);
		DiceDraw.centre(tmp, map(), layout.die, out);
		return out;
	}
}
