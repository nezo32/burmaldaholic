package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared frame of the Wheel of Fortune and Plinko machine screens (visual/extras.md §2.2): the themed {@link Scene} as
 * a 400 × 240 panel (backdrop, frame, title banner), casino buttons, the chip counter (held while a reveal runs) and
 * the base error line. Widgets are rebuilt on every server state.
 */
abstract class ExtrasTableScreen extends CasinoTableScreen {
	static final int PAD = 8;
	static final int MUTED = 0xFFDDDDDD;
	static final int GOLD = 0xFFFFD700;
	protected final Scene scene;
	protected float partial;
	protected int ticks;
	private long heldBalance = -1;

	protected ExtrasTableScreen(CasinoTableMenu menu, Inventory inventory, Component title, Scene scene) {
		super(menu, inventory, title, Scene.W, Scene.H);
		this.scene = scene;
		this.titleLabelY = -10_000;
	}

	/** The compact layout at small GUI sizes: the full 400 × 240 scene drawn at a lower whole GUI scale (FitScaled). */
	@Override
	protected boolean fitToScreen() {
		return true;
	}

	@Override
	protected void init() {
		super.init();
		topPos = Scene.top(height);
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
		layout();
	}

	/** Adds widgets at panel-local positions. */
	protected abstract void layout();

	protected KitButton button(int x, int y, int w, int h, Component label, KitButton.Style style, Consumer<KitButton> onPress) {
		KitButton b = new KitButton(leftPos + x, topPos + y, w, h, label, style, onPress);
		addRenderableWidget(b);
		return b;
	}

	protected void holdBalance(long balance) {
		heldBalance = balance;
	}

	protected void releaseBalance() {
		heldBalance = -1;
	}

	protected long shownBalance() {
		return heldBalance >= 0 ? heldBalance : balance();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		ticks++;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		scene.backdrop(g, leftPos, topPos);
		extractPlayArea(g, mouseX - leftPos, mouseY - topPos);
		scene.frame(g, font, leftPos, topPos, title);
	}

	/** Objects on the backdrop under the frame, panel-local via the pose. */
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		partial = a;
		super.extractRenderState(graphics, mouseX, mouseY, a);
		extractOverlay(graphics, mouseX, mouseY);
	}

	/** Over the widgets (pop-outs, banners). Absolute coordinates. */
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		extractContent(graphics, xm - leftPos, ym - topPos);
		int[] at = chipCounterAt();
		Scene.chipCounter(graphics, font, Texts.number(shownBalance()), at[0], at[1], false);
	}

	/** Panel-local position of the chip counter (bottom left by default). */
	protected int[] chipCounterAt() {
		return new int[] {16, 210};
	}

	/** Draws in panel-relative coordinates (text, side columns). */
	protected abstract void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

	/** Space / Enter skip the running reveal. */
	protected boolean skip() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if ((k == 32 || k == 257 || k == 335) && skip()) return true;
		return super.keyPressed(event);
	}

	protected static int alphaWhite(double a) {
		return Kit.fade(a);
	}
}
