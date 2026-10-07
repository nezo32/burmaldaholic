package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.FeltPrint;
import dev.nezo.burmaldaholic.client.ui.v2.SeatPlate;
import dev.nezo.burmaldaholic.client.ui.v2.Stamp;
import dev.nezo.burmaldaholic.client.ui.v2.TotalPill;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2SceneScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.Stamps;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The kit gallery (docs/architecture/ui-v2.md §4.1 "V2Gallery"): every v2 component in its states on a crescent table of
 * five seats, at the current GUI size, driven by a fake table that answers like the server (ack echo next tick, a bet
 * adds to the stake). Phases: BETTING (BetBar), ACTING (ActionRow), RESULT (stamps, banner, rebet). The client GameTest
 * clicks it like a player and screenshots it at the real-player sizes.
 */
public final class KitGalleryV2Screen extends V2SceneScreen<KitGalleryV2Screen.Demo> {
	public enum Phase {
		BETTING,
		ACTING,
		RESULT,
		WATCHING
	}

	/** The fake table's state. */
	public record Demo(Phase phase, long balance, long bet, long lastBet, int heroCards, long net) {}

	/** Actions the screen "sent" (tests read them). */
	public final List<String> sent = new ArrayList<>();
	/** When set, the fake server ignores actions (the watchdog test). */
	public boolean deaf;
	private long ack;
	private Demo demo = new Demo(Phase.BETTING, 12_500, 0, 50, 2, 0);
	private final List<Runnable> inbox = new ArrayList<>();
	private long stampAt = Util.getMillis();

	public KitGalleryV2Screen() {
		super(Component.translatable("block.burmaldaholic.blackjack_table"));
	}

	public Demo demo() {
		return demo;
	}

	public void phase(Phase p) {
		long net = p == Phase.RESULT ? 125 : 0;
		demo = new Demo(p, demo.balance(), p == Phase.WATCHING ? 0 : Math.max(demo.bet(), p == Phase.BETTING ? demo.bet() : 50),
			demo.lastBet(), p == Phase.ACTING ? 3 : 2, net);
		stampAt = Util.getMillis();
		if (p == Phase.RESULT) {
			chrome.banner.show(net);
			chrome.top.balance.delta(net);
		}
		state(demo, ack);
	}

	@Override
	protected void init() {
		super.init();
		chrome.keys(KeyMap.standard().bind(KeyMap.H, "action.hit").bind(KeyMap.S, "action.stand").bind(KeyMap.D, "action.double")
			.bind(KeyMap.P, "action.split").bind(KeyMap.U, "action.surrender"));
		if (view == null) state(demo, ack);
		else refresh();
	}

	@Override
	protected long balance() {
		return demo.balance();
	}

	@Override
	protected long minBet() {
		return 1;
	}

	@Override
	protected long maxBet() {
		return 100;
	}

	@Override
	public void tick() {
		super.tick();
		List<Runnable> now = new ArrayList<>(inbox);
		inbox.clear();
		now.forEach(Runnable::run);
	}

	// ---- the fake server ---------------------------------------------------------------------------------------------

	@Override
	public void sendRaw(String action, CompoundTag args) {
		sent.add(action);
		if (deaf && !action.equals("resync")) return;
		inbox.add(() -> {
			ack++;
			Demo d = demo;
			switch (action) {
				case "bet" -> {
					long a = args.getLongOr("amount", 0);
					d = new Demo(d.phase(), d.balance() - a, d.bet() + a, d.lastBet(), d.heroCards(), 0);
				}
				case "undo", "clear" -> d = new Demo(d.phase(), d.balance() + d.bet(), 0, d.lastBet(), d.heroCards(), 0);
				case "rebet" -> d = new Demo(Phase.BETTING, d.balance() - d.lastBet(), d.lastBet(), d.lastBet(), 2, 0);
				case "double" -> d = new Demo(d.phase(), d.balance() - d.bet(), d.bet() * 2, d.lastBet(), d.heroCards(), 0);
				case "deal" -> d = new Demo(Phase.ACTING, d.balance(), d.bet(), d.bet(), 2, 0);
				case "hit" -> d = new Demo(Phase.ACTING, d.balance(), d.bet(), d.lastBet(), Math.min(5, d.heroCards() + 1), 0);
				case "stand" -> {
					d = new Demo(Phase.RESULT, d.balance() + d.bet() * 2, d.bet(), d.bet(), d.heroCards(), d.bet());
					chrome.banner.show(d.net());
					chrome.top.balance.delta(d.net());
					stampAt = Util.getMillis();
				}
				default -> {}
			}
			demo = d;
			state(demo, ack);
		});
	}

	// ---- flow --------------------------------------------------------------------------------------------------------

	private static BetView bet(Demo d) {
		return BetView.builder().seated(d.phase() != Phase.WATCHING).canSit(true).phase(d.phase() == Phase.BETTING ? BetView.Phase.BETTING
			: d.phase() == Phase.RESULT ? BetView.Phase.BETTING : BetView.Phase.ACTING).bettingOpen(d.phase() == Phase.BETTING || d.phase() == Phase.RESULT)
			.limits(1, 100).balance(d.balance()).myTotal(d.phase() == Phase.RESULT ? 0 : d.bet()).canUndo(d.bet() > 0).canClear(d.bet() > 0)
			.rebet(d.lastBet() > 0, d.lastBet()).canDouble(d.bet() > 0).secondsLeft(d.phase() == Phase.BETTING ? 12 : -1).build();
	}

	private static final Flow<Demo> FLOW = new Flow<>() {
		@Override
		public Gate primary(Demo d) {
			return switch (d.phase()) {
				case ACTING -> Gate.on("stand", "gui.burmaldaholic.blackjack.stand");
				default -> BetGates.betOrRebet("deal", V2Keys.PRIMARY_DEAL, bet(d));
			};
		}

		@Override
		public List<Gate> actions(Demo d) {
			if (d.phase() != Phase.ACTING) return List.of();
			return List.of(Gate.on("hit", "gui.burmaldaholic.blackjack.hit"), Gate.on("double", "gui.burmaldaholic.blackjack.double"),
				Gate.off("split", "gui.burmaldaholic.blackjack.split", "gui.burmaldaholic.core.v2.reason.round_in_progress"),
				Gate.on("surrender", "gui.burmaldaholic.blackjack.surrender"));
		}

		@Override
		public BetView bet(Demo d) {
			return d.phase() == Phase.ACTING ? null : KitGalleryV2Screen.bet(d);
		}

		@Override
		public java.util.Optional<String> hintKey(Demo d) {
			return d.phase() == Phase.BETTING ? java.util.Optional.of(V2Keys.STATUS_CHIPS_ADD)
				: d.phase() == Phase.WATCHING ? java.util.Optional.of(V2Keys.STATUS_PICK_SEAT) : java.util.Optional.empty();
		}
	};

	@Override
	protected Flow<Demo> flow() {
		return FLOW;
	}

	@Override
	protected BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				CompoundTag t = new CompoundTag();
				t.putLong("amount", denom);
				chrome.send("bet", t, "", denom);
			}

			@Override
			public void tool(String toolId) {
				chrome.send(toolId);
			}
		};
	}

	@Override
	public void onGate(String id, Gate gate) {
		if (gate.action().equals(BetGates.REBET)) chrome.send("rebet");
		else super.onGate(id, gate);
	}

	// ---- the PlayArea ------------------------------------------------------------------------------------------------

	private static final String[] NAMES = {"Mira", "Axolotl Al", "", "Very long player name", "Steve"};
	private SeatColumn[] cols = new SeatColumn[0];
	private final int[] levels = new int[5];
	private int heroSlot = 2;
	private final Stamps.Outcome[] outcomes = {Stamps.Outcome.BUST, Stamps.Outcome.PUSH, Stamps.Outcome.BLACKJACK, Stamps.Outcome.WIN,
		Stamps.Outcome.LOSE};

	@Override
	protected void layoutPlay(TableGrid grid, Demo d, HitMap hits) {
		int seats = 5;
		heroSlot = d.phase() == Phase.WATCHING ? -1 : 2;
		int[] cards = {3, 2, d.heroCards(), 5, -1};
		if (d.phase() == Phase.WATCHING) cards[2] = -1;
		int[][] outW = new int[5][];
		int[] valueW = new int[5];
		for (int i = 0; i < 5; i++) {
			Component w = d.phase() == Phase.RESULT ? Stamp.word(outcomes[i]) : Texts.number(10 + i * 3);
			int ww = font.width(w);
			outW[i] = d.phase() == Phase.RESULT ? Stamps.levels(ww, i == heroSlot) : new int[] {TotalPill.width(font, w, i == heroSlot ? 2 : 1)};
			valueW[i] = ValuePill.width(font, Texts.number(150));
		}
		cols = SeatColumn.table(grid, seats, cards, heroSlot, outW, valueW, levels);
		int[][] anchors = SeatColumn.crescent(grid, seats);
		for (int i = 0; i < cols.length; i++) {
			if (cols[i] == null) {
				Box seatBox = Box.centredAt(anchors[i][0], anchors[i][1] - 18, 30, 30);
				hits.add(KeyMap.Ids.seat(i), seatBox, true);
			} else {
				hits.add(KeyMap.Ids.felt("plate." + i), cols[i].plate(), true);
			}
		}
		List<Box> obstacles = new ArrayList<>();
		for (SeatColumn c : cols) if (c != null) obstacles.addAll(List.of(c.boxes()));
		Box dealer = dealerBox(grid);
		obstacles.add(dealer);
		Box gap = new Box(grid.felt().x(), dealer.bottom() + 2, grid.felt().w(), Math.max(0, minTop(cols) - dealer.bottom() - 4));
		chrome.banner.regions(List.of(gap, grid.felt()), obstacles);
	}

	private static int minTop(SeatColumn[] cols) {
		int top = Integer.MAX_VALUE;
		for (SeatColumn c : cols) if (c != null) top = Math.min(top, c.outcome().y());
		return top == Integer.MAX_VALUE ? 200 : top;
	}

	private static Box dealerBox(TableGrid g) {
		int cw = g.tier.heroW;
		return new Box(g.fx(0.5) - cw - cw / 2, g.play.y() + 8, cw * 3, g.tier.heroH);
	}

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, Demo d, int mx, int my, float partial) {
		Box dealer = dealerBox(grid);
		for (int k = 0; k < 3; k++) card(g, dealer.x() + k * (grid.tier.heroW / 2 + 2), dealer.y(), grid.tier.heroW, grid.tier.heroH, k == 1 && d.phase() != Phase.RESULT);
		List<Box> content = new ArrayList<>();
		content.add(dealer);
		for (SeatColumn c : cols) if (c != null) content.addAll(List.of(c.boxes()));
		Box print = new Box(grid.felt().x(), dealer.bottom() + 6, grid.felt().w(), 10);
		FeltPrint.draw(g, font, print, Component.translatable("gui.burmaldaholic.core.v2.status.chips_add"), 0.55f, content);
		long age = Util.getMillis() - stampAt;
		int[][] anchors = SeatColumn.crescent(grid, 5);
		for (int i = 0; i < cols.length; i++) {
			SeatColumn c = cols[i];
			if (c == null) {
				Box seatBox = Box.centredAt(anchors[i][0], anchors[i][1] - 18, 30, 30);
				FeltPrint.seatBox(g, font, seatBox, seatBox.contains(mx, my));
				continue;
			}
			boolean hero = i == heroSlot;
			int n = hero ? d.heroCards() : i == 0 ? 3 : i == 1 ? 2 : 5;
			int cw = hero ? grid.tier.heroW : grid.tier.seatW;
			int step = n > 1 ? (c.cards().w() - cw) / (n - 1) : 0;
			for (int k = 0; k < n; k++) card(g, c.cards().x() + k * step, c.cards().y(), cw, c.cards().h(), false);
			if (d.phase() == Phase.RESULT) {
				Stamp.drawResolved(g, font, c.outcome(), outcomes[i], levels[i], hero, age);
				long net = switch (outcomes[i]) {
					case WIN -> 25;
					case BLACKJACK -> 75;
					case PUSH -> 0;
					default -> -50;
				};
				ValuePill.draw(g, font, c.value(), V2Draw.net(net), ValuePill.ofNet(net));
			} else {
				TotalPill.draw(g, font, c.outcome(), Texts.number(10 + i * 3), hero && levels[i] == 0 ? 2 : 1);
				ValuePill.draw(g, font, c.value(), Texts.number(hero ? Math.max(d.bet(), 0) : 25 * (i + 1)), hero ? ValuePill.Style.YOU
					: ValuePill.Style.OTHER);
			}
			ChipStack.draw(g, c.stack(), hero ? d.bet() : 25L * (i + 1));
			if (hero && chrome.pending() != null) ChipStack.drawPending(g, c.stack().offset(0, -6), Math.max(1, chrome.pending().amount()));
			SeatPlate.draw(g, font, c.plate(), hero ? Component.translatable("gui.burmaldaholic.common.you") : Texts.raw(NAMES[i]),
				hero ? SeatPlate.Kind.YOU : i == 3 && d.phase() == Phase.ACTING ? SeatPlate.Kind.ACTING : SeatPlate.Kind.OTHER, i == 1);
		}
	}

	/** A card placeholder (the gallery shows layout, not the card art lane A owns). */
	private static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean back) {
		g.fill(x, y, x + w, y + h, CasinoPalette.V2_INK);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, back ? 0xFF3A4AA0 : 0xFFF4ECF8);
		if (!back) {
			g.fill(x + 2, y + 2, x + 5, y + 7, 0xFFD83440);
			g.fill(x + w - 5, y + h - 7, x + w - 2, y + h - 2, 0xFFD83440);
		}
	}
}
