package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** "Say something": the 8 fixed taunt lines of PVP.md §3.9 (index 0–7 = the §15.4 order); returns to the parent. */
public final class TauntScreen extends PvpPanel {
	public static final List<String> LINES = List.of("gg", "luck", "wow", "rigged", "again", "steel", "bye", "respect");

	private final @Nullable Screen parent;
	private final IntConsumer send;

	public TauntScreen(@Nullable Screen parent, IntConsumer send) {
		super(Component.translatable("gui.burmaldaholic.pvp.taunt.title"), 220, 120);
		this.parent = parent;
		this.send = send;
	}

	@Override
	protected int layout() {
		Flow flow = new Flow(top + 22);
		for (int i = 0; i < LINES.size(); i++) {
			int line = i;
			flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt." + LINES.get(i)), 60, b -> {
				send.accept(line);
				onClose();
			});
		}
		flow.newRow();
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 60, b -> onClose());
		return flow.bottom();
	}

	@Override
	protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}
}
