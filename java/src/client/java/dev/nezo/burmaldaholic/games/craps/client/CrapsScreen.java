package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Felt;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.dice.DiceThrowPath;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.present.CrapsFlow;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout.Layer;
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
 * Craps, table UI v2 (REDESIGN.md §7.6, mockup {@code v2_craps_point_480.png}; docs/architecture/ui-v2.md, lane D). The
 * felt fills the PlayArea ({@link CrapsLayout}): the place row with the puck, the COME row with the dice and the total,
 * the FIELD, the DON'T PASS BAR and the PASS LINE with its odds lane. The Dock is the kit's: the chip tray (multi-spot:
 * a chip arms, a felt click places it — {@link CrapsFlow#click}: a flat bet, odds behind it, or the reason), Undo /
 * Clear, the {@code On table} readout, the status ({@code Point is 8} / {@code Roll in 18 s}) and the primary
 * ({@code Sit down} → {@code Roll}).
 *
 * <p>The roll (animation/tables.md §2.4): the dice fly from the shooter's side over the felt and hit the back of the
 * COME row (the shared {@link DiceThrowPath}, blank tumble frames until 82 %), rest on the real faces, the total pill
 * pops, the puck flips and flies, every bet shows its net (server returns from {@code last_res}), and the round's net
 * shows in the banner and the balance delta. Late joiners see the settled table; Space / a click skips the chip beats.
 */
public class CrapsScreen extends V2TableScreen<CrapsView> {
	private static final String K = "gui.burmaldaholic.craps.";
	private static final String K2 = CrapsFlow.K;

	/** What the last roll did to a bet (server {@code last_res}). */
	private record Res(String kind, int point, boolean mine, String outcome, long flat, long odds, long ret, int movedTo) {}

	private final TableClock clock = new TableClock();
	private CrapsLayout layout = CrapsLayout.of(TableGrid.of(480, 270));
	private float partial;
	private boolean showRules;
	private @Nullable String hoverSpot;
	/** spots a click would place on now (lit while a chip is armed) */
	private final Set<String> live = new HashSet<>();
	// the roll
	private int shownRolls = -1;
	private @Nullable DiceThrowPath throwPath;
	private boolean lateRoll;
	private boolean skipped;
	private long rollPressedAt = -1;
	private int cueIndex;
	private int puckCue = -1;
	private int sweepCue = -1;
	private int settledFor = -1;
	private boolean rollingShown;
	private long lastSecond = -1;
	private final DiceThrowPath.Sample sample = new DiceThrowPath.Sample();
	private final CrapsBeats.Puck puck = new CrapsBeats.Puck();
	/** The dice ease from the throw's rest point into their slots over 4 ticks. */
	private static final double SNAP_MS = 200;

	public CrapsScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(K + "title"));
		chrome.bet.multiSpot(true);
		chrome.bet.tools(BetGates.UNDO, BetGates.CLEAR);
	}

	// ---- the game's pure parts ----------------------------------------------------------------------------------------

	@Override
	protected CrapsView view(CompoundTag s) {
		return CrapsView.read(s, secondsLeft(s, "window"), secondsLeft(s, "roll"), rolling());
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
			shownRolls = rolls;
			rollPressedAt = -1;
			throwPath = null;
			int d1 = s.getIntOr("d1", 0);
			int d2 = s.getIntOr("d2", 0);
			if (d1 > 0 && d2 > 0) {
				newThrow(s, d1, d2);
				lateRoll = first || rollMs(s) >= 0.85 * CrapsBeats.IDLE;
			}
			skipped = false;
			cueIndex = 0;
			puckCue = sweepCue = -1;
			if (lateRoll) settledFor = rolls;
		}
		super.onStateChanged(s);
		rollingShown = rolling();
		statusLines();
	}

	private void newThrow(CompoundTag s, int d1, int d2) {
		CrapsLayout l = layout;
		Box r = l.restRegion;
		double sx = s.getIntOr("shooter_dir", 0) == 1 ? l.felt.x() + l.die1.w() : l.throwX;
		throwPath = DiceThrowPath.of(s.getIntOr("seed", 0), d1, d2, DiceThrowPath.Params.craps(sx, l.throwY, l.come.y() + 2, l.come.x(), l.felt.right(),
			r.x(), r.y(), r.right(), r.bottom()));
	}

	@Override
	protected void layoutPlay(TableGrid grid, CrapsView v, HitMap hits) {
		CrapsLayout l = CrapsLayout.of(grid);
		boolean moved = !l.felt.equals(layout.felt);
		layout = l;
		if (moved && throwPath != null) {
			CompoundTag s = state();
			newThrow(s, s.getIntOr("d1", 1), s.getIntOr("d2", 1));
		}
		live.clear();
		long chip = Math.max(1, chrome.bet.selected());
		for (Map.Entry<String, Box> e : l.spots().entrySet()) {
			boolean sends = CrapsFlow.click(v, e.getKey(), chip).sends();
			if (sends) live.add(e.getKey());
			hits.add(e.getKey(), e.getValue(), true);
		}
		List<Box> obstacles = new ArrayList<>(l.glyphs());
		obstacles.add(l.die1);
		obstacles.add(l.die2);
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
		FxSounds.play("chip_place", 1f, (float) (0.9 + 0.1 * Math.log(Math.max(1, c.amount())) / Math.log(5)));
		return true;
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
			FxSounds.play("dice_cup", 1f);
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
		if (throwPath == null || lateRoll) return CrapsBeats.IDLE + 1;
		double t = rollMs(state());
		if (skipped && t >= CrapsBeats.BADGE) return CrapsBeats.IDLE + 1;
		return t;
	}

	/** The throw is still being shown (no Roll, no Undo meanwhile). */
	private boolean rolling() {
		return throwPath != null && !lateRoll && t() < CrapsBeats.CHIPS;
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

	private List<Res> lastRes() {
		List<Res> out = new ArrayList<>();
		ListTag list = state().getListOrEmpty("last_res");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			out.add(new Res(t.getStringOr("kind", ""), t.getIntOr("point", 0), t.getBooleanOr("mine", false), t.getStringOr("outcome", ""),
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
		long sec = Util.getMillis() / 1000;
		if (r != rollingShown || sec != lastSecond) {
			rollingShown = r;
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
		skipBeats();
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

	/** Space / a click while the chips resolve: jump to the settled table. */
	private boolean skipBeats() {
		if (throwPath == null || skipped || lateRoll) return false;
		double t = rollMs(state());
		if (t >= CrapsBeats.BADGE && t < CrapsBeats.IDLE) {
			skipped = true;
			return true;
		}
		return false;
	}

	// ---- drawing ------------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, CrapsView v, int mx, int my, float a) {
		partial = a;
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
		int pointBefore = t >= CrapsBeats.PUCK + CrapsBeats.PUCK_TOTAL ? point : s.getIntOr("point_before", point);
		boolean armed = chrome.bet.armed();
		feltLines(g, l, print, pointBefore, armed);
		labels(g, l, print);
		chips(g, l, v, t);
		puck(g, l, t, s.getIntOr("point_before", point), point);
		dice(g, l, s, t);
		cues(t);
		settle(t);
	}

	private void feltLines(GuiGraphicsExtractor g, CrapsLayout l, int print, int point, boolean armed) {
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
			V2Draw.outline(g, b, CasinoPalette.V2_GOLD);
			V2Draw.outline(g, b.inset(1), CasinoPalette.V2_GOLD);
		}
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
		big(g, Component.translatable(K + "layout.come"), l.comeWord, print);
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
		Component fit = c;
		int max = row.w() / 3;
		FormattedCharSequence seq = font.width(c) <= max ? c.getVisualOrderText()
			: dev.nezo.burmaldaholic.client.ui.CasinoUi.fit(font, fit, max);
		g.text(font, seq, cx - font.width(seq) / 2, row.cy() - 3, color, true);
	}

	// ---- chips ------------------------------------------------------------------------------------------------------

	private Box stackBox(String kind, int point, boolean odds, boolean mine) {
		return layout.stack(kind, point, mine ? (odds ? Layer.ODDS : Layer.MINE) : Layer.OTHERS);
	}

	/** Bets: before the roll while the dice fly, the nets of the resolution beat, then the current bets. */
	private void chips(GuiGraphicsExtractor g, CrapsLayout l, CrapsView v, double t) {
		if (t < CrapsBeats.CHIPS) {
			drawBets(g, prevBets(), 1f);
		} else if (t < CrapsBeats.IDLE) {
			drawBets(g, v.bets(), 1f);
			double k = t - CrapsBeats.CHIPS;
			for (Res r : lastRes()) {
				if (!r.mine()) continue;
				switch (r.outcome()) {
					case "win", "lose", "push", "odds_off" -> {
						long net = r.ret() - r.flat() - r.odds();
						Box at = stackBox(r.kind(), r.point(), false, true);
						float fade = (float) Math.max(0, 1 - Math.max(0, k - 600) / 500.0);
						if (net < 0) ChipStack.draw(g, at.offset(0, -(int) Math.round(Ease.IN_CUBIC.apply(Math.min(1, k / 500.0)) * 6)), r.flat() + r.odds(),
							fade * 0.8f);
						netPill(g, at, net, fade);
					}
					default -> {}
				}
			}
		} else {
			drawBets(g, v.bets(), 1f);
		}
		// the pending chip of a bet / odds not yet answered
		var p = chrome.pending();
		if (p != null && p.spot().startsWith("felt.")) {
			String kind = CrapsLayout.kindOf(p.spot());
			Box at = switch (kind) {
				case "point" -> stackBox("come", CrapsLayout.pointOf(p.spot()), true, true);
				case "pass_odds" -> stackBox("pass", 0, true, true);
				default -> stackBox(kind, 0, "odds".equals(p.action()), true);
			};
			ChipStack.drawPending(g, at.offset(0, -3), p.amount());
		}
	}

	private void drawBets(GuiGraphicsExtractor g, List<BetPub> bets, float alpha) {
		Map<String, Long> others = new LinkedHashMap<>();
		for (BetPub b : bets) {
			if (b.mine()) continue;
			String k = (b.point() != 0 && (b.kind().equals("come") || b.kind().equals("dont_come")) ? "come" : b.kind()) + "@" + b.point();
			others.merge(k, b.staked(), Long::sum);
		}
		for (Map.Entry<String, Long> e : others.entrySet()) {
			String[] kp = e.getKey().split("@");
			ChipStack.draw(g, stackBox(kp[0], Integer.parseInt(kp[1]), false, false), e.getValue(), alpha * 0.85f);
		}
		List<Box> tags = new ArrayList<>();
		for (BetPub b : bets) {
			if (!b.mine()) continue;
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

	/** The value pill of one of your stacks: above-right of its top disc, kept inside the felt and off other tags. */
	private void valueTag(GuiGraphicsExtractor g, Box stack, long amount, List<Box> placed) {
		Component n = Texts.number(amount);
		int w = ValuePill.width(font, n);
		int top = stack.bottom() - stack.w() - Math.min(3, ChipStack.discs(amount).size() - 1) - 1;
		Box tag = new Box(stack.right() - 2, top - 9, w, 11);
		Box felt = layout.felt;
		if (tag.right() > felt.right() - 1) tag = tag.offset(felt.right() - 1 - tag.right(), 0);
		if (tag.y() < felt.y() + 1) tag = tag.offset(0, felt.y() + 1 - tag.y());
		for (Box o : placed) {
			if (o.intersects(tag)) tag = tag.offset(0, o.y() - tag.bottom() - 1);
		}
		placed.add(tag);
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

	// ---- puck and dice ----------------------------------------------------------------------------------------------

	private void puck(GuiGraphicsExtractor g, CrapsLayout l, double t, int from, int to) {
		double pt = FxSettings.reduceMotion() ? (t >= CrapsBeats.PUCK ? CrapsBeats.PUCK_TOTAL : 0) : t - CrapsBeats.PUCK;
		Box fb = l.puckBox(from);
		Box tb = l.puckBox(to);
		CrapsBeats.puck(new int[] {fb.x(), fb.y()}, from, new int[] {tb.x(), tb.y()}, to, Math.max(0, pt), puck);
		int size = to != 0 ? tb.w() : fb.w();
		int sheet = size <= 12 ? 12 : size <= 16 ? 16 : 20;
		int x = (int) Math.round(puck.x);
		int y = (int) Math.round(puck.y) - (int) Math.round(puck.lift);
		Identifier tex = dev.nezo.burmaldaholic.Burmaldaholic.id("textures/gui/tables/craps/v2/puck_" + sheet + ".png");
		g.blit(tex, x, y, x + sheet, y + sheet, 0f, 1f, puck.frame / 5f, (puck.frame + 1) / 5f);
		if (puck.frame == CrapsBeats.FRAME_ON || puck.frame == CrapsBeats.FRAME_OFF) {
			Component word = Component.translatable(puck.frame == CrapsBeats.FRAME_ON ? "gui.burmaldaholic.common.on" : "gui.burmaldaholic.common.off");
			int w = font.width(word);
			int c = puck.frame == CrapsBeats.FRAME_ON ? 0xFF26202C : CasinoPalette.V2_TEXT;
			if (w <= sheet - 2) {
				g.text(font, word, x + (sheet - w) / 2, y + (sheet - 7) / 2, c, false);
			} else {
				// RU "ВКЛ" / small pucks: half size (still ≥ 2 real px per glyph px at GUI 4)
				g.pose().pushMatrix();
				g.pose().translate(x + sheet / 2f, y + sheet / 2f);
				g.pose().scale(0.5f, 0.5f);
				g.text(font, word, -w / 2, -4, c, false);
				g.pose().popMatrix();
			}
		}
	}

	private Identifier diceTex() {
		int s = layout.die1.w();
		return dev.nezo.burmaldaholic.Burmaldaholic.id("textures/gui/tables/craps/v2/dice_" + s + ".png");
	}

	private void die(GuiGraphicsExtractor g, int x, int y, int col, int row, float alpha) {
		int s = layout.die1.w();
		g.blit(diceTex(), x, y, x + s, y + s, col / 8f, (col + 1) / 8f, row / 2f, (row + 1) / 2f);
	}

	/** The dice: the shooter's rattle, the throw, the rest with the total pill and the {@code point} caption. */
	private void dice(GuiGraphicsExtractor g, CrapsLayout l, CompoundTag s, double t) {
		int size = l.die1.w();
		long now = Util.getMillis();
		if (rollPressedAt >= 0 && now - rollPressedAt < 700 && (throwPath == null || rollMs(s) < 0 || shownRollsStale())) {
			int j = (int) ((now - rollPressedAt) / 60) % 2 == 0 ? 1 : -1;
			for (int d = 0; d < 2; d++) {
				int fr = (int) ((now / 60 + d * 3) % 8);
				die(g, l.throwX - size - 4 + d * (size + 2) + j * (d == 0 ? 1 : -1) - size, l.throwY - size, fr, 1, 1f);
			}
		}
		int d1 = s.getIntOr("d1", 0);
		int d2 = s.getIntOr("d2", 0);
		DiceThrowPath p = throwPath;
		if (p == null || d1 <= 0) {
			return; // no roll yet: the COME row stays clean
		}
		boolean settled = t >= p.durationMs() || FxSettings.reduceMotion();
		if (!settled) {
			for (int d = 0; d < 2; d++) {
				p.sample(d, t, sample);
				int x = (int) Math.round(sample.x) - size / 2;
				int y = (int) Math.round(sample.y - sample.z) - size / 2;
				g.fill(x + 1, (int) Math.round(sample.y) + size / 2 - 1, x + size - 1, (int) Math.round(sample.y) + size / 2 + 1, 0x50000000);
				if (sample.frame >= 0) die(g, x, y, sample.frame, 1, 1f);
				else die(g, x, y, sample.face - 1, 0, 1f);
			}
			return;
		}
		// at rest: the dice in their slots (the throw ends in the rest region; the slots keep the total readable). The
		// move from where the throw stopped into the slots is eased over 4 ticks instead of a jump (UI-D risk).
		double k = FxSettings.reduceMotion() || lateRoll ? 1 : Math.min(1, Math.max(0, (t - p.durationMs()) / SNAP_MS));
		Box[] slots = {l.die1, l.die2};
		int[] faces = {d1, d2};
		for (int d = 0; d < 2; d++) {
			int x = slots[d].x();
			int y = slots[d].y();
			if (k < 1) {
				p.sample(d, p.durationMs(), sample);
				double rx = sample.x - size / 2.0;
				double ry = sample.y - sample.z - size / 2.0;
				double e = Ease.OUT_CUBIC.apply(k);
				x = (int) Math.round(rx + (x - rx) * e);
				y = (int) Math.round(ry + (y - ry) * e);
			}
			die(g, x, y, faces[d] - 1, 0, 1f);
		}
		if (t >= CrapsBeats.BADGE || lateRoll) {
			Component n = Texts.number(d1 + d2);
			int color = switch (s.getStringOr("event", "")) {
				case "natural", "point_made" -> CasinoPalette.V2_WIN;
				case "craps", "seven_out" -> CasinoPalette.V2_LOSE;
				default -> CasinoPalette.V2_GOLD;
			};
			Box tb = l.total;
			V2Draw.fill(g, tb, CasinoPalette.V2_INK);
			V2Draw.outline(g, tb, V2Draw.alpha(CasinoPalette.V2_GOLD_SHADE, 1f));
			int scale = l.tier == Tier.T1 ? 1 : 2;
			int w = font.width(n) * scale;
			V2Draw.text(g, font, n, tb.cx() - w / 2 + 1, tb.cy() - 4 * scale + 1, scale, color, true);
			String ev = s.getStringOr("event", "");
			if (ev.equals("point_set") || ev.equals("point_made")) {
				V2Draw.text(g, font, Component.translatable(K2 + "caption.point"), l.pointCaption.x(), l.pointCaption.y(), 1, V2Felt.print(theme()), true);
			}
		}
	}

	private boolean shownRollsStale() {
		return state().getIntOr("rolls", 0) == shownRolls && rollMs(state()) > CrapsBeats.IDLE;
	}

	/** Sound cues of the roll (own screen). */
	private void cues(double t) {
		DiceThrowPath p = throwPath;
		if (p == null || lateRoll || t >= CrapsBeats.IDLE) return;
		var list = p.cues();
		if (cueIndex == 0 && t > 300) {
			while (cueIndex < list.size() && list.get(cueIndex).atMs() < t - 100) cueIndex++;
		}
		while (cueIndex < list.size() && list.get(cueIndex).atMs() <= t) {
			var c = list.get(cueIndex++);
			FxSounds.play(c.sound(), 1f, c.pitch());
		}
		int landAt = CrapsBeats.PUCK + CrapsBeats.PUCK_TOTAL - CrapsBeats.PUCK_LAND;
		int pointNow = state().getIntOr("point", 0);
		if (puckCue != shownRolls && t >= landAt && pointNow != state().getIntOr("point_before", pointNow)) {
			puckCue = shownRolls;
			FxSounds.play("craps_puck", 1f);
		}
		if (sweepCue != shownRolls && t >= CrapsBeats.CHIPS) {
			sweepCue = shownRolls;
			for (Res r : lastRes()) {
				if ("lose".equals(r.outcome())) {
					FxSounds.play("chip_sweep", 1f);
					break;
				}
			}
		}
	}

	/** The result of the roll for the viewer: banner, balance delta, celebration (once per roll). */
	private void settle(double t) {
		if (settledFor == shownRolls || t < CrapsBeats.CHIPS || throwPath == null) return;
		settledFor = shownRolls;
		CompoundTag s = state();
		long staked = s.getLongOr("res_staked", 0);
		long ret = s.getLongOr("res_return", 0);
		if (staked <= 0) return;
		long net = ret - staked;
		chrome.banner.show(net);
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

	/** The current layout (tests). */
	public CrapsLayout layout() {
		return layout;
	}
}
