package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import dev.nezo.burmaldaholic.games.extras.logic.anim.WheelAnim;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Wheel of Fortune (UI.md §9; visual/extras.md §4, mockup {@code extras_wheel_landing.png}; extras-pvp.md §3.3): the
 * carnival scene with the wheel block on the left (face, rim with 24 bulbs, hub, leather flapper, stand) and the
 * segment legend, bet well and controls on the right. A result starts the {@link WheelAnim} spin onto the server's
 * segment: pull-back, {@code outQuint} spin with the bulbs chasing and the flapper ticking on every peg (ticks rise in
 * pitch as it slows), the settle inside the segment, then the stop beat: the landed wedge brightens and the others dim,
 * the bulbs blink gold, the icon pops out with its name (wins only; LOSS / RETURN / PUSH get a plain outline) and the
 * shared celebration plays the server tier. A creeper swells and the screen closes for the mob wave. Click on the wheel,
 * Space or Enter skip to the stop; reduce motion turns straight to the segment. The chip counter is held until the stop.
 */
final class WheelScreen extends ExtrasTableScreen {
	private static final int WX = 22;
	private static final int WY = 32;
	private static final int RX = 212;
	private static final int STOP_MS = 600;
	private static long lastAmount = 1;

	private final BetControl bet;
	private final List<String> legendCodes = new ArrayList<>();
	private int shownSeq = -1;
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

	WheelScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.extras.wheel.title"), Scene.WHEEL);
		this.bet = new BetControl(true, lastAmount);
	}

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

	@Override
	protected void stateArrived(CompoundTag newState) {
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		int n = Math.max(2, newState.getListOrEmpty("segments").size());
		if (shownSeq < 0 && seq >= 0) {
			// screen (re)opened: the last result at rest
			shownSeq = seq;
			int index = r.getIntOr("index", 0);
			angle = WheelAnim.restAngle(index, n, SeedMix.mix(SeedMix.hash("wheel"), seq));
			return;
		}
		if (seq >= 0 && seq != shownSeq) {
			if (spinning()) finish(false);
			shownSeq = seq;
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
			if (!r.getBooleanOr("pawn", false)) holdBalance(newState.getLongOr("balance", 0) - r.getLongOr("net", 0));
			ClientCasinoState.holdBalanceDelta((int) spinMs + STOP_MS);
		}
	}

	private double endAngle() {
		return from + WheelAnim.travel(from, rest, Kit.reduceMotion() ? 0 : WheelAnim.EXTRA_TURNS);
	}

	/** Current rotation (and drives the landing). */
	private double currentAngle() {
		if (spinStart < 0 || settledDone) return angle;
		long now = Util.getMillis();
		if (skipAt >= 0) {
			double a = WheelAnim.skip(skipFrom, endAngle(), now - skipAt);
			if (now - skipAt >= WheelAnim.SKIP_MS) finish(true);
			return a;
		}
		double ms = now - spinStart;
		double a;
		if (Kit.reduceMotion()) {
			a = WheelAnim.reduced(from, rest, ms);
		} else {
			a = WheelAnim.angle(from, rest, (int) spinMs, ms, room);
		}
		if (ms >= spinMs) finish(false);
		return a;
	}

	@Override
	protected boolean skip() {
		if (!spinning() || skipAt >= 0) return false;
		skipFrom = currentAngle();
		skipAt = Util.getMillis();
		return true;
	}

	/** The wheel stops: the stop beat starts, the celebration plays the server tier. */
	private void finish(boolean viaSkip) {
		if (settledDone) return;
		settledDone = true;
		angle = endAngle();
		stopAt = Util.getMillis();
		releaseBalance();
		CompoundTag r = result();
		String code = r.getStringOr("code", "B");
		long net = r.getLongOr("net", 0);
		long stake = r.getLongOr("stake", 0);
		if (Wheel.CREEPER.equals(code)) {
			Kit.vanilla("entity.creeper.primed", 1f, 1f);
		} else if (net > 0) {
			FxSounds.play("wheel_stop", 1f, 1f);
			if (stake > 0 && !r.getBooleanOr("pawn", false)) {
				long ret = stake + net;
				CelebrationOverlay.get().play(CelebrationRequest.core(WinTier.of(ret, stake, WinTierTable.DEFAULT), ret, stake,
					SeedMix.mix(SeedMix.hash("wheel"), shownSeq)));
			}
		} else if (net == 0 || "H".equals(code) || "M".equals(code)) {
			FxSounds.play("push", 0.6f, 1f);
		} else {
			FxSounds.play("lose", 0.5f, 1f);
		}
		if (minecraft != null) rebuild();
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
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double dx = event.x() - (leftPos + WX + WheelDraw.C);
		double dy = event.y() - (topPos + WY + WheelDraw.C);
		if (event.button() == 0 && dx * dx + dy * dy < 92 * 92 && skip()) return true;
		return super.mouseClicked(event, doubleClick);
	}

	// ---- layout ------------------------------------------------------------------------------------------------

	@Override
	protected void layout() {
		CompoundTag s = state();
		bet.validate(s);
		boolean busy = spinning();
		boolean steps = !bet.kind().equals(BetControl.ITEM);
		button(RX - 4, 186, 58, 20, Component.translatable("gui.burmaldaholic.extras.bet_minus"), KitButton.Style.SECONDARY, b -> {
			bet.step(state(), -1);
			rebuild();
		}).active(steps && !busy);
		button(RX + 57, 186, 58, 20, Component.translatable("gui.burmaldaholic.extras.bet_plus"), KitButton.Style.SECONDARY, b -> {
			bet.step(state(), 1);
			rebuild();
		}).active(steps && !busy);
		button(RX + 118, 186, 58, 20, Component.translatable("gui.burmaldaholic.common.max"), KitButton.Style.SECONDARY, b -> {
			bet.max(state(), true);
			rebuild();
		}).active(steps && !busy);
		button(RX - 4, 210, 116, 20, Component.translatable("gui.burmaldaholic.common.spin"), KitButton.Style.PRIMARY, b -> {
			lastAmount = Math.max(1, bet.amount());
			sendAction("spin", bet.args());
		}).active(!busy);
		button(RX + 118, 210, 58, 20, Component.translatable("gui.burmaldaholic.extras.leave"), KitButton.Style.SECONDARY, b -> onClose());
		if (bet.kinds(s).size() > 1) {
			button(RX + 92, 148, 84, 12, bet.kindLabel(), KitButton.Style.SECONDARY, b -> {
				bet.nextKind(state());
				rebuild();
			}).active(!busy);
		}
		Component party = dev.nezo.burmaldaholic.games.extras.client.pvp.wheel.WheelPartyClient.buttonLabel(s.getCompoundOrEmpty("party"));
		if (party != null) { // PvP Wheel Party entry (J-M2)
			button(94, 210, 112, 20, party, KitButton.Style.SECONDARY, b -> {
				dev.nezo.burmaldaholic.games.extras.client.pvp.wheel.WheelPartyClient.open(menu.pos());
				onClose(); // the party panel replaces the machine screen
			});
		}
	}

	private List<Map.Entry<String, Integer>> legend() {
		Map<String, Integer> counts = new TreeMap<>();
		segments().forEach(c -> counts.merge(c, 1, Integer::sum));
		CompoundTag mult = state().getCompoundOrEmpty("multipliers");
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
		entries.sort((a, b) -> {
			int c = Double.compare(mult.getDoubleOr(a.getKey(), 0), mult.getDoubleOr(b.getKey(), 0));
			if (c != 0) return c;
			return Integer.compare(WheelDraw.iconIndex(a.getKey()), WheelDraw.iconIndex(b.getKey()));
		});
		return entries;
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int wx = leftPos + WX;
		int wy = topPos + WY;
		List<String> segs = segments();
		int n = segs.size();
		double s = 360.0 / n;
		double theta = currentAngle();
		pegTicks(theta, s);
		WheelDraw.stand(g, wx, wy);
		WheelDraw.face(g, wx, wy, segs, theta);
		// stop beat: dim the others, brighten the landed wedge (wins) or outline it (loss / return / push)
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
				// a plain 1 px outline of the landed wedge: its two edges
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
		double flap = spinning() && !still ? WheelAnim.flapper(theta, s) : 0;
		WheelDraw.flapper(g, wx, wy, flap);
		// hover ring (click = spin / skip)
		double dx = mouseX - (WX + WheelDraw.C);
		double dy = mouseY - (WY + WheelDraw.C);
		// the pop-out of a win (icon at 40 px on the gold burst) — creeper swells instead
		if (shown && r.getLongOr("net", 0) > 0 && beat >= 0) {
			popOut(g, wx, wy, r.getStringOr("code", "B"), beat);
		} else if (shown && Wheel.CREEPER.equals(r.getStringOr("code", "")) && beat >= 0 && beat < 1200) {
			int size = still || beat < 300 ? 40 : 48;
			if (size == 48) Kit.region(g, WheelDraw.ICONS_16, 128, 16, 16, 0, 16, 16, wx + WheelDraw.C - 24, wy + 16, 48, 48, 0xFFFFFFFF);
			else Kit.region(g, WheelDraw.ICONS_40, 320, 40, 40, 0, 40, 40, wx + WheelDraw.C - 20, wy + 16);
		}
		// right column wells (under the widgets)
		int rx = leftPos + RX;
		Scene.inset(g, rx - 4, topPos + 32, 180, 110);
		Scene.inset(g, rx - 4, topPos + 146, 180, 34);
		if (dx * dx + dy * dy < 92 * 92 && !spinning()) {
			// nothing: the whole wheel is the hit area; the hover glint ring is part of the rim art
		}
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
		if (now - lastTickMs < 50) return; // ≤ 20 ticks per second (every 2nd–3rd peg at speed)
		lastTickMs = now;
		double speed = passed * pitch; // degrees since the last frame
		FxSounds.play("wheel_tick", 0.5f, WheelAnim.tickPitch(speed, 30));
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag s = state();
		CompoundTag r = result();
		boolean shown = r.getIntOr("seq", -1) >= 0 && settledDone;
		String landed = shown ? r.getStringOr("code", "") : "";
		// legend
		Kit.text(g, font, Component.translatable("gui.burmaldaholic.extras.wheel.segments"), RX, 37, Kit.GOLD);
		CompoundTag mult = s.getCompoundOrEmpty("multipliers");
		List<Map.Entry<String, Integer>> rows = legend();
		int total = segments().size();
		legendCodes.clear();
		int y = 48;
		for (Map.Entry<String, Integer> e : rows) {
			if (y > 128) break;
			String code = e.getKey();
			legendCodes.add(code);
			Kit.region(g, WheelDraw.ICONS_8, 64, 8, WheelDraw.iconIndex(code) * 8, 0, 8, 8, RX, y);
			Component name = Component.translatable("gui.burmaldaholic.extras.wheel.pop", Component.translatable(Wheel.segmentNameKey(code)),
				Texts.decimal(Payouts.formatMultiplier(mult.getDoubleOr(code, 0))));
			Component count = Component.translatable("gui.burmaldaholic.extras.wheel.count", Texts.number(e.getValue()), Texts.number(total));
			int cw = font.width(count);
			Kit.fit(g, font, name, RX + 12, y, 172 - 12 - cw - 4, code.equals(landed) ? Kit.GOLD : Kit.BONE, true);
			Kit.right(g, font, count, RX + 172, y, Kit.BONE_SHADE);
			y += 11;
		}
		// bet and result
		Kit.text(g, font, Component.translatable("gui.burmaldaholic.extras.bet"), RX, 151, Kit.BONE_SHADE);
		int bx = RX + font.width(Component.translatable("gui.burmaldaholic.extras.bet")) + 4;
		Kit.sprite(g, Kit.core("fx/chip_" + BetControl.chipDenom(bet.amount())), bx, 151, 8, 8);
		Kit.fit(g, font, bet.shown(s), bx + 12, 151, bet.kinds(s).size() > 1 ? RX + 88 - bx - 12 : 170 - bx + RX, Kit.GOLD, true);
		if (spinning()) {
			Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.wheel.spinning"), RX, 166, 170, Kit.LILAC, true);
		} else if (shown) {
			long net = r.getLongOr("net", 0);
			if (net > 0) {
				Component yw = Component.translatable("gui.burmaldaholic.extras.wheel.you_win");
				Kit.text(g, font, yw, RX, 166, Kit.BONE);
				Kit.text(g, font, Texts.raw("+").append(Texts.number(net)).withStyle(ChatFormatting.BOLD), RX + font.width(yw) + 6, 166, Kit.BONUS); // literal-ok: sign
			} else {
				Kit.fit(g, font, resultLine(net), RX, 166, 170, Kit.BONE, true);
			}
		} else {
			Kit.fit(g, font, limitsLine(), RX, 166, 170, Kit.BONE_SHADE, true);
		}
		// the pop-out's name plaque
		double beat = stopAt >= 0 ? Util.getMillis() - stopAt : shown && spinStart >= 0 ? STOP_MS : -1;
		if (shown && r.getLongOr("net", 0) > 0 && beat >= 0) {
			Component label = Component.translatable("gui.burmaldaholic.extras.wheel.pop", Component.translatable(Wheel.segmentNameKey(landed)),
				Texts.decimal(Payouts.formatMultiplier(mult.getDoubleOr(landed, 0))));
			int lw = font.width(label) + 16;
			int cx = WX + WheelDraw.C;
			double a = Kit.reduceMotion() ? 1 : Math.min(1, beat / 300.0);
			Kit.sprite(g, Kit.pvp("mode_banner"), cx - lw / 2, WY + 58, lw, 16, Kit.fade(a));
			Kit.centered(g, font, label, cx, WY + 62, Kit.alpha(Kit.GOLD, a));
		}
	}

	static Component resultLine(long net) {
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.common.result.win", Texts.chips(net)).withStyle(ChatFormatting.GREEN);
		}
		if (net < 0) {
			return Component.translatable("gui.burmaldaholic.common.result.loss", Texts.chips(-net)).withStyle(ChatFormatting.RED);
		}
		return Component.translatable("gui.burmaldaholic.common.result.push").withStyle(ChatFormatting.GRAY);
	}
}
