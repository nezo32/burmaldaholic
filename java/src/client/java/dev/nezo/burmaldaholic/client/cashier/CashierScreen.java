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
import dev.nezo.burmaldaholic.core.ui.CountingTray;
import dev.nezo.burmaldaholic.core.ui.LedgerLayout;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Cashier screen (UI.md §3) in the Casino Menu's ledger shell with the lobby backdrop (docs/design/visual/extras.md §8.3,
 * global.md §4.3; lane J-L2 J9): the <b>counting tray</b> on top, then ledger rows for deposit, withdraw (amount well,
 * chip "+" buttons, Max, the primary Withdraw) and the exchange (emerald / gold ingot rows). Buttons flow into rows so
 * longer Russian labels wrap instead of overflowing.
 *
 * <p>Counting tray ({@link CountingTray}): every move the server reports ({@code tray} in the state: the chip items
 * actually deposited or withdrawn per denomination; the greedy breakdown for emerald / gold exchanges and shop buys)
 * is replayed as chips flying into one well per denomination — from the bottom edge (your inventory) for a deposit,
 * from the balance plaque for a withdrawal — largest first, 280 ms flights with a ≤ 70 ms stagger; then the stacks
 * stay with their counts ("×N") under the "Deposited N" / "Withdrawn N" line until the next move. Click the tray to
 * skip; reduce motion shows the final stacks at once. The amount is the server's, never recomputed.
 */
public class CashierScreen extends CasinoTableScreen {
	private static final int W = 400;
	private static final int H = 240;
	private static final int PAGE_Y = 28;
	private static final int CX = 24;
	private static final int TRAY_Y = 36;
	private static final int TRAY_H = 44;
	/** Tray columns (small denominations on the left, like the wallet's pocket), 40 px each, right-aligned. */
	private static final int TRAY_COL_W = 40;
	private static final Identifier[] CHIP = new Identifier[ChipColumns.DENOMS.length];
	private static final Identifier[] CHIP_SIDE = new Identifier[ChipColumns.DENOMS.length];
	private static final String[] DENOM_LABEL = new String[ChipColumns.DENOMS.length];

	static {
		for (int i = 0; i < ChipColumns.DENOMS.length; i++) {
			CHIP[i] = UiSprites.sprite("fx/chip_" + ChipColumns.DENOMS[i]);
			CHIP_SIDE[i] = UiSprites.sprite("fx/chip_side_" + ChipColumns.DENOMS[i]);
			DENOM_LABEL[i] = Integer.toString(ChipColumns.DENOMS[i]); // literal-ok: denomination
		}
	}
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
	/** The move being shown (null = idle), when it started, and its pre-built labels (no per-frame allocation). */
	private @Nullable CountingTray tray;
	private long trayAt = -1;
	/** Chips fly in from the bottom edge (deposit / credit) or from the balance plaque (withdraw / paid). */
	private boolean trayFromBottom;
	private Component trayHead = Component.empty();
	private Component trayChips = Component.empty();
	private final String[] trayCountLabel = new String[ChipColumns.DENOMS.length];
	private int trayHeadColor;
	/** Last move sequence seen ({@link Integer#MIN_VALUE} = no state yet: the move that was there on open is not replayed). */
	private int traySeq = Integer.MIN_VALUE;
	private int soundsPlayed;
	private static final Component TRAY_IDLE = Component.translatable("gui.burmaldaholic.cashier.tray");
	private static final Component COUNTING = Component.translatable("gui.burmaldaholic.cashier.counting");

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
		CompoundTag move = newState.getCompoundOrEmpty("tray");
		int seq = move.getIntOr("seq", 0);
		if (traySeq != Integer.MIN_VALUE && seq != traySeq && seq != 0) startTray(move, now);
		traySeq = seq;
		ticker.retarget(bal, now, FxSettings.reduceMotion() || lastBalance == Long.MIN_VALUE);
		lastBalance = bal;
		if (minecraft != null) rebuild();
	}

	/** Starts replaying a server move ({@code tray}: kind, amount, chip counts per denomination). */
	private void startTray(CompoundTag move, long now) {
		long[] counts = new long[ChipColumns.DENOMS.length];
		for (int i = 0; i < counts.length; i++) counts[i] = Math.max(0, move.getLongOr(Integer.toString(ChipColumns.DENOMS[i]), 0));
		String kind = move.getStringOr("kind", "deposit");
		long amount = Math.max(0, move.getLongOr("amount", 0));
		tray = new CountingTray(counts);
		trayAt = now;
		soundsPlayed = 0;
		trayFromBottom = kind.equals("deposit") || kind.equals("credit");
		String key = switch (kind) {
			case "withdraw" -> "gui.burmaldaholic.cashier.withdrawn";
			case "credit" -> "gui.burmaldaholic.cashier.credited";
			case "paid" -> "gui.burmaldaholic.cashier.paid";
			default -> "gui.burmaldaholic.cashier.deposited";
		};
		trayHead = Component.translatable(key, Texts.number(amount));
		trayHeadColor = trayFromBottom ? CasinoPalette.BONUS : CasinoPalette.GOLD;
		trayChips = Texts.plural("unit.burmaldaholic.chip", tray.chips());
		for (int i = 0; i < counts.length; i++) trayCountLabel[i] = counts[i] > 0 ? "×" + Numbers.format(counts[i]) : ""; // literal-ok: count
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (tray != null && mx >= leftPos + CX && mx < leftPos + W - CX && my >= topPos + TRAY_Y && my < topPos + TRAY_Y + trayH()) {
			long done = tray.flightsDoneMs();
			if (Util.getMillis() - trayAt < done) {
				trayAt = Util.getMillis() - done; // skip to the final stacks
				soundsPlayed = tray.flights();
				return true;
			}
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
		flowY = trayH() > 0 ? TRAY_Y + trayH() + 6 : TRAY_Y;
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
		if (!shopTab && trayH() > 0) drawTray(g, now);
	}

	/**
	 * The tray's height: none at the Nether cashier, whose two gold rows need the room (with the tray its last row
	 * would fall off the page); the moves are still told in chat there.
	 */
	private int trayH() {
		return state().getBooleanOr("nether", false) ? 0 : TRAY_H;
	}

	private void drawTray(GuiGraphicsExtractor g, long now) {
		int x = leftPos + CX;
		int y = topPos + TRAY_Y;
		int w = W - 2 * CX;
		CasinoUi.inset(g, x, y, w, TRAY_H);
		int n = ChipColumns.DENOMS.length;
		int colsX = x + w - 6 - n * TRAY_COL_W;
		int headW = colsX - x - 12;
		CountingTray t = tray;
		long age = t == null ? 0 : now - trayAt;
		boolean reduced = FxSettings.reduceMotion();
		if (t != null && reduced) age = Math.max(age, t.flightsDoneMs());
		boolean counting = t != null && age < t.flightsDoneMs();
		// head: "Chip tray" (idle) / "Counting…" / "Withdrawn 1,910" + "7 chips"
		if (t == null) {
			CasinoUi.text(g, font, TRAY_IDLE, x + 6, y + 6, headW, CasinoPalette.BONE_SHADE);
		} else {
			CasinoUi.text(g, font, counting ? COUNTING : trayHead, x + 6, y + 6, headW, counting ? CasinoPalette.BONE : trayHeadColor);
			if (!counting) CasinoUi.text(g, font, trayChips, x + 6, y + 18, headW, CasinoPalette.BONE_SHADE);
		}
		int base = y + 32; // bottom of the lowest disc
		// wells: small denominations on the left (the wallet's order); columns index ChipColumns.DENOMS (largest first)
		for (int k = 0; k < n; k++) {
			int di = n - 1 - k;
			int cx = colsX + k * TRAY_COL_W + TRAY_COL_W / 2;
			g.fill(cx - 10, y + 12, cx + 10, base + 1, 0x40000000);
			g.fill(cx - 10, base + 1, cx + 10, base + 2, 0x30FFFFFF);
			String dl = DENOM_LABEL[di];
			g.text(font, dl, cx - font.width(dl) / 2, y + 3, CasinoPalette.BONE_SHADE, false);
			if (t == null || t.count(di) <= 0) continue;
			int landed = 0;
			for (int j = 0; j < t.discs(di); j++) {
				double p = t.flight(di, j, age);
				int slotY = CountingTray.discY(base, j);
				if (p >= 1) {
					CasinoUi.sprite(g, CHIP_SIDE[di], cx - 6, slotY, 12, 3);
					landed++;
					continue;
				}
				if (p < 0) continue;
				// arc from the bottom edge (inventory) or the balance plaque (the house) into the slot
				double e = Ease.OUT_CUBIC.apply(p);
				double fromX = trayFromBottom ? cx : leftPos + W - 16 - LedgerLayout.PLAQUE_W / 2.0;
				double fromY = trayFromBottom ? topPos + H - 16 : topPos + LedgerLayout.HEADER_Y + 6;
				double px = fromX + (cx - fromX) * e;
				double py = fromY + (slotY - 5 - fromY) * e - 20 * 4 * e * (1 - e);
				CasinoUi.sprite(g, CHIP[di], (int) Math.round(px) - 4, (int) Math.round(py), 8, 8);
			}
			if (landed > 0 && landed == t.discs(di)) {
				CasinoUi.sprite(g, CHIP[di], cx - 4, CountingTray.discY(base, landed - 1) - 5, 8, 8); // the top chip, face up
			}
			if (!counting) {
				String cl = trayCountLabel[di];
				g.text(font, cl, cx - font.width(cl) / 2, base + 3, t.count(di) > t.discs(di) ? CasinoPalette.GOLD : CasinoPalette.BONE, false);
			}
		}
		if (t != null && !reduced && counting) {
			int landedAll = t.landed(age);
			if (landedAll > soundsPlayed) {
				soundsPlayed = landedAll;
				FxSounds.play("chip_stack", 0.6f, 1f + 0.05f * Math.min(8, landedAll));
			}
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
