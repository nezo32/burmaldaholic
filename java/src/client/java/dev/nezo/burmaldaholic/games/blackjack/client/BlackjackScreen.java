package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.ClientFx;
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
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.cards.CardLayout;
import dev.nezo.burmaldaholic.core.anim.cards.CardLayout.Blackjack;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Hands;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Blackjack table screen (UI.md §4; docs/design/visual/cards.md §6.3, animation/cards.md §1.2–§1.3; J-C6): the themed
 * half-moon table with the shoe, tray and rack, the dealer's hand (hole card tucked under the up card), the viewer at the
 * centre spot (split hands side by side, 3–4 hands shrink the inactive ones), the other seats around the rail with seat
 * plates (bot avatars + level badges), chip stacks, total badges, BLACKJACK! / BUST stamps, the insurance band and the
 * console (chip rack, Clear / Rebet / Deal; Hit / Stand / Double / Split with icons).
 *
 * <p>Renders the PUBLISHED state only (the server paces the cards: {@code BlackjackBeats}): every card flies from the
 * shoe on its beat, flips on landing, the hole card turns on its flip beat; results, decisions and timers appear at the
 * reveal gate ({@code busy} false). Actions: {@code bet {amount}}, {@code hit|stand|double|split|surrender},
 * {@code insurance {amount}}, {@code even_money {take}}, core's {@code sit|leave}.
 */
public class BlackjackScreen extends CardTableScreen {
	private static final int[] CHIPS = {1, 5, 25, 100, 500};
	private static final int LAVENDER = 0xFFC0B0DC;
	private final CardAnimator cards = new CardAnimator();
	private final CardMotion.Pose pose = new CardMotion.Pose();
	private Model m = new Model();
	private long pendingBet;
	private int selectedChip = 25;
	/** Balance shown in the corner: held while a card is still moving (no spoiler of the settlement). */
	private long heldBalance = -1;
	private long prevBalance = -1;
	private long resultAt = -1;
	private int resultRound = -1;
	private int celebratedRound = -1;
	private final Map<Integer, Long> stampSeen = new HashMap<>();

	public BlackjackScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType().name().endsWith("high_roller")
			? "gui.burmaldaholic.blackjack.title_high_roller" : "gui.burmaldaholic.blackjack.title"), TableTheme.Shape.CRESCENT);
	}

	// ---- model ------------------------------------------------------------------------------------------------------

	/** One hand of the published state. */
	record Hand(int[] codes, int[] ids, int[] at, long bet, boolean doubled, boolean split, boolean done, String outcome, long ret) {
		List<Card> cards() {
			List<Card> out = new ArrayList<>();
			for (int c : codes) if (c >= 0 && c < 52) out.add(Card.fromCode(c));
			return out;
		}

		int total() {
			return Hands.total(cards());
		}

		boolean natural() {
			return codes.length == 2 && !split && total() == 21;
		}

		boolean bust() {
			return total() > 21;
		}
	}

	/** One seat of the round (or of the betting lobby). */
	record Seat(int seat, String name, @Nullable Component nameC, boolean bot, int botLevel, String botName, boolean you, long bet, long insurance,
			boolean settled, long net, long ret, long staked, String tier, List<Hand> hands) {}

	/** The parsed state. */
	static final class Model {
		String phase = "betting";
		boolean busy;
		int roundSeq;
		long roundTick;
		boolean inRound;
		int[] dealer = new int[0];
		int[] dealerIds = new int[0];
		int[] dealerAt = new int[0];
		int holeFlipAt = -1;
		boolean holeHidden;
		int currentSeat = -1;
		int currentHand = -1;
		final List<Seat> seats = new ArrayList<>();
		String offer = "";
		long insuranceMax;
		final List<String> legal = new ArrayList<>();
		boolean betPlaced;
		long lastBet;
		String notice = "";
		int seatCount = 5;
		int mySeat = -1;
	}

	private static Component decode(@Nullable Tag tag) {
		if (tag == null) return Component.empty();
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	private Model parse(CompoundTag s) {
		Model n = new Model();
		n.phase = s.getStringOr("phase", "betting");
		n.busy = s.getBooleanOr("busy", false);
		n.roundSeq = s.getIntOr("round_seq", 0);
		n.roundTick = s.getLongOr("round_tick", 0);
		n.inRound = !s.getListOrEmpty("players").isEmpty();
		n.dealer = s.getIntArray("dealer").orElse(new int[0]);
		n.dealerIds = s.getIntArray("dealer_ids").orElse(new int[n.dealer.length]);
		n.dealerAt = s.getIntArray("dealer_at").orElse(new int[n.dealer.length]);
		n.holeFlipAt = s.getIntOr("hole_flip_at", -1);
		n.holeHidden = s.getBooleanOr("hole_hidden", false);
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
		ListTag players = s.getListOrEmpty("players");
		for (int i = 0; i < players.size(); i++) {
			CompoundTag p = players.getCompoundOrEmpty(i);
			List<Hand> hands = new ArrayList<>();
			ListTag hs = p.getListOrEmpty("hands");
			for (int h = 0; h < hs.size(); h++) {
				CompoundTag ht = hs.getCompoundOrEmpty(h);
				int[] codes = ht.getIntArray("cards").orElse(new int[0]);
				hands.add(new Hand(codes, ht.getIntArray("cards_ids").orElse(new int[codes.length]), ht.getIntArray("cards_at").orElse(new int[codes.length]),
					ht.getLongOr("bet", 0), ht.getBooleanOr("doubled", false), ht.getBooleanOr("split", false), ht.getBooleanOr("done", false),
					ht.getStringOr("outcome", ""), ht.getLongOr("ret", 0)));
			}
			boolean bot = p.getBooleanOr("bot", false);
			n.seats.add(new Seat(p.getIntOr("seat", -1), p.getStringOr("name", ""), bot ? decode(p.get("name_c")) : null, bot, p.getIntOr("bot_level", 0),
				p.getStringOr("bot_name", ""), p.getBooleanOr("you", false), p.getLongOr("bet", 0), p.getLongOr("insurance", 0),
				p.getBooleanOr("settled", false), p.getLongOr("net", 0), p.getLongOr("ret", 0), p.getLongOr("staked", 0), p.getStringOr("tier", ""), hands));
		}
		if (!n.inRound) {
			// the betting lobby: seated humans with their bets, bots with their virtual bets
			Map<Integer, Long> bets = new HashMap<>();
			ListTag bl = s.getListOrEmpty("bets");
			for (int i = 0; i < bl.size(); i++) bets.put(bl.getCompoundOrEmpty(i).getIntOr("seat", -1), bl.getCompoundOrEmpty(i).getLongOr("amount", 0));
			ListTag seats = s.getListOrEmpty("seats");
			for (int i = 0; i < seats.size(); i++) {
				CompoundTag st = seats.getCompoundOrEmpty(i);
				int idx = st.getIntOr("index", -1);
				n.seats.add(new Seat(idx, st.getStringOr("name", ""), null, false, 0, "", st.getBooleanOr("you", false), bets.getOrDefault(idx, 0L), 0, false,
					0, 0, 0, "", List.of()));
			}
			ListTag bots = s.getListOrEmpty("bot_seats");
			for (int i = 0; i < bots.size(); i++) {
				CompoundTag bt = bots.getCompoundOrEmpty(i);
				n.seats.add(new Seat(bt.getIntOr("seat", -1), "", decode(bt.get("name_c")), true, bt.getIntOr("bot_level", 0), bt.getStringOr("bot_name", ""), false,
					bt.getLongOr("amount", 0), 0, false, 0, 0, 0, "", List.of()));
			}
		}
		return n;
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		m = parse(newState);
		// the corner balance holds the pre-settlement value while a card is still moving and until the payout chips
		// reached it (cards.md §0.7.5); the HUD gets the same hold
		if (m.busy) {
			if (heldBalance < 0) heldBalance = prevBalance >= 0 ? prevBalance : balance();
			if (me() != null) ClientFx.balanceHold.accept(1500);
		}
		Seat me = me();
		if (me != null && me.settled() && resultRound != m.roundSeq) {
			resultRound = m.roundSeq;
			resultAt = Util.getMillis();
		} else if (!m.busy && (me == null || !me.settled())) {
			heldBalance = -1;
		}
		prevBalance = balance();
		if (!"betting".equals(m.phase) || m.betPlaced) pendingBet = 0;
		if (minecraft != null) rebuildConsole();
	}

	@Override
	protected long shownBalance() {
		if (heldBalance >= 0 && (m.busy || resultAt >= 0 && Util.getMillis() - resultAt < 1300)) return heldBalance;
		return balance();
	}

	private @Nullable Seat me() {
		for (Seat s : m.seats) if (s.you()) return s;
		return null;
	}

	private boolean myTurn() {
		Seat me = me();
		return me != null && "turns".equals(m.phase) && !m.busy && me.seat() == m.currentSeat;
	}

	/** Seat index drawn at the centre spot (the viewer, or the middle seat for a spectator). */
	private int centreSeat() {
		return m.mySeat >= 0 ? m.mySeat : m.seatCount / 2;
	}

	/** Position 0..3 (far left … far right) of another seat around the centre, keeping the table order. */
	private int positionOf(int seat) {
		int centre = centreSeat();
		int pos = 0;
		for (int i = 0; i < m.seatCount; i++) {
			if (i == centre) continue;
			if (i == seat) return pos;
			pos++;
		}
		return pos;
	}

	// ---- console ----------------------------------------------------------------------------------------------------

	@Override
	protected Component plaqueTitle() {
		long max = maxBet();
		if (max <= 0 || compact) return title;
		return Component.translatable("gui.burmaldaholic.cards.plaque", title, Texts.number(minBet()), Texts.number(max));
	}

	private long maxAffordable() {
		return Math.max(0, Math.min(maxBet(), balance()));
	}

	@Override
	protected void buildConsole() {
		List<CardButton> row = new ArrayList<>();
		boolean seated = mySeat() >= 0;
		boolean betting = ("betting".equals(m.phase) || "idle".equals(m.phase)) && !m.inRound;
		int minX = compact ? 4 : 140;
		if (!seated) {
			row.add(button(Component.translatable("gui.burmaldaholic.blackjack.sit"), "play", CardButton.Family.PRIMARY, true, () -> sendAction("sit")));
		} else if (betting && !m.betPlaced) {
			long max = maxAffordable();
			minX = compact ? 4 : 8 + CHIPS.length * 25 + 6;
			row.add(button(Component.translatable("gui.burmaldaholic.common.clear"), "clear", CardButton.Family.TABLE, pendingBet > 0, () -> {
				pendingBet = 0;
				rebuildConsole();
			}));
			long last = m.lastBet;
			row.add(button(Component.translatable("gui.burmaldaholic.common.rebet"), "rebet", CardButton.Family.TABLE, last > 0 && last <= max, () -> {
				pendingBet = last;
				rebuildConsole();
			}));
			row.add(button(Component.translatable("gui.burmaldaholic.common.deal"), "deal", CardButton.Family.PRIMARY,
				pendingBet >= minBet() && pendingBet <= max, this::deal));
			if (!compact) {
				row.addFirst(button(Component.translatable("gui.burmaldaholic.common.leave"), "leave", CardButton.Family.TABLE, true, () -> sendAction("leave")));
			}
		} else if ("insurance".equals(m.offer)) {
			long amount = m.insuranceMax;
			row.add(button(Component.translatable("gui.burmaldaholic.blackjack.insure", Texts.number(amount)), "insurance", CardButton.Family.PRIMARY, true,
				() -> insure(amount)));
			row.add(button(Component.translatable("gui.burmaldaholic.blackjack.no_insurance"), "clear", CardButton.Family.TABLE, true, () -> insure(0)));
		} else if ("even_money".equals(m.offer)) {
			row.add(button(Component.translatable("gui.burmaldaholic.blackjack.even_money"), "insurance", CardButton.Family.PRIMARY, true, () -> evenMoney(true)));
			row.add(button(Component.translatable("gui.burmaldaholic.blackjack.decline_even_money"), "clear", CardButton.Family.TABLE, true,
				() -> evenMoney(false)));
		} else if (me() != null && myHandsOpen()) {
			boolean turn = myTurn();
			for (String a : new String[] {"hit", "stand", "double", "split"}) {
				boolean ok = turn && m.legal.contains(a);
				CardButton b = button(Component.translatable("gui.burmaldaholic.blackjack." + a), a,
					"stand".equals(a) ? CardButton.Family.PRIMARY : CardButton.Family.TABLE, ok, () -> sendAction(a));
				b.hint(Component.translatable("gui.burmaldaholic.blackjack." + a + ".tooltip"));
				row.add(b);
			}
			if (turn && m.legal.contains("surrender")) {
				row.add(button(Component.translatable("gui.burmaldaholic.blackjack.surrender"), "surrender", CardButton.Family.DANGER, true,
					() -> sendAction("surrender")));
			}
		} else if (!m.inRound || "result".equals(m.phase)) {
			row.add(button(Component.translatable("gui.burmaldaholic.common.leave"), "leave", CardButton.Family.TABLE, !m.inRound || !m.busy,
				() -> sendAction("leave")));
		}
		layoutButtons(row, minX);
	}

	private boolean myHandsOpen() {
		Seat me = me();
		if (me == null || me.settled()) return false;
		if (!"turns".equals(m.phase) && !"insurance".equals(m.phase)) return false;
		for (Hand h : me.hands()) if (!h.done()) return true;
		return false;
	}

	private void deal() {
		CompoundTag args = new CompoundTag();
		args.putLong("amount", pendingBet);
		sendAction("bet", args);
		FxSounds.play("chip_place", 1f);
	}

	private void insure(long amount) {
		CompoundTag args = new CompoundTag();
		args.putLong("amount", amount);
		sendAction("insurance", args);
	}

	private void evenMoney(boolean take) {
		CompoundTag args = new CompoundTag();
		args.putBoolean("take", take);
		sendAction("even_money", args);
	}

	private boolean chipRackShown() {
		return !compact && mySeat() >= 0 && ("betting".equals(m.phase) || "idle".equals(m.phase)) && !m.inRound && !m.betPlaced;
	}

	@Override
	protected boolean onCanvasClick(double x, double y, int button) {
		if (!chipRackShown() || button != 0) return false;
		for (int i = 0; i < CHIPS.length; i++) {
			int cx = 8 + i * 25;
			if (x >= cx && x < cx + 22 && y >= 212 && y < 234) {
				selectedChip = CHIPS[i];
				long max = maxAffordable();
				if (pendingBet + CHIPS[i] > max) {
					FxSounds.play("ui_deny", 1f);
				} else {
					pendingBet += CHIPS[i];
					FxSounds.play("chip_place", 1f);
				}
				rebuildConsole();
				return true;
			}
		}
		return false;
	}

	@Override
	protected void drawConsoleText(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (chipRackShown()) {
			long max = maxAffordable();
			for (int i = 0; i < CHIPS.length; i++) {
				int x = 8 + i * 25;
				boolean hover = mouseX >= x && mouseX < x + 22 && mouseY >= 212 && mouseY < 234;
				ChipStackView.big(g, CHIPS[i], x, 212, CHIPS[i] == selectedChip, hover, pendingBet + CHIPS[i] <= max);
			}
			return;
		}
		int right = consoleTextRight();
		if (compact) {
			Seat me = me();
			if (me != null && me.hands().size() > 1 && m.currentSeat == me.seat()) {
				Component h = Component.translatable("gui.burmaldaholic.blackjack.hand", Texts.number(m.currentHand + 1));
				CardGfx.text(g, font, h, Math.max(right + 12, canvasW() - 4 - font.width(h)), 145, CasinoPalette.GOLD, true);
			}
			return;
		}
		Component l1 = statusLine();
		Component l2 = subLine();
		if (l1 != null) CardGfx.fitted(g, font, l1, 8, 212, right - 8, statusColor(), true);
		if (l2 != null) CardGfx.fitted(g, font, l2, 8, 224, right - 8, subColor(), true);
	}

	private int statusColor() {
		Seat me = me();
		if (me != null && me.settled() && !m.busy) return me.net() > 0 ? CasinoPalette.BONUS : me.net() < 0 ? CasinoPalette.CHIP_RED_LIGHT : LAVENDER;
		return CasinoPalette.GOLD;
	}

	private int subColor() {
		return LAVENDER;
	}

	private @Nullable Component statusLine() {
		if (!m.notice.isEmpty()) return Component.translatable(m.notice);
		if ("insurance".equals(m.offer)) return Component.translatable("gui.burmaldaholic.blackjack.insurance_prompt");
		if ("even_money".equals(m.offer)) return Component.translatable("gui.burmaldaholic.blackjack.even_money_prompt");
		if (m.busy) return Component.translatable("gui.burmaldaholic.blackjack.dealing");
		Seat me = me();
		switch (m.phase) {
			case "turns" -> {
				if (myTurn()) {
					return me.hands().size() > 1 ? Component.translatable("gui.burmaldaholic.blackjack.your_turn_hand", Texts.number(m.currentHand + 1),
						Texts.number(me.hands().size())) : Component.translatable("gui.burmaldaholic.blackjack.your_turn");
				}
				for (Seat s : m.seats) {
					if (s.seat() == m.currentSeat) return Component.translatable("gui.burmaldaholic.blackjack.turn_of", seatName(s));
				}
				return Component.translatable("gui.burmaldaholic.common.waiting_players");
			}
			case "insurance" -> {
				return Component.translatable("gui.burmaldaholic.common.waiting_players");
			}
			case "result" -> {
				if (me == null || !me.settled()) return null;
				long net = me.net();
				return net > 0 ? Component.translatable("gui.burmaldaholic.common.result.win", Texts.number(net))
					: net < 0 ? Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(-net))
					: Component.translatable("gui.burmaldaholic.common.result.push");
			}
			default -> {
				if (mySeat() < 0) return Component.translatable("gui.burmaldaholic.blackjack.betting_open");
				if (m.betPlaced) {
					long sec = timerSeconds("bet");
					return sec < 0 ? Component.translatable("gui.burmaldaholic.common.waiting_players")
						: Component.translatable("msg.burmaldaholic.blackjack.round_starts_in", Texts.plural("unit.burmaldaholic.second_acc", sec));
				}
				return Component.translatable("gui.burmaldaholic.blackjack.betting_open");
			}
		}
	}

	private @Nullable Component subLine() {
		Seat me = me();
		if ("result".equals(m.phase) && !m.busy) {
			long sec = timerSeconds("result");
			return sec < 0 ? null : Component.translatable("gui.burmaldaholic.blackjack.next_round", Texts.plural("unit.burmaldaholic.second_acc", sec));
		}
		if (me != null && m.inRound) {
			long base = me.bet();
			long extra = 0;
			for (Hand h : me.hands()) extra += h.bet();
			extra -= base;
			boolean doubled = me.hands().stream().anyMatch(Hand::doubled);
			if (extra > 0 && doubled) return Component.translatable("gui.burmaldaholic.blackjack.bet_doubled", Texts.number(base), Texts.number(extra));
			long sec = myTurn() ? timerSeconds("turn") : -1;
			if (sec >= 0) {
				return Component.translatable("gui.burmaldaholic.cards.join", Component.translatable("gui.burmaldaholic.blackjack.bet_total",
					Texts.number(base + Math.max(0, extra))), Component.translatable("gui.burmaldaholic.common.auto_action",
					Component.translatable("gui.burmaldaholic.blackjack.stand"), Texts.plural("unit.burmaldaholic.second_acc", sec)));
			}
			return Component.translatable("gui.burmaldaholic.blackjack.bet_total", Texts.number(base + Math.max(0, extra)));
		}
		if (mySeat() >= 0 && !m.inRound && !m.betPlaced) {
			return pendingBet > 0 ? Component.translatable("gui.burmaldaholic.blackjack.bet_total", Texts.number(pendingBet)) : limitsLine();
		}
		return limitsLine();
	}

	private Component seatName(Seat s) {
		if (s.bot() && !s.botName().isEmpty()) return Component.translatable(s.botName());
		if (s.bot() && s.nameC() != null) return s.nameC();
		return s.you() ? Component.translatable("gui.burmaldaholic.common.you") : Texts.raw(s.name());
	}

	// ---- scene --------------------------------------------------------------------------------------------------------

	private int tx(int x) {
		return tableX() + x;
	}

	private int ty(int y) {
		return tableY() + y;
	}

	private int backRow() {
		return theme.back(CardSprites.BACK_NAVY);
	}

	private double beatMs(int at) {
		return (m.roundTick + at) * 50.0;
	}

	private int key(int id) {
		return Math.floorMod(m.roundSeq, 1000) * 1000 + id;
	}

	@Override
	protected void drawScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		double now = nowMs();
		boolean reduced = reduced();
		int back = backRow();
		// props (tray, shoe) and prints
		int trayX = compact ? tx(4) : tx(Blackjack.TRAY_X);
		int trayY = compact ? ty(4) : ty(Blackjack.TRAY_Y);
		CardGfx.sprite(g, theme.themed("prop/tray"), trayX, trayY, 30, 22, 0xFFFFFFFF);
		CardGfx.tex(g, CardGfx.spriteFile("cards/prop/tray_fill"), trayX + 3, trayY + 7, 24, 6, 0, 6, 24,
			6, 24, 12, 0xFFFFFFFF);
		int shoeX = compact ? tx(Blackjack.C_SHOE_X) : tx(Blackjack.SHOE_X);
		int shoeY = compact ? ty(Blackjack.C_SHOE_Y) : ty(Blackjack.SHOE_Y);
		CardGfx.sprite(g, theme.themed("prop/shoe"), shoeX, shoeY, 40, 28, 0xFFFFFFFF);
		cards.origin(shoeX + 2, shoeY + 16).tray(trayX + 3, trayY + 1);
		int print = theme.print;
		if (compact) {
			CardGfx.centered(g, font, Component.translatable("gui.burmaldaholic.blackjack.print.pays"), tx(Blackjack.C_RULES_CX), ty(Blackjack.C_RULES_Y), print,
				false);
		} else {
			CardGfx.centered(g, font, Component.translatable("gui.burmaldaholic.blackjack.print.pays"), tx(Blackjack.RULES_CX), ty(Blackjack.RULES_Y), print, false);
			CardGfx.sprite(g, FxSprites.sprite("cards/print/insurance"), tx(Blackjack.INSURANCE_X), ty(Blackjack.INSURANCE_Y), 232, 30, theme.printArgb(0.55));
			CardGfx.centered(g, font, Component.translatable("gui.burmaldaholic.blackjack.print.insurance"), tx(Blackjack.INSURANCE_CX), ty(Blackjack.INSURANCE_TY),
				print, false);
		}
		// spots
		if (!compact) {
			for (int pos = 0; pos < 4; pos++) {
				int[] s = Blackjack.spot(pos, false);
				CardGfx.sprite(g, FxSprites.sprite("cards/print/spot"), tx(s[0] - 14), ty(s[1] - 14), 28, 28, theme.printArgb(0.55));
			}
			if (myTurn() || (me() != null && winning(me()))) {
				CardGfx.sprite(g, FxSprites.sprite("cards/fx/spot_glow"), tx(Blackjack.ME_SPOT_X - 17), ty(Blackjack.ME_SPOT_Y - 17), 34, 34, CardGfx.white(0.8));
			}
			CardGfx.sprite(g, FxSprites.sprite("cards/print/spot"), tx(Blackjack.ME_SPOT_X - 14), ty(Blackjack.ME_SPOT_Y - 14), 28, 28, theme.printArgb(0.55));
		}
		// cards (published state only)
		cards.begin(now, reduced);
		drawDealerCards(back);
		for (Seat s : m.seats) {
			if (s.hands().isEmpty()) continue;
			if (s.seat() == centreSeat()) drawCentreCards(s, back);
			else drawOtherCards(s, back);
		}
		cards.end();
		cards.draw(g, font);
		// chips, totals, stamps
		drawChips(g, now);
		drawTotals(g);
		// the dealer rack sits on the dealer edge, over the top of the dealer's cards
		if (compact) {
			CardGfx.tex(g, CardGfx.spriteFile("cards/prop/rack_" + theme.id), tx(Blackjack.C_RACK_X), ty(-1),
				80, 10, 0, 0, 80, 10, 80, 14, 0xFFFFFFFF);
		} else {
			CardGfx.sprite(g, theme.themed("prop/rack"), tx(Blackjack.RACK_X), ty(Blackjack.RACK_Y), 80, 14, 0xFFFFFFFF);
		}
		drawPlates(g);
		drawStamps(g, reduced);
		maybeCelebrate();
	}

	private boolean winning(Seat s) {
		return s.settled() && !m.busy && s.net() > 0;
	}

	private void drawDealerCards(int back) {
		int n = m.dealer.length;
		// hole card first: it is tucked UNDER the up card
		int[] order = n >= 2 ? concat(new int[] {1, 0}, n) : new int[] {0};
		boolean dealerBust = dealerTotal() > 21;
		for (int i : order) {
			if (i >= n) continue;
			int x = tx(Blackjack.dealerX(i, compact));
			int y = ty(Blackjack.dealerY(i, compact));
			double reveal = i == 1 ? (m.holeFlipAt >= 0 ? beatMs(m.holeFlipAt) : Double.NaN) : Double.NaN;
			CardAnimator.Slot s = cards.card(key(m.dealerIds[i]), m.dealer[i], compact ? CardSprites.M : CardSprites.L, back, x, y, 0, beatMs(m.dealerAt[i]),
				reveal);
			if (dealerBust && !m.busy) cards.dim(s, true);
		}
	}

	private static int[] concat(int[] head, int n) {
		int[] out = new int[Math.max(head.length, n)];
		System.arraycopy(head, 0, out, 0, head.length);
		for (int i = head.length; i < n; i++) out[i] = i;
		return out;
	}

	private int dealerTotal() {
		List<Card> cs = new ArrayList<>();
		for (int i = 0; i < m.dealer.length; i++) {
			if (m.dealer[i] >= 0 && cards.faceShown(key(m.dealerIds[i]))) cs.add(Card.fromCode(m.dealer[i]));
		}
		return cs.isEmpty() ? 0 : Hands.total(cs);
	}

	private boolean handLost(Seat s, Hand h) {
		if (h.bust()) return true;
		return !m.busy && s.settled() && ("lose".equals(h.outcome()) || "dealer_blackjack".equals(h.outcome()) || "surrender".equals(h.outcome()));
	}

	private void drawCentreCards(Seat s, int back) {
		int n = s.hands().size();
		int active = s.seat() == m.currentSeat ? Math.max(0, m.currentHand) : Math.max(0, n - 1);
		for (int h = 0; h < n; h++) {
			Hand hand = s.hands().get(h);
			int size = Blackjack.handSize(h, n, active, compact);
			int sz = compact ? CardSprites.M : size == 0 ? CardSprites.L : CardSprites.M;
			int step = sz == CardSprites.L ? Blackjack.ME_STEP : Blackjack.ME_STEP_M;
			int x0 = tx(Blackjack.handX(h, n, active, Math.min(2, hand.codes().length), compact));
			int y0 = ty(Blackjack.handY(size, compact));
			boolean glow = s.seat() == m.currentSeat && h == m.currentHand && !m.busy || winning(s) && handWon(hand);
			for (int c = 0; c < hand.codes().length; c++) {
				boolean side = hand.doubled() && c == 2;
				int x = side ? x0 + (sz == CardSprites.L ? 24 : 14) : x0 + c * step;
				int y = side ? y0 + (sz == CardSprites.L ? 20 : 12) : y0;
				CardAnimator.Slot slot = cards.card(key(hand.ids()[c]), hand.codes()[c], sz, back, x, y, side ? 90 : 0, beatMs(hand.at()[c]), Double.NaN);
				if (glow && c == 0) cards.glow(slot, true);
				if (handLost(s, hand)) cards.dim(slot, true);
			}
		}
	}

	private static boolean handWon(Hand h) {
		return "win".equals(h.outcome()) || "blackjack".equals(h.outcome()) || "dealer_bust".equals(h.outcome()) || "even_money".equals(h.outcome());
	}

	private int[] otherCardsXY(Seat s, Hand hand) {
		int[] spot = Blackjack.spot(positionOf(s.seat()), compact);
		int w = compact ? CardLayout.handWidth(hand.codes().length, CardLayout.S_W, 7) : CardLayout.handWidth(hand.codes().length, CardLayout.M_W,
			Blackjack.OTHER_STEP);
		return new int[] {tx(spot[0] - w / 2), ty(spot[1] - (compact ? 20 : 48)), w};
	}

	private void drawOtherCards(Seat s, int back) {
		Hand hand = s.hands().getFirst();
		int[] xy = otherCardsXY(s, hand);
		int sz = compact ? CardSprites.S : CardSprites.M;
		int step = compact ? 7 : Blackjack.OTHER_STEP;
		for (int c = 0; c < hand.codes().length; c++) {
			CardAnimator.Slot slot = cards.card(key(hand.ids()[c]), hand.codes()[c], sz, back, xy[0] + c * step, xy[1], 0, beatMs(hand.at()[c]), Double.NaN);
			if (handLost(s, hand)) cards.dim(slot, true);
			if (s.seat() == m.currentSeat && !m.busy) cards.glow(slot, c == 0);
		}
		// further split hands of other seats are not drawn separately: their cards count in the badge
	}

	private Component badge(Hand h) {
		List<Card> cs = new ArrayList<>();
		for (int c = 0; c < h.codes().length; c++) if (h.codes()[c] >= 0 && cards.faceShown(key(h.ids()[c]))) cs.add(Card.fromCode(h.codes()[c]));
		if (cs.isEmpty()) return null;
		if (cs.size() == 2 && !h.split() && Hands.total(cs) == 21) return Component.translatable("gui.burmaldaholic.blackjack.badge.bj");
		int[] t = Hands.displayTotals(cs);
		return t.length == 2 ? Texts.raw(t[0] + "/" + t[1]) : Texts.number(t[0]); // literal-ok: soft total digits
	}

	private void drawTotals(GuiGraphicsExtractor g) {
		// dealer
		int dt = dealerTotal();
		if (dt > 0) {
			Component t = Texts.number(dt);
			int x = compact ? tx(Blackjack.C_BADGE_X) : tx(Blackjack.DEALER_X - 24);
			int y = compact ? ty(Blackjack.C_BADGE_Y) : ty(Blackjack.DEALER_Y + 4);
			ActionTag.badge(g, font, t, x, y, false, 1);
		}
		for (Seat s : m.seats) {
			if (s.hands().isEmpty()) continue;
			if (s.seat() == centreSeat()) {
				int n = s.hands().size();
				int active = s.seat() == m.currentSeat ? Math.max(0, m.currentHand) : Math.max(0, n - 1);
				for (int h = 0; h < n; h++) {
					Hand hand = s.hands().get(h);
					Component t = badge(hand);
					if (t == null) continue;
					int size = Blackjack.handSize(h, n, active, compact);
					int x = tx(Blackjack.handX(h, n, active, Math.min(2, hand.codes().length), compact));
					int y = ty(Blackjack.handY(size, compact)) - 12;
					boolean gold = hand.total() == 21 || (s.seat() == m.currentSeat && h == m.currentHand && !m.busy);
					int shake = hand.bust() ? CardMotion.bustShakePx((Util.getMillis() - stampTime(hand)) / (double) CardMotion.BUST_SHAKE_MS) : 0;
					ActionTag.badge(g, font, t, x + shake, y, gold, 1);
				}
			} else {
				Hand hand = s.hands().getFirst();
				Component t = badge(hand);
				if (t == null) continue;
				int[] xy = otherCardsXY(s, hand);
				ActionTag.badge(g, font, t, xy[0] - 4, xy[1] - 11, hand.total() == 21, 1);
			}
		}
	}

	private long stampTime(Hand h) {
		int k = h.ids().length == 0 ? -1 : key(h.ids()[h.ids().length - 1]);
		return stampSeen.computeIfAbsent(k, x -> Util.getMillis());
	}

	private void drawChips(GuiGraphicsExtractor g, double now) {
		long local = Util.getMillis();
		boolean results = resultAt >= 0 && resultRound == m.roundSeq && !m.busy;
		long age = results ? local - resultAt : -1;
		if (!m.inRound) {
			// betting: the viewer's pending bet on the centre spot, others' bets on their spots
			for (Seat s : m.seats) {
				long amount = s.you() && !m.betPlaced ? pendingBet : s.bet();
				if (amount <= 0) continue;
				if (s.seat() == centreSeat()) {
					int x = tx(compact ? 140 : Blackjack.ME_SPOT_X);
					int y = ty(compact ? Blackjack.C_STACK_Y : Blackjack.ME_STACK_Y);
					ChipStackView.stack(g, x, y, amount, 5, 0, s.bot(), 1);
					ChipStackView.label(g, font, x, y, amount, CasinoPalette.BONE, 1);
				} else {
					int[] sp = Blackjack.spot(positionOf(s.seat()), compact);
					ChipStackView.stack(g, tx(sp[0]), ty(sp[1] + (compact ? 8 : 4)), amount, 5, 0, s.bot(), 1);
				}
			}
			return;
		}
		int rackX = tx((compact ? Blackjack.C_RACK_X : Blackjack.RACK_X) + 40);
		int rackY = ty(4);
		int hudX = canvasW() - 30;
		int hudY = 8;
		for (Seat s : m.seats) {
			if (s.hands().isEmpty()) continue;
			boolean centre = s.seat() == centreSeat();
			int n = s.hands().size();
			for (int h = 0; h < (centre ? n : 1); h++) {
				Hand hand = s.hands().get(h);
				long bet = centre ? hand.bet() : s.hands().stream().mapToLong(Hand::bet).sum();
				int x;
				int y;
				if (centre) {
					x = tx(Blackjack.stackX(h, n, compact));
					y = ty(compact ? Blackjack.C_STACK_Y : Blackjack.ME_STACK_Y);
				} else {
					int[] sp = Blackjack.spot(positionOf(s.seat()), compact);
					x = tx(sp[0]);
					y = ty(sp[1] + (compact ? 8 : 4));
				}
				String o = hand.outcome();
				boolean bustNow = hand.bust();
				int delay = h * 150;
				if (bustNow) {
					// bust: the stack is swept to the rack at once (260 ms)
					double t = CardMotion.progress(local, stampTime(hand) + 520, CardMotion.SWEEP_MS);
					if (t < 1) ChipStackView.flying(g, pose, x, y, rackX, rackY, t, true, bet, 0, s.bot(), reduced());
					continue;
				}
				if (!results || o.isEmpty()) {
					ChipStackView.stack(g, x, y, bet, 5, 0, s.bot(), 1);
					if (centre && !compact) ChipStackView.label(g, font, x, y, bet, CasinoPalette.BONE, 1);
					continue;
				}
				int[] target = centre ? new int[] {hudX, hudY} : plateCentre(s);
				if (handWon(hand) || "push".equals(o) || "surrender".equals(o) && hand.ret() > 0) {
					long pay = Math.max(0, hand.ret() - bet);
					long flyAt = delay + 900;
					if (age < flyAt) {
						int wig = "push".equals(o) ? CardMotion.wigglePx((age - delay) / (double) CardMotion.PUSH_WIGGLE_MS) : 0;
						ChipStackView.stack(g, x + wig, y, bet, 5, 0, s.bot(), 1);
						if (pay > 0 && age >= delay) {
							double p = CardMotion.progress(age, delay, CardMotion.CHIP_FLIGHT_MS);
							ChipStackView.flying(g, pose, rackX, rackY, x + 14, y + 2, p, false, pay, 0, s.bot(), reduced());
							if (centre && !compact) ChipStackView.label(g, font, x + 14, y + 2, pay, CasinoPalette.BONUS, 1);
						}
					} else {
						double t = CardMotion.progress(age, flyAt, CardMotion.TO_BALANCE_MS);
						if (t < 1) ChipStackView.flying(g, pose, x, y, target[0], target[1], t, false, hand.ret(), 0, s.bot(), reduced());
					}
				} else {
					double t = CardMotion.progress(age, delay, CardMotion.SWEEP_MS);
					if (t < 1) ChipStackView.flying(g, pose, x, y, rackX, rackY, t, true, bet, 0, s.bot(), reduced());
				}
			}
		}
	}

	private int[] plateCentre(Seat s) {
		int[] p = Blackjack.plate(positionOf(s.seat()));
		return new int[] {tx(p[0] + 20), ty(p[1] + 11)};
	}

	private void drawPlates(GuiGraphicsExtractor g) {
		if (compact) return; // compact: avatars collapse into the cards' badges (§6.7)
		long local = Util.getMillis();
		boolean results = resultAt >= 0 && resultRound == m.roundSeq && !m.busy;
		for (Seat s : m.seats) {
			if (s.seat() == centreSeat() && s.you()) continue;
			if (s.seat() == centreSeat()) continue;
			int pos = positionOf(s.seat());
			int[] p = Blackjack.plate(pos);
			boolean bust = !s.hands().isEmpty() && s.hands().getFirst().bust();
			boolean lost = bust || results && s.settled() && s.net() < 0 && !s.hands().isEmpty();
			boolean won = results && s.settled() && s.net() > 0;
			boolean thinking = s.bot() && s.seat() == m.currentSeat && "turns".equals(m.phase) && !m.busy;
			SeatPlate.State st = lost ? SeatPlate.State.FOLDED : won ? SeatPlate.State.WINNER
				: s.seat() == m.currentSeat && !m.busy ? SeatPlate.State.ACTIVE : SeatPlate.State.NORMAL;
			Component sub = bust ? Component.translatable("gui.burmaldaholic.blackjack.sub.bust")
				: s.bet() > 0 ? Texts.number(s.hands().isEmpty() ? s.bet() : s.hands().stream().mapToLong(Hand::bet).sum())
				: Component.translatable("gui.burmaldaholic.blackjack.no_bet");
			Component name = s.bot() ? (s.botName().isEmpty() ? Texts.raw("?") : Component.translatable(s.botName())) : Texts.raw(s.name()); // literal-ok
			String avatar = s.bot() ? theme.botAvatar(s.botName()) : null;
			SeatPlate.Info info = new SeatPlate.Info(name, s.name(), avatar, s.bot() ? Math.max(1, s.botLevel()) : 0, sub, CasinoPalette.GOLD, st, thinking);
			SeatPlate.draw(g, font, info, tx(p[0]), ty(p[1]), 1, 0);
			if (s.bot() && results && s.settled()) {
				int w = SeatPlate.width(font, info);
				SeatPlate.emote(g, s.net() >= 0, tx(p[0]) + w - 6, ty(p[1]), local - resultAt - 300);
			}
		}
	}

	private void drawStamps(GuiGraphicsExtractor g, boolean reduced) {
		long local = Util.getMillis();
		for (Seat s : m.seats) {
			if (s.hands().isEmpty()) continue;
			boolean centre = s.seat() == centreSeat();
			int n = s.hands().size();
			int active = s.seat() == m.currentSeat ? Math.max(0, m.currentHand) : Math.max(0, n - 1);
			for (int h = 0; h < (centre ? n : 1); h++) {
				Hand hand = s.hands().get(h);
				if (!allShown(hand)) continue;
				boolean nat = hand.natural();
				boolean bust = hand.bust();
				boolean push = !m.busy && s.settled() && "push".equals(hand.outcome()) && centre;
				if (!nat && !bust && !push) continue;
				double cx;
				double cy;
				if (centre) {
					int size = Blackjack.handSize(h, n, active, compact);
					int sz = compact ? CardSprites.M : size == 0 ? CardSprites.L : CardSprites.M;
					int x0 = tx(Blackjack.handX(h, n, active, Math.min(2, hand.codes().length), compact));
					cx = x0 + (CardLayout.handWidth(Math.min(2, hand.codes().length), CardSprites.w(sz), sz == CardSprites.L ? 14 : 9)) / 2.0;
					cy = ty(Blackjack.handY(size, compact)) + CardSprites.h(sz) / 2.0;
				} else {
					int[] xy = otherCardsXY(s, hand);
					cx = xy[0] + xy[2] / 2.0;
					cy = xy[1] + (compact ? 9 : 14);
				}
				long t0 = stampTime(hand);
				if (nat && local - t0 < 30 && local - t0 >= 0 && centre) FxSounds.play("card_sting", 1f);
				Component text = nat ? Component.translatable("gui.burmaldaholic.blackjack.fx.blackjack") : bust ? Component.translatable(
					"gui.burmaldaholic.blackjack.fx.bust") : Component.translatable("gui.burmaldaholic.cards.stamp.push");
				TableStamp.Kind kind = nat ? TableStamp.Kind.GOLD : bust ? TableStamp.Kind.RED : TableStamp.Kind.GREEN;
				TableStamp.draw(g, font, text, kind, cx, cy, nat ? -6 : 6, local - t0, reduced, pose);
			}
		}
		// dealer blackjack / dealer busts
		if (m.dealer.length >= 2 && !m.holeHidden && cards.faceShown(key(m.dealerIds[1]))) {
			int dt = dealerTotal();
			boolean dealerBj = m.dealer.length == 2 && dt == 21;
			if (dealerBj || dt > 21) {
				long t0 = stampSeen.computeIfAbsent(-2 - m.roundSeq, x -> Util.getMillis());
				double cx = tx(Blackjack.dealerX(0, compact)) + (compact ? 20 : 30);
				double cy = ty(Blackjack.dealerY(0, compact)) + (compact ? 14 : 24);
				Component text = dealerBj ? Component.translatable("gui.burmaldaholic.blackjack.fx.dealer_blackjack")
					: Component.translatable("gui.burmaldaholic.blackjack.fx.bust");
				TableStamp.draw(g, font, text, TableStamp.Kind.RED, cx, cy, 6, local - t0, reduced, pose);
			}
		}
	}

	private boolean allShown(Hand h) {
		if (h.codes().length == 0) return false;
		for (int id : h.ids()) if (!cards.faceShown(key(id))) return false;
		return true;
	}

	/** The viewer's tier celebration (global kit), after the payout chips reached the balance (cards.md §0.5). */
	private void maybeCelebrate() {
		Seat me = me();
		if (me == null || !me.settled() || m.busy || celebratedRound == m.roundSeq || resultAt < 0) return;
		if (Util.getMillis() - resultAt < 1300) return;
		celebratedRound = m.roundSeq;
		WinTier tier;
		try {
			tier = WinTier.valueOf(me.tier());
		} catch (IllegalArgumentException e) {
			return;
		}
		if (tier.isWin() || tier == WinTier.RETURN) {
			CelebrationOverlay.get().play(CelebrationRequest.core(tier, me.ret(), me.staked(), SeedMix.mix(m.roundSeq, me.seat())));
		} else if (tier == WinTier.LOSS) {
			FxSounds.play("lose", 0.6f, 1f);
		}
	}

	@Override
	protected void drawTooltips(GuiGraphicsExtractor g, int mx, int my, int sx, int sy) {
		if (compact) return;
		for (Seat s : m.seats) {
			if (s.seat() == centreSeat() || !s.bot()) continue;
			int[] p = Blackjack.plate(positionOf(s.seat()));
			if (mx >= tx(p[0]) && mx < tx(p[0]) + 60 && my >= ty(p[1]) && my < ty(p[1]) + 22) {
				String lvl = s.botLevel() <= 1 ? "easy" : s.botLevel() == 2 ? "normal" : "hard";
				List<Component> lines = new ArrayList<>();
				if (s.nameC() != null) lines.add(s.nameC());
				lines.add(Component.translatable("gui.burmaldaholic.cards.level." + lvl + ".tooltip"));
				g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), sx, sy);
			}
		}
	}
}
