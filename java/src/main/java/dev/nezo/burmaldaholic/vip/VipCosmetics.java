package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side name cosmetics (§12): Silver+ names take the tier color (unless a team already colors
 * them), Platinum+ get the {@code [Platinum]} chat title in front. Applied to {@code Player#getDisplayName}
 * (chat, join/death messages) while casino mode is on.
 */
public final class VipCosmetics {
	private VipCosmetics() {}

	/** Decorated display name, or null to keep the vanilla one. */
	public static Component decorateName(Player player, Component original) {
		if (original == null || !(player.level() instanceof ServerLevel level) || !CasinoMode.isEnabled(level)) {
			return null;
		}
		int tier = VipService.tier(level.getServer(), player.getUUID());
		if (tier < VipRules.SILVER) {
			return null;
		}
		MutableComponent tierName = VipTiers.name(tier);
		TextColor color = tierName.getStyle().getColor();
		MutableComponent name = original.copy().withStyle(style -> style.getColor() == null ? style.withColor(color) : style);
		if (tier < VipRules.PLATINUM) {
			return name;
		}
		return Component.translatable("msg.burmaldaholic.vip.chat_title", tierName).append(Texts.raw(" ")).append(name);
	}
}
