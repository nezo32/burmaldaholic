package dev.nezo.burmaldaholic.games.slots.client.reels;

import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Symbol art of one machine (slots.md §3.2, §9.1; JS1). Primary: the generated sheet
 * {@code textures/gui/slots/<m>_symbols.png} (40 × 40 frames drawn 1:1 in 44 px cells; columns 0 base, 1 blur,
 * 2–9 win, 10–15 idle; row = symbol) and the 32 × 32 compact sheet. Until the art module (SX1, lane B-L10) produces
 * those files, a PLACEHOLDER is drawn: a tinted gem tile plus the vanilla item (no text), with a code-made blur.
 */
public final class SymbolSheet {
	public static final int COL_BASE = 0;
	public static final int COL_BLUR = 1;
	public static final int COL_WIN = 2;
	public static final int COL_IDLE = 10;
	private static final int COLS = 16;
	private static final int ROWS = 11;

	private static final Map<Identifier, Boolean> PRESENT = new HashMap<>();
	private static final Map<Machine, ItemStack[]> ITEMS = new EnumMap<>(Machine.class);

	private SymbolSheet() {}

	public static Identifier sheet(Machine m, boolean compact) {
		return Identifier.fromNamespaceAndPath("burmaldaholic", "textures/gui/slots/" + m.id + (compact ? "_symbols_32.png" : "_symbols.png"));
	}

	/** Whether the generated sheet exists (checked once per resource reload). */
	public static boolean hasSheet(Machine m, boolean compact) {
		Identifier id = sheet(m, compact);
		return PRESENT.computeIfAbsent(id, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
	}

	public static void invalidate() {
		PRESENT.clear();
	}

	/** Vanilla item standing in for a symbol (placeholder art). */
	public static ItemStack item(Machine m, int symbol) {
		ItemStack[] items = ITEMS.computeIfAbsent(m, SymbolSheet::items);
		return items[Math.floorMod(symbol, items.length)];
	}

	private static ItemStack[] items(Machine m) {
		Item[] it = switch (m) {
			case OVERWORLD -> new Item[] {Items.TOTEM_OF_UNDYING, Items.COMPASS, Items.CHEST, Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT,
				Items.APPLE, Items.CARROT, Items.WHEAT, Items.SWEET_BERRIES};
			case NETHER -> new Item[] {Items.LAVA_BUCKET, Items.GHAST_TEAR, Items.GOLD_NUGGET, Items.WITHER_SKELETON_SKULL, Items.BLAZE_ROD, Items.MAGMA_CREAM,
				Items.QUARTZ, Items.NETHER_WART, Items.CRIMSON_FUNGUS, Items.WARPED_FUNGUS, Items.GLOWSTONE_DUST};
			case END -> new Item[] {Items.DRAGON_EGG, Items.ENDER_EYE, Items.END_CRYSTAL, Items.DRAGON_HEAD, Items.ELYTRA, Items.SHULKER_SHELL, Items.CHORUS_FRUIT,
				Items.ENDER_PEARL, Items.PURPUR_BLOCK, Items.END_ROD, Items.END_STONE};
		};
		ItemStack[] out = new ItemStack[it.length];
		for (int i = 0; i < it.length; i++) out[i] = new ItemStack(it[i]);
		return out;
	}

	/**
	 * Draws a symbol centred in the cell at (x, y).
	 *
	 * @param col      sheet column (base / blur / win frame / idle frame)
	 * @param cell     cell size (44 or 32)
	 * @param scaleX   horizontal scale (squash / pop)
	 * @param scaleY   vertical scale
	 * @param alpha    0..1
	 */
	public static void draw(GuiGraphicsExtractor g, Machine m, int symbol, int col, int x, int y, int cell, double scaleX, double scaleY, double alpha) {
		boolean compact = cell < 40;
		int size = compact ? 32 : 40;
		float cx = x + cell / 2f;
		float cy = y + cell / 2f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale((float) scaleX, (float) scaleY);
		if (hasSheet(m, compact)) {
			if (alpha >= 0.35) {
				SlotSprites.sheet(g, sheet(m, compact), COLS * size, ROWS * size, col * size, Math.floorMod(symbol, ROWS) * size, size, size, -size / 2, -size / 2,
					size, size, 0xFFFFFFFF);
				// no tinted blits across versions: fade by darkening instead
				if (alpha < 1) g.fill(-size / 2, -size / 2, size / 2, size / 2, SlotDraw.withAlpha(0xFF000000, (1 - alpha) * 0.8));
			}
		} else {
			placeholder(g, m, symbol, col, size, alpha);
		}
		g.pose().popMatrix();
	}

	private static void placeholder(GuiGraphicsExtractor g, Machine m, int symbol, int col, int size, double alpha) {
		int tint = SymbolStyle.pathColor(m, symbol);
		int half = size / 2;
		boolean special = symbol <= 2;
		boolean win = col >= COL_WIN && col < COL_IDLE;
		// gem tile behind the item: special symbols get a stronger, framed tile
		int tile = SlotDraw.withAlpha(tint, (special ? 0.34 : 0.18) * alpha);
		g.fill(-half + 3, -half + 2, half - 3, half - 2, tile);
		g.fill(-half + 2, -half + 3, half - 2, half - 3, tile);
		if (special) SlotDraw.frame(g, -half + 2, -half + 2, size - 4, size - 4, 1, SlotDraw.withAlpha(tint, 0.8 * alpha));
		if (win) {
			double glow = 0.35 + 0.25 * Math.sin((col - COL_WIN) * Math.PI / 4);
			g.fill(-half + 4, -half + 4, half - 4, half - 4, SlotDraw.withAlpha(0xFFFFFFFF, glow * 0.35 * alpha));
		}
		if (col == COL_BLUR) {
			// motion blur: streaks in the symbol colour instead of the crisp item
			for (int i = -2; i <= 2; i++) {
				int w = 3 + (2 - Math.abs(i)) * 3;
				g.fill(-w + i * 2, -half + 3, w + i * 2, half - 3, SlotDraw.withAlpha(tint, 0.16 * alpha));
			}
			g.fill(-3, -half + 1, 3, half - 1, SlotDraw.withAlpha(SymbolStyle.flowColor(tint), 0.45 * alpha));
			return;
		}
		if (alpha < 0.35) return; // items cannot fade: hide them when nearly transparent
		float s = (size - 8) / 16f;
		g.pose().pushMatrix();
		g.pose().scale(s, s);
		g.item(item(m, symbol), -8, -8);
		g.pose().popMatrix();
		if (col >= COL_IDLE) {
			// idle flourish placeholder: a shine band sweeping diagonally
			int f = col - COL_IDLE;
			int bx = -half + f * size / 6;
			SlotDraw.line(g, bx, half - 4, bx + 10, -half + 4, 2, SlotDraw.withAlpha(0xFFFFFFFF, 0.55 * alpha));
		}
		if (alpha < 1) g.fill(-half, -half, half, half, SlotDraw.withAlpha(0xFF000000, (1 - alpha) * 0.8));
	}
}
