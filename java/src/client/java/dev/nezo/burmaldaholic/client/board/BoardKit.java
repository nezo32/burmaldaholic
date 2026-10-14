package dev.nezo.burmaldaholic.client.board;

import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Registration of the board kit (called once from {@code CoreClientModule}): the per-world caches of the kit
 * ({@link PlateFrame}, {@link BoardStats}) are dropped on disconnect, and the interim {@link SeatsBoardRenderer} is
 * registered for any table type that has no board renderer of its own yet. Every v2 table now registers its own
 * renderer (blackjack and baccarat by lane A), so the interim list is empty; the core client module initialises BEFORE
 * the game modules, so a game's own registration would override an interim one anyway.
 */
public final class BoardKit {
	/** Tables without a board renderer of their own yet, and their felt (none left). */
	private static List<Interim> interim() {
		return List.of();
	}

	private record Interim(TableType<?> type, Felt felt) {}

	private BoardKit() {}

	@SuppressWarnings({"deprecation", "unchecked"}) // the only public 26.x registration path
	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PlateFrame.clear();
			BoardStats.clear();
		});
		for (Interim i : interim()) {
			if (i.type() == null) continue;
			BlockEntityType<CasinoTableBlockEntity> type = (BlockEntityType<CasinoTableBlockEntity>) (BlockEntityType<?>) i.type().blockEntityType();
			BlockEntityRendererRegistry.register(type, ctx -> new SeatsBoardRenderer(ctx, i.felt()));
		}
	}
}
