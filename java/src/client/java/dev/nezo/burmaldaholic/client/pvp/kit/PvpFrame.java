package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.TextFit;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The table UI v2 frame of the PvP screens (REDESIGN.md §7.8, ui-v2.md lane E): the PvP arena fill over the whole GUI
 * (red-lit in a grudge match), the kit's TopBar band (the gold title pill, the step / timer on the right, the PvP pill,
 * the balance pill) and the Dock band at the bottom, where the screen's own buttons (Ready / Drop! / Rematch, Taunt,
 * Close…) sit in one row at {@link Layout#dockButtonY}. The screen's 400-wide content panel is centred between the two
 * bands ({@link Layout#panelLeft}, {@link Layout#panelTop}: the old panel's y 28 lands under the TopBar), so every mode
 * keeps its art and its widgets while the frame, the buttons and the numbers become the same as at every table.
 */
public final class PvpFrame {
	private PvpFrame() {}

	/** Old panel row of the content's first line (the old title line ends at 18). */
	public static final int CONTENT_TOP = 18;
	/** Old panel height of the content (18 … 208). */
	public static final int CONTENT_H = 190;

	/**
	 * @param grid        the kit grid of the GUI (TopBar, PlayArea, Dock)
	 * @param panelLeft   x of the 400-wide panel (centred)
	 * @param panelTop    y of the panel so its content (y 28 … 200) is centred in the PlayArea
	 * @param dockButtonY y of a 20-high button row centred in the Dock
	 */
	public record Layout(TableGrid grid, int panelLeft, int panelTop, int dockButtonY) {
		public Box top() {
			return grid.topBar;
		}

		public Box dock() {
			return grid.dock;
		}

		public Box play() {
			return grid.play;
		}

		/** Converts an old panel-local bottom-row y (≈ 200 … 215) to the Dock row. */
		public int dockY() {
			return dockButtonY;
		}
	}

	public static Layout layout(int w, int h) {
		TableGrid g = TableGrid.of(w, h);
		int panelLeft = (w - Scene.W) / 2;
		int panelTop = g.play.y() - CONTENT_TOP + Math.max(0, (g.play.h() - CONTENT_H) / 2);
		int dockY = g.dock.y() + (g.dock.h() - 20) / 2;
		return new Layout(g, panelLeft, panelTop, dockY);
	}

	/** The arena fill over the whole GUI (the bands cover its top and bottom). */
	public static void background(GuiGraphicsExtractor g, Layout l, boolean grudge) {
		Box all = l.grid.screen;
		g.enableScissor(all.x(), all.y(), all.right(), all.bottom());
		for (int y = 0; y < all.h(); y += 64) {
			for (int x = 0; x < all.w(); x += 64) Kit.region(g, Kit.sheet("extras/fill_arena"), 64, 64, 0, 0, 64, 64, x, y);
		}
		g.disableScissor();
		if (grudge) g.fill(0, 0, all.w(), all.h(), 0x38B0242E);
		Box p = l.play();
		g.fillGradient(p.x(), p.y(), p.right(), p.y() + 10, 0x60000000, 0x00000000);
		g.fillGradient(p.x(), p.bottom() - 10, p.right(), p.bottom(), 0x00000000, 0x60000000);
	}

	/**
	 * The TopBar (title pill left, {@code right} muted-gold right of it, the PvP pill and the balance pill at the right
	 * edge) and the Dock band. Draw after the content and before the widgets.
	 */
	public static void bars(GuiGraphicsExtractor g, Font font, Layout l, Component title, @Nullable Component right, long balance, boolean grudge) {
		Box top = l.top();
		Box dock = l.dock();
		if (!V2Draw.sprite(g, V2Sprites.TOPBAR, top, 0xFFFFFFFF)) {
			V2Draw.fill(g, top, CasinoPalette.V2_INK);
			g.fill(top.x(), top.bottom() - 2, top.right(), top.bottom() - 1, CasinoPalette.V2_GOLD_SHADE);
			g.fill(top.x(), top.bottom() - 1, top.right(), top.bottom(), CasinoPalette.V2_RAIL_DARK);
		}
		if (!V2Draw.sprite(g, V2Sprites.DOCK, dock, 0xFFFFFFFF)) {
			V2Draw.fill(g, dock, CasinoPalette.V2_INK);
			g.fill(dock.x(), dock.y(), dock.right(), dock.y() + 1, CasinoPalette.V2_RAIL_DARK);
			g.fill(dock.x(), dock.y() + 1, dock.right(), dock.y() + 2, CasinoPalette.V2_GOLD_SHADE);
		}
		int m = l.grid.tier.margin;
		int ph = Math.min(top.h() - 6, 16);
		int py = top.y() + (top.h() - ph) / 2 - 1;
		// right: balance pill, then the PvP pill
		Component bal = Texts.number(Math.max(0, balance));
		int bw = font.width(bal) + 24;
		Box balBox = new Box(top.right() - m - bw, py, bw, ph);
		if (!V2Draw.sprite(g, V2Sprites.BALANCE, balBox, 0xFFFFFFFF)) V2Draw.pill(g, balBox, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD_SHADE);
		if (!V2Draw.sprite(g, V2Sprites.CHIP_ICON, new Box(balBox.x() + 4, balBox.y() + (ph - 10) / 2, 10, 10), 0xFFFFFFFF)) {
			Kit.sprite(g, Kit.core("hud/chip_icon"), balBox.x() + 4, balBox.y() + (ph - 10) / 2, 10, 10);
		}
		V2Draw.left(g, font, bal, new Box(balBox.x() + 16, balBox.y(), bw - 18, ph), 0, 1, CasinoPalette.V2_GOLD, true);
		Component pvp = Component.translatable(grudge ? "gui.burmaldaholic.pvp.v2.pill_grudge" : "gui.burmaldaholic.pvp.v2.pill");
		int vw = font.width(pvp) + 10;
		Box pvpBox = new Box(balBox.x() - 4 - vw, py, vw, ph);
		V2Draw.pill(g, pvpBox, grudge ? CasinoPalette.V2_LOSE_FILL : CasinoPalette.V2_PANEL_HI, grudge ? CasinoPalette.V2_LOSE : CasinoPalette.V2_EDGE);
		V2Draw.centred(g, font, pvp, pvpBox, 1, CasinoPalette.V2_TEXT, true);
		// left: the title pill, the step / timer after it
		int room = pvpBox.x() - m - 8;
		int rw = right == null ? 0 : Math.min(font.width(right), room / 2);
		int tw = Math.min(font.width(title) + 12, room - (rw > 0 ? rw + 8 : 0));
		Box titleBox = new Box(top.x() + m, py, Math.max(0, tw), ph);
		if (!V2Draw.sprite(g, V2Sprites.TITLE_PILL, titleBox, 0xFFFFFFFF)) V2Draw.pill(g, titleBox, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD_SHADE);
		V2Draw.centred(g, font, title, titleBox, 1, CasinoPalette.V2_GOLD, true);
		if (right != null && rw > 0) {
			V2Draw.left(g, font, right, new Box(titleBox.right() + 8, py, rw, ph), 0, 1, CasinoPalette.V2_MUTED, true);
		}
	}

	/** The width of a row of buttons laid out by {@link #row}. */
	public static int rowWidth(int[] widths, int gap) {
		int t = 0;
		for (int w : widths) t += w + gap;
		return widths.length == 0 ? 0 : t - gap;
	}

	/** x positions of a button row right-aligned in the Dock (the primary last, at the right edge, like every table). */
	public static int[] row(Layout l, int[] widths, int gap) {
		int[] xs = new int[widths.length];
		int x = l.grid.dock.right() - l.grid.tier.margin - rowWidth(widths, gap);
		for (int i = 0; i < widths.length; i++) {
			xs[i] = x;
			x += widths[i] + gap;
		}
		return xs;
	}

	/** Status text left in the Dock (ellipsised to {@code maxW}). */
	public static void status(GuiGraphicsExtractor g, Font font, Layout l, @Nullable Component line1, @Nullable Component line2, int maxRight) {
		Box d = l.dock();
		int x = d.x() + l.grid.tier.margin;
		int w = Math.max(0, maxRight - 6 - x);
		if (line1 != null) V2Draw.left(g, font, line1, new Box(x, d.y() + d.h() / 2 - 11, w, 10), 0, 1, CasinoPalette.V2_GOLD, true);
		if (line2 != null) V2Draw.left(g, font, line2, new Box(x, d.y() + d.h() / 2 + 1, w, 10), 0, 1, CasinoPalette.V2_MUTED, true);
	}

	/** Shortens {@code text} to {@code w} px (names in plates). */
	public static String fit(Font font, String text, int w) {
		return TextFit.fit(text, w, font::width);
	}

	public static Tier tier(Layout l) {
		return l.grid.tier;
	}
}
