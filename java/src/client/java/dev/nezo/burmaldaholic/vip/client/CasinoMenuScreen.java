package dev.nezo.burmaldaholic.vip.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSettingsScreen;
import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.client.menu.ClientCasinoMenu;
import dev.nezo.burmaldaholic.client.ui.BookmarkTab;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import dev.nezo.burmaldaholic.core.chips.ChipItem;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.ChipColumns;
import dev.nezo.burmaldaholic.core.ui.LedgerLayout;
import dev.nezo.burmaldaholic.core.ui.LoanLook;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import dev.nezo.burmaldaholic.vip.net.VipSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Casino Menu — "the casino ledger" (UI.md §2; docs/design/visual/extras.md §8; global.md §4.2; lane J-L2 J10). A
 * leather-bound ledger in the art-deco lobby: bookmark tabs along the top (icon-only, the selected one with its name),
 * a ruled page, ledger rows, {@code CasinoButton}s. The vip module draws its own tabs (Wallet, VIP, Contracts) from the
 * synced {@link VipClientState}; every other tab is a server page of core's {@code CasinoMenu} ({@link ClientCasinoMenu}):
 * lines plus buttons (optionally with an amount field), actions validated by the server. The Loan tab turns the book
 * into the Loan Shark's gunmetal dossier on the night harbour (extras.md §8.4); Achievements lays its lines out as medal
 * plates. Motion: shared entrance, tab content slide + fade-in (80 / 120 ms), progress bars fill on open (600 ms);
 * reduce motion: final values at once.
 */
public class CasinoMenuScreen extends CasinoScreen {
	/** Built-in tab ids (server tabs use their page id). */
	public static final String WALLET = "wallet", VIP = "vip", CONTRACTS = "contracts";
	private static final String LOAN = "loan", ACHIEVEMENTS = "achievements";
	private static final List<ClientCasinoMenu.Tab> NATIVE = List.of(
		new ClientCasinoMenu.Tab(WALLET, 10, Component.translatable("gui.burmaldaholic.menu.wallet")),
		new ClientCasinoMenu.Tab(VIP, 15, Component.translatable("gui.burmaldaholic.vip.title")),
		new ClientCasinoMenu.Tab(CONTRACTS, 20, Component.translatable("gui.burmaldaholic.menu.contracts")));

	private static final int BONE = CasinoPalette.BONE;
	private static final int DIM = CasinoPalette.BONE_SHADE;
	private static final int GOLD = CasinoPalette.GOLD;
	private static final int ROW_H = 14;

	/** A drawn text row of a page: {@code kind} = ledger row sprite (−1 none), {@code right} = right-aligned value. */
	private record Row(int y, int h, int kind, @Nullable FormattedCharSequence text, int color, @Nullable Component right, int rightColor, int indent,
		int width) {
		Row(int y, int h, int kind, @Nullable FormattedCharSequence text, int color, @Nullable Component right, int rightColor, int indent) {
			this(y, h, kind, text, color, right, rightColor, indent, 0);
		}
	}

	/** A widget placed in page-content coordinates (scrolls with the page). */
	private record Placed(AbstractWidget widget, int x, int y) {}

	private String tab;
	private String lastServerPage = "";
	private long tabAt = Util.getMillis();
	private int tabDir;
	private int scroll;
	private int contentH;
	private final List<Row> rows = new ArrayList<>();
	private final List<Placed> placed = new ArrayList<>();
	private final List<EditBox> amountBoxes = new ArrayList<>();
	private final Map<String, String> typed = new HashMap<>();
	/** Contract rows: [y, fraction×1000, done, fresh]. */
	private final List<long[]> contractBars = new ArrayList<>();
	/** Achievements: parsed plates (title, description, unlocked). */
	private record Plate(Component title, Component desc, boolean got) {}

	private final List<Plate> plates = new ArrayList<>();
	private @Nullable Component achSummary;
	private double achFraction;

	public CasinoMenuScreen(String tab) {
		super(Component.translatable("gui.burmaldaholic.menu.title"));
		this.tab = tab == null || tab.isEmpty() ? WALLET : tab;
	}

	private static boolean isNative(String id) {
		return NATIVE.stream().anyMatch(t -> t.id().equals(id));
	}

	private List<ClientCasinoMenu.Tab> allTabs() {
		List<ClientCasinoMenu.Tab> all = new ArrayList<>(NATIVE);
		for (ClientCasinoMenu.Tab t : ClientCasinoMenu.tabs()) {
			if (!isNative(t.id())) all.add(t);
		}
		all.sort(java.util.Comparator.comparingInt(ClientCasinoMenu.Tab::order));
		return all;
	}

	private boolean loanTab() {
		return LOAN.equals(tab);
	}

	@Override
	protected CasinoTheme theme() {
		return loanTab() ? CasinoTheme.LOAN : CasinoTheme.LOBBY;
	}

	@Override
	protected boolean showBanner() {
		return false;
	}

	@Override
	protected boolean showBalance() {
		return false; // drawn by the shell header (same plaque + ticker)
	}

	@Override
	protected void init() {
		super.init();
		VipClientModule.requestSync();
		ClientCasinoMenu.setListener(this::onServerPage);
		ClientCasinoMenu.request(isNative(tab) ? "" : tab);
		rebuild();
	}

	@Override
	public void removed() {
		ClientCasinoMenu.setListener(null);
		super.removed();
	}

	private void onServerPage() {
		Component e = ClientCasinoMenu.takeError();
		if (e != null) {
			showError(e);
			shakeFocused();
		}
		rebuild();
	}

	/** New data from the server. */
	void refresh() {
		rebuild();
	}

	@Override
	public void showError(Component message) {
		super.showError(message);
	}

	private void shakeFocused() {
		if (getFocused() instanceof CasinoButton b) b.shake();
	}

	@Override
	public void tick() {
		super.tick();
		if (CONTRACTS.equals(tab) && ClientCasinoState.clientTicks() % 20 == 0) {
			rebuild(); // reset countdown
		}
	}

	// ---- geometry ---------------------------------------------------------------------------------------------------

	private int pageX() {
		return px(LedgerLayout.PAGE_X);
	}

	private int pageY() {
		return py(LedgerLayout.PAGE_Y);
	}

	private int pageW() {
		return LedgerLayout.pageW(panel.w());
	}

	private int pageH() {
		return LedgerLayout.pageH(panel.h());
	}

	private int contentX() {
		return pageX() + LedgerLayout.PAD;
	}

	private int contentTop() {
		return pageY() + LedgerLayout.PAD;
	}

	private int contentBottom() {
		return pageY() + pageH() - LedgerLayout.PAD;
	}

	private int contentW() {
		return pageW() - 2 * LedgerLayout.PAD;
	}

	// ---- layout -------------------------------------------------------------------------------------------------------

	private void rebuild() {
		for (int i = 0; i < amountBoxes.size(); i++) {
			if (amountBoxes.get(i) != null) typed.put(lastServerPage + ":" + i, amountBoxes.get(i).getValue());
		}
		clearWidgets();
		rows.clear();
		placed.clear();
		amountBoxes.clear();
		contractBars.clear();
		plates.clear();
		achSummary = null;
		List<ClientCasinoMenu.Tab> tabs = allTabs();
		if (tabs.stream().noneMatch(t -> t.id().equals(tab))) tab = WALLET;
		buildTabs(tabs);
		contentH = 0;
		int w = contentW();
		switch (tab) {
			case WALLET -> wallet(w);
			case VIP -> vip(w);
			case CONTRACTS -> contracts(w);
			case LOAN -> loan(w);
			case ACHIEVEMENTS -> achievements(w);
			default -> serverPage(w);
		}
		lastServerPage = isNative(tab) ? "" : tab;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		place();
	}

	private int maxScroll() {
		return Math.max(0, contentH - (contentBottom() - contentTop()));
	}

	private void buildTabs(List<ClientCasinoMenu.Tab> tabs) {
		int selected = 0;
		for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id().equals(tab)) selected = i;
		Component selLabel = tabs.isEmpty() ? Component.empty() : tabs.get(selected).label();
		LedgerLayout.Tabs lay = LedgerLayout.tabs(tabs.size(), selected, font.width(selLabel), px(LedgerLayout.TABS_X), pageW());
		for (int i = 0; i < tabs.size(); i++) {
			ClientCasinoMenu.Tab t = tabs.get(i);
			boolean sel = i == selected;
			int y = py(sel ? LedgerLayout.TAB_SELECTED_Y : LedgerLayout.TAB_Y);
			int h = sel ? LedgerLayout.TAB_SELECTED_H : LedgerLayout.TAB_H;
			final int index = i;
			final int from = selected;
			BookmarkTab b = new BookmarkTab(lay.x()[i], y, lay.w()[i], h, t.label(), UiSprites.TabIcon.forPage(t.id()), LOAN.equals(t.id()),
				btn -> select(t.id(), Integer.signum(index - from)));
			b.select(sel, lay.labelShown());
			addRenderableWidget(b);
		}
	}

	private void select(String t, int dir) {
		tab = t;
		tabDir = dir;
		tabAt = Util.getMillis();
		scroll = 0;
		if (!isNative(t)) ClientCasinoMenu.request(t);
		rebuild();
	}

	private void place() {
		int top = contentTop();
		int bottom = contentBottom();
		for (Placed p : placed) {
			int y = top + p.y() - scroll;
			p.widget().setX(contentX() + p.x());
			p.widget().setY(y);
			p.widget().visible = y >= top - 1 && y + p.widget().getHeight() <= bottom + 1;
		}
	}

	private <T extends AbstractWidget> T put(T widget, int x, int y) {
		addRenderableWidget(widget);
		placed.add(new Placed(widget, x, y));
		return widget;
	}

	/** Adds wrapped text rows at the content bottom; returns the new content height. */
	private int text(Component text, int color, int indent, int w, int kind) {
		List<FormattedCharSequence> split = font.split(text, Math.max(40, w - indent - 8));
		for (FormattedCharSequence s : split) {
			rows.add(new Row(contentH, kind >= 0 ? ROW_H : 10, kind, s, color, null, 0, indent));
			contentH += kind >= 0 ? ROW_H : 10;
		}
		return contentH;
	}

	/** A ledger row "label ...... value" of width {@code w} at x 0. */
	private void ledger(Component label, Component value, int valueColor, int w, int kind) {
		rows.add(new Row(contentH, ROW_H, kind, CasinoUi.fit(font, label, w - font.width(value) - 16), BONE, value, valueColor, 0));
		contentH += ROW_H;
	}

	// ---- wallet (extras.md §8.3, mockup extras_menu_wallet.png) ------------------------------------------------------

	private static VipSyncPayload data() {
		return VipClientState.data();
	}

	private static MutableComponent signed(long n) {
		String s = n > 0 ? "+" + Numbers.format(n) : n < 0 ? "−" + Numbers.format(-n) : "0"; // literal-ok: signed number
		return Texts.raw(s);
	}

	private void wallet(int w) {
		VipSyncPayload d = data();
		int leftW = compact() ? w : 208;
		contentH = 38; // balance hero drawn in extractPanel
		ledger(Component.translatable("gui.burmaldaholic.menu.wallet.row.wagered"), Texts.number(d.wagered()), BONE, leftW, 0);
		long net = d.todayReturned() - d.todayStaked();
		ledger(Component.translatable("gui.burmaldaholic.menu.wallet.row.today"), signed(net), net > 0 ? CasinoPalette.BONUS : net < 0 ? CasinoPalette.CHIP_RED_LIGHT : BONE,
			leftW, 1);
		int streak = ClientCasinoState.streak();
		Component streakValue = streak == 0 ? Component.translatable("gui.burmaldaholic.menu.wallet.streak_none")
			: Component.translatable(streak > 0 ? "hud.burmaldaholic.streak.lucky" : "hud.burmaldaholic.streak.unlucky", Texts.number(Math.abs(streak)));
		ledger(Component.translatable("gui.burmaldaholic.menu.wallet.row.streak"), streakValue,
			streak > 0 ? CasinoPalette.BONUS : streak < 0 ? CasinoPalette.COOL : DIM, leftW, 0);
		if (d.botCap() > 0) {
			ledger(Component.translatable("gui.burmaldaholic.menu.wallet.row.bots"),
				Component.translatable("gui.burmaldaholic.menu.wallet.row.bots_value", Texts.number(d.botNet()), Texts.number(d.botCap())),
				d.botNet() >= d.botCap() ? CasinoPalette.CHIP_RED_LIGHT : GOLD, leftW, 1);
		}
		contentH += 6;
		walletVipTop = contentH;
		contentH += 44;
		if (!compact()) {
			Component settings = Component.translatable("gui.burmaldaholic.menu.settings");
			int bw = CasinoButton.width(font, settings, 70, true);
			put(CasinoButton.builder(settings, b -> {
				if (minecraft != null) minecraft.gui.setScreen(new FxSettingsScreen(this));
			}).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.SETTINGS)).size(bw, 20).build(), w - bw, 128);
		}
		contentH = Math.max(contentH, 150);
	}

	private int walletVipTop;

	private void drawWallet(GuiGraphicsExtractor g, int x, int y, int w, long age) {
		VipSyncPayload d = data();
		boolean reduced = FxSettings.reduceMotion();
		// balance hero (2×, gold, ink outline) with the ticker
		long now = Util.getMillis();
		balanceTicker.retarget(ClientCasinoState.shownBalance(), now, reduced);
		long bal = balanceTicker.value(now);
		g.text(font, Component.translatable("gui.burmaldaholic.menu.wallet.balance"), x + 2, y, BONE, true);
		Component hero = Texts.raw(Numbers.format(bal));
		int heroW = font.width(hero) * 2;
		FxText.outlinedCentered(g, font, hero, x + 2 + heroW / 2, y + 12, 2f, GOLD, CasinoPalette.INK);
		g.text(font, Texts.plural("gui.burmaldaholic.menu.wallet.chips", Math.max(0, bal)), x + 2 + heroW + 8, y + 19, BONE, true);
		// VIP line: badge, tier name in its colour, next tier, gold bar, numbers, perks
		int tier = VipTiers.clamp(d.tier());
		int vy = y + walletVipTop;
		int leftW = compact() ? w : 208;
		CasinoUi.tabIcon(g, UiSprites.TabIcon.VIP, 16, x + 2, vy + 1);
		g.text(font, VipTiers.name(tier), x + 22, vy, CasinoPalette.VIP[tier], true);
		VipRules.Progress p = VipRules.progress(d.wagered(), VipClientModule.thresholds(), d.tier());
		int barX = x + 104;
		int barW = leftW - 104;
		if (p.maxed()) {
			g.text(font, Component.translatable("gui.burmaldaholic.vip.max_tier"), x + 22, vy + 10, GOLD, true);
			CasinoUi.progress(g, barX, vy + 4, barW, 10, 1, UiSprites.Fill.GOLD);
		} else {
			g.text(font, Component.translatable("gui.burmaldaholic.menu.wallet.next_tier", VipTiers.name(p.next())), x + 22, vy + 10, BONE, true);
			CasinoUi.progress(g, barX, vy + 4, barW, 10, UiLayout.barFill(p.fraction(), age, reduced), UiSprites.Fill.GOLD);
			CasinoUi.textRight(g, font, Component.translatable("gui.burmaldaholic.menu.wallet.progress", Texts.number(d.wagered()), Texts.number(p.target())),
				barX + barW, vy + 17, BONE);
		}
		var cfg = CasinoConfig.vip();
		double cashback = switch (tier) {
			case VipTiers.GOLD -> cfg.cashback.gold;
			case VipTiers.PLATINUM -> cfg.cashback.platinum;
			case VipTiers.DIAMOND -> cfg.cashback.diamond;
			case VipTiers.NETHERITE -> cfg.cashback.netherite;
			default -> 0;
		};
		long tenths = Math.round(cashback * 1000);
		Component pct = tenths % 10 == 0 ? Texts.number(tenths / 10) : Texts.raw((tenths / 10) + "." + (tenths % 10)); // literal-ok: decimal percent
		CasinoUi.text(g, font, Component.translatable("gui.burmaldaholic.menu.wallet.perks", Texts.number(VipTiers.maxBet(tier)), pct), x + 2, vy + 30, leftW,
			BONE);
		if (!compact()) drawPocket(g, x + leftW + 12, y, w - leftW - 12);
	}

	/** "In your pocket": chip columns of the chips in the inventory (side discs 2 px apart, top chip, denomination). */
	private void drawPocket(GuiGraphicsExtractor g, int x, int y, int w) {
		int h = 118;
		CasinoUi.inset(g, x, y, w, h);
		g.text(font, Component.translatable("gui.burmaldaholic.menu.wallet.pocket"), x + 6, y + 6, GOLD, true);
		long[] counts = new long[ChipColumns.DENOMS.length];
		if (minecraft != null && minecraft.player != null) {
			var inv = minecraft.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.getItem() instanceof ChipItem chip) {
					for (int k = 0; k < counts.length; k++) if (ChipColumns.DENOMS[k] == chip.value()) counts[k] += s.getCount();
				}
			}
		}
		boolean any = false;
		for (long c : counts) any |= c > 0;
		if (!any) {
			FxText.wrappedCentered(g, font, Component.translatable("gui.burmaldaholic.menu.wallet.pocket_empty"), x + w / 2, y + h / 2 - 4, w - 12, 2, DIM,
				CasinoPalette.INK);
			return;
		}
		int n = counts.length;
		int colW = (w - 12) / n;
		int base = y + h - 20;
		int maxDiscs = Math.min(ChipColumns.MAX_VISIBLE * 3, (base - (y + 22)) / ChipColumns.PITCH - 4);
		// small denominations on the left, like the mockup
		for (int k = 0; k < n; k++) {
			int di = n - 1 - k;
			int denom = ChipColumns.DENOMS[di];
			int cx = x + 6 + k * colW + colW / 2;
			int discs = (int) Math.min(counts[di], maxDiscs);
			for (int j = 0; j < discs; j++) {
				CasinoUi.sprite(g, UiSprites.sprite("fx/chip_side_" + denom), cx - 6, base - 3 - j * ChipColumns.PITCH, 12, 3);
			}
			if (discs > 0) CasinoUi.sprite(g, UiSprites.sprite("fx/chip_" + denom), cx - 4, base - 3 - discs * ChipColumns.PITCH - 6, 8, 8);
			String label = Numbers.format(denom);
			g.text(font, label, cx - font.width(label) / 2, base + 4, BONE, true);
			if (counts[di] > maxDiscs) {
				String more = "×" + Numbers.format(counts[di]); // literal-ok: count
				g.text(font, more, cx - font.width(more) / 2, base - 3 - discs * ChipColumns.PITCH - 16, GOLD, true);
			}
		}
	}

	// ---- VIP ------------------------------------------------------------------------------------------------------------

	private static Component perkText(VipRules.Perk perk) {
		return switch (perk.kind()) {
			case CHIPS -> Component.translatable(perk.key(), Texts.chips(perk.value()));
			case COUNT -> Component.translatable(perk.key(), Texts.number(perk.value()));
			case PERCENT -> Component.translatable(perk.key(), perk.value() % 10 == 0 ? Texts.number(perk.value() / 10)
				: Texts.raw((perk.value() / 10) + "." + (perk.value() % 10))); // literal-ok: decimal percent
			case NONE -> Component.translatable(perk.key());
		};
	}

	private void vip(int w) {
		VipSyncPayload d = data();
		var cfg = CasinoConfig.vip();
		VipRules.PerkParams params = new VipRules.PerkParams(CasinoConfig.loan().products, cfg.contractBonus.silver, cfg.contractBonus.gold,
			CasinoConfig.contracts().slots, new double[] {cfg.cashback.gold, cfg.cashback.platinum, cfg.cashback.diamond, cfg.cashback.netherite});
		long[] th = VipClientModule.thresholds();
		contentH = 50; // badge plates
		int kind = 0;
		for (int t = VipRules.BRONZE; t <= VipRules.MAX_TIER; t++) {
			boolean reached = t <= d.tier();
			MutableComponent head = Component.translatable("gui.burmaldaholic.vip.tier_line", VipTiers.name(t),
				Texts.number(VipRules.thresholdOf(t, th)), Texts.number(VipTiers.maxBet(t)));
			if (t == d.tier()) head = head.withStyle(ChatFormatting.BOLD);
			text(head, reached ? (t == d.tier() ? GOLD : BONE) : DIM, 0, w, 2);
			for (VipRules.Perk perk : VipRules.perksAt(t, params)) {
				text(Component.translatable("gui.burmaldaholic.vip.perk_bullet", perkText(perk)), reached ? BONE : DIM, 8, w, kind++ % 2);
			}
		}
	}

	private void drawVipPlates(GuiGraphicsExtractor g, int x, int y, int w) {
		int tier = VipTiers.clamp(data().tier());
		int n = VipRules.MAX_TIER + 1;
		int pw = (w - (n - 1) * 4) / n;
		for (int t = 0; t < n; t++) {
			int x0 = x + t * (pw + 4);
			CasinoUi.sprite(g, t == tier ? UiSprites.ACH_GOLD : t < tier ? UiSprites.ACH_UNLOCKED : UiSprites.ACH_LOCKED, x0, y, pw, 42, CasinoPalette.BG_DEEP,
				CasinoPalette.FRAME);
			CasinoUi.tabIcon(g, UiSprites.TabIcon.VIP, 20, x0 + pw / 2 - 10, y + 5);
			if (t > tier) g.fill(x0 + pw / 2 - 10, y + 5, x0 + pw / 2 + 10, y + 25, 0x99140822);
			g.fill(x0 + pw / 2 - 8, y + 25, x0 + pw / 2 + 8, y + 26, CasinoPalette.VIP[t]);
			Component name = VipTiers.name(t);
			FormattedCharSequence s = CasinoUi.fit(font, name, pw - 4);
			g.text(font, s, x0 + (pw - font.width(s)) / 2, y + 28, t <= tier ? CasinoPalette.VIP[t] : DIM, true);
		}
	}

	// ---- contracts -------------------------------------------------------------------------------------------------------

	private void contracts(int w) {
		VipSyncPayload d = data();
		if (!VipClientState.received()) {
			text(Component.translatable("gui.burmaldaholic.vip.loading"), DIM, 0, w, -1);
			return;
		}
		if (!d.contractsOn()) {
			text(Component.translatable("gui.burmaldaholic.vip.contracts_off"), DIM, 0, w, -1);
			return;
		}
		rows.add(new Row(contentH, 10, -1, Component.translatable("gui.burmaldaholic.contracts.title").getVisualOrderText(), GOLD,
			Component.translatable("gui.burmaldaholic.contracts.resets_in", Texts.raw(Numbers.minutesSeconds(VipClientState.resetTicks()))), DIM, 0));
		contentH += 14;
		for (int i = 0; i < d.contracts().size(); i++) {
			VipSyncPayload.ContractView c = d.contracts().get(i);
			final int index = i;
			Component label = c.done() ? Component.translatable("gui.burmaldaholic.contracts.done")
				: c.rerolled() ? Component.translatable("gui.burmaldaholic.contracts.rerolled_already")
				: Component.translatable("gui.burmaldaholic.contracts.reroll", Texts.chipsAcc(d.rerollCost()));
			int bw = Math.min(w / 3, CasinoButton.width(font, label, 50, false));
			int top = contentH;
			CasinoButton b = CasinoButton.builder(label, btn -> VipClientModule.reroll(index)).size(bw, 18).build();
			b.enabled(!c.done() && !c.rerolled(), null);
			if (font.width(label) + 8 > bw) b.tooltip(label);
			put(b, w - bw, top + 4);
			int textW = w - bw - 8;
			Component task = Component.translatable("gui.burmaldaholic.contracts.task." + c.id(), Texts.number(c.target()));
			Component reward = Component.translatable("gui.burmaldaholic.contracts.reward", Texts.chips(c.reward()));
			rows.add(new Row(top, ROW_H, i % 2, CasinoUi.fit(font, task, textW - font.width(reward) - 12), c.done() ? CasinoPalette.BONUS : BONE, reward,
				GOLD, 0, textW));
			rows.add(new Row(top + ROW_H, ROW_H, i % 2, null, BONE,
				Component.translatable("gui.burmaldaholic.contracts.progress", Texts.number(c.progress()), Texts.number(c.target())), DIM, 0, textW));
			contractBars.add(new long[] {top + ROW_H + 1, c.target() <= 0 ? 0 : Math.min(1000, 1000 * c.progress() / c.target()), c.done() ? 1 : 0, textW - 60});
			contentH += 2 * ROW_H + 6;
		}
	}

	// ---- loan: the Loan Shark's dossier (extras.md §8.4) ------------------------------------------------------------

	private void loan(int w) {
		if (!tab.equals(ClientCasinoMenu.page())) {
			text(Component.translatable("gui.burmaldaholic.vip.loading"), DIM, 84, w, -1);
			return;
		}
		int colW = 132;
		for (ClientCasinoMenu.Button spec : ClientCasinoMenu.buttons()) colW = Math.max(colW, font.width(spec.label()) + 12);
		int colX = w - colW;
		// status lines next to the portrait
		contentH = 38;
		for (ClientCasinoMenu.Line l : ClientCasinoMenu.lines()) {
			for (FormattedCharSequence s : font.split(l.text(), colX - 90)) {
				rows.add(new Row(contentH, 10, -1, s, 0xFF000000 | l.color(), null, 0, 80));
				contentH += 10;
			}
		}
		contentH = Math.max(contentH + 4, 76);
		loanMeterY = contentH;
		contentH += 30;
		// buttons in the right column: Pay all (danger), amount + Pay
		int by = 12;
		List<ClientCasinoMenu.Button> buttons = ClientCasinoMenu.buttons();
		for (int i = 0; i < buttons.size(); i++) {
			ClientCasinoMenu.Button spec = buttons.get(i);
			EditBox box = null;
			boolean payAll = spec.action().equals("pay_all");
			int bw = colW;
			if (spec.amount()) {
				box = new EditBox(font, 0, 0, 60, 18, spec.label());
				box.setMaxLength(12);
				String prev = typed.get(tab + ":" + i);
				box.setValue(prev != null ? prev : spec.suggested() > 0 ? Long.toString(spec.suggested()) : "");
				put(box, colX, by + 1);
				bw = colW - 64;
			}
			final EditBox field = box;
			CasinoButton b = CasinoButton.builder(spec.label(), btn -> pressServer(spec, field))
				.style(payAll ? CasinoButton.Style.DANGER : CasinoButton.Style.SECONDARY).size(bw, 20).build();
			b.enabled(spec.active(), null);
			put(b, colX + (box != null ? 64 : 0), by);
			amountBoxes.add(field);
			by += 24;
		}
	}

	private int loanMeterY;

	private void drawLoan(GuiGraphicsExtractor g, int x, int y, int w) {
		var s = ClientCasinoState.status();
		LoanLook.Mood mood = LoanLook.mood(s.debt(), s.inDefault());
		// portrait with a steel frame, the bubble, the name
		g.fill(x - 1, y - 1, x + 73, y + 73, 0xFF0A1214);
		CasinoUi.sheet(g, UiSprites.SHARK, 0, 0, 72, 72, x, y);
		Component line = Component.translatable(mood.key());
		int bw = Math.min(w - 90, font.width(line) + 16);
		CasinoUi.sprite(g, UiSprites.BUBBLE_CHEEKY, x + 78, y + 2, bw, 18, 0xFFF4ECF8, CasinoPalette.CHIP_RED);
		g.text(font, CasinoUi.fit(font, line, bw - 12), x + 86, y + 7, CasinoPalette.CHIP_RED_DARK, false);
		FxText.outlined(g, font, Component.translatable("gui.burmaldaholic.loan.title").withStyle(ChatFormatting.BOLD), x + 80, y + 26,
			CasinoPalette.CHIP_RED_LIGHT, CasinoPalette.INK);
		// the debt meter: owed against the balance at stake (principal unknown on this page: full while owed)
		int my = y + loanMeterY;
		int mw = w - 132 - 12;
		CasinoUi.sprite(g, UiSprites.DEBT_METER, x, my, mw, 12, 0xFF140810, CasinoPalette.CHIP_RED_DARK);
		double fill = LoanLook.debtFill(s.debt(), 0);
		int fw = UiLayout.fillPixels(mw - 16, fill);
		if (fw > 0) CasinoUi.sprite(g, UiSprites.Fill.RED.id, x + 2, my + 3, fw, 6);
		CasinoUi.sprite(g, UiSprites.DEBT_SKULL, x + mw - 12, my, 12, 12);
		if (s.inDefault()) {
			g.text(font, Component.translatable("gui.burmaldaholic.loan.collectors_coming"), x, my + 16, BONE, true);
		}
	}

	// ---- achievements (extras.md §8.3: medal plates in 2 columns) -------------------------------------------------------

	private void achievements(int w) {
		if (!tab.equals(ClientCasinoMenu.page())) {
			text(Component.translatable("gui.burmaldaholic.vip.loading"), DIM, 0, w, -1);
			return;
		}
		List<ClientCasinoMenu.Line> lines = ClientCasinoMenu.lines();
		int i = 0;
		if (!lines.isEmpty()) {
			achSummary = lines.get(0).text();
			i = 1;
		}
		List<ClientCasinoMenu.Line> rest = new ArrayList<>();
		for (; i < lines.size(); i++) if (!lines.get(i).text().getString().isEmpty()) rest.add(lines.get(i));
		for (int k = 0; k + 1 < rest.size(); k += 2) {
			boolean got = (rest.get(k).color() & 0xFFFFFF) != 0x777777;
			plates.add(new Plate(rest.get(k).text(), rest.get(k + 1).text(), got));
		}
		int have = 0;
		for (Plate p : plates) if (p.got) have++;
		achFraction = plates.isEmpty() ? 0 : have / (double) plates.size();
		contentH = 24 + ((plates.size() + 1) / 2) * 30;
		for (ClientCasinoMenu.Button spec : ClientCasinoMenu.buttons()) {
			Component label = spec.label();
			int bw = CasinoButton.width(font, label, 60, true);
			put(CasinoButton.builder(label, b -> pressServer(spec, null)).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.ACHIEVEMENTS)).size(bw, 18).build(),
				w - bw, 0);
			break;
		}
	}

	private void drawAchievements(GuiGraphicsExtractor g, int x, int y, int w, long age) {
		if (achSummary != null) {
			g.text(font, achSummary, x, y + 5, GOLD, true);
			int bx = x + font.width(achSummary) + 8;
			int bw = Math.max(20, w - 110 - (bx - x));
			CasinoUi.progress(g, bx, y + 4, bw, 10, UiLayout.barFill(achFraction, age, FxSettings.reduceMotion()), UiSprites.Fill.GOLD);
		}
		int colW = (w - 6) / 2;
		for (int i = 0; i < plates.size(); i++) {
			Plate p = plates.get(i);
			int px = x + (i % 2) * (colW + 6);
			int py = y + 24 + (i / 2) * 30;
			CasinoUi.sprite(g, p.got ? UiSprites.ACH_UNLOCKED : UiSprites.ACH_LOCKED, px, py, colW, 28, CasinoPalette.BG_DEEP, CasinoPalette.FRAME);
			CasinoUi.sheet(g, UiSprites.ACH_MEDALS, p.got ? 72 : 0, 0, 24, 24, px + 2, py + 2);
			CasinoUi.text(g, font, p.title, px + 30, py + 5, colW - 34, p.got ? GOLD : DIM);
			CasinoUi.text(g, font, p.desc, px + 30, py + 16, colW - 34, p.got ? BONE : 0xFF8A7AA0);
		}
	}

	// ---- generic server pages ---------------------------------------------------------------------------------------------

	/** A server page: button rows (with optional amount fields) on top, then the text lines as ledger rows. */
	private void serverPage(int w) {
		if (!tab.equals(ClientCasinoMenu.page())) {
			text(Component.translatable("gui.burmaldaholic.vip.loading"), DIM, 0, w, -1);
			return;
		}
		List<ClientCasinoMenu.Button> buttons = ClientCasinoMenu.buttons();
		int bx = 0;
		int by = 0;
		for (int i = 0; i < buttons.size(); i++) {
			ClientCasinoMenu.Button spec = buttons.get(i);
			int bw = Math.min(w, CasinoButton.width(font, spec.label(), 60, false));
			int fw = spec.amount() ? Math.max(50, Math.min(90, w - bw - 6)) : 0;
			int need = bw + (fw > 0 ? fw + 4 : 0);
			if (bx > 0 && bx + need > w) {
				bx = 0;
				by += 24;
			}
			EditBox box = null;
			if (spec.amount()) {
				box = new EditBox(font, 0, 0, fw, 18, spec.label());
				box.setMaxLength(12);
				String prev = typed.get(tab + ":" + i);
				box.setValue(prev != null ? prev : spec.suggested() > 0 ? Long.toString(spec.suggested()) : "");
				put(box, bx, by + 1);
				bx += fw + 4;
			}
			final EditBox field = box;
			CasinoButton b = CasinoButton.builder(spec.label(), btn -> pressServer(spec, field)).size(bw, 20).build();
			b.enabled(spec.active(), null);
			put(b, bx, by);
			amountBoxes.add(field);
			bx += bw + 4;
		}
		contentH = buttons.isEmpty() ? 0 : by + 26;
		int kind = 0;
		for (ClientCasinoMenu.Line l : ClientCasinoMenu.lines()) {
			if (l.text().getString().isEmpty()) {
				contentH += 6;
				kind = 0;
				continue;
			}
			text(l.text(), 0xFF000000 | l.color(), 4, w, kind++ % 2);
		}
	}

	private void pressServer(ClientCasinoMenu.Button spec, @Nullable EditBox field) {
		if (spec.action().equals("client:advancements")) {
			if (minecraft != null && minecraft.getConnection() != null) {
				minecraft.gui.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(minecraft.getConnection().getAdvancements(), this));
			}
			return;
		}
		long amount = 0;
		if (field != null) {
			try {
				String digits = field.getValue().replaceAll("[^0-9]", "");
				amount = digits.isEmpty() ? 0 : Long.parseLong(digits);
			} catch (NumberFormatException e) {
				amount = 0;
			}
		}
		ClientCasinoMenu.press(tab, spec.action(), amount);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int next = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(scrollY * 12)));
		if (next != scroll) {
			scroll = next;
			place();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---- rendering --------------------------------------------------------------------------------------------------------

	@Override
	protected void extractFrame(GuiGraphicsExtractor g, CasinoTheme t) {
		boolean loan = loanTab();
		CasinoUi.sprite(g, loan ? UiSprites.SHELL_LOAN : UiSprites.SHELL, panel.x(), panel.y(), panel.w(), panel.h(), 0xFF4A0A1C, CasinoPalette.GOLD);
		CasinoUi.header(g, font, title, px(LedgerLayout.HEADER_X), py(LedgerLayout.HEADER_Y), LedgerLayout.HEADER_W);
		long now = Util.getMillis();
		balanceTicker.retarget(ClientCasinoState.shownBalance(), now, FxSettings.reduceMotion());
		int dir = balanceTicker.direction(now);
		int color = dir == 0 ? GOLD : CasinoUi.mix(GOLD, dir > 0 ? CasinoPalette.BONUS : CasinoPalette.CHIP_RED, balanceTicker.tint(now));
		CasinoUi.balancePlaque(g, font, balanceTicker.value(now), panel.right() - 16 - LedgerLayout.PLAQUE_W, py(LedgerLayout.HEADER_Y), LedgerLayout.PLAQUE_W,
			color);
		CasinoUi.sprite(g, loan ? UiSprites.PAGE_LOAN : UiSprites.PAGE, pageX(), pageY(), pageW(), pageH(), 0xFF1E0E2C, CasinoPalette.FRAME);
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long age = Util.getMillis() - tabAt;
		boolean reduced = FxSettings.reduceMotion();
		int slide = UiLayout.tabSlide(age, tabDir, reduced);
		int x = contentX() + slide;
		int top = contentTop();
		int y = top - scroll;
		int w = contentW();
		g.enableScissor(pageX() + 2, top - 2, pageX() + pageW() - 2, contentBottom() + 2);
		for (Row r : rows) {
			int ry = y + r.y();
			if (ry + r.h() < top - 2 || ry > contentBottom()) continue;
			int rw = r.width() > 0 ? r.width() : r.kind() >= 0 && WALLET.equals(tab) && !compact() ? 208 : w;
			if (r.kind() >= 0) CasinoUi.row(g, loanTab() ? 3 : r.kind(), x, ry, rw, r.h());
			int ty = ry + (r.h() - 8) / 2;
			if (r.text() != null) g.text(font, r.text(), x + 4 + r.indent(), ty, r.color(), true);
			if (r.right() != null) CasinoUi.textRight(g, font, r.right(), x + rw - 4, ty, r.rightColor());
		}
		switch (tab) {
			case WALLET -> drawWallet(g, x, y, w, Util.getMillis() - tabAt);
			case VIP -> drawVipPlates(g, x, y, w);
			case LOAN -> {
				if (tab.equals(ClientCasinoMenu.page())) drawLoan(g, x, y, w);
			}
			case ACHIEVEMENTS -> drawAchievements(g, x, y, w, age);
			default -> {
			}
		}
		for (long[] bar : contractBars) {
			int by = y + (int) bar[0];
			CasinoUi.progress(g, x + 4, by + 2, (int) bar[3], 8, UiLayout.barFill(bar[1] / 1000.0, age, reduced),
				bar[2] == 1 ? UiSprites.Fill.GREEN : UiSprites.Fill.GOLD);
			if (bar[2] == 1) CasinoUi.sprite(g, UiSprites.STAMP, x + (int) bar[3] + 8, by - 3, 16, 16);
		}
		// tab switch: the new page fades in over the old (veil in the page colour)
		float fade = UiLayout.tabFadeIn(age, reduced);
		if (fade < 1f) g.fill(pageX() + 2, top - 2, pageX() + pageW() - 2, contentBottom() + 2, CasinoPalette.withAlpha(loanTab() ? 0xFF0E1A1E : 0xFF1E0E2C,
			0.9f * (1 - fade)));
		g.disableScissor();
		if (maxScroll() > 0) {
			int track = contentBottom() - top;
			int thumb = Math.max(10, track * track / Math.max(1, contentH));
			int ty = top + (track - thumb) * scroll / Math.max(1, maxScroll());
			int sx = pageX() + pageW() - 5;
			g.fill(sx, top, sx + 2, top + track, 0x40000000);
			g.fill(sx, ty, sx + 2, ty + thumb, CasinoPalette.withAlpha(GOLD, 0.7f));
		}
	}

	@Override
	protected int errorY() {
		return contentBottom() - 10;
	}
}
