package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A seat's name plate (docs/architecture/ui-v2.md §4.2, REDESIGN §4): avatar 14², one line — the name, ellipsised — and
 * an edge colour: cyan = you, gold = acting, purple = others. No numbers in plates. Empty seats have no plate (§2.3).
 */
public final class SeatPlate {
	public enum Kind {
		YOU,
		ACTING,
		OTHER
	}

	public static final int AVATAR = 14;
	/** Height of the ROOMY two-line plate (name + stack / last action; ui-v2.md §3.7). */
	public static final int ROOMY_H = 26;

	private SeatPlate() {}

	public static int edge(Kind k) {
		return switch (k) {
			case YOU -> CasinoPalette.V2_YOU;
			case ACTING -> CasinoPalette.V2_GOLD;
			case OTHER -> CasinoPalette.V2_EDGE;
		};
	}

	/** Draws the plate; {@code bot} tints the avatar (bots get the creeper green, players a skin tone). */
	public static void draw(GuiGraphicsExtractor g, Font font, Box box, Component name, Kind kind, boolean bot) {
		if (box.isEmpty()) return;
		int edge = edge(kind);
		if (!V2Draw.sprite(g, V2Sprites.plate(kind), box, 0xFFFFFFFF)) {
			V2Draw.fill(g, box, CasinoPalette.V2_INK);
			g.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, CasinoPalette.V2_PANEL);
			g.fill(box.x(), box.y(), box.right(), box.y() + 1, edge);
			g.fill(box.x(), box.bottom() - 2, box.right(), box.bottom(), edge);
		}
		int ay = box.y() + (box.h() - AVATAR) / 2;
		int ax = box.x() + 2;
		int skin = bot ? 0xFF4E9A3C : 0xFFE0A884;
		if (kind == Kind.YOU) skin = 0xFF7A5232;
		g.fill(ax, ay, ax + AVATAR, ay + AVATAR, skin);
		g.fill(ax + 3, ay + 5, ax + 5, ay + 7, CasinoPalette.V2_TEXT);
		g.fill(ax + 9, ay + 5, ax + 11, ay + 7, CasinoPalette.V2_TEXT);
		g.fill(ax + 4, ay + 6, ax + 5, ay + 7, CasinoPalette.V2_INK);
		g.fill(ax + 10, ay + 6, ax + 11, ay + 7, CasinoPalette.V2_INK);
		Box text = new Box(ax + AVATAR + 3, box.y(), Math.max(0, box.right() - (ax + AVATAR + 3) - 3), box.h() - 1);
		V2Draw.left(g, font, name, text, 0, 1, kind == Kind.YOU ? CasinoPalette.V2_YOU : kind == Kind.ACTING ? CasinoPalette.V2_GOLD
			: CasinoPalette.V2_TEXT, true);
	}

	public static void draw(GuiGraphicsExtractor g, Font font, Box box, Component name, Kind kind) {
		draw(g, font, box, name, kind, false);
	}

	/**
	 * ROOMY frames (grid.room(), §3.7): the plate with a second, muted line under the name (stack and last action,
	 * e.g. "250 · Raise"). A box lower than {@link #ROOMY_H} or a null {@code line2} draws the one-line plate.
	 */
	public static void draw(GuiGraphicsExtractor g, Font font, Box box, Component name, @org.jspecify.annotations.Nullable Component line2, Kind kind,
			boolean bot) {
		if (line2 == null || box.h() < ROOMY_H) {
			draw(g, font, box, name, kind, bot);
			return;
		}
		draw(g, font, new Box(box.x(), box.y(), box.w(), box.h()), Component.empty(), kind, bot);
		int tx = box.x() + 2 + AVATAR + 3;
		int tw = Math.max(0, box.right() - tx - 3);
		int top = box.y() + (box.h() - 22) / 2;
		V2Draw.left(g, font, name, new Box(tx, top, tw, 11), 0, 1, kind == Kind.YOU ? CasinoPalette.V2_YOU : kind == Kind.ACTING ? CasinoPalette.V2_GOLD
			: CasinoPalette.V2_TEXT, true);
		V2Draw.left(g, font, line2, new Box(tx, top + 11, tw, 11), 0, 1, CasinoPalette.V2_MUTED, true);
	}
}
