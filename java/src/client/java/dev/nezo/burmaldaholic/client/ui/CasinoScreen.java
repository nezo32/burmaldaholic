package dev.nezo.burmaldaholic.client.ui;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.core.ui.BalanceTicker;
import dev.nezo.burmaldaholic.core.ui.NarrationThrottle;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Base of every casino (non-container) screen (docs/architecture/animation.md §2.12, lane J-L2): the themed backdrop
 * of the casino location, the gold nine-slice frame, the header bar (title banner + balance plaque with the HUD
 * ticker), the shared entrance ({@link ScreenEntrance}), the error line that slides in from below, the compact-layout
 * switch ({@link #layoutSize()}) and the casino key rules (Esc closes, but never cancels a confirmed action: override
 * {@link #busy()}). The shared {@code CelebrationOverlay} is drawn above every screen by {@code ClientFx}.
 *
 * <p>Drawing order (extras.md §1.1): {@link #extractScene} (backdrop → frame → header) in the background stratum,
 * then {@link #extractPanel} (content under the widgets), the widgets, {@link #extractOverlay} (pop-outs, banners), the
 * error line. Coordinates: {@link #panel} is the centred panel in GUI space; {@link #px}/{@link #py} convert
 * panel-local positions.
 *
 * <p>Subclass contract: build widgets in {@link #init()} after {@code super.init()} with {@link #button} / {@link #add};
 * keep all state in fields (init runs again on resize).
 */
public abstract class CasinoScreen extends Screen {
	private final int fullW;
	private final int fullH;
	private @Nullable CasinoTheme theme;
	protected UiLayout.Rect panel = new UiLayout.Rect(0, 0, UiLayout.FULL_W, UiLayout.FULL_H);
	private @Nullable ScreenEntrance entrance;
	private @Nullable Component error;
	private long errorAt;
	private int errorTicks;
	/** The balance plaque ticker (same rules as the HUD). */
	protected final BalanceTicker balanceTicker = new BalanceTicker();
	private final NarrationThrottle<Component> narration = new NarrationThrottle<>();

	protected CasinoScreen(Component title) {
		this(title, UiLayout.FULL_W, UiLayout.FULL_H);
	}

	protected CasinoScreen(Component title, int fullW, int fullH) {
		super(title);
		this.fullW = fullW;
		this.fullH = fullH;
	}

	// ---- configuration hooks -------------------------------------------------------------------------------------

	/** The scene (default: the location of the player, resolved once per screen). */
	protected CasinoTheme theme() {
		if (theme == null) theme = CasinoTheme.current();
		return theme;
	}

	/** Forces a theme (e.g. the Loan tab's dark look); takes effect on the next frame. */
	protected void setTheme(CasinoTheme t) {
		this.theme = t;
	}

	/** Draw the title banner at the top border (default true). */
	protected boolean showBanner() {
		return true;
	}

	/** Draw the balance plaque at the top right (default true). */
	protected boolean showBalance() {
		return true;
	}

	/** The balance shown in the plaque (default: the synced balance, held during a presentation, F6). */
	protected long balance() {
		return ClientCasinoState.shownBalance();
	}

	/** True while an action is confirmed and waiting for the server: Esc then does not close the screen. */
	protected boolean busy() {
		return false;
	}

	/** Full / compact / forced-small layout of the current GUI size. */
	public UiLayout.Size layoutSize() {
		return UiLayout.size(width, height);
	}

	public boolean compact() {
		return layoutSize() != UiLayout.Size.L;
	}

	/** GUI x of a panel-local x. */
	protected int px(int localX) {
		return panel.x() + localX;
	}

	/** GUI y of a panel-local y. */
	protected int py(int localY) {
		return panel.y() + localY;
	}

	/** Milliseconds since the screen opened (entrance timing, "on open" animations). */
	protected long openAge() {
		return entrance == null ? Long.MAX_VALUE / 4 : entrance.age();
	}

	// ---- lifecycle -------------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		panel = UiLayout.panel(width, height, fullW, fullH);
		if (entrance == null) entrance = ScreenEntrance.install(this, () -> panel);
		balanceTicker.retarget(balance(), Util.getMillis(), true);
	}

	@Override
	public void tick() {
		super.tick();
		if (errorTicks > 0 && --errorTicks == 0) error = null;
		Component pending = narration.poll(Util.getMillis());
		if (pending != null) CasinoUi.say(pending);
	}

	/**
	 * Narrates a public game event (a card lands, a result) through the vanilla narrator when it is on, at most one per
	 * 600 ms: a message offered sooner waits and is replaced by any newer one (cards.md §0.6).
	 */
	public void narrate(Component message) {
		Component now = narration.offer(message, Util.getMillis());
		if (now != null) CasinoUi.say(now);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !busy();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	/** Shows {@code message} under the content for 3 s, sliding in from below (reduce motion: in place). */
	public void showError(Component message) {
		this.error = message;
		this.errorAt = Util.getMillis();
		this.errorTicks = 60;
	}

	public @Nullable Component error() {
		return error;
	}

	/** Adds a casino button at a GUI position. */
	protected CasinoButton button(Component label, int x, int y, int w, int h, CasinoButton.Style style, Consumer<CasinoButton> onPress) {
		return addRenderableWidget(new CasinoButton(x, y, w, h, label, style, onPress));
	}

	/** Adds any widget. */
	protected <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable
		& net.minecraft.client.gui.narration.NarratableEntry> T add(T widget) {
		return addRenderableWidget(widget);
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------

	@Override
	public final void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		extractScene(g, mouseX, mouseY, a);
	}

	/** Backdrop, frame and header bar (override to add scene layers; call super first). */
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		CasinoTheme t = theme();
		CasinoUi.backdrop(g, t, panel);
		extractFrame(g, t);
		if (showBanner()) CasinoUi.banner(g, font, t, title, panel.centerX(), panel.y() + 3, UiLayout.bannerMaxW(panel.w(), showBalance()));
		if (showBalance()) {
			long now = Util.getMillis();
			balanceTicker.retarget(balance(), now, FxSettings.reduceMotion());
			int dir = balanceTicker.direction(now);
			int color = dir == 0 ? CasinoPalette.GOLD : CasinoUi.mix(CasinoPalette.GOLD, dir > 0 ? CasinoPalette.BONUS : CasinoPalette.CHIP_RED,
				balanceTicker.tint(now));
			int w = UiLayout.PLAQUE_W;
			CasinoUi.balancePlaque(g, font, balanceTicker.value(now), panel.right() - UiLayout.PLAQUE_RIGHT - w, panel.y() + 4, w, color);
		}
	}

	/** The frame (default: the theme's gold nine-slice over the whole panel). */
	protected void extractFrame(GuiGraphicsExtractor g, CasinoTheme t) {
		CasinoUi.frame(g, t, panel);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractPanel(g, mouseX, mouseY, a);
		super.extractRenderState(g, mouseX, mouseY, a);
		extractOverlay(g, mouseX, mouseY, a);
		extractError(g);
	}

	/** Content under the widgets (panels, rows, art). */
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

	/** Content above the widgets (pop-outs, banners, tooltips of art). */
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

	/** Where the error line sits (GUI y of its top; default: 14 px above the panel bottom). */
	protected int errorY() {
		return panel.bottom() - 14 - 12;
	}

	private void extractError(GuiGraphicsExtractor g) {
		if (error == null) return;
		int dy = UiLayout.errorSlide(Util.getMillis() - errorAt, FxSettings.reduceMotion());
		FxText.wrappedCentered(g, font, error, panel.centerX(), errorY() + dy, panel.w() - 40, 2, CasinoPalette.CHIP_RED_LIGHT, CasinoPalette.INK);
	}
}
