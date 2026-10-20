package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.config.CasinoConfigScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
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
import org.jspecify.annotations.Nullable;

/**
 * Base of the v2 non-container screens (Coin Flip, Scratch, Dice Duel, Plinko / Wheel machines, PvP invite and duel;
 * docs/architecture/ui-v2.md §4.1, D5): the same {@link V2Chrome} over {@link CasinoScreen}. These screens have no table
 * menu: the subclass decides what {@link #sendRaw} does (a module payload) and calls {@link #state} with its view when
 * its data changes. Fills the virtual GUI like {@link V2TableScreen}.
 */
public abstract class V2SceneScreen<V> extends CasinoScreen implements V2Chrome.Host, V2Chrome.Holder, dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.Host,
		dev.nezo.burmaldaholic.client.ui.ScreenEntrance.OverFrame {
	protected final V2Chrome chrome = new V2Chrome(this);
	private Viewport viewport = new Viewport(480, 270, 1, 1f);
	protected @Nullable V view;

	protected V2SceneScreen(Component title) {
		super(title, 480, 270);
	}

	/** The game's flow (gates). */
	protected abstract Flow<V> flow();

	/** Registers the PlayArea targets for the current view. */
	protected abstract void layoutPlay(TableGrid grid, V view, HitMap hits);

	/** Draws the PlayArea content over the felt. */
	protected abstract void renderPlay(GuiGraphicsExtractor g, TableGrid grid, V view, int mouseX, int mouseY, float partial);

	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, V view, int mouseX, int mouseY, float partial) {}

	/** The BetBar port (null = no BetBar). */
	protected @Nullable BetPort betPort() {
		return null;
	}

	protected long minBet() {
		return 0;
	}

	protected long maxBet() {
		return 0;
	}

	protected void openRules() {}

	/** Sets the view (the subclass's state arrived) and re-applies the flow; {@code ack} = the server's ack or -1. */
	public void state(V view, long ack) {
		this.view = view;
		CompoundTag t = new CompoundTag();
		t.putLong("ack", ack);
		chrome.acceptState(t);
		refresh();
	}

	protected void refresh() {
		if (view == null || font == null) return;
		chrome.top.set(title, minBet(), maxBet(), 0, 0);
		chrome.top.balance.target(balance());
		chrome.apply(flow(), view, betPort());
	}

	@Override
	public V2Chrome chrome() {
		return chrome;
	}

	@Override
	protected final float applyFit(int[] memo) { // final: every v2 game sizes by ViewportPolicy (owner B6)
		viewport = FitScaled.applyViewport(this, memo);
		return viewport.factor();
	}

	@Override
	public final float offsetX() {
		return viewport.guiOx();
	}

	@Override
	public final float offsetY() {
		return viewport.guiOy();
	}

	@Override
	protected void init() {
		super.init();
		panel = new dev.nezo.burmaldaholic.core.ui.UiLayout.Rect(0, 0, width, height);
		chrome.resize(viewport);
		refresh();
	}

	@Override
	public void tick() {
		super.tick();
		chrome.tick();
	}

	@Override
	public Font font() {
		return font;
	}

	@Override
	public void layoutPlay(TableGrid grid, HitMap hits) {
		if (view != null) layoutPlay(grid, view, hits);
	}

	@Override
	public void onGate(String id, Gate gate) {
		if (!gate.action().isEmpty()) chrome.send(gate.action());
	}

	@Override
	public void onTopIcon(String id) {
		switch (id) {
			case KeyMap.Ids.TOP_LEAVE -> onClose();
			case KeyMap.Ids.TOP_SETTINGS -> {
				if (minecraft != null) minecraft.gui.setScreen(new CasinoConfigScreen(this));
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
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// nothing here: a plain Screen extracts its background OUTSIDE the entrance's fit / frame pose, so the felt and the
		// outside-frame dim drew at the top-left corner of a framed screen (PL report); they draw in extractPanel instead
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		chrome.renderBackground(g, theme()); // under the pose: felt in the frame, the dim around it
		if (view != null) renderPlay(g, chrome.grid(), view, mouseX, mouseY, a);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		chrome.renderChrome(g, mouseX, mouseY);
		if (view != null) renderOverlay(g, chrome.grid(), view, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// a celebration running over the screen: a click skips it; a click on an enabled kit target ALSO does what it
		// says (the gold Rebet after a big win used to need two clicks; review T-C2 V3)
		var fx = dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get();
		if (fx.skippable()) {
			fx.skip();
			if (!actsThroughCelebration(event.x(), event.y())) return true;
		}
		if (chrome.click(event.x(), event.y(), event.button())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	/**
	 * Whether a click at (x, y) during a skippable celebration also acts (default: an enabled kit target). Screens with their
	 * own widgets (the PvP match screens) add an enabled widget under the cursor (T-E2 #3).
	 */
	protected boolean actsThroughCelebration(double x, double y) {
		return chrome.hitsEnabled(x, y);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		var fx = dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get();
		if (fx.skippable()) { // the same for keys: a kit key (Space = the primary, 1–5 chips, …) skips AND acts
			if (!chrome.keyHitsEnabled(event.key())) return fx.onKey(event) || super.keyPressed(event);
			fx.skip();
		}
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

	/** The shared celebration, over the screen and outside its frame (this screen is its {@code Host}). */
	@Override
	public void extractOverFrame(GuiGraphicsExtractor g) {
		dev.nezo.burmaldaholic.client.fx.CelebrationOverlay.get().extract(g, false);
	}
}
