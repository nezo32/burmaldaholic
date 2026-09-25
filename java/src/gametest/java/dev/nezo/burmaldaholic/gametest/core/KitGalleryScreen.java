package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.table.cards.ActionTag;
import dev.nezo.burmaldaholic.client.table.cards.CardAnimator;
import dev.nezo.burmaldaholic.client.table.cards.CardButton;
import dev.nezo.burmaldaholic.client.table.cards.CardSprites;
import dev.nezo.burmaldaholic.client.table.cards.ChipStackView;
import dev.nezo.burmaldaholic.client.table.cards.SeatPlate;
import dev.nezo.burmaldaholic.client.table.cards.TableStamp;
import dev.nezo.burmaldaholic.client.table.cards.TableTheme;
import dev.nezo.burmaldaholic.client.ui.BookmarkTab;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Tester gallery (J-L2 / J-L4 kits): every kit widget of both kits on one {@link CasinoScreen} in the given theme —
 * CasinoButton styles and states, bookmark tabs, panels / plates / rows / bars, the card kit's CardButton families,
 * faces and backs in L / M / S with glow / dim / lift, seat plates, chip stacks, tags, badges and stamps, and the error
 * line — with the longest Russian strings of the lang files in fixed boxes (ellipsis / shrink, never overflow).
 */
public class KitGalleryScreen extends CasinoScreen {
	static final int W = 620;
	static final int H = 390;
	final List<AbstractWidget> kit = new ArrayList<>();
	int presses;
	private final CasinoTheme scene;
	private final CardAnimator cards = new CardAnimator().sounds(false);
	private final CardMotion.Pose pose = new CardMotion.Pose();

	public KitGalleryScreen(CasinoTheme scene) {
		super(Component.translatable("gui.burmaldaholic.baccarat.rules.2"), W, H);
		this.scene = scene;
		setTheme(scene);
	}

	private static Component t(String key) {
		return Component.translatable(key);
	}

	private <T extends AbstractWidget> T widget(T w) {
		kit.add(w);
		return add(w);
	}

	@Override
	protected void init() {
		super.init();
		kit.clear();
		TableTheme table = TableTheme.of(scene);
		int x = px(16);
		int y = py(34);
		Component deal = t("gui.burmaldaholic.common.deal");
		widget(new CasinoButton(x, y, CasinoButton.width(font, deal, 50, false), 20, deal, CasinoButton.Style.PRIMARY, b -> presses++));
		Component hit = t("gui.burmaldaholic.blackjack.hit");
		widget(new CasinoButton(x + 66, y, 60, 20, hit, CasinoButton.Style.SECONDARY, b -> presses++));
		Component stand = t("gui.burmaldaholic.blackjack.stand");
		widget(new CasinoButton(x + 130, y, 60, 20, stand, CasinoButton.Style.DANGER, b -> presses++));
		widget(new CasinoButton(x + 194, y, 60, 20, t("gui.burmaldaholic.blackjack.split"), CasinoButton.Style.SECONDARY, b -> presses++))
			.enabled(false, t("gui.burmaldaholic.blackjack.split.tooltip"));
		widget(new CasinoButton(x + 258, y, 60, 20, t("gui.burmaldaholic.blackjack.double"), CasinoButton.Style.SECONDARY, b -> presses++)).selected(true);
		// the longest RU strings in a fixed 70 px box: ellipsis, never past the button
		widget(new CasinoButton(x + 322, y, 70, 20, t("gui.burmaldaholic.blackjack.even_money_prompt"), CasinoButton.Style.SECONDARY, b -> presses++));
		widget(CasinoButton.builder(t("gui.burmaldaholic.menu.wallet"), b -> presses++).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.CASHIER))
			.bounds(x + 396, y, 90, 20).build());
		widget(CasinoButton.builder(Component.empty(), b -> presses++).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.RULES))
			.tooltip(t("gui.burmaldaholic.menu.rules")).bounds(x + 490, y, 20, 20).build());
		widget(new CasinoButton(x + 516, y - 10, 40, 40, deal, CasinoButton.Style.ACTION, b -> presses++));
		widget(new CasinoButton(x + 560, y - 10, 40, 40, deal, CasinoButton.Style.ACTION, b -> presses++)).enabled(false, null);

		// bookmark tabs: two plain, one selected + labelled, a loan tab
		int ty = py(62);
		widget(new BookmarkTab(x, ty, 26, 20, t("gui.burmaldaholic.menu.wallet"), UiSprites.TabIcon.WALLET, false, b -> presses++));
		widget(new BookmarkTab(x + 28, ty, 26, 20, t("gui.burmaldaholic.menu.contracts"), UiSprites.TabIcon.CONTRACTS, false, b -> presses++));
		Component ach = t("gui.burmaldaholic.menu.achievements");
		widget(new BookmarkTab(x + 56, ty - 2, BookmarkTab.labelledWidth(font, ach), 22, ach, UiSprites.TabIcon.ACHIEVEMENTS, false, b -> presses++))
			.select(true, true);
		widget(new BookmarkTab(x + 62 + BookmarkTab.labelledWidth(font, ach), ty, 26, 20, t("gui.burmaldaholic.menu.loan"), UiSprites.TabIcon.LOAN, true,
			b -> presses++));
		widget(new BookmarkTab(x + 92 + BookmarkTab.labelledWidth(font, ach), ty, 40, 20, t("gui.burmaldaholic.menu.my_casino"), null, false, b -> presses++));

		// card kit console buttons: every family, the table theme's secondary, one disabled, one capped RU label
		int cy = py(206);
		int cx = x;
		for (CardButton.Family f : CardButton.Family.values()) {
			Component l = f == CardButton.Family.PRIMARY ? deal : f == CardButton.Family.DANGER ? stand : hit;
			CardButton b = widget(new CardButton(cx, cy, CardButton.naturalWidth(font, l, true), l, f == CardButton.Family.TABLE ? "hit" : null, f, table,
				c -> presses++));
			cx += b.getWidth() + 4;
		}
		CardButton off = widget(new CardButton(cx, cy, 64, t("gui.burmaldaholic.blackjack.insurance"), null, CardButton.Family.TABLE, table, c -> presses++));
		off.active = false;
		cx += 68;
		widget(new CardButton(cx, cy, 60, t("gui.burmaldaholic.blackjack.split.tooltip"), "split", CardButton.Family.TABLE, table, c -> presses++));
		showError(Component.translatable("gui.burmaldaholic.baccarat.chemmy.rules.3", "5"));
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = px(16);
		// panels, plates, header, balance plaque, rows, bars
		int y = py(92);
		CasinoUi.panel(g, x, y, 80, 48);
		CasinoUi.inset(g, x + 84, y, 80, 48);
		CasinoUi.plate(g, x + 168, y, 60, 20, false);
		CasinoUi.plate(g, x + 168, y + 24, 60, 20, true);
		CasinoUi.header(g, font, t("gui.burmaldaholic.blackjack.rules.2"), x + 232, y, 112);
		CasinoUi.balancePlaque(g, font, 1_234_567, x + 232, y + 24, 90, CasinoPalette.GOLD);
		for (int k = 0; k < 4; k++) CasinoUi.row(g, k, x + 350, y + k * 12, 110, 12);
		UiSprites.Fill[] fills = UiSprites.Fill.values();
		for (int k = 0; k < fills.length; k++) CasinoUi.progress(g, x + 466, y + k * 12, 120, 10, (k + 1) / 4.0 - 0.1, fills[k]);
		CasinoUi.text(g, font, t("gui.burmaldaholic.baccarat.rules.2"), x + 4, y + 20, 72, CasinoPalette.BONE);

		// card kit: faces / backs in L, M, S (+ sideways), glow, dim, lift, through the animator (settled)
		TableTheme table = TableTheme.of(scene);
		double now = Util.getMillis();
		cards.begin(now, false);
		int[] codes = {0, 22, 37, 51, 9};
		int cx = x;
		for (int size = 0; size < 3; size++) {
			for (int i = 0; i < codes.length; i++) {
				var s = cards.card(size * 10 + i, codes[i], size, table.back(CardSprites.BACK_NAVY), cx, py(146 + (size == 2 ? 16 : 0)), 0, Double.NaN,
					Double.NaN);
				if (i == 1) cards.glow(s, true);
				if (i == 2) cards.dim(s, true);
				if (i == 3) cards.lift(s, true);
				cx += CardSprites.w(size) + 3;
			}
			var back = cards.card(size * 10 + 9, -1, size, table.back(CardSprites.BACK_BURGUNDY), cx, py(146 + (size == 2 ? 16 : 0)), 0, Double.NaN,
				Double.NaN);
			cards.glow(back, size == 0);
			cx += CardSprites.w(size) + 8;
		}
		cards.card(99, 12, CardSprites.L, 0, cx, py(152), 90, Double.NaN, Double.NaN);
		cards.end();
		cards.draw(g, font);

		// seat plates in every state (a bot with a level badge, a long name, a folded seat)
		int sy = py(236);
		int sx = x;
		SeatPlate.State[] states = SeatPlate.State.values();
		String[] bots = {null, "villager", null, "piglin", "enderman"};
		Component[] names = {Component.translatable("gui.burmaldaholic.blackjack.dealer"), Component.translatable("gui.burmaldaholic.menu.achievements"),
			Component.translatable("gui.burmaldaholic.blackjack.even_money_prompt"), Component.translatable("gui.burmaldaholic.blackjack.stand"),
			Component.translatable("gui.burmaldaholic.menu.wallet")};
		for (int i = 0; i < states.length; i++) {
			var info = new SeatPlate.Info(names[i], null, bots[i], bots[i] == null ? 0 : i % 3 + 1, Component.translatable("gui.burmaldaholic.blackjack.split.tooltip"),
				CasinoPalette.GOLD, states[i], i == 1);
			sx += SeatPlate.draw(g, font, info, sx, sy, 1, 0) + 4;
		}
		// chip stacks (own colours, seat tint, hatched bot), rack buttons, tags, badges, stamps
		int by = py(300);
		ChipStackView.stack(g, x + 10, by, 235, 5, 0, false, 1);
		ChipStackView.label(g, font, x + 10, by, 235, CasinoPalette.BONE, 1);
		ChipStackView.stack(g, x + 40, by, 10_000, 5, ChipStackView.seatTint(2), false, 1);
		ChipStackView.stack(g, x + 70, by, 55, 5, 0, true, 1);
		int[] denoms = {1, 5, 25, 100, 500};
		for (int i = 0; i < denoms.length; i++) ChipStackView.big(g, denoms[i], x + 96 + i * 26, by - 22, i == 2, false, i != 4);
		ActionTag.draw(g, font, Component.translatable("gui.burmaldaholic.blackjack.double"), x + 260, by - 20, 1);
		ActionTag.badge(g, font, Component.translatable("gui.burmaldaholic.blackjack.stand"), x + 300, by - 20, false, 1);
		ActionTag.badge(g, font, Component.translatable("gui.burmaldaholic.card.rank.a"), x + 350, by - 20, true, 1);
		TableStamp.Kind[] kinds = TableStamp.Kind.values();
		for (int i = 0; i < kinds.length; i++) {
			TableStamp.draw(g, font, Component.translatable("gui.burmaldaholic.blackjack.insurance"), kinds[i], x + 400 + i * 58, by - 12, -6, 10_000, false,
				pose);
		}
	}
}
