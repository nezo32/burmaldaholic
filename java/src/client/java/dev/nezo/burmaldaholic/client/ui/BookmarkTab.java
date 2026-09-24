package dev.nezo.burmaldaholic.client.ui;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.LedgerLayout;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A ledger bookmark tab (extras.md §8.2; kit): unselected tabs are icon-only 26 × 20 bookmarks with the name as
 * tooltip; the selected tab is 2 px taller, shows icon + name and opens into the page (no bottom edge). The {@code loan}
 * variant is the Loan Shark's steel tab (name in {@code chip.light}). Tabs without an icon show their name.
 */
public class BookmarkTab extends AbstractButton {
	private final UiSprites.@Nullable TabIcon icon;
	private final boolean loan;
	private final Consumer<BookmarkTab> onPress;
	private boolean selected;
	private boolean showLabel;

	public BookmarkTab(int x, int y, int w, int h, Component name, UiSprites.@Nullable TabIcon icon, boolean loan, Consumer<BookmarkTab> onPress) {
		super(x, y, w, h, name);
		this.icon = icon;
		this.loan = loan;
		this.onPress = onPress;
		setTooltip(Tooltip.create(name));
	}

	/** Selected: taller, labelled ({@code showLabel}), not clickable. */
	public BookmarkTab select(boolean on, boolean showLabel) {
		this.selected = on;
		this.showLabel = showLabel;
		if (on) setTooltip(showLabel ? null : Tooltip.create(getMessage()));
		return this;
	}

	public boolean isSelected() {
		return selected;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (!selected) onPress.accept(this);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		boolean hot = !selected && isHoveredOrFocused();
		var id = loan ? (selected ? UiSprites.TAB_LOAN_SELECTED : UiSprites.TAB_LOAN)
			: selected ? UiSprites.TAB_SELECTED : hot ? UiSprites.TAB_HOVER : UiSprites.TAB;
		// bookmarks are drawn 4 px taller so the ribbon runs under the page edge
		CasinoUi.sprite(g, id, x, y, w, h + 4, selected ? 0xFF3A1A5C : 0xFF26103C, selected ? CasinoPalette.GOLD : CasinoPalette.FRAME);
		if (isFocused() && !selected) g.outline(x - 1, y - 1, w + 2, h + 2, CasinoPalette.GLINT);
		Font font = Minecraft.getInstance().font;
		int iy = y + (h - 16) / 2 + (hot ? -1 : 0);
		if (icon != null && (w >= 18 || !showLabel)) {
			int ix = selected && showLabel ? x + 5 : x + (w - 16) / 2;
			CasinoUi.tabIcon(g, icon, 16, ix, iy);
		}
		if (selected && showLabel || icon == null) {
			int tx = icon != null ? x + 5 + 16 + 4 : x + 5;
			int color = loan ? CasinoPalette.CHIP_RED_LIGHT : CasinoPalette.GOLD;
			CasinoUi.text(g, font, getMessage(), tx, y + (h - 8) / 2, x + w - tx - 3, color);
		}
	}

	/** Width of a selected, labelled tab for {@code name}. */
	public static int labelledWidth(Font font, Component name) {
		return Math.max(LedgerLayout.TAB_W, LedgerLayout.TAB_LABEL_EXTRA + font.width(name));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
