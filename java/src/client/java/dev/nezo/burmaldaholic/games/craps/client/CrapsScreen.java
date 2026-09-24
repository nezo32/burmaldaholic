package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.table.fx.ChipButton;
import dev.nezo.burmaldaholic.client.table.fx.TableButton;
import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.dice.ChipStacks;
import dev.nezo.burmaldaholic.core.anim.dice.DiceThrowPath;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsFelt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Craps (UI.md §8; docs/design/visual/tables.md §4, §6; animation/tables.md §2.4). The full felt with its back wall,
 * chips on the layout (odds behind the line), the puck and the point glow. A roll: the dice fly from the shooter's
 * side, hit the pyramid wall, bounce twice and rest on the REAL faces (shared {@link DiceThrowPath}; blank tumble
 * frames before 82 %), the total badge pops in the event colour, the puck flips and flies, losing chips are swept,
 * come bets travel to their box, winners get payout discs and fly to the balance, and the stick drags the dice back.
 * Every chip motion uses the server's {@code last_res} (per-bet returns); the chat lines wait for the dice
 * (REVEAL_DELAY, server). Late joiners past 85 % see the settled table; Space / click skips the local chip beats.
 */
public class CrapsScreen extends CasinoTableScreen {
	private static final String K = "gui.burmaldaholic.craps.";
	private static final int[] CHIPS = ChipStacks.DENOMS;

	/** One bet as sent by the server. */
	private record BetView(int id, String kind, long flat, long odds, int point, boolean mine, int unit, long oddsMax, long room, boolean off) {}

	/** What the last roll did to a bet. */
	private record Res(String kind, int point, boolean mine, String outcome, long flat, long odds, long ret, int movedTo) {}

	private TableChrome.Frame frame = TableChrome.FULL;
	private int ox;
	private int oy;
	private TableTheme theme = TableTheme.VILLAGE;
	private final TableClock clock = new TableClock();
	private int selectedChip = 5;
	private boolean showRules;
	private float partial;
	private CrapsFelt.@Nullable Area hoverArea;
	// the roll
	private int shownRolls = -1;
	private @Nullable DiceThrowPath throwPath;
	private int cueIndex;
	private boolean lateRoll;
	private boolean skipped;
	private long rollPressedAt = -1;
	private int puckCue = -1;
	private int sweepCue = -1;
	private int celebrated = -1;
	private @Nullable Component error;
	private int errorTicks;
	private final DiceThrowPath.Sample sample = new DiceThrowPath.Sample();
	private final CrapsBeats.Puck puck = new CrapsBeats.Puck();

	public CrapsScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(K + "title"), 427, 240);
		this.titleLabelY = -10_000;
	}

	private boolean compact() {
		return frame.compact();
	}

	private CrapsFelt felt() {
		return compact() ? CrapsFelt.COMPACT : CrapsFelt.BIG;
	}

	private int lx() {
		return ox + (compact() ? 10 : 20);
	}

	private int ly() {
		return oy + (compact() ? 17 : 33);
	}

	@Override
	protected void init() {
		frame = TableChrome.frameFor(width, height);
		super.init();
		ox = (width - frame.w()) / 2;
		oy = (height - frame.h()) / 2;
		leftPos = ox;
		topPos = oy;
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag s) {
		clock.sync(s.getLongOr("game_time", 0));
		theme = TableKit.theme(theme());
		int rolls = s.getIntOr("rolls", 0);
		if (rolls != shownRolls) {
			boolean first = shownRolls < 0;
			shownRolls = rolls;
			rollPressedAt = -1;
			int d1 = s.getIntOr("d1", 0);
			int d2 = s.getIntOr("d2", 0);
			if (d1 > 0 && d2 > 0) {
				CrapsFelt f = felt();
				double[] r = f.restRegion();
				double[] st = f.throwStart();
				int dir = s.getIntOr("shooter_dir", 0);
				double sx = dir == 1 ? 24 : st[0];
				throwPath = DiceThrowPath.of(s.getIntOr("seed", 0), d1, d2,
					DiceThrowPath.Params.craps(sx, st[1], f.wall(), f.dcW() + 2, f.w(), r[0], r[1], r[2], r[3]));
				double age = rollMs();
				lateRoll = first || age >= 0.85 * CrapsBeats.IDLE;
				skipped = false;
				cueIndex = 0;
				puckCue = sweepCue = -1;
			} else {
				throwPath = null;
			}
		}
		if (minecraft != null) {
			rebuild();
		}
	}

	@Override
	public void showError(Component message) {
		super.showError(message);
		error = message;
		errorTicks = 60;
		FxSounds.play("ui_deny", 1f);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	// ---- state ------------------------------------------------------------------------------------------------------

	private double rollMs() {
		return clock.msSince(state().getLongOr("roll_time", 0), partial);
	}

	/** ms into the roll storyboard (IDLE and beyond = settled). */
	private double t() {
		if (throwPath == null || lateRoll) {
			return CrapsBeats.IDLE + 1;
		}
		double t = rollMs();
		if (skipped && t >= CrapsBeats.BADGE) {
			return CrapsBeats.IDLE + 1;
		}
		return t;
	}

	private List<BetView> bets() {
		List<BetView> out = new ArrayList<>();
		ListTag list = state().getListOrEmpty("bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			out.add(new BetView(t.getIntOr("id", -1), t.getStringOr("kind", ""), t.getLongOr("flat", 0), t.getLongOr("odds", 0), t.getIntOr("point", 0),
				t.getBooleanOr("mine", false), t.getIntOr("unit", 1), t.getLongOr("odds_max", 0), t.getLongOr("room", 0), t.getBooleanOr("off", false)));
		}
		return out;
	}

	private List<BetView> prevBets() {
		List<BetView> out = new ArrayList<>();
		ListTag list = state().getListOrEmpty("prev_bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			out.add(new BetView(-1, t.getStringOr("kind", ""), t.getLongOr("flat", 0), t.getLongOr("odds", 0), t.getIntOr("point", 0),
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

	private boolean allowed(String kind) {
		return state().getCompoundOrEmpty("allowed").getBooleanOr(kind, false);
	}

	private List<BetView> betsOn(CrapsFelt.Area a) {
		List<BetView> out = new ArrayList<>();
		for (BetView b : bets()) {
			boolean on = a.kind().equals("point") ? (b.point() == a.point() && (b.kind().equals("come") || b.kind().equals("dont_come")))
				: b.kind().equals(a.kind()) && b.point() == 0;
			if (on) {
				out.add(b);
			}
		}
		return out;
	}

	private @Nullable BetView oddsTarget(CrapsFelt.Area a) {
		BetView best = null;
		for (BetView b : betsOn(a)) {
			if (b.mine() && b.room() > 0 && !b.kind().equals("field")) {
				if (best == null || b.kind().equals("come") || b.kind().equals("pass")) {
					best = b;
				}
			}
		}
		return best;
	}

	private long oddsAmount(BetView b) {
		long raw = Math.min(selectedChip, b.room());
		return raw / Math.max(1, b.unit()) * Math.max(1, b.unit());
	}

	private long secondsLeft(String timer) {
		long ticks = state().getCompoundOrEmpty("timers").getLongOr(timer, -1);
		if (ticks < 0) {
			return -1;
		}
		double elapsed = clock.msSince(state().getLongOr("game_time", 0), partial) / 50.0;
		return (long) Math.max(0, Math.ceil((ticks - elapsed) / 20.0));
	}

	private static Component decode(@Nullable Tag tag) {
		if (tag == null) {
			return Component.empty();
		}
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	// ---- widgets ------------------------------------------------------------------------------------------------------

	private void rebuild() {
		clearWidgets();
		long max = maxBet();
		if (selectedChip < minBet() || (max > 0 && selectedChip > max)) {
			for (int c : CHIPS) {
				if (c >= minBet() && (max <= 0 || c <= max)) {
					selectedChip = c;
					break;
				}
			}
		}
		for (int i = 0; i < CHIPS.length; i++) {
			int d = CHIPS[i];
			int x = compact() ? ox + 4 + i * 25 : ox + 8 + i * 27;
			int y = compact() ? oy + 136 : oy + 212;
			ChipButton b = addRenderableWidget(new ChipButton(x, y, d, theme, !compact(), () -> selectedChip, v -> selectedChip = v));
			b.active = d >= minBet() && (max <= 0 || d <= max);
		}
		int bw = TableChrome.balanceWidth(font, balance());
		int tx = ox + frame.w() - (compact() ? 2 : 4) - bw - 46;
		if (compact()) {
			addRenderableWidget(TableButton.icon(ox + 132, oy + 138, "rules", Component.translatable("gui.burmaldaholic.common.rules"), theme,
				b -> showRules = !showRules));
		} else {
			addRenderableWidget(TableButton.icon(tx, oy + 1, "rules", Component.translatable("gui.burmaldaholic.common.rules"), theme,
				b -> showRules = !showRules));
			addRenderableWidget(TableButton.icon(tx + 22, oy + 1, "leave", Component.translatable("gui.burmaldaholic.common.leave"), theme,
				b -> sendAction("leave"))).active = mySeat() >= 0;
		}
		if (state().getBooleanOr("you_shoot", false)) {
			int sx = compact() ? ox + 236 : ox + 373;
			int sy = compact() ? oy + 112 : oy + 189;
			TableButton roll = addRenderableWidget(TableButton.action(sx, sy, TableButton.Kind.ROLL, Component.translatable(K + "roll"), b -> {
				rollPressedAt = Util.getMillis();
				FxSounds.play("dice_cup", 1f);
				sendAction("roll");
			}));
			roll.active = state().getBooleanOr("can_roll", false);
			roll.pulse(roll.active);
		}
	}

	// ---- input --------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			if (throwPath != null && !skipped && rollMs() >= CrapsBeats.BADGE && rollMs() < CrapsBeats.IDLE) {
				skipped = true;
			}
			if (showRules) {
				if (!super.mouseClicked(event, doubleClick)) {
					showRules = false;
				}
				return true;
			}
			var a = felt().hit(event.x() - lx(), event.y() - ly());
			if (a.isPresent()) {
				click(a.get());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 32 && throwPath != null && rollMs() >= CrapsBeats.BADGE) { // Space skips the chip beats
			skipped = true;
			return true;
		}
		return super.keyPressed(event);
	}

	private void click(CrapsFelt.Area a) {
		if (!a.kind().equals("point") && allowed(a.kind())) {
			CompoundTag args = new CompoundTag();
			args.putString("kind", a.kind());
			args.putLong("amount", selectedChip);
			sendAction("bet", args);
			FxSounds.play("chip_place", 1f, (float) (0.9 + 0.1 * Math.log(selectedChip) / Math.log(5)));
			return;
		}
		BetView target = oddsTarget(a);
		if (target != null) {
			long amount = oddsAmount(target);
			if (amount <= 0) {
				showError(Component.translatable(K + "odds_multiple", Texts.chips(target.unit())));
				return;
			}
			CompoundTag args = new CompoundTag();
			args.putInt("bet", target.id());
			args.putLong("amount", amount);
			sendAction("odds", args);
			FxSounds.play("chip_place", 1f, 1.1f);
			return;
		}
		if ((a.kind().equals("pass") || a.kind().equals("dont_pass")) && state().getIntOr("point", 0) != 0) {
			showError(Component.translatable(K + "line_only_come_out"));
		} else {
			showError(Component.translatable("gui.burmaldaholic.error.invalid_bet_position"));
		}
	}

	// ---- rendering ----------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		hoverArea = showRules ? null : felt().hit(mouseX - lx(), mouseY - ly()).orElse(null);
		super.extractRenderState(g, mouseX, mouseY, a);
		g.nextStratum();
		status(g);
		if (showRules) {
			rules(g);
		}
		if (hoverArea != null) {
			List<FormattedCharSequence> tip = new ArrayList<>();
			for (Component c : tooltip(hoverArea)) {
				tip.addAll(font.split(c, 200));
			}
			g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		TableChrome.backdrop(g, theme, width, height);
		TableChrome.table(g, theme, frame, ox, oy);
		topBar(g);
		CrapsFelt f = felt();
		TableGfx.region(g, TableGfx.sheet("craps/layout" + (compact() ? "_compact_" : "_") + theme.id), f.w(), f.height(), 0, 0, f.w(), f.height(), lx(),
			ly(), 0xFFFFFFFF);
		labels(g, f);
		double t = t();
		boolean settled = t >= CrapsBeats.IDLE;
		CompoundTag s = state();
		int point = s.getIntOr("point", 0);
		int pointBefore = settled ? point : s.getIntOr("point_before", point);
		// hover outline
		if (hoverArea != null) {
			var r = hoverArea.rect();
			frameRect(g, lx() + r.x(), ly() + r.y(), r.w(), r.h(), 0xB0FFFFFF);
		}
		// point glow (steady after the puck landed)
		int glowPoint = t >= CrapsBeats.PUCK + CrapsBeats.PUCK_TOTAL ? point : pointBefore;
		if (glowPoint != 0) {
			var b = f.place(glowPoint);
			int gx = lx() + b.x() + b.w() / 2 - 24;
			int gy = ly() + b.y() + b.h() / 2 - 20;
			if (FxSettings.flashes() && !FxSettings.reduceMotion()) {
				TableGfx.blit(g, "craps/point_glow", gx, gy, 48, 40);
			} else {
				TableGfx.frame(g, "craps/point_glow", 48, 40, 4, 2, gx, gy, 0xFFFFFFFF);
			}
		}
		chips(g, f, t);
		puck(g, f, t, pointBefore, point);
		dice(g, f, t);
		cues(t);
		celebrate(t);
	}

	private void topBar(GuiGraphicsExtractor g) {
		int bx = TableChrome.balanceWidth(font, balance());
		int right = frame.w() - (compact() ? 2 : 4) - bx - (compact() ? 4 : 50);
		int x = TableChrome.title(g, font, theme, frame, ox, oy, title, compact() ? 90 : 120);
		if (!compact()) {
			String shooter = state().getStringOr("shooter", "");
			List<TableChrome.Seat> seats = new ArrayList<>();
			ListTag list = state().getListOrEmpty("seats");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag s = list.getCompoundOrEmpty(i);
				String name = s.getStringOr("name", "");
				int idx = s.getIntOr("index", i);
				String kind = name.equals(shooter) ? "active" : s.getBooleanOr("you", false) ? "you" : "other";
				seats.add(new TableChrome.Seat(Texts.raw(name), kind, TableChrome.seatTint(idx)));
			}
			int end = TableChrome.seats(g, font, theme, ox, oy, x + 6, seats, right);
			// hot shooter: the flame under the active plate (static with reduced motion)
			if (state().getIntOr("points_in_row", 0) >= 3 && !shooter.isEmpty() && end > x + 6) {
				int fx = x + 6;
				for (TableChrome.Seat seat : seats) {
					int w = font.width(seat.name()) + 20;
					if ("active".equals(seat.kind())) {
						if (FxSettings.reduceMotion()) {
							TableGfx.frame(g, "craps/flame", 32, 10, 3, 0, ox + fx + (w - 32) / 2, oy + 14, 0xFFFFFFFF);
						} else {
							TableGfx.blit(g, "craps/flame", ox + fx + (w - 32) / 2, oy + 14, 32, 10);
						}
					}
					fx += w + 4;
				}
			}
		}
		TableChrome.balance(g, font, theme, frame, ox, oy, balance());
	}

	private void labels(GuiGraphicsExtractor g, CrapsFelt f) {
		int line = theme.line;
		var dc = f.dontCome();
		List<FormattedCharSequence> dcl = font.split(Component.translatable(K + "layout.dont_come"), dc.w() - 6);
		int dy = ly() + dc.y() + (dc.h() - dcl.size() * 10) / 2 + 1;
		for (FormattedCharSequence l : dcl) {
			g.text(font, l, lx() + dc.x() + (dc.w() - font.width(l)) / 2, dy, line, true);
			dy += 10;
		}
		var come = f.come();
		big(g, Component.translatable(K + "layout.come"), lx() + come.cx() + (compact() ? 24 : 0), ly() + come.y() + (f.big() ? 9 : 7), come.w() / 2, line);
		var fl = f.fieldLabel();
		big(g, Component.translatable(K + "layout.field"), lx() + fl.cx(), ly() + fl.y() + (f.big() ? 8 : 7), fl.w() - 4, line);
		var dp = f.dontPass();
		small(g, Component.translatable(K + "layout.dont_pass"), lx() + dp.cx(), ly() + dp.y() + (dp.h() - 8) / 2 + 1, dp.w() - 60, line);
		var pass = f.pass();
		small(g, Component.translatable(K + "layout.pass_line"), lx() + pass.cx(), ly() + pass.y() + (pass.h() - 8) / 2 + 1, pass.w() - 20, line);
		var odds = f.odds();
		if (f.big()) {
			Component o = Component.translatable(K + "layout.odds");
			g.text(font, TableChrome.fit(font, o, 60), lx() + odds.x() + 6, ly() + odds.y(), line, true);
		}
	}

	/** Label at 2× when it fits its box, else 1×. */
	private void big(GuiGraphicsExtractor g, Component c, int cx, int y, int maxW, int color) {
		int w = font.width(c);
		if (w * 2 <= maxW) {
			g.pose().pushMatrix();
			g.pose().translate(cx - w, y);
			g.pose().scale(2f, 2f);
			g.text(font, c, 0, 0, color, true);
			g.pose().popMatrix();
		} else {
			small(g, c, cx, y + 4, maxW, color);
		}
	}

	private void small(GuiGraphicsExtractor g, Component c, int cx, int y, int maxW, int color) {
		FormattedCharSequence s = TableChrome.fit(font, c, maxW);
		g.text(font, s, cx - font.width(s) / 2, y, color, true);
	}

	private static void frameRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		g.fill(x, y, x + w, y + 1, c);
		g.fill(x, y + h - 1, x + w, y + h, c);
		g.fill(x, y, x + 1, y + h, c);
		g.fill(x + w - 1, y, x + w, y + h, c);
	}

	/** Chip stacks: pre-roll bets while the dice fly, the resolution beats, then the current bets. */
	private void chips(GuiGraphicsExtractor g, CrapsFelt f, double t) {
		int other = 1;
		if (t < CrapsBeats.CHIPS) {
			for (BetView b : prevBets()) {
				drawBet(g, f, b.kind(), b.point(), b.flat(), b.odds(), b.mine() ? 0 : other, 0, 0, 1);
			}
			return;
		}
		if (t < CrapsBeats.STICK) {
			double k = (t - CrapsBeats.CHIPS);
			int feltTop = oy + frame.feltY();
			int stagger = 0;
			for (Res r : lastRes()) {
				int slot = r.mine() ? 0 : other;
				switch (r.outcome()) {
					case "lose" -> {
						double s = Math.max(0, Math.min(1, (k - 30 * Math.min(12, stagger++)) / 260.0));
						if (s < 1) {
							int[] p = spot(f, r.kind(), r.point(), slot);
							int dy = -(int) Math.round((p[1] - feltTop) * Ease.IN_CUBIC.apply(s));
							drawBet(g, f, r.kind(), r.point(), r.flat(), r.odds(), slot, 0, dy, s > 0.7 ? 1 - (s - 0.7) / 0.3 : 1);
						}
					}
					case "move" -> {
						double s = Ease.IN_OUT_CUBIC.apply(Math.min(1, k / 400.0));
						int[] from = spot(f, r.kind(), 0, slot);
						int[] to = spot(f, r.kind(), r.movedTo(), slot);
						int x = (int) Math.round(from[0] + (to[0] - from[0]) * s);
						int y = (int) Math.round(from[1] + (to[1] - from[1]) * s - 10 * Math.sin(Math.PI * s));
						TableChrome.stack(g, r.flat(), x, y, r.mine() ? 0 : TableChrome.seatTint(other), 0xFFFFFFFF);
					}
					case "win" -> {
						long payout = Math.max(0, r.ret() - r.flat() - r.odds());
						double fly = Math.max(0, Math.min(1, (k - 400) / 420.0));
						int[] p = spot(f, r.kind(), r.point(), slot);
						int x = p[0];
						int y = p[1];
						if (fly > 0 && r.mine() && !FxSettings.reduceMotion()) {
							double e = Ease.IN_CUBIC.apply(fly);
							int bx = ox + frame.w() - 30;
							int by = oy + 10;
							x = (int) Math.round(x + (bx - x) * e);
							y = (int) Math.round(y + (by - y) * e);
						}
						if (fly < 1) {
							TableChrome.stack(g, r.flat() + r.odds(), x, y, r.mine() ? 0 : TableChrome.seatTint(other), 0xFFFFFFFF);
							int n = Math.min(5, (int) (k / 40) + 1);
							int[] discs = new int[ChipStacks.MAX_DISCS];
							n = Math.min(n, ChipStacks.discs(payout, discs));
							for (int d = 0; d < n; d++) {
								TableChrome.disc(g, discs[d], x + 8, y - 3 * d, r.mine() ? 0xFFFFFFFF : 0xB0FFFFFF);
							}
							if (r.mine() && fly <= 0) {
								TableChrome.label(g, font, theme, Component.empty().append(Texts.raw("+")).append(Texts.number(payout)), x + 4,
									y - TableChrome.stackHeight(r.flat() + r.odds()) - 16, Math.min(1, k / 250.0));
							}
						}
					}
					case "push", "odds_off" -> {
						double s = Math.min(1, k / 400.0);
						int[] p = spot(f, r.kind(), r.point(), slot);
						int x = (int) Math.round(p[0] + (ox + 60 - p[0]) * Ease.IN_CUBIC.apply(s));
						int y = (int) Math.round(p[1] + (oy + frame.barY() + 10 - p[1]) * Ease.IN_CUBIC.apply(s));
						if (s < 1) {
							TableChrome.stack(g, r.flat() + r.odds(), x, y, r.mine() ? 0 : TableChrome.seatTint(other), TableGfx.fade(1 - s * 0.6));
						}
					}
					default -> drawBet(g, f, r.kind(), r.point(), r.flat(), r.odds(), slot, 0, 0, 1);
				}
			}
			return;
		}
		for (BetView b : bets()) {
			drawBet(g, f, b.kind(), b.point(), b.flat(), b.odds(), b.mine() ? 0 : other, 0, 0, 1);
		}
	}

	private int[] spot(CrapsFelt f, String kind, int point, int slot) {
		int[] p = f.chipSpot(kind, point, slot);
		return new int[] {lx() + p[0], ly() + p[1]};
	}

	private void drawBet(GuiGraphicsExtractor g, CrapsFelt f, String kind, int point, long flat, long odds, int slot, int dx, int dy, double alpha) {
		int tint = slot == 0 ? 0 : TableChrome.seatTint(slot);
		int[] p = f.chipSpot(kind, point, slot);
		TableChrome.stack(g, flat, lx() + p[0] + dx, ly() + p[1] + dy, tint, TableGfx.fade(alpha));
		if (odds > 0) {
			int[] o = f.oddsSpot(kind, point, slot);
			TableChrome.stack(g, odds, lx() + o[0] + dx, ly() + o[1] + dy, tint, TableGfx.fade(alpha));
		}
	}

	private void puck(GuiGraphicsExtractor g, CrapsFelt f, double t, int from, int to) {
		double pt = FxSettings.reduceMotion() ? (t >= CrapsBeats.PUCK ? CrapsBeats.PUCK_TOTAL : 0) : t - CrapsBeats.PUCK;
		CrapsBeats.puck(f.puck(from), from, f.puck(to), to, Math.max(0, pt), puck);
		int x = lx() + (int) Math.round(puck.x);
		int y = ly() + (int) Math.round(puck.y);
		int lift = (int) Math.round(puck.lift);
		if (lift > 0) {
			TableGfx.blit(g, "craps/die_shadow", x, y + 13, 18, 6, 0xB0FFFFFF);
		}
		TableGfx.frame(g, "craps/puck", 18, 18, 5, puck.frame, x, y - lift, 0xFFFFFFFF);
		if (puck.frame == CrapsBeats.FRAME_ON || puck.frame == CrapsBeats.FRAME_OFF) {
			Component l = Component.translatable(puck.frame == CrapsBeats.FRAME_ON ? "gui.burmaldaholic.common.on" : "gui.burmaldaholic.common.off");
			int w = font.width(l);
			float sc = w > 14 ? 0.5f : 1f;
			g.pose().pushMatrix();
			g.pose().translate(x + 9, y - lift + 9);
			g.pose().scale(sc, sc);
			g.text(font, l, -w / 2, -4, puck.frame == CrapsBeats.FRAME_ON ? 0xFF26202C : 0xFFF4ECF8, false);
			g.pose().popMatrix();
		}
	}

	private static final int[] BLANK = {0, 0};

	/** The dice: waiting rattle, the shared throw, rest, the total badge, the stick-back. */
	private void dice(GuiGraphicsExtractor g, CrapsFelt f, double t) {
		var tex = TableGfx.sheet("craps/dice_small");
		long now = Util.getMillis();
		if (rollPressedAt >= 0 && now - rollPressedAt < 600) {
			// the shooter's dice rattle (blank tumble frames: no number before the result)
			int j = (int) ((now - rollPressedAt) / 60) % 2 == 0 ? 1 : -1;
			int bx = compact() ? ox + 210 : ox + 330;
			int by = compact() ? oy + 118 : oy + 196;
			for (int d = 0; d < 2; d++) {
				int fr = (int) ((now / 60 + d * 3) % 8);
				TableGfx.region(g, tex, 144, 36, fr * 18, 18, 18, 18, bx + d * 20 + j * (d == 0 ? 1 : -1), by, 0xFFFFFFFF);
			}
		}
		DiceThrowPath p = throwPath;
		if (p == null) {
			return;
		}
		boolean reduced = FxSettings.reduceMotion();
		// the dice stay where they landed, with their total, until the next throw (visual/tables.md mockups)
		double stick = 0;
		int stickDx = 0;
		int rightX = 0;
		int topY = Integer.MAX_VALUE;
		for (int d = 0; d < 2; d++) {
			p.sample(d, reduced ? p.durationMs() : t, sample);
			int x = lx() + (int) Math.round(sample.x) - 9 + (stick > 0 ? stickDx : 0);
			int y = ly() + (int) Math.round(sample.y - sample.z) - 9;
			double a = reduced ? Math.min(1, t / 200.0) : 1;
			TableGfx.blit(g, "craps/die_shadow", lx() + (int) Math.round(sample.x) - 9 + (stick > 0 ? stickDx : 0), ly() + (int) Math.round(sample.y) + 6, 18, 6,
				TableGfx.fade(a * Math.max(0.3, 1 - sample.z / 30)));
			if (sample.frame >= 0) {
				if (!reduced && sample.z > 8) {
					TableGfx.frame(g, "craps/streak", 12, 18, 1, 0, x + 12, y, 0x90FFFFFF);
				}
				TableGfx.region(g, tex, 144, 36, sample.frame * 18, 18, 18, 18, x, y, TableGfx.fade(a));
			} else {
				TableGfx.region(g, tex, 144, 36, (sample.face - 1) * 18, 0, 18, 18, x, y, TableGfx.fade(a));
			}
			rightX = Math.max(rightX, x + 18);
			topY = Math.min(topY, y);
		}
		// total badge: pops at 1350 in the event colour
		if (t >= CrapsBeats.BADGE && stick <= 0) {
			int total = state().getIntOr("d1", 0) + state().getIntOr("d2", 0);
			double k = reduced ? 1 : Math.min(1, (t - CrapsBeats.BADGE) / 250.0);
			double pop = Ease.OUT_BACK.apply(k);
			int shake = 0;
			if ("seven_out".equals(state().getStringOr("event", "")) && !reduced && t < CrapsBeats.BADGE + 400) {
				shake = (int) Math.round(2 * Math.sin((t - CrapsBeats.BADGE) / 400.0 * Math.PI * 6));
			}
			int bx = rightX + 6 + shake;
			int by = topY - 3 - (int) Math.round(4 * (1 - pop));
			TableGfx.blit(g, "craps/total_" + badgeKind(), bx, by, 24, 24, TableGfx.fade(Math.min(1, k * 2)));
			if (k > 0.4) {
				Component n = Texts.number(total);
				g.text(font, n, bx + 12 - font.width(n) / 2, by + 8, TableChrome.GOLD, true);
			}
		}
	}

	private String badgeKind() {
		return switch (state().getStringOr("event", "")) {
			case "natural", "point_made" -> "natural";
			case "craps", "seven_out" -> "craps";
			case "point_set" -> "point";
			default -> "neutral";
		};
	}

	/** Sound cues of the roll (own screen, personal playback). */
	private void cues(double t) {
		DiceThrowPath p = throwPath;
		if (p == null || lateRoll || t >= CrapsBeats.IDLE) {
			return;
		}
		var list = p.cues();
		if (cueIndex == 0 && t > 300) {
			while (cueIndex < list.size() && list.get(cueIndex).atMs() < t - 100) {
				cueIndex++;
			}
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

	/** The shared celebration after the payout flew to the balance (tables.md §0.3, §2.9). */
	private void celebrate(double t) {
		if (celebrated == shownRolls || t < CrapsBeats.CHIPS + 820 || lateRoll) {
			return;
		}
		celebrated = shownRolls;
		CompoundTag s = state();
		long staked = s.getLongOr("res_staked", 0);
		long ret = s.getLongOr("res_return", 0);
		if (staked <= 0) {
			return;
		}
		WinTier tier = WinTier.valueOf(s.getStringOr("tier", "LOSS"));
		if (tier.ordinal() >= WinTier.NICE.ordinal() && FxSettings.celebrations() != FxSettings.Celebrations.OFF) {
			CelebrationOverlay.get().play(CelebrationRequest.core(tier, ret, staked, s.getIntOr("seed", 0)));
		} else if (ret > staked) {
			FxSounds.play("win_small", 1f);
		} else if (ret > 0) {
			FxSounds.play("push", 1f);
		}
	}

	private void status(GuiGraphicsExtractor g) {
		CompoundTag s = state();
		int point = s.getIntOr("point", 0);
		double t = t();
		Component line1;
		int c1 = TableChrome.GOLD;
		String event = s.getStringOr("event", "");
		if (throwPath != null && t < CrapsBeats.BANNER) {
			line1 = Component.translatable("gui.burmaldaholic.craps.fx.throwing", Texts.raw(s.getStringOr("shooter", "")));
		} else if (throwPath != null && t < CrapsBeats.IDLE && eventLine(event, s.getIntOr("event_total", 0)) != null) {
			line1 = eventLine(event, s.getIntOr("event_total", 0));
			c1 = switch (event) {
				case "natural", "point_made" -> TableChrome.GREEN;
				case "craps", "seven_out" -> TableChrome.RED;
				default -> TableChrome.GOLD;
			};
		} else {
			line1 = point == 0 ? Component.translatable(K + "point_off") : Component.translatable(K + "point_on", Texts.number(point));
		}
		Component line2;
		long window = s.getBooleanOr("window", false) ? secondsLeft("window") : -1;
		long roll = secondsLeft("roll");
		boolean youShoot = s.getBooleanOr("you_shoot", false);
		String shooter = s.getStringOr("shooter", "");
		if (s.getBooleanOr("need_line", false)) {
			line2 = Component.translatable(K + "need_line_bet");
		} else if (window >= 0) {
			line2 = Component.translatable(K + "bet_window", Texts.plural("unit.burmaldaholic.second", window));
		} else if (!youShoot && !shooter.isEmpty()) {
			line2 = Component.translatable(K + "waiting_shooter", Texts.raw(shooter));
		} else if (roll >= 0 && youShoot) {
			line2 = Component.translatable(K + "auto_roll_in", Texts.plural("unit.burmaldaholic.second", roll));
		} else {
			long total = 0;
			for (BetView b : bets()) {
				if (b.mine()) {
					total += b.flat() + b.odds();
				}
			}
			line2 = Component.translatable("gui.burmaldaholic.common.total_bet", Texts.number(total));
		}
		if (error != null) {
			line1 = error;
			c1 = TableChrome.RED;
		}
		if (compact()) {
			var seq = TableChrome.fit(font, line1, 130);
			g.text(font, seq, ox + 200 - font.width(seq) / 2, oy + 140, c1, true);
		} else {
			TableChrome.status(g, font, ox + 262, oy + 211, 216, line1, line2, c1);
			if (window >= 0) {
				TableChrome.timer(g, ox + 352 - 4, oy + 186, window, Math.max(1, s.getLongOr("window_total", 20)), true);
			}
			Tag botLine = s.get("bot_line");
			if (botLine != null) {
				var seq = TableChrome.fit(font, decode(botLine), 200);
				g.text(font, seq, ox + 150, oy + 197, TableChrome.MUTED, true);
			}
		}
	}

	private static @Nullable Component eventLine(String event, int total) {
		String m = "msg.burmaldaholic.craps.";
		return switch (event) {
			case "natural" -> Component.translatable(m + "natural", Texts.number(total));
			case "craps" -> Component.translatable(m + "craps", Texts.number(total));
			case "point_set" -> Component.translatable(m + "point_set", Texts.number(total));
			case "point_made" -> Component.translatable(m + "point_made", Texts.number(total));
			case "seven_out" -> Component.translatable(m + "seven_out");
			default -> null;
		};
	}

	private void rules(GuiGraphicsExtractor g) {
		int x = ox + frame.feltX() + 16;
		int y = oy + frame.feltY() + 10;
		int w = frame.feltW() - 32;
		TableGfx.blit(g, "core/plate_" + theme.id, x, y, w, frame.feltH() - 20);
		int ty = y + 8;
		for (int i = 1; i <= 3; i++) {
			for (FormattedCharSequence seq : font.split(Component.translatable(K + "rules." + i), w - 16)) {
				g.text(font, seq, x + 8, ty, TableChrome.BONE, true);
				ty += 10;
			}
			ty += 4;
		}
		g.text(font, TableChrome.fit(font, limitsLine(), w - 16), x + 8, ty, TableChrome.GOLD, true);
	}

	private List<Component> tooltip(CrapsFelt.Area a) {
		List<Component> out = new ArrayList<>();
		out.add(a.kind().equals("point") ? Texts.number(a.point()).withStyle(ChatFormatting.YELLOW)
			: Component.translatable(K + a.kind()).withStyle(ChatFormatting.YELLOW));
		long others = 0;
		for (BetView b : betsOn(a)) {
			if (!b.mine()) {
				others += b.flat() + b.odds();
				continue;
			}
			Component label = betLabel(b.kind(), b.point());
			out.add(b.odds() > 0 ? Component.translatable(K + "bet_with_odds", label, Texts.chips(b.flat()), Texts.chips(b.odds()))
				: Component.translatable(K + "bet_flat", label, Texts.chips(b.flat())));
			if (b.off() && b.odds() > 0) {
				out.add(Component.translatable(K + "odds_off").withStyle(ChatFormatting.GRAY));
			}
		}
		if (others > 0) {
			out.add(Component.translatable(K + "others", Texts.chips(others)).withStyle(ChatFormatting.GRAY));
		}
		if (!a.kind().equals("point") && allowed(a.kind())) {
			out.add(Component.translatable(K + "click_to_bet", Texts.chipsAcc(selectedChip)).withStyle(ChatFormatting.GREEN));
		} else {
			BetView t = oddsTarget(a);
			if (t != null) {
				out.add(Component.translatable(K + "odds_max", Texts.chips(t.oddsMax())).withStyle(ChatFormatting.GRAY));
				out.add(Component.translatable(K + "odds_multiple", Texts.chips(t.unit())).withStyle(ChatFormatting.GRAY));
				long amt = oddsAmount(t);
				if (amt > 0) {
					out.add(Component.translatable(K + "click_odds", Texts.chips(amt)).withStyle(ChatFormatting.GREEN));
				}
			}
		}
		return out;
	}

	private static MutableComponent betLabel(String kind, int point) {
		if (kind.equals("come") && point != 0) {
			return Component.translatable(K + "come_point", Texts.number(point));
		}
		if (kind.equals("dont_come") && point != 0) {
			return Component.translatable(K + "dont_come_point", Texts.number(point));
		}
		return Component.translatable(K + kind);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		// the chrome draws the title, status and error line
	}
}
