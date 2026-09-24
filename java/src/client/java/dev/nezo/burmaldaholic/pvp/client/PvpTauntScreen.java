package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.pvp.logic.Taunts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** The 8 fixed taunt lines (PVP.md §3.9) as buttons; picking one sends it and returns to the parent screen. */
final class PvpTauntScreen extends PvpScreen {
	private final @Nullable Screen parent;
	private final String matchId;

	PvpTauntScreen(@Nullable Screen parent, String matchId) {
		super(Component.translatable("gui.burmaldaholic.pvp.taunt.title"), new JsonObject());
		this.parent = parent;
		this.matchId = matchId;
	}

	@Override
	String matchId() {
		return matchId;
	}

	@Override
	protected void layout() {
		Flow flow = new Flow(contentTop() + 2);
		for (int i = 0; i < Taunts.IDS.size(); i++) {
			int line = i;
			flow.button(Component.translatable(Taunts.key(i)), 70, b -> {
				PvpScreens.action("taunt", matchId, "", line);
				onClose();
			});
		}
		flow.newRow();
		flow.button(Component.translatable("gui.burmaldaholic.common.back"), 50, b -> onClose());
		fitHeight(flow.bottom());
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}
}
