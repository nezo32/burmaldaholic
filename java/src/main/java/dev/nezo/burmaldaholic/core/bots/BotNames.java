package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * How a bot is written everywhere (BOTS.md §7.1): the bot glyph U+E190 (prepended by code, never inside
 * a string) + {@code gui.burmaldaholic.bots.display} ("[BOT] %1$s") around the translated name; with a
 * level: {@code …bots.display_level}. Server-safe (translatable components).
 */
public final class BotNames {
	/** Bot glyph (font sheet {@code glyph_E1.png} row 9, cell 0; same code point in both editions). */
	public static final String GLYPH = "";

	/** How the level control is labelled at a game (§4.2). */
	public enum LevelLabel {
		/** Easy / Normal / Hard (poker, blackjack, UTH). */
		LEVEL,
		/** Wild / Steady / Cool-headed (chemin de fer, Coin Flip Duel, Wheel Party). */
		STYLE,
		/** No level shown (bots have no decisions: "luck only"). */
		HIDDEN
	}

	private BotNames() {}

	/** The translated name alone ({@code gui.burmaldaholic.bots.name.<id>}). */
	public static MutableComponent name(BotProfile bot) {
		return Component.translatable(bot.nameKey());
	}

	/** " [BOT] Lucky Steve". */
	public static MutableComponent display(BotProfile bot) {
		return glyph().append(Component.translatable("gui.burmaldaholic.bots.display", name(bot)));
	}

	/** " [BOT] Lucky Steve · Hard" (or the Style name; plain {@link #display} when HIDDEN). */
	public static MutableComponent display(BotProfile bot, LevelLabel label) {
		if (label == LevelLabel.HIDDEN) {
			return display(bot);
		}
		return glyph().append(Component.translatable("gui.burmaldaholic.bots.display_level", name(bot), level(bot.level(), label)));
	}

	/** The level / style word of a level ({@code …bots.level.<id>} / {@code …bots.style.<id>}). */
	public static MutableComponent level(BotDifficulty level, LevelLabel label) {
		return Component.translatable(label == LevelLabel.STYLE ? level.styleKey() : level.translationKey());
	}

	/** A seat occupant: player name as is, bot as {@link #display}. */
	public static MutableComponent of(SeatOccupant o) {
		return o instanceof SeatOccupant.Bot b ? display(b.profile()) : Texts.raw(o.name());
	}

	/** "A, B, C" of bot display names ({@code …bots.joined_many}). */
	public static MutableComponent list(Collection<BotProfile> bots) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (BotProfile b : bots) {
			if (!first) {
				out.append(Texts.raw(", "));
			}
			out.append(display(b));
			first = false;
		}
		return out;
	}

	private static MutableComponent glyph() {
		return Texts.raw(GLYPH + " ");
	}
}
