package dev.nezo.burmaldaholic.games.uth.client;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.cards.CardAnimator;
import dev.nezo.burmaldaholic.client.table.cards.CardSprites;
import dev.nezo.burmaldaholic.client.table.cards.TableTheme;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.FeltPrint;
import dev.nezo.burmaldaholic.client.ui.v2.SeatPlate;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Felt;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.poker.client.V2Cards;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.logic.PayHand;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import dev.nezo.burmaldaholic.games.uth.present.UthBetDraft;
import dev.nezo.burmaldaholic.games.uth.present.UthFlow;
import dev.nezo.burmaldaholic.games.uth.present.UthPub;
import dev.nezo.burmaldaholic.games.uth.present.UthTableLayout;
import dev.nezo.burmaldaholic.games.uth.present.UthView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Ultimate Texas Hold'em, table UI v2 (REDESIGN.md §7.4; docs/architecture/ui-v2.md): the dealer's cards at the top
 * centre, the board in the middle, the viewer's cards bottom-centre with the four bet spots Trips · Ante · Blind · Play
 * right of them, the other seats in the side columns, and the kit's Dock.
 *
 * <p>Betting (the v0.1.1 report "I could not change my UTH bet"): the BetBar edits a local {@link UthBetDraft} — a chip
 * adds to the armed spot (Ante, or Trips after clicking the Trips spot), right click takes one off, Undo / Clear /
 * Rebet / ×2 work, the readout and the status line show the cost (Ante + Blind + Trips), and the gold primary
 * <b>Deal · 30</b> sends {@code bet {ante, trips}}. While others still bet, the primary is <b>Change bet</b> and any
 * chip or tool changes it too: {@code clear} is sent (the server refunds) and the draft keeps the amounts to edit.
 * Decisions: ActionRow {@code Check · Bet ×3} + primary {@code Bet ×4} pre-flop, {@code Check} + {@code Bet ×2} on the
 * flop, {@code Fold} + {@code Bet ×1} on the river. Player bank: {@code take_bank {amount}} (stepper),
 * {@code leave_bank}. Bot settings and the paytable live behind the TopBar icons.
 */
public class UthScreen extends V2TableScreen<UthView> {
	private static final int[] NO_CARDS = new int[0];
	private static final String STEPPER = "~stepper";
	public static final String STEP_MINUS = "stepper.minus", STEP_PLUS = "stepper.plus";

	private final CardAnimator cards = new CardAnimator();
	private UthBetDraft draft = UthBetDraft.empty();
	private boolean draftLoaded;
	private boolean bankForm;
	private long bankAmount = -1;
	private boolean rulesOpen;
	private int clientTicks;
	private int stateTick;
	private @Nullable String previewBet;
	private UthTableLayout.@Nullable Layout layout;

	private int fxSeq = -1;
	private int fxKind;
	private long fxStart;
	private @Nullable Timeline fx;
	private int roundKey;
	private int celebrated = -1;

	private final Flow<UthView> flow = new Flow<>() {
		@Override
		public Gate primary(UthView v) {
			return UthFlow.primary(v);
		}

		@Override
		public List<Gate> actions(UthView v) {
			List<Gate> out = new ArrayList<>();
			if (v.deciding() || (v.bank() != null && v.bank().form() && v.betting())) out.addAll(UthFlow.actions(v));
			UthFlow.stepper(v).ifPresent(s -> out.add(new Gate(STEPPER, "gui.burmaldaholic.poker.v2.stepper", new Object[] {s.value()}, true, null, null)));
			return out;
		}

		@Override
		public @Nullable BetView bet(UthView v) {
			if (!v.betting() || v.deciding()) return null;
			UthBetDraft d = v.draft();
			return BetView.builder().seated(v.seated() && !v.banker()).canSit(!v.seated()).phase(BetView.Phase.BETTING).bettingOpen(true)
				.limits(v.minAnte(), v.maxW()).balance(v.balance()).myTotal(d.cost()).canUndo(d.canUndo()).canClear(!d.isEmpty())
				.rebet(v.lastAnte() > 0, 2 * v.lastAnte() + v.lastTrips()).canDouble(!d.isEmpty()).secondsLeft(v.secondsLeft()).build();
		}

		@Override
		public Gate tool(String id, UthView v) {
			return UthFlow.tool(id, v);
		}

		@Override
		public Gate chip(long denom, UthView v) {
			Gate c = UthFlow.chips(v);
			return c.enabled() ? Gate.on(BetGates.CHIP, c.labelKey()) : Gate.off(BetGates.CHIP, c.labelKey(), c.reasonKey(), c.reasonArgs());
		}
	};

	public UthScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(titleKey(menu)));
	}

	private static String titleKey(CasinoTableMenu menu) {
		String name = menu.tableType().name();
		return name.endsWith("high_roller") ? "gui.burmaldaholic.uth.title_high_roller"
			: name.endsWith("player_banked") ? "gui.burmaldaholic.uth.title_player_banked" : "gui.burmaldaholic.uth.title";
	}

	// ---- GameTest hooks ---------------------------------------------------------------------------------------------

	/** The running segment kind ({@link UthPub#DEAL} …, 0 = betting). */
	public int fxKind() {
		return fxKind;
	}

	/** Shows the ghost Play stack of decision {@code id} as if its button were hovered (mockup screenshots). */
	public void previewBetForTests(@Nullable String id) {
		this.previewBet = id;
	}

	/** The bet being built (tests assert the draft after clicks). */
	public UthBetDraft draft() {
		return draft;
	}

	public UthTableLayout.@Nullable Layout layout() {
		return layout;
	}

	// ---- state ------------------------------------------------------------------------------------------------------

	private CompoundTag s() {
		return state();
	}

	private CompoundTag bank() {
		return s().getCompoundOrEmpty("bank");
	}

	private boolean playerBanked() {
		return "player_banked".equals(s().getStringOr("variant", ""));
	}

	private boolean bettingPhase() {
		return "betting".equals(phase()) || "idle".equals(phase());
	}

	private long secondsLeft(String id) {
		long t = s().getCompoundOrEmpty("timers").getLongOr(id, -1);
		return t < 0 ? -1 : (Math.max(0, t - (clientTicks - stateTick)) + 19) / 20;
	}

	private List<String> legal() {
		List<String> out = new ArrayList<>();
		ListTag l = s().getListOrEmpty("legal");
		for (int i = 0; i < l.size(); i++) out.add(l.getStringOr(i, ""));
		return out;
	}

	private List<CompoundTag> players() {
		List<CompoundTag> out = new ArrayList<>();
		ListTag p = s().getListOrEmpty("players");
		for (int i = 0; i < p.size(); i++) out.add(p.getCompoundOrEmpty(i));
		return out;
	}

	private @Nullable CompoundTag me() {
		for (CompoundTag p : players()) if (p.getBooleanOr("you", false)) return p;
		return null;
	}

	private int myLane() {
		List<CompoundTag> ps = players();
		for (int i = 0; i < ps.size(); i++) if (ps.get(i).getBooleanOr("you", false)) return i;
		return -1;
	}

	private double nowMs() {
		return AnimClock.levelMs(minecraft == null ? 0 : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Override
	protected UthView view(CompoundTag st) {
		stateTick = clientTicks;
		CompoundTag f = st.getCompoundOrEmpty("fx");
		int seq = f.getIntOr("seq", -1);
		if (seq != fxSeq) {
			fxSeq = seq;
			fxKind = f.getIntOr("kind", 0);
			fxStart = f.getLongOr("start", 0);
			fx = UthTableBlockEntity.timelineOf(fxKind, f.getIntOr("seats", 1), f.getIntOr("seed", 0));
			if (fxKind == UthPub.DEAL) roundKey = seq;
		}
		if (f.isEmpty()) fxKind = 0;
		long myAnte = st.getLongOr("my_ante", 0);
		long[] confirmed = myAnte > 0 ? new long[] {myAnte, st.getLongOr("my_trips", 0)} : null;
		if (!draftLoaded) {
			// first state: the draft starts from the confirmed bet, else the last round's, else empty
			draftLoaded = true;
			if (confirmed != null) draft = UthBetDraft.empty().load(confirmed[0], confirmed[1]);
			else if (st.getLongOr("last_ante", 0) > 0) draft = UthBetDraft.empty().load(st.getLongOr("last_ante", 0), st.getLongOr("last_trips", 0));
		}
		celebrate(st);
		return build(st, confirmed);
	}

	private UthView build(CompoundTag st, long @Nullable [] confirmed) {
		CompoundTag b = st.getCompoundOrEmpty("bank");
		boolean banker = b.getBooleanOr("you", false);
		UthView.Phase phase = bettingPhase() ? UthView.Phase.BETTING
			: fxKind == UthPub.SHOWDOWN || fxKind == UthPub.RESULT || "result".equals(phase()) ? UthView.Phase.SHOWDOWN : UthView.Phase.PLAYING;
		CompoundTag me = me();
		String waiting = "";
		for (CompoundTag p : players()) {
			if (!p.getBooleanOr("you", false) && "deciding".equals(p.getStringOr("tag", ""))) {
				waiting = p.getBooleanOr("bot", false) ? Component.translatable(BotRoster.nameKey(p.getStringOr("bot_name", ""))).getString()
					: p.getStringOr("name", "");
				break;
			}
		}
		long sec = phase == UthView.Phase.BETTING ? secondsLeft("bet") : secondsLeft("decide");
		UthView.Bank bank = null;
		if (playerBanked() && b.getBooleanOr("enabled", false)) {
			long min = b.getLongOr("min_bank", 0);
			if (bankAmount < min) bankAmount = Math.max(min, Math.min(balance(), min * 2));
			bank = new UthView.Bank(b.getBooleanOr("can_take", false), b.getBooleanOr("leaving", false), bankForm && !banker, bankAmount, min);
		}
		return new UthView(mySeat() >= 0, banker, phase, confirmed, draft, Math.max(1, minBet()), maxBet() <= 0 ? Long.MAX_VALUE / 16 : maxBet(),
			st.getBooleanOr("trips_enabled", true), st.getLongOr("trips_min", 1), balance(), st.getLongOr("last_ante", 0), st.getLongOr("last_trips", 0),
			legal(), me == null ? 0 : me.getLongOr("ante", 0), me != null, waiting, (int) sec, bank);
	}

	/** Re-applies a local change (draft, form) without waiting for the server. */
	private void local() {
		view = build(s(), s().getLongOr("my_ante", 0) > 0 ? new long[] {s().getLongOr("my_ante", 0), s().getLongOr("my_trips", 0)} : null);
		refresh();
	}

	private void celebrate(CompoundTag st) {
		CompoundTag r = st.getCompoundOrEmpty("result");
		if (r.isEmpty() || celebrated == fxSeq) return;
		celebrated = fxSeq;
		chrome.banner.show(r.getLongOr("net", 0));
		try {
			WinTier tier = WinTier.valueOf(r.getStringOr("tier", "LOSS"));
			if (tier.isWin()) {
				CelebrationOverlay.get().play(CelebrationRequest.core(tier, r.getLongOr("ret", 0), Math.max(1, r.getLongOr("staked", 1)), SeedMix.mix(fxSeq, 7)));
			} else if (tier == WinTier.RETURN || tier == WinTier.PUSH) {
				FxSounds.play("push", 0.8f, 1f);
			}
		} catch (IllegalArgumentException ignored) {
			// unknown tier: no celebration
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		clientTicks++;
		if (clientTicks % 10 == 0 && view != null) local();
	}

	private double fxMs() {
		return nowMs() - fxStart * 50.0;
	}

	private @Nullable Beat beat(String kind, int lane) {
		if (fx == null) return null;
		for (Beat b : fx.beats()) if (b.kind().equals(kind) && b.lane() == lane) return b;
		return null;
	}

	private double beatAt(String kind, int lane) {
		Beat b = beat(kind, lane);
		return b == null ? Double.NaN : fxStart * 50.0 + b.at();
	}

	private double beatAt(String kind, int lane, int arg) {
		if (fx == null) return Double.NaN;
		for (Beat b : fx.beats()) if (b.kind().equals(kind) && b.lane() == lane && b.arg(0) == arg) return fxStart * 50.0 + b.at();
		return Double.NaN;
	}

	private boolean started(String kind) {
		Beat b = beat(kind, kind.equals(UthBeats.QUALIFY) || kind.equals(UthBeats.REVEAL) || kind.equals(UthBeats.GATHER) ? -1 : 0);
		return b != null && fxMs() >= b.at();
	}

	// ---- the kit's hooks -------------------------------------------------------------------------------------------

	@Override
	protected Flow<UthView> flow() {
		return flow;
	}

	@Override
	protected @Nullable BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				edit(draft.add(draft.spot(), denom));
			}

			@Override
			public void remove(long denom) {
				edit(draft.remove(draft.spot(), denom));
			}

			@Override
			public void tool(String toolId) {
				switch (toolId) {
					case BetGates.UNDO -> edit(draft.undo());
					case BetGates.CLEAR -> edit(draft.clear());
					case BetGates.REBET -> edit(draft.load(s().getLongOr("last_ante", 0), s().getLongOr("last_trips", 0)));
					case BetGates.DOUBLE -> edit(draft.doubled());
					default -> {
					}
				}
			}
		};
	}

	/**
	 * A draft edit from the BetBar or a felt spot. When the bet was already confirmed this IS the change: the server
	 * refunds it ({@code clear}) and the edited draft waits for Deal again.
	 */
	private void edit(UthBetDraft next) {
		UthView v = view;
		if (v == null || !v.betting() || !v.seated() || v.banker()) return;
		if (v.confirmed() != null) {
			sendTracked("clear", new CompoundTag(), "", 0);
			if (draft.isEmpty()) draft = draft.load(v.confirmed()[0], v.confirmed()[1]);
		}
		if (next.ante() != draft.ante() || next.trips() != draft.trips()) FxSounds.play("chip_place", 0.6f, 1.1f);
		draft = next;
		local();
	}

	@Override
	protected void refresh() {
		if (view == null || font == null) return;
		KeyMap k = KeyMap.standard();
		k.bind(KeyMap.C, KeyMap.Ids.action("check"));
		k.bind(KeyMap.F, KeyMap.Ids.action("fold"));
		k.bind(KeyMap.KEY_3, KeyMap.Ids.action("bet_3x"));
		k.bind(KeyMap.T, KeyMap.Ids.felt("trips"));
		k.bind(KeyMap.A, KeyMap.Ids.felt("ante"));
		chrome.keys(k);
		chrome.actions.danger("fold", "leave_bank");
		chrome.bet.multiSpot(false);
		chrome.secondary(secondary(view));
		super.refresh();
		status(view);
		stepperHits();
	}

	/** Player-bank controls while betting sit in the Dock's secondary slot (the BetBar keeps the row). */
	private @Nullable Gate secondary(UthView v) {
		if (!v.betting() || v.bank() == null || v.bank().form()) return null;
		List<Gate> a = UthFlow.actions(v);
		return a.isEmpty() ? null : a.get(0);
	}

	private void stepperHits() {
		Optional<Box> b = chrome.hits().box(KeyMap.Ids.action(STEPPER));
		if (b.isEmpty() || view == null) return;
		UthFlow.Stepper s = UthFlow.stepper(view).orElse(null);
		if (s == null) return;
		Box box = b.get();
		int third = Math.max(10, box.w() / 3);
		chrome.hits().add(STEP_MINUS, new Box(box.x(), box.y(), third, box.h()), s.value() > s.min());
		chrome.hits().add(STEP_PLUS, new Box(box.right() - third, box.y(), third, box.h()), s.value() < s.max());
	}

	private void status(UthView v) {
		Component l1 = null;
		Component l2 = null;
		int c1 = CasinoPalette.V2_GOLD;
		CompoundTag res = s().getCompoundOrEmpty("result");
		CompoundTag me = me();
		if (v.deciding()) {
			boolean allow3x = s().getBooleanOr("allow3x", true);
			List<String> legal = v.legal();
			l1 = Component.translatable(legal.contains("bet_4x") ? allow3x ? "gui.burmaldaholic.uth.hint.preflop" : "gui.burmaldaholic.uth.hint.preflop_no3x"
				: legal.contains("bet_2x") ? "gui.burmaldaholic.uth.hint.flop" : "gui.burmaldaholic.uth.hint.river");
			long a = me == null ? 0 : me.getLongOr("ante", 0);
			long t = me == null ? 0 : me.getLongOr("trips", 0);
			MutableComponent l = Component.translatable("gui.burmaldaholic.uth.at_risk_now", Texts.number(2 * a + t));
			if (v.secondsLeft() >= 0) {
				l.append(Texts.raw(" · ")).append(Component.translatable(UthFlow.V2 + "status.decide_in", Texts.number(v.secondsLeft())));
				chrome.status.timer(v.secondsLeft(), Math.max(1, s().getIntOr("decision_ticks", 400) / 20));
			}
			l2 = l;
		} else if (!res.isEmpty()) {
			l1 = resultLine(res);
			c1 = res.getLongOr("net", 0) > 0 ? CasinoPalette.V2_WIN : res.getLongOr("net", 0) < 0 ? CasinoPalette.V2_LOSE : CasinoPalette.V2_MUTED;
			l2 = perBetLine(res);
		} else if (v.banker()) {
			CompoundTag b = bank();
			if (b.contains("last_result")) {
				long r = b.getLongOr("last_result", 0);
				l1 = Component.translatable("gui.burmaldaholic.uth.pvp.round_result", V2Draw.net(r), Texts.number(b.getLongOr("last_rake", 0)));
			} else {
				l1 = Component.translatable("gui.burmaldaholic.uth.pvp.you_bank");
			}
		} else if (v.betting() && v.bank() != null && v.bank().form()) {
			l1 = Component.translatable(UthFlow.V2 + "status.bank_form");
		} else if (v.betting() && !v.seated()) {
			l1 = null; // the gold "Sit down" says it; the balance is in the TopBar
		} else if (v.betting() && v.confirmed() != null) {
			l1 = Component.translatable(UthFlow.V2 + "status.bet_placed");
			if (v.secondsLeft() >= 0) l2 = Component.translatable(UthFlow.V2 + "status.starts_in", Texts.number(v.secondsLeft()));
		} else if (v.betting()) {
			Component spot = Component.translatable(draft.spot() == UthBetDraft.Spot.TRIPS ? "gui.burmaldaholic.uth.trips" : "gui.burmaldaholic.uth.ante");
			l1 = Component.translatable(UthFlow.V2 + "status.chips_to", spot);
			l2 = Component.translatable(UthFlow.V2 + "status.right_click");
		} else if (me != null && me.getIntOr("play", 0) > 0) {
			l1 = Component.translatable("gui.burmaldaholic.uth.waiting_showdown",
				Component.translatable("gui.burmaldaholic.uth.play_multiple", Texts.number(me.getIntOr("play", 0))));
		}
		if (!v.deciding()) {
			if (v.betting() && v.secondsLeft() >= 0) chrome.status.timer(v.secondsLeft(), Math.max(v.secondsLeft(), 15));
			else chrome.status.timer.clear();
		}
		chrome.status.set(l1, l2);
		chrome.status.color(c1);
	}

	private Component resultLine(CompoundTag res) {
		return switch (res.getStringOr("outcome", "")) {
			case "win" -> Component.translatable("gui.burmaldaholic.uth.result.win", handName(s().getStringOr("my_hand", "")));
			case "lose" -> Component.translatable("gui.burmaldaholic.uth.result.lose", handName(s().getStringOr("dealer_hand", "")));
			case "tie" -> Component.translatable("gui.burmaldaholic.uth.result.tie");
			default -> Component.translatable("gui.burmaldaholic.uth.result.folded");
		};
	}

	private Component perBetLine(CompoundTag res) {
		long net = res.getLongOr("net", 0);
		MutableComponent line = Component.empty().append(net > 0 ? Component.translatable("gui.burmaldaholic.common.result.win", Texts.number(net))
			: net < 0 ? Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(-net)) : Component.translatable("gui.burmaldaholic.common.result.push"));
		long blind = res.getLongOr("blind_net", 0);
		if (blind > 0) {
			line.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.line.blind_bonus", handName(handOf(res.getStringOr("pay_hand", ""))),
				ratio(res.getDoubleOr("blind_pay", 0)), Texts.number(blind)));
		}
		long tn = res.getLongOr("trips_net", 0);
		if (res.getLongOr("trips", 0) > 0 && tn > 0) {
			line.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.line.trips_bonus", handName(handOf(res.getStringOr("pay_hand", ""))),
				ratio(res.getIntOr("trips_pay", 0)), Texts.number(tn)));
		}
		return line;
	}

	private static String handOf(String payKey) {
		for (PayHand h : PayHand.values()) if (h.key().equals(payKey)) return h.handName();
		return "";
	}

	private static Component ratio(double pay) {
		String plain = pay == Math.rint(pay) ? Long.toString((long) pay) : String.format(java.util.Locale.ROOT, "%.2f", pay).replaceAll("0+$", "");
		return Component.empty().append(Texts.decimal(plain)).append(Texts.raw(":1"));
	}

	private static Component handName(String name) {
		return name.isEmpty() ? Component.empty() : Component.translatable("gui.burmaldaholic.poker.hand." + name);
	}

	@Override
	public void onGate(String id, Gate gate) {
		UthView v = view;
		if (v == null) return;
		String a = gate.action();
		switch (a) {
			case "bet" -> {
				CompoundTag args = new CompoundTag();
				args.putLong("ante", draft.ante());
				args.putLong("trips", draft.trips());
				sendTracked("bet", args, "ante", draft.ante());
			}
			case UthFlow.LOCAL + "change" -> {
				if (v.confirmed() != null && draft.isEmpty()) draft = draft.load(v.confirmed()[0], v.confirmed()[1]);
				sendTracked("clear", new CompoundTag(), "", 0);
			}
			case UthFlow.LOCAL + "bank_form" -> {
				bankForm = true;
				local();
			}
			case UthFlow.LOCAL + "back" -> {
				bankForm = false;
				local();
			}
			case "take_bank" -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", bankAmount);
				bankForm = false;
				sendTracked("take_bank", args, "", 0);
			}
			case STEPPER -> {
				UthFlow.Stepper s = UthFlow.stepper(v).orElse(null);
				if (s != null) {
					bankAmount = s.value() >= s.max() ? s.min() : s.max();
					local();
				}
			}
			default -> {
				if (!a.isEmpty() && !a.startsWith(UthFlow.LOCAL)) sendTracked(a);
			}
		}
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		UthView v = view;
		if (v == null) return false;
		if (id.equals(STEP_MINUS) || id.equals(STEP_PLUS) || id.equals(KeyMap.Ids.action(STEPPER))) {
			UthFlow.Stepper s = UthFlow.stepper(v).orElse(null);
			if (s == null) return true;
			int dir = button.scroll() != 0 ? button.scroll() : !button.left() ? 0 : id.equals(STEP_PLUS) ? 1 : id.equals(STEP_MINUS) ? -1 : 0;
			if (dir != 0) {
				bankAmount = Math.max(s.min(), Math.min(s.max(), s.value() + dir * s.step()));
				FxSounds.play("chip_count", 0.5f, 1f);
				local();
			}
			return true;
		}
		for (String spot : new String[] {"ante", "trips"}) {
			if (!id.equals(KeyMap.Ids.felt(spot))) continue;
			Gate g = UthFlow.spot(spot, v);
			if (!g.enabled()) {
				chrome.refuse(g);
				return true;
			}
			UthBetDraft.Spot sp = spot.equals("trips") ? UthBetDraft.Spot.TRIPS : UthBetDraft.Spot.ANTE;
			long chip = chrome.bet.armOrDefault();
			if (button.right()) edit(draft.arm(sp).remove(sp, chip));
			else if (button == Press.SCROLL_UP) edit(draft.add(sp, chip));
			else if (button == Press.SCROLL_DOWN) edit(draft.arm(sp).remove(sp, chip));
			else edit(draft.add(sp, chip));
			return true;
		}
		return false;
	}

	@Override
	public void onTopIcon(String id) {
		if (id.equals(KeyMap.Ids.TOP_SETTINGS) && s().getBooleanOr("bots_ui", false)) {
			sendAction("bot_settings");
			return;
		}
		super.onTopIcon(id);
	}

	@Override
	protected void openRules() {
		rulesOpen = !rulesOpen;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (rulesOpen) {
			rulesOpen = false;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (rulesOpen && (event.isEscape() || event.isConfirmation())) {
			rulesOpen = false;
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- layout -----------------------------------------------------------------------------------------------------

	/** Other seats in the viewer's order: dealt-in players, or (betting) the seated players and the atmosphere bots. */
	private List<CompoundTag> others() {
		List<CompoundTag> out = new ArrayList<>();
		List<CompoundTag> ps = players();
		if (!ps.isEmpty()) {
			for (CompoundTag p : ps) if (!p.getBooleanOr("you", false)) out.add(p);
		} else {
			List<String> names = seatNames();
			ListTag bets = s().getListOrEmpty("bets");
			for (int i = 0; i < names.size(); i++) {
				if (names.get(i).isEmpty() || i == mySeat()) continue;
				CompoundTag p = new CompoundTag();
				p.putInt("seat", i);
				p.putString("name", names.get(i));
				for (int b = 0; b < bets.size(); b++) {
					CompoundTag bt = bets.getCompoundOrEmpty(b);
					if (bt.getIntOr("seat", -1) == i) {
						p.putLong("ante", bt.getLongOr("ante", 0));
						p.putLong("trips", bt.getLongOr("trips", 0));
					}
				}
				out.add(p);
			}
		}
		ListTag bots = s().getListOrEmpty("bots");
		for (int i = 0; i < bots.size(); i++) {
			CompoundTag b = bots.getCompoundOrEmpty(i).copy();
			b.putBoolean("bot", true);
			b.putBoolean("idle_bot", true);
			out.add(b);
		}
		int base = Math.max(0, mySeat());
		int n = Math.max(7, s().getIntOr("seat_count", 6) + 1);
		out.sort((a, b) -> Integer.compare(Math.floorMod(a.getIntOr("seat", 0) - base, n), Math.floorMod(b.getIntOr("seat", 0) - base, n)));
		if (out.size() > 5) out = out.subList(0, 5);
		return out;
	}

	private Component dealerLine() {
		MutableComponent c = Component.translatable("gui.burmaldaholic.uth.dealer").copy();
		String hand = s().getStringOr("dealer_hand", "");
		if (!hand.isEmpty() && s().getIntArray("dealer").isPresent() && (fxKind != UthPub.SHOWDOWN || started(UthBeats.QUALIFY))) {
			c.append(Texts.raw(" · ")).append(handName(hand));
		}
		return c;
	}

	@Override
	protected void layoutPlay(TableGrid grid, UthView v, HitMap hits) {
		int labelW = 0;
		for (String k : new String[] {"trips", "ante", "blind", "play"}) labelW = Math.max(labelW, font.width(Component.translatable("gui.burmaldaholic.uth." + k)));
		int handW = font.width(handName(s().getStringOr("my_hand", "")));
		layout = UthTableLayout.of(grid, others().size(), font.width(dealerLine()) + 2 * ValuePill.PAD, Math.max(40, handW + 2 * ValuePill.PAD), labelW + 2,
			font.width("+88 888") + 2 * ValuePill.PAD);
		UthTableLayout.Layout l = layout;
		List<Box> obstacles = new ArrayList<>(List.of(l.board()));
		obstacles.add(l.hero());
		obstacles.add(l.dealer());
		chrome.banner.regions(List.of(l.banner()), obstacles);
		Gate ante = UthFlow.spot("ante", v);
		Gate trips = UthFlow.spot("trips", v);
		Box ta = l.spots()[0];
		Box aa = l.spots()[1];
		hits.add(KeyMap.Ids.felt("trips"), new Box(ta.x(), l.spotLabels()[0].y(), ta.w(), l.spotValues()[0].bottom() - l.spotLabels()[0].y()).inset(-2),
			trips.enabled());
		hits.add(KeyMap.Ids.felt("ante"), new Box(aa.x(), l.spotLabels()[1].y(), aa.w(), l.spotValues()[1].bottom() - l.spotLabels()[1].y()).inset(-2),
			ante.enabled());
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	private static int kit(int card) {
		int r = UthCards.rank(card);
		return UthCards.suit(card) * 13 + (r == 14 ? 0 : r - 1);
	}

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, UthView v, int mouseX, int mouseY, float partial) {
		if (layout == null || layout.grid() != grid) layoutPlay(grid, v, new HitMap());
		UthTableLayout.Layout l = layout;
		List<CompoundTag> others = others();
		drawFelt(g, grid, l, v, mouseX, mouseY);
		drawCards(g, grid.tier, l, others);
		drawSpots(g, grid.tier, l, v, mouseX, mouseY);
		drawSeats(g, grid.tier, l, others);
		drawPills(g, l);
	}

	private void drawFelt(GuiGraphicsExtractor g, TableGrid grid, UthTableLayout.Layout l, UthView v, int mx, int my) {
		// the dealer's rail along the top of the felt
		Box rail = new Box(grid.play.x(), grid.play.y(), grid.play.w(), 3);
		V2Draw.fill(g, rail, V2Felt.rail(theme()));
		g.fill(rail.x(), rail.bottom(), rail.right(), rail.bottom() + 1, CasinoPalette.V2_GOLD_SHADE);
		int print = TableTheme.of(theme()).printArgb(0.45);
		if (players().isEmpty()) for (Box b : l.board()) V2Draw.outline(g, b, print);
		Component print2 = Component.translatable("gui.burmaldaholic.uth.blind_equals_ante");
		if (font.width(print2) <= l.banner().w()) FeltPrint.draw(g, font, l.banner(), print2, 0.35f, List.of());
		if (playerBanked()) {
			CompoundTag b = bank();
			Component who = b.contains("name") ? Texts.raw(b.getStringOr("name", ""))
				: b.contains("stand_in") ? Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(BotRoster.nameKey(b.getStringOr("stand_in", ""))))
				: Component.translatable("gui.burmaldaholic.uth.pvp.the_house");
			MutableComponent line = Component.translatable("gui.burmaldaholic.uth.pvp.dealer_seat", who);
			if (b.contains("bank")) line.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.pvp.bank", Texts.number(b.getLongOr("bank", 0)),
				Texts.number(b.getLongOr("reserved", 0))));
			Box d = l.deck();
			V2Draw.left(g, font, line, new Box(d.right() + 6, d.y(), Math.max(0, grid.felt().right() - d.right() - 6), 11), 0, 1, CasinoPalette.V2_GOLD, true);
		}
	}

	private void drawCards(GuiGraphicsExtractor g, Tier tier, UthTableLayout.Layout l, List<CompoundTag> others) {
		Box deck = l.deck();
		cards.origin(l.dealer().cx() - 10, l.dealer().y()).tray(deck.x(), deck.y());
		cards.begin(nowMs(), FxSettings.reduceMotion());
		int back = TableTheme.of(theme()).back(CardSprites.BACK_EMERALD);
		int key = Math.floorMod(roundKey, 100) * 1000;
		List<CompoundTag> ps = players();
		boolean round = !ps.isEmpty();
		boolean gathered = fxKind == UthPub.RESULT && started(UthBeats.GATHER);
		int hero = V2Cards.size(tier, true);
		int seat = V2Cards.size(tier, false);
		if (round && !gathered) {
			boolean dealing = fxKind == UthPub.DEAL;
			boolean settled = s().getBooleanOr("settled", false);
			int[] dealer = s().getIntArray("dealer").orElse(NO_CARDS);
			int dealerBest = s().getIntOr("dealer_best", 0);
			for (int k = 0; k < 2; k++) {
				int[] at = V2Cards.at(l.dealerCard(k), tier, true);
				int code = k < dealer.length ? kit(dealer[k]) : -1;
				double dealAt = dealing ? beatAt(UthBeats.DEALER_DEAL, k) : Double.NaN;
				double revealAt = fxKind == UthPub.SHOWDOWN ? beatAt(UthBeats.DEALER_FLIP, k) : Double.NaN;
				CardAnimator.Slot sl = cards.card(key + 10 + k, code, hero, back, at[0], at[1], 0, dealAt, revealAt);
				if (code >= 0 && settled) cards.dim(sl, (dealerBest & (1 << k)) == 0);
			}
			int[] board = s().getIntArray("board").orElse(NO_CARDS);
			int myBest = s().getIntOr("my_best", 0);
			for (int i = 0; i < 5; i++) {
				int[] at = V2Cards.at(l.board()[i], tier, true);
				int code = i < board.length ? kit(board[i]) : -1;
				double dealAt = dealing ? beatAt(UthBeats.BOARD, i) : Double.NaN;
				double revealAt = fxKind == UthPub.FLOP || fxKind == UthPub.RIVER ? beatAt(UthBeats.FLIP, i) : Double.NaN;
				CardAnimator.Slot sl = cards.card(key + 20 + i, code, hero, back, at[0], at[1], 0, dealAt, revealAt);
				if (settled && myBest != 0) {
					boolean in = (myBest & (1 << (2 + i))) != 0;
					cards.lift(sl, in);
					cards.glow(sl, in);
				}
			}
			CompoundTag me = me();
			int lane = myLane();
			if (me != null && !"folded".equals(me.getStringOr("tag", ""))) {
				int[] mine = me.getIntArray("cards").orElse(NO_CARDS);
				for (int k = 0; k < 2; k++) {
					int[] at = V2Cards.at(l.heroCard(k), tier, true);
					int code = k < mine.length ? kit(mine[k]) : -1;
					double dealAt = dealing ? beatAt(UthBeats.DEAL, lane, k) : Double.NaN;
					double revealAt = dealing ? beatAt(UthBeats.LIFT, lane) : Double.NaN;
					CardAnimator.Slot sl = cards.card(key + 100 + k, code, hero, back, at[0], at[1], 0, dealAt, revealAt);
					if (settled && myBest != 0) {
						boolean in = (myBest & (1 << k)) != 0;
						cards.lift(sl, in);
						cards.glow(sl, in);
					}
				}
			}
			for (int i = 0; i < others.size(); i++) {
				CompoundTag p = others.get(i);
				if (p.getBooleanOr("idle_bot", false) || "folded".equals(p.getStringOr("tag", ""))) continue;
				int pl = laneOf(ps, p);
				int[] theirs = p.getIntArray("cards").orElse(NO_CARDS);
				UthTableLayout.Other o = l.others()[i];
				for (int k = 0; k < 2; k++) {
					int[] at = V2Cards.at(o.card(k, tier), tier, false);
					int code = k < theirs.length ? kit(theirs[k]) : -1;
					double dealAt = dealing ? beatAt(UthBeats.DEAL, pl, k) : Double.NaN;
					double revealAt = fxKind == UthPub.SHOWDOWN ? beatAt(UthBeats.REVEAL, -1) : Double.NaN;
					CardAnimator.Slot sl = cards.card(key + 200 + p.getIntOr("seat", i) * 10 + k, code, seat, back, at[0], at[1], 0, dealAt, revealAt);
					if (settled && p.getLongOr("net", 0) < 0) cards.dim(sl, true);
				}
			}
		}
		cards.end();
		cards.draw(g, font);
	}

	private static int laneOf(List<CompoundTag> ps, CompoundTag p) {
		for (int i = 0; i < ps.size(); i++) if (ps.get(i).getIntOr("seat", -1) == p.getIntOr("seat", -2)) return i;
		return 0;
	}

	/** The four circles: label, ring (the armed spot gold), stack (the draft at 60 % until dealt), value or net pill. */
	private void drawSpots(GuiGraphicsExtractor g, Tier tier, UthTableLayout.Layout l, UthView v, int mx, int my) {
		CompoundTag me = me();
		long a;
		long t;
		int play;
		boolean pending = false;
		if (me != null) {
			a = me.getLongOr("ante", 0);
			t = me.getLongOr("trips", 0);
			play = me.getIntOr("play", 0);
		} else if (v.seated() && v.betting() && !v.banker()) {
			a = draft.ante();
			t = draft.trips();
			play = 0;
			pending = v.confirmed() == null;
		} else {
			a = 0;
			t = 0;
			play = 0;
		}
		long[] amounts = {t, a, a, (long) play * a};
		if (me != null && "folded".equals(me.getStringOr("tag", ""))) {
			amounts[1] = 0;
			amounts[2] = 0;
		}
		long[] nets = me == null || !s().getBooleanOr("settled", false) ? null : me.getLongArray("circles").orElse(null);
		boolean gathered = fxKind == UthPub.RESULT && started(UthBeats.GATHER);
		String ghost = previewBet;
		if (ghost == null) {
			Optional<HitMap.Target> h = chrome.hits().at(mx, my);
			if (h.isPresent() && h.get().id().startsWith("action.bet_") && h.get().enabled()) ghost = h.get().id().substring("action.".length());
			if (h.isPresent() && h.get().id().equals(KeyMap.Ids.PRIMARY) && chrome.primary.gate().action().startsWith("bet_")) ghost = chrome.primary.gate().action();
		}
		int print = TableTheme.of(theme()).printArgb(0.75);
		boolean editable = UthFlow.spot("ante", v).enabled();
		for (int c = 0; c < 4; c++) {
			if (c == 0 && !v.tripsOn()) continue;
			Box ring = l.spots()[c];
			boolean armed = editable && c <= 1 && (c == 0) == (draft.spot() == UthBetDraft.Spot.TRIPS);
			boolean hover = editable && c <= 1 && l.spotLabels()[c].x() <= mx && mx < l.spotLabels()[c].right() && l.spotLabels()[c].y() <= my
				&& my < l.spotValues()[c].bottom();
			FeltPrint.ring(g, ring, armed ? CasinoPalette.V2_GOLD : hover ? V2Draw.alpha(CasinoPalette.V2_GOLD, 0.7f) : print);
			if (armed) FeltPrint.ring(g, ring.inset(-2), V2Draw.alpha(CasinoPalette.V2_GOLD, 0.5f));
			Component label = Component.translatable("gui.burmaldaholic.uth." + UthTableLayout.SPOTS[c]);
			V2Draw.centred(g, font, label, l.spotLabels()[c], 1, armed ? CasinoPalette.V2_GOLD : print, false);
			long amount = gathered ? 0 : amounts[c];
			Box stack = Box.centredAt(ring.cx(), ring.cy(), tier.feltChip, tier.feltChip + 4);
			if (c == 3 && amount == 0 && ghost != null && me != null) {
				long ga = switch (ghost) {
					case "bet_4x" -> 4 * a;
					case "bet_3x" -> 3 * a;
					case "bet_2x" -> 2 * a;
					default -> a;
				};
				ChipStack.draw(g, stack, ga, 0.45f);
				Component plus = Component.empty().append(Texts.raw("+")).append(Texts.number(ga));
				ValuePill.draw(g, font, l.spotValues()[c].centred(Math.min(l.spotValues()[c].w(), ValuePill.width(font, plus)), l.spotValues()[c].h()), plus,
					ValuePill.Style.YOU);
				continue;
			}
			if (amount <= 0 && (nets == null || nets[c] == 0)) continue;
			if (amount > 0) ChipStack.draw(g, stack, amount, pending ? 0.6f : 1f);
			Component text;
			ValuePill.Style style;
			if (nets != null && c < nets.length && (amount > 0 || nets[c] != 0)) {
				text = V2Draw.net(nets[c]);
				style = ValuePill.ofNet(nets[c]);
			} else {
				text = Texts.number(amount);
				style = ValuePill.Style.YOU;
			}
			Box vb = l.spotValues()[c];
			ValuePill.draw(g, font, vb.centred(Math.min(vb.w(), ValuePill.width(font, text)), vb.h()), text, style);
		}
	}

	private void drawSeats(GuiGraphicsExtractor g, Tier tier, UthTableLayout.Layout l, List<CompoundTag> others) {
		boolean settled = s().getBooleanOr("settled", false);
		boolean showdown = s().getIntArray("dealer").isPresent() && (fxKind != UthPub.SHOWDOWN || started(UthBeats.REVEAL));
		for (int i = 0; i < others.size(); i++) {
			CompoundTag p = others.get(i);
			UthTableLayout.Other o = l.others()[i];
			boolean bot = p.getBooleanOr("bot", false);
			Component name = bot ? Component.translatable(BotRoster.nameKey(p.getStringOr("bot_name", ""))) : Texts.raw(p.getStringOr("name", ""));
			String tag = p.getStringOr("tag", "");
			SeatPlate.draw(g, font, o.plate(), name, "deciding".equals(tag) || "thinking".equals(p.getStringOr("bot_state", "")) ? SeatPlate.Kind.ACTING
				: SeatPlate.Kind.OTHER, bot);
			Component sub;
			ValuePill.Style style = ValuePill.Style.OTHER;
			if (settled && p.contains("net")) {
				long net = p.getLongOr("net", 0);
				sub = V2Draw.net(net);
				style = ValuePill.ofNet(net);
			} else if (showdown && !p.getStringOr("hand", "").isEmpty() && !"folded".equals(tag)) {
				sub = handName(p.getStringOr("hand", ""));
			} else if ("play".equals(tag)) {
				sub = Component.translatable("gui.burmaldaholic.uth.tag.play", Texts.number(p.getIntOr("play", 0)));
			} else if (!tag.isEmpty()) {
				sub = Component.translatable("gui.burmaldaholic.uth.tag." + tag);
				style = ValuePill.Style.PUSH;
			} else if ("watching".equals(p.getStringOr("bot_state", ""))) {
				sub = Component.translatable("gui.burmaldaholic.bots.watching");
				style = ValuePill.Style.PUSH;
			} else if (p.getLongOr("ante", 0) > 0) {
				sub = Component.translatable("gui.burmaldaholic.uth.ante_amount", Texts.number(p.getLongOr("ante", 0)));
			} else {
				continue;
			}
			Box pb = o.pill();
			int w = Math.min(o.plate().w(), ValuePill.width(font, sub));
			Box pill = o.plate().x() == pb.x() ? new Box(pb.x(), pb.y(), w, pb.h()) : new Box(o.plate().right() - w, pb.y(), w, pb.h());
			ValuePill.draw(g, font, pill, sub, style);
		}
		// the viewer's plate
		if (mySeat() >= 0) SeatPlate.draw(g, font, l.heroPlate(), Component.translatable("gui.burmaldaholic.common.you"), SeatPlate.Kind.YOU, false);
	}

	private void drawPills(GuiGraphicsExtractor g, UthTableLayout.Layout l) {
		if (!players().isEmpty()) {
			Component d = dealerLine();
			if (s().getIntArray("dealer").isPresent() && s().getBooleanOr("settled", false) && !s().getBooleanOr("qualifies", true)) {
				d = Component.translatable(UthFlow.V2 + "dealer_no_open");
			}
			Box dp = l.dealerPill();
			int w = Math.min(dp.w(), ValuePill.width(font, d));
			ValuePill.draw(g, font, new Box(dp.right() - w, dp.y(), w, dp.h()), d, ValuePill.Style.OTHER);
		}
		String myHand = s().getStringOr("my_hand", "");
		if (!myHand.isEmpty() && me() != null) {
			Component t = handName(myHand);
			Box hb = l.heroHand();
			int w = Math.min(hb.w(), ValuePill.width(font, t));
			ValuePill.draw(g, font, new Box(hb.x() + (hb.w() - w) / 2, hb.y(), w, hb.h()), t, ValuePill.Style.YOU);
		}
	}

	/** The {@code ?} overlay: Blind and Trips paytables, bot edges, the player-bank rules. */
	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, UthView v, int mouseX, int mouseY, float partial) {
		if (!rulesOpen) return;
		Box play = grid.play;
		V2Draw.fill(g, play, 0xB0080410);
		Box panel = play.inset(grid.m() + 6);
		V2Draw.pill(g, panel, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD_SHADE);
		CompoundTag pays = s().getCompoundOrEmpty("pays");
		int colW = (panel.w() - 18) / 2;
		int x1 = panel.x() + 6;
		int x2 = x1 + colW + 6;
		int bottom = panel.bottom() - 4;
		int y1 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.blind"), x1, panel.y() + 5, colW, CasinoPalette.V2_GOLD, bottom);
		int y2 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.trips"), x2, panel.y() + 5, colW, CasinoPalette.V2_GOLD, bottom);
		for (PayHand ph : PayHand.values()) {
			if (ph == PayHand.NONE) continue;
			double bp = pays.getDoubleOr("blind_" + ph.key(), 0);
			if (bp > 0) y1 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.row", handName(ph.handName()), ratio(bp)), x1, y1, colW,
				CasinoPalette.V2_TEXT, bottom);
			int tp = pays.getIntOr("trips_" + ph.key(), 0);
			if (tp > 0) y2 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.row", handName(ph.handName()), ratio(tp)), x2, y2, colW,
				CasinoPalette.V2_TEXT, bottom);
		}
		y1 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.blind_lower"), x1, y1, colW, CasinoPalette.V2_MUTED, bottom);
		ListTag levels = s().getListOrEmpty("bot_levels");
		for (int i = 0; i < levels.size(); i++) {
			BotDifficulty lvl = BotDifficulty.byId(levels.getStringOr(i, ""), BotDifficulty.NORMAL);
			Component line = Component.empty().append(Component.translatable(lvl.translationKey())).append(Texts.raw(": "))
				.append(Component.translatable("gui.burmaldaholic.bots.uth_edge", Texts.raw(UthBotPolicy.edgePercent(lvl))));
			y1 = wrapped(g, line, x1, y1 + (i == 0 ? 2 : 0), colW, CasinoPalette.V2_MUTED, bottom);
		}
		if (playerBanked()) {
			CompoundTag b = bank();
			String pct = UthClientModule.trimPercent(b.getDoubleOr("rake", 0) * 100);
			wrapped(g, Component.translatable("gui.burmaldaholic.uth.pvp.rules.2", Texts.number(b.getLongOr("ante_factor", 505)),
				Texts.number(b.getLongOr("trips_factor", 50)), Texts.decimal(pct)), x2, y2 + 2, colW, CasinoPalette.V2_MUTED, bottom);
		}
	}

	private int wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color, int bottom) {
		for (FormattedCharSequence l : font.split(text, w)) {
			if (y + 9 > bottom) return y;
			V2Draw.text(g, font, l, x, y, 1, color, true);
			y += 10;
		}
		return y;
	}
}
