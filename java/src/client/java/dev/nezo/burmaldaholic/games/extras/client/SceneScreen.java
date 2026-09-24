package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Base of the redesigned item-driven extras screens (Coin Flip, Scratch Cards; visual/extras.md §2.2) on the J-L2 kit's
 * {@link CasinoScreen} (entrance, error slide, casino key rules): the game's own {@link Scene} (backdrop, frame,
 * marquee, title banner) as a 400 × 240 panel, casino buttons and the HUD chip counter at the bottom left instead of
 * the kit's balance plaque (the mockups). Server-driven like {@link ExtrasScreen}: it renders the last
 * {@code ExtrasScreenPayload} state and sends {@code ExtrasActionPayload}s; the animations only reveal the server's
 * settled result. The chip counter is held at its pre-result value while a reveal runs ({@link #holdBalance}), so the
 * number never spoils the landing (extras-pvp.md §0.3 rule 4).
 */
abstract class SceneScreen extends CasinoScreen {
	protected final Scene scene;
	private final String game;
	private CompoundTag state;
	/** Panel origin. */
	protected int px;
	protected int py;
	protected int ticks;
	private long heldBalance = -1;

	protected SceneScreen(String game, Component title, CompoundTag state, Scene scene) {
		super(title, Scene.W, Scene.H);
		this.game = game;
		this.state = state;
		this.scene = scene;
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
		if (minecraft != null) rebuildWidgets();
	}

	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {}

	@Override
	protected void init() {
		super.init();
		px = Scene.left(width);
		py = Scene.top(height);
		layout();
	}

	/** Adds the widgets (panel-local through {@link #button}). */
	protected abstract void layout();

	protected KitButton add(KitButton b) {
		addRenderableWidget(b);
		return b;
	}

	/** A casino button at panel-local (x, y). */
	protected KitButton button(int x, int y, int w, int h, Component label, KitButton.Style style, Consumer<KitButton> onPress) {
		return add(new KitButton(px + x, py + y, w, h, label, style, onPress));
	}

	protected void send(String action, CompoundTag args) {
		ClientPlayNetworking.send(new ExtrasActionPayload(game, action, args));
	}

	/** Keep showing {@code balance} on the chip counter until {@link #releaseBalance}. */
	protected void holdBalance(long balance) {
		heldBalance = balance;
	}

	protected void releaseBalance() {
		heldBalance = -1;
	}

	protected long shownBalance() {
		return heldBalance >= 0 ? heldBalance : state.getLongOr("balance", 0);
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
	protected CasinoTheme theme() {
		return CasinoTheme.current();
	}

	@Override
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		scene.backdrop(g, px, py);
		extractPlayArea(g, mouseX, mouseY, a);
		scene.frame(g, font, px, py, sceneTitle());
	}

	/** The title on the scene's banner. */
	protected Component sceneTitle() {
		return title;
	}

	/** Objects on the backdrop, under the frame (the frame's border overlaps them). */
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractContent(g, mouseX, mouseY, a);
		Scene.chipCounter(g, font, Texts.number(shownBalance()), px + 16, py + 210, false);
	}

	/** Text and wells under the widgets. */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

	@Override
	protected int errorY() {
		return py + 184;
	}

	@Override
	public void tick() {
		super.tick();
		ticks++;
	}

	/** Space / Enter skip the running reveal (extras-pvp §0.1 rule 5). */
	protected boolean skip() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if ((k == 32 || k == 257 || k == 335) && skip()) return true;
		return super.keyPressed(event);
	}
}
