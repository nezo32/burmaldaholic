package dev.nezo.burmaldaholic.client.board;

import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Registration of the board kit (called once from {@code CoreClientModule}). Registers the interim
 * {@link SeatsBoardRenderer} for the tables that draw nothing in the world yet (blackjack, baccarat). The core client
 * module initialises BEFORE the game modules, so a game lane's own registration for the same type (e.g. lane A's
 * {@code BlackjackBoardRenderer}) overrides this one; lane A may also delete its rows from {@code interim()}.
 */
public final class BoardKit {
	/** Tables without a board renderer of their own yet, and their felt. */
	private static List<Interim> interim() {
		return List.of(
			new Interim(BlackjackModule.TABLE, Felt.CRESCENT),
			new Interim(BlackjackModule.HIGH_ROLLER_TABLE, Felt.CRESCENT),
			new Interim(BaccaratModule.TABLE, Felt.CRESCENT),
			new Interim(BaccaratModule.HIGH_ROLLER_TABLE, Felt.CRESCENT),
			new Interim(BaccaratModule.CHEMMY_TABLE, Felt.CRESCENT));
	}

	private record Interim(TableType<?> type, Felt felt) {}

	private BoardKit() {}

	@SuppressWarnings({"deprecation", "unchecked"}) // the only public 26.x registration path
	public static void register() {
		for (Interim i : interim()) {
			if (i.type() == null) continue;
			BlockEntityType<CasinoTableBlockEntity> type = (BlockEntityType<CasinoTableBlockEntity>) (BlockEntityType<?>) i.type().blockEntityType();
			BlockEntityRendererRegistry.register(type, ctx -> new SeatsBoardRenderer(ctx, i.felt()));
		}
	}
}
