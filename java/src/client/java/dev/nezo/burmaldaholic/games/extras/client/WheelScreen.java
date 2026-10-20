package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
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
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import dev.nezo.burmaldaholic.games.extras.logic.anim.WheelAnim;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasFlow;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasKeys;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasLayout;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasView;
import dev.nezo.burmaldaholic.games.extras.present.LocalBet;
import dev.nezo.burmaldaholic.games.extras.present.LocalBets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Wheel of Fortune, table UI v2 (REDESIGN.md §7.7 "Wheel: wheel left (cols 1–7), segment legend right with odds"; lane
 * E): the kit's TopBar and Dock (BetBar, primary <b>Spin</b> {@code Space}; pawn stakes — a held item, levels, hearts —
 * replace the BetBar with −/+ and are chosen with the Stake toggle) and the carnival scene: the wheel (face, rim with 24
 * bulbs, hub, flapper) on the purple fill, the legend (icon, name ×multiplier, count / total; the landed row gold). A
 * result starts the {@link WheelAnim} spin onto the server's segment; the stop beat dims the other wedges, the bulbs blink,
 * a win pops its icon and name; a creeper swells and the screen closes for the mob wave. Click the wheel or Enter skips.
 *
 * <p>Hit map ids: {@code felt.wheel} (skip), {@code felt.stake} (stake kind), {@code felt.party} (Wheel Party), plus the
 * kit's ids.
 */
final class WheelScreen extends V2TableScreen<ExtrasView> implements dev.nezo.burmaldaholic.client.fx.ClientFx.CelebrationGate {
	private static final int STOP_MS = 600;
	/** This machine's bet in the client session (per position, R-CDE m5). */
	private final LocalBet bet;
	private static final BetControl PAWN = new BetControl(true, 1);

	private int shownSeq = -1;
	private boolean seenState;
	private double angle;
	private double from;
	private double rest;
	private double room = 1;
	private long spinStart = -1;
	private double spinMs = 4000;
	private long skipAt = -1;
	private double skipFrom;
	private long stopAt = -1;
	private long lastPeg;
	private long lastTickMs;
	private boolean settledDone = true;
	private long heldBalance = -1;
	private ExtrasLayout.Scene scene;

	WheelScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.extras.wheel.title"));
		this.bet = LocalBets.of("wheel", menu.pos().asLong());
	}

	// ---- the pure parts ------------------------------------------------------------------------------------------------

	private boolean chips() {
		return PAWN.kind().equals(BetControl.CHIPS);
	}

	@Override
	protected ExtrasView view(CompoundTag s) {
		PAWN.validate(s);
		boolean busy = spinning();
		if (!chips()) {
			long max = PAWN.max(s);
			return ExtrasView.pawn(busy, null, PAWN.kind().equals(BetControl.ITEM) ? s.getLongOr("held_value", 0) : PAWN.amount(), max);
		}
		long bal = shownBalance();
		bet.clamp(s.getLongOr("max", 0), s.getLongOr("balance", 0));
		double top = 0;
		CompoundTag mult = s.getCompoundOrEmpty("multipliers");
		for (String k : mult.keySet()) top = Math.max(top, mult.getDoubleOr(k, 0));
		return ExtrasView.chips(bet.view(s.getLongOr("min", 1), s.getLongOr("max", 0), bal, busy), busy, null, (long) Math.floor(bet.amount() * top));
	}

	@Override
	protected Flow<ExtrasView> flow() {
		return ExtrasFlow.WHEEL;
	}

	private void local() {
		view = view(state());
		refresh();
	}

	@Override
	protected void refresh() {
		super.refresh();
		if (view != null && !chips() && !view.busy()) chrome.status.hint(Component.translatable(ExtrasKeys.HINT_STAKE, PAWN.shown(state())));
	}

	@Override
	protected BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				bet.add(denom, state().getLongOr("max", 0), shownBalance());
				local();
			}

			@Override
			public boolean setsAmount() {
				return true;
			}

			@Override
			public boolean amount(long amount) { // the bet-entry control's OK (lane HR)
				BET.set(amount, state().getLongOr("max", 0), shownBalance());
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
		switch (gate.action()) {
			case "spin" -> {
				CompoundTag args;
				if (chips()) {
					args = new CompoundTag();
					args.putString("stake", "chips");
					args.putLong("amount", bet.amount());
					bet.sent();
				} else {
					args = PAWN.args();
				}
				sendTracked("spin", args, "", 0);
			}
			case ExtrasFlow.MINUS -> {
				PAWN.step(state(), -1);
				local();
			}
			case ExtrasFlow.PLUS -> {
				PAWN.step(state(), 1);
				local();
			}
			default -> super.onGate(id, gate);
		}
	}

	// ---- state and the spin --------------------------------------------------------------------------------------------

	private List<String> segments() {
		ListTag list = state().getListOrEmpty("segments");
		List<String> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) out.add(list.getStringOr(i, "B"));
		return out.isEmpty() ? Wheel.DEFAULT_SEGMENTS : out;
	}

	private CompoundTag result() {
		return state().getCompoundOrEmpty("result");
	}

	private boolean spinning() {
		return spinStart >= 0 && !settledDone;
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
		int n = Math.max(2, newState.getListOrEmpty("segments").size());
		if (!seenState) {
			if (!newState.isEmpty()) {
				seenState = true;
				if (bet.amount() <= 0) bet.add(Math.max(1, newState.getLongOr("min", 1)), newState.getLongOr("max", 0), newState.getLongOr("balance", 0));
				if (seq >= 0) {
					shownSeq = seq;
					angle = WheelAnim.restAngle(r.getIntOr("index", 0), n, SeedMix.mix(SeedMix.hash("wheel"), seq));
				}
			}
		} else {
			if (seq < 0) shownSeq = -1;
			if (seq >= 0 && seq != shownSeq) {
				if (spinning()) finish(false);
				shownSeq = seq;
				bet.ack(); // the server played the amount: it becomes Rebet
				int index = r.getIntOr("index", 0);
				from = angle;
				rest = WheelAnim.restAngle(index, n, SeedMix.mix(SeedMix.hash("wheel"), seq));
				room = WheelAnim.settleRoom(rest, index, n);
				spinStart = Util.getMillis();
				spinMs = Kit.reduceMotion() ? WheelAnim.REDUCED_MS : newState.getIntOr("spin_ticks", 80) * 50.0 * 100 / Kit.speedPct();
				skipAt = -1;
				stopAt = -1;
				settledDone = false;
				lastPeg = WheelAnim.pegCount(from, 360.0 / n);
				if (!r.getBooleanOr("pawn", false)) heldBalance = newState.getLongOr("balance", 0) - r.getLongOr("net", 0);
				ClientCasinoState.holdBalanceDelta((int) spinMs + STOP_MS);
			}
		}
		super.onStateChanged(newState);
	}

	private double endAngle() {
		return from + WheelAnim.travel(from, rest, Kit.reduceMotion() ? 0 : WheelAnim.EXTRA_TURNS);
	}

	private double currentAngle() {
		if (spinStart < 0 || settledDone) return angle;
		long now = Util.getMillis();
		if (skipAt >= 0) {
			double a = WheelAnim.skip(skipFrom, endAngle(), now - skipAt);
			if (now - skipAt >= WheelAnim.SKIP_MS) finish(true);
			return a;
		}
		double ms = now - spinStart;
		double a = Kit.reduceMotion() ? WheelAnim.reduced(from, rest, ms) : WheelAnim.angle(from, rest, (int) spinMs, ms, room);
		if (ms >= spinMs) finish(false);
		return a;
	}

	@Override
	public boolean holdsCelebration(String game) {
		return dev.nezo.burmaldaholic.games.extras.server.ExtrasGames.WHEEL.equals(game) && spinning();
	}

	private boolean skip() {
		if (!spinning() || skipAt >= 0) return false;
		skipFrom = currentAngle();
		skipAt = Util.getMillis();
		return true;
	}

	private void finish(boolean viaSkip) {
		if (settledDone) return;
		settledDone = true;
		angle = endAngle();
		stopAt = Util.getMillis();
		heldBalance = -1;
		CompoundTag r = result();
		String code = r.getStringOr("code", "B");
		long net = r.getLongOr("net", 0);
		if (Wheel.CREEPER.equals(code)) {
			Kit.vanilla("entity.creeper.primed", 1f, 1f);
		} else if (net > 0) {
			FxSounds.play("wheel_stop", 1f, 1f);
			dev.nezo.burmaldaholic.client.fx.ClientFx.releaseCelebration();
		} else if (net == 0 || "H".equals(code) || "M".equals(code)) {
			FxSounds.play("push", 0.6f, 1f);
		} else {
			FxSounds.play("lose", 0.5f, 1f);
		}
		if (!r.getBooleanOr("pawn", false)) chrome.banner.show(net);
		local();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (stopAt >= 0 && Wheel.CREEPER.equals(result().getStringOr("code", "")) && Util.getMillis() - stopAt > 1000 && minecraft != null) {
			stopAt = -1;
			onClose(); // the chaos mob wave starts (server)
		}
	}

	@Override
	public void onClose() {
		if (spinning()) finish(true);
		super.onClose();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.isSelection() && spinning() && skip()) return true; // Space / Enter / keypad Enter, resolved by the running version
		return super.keyPressed(event);
	}

	// ---- layout -------------------------------------------------------------------------------------------------------

	private int wx() {
		return scene.object().x();
	}

	private int wy() {
		return scene.object().y();
	}

	private Box stakeBox() {
		Box side = scene.side();
		return new Box(side.x(), side.bottom() - 2 * scene.row() - 2, side.w(), scene.row());
	}

	private Box partyBox() {
		Box side = scene.side();
		return new Box(side.x(), side.bottom() - scene.row(), side.w(), scene.row());
	}

	@Override
	protected void layoutPlay(TableGrid grid, ExtrasView v, HitMap hits) {
		scene = ExtrasLayout.wheel(grid);
		Box o = scene.object();
		chrome.banner.regions(List.of(new Box(o.x() - 20, o.y() + o.h() / 2 + 20, o.w() + 40, 40), scene.side()), List.of());
		hits.add(KeyMap.Ids.felt("wheel"), o, true);
		if (PAWN.kinds(state()).size() > 1) hits.add(KeyMap.Ids.felt("stake"), stakeBox(), !v.busy());
		if (dev.nezo.burmaldaholic.games.extras.client.pvp.wheel.WheelPartyClient.buttonLabel(state().getCompoundOrEmpty("party")) != null) {
			hits.add(KeyMap.Ids.felt("party"), partyBox(), true);
		}
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		if (id.equals(KeyMap.Ids.felt("wheel"))) return skip();
		if (id.equals(KeyMap.Ids.felt("stake"))) {
			if (!enabled) {
				chrome.refuse(Gate.off("stake", ExtrasKeys.STAKE, dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_SPINNING));
				return true;
			}
			PAWN.nextKind(state());
			local();
			return true;
		}
		if (id.equals(KeyMap.Ids.felt("party"))) {
			dev.nezo.burmaldaholic.games.extras.client.pvp.wheel.WheelPartyClient.open(menu.pos());
			onClose();
			return true;
		}
		return false;
	}

	private List<Map.Entry<String, Integer>> legend() {
		Map<String, Integer> counts = new TreeMap<>();
		segments().forEach(c -> counts.merge(c, 1, Integer::sum));
		CompoundTag mult = state().getCompoundOrEmpty("multipliers");
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
		entries.sort((a, b) -> {
			int c = Double.compare(mult.getDoubleOr(b.getKey(), 0), mult.getDoubleOr(a.getKey(), 0));
			if (c != 0) return c;
			return Integer.compare(WheelDraw.iconIndex(a.getKey()), WheelDraw.iconIndex(b.getKey()));
		});
		return entries;
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, ExtrasView v, int mouseX, int mouseY, float partial) {
		if (scene == null) scene = ExtrasLayout.wheel(grid);
		SceneKit.fill(g, grid.play, "wheel");
		Box o = scene.object();
		List<String> segs = segments();
		int n = segs.size();
		double s = 360.0 / n;
		double theta = currentAngle();
		pegTicks(theta, s);
		g.enableScissor(grid.play.x(), grid.play.y(), grid.play.right(), grid.play.bottom());
		g.pose().pushMatrix();
		g.pose().translate(o.x(), o.y());
		g.pose().scale(scene.scale(), scene.scale());
		int wx = 0;
		int wy = 0;
		WheelDraw.stand(g, wx, wy);
		WheelDraw.face(g, wx, wy, segs, theta);
		double beat = stopAt >= 0 ? Util.getMillis() - stopAt : settledDone && spinStart >= 0 ? STOP_MS : -1;
		CompoundTag r = result();
		boolean shown = r.getIntOr("seq", -1) >= 0 && settledDone;
		if (shown) {
			int index = r.getIntOr("index", 0);
			boolean win = r.getLongOr("net", 0) > 0;
			boolean creeper = Wheel.CREEPER.equals(r.getStringOr("code", ""));
			g.pose().pushMatrix();
			g.pose().translate(wx + WheelDraw.C, wy + WheelDraw.C);
			g.pose().rotate((float) Math.toRadians(theta));
			for (int i = 0; i < n; i++) {
				if (i != index) WheelDraw.wedge(g, i * s, s / 2, 22, 79, 0x5E000000);
			}
			double pulse = beat < 0 || beat > 1000 || Kit.reduceMotion() ? 0.35 : 0.35 * (0.5 + 0.5 * Math.cos(beat / 1000.0 * Math.PI * 4));
			if (creeper) {
				boolean flash = Kit.flashes() && !Kit.reduceMotion() && beat >= 0 && beat < 1000 && ((int) (beat / 250)) % 2 == 0;
				WheelDraw.wedge(g, index * s, s / 2, 22, 79, flash ? 0x7040FF40 : 0x30FFFFFF);
			} else if (win) {
				WheelDraw.wedge(g, index * s, s / 2, 22, 79, Kit.alpha(0xFFFFFFFF, pulse));
			}
			if (!win || creeper) {
				g.pose().pushMatrix();
				g.pose().rotate((float) Math.toRadians(index * s - s / 2));
				g.fill(0, -79, 1, -22, Kit.BONE_SHADE);
				g.pose().popMatrix();
				g.pose().pushMatrix();
				g.pose().rotate((float) Math.toRadians(index * s + s / 2));
				g.fill(-1, -79, 0, -22, Kit.BONE_SHADE);
				g.pose().popMatrix();
			}
			g.pose().popMatrix();
		}
		WheelDraw.rim(g, wx, wy);
		long now = Util.getMillis();
		boolean still = Kit.reduceMotion();
		WheelDraw.bulbs(g, wx, wy, i -> {
			if (spinning() && !still) return (i + (int) Math.floor(theta / 15)) % 3 == 0 ? 1 : 0;
			if (shown && beat >= 0 && beat < STOP_MS && r.getLongOr("net", 0) > 0) return ((int) (beat / 150) + i) % 2 == 0 ? 2 : 1;
			if (still) return i % 2 == 0 ? 1 : 0;
			return (i + now / 1500) % 2 == 0 ? 1 : 0;
		});
		WheelDraw.hub(g, wx, wy);
		WheelDraw.flapper(g, wx, wy, spinning() && !still ? WheelAnim.flapper(theta, s) : 0);
		if (shown && r.getLongOr("net", 0) > 0 && beat >= 0) {
			popOut(g, wx, wy, r.getStringOr("code", "B"), beat);
		} else if (shown && Wheel.CREEPER.equals(r.getStringOr("code", "")) && beat >= 0 && beat < 1200) {
			int size = still || beat < 300 ? 40 : 48;
			if (size == 48) Kit.region(g, WheelDraw.ICONS_16, 128, 16, 16, 0, 16, 16, wx + WheelDraw.C - 24, wy + 16, 48, 48, 0xFFFFFFFF);
			else Kit.region(g, WheelDraw.ICONS_40, 320, 40, 40, 0, 40, 40, wx + WheelDraw.C - 20, wy + 16);
		}
		g.pose().popMatrix();
		g.disableScissor();
		side(g, v, shown ? r.getStringOr("code", "") : "", mouseX, mouseY);
	}

	private void popOut(GuiGraphicsExtractor g, int wx, int wy, String code, double beat) {
		double p = Kit.reduceMotion() ? 1 : Ease.OUT_BACK.apply(Math.min(1, beat / 300.0));
		int cx = wx + WheelDraw.C;
		int cy = wy + 12 + 24;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale((float) p, (float) p);
		Kit.sprite(g, Kit.extras("wheel_pop"), -24, -24, 48, 48);
		Kit.region(g, WheelDraw.ICONS_40, 320, 40, WheelDraw.iconIndex(code) * 40, 0, 40, 40, -20, -20);
		g.pose().popMatrix();
	}

	private void pegTicks(double theta, double pitch) {
		if (!spinning()) return;
		long peg = WheelAnim.pegCount(theta, pitch);
		if (peg == lastPeg) return;
		long now = Util.getMillis();
		long passed = Math.abs(peg - lastPeg);
		lastPeg = peg;
		if (now - lastTickMs < 50) return;
		lastTickMs = now;
		FxSounds.play("wheel_tick", 0.5f, WheelAnim.tickPitch(passed * pitch, 30));
	}

	/** The legend: one row per segment kind (icon, name ×multiplier, count / total), the landed row gold. */
	private void side(GuiGraphicsExtractor g, ExtrasView v, String landed, int mx, int my) {
		Box side = scene.side();
		SceneKit.caption(g, font, Component.translatable(ExtrasKeys.SEGMENTS), side, side.y() + 2);
		CompoundTag mult = state().getCompoundOrEmpty("multipliers");
		int total = segments().size();
		int y = side.y() + 15;
		int rowH = Math.max(11, Math.min(16, (stakeBox().y() - 4 - y) / Math.max(1, legend().size())));
		for (Map.Entry<String, Integer> e : legend()) {
			if (y + 10 > stakeBox().y() - 2) break;
			String code = e.getKey();
			boolean hit = code.equals(landed);
			if (hit) V2Draw.fill(g, new Box(side.x() - 2, y - 2, side.w() + 4, rowH - 1), 0x40FFD640);
			Kit.region(g, WheelDraw.ICONS_8, 64, 8, WheelDraw.iconIndex(code) * 8, 0, 8, 8, side.x(), y);
			double m = mult.getDoubleOr(code, 0);
			Component name = Component.translatable(Wheel.segmentNameKey(code));
			Component odds = Component.translatable("gui.burmaldaholic.extras.wheel.count", Texts.number(e.getValue()), Texts.number(total));
			Component times = Texts.raw("×" + Payouts.formatMultiplier(m)); // literal-ok: the multiplier sign
			int ow = font.width(odds);
			int tw = font.width(times);
			int color = m > 1 ? CasinoPalette.V2_WIN : m == 1 ? CasinoPalette.V2_YOU : CasinoPalette.V2_LOSE;
			if (m >= 5) color = CasinoPalette.V2_GOLD;
			V2Draw.left(g, font, name, new Box(side.x() + 11, y - 1, side.w() - 11 - tw - ow - 10, 10), 0, 1, hit ? CasinoPalette.V2_GOLD
				: CasinoPalette.V2_TEXT, true);
			g.text(font, times, side.right() - ow - 6 - tw, y, color, true);
			g.text(font, odds, side.right() - ow, y, CasinoPalette.V2_MUTED, true);
			y += rowH;
		}
		if (PAWN.kinds(state()).size() > 1) {
			SceneKit.toggle(g, font, stakeBox(), PAWN.kindLabel(), !chips(), !v.busy(), mx, my);
		}
		Component party = dev.nezo.burmaldaholic.games.extras.client.pvp.wheel.WheelPartyClient.buttonLabel(state().getCompoundOrEmpty("party"));
		if (party != null) SceneKit.toggle(g, font, partyBox(), party, false, true, mx, my);
	}
}
