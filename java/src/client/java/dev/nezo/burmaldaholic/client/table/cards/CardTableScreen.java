package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.anim.cards.CardLayout;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Card-table screen frame (docs/design/visual/cards.md §6): the 427 × 240 design canvas drawn at the largest integer
 * scale that fits (centred; the room fills the rest), or the compact 284 × 160 canvas below it; strata of §6.2 —
 * the themed room, the table art, the game's scene ({@link #drawScene}), the console strip, the title plaque, the
 * balance HUD, the console buttons ({@link CardButton}) and overlays. Widgets live in canvas coordinates; mouse input
 * is mapped into the canvas.
 *
 * <p>Thin local adapter for the shared {@code CasinoScreen} kit (lane J-L2): switch the frame to it when it lands; the
 * games only use {@link #drawScene}, {@link #buildConsole}, {@link #addButton} and the canvas helpers.
 */
public abstract class CardTableScreen extends CasinoTableScreen {
	private final List<CardButton> buttons = new ArrayList<>();
	private final TableTheme.Shape shape;
	/** Integer canvas scale (≥ 1), or 1 in the compact layout. */
	protected int k = 1;
	/** Fractional scale for GUIs smaller than the compact canvas (the only non-integer case). */
	protected float fk = 1f;
	protected int ox;
	protected int oy;
	protected boolean compact;
	protected TableTheme theme = TableTheme.VILLAGE;
	private @Nullable Component error;
	private long errorAt;

	protected CardTableScreen(CasinoTableMenu menu, Inventory inventory, Component title, TableTheme.Shape shape) {
		super(menu, inventory, title, CardLayout.CANVAS_W, CardLayout.CANVAS_H);
		this.shape = shape;
		this.inventoryLabelY = -10_000;
		this.titleLabelY = -10_000;
	}

	// ---- canvas ---------------------------------------------------------------------------------------------------

	public int canvasW() {
		return compact ? CardLayout.COMPACT_W : CardLayout.CANVAS_W;
	}

	public int canvasH() {
		return compact ? CardLayout.COMPACT_H : CardLayout.CANVAS_H;
	}

	/** Table art origin on the canvas. */
	public int tableX() {
		return compact ? CardLayout.TABLE_CX : CardLayout.TABLE_X;
	}

	public int tableY() {
		return compact ? CardLayout.TABLE_CY : CardLayout.TABLE_Y;
	}

	public boolean compact() {
		return compact;
	}

	/**
	 * The card-table theme, resolved in {@link #init} from the kit's {@link #theme()} (forced / {@code cards.theme}, the
	 * table state's {@code theme}, then the dimension). Named {@code tableTheme} because {@code theme()} is the shared
	 * {@code CasinoTableScreen} hook (it returns the kit's {@code CasinoTheme}).
	 */
	public TableTheme tableTheme() {
		return theme;
	}

	protected static boolean reduced() {
		return FxSettings.reduceMotion();
	}

	/** Shared clock (ms): level game time + partial tick. */
	protected double nowMs() {
		return AnimClock.levelMs(minecraft == null ? 0 : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Override
	protected void init() {
		super.init();
		theme = TableTheme.of(theme());
		int kk = CardLayout.scale(width, height);
		compact = kk == 0;
		k = Math.max(1, kk);
		fk = compact ? Math.min(1f, Math.min(width / (float) CardLayout.COMPACT_W, height / (float) CardLayout.COMPACT_H)) : k;
		ox = (int) Math.floor((width - canvasW() * fk) / 2f);
		oy = (int) Math.floor((height - canvasH() * fk) / 2f);
		leftPos = 0;
		topPos = 0;
		rebuildConsole();
	}

	/** The entrance scales / veils the drawn canvas (GUI coordinates), not the unscaled 427 × 240 image at (0, 0). */
	@Override
	protected dev.nezo.burmaldaholic.core.ui.UiLayout.Rect entranceRect() {
		return new dev.nezo.burmaldaholic.core.ui.UiLayout.Rect(ox, oy, Math.round(canvasW() * fk), Math.round(canvasH() * fk));
	}

	/** Re-creates the console buttons ({@link #buildConsole}). */
	protected final void rebuildConsole() {
		for (CardButton b : buttons) removeWidget(b);
		buttons.clear();
		if (minecraft == null) return;
		buildConsole();
	}

	/** Adds the console buttons with {@link #addButton} / {@link #layoutButtons}. */
	protected abstract void buildConsole();

	/** Adds a button (canvas coordinates). */
	protected CardButton addButton(CardButton b) {
		buttons.add(b);
		addWidget(b);
		return b;
	}

	protected List<CardButton> buttons() {
		return buttons;
	}

	/**
	 * Builds and places a row of console buttons: right-aligned at y 213 (full) or left-aligned at y 139 (compact), 4 px
	 * gaps, each as wide as icon + label; wraps into a second row above when the row would pass {@code minX}.
	 */
	protected void layoutButtons(List<CardButton> row, int minX) {
		int gap = compact ? 3 : 4;
		if (compact) {
			int x = 4;
			int y = 139;
			for (CardButton b : row) {
				if (x + b.getWidth() > canvasW() - 4 && x > 4) {
					x = 4;
					y -= 22;
				}
				b.setX(x);
				b.setY(y);
				x += b.getWidth() + gap;
				addButton(b);
			}
			return;
		}
		int right = canvasW() - 6;
		int x = right;
		int y = 213;
		for (int i = row.size() - 1; i >= 0; i--) {
			CardButton b = row.get(i);
			if (x - b.getWidth() < minX && x < right) {
				x = right;
				y -= 22;
			}
			x -= b.getWidth();
			b.setX(x);
			b.setY(y);
			x -= gap;
			addButton(b);
		}
	}

	/** A console button sized to its label (+ icon). */
	protected CardButton button(Component label, @Nullable String icon, CardButton.Family family, boolean active, Runnable action) {
		int w = CardButton.naturalWidth(font, label, icon != null);
		CardButton b = new CardButton(0, 0, Math.max(compact ? 44 : 56, w), label, icon, family, theme, x -> action.run());
		b.active = active;
		return b;
	}

	// ---- rendering ------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, width, height, CasinoPalette.BG_DARKEST);
		double cmx = (mouseX - ox) / fk;
		double cmy = (mouseY - oy) / fk;
		g.pose().pushMatrix();
		g.pose().translate(ox, oy);
		if (fk != 1f) g.pose().scale(fk, fk);
		drawRoom(g);
		CardGfx.picture(g, theme.table(shape, compact), tableX(), tableY(), compact ? CardLayout.TABLE_CW : CardLayout.TABLE_W,
			compact ? CardLayout.TABLE_CH : CardLayout.TABLE_H, 0xFFFFFFFF);
		int mx = (int) Math.floor(cmx);
		int my = (int) Math.floor(cmy);
		drawScene(g, mx, my, a);
		drawConsole(g);
		drawPlaque(g);
		drawBalance(g);
		drawConsoleText(g, mx, my);
		for (CardButton b : buttons) b.extractRenderState(g, mx, my, a);
		drawOverlay(g, mx, my, a);
		drawError(g);
		g.pose().popMatrix();
		for (CardButton b : buttons) {
			if (b.visible && b.isMouseOver(cmx, cmy) && b.hint() != null) {
				g.setTooltipForNextFrame(font.split(b.hint(), 220), mouseX, mouseY);
			}
		}
		drawTooltips(g, mx, my, mouseX, mouseY);
	}

	/** The room behind the table: the backdrop picture tiled horizontally, letterboxed with the darkest colour. */
	private void drawRoom(GuiGraphicsExtractor g) {
		if (compact) {
			CardGfx.tex(g, theme.backdrop(), 0, 0, CardLayout.COMPACT_W, CardLayout.COMPACT_H, 72, 40, CardLayout.COMPACT_W, CardLayout.COMPACT_H, 428,
				240, 0xFFFFFFFF);
			return;
		}
		int left = (int) Math.floor(-ox / fk) - 1;
		int right = (int) Math.ceil((width - ox) / fk) + 1;
		for (int x = Math.floorDiv(left, 428) * 428; x < right; x += 428) CardGfx.picture(g, theme.backdrop(), x, 0, 428, 240, 0xFFFFFFFF);
	}

	private void drawConsole(GuiGraphicsExtractor g) {
		int y = compact ? CardLayout.CONSOLE_CY : CardLayout.CONSOLE_Y;
		int h = compact ? CardLayout.CONSOLE_CH : CardLayout.CONSOLE_H;
		int top = y;
		for (CardButton b : buttons) top = Math.min(top, b.getY() - 3);
		CardGfx.sprite(g, theme.themed("panel/console"), 0, top, canvasW() + 1, y + h - top, 0xFFFFFFFF, 0xFF26103C);
	}

	private void drawPlaque(GuiGraphicsExtractor g) {
		Component t = plaqueTitle();
		int w = font.width(t) + (compact ? 20 : 24);
		int x = compact ? 2 : 4;
		int y = compact ? -2 : 1;
		CardGfx.sprite(g, theme.themed("panel/plaque"), x, y, w, 18, 0xFFFFFFFF, 0xFF4A2A1A);
		CardGfx.centered(g, font, t, x + w / 2, y + 5, CasinoPalette.GOLD, true);
	}

	/** Title on the plaque ("Blackjack · 1–100"). */
	protected Component plaqueTitle() {
		return title;
	}

	/** The balance shown in the HUD corner (games may hold it until the reveal gate). */
	protected long shownBalance() {
		return balance();
	}

	private void drawBalance(GuiGraphicsExtractor g) {
		Component t = Texts.number(shownBalance());
		int w = font.width(t) + 20;
		int h = compact ? 12 : 14;
		int x = canvasW() - w - (compact ? 3 : 5);
		int y = compact ? 1 : 3;
		CardGfx.sprite(g, FxSprites.sprite("panel/hud"), x, y, w, h, 0xFFFFFFFF, CasinoPalette.BG_DEEP);
		CardGfx.sprite(g, FxSprites.chip(25), x + 4, y + (h - 8) / 2, 8, 8, 0xFFFFFFFF);
		CardGfx.text(g, font, t, x + 15, y + (h - 8) / 2, CasinoPalette.GOLD, true);
	}

	/** Game scene between the table art and the console (canvas coordinates). */
	protected abstract void drawScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial);

	/** Status lines in the console (left of the buttons). */
	protected void drawConsoleText(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	/** Overlays above the widgets (stamps that must sit over buttons, panels). */
	protected void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

	/** Tooltips in screen coordinates ({@code sx, sy}) for canvas hover ({@code mx, my}). */
	protected void drawTooltips(GuiGraphicsExtractor g, int mx, int my, int sx, int sy) {}

	/** Leftmost x the buttons may use (status lines to their left). */
	protected int consoleTextRight() {
		int x = canvasW();
		for (CardButton b : buttons) if (b.getY() >= (compact ? 130 : 200)) x = Math.min(x, b.getX());
		return x - 6;
	}

	// ---- errors ---------------------------------------------------------------------------------------------------

	@Override
	public void showError(Component message) {
		super.showError(message);
		this.error = message;
		this.errorAt = Util.getMillis();
	}

	private void drawError(GuiGraphicsExtractor g) {
		if (error == null) return;
		long age = Util.getMillis() - errorAt;
		if (age > 3000) {
			error = null;
			return;
		}
		double a = age > 2600 ? (3000 - age) / 400.0 : 1;
		int w = font.width(error) + 12;
		int x = (canvasW() - w) / 2;
		int y = (compact ? CardLayout.CONSOLE_CY : CardLayout.CONSOLE_Y) - 16 - (age < 150 ? (int) (6 * (1 - age / 150.0)) : 0);
		g.fill(x, y, x + w, y + 13, CardGfx.alpha(0xE0180A28, a));
		CardGfx.frame(g, x, y, w, 13, CardGfx.alpha(CasinoPalette.CHIP_RED, a));
		CardGfx.text(g, font, error, x + 6, y + 3, CardGfx.alpha(CasinoPalette.CHIP_RED_LIGHT, a), false);
	}

	// ---- input (screen → canvas) ----------------------------------------------------------------------------------

	private MouseButtonEvent toCanvas(MouseButtonEvent e) {
		return new MouseButtonEvent((e.x() - ox) / fk, (e.y() - oy) / fk, e.buttonInfo());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MouseButtonEvent c = toCanvas(event);
		if (onCanvasClick(c.x(), c.y(), c.button())) return true;
		return super.mouseClicked(c, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(toCanvas(event));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return super.mouseDragged(toCanvas(event), dx / fk, dy / fk);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		return super.mouseScrolled((x - ox) / fk, (y - oy) / fk, dx, dy);
	}

	/** Clicks on the scene (chip rack, bet spots) in canvas coordinates; true when handled. */
	protected boolean onCanvasClick(double x, double y, int button) {
		return false;
	}
}
