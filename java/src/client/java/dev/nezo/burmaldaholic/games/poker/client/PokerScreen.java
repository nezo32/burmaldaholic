package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.table.cards.CardAnimator;
import dev.nezo.burmaldaholic.client.table.cards.CardSprites;
import dev.nezo.burmaldaholic.client.table.cards.TableTheme;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.SeatPlate;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Felt;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.poker.PokerText;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBeats;
import dev.nezo.burmaldaholic.games.poker.present.PokerFlow;
import dev.nezo.burmaldaholic.games.poker.present.PokerLayout;
import dev.nezo.burmaldaholic.games.poker.present.PokerView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
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
 * Texas Hold'em, table UI v2 (REDESIGN.md §7.3, mockups {@code v2_poker_turn_480/640.png}; docs/architecture/ui-v2.md):
 * the screen is the table — the oval felt in the PlayArea, the board and the pot in the middle, the viewer's hero cards
 * at the bottom centre (plate left, stack above it, hand strength and bet right), the other seats clockwise around the
 * rail (plate + stack / last action, two cards inside the rail, bet toward the centre, the dealer button), and the Dock:
 * status + timer, Fold · Check / Call · the amount stepper ({@code −  40  +}, scroll, click for presets) · the gold
 * primary (Raise to / Bet / All-in / Call / Check; Buy in; I'm back; or a disabled "Waiting" that says why).
 *
 * <p>Layout is pure ({@link PokerLayout}), gates are pure ({@link PokerFlow}). Motion follows the server's
 * {@link PokerBeats} segments ({@code fx}): hole cards arc from the centre, streets slide and flip, the showdown lifts
 * the best five, the pot slides to the winners. Only PUBLISHED cards are drawn (the server sends a board card on its
 * slide beat; others' hole cards only when shown), so nothing leaks. Actions: {@code buy_in {level, amount}},
 * {@code act {kind, to, seq}}, {@code sit_out}, {@code sit_in}, {@code top_up {amount}}, {@code stand_up}.
 */
public class PokerScreen extends V2TableScreen<PokerView> {
	private static final int[] NO_CARDS = new int[0];
	public static final String STEP_MINUS = "stepper.minus", STEP_PLUS = "stepper.plus";
	private static final String STEPPER = "~stepper";
	private static final int KEY_UP = 265, KEY_DOWN = 264;
	private boolean rulesOpen;

	private final CardAnimator cards = new CardAnimator();
	private final Flow<PokerView> flow = new Flow<>() {
		@Override
		public Gate primary(PokerView v) {
			return PokerFlow.primary(v);
		}

		@Override
		public List<Gate> actions(PokerView v) {
			List<Gate> out = new ArrayList<>(PokerFlow.actions(v));
			PokerFlow.stepper(v).ifPresent(s -> out.add(new Gate(STEPPER, PokerFlow.V2 + "stepper", new Object[] {s.value()}, true, null, null)));
			return out;
		}
	};

	// ---- local choices --------------------------------------------------------------------------------------------
	private @Nullable String pickedLevel;
	private long buyIn = -1;
	private boolean topUpForm;
	private long topUp = -1;
	private long raiseTo = -1;
	private int raiseSeq = -1;
	private int clientTicks;
	private int stateTick;

	// ---- the parsed state -----------------------------------------------------------------------------------------
	private final List<SeatView> seats = new ArrayList<>();
	private int size = 6;
	private int me = -1;
	private int toAct = -1;
	private int button = -1;
	private int handNo;
	private boolean live;
	private boolean presenting;
	private int[] board = NO_CARDS;
	private long[] pots = new long[0];
	private long potTotal;
	private final List<Award> awards = new ArrayList<>();
	private List<Component> results = List.of();
	private PokerLayout.@Nullable Layout layout;
	private @Nullable CompoundTag parsed;

	// ---- presentation ---------------------------------------------------------------------------------------------
	private int fxSeq = -1;
	private String fxKind = "";
	private long fxStart;
	private int[] fxArgs = NO_CARDS;
	private @Nullable Timeline fx;
	private boolean fxDone = true;
	private long[] gatherBets = new long[0];
	private long[] lastBets = new long[0];
	private int lastActionN = -1;
	private int lastActionSeat = -1;
	private double lastActionAt = Double.NaN;
	private Component lastActionText = Component.empty();
	private int celebratedHand = -1;
	private int bannerHand = -1;
	private int soundedAward = -1;

	private record Award(int pot, long amount, int[] seats, long[] shares) {}

	private static final class SeatView {
		int index;
		Component name = Component.empty();
		boolean you;
		boolean bot;
		boolean folded;
		boolean allIn;
		boolean out;
		boolean mucked;
		boolean dealt;
		int handIndex = -1;
		long stack;
		long bet;
		long won;
		long net;
		@Nullable String tier;
		int[] cards = NO_CARDS;
		int hidden;
		@Nullable Component hand;
		@Nullable Component act;
		int best = -1;
	}

	public PokerScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, PokerText.gui("title"));
	}

	// ---- GameTest hooks (kept from the v1 screen) --------------------------------------------------------------------

	/** True while the server says it is the viewer's turn. */
	public boolean isMyTurn() {
		return legal() != null;
	}

	/** The presentation segment playing now ("deal" / "street" / "finish"). */
	public String fxKind() {
		return fxKind;
	}

	/** The board cards drawn (published) now. */
	public int boardShown() {
		return board.length;
	}

	/** The current pure layout (tests read seat boxes from it). */
	public PokerLayout.@Nullable Layout layout() {
		return layout;
	}

	// ---- state ------------------------------------------------------------------------------------------------------

	private CompoundTag s() {
		return state();
	}

	private @Nullable CompoundTag legal() {
		CompoundTag t = s().getCompoundOrEmpty("legal");
		return t.isEmpty() ? null : t;
	}

	private Component decode(@Nullable Tag tag) {
		if (tag == null) return Component.empty();
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	private long ticksLeft(String id) {
		long t = s().getCompoundOrEmpty("timers").getLongOr(id, -1);
		return t < 0 ? -1 : Math.max(0, t - (clientTicks - stateTick));
	}

	private double nowMs() {
		return AnimClock.levelMs(minecraft == null ? 0 : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Override
	protected PokerView view(CompoundTag st) {
		if (st != parsed) {
			parsed = st;
			parse(st); // a new state from the server (local re-builds keep the timers' baseline)
		}
		List<PokerView.Level> levels = new ArrayList<>();
		ListTag lt = st.getListOrEmpty("levels");
		int vip = st.getIntOr("vip", 0);
		for (int i = 0; i < lt.size(); i++) {
			CompoundTag l = lt.getCompoundOrEmpty(i);
			int minTier = l.getIntOr("min_tier", 0);
			levels.add(new PokerView.Level(l.getStringOr("id", ""), l.getLongOr("sb", 1), l.getLongOr("bb", 2), l.getLongOr("buy_min", 0),
				l.getLongOr("buy_max", 0), minTier, vip >= minTier));
		}
		String fixed = st.getStringOr("stake", "");
		String chosen = fixed.isEmpty() ? pickedLevel : fixed;
		PokerView.Level level = null;
		for (PokerView.Level l : levels) if (l.id().equals(chosen)) level = l;
		if (level == null && levels.size() == 1) level = levels.get(0);
		if (level != null && (buyIn < level.buyMin() || buyIn > level.buyMax())) buyIn = level.buyMax();
		PokerView.Seat seat = st.getBooleanOr("seated", false) ? PokerView.Seat.SEATED
			: st.getBooleanOr("waiting_seat", false) ? PokerView.Seat.QUEUED : PokerView.Seat.NONE;
		long topMax = st.getLongOr("top_max", 0);
		if (topUp < 1 || topUp > topMax) topUp = topMax;
		PokerView.Turn turn = null;
		CompoundTag l = legal();
		if (l != null) {
			int seq = l.getIntOr("seq", 0);
			long minTo = l.getLongOr("min_to", 0);
			long maxTo = l.getLongOr("max_to", 0);
			if (raiseSeq != seq || raiseTo < minTo || raiseTo > maxTo) {
				raiseSeq = seq;
				raiseTo = minTo;
			}
			turn = new PokerView.Turn(seq, l.getLongOr("to_call", 0), l.getBooleanOr("can_check", false), l.getBooleanOr("can_raise", false), minTo, maxTo,
				l.getBooleanOr("is_bet", false), l.getLongOr("my_stack", 0), l.getLongOr("my_bet", 0), l.getLongOr("current_bet", 0), potTotal,
				Math.max(1, st.getLongOr("bb", 2)), raiseTo);
		}
		if (seat != PokerView.Seat.SEATED) topUpForm = false;
		String waiting = "";
		SeatView acting = seat(toAct);
		if (live && acting != null && !acting.you) waiting = acting.name.getString();
		long next = ticksLeft("next_hand");
		long action = ticksLeft("action");
		return new PokerView(st.getBooleanOr("enabled", true), seat, !fixed.isEmpty(), levels, level, buyIn, balance(),
			st.getBooleanOr("sitting_out", false), st.getBooleanOr("leaving", false), st.getBooleanOr("can_top_up", false), topMax, topUpForm, topUp,
			turn, seats.size(), live, presenting, waiting, (int) ((action >= 0 ? action : next) + 19) / 20, next < 0 ? -1 : (int) ((next + 19) / 20));
	}

	/** Rebuilds the view from the current state and local choices (a local click: pick, stepper, form). */
	private void local() {
		view = view(state());
		refresh();
	}

	private void parse(CompoundTag st) {
		stateTick = clientTicks;
		CompoundTag fxTag = st.getCompoundOrEmpty("fx");
		int seq = fxTag.getIntOr("seq", -1);
		if (seq != fxSeq) {
			String kind = fxTag.getStringOr("kind", "");
			gatherBets = kind.equals("street") || kind.equals("finish") ? lastBets.clone() : new long[0];
			fxSeq = seq;
			fxKind = kind;
			fxStart = fxTag.getLongOr("start", 0);
			fxArgs = fxTag.getIntArray("args").orElse(NO_CARDS);
			fx = PokerBeats.segment(fxKind, fxArgs, fxTag.getIntOr("seed", 0));
		}
		fxDone = fxTag.getBooleanOr("done", true);
		size = Math.max(2, st.getIntOr("table_size", 6));
		handNo = st.getIntOr("hand_no", 0);
		live = st.getBooleanOr("live", false);
		presenting = st.getBooleanOr("presenting", false);
		toAct = st.getIntOr("to_act", -1);
		button = st.getIntOr("button", -1);
		board = st.getIntArray("board").orElse(NO_CARDS);
		pots = st.getLongArray("pots").orElse(new long[0]);
		potTotal = st.getLongOr("pot_total", 0);
		seats.clear();
		me = -1;
		ListTag table = st.getListOrEmpty("table");
		long[] bets = new long[Math.max(size, 16)];
		for (int i = 0; i < table.size(); i++) {
			CompoundTag t = table.getCompoundOrEmpty(i);
			SeatView v = new SeatView();
			v.index = t.getIntOr("index", 0);
			v.you = t.getBooleanOr("you", false);
			v.bot = t.getBooleanOr("bot", false);
			String botName = t.contains("bot_name") ? t.getStringOr("bot_name", "") : null;
			v.name = v.bot && botName != null ? Component.translatable(BotRoster.nameKey(botName)) : decode(t.get("name"));
			v.folded = t.getBooleanOr("folded", false);
			v.allIn = t.getBooleanOr("all_in", false);
			v.out = t.getBooleanOr("out", false);
			v.mucked = t.getBooleanOr("mucked", false);
			v.handIndex = t.getIntOr("hand_index", -1);
			v.dealt = v.handIndex >= 0;
			v.stack = t.getLongOr("stack", 0);
			v.bet = t.getLongOr("bet", 0);
			v.won = t.getLongOr("won", 0);
			v.net = t.getLongOr("net", 0);
			v.tier = t.contains("tier") ? t.getStringOr("tier", "") : null;
			v.cards = t.getIntArray("cards").orElse(NO_CARDS);
			v.hidden = t.getIntOr("hidden", 0);
			v.hand = t.contains("hand") ? decode(t.get("hand")) : null;
			v.act = t.contains("act") ? decode(t.get("act")) : null;
			v.best = t.getIntOr("best", -1);
			if (v.you) me = v.index;
			if (v.index >= 0 && v.index < bets.length) bets[v.index] = v.bet;
			seats.add(v);
		}
		lastBets = bets;
		awards.clear();
		ListTag aw = st.getListOrEmpty("awards");
		for (int i = 0; i < aw.size(); i++) {
			CompoundTag a = aw.getCompoundOrEmpty(i);
			awards.add(new Award(a.getIntOr("pot", 0), a.getLongOr("amount", 0), a.getIntArray("seats").orElse(NO_CARDS),
				a.getLongArray("shares").orElse(new long[0])));
		}
		List<Component> res = new ArrayList<>();
		ListTag rt = st.getListOrEmpty("result");
		for (int i = 0; i < rt.size(); i++) res.add(decode(rt.get(i)));
		results = res;
		CompoundTag la = st.getCompoundOrEmpty("last_action");
		int n = la.getIntOr("n", -1);
		if (!la.isEmpty() && (n != lastActionN || la.getIntOr("seat", -1) != lastActionSeat) && live) {
			boolean first = lastActionN == -1;
			lastActionN = n;
			lastActionSeat = la.getIntOr("seat", -1);
			lastActionAt = nowMs();
			lastActionText = actionTag(la);
			if (!first) actionSound(la);
		}
		celebrate();
	}

	private static Component actionTag(CompoundTag la) {
		long amount = la.getLongOr("amount", 0);
		return switch (la.getStringOr("type", "")) {
			case "fold" -> PokerText.gui("tag.fold");
			case "check" -> PokerText.gui("tag.check");
			case "call" -> PokerText.gui("tag.call", Texts.number(amount));
			case "bet" -> PokerText.gui("tag.bet", Texts.number(amount));
			case "raise" -> PokerText.gui("tag.raise", Texts.number(amount));
			case "small_blind" -> PokerText.gui("tag.small_blind", Texts.number(amount));
			case "big_blind" -> PokerText.gui("tag.big_blind", Texts.number(amount));
			default -> Component.empty();
		};
	}

	private static void actionSound(CompoundTag la) {
		String type = la.getStringOr("type", "");
		if (la.getBooleanOr("all_in", false) && !type.equals("fold") && !type.equals("check")) {
			FxSounds.play("chip_push", 0.8f, 1f);
			return;
		}
		switch (type) {
			case "check" -> FxSounds.play("table_knock", 0.8f, 1f);
			case "call", "bet", "raise", "small_blind", "big_blind" -> FxSounds.play("chip_place", 0.7f, 1f);
			case "fold" -> FxSounds.play("card_gather", 0.4f, 1f);
			default -> {
			}
		}
	}

	/** The viewer's celebration and the result banner after the gate. */
	private void celebrate() {
		if (live || presenting || handNo <= 0) return;
		for (SeatView v : seats) {
			if (!v.you || v.tier == null) continue;
			if (bannerHand != handNo) {
				bannerHand = handNo;
				chrome.banner.show(v.net);
			}
			if (celebratedHand == handNo) return;
			celebratedHand = handNo;
			try {
				WinTier tier = WinTier.valueOf(v.tier);
				if (tier.isWin()) {
					long stake = Math.max(1, v.won - v.net);
					CelebrationOverlay.get().play(CelebrationRequest.core(tier, v.won, stake, SeedMix.mix(handNo, v.index)));
				}
			} catch (IllegalArgumentException ignored) {
				// unknown tier: no celebration
			}
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		clientTicks++;
		if (clientTicks % 10 == 0 && view != null) local(); // timers in the status line / gates
	}

	private double fxMs() {
		return nowMs() - fxStart * 50.0;
	}

	private @Nullable Beat beat(String kind, int lane) {
		return fx == null ? null : PokerBeats.find(fx, kind, lane);
	}

	private double beatAt(String kind, int lane) {
		Beat b = beat(kind, lane);
		return b == null ? Double.NaN : fxStart * 50.0 + b.at();
	}

	private boolean started(String kind) {
		return fx != null && PokerBeats.started(fx, kind, fxMs()) > 0;
	}

	private @Nullable SeatView seat(int index) {
		for (SeatView v : seats) if (v.index == index) return v;
		return null;
	}

	// ---- the kit's hooks -------------------------------------------------------------------------------------------

	@Override
	protected Flow<PokerView> flow() {
		return flow;
	}

	@Override
	protected dev.nezo.burmaldaholic.client.ui.v2.@Nullable BetPort betPort() {
		return null; // no BetBar: the buy-in, raise and top-up amounts are on the stepper
	}

	@Override
	protected Component tableTitle() {
		if (!s().contains("bb")) return title;
		return Component.empty().append(title).append(Texts.raw("  ")).append(Texts.number(s().getLongOr("sb", 1))).append(Texts.raw("/"))
			.append(Texts.number(s().getLongOr("bb", 2)));
	}

	@Override
	protected void refresh() {
		if (view == null || font == null) return;
		chrome.keys(keyMap(view));
		chrome.actions.danger("fold", "stand_up");
		super.refresh();
		// TopBar: the blinds are in the title; the pills show the buy-in range and the seats taken
		PokerView.Level lv = view.level();
		chrome.top.set(tableTitle(), lv == null ? 0 : lv.buyMin(), lv == null ? 0 : lv.buyMax(), seats.size(), size);
		chrome.relayout();
		status(view);
		stepperHits();
	}

	/** Game keys: F fold, C check / call, R the raise primary, ↑ / ↓ the stepper. */
	private KeyMap keyMap(PokerView v) {
		KeyMap k = KeyMap.standard();
		k.bind(KeyMap.F, KeyMap.Ids.action("fold"));
		List<Gate> acts = PokerFlow.actions(v);
		String cc = acts.stream().map(Gate::action).filter(a -> a.equals("check") || a.equals("call")).findFirst().orElse("");
		String p = PokerFlow.primary(v).action();
		if (!cc.isEmpty()) k.bind(KeyMap.C, KeyMap.Ids.action(cc));
		else if (p.equals("check") || p.equals("call")) k.bind(KeyMap.C, KeyMap.Ids.PRIMARY); // Space still works (keyPressed)
		if (p.equals("raise") || p.equals("all_in")) k.bind(KeyMap.R, KeyMap.Ids.PRIMARY);
		return k;
	}

	/** The stepper's − / + halves on top of its button (registered last: topmost). */
	private void stepperHits() {
		Box b = stepperBox();
		if (b == null) return;
		int third = Math.max(10, b.w() / 3);
		PokerFlow.Stepper s = view == null ? null : PokerFlow.stepper(view).orElse(null);
		if (s == null) return;
		chrome.hits().add(STEP_MINUS, new Box(b.x(), b.y(), third, b.h()), s.canMinus());
		chrome.hits().add(STEP_PLUS, new Box(b.right() - third, b.y(), third, b.h()), s.canPlus());
	}

	private @Nullable Box stepperBox() {
		return chrome.hits().box(KeyMap.Ids.action(STEPPER)).orElse(null);
	}

	private void status(PokerView v) {
		Component l1 = null;
		Component l2 = null;
		int c1 = CasinoPalette.V2_GOLD;
		PokerView.Turn t = v.turn();
		if (!v.enabled()) {
			l1 = Component.translatable("gui.burmaldaholic.error.disabled");
		} else if (t != null) {
			l1 = t.toCall() > 0 && !t.canCheck() ? PokerText.gui("v2.status.to_call", Texts.number(t.toCall())) : PokerText.gui("your_turn");
			long left = ticksLeft("action");
			if (left >= 0) {
				l2 = PokerText.gui(t.canCheck() ? "v2.status.auto_check" : "v2.status.auto_fold", Texts.number((left + 19) / 20));
				chrome.status.timer((int) ((left + 19) / 20), Math.max(1, dev.nezo.burmaldaholic.core.config.CasinoConfig.poker().actionTimerTicks / 20));
			} else {
				chrome.status.timer.clear();
			}
			if (t.canRaise() && l2 == null) l2 = PokerText.gui("v2.status.stepper");
		} else {
			chrome.status.timer.clear();
			if (presenting) {
				boolean runout = fxArgs.length >= 2 && fxArgs[1] > 0 && !started(PokerBeats.SHOW);
				l1 = runout ? PokerText.gui("fx.all_in_runout") : PokerText.gui("showdown");
			} else if (!live && !results.isEmpty()) {
				l1 = results.get(0);
				c1 = CasinoPalette.V2_WIN;
			}
			if (v.seat() == PokerView.Seat.NONE) {
				PokerView.Level lv = v.level();
				if (lv == null) {
					l1 = PokerText.gui("v2.status.watching");
					l2 = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
				} else {
					l2 = PokerText.gui("v2.status.buy_in", Texts.number(lv.buyMin()), Texts.number(lv.buyMax()), Texts.number(lv.sb()), Texts.number(lv.bb()));
				}
			} else if (v.seated() && v.topUpForm()) {
				l2 = PokerText.gui("v2.status.top_up", Texts.number(v.topMax()));
			} else if (v.seated() && l2 == null) {
				SeatView mine = seat(me);
				if (mine != null) l2 = PokerText.gui("stack", Texts.number(mine.stack));
			}
		}
		chrome.status.set(l1, l2);
		chrome.status.color(c1);
	}

	@Override
	public void onGate(String id, Gate gate) {
		PokerView v = view;
		if (v == null) return;
		String a = gate.action();
		if (a.startsWith(PokerFlow.LOCAL + "pick:")) {
			pickedLevel = a.substring((PokerFlow.LOCAL + "pick:").length());
			buyIn = -1;
			local();
			return;
		}
		switch (a) {
			case PokerFlow.LOCAL + "back" -> {
				if (topUpForm) topUpForm = false;
				else pickedLevel = null;
				local();
			}
			case PokerFlow.LOCAL + "top_up_form" -> {
				topUpForm = true;
				topUp = v.topMax();
				local();
			}
			case STEPPER -> presetClick(v);
			case "buy_in" -> {
				if (v.level() == null) return;
				CompoundTag args = new CompoundTag();
				args.putString("level", v.level().id());
				args.putLong("amount", PokerFlow.stepper(v).map(PokerFlow.Stepper::value).orElse(buyIn));
				sendTracked("buy_in", args, "", 0);
			}
			case "top_up" -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", topUp);
				topUpForm = false;
				sendTracked("top_up", args, "", 0);
			}
			case "fold", "check", "call", "raise", "all_in" -> {
				PokerView.Turn t = v.turn();
				if (t == null) return;
				CompoundTag args = new CompoundTag();
				args.putString("kind", a);
				args.putLong("to", a.equals("raise") ? raiseTo : 0);
				args.putInt("seq", t.seq());
				sendTracked("act", args, "", 0);
			}
			default -> {
				if (!a.isEmpty() && !a.startsWith(PokerFlow.LOCAL)) sendTracked(a);
			}
		}
	}

	/** A click on the stepper's amount: the next raise preset (½ pot, ¾, pot, all-in), or min / max for a buy-in. */
	private void presetClick(PokerView v) {
		PokerFlow.Stepper s = PokerFlow.stepper(v).orElse(null);
		if (s == null) return;
		long next;
		if (s.id().equals("raise") && v.turn() != null) next = PokerFlow.nextPreset(v.turn(), s.value());
		else next = s.value() >= s.max() ? s.min() : s.max();
		setAmount(s.id(), next);
	}

	private void step(int dir) {
		PokerView v = view;
		if (v == null) return;
		PokerFlow.Stepper s = PokerFlow.stepper(v).orElse(null);
		if (s == null) return;
		setAmount(s.id(), s.next(dir, 1));
	}

	private void setAmount(String id, long amount) {
		long before = switch (id) {
			case "raise" -> raiseTo;
			case "buy_in" -> buyIn;
			default -> topUp;
		};
		switch (id) {
			case "raise" -> raiseTo = amount;
			case "buy_in" -> buyIn = amount;
			default -> topUp = amount;
		}
		if (before != amount) FxSounds.play("chip_count", 0.5f, 1f);
		local();
	}

	@Override
	public boolean onTarget(String id, boolean enabled, int button) {
		if (id.equals(STEP_MINUS) || id.equals(STEP_PLUS) || id.equals(KeyMap.Ids.action(STEPPER))) {
			if (button == 2) step(1);
			else if (button == 3) step(-1);
			else if (id.equals(STEP_MINUS)) step(-1);
			else if (id.equals(STEP_PLUS)) step(1);
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (rulesOpen && (event.key() == KeyMap.ESCAPE || event.key() == KeyMap.ENTER)) {
			rulesOpen = false;
			return true;
		}
		if (event.key() == KeyMap.SPACE && chrome.keys().idFor(KeyMap.SPACE).isEmpty()) {
			// the primary's plate shows its letter (C / R); Space presses it as everywhere else
			boolean on = chrome.hits().targets().stream().anyMatch(t -> t.id().equals(KeyMap.Ids.PRIMARY) && t.enabled());
			return chrome.press(KeyMap.Ids.PRIMARY, on, 0);
		}
		if (event.key() == KEY_UP || event.key() == KEY_DOWN) {
			if (view != null && PokerFlow.stepper(view).isPresent()) {
				step(event.key() == KEY_UP ? 1 : -1);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onTopIcon(String id) {
		if (id.equals(KeyMap.Ids.TOP_LEAVE)) {
			if (view != null && view.seated()) sendAction("stand_up");
			onClose();
			return;
		}
		super.onTopIcon(id);
	}

	// ---- layout ---------------------------------------------------------------------------------------------------

	@Override
	protected void layoutPlay(TableGrid grid, PokerView v, HitMap hits) {
		int pill = font.width("888 888") + 2 * ValuePill.PAD;
		int bet = font.width("88 888") + 2 * ValuePill.PAD;
		int potW = font.width(PokerText.gui("v2.pot")) + 6 + 2 * font.width("888 888");
		int handW = 0;
		for (SeatView s : seats) if (s.hand != null) handW = Math.max(handW, font.width(s.hand) + 2 * ValuePill.PAD);
		handW = Math.max(handW, font.width(PokerText.gui("tag.fold")) + 2 * ValuePill.PAD);
		layout = PokerLayout.of(grid, size, new PokerLayout.Measure(pill, bet, potW, Math.max(60, handW)));
		List<Box> obstacles = new ArrayList<>(layout.cardBoxes());
		Box board0 = layout.board()[0];
		Box hero = layout.seat(0).cards();
		chrome.banner.regions(List.of(new Box(board0.x(), layout.pot().bottom() + 2, layout.board()[4].right() - board0.x(),
			Math.max(0, hero.y() - layout.pot().bottom() - 4)), layout.oval()), obstacles);
		for (int r = 0; r < layout.seats().length; r++) hits.add("seat." + r, layout.seat(r).plate(), false);
	}

	private PokerLayout.Seat slot(int index) {
		return layout.seat(PokerLayout.rel(index, me, size));
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, PokerView v, int mouseX, int mouseY, float partial) {
		if (layout == null || layout.grid() != grid) layoutPlay(grid, v, new HitMap());
		PokerLayout.Layout l = layout;
		drawOval(g, grid, l.oval());
		if (!v.enabled()) return;
		drawBoardSlots(g, l);
		drawCards(g, grid.tier, l);
		drawChips(g, grid.tier, l);
		drawButton(g, l);
		drawSeats(g, l);
		drawPot(g, l);
	}

	/** The dimmed room, the rail and the oval felt (scanlines: crisp at every size, no sprite needed). */
	private void drawOval(GuiGraphicsExtractor g, TableGrid grid, Box o) {
		TableTheme theme = TableTheme.of(theme());
		V2Draw.fill(g, grid.play, 0x8C0C0614);
		int cx = o.cx();
		int cy = o.cy();
		double rx = o.w() / 2.0;
		double ry = o.h() / 2.0;
		int rail = grid.tier == Tier.T1 ? 6 : 8;
		int felt = V2Felt.felt(theme());
		int railC = V2Felt.rail(theme());
		int railDark = CasinoPalette.V2_RAIL_DARK;
		int gold = V2Draw.alpha(CasinoPalette.V2_GOLD_SHADE, 0.9f);
		for (int y = (int) (cy - ry - rail); y <= cy + ry + rail; y++) {
			int ow = halfWidth(rx + rail, ry + rail, y - cy);
			if (ow < 0) continue;
			g.fill(cx - ow, y, cx + ow, y + 1, railDark);
			int mw = halfWidth(rx + rail - 1, ry + rail - 1, y - cy);
			if (mw > 0) g.fill(cx - mw, y, cx + mw, y + 1, railC);
			int gw = halfWidth(rx + 1, ry + 1, y - cy);
			if (gw > 0) g.fill(cx - gw, y, cx + gw, y + 1, gold);
			int iw = halfWidth(rx, ry, y - cy);
			if (iw > 0) g.fill(cx - iw, y, cx + iw, y + 1, felt);
		}
		int print = theme.printArgb(0.18);
		int iw = halfWidth(rx - 10, ry - 10, 0);
		if (iw > 0) {
			for (int y = (int) (cy - ry + 10); y <= cy + ry - 10; y++) {
				int w = halfWidth(rx - 10, ry - 10, y - cy);
				int w2 = halfWidth(rx - 11, ry - 11, y - cy);
				if (w < 0) continue;
				if (w2 < 0) g.fill(cx - w, y, cx + w, y + 1, print);
				else {
					g.fill(cx - w, y, cx - w2, y + 1, print);
					g.fill(cx + w2, y, cx + w, y + 1, print);
				}
			}
		}
	}

	private static int halfWidth(double rx, double ry, double dy) {
		double t = 1 - (dy * dy) / (ry * ry);
		return t < 0 ? -1 : (int) Math.round(rx * Math.sqrt(t));
	}

	private void drawBoardSlots(GuiGraphicsExtractor g, PokerLayout.Layout l) {
		boolean handOn = live || presenting || handNo > 0;
		if (!handOn) return;
		int print = TableTheme.of(theme()).printArgb(0.45);
		for (int i = board.length; i < 5; i++) V2Draw.outline(g, l.board()[i], print);
	}

	private int handKey() {
		return Math.floorMod(handNo, 50) * 20_000;
	}

	private boolean awardStarted(int lane) {
		return fx != null && "finish".equals(fxKind) && PokerBeats.started(fx, PokerBeats.AWARD, fxMs()) > lane;
	}

	private boolean winner(int seat) {
		if (!(fxDone || awardStarted(0))) return false;
		for (Award a : awards) for (int s : a.seats()) if (s == seat) return true;
		return false;
	}

	/** Board and hole cards through the kit's animator (deal arcs, flips, best five lifted). */
	private void drawCards(GuiGraphicsExtractor g, Tier tier, PokerLayout.Layout l) {
		int[] c = l.centre();
		cards.origin(c[0] - 10, c[1] - 14).tray(c[0] - 10, c[1] - 14);
		cards.begin(nowMs(), FxSettings.reduceMotion());
		int back = TableTheme.of(theme()).back(CardSprites.BACK_CRIMSON);
		boolean best = started(PokerBeats.BEST) || (!live && !presenting && handNo > 0 && !awards.isEmpty());
		int winnerMask = 0;
		for (SeatView v : seats) if (v.best >= 0 && winner(v.index)) winnerMask |= v.best >> 2;
		boolean street = "street".equals(fxKind) || "finish".equals(fxKind);
		int heroSize = V2Cards.size(tier, true);
		for (int i = 0; i < board.length; i++) {
			int[] at = V2Cards.at(l.board()[i], tier, true);
			double dealAt = street ? beatAt(PokerBeats.SLIDE, i) : Double.NaN;
			double revealAt = street ? beatAt(PokerBeats.FLIP, i) : Double.NaN;
			CardAnimator.Slot slot = cards.card(handKey() + 100 + i, kit(board[i]), heroSize, back, at[0], at[1], 0, dealAt, revealAt);
			if (best && winnerMask != 0) {
				boolean in = (winnerMask & (1 << i)) != 0;
				cards.lift(slot, in);
				cards.glow(slot, in);
				cards.dim(slot, !in);
			}
		}
		boolean dealing = "deal".equals(fxKind);
		for (SeatView v : seats) {
			if (!v.dealt || v.mucked || v.folded) continue;
			int rel = PokerLayout.rel(v.index, me, size);
			PokerLayout.Seat sl = l.seat(rel);
			boolean hero = rel == 0;
			int sz = V2Cards.size(tier, hero);
			int n = v.cards.length > 0 ? v.cards.length : v.hidden;
			for (int k = 0; k < n; k++) {
				int[] at = V2Cards.at(sl.card(k, tier, hero), tier, hero);
				int code = k < v.cards.length ? kit(v.cards[k]) : -1;
				double dealAt = Double.NaN;
				double revealAt = Double.NaN;
				if (dealing && fx != null) {
					for (Beat b : fx.beats()) {
						if (b.kind().equals(PokerBeats.DEAL) && b.lane() == v.handIndex && b.arg(0) == k) dealAt = fxStart * 50.0 + b.at();
					}
					if (v.you) revealAt = beatAt(PokerBeats.LIFT, v.handIndex);
				}
				CardAnimator.Slot slot = cards.card(handKey() + 1000 + v.index * 10 + k, code, sz, back, at[0], at[1], 0, dealAt, revealAt);
				if (best && code >= 0 && v.best >= 0) {
					boolean in = (v.best & (1 << k)) != 0 && winner(v.index);
					cards.lift(slot, in);
					cards.glow(slot, in);
					cards.dim(slot, !winner(v.index));
				}
			}
			if (hero && v.index == toAct && live) V2Draw.outline(g, sl.cards().inset(-2), CasinoPalette.V2_GOLD);
		}
		cards.end();
		cards.draw(g, font);
	}

	/** Poker card (rank index × 4 + suit) → the kit's card code (suit × 13 + rank − 1, ace = 1). */
	static int kit(int card) {
		int r = (card >> 2) + 2;
		return (card & 3) * 13 + (r == 14 ? 0 : r - 1);
	}

	/** Bets on the felt, the gather to the pot, the award slides. */
	private void drawChips(GuiGraphicsExtractor g, Tier tier, PokerLayout.Layout l) {
		double t = fxMs();
		Beat gather = beat(PokerBeats.GATHER, -1);
		boolean gathering = gather != null && gatherBets.length > 0 && t < gather.end() + 50;
		Box pot = l.pot();
		for (SeatView v : seats) {
			PokerLayout.Seat sl = slot(v.index);
			Box stack = sl.stack(tier);
			if (gathering && v.index < gatherBets.length && gatherBets[v.index] > 0) {
				double u = t < gather.at() ? 0 : Math.min(1, (t - gather.at()) / gather.dur());
				double p = Ease.OUT_CUBIC.apply(u);
				int x = (int) Math.round(stack.x() + (pot.cx() - stack.x()) * p);
				int y = (int) Math.round(stack.y() + (pot.y() - stack.y()) * p);
				if (u < 1) ChipStack.draw(g, new Box(x, y, stack.w(), stack.h()), gatherBets[v.index], (float) (1 - Math.max(0, u - 0.8) * 5));
				continue;
			}
			if (live && v.bet > 0) {
				ChipStack.draw(g, stack, v.bet);
				Component amount = Texts.number(v.bet);
				ValuePill.draw(g, font, fit(sl.betPill(), amount), amount, v.you ? ValuePill.Style.YOU : ValuePill.Style.OTHER);
			}
		}
		if (!presenting || awards.isEmpty()) return;
		for (int lane = 0; lane < awards.size(); lane++) {
			Award a = awards.get(lane);
			Beat b = beat(PokerBeats.AWARD, lane);
			double u = b == null ? 1 : t < b.at() ? 0 : Math.min(1, (t - b.at()) / b.dur());
			if (u > 0 && soundedAward != handNo * 16 + lane) {
				soundedAward = handNo * 16 + lane;
				FxSounds.play("pot_win", 0.8f, 1f);
			}
			for (int w = 0; w < a.seats().length; w++) {
				SeatView v = seat(a.seats()[w]);
				if (v == null) continue;
				Box plate = slot(v.index).plate();
				double p = Ease.IN_OUT_CUBIC.apply(u);
				int x = (int) Math.round(pot.cx() + (plate.cx() - pot.cx()) * p);
				int y = (int) Math.round(pot.y() + (plate.y() - pot.y()) * p);
				long share = w < a.shares().length ? a.shares()[w] : a.amount();
				if (u < 1) ChipStack.draw(g, new Box(x - tier.feltChip / 2, y, tier.feltChip, tier.feltChip + 4), share, (float) (u < 0.85 ? 1 : (1 - u) / 0.15));
			}
		}
	}

	private void drawButton(GuiGraphicsExtractor g, PokerLayout.Layout l) {
		if (button < 0 || handNo <= 0) return;
		Box b = slot(button).button();
		V2Draw.sprite(g, FxSprites.sprite("cards/prop/dealer_button"), b, 0xFFF4ECF8, 0xFF180A28);
	}

	private void drawSeats(GuiGraphicsExtractor g, PokerLayout.Layout l) {
		boolean showdownNames = !live && (presenting || handNo > 0);
		for (SeatView v : seats) {
			PokerLayout.Seat sl = slot(v.index);
			SeatPlate.Kind kind = v.you ? SeatPlate.Kind.YOU : v.index == toAct && live ? SeatPlate.Kind.ACTING : SeatPlate.Kind.OTHER;
			Component name = v.you ? Component.translatable("gui.burmaldaholic.common.you") : v.name;
			SeatPlate.draw(g, font, sl.plate(), name, kind, v.bot);
			if (v.index == toAct && live && !v.you) {
				long left = ticksLeft("action");
				int total = Math.max(1, dev.nezo.burmaldaholic.core.config.CasinoConfig.poker().actionTimerTicks);
				Box p = sl.plate();
				int w = left < 0 ? p.w() : (int) Math.round(p.w() * Math.min(1.0, left / (double) total));
				g.fill(p.x(), p.bottom(), p.x() + w, p.bottom() + 1, left >= 0 && left < 100 ? CasinoPalette.V2_LOSE : CasinoPalette.V2_GOLD);
			}
			// stack pill: the number, "Fold" muted, sitting out, or the won net at the end of a hand
			Component pill;
			ValuePill.Style style;
			if (!live && !presenting && handNo > 0 && v.tier != null && v.net != 0 && winner(v.index)) {
				pill = V2Draw.net(v.net);
				style = ValuePill.Style.WIN;
			} else if (v.out) {
				pill = PokerText.gui("sitting_out");
				style = ValuePill.Style.PUSH;
			} else if (v.folded && (live || presenting)) {
				pill = PokerText.gui("tag.fold");
				style = ValuePill.Style.PUSH;
			} else {
				pill = Texts.number(v.stack);
				style = v.you ? ValuePill.Style.YOU : ValuePill.Style.OTHER;
			}
			ValuePill.draw(g, font, fit(sl.pill(), pill), pill, style);
			// hand name / last action under the plate (the viewer: right of the cards)
			Component tag = null;
			if (v.mucked) tag = PokerText.gui("tag.mucks");
			else if (v.hand != null && (v.you || showdownNames) && !v.folded) tag = v.hand;
			else if (v.allIn && (live || presenting)) tag = PokerText.gui("all_in_tag");
			if (tag == null && live && v.index == lastActionSeat && nowMs() - lastActionAt < 2500 && !lastActionText.getString().isEmpty()) {
				tag = lastActionText;
			}
			if (tag != null) {
				ValuePill.Style ts = winner(v.index) && !live ? ValuePill.Style.WIN : v.you ? ValuePill.Style.YOU : ValuePill.Style.OTHER;
				ValuePill.draw(g, font, fit(sl.hand(), tag), tag, ts);
			}
		}
	}

	/** {@code box} narrowed to the pill's text (left-aligned; never wider than the box). */
	private Box fit(Box box, Component text) {
		return new Box(box.x(), box.y(), Math.min(box.w(), ValuePill.width(font, text)), box.h());
	}

	private void drawPot(GuiGraphicsExtractor g, PokerLayout.Layout l) {
		long amount = live ? potTotal : presenting && !awardStarted(0) ? potTotal : 0;
		if (amount <= 0) return;
		Box p = l.pot();
		Component cap = PokerText.gui("v2.pot");
		Component val = Texts.number(amount);
		int capW = font.width(cap);
		int valW = font.width(val) * 2;
		int x = p.cx() - (capW + 4 + valW) / 2;
		V2Draw.text(g, font, cap, x, p.y() + 10, 1, CasinoPalette.V2_MUTED, true);
		V2Draw.text(g, font, val, x + capW + 4, p.y() + 2, 2, CasinoPalette.V2_GOLD, true);
		if (pots.length > 1) {
			MutableComponent side = Component.empty();
			for (int i = 1; i < pots.length; i++) {
				if (i > 1) side.append(Texts.raw("  "));
				side.append(PokerText.gui("side_pot", Texts.number(i), Texts.number(pots[i])));
			}
			V2Draw.centred(g, font, side, new Box(p.x() - 40, p.bottom(), p.w() + 80, 10), 1, CasinoPalette.V2_MUTED, true);
		}
	}

	@Override
	protected void openRules() {
		rulesOpen = !rulesOpen;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (rulesOpen) {
			rulesOpen = false;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The {@code ?} overlay: the hand history of this table and the rake (the old console's History tooltip). */
	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, PokerView v, int mouseX, int mouseY, float partial) {
		if (!rulesOpen) return;
		Box play = grid.play;
		V2Draw.fill(g, play, 0xB0080410);
		int w = Math.min(play.w() - 2 * grid.m(), 360);
		List<Component> lines = new ArrayList<>();
		ListTag lt = s().getListOrEmpty("log");
		for (int i = 0; i < lt.size(); i++) lines.add(decode(lt.get(i)));
		int max = Math.max(1, (play.h() - 40) / 11);
		if (lines.size() > max) lines = lines.subList(lines.size() - max, lines.size());
		Box panel = Box.centredAt(play.cx(), play.cy(), w, 30 + 11 * Math.max(1, lines.size()));
		V2Draw.pill(g, panel, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD_SHADE);
		V2Draw.text(g, font, Component.translatable("gui.burmaldaholic.common.history"), panel.x() + 8, panel.y() + 6, 1, CasinoPalette.V2_GOLD, true);
		long permille = s().getLongOr("rake_permille", 50);
		String pct = permille % 10 == 0 ? Long.toString(permille / 10) : (permille / 10) + "." + (permille % 10);
		CompoundTag lv = null;
		ListTag levels = s().getListOrEmpty("levels");
		for (int i = 0; i < levels.size(); i++) {
			CompoundTag c = levels.getCompoundOrEmpty(i);
			if (v.level() != null && c.getStringOr("id", "").equals(v.level().id())) lv = c;
		}
		if (lv != null) {
			Component rake = PokerText.gui("rake_info", Texts.raw(pct), Texts.number(lv.getLongOr("rake_cap", 0)));
			int rw = Math.min(font.width(rake), w / 2);
			V2Draw.left(g, font, rake, new Box(panel.right() - 8 - rw, panel.y() + 6, rw, 11), 0, 1, CasinoPalette.V2_MUTED, true);
		}
		int y = panel.y() + 20;
		for (Component c : lines) {
			V2Draw.left(g, font, c, new Box(panel.x() + 8, y, panel.w() - 16, 11), 0, 1, CasinoPalette.V2_TEXT, true);
			y += 11;
		}
	}
}
