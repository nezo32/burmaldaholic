package dev.nezo.burmaldaholic.lastchance;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Mode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Casino Menu "Rules" tab (UI.md §2): difficulty, Last Chance status and cooldown, Hardcore mode, chaos
 * on/off and the disclaimer. Last Chance owns it because most of the page is its status.
 */
final class RulesPage implements CasinoMenu.Page {
	@Override
	public String id() {
		return "rules";
	}

	@Override
	public int order() {
		return 70;
	}

	@Override
	public Component label() {
		return Component.translatable("gui.burmaldaholic.menu.rules");
	}

	@Override
	public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
		MinecraftServer server = player.level().getServer();
		String diff = LastChance.hardcore(server) ? "hardcore" : player.level().getDifficulty().getSerializedName();
		out.line(Component.translatable("gui.burmaldaholic.menu.rules.difficulty", Component.translatable("gui.burmaldaholic.common.difficulty." + diff)));
		out.blank();
		LastChance.Status st = LastChance.status(player);
		if (st.mode() == null) {
			out.line(Component.translatable("gui.burmaldaholic.menu.rules.last_chance_off"), 0xAAAAAA);
		} else {
			Component pct = Texts.raw(Math.round(st.chance() * 100) + "%"); // literal-ok: percentage
			out.line(Component.translatable("gui.burmaldaholic.menu.rules.last_chance", pct, LastChance.duration(st.cooldownTicks())));
			out.line(st.remainingTicks() > 0
				? Component.translatable("gui.burmaldaholic.menu.rules.last_chance_cooldown", LastChance.duration(st.remainingTicks()))
				: Component.translatable("gui.burmaldaholic.menu.rules.last_chance_ready"), st.remainingTicks() > 0 ? 0xFFAA00 : 0x55FF55);
			if (st.mode() == Mode.HIGH_STAKES) {
				out.line(Component.translatable("gui.burmaldaholic.menu.rules.hardcore_high_stakes"), 0xFF5555);
			}
		}
		out.blank();
		out.line(Component.translatable("gui.burmaldaholic.menu.rules.chaos",
			Component.translatable(CasinoConfig.chaos().enabled ? "gui.burmaldaholic.common.on" : "gui.burmaldaholic.common.off")));
		out.blank();
		out.line(Component.translatable("gui.burmaldaholic.menu.rules.disclaimer"), 0xAAAAAA);
	}
}
