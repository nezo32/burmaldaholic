package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Base of the item-driven extras screens (coin flip, dice duel, scratch card, duel invite). Server-driven: it
 * renders {@link #state()} (from {@code ExtrasScreenPayload}) and sends {@code ExtrasActionPayload}s; it never
 * decides outcomes. Felt panel, title, balance, error line (UI.md §12, 60 ticks). The panel grows to fit its
 * flowed content, so long Russian labels wrap instead of being cut.
 */
abstract class ExtrasScreen extends Screen {
	static final int FELT = 0xFF1E5E3A;
	static final int FELT_BORDER = 0xFF0E2E1C;
	static final int TEXT = 0xFFFFFFFF;
	static final int MUTED = 0xFFDDDDDD;
	static final int GOLD = 0xFFFFD700;
	static final int ERROR = 0xFFFF5555;
	static final int PAD = 8;

	private final String game;
	private CompoundTag state;
	private @Nullable Component error;
	private int errorTicks;
	protected int panelWidth;
	protected int panelHeight;
	protected int left;
	protected int top;
	protected int ticks;

	protected ExtrasScreen(String game, Component title, CompoundTag state, int width, int height) {
		super(title);
		this.game = game;
		this.state = state;
		this.panelWidth = width;
		this.panelHeight = height;
	}

	String game() {
		return game;
	}

	CompoundTag state() {
		return state;
	}

	/** New state from the server: keep animations consistent, then rebuild the widgets. */
	void acceptState(CompoundTag newState) {
		CompoundTag old = state;
		state = newState;
		onStateChanged(old, newState);
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {}

	void showError(Component message) {
		error = message;
		errorTicks = 60;
	}

	protected void send(String action, CompoundTag args) {
		ClientPlayNetworking.send(new ExtrasActionPayload(game, action, args));
	}

	@Override
	protected void init() {
		left = (width - panelWidth) / 2;
		top = Math.max(4, (height - panelHeight) / 2);
		layout();
	}

	/** Adds widgets (use {@link #flow(int)}); may change {@link #panelHeight} via {@link #fitHeight}. */
	protected abstract void layout();

	protected Flow flow(int y) {
		return new Flow(font, this::addRenderableWidget, left + PAD, top + y, panelWidth - 2 * PAD);
	}

	/** Grows the panel so {@code contentBottom} (absolute y) plus the error line fits; re-centers once. */
	protected void fitHeight(int contentBottom) {
		int needed = contentBottom - top + 16;
		if (needed > panelHeight) {
			panelHeight = needed;
			rebuildWidgets();
		}
	}

	@Override
	public void tick() {
		ticks++;
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, FELT_BORDER);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, FELT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.text(font, title, left + PAD, top + 6, TEXT, true);
		Component balance = Component.translatable("gui.burmaldaholic.common.balance", Texts.number(state.getLongOr("balance", 0)));
		int bw = font.width(balance);
		if (font.width(title) + bw + 3 * PAD < panelWidth) {
			graphics.text(font, balance, left + panelWidth - PAD - bw, top + 6, GOLD, true);
		}
		extractContent(graphics, mouseX, mouseY, a);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (error != null) {
			Art.wrap(graphics, font, error, left + PAD, top + panelHeight - 12, panelWidth - 2 * PAD, ERROR);
		}
	}

	protected abstract void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

	/** Word-wrapped text; returns the y below it. */
	protected int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		Art.wrap(graphics, font, text, x, y, width, color);
		return y + font.split(text, width).size() * font.lineHeight;
	}

	protected int wrappedHeight(Component text, int width) {
		return font.split(text, width).size() * font.lineHeight;
	}

	protected CompoundTag args() {
		return new CompoundTag();
	}
}
