package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Casino Menu tab "Bots" (order 45): the heat stage (the "Winnings from bots today" line is on the Wallet
 * tab, sent with the vip sync, BOTS.md §5.4), the player's Bot chatter mute (§7.4) and shortcuts to the table
 * the player sits at (Table settings…, Private table…, Table defaults…).
 */
final class BotsMenuPage implements CasinoMenu.Page {
	@Override
	public String id() {
		return "bots";
	}

	@Override
	public int order() {
		return 45;
	}

	@Override
	public Component label() {
		return Component.translatable("gui.burmaldaholic.bots.menu.tab");
	}

	@Override
	public boolean visible(ServerPlayer player) {
		return CasinoConfig.bots().enabled;
	}

	@Override
	public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
		MinecraftServer server = player.level().getServer();
		long threshold = BotLedger.threshold(server, player.getUUID());
		if (threshold > 0) {
			// the "Winnings from bots today" line itself is on the Wallet tab (vip, BOTS.md §5.4)
			HeatStage stage = TableSettings.heat(player);
			if (stage == HeatStage.SULKING) {
				long ticks = HeatStage.ticksToNextDay(server.overworld().getGameTime());
				out.line(Component.translatable("gui.burmaldaholic.bots.error.capped", untilTomorrow(ticks)), 0xFF5555);
			} else if (stage == HeatStage.WORD_GOT_AROUND) {
				out.line(Component.translatable("gui.burmaldaholic.bots.heat_hard_only"), 0xFFAA00);
			}
			out.blank();
		}
		boolean muted = BotsData.get(server).muted(player.getUUID());
		out.line(Component.translatable(muted ? "gui.burmaldaholic.bots.menu.chatter_off" : "gui.burmaldaholic.bots.menu.chatter_on"));
		out.line(Component.translatable("gui.burmaldaholic.bots.rules.fair"), 0xAAAAAA);
		out.line(Component.translatable("gui.burmaldaholic.bots.rules.money"), 0xAAAAAA);
		Optional<BotTables.Found> table = BotTables.seated(player);
		table.ifPresent(f -> {
			out.blank();
			out.line(Component.translatable("gui.burmaldaholic.bots.menu.table", f.name(), TableSettings.summary(f, TableSettings.currentOrPending(f.bots()))));
		});
		out.button("chatter", Component.translatable(muted ? "gui.burmaldaholic.bots.menu.unmute" : "gui.burmaldaholic.bots.menu.mute"));
		table.ifPresent(f -> {
			boolean manager = f.actor(player).manager() || player.getUUID().equals(f.host());
			out.button("settings", Component.translatable(manager ? "gui.burmaldaholic.bots.settings.open" : "gui.burmaldaholic.bots.settings.info"));
		});
	}

	/** "20 minutes" / "40 seconds" in the accusative (RU «через 20 минут»). */
	static Component untilTomorrow(long ticks) {
		long secs = (ticks + 19) / 20;
		return secs >= 60 ? Texts.plural("unit.burmaldaholic.minute_acc", (secs + 59) / 60) : Texts.plural("unit.burmaldaholic.second_acc", secs);
	}

	@Override
	public @Nullable Component action(ServerPlayer player, String action, long amount) {
		switch (action) {
			case "chatter" -> {
				BotsData data = BotsData.get(player.level().getServer());
				data.setMuted(player.getUUID(), !data.muted(player.getUUID()));
			}
			case "settings" -> {
				Optional<BotTables.Found> f = BotTables.seated(player);
				if (f.isEmpty()) {
					return Component.translatable("gui.burmaldaholic.bots.error.no_table");
				}
				TableSettings.send(player, f.get(), true, false, null);
			}
			default -> {
			}
		}
		return null;
	}
}
