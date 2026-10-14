package dev.nezo.burmaldaholic.bots.client;

import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;

/**
 * The vanilla text-display renderer, except for bot nameplates seated at a table that has an in-world board: those are
 * hidden, because the board's seat plates show the bot already (docs/architecture/ui-v2.md §7.4, REDESIGN §9.2: "bots
 * keep their tag only away from tables"). A plate is recognised by its text (the {@code bots.display_level} key the
 * server's {@code BotAvatars} writes) and its place (1–3 blocks above a table block within 2.5 blocks). Results are
 * cached per entity for 10 ticks.
 */
public final class BoardAwareTextDisplayRenderer extends DisplayRenderer.TextDisplayRenderer {
	private static final String KEY = "gui.burmaldaholic.bots.display_level";
	private static final Map<Display.TextDisplay, long[]> CACHE = new WeakHashMap<>();

	public BoardAwareTextDisplayRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	/** Same signature on 26.2 and 26.3 (shouldRender's is not): a hidden plate keeps no text sub-state, so nothing draws. */
	@Override
	public void extractRenderState(Display.TextDisplay entity, TextDisplayEntityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (hiddenAtBoard(entity)) state.textRenderState = null;
	}

	private static boolean hiddenAtBoard(Display.TextDisplay entity) {
		Level level = entity.level();
		long now = level.getGameTime();
		long[] c = CACHE.get(entity);
		if (c != null && now - c[0] < 10 && now >= c[0]) return c[1] != 0;
		boolean hide = isBotPlate(entity.getText()) && nearBoard(level, entity.blockPosition());
		CACHE.put(entity, new long[] {now, hide ? 1 : 0});
		return hide;
	}

	static boolean isBotPlate(Component text) {
		if (text.getContents() instanceof TranslatableContents t && KEY.equals(t.getKey())) return true;
		for (Component sibling : text.getSiblings()) if (isBotPlate(sibling)) return true;
		if (text.getContents() instanceof TranslatableContents t) {
			for (Object arg : t.getArgs()) if (arg instanceof Component a && isBotPlate(a)) return true;
		}
		return false;
	}

	private static boolean nearBoard(Level level, BlockPos at) {
		var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
		for (int dy = -3; dy <= -1; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (level.getBlockEntity(at.offset(dx, dy, dz)) instanceof CasinoTableBlockEntity be && be.clientSeats().length > 0
						&& dispatcher.getRenderer(be) instanceof BoardRenderer<?, ?>) return true;
				}
			}
		}
		return false;
	}
}
