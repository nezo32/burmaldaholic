package dev.nezo.burmaldaholic.client.table.fx;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * One chip of the tray (visual/tables.md §2.2 {@code chip_<d>} in its {@code chip_well_<theme>}): the selected chip
 * rises 2 px (120 ms) and wears the {@code chip_select} gold ring with its travelling glint (frame 0 with reduced
 * motion). Selecting plays {@code chip_place}.
 */
public final class ChipButton extends AbstractButton {
	private final int denom;
	private final IntSupplier selected;
	private final IntConsumer select;
	private final boolean well;
	private TableTheme theme;
	private long selectedAt;
	private boolean wasSelected;

	public ChipButton(int x, int y, int denom, TableTheme theme, boolean well, IntSupplier selected, IntConsumer select) {
		super(x, y, 24, 24, Texts.chips(denom));
		this.denom = denom;
		this.theme = theme;
		this.well = well;
		this.selected = selected;
		this.select = select;
		setTooltip(Tooltip.create(Texts.chips(denom)));
	}

	public int denom() {
		return denom;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		select.accept(denom);
		FxSounds.play("chip_place", 0.7f, (float) (0.9 + 0.1 * Math.log(denom) / Math.log(5)));
	}

	@Override
	public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
		// chip_place instead of the vanilla click
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = getX();
		int y = getY();
		boolean sel = selected.getAsInt() == denom;
		long now = Util.getMillis();
		if (sel && !wasSelected) {
			selectedAt = now;
		}
		wasSelected = sel;
		if (well) {
			TableGfx.blit(g, "core/chip_well_" + theme.id, x - 1, y - 1, 26, 26);
		}
		int lift = 0;
		if (sel) {
			lift = FxSettings.reduceMotion() ? 2 : (int) Math.round(2 * Math.min(1, (now - selectedAt) / 120.0));
		} else if (active && isHoveredOrFocused()) {
			lift = 1;
		}
		int tint = active ? 0xFFFFFFFF : 0x90FFFFFF;
		TableGfx.blit(g, active ? "core/chip_" + denom : "core/chip_grey", x, y - lift, 24, 24, tint);
		if (sel) {
			if (FxSettings.reduceMotion()) {
				TableGfx.frame(g, "core/chip_select", 28, 28, 4, 0, x - 2, y - 2 - lift, 0xFFFFFFFF);
			} else {
				TableGfx.blit(g, "core/chip_select", x - 2, y - 2 - lift, 28, 28);
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}

	/** Label for narration. */
	public Component label() {
		return getMessage();
	}
}
