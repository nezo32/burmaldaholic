package dev.nezo.burmaldaholic.pvp;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.PvpText;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Casino Menu → PvP matches (operators; PVP.md §3.13 "Admin → PvP matches"): every match with its state and pot,
 * [Cancel / settle now] per match (LOBBY → refund, DRAWN → settle now), and the rake warning of §3.12.
 */
public final class PvpAdminPage implements CasinoMenu.Page {
	/** Cheapest house game edge in basis points (coin flip, 2 %): below it VIP wagered can be farmed cheaply. */
	public static final int CHEAPEST_HOUSE_EDGE_BP = 200;

	@Override
	public String id() {
		return "pvp_admin";
	}

	@Override
	public int order() {
		return 95;
	}

	@Override
	public Component label() {
		return Component.translatable("gui.burmaldaholic.pvp.admin.matches");
	}

	@Override
	public boolean visible(ServerPlayer player) {
		return isOperator(player);
	}

	static boolean isOperator(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	/** The §3.12 validation warning, or null. */
	public static @Nullable Component rakeWarning() {
		if (CasinoConfig.pvp().countsTowardVip && CasinoConfig.pvp().rakeBasisPoints < CHEAPEST_HOUSE_EDGE_BP) {
			return Component.translatable("gui.burmaldaholic.pvp.admin.rake_warning", percent());
		}
		return null;
	}

	static Component percent() {
		return Component.translatable("gui.burmaldaholic.pvp.percent", Texts.decimal(PvpText.percent(CasinoConfig.pvp().rakeBasisPoints)));
	}

	/** One admin row: id · game · state · pot. */
	public static Component row(PvpMatch m) {
		return Component.translatable("gui.burmaldaholic.pvp.admin.match_row", Texts.raw(m.id), PvpHubPage.game(m.mode), stateName(m.state()),
			Texts.chips(m.pot()));
	}

	static Component stateName(MatchState s) {
		String id = switch (s) {
			case INVITED -> "invited";
			case LOBBY, STARTING -> "lobby";
			case DRAWN -> "drawn";
			default -> "settled";
		};
		return Component.translatable("gui.burmaldaholic.pvp.admin.state." + id);
	}

	@Override
	public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
		out.line(Component.translatable("gui.burmaldaholic.pvp.admin.matches"), 0xFFD700);
		Component warning = rakeWarning();
		if (warning != null) {
			out.line(warning, 0xFF5555);
		}
		List<PvpMatch> all = Pvp.service().all();
		if (all.isEmpty()) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.admin.none"), 0xAAAAAA);
		}
		for (PvpMatch m : all) {
			out.line(row(m));
			boolean cancellable = m.state() == MatchState.LOBBY || m.state() == MatchState.DRAWN || m.state() == MatchState.INVITED;
			out.button("cancel:" + m.id, Component.translatable("gui.burmaldaholic.pvp.admin.cancel"), cancellable);
		}
	}

	@Override
	public @Nullable Component action(ServerPlayer player, String action, long amount) {
		if (!isOperator(player) || !action.startsWith("cancel:")) {
			return null;
		}
		String id = action.substring("cancel:".length());
		if (Pvp.service().get(id).isEmpty()) {
			return Component.translatable("msg.burmaldaholic.pvp.command.unknown_match", Texts.raw(id));
		}
		Pvp.service().cancel(id);
		return null;
	}
}
