package dev.nezo.burmaldaholic.games.baccarat.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.Card;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Baccarat table screen (UI.md §14). Renders the server state only; actions: house {@code bet {box, amount}}
 * (left click adds the selected chip, right click removes the box), {@code clear}, {@code rebet},
 * {@code ready}; chemin de fer {@code punt}, {@code banco}, {@code take_bank}, {@code keep_bank}, {@code pass}.
 *
 * <pre>
 *  Baccarat                              Shoe: 287 cards left   Time left: 14
 *  ┌── PLAYER ──────────────┐   ┌── BANKER ──────────────┐
 *  │ [8♠][K♥]          8    │   │ [9♦][7♣]          6    │
 *  Player wins 8 to 6 · Player: +50
 *  [P.Pair 11:1] [Player 1:1] [Tie 8:1] [Banker 1:1 −5%] [B.Pair 11:1]
 *  Bead plate  (P)(B)(B)(T)…   Player 12 · Banker 15 · Tie 3
 *  Seats: Alex ✓ 120 · YOU 55
 *  [1][5][25][100][500] [Clear] [Rebet] [Ready]            [Leave]
 *  Balance: 12 500                   Min 1 · Max 1 000 · Banker ×20
 * </pre>
 *
 * Every label is measured: boxes and buttons are as wide as their (possibly Russian) text and wrap to a
 * new row when the row is full.
 */
public class BaccaratScreen extends CasinoTableScreen {
	private static final int W = 400, H = 240;
	private static final int PAD = 8;
	private static final int CW = 18, CH = 24;
	private static final long[] CHIPS = {1, 5, 25, 100, 500};
	private static final int GOLD = 0xFFFFD700, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55, RED = 0xFFFF5555, YELLOW = 0xFFFFFF55;
	private static final int BLUE_BEAD = 0xFF3A6BE0, RED_BEAD = 0xFFD03030, GREEN_BEAD = 0xFF2FA64A;
	private static final int CARD_FACE = 0xFFF8F8F0, CARD_EDGE = 0xFF202020, CARD_BACK = 0xFF7A1F1F, CARD_BACK_DOT = 0xFFD8A06C;
	private static final int HAND_TOP = 17, HAND_H = 46;

	private long chip = 5;
	private int ticks;
	private int stateTick;
	private boolean showRules;
	private int boxesTop = 96;
	private int buttonsTop = H - 50;
	private final List<BoxBounds> boxes = new ArrayList<>();
	private @Nullable EditBox amountBox;
	private @Nullable Button takeButton;
	private String amountText = "";

	private record BoxBounds(BetKind kind, int x, int y, int w, int h) {}

	private record Spec(Component label, Button.OnPress onPress, boolean active, @Nullable Component tooltip, int minWidth) {}

	public BaccaratScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType().name().endsWith("player_banked") ? "gui.burmaldaholic.baccarat.title_chemmy"
			: menu.tableType().name().endsWith("high_roller") ? "gui.burmaldaholic.baccarat.title_high_roller" : "gui.burmaldaholic.baccarat.title"), W, H);
		this.titleLabelX = PAD;
		this.titleLabelY = 6;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		stateTick = ticks;
		if (minecraft != null) {
			rebuild();
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		ticks++;
	}

	// ---- state ------------------------------------------------------------------------------------

	private boolean chemmy() {
		return state().contains("chemmy");
	}

	private CompoundTag ch() {
		return state().getCompoundOrEmpty("chemmy");
	}

	private boolean houseCoup() {
		return state().getBooleanOr("house_coup", true);
	}

	private boolean seated() {
		return mySeat() >= 0;
	}

	private long slip(String tag, BetKind k) {
		return state().getCompoundOrEmpty(tag).getLongOr(k.id(), 0);
	}

	private long mySlipTotal() {
		long s = 0;
		for (BetKind k : BetKind.values()) {
			s += slip("slip", k);
		}
		return s;
	}

	private long secondsLeft(String id) {
		long t = state().getCompoundOrEmpty("timers").getLongOr(id, -1);
		return t < 0 ? -1 : (Math.max(0, t - (ticks - stateTick)) + 19) / 20;
	}

	private static List<Card> cards(CompoundTag tag, String key) {
		List<Card> out = new ArrayList<>();
		for (int code : tag.getIntArray(key).orElse(new int[0])) {
			if (code >= 0 && code < 52) {
				out.add(Card.fromCode(code));
			}
		}
		return out;
	}

	/** Ticks elapsed in the reveal (client-interpolated), or MAX when the whole coup is shown. */
	private double revealElapsed() {
		if (!"reveal".equals(phase())) {
			return Double.MAX_VALUE;
		}
		long total = state().getIntOr("reveal_total", 80);
		long left = Math.max(0, state().getLongOr("reveal_left", 0) - (ticks - stateTick));
		return total - left;
	}

	private double step() {
		return Math.max(2, state().getIntOr("reveal_total", 80) / 8.0);
	}

	/** Cards of each hand visible now: deal order P1, B1, P2, B2, P3 (at 5 steps), B3 (at 6.5 steps). */
	private int[] visible(int pCount, int bCount) {
		double e = revealElapsed();
		double s = step();
		int p = 0;
		int b = 0;
		double[] at = {0, s, 2 * s, 3 * s};
		for (int i = 0; i < 4; i++) {
			if (e >= at[i]) {
				if (i % 2 == 0) {
					p++;
				} else {
					b++;
				}
			}
		}
		if (pCount > 2 && e >= 5 * s) {
			p = 3;
		}
		double bankerThirdAt = pCount > 2 ? 6.5 * s : 5 * s;
		if (bCount > 2 && e >= bankerThirdAt) {
			b = 3;
		}
		return new int[] {Math.min(p, pCount), Math.min(b, bCount)};
	}

	private boolean coupFullyShown() {
		return !"reveal".equals(phase());
	}

	// ---- widgets ------------------------------------------------------------------------------------

	private void rebuild() {
		if (amountBox != null) {
			amountText = amountBox.getValue();
		}
		clearWidgets();
		boxes.clear();
		amountBox = null;
		takeButton = null;
		boolean betting = "betting".equals(phase());
		List<Spec> specs = new ArrayList<>();
		if (chemmy() && !houseCoup()) {
			boxesTop = HAND_TOP + HAND_H + 34;
			buildChemmy(specs, betting);
		} else {
			boxesTop = HAND_TOP + HAND_H + 34;
			buildBoxes(betting);
			if (betting || "idle".equals(phase())) {
				long max = Math.max(0, Math.min(maxBet(), balance()));
				for (long c : CHIPS) {
					long value = c;
					Component label = Texts.number(c);
					specs.add(new Spec(chip == c ? label.copy().withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.UNDERLINE) : label,
						b -> {
							chip = value;
							rebuild();
						}, c <= max, null, 22));
				}
				specs.add(new Spec(Component.translatable("gui.burmaldaholic.baccarat.clear_bets"), b -> sendAction("clear"), mySlipTotal() > 0, null, 30));
				specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.rebet"), b -> sendAction("rebet"), state().getBooleanOr("can_rebet", false), null, 30));
				boolean alone = state().getListOrEmpty("seat_list").size() <= 1;
				specs.add(new Spec(Component.translatable(alone ? "gui.burmaldaholic.common.deal" : "gui.burmaldaholic.common.ready"), b -> sendAction("ready"),
					mySlipTotal() > 0 && !state().getBooleanOr("ready", false), null, 36));
			}
		}
		specs.add(new Spec(Component.translatable(showRules ? "gui.burmaldaholic.common.back" : "gui.burmaldaholic.common.rules"), b -> {
			showRules = !showRules;
			rebuild();
		}, true, null, 36));
		specs.add(new Spec(Component.translatable(seated() ? "gui.burmaldaholic.common.leave" : "gui.burmaldaholic.baccarat.sit"),
			b -> sendAction(seated() ? "leave" : "sit"), true, null, 40));
		layout(specs);
	}

	/**
	 * The five betting boxes (UI.md §14), drawn by the screen: name and ratio are separate lines so Russian
	 * fits (box width = max(name, ratio, amount) + 8); the pair boxes move to a second row if the first
	 * would overflow. Left click adds the selected chip, right click removes the box.
	 */
	private void buildBoxes(boolean betting) {
		boolean pairs = state().getBooleanOr("pairs", true);
		List<BetKind> row = new ArrayList<>();
		if (pairs) {
			row.add(BetKind.PLAYER_PAIR);
		}
		row.add(BetKind.PLAYER);
		row.add(BetKind.TIE);
		row.add(BetKind.BANKER);
		if (pairs) {
			row.add(BetKind.BANKER_PAIR);
		}
		int avail = imageWidth - 2 * PAD;
		int total = -4;
		for (BetKind k : row) {
			total += boxWidth(k) + 4;
		}
		List<List<BetKind>> rows = new ArrayList<>();
		if (total <= avail) {
			rows.add(row);
		} else {
			rows.add(row.stream().filter(k -> !k.pair()).toList());
			if (pairs) {
				rows.add(List.of(BetKind.PLAYER_PAIR, BetKind.BANKER_PAIR));
			}
		}
		int y = boxesTop;
		for (List<BetKind> r : rows) {
			int rowW = -4;
			for (BetKind k : r) {
				rowW += boxWidth(k) + 4;
			}
			int x = (imageWidth - rowW) / 2;
			for (BetKind k : r) {
				int w = boxWidth(k);
				boxes.add(new BoxBounds(k, x, y, w, BOX_H));
				x += w + 4;
			}
			y += BOX_H + 3;
		}
		buttonsMinTop = y;
	}

	private static final int BOX_H = 32;
	private int buttonsMinTop;

	private int boxWidth(BetKind k) {
		int w = Math.max(font.width(boxName(k)), font.width(boxRatio(k)));
		w = Math.max(w, font.width(boxAmount(k)));
		return Math.max(50, w + 8);
	}

	private Component boxName(BetKind k) {
		return Component.translatable("gui.burmaldaholic.baccarat." + k.id());
	}

	private Component boxRatio(BetKind k) {
		return switch (k) {
			case PLAYER -> Texts.raw("1:1");
			case BANKER -> Component.translatable("gui.burmaldaholic.baccarat.ratio_banker", Texts.raw(pct(state().getIntOr("commission_bp", 500))));
			case TIE -> Texts.raw(state().getIntOr("tie_pays", 8) + ":1");
			case PLAYER_PAIR, BANKER_PAIR -> Texts.raw(state().getIntOr("pair_pays", 11) + ":1");
		};
	}

	/** "50" (yours) and "+120" (the rest of the table). */
	private Component boxAmount(BetKind k) {
		long mine = slip("slip", k);
		long others = slip("others", k);
		MutableComponent out = Component.empty();
		if (mine > 0) {
			out.append(Texts.number(mine).withStyle(net.minecraft.ChatFormatting.GOLD));
		}
		if (others > 0) {
			out.append(Texts.raw(mine > 0 ? " +" : "+").withStyle(net.minecraft.ChatFormatting.GRAY))
				.append(Texts.number(others).withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		return out;
	}

	private Component boxTooltip(BetKind k) {
		String commission = pct(state().getIntOr("commission_bp", 500));
		return switch (k) {
			case BANKER -> Component.translatable("gui.burmaldaholic.baccarat.bet.banker.tooltip", Texts.raw(commission)).append(" ")
				.append(Component.translatable("gui.burmaldaholic.baccarat.limits_banker", Texts.number(state().getLongOr("step", 20))));
			case TIE -> Component.translatable("gui.burmaldaholic.baccarat.bet.tie.tooltip");
			case PLAYER_PAIR, BANKER_PAIR -> Component.translatable("gui.burmaldaholic.baccarat.bet.pair.tooltip");
			case PLAYER -> Component.translatable("gui.burmaldaholic.baccarat.rules.2");
		};
	}

	private static int boxColor(BetKind k) {
		return switch (k) {
			case PLAYER -> 0xFF28437F;
			case BANKER -> 0xFF7F2828;
			case TIE -> 0xFF22603A;
			case PLAYER_PAIR -> 0xFF1D3159;
			case BANKER_PAIR -> 0xFF591D1D;
		};
	}

	private void drawBoxes(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean betting = "betting".equals(phase());
		for (BoxBounds b : boxes) {
			int x = leftPos + b.x;
			int y = topPos + b.y;
			boolean hover = mouseX >= x && mouseX < x + b.w && mouseY >= y && mouseY < y + b.h;
			g.fill(x - 1, y - 1, x + b.w + 1, y + b.h + 1, hover && betting ? GOLD : 0xFF0A0A0A);
			g.fill(x, y, x + b.w, y + b.h, betting ? boxColor(b.kind) : (boxColor(b.kind) & 0x00FFFFFF) | 0x90000000);
			Component name = boxName(b.kind);
			Component ratio = boxRatio(b.kind);
			Component amount = boxAmount(b.kind);
			g.text(font, name, x + (b.w - font.width(name)) / 2, y + 2, TEXT, true);
			g.text(font, ratio, x + (b.w - font.width(ratio)) / 2, y + 12, 0xFFDDDDDD, false);
			g.text(font, amount, x + (b.w - font.width(amount)) / 2, y + 22, GOLD, true);
			if (hover) {
				g.setTooltipForNextFrame(font.split(boxTooltip(b.kind), 220), mouseX, mouseY);
			}
		}
	}

	private static String pct(int bp) {
		String s = java.math.BigDecimal.valueOf(bp, 2).stripTrailingZeros().toPlainString();
		return s;
	}

	private void addChip(BetKind k) {
		CompoundTag args = new CompoundTag();
		args.putString("box", k.id());
		long amount = chip;
		if (k == BetKind.BANKER) {
			long step = state().getLongOr("step", 20);
			long current = slip("slip", BetKind.BANKER);
			long add = chip >= step ? chip : step; // the server snaps larger chips down to the step (…baccarat.snapped)
			long want = Math.max(current + add, state().getLongOr("banker_min", step));
			amount = want - current;
		}
		args.putLong("amount", amount);
		sendAction("bet", args);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if ((event.button() == 0 || event.button() == 1) && "betting".equals(phase())) {
			double mx = event.x() - leftPos;
			double my = event.y() - topPos;
			for (BoxBounds b : boxes) {
				if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
					if (event.button() == 0) {
						addChip(b.kind);
					} else if (slip("slip", b.kind) > 0) {
						CompoundTag args = new CompoundTag();
						args.putString("box", b.kind.id());
						sendAction("unbet", args);
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void buildChemmy(List<Spec> specs, boolean betting) {
		CompoundTag c = ch();
		buttonsMinTop = boxesTop + 46;
		String phase = phase();
		if ("bank_offer".equals(phase) && c.getBooleanOr("offer_you", false)) {
			if (c.getBooleanOr("offer_keep", false)) {
				specs.add(new Spec(Component.translatable("gui.burmaldaholic.baccarat.chemmy.keep", Texts.number(c.getLongOr("bank", 0))),
					b -> sendAction("keep_bank"), true, null, 40));
				specs.add(new Spec(Component.translatable("gui.burmaldaholic.baccarat.chemmy.pass_bank"), b -> sendAction("pass"), true, null, 40));
			} else {
				amountBox = new EditBox(font, leftPos + PAD, topPos + boxesTop + 22, 70, 20, Component.translatable("gui.burmaldaholic.common.amount"));
				amountBox.setMaxLength(12);
				amountBox.setValue(amountText.isEmpty() ? Long.toString(c.getLongOr("default_bank", 20)) : amountText);
				amountBox.setResponder(v -> {
					if (!v.chars().allMatch(Character::isDigit)) {
						amountBox.setValue(v.replaceAll("\\D", ""));
						return;
					}
					if (takeButton != null) {
						takeButton.setMessage(Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.raw(v.isEmpty() ? "0" : v)));
					}
				});
				addRenderableWidget(amountBox);
				Component take = Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.raw(amountText.isEmpty()
					? Long.toString(c.getLongOr("default_bank", 20)) : amountText));
				Button tb = button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.take_other"), PAD + 76, boxesTop + 22, 40, b -> takeBank());
				tb.setMessage(take);
				takeButton = tb;
				tb.setWidth(Math.max(80, font.width(Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.raw("000000000"))) + 8));
				Button pass = button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.pass"), PAD + 80 + tb.getWidth(), boxesTop + 22, 40, b -> sendAction("pass"));
				pass.active = true;
			}
		} else if ("waiting".equals(phase) && seated()) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.number(c.getLongOr("default_bank", 20))),
				b -> {
					CompoundTag args = new CompoundTag();
					args.putLong("amount", c.getLongOr("default_bank", 20));
					sendAction("take_bank", args);
				}, true, null, 40));
		} else if (betting && seated() && !c.getBooleanOr("you_bank", false)) {
			long max = Math.max(0, Math.min(maxBet(), balance()));
			for (long v : CHIPS) {
				long value = v;
				Component label = Texts.number(v);
				specs.add(new Spec(chip == v ? label.copy().withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.UNDERLINE) : label,
					b -> {
						chip = value;
						rebuild();
					}, v <= max, null, 22));
			}
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.baccarat.chemmy.bet_player"), b -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", chip);
				sendAction("punt", args);
			}, c.getLongOr("open", 0) > 0 && c.getStringOr("banco", "").isEmpty(), null, 40));
			long cov = c.getLongOr("coverage", 0);
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.baccarat.chemmy.banco", Texts.number(cov)), b -> sendAction("banco"),
				c.getStringOr("banco", "").isEmpty() && cov > 0 && balance() + c.getLongOr("my_punt", 0) >= cov && c.getLongOr("my_max", 0) >= cov,
				Component.translatable("gui.burmaldaholic.baccarat.chemmy.banco.tooltip"), 40));
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.baccarat.clear_bets"), b -> sendAction("clear"), c.getLongOr("my_punt", 0) > 0
				&& c.getStringOr("banco", "").isEmpty(), null, 30));
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.ready"), b -> sendAction("ready"),
				c.getLongOr("my_punt", 0) > 0 && !state().getBooleanOr("ready", false), null, 36));
		}
	}

	private void takeBank() {
		long amount;
		try {
			amount = Long.parseLong(amountBox != null ? amountBox.getValue() : amountText);
		} catch (NumberFormatException e) {
			amount = ch().getLongOr("default_bank", 20);
		}
		amountText = "";
		CompoundTag args = new CompoundTag();
		args.putLong("amount", amount);
		sendAction("take_bank", args);
	}

	private void layout(List<Spec> specs) {
		int right = imageWidth - PAD;
		List<List<Spec>> rows = new ArrayList<>();
		List<Spec> row = new ArrayList<>();
		int x = PAD;
		for (Spec s : specs) {
			int w = width(s);
			if (x + w > right && !row.isEmpty()) {
				rows.add(row);
				row = new ArrayList<>();
				x = PAD;
			}
			row.add(s);
			x += w + 3;
		}
		if (!row.isEmpty()) {
			rows.add(row);
		}
		buttonsTop = imageHeight - 24 - rows.size() * 22;
		int y = buttonsTop;
		for (List<Spec> r : rows) {
			x = PAD;
			for (Spec s : r) {
				int w = width(s);
				Button b = button(s.label(), x, y, w, s.onPress());
				b.active = s.active();
				if (s.tooltip() != null) {
					b.setTooltip(Tooltip.create(s.tooltip()));
				}
				x += w + 3;
			}
			y += 22;
		}
	}

	private int width(Spec s) {
		return Math.max(s.minWidth(), font.width(s.label()) + 8);
	}

	// ---- rendering ------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		if (state().getBooleanOr("high_roller", false)) {
			g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, GOLD);
			g.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, GOLD);
		}
		drawBoxes(g, mouseX, mouseY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		drawHeader(g);
		drawHands(g);
		int y = HAND_TOP + HAND_H + 2;
		y = drawResultLines(g, y);
		if (chemmy() && !houseCoup()) {
			drawChemmyInfo(g, boxesTop);
		}
		int infoTop = Math.max(buttonsMinTop, boxesTop + 24) + 1;
		if (showRules) {
			drawRules(g, infoTop);
		} else {
			drawTableInfo(g, infoTop);
		}
		drawInfoLine(g);
		super.extractLabels(g, xm, ym);
	}

	private void drawHeader(GuiGraphicsExtractor g) {
		int x = imageWidth - PAD;
		long sec = -1;
		for (String id : new String[] {"bet", "offer", "idle"}) {
			sec = secondsLeft(id);
			if (sec >= 0) {
				break;
			}
		}
		if (sec >= 0) {
			Component t = Component.translatable("gui.burmaldaholic.common.timer", Texts.plural("unit.burmaldaholic.second", sec));
			x -= font.width(t);
			g.text(font, t, x, 6, sec <= 5 ? RED : TEXT, true);
			x -= 10;
		}
		Component shoe = Texts.plural("gui.burmaldaholic.baccarat.shoe_left", state().getIntOr("shoe_left", 416));
		int titleEnd = PAD + font.width(title) + 10;
		if (x - font.width(shoe) > titleEnd) {
			g.text(font, shoe, x - font.width(shoe), 6, GRAY, true);
		}
	}

	private void drawHands(GuiGraphicsExtractor g) {
		List<Card> p = cards(state(), "player_cards");
		List<Card> b = cards(state(), "banker_cards");
		int[] vis = visible(p.size(), b.size());
		int half = (imageWidth - 2 * PAD - 8) / 2;
		drawHand(g, PAD, half, Component.translatable("gui.burmaldaholic.baccarat.player"), p.subList(0, Math.min(vis[0], p.size())), BLUE_BEAD,
			p.size(), b, vis, true);
		drawHand(g, PAD + half + 8, half, Component.translatable("gui.burmaldaholic.baccarat.banker"), b.subList(0, Math.min(vis[1], b.size())), RED_BEAD,
			b.size(), p, vis, false);
	}

	private void drawHand(GuiGraphicsExtractor g, int x, int w, Component label, List<Card> shown, int color, int totalCards,
			List<Card> other, int[] vis, boolean player) {
		int y = HAND_TOP;
		g.fill(x, y, x + w, y + HAND_H, 0x40000000);
		g.fill(x, y, x + w, y + 1, color);
		g.text(font, label, x + 4, y + 3, TEXT, true);
		int cx = x + 4;
		int cy = y + 12;
		for (Card c : shown) {
			drawCard(g, cx, cy, c);
			cx += CW + 2;
		}
		int dealt = player ? vis[0] : vis[1];
		if ("reveal".equals(phase())) {
			for (int i = dealt; i < Math.min(2, totalCards); i++) {
				drawBack(g, cx, cy);
				cx += CW + 2;
			}
		}
		if (!shown.isEmpty()) {
			Component total = Texts.number(BaccaratRules.total(shown));
			g.text(font, total, x + w - 6 - font.width(total), cy + 8, GOLD, true);
		}
		// status under the cards: natural / draws / stands
		Component status = handStatus(shown, totalCards, other, vis, player);
		if (status != null) {
			List<FormattedCharSequence> lines = font.split(status, w - 8);
			if (!lines.isEmpty()) {
				g.text(font, lines.getFirst(), x + 4, y + HAND_H - 9, YELLOW, false);
			}
		}
	}

	private @Nullable Component handStatus(List<Card> shown, int totalCards, List<Card> other, int[] vis, boolean player) {
		if (shown.size() < 2 || vis[0] < 2 || vis[1] < 2) {
			return null;
		}
		int two = BaccaratRules.total(shown.subList(0, 2));
		if (BaccaratRules.isNatural(two)) {
			return Component.translatable("gui.burmaldaholic.baccarat.natural", Texts.number(two));
		}
		if (totalCards > 2) {
			return Component.translatable(player ? "gui.burmaldaholic.baccarat.player_draws" : "gui.burmaldaholic.baccarat.banker_draws");
		}
		List<Card> o2 = other.size() >= 2 ? other.subList(0, 2) : other;
		if (o2.size() == 2 && BaccaratRules.isNatural(BaccaratRules.total(o2))) {
			return null;
		}
		if (coupFullyShown() || (!player && vis[0] >= Math.min(3, other.size()))) {
			return Component.translatable(player ? "gui.burmaldaholic.baccarat.player_stands" : "gui.burmaldaholic.baccarat.banker_stands", Texts.number(two));
		}
		return null;
	}

	/** Result banner + the viewer's per-bet lines (after the reveal). */
	private int drawResultLines(GuiGraphicsExtractor g, int y) {
		List<Card> p = cards(state(), "player_cards");
		List<Card> b = cards(state(), "banker_cards");
		String phase = phase();
		if (state().getBooleanOr("waiting_next", false)) {
			g.centeredText(font, Component.translatable("gui.burmaldaholic.baccarat.waiting_next"), imageWidth / 2, y + 2, GRAY);
			return y + 12;
		}
		if ("shuffle".equals(phase)) {
			MutableComponent line = Component.translatable("gui.burmaldaholic.baccarat.shuffling");
			int burned = state().getIntOr("burned", 0);
			if (burned > 0) {
				line.append(" ").append(Texts.plural("gui.burmaldaholic.baccarat.burned", burned));
			}
			g.centeredText(font, line, imageWidth / 2, y + 2, YELLOW);
			return y + 12;
		}
		if ("no_more_bets".equals(phase)) {
			g.centeredText(font, Component.translatable("gui.burmaldaholic.baccarat.no_more_bets"), imageWidth / 2, y + 2, YELLOW);
			return y + 12;
		}
		if (p.size() < 2 || b.size() < 2 || !coupFullyShown()) {
			if ("betting".equals(phase) && (houseCoup() || !chemmy())) {
				long sec = secondsLeft("bet");
				Component line = sec >= 0 ? Component.translatable("gui.burmaldaholic.baccarat.bets_close_in", Texts.plural("unit.burmaldaholic.second_acc", sec))
					: Component.translatable("gui.burmaldaholic.baccarat.place_bets");
				g.centeredText(font, line, imageWidth / 2, y + 2, YELLOW);
			}
			return y + 12;
		}
		int pt = BaccaratRules.total(p);
		int bt = BaccaratRules.total(b);
		MutableComponent banner = pt > bt ? Component.translatable("gui.burmaldaholic.baccarat.result.player", Texts.number(pt), Texts.number(bt))
			: bt > pt ? Component.translatable("gui.burmaldaholic.baccarat.result.banker", Texts.number(bt), Texts.number(pt))
			: Component.translatable("gui.burmaldaholic.baccarat.result.tie", Texts.number(pt));
		if (p.get(0).rank() == p.get(1).rank()) {
			banner.append(" · ").append(Component.translatable("gui.burmaldaholic.baccarat.result.pair_player"));
		}
		if (b.get(0).rank() == b.get(1).rank()) {
			banner.append(" · ").append(Component.translatable("gui.burmaldaholic.baccarat.result.pair_banker"));
		}
		g.centeredText(font, banner, imageWidth / 2, y + 2, pt > bt ? 0xFF8FB4FF : bt > pt ? 0xFFFF9A9A : GREEN);
		y += 12;
		// my lines
		List<Component> lines = new ArrayList<>();
		CompoundTag res = state().getCompoundOrEmpty("my_result");
		CompoundTag rets = res.getCompoundOrEmpty("returns");
		for (BetKind k : BetKind.values()) {
			long stake = res.getLongOr(k.id(), 0);
			if (stake <= 0) {
				continue;
			}
			long ret = rets.getLongOr(k.id(), 0);
			Component name = Component.translatable("gui.burmaldaholic.baccarat." + k.id());
			lines.add(ret > stake ? Component.translatable("gui.burmaldaholic.baccarat.line.win", name, Texts.number(ret - stake))
				: ret == stake ? Component.translatable("gui.burmaldaholic.baccarat.line.push", name)
				: Component.translatable("gui.burmaldaholic.baccarat.line.lose", name, Texts.number(stake)));
		}
		long commission = res.getLongOr("commission", 0);
		if (commission > 0) {
			lines.add(Component.translatable("gui.burmaldaholic.baccarat.line.commission", Texts.number(commission)));
		}
		if (state().contains("my_punt")) {
			long stake = state().getLongOr("my_punt", 0);
			long ret = state().getLongOr("my_punt_return", 0);
			Component name = Component.translatable("gui.burmaldaholic.baccarat.player");
			lines.add(ret > stake ? Component.translatable("gui.burmaldaholic.baccarat.line.win", name, Texts.number(ret - stake))
				: ret == stake ? Component.translatable("gui.burmaldaholic.baccarat.line.push", name)
				: Component.translatable("gui.burmaldaholic.baccarat.line.lose", name, Texts.number(stake)));
		}
		if (state().contains("my_bank_delta")) {
			long d = state().getLongOr("my_bank_delta", 0);
			Component name = Component.translatable("gui.burmaldaholic.baccarat.banker");
			lines.add(d > 0 ? Component.translatable("gui.burmaldaholic.baccarat.line.win", name, Texts.number(d))
				: d == 0 ? Component.translatable("gui.burmaldaholic.baccarat.line.push", name)
				: Component.translatable("gui.burmaldaholic.baccarat.line.lose", name, Texts.number(-d)));
			long rake = state().getLongOr("my_bank_rake", 0);
			if (rake > 0) {
				lines.add(Component.translatable("gui.burmaldaholic.baccarat.line.commission", Texts.number(rake)));
			}
		}
		if (!lines.isEmpty()) {
			MutableComponent joined = Component.empty();
			for (int i = 0; i < lines.size(); i++) {
				if (i > 0) {
					joined.append("   ");
				}
				joined.append(lines.get(i));
			}
			for (FormattedCharSequence l : font.split(joined, imageWidth - 2 * PAD)) {
				if (y + 9 > boxesTop) {
					break;
				}
				g.text(font, l, PAD, y, TEXT, true);
				y += 10;
			}
		}
		return y;
	}

	private void drawChemmyInfo(GuiGraphicsExtractor g, int y) {
		CompoundTag c = ch();
		List<Component> parts = new ArrayList<>();
		String phase = phase();
		if (c.getBooleanOr("held", false)) {
			parts.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.bank", Texts.number(c.getLongOr("bank", 0))));
			parts.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.coverage", Texts.number(c.getLongOr("coverage", 0)), Texts.number(c.getLongOr("open", 0))));
			parts.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.banker_is", Texts.raw(c.getStringOr("banker", ""))));
		}
		Component status = null;
		if ("bank_offer".equals(phase)) {
			status = c.getBooleanOr("offer_you", false) ? Component.translatable("gui.burmaldaholic.baccarat.chemmy.offer")
				: Component.translatable("gui.burmaldaholic.baccarat.chemmy.waiting_offer", Texts.raw(c.getStringOr("candidate", "")));
		} else if ("betting".equals(phase) && c.getBooleanOr("you_bank", false)) {
			status = Component.translatable("gui.burmaldaholic.baccarat.chemmy.you_bank");
		} else if ("betting".equals(phase)) {
			String banco = c.getStringOr("banco", "");
			status = banco.isEmpty() ? Component.translatable("gui.burmaldaholic.baccarat.place_bets")
				: Component.translatable("msg.burmaldaholic.baccarat.chemmy.banco_called", Texts.raw(banco));
		} else if ("waiting".equals(phase)) {
			status = Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.number(c.getLongOr("min_bank", 20)));
		}
		MutableComponent line = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				line.append(" · ");
			}
			line.append(parts.get(i));
		}
		int yy = y;
		if (!parts.isEmpty()) {
			for (FormattedCharSequence l : font.split(line, imageWidth - 2 * PAD - 50)) {
				g.text(font, l, PAD, yy, GOLD, true);
				yy += 10;
			}
		}
		if (status != null) {
			g.text(font, status, PAD, Math.min(yy, boxesTop + 12), YELLOW, true);
		}
		// punts against the bank
		ListTag punts = c.getListOrEmpty("punts");
		int x = PAD;
		int py = boxesTop + 24;
		if ("bank_offer".equals(phase) && c.getBooleanOr("offer_you", false)) {
			if (!c.getBooleanOr("offer_keep", false)) {
				Component hint = Component.translatable("gui.burmaldaholic.baccarat.chemmy.bank_amount", Texts.number(c.getLongOr("min_bank", 20)));
				g.text(font, hint, PAD, boxesTop + 12, GRAY, false);
			}
			return;
		}
		for (int i = 0; i < punts.size(); i++) {
			CompoundTag pt = punts.getCompoundOrEmpty(i);
			Component who = pt.getBooleanOr("you", false) ? Component.translatable("gui.burmaldaholic.common.you") : Texts.raw(pt.getStringOr("name", ""));
			Component t = Component.translatable("gui.burmaldaholic.baccarat.bet_line", who, Texts.number(pt.getLongOr("amount", 0)));
			if (x + font.width(t) > imageWidth - PAD) {
				break;
			}
			g.text(font, t, x, py, TEXT, true);
			x += font.width(t) + 10;
		}
	}

	/**
	 * Bottom-left: the bead plate (up to 6 rows, columns fill top to bottom; letter + colour, pair dots —
	 * never colour alone, UI.md §14). Bottom-right: counts, readiness and the seats (wrapped).
	 */
	private void drawTableInfo(GuiGraphicsExtractor g, int y) {
		int bottom = buttonsTop - 3;
		int[] beads = state().getIntArray("beads").orElse(new int[0]);
		int cell = 9;
		int rows = Math.max(1, Math.min(6, (bottom - y - 10) / cell));
		int leftW = 200;
		Component label = Component.translatable("gui.burmaldaholic.baccarat.history");
		g.text(font, label, PAD, y, GRAY, false);
		int x0 = PAD;
		int y0 = y + 10;
		int maxCols = Math.max(1, (leftW - 4) / cell);
		int start = Math.max(0, beads.length - maxCols * rows);
		start += (rows - start % rows) % rows;
		int players = 0, bankers = 0, ties = 0;
		for (int b : beads) {
			switch (b & 3) {
				case 0 -> players++;
				case 1 -> bankers++;
				default -> ties++;
			}
		}
		for (int i = Math.min(start, beads.length); i < beads.length; i++) {
			int idx = i - start;
			int bx = x0 + (idx / rows) * cell;
			int by = y0 + (idx % rows) * cell;
			int b = beads[i];
			int side = b & 3;
			int color = side == 0 ? BLUE_BEAD : side == 1 ? RED_BEAD : GREEN_BEAD;
			g.fill(bx + 1, by, bx + cell - 1, by + cell, color);
			g.fill(bx, by + 1, bx + cell, by + cell - 1, color);
			String letterKey = side == 0 ? "gui.burmaldaholic.baccarat.bead.player" : side == 1 ? "gui.burmaldaholic.baccarat.bead.banker" : "gui.burmaldaholic.baccarat.bead.tie";
			Component letter = Component.translatable(letterKey);
			g.text(font, letter, bx + (cell - font.width(letter)) / 2 + 1, by + 1, 0xFFFFFFFF, false);
			if ((b & 4) != 0) {
				g.fill(bx, by + cell - 2, bx + 2, by + cell, 0xFFFFFFFF);
			}
			if ((b & 8) != 0) {
				g.fill(bx + cell - 2, by, bx + cell, by + 2, 0xFFFFFFFF);
			}
		}
		// right column
		int rx = PAD + leftW + 6;
		int rw = imageWidth - PAD - rx;
		int ry = y;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.burmaldaholic.baccarat.stats", Texts.number(players), Texts.number(bankers), Texts.number(ties)));
		if (state().getIntOr("bettors", 0) > 1) {
			lines.add(Component.translatable("gui.burmaldaholic.baccarat.ready_count",
				Texts.number(state().getIntOr("ready_count", 0)), Texts.number(state().getIntOr("bettors", 0))));
		}
		ListTag seats = state().getListOrEmpty("seat_list");
		MutableComponent seatLine = Component.translatable("gui.burmaldaholic.baccarat.seated", Texts.number(seats.size()));
		for (int i = 0; i < seats.size(); i++) {
			CompoundTag st = seats.getCompoundOrEmpty(i);
			seatLine.append(i == 0 ? " " : " · ");
			Component who = st.getBooleanOr("you", false) ? Component.translatable("gui.burmaldaholic.common.you") : Texts.raw(st.getStringOr("name", ""));
			seatLine.append(who);
			if (st.getBooleanOr("banker", false)) {
				seatLine.append(" ").append(Component.translatable("gui.burmaldaholic.baccarat.chemmy.bank", Texts.number(ch().getLongOr("bank", 0)))
					.withStyle(net.minecraft.ChatFormatting.GOLD));
			}
			if (st.getBooleanOr("ready", false)) {
				seatLine.append(" ").append(Texts.raw("✓"));
			}
			long stake = st.getLongOr("stake", 0);
			if (stake > 0) {
				seatLine.append(" ").append(Texts.number(stake));
			}
		}
		lines.add(seatLine);
		for (Component c : lines) {
			for (FormattedCharSequence l : font.split(c, rw)) {
				if (ry + 9 > bottom) {
					return;
				}
				g.text(font, l, rx, ry, GRAY, false);
				ry += 10;
			}
		}
	}

	private void drawRules(GuiGraphicsExtractor g, int y) {
		List<Component> rules = new ArrayList<>();
		String commission = pct(state().getIntOr("commission_bp", 500));
		if (chemmy() && !houseCoup()) {
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.rules.1"));
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.rules.2"));
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.rules.3", Texts.raw(pct(ch().getIntOr("rake_bp", 500)))));
		} else {
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.1", Texts.number(state().getIntOr("decks", 8)), Texts.raw(commission),
				Texts.number(state().getIntOr("tie_pays", 8))));
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.4"));
			if (state().getBooleanOr("pairs", true)) {
				rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.5", Texts.number(state().getIntOr("pair_pays", 11))));
			}
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.banker_step", Texts.number(state().getLongOr("step", 20))));
		}
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.2"));
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.3"));
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.natural"));
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.player"));
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.banker_no_draw"));
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.banker_title"));
		MutableComponent table = Component.empty();
		String[] rows = {"banker_0_2", "banker_3", "banker_4", "banker_5", "banker_6", "banker_7"};
		for (int i = 0; i < rows.length; i++) {
			if (i > 0) {
				table.append(" · ");
			}
			table.append(Component.translatable("gui.burmaldaholic.baccarat.rules." + rows[i]));
		}
		rules.add(table);
		int limit = buttonsTop - 2;
		for (Component r : rules) {
			for (FormattedCharSequence l : font.split(r, imageWidth - 2 * PAD)) {
				if (y + 9 > limit) {
					return;
				}
				g.text(font, l, PAD, y, GRAY, false);
				y += 9;
			}
		}
	}

	private void drawCard(GuiGraphicsExtractor g, int x, int y, Card c) {
		g.fill(x, y, x + CW, y + CH, CARD_EDGE);
		g.fill(x + 1, y + 1, x + CW - 1, y + CH - 1, CARD_FACE);
		int color = c.isRed() ? 0xFFCC0000 : 0xFF111111;
		g.text(font, c.rankLabel(), x + 2, y + 2, color, false);
		g.text(font, c.suitSymbol(), x + CW - 2 - font.width(c.suitSymbol()), y + CH - 10, color, false);
	}

	private void drawBack(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + CW, y + CH, CARD_EDGE);
		g.fill(x + 1, y + 1, x + CW - 1, y + CH - 1, CARD_BACK);
		for (int dy = 3; dy < CH - 3; dy += 4) {
			for (int dx = 3 + (dy / 4 % 2) * 2; dx < CW - 3; dx += 4) {
				g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, CARD_BACK_DOT);
			}
		}
	}

	private void drawInfoLine(GuiGraphicsExtractor g) {
		int y = imageHeight - 21;
		Component bal = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		g.text(font, bal, PAD, y, GOLD, true);
		MutableComponent limits = limitsLine().copy();
		if (!chemmy() || houseCoup()) {
			limits.append(" · ").append(Component.translatable("gui.burmaldaholic.baccarat.limits_banker", Texts.number(state().getLongOr("step", 20))));
		}
		int avail = imageWidth - 2 * PAD - font.width(bal) - 10;
		List<FormattedCharSequence> lines = font.split(limits, avail);
		int ly = lines.size() > 1 ? y - 4 : y;
		for (FormattedCharSequence l : lines) {
			g.text(font, l, imageWidth - PAD - font.width(l), ly, GRAY, true);
			ly += 9;
		}
	}
}
