package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.BalanceTicker;
import dev.nezo.burmaldaholic.core.ui.ChipColumns;
import dev.nezo.burmaldaholic.core.ui.LedgerLayout;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cashier screen (UI.md §3) in the Casino Menu's ledger shell with the lobby backdrop (docs/design/visual/extras.md §8.3,
 * global.md §4.3; lane J-L2 J9): the <b>counting tray</b> on top, then ledger rows for deposit, withdraw (amount well,
 * chip "+" buttons, Max, the primary Withdraw) and the exchange (emerald / gold ingot rows). Buttons flow into rows so
 * longer Russian labels wrap instead of overflowing.
 *
 * <p>Counting tray: when the balance changes while the cashier is open, the change is replayed as chip stack columns
 * (largest denomination first, 70 ms stagger, 280 ms flights from the bottom edge for a deposit / into it for a
 * withdrawal), then the "Deposited N" / "Withdrawn N" line. The columns show the greedy breakdown of the change (the
 * exact amount; the server does not send its per-denomination breakdown yet). Click the tray to skip; reduce motion
 * shows the final columns. The shown amount always equals the server's balance change.
 */
public class CashierScreen extends CasinoTableScreen {
	private static final int W = 400;
	private static final int H = 240;
	private static final int PAGE_Y = 28;
	private static final int CX = 24;
	private static final int TRAY_Y = 36;
	private static final int TRAY_H = 44;
	private static final int FLIGHT_MS = 280;
	private static final int STAGGER_MS = 70;
	private EditBox amount;
	private String amountText = "";
	private int flowX;
	private int flowY;
	private int withdrawLineY;
	private final java.util.List<int[]> rowBands = new java.util.ArrayList<>();
	/** UI.md §3 tabs: Cashier / Shop. */
	private boolean shopTab;
	private final BalanceTicker ticker = new BalanceTicker();
	private long lastBalance = Long.MIN_VALUE;
	private long trayAmount;
	private long trayAt = -1;
	private long[] trayCounts = new long[ChipColumns.DENOMS.length];
	private int soundsPlayed;

	public CashierScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.cashier.title"), W, H);
		this.titleLabelY = -10_000;
	}

	@Override
	protected CasinoTheme theme() {
		return CasinoTheme.LOBBY;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		long bal = newState.getLongOr("balance", 0);
		long now = Util.getMillis();
		if (!newState.contains("balance")) return; // the empty placeholder before the first server state
		if (lastBalance != Long.MIN_VALUE && bal != lastBalance) startTray(bal - lastBalance, now);
		ticker.retarget(bal, now, FxSettings.reduceMotion() || lastBalance == Long.MIN_VALUE);
		lastBalance = bal;
		if (minecraft != null) rebuild();
	}

	private void startTray(long delta, long now) {
		trayAmount = delta;
		trayAt = now;
		trayCounts = ChipColumns.greedy(Math.abs(delta));
		soundsPlayed = 0;
	}

	private int trayChips() {
		int n = 0;
		for (long c : trayCounts) n += (int) Math.min(10, c);
		return n;
	}

	private long trayEnd() {
		return (long) Math.max(0, trayChips() - 1) * STAGGER_MS + FLIGHT_MS + 1200;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (trayAt >= 0 && mx >= leftPos + CX && mx < leftPos + W - CX && my >= topPos + TRAY_Y && my < topPos + TRAY_Y + TRAY_H) {
			trayAt = Util.getMillis() - trayEnd() + 1000; // skip to the final columns
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---- widgets ------------------------------------------------------------------------------------------------------

	private void rebuild() {
		if (amount != null) amountText = amount.getValue();
		clearWidgets();
		rowBands.clear();
		CompoundTag s = state();
		flowX = CX + 4;
		flowY = TRAY_Y + TRAY_H + 6;
		ListTag shop = s.getListOrEmpty("shop");
		if (!shop.isEmpty()) {
			flow(Component.translatable("gui.burmaldaholic.cashier.title"), b -> selectTab(false), true, 41, shopTab ? null : CasinoButton.Style.PRIMARY)
				.icon(CasinoButton.Icon.tab(UiSprites.TabIcon.CASHIER));
			flow(Component.translatable("gui.burmaldaholic.cashier.shop"), b -> selectTab(true), true, 40, shopTab ? CasinoButton.Style.PRIMARY : null);
			newRow();
		} else {
			shopTab = false;
		}
		if (shopTab) {
			shop(s, shop);
			return;
		}
		// Deposit
		band();
		flow(Component.translatable("gui.burmaldaholic.cashier.deposit_all"), b -> sendAction("deposit_all"), s.getLongOr("carried_chips", 0) > 0, 40,
			CasinoButton.Style.PRIMARY);
		flow(Component.translatable("gui.burmaldaholic.cashier.deposit_held"), b -> sendAction("deposit_held"), s.getLongOr("held_chips", 0) > 0);
		newRow();
		// Withdraw
		band();
		amount = new EditBox(font, leftPos + flowX, topPos + flowY, 64, 20, Component.translatable("gui.burmaldaholic.common.amount"));
		amount.setMaxLength(12);
		amount.setResponder(v -> {
			if (!v.chars().allMatch(Character::isDigit)) amount.setValue(v.replaceAll("\\D", ""));
		});
		amount.setHint(Component.translatable("gui.burmaldaholic.common.amount"));
		amount.setValue(amountText);
		addRenderableWidget(amount);
		flowX += 68;
		for (int d : ChipMath.DENOMINATIONS) {
			flow(Texts.number(d), b -> add(d), true, 22, null).icon(CasinoButton.Icon.sprite(UiSprites.sprite("fx/chip_" + d), 8, 8));
		}
		flow(Component.translatable("gui.burmaldaholic.common.max"), b -> amount.setValue(Long.toString(s.getLongOr("withdrawable", 0))), true, 30, null);
		CasinoButton wd = flow(Component.translatable("gui.burmaldaholic.cashier.withdraw"), b -> withdraw(), !s.getBooleanOr("in_default", false), 40,
			CasinoButton.Style.PRIMARY);
		if (s.getBooleanOr("in_default", false)) wd.enabled(false, Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked"));
		newRow();
		withdrawLineY = flowY - 3;
		flowY += 10;
		// Exchange
		int buy = s.getIntOr("buy_rate", 8);
		int sell = s.getIntOr("sell_rate", 10);
		exchangeRow(Component.translatable("gui.burmaldaholic.cashier.buy", Texts.chipsAcc(buy)), "buy");
		exchangeRow(Component.translatable("gui.burmaldaholic.cashier.sell", Texts.chipsAcc(sell)), "sell");
		if (s.getBooleanOr("nether", false)) {
			exchangeRow(Component.translatable("gui.burmaldaholic.cashier.buy_gold", Texts.chipsAcc(s.getIntOr("gold_buy_rate", 3))), "buy_gold");
			exchangeRow(Component.translatable("gui.burmaldaholic.cashier.sell_gold", Texts.chipsAcc(s.getIntOr("gold_sell_rate", 12))), "sell_gold");
		}
	}

	private void exchangeRow(Component label, String action) {
		band();
		flowX += 20; // item icon
		flow(label, b -> exchange(action, 1), true);
		flow(Component.translatable("gui.burmaldaholic.cashier.times", Texts.number(10)), b -> exchange(action, 10), true, 24, null);
		newRow();
	}

	/** Marks the start of a ledger row band behind the next widgets (drawn in the background). */
	private void band() {
		rowBands.add(new int[] {flowY - 2, rowBands.size() % 2});
	}

	private void selectTab(boolean shop) {
		shopTab = shop;
		rebuild();
	}

	/** Shop tab: one "Buy X — price" button per offer; VIP-gated offers are disabled with a tooltip. */
	private void shop(CompoundTag s, ListTag shop) {
		int tier = s.getIntOr("vip", 0);
		withdrawLineY = -100;
		for (int i = 0; i < shop.size(); i++) {
			CompoundTag o = shop.getCompoundOrEmpty(i);
			String id = o.getStringOr("id", "");
			int minTier = o.getIntOr("min_tier", 0);
			Component label = Component.translatable("gui.burmaldaholic.cashier.shop.buy_item", Component.translatable(o.getStringOr("name", "")),
				Texts.chips(o.getLongOr("price", 0)));
			boolean allowed = tier >= minTier;
			band();
			CasinoButton button = flow(label, b -> {
				CompoundTag args = new CompoundTag();
				args.putString("id", id);
				sendAction("shop_buy", args);
			}, allowed && balance() >= o.getLongOr("price", 0));
			if (!allowed) button.enabled(false, Component.translatable("gui.burmaldaholic.common.requires_vip", VipTiers.name(minTier)));
			newRow();
		}
	}

	private CasinoButton flow(Component label, Consumer<CasinoButton> onPress, boolean active) {
		return flow(label, onPress, active, 40, null);
	}

	private CasinoButton flow(Component label, Consumer<CasinoButton> onPress, boolean active, int minWidth, CasinoButton.Style style) {
		int w = minWidth == 22 ? Math.max(22, font.width(label) + 8 + 11) : CasinoButton.width(font, label, minWidth, minWidth == 41);
		if (flowX + w > W - CX - 4 && flowX > CX + 4) newRow();
		CasinoButton b = addRenderableWidget(new CasinoButton(leftPos + flowX, topPos + flowY, w, 20, label,
			style == null ? CasinoButton.Style.SECONDARY : style, onPress));
		b.active = active;
		flowX += w + 3;
		return b;
	}

	private void newRow() {
		flowX = CX + 4;
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

	// ---- drawing ----------------------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractTransparentBackground(g);
		UiLayout.Rect r = new UiLayout.Rect(leftPos, topPos, W, H);
		CasinoUi.backdrop(g, CasinoTheme.LOBBY, r);
		CasinoUi.sprite(g, UiSprites.SHELL, r.x(), r.y(), W, H, 0xFF4A0A1C, CasinoPalette.GOLD);
		CasinoUi.header(g, font, title, leftPos + LedgerLayout.HEADER_X, topPos + LedgerLayout.HEADER_Y, LedgerLayout.HEADER_W);
		long now = Util.getMillis();
		int dir = ticker.direction(now);
		int color = dir == 0 ? CasinoPalette.GOLD : CasinoUi.mix(CasinoPalette.GOLD, dir > 0 ? CasinoPalette.BONUS : CasinoPalette.CHIP_RED, ticker.tint(now));
		CasinoUi.balancePlaque(g, font, ticker.value(now), leftPos + W - 16 - LedgerLayout.PLAQUE_W, topPos + LedgerLayout.HEADER_Y, LedgerLayout.PLAQUE_W,
			color);
		CasinoUi.sprite(g, UiSprites.PAGE, leftPos + 16, topPos + PAGE_Y, W - 32, H - PAGE_Y - 16, 0xFF1E0E2C, CasinoPalette.FRAME);
		for (int[] band : rowBands) CasinoUi.row(g, band[1], leftPos + CX, topPos + band[0], W - 2 * CX, 24);
		if (!shopTab) drawTray(g, now);
	}

	private void drawTray(GuiGraphicsExtractor g, long now) {
		int x = leftPos + CX;
		int y = topPos + TRAY_Y;
		int w = W - 2 * CX;
		CasinoUi.inset(g, x, y, w, TRAY_H);
		if (trayAt < 0) {
			g.text(font, Component.translatable("gui.burmaldaholic.cashier.tray"), x + 6, y + 4, CasinoPalette.BONE_SHADE, true);
			return;
		}
		long t = now - trayAt;
		boolean reduced = FxSettings.reduceMotion();
		if (reduced) t = Math.max(t, trayEnd() - 1200);
		boolean deposit = trayAmount > 0;
		int chipsTotal = trayChips();
		long flightsDone = (long) Math.max(0, chipsTotal - 1) * STAGGER_MS + FLIGHT_MS;
		Component head = t < flightsDone ? Component.translatable("gui.burmaldaholic.cashier.counting")
			: Component.translatable(deposit ? "gui.burmaldaholic.cashier.deposited" : "gui.burmaldaholic.cashier.withdrawn", Texts.number(Math.abs(trayAmount)));
		g.text(font, head, x + 6, y + 4, t < flightsDone ? CasinoPalette.BONE : deposit ? CasinoPalette.BONUS : CasinoPalette.GOLD, true);
		int base = y + TRAY_H - 6;
		int colX = x + 100;
		int colW = (w - 110) / ChipColumns.DENOMS.length;
		int k = 0;
		int landed = 0;
		for (int di = 0; di < ChipColumns.DENOMS.length; di++) {
			long count = trayCounts[di];
			if (count <= 0) continue;
			int denom = ChipColumns.DENOMS[di];
			int cx = colX + di * colW + colW / 2;
			int shown = (int) Math.min(10, count);
			int stacked = 0;
			for (int j = 0; j < shown; j++, k++) {
				long ct = t - (long) k * STAGGER_MS;
				int slotY = base - 3 - j * ChipColumns.PITCH;
				if (ct >= FLIGHT_MS) {
					stacked++;
					landed++;
					continue;
				}
				if (ct < 0) continue;
				// quadratic flight from the bottom edge (deposit) or to it (withdraw), apex 24 px above
				double p = Ease.OUT_CUBIC.apply(ct / (double) FLIGHT_MS);
				double from = deposit ? topPos + H - 16 : slotY;
				double to = deposit ? slotY : topPos + H - 16;
				double py = from + (to - from) * p - 24 * 4 * p * (1 - p);
				CasinoUi.sprite(g, UiSprites.sprite("fx/chip_" + denom), cx - 4, (int) Math.round(py), 8, 8);
			}
			int drawn = deposit ? stacked : shown - stacked;
			for (int j = 0; j < drawn; j++) CasinoUi.sprite(g, UiSprites.sprite("fx/chip_side_" + denom), cx - 6, base - 3 - j * ChipColumns.PITCH, 12, 3);
			String label = count > 10 ? "×" + Numbers.format(count) : Numbers.format(denom); // literal-ok: count
			g.text(font, label, cx - font.width(label) / 2, y + 4, CasinoPalette.BONE_SHADE, false);
		}
		if (!reduced && landed > soundsPlayed && t < flightsDone + 100) {
			soundsPlayed = landed;
			FxSounds.play("chip_stack", 0.6f, 1f + 0.05f * Math.min(8, landed));
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		if (error() != null) graphics.centeredText(font, error(), imageWidth / 2, imageHeight - 28, CasinoPalette.CHIP_RED_LIGHT);
		CompoundTag s = state();
		int iy = 0;
		for (int[] band : rowBands) {
			// exchange rows: the item icon on the left of the band
			iy++;
			if (iy >= 3 && !shopTab) {
				boolean gold = iy >= 5;
				graphics.fakeItem(new ItemStack(gold ? Items.GOLD_INGOT : Items.EMERALD), CX + 5, band[0] + 4);
			}
		}
		if (shopTab) return;
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
		CasinoUi.text(graphics, font, line, CX + 6, withdrawLineY + 2, W - 2 * CX - 12,
			s.getBooleanOr("in_default", false) ? CasinoPalette.CHIP_RED_LIGHT : CasinoPalette.BONE);
	}

	private Component error() {
		return errorLine();
	}
}
