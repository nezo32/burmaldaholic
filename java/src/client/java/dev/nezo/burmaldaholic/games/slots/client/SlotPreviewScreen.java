package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.games.slots.client.panels.SlotModel;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewMachines;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewTapes;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * PREVIEW of the slots v2 screen (lane J-L9) driven by a local fake "server" that plays the deterministic preview
 * tapes ({@link PreviewTapes}) through the same {@link SlotBody} / {@link SlotStage} as the real machine screen.
 * Used by the client GameTests (JS17) and for reviewing the animations before the v2 protocol (S-J5) lands. Keys:
 * Space spin / stop, N next scenario, M next machine. Never touches money.
 */
public final class SlotPreviewScreen extends Screen {
	private static final int GLFW_KEY_N = 78; // GLFW key code (lwjgl is not on the 26.3 compile path)
	private static final int GLFW_KEY_M = 77; // GLFW key code (lwjgl is not on the 26.3 compile path)
	private static final long LATENCY_MS = 120;

	private final Machine machine;
	private final List<String> scenarios = new ArrayList<>();
	private int scenarioIndex;
	private SlotBody body;
	private final SlotModel model = new SlotModel();
	private PreviewTapes.Scenario pending;
	private long pendingAt = -1;
	private long revealAt = -1;
	private long nextAutoAt = -1;
	private PreviewTapes.Scenario current;
	private boolean autoFeatures;
	private boolean credited = true;

	public SlotPreviewScreen(Machine machine, String scenario) {
		super(Component.translatable("gui.burmaldaholic.slots.machine." + machine.id));
		this.machine = machine;
		String prefix = switch (machine) {
			case OVERWORLD -> "ow_";
			case NETHER -> "ne_";
			case END -> "end_";
		};
		for (String n : PreviewTapes.NAMES) if (n.startsWith(prefix)) scenarios.add(n);
		scenarioIndex = Math.max(0, scenario == null ? 0 : scenarios.indexOf(scenario));
		model.def = PreviewMachines.def(machine);
		model.bets = new long[] {10, 25, PreviewTapes.BET, 100, 250};
		model.betIndex = 2;
		model.balance = 125_000;
		model.pools = PreviewTapes.POOLS.clone();
		model.buyAllowed = model.def.buyPriceFifths() > 0;
		model.rtpBasisPoints = switch (machine) {
			case OVERWORLD -> 9507;
			case NETHER -> 9512;
			case END -> 9496;
		};
		body = new SlotBody(model, new FakeServer(), title);
	}

	/** Non-interactive mode (spectator / autoplay behaviour: the hunt and the wheel follow the timeline). */
	public SlotPreviewScreen autoFeatures(boolean on) {
		this.autoFeatures = on;
		body.interactive(!on);
		return this;
	}

	public SlotBody body() {
		return body;
	}

	public PreviewTapes.Scenario current() {
		return current;
	}

	public String scenario() {
		return scenarios.isEmpty() ? null : scenarios.get(scenarioIndex);
	}

	@Override
	protected void init() {
		body.init(width, height, font, this::addRenderableWidget);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Starts the selected scenario now (tests; skips the fake latency). */
	public void playNow(String name) {
		PreviewTapes.Scenario s = PreviewTapes.get(name);
		start(s, Util.getMillis());
	}

	private void start(PreviewTapes.Scenario s, long now) {
		current = s;
		credited = false;
		TimingProfile shared = TimingProfile.SHARED.withSpeed(model.turbo ? 200 : 100);
		int seed = SeedMix.mix(SeedMix.hash(s.name()), (int) (now / 1000));
		body.stage().rest(s.restStops(), null);
		Timeline tl = SlotTimeline.build(s.tape(), s.def(), shared, FxSettings.localProfile(), seed, true, null);
		body.stage().play(s.tape(), tl, SpinClock.wall(tl, now), seed);
		model.balance -= s.tape().bought() ? (long) s.def().buyPriceFifths() * s.tape().bet() / 5 : s.tape().bet();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		if (pending != null && now >= pendingAt) {
			PreviewTapes.Scenario s = pending;
			pending = null;
			start(s, now);
		}
		if (revealAt >= 0 && now >= revealAt) {
			revealAt = -1;
			SpinTape.Hunt h = current == null ? null : current.tape().hunt();
			if (h != null) {
				int i = body.stage().hunt().board().opened();
				body.stage().huntReveal(h.entries()[Math.min(i, h.entries().length - 1)]);
				if (body.stage().hunt().board().ended()) {
					body.stage().huntRest(java.util.Arrays.copyOfRange(h.entries(), body.stage().hunt().board().opened(), h.entries().length));
				}
			}
		}
		SlotBody b = body;
		if (b.stage().finished() && current != null && !credited) {
			credited = true;
			model.balance += current.tape().totalChips();
			for (SpinTape.JackpotAward award : current.tape().jackpots()) model.balance += award.chips();
			nextAutoAt = model.autoLeft > 0 ? now + 1500 : -1;
			if (model.autoLeft == 0) model.autoLeft = -1;
		}
		if (model.autoLeft > 0 && nextAutoAt > 0 && now >= nextAutoAt && pending == null) {
			model.autoLeft--;
			nextAutoAt = -1;
			nextScenario();
			b.stage().preRoll(now);
			pending = PreviewTapes.get(scenario());
			pendingAt = now + LATENCY_MS;
		}
		b.drawBackground(g, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		body.drawOverlays(g, width, height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (body.mouseClicked(event.x(), event.y())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		return body.mouseScrolled(dy) || super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (body.keyPressed(event.key())) return true;
		if (event.key() == GLFW_KEY_N) {
			nextScenario();
			return true;
		}
		if (event.key() == GLFW_KEY_M && minecraft != null) {
			Machine next = Machine.values()[(machine.ordinal() + 1) % Machine.values().length];
			minecraft.gui.setScreen(new SlotPreviewScreen(next, null));
			return true;
		}
		return super.keyPressed(event);
	}

	private void nextScenario() {
		if (!scenarios.isEmpty()) scenarioIndex = (scenarioIndex + 1) % scenarios.size();
	}

	@Override
	public void removed() {
		body.close();
		super.removed();
	}

	/** Local fake server: plays the selected preview tape after a small latency; reveals hunt entries per pick. */
	private final class FakeServer implements SlotBody.Controls {
		@Override
		public void spin(long bet) {
			pending = PreviewTapes.get(scenario());
			pendingAt = Util.getMillis() + LATENCY_MS;
			if (autoFeatures) body.interactive(false);
		}

		@Override
		public void skip() {}

		@Override
		public void pickChest(int chest) {
			revealAt = Util.getMillis() + 180;
		}

		@Override
		public void buy(long bet) {
			nextAutoAt = -1;
			String name = machine == Machine.NETHER ? "ne_buy" : machine == Machine.END ? "end_fs" : "ow_fs";
			pending = PreviewTapes.get(name);
			pendingAt = Util.getMillis() + LATENCY_MS;
		}

		@Override
		public void auto(int count, int lossLimitTimesBet, boolean stopOnFeature, long bet) {
			model.autoLeft = count;
			nextAutoAt = Util.getMillis() + 1;
		}

		@Override
		public void stopAuto() {
			model.autoLeft = -1;
		}

		@Override
		public void turbo(boolean on) {}
	}
}
