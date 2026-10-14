package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.pvp.kit.Faces;
import dev.nezo.burmaldaholic.client.pvp.kit.InviteCard;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * PvP Dice Duel invitation, table UI v2 (REDESIGN.md §7.8 "Invite: a centred 232 × 96 card on a scrim"; lane E): the
 * {@link InviteCard} with the challenger's head, "Alex challenges you", "Dice Duel · 200 chips" 2× gold, the TimerBar, <b>Accept</b> {@code Y} and
 * <b>Decline</b> {@code N} (Esc declines too). Only shown when the target has no other screen open; closes itself when it times out (the
 * dice screen still lists the challenge until then).
 *
 * <p>Hit ids {@code accept}, {@code decline} ({@link #hits()} for client GameTests).
 */
final class DuelInviteScreen extends CasinoScreen {
	private final CompoundTag state;
	private final InviteCard card = new InviteCard();
	private final HitMap hits = new HitMap();
	private int ticks;

	DuelInviteScreen(CompoundTag state) {
		super(Component.translatable("gui.burmaldaholic.extras.dice.title"));
		this.state = state;
	}

	HitMap hits() {
		return hits;
	}

	@Override
	protected void init() {
		super.init();
		card.layout(width, height, hits);
	}

	private void answer(String action) {
		CompoundTag args = new CompoundTag();
		args.putInt("id", state.getIntOr("id", -1));
		ClientPlayNetworking.send(new ExtrasActionPayload("dice", action, args));
		onClose();
	}

	@Override
	public void tick() {
		super.tick();
		if (++ticks >= state.getLongOr("ticks", 600)) onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		var t = hits.at(event.x(), event.y());
		if (t.isPresent() && Inputs.isLeft(event.button())) {
			answer(t.get().id().equals(InviteCard.ACCEPT) ? "accept" : "decline");
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == Inputs.KEY_Y) {
			answer("accept");
			return true;
		}
		if (event.key() == Inputs.KEY_N || event.isEscape()) {
			// Esc answers too: a dismissed card declines, so the challenger is not left waiting out the timer (R-CDE m5)
			answer("decline");
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected boolean showBanner() {
		return false;
	}

	@Override
	protected boolean showBalance() {
		return false;
	}

	@Override
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// the world under a scrim (drawn by the card)
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long total = Math.max(1, state.getLongOr("ticks", 600));
		int left = (int) Math.ceil(Math.max(0, total - ticks - a) / 20.0);
		Component from = Component.translatable("gui.burmaldaholic.pvp.v2.invite.from", Texts.raw(state.getStringOr("name", "")));
		Component stake = Component.translatable("gui.burmaldaholic.pvp.v2.invite.stake", Component.translatable("gui.burmaldaholic.extras.dice.title"),
			Texts.chips(state.getLongOr("stake", 0)));
		String uuid = state.getStringOr("uuid", "");
		card.draw(g, font, width, height, (gg, x, y, size) -> Faces.draw(gg, uuid, false, "", x, y, size), from, stake, left, (int) (total / 20), mouseX,
			mouseY);
	}
}
