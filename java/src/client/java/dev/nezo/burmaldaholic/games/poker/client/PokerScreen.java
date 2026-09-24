package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.table.cards.ActionTag;
import dev.nezo.burmaldaholic.client.table.cards.CardAnimator;
import dev.nezo.burmaldaholic.client.table.cards.CardButton;
import dev.nezo.burmaldaholic.client.table.cards.CardGfx;
import dev.nezo.burmaldaholic.client.table.cards.CardSprites;
import dev.nezo.burmaldaholic.client.table.cards.CardTableScreen;
import dev.nezo.burmaldaholic.client.table.cards.ChipStackView;
import dev.nezo.burmaldaholic.client.table.cards.SeatPlate;
import dev.nezo.burmaldaholic.client.table.cards.TableStamp;
import dev.nezo.burmaldaholic.client.table.cards.TableTheme;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.poker.PokerText;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBeats;
import dev.nezo.burmaldaholic.games.poker.present.HoldemLayout;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * Texas Hold'em screen, redesigned (docs/design/visual/cards.md §6.4, mockups {@code cards_holdem_showdown.png} and
 * {@code cards_holdem_nether.png}; motion animation/cards.md §2.2–§2.3, tasks J-C7 + J-C12): the themed room, the oval
 * felt, up to nine seat plates around it (mob-face bot avatars with level badges, the viewer at the bottom with L cards
 * "in your hands"), the board, pot and side pots, bet stacks on the bet line, the dealer button travelling along the
 * ellipse, K12 action tags, the ALL-IN / MONSTER POT stamps and the showdown's hand names — placed by
 * {@link LabelPlacer} so a label never covers a card (the mockup's flaw, fixed).
 *
 * <p>Everything moves on the server's {@link PokerBeats} segments (the {@code fx} tag: deal, street, finish) sampled on
 * the shared clock: hole cards arc from the deck, streets gather / burn / slide / flip, the slow all-in run-out, the
 * ordered showdown, best five lifted, pots sliding to the winners (side pots first). The screen only draws the
 * PUBLISHED state (the server sends a board card on its slide beat and a hand on its show beat), so skip, reduce motion
 * and late joins all end on the server's result. Actions: {@code buy_in}, {@code act}, {@code sit_out}/{@code sit_in},
 * {@code top_up}, {@code stand_up}.
 */
public class PokerScreen extends CardTableScreen {
	private static final int GREEN = 0xFF80FF40;
	private static final int[] NO_CARDS = new int[0];

	private final CardAnimator cards = new CardAnimator();
	private final CardMotion.Pose pose = new CardMotion.Pose();
	private final List<AbstractWidget> extras = new ArrayList<>();
	private final List<Hover> hovers = new ArrayList<>();
	private @Nullable String pickedLevel;
	private boolean topUpMode;
	private long raiseTo = -1;
	private int raiseSeq = -1;
	private long buyIn = -1;
	private long topUp = -1;
	private int clientTicks;
	/** The raise sizing panel (slider, ½ / ¾ / pot, all-in) is open above the console. */
	private boolean raiseOpen;
	private int panelX = -1;
	/** Buttons of the raise panel (not console buttons: the console must not grow under them). */
	private final List<CardButton> panel = new ArrayList<>();
	private int panelY;
	private int stateTick;

	// ---- the parsed state -------------------------------------------------------------------------------------
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

	// ---- presentation state -----------------------------------------------------------------------------------
	private int fxSeq = -1;
	private String fxKind = "";
	private long fxStart;
	private int[] fxArgs = NO_CARDS;
	private @Nullable Timeline fx;
	private String moment = "";
	private boolean fxDone = true;
	/** Bets on the felt before the running street / finish segment (they gather to the pot on its gather beat). */
	private long[] gatherBets = new long[0];
	private long[] lastBets = new long[0];
	private int prevButton = -1;
	private int lastActionN = -1;
	private int lastActionSeat = -1;
	private double lastActionAt = Double.NaN;
	private Component lastActionText = Component.empty();
	private final double[] allInAt = new double[16];
	private int celebratedHand = -1;
	private int soundedAward = -1;

	private record Hover(int x, int y, int w, int h, Component text) {}

	private record Award(int pot, long amount, int[] seats, long[] shares) {}

	private static final class SeatView {
		int index;
		Component name = Component.empty();
		String rawName = "";
		@Nullable String uuid;
		boolean you;
		boolean bot;
		@Nullable String botName;
		int level;
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
		int best = -1;
	}

	public PokerScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, PokerText.gui("title"), TableTheme.Shape.OVAL);
		java.util.Arrays.fill(allInAt, Double.NaN);
	}

	// ---- state -------------------------------------------------------------------------------------------------

	private CompoundTag s() {
		return state();
	}

	private boolean seated() {
		return s().getBooleanOr("seated", false);
	}

	private @Nullable CompoundTag legal() {
		CompoundTag t = s().getCompoundOrEmpty("legal");
		return t.isEmpty() ? null : t;
	}

	/** True while the server says it is the viewer's turn (tests, narration). */
	public boolean isMyTurn() {
		return legal() != null;
	}

	/** GameTests: the presentation segment playing now ("deal" / "street" / "finish"). */
	public String fxKind() {
		return fxKind;
	}

	/** GameTests: the board cards drawn (published) now. */
	public int boardShown() {
		return board.length;
	}

	private Component decode(@Nullable Tag tag) {
		if (tag == null) return Component.empty();
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	private @Nullable CompoundTag level(String id) {
		ListTag levels = s().getListOrEmpty("levels");
		for (int i = 0; i < levels.size(); i++) {
			CompoundTag l = levels.getCompoundOrEmpty(i);
			if (l.getStringOr("id", "").equals(id)) return l;
		}
		return null;
	}

	private @Nullable String levelId() {
		String fixed = s().getStringOr("stake", "");
		return fixed.isEmpty() ? pickedLevel : fixed;
	}

	private long ticksLeft(String id) {
		long t = s().getCompoundOrEmpty("timers").getLongOr(id, -1);
		return t < 0 ? -1 : Math.max(0, t - (clientTicks - stateTick));
	}

	@Override
	protected void onStateChanged(CompoundTag st) {
		stateTick = clientTicks;
		// bets on the felt before a new street / finish segment: they gather to the pot on its beat
		CompoundTag fxTag = st.getCompoundOrEmpty("fx");
		int seq = fxTag.getIntOr("seq", -1);
		if (seq != fxSeq) {
			String kind = fxTag.getStringOr("kind", "");
			gatherBets = kind.equals("street") || kind.equals("finish") ? lastBets.clone() : new long[0];
			if (kind.equals("deal")) prevButton = button;
			fxSeq = seq;
			fxKind = kind;
			fxStart = fxTag.getLongOr("start", 0);
			fxArgs = fxTag.getIntArray("args").orElse(NO_CARDS);
			fx = timeline(fxKind, fxArgs, fxTag.getIntOr("seed", 0));
		}
		fxDone = fxTag.getBooleanOr("done", true);
		moment = fxTag.getStringOr("moment", "");
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
			v.botName = t.contains("bot_name") ? t.getStringOr("bot_name", "") : null;
			String lvl = t.getStringOr("level", "");
			v.level = lvl.equals("easy") ? 1 : lvl.equals("normal") ? 2 : lvl.equals("hard") ? 3 : 0;
			v.name = v.bot && v.botName != null ? Component.translatable(BotRoster.nameKey(v.botName)) : decode(t.get("name"));
			v.rawName = v.name.getString();
			v.uuid = t.contains("uuid") ? t.getStringOr("uuid", "") : null;
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
		for (SeatView v : seats) {
			if (v.index < allInAt.length) {
				if (v.allIn && Double.isNaN(allInAt[v.index])) allInAt[v.index] = nowMs();
				if (!v.allIn) allInAt[v.index] = Double.NaN;
			}
		}
		celebrate();
		if (minecraft != null) rebuildConsole();
	}

	private static @Nullable Timeline timeline(String kind, int[] a, int seed) {
		return switch (kind) {
			case "deal" -> a.length >= 2 ? PokerBeats.deal(a[0], a[1], PokerBeats.Pacing.DEFAULT, seed) : null;
			case "street" -> a.length >= 1 ? PokerBeats.street(a[0], PokerBeats.Pacing.DEFAULT, seed) : null;
			case "finish" -> a.length >= 5
				? PokerBeats.finish(new PokerBeats.Finish(a[0] != 0, a[1], a[2] != 0, a[3], a[4]), PokerBeats.Pacing.DEFAULT, seed) : null;
			default -> null;
		};
	}

	/** K12 tag text of the latest action ("Check", "Call 20", "Raise to 120", "SB 5", "Fold"). */
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

	/** The viewer's celebration after the gate (the server's tier on this hand; global kit). */
	private void celebrate() {
		if (live || presenting || handNo == celebratedHand) return;
		for (SeatView v : seats) {
			if (!v.you || v.tier == null) continue;
			celebratedHand = handNo;
			WinTier tier;
			try {
				tier = WinTier.valueOf(v.tier);
			} catch (IllegalArgumentException e) {
				return;
			}
			if (tier.isWin()) {
				long stake = Math.max(1, v.won - v.net);
				CelebrationOverlay.get().play(CelebrationRequest.core(tier, v.won, stake, SeedMix.mix(handNo, v.index)));
			}
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		clientTicks++;
	}

	private double fxMs() {
		return nowMs() - fxStart * 50.0;
	}

	private @Nullable Beat beat(String kind, int lane) {
		return fx == null ? null : PokerBeats.find(fx, kind, lane);
	}

	/** Shared-clock start (ms) of a beat of the current segment, or NaN. */
	private double beatAt(String kind, int lane) {
		Beat b = beat(kind, lane);
		return b == null ? Double.NaN : fxStart * 50.0 + b.at();
	}

	private boolean started(String kind) {
		return fx != null && (PokerBeats.started(fx, kind, fxMs()) > 0);
	}

	// ---- console -----------------------------------------------------------------------------------------------

	@Override
	protected Component plaqueTitle() {
		if (!s().contains("bb")) return title;
		return Component.empty().append(title).append(Texts.raw(" · ")).append(Texts.number(s().getLongOr("sb", 1))).append(Texts.raw("/"))
			.append(Texts.number(s().getLongOr("bb", 2)));
	}

	@Override
	protected void buildConsole() {
		for (AbstractWidget w : extras) removeWidget(w);
		extras.clear();
		for (CardButton b : panel) removeWidget(b);
		panel.clear();
		List<CardButton> row = new ArrayList<>();
		List<CardButton> row2 = new ArrayList<>();
		if (s().getBooleanOr("enabled", true)) {
			if (!seated() && s().getBooleanOr("waiting_seat", false)) {
				row.add(button(PokerText.gui("stand_up"), "leave", CardButton.Family.TABLE, true, () -> sendAction("stand_up"))
					.hint(Component.translatable("msg.burmaldaholic.bots.seat_after_round")));
			} else if (!seated()) {
				buildJoin(row, row2);
			} else if (legal() != null) {
				topUpMode = false;
				buildTurn(row, row2, legal());
			} else if (topUpMode) {
				buildTopUp(row);
			} else {
				boolean out = s().getBooleanOr("sitting_out", false);
				boolean canTop = s().getBooleanOr("can_top_up", false) && s().getLongOr("top_max", 0) > 0;
				row.add(button(PokerText.gui(out ? "sit_in" : "sit_out"), "leave", CardButton.Family.TABLE, true,
					() -> sendAction(out ? "sit_in" : "sit_out")));
				row.add(button(PokerText.gui("top_up"), "deal", CardButton.Family.TABLE, canTop, () -> {
					topUpMode = true;
					topUp = -1;
					rebuildConsole();
				}));
				row.add(button(PokerText.gui("stand_up"), "leave", CardButton.Family.TABLE, !s().getBooleanOr("leaving", false),
					() -> sendAction("stand_up")));
			}
		}
		int minX = compact() ? 0 : seated() ? 150 : 104;
		layoutButtons(row, minX);
		if (!row2.isEmpty()) {
			// the raise panel: right-aligned above the console, over the felt's lower-right corner
			int x = canvasW() - 8;
			int y = (compact() ? 139 : 213) - 26;
			for (int i = row2.size() - 1; i >= 0; i--) {
				CardButton b = row2.get(i);
				x -= b.getWidth();
				b.setX(x);
				b.setY(y);
				x -= 3;
				panel.add(b);
				addWidget(b);
			}
			layoutExtras(row2, true);
			panelX = extras.isEmpty() ? x : extras.get(extras.size() - 1).getX() - 4;
			panelY = y - 3;
		} else {
			layoutExtras(row, false);
			panelX = -1;
		}
	}

	/** Sliders sit left of their button row. */
	private void layoutExtras(List<CardButton> row, boolean upper) {
		if (extras.isEmpty()) return;
		int x = row.isEmpty() ? canvasW() - 6 : row.get(0).getX();
		for (AbstractWidget w : extras) {
			x -= w.getWidth() + 4;
			w.setX(Math.max(4, x));
			w.setY(row.isEmpty() ? (compact() ? 139 : 213) : row.get(0).getY());
			addWidget(w);
		}
	}

	private void buildJoin(List<CardButton> row, List<CardButton> row2) {
		String levelId = levelId();
		int vip = s().getIntOr("vip", 0);
		if (levelId == null) {
			ListTag levels = s().getListOrEmpty("levels");
			for (int i = 0; i < levels.size(); i++) {
				CompoundTag l = levels.getCompoundOrEmpty(i);
				String id = l.getStringOr("id", "");
				int minTier = l.getIntOr("min_tier", 0);
				boolean ok = vip >= minTier;
				Component label = PokerText.gui("stakes." + id, Texts.number(l.getLongOr("sb", 1)), Texts.number(l.getLongOr("bb", 2)));
				row.add(button(label, null, CardButton.Family.TABLE, ok, () -> {
					pickedLevel = id;
					buyIn = -1;
					rebuildConsole();
				}).hint(ok ? null : Component.translatable("gui.burmaldaholic.common.requires_vip", VipTiers.name(minTier))));
			}
			return;
		}
		CompoundTag l = level(levelId);
		if (l == null) return;
		long min = l.getLongOr("buy_min", 0);
		long max = l.getLongOr("buy_max", 0);
		long bb = Math.max(1, l.getLongOr("bb", 2));
		boolean can = max >= min && max > 0 && vip >= l.getIntOr("min_tier", 0);
		if (can) {
			if (buyIn < min || buyIn > max) buyIn = max;
			extras.add(new AmountSlider(compact() ? 100 : 120, min, max, bb, buyIn,
				v -> PokerText.gui("buy_in").append(Texts.raw(": ")).append(Texts.number(v)), v -> buyIn = v));
		}
		String id = levelId;
		if (s().getStringOr("stake", "").isEmpty()) {
			row.add(button(Component.translatable("gui.burmaldaholic.common.back"), null, CardButton.Family.TABLE, true, () -> {
				pickedLevel = null;
				rebuildConsole();
			}));
		}
		row.add(button(PokerText.gui("sit_down"), "deal", CardButton.Family.PRIMARY, can, () -> {
			CompoundTag args = new CompoundTag();
			args.putString("level", id);
			args.putLong("amount", buyIn);
			sendAction("buy_in", args);
		}).hint(rakeInfo(l)));
	}

	private Component rakeInfo(CompoundTag level) {
		long permille = s().getLongOr("rake_permille", 50);
		String pct = permille % 10 == 0 ? Long.toString(permille / 10) : (permille / 10) + "." + (permille % 10);
		return PokerText.gui("rake_info", Texts.raw(pct), Texts.number(level.getLongOr("rake_cap", 0)));
	}

	private void buildTopUp(List<CardButton> row) {
		long max = s().getLongOr("top_max", 0);
		if (max <= 0 || !s().getBooleanOr("can_top_up", false)) {
			topUpMode = false;
			return;
		}
		if (topUp < 1 || topUp > max) topUp = max;
		extras.add(new AmountSlider(compact() ? 100 : 120, 1, max, 1, topUp,
			v -> PokerText.gui("top_up").append(Texts.raw(": ")).append(Texts.number(v)), v -> topUp = v));
		row.add(button(Component.translatable("gui.burmaldaholic.common.back"), null, CardButton.Family.TABLE, true, () -> {
			topUpMode = false;
			rebuildConsole();
		}));
		row.add(button(PokerText.gui("top_up"), "deal", CardButton.Family.PRIMARY, true, () -> {
			CompoundTag args = new CompoundTag();
			args.putLong("amount", topUp);
			sendAction("top_up", args);
			topUpMode = false;
		}));
	}

	private void sendAct(String kind, long to, int seq) {
		CompoundTag args = new CompoundTag();
		args.putString("kind", kind);
		args.putLong("to", to);
		args.putInt("seq", seq);
		sendAction("act", args);
	}

	/** Fold (table), Check / Call (primary), Raise to (primary); the raise slider row with ½ ¾ Pot All-in (danger). */
	private void buildTurn(List<CardButton> row, List<CardButton> row2, CompoundTag l) {
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
		if (!canCheck) {
			row.add(button(PokerText.gui("fold"), "fold", CardButton.Family.TABLE, true, () -> sendAct("fold", 0, seq))
				.hint(PokerText.gui("fold.tooltip")));
		}
		if (canCheck) {
			row.add(button(PokerText.gui("check"), "check", CardButton.Family.PRIMARY, true, () -> sendAct("check", 0, seq)));
		} else if (myStack <= toCall) {
			row.add(button(PokerText.gui("all_in", Texts.number(myBet + myStack)), "all_in", CardButton.Family.DANGER, true,
				() -> sendAct("call", 0, seq)));
		} else {
			row.add(button(PokerText.gui("call", Texts.number(toCall)), "call", CardButton.Family.PRIMARY, true, () -> sendAct("call", 0, seq)));
		}
		if (!canRaise) return;
		if (raiseSeq != seq || raiseTo < minTo || raiseTo > maxTo) {
			raiseSeq = seq;
			raiseTo = minTo;
		}
		CardButton confirm = button(raiseLabel(raiseTo, maxTo, isBet), "raise", CardButton.Family.PRIMARY, true, () -> {
			if (raiseTo >= maxTo) sendAct("all_in", 0, seq);
			else sendAct("raise", raiseTo, seq);
		});
		int widest = Math.max(CardButton.naturalWidth(font, raiseLabel(maxTo, maxTo, isBet), true),
			CardButton.naturalWidth(font, raiseLabel(Math.max(minTo, maxTo - 1), maxTo, isBet), true));
		confirm.setWidth(Math.max(confirm.getWidth(), widest));
		// the sizing controls live in a panel above the console (one console row keeps the viewer's seat visible)
		row.add(button(Texts.raw(raiseOpen ? "▾" : "▴"), null, CardButton.Family.TABLE, true, () -> {
			raiseOpen = !raiseOpen;
			rebuildConsole();
		}).hint(PokerText.gui("raise")));
		row.add(confirm);
		if (!raiseOpen) return;
		AmountSlider slider = new AmountSlider(compact() ? 70 : 96, minTo, maxTo, bb, raiseTo, Texts::number, v -> {
			raiseTo = v;
			confirm.setMessage(raiseLabel(v, maxTo, isBet));
		});
		extras.add(slider);
		String[] keys = {"half_pot", "three_quarter_pot", "pot_size"};
		double[] fractions = {0.5, 0.75, 1.0};
		for (int k = 0; k < keys.length; k++) {
			long to = currentBet == 0 ? Math.round(pot * fractions[k]) : Math.round(currentBet + fractions[k] * (pot + toCall));
			long clamped = Math.max(minTo, Math.min(maxTo, to));
			CardButton b = button(PokerText.gui(keys[k]), null, CardButton.Family.TABLE, true, () -> slider.setAmount(clamped));
			b.setWidth(Math.max(compact() ? 30 : 34, CardButton.naturalWidth(font, PokerText.gui(keys[k]), false) - 6));
			row2.add(b.hint(Texts.number(clamped)));
		}
		row2.add(button(PokerText.gui("all_in", Texts.number(maxTo)), "all_in", CardButton.Family.DANGER, true, () -> slider.setAmount(maxTo)));
	}

	private static Component raiseLabel(long to, long maxTo, boolean isBet) {
		if (to >= maxTo) return PokerText.gui("all_in", Texts.number(maxTo));
		return isBet ? PokerText.gui("bet").append(Texts.raw(" ")).append(Texts.number(to)) : PokerText.gui("raise_to", Texts.number(to));
	}

	/** Slider over [min, max] in steps (bb); every step change plays {@code chip_count} (≤ 15/s by the sound budget). */
	private final class AmountSlider extends AbstractSliderButton {
		private final long min;
		private final long max;
		private final long step;
		private final LongFunction<Component> label;
		private final LongConsumer onChange;
		private long shown = Long.MIN_VALUE;

		AmountSlider(int width, long min, long max, long step, long initial, LongFunction<Component> label, LongConsumer onChange) {
			super(0, 0, width, 20, Component.empty(), 0);
			this.min = min;
			this.max = Math.max(min, max);
			this.step = Math.max(1, step);
			this.label = label;
			this.onChange = onChange;
			setAmountSilently(initial);
		}

		long amount() {
			if (max <= min || value >= 1.0) return max;
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
			long a = amount();
			if (a != shown && shown != Long.MIN_VALUE) {
				double f = max <= min ? 1 : (a - min) / (double) (max - min);
				FxSounds.play("chip_count", 0.5f, (float) (0.9 + 0.5 * f));
			}
			shown = a;
			onChange.accept(a);
		}
	}

	// ---- the scene ---------------------------------------------------------------------------------------------

	private int backRow() {
		return tableTheme().back(CardSprites.BACK_CRIMSON);
	}

	/** Poker card (rank index × 4 + suit) → the kit's card code (suit × 13 + rank − 1, ace = 1). */
	static int kit(int card) {
		int r = (card >> 2) + 2;
		return (card & 3) * 13 + (r == 14 ? 0 : r - 1);
	}

	/** Card slot keys are per hand, so a new hand deals fresh cards and the last hand's cards gather to the muck. */
	private int handKey() {
		return Math.floorMod(handNo, 50) * 20_000;
	}

	private @Nullable SeatView seat(int index) {
		for (SeatView v : seats) if (v.index == index) return v;
		return null;
	}

	private HoldemLayout.Slot slotOf(int index) {
		return HoldemLayout.slotOf(index, me, size);
	}

	private boolean awardStarted(int lane) {
		return fx != null && "finish".equals(fxKind) && PokerBeats.started(fx, PokerBeats.AWARD, fxMs()) > lane;
	}

	private boolean winner(int seat) {
		if (!(fxDone || awardStarted(0))) return false;
		for (Award a : awards) for (int s : a.seats()) if (s == seat) return true;
		return false;
	}

	@Override
	protected void drawScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		hovers.clear();
		int mx = mouseX;
		int my = mouseY;
		if (compact()) {
			// the full layout, scaled into the compact table (visual §6.7: anchors × 280/408)
			float k = 280f / 408f;
			g.pose().pushMatrix();
			g.pose().translate(tableX(), tableY());
			g.pose().scale(k, k);
			g.pose().translate(-HoldemLayout.TABLE_X, -HoldemLayout.TABLE_Y);
			mx = Math.round((mouseX - tableX()) / k) + HoldemLayout.TABLE_X;
			my = Math.round((mouseY - tableY()) / k) + HoldemLayout.TABLE_Y;
		}
		if (!s().getBooleanOr("enabled", true)) {
			CardGfx.centered(g, font, Component.translatable("gui.burmaldaholic.error.disabled"), HoldemLayout.CENTER_X, HoldemLayout.CENTER_Y,
				CasinoPalette.CHIP_RED_LIGHT, true);
		} else {
			drawFelt(g);
			drawCards(g);
			drawChips(g);
			drawButton(g);
			drawPlates(g, mx, my);
			drawLabels(g);
		}
		if (compact()) g.pose().popMatrix();
	}

	/** Prints: the pot well and the board slots (tinted, 55 %). */
	private void drawFelt(GuiGraphicsExtractor g) {
		int print = tableTheme().printArgb(0.55);
		Rect p = HoldemLayout.potPrint();
		CardGfx.sprite(g, FxSprites.sprite("cards/print/pot"), p.x(), p.y(), p.w(), p.h(), CardGfx.alpha(print, 0.35 / 0.55));
		boolean handOn = live || presenting || !seats.isEmpty() && handNo > 0;
		if (handOn) {
			for (int i = board.length; i < 5; i++) {
				Rect b = HoldemLayout.board(i);
				CardGfx.sprite(g, FxSprites.sprite("cards/print/slot_l"), b.x(), b.y(), b.w(), b.h(), CardGfx.alpha(print, 0.3 / 0.55));
			}
		}
		Rect d = HoldemLayout.deck();
		CardGfx.sprite(g, FxSprites.sprite("cards/prop/deck_" + backName()), d.x(), d.y(), d.w(), d.h(), 0xFFFFFFFF, 0xFF8A1A2A);
	}

	private String backName() {
		return switch (tableTheme()) {
			case BASTION -> "bastion";
			case END -> "end";
			default -> "crimson";
		};
	}

	/** Board and hole cards through the kit's animator (deal arcs, flips, gather to the muck, dim / lift / glow). */
	private void drawCards(GuiGraphicsExtractor g) {
		Rect deck = HoldemLayout.deck();
		cards.origin(HoldemLayout.dealOriginX() - 18, HoldemLayout.dealOriginY() - 24).tray(deck.x(), deck.y());
		cards.begin(nowMs(), FxSettings.reduceMotion());
		int back = backRow();
		boolean best = started(PokerBeats.BEST) || (!live && !presenting && handNo > 0 && !awards.isEmpty());
		int winnerMask = 0; // board bits (0..4) of the winners' best five
		for (SeatView v : seats) if (v.best >= 0 && winner(v.index)) winnerMask |= v.best >> 2;
		boolean[] boardInBest = new boolean[5];
		for (int i = 0; i < 5; i++) boardInBest[i] = (winnerMask & (1 << i)) != 0;
		boolean street = "street".equals(fxKind) || "finish".equals(fxKind);
		for (int i = 0; i < board.length; i++) {
			Rect r = HoldemLayout.board(i);
			double dealAt = street ? beatAt(PokerBeats.SLIDE, i) : Double.NaN;
			double revealAt = street ? beatAt(PokerBeats.FLIP, i) : Double.NaN;
			CardAnimator.Slot slot = cards.card(handKey() + 100 + i, kit(board[i]), CardSprites.L, back, r.x(), r.y(), 0, dealAt, revealAt);
			if (best && winnerMask != 0) {
				cards.lift(slot, boardInBest[i]);
				cards.glow(slot, boardInBest[i]);
				cards.dim(slot, !boardInBest[i]);
			}
		}
		boolean dealing = "deal".equals(fxKind);
		for (SeatView v : seats) {
			if (!v.dealt || (v.folded && !v.you && v.cards.length == 0) || v.mucked) continue;
			if (v.folded) continue; // mucked: the cards gather to the deck
			HoldemLayout.Slot sl = slotOf(v.index);
			int size = sl.large() ? CardSprites.L : CardSprites.M;
			int n = v.cards.length > 0 ? v.cards.length : v.hidden;
			for (int k = 0; k < n; k++) {
				Rect r = sl.card(k);
				int code = k < v.cards.length ? kit(v.cards[k]) : -1;
				double dealAt = Double.NaN;
				double revealAt = Double.NaN;
				if (dealing && fx != null) {
					for (Beat b : fx.beats()) {
						if (b.kind().equals(PokerBeats.DEAL) && b.lane() == v.handIndex && b.arg(0) == k) dealAt = fxStart * 50.0 + b.at();
					}
					if (v.you) revealAt = beatAt(PokerBeats.LIFT, v.handIndex);
				}
				CardAnimator.Slot slot = cards.card(handKey() + 1000 + v.index * 10 + k, code, size, back, r.x(), r.y(), 0, dealAt, revealAt);
				if (best && code >= 0 && v.best >= 0) {
					boolean in = (v.best & (1 << k)) != 0 && winner(v.index);
					cards.lift(slot, in);
					cards.glow(slot, in);
					cards.dim(slot, !winner(v.index));
				}
				if (code >= 0) hovers.add(new Hover(r.x(), r.y(), r.w(), r.h(), CardSprites.narration(code)));
			}
		}
		cards.end();
		cards.draw(g, font);
	}

	/** Bet stacks on the bet line, the gather to the pot, the pot and side pots, the award slides. */
	private void drawChips(GuiGraphicsExtractor g) {
		double t = fxMs();
		Beat gather = beat(PokerBeats.GATHER, -1);
		boolean gathering = gather != null && gatherBets.length > 0 && t < gather.end() + 50;
		for (SeatView v : seats) {
			HoldemLayout.Slot sl = slotOf(v.index);
			int[] spot = HoldemLayout.betSpot(sl, plateWidth(v));
			int tint = v.you ? 0 : ChipStackView.seatTint(v.index);
			if (gathering && v.index < gatherBets.length && gatherBets[v.index] > 0) {
				double u = gather == null || t < gather.at() ? 0 : Math.min(1, (t - gather.at()) / gather.dur());
				double p = Ease.OUT_CUBIC.apply(u);
				int x = (int) Math.round(spot[0] + (HoldemLayout.potX(0) - spot[0]) * p);
				int y = (int) Math.round(spot[1] + (HoldemLayout.potY() - spot[1]) * p);
				if (u < 1) ChipStackView.stack(g, x, y + 4, gatherBets[v.index], 5, tint, false, 1 - Math.max(0, u - 0.8) * 5);
				continue;
			}
			if (live && v.bet > 0) {
				ChipStackView.stack(g, spot[0], spot[1] + 4, v.bet, 5, tint, false, 1);
				if (v.you) ChipStackView.label(g, font, spot[0], spot[1] + 4, v.bet, CasinoPalette.GOLD, 1);
				else hovers.add(new Hover(spot[0] - 7, spot[1] - 8, 14, 12, Texts.number(v.bet)));
			}
		}
		int potY = HoldemLayout.potY() + 6;
		if (live) {
			long[] list = pots.length > 0 ? pots : potTotal > 0 ? new long[] {potTotal} : new long[0];
			if (gathering) list = new long[] {potTotal};
			for (int i = 0; i < list.length; i++) {
				if (list[i] <= 0) continue;
				int x = HoldemLayout.potX(i);
				ChipStackView.stack(g, x, potY, list[i], 5, 0, false, 1);
				ChipStackView.label(g, font, x, potY, list[i], CasinoPalette.GOLD, 1);
				hovers.add(new Hover(x - 8, potY - 16, 16, 26, i == 0 ? PokerText.gui("pot", Texts.number(list[i]))
					: PokerText.gui("side_pot", Texts.number(i), Texts.number(list[i]))));
			}
			return;
		}
		if (!presenting && !fxDone) return;
		// finished hand: the pot waits in the middle, then each award slides to its winners (side pots first)
		if (awards.isEmpty()) {
			if (presenting && potTotal > 0) {
				ChipStackView.stack(g, HoldemLayout.potX(0), potY, potTotal, 5, 0, false, 1);
				ChipStackView.label(g, font, HoldemLayout.potX(0), potY, potTotal, CasinoPalette.GOLD, 1);
			}
			return;
		}
		for (int lane = 0; lane < awards.size(); lane++) {
			Award a = awards.get(lane);
			Beat b = beat(PokerBeats.AWARD, lane);
			double u = !presenting || b == null ? 1 : t < b.at() ? 0 : Math.min(1, (t - b.at()) / b.dur());
			if (u >= 1 && !presenting) continue; // settled: the chips are in the winners' stacks
			if (u > 0 && soundedAward != handNo * 16 + lane && presenting) {
				soundedAward = handNo * 16 + lane;
				FxSounds.play("pot_win", 0.8f, 1f);
			}
			int sx = HoldemLayout.potX(lane);
			for (int w = 0; w < a.seats().length; w++) {
				SeatView v = seat(a.seats()[w]);
				if (v == null) continue;
				Rect plate = HoldemLayout.plate(slotOf(v.index), plateWidth(v));
				double p = Ease.IN_OUT_CUBIC.apply(u);
				int x = (int) Math.round(sx + (plate.cx() - sx) * p);
				int y = (int) Math.round(potY + (plate.y() - potY) * p);
				long share = w < a.shares().length ? a.shares()[w] : a.amount();
				double alpha = u < 0.85 ? 1 : 1 - (u - 0.85) / 0.15;
				ChipStackView.stack(g, x, y, share, 5, 0, false, alpha);
				if (u == 0) ChipStackView.label(g, font, x, y, a.amount(), CasinoPalette.GOLD, 1);
			}
		}
	}

	private void drawButton(GuiGraphicsExtractor g) {
		if (button < 0 || handNo <= 0) return;
		HoldemLayout.Slot to = slotOf(button);
		int[] p;
		Beat b = "deal".equals(fxKind) ? beat(PokerBeats.BUTTON, -1) : null;
		if (b != null && prevButton >= 0 && prevButton != button && !FxSettings.reduceMotion()) {
			p = HoldemLayout.button(slotOf(prevButton), to, b.progress(fxMs()));
		} else {
			p = HoldemLayout.button(to, to, 1);
		}
		CardGfx.sprite(g, FxSprites.sprite("cards/prop/dealer_button"), p[0] - 6, p[1] - 6, 13, 13, 0xFFFFFFFF, CasinoPalette.BONE);
		hovers.add(new Hover(p[0] - 6, p[1] - 6, 13, 13, PokerText.gui("button")));
	}

	private SeatPlate.Info plateInfo(SeatView v) {
		SeatPlate.State state = v.you ? SeatPlate.State.ME : SeatPlate.State.NORMAL;
		if ((v.folded && (live || presenting)) || v.out) state = SeatPlate.State.FOLDED;
		if (v.index == toAct) state = SeatPlate.State.ACTIVE;
		if (winner(v.index) && !live) state = SeatPlate.State.WINNER;
		Component sub = Texts.number(v.stack);
		int subColor = CasinoPalette.GOLD;
		if (v.out) {
			sub = PokerText.gui("sitting_out");
			subColor = 0xFFB0A0C0;
		}
		boolean thinking = v.bot && v.index == toAct;
		String avatar = v.bot ? tableTheme().botAvatar(v.botName != null ? v.botName : v.rawName) : null;
		return new SeatPlate.Info(fit(v.name, 62), v.bot ? null : v.rawName, avatar, v.bot ? v.level : 0, sub, subColor, state, thinking);
	}

	/** A name cut with "…" to {@code maxW} px (plates stay off the board; the full name is in the tooltip). */
	private Component fit(Component name, int maxW) {
		if (font.width(name) <= maxW) return name;
		return Texts.raw(font.plainSubstrByWidth(name.getString(), maxW - font.width("…")) + "…");
	}

	private int plateWidth(SeatView v) {
		return SeatPlate.width(font, plateInfo(v));
	}

	private void drawPlates(GuiGraphicsExtractor g, int mx, int my) {
		for (SeatView v : seats) {
			HoldemLayout.Slot sl = slotOf(v.index);
			SeatPlate.Info info = plateInfo(v);
			int w = SeatPlate.width(font, info);
			Rect r = HoldemLayout.plate(sl, w);
			int dy = 0;
			if (v.index == lastActionSeat && live && lastActionText.getString().equals(PokerText.gui("tag.check").getString())) {
				dy = CardMotion.knockDy(Math.min(1, (nowMs() - lastActionAt) / CardMotion.KNOCK_MS));
			}
			if (info.state() == SeatPlate.State.ACTIVE) {
				CardGfx.sprite(g, FxSprites.sprite("cards/fx/spot_glow"), r.x() - 3, r.y() - 3, r.w() + 6, r.h() + 6,
					CardGfx.white(CardMotion.glowAlpha((long) nowMs(), FxSettings.reduceMotion(), FxSettings.flashes())));
			}
			SeatPlate.draw(g, font, info, r.x(), r.y(), 1, dy);
			// the acting human's timer bar (bots never use the human timer, BOTS.md §7.3)
			if (v.index == toAct && !v.bot) {
				long left = ticksLeft("action");
				if (left >= 0) {
					int total = Math.max(1, CasinoConfig.poker().actionTimerTicks);
					int bw = (int) Math.round((r.w() - 4) * Math.min(1.0, left / (double) total));
					boolean hurry = left < 100;
					int color = hurry && FxSettings.flashes() && (clientTicks / 5) % 2 == 0 ? CasinoPalette.CHIP_RED_LIGHT : hurry ? CasinoPalette.CHIP_RED
						: CasinoPalette.GOLD;
					g.fill(r.x() + 2, r.y() + r.h(), r.x() + 2 + bw, r.y() + r.h() + 2, color);
				}
			}
			MutableComponent tip = Component.empty().append(v.bot ? Component.translatable("gui.burmaldaholic.bots.display", v.name) : v.name);
			if (v.bot && v.level > 0) {
				String[] ids = {"", "easy", "normal", "hard"};
				tip.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.bots.level." + ids[v.level]));
			}
			tip.append(Texts.raw("\n")).append(PokerText.gui("stack", Texts.number(v.stack)));
			hovers.add(new Hover(r.x(), r.y(), r.w(), r.h(), tip));
		}
	}

	/**
	 * Hand names, action tags, "Mucks", the ALL-IN stamps and the pot moment, placed so that none covers a card (hard
	 * obstacles: the board, every seat's hole cards, the deck) — the mockup's label/card overlap fixed.
	 */
	private void drawLabels(GuiGraphicsExtractor g) {
		List<Rect> hard = new ArrayList<>(HoldemLayout.cardRects(size));
		List<Rect> soft = new ArrayList<>();
		for (SeatView v : seats) soft.add(HoldemLayout.plate(slotOf(v.index), plateWidth(v)));
		List<LabelPlacer.Request> reqs = new ArrayList<>();
		List<Component> texts = new ArrayList<>();
		List<Double> ages = new ArrayList<>();
		boolean showdownNames = !live && (presenting || handNo > 0);
		for (SeatView v : seats) {
			Component text = null;
			double age = -1;
			if (v.mucked) {
				text = PokerText.gui("tag.mucks");
			} else if (v.hand != null && (v.you || showdownNames) && !v.folded) {
				text = v.hand;
			}
			if (text == null && live && v.index == lastActionSeat && !Double.isNaN(lastActionAt) && !lastActionText.getString().isEmpty()) {
				text = lastActionText;
				age = nowMs() - lastActionAt;
				if (age > CardMotion.tagTotalMs()) text = null;
			}
			if (text == null) continue;
			HoldemLayout.Slot sl = slotOf(v.index);
			reqs.add(HoldemLayout.tagRequest(sl, plateWidth(v), font.width(text) + 8));
			texts.add(text);
			ages.add(age);
		}
		List<Rect> placed = LabelPlacer.place(hard, soft, HoldemLayout.labelBounds(), reqs);
		for (int i = 0; i < placed.size(); i++) {
			Rect r = placed.get(i);
			if (ages.get(i) >= 0) ActionTag.timed(g, font, texts.get(i), r.cx(), r.y(), (long) (double) ages.get(i), FxSettings.reduceMotion());
			else ActionTag.draw(g, font, texts.get(i), r.cx(), r.y(), 1);
			hard.add(r);
		}
		// ALL-IN stamps over the all-in seats' plates, the pot moment over the pot (K7)
		for (SeatView v : seats) {
			if (!v.allIn || !(live || presenting) || v.index >= allInAt.length || Double.isNaN(allInAt[v.index])) continue;
			Rect plate = HoldemLayout.plate(slotOf(v.index), plateWidth(v));
			Component text = PokerText.gui("all_in_tag");
			int w = font.width(text) + 14;
			Rect spot = LabelPlacer.place(hard, List.of(), HoldemLayout.labelBounds(),
				List.of(new LabelPlacer.Request(w, 16, plate.x() + 4, plate.y() - 14, plate.x() + 4, plate.y() + plate.h() - 2))).get(0);
			TableStamp.draw(g, font, text, TableStamp.Kind.RED, spot.cx(), spot.cy(), -6, nowMs() - allInAt[v.index], FxSettings.reduceMotion(), pose);
		}
		if (!moment.isEmpty() && (presenting || fxDone && !live)) {
			Beat award = beat(PokerBeats.AWARD, 0);
			double age = award == null ? 1000 : fxMs() - award.at() + 400;
			Component text = PokerText.gui("monster".equals(moment) ? "fx.monster_pot" : "fx.big_pot");
			int w = font.width(text) + 14;
			Rect pot = HoldemLayout.potPrint();
			Rect spot = LabelPlacer.place(hard, List.of(), HoldemLayout.labelBounds(),
				List.of(new LabelPlacer.Request(w, 16, pot.cx() - w / 2, pot.y() + 2, pot.x() + pot.w() + 4, pot.y()))).get(0);
			TableStamp.draw(g, font, text, "monster".equals(moment) ? TableStamp.Kind.GOLD : TableStamp.Kind.VIOLET, spot.cx(), spot.cy(), -3, age,
				FxSettings.reduceMotion(), pose);
		}
	}

	// ---- console text, overlay, tooltips ------------------------------------------------------------------------

	@Override
	protected void drawConsoleText(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = compact() ? 4 : 8;
		int y1 = compact() ? 141 : 212;
		int y2 = y1 + 12;
		int maxW = Math.max(40, consoleTextRight() - x);
		if (!extras.isEmpty() && !compact()) maxW = Math.max(40, extras.get(extras.size() - 1).getX() - 4 - x);
		Component l1 = null;
		Component l2 = null;
		int c1 = CasinoPalette.BONE;
		CompoundTag l = legal();
		if (l != null) {
			MutableComponent c = PokerText.gui("your_turn").copy();
			long toCall = l.getLongOr("to_call", 0);
			if (toCall > 0) c.append(Texts.raw(" · ")).append(PokerText.gui("to_call", Texts.number(toCall)));
			l1 = c;
			c1 = GREEN;
			long left = ticksLeft("action");
			if (left >= 0) {
				Component auto = PokerText.gui(l.getBooleanOr("can_check", false) ? "check" : "fold");
				l2 = Component.translatable("gui.burmaldaholic.common.auto_action", auto, Texts.plural("unit.burmaldaholic.second_acc", (left + 19) / 20));
			}
		} else if (presenting) {
			boolean runout = fxArgs.length >= 2 && fxArgs[1] > 0 && !started(PokerBeats.SHOW);
			l1 = runout ? PokerText.gui("fx.all_in_runout") : PokerText.gui("showdown");
			c1 = CasinoPalette.GOLD;
		} else if (!live && !results.isEmpty()) {
			l1 = results.get(0);
			c1 = GREEN;
			long next = ticksLeft("next_hand");
			if (next >= 0) l2 = PokerText.gui("next_hand_in", Texts.number((next + 19) / 20));
		} else if (seated()) {
			if (s().getBooleanOr("leaving", false)) l1 = PokerText.msg("leaving_after_hand");
			else if (s().getBooleanOr("sitting_out", false)) l1 = PokerText.gui("sitting_out");
			else if (!live) l1 = seats.size() < 2 ? Component.translatable("gui.burmaldaholic.common.waiting_players") : PokerText.gui("waiting_hand");
		} else {
			CompoundTag lv = levelId() == null ? null : level(levelId());
			l1 = lv == null ? PokerText.gui("choose_stakes")
				: PokerText.gui("buy_in_range", Texts.number(lv.getLongOr("full_min", 0)), Texts.number(lv.getLongOr("full_max", 0)));
			l2 = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		}
		if (l1 != null) CardGfx.fitted(g, font, l1, x, y1, maxW, c1, true);
		if (l2 != null) CardGfx.fitted(g, font, l2, x, y2, maxW, 0xFFB0A0C0, true);
		if (results.size() > 1 && !live && !presenting) hovers.add(new Hover(x, y1, maxW, 20, joined(results)));
		List<Component> log = logLines();
		if (!log.isEmpty() && !compact()) {
			Component hist = Component.translatable("gui.burmaldaholic.common.history");
			int hw = font.width(hist);
			int hx = canvasW() - hw - 8;
			CardGfx.text(g, font, hist, hx, 20, 0xFFB8A8FF, true);
			hovers.add(new Hover(hx, 20, hw, 9, joined(log)));
		}
	}

	private List<Component> logLines() {
		List<Component> out = new ArrayList<>();
		ListTag log = s().getListOrEmpty("log");
		for (int i = 0; i < log.size(); i++) out.add(decode(log.get(i)));
		return out;
	}

	private static Component joined(List<Component> lines) {
		MutableComponent all = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) all.append(Texts.raw("\n"));
			all.append(lines.get(i));
		}
		return all;
	}

	@Override
	protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		if (panelX >= 0) {
			CardGfx.sprite(g, FxSprites.sprite("cards/panel/road"), panelX, panelY, canvasW() - 4 - panelX, 26, 0xFFFFFFFF, 0xE0180A28);
			for (CardButton b : panel) b.extractRenderState(g, mouseX, mouseY, partial);
		}
		for (AbstractWidget w : extras) w.extractRenderState(g, mouseX, mouseY, partial);
	}

	@Override
	protected void drawTooltips(GuiGraphicsExtractor g, int mx, int my, int sx, int sy) {
		int hx = mx;
		int hy = my;
		if (compact()) {
			float k = 280f / 408f;
			hx = Math.round((mx - tableX()) / k) + HoldemLayout.TABLE_X;
			hy = Math.round((my - tableY()) / k) + HoldemLayout.TABLE_Y;
		}
		for (int i = hovers.size() - 1; i >= 0; i--) {
			Hover h = hovers.get(i);
			boolean console = h.y() >= 200;
			int tx = console ? mx : hx;
			int ty = console ? my : hy;
			if (tx >= h.x() && tx < h.x() + h.w() && ty >= h.y() && ty < h.y() + h.h()) {
				g.setTooltipForNextFrame(font, font.split(h.text(), 220), sx, sy);
				return;
			}
		}
	}
}
