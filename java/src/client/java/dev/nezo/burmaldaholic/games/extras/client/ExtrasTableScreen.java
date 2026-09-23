package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Shared bits of the wheel and plinko machine screens: widget rebuild on state, auto-growing panel, partial tick. */
abstract class ExtrasTableScreen extends CasinoTableScreen {
	static final int PAD = 8;
	static final int MUTED = 0xFFDDDDDD;
	static final int GOLD = 0xFFFFD700;
	protected float partial;
	protected int ticks;

	protected ExtrasTableScreen(CasinoTableMenu menu, Inventory inventory, Component title, int width, int height) {
		super(menu, inventory, title, width, height);
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	@Override
	protected final void onStateChanged(CompoundTag newState) {
		stateArrived(newState);
		if (minecraft != null) {
			rebuild();
		}
	}

	protected void stateArrived(CompoundTag newState) {}

	protected final void rebuild() {
		clearWidgets();
		if (extraHeight > 0) {
			topPos = Math.max(2, (height - imageHeight - extraHeight) / 2);
		}
		int bottom = layout();
		int needed = bottom - topPos + 16;
		if (needed > imageHeight + extraHeight) {
			// Very long (Russian) labels wrapped into more rows than planned: extend the felt downwards and re-center.
			extraHeight = needed - imageHeight;
			topPos = Math.max(2, (height - imageHeight - extraHeight) / 2);
			clearWidgets();
			layout();
		}
	}

	private int extraHeight;

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		if (extraHeight > 0) {
			int bottom = topPos + imageHeight;
			graphics.fill(leftPos - 1, bottom - 1, leftPos + imageWidth + 1, bottom + extraHeight + 1, FELT_BORDER);
			graphics.fill(leftPos, bottom - 1, leftPos + imageWidth, bottom + extraHeight, FELT);
		}
	}

	/** Adds widgets at absolute positions (leftPos/topPos based); returns the absolute bottom of the content. */
	protected abstract int layout();

	protected Flow flow(int relX, int relY, int width) {
		return new Flow(font, this::addRenderableWidget, leftPos + relX, topPos + relY, width);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		ticks++;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		partial = a;
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance()));
		int bw = font.width(balance);
		if (font.width(title) + bw + 3 * PAD < imageWidth) {
			graphics.text(font, balance, imageWidth - PAD - bw, titleLabelY, GOLD, true);
		}
		extractContent(graphics, xm - leftPos, ym - topPos);
	}

	/** Draws in panel-relative coordinates. */
	protected abstract void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

	protected int wrappedHeight(Component text, int width) {
		return font.split(text, width).size() * font.lineHeight;
	}
}
