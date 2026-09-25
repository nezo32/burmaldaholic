package dev.nezo.burmaldaholic.client.table;

import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Base screen for tables: renders {@link #state()} (sent by the block entity) and sends input with
 * {@link #sendAction}. NEVER decide outcomes on the client. All text via Component.translatable.
 *
 * <p>Provided: a felt panel ({@link #extractBackground}, override for textures), the title, an error
 * line (server {@code sendError}, shown 60 ticks), helpers for the common state fields written by
 * {@code CasinoTableBlockEntity#baseState} ({@link #balance()}, {@link #minBet()}, {@link #maxBet()},
 * {@link #mySeat()}, {@link #seatNames()}, {@link #timerSeconds}), and {@link #button} which sizes
 * buttons to their (translated, possibly Russian) label as UI.md §0.1 requires.
 */
public abstract class CasinoTableScreen extends AbstractContainerScreen<CasinoTableMenu> implements dev.nezo.burmaldaholic.client.ui.FitScaled {
	protected static final int FELT = 0xFF1E5E3A;
	protected static final int FELT_BORDER = 0xFF0E2E1C;
	protected static final int TEXT = 0xFFFFFFFF;
	protected static final int ERROR = 0xFFFF5555;
	private CompoundTag state = new CompoundTag();
	private @Nullable Component error;
	private int errorTicks;

	protected CasinoTableScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		this(menu, inventory, title, 256, 200);
	}

	protected CasinoTableScreen(CasinoTableMenu menu, Inventory inventory, Component title, int width, int height) {
		super(menu, inventory, title, width, height);
		this.inventoryLabelY = -10_000; // slot-less menus: hide the "Inventory" label
	}

	/** Opt in to the compact layout ({@link dev.nezo.burmaldaholic.client.ui.FitScaled}; the extras machines). Default false. */
	protected boolean fitToScreen() {
		return false;
	}

	private float fit = 1f;
	private final int[] fitMemo = {-1, -1, -1, -1};

	@Override
	public float fitScale() {
		return fit;
	}

	@Override
	protected void init() {
		fit = dev.nezo.burmaldaholic.client.ui.FitScaled.apply(this, fitToScreen(), imageWidth + 8, imageHeight, fitMemo);
		super.init();
		if (entrance == null) {
			entrance = dev.nezo.burmaldaholic.client.ui.ScreenEntrance.install(this, this::entranceRect);
		}
		acceptState(ClientTableCache.get(menu.pos()));
	}

	/** The panel the entrance scales and veils, in GUI coordinates (default: the container image; J-L2 kit hook). */
	protected dev.nezo.burmaldaholic.core.ui.UiLayout.Rect entranceRect() {
		return new dev.nezo.burmaldaholic.core.ui.UiLayout.Rect(leftPos, topPos, imageWidth, imageHeight);
	}

	/** Shared entrance (global.md §4.14; lane J-L2 kit). */
	private dev.nezo.burmaldaholic.client.ui.@Nullable ScreenEntrance entrance;

	/**
	 * Casino location theme of this table (J-L2 kit hook): the {@code theme} string of the state when the block entity
	 * sends one ({@code village | bastion | end}), else the dimension the player is in; a forced theme
	 * ({@link dev.nezo.burmaldaholic.client.ui.CasinoTheme#force}, the {@code cards.theme} config) wins.
	 */
	protected dev.nezo.burmaldaholic.client.ui.CasinoTheme theme() {
		return dev.nezo.burmaldaholic.client.ui.CasinoTheme.resolve(state.getStringOr("theme", ""));
	}

	private final dev.nezo.burmaldaholic.core.ui.NarrationThrottle<Component> narration = new dev.nezo.burmaldaholic.core.ui.NarrationThrottle<>();

	/** Throttled narration of a public table event (≤ 1 per 600 ms, newest wins; J-L2 kit hook, cards.md §0.6). */
	public void narrate(Component message) {
		Component now = narration.offer(message, net.minecraft.util.Util.getMillis());
		if (now != null) dev.nezo.burmaldaholic.client.ui.CasinoUi.say(now);
	}

	/** Milliseconds since the screen opened ("on open" animations; J-L2 kit hook). */
	protected long openAge() {
		return entrance == null ? Long.MAX_VALUE / 4 : entrance.age();
	}

	/** A kit {@code CasinoButton} sized to its label (min {@code minWidth}) at GUI-relative {@code x, y} (J-L2 kit hook). */
	protected dev.nezo.burmaldaholic.client.ui.CasinoButton casinoButton(Component label, int x, int y, int minWidth,
			dev.nezo.burmaldaholic.client.ui.CasinoButton.Style style, java.util.function.Consumer<dev.nezo.burmaldaholic.client.ui.CasinoButton> onPress) {
		int w = dev.nezo.burmaldaholic.client.ui.CasinoButton.width(font, label, minWidth, false);
		return addRenderableWidget(new dev.nezo.burmaldaholic.client.ui.CasinoButton(leftPos + x, topPos + y, w, 20, label, style, onPress));
	}

	/** Latest server state (never null). */
	protected CompoundTag state() {
		return state;
	}

	public final void acceptState(CompoundTag newState) {
		this.state = newState;
		onStateChanged(newState);
	}

	/** Rebuild widgets / animations here. */
	protected void onStateChanged(CompoundTag newState) {}

	public void showError(Component message) {
		this.error = message;
		this.errorTicks = 60;
	}

	/** The current error line (null = none; J-L2 hook for screens that draw their own labels). */
	protected @Nullable Component errorLine() {
		return error;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		Component pendingNarration = narration.poll(net.minecraft.util.Util.getMillis());
		if (pendingNarration != null) dev.nezo.burmaldaholic.client.ui.CasinoUi.say(pendingNarration);
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	protected void sendAction(String action, CompoundTag args) {
		ClientPlayNetworking.send(new TableActionPayload(menu.pos(), action, args));
	}

	protected void sendAction(String action) {
		sendAction(action, new CompoundTag());
	}

	// ---- common state helpers -----------------------------------------------------------------

	protected long balance() {
		return state.getLongOr("balance", 0);
	}

	protected long minBet() {
		return state.getLongOr("min", 1);
	}

	protected long maxBet() {
		return state.getLongOr("max", 0);
	}

	protected long myStake() {
		return state.getLongOr("stake", 0);
	}

	protected int mySeat() {
		return state.getIntOr("seat", -1);
	}

	protected String phase() {
		return state.getStringOr("phase", "idle");
	}

	/** Seconds left on a server timer, or -1. */
	protected long timerSeconds(String id) {
		long ticks = state.getCompoundOrEmpty("timers").getLongOr(id, -1);
		return ticks < 0 ? -1 : (ticks + 19) / 20;
	}

	/** Player names per seat index ("" = empty). */
	protected List<String> seatNames() {
		int count = state.getIntOr("seat_count", 0);
		List<String> names = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			names.add("");
		}
		ListTag seats = state.getListOrEmpty("seats");
		for (int i = 0; i < seats.size(); i++) {
			CompoundTag s = seats.getCompoundOrEmpty(i);
			int index = s.getIntOr("index", -1);
			if (index >= 0 && index < names.size()) {
				names.set(index, s.getStringOr("name", ""));
			}
		}
		return names;
	}

	/** "Min 1 · Max 1 000" line. */
	protected Component limitsLine() {
		return Component.translatable("gui.burmaldaholic.common.limits", Texts.number(minBet()), Texts.number(maxBet()));
	}

	/** A button as wide as its label needs (min {@code minWidth}), at GUI-relative {@code x, y}. */
	protected Button button(Component label, int x, int y, int minWidth, Button.OnPress onPress) {
		int w = Math.max(minWidth, font.width(label) + 8);
		return addRenderableWidget(Button.builder(label, onPress).bounds(leftPos + x, topPos + y, w, 20).build());
	}

	// ---- rendering ----------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		// J-L2 kit: the felt nine-slice (global.md §2.2 panel/felt); flat felt when the art is missing
		if (!dev.nezo.burmaldaholic.client.ui.CasinoUi.sprite(graphics, dev.nezo.burmaldaholic.client.ui.UiSprites.PANEL_FELT, leftPos, topPos, imageWidth,
			imageHeight)) {
			graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, FELT_BORDER);
			graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, FELT);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		graphics.text(font, title, titleLabelX, titleLabelY, TEXT, true);
		if (error != null) {
			graphics.centeredText(font, error, imageWidth / 2, imageHeight - 12, ERROR);
		}
	}
}
