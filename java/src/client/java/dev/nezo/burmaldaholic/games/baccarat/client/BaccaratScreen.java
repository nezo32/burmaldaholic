package dev.nezo.burmaldaholic.games.baccarat.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.cards.CardSprites;
import dev.nezo.burmaldaholic.client.table.cards.CardsV2;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
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
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules;
import dev.nezo.burmaldaholic.games.baccarat.logic.Card;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratFlow;
import dev.nezo.burmaldaholic.games.baccarat.present.BancoConfirm;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratLayout;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Baccarat / chemin de fer, table UI v2 (REDESIGN.md §7.2, docs/architecture/ui-v2.md): the PLAYER (blue) and BANKER
 * (red) hands with 2× totals in the upper half, the row of bet boxes P.PAIR · PLAYER · TIE · BANKER · B.PAIR with the
 * payout printed inside each box, the seat plates along the bottom rail. House tables are multi-spot (a chip arms, a box
 * takes it); chemin de fer drafts the punt / the bank with the chips and sends it with the primary. Squeeze and third-card
 * announcements go to the status line, never on the cards. Gates from {@link BaccaratFlow}, boxes from
 * {@link BaccaratLayout}.
 */
public class BaccaratScreen extends V2TableScreen<BaccaratView> {
	private static final int PLAYER_EDGE = 0xFF2A5ED8, BANKER_EDGE = 0xFFD42A3A;
	private @Nullable BaccaratLayout layout;
	private long draft;
	private final Deque<Long> history = new ArrayDeque<>();
	/** Chips put down this coup, newest first: box index and the chip's amount (Undo / right-click take one back). */
	private final Deque<long[]> placed = new ArrayDeque<>();
	private final Map<Long, Long> flipSeen = new HashMap<>();
	private long lastCoup = -1;
	private long bannerCoup = -1, celebratedCoup = -1, resultAt = -1;
	/** A big Banco asks for a second press within 3 s (T-A2 R4). */
	private final BancoConfirm banco = new BancoConfirm(BaccaratFlow.INSTANCE, Util::getMillis);

	public BaccaratScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType().name().endsWith("player_banked") ? "gui.burmaldaholic.baccarat.title_chemmy"
			: menu.tableType().name().endsWith("high_roller") ? "gui.burmaldaholic.baccarat.title_high_roller" : "gui.burmaldaholic.baccarat.title"));
		chrome.keys(KeyMap.standard().bind(KeyMap.P, KeyMap.Ids.felt("box.player")).bind(KeyMap.B, KeyMap.Ids.felt("box.banker"))
			.bind(KeyMap.T, KeyMap.Ids.felt("box.tie")).bind(KeyMap.O, KeyMap.Ids.action("banco")).bind(KeyMap.N, KeyMap.Ids.action("pass")));
	}

	// ---- state -------------------------------------------------------------------------------------------------------

	private CompoundTag ch() {
		return state().getCompoundOrEmpty("chemmy");
	}

	private boolean chemmy() {
		return !state().getBooleanOr("house_coup", true);
	}

	private long[] slip(String key) {
		CompoundTag t = state().getCompoundOrEmpty(key);
		long[] out = new long[BaccaratPub.BOXES];
		for (int k = 0; k < BaccaratPub.BOXES; k++) out[k] = t.getLongOr(BaccaratPub.BOX_IDS[k], 0);
		return out;
	}

	private int[] arr(String key) {
		return state().getIntArray(key).orElse(new int[0]);
	}

	@Override
	protected BaccaratView view(CompoundTag s) {
		long coup = s.getLongOr("coup_no", 0);
		if (coup != lastCoup) {
			lastCoup = coup;
			flipSeen.clear();
			placed.clear();
			resultAt = -1;
		}
		String phase = s.getStringOr("phase", "idle");
		if (!"betting".equals(phase) && !"bank_offer".equals(phase)) {
			draft = 0;
			history.clear();
		}
		if ("result".equals(phase) && resultAt < 0) resultAt = Util.getMillis();
		return build();
	}

	private BaccaratView build() {
		CompoundTag s = state();
		CompoundTag c = ch();
		int secs = -1;
		for (String t : new String[] {"bet", "offer", "phase", "idle"}) {
			long v = timerSeconds(t);
			if (v >= 0) {
				secs = (int) v;
				break;
			}
		}
		int seated = s.getListOrEmpty("seat_list").size();
		return new BaccaratView(mySeat() >= 0, seated < s.getIntOr("seat_count", 7), chemmy(), s.getStringOr("phase", "idle"),
			Math.max(1, s.getLongOr("bet_min", minBet())), maxBet(), s.getLongOr("min_total", 0), balance(), slip("slip"), s.getBooleanOr("ready", false),
			s.getBooleanOr("can_rebet", false), s.getBooleanOr("pairs", true), secs, s.getBooleanOr("waiting_next", false), draft,
			c.getBooleanOr("offer_you", false), c.getBooleanOr("offer_keep", false), c.getBooleanOr("you_bank", false), c.getBooleanOr("held", false),
			c.getLongOr("open", 0), c.getLongOr("my_punt", 0), !c.getStringOr("banco", "").isEmpty() || !c.getStringOr("banco_bot", "").isEmpty(),
			c.getLongOr("min_bank", 0), who(c, "banker").getString(), chrome.pending() != null);
	}

	private static Component who(CompoundTag c, String field) {
		String bot = c.getStringOr(field + "_bot", "");
		return bot.isEmpty() ? Texts.raw(c.getStringOr(field, "")) : Component.translatable(bot);
	}

	@Override
	protected Flow<BaccaratView> flow() {
		return banco;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (banco.expired()) refresh();
	}

	@Override
	protected void refresh() {
		if (font == null) return;
		if (view != null) view = build();
		chrome.bet.multiSpot(view != null && !view.chemmy());
		super.refresh();
		if (view == null) return;
		BaccaratFlow.Line[] lines = BaccaratFlow.status(view);
		Component l1 = line(lines[0]);
		Component l2 = line(lines[1]);
		Component sq = squeezeLine();
		Component an = announcement();
		if (sq != null) l2 = sq;
		else if (an != null && ("reveal".equals(view.phase()) || "result".equals(view.phase()))) l2 = an;
		if ("result".equals(view.phase()) && hasMyResult()) {
			long net = myNet();
			l1 = net > 0 ? Component.translatable("gui.burmaldaholic.baccarat.you_win", Texts.number(net))
				: net < 0 ? Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(-net))
				: Component.translatable("gui.burmaldaholic.common.result.push");
			chrome.status.color(net > 0 ? CasinoPalette.V2_WIN : net < 0 ? CasinoPalette.V2_LOSE : CasinoPalette.V2_PUSH);
		} else {
			chrome.status.color(CasinoPalette.V2_GOLD);
		}
		chrome.status.set(l1, l2);
	}

	private static @Nullable Component line(BaccaratFlow.Line l) {
		return l.key().isEmpty() ? null : V2Draw.tr(l.key(), l.args());
	}

	private boolean hasMyResult() {
		return state().contains("my_result") || state().contains("my_punt") || state().contains("my_bank_delta");
	}

	/** GameTests: the total return of the viewer's slip in the shown coup (-1 = none). */
	public long myReturnForTests() {
		return state().contains("my_result") ? state().getCompoundOrEmpty("my_result").getLongOr("ret", 0) : -1;
	}

	private long myNet() {
		CompoundTag res = state().getCompoundOrEmpty("my_result");
		long net = res.getLongOr("ret", 0) - res.getLongOr("staked", 0);
		if (state().contains("my_punt")) net += state().getLongOr("my_punt_return", 0) - state().getLongOr("my_punt", 0);
		if (state().contains("my_bank_delta")) net += state().getLongOr("my_bank_delta", 0);
		return net;
	}

	private double nowMs() {
		var level = Minecraft.getInstance().level;
		return level == null ? 0 : level.getGameTime() * 50.0;
	}

	private List<Card> cardsOf(String key) {
		List<Card> out = new ArrayList<>();
		for (int c : arr(key + "_cards")) if (c >= 0 && c < 52) out.add(Card.fromCode(c));
		return out;
	}

	/** "Player draws a third card · Banker stands on 7" from the public cards once each announcement is public. */
	private @Nullable Component announcement() {
		List<Card> p = cardsOf("player");
		List<Card> b = cardsOf("banker");
		if (!state().contains("announce_0") || p.size() < 2 || b.size() < 2) return null;
		int pt = BaccaratRules.total(p.subList(0, 2));
		int bt = BaccaratRules.total(b.subList(0, 2));
		if (BaccaratRules.isNatural(pt) || BaccaratRules.isNatural(bt)) {
			return Component.translatable("gui.burmaldaholic.baccarat.natural", Texts.number(Math.max(pt, bt)));
		}
		boolean pDraws = BaccaratRules.playerDraws(pt);
		Component first = pDraws ? Component.translatable("gui.burmaldaholic.baccarat.player_draws")
			: Component.translatable("gui.burmaldaholic.baccarat.player_stands", Texts.number(pt));
		boolean bankerKnown = !pDraws || state().contains("announce_1") || arr("banker_cards").length > 2;
		if (!bankerKnown) return first;
		int third = p.size() > 2 ? p.get(2).points() : -1;
		if (pDraws && p.size() < 3) return first;
		boolean bDraws = BaccaratRules.bankerDraws(bt, third);
		Component second = bDraws ? Component.translatable("gui.burmaldaholic.baccarat.banker_draws")
			: Component.translatable("gui.burmaldaholic.baccarat.banker_stands", Texts.number(bt));
		return Component.translatable("gui.burmaldaholic.cards.join", first, second);
	}

	/** Who squeezes the card being peeled now (status line, never on the cards). */
	private @Nullable Component squeezeLine() {
		double now = nowMs();
		long rv = state().getLongOr("rv_start", 0);
		for (int side = 0; side < 2; side++) {
			String key = side == 0 ? "player" : "banker";
			int[] rev = arr(key + "_rev");
			int[] sqz = arr(key + "_sqz");
			for (int i = 0; i < rev.length && i < sqz.length; i++) {
				if (rev[i] < 0 || sqz[i] <= 0) continue;
				double start = (rv + rev[i]) * 50.0;
				if (now >= start && now < start + sqz[i] * 50.0) {
					String w = state().getStringOr(side == 0 ? "sq_player" : "sq_banker", "");
					String me = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
					if (!w.isEmpty() && w.equals(me)) return Component.translatable("gui.burmaldaholic.baccarat.fx.you_squeeze");
					if (!w.isEmpty()) return Component.translatable("gui.burmaldaholic.baccarat.fx.squeezing", Texts.raw(w));
					return Component.translatable(side == 0 ? "gui.burmaldaholic.baccarat.fx.player_card" : "gui.burmaldaholic.baccarat.fx.banker_card");
				}
			}
		}
		return null;
	}

	// ---- actions -----------------------------------------------------------------------------------------------------

	@Override
	protected @Nullable BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				// chemin de fer: chips build the punt / the bank (single spot)
				if (view == null || !BaccaratFlow.INSTANCE.chip(denom, view).enabled()) return;
				draft += denom;
				history.push(denom);
				FxSounds.play("chip_place", 1f);
				refresh();
			}

			@Override
			public void remove(long denom) {
				tool(BetGates.UNDO);
			}

			@Override
			public void tool(String toolId) {
				if (view == null) return;
				if (view.chemmy()) {
					switch (toolId) {
						case BetGates.UNDO -> {
							if (!history.isEmpty()) draft = Math.max(0, draft - history.pop());
						}
						case BetGates.CLEAR -> {
							if (draft == 0 && view.myPunt() > 0) sendTracked("clear");
							draft = 0;
							history.clear();
						}
						case BetGates.DOUBLE -> {
							history.push(draft);
							draft *= 2;
						}
						default -> {}
					}
					refresh();
					return;
				}
				switch (toolId) {
					case BetGates.UNDO -> undo();
					case BetGates.CLEAR -> {
						placed.clear();
						sendTracked("clear");
					}
					case BetGates.REBET -> sendTracked("rebet");
					case BetGates.DOUBLE -> {
						long[] mine = view.bets();
						for (int k = 0; k < BaccaratPub.BOXES; k++) if (mine[k] > 0) placeChip(k, mine[k]);
					}
					default -> {}
				}
			}
		};
	}

	/** Undo = one {@code unbet_chip} of the newest chip (review R-AB m8: one packet, the server's slip stays the truth). */
	private void undo() {
		chipBack(-1);
	}

	/** Takes one chip back from {@code box} (-1 = the newest chip anywhere) with one {@code unbet_chip}. */
	private void chipBack(int box) {
		if (view == null) return;
		List<long[]> hist = new ArrayList<>(placed);
		BaccaratFlow.ChipBack back = BaccaratFlow.chipBack(hist, view.bets(), box);
		if (back == null) return;
		if (back.historyIndex() >= 0) {
			hist.remove(back.historyIndex());
			placed.clear();
			placed.addAll(hist);
		}
		CompoundTag args = new CompoundTag();
		args.putString("box", BaccaratPub.BOX_IDS[back.box()]);
		args.putLong("amount", back.amount());
		sendTracked("unbet_chip", args, "", 0);
		FxSounds.play("chip_place", 0.8f);
	}

	private void placeChip(int box, long amount) {
		if (view == null) return;
		placed.push(new long[] {box, amount});
		CompoundTag args = new CompoundTag();
		args.putString("box", BaccaratPub.BOX_IDS[box]);
		args.putLong("amount", amount);
		sendTracked("bet", args, BaccaratPub.BOX_IDS[box], amount);
		FxSounds.play("chip_place", 1f);
	}

	@Override
	public void onGate(String id, Gate gate) {
		if (!banco.press(gate, balance())) {
			// first press of a big Banco: the primary now reads "Confirm Banco N?" until a second press or 3 s pass
			refresh();
			return;
		}
		switch (gate.action()) {
			case "deal" -> sendTracked("ready");
			case "punt" -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", draft);
				sendTracked("punt", args, "player", draft);
				draft = 0;
				history.clear();
			}
			case "take_bank" -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", Math.max(draft, view == null ? 0 : view.minBank()));
				sendTracked("take_bank", args, "", 0);
			}
			default -> sendTracked(gate.action());
		}
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		if (id.startsWith("seat.")) {
			if (enabled) sendTracked(BetGates.SIT);
			return true;
		}
		if (id.startsWith("felt.box.") && view != null) {
			int box = boxIndex(id.substring("felt.box.".length()));
			if (box < 0) return false;
			if (button.right()) {
				// right-click: one chip off this box (not the whole box)
				if (enabled && view.bets()[box] > 0) chipBack(box);
				return true;
			}
			long d = chrome.bet.armOrDefault();
			Gate g = BaccaratFlow.INSTANCE.box(box, Math.max(1, d), view);
			if (d <= 0 || !g.enabled()) {
				chrome.refuse(g);
				return true;
			}
			placeChip(box, d);
			return true;
		}
		return false;
	}

	private static int boxIndex(String id) {
		for (int k = 0; k < BaccaratPub.BOXES; k++) if (BaccaratPub.BOX_IDS[k].equals(id)) return k;
		return -1;
	}

	@Override
	protected void openRules() {
		chrome.status.flash(Component.translatable("gui.burmaldaholic.baccarat.rules.2"));
	}

	// ---- layout ------------------------------------------------------------------------------------------------------

	private boolean[] open() {
		boolean ch = chemmy();
		boolean pairs = state().getBooleanOr("pairs", true);
		return new boolean[] {true, true, !ch, !ch && pairs, !ch && pairs};
	}

	@Override
	protected void layoutPlay(TableGrid grid, BaccaratView v, HitMap hits) {
		int seats = Math.max(1, state().getIntOr("seat_count", 7));
		layout = BaccaratLayout.of(grid, open(), seats, new int[] {font.width(Component.translatable("gui.burmaldaholic.baccarat.player")),
			font.width(Component.translatable("gui.burmaldaholic.baccarat.banker"))});
		BaccaratLayout l = layout;
		if (!v.chemmy()) {
			for (int k = 0; k < BaccaratPub.BOXES; k++) {
				if (l.boxes()[k].isEmpty()) continue;
				hits.add(KeyMap.Ids.felt("box." + BaccaratPub.BOX_IDS[k]), l.boxes()[k], v.seated() && v.houseOpen() && !v.ready());
			}
		}
		if (!v.seated()) {
			for (int i = 0; i < l.plates().length; i++) if (seatAt(i) == null) hits.add(KeyMap.Ids.seat(i), l.plates()[i], v.canSit());
		}
		List<Box> obstacles = new ArrayList<>(List.of(l.player(), l.bankerCards(), l.playerCards()));
		for (Box b : l.boxes()) obstacles.add(b);
		// the result banner (K2a: full, else the compact 200 x 20) may cover the hands' empty felt, never the cards, totals,
		// labels or the bet boxes' names / payouts (run-4 shot: PLAYER WINS covered the PLAYER / TIE / BANKER headers)
		List<Box> bannerObs = new ArrayList<>(List.of(l.playerCards(), l.bankerCards(), l.playerTotal(), l.bankerTotal(), l.playerLabel(), l.bankerLabel()));
		for (Box b : l.boxName()) if (b != null && !b.isEmpty()) bannerObs.add(b);
		for (Box b : l.boxPays()) if (b != null && !b.isEmpty()) bannerObs.add(b);
		Box hands = new Box(l.player().x(), l.player().y(), l.banker().right() - l.player().x(), l.player().h());
		chrome.banner.regions(List.of(new Box(l.player().x(), l.player().bottom() - 8, l.banker().right() - l.player().x(), 40), hands), bannerObs);
	}

	private @Nullable CompoundTag seatAt(int index) {
		ListTag list = state().getListOrEmpty("seat_list");
		for (int i = 0; i < list.size(); i++) if (list.getCompoundOrEmpty(i).getIntOr("index", -1) == index) return list.getCompoundOrEmpty(i);
		return null;
	}

	// ---- drawing -----------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, BaccaratView v, int mx, int my, float partial) {
		BaccaratLayout l = layout;
		if (l == null) return;
		Tier t = grid.tier;
		var theme = theme();
		int print = V2Felt.print(theme);
		double now = Minecraft.getInstance().level == null ? 0 : (Minecraft.getInstance().level.getGameTime() + partial) * 50.0;
		// the players' rail along the bottom (decor, under the plates)
		if (l.plates().length > 0) {
			Box p0 = l.plates()[0];
			int th = t == Tier.T3 ? 10 : t == Tier.T2 ? 8 : 6;
			int ry = p0.cy() - th / 2;
			g.fill(grid.play.x(), ry, grid.play.right(), ry + th, V2Felt.rail(theme));
			g.fill(grid.play.x(), ry, grid.play.right(), ry + 1, CasinoPalette.V2_RAIL_DARK);
			g.fill(grid.play.x(), ry + 1, grid.play.right(), ry + 2, CasinoPalette.V2_RAIL_HI);
			g.fill(grid.play.x(), ry + th / 2, grid.play.right(), ry + th / 2 + 1, CasinoPalette.V2_GOLD_SHADE);
			g.fill(grid.play.x(), ry + th - 1, grid.play.right(), ry + th, CasinoPalette.V2_RAIL_DARK);
		}
		int winner = winner();
		hand(g, t, l.player(), l.playerCards(), l.playerLabel(), l.playerTotal(), "player", PLAYER_EDGE, winner == 1, winner == 3, now);
		hand(g, t, l.banker(), l.bankerCards(), l.bankerLabel(), l.bankerTotal(), "banker", BANKER_EDGE, winner == 2, winner == 3, now);
		// bet boxes
		long[] mine = v.bets();
		long[] others = slip("others");
		CompoundTag rets = state().getCompoundOrEmpty("my_result").getCompoundOrEmpty("returns");
		boolean result = "result".equals(v.phase()) && state().contains("my_result");
		if (v.chemmy()) {
			mine = new long[BaccaratPub.BOXES];
			mine[BaccaratPub.P] = v.myPunt() + (v.youBank() ? 0 : draft);
			others = new long[BaccaratPub.BOXES];
			others[BaccaratPub.B] = ch().getLongOr("bank", 0);
		}
		boolean armed = v.houseOpen() && v.seated() && !v.ready();
		for (int k : BaccaratLayout.ORDER) {
			Box b = l.boxes()[k];
			if (b.isEmpty()) continue;
			boolean hover = armed && b.contains(mx, my);
			int edge = hover ? CasinoPalette.V2_GOLD : CasinoPalette.withAlpha(print, 0.7f);
			if (hover) V2Draw.fill(g, b, CasinoPalette.withAlpha(CasinoPalette.V2_GOLD, 0.10f));
			V2Draw.outline(g, b, edge);
			V2Draw.outline(g, b.inset(1), CasinoPalette.withAlpha(print, 0.25f));
			int nameColor = k == BaccaratPub.P ? 0xFF8AB0FF : k == BaccaratPub.B ? 0xFFFF9A9A : CasinoPalette.withAlpha(print, 0.9f);
			V2Draw.centred(g, font, Component.translatable("gui.burmaldaholic.baccarat." + BaccaratPub.BOX_IDS[k]), l.boxName()[k], 1, nameColor, true);
			V2Draw.centred(g, font, pays(k, v.chemmy()), l.boxPays()[k], 1, CasinoPalette.withAlpha(print, 0.7f), false);
			if (others[k] > 0 && !l.others()[k].isEmpty()) {
				Component ot = Texts.number(others[k]);
				Box ob = new Box(l.others()[k].x(), l.others()[k].y(), Math.min(l.others()[k].w(), ValuePill.width(font, ot)), SeatColumn.PILL_1X);
				ValuePill.draw(g, font, ob, ot, ValuePill.Style.OTHER);
			}
			if (mine[k] > 0) {
				var pending = chrome.pending();
				boolean pend = pending != null && pending.spot().equals(BaccaratPub.BOX_IDS[k]);
				ChipStack.draw(g, l.mine()[k], mine[k], pend ? 0.6f : 1f);
				Component vt = Texts.number(mine[k]);
				ValuePill.Style st = ValuePill.Style.YOU;
				if (result && rets.contains(BaccaratPub.BOX_IDS[k])) {
					long net = rets.getLongOr(BaccaratPub.BOX_IDS[k], 0) - mine[k];
					vt = V2Draw.net(net);
					st = ValuePill.ofNet(net);
				}
				ValuePill.draw(g, font, fit(l.mineValue()[k], ValuePill.width(font, vt)), vt, st);
			}
		}
		// plates along the rail
		for (int i = 0; i < l.plates().length; i++) {
			CompoundTag s = seatAt(i);
			Box p = l.plates()[i];
			if (s == null) {
				if (!v.seated()) {
					boolean hover = p.contains(mx, my) && v.canSit();
					V2Draw.outline(g, p, hover ? CasinoPalette.V2_GOLD : CasinoPalette.withAlpha(print, 0.35f));
					if (hover) V2Draw.centred(g, font, Component.translatable("gui.burmaldaholic.core.v2.sit"), p, 1, CasinoPalette.V2_GOLD, true);
				}
				continue;
			}
			String bot = s.getStringOr("bot", "");
			Component name = bot.isEmpty() ? Texts.raw(s.getStringOr("name", "")) : Component.translatable(bot);
			boolean you = s.getBooleanOr("you", false);
			SeatPlate.Kind kind = you ? SeatPlate.Kind.YOU : s.getBooleanOr("banker", false) ? SeatPlate.Kind.ACTING : SeatPlate.Kind.OTHER;
			SeatPlate.draw(g, font, p, name, kind, !bot.isEmpty());
		}
		maybeResult();
	}

	private static Box fit(Box b, int w) {
		return new Box(b.x(), b.y(), Math.min(b.w(), Math.max(12, w)), b.h());
	}

	private Component pays(int k, boolean chemmy) {
		if (chemmy) return k == BaccaratPub.B ? Component.translatable("gui.burmaldaholic.baccarat.v2.pays.bank", Texts.number(ch().getLongOr("bank", 0)))
			: Component.translatable("gui.burmaldaholic.baccarat.v2.pays.odds", 1);
		int bp = state().getIntOr("commission_bp", 500);
		return switch (k) {
			case BaccaratPub.B -> Component.translatable("gui.burmaldaholic.baccarat.v2.pays.banker", Texts.decimal(trim(bp / 100.0)));
			case BaccaratPub.T -> Component.translatable("gui.burmaldaholic.baccarat.v2.pays.odds", state().getIntOr("tie_pays", 8));
			case BaccaratPub.PP, BaccaratPub.BP -> Component.translatable("gui.burmaldaholic.baccarat.v2.pays.odds", state().getIntOr("pair_pays", 11));
			default -> Component.translatable("gui.burmaldaholic.baccarat.v2.pays.odds", 1);
		};
	}

	private static String trim(double d) {
		return d == Math.floor(d) ? Long.toString((long) d) : Double.toString(d);
	}

	/** 1 Player, 2 Banker, 3 Tie once the result phase shows it; 0 before. */
	private int winner() {
		if (!"result".equals(state().getStringOr("phase", ""))) return 0;
		List<Card> p = cardsOf("player");
		List<Card> b = cardsOf("banker");
		if (p.size() < 2 || b.size() < 2) return 0;
		int pt = BaccaratRules.total(p);
		int bt = BaccaratRules.total(b);
		return pt > bt ? 1 : bt > pt ? 2 : 3;
	}

	private void hand(GuiGraphicsExtractor g, Tier t, Box box, Box fan, Box label, Box totalBox, String key, int edge, boolean won, boolean tie, double now) {
		V2Draw.fill(g, box, CasinoPalette.withAlpha(CasinoPalette.V2_INK, 0.35f));
		int e = won ? CasinoPalette.V2_GOLD : edge;
		V2Draw.outline(g, box, e);
		g.fill(box.x(), box.y(), box.right(), box.y() + 2, e);
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.baccarat." + key), label, 0, 1, CasinoPalette.V2_TEXT, true);
		int[] codes = arr(key + "_cards");
		int[] deal = arr(key + "_deal");
		int[] rev = arr(key + "_rev");
		long rv = state().getLongOr("rv_start", 0);
		int cw = t == Tier.T1 ? t.seatW : t.heroW;
		boolean anyFace = false;
		for (int i = 0; i < codes.length; i++) {
			Box slot = BaccaratLayout.card(fan, i, cw);
			double dealMs = (rv + (i < deal.length ? deal[i] : 0)) * 50.0;
			float u = rv > 0 ? CardsV2.deal(now, dealMs) : 1;
			if (u <= 0) continue;
			float ee = CardsV2.ease(u);
			Box from = layout == null ? slot : new Box(chrome.grid().play.right() - 40, chrome.grid().play.y() + 4, slot.w(), slot.h());
			Box at = new Box(Math.round(from.x() + (slot.x() - from.x()) * ee), Math.round(from.y() + (slot.y() - from.y()) * ee), slot.w(), slot.h());
			float flip = 1;
			long k = (key.equals("player") ? 0 : 10) + i;
			if (codes[i] >= 0) {
				anyFace = true;
				Long seen = flipSeen.get(k);
				if (seen != null && seen == 0L) {
					seen = Util.getMillis();
					flipSeen.put(k, seen);
				}
				flip = seen != null ? CardsV2.flip(seen) : 1;
			} else {
				flipSeen.put(k, 0L);
			}
			CardsV2.card(g, font, codes[i], CardSprites.BACK_EMERALD, at, flip, (won || tie || winner() == 0) ? 0xFFFFFFFF : 0xFFA8A8A8);
		}
		if (anyFace) {
			int total = 0;
			for (int c : codes) if (c >= 0) total += Math.min(10, c % 13 + 1) % 10;
			Component tt = Texts.number(total % 10);
			TotalPill.draw(g, font, totalBox.centred(TotalPill.width(font, tt, 2), SeatColumn.PILL_2X), tt, 2);
		}
		if (won || tie) {
			Component word = Component.translatable(tie ? "gui.burmaldaholic.baccarat.v2.stamp.tie" : "gui.burmaldaholic.baccarat.v2.stamp.wins");
			int w = Stamp.width(font, word, 1);
			Box sb = new Box(box.cx() - w / 2, box.y() + 5, w, Stamps.H_1X);
			if (!sb.intersects(label) && !sb.intersects(totalBox)) Stamp.draw(g, font, sb, word, tie ? Stamps.Kind.PUSH : Stamps.Kind.GOLD, 1,
				Util.getMillis() - Math.max(0, resultAt));
		}
	}

	private void maybeResult() {
		if (!"result".equals(state().getStringOr("phase", "")) || !hasMyResult()) return;
		if (bannerCoup != lastCoup) {
			bannerCoup = lastCoup;
			int w = winner();
			Component word = Component.translatable("gui.burmaldaholic.baccarat.v2.banner." + (w == 1 ? "player" : w == 2 ? "banker" : "tie"));
			chrome.banner.show(word, myNet(), 1500);
		}
		CompoundTag res = state().getCompoundOrEmpty("my_result");
		if (celebratedCoup != lastCoup && resultAt > 0 && Util.getMillis() - resultAt > 1300) {
			celebratedCoup = lastCoup;
			long ret = res.getLongOr("ret", 0);
			long staked = res.getLongOr("staked", 0);
			try {
				WinTier tier = WinTier.valueOf(res.getStringOr("tier", ""));
				if (tier.isWin() && ret >= 10 * Math.max(1, staked)) {
					chrome.banner.hide();
					CelebrationOverlay.get().play(CelebrationRequest.core(tier, ret, staked, SeedMix.mix((int) lastCoup, mySeat())));
				} else if (tier == WinTier.LOSS) {
					FxSounds.play("lose", 0.6f, 1f);
				}
			} catch (IllegalArgumentException ignored) {
				// chemmy punt / bank: no tier
			}
		}
	}
}
