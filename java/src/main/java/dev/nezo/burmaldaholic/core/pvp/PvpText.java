package dev.nezo.burmaldaholic.core.pvp;

import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Translated names and result lines of the PvP engine (PVP.md §15, BOTS.md §11). */
public final class PvpText {
	private PvpText() {}

	/** {@code gui.burmaldaholic.pvp.game.<mode>}. */
	public static MutableComponent modeName(String mode) {
		return Component.translatable("gui.burmaldaholic.pvp.game." + mode);
	}

	/**
	 * A participant's display name: the player name, or {@code [BOT] Name} — with the level / Style tag in
	 * modes where bots make decisions (BOTS.md §4.3: "Creeper42 [Hard]"; luck-only modes hide difficulty).
	 */
	public static MutableComponent name(SeatOccupant occ, boolean withLevel) {
		if (occ instanceof SeatOccupant.Bot b) {
			MutableComponent display = Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(b.name()));
			if (withLevel) {
				return Component.translatable("gui.burmaldaholic.pvp.bots.tagged", display,
					Component.translatable(b.profile().level().styleKey()));
			}
			return display;
		}
		return Texts.raw(occ.name());
	}

	public static MutableComponent name(Participant p, PvpMode<?, ?> mode) {
		return name(p.occupant, mode != null && mode.hasDecisions());
	}

	/** Personal result line: win (+net), split (+payout) or loss (−stake). */
	public static MutableComponent resultLine(long stake, long payout, boolean split) {
		long net = payout - stake;
		if (split && payout > 0) {
			return Component.translatable("gui.burmaldaholic.pvp.result.split", Texts.chips(payout));
		}
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.pvp.result.you_win", Texts.chips(net));
		}
		return Component.translatable("gui.burmaldaholic.pvp.result.you_lose", Texts.chips(Math.max(0, -net)));
	}

	/** Names joined with a comma (the separator is a symbol, not a word). */
	public static MutableComponent list(List<? extends Component> names) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				out.append(Texts.raw(", "));
			}
			out.append(names.get(i));
		}
		return out;
	}
}
