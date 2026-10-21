package dev.nezo.burmaldaholic.client.table;

import dev.nezo.burmaldaholic.client.board.BoardCard;
import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.board.BoardTextures;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.present.TableWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Card-table view kept for Hold'em and UTH until lane B ports them (docs/architecture/ui-v2.md §7.1): a thin
 * subclass of the board kit's {@link BoardRenderer}. The kit now draws the felt overhang, the cards (from
 * {@code faces_w2.png}, leaning toward their owner), the chip stacks and the holo text; this class only keeps the old
 * subclass API — {@link State#card}, {@link State#stack(float, float, long, float)}, {@link State#text}, {@link #dealt},
 * {@link #tweened}, {@link #tableSound} — so the poker / UTH renderers compile and draw unchanged on top of it.
 *
 * <p>Subclasses fill the {@link State} from their table's PUBLIC tag only (never a hole card that is not face up) and the
 * same beat timeline as the screens (shared clock). Table sounds are positional and only for viewers who do not have
 * this table's screen open (the screen plays its own), via {@link FxSounds#playAt}.
 */
public abstract class CardTableRenderer<T extends BlockEntity, S extends CardTableRenderer.State> extends BoardRenderer<T, S> {
	public static final int MAX_CARDS = 32, MAX_DISCS = 96, MAX_TEXTS = 8;
	/** Back rows of the card atlas: navy, burgundy, crimson, emerald, bastion, end. */
	public static final int BACK_CRIMSON = BoardTextures.BACK_CRIMSON, BACK_EMERALD = BoardTextures.BACK_EMERALD, BACK_BASTION = BoardTextures.BACK_BASTION,
		BACK_END = BoardTextures.BACK_END;

	/** The legacy state API on top of {@link BoardState}. */
	public static class State extends BoardState {
		/** A chip stack (legacy signature). */
		public void stack(float x, float z, long amount, float alpha) {
			stack(x, z, amount, alpha, 0);
		}

		/** A small billboard label (legacy; within 8 blocks). */
		public void text(float x, float y, float z, @Nullable FormattedCharSequence text, int color) {
			label(x, y, z, text, color);
		}
	}

	protected CardTableRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	/** Five community cards at {@code CARD_W + 0.03} pitch (Hold'em row at z 0.40, UTH at 0.50), plus the felt's dealer row. */
	private static final float[] HOLDEM_CARDS = communityRect(Felt.OVAL, 0.40f), UTH_CARDS = communityRect(Felt.CRESCENT, 0.50f);

	private static float[] communityRect(Felt felt, float rowZ) {
		float[] r = BoardGeometry.dealerRect(felt);
		float half = 2 * (BoardGeometry.CARD_W + 0.03f) + BoardGeometry.CARD_W / 2;
		float dz = BoardGeometry.CARD_H / 2 + 0.02f;
		if (r == null) return new float[] {0.5f - half, rowZ - dz, 0.5f + half, rowZ + dz, BoardGeometry.TOP + 0.2f};
		return new float[] {Math.min(r[0], 0.5f - half), Math.min(r[1], rowZ - dz), Math.max(r[2], 0.5f + half), Math.max(r[3], rowZ + dz), r[4]};
	}

	/**
	 * The card area the seat plates keep clear of (design audit W P1: plates covered the dealer cards): Hold'em and UTH
	 * add their community row to the felt's dealer row; blackjack / baccarat keep the kit's dealer row.
	 */
	@Override
	protected float @Nullable [] dealerRect(T be) {
		if (be instanceof PokerTableBlockEntity) return HOLDEM_CARDS;
		if (be instanceof dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity) return UTH_CARDS;
		return super.dealerRect(be);
	}

	/** Hold'em plays on the oval, UTH on the crescent. */
	@Override
	protected Felt felt(T be) {
		return be instanceof PokerTableBlockEntity ? Felt.OVAL : Felt.CRESCENT;
	}

	// ---- helpers for subclasses -------------------------------------------------------------------------------

	/** Motion is tweened (near, not reduced); otherwise cards snap to their slot and flip in place. */
	protected static boolean tweened(BoardState s) {
		return s.lod == Lod.FULL && !FxSettings.reduceMotion();
	}

	/** The local player has this table's screen open (it plays its own sounds). */
	protected static boolean viewingOwnScreen(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof MenuAccess<?> s && s.getMenu() instanceof CasinoTableMenu m && m.pos().equals(pos);
	}

	/** Plays a table sound at the table top for a spectator (never for a viewer of the screen, never far away). */
	protected static void tableSound(BlockPos pos, BoardState s, String id, float volume, float pitch) {
		if (s.lod != Lod.FULL || viewingOwnScreen(pos)) return;
		FxSounds.playAt(id, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, volume, pitch);
	}

	/**
	 * Places a card that is dealt from {@code (fx, fz)} to {@code (tx, tz)}: {@code dealU} 0..1 along the arc (1 = in
	 * its slot), then {@code flipU} 0..1 turning it over (the face shows after ½; {@code face} = −1 keeps the back).
	 */
	protected static @Nullable BoardCard dealt(BoardState s, float fx, float fz, float tx, float tz, float yaw, float w, float h, int face, double dealU,
			double flipU) {
		boolean motion = tweened(s);
		if (!motion) dealU = dealU > 0 ? 1 : 0;
		if (dealU <= 0) return null;
		float[] p = TableWorld.arc(fx, fz, tx, tz, dealU, 0.10f);
		boolean flipping = face >= 0 && flipU > 0 && flipU < 1;
		BoardCard c = s.card(p[0], p[1], yaw - 20f * (float) (1 - Math.min(1, dealU)), w, h, face >= 0 && (flipU >= 1 || !motion && flipU >= 0.5) ? face : -1);
		c.lift = p[2];
		if (flipping && motion) {
			c.face = face;
			c.flip = (float) flipU;
			c.lift += 0.05f * (float) Math.sin(Math.PI * flipU);
		}
		return c;
	}
}
