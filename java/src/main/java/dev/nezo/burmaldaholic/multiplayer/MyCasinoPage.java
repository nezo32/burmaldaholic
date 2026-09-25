package dev.nezo.burmaldaholic.multiplayer;

import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.Casino;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoStats;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Casino Menu "My Casino" tab (UI.md §2): only for charter owners — status, bankroll, profit, shortcut to the Charter screen. */
final class MyCasinoPage implements CasinoMenu.Page {
	@Override
	public String id() {
		return "my_casino";
	}

	@Override
	public int order() {
		return 60;
	}

	@Override
	public Component label() {
		return Component.translatable("gui.burmaldaholic.menu.my_casino");
	}

	@Override
	public boolean visible(ServerPlayer player) {
		return Ownership.enabled(player.level().getServer()) && !Ownership.ownedBy(player.level().getServer(), player.getUUID()).isEmpty();
	}

	@Override
	public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
		MinecraftServer server = player.level().getServer();
		List<Casino> mine = Ownership.ownedBy(server, player.getUUID());
		for (int i = 0; i < mine.size(); i++) {
			Casino c = mine.get(i);
			Economy.BankrollInfo b = Ownership.bankroll(server, c);
			c.stats.roll(Ownership.day(server));
			CasinoStats.Tally today = c.stats.today();
			CasinoStats.Tally total = c.stats.total();
			out.line(Component.translatable("gui.burmaldaholic.charter.title").append(" ").append(Texts.raw(c.x + ", " + c.y + ", " + c.z)), 0xFFD700); // literal-ok: coordinates
			out.line(Component.translatable(c.broke ? "gui.burmaldaholic.charter.status_broke" : "gui.burmaldaholic.charter.status_open"),
				c.broke ? 0xFF5555 : 0x55FF55);
			out.line(Component.translatable("gui.burmaldaholic.charter.bankroll_value", Texts.chips(b.balance())));
			out.line(Component.translatable("gui.burmaldaholic.charter.today", Texts.chips(today.handle), Texts.chips(today.paid), Texts.number(today.profit())), 0xAAAAAA);
			out.line(Component.translatable("gui.burmaldaholic.charter.total", Texts.chips(total.handle), Texts.chips(total.paid), Texts.number(total.profit())), 0xAAAAAA);
			out.button("open:" + i, Component.translatable("gui.burmaldaholic.charter.title"));
			out.blank();
		}
	}

	@Override
	public @Nullable Component action(ServerPlayer player, String action, long amount) {
		if (action.startsWith("open:")) {
			List<Casino> mine = Ownership.ownedBy(player.level().getServer(), player.getUUID());
			try {
				int i = Integer.parseInt(action.substring(5));
				if (i >= 0 && i < mine.size()) {
					Ownership.sendState(player, mine.get(i), true, null, false);
				}
			} catch (NumberFormatException ignored) {
				// stale client
			}
		}
		return null;
	}
}
