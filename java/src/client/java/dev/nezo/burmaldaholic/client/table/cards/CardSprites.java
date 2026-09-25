package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.anim.cards.CardLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Card art of the kit (docs/design/visual/cards.md §3): face atlases L / M / S (13 ranks × 4 suits, rows ♠♥♦♣, columns
 * A 2 … 10 J Q K), the four-colour (default) and classic two-colour decks, the back atlas (six designs × L | M | S | W),
 * the runtime rank index in the {@code burmaldaholic:core/card_index} font in the suit colour (top-left; rotated 180° in
 * the bottom-right on L), and the shadow / glow / curl sprites. Card codes are the modules' shared packing
 * {@code suit × 13 + rank − 1} (0..51); {@code -1} is a face-down card.
 */
public final class CardSprites {
	public static final int L = 0, M = 1, S = 2;
	/** Back rows (§3.7). */
	public static final int BACK_NAVY = 0, BACK_BURGUNDY = 1, BACK_CRIMSON = 2, BACK_EMERALD = 3, BACK_BASTION = 4, BACK_END = 5;
	private static final int[] BACK_COL = {0, 37, 58};
	private static final int BACKS_W = 87, BACKS_H = 294;
	private static final String[] SIZE_ID = {"l", "m", "s"};
	/** Suit colours ♠♥♦♣: four-colour deck and the classic deck (§3.5). */
	private static final int[] FOUR = {0xFF231C38, 0xFFD42A3A, 0xFF2A5ED8, 0xFF0F7E66};
	private static final int[] CLASSIC = {0xFF231C38, 0xFFD42A3A, 0xFFD42A3A, 0xFF231C38};
	public static final FontDescription INDEX_FONT = new FontDescription.Resource(Burmaldaholic.id("core/card_index"));
	private static final Identifier BACKS = Burmaldaholic.id("textures/gui/core/cards/backs.png");
	private static final Identifier[][] FACES = new Identifier[2][3];
	private static final Component[] RANK = new Component[13];

	/** The classic two-colour deck (opt-in; the four-colour deck is the default). */
	public static boolean classicDeck;

	static {
		for (int s = 0; s < 3; s++) {
			FACES[0][s] = Burmaldaholic.id("textures/gui/core/cards/faces_" + SIZE_ID[s] + ".png");
			FACES[1][s] = Burmaldaholic.id("textures/gui/core/cards/faces_" + SIZE_ID[s] + "_classic.png");
		}
	}

	private CardSprites() {}

	public static int w(int size) {
		return CardLayout.cardW(size);
	}

	public static int h(int size) {
		return CardLayout.cardH(size);
	}

	public static int rank(int code) {
		return code % 13 + 1;
	}

	public static int suit(int code) {
		return code / 13;
	}

	public static int suitColor(int code) {
		return (classicDeck ? CLASSIC : FOUR)[Math.floorMod(suit(code), 4)];
	}

	/** Runtime rank index: A / J / Q / K / 10 from the lang file (RU Т / В / Д / К), 2–9 digits, in the index font. */
	public static Component rankText(int rank) {
		int i = Math.max(1, Math.min(13, rank)) - 1;
		Component c = RANK[i];
		if (c == null) {
			MutableComponent m = switch (rank) {
				case 1 -> Component.translatable("gui.burmaldaholic.card.rank.a");
				case 11 -> Component.translatable("gui.burmaldaholic.card.rank.j");
				case 12 -> Component.translatable("gui.burmaldaholic.card.rank.q");
				case 13 -> Component.translatable("gui.burmaldaholic.card.rank.k");
				case 10 -> Component.translatable("gui.burmaldaholic.card.rank.10");
				default -> Component.literal(Integer.toString(rank)); // literal-ok: digit
			};
			c = m.withStyle(s -> s.withFont(INDEX_FONT));
			RANK[i] = c;
		}
		return c;
	}

	/** Forget cached rank components (language change). */
	public static void resetText() {
		java.util.Arrays.fill(RANK, null);
	}

	/**
	 * The full face of {@code code} at (x, y) (index included), multiplied by {@code argb} (dimming / fading).
	 */
	public static void face(GuiGraphicsExtractor g, Font font, int code, int size, int x, int y, int argb) {
		faceRows(g, font, code, size, x, y, 0, h(size), argb);
	}

	/**
	 * Rows [{@code from}, {@code to}) of the face (the squeeze reveal band); the indices are drawn when their rows are
	 * inside the band.
	 */
	public static void faceRows(GuiGraphicsExtractor g, Font font, int code, int size, int x, int y, int from, int to, int argb) {
		int w = w(size);
		int h = h(size);
		int a = Math.max(0, from);
		int b = Math.min(h, to);
		if (b <= a || code < 0 || code > 51) return;
		Identifier atlas = FACES[classicDeck ? 1 : 0][size];
		int col = rank(code) - 1;
		int row = suit(code);
		CardGfx.tex(g, atlas, x, y + a, w, b - a, col * w, row * h + a, w, b - a, 13 * w, 4 * h, argb);
		int color = CardGfx.alpha(suitColor(code), (argb >>> 24) / 255.0);
		Component idx = rankText(rank(code));
		if (a <= 3 && b >= 9) CardGfx.text(g, font, idx, x + 2, y + 2, color, false);
		if (size == L && a <= h - 9 && b >= h - 3) {
			int tw = Math.max(1, font.width(idx) - 1);
			g.pose().pushMatrix();
			g.pose().translate(x + w - 2 - tw / 2f, y + h - 6f);
			g.pose().rotate((float) Math.PI);
			CardGfx.text(g, font, idx, -(tw + 1) / 2, -4, color, false);
			g.pose().popMatrix();
		}
	}

	/** A back of design {@code back} (row), rows [from, to). */
	public static void backRows(GuiGraphicsExtractor g, int back, int size, int x, int y, int from, int to, int argb) {
		int w = w(size);
		int h = h(size);
		int a = Math.max(0, from);
		int b = Math.min(h, to);
		if (b <= a) return;
		CardGfx.tex(g, BACKS, x, y + a, w, b - a, BACK_COL[Math.min(2, size)], back * 49 + a, w, b - a, BACKS_W, BACKS_H, argb);
	}

	public static void back(GuiGraphicsExtractor g, int back, int size, int x, int y, int argb) {
		backRows(g, back, size, x, y, 0, h(size), argb);
	}

	/** Card drop shadow (L / M: the generated soft shadow; S: a flat ink offset). */
	public static void shadow(GuiGraphicsExtractor g, int size, int x, int y, double alpha) {
		if (size == L) CardGfx.sprite(g, FxSprites.sprite("cards/fx/shadow_l"), x - 2, y - 1, 41, 53, CardGfx.white(alpha));
		else if (size == M) CardGfx.sprite(g, FxSprites.sprite("cards/fx/shadow_m"), x - 2, y - 1, 25, 33, CardGfx.white(alpha));
		else g.fill(x + 1, y + 1, x + w(S) + 1, y + h(S) + 1, CardGfx.alpha(0x5A0A0412, alpha));
	}

	/** K9 glow behind a card (animated strip, frametime 3). */
	public static void glow(GuiGraphicsExtractor g, int size, int x, int y, double alpha) {
		if (size == L) CardGfx.sprite(g, FxSprites.sprite("cards/fx/glow_l"), x - 3, y - 3, 43, 55, CardGfx.white(alpha));
		else if (size == M) CardGfx.sprite(g, FxSprites.sprite("cards/fx/glow_m"), x - 3, y - 3, 27, 35, CardGfx.white(alpha));
		else CardGfx.frame(g, x - 1, y - 1, w(S) + 2, h(S) + 2, CardGfx.alpha(0xFFFFD640, alpha));
	}

	/** Squeeze curl at the reveal boundary (L: 37 × 6, M: 21 × 4). */
	public static void curl(GuiGraphicsExtractor g, int size, int x, int y, double scaleY, double alpha) {
		int w = size == L ? 37 : 21;
		int h = size == L ? 6 : 4;
		int hh = Math.max(1, (int) Math.round(h * scaleY));
		CardGfx.sprite(g, FxSprites.sprite(size == L ? "cards/fx/curl_l" : "cards/fx/curl_m"), x, y - hh / 2, w, hh, CardGfx.white(alpha));
	}

	/** Narration of a card: "Queen of spades". */
	public static Component narration(int code) {
		if (code < 0) return Component.translatable("gui.burmaldaholic.cards.hidden_card");
		int r = rank(code);
		Component rank = switch (r) {
			case 1 -> Component.translatable("gui.burmaldaholic.card.name.a");
			case 11 -> Component.translatable("gui.burmaldaholic.card.name.j");
			case 12 -> Component.translatable("gui.burmaldaholic.card.name.q");
			case 13 -> Component.translatable("gui.burmaldaholic.card.name.k");
			default -> Component.literal(Integer.toString(r)); // literal-ok: digit
		};
		String suit = switch (suit(code)) {
			case 0 -> "spades";
			case 1 -> "hearts";
			case 2 -> "diamonds";
			default -> "clubs";
		};
		return Component.translatable("gui.burmaldaholic.cards.narrate.card", rank, Component.translatable("gui.burmaldaholic.card.suit." + suit));
	}
}
