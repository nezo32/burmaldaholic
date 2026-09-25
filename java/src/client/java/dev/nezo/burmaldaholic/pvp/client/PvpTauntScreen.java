package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.pvp.logic.Taunts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The 8-line taunt picker (PVP.md §3.9) on the arena scene: one casino button per line with its pictogram
 * ({@code taunt_icons}: gg, luck, wow, rigged, again, steel, bye, respect), two columns; returns to its parent.
 */
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
	protected @Nullable Component bannerTitle() {
		return getTitle();
	}

	@Override
	protected void layout() {
		for (int i = 0; i < Taunts.IDS.size(); i++) {
			int line = i;
			int x = 40 + (i % 2) * 164;
			int y = 44 + (i / 2) * 36;
			button(x, y, 156, Component.translatable(Taunts.key(i)), KitButton.Style.SECONDARY, b -> {
				PvpScreens.action("taunt", matchId, "", line);
				onClose();
			}).icon(new KitButton.Icon(PvpDraw.TAUNT_ICONS, 128, 16, i * 16, 0, 16, 16));
		}
		button(170, 200, 60, Component.translatable("gui.burmaldaholic.common.back"), KitButton.Style.SECONDARY, b -> onClose());
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
