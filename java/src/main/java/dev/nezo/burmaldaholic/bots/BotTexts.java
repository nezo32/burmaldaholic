package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Bot presentation texts shared by server and client (BOTS.md §7.1, §8.1): the {@code [BOT]} display name,
 * the coloured level badge, personality lines and the table summary line. The bot glyph U+E190 is
 * prepended by code once the font sheet has it ({@link #GLYPH_IN_FONT}); it is never inside a string.
 */
public final class BotTexts {
	/** The copper automaton glyph (BOTS.md §7.1). Not in the Java font sheet yet (pvp-bots.md §8 open item). */
	public static final String GLYPH = "";
	public static final boolean GLYPH_IN_FONT = false;

	private BotTexts() {}

	/** "[BOT] Creeper42" (translated name). */
	public static MutableComponent display(String nameId) {
		MutableComponent name = Component.translatable(dev.nezo.burmaldaholic.core.bots.logic.BotRoster.nameKey(nameId));
		return withGlyph(Component.translatable("gui.burmaldaholic.bots.display", name));
	}

	public static MutableComponent display(BotProfile bot) {
		return display(bot.nameId());
	}

	/** "[BOT] Creeper42 · N" with the coloured level badge. */
	public static MutableComponent displayLevel(BotProfile bot) {
		MutableComponent name = Component.translatable(bot.nameKey());
		return withGlyph(Component.translatable("gui.burmaldaholic.bots.display_level", name, badge(bot.level())));
	}

	private static MutableComponent withGlyph(MutableComponent c) {
		if (!GLYPH_IN_FONT) {
			return c;
		}
		return Texts.raw(GLYPH + " ").append(c); // literal-ok: font glyph, language-neutral
	}

	/** Translated one-letter level badge on green / yellow / red (BOTS.md §8.1). */
	public static MutableComponent badge(BotDifficulty level) {
		ChatFormatting color = switch (level) {
			case EASY -> ChatFormatting.GREEN;
			case NORMAL -> ChatFormatting.YELLOW;
			case HARD -> ChatFormatting.RED;
			case MIXED -> ChatFormatting.GRAY;
		};
		return Component.translatable(level.shortKey()).withStyle(color, ChatFormatting.BOLD);
	}

	/** Level word, or the Style word where decisions can't change EV (chemin de fer). */
	public static MutableComponent level(BotDifficulty level, boolean style) {
		return Component.translatable(style ? level.styleKey() : level.translationKey());
	}

	/** "Rock — rarely bluffs". */
	public static MutableComponent personality(Personality p) {
		return Component.translatable("gui.burmaldaholic.bots.personality_line", Component.translatable(p.translationKey()),
			Component.translatable(p.translationKey() + ".desc"));
	}

	/**
	 * Table header line (BOTS.md §8.1): "Humans + 3 bots · Mixed · Private".
	 *
	 * @param botsSeated bots actually seated (MIXED shows the target count when none sit yet)
	 * @param hostName   BOTS_ONLY: the host's name
	 */
	public static MutableComponent summary(BotSettings s, boolean privateTable, int botsSeated, String hostName, boolean style) {
		Component access = Component.translatable(privateTable ? "gui.burmaldaholic.bots.summary.private" : "gui.burmaldaholic.bots.summary.open");
		return switch (s.policy()) {
			case HUMANS_ONLY -> Component.translatable("gui.burmaldaholic.bots.summary.humans_only", access);
			case MIXED -> Component.translatable("gui.burmaldaholic.bots.summary.mixed",
				Texts.plural("unit.burmaldaholic.bot", botsSeated > 0 ? botsSeated : s.count()), level(s.difficulty(), style), access);
			case BOTS_ONLY -> Component.translatable("gui.burmaldaholic.bots.summary.bots_only", Texts.raw(hostName),
				Texts.plural("unit.burmaldaholic.bot", s.count()), level(s.difficulty(), style));
		};
	}
}
