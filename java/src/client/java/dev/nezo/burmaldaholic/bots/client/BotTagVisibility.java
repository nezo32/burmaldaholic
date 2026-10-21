package dev.nezo.burmaldaholic.bots.client;

import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;

/**
 * Which {@code [BOT]} nameplates to hide (docs/architecture/ui-v2.md §7.4, REDESIGN §9.2: "bots keep their tag only away
 * from tables"). Called by {@code TextDisplayRendererMixin} at the tail of the vanilla text-display render-state
 * extraction; every other text display is untouched. A tag is hidden only when ALL hold:
 * <ul>
 *   <li>its text is a bot plate (the {@code bots.display_level} key the server's {@code BotAvatars} writes);</li>
 *   <li>it floats 1–3 blocks above a table (main block within 3 blocks) whose renderer is an in-world board with a seat
 *       header.</li>
 * </ul>
 * At any distance: the board's plates are the one name system near tables. Cached per entity for 10 ticks (weak keys;
 * cleared on disconnect).
 */
public final class BotTagVisibility {
	private static final String KEY = "gui.burmaldaholic.bots.display_level";
	private static final int CACHE_TICKS = 10;
	private static final Map<Display.TextDisplay, long[]> CACHE = new WeakHashMap<>();

	private BotTagVisibility() {}

	/**
	 * True when this text display's text must not be drawn this frame: a bot tag over a table whose board draws seat
	 * plates — at ANY distance (visual QA round 1: a neighbouring table's [BOT] tags beside the near table's plates
	 * mixed two name systems on one screen; past 8 blocks the board shows no names at all).
	 */
	public static boolean hidden(Display.TextDisplay entity) {
		return botPlateAtBoard(entity);
	}

	private static boolean botPlateAtBoard(Display.TextDisplay entity) {
		Level level = entity.level();
		long now = level.getGameTime();
		long[] c = CACHE.get(entity);
		if (c != null && now - c[0] < CACHE_TICKS && now >= c[0]) return c[1] != 0;
		boolean hide = isBotPlate(entity.getText()) && nearBoard(level, entity.blockPosition());
		if (c == null) CACHE.put(entity, new long[] {now, hide ? 1 : 0});
		else {
			c[0] = now;
			c[1] = hide ? 1 : 0;
		}
		return hide;
	}

	/** Forgets every cached answer (disconnect / world change). */
	public static void clear() {
		CACHE.clear();
	}

	public static boolean isBotPlate(Component text) {
		if (text.getContents() instanceof TranslatableContents t) {
			if (KEY.equals(t.getKey())) return true;
			for (Object arg : t.getArgs()) if (arg instanceof Component a && isBotPlate(a)) return true;
		}
		for (Component sibling : text.getSiblings()) if (isBotPlate(sibling)) return true;
		return false;
	}

	private static boolean nearBoard(Level level, BlockPos at) {
		var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
		// a 3×2 table's seat points reach ≈ 2.4 blocks from its main block (MT seatPoint, 0.35 past the rail)
		for (int dy = -3; dy <= -1; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (level.getBlockEntity(at.offset(dx, dy, dz)) instanceof CasinoTableBlockEntity be && be.clientSeats().length > 0
						&& dispatcher.getRenderer(be) instanceof BoardRenderer<?, ?>) return true;
				}
			}
		}
		return false;
	}
}
