package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Craps table screen (UI.md §8, 400 × 240): layout with Pass line, Don't Pass bar, Come, Don't Come,
 * Field and the point boxes 4/5/6/8/9/10 with the puck; odds are added by clicking your line bet (or
 * the point box your come bet travelled to). Dice tray with two rendered dice, Roll for the shooter
 * only, shooter name and betting-window / auto-roll timer. Purely a view: every click is an action the
 * server validates. Labels wrap inside their areas so Russian text never truncates.
 */
public class CrapsScreen extends CasinoTableScreen {
	private static final String K = "gui.burmaldaholic.craps.";
	private static final int W = 400;
	private static final int H = 240;
	private static final int PAD = 8;
	private static final int LEFT_W = 232;
	private static final int RX = 248;
	private static final int RW = W - RX - PAD;
	private static final int BOX_Y = 18;
	private static final int BOX_H = 30;
	private static final int CONTROLS_Y = 150;
	private static final int[] POINTS = {4, 5, 6, 8, 9, 10};
	private static final int[] CHIPS = {1, 5, 25, 100, 500};
	private static final int GOLD = 0xFFFFD700;
	private static final int GRAY = 0xFFBBBBBB;
	private static final int LINE = 0xFFEEEEDD;

	/** A clickable region of the layout. {@code kind} = bet kind id, or "point" with {@code point}. */
	private record Area(String kind, int point, int x, int y, int w, int h, int color) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** One bet as sent by the server. */
	private record BetView(int id, String kind, long flat, long odds, int point, boolean mine, int unit, long oddsMax, long room,
			boolean off) {}

	private final List<Area> areas = new ArrayList<>();
	private final Random cosmetic = new Random();
	private long betAmount = -1;
	private boolean showRules;
	private int stateAge;
	private int lastRolls = -1;
	private int animTicks;
	private int animD1 = 1;
	private int animD2 = 1;

	public CrapsScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(K + "title"), W, H);
	}

	@Override
	protected void init() {
		super.init();
		buildAreas();
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag s) {
		stateAge = 0;
		int rolls = s.getIntOr("rolls", 0);
		if (lastRolls >= 0 && rolls > lastRolls) {
			animTicks = 16;
		}
		lastRolls = rolls;
		if (betAmount < 0 && s.contains("min")) {
			betAmount = minBet();
		}
		if (minecraft != null) {
			rebuild();
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		stateAge++;
		if (animTicks > 0) {
			animTicks--;
			if (animTicks % 2 == 0) {
				animD1 = 1 + cosmetic.nextInt(6);
				animD2 = 1 + cosmetic.nextInt(6);
			}
		}
	}

	// ---- layout -----------------------------------------------------------------------------------

	private void buildAreas() {
		areas.clear();
		int boxW = LEFT_W / POINTS.length;
		for (int i = 0; i < POINTS.length; i++) {
			areas.add(new Area("point", POINTS[i], PAD + i * boxW, BOX_Y, boxW - 2, BOX_H, 0xFF24704A));
		}
		areas.add(new Area("dont_come", 0, PAD, 52, 72, 22, 0xFF3A3A3A));
		areas.add(new Area("come", 0, PAD + 74, 52, LEFT_W - 76, 22, 0xFF2C7A4E));
		areas.add(new Area("field", 0, PAD, 76, LEFT_W - 2, 24, 0xFF2E6B8F));
		areas.add(new Area("dont_pass", 0, PAD, 102, LEFT_W - 2, 18, 0xFF3A3A3A));
		areas.add(new Area("pass", 0, PAD, 122, LEFT_W - 2, 22, 0xFF2C7A4E));
	}

	private int flowX;
	private int flowY;

	private void rebuild() {
		clearWidgets();
		CompoundTag s = state();
		flowX = PAD;
		flowY = CONTROLS_Y + 12;
		for (int d : CHIPS) {
			flow(Texts.number(d), b -> addChips(d), true, 24);
		}
		flow(Component.translatable("gui.burmaldaholic.common.clear"), b -> betAmount = 0, true, 36);
		flow(Component.translatable("gui.burmaldaholic.common.max"), b -> betAmount = Math.max(0, Math.min(maxBet(), balance())), true, 36);
		flow(Component.translatable("gui.burmaldaholic.common.rules"), b -> showRules = !showRules, true, 40);
		flow(Component.translatable("gui.burmaldaholic.common.leave"), b -> sendAction("leave"), mySeat() >= 0, 40);
		// Roll button under the dice tray (right panel), shooter only.
		if (s.getBooleanOr("you_shoot", false)) {
			Component roll = Component.translatable(K + "roll");
			int w = Math.min(RW, Math.max(80, font.width(roll) + 8));
			Button b = button(roll, RX + (RW - w) / 2, diceY() + 34, w, x -> sendAction("roll"));
			b.active = s.getBooleanOr("can_roll", false);
		}
	}

	private void flow(Component label, Button.OnPress onPress, boolean active, int minWidth) {
		int w = Math.max(minWidth, font.width(label) + 8);
		if (flowX + w > PAD + LEFT_W && flowX > PAD) {
			flowX = PAD;
			flowY += 22;
		}
		Button b = button(label, flowX, flowY, w, onPress);
		b.active = active;
		flowX += w + 3;
	}

	private void addChips(long chips) {
		long next = Math.max(0, betAmount) + chips;
		long max = maxBet();
		betAmount = max > 0 ? Math.min(next, max) : next;
	}

	private int diceY() {
		return 150;
	}

	// ---- state helpers -----------------------------------------------------------------------------

	private List<BetView> bets() {
		List<BetView> out = new ArrayList<>();
		ListTag list = state().getListOrEmpty("bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			out.add(new BetView(t.getIntOr("id", -1), t.getStringOr("kind", ""), t.getLongOr("flat", 0), t.getLongOr("odds", 0),
				t.getIntOr("point", 0), t.getBooleanOr("mine", false), t.getIntOr("unit", 1), t.getLongOr("odds_max", 0),
				t.getLongOr("room", 0), t.getBooleanOr("off", false)));
		}
		return out;
	}

	/** Bets drawn on an area: line/field/unmoved come bets by kind; point boxes by travelled point. */
	private List<BetView> betsOn(Area a) {
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

	/** The viewer's bet an odds click on this area would target. */
	private @Nullable BetView oddsTarget(Area a) {
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

	private boolean allowed(String kind) {
		return state().getCompoundOrEmpty("allowed").getBooleanOr(kind, false);
	}

	private long secondsLeft(String timer) {
		long ticks = state().getCompoundOrEmpty("timers").getLongOr(timer, -1);
		if (ticks < 0) {
			return -1;
		}
		return Math.max(0, (ticks - stateAge + 19) / 20);
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

	// ---- input -------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && !showRules) {
			double mx = event.x() - leftPos;
			double my = event.y() - topPos;
			for (Area a : areas) {
				if (a.contains(mx, my)) {
					click(a);
					return true;
				}
			}
		} else if (event.button() == 0 && showRules) {
			double mx = event.x() - leftPos;
			double my = event.y() - topPos;
			if (mx >= PAD && mx < PAD + LEFT_W && my >= BOX_Y && my < CONTROLS_Y - 2) {
				showRules = false;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void click(Area a) {
		if (!a.kind().equals("point") && allowed(a.kind())) {
			if (betAmount <= 0) {
				showError(Component.translatable("gui.burmaldaholic.error.invalid_amount"));
				return;
			}
			CompoundTag args = new CompoundTag();
			args.putString("kind", a.kind());
			args.putLong("amount", betAmount);
			sendAction("bet", args);
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
			return;
		}
		if ((a.kind().equals("pass") || a.kind().equals("dont_pass")) && state().getIntOr("point", 0) != 0) {
			showError(Component.translatable(K + "line_only_come_out"));
		} else {
			showError(Component.translatable("gui.burmaldaholic.error.invalid_bet_position"));
		}
	}

	/** UI.md §8: the chosen amount, capped at the room left and snapped DOWN to a valid multiple. */
	private long oddsAmount(BetView b) {
		long raw = Math.min(Math.max(0, betAmount), b.room());
		return raw / b.unit() * b.unit();
	}

	// ---- rendering ---------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int ox = leftPos;
		int oy = topPos;
		CompoundTag s = state();
		int point = s.getIntOr("point", 0);
		for (Area area : areas) {
			drawArea(g, area, point, area.contains(mouseX - ox, mouseY - oy) && !showRules);
		}
		drawStatus(g, s);
		drawDiceTray(g, s);
		drawControlsLine(g);
		if (showRules) {
			drawRules(g);
		}
	}

	private void drawArea(GuiGraphicsExtractor g, Area a, int point, boolean hover) {
		int x = leftPos + a.x();
		int y = topPos + a.y();
		g.fill(x, y, x + a.w(), y + a.h(), a.color());
		g.outline(x, y, a.w(), a.h(), hover ? 0xFFFFFFFF : LINE);
		List<BetView> on = betsOn(a);
		long mine = 0;
		long mineOdds = 0;
		long others = 0;
		for (BetView b : on) {
			if (b.mine()) {
				mine += b.flat();
				mineOdds += b.odds();
			} else {
				others += b.flat() + b.odds();
			}
		}
		if (a.kind().equals("point")) {
			g.pose().pushMatrix();
			g.pose().translate(x + a.w() / 2f, y + 3f);
			g.pose().scale(1.5f, 1.5f);
			g.centeredText(font, Texts.number(a.point()), 0, 0, 0xFFFFFFFF);
			g.pose().popMatrix();
			if (point == a.point()) {
				drawPuck(g, x + a.w() - 9, y + 7, true);
			}
			if (mine > 0) {
				Component amt = mineOdds > 0 ? Component.translatable(K + "amount_with_odds", Texts.number(mine), Texts.number(mineOdds)) : Texts.number(mine);
				drawFitted(g, amt, x + a.w() / 2, y + a.h() - 10, a.w() - 2, GOLD);
			} else if (others > 0) {
				drawFitted(g, Texts.number(others), x + a.w() / 2, y + a.h() - 10, a.w() - 2, GRAY);
			}
			return;
		}
		// Label (wrapped to the area) on the left, amounts on the right.
		Component label = Component.translatable(K + a.kind());
		if (a.kind().equals("field")) {
			label = Component.translatable(K + "field_label", Texts.number(state().getIntOr("field2", 2)), Texts.number(state().getIntOr("field12", 3)));
		}
		Component amount = null;
		if (mine > 0) {
			amount = mineOdds > 0 ? Component.translatable(K + "amount_with_odds", Texts.number(mine), Texts.number(mineOdds)) : Texts.number(mine);
		}
		int amountW = amount == null ? 0 : font.width(amount) + 6;
		int othersW = 0;
		Component othersC = null;
		if (others > 0) {
			othersC = Component.translatable(K + "others_short", Texts.number(others));
			othersW = font.width(othersC) + 6;
		}
		int textW = Math.max(20, a.w() - 6 - amountW - othersW);
		List<FormattedCharSequence> lines = font.split(label, textW);
		int lineH = lines.size() > 1 ? 9 : font.lineHeight;
		int ty = y + (a.h() - lines.size() * lineH) / 2 + 1;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, x + 4, ty, 0xFFFFFFFF, true);
			ty += lineH;
		}
		int ay = y + (a.h() - font.lineHeight) / 2 + 1;
		int rx = x + a.w() - 4;
		if (amount != null) {
			rx -= font.width(amount);
			g.text(font, amount, rx, ay, GOLD, true);
			rx -= 6;
		}
		if (othersC != null) {
			g.text(font, othersC, rx - font.width(othersC), ay, GRAY, true);
		}
		if (a.kind().equals("dont_come") && point == 0) {
			drawPuck(g, x + a.w() - 9 - amountW, y + a.h() / 2, false);
		}
	}

	private void drawFitted(GuiGraphicsExtractor g, Component c, int cx, int y, int maxW, int color) {
		List<FormattedCharSequence> lines = font.split(c, Math.max(10, maxW));
		if (!lines.isEmpty()) {
			g.centeredText(font, lines.get(0), cx, y, color);
		}
	}

	/** The puck: white "ON" on the point box, black "OFF" in the Don't Come bar during the come-out. */
	private void drawPuck(GuiGraphicsExtractor g, int cx, int cy, boolean on) {
		int r = 7;
		g.fill(cx - r, cy - r + 2, cx + r, cy + r - 2, on ? 0xFFFFFFFF : 0xFF111111);
		g.fill(cx - r + 2, cy - r, cx + r - 2, cy + r, on ? 0xFFFFFFFF : 0xFF111111);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy - 2f);
		g.pose().scale(0.5f, 0.5f);
		Component label = Component.translatable(on ? "gui.burmaldaholic.common.on" : "gui.burmaldaholic.common.off");
		g.centeredText(font, label, 0, 0, on ? 0xFF111111 : 0xFFFFFFFF);
		g.pose().popMatrix();
	}

	private void drawStatus(GuiGraphicsExtractor g, CompoundTag s) {
		int x = leftPos + RX;
		int y = topPos + BOX_Y;
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		g.text(font, balance, leftPos + W - PAD - font.width(balance), topPos + 6, GOLD, true);
		List<Component> lines = new ArrayList<>();
		int point = s.getIntOr("point", 0);
		lines.add(point == 0 ? Component.translatable(K + "point_off").withStyle(ChatFormatting.YELLOW)
			: Component.translatable(K + "point_on", Texts.number(point)).withStyle(ChatFormatting.YELLOW));
		String shooter = s.getStringOr("shooter", "");
		boolean youShoot = s.getBooleanOr("you_shoot", false);
		if (youShoot) {
			lines.add(Component.translatable(K + "you_shoot").withStyle(ChatFormatting.GOLD));
		} else if (!shooter.isEmpty()) {
			lines.add(Component.translatable(K + "shooter", Texts.raw(shooter)));
		}
		long window = s.getBooleanOr("window", false) ? secondsLeft("window") : -1;
		long roll = secondsLeft("roll");
		if (s.getBooleanOr("need_line", false)) {
			lines.add(Component.translatable(K + "need_line_bet").withStyle(ChatFormatting.GRAY));
		} else if (window >= 0) {
			lines.add(Component.translatable(K + "bet_window", Texts.plural("unit.burmaldaholic.second", window))
				.withStyle(window < 5 ? ChatFormatting.RED : ChatFormatting.GRAY));
		} else {
			if (!youShoot && !shooter.isEmpty()) {
				lines.add(Component.translatable(K + "waiting_shooter", Texts.raw(shooter)).withStyle(ChatFormatting.GRAY));
			}
			if (roll >= 0) {
				lines.add(Component.translatable(K + "auto_roll_in", Texts.plural("unit.burmaldaholic.second", roll))
					.withStyle(roll < 5 ? ChatFormatting.RED : ChatFormatting.GRAY));
			}
		}
		int d1 = s.getIntOr("d1", 0);
		int d2 = s.getIntOr("d2", 0);
		if (d1 > 0 && animTicks == 0) {
			lines.add(Component.translatable(K + "last_roll", Texts.number(d1), Texts.number(d2), Texts.number(d1 + d2)));
			Component ev = eventLine(s.getStringOr("event", ""), s.getIntOr("event_total", 0));
			if (ev != null) {
				lines.add(ev);
			}
		}
		int limit = topPos + diceY() - 4;
		for (Component c : lines) {
			for (FormattedCharSequence seq : font.split(c, RW)) {
				if (y + font.lineHeight > limit) {
					return;
				}
				g.text(font, seq, x, y, 0xFFFFFFFF, true);
				y += font.lineHeight + 1;
			}
		}
	}

	private static @Nullable Component eventLine(String event, int total) {
		String m = "msg.burmaldaholic.craps.";
		return switch (event) {
			case "natural" -> Component.translatable(m + "natural", Texts.number(total)).withStyle(ChatFormatting.GREEN);
			case "craps" -> Component.translatable(m + "craps", Texts.number(total)).withStyle(ChatFormatting.RED);
			case "point_set" -> Component.translatable(m + "point_set", Texts.number(total)).withStyle(ChatFormatting.YELLOW);
			case "point_made" -> Component.translatable(m + "point_made", Texts.number(total)).withStyle(ChatFormatting.GREEN);
			case "seven_out" -> Component.translatable(m + "seven_out").withStyle(ChatFormatting.RED);
			default -> null;
		};
	}

	private void drawDiceTray(GuiGraphicsExtractor g, CompoundTag s) {
		int x = leftPos + RX;
		int y = topPos + diceY();
		g.fill(x, y, x + RW, y + 30, 0xFF5A3A1E);
		g.outline(x, y, RW, 30, 0xFF2E1D0E);
		int d1 = s.getIntOr("d1", 0);
		int d2 = s.getIntOr("d2", 0);
		int jitter = animTicks > 0 ? (animTicks % 4) - 2 : 0;
		if (animTicks > 0) {
			d1 = animD1;
			d2 = animD2;
		}
		int size = 22;
		int cx = x + RW / 2;
		drawDie(g, cx - size - 4, y + 4 + jitter, size, d1);
		drawDie(g, cx + 4, y + 4 - jitter, size, d2);
	}

	/** A die face drawn with fills (0 = blank, before the first roll). */
	static void drawDie(GuiGraphicsExtractor g, int x, int y, int size, int face) {
		g.fill(x + 1, y, x + size - 1, y + size, 0xFFF4F1E8);
		g.fill(x, y + 1, x + size, y + size - 1, 0xFFF4F1E8);
		g.outline(x, y, size, size, 0xFF8A8577);
		int p = Math.max(2, size / 6);
		int lo = x + size / 4 - p / 2;
		int mid = x + size / 2 - p / 2;
		int hi = x + size * 3 / 4 - p / 2;
		int top = y + size / 4 - p / 2;
		int cen = y + size / 2 - p / 2;
		int bot = y + size * 3 / 4 - p / 2;
		int c = face == 1 ? 0xFFB01E1E : 0xFF1A1A1A;
		switch (face) {
			case 1 -> pip(g, mid, cen, p, c);
			case 2 -> {
				pip(g, lo, top, p, c);
				pip(g, hi, bot, p, c);
			}
			case 3 -> {
				pip(g, lo, top, p, c);
				pip(g, mid, cen, p, c);
				pip(g, hi, bot, p, c);
			}
			case 4, 5, 6 -> {
				pip(g, lo, top, p, c);
				pip(g, hi, top, p, c);
				pip(g, lo, bot, p, c);
				pip(g, hi, bot, p, c);
				if (face == 5) {
					pip(g, mid, cen, p, c);
				}
				if (face == 6) {
					pip(g, lo, cen, p, c);
					pip(g, hi, cen, p, c);
				}
			}
			default -> {
			}
		}
	}

	private static void pip(GuiGraphicsExtractor g, int x, int y, int p, int color) {
		g.fill(x, y, x + p, y + p, color);
	}

	private void drawControlsLine(GuiGraphicsExtractor g) {
		Component bet = Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(Math.max(0, betAmount)));
		g.text(font, bet, leftPos + PAD, topPos + CONTROLS_Y, GOLD, true);
		Component limits = limitsLine();
		int lx = leftPos + PAD + font.width(bet) + 8;
		if (lx + font.width(limits) <= leftPos + PAD + LEFT_W) {
			g.text(font, limits, lx, topPos + CONTROLS_Y, GRAY, true);
		}
	}

	private void drawRules(GuiGraphicsExtractor g) {
		int x = leftPos + PAD;
		int y = topPos + BOX_Y;
		g.fill(x, y, x + LEFT_W, topPos + CONTROLS_Y - 2, 0xF0102018);
		g.outline(x, y, LEFT_W, CONTROLS_Y - 2 - BOX_Y, LINE);
		int ty = y + 4;
		for (int i = 1; i <= 3; i++) {
			for (FormattedCharSequence seq : font.split(Component.translatable(K + "rules." + i), LEFT_W - 8)) {
				g.text(font, seq, x + 4, ty, 0xFFFFFFFF, true);
				ty += font.lineHeight + 1;
			}
			ty += 3;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (showRules) {
			return;
		}
		for (Area area : areas) {
			if (area.contains(mouseX - leftPos, mouseY - topPos)) {
				List<FormattedCharSequence> tip = new ArrayList<>();
				for (Component c : tooltip(area)) {
					tip.addAll(font.split(c, 200));
				}
				g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
				return;
			}
		}
	}

	private List<Component> tooltip(Area a) {
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
			out.add(Component.translatable(K + "click_to_bet", Texts.chipsAcc(Math.max(0, betAmount))).withStyle(ChatFormatting.GREEN));
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
}
