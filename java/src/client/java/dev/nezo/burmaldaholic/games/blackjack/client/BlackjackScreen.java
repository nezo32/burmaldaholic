package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BlackjackConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Hands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Blackjack table screen (UI.md §4). Renders the server state only and sends actions:
 * {@code bet {amount}}, {@code hit|stand|double|split|surrender}, {@code insurance {amount 0..bet/2}},
 * {@code even_money {take}}, core's {@code sit|leave}.
 *
 * <pre>
 *  Blackjack                                         Time left: 18 seconds
 *  Dealer   [A♠][▒]  11
 *  ─────────────────────────────────────────────────────────────
 *  Your turn · Auto-Stand in 18 seconds
 *  You      [8♦][8♠] 16  ◀                     Bet: 100
 *  Alex     10♥ 7♣ 17                          Bet: 50
 *  [Hit] [Stand] [Double] [Split]                         [Leave table]
 *  Balance: 12 500                                   Min 1 · Max 1 000
 * </pre>
 *
 * Buttons flow into rows sized to their translated label and wrap (Russian ≈ 1.45× English);
 * the viewer's own hands are drawn as big cards, other seats as compact card chips.
 */
public class BlackjackScreen extends CasinoTableScreen {
	private static final int W = 320, H = 232;
	private static final int PAD = 8;
	private static final int CW = 18, CH = 24;
	private static final int ROW = 11;
	private static final int BIG_ROW = CH + 6;
	private static final int[] CHIPS = {1, 5, 25, 100, 500};
	private static final int GOLD = 0xFFFFD700, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55, RED = 0xFFFF5555, YELLOW = 0xFFFFFF55;
	private static final int CARD_FACE = 0xFFF8F8F0, CARD_EDGE = 0xFF202020, CARD_BACK = 0xFF2A3F8F, CARD_BACK_DOT = 0xFF6C82D8;

	private long pendingBet;
	private @Nullable EditBox insuranceBox;
	private String insuranceText = "";
	private @Nullable Button insureButton;
	private int buttonsTop = H - 30;
	private int ticks;
	private int stateTick;

	private record Spec(Component label, Button.OnPress onPress, boolean active, @Nullable Component tooltip, int minWidth) {}

	public BlackjackScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType().name().endsWith("high_roller")
			? "gui.burmaldaholic.blackjack.title_high_roller" : "gui.burmaldaholic.blackjack.title"), W, H);
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

	// ---- state helpers -------------------------------------------------------------------------

	private boolean highRoller() {
		return state().getBooleanOr("high_roller", false);
	}

	/** Seconds left on a timer, counting down locally between server syncs. */
	private long secondsLeft(String id) {
		long t = state().getCompoundOrEmpty("timers").getLongOr(id, -1);
		if (t < 0) {
			return -1;
		}
		return (Math.max(0, t - (ticks - stateTick)) + 19) / 20;
	}

	private boolean seated() {
		return mySeat() >= 0;
	}

	private CompoundTag myPlayer() {
		ListTag players = state().getListOrEmpty("players");
		for (int i = 0; i < players.size(); i++) {
			CompoundTag p = players.getCompoundOrEmpty(i);
			if (p.getBooleanOr("you", false)) {
				return p;
			}
		}
		return null;
	}

	private boolean myTurn() {
		CompoundTag me = myPlayer();
		return me != null && "turns".equals(phase()) && me.getIntOr("seat", -2) == state().getIntOr("current_seat", -1);
	}

	private List<String> legal() {
		List<String> out = new ArrayList<>();
		ListTag l = state().getListOrEmpty("legal");
		for (int i = 0; i < l.size(); i++) {
			out.add(l.getStringOr(i, ""));
		}
		return out;
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

	private long maxAffordable() {
		return Math.max(0, Math.min(maxBet(), balance()));
	}

	// ---- widgets ---------------------------------------------------------------------------------

	private void rebuild() {
		if (insuranceBox != null) {
			insuranceText = insuranceBox.getValue();
		}
		clearWidgets();
		insuranceBox = null;
		insureButton = null;
		List<Spec> specs = new ArrayList<>();
		String phase = phase();
		String offer = state().getStringOr("offer", "");

		if (("betting".equals(phase) || "idle".equals(phase)) && !state().getBooleanOr("bet_placed", false)) {
			long max = maxAffordable();
			for (int chip : CHIPS) {
				specs.add(new Spec(Texts.number(chip), b -> addChip(chip), pendingBet + chip <= max, null, 24));
			}
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.clear"), b -> setPending(0), pendingBet > 0, null, 30));
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.max"), b -> setPending(maxAffordable()), max >= minBet(), null, 30));
			long last = state().getLongOr("last_bet", 0);
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.rebet"), b -> setPending(last), last > 0 && last <= max, null, 30));
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.deal"), b -> deal(),
				pendingBet >= minBet() && pendingBet <= max, null, 40));
		} else if ("insurance".equals(offer)) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.blackjack.insure", Texts.number(insuranceAmount())),
				b -> insure(insuranceAmount()), true, null, 40));
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.blackjack.no_insurance"), b -> insure(0), true, null, 40));
		} else if ("even_money".equals(offer)) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.blackjack.even_money"), b -> evenMoney(true), true, null, 40));
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.blackjack.decline_even_money"), b -> evenMoney(false), true, null, 40));
		} else if (myTurn()) {
			List<String> legal = legal();
			List<String> actions = new ArrayList<>(List.of("hit", "stand", "double", "split"));
			if (CasinoConfig.blackjack().lateSurrender) {
				actions.add("surrender");
			}
			for (String a : actions) {
				specs.add(new Spec(Component.translatable("gui.burmaldaholic.blackjack." + a), b -> sendAction(a), legal.contains(a),
					Component.translatable("gui.burmaldaholic.blackjack." + a + ".tooltip"), 40));
			}
		}
		if (!seated()) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.blackjack.sit"), b -> sendAction("sit"), true, null, 40));
		} else {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.leave"), b -> sendAction("leave"), true, null, 40));
		}
		layout(specs, "insurance".equals(offer));
	}

	/** Packs the buttons into rows (wrapping when a row is full) anchored above the info line. */
	private void layout(List<Spec> specs, boolean withInsuranceBox) {
		int right = imageWidth - PAD;
		List<List<Spec>> rows = new ArrayList<>();
		List<Integer> firstRowX = new ArrayList<>();
		List<Spec> row = new ArrayList<>();
		int x = PAD;
		if (withInsuranceBox) {
			x += 64;
		}
		firstRowX.add(x);
		for (Spec s : specs) {
			int w = width(s);
			if (x + w > right && !row.isEmpty()) {
				rows.add(row);
				row = new ArrayList<>();
				x = PAD;
				firstRowX.add(x);
			}
			row.add(s);
			x += w + 4;
		}
		if (!row.isEmpty()) {
			rows.add(row);
		}
		buttonsTop = imageHeight - 26 - rows.size() * 22;
		int y = buttonsTop;
		for (int r = 0; r < rows.size(); r++) {
			x = firstRowX.get(r);
			for (Spec s : rows.get(r)) {
				int w = width(s);
				Button b = button(s.label(), x, y, w, s.onPress());
				b.active = s.active();
				if (s.tooltip() != null) {
					b.setTooltip(Tooltip.create(s.tooltip()));
				}
				if (withInsuranceBox && r == 0 && x == firstRowX.get(0)) {
					insureButton = b;
				}
				x += w + 4;
			}
			y += 22;
		}
		if (withInsuranceBox) {
			long max = state().getLongOr("insurance_max", 0);
			insuranceBox = new EditBox(font, leftPos + PAD, topPos + buttonsTop, 60, 20, Component.translatable("gui.burmaldaholic.common.amount"));
			insuranceBox.setMaxLength(12);
			insuranceBox.setValue(insuranceText.isEmpty() ? Long.toString(max) : insuranceText);
			insuranceBox.setResponder(v -> {
				if (!v.chars().allMatch(Character::isDigit)) {
					insuranceBox.setValue(v.replaceAll("\\D", ""));
					return;
				}
				if (insureButton != null) {
					insureButton.setMessage(Component.translatable("gui.burmaldaholic.blackjack.insure", Texts.number(insuranceAmount())));
				}
			});
			addRenderableWidget(insuranceBox);
		}
	}

	private int width(Spec s) {
		return Math.max(s.minWidth(), font.width(s.label()) + 8);
	}

	private void addChip(long chip) {
		setPending(Math.min(maxAffordable(), pendingBet + chip));
	}

	private void setPending(long amount) {
		pendingBet = Math.max(0, amount);
		rebuild();
	}

	private void deal() {
		CompoundTag args = new CompoundTag();
		args.putLong("amount", pendingBet);
		sendAction("bet", args);
	}

	/** Insurance amount from the box, clamped to [0, max] (the server re-validates). */
	private long insuranceAmount() {
		long max = state().getLongOr("insurance_max", 0);
		String v = insuranceBox != null ? insuranceBox.getValue() : insuranceText;
		if (v.isEmpty()) {
			return max;
		}
		try {
			return Math.max(0, Math.min(max, Long.parseLong(v)));
		} catch (NumberFormatException e) {
			return max;
		}
	}

	private void insure(long amount) {
		CompoundTag args = new CompoundTag();
		args.putLong("amount", amount);
		insuranceText = "";
		sendAction("insurance", args);
	}

	private void evenMoney(boolean take) {
		CompoundTag args = new CompoundTag();
		args.putBoolean("take", take);
		sendAction("even_money", args);
	}

	// ---- rendering ---------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		if (highRoller()) {
			g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, GOLD);
			g.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, GOLD);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		drawTimer(g);
		int y = 18;
		drawDealer(g, y);
		y += BIG_ROW + 2;
		g.fill(PAD, y, imageWidth - PAD, y + 1, 0x60FFFFFF);
		y += 4;
		y = drawStatus(g, y);
		if (state().getListOrEmpty("players").isEmpty()) {
			y = drawBetting(g, y);
			drawRules(g, y + 2);
		} else {
			drawPlayers(g, y);
		}
		drawInfoLine(g);
		super.extractLabels(g, xm, ym); // title + error line
	}

	private void drawTimer(GuiGraphicsExtractor g) {
		long best = -1;
		for (String id : new String[] {"turn", "insurance", "bet"}) {
			long s = secondsLeft(id);
			if (s >= 0) {
				best = s;
				break;
			}
		}
		if (best < 0) {
			return;
		}
		Component t = Component.translatable("gui.burmaldaholic.common.timer", Texts.plural("unit.burmaldaholic.second", best));
		g.text(font, t, imageWidth - PAD - font.width(t), 6, best <= 5 ? RED : TEXT, true);
	}

	private void drawDealer(GuiGraphicsExtractor g, int y) {
		Component label = Component.translatable("gui.burmaldaholic.blackjack.dealer");
		g.text(font, label, PAD, y + 8, TEXT, true);
		int x = PAD + Math.max(56, font.width(label) + 6);
		List<Card> dealer = cards(state(), "dealer");
		if (dealer.isEmpty()) {
			return;
		}
		for (Card c : dealer) {
			drawCard(g, x, y, c);
			x += CW + 2;
		}
		if (state().getBooleanOr("hole_hidden", false)) {
			drawBack(g, x, y);
			x += CW + 2;
		}
		g.text(font, totalText(dealer), x + 4, y + 8, TEXT, true);
	}

	private int drawStatus(GuiGraphicsExtractor g, int y) {
		Component line = statusLine();
		// Seats & Bots: header ("Humans + 2 bots · Normal · Open to all") and the virtual-chips note.
		Tag header = state().get("bots_header");
		if (header != null) {
			g.text(font, clip(decode(header), imageWidth - 2 * PAD), PAD, y, GRAY, true);
			y += 10;
		}
		if (state().getBooleanOr("virtual", false)) {
			g.text(font, clip(Component.translatable("gui.burmaldaholic.bots.virtual_tooltip"), imageWidth - 2 * PAD), PAD, y, GRAY, false);
			y += 10;
		}
		if (line == null) {
			return y;
		}
		List<FormattedCharSequence> lines = font.split(line, imageWidth - 2 * PAD);
		for (FormattedCharSequence l : lines) {
			g.text(font, l, PAD, y, YELLOW, true);
			y += 10;
		}
		return y + 2;
	}

	/** A component sent by the server (bot names, the bots header). */
	private static Component decode(@Nullable Tag tag) {
		if (tag == null) {
			return Component.empty();
		}
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	/** One line cut to {@code width} pixels. */
	private FormattedCharSequence clip(Component c, int width) {
		if (font.width(c) <= width) {
			return c.getVisualOrderText();
		}
		return Language.getInstance().getVisualOrder(font.substrByWidth(c, width));
	}

	/** Seat name of a round participant: the player's name, or a bot's "[BOT] name · level". */
	private Component playerName(CompoundTag p) {
		Tag c = p.get("name_c");
		return c != null ? decode(c) : Texts.raw(p.getStringOr("name", ""));
	}

	private @Nullable Component statusLine() {
		CompoundTag s = state();
		String notice = s.getStringOr("notice", "");
		if (!notice.isEmpty()) {
			return Component.translatable(notice);
		}
		String offer = s.getStringOr("offer", "");
		if ("insurance".equals(offer)) {
			return Component.translatable("gui.burmaldaholic.blackjack.insurance_prompt");
		}
		if ("even_money".equals(offer)) {
			return Component.translatable("gui.burmaldaholic.blackjack.even_money_prompt");
		}
		switch (phase()) {
			case "insurance" -> {
				return Component.translatable("gui.burmaldaholic.common.waiting_players");
			}
			case "turns" -> {
				if (myTurn()) {
					long sec = secondsLeft("turn");
					return sec < 0 ? Component.translatable("gui.burmaldaholic.blackjack.your_turn")
						: Component.translatable("gui.burmaldaholic.common.auto_action", Component.translatable("gui.burmaldaholic.blackjack.stand"),
						Texts.plural("unit.burmaldaholic.second_acc", sec));
				}
				return Component.translatable("gui.burmaldaholic.blackjack.turn_of", currentName());
			}
			case "result" -> {
				CompoundTag me = myPlayer();
				return me == null ? null : netText(me.getLongOr("net", 0));
			}
			default -> {
				if (s.getBooleanOr("bet_placed", false)) {
					long sec = secondsLeft("bet");
					return sec < 0 ? Component.translatable("gui.burmaldaholic.common.waiting_players")
						: Component.translatable("msg.burmaldaholic.blackjack.round_starts_in", Texts.plural("unit.burmaldaholic.second_acc", sec));
				}
				return Component.translatable("gui.burmaldaholic.blackjack.betting_open");
			}
		}
	}

	private Component currentName() {
		int cur = state().getIntOr("current_seat", -1);
		ListTag players = state().getListOrEmpty("players");
		for (int i = 0; i < players.size(); i++) {
			CompoundTag p = players.getCompoundOrEmpty(i);
			if (p.getIntOr("seat", -2) == cur) {
				return playerName(p);
			}
		}
		return Component.empty();
	}

	private static Component netText(long net) {
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.common.result.win", Texts.number(net));
		}
		if (net < 0) {
			return Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(-net));
		}
		return Component.translatable("gui.burmaldaholic.common.result.push");
	}

	/** Betting phase: seated players (and bots, virtual bets) and their bets, plus the pending bet of the viewer. */
	private int drawBetting(GuiGraphicsExtractor g, int y) {
		List<String> names = seatNames();
		ListTag bets = state().getListOrEmpty("bets");
		ListTag botSeats = state().getListOrEmpty("bot_seats");
		int nameW = 90;
		int seatCount = Math.max(names.size(), state().getIntOr("seat_count", 0));
		for (int i = 0; i < seatCount; i++) {
			CompoundTag bot = null;
			for (int b = 0; b < botSeats.size(); b++) {
				if (botSeats.getCompoundOrEmpty(b).getIntOr("seat", -1) == i) {
					bot = botSeats.getCompoundOrEmpty(b);
				}
			}
			String human = i < names.size() ? names.get(i) : "";
			if ((human.isEmpty() && bot == null) || y + 10 > buttonsTop - 12) {
				continue;
			}
			boolean me = i == mySeat();
			if (bot != null && human.isEmpty()) {
				g.text(font, clip(decode(bot.get("name_c")), nameW), PAD, y, GRAY, true);
				long v = bot.getLongOr("amount", 0);
				Component vb = v > 0 ? Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(v))
					: Component.translatable("gui.burmaldaholic.blackjack.no_bet");
				g.text(font, vb, PAD + nameW + 6, y, GRAY, true);
				y += ROW;
				continue;
			}
			g.text(font, name(human, me, nameW), PAD, y, me ? YELLOW : TEXT, true);
			long amount = 0;
			for (int b = 0; b < bets.size(); b++) {
				CompoundTag bt = bets.getCompoundOrEmpty(b);
				if (bt.getIntOr("seat", -1) == i) {
					amount = bt.getLongOr("amount", 0);
				}
			}
			Component bet = amount > 0 ? Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(amount))
				: Component.translatable("gui.burmaldaholic.blackjack.no_bet");
			g.text(font, bet, PAD + nameW + 6, y, amount > 0 ? TEXT : GRAY, true);
			y += ROW;
		}
		if (!state().getBooleanOr("bet_placed", false)) {
			Component pending = Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(pendingBet));
			g.text(font, pending, imageWidth - PAD - font.width(pending), buttonsTop - 11, GOLD, true);
		}
		return y;
	}

	private void drawRules(GuiGraphicsExtractor g, int y) {
		BlackjackConfig c = CasinoConfig.blackjack();
		List<Component> rules = new ArrayList<>();
		if (c.decks == 6 && !c.dealerHitsSoft17 && c.blackjackPayout == 1.5) {
			rules.add(Component.translatable("gui.burmaldaholic.blackjack.rules.1"));
		}
		if (c.dealerHitsSoft17) {
			rules.add(Component.translatable("gui.burmaldaholic.blackjack.rules.h17"));
		}
		if (c.doubleAfterSplit && c.maxHands == 4) {
			rules.add(Component.translatable("gui.burmaldaholic.blackjack.rules.2"));
		}
		if (!c.resplitAces && c.insurance) {
			rules.add(Component.translatable("gui.burmaldaholic.blackjack.rules.3"));
		}
		int limit = buttonsTop - 13;
		for (Component r : rules) {
			for (FormattedCharSequence l : font.split(r, imageWidth - 2 * PAD)) {
				if (y + 9 > limit) {
					return;
				}
				g.text(font, l, PAD, y, GRAY, false);
				y += 10;
			}
		}
	}

	private void drawPlayers(GuiGraphicsExtractor g, int y) {
		ListTag players = state().getListOrEmpty("players");
		int cur = state().getIntOr("current_seat", -1);
		int curHand = state().getIntOr("current_hand", -1);
		int nameW = 64;
		// the viewer first, with big cards
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < players.size(); i++) {
				CompoundTag p = players.getCompoundOrEmpty(i);
				boolean me = p.getBooleanOr("you", false);
				if (me != (pass == 0)) {
					continue;
				}
				int rowH = me ? BIG_ROW + 10 : ROW;
				if (y + rowH > buttonsTop - 2) {
					return;
				}
				int seat = p.getIntOr("seat", -1);
				boolean active = seat == cur;
				boolean bot = p.getBooleanOr("bot", false);
				int color = p.getBooleanOr("away", false) || bot ? GRAY : me ? YELLOW : TEXT;
				if (bot) {
					g.text(font, clip(playerName(p), nameW), PAD, y, color, true);
				} else {
					g.text(font, name(p.getStringOr("name", ""), me, nameW), PAD, y + (me ? 8 : 0), color, true);
				}
				if (active) {
					g.text(font, Texts.raw("▶"), PAD - 7, y + (me ? 8 : 0), YELLOW, true);
				}
				int x = PAD + nameW + 4;
				ListTag hands = p.getListOrEmpty("hands");
				Component betLine = Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(totalBet(p)));
				int betW = font.width(betLine);
				int right = imageWidth - PAD - betW - 6;
				for (int h = 0; h < hands.size(); h++) {
					CompoundTag hand = hands.getCompoundOrEmpty(h);
					boolean activeHand = active && h == curHand;
					x = me ? drawBigHand(g, x, y, hand, activeHand, (right - x) / Math.max(1, hands.size() - h))
						: drawSmallHand(g, x, y, hand, activeHand);
					x += 8;
				}
				g.text(font, betLine, imageWidth - PAD - betW, y + (me ? 8 : 0), GRAY, true);
				if (me && p.getLongOr("insurance", 0) > 0) {
					Component ins = p.getBooleanOr("settled", false) && p.getLongOr("insurance_return", 0) > 0
						? Component.translatable("gui.burmaldaholic.blackjack.result.insurance_paid", Texts.number(p.getLongOr("insurance_return", 0) - p.getLongOr("insurance", 0)))
						: Component.translatable("gui.burmaldaholic.blackjack.insurance");
					g.text(font, ins, imageWidth - PAD - font.width(ins), y + 18, GRAY, true);
				}
				y += rowH;
			}
		}
	}

	private static long totalBet(CompoundTag p) {
		long sum = 0;
		ListTag hands = p.getListOrEmpty("hands");
		for (int h = 0; h < hands.size(); h++) {
			sum += hands.getCompoundOrEmpty(h).getLongOr("bet", 0);
		}
		return sum + p.getLongOr("insurance", 0);
	}

	private Component name(String name, boolean me, int width) {
		if (me) {
			return Component.translatable("gui.burmaldaholic.common.you");
		}
		if (font.width(name) <= width) {
			return Texts.raw(name);
		}
		return Texts.raw(font.plainSubstrByWidth(name, width - font.width("…")) + "…");
	}

	/** Big cards (viewer), overlapping when space is short; total and outcome below. */
	private int drawBigHand(GuiGraphicsExtractor g, int x, int y, CompoundTag hand, boolean active, int space) {
		List<Card> cs = cards(hand, "cards");
		int startX = x;
		int step = CW + 2;
		if (cs.size() > 1) {
			step = Math.max(8, Math.min(CW + 2, (space - CW - 4) / (cs.size() - 1)));
		}
		for (Card c : cs) {
			drawCard(g, x, y, c);
			x += step;
		}
		x += CW - step;
		int endX = Math.max(x, startX + CW);
		if (active) {
			g.fill(startX, y + CH + 1, endX, y + CH + 2, YELLOW);
		}
		Component under = outcomeText(hand);
		if (under == null) {
			under = totalText(cs);
		}
		g.text(font, under, startX, y + CH + 3, outcomeColor(hand), true);
		return Math.max(endX, startX + font.width(under));
	}

	/** Compact card chips for other players: "10♥ 7♣  17 · Win +50". */
	private int drawSmallHand(GuiGraphicsExtractor g, int x, int y, CompoundTag hand, boolean active) {
		List<Card> cs = cards(hand, "cards");
		int startX = x;
		for (Card c : cs) {
			String label = c.rankLabel() + c.suitSymbol();
			int w = font.width(label) + 2;
			g.fill(x, y - 1, x + w, y + 9, CARD_FACE);
			g.text(font, label, x + 1, y, c.isRed() ? 0xFFCC0000 : 0xFF111111, false);
			x += w + 1;
		}
		if (active) {
			g.fill(startX, y + 9, x - 1, y + 10, YELLOW);
		}
		Component tail = outcomeText(hand);
		if (tail == null) {
			tail = totalText(cs);
		}
		g.text(font, tail, x + 3, y, outcomeColor(hand), true);
		return x + 3 + font.width(tail);
	}

	private Component totalText(List<Card> cs) {
		int[] t = Hands.displayTotals(cs);
		return t.length == 2 ? Component.translatable("gui.burmaldaholic.blackjack.total_soft", Texts.number(t[0]), Texts.number(t[1]))
			: Component.translatable("gui.burmaldaholic.blackjack.total", Texts.number(t[0]));
	}

	private static @Nullable Component outcomeText(CompoundTag hand) {
		String o = hand.getStringOr("outcome", "");
		long profit = hand.getLongOr("ret", 0) - hand.getLongOr("bet", 0);
		return switch (o) {
			case "blackjack" -> Component.translatable("gui.burmaldaholic.blackjack.result.blackjack", Texts.number(profit));
			case "win" -> Component.translatable("gui.burmaldaholic.blackjack.result.win", Texts.number(profit));
			case "dealer_bust" -> Component.translatable("gui.burmaldaholic.blackjack.result.dealer_bust", Texts.number(profit));
			case "even_money" -> Component.translatable("gui.burmaldaholic.blackjack.result.even_money", Texts.number(profit));
			case "push" -> Component.translatable("gui.burmaldaholic.blackjack.result.push");
			case "bust" -> Component.translatable("gui.burmaldaholic.blackjack.result.bust");
			case "lose" -> Component.translatable("gui.burmaldaholic.blackjack.result.lose");
			case "dealer_blackjack" -> Component.translatable("gui.burmaldaholic.blackjack.result.dealer_blackjack");
			case "surrender" -> Component.translatable("gui.burmaldaholic.blackjack.result.surrender");
			default -> null;
		};
	}

	private static int outcomeColor(CompoundTag hand) {
		return switch (hand.getStringOr("outcome", "")) {
			case "blackjack", "win", "dealer_bust", "even_money" -> GREEN;
			case "push" -> GRAY;
			case "" -> TEXT;
			default -> RED;
		};
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
		int y = imageHeight - 23;
		g.text(font, Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance())), PAD, y, GOLD, true);
		Component limits = limitsLine();
		g.text(font, limits, imageWidth - PAD - font.width(limits), y, GRAY, true);
	}
}
