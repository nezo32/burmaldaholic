package dev.nezo.burmaldaholic.vip.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import dev.nezo.burmaldaholic.vip.net.VipSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Casino Menu (UI.md §2), vip part: Wallet, VIP status and Contracts tabs. Renders the synced
 * {@link VipClientState}; the only action (contract reroll) goes to the server. Every text wraps to
 * the panel width and every button is as wide as its (possibly Russian) label.
 */
public class CasinoMenuScreen extends Screen {
	public enum Tab {
		WALLET, VIP, CONTRACTS
	}

	private static final int PANEL_BG = 0xF01B1F2A;
	private static final int PANEL_BORDER = 0xFFB8963E;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFAAAAAA;
	private static final int ERROR = 0xFFFF5555;
	private static final int PAD = 8;

	/** A rendered line of the scrollable body; {@code bar} ≥ 0 draws a progress bar instead of text. */
	private record Line(@Nullable FormattedCharSequence text, int color, int indent, double bar) {}

	private Tab tab;
	private int left;
	private int top;
	private int panelW;
	private int panelH;
	private int bodyTop;
	private int bodyBottom;
	private int scroll;
	private int contentHeight;
	private final List<Line> lines = new ArrayList<>();
	/** Contract rows: y offset of each row inside the body (for drawing). */
	private final List<int[]> rowLayout = new ArrayList<>();
	private final List<List<Line>> rowLines = new ArrayList<>();
	private final List<Button> rowButtons = new ArrayList<>();
	private @Nullable Component error;
	private int errorTicks;

	public CasinoMenuScreen(Tab tab) {
		super(Component.translatable("gui.burmaldaholic.menu.title"));
		this.tab = tab;
	}

	@Override
	protected void init() {
		panelW = Math.min(340, width - 16);
		panelH = Math.min(230, height - 16);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		VipClientModule.requestSync();
		rebuild();
	}

	/** New data from the server. */
	void refresh() {
		rebuild();
	}

	void showError(Component message) {
		error = message;
		errorTicks = 60;
	}

	@Override
	public void tick() {
		super.tick();
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
		if (tab == Tab.CONTRACTS && ClientCasinoState.clientTicks() % 20 == 0) {
			rebuild(); // reset countdown
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- layout -------------------------------------------------------------------------------

	private void rebuild() {
		clearWidgets();
		lines.clear();
		rowLayout.clear();
		rowLines.clear();
		rowButtons.clear();
		int x = left + PAD;
		int y = top + 20;
		for (Tab t : Tab.values()) {
			Component label = switch (t) {
				case WALLET -> Component.translatable("gui.burmaldaholic.menu.wallet");
				case VIP -> Component.translatable("gui.burmaldaholic.vip.title");
				case CONTRACTS -> Component.translatable("gui.burmaldaholic.menu.contracts");
			};
			int w = Math.max(50, font.width(label) + 10);
			if (x + w > left + panelW - PAD && x > left + PAD) {
				x = left + PAD;
				y += 22;
			}
			Button b = addRenderableWidget(Button.builder(label, btn -> select(t)).bounds(x, y, w, 20).build());
			b.active = t != tab;
			x += w + 4;
		}
		bodyTop = y + 26;
		bodyBottom = top + panelH - 16;
		int bodyW = panelW - 2 * PAD;
		switch (tab) {
			case WALLET -> wallet(bodyW);
			case VIP -> vip(bodyW);
			case CONTRACTS -> contracts(bodyW);
		}
		if (tab != Tab.CONTRACTS) {
			contentHeight = 0;
			for (Line l : lines) {
				contentHeight += l.bar() >= 0 ? 8 : font.lineHeight + 1;
			}
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (bodyBottom - bodyTop))));
		placeRowButtons();
	}

	private void select(Tab t) {
		tab = t;
		scroll = 0;
		rebuild();
	}

	private void add(Component text, int color, int indent, int width) {
		for (FormattedCharSequence s : font.split(text, Math.max(40, width - indent))) {
			lines.add(new Line(s, color, indent, -1));
		}
	}

	private void blank() {
		lines.add(new Line(null, TEXT, 0, -1));
	}

	private static VipSyncPayload data() {
		return VipClientState.data();
	}

	private static MutableComponent signed(long n) {
		String s = n > 0 ? "+" + Numbers.format(n) : n < 0 ? "−" + Numbers.format(-n) : "0";
		return Texts.raw(s).withStyle(n > 0 ? ChatFormatting.GREEN : n < 0 ? ChatFormatting.RED : ChatFormatting.GRAY); // literal-ok: signed number
	}

	private void wallet(int w) {
		VipSyncPayload d = data();
		add(Component.translatable("gui.burmaldaholic.common.balance", Texts.number(ClientCasinoState.balance())), 0xFFFFD700, 0, w);
		add(Component.translatable("gui.burmaldaholic.menu.wallet.lifetime", Texts.chips(d.wagered())), TEXT, 0, w);
		add(Component.translatable("gui.burmaldaholic.menu.wallet.today", signed(d.todayReturned() - d.todayStaked())), TEXT, 0, w);
		int streak = ClientCasinoState.streak();
		add(streak == 0 ? Component.translatable("gui.burmaldaholic.menu.wallet.streak_none")
			: Component.translatable("gui.burmaldaholic.menu.wallet.streak", Component.translatable(
				streak > 0 ? "hud.burmaldaholic.streak.lucky" : "hud.burmaldaholic.streak.unlucky", Texts.number(Math.abs(streak)))), TEXT, 0, w);
		blank();
		add(Component.translatable("gui.burmaldaholic.vip.current", VipTiers.name(d.tier())), TEXT, 0, w);
		add(Component.translatable("gui.burmaldaholic.vip.max_bet", Texts.chips(VipTiers.maxBet(d.tier()))), TEXT, 0, w);
		progressLines(d, w);
	}

	private void progressLines(VipSyncPayload d, int w) {
		VipRules.Progress p = VipRules.progress(d.wagered(), VipClientModule.thresholds(), d.tier());
		if (p.maxed()) {
			add(Component.translatable("gui.burmaldaholic.vip.max_tier"), 0xFFFFAA00, 0, w);
			return;
		}
		add(Component.translatable("gui.burmaldaholic.vip.progress", VipTiers.name(p.next()), Texts.number(d.wagered()), Texts.number(p.target())), TEXT, 0, w);
		lines.add(new Line(null, 0xFF55FF55, 0, p.fraction()));
	}

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
		add(Component.translatable("gui.burmaldaholic.vip.current", VipTiers.name(d.tier())), TEXT, 0, w);
		progressLines(d, w);
		for (int t = VipRules.BRONZE; t <= VipRules.MAX_TIER; t++) {
			blank();
			boolean reached = t <= d.tier();
			MutableComponent head = Component.translatable("gui.burmaldaholic.vip.tier_line", VipTiers.name(t),
				Texts.number(VipRules.thresholdOf(t, th)), Texts.number(VipTiers.maxBet(t)));
			if (t == d.tier()) {
				head = head.withStyle(ChatFormatting.BOLD);
			}
			add(head, reached ? TEXT : DIM, 0, w);
			for (VipRules.Perk perk : VipRules.perksAt(t, params)) {
				add(Component.translatable("gui.burmaldaholic.vip.perk_bullet", perkText(perk)), reached ? 0xFFDDDDDD : 0xFF777777, 8, w);
			}
		}
	}

	private void contracts(int w) {
		VipSyncPayload d = data();
		contentHeight = 0;
		if (!VipClientState.received()) {
			add(Component.translatable("gui.burmaldaholic.vip.loading"), DIM, 0, w);
			contentHeight = lines.size() * (font.lineHeight + 1);
			return;
		}
		if (!d.contractsOn()) {
			add(Component.translatable("gui.burmaldaholic.vip.contracts_off"), DIM, 0, w);
			contentHeight = lines.size() * (font.lineHeight + 1);
			return;
		}
		add(Component.translatable("gui.burmaldaholic.contracts.title"), 0xFFFFD700, 0, w);
		add(Component.translatable("gui.burmaldaholic.contracts.resets_in", Texts.raw(Numbers.minutesSeconds(VipClientState.resetTicks()))), DIM, 0, w);
		contentHeight = lines.size() * (font.lineHeight + 1) + 4;
		for (int i = 0; i < d.contracts().size(); i++) {
			VipSyncPayload.ContractView c = d.contracts().get(i);
			final int index = i;
			Component label = c.done() ? Component.translatable("gui.burmaldaholic.contracts.done")
				: c.rerolled() ? Component.translatable("gui.burmaldaholic.contracts.rerolled_already")
				: Component.translatable("gui.burmaldaholic.contracts.reroll", Texts.chipsAcc(d.rerollCost()));
			int bw = Math.min(w / 2, Math.max(50, font.width(label) + 10));
			Button b = Button.builder(label, btn -> VipClientModule.reroll(index)).bounds(0, 0, bw, 20).build();
			b.active = !c.done() && !c.rerolled();
			if (font.width(label) + 10 > bw) {
				b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
			}
			addRenderableWidget(b);
			rowButtons.add(b);
			int textW = w - bw - 6;
			List<Line> row = new ArrayList<>();
			Component task = Component.translatable("gui.burmaldaholic.contracts.task." + c.id(), Texts.number(c.target()));
			for (FormattedCharSequence s : font.split(task, Math.max(40, textW))) {
				row.add(new Line(s, c.done() ? 0xFF55FF55 : TEXT, 0, -1));
			}
			Component status = Component.translatable("gui.burmaldaholic.vip.contract_status",
				Component.translatable("gui.burmaldaholic.contracts.progress", Texts.number(c.progress()), Texts.number(c.target())),
				Component.translatable("gui.burmaldaholic.contracts.reward", Texts.chips(c.reward())));
			for (FormattedCharSequence s : font.split(status, Math.max(40, textW))) {
				row.add(new Line(s, DIM, 0, -1));
			}
			row.add(new Line(null, c.done() ? 0xFF55FF55 : 0xFFFFAA00, 0, c.target() <= 0 ? 0 : Math.min(1.0, (double) c.progress() / c.target())));
			int h = Math.max(24, row.size() * (font.lineHeight + 1) + 6);
			rowLayout.add(new int[] {contentHeight, h, textW});
			rowLines.add(row);
			contentHeight += h;
		}
	}

	private void placeRowButtons() {
		for (int i = 0; i < rowButtons.size(); i++) {
			Button b = rowButtons.get(i);
			int y = bodyTop + rowLayout.get(i)[0] - scroll + 1;
			b.setX(left + panelW - PAD - b.getWidth());
			b.setY(y);
			b.visible = y >= bodyTop && y + 20 <= bodyBottom;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, contentHeight - (bodyBottom - bodyTop));
		int next = Math.max(0, Math.min(max, scroll - (int) Math.round(scrollY * 12)));
		if (next != scroll) {
			scroll = next;
			placeRowButtons();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---- rendering ----------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, PANEL_BORDER);
		g.fill(left, top, left + panelW, top + panelH, PANEL_BG);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(font, title, left + PAD, top + 7, 0xFFFFD700, true);
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(ClientCasinoState.balance()));
		g.text(font, balance, left + panelW - PAD - font.width(balance), top + 7, TEXT, true);
		g.enableScissor(left + 2, bodyTop, left + panelW - 2, bodyBottom);
		int x = left + PAD;
		if (tab == Tab.CONTRACTS) {
			int y = bodyTop - scroll;
			for (Line l : lines) {
				y = drawLine(g, l, x, y, panelW - 2 * PAD);
			}
			for (int i = 0; i < rowLines.size(); i++) {
				int[] layout = rowLayout.get(i);
				int ry = bodyTop + layout[0] - scroll;
				g.fill(x, ry - 2, left + panelW - PAD, ry - 1, 0x40FFFFFF);
				int yy = ry + 2;
				for (Line l : rowLines.get(i)) {
					yy = drawLine(g, l, x, yy, layout[2]);
				}
			}
		} else {
			int y = bodyTop - scroll;
			for (Line l : lines) {
				y = drawLine(g, l, x, y, panelW - 2 * PAD);
			}
		}
		g.disableScissor();
		if (contentHeight > bodyBottom - bodyTop) {
			int track = bodyBottom - bodyTop;
			int thumb = Math.max(10, track * track / contentHeight);
			int ty = bodyTop + (track - thumb) * scroll / Math.max(1, contentHeight - track);
			g.fill(left + panelW - 4, ty, left + panelW - 2, ty + thumb, 0x80FFFFFF);
		}
		if (error != null) {
			g.centeredText(font, error, left + panelW / 2, top + panelH - 12, ERROR);
		}
	}

	private int drawLine(GuiGraphicsExtractor g, Line l, int x, int y, int width) {
		if (l.bar() >= 0) {
			int w = Math.max(20, Math.min(width, 200));
			g.fill(x, y + 1, x + w, y + 5, 0xFF333333);
			g.fill(x, y + 1, x + (int) Math.round(w * l.bar()), y + 5, l.color());
			return y + 8;
		}
		if (l.text() != null) {
			g.text(font, l.text(), x + l.indent(), y, l.color(), true);
		}
		return y + font.lineHeight + 1;
	}
}
