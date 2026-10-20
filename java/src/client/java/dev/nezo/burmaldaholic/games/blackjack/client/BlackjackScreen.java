package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.ClientFx;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.cards.CardSprites;
import dev.nezo.burmaldaholic.client.table.cards.CardsV2;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.FeltPrint;
import dev.nezo.burmaldaholic.client.ui.v2.SeatPlate;
import dev.nezo.burmaldaholic.client.ui.v2.Stamp;
import dev.nezo.burmaldaholic.client.ui.v2.TotalPill;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Felt;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.Stamps;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Hands;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackFlow;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackLayout;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Blackjack, table UI v2 (REDESIGN.md §7.1, docs/architecture/ui-v2.md): the felt fills the screen, the dealer's fan
 * at the top, the crescent of seats with the viewer always in the middle; the kit's TopBar and Dock (BetBar with a
 * local bet draft, the ActionRow Hit · Double · Split · Surrender with Stand as the primary, the insurance / even-money
 * offer, Rebet + Leave on the result). Every button's state comes from the pure {@link BlackjackFlow}; every box from
 * the pure {@link BlackjackLayout}.
 *
 * <p>The server takes the whole bet at once ({@code bet amount}), so chips build a local draft that the primary
 * ({@code Deal}) sends under the kit's acknowledgement watchdog; a primary {@code Rebet n} sends the last bet in one
 * press (on the result it is queued for the next betting phase).
 */
public class BlackjackScreen extends V2TableScreen<BlackjackView> {
	/** One hand of the published state. */
	record Hand(int[] codes, int[] at, long bet, boolean doubled, boolean split, boolean done, String outcome, long ret) {
		List<Card> cards() {
			List<Card> out = new ArrayList<>();
			for (int c : codes) if (c >= 0 && c < 52) out.add(Card.fromCode(c));
			return out;
		}
	}

	/** One seat of the round (or of the betting lobby). */
	record Seat(int seat, String name, @Nullable Component nameC, boolean bot, boolean you, long bet, boolean settled, long net, long ret,
			long staked, String tier, List<Hand> hands) {
		Component display() {
			return nameC != null ? nameC : Texts.raw(name);
		}
	}

	/** The parsed state. */
	static final class Model {
		String phase = "betting";
		boolean busy;
		int roundSeq;
		long roundTick;
		boolean inRound;
		int[] dealer = new int[0];
		int[] dealerAt = new int[0];
		int currentSeat = -1;
		int currentHand = -1;
		final List<Seat> seats = new ArrayList<>();
		String offer = "";
		long insuranceMax;
		final Set<String> legal = new HashSet<>();
		boolean betPlaced;
		long placedAmount;
		long lastBet;
		String notice = "";
		int seatCount = 5;
		int mySeat = -1;
		int occupied;
	}

	private Model m = new Model();
	private long draft;
	private final Deque<Long> history = new ArrayDeque<>();
	/** Rebet pressed on the result: sent when the next betting phase opens. */
	private boolean queuedRebet;
	private @Nullable BlackjackLayout layout;
	private final Map<Long, Long> flipSeen = new HashMap<>();
	private int bannerRound = -1, celebratedRound = -1;
	private long settledAt = -1;
	private final Map<Integer, Long> stampSeen = new HashMap<>();

	public BlackjackScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType().name().endsWith("high_roller")
			? "gui.burmaldaholic.blackjack.title_high_roller" : "gui.burmaldaholic.blackjack.title"));
		chrome.keys(KeyMap.standard().bind(KeyMap.H, KeyMap.Ids.action("hit")).bind(KeyMap.D, KeyMap.Ids.action("double"))
			.bind(KeyMap.P, KeyMap.Ids.action("split")).bind(KeyMap.U, KeyMap.Ids.action("surrender")).bind(KeyMap.N, KeyMap.Ids.action("no_insurance"))
			.bind(KeyMap.L, KeyMap.Ids.action("leave")));
	}

	// ---- state -------------------------------------------------------------------------------------------------------

	private static Component decode(@Nullable Tag tag) {
		if (tag == null) return Component.empty();
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	static Model parse(CompoundTag s) {
		Model n = new Model();
		n.phase = s.getStringOr("phase", "betting");
		n.busy = s.getBooleanOr("busy", false);
		n.roundSeq = s.getIntOr("round_seq", 0);
		n.roundTick = s.getLongOr("round_tick", 0);
		n.inRound = !s.getListOrEmpty("players").isEmpty();
		n.dealer = s.getIntArray("dealer").orElse(new int[0]);
		n.dealerAt = s.getIntArray("dealer_at").orElse(new int[n.dealer.length]);
		n.currentSeat = s.getIntOr("current_seat", -1);
		n.currentHand = s.getIntOr("current_hand", -1);
		n.offer = s.getStringOr("offer", "");
		n.insuranceMax = s.getLongOr("insurance_max", 0);
		ListTag legal = s.getListOrEmpty("legal");
		for (int i = 0; i < legal.size(); i++) n.legal.add(legal.getStringOr(i, ""));
		n.betPlaced = s.getBooleanOr("bet_placed", false);
		n.lastBet = s.getLongOr("last_bet", 0);
		n.notice = s.getStringOr("notice", "");
		n.seatCount = Math.max(1, s.getIntOr("seat_count", 5));
		n.mySeat = s.getIntOr("seat", -1);
		n.occupied = s.getListOrEmpty("seats").size();
		ListTag players = s.getListOrEmpty("players");
		for (int i = 0; i < players.size(); i++) {
			CompoundTag p = players.getCompoundOrEmpty(i);
			List<Hand> hands = new ArrayList<>();
			ListTag hs = p.getListOrEmpty("hands");
			for (int h = 0; h < hs.size(); h++) {
				CompoundTag ht = hs.getCompoundOrEmpty(h);
				int[] codes = ht.getIntArray("cards").orElse(new int[0]);
				hands.add(new Hand(codes, ht.getIntArray("cards_at").orElse(new int[codes.length]), ht.getLongOr("bet", 0), ht.getBooleanOr("doubled", false),
					ht.getBooleanOr("split", false), ht.getBooleanOr("done", false), ht.getStringOr("outcome", ""), ht.getLongOr("ret", 0)));
			}
			boolean bot = p.getBooleanOr("bot", false);
			n.seats.add(new Seat(p.getIntOr("seat", -1), p.getStringOr("name", ""), bot ? decode(p.get("name_c")) : null, bot, p.getBooleanOr("you", false),
				p.getLongOr("bet", 0), p.getBooleanOr("settled", false), p.getLongOr("net", 0), p.getLongOr("ret", 0), p.getLongOr("staked", 0),
				p.getStringOr("tier", ""), hands));
		}
		Map<Integer, Long> bets = new HashMap<>();
		ListTag bl = s.getListOrEmpty("bets");
		for (int i = 0; i < bl.size(); i++) bets.put(bl.getCompoundOrEmpty(i).getIntOr("seat", -1), bl.getCompoundOrEmpty(i).getLongOr("amount", 0));
		n.placedAmount = n.mySeat >= 0 ? bets.getOrDefault(n.mySeat, 0L) : 0;
		if (!n.inRound) {
			// the betting lobby: seated humans with their bets, bots with their virtual bets
			ListTag seats = s.getListOrEmpty("seats");
			for (int i = 0; i < seats.size(); i++) {
				CompoundTag st = seats.getCompoundOrEmpty(i);
				int idx = st.getIntOr("index", -1);
				n.seats.add(new Seat(idx, st.getStringOr("name", ""), null, false, st.getBooleanOr("you", false), bets.getOrDefault(idx, 0L), false, 0, 0, 0, "",
					List.of()));
			}
			ListTag bots = s.getListOrEmpty("bot_seats");
			for (int i = 0; i < bots.size(); i++) {
				CompoundTag bt = bots.getCompoundOrEmpty(i);
				int idx = bt.getIntOr("seat", -1);
				if (n.seats.stream().anyMatch(x -> x.seat() == idx)) continue;
				n.seats.add(new Seat(idx, "", decode(bt.get("name_c")), true, false, bt.getLongOr("amount", 0), false, 0, 0, 0, "", List.of()));
			}
		}
		if (n.betPlaced && n.placedAmount <= 0) n.placedAmount = Math.max(1, n.lastBet);
		return n;
	}

	private @Nullable Seat me() {
		for (Seat s : m.seats) if (s.you()) return s;
		return null;
	}

	private @Nullable Seat seat(int index) {
		for (Seat s : m.seats) if (s.seat() == index) return s;
		return null;
	}

	@Override
	protected BlackjackView view(CompoundTag state) {
		Model old = m;
		m = parse(state);
		if (old.roundSeq != m.roundSeq) {
			flipSeen.clear();
			stampSeen.clear();
		}
		BlackjackView.Phase phase = BlackjackView.phase(m.phase, m.inRound);
		if (phase != BlackjackView.Phase.BETTING || m.betPlaced) {
			draft = 0;
			history.clear();
		}
		if (phase == BlackjackView.Phase.BETTING && !m.betPlaced && queuedRebet && m.mySeat >= 0) {
			queuedRebet = false;
			if (m.lastBet > 0 && m.lastBet <= balance()) {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", m.lastBet);
				sendTracked("bet", args, "spot", m.lastBet);
			}
		}
		Seat me = me();
		if (me != null && me.settled() && !m.busy) {
			if (settledAt < 0) settledAt = Util.getMillis();
		} else {
			settledAt = -1;
		}
		return build();
	}

	private BlackjackView build() {
		BlackjackView.Phase phase = BlackjackView.phase(m.phase, m.inRound);
		Seat me = me();
		boolean myTurn = me != null && "turns".equals(m.phase) && !m.busy && me.seat() == m.currentSeat;
		String waiting = "";
		if (m.currentSeat >= 0 && !myTurn && !m.busy) {
			Seat cur = seat(m.currentSeat);
			if (cur != null) waiting = cur.display().getString();
		}
		int secs = -1;
		for (String t : new String[] {"bet", "insurance", "turn", "result"}) {
			long s = timerSeconds(t);
			if (s >= 0) {
				secs = (int) s;
				break;
			}
		}
		int hands = me == null ? 0 : me.hands().size();
		int hand = myTurn ? m.currentHand + 1 : 0;
		return new BlackjackView(mySeat() >= 0, m.occupied < m.seatCount, phase, m.busy, minBet(), maxBet(), balance(), draft,
			m.betPlaced ? m.placedAmount : 0, m.lastBet, !history.isEmpty(), me != null && hands > 0, myTurn, m.legal, m.offer, m.insuranceMax, waiting, secs,
			hand, hands, chrome.pending() != null);
	}

	@Override
	protected Flow<BlackjackView> flow() {
		return BlackjackFlow.INSTANCE;
	}

	@Override
	protected void refresh() {
		if (font == null) return;
		if (view != null) view = build();
		super.refresh();
		if (view == null) return;
		BlackjackFlow.Line[] lines = BlackjackFlow.status(view);
		Component l1 = line(lines[0]);
		Component l2 = line(lines[1]);
		Seat me = me();
		if (me != null && me.settled() && !m.busy) {
			l1 = resultLine(me);
			chrome.status.color(me.net() > 0 ? CasinoPalette.V2_WIN : me.net() < 0 ? CasinoPalette.V2_LOSE : CasinoPalette.V2_PUSH);
		} else {
			chrome.status.color(CasinoPalette.V2_GOLD);
		}
		boolean mine = view.myTurn() || me != null && me.settled() && !m.busy;
		if (!m.notice.isEmpty() && !mine && view.phase() != BlackjackView.Phase.BETTING) l2 = Component.translatable(m.notice);
		chrome.status.set(l1, l2);
	}

	private static @Nullable Component line(BlackjackFlow.Line l) {
		return l.key().isEmpty() ? null : V2Draw.tr(l.key(), l.args());
	}

	private Component resultLine(Seat me) {
		String best = BlackjackBoardRenderer.word(outcomes(me));
		long net = me.net();
		return switch (best) {
			case "blackjack" -> Component.translatable("gui.burmaldaholic.blackjack.result.blackjack", Texts.number(Math.max(0, net)));
			case "win" -> Component.translatable("gui.burmaldaholic.blackjack.result.win", Texts.number(Math.max(0, net)));
			case "push" -> Component.translatable("gui.burmaldaholic.blackjack.result.push");
			case "bust" -> Component.translatable("gui.burmaldaholic.blackjack.result.bust");
			case "surrender" -> Component.translatable("gui.burmaldaholic.blackjack.result.surrender");
			case "even_money" -> Component.translatable("gui.burmaldaholic.blackjack.result.even_money", Texts.number(Math.max(0, net)));
			default -> Component.translatable("gui.burmaldaholic.blackjack.result.lose");
		};
	}

	/** The seat as the public-board type (one stamp word rule for screen and world). */
	private static dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.SeatPub outcomes(Seat s) {
		List<dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.HandPub> hs = new ArrayList<>();
		for (Hand h : s.hands()) {
			hs.add(new dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.HandPub(h.bet(), 0,
				dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.outcomeCode(h.outcome()), h.ret(), new int[0], new int[0]));
		}
		return new dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.SeatPub(s.seat(), 0, s.bet(), s.net(), hs);
	}

	static Stamps.Outcome stampOf(String word) {
		return switch (word) {
			case "blackjack" -> Stamps.Outcome.BLACKJACK;
			case "win", "even_money" -> Stamps.Outcome.WIN;
			case "push" -> Stamps.Outcome.PUSH;
			case "bust" -> Stamps.Outcome.BUST;
			case "surrender" -> Stamps.Outcome.SURRENDER;
			default -> Stamps.Outcome.LOSE;
		};
	}

	// ---- actions -----------------------------------------------------------------------------------------------------

	@Override
	protected @Nullable BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				if (view == null || !BlackjackFlow.INSTANCE.chip(denom, view).enabled()) return;
				draft += denom;
				history.push(denom);
				FxSounds.play("chip_place", 1f);
				refresh();
			}

			@Override
			public boolean setsAmount() {
				return true;
			}

			@Override
			public boolean amount(long amount) { // the bet-entry control's OK (lane HR)
				if (view == null || amount <= 0) return false;
				draft = amount;
				history.clear();
				history.push(amount);
				FxSounds.play("chip_place", 1f);
				refresh();
				return true;
			}

			@Override
			public void remove(long denom) {
				if (history.isEmpty()) return;
				// remove the last chip of that value (or the last chip)
				Long hit = null;
				for (Long d : history) if (d == denom) {
					hit = d;
					break;
				}
				if (hit != null) history.removeFirstOccurrence(hit);
				else hit = history.pop();
				draft = Math.max(0, draft - hit);
				refresh();
			}

			@Override
			public void tool(String toolId) {
				switch (toolId) {
					case BetGates.UNDO -> {
						if (!history.isEmpty()) draft = Math.max(0, draft - history.pop());
					}
					case BetGates.CLEAR -> {
						draft = 0;
						history.clear();
					}
					case BetGates.REBET -> {
						draft = m.lastBet;
						history.clear();
						history.push(m.lastBet);
					}
					case BetGates.DOUBLE -> {
						history.push(draft);
						draft *= 2;
					}
					default -> {}
				}
				FxSounds.play("chip_place", 0.8f);
				refresh();
			}
		};
	}

	@Override
	public void onGate(String id, Gate gate) {
		switch (gate.action()) {
			case "bet" -> sendBet(draft);
			case "rebet" -> {
				if (view != null && view.phase() == BlackjackView.Phase.BETTING) sendBet(m.lastBet);
				else {
					queuedRebet = true;
					chrome.status.flash(Component.translatable("gui.burmaldaholic.blackjack.next_round", Texts.number(m.lastBet)));
				}
			}
			case "insurance" -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", m.insuranceMax);
				sendTracked("insurance", args, "", 0);
			}
			case "no_insurance" -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", 0);
				sendTracked("insurance", args, "", 0);
			}
			case "even_money", "no_even_money" -> {
				CompoundTag args = new CompoundTag();
				args.putBoolean("take", gate.action().equals("even_money"));
				sendTracked("even_money", args, "", 0);
			}
			case "leave" -> {
				sendAction("leave");
				onClose();
			}
			default -> sendTracked(gate.action());
		}
	}

	private void sendBet(long amount) {
		if (amount <= 0) return;
		CompoundTag args = new CompoundTag();
		args.putLong("amount", amount);
		sendTracked("bet", args, "spot", amount);
		FxSounds.play("chip_place", 1f);
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		if (id.startsWith("seat.")) {
			if (enabled) sendTracked(BetGates.SIT);
			return true;
		}
		if (id.equals(KeyMap.Ids.felt("spot")) && view != null) {
			if (button.right()) {
				BetPort p = betPort();
				if (p != null) p.tool(BetGates.UNDO);
				return true;
			}
			long d = chrome.bet.armOrDefault();
			Gate g = BlackjackFlow.INSTANCE.chip(Math.max(1, d), view);
			if (d <= 0 || !g.enabled()) {
				chrome.refuse(g);
				return true;
			}
			BetPort p = betPort();
			if (p != null) p.add(d);
			return true;
		}
		return false;
	}

	@Override
	protected void openRules() {
		chrome.status.flash(Component.translatable("gui.burmaldaholic.blackjack.rules.1"));
	}

	// ---- layout ------------------------------------------------------------------------------------------------------

	private boolean hero(int serverSeat) {
		return serverSeat == m.mySeat && m.mySeat >= 0;
	}

	@Override
	protected void layoutPlay(TableGrid grid, BlackjackView v, HitMap hits) {
		layout = computeLayout(grid);
		BlackjackLayout l = layout;
		int slots = l.columns().length;
		// empty seats: sit there (the server seats the player in the first free seat)
		if (!v.seated()) {
			for (int s = 0; s < m.seatCount; s++) {
				if (seat(s) != null) continue;
				int slot = l.slotOfSeat()[s];
				if (slot >= 0 && slot < slots) hits.add(KeyMap.Ids.seat(s), l.spots()[slot], v.canSit());
			}
		} else if (l.hero() >= 0 && v.bettingOpen()) {
			hits.add(KeyMap.Ids.felt("spot"), heroSpot(l), true);
		}
		List<Box> obstacles = new ArrayList<>();
		obstacles.add(l.dealer());
		for (SeatColumn c : l.columns()) if (c != null) obstacles.addAll(List.of(c.boxes()));
		chrome.banner.regions(l.banner().isEmpty() ? List.of() : List.of(l.banner()), obstacles);
	}

	private Box heroSpot(BlackjackLayout l) {
		SeatColumn c = l.columns()[l.hero()];
		Box cards = c.cards();
		int r = Math.min(cards.w() + 10, cards.h()) ;
		return Box.centredAt(cards.cx(), cards.cy() + 2, r, r);
	}

	private BlackjackLayout computeLayout(TableGrid grid) {
		Tier t = grid.tier;
		BlackjackLayout.SeatIn[] seats = new BlackjackLayout.SeatIn[m.seatCount];
		for (Seat s : m.seats) {
			if (s.seat() < 0 || s.seat() >= m.seatCount) continue;
			boolean hero = hero(s.seat());
			int[] cards = new int[s.hands().size()];
			for (int h = 0; h < cards.length; h++) cards[h] = s.hands().get(h).codes().length;
			int[] outW;
			if (s.settled() && !m.busy) {
				int ww = font.width(Stamp.word(stampOf(BlackjackBoardRenderer.word(outcomes(s)))));
				outW = Stamps.levels(ww, hero);
			} else {
				Component tt = totalOf(s);
				outW = tt == null ? new int[0] : hero ? new int[] {TotalPill.width(font, tt, 2), TotalPill.width(font, tt, 1)}
					: new int[] {TotalPill.width(font, tt, 1)};
			}
			int valueW = ValuePill.width(font, valueText(s));
			seats[s.seat()] = new BlackjackLayout.SeatIn(cards, outW, valueW);
		}
		if (m.mySeat >= 0 && m.mySeat < m.seatCount && seats[m.mySeat] == null) {
			seats[m.mySeat] = new BlackjackLayout.SeatIn(new int[0], new int[0], ValuePill.width(font, Texts.number(Math.max(draft, 0))));
		}
		Component dt = dealerTotal();
		int dtw = dt == null ? 0 : TotalPill.width(font, dt, t == Tier.T1 ? 1 : 2);
		int noteW = Math.max(font.width(Component.translatable("gui.burmaldaholic.blackjack.v2.note.dealer")),
			font.width(Component.translatable("gui.burmaldaholic.blackjack.v2.note.s17")));
		int[] printW = {font.width(Component.translatable("gui.burmaldaholic.blackjack.print.pays")),
			font.width(Component.translatable("gui.burmaldaholic.blackjack.print.insurance"))};
		return BlackjackLayout.of(grid, seats, m.mySeat >= 0 && m.mySeat < m.seatCount ? m.mySeat : -1, m.dealer.length, dtw, noteW, printW);
	}

	private @Nullable Component dealerTotal() {
		if (m.dealer.length == 0) return null;
		return totalText(m.dealer);
	}

	private static Component totalText(int[] codes) {
		List<Card> cards = new ArrayList<>();
		for (int c : codes) if (c >= 0 && c < 52) cards.add(Card.fromCode(c));
		if (cards.isEmpty()) return Texts.number(0);
		int[] t = Hands.displayTotals(cards);
		return t.length == 2 ? Component.translatable("gui.burmaldaholic.blackjack.v2.total_soft", t[0], t[1]) : Texts.number(t[0]);
	}

	/** The total pill of a seat: its acting hand (or first hand); null without cards. */
	private @Nullable Component totalOf(Seat s) {
		if (s.hands().isEmpty()) return null;
		int h = s.seat() == m.currentSeat && m.currentHand >= 0 && m.currentHand < s.hands().size() ? m.currentHand : 0;
		Hand hand = s.hands().get(h);
		if (hand.codes().length == 0) return null;
		return totalText(hand.codes());
	}

	private Component valueText(Seat s) {
		if (s.settled() && !m.busy) return V2Draw.net(s.net());
		long bet = s.hands().isEmpty() ? s.bet() : s.hands().stream().mapToLong(Hand::bet).sum();
		if (hero(s.seat()) && bet <= 0) bet = draft;
		return Texts.number(Math.max(0, bet));
	}

	// ---- drawing -----------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, BlackjackView v, int mx, int my, float partial) {
		BlackjackLayout l = layout;
		if (l == null) return;
		Tier t = grid.tier;
		var theme = theme();
		int back = CardSprites.BACK_CRIMSON;
		double nowMs = Minecraft.getInstance().level == null ? 0 : (Minecraft.getInstance().level.getGameTime() + partial) * 50.0;
		// the crescent rail (decor, under the seats), the dealer rail, rack, shoe, discard
		dev.nezo.burmaldaholic.client.table.cards.RailArc.draw(g, grid.play, t, grid.play.bottom() - (t == Tier.T1 ? 4 : 7),
			grid.fy(0.18), V2Felt.rail(theme), CasinoPalette.V2_RAIL_DARK, CasinoPalette.V2_RAIL_HI);
		g.fill(l.rail().x(), l.rail().y(), l.rail().right(), l.rail().bottom(), V2Felt.rail(theme));
		g.fill(l.rail().x(), l.rail().bottom() - 1, l.rail().right(), l.rail().bottom(), CasinoPalette.V2_GOLD_SHADE);
		drawRack(g, l.rack());
		drawShoe(g, l.shoe(), back);
		V2Draw.fill(g, l.discard(), 0xFF2A1A10);
		V2Draw.outline(g, l.discard(), V2Felt.rail(theme));
		// felt prints (decor; only where nothing is)
		List<Box> content = new ArrayList<>();
		content.add(l.dealer());
		for (SeatColumn c : l.columns()) if (c != null) content.addAll(List.of(c.boxes()));
		int print = V2Felt.print(theme);
		FeltPrint.draw(g, font, l.prints()[0], Component.translatable("gui.burmaldaholic.blackjack.print.pays"), 0.55f, content);
		if (l.prints().length > 1) FeltPrint.draw(g, font, l.prints()[1], Component.translatable("gui.burmaldaholic.blackjack.print.insurance"), 0.55f, content);
		if (!l.dealerNote().isEmpty()) {
			Box n = l.dealerNote();
			V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.blackjack.v2.note.dealer"), new Box(n.x(), n.y(), n.w(), 10), 0, 1,
				CasinoPalette.V2_MUTED, true);
			V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.blackjack.v2.note.s17"), new Box(n.x(), n.y() + 11, n.w(), 10), 0, 1,
				CasinoPalette.V2_MUTED, true);
		}
		// dealer
		int dn = Math.max(2, m.dealer.length);
		int dcw = t == Tier.T1 ? t.seatW : t.heroW;
		int step = BlackjackLayout.step(l.dealer(), dn, dcw);
		for (int i = 0; i < m.dealer.length; i++) {
			Box b = new Box(l.dealer().x() + i * step, l.dealer().y(), dcw, l.dealer().h());
			drawCard(g, b, m.dealer[i], i < m.dealerAt.length ? m.dealerAt[i] : 0, nowMs, l.shoe(), back, 1000L * m.roundSeq + i, 0xFFFFFFFF);
		}
		Component dt = dealerTotal();
		if (dt != null && !l.dealerTotal().isEmpty()) TotalPill.draw(g, font, l.dealerTotal(), dt, t == Tier.T1 ? 1 : 2);
		// seats
		int slots = l.columns().length;
		Map<Integer, Integer> seatOfSlot = new HashMap<>();
		for (int s = 0; s < m.seatCount; s++) seatOfSlot.put(l.slotOfSeat()[s], s);
		for (int slot = 0; slot < slots; slot++) {
			Integer s = seatOfSlot.get(slot);
			SeatColumn c = l.columns()[slot];
			if (s == null) continue;
			Seat st = seat(s);
			if (c == null || st == null && !hero(s)) {
				// an empty seat: the printed box (gold ring + "Sit" on hover when the viewer can sit)
				boolean hover = !v.seated() && l.spots()[slot].contains(mx, my) && v.canSit();
				FeltPrint.seatBox(g, font, l.spots()[slot], hover);
				continue;
			}
			drawSeat(g, t, l, slot, c, st, v, nowMs, back, mx, my);
		}
		maybeResult(l);
	}

	private void drawSeat(GuiGraphicsExtractor g, Tier t, BlackjackLayout l, int slot, SeatColumn c, @Nullable Seat st, BlackjackView v, double nowMs, int back,
			int mx, int my) {
		boolean hero = slot == l.hero();
		int cw = hero ? t.heroW : t.seatW;
		boolean settled = st != null && st.settled() && !m.busy;
		boolean acting = st != null && st.seat() == m.currentSeat && !m.busy && "turns".equals(m.phase);
		List<Hand> hands = st == null ? List.of() : st.hands();
		if (!hands.isEmpty()) {
			int[] per = new int[hands.size()];
			for (int h = 0; h < per.length; h++) per[h] = hands.get(h).codes().length;
			Box[] fans = BlackjackLayout.handFans(c.cards(), per, cw);
			for (int h = 0; h < hands.size(); h++) {
				Hand hand = hands.get(h);
				Box fan = fans[h];
				int n = hand.codes().length;
				int step = BlackjackLayout.step(fan, n, cw);
				boolean lost = !hand.outcome().isEmpty() && !m.busy && List.of("bust", "lose", "dealer_blackjack", "surrender").contains(hand.outcome());
				int argb = lost ? 0xFFA0A0A0 : 0xFFFFFFFF;
				for (int k = 0; k < n; k++) {
					Box b = new Box(fan.x() + k * step, fan.y(), cw, fan.h());
					drawCard(g, b, hand.codes()[k], k < hand.at().length ? hand.at()[k] : 0, nowMs, l.shoe(), back, 1000L * m.roundSeq + 100L * (st.seat() + 1) + 10L * h + k,
						argb);
				}
				if (acting && h == m.currentHand && n > 0) CardsV2.glow(g, new Box(fan.x(), fan.y(), cw + (n - 1) * step, fan.h()), CasinoPalette.V2_GOLD);
			}
		} else if (hero && v.phase() == BlackjackView.Phase.BETTING) {
			drawHeroSpot(g, l, v, mx, my);
		}
		// outcome slot: the total pill while playing, the stamp word on settle
		if (st != null && settled) {
			Stamps.Outcome o = stampOf(BlackjackBoardRenderer.word(outcomes(st)));
			long age = Util.getMillis() - stampSeen.computeIfAbsent(st.seat(), k -> Util.getMillis());
			Stamp.drawResolved(g, font, c.outcome(), o, l.levels()[slot], hero, age);
		} else if (st != null && !c.outcome().isEmpty() && c.outcome().w() > SeatColumn.DOT) {
			Component tt = totalOf(st);
			if (tt != null) TotalPill.draw(g, font, c.outcome(), tt, hero && l.levels()[slot] == 0 ? 2 : 1);
		}
		// stack + value pill (the bet; the net on settle)
		long bet = st == null ? 0 : st.hands().isEmpty() ? st.bet() : st.hands().stream().mapToLong(Hand::bet).sum();
		if (hero && bet <= 0 && v.phase() == BlackjackView.Phase.BETTING) bet = v.placed() > 0 ? v.placed() : draft;
		if (bet > 0 && !(hero && hands.isEmpty() && v.phase() == BlackjackView.Phase.BETTING)) {
			if (!c.stack().isEmpty()) ChipStack.draw(g, c.stack(), bet);
			Component vt = settled ? V2Draw.net(st.net()) : Texts.number(bet);
			ValuePill.draw(g, font, c.value(), vt, settled ? ValuePill.ofNet(st.net()) : hero ? ValuePill.Style.YOU : ValuePill.Style.OTHER);
		}
		// plate: the name only
		Component name = st != null ? st.display() : Component.translatable("gui.burmaldaholic.blackjack.v2.your_bet");
		if (hero && st == null && minecraft != null && minecraft.player != null) name = minecraft.player.getName();
		SeatPlate.draw(g, font, c.plate(), name, hero ? SeatPlate.Kind.YOU : acting ? SeatPlate.Kind.ACTING : SeatPlate.Kind.OTHER, st != null && st.bot());
	}

	/** The viewer's betting spot: the pulsing gold ring, "Your bet" above it, the draft (or the placed bet) inside. */
	private void drawHeroSpot(GuiGraphicsExtractor g, BlackjackLayout l, BlackjackView v, int mx, int my) {
		Box spot = heroSpot(l);
		boolean open = v.bettingOpen();
		double pulse = open ? 0.5 + 0.5 * Math.sin(Util.getMillis() / 260.0) : 0;
		int ring = open ? CardsV2Colors.lerp(CasinoPalette.V2_GOLD_SHADE, CasinoPalette.V2_GOLD, pulse) : V2Felt.print(theme());
		FeltPrint.ring(g, spot, ring);
		if (open) FeltPrint.ring(g, spot.inset(-3), V2Draw.alpha(CasinoPalette.V2_GOLD, 0.35f));
		Component yb = Component.translatable("gui.burmaldaholic.blackjack.v2.your_bet");
		int yw = font.width(yb) + 8;
		Box tag = new Box(spot.cx() - yw / 2, spot.y() - 15, yw, 11);
		if (open && tag.y() > l.dealer().bottom() + 2) {
			V2Draw.pill(g, tag, CasinoPalette.V2_GOLD, CasinoPalette.V2_INK);
			V2Draw.centred(g, font, yb, tag, 1, CasinoPalette.V2_INK, false);
		}
		long amount = v.placed() > 0 ? v.placed() : draft;
		var pending = chrome.pending();
		if (amount > 0) {
			int chip = chrome.grid().tier.feltChip;
			Box st = Box.centredAt(spot.cx(), spot.cy(), chip, chip + 4);
			ChipStack.draw(g, st, amount, pending != null ? 0.6f : 1f);
			Component vt = Texts.number(amount);
			int vw = ValuePill.width(font, vt);
			ValuePill.draw(g, font, new Box(spot.right() - 4, spot.y() + 2, vw, SeatColumn.PILL_1X), vt, ValuePill.Style.YOU);
		}
	}

	private void drawCard(GuiGraphicsExtractor g, Box b, int code, int at, double nowMs, Box shoe, int back, long key, int argb) {
		double dealMs = (m.roundTick + at) * 50.0;
		float u = m.roundTick > 0 ? CardsV2.deal(nowMs, dealMs) : 1;
		if (u <= 0) return;
		float e = CardsV2.ease(u);
		int x = Math.round(shoe.x() + (b.x() - shoe.x()) * e);
		int y = Math.round(shoe.y() + (b.y() - shoe.y()) * e);
		float flip = 1;
		if (code < 0) {
			flipSeen.put(key, 0L); // a back now (the hole card): remember it, so its reveal flips
		} else {
			Long seen = flipSeen.get(key);
			if (seen != null && seen == 0L) {
				seen = Util.getMillis();
				flipSeen.put(key, seen);
			}
			// a card that arrives face up turns during its slide; the revealed hole card turns in place
			flip = seen != null ? CardsV2.flip(seen) : u < 1 ? Math.min(1, u * 1.4f) : 1;
		}
		CardsV2.card(g, font, code, back, new Box(x, y, b.w(), b.h()), flip, argb);
	}

	/** The dealer rack's six wells: the five house chips largest first, then the smallest again (the rack art's order). */
	private static final long[] RACK = java.util.stream.LongStream.concat(
		java.util.Arrays.stream(dev.nezo.burmaldaholic.core.chips.ChipMath.DENOMINATIONS).filter(v -> v <= 500).asLongStream(),
		java.util.stream.LongStream.of(dev.nezo.burmaldaholic.core.chips.ChipMath.largestAtMost(1))).toArray();

	private void drawRack(GuiGraphicsExtractor g, Box r) {
		V2Draw.fill(g, r, 0xFF2A1A10);
		V2Draw.outline(g, r, CasinoPalette.V2_GOLD_SHADE);
		int cols = 6;
		int cw = Math.max(2, (r.w() - 4) / cols);
		long[] d = RACK;
		for (int i = 0; i < cols; i++) {
			int x = r.x() + 2 + i * cw;
			g.fill(x + 1, r.y() + 2, x + cw - 1, r.bottom() - 2, ChipStack.color(d[i]));
			for (int yy = r.y() + 3; yy < r.bottom() - 2; yy += 2) g.fill(x + 1, yy, x + cw - 1, yy + 1, ChipStack.stripe(d[i]));
		}
	}

	private void drawShoe(GuiGraphicsExtractor g, Box s, int back) {
		V2Draw.fill(g, s, 0xFF4A2A14);
		V2Draw.outline(g, s, CasinoPalette.V2_GOLD_SHADE);
		g.fill(s.x() + 2, s.y() + 2, s.right() - 2, s.y() + 4, 0xFFF4ECF8);
		for (int x = s.x() + 4; x < s.right() - 3; x += 4) g.fill(x, s.y() + 6, x + 1, s.bottom() - 2, 0xFF2A1A10);
		g.fill(s.x() - 2, s.y() + 3, s.x(), s.bottom() - 3, 0xFF2A5ED8);
	}

	/** Banner, celebration and sounds once per round when the viewer's seat settled. */
	private void maybeResult(BlackjackLayout l) {
		Seat me = me();
		if (me == null || !me.settled() || m.busy || settledAt < 0) return;
		if (bannerRound != m.roundSeq) {
			bannerRound = m.roundSeq;
			chrome.banner.show(me.net());
			ClientFx.balanceHold.accept(600);
		}
		if (celebratedRound != m.roundSeq && Util.getMillis() - settledAt > 1300) {
			celebratedRound = m.roundSeq;
			try {
				WinTier tier = WinTier.valueOf(me.tier());
				if (tier.isWin() || tier == WinTier.RETURN) {
					if (me.ret() >= 10 * Math.max(1, me.staked())) {
						chrome.banner.hide();
						CelebrationOverlay.get().play(CelebrationRequest.core(tier, me.ret(), me.staked(), SeedMix.mix(m.roundSeq, me.seat())));
					}
				} else if (tier == WinTier.LOSS) {
					FxSounds.play("lose", 0.6f, 1f);
				}
			} catch (IllegalArgumentException ignored) {
				// no tier sent
			}
		}
	}

	/** Small colour helper. */
	static final class CardsV2Colors {
		static int lerp(int a, int b, double t) {
			int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
			int gg = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
			int bb = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
			return 0xFF000000 | r << 16 | gg << 8 | bb;
		}
	}
}
