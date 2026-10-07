package dev.nezo.burmaldaholic.games.baccarat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.client.table.CardTableRenderer;
import dev.nezo.burmaldaholic.client.table.cards.BoardFelt;
import dev.nezo.burmaldaholic.client.table.cards.BoardHolo;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub.SeatPub;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Baccarat / chemin de fer in the world (REDESIGN.md §9.3, docs/architecture/ui-v2.md §7.6): the felt, the PLAYER and
 * BANKER hands dealt from the shoe and turned on their reveal beats (the squeeze included: a card stays a back until
 * its own reveal), the stacks on P / B / T (and the pairs), the board ({@code Baccarat · 10–1000} /
 * {@code Player 4 · Banker 7} / {@code BANKER WINS}) and one plate per seat ({@code Alex} / {@code 100 · Banker}). From
 * the PUBLIC tag only ({@link BaccaratPub}).
 */
public final class BaccaratBoardRenderer extends CardTableRenderer<BaccaratTableBlockEntity, BaccaratBoardRenderer.BacState> {
	private static final double DEAL_MS = 320, FLIP_MS = 260;
	private static final Map<BaccaratTableBlockEntity, Cache> CACHE = new WeakHashMap<>();
	/** Hand centres (x of the fan middle) and the row; box positions on the felt. */
	static final float HAND_DX = 0.48f, HAND_Z = BlackjackWorld.Z0 + 0.30f, BOX_Z = BlackjackWorld.Z0 + 0.82f, STEP = 0.12f;
	static final float[][] BOX = {{0.5f - 0.48f, BOX_Z}, {0.5f + 0.48f, BOX_Z}, {0.5f, BOX_Z - 0.05f}, {0.5f - 0.95f, BOX_Z - 0.12f},
		{0.5f + 0.95f, BOX_Z - 0.12f}};

	public static final class BacState extends CardTableRenderer.State {
		public final BoardHolo holo = new BoardHolo();
		public final BoardFelt felt = new BoardFelt();
	}

	private static final class Cache {
		@Nullable BaccaratPub pub;
		@Nullable CompoundTag names;
		long second = Long.MIN_VALUE;
		String lang = "";
		final List<FormattedCharSequence> board = new ArrayList<>();
		final List<Integer> boardColor = new ArrayList<>();
		final List<Object[]> plates = new ArrayList<>();
	}

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
	public BacState createRenderState() {
		return new BacState();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	protected void fill(BaccaratTableBlockEntity be, BacState s, float partialTick) {
		BaccaratPub p = be.clientPub();
		Level level = be.getLevel();
		s.back = level == null ? BACK_EMERALD : level.dimension() == Level.NETHER ? BACK_BASTION : level.dimension() == Level.END ? BACK_END : BACK_EMERALD;
		s.felt.set(level, be.isHighRoller());
		s.holo.clear();
		s.holo.lod(Math.sqrt(s.distSq));
		double now = s.levelMs;
		long gameTime = level == null ? 0 : level.getGameTime();
		float[] shoe = BlackjackWorld.shoe();
		s.card(shoe[0], shoe[1], 90, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, -1).lift = 0.03f;
		s.card(shoe[0], shoe[1], 90, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H, -1).lift = 0.05f;
		s.holo.content(shoe[0], shoe[1]);
		hand(s, p, p.player(), p.playerDeal(), p.playerRev(), 0.5f - HAND_DX, shoe, now, p.winner() == 2);
		hand(s, p, p.banker(), p.bankerDeal(), p.bankerRev(), 0.5f + HAND_DX, shoe, now, p.winner() == 1);
		for (int k = 0; k < BaccaratPub.BOXES; k++) {
			if (p.boxes()[k] <= 0) continue;
			s.stack(BOX[k][0], BOX[k][1], p.boxes()[k], 1);
			s.holo.content(BOX[k][0], BOX[k][1]);
		}
		long second = p.deadline() >= 0 ? Math.max(0, (p.deadline() - gameTime + 19) / 20) : -1;
		String lang = Minecraft.getInstance().getLanguageManager().getSelected();
		CompoundTag names = be.clientPubNames();
		Cache c = CACHE.computeIfAbsent(be, k -> new Cache());
		if (c.pub != p || c.second != second || !lang.equals(c.lang) || c.names != names) {
			c.pub = p;
			c.second = second;
			c.lang = lang;
			c.names = names;
			rebuild(c, p, names, (int) second, be);
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

	private void hand(BacState s, BaccaratPub p, int[] codes, int[] deal, int[] rev, float cx, float[] shoe, double now, boolean lost) {
		int n = codes.length;
		for (int i = 0; i < n; i++) {
			// the third card lies sideways in front of the two (real-table habit)
			boolean third = i == 2;
			float x = third ? cx : cx + (i - 0.5f) * STEP;
			float z = third ? HAND_Z + BlackjackWorld.DEALER_H * 0.75f : HAND_Z;
			double dealU = u(now, p.revStart(), deal[i], 0, DEAL_MS);
			double flipU = codes[i] >= 0 ? (rev[i] >= 0 ? u(now, p.revStart(), rev[i], 0, FLIP_MS) : 1) : 0;
			var card = dealt(s, shoe[0], shoe[1], x, z, third ? 90 : 0, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H,
				dev.nezo.burmaldaholic.games.blackjack.present.BlackjackWorld.face(codes[i]), dealU, flipU);
			if (card != null && lost) card.dim = true;
			s.holo.content(x, z);
		}
	}

	private static double u(double nowMs, long startTick, int at, double delayMs, double durMs) {
		double t = nowMs - (startTick + at) * 50.0 - delayMs;
		return Math.max(0, Math.min(1, t / durMs));
	}

	/** Plate anchor of seat {@code i} of {@code n}: along the front rail. */
	static float[] plate(int i, int n) {
		float u = n <= 1 ? 0.5f : i / (float) (n - 1);
		float x = BlackjackWorld.X0 + 0.25f + u * (BlackjackWorld.FELT_W - 0.5f);
		return new float[] {x, 1 + BlackjackWorld.PLATE_UP, BlackjackWorld.Z1 + BlackjackWorld.PLATE_OUT};
	}

	private static void rebuild(Cache c, BaccaratPub p, CompoundTag names, int second, BaccaratTableBlockEntity be) {
		c.board.clear();
		c.boardColor.clear();
		c.plates.clear();
		String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
		Component title = Component.translatable(p.chemmy() ? "gui.burmaldaholic.baccarat.title_chemmy"
			: be.isHighRoller() ? "gui.burmaldaholic.baccarat.title_high_roller" : "gui.burmaldaholic.baccarat.title");
		if (p.max() > 0 && !p.chemmy()) title = Component.translatable("gui.burmaldaholic.cards.plaque", title, Texts.number(p.min()), Texts.number(p.max()));
		Component state;
		boolean cards = p.player().length + p.banker().length > 0 && p.phase() != BaccaratPub.BETTING && p.phase() != BaccaratPub.IDLE;
		if (cards) {
			state = Component.translatable("gui.burmaldaholic.baccarat.actionbar", BaccaratPub.total(p.player()), BaccaratPub.total(p.banker()));
		} else if (p.phase() == BaccaratPub.BETTING && second >= 0) {
			state = Component.translatable("gui.burmaldaholic.baccarat.v2.board.betting", second);
		} else if (p.phase() == BaccaratPub.SHUFFLE) {
			state = Component.translatable("gui.burmaldaholic.baccarat.v2.reason.shuffling");
		} else {
			state = Component.translatable("gui.burmaldaholic.baccarat.place_bets");
		}
		Component third = null;
		int thirdColor = BoardHolo.MUTED;
		if (p.winner() > 0) {
			third = Component.translatable("gui.burmaldaholic.baccarat.v2.banner." + (p.winner() == 1 ? "player" : p.winner() == 2 ? "banker" : "tie"));
			thirdColor = p.winner() == 3 ? BoardHolo.PUSH : BoardHolo.GOLD;
		} else if (p.phase() == BaccaratPub.REVEAL || p.phase() == BaccaratPub.NO_MORE_BETS) {
			third = Component.translatable("gui.burmaldaholic.baccarat.v2.board.no_more_bets");
		} else if (p.chemmy() && p.bank() > 0) {
			third = Component.translatable("gui.burmaldaholic.baccarat.v2.board.bank", Texts.number(p.bank()));
			thirdColor = BoardHolo.GOLD;
		}
		c.board.add(title.getVisualOrderText());
		c.boardColor.add(BoardHolo.GOLD);
		c.board.add(state.getVisualOrderText());
		c.boardColor.add(BoardHolo.TEXT);
		if (third != null) {
			c.board.add(third.getVisualOrderText());
			c.boardColor.add(thirdColor);
		}
		int slots = 7;
		for (SeatPub s : p.seats()) slots = Math.max(slots, s.seat() + 1);
		for (SeatPub s : p.seats()) {
			Component n = name(names, s.seat());
			boolean you = !me.isEmpty() && n.getString().equals(me);
			int c1 = you ? BoardHolo.YOU : s.has(BaccaratPub.BANKER) ? BoardHolo.GOLD : BoardHolo.TEXT;
			Component line2 = null;
			int c2 = BoardHolo.GOLD;
			if (s.has(BaccaratPub.BANKER) && p.chemmy()) {
				line2 = Component.translatable("gui.burmaldaholic.baccarat.v2.board.bank", Texts.number(p.bank()));
			} else if (s.total() > 0) {
				int box = s.mainBox();
				line2 = Component.translatable("gui.burmaldaholic.baccarat.v2.plate.line", Texts.number(s.total()),
					Component.translatable("gui.burmaldaholic.baccarat." + BaccaratPub.BOX_IDS[box]));
			}
			c.plates.add(new Object[] {plate(s.seat(), slots), n.getVisualOrderText(), c1, line2 == null ? null : line2.getVisualOrderText(), c2,
				you ? 0 : s.has(BaccaratPub.BANKER) ? 1 : 2});
		}
	}

	static Component name(CompoundTag names, int seat) {
		String n = names.getStringOr(Integer.toString(seat), "");
		if (n.startsWith("#")) return Component.translatable(n.substring(1));
		return Texts.raw(n);
	}

	@Override
	public void submit(BacState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		if (!s.visible) return;
		s.felt.submit(pose, out, s.facing, s.lightCoords, BlackjackWorld.X0, BlackjackWorld.Z0, BlackjackWorld.X1, BlackjackWorld.Z1);
		super.submit(s, pose, out, camera);
		s.holo.submit(pose, out, camera, s.facing, font);
	}
}
