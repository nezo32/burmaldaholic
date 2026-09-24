package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Base of the redesigned item-driven extras screens (Coin Flip, Scratch Cards; visual/extras.md §2.2): a themed
 * {@link Scene} (backdrop, frame, marquee, title banner) as a 400 × 240 panel, casino buttons, the chip counter and the
 * error line. Server-driven like {@link ExtrasScreen}: it renders the last {@code ExtrasScreenPayload} state and sends
 * {@code ExtrasActionPayload}s; the animations only reveal the server's settled result.
 *
 * <p>The balance shown on the chip counter is held at its pre-result value while a reveal runs
 * ({@link #holdBalance}), so the number never spoils the landing (extras-pvp.md §0.3 rule 4).
 */
abstract class SceneScreen extends ExtrasScreen {
	protected final Scene scene;
	/** Panel origin. */
	protected int px;
	protected int py;
	private @Nullable Component sceneError;
	private long sceneErrorUntil;
	private long heldBalance = -1;

	protected SceneScreen(String game, Component title, CompoundTag state, Scene scene) {
		super(game, title, state, Scene.W, Scene.H);
		this.scene = scene;
	}

	@Override
	void showError(Component message) {
		sceneError = message;
		sceneErrorUntil = Util.getMillis() + 3000;
	}

	@Override
	protected void init() {
		px = Scene.left(width);
		py = Scene.top(height);
		left = px;
		top = py;
		layout();
	}

	protected KitButton add(KitButton b) {
		addRenderableWidget(b);
		return b;
	}

	/** A casino button at panel-local (x, y). */
	protected KitButton button(int x, int y, int w, int h, Component label, KitButton.Style style, Consumer<KitButton> onPress) {
		return add(new KitButton(px + x, py + y, w, h, label, style, onPress));
	}

	@Override
	protected void send(String action, CompoundTag args) {
		ClientPlayNetworking.send(new ExtrasActionPayload(game(), action, args));
	}

	/** Keep showing {@code balance} on the chip counter until {@link #releaseBalance}. */
	protected void holdBalance(long balance) {
		heldBalance = balance;
	}

	protected void releaseBalance() {
		heldBalance = -1;
	}

	protected long shownBalance() {
		return heldBalance >= 0 ? heldBalance : state().getLongOr("balance", 0);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackgroundScreenOnly(g, mouseX, mouseY, a);
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
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractContent(g, mouseX, mouseY, a);
		Scene.chipCounter(g, font, Texts.number(shownBalance()), px + 16, py + 210, false);
		renderWidgets(g, mouseX, mouseY, a);
		extractOverlay(g, mouseX, mouseY, a);
		if (sceneError != null && Util.getMillis() < sceneErrorUntil) {
			int w = Math.min(360, font.width(sceneError) + 16);
			int x = px + Scene.W / 2 - w / 2;
			int y = py + 182;
			g.fill(x, y, x + w, y + 14, 0xE0300818);
			Kit.frameRect(g, x, y, w, 14, Kit.RED);
			Kit.centeredFit(g, font, sceneError, px + Scene.W / 2, y + 3, w - 8, Kit.RED_LIGHT, true);
		}
	}

	/** Over the widgets (pop-outs, banners, the scraper cursor). */
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

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

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event, doubleClick);
	}
}
