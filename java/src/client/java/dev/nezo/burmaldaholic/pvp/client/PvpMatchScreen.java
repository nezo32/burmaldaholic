package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Generic live-match screen, used when a mode registered no screen with {@code client.pvp.PvpScreens}: players,
 * stakes, ALL-IN tags, the reveal progress ("Step k", "Final results…") and [Go!] (the mode's advance button,
 * only speeds up the timeline) · [Taunt…] · [Close] (the HUD ticker keeps the match visible).
 */
final class PvpMatchScreen extends PvpScreen {
	private int rowsY;

	PvpMatchScreen(JsonObject state) {
		super(game(state), state);
	}

	@Override
	protected @Nullable Component titleRight() {
		return Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(num(state(), "pot", 0)));
	}

	private @Nullable JsonObject lastStep() {
		JsonArray steps = state().getAsJsonArray("steps");
		return steps == null || steps.isEmpty() ? null : steps.get(steps.size() - 1).getAsJsonObject();
	}

	private Component progress() {
		if (num(state(), "final", -1) >= 0) {
			return Component.translatable("gui.burmaldaholic.pvp.match.final");
		}
		JsonArray steps = state().getAsJsonArray("steps");
		int n = steps == null ? 0 : steps.size();
		return n == 0 ? Component.translatable("gui.burmaldaholic.common.waiting_players")
			: Component.translatable("gui.burmaldaholic.pvp.match.step", Texts.number(n));
	}

	private Component row(JsonObject p) {
		MutableComponent c = name(p).copy();
		if (bool(p, "allIn")) {
			c.append(Texts.raw(" ")).append(Component.translatable("gui.burmaldaholic.pvp.all_in_tag").withStyle(ChatFormatting.RED));
		}
		return c;
	}

	@Override
	protected void layout() {
		int w = panelWidth - 2 * PAD;
		int y = contentTop();
		if (bool(state(), "grudge")) {
			y += LINE;
		}
		y += wrappedHeight(progress(), w) + 4;
		rowsY = y;
		List<JsonObject> ps = participants(state());
		for (JsonObject p : ps) {
			y += wrappedHeight(row(p), w - 60);
		}
		Flow flow = new Flow(y + 6);
		JsonObject s = state();
		long you = num(s, "you", -1);
		JsonObject me = you < 0 ? null : participant(s, you);
		JsonObject step = lastStep();
		if (me != null) {
			boolean waiting = step != null && bool(step, "waitForAll") && num(s, "final", -1) < 0;
			flow.button(Component.translatable("gui.burmaldaholic.pvp.match.go").withStyle(ChatFormatting.GREEN), 50, b -> send("press", "", 0)).active =
				waiting && !bool(me, "pressed");
			flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), 50, b -> PvpScreens.openTaunts(this));
		}
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
		fitHeight(flow.bottom());
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int w = panelWidth - 2 * PAD;
		int x = left + PAD;
		int y = contentTop();
		if (bool(state(), "grudge")) {
			g.text(font, Component.translatable("gui.burmaldaholic.pvp.grudge.title").withStyle(ChatFormatting.BOLD), x, y, RED, true);
			y += LINE;
		}
		wrap(g, font, progress(), x, y, w, GOLD);
		int ry = rowsY;
		for (JsonObject p : participants(state())) {
			int end = wrap(g, font, row(p), x, ry, w - 60, bool(p, "you") ? GREEN : TEXT);
			Component stake = Texts.chips(num(p, "stake", 0));
			g.text(font, stake, x + w - font.width(stake), ry, MUTED, true);
			ry = end;
		}
	}
}
