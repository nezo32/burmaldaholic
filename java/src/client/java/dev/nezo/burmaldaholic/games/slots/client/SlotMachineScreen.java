package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.logic.Paylines;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Slot machine screen (UI.md §6): 3×3 reel window with the active paylines, jackpot meter, line bet
 * {@code [−] 5 [+]}, total bet, Spin, Auto ×10 / Stop and a scrollable paytable overlay. The server
 * decides every outcome; this screen only animates the grid it receives (reels stop left→right over
 * {@code slots.spinTicks}), then flashes the winning lines and rolls the payout counter up.
 *
 * <p>Layout adapts to the (possibly Russian) label widths: the right-hand panel is as wide as its
 * widest label, and all long texts wrap.
 */
public class SlotMachineScreen extends CasinoTableScreen {
	private static final int PAD = 10;
	private static final int CELL = 44;
	private static final int WINDOW = CELL * 3;
	private static final int REEL_X = PAD;
	private static final int REEL_Y = 32;
	private static final int PANEL_X = REEL_X + WINDOW + 10;
	private static final int HEIGHT = 256;
	/** Bottom of the status lines; the Slot Showdown entry button sits below. */
	private static final int STATUS_BOTTOM = 210;
	private static final int RESULT_Y = 180;
	private static final int GOLD = 0xFFFFD24A;
	private static final int GREEN = 0xFF55FF55;
	private static final int GRAY = 0xFFB0B0B0;
	private static final int[] LINE_COLORS = {0xFFFF5555, 0xFFFFFF55, 0xFF55FFFF, 0xFFFF55FF, 0xFFFFAA00};
	private static final long ROLL_MS = 900;
	private static final long FLASH_MS = 3000;

	private final int panelWidth;
	private final dev.nezo.burmaldaholic.games.slots.client.pvp.ShowdownPanel showdown = new dev.nezo.burmaldaholic.games.slots.client.pvp.ShowdownPanel(
		new dev.nezo.burmaldaholic.games.slots.client.pvp.ShowdownPanel.Host() {
			@Override
			public net.minecraft.client.gui.components.Button button(Component label, int x, int y, int minWidth,
					net.minecraft.client.gui.components.Button.OnPress onPress) {
				return SlotMachineScreen.this.button(label, x, y, minWidth, onPress);
			}

			@Override
			public void send(String action, CompoundTag args) {
				sendAction(action, args);
			}

			@Override
			public CompoundTag state() {
				return SlotMachineScreen.this.state();
			}

			@Override
			public void rebuild() {
				rebuildWidgets();
			}

			@Override
			public Font font() {
				return SlotMachineScreen.this.font;
			}
		});

	private long lineBet = -1;
	private boolean firstState = true;
	private boolean showPaytable;
	private int paytableScroll;

	// animation
	private int animSeq = -1;
	private long animStart;
	private long animDuration;
	private int reelsStopped = 3;
	private int[] grid = defaultGrid();
	private final int[][] strips = new int[3][];
	// result reveal
	private int revealedSeq = -1;
	private long revealStart;
	private @Nullable CompoundTag shownResult;

	public SlotMachineScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_X + panelWidth(Minecraft.getInstance().font) + PAD, HEIGHT);
		this.panelWidth = panelWidth(Minecraft.getInstance().font);
		this.titleLabelX = PAD;
		this.titleLabelY = 7;
	}

	/** Widest right-panel label (+ padding) with worst-case numbers, so Russian never truncates. */
	private static int panelWidth(Font font) {
		String big = "999 999";
		List<Component> labels = List.of(
			Component.translatable("gui.burmaldaholic.slots.line_bet", Texts.raw(big)),
			Component.translatable("gui.burmaldaholic.common.limits", Texts.raw(big), Texts.raw(big)),
			Component.translatable("gui.burmaldaholic.slots.lines", Texts.raw("5")),
			Component.translatable("gui.burmaldaholic.common.total_bet", Texts.chips(999_999)),
			Component.translatable("gui.burmaldaholic.slots.spin", Texts.chips(999_999)),
			Component.translatable("gui.burmaldaholic.slots.auto"),
			Component.translatable("gui.burmaldaholic.slots.stop_auto"),
			Component.translatable("gui.burmaldaholic.common.paytable"),
			Component.translatable("gui.burmaldaholic.common.back"),
			Component.translatable("gui.burmaldaholic.common.balance", Texts.raw("99 999 999")));
		int w = 0;
		for (Component c : labels) {
			w = Math.max(w, font.width(c));
		}
		return Math.max(100, Math.min(220, w + 10));
	}

	private static int[] defaultGrid() {
		return new int[] {
			Symbol.APPLE.ordinal(), Symbol.EMERALD.ordinal(), Symbol.BERRIES.ordinal(),
			Symbol.SEVEN.ordinal(), Symbol.SEVEN.ordinal(), Symbol.SEVEN.ordinal(),
			Symbol.DIAMOND.ordinal(), Symbol.GOLDEN_CARROT.ordinal(), Symbol.APPLE.ordinal()};
	}

	// ---- state ----------------------------------------------------------------------------------

	private int lines() {
		return Math.max(1, Math.min(Paylines.MAX, state().getIntOr("lines", 1)));
	}

	private long lineMin() {
		return state().getLongOr("line_min", 1);
	}

	private long lineMax() {
		return state().getLongOr("line_max", 1);
	}

	private boolean playable() {
		return lineMax() >= lineMin();
	}

	private boolean autoMine() {
		return state().getCompoundOrEmpty("auto").getBooleanOr("mine", false);
	}

	private boolean autoRunning() {
		return state().contains("auto");
	}

	private boolean animating() {
		return reelsStopped < 3;
	}

	private boolean busy() {
		return animating() || state().contains("spin") || autoRunning();
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	@Override
	protected void onStateChanged(CompoundTag s) {
		if (lineBet < 0 || lineBet < s.getLongOr("line_min", 1) || lineBet > Math.max(s.getLongOr("line_min", 1), s.getLongOr("line_max", 1))) {
			lineBet = s.getLongOr("line_bet", s.getLongOr("line_min", 1));
		}
		CompoundTag spin = s.getCompoundOrEmpty("spin");
		CompoundTag result = s.contains("result") ? s.getCompoundOrEmpty("result") : null;
		if (s.contains("spin")) {
			int seq = spin.getIntOr("seq", 0);
			if (seq != animSeq) {
				startAnimation(seq, spin.getIntArray("grid").orElse(grid), Math.max(250, spin.getLongOr("ticks_left", 50) * 50));
			}
		} else if (result != null) {
			int seq = result.getIntOr("seq", 0);
			if (firstState) {
				// opened after the spin: show it as it is, no sounds
				animSeq = seq;
				grid = validGrid(result.getIntArray("grid").orElse(grid));
				reelsStopped = 3;
				revealedSeq = seq;
				revealStart = now() - FLASH_MS - ROLL_MS;
				shownResult = result;
			} else if (seq != animSeq) {
				// settled without our animation (e.g. the player left): reveal at once
				animSeq = seq;
				grid = validGrid(result.getIntArray("grid").orElse(grid));
				reelsStopped = 3;
			}
		}
		if (s.contains("tier")) {
			firstState = false;
		}
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	private static int[] validGrid(int[] g) {
		return g.length == 9 ? g : defaultGrid();
	}

	private void startAnimation(int seq, int[] target, long durationMs) {
		animSeq = seq;
		animStart = now();
		animDuration = durationMs;
		reelsStopped = 0;
		grid = validGrid(target);
		List<Integer> symbols = paytableSymbols();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int c = 0; c < 3; c++) {
			strips[c] = new int[24];
			for (int i = 0; i < strips[c].length; i++) {
				strips[c][i] = symbols.get(r.nextInt(symbols.size()));
			}
		}
		playSound(1.0f);
	}

	private List<Integer> paytableSymbols() {
		List<Integer> out = new ArrayList<>();
		ListTag pays = state().getListOrEmpty("paytable");
		for (int i = 0; i < pays.size(); i++) {
			out.add(pays.getCompoundOrEmpty(i).getIntOr("symbol", 0));
		}
		if (out.isEmpty()) {
			for (int g : defaultGrid()) {
				out.add(g);
			}
		}
		return out;
	}

	private long reelStopAt(int col) {
		return switch (col) {
			case 0 -> animDuration / 2;
			case 1 -> animDuration * 3 / 4;
			default -> animDuration;
		};
	}

	/** Advances the animation clock; reveals the result once all reels stopped and it has arrived. */
	private void updateAnimation() {
		if (animating()) {
			long elapsed = now() - animStart;
			while (reelsStopped < 3 && elapsed >= reelStopAt(reelsStopped)) {
				reelsStopped++;
				playSound(0.8f + 0.2f * reelsStopped);
			}
			if (!animating() && minecraft != null) {
				rebuildWidgets();
			}
		}
		CompoundTag result = state().contains("result") ? state().getCompoundOrEmpty("result") : null;
		if (!animating() && result != null && result.getIntOr("seq", 0) == animSeq && revealedSeq != animSeq) {
			revealedSeq = animSeq;
			revealStart = now();
			shownResult = result;
			onReveal(result);
		}
	}

	private void onReveal(CompoundTag result) {
		if (minecraft == null) {
			return;
		}
		long total = result.getLongOr("total", 0);
		long spinBet = Math.max(1, result.getLongOr("spin_bet", 1));
		if (result.getLongOr("award", 0) > 0) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
		} else if (result.getBooleanOr("sevens", false) || total >= 20 * spinBet) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f));
		} else if (total > 0) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
		}
	}

	private void playSound(float pitch) {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(dev.nezo.burmaldaholic.core.CoreSounds.SLOT_SPIN, pitch));
		}
	}

	// ---- widgets --------------------------------------------------------------------------------

	// init(): CasinoTableScreen.init() hands us the cached state -> onStateChanged -> rebuildWidgets().

	@Override
	protected void rebuildWidgets() {
		clearWidgets();
		buildWidgets();
	}

	private void buildWidgets() {
		if (showdown.open()) { // Slot Showdown panel (games/slots/client/pvp)
			showdown.buildWidgets(PANEL_X, panelWidth, REEL_X, PANEL_X - REEL_X - 6, 200);
			return;
		}
		if (showPaytable) {
			button(Component.translatable("gui.burmaldaholic.common.back"), PANEL_X, 156, panelWidth, b -> {
				showPaytable = false;
				rebuildWidgets();
			});
			return;
		}
		boolean canBet = !busy() && playable();
		var minus = button(Texts.raw("−"), PANEL_X, 46, 20, b -> changeBet(-1)); // literal-ok: symbol
		var plus = button(Texts.raw("+"), PANEL_X + 24, 46, 20, b -> changeBet(1)); // literal-ok: symbol
		minus.active = canBet && lineBet > lineMin();
		plus.active = canBet && lineBet < lineMax();
		boolean ready = !busy() && playable() && state().getBooleanOr("vip_ok", true) && state().getBooleanOr("enabled", true);
		var spin = button(Component.translatable("gui.burmaldaholic.slots.spin", Texts.chips(spinBet())), PANEL_X, 108, panelWidth, b -> {
			CompoundTag args = new CompoundTag();
			args.putLong("line_bet", lineBet);
			sendAction("spin", args);
		});
		spin.active = ready;
		if (autoMine()) {
			button(Component.translatable("gui.burmaldaholic.slots.stop_auto"), PANEL_X, 132, panelWidth, b -> sendAction("stop_auto"));
		} else {
			var auto = button(Component.translatable("gui.burmaldaholic.slots.auto"), PANEL_X, 132, panelWidth, b -> {
				CompoundTag args = new CompoundTag();
				args.putLong("line_bet", lineBet);
				sendAction("auto", args);
			});
			auto.active = ready;
		}
		button(Component.translatable("gui.burmaldaholic.common.paytable"), PANEL_X, 156, panelWidth, b -> {
			showPaytable = true;
			paytableScroll = 0;
			rebuildWidgets();
		});
		showdown.entryButton(PAD, STATUS_BOTTOM + 4, imageWidth - 2 * PAD);
	}

	private long spinBet() {
		return Math.max(0, lineBet) * lines();
	}

	/** −/+ with growing steps; Shift = ×10 steps. */
	private void changeBet(int dir) {
		long step = lineBet < 10 ? 1 : lineBet < 50 ? 5 : lineBet < 200 ? 10 : 50;
		if (hasShiftDown()) {
			step *= 10;
		}
		long next = lineBet + dir * step;
		if (dir > 0 && lineBet < 10 && next > 10) {
			next = 10;
		}
		lineBet = Math.max(lineMin(), Math.min(lineMax(), next));
		rebuildWidgets();
	}

	private static boolean hasShiftDown() {
		Minecraft mc = Minecraft.getInstance();
		return mc.hasShiftDown();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (showPaytable) {
			paytableScroll = Math.max(0, paytableScroll - (int) Math.signum(dy) * 10);
			return true;
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	// ---- rendering ------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		updateAnimation();
		int wx = leftPos + REEL_X;
		int wy = topPos + REEL_Y;
		g.fill(wx - 3, wy - 3, wx + WINDOW + 3, wy + WINDOW + 3, frameColor());
		g.fill(wx - 1, wy - 1, wx + WINDOW + 1, wy + WINDOW + 1, 0xFF101010);
		for (int c = 0; c < 3; c++) {
			for (int r = 0; r < 3; r++) {
				g.fill(wx + c * CELL + 1, wy + r * CELL + 1, wx + (c + 1) * CELL - 1, wy + (r + 1) * CELL - 1, 0xFFEFE6D2);
			}
		}
		if (showdown.open()) {
			showdown.drawBackground(g, wx, wy, PANEL_X - REEL_X - 6, topPos + imageHeight - 24);
		}
		if (showPaytable || showdown.open()) {
			return;
		}
		g.enableScissor(wx, wy, wx + WINDOW, wy + WINDOW);
		long elapsed = now() - animStart;
		for (int c = 0; c < 3; c++) {
			if (c < reelsStopped || strips[c] == null) {
				for (int r = 0; r < 3; r++) {
					drawSymbol(g, Symbol.byOrdinal(grid[r * 3 + c]), wx + c * CELL, wy + r * CELL);
				}
			} else {
				double pos = elapsed / 40.0; // cells scrolled
				int base = (int) Math.floor(pos);
				int offset = (int) ((pos - base) * CELL);
				int[] strip = strips[c];
				for (int k = -1; k < 3; k++) {
					int idx = Math.floorMod(base - k, strip.length);
					drawSymbol(g, Symbol.byOrdinal(strip[idx]), wx + c * CELL, wy + k * CELL + offset);
				}
			}
		}
		g.disableScissor();
		drawPaylines(g, wx, wy);
	}

	private int frameColor() {
		return switch (state().getStringOr("tier", "copper")) {
			case "gold" -> 0xFFE0B020;
			case "netherite" -> 0xFF3A3238;
			default -> 0xFFC06A3C;
		};
	}

	private static void drawSymbol(GuiGraphicsExtractor g, Symbol s, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x + 6, y + 6);
		g.pose().scale(2f, 2f);
		g.item(SlotSymbols.icon(s), 0, 0);
		g.pose().popMatrix();
	}

	/** Active paylines as colored rails on the left; winning lines drawn across the window (flashing at first). */
	private void drawPaylines(GuiGraphicsExtractor g, int wx, int wy) {
		int lines = lines();
		for (int line = 1; line <= lines; line++) {
			int y = wy + Paylines.row(line, 0) * CELL + CELL / 2 + (line > 3 ? (line == 4 ? -8 : 8) : 0);
			g.fill(wx - 3, y - 2, wx - 1, y + 2, LINE_COLORS[line - 1]);
		}
		if (animating() || shownResult == null || revealedSeq != animSeq) {
			return;
		}
		long since = now() - revealStart;
		if (since < FLASH_MS && (since / 250) % 2 == 1) {
			return;
		}
		ListTag wins = shownResult.getListOrEmpty("wins");
		for (int i = 0; i < wins.size(); i++) {
			CompoundTag w = wins.getCompoundOrEmpty(i);
			int line = w.getIntOr("line", 0);
			if (line < 1 || line > Paylines.MAX || (w.getLongOr("payout", 0) <= 0 && !"special".equals(w.getStringOr("kind", "")))) {
				continue;
			}
			drawLine(g, wx, wy, line, LINE_COLORS[line - 1]);
		}
	}

	private static void drawLine(GuiGraphicsExtractor g, int wx, int wy, int line, int color) {
		int x0 = wx + 4;
		int x1 = wx + WINDOW - 4;
		int y0 = wy + Paylines.row(line, 0) * CELL + CELL / 2;
		int y1 = wy + Paylines.row(line, 2) * CELL + CELL / 2;
		if (y0 == y1) {
			g.fill(x0, y0 - 1, x1, y0 + 1, color);
			return;
		}
		int steps = x1 - x0;
		for (int i = 0; i <= steps; i++) {
			int x = x0 + i;
			int y = y0 + (int) Math.round((y1 - y0) * (i / (double) steps));
			g.fill(x, y - 1, x + 2, y + 1, color);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractLabels(g, mouseX, mouseY);
		CompoundTag s = state();
		// balance (panel column)
		g.text(font, Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance())), PANEL_X, 20, TEXT, true);
		if (s.getBooleanOr("progressive", false)) {
			Component jp = Component.translatable("gui.burmaldaholic.slots.jackpot", Texts.number(s.getLongOr("jackpot", 0)));
			g.centeredText(font, jp, REEL_X + WINDOW / 2, 20, GOLD);
		}
		if (showdown.open()) {
			showdown.drawLabels(g, PANEL_X, REEL_X, REEL_Y, PANEL_X - REEL_X - 6, imageHeight - 24);
			return;
		}
		if (showPaytable) {
			drawPaytable(g);
			return;
		}
		g.text(font, Component.translatable("gui.burmaldaholic.slots.line_bet", Texts.number(Math.max(0, lineBet))), PANEL_X, 34, TEXT, true);
		int y = 70;
		y = wrapped(g, Component.translatable("gui.burmaldaholic.common.limits", Texts.number(lineMin()), Texts.number(Math.max(lineMin(), lineMax()))),
			PANEL_X, y, panelWidth, GRAY);
		y = wrapped(g, Component.translatable("gui.burmaldaholic.slots.lines", Texts.number(lines())), PANEL_X, y, panelWidth, TEXT);
		wrapped(g, Component.translatable("gui.burmaldaholic.common.total_bet", Texts.chips(spinBet())), PANEL_X, y, panelWidth, TEXT);
		drawStatus(g);
	}

	private int wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			g.text(font, line, x, y, color, true);
			y += 10;
		}
		return y + 1;
	}

	/** Two result/status lines under the reels (full width, wrapped). */
	private void drawStatus(GuiGraphicsExtractor g) {
		CompoundTag s = state();
		int width = imageWidth - 2 * PAD;
		int y = RESULT_Y;
		List<Component> out = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		if (!s.getBooleanOr("vip_ok", true)) {
			out.add(Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(s.getIntOr("min_vip", 0))));
			colors.add(ERROR);
		} else if (!s.getBooleanOr("enabled", true)) {
			out.add(Component.translatable("gui.burmaldaholic.error.disabled"));
			colors.add(ERROR);
		}
		CompoundTag spin = s.getCompoundOrEmpty("spin");
		if (s.contains("spin") && !spin.getBooleanOr("mine", false)) {
			out.add(Component.translatable("gui.burmaldaholic.slots.playing", Texts.raw(spin.getStringOr("player", ""))));
			colors.add(GRAY);
		}
		CompoundTag r = shownResult;
		if (!animating() && r != null && revealedSeq == animSeq) {
			long total = r.getLongOr("total", 0);
			long since = now() - revealStart;
			long shown = since >= ROLL_MS ? total : total * since / ROLL_MS;
			if (!r.getBooleanOr("mine", true)) {
				out.add(Component.translatable("gui.burmaldaholic.slots.playing", Texts.raw(r.getStringOr("player", ""))));
				colors.add(GRAY);
			}
			if (total > 0) {
				out.add(Component.translatable("gui.burmaldaholic.slots.spin_total", Texts.chips(shown)));
				colors.add(GREEN);
			} else {
				out.add(Component.translatable("gui.burmaldaholic.slots.no_win"));
				colors.add(GRAY);
			}
			long award = r.getLongOr("award", 0);
			if (award > 0) {
				out.add(Component.translatable("msg.burmaldaholic.slots.jackpot_self", Texts.chipsAcc(award)));
				colors.add(GOLD);
			} else if (r.getBooleanOr("sevens", false)) {
				out.add(Component.translatable("msg.burmaldaholic.slots.seven_title"));
				colors.add(ERROR);
			} else {
				Component best = bestLine(r);
				if (best != null) {
					out.add(best);
					colors.add(TEXT);
				}
			}
		}
		CompoundTag summary = s.getCompoundOrEmpty("auto_summary");
		if (s.contains("auto_summary")) {
			String notice = summary.getStringOr("notice", "");
			if (!notice.isEmpty()) {
				out.add(Component.translatable("big_win".equals(notice) ? "gui.burmaldaholic.slots.auto_stopped_big_win"
					: "gui.burmaldaholic.slots.auto_stopped_funds"));
				colors.add(0xFFFFFF55);
			}
			out.add(Component.translatable("gui.burmaldaholic.slots.auto_summary", Texts.plural("unit.burmaldaholic.spin", summary.getIntOr("spins", 0)),
				Texts.chipsAcc(summary.getLongOr("bet", 0)), Texts.chipsAcc(summary.getLongOr("won", 0))));
			colors.add(TEXT);
		}
		int maxY = STATUS_BOTTOM;
		for (int i = 0; i < out.size() && y < maxY; i++) {
			for (FormattedCharSequence line : font.split(out.get(i), width)) {
				if (y >= maxY) {
					break;
				}
				g.text(font, line, PAD, y, colors.get(i), true);
				y += 10;
			}
		}
	}

	private static @Nullable Component bestLine(CompoundTag r) {
		ListTag wins = r.getListOrEmpty("wins");
		CompoundTag best = null;
		for (int i = 0; i < wins.size(); i++) {
			CompoundTag w = wins.getCompoundOrEmpty(i);
			if (w.getLongOr("payout", 0) > 0 && (best == null || w.getLongOr("payout", 0) > best.getLongOr("payout", 0))) {
				best = w;
			}
		}
		if (best == null) {
			return null;
		}
		return Component.translatable("gui.burmaldaholic.slots.line_win", Texts.number(best.getIntOr("line", 1)), mult(best.getDoubleOr("mult", 0)),
			Texts.chips(best.getLongOr("payout", 0)));
	}

	/** Multiplier text: whole numbers grouped ("1 000"), fractions with up to 2 decimals. */
	static MutableComponent mult(double m) {
		if (m == Math.rint(m) && Math.abs(m) < 1e15) {
			return Texts.number((long) m);
		}
		String s = String.format(Locale.ROOT, "%.2f", m).replaceAll("0+$", "");
		return Texts.raw(s);
	}

	// ---- paytable overlay -----------------------------------------------------------------------

	private List<Component> paytableLines() {
		CompoundTag s = state();
		List<Component> out = new ArrayList<>();
		ListTag pays = s.getListOrEmpty("paytable");
		boolean progressive = s.getBooleanOr("progressive", false);
		boolean hasWild = false;
		for (int i = 0; i < pays.size(); i++) {
			CompoundTag p = pays.getCompoundOrEmpty(i);
			Symbol sym = Symbol.byOrdinal(p.getIntOr("symbol", 0));
			double pay = p.getDoubleOr("pay", 0);
			Component name = Component.translatable(sym.translationKey());
			if (sym == Symbol.STAR) {
				out.add(progressive ? Component.translatable("gui.burmaldaholic.slots.paytable.star")
					: Component.translatable("gui.burmaldaholic.slots.paytable.star_owned", mult(pay)));
				if (progressive) {
					out.add(Component.translatable("gui.burmaldaholic.slots.paytable.max_bet_jackpot"));
				}
				continue;
			}
			if (pay > 0) {
				out.add(Component.translatable("gui.burmaldaholic.slots.paytable.three", name, mult(pay)));
			}
			if (sym == Symbol.WILD) {
				hasWild = true;
			}
			if (sym.isSpecial()) {
				out.add(Component.translatable("gui.burmaldaholic.slots.paytable.chaos", name));
			}
		}
		if (s.getDoubleOr("berry1", 0) > 0) {
			out.add(Component.translatable("gui.burmaldaholic.slots.paytable.berry_1", mult(s.getDoubleOr("berry1", 0))));
		}
		if (s.getDoubleOr("berry2", 0) > 0) {
			out.add(Component.translatable("gui.burmaldaholic.slots.paytable.berry_2", mult(s.getDoubleOr("berry2", 0))));
		}
		if (hasWild) {
			out.add(Component.translatable("gui.burmaldaholic.slots.paytable.wild"));
		}
		out.add(Component.translatable("gui.burmaldaholic.slots.lines", Texts.number(lines())));
		return out;
	}

	private void drawPaytable(GuiGraphicsExtractor g) {
		int x = REEL_X;
		int top = REEL_Y;
		int bottom = imageHeight - 16;
		int textWidth = PANEL_X - x - 6; // the reel column; the panel keeps the Back button
		g.fill(x - 3, top - 3, x + textWidth + 3, bottom + 3, 0xF0101010);
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component c : paytableLines()) {
			lines.addAll(font.split(c, textWidth));
		}
		int maxScroll = Math.max(0, lines.size() * 10 - (bottom - top));
		paytableScroll = Math.min(paytableScroll, maxScroll);
		// labels are drawn in GUI-relative coordinates; the scissor follows the pose
		g.enableScissor(x, top, x + textWidth, bottom);
		int y = top - paytableScroll;
		for (FormattedCharSequence line : lines) {
			if (y > top - 10 && y < bottom) {
				g.text(font, line, x, y, TEXT, true);
			}
			y += 10;
		}
		g.disableScissor();
	}
}
