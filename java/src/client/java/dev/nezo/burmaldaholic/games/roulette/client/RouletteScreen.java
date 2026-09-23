package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Layout;
import dev.nezo.burmaldaholic.games.roulette.logic.SpinAnimation;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import org.jspecify.annotations.Nullable;

/**
 * Roulette screen (UI.md §7): clickable European layout (cell = straight, edge = split, outer row edge =
 * street, intersection = corner / six line, zero edges = split / trio / first four), chip value selector,
 * {@code [Clear] [Rebet] [Spin|Ready] [Leave]}, history strip, and a wheel strip animation that replaces
 * the layout while the ball spins. Only renders server state and sends actions.
 */
public class RouletteScreen extends CasinoTableScreen {
	private static final int W = 380;
	private static final int H = 236;
	private static final int PAD = 8;
	private static final int LX = (W - Layout.WIDTH) / 2;
	private static final int LY = 44;
	private static final int[] CHIPS = {1, 5, 25, 100, 500};
	private static final int RED = 0xFFB3261E;
	private static final int BLACK = 0xFF151515;
	private static final int GREEN = 0xFF1B8A3A;
	private static final int LINE = 0xFFE8E8E8;
	private static final int GOLD = 0xFFFFD700;
	private static final int DIM = 0xFFBBBBBB;

	private int selectedChip = 1;
	private long clientTicks;
	private long stateTick;
	private @Nullable Spot hover;
	private float partialTick;
	private int flowX;
	private int flowY;

	public RouletteScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType() == RouletteModule.HIGH_ROLLER_TABLE
			? "gui.burmaldaholic.roulette.title_high_roller" : "gui.burmaldaholic.roulette.title"), W, H);
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		stateTick = clientTicks;
		if (minecraft != null) {
			rebuild();
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		clientTicks++;
	}

	// ---- state helpers ------------------------------------------------------------------------

	private boolean betting() {
		return "betting".equals(phase());
	}

	private long total() {
		return state().getLongOr("total", 0);
	}

	/** Ticks left in the current phase, counted down locally between state packets (-1 = none). */
	private double ticksLeft(float partial) {
		long left = state().getLongOr("ticks_left", -1);
		if (left < 0) {
			return -1;
		}
		return Math.max(0, left - (clientTicks - stateTick) - partial);
	}

	private Map<Spot, Long> myBets() {
		Map<Spot, Long> out = new LinkedHashMap<>();
		ListTag list = state().getListOrEmpty("bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			Spot.parseKey(b.getStringOr("spot", "")).ifPresent(s -> out.put(s, b.getLongOr("amount", 0)));
		}
		return out;
	}

	private Map<Spot, Long> otherBets() {
		Map<Spot, Long> out = new LinkedHashMap<>();
		CompoundTag others = state().getCompoundOrEmpty("others");
		for (String key : others.keySet()) {
			Spot.parseKey(key).ifPresent(s -> out.put(s, others.getLongOr(key, 0)));
		}
		return out;
	}

	private int[] history() {
		return state().getIntArray("history").orElse(new int[0]);
	}

	private int result() {
		return state().getIntOr("result", -1);
	}

	private boolean multiplayer() {
		return state().getIntOr("bettors", 0) > 1 || state().getListOrEmpty("seats").size() > 1;
	}

	// ---- widgets ------------------------------------------------------------------------------

	private void rebuild() {
		clearWidgets();
		long max = maxBet();
		if (selectedChip > Math.max(1, max) || selectedChip < minBet()) {
			selectedChip = CHIPS[0];
			for (int c : CHIPS) {
				if (c >= minBet() && (max <= 0 || c <= max)) {
					selectedChip = c;
					break;
				}
			}
		}
		flowX = PAD;
		flowY = 166;
		boolean betting = betting() && state().getBooleanOr("enabled", true);
		for (int chip : CHIPS) {
			Button b = flow(Texts.number(chip), btn -> {
				selectedChip = chip;
				rebuild();
			}, 26);
			b.active = chip != selectedChip && chip >= minBet() && (max <= 0 || chip <= max);
		}
		flowX += 6;
		flow(Component.translatable("gui.burmaldaholic.common.clear"), b -> sendAction("clear"), 40).active = betting && total() > 0;
		flow(Component.translatable("gui.burmaldaholic.common.rebet"), b -> sendAction("rebet"), 40).active = betting && state().getBooleanOr("can_rebet", false);
		boolean ready = state().getBooleanOr("ready", false);
		Component spinLabel = Component.translatable(multiplayer() ? "gui.burmaldaholic.common.ready" : "gui.burmaldaholic.common.spin");
		flow(spinLabel, b -> sendAction("spin"), 44).active = betting && total() > 0 && !ready;
		flow(Component.translatable("gui.burmaldaholic.common.leave"), b -> {
			sendAction("leave");
			onClose();
		}, 40).active = mySeat() >= 0;
	}

	private Button flow(Component label, Button.OnPress onPress, int minWidth) {
		int w = Math.max(minWidth, font.width(label) + 8);
		if (flowX + w > imageWidth - PAD && flowX > PAD) {
			flowX = PAD;
			flowY += 22;
		}
		Button b = button(label, flowX, flowY, w, onPress);
		flowX += w + 3;
		return b;
	}

	// ---- input --------------------------------------------------------------------------------

	private Optional<Spot> spotAt(double mouseX, double mouseY) {
		if (!betting()) {
			return Optional.empty();
		}
		return Layout.hit(mouseX - leftPos - LX, mouseY - topPos - LY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			Optional<Spot> spot = spotAt(event.x(), event.y());
			if (spot.isPresent()) {
				CompoundTag args = new CompoundTag();
				args.putString("type", spot.get().type().id());
				args.putIntArray("nums", spot.get().numbers().stream().mapToInt(Integer::intValue).toArray());
				args.putLong("amount", selectedChip);
				sendAction("bet", args);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---- rendering ----------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		hover = spotAt(mouseX, mouseY).orElse(null);
		partialTick = a;
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (hover != null) {
			graphics.setComponentTooltipForNextFrame(font, tooltip(hover), mouseX, mouseY);
		}
	}

	private List<Component> tooltip(Spot spot) {
		List<Component> lines = new ArrayList<>();
		Component desc = describe(spot);
		Component payout = Component.translatable("gui.burmaldaholic.roulette.bet." + spot.type().id());
		lines.add(desc);
		if (!desc.getString().equals(payout.getString())) {
			lines.add(payout.copy().withColor(DIM));
		}
		Long mine = myBets().get(spot);
		if (mine != null) {
			lines.add(Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.chips(mine)).withColor(GOLD));
		}
		return lines;
	}

	/** "Split 17-20", "2nd dozen (13–24)", "Red (1:1)". */
	static Component describe(Spot s) {
		return switch (s.type()) {
			case STRAIGHT, SPLIT, STREET, TRIO, CORNER, SIX_LINE ->
				Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id(), Texts.raw(s.label()));
			case DOZEN, COLUMN -> Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id() + "." + s.outsideIndex());
			default -> Component.translatable("gui.burmaldaholic.roulette.bet." + s.type().id());
		};
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		float partial = partialTick;
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		graphics.text(font, balance, imageWidth - PAD - font.width(balance), titleLabelY, GOLD, true);
		drawStatus(graphics, partial);
		drawHistory(graphics);
		if ("spin".equals(phase()) && result() >= 0) {
			drawWheel(graphics, partial);
		} else {
			drawLayout(graphics);
		}
		drawTotals(graphics);
	}

	private void drawStatus(GuiGraphicsExtractor graphics, float partial) {
		Component status;
		String phase = phase();
		switch (phase) {
			case "no_more_bets" -> status = Component.translatable("gui.burmaldaholic.roulette.no_more_bets");
			case "spin" -> status = Component.translatable("msg.burmaldaholic.roulette.spinning");
			case "result" -> status = result() >= 0
				? RouletteTableBlockEntity.resultLine(result(), state().getLongOr("out_staked", 0), state().getLongOr("out_return", 0))
				: Component.translatable("gui.burmaldaholic.roulette.place_bets");
			default -> {
				if (!state().getBooleanOr("enabled", true)) {
					status = Component.translatable("gui.burmaldaholic.error.disabled");
				} else {
					double left = ticksLeft(partial);
					status = left >= 0
						? Component.translatable("gui.burmaldaholic.common.timer", Texts.plural("unit.burmaldaholic.second", (long) Math.ceil(left / 20.0)))
						: Component.translatable("gui.burmaldaholic.roulette.place_bets");
				}
			}
		}
		int color = "result".equals(phase) ? LINE : (ticksLeft(partial) >= 0 && ticksLeft(partial) < 100 && betting() ? ERROR : LINE);
		fitted(graphics, status, imageWidth / 2, 20, imageWidth - 2 * PAD, color);
	}

	private void drawHistory(GuiGraphicsExtractor graphics) {
		int[] hist = history();
		if (hist.length == 0) {
			return;
		}
		Component label = Component.translatable("gui.burmaldaholic.common.history");
		int y = 31;
		graphics.text(font, label, PAD, y, DIM, true);
		int x = PAD + font.width(label) + 4;
		for (int i = 0; i < hist.length && x + 15 <= imageWidth - PAD; i++) {
			int n = hist[i];
			pocketBox(graphics, n, x, y - 1, 15, 10, i == 0);
			x += 17;
		}
	}

	/** A small pocket badge: red filled, black outlined, zero green (UI.md §accessibility). */
	private void pocketBox(GuiGraphicsExtractor graphics, int n, int x, int y, int w, int h, boolean highlight) {
		switch (Wheel.color(n)) {
			case RED -> graphics.fill(x, y, x + w, y + h, RED);
			case GREEN -> graphics.fill(x, y, x + w, y + h, GREEN);
			case BLACK -> {
				graphics.fill(x, y, x + w, y + h, BLACK);
				graphics.outline(x, y, w, h, LINE);
			}
		}
		if (highlight) {
			graphics.outline(x - 1, y - 1, w + 2, h + 2, GOLD);
		}
		fitted(graphics, Texts.number(n), x + w / 2, y + h / 2 - 4, w - 2, 0xFFFFFFFF);
	}

	private void drawLayout(GuiGraphicsExtractor graphics) {
		int winning = "result".equals(phase()) ? result() : -1;
		// Zero + numbers.
		for (int n = 0; n <= 36; n++) {
			Layout.Rect r = Layout.cell(n);
			int x = LX + r.x();
			int y = LY + r.y();
			switch (Wheel.color(n)) {
				case RED -> graphics.fill(x, y, x + r.w(), y + r.h(), RED);
				case GREEN -> graphics.fill(x, y, x + r.w(), y + r.h(), GREEN);
				case BLACK -> graphics.outline(x + 2, y + 2, r.w() - 4, r.h() - 4, BLACK);
			}
			graphics.outline(x, y, r.w(), r.h(), LINE);
			fitted(graphics, Texts.number(n), x + r.w() / 2, y + r.h() / 2 - 4, r.w() - 2, 0xFFFFFFFF);
			if (n == winning) {
				graphics.outline(x - 1, y - 1, r.w() + 2, r.h() + 2, GOLD);
				graphics.outline(x, y, r.w(), r.h(), GOLD);
			}
		}
		// Outside boxes.
		for (BetType t : List.of(BetType.COLUMN, BetType.DOZEN)) {
			for (Spot s : Spot.all(t)) {
				Layout.Rect r = Layout.outsideBox(s);
				Component label = t == BetType.COLUMN ? Texts.raw("2:1") // literal-ok: payout ratio, language neutral
					: Texts.raw(s.numbers().get(0) + "–" + s.numbers().get(s.numbers().size() - 1)); // literal-ok: number range
				box(graphics, r, label, 0, s.covers(winning));
			}
		}
		for (BetType t : Layout.EVEN_ROW) {
			Spot s = Spot.all(t).get(0);
			Layout.Rect r = Layout.outsideBox(s);
			Component label = switch (t) {
				case LOW -> Texts.raw("1–18"); // literal-ok: number range
				case HIGH -> Texts.raw("19–36"); // literal-ok: number range
				case RED -> Component.translatable("gui.burmaldaholic.roulette.color.red");
				case BLACK -> Component.translatable("gui.burmaldaholic.roulette.color.black");
				case ODD -> Component.translatable("gui.burmaldaholic.roulette.short.odd");
				default -> Component.translatable("gui.burmaldaholic.roulette.short.even");
			};
			int fill = t == BetType.RED ? RED : t == BetType.BLACK ? BLACK : 0;
			box(graphics, r, label, fill, s.covers(winning));
		}
		// Hover: highlight every covered number and the box.
		if (hover != null) {
			if (hover.type().inside()) {
				for (int n : hover.numbers()) {
					Layout.Rect r = Layout.cell(n);
					graphics.fill(LX + r.x(), LY + r.y(), LX + r.x() + r.w(), LY + r.y() + r.h(), 0x60FFFFFF);
				}
			} else {
				Layout.Rect r = Layout.outsideBox(hover);
				graphics.fill(LX + r.x(), LY + r.y(), LX + r.x() + r.w(), LY + r.y() + r.h(), 0x60FFFFFF);
			}
		}
		// Chips: other players (small grey) first, then mine.
		for (Map.Entry<Spot, Long> e : otherBets().entrySet()) {
			int[] c = Layout.center(e.getKey());
			int x = LX + c[0] + 3;
			int y = LY + c[1] - 7;
			graphics.fill(x, y, x + 5, y + 5, 0xFF9E9E9E);
			graphics.outline(x, y, 5, 5, 0xFF303030);
		}
		for (Map.Entry<Spot, Long> e : myBets().entrySet()) {
			int[] c = Layout.center(e.getKey());
			chip(graphics, LX + c[0], LY + c[1], e.getValue(), e.getKey().covers(winning));
		}
	}

	private void box(GuiGraphicsExtractor graphics, Layout.Rect r, Component label, int fill, boolean win) {
		int x = LX + r.x();
		int y = LY + r.y();
		if (fill != 0) {
			graphics.fill(x, y, x + r.w(), y + r.h(), fill);
		}
		graphics.outline(x, y, r.w(), r.h(), win ? GOLD : LINE);
		fitted(graphics, label, x + r.w() / 2, y + r.h() / 2 - 4, r.w() - 4, 0xFFFFFFFF);
	}

	private static int chipColor(long amount) {
		if (amount >= 500) {
			return 0xFF7B2FBE;
		}
		if (amount >= 100) {
			return 0xFF222222;
		}
		if (amount >= 25) {
			return 0xFF2E7D32;
		}
		if (amount >= 5) {
			return 0xFFC62828;
		}
		return 0xFFF5F5F5;
	}

	private void chip(GuiGraphicsExtractor graphics, int cx, int cy, long amount, boolean win) {
		int color = chipColor(amount);
		graphics.fill(cx - 5, cy - 4, cx + 5, cy + 4, color);
		graphics.fill(cx - 4, cy - 5, cx + 4, cy + 5, color);
		graphics.outline(cx - 5, cy - 5, 10, 10, win ? GOLD : 0xFF000000);
		int text = color == 0xFFF5F5F5 ? 0xFF000000 : 0xFFFFFFFF;
		Component label = Texts.number(amount);
		float scale = Math.min(0.5f, 16f / Math.max(1, font.width(label)));
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(scale, scale);
		graphics.text(font, label, -font.width(label) / 2, -4, text, false);
		graphics.pose().popMatrix();
	}

	/** Wheel strip: pockets in wheel order scrolling under a fixed marker, easing out onto the result. */
	private void drawWheel(GuiGraphicsExtractor graphics, float partial) {
		int spinTicks = Math.max(1, state().getIntOr("spin_ticks", 100));
		double left = ticksLeft(partial);
		double progress = left < 0 ? 1 : 1 - left / spinTicks;
		double pos = SpinAnimation.position(result(), progress, 0, 3);
		int cell = 26;
		int visible = 11;
		int cx = imageWidth / 2;
		int top = LY + 20;
		int h = 40;
		int x0 = cx - visible * cell / 2;
		graphics.fill(x0 - 4, top - 6, x0 + visible * cell + 4, top + h + 6, 0xFF3E2723);
		int base = (int) Math.floor(pos);
		double frac = pos - base;
		for (int k = -visible / 2 - 1; k <= visible / 2 + 1; k++) {
			int n = Wheel.ORDER.get(Math.floorMod(base + k, Wheel.POCKETS));
			int x = (int) Math.round(cx + (k - frac) * cell - cell / 2.0);
			int fill = switch (Wheel.color(n)) {
				case RED -> RED;
				case BLACK -> BLACK;
				case GREEN -> GREEN;
			};
			// Clip manually to the window (no scissor: it must work in the translated label pass).
			int clipL = Math.max(x + 1, x0);
			int clipR = Math.min(x + cell - 1, x0 + visible * cell);
			if (clipR <= clipL) {
				continue;
			}
			graphics.fill(clipL, top, clipR, top + h, fill);
			if (clipL == x + 1 && clipR == x + cell - 1) {
				graphics.outline(x + 1, top, cell - 2, h, 0xFFB08D57);
				fitted(graphics, Texts.number(n), x + cell / 2, top + h / 2 - 4, cell - 4, 0xFFFFFFFF);
			}
		}
		// Ball marker.
		graphics.fill(cx - 3, top - 8, cx + 3, top - 2, 0xFFFFFFFF);
		graphics.fill(cx - 1, top - 2, cx + 1, top + 2, 0xFFFFFFFF);
		graphics.outline(cx - cell / 2, top - 1, cell, h + 2, GOLD);
	}

	private void drawTotals(GuiGraphicsExtractor graphics) {
		int y = LY + Layout.HEIGHT + 6;
		Component totalLine = Component.translatable("gui.burmaldaholic.common.total_bet", Texts.chips(total()));
		graphics.text(font, totalLine, PAD, y, 0xFFFFFFFF, true);
		Component limits = limitsLine();
		int right = imageWidth - PAD - font.width(limits);
		int x = PAD + font.width(totalLine) + 8;
		if (multiplayer() && betting() && state().getIntOr("bettors", 0) > 0) {
			Component ready = Component.translatable("gui.burmaldaholic.roulette.ready_count",
				Texts.number(state().getIntOr("ready_count", 0)), Texts.number(state().getIntOr("bettors", 0)));
			if (x + font.width(ready) + 8 <= right) {
				graphics.text(font, ready, x, y, DIM, true);
			}
		}
		if (right > x) {
			graphics.text(font, limits, right, y, DIM, true);
		} else {
			graphics.text(font, limits, PAD, y + 10, DIM, true);
		}
	}

	/** Centered text at (cx, y), scaled down (never truncated) to fit {@code maxWidth}. */
	private void fitted(GuiGraphicsExtractor graphics, Component text, int cx, int y, int maxWidth, int color) {
		int w = font.width(text);
		if (w <= maxWidth) {
			graphics.text(font, text, cx - w / 2, y, color, true);
			return;
		}
		float scale = (float) maxWidth / w;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, y + 4);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, -w / 2, -4, color, true);
		graphics.pose().popMatrix();
	}
}
