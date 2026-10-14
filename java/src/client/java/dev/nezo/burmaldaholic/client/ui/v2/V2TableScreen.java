package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Base of every v2 menu table screen (blackjack, baccarat, poker, UTH, roulette, craps; docs/architecture/ui-v2.md §4.1).
 * The screen fills the virtual GUI (D3: {@code leftPos = topPos = 0}, the screen is the whole GUI), so layout px = GUI px =
 * what the mouse reports. Subclasses supply the pure parts — {@link #view} (state tag → the game's view record),
 * {@link #flow} (gates), {@link #layoutPlay} (felt targets) — and draw the PlayArea in {@link #renderPlay}. The kit's
 * {@link V2Chrome} does the rest: TopBar, Dock (BetBar / ActionRow / status / primary), banner, toasts, the hit map, keys
 * and the acknowledgement watchdog.
 *
 * <p>Sending: use {@link #sendTracked} (watchdog) for every player action; {@link #sendAction} only for silent ones.
 */
public abstract class V2TableScreen<V> extends CasinoTableScreen implements V2Chrome.Host, V2Chrome.Holder {
	protected final V2Chrome chrome = new V2Chrome(this);
	private Viewport viewport = new Viewport(480, 270, 1, 1f);
	protected @Nullable V view;

	protected V2TableScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 480, 270);
		this.titleLabelY = -10_000;
	}

	// ---- the game's pure parts ----------------------------------------------------------------------------------------

	/** The game's view record from the server state (pure mapping; called on every state change). */
	protected abstract V view(CompoundTag state);

	/** The game's flow (gates); usually a constant. */
	protected abstract Flow<V> flow();

	/** Registers the PlayArea targets (felt spots, seat boxes, …) for the current view; ids per {@link KeyMap.Ids}. */
	protected abstract void layoutPlay(TableGrid grid, V view, HitMap hits);

	/** Draws the PlayArea content (cards, seats, stacks, felt prints) over the kit's felt. */
	protected abstract void renderPlay(GuiGraphicsExtractor g, TableGrid grid, V view, int mouseX, int mouseY, float partial);

	/** Content above the chrome (flights, celebrations of the game). Default nothing. */
	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, V view, int mouseX, int mouseY, float partial) {}

	/** Where the BetBar sends (default: single spot, {@code bet} with {@code amount}, tools by their id). Null = no BetBar. */
	protected @Nullable BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", denom);
				sendTracked("bet", args, "", denom);
			}

			@Override
			public void tool(String toolId) {
				sendTracked(toolId, new CompoundTag(), "", 0);
			}
		};
	}

	/** The TopBar title (default: the screen title). */
	protected Component tableTitle() {
		return title;
	}

	/** Opens the rules (TopBar {@code ?}); default: nothing. */
	protected void openRules() {}

	// ---- kit plumbing -------------------------------------------------------------------------------------------------

	@Override
	public V2Chrome chrome() {
		return chrome;
	}

	public @Nullable V currentView() {
		return view;
	}

	@Override
	protected float applyFit(int[] memo) {
		viewport = FitScaled.applyViewport(this, memo);
		return viewport.factor();
	}

	@Override
	protected void init() {
		super.init();
		leftPos = 0; // D3: the screen is the whole GUI (imageWidth is final in this MC; nothing uses it once leftPos = 0)
		topPos = 0;
		chrome.resize(viewport);
		refresh();
	}

	@Override
	protected dev.nezo.burmaldaholic.core.ui.UiLayout.Rect entranceRect() {
		return new dev.nezo.burmaldaholic.core.ui.UiLayout.Rect(0, 0, width, height);
	}

	@Override
	protected void onStateChanged(CompoundTag state) {
		chrome.acceptState(state);
		view = view(state);
		refresh();
	}

	/** Re-applies the flow to the chrome (state change, resize, or a local change such as an armed chip). */
	protected void refresh() {
		if (view == null || font == null) return;
		int seated = 0;
		for (String n : seatNames()) if (!n.isEmpty()) seated++;
		chrome.top.set(tableTitle(), minBet(), maxBet(), seated, state().getIntOr("seat_count", 0));
		chrome.top.icons(true, hasSettings(), true);
		chrome.top.balance.target(balance());
		chrome.apply(flow(), view, betPort());
	}

	/** Sends a player action under the watchdog; {@code spot}/{@code amount} = the pending chip ("" / 0 = none). */
	protected void sendTracked(String action, CompoundTag args, String spot, long amount) {
		chrome.send(action, args, spot, amount);
	}

	protected void sendTracked(String action) {
		chrome.send(action);
	}

	@Override
	public Font font() {
		return font;
	}

	@Override
	public void sendRaw(String action, CompoundTag args) {
		sendAction(action, args);
	}

	@Override
	public void layoutPlay(TableGrid grid, HitMap hits) {
		if (view != null) layoutPlay(grid, view, hits);
	}

	/** An enabled primary / secondary / action gate: by default sends the gate's action (rebet through the BetPort). */
	@Override
	public void onGate(String id, Gate gate) {
		if (gate.action().isEmpty()) return;
		if (gate.action().equals(BetGates.REBET)) {
			BetPort p = betPort();
			if (p != null) {
				p.tool(BetGates.REBET);
				return;
			}
		}
		sendTracked(gate.action());
	}

	/** A settings screen for some tables (TopBar gear, {@link #registerSettings}). */
	public record SettingsProvider(java.util.function.Predicate<net.minecraft.world.level.block.entity.BlockEntity> applies,
			java.util.function.Consumer<net.minecraft.core.BlockPos> open) {}

	private static final java.util.List<SettingsProvider> SETTINGS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Registers the owner of the TopBar gear for the tables {@code applies} accepts (bots: a bot table's gear opens its
	 * Table settings, v0.1.2 hotfix A9 / review R-AB M6). The gear ({@code top.settings}) shows only at a table some
	 * provider applies to; a click opens the first one's {@code open(pos)}. Call once at client init.
	 */
	public static void registerSettings(java.util.function.Predicate<net.minecraft.world.level.block.entity.BlockEntity> applies,
			java.util.function.Consumer<net.minecraft.core.BlockPos> open) {
		SETTINGS.add(new SettingsProvider(applies, open));
	}

	private static final java.util.function.Predicate<V2TableScreen<?>> NO_HOOK = s -> false;

	/**
	 * @deprecated the integrator's bridge for the bots gear; use {@link #registerSettings}. While a module still sets it,
	 *             it is a settings provider for {@link dev.nezo.burmaldaholic.core.bots.BotTable bot tables} (what the
	 *             one user checks); removed once that module moved (phase 3 cleanup).
	 */
	@Deprecated
	public static java.util.function.Predicate<V2TableScreen<?>> topSettingsHook = NO_HOOK;

	/** The settings provider of this table, or null (no gear). */
	protected @Nullable SettingsProvider settingsProvider() {
		if (minecraft == null || minecraft.level == null) return null;
		net.minecraft.world.level.block.entity.BlockEntity be = minecraft.level.getBlockEntity(getMenu().pos());
		if (be == null) return null;
		for (SettingsProvider p : SETTINGS) if (p.applies().test(be)) return p;
		if (topSettingsHook != NO_HOOK && be instanceof dev.nezo.burmaldaholic.core.bots.BotTable) {
			return new SettingsProvider(b -> true, pos -> topSettingsHook.test(this));
		}
		return null;
	}

	/** Whether the TopBar shows the gear (default: a {@link #registerSettings settings provider} applies to this table). */
	protected boolean hasSettings() {
		return settingsProvider() != null;
	}

	@Override
	public void onTopIcon(String id) {
		switch (id) {
			case KeyMap.Ids.TOP_LEAVE -> {
				if (mySeat() >= 0) sendAction("leave");
				onClose();
			}
			case KeyMap.Ids.TOP_SETTINGS -> {
				SettingsProvider p = settingsProvider();
				if (p != null) p.open().accept(getMenu().pos());
			}
			case KeyMap.Ids.TOP_RULES -> openRules();
			default -> {}
		}
	}

	@Override
	public void showError(Component message) {
		chrome.error(message);
	}

	@Override
	protected boolean drawsPanel() {
		return false;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		chrome.tick();
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		chrome.renderBackground(g, theme());
		if (view != null) renderPlay(g, chrome.grid(), view, mouseX, mouseY, a);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		chrome.renderChrome(g, mouseX, mouseY);
		if (view != null) renderOverlay(g, chrome.grid(), view, mouseX, mouseY, a);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		// the chrome draws the title (TopBar) and errors (toasts)
	}

	// ---- input -----------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (chrome.click(event.x(), event.y(), event.button())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (chrome.key(event.key())) return true;
		return super.keyPressed(event);
	}

	/** Ends a held key (the chrome swallows its auto-repeat until then; review R-KW M5). */
	@Override
	public boolean keyReleased(KeyEvent event) {
		chrome.keyReleased(event.key());
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (chrome.scroll(x, y, dy)) return true;
		return super.mouseScrolled(x, y, dx, dy);
	}
}
