package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The felt of the PlayArea, full bleed (REDESIGN §3.2 "TableFrame"): a tiled felt sprite per theme ({@code core/v2/felt_<θ>})
 * or the flat felt colour, a speckle and the edge vignette. Themes swap only felt / rail / print colours.
 */
public final class V2Felt {
	private V2Felt() {}

	public static int felt(CasinoTheme t) {
		return switch (t) {
			case BASTION -> 0xFF6A1E22;
			case END -> 0xFF2E2050;
			default -> CasinoPalette.V2_FELT;
		};
	}

	public static int rail(CasinoTheme t) {
		return switch (t) {
			case BASTION -> 0xFF3A2A2A;
			case END -> 0xFF2A2440;
			default -> CasinoPalette.V2_RAIL;
		};
	}

	public static int print(CasinoTheme t) {
		return switch (t) {
			case END -> 0xFFE0D0FF;
			default -> CasinoPalette.V2_FELT_PRINT;
		};
	}

	public static Identifier sprite(CasinoTheme t) {
		return V2Sprites.v2("felt_" + (t == CasinoTheme.BASTION || t == CasinoTheme.END ? t.name().toLowerCase(java.util.Locale.ROOT) : "village"));
	}

	public static void draw(GuiGraphicsExtractor g, Box b, CasinoTheme theme) {
		if (b.isEmpty()) return;
		if (!V2Draw.sprite(g, sprite(theme), b, 0xFFFFFFFF)) {
			int base = felt(theme);
			V2Draw.fill(g, b, base);
			int speck = V2Draw.darker(base, 0.86f);
			int light = V2Draw.alpha(0xFFFFFFFF, 0.05f);
			for (int y = b.y(); y < b.bottom(); y += 3) {
				for (int x = b.x() + ((y * 7) % 5); x < b.right(); x += 5) {
					int h = (x * 73856093) ^ (y * 19349663);
					if ((h & 7) == 0) g.fill(x, y, x + 1, y + 1, speck);
					else if ((h & 31) == 3) g.fill(x, y, x + 1, y + 1, light);
				}
			}
		}
		vignette(g, b);
	}

	/** The edge darkening (8 px steps of ink at falling alpha). */
	public static void vignette(GuiGraphicsExtractor g, Box b) {
		if (V2Draw.sprite(g, V2Sprites.FELT_VIGNETTE, b, 0xFFFFFFFF)) return;
		int steps = 6;
		for (int i = 0; i < steps; i++) {
			int c = CasinoPalette.withAlpha(CasinoPalette.V2_INK, 0.16f * (1 - i / (float) steps));
			int d = i * 2;
			g.fill(b.x() + d, b.y() + d, b.right() - d, b.y() + d + 2, c);
			g.fill(b.x() + d, b.bottom() - d - 2, b.right() - d, b.bottom() - d, c);
			g.fill(b.x() + d, b.y() + d + 2, b.x() + d + 2, b.bottom() - d - 2, c);
			g.fill(b.right() - d - 2, b.y() + d + 2, b.right() - d, b.bottom() - d - 2, c);
		}
	}
}
