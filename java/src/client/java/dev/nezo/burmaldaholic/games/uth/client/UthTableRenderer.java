package dev.nezo.burmaldaholic.games.uth.client;

import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.poker.present.TableWorld;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import dev.nezo.burmaldaholic.games.uth.present.UthPub;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Ultimate Texas Hold'em table in the world (animation/cards.md §3.4, task J-C10): the dealer's two cards at the far
 * edge (backs until the showdown), the five board cards across the centre (backs until their street flips), and per
 * dealt-in seat two backs and one combined stack along the near rim. Deal arcs, the street flips and the showdown
 * (dealer first, then every seat) follow the {@link UthBeats} timeline of the public tag on the shared clock.
 */
public final class UthTableRenderer extends CardTableRenderer<UthTableBlockEntity, CardTableRenderer.State> {
	private static final Map<UthTableBlockEntity, Cache> CACHE = new WeakHashMap<>();

	private static final class Cache {
		int seq = -1;
		boolean catchUp;
		double lastT = Double.NaN;
	}

	public UthTableRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		for (TableType<UthTableBlockEntity> type : java.util.List.of(UthModule.TABLE, UthModule.HIGH_ROLLER_TABLE, UthModule.PLAYER_BANKED_TABLE)) {
			BlockEntityRendererRegistry.register(type.blockEntityType(), UthTableRenderer::new);
		}
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	static int backFor(@Nullable Level level) {
		if (level == null) return BACK_EMERALD;
		if (level.dimension() == Level.NETHER) return BACK_BASTION;
		if (level.dimension() == Level.END) return BACK_END;
		return BACK_EMERALD;
	}

	@Override
	protected void fill(UthTableBlockEntity be, State s, float partialTick) {
		UthPub p = be.clientPub();
		if (p.seq() == 0) return;
		s.back = backFor(be.getLevel());
		Cache c = CACHE.computeIfAbsent(be, k -> new Cache());
		Timeline full = be.clientTimeline();
		double t = s.levelMs - p.startTick() * 50.0;
		if (c.seq != p.seq()) {
			c.seq = p.seq();
			c.lastT = Double.NaN;
			c.catchUp = full != null && t > 0 && TimelineSeed.showSettled(t, full.sharedEndMs());
		}
		Timeline tl = c.catchUp ? null : full;
		int kind = p.kind();
		boolean dealing = kind == UthPub.DEAL && tl != null;
		boolean showdown = kind == UthPub.SHOWDOWN;
		float[] deck = TableWorld.deck(true);
		s.card(deck[0], deck[1], 0, TableWorld.SEAT_W, TableWorld.SEAT_H, -1);
		// the dealer's hand
		for (int j = 0; j < 2; j++) {
			float x = 0.5f + (j - 0.5f) * (TableWorld.BOARD_W + 0.02f);
			double dealU = dealing ? beat(tl, UthBeats.DEALER_DEAL, j, t) : 1;
			int face = p.dealer().length == 2 ? p.dealer()[j] : -1;
			double flipU = showdown && tl != null ? beat(tl, UthBeats.DEALER_FLIP, j, t) : 1;
			dealt(s, deck[0], deck[1], x, 0.18f, 0, TableWorld.BOARD_W, TableWorld.BOARD_H, face, dealU, flipU);
		}
		// the board: five backs from the deal on, faces as the streets turn them
		boolean gathered = kind == UthPub.RESULT && tl != null && beat(tl, UthBeats.GATHER, -1, t) >= 1;
		for (int i = 0; i < 5 && !gathered; i++) {
			float[] b = TableWorld.board(i, 0.45f);
			double dealU = dealing ? beat(tl, UthBeats.BOARD, i, t) : 1;
			int face = i < p.board().length ? p.board()[i] : -1;
			double flipU = 1;
			if (tl != null && (kind == UthPub.FLOP || kind == UthPub.RIVER)) {
				Beat f = find(tl, UthBeats.FLIP, i);
				if (f != null) flipU = f.progress(t);
			}
			dealt(s, deck[0], deck[1], b[0], b[1], 0, TableWorld.BOARD_W, TableWorld.BOARD_H, face, dealU, flipU);
		}
		// seats
		int n = p.seats().length;
		for (int lane = 0; lane < n && !gathered; lane++) {
			float[] a = TableWorld.uthSeat(lane, n);
			if (!p.has(lane, UthPub.FOLDED)) {
				for (int j = 0; j < 2; j++) {
					double dealU = 1;
					if (dealing) {
						for (Beat b : tl.beats()) if (b.kind().equals(UthBeats.DEAL) && b.lane() == lane && b.arg(0) == j) dealU = b.progress(t);
					}
					float off = (j - 0.5f) * TableWorld.SEAT_W * 0.55f;
					int face = p.cards()[lane * 2 + j];
					double flipU = showdown && tl != null ? beat(tl, UthBeats.REVEAL, -1, t) : 1;
					var card = dealt(s, deck[0], deck[1], a[0] + off, a[1] - 0.06f, a[2], TableWorld.SEAT_W, TableWorld.SEAT_H, face, dealU, flipU);
					if (card != null && kind == UthPub.RESULT && !p.has(lane, UthPub.WON) && face >= 0) card.dim = true;
				}
			}
			s.stack(a[0], a[1] + 0.05f, p.bets()[lane], 1);
		}
		sounds(be, s, c, tl, t);
		c.lastT = t;
	}

	private static @Nullable Beat find(Timeline tl, String kind, int lane) {
		for (Beat b : tl.beats()) if (b.kind().equals(kind) && b.lane() == lane) return b;
		return null;
	}

	private static double beat(Timeline tl, String kind, int lane, double t) {
		Beat b = find(tl, kind, lane);
		return b == null ? 1 : b.progress(t);
	}

	private static void sounds(UthTableBlockEntity be, State s, Cache c, @Nullable Timeline tl, double t) {
		if (tl == null || Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) return;
		for (Beat b : tl.beats()) {
			if (b.at() <= c.lastT || b.at() > t) continue;
			switch (b.kind()) {
				case UthBeats.DEAL, UthBeats.DEALER_DEAL -> tableSound(be.getBlockPos(), s, "card_deal", 0.35f, 1f);
				case UthBeats.BOARD -> {
					if (b.lane() == 0) tableSound(be.getBlockPos(), s, "card_slide", 0.4f, 1f);
				}
				case UthBeats.FLIP, UthBeats.DEALER_FLIP -> tableSound(be.getBlockPos(), s, "card_flip", 0.4f, 1f);
				case UthBeats.GATHER -> tableSound(be.getBlockPos(), s, "card_gather", 0.4f, 1f);
				default -> {
				}
			}
		}
	}
}
