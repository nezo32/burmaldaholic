package dev.nezo.burmaldaholic.games.extras.client.pvp;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Base of the extras PvP match screens (PVP.md §7.4, §8.5): a 400 × 240 felt panel, the mode's top bar,
 * [advance] [Taunt…] [Rules] buttons, the taunt picker and the rules overlay. Server-driven: renders the
 * {@link PvpModeView} (public state + revealed steps only) and sends {@code extras_action} game {@code pvp}
 * ({@code press}, {@code taunt}); it never decides anything.
 */
public abstract class PvpModeScreen extends Screen implements PvpScreens.ModeScreen {
	protected static final int W = 400;
	protected static final int H = 240;
	protected static final int FELT = 0xFF1E5E3A;
	protected static final int FELT_BORDER = 0xFF0E2E1C;
	protected static final int TEXT = 0xFFFFFFFF;
	protected static final int MUTED = 0xFFDDDDDD;
	protected static final int GOLD = 0xFFFFD700;
	protected static final int RED = 0xFFFF5555;
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

	protected PvpModeScreen(Component title, String pressKey, List<String> rulesKeys, JsonObject first) {
		super(title);
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

	/** Top-bar parts, joined with " · ". */
	protected abstract List<Component> topBar();

	/** Draws the mode's content in absolute coordinates (panel at {@link #left}, {@link #top}). */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY);

	/** Is the current step a wait for the advance button? */
	protected boolean waiting() {
		PvpModeView.StepView s = view.last();
		return s != null && s.waitForAll() && !view.settled() && view.you() >= 0;
	}

	/** Ticks left of the current step (for "Auto-drop in …"). */
	protected int ticksLeft(PvpModeView.StepView s) {
		return Math.max(0, s.ticks() - (ticks - s.arrived()));
	}

	protected static Component seconds(int ticks) {
		return Texts.plural("unit.burmaldaholic.second_acc", (ticks + 19) / 20);
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = Math.max(2, (height - H) / 2);
		int y = top + H - 24;
		int x = left + PAD;
		Button press = Button.builder(Component.translatable(pressKey).withStyle(ChatFormatting.BOLD), b -> {
			PvpModeView.StepView s = view.last();
			pressedStep = s == null ? -1 : view.steps().size() - 1;
			send("press", new CompoundTag());
			rebuildWidgets();
		}).bounds(x, y, 80, 20).build();
		press.active = waiting() && pressedStep != view.steps().size() - 1;
		addRenderableWidget(press);
		x += 83;
		addRenderableWidget(Button.builder(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), b -> {
			showTaunts = !showTaunts;
			rebuildWidgets();
		}).bounds(x, y, 70, 20).build()).active = view.you() >= 0;
		x += 73;
		addRenderableWidget(Button.builder(Component.translatable("gui.burmaldaholic.common.rules"), b -> {
			showRules = !showRules;
			rebuildWidgets();
		}).bounds(x, y, 60, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.burmaldaholic.common.close"), b -> onClose())
			.bounds(left + W - PAD - 60, y, 60, 20).build());
		if (showTaunts) {
			int tx = left + PAD;
			int ty = y - 22;
			for (int i = 0; i < TAUNTS.size(); i++) {
				Component label = Component.translatable("gui.burmaldaholic.pvp.taunt." + TAUNTS.get(i));
				int w = Math.min(W - 2 * PAD, font.width(label) + 10);
				if (tx + w > left + W - PAD) {
					tx = left + PAD;
					ty -= 22;
				}
				int line = i;
				addRenderableWidget(Button.builder(label, b -> {
					CompoundTag args = new CompoundTag();
					args.putInt("line", line);
					send("taunt", args);
					showTaunts = false;
					rebuildWidgets();
				}).bounds(tx, ty, w, 20).build());
				tx += w + 3;
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
		ticks++;
		PvpModeView.StepView s = view.last();
		// the press button follows the timeline (new wait step / wait over)
		if (s != null && s.waitForAll() && ticks - s.arrived() == 1) {
			rebuildWidgets();
		}
		onTick();
	}

	protected void onTick() {}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(left - 1, top - 1, left + W + 1, top + H + 1, FELT_BORDER);
		g.fill(left, top, left + W, top + H, FELT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		MutableComponent bar = Component.empty();
		List<Component> parts = topBar();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				bar.append(Texts.raw(" · ").withStyle(ChatFormatting.GRAY));
			}
			bar.append(parts.get(i));
		}
		g.text(font, bar, left + PAD, top + 6, TEXT, true);
		extractContent(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, a);
		if (showRules) {
			int x = left + 20;
			int y = top + 24;
			int w = W - 40;
			int lines = 0;
			for (String k : rulesKeys) {
				lines += font.split(Component.translatable(k), w - 12).size();
			}
			g.fill(x, y, x + w, y + lines * font.lineHeight + 12, 0xEE101010);
			int ty = y + 6;
			for (String k : rulesKeys) {
				for (FormattedCharSequence line : font.split(Component.translatable(k), w - 12)) {
					g.text(font, line, x + 6, ty, TEXT, true);
					ty += font.lineHeight;
				}
			}
		}
	}

	/** Name with the ALL-IN tag. */
	protected Component nameTag(int seat) {
		Component name = view.name(seat);
		if (seat >= 0 && seat < view.seats().size() && view.seats().get(seat).allIn()) {
			return Component.empty().append(name).append(" ")
				.append(Component.translatable("gui.burmaldaholic.pvp.all_in_tag").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		return seat == view.you() ? name.copy().withStyle(ChatFormatting.YELLOW) : name;
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
		return Component.translatable("gui.burmaldaholic.pvp.match.row", Texts.number(place), nameTag(seat), Texts.plural("unit.burmaldaholic.point", points));
	}
}
