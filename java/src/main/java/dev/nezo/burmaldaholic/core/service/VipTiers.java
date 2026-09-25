package dev.nezo.burmaldaholic.core.service;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.VipConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** VIP tier constants shared by core (HUD, bet limits) and the vip module (GAME_DESIGN.md §12). */
public final class VipTiers {
	public static final String[] IDS = {"bronze", "silver", "gold", "platinum", "diamond", "netherite"};
	public static final int BRONZE = 0, SILVER = 1, GOLD = 2, PLATINUM = 3, DIAMOND = 4, NETHERITE = 5;
	private static final ChatFormatting[] COLORS = {ChatFormatting.RED, ChatFormatting.GRAY, ChatFormatting.GOLD,
		ChatFormatting.WHITE, ChatFormatting.AQUA, ChatFormatting.DARK_PURPLE};

	private VipTiers() {}

	public static int clamp(int tier) {
		return Math.max(0, Math.min(IDS.length - 1, tier));
	}

	/** {@code gui.burmaldaholic.vip.tier.<id>}, colored per UI.md §0.1. */
	public static MutableComponent name(int tier) {
		int t = clamp(tier);
		return Component.translatable("gui.burmaldaholic.vip.tier." + IDS[t]).withStyle(COLORS[t]);
	}

	/** Tier max bet from config {@code vip.maxBet.<tier>}. */
	public static long maxBet(int tier) {
		VipConfig.MaxBet m = CasinoConfig.vip().maxBet;
		return switch (clamp(tier)) {
			case 0 -> m.bronze;
			case 1 -> m.silver;
			case 2 -> m.gold;
			case 3 -> m.platinum;
			case 4 -> m.diamond;
			default -> m.netherite;
		};
	}
}
