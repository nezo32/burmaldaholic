package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Seat plates (docs/design/visual/cards.md §8.2–§8.3): {@code seat/plate_<state>} (nine-slice 40 × 22, border 6), width =
 * content + 30 px, the avatar frame (gold for the viewer / the winner) with the player's skin face or a mob-face bot
 * avatar ({@code bot/<name>}, 16 × 16), the bot level badge (shape + pips, 11 × 11) before the name, and the sub-line
 * (stack, bet or state word; a bot deciding shows the animated {@code bot/thinking} dots). Bots look exactly like humans
 * otherwise (faithfulness §0.7.9).
 */
public final class SeatPlate {
	public enum State {
		NORMAL("normal"),
		ACTIVE("active"),
		ME("me"),
		FOLDED("folded"),
		WINNER("winner");

		final String id;

		State(String id) {
			this.id = id;
		}
	}

	/** What a plate shows. {@code bot} = avatar name (villager, witch, piglin, brute, enderman, shulker) or null. */
	public record Info(Component name, @Nullable String playerName, @Nullable String bot, int level, Component sub, int subColor, State state,
			boolean thinking) {}

	private SeatPlate() {}

	public static int width(Font font, Info info) {
		return Math.max(font.width(info.name()) + (info.level() > 0 ? 13 : 0), Math.max(font.width(info.sub()), 14)) + 30;
	}

	/** Draws the plate at (x, y); returns its width. */
	public static int draw(GuiGraphicsExtractor g, Font font, Info info, int x, int y, double alpha, int dy) {
		int w = width(font, info);
		int a = CardGfx.white(alpha);
		y += dy;
		CardGfx.sprite(g, FxSprites.sprite("cards/seat/plate_" + info.state().id), x, y, w, 22, a, 0xFF26103C);
		boolean gold = info.state() == State.ME || info.state() == State.WINNER;
		CardGfx.sprite(g, FxSprites.sprite(gold ? "cards/seat/avatar_frame_gold" : "cards/seat/avatar_frame"), x + 2, y + 1, 20, 20, a);
		boolean dim = info.state() == State.FOLDED;
		int faceTint = dim ? CardGfx.alpha(0xFF8A8A8A, alpha) : a;
		if (info.bot() != null) {
			CardGfx.sprite(g, FxSprites.sprite("cards/bot/" + info.bot()), x + 4, y + 3, 16, 16, faceTint, 0xFF6A5A4A);
		} else {
			face(g, info.playerName(), x + 4, y + 3, alpha, dim);
		}
		int tx = x + 24;
		if (info.level() > 0) {
			String lvl = info.level() == 1 ? "easy" : info.level() == 2 ? "normal" : "hard";
			CardGfx.sprite(g, FxSprites.sprite("cards/bot/badge_" + lvl), tx, y + 3, 11, 11, a);
			tx += 13;
		}
		int nameColor = dim ? 0xFF8A7A9A : CasinoPalette.BONE;
		CardGfx.text(g, font, info.name(), tx, y + 4, CardGfx.alpha(nameColor, alpha), true);
		if (info.thinking()) {
			CardGfx.sprite(g, FxSprites.sprite("cards/bot/thinking"), x + 24, y + 14, 13, 5, a); // animated strip (.mcmeta)
		} else {
			CardGfx.text(g, font, info.sub(), x + 24, y + 13, CardGfx.alpha(dim ? 0xFF8A7A9A : info.subColor(), alpha), true);
		}
		return w;
	}

	/** The player's skin face (8 × 8 at 2×) or a plain head when the player is unknown to this client. */
	private static void face(GuiGraphicsExtractor g, @Nullable String playerName, int x, int y, double alpha, boolean dim) {
		Minecraft mc = Minecraft.getInstance();
		PlayerInfo info = playerName == null || playerName.isEmpty() || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(playerName);
		if (info != null && alpha > 0.5) {
			PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, 16);
			if (dim) g.fill(x, y, x + 16, y + 16, 0x60180A28);
			return;
		}
		g.fill(x, y, x + 16, y + 16, CardGfx.alpha(0xFFC8906A, alpha));
		g.fill(x, y, x + 16, y + 5, CardGfx.alpha(0xFF4A2A1A, alpha));
		g.fill(x + 3, y + 8, x + 6, y + 10, CardGfx.alpha(0xFF3A5AA8, alpha));
		g.fill(x + 10, y + 8, x + 13, y + 10, CardGfx.alpha(0xFF3A5AA8, alpha));
	}

	/** Emote above a plate after a public result (bot happy / grumpy), popping in 0 → 1 (outBack 200 ms), hold 1 s, fade. */
	public static void emote(GuiGraphicsExtractor g, boolean happy, int cx, int y, long ageMs) {
		if (ageMs < 0 || ageMs > 1500) return;
		double s = ageMs < 200 ? dev.nezo.burmaldaholic.core.anim.Ease.OUT_BACK.apply(ageMs / 200.0) : 1;
		double a = ageMs < 1200 ? 1 : 1 - (ageMs - 1200) / 300.0;
		CardGfx.pushBox(g, cx - 5.5, y - 11, 11, 11, 0, s, s);
		CardGfx.sprite(g, FxSprites.sprite(happy ? "cards/bot/emote_happy" : "cards/bot/emote_grumpy"), 0, 0, 11, 11, CardGfx.white(a));
		CardGfx.pop(g);
	}
}
