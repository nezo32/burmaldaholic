package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Casino location themes of the card tables (docs/design/visual/cards.md §7): the room, felt, rail, props, buttons,
 * console, card backs and celebration accent follow the table's dimension (overworld → village parlour, the Nether →
 * Piglin bastion parlour, the End → High Roller lounge). {@link #force} overrides it (tests; a future
 * {@code cards.theme} config).
 */
public enum TableTheme {
	VILLAGE("village", 0xFFE8C860, 0xFFFFD640, "villager", "witch"),
	BASTION("bastion", 0xFFFFC850, 0xFFFF6020, "piglin", "brute"),
	END("end", 0xFFE8E4A8, 0xFFD696FF, "enderman", "shulker");

	/** Card table shapes (§2.1). */
	public enum Shape {
		CRESCENT("crescent"),
		OVAL("oval");

		final String id;

		Shape(String id) {
			this.id = id;
		}
	}

	public final String id;
	/** Felt print colour (drawn at 55 % alpha). */
	public final int print;
	/** Celebration accent (gold rays / ember sparks / lilac sparkle). */
	public final int accent;
	private final String botA;
	private final String botB;

	private static @Nullable TableTheme forced;

	TableTheme(String id, int print, int accent, String botA, String botB) {
		this.id = id;
		this.print = print;
		this.accent = accent;
		this.botA = botA;
		this.botB = botB;
	}

	/** Forces a theme for every card table (null = by location). */
	public static void force(@Nullable TableTheme theme) {
		forced = theme;
	}

	/** The theme of the current level (or the forced one). */
	public static TableTheme current() {
		if (forced != null) return forced;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) return VILLAGE;
		var dim = mc.level.dimension();
		if (dim == Level.NETHER) return BASTION;
		if (dim == Level.END) return END;
		return VILLAGE;
	}

	/** Print colour at the felt's 55 % alpha. */
	public int printArgb(double alpha) {
		return CardGfx.alpha(print, alpha);
	}

	public Identifier backdrop() {
		return Burmaldaholic.id("textures/gui/core/cards/backdrop_" + id + ".png");
	}

	public Identifier table(Shape shape, boolean compact) {
		return Burmaldaholic.id("textures/gui/core/cards/table_" + shape.id + "_" + id + (compact ? "_compact" : "") + ".png");
	}

	/** A themed sprite: {@code cards/<group>/<name>_<theme>}. */
	public Identifier themed(String groupAndName) {
		return FxSprites.sprite("cards/" + groupAndName + "_" + id);
	}

	/** Card back row (§3.7): the game's own back in the village, the location's back elsewhere. */
	public int back(int villageBack) {
		return switch (this) {
			case VILLAGE -> villageBack;
			case BASTION -> CardSprites.BACK_BASTION;
			case END -> CardSprites.BACK_END;
		};
	}

	/** A bot avatar of this theme's pair, picked by a hash of the bot's name (§8.3). */
	public String botAvatar(String name) {
		return (name.hashCode() & 1) == 0 ? botA : botB;
	}
}
