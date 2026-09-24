package dev.nezo.burmaldaholic.games.uth.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.uth.logic.Decision;
import dev.nezo.burmaldaholic.games.uth.logic.PayHand;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Ultimate Texas Hold'em screen (UI.md §15). Renders the server state and sends actions only:
 * {@code bet {ante, trips}}, {@code clear}, {@code check|bet_4x|bet_3x|bet_2x|bet_1x|fold},
 * {@code take_bank {amount}}, {@code leave_bank}, {@code bot_settings} (the bots module's table settings),
 * core's {@code sit|leave}. Atmosphere bots (BOTS.md §4.5) show as seat plates "[BOT] Name" with their
 * virtual bets, "Thinking…" or "Watching" (under a human banker).
 *
 * <pre>
 *  Ultimate Texas Hold'em                                           Time left: 17 s
 *  Dealer seat: Alex · Bank 12 000 · reserved 5 550                  (player-banked only)
 *  Dealer [▒][▒]                         Board [Q♠][J♠][T♠][▒][▒]
 *  Still deciding: 2                                   (status, wraps)
 *  ┌ Alex  Play ×4 ┐ ┌ You  Deciding… ┐ ┌ Steve  Checked ┐          (3 plates per row)
 *  [A♠][K♠]  Your hand: Straight
 *            Trips: 5 · Ante: 10 · Blind: 10 · Play: —      At risk: up to 65
 *  [Check] [Bet ×3 (30)] [Bet ×4 (40)] [Paytable] [Leave]            (rows wrap, RU-safe)
 *  Balance: 12 500                                   Min 1 · Max 1 000
 * </pre>
 * Layout 400 × 240 (compact 320 × 220 on small windows); every label is measured, long ones wrap or are
 * trimmed with an ellipsis (names only).
 */
public class UthScreen extends CasinoTableScreen {
	private static final int WIDE_W = 400, WIDE_H = 240, COMPACT_W = 320, COMPACT_H = 220;
	private static final int PAD = 6;
	private static final int[] CHIPS = {1, 5, 25, 100, 500};
	private static final int GOLD = 0xFFFFD700, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55, RED = 0xFFFF5555, YELLOW = 0xFFFFFF55,
		YOU = 0xFF7FC8FF, CARD_FACE = 0xFFF8F8F0, CARD_EDGE = 0xFF202020, CARD_BACK = 0xFF8B1E2B, CARD_RED = 0xFFD42A2A,
		CARD_BLACK = 0xFF151515, PLATE = 0x50000000, PLATE_ME = 0x6030507F, OVERLAY = 0xE0102018;
	private static final String[] SUITS = {"♠", "♥", "♦", "♣"};

	private final boolean compact;
	private long ante;
	private long trips;
	private boolean chipsToTrips;
	private boolean showPaytable;
	private boolean bankForm;
	private @Nullable EditBox bankBox;
	private String bankText = "";
	private int buttonsTop;
	private int ticks;
	private int stateTick;
	private int errorTicks;

	private record Spec(Component label, Button.OnPress onPress, boolean active, @Nullable Component tooltip, int minWidth) {}

	public UthScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		this(menu, inventory, useCompact());
	}

	private UthScreen(CasinoTableMenu menu, Inventory inventory, boolean compact) {
		super(menu, inventory, Component.translatable(titleKey(menu)), compact ? COMPACT_W : WIDE_W, compact ? COMPACT_H : WIDE_H);
		this.compact = compact;
		this.titleLabelX = PAD;
		this.titleLabelY = 5;
		this.buttonsTop = imageHeight - 40;
	}

	private static String titleKey(CasinoTableMenu menu) {
		String name = menu.tableType().name();
		return name.endsWith("high_roller") ? "gui.burmaldaholic.uth.title_high_roller"
			: name.endsWith("player_banked") ? "gui.burmaldaholic.uth.title_player_banked" : "gui.burmaldaholic.uth.title";
	}

	private static boolean useCompact() {
		var window = Minecraft.getInstance().getWindow();
		return window.getGuiScaledWidth() < WIDE_W + 10 || window.getGuiScaledHeight() < WIDE_H + 6;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		stateTick = ticks;
		if (ante == 0 && newState.getLongOr("last_ante", 0) == 0) {
			ante = Math.max(0, newState.getLongOr("min", 0));
		}
		if (minecraft != null) {
			rebuild();
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		ticks++;
		if (errorTicks > 0) {
			errorTicks--;
		}
	}

	@Override
	public void showError(Component message) {
		super.showError(message);
		errorTicks = 60;
	}

	// ---- state helpers ------------------------------------------------------------------------------

	private CompoundTag s() {
		return state();
	}

	private boolean seated() {
		return mySeat() >= 0;
	}

	private boolean betting() {
		return "betting".equals(phase()) || "idle".equals(phase());
	}

	private boolean confirmedBet() {
		return s().getLongOr("my_ante", 0) > 0;
	}

	private CompoundTag bank() {
		return s().getCompoundOrEmpty("bank");
	}

	private boolean playerBanked() {
		return "player_banked".equals(s().getStringOr("variant", ""));
	}

	private boolean iAmBanker() {
		return bank().getBooleanOr("you", false);
	}

	private long secondsLeft(String id) {
		long t = s().getCompoundOrEmpty("timers").getLongOr(id, -1);
		return t < 0 ? -1 : (Math.max(0, t - (ticks - stateTick)) + 19) / 20;
	}

	private List<String> legal() {
		List<String> out = new ArrayList<>();
		ListTag l = s().getListOrEmpty("legal");
		for (int i = 0; i < l.size(); i++) {
			out.add(l.getStringOr(i, ""));
		}
		return out;
	}

	private @Nullable CompoundTag me() {
		ListTag players = s().getListOrEmpty("players");
		for (int i = 0; i < players.size(); i++) {
			CompoundTag p = players.getCompoundOrEmpty(i);
			if (p.getBooleanOr("you", false)) {
				return p;
			}
		}
		return null;
	}

	/** Largest W the viewer may bet: min(max, what the balance allows keeping 1 × Ante for the river). */
	private long maxW() {
		return maxBet();
	}

	private long worstCase(long a, long t) {
		return 6 * a + t;
	}

	private boolean pendingValid() {
		long min = minBet();
		long tripsMin = s().getLongOr("trips_min", 1);
		return ante >= min && (trips == 0 || trips >= tripsMin) && worstCase(ante, trips) <= maxW() && 3 * ante + trips <= balance();
	}

	// ---- widgets ------------------------------------------------------------------------------------

	private void rebuild() {
		if (bankBox != null) {
			bankText = bankBox.getValue();
		}
		clearWidgets();
		bankBox = null;
		List<Spec> specs = new ArrayList<>();
		CompoundTag bank = bank();
		boolean bankerView = iAmBanker();
		if (!seated() && !bankerView) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.poker.sit_down"), b -> sendAction("sit"), true, null, 40));
		}
		if (seated() && betting() && !confirmedBet() && !bankerView) {
			for (int chip : CHIPS) {
				specs.add(new Spec(Texts.number(chip), b -> addChip(chip), true, null, 22));
			}
			if (s().getBooleanOr("trips_enabled", true)) {
				MutableComponent label = Component.translatable("gui.burmaldaholic.uth.trips");
				specs.add(new Spec(chipsToTrips ? label.withStyle(net.minecraft.ChatFormatting.GOLD) : label, b -> {
					chipsToTrips = !chipsToTrips;
					rebuild();
				}, true, Component.translatable("gui.burmaldaholic.uth.trips_optional"), 30));
			}
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.clear"), b -> {
				ante = 0;
				trips = 0;
				rebuild();
			}, ante > 0 || trips > 0, null, 30));
			long lastAnte = s().getLongOr("last_ante", 0);
			long lastTrips = s().getLongOr("last_trips", 0);
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.rebet"), b -> {
				ante = lastAnte;
				trips = lastTrips;
				rebuild();
			}, lastAnte > 0, null, 30));
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.deal"), b -> deal(), pendingValid(), null, 36));
		} else if (seated() && betting() && confirmedBet()) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.clear"), b -> sendAction("clear"), true, null, 30));
		}
		List<String> legal = legal();
		if (!legal.isEmpty()) {
			CompoundTag me = me();
			long a = me == null ? 0 : me.getLongOr("ante", 0);
			for (Decision d : List.of(Decision.CHECK, Decision.FOLD, Decision.BET_1X, Decision.BET_2X, Decision.BET_3X, Decision.BET_4X)) {
				if (!legal.contains(d.id())) {
					continue;
				}
				long amount = d.multiple() * a;
				boolean affordable = amount <= balance();
				Component label = d.isBet() ? Component.translatable("gui.burmaldaholic.uth." + d.id(), Texts.number(amount))
					: Component.translatable("gui.burmaldaholic.uth." + d.id());
				Component tip = affordable ? Component.translatable("gui.burmaldaholic.uth." + d.id() + ".tooltip")
					: Component.translatable("gui.burmaldaholic.uth.unaffordable");
				specs.add(new Spec(label, b -> sendAction(d.id()), affordable, tip, 36));
			}
		}
		if (playerBanked() && bank.getBooleanOr("enabled", false)) {
			if (bankerView) {
				boolean leaving = bank.getBooleanOr("leaving", false);
				specs.add(new Spec(Component.translatable(leaving ? "gui.burmaldaholic.uth.pvp.leaving_after_round" : "gui.burmaldaholic.uth.pvp.leave_seat"),
					b -> sendAction("leave_bank"), !leaving, null, 40));
			} else if (bank.getBooleanOr("can_take", false)) {
				if (bankForm) {
					specs.add(new Spec(Component.translatable("gui.burmaldaholic.uth.pvp.take_seat_submit"), b -> takeBank(), true, null, 40));
					specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.cancel"), b -> {
						bankForm = false;
						rebuild();
					}, true, null, 30));
				} else {
					specs.add(new Spec(Component.translatable("gui.burmaldaholic.uth.pvp.take_seat"), b -> {
						bankForm = true;
						rebuild();
					}, true, Component.translatable("gui.burmaldaholic.uth.pvp.rules.1"), 40));
				}
			}
		}
		if (s().getBooleanOr("bots_ui", false)) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.bots.settings.open"), b -> sendAction("bot_settings"), true, null, 40));
		}
		specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.paytable"), b -> {
			showPaytable = !showPaytable;
		}, true, null, 30));
		if (seated()) {
			specs.add(new Spec(Component.translatable("gui.burmaldaholic.common.leave"), b -> sendAction("leave"), true, null, 30));
		}
		layout(specs, bankForm && bank.getBooleanOr("can_take", false) && !bankerView);
	}

	private void layout(List<Spec> specs, boolean withBankBox) {
		int right = imageWidth - PAD;
		List<List<Spec>> rows = new ArrayList<>();
		List<Spec> row = new ArrayList<>();
		int boxW = 64;
		int x = PAD + (withBankBox ? boxW + 4 : 0);
		int firstX = x;
		for (Spec sp : specs) {
			int w = width(sp);
			if (x + w > right && !row.isEmpty()) {
				rows.add(row);
				row = new ArrayList<>();
				x = PAD;
			}
			row.add(sp);
			x += w + 3;
		}
		if (!row.isEmpty()) {
			rows.add(row);
		}
		buttonsTop = imageHeight - 14 - rows.size() * 22;
		int y = buttonsTop;
		for (int r = 0; r < rows.size(); r++) {
			x = r == 0 ? firstX : PAD;
			for (Spec sp : rows.get(r)) {
				int w = width(sp);
				Button b = button(sp.label(), x, y, w, sp.onPress());
				b.active = sp.active();
				if (sp.tooltip() != null) {
					b.setTooltip(Tooltip.create(sp.tooltip()));
				}
				x += w + 3;
			}
			y += 22;
		}
		if (withBankBox) {
			long min = bank().getLongOr("min_bank", 0);
			bankBox = new EditBox(font, leftPos + PAD, topPos + buttonsTop, boxW, 20, Component.translatable("gui.burmaldaholic.uth.pvp.bank_amount",
				Texts.number(min)));
			bankBox.setMaxLength(12);
			bankBox.setValue(bankText.isEmpty() ? Long.toString(min) : bankText);
			bankBox.setTooltip(Tooltip.create(Component.translatable("gui.burmaldaholic.uth.pvp.bank_amount", Texts.number(min))));
			bankBox.setResponder(v -> {
				if (!v.chars().allMatch(Character::isDigit)) {
					bankBox.setValue(v.replaceAll("\\D", ""));
				}
			});
			addRenderableWidget(bankBox);
		}
	}

	private int width(Spec sp) {
		return Math.max(sp.minWidth(), font.width(sp.label()) + 8);
	}

	private void addChip(long chip) {
		if (chipsToTrips) {
			trips += chip;
		} else {
			ante += chip;
		}
		rebuild();
	}

	private void deal() {
		CompoundTag args = new CompoundTag();
		args.putLong("ante", ante);
		args.putLong("trips", trips);
		sendAction("bet", args);
	}

	private void takeBank() {
		long amount;
		try {
			amount = Long.parseLong(bankBox != null ? bankBox.getValue() : bankText);
		} catch (NumberFormatException e) {
			amount = bank().getLongOr("min_bank", 0);
		}
		CompoundTag args = new CompoundTag();
		args.putLong("amount", amount);
		bankForm = false;
		bankText = "";
		sendAction("take_bank", args);
	}

	// ---- rendering ----------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		if (s().getBooleanOr("high_roller", false)) {
			g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, GOLD);
			g.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, GOLD);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		drawTimer(g);
		int y = 16;
		if (playerBanked()) {
			y = drawBankPlate(g, y);
		}
		y = drawDealerAndBoard(g, y);
		y = drawStatus(g, y + 2);
		if (showPaytable) {
			drawPaytable(g, y);
		} else {
			y = drawPlates(g, y + 1);
			drawMine(g, y + 2);
		}
		if (errorTicks <= 0) {
			drawInfoLine(g);
		}
		super.extractLabels(g, xm, ym);
	}

	private void drawTimer(GuiGraphicsExtractor g) {
		long sec = -1;
		for (String id : new String[] {"decide", "bet"}) {
			sec = secondsLeft(id);
			if (sec >= 0) {
				break;
			}
		}
		if (sec < 0) {
			return;
		}
		Component t = Component.translatable("gui.burmaldaholic.common.timer", Texts.plural("unit.burmaldaholic.second", sec));
		g.text(font, t, imageWidth - PAD - font.width(t), 5, sec <= 5 ? RED : TEXT, true);
	}

	private int drawBankPlate(GuiGraphicsExtractor g, int y) {
		CompoundTag b = bank();
		Component who = b.contains("name") ? Texts.raw(b.getStringOr("name", ""))
			: b.contains("stand_in") ? botName(b.getStringOr("stand_in", "")) : Component.translatable("gui.burmaldaholic.uth.pvp.the_house");
		MutableComponent line = Component.translatable("gui.burmaldaholic.uth.pvp.dealer_seat", who);
		if (b.contains("bank")) {
			line.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.pvp.bank",
				Texts.number(b.getLongOr("bank", 0)), Texts.number(b.getLongOr("reserved", 0))));
		}
		for (FormattedCharSequence l : font.split(line, imageWidth - 2 * PAD)) {
			g.text(font, l, PAD, y, GOLD, true);
			y += 10;
		}
		return y;
	}

	private int drawDealerAndBoard(GuiGraphicsExtractor g, int y) {
		int cw = compact ? 14 : 16;
		int ch = compact ? 20 : 22;
		Component dl = Component.translatable("gui.burmaldaholic.uth.dealer");
		g.text(font, dl, PAD, y + ch / 2 - 4, TEXT, true);
		int x = PAD + font.width(dl) + 4;
		int[] dealer = s().getIntArray("dealer").orElse(new int[0]);
		boolean inRound = !s().getListOrEmpty("players").isEmpty();
		for (int k = 0; k < 2; k++) {
			if (k < dealer.length) {
				drawCard(g, x, y, cw, ch, dealer[k]);
			} else if (inRound) {
				drawBack(g, x, y, cw, ch);
			} else {
				g.outline(x, y, cw, ch, 0x55FFFFFF);
			}
			x += cw + 2;
		}
		int dealerEnd = x;
		Component bl = Component.translatable("gui.burmaldaholic.uth.board");
		int boardW = font.width(bl) + 4 + 5 * (cw + 2);
		int bx = Math.max(dealerEnd + 8, imageWidth - PAD - boardW);
		g.text(font, bl, bx, y + ch / 2 - 4, TEXT, true);
		bx += font.width(bl) + 4;
		int[] board = s().getIntArray("board").orElse(new int[0]);
		for (int k = 0; k < 5; k++) {
			if (k < board.length) {
				drawCard(g, bx, y, cw, ch, board[k]);
			} else if (inRound) {
				drawBack(g, bx, y, cw, ch);
			} else {
				g.outline(bx, y, cw, ch, 0x55FFFFFF);
			}
			bx += cw + 2;
		}
		y += ch + 2;
		if (dealer.length == 2) {
			Component hand = Component.translatable("gui.burmaldaholic.uth.dealer_hand", handName(s().getStringOr("dealer_hand", "")));
			g.text(font, hand, PAD, y, TEXT, true);
			y += 10;
		}
		return y;
	}

	private int drawStatus(GuiGraphicsExtractor g, int y) {
		List<Component> lines = statusLines();
		for (Component c : lines) {
			for (FormattedCharSequence l : font.split(c, imageWidth - 2 * PAD)) {
				g.text(font, l, PAD, y, YELLOW, true);
				y += 10;
			}
		}
		return y;
	}

	private List<Component> statusLines() {
		List<Component> out = new ArrayList<>();
		CompoundTag st = s();
		String phase = phase();
		if (st.getBooleanOr("bots_pending", false) && betting()) {
			out.add(Component.translatable("gui.burmaldaholic.bots.pending"));
		}
		if (st.getIntArray("dealer").isPresent()) {
			out.add(Component.translatable(st.getBooleanOr("qualifies", false) ? "gui.burmaldaholic.uth.qualifies" : "gui.burmaldaholic.uth.not_qualifies"));
		}
		if (iAmBanker()) {
			CompoundTag b = bank();
			if (b.contains("last_result") && ("result".equals(phase) || betting())) {
				long r = b.getLongOr("last_result", 0);
				out.add(Component.translatable("gui.burmaldaholic.uth.pvp.round_result",
					Component.empty().append(Texts.raw(r >= 0 ? "+" : "−")).append(Texts.number(Math.abs(r))), Texts.number(b.getLongOr("last_rake", 0))));
			} else {
				out.add(Component.translatable("gui.burmaldaholic.uth.pvp.you_bank"));
			}
			return out;
		}
		if (st.getBooleanOr("waiting_next", false) && !betting()) {
			out.add(Component.translatable("gui.burmaldaholic.uth.waiting_next"));
			return out;
		}
		int deciding = st.getIntOr("deciding", 0);
		if (!legal().isEmpty()) {
			out.add(Component.translatable("gui.burmaldaholic.uth.your_decision"));
		} else if (deciding > 0) {
			out.add(Component.translatable("gui.burmaldaholic.uth.still_deciding", Texts.number(deciding)));
		} else if (betting()) {
			long sec = secondsLeft("bet");
			if (sec >= 0) {
				out.add(Component.translatable("msg.burmaldaholic.uth.round_starts_in", Texts.plural("unit.burmaldaholic.second_acc", sec)));
			} else if (!seated()) {
				int free = st.getIntOr("seat_count", 0) - st.getListOrEmpty("seats").size();
				out.add(Texts.plural("gui.burmaldaholic.uth.seats_free", Math.max(0, free)));
			}
		}
		return out;
	}

	/** Seat plates: 3 per row (name + status; bets or result). */
	private int drawPlates(GuiGraphicsExtractor g, int y) {
		ListTag players = s().getListOrEmpty("players");
		List<CompoundTag> plates = new ArrayList<>();
		if (players.isEmpty()) {
			// betting: seated players with their confirmed bets
			List<String> names = seatNames();
			ListTag bets = s().getListOrEmpty("bets");
			for (int i = 0; i < names.size(); i++) {
				if (names.get(i).isEmpty()) {
					continue;
				}
				CompoundTag p = new CompoundTag();
				p.putString("name", names.get(i));
				p.putBoolean("you", i == mySeat());
				for (int b = 0; b < bets.size(); b++) {
					CompoundTag bt = bets.getCompoundOrEmpty(b);
					if (bt.getIntOr("seat", -1) == i) {
						p.putLong("ante", bt.getLongOr("ante", 0));
						p.putLong("trips", bt.getLongOr("trips", 0));
					}
				}
				plates.add(p);
			}
		} else {
			for (int i = 0; i < players.size(); i++) {
				plates.add(players.getCompoundOrEmpty(i));
			}
		}
		// atmosphere bots not dealt in: virtual bets / Thinking… while betting, Watching under a human banker
		ListTag bots = s().getListOrEmpty("bots");
		for (int i = 0; i < bots.size(); i++) {
			CompoundTag b = bots.getCompoundOrEmpty(i).copy();
			b.putBoolean("bot", true);
			plates.add(b);
		}
		if (plates.isEmpty()) {
			return y;
		}
		int cols = 3;
		int gap = 3;
		int pw = (imageWidth - 2 * PAD - (cols - 1) * gap) / cols;
		int ph = 22;
		for (int i = 0; i < plates.size(); i++) {
			int px = PAD + (i % cols) * (pw + gap);
			int py = y + (i / cols) * (ph + gap);
			drawPlate(g, px, py, pw, ph, plates.get(i));
		}
		int rows = (plates.size() + cols - 1) / cols;
		return y + rows * (ph + gap);
	}

	private void drawPlate(GuiGraphicsExtractor g, int x, int y, int w, int h, CompoundTag p) {
		boolean you = p.getBooleanOr("you", false);
		g.fill(x, y, x + w, y + h, you ? PLATE_ME : PLATE);
		int[] cards = p.getIntArray("cards").orElse(new int[0]);
		boolean inRound = p.contains("tag");
		int cw = 9, ch = 12;
		int cardsW = inRound && !you ? 2 * (cw + 1) : 0;
		int textW = w - 4 - cardsW;
		boolean bot = p.getBooleanOr("bot", false);
		Component name = you ? Component.translatable("gui.burmaldaholic.common.you")
			: bot ? botName(p.getStringOr("bot_name", "")) : Texts.raw(trim(p.getStringOr("name", ""), textW / 2));
		Component tag = tagText(p);
		MutableComponent line1 = Component.empty().append(name);
		if (tag != null) {
			line1.append(Texts.raw("  ")).append(tag);
		}
		drawTrimmed(g, line1, x + 2, y + 2, textW, you ? YOU : TEXT);
		Component line2;
		int color = GRAY;
		if (p.contains("net")) {
			long net = p.getLongOr("net", 0);
			line2 = net > 0 ? Component.empty().append(Texts.raw("+")).append(Texts.number(net))
				: net < 0 ? Component.empty().append(Texts.raw("−")).append(Texts.number(-net)) : Component.translatable("gui.burmaldaholic.common.result.push");
			color = net > 0 ? GREEN : net < 0 ? RED : GRAY;
			if (p.contains("hand")) {
				line2 = Component.empty().append(handName(p.getStringOr("hand", ""))).append(Texts.raw(" ")).append(line2);
			}
		} else if (p.getLongOr("ante", 0) > 0) {
			MutableComponent bets = Component.translatable("gui.burmaldaholic.uth.ante_amount", Texts.number(p.getLongOr("ante", 0)));
			if (p.getLongOr("trips", 0) > 0) {
				bets.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.trips_amount", Texts.number(p.getLongOr("trips", 0))));
			}
			line2 = bets;
		} else if (bot && "watching".equals(p.getStringOr("bot_state", ""))) {
			line2 = Component.translatable("gui.burmaldaholic.bots.watching");
		} else if (bot && "thinking".equals(p.getStringOr("bot_state", ""))) {
			line2 = Component.translatable("gui.burmaldaholic.bots.thinking");
		} else {
			line2 = Component.empty();
		}
		drawTrimmed(g, line2, x + 2, y + 12, textW, color);
		if (cardsW > 0) {
			int cx = x + w - cardsW - 1;
			for (int k = 0; k < 2; k++) {
				if (k < cards.length) {
					drawMiniCard(g, cx + k * (cw + 1), y + 5, cw, ch, cards[k]);
				} else {
					drawBack(g, cx + k * (cw + 1), y + 5, cw, ch);
				}
			}
		}
	}

	private @Nullable Component tagText(CompoundTag p) {
		String tag = p.getStringOr("tag", "");
		return switch (tag) {
			case "deciding" -> Component.translatable("gui.burmaldaholic.uth.tag.deciding");
			case "checked" -> Component.translatable("gui.burmaldaholic.uth.tag.checked");
			case "folded" -> Component.translatable("gui.burmaldaholic.uth.tag.folded");
			case "play" -> Component.translatable("gui.burmaldaholic.uth.play_multiple", Texts.number(p.getIntOr("play", 0)));
			default -> null;
		};
	}

	/** The viewer's own area: cards, hand, bets, at-risk / result lines. */
	private void drawMine(GuiGraphicsExtractor g, int y) {
		int limit = buttonsTop - 2;
		CompoundTag me = me();
		int x = PAD;
		if (me != null) {
			int[] cards = me.getIntArray("cards").orElse(new int[0]);
			int cw = compact ? 16 : 18;
			int ch = compact ? 22 : 24;
			if (y + ch <= limit + 2) {
				for (int k = 0; k < cards.length; k++) {
					drawCard(g, x + k * (cw + 2), y, cw, ch, cards[k]);
				}
			}
			x += 2 * (cw + 2) + 6;
		}
		int w = imageWidth - PAD - x;
		List<Component> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		CompoundTag st = s();
		if (me != null) {
			if (st.contains("my_hand")) {
				lines.add(Component.translatable("gui.burmaldaholic.uth.your_hand", handName(st.getStringOr("my_hand", ""))));
				colors.add(TEXT);
			}
			CompoundTag res = st.getCompoundOrEmpty("result");
			if (!res.isEmpty()) {
				addResultLines(lines, colors, res);
			} else {
				long a = me.getLongOr("ante", 0);
				long t = me.getLongOr("trips", 0);
				int play = me.getIntOr("play", 0);
				lines.add(betsLine(a, t, play));
				colors.add(GRAY);
				if (play > 0) {
					lines.add(Component.translatable("gui.burmaldaholic.uth.waiting_showdown",
						Component.translatable("gui.burmaldaholic.uth.play_multiple", Texts.number(play))));
					colors.add(TEXT);
				} else {
					lines.add(Component.translatable("gui.burmaldaholic.uth.at_risk", Texts.number(worstCase(a, t) - (long) 0)));
					colors.add(GRAY);
				}
			}
		} else if (seated() && betting() && !iAmBanker()) {
			long a = confirmedBet() ? st.getLongOr("my_ante", 0) : ante;
			long t = confirmedBet() ? st.getLongOr("my_trips", 0) : trips;
			lines.add(betsLine(a, t, 0));
			colors.add(confirmedBet() ? TEXT : GOLD);
			lines.add(Component.translatable("gui.burmaldaholic.uth.at_risk", Texts.number(worstCase(a, t))));
			colors.add(GRAY);
			long maxW = maxW();
			long maxAnte = Math.max(0, (maxW - t) / 6);
			lines.add(Component.translatable("gui.burmaldaholic.uth.ante_limits", Texts.number(minBet()), Texts.number(maxAnte), Texts.number(maxW)));
			colors.add(GRAY);
			lines.add(Component.translatable("gui.burmaldaholic.uth.blind_equals_ante"));
			colors.add(GRAY);
		} else if (!seated() && !iAmBanker() && st.getListOrEmpty("players").isEmpty()) {
			boolean allow3x = st.getBooleanOr("allow3x", true);
			for (String k : new String[] {"1", allow3x ? "2" : "2_no3x", "3", "4", "5"}) {
				lines.add(Component.translatable("gui.burmaldaholic.uth.rules." + k));
				colors.add(GRAY);
			}
		}
		if (st.getBooleanOr("bots_virtual", false) && (st.contains("bots") || anyBotPlayer())) {
			lines.add(Component.translatable("gui.burmaldaholic.bots.virtual_tooltip"));
			colors.add(GRAY);
		}
		for (int i = 0; i < lines.size(); i++) {
			for (FormattedCharSequence l : font.split(lines.get(i), w)) {
				if (y + 9 > limit) {
					return;
				}
				g.text(font, l, x, y, colors.get(i), true);
				y += 10;
			}
		}
	}

	private Component betsLine(long a, long t, int play) {
		MutableComponent line = Component.empty();
		if (t > 0) {
			line.append(Component.translatable("gui.burmaldaholic.uth.trips_amount", Texts.number(t))).append(Texts.raw(" · "));
		}
		line.append(Component.translatable("gui.burmaldaholic.uth.ante_amount", Texts.number(a))).append(Texts.raw(" · "))
			.append(Component.translatable("gui.burmaldaholic.uth.blind_amount", Texts.number(a))).append(Texts.raw(" · "))
			.append(Component.translatable("gui.burmaldaholic.uth.play_amount", play > 0 ? Texts.number(play * a) : Texts.raw("—")));
		return line;
	}

	private void addResultLines(List<Component> lines, List<Integer> colors, CompoundTag res) {
		String outcome = res.getStringOr("outcome", "");
		String myHand = s().getStringOr("my_hand", "");
		switch (outcome) {
			case "win" -> lines.add(Component.translatable("gui.burmaldaholic.uth.result.win", handName(myHand)));
			case "lose" -> lines.add(Component.translatable("gui.burmaldaholic.uth.result.lose", handName(s().getStringOr("dealer_hand", ""))));
			case "tie" -> lines.add(Component.translatable("gui.burmaldaholic.uth.result.tie"));
			default -> lines.add(Component.translatable("gui.burmaldaholic.uth.result.folded"));
		}
		colors.add(YELLOW);
		MutableComponent bets = Component.empty();
		if (res.getLongOr("play", 0) > 0 || !"folded".equals(outcome)) {
			append(bets, betLine("gui.burmaldaholic.uth.play", res.getLongOr("play_net", 0), res.getLongOr("play", 0) == 0 && !"folded".equals(outcome)));
		}
		append(bets, betLine("gui.burmaldaholic.uth.ante", res.getLongOr("ante_net", 0), false));
		long blind = res.getLongOr("blind_net", 0);
		String payHand = res.getStringOr("pay_hand", "");
		if (blind > 0) {
			append(bets, Component.translatable("gui.burmaldaholic.uth.line.blind_bonus", handName(handOf(payHand)),
				ratio(res.getDoubleOr("blind_pay", 0)), Texts.number(blind)));
		} else {
			append(bets, betLine("gui.burmaldaholic.uth.blind", blind, false));
		}
		long tripsBet = res.getLongOr("trips", 0);
		if (tripsBet > 0) {
			long tn = res.getLongOr("trips_net", 0);
			if (tn > 0) {
				append(bets, Component.translatable("gui.burmaldaholic.uth.line.trips_bonus", handName(handOf(payHand)),
					ratio(res.getIntOr("trips_pay", 0)), Texts.number(tn)));
			} else {
				append(bets, betLine("gui.burmaldaholic.uth.trips", tn, false));
			}
		}
		lines.add(bets);
		colors.add(TEXT);
		long net = res.getLongOr("net", 0);
		lines.add(net > 0 ? Component.translatable("gui.burmaldaholic.common.result.win", Texts.number(net))
			: net < 0 ? Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(-net))
			: Component.translatable("gui.burmaldaholic.common.result.push"));
		colors.add(net > 0 ? GREEN : net < 0 ? RED : GRAY);
	}

	private static void append(MutableComponent line, Component part) {
		if (!line.getSiblings().isEmpty()) {
			line.append(Texts.raw(" · "));
		}
		line.append(part);
	}

	private static Component betLine(String nameKey, long net, boolean none) {
		Component name = Component.translatable(nameKey);
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.uth.line.win", name, Texts.number(net));
		}
		if (net < 0) {
			return Component.translatable("gui.burmaldaholic.uth.line.lose", name, Texts.number(-net));
		}
		return none ? Component.translatable("gui.burmaldaholic.uth.play_amount", Texts.raw("—"))
			: Component.translatable("gui.burmaldaholic.uth.line.push", name);
	}

	private static String handOf(String payKey) {
		for (PayHand h : PayHand.values()) {
			if (h.key().equals(payKey)) {
				return h.handName();
			}
		}
		return "";
	}

	/** "500:1", "1.5:1" (decimal separator per language). */
	private static Component ratio(double pay) {
		String plain = pay == Math.rint(pay) ? Long.toString((long) pay) : String.format(java.util.Locale.ROOT, "%.2f", pay).replaceAll("0+$", "");
		return Component.empty().append(Texts.decimal(plain)).append(Texts.raw(":1"));
	}

	/** "[BOT] Lucky Steve" from a bot name id. */
	private static Component botName(String nameId) {
		return Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(BotRoster.nameKey(nameId)));
	}

	private boolean anyBotPlayer() {
		ListTag players = s().getListOrEmpty("players");
		for (int i = 0; i < players.size(); i++) {
			if (players.getCompoundOrEmpty(i).getBooleanOr("bot", false)) {
				return true;
			}
		}
		return false;
	}

	private static Component handName(String name) {
		return name.isEmpty() ? Component.empty() : Component.translatable("gui.burmaldaholic.poker.hand." + name);
	}

	private void drawPaytable(GuiGraphicsExtractor g, int y) {
		int bottom = buttonsTop - 2;
		g.fill(PAD, y, imageWidth - PAD, bottom, OVERLAY);
		CompoundTag pays = s().getCompoundOrEmpty("pays");
		int colW = (imageWidth - 2 * PAD - 12) / 2;
		int x1 = PAD + 4;
		int x2 = x1 + colW + 4;
		int y1 = drawWrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.blind"), x1, y + 3, colW, GOLD, bottom);
		int y2 = drawWrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.trips"), x2, y + 3, colW, GOLD, bottom);
		for (PayHand h : PayHand.values()) {
			if (h == PayHand.NONE) {
				continue;
			}
			double bp = pays.getDoubleOr("blind_" + h.key(), 0);
			if (bp > 0) {
				y1 = drawWrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.row", handName(h.handName()), ratio(bp)), x1, y1, colW, TEXT, bottom);
			}
			int tp = pays.getIntOr("trips_" + h.key(), 0);
			if (tp > 0) {
				y2 = drawWrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.row", handName(h.handName()), ratio(tp)), x2, y2, colW, TEXT, bottom);
			}
		}
		y1 = drawWrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.blind_lower"), x1, y1, colW, GRAY, bottom);
		// "Normal: Costs this bot about 2.27 % of the Ante" per level seated (BOTS.md §4.5)
		ListTag levels = s().getListOrEmpty("bot_levels");
		for (int i = 0; i < levels.size(); i++) {
			BotDifficulty lvl = BotDifficulty.byId(levels.getStringOr(i, ""), BotDifficulty.NORMAL);
			Component line = Component.empty().append(Component.translatable(lvl.translationKey())).append(Texts.raw(": "))
				.append(Component.translatable("gui.burmaldaholic.bots.uth_edge", Texts.raw(UthBotPolicy.edgePercent(lvl))));
			y1 = drawWrapped(g, line, x1, y1 + (i == 0 ? 2 : 0), colW, GRAY, bottom);
		}
		if (playerBanked()) {
			CompoundTag b = bank();
			String pct = UthClientModule.trimPercent(b.getDoubleOr("rake", 0) * 100);
			drawWrapped(g, Component.translatable("gui.burmaldaholic.uth.pvp.rules.2", Texts.number(b.getLongOr("ante_factor", 505)),
				Texts.number(b.getLongOr("trips_factor", 50)), Texts.decimal(pct)), x2, y2 + 2, colW, GRAY, bottom);
		}
	}

	private int drawWrapped(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color, int bottom) {
		for (FormattedCharSequence l : font.split(text, w)) {
			if (y + 9 > bottom) {
				return y;
			}
			g.text(font, l, x, y, color, true);
			y += 10;
		}
		return y;
	}

	private void drawInfoLine(GuiGraphicsExtractor g) {
		int y = imageHeight - 11;
		Component bal = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		g.text(font, bal, PAD, y, GOLD, true);
		Component lim = limitsLine();
		int lx = imageWidth - PAD - font.width(lim);
		if (lx > PAD + font.width(bal) + 6) {
			g.text(font, lim, lx, y, GRAY, true);
		}
	}

	private void drawTrimmed(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
		if (font.width(text) <= w) {
			g.text(font, text, x, y, color, true);
			return;
		}
		String ellipsis = "…";
		FormattedCharSequence cut = net.minecraft.locale.Language.getInstance().getVisualOrder(
			font.substrByWidth(text, Math.max(0, w - font.width(ellipsis))));
		g.text(font, cut, x, y, color, true);
		g.text(font, Texts.raw(ellipsis), x + Math.max(0, w - font.width(ellipsis)), y, color, true);
	}

	private String trim(String name, int maxChars) {
		return name.length() <= Math.max(4, maxChars) ? name : name.substring(0, Math.max(4, maxChars));
	}

	// ---- cards ------------------------------------------------------------------------------------------

	private static Component rank(int card) {
		int r = UthCards.rank(card);
		if (r <= 9) {
			return Texts.raw(Integer.toString(r));
		}
		String id = switch (r) {
			case 10 -> "10";
			case 11 -> "j";
			case 12 -> "q";
			case 13 -> "k";
			default -> "a";
		};
		return Component.translatable("gui.burmaldaholic.card.rank." + id);
	}

	private static boolean red(int card) {
		int s = UthCards.suit(card);
		return s == 1 || s == 2;
	}

	private void drawCard(GuiGraphicsExtractor g, int x, int y, int w, int h, int card) {
		g.fill(x, y, x + w, y + h, CARD_FACE);
		g.outline(x, y, w, h, CARD_EDGE);
		int color = red(card) ? CARD_RED : CARD_BLACK;
		g.text(font, rank(card), x + 2, y + 2, color, false);
		Component suit = Texts.raw(SUITS[UthCards.suit(card)]);
		g.text(font, suit, x + w - font.width(suit) - 1, y + h - 9, color, false);
	}

	private void drawMiniCard(GuiGraphicsExtractor g, int x, int y, int w, int h, int card) {
		g.fill(x, y, x + w, y + h, CARD_FACE);
		int color = red(card) ? CARD_RED : CARD_BLACK;
		Component r = rank(card);
		g.text(font, r, x + 1, y + 2, color, false);
	}

	private void drawBack(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFFE8E8E8);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CARD_BACK);
		for (int yy = y + 3; yy < y + h - 2; yy += 4) {
			g.fill(x + 2, yy, x + w - 2, yy + 1, 0xFFB5475A);
		}
	}
}
