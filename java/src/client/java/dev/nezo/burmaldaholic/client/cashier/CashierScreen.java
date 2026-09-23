package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Cashier screen (UI.md §3). Buttons flow into rows so longer Russian labels wrap instead of overflowing. */
public class CashierScreen extends CasinoTableScreen {
	private static final int PAD = 8;
	private EditBox amount;
	private String amountText = "";
	private int flowX;
	private int flowY;
	private int withdrawLineY;

	public CashierScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.cashier.title"), 300, 196);
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		if (minecraft != null) {
			rebuild();
		}
	}

	private void rebuild() {
		if (amount != null) {
			amountText = amount.getValue();
		}
		clearWidgets();
		CompoundTag s = state();
		flowX = PAD;
		flowY = 22;
		// Deposit
		flow(Component.translatable("gui.burmaldaholic.cashier.deposit_all"), b -> sendAction("deposit_all"), s.getLongOr("carried_chips", 0) > 0);
		flow(Component.translatable("gui.burmaldaholic.cashier.deposit_held"), b -> sendAction("deposit_held"), s.getLongOr("held_chips", 0) > 0);
		newRow();
		// Withdraw
		amount = new EditBox(font, leftPos + flowX, topPos + flowY, 70, 20, Component.translatable("gui.burmaldaholic.common.amount"));
		amount.setMaxLength(12);
		amount.setResponder(v -> {
			if (!v.chars().allMatch(Character::isDigit)) {
				amount.setValue(v.replaceAll("\\D", ""));
			}
		});
		amount.setHint(Component.translatable("gui.burmaldaholic.common.amount"));
		amount.setValue(amountText);
		addRenderableWidget(amount);
		flowX += 74;
		for (int d : ChipMath.DENOMINATIONS) {
			flow(Texts.number(d), b -> add(d), true, 24);
		}
		flow(Component.translatable("gui.burmaldaholic.common.max"), b -> amount.setValue(Long.toString(s.getLongOr("withdrawable", 0))), true);
		flow(Component.translatable("gui.burmaldaholic.cashier.withdraw"), b -> withdraw(), !s.getBooleanOr("in_default", false));
		newRow();
		withdrawLineY = flowY;
		flowY += 12;
		// Exchange
		int buy = s.getIntOr("buy_rate", 8);
		int sell = s.getIntOr("sell_rate", 10);
		flow(Component.translatable("gui.burmaldaholic.cashier.buy", Texts.chipsAcc(buy)), b -> exchange("buy", 1), true);
		flow(Component.translatable("gui.burmaldaholic.cashier.times", Texts.number(10)), b -> exchange("buy", 10), true, 24);
		newRow();
		flow(Component.translatable("gui.burmaldaholic.cashier.sell", Texts.chipsAcc(sell)), b -> exchange("sell", 1), true);
		flow(Component.translatable("gui.burmaldaholic.cashier.times", Texts.number(10)), b -> exchange("sell", 10), true, 24);
		if (s.getBooleanOr("nether", false)) {
			newRow();
			flow(Component.translatable("gui.burmaldaholic.cashier.buy_gold", Texts.chipsAcc(s.getIntOr("gold_buy_rate", 3))), b -> exchange("buy_gold", 1), true);
			flow(Component.translatable("gui.burmaldaholic.cashier.times", Texts.number(10)), b -> exchange("buy_gold", 10), true, 24);
			newRow();
			flow(Component.translatable("gui.burmaldaholic.cashier.sell_gold", Texts.chipsAcc(s.getIntOr("gold_sell_rate", 12))), b -> exchange("sell_gold", 1), true);
			flow(Component.translatable("gui.burmaldaholic.cashier.times", Texts.number(10)), b -> exchange("sell_gold", 10), true, 24);
		}
	}

	private void flow(Component label, Button.OnPress onPress, boolean active) {
		flow(label, onPress, active, 40);
	}

	private void flow(Component label, Button.OnPress onPress, boolean active, int minWidth) {
		int w = Math.max(minWidth, font.width(label) + 8);
		if (flowX + w > imageWidth - PAD && flowX > PAD) {
			newRow();
		}
		Button b = button(label, flowX, flowY, w, onPress);
		b.active = active;
		flowX += w + 4;
	}

	private void newRow() {
		flowX = PAD;
		flowY += 24;
	}

	private void add(int chips) {
		long current = amount.getValue().isEmpty() ? 0 : parse(amount.getValue());
		amount.setValue(Long.toString(Math.max(0, current) + chips));
	}

	private static long parse(String v) {
		try {
			return Long.parseLong(v);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void withdraw() {
		long value = parse(amount.getValue());
		if (value <= 0) {
			showError(Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return;
		}
		CompoundTag args = new CompoundTag();
		args.putLong("amount", value);
		args.putInt("denom", 0);
		amount.setValue("");
		sendAction("withdraw", args);
	}

	private void exchange(String action, int count) {
		CompoundTag args = new CompoundTag();
		args.putInt("count", count);
		sendAction(action, args);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		graphics.text(font, balance, imageWidth - PAD - font.width(balance), titleLabelY, 0xFFFFD700, true);
		CompoundTag s = state();
		long withdrawable = s.getLongOr("withdrawable", 0);
		long debt = s.getLongOr("debt", 0);
		Component line;
		if (s.getBooleanOr("in_default", false)) {
			line = Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked");
		} else if (debt > 0) {
			line = Component.translatable("gui.burmaldaholic.cashier.withdrawable_loan", Texts.number(withdrawable), Texts.number(debt));
		} else {
			line = Component.translatable("gui.burmaldaholic.cashier.withdrawable", Texts.number(withdrawable));
		}
		graphics.text(font, line, PAD, withdrawLineY + 2, s.getBooleanOr("in_default", false) ? ERROR : 0xFFDDDDDD, true);
	}
}
