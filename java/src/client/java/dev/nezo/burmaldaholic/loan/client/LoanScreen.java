package dev.nezo.burmaldaholic.loan.client;

import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.loan.net.LoanActionPayload;
import dev.nezo.burmaldaholic.loan.net.LoanUiPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Loan Shark screen (UI.md §10): portrait, greeting, status, the product table (amount, interest, due,
 * deadline; locked rows show the VIP tier needed), Take loan → confirmation, and Pay / Pay all when a
 * loan exists. Server-driven: renders {@link LoanUiPayload} state, sends {@link LoanActionPayload}.
 * Every text is wrapped / measured at runtime so Russian labels never get cut.
 */
final class LoanScreen extends Screen {
	private static final int PAD = 8;
	private static final int PANEL = 0xF0202028;
	private static final int BORDER = 0xFFB8912E;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int GOLD = 0xFFFFD35A;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int ERROR = 0xFFFF6060;
	private static final int OK = 0xFF7CFC7C;
	private static final int PORTRAIT = 56;

	private CompoundTag state;
	private int confirmIndex = -1;
	private @Nullable EditBox amount;
	private String amountText = "";
	private final List<TextLine> lines = new ArrayList<>();
	private int left;
	private int top;
	private int panelW;
	private int panelH;

	private record TextLine(int x, int y, FormattedCharSequence text, int color) {}

	LoanScreen(CompoundTag state) {
		super(Component.translatable("gui.burmaldaholic.loan.title"));
		this.state = state;
	}

	void accept(CompoundTag newState) {
		this.state = newState;
		if (!newState.getBooleanOr("can_take", false)) {
			confirmIndex = -1;
		}
		if (newState.contains("message") && !newState.getBooleanOr("error", false)) {
			confirmIndex = -1; // loan taken / paid: back to the overview
			amountText = "";
		}
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	@Override
	protected void init() {
		panelW = Math.min(340, width - 16);
		int h = layout(0, false);
		panelH = h;
		left = (width - panelW) / 2;
		top = Math.max(4, (height - panelH) / 2);
		layout(top, true);
	}

	private boolean piglin() {
		return state.getBooleanOr("piglin", false);
	}

	private @Nullable LivingEntity portrait() {
		if (minecraft == null || minecraft.level == null) {
			return null;
		}
		Entity e = minecraft.level.getEntity(state.getIntOr("entity", -1));
		return e instanceof LivingEntity living ? living : null;
	}

	/** Lays out text and widgets from y = {@code y0}; returns the panel height. */
	private int layout(int y0, boolean create) {
		if (create) {
			if (amount != null) {
				amountText = amount.getValue();
			}
			amount = null;
			lines.clear();
		}
		int x0 = left + PAD;
		int inner = panelW - 2 * PAD;
		int y = y0 + PAD;
		// header: name + balance
		Component name = Component.translatable(piglin() ? "entity.burmaldaholic.piglin_moneylender" : "gui.burmaldaholic.loan.title");
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(state.getLongOr("balance", 0)));
		if (create) {
			lines.add(new TextLine(x0, y, name.getVisualOrderText(), GOLD));
			lines.add(new TextLine(left + panelW - PAD - font.width(balance), y, balance.getVisualOrderText(), TEXT));
		}
		y += 14;
		int portraitW = portrait() != null ? PORTRAIT + 6 : 0;
		int tx = x0 + portraitW;
		int tw = inner - portraitW;
		int textTop = y;
		y = text(tx, y, tw, component("greeting"), GOLD, create);
		y += 4;
		y = text(tx, y, tw, component("status_line"), TEXT, create);
		if (portraitW > 0) {
			y = Math.max(y, textTop + PORTRAIT + 4);
		}
		y += 6;

		boolean canTake = state.getBooleanOr("can_take", false);
		ListTag products = state.getListOrEmpty("products");
		if (confirmIndex >= 0 && canTake && confirmIndex < products.size()) {
			CompoundTag p = products.getCompoundOrEmpty(confirmIndex);
			y = text(x0, y, inner, Component.translatable("gui.burmaldaholic.loan.confirm_title").withStyle(ChatFormatting.BOLD), GOLD, create);
			y += 2;
			y = text(x0, y, inner, Component.translatable("gui.burmaldaholic.loan.confirm", Texts.chipsAcc(p.getLongOr("principal", 0)),
				Texts.chipsAcc(p.getLongOr("due", 0)), Texts.plural("unit.burmaldaholic.day", p.getIntOr("days", 0))), TEXT, create);
			y += 6;
			int index = confirmIndex;
			int bx = x0;
			bx = button(bx, y, Component.translatable("gui.burmaldaholic.loan.sign"), b -> send("take", index, 0), true, create);
			button(bx + 4, y, Component.translatable("gui.burmaldaholic.common.cancel"), b -> {
				confirmIndex = -1;
				rebuildWidgets();
			}, true, create);
			y += 24;
		} else if (canTake) {
			y = text(x0, y, inner, Component.translatable("gui.burmaldaholic.loan.interest", Texts.raw(state.getStringOr("rate", "0"))), TEXT, create);
			Component gs = component("good_standing");
			if (gs != null) {
				y = text(x0, y, inner, gs, OK, create);
			}
			y += 4;
			Component take = Component.translatable("gui.burmaldaholic.loan.take");
			int bw = Math.max(60, font.width(take) + 10);
			for (int i = 0; i < products.size(); i++) {
				CompoundTag p = products.getCompoundOrEmpty(i);
				boolean locked = p.getBooleanOr("locked", false);
				int rowTop = y;
				int rw = inner - bw - 6;
				y = text(x0, y, rw, Component.translatable("gui.burmaldaholic.loan.product." + p.getStringOr("id", "pocket")).withStyle(ChatFormatting.BOLD),
					locked ? GRAY : GOLD, create);
				Component offer = locked
					? Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(p.getIntOr("min_tier", 0)))
					: Component.translatable("gui.burmaldaholic.loan.offer", Texts.chipsAcc(p.getLongOr("principal", 0)), Texts.chipsAcc(p.getLongOr("due", 0)),
						Texts.plural("unit.burmaldaholic.day", p.getIntOr("days", 0)));
				y = text(x0, y, rw, offer, locked ? GRAY : TEXT, create);
				int index = p.getIntOr("index", i);
				if (create) {
					Button b = addRenderableWidget(Button.builder(take, btn -> {
						confirmIndex = index;
						rebuildWidgets();
					}).bounds(left + panelW - PAD - bw, rowTop, bw, 20).build());
					b.active = !locked;
				}
				y = Math.max(y, rowTop + 20) + 4;
			}
			int locked = state.getIntOr("locked", 0);
			if (locked > 0) {
				y = text(x0, y, inner, Component.translatable("gui.burmaldaholic.loan.locked_count", Texts.number(locked)), GRAY, create);
				Component refuse = component("refuse_line");
				if (refuse != null) {
					y = text(x0, y, inner, refuse, GRAY, create);
				}
			}
		} else if (state.getLongOr("owed", 0) > 0) {
			long owed = state.getLongOr("owed", 0);
			y = text(x0, y, inner, Component.translatable("gui.burmaldaholic.loan.pay_amount"), TEXT, create);
			y += 2;
			int bx = x0;
			if (create) {
				amount = new EditBox(font, bx, y, 80, 20, Component.translatable("gui.burmaldaholic.loan.pay_amount"));
				amount.setMaxLength(12);
				amount.setResponder(v -> {
					if (!v.chars().allMatch(Character::isDigit)) {
						amount.setValue(v.replaceAll("\\D", ""));
					}
				});
				amount.setHint(Component.translatable("gui.burmaldaholic.common.amount"));
				amount.setValue(amountText);
				addRenderableWidget(amount);
			}
			bx += 84;
			bx = flowButton(x0, bx, y, Component.translatable("gui.burmaldaholic.loan.pay"), b -> pay(), create);
			int[] pos = flow(x0, bx + 4, y, Component.translatable("gui.burmaldaholic.loan.pay_all", Texts.chips(owed)));
			y = pos[1];
			button(pos[0], y, Component.translatable("gui.burmaldaholic.loan.pay_all", Texts.chips(owed)), b -> send("pay_all", 0, 0), true, create);
			y += 24;
		}
		Component msg = component("message");
		if (msg != null) {
			y += 2;
			y = text(x0, y, inner, msg, state.getBooleanOr("error", false) ? ERROR : OK, create);
		}
		y += 4;
		button(left + panelW - PAD - Math.max(60, font.width(Component.translatable("gui.burmaldaholic.common.close")) + 10), y,
			Component.translatable("gui.burmaldaholic.common.close"), b -> onClose(), true, create);
		y += 20 + PAD;
		return y - y0;
	}

	/** Where a button of this label goes: same row if it fits, else the next row. */
	private int[] flow(int rowStart, int x, int y, Component label) {
		int w = Math.max(40, font.width(label) + 10);
		if (x + w > left + panelW - PAD) {
			return new int[] {rowStart, y + 24};
		}
		return new int[] {x, y};
	}

	private int flowButton(int rowStart, int x, int y, Component label, Button.OnPress onPress, boolean create) {
		int[] pos = flow(rowStart, x, y, label);
		return button(pos[0], pos[1], label, onPress, true, create);
	}

	private int button(int x, int y, Component label, Button.OnPress onPress, boolean active, boolean create) {
		int w = Math.max(40, font.width(label) + 10);
		if (create) {
			Button b = addRenderableWidget(Button.builder(label, onPress).bounds(x, y, w, 20).build());
			b.active = active;
		}
		return x + w;
	}

	private int text(int x, int y, int w, @Nullable Component c, int color, boolean create) {
		if (c == null) {
			return y;
		}
		for (FormattedCharSequence line : font.split(c, Math.max(40, w))) {
			if (create) {
				lines.add(new TextLine(x, y, line, color));
			}
			y += 10;
		}
		return y;
	}

	private @Nullable Component component(String key) {
		return LoanClientModule.readComponent(state, key);
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
			CompoundTag copy = state.copy();
			LoanClientModule.putError(copy, Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			accept(copy);
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
		graphics.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
		graphics.fill(left, top, left + panelW, top + panelH, PANEL);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		for (TextLine l : lines) {
			graphics.text(font, l.text(), l.x(), l.y(), l.color(), true);
		}
		LivingEntity e = portrait();
		if (e != null) {
			int px = left + PAD;
			int py = top + PAD + 14;
			graphics.fill(px, py, px + PORTRAIT, py + PORTRAIT, 0xFF101014);
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, px, py, px + PORTRAIT, py + PORTRAIT, 24, 0.0625F, mouseX, mouseY, e);
		}
	}
}
