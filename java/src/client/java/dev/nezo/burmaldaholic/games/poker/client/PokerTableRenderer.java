package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBeats;
import dev.nezo.burmaldaholic.games.poker.present.PokerPub;
import dev.nezo.burmaldaholic.games.poker.present.TableWorld;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Hold'em table in the world (animation/cards.md §2.4, task J-C10): the board in a row across the centre, every
 * dealt-in seat's two cards (backs; faces only when the public tag has them — exposed at an all-in or shown at the
 * showdown), the seats' bets, the pot and the dealer button, all moving on the same {@link PokerBeats} timeline as the
 * players' screens: hole cards arc from the deck, streets slide and flip (the slow all-in run-out included), the pot
 * slides to the winner. Text (the pot) only within 8 blocks. Positional sounds for spectators only.
 */
public final class PokerTableRenderer extends CardTableRenderer<PokerTableBlockEntity, CardTableRenderer.State> {
	private static final Map<PokerTableBlockEntity, Cache> CACHE = new WeakHashMap<>();

	/** Timeline per public segment (rebuilt only when the segment changes). */
	private static final class Cache {
		int seq = -1;
		@Nullable Timeline tl;
		boolean catchUp;
		double lastT = Double.NaN;
		long pot = -1;
		net.minecraft.util.FormattedCharSequence potText;
	}

	public PokerTableRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(PokerModule.TABLE.blockEntityType(), PokerTableRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	static @Nullable Timeline timeline(PokerPub p) {
		return PokerBeats.segment(PokerPub.kindName(p.kind()), p.args(), p.seed());
	}

	/** Back design by the table's dimension (visual/cards.md §7: crimson in the village, bastion, end). */
	static int backFor(@Nullable Level level) {
		if (level == null) return BACK_CRIMSON;
		if (level.dimension() == Level.NETHER) return BACK_BASTION;
		if (level.dimension() == Level.END) return BACK_END;
		return BACK_CRIMSON;
	}

	@Override
	protected void fill(PokerTableBlockEntity be, State s, float partialTick) {
		PokerPub p = be.clientPub();
		if (p.seatCount() == 0) return;
		s.back = backFor(be.getLevel());
		Cache c = CACHE.computeIfAbsent(be, k -> new Cache());
		if (c.seq != p.seq()) {
			c.seq = p.seq();
			c.tl = timeline(p);
			c.lastT = Double.NaN;
			c.catchUp = false;
			double t0 = s.levelMs - p.startTick() * 50.0;
			if (c.tl != null && t0 > 0 && TimelineSeed.showSettled(t0, c.tl.sharedEndMs())) c.catchUp = true;
		}
		Timeline tl = c.catchUp ? null : c.tl;
		double t = s.levelMs - p.startTick() * 50.0;
		String kind = PokerPub.kindName(p.kind());
		int size = p.seatCount();
		float[] deck = TableWorld.deck(false);
		// the deck
		s.card(deck[0], deck[1], 0, TableWorld.SEAT_W, TableWorld.SEAT_H, -1);
		// board
		for (int i = 0; i < p.board().length; i++) {
			float[] b = TableWorld.board(i, 0.47f);
			double dealU = 1;
			double flipU = 1;
			if (tl != null && (kind.equals("street") || kind.equals("finish"))) {
				Beat slide = PokerBeats.find(tl, PokerBeats.SLIDE, i);
				Beat flip = PokerBeats.find(tl, PokerBeats.FLIP, i);
				if (slide != null) dealU = slide.progress(t);
				if (flip != null) flipU = flip.progress(t);
			}
			dealt(s, deck[0], deck[1], b[0], b[1], 0, TableWorld.BOARD_W, TableWorld.BOARD_H, p.board()[i], dealU, flipU);
		}
		// seats (hand index k = k-th dealt seat in seat order)
		int k = 0;
		for (int seat = 0; seat < size; seat++) {
			if (!p.has(seat, PokerPub.DEALT)) continue;
			int handIndex = k++;
			float[] a = TableWorld.holdemSeat(seat, size);
			boolean folded = p.has(seat, PokerPub.FOLDED);
			if (!folded || kind.equals("deal")) {
				for (int j = 0; j < 2; j++) {
					double dealU = 1;
					if (tl != null && kind.equals("deal")) {
						for (Beat b : tl.beats()) {
							if (b.kind().equals(PokerBeats.DEAL) && b.lane() == handIndex && b.arg(0) == j) dealU = b.progress(t);
						}
					}
					if (folded && dealU >= 1) continue;
					float off = (j - 0.5f) * (TableWorld.SEAT_W * 0.55f);
					double ang = Math.toRadians(a[2]);
					float cx = a[0] + off * (float) Math.cos(ang);
					float cz = a[1] + off * (float) Math.sin(ang);
					int face = p.card(seat, j);
					double flipU = 1;
					if (face >= 0 && tl != null && kind.equals("finish")) flipU = showFlip(tl, t);
					var card = dealt(s, deck[0], deck[1], cx, cz, a[2] + (j - 0.5f) * 6f, TableWorld.SEAT_W, TableWorld.SEAT_H, face, dealU, flipU);
					if (card != null && p.has(seat, PokerPub.WINNER)) card.lift += 0.004f;
				}
			}
			float[] bet = TableWorld.holdemBet(seat, size);
			s.stack(bet[0], bet[1], p.bets()[seat], 1);
		}
		// the pot: in the middle, sliding to the winners on the award beat
		if (p.pot() > 0) {
			float px = 0.5f;
			float pz = 0.66f;
			if (tl != null && kind.equals("finish")) {
				Beat award = PokerBeats.find(tl, PokerBeats.AWARD, 0);
				int winner = firstWinner(p);
				if (award != null && winner >= 0 && t >= award.at()) {
					float[] w = TableWorld.holdemBet(winner, size);
					double u = tweened(s) ? award.progress(t) : 1;
					px += (float) ((w[0] - px) * u);
					pz += (float) ((w[1] - pz) * u);
				}
			}
			s.stack(px, pz, p.pot(), 1);
			if (c.pot != p.pot()) {
				c.pot = p.pot();
				c.potText = Texts.number(p.pot()).getVisualOrderText();
			}
			s.text(px, TableWorld.TOP + 0.12f, pz, c.potText, 0xFFFFD640);
		}
		sounds(be, s, c, tl, t);
		c.lastT = t;
	}

	private static int firstWinner(PokerPub p) {
		for (int i = 0; i < p.seatCount(); i++) if (p.has(i, PokerPub.WINNER)) return i;
		return -1;
	}

	/** Face-up hole cards turn over with the exposure / their show beat (all at the first show on the table view). */
	private static double showFlip(Timeline tl, double t) {
		Beat expose = PokerBeats.find(tl, PokerBeats.EXPOSE, -1);
		if (expose != null) return expose.progress(t);
		Beat show = PokerBeats.find(tl, PokerBeats.SHOW, 0);
		return show == null ? 1 : Math.min(1, (t - show.at()) / PokerBeats.FLIP_MS);
	}

	private static void sounds(PokerTableBlockEntity be, State s, Cache c, @Nullable Timeline tl, double t) {
		if (tl == null || Double.isNaN(c.lastT) || t <= c.lastT || t - c.lastT > 500) return;
		for (Beat b : tl.beats()) {
			if (b.at() <= c.lastT || b.at() > t) continue;
			switch (b.kind()) {
				case PokerBeats.DEAL -> tableSound(be.getBlockPos(), s, "card_deal", 0.35f, 1f);
				case PokerBeats.FLIP -> tableSound(be.getBlockPos(), s, "card_flip", 0.4f, 1f + 0.06f * Math.max(0, b.lane()) % 3);
				case PokerBeats.AWARD -> tableSound(be.getBlockPos(), s, "pot_win", 0.5f, 1f);
				case PokerBeats.GATHER -> tableSound(be.getBlockPos(), s, "chip_sweep", 0.35f, 1f);
				default -> {
				}
			}
		}
	}
}
