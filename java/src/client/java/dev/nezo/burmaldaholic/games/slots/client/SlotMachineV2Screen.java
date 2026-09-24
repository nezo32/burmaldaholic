package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotModel;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import dev.nezo.burmaldaholic.games.slots.v2.logic.TapeCodec;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewMachines;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Slots v2 machine screen (SLOTS.md §10.5, slots.md §4; JS2) on the table-screen protocol. Everything visual is the
 * shared {@link SlotBody}; this class only maps the server state to it and sends the actions. Registered by the
 * slots cut-over (S-J5) in place of the v1 {@code SlotMachineScreen}.
 *
 * <p><b>State contract proposed to lane J-L8 (JS13)</b> — keys of the machine state tag:
 * <pre>
 * v:2 · machine:"overworld|nether|end" · balance · bets:long[] · bet_index · pools:long[4] (0 = no meter)
 * enabled · vip_ok · turbo_allowed · buy_allowed · autoplay_allowed · auto_counts:int[] · loss_limits:int[]
 * rtp_bp · buy_rtp_bp · auto:{left, mine}
 * def:{strips:[int[]×5], pays:int[33], scatter:int[3], bonus_mask, fs:int[3], retrigger, fs_cap, fs_mult,
 *      ladder:int[], ladder_free:int[], cap, buy}          (omitted → the SLOTS.md defaults)
 * spin:{seq, start_tick, seed, speed_pct, tape:TapeCodec string (the section the client may see, F7),
 *       rest_stops:int[5], rest_cells:int[15], anticipation, mine, player}
 * hunt:{seq, opened, value (entry of the latest pick), rest:int[] (at the end only)}
 * </pre>
 * Actions: {@code spin{bet}}, {@code skip}, {@code pick{chest}}, {@code buy{bet}},
 * {@code auto{count, loss_limit, stop_feature, bet}}, {@code stop_auto}, {@code turbo{on}}. Rejections arrive as the
 * base {@code sendError}: the reels decelerate back to the previous window (F1).
 */
public class SlotMachineV2Screen extends CasinoTableScreen {
	private final SlotModel model = new SlotModel();
	private SlotBody body;
	private Machine machine;
	private int playedSeq = -1;
	private int huntOpened;
	private boolean restSent;

	public SlotMachineV2Screen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 400, 240);
		this.inventoryLabelY = -10_000;
		this.titleLabelY = -10_000;
	}

	@Override
	protected void init() {
		super.init();
	}

	private void ensureBody(CompoundTag s) {
		Machine m = Machine.byId(s.getStringOr("machine", "overworld"));
		if (body != null && m == machine) return;
		machine = m;
		model.def = readDef(m, s.getCompoundOrEmpty("def"));
		body = new SlotBody(model, new ServerControls(), Component.translatable("gui.burmaldaholic.slots.machine." + m.id));
		if (minecraft != null) {
			clearWidgets();
			body.init(width, height, font, this::addRenderableWidget);
		}
	}

	@Override
	protected void rebuildWidgets() {
		clearWidgets();
		if (body != null) body.init(width, height, font, this::addRenderableWidget);
	}

	@Override
	protected void onStateChanged(CompoundTag s) {
		ensureBody(s);
		model.balance = s.getLongOr("balance", model.balance);
		model.bets = s.getLongArray("bets").orElse(model.bets);
		if (s.contains("bet_index") && !body.stage().spinning()) model.betIndex = s.getIntOr("bet_index", model.betIndex);
		long[] pools = s.getLongArray("pools").orElse(null);
		if (pools != null && pools.length == 4) model.pools = pools;
		model.playable = s.getBooleanOr("enabled", true) && s.getBooleanOr("vip_ok", true);
		model.turboAllowed = s.getBooleanOr("turbo_allowed", true);
		model.buyAllowed = s.getBooleanOr("buy_allowed", false);
		model.autoplayAllowed = s.getBooleanOr("autoplay_allowed", true);
		model.autoCounts = s.getIntArray("auto_counts").orElse(model.autoCounts);
		model.lossLimits = s.getIntArray("loss_limits").orElse(model.lossLimits);
		model.rtpBasisPoints = s.getIntOr("rtp_bp", model.rtpBasisPoints);
		model.buyRtpBasisPoints = s.getIntOr("buy_rtp_bp", model.buyRtpBasisPoints);
		CompoundTag auto = s.getCompoundOrEmpty("auto");
		model.autoLeft = s.contains("auto") && auto.getBooleanOr("mine", false) ? auto.getIntOr("left", 0) : -1;
		if (s.contains("spin")) playSpin(s.getCompoundOrEmpty("spin"));
		if (s.contains("hunt")) hunt(s.getCompoundOrEmpty("hunt"));
	}

	private void playSpin(CompoundTag spin) {
		int seq = spin.getIntOr("seq", -1);
		if (seq == playedSeq) return;
		SpinTape tape;
		try {
			tape = TapeCodec.decode(spin.getStringOr("tape", ""));
		} catch (RuntimeException notYet) {
			// the v2 protocol / codec is not live yet (lane J-L8): keep the reels at rest
			return;
		}
		playedSeq = seq;
		huntOpened = 0;
		restSent = false;
		int[] restStops = spin.getIntArray("rest_stops").orElse(new int[5]);
		int[] restCells = spin.getIntArray("rest_cells").orElse(null);
		int seed = spin.getIntOr("seed", 0);
		int speed = Math.max(25, spin.getIntOr("speed_pct", 100));
		TimelineSeed ts = new TimelineSeed("slots." + machine.id, seq, spin.getLongOr("start_tick", 0), seed, speed);
		Timeline tl = PreviewTimeline.buildOrPreview(tape, model.def, restStops, TimingProfile.SHARED.withSpeed(speed), FxSettings.localProfile(), seed,
			spin.getBooleanOr("anticipation", true));
		body.interactive(spin.getBooleanOr("mine", true));
		body.stage().rest(restStops, restCells != null && restCells.length == 15 ? restCells : null);
		body.stage().play(tape, tl, SpinClock.server(tl, ts, () -> Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)), seed);
	}

	private void hunt(CompoundTag h) {
		int opened = h.getIntOr("opened", 0);
		if (opened > huntOpened) {
			huntOpened = opened;
			body.stage().huntReveal(h.getIntOr("value", 0));
		}
		int[] rest = h.getIntArray("rest").orElse(null);
		if (rest != null && !restSent) {
			restSent = true;
			body.stage().huntRest(rest);
		}
	}

	/** Machine definition from the state, or the SLOTS.md defaults for anything omitted. */
	static MachineDef readDef(Machine m, CompoundTag d) {
		MachineDef base = PreviewMachines.def(m);
		if (d.isEmpty()) return base;
		int[][] strips = base.strips();
		ListTag st = d.getListOrEmpty("strips");
		if (st.size() == 5) {
			strips = new int[5][];
			for (int r = 0; r < 5; r++) strips[r] = st.getIntArray(r).orElse(base.strips()[r]);
		}
		int[][] pays = base.paysFifths();
		int[] flat = d.getIntArray("pays").orElse(null);
		if (flat != null && flat.length == pays.length * 3) {
			pays = new int[pays.length][3];
			for (int i = 0; i < pays.length; i++) System.arraycopy(flat, i * 3, pays[i], 0, 3);
		}
		SymbolRole[] roles = base.roles();
		return new MachineDef(m, base.codes(), roles, strips, pays, d.getIntArray("scatter").orElse(base.scatterFifths()),
			d.getIntOr("bonus_mask", base.bonusReelsMask()), d.getIntArray("fs").orElse(base.freeSpins()), d.getIntOr("retrigger", base.retrigger()),
			d.getIntOr("fs_cap", base.fsCap()), d.getIntOr("fs_mult", base.fsMultiplier()), d.getIntArray("ladder").orElse(base.ladder()),
			d.getIntArray("ladder_free").orElse(base.ladderFree()), d.getIntOr("cap", base.capMultiple()), d.getIntOr("buy", base.buyPriceFifths()));
	}

	@Override
	public void showError(Component message) {
		super.showError(message);
		if (body != null && body.stage().preRolling()) body.rejected("gui.burmaldaholic.error.round_in_progress");
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (body == null) {
			super.extractBackground(g, mouseX, mouseY, a);
			return;
		}
		body.drawBackground(g, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (body != null) body.drawOverlays(g, width, height, mouseX, mouseY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		// the body draws its own labels; the base error line stays
		super.extractLabels(g, xm, ym);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (body != null && body.mouseClicked(event.x(), event.y())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		return body != null && body.mouseScrolled(dy) || super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (body != null && body.keyPressed(event.key())) return true;
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		if (body != null) body.close();
		super.removed();
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
