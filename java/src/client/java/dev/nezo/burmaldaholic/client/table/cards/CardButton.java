package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Card-table console button (docs/design/visual/cards.md §8.1) on the shared kit {@link CasinoButton} (lane J-L2): the
 * themed secondary family ({@code button/table_<theme>[_highlighted|_disabled]}: leather + brass, blackstone + gold,
 * purpur + end stone), or the kit's primary / danger families (a gold button always means "the usual choice"); a
 * 12 × 12 icon ({@code cards/icon/<name>}) left of the label. Hover lift, press, the invalid shake and the disabled
 * look are the kit's.
 */
public class CardButton extends CasinoButton {
	public enum Family {
		TABLE,
		PRIMARY,
		DANGER
	}

	private Family cardFamily;
	private TableTheme theme;
	private @Nullable Component hint;

	public CardButton(int x, int y, int w, Component label, @Nullable String icon, Family family, TableTheme theme, Consumer<CardButton> onPress) {
		this(x, y, w, 20, label, icon, family, theme, onPress);
	}

	public CardButton(int x, int y, int w, int h, Component label, @Nullable String icon, Family family, TableTheme theme,
			Consumer<CardButton> onPress) {
		super(x, y, w, h, label, style(family), b -> onPress.accept((CardButton) b));
		this.cardFamily = family;
		this.theme = theme;
		if (icon != null) icon(Icon.sprite(FxSprites.sprite("cards/icon/" + icon), 12, 12));
		applyFamily();
	}

	private static Style style(Family f) {
		return switch (f) {
			case PRIMARY -> Style.PRIMARY;
			case DANGER -> Style.DANGER;
			case TABLE -> Style.SECONDARY;
		};
	}

	private void applyFamily() {
		style(style(cardFamily));
		if (cardFamily == Family.TABLE) {
			String base = "cards/button/table_" + theme.id;
			family(new UiSprites.Family(FxSprites.sprite(base), FxSprites.sprite(base + "_highlighted"), FxSprites.sprite(base + "_disabled")));
		} else {
			super.family((UiSprites.Family) null);
		}
	}

	/** Natural width for {@code label} with an icon (kit rule: text + icon + padding). */
	public static int naturalWidth(Font font, Component label, boolean icon) {
		return font.width(label) + (icon ? 15 : 0) + 16;
	}

	public CardButton family(Family f) {
		this.cardFamily = f;
		applyFamily();
		return this;
	}

	public CardButton theme(TableTheme t) {
		this.theme = t;
		applyFamily();
		return this;
	}

	/** Tooltip (drawn by the card-table frame in screen coordinates: the canvas may be scaled). */
	public CardButton hint(@Nullable Component h) {
		this.hint = h;
		return this;
	}

	public @Nullable Component hint() {
		return hint;
	}
}
