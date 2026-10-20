package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterLayout;
import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Exchange tab of the cashier (EC-SPEC §1, §3): deposit / withdraw on the left, the exchange rows on the right (icon,
 * "You have N", {@code →9 [×1][×64][All]}, {@code ←10 [×1][×64][Max]}). Shift+click on a quantity button sends ×64.
 * Keys: ↑ / ↓ row, ← / → direction, Enter ×1, Shift+Enter ×64, Ctrl+Enter All / Max, A deposit all.
 *
 * <p>Targets: {@code exchange.deposit_all}, {@code exchange.deposit_held}, {@code exchange.chip.<denom>},
 * {@code exchange.max}, {@code exchange.clear}, {@code exchange.withdraw}, {@code exchange.row.<id>.<in|out>.<1|64|all>}.
 */
public final class ExchangeTab implements CounterTab {
	public static final String ID = "exchange";
	private final CounterShell shell;
	private long amount;
	private int selRow;
	private boolean selOut;
	private CounterLayout.Exchange layout;
	private CounterLayout.Money money;
	private final List<CompoundTag> rows = new ArrayList<>();
	private int[] denoms = new int[0];

	public ExchangeTab(CounterShell shell) {
		this.shell = shell;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("gui.burmaldaholic.cashier.tab.exchange");
	}

	private CompoundTag s() {
		return shell.host().state();
	}

	/** The rows of the current state (tests). */
	public List<CompoundTag> rows() {
		return List.copyOf(rows);
	}

	public int selectedRow() {
		return selRow;
	}

	public boolean selectedOut() {
		return selOut;
	}

	public long amount() {
		return amount;
	}

	@Override
	public void layout(Box body, HitMap hits) {
		rows.clear();
		ListTag list = s().getListOrEmpty("rows");
		for (int i = 0; i < list.size(); i++) rows.add(list.getCompoundOrEmpty(i));
		selRow = Math.max(0, Math.min(selRow, rows.size() - 1));
		denoms = Arrays.stream(ChipMath.DENOMINATIONS).sorted().toArray();
		layout = CounterLayout.exchange(body, rows.size());
		money = CounterLayout.money(layout.money(), denoms.length);
		boolean inDefault = s().getBooleanOr("in_default", false);
		hits.add("exchange.deposit_all", money.depositAll(), s().getLongOr("carried_chips", 0) > 0);
		hits.add("exchange.deposit_held", money.depositHeld(), s().getLongOr("held_chips", 0) > 0);
		for (int i = 0; i < denoms.length; i++) hits.add("exchange.chip." + denoms[i], money.chips().get(i), !inDefault);
		hits.add("exchange.max", money.max(), !inDefault);
		hits.add("exchange.clear", money.clear(), amount > 0);
		hits.add("exchange.withdraw", money.withdraw(), !inDefault && amount > 0);
		int visible = CounterLayout.visibleRows(layout);
		for (int i = 0; i < rows.size() && i < visible; i++) {
			CompoundTag r = rows.get(i);
			CounterLayout.RowBoxes b = CounterLayout.row(rowBox(i));
			String p = "exchange.row." + r.getStringOr("id", "") + ".";
			boolean canIn = r.getIntOr("to", 0) > 0 && r.getLongOr("max_in", 0) > 0;
			boolean canOut = r.getIntOr("from", 0) > 0 && r.getLongOr("max_out", 0) > 0 && tier() >= r.getIntOr("gate", 0) && !inDefault;
			hits.add(p + "in.1", b.in1(), canIn);
			hits.add(p + "in.64", b.in64(), canIn);
			hits.add(p + "in.all", b.inAll(), canIn);
			if (r.getIntOr("from", 0) > 0) {
				hits.add(p + "out.1", b.out1(), canOut);
				hits.add(p + "out.64", b.out64(), canOut);
				hits.add(p + "out.max", b.outMax(), canOut);
			}
		}
	}

	private int tier() {
		return s().getIntOr("vip", 0);
	}

	private Box rowBox(int i) {
		Box r = layout.rows();
		return new Box(r.x(), r.y() + i * CounterLayout.ROW_H, r.w(), CounterLayout.ROW_H);
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press press, int mods) {
		if (!press.left()) return false;
		if (!enabled) {
			return true;
		}
		switch (id) {
			case "exchange.deposit_all" -> shell.host().sendTable("deposit_all", new CompoundTag());
			case "exchange.deposit_held" -> shell.host().sendTable("deposit_held", new CompoundTag());
			case "exchange.max" -> amount = s().getLongOr("withdraw_fit", 0);
			case "exchange.clear" -> amount = 0;
			case "exchange.withdraw" -> withdraw();
			default -> {
				if (id.startsWith("exchange.chip.")) {
					amount += Long.parseLong(id.substring("exchange.chip.".length()));
				} else if (id.startsWith("exchange.row.")) {
					String[] parts = id.substring("exchange.row.".length()).split("\\.");
					if (parts.length != 3) return false;
					boolean out = parts[1].equals("out");
					boolean all = parts[2].equals("all") || parts[2].equals("max");
					int qty = all ? 0 : Integer.parseInt(parts[2]);
					if (Inputs.shift(mods)) {
						qty = 64; // EC-SPEC §1: Shift+click on any quantity button sends ×64
						all = false;
					}
					for (int i = 0; i < rows.size(); i++) if (rows.get(i).getStringOr("id", "").equals(parts[0])) selRow = i;
					selOut = out;
					send(parts[0], out, qty, all);
				} else {
					return false;
				}
			}
		}
		shell.relayout();
		return true;
	}

	private void withdraw() {
		if (amount <= 0) return;
		CompoundTag args = new CompoundTag();
		args.putLong("amount", amount);
		args.putInt("denom", 0);
		amount = 0;
		shell.host().sendTable("withdraw", args);
	}

	private void send(String row, boolean out, int qty, boolean all) {
		CompoundTag args = new CompoundTag();
		args.putString("row", row);
		args.putString("dir", out ? "out" : "in");
		args.putLong("qty", qty);
		args.putBoolean("all", all);
		shell.host().sendTable("exchange", args);
	}

	@Override
	public boolean key(int keyCode, int mods) {
		if (keyCode == Inputs.KEY_UP || keyCode == Inputs.KEY_DOWN) {
			if (!rows.isEmpty()) selRow = Math.floorMod(selRow + (keyCode == Inputs.KEY_UP ? -1 : 1), rows.size());
			return true;
		}
		if (keyCode == Inputs.KEY_LEFT || keyCode == Inputs.KEY_RIGHT) {
			selOut = keyCode == Inputs.KEY_RIGHT;
			return true;
		}
		if (keyCode == Inputs.KEY_RETURN || keyCode == Inputs.KEY_NUMPADENTER) {
			if (rows.isEmpty()) return true;
			String row = rows.get(selRow).getStringOr("id", "");
			boolean all = Inputs.ctrl(mods);
			int qty = all ? 0 : Inputs.shift(mods) ? 64 : 1;
			shell.pressed("exchange.row." + row + "." + (selOut ? "out" : "in") + "." + (all ? (selOut ? "max" : "all") : Integer.toString(qty)));
			send(row, selOut, qty, all);
			return true;
		}
		if (keyCode == Inputs.KEY_A) {
			shell.pressed("exchange.deposit_all");
			shell.host().sendTable("deposit_all", new CompoundTag());
			return true;
		}
		return false;
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------

	@Override
	public void render(GuiGraphicsExtractor g, Box body, int mouseX, int mouseY, float partial) {
		if (layout == null || money == null) return;
		Font font = shell.font();
		CompoundTag s = s();
		boolean inDefault = s.getBooleanOr("in_default", false);
		CounterDraw.panel(g, layout.money().inset(-3));
		btn(g, font, money.depositAll(), Component.translatable("gui.burmaldaholic.cashier.deposit_all"), "exchange.deposit_all", true, mouseX, mouseY);
		btn(g, font, money.depositHeld(), Component.translatable("gui.burmaldaholic.cashier.deposit_held"), "exchange.deposit_held", false, mouseX, mouseY);
		Component amountLine = Component.translatable("gui.burmaldaholic.cashier.withdraw_amount", Texts.number(amount));
		V2Draw.left(g, font, amountLine, money.amount(), 0, 1, CasinoPalette.V2_TEXT, true);
		for (int i = 0; i < denoms.length; i++) {
			btn(g, font, money.chips().get(i), Texts.raw("+" + CounterDraw.compact(denoms[i])), "exchange.chip." + denoms[i], false, mouseX, mouseY); // literal-ok: +denomination
		}
		btn(g, font, money.max(), Component.translatable("gui.burmaldaholic.common.max"), "exchange.max", false, mouseX, mouseY);
		btn(g, font, money.clear(), Component.translatable("gui.burmaldaholic.cashier.clear"), "exchange.clear", false, mouseX, mouseY);
		btn(g, font, money.withdraw(), Component.translatable("gui.burmaldaholic.cashier.withdraw"), "exchange.withdraw", true, mouseX, mouseY);
		long withdrawable = s.getLongOr("withdrawable", 0);
		long debt = s.getLongOr("debt", 0);
		Component info = inDefault ? Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked")
			: debt > 0 ? Component.translatable("gui.burmaldaholic.cashier.withdrawable_loan", Texts.number(withdrawable), Texts.number(debt))
			: Component.translatable("gui.burmaldaholic.cashier.withdrawable", Texts.number(withdrawable));
		V2Draw.left(g, font, info, money.info(), 0, 1, inDefault ? CasinoPalette.V2_LOSE : CasinoPalette.V2_MUTED, false);
		if (rows.isEmpty()) {
			V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.cashier.exchange.empty"), layout.rows().isEmpty()
				? new Box(layout.rows().x(), layout.rows().y(), layout.rows().w(), 12) : layout.rows(), 2, 1, CasinoPalette.V2_MUTED, false);
			return;
		}
		int visible = CounterLayout.visibleRows(layout);
		for (int i = 0; i < rows.size() && i < visible; i++) {
			CompoundTag r = rows.get(i);
			Box rb = rowBox(i);
			if (i % 2 == 0) V2Draw.fill(g, rb, 0x30FFFFFF);
			if (i == selRow) V2Draw.outline(g, rb, CasinoPalette.V2_GOLD);
			CounterLayout.RowBoxes b = CounterLayout.row(rb);
			ItemStack stack = stack(r.getStringOr("item", ""));
			g.fakeItem(stack, b.icon().x(), b.icon().y());
			long have = r.getLongOr("have", 0);
			Component full = Component.translatable("gui.burmaldaholic.cashier.exchange.have", Texts.number(have));
			Component haveText = font.width(full) <= b.have().w() ? full : Texts.raw("×" + Numbers.format(have)); // literal-ok: count
			V2Draw.left(g, font, haveText, b.have(), 0, 1, have > 0 ? CasinoPalette.V2_TEXT : CasinoPalette.V2_MUTED, false);
			String p = "exchange.row." + r.getStringOr("id", "") + ".";
			V2Draw.centred(g, font, Component.translatable("gui.burmaldaholic.cashier.exchange.in", Texts.number(r.getIntOr("to", 0))), b.inRate(), 1,
				CasinoPalette.V2_WIN, false);
			btn(g, font, b.in1(), Component.translatable("gui.burmaldaholic.cashier.qty.x1"), p + "in.1", false, mouseX, mouseY, i == selRow && !selOut);
			btn(g, font, b.in64(), Component.translatable("gui.burmaldaholic.cashier.qty.x64"), p + "in.64", false, mouseX, mouseY);
			btn(g, font, b.inAll(), Component.translatable("gui.burmaldaholic.cashier.exchange.all"), p + "in.all", false, mouseX, mouseY);
			int from = r.getIntOr("from", 0);
			if (from > 0) {
				V2Draw.centred(g, font, Component.translatable("gui.burmaldaholic.cashier.exchange.out", Texts.number(from)), b.outRate(), 1,
					CasinoPalette.V2_GOLD, false);
				btn(g, font, b.out1(), Component.translatable("gui.burmaldaholic.cashier.qty.x1"), p + "out.1", false, mouseX, mouseY, i == selRow && selOut);
				btn(g, font, b.out64(), Component.translatable("gui.burmaldaholic.cashier.qty.x64"), p + "out.64", false, mouseX, mouseY);
				btn(g, font, b.outMax(), Component.translatable("gui.burmaldaholic.cashier.exchange.max"), p + "out.max", false, mouseX, mouseY);
			}
			if (b.icon().contains(mouseX, mouseY) || b.have().contains(mouseX, mouseY)) {
				List<Component> tip = new ArrayList<>();
				tip.add(stack.getHoverName());
				tip.add(full);
				int gate = r.getIntOr("gate", 0);
				if (gate > tier()) tip.add(Component.translatable("gui.burmaldaholic.common.requires_vip", VipTiers.name(gate)));
				g.setTooltipForNextFrame(font, tip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
			}
		}
		if (rows.size() > visible) {
			V2Draw.left(g, font, Texts.raw("…"), new Box(layout.rows().x(), layout.rows().bottom(), 20, 8), 0, 1, CasinoPalette.V2_MUTED, false); // literal-ok: ellipsis
		}
	}

	private void btn(GuiGraphicsExtractor g, Font font, Box b, Component label, String id, boolean primary, int mx, int my) {
		btn(g, font, b, label, id, primary, mx, my, false);
	}

	private void btn(GuiGraphicsExtractor g, Font font, Box b, Component label, String id, boolean primary, int mx, int my, boolean focused) {
		boolean enabled = shell.hits().targets().stream().anyMatch(t -> t.id().equals(id) && t.enabled());
		CounterDraw.button(g, font, b, label, primary, enabled, mx, my, focused);
	}

	private static ItemStack stack(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(key));
	}
}
