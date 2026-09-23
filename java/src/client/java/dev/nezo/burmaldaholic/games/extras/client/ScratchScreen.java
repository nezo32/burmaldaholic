package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Scratch card screen (UI.md §9, Java: click cells, "Scratch all"). Hidden cells are silver; the server only
 * ever sends revealed cells. When the card is done: result line, [Buy for N] and [Play again] (next card of
 * the same kind from the inventory).
 */
final class ScratchScreen extends ExtrasScreen {
	private static final int CELL_W = 56;
	private static final int CELL_H = 28;
	private static final int GAP = 4;

	private int gridX;
	private int gridY;
	private int resultY;

	ScratchScreen(CompoundTag state) {
		super("scratch", title(state), state, 220, 200);
	}

	private static Component title(CompoundTag s) {
		return Component.translatable("gold".equals(s.getStringOr("kind", "basic"))
			? "gui.burmaldaholic.extras.scratch.title_gold" : "gui.burmaldaholic.extras.scratch.title");
	}

	private CompoundTag kindArgs() {
		CompoundTag t = new CompoundTag();
		t.putString("kind", state().getStringOr("kind", "basic"));
		t.putString("id", state().getStringOr("id", ""));
		return t;
	}

	@Override
	protected void layout() {
		CompoundTag s = state();
		int w = panelWidth - 2 * PAD;
		int y = top + 20 + wrappedHeight(Component.translatable("gui.burmaldaholic.extras.scratch.hint"), w) + 4;
		gridX = left + (panelWidth - (3 * CELL_W + 2 * GAP)) / 2;
		gridY = y;
		y += 3 * CELL_H + 2 * GAP + 6;
		resultY = y;
		boolean done = s.getBooleanOr("done", false);
		if (done) {
			y += wrappedHeight(resultLine(), w) + 4;
		}
		Flow flow = new Flow(font, this::addRenderableWidget, left + PAD, y, w);
		if (!done && s.getStringOr("id", "").isEmpty() && s.getIntOr("fresh", 0) == 0) {
			flow.button(Component.translatable("gui.burmaldaholic.extras.scratch.buy", Texts.chipsAcc(s.getLongOr("buy_price", 0))), 70,
				b -> send("buy", kindArgs())).active = s.getBooleanOr("can_buy", true);
		} else if (!done) {
			flow.button(Component.translatable("gui.burmaldaholic.extras.scratch.all"), 70, b -> send("all", kindArgs()));
		} else {
			if (s.getIntOr("fresh", 0) > 0) {
				flow.button(Component.translatable("gui.burmaldaholic.common.play_again"), 70, b -> send("new", kindArgs()));
			}
			flow.button(Component.translatable("gui.burmaldaholic.extras.scratch.buy", Texts.chipsAcc(s.getLongOr("buy_price", 0))), 70,
				b -> send("buy", kindArgs())).active = s.getBooleanOr("can_buy", true);
		}
		fitHeight(flow.bottom());
	}

	private Component resultLine() {
		CompoundTag s = state();
		long prize = s.getLongOr("prize", 0);
		if (prize > 0) {
			return s.getBooleanOr("top", false)
				? Component.translatable("gui.burmaldaholic.extras.scratch.top_prize", Texts.chips(prize)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				: Component.translatable("gui.burmaldaholic.extras.scratch.win", Texts.chips(prize)).withStyle(ChatFormatting.GREEN);
		}
		if (s.getBooleanOr("creeper", false)) {
			return Component.translatable("gui.burmaldaholic.extras.scratch.creeper").withStyle(ChatFormatting.DARK_GREEN);
		}
		return Component.translatable("gui.burmaldaholic.extras.scratch.lose").withStyle(ChatFormatting.RED);
	}

	private int cellAt(double mx, double my) {
		for (int i = 0; i < Scratch.CELLS; i++) {
			int x = gridX + (i % 3) * (CELL_W + GAP);
			int y = gridY + (i / 3) * (CELL_H + GAP);
			if (mx >= x && mx < x + CELL_W && my >= y && my < y + CELL_H) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && !state().getBooleanOr("done", false)) {
			int cell = cellAt(event.x(), event.y());
			if (cell >= 0 && (state().getIntOr("mask", 0) >> cell & 1) == 0) {
				CompoundTag args = kindArgs();
				args.putInt("cell", cell);
				send("scratch", args);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		CompoundTag s = state();
		int w = panelWidth - 2 * PAD;
		wrapped(g, Component.translatable("gui.burmaldaholic.extras.scratch.hint"), left + PAD, top + 20, w, MUTED);
		long[] cells = s.getLongArray("cells").orElse(new long[0]);
		int mask = s.getIntOr("mask", 0);
		boolean done = s.getBooleanOr("done", false);
		long prize = s.getLongOr("prize", 0);
		int hover = cellAt(mouseX, mouseY);
		for (int i = 0; i < Scratch.CELLS; i++) {
			int x = gridX + (i % 3) * (CELL_W + GAP);
			int y = gridY + (i / 3) * (CELL_H + GAP);
			boolean shown = (mask >> i & 1) == 1 && i < cells.length && cells[i] >= 0;
			if (!shown) {
				g.fill(x, y, x + CELL_W, y + CELL_H, hover == i && !done ? 0xFFD8D8D8 : 0xFFB4B4B4);
				for (int k = 0; k < CELL_W; k += 6) {
					g.fill(x + k, y + 3, x + k + 3, y + CELL_H - 3, 0x22FFFFFF);
				}
				continue;
			}
			long v = cells[i];
			boolean match = done && ((prize > 0 && v == prize) || (prize == 0 && s.getBooleanOr("creeper", false) && v == Scratch.CREEPER));
			g.fill(x, y, x + CELL_W, y + CELL_H, match ? 0xFF6B5A12 : 0xFF2A2A2A);
			Art.frame(g, x, y, CELL_W, CELL_H, match ? GOLD : 0xFF555555);
			if (v == Scratch.CREEPER) {
				Art.creeper(g, x + (CELL_W - 16) / 2, y + (CELL_H - 16) / 2, 16);
			} else {
				Component num = Texts.number(v);
				g.centeredText(font, num, x + CELL_W / 2, y + (CELL_H - 8) / 2, match ? GOLD : TEXT);
			}
		}
		if (done) {
			wrapped(g, resultLine(), left + PAD, resultY, w, TEXT);
		}
	}
}
