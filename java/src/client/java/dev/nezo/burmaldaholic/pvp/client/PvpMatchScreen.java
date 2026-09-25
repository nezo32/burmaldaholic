package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.PlateRow;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Generic live-match window (PVP.md §3.11.1) for a mode without its own screen: the seat plates (heads, badges,
 * stakes, ready ticks, taunt bubbles), the pot and the step counter on the arena scene, and [Go!] [Taunt…] [Close].
 * The grudge clash, the countdown and the Final Reveal are the shared overlay of the pvp client module.
 */
final class PvpMatchScreen extends PvpScreen {
	PvpMatchScreen(JsonObject state) {
		super(game(state), state);
	}

	private @Nullable JsonObject lastStep() {
		JsonArray steps = state().getAsJsonArray("steps");
		return steps == null || steps.isEmpty() ? null : steps.get(steps.size() - 1).getAsJsonObject();
	}

	@Override
	protected @Nullable Component titleRight() {
		JsonArray steps = state().getAsJsonArray("steps");
		int n = steps == null ? 0 : steps.size();
		return n == 0 ? null : Component.translatable("gui.burmaldaholic.pvp.match.step", Texts.number(n));
	}

	@Override
	protected void layout() {
		JsonObject s = state();
		long you = num(s, "you", -1);
		JsonObject me = you < 0 ? null : participant(s, you);
		JsonObject step = lastStep();
		int x = Scene.W / 2 - 110;
		if (me != null) {
			boolean waiting = step != null && bool(step, "waitForAll") && num(s, "final", -1) < 0;
			Component go = Component.translatable("gui.burmaldaholic.pvp.match.go");
			button(x, 207, 90, go, KitButton.Style.PRIMARY, b -> send("press", "", 0)).active(waiting && !bool(me, "pressed")).breathe(waiting);
			Component taunt = Component.translatable("gui.burmaldaholic.pvp.taunt.button");
			button(x + 94, 207, 60, taunt, KitButton.Style.SECONDARY, b -> PvpScreens.openTaunts(this));
		}
		button(x + 158, 207, 60, Component.translatable("gui.burmaldaholic.common.close"), KitButton.Style.SECONDARY, b -> onClose());
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		JsonObject s = state();
		List<PvpSeat> seats = PvpSeat.all(s);
		long entry = num(s, "entry", 1);
		PvpDraw.pot(g, font, num(s, "pot", 0), entry, left + Scene.W / 2, top + 34);
		PlateRow.draw(g, font, s, seats, left, top + 88, seat -> Texts.number(seat.stake()));
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		PlateRow.bubbles(g, font, state(), PvpSeat.all(state()), left, top + 88);
	}
}
