package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The cashier block's counter screen (EC-SPEC §3; replaces the v0.1 {@code CashierScreen}): a table-menu host of the
 * {@link CounterShell} with the tabs Exchange / Store / Comps. Fills the virtual GUI like the v2 table screens
 * ({@code leftPos = topPos = 0}); the shell centres its 480 × 270 panel.
 */
public class CounterScreen extends CasinoTableScreen implements CounterShell.Host, CounterShell.Holder {
	private final CounterShell shell = new CounterShell(this);
	private Viewport viewport = new Viewport(480, 270, 1, 1f);
	private boolean tabsSet;

	public CounterScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.cashier.title"), 480, 270);
		this.titleLabelY = -10_000;
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
		leftPos = 0;
		topPos = 0;
		if (!tabsSet) {
			tabsSet = true;
			shell.setTabs(CounterTabs.CASHIER_TABS);
		}
		shell.resize(viewport);
	}

	@Override
	protected UiLayout.Rect entranceRect() {
		return new UiLayout.Rect(0, 0, width, height);
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		if (font != null) shell.stateChanged();
	}

	@Override
	public void showError(Component message) {
		shell.result(false, message);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		shell.tick();
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
		return CounterSource.cashier(menu.pos());
	}

	@Override
	public String dimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "?" : CounterSessions.dimCode(mc.level.dimension());
	}

	@Override
	public CompoundTag state() {
		return super.state();
	}

	@Override
	public void sendTable(String action, CompoundTag args) {
		sendAction(action, args);
	}

	@Override
	public long balance() {
		return super.balance();
	}

	@Override
	public long withdrawable() {
		return state().getLongOr("withdrawable", 0);
	}

	// ---- drawing / input -------------------------------------------------------------------------------------------

	@Override
	protected boolean drawsPanel() {
		return false;
	}

	@Override
	protected CasinoTheme theme() {
		return CasinoTheme.LOBBY;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		CasinoUi.backdrop(g, theme(), new UiLayout.Rect(0, 0, width, height));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		shell.render(g, mouseX, mouseY, a);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		// the shell draws everything
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (shell.click(event.x(), event.y(), event.button(), event.modifiers())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!event.isEscape() && shell.key(event.key(), event.modifiers())) return true;
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (shell.charTyped(event.codepoint())) return true;
		return super.charTyped(event);
	}
}
