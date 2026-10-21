package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.fx.ClientFx;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Input;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.DockLayout;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.slots.client.panels.Overlays;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotLayout;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotModel;
import dev.nezo.burmaldaholic.games.slots.client.pvp.ShowdownPanel;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.TapeCodec;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotDock;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFlow;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
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
 *
 * <p>Owner B6 (v0.2.0): on the v2 kit like every other game. {@link V2TableScreen} sizes it by {@code ViewportPolicy}
 * and lays it out in the shared {@link TableGrid} rows: TopBar (title, balance, {@code ?} = paytable and jackpot ladder),
 * the cabinet and reels in the PlayArea ({@link SlotLayout#docked}), and the Dock from {@link SlotFlow}: the bet stepper
 * ({@code - bet +}, coin x multiplier in the hint), Buy / Autoplay / Turbo / Slot Showdown, and the primary Spin / Stop.
 * Keys: Space = primary, A = autoplay, T = turbo.
 *
 * <p>T-SLT D3: when the full Dock does not fit the frame ({@link SlotDock#collapse}; 427 × 240 and 480 × 270), Buy /
 * Autoplay / Turbo / Showdown fold into a More button ({@link SlotFlow#COMPACT}) that opens them as a column just above
 * the Dock (targets {@code more.<action>}); A / T keep working.
 */
public class SlotMachineV2Screen extends V2TableScreen<SlotView> {
	/** Slot Showdown panel column width (inside the PlayArea while open). */
	private static final int SD_PANEL_W = 140;
	private final SlotModel model = new SlotModel();
	/** Slot Showdown (PvP) entry (a Dock action) + panel (widgets in the PlayArea while open). */
	private final ShowdownPanel showdown = new ShowdownPanel(new ShowdownHost());
	private CompoundTag lastPvp = new CompoundTag();
	private Box play = Box.EMPTY;
	private String[] lockArgs = new String[0];
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
	/** D3: the Dock is the compact one (More), the More menu is open, its gates and boxes (registration order). */
	private boolean dockMore;
	private boolean moreOpen;
	private List<Gate> moreGates = List.of();
	private List<Box> moreBoxes = List.of();
	/** A / T held down in the compact Dock (the OS auto-repeat must not toggle Turbo per repeat). */
	private final Set<String> heldMore = new HashSet<>();

	public SlotMachineV2Screen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.inventoryLabelY = -10_000;
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
		if (!play.equals(Box.EMPTY)) body.initDocked(SlotLayout.docked(play));
	}

	// ---- v2 kit ------------------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		super.init();
		rebuildShowdown();
	}

	/** The Slot Showdown panel's widgets while it is open (inside the PlayArea). */
	private void rebuildShowdown() {
		clearWidgets();
		if (!showdown.open() || play.equals(Box.EMPTY)) return;
		int px = play.x() + play.w() - 8 - SD_PANEL_W;
		showdown.buildWidgets(px, SD_PANEL_W, play.x() + 8, px - play.x() - 16, play.y() + play.h() - 6);
	}

	@Override
	protected Flow<SlotView> flow() {
		return dockMore ? SlotFlow.COMPACT : SlotFlow.INSTANCE;
	}

	/** Test hook: whether the Dock is the compact one (More). */
	public boolean dockMore() {
		return dockMore;
	}

	/** Test hook: whether the More menu is open. */
	public boolean moreOpen() {
		return moreOpen;
	}

	/** Hit id of a More-menu entry. */
	public static String moreId(String action) {
		return "more." + action;
	}

	@Override
	protected @org.jspecify.annotations.Nullable BetPort betPort() {
		return null; // the bet is the Dock stepper over the machine's ladder
	}

	/** The Dock's view of the machine now (server state + the running stage). */
	private SlotView snapshot() {
		boolean busy = body != null && (body.stage().spinning() || body.stage().preRolling());
		Gate sd = showdown.open() ? null : showdown.entryGate();
		return new SlotView(model.playable, model.lockReason != null, model.lockReason == null ? new String[0] : lockArgs, busy, model.autoLeft >= 0,
			model.bet(), model.betIndex, model.bets.length, model.balance, model.coinValue(), model.canBuy(), model.buyPrice(), model.autoplayAllowed,
			model.turboAllowed, model.turbo, sd);
	}

	@Override
	protected void refresh() {
		if (view == null || font == null) return;
		KeyMap k = KeyMap.standard();
		k.bind(KeyMap.A, KeyMap.Ids.action(SlotFlow.AUTO));
		k.bind(KeyMap.T, KeyMap.Ids.action(SlotFlow.TURBO));
		chrome.keys(k);
		chrome.stepper.set(SlotFlow.betDown(view), SlotFlow.betUp(view), Texts.chips(view.bet()), null, null);
		// D3: the full Dock first; when it does not fit the frame, the compact one (the same pure decision as SlotDockFitTest)
		boolean wasMore = dockMore;
		dockMore = false;
		super.refresh();
		if (SlotDock.collapse(chrome.grid(), chrome.dock())) {
			dockMore = true;
			super.refresh();
		}
		if (!dockMore || wasMore != dockMore) moreOpen = false;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (body != null && !body.stage().spinning()) model.balance = latestBalance;
		SlotView now = snapshot();
		if (view != null && !now.equals(view)) {
			view = now;
			refresh();
		}
	}

	@Override
	protected void layoutPlay(TableGrid grid, SlotView v, HitMap hits) {
		if (!grid.play.equals(play)) {
			play = grid.play;
			if (body != null) body.initDocked(SlotLayout.docked(play));
			rebuildShowdown();
		}
	}

	/** The More menu's targets, on top of everything (registered last). */
	@Override
	public void layoutOverlay(DockLayout.Dock dock, HitMap hits) {
		moreGates = List.of();
		moreBoxes = List.of();
		if (!dockMore || !moreOpen || view == null || font == null) return;
		Box anchor = hits.box(KeyMap.Ids.action(SlotFlow.MORE)).orElse(null);
		if (anchor == null) return;
		List<Gate> gates = SlotFlow.secondary(view);
		int[] w = new int[gates.size()];
		for (int i = 0; i < w.length; i++) w[i] = V2Button.width(font, V2Draw.label(gates.get(i)), null);
		List<Box> boxes = SlotDock.menu(chrome.grid(), anchor, w);
		for (int i = 0; i < gates.size(); i++) hits.add(moreId(gates.get(i).action()), boxes.get(i), gates.get(i).enabled());
		moreGates = gates;
		moreBoxes = boxes;
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press press) {
		if (!id.startsWith("more.")) return false;
		for (Gate g : moreGates) {
			if (!moreId(g.action()).equals(id)) continue;
			moreOpen = false;
			if (!g.enabled()) {
				chrome.refuse(g);
				refresh();
			} else {
				onGate(id, g);
			}
			return true;
		}
		return false;
	}

	/** A / T in the compact Dock: the folded Autoplay (or Stop autoplay) / Turbo gate. */
	private boolean moreKey(String name) {
		String action = KeyMap.A.equals(name) ? SlotFlow.AUTO : KeyMap.T.equals(name) ? SlotFlow.TURBO : null;
		if (action == null || view == null) return false;
		if (!heldMore.add(name)) return true; // auto-repeat of a held key
		for (Gate g : SlotFlow.secondary(view)) {
			boolean match = g.action().equals(action) || action.equals(SlotFlow.AUTO) && g.action().equals(SlotFlow.STOP_AUTO);
			if (!match) continue;
			moreOpen = false;
			if (g.enabled()) onGate(moreId(g.action()), g);
			else chrome.refuse(g);
			return true;
		}
		return false;
	}

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, SlotView v, int mouseX, int mouseY, float partial) {
		if (showdown.open()) {
			int px = play.x() + play.w() - 8 - SD_PANEL_W;
			showdown.drawBackground(g, play.x() + 8, play.y() + 4, px - play.x() - 16, play.y() + play.h() - 6);
			return;
		}
		if (body != null && body.layout() != null) body.drawBackground(g, mouseX, mouseY);
	}

	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, TableGrid grid, SlotView v, int mouseX, int mouseY, float partial) {
		if (showdown.open()) {
			int px = play.x() + play.w() - 8 - SD_PANEL_W;
			showdown.drawLabels(g, px, play.x() + 8, play.y() + 4, px - play.x() - 16, play.y() + play.h() - 6);
			return;
		}
		if (bodyShown()) body.drawOverlays(g, width, height, mouseX, mouseY);
		for (int i = 0; i < Math.min(moreGates.size(), moreBoxes.size()); i++) {
			Gate gate = moreGates.get(i);
			Box b = moreBoxes.get(i);
			V2Draw.fill(g, b.inset(-1), 0xC0000000);
			V2Button.draw(g, font, b, V2Draw.label(gate), null, V2Sprites.ButtonStyle.NORMAL, gate.enabled(), b.contains(mouseX, mouseY), false, false);
		}
	}

	@Override
	protected void openRules() {
		if (bodyShown()) body.openPaytable();
	}

	@Override
	public void onGate(String id, Gate gate) {
		if (body == null) return;
		switch (gate.action()) {
			case SlotFlow.SPIN, SlotFlow.SKIP -> body.spinPressed();
			case SlotFlow.STOP_AUTO, SlotFlow.AUTO -> body.autoPressed();
			case SlotFlow.BUY -> body.openBuy();
			case SlotFlow.TURBO -> body.toggleTurbo();
			case SlotFlow.BET_DOWN -> body.changeBet(-1);
			case SlotFlow.BET_UP -> body.changeBet(1);
			case SlotFlow.SHOWDOWN -> showdown.openPanel();
			case SlotFlow.MORE -> moreOpen = !moreOpen;
			default -> {
				return;
			}
		}
		view = snapshot();
		refresh();
	}

	@Override
	protected SlotView view(CompoundTag state) {
		CompoundTag s = state.getCompoundOrEmpty("v2");
		ensureBody(s);
		latestBalance = state.getLongOr("balance", latestBalance);
		// F6: the balance panel follows the reels, never ahead of them
		if (!body.stage().spinning()) model.balance = latestBalance;
		long[] bets = s.getLongArray("bets").orElse(new long[0]);
		if (bets.length > 0) model.bets = bets;
		model.coin = s.getLongOr("coin", 0);
		if (!body.stage().spinning()) {
			long bet = s.getLongOr("bet", model.bet());
			for (int i = 0; i < model.bets.length; i++) if (model.bets[i] == bet) model.betIndex = i;
		}
		long[] pools = s.getLongArray("jackpots").orElse(null);
		if (pools != null && pools.length == 4) model.pools = pools;
		boolean vipOk = s.getBooleanOr("vip_ok", true);
		model.playable = s.getBooleanOr("enabled", true) && vipOk && bets.length > 0;
		// a machine above the player's VIP tier: a lock state with its reason, not just a greyed-out SPIN
		Component minTier = dev.nezo.burmaldaholic.core.service.VipTiers.name(s.getIntOr("min_vip", 0));
		Component myTier = dev.nezo.burmaldaholic.core.service.VipTiers.name(s.getIntOr("vip", 0));
		model.lockReason = vipOk ? null : Component.translatable("gui.burmaldaholic.slots.vip_lock", minTier, myTier);
		lockArgs = new String[] {minTier.getString(), myTier.getString()};
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
		}
		// Slot Showdown: refresh the open panel when the PvP part of the state changes (the Dock entry follows the view)
		CompoundTag pvp = state.getCompoundOrEmpty("pvp");
		if (!pvp.equals(lastPvp)) {
			lastPvp = pvp.copy();
			if (showdown.open() && minecraft != null) rebuildShowdown();
		}
		return snapshot();
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

	/** A result of the player's own spin newer than this (shared ms) is played from its arrival (autoplay, slow links). */
	static final int ACTOR_FRESH_MS = 2000;

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
			startTick = (long) Math.floor(SpinClock.timeSource().levelMs(0f) / 50.0) - holdMs / 50;
		}
		SpinTape.Hunt h = tape.hunt();
		huntOpened = h == null ? 0 : h.opened();
		huntComplete = tape.totalFifths() >= 0;
		TimelineSeed ts = new TimelineSeed("slots." + machine.id, seq, startTick, seed, speedPct);
		Timeline tl = timeline(tape);
		boolean mine = spin.getBooleanOr("mine", false);
		body.interactive(mine);
		body.stage().rest(restStops, restCells);
		double sharedNow = SpinClock.timeSource().sharedMs(ts, 0);
		// S1 (SY-SPEC §2): the player who pressed is anchored at the ARRIVAL on local time (the pre-roll hands off into
		// the reels); a reopened screen mid-spin or a spectator follows the shared clock
		boolean actor = mine && holdMs < 0 && (body.stage().preRolling() || sharedNow < ACTOR_FRESH_MS);
		double elapsed;
		if (actor) {
			elapsed = body.stage().playActor(tape, tl, seed, SpinClock.wallNow());
		} else {
			body.stage().play(tape, tl, SpinClock.server(tl, ts, () -> mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)), seed);
			elapsed = sharedNow;
		}
		if (mine) {
			// F6: the HUD balance delta waits for the reels and the roll-up
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
			f.holdWeights(), rings, d.getLongOr("jackpot_ref", f.jackpotRef()), f.jackpotSeedMult(), f.contributionPpm(), f.ownedMult(),
			d.getIntArray("gem").filter(a -> a.length == 2).orElse(f.gemPpm()));
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
		return body != null && body.layout() != null && !showdown.open();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// a slot panel (paytable, buy, autoplay) is modal over the PlayArea and the Dock
		if (bodyShown() && body.overlays().mode() != Overlays.Mode.NONE) return body.mouseClicked(event.x(), event.y());
		if (moreOpen) { // a click anywhere but More and its entries closes the menu (and still acts)
			String id = chrome.hits().at(event.x(), event.y()).map(HitMap.Target::id).orElse("");
			if (!id.startsWith("more.") && !id.equals(KeyMap.Ids.action(SlotFlow.MORE))) {
				moreOpen = false;
				refresh();
			}
		}
		if (super.mouseClicked(event, doubleClick)) return true;
		return bodyShown() && body.mouseClicked(event.x(), event.y());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		return bodyShown() && body.mouseScrolled(dy) || super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Esc closes an open slot panel first; Space / A / T are kit keys (primary, autoplay, turbo)
		if (bodyShown() && body.overlays().mode() != Overlays.Mode.NONE && body.keyPressed(event.key())) return true;
		String name = V2Input.keyName(event.key());
		if (moreOpen && KeyMap.ESCAPE.equals(name)) {
			moreOpen = false;
			refresh();
			return true;
		}
		if (dockMore && moreKey(name)) return true;
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		String name = V2Input.keyName(event.key());
		if (name != null) heldMore.remove(name);
		return super.keyReleased(event);
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
			return SlotMachineV2Screen.this.button(label, x, y, minWidth, onPress);
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
			rebuildShowdown();
			view = snapshot();
			refresh();
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
