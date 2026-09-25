package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.GuiParticlePool;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.logic.anim.ScratchMask;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Scratch Cards (UI.md §9; visual/extras.md §6, mockup {@code extras_scratch_half.png}; extras-pvp.md §7.3): the kiosk
 * scene, the "Lucky Miner" / "Gold Rush" ticket with a 3 × 3 grid of foil cells, the prize table card and the
 * controls. Hold and drag to scratch: the cursor (the Lucky Coin scraper) erases 4 × 4 sub-tiles along its path, the
 * torn border uses the scratch-edge autotile, foil flakes spray and the scratch sound plays (≤ 8/s). The first erased
 * sub-tile of a covered cell sends {@code scratch}; the symbol shows through the holes once the server sent it; at
 * 55 % (or on release) the rest dissolves. A click without a drag, the keyboard and "Scratch all" auto-swipe (three
 * zig-zag strokes, 120 ms apart in reading order).
 *
 * <p>Honest emphasis (§7.3): no pair is ever highlighted while scratching. Only when the server says the card is done:
 * a win frames the three cells one by one with a gold line and plays the celebration; the top prize adds a holographic
 * sweep; three creepers blink, char the edges and close the screen for the mob wave; a loss greys the cells and folds
 * the corner. Reduce motion: cells cross-fade (150 ms), no flakes, no shake.
 */
final class ScratchScreen extends SceneScreen implements dev.nezo.burmaldaholic.client.fx.ClientFx.CelebrationGate {
	private static final Identifier TICKET_BASIC = Kit.sheet("extras/scratch_ticket_basic");
	private static final Identifier TICKET_GOLD = Kit.sheet("extras/scratch_ticket_gold");
	private static final int TX = 20;
	private static final int TY = 30;
	private static final int CELL_W = 60;
	private static final int CELL_H = 44;
	private static final int RX = 244;
	private static final int SWIPE_GAP = 120;

	private final ScratchMask[] masks = new ScratchMask[Scratch.CELLS];
	/** Local reveal state per cell: swipe start (ms, −1 none), dissolve start (ms, −1 none), sent. */
	private final long[] swipeAt = new long[Scratch.CELLS];
	private final long[] dissolveAt = new long[Scratch.CELLS];
	private final boolean[] sent = new boolean[Scratch.CELLS];
	private final int[] dissolveOrder = new int[Scratch.CELLS];
	private final GuiParticlePool flakes = new GuiParticlePool();
	private String cardId = "";
	private int dragCell = -1;
	private double lastX;
	private double lastY;
	private double dragDist;
	private long lastScratchSound;
	private long lastFrame;
	private long endAt = -1;
	private boolean ended;
	private boolean celebrated;
	private int chimes;

	ScratchScreen(CompoundTag state) {
		super("scratch", title(state), state, Scene.SCRATCH);
		for (int i = 0; i < Scratch.CELLS; i++) masks[i] = ScratchMask.solo();
		resetCard(state, true);
	}

	private static Component title(CompoundTag s) {
		return Component.translatable("gold".equals(s.getStringOr("kind", "basic")) ? "gui.burmaldaholic.extras.scratch.title_gold"
			: "gui.burmaldaholic.extras.scratch.title");
	}

	private boolean gold() {
		return "gold".equals(state().getStringOr("kind", "basic"));
	}

	/** A new card (or the screen opened on one): cells the server already revealed start clear (late open). */
	private void resetCard(CompoundTag s, boolean opening) {
		cardId = s.getStringOr("id", "");
		int mask = s.getIntOr("mask", 0);
		for (int i = 0; i < Scratch.CELLS; i++) {
			masks[i].reset();
			swipeAt[i] = -1;
			dissolveAt[i] = -1;
			sent[i] = (mask >> i & 1) == 1;
			if (sent[i]) masks[i].clear();
		}
		ended = false;
		celebrated = false;
		chimes = 0;
		endAt = -1;
		releaseBalance();
		if (s.getBooleanOr("done", false)) {
			// reopened on a finished card: the end state at once
			ended = true;
			celebrated = true;
			chimes = 3;
			endAt = Util.getMillis() - 5000;
		}
	}

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag s) {
		String id = s.getStringOr("id", "");
		boolean newCard = !id.equals(cardId) && !(cardId.isEmpty() && !id.isEmpty() && anyStarted());
		if (!id.equals(cardId) && cardId.isEmpty() && !id.isEmpty()) {
			cardId = id; // the first scratch of a fresh card got its id: same card
			newCard = false;
		}
		if (newCard) {
			resetCard(s, false);
			return;
		}
		int mask = s.getIntOr("mask", 0);
		long now = Util.getMillis();
		int k = 0;
		for (int i = 0; i < Scratch.CELLS; i++) {
			if ((mask >> i & 1) == 1 && !sent[i]) {
				sent[i] = true;
			}
			// revealed by the server ("Scratch all") but still covered here: auto-swipe in reading order
			if ((mask >> i & 1) == 1 && masks[i].untouched() && swipeAt[i] < 0 && dissolveAt[i] < 0) {
				swipeAt[i] = now + (long) k++ * SWIPE_GAP;
			}
		}
		if (s.getBooleanOr("done", false) && !oldState.getBooleanOr("done", false)) {
			holdBalance(oldState.getLongOr("balance", s.getLongOr("balance", 0)));
		}
	}

	@Override
	public boolean holdsCelebration(String game) {
		return dev.nezo.burmaldaholic.games.extras.server.ExtrasGames.SCRATCH.equals(game) && state().getBooleanOr("done", false) && !celebrated;
	}

	private boolean anyStarted() {
		for (ScratchMask m : masks) if (!m.untouched()) return true;
		return false;
	}

	private long[] cells() {
		return state().getLongArray("cells").orElse(new long[0]);
	}

	private int cellX(int i) {
		return px + TX + 12 + (i % 3) * 64;
	}

	private int cellY(int i) {
		return py + TY + 36 + (i / 3) * 48;
	}

	private int cellAt(double mx, double my) {
		for (int i = 0; i < Scratch.CELLS; i++) {
			if (mx >= cellX(i) && mx < cellX(i) + CELL_W && my >= cellY(i) && my < cellY(i) + CELL_H) return i;
		}
		return -1;
	}

	private boolean playable() {
		CompoundTag s = state();
		return !s.getBooleanOr("done", false) && (!s.getStringOr("id", "").isEmpty() || s.getIntOr("fresh", 0) > 0);
	}

	private CompoundTag kindArgs() {
		CompoundTag t = new CompoundTag();
		t.putString("kind", state().getStringOr("kind", "basic"));
		t.putString("id", state().getStringOr("id", ""));
		return t;
	}

	/** First scratch of a covered cell: the server reveals it (and draws the card on its first scratch). */
	private void sendScratch(int cell) {
		if (sent[cell]) return;
		sent[cell] = true;
		CompoundTag args = kindArgs();
		args.putInt("cell", cell);
		send("scratch", args);
	}

	// ---- input ---------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && playable()) {
			int cell = cellAt(event.x(), event.y());
			if (cell >= 0 && dissolveAt[cell] < 0 && swipeAt[cell] < 0 && !masks[cell].isClear()) {
				dragCell = cell;
				lastX = event.x();
				lastY = event.y();
				dragDist = 0;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragCell >= 0 && event.button() == 0) {
			double x = event.x();
			double y = event.y();
			dragDist += Math.hypot(x - lastX, y - lastY);
			for (int i = 0; i < Scratch.CELLS; i++) {
				if (dissolveAt[i] >= 0 || swipeAt[i] >= 0 || masks[i].isClear()) continue;
				int n = masks[i].eraseLine(lastX - cellX(i), lastY - cellY(i), x - cellX(i), y - cellY(i), 6);
				if (n > 0) {
					sendScratch(i);
					scratchFeedback(x, y, n, i);
					if (masks[i].erased() >= ScratchMask.DISSOLVE_AT) dissolve(i);
				}
			}
			lastX = x;
			lastY = y;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragCell >= 0 && event.button() == 0) {
			int cell = dragCell;
			dragCell = -1;
			if (dragDist < 3 && masks[cell].untouched()) {
				// a click without a drag: auto-swipe
				swipeAt[cell] = Util.getMillis();
				sendScratch(cell);
			} else {
				for (int i = 0; i < Scratch.CELLS; i++) {
					if (!masks[i].untouched() && !masks[i].isClear() && dissolveAt[i] < 0) dissolve(i);
				}
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	private void dissolve(int cell) {
		dissolveAt[cell] = Util.getMillis();
		dissolveOrder[cell] = SeedMix.mix(SeedMix.hash(cardId), cell);
		sendScratch(cell);
	}

	private void scratchFeedback(double x, double y, int erased, int cell) {
		long now = Util.getMillis();
		if (!Kit.reduceMotion()) {
			ScratchDraw.spray(flakes, (float) x, (float) y, Math.min(3, erased), SeedMix.mix((int) now, cell), now, false);
		}
		if (now - lastScratchSound >= 125) {
			lastScratchSound = now;
			FxSounds.play("scratch", 0.8f, (float) (0.9 + 0.2 * ((now / 125) % 5) / 4.0));
		}
	}

	@Override
	protected boolean skip() {
		// Space / Enter: the keyboard path of the auto-swipe (next covered cell)
		if (!playable()) return false;
		for (int i = 0; i < Scratch.CELLS; i++) {
			if (!masks[i].isClear() && swipeAt[i] < 0 && dissolveAt[i] < 0) {
				swipeAt[i] = Util.getMillis();
				sendScratch(i);
				return true;
			}
		}
		return false;
	}

	// ---- per-frame reveal ------------------------------------------------------------------------------------------

	private void advance() {
		long now = Util.getMillis();
		float dt = lastFrame == 0 ? 16 : Math.min(50, now - lastFrame);
		lastFrame = now;
		flakes.step(now, dt);
		boolean rm = Kit.reduceMotion();
		for (int i = 0; i < Scratch.CELLS; i++) {
			ScratchMask m = masks[i];
			if (swipeAt[i] >= 0 && now >= swipeAt[i] && !m.isClear()) {
				double p = (now - swipeAt[i]) / (double) (rm ? 150 : ScratchMask.SWIPE_MS);
				if (rm || p >= 1) {
					m.clear();
				} else {
					double[] a = ScratchMask.swipePoint(CELL_W, CELL_H, Math.max(0, p - 0.08));
					double[] b = ScratchMask.swipePoint(CELL_W, CELL_H, p);
					int n = m.eraseLine(a[0], a[1], b[0], b[1], ScratchMask.swipeRadius(CELL_H));
					if (n > 0) scratchFeedback(cellX(i) + b[0], cellY(i) + b[1], n, i);
				}
			}
			if (dissolveAt[i] >= 0 && !m.isClear()) {
				double p = (now - dissolveAt[i]) / (double) ScratchMask.DISSOLVE_MS;
				int total = m.nx() * m.ny();
				int[] order = ScratchMask.dissolveOrder(total, dissolveOrder[i]);
				int upto = rm || p >= 1 ? total : (int) (p * total);
				for (int k = 0; k < upto; k++) {
					int t = order[k];
					if (m.erase(t % m.nx(), t / m.nx()) && !rm && k % 6 == 0) {
						ScratchDraw.spray(flakes, cellX(i) + (t % m.nx()) * 4, cellY(i) + (t / m.nx()) * 4, 1, SeedMix.mix(t, i), now, false);
					}
				}
				if (p >= 1) m.clear();
			}
		}
		// the end emphasis starts when the server says done and every cell is clear here
		if (state().getBooleanOr("done", false) && !ended) {
			boolean allClear = true;
			for (ScratchMask m : masks) allClear &= m.isClear();
			if (allClear) {
				ended = true;
				endAt = now;
				startEnd();
			}
		}
	}

	private int[] trio() {
		CompoundTag s = state();
		long[] cells = cells();
		long prize = s.getLongOr("prize", 0);
		boolean creeper = s.getBooleanOr("creeper", false);
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < cells.length && out.size() < 3; i++) {
			if ((prize > 0 && cells[i] == prize) || (prize == 0 && creeper && cells[i] == Scratch.CREEPER)) out.add(i);
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	private void startEnd() {
		CompoundTag s = state();
		long prize = s.getLongOr("prize", 0);
		if (s.getBooleanOr("creeper", false)) {
			Kit.vanilla("entity.creeper.primed", 1f, 1f);
		} else if (prize <= 0) {
			FxSounds.play("lose", 0.6f, 1f);
		}
	}

	private void endTick(long since) {
		CompoundTag s = state();
		long prize = s.getLongOr("prize", 0);
		int[] trio = trio();
		if (prize > 0) {
			while (chimes < trio.length && since >= chimes * 100L) {
				Kit.vanilla("block.amethyst_block.chime", 1f, new float[] {1f, 1.26f, 1.5f}[chimes]);
				chimes++;
			}
			if (!celebrated && since >= 700) {
				celebrated = true;
				releaseBalance();
				// the server's celebration (tier from the card's own price), held until the trio line is drawn
				dev.nezo.burmaldaholic.client.fx.ClientFx.releaseCelebration();
			}
		} else if (!celebrated && since >= 300) {
			celebrated = true;
			releaseBalance();
		}
		if (s.getBooleanOr("creeper", false) && since >= 1200 && minecraft != null) {
			onClose(); // the chaos mob wave starts (server)
		}
	}

	// ---- layout ------------------------------------------------------------------------------------------------

	@Override
	protected int[] chipCounterAt() {
		return new int[] {16, 6};
	}

	@Override
	protected Component sceneTitle() {
		return title(state());
	}

	@Override
	protected void layout() {
		CompoundTag s = state();
		boolean isGold = gold();
		button(RX - 4, 160, 73, 20, Component.translatable("gui.burmaldaholic.extras.scratch.kind.basic"), KitButton.Style.SECONDARY,
			b -> switchKind("basic")).selected(!isGold);
		button(RX + 71, 160, 73, 20, Component.translatable("gui.burmaldaholic.extras.scratch.kind.gold"), KitButton.Style.SECONDARY,
			b -> switchKind("gold")).selected(isGold);
		boolean done = s.getBooleanOr("done", false);
		button(RX - 4, 184, 148, 20, Component.translatable("gui.burmaldaholic.extras.scratch.all"), KitButton.Style.PRIMARY, b -> {
			send("all", kindArgs());
		}).active(playable());
		if (done && s.getIntOr("fresh", 0) > 0) {
			button(RX - 4, 208, 73, 20, Component.translatable("gui.burmaldaholic.common.play_again"), KitButton.Style.SECONDARY, b -> send("new", kindArgs()));
		} else {
			button(RX - 4, 208, 73, 20, Component.translatable("gui.burmaldaholic.extras.scratch.new", Texts.number(s.getLongOr("buy_price", 0))),
				KitButton.Style.SECONDARY, b -> send("buy", kindArgs())).active(s.getBooleanOr("can_buy", true))
				.tooltip(Component.translatable("gui.burmaldaholic.extras.scratch.buy", Texts.chipsAcc(s.getLongOr("buy_price", 0))));
		}
		button(RX + 71, 208, 73, 20, Component.translatable("gui.burmaldaholic.extras.leave"), KitButton.Style.SECONDARY, b -> onClose());
	}

	private void switchKind(String kind) {
		if (kind.equals(state().getStringOr("kind", "basic"))) return;
		CompoundTag t = new CompoundTag();
		t.putString("kind", kind);
		t.putString("id", "");
		send("new", t);
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		advance();
		long now = Util.getMillis();
		CompoundTag s = state();
		boolean isGold = gold();
		long since = ended ? now - endAt : -1;
		if (ended) endTick(since);
		boolean creeper = ended && s.getBooleanOr("creeper", false);
		int shake = creeper && !Kit.reduceMotion() && since < 1000 ? (int) Math.round(2 * Math.sin(since / 30.0)) : 0;
		int tx = px + TX + shake;
		int ty = py + TY;
		Kit.sprite(g, Kit.extras("ticket_shadow"), tx + 3, ty + 4, 212, 196);
		Kit.region(g, isGold ? TICKET_GOLD : TICKET_BASIC, 212, 196, 0, 0, 212, 196, tx, ty);
		long[] cells = cells();
		long[] prizes = prizeAmounts();
		int hover = playable() ? cellAt(mouseX, mouseY) : -1;
		int[] trio = ended ? trio() : new int[0];
		for (int i = 0; i < Scratch.CELLS; i++) {
			int x = cellX(i) + shake;
			int y = cellY(i);
			long v = i < cells.length ? cells[i] : -1;
			if (!masks[i].untouched() || masks[i].isClear()) {
				if (v >= 0) {
					int sym = symbolOf(v, prizes);
					boolean lit = ended && contains(trio, i) && s.getLongOr("prize", 0) > 0 && since >= indexIn(trio, i) * 100L;
					ScratchDraw.symbol40(g, sym, x + 10, y - 3, lit);
					if (v > 0) Kit.centered(g, font, Texts.number(v), x + 30, y + 35, 0xFF3A2010);
					Kit.region(g, ScratchDraw.SCUFF, 60, 44, 0, 0, 60, 44, x, y);
				} else {
					g.fill(x + 2, y + 2, x + CELL_W - 2, y + CELL_H - 2, 0xFF3A3040); // unknown: the value is on its way
				}
			}
			ScratchDraw.foil(g, masks[i], x, y, false, isGold ? 44 : 0, isGold ? ScratchDraw.ROW_GOLD : ScratchDraw.ROW_BASIC, true);
			if (hover == i && !masks[i].isClear()) {
				Kit.sprite(g, Kit.extras("scratch_foil_shimmer"), x, y, CELL_W, CELL_H);
				Kit.frameRect(g, x - 1, y - 1, CELL_W + 2, CELL_H + 2, Kit.GOLD);
			}
		}
		if (ended) endEmphasis(g, s, since, trio, tx, ty);
		// the title plaque and the hint ribbon
		Component name = Component.translatable(isGold ? "gui.burmaldaholic.extras.scratch.ticket.gold" : "gui.burmaldaholic.extras.scratch.ticket.basic")
			.withStyle(ChatFormatting.BOLD);
		Kit.centeredFit(g, font, name, tx + 106, ty + 15, 104, isGold ? 0xFF8C1834 : 0xFF0E5A3A, false);
		Component hint = ended ? endLine(s) : Component.translatable("gui.burmaldaholic.anim.scratch.drag_hint");
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(hint, 184);
		if (lines.size() == 1) {
			g.text(font, lines.get(0), tx + 106 - font.width(lines.get(0)) / 2, ty + 180, ended ? endColor(s) : Kit.BONE, true);
		} else {
			float sc = 0.75f;
			g.pose().pushMatrix();
			g.pose().translate(tx + 106, ty + 177);
			g.pose().scale(sc, sc);
			List<net.minecraft.util.FormattedCharSequence> l2 = font.split(hint, (int) (184 / sc));
			for (int k = 0; k < Math.min(2, l2.size()); k++) {
				g.text(font, l2.get(k), -font.width(l2.get(k)) / 2, k * 9, ended ? endColor(s) : Kit.BONE, true);
			}
			g.pose().popMatrix();
		}
		// right column: the prize table card
		Scene.card(g, px + RX - 4, py + 30, 148, 126);
	}

	private void endEmphasis(GuiGraphicsExtractor g, CompoundTag s, long since, int[] trio, int tx, int ty) {
		long prize = s.getLongOr("prize", 0);
		boolean rm = Kit.reduceMotion();
		if (prize > 0) {
			for (int k = 0; k < trio.length; k++) {
				if (since < k * 100L) continue;
				double p = rm ? 1 : Ease.OUT_BACK.apply(Math.min(1, (since - k * 100L) / 200.0));
				int x = cellX(trio[k]);
				int y = cellY(trio[k]);
				int grow = (int) Math.round(3 * (1 - p));
				Kit.sprite(g, Kit.extras("trio_frame"), x - 2 - grow, y - 2 - grow, CELL_W + 4 + 2 * grow, CELL_H + 4 + 2 * grow);
			}
			if (trio.length == 3 && since >= 300) {
				double p = rm ? 1 : Math.min(1, (since - 300) / 300.0);
				for (int k = 0; k < 2; k++) {
					double x0 = cellX(trio[k]) + CELL_W / 2.0;
					double y0 = cellY(trio[k]) + CELL_H / 2.0;
					double x1 = cellX(trio[k + 1]) + CELL_W / 2.0;
					double y1 = cellY(trio[k + 1]) + CELL_H / 2.0;
					double seg = Math.max(0, Math.min(1, p * 2 - k));
					if (seg > 0) Kit.line(g, x0, y0, x0 + (x1 - x0) * seg, y0 + (y1 - y0) * seg, 1, Kit.GOLD);
				}
			}
			if (s.getBooleanOr("top", false) && !rm && Kit.flashes() && since >= 300 && since < 1100) {
				// the holographic sweep over the ticket (800 ms, diagonal band)
				double p = (since - 300) / 800.0;
				int band = (int) (-60 + p * 332);
				for (int k = 0; k < 24; k++) {
					int hue = Kit.lerp(0x55FF60C0, 0x5560E0FF, k / 24.0);
					g.fill(tx + band + k * 2, ty + 4, tx + band + k * 2 + 2, ty + 192, hue);
				}
			}
		} else if (s.getBooleanOr("creeper", false)) {
			double p = rm ? 1 : Math.min(1, since / 600.0);
			int inset = (int) Math.round(40 * (1 - p));
			Kit.sprite(g, Kit.extras("char_vignette"), tx - inset, ty - inset, 212 + 2 * inset, 196 + 2 * inset);
			if (Kit.flashes() && !rm && (since / 200) % 2 == 0) {
				for (int i : trio) g.fill(cellX(i), cellY(i), cellX(i) + CELL_W, cellY(i) + CELL_H, 0x3040FF40);
			}
		} else {
			// loss: grey cells, the corner folds down
			for (int i = 0; i < Scratch.CELLS; i++) g.fill(cellX(i), cellY(i), cellX(i) + CELL_W, cellY(i) + CELL_H, 0x55303030);
			int f = rm ? 2 : (int) Math.min(2, since / 70);
			Kit.region(g, ScratchDraw.TORN, 72, 24, f * 24, 0, 24, 24, tx + 212 - 24, ty);
		}
	}

	private Component endLine(CompoundTag s) {
		long prize = s.getLongOr("prize", 0);
		if (prize > 0) {
			return s.getBooleanOr("top", false) ? Component.translatable("gui.burmaldaholic.extras.scratch.top_prize", Texts.chips(prize))
				: Component.translatable("gui.burmaldaholic.extras.scratch.win", Texts.chips(prize));
		}
		if (s.getBooleanOr("creeper", false)) return Component.translatable("gui.burmaldaholic.extras.scratch.creeper");
		return Component.translatable("gui.burmaldaholic.extras.scratch.lose");
	}

	private int endColor(CompoundTag s) {
		return s.getLongOr("prize", 0) > 0 ? Kit.GOLD : s.getBooleanOr("creeper", false) ? Kit.CURSE : Kit.RED_LIGHT;
	}

	private static boolean contains(int[] a, int v) {
		for (int x : a) if (x == v) return true;
		return false;
	}

	private static int indexIn(int[] a, int v) {
		for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
		return 0;
	}

	/** Prize amounts ascending (the server's table), or the distinct revealed values when the table is absent. */
	private long[] prizeAmounts() {
		ListTag list = state().getListOrEmpty("prizes");
		long[] out = new long[list.size()];
		for (int i = 0; i < out.length; i++) out[i] = list.getCompoundOrEmpty(i).getLongOr("a", 0);
		return out;
	}

	/** Symbol of a cell value: creeper, else the prize's rank (lowest = coal … top = nether star; longer lists wrap to the top icon). */
	static int symbolOf(long v, long[] prizes) {
		if (v == Scratch.CREEPER) return ScratchDraw.CREEPER;
		int rank = 0;
		for (long p : prizes) if (p < v) rank++;
		return Math.min(5, rank);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		CompoundTag s = state();
		ListTag list = s.getListOrEmpty("prizes");
		int rx = px + RX;
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.scratch.match3"), rx + 2, py + 36, 138, Kit.GOLD, true);
		int shown = Math.min(6, list.size());
		for (int k = 0; k < shown; k++) {
			CompoundTag p = list.getCompoundOrEmpty(list.size() - 1 - k);
			long amount = p.getLongOr("a", 0);
			double prob = p.getDoubleOr("p", 0);
			int yy = py + 48 + k * 15;
			ScratchDraw.symbol20(g, Math.min(5, list.size() - 1 - k), rx - 2, yy - 5);
			Kit.text(g, font, Texts.number(amount), rx + 22, yy + 1, k == 0 ? Kit.GOLD : Kit.BONE);
			if (prob > 0) {
				Component odds = Component.translatable("gui.burmaldaholic.extras.scratch.odds", Texts.number(Math.round(1 / prob)));
				Kit.right(g, font, odds, rx + 140, yy + 1, Kit.BONE_SHADE);
			}
		}
		ScratchDraw.symbol20(g, ScratchDraw.CREEPER, rx - 2, py + 135);
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.scratch.creeper_rule"), rx + 22, py + 141, 118, Kit.CURSE, true);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ScratchDraw.flakes(g, flakes, Util.getMillis());
		boolean overTicket = mouseX >= px + TX && mouseX < px + TX + 212 && mouseY >= py + TY && mouseY < py + TY + 196;
		if (overTicket && playable()) {
			Kit.sprite(g, Kit.extras("scraper"), mouseX - 2, mouseY - 12, 16, 16);
		}
	}
}
