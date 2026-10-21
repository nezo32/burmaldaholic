package dev.nezo.burmaldaholic.games.baccarat.client;

import dev.nezo.burmaldaholic.client.board.BoardCard;
import dev.nezo.burmaldaholic.client.board.BoardState;
import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub.SeatPub;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackWorld;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Baccarat / chemin de fer in the world (REDESIGN.md §9.3, docs/architecture/ui-v2.md §7.6), on the board kit: the
 * PLAYER (left) and BANKER (right) hands dealt from the shoe and turned on their own reveal beats (a squeezed card
 * stays a back until its reveal), the stacks on P / T / B and the pairs, the board ({@code Baccarat · 10–1000} /
 * {@code Player 4 · Banker 7} / {@code BANKER WINS}) and a plate per seat ({@code Alex} / {@code 100 · Banker}). From the
 * PUBLIC tag only ({@link BaccaratPub}).
 */
public final class BaccaratBoardRenderer extends CardTableRenderer<BaccaratTableBlockEntity, CardTableRenderer.State> {
	private static final double DEAL_MS = 320, FLIP_MS = 260;
	/** Hand fan centres and their row; box positions (P, B, T, P.PAIR, B.PAIR) on the felt. */
	static final float HAND_DX = 0.42f, STEP = 0.13f;
	static final float HAND_Z = BlackjackWorld.FELT.z0() + BoardGeometry.RAIL + BoardGeometry.CARD_H / 2 + 0.08f;
	static final float BOX_Z = 0.62f;
	static final float[][] BOX = {{0.5f - 0.40f, BOX_Z}, {0.5f + 0.40f, BOX_Z}, {0.5f, BOX_Z + 0.08f}, {0.5f - 0.82f, BOX_Z - 0.10f},
		{0.5f + 0.82f, BOX_Z - 0.10f}};

	public BaccaratBoardRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(BaccaratModule.TABLE.blockEntityType(), BaccaratBoardRenderer::new);
		BlockEntityRendererRegistry.register(BaccaratModule.HIGH_ROLLER_TABLE.blockEntityType(), BaccaratBoardRenderer::new);
		BlockEntityRendererRegistry.register(BaccaratModule.CHEMMY_TABLE.blockEntityType(), BaccaratBoardRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	protected int theme(BaccaratTableBlockEntity be) {
		return be.isHighRoller() ? 2 : super.theme(be);
	}

	@Override
	protected void fill(BaccaratTableBlockEntity be, State s, float partialTick) {
		BaccaratPub p = be.clientPub();
		s.back = dev.nezo.burmaldaholic.client.board.BoardTextures.BACK_EMERALD;
		double now = s.levelMs;
		long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
		float[] shoe = BlackjackWorld.shoe();
		BoardCard sc = s.card(shoe[0], shoe[1], 90, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, -1);
		sc.lift = 0.04f;
		sc.tilt = 0;
		// S1: one first-seen anchor per deal index on this table (a late or bunched packet's card still flies)
		dev.nezo.burmaldaholic.client.table.cards.DealAnchors deals = s.cache(dev.nezo.burmaldaholic.client.table.cards.DealAnchors::new);
		deals.frame(p.revStart());
		hand(s, p, p.player(), p.playerDeal(), p.playerRev(), 0.5f - HAND_DX, shoe, now, p.winner() == 2, deals, 0);
		hand(s, p, p.banker(), p.bankerDeal(), p.bankerRev(), 0.5f + HAND_DX, shoe, now, p.winner() == 1, deals, 10);
		for (int k = 0; k < BaccaratPub.BOXES; k++) s.stack(BOX[k][0], BOX[k][1], p.boxes()[k]);
		board(be, s, p, gameTime);
		int seats = Math.max(7, be.clientSeats().length);
		seatPlates(s, be, (i, n) -> spot(i, seats), (i, pub, ref) -> {
			SeatPub sp = p.seat(i);
			if (sp == null) return;
			ref.acting(sp.has(BaccaratPub.BANKER));
			ref.detail((long) p.hashCode() * 31 + i, () -> plateLine(p, sp), BoardState.GOLD);
		});
	}

	/** Seat spots along the front arc (the kit's crescent). */
	static float[] spot(int i, int seats) {
		return BoardGeometry.crescentSeat(i, seats);
	}

	private void hand(State s, BaccaratPub p, int[] codes, int[] deal, int[] rev, float cx, float[] shoe, double now, boolean lost,
			dev.nezo.burmaldaholic.client.table.cards.DealAnchors deals, int keyBase) {
		int n = codes.length;
		for (int i = 0; i < n; i++) {
			boolean third = i == 2; // the third card lies sideways in front of the two (real-table habit)
			float x = third ? cx : cx + (i - 0.5f) * STEP;
			float z = third ? HAND_Z + BlackjackWorld.DEALER_H * 0.62f : HAND_Z;
			double dealU = dealU(deals, keyBase + i, now, p.revStart(), deal[i], DEAL_MS);
			double flipU = codes[i] >= 0 ? (rev[i] >= 0 ? u(now, p.revStart(), rev[i], FLIP_MS) : 1) : 0;
			BoardCard card = dealt(s, shoe[0], shoe[1], x, z, third ? 90 : 0, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, BlackjackWorld.face(codes[i]),
				dealU, flipU);
			if (card != null && lost) card.dim = true;
		}
	}

	/** The deal slide of a card on its first-seen anchor (S1: a late card still flies, ≥ 120 ms). */
	private static double dealU(dev.nezo.burmaldaholic.client.table.cards.DealAnchors deals, long key, double nowMs, long startTick, int at, double durMs) {
		return deals.u(key, nowMs, (startTick + at) * 50.0, durMs);
	}

	private static double u(double nowMs, long startTick, int at, double durMs) {
		double t = nowMs - (startTick + at) * 50.0;
		return Math.max(0, Math.min(1, t / durMs));
	}

	private void board(BaccaratTableBlockEntity be, State s, BaccaratPub p, long gameTime) {
		long second = p.deadline() >= 0 ? Math.max(0, (p.deadline() - gameTime + 19) / 20) : -1;
		boolean hr = be.isHighRoller();
		s.line(BoardState.TITLE, p.min() * 31 + p.max() + (p.chemmy() ? 3 : 0) + (hr ? 7 : 0), () -> {
			Component title = Component.translatable(p.chemmy() ? "gui.burmaldaholic.baccarat.title_chemmy"
				: hr ? "gui.burmaldaholic.baccarat.title_high_roller" : "gui.burmaldaholic.baccarat.title");
			return p.max() > 0 && !p.chemmy() ? Component.translatable("gui.burmaldaholic.cards.plaque", title, Texts.number(p.min()), Texts.number(p.max()))
				: title;
		}, BoardState.GOLD);
		boolean cards = p.player().length + p.banker().length > 0 && p.phase() != BaccaratPub.BETTING && p.phase() != BaccaratPub.IDLE;
		if (cards) {
			int pt = BaccaratPub.total(p.player());
			int bt = BaccaratPub.total(p.banker());
			s.line(BoardState.STATE, 100L * pt + bt, () -> Component.translatable("gui.burmaldaholic.baccarat.actionbar", pt, bt), BoardState.WHITE);
		} else if (p.phase() == BaccaratPub.BETTING && second >= 0) {
			s.line(BoardState.STATE, 1000 + second, () -> Component.translatable("gui.burmaldaholic.baccarat.v2.board.betting", second), BoardState.WHITE);
		} else if (p.phase() == BaccaratPub.SHUFFLE) {
			s.line(BoardState.STATE, 3, () -> Component.translatable("gui.burmaldaholic.baccarat.v2.reason.shuffling"), BoardState.WHITE);
		} else {
			s.line(BoardState.STATE, 4, () -> Component.translatable("gui.burmaldaholic.baccarat.place_bets"), BoardState.WHITE);
		}
		if (p.winner() > 0) {
			String w = p.winner() == 1 ? "player" : p.winner() == 2 ? "banker" : "tie";
			s.line(BoardState.RESULT, 10 + p.winner(), () -> Component.translatable("gui.burmaldaholic.baccarat.v2.banner." + w),
				p.winner() == 3 ? BoardState.MUTED : BoardState.GOLD);
		} else if (p.phase() == BaccaratPub.REVEAL || p.phase() == BaccaratPub.NO_MORE_BETS) {
			s.line(BoardState.RESULT, 20, () -> Component.translatable("gui.burmaldaholic.baccarat.v2.board.no_more_bets"), BoardState.MUTED);
		} else if (p.chemmy() && p.bank() > 0) {
			s.line(BoardState.RESULT, 30 + p.bank(), () -> Component.translatable("gui.burmaldaholic.baccarat.v2.board.bank", Texts.number(p.bank())),
				BoardState.GOLD);
		}
	}

	private static @Nullable Component plateLine(BaccaratPub p, SeatPub s) {
		if (s.has(BaccaratPub.BANKER) && p.chemmy()) return Component.translatable("gui.burmaldaholic.baccarat.v2.board.bank", Texts.number(p.bank()));
		if (s.total() <= 0) return null;
		return Component.translatable("gui.burmaldaholic.baccarat.v2.plate.line", Texts.number(s.total()),
			Component.translatable("gui.burmaldaholic.baccarat." + BaccaratPub.BOX_IDS[s.mainBox()]));
	}
}
