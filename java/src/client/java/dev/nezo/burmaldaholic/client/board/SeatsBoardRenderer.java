package dev.nezo.burmaldaholic.client.board;

import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

/**
 * The generic board of a table whose game has no board renderer yet (blackjack and baccarat until lane A ships
 * {@code BlackjackBoardRenderer} / {@code BaccaratBoardRenderer}): the felt, the table's name on the board and a plate
 * per occupied seat (names from {@link dev.nezo.burmaldaholic.core.table.TablePublic}). Registered by
 * {@link BoardKit}; a game lane replaces it by registering its own renderer for the type.
 */
public class SeatsBoardRenderer extends BoardRenderer<CasinoTableBlockEntity, BoardState> {
	private final Felt felt;

	public SeatsBoardRenderer(BlockEntityRendererProvider.Context ctx, Felt felt) {
		super(ctx);
		this.felt = felt;
	}

	@Override
	public BoardState createRenderState() {
		return new BoardState();
	}

	@Override
	protected Felt felt(CasinoTableBlockEntity be) {
		return felt;
	}

	@Override
	protected void fill(CasinoTableBlockEntity be, BoardState s, float partialTick) {
		String key = be.getBlockState().getBlock().getDescriptionId();
		s.line(BoardState.TITLE, key.hashCode(), () -> Component.translatable(key), BoardState.GOLD);
		SeatSpot spot = felt == Felt.OVAL ? BoardGeometry::ovalSeat : BoardGeometry::crescentSeat;
		seatPlates(s, be, spot, null);
	}
}
