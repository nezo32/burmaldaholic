package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.poker.PokerText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Texas Hold'em screen (UI.md §5): oval table with seat plates (name, stack, last action, cards), board
 * and pots in the middle, dealer button, timer bar on the acting seat, and an action area at the bottom
 * (Fold / Check-Call / Raise-to + raise slider with ½ pot, ¾ pot, Pot, All-in). Not seated: stake
 * chooser and buy-in slider. Server-driven: everything shown comes from the synced state; buttons only
 * send actions. Button widths follow their (possibly Russian) labels and rows wrap (UI.md §0.1); texts
 * that could overflow are cut with "…" and show the full text as a tooltip.
 */
public class PokerScreen extends CasinoTableScreen {
	private static final int WIDE_W = 400, WIDE_H = 240, COMPACT_W = 320, COMPACT_H = 220;
	private static final int TABLE_FILL = 0xFF17492D, RAIL = 0xFF5A3A1A, PLATE = 0xCC101010, ACTIVE = 0xFFFFD34D,
		YOU = 0xFF7FC8FF, GOLD = 0xFFFFD27F, GRAY = 0xFFAAAAAA, CARD_FACE = 0xFFF8F8F0, CARD_BACK = 0xFF8B1E2B, RED = 0xFFD42A2A,
		BLACK = 0xFF151515, WIN = 0xFF55FF55;
	private static final int PLATE_H = 30, SMALL_W = 13, SMALL_H = 17, BOARD_W = 20, BOARD_H = 28, ROW_H = 20, GAP = 3;
	private static final String ELLIPSIS = "…";

	private final boolean compact;
	private boolean ready;
	private int clientTicks;
	private int stateTick;
	/** Stake the viewer picked in the chooser (not seated, table empty). */
	private @Nullable String pickedLevel;
	private boolean topUpMode;
	private long raiseTo = -1;
	private int raiseSeq = -1;
	private long buyIn = -1;
	private long topUp = -1;
	private @Nullable Component error;
	private int errorTicks;
	private int actionTop;
	private final List<Hover> hovers = new ArrayList<>();

	private record Hover(int x, int y, int w, int h, Component text) {}

	public PokerScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		this(menu, inventory, useCompact());
	}

	private PokerScreen(CasinoTableMenu menu, Inventory inventory, boolean compact) {
		super(menu, inventory, PokerText.gui("title"), compact ? COMPACT_W : WIDE_W, compact ? COMPACT_H : WIDE_H);
		this.compact = compact;
		this.titleLabelX = 6;
		this.titleLabelY = 5;
	}

	/** UI.md §0.2: compact mode below the wide layout's size (e.g. 427 × 240 at GUI scale 2). */
	private static boolean useCompact() {
		var window = Minecraft.getInstance().getWindow();
		return window.getGuiScaledWidth() < WIDE_W + 10 || window.getGuiScaledHeight() < WIDE_H + 6;
	}

	// ---- state helpers ------------------------------------------------------------------------------

	private CompoundTag s() {
		return state();
	}

	private boolean seated() {
		return s().getBooleanOr("seated", false);
	}

	private boolean live() {
		return s().getBooleanOr("live", false);
	}

	private @Nullable CompoundTag legal() {
		CompoundTag t = s().getCompoundOrEmpty("legal");
		return t.isEmpty() ? null : t;
	}

	/** True while the server says it is the viewer's turn (tests, narration). */
	public boolean isMyTurn() {
		return legal() != null;
	}

	private int mySeatIndex() {
		ListTag seats = s().getListOrEmpty("table");
		for (int i = 0; i < seats.size(); i++) {
			CompoundTag st = seats.getCompoundOrEmpty(i);
			if (st.getBooleanOr("you", false)) {
				return st.getIntOr("index", -1);
			}
		}
		return -1;
	}

	private Component decode(@Nullable Tag tag) {
		if (tag == null) {
			return Component.empty();
		}
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	private List<Component> components(String key) {
		ListTag list = s().getListOrEmpty(key);
		List<Component> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			out.add(decode(list.get(i)));
		}
		return out;
	}

	private @Nullable CompoundTag level(String id) {
		ListTag levels = s().getListOrEmpty("levels");
		for (int i = 0; i < levels.size(); i++) {
			CompoundTag l = levels.getCompoundOrEmpty(i);
			if (l.getStringOr("id", "").equals(id)) {
				return l;
			}
		}
		return null;
	}

	private @Nullable String levelId() {
		String fixed = s().getStringOr("stake", "");
		return fixed.isEmpty() ? pickedLevel : fixed;
	}

	/** Ticks left on a server timer, counted down locally between syncs; -1 if not running. */
	private long ticksLeft(String id) {
		long t = s().getCompoundOrEmpty("timers").getLongOr(id, -1);
		return t < 0 ? -1 : Math.max(0, t - (clientTicks - stateTick));
	}

	// ---- lifecycle ------------------------------------------------------------------------------------

	@Override
	protected void init() {
		super.init();
		ready = true;
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		stateTick = clientTicks;
		if (ready) {
			rebuild();
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		clientTicks++;
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	@Override
	public void showError(Component message) {
		this.error = message;
		this.errorTicks = 60;
	}

	// ---- widgets ----------------------------------------------------------------------------------------

	private Button btn(Component label, Button.OnPress onPress, @Nullable Component tooltip, boolean active) {
		Button b = Button.builder(label, onPress).bounds(0, 0, Math.max(40, font.width(label) + 10), ROW_H).build();
		if (tooltip != null) {
			b.setTooltip(Tooltip.create(tooltip));
		}
		b.active = active;
		return b;
	}

	/** Lays out the rows bottom-up above the error line, wrapping any row wider than the panel. */
	private void layout(List<List<AbstractWidget>> rows) {
		int maxW = imageWidth - 12;
		List<List<AbstractWidget>> wrapped = new ArrayList<>();
		for (List<AbstractWidget> row : rows) {
			List<AbstractWidget> cur = new ArrayList<>();
			int w = 0;
			for (AbstractWidget wd : row) {
				int add = (cur.isEmpty() ? 0 : GAP) + wd.getWidth();
				if (!cur.isEmpty() && w + add > maxW) {
					wrapped.add(cur);
					cur = new ArrayList<>();
					w = 0;
					add = wd.getWidth();
				}
				cur.add(wd);
				w += add;
			}
			if (!cur.isEmpty()) {
				wrapped.add(cur);
			}
		}
		actionTop = imageHeight - 13 - wrapped.size() * (ROW_H + 2);
		int y = actionTop;
		for (List<AbstractWidget> row : wrapped) {
			int total = -GAP;
			for (AbstractWidget wd : row) {
				total += wd.getWidth() + GAP;
			}
			int x = (imageWidth - total) / 2;
			for (AbstractWidget wd : row) {
				wd.setX(leftPos + x);
				wd.setY(topPos + y);
				addRenderableWidget(wd);
				x += wd.getWidth() + GAP;
			}
			y += ROW_H + 2;
		}
	}

	private void rebuild() {
		clearWidgets();
		List<List<AbstractWidget>> rows = new ArrayList<>();
		if (s().getBooleanOr("enabled", true)) {
			if (!seated() && s().getBooleanOr("waiting_seat", false)) {
				// claimant (BOTS.md §3.2): the buy-in is escrowed until a bot gives up its seat after this hand
				List<AbstractWidget> row = new ArrayList<>();
				row.add(btn(PokerText.gui("stand_up"), b -> sendAction("stand_up"), Component.translatable("msg.burmaldaholic.bots.seat_after_round"), true));
				rows.add(row);
			} else if (!seated()) {
				buildJoin(rows);
			} else if (legal() != null) {
				topUpMode = false;
				buildTurn(rows, legal());
			} else if (topUpMode) {
				buildTopUp(rows);
			} else {
				buildSeated(rows);
			}
		}
		layout(rows);
	}

	private void buildJoin(List<List<AbstractWidget>> rows) {
		String fixed = s().getStringOr("stake", "");
		String levelId = levelId();
		int vip = s().getIntOr("vip", 0);
		if (levelId == null) {
			List<AbstractWidget> row = new ArrayList<>();
			ListTag levels = s().getListOrEmpty("levels");
			for (int i = 0; i < levels.size(); i++) {
				CompoundTag l = levels.getCompoundOrEmpty(i);
				String id = l.getStringOr("id", "");
				int minTier = l.getIntOr("min_tier", 0);
				boolean ok = vip >= minTier;
				Component label = PokerText.gui("stakes." + id, Texts.number(l.getLongOr("sb", 1)), Texts.number(l.getLongOr("bb", 2)));
				row.add(btn(label, b -> {
					pickedLevel = id;
					buyIn = -1;
					rebuild();
				}, ok ? null : Component.translatable("gui.burmaldaholic.common.requires_vip", VipTiers.name(minTier)), ok));
			}
			rows.add(row);
			return;
		}
		CompoundTag l = level(levelId);
		if (l == null) {
			return;
		}
		long min = l.getLongOr("buy_min", 0);
		long max = l.getLongOr("buy_max", 0);
		long bb = Math.max(1, l.getLongOr("bb", 2));
		boolean can = max >= min && max > 0 && vip >= l.getIntOr("min_tier", 0);
		List<AbstractWidget> row = new ArrayList<>();
		if (can) {
			if (buyIn < min || buyIn > max) {
				buyIn = max;
			}
			AmountSlider slider = new AmountSlider(compact ? 130 : 150, min, max, bb, buyIn,
				v -> PokerText.gui("buy_in").append(Texts.raw(": ")).append(Texts.number(v)), v -> buyIn = v);
			row.add(slider);
			row.add(btn(Component.translatable("gui.burmaldaholic.common.min"), b -> slider.setAmount(min), null, true));
			row.add(btn(Component.translatable("gui.burmaldaholic.common.max"), b -> slider.setAmount(max), null, true));
		}
		List<AbstractWidget> row2 = new ArrayList<>();
		String id = levelId;
		row2.add(btn(PokerText.gui("sit_down"), b -> {
			CompoundTag args = new CompoundTag();
			args.putString("level", id);
			args.putLong("amount", buyIn);
			sendAction("buy_in", args);
		}, rakeInfo(l), can));
		if (fixed.isEmpty()) {
			row2.add(btn(Component.translatable("gui.burmaldaholic.common.back"), b -> {
				pickedLevel = null;
				rebuild();
			}, null, true));
		}
		rows.add(row);
		rows.add(row2);
	}

	private Component rakeInfo(CompoundTag level) {
		long permille = s().getLongOr("rake_permille", 50);
		String pct = permille % 10 == 0 ? Long.toString(permille / 10) : (permille / 10) + "." + (permille % 10);
		return PokerText.gui("rake_info", Texts.raw(pct), Texts.number(level.getLongOr("rake_cap", 0)));
	}

	private void buildSeated(List<List<AbstractWidget>> rows) {
		List<AbstractWidget> row = new ArrayList<>();
		boolean out = s().getBooleanOr("sitting_out", false);
		row.add(btn(PokerText.gui(out ? "sit_in" : "sit_out"), b -> sendAction(out ? "sit_in" : "sit_out"), null, true));
		boolean canTop = s().getBooleanOr("can_top_up", false) && s().getLongOr("top_max", 0) > 0;
		row.add(btn(PokerText.gui("top_up"), b -> {
			topUpMode = true;
			topUp = -1;
			rebuild();
		}, null, canTop));
		row.add(btn(PokerText.gui("stand_up"), b -> sendAction("stand_up"), null, !s().getBooleanOr("leaving", false)));
		rows.add(row);
	}

	private void buildTopUp(List<List<AbstractWidget>> rows) {
		long max = s().getLongOr("top_max", 0);
		if (max <= 0 || !s().getBooleanOr("can_top_up", false)) {
			topUpMode = false;
			buildSeated(rows);
			return;
		}
		if (topUp < 1 || topUp > max) {
			topUp = max;
		}
		List<AbstractWidget> row = new ArrayList<>();
		row.add(new AmountSlider(compact ? 130 : 150, 1, max, 1, topUp,
			v -> PokerText.gui("top_up").append(Texts.raw(": ")).append(Texts.number(v)), v -> topUp = v));
		row.add(btn(PokerText.gui("top_up"), b -> {
			CompoundTag args = new CompoundTag();
			args.putLong("amount", topUp);
			sendAction("top_up", args);
			topUpMode = false;
		}, null, true));
		row.add(btn(Component.translatable("gui.burmaldaholic.common.back"), b -> {
			topUpMode = false;
			rebuild();
		}, null, true));
		rows.add(row);
	}

	private void sendAct(String kind, long to, int seq) {
		CompoundTag args = new CompoundTag();
		args.putString("kind", kind);
		args.putLong("to", to);
		args.putInt("seq", seq);
		sendAction("act", args);
	}

	private void buildTurn(List<List<AbstractWidget>> rows, CompoundTag l) {
		int seq = l.getIntOr("seq", 0);
		long toCall = l.getLongOr("to_call", 0);
		boolean canCheck = l.getBooleanOr("can_check", false);
		boolean canRaise = l.getBooleanOr("can_raise", false);
		long minTo = l.getLongOr("min_to", 0);
		long maxTo = l.getLongOr("max_to", 0);
		boolean isBet = l.getBooleanOr("is_bet", false);
		long myStack = l.getLongOr("my_stack", 0);
		long myBet = l.getLongOr("my_bet", 0);
		long currentBet = l.getLongOr("current_bet", 0);
		long pot = s().getLongOr("pot_total", 0);
		long bb = Math.max(1, s().getLongOr("bb", 2));
		List<AbstractWidget> row = new ArrayList<>();
		if (!canCheck) {
			row.add(btn(PokerText.gui("fold"), b -> sendAct("fold", 0, seq), PokerText.gui("fold.tooltip"), true));
		}
		if (canCheck) {
			row.add(btn(PokerText.gui("check"), b -> sendAct("check", 0, seq), null, true));
		} else if (myStack <= toCall) {
			row.add(btn(PokerText.gui("all_in", Texts.number(myBet + myStack)), b -> sendAct("call", 0, seq), null, true));
		} else {
			row.add(btn(PokerText.gui("call", Texts.number(toCall)), b -> sendAct("call", 0, seq), null, true));
		}
		rows.add(row);
		if (!canRaise) {
			return;
		}
		if (raiseSeq != seq || raiseTo < minTo || raiseTo > maxTo) {
			raiseSeq = seq;
			raiseTo = minTo;
		}
		Button confirm = btn(raiseLabel(raiseTo, maxTo, isBet), b -> {
			if (raiseTo >= maxTo) {
				sendAct("all_in", 0, seq);
			} else {
				sendAct("raise", raiseTo, seq);
			}
		}, null, true);
		// Reserve room for the widest label this button can take, so the row does not jump.
		confirm.setWidth(Math.max(confirm.getWidth(), Math.max(font.width(raiseLabel(maxTo, maxTo, isBet)),
			font.width(raiseLabel(Math.max(minTo, maxTo - 1), maxTo, isBet))) + 10));
		row.add(confirm);
		List<AbstractWidget> row2 = new ArrayList<>();
		AmountSlider slider = new AmountSlider(compact ? 76 : 110, minTo, maxTo, bb, raiseTo, Texts::number, v -> {
			raiseTo = v;
			confirm.setMessage(raiseLabel(v, maxTo, isBet));
		});
		row2.add(slider);
		String[] keys = {"half_pot", "three_quarter_pot", "pot_size"};
		double[] fractions = {0.5, 0.75, 1.0};
		for (int k = 0; k < keys.length; k++) {
			long to = currentBet == 0 ? Math.round(pot * fractions[k]) : Math.round(currentBet + fractions[k] * (pot + toCall));
			long clamped = Math.max(minTo, Math.min(maxTo, to));
			row2.add(btn(PokerText.gui(keys[k]), b -> slider.setAmount(clamped), Texts.number(clamped), true));
		}
		row2.add(btn(PokerText.gui("all_in", Texts.number(maxTo)), b -> slider.setAmount(maxTo), null, true));
		rows.add(row2);
	}

	private static Component raiseLabel(long to, long maxTo, boolean isBet) {
		if (to >= maxTo) {
			return PokerText.gui("all_in", Texts.number(maxTo));
		}
		return isBet ? PokerText.gui("bet").append(Texts.raw(" ")).append(Texts.number(to)) : PokerText.gui("raise_to", Texts.number(to));
	}

	/** Slider over [min, max] in steps (bb), label from the current amount. */
	private final class AmountSlider extends AbstractSliderButton {
		private final long min;
		private final long max;
		private final long step;
		private final LongFunction<Component> label;
		private final LongConsumer onChange;

		AmountSlider(int width, long min, long max, long step, long initial, LongFunction<Component> label, LongConsumer onChange) {
			super(0, 0, width, ROW_H, Component.empty(), 0);
			this.min = min;
			this.max = Math.max(min, max);
			this.step = Math.max(1, step);
			this.label = label;
			this.onChange = onChange;
			setAmountSilently(initial);
		}

		long amount() {
			if (max <= min || value >= 1.0) {
				return max;
			}
			long raw = min + Math.round(value * (max - min));
			long snapped = min + Math.round((raw - min) / (double) step) * step;
			return Math.max(min, Math.min(max, snapped));
		}

		private void setAmountSilently(long a) {
			value = max <= min ? 1.0 : Math.max(0, Math.min(1, (a - min) / (double) (max - min)));
			updateMessage();
		}

		void setAmount(long a) {
			setAmountSilently(a);
			applyValue();
		}

		@Override
		protected void updateMessage() {
			setMessage(label.apply(amount()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(amount());
		}
	}

	// ---- geometry ---------------------------------------------------------------------------------------

	private int plateW() {
		return compact ? 92 : 100;
	}

	private int cx() {
		return imageWidth / 2;
	}

	private int cy() {
		return compact ? 82 : 92;
	}

	private int rx() {
		return (imageWidth - plateW()) / 2 - 4;
	}

	private int ry() {
		return compact ? 52 : 62;
	}

	/** Centre of a seat plate; the viewer's seat is always at the bottom. */
	private int[] seatPos(int index, int size, int mySeat) {
		int n = Math.max(1, size);
		int rel = Math.floorMod(index - Math.max(0, mySeat), n);
		double angle = Math.PI / 2 + rel * 2 * Math.PI / n;
		return new int[] {cx() + (int) Math.round(rx() * Math.cos(angle)), cy() + (int) Math.round(ry() * Math.sin(angle))};
	}

	// ---- rendering ------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = leftPos + cx();
		int y = topPos + cy();
		int erx = rx() - plateW() / 4;
		int ery = ry() - 12;
		ellipse(g, x, y, erx + 4, ery + 4, RAIL);
		ellipse(g, x, y, erx, ery, TABLE_FILL);
	}

	private static void ellipse(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color) {
		for (int dy = -ry; dy <= ry; dy++) {
			double f = 1 - (dy * dy) / (double) (ry * ry);
			int hw = (int) Math.round(rx * Math.sqrt(Math.max(0, f)));
			g.fill(cx - hw, cy + dy, cx + hw, cy + dy + 1, color);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hovers.clear();
		super.extractRenderState(g, mouseX, mouseY, a);
		for (Hover h : hovers) {
			int x = leftPos + h.x();
			int y = topPos + h.y();
			if (mouseX >= x && mouseX < x + h.w() && mouseY >= y && mouseY < y + h.h()) {
				g.setTooltipForNextFrame(font, font.split(h.text(), 200), mouseX, mouseY);
				break;
			}
		}
	}

	/** Draws {@code text} left-aligned, cut with "…" to {@code maxW} (full text as tooltip). */
	private void fitText(GuiGraphicsExtractor g, Component text, int x, int y, int maxW, int color, boolean shadow) {
		if (font.width(text) <= maxW) {
			g.text(font, text, x, y, color, shadow);
			return;
		}
		String cut = font.plainSubstrByWidth(text.getString(), Math.max(0, maxW - font.width(ELLIPSIS))) + ELLIPSIS;
		g.text(font, cut, x, y, color, shadow);
		hovers.add(new Hover(x, y, maxW, 9, text));
	}

	/** Centred version of {@link #fitText}. */
	private void fitCentered(GuiGraphicsExtractor g, Component text, int y, int maxW, int color) {
		int w = Math.min(maxW, font.width(text));
		fitText(g, text, cx() - w / 2, y, maxW, color, true);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		CompoundTag st = s();
		MutableComponent info = Component.empty();
		if (st.contains("bb")) {
			info.append(PokerText.gui("blinds", Texts.number(st.getLongOr("sb", 1)), Texts.number(st.getLongOr("bb", 2))));
			int handNo = st.getIntOr("hand_no", 0);
			if (handNo > 0) {
				info.append(Texts.raw(" · #")).append(Texts.number(handNo));
			}
			String street = st.getStringOr("street", "");
			if (!street.isEmpty()) {
				info.append(Texts.raw(" · ")).append(PokerText.gui(street));
			}
		}
		int titleW = Math.min(font.width(title), imageWidth / 2 - 10);
		fitText(g, title, titleLabelX, titleLabelY, imageWidth / 2 - 10, TEXT, true);
		int infoMax = imageWidth - titleW - 20;
		int infoW = Math.min(infoMax, font.width(info));
		fitText(g, info, imageWidth - 6 - infoW, 5, infoMax, GRAY, true);
		List<Component> log = components("log");
		if (!log.isEmpty()) {
			// The whole hand log is a tooltip on the "History" label.
			Component hist = Component.translatable("gui.burmaldaholic.common.history");
			int hw = font.width(hist);
			int hx = imageWidth - 6 - hw;
			g.text(font, hist, hx, 15, 0xFF8FB8FF, true);
			MutableComponent all = Component.empty();
			for (int i = 0; i < log.size(); i++) {
				if (i > 0) {
					all.append(Texts.raw("\n"));
				}
				all.append(log.get(i));
			}
			hovers.add(new Hover(hx, 15, hw, 9, all));
		}
		if (!st.getBooleanOr("enabled", true)) {
			fitCentered(g, Component.translatable("gui.burmaldaholic.error.disabled"), cy(), imageWidth - 20, ERROR);
		} else {
			drawSeats(g);
			drawCenter(g);
			drawStatus(g);
		}
		if (error != null) {
			fitCentered(g, error, imageHeight - 11, imageWidth - 12, ERROR);
		}
	}

	private void drawSeats(GuiGraphicsExtractor g) {
		CompoundTag st = s();
		int size = Math.max(2, st.getIntOr("table_size", 6));
		int me = mySeatIndex();
		int toAct = st.getIntOr("to_act", -1);
		int button = st.getIntOr("button", -1);
		ListTag seats = st.getListOrEmpty("table");
		boolean[] taken = new boolean[size];
		for (int i = 0; i < seats.size(); i++) {
			CompoundTag seat = seats.getCompoundOrEmpty(i);
			int index = seat.getIntOr("index", 0);
			if (index >= 0 && index < size) {
				taken[index] = true;
				drawSeat(g, seat, seatPos(index, size, me), index == toAct, index == button && st.getIntOr("hand_no", 0) > 0);
			}
		}
		int pw = plateW();
		for (int i = 0; i < size; i++) {
			if (!taken[i]) {
				int[] p = seatPos(i, size, me);
				int x = p[0] - pw / 2;
				int y = p[1] - PLATE_H / 2;
				g.fill(x, y, x + pw, y + PLATE_H, 0x55101010);
				Component empty = Component.translatable("gui.burmaldaholic.common.seat_empty");
				int w = Math.min(pw - 6, font.width(empty));
				fitText(g, empty, p[0] - w / 2, y + 11, pw - 6, 0xFF777777, false);
			}
		}
	}

	private void drawSeat(GuiGraphicsExtractor g, CompoundTag seat, int[] pos, boolean acting, boolean dealer) {
		boolean live = live();
		int pw = plateW();
		int x = pos[0] - pw / 2;
		int y = pos[1] - PLATE_H / 2;
		boolean you = seat.getBooleanOr("you", false);
		boolean folded = seat.getBooleanOr("folded", false);
		g.fill(x, y, x + pw, y + PLATE_H, PLATE);
		if (acting) {
			g.outline(x - 1, y - 1, pw + 2, PLATE_H + 2, ACTIVE);
		} else if (you) {
			g.outline(x - 1, y - 1, pw + 2, PLATE_H + 2, YOU);
		}
		// hole cards inside the plate (right side); the viewer's own cards are drawn larger outside
		int[] cards = seat.getIntArray("cards").orElse(new int[0]);
		int hidden = seat.getIntOr("hidden", 0);
		int n = cards.length > 0 ? cards.length : hidden;
		int textW = pw - 6;
		if (n > 0 && !you) {
			int cx0 = x + pw - 2 - n * (SMALL_W + 1);
			for (int k = 0; k < n; k++) {
				int xk = cx0 + k * (SMALL_W + 1);
				if (cards.length > 0) {
					drawCard(g, xk, y + 2, SMALL_W, SMALL_H, cards[k]);
				} else {
					drawBack(g, xk, y + 2, SMALL_W, SMALL_H);
				}
			}
			textW = pw - 6 - n * (SMALL_W + 1);
		} else if (n > 0) {
			int cw = compact ? 16 : BOARD_W;
			int ch = compact ? 24 : BOARD_H;
			for (int k = 0; k < n; k++) {
				int xk = x + pw + 3 + k * (cw + 2);
				if (cards.length > 0) {
					drawCard(g, xk, y + PLATE_H - ch, cw, ch, cards[k]);
				} else {
					drawBack(g, xk, y + PLATE_H - ch, cw, ch);
				}
			}
		}
		Component name = you ? Component.translatable("gui.burmaldaholic.common.you") : decode(seat.get("name"));
		fitText(g, name, x + 3, y + 2, textW, folded && live ? GRAY : TEXT, false);
		// line 2: stack (+ net result after the hand)
		g.text(font, Texts.number(seat.getLongOr("stack", 0)), x + 3, y + 11, GOLD, false);
		if (!live && seat.contains("net")) {
			long net = seat.getLongOr("net", 0);
			if (net != 0) {
				Component c = Texts.raw(net > 0 ? "+" : "−").append(Texts.number(Math.abs(net)));
				g.text(font, c, x + 3 + textW - font.width(c), y + 11, net > 0 ? WIN : ERROR, false);
			}
		}
		// line 3: status / last action / shown hand
		Component line3 = null;
		int color3 = TEXT;
		if (seat.getBooleanOr("out", false)) {
			line3 = PokerText.gui("sitting_out");
			color3 = GRAY;
		} else if (live && folded) {
			line3 = PokerText.gui("folded");
			color3 = GRAY;
		} else if (!live && seat.contains("hand")) {
			line3 = decode(seat.get("hand"));
			color3 = seat.getLongOr("won", 0) > 0 ? WIN : TEXT;
		} else if (live && seat.contains("act")) {
			line3 = decode(seat.get("act"));
			color3 = seat.getBooleanOr("all_in", false) ? ACTIVE : GOLD;
		} else if (live && seat.getBooleanOr("all_in", false)) {
			line3 = PokerText.gui("all_in_tag");
			color3 = ACTIVE;
		}
		if (line3 != null) {
			fitText(g, line3, x + 3, y + 20, pw - 6, color3, false);
		}
		if (dealer) {
			int dx = x - 9;
			int dy = y + PLATE_H / 2 - 3;
			g.fill(dx, dy, dx + 7, dy + 7, 0xFFFFFFFF);
			g.outline(dx - 1, dy - 1, 9, 9, BLACK);
			hovers.add(new Hover(dx - 1, dy - 1, 9, 9, PokerText.gui("button")));
		}
		if (acting) {
			long left = ticksLeft("action");
			if (left >= 0) {
				int total = Math.max(1, CasinoConfig.poker().actionTimerTicks);
				int w = (int) Math.round((pw - 2) * Math.min(1.0, left / (double) total));
				g.fill(x + 1, y + PLATE_H - 2, x + 1 + w, y + PLATE_H - 1, left < 100 ? ERROR : ACTIVE);
			}
		}
	}

	private void drawCard(GuiGraphicsExtractor g, int x, int y, int w, int h, int card) {
		g.fill(x, y, x + w, y + h, CARD_FACE);
		g.outline(x, y, w, h, 0xFF404040);
		int color = PokerText.red(card) ? RED : BLACK;
		Component rank = PokerText.rank(card);
		Component suit = PokerText.suit(card);
		g.text(font, rank, x + 2, y + 1, color, false);
		if (h >= 24) {
			g.text(font, suit, x + w / 2 - font.width(suit) / 2, y + h / 2 + 1, color, false);
		} else {
			g.text(font, suit, x + w - font.width(suit) - 1, y + h - 9, color, false);
		}
		hovers.add(new Hover(x, y, w, h, PokerText.cardNarration(card)));
	}

	private void drawBack(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFFE8E8E8);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CARD_BACK);
		for (int yy = y + 3; yy < y + h - 2; yy += 4) {
			g.fill(x + 3, yy, x + w - 3, yy + 1, 0xFFB5475A);
		}
	}

	/** Board, pots and the centre message. Lines sit in the band between the upper and lower side seats. */
	private void drawCenter(GuiGraphicsExtractor g) {
		CompoundTag st = s();
		int[] board = st.getIntArray("board").orElse(new int[0]);
		int cw = compact ? 17 : BOARD_W;
		int ch = compact ? 24 : BOARD_H;
		int gap = compact ? 2 : 3;
		int bw = 5 * cw + 4 * gap;
		int bx = cx() - bw / 2;
		int by = cy() - 36;
		boolean live = live();
		for (int k = 0; k < 5; k++) {
			int x = bx + k * (cw + gap);
			if (k < board.length) {
				drawCard(g, x, by, cw, ch, board[k]);
			} else if (live) {
				g.outline(x, by, cw, ch, 0x55FFFFFF);
			}
		}
		int ty = by + ch + 3;
		int maxW = imageWidth - 16;
		long[] pots = st.getLongArray("pots").orElse(new long[0]);
		if (pots.length > 0) {
			MutableComponent line = PokerText.gui("pot", Texts.number(pots[0]));
			for (int i = 1; i < pots.length; i++) {
				line.append(Texts.raw(" · ")).append(PokerText.gui("side_pot", Texts.number(i), Texts.number(pots[i])));
			}
			fitCentered(g, line, ty, maxW, GOLD);
			return;
		}
		if (!seated()) {
			CompoundTag l = levelId() == null ? null : level(levelId());
			Component msg = l == null ? PokerText.gui("choose_stakes")
				: PokerText.gui("buy_in_range", Texts.number(l.getLongOr("full_min", 0)), Texts.number(l.getLongOr("full_max", 0)));
			fitCentered(g, msg, ty, maxW, TEXT);
		} else if (!live && components("result").isEmpty()) {
			boolean alone = st.getListOrEmpty("table").size() < 2;
			fitCentered(g, alone ? Component.translatable("gui.burmaldaholic.common.waiting_players") : PokerText.gui("waiting_hand"), ty, maxW, GRAY);
		}
	}

	/** One line above the buttons: your move + timer, results of the last hand, or balance while joining. */
	private void drawStatus(GuiGraphicsExtractor g) {
		CompoundTag st = s();
		Component status = null;
		int color = TEXT;
		CompoundTag l = legal();
		List<Component> result = components("result");
		if (l != null) {
			MutableComponent c = PokerText.gui("your_turn").copy();
			long toCall = l.getLongOr("to_call", 0);
			MutableComponent withCall = c.copy();
			if (toCall > 0) {
				withCall.append(Texts.raw(" · ")).append(PokerText.gui("to_call", Texts.number(toCall)));
			}
			long left = ticksLeft("action");
			if (left >= 0) {
				Component auto = PokerText.gui(l.getBooleanOr("can_check", false) ? "check" : "fold");
				Component timer = Component.translatable("gui.burmaldaholic.common.auto_action", auto,
					Texts.plural("unit.burmaldaholic.second_acc", (left + 19) / 20));
				c.append(Texts.raw(" · ")).append(timer);
				withCall.append(Texts.raw(" · ")).append(timer);
			}
			// "To call" is also on the Call button: drop it first when the line is too long (Russian).
			status = font.width(withCall) <= imageWidth - 12 ? withCall : c;
			color = WIN;
		} else if (!live() && !result.isEmpty()) {
			MutableComponent all = Component.empty();
			for (int i = 0; i < result.size(); i++) {
				if (i > 0) {
					all.append(Texts.raw("\n"));
				}
				all.append(result.get(i));
			}
			fitCentered(g, result.get(0), actionTop - 11, imageWidth - 12, WIN);
			if (result.size() > 1) {
				hovers.add(new Hover(6, actionTop - 11, imageWidth - 12, 9, all));
			}
			return;
		} else if (seated()) {
			if (st.getBooleanOr("leaving", false)) {
				status = PokerText.msg("leaving_after_hand");
				color = GRAY;
			} else if (st.getBooleanOr("sitting_out", false)) {
				status = PokerText.gui("sitting_out");
				color = GRAY;
			}
		} else if (levelId() != null) {
			status = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
			color = GRAY;
		}
		if (status != null) {
			fitCentered(g, status, actionTop - 11, imageWidth - 12, color);
		}
	}
}
