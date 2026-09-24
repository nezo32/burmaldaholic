package dev.nezo.burmaldaholic.loan.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.LedgerLayout;
import dev.nezo.burmaldaholic.core.ui.LoanLook;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import dev.nezo.burmaldaholic.loan.net.LoanActionPayload;
import dev.nezo.burmaldaholic.loan.net.LoanUiPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Loan Shark screen (UI.md §10) in the Loan Shark's dark look (docs/design/visual/extras.md §8.4, mockup
 * {@code extras_menu_loan.png}; lane J-L2): the gunmetal dossier on the night harbour, the shark portrait (72²; the
 * Piglin moneylender is rendered from its entity) with a cheeky bubble, the debt meter with the skull, the active
 * contract on parchment with the OVERDUE stamp, the loan products as offer cards (locked ones chained, with the VIP
 * tier needed), <i>Pay all</i> as a danger button, an amount field + <i>Pay</i>, <i>Close</i>. Taking a loan asks for a
 * signature on the parchment first. Server-driven: renders {@link LoanUiPayload} state, sends {@link LoanActionPayload}.
 * Motion: the shared entrance; the portrait slides in from the left (250 ms {@code outCubic}); reduce motion: static.
 */
final class LoanScreen extends CasinoScreen {
	private static final int BONE = CasinoPalette.BONE;
	private static final int RED = CasinoPalette.CHIP_RED_LIGHT;
	private static final int PAGE_Y = 28;
	private static final int COL_W = 128;

	private CompoundTag state;
	private int confirmIndex = -1;
	private @Nullable EditBox amount;
	private String amountText = "";

	LoanScreen(CompoundTag state) {
		super(Component.translatable("gui.burmaldaholic.loan.title"));
		this.state = state;
	}

	void accept(CompoundTag newState) {
		this.state = newState;
		if (!newState.getBooleanOr("can_take", false)) confirmIndex = -1;
		if (newState.contains("message") && !newState.getBooleanOr("error", false)) {
			confirmIndex = -1; // loan taken / paid: back to the overview
			amountText = "";
		}
		Component msg = component("message");
		if (msg != null && newState.getBooleanOr("error", false)) showError(msg);
		if (minecraft != null) rebuildWidgets();
	}

	@Override
	protected CasinoTheme theme() {
		return CasinoTheme.LOAN;
	}

	@Override
	protected boolean showBanner() {
		return false;
	}

	@Override
	protected boolean showBalance() {
		return false;
	}

	private boolean piglin() {
		return state.getBooleanOr("piglin", false);
	}

	private long owed() {
		return state.getLongOr("owed", 0);
	}

	private String status() {
		return state.getStringOr("status", "none");
	}

	private @Nullable Component component(String key) {
		return LoanClientModule.readComponent(state, key);
	}

	private @Nullable LivingEntity portraitEntity() {
		if (!piglin() || minecraft == null || minecraft.level == null) return null;
		Entity e = minecraft.level.getEntity(state.getIntOr("entity", -1));
		return e instanceof LivingEntity living ? living : null;
	}

	// ---- geometry (panel-local) ---------------------------------------------------------------------------------------

	private int colX() {
		return panel.w() - 16 - 8 - COL_W;
	}

	private int leftW() {
		return colX() - 24 - 12;
	}

	// ---- widgets ------------------------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		super.init();
		if (amount != null) amountText = amount.getValue();
		amount = null;
		ListTag products = state.getListOrEmpty("products");
		boolean canTake = state.getBooleanOr("can_take", false);
		int cx = px(colX());
		int y = py(PAGE_Y + 22);
		int maxCards = owed() > 0 ? 2 : 5;
		for (int i = 0; i < Math.min(maxCards, products.size()); i++) {
			CompoundTag p = products.getCompoundOrEmpty(i);
			boolean locked = p.getBooleanOr("locked", false);
			int index = p.getIntOr("index", i);
			OfferCard card = new OfferCard(cx, y, COL_W, 24, p, locked, () -> {
				confirmIndex = index;
				rebuildWidgets();
			});
			card.active = canTake && !locked;
			if (locked) card.setTooltip(Tooltip.create(Component.translatable("gui.burmaldaholic.loan.locked_vip", VipTiers.name(p.getIntOr("min_tier", 0)))));
			addRenderableWidget(card);
			y += 27;
		}
		int bottom = py(panel.h() - 16 - 8);
		Component close = Component.translatable("gui.burmaldaholic.common.close");
		if (owed() > 0) {
			Component payAll = Component.translatable("gui.burmaldaholic.loan.pay_all", Texts.number(owed()));
			button(payAll, cx, bottom - 68, COL_W, 20, CasinoButton.Style.DANGER, b -> send("pay_all", 0, 0));
			amount = new EditBox(font, cx, bottom - 44, 60, 20, Component.translatable("gui.burmaldaholic.loan.pay_amount"));
			amount.setMaxLength(12);
			amount.setResponder(v -> {
				if (!v.chars().allMatch(Character::isDigit)) amount.setValue(v.replaceAll("\\D", ""));
			});
			amount.setHint(Component.translatable("gui.burmaldaholic.common.amount"));
			amount.setValue(amountText);
			add(amount);
			button(Component.translatable("gui.burmaldaholic.loan.pay"), cx + 62, bottom - 44, COL_W - 62, 20, CasinoButton.Style.SECONDARY, b -> pay());
		}
		int closeW = CasinoButton.width(font, close, 60, false);
		button(close, cx + COL_W - closeW, bottom - 20, closeW, 20, CasinoButton.Style.SECONDARY, b -> onClose());
		if (confirmIndex >= 0 && canTake) {
			int sx = px(24) + 8;
			int sy = py(PAGE_Y + 8 + 146) - 2;
			Component sign = Component.translatable("gui.burmaldaholic.loan.sign");
			int sw = CasinoButton.width(font, sign, 60, false);
			button(sign, sx, sy, sw, 18, CasinoButton.Style.PRIMARY, b -> send("take", confirmIndex, 0));
			Component cancel = Component.translatable("gui.burmaldaholic.common.cancel");
			button(cancel, sx + sw + 4, sy, CasinoButton.width(font, cancel, 50, false), 18, CasinoButton.Style.SECONDARY, b -> {
				confirmIndex = -1;
				rebuildWidgets();
			});
		}
	}

	private void pay() {
		String v = amount == null ? "" : amount.getValue();
		long n;
		try {
			n = Long.parseLong(v);
		} catch (NumberFormatException e) {
			n = -1;
		}
		if (n <= 0) {
			showError(Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return;
		}
		send("pay", 0, n);
	}

	private void send(String action, int index, long value) {
		ClientPlayNetworking.send(new LoanActionPayload(LoanUiPayload.LOAN, action, index, value));
	}

	@Override
	public void removed() {
		ClientPlayNetworking.send(new LoanActionPayload(LoanUiPayload.LOAN, "close", 0, 0));
		super.removed();
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------------

	@Override
	protected void extractFrame(GuiGraphicsExtractor g, CasinoTheme t) {
		CasinoUi.sprite(g, UiSprites.SHELL_LOAN, panel.x(), panel.y(), panel.w(), panel.h(), 0xFF0E1A1E, RED);
		Component name = Component.translatable(piglin() ? "entity.burmaldaholic.piglin_moneylender" : "gui.burmaldaholic.loan.title");
		CasinoUi.header(g, font, name, px(LedgerLayout.HEADER_X), py(LedgerLayout.HEADER_Y), Math.max(LedgerLayout.HEADER_W, font.width(name) + 24));
		CasinoUi.balancePlaque(g, font, state.getLongOr("balance", 0), panel.right() - 16 - LedgerLayout.PLAQUE_W, py(LedgerLayout.HEADER_Y),
			LedgerLayout.PLAQUE_W, CasinoPalette.GOLD);
		CasinoUi.sprite(g, UiSprites.PAGE_LOAN, px(16), py(PAGE_Y), panel.w() - 32, panel.h() - PAGE_Y - 16, 0xFF0E1A1E, CasinoPalette.CHIP_RED_DARK);
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = px(24);
		int y = py(PAGE_Y + 8);
		int lw = leftW();
		LoanLook.Mood mood = LoanLook.mood(status());
		// portrait slides in from the left
		long age = openAge();
		int slide = FxSettings.reduceMotion() ? 0 : (int) Math.round(-24 * (1 - dev.nezo.burmaldaholic.core.anim.Ease.OUT_CUBIC.apply(Math.min(1, age / 250.0))));
		g.enableScissor(x - 2, y - 2, x + 76, y + 76);
		g.fill(x - 1, y - 1, x + 73, y + 73, 0xFF0A1214);
		LivingEntity piglin = portraitEntity();
		if (piglin != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + slide, y, x + 72 + slide, y + 72, 30, 0.0625F, mouseX, mouseY, piglin);
		} else {
			CasinoUi.sheet(g, UiSprites.SHARK, 0, 0, 72, 72, x + slide, y);
		}
		g.disableScissor();
		// the cheeky bubble
		Component line = Component.translatable(mood.key());
		int bw = Math.min(lw + 40, font.width(line) + 16);
		CasinoUi.sprite(g, UiSprites.BUBBLE_CHEEKY, x + 76, y + 2, bw, 18, 0xFFF4ECF8, CasinoPalette.CHIP_RED);
		g.text(font, CasinoUi.fit(font, line, bw - 12), x + 84, y + 7, CasinoPalette.CHIP_RED_DARK, false);
		Component name = Component.translatable(piglin() ? "entity.burmaldaholic.piglin_moneylender" : "gui.burmaldaholic.loan.title");
		FxText.outlined(g, font, name.copy().withStyle(ChatFormatting.BOLD), x + 78, y + 28, RED, CasinoPalette.INK);
		long owed = owed();
		long ticksLeft = state.getLongOr("ticks_left", 0);
		if (owed > 0) {
			CasinoUi.text(g, font, Component.translatable("gui.burmaldaholic.loan.you_owe", Texts.number(owed)), x + 78, y + 40, lw - 78, BONE);
			Component when = "default".equals(status())
				? Component.translatable("gui.burmaldaholic.loan.overdue_by", Texts.plural("unit.burmaldaholic.day", LoanLook.overdueDays(-ticksLeft)))
				: Component.translatable("gui.burmaldaholic.loan.due_in", dhm(ticksLeft));
			CasinoUi.text(g, font, when, x + 78, y + 50, lw - 78, "default".equals(status()) ? RED : CasinoPalette.GOLD);
		} else {
			List<FormattedCharSequence> st = font.split(component("status_line") == null ? Component.empty() : component("status_line"), lw - 78);
			for (int i = 0; i < Math.min(3, st.size()); i++) g.text(font, st.get(i), x + 78, y + 40 + i * 10, BONE, true);
		}
		// debt meter
		int my = y + 80;
		CasinoUi.sprite(g, UiSprites.DEBT_METER, x, my, lw, 12, 0xFF140810, CasinoPalette.CHIP_RED_DARK);
		int fw = UiLayout.fillPixels(lw - 16, UiLayout.barFill(LoanLook.debtFill(owed, state.getLongOr("principal", 0)), age, FxSettings.reduceMotion()));
		if (fw > 0 && !CasinoUi.sprite(g, UiSprites.Fill.RED.id, x + 2, my + 3, fw, 6)) g.fill(x + 2, my + 3, x + 2 + fw, my + 9, CasinoPalette.CHIP_RED);
		CasinoUi.sprite(g, UiSprites.DEBT_SKULL, x + lw - 12, my, 12, 12);
		if ("default".equals(status())) g.text(font, Component.translatable("gui.burmaldaholic.loan.collectors_coming"), x, my + 16, BONE, true);
		// parchment: the contract, the confirmation, or the terms of the house
		int cy = y + 108;
		int ch = panel.h() - 16 - 8 - (cy - panel.y());
		CasinoUi.sprite(g, UiSprites.CONTRACT, x, cy, lw, ch, 0xFFE8DCC0, 0xFF8A6A3A);
		int ink = 0xFF3A2410;
		ListTag products = state.getListOrEmpty("products");
		if (confirmIndex >= 0 && confirmIndex < products.size()) {
			CompoundTag p = products.getCompoundOrEmpty(confirmIndex);
			g.text(font, Component.translatable("gui.burmaldaholic.loan.confirm_title").withStyle(ChatFormatting.BOLD), x + 8, cy + 6, CasinoPalette.CHIP_RED_DARK,
				false);
			List<FormattedCharSequence> lines = font.split(Component.translatable("gui.burmaldaholic.loan.confirm", Texts.chipsAcc(p.getLongOr("principal", 0)),
				Texts.chipsAcc(p.getLongOr("due", 0)), Texts.plural("unit.burmaldaholic.day", p.getIntOr("days", 0))), lw - 16);
			for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), x + 8, cy + 17 + i * 9, ink, false);
		} else if (owed > 0) {
			String product = state.getStringOr("product", "");
			Component pname = Component.translatable("gui.burmaldaholic.loan.product." + (product.isEmpty() ? "pocket" : product));
			g.text(font, CasinoUi.fit(font, Component.translatable("gui.burmaldaholic.loan.contract.terms", pname, Texts.number(state.getLongOr("principal", 0)),
				Texts.number(state.getLongOr("due_total", owed))), lw - 16), x + 8, cy + 7, ink, false);
			g.text(font, CasinoUi.fit(font, Component.translatable("gui.burmaldaholic.loan.due_in", dhm(Math.max(0, ticksLeft))),
				"default".equals(status()) ? lw - 110 : lw - 16), x + 8, cy + 18,
				ink, false);
			String sig = "× ________________"; // literal-ok: signature rule
			g.text(font, sig, x + 8, cy + ch - 14, 0xFF6A5030, false);
			if ("default".equals(status())) {
				Component word = Component.translatable("gui.burmaldaholic.loan.stamp.overdue").withStyle(ChatFormatting.BOLD);
				int stampW = Math.max(72, font.width(word) + 16); // the RU word is longer: the stamp stretches
				int sx = x + lw - stampW - 12;
				int sy = cy + 10;
				CasinoUi.sprite(g, UiSprites.STAMP_OVERDUE, sx, sy, stampW, 28);
				FormattedCharSequence ws = CasinoUi.fit(font, word, stampW - 8);
				g.pose().pushMatrix();
				g.pose().translate(sx + stampW / 2f, sy + 14);
				g.pose().rotate((float) Math.toRadians(-4));
				g.text(font, ws, -font.width(ws) / 2, -4, CasinoPalette.CHIP_RED, false);
				g.pose().popMatrix();
			}
		} else {
			int ly = cy + 7;
			ly = para(g, Component.translatable("gui.burmaldaholic.loan.interest", Texts.raw(state.getStringOr("rate", "0"))), x + 8, ly, lw - 16, ink);
			Component gs = component("good_standing");
			if (gs != null) ly = para(g, gs, x + 8, ly, lw - 16, 0xFF1E5E1E);
			int locked = state.getIntOr("locked", 0);
			if (locked > 0) para(g, Component.translatable("gui.burmaldaholic.loan.locked_count", Texts.number(locked)), x + 8, ly, lw - 16, 0xFF6A5030);
		}
		// right column header and notes
		int cx = px(colX());
		g.text(font, Component.translatable("gui.burmaldaholic.loan.borrow"), cx, y, RED, true);
		if (owed > 0) {
			int ny = py(PAGE_Y + 22) + 2 * 27;
			Component note = "default".equals(status()) ? Component.translatable("gui.burmaldaholic.loan.no_new_overdue")
				: component("status_line") == null ? Component.empty() : component("status_line");
			List<FormattedCharSequence> nl = font.split(note, COL_W);
			for (int i = 0; i < Math.min(2, nl.size()); i++) g.text(font, nl.get(i), cx, ny + i * 10, BONE, true);
		}
	}

	private int para(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
		for (FormattedCharSequence s : font.split(text, w)) {
			g.text(font, s, x, y, color, false);
			y += 9;
		}
		return y + 2;
	}

	private static Component dhm(long ticks) {
		long t = Math.max(0, ticks);
		return Component.translatable("hud.burmaldaholic.time.dhm", Texts.number(t / 24000), Texts.raw(Numbers.hoursMinutes(t % 24000)));
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Component msg = component("message");
		if (msg != null && !state.getBooleanOr("error", false)) {
			FxText.wrappedCentered(g, font, msg, px(24 + leftW() / 2), py(PAGE_Y + 8 + 94), leftW(), 1, CasinoPalette.BONUS, CasinoPalette.INK);
		}
	}

	/** A loan product card (extras.md §8.4 {@code offer} / {@code offer_locked}): name and "borrow → repay". */
	private final class OfferCard extends AbstractButton {
		private final CompoundTag product;
		private final boolean locked;
		private final Runnable onPress;

		OfferCard(int x, int y, int w, int h, CompoundTag product, boolean locked, Runnable onPress) {
			super(x, y, w, h, Component.translatable("gui.burmaldaholic.loan.product." + product.getStringOr("id", "pocket")));
			this.product = product;
			this.locked = locked;
			this.onPress = onPress;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			onPress.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY() - (active && isHoveredOrFocused() ? 1 : 0);
			CasinoUi.sprite(g, locked ? UiSprites.OFFER_LOCKED : UiSprites.OFFER, x, y, getWidth(), getHeight(), 0xFF1A2A2E, CasinoPalette.CHIP_RED);
			if (active && isHoveredOrFocused()) g.outline(x - 1, y - 1, getWidth() + 2, getHeight() + 2, CasinoPalette.GOLD);
			int c1 = locked ? 0xFF6A7A88 : BONE;
			int c2 = locked ? 0xFF4A5A68 : active ? BONE : 0xFF8A9AA8;
			CasinoUi.text(g, font, getMessage(), x + 6, y + 3, getWidth() - 24, c1);
			Component second = locked ? Component.translatable("gui.burmaldaholic.loan.locked_vip", VipTiers.name(product.getIntOr("min_tier", 0)))
				: Component.translatable("gui.burmaldaholic.loan.offer_line", Texts.number(product.getLongOr("principal", 0)), Texts.number(product.getLongOr("due", 0)));
			CasinoUi.text(g, font, second, x + 6, y + 13, getWidth() - 24, c2);
			if (locked) {
				// padlock (drawn: the PvP kit's padlock is the same shape)
				int lx = x + getWidth() - 14;
				int ly = y + 8;
				g.fill(lx + 1, ly, lx + 7, ly + 1, 0xFFB0B8C0);
				g.fill(lx, ly + 1, lx + 1, ly + 4, 0xFFB0B8C0);
				g.fill(lx + 7, ly + 1, lx + 8, ly + 4, 0xFFB0B8C0);
				g.fill(lx - 1, ly + 4, lx + 9, ly + 10, 0xFFE8B830);
				g.fill(lx + 3, ly + 6, lx + 5, ly + 8, 0xFF5A3A08);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
