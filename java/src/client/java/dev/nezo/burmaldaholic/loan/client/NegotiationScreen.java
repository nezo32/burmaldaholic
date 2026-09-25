package dev.nezo.burmaldaholic.loan.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.loan.net.LoanActionPayload;
import dev.nezo.burmaldaholic.loan.net.LoanUiPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.ScreenEntrance;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Collector negotiation dialog (GAME_DESIGN.md §5.5, UI.md §10): the leader's demand, balance, a
 * countdown and Pay in full / Pay half / Refuse. Closing it (or letting the timer run out) counts as
 * Refuse on the server; the server also closes it when the squad turns hostile or leaves.
 */
final class NegotiationScreen extends Screen {
	private static final int PAD = 8;
	private final CompoundTag state;
	private final List<FormattedCharSequence> lineText = new ArrayList<>();
	private int ticksLeft;
	private int left;
	private int top;
	private int panelW;
	private ScreenEntrance entrance;
	private int panelH;
	private int textY;
	private int timerY;
	private boolean answered;

	NegotiationScreen(CompoundTag state) {
		super(Component.translatable("gui.burmaldaholic.loan.negotiate.title"));
		this.state = state;
		this.ticksLeft = state.getIntOr("ticks", 200);
	}

	@Override
	protected void init() {
		if (entrance == null) entrance = ScreenEntrance.install(this, () -> new UiLayout.Rect(left, top, panelW, panelH));
		panelW = Math.min(300, width - 16);
		left = (width - panelW) / 2;
		int inner = panelW - 2 * PAD;
		lineText.clear();
		Component line = LoanClientModule.readComponent(state, "line");
		if (line != null) {
			lineText.addAll(font.split(line, inner));
		}
		lineText.addAll(font.split(Component.translatable("gui.burmaldaholic.common.balance", Texts.number(state.getLongOr("balance", 0))), inner));
		// measure buttons in flow rows
		List<Component> labels = new ArrayList<>();
		List<String> actions = new ArrayList<>();
		if (state.getBooleanOr("can_all", false)) {
			labels.add(Component.translatable("gui.burmaldaholic.loan.negotiate.pay_all", Texts.chips(state.getLongOr("owed", 0))));
			actions.add("pay_all");
		}
		if (state.getBooleanOr("can_part", false)) {
			labels.add(Component.translatable("gui.burmaldaholic.loan.negotiate.pay_half", Texts.chips(state.getLongOr("part", 0))));
			actions.add("pay_part");
		}
		labels.add(Component.translatable("gui.burmaldaholic.loan.negotiate.refuse"));
		actions.add("refuse");
		int rows = 1;
		int x = 0;
		for (Component l : labels) {
			int w = Math.max(60, font.width(l) + 10);
			if (x > 0 && x + w > inner) {
				rows++;
				x = 0;
			}
			x += w + 4;
		}
		panelH = PAD + 14 + lineText.size() * 10 + 4 + 12 + rows * 24 + PAD;
		top = Math.max(4, (height - panelH) / 2);
		textY = top + PAD + 14;
		timerY = textY + lineText.size() * 10 + 4;
		int by = timerY + 14;
		int bx = left + PAD;
		for (int i = 0; i < labels.size(); i++) {
			Component l = labels.get(i);
			int w = Math.max(60, font.width(l) + 10);
			if (bx > left + PAD && bx + w > left + panelW - PAD) {
				bx = left + PAD;
				by += 24;
			}
			String action = actions.get(i);
			addRenderableWidget(new CasinoButton(bx, by, w, 20, l, "pay_all".equals(action) ? CasinoButton.Style.PRIMARY
				: "refuse".equals(action) ? CasinoButton.Style.DANGER : CasinoButton.Style.SECONDARY, b -> answer(action)));
			bx += w + 4;
		}
	}

	private void answer(String action) {
		if (answered) {
			return;
		}
		answered = true;
		ClientPlayNetworking.send(new LoanActionPayload(LoanUiPayload.NEGOTIATE, action, 0, 0));
		onClose();
	}

	@Override
	public void tick() {
		if (--ticksLeft <= 0) {
			onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		// J-L2 kit: the Loan Shark's steel dossier (extras.md §8.4)
		UiLayout.Rect r = new UiLayout.Rect(left - 6, top - 6, panelW + 12, panelH + 12);
		CasinoUi.backdrop(graphics, CasinoTheme.LOAN, r);
		CasinoUi.sprite(graphics, dev.nezo.burmaldaholic.client.ui.UiSprites.PAGE_LOAN, left, top, panelW, panelH, 0xF00E1A1E, CasinoPalette.CHIP_RED_DARK);
		CasinoUi.frame(graphics, CasinoTheme.LOAN, r);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.text(font, title, left + PAD, top + PAD, 0xFFFF6060, true);
		int y = textY;
		for (FormattedCharSequence l : lineText) {
			graphics.text(font, l, left + PAD, y, 0xFFE8E8E8, true);
			y += 10;
		}
		long seconds = Math.max(0, (ticksLeft + 19) / 20);
		graphics.text(font, Component.translatable("gui.burmaldaholic.loan.negotiate.timer", Texts.plural("unit.burmaldaholic.second", seconds)),
			left + PAD, timerY, 0xFFFFD35A, true);
	}
}
