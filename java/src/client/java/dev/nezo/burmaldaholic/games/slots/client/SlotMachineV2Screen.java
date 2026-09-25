package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.fx.ClientFx;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotButton;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotModel;
import dev.nezo.burmaldaholic.games.slots.client.pvp.ShowdownPanel;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.TapeCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Slots v2 machine screen (SLOTS.md §10.5, slots.md §4; JS2) on the table-screen protocol. Everything visual is the
 * shared {@link SlotBody}; this class only maps the server state to it and sends the actions.
 *
 * <p>State (the {@code v2} tag of {@code SlotMachineBlockEntity#writeClientState}; the balance is the base state's):
 * <pre>
 * machine · enabled · vip_ok · owned · bets:long[] · bet · buy_enabled · buy_price · buy_rtp · rtp · max_win
 * turbo_allowed · turbo · autoplay · auto_counts · auto_loss_limits · big_win_tiers · jackpots:long[4]
 * def: pays:int[] · scatter_pays · free_spins · retrigger · fs_cap · fs_mult · ladder · ladder_free · bonus_mask · buy
 *      strip0..4 · wheel0..2 · hunt_board                         (anything omitted → the SLOTS.md defaults)
 * rest:int[5] · rest_cells:int[15]                                (the window before the running / next spin)
 * spin:{seq, start_tick, speed, seed, anticipation, hold_ms, tape (the section the client may see, F7), mine, player}
 * auto:{left, mine} · auto_summary:{spins, bet, won, notice} · result:{…}
 * </pre>
 * A running Treasure Hunt is followed through the tape: every pick the server publishes the next entry (the i-th pick
 * shows entry i, D6); when the hunt ends the full tape arrives and the timeline's local part (roll-up, jackpots) is
 * swapped in without moving the clock. Actions: {@code spin{bet}}, {@code skip}, {@code pick{chest}}, {@code buy{bet}},
 * {@code auto{count, loss_limit, stop_feature, bet}}, {@code stop_auto}, {@code turbo{on}}. Rejections arrive as the base
 * {@code sendError}: the reels decelerate back to the previous window (F1).
 */
public class SlotMachineV2Screen extends CasinoTableScreen {
	/** Slot Showdown panel layout while open (GUI-relative): rules column left, controls column right. */
	private static final int SD_TEXT_X = 8;
	private static final int SD_TEXT_Y = 24;
	private static final int SD_PANEL_W = 140;
	private static final int SD_PANEL_X = 400 - 8 - SD_PANEL_W;
	private static final int SD_TEXT_W = SD_PANEL_X - SD_TEXT_X - 8;
	private final SlotModel model = new SlotModel();
	/** Slot Showdown (PvP) entry + panel; the entry button joins the body's control flow. */
	private final ShowdownPanel showdown = new ShowdownPanel(new ShowdownHost());
	/** Collects the entry button while the body's widgets are built (the panel is closed). */
	private List<SlotButton> entryCollector;
	private CompoundTag lastPvp = new CompoundTag();
	private SlotBody body;
	private Machine machine;
	private int playedSeq = -1;
	private int huntOpened;
	private boolean huntComplete = true;
	private long latestBalance;
	private int[] bigWinTiers;
	private int speedPct = 100;
	private int seed;
	private boolean anticipation = true;

	public SlotMachineV2Screen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 400, 240);
		this.inventoryLabelY = -10_000;
		this.titleLabelY = -10_000;
	}

	/** The body (GameTests: the pure present model behind the screen). */
	public SlotBody body() {
		return body;
	}

	private void ensureBody(CompoundTag s) {
		Machine m = Machine.byId(s.getStringOr("machine", "overworld"));
		MachineDef def = readDef(m, s);
		if (body != null && m == machine && def.equals(model.def)) return;
		machine = m;
		model.def = def;
		body = new SlotBody(model, new ServerControls(), Component.translatable("gui.burmaldaholic.slots.machine." + m.id));
		body.serverPaced(true);
		playedSeq = -1;
		if (minecraft != null) rebuildWidgets();
	}

	@Override
	protected void rebuildWidgets() {
		clearWidgets();
		if (showdown.open()) {
			showdown.buildWidgets(SD_PANEL_X, SD_PANEL_W, SD_TEXT_X, SD_TEXT_W, imageHeight - 30);
			return;
		}
		if (body == null) return;
		entryCollector = new ArrayList<>();
		showdown.entryButton(0, 0, 0);
		body.extraButtons(entryCollector);
		entryCollector = null;
		body.init(width, height, font, this::addRenderableWidget);
	}

	@Override
	protected void onStateChanged(CompoundTag state) {
		CompoundTag s = state.getCompoundOrEmpty("v2");
		ensureBody(s);
		latestBalance = state.getLongOr("balance", latestBalance);
		// F6: the balance panel follows the reels, never ahead of them
		if (!body.stage().spinning()) model.balance = latestBalance;
		long[] bets = s.getLongArray("bets").orElse(new long[0]);
		if (bets.length > 0) model.bets = bets;
		if (!body.stage().spinning()) {
			long bet = s.getLongOr("bet", model.bet());
			for (int i = 0; i < model.bets.length; i++) if (model.bets[i] == bet) model.betIndex = i;
		}
		long[] pools = s.getLongArray("jackpots").orElse(null);
		if (pools != null && pools.length == 4) model.pools = pools;
		model.playable = s.getBooleanOr("enabled", true) && s.getBooleanOr("vip_ok", true) && bets.length > 0;
		model.turboAllowed = s.getBooleanOr("turbo_allowed", true);
		model.turbo = model.turboAllowed && s.getBooleanOr("turbo", model.turbo);
		model.buyAllowed = s.getBooleanOr("buy_enabled", false);
		model.autoplayAllowed = s.getBooleanOr("autoplay", true);
		model.autoCounts = s.getIntArray("auto_counts").orElse(model.autoCounts);
		model.lossLimits = s.getIntArray("auto_loss_limits").orElse(model.lossLimits);
		model.rtpBasisPoints = (int) Math.round(s.getDoubleOr("rtp", model.rtpBasisPoints / 10000.0) * 10000);
		model.buyRtpBasisPoints = (int) Math.round(s.getDoubleOr("buy_rtp", model.buyRtpBasisPoints / 10000.0) * 10000);
		model.autoSummary = s.contains("auto_summary") ? autoSummary(s.getCompoundOrEmpty("auto_summary")) : null;
		CompoundTag auto = s.getCompoundOrEmpty("auto");
		model.autoLeft = s.contains("auto") && auto.getBooleanOr("mine", false) ? auto.getIntOr("left", 0) : -1;
		bigWinTiers = s.getIntArray("big_win_tiers").filter(a -> a.length == 4).orElse(null);
		int[] rest = s.getIntArray("rest").filter(a -> a.length == 5).orElse(new int[5]);
		int[] restCells = s.getIntArray("rest_cells").filter(a -> a.length == 15).orElse(null);
		if (s.contains("spin")) {
			playSpin(s.getCompoundOrEmpty("spin"), rest, restCells);
		} else if (!body.stage().active()) {
			body.stage().rest(rest, restCells);
		}		// Slot Showdown: relabel the entry / refresh the open panel when the PvP part of the state changes
		CompoundTag pvp = state.getCompoundOrEmpty("pvp");
		if (!pvp.equals(lastPvp)) {
			lastPvp = pvp.copy();
			if (showdown.open() || !body.stage().spinning()) rebuildWidgets();
		}
	}

	/** "Autoplay stopped: …" and "Spins N: bet X, won Y" (SLOTS.md §6.4). */
	static Component autoSummary(CompoundTag summary) {
		Component line = Component.translatable("gui.burmaldaholic.slots.auto_summary", Texts.number(summary.getIntOr("spins", 0)),
			Texts.chipsAcc(summary.getLongOr("bet", 0)), Texts.chipsAcc(summary.getLongOr("won", 0)));
		String notice = summary.getStringOr("notice", "");
		if (notice.isEmpty()) return line;
		String key = switch (notice) {
			case "big_win" -> "gui.burmaldaholic.slots.auto_stopped_big_win";
			case "funds" -> "gui.burmaldaholic.slots.auto_stopped_funds";
			case "feature" -> "gui.burmaldaholic.slots.auto_stopped_feature";
			case "loss" -> "gui.burmaldaholic.slots.auto_stopped_loss";
			case "jackpot" -> "gui.burmaldaholic.slots.auto_stopped_jackpot";
			default -> null;
		};
		return key == null ? line : Component.translatable(key).append(Texts.raw(" · ")).append(line); // literal-ok: separator
	}

	private Timeline timeline(SpinTape tape) {
		return SlotTimeline.build(tape, model.def, TimingProfile.SHARED.withSpeed(speedPct), FxSettings.localProfile(), seed, anticipation,
			bigWinTiers);
	}

	private void playSpin(CompoundTag spin, int[] restStops, int[] restCells) {
		int seq = spin.getIntOr("seq", -1);
		SpinTape tape;
		try {
			tape = TapeCodec.decode(spin.getStringOr("tape", ""));
		} catch (RuntimeException malformed) {
			return;
		}
		if (seq == playedSeq) {
			progress(tape);
			return;
		}
		playedSeq = seq;
		seed = spin.getIntOr("seed", 0);
		speedPct = Math.max(25, Math.min(400, spin.getIntOr("speed", 100)));
		anticipation = spin.getBooleanOr("anticipation", true);
		long startTick = spin.getLongOr("start_tick", 0);
		int holdMs = spin.getIntOr("hold_ms", -1);
		Minecraft mc = Minecraft.getInstance();
		if (holdMs >= 0 && mc.level != null) {
			// the server's shared clock stands at the Treasure Hunt pause: start the local clock there
			startTick = mc.level.getGameTime() - holdMs / 50;
		}
		SpinTape.Hunt h = tape.hunt();
		huntOpened = h == null ? 0 : h.opened();
		huntComplete = tape.totalFifths() >= 0;
		TimelineSeed ts = new TimelineSeed("slots." + machine.id, seq, startTick, seed, speedPct);
		Timeline tl = timeline(tape);
		boolean mine = spin.getBooleanOr("mine", false);
		body.interactive(mine);
		body.stage().rest(restStops, restCells);
		body.stage().play(tape, tl, SpinClock.server(tl, ts, () -> mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)), seed);
		if (mine) {
			// F6: the HUD balance delta waits for the reels and the roll-up
			double elapsed = mc.level == null ? 0 : ts.elapsedMs(mc.level.getGameTime(), 0);
			ClientFx.balanceHold.accept((int) Math.max(0, Math.min(120_000, tl.endMs() - elapsed)));
		}
	}

	/** More of the running spin's tape: the next Treasure Hunt entries, then (hunt over) the full tape. */
	private void progress(SpinTape tape) {
		SpinTape.Hunt h = tape.hunt();
		if (h == null) return;
		int[] e = h.entries();
		for (int i = huntOpened; i < Math.min(h.opened(), e.length); i++) body.stage().huntReveal(e[i]);
		huntOpened = Math.max(huntOpened, h.opened());
		if (!huntComplete && tape.totalFifths() >= 0) {
			huntComplete = true;
			body.stage().huntRest(Arrays.copyOfRange(e, Math.min(h.opened(), e.length), e.length));
			body.stage().retape(tape, timeline(tape));
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (body != null && !body.stage().spinning()) model.balance = latestBalance;
	}

	/** Machine definition from the state, or the SLOTS.md defaults for anything omitted. */
	static MachineDef readDef(Machine m, CompoundTag d) {
		MachineDef base = SlotDefaults.def(m);
		if (d.isEmpty()) return base;
		int[][] strips = new int[5][];
		for (int r = 0; r < 5; r++) strips[r] = d.getIntArray("strip" + r).filter(a -> a.length >= 3).orElse(base.strips()[r]);
		int[][] pays = base.paysFifths();
		int[] flat = d.getIntArray("pays").orElse(null);
		if (flat != null && flat.length == pays.length * 3) {
			pays = new int[pays.length][3];
			for (int i = 0; i < pays.length; i++) System.arraycopy(flat, i * 3, pays[i], 0, 3);
		}
		MachineDef.Features f = base.features();
		int[][] rings = f.wheelRings();
		if (rings.length > 0) {
			int[][] wr = new int[rings.length][];
			for (int i = 0; i < rings.length; i++) wr[i] = d.getIntArray("wheel" + i).filter(a -> a.length > 0).orElse(rings[i]);
			rings = wr;
		}
		MachineDef.Features features = new MachineDef.Features(d.getIntOr("hunt_board", f.pickBoard()), f.pickValues(), f.pickWeights(),
			d.getIntOr("hold_trigger", f.holdTrigger()), d.getIntOr("hold_respins", f.holdRespins()), f.holdCoinPpm(), f.holdValues(),
			f.holdWeights(), rings, d.getLongOr("jackpot_ref", f.jackpotRef()), f.jackpotSeedMult(), f.contributionPpm(), f.ownedMult());
		return new MachineDef(m, base.codes(), base.roles(), strips, pays, d.getIntArray("scatter_pays").orElse(base.scatterFifths()),
			d.getIntOr("bonus_mask", base.bonusReelsMask()), d.getIntArray("free_spins").orElse(base.freeSpins()), d.getIntOr("retrigger", base.retrigger()),
			d.getIntOr("fs_cap", base.fsCap()), d.getIntOr("fs_mult", base.fsMultiplier()), d.getIntArray("ladder").orElse(base.ladder()),
			d.getIntArray("ladder_free").orElse(base.ladderFree()), d.getIntOr("max_win", base.capMultiple()), d.getIntOr("buy", base.buyPriceFifths()),
			features);
	}

	@Override
	public void showError(Component message) {
		super.showError(message);
		if (body != null && body.stage().preRolling()) body.rejected("gui.burmaldaholic.error.round_in_progress");
	}

	/** Whether the machine body is shown (not the Slot Showdown panel). */
	private boolean bodyShown() {
		return body != null && !showdown.open();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (!bodyShown()) {
			super.extractBackground(g, mouseX, mouseY, a);
			if (showdown.open()) {
				showdown.drawBackground(g, leftPos + SD_TEXT_X, topPos + SD_TEXT_Y, SD_TEXT_W, topPos + imageHeight - 24);
			}
			return;
		}
		body.drawBackground(g, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (bodyShown()) body.drawOverlays(g, width, height, mouseX, mouseY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		// the body draws its own labels; the base error line stays
		super.extractLabels(g, xm, ym);
		if (showdown.open()) showdown.drawLabels(g, SD_PANEL_X, SD_TEXT_X, SD_TEXT_Y, SD_TEXT_W, imageHeight - 24);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (bodyShown() && body.mouseClicked(event.x(), event.y())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		return bodyShown() && body.mouseScrolled(dy) || super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (bodyShown() && body.keyPressed(event.key())) return true;
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		if (body != null) body.close();
		super.removed();
	}

	/** What the Slot Showdown panel borrows from this screen. */
	private final class ShowdownHost implements ShowdownPanel.Host {
		@Override
		public Button button(Component label, int x, int y, int minWidth, Button.OnPress onPress) {
			if (entryCollector == null) return SlotMachineV2Screen.this.button(label, x, y, minWidth, onPress);
			// the entry button joins the machine's control flow (SlotBody places it)
			Button proxy = Button.builder(label, onPress).build();
			entryCollector.add(new SlotButton(0, 0, 0, 18, label, SlotButton.Style.GOLD, b -> onPress.onPress(proxy)));
			return proxy;
		}

		@Override
		public void send(String action, CompoundTag args) {
			sendAction(action, args);
		}

		@Override
		public CompoundTag state() {
			return SlotMachineV2Screen.this.state();
		}

		@Override
		public void rebuild() {
			rebuildWidgets();
		}

		@Override
		public Font font() {
			return font;
		}
	}

	private final class ServerControls implements SlotBody.Controls {
		@Override
		public void spin(long bet) {
			CompoundTag t = new CompoundTag();
			t.putLong("bet", bet);
			sendAction("spin", t);
		}

		@Override
		public void skip() {
			sendAction("skip");
		}

		@Override
		public void pickChest(int chest) {
			CompoundTag t = new CompoundTag();
			t.putInt("chest", chest);
			sendAction("pick", t);
		}

		@Override
		public void buy(long bet) {
			CompoundTag t = new CompoundTag();
			t.putLong("bet", bet);
			sendAction("buy", t);
		}

		@Override
		public void auto(int count, int lossLimitTimesBet, boolean stopOnFeature, long bet) {
			CompoundTag t = new CompoundTag();
			t.putInt("count", count);
			t.putInt("loss_limit", lossLimitTimesBet);
			t.putBoolean("stop_feature", stopOnFeature);
			t.putLong("bet", bet);
			sendAction("auto", t);
		}

		@Override
		public void stopAuto() {
			sendAction("stop_auto");
		}

		@Override
		public void turbo(boolean on) {
			CompoundTag t = new CompoundTag();
			t.putBoolean("on", on);
			sendAction("turbo", t);
		}
	}
}
