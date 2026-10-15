package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The kit's button look (primary gold, normal purple, danger red, icon square), label + key hint plate, by box. Not a
 * vanilla widget: the chrome hit-tests by {@link Box} (docs/architecture/ui-v2.md §4). Disabled = dim (the reason is in
 * the status line). Pressed = 1 px down.
 */
public final class V2Button {
	/** Paddings: label sides, gap label → key plate, key plate sides. */
	public static final int PAD = 8, HINT_GAP = 5, HINT_PAD = 3;

	private V2Button() {}

	/** The display name of a {@link dev.nezo.burmaldaholic.core.ui.v2.KeyMap} key ("Space" / "Пробел", "H"), or null for none. */
	public static @Nullable Component keyName(@Nullable String key) {
		String k = dev.nezo.burmaldaholic.core.ui.v2.KeyMap.vanillaNameKey(key);
		if (k != null) return Component.translatable(k);
		String c = V2Input.keyCap(key); // the player's layout (AZERTY / QWERTZ), else the US name
		return c == null ? null : dev.nezo.burmaldaholic.core.text.Texts.raw(c); // a key cap letter: language-neutral
	}

	/** Width a button needs for {@code label} and the optional key hint. */
	public static int width(Font font, Component label, @Nullable Component hint) {
		int w = font.width(label) + 2 * PAD;
		if (hint != null) w += HINT_GAP + font.width(hint) + 2 * HINT_PAD;
		return w;
	}

	public static void draw(GuiGraphicsExtractor g, Font font, Box box, Component label, @Nullable Component hint, V2Sprites.ButtonStyle style,
			boolean enabled, boolean hover, boolean pressed, boolean focused) {
		if (box.isEmpty()) return;
		Box b = pressed && enabled ? box.offset(0, 1) : box;
		if (!V2Draw.sprite(g, style.sprite(enabled, hover, pressed), b, 0xFFFFFFFF)) flat(g, b, style, enabled, hover);
		int text = textColor(style, enabled);
		int hintW = hint == null ? 0 : font.width(hint) + 2 * HINT_PAD;
		boolean showHint = hint != null && font.width(label) + HINT_GAP + hintW + 2 * 4 <= b.w();
		int contentW = font.width(label) + (showHint ? HINT_GAP + hintW : 0);
		int maxLabel = b.w() - 8 - (showHint ? HINT_GAP + hintW : 0);
		contentW = Math.min(contentW, b.w() - 8);
		int x = b.x() + (b.w() - contentW) / 2;
		int ty = b.y() + (b.h() - 8) / 2 - 1;
		Box labelBox = new Box(x, b.y(), Math.max(0, Math.min(font.width(label), maxLabel)), b.h() - 2);
		V2Draw.left(g, font, label, labelBox, 0, 1, text, style != V2Sprites.ButtonStyle.PRIMARY || !enabled);
		if (showHint) {
			Box hb = new Box(labelBox.right() + HINT_GAP, ty - 2, hintW, 12);
			keyHint(g, font, hb, hint, style, enabled);
		}
		if (focused) V2Draw.outline(g, box.inset(-1), CasinoPalette.V2_GOLD);
	}

	/** Ink of the key plate on the gold primary (review DES 2: dark plate, gold text; the mockup's #3a2a0a). */
	public static final int PRIMARY_PLATE = 0xFF3A2A0A;

	/**
	 * The key hint plate. On the gold primary a dark ink plate with gold text (yellow on dark-gold had no contrast, DES 2),
	 * dimmed with the button when disabled; elsewhere the kit's plate sprite or its flat fallback.
	 */
	public static void keyHint(GuiGraphicsExtractor g, Font font, Box hb, Component hint, V2Sprites.ButtonStyle style, boolean enabled) {
		if (style == V2Sprites.ButtonStyle.PRIMARY) {
			V2Draw.fill(g, hb, enabled ? PRIMARY_PLATE : V2Draw.alpha(PRIMARY_PLATE, 0.45f));
			V2Draw.outline(g, hb, enabled ? 0xFF1E1404 : V2Draw.alpha(0xFF1E1404, 0.45f));
			V2Draw.centred(g, font, hint, hb, 1, enabled ? CasinoPalette.V2_GOLD : 0xFF9AA6BC, false);
			return;
		}
		if (!V2Draw.sprite(g, V2Sprites.KEY_HINT, hb, enabled ? 0xFFFFFFFF : 0x80FFFFFF)) {
			int fill = V2Draw.darker(base(style), 0.55f);
			V2Draw.fill(g, hb, enabled ? fill : V2Draw.alpha(fill, 0.5f));
			V2Draw.outline(g, hb, CasinoPalette.V2_INK);
		}
		V2Draw.centred(g, font, hint, hb, 1, enabled ? CasinoPalette.V2_TEXT : CasinoPalette.V2_MUTED, true);
	}

	static int base(V2Sprites.ButtonStyle s) {
		return switch (s) {
			case PRIMARY -> CasinoPalette.V2_GOLD;
			case DANGER -> CasinoPalette.V2_DANGER;
			default -> CasinoPalette.V2_BTN;
		};
	}

	static int textColor(V2Sprites.ButtonStyle s, boolean enabled) {
		if (!enabled) return s == V2Sprites.ButtonStyle.PRIMARY ? 0xFF9AA6BC : 0xFF8A7A9C; // primary: the one slate disabled style (DES roulette P2)
		return s == V2Sprites.ButtonStyle.PRIMARY ? CasinoPalette.V2_INK : CasinoPalette.V2_TEXT;
	}

	/** The flat fallback: base fill, 1 px ink rim, light top row, 2 px shade at the bottom. */
	static void flat(GuiGraphicsExtractor g, Box b, V2Sprites.ButtonStyle s, boolean enabled, boolean hover) {
		int base = base(s);
		if (hover && enabled) base = s == V2Sprites.ButtonStyle.PRIMARY ? 0xFFFFE070 : s == V2Sprites.ButtonStyle.DANGER ? CasinoPalette.V2_DANGER_HI
			: CasinoPalette.V2_BTN_HI;
		if (!enabled) base = s == V2Sprites.ButtonStyle.PRIMARY ? 0xFF3A4256 : 0xFF2C2038;
		V2Draw.fill(g, b, CasinoPalette.V2_INK);
		Box in = b.inset(1);
		V2Draw.fill(g, in, base);
		g.fill(in.x(), in.y(), in.right(), in.y() + 1, enabled ? V2Draw.alpha(0xFFFFFFFF, 0.3f) : 0x10FFFFFF);
		g.fill(in.x(), in.bottom() - 2, in.right(), in.bottom(), V2Draw.darker(base, 0.65f));
	}
}
