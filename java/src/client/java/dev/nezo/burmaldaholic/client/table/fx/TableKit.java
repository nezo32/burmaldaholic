package dev.nezo.burmaldaholic.client.table.fx;

import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Glue between the table screens and the shared J-L2 UI kit ({@code client.ui}): text buttons are kit
 * {@link CasinoButton}s (secondary; Accept is primary), and the location theme comes from the kit's
 * {@link CasinoTheme} (the {@code theme} string the table / duel state sends). The table-specific art (felt, rail,
 * chips, the 48² Spin / Roll chips and the 20² icon buttons of visual/tables.md §2.2) stays in this package.
 */
public final class TableKit {
	private TableKit() {}

	/** A kit text button as wide as its label needs (min {@code minW}), 20 px high. */
	public static CasinoButton button(int x, int y, int minW, Component label, TableTheme theme, Consumer<CasinoButton> onPress) {
		int w = CasinoButton.width(Minecraft.getInstance().font, label, minW, false);
		return new CasinoButton(x, y, w, 20, label, CasinoButton.Style.SECONDARY, onPress);
	}

	/** The table theme of a kit location theme. */
	public static TableTheme theme(CasinoTheme t) {
		return switch (t) {
			case BASTION -> TableTheme.BASTION;
			case END -> TableTheme.END;
			default -> TableTheme.VILLAGE;
		};
	}
}
