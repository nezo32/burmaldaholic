package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.GuiParticlePool;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.logic.anim.ScratchMask;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasKeys;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasLayout;
import dev.nezo.burmaldaholic.games.extras.present.ScratchFlow;
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
 * Scratch Cards, table UI v2 (REDESIGN.md §7.7 "Scratch: ticket centred, prize table right"; lane E): the kit's TopBar
 * and Dock (no BetBar — a ticket has a price; the Dock offers Basic / Golden; primary <b>Scratch all</b> → <b>Next card</b> → <b>Buy (price)</b> by
 * {@link ScratchFlow}) and the kiosk scene: the "Lucky Miner" / "Gold Rush" ticket with its 3 × 3 foil cells on the teal
 * fill, the options column (the prize table with odds, the creeper rule). Hold and drag to
 * scratch (the Lucky Coin scraper erases 4 × 4 sub-tiles, flakes spray); a click auto-swipes a cell; Enter swipes the
 * next. Honest emphasis: nothing is highlighted until the server says the ticket is done — a win frames the trio, the top
 * prize sweeps, three creepers char the edges and close the screen for the mob wave, a loss greys the cells.
 *
 * <p>Hit map ids: {@code felt.cell.<0..8>}, {@code action.kind.basic}, {@code action.kind.gold}, plus the kit's ids.
 */
final class ScratchScreen extends ExtrasSceneScreen<ScratchFlow.View> implements dev.nezo.burmaldaholic.client.fx.ClientFx.CelebrationGate {
	private static final Identifier TICKET_BASIC = Kit.sheet("extras/scratch_ticket_basic");
	private static final Identifier TICKET_GOLD = Kit.sheet("extras/scratch_ticket_gold");
	private static final int CELL_W = 60;
	private static final int CELL_H = 44;
	private static final int SWIPE_GAP = 120;

	private final ScratchMask[] masks = new ScratchMask[Scratch.CELLS];
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
	private double downX;
	private double downY;
	private long lastScratchSound;
	private long lastFrame;
	private long endAt = -1;
	private boolean ended;
	private boolean celebrated;
	private int chimes;
	private ExtrasLayout.Scene scene;

	ScratchScreen(CompoundTag state) {
		super("scratch", title(state), state);
		for (int i = 0; i < Scratch.CELLS; i++) masks[i] = ScratchMask.solo();
		resetCard(state);
		chrome.keys(KeyMap.standard().bind(KeyMap.ENTER, KeyMap.Ids.felt("next")));
	}

	private static Component title(CompoundTag s) {
		return Component.translatable("gold".equals(s.getStringOr("kind", "basic")) ? "gui.burmaldaholic.extras.scratch.title_gold"
			: "gui.burmaldaholic.extras.scratch.title");
	}

	private boolean gold() {
		return "gold".equals(state().getStringOr("kind", "basic"));
	}

	// ---- the pure parts ------------------------------------------------------------------------------------------------

	@Override
	protected ScratchFlow.View view(CompoundTag s) {
		boolean revealing = s.getBooleanOr("done", false) && !celebrated;
		return new ScratchFlow.View(playable(s), revealing, s.getIntOr("fresh", 0), s.getLongOr("buy_price", 0), s.getBooleanOr("can_buy", true),
			shownBalance(), "gold".equals(s.getStringOr("kind", "basic")));
	}

	@Override
	protected Flow<ScratchFlow.View> flow() {
		return ScratchFlow.INSTANCE;
	}

	@Override
	public void onGate(String id, Gate gate) {
		switch (gate.action()) {
			case ScratchFlow.ALL -> send("all", kindArgs());
			case ScratchFlow.NEW -> send("new", kindArgs());
			case ScratchFlow.BUY -> send("buy", kindArgs());
			case ScratchFlow.BASIC -> switchKind("basic");
			case ScratchFlow.GOLD -> switchKind("gold");
			default -> super.onGate(id, gate);
		}
	}

	@Override
	public void sendRaw(String action, CompoundTag args) {
		if (action.equals("resync")) {
			super.sendRaw("refresh", kindArgs());
			return;
		}
		super.sendRaw(action, args);
	}

	// ---- the card ---------------------------------------------------------------------------------------------------

	private void resetCard(CompoundTag s) {
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
			cardId = id;
			newCard = false;
		}
		if (newCard) {
			resetCard(s);
			return;
		}
		int mask = s.getIntOr("mask", 0);
		long now = Util.getMillis();
		int k = 0;
		for (int i = 0; i < Scratch.CELLS; i++) {
			if ((mask >> i & 1) == 1 && !sent[i]) sent[i] = true;
			if ((mask >> i & 1) == 1 && masks[i].untouched() && swipeAt[i] < 0 && dissolveAt[i] < 0) swipeAt[i] = now + (long) k++ * SWIPE_GAP;
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

	/** Ticket-local cell origin. */
	private static int cellX(int i) {
		return 12 + (i % 3) * 64;
	}

	private static int cellY(int i) {
		return 36 + (i / 3) * 48;
	}

	private float scale() {
		return scene == null ? 1f : scene.scale();
	}

	/** GUI → ticket-local. */
	private double lx(double x) {
		return (x - scene.object().x()) / scale();
	}

	private double ly(double y) {
		return (y - scene.object().y()) / scale();
	}

	/** Ticket-local → GUI. */
	private float gx(double x) {
		return (float) (scene.object().x() + x * scale());
	}

	private float gy(double y) {
		return (float) (scene.object().y() + y * scale());
	}

	private Box cellBox(int i) {
		int x = Math.round(gx(cellX(i)));
		int y = Math.round(gy(cellY(i)));
		return new Box(x, y, Math.round(CELL_W * scale()), Math.round(CELL_H * scale()));
	}

	private int cellAt(double mx, double my) {
		if (scene == null) return -1;
		double x = lx(mx);
		double y = ly(my);
		for (int i = 0; i < Scratch.CELLS; i++) {
			if (x >= cellX(i) && x < cellX(i) + CELL_W && y >= cellY(i) && y < cellY(i) + CELL_H) return i;
		}
		return -1;
	}

	private static boolean playable(CompoundTag s) {
		return !s.getBooleanOr("done", false) && (!s.getStringOr("id", "").isEmpty() || s.getIntOr("fresh", 0) > 0);
	}

	private boolean playable() {
		return playable(state());
	}

	private CompoundTag kindArgs() {
		CompoundTag t = new CompoundTag();
		t.putString("kind", state().getStringOr("kind", "basic"));
		t.putString("id", state().getStringOr("id", ""));
		return t;
	}

	private void sendScratch(int cell) {
		if (sent[cell]) return;
		sent[cell] = true;
		CompoundTag args = kindArgs();
		args.putInt("cell", cell);
		send("scratch", args);
	}

	private void switchKind(String kind) {
		if (kind.equals(state().getStringOr("kind", "basic"))) return;
		CompoundTag t = new CompoundTag();
		t.putString("kind", kind);
		t.putString("id", "");
		send("new", t);
	}

	// ---- input ---------------------------------------------------------------------------------------------------

	@Override
	protected void layoutPlay(TableGrid grid, ScratchFlow.View v, HitMap hits) {
		scene = ExtrasLayout.scratch(grid);
		for (int i = 0; i < Scratch.CELLS; i++) hits.add(KeyMap.Ids.felt("cell." + i), cellBox(i), v.playable());
		Box o = scene.object();
		chrome.banner.regions(List.of(new Box(o.x() - 10, o.y() + o.h() / 2 - 20, o.w() + 20, 40)), List.of());
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		if (id.equals(KeyMap.Ids.felt("next"))) return swipeNext();
		if (id.startsWith("felt.cell.") && button.left()) {
			try {
				return startStroke(Integer.parseInt(id.substring("felt.cell.".length())), enabled);
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		downX = event.x();
		downY = event.y();
		return super.mouseClicked(event, doubleClick); // the chrome routes a cell press to onTarget(felt.cell.<i>)
	}

	/** A press on a covered cell starts a scratch stroke there (a click without a drag auto-swipes on release). */
	private boolean startStroke(int cell, boolean enabled) {
		if (!enabled || !playable() || cell < 0 || dissolveAt[cell] >= 0 || swipeAt[cell] >= 0 || masks[cell].isClear()) return enabled;
		dragCell = cell;
		lastX = lx(downX);
		lastY = ly(downY);
		dragDist = 0;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragCell >= 0 && Inputs.isLeft(event.button())) {
			double x = lx(event.x());
			double y = ly(event.y());
			dragDist += Math.hypot(x - lastX, y - lastY);
			for (int i = 0; i < Scratch.CELLS; i++) {
				if (dissolveAt[i] >= 0 || swipeAt[i] >= 0 || masks[i].isClear()) continue;
				int n = masks[i].eraseLine(lastX - cellX(i), lastY - cellY(i), x - cellX(i), y - cellY(i), 6);
				if (n > 0) {
					sendScratch(i);
					scratchFeedback(event.x(), event.y(), n, i);
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
		if (dragCell >= 0 && Inputs.isLeft(event.button())) {
			int cell = dragCell;
			dragCell = -1;
			if (dragDist < 3 && masks[cell].untouched()) {
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
		if (!Kit.reduceMotion()) ScratchDraw.spray(flakes, (float) x, (float) y, Math.min(3, erased), SeedMix.mix((int) now, cell), now, false);
		if (now - lastScratchSound >= 125) {
			lastScratchSound = now;
			FxSounds.play("scratch", 0.8f, (float) (0.9 + 0.2 * ((now / 125) % 5) / 4.0));
		}
	}

	/** Enter: the keyboard path of the auto-swipe (next covered cell). */
	private boolean swipeNext() {
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
					if (n > 0) scratchFeedback(gx(cellX(i) + b[0]), gy(cellY(i) + b[1]), n, i);
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
						ScratchDraw.spray(flakes, gx(cellX(i) + (t % m.nx()) * 4), gy(cellY(i) + (t / m.nx()) * 4), 1, SeedMix.mix(t, i), now, false);
					}
				}
				if (p >= 1) m.clear();
			}
		}
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
		if (s.getBooleanOr("creeper", false)) Kit.vanilla("entity.creeper.primed", 1f, 1f);
		else if (prize <= 0) FxSounds.play("lose", 0.6f, 1f);
	}

	private void endTick(long since) {
		CompoundTag s = state();
		long prize = s.getLongOr("prize", 0);
		int[] trio = trio();
		boolean was = celebrated;
		if (prize > 0) {
			while (chimes < trio.length && since >= chimes * 100L) {
				Kit.vanilla("block.amethyst_block.chime", 1f, new float[] {1f, 1.26f, 1.5f}[chimes]);
				chimes++;
			}
			if (!celebrated && since >= 700) {
				celebrated = true;
				releaseBalance();
				dev.nezo.burmaldaholic.client.fx.ClientFx.releaseCelebration();
			}
		} else if (!celebrated && since >= 300) {
			celebrated = true;
			releaseBalance();
		}
		if (!was && celebrated) {
			chrome.status.hint(endLine(s));
			local();
		}
		if (s.getBooleanOr("creeper", false) && since >= 1200 && minecraft != null) onClose(); // the chaos mob wave starts (server)
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, ScratchFlow.View v, int mouseX, int mouseY, float partial) {
		if (scene == null) scene = ExtrasLayout.scratch(grid);
		SceneKit.fill(g, grid.play, "scratch");
		advance();
		long now = Util.getMillis();
		CompoundTag s = state();
		boolean isGold = gold();
		long since = ended ? now - endAt : -1;
		if (ended) endTick(since);
		boolean creeper = ended && s.getBooleanOr("creeper", false);
		int shake = creeper && !Kit.reduceMotion() && since < 1000 ? (int) Math.round(2 * Math.sin(since / 30.0)) : 0;
		Box o = scene.object();
		g.pose().pushMatrix();
		g.pose().translate(o.x() + shake, o.y());
		g.pose().scale(scale(), scale());
		Kit.sprite(g, Kit.extras("ticket_shadow"), 3, 4, 212, 196);
		Kit.region(g, isGold ? TICKET_GOLD : TICKET_BASIC, 212, 196, 0, 0, 212, 196, 0, 0);
		long[] cells = cells();
		long[] prizes = prizeAmounts();
		int hover = playable() ? cellAt(mouseX, mouseY) : -1;
		int[] trio = ended ? trio() : new int[0];
		for (int i = 0; i < Scratch.CELLS; i++) {
			int x = cellX(i);
			int y = cellY(i);
			long val = i < cells.length ? cells[i] : -1;
			if (!masks[i].untouched() || masks[i].isClear()) {
				if (val >= 0) {
					int sym = symbolOf(val, prizes);
					boolean lit = ended && contains(trio, i) && s.getLongOr("prize", 0) > 0 && since >= indexIn(trio, i) * 100L;
					ScratchDraw.symbol40(g, sym, x + 10, y - 3, lit);
					if (val > 0) Kit.centered(g, font, Texts.number(val), x + 30, y + 35, 0xFF3A2010);
					Kit.region(g, ScratchDraw.SCUFF, 60, 44, 0, 0, 60, 44, x, y);
				} else {
					g.fill(x + 2, y + 2, x + CELL_W - 2, y + CELL_H - 2, 0xFF3A3040);
				}
			}
			ScratchDraw.foil(g, masks[i], x, y, false, isGold ? 44 : 0, isGold ? ScratchDraw.ROW_GOLD : ScratchDraw.ROW_BASIC, true);
			if (hover == i && !masks[i].isClear()) {
				Kit.sprite(g, Kit.extras("scratch_foil_shimmer"), x, y, CELL_W, CELL_H);
				Kit.frameRect(g, x - 1, y - 1, CELL_W + 2, CELL_H + 2, Kit.GOLD);
			}
		}
		if (ended) endEmphasis(g, s, since, trio);
		Component name = Component.translatable(isGold ? "gui.burmaldaholic.extras.scratch.ticket.gold" : "gui.burmaldaholic.extras.scratch.ticket.basic")
			.withStyle(ChatFormatting.BOLD);
		Kit.centeredFit(g, font, name, 106, 15, 104, isGold ? 0xFF8C1834 : 0xFF0E5A3A, false);
		Component hint = ended ? endLine(s) : Component.translatable("gui.burmaldaholic.anim.scratch.drag_hint");
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(hint, 184);
		if (lines.size() == 1) {
			g.text(font, lines.get(0), 106 - font.width(lines.get(0)) / 2, 180, ended ? endColor(s) : Kit.BONE, true);
		} else {
			float sc = 0.75f;
			List<net.minecraft.util.FormattedCharSequence> l2 = font.split(hint, (int) (184 / sc));
			g.pose().pushMatrix();
			// centred on the ribbon (rows 178–190 of the ticket art): one scaled line sits mid-band, two fill it
			g.pose().translate(106, l2.size() == 1 ? 181.5f : 177.5f);
			g.pose().scale(sc, sc);
			for (int k = 0; k < Math.min(2, l2.size()); k++) g.text(font, l2.get(k), -font.width(l2.get(k)) / 2, k * 9, ended ? endColor(s) : Kit.BONE, true);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
		side(g, v, mouseX, mouseY);
	}

	private void endEmphasis(GuiGraphicsExtractor g, CompoundTag s, long since, int[] trio) {
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
				double p = (since - 300) / 800.0;
				int band = (int) (-60 + p * 332);
				for (int k = 0; k < 24; k++) {
					int hue = Kit.lerp(0x55FF60C0, 0x5560E0FF, k / 24.0);
					g.fill(band + k * 2, 4, band + k * 2 + 2, 192, hue);
				}
			}
		} else if (s.getBooleanOr("creeper", false)) {
			double p = rm ? 1 : Math.min(1, since / 600.0);
			int inset = (int) Math.round(40 * (1 - p));
			Kit.sprite(g, Kit.extras("char_vignette"), -inset, -inset, 212 + 2 * inset, 196 + 2 * inset);
			if (Kit.flashes() && !rm && (since / 200) % 2 == 0) {
				for (int i : trio) g.fill(cellX(i), cellY(i), cellX(i) + CELL_W, cellY(i) + CELL_H, 0x3040FF40);
			}
		} else {
			for (int i = 0; i < Scratch.CELLS; i++) g.fill(cellX(i), cellY(i), cellX(i) + CELL_W, cellY(i) + CELL_H, 0x55303030);
			int f = rm ? 2 : (int) Math.min(2, since / 70);
			Kit.region(g, ScratchDraw.TORN, 72, 24, f * 24, 0, 24, 24, 212 - 24, 0);
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

	private long[] prizeAmounts() {
		ListTag list = state().getListOrEmpty("prizes");
		long[] out = new long[list.size()];
		for (int i = 0; i < out.length; i++) out[i] = list.getCompoundOrEmpty(i).getLongOr("a", 0);
		return out;
	}

	static int symbolOf(long v, long[] prizes) {
		if (v == Scratch.CREEPER) return ScratchDraw.CREEPER;
		int rank = 0;
		for (long p : prizes) if (p < v) rank++;
		return Math.min(5, rank);
	}

	/** The options column: ticket kind toggles, the prize table (symbol, amount, odds), the creeper rule. */
	private void side(GuiGraphicsExtractor g, ScratchFlow.View v, int mx, int my) {
		Box side = scene.side();
		int y = side.y() + 2;
		SceneKit.caption(g, font, Component.translatable(ExtrasKeys.PRIZES), side, y);
		ListTag list = state().getListOrEmpty("prizes");
		int rowH = 16;
		int shown = Math.min(6, Math.max(0, (side.bottom() - y - 14 - 20) / rowH));
		shown = Math.min(shown, list.size());
		for (int k = 0; k < shown; k++) {
			CompoundTag p = list.getCompoundOrEmpty(list.size() - 1 - k);
			long amount = p.getLongOr("a", 0);
			double prob = p.getDoubleOr("p", 0);
			int yy = y + 14 + k * rowH;
			ScratchDraw.symbol20(g, Math.min(5, list.size() - 1 - k), side.x() - 2, yy - 5);
			g.text(font, Texts.number(amount), side.x() + 22, yy + 1, k == 0 ? CasinoPalette.V2_GOLD : CasinoPalette.V2_TEXT, true);
			if (prob > 0) {
				Component odds = Component.translatable("gui.burmaldaholic.extras.scratch.odds", Texts.number(Math.round(1 / prob)));
				g.text(font, odds, side.right() - font.width(odds), yy + 1, CasinoPalette.V2_MUTED, true);
			}
		}
		int cy = y + 14 + shown * rowH + 2;
		ScratchDraw.symbol20(g, ScratchDraw.CREEPER, side.x() - 2, cy - 5);
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.extras.scratch.creeper_rule"), new Box(side.x() + 22, cy, side.w() - 22, 10), 0, 1,
			Kit.CURSE, true);
	}

	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, ScratchFlow.View v, int mouseX, int mouseY, float partial) {
		ScratchDraw.flakes(g, flakes, Util.getMillis());
		if (scene != null && scene.object().contains(mouseX, mouseY) && playable()) Kit.sprite(g, Kit.extras("scraper"), mouseX - 2, mouseY - 12, 16, 16);
	}
}
