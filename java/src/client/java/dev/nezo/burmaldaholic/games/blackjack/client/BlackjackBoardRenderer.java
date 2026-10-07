package dev.nezo.burmaldaholic.games.blackjack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.client.table.cards.BoardFelt;
import dev.nezo.burmaldaholic.client.table.cards.BoardHolo;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Hands;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.HandPub;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub.SeatPub;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Blackjack in the world (REDESIGN.md §9.3, docs/architecture/ui-v2.md §7.6): the felt, the dealer's fan (hole card
 * face-down until its flip beat), every seat's hand(s) and bet stack, the shoe; an upright board over the dealer
 * ({@code Blackjack · 10–100} / {@code Dealer 10} / {@code Steve · 18 s} → {@code Steve +350}) and one plate per
 * seat ({@code Steve} / {@code 50 · 15} → {@code +75 BLACKJACK}). Everything from the table's PUBLIC tag
 * ({@link BlackjackPub}); cards fly from the shoe on their deal tick (the screens' shared clock), so a passer-by sees the
 * same deal at the same moment as the players.
 */
public final class BlackjackBoardRenderer extends CardTableRenderer<BlackjackTableBlockEntity, BlackjackBoardRenderer.BjState> {
	private static final double DEAL_MS = 320, FLIP_MS = 260;
	private static final Map<BlackjackTableBlockEntity, Cache> CACHE = new WeakHashMap<>();

	public static final class BjState extends CardTableRenderer.State {
		public final BoardHolo holo = new BoardHolo();
		public final BoardFelt felt = new BoardFelt();
	}

	/** Text and flip memory per table (rebuilt only when the public data / the second / the language changes). */
	private static final class Cache {
		@Nullable BlackjackPub pub;
		@Nullable CompoundTag names;
		long second = Long.MIN_VALUE;
		String lang = "";
		int seq = -1;
		double holeSeenMs = -1;
		boolean holeWasHidden;
		final List<FormattedCharSequence> board = new ArrayList<>();
		final List<Integer> boardColor = new ArrayList<>();
		final List<Object[]> plates = new ArrayList<>();
	}

	public BlackjackBoardRenderer(BlockEntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@SuppressWarnings("deprecation") // the only public 26.x registration path
	public static void register() {
		BlockEntityRendererRegistry.register(BlackjackModule.TABLE.blockEntityType(), BlackjackBoardRenderer::new);
		BlockEntityRendererRegistry.register(BlackjackModule.HIGH_ROLLER_TABLE.blockEntityType(), BlackjackBoardRenderer::new);
	}

	@Override
	public BjState createRenderState() {
		return new BjState();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true; // the felt and the board overhang the block
	}

	static int backFor(@Nullable Level level) {
		if (level == null) return BACK_CRIMSON;
		if (level.dimension() == Level.NETHER) return BACK_BASTION;
		if (level.dimension() == Level.END) return BACK_END;
		return BACK_CRIMSON;
	}

	@Override
	protected void fill(BlackjackTableBlockEntity be, BjState s, float partialTick) {
		BlackjackPub p = be.clientPub();
		s.back = backFor(be.getLevel());
		s.felt.set(be.getLevel(), be.isHighRoller());
		s.holo.clear();
		s.holo.lod(Math.sqrt(s.distSq));
		Cache c = CACHE.computeIfAbsent(be, k -> new Cache());
		long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
		double now = s.levelMs;
		if (c.seq != p.seq()) {
			c.seq = p.seq();
			c.holeSeenMs = -1;
			c.holeWasHidden = false;
		}
		// shoe
		float[] shoe = BlackjackWorld.shoe();
		s.card(shoe[0], shoe[1], 90, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, -1).lift = 0.03f;
		s.card(shoe[0], shoe[1], 90, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, -1).lift = 0.05f;
		s.holo.content(shoe[0], shoe[1]);
		// dealer
		int dn = p.dealer().length;
		for (int i = 0; i < dn; i++) {
			int code = p.dealer()[i];
			double dealU = u(now, p.startTick(), p.dealerAt()[i], 0, DEAL_MS);
			double flipU = u(now, p.startTick(), p.dealerAt()[i], 120, FLIP_MS);
			if (i == 1) {
				if (code < 0) c.holeWasHidden = true;
				else if (c.holeSeenMs < 0) c.holeSeenMs = c.holeWasHidden ? now : 0;
				if (code >= 0 && c.holeSeenMs > 0) flipU = Math.max(0, Math.min(1, (now - c.holeSeenMs) / FLIP_MS));
			}
			float x = BlackjackWorld.dealerX(i, Math.max(2, dn));
			dealt(s, shoe[0], shoe[1], x, BlackjackWorld.DEALER_Z, 0, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, BlackjackWorld.face(code),
				dealU, flipU);
			s.holo.content(x, BlackjackWorld.DEALER_Z);
		}
		// seats (world order: seat index = slot, no rotation)
		int slots = slots(p);
		for (SeatPub seat : p.seats()) {
			float[] a = BlackjackWorld.seat(seat.seat(), slots);
			int hands = seat.hands().size();
			long staked = 0;
			for (int h = 0; h < hands; h++) {
				HandPub hp = seat.hands().get(h);
				staked += hp.bet();
				int n = hp.cards().length;
				for (int k = 0; k < n; k++) {
					float[] pos = BlackjackWorld.seatCard(a, h, hands, k, n);
					double dealU = u(now, p.startTick(), hp.at()[k], 0, DEAL_MS);
					double flipU = u(now, p.startTick(), hp.at()[k], 120, FLIP_MS);
					var card = dealt(s, shoe[0], shoe[1], pos[0], pos[1], a[2], BlackjackWorld.SEAT_W, BlackjackWorld.SEAT_H, BlackjackWorld.face(hp.cards()[k]),
						dealU, flipU);
					if (card != null && hp.outcome() > 0 && lost(hp.outcomeId())) card.dim = true;
					s.holo.content(pos[0], pos[1]);
				}
			}
			if (hands == 0) staked = seat.bet();
			float[] st = BlackjackWorld.stack(a);
			s.stack(st[0], st[1], staked, 1);
			if (seat.has(BlackjackPub.SETTLED) && seat.net() > 0) {
				double ang = Math.toRadians(a[2]);
				s.stack(st[0] + 0.09f * (float) Math.cos(ang), st[1] + 0.09f * (float) Math.sin(ang), seat.net(), 1);
			}
			if (staked > 0) s.holo.content(st[0], st[1]);
		}
		// text (cached sequences)
		long second = p.deadline() >= 0 ? Math.max(0, (p.deadline() - gameTime + 19) / 20) : -1;
		String lang = Minecraft.getInstance().getLanguageManager().getSelected();
		CompoundTag names = be.clientPubNames();
		if (c.pub != p || c.second != second || !lang.equals(c.lang) || c.names != names) {
			c.pub = p;
			c.second = second;
			c.lang = lang;
			c.names = names;
			rebuild(c, p, names, (int) second, be.isHighRoller(), slots);
		}
		float[] b = BlackjackWorld.board();
		s.holo.bx = b[0];
		s.holo.by = b[1];
		s.holo.bz = b[2];
		for (int i = 0; i < c.board.size(); i++) s.holo.boardLine(c.board.get(i), c.boardColor.get(i));
		for (Object[] pl : c.plates) {
			float[] pp = (float[]) pl[0];
			s.holo.plate(pp[0], pp[1], pp[2], (FormattedCharSequence) pl[1], (Integer) pl[2], (FormattedCharSequence) pl[3], (Integer) pl[4], (Integer) pl[5]);
		}
	}

	/** Seat slots of the table: the config's seat count is not public; 5 (the default) or the highest seat seen. */
	static int slots(BlackjackPub p) {
		int max = 4;
		for (SeatPub s : p.seats()) max = Math.max(max, s.seat());
		return max + 1;
	}

	private static boolean lost(String outcome) {
		return outcome.equals("bust") || outcome.equals("lose") || outcome.equals("dealer_blackjack") || outcome.equals("surrender");
	}

	/** Progress 0..1 of a card's beat that starts {@code delayMs} after its deal tick. */
	private static double u(double nowMs, long startTick, int at, double delayMs, double durMs) {
		double t = nowMs - (startTick + at) * 50.0 - delayMs;
		return Math.max(0, Math.min(1, t / durMs));
	}

	private static void rebuild(Cache c, BlackjackPub p, CompoundTag names, int second, boolean highRoller, int slots) {
		c.board.clear();
		c.boardColor.clear();
		c.plates.clear();
		String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
		// board: title · state · turn / result
		Component title = Component.translatable(highRoller ? "gui.burmaldaholic.blackjack.title_high_roller" : "gui.burmaldaholic.blackjack.title");
		if (p.max() > 0) title = Component.translatable("gui.burmaldaholic.cards.plaque", title, Texts.number(p.min()), Texts.number(p.max()));
		int dealerTotal = total(p.dealer());
		boolean round = p.dealer().length > 0;
		Component state;
		Component third = null;
		int thirdColor = BoardHolo.MUTED;
		if (!round || p.phase() == BlackjackPub.BETTING) {
			state = second >= 0 ? Component.translatable("gui.burmaldaholic.blackjack.v2.board.betting_in", second)
				: Component.translatable("gui.burmaldaholic.blackjack.v2.board.betting");
		} else {
			state = Component.translatable("gui.burmaldaholic.blackjack.v2.board.dealer", totalText(p.dealer()));
			if (p.phase() == BlackjackPub.RESULT) {
				SeatPub best = null;
				for (SeatPub s : p.seats()) {
					if (s.has(BlackjackPub.SETTLED) && (best == null || s.net() > best.net())) best = s;
				}
				if (best != null) {
					third = Component.translatable("gui.burmaldaholic.blackjack.v2.board.result", name(names, best.seat()), signed(best.net()));
					thirdColor = best.net() > 0 ? BoardHolo.WIN : best.net() < 0 ? BoardHolo.LOSE : BoardHolo.PUSH;
				}
			} else if (p.phase() == BlackjackPub.DEALER) {
				third = Component.translatable("gui.burmaldaholic.blackjack.v2.board.dealer_draws");
			} else if (p.acting() >= 0) {
				third = second >= 0 ? Component.translatable("gui.burmaldaholic.blackjack.v2.board.turn", name(names, p.acting()), second)
					: name(names, p.acting());
			}
		}
		if (dealerTotal < 0) dealerTotal = 0;
		// far LOD keeps the first two lines: state + result (the title goes last when far)
		c.board.add(title.getVisualOrderText());
		c.boardColor.add(BoardHolo.GOLD);
		c.board.add(state.getVisualOrderText());
		c.boardColor.add(BoardHolo.TEXT);
		if (third != null) {
			c.board.add(third.getVisualOrderText());
			c.boardColor.add(thirdColor);
		}
		// plates
		for (SeatPub s : p.seats()) {
			float[] a = BlackjackWorld.seat(s.seat(), slots);
			float[] at = BlackjackWorld.plate(a);
			Component n = name(names, s.seat());
			boolean you = !me.isEmpty() && n.getString().equals(me);
			boolean acting = p.acting() == s.seat();
			int c1 = you ? BoardHolo.YOU : acting ? BoardHolo.GOLD : BoardHolo.TEXT;
			Component line2;
			int c2;
			long bet = s.hands().isEmpty() ? s.bet() : s.hands().stream().mapToLong(HandPub::bet).sum();
			if (s.has(BlackjackPub.SETTLED)) {
				String word = word(s);
				line2 = Component.translatable("gui.burmaldaholic.blackjack.v2.plate.line", signed(s.net()),
					Component.translatable("gui.burmaldaholic.blackjack.v2.stamp." + word));
				c2 = s.net() > 0 ? BoardHolo.WIN : s.net() < 0 ? BoardHolo.LOSE : BoardHolo.PUSH;
			} else if (bet <= 0) {
				line2 = null;
				c2 = BoardHolo.MUTED;
			} else if (s.hands().isEmpty() || s.hands().get(0).cards().length == 0) {
				line2 = Texts.number(bet);
				c2 = BoardHolo.GOLD;
			} else {
				MutableComponent totals = null;
				for (HandPub h : s.hands()) {
					Component t = totalText(h.cards());
					totals = totals == null ? Component.empty().append(t) : Component.translatable("gui.burmaldaholic.cards.join", totals, t);
				}
				line2 = Component.translatable("gui.burmaldaholic.blackjack.v2.plate.line", Texts.number(bet), totals);
				c2 = BoardHolo.GOLD;
			}
			c.plates.add(new Object[] {at, n.getVisualOrderText(), c1, line2 == null ? null : line2.getVisualOrderText(), c2, you ? 0 : acting ? 1 : 2});
		}
	}

	/** The seat's stamp word: its best hand's outcome (blackjack > win > push > others). */
	static String word(SeatPub s) {
		String best = "lose";
		int rank = -1;
		for (HandPub h : s.hands()) {
			String w = switch (h.outcomeId()) {
				case "blackjack" -> "blackjack";
				case "win", "dealer_bust" -> "win";
				case "push" -> "push";
				case "bust" -> "bust";
				case "surrender" -> "surrender";
				case "even_money" -> "even_money";
				default -> "lose";
			};
			int r = switch (w) {
				case "blackjack" -> 5;
				case "even_money" -> 4;
				case "win" -> 3;
				case "push" -> 2;
				case "surrender" -> 1;
				default -> 0;
			};
			if (r > rank) {
				rank = r;
				best = w;
			}
		}
		return best;
	}

	static Component signed(long net) {
		if (net > 0) return Component.translatable("gui.burmaldaholic.blackjack.v2.plus", Texts.number(net));
		if (net < 0) return Component.translatable("gui.burmaldaholic.blackjack.v2.minus", Texts.number(-net));
		return Texts.number(0);
	}

	static Component name(CompoundTag names, int seat) {
		String n = names.getStringOr(Integer.toString(seat), "");
		if (n.startsWith("#")) return Component.translatable(n.substring(1));
		return Texts.raw(n);
	}

	/** Visible total of a hand (backs ignored); soft hands below 21 as {@code 7/17}. */
	static Component totalText(int[] codes) {
		List<Card> cards = new ArrayList<>();
		for (int code : codes) if (code >= 0 && code < 52) cards.add(Card.fromCode(code));
		int[] t = Hands.displayTotals(cards);
		return t.length == 2 ? Component.translatable("gui.burmaldaholic.blackjack.v2.total_soft", t[0], t[1]) : Texts.number(t[0]);
	}

	static int total(int[] codes) {
		List<Card> cards = new ArrayList<>();
		for (int code : codes) if (code >= 0 && code < 52) cards.add(Card.fromCode(code));
		return cards.isEmpty() ? -1 : Hands.total(cards);
	}

	@Override
	public void submit(BjState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		if (!s.visible) return;
		s.felt.submit(pose, out, s.facing, s.lightCoords, BlackjackWorld.X0, BlackjackWorld.Z0, BlackjackWorld.X1, BlackjackWorld.Z1);
		super.submit(s, pose, out, camera);
		s.holo.submit(pose, out, camera, s.facing, font);
	}
}
