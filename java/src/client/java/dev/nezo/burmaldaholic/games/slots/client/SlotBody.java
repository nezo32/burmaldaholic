package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotGeometry.Rect;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.client.panels.Overlays;
import dev.nezo.burmaldaholic.games.slots.client.panels.SidePanels;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotButton;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotLayout;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotModel;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.MeterModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * The slot machine screen body (SLOTS.md §10.5, slots.md §4; JS2): layout (400 × 240 or compact 320 × 220),
 * backdrop, cabinet and marquee, the four jackpot meters, the side panels, the reel {@link SlotStage}, the control row
 * (bet −/+, buy, auto, turbo, paytable, the round SPIN ↔ STOP button) with press / invalid feedback, the paytable /
 * buy / autoplay panels and the strata (reels under the widgets, feature overlays and celebrations above). Shared by
 * the real machine screen and the preview screen; the host supplies the {@link Controls} that talk to the server.
 */
public final class SlotBody implements StageHost {
	private static final int GLFW_KEY_ESCAPE = 256; // GLFW key code (lwjgl is not on the 26.3 compile path)
	private static final int GLFW_KEY_SPACE = 32; // GLFW key code (lwjgl is not on the 26.3 compile path)
	private static final int GLFW_KEY_ENTER = 257; // GLFW key code (lwjgl is not on the 26.3 compile path)
	private static final int GLFW_KEY_KP_ENTER = 335; // GLFW key code (lwjgl is not on the 26.3 compile path)
	private static final int GLFW_KEY_A = 65; // GLFW key code (lwjgl is not on the 26.3 compile path)
	private static final int GLFW_KEY_T = 84; // GLFW key code (lwjgl is not on the 26.3 compile path)
	/** What the controls ask for (the real screen sends table actions; the preview plays fake tapes). */
	public interface Controls {
		void spin(long bet);

		void skip();

		void pickChest(int chest);

		void buy(long bet);

		void auto(int count, int lossLimitTimesBet, boolean stopOnFeature, long bet);

		void stopAuto();

		void turbo(boolean on);
	}

	private final SlotModel model;
	private final SlotStage stage;
	private final Controls controls;
	private final Component name;
	private final MeterModel meters = new MeterModel();
	private final Overlays overlays = new Overlays();
	private final List<SlotButton> buttons = new ArrayList<>();
	/** Host-supplied buttons that join the control flow (the Slot Showdown entry); see {@link #extraButtons}. */
	private final List<SlotButton> extraButtons = new ArrayList<>();
	private SlotLayout layout;
	private SlotButton spin;
	private SlotButton betDown;
	private SlotButton betUp;
	private SlotButton buy;
	private SlotButton auto;
	private SlotButton turbo;
	private SlotButton paytable;
	private boolean interactive = true;
	private long betChangedAt = -1;
	private int betDir;
	private int jackpotDropped;

	public SlotBody(SlotModel model, Controls controls, Component machineName) {
		this.model = model;
		this.controls = controls;
		this.name = machineName;
		this.stage = new SlotStage(this, model.def.machine(), model.def);
	}

	public SlotStage stage() {
		return stage;
	}

	public SlotModel model() {
		return model;
	}

	public SlotLayout layout() {
		return layout;
	}

	public MeterModel meters() {
		return meters;
	}

	public Overlays overlays() {
		return overlays;
	}

	public List<SlotButton> buttons() {
		return buttons;
	}

	public void interactive(boolean on) {
		this.interactive = on;
	}

	private boolean serverPaced;

	/** The real machine screen: the shared clock is the server's (see {@link StageHost#serverPaced()}). */
	public void serverPaced(boolean on) {
		this.serverPaced = on;
	}

	@Override
	public boolean serverPaced() {
		return serverPaced;
	}

	@Override
	public boolean interactive() {
		return interactive;
	}

	// ---- layout and widgets -----------------------------------------------------------------------------------

	/** Extra host buttons for the control flow (after Paytable); takes effect on the next {@link #init}. */
	public void extraButtons(List<SlotButton> extra) {
		extraButtons.clear();
		extraButtons.addAll(extra);
	}

	/** (Re)builds the layout and the control widgets for a screen of {@code w × h}. */
	public void init(int w, int h, Font font, Consumer<AbstractWidget> add) {
		layout = SlotLayout.of(w, h);
		stage.layout(layout.wx, layout.wy, layout.cell);
		buttons.clear();
		SlotLayout l = layout;
		int bh = l.rowH;
		// bet group: "BET" over a value plate between − and + (the value never shrinks: EN and RU fit, slots.md §4.14)
		betDown = new SlotButton(l.betMinusX, l.betRowY, l.betButton, l.betButton, Component.translatable("gui.burmaldaholic.slots.bet_down"),
			SlotButton.Style.SECONDARY, b -> changeBet(-1)).icon(SlotSprites.ICON_MINUS, null).iconOnly(true);
		betDown.setTooltip(Tooltip.create(Component.translatable("gui.burmaldaholic.slots.bet_down")));
		betUp = new SlotButton(l.betPlusX, l.betRowY, l.betButton, l.betButton, Component.translatable("gui.burmaldaholic.slots.bet_up"),
			SlotButton.Style.SECONDARY, b -> changeBet(1)).icon(SlotSprites.ICON_PLUS, null).iconOnly(true);
		betUp.setTooltip(Tooltip.create(Component.translatable("gui.burmaldaholic.slots.bet_up")));
		// the other buttons flow into ≤ 2 rows between the bet group and the SPIN button (compact: icons only)
		List<SlotButton> flow = new ArrayList<>();
		if (model.canBuy()) {
			buy = new SlotButton(0, 0, 0, bh, buyLabel(), SlotButton.Style.GOLD, b -> openBuy()).icon(SlotSprites.ICON_BONUS, null);
			flow.add(buy);
		} else {
			buy = null;
		}
		auto = new SlotButton(0, 0, 0, bh, autoLabel(), SlotButton.Style.SECONDARY, b -> autoPressed(b)).icon(SlotSprites.ICON_AUTO, null)
			.iconOnly(l.compact);
		if (model.autoplayAllowed) flow.add(auto);
		turbo = new SlotButton(0, 0, 0, bh, Component.translatable("gui.burmaldaholic.slots.turbo"), SlotButton.Style.SECONDARY, b -> toggleTurbo())
			.icon(SlotSprites.TURBO_OFF, SlotSprites.TURBO_ON).iconOnly(l.compact);
		if (model.turboAllowed) flow.add(turbo);
		paytable = new SlotButton(0, 0, 0, bh, Component.translatable("gui.burmaldaholic.common.paytable"), SlotButton.Style.SECONDARY,
			b -> overlays.open(Overlays.Mode.PAYTABLE, Util.getMillis())).icon(SlotSprites.ICON_PAYTABLE, null).iconOnly(l.compact);
		flow.add(paytable);
		flow.addAll(extraButtons);
		for (SlotButton b : flow) if (b.iconOnly()) b.setTooltip(Tooltip.create(b.getMessage()));
		int[] widths = new int[flow.size()];
		for (int i = 0; i < widths.length; i++) widths[i] = flow.get(i).preferredWidth(font);
		if (!l.flowFits(widths)) {
			// too many / too long labels (Russian + the Showdown entry): the secondary controls become icons
			for (SlotButton b : new SlotButton[] {auto, turbo, paytable}) {
				b.iconOnly(true);
				b.setTooltip(Tooltip.create(b.getMessage()));
			}
			for (int i = 0; i < widths.length; i++) widths[i] = flow.get(i).preferredWidth(font);
		}
		SlotLayout.Rect[] rects = l.flow(widths);
		for (int i = 0; i < flow.size(); i++) {
			SlotButton b = flow.get(i);
			b.setX(rects[i].x());
			b.setY(rects[i].y());
			b.setWidth(rects[i].w());
			b.setHeight(rects[i].h());
		}
		spin = new SlotButton(l.spinX, l.spinY, l.spinW, l.spinH, spinLabel(), SlotButton.Style.SPIN, b -> spinPressed());
		buttons.add(betDown);
		buttons.add(betUp);
		buttons.addAll(flow);
		buttons.add(spin);
		for (SlotButton b : buttons) add.accept(b);
		refreshButtons();
	}

	private Component spinLabel() {
		return stage.spinning() ? Component.translatable("gui.burmaldaholic.slots.stop") : Component.translatable("gui.burmaldaholic.slots.spin", Texts.chips(model.bet()));
	}

	private Component buyLabel() {
		return Component.translatable("gui.burmaldaholic.slots.buy.button", Texts.chips(model.buyPrice()));
	}

	private Component autoLabel() {
		return model.autoLeft >= 0 ? Component.translatable("gui.burmaldaholic.slots.stop_auto") : Component.translatable("gui.burmaldaholic.slots.auto");
	}

	/** Enables / relabels the controls for the current state (called every frame; cheap). */
	public void refreshButtons() {
		if (spin == null) return;
		boolean busy = stage.spinning();
		spin.stopMode(busy);
		spin.setMessage(spinLabel());
		spin.caption(busy ? Component.translatable("gui.burmaldaholic.slots.stop") : Texts.chips(model.bet()));
		spin.active = busy || model.playable && model.autoLeft < 0;
		betDown.active = !busy && model.betIndex > 0;
		betUp.active = !busy && model.betIndex < model.bets.length - 1;
		if (buy != null) {
			buy.active = !busy && model.playable && model.autoLeft < 0;
			buy.setMessage(buyLabel());
		}
		auto.setMessage(autoLabel());
		auto.active = model.autoLeft >= 0 || !busy && model.playable;
		turbo.toggled(model.turbo);
	}

	// ---- actions ----------------------------------------------------------------------------------------------

	private void changeBet(int dir) {
		int next = model.betIndex + dir;
		if (next < 0 || next >= model.bets.length) {
			(dir < 0 ? betDown : betUp).shake();
			return;
		}
		model.betIndex = next;
		betChangedAt = Util.getMillis();
		betDir = dir;
		SlotSounds.vanilla(SoundEvents.NOTE_BLOCK_HAT, dir > 0 ? 1.2f : 0.9f, 0.5f);
		refreshButtons();
	}

	/** Space / Enter / the SPIN button: spin, or Stop (= skip, never skips the result), or a feature action. */
	public void spinPressed() {
		long now = Util.getMillis();
		if (stage.bigWin().skip(stage)) return;
		if (stage.spinning()) {
			if (stage.actionKey()) return;
			stage.skip();
			controls.skip();
			return;
		}
		if (!model.playable) {
			invalid("gui.burmaldaholic.error.disabled");
			return;
		}
		if (model.balance < model.bet()) {
			invalid("gui.burmaldaholic.error.insufficient_funds");
			return;
		}
		SlotSounds.click();
		stage.preRoll(now);
		controls.spin(model.bet());
		refreshButtons();
	}

	/** The server rejected the action: the reels decelerate back, the button shakes, the error line slides in (F1). */
	public void rejected(String errorKey) {
		stage.reject(Util.getMillis());
		invalid(errorKey);
	}

	private void invalid(String key) {
		spin.shake();
		model.errorKey = key;
		model.errorAt = Util.getMillis();
	}

	private void openBuy() {
		if (stage.spinning()) return;
		overlays.open(Overlays.Mode.BUY, Util.getMillis());
	}

	private void autoPressed(SlotButton b) {
		if (model.autoLeft >= 0) {
			controls.stopAuto();
			return;
		}
		overlays.open(Overlays.Mode.AUTO, Util.getMillis());
	}

	private void toggleTurbo() {
		model.turbo = !model.turbo;
		controls.turbo(model.turbo);
		SlotSounds.click();
		refreshButtons();
	}

	// ---- StageHost ----------------------------------------------------------------------------------------------

	@Override
	public void pickChest(int chest) {
		controls.pickChest(chest);
	}

	@Override
	public int[] meterCenter(int tier) {
		SlotLayout l = layout;
		for (int i = 0; i < l.meterTiers.length; i++) if (l.meterTiers[i] == tier) return new int[] {l.meterX(i) + l.meterW / 2, l.meterY + 8};
		return new int[] {l.left + l.width / 2, l.meterY + 8};
	}

	@Override
	public int[] winPanelCenter() {
		return SidePanels.winCenter(layout);
	}

	@Override
	public int[] featurePanelCenter() {
		return SidePanels.featureCenter(layout);
	}

	// ---- per frame ----------------------------------------------------------------------------------------------

	/** Stratum 1 (under the widgets): world, cabinet, meters, panels, the reels. */
	public void drawBackground(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		long now = Util.getMillis();
		stage.update(now);
		updateMeters(now);
		refreshButtons();
		SlotLayout l = layout;
		CabinetArt.backdrop(g, l, stage);
		WinTier tier = stage.active() ? stage.bigWin().panelTier(stage) : WinTier.LOSS;
		boolean jackpot = stage.active() && stage.jackpots().active(stage) != null;
		CabinetArt.cabinet(g, l, stage, name, tier, jackpot);
		CabinetArt.meters(g, l, stage, meters, model.pools);
		SidePanels.draw(g, l, stage, model);
		stage.drawWindow(g, mouseX, mouseY);
		CabinetArt.glass(g, l);
		SidePanels.label(g, l, stage, model);
		SidePanels.balance(g, l, stage, model);
		drawBet(g, l);
		if (!l.compact) {
			Rect pay = SidePanels.paytableArea(l);
			boolean hot = pay != null && mouseX >= pay.x() && mouseX < pay.right() && mouseY >= pay.y() && mouseY < pay.bottom();
			if (hot && overlays.mode() == Overlays.Mode.NONE) SlotDraw.frame(g, pay.x() - 1, pay.y() - 1, pay.w() + 2, pay.h() + 2, 1, 0x80FFD640);
		}
	}

	/** The bet group: "BET" label, the value plate with a chip glyph (the number flips on a change, 120 ms). */
	private void drawBet(GuiGraphicsExtractor g, SlotLayout l) {
		Font font = stage.font();
		int px = l.betPlateX;
		int pw = l.betPlateW;
		SlotDraw.centeredFit(g, font, Component.translatable("gui.burmaldaholic.slots.bet_label"), px + pw / 2, l.betLabelY, pw + 2 * l.betButton, 0xFFD6C6F0);
		int py = l.betRowY;
		int ph = l.betButton;
		if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.VALUE_PLATE, px, py, pw, ph);
		else SlotDraw.plate(g, px, py, pw, ph, 0xFF0C0616, 0xFF0C0616, 0xFFFFD640);
		Component value = Texts.number(model.bet());
		int tw = Math.min(font.width(value), pw - 20);
		int cx = px + (pw - (tw + 11)) / 2;
		SlotDraw.chip(g, cx + 4, py + ph / 2);
		long ms = betChangedAt < 0 ? 1000 : Util.getMillis() - betChangedAt;
		int dy = stage.reduceMotion() || ms >= 120 ? 0 : (int) Math.round((1 - ms / 120.0) * 8 * (betDir > 0 ? 1 : -1));
		g.enableScissor(px + 3, py + 3, px + pw - 3, py + ph - 3);
		SlotDraw.textFit(g, font, value, cx + 11, py + (ph - 8) / 2 + 1 + dy, tw, 0xFFFFE680, true);
		g.disableScissor();
	}

	/** Stratum 2/3 (above the widgets): feature overlays, celebrations, modal panels. */
	public void drawOverlays(GuiGraphicsExtractor g, int screenW, int screenH, int mouseX, int mouseY) {
		g.nextStratum();
		stage.drawOverlays(g, screenW, screenH);
		overlays.draw(g, layout, stage, model, mouseX, mouseY);
	}

	private void updateMeters(long now) {
		// the own jackpot's plate drops during its celebration (not as someone else's WON!)
		Beat jp = stage.active() ? stage.jackpots().active(stage) : null;
		for (int i = 0; i < 4; i++) meters.holdDrop(i, jp != null && jp.arg(0) == i + 1);
		meters.update(model.pools, now);
		if (jp != null) jackpotDropped = jp.arg(0);
	}

	// ---- input --------------------------------------------------------------------------------------------------

	/** Mouse click before the widgets; true when consumed. */
	public boolean mouseClicked(double mx, double my) {
		if (overlays.mode() != Overlays.Mode.NONE) {
			Overlays.Action a = overlays.click(mx, my, model);
			switch (a) {
				case CLOSE -> overlays.close();
				case BUY_CONFIRM -> {
					overlays.close();
					if (model.balance < model.buyPrice()) {
						invalid("gui.burmaldaholic.error.insufficient_funds");
					} else {
						stage.preRoll(Util.getMillis());
						controls.buy(model.bet());
					}
				}
				case AUTO_START -> {
					if (overlays.lossLimit < 0) {
						model.errorKey = "gui.burmaldaholic.slots.error.loss_limit_required";
						model.errorAt = Util.getMillis();
					} else {
						overlays.close();
						int count = model.autoCounts[Math.max(0, overlays.autoCount)];
						controls.auto(count, model.lossLimits[overlays.lossLimit], overlays.stopOnFeature, model.bet());
					}
				}
				default -> {
				}
			}
			return true;
		}
		if (stage.bigWin().overlayShowing(stage) && stage.bigWin().skip(stage)) return true;
		Rect pay = layout.compact ? null : SidePanels.paytableArea(layout);
		if (pay != null && !stage.spinning() && mx >= pay.x() && mx < pay.right() && my >= pay.y() && my < pay.bottom()) {
			SlotSounds.click();
			overlays.open(Overlays.Mode.PAYTABLE, Util.getMillis());
			return true;
		}
		if (stage.click(mx, my)) return true;
		SlotLayout l = layout;
		boolean inWindow = mx >= l.wx && mx < l.wx + l.windowW() && my >= l.wy && my < l.wy + l.windowH();
		if (inWindow && stage.spinning()) {
			stage.skip();
			controls.skip();
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double dy) {
		if (overlays.mode() == Overlays.Mode.PAYTABLE) {
			overlays.scroll(dy);
			return true;
		}
		return false;
	}

	/** Keys: Space/Enter spin / stop / feature action, A autoplay, T turbo, Esc closes a panel. */
	public boolean keyPressed(int key) {
		if (key == GLFW_KEY_ESCAPE && overlays.mode() != Overlays.Mode.NONE) {
			overlays.close();
			return true;
		}
		if (overlays.mode() != Overlays.Mode.NONE) return false;
		if (key == GLFW_KEY_SPACE || key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
			spinPressed();
			return true;
		}
		if (key == GLFW_KEY_A && model.autoplayAllowed) {
			autoPressed(auto);
			return true;
		}
		if (key == GLFW_KEY_T && model.turboAllowed) {
			toggleTurbo();
			return true;
		}
		return false;
	}

	/** Esc / close / disconnect: the running step jumps to its terminal frame (F8); loops stop. */
	public void close() {
		stage.close();
	}

	/** Beats of the running spin that ended the reveal gate (for tests). */
	public boolean spinDone() {
		return !stage.spinning() && stage.active() && stage.finished();
	}

	/** Debug / tests: kind of the latest started beat. */
	public String lastBeatKind() {
		if (stage.script() == null) return SlotTimeline.END;
		Beat last = null;
		for (Beat b : stage.script().timeline().beats()) if (b.at() <= stage.t()) last = b;
		return last == null ? SlotTimeline.SPIN_UP : last.kind();
	}
}
