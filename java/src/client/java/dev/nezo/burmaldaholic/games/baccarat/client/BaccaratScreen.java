package dev.nezo.burmaldaholic.games.baccarat.client;

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
import dev.nezo.burmaldaholic.core.anim.cards.CardLayout.Baccarat;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.Card;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Baccarat / Chemin de fer table screen (UI.md §14; docs/design/visual/cards.md §6.6, animation/cards.md §4.2–§4.5;
 * J-C9): the Player and Banker hand panels (blue / red boxes with emblems, total badges), the cards dealt one per beat
 * from the shoe and turned by flips and the SQUEEZE (P2, B2 and the sideways third cards peel from the edge), the result
 * stamp, the five shared bet boxes with every bettor's mini stacks, the bead road, seat plates along the rail and the
 * console (chip rack, Rebet / Clear bets / Deal; chemmy: Take bank / Pass, Bet Player / Banco / Ready).
 *
 * <p>Only the published cards are drawn (a card appears at its DEAL step, its face at its FLIP / SQUEEZE start —
 * {@code BaccaratReveal}); the result, per-box returns and the celebration appear when the server leaves REVEAL. Actions:
 * house {@code bet {box, amount}} (left click adds the selected chip, right click removes the box), {@code clear},
 * {@code rebet}, {@code ready}; chemin de fer {@code punt}, {@code banco}, {@code take_bank}, {@code keep_bank}, {@code pass}.
 */
public class BaccaratScreen extends CardTableScreen {
	private static final long[] CHIPS = {1, 5, 25, 100, 500};
	private static final int BLUE = 0xFF3A6BE0, RED = 0xFFD03030, GREEN = 0xFF2FA64A, PAIR = 0xFFFFD640;
	private static final int LAVENDER = 0xFFC0B0DC;
	private static final BetKind[] BOX_ORDER = {BetKind.PLAYER_PAIR, BetKind.PLAYER, BetKind.TIE, BetKind.BANKER, BetKind.BANKER_PAIR};
	private final CardAnimator cards = new CardAnimator();
	private final CardMotion.Pose pose = new CardMotion.Pose();
	private long chip = 5;
	private boolean showRules;
	private int coupSeq;
	private long lastRvStart = Long.MIN_VALUE;
	private long resultAt = -1;
	private int celebrated = -1;
	private int beadCount = -1;
	private long beadAt = -1;
	private long heldBalance = -1;
	private long prevBalance = -1;
	private int shoeSlot = -1;
	private int shoeFromSlot = -1;
	private long shoePassAt = -1;
	private String lastBanco = "";
	private long bancoAt = -1;

	public BaccaratScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType().name().endsWith("player_banked") ? "gui.burmaldaholic.baccarat.title_chemmy"
			: menu.tableType().name().endsWith("high_roller") ? "gui.burmaldaholic.baccarat.title_high_roller" : "gui.burmaldaholic.baccarat.title"),
			TableTheme.Shape.CRESCENT);
	}

	// ---- state ------------------------------------------------------------------------------------------------------

	private boolean chemmy() {
		return state().contains("chemmy");
	}

	private CompoundTag ch() {
		return state().getCompoundOrEmpty("chemmy");
	}

	private boolean houseCoup() {
		return state().getBooleanOr("house_coup", true);
	}

	private boolean seated() {
		return mySeat() >= 0;
	}

	private long slip(String tag, BetKind k) {
		return state().getCompoundOrEmpty(tag).getLongOr(k.id(), 0);
	}

	private long mySlipTotal() {
		long s = 0;
		for (BetKind k : BetKind.values()) s += slip("slip", k);
		return s;
	}

	private boolean betting() {
		return "betting".equals(phase());
	}

	private boolean revealing() {
		return "reveal".equals(phase());
	}

	private boolean resultShown() {
		return "result".equals(phase());
	}

	private int[] arr(String key) {
		return state().getIntArray(key).orElse(new int[0]);
	}

	@Override
	protected void onStateChanged(CompoundTag s) {
		long rv = s.getLongOr("rv_start", Long.MIN_VALUE);
		if (rv != lastRvStart && s.getIntArray("player_cards").map(a -> a.length > 0).orElse(false)) {
			if (lastRvStart != Long.MIN_VALUE || revealing()) coupSeq++;
			lastRvStart = rv;
		}
		if (revealing()) {
			if (heldBalance < 0) heldBalance = prevBalance >= 0 ? prevBalance : balance();
			if (s.contains("my_result") || mySlipTotal() > 0) ClientFx.balanceHold.accept(1500);
		}
		if (resultShown() && resultAt < 0) resultAt = Util.getMillis();
		if (!resultShown()) resultAt = -1;
		if (!revealing() && !resultShown()) heldBalance = -1;
		int beads = arr("beads").length;
		if (beadCount >= 0 && beads > beadCount) beadAt = Util.getMillis();
		beadCount = beads;
		String banco = ch().getStringOr("banco", "") + ch().getStringOr("banco_bot", "");
		if (!banco.isEmpty() && !banco.equals(lastBanco)) {
			bancoAt = Util.getMillis();
			FxSounds.play("chip_push", 1f);
		}
		lastBanco = banco;
		prevBalance = balance();
		if (minecraft != null) rebuildConsole();
	}

	@Override
	protected long shownBalance() {
		if (heldBalance >= 0 && (revealing() || resultAt >= 0 && Util.getMillis() - resultAt < 1300)) return heldBalance;
		return balance();
	}

	// ---- console ----------------------------------------------------------------------------------------------------

	@Override
	protected Component plaqueTitle() {
		return title;
	}

	private boolean chipRackShown() {
		if (compact || !seated() || !betting()) return false;
		return !chemmy() || houseCoup() || !ch().getBooleanOr("you_bank", false);
	}

	@Override
	protected void buildConsole() {
		List<CardButton> row = new ArrayList<>();
		int minX = chipRackShown() ? 8 + CHIPS.length * 25 + 6 : compact ? 4 : 250;
		String phase = phase();
		row.add(button(Component.translatable(showRules ? "gui.burmaldaholic.common.back" : "gui.burmaldaholic.common.rules"), "paytable",
			CardButton.Family.TABLE, true, () -> {
				showRules = !showRules;
				rebuildConsole();
			}));
		if (!seated()) {
			row.add(button(Component.translatable("gui.burmaldaholic.baccarat.sit"), "play", CardButton.Family.PRIMARY, true, () -> sendAction("sit")));
		} else if (chemmy() && !houseCoup()) {
			CompoundTag c = ch();
			if ("bank_offer".equals(phase) && c.getBooleanOr("offer_you", false)) {
				if (c.getBooleanOr("offer_keep", false)) {
					row.add(button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.keep", Texts.number(c.getLongOr("bank", 0))), "take_bank",
						CardButton.Family.PRIMARY, true, () -> sendAction("keep_bank")));
					row.add(button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.pass_bank"), "leave", CardButton.Family.TABLE, true,
						() -> sendAction("pass")));
				} else {
					long amount = c.getLongOr("default_bank", 20);
					row.add(button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.number(amount)), "take_bank", CardButton.Family.PRIMARY,
						true, () -> takeBank(amount)));
					row.add(button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.pass"), "leave", CardButton.Family.TABLE, true, () -> sendAction("pass")));
				}
			} else if ("waiting".equals(phase)) {
				long amount = c.getLongOr("default_bank", 20);
				row.add(button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.number(amount)), "take_bank", CardButton.Family.PRIMARY, true,
					() -> takeBank(amount)));
			} else if (betting() && !c.getBooleanOr("you_bank", false)) {
				boolean open = c.getStringOr("banco", "").isEmpty() && c.getStringOr("banco_bot", "").isEmpty();
				row.add(button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.bet_player"), "player", CardButton.Family.TABLE,
					c.getLongOr("open", 0) > 0 && open, () -> {
						CompoundTag args = new CompoundTag();
						args.putLong("amount", chip);
						sendAction("punt", args);
						FxSounds.play("chip_place", 1f);
					}));
				long cov = c.getLongOr("coverage", 0);
				CardButton banco = button(Component.translatable("gui.burmaldaholic.baccarat.chemmy.banco", Texts.number(cov)), "banco", CardButton.Family.PRIMARY,
					open && cov > 0 && balance() + c.getLongOr("my_punt", 0) >= cov && c.getLongOr("my_max", 0) >= cov, () -> sendAction("banco"));
				banco.hint(Component.translatable("gui.burmaldaholic.baccarat.chemmy.banco.tooltip"));
				row.add(banco);
				row.add(button(Component.translatable("gui.burmaldaholic.common.ready"), "deal", CardButton.Family.TABLE,
					c.getLongOr("my_punt", 0) > 0 && !state().getBooleanOr("ready", false), () -> sendAction("ready")));
			} else {
				row.add(button(Component.translatable("gui.burmaldaholic.common.leave"), "leave", CardButton.Family.TABLE, true, () -> sendAction("leave")));
			}
		} else if (betting() || "idle".equals(phase) || resultShown()) {
			boolean open = betting() || "idle".equals(phase);
			row.add(button(Component.translatable("gui.burmaldaholic.common.rebet"), "rebet", CardButton.Family.TABLE,
				open && state().getBooleanOr("can_rebet", false), () -> sendAction("rebet")));
			row.add(button(Component.translatable("gui.burmaldaholic.baccarat.clear_bets"), "clear", CardButton.Family.TABLE, open && mySlipTotal() > 0,
				() -> sendAction("clear")));
			if (open) {
				boolean alone = state().getListOrEmpty("seat_list").size() <= 1;
				row.add(button(Component.translatable(alone ? "gui.burmaldaholic.common.deal" : "gui.burmaldaholic.common.ready"), "deal",
					CardButton.Family.PRIMARY, mySlipTotal() > 0 && !state().getBooleanOr("ready", false), () -> sendAction("ready")));
			}
		} else {
			row.add(button(Component.translatable("gui.burmaldaholic.common.leave"), "leave", CardButton.Family.TABLE, !revealing(), () -> sendAction("leave")));
		}
		if (compact) row.removeFirst(); // no room for the rules toggle
		layoutButtons(row, minX);
	}

	private void takeBank(long amount) {
		CompoundTag args = new CompoundTag();
		args.putLong("amount", amount);
		sendAction("take_bank", args);
	}

	private void addChip(BetKind k) {
		CompoundTag args = new CompoundTag();
		args.putString("box", k.id());
		long amount = chip;
		if (k == BetKind.BANKER) {
			long step = state().getLongOr("step", 20);
			long current = slip("slip", BetKind.BANKER);
			long add = chip >= step ? chip : step; // the server snaps larger chips down to the step (…baccarat.snapped)
			long want = Math.max(current + add, state().getLongOr("banker_min", step));
			amount = want - current;
		}
		args.putLong("amount", amount);
		sendAction("bet", args);
		FxSounds.play("chip_place", 1f);
	}

	@Override
	protected boolean onCanvasClick(double x, double y, int button) {
		if (chipRackShown() && button == 0) {
			for (int i = 0; i < CHIPS.length; i++) {
				int cx = 8 + i * 25;
				if (x >= cx && x < cx + 22 && y >= 212 && y < 234) {
					chip = CHIPS[i];
					FxSounds.play("chip_place", 0.6f, 1.2f);
					return true;
				}
			}
		}
		if (!showRules && betting() && seated() && (!chemmy() || houseCoup())) {
			for (BetKind k : boxes()) {
				int[] b = boxRect(k);
				if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
					if (button == 0) {
						addChip(k);
					} else if (button == 1 && slip("slip", k) > 0) {
						CompoundTag args = new CompoundTag();
						args.putString("box", k.id());
						sendAction("unbet", args);
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void drawConsoleText(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (chipRackShown()) {
			long max = Math.max(0, Math.min(maxBet(), balance()));
			for (int i = 0; i < CHIPS.length; i++) {
				int x = 8 + i * 25;
				boolean hover = mouseX >= x && mouseX < x + 22 && mouseY >= 212 && mouseY < 234;
				ChipStackView.big(g, (int) CHIPS[i], x, 212, CHIPS[i] == chip, hover, CHIPS[i] <= max);
			}
			return;
		}
		if (compact) return;
		int right = consoleTextRight();
		Component l1 = statusLine();
		if (l1 != null) CardGfx.fitted(g, font, l1, 8, 212, right - 8, CasinoPalette.BONE, true);
		Component l2 = resultLine();
		if (l2 != null) {
			CardGfx.fitted(g, font, l2, 8, 224, right - 8, resultColor(), true);
		} else {
			CardGfx.fitted(g, font, limitsText(), 8, 224, right - 8, LAVENDER, true);
		}
	}

	private Component limitsText() {
		MutableComponent l = limitsLine().copy();
		if (!chemmy() || houseCoup()) l = Component.translatable("gui.burmaldaholic.cards.join", l,
			Component.translatable("gui.burmaldaholic.baccarat.limits_banker", Texts.number(state().getLongOr("step", 20))));
		return l;
	}

	private long secondsLeft(String id) {
		return timerSeconds(id);
	}

	/** Phase / announcement line. */
	private @Nullable Component statusLine() {
		String phase = phase();
		if (state().getBooleanOr("waiting_next", false)) return Component.translatable("gui.burmaldaholic.baccarat.waiting_next");
		switch (phase) {
			case "shuffle" -> {
				MutableComponent line = Component.translatable("gui.burmaldaholic.baccarat.shuffling");
				int burned = state().getIntOr("burned", 0);
				return burned > 0 ? Component.translatable("gui.burmaldaholic.cards.join", line, Texts.plural("gui.burmaldaholic.baccarat.burned", burned)) : line;
			}
			case "no_more_bets" -> {
				return Component.translatable("gui.burmaldaholic.baccarat.no_more_bets");
			}
			case "reveal", "result" -> {
				Component a = announcement();
				if (a != null) return a;
				return revealing() ? squeezeLine() : null;
			}
			case "betting" -> {
				if (chemmy() && !houseCoup()) return chemmyStatus();
				long sec = secondsLeft("bet");
				return sec >= 0 ? Component.translatable("gui.burmaldaholic.baccarat.bets_close_in", Texts.plural("unit.burmaldaholic.second_acc", sec))
					: Component.translatable("gui.burmaldaholic.baccarat.place_bets");
			}
			default -> {
				return chemmy() ? chemmyStatus() : Component.translatable("gui.burmaldaholic.baccarat.place_bets");
			}
		}
	}

	private @Nullable Component chemmyStatus() {
		CompoundTag c = ch();
		String phase = phase();
		if ("bank_offer".equals(phase)) {
			return c.getBooleanOr("offer_you", false) ? Component.translatable("gui.burmaldaholic.baccarat.chemmy.offer")
				: Component.translatable("gui.burmaldaholic.baccarat.chemmy.waiting_offer", who(c, "candidate"));
		}
		if ("waiting".equals(phase)) return Component.translatable("gui.burmaldaholic.baccarat.chemmy.take", Texts.number(c.getLongOr("min_bank", 20)));
		if (c.getBooleanOr("you_bank", false)) return Component.translatable("gui.burmaldaholic.baccarat.chemmy.you_bank");
		boolean banco = !c.getStringOr("banco", "").isEmpty() || !c.getStringOr("banco_bot", "").isEmpty();
		if (banco) return Component.translatable("msg.burmaldaholic.baccarat.chemmy.banco_called", who(c, "banco"));
		if (c.getBooleanOr("held", false)) {
			return Component.translatable("gui.burmaldaholic.cards.join", Component.translatable("gui.burmaldaholic.baccarat.chemmy.banker_is", who(c, "banker")),
				Component.translatable("gui.burmaldaholic.baccarat.chemmy.coverage", Texts.number(c.getLongOr("coverage", 0)), Texts.number(c.getLongOr("open", 0))));
		}
		return Component.translatable("gui.burmaldaholic.baccarat.place_bets");
	}

	private static Component who(CompoundTag c, String field) {
		String bot = c.getStringOr(field + "_bot", "");
		return bot.isEmpty() ? Texts.raw(c.getStringOr(field, "")) : Component.translatable(bot);
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

	/** Who squeezes the card being peeled now (cosmetic label, §4.3). */
	private @Nullable Component squeezeLine() {
		int[] ss = squeezing();
		if (ss == null) return null;
		String who = state().getStringOr(ss[0] == 0 ? "sq_player" : "sq_banker", "");
		String me = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
		if (!who.isEmpty() && who.equals(me)) return Component.translatable("gui.burmaldaholic.baccarat.fx.you_squeeze");
		if (!who.isEmpty()) return Component.translatable("gui.burmaldaholic.baccarat.fx.squeezing", Texts.raw(who));
		return Component.translatable(ss[0] == 0 ? "gui.burmaldaholic.baccarat.fx.player_card" : "gui.burmaldaholic.baccarat.fx.banker_card");
	}

	/** [side, index] of the card in its squeeze window now, or null. */
	private int @Nullable [] squeezing() {
		double now = nowMs();
		long rv = state().getLongOr("rv_start", 0);
		for (int side = 0; side < 2; side++) {
			String key = side == 0 ? "player" : "banker";
			int[] rev = arr(key + "_rev");
			int[] sqz = arr(key + "_sqz");
			for (int i = 0; i < rev.length && i < sqz.length; i++) {
				if (rev[i] < 0 || sqz[i] <= 0) continue;
				double start = (rv + rev[i]) * 50.0;
				if (now >= start && now < start + sqz[i] * 50.0) return new int[] {side, i};
			}
		}
		return null;
	}

	private List<Card> cardsOf(String key) {
		List<Card> out = new ArrayList<>();
		for (int c : arr(key + "_cards")) if (c >= 0 && c < 52) out.add(Card.fromCode(c));
		return out;
	}

	/** "You win 100 · next coup in 4 s". */
	private @Nullable Component resultLine() {
		if (!resultShown()) return null;
		long net = myNet();
		Component first = null;
		if (hasMyResult()) {
			first = net > 0 ? Component.translatable("gui.burmaldaholic.baccarat.you_win", Texts.number(net))
				: net < 0 ? Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(-net))
				: Component.translatable("gui.burmaldaholic.common.result.push");
		}
		long sec = secondsLeft("phase");
		Component next = sec >= 0 ? Component.translatable("gui.burmaldaholic.baccarat.next_coup", Texts.plural("unit.burmaldaholic.second_acc", sec)) : null;
		if (first == null) return next;
		return next == null ? first : Component.translatable("gui.burmaldaholic.cards.join", first, next);
	}

	private int resultColor() {
		if (!hasMyResult()) return LAVENDER;
		long net = myNet();
		return net > 0 ? CasinoPalette.BONUS : net < 0 ? CasinoPalette.CHIP_RED_LIGHT : LAVENDER;
	}

	private boolean hasMyResult() {
		return state().contains("my_result") || state().contains("my_punt") || state().contains("my_bank_delta");
	}

	private long myNet() {
		CompoundTag res = state().getCompoundOrEmpty("my_result");
		long net = res.getLongOr("ret", 0) - res.getLongOr("staked", 0);
		if (state().contains("my_punt")) net += state().getLongOr("my_punt_return", 0) - state().getLongOr("my_punt", 0);
		if (state().contains("my_bank_delta")) net += state().getLongOr("my_bank_delta", 0);
		return net;
	}

	// ---- scene --------------------------------------------------------------------------------------------------------

	private int tx(int x) {
		return compact ? tableX() + dev.nezo.burmaldaholic.core.anim.cards.CardLayout.c(x) : tableX() + x;
	}

	private int ty(int y) {
		return compact ? tableY() + dev.nezo.burmaldaholic.core.anim.cards.CardLayout.c(y) : tableY() + y;
	}

	private List<BetKind> boxes() {
		List<BetKind> out = new ArrayList<>();
		boolean pairs = state().getBooleanOr("pairs", true);
		for (BetKind k : BOX_ORDER) if (pairs || !k.pair()) out.add(k);
		return out;
	}

	/** Canvas rect (x, y, w, h) of a bet box. */
	private int[] boxRect(BetKind k) {
		int i = switch (k) {
			case PLAYER_PAIR -> 0;
			case PLAYER -> 1;
			case TIE -> 2;
			case BANKER -> 3;
			case BANKER_PAIR -> 4;
		};
		int[] b = Baccarat.BOXES[i];
		int x = tx(b[0]);
		int w = tx(b[0] + b[1]) - x;
		int y = ty(Baccarat.BOX_Y);
		int h = ty(Baccarat.BOX_Y + Baccarat.BOX_H) - y;
		return new int[] {x, y, w, h};
	}

	private static int boxColor(BetKind k) {
		return switch (k) {
			case PLAYER -> BLUE;
			case BANKER -> RED;
			case TIE -> GREEN;
			default -> PAIR;
		};
	}

	/** Which boxes win this coup (public after the reveal). */
	private boolean boxWins(BetKind k) {
		if (!resultShown()) return false;
		List<Card> p = cardsOf("player");
		List<Card> b = cardsOf("banker");
		if (p.size() < 2 || b.size() < 2) return false;
		int pt = BaccaratRules.total(p);
		int bt = BaccaratRules.total(b);
		return switch (k) {
			case PLAYER -> pt > bt;
			case BANKER -> bt > pt;
			case TIE -> pt == bt;
			case PLAYER_PAIR -> p.get(0).rank() == p.get(1).rank();
			case BANKER_PAIR -> b.get(0).rank() == b.get(1).rank();
		};
	}

	private Component boxLabel(BetKind k) {
		Component name = Component.translatable("gui.burmaldaholic.baccarat.box." + k.id());
		Component ratio = switch (k) {
			case PLAYER -> Texts.raw("1:1"); // literal-ok: ratio
			case BANKER -> Texts.raw(bankerRatio()); // literal-ok: ratio
			case TIE -> Texts.raw(state().getIntOr("tie_pays", 8) + ":1"); // literal-ok: ratio
			case PLAYER_PAIR, BANKER_PAIR -> Texts.raw(state().getIntOr("pair_pays", 11) + ":1"); // literal-ok: ratio
		};
		return Component.translatable("gui.burmaldaholic.baccarat.box_label", name, ratio);
	}

	/** The Banker payout after commission: ".95" for 5 %. */
	private String bankerRatio() {
		int bp = state().getIntOr("commission_bp", 500);
		String s = java.math.BigDecimal.valueOf(10_000 - bp, 4).stripTrailingZeros().toPlainString();
		return s.startsWith("0") ? s.substring(1) : s;
	}

	@Override
	protected void drawScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		double now = nowMs();
		boolean reduced = reduced();
		long local = Util.getMillis();
		int back = theme.back(CardSprites.BACK_BURGUNDY);
		// props
		int trayX = tx(Baccarat.TRAY_X);
		int trayY = ty(Baccarat.TRAY_Y);
		CardGfx.sprite(g, theme.themed("prop/tray"), trayX, trayY, 30, 22, 0xFFFFFFFF);
		CardGfx.tex(g, CardGfx.spriteFile("cards/prop/tray_fill"), trayX + 3, trayY + 11, 24, 8, 0, 4, 24, 8, 24, 12, 0xFFFFFFFF);
		int[] shoe = shoePos(local);
		CardGfx.sprite(g, theme.themed("prop/shoe"), shoe[0], shoe[1], 40, 28, 0xFFFFFFFF);
		cards.origin(shoe[0] + 2, shoe[1] + 16).tray(trayX + 3, trayY + 1);
		// hand panels
		boolean result = resultShown();
		List<Card> pc = cardsOf("player");
		List<Card> bc = cardsOf("banker");
		int pt = pc.isEmpty() ? -1 : BaccaratRules.total(pc);
		int bt = bc.isEmpty() ? -1 : BaccaratRules.total(bc);
		int winner = !result || pt < 0 || bt < 0 ? -1 : pt > bt ? 0 : bt > pt ? 1 : 2;
		for (int side = 0; side < 2; side++) {
			int x0 = tx(Baccarat.PANEL_X[side]);
			int y0 = ty(Baccarat.PANEL_Y);
			int w = tx(Baccarat.PANEL_X[side] + Baccarat.PANEL_W) - x0;
			int h = ty(Baccarat.PANEL_Y + Baccarat.PANEL_H) - y0;
			boolean win = winner == side || winner == 2;
			int color = winner == 2 ? GREEN : side == 0 ? BLUE : RED;
			if (win) CardGfx.sprite(g, FxSprites.sprite("cards/fx/spot_glow"), x0 - 4, y0 - 4, w + 8, h + 8, CardGfx.white(0.55));
			CardGfx.sprite(g, FxSprites.sprite("cards/print/box"), x0, y0, w, h, CardGfx.alpha(color, win ? 0.95 : 0.55));
			if (!compact) {
				CardGfx.sprite(g, FxSprites.sprite(side == 0 ? "cards/print/emblem_player" : "cards/print/emblem_banker"), x0 + 5, y0 + 4, 11, 11,
					CardGfx.alpha(color, 0.95));
				CardGfx.text(g, font, Component.translatable(side == 0 ? "gui.burmaldaholic.baccarat.box.player" : "gui.burmaldaholic.baccarat.box.banker"),
					x0 + 19, y0 + 5, win ? CasinoPalette.BONE : LAVENDER, win);
			}
		}
		// cards
		cards.begin(now, reduced);
		long rv = state().getLongOr("rv_start", 0);
		for (int side = 0; side < 2; side++) {
			String key = side == 0 ? "player" : "banker";
			int[] codes = arr(key + "_cards");
			int[] deal = arr(key + "_deal");
			int[] rev = arr(key + "_rev");
			int[] sqz = arr(key + "_sqz");
			for (int i = 0; i < codes.length; i++) {
				int x = tx(Baccarat.cardX(side, i));
				int y = ty(Baccarat.cardY(i));
				int size = compact ? CardSprites.M : CardSprites.L;
				double dealMs = i < deal.length ? (rv + deal[i]) * 50.0 : Double.NaN;
				boolean squeeze = i < sqz.length && sqz[i] > 0 && i < rev.length && rev[i] >= 0;
				double revMs = !squeeze && i < rev.length && rev[i] >= 0 ? (rv + rev[i]) * 50.0 : Double.NaN;
				int k = Math.floorMod(coupSeq, 1000) * 10 + side * 3 + i;
				CardAnimator.Slot s = cards.card(k, codes[i], size, back, x, y, i == 2 ? 90 : 0, dealMs, revMs);
				if (squeeze) cards.squeeze(s, (rv + rev[i]) * 50.0, sqz[i] * 50, side == 1);
				if (result && winner >= 0 && winner != 2 && winner != side) cards.dim(s, true);
				if (result && (winner == side || winner == 2)) cards.glow(s, true);
			}
		}
		cards.end();
		cards.draw(g, font);
		// totals (only of faces fully shown)
		for (int side = 0; side < 2; side++) {
			String key = side == 0 ? "player" : "banker";
			int[] codes = arr(key + "_cards");
			List<Card> shown = new ArrayList<>();
			for (int i = 0; i < codes.length; i++) {
				if (codes[i] >= 0 && cards.faceShown(Math.floorMod(coupSeq, 1000) * 10 + side * 3 + i)) shown.add(Card.fromCode(codes[i]));
			}
			if (shown.isEmpty()) continue;
			int total = BaccaratRules.total(shown);
			boolean natural = shown.size() == 2 && codes.length == 2 && BaccaratRules.isNatural(total);
			int x = tx(Baccarat.PANEL_X[side] + 118);
			ActionTag.badge(g, font, Texts.number(total), compact ? x - 4 : x, ty(17), natural || winner == side, 1);
		}
		drawBoxes(g, mouseX, mouseY, local);
		// the dealer rack sits on the dealer edge
		if (compact) {
			CardGfx.tex(g, CardGfx.spriteFile("cards/prop/rack_" + theme.id), tx(Baccarat.RACK_X), ty(-1), 80, 10, 0, 0, 80, 10, 80, 14, 0xFFFFFFFF);
		} else {
			CardGfx.sprite(g, theme.themed("prop/rack"), tx(Baccarat.RACK_X), ty(Baccarat.RACK_Y), 80, 14, 0xFFFFFFFF);
		}
		drawRoad(g, local);
		drawPlates(g, local);
		// stamps: natural on the hand, the result in the middle
		if (state().contains("announce_0") && pc.size() >= 2 && bc.size() >= 2 && !compact) {
			int p2 = BaccaratRules.total(pc.subList(0, 2));
			int b2 = BaccaratRules.total(bc.subList(0, 2));
			for (int side = 0; side < 2; side++) {
				int t2 = side == 0 ? p2 : b2;
				if (BaccaratRules.isNatural(t2) && !result) {
					double cx = tx(Baccarat.PANEL_X[side] + Baccarat.PANEL_W / 2);
					double at = (rv + state().getIntOr("announce_0", 0)) * 50.0;
					TableStamp.draw(g, font, Component.translatable("gui.burmaldaholic.baccarat.stamp.natural", Texts.number(t2)), TableStamp.Kind.GOLD, cx,
						ty(56), -6, now - at, reduced, pose);
				}
			}
		}
		if (result && winner >= 0) {
			String k = winner == 0 ? "player" : winner == 1 ? "banker" : "tie";
			Component text = winner == 1 ? Component.translatable("gui.burmaldaholic.baccarat.stamp.banker", Texts.number(bt), Texts.number(pt))
				: Component.translatable("gui.burmaldaholic.baccarat.stamp." + k, Texts.number(pt), Texts.number(bt));
			boolean tieBettor = slip("slip", BetKind.TIE) > 0 || state().getCompoundOrEmpty("my_result").getLongOr("tie", 0) > 0;
			if (winner == 2 && tieBettor) text = Component.translatable("gui.burmaldaholic.baccarat.fx.tie_pays");
			TableStamp.draw(g, font, text, winner == 2 ? TableStamp.Kind.GREEN : TableStamp.Kind.GOLD, tx(Baccarat.RESULT_CX), ty(Baccarat.RESULT_CY), -3,
				resultAt < 0 ? 0 : local - resultAt, reduced, pose);
		}
		// squeeze label (who squeezes) under the hand
		int[] sq = squeezing();
		if (sq != null && !compact) {
			Component line = squeezeLine();
			if (line != null) {
				int cx = tx(Baccarat.PANEL_X[sq[0]] + Baccarat.PANEL_W / 2);
				CardGfx.centered(g, font, line, cx, ty(Baccarat.PANEL_Y + Baccarat.PANEL_H - 11), CasinoPalette.GOLD, true);
			}
		}
		maybeCelebrate();
	}

	/** Shoe position: fixed at the top-right, or (chemin de fer) at the banker's plate edge, sliding when the bank passes. */
	private int[] shoePos(long local) {
		int[] home = {tx(Baccarat.SHOE_X), ty(Baccarat.SHOE_Y)};
		if (!chemmy() || houseCoup() || compact) return home;
		int slot = -1;
		ListTag seats = state().getListOrEmpty("seat_list");
		List<CompoundTag> order = plateOrder(seats);
		for (int i = 0; i < order.size(); i++) if (order.get(i).getBooleanOr("banker", false)) slot = i;
		if (slot != shoeSlot) {
			if (shoeSlot >= 0 && slot >= 0) {
				shoeFromSlot = shoeSlot;
				shoePassAt = local;
				FxSounds.play("card_slide", 1f);
			}
			shoeSlot = slot;
		}
		int[] to = slot < 0 ? home : shoeAt(slot);
		if (shoePassAt >= 0 && shoeFromSlot >= 0) {
			double t = CardMotion.progress(local, shoePassAt, CardMotion.SHOE_PASS_MS);
			if (t < 1 && !reduced()) {
				int[] from = shoeAt(shoeFromSlot);
				double p = CardMotion.pass(t);
				return new int[] {(int) Math.round(from[0] + (to[0] - from[0]) * p), (int) Math.round(from[1] + (to[1] - from[1]) * p)};
			}
		}
		return to;
	}

	private int[] shoeAt(int slot) {
		int[] p = Baccarat.PLATES[Math.min(slot, Baccarat.PLATES.length - 1)];
		int x = Math.max(0, Math.min(canvasW() - 42, tx(p[0] + 20)));
		return new int[] {x, ty(p[1] - 26)};
	}

	private void drawBoxes(GuiGraphicsExtractor g, int mouseX, int mouseY, long local) {
		boolean betting = betting();
		boolean chemmyBank = chemmy() && !houseCoup();
		for (BetKind k : boxes()) {
			if (chemmyBank && k != BetKind.PLAYER) continue;
			int[] r = boxRect(k);
			boolean win = boxWins(k);
			boolean hover = betting && mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
			if (win) CardGfx.sprite(g, FxSprites.sprite("cards/fx/spot_glow"), r[0] - 4, r[1] - 4, r[2] + 8, r[3] + 8, CardGfx.white(0.9));
			CardGfx.sprite(g, FxSprites.sprite("cards/print/box"), r[0], r[1], r[2], r[3], CardGfx.alpha(boxColor(k), win ? 0.95 : hover ? 0.85 : 0.6));
			if (hover) CardGfx.frame(g, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, CardGfx.alpha(CasinoPalette.GLINT, 0.8));
			Component label = boxLabel(k);
			if (compact) {
				CardGfx.fitted(g, font, label, r[0] + 2, r[1] + 1, r[2] - 4, win ? CasinoPalette.BONE : theme.print, win);
			} else {
				int lw = Math.min(font.width(label), r[2] - 4);
				CardGfx.fitted(g, font, label, r[0] + (r[2] - lw) / 2, r[1] + 3, r[2] - 4, win ? CasinoPalette.BONE : theme.print, win);
			}
			drawBoxStacks(g, k, r, win, local);
		}
	}

	/** Every bettor's stack in a box: mine (real discs, label), the others' combined (seat tint), atmosphere bots (hatch). */
	private void drawBoxStacks(GuiGraphicsExtractor g, BetKind k, int[] r, boolean win, long local) {
		boolean chemmyBank = chemmy() && !houseCoup();
		long mine = chemmyBank ? ch().getLongOr("my_punt", 0) : slip("slip", k);
		CompoundTag res = state().getCompoundOrEmpty("my_result");
		if (resultShown() && mine == 0) mine = res.getLongOr(k.id(), 0);
		long others = slip("others", k);
		long bots = 0;
		ListTag bb = state().getListOrEmpty("bot_bets");
		for (int i = 0; i < bb.size(); i++) bots += bb.getCompoundOrEmpty(i).getLongOr(k.id(), 0);
		if (chemmyBank) {
			ListTag punts = ch().getListOrEmpty("punts");
			for (int i = 0; i < punts.size(); i++) {
				CompoundTag pt = punts.getCompoundOrEmpty(i);
				if (pt.getBooleanOr("you", false)) continue;
				if (pt.getStringOr("bot", "").isEmpty()) others += pt.getLongOr("amount", 0);
				else bots += pt.getLongOr("amount", 0);
			}
		}
		int baseY = r[1] + r[3] - 3;
		boolean result = resultShown();
		long age = result && resultAt >= 0 ? local - resultAt - 400 - 120L * boxIndex(k) : -1;
		int rackX = tx(Baccarat.RACK_X + 40);
		int rackY = ty(4);
		int x = r[0] + 12;
		long ret = res.getCompound("returns").map(t -> t.getLongOr(k.id(), 0)).orElse(0L);
		if (mine > 0) {
			if (result && age >= 0 && !win && ret < mine && ret == 0) {
				double t = CardMotion.progress(age, 0, CardMotion.SWEEP_MS);
				if (t < 1) ChipStackView.flying(g, pose, x, baseY, rackX, rackY, t, true, mine, 0, false, reduced());
			} else if (result && age >= 900) {
				double t = CardMotion.progress(age, 900, CardMotion.TO_BALANCE_MS);
				if (t < 1) ChipStackView.flying(g, pose, x, baseY, canvasW() - 30, 8, t, false, Math.max(mine, ret), 0, false, reduced());
			} else {
				int wig = result && ret == mine && age >= 0 ? CardMotion.wigglePx(age / (double) CardMotion.PUSH_WIGGLE_MS) : 0;
				ChipStackView.stack(g, x + wig, baseY, mine, 4, 0, false, 1);
				if (result && ret > mine && age >= 0) {
					double p = CardMotion.progress(age, 0, CardMotion.CHIP_FLIGHT_MS);
					ChipStackView.flying(g, pose, rackX, rackY, x + 14, baseY, p, false, ret - mine, 0, false, reduced());
					CardGfx.text(g, font, Texts.raw("+" + Texts.number(ret - mine).getString()), x + 22, baseY - 12, CasinoPalette.BONUS, true); // literal-ok: +N
				}
			}
			x += compact ? 10 : 16;
		}
		if (others > 0 && !(result && age >= 0 && !win)) {
			ChipStackView.stack(g, x, baseY, others, 3, ChipStackView.seatTint(0), false, 1);
			x += compact ? 10 : 16;
		}
		if (bots > 0 && !(result && age >= 0 && !win)) ChipStackView.stack(g, x, baseY, bots, 3, 0, true, 1);
	}

	private static int boxIndex(BetKind k) {
		return switch (k) {
			case PLAYER -> 0;
			case BANKER -> 1;
			case TIE -> 2;
			case PLAYER_PAIR -> 3;
			case BANKER_PAIR -> 4;
		};
	}

	/** The bead road (4 rows visible, newest column right), runtime letters, pair dots; the newest bead drops in. */
	private void drawRoad(GuiGraphicsExtractor g, long local) {
		if (compact) return;
		int x0 = tx(Baccarat.ROAD_X);
		int y0 = ty(Baccarat.ROAD_Y);
		CardGfx.sprite(g, FxSprites.sprite("cards/panel/road"), x0, y0, Baccarat.ROAD_W, Baccarat.ROAD_H, 0xFFFFFFFF, 0xE0180A28);
		int[] beads = arr("beads");
		int rows = Baccarat.BEAD_ROWS;
		int cols = Baccarat.BEAD_COLS;
		int start = Math.max(0, beads.length - rows * cols);
		start -= start % rows;
		if (beads.length - start > rows * cols) start += rows;
		for (int i = start; i < beads.length; i++) {
			int idx = i - start;
			int bx = tx(Baccarat.BEAD_X + (idx / rows) * Baccarat.BEAD_COL);
			int by = ty(Baccarat.BEAD_Y + (idx % rows) * Baccarat.BEAD_ROW);
			boolean newest = i == beads.length - 1 && beadAt >= 0;
			if (newest) {
				double t = CardMotion.progress(local, beadAt + 200, CardMotion.BEAD_DROP_MS);
				if (local < beadAt + 200) continue;
				by += (int) Math.round(reduced() ? 0 : CardMotion.beadDropDy(t));
			}
			int b = beads[i];
			int side = b & 3;
			String kind = side == 0 ? "player" : side == 1 ? "banker" : "tie";
			CardGfx.sprite(g, FxSprites.sprite("cards/bead/" + kind), bx, by, 9, 9, 0xFFFFFFFF, side == 0 ? BLUE : side == 1 ? RED : GREEN);
			Component letter = Component.translatable("gui.burmaldaholic.baccarat.bead." + kind);
			CardGfx.text(g, font, letter, bx + (9 - font.width(letter)) / 2 + 1, by + 1, 0xFFFFFFFF, false);
			if ((b & 4) != 0) CardGfx.sprite(g, FxSprites.sprite("cards/bead/pair_player"), bx - 1, by - 1, 3, 3, 0xFFFFFFFF, 0xFFFFFFFF);
			if ((b & 8) != 0) CardGfx.sprite(g, FxSprites.sprite("cards/bead/pair_banker"), bx + 7, by + 7, 3, 3, 0xFFFFFFFF, 0xFFFFFFFF);
		}
	}

	/** Seats in plate order: the viewer first (the slot nearest the player), then by seat index. */
	private static List<CompoundTag> plateOrder(ListTag seats) {
		List<CompoundTag> out = new ArrayList<>();
		for (int i = 0; i < seats.size(); i++) if (seats.getCompoundOrEmpty(i).getBooleanOr("you", false)) out.add(seats.getCompoundOrEmpty(i));
		List<CompoundTag> rest = new ArrayList<>();
		for (int i = 0; i < seats.size(); i++) if (!seats.getCompoundOrEmpty(i).getBooleanOr("you", false)) rest.add(seats.getCompoundOrEmpty(i));
		rest.sort(java.util.Comparator.comparingInt(t -> t.getIntOr("index", 0)));
		out.addAll(rest);
		return out;
	}

	private void drawPlates(GuiGraphicsExtractor g, long local) {
		if (compact) return;
		List<CompoundTag> order = plateOrder(state().getListOrEmpty("seat_list"));
		for (int i = 0; i < order.size() && i < Baccarat.PLATES.length; i++) {
			CompoundTag st = order.get(i);
			String botKey = st.getStringOr("bot", "");
			boolean bot = !botKey.isEmpty();
			boolean me = st.getBooleanOr("you", false);
			Component name = bot ? Component.translatable(botKey) : Texts.raw(st.getStringOr("name", ""));
			long stake = st.getLongOr("stake", 0);
			Component sub;
			if (st.getBooleanOr("banker", false)) {
				sub = Component.translatable("gui.burmaldaholic.baccarat.chemmy.bank", Texts.number(ch().getLongOr("bank", 0)));
			} else if (me && mySlipTotal() > 0) {
				BetKind main = BetKind.PLAYER;
				for (BetKind k : BetKind.values()) if (slip("slip", k) > slip("slip", main)) main = k;
				sub = Component.translatable("gui.burmaldaholic.baccarat.hand", Component.translatable("gui.burmaldaholic.baccarat." + main.id()),
					Texts.number(slip("slip", main)));
			} else if (bot && (st.getBooleanOr("watching", false) || stake <= 0)) {
				sub = Component.translatable("gui.burmaldaholic.bots.watching");
			} else {
				sub = stake > 0 ? Texts.number(stake) : Component.translatable("gui.burmaldaholic.baccarat.no_bets");
			}
			boolean won = resultShown() && me && hasMyResult() && myNet() > 0;
			SeatPlate.State state = won ? SeatPlate.State.WINNER : me ? SeatPlate.State.ME
				: st.getBooleanOr("banker", false) ? SeatPlate.State.ACTIVE : SeatPlate.State.NORMAL;
			String avatar = bot ? theme.botAvatar(botKey) : null;
			int[] p = Baccarat.PLATES[i];
			SeatPlate.Info info = new SeatPlate.Info(name, st.getStringOr("name", ""), avatar, bot ? Math.max(1, st.getIntOr("bot_level", 2)) : 0, sub,
				CasinoPalette.GOLD, state, false);
			int x = tx(p[0]);
			int y = ty(p[1]);
			SeatPlate.draw(g, font, info, x, y, 1, 0);
			if (st.getBooleanOr("ready", false) && betting() && !bot) {
				CardGfx.sprite(g, FxSprites.sprite("cards/icon/check"), x + SeatPlate.width(font, info) - 8, y - 6, 12, 12, 0xFFFFFFFF);
			}
			// Banco!: the caller's plate gets the gold stamp
			String bancoName = ch().getStringOr("banco", "");
			String bancoBot = ch().getStringOr("banco_bot", "");
			boolean caller = !bancoName.isEmpty() && bancoName.equals(st.getStringOr("name", "")) || !bancoBot.isEmpty() && bancoBot.equals(botKey);
			if (caller && bancoAt >= 0) {
				TableStamp.draw(g, font, Component.translatable("gui.burmaldaholic.baccarat.fx.banco"), TableStamp.Kind.GOLD, x + 30, y - 4, -6,
					local - bancoAt, reduced(), pose);
			}
		}
	}

	private void maybeCelebrate() {
		if (!resultShown() || resultAt < 0 || celebrated == coupSeq) return;
		if (Util.getMillis() - resultAt < 1600) return;
		celebrated = coupSeq;
		CompoundTag res = state().getCompoundOrEmpty("my_result");
		if (res.isEmpty()) return;
		WinTier tier;
		try {
			tier = WinTier.valueOf(res.getStringOr("tier", "LOSS"));
		} catch (IllegalArgumentException e) {
			return;
		}
		if (tier.isWin() || tier == WinTier.RETURN) {
			CelebrationOverlay.get().play(CelebrationRequest.core(tier, res.getLongOr("ret", 0), res.getLongOr("staked", 0), SeedMix.mix(coupSeq, 7)));
		}
	}

	@Override
	protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		if (!showRules) return;
		int x = compact ? 8 : 40;
		int y = compact ? 14 : 24;
		int w = canvasW() - 2 * x;
		int h = (compact ? 118 : 176) - y;
		CardGfx.sprite(g, FxSprites.sprite("cards/panel/road"), x, y, w, h, 0xFFFFFFFF, 0xF0180A28);
		List<Component> rules = new ArrayList<>();
		String commission = java.math.BigDecimal.valueOf(state().getIntOr("commission_bp", 500), 2).stripTrailingZeros().toPlainString();
		if (chemmy() && !houseCoup()) {
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.rules.1"));
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.rules.2"));
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.chemmy.rules.3",
				Texts.raw(java.math.BigDecimal.valueOf(ch().getIntOr("rake_bp", 500), 2).stripTrailingZeros().toPlainString())));
		} else {
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.1", Texts.number(state().getIntOr("decks", 8)), Texts.raw(commission),
				Texts.number(state().getIntOr("tie_pays", 8))));
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.4"));
			if (state().getBooleanOr("pairs", true)) rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.5", Texts.number(state().getIntOr("pair_pays", 11))));
			rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.banker_step", Texts.number(state().getLongOr("step", 20))));
		}
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.natural"));
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.player"));
		rules.add(Component.translatable("gui.burmaldaholic.baccarat.rules.banker_no_draw"));
		int ly = y + 6;
		for (Component r : rules) {
			for (FormattedCharSequence l : font.split(r, w - 12)) {
				if (ly + 9 > y + h - 4) return;
				CardGfx.text(g, font, l, x + 6, ly, LAVENDER, false);
				ly += 10;
			}
		}
	}

	@Override
	protected void drawTooltips(GuiGraphicsExtractor g, int mx, int my, int sx, int sy) {
		if (!betting() || showRules) return;
		for (BetKind k : boxes()) {
			int[] r = boxRect(k);
			if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
				String commission = java.math.BigDecimal.valueOf(state().getIntOr("commission_bp", 500), 2).stripTrailingZeros().toPlainString();
				Component tip = switch (k) {
					case BANKER -> Component.translatable("gui.burmaldaholic.baccarat.bet.banker.tooltip", Texts.raw(commission));
					case TIE -> Component.translatable("gui.burmaldaholic.baccarat.bet.tie.tooltip");
					case PLAYER_PAIR, BANKER_PAIR -> Component.translatable("gui.burmaldaholic.baccarat.bet.pair.tooltip");
					case PLAYER -> Component.translatable("gui.burmaldaholic.baccarat.rules.2");
				};
				g.setTooltipForNextFrame(font.split(tip, 220), sx, sy);
			}
		}
	}
}
