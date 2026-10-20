package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The menu-less counter screen (merchant NPC, {@code /casino store}; EC-SPEC §3): opened by {@code CounterOpenPayload}
 * with the server's tab list (Store, Comps). Same {@link CounterShell} as the cashier's {@link CounterScreen}.
 */
public class CounterSceneScreen extends CasinoScreen implements CounterShell.Host, CounterShell.Holder {
	private final CounterShell shell = new CounterShell(this);
	private final CounterSource source;
	private final String dimension;
	private final List<String> tabIds;
	private Viewport viewport = new Viewport(480, 270, 1, 1f);
	private boolean tabsSet;

	public CounterSceneScreen(CounterSource source, List<String> tabs, String dimension) {
		super(Component.translatable("gui.burmaldaholic.cashier.store_title"), 480, 270);
		this.source = source;
		this.tabIds = List.copyOf(tabs);
		this.dimension = dimension;
	}

	@Override
	public CounterShell shell() {
		return shell;
	}

	@Override
	protected float applyFit(int[] memo) {
		viewport = FitScaled.applyViewport(this, memo);
		return viewport.factor();
	}

	@Override
	protected void init() {
		super.init();
		panel = new UiLayout.Rect(0, 0, width, height);
		if (!tabsSet) {
			tabsSet = true;
			shell.setTabs(tabIds);
		}
		shell.resize(viewport);
	}

	@Override
	public void tick() {
		super.tick();
		shell.tick();
	}

	@Override
	protected boolean showBanner() {
		return false;
	}

	@Override
	protected boolean showBalance() {
		return false;
	}

	@Override
	public void showError(Component message) {
		shell.result(false, message);
	}

	// ---- host ------------------------------------------------------------------------------------------------------

	@Override
	public Font font() {
		return font;
	}

	@Override
	public void closeCounter() {
		onClose();
	}

	@Override
	public CounterSource source() {
		return source;
	}

	@Override
	public String dimension() {
		return dimension;
	}

	@Override
	public CompoundTag state() {
		return new CompoundTag();
	}

	@Override
	public void sendTable(String action, CompoundTag args) {}

	@Override
	public long balance() {
		return ClientCasinoState.shownBalance();
	}

	@Override
	public long withdrawable() {
		return -1;
	}

	// ---- drawing / input -------------------------------------------------------------------------------------------

	@Override
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		CasinoUi.backdrop(g, theme(), new UiLayout.Rect(0, 0, width, height));
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		shell.render(g, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (shell.click(event.x(), event.y(), event.button(), event.modifiers())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) shell.trackKey(event.key(), true);
		else if (shell.key(event.key(), event.modifiers())) return true;
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (shell.scroll(x, y, dy)) return true;
		return super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		shell.trackKey(event.key(), false);
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (shell.charTyped(event.codepoint())) return true;
		return super.charTyped(event);
	}
}
