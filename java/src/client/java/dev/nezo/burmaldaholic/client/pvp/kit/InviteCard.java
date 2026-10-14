package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.v2.TimerBar;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The v2 invite card (REDESIGN.md §7.8): a centred 232 × 96 card on a scrim — the challenger's head 32², "Alex
 * challenges you", the game and the stake 2× gold, the TimerBar, <b>Accept</b> (primary, {@code Y}) and
 * <b>Decline</b> ({@code N}). Pure layout by {@link Box} (hit ids {@code accept}, {@code decline}); the screen owns the
 * input and what the buttons send.
 */
public final class InviteCard {
	public static final String ACCEPT = "accept", DECLINE = "decline";
	public static final int W = 232, H = 96;

	private final TimerBar timer = new TimerBar();
	private Box card = Box.EMPTY;
	private Box accept = Box.EMPTY;
	private Box decline = Box.EMPTY;

	/** Lays the card out centred in a {@code w × h} GUI (scaled ×2 on large GUIs). Returns the hit map. */
	public HitMap layout(int w, int h, HitMap hits) {
		card = Box.centredAt(w / 2, h / 2, Math.min(W, w - 8), H);
		int bw = (card.w() - 3 * 8) / 2;
		accept = new Box(card.x() + 8, card.bottom() - 30, bw, 22);
		decline = new Box(accept.right() + 8, accept.y(), bw, 22);
		hits.clear();
		hits.add(ACCEPT, accept, true);
		hits.add(DECLINE, decline, true);
		return hits;
	}

	public Box card() {
		return card;
	}

	/**
	 * Draws the scrim and the card.
	 *
	 * @param head      draws the challenger's face at (x, y, 32)
	 * @param from      "Alex challenges you"
	 * @param stake     the game and stake, 2× gold
	 * @param secondsLeft seconds until it expires, {@code total} the full time
	 */
	public void draw(GuiGraphicsExtractor g, Font font, int screenW, int screenH, Face head, Component from, Component stake, int secondsLeft, int total,
			int mx, int my) {
		g.fill(0, 0, screenW, screenH, 0xA0100818);
		V2Draw.pill(g, card, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD);
		g.fill(card.x() + 1, card.y() + 1, card.right() - 1, card.y() + 2, 0x40FFFFFF);
		head.draw(g, card.x() + 8, card.y() + 8, 32);
		V2Draw.outline(g, new Box(card.x() + 7, card.y() + 7, 34, 34), CasinoPalette.V2_LOSE);
		int tx = card.x() + 48;
		int tw = card.right() - 8 - tx;
		V2Draw.left(g, font, from, new Box(tx, card.y() + 8, tw, 10), 0, 1, CasinoPalette.V2_TEXT, true);
		int scale = font.width(stake) * 2 <= tw ? 2 : 1;
		V2Draw.left(g, font, stake, new Box(tx, card.y() + 20, tw, scale == 2 ? 18 : 10), 0, scale, CasinoPalette.V2_GOLD, true);
		timer.set(Math.max(0, secondsLeft), Math.max(1, total));
		timer.draw(g, new Box(card.x() + 8, card.y() + 50, card.w() - 16, 3));
		V2Button.draw(g, font, accept, Component.translatable("gui.burmaldaholic.pvp.v2.invite.accept"), V2Button.keyName(KeyMap.Y),
			V2Sprites.ButtonStyle.PRIMARY, true, accept.contains(mx, my), false, false);
		V2Button.draw(g, font, decline, Component.translatable("gui.burmaldaholic.pvp.v2.invite.decline"), V2Button.keyName(KeyMap.N),
			V2Sprites.ButtonStyle.NORMAL, true, decline.contains(mx, my), false, false);
	}

	/** A face drawer (player skin, bot, creeper). */
	@FunctionalInterface
	public interface Face {
		void draw(GuiGraphicsExtractor g, int x, int y, int size);
	}
}
