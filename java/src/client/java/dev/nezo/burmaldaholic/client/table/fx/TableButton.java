package dev.nezo.burmaldaholic.client.table.fx;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The table kit's casino buttons (docs/design/visual/tables.md §2.2), real widgets (focus, keyboard, narration):
 * <ul>
 *   <li>{@link Kind#ICON} — 20² {@code icon_button_<theme>} + a 14² icon; the words are the tooltip;</li>
 *   <li>{@link Kind#SPIN} / {@link Kind#ROLL} — the 48² red casino-chip action buttons on the rail corner, with an
 *       idle glow pulse (static with reduced motion) and a 1 px press.</li>
 * </ul>
 * Pressed drops 1 px; an invalid press shakes ±2 px (240 ms) with {@code ui_deny}. Text buttons are the J-L2 kit's
 * {@code CasinoButton} ({@link TableKit#button}); these are the table-art buttons of visual/tables.md §2.2.
 */
public final class TableButton extends AbstractButton {
	public enum Kind {
		ICON, SPIN, ROLL
	}

	private final Kind kind;
	private final String icon;
	private final Consumer<TableButton> onPress;
	private TableTheme theme;
	private long pressedAt = -1;
	private long shakeAt = -1;
	private boolean pulse;
	private int glowColor;

	private TableButton(int x, int y, int w, int h, Component label, Kind kind, String icon, TableTheme theme, Consumer<TableButton> onPress) {
		super(x, y, w, h, label);
		this.kind = kind;
		this.icon = icon;
		this.theme = theme;
		this.onPress = onPress;
	}

	/** Icon button ({@code core/icon/<icon>}) whose tooltip is {@code label}. */
	public static TableButton icon(int x, int y, String icon, Component label, TableTheme theme, Consumer<TableButton> onPress) {
		TableButton b = new TableButton(x, y, 20, 20, label, Kind.ICON, icon, theme, onPress);
		b.setTooltip(Tooltip.create(label));
		return b;
	}

	/** Big Spin / Roll chip button (48²); {@code label} is its tooltip and narration. */
	public static TableButton action(int x, int y, Kind kind, Component label, Consumer<TableButton> onPress) {
		TableButton b = new TableButton(x, y, 48, 48, label, kind, "", TableTheme.VILLAGE, onPress);
		b.setTooltip(Tooltip.create(label));
		return b;
	}

	public TableButton theme(TableTheme t) {
		this.theme = t;
		return this;
	}

	/** Gentle idle glow (Roll while the shooter may roll, Spin while bets are down). */
	public TableButton pulse(boolean on) {
		this.pulse = on;
		return this;
	}

	/** A 1 px coloured glow around the button (Accept: {@code #80FF40}); 0 = none. */
	public TableButton glow(int color) {
		this.glowColor = color;
		return this;
	}

	/** Invalid feedback: ±2 px shake for 240 ms + {@code ui_deny}. */
	public void shake() {
		shakeAt = Util.getMillis();
		FxSounds.play("ui_deny", 1f);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		onPress.accept(this);
	}

	private String state(boolean pressed) {
		if (!active) {
			return "_disabled";
		}
		if (pressed) {
			return "_pressed";
		}
		return isHoveredOrFocused() ? "_hover" : "";
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		boolean pressed = pressedAt >= 0 && now - pressedAt < 90;
		int x = getX();
		int y = getY();
		if (shakeAt >= 0 && now - shakeAt < 240) {
			double k = (now - shakeAt) / 240.0;
			x += (int) Math.round(2 * Math.sin(k * Math.PI * 6) * (1 - k));
		}
		int w = getWidth();
		int h = getHeight();
		Font font = Minecraft.getInstance().font;
		switch (kind) {
			case ICON -> {
				int dy = pressed ? 1 : 0;
				TableGfx.blit(g, "core/icon_button_" + theme.id + state(pressed), x, y, 20, 20);
				TableGfx.blit(g, "core/icon/" + icon, x + 3, y + 2 + dy, 14, 14, active ? 0xFFFFFFFF : 0x80FFFFFF);
			}
			case SPIN, ROLL -> {
				String base = kind == Kind.SPIN ? "core/spin_button" : "core/roll_button";
				if (pulse && active && !FxSettings.reduceMotion()) {
					double p = 0.5 + 0.5 * Math.sin(now / 1000.0 * Math.PI * 2);
					int c = TableGfx.alpha(0xFFFFD640, 0.18 + 0.22 * p);
					TableGfx.disc(g, x + 24, y + 24, 25, c);
				}
				TableGfx.blit(g, base + state(pressed), x, y + (pressed ? 1 : 0), 48, 48);
			}
		}
		if (isFocused()) {
			g.fill(x - 1, y - 1, x + w + 1, y, 0xAAFFFFFF);
			g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xAAFFFFFF);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
