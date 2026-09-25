package dev.nezo.burmaldaholic.games.extras.client.pvp;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.MatchOverlay;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.RevealState;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Base of the extras PvP match screens (Plinko Battle, Scratch Showdown; visual/extras.md §7.1, mockup
 * {@code extras_pvp_match.png}): the arena scene (red-lit in a grudge match, with the torn GRUDGE MATCH banner held
 * after the clash), the mode title at the top left and the step at the top right, the advance button (primary,
 * breathing while the server waits), Taunt… (a pictogram grid), the rules card and Close. Server-driven: renders the
 * {@link PvpModeView} (public state + revealed steps only) and sends {@code extras_action} game {@code pvp}
 * ({@code press}, {@code taunt}); it never decides anything. The countdown, the clash and the Final Reveal are the
 * shared overlay of the pvp client module.
 */
public abstract class PvpModeScreen extends dev.nezo.burmaldaholic.client.ui.CasinoScreen implements PvpScreens.ModeScreen {
	protected static final int W = Scene.W;
	protected static final int H = Scene.H;
	protected static final int TEXT = Kit.BONE;
	protected static final int MUTED = Kit.BONE_SHADE;
	protected static final int GOLD = Kit.GOLD;
	protected static final int RED = Kit.RED_LIGHT;
	protected static final int PAD = 6;
	/** Taunt lines in engine order (index = {@code PvpService#taunt} line). */
	static final List<String> TAUNTS = List.of("gg", "luck", "wow", "rigged", "again", "steel", "bye", "respect");

	protected final PvpModeView view = new PvpModeView();
	private final String pressKey;
	private final List<String> rulesKeys;
	protected int left;
	protected int top;
	protected int ticks;
	protected float partial;
	private boolean showTaunts;
	private boolean showRules;
	private int pressedStep = -1;
	protected final long openedAt = Util.getMillis();

	protected PvpModeScreen(Component title, String pressKey, List<String> rulesKeys, JsonObject first) {
		super(title, Scene.W, Scene.H);
		this.pressKey = pressKey;
		this.rulesKeys = rulesKeys;
		view.update(first, 0);
	}

	@Override
	public Screen screen() {
		return this;
	}

	@Override
	public final void update(JsonObject state) {
		int before = view.steps().size();
		boolean wasSettled = view.settled();
		view.update(state, ticks);
		for (int i = before; i < view.steps().size(); i++) {
			onStep(view.steps().get(i));
		}
		if (!wasSettled && view.settled()) {
			onSettled();
		}
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	/** A newly revealed step (sounds, banners). */
	protected void onStep(PvpModeView.StepView step) {}

	/** The outcome arrived (Final Reveal / result). */
	protected void onSettled() {}

	/** Top-bar parts: {left title, right status}. */
	protected abstract List<Component> topBar();

	/** Draws the mode's content in absolute coordinates (panel at {@link #left}, {@link #top}). */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY);

	/** Over the widgets (bubbles, banners). */
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	/** Panel-local {x, y, w} of press, taunt, close and rules in the side layout. */
	protected int[][] sideControlPositions() {
		return new int[][] {{300, 112, 88}, {300, 136, 88}, {300, 160, 88}, {368, 184, 20}};
	}

	/** Controls in the right column (4–6 player layouts) instead of the bottom bar. */
	protected boolean sideControls() {
		return false;
	}

	/** Is the current step a wait for the advance button? */
	protected boolean waiting() {
		PvpModeView.StepView s = view.last();
		return s != null && s.waitForAll() && !view.settled() && view.you() >= 0;
	}

	/** You pressed the advance button in the current step. */
	protected boolean pressed() {
		return pressedStep == view.steps().size() - 1;
	}

	/** Ticks left of the current step (for "Auto-drop in …"). */
	protected int ticksLeft(PvpModeView.StepView s) {
		return Math.max(0, s.ticks() - (ticks - s.arrived()));
	}

	protected static Component seconds(int ticks) {
		return Texts.plural("unit.burmaldaholic.second_acc", (ticks + 19) / 20);
	}

	@Override
	protected boolean showBanner() {
		return false;
	}

	@Override
	protected boolean showBalance() {
		return false;
	}

	/** The compact layout at small GUI sizes: the full 400 × 240 scene drawn at a lower whole GUI scale (FitScaled). */
	@Override
	protected boolean fitToScreen() {
		return true;
	}

	@Override
	protected void init() {
		super.init();
		left = Scene.left(width);
		top = Scene.top(height);
		boolean side = sideControls();
		int cx = W / 2;
		int[][] pos = side ? sideControlPositions()
			: new int[][] {{cx - 124, 207, 96}, {cx - 24, 207, 72}, {cx + 52, 207, 64}, {cx + 120, 207, 20}};
		KitButton press = KitButton.of(left + pos[0][0], top + pos[0][1], pos[0][2], Component.translatable(pressKey), KitButton.Style.PRIMARY, b -> {
			pressedStep = view.steps().size() - 1;
			send("press", new CompoundTag());
			rebuildWidgets();
		});
		press.active(waiting() && !pressed()).selected(waiting() && !pressed()).breathe(waiting() && !pressed());
		addRenderableWidget(press);
		addRenderableWidget(KitButton.of(left + pos[1][0], top + pos[1][1], pos[1][2], Component.translatable("gui.burmaldaholic.pvp.taunt.button"),
			KitButton.Style.SECONDARY, b -> {
				showTaunts = !showTaunts;
				showRules = false;
				rebuildWidgets();
			}).selected(showTaunts).active(view.you() >= 0));
		addRenderableWidget(KitButton.of(left + pos[2][0], top + pos[2][1], pos[2][2], Component.translatable("gui.burmaldaholic.common.close"),
			KitButton.Style.SECONDARY, b -> onClose()));
		addRenderableWidget(new KitButton(left + pos[3][0], top + pos[3][1], 20, 20, Component.translatable("gui.burmaldaholic.extras.rules_short"),
			KitButton.Style.SECONDARY, b -> {
				showRules = !showRules;
				showTaunts = false;
				rebuildWidgets();
			}).selected(showRules).tooltip(Component.translatable("gui.burmaldaholic.common.rules")));
		if (showTaunts) {
			for (int i = 0; i < TAUNTS.size(); i++) {
				int line = i;
				int bx = left + 40 + (i % 4) * 82;
				int by = top + 150 + (i / 4) * 24;
				addRenderableWidget(new KitButton(bx, by, 80, 20, Component.translatable("gui.burmaldaholic.pvp.taunt." + TAUNTS.get(i)),
					KitButton.Style.SECONDARY, b -> {
						CompoundTag args = new CompoundTag();
						args.putInt("line", line);
						send("taunt", args);
						showTaunts = false;
						rebuildWidgets();
					}).icon(new KitButton.Icon(PvpDraw.TAUNT_ICONS, 128, 16, i * 16, 0, 16, 16)));
			}
		}
	}

	protected static void send(String action, CompoundTag args) {
		if (ClientPlayNetworking.canSend(ExtrasActionPayload.TYPE)) {
			ClientPlayNetworking.send(new ExtrasActionPayload("pvp", action, args));
		}
	}

	@Override
	public void tick() {
		super.tick();
		ticks++;
		PvpModeView.StepView s = view.last();
		// the press button follows the timeline (new wait step / wait over)
		if (s != null && s.waitForAll() && ticks - s.arrived() == 1) {
			rebuildWidgets();
		}
		onTick();
	}

	protected void onTick() {}

	protected Scene scene() {
		return view.grudge() ? Scene.PVP_GRUDGE : Scene.PVP;
	}

	@Override
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		scene().backdrop(g, left, top);
		extractPlayArea(g, mouseX, mouseY);
		scene().frame(g, font, left, top, null);
	}

	/** Objects on the backdrop under the frame. */
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		List<Component> bar = topBar();
		Component right = bar.size() > 1 ? bar.get(1) : null;
		int rw = right == null ? 0 : font.width(right);
		if (!bar.isEmpty()) Kit.fit(g, font, bar.get(0), left + 16, top + 15, W - 32 - rw - 110, GOLD, true);
		if (right != null) g.text(font, right, left + W - 16 - rw, top + 15, GOLD, true);
		if (view.grudge() && !MatchOverlay.clashPlaying(RevealState.current())) {
			PvpDraw.grudgeBanner(g, font, left + W / 2, top + 22, left, left + W, 1e6, true);
		}
		extractContent(g, mouseX, mouseY);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractOverlay(g, mouseX, mouseY);
		if (showRules) {
			int x = left + 30;
			int y = top + 40;
			int w = W - 60;
			int lines = 0;
			for (String k : rulesKeys) {
				lines += font.split(Component.translatable(k), w - 16).size();
			}
			Scene.card(g, x, y, w, lines * 10 + 16);
			int ty = y + 8;
			for (String k : rulesKeys) {
				for (FormattedCharSequence line : font.split(Component.translatable(k), w - 16)) {
					g.text(font, line, x + 8, ty, TEXT, true);
					ty += 10;
				}
			}
		}
	}

	/** Name with the ALL-IN tag. */
	protected Component nameTag(int seat) {
		Component name = view.name(seat);
		if (seat >= 0 && seat < view.seats().size() && view.seats().get(seat).allIn()) {
			return Component.empty().append(name).append(" ").append(Component.translatable("gui.burmaldaholic.pvp.all_in_tag"));
		}
		return name;
	}

	/** Truncates a component to {@code width} pixels (names only, UI.md §0.1). */
	protected FormattedCharSequence fit(Component c, int width) {
		if (font.width(c) <= width) {
			return c.getVisualOrderText();
		}
		return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(c, width));
	}

	protected static void play(@Nullable SoundEvent sound, float pitch, float volume) {
		if (sound != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
		}
	}

	/** Ranking line "#k name — points". */
	protected Component row(int place, int seat, long points) {
		MutableComponent name = nameTag(seat).copy();
		return Component.translatable("gui.burmaldaholic.pvp.match.row", Texts.number(place), name, Texts.plural("unit.burmaldaholic.point", points));
	}
}
