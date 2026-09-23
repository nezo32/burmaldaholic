package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * PvP Dice Duel invitation (UI.md §9: "Alex challenges you to a Dice Duel for 200 chips" [Accept] [Decline]).
 * Only shown when the target has no other screen open; otherwise the chat line and the dice screen's
 * "Pending challenges" list serve. Closes itself when the challenge times out.
 */
final class DuelInviteScreen extends ExtrasScreen {
	private int textBottom;

	DuelInviteScreen(CompoundTag state) {
		super("dice", Component.translatable("gui.burmaldaholic.extras.dice.title"), state, 220, 90);
	}

	private Component text() {
		CompoundTag s = state();
		return Component.translatable("gui.burmaldaholic.extras.dice.invite", Texts.raw(s.getStringOr("name", "")), Texts.chips(s.getLongOr("stake", 0)));
	}

	@Override
	protected void layout() {
		int w = panelWidth - 2 * PAD;
		textBottom = top + 22 + wrappedHeight(text(), w) + 12;
		Flow flow = new Flow(font, this::addRenderableWidget, left + PAD, textBottom + 4, w);
		int id = state().getIntOr("id", -1);
		flow.button(Component.translatable("gui.burmaldaholic.extras.dice.accept").withStyle(ChatFormatting.GREEN), 60, b -> answer("accept", id));
		flow.button(Component.translatable("gui.burmaldaholic.extras.dice.decline"), 60, b -> answer("decline", id));
		fitHeight(flow.bottom());
	}

	private void answer(String action, int id) {
		CompoundTag args = new CompoundTag();
		args.putInt("id", id);
		send(action, args);
		onClose();
	}

	@Override
	public void tick() {
		super.tick();
		if (ticks >= state().getLongOr("ticks", 600)) {
			onClose();
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int w = panelWidth - 2 * PAD;
		int y = wrapped(g, text(), left + PAD, top + 22, w, TEXT);
		long secs = Math.max(0, (state().getLongOr("ticks", 600) - ticks + 19) / 20);
		g.text(font, Component.translatable("gui.burmaldaholic.common.timer", Texts.plural("unit.burmaldaholic.second", secs)), left + PAD, y + 2,
			secs <= 5 ? ERROR : MUTED, true);
	}
}
