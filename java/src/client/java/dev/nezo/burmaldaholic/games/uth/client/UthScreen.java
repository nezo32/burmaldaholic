package dev.nezo.burmaldaholic.games.uth.client;

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
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer.Rect;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.logic.Decision;
import dev.nezo.burmaldaholic.games.uth.logic.PayHand;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import dev.nezo.burmaldaholic.games.uth.present.UthLayout;
import dev.nezo.burmaldaholic.games.uth.present.UthPub;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Ultimate Texas Hold'em screen, redesigned (docs/design/visual/cards.md §6.5, mockup {@code cards_uth_decision.png};
 * motion animation/cards.md §3.2–§3.3, tasks J-C8 + J-C12): the crescent felt, the dealer's two cards at the dealer
 * edge with the chip rack, the five board cards, the Blind paytable panel, the viewer's hole cards "in your hands" and
 * the row of four circles (Trips, Ante, Blind, Play), the other seats' plates (mob-face bot avatars with level badges,
 * thinking dots) with their cards and combined stacks, the console with Bet 4× / 3× (primary), Check, the ghost Play
 * stack on hover, and the chip rack while betting.
 *
 * <p>Motion follows the server's {@link UthBeats} segments ({@code fx}): the deal (seats, dealer, the board backs
 * sweeping out), street flips, the showdown (dealer flip, the qualify banner — the same pause either way — every
 * seat's cards, then settlement circle by circle: payouts pop from the rack, losses sweep to it, pushes wiggle), and the
 * gather. The per-bet lines, the result and the celebration appear after the gate ({@code result}). Actions as before:
 * {@code bet {ante, trips}}, {@code clear}, {@code check|bet_4x|bet_3x|bet_2x|bet_1x|fold}, {@code take_bank {amount}},
 * {@code leave_bank}, {@code bot_settings}, core's {@code sit|leave}.
 */
public class UthScreen extends CardTableScreen {
	private static final int[] CHIPS = {1, 5, 25, 100, 500};
	private static final int GREEN = 0xFF80FF40;
	private static final int MUTED = 0xFFB0A0C0;
	private static final int[] NO_CARDS = new int[0];

	private final CardAnimator cards = new CardAnimator();
	private final CardMotion.Pose pose = new CardMotion.Pose();
	private final List<Hover> hovers = new ArrayList<>();
	/** This frame's seat plates (labels keep off them). */
	private final List<Rect> plates = new ArrayList<>();
	private long ante;
	private long trips;
	private boolean chipsToTrips;
	private boolean showPaytable;
	private boolean bankForm;
	private @Nullable EditBox bankBox;
	private String bankText = "";
	private int clientTicks;
	private int stateTick;
	private @Nullable CardButton hoveredBet;
	private long hoveredBetAmount;
	/** GameTests: a decision whose ghost Play stack is shown as if its button were hovered. */
	private @Nullable String previewBet;

	// presentation
	private int fxSeq = -1;
	private int fxKind;
	private long fxStart;
	private int fxSeats = 1;
	private @Nullable Timeline fx;
	private int roundKey;
	private double anteAt = Double.NaN;
	private long shownAnte;
	private int celebrated = -1;

	private record Hover(int x, int y, int w, int h, Component text) {}

	public UthScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(titleKey(menu)), TableTheme.Shape.CRESCENT);
	}

	private static String titleKey(CasinoTableMenu menu) {
		String name = menu.tableType().name();
		return name.endsWith("high_roller") ? "gui.burmaldaholic.uth.title_high_roller"
			: name.endsWith("player_banked") ? "gui.burmaldaholic.uth.title_player_banked" : "gui.burmaldaholic.uth.title";
	}

	// ---- state helpers ------------------------------------------------------------------------------------------

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

	private boolean pendingValid() {
		long min = minBet();
		long tripsMin = s().getLongOr("trips_min", 1);
		return ante >= min && (trips == 0 || trips >= tripsMin) && 6 * ante + trips <= maxBet() && 3 * ante + trips <= balance();
	}

	/** GameTests (mockup screenshots): show the ghost Play stack of decision {@code id} as if its button were hovered. */
	public void previewBetForTests(@Nullable String id) {
		this.previewBet = id;
	}

	/** GameTests: the running segment kind ({@link UthPub#DEAL} …, 0 = betting). */
	public int fxKind() {
		return fxKind;
	}

	@Override
	protected void onStateChanged(CompoundTag st) {
		stateTick = clientTicks;
		if (ante == 0 && st.getLongOr("last_ante", 0) == 0) ante = Math.max(0, st.getLongOr("min", 0));
		CompoundTag f = st.getCompoundOrEmpty("fx");
		int seq = f.getIntOr("seq", -1);
		if (seq != fxSeq) {
			fxSeq = seq;
			fxKind = f.getIntOr("kind", 0);
			fxStart = f.getLongOr("start", 0);
			fxSeats = f.getIntOr("seats", 1);
			fx = UthTableBlockEntity.timelineOf(fxKind, fxSeats, f.getIntOr("seed", 0));
			if (fxKind == UthPub.DEAL) roundKey = seq;
		}
		if (f.isEmpty()) fxKind = 0;
		long a = st.getLongOr("my_ante", 0);
		CompoundTag me = me();
		if (me != null) a = me.getLongOr("ante", a);
		if (a != shownAnte) {
			if (a > 0) {
				anteAt = nowMs();
				FxSounds.play("chip_place", 0.7f, 1f);
			}
			shownAnte = a;
		}
		celebrate(st);
		if (minecraft != null) rebuildConsole();
	}

	private void celebrate(CompoundTag st) {
		CompoundTag r = st.getCompoundOrEmpty("result");
		if (r.isEmpty() || celebrated == fxSeq) return;
		celebrated = fxSeq;
		try {
			WinTier tier = WinTier.valueOf(r.getStringOr("tier", "LOSS"));
			if (tier.isWin()) {
				CelebrationRequest req = CelebrationRequest.core(tier, r.getLongOr("ret", 0), Math.max(1, r.getLongOr("staked", 1)), SeedMix.mix(fxSeq, 7));
				CelebrationOverlay.get().play(req);
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

	private boolean started(String kind) {
		Beat b = beat(kind, kind.equals(UthBeats.QUALIFY) || kind.equals(UthBeats.REVEAL) || kind.equals(UthBeats.GATHER) ? -1 : 0);
		return b != null && fxMs() >= b.at();
	}

	// ---- console ------------------------------------------------------------------------------------------------

	@Override
	protected void buildConsole() {
		if (bankBox != null) {
			bankText = bankBox.getValue();
			removeWidget(bankBox);
			bankBox = null;
		}
		hoveredBet = null;
		List<CardButton> row = new ArrayList<>();
		CompoundTag bank = bank();
		boolean bankerView = iAmBanker();
		if (!seated() && !bankerView) {
			row.add(button(Component.translatable("gui.burmaldaholic.poker.sit_down"), "deal", CardButton.Family.PRIMARY, true, () -> sendAction("sit")));
		}
		if (seated() && betting() && !confirmedBet() && !bankerView) {
			long lastAnte = s().getLongOr("last_ante", 0);
			long lastTrips = s().getLongOr("last_trips", 0);
			row.add(button(Component.translatable("gui.burmaldaholic.common.clear"), "clear", CardButton.Family.TABLE, ante > 0 || trips > 0, () -> {
				ante = 0;
				trips = 0;
				rebuildConsole();
			}));
			if (lastAnte > 0) {
				row.add(button(Component.translatable("gui.burmaldaholic.common.rebet"), "rebet", CardButton.Family.TABLE, true, () -> {
					ante = lastAnte;
					trips = lastTrips;
					rebuildConsole();
				}));
			}
			row.add(button(Component.translatable("gui.burmaldaholic.common.deal"), "deal", CardButton.Family.PRIMARY, pendingValid(), this::deal));
		} else if (seated() && betting() && confirmedBet()) {
			row.add(button(Component.translatable("gui.burmaldaholic.common.clear"), "clear", CardButton.Family.TABLE, true, () -> sendAction("clear")));
		}
		List<String> legal = legal();
		if (!legal.isEmpty()) {
			CompoundTag me = me();
			long a = me == null ? 0 : me.getLongOr("ante", 0);
			for (Decision d : List.of(Decision.BET_4X, Decision.BET_3X, Decision.BET_2X, Decision.BET_1X, Decision.CHECK, Decision.FOLD)) {
				if (!legal.contains(d.id())) continue;
				long amount = d.multiple() * a;
				boolean affordable = amount <= balance();
				Component label = d.isBet() ? Component.translatable("gui.burmaldaholic.uth.button." + d.id()) : Component.translatable("gui.burmaldaholic.uth." + d.id());
				CardButton.Family fam = d.isBet() ? CardButton.Family.PRIMARY : d == Decision.FOLD ? CardButton.Family.DANGER : CardButton.Family.TABLE;
				String icon = d.isBet() ? "play" : d == Decision.FOLD ? "fold" : "check";
				Component tip = affordable ? Component.translatable("gui.burmaldaholic.uth." + d.id() + ".tooltip") : Component.translatable("gui.burmaldaholic.uth.unaffordable");
				CardButton b = button(label, icon, fam, affordable, () -> sendAction(d.id())).hint(tip);
				if (d.isBet()) b.setWidth(Math.max(b.getWidth(), compact() ? 50 : 64));
				row.add(b);
			}
		}
		if (playerBanked() && bank.getBooleanOr("enabled", false)) {
			if (bankerView) {
				boolean leaving = bank.getBooleanOr("leaving", false);
				row.add(button(Component.translatable(leaving ? "gui.burmaldaholic.uth.pvp.leaving_after_round" : "gui.burmaldaholic.uth.pvp.leave_seat"),
					"leave", CardButton.Family.TABLE, !leaving, () -> sendAction("leave_bank")));
			} else if (bank.getBooleanOr("can_take", false)) {
				if (bankForm) {
					row.add(button(Component.translatable("gui.burmaldaholic.common.cancel"), null, CardButton.Family.TABLE, true, () -> {
						bankForm = false;
						rebuildConsole();
					}));
					row.add(button(Component.translatable("gui.burmaldaholic.uth.pvp.take_seat_submit"), "take_bank", CardButton.Family.PRIMARY, true,
						this::takeBank));
				} else {
					row.add(button(Component.translatable("gui.burmaldaholic.uth.pvp.take_seat"), "take_bank", CardButton.Family.TABLE, true, () -> {
						bankForm = true;
						rebuildConsole();
					}).hint(Component.translatable("gui.burmaldaholic.uth.pvp.rules.1")));
				}
			}
		}
		if (s().getBooleanOr("bots_ui", false)) {
			row.add(button(Component.translatable("gui.burmaldaholic.bots.settings.open"), null, CardButton.Family.TABLE, true, () -> sendAction("bot_settings")));
		}
		if (legal.isEmpty()) {
			// the paytable panel is on the felt unless 4+ seats fold it into this button
			if (UthLayout.crowded(others().size()) || !rackShown()) {
				row.add(button(Component.translatable("gui.burmaldaholic.common.paytable"), "paytable", CardButton.Family.TABLE, true,
					() -> showPaytable = !showPaytable));
			}
			if (seated() && betting() && !rackShown()) {
				row.add(button(Component.translatable("gui.burmaldaholic.common.leave"), "leave", CardButton.Family.TABLE, true, () -> sendAction("leave")));
			}
		}
		// betting: the chip rack sits at the console's left; the buttons keep right of it
		int minX = seated() && betting() && !confirmedBet() && !bankerView ? 8 + CHIPS.length * 25 + 40 : compact() ? 0 : 150;
		layoutButtons(row, minX);
		if (bankForm && bank.getBooleanOr("can_take", false) && !bankerView) {
			long min = bank.getLongOr("min_bank", 0);
			int x = row.isEmpty() ? 8 : row.get(0).getX() - 70;
			bankBox = new EditBox(font, Math.max(4, x), row.isEmpty() ? 213 : row.get(0).getY(), 64, 20,
				Component.translatable("gui.burmaldaholic.uth.pvp.bank_amount", Texts.number(min)));
			bankBox.setMaxLength(12);
			bankBox.setValue(bankText.isEmpty() ? Long.toString(min) : bankText);
			bankBox.setResponder(v -> {
				if (!v.chars().allMatch(Character::isDigit)) bankBox.setValue(v.replaceAll("\\D", ""));
			});
			addWidget(bankBox);
		}
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

	private boolean rackShown() {
		return seated() && betting() && !confirmedBet() && !iAmBanker();
	}

	@Override
	protected boolean onCanvasClick(double x, double y, int button) {
		if (!rackShown() || button != 0) return false;
		int ry = compact() ? 138 : 211;
		for (int i = 0; i < CHIPS.length; i++) {
			int cx = (compact() ? 4 : 8) + i * 25;
			if (x >= cx && x < cx + 22 && y >= ry && y < ry + 22) {
				if (chipsToTrips) trips += CHIPS[i];
				else ante += CHIPS[i];
				FxSounds.play("chip_place", 0.6f, 1.1f);
				rebuildConsole();
				return true;
			}
		}
		// the Trips / Ante circles pick where the chips go
		for (int c = 0; c < 2; c++) {
			Rect r = mapped(UthLayout.circle(c));
			if (x >= r.x() && x < r.x() + r.w() && y >= r.y() && y < r.y() + r.h()) {
				chipsToTrips = c == 0 && s().getBooleanOr("trips_enabled", true);
				FxSounds.play("chip_count", 0.5f, 1f);
				return true;
			}
		}
		return false;
	}

	/** A table-layout rectangle in canvas coordinates (the compact layout scales the full one into its table). */
	private Rect mapped(Rect r) {
		if (!compact()) return r;
		float k = 280f / 408f;
		return new Rect(Math.round((r.x() - UthLayout.TABLE_X) * k) + tableX(), Math.round((r.y() - UthLayout.TABLE_Y) * k) + tableY(),
			Math.round(r.w() * k), Math.round(r.h() * k));
	}

	// ---- the scene ----------------------------------------------------------------------------------------------

	/** UTH card (poker encoding) → the kit's card code. */
	private static int kit(int card) {
		int r = UthCards.rank(card);
		return UthCards.suit(card) * 13 + (r == 14 ? 0 : r - 1);
	}

	private int backRow() {
		return tableTheme().back(CardSprites.BACK_EMERALD);
	}

	private String backName() {
		return switch (tableTheme()) {
			case BASTION -> "bastion";
			case END -> "end";
			default -> "emerald";
		};
	}

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

	@Override
	protected void drawScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		hovers.clear();
		int mx = mouseX;
		int my = mouseY;
		if (compact()) {
			float k = 280f / 408f;
			g.pose().pushMatrix();
			g.pose().translate(tableX(), tableY());
			g.pose().scale(k, k);
			g.pose().translate(-UthLayout.TABLE_X, -UthLayout.TABLE_Y);
			mx = Math.round((mouseX - tableX()) / k) + UthLayout.TABLE_X;
			my = Math.round((mouseY - tableY()) / k) + UthLayout.TABLE_Y;
		}
		List<CompoundTag> others = others();
		drawFelt(g, others.size());
		drawCards(g, others);
		drawMyChips(g);
		drawSeats(g, others, mx, my);
		drawLabels(g, others);
		if (showPaytable) drawPaytableOverlay(g);
		if (compact()) g.pose().popMatrix();
	}

	private void drawFelt(GuiGraphicsExtractor g, int others) {
		int print = tableTheme().printArgb(0.55);
		Rect rack = UthLayout.rack();
		CardGfx.sprite(g, tableTheme().themed("prop/rack"), rack.x(), rack.y(), rack.w(), rack.h(), 0xFFFFFFFF, 0xFF4A2A1A);
		Rect deck = UthLayout.deck(others);
		CardGfx.sprite(g, FxSprites.sprite("cards/prop/deck_" + backName()), deck.x(), deck.y(), deck.w(), deck.h(), 0xFFFFFFFF, 0xFF1A5A3A);
		boolean round = !players().isEmpty();
		if (!round) {
			for (int i = 0; i < 5; i++) {
				Rect b = UthLayout.board(i);
				CardGfx.sprite(g, FxSprites.sprite("cards/print/slot_l"), b.x(), b.y(), b.w(), b.h(), CardGfx.alpha(print, 0.3 / 0.55));
			}
		}
		// the viewer's circles: ring style carries the meaning (dotted Trips, solid Ante, double Blind, dashed Play)
		String[] prints = {"uth_trips", "uth_ante", "uth_blind", "uth_play"};
		String[] labels = {"trips", "ante", "blind", "play"};
		boolean tripsOn = s().getBooleanOr("trips_enabled", true);
		for (int c = 0; c < 4; c++) {
			if (c == 0 && !tripsOn) continue;
			Rect r = UthLayout.circle(c);
			boolean target = rackShown() && ((c == 0) == chipsToTrips) && c <= 1;
			if (target) CardGfx.sprite(g, FxSprites.sprite("cards/fx/spot_glow"), r.x() - 5, r.y() - 5, 34, 34, CardGfx.white(0.55));
			CardGfx.sprite(g, FxSprites.sprite("cards/print/" + prints[c]), r.x(), r.y(), r.w(), r.h(), print);
			int[] l = UthLayout.circleLabel(c);
			Component label = Component.translatable("gui.burmaldaholic.uth." + labels[c]);
			CardGfx.fitted(g, font, label, l[0] - Math.min(font.width(label), 40) / 2, l[1], 40, tableTheme().print, false);
		}
		if (!UthLayout.crowded(others)) drawPaytablePanel(g);
		if (playerBanked()) drawBankLine(g);
	}

	/** The Blind paytable panel (top-left of the felt): the three best rows, a paying row outlined in gold. */
	private void drawPaytablePanel(GuiGraphicsExtractor g) {
		Rect p = UthLayout.paytable();
		CardGfx.sprite(g, FxSprites.sprite("cards/panel/road"), p.x(), p.y(), p.w(), p.h(), 0xFFFFFFFF, 0xE0180A28);
		CardGfx.fitted(g, font, Component.translatable("gui.burmaldaholic.uth.paytable.blind_short"), p.x() + 5, p.y() + 4, p.w() - 10,
			CasinoPalette.GOLD, true);
		CompoundTag pays = s().getCompoundOrEmpty("pays");
		String paying = s().getCompoundOrEmpty("result").getStringOr("pay_hand", "");
		int y = p.y() + 14;
		int rows = 0;
		for (PayHand h : PayHand.values()) {
			if (h == PayHand.NONE || rows >= 3) continue;
			double bp = pays.getDoubleOr("blind_" + h.key(), 0);
			if (bp <= 0) continue;
			Component name = handName(h.handName());
			Component ratio = ratio(bp);
			int rw = font.width(ratio);
			if (h.key().equals(paying)) CardGfx.frame(g, p.x() + 3, y - 1, p.w() - 6, 10, CasinoPalette.GOLD);
			CardGfx.fitted(g, font, name, p.x() + 5, y, p.w() - 16 - rw, CasinoPalette.BONE, true);
			CardGfx.text(g, font, ratio, p.x() + p.w() - 5 - rw, y, CasinoPalette.BONE, true);
			y += 10;
			rows++;
		}
		hovers.add(new Hover(p.x(), p.y(), p.w(), p.h(), Component.translatable("gui.burmaldaholic.uth.paytable.blind")));
	}

	private void drawBankLine(GuiGraphicsExtractor g) {
		CompoundTag b = bank();
		Component who = b.contains("name") ? Texts.raw(b.getStringOr("name", ""))
			: b.contains("stand_in") ? botName(b.getStringOr("stand_in", "")) : Component.translatable("gui.burmaldaholic.uth.pvp.the_house");
		MutableComponent line = Component.translatable("gui.burmaldaholic.uth.pvp.dealer_seat", who);
		if (b.contains("bank")) {
			line.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.pvp.bank", Texts.number(b.getLongOr("bank", 0)),
				Texts.number(b.getLongOr("reserved", 0))));
		}
		Rect rack = UthLayout.rack();
		CardGfx.fitted(g, font, line, rack.x() + rack.w() + 6, rack.y() + 3, 150, CasinoPalette.GOLD, true);
	}

	private void drawCards(GuiGraphicsExtractor g, List<CompoundTag> others) {
		Rect deck = UthLayout.deck(others.size());
		int[] origin = UthLayout.dealOrigin(others.size());
		cards.origin(origin[0] - 18, origin[1] - 24).tray(deck.x(), deck.y());
		cards.begin(nowMs(), FxSettings.reduceMotion());
		int back = backRow();
		int key = Math.floorMod(roundKey, 100) * 1000;
		List<CompoundTag> ps = players();
		boolean round = !ps.isEmpty();
		boolean gathered = fxKind == UthPub.RESULT && started(UthBeats.GATHER);
		if (round && !gathered) {
			boolean dealing = fxKind == UthPub.DEAL;
			int[] dealer = s().getIntArray("dealer").orElse(NO_CARDS);
			int dealerBest = s().getIntOr("dealer_best", 0);
			for (int k = 0; k < 2; k++) {
				Rect r = UthLayout.dealer(k);
				int code = k < dealer.length ? kit(dealer[k]) : -1;
				double dealAt = dealing ? beatAt(UthBeats.DEALER_DEAL, k) : Double.NaN;
				double revealAt = fxKind == UthPub.SHOWDOWN ? beatAt(UthBeats.DEALER_FLIP, k) : Double.NaN;
				CardAnimator.Slot sl = cards.card(key + 10 + k, code, CardSprites.L, back, r.x(), r.y(), 0, dealAt, revealAt);
				if (code >= 0 && s().getBooleanOr("settled", false)) cards.dim(sl, (dealerBest & (1 << k)) == 0);
			}
			int[] board = s().getIntArray("board").orElse(NO_CARDS);
			int myBest = s().getIntOr("my_best", 0);
			boolean resultShown = s().getBooleanOr("settled", false);
			for (int i = 0; i < 5; i++) {
				Rect r = UthLayout.board(i);
				int code = i < board.length ? kit(board[i]) : -1;
				double dealAt = dealing ? beatAt(UthBeats.BOARD, i) : Double.NaN;
				double revealAt = fxKind == UthPub.FLOP || fxKind == UthPub.RIVER ? beatAt(UthBeats.FLIP, i) : Double.NaN;
				CardAnimator.Slot sl = cards.card(key + 20 + i, code, CardSprites.L, back, r.x(), r.y(), 0, dealAt, revealAt);
				if (resultShown && myBest != 0) {
					boolean in = (myBest & (1 << (2 + i))) != 0;
					cards.lift(sl, in);
					cards.glow(sl, in);
				}
				if (code >= 0) hovers.add(new Hover(r.x(), r.y(), r.w(), r.h(), CardSprites.narration(code)));
			}
			CompoundTag me = me();
			int lane = myLane();
			if (me != null) {
				int[] mine = me.getIntArray("cards").orElse(NO_CARDS);
				boolean folded = "folded".equals(me.getStringOr("tag", ""));
				if (!folded) { // a river fold mucks the viewer's cards (K6: they gather to the deck)
					for (int k = 0; k < 2; k++) {
						Rect r = UthLayout.hole(k);
						int code = k < mine.length ? kit(mine[k]) : -1;
						double dealAt = dealing ? beatAt(UthBeats.DEAL, lane, k) : Double.NaN;
						double revealAt = dealing ? beatAt(UthBeats.LIFT, lane) : Double.NaN;
						CardAnimator.Slot sl = cards.card(key + 100 + k, code, CardSprites.L, back, r.x(), r.y(), 0, dealAt, revealAt);
						if (resultShown && myBest != 0) {
							boolean in = (myBest & (1 << k)) != 0;
							cards.lift(sl, in);
							cards.glow(sl, in);
						}
						if (code >= 0) hovers.add(new Hover(r.x(), r.y(), r.w(), r.h(), CardSprites.narration(code)));
					}
				}
			}
			UthLayout.Seat[] slots = UthLayout.others(others.size());
			for (int i = 0; i < others.size(); i++) {
				CompoundTag p = others.get(i);
				if (p.getBooleanOr("idle_bot", false) || "folded".equals(p.getStringOr("tag", ""))) continue;
				int pl = laneOf(ps, p);
				int[] theirs = p.getIntArray("cards").orElse(NO_CARDS);
				for (int k = 0; k < 2; k++) {
					Rect r = slots[i].card(k);
					int code = k < theirs.length ? kit(theirs[k]) : -1;
					double dealAt = dealing ? beatAt(UthBeats.DEAL, pl, k) : Double.NaN;
					double revealAt = fxKind == UthPub.SHOWDOWN ? beatAt(UthBeats.REVEAL, -1) : Double.NaN;
					CardAnimator.Slot sl = cards.card(key + 200 + p.getIntOr("seat", i) * 10 + k, code, CardSprites.M, back, r.x(), r.y(), 0, dealAt,
						revealAt);
					if (resultShown && p.getLongOr("net", 0) < 0) cards.dim(sl, true);
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

	private double beatAt(String kind, int lane, int arg) {
		if (fx == null) return Double.NaN;
		for (Beat b : fx.beats()) if (b.kind().equals(kind) && b.lane() == lane && b.arg(0) == arg) return fxStart * 50.0 + b.at();
		return Double.NaN;
	}

	/** The viewer's four stacks; the Blind mirrors the Ante 120 ms later (a 34 px slide); settle beats move them. */
	private void drawMyChips(GuiGraphicsExtractor g) {
		CompoundTag me = me();
		long a;
		long t;
		int play;
		boolean pendingBet = false;
		if (me != null) {
			a = me.getLongOr("ante", 0);
			t = me.getLongOr("trips", 0);
			play = me.getIntOr("play", 0);
		} else if (seated() && betting() && !iAmBanker()) {
			boolean conf = confirmedBet();
			a = conf ? s().getLongOr("my_ante", 0) : ante;
			t = conf ? s().getLongOr("my_trips", 0) : trips;
			play = 0;
			pendingBet = !conf;
		} else {
			return;
		}
		long[] amounts = {t, a, a, (long) play * a};
		if (me != null && "folded".equals(me.getStringOr("tag", ""))) {
			amounts[1] = 0; // a fold sweeps Ante and Blind at once; Trips still settles
			amounts[2] = 0;
		}
		long[] nets = me == null ? null : me.getLongArray("circles").orElse(null);
		boolean settling = fxKind == UthPub.SHOWDOWN || fxKind == UthPub.RESULT;
		int lane = myLane();
		double now = nowMs();
		// bonus stamps never cover a card (board, hole cards) nor each other: placed below / above their circle
		List<Rect> stampHard = new ArrayList<>(UthLayout.cardRects(0));
		for (int c = 0; c < 4; c++) stampHard.add(UthLayout.circle(c));
		for (int c = 0; c < 4; c++) {
			long amount = amounts[c];
			Rect r = UthLayout.circle(c);
			int cx = r.cx();
			int base = r.y() + r.h() - 4;
			if (c == 3 && amount == 0 && hoveredBet != null && hoveredBetAmount > 0) {
				// the ghost Play stack of the hovered Bet button (45 %, glow, amount)
				CardGfx.sprite(g, FxSprites.sprite("cards/fx/spot_glow"), r.x() - 5, r.y() - 5, 34, 34, CardGfx.white(0.8));
				ChipStackView.stack(g, cx, base, hoveredBetAmount, 5, 0, false, 0.45);
				Component plus = Component.empty().append(Texts.raw("+")).append(Texts.number(hoveredBetAmount));
				CardGfx.text(g, font, plus, r.x() + r.w() + 3, r.y() + 8, CardGfx.alpha(CasinoPalette.GOLD, 0.9), true);
				continue;
			}
			if (amount <= 0) continue;
			double alpha = pendingBet ? 0.75 : 1;
			int dx = 0;
			if (c == 2 && !Double.isNaN(anteAt) && !FxSettings.reduceMotion()) {
				// Blind = Ante, shown as a copy sliding from the Ante circle 120 ms later
				double u = Math.min(1, Math.max(0, (now - anteAt - 120) / 220.0));
				if (u <= 0) continue;
				dx = (int) Math.round(-UthLayout.circlePitch() * (1 - Ease.OUT_CUBIC.apply(u)));
			}
			if (settling && nets != null && fx != null && lane >= 0) {
				Beat b = null;
				for (Beat x : fx.beats()) if (x.kind().equals(UthBeats.SETTLE) && x.lane() == lane && x.arg(0) == c) b = x;
				double u = fxKind == UthPub.RESULT ? 1 : b == null ? 1 : b.progress(fxMs());
				long net = nets[c];
				boolean gather = fxKind == UthPub.RESULT && started(UthBeats.GATHER);
				if (gather) continue;
				if (net < 0 && u > 0) {
					// lost: swept to the rack
					Rect rack = UthLayout.rack();
					CardMotion.sweep(pose, cx, base, rack.cx(), rack.y() + 8, u, FxSettings.reduceMotion());
					if (pose.alpha > 0.02) ChipStackView.stack(g, (int) Math.round(pose.x), (int) Math.round(pose.y), amount, 5, 0, false, pose.alpha);
					continue;
				}
				if (net > 0 && u > 0) {
					// won: the payout pops in beside the stack (+14 px) from the rack
					Rect rack = UthLayout.rack();
					CardMotion.chipFlight(pose, rack.cx(), rack.y() + 8, cx + 14, base + 2, u, FxSettings.reduceMotion());
					ChipStackView.stack(g, (int) Math.round(pose.x), (int) Math.round(pose.y), net, 5, 0, false, pose.alpha);
					if ((c == 0 || c == 2) && u >= 1 && s().getBooleanOr("settled", false)) {
						Component word = Component.translatable(c == 0 ? "gui.burmaldaholic.uth.fx.trips_bonus" : "gui.burmaldaholic.uth.fx.blind_bonus");
						double age = b == null ? 1000 : fxMs() - b.end();
						if (fxKind == UthPub.RESULT) age = 1000;
						int sw = CardGfx.fittedWidth(font, word, TableStamp.MAX_TEXT_W) + 18;
						Rect spot = LabelPlacer.place(stampHard, List.of(), UthLayout.labelBounds(), List.of(new LabelPlacer.Request(sw, 18,
							cx - sw / 2, r.y() + r.h() + 2, cx - sw / 2, r.y() - 20))).get(0);
						stampHard.add(spot);
						TableStamp.draw(g, font, word, TableStamp.Kind.VIOLET, spot.cx(), spot.cy(), -4, age, FxSettings.reduceMotion(), pose);
					}
				}
				if (net == 0 && u > 0 && u < 1 && !FxSettings.reduceMotion()) {
					dx += (int) Math.round(Math.sin(u * Math.PI * 4) * 1.2); // push: wiggle, stays
				}
			}
			ChipStackView.stack(g, cx + dx, base, amount, 5, 0, false, alpha);
			hovers.add(new Hover(r.x(), r.y(), r.w(), r.h(), Texts.number(amount)));
		}
	}

	private void drawSeats(GuiGraphicsExtractor g, List<CompoundTag> others, int mx, int my) {
		UthLayout.Seat[] slots = UthLayout.others(others.size());
		boolean resultShown = s().getBooleanOr("settled", false);
		plates.clear();
		for (int i = 0; i < others.size(); i++) {
			CompoundTag p = others.get(i);
			UthLayout.Seat sl = slots[i];
			boolean bot = p.getBooleanOr("bot", false);
			String botId = p.getStringOr("bot_name", "");
			Component name = bot ? Component.translatable(BotRoster.nameKey(botId)) : Texts.raw(p.getStringOr("name", ""));
			int level = switch (p.getStringOr("bot_level", "")) {
				case "easy" -> 1;
				case "normal" -> 2;
				case "hard" -> 3;
				default -> 0;
			};
			String tag = p.getStringOr("tag", "");
			boolean thinking = bot && ("deciding".equals(tag) || "thinking".equals(p.getStringOr("bot_state", "")));
			Component sub;
			int subColor = CasinoPalette.GOLD;
			if (resultShown && p.contains("net")) {
				long net = p.getLongOr("net", 0);
				sub = net > 0 ? Component.empty().append(Texts.raw("+")).append(Texts.number(net))
					: net < 0 ? Component.empty().append(Texts.raw("−")).append(Texts.number(-net)) : Component.translatable("gui.burmaldaholic.common.result.push");
				subColor = net > 0 ? GREEN : net < 0 ? CasinoPalette.CHIP_RED_LIGHT : MUTED;
			} else if ("play".equals(tag)) {
				sub = Component.translatable("gui.burmaldaholic.uth.tag.play", Texts.number(p.getIntOr("play", 0)));
			} else if (!tag.isEmpty()) {
				sub = Component.translatable("gui.burmaldaholic.uth.tag." + tag);
				subColor = MUTED;
			} else if ("watching".equals(p.getStringOr("bot_state", ""))) {
				sub = Component.translatable("gui.burmaldaholic.bots.watching");
				subColor = MUTED;
			} else if (p.getLongOr("ante", 0) > 0) {
				sub = Component.translatable("gui.burmaldaholic.uth.ante_amount", Texts.number(p.getLongOr("ante", 0)));
			} else {
				sub = Component.empty();
			}
			SeatPlate.State st = "folded".equals(tag) ? SeatPlate.State.FOLDED
				: resultShown && p.getLongOr("net", 0) > 0 ? SeatPlate.State.WINNER : "deciding".equals(tag) ? SeatPlate.State.ACTIVE : SeatPlate.State.NORMAL;
			Component shown = font.width(name) <= 62 ? name : Texts.raw(font.plainSubstrByWidth(name.getString(), 62 - font.width("…")) + "…");
			SeatPlate.Info info = new SeatPlate.Info(shown, bot ? null : p.getStringOr("name", ""), bot ? tableTheme().botAvatar(botId) : null, level, sub,
				subColor, st, thinking);
			int w = SeatPlate.width(font, info);
			int px = Math.min(sl.plateX(), UthLayout.CANVAS_W - 2 - w);
			// the combined stack (sum of the circles), atmosphere bots hatched, before the plate
			long total = 2 * p.getLongOr("ante", 0) + p.getLongOr("trips", 0) + (long) p.getIntOr("play", 0) * p.getLongOr("ante", 0);
			if (total > 0 && !(fxKind == UthPub.RESULT && started(UthBeats.GATHER))) {
				ChipStackView.stack(g, sl.stackX(), sl.stackY(), total, 5, bot ? 0 : ChipStackView.seatTint(p.getIntOr("seat", i)), bot, 1);
				hovers.add(new Hover(sl.stackX() - 7, sl.stackY() - 16, 14, 16, Texts.number(total)));
			}
			SeatPlate.draw(g, font, info, px, sl.plateY(), 1, 0);
			plates.add(new Rect(px, sl.plateY(), w, UthLayout.PLATE_H));
			MutableComponent tip = Component.empty().append(bot ? Component.translatable("gui.burmaldaholic.bots.display", name) : name);
			if (level > 0) {
				BotDifficulty d = level == 1 ? BotDifficulty.EASY : level == 2 ? BotDifficulty.NORMAL : BotDifficulty.HARD;
				tip.append(Texts.raw(" · ")).append(Component.translatable(d.translationKey()));
			}
			if (bot) tip.append(Texts.raw("\n")).append(Component.translatable("gui.burmaldaholic.bots.virtual_tooltip"));
			hovers.add(new Hover(px, sl.plateY(), w, UthLayout.PLATE_H, tip));
		}
	}

	/** The viewer's hand name, the others' hand names at the showdown and the qualify banner — never over a card. */
	private void drawLabels(GuiGraphicsExtractor g, List<CompoundTag> others) {
		List<Rect> hard = new ArrayList<>(UthLayout.cardRects(others.size()));
		for (int c = 0; c < 4; c++) hard.add(UthLayout.circle(c));
		hard.addAll(plates);
		List<LabelPlacer.Request> reqs = new ArrayList<>();
		List<Component> texts = new ArrayList<>();
		String myHand = s().getStringOr("my_hand", "");
		boolean showdown = s().getIntArray("dealer").isPresent() && (fxKind != UthPub.SHOWDOWN || started(UthBeats.REVEAL));
		if (!myHand.isEmpty() && me() != null) {
			Component t = handName(myHand);
			reqs.add(UthLayout.viewerTag(font.width(t) + 8));
			texts.add(t);
		}
		UthLayout.Seat[] slots = UthLayout.others(others.size());
		for (int i = 0; i < others.size() && showdown; i++) {
			CompoundTag p = others.get(i);
			String hand = p.getStringOr("hand", "");
			if (hand.isEmpty() || "folded".equals(p.getStringOr("tag", ""))) continue;
			Component t = handName(hand);
			reqs.add(UthLayout.seatTag(slots[i], 60, font.width(t) + 8));
			texts.add(t);
		}
		List<Rect> placed = LabelPlacer.place(hard, List.of(), UthLayout.labelBounds(), reqs);
		for (int i = 0; i < placed.size(); i++) {
			ActionTag.draw(g, font, texts.get(i), placed.get(i).cx(), placed.get(i).y(), 1);
			hard.add(placed.get(i));
		}
		// the qualify banner slides down from the dealer area at its beat (the same pause either way)
		if (s().getIntArray("dealer").isPresent()) {
			double age = fxKind == UthPub.SHOWDOWN && beat(UthBeats.QUALIFY, -1) != null ? fxMs() - beat(UthBeats.QUALIFY, -1).at() : 10_000;
			if (age >= 0) {
				boolean q = s().getBooleanOr("qualifies", false);
				Component t = Component.translatable(q ? "gui.burmaldaholic.uth.qualifies" : "gui.burmaldaholic.uth.not_qualifies");
				Rect d = UthLayout.dealer(1);
				int w = font.width(t) + 14;
				Rect spot = LabelPlacer.place(hard, List.of(), UthLayout.labelBounds(),
					List.of(new LabelPlacer.Request(w, 16, d.x() + d.w() + 8, d.y() + 16, d.x() + d.w() + 8, d.y() + 2))).get(0);
				TableStamp.draw(g, font, t, q ? TableStamp.Kind.GREEN : TableStamp.Kind.RED, spot.cx(), spot.cy(), 0, age, FxSettings.reduceMotion(), pose);
			}
		}
	}

	private void drawPaytableOverlay(GuiGraphicsExtractor g) {
		int x = UthLayout.TABLE_X + 40;
		int y = UthLayout.TABLE_Y + 20;
		int w = 328;
		int h = 150;
		CardGfx.sprite(g, FxSprites.sprite("cards/panel/road"), x, y, w, h, 0xF8FFFFFF, 0xF0180A28);
		CompoundTag pays = s().getCompoundOrEmpty("pays");
		int colW = (w - 18) / 2;
		int x1 = x + 6;
		int x2 = x1 + colW + 6;
		int bottom = y + h - 4;
		int y1 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.blind"), x1, y + 5, colW, CasinoPalette.GOLD, bottom);
		int y2 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.trips"), x2, y + 5, colW, CasinoPalette.GOLD, bottom);
		for (PayHand ph : PayHand.values()) {
			if (ph == PayHand.NONE) continue;
			double bp = pays.getDoubleOr("blind_" + ph.key(), 0);
			if (bp > 0) y1 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.row", handName(ph.handName()), ratio(bp)), x1, y1, colW,
				CasinoPalette.BONE, bottom);
			int tp = pays.getIntOr("trips_" + ph.key(), 0);
			if (tp > 0) y2 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.row", handName(ph.handName()), ratio(tp)), x2, y2, colW,
				CasinoPalette.BONE, bottom);
		}
		y1 = wrapped(g, Component.translatable("gui.burmaldaholic.uth.paytable.blind_lower"), x1, y1, colW, MUTED, bottom);
		ListTag levels = s().getListOrEmpty("bot_levels");
		for (int i = 0; i < levels.size(); i++) {
			BotDifficulty lvl = BotDifficulty.byId(levels.getStringOr(i, ""), BotDifficulty.NORMAL);
			Component line = Component.empty().append(Component.translatable(lvl.translationKey())).append(Texts.raw(": "))
				.append(Component.translatable("gui.burmaldaholic.bots.uth_edge", Texts.raw(UthBotPolicy.edgePercent(lvl))));
			y1 = wrapped(g, line, x1, y1 + (i == 0 ? 2 : 0), colW, MUTED, bottom);
		}
		if (playerBanked()) {
			CompoundTag b = bank();
			String pct = UthClientModule.trimPercent(b.getDoubleOr("rake", 0) * 100);
			wrapped(g, Component.translatable("gui.burmaldaholic.uth.pvp.rules.2", Texts.number(b.getLongOr("ante_factor", 505)),
				Texts.number(b.getLongOr("trips_factor", 50)), Texts.decimal(pct)), x2, y2 + 2, colW, MUTED, bottom);
		}
	}

	private int wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color, int bottom) {
		for (FormattedCharSequence l : font.split(text, w)) {
			if (y + 9 > bottom) return y;
			CardGfx.text(g, font, l, x, y, color, true);
			y += 10;
		}
		return y;
	}

	// ---- console text, overlay, tooltips ------------------------------------------------------------------------

	@Override
	protected void drawConsoleText(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		hoveredBet = null;
		hoveredBetAmount = 0;
		CompoundTag me = me();
		for (CardButton b : buttons()) {
			boolean preview = previewBet != null && b.getMessage().getString().equals(Component.translatable("gui.burmaldaholic.uth.button." + previewBet).getString());
			if ((b.isMouseOver(mouseX, mouseY) || preview) && b.active && me != null) {
				for (Decision d : Decision.values()) {
					if (d.isBet() && b.getMessage().getString().equals(Component.translatable("gui.burmaldaholic.uth.button." + d.id()).getString())) {
						hoveredBet = b;
						hoveredBetAmount = d.multiple() * me.getLongOr("ante", 0);
					}
				}
			}
		}
		int x = compact() ? 4 : 8;
		int y1 = compact() ? 141 : 212;
		if (rackShown()) {
			int ry = compact() ? 138 : 211;
			for (int i = 0; i < CHIPS.length; i++) {
				int cx = x + i * 25;
				boolean hover = mouseX >= cx && mouseX < cx + 22 && mouseY >= ry && mouseY < ry + 22;
				ChipStackView.big(g, CHIPS[i], cx, ry, false, hover, true);
			}
			Component where = Component.translatable(chipsToTrips ? "gui.burmaldaholic.uth.trips" : "gui.burmaldaholic.uth.ante");
			long at = 6 * ante + trips;
			Component line = Component.empty().append(where).append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.at_risk",
				Texts.number(at)));
			int lx = x + CHIPS.length * 25 + 2;
			CardGfx.fitted(g, font, line, lx, ry + 7, Math.max(20, consoleTextRight() - lx), MUTED, true);
			return;
		}
		int maxW = Math.max(40, consoleTextRight() - x);
		Component l1 = null;
		Component l2 = null;
		int c1 = CasinoPalette.BONE;
		List<String> legal = legal();
		CompoundTag res = s().getCompoundOrEmpty("result");
		if (!legal.isEmpty()) {
			boolean allow3x = s().getBooleanOr("allow3x", true);
			l1 = Component.translatable(legal.contains("bet_4x") ? allow3x ? "gui.burmaldaholic.uth.hint.preflop" : "gui.burmaldaholic.uth.hint.preflop_no3x"
				: legal.contains("bet_2x") ? "gui.burmaldaholic.uth.hint.flop" : "gui.burmaldaholic.uth.hint.river");
			long a = me == null ? 0 : me.getLongOr("ante", 0);
			long t = me == null ? 0 : me.getLongOr("trips", 0);
			MutableComponent l = Component.translatable("gui.burmaldaholic.uth.at_risk_now", Texts.number(2 * a + t));
			long sec = secondsLeft("decide");
			if (sec >= 0) l.append(Texts.raw(" · ")).append(Component.translatable("gui.burmaldaholic.uth.seconds_short", Texts.number(sec)));
			l2 = l;
		} else if (!res.isEmpty()) {
			l1 = resultLine(res);
			c1 = res.getLongOr("net", 0) > 0 ? GREEN : res.getLongOr("net", 0) < 0 ? CasinoPalette.CHIP_RED_LIGHT : MUTED;
			l2 = perBetLine(res);
		} else if (iAmBanker()) {
			CompoundTag b = bank();
			if (b.contains("last_result") && ("result".equals(phase()) || betting())) {
				long r = b.getLongOr("last_result", 0);
				l1 = Component.translatable("gui.burmaldaholic.uth.pvp.round_result", Component.empty().append(Texts.raw(r >= 0 ? "+" : "−"))
					.append(Texts.number(Math.abs(r))), Texts.number(b.getLongOr("last_rake", 0)));
			} else {
				l1 = Component.translatable("gui.burmaldaholic.uth.pvp.you_bank");
			}
		} else if (s().getBooleanOr("waiting_next", false) && !betting()) {
			l1 = Component.translatable("gui.burmaldaholic.uth.waiting_next");
		} else if (me != null && me.getIntOr("play", 0) > 0) {
			l1 = Component.translatable("gui.burmaldaholic.uth.waiting_showdown",
				Component.translatable("gui.burmaldaholic.uth.play_multiple", Texts.number(me.getIntOr("play", 0))));
			if (s().getIntOr("deciding", 0) > 0) l2 = Component.translatable("gui.burmaldaholic.uth.still_deciding", Texts.number(s().getIntOr("deciding", 0)));
		} else if (s().getIntOr("deciding", 0) > 0) {
			l1 = Component.translatable("gui.burmaldaholic.uth.still_deciding", Texts.number(s().getIntOr("deciding", 0)));
		} else if (betting()) {
			long sec = secondsLeft("bet");
			if (sec >= 0) l1 = Component.translatable("msg.burmaldaholic.uth.round_starts_in", Texts.plural("unit.burmaldaholic.second_acc", sec));
			else if (!seated()) {
				int free = s().getIntOr("seat_count", 0) - s().getListOrEmpty("seats").size();
				l1 = Texts.plural("gui.burmaldaholic.uth.seats_free", Math.max(0, free));
			}
			if (confirmedBet()) l2 = Component.translatable("gui.burmaldaholic.uth.at_risk", Texts.number(6 * s().getLongOr("my_ante", 0) + s().getLongOr("my_trips", 0)));
			else if (!seated()) l2 = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		}
		if (s().getBooleanOr("bots_pending", false) && betting() && l2 == null) l2 = Component.translatable("gui.burmaldaholic.bots.pending");
		if (l1 != null) CardGfx.fitted(g, font, l1, x, y1, maxW, c1, true);
		if (l2 != null) CardGfx.fitted(g, font, l2, x, y1 + 12, maxW, MUTED, true);
		if (l1 != null && font.width(l1) > maxW) hovers.add(new Hover(x, y1, maxW, 10, l1));
		if (l2 != null && font.width(l2) > maxW) hovers.add(new Hover(x, y1 + 12, maxW, 10, l2));
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

	/** "500:1", "1.5:1" (decimal separator per language). */
	private static Component ratio(double pay) {
		String plain = pay == Math.rint(pay) ? Long.toString((long) pay) : String.format(java.util.Locale.ROOT, "%.2f", pay).replaceAll("0+$", "");
		return Component.empty().append(Texts.decimal(plain)).append(Texts.raw(":1"));
	}

	private static Component botName(String nameId) {
		return Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(BotRoster.nameKey(nameId)));
	}

	private static Component handName(String name) {
		return name.isEmpty() ? Component.empty() : Component.translatable("gui.burmaldaholic.poker.hand." + name);
	}

	@Override
	protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		if (bankBox != null) bankBox.extractRenderState(g, mouseX, mouseY, partial);
	}

	@Override
	protected void drawTooltips(GuiGraphicsExtractor g, int mx, int my, int sx, int sy) {
		int hx = mx;
		int hy = my;
		if (compact()) {
			float k = 280f / 408f;
			hx = Math.round((mx - tableX()) / k) + UthLayout.TABLE_X;
			hy = Math.round((my - tableY()) / k) + UthLayout.TABLE_Y;
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
