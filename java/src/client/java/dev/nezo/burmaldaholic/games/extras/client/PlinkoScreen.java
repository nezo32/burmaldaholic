package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Plinko (UI.md §9; visual/extras.md §5, mockup {@code extras_plinko_drop.png}; extras-pvp.md §5.3): the neon arcade
 * with the 272 × 204 board (20 × 12 peg pitch) on the left and Risk, bet, Drop and the last balls on the right. A
 * result drops the ball down the server's exact path ({@link PlinkoAnim}): the chute gate opens, one peg per row with a
 * hop and a squash, the hit peg flashes then glows, the peg sound is pitched by the column, three ghost balls trail;
 * the ball falls into its cup, the cap presses and lights, the label pops, and the shared celebration plays the
 * server tier (JACKPOT for an edge bin on High). No neighbour ever lights. Click on the board, Space or Enter skip;
 * reduce motion draws the path as dots row by row and puts the ball in the cup.
 */
final class PlinkoScreen extends ExtrasTableScreen implements dev.nezo.burmaldaholic.client.fx.ClientFx.CelebrationGate {
	private static final int BX = 14;
	private static final int BY = 28;
	private static final int RX = 296;
	private static long lastAmount = 1;
	private static Plinko.Risk risk = Plinko.Risk.MEDIUM;
	/** Last landed balls of this session (multipliers, newest last). */
	private static final List<Double> LAST = new ArrayList<>();

	private final BetControl bet;
	private int shownSeq = -1;
	private boolean seenState;
	private long dropStart = -1;
	private boolean landed = true;
	private long landedAt = -1;
	private long skipAt = -1;
	private int lastRowSound = -1;
	private boolean battle;

	PlinkoScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.extras.plinko.title"), Scene.PLINKO);
		this.bet = new BetControl(false, lastAmount);
	}

	private int rowMs() {
		return Math.max(1, state().getIntOr("step_ticks", 4)) * 50;
	}

	private CompoundTag result() {
		return state().getCompoundOrEmpty("result");
	}

	private boolean dropping() {
		return dropStart >= 0 && !landed;
	}

	private double[] table(Plinko.Risk r) {
		ListTag tables = state().getListOrEmpty("tables");
		for (int i = 0; i < tables.size(); i++) {
			CompoundTag t = tables.getCompoundOrEmpty(i);
			if (r.id().equals(t.getStringOr("risk", ""))) {
				double[] out = new double[Plinko.BINS];
				for (int b = 0; b < Plinko.BINS; b++) out[b] = t.getDoubleOr("m" + b, 0);
				return out;
			}
		}
		return r.defaults();
	}

	@Override
	protected void stateArrived(CompoundTag newState) {
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (!seenState) {
			if (newState.isEmpty()) return; // the cache before the first server state
			// the screen (re)opened: the last result at rest, no replay
			seenState = true;
			shownSeq = seq;
			return;
		}
		if (seq < 0) shownSeq = -1; // no result yet (a new machine at this position)
		if (seq >= 0 && seq != shownSeq) {
			if (dropping()) land(false);
			shownSeq = seq;
			dropStart = Util.getMillis();
			landed = false;
			landedAt = -1;
			skipAt = -1;
			lastRowSound = -1;
			holdBalance(newState.getLongOr("balance", 0) - r.getLongOr("net", 0));
			ClientCasinoState.holdBalanceDelta((int) (durationMs() + 300));
			Kit.vanilla("block.wooden_button.click_on", 0.4f, 1f);
		}
	}

	private double durationMs() {
		return Kit.reduceMotion() ? PlinkoAnim.reducedMs() : PlinkoAnim.landMs(rowMs()) * 100.0 / Kit.speedPct();
	}

	/** Storyboard time of the running drop (speed-scaled), or −1. */
	private double dropMs() {
		if (dropStart < 0) return -1;
		double ms = Util.getMillis() - dropStart;
		return Kit.reduceMotion() ? ms : ms * Kit.speedPct() / 100.0;
	}

	@Override
	public boolean holdsCelebration(String game) {
		return dev.nezo.burmaldaholic.games.extras.server.ExtrasGames.PLINKO.equals(game) && dropping();
	}

	@Override
	protected boolean skip() {
		if (!dropping()) return false;
		skipAt = Util.getMillis();
		land(true);
		return true;
	}

	/** The ball is in its cup: cap lit, sound by multiplier, the celebration for the server tier. */
	private void land(boolean viaSkip) {
		if (landed) return;
		landed = true;
		landedAt = Util.getMillis();
		releaseBalance();
		CompoundTag r = result();
		double mult = r.getDoubleOr("mult", 0);
		LAST.add(mult);
		while (LAST.size() > 4) LAST.removeFirst();
		FxSounds.play("plinko_bin", 1f, PlinkoAnim.binPitch(mult));
		// the celebration is the server's (sent when the ball lands in the world; held if it came first)
		dev.nezo.burmaldaholic.client.fx.ClientFx.releaseCelebration();
		if (minecraft != null) rebuild();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (!dropping()) return;
		double ms = dropMs();
		CompoundTag r = result();
		int path = r.getIntOr("path", 0);
		if (!Kit.reduceMotion()) {
			for (int row = lastRowSound + 1; row < PlinkoAnim.ROWS && ms >= PlinkoAnim.contactMs(rowMs(), row); row++) {
				lastRowSound = row;
				Kit.mod(ExtrasModule.PLINKO_PEG_SOUND, 0.5f, PlinkoAnim.pegPitch(path, row));
			}
		}
		if (ms >= (Kit.reduceMotion() ? PlinkoAnim.reducedMs() : PlinkoAnim.landMs(rowMs()))) land(false);
	}

	@Override
	public void onClose() {
		if (dropping()) land(true);
		super.onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() - leftPos;
		double y = event.y() - topPos;
		if (!battle && event.button() == 0 && x >= BX && x < BX + PlinkoDraw.BOARD_W && y >= BY && y < BY + PlinkoDraw.BOARD_H && skip()) return true;
		return super.mouseClicked(event, doubleClick);
	}

	// ---- layout ------------------------------------------------------------------------------------------------

	@Override
	protected void layout() {
		CompoundTag s = state();
		bet.validate(s);
		boolean busy = dropping();
		Plinko.Risk[] risks = Plinko.Risk.values();
		for (int i = 0; i < risks.length; i++) {
			Plinko.Risk rk = risks[i];
			button(RX - 2, 44 + 16 * i, 92, 14, Component.translatable(rk.key()), KitButton.Style.SECONDARY, b -> {
				risk = rk;
				rebuild();
			}).selected(rk == risk).active(!busy);
		}
		button(RX - 4, 132, 46, 20, Component.translatable("gui.burmaldaholic.extras.minus"), KitButton.Style.SECONDARY, b -> {
			bet.step(state(), -1);
			rebuild();
		}).active(!busy);
		button(RX + 46, 132, 46, 20, Component.translatable("gui.burmaldaholic.extras.plus"), KitButton.Style.SECONDARY, b -> {
			bet.step(state(), 1);
			rebuild();
		}).active(!busy);
		button(RX - 4, 156, 96, 20, Component.translatable("gui.burmaldaholic.extras.plinko.drop"), KitButton.Style.PRIMARY, b -> {
			lastAmount = Math.max(1, bet.amount());
			CompoundTag args = bet.args();
			args.putString("risk", risk.id());
			sendAction("drop", args);
		}).active(!busy);
		if (s.contains("pvp")) {
			// Plinko Battle (PVP.md §7.4): the entries open on a card over the board
			button(RX + 72, 29, 18, 13, Component.empty(), battle ? KitButton.Style.PRIMARY : KitButton.Style.SECONDARY, b -> {
				battle = !battle;
				rebuild();
			}).selected(battle).tooltip(Component.translatable("gui.burmaldaholic.pvp.plinko.host"))
				.icon(new KitButton.Icon(PvpDraw.MODE_ICONS, 80, 16, 32, 0, 16, 16));
			if (battle) {
				dev.nezo.burmaldaholic.games.extras.client.pvp.plinko.PlinkoBattleEntries.layout(font, this::addRenderableWidget, leftPos + BX + 12,
					topPos + BY + 26, PlinkoDraw.BOARD_W - 24, s, risk.id(), () -> Math.max(1, bet.amount()), this::sendAction, this::rebuild, busy);
			}
		}
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int bx = leftPos + BX;
		int by = topPos + BY;
		CompoundTag r = result();
		boolean have = r.getIntOr("seq", -1) >= 0;
		boolean anim = dropping();
		Plinko.Risk shownRisk = have && (anim || landed && landedAt >= 0) ? Plinko.Risk.parse(r.getStringOr("risk", risk.id())) : risk;
		if (shownRisk == null) shownRisk = risk;
		double[] table = table(shownRisk);
		int path = r.getIntOr("path", 0);
		int rowMs = rowMs();
		double ms = anim ? dropMs() : -1;
		boolean rm = Kit.reduceMotion();
		PlinkoDraw.board(g, bx, by);
		PlinkoDraw.pegs(g, bx, by, anim && !rm && Kit.flashes() ? (row, j) -> PlinkoAnim.pegState(path, rowMs, ms, row, j) : null);
		PlinkoDraw.chute(g, bx, by, anim && ms < PlinkoAnim.RELEASE_MS + rowMs);
		boolean showLanded = have && shownRisk.id().equals(r.getStringOr("risk", "")) && (landed && landedAt >= 0 || !anim && shownSeq >= 0 && dropStart < 0);
		int lit = showLanded ? r.getIntOr("bin", -1) : -1;
		double press = 0;
		double pop = -1;
		if (landed && landedAt >= 0) {
			double since = Util.getMillis() - landedAt;
			press = rm || since > 80 ? 0 : Math.sin(Math.PI * since / 80);
			pop = rm ? -1 : Math.min(1, since / 300.0);
		}
		if (anim) {
			if (rm) {
				PlinkoDraw.dottedPath(g, bx, by, path, PlinkoAnim.reducedRows(ms), 0xFFFFE680);
			} else {
				// ghosts at 2, 4 and 6 frames ago, then the ball
				double[] lag = {100, 67, 33};
				double[] alpha = {0.1, 0.25, 0.45};
				for (int i = 0; i < 3; i++) {
					PlinkoAnim.Ball gb = PlinkoAnim.sample(path, rowMs, Math.max(0, ms - lag[i]));
					if (ms - lag[i] > PlinkoAnim.RELEASE_MS) PlinkoDraw.ball(g, bx, by, gb.x(), gb.y(), gb.roll(), 1, 1, false, alpha[i]);
				}
				PlinkoAnim.Ball b = PlinkoAnim.sample(path, rowMs, ms);
				PlinkoDraw.ball(g, bx, by, b.x(), b.y(), b.roll(), b.sx(), b.sy(), false, 1);
			}
		} else if (skipAt >= 0 && Util.getMillis() - skipAt < 150) {
			PlinkoDraw.dottedPath(g, bx, by, path, PlinkoAnim.ROWS, 0xFFFFE680);
		}
		// the ball rests in its cup, behind the cap
		PlinkoDraw.bins(g, font, bx, by, table, lit, press, pop, false);
		// right column wells
		int rx = leftPos + RX;
		Scene.inset(g, rx - 4, topPos + 28, 96, 66);
		Scene.inset(g, rx - 4, topPos + 98, 96, 30);
		if (battle) {
			Scene.card(g, bx + 4, by + 4, PlinkoDraw.BOARD_W - 8, PlinkoDraw.BOARD_H - 8);
		}
	}

	@Override
	protected int[] chipCounterAt() {
		return new int[] {RX - 4, 214};
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag s = state();
		Kit.text(g, font, Component.translatable("gui.burmaldaholic.extras.plinko.risk"), RX, 33, Kit.GOLD);
		Component betLabel = Component.translatable("gui.burmaldaholic.extras.bet");
		Kit.text(g, font, betLabel, RX, 103, Kit.BONE_SHADE);
		int cx = RX + font.width(betLabel) + 4;
		Kit.sprite(g, Kit.core("fx/chip_" + BetControl.chipDenom(bet.amount())), cx, 103, 8, 8);
		Kit.fit(g, font, bet.shown(s), cx + 12, 103, RX + 88 - cx - 12, Kit.GOLD, true);
		double top = 0;
		for (double m : table(risk)) top = Math.max(top, m);
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.plinko.top", Texts.decimal(Payouts.formatMultiplier(top))), RX, 115, 88,
			Kit.BONE_SHADE, true);
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.plinko.last"), RX, 182, 90, Kit.GOLD, true);
		for (int i = 0; i < LAST.size(); i++) {
			PlinkoDraw.cap(g, font, LAST.get(i), RX + i * 22, 194, true, -1);
		}
		if (battle) {
			Kit.text(g, font, Component.translatable("gui.burmaldaholic.pvp.plinko.title"), BX + 16, BY + 12, Kit.GOLD);
		}
	}
}
