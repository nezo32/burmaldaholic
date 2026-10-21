package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasFlow;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasKeys;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasLayout;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasView;
import dev.nezo.burmaldaholic.games.extras.present.LocalBet;
import dev.nezo.burmaldaholic.games.extras.present.LocalBets;
import dev.nezo.burmaldaholic.games.extras.present.MachinePub;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import net.minecraft.world.entity.player.Inventory;

/**
 * Plinko, table UI v2 (REDESIGN.md §7.7, mockup {@code v2_plinko_480.png}; ui-v2.md lane E): the kit's TopBar and Dock
 * (BetBar: chips add to the bet, Undo / Clear / Rebet / ×2; status "Win up to …"; primary <b>Drop</b> {@code Space}) and
 * the PlayArea scene: the 272 × 204 board centred in grid columns 1–9 on the navy fill, the options column (Risk as three
 * toggles, the selected one gold; Last balls pills; Top prize 2×). A result drops the ball down the server's exact path
 * ({@link PlinkoAnim}): the chute opens, pegs flash, ghost balls trail, the ball lands in its cup, the cap presses and
 * the label pops; click the board or press Enter to skip. The balance is held until the landing.
 *
 * <p>Hit map ids: {@code felt.risk.<low|medium|high>}, {@code felt.board} (skip), {@code felt.battle} (Plinko Battle
 * entries when PvP is on), plus the kit's {@code chip.*}, {@code tool.*}, {@code primary}.
 */
final class PlinkoScreen extends V2TableScreen<ExtrasView> implements dev.nezo.burmaldaholic.client.fx.ClientFx.CelebrationGate {
	/** This machine's bet in the client session (per position, R-CDE m5). */
	private final LocalBet bet;
	private static Plinko.Risk risk = Plinko.Risk.MEDIUM;
	/** Last landed balls of this session per machine (multipliers, newest last). */
	private static final java.util.Map<Long, List<Double>> LAST_BY_POS = new java.util.HashMap<>();

	/** Forgets the session's last balls (disconnect). */
	static void clearSession() {
		LAST_BY_POS.clear();
	}

	/** This machine's last landed balls (multipliers, newest last). */
	private final List<Double> last;

	private int shownSeq = -1;
	private boolean seenState;
	private long dropStart = -1;
	private boolean landed = true;
	private long landedAt = -1;
	private long skipAt = -1;
	private int lastRowSound = -1;
	private boolean battle;
	private long heldBalance = -1;
	private ExtrasLayout.Scene scene;

	PlinkoScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.extras.plinko.title"));
		this.bet = LocalBets.of("plinko", menu.pos().asLong());
		this.last = LAST_BY_POS.computeIfAbsent(menu.pos().asLong(), k -> new ArrayList<>());
	}

	// ---- the pure parts ------------------------------------------------------------------------------------------------

	/**
	 * The minimum bet of a risk (v0.1.2: the server's per-risk "min" of its table; the machine's "min" from an older
	 * server). Gating on the machine-wide minimum let the screen send bets the server refuses on Medium / High.
	 */
	private static long minFor(CompoundTag s, Plinko.Risk r) {
		ListTag tables = s.getListOrEmpty("tables");
		for (int i = 0; i < tables.size(); i++) {
			CompoundTag t = tables.getCompoundOrEmpty(i);
			if (r.id().equals(t.getStringOr("risk", "")) && t.contains("min")) {
				return Math.max(1, t.getLongOr("min", 1));
			}
		}
		return Math.max(1, s.getLongOr("min", 1));
	}

	/** After a risk change: a bet below the new risk's minimum is raised to it (when the balance allows). */
	private void topUpToMin() {
		long min = minFor(state(), risk);
		if (bet.amount() > 0 && bet.amount() < min) bet.add(min - bet.amount(), state().getLongOr("max", 0), shownBalance());
	}

	@Override
	protected ExtrasView view(CompoundTag s) {
		long bal = shownBalance();
		bet.clamp(s.getLongOr("max", 0), s.getLongOr("balance", 0));
		boolean busy = dropping();
		double top = top(table(risk));
		return ExtrasView.chips(bet.view(minFor(s, risk), s.getLongOr("max", 0), bal, busy), busy, null, (long) Math.floor(bet.amount() * top));
	}

	@Override
	protected Flow<ExtrasView> flow() {
		return ExtrasFlow.PLINKO;
	}

	/** A local change (chip, tool, risk, the landing): recompute the view and re-apply the flow. */
	private void local() {
		view = view(state());
		refresh();
	}

	@Override
	protected BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				if (bet.add(denom, state().getLongOr("max", 0), shownBalance())) Kit.vanilla("block.stone_button.click_on", 0.4f, 1.4f);
				local();
			}

			@Override
			public boolean setsAmount() {
				return true;
			}

			@Override
			public boolean amount(long amount) { // the bet-entry control's OK (lane HR)
				bet.set(amount, state().getLongOr("max", 0), shownBalance());
				local();
				return true;
			}

			@Override
			public void remove(long denom) {
				bet.undo();
				local();
			}

			@Override
			public void tool(String id) {
				long max = state().getLongOr("max", 0);
				long bal = shownBalance();
				switch (id) {
					case BetGates.UNDO -> bet.undo();
					case BetGates.CLEAR -> bet.clear();
					case BetGates.REBET -> bet.rebet(max, bal);
					case BetGates.DOUBLE -> bet.dbl(max, bal);
					default -> {}
				}
				local();
			}
		};
	}

	@Override
	public void onGate(String id, Gate gate) {
		if (!gate.action().equals("drop")) {
			super.onGate(id, gate);
			return;
		}
		CompoundTag args = new CompoundTag();
		args.putString("stake", "chips");
		args.putLong("amount", bet.amount());
		args.putString("risk", risk.id());
		bet.sent();
		sendTracked("drop", args, "", 0);
	}

	// ---- state and the drop -----------------------------------------------------------------------------------------

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

	private static double top(double[] table) {
		double top = 0;
		for (double m : table) top = Math.max(top, m);
		return top;
	}

	private long shownBalance() {
		return heldBalance >= 0 ? heldBalance : state().getLongOr("balance", 0);
	}

	@Override
	protected long balance() {
		return shownBalance();
	}

	@Override
	protected void onStateChanged(CompoundTag newState) {
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (!seenState) {
			if (!newState.isEmpty()) {
				// the screen (re)opened: the last result at rest, no replay
				seenState = true;
				shownSeq = seq;
				if (bet.amount() <= 0) bet.add(minFor(newState, risk), newState.getLongOr("max", 0), newState.getLongOr("balance", 0));
			}
		} else {
			if (seq < 0) shownSeq = -1;
			if (seq >= 0 && seq != shownSeq) {
				if (dropping()) land(false);
				shownSeq = seq;
				bet.ack(); // the server played the amount: it becomes Rebet
				dropStart = AnimClock.localMs();
				landed = false;
				landedAt = -1;
				skipAt = -1;
				lastRowSound = -1;
				heldBalance = newState.getLongOr("balance", 0) - r.getLongOr("net", 0);
				ClientCasinoState.holdBalanceDelta((int) (durationMs() + 300));
				Kit.vanilla("block.wooden_button.click_on", 0.4f, 1f);
			}
		}
		super.onStateChanged(newState);
	}

	/**
	 * Tests (NetLag lane): {ms since the drop on this screen's clock, ball x, ball y, landed 0/1, server bin} — the ball
	 * pose the next frame draws ({@code -1} ms = no drop running).
	 */
	public double[] poseProbe() {
		CompoundTag r = result();
		int path = r.getIntOr("path", 0);
		double ms = dropping() ? dropMs() : -1;
		PlinkoAnim.Ball b = ms < 0 || Kit.reduceMotion() ? null : PlinkoAnim.sample(path, rowMs(), ms);
		return new double[] {ms, b == null ? Double.NaN : b.x(), b == null ? Double.NaN : b.y(), landed ? 1 : 0, r.getIntOr("bin", -1), path, rowMs()};
	}

	private double durationMs() {
		return Kit.reduceMotion() ? PlinkoAnim.reducedMs() : PlinkoAnim.landMs(rowMs()) * 100.0 / Kit.speedPct();
	}

	private double dropMs() {
		if (dropStart < 0) return -1;
		double ms = AnimClock.localMs() - dropStart;
		return Kit.reduceMotion() ? ms : ms * Kit.speedPct() / 100.0;
	}

	@Override
	public boolean holdsCelebration(String game) {
		return dev.nezo.burmaldaholic.games.extras.server.ExtrasGames.PLINKO.equals(game) && dropping();
	}

	private boolean skip() {
		if (!dropping()) return false;
		skipAt = AnimClock.localMs();
		land(true);
		return true;
	}

	/** The ball is in its cup: cap lit, sound by multiplier, the server's celebration, the balance released. */
	private void land(boolean viaSkip) {
		if (landed) return;
		landed = true;
		landedAt = AnimClock.localMs();
		heldBalance = -1;
		double mult = result().getDoubleOr("mult", 0);
		last.add(mult);
		while (last.size() > 4) last.removeFirst();
		FxSounds.play("plinko_bin", 1f, PlinkoAnim.binPitch(mult));
		long net = result().getLongOr("net", 0);
		long stake = result().getLongOr("stake", 0);
		if (stake > 0) chrome.banner.show(net);
		dev.nezo.burmaldaholic.client.fx.ClientFx.releaseCelebration();
		if (stake > 0) bet.clamp(state().getLongOr("max", 0), state().getLongOr("balance", 0));
		local();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (!dropping()) return;
		double ms = dropMs();
		int path = result().getIntOr("path", 0);
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
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.isSelection() && dropping() && skip()) return true; // Space / Enter / keypad Enter, resolved by the running version
		return super.keyPressed(event);
	}

	// ---- layout -------------------------------------------------------------------------------------------------------

	private Box riskBox(int i) {
		return scene.row(14, i);
	}

	@Override
	protected void layoutPlay(TableGrid grid, ExtrasView v, HitMap hits) {
		scene = ExtrasLayout.plinko(grid);
		Box o = scene.object();
		chrome.banner.regions(List.of(new Box(o.x() + 8, o.y() + 40, o.w() - 16, o.h() / 2)), List.of());
		hits.add(KeyMap.Ids.felt("board"), scene.object(), true);
		Plinko.Risk[] risks = Plinko.Risk.values();
		for (int i = 0; i < risks.length; i++) hits.add(KeyMap.Ids.felt("risk." + risks[i].id()), riskBox(i), !v.busy());
		if (state().contains("pvp")) hits.add(KeyMap.Ids.felt("battle"), battleBox(), true);
		if (battle) {
			// Plinko Battle entries: casino widgets on a card over the board (the old flow, PVP.md §7.4)
			clearWidgets();
			dev.nezo.burmaldaholic.games.extras.client.pvp.plinko.PlinkoBattleEntries.layout(font, this::addRenderableWidget, o.x() + 12, o.y() + 26,
				o.w() - 24, state(), risk.id(), () -> Math.max(1, bet.amount()), this::sendAction, this::rebuildBattle, v.busy());
		} else {
			clearWidgets();
		}
	}

	private void rebuildBattle() {
		chrome.relayout();
	}

	/** The Plinko Battle toggle's label key (the mode's short name). */
	static final String BATTLE_TOGGLE = "gui.burmaldaholic.pvp.plinko.title";

	private Box battleBox() {
		Box side = scene.side();
		return new Box(side.x(), side.bottom() - scene.row(), side.w(), scene.row());
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		if (id.equals(KeyMap.Ids.felt("board"))) {
			return !battle && skip();
		}
		if (id.equals(KeyMap.Ids.felt("battle"))) {
			battle = !battle;
			chrome.relayout();
			return true;
		}
		if (id.startsWith("felt.risk.")) {
			Plinko.Risk r = Plinko.Risk.parse(id.substring("felt.risk.".length()));
			if (r == null) return false;
			if (!enabled) {
				chrome.refuse(Gate.off("risk", ExtrasKeys.RISK, ExtrasKeys.REASON_DROPPING));
				return true;
			}
			if (button.scroll() != 0) {
				int k = Math.floorMod(risk.ordinal() - button.scroll(), 3);
				risk = Plinko.Risk.values()[k];
			} else {
				risk = r;
			}
			topUpToMin();
			Kit.vanilla("block.stone_button.click_on", 0.3f, 1.2f);
			local();
			return true;
		}
		return false;
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, ExtrasView v, int mouseX, int mouseY, float partial) {
		if (scene == null) scene = ExtrasLayout.plinko(grid);
		SceneKit.fill(g, grid.play, "plinko");
		Box o = scene.object();
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
		g.pose().pushMatrix();
		g.pose().translate(o.x(), o.y());
		g.pose().scale(scene.scale(), scene.scale());
		PlinkoDraw.board(g, 0, 0);
		PlinkoDraw.pegs(g, 0, 0, anim && !rm && Kit.flashes() ? (row, j) -> PlinkoAnim.pegState(path, rowMs, ms, row, j) : null);
		PlinkoDraw.chute(g, 0, 0, anim && ms < PlinkoAnim.RELEASE_MS + rowMs);
		boolean showLanded = have && shownRisk.id().equals(r.getStringOr("risk", "")) && (landed && landedAt >= 0 || !anim && shownSeq >= 0 && dropStart < 0);
		int lit = showLanded ? r.getIntOr("bin", -1) : -1;
		double press = 0;
		double pop = -1;
		if (landed && landedAt >= 0) {
			double since = AnimClock.localMs() - landedAt;
			press = rm || since > 80 ? 0 : Math.sin(Math.PI * since / 80);
			pop = rm ? -1 : Math.min(1, since / 300.0);
		}
		if (anim) {
			if (rm) {
				PlinkoDraw.dottedPath(g, 0, 0, path, PlinkoAnim.reducedRows(ms), 0xFFFFE680);
			} else {
				double[] lag = {100, 67, 33};
				double[] alpha = {0.1, 0.25, 0.45};
				for (int i = 0; i < 3; i++) {
					PlinkoAnim.Ball gb = PlinkoAnim.sample(path, rowMs, Math.max(0, ms - lag[i]));
					if (ms - lag[i] > PlinkoAnim.RELEASE_MS) PlinkoDraw.ball(g, 0, 0, gb.x(), gb.y(), gb.roll(), 1, 1, false, alpha[i]);
				}
				PlinkoAnim.Ball b = PlinkoAnim.sample(path, rowMs, ms);
				PlinkoDraw.ball(g, 0, 0, b.x(), b.y(), b.roll(), b.sx(), b.sy(), false, 1);
			}
		} else if (skipAt >= 0 && AnimClock.localMs() - skipAt < 150) {
			PlinkoDraw.dottedPath(g, 0, 0, path, PlinkoAnim.ROWS, 0xFFFFE680);
		}
		PlinkoDraw.bins(g, font, 0, 0, table, lit, press, pop, false);
		if (battle) Scene.card(g, 4, 4, PlinkoDraw.BOARD_W - 8, PlinkoDraw.BOARD_H - 8);
		g.pose().popMatrix();
		if (battle) {
			Kit.text(g, font, Component.translatable("gui.burmaldaholic.pvp.plinko.title"), o.x() + 16, o.y() + 12, Kit.GOLD);
		}
		side(g, v, mouseX, mouseY);
	}

	private void side(GuiGraphicsExtractor g, ExtrasView v, int mx, int my) {
		Box side = scene.side();
		SceneKit.caption(g, font, Component.translatable(ExtrasKeys.RISK), side, side.y() + 2);
		Plinko.Risk[] risks = Plinko.Risk.values();
		for (int i = 0; i < risks.length; i++) {
			SceneKit.toggle(g, font, riskBox(i), Component.translatable(risks[i].key()), risks[i] == risk, !v.busy(), mx, my);
		}
		boolean pvp = state().contains("pvp");
		int[] lay = sideLayout();
		double topNow = top(table(risk));
		if (lay[0] > 0) { // DES Plinko P1: the "Last balls" caption only with a ball under it
			SceneKit.caption(g, font, Component.translatable(ExtrasKeys.LAST_BALLS), side, side.y() + lay[1]);
			for (int i = 0; i < Math.min(last.size(), 2 * lay[0]); i++) {
				int k = last.size() - 1 - i; // newest first
				Box cell = scene.cell(lay[2], i / 2, i % 2);
				Box pill = new Box(cell.x(), cell.y(), Math.min(cell.w(), 40), Math.min(cell.h(), 14));
				double m = last.get(k);
				SceneKit.pill(g, font, pill, Texts.raw("×" + MachinePub.compact(m)), SceneKit.multColor(m, topNow));
			}
		}
		if (lay[3] >= 0) {
			int ty = side.y() + lay[3];
			SceneKit.caption(g, font, Component.translatable(ExtrasKeys.TOP_PRIZE), side, ty);
			SceneKit.big(g, font, Texts.raw("×" + MachinePub.compact(topNow)), side.x(), ty + 11, side.w(), CasinoPalette.V2_GOLD);
		}
		if (pvp) {
			// the short name: "Start a Plinko Battle" was cut to "Start a Plinko Ba…" in the 98-px column at 427 × 240 (PL report);
			// the expanded card keeps the long "Start a Plinko Battle" entry (PlayFieldUnityTest checks the fit, EN and RU)
			SceneKit.toggle(g, font, battleBox(), Component.translatable(BATTLE_TOGGLE), battle, true, mx, my);
		}
	}

	/** {@link ExtrasLayout#plinkoSide} for this column: above the Battle toggle when PvP is on. */
	int[] sideLayout() {
		Box side = scene.side();
		int bottom = state().contains("pvp") ? battleBox().y() - 2 - side.y() : side.h();
		return ExtrasLayout.plinkoSide(scene, last.size(), bottom);
	}

	/** The tooltip of the hovered Risk toggle (its own minimum bet; v0.1.2 hotfix), or null. Read by client GameTests. */
	@org.jspecify.annotations.Nullable Component riskTip;

	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, ExtrasView v, int mouseX, int mouseY, float partial) {
		riskTip = null;
		if (scene == null || battle) return;
		Plinko.Risk[] risks = Plinko.Risk.values();
		for (int i = 0; i < risks.length; i++) {
			if (riskBox(i).contains(mouseX, mouseY)) {
				riskTip = Component.translatable("gui.burmaldaholic.extras.plinko.min", Texts.chips(minFor(state(), risks[i])));
				g.setTooltipForNextFrame(font.split(riskTip, 200), mouseX, mouseY);
			}
		}
	}
}
